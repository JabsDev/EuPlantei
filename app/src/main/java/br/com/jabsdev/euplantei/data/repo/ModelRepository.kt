package br.com.jabsdev.euplantei.data.repo

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import br.com.jabsdev.euplantei.data.prefs.SettingsRepository
import br.com.jabsdev.euplantei.domain.model.ModelInfo
import br.com.jabsdev.euplantei.domain.model.ModelSource
import br.com.jabsdev.euplantei.service.ModelDownloadService
import br.com.jabsdev.euplantei.util.NetworkMonitor
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

enum class DownloadFailure {
    OFFLINE,
    NETWORK,
    HTTP,
    INVALID_GGUF,
    SHA_MISMATCH,
    NO_SPACE,
    IO,
    CANCELLED,
    INVALID_URL,
}

sealed interface ModelDownloadState {
    data object Idle : ModelDownloadState
    data class Downloading(
        val downloadedBytes: Long,
        val totalBytes: Long,
        val percent: Int,
    ) : ModelDownloadState
    data object Verifying : ModelDownloadState
    data class Completed(val info: ModelInfo) : ModelDownloadState
    data class Failed(val reason: DownloadFailure, val httpCode: Int? = null) : ModelDownloadState
}

/**
 * Download retomável (HTTP Range + SHA-256 opcional) e importação de GGUF local.
 * O arquivo parcial fica em `filesDir/models/download.part` e sobrevive a reinícios.
 */
class ModelRepository(
    context: Context,
    private val settings: SettingsRepository,
    private val networkMonitor: NetworkMonitor,
    private val scope: CoroutineScope,
    private val downloadClient: OkHttpClient = defaultDownloadClient(),
) {
    private val appContext = context.applicationContext
    private val modelsDir = File(appContext.filesDir, "models").apply { mkdirs() }
    private val partFile = File(modelsDir, "download.part")

    private val _downloadState = MutableStateFlow<ModelDownloadState>(ModelDownloadState.Idle)
    val downloadState: StateFlow<ModelDownloadState> = _downloadState.asStateFlow()

    private val cancelRequested = AtomicBoolean(false)
    private var downloadJob: Job? = null

    @Volatile
    private var activeCall: okhttp3.Call? = null

    fun observeModel(): kotlinx.coroutines.flow.Flow<ModelInfo?> = settings.modelInfo

    suspend fun activeModelOnce(): ModelInfo? {
        val info = settings.modelInfo.first() ?: return null
        if (!File(info.path).exists()) {
            settings.clearActiveModel()
            return null
        }
        return info
    }

    suspend fun modelSource(): ModelSource = settings.modelSource.first()

    fun startDownload() {
        if (downloadJob?.isActive == true) {
            ModelDownloadService.start(appContext)
            return
        }
        cancelRequested.set(false)
        // Estado definido antes de subir o serviço para que a notificação já
        // encontre o download em andamento.
        _downloadState.value = ModelDownloadState.Downloading(
            downloadedBytes = partFile.length(),
            totalBytes = -1,
            percent = 0,
        )
        ModelDownloadService.start(appContext)
        downloadJob = scope.launch {
            runCatching { performDownload() }
                .onFailure { error ->
                    if (error is CancellationException) {
                        _downloadState.value = ModelDownloadState.Failed(DownloadFailure.CANCELLED)
                    } else {
                        _downloadState.value = ModelDownloadState.Failed(
                            reason = classifyFailure(error),
                            httpCode = (error as? HttpStatusException)?.code,
                        )
                    }
                }
        }
    }

    fun cancelDownload() {
        cancelRequested.set(true)
        activeCall?.cancel()
    }

    suspend fun importModel(uri: Uri): Result<ModelInfo> = withContext(Dispatchers.IO) {
        runCatching {
            val displayName = queryDisplayName(uri) ?: "modelo.gguf"
            require(displayName.endsWith(".gguf", ignoreCase = true)) {
                "O arquivo precisa ter extensão .gguf"
            }
            val target = File(modelsDir, "import_${System.currentTimeMillis()}.gguf")
            try {
                val input = appContext.contentResolver.openInputStream(uri)
                    ?: throw IOException("Não foi possível abrir o arquivo")
                input.use { source -> target.outputStream().use { output -> source.copyTo(output) } }
                if (!hasGgufMagic(target)) {
                    target.delete()
                    throw IOException("Cabeçalho GGUF inválido")
                }
                if (target.length() < MIN_MODEL_BYTES) {
                    target.delete()
                    throw IOException("Arquivo GGUF muito pequeno")
                }
                val info = ModelInfo(
                    path = target.absolutePath,
                    displayName = displayName,
                    sizeBytes = target.length(),
                    addedAtMillis = System.currentTimeMillis(),
                )
                settings.setActiveModel(info)
                _downloadState.value = ModelDownloadState.Completed(info)
                info
            } catch (error: Throwable) {
                target.delete()
                throw error
            }
        }
    }

    suspend fun deleteActiveModel() = withContext(Dispatchers.IO) {
        val info = settings.modelInfo.first()
        if (info != null) {
            val file = File(info.path)
            if (file.exists() && isInsideModelsDir(file)) file.delete()
        }
        settings.clearActiveModel()
        partFile.delete()
        _downloadState.value = ModelDownloadState.Idle
    }

    suspend fun updateSource(url: String, sha256: String) {
        val trimmedUrl = url.trim()
        require(trimmedUrl.startsWith("https://")) { "A URL precisa usar https" }
        val normalizedSha = sha256.trim().lowercase(Locale.ROOT)
        require(normalizedSha.isBlank() || normalizedSha.matches(Regex("[0-9a-f]{64}"))) {
            "SHA-256 deve ter 64 caracteres hexadecimais"
        }
        val previous = settings.modelSource.first()
        if (previous.url != trimmedUrl) {
            // Um parcial de outra URL não pode ser retomado com segurança.
            partFile.delete()
        }
        settings.setModelSource(trimmedUrl, normalizedSha)
    }

    private suspend fun performDownload() {
        if (!networkMonitor.isOnlineNow()) throw OfflineException()
        val source = settings.modelSource.first()
        if (!source.url.startsWith("https://")) throw InvalidUrlException()
        _downloadState.value = ModelDownloadState.Downloading(
            downloadedBytes = partFile.length(),
            totalBytes = -1,
            percent = 0,
        )

        var restartOnce = true
        while (true) {
            if (cancelRequested.get()) throw CancellationException("Download cancelado")
            val existing = if (partFile.exists()) partFile.length() else 0L
            val requestBuilder = Request.Builder()
                .url(source.url)
                .header("User-Agent", "EuPlantei/1.0 (Android)")
            if (existing > 0) {
                requestBuilder.header("Range", "bytes=$existing-")
            }
            val call = downloadClient.newCall(requestBuilder.build())
            activeCall = call
            val response = try {
                call.execute()
            } catch (error: IOException) {
                if (cancelRequested.get()) throw CancellationException("Download cancelado")
                throw error
            }
            var retryRequest = false
            response.use { result ->
                if (result.code == 416 && existing > 0 && restartOnce) {
                    restartOnce = false
                    partFile.delete()
                    retryRequest = true
                    return@use
                }
                if (!result.isSuccessful) {
                    throw HttpStatusException(result.code)
                }
                val body = result.body ?: throw IOException("Resposta vazia")
                val append = result.code == 206 && existing > 0
                val total = if (body.contentLength() > 0) {
                    (if (append) existing else 0L) + body.contentLength()
                } else {
                    -1L
                }
                writeBody(body.byteStream(), append, total)
            }
            activeCall = null
            if (retryRequest) continue
            break
        }

        if (cancelRequested.get()) throw CancellationException("Download cancelado")
        _downloadState.value = ModelDownloadState.Verifying
        if (!hasGgufMagic(partFile)) {
            partFile.delete()
            throw InvalidGgufException()
        }
        val expected = source.sha256.trim().lowercase(Locale.ROOT)
        if (expected.isNotBlank()) {
            val actual = sha256(partFile)
            if (actual != expected) {
                partFile.delete()
                throw ShaMismatchException()
            }
        }

        val target = File(modelsDir, "euplantei_${System.currentTimeMillis()}.gguf")
        if (partFile.exists() && !partFile.renameTo(target)) {
            partFile.copyTo(target, overwrite = true)
            partFile.delete()
        }
        val info = ModelInfo(
            path = target.absolutePath,
            displayName = appContext.getString(br.com.jabsdev.euplantei.R.string.settings_model_default_name),
            sizeBytes = target.length(),
            addedAtMillis = System.currentTimeMillis(),
        )
        settings.setActiveModel(info)
        _downloadState.value = ModelDownloadState.Completed(info)
    }

    private fun writeBody(input: InputStream, append: Boolean, totalBytes: Long) {
        FileOutputStream(partFile, append).use { output ->
            val buffer = ByteArray(64 * 1024)
            var downloaded = if (append) partFile.length() else 0L
            var lastUpdate = 0L
            var read: Int
            while (input.read(buffer).also { read = it } != -1) {
                if (cancelRequested.get()) throw CancellationException("Download cancelado")
                output.write(buffer, 0, read)
                downloaded += read
                val now = System.currentTimeMillis()
                if (now - lastUpdate >= PROGRESS_INTERVAL_MS) {
                    lastUpdate = now
                    _downloadState.value = ModelDownloadState.Downloading(
                        downloadedBytes = downloaded,
                        totalBytes = totalBytes,
                        percent = if (totalBytes > 0) {
                            ((downloaded * 100) / totalBytes).toInt().coerceIn(0, 100)
                        } else {
                            0
                        },
                    )
                }
            }
            output.flush()
            _downloadState.value = ModelDownloadState.Downloading(
                downloadedBytes = downloaded,
                totalBytes = if (totalBytes > 0) totalBytes else downloaded,
                percent = if (totalBytes > 0) 100 else 0,
            )
            if (totalBytes > 0 && downloaded != totalBytes) {
                throw IOException("Download incompleto: $downloaded de $totalBytes bytes")
            }
        }
    }

    private fun hasGgufMagic(file: File): Boolean {
        if (!file.exists() || file.length() < 4) return false
        return runCatching {
            file.inputStream().use { stream ->
                val magic = ByteArray(4)
                stream.read(magic) == 4 &&
                    magic.contentEquals(byteArrayOf(0x47, 0x47, 0x55, 0x46))
            }
        }.getOrDefault(false)
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { stream ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = stream.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun queryDisplayName(uri: Uri): String? =
        appContext.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }

    private fun isInsideModelsDir(file: File): Boolean =
        runCatching { file.canonicalPath.startsWith(modelsDir.canonicalPath + File.separator) }
            .getOrDefault(false)

    private fun classifyFailure(error: Throwable): DownloadFailure = when (error) {
        is OfflineException -> DownloadFailure.OFFLINE
        is HttpStatusException -> DownloadFailure.HTTP
        is InvalidGgufException -> DownloadFailure.INVALID_GGUF
        is ShaMismatchException -> DownloadFailure.SHA_MISMATCH
        is InvalidUrlException -> DownloadFailure.INVALID_URL
        is IOException -> if (error.message?.contains("ENOSPC", true) == true) {
            DownloadFailure.NO_SPACE
        } else {
            DownloadFailure.NETWORK
        }
        else -> DownloadFailure.IO
    }

    private class OfflineException : IOException("Sem conexão")
    private class InvalidUrlException : IOException("URL inválida")
    private class InvalidGgufException : IOException("GGUF inválido")
    private class ShaMismatchException : IOException("SHA-256 não confere")
    private class HttpStatusException(val code: Int) : IOException("HTTP $code")

    companion object {
        private const val PROGRESS_INTERVAL_MS = 150L
        private const val MIN_MODEL_BYTES = 16L * 1024 * 1024

        fun defaultDownloadClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(0, TimeUnit.MILLISECONDS)
            .build()
    }
}
