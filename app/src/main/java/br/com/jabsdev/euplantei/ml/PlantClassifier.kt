package br.com.jabsdev.euplantei.ml

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import br.com.jabsdev.euplantei.domain.LabelParser
import br.com.jabsdev.euplantei.domain.model.FriendlyLabel
import br.com.jabsdev.euplantei.domain.model.PlantPrediction
import br.com.jabsdev.euplantei.domain.model.PredictionSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.tensorflow.lite.DataType
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.roundToInt

/**
 * Classificador de visão TFLite.
 * A entrada é float32 [1,224,224,3]; `normalizeToUnit` escolhe [0,1] (PlantVillage)
 * ou [0,255] (modelo de espécies, cuja normalização ImageNet está no próprio grafo).
 */
class PlantClassifier(
    context: Context,
    private val modelAsset: String = "models/plant_model.tflite",
    private val labelsAsset: String = "models/labels.txt",
    private val friendlyAsset: String = "models/labels_ptbr.json",
    private val normalizeToUnit: Boolean = true,
    private val source: PredictionSource = PredictionSource.CROP_DISEASE,
) : VisionClassifier {

    private val appContext = context.applicationContext
    private val interpreter: Interpreter
    private val labels: List<String>
    private val friendlyMap: Map<String, FriendlyLabel>

    @Volatile
    private var closed = false

    override val labelsCount: Int get() = labels.size

    init {
        labels = loadLabels()
        friendlyMap = loadFriendlyLabels()
        interpreter = try {
            Interpreter(
                loadModelBuffer(),
                Interpreter.Options().apply {
                    setNumThreads(4)
                },
            )
        } catch (error: Exception) {
            Log.e(TAG, "Falha ao carregar o TFLite", error)
            throw IOException("Falha ao carregar o modelo de visão", error)
        }
    }

    override suspend fun classify(bitmap: Bitmap): List<PlantPrediction> = withContext(Dispatchers.Default) {
        check(!closed) { "Classificador já foi fechado" }
        val inputTensor = interpreter.getInputTensor(0)
        val inputShape = inputTensor.shape()
        val height = inputShape.getOrNull(1) ?: 224
        val width = inputShape.getOrNull(2) ?: 224
        val channels = inputShape.getOrNull(3) ?: 3

        val scaled = if (bitmap.width != width || bitmap.height != height) {
            Bitmap.createScaledBitmap(bitmap, width, height, true)
        } else {
            bitmap
        }
        try {
            val pixels = IntArray(width * height)
            scaled.getPixels(pixels, 0, width, 0, 0, width, height)
            val input = createInputBuffer(inputTensor.dataType(), pixels, width, height, channels)
            val outputTensor = interpreter.getOutputTensor(0)
            val classCount = outputTensor.shape().lastOrNull() ?: labels.size
            val output = Array(1) { FloatArray(classCount) }
            interpreter.run(input, output)

            val probabilities = when (outputTensor.dataType()) {
                DataType.FLOAT32 -> output[0]
                DataType.UINT8, DataType.INT8 -> dequantize(output[0], outputTensor)
                else -> output[0]
            }
            val normalized = ensureProbabilities(probabilities)
            LabelParser.topPredictions(labels, normalized, friendlyMap, limit = 3)
                .map { it.copy(source = source) }
        } finally {
            if (scaled !== bitmap && !scaled.isRecycled) scaled.recycle()
        }
    }

    override fun close() {
        if (closed) return
        closed = true
        runCatching { interpreter.close() }
    }

    private fun createInputBuffer(
        dataType: DataType,
        pixels: IntArray,
        width: Int,
        height: Int,
        channels: Int,
    ): ByteBuffer {
        val pixelCount = width * height
        val buffer = when (dataType) {
            DataType.FLOAT32 -> ByteBuffer.allocateDirect(pixelCount * channels * 4)
            else -> ByteBuffer.allocateDirect(pixelCount * channels)
        }.order(ByteOrder.nativeOrder())

        for (pixel in pixels) {
            val r = (pixel shr 16) and 0xFF
            val g = (pixel shr 8) and 0xFF
            val b = pixel and 0xFF
            when (dataType) {
                DataType.FLOAT32 -> {
                    // PlantVillage espera [0,1]; o modelo de plantas de interior
                    // já tem uma camada Rescaling interna e espera [0,255].
                    val scale = if (normalizeToUnit) 255f else 1f
                    buffer.putFloat(r / scale)
                    buffer.putFloat(g / scale)
                    buffer.putFloat(b / scale)
                }
                DataType.UINT8 -> {
                    buffer.put(r.toByte())
                    buffer.put(g.toByte())
                    buffer.put(b.toByte())
                }
                DataType.INT8 -> {
                    buffer.put((r - 128).toByte())
                    buffer.put((g - 128).toByte())
                    buffer.put((b - 128).toByte())
                }
                else -> error("Tipo de entrada não suportado: $dataType")
            }
            if (channels > 3) {
                repeat(channels - 3) {
                    if (dataType == DataType.FLOAT32) buffer.putFloat(0f) else buffer.put(0)
                }
            }
        }
        buffer.rewind()
        return buffer
    }

    private fun dequantize(values: FloatArray, tensor: org.tensorflow.lite.Tensor): FloatArray {
        val params = tensor.quantizationParams()
        val scale = params.scale
        val zeroPoint = params.zeroPoint
        if (scale == 0f) return values
        return FloatArray(values.size) { index ->
            // O buffer de saída quantizado foi lido em FloatArray pelo interpreter;
            // convertemos de volta ao valor real antes de normalizar.
            (values[index] - zeroPoint) * scale
        }
    }

    /** Alguns modelos já terminam em softmax; outros entregam logits. Detectamos sem heurística fixa. */
    private fun ensureProbabilities(values: FloatArray): FloatArray {
        if (values.isEmpty()) return values
        val sum = values.sum()
        val hasNegative = values.any { it < 0f }
        if (!hasNegative && abs(sum - 1f) <= 0.08f) return values
        val max = values.maxOrNull() ?: 0f
        val exps = FloatArray(values.size) { exp((values[it] - max).toDouble()).toFloat() }
        val expSum = exps.sum().takeIf { it > 0f } ?: 1f
        return FloatArray(values.size) { exps[it] / expSum }
    }

    private fun loadLabels(): List<String> {
        val content = appContext.assets.open(labelsAsset).bufferedReader().use { it.readText() }
        val result = content.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        if (result.isEmpty()) throw IOException("Arquivo de labels vazio: $labelsAsset")
        return result
    }

    private fun loadFriendlyLabels(): Map<String, FriendlyLabel> {
        val json = Json { ignoreUnknownKeys = true }
        val content = appContext.assets.open(friendlyAsset)
            .bufferedReader()
            .use { it.readText() }
        val file = json.decodeFromString<LabelsFile>(content)
        return file.labels.entries.associate { (key, value) ->
            LabelParser.normalizeKey(key) to value
        }
    }

    private fun loadModelBuffer(): ByteBuffer {
        val assetPath = modelAsset
        return try {
            appContext.assets.openFd(assetPath).use { descriptor ->
                FileInputStream(descriptor.fileDescriptor).use { stream ->
                    stream.channel.map(
                        FileChannel.MapMode.READ_ONLY,
                        descriptor.startOffset,
                        descriptor.declaredLength,
                    )
                }
            }
        } catch (error: Exception) {
            Log.w(TAG, "Asset comprimido; copiando o TFLite para memória", error)
            val bytes = appContext.assets.open(assetPath).use { it.readBytes() }
            ByteBuffer.allocateDirect(bytes.size).order(ByteOrder.nativeOrder()).apply {
                put(bytes)
                rewind()
            }
        }
    }

    @Serializable
    private data class LabelsFile(val labels: Map<String, FriendlyLabel>)

    companion object {
        private const val TAG = "PlantClassifier"
    }
}
