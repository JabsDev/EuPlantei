package br.com.jabsdev.euplantei.domain.model

import kotlinx.serialization.Serializable

enum class WeatherSource {
    API,
    CACHE,
    MANUAL,
}

@Serializable
data class WeatherContext(
    val city: String = "",
    val latitude: Double? = null,
    val longitude: Double? = null,
    val temperatureC: Double? = null,
    val apparentTemperatureC: Double? = null,
    val humidityPercent: Int? = null,
    val conditionCode: Int? = null,
    val precipitationMm: Double? = null,
    val cloudCoverPercent: Int? = null,
    val windSpeedMs: Double? = null,
    val shortwaveRadiationWm2: Double? = null,
    val sunrise: String? = null,
    val sunset: String? = null,
    val uvIndexMax: Double? = null,
    val precipitationProbabilityPercent: Int? = null,
    val timestampMillis: Long = System.currentTimeMillis(),
    val source: WeatherSource = WeatherSource.API,
) {
    val hasAnyData: Boolean
        get() = temperatureC != null || humidityPercent != null || conditionCode != null
}

@Serializable
data class ManualWeatherInput(
    val city: String,
    val temperatureC: Double,
    val humidityPercent: Int,
    val conditionCode: Int,
    val precipitationProbabilityPercent: Int? = null,
)

data class SunTimes(
    val sunrise: String?,
    val sunset: String?,
    val dayLengthMinutes: Int?,
)

enum class WeatherFailureReason {
    LOCATION_DENIED,
    LOCATION_UNAVAILABLE,
    NETWORK,
    API,
    UNKNOWN,
}

data class WeatherResult(
    val weather: WeatherContext?,
    val fromCache: Boolean = false,
    val failureReason: WeatherFailureReason? = null,
    val fetchedAtMillis: Long = System.currentTimeMillis(),
)
