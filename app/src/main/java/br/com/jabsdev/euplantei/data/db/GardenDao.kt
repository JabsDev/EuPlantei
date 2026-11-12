package br.com.jabsdev.euplantei.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface GardenDao {
    @Query("SELECT * FROM garden_plants ORDER BY nextWateringAt ASC, name ASC")
    fun observePlants(): Flow<List<GardenPlantEntity>>

    @Query("SELECT * FROM garden_plants WHERE id = :id LIMIT 1")
    fun observePlant(id: Long): Flow<GardenPlantEntity?>

    @Query("SELECT * FROM garden_plants WHERE id = :id LIMIT 1")
    suspend fun getPlant(id: Long): GardenPlantEntity?

    @Query("SELECT * FROM garden_plants ORDER BY nextWateringAt ASC, name ASC")
    suspend fun getAllPlants(): List<GardenPlantEntity>

    @Query("SELECT * FROM garden_plants WHERE nextWateringAt <= :now ORDER BY nextWateringAt ASC")
    suspend fun getDuePlants(now: Long): List<GardenPlantEntity>

    @Insert
    suspend fun insertPlant(plant: GardenPlantEntity): Long

    @Update
    suspend fun updatePlant(plant: GardenPlantEntity)

    @Query("DELETE FROM garden_plants WHERE id = :id")
    suspend fun deletePlant(id: Long)

    @Query("SELECT * FROM garden_photos WHERE plantId = :plantId ORDER BY capturedAt ASC")
    fun observePhotos(plantId: Long): Flow<List<GardenPhotoEntity>>

    @Query("SELECT * FROM garden_photos WHERE plantId = :plantId ORDER BY capturedAt ASC")
    suspend fun getPhotos(plantId: Long): List<GardenPhotoEntity>

    @Insert
    suspend fun insertPhoto(photo: GardenPhotoEntity): Long

    @Query("DELETE FROM garden_photos WHERE plantId = :plantId")
    suspend fun deletePhotos(plantId: Long)
}
