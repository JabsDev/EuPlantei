package br.com.jabsdev.euplantei.ml

import android.graphics.Bitmap
import br.com.jabsdev.euplantei.domain.model.PlantPrediction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Combina o classificador de plantas de interior (47 espécies) com o
 * classificador PlantVillage (14 culturas + doença). O primeiro cobre os
 * vasos e ornamentais; o segundo cobre hortaliças/frutíferas e doença foliar.
 *
 * Quando há candidatos dos dois modelos, a UI pede confirmação ao usuário em
 * vez de escolher silenciosamente a maior confiança. Isso evita casos como uma
 * foto de lírio ser rotulada como milho.
 */
class HybridVisionClassifier(
    private val houseplantClassifier: VisionClassifier,
    private val cropClassifier: VisionClassifier,
) : VisionClassifier {

    override val labelsCount: Int =
        houseplantClassifier.labelsCount + cropClassifier.labelsCount

    override suspend fun classify(bitmap: Bitmap): List<PlantPrediction> =
        withContext(Dispatchers.Default) {
            val house = runCatching { houseplantClassifier.classify(bitmap) }
                .getOrDefault(emptyList())
            val crop = runCatching { cropClassifier.classify(bitmap) }
                .getOrDefault(emptyList())

            if (house.isEmpty() && crop.isEmpty()) {
                throw IllegalStateException("Nenhum classificador de visão conseguiu analisar a imagem")
            }

            (house.filter { it.confidence >= MIN_CANDIDATE_CONFIDENCE }.take(3) +
                crop.filter { it.confidence >= MIN_CANDIDATE_CONFIDENCE }.take(2))
                .distinctBy { "${it.speciesId}|${it.condition}" }
                .sortedByDescending { it.confidence }
                .take(MAX_CANDIDATES)
        }

    override fun close() {
        houseplantClassifier.close()
        cropClassifier.close()
    }

    private companion object {
        const val MIN_CANDIDATE_CONFIDENCE = 0.35f
        const val MAX_CANDIDATES = 4
    }
}
