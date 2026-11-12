package br.com.jabsdev.euplantei.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Todo o trabalho com imagens passa por aqui:
 * - decodificação com inSampleSize (nunca um bitmap full-res na memória);
 * - correção de orientação EXIF;
 * - limite de 1024 px para exibição/compartilhamento;
 * - recorte central 224x224 para o TensorFlow Lite.
 */
object ImageUtils {
    private const val GENERAL_MAX_DIMENSION = 1024
    const val CLASSIFIER_SIZE = 224

    suspend fun prepareFromUri(context: Context, uri: Uri): File = withContext(Dispatchers.IO) {
        val decoded = decodeSampledBitmap(context, uri, GENERAL_MAX_DIMENSION)
            ?: throw IOException("Não foi possível decodificar a imagem")
        val oriented = orientBitmap(context, uri, decoded)
        try {
            saveConsultationBitmap(context, oriented)
        } finally {
            if (!oriented.isRecycled) oriented.recycle()
        }
    }

    suspend fun prepareFromFile(context: Context, source: File): File = withContext(Dispatchers.IO) {
        require(source.exists()) { "Arquivo de captura ausente" }
        val decoded = decodeSampledBitmap(source.absolutePath, GENERAL_MAX_DIMENSION)
            ?: throw IOException("Não foi possível decodificar a captura")
        val oriented = orientBitmap(source.absolutePath, decoded)
        try {
            saveConsultationBitmap(context, oriented)
        } finally {
            if (!oriented.isRecycled) oriented.recycle()
        }
    }

    /**
     * Gera o tensor de entrada 224x224. O recorte central evita distorcer a folha,
     * que é o erro mais comum ao redimensionar direto para um quadrado.
     */
    suspend fun classifierBitmap(path: String): Bitmap = withContext(Dispatchers.IO) {
        val source = decodeSampledBitmap(path, GENERAL_MAX_DIMENSION)
            ?: throw IOException("Não foi possível abrir a imagem para classificação")
        try {
            centerCrop(source, CLASSIFIER_SIZE)
        } finally {
            if (!source.isRecycled) source.recycle()
        }
    }

    fun decodeSampledBitmap(context: Context, uri: Uri, maxDimension: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        val opened = context.contentResolver.openInputStream(uri) ?: return null
        opened.use { stream -> BitmapFactory.decodeStream(stream, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxDimension)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return context.contentResolver.openInputStream(uri)?.use { stream ->
            BitmapFactory.decodeStream(stream, null, options)
        }
    }

    fun decodeSampledBitmap(path: String, maxDimension: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = calculateInSampleSize(bounds.outWidth, bounds.outHeight, maxDimension)
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        return BitmapFactory.decodeFile(path, options)
    }

    private fun calculateInSampleSize(width: Int, height: Int, maxDimension: Int): Int {
        var sample = 1
        var longest = max(width, height)
        while (longest / 2 >= maxDimension) {
            longest /= 2
            sample *= 2
        }
        return sample.coerceAtLeast(1)
    }

    private fun orientBitmap(context: Context, uri: Uri, bitmap: Bitmap): Bitmap {
        val orientation = runCatching {
            context.contentResolver.openInputStream(uri)?.use { stream ->
                ExifInterface(stream).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            } ?: ExifInterface.ORIENTATION_NORMAL
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        return applyOrientation(bitmap, orientation)
    }

    private fun orientBitmap(path: String, bitmap: Bitmap): Bitmap {
        val orientation = runCatching {
            ExifInterface(path).getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL,
            )
        }.getOrDefault(ExifInterface.ORIENTATION_NORMAL)
        return applyOrientation(bitmap, orientation)
    }

    private fun applyOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            else -> return bitmap
        }
        val transformed = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        if (transformed !== bitmap) bitmap.recycle()
        return transformed
    }

    private fun centerCrop(source: Bitmap, size: Int): Bitmap {
        val side = minOf(source.width, source.height)
        val left = ((source.width - side) / 2f).roundToInt()
        val top = ((source.height - side) / 2f).roundToInt()
        val output = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(output)
        val src = android.graphics.Rect(left, top, left + side, top + side)
        val dst = android.graphics.Rect(0, 0, size, size)
        canvas.drawBitmap(source, src, dst, android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
        return output
    }

    private fun saveConsultationBitmap(context: Context, bitmap: Bitmap): File {
        val dir = File(context.cacheDir, "consultas").apply { mkdirs() }
        val output = File(dir, "consulta_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.jpg")
        FileOutputStream(output).use { stream ->
            if (!bitmap.compress(Bitmap.CompressFormat.JPEG, 90, stream)) {
                throw IOException("Falha ao codificar JPEG")
            }
        }
        return output
    }

    /** Copia a imagem do cache para o armazenamento persistente do app. */
    suspend fun persistForHistory(context: Context, sourcePath: String): File = withContext(Dispatchers.IO) {
        val source = File(sourcePath)
        val dir = File(context.filesDir, "consultas").apply { mkdirs() }
        val target = File(dir, "hist_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.jpg")
        source.copyTo(target, overwrite = false)
        target
    }

    /** Copia a foto para o armazenamento permanente do jardim. */
    suspend fun persistForGarden(context: Context, sourcePath: String): File = withContext(Dispatchers.IO) {
        val source = File(sourcePath)
        require(source.exists()) { "Imagem do jardim ausente" }
        val dir = File(context.filesDir, "garden").apply { mkdirs() }
        val target = File(dir, "garden_${System.currentTimeMillis()}_${UUID.randomUUID().toString().take(6)}.jpg")
        source.copyTo(target, overwrite = false)
        target
    }

    fun shareUri(context: Context, path: String): Uri = FileProvider.getUriForFile(
        context,
        "${context.packageName}.fileprovider",
        File(path),
    )

    suspend fun deleteQuietly(path: String?) {
        if (path.isNullOrBlank()) return
        withContext(Dispatchers.IO) {
            runCatching { File(path).delete() }
        }
    }
}
