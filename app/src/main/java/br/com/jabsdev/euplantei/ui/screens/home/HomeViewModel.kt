package br.com.jabsdev.euplantei.ui.screens.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import br.com.jabsdev.euplantei.AppContainer
import br.com.jabsdev.euplantei.data.db.ConsultationEntity
import br.com.jabsdev.euplantei.data.prefs.CachedWeather
import br.com.jabsdev.euplantei.domain.model.ConsultationDraft
import br.com.jabsdev.euplantei.domain.model.ModelInfo
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class HomeUiState(
    val recent: ConsultationEntity? = null,
    val recentWeather: CachedWeather? = null,
    val modelInfo: ModelInfo? = null,
    val online: Boolean = true,
    val draft: ConsultationDraft? = null,
)

class HomeViewModel(container: AppContainer) : ViewModel() {
    val ui: StateFlow<HomeUiState> = combine(
        container.consultationRepository.observeAll().map { it.firstOrNull() },
        container.weatherRepository.observeCached(),
        container.modelRepository.observeModel(),
        container.networkMonitor.online,
        container.settingsRepository.consultationDraft,
    ) { recent, weather, model, online, draft ->
        HomeUiState(
            recent = recent,
            recentWeather = weather,
            modelInfo = model,
            online = online,
            draft = draft,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        HomeUiState(),
    )

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { HomeViewModel(container) }
        }
    }
}
