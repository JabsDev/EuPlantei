package br.com.jabsdev.euplantei.ml

import br.com.jabsdev.euplantei.domain.model.LlmParams
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

interface LlmEngine {
    val isLoaded: Boolean

    suspend fun load(modelPath: String, nCtx: Int = 4096, nThreads: Int = 4)

    fun generate(prompt: String, params: LlmParams = LlmParams()): Flow<String>

    fun cancel()

    suspend fun unload()
}

class LlamaEngine : LlmEngine {
    @Volatile
    private var loaded = false

    override val isLoaded: Boolean
        get() = loaded && NativeLlamaBridge.nativeIsLoaded()

    override suspend fun load(modelPath: String, nCtx: Int, nThreads: Int) {
        withContext(Dispatchers.IO) {
            val code = NativeLlamaBridge.nativeLoadModel(modelPath, nCtx, nThreads)
            if (code != 0) {
                loaded = false
                throw LlmEngineException(code, "Falha ao carregar o modelo (código $code)")
            }
            loaded = true
        }
    }

    override fun generate(prompt: String, params: LlmParams): Flow<String> = callbackFlow {
        val job = launch(Dispatchers.IO) {
            val code = runCatching {
                NativeLlamaBridge.nativeGenerate(
                    prompt = prompt,
                    temperature = params.temperature,
                    topP = params.topP,
                    topK = params.topK,
                    repeatPenalty = params.repeatPenalty,
                    maxTokens = params.maxTokens,
                    callback = LlamaTokenCallback { token ->
                        trySend(token)
                    },
                )
            }.getOrElse { error ->
                close(LlmEngineException(-99, error.message ?: "Falha nativa"))
                return@launch
            }
            when {
                code == 2 -> close() // cancelado de forma cooperativa no JNI
                code < 0 -> close(LlmEngineException(code, nativeErrorMessage(code)))
                else -> close()
            }
        }
        awaitClose {
            NativeLlamaBridge.nativeCancel()
            job.cancel()
        }
    }

    override fun cancel() {
        NativeLlamaBridge.nativeCancel()
    }

    override suspend fun unload() {
        withContext(Dispatchers.IO) {
            NativeLlamaBridge.nativeUnload()
            loaded = false
        }
    }

    private fun nativeErrorMessage(code: Int): String = when (code) {
        -1 -> "Modelo não carregado"
        -2 -> "Prompt maior que a janela de contexto"
        -3 -> "Falha ao tokenizar o prompt"
        -4 -> "Falha na inferência do modelo"
        -5 -> "Callback ou recursos inválidos"
        -6 -> "Falha ao entregar o texto gerado"
        else -> "Erro nativo ($code)"
    }
}
