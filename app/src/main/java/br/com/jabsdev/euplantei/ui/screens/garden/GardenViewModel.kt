package br.com.jabsdev.euplantei.ui.screens.garden

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import br.com.jabsdev.euplantei.AppContainer
import br.com.jabsdev.euplantei.data.db.GardenPlantEntity
import br.com.jabsdev.euplantei.data.prefs.AppSettings
import br.com.jabsdev.euplantei.domain.model.WeatherContext
import br.com.jabsdev.euplantei.service.GardenReminders
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class GardenWeatherState(
    val weather: WeatherContext? = null,
    val fromCache: Boolean = false,
    val loading: Boolean = false,
)

class GardenViewModel(private val container: AppContainer) : ViewModel() {

    val plants: StateFlow<List<GardenPlantEntity>> = container.gardenRepository.plants
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val settings: StateFlow<AppSettings> = container.settingsRepository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    private val _weather = MutableStateFlow(GardenWeatherState(loading = true))
    val weather: StateFlow<GardenWeatherState> = _weather.asStateFlow()

    init {
        refreshWeather()
    }

    fun refreshWeather() {
        viewModelScope.launch {
            _weather.update { it.copy(loading = true) }
            val result = runCatching {
                container.weatherRepository.getWeather(forceRefresh = false)
            }.getOrNull()
            _weather.value = GardenWeatherState(
                weather = result?.weather,
                fromCache = result?.fromCache == true,
                loading = false,
            )
        }
    }

    fun water(id: Long) {
        viewModelScope.launch { container.gardenRepository.waterPlant(id) }
    }

    fun delete(id: Long) {
        viewModelScope.launch { container.gardenRepository.deletePlant(id) }
    }

    fun setRemindersEnabled(enabled: Boolean) {
        viewModelScope.launch {
            container.settingsRepository.setGardenRemindersEnabled(enabled)
            if (enabled) {
                GardenReminders.schedule(container.context)
            } else {
                GardenReminders.cancel(container.context)
            }
        }
    }

    companion object {
        fun factory(container: AppContainer): ViewModelProvider.Factory = viewModelFactory {
            initializer { GardenViewModel(container) }
        }
    }
}
