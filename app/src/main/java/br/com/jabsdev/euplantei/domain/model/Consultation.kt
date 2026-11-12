package br.com.jabsdev.euplantei.domain.model

import kotlinx.serialization.Serializable

enum class ConsultationFailureKind {
    MODEL_MISSING,
    CLASSIFICATION,
    IMAGE,
    THERMAL,
    RATE_LIMITED,
    BUSY,
    GENERATION,
    CANCELLED,
    UNKNOWN,
}

enum class DraftStage {
    CLASSIFYING,
    AWAITING_CONFIRMATION,
    AWAITING_SYMPTOMS,
    AWAITING_MANUAL,
    FETCHING_WEATHER,
    GENERATING,
    SUCCESS,
    ERROR,
}

@Serializable
data class ConsultationDraft(
    val imagePath: String,
    val stage: DraftStage = DraftStage.CLASSIFYING,
    val topPredictions: List<PlantPrediction> = emptyList(),
    val selected: PlantPrediction? = null,
    val species: String = "",
    val condition: String = "",
    val confidencePercent: Int = 0,
    val manual: Boolean = false,
    val symptom: String = "",
    val question: String = "",
    val careProfile: PlantProfile? = null,
    val weather: WeatherContext? = null,
    val weatherFailure: WeatherFailureReason? = null,
    val weatherFromCache: Boolean = false,
    val weatherFetchedAtMillis: Long = 0L,
    val advice: String = "",
    val savedId: Long? = null,
    val saveFailed: Boolean = false,
    val failureKind: ConsultationFailureKind? = null,
    val failureTemperatureC: Float? = null,
    val failureWaitSeconds: Int? = null,
    val updatedAtMillis: Long = System.currentTimeMillis(),
)
