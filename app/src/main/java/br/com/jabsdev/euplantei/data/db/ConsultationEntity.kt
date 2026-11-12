package br.com.jabsdev.euplantei.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "consultations",
    indices = [Index("createdAt"), Index("species")],
)
data class ConsultationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAt: Long,
    val imagePath: String,
    val species: String,
    val condition: String,
    val confidencePercent: Int,
    val manual: Boolean,
    val symptom: String,
    val question: String,
    val city: String?,
    val weatherJson: String?,
    val advice: String,
)
