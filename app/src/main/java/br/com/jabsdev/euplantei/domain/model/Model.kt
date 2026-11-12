package br.com.jabsdev.euplantei.domain.model

data class ModelInfo(
    val path: String,
    val displayName: String,
    val sizeBytes: Long,
    val addedAtMillis: Long,
)

data class ModelSource(
    val url: String,
    val sha256: String,
)

data class LlmParams(
    val temperature: Float = 0.3f,
    val topP: Float = 0.9f,
    val topK: Int = 40,
    val repeatPenalty: Float = 1.1f,
    val maxTokens: Int = 512,
    val nCtx: Int = 4096,
    val nThreads: Int = 4,
)

enum class LlmRuntimeState {
    NOT_CONFIGURED,
    IDLE,
    LOADING,
    READY,
    GENERATING,
    UNLOADING,
    ERROR,
}
