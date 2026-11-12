package br.com.jabsdev.euplantei.ui.screens.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import br.com.jabsdev.euplantei.AppContainer
import br.com.jabsdev.euplantei.R
import br.com.jabsdev.euplantei.data.prefs.AppSettings
import br.com.jabsdev.euplantei.data.repo.DownloadFailure
import br.com.jabsdev.euplantei.data.repo.ModelDownloadState
import br.com.jabsdev.euplantei.domain.model.LlmRuntimeState
import br.com.jabsdev.euplantei.domain.model.ModelInfo
import br.com.jabsdev.euplantei.domain.model.ModelSource
import br.com.jabsdev.euplantei.domain.model.TemperatureUnit
import br.com.jabsdev.euplantei.domain.model.ThemeMode
import br.com.jabsdev.euplantei.service.GardenReminders
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val settings: AppSettings = AppSettings(),
    val modelInfo: ModelInfo? = null,
    val downloadState: ModelDownloadState = ModelDownloadState.Idle,
    val llmState: LlmRuntimeState = LlmRuntimeState.NOT_CONFIGURED,
    val online: Boolean = true,
    val importing: Boolean = false,
    val batteryTemperatureC: Float? = null,
)

class SettingsViewModel(private val container: AppContainer) : ViewModel() {
    private val _importing = MutableStateFlow(false)
    private val _messages = MutableSharedFlow<Int>(extraBufferCapacity = 8)
    val messages: SharedFlow<Int> = _messages

    val modelSource: StateFlow<ModelSource?> = container.settingsRepository.modelSource
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private data class BaseState(
        val settings: AppSettings,
        val modelInfo: ModelInfo?,
        val downloadState: ModelDownloadState,
        val llmState: LlmRuntimeState,
        val online: Boolean,
    )

    val ui: StateFlow<SettingsUiState> = combine(
        combine(
            container.settingsRepository.settings,
            container.modelRepository.observeModel(),
            container.modelRepository.downloadState,
            container.llmManager.state,
            container.networkMonitor.online,
        ) { settings, modelInfo, downloadState, llmState, online ->
            BaseState(settings, modelInfo, downloadState, llmState, online)
        },
        _importing,
        container.thermalMonitor.temperatureC,
    ) { base, importing, temperature ->
        SettingsUiState(
            settings = base.settings,
            modelInfo = base.modelInfo,
            downloadState = base.downloadState,
            llmState = base.llmState,
            online = base.online,
            importing = importing,
            batteryTemperatureC = temperature,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        SettingsUiState(),
    )

    fun setTheme(mode: ThemeMode) {
        viewModelScope.launch { container.settingsRepository.setThemeMode(mode) }
    }

    fun setUnit(unit: TemperatureUnit) {
        viewModelScope.launch { container.settingsRepository.setTemperatureUnit(unit) }
    }

    fun setUseGeocoder(enabled: Boolean) {
        viewModelScope.launch { container.settingsRepository.setUseGeocoder(enabled) }
    }

    fun setGardenReminders(enabled: Boolean) {
        viewModelScope.launch {
            container.settingsRepository.setGardenRemindersEnabled(enabled)
            if (enabled) {
                GardenReminders.schedule(container.context)
            } else {
                GardenReminders.cancel(container.context)
            }
        }
    }

    fun startDownload() {
        container.modelRepository.startDownload()
    }

    fun cancelDownload() {
        container.modelRepository.cancelDownload()
    }

    fun importModel(uri: Uri) {
        if (_importing.value) return
        viewModelScope.launch {
            container.modelRepository.cancelDownload()
            _importing.value = true
            val result = container.modelRepository.importModel(uri)
            _importing.value = false
            _messages.emit(
                if (result.isSuccess) {
                    R.string.settings_model_import_success
                } else {
                    R.string.settings_model_import_failed
                },
            )
        }
    }

    fun deleteModel() {
        viewModelScope.launch {
            container.modelRepository.cancelDownload()
            runCatching { container.llmManager.unload() }
            container.modelRepository.deleteActiveModel()
            _messages.emit(R.string.settings_model_deleted)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            container.consultationRepository.clearAll()
            container.settingsRepository.clearDraft()
            _messages.emit(R.string.settings_history_cleared)
        }
    }

    fun saveSource(url: String, sha: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch {
            val result = runCatching {
                container.modelRepository.updateSource(url.trim(), sha.trim())
            }
            if (result.isSuccess) {
                _messages.emit(R.string.settings_model_source_saved)
                onResult(true)
            } else {
                _messages.emit(R.string.settings_model_source_invalid)
                onResult(false)
            }
        }
    }

    fun failureMessage(failure: DownloadFailure, httpCode: Int?): Int = when (failure) {
        DownloadFailure.OFFLINE -> R.string.settings_model_download_offline
        DownloadFailure.NETWORK -> R.string.error_network
        DownloadFailure.HTTP -> R.string.settings_model_download_http
        DownloadFailure.INVALID_GGUF -> R.string.settings_model_download_invalid
        DownloadFailure.SHA_MISMATCH -> R.string.settings_model_download_sha
        DownloadFailure.NO_SPACE -> R.string.settings_model_download_space
        DownloadFailure.IO -> R.string.settings_model_download_io
        DownloadFailure.CANCELLED -> R.string.settings_model_download_cancelled
        DownloadFailure.INVALID_URL -> R.string.settings_model_source_invalid
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { SettingsViewModel(container) }
        }
    }
}
