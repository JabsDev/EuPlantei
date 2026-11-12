package br.com.jabsdev.euplantei.domain.model

import kotlinx.serialization.Serializable

@Serializable
data class FriendlyLabel(
    val species: String,
    val speciesId: String,
    val condition: String,
    val healthy: Boolean,
)

@Serializable
enum class PredictionSource {
    HOUSEPLANT,
    CROP_DISEASE,
    MANUAL,
}

@Serializable
data class PlantPrediction(
    val rawLabel: String,
    val species: String,
    val speciesId: String,
    val condition: String,
    val healthy: Boolean,
    val confidence: Float,
    val source: PredictionSource = PredictionSource.CROP_DISEASE,
)

enum class ConfidenceLevel {
    HIGH,
    MEDIUM,
    LOW,
}

/**
 * Regras de produto para a confiança do classificador.
 * - >= 65%: segue automaticamente
 * - 40% a 64%: pede confirmação entre as top-3
 * - < 40%: fluxo manual
 */
object ConfidenceRules {
    const val HIGH_THRESHOLD = 0.65f
    const val MEDIUM_THRESHOLD = 0.40f

    fun level(confidence: Float): ConfidenceLevel = when {
        confidence >= HIGH_THRESHOLD -> ConfidenceLevel.HIGH
        confidence >= MEDIUM_THRESHOLD -> ConfidenceLevel.MEDIUM
        else -> ConfidenceLevel.LOW
    }

    fun shouldUseAutomatically(confidence: Float): Boolean = confidence >= HIGH_THRESHOLD

    fun needsConfirmation(confidence: Float): Boolean =
        confidence >= MEDIUM_THRESHOLD && confidence < HIGH_THRESHOLD

    fun needsManualInput(confidence: Float): Boolean = confidence < MEDIUM_THRESHOLD
}
