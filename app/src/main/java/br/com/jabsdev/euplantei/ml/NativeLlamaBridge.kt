package br.com.jabsdev.euplantei.ml

/**
 * Ponte JNI fina. O carregamento da biblioteca é feito sob demanda para que
 * testes JVM e telas sem IA não toquem no código nativo.
 */
object NativeLlamaBridge {
    init {
        System.loadLibrary("euplantei_llm")
    }

    external fun nativeLoadModel(path: String, nCtx: Int, nThreads: Int): Int
    external fun nativeGenerate(
        prompt: String,
        temperature: Float,
        topP: Float,
        topK: Int,
        repeatPenalty: Float,
        maxTokens: Int,
        callback: LlamaTokenCallback,
    ): Int
    external fun nativeCancel()
    external fun nativeUnload()
    external fun nativeIsLoaded(): Boolean
}

fun interface LlamaTokenCallback {
    fun onToken(token: String)
}

class LlmEngineException(
    val code: Int,
    message: String,
) : Exception(message)
