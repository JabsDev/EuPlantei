package br.com.jabsdev.euplantei.data.repo

import android.content.Context
import br.com.jabsdev.euplantei.data.db.AppDatabase
import br.com.jabsdev.euplantei.data.db.GardenPhotoEntity
import br.com.jabsdev.euplantei.data.db.GardenPlantEntity
import br.com.jabsdev.euplantei.domain.DAY_MILLIS
import br.com.jabsdev.euplantei.domain.model.PlantProfile
import br.com.jabsdev.euplantei.domain.model.WeatherContext
import br.com.jabsdev.euplantei.util.ImageUtils
import kotlinx.coroutines.flow.Flow

/**
 * Fonte única do jardim: plantas, fotos de acompanhamento e rega.
 * As imagens são copiadas para `filesDir/garden` para não dependerem do cache
 * de consultas (que pode ser limpo pelo sistema).
 */
class GardenRepository(
    context: Context,
    database: AppDatabase,
    private val weatherRepository: WeatherRepository,
    private val knowledgeRepository: KnowledgeRepository,
) {
    private val appContext = context.applicationContext
    private val dao = database.gardenDao()

    val plants: Flow<List<GardenPlantEntity>> = dao.observePlants()

    fun plant(id: Long): Flow<GardenPlantEntity?> = dao.observePlant(id)

    fun photos(plantId: Long): Flow<List<GardenPhotoEntity>> = dao.observePhotos(plantId)

    suspend fun plantsOnce(): List<GardenPlantEntity> = dao.getAllPlants()

    suspend fun duePlants(now: Long = System.currentTimeMillis()): List<GardenPlantEntity> =
        dao.getDuePlants(now)

    suspend fun plantOnce(id: Long): GardenPlantEntity? = dao.getPlant(id)

    suspend fun addPlant(
        name: String,
        species: String,
        speciesId: String,
        sourceImagePath: String,
        wateringIntervalDays: Int = DEFAULT_INTERVAL_DAYS,
        addedAt: Long = System.currentTimeMillis(),
    ): Long {
        val cleanName = name.trim().ifBlank { species.trim() }
        require(cleanName.isNotBlank()) { "Nome da planta obrigatório" }
        require(sourceImagePath.isNotBlank()) { "Foto da planta obrigatória" }
        val cover = ImageUtils.persistForGarden(appContext, sourceImagePath)
        val interval = wateringIntervalDays.coerceIn(MIN_INTERVAL_DAYS, MAX_INTERVAL_DAYS)
        return dao.insertPlant(
            GardenPlantEntity(
                name = cleanName,
                species = species.trim().ifBlank { cleanName },
                speciesId = speciesId.trim(),
                coverImagePath = cover.absolutePath,
                addedAt = addedAt,
                wateringIntervalDays = interval,
                lastWateredAt = null,
                nextWateringAt = addedAt + interval * DAY_MILLIS,
            ),
        )
    }

    suspend fun waterPlant(id: Long, now: Long = System.currentTimeMillis()): GardenPlantEntity? {
        val plant = dao.getPlant(id) ?: return null
        val watered = plant.copy(
            lastWateredAt = now,
            nextWateringAt = now + plant.wateringIntervalDays * DAY_MILLIS,
        )
        dao.updatePlant(watered)
        return watered
    }

    suspend fun updateWateringInterval(id: Long, days: Int): GardenPlantEntity? {
        val plant = dao.getPlant(id) ?: return null
        val interval = days.coerceIn(MIN_INTERVAL_DAYS, MAX_INTERVAL_DAYS)
        val base = plant.lastWateredAt ?: System.currentTimeMillis()
        val updated = plant.copy(
            wateringIntervalDays = interval,
            nextWateringAt = base + interval * DAY_MILLIS,
        )
        dao.updatePlant(updated)
        return updated
    }

    suspend fun addPhoto(
        plantId: Long,
        sourceImagePath: String,
        note: String = "",
        capturedAt: Long = System.currentTimeMillis(),
    ): Long? {
        if (dao.getPlant(plantId) == null) return null
        if (sourceImagePath.isBlank()) return null
        val file = ImageUtils.persistForGarden(appContext, sourceImagePath)
        return dao.insertPhoto(
            GardenPhotoEntity(
                plantId = plantId,
                imagePath = file.absolutePath,
                capturedAt = capturedAt,
                note = note,
            ),
        )
    }

    suspend fun deletePlant(id: Long) {
        val plant = dao.getPlant(id) ?: return
        val photos = dao.getPhotos(id)
        dao.deletePlant(id)
        ImageUtils.deleteQuietly(plant.coverImagePath)
        photos.forEach { ImageUtils.deleteQuietly(it.imagePath) }
    }

    /** Perfil de cuidado pelo id do modelo ou, na falta dele, pelo nome. */
    suspend fun profileFor(speciesId: String, species: String = ""): PlantProfile? {
        val base = runCatching { knowledgeRepository.load() }.getOrNull() ?: return null
        return base.plantas.firstOrNull { it.id.equals(speciesId, ignoreCase = true) }
            ?: base.plantas.firstOrNull { profile ->
                profile.nomes.any { it.equals(species, ignoreCase = true) }
            }
    }

    suspend fun todayWeather(): WeatherContext? =
        runCatching { weatherRepository.getWeather(forceRefresh = false) }
            .getOrNull()
            ?.weather

    companion object {
        const val DEFAULT_INTERVAL_DAYS = 7
        const val MIN_INTERVAL_DAYS = 1
        const val MAX_INTERVAL_DAYS = 60
    }
}
