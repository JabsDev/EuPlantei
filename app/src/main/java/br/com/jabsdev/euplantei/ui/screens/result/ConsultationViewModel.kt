package br.com.jabsdev.euplantei.ui.screens.result

import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import br.com.jabsdev.euplantei.AppContainer
import br.com.jabsdev.euplantei.R
import br.com.jabsdev.euplantei.data.repo.ConsultationRepository
import br.com.jabsdev.euplantei.data.repo.NewConsultation
import br.com.jabsdev.euplantei.domain.IdentificationSource
import br.com.jabsdev.euplantei.domain.PromptBuilder
import br.com.jabsdev.euplantei.domain.PromptInput
import br.com.jabsdev.euplantei.domain.WeatherCodeMapper
import br.com.jabsdev.euplantei.domain.model.ConsultationDraft
import br.com.jabsdev.euplantei.domain.model.ConsultationFailureKind
import br.com.jabsdev.euplantei.domain.model.ConfidenceRules
import br.com.jabsdev.euplantei.domain.model.DraftStage
import br.com.jabsdev.euplantei.domain.model.LlmParams
import br.com.jabsdev.euplantei.domain.model.ManualWeatherInput
import br.com.jabsdev.euplantei.domain.model.PlantPrediction
import br.com.jabsdev.euplantei.domain.model.PlantProfile
import br.com.jabsdev.euplantei.domain.model.PredictionSource
import br.com.jabsdev.euplantei.domain.model.RetrievalResult
import br.com.jabsdev.euplantei.domain.model.WeatherContext
import br.com.jabsdev.euplantei.domain.model.WeatherFailureReason
import br.com.jabsdev.euplantei.ml.LlmBusyException
import br.com.jabsdev.euplantei.ml.LlmLoadException
import br.com.jabsdev.euplantei.ml.ModelNotConfiguredException
import br.com.jabsdev.euplantei.ml.RateLimitedException
import br.com.jabsdev.euplantei.ml.ThermalBlockedException
import br.com.jabsdev.euplantei.util.ImageUtils
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.Dispatchers
import java.io.File
import java.time.LocalDateTime

sealed interface ConsultationStage {
    data object Idle : ConsultationStage
    data object Classifying : ConsultationStage
    data class AwaitingConfirmation(val predictions: List<PlantPrediction>) : ConsultationStage
    data object AwaitingSymptoms : ConsultationStage
    data object AwaitingManual : ConsultationStage
    data object FetchingWeather : ConsultationStage
    data class Generating(val partial: String) : ConsultationStage
    data class Success(val advice: String) : ConsultationStage
    data class Error(
        val failure: ConsultationFailureKind,
        val partial: String = "",
        val temperatureC: Float? = null,
        val waitSeconds: Int? = null,
    ) : ConsultationStage
}

data class ConsultationUiState(
    val imagePath: String = "",
    val stage: ConsultationStage = ConsultationStage.Idle,
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
    val elapsedSeconds: Int = 0,
    val batteryTemperatureC: Float? = null,
    val errorMessageRes: Int? = null,
) {
    val isGenerating: Boolean get() = stage is ConsultationStage.Generating
    val isBusy: Boolean
        get() = stage is ConsultationStage.Classifying ||
            stage is ConsultationStage.FetchingWeather ||
            stage is ConsultationStage.Generating
    val canShare: Boolean get() = advice.isNotBlank()
    val hasWeather: Boolean get() = weather?.hasAnyData == true
}

class ConsultationViewModel(
    private val container: AppContainer,
    private val imagePath: String,
) : ViewModel() {
    private val settings = container.settingsRepository
    private val weatherRepository = container.weatherRepository
    private val knowledgeRepository = container.knowledgeRepository
    private val consultationRepository = container.consultationRepository
    private val llmManager = container.llmManager

    private val _ui = MutableStateFlow(ConsultationUiState(imagePath = imagePath))
    val ui: StateFlow<ConsultationUiState> = _ui.asStateFlow()

    private var timerJob: Job? = null
    private var draftJob: Job? = null
    private var generationStartedAt = 0L
    @Volatile
    private var cancelRequested = false
    private val locationPermissionCheck = CompletableDeferred<Boolean>()

    init {
        viewModelScope.launch {
            llmManager.thermalMonitor.temperatureC.collect { temperature ->
                _ui.update { it.copy(batteryTemperatureC = temperature) }
            }
        }
        viewModelScope.launch {
            restoreOrStart()
        }
    }

    fun onLocationPermissionResult(granted: Boolean) {
        if (!locationPermissionCheck.isCompleted) {
            locationPermissionCheck.complete(granted)
        }
    }

    fun confirmPrediction(prediction: PlantPrediction) {
        if (_ui.value.isBusy) return
        if (prediction.source == PredictionSource.HOUSEPLANT) {
            val confidencePercent = (prediction.confidence * 100).toInt().coerceIn(0, 100)
            _ui.update {
                it.copy(
                    stage = ConsultationStage.AwaitingSymptoms,
                    selected = prediction,
                    species = prediction.species,
                    condition = prediction.condition,
                    confidencePercent = confidencePercent,
                    manual = false,
                    symptom = "",
                    topPredictions = emptyList(),
                )
            }
            persistDraft(DraftStage.AWAITING_SYMPTOMS)
        } else {
            proceedWithPrediction(prediction)
        }
    }

    fun submitSymptoms(symptomText: String, healthy: Boolean) {
        if (_ui.value.isBusy) return
        val selected = _ui.value.selected ?: return
        val symptom = if (healthy) {
            "Planta aparentemente saudável; quero orientações de cuidado."
        } else {
            symptomText.trim().ifBlank { "Sem descrição detalhada; quero orientações de cuidado." }
        }
        _ui.update {
            it.copy(
                stage = ConsultationStage.FetchingWeather,
                condition = if (healthy) "Planta saudável" else "Sinais observados pelo usuário",
                symptom = symptom,
                manual = selected.source == PredictionSource.MANUAL,
                advice = "",
                savedId = null,
                saveFailed = false,
            )
        }
        persistDraft(DraftStage.FETCHING_WEATHER)
        viewModelScope.launch { fetchWeatherAndGenerate() }
    }

    fun chooseManual() {
        if (_ui.value.isBusy) return
        _ui.update {
            it.copy(
                stage = ConsultationStage.AwaitingManual,
                topPredictions = emptyList(),
            )
        }
        persistDraft(DraftStage.AWAITING_MANUAL)
    }

    fun submitManual(species: String, symptom: String, question: String) {
        if (_ui.value.isBusy) return
        val cleanSpecies = species.trim()
        val cleanSymptom = symptom.trim()
        if (cleanSpecies.isBlank() || cleanSymptom.isBlank()) return
        val prediction = PlantPrediction(
            rawLabel = "manual",
            species = cleanSpecies,
            speciesId = "",
            condition = container.context.getString(R.string.result_manual_condition_value),
            healthy = false,
            confidence = 1f,
        )
        _ui.update {
            it.copy(
                manual = true,
                symptom = cleanSymptom,
                question = question.trim(),
                topPredictions = emptyList(),
            )
        }
        proceedWithPrediction(prediction, manual = true)
    }

    fun applyManualWeather(input: ManualWeatherInput) {
        viewModelScope.launch {
            val weather = runCatching { weatherRepository.saveManual(input) }.getOrNull() ?: return@launch
            _ui.update {
                it.copy(
                    weather = weather,
                    weatherFailure = null,
                    weatherFromCache = false,
                    weatherFetchedAtMillis = System.currentTimeMillis(),
                )
            }
            persistDraft(stageForDraft(_ui.value.stage))
            if (_ui.value.advice.isNotBlank() || _ui.value.stage is ConsultationStage.Error) {
                regenerate()
            }
        }
    }

    fun retry() {
        when (val stage = _ui.value.stage) {
            is ConsultationStage.Error -> when (stage.failure) {
                ConsultationFailureKind.CLASSIFICATION,
                ConsultationFailureKind.IMAGE,
                -> classify()

                ConsultationFailureKind.MODEL_MISSING -> regenerate()
                else -> regenerate()
            }
            else -> Unit
        }
    }

    fun cancelGeneration() {
        cancelRequested = true
        llmManager.cancel()
        timerJob?.cancel()
    }

    fun regenerate() {
        if (_ui.value.isBusy) return
        if (_ui.value.species.isBlank()) {
            classify()
            return
        }
        viewModelScope.launch {
            preparePromptAndGenerate(resetAdvice = true)
        }
    }

    fun saveToHistory() {
        viewModelScope.launch { saveSuccessToHistory() }
    }

    fun consumeErrorMessage() {
        _ui.update { it.copy(errorMessageRes = null) }
    }

    private suspend fun restoreOrStart() {
        val draft = runCatching { settings.loadDraft() }.getOrNull()
        val effectivePath = imagePath.ifBlank { draft?.imagePath.orEmpty() }
        if (effectivePath.isBlank()) {
            _ui.update { it.copy(stage = ConsultationStage.Error(ConsultationFailureKind.IMAGE)) }
            return
        }
        if (draft != null && draft.imagePath == effectivePath) {
            _ui.value = draft.toUiState()
            when (draft.stage) {
                DraftStage.CLASSIFYING -> classify()
                DraftStage.SUCCESS -> {
                    if (draft.savedId == null) saveSuccessToHistory()
                }
                DraftStage.GENERATING -> {
                    _ui.update {
                        it.copy(
                            stage = ConsultationStage.Error(
                                failure = ConsultationFailureKind.CANCELLED,
                                partial = draft.advice,
                            ),
                        )
                    }
                }
                DraftStage.ERROR -> Unit
                DraftStage.FETCHING_WEATHER -> {
                    if (draft.weather != null) {
                        preparePromptAndGenerate(resetAdvice = true)
                    } else if (draft.species.isNotBlank()) {
                        fetchWeatherAndGenerate()
                    } else {
                        classify()
                    }
                }
                DraftStage.AWAITING_CONFIRMATION,
                DraftStage.AWAITING_SYMPTOMS,
                DraftStage.AWAITING_MANUAL,
                -> Unit
            }
        } else {
            _ui.update { it.copy(imagePath = effectivePath) }
            classify()
        }
    }

    private fun classify() {
        val currentPath = _ui.value.imagePath
        if (currentPath.isBlank() || !File(currentPath).exists()) {
            _ui.update {
                it.copy(
                    stage = ConsultationStage.Error(ConsultationFailureKind.IMAGE),
                    errorMessageRes = R.string.error_image_decode,
                )
            }
            return
        }
        timerJob?.cancel()
        _ui.update {
            it.copy(
                imagePath = currentPath,
                stage = ConsultationStage.Classifying,
                topPredictions = emptyList(),
                selected = null,
                species = "",
                condition = "",
                confidencePercent = 0,
                manual = false,
                symptom = "",
                question = "",
                advice = "",
                savedId = null,
                saveFailed = false,
                elapsedSeconds = 0,
            )
        }
        persistDraft(DraftStage.CLASSIFYING)
        viewModelScope.launch {
            try {
                val bitmap = withContext(Dispatchers.IO) { ImageUtils.classifierBitmap(currentPath) }
                val predictions = try {
                    container.visionClassifier.classify(bitmap)
                } finally {
                    if (!bitmap.isRecycled) bitmap.recycle()
                }
                val sorted = predictions.sortedByDescending { it.confidence }.take(3)
                if (sorted.isEmpty()) {
                    _ui.update {
                        it.copy(
                            stage = ConsultationStage.AwaitingManual,
                            topPredictions = emptyList(),
                        )
                    }
                    persistDraft(DraftStage.AWAITING_MANUAL)
                    return@launch
                }
                val top = sorted.first()
                _ui.update { it.copy(topPredictions = sorted, batteryTemperatureC = it.batteryTemperatureC) }
                when {
                    // A identificação de planta de interior é sempre confirmada:
                    // o usuário escolhe entre espécies antes de gerar o cuidado.
                    sorted.any { it.source == PredictionSource.HOUSEPLANT } -> {
                        _ui.update { it.copy(stage = ConsultationStage.AwaitingConfirmation(sorted)) }
                        persistDraft(DraftStage.AWAITING_CONFIRMATION)
                    }
                    ConfidenceRules.shouldUseAutomatically(top.confidence) -> proceedWithPrediction(top)
                    ConfidenceRules.needsConfirmation(top.confidence) -> {
                        _ui.update { it.copy(stage = ConsultationStage.AwaitingConfirmation(sorted)) }
                        persistDraft(DraftStage.AWAITING_CONFIRMATION)
                    }
                    else -> {
                        _ui.update { it.copy(stage = ConsultationStage.AwaitingManual) }
                        persistDraft(DraftStage.AWAITING_MANUAL)
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Throwable) {
                _ui.update {
                    it.copy(
                        stage = ConsultationStage.Error(
                            failure = ConsultationFailureKind.CLASSIFICATION,
                            partial = "",
                        ),
                        errorMessageRes = R.string.error_image_decode,
                    )
                }
                persistDraft(DraftStage.ERROR)
            }
        }
    }

    private fun proceedWithPrediction(prediction: PlantPrediction, manual: Boolean = false) {
        val confidencePercent = if (manual) 100 else (prediction.confidence * 100).toInt().coerceIn(0, 100)
        val current = _ui.value
        val symptom = when {
            current.symptom.isNotBlank() -> current.symptom
            manual -> current.symptom
            prediction.healthy -> "Planta aparentemente saudável; quero orientações de cuidado."
            else -> container.context.getString(R.string.result_auto_symptom, prediction.condition)
        }
        _ui.update {
            it.copy(
                stage = ConsultationStage.FetchingWeather,
                selected = prediction,
                species = prediction.species,
                condition = prediction.condition,
                confidencePercent = confidencePercent,
                manual = manual,
                symptom = symptom,
                advice = "",
                savedId = null,
                saveFailed = false,
                topPredictions = it.topPredictions,
            )
        }
        persistDraft(DraftStage.FETCHING_WEATHER)
        viewModelScope.launch { fetchWeatherAndGenerate() }
    }

    private suspend fun fetchWeatherAndGenerate() {
        // A tela de resultado pede a permissão COARSE assim que abre. Esperamos a
        // resposta para não consultar o clima antes de o usuário decidir.
        withTimeoutOrNull(LOCATION_DECISION_TIMEOUT_MS) {
            locationPermissionCheck.await()
        }
        val result = runCatching { weatherRepository.getWeather(forceRefresh = false) }.getOrNull()
        _ui.update {
            it.copy(
                weather = result?.weather,
                weatherFailure = result?.failureReason,
                weatherFromCache = result?.fromCache == true,
                weatherFetchedAtMillis = result?.fetchedAtMillis ?: System.currentTimeMillis(),
            )
        }
        persistDraft(DraftStage.FETCHING_WEATHER)
        preparePromptAndGenerate(resetAdvice = true)
    }

    private suspend fun preparePromptAndGenerate(resetAdvice: Boolean) {
        val state = _ui.value
        if (state.species.isBlank()) {
            classify()
            return
        }
        val weather = state.weather
        val conditionDescription = weather?.let {
            container.context.getString(WeatherCodeMapper.descriptionRes(it.conditionCode))
        }
        val retrieval = runCatching {
            knowledgeRepository.retrieve(state.species, state.symptom)
        }.getOrDefault(RetrievalResult())
        _ui.update { it.copy(careProfile = retrieval.profiles.firstOrNull()) }
        val knowledge = retrieval.toPromptText()
        val prompt = PromptBuilder.build(
            PromptInput(
                weather = weather,
                species = state.species,
                identification = if (state.manual) IdentificationSource.MANUAL else IdentificationSource.AUTOMATIC,
                confidencePercent = state.confidencePercent,
                symptom = state.symptom,
                question = state.question,
                now = LocalDateTime.now(),
                conditionDescription = conditionDescription,
            ),
            knowledge,
        )
        if (resetAdvice) {
            _ui.update { it.copy(advice = "") }
        }
        generateInternal(prompt)
    }

    private suspend fun generateInternal(prompt: String) {
        if (_ui.value.isGenerating) return
        val model = runCatching { container.modelRepository.activeModelOnce() }.getOrNull()
        if (model == null) {
            _ui.update {
                it.copy(
                    stage = ConsultationStage.Error(ConsultationFailureKind.MODEL_MISSING),
                    errorMessageRes = R.string.result_model_missing_body,
                )
            }
            persistDraft(DraftStage.ERROR)
            return
        }
        val temperature = llmManager.thermalMonitor.temperatureC.value ?: 0f
        if (temperature >= br.com.jabsdev.euplantei.util.ThermalMonitor.CRITICAL_THRESHOLD_C) {
            _ui.update {
                it.copy(
                    stage = ConsultationStage.Error(
                        failure = ConsultationFailureKind.THERMAL,
                        temperatureC = temperature,
                    ),
                    errorMessageRes = R.string.error_thermal,
                )
            }
            persistDraft(DraftStage.ERROR)
            return
        }

        cancelRequested = false
        generationStartedAt = SystemClock.elapsedRealtime()
        _ui.update {
            it.copy(
                stage = ConsultationStage.Generating(""),
                advice = "",
                elapsedSeconds = 0,
                savedId = null,
                saveFailed = false,
            )
        }
        persistDraft(DraftStage.GENERATING)
        startTimer()

        viewModelScope.launch {
            val builder = StringBuilder()
            var lastUiUpdate = 0L
            var lastDraftSave = 0L
            try {
                llmManager.generate(prompt, LlmParams()).collect { token ->
                    builder.append(token)
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastUiUpdate >= UI_UPDATE_INTERVAL_MS) {
                        lastUiUpdate = now
                        val partial = builder.toString()
                        _ui.update {
                            it.copy(
                                stage = ConsultationStage.Generating(partial),
                                advice = partial,
                            )
                        }
                    }
                    if (now - lastDraftSave >= DRAFT_SAVE_INTERVAL_MS) {
                        lastDraftSave = now
                        persistDraft(DraftStage.GENERATING)
                    }
                }
                timerJob?.cancel()
                val finalText = builder.toString().trim()
                if (!cancelRequested && finalText.isBlank()) {
                    _ui.update {
                        it.copy(
                            stage = ConsultationStage.Error(ConsultationFailureKind.GENERATION),
                            errorMessageRes = R.string.error_llm_generation,
                        )
                    }
                    persistDraft(DraftStage.ERROR)
                    return@launch
                }
                if (cancelRequested) {
                    _ui.update {
                        it.copy(
                            stage = ConsultationStage.Error(
                                failure = ConsultationFailureKind.CANCELLED,
                                partial = finalText,
                            ),
                            advice = finalText,
                        )
                    }
                    persistDraft(DraftStage.ERROR)
                } else {
                    _ui.update {
                        it.copy(
                            stage = ConsultationStage.Success(finalText),
                            advice = finalText,
                        )
                    }
                    persistDraft(DraftStage.SUCCESS)
                    saveSuccessToHistory()
                }
            } catch (error: CancellationException) {
                timerJob?.cancel()
                throw error
            } catch (error: Throwable) {
                timerJob?.cancel()
                val partial = builder.toString().trim()
                val failure = when (error) {
                    is ThermalBlockedException -> ConsultationFailureKind.THERMAL
                    is RateLimitedException -> ConsultationFailureKind.RATE_LIMITED
                    is LlmBusyException -> ConsultationFailureKind.BUSY
                    is ModelNotConfiguredException -> ConsultationFailureKind.MODEL_MISSING
                    is LlmLoadException -> ConsultationFailureKind.GENERATION
                    else -> ConsultationFailureKind.GENERATION
                }
                _ui.update {
                    it.copy(
                        stage = ConsultationStage.Error(
                            failure = failure,
                            partial = partial,
                            temperatureC = (error as? ThermalBlockedException)?.temperatureC,
                            waitSeconds = (error as? RateLimitedException)?.remainingMs?.let {
                                ((it + 999) / 1000).toInt()
                            },
                        ),
                        advice = partial,
                        errorMessageRes = when (failure) {
                            ConsultationFailureKind.MODEL_MISSING -> R.string.result_model_missing_body
                            ConsultationFailureKind.THERMAL -> R.string.error_thermal
                            ConsultationFailureKind.RATE_LIMITED -> R.string.error_rate_limit
                            ConsultationFailureKind.BUSY -> R.string.error_rate_limit
                            else -> R.string.error_llm_generation
                        },
                    )
                }
                persistDraft(DraftStage.ERROR)
            }
        }
    }

    private suspend fun saveSuccessToHistory() {
        val state = _ui.value
        if (state.advice.isBlank() || state.savedId != null) return
        if (!File(state.imagePath).exists()) {
            _ui.update { it.copy(saveFailed = true) }
            persistDraft(DraftStage.SUCCESS)
            return
        }
        val saved = runCatching {
            consultationRepository.save(
                NewConsultation(
                    imagePath = state.imagePath,
                    species = state.species,
                    condition = state.condition,
                    confidencePercent = state.confidencePercent,
                    manual = state.manual,
                    symptom = state.symptom,
                    question = state.question,
                    city = state.weather?.city,
                    weather = state.weather,
                    advice = state.advice,
                ),
            )
        }.getOrNull()
        if (saved != null) {
            _ui.update { it.copy(savedId = saved.id, saveFailed = false) }
        } else {
            _ui.update { it.copy(saveFailed = true) }
        }
        persistDraft(DraftStage.SUCCESS)
    }

    private fun startTimer() {
        timerJob?.cancel()
        timerJob = viewModelScope.launch {
            while (isActive) {
                delay(1000)
                val elapsed = ((SystemClock.elapsedRealtime() - generationStartedAt) / 1000).toInt()
                _ui.update { it.copy(elapsedSeconds = elapsed) }
            }
        }
    }

    private fun persistDraft(stage: DraftStage) {
        val snapshot = _ui.value
        val draft = snapshot.toDraft(stage)
        // Cancela a escrita anterior para que um rascunho antigo não sobrescreva
        // o estado mais recente (especialmente durante o streaming).
        draftJob?.cancel()
        draftJob = viewModelScope.launch { runCatching { settings.saveDraft(draft) } }
    }

    private fun stageForDraft(stage: ConsultationStage): DraftStage = when (stage) {
        ConsultationStage.Idle -> DraftStage.CLASSIFYING
        ConsultationStage.Classifying -> DraftStage.CLASSIFYING
        is ConsultationStage.AwaitingConfirmation -> DraftStage.AWAITING_CONFIRMATION
        ConsultationStage.AwaitingSymptoms -> DraftStage.AWAITING_SYMPTOMS
        ConsultationStage.AwaitingManual -> DraftStage.AWAITING_MANUAL
        ConsultationStage.FetchingWeather -> DraftStage.FETCHING_WEATHER
        is ConsultationStage.Generating -> DraftStage.GENERATING
        is ConsultationStage.Success -> DraftStage.SUCCESS
        is ConsultationStage.Error -> DraftStage.ERROR
    }

    private fun ConsultationUiState.toDraft(stage: DraftStage): ConsultationDraft {
        val failure = (this.stage as? ConsultationStage.Error)
        return ConsultationDraft(
            imagePath = imagePath,
            stage = stage,
            topPredictions = topPredictions,
            selected = selected,
            species = species,
            condition = condition,
            confidencePercent = confidencePercent,
            manual = manual,
            symptom = symptom,
            question = question,
            careProfile = careProfile,
            weather = weather,
            weatherFailure = weatherFailure,
            weatherFromCache = weatherFromCache,
            weatherFetchedAtMillis = weatherFetchedAtMillis,
            advice = advice,
            savedId = savedId,
            saveFailed = saveFailed,
            failureKind = failure?.failure,
            failureTemperatureC = failure?.temperatureC,
            failureWaitSeconds = failure?.waitSeconds,
            updatedAtMillis = System.currentTimeMillis(),
        )
    }

    private fun ConsultationDraft.toUiState(): ConsultationUiState {
        val stage = when (this.stage) {
            DraftStage.CLASSIFYING -> ConsultationStage.Classifying
            DraftStage.AWAITING_CONFIRMATION -> ConsultationStage.AwaitingConfirmation(topPredictions)
            DraftStage.AWAITING_SYMPTOMS -> ConsultationStage.AwaitingSymptoms
            DraftStage.AWAITING_MANUAL -> ConsultationStage.AwaitingManual
            DraftStage.FETCHING_WEATHER -> ConsultationStage.FetchingWeather
            DraftStage.GENERATING -> ConsultationStage.Generating(advice)
            DraftStage.SUCCESS -> ConsultationStage.Success(advice)
            DraftStage.ERROR -> ConsultationStage.Error(
                failure = failureKind ?: ConsultationFailureKind.UNKNOWN,
                partial = advice,
                temperatureC = failureTemperatureC,
                waitSeconds = failureWaitSeconds,
            )
        }
        return ConsultationUiState(
            imagePath = imagePath,
            stage = stage,
            topPredictions = topPredictions,
            selected = selected,
            species = species,
            condition = condition,
            confidencePercent = confidencePercent,
            manual = manual,
            symptom = symptom,
            question = question,
            careProfile = careProfile,
            weather = weather,
            weatherFailure = weatherFailure,
            weatherFromCache = weatherFromCache,
            weatherFetchedAtMillis = weatherFetchedAtMillis,
            advice = advice,
            savedId = savedId,
            saveFailed = saveFailed,
        )
    }

    override fun onCleared() {
        timerJob?.cancel()
        if (cancelRequested || _ui.value.isGenerating) {
            llmManager.cancel()
        }
        super.onCleared()
    }

    companion object {
        private const val UI_UPDATE_INTERVAL_MS = 33L
        private const val DRAFT_SAVE_INTERVAL_MS = 2_000L
        private const val LOCATION_DECISION_TIMEOUT_MS = 8_000L

        fun factory(container: AppContainer, imagePath: String): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { ConsultationViewModel(container, imagePath) }
            }
    }
}
