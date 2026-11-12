package br.com.jabsdev.euplantei.domain

import br.com.jabsdev.euplantei.domain.model.WeatherContext
import kotlin.math.max

/**
 * Traduz o clima do dia em avisos práticos para o jardim. A regra é
 * conservadora: só alerta quando há sinal claro no Open-Meteo, para não
 * transformar o lembrete diário em ruído.
 */
enum class GardenAlertKind {
    RAIN,
    HEAT,
    DRY_AIR,
    COLD,
    WIND,
}

data class GardenWeatherAlert(
    val kind: GardenAlertKind,
    val precipitationProbability: Int? = null,
    val temperatureC: Double? = null,
    val humidityPercent: Int? = null,
    val windSpeedMs: Double? = null,
)

object GardenWeatherAdvisor {
    private const val RAIN_MM = 0.5
    private const val RAIN_PROBABILITY = 60
    private const val HEAT_C = 31.0
    private const val DRY_HUMIDITY = 40
    private const val DRY_TEMPERATURE_C = 24.0
    private const val COLD_C = 12.0
    private const val WIND_MS = 8.0
    private const val MAX_WEATHER_AGE_MS = 8L * 60L * 60L * 1000L

    fun alerts(weather: WeatherContext?): List<GardenWeatherAlert> {
        if (weather == null || !weather.hasAnyData) return emptyList()
        // Evita repetir um aviso de chuva de uma previsão antiga do cache.
        if (System.currentTimeMillis() - weather.timestampMillis > MAX_WEATHER_AGE_MS) {
            return emptyList()
        }

        val alerts = mutableListOf<GardenWeatherAlert>()
        val rainMm = weather.precipitationMm ?: 0.0
        val rainProbability = weather.precipitationProbabilityPercent
        if (rainMm >= RAIN_MM ||
            (rainProbability ?: 0) >= RAIN_PROBABILITY ||
            isRainCode(weather.conditionCode)
        ) {
            alerts += GardenWeatherAlert(
                kind = GardenAlertKind.RAIN,
                precipitationProbability = rainProbability,
            )
        }

        val feelsLike = max(
            weather.temperatureC ?: Double.NEGATIVE_INFINITY,
            weather.apparentTemperatureC ?: Double.NEGATIVE_INFINITY,
        )
        if (feelsLike >= HEAT_C) {
            alerts += GardenWeatherAlert(
                kind = GardenAlertKind.HEAT,
                temperatureC = feelsLike.takeIf { it.isFinite() },
            )
        }

        val humidity = weather.humidityPercent
        val temperature = weather.temperatureC
        if (humidity != null && humidity <= DRY_HUMIDITY &&
            temperature != null && temperature >= DRY_TEMPERATURE_C
        ) {
            alerts += GardenWeatherAlert(
                kind = GardenAlertKind.DRY_AIR,
                humidityPercent = humidity,
            )
        }

        if (temperature != null && temperature <= COLD_C) {
            alerts += GardenWeatherAlert(
                kind = GardenAlertKind.COLD,
                temperatureC = temperature,
            )
        }

        val wind = weather.windSpeedMs
        if (wind != null && wind >= WIND_MS) {
            alerts += GardenWeatherAlert(
                kind = GardenAlertKind.WIND,
                windSpeedMs = wind,
            )
        }

        return alerts
    }

    private fun isRainCode(code: Int?): Boolean = when (code) {
        null -> false
        in 51..67, in 80..82, in 95..99 -> true
        else -> false
    }
}
