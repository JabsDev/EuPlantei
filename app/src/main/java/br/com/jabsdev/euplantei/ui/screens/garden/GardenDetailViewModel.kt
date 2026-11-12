package br.com.jabsdev.euplantei.ui.screens.garden

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import br.com.jabsdev.euplantei.AppContainer
import br.com.jabsdev.euplantei.data.db.GardenPhotoEntity
import br.com.jabsdev.euplantei.data.db.GardenPlantEntity
import br.com.jabsdev.euplantei.domain.model.PlantProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class GardenDetailViewModel(
    private val container: AppContainer,
    private val plantId: Long,
) : ViewModel() {

    private val repository = container.gardenRepository

    val plant: StateFlow<GardenPlantEntity?> = repository.plant(plantId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val photos: StateFlow<List<GardenPhotoEntity>> = repository.photos(plantId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _profile = MutableStateFlow<PlantProfile?>(null)
    val profile: StateFlow<PlantProfile?> = _profile.asStateFlow()

    private val _weather = MutableStateFlow(GardenWeatherState(loading = true))
    val weather: StateFlow<GardenWeatherState> = _weather.asStateFlow()

    private val _deleted = MutableStateFlow(false)
    val deleted: StateFlow<Boolean> = _deleted.asStateFlow()

    init {
        viewModelScope.launch {
            val current = repository.plantOnce(plantId) ?: return@launch
            _profile.value = repository.profileFor(current.speciesId, current.species)
        }
        refreshWeather()
    }

    fun refreshWeather() {
        viewModelScope.launch {
            _weather.value = GardenWeatherState(loading = true)
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

    fun water() {
        viewModelScope.launch { repository.waterPlant(plantId) }
    }

    fun updateInterval(deltaDays: Int) {
        val current = plant.value ?: return
        viewModelScope.launch {
            repository.updateWateringInterval(
                plantId,
                current.wateringIntervalDays + deltaDays,
            )
        }
    }

    fun useRecommendedInterval(days: Int) {
        viewModelScope.launch {
            repository.updateWateringInterval(plantId, days)
        }
    }

    /** Chamado pela rota de câmera; só retorna depois de copiar a imagem. */
    suspend fun addPhoto(sourcePath: String): Boolean = runCatching {
        repository.addPhoto(plantId, sourcePath) != null
    }.getOrDefault(false)

    fun delete() {
        viewModelScope.launch {
            repository.deletePlant(plantId)
            _deleted.value = true
        }
    }

    companion object {
        fun factory(container: AppContainer, plantId: Long): ViewModelProvider.Factory =
            viewModelFactory {
                initializer { GardenDetailViewModel(container, plantId) }
            }
    }
}
