package br.com.jabsdev.euplantei.domain

import br.com.jabsdev.euplantei.data.db.GardenPlantEntity
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

const val DAY_MILLIS = 24L * 60L * 60L * 1000L

enum class WateringStatus {
    OVERDUE,
    TODAY,
    TOMORROW,
    LATER,
}

fun GardenPlantEntity.daysUntilWatering(now: Long = System.currentTimeMillis()): Long {
    val zone = ZoneId.systemDefault()
    val target = Instant.ofEpochMilli(nextWateringAt).atZone(zone).toLocalDate()
    val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
    return ChronoUnit.DAYS.between(today, target)
}

fun GardenPlantEntity.wateringStatus(now: Long = System.currentTimeMillis()): WateringStatus =
    when (val days = daysUntilWatering(now)) {
        in Long.MIN_VALUE..-1L -> WateringStatus.OVERDUE
        0L -> WateringStatus.TODAY
        1L -> WateringStatus.TOMORROW
        else -> WateringStatus.LATER
    }
