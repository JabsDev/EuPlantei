package br.com.jabsdev.euplantei.ml

import android.os.SystemClock
import br.com.jabsdev.euplantei.data.repo.ModelRepository
import br.com.jabsdev.euplantei.domain.model.LlmParams
import br.com.jabsdev.euplantei.domain.model.LlmRuntimeState
import br.com.jabsdev.euplantei.util.ThermalMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

class ModelNotConfiguredException : IllegalStateException("Nenhum modelo GGUF configurado")
class LlmLoadException(message: String, cause: Throwable? = null) : IllegalStateException(message, cause)
class ThermalBlockedException(val temperatureC: Float) :
    IllegalStateException("Dispositivo a ${temperatureC} °C")
class RateLimitedException(val remainingMs: Long) :
    IllegalStateException("Aguarde ${remainingMs / 1000}s entre gerações")
class LlmBusyException : IllegalStateException("Já existe uma geração em andamento")

/**
 * Mantém o modelo carregado entre consultas, serializa gerações, aplica o
 * intervalo mínimo de 15 s e descarrega após 120 s ociosos ou sob pressão de memória.
 */
class LlmManager(
    private val scope: CoroutineScope,
    private val engine: LlmEngine,
    private val modelRepository: ModelRepository,
    val thermalMonitor: ThermalMonitor,
) {
    private val loadMutex = Mutex()
    private val generationMutex = Mutex()
    private val _state = MutableStateFlow(LlmRuntimeState.NOT_CONFIGURED)
    val state: StateFlow<LlmRuntimeState> = _state.asStateFlow()

    @Volatile
    private var loadedPath: String? = null

    @Volatile
    private var lastGenerationAt: Long = 0L

    @Volatile
    private var cancelRequested = false

    private var idleJob: Job? = null

    fun generate(prompt: String, params: LlmParams = LlmParams()): Flow<String> = flow {
        cancelRequested = false
        val temperature = thermalMonitor.temperatureC.value ?: 0f
        if (temperature >= ThermalMonitor.CRITICAL_THRESHOLD_C) {
            throw ThermalBlockedException(temperature)
        }
        if (!generationMutex.tryLock()) {
            throw LlmBusyException()
        }
        try {
            val now = SystemClock.elapsedRealtime()
            val elapsed = now - lastGenerationAt
            if (lastGenerationAt > 0L && elapsed < MIN_INTERVAL_MS) {
                throw RateLimitedException(MIN_INTERVAL_MS - elapsed)
            }
            idleJob?.cancel()
            ensureLoaded()
            if (cancelRequested) {
                // Cancelamento durante o carregamento do modelo: encerra sem gerar.
                return@flow
            }
            lastGenerationAt = SystemClock.elapsedRealtime()
            _state.value = LlmRuntimeState.GENERATING
            try {
                emitAll(engine.generate(prompt, params))
            } finally {
                _state.value = if (engine.isLoaded) {
                    LlmRuntimeState.READY
                } else {
                    LlmRuntimeState.NOT_CONFIGURED
                }
            }
        } finally {
            if (generationMutex.isLocked) {
                generationMutex.unlock()
            }
            scheduleIdleUnload()
        }
    }

    fun cancel() {
        cancelRequested = true
        engine.cancel()
    }

    suspend fun ensureLoaded() {
        loadMutex.withLock {
            val model = modelRepository.activeModelOnce()
                ?: run {
                    _state.value = LlmRuntimeState.NOT_CONFIGURED
                    throw ModelNotConfiguredException()
                }
            if (!File(model.path).exists()) {
                _state.value = LlmRuntimeState.NOT_CONFIGURED
                throw ModelNotConfiguredException()
            }
            if (engine.isLoaded && loadedPath == model.path) {
                _state.value = LlmRuntimeState.READY
                return
            }
            if (engine.isLoaded) {
                engine.unload()
                loadedPath = null
            }
            _state.value = LlmRuntimeState.LOADING
            try {
                engine.load(model.path, nCtx = 4096, nThreads = 4)
                loadedPath = model.path
                _state.value = LlmRuntimeState.READY
            } catch (error: Throwable) {
                loadedPath = null
                _state.value = LlmRuntimeState.ERROR
                throw LlmLoadException(error.message ?: "Falha ao carregar o modelo", error)
            }
        }
    }

    fun isModelLoaded(): Boolean = engine.isLoaded

    suspend fun unload() {
        loadMutex.withLock {
            idleJob?.cancel()
            if (engine.isLoaded) {
                _state.value = LlmRuntimeState.UNLOADING
                engine.cancel()
                engine.unload()
            }
            loadedPath = null
            _state.value = LlmRuntimeState.NOT_CONFIGURED
        }
    }

    fun onCriticalMemory() {
        cancelRequested = true
        scope.launch {
            runCatching {
                engine.cancel()
                unload()
            }
        }
    }

    private fun scheduleIdleUnload() {
        idleJob?.cancel()
        idleJob = scope.launch {
            delay(IDLE_UNLOAD_MS)
            if (!generationMutex.isLocked) {
                runCatching { unload() }
            }
        }
    }

    private companion object {
        const val MIN_INTERVAL_MS = 15_000L
        const val IDLE_UNLOAD_MS = 120_000L
    }
}
