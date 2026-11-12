package br.com.jabsdev.euplantei.data.repo

import android.content.Context
import androidx.annotation.StringRes
import br.com.jabsdev.euplantei.BuildConfig
import br.com.jabsdev.euplantei.R
import br.com.jabsdev.euplantei.data.prefs.SettingsRepository
import br.com.jabsdev.euplantei.util.UpdateInstaller
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.util.Locale

/**
 * Metadados publicados em `update.json` junto de cada release do GitHub.
 * O APK é baixado só quando `versionCode` é maior que o do app instalado.
 */
@Serializable
data class UpdateInfo(
    val versionCode: Int = 0,
    val versionName: String = "",
    val apkUrl: String = "",
    val sha256: String = "",
    val notes: String = "",
)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(val info: UpdateInfo, val percent: Int) : UpdateState
    data class Ready(val info: UpdateInfo, val apkPath: String) : UpdateState
    data class Failed(@StringRes val messageRes: Int) : UpdateState
}

/**
 * Verifica a última release pública no GitHub e instala o APK novo via
 * PackageInstaller do sistema (o usuário precisa permitir "instalar apps
 * desconhecidos" para o EuPlantei).
 */
class UpdateManager(
    context: Context,
    private val settings: SettingsRepository,
    private val client: OkHttpClient,
    private val scope: CoroutineScope,
) {
    private val appContext = context.applicationContext
    private val json = Json { ignoreUnknownKeys = true }

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private var checkJob: Job? = null
    private var downloadJob: Job? = null

    /** Chamada na abertura da Home; respeita o intervalo de 6 horas. */
    fun checkIfDue() {
        if (checkJob?.isActive == true) return
        checkJob = scope.launch {
            val last = runCatching { settings.current().lastUpdateCheckAt }.getOrDefault(0L)
            if (System.currentTimeMillis() - last < CHECK_INTERVAL_MS) return@launch
            performCheck()
        }
    }

    /** Verificação manual (Configurações), sem throttle. */
    fun checkNow() {
        if (checkJob?.isActive == true) return
        checkJob = scope.launch { performCheck() }
    }

    fun downloadAndInstall(info: UpdateInfo) {
        if (downloadJob?.isActive == true) return
        downloadJob = scope.launch {
            _state.value = UpdateState.Downloading(info, 0)
            val directory = File(appContext.cacheDir, "updates").apply { mkdirs() }
            val target = File(directory, "EuPlantei-${info.versionName}.apk")
            try {
                val apkUrl = validatedApkUrl(info)
                download(apkUrl, target) { percent ->
                    _state.value = UpdateState.Downloading(info, percent)
                }
                val actual = sha256(target)
                if (info.sha256.isBlank() ||
                    !actual.equals(info.sha256.trim(), ignoreCase = true)
                ) {
                    target.delete()
                    _state.value = UpdateState.Failed(R.string.update_error_sha)
                    return@launch
                }
                _state.value = UpdateState.Ready(info, target.absolutePath)
                UpdateInstaller.install(appContext, target)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                target.delete()
                _state.value = UpdateState.Failed(R.string.update_error_download)
            }
        }
    }

    /** Repete a instalação do APK já baixado (ex.: depois de liberar a permissão). */
    fun installReady() {
        val ready = _state.value as? UpdateState.Ready ?: return
        val file = File(ready.apkPath)
        if (!file.exists()) {
            _state.value = UpdateState.Failed(R.string.update_error_download)
            return
        }
        UpdateInstaller.install(appContext, file)
    }

    private suspend fun performCheck() {
        _state.value = UpdateState.Checking
        val info = runCatching { fetchLatest() }.getOrNull()
        runCatching { settings.setLastUpdateCheckAt(System.currentTimeMillis()) }
        _state.value = when {
            info == null -> UpdateState.Failed(R.string.update_error_check)
            info.isNewerThan(BuildConfig.VERSION_CODE) -> UpdateState.Available(info)
            else -> UpdateState.UpToDate
        }
    }

    private suspend fun fetchLatest(): UpdateInfo = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(METADATA_URL)
            .header("Accept", "application/json")
            .header("Cache-Control", "no-cache")
            .header("User-Agent", "EuPlantei/${BuildConfig.VERSION_NAME} (Android)")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("GitHub respondeu ${response.code}")
            }
            val body = response.body?.string().orEmpty()
            val info = runCatching { json.decodeFromString<UpdateInfo>(body) }
                .getOrElse { throw IOException("update.json inválido", it) }
            if (info.versionCode <= 0 || info.versionName.isBlank()) {
                throw IOException("update.json sem versão")
            }
            info
        }
    }

    private fun validatedApkUrl(info: UpdateInfo): String {
        val url = info.apkUrl.trim()
        if (!url.startsWith("https://github.com/", ignoreCase = true)) {
            throw IOException("URL do APK fora do GitHub")
        }
        return url
    }

    private suspend fun download(
        url: String,
        target: File,
        onProgress: (Int) -> Unit,
    ) = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "EuPlantei/${BuildConfig.VERSION_NAME} (Android)")
            .build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException("Download respondeu ${response.code}")
            }
            val body = response.body ?: throw IOException("Resposta vazia")
            val total = body.contentLength()
            var written = 0L
            var lastPercent = -1
            body.byteStream().use { input ->
                FileOutputStream(target).use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        output.write(buffer, 0, read)
                        written += read
                        if (total > 0) {
                            val percent = ((written * 100) / total).toInt().coerceIn(0, 100)
                            if (percent != lastPercent) {
                                lastPercent = percent
                                onProgress(percent)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { stream ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = stream.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") {
            String.format(Locale.ROOT, "%02x", it)
        }
    }

    companion object {
        const val OWNER = "JabsDev"
        const val REPO = "EuPlantei"
        const val METADATA_URL =
            "https://github.com/$OWNER/$REPO/releases/latest/download/update.json"
        private const val CHECK_INTERVAL_MS = 6L * 60L * 60L * 1000L
    }
}

internal fun UpdateInfo.isNewerThan(currentVersionCode: Int): Boolean =
    versionCode > currentVersionCode
