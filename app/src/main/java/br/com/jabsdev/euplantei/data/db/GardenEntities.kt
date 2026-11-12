package br.com.jabsdev.euplantei.data.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "garden_plants",
    indices = [Index("nextWateringAt")],
)
data class GardenPlantEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val species: String,
    val speciesId: String,
    val coverImagePath: String,
    val addedAt: Long,
    val wateringIntervalDays: Int = 7,
    val lastWateredAt: Long? = null,
    val nextWateringAt: Long,
)

@Entity(
    tableName = "garden_photos",
    foreignKeys = [
        ForeignKey(
            entity = GardenPlantEntity::class,
            parentColumns = ["id"],
            childColumns = ["plantId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("plantId"), Index("capturedAt")],
)
data class GardenPhotoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val plantId: Long,
    val imagePath: String,
    val capturedAt: Long,
    val note: String = "",
)
