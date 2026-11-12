package br.com.jabsdev.euplantei.data.repo

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import android.location.Location
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.CancellationTokenSource
import br.com.jabsdev.euplantei.data.prefs.CachedWeather
import br.com.jabsdev.euplantei.data.prefs.SettingsRepository
import br.com.jabsdev.euplantei.domain.SolarCalculator
import br.com.jabsdev.euplantei.domain.model.ManualWeatherInput
import br.com.jabsdev.euplantei.domain.model.WeatherContext
import br.com.jabsdev.euplantei.domain.model.WeatherFailureReason
import br.com.jabsdev.euplantei.domain.model.WeatherResult
import br.com.jabsdev.euplantei.domain.model.WeatherSource
import br.com.jabsdev.euplantei.util.NetworkMonitor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.time.LocalDate
import java.util.Locale
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.roundToInt

/**
 * Clima do Open-Meteo (sem chave), com localização aproximada e cache persistente.
 * Nenhuma coordenada sai daqui com mais de duas casas decimais.
 */
class WeatherRepository(
    context: Context,
    private val settings: SettingsRepository,
    private val networkMonitor: NetworkMonitor,
    private val client: OkHttpClient,
) {
    private val appContext = context.applicationContext
    private val fusedLocation = LocationServices.getFusedLocationProviderClient(appContext)
    private val json = Json { ignoreUnknownKeys = true }
    private val geocoder = Geocoder(appContext, Locale.forLanguageTag("pt-BR"))

    fun observeCached(): kotlinx.coroutines.flow.Flow<CachedWeather?> = settings.cachedWeather

    suspend fun getWeather(forceRefresh: Boolean = false): WeatherResult {
        // O primeiro valor é suficiente: o cache é atualizado por este repositório.
        val cached = settings.cachedWeather.first()
        val cacheAge = cached?.let { System.currentTimeMillis() - it.savedAtMillis }

        if (!forceRefresh && cached != null && cacheAge != null && cacheAge in 0..FRESH_CACHE_MS) {
            return WeatherResult(weather = cached.weather.copy(source = WeatherSource.CACHE))
        }

        if (!hasLocationPermission()) {
            return fallbackFromCache(cached, WeatherFailureReason.LOCATION_DENIED)
        }

        val location = runCatching { getBestLocation() }.getOrNull()
            ?: return fallbackFromCache(cached, WeatherFailureReason.LOCATION_UNAVAILABLE)

        if (!networkMonitor.isOnlineNow()) {
            return fallbackFromCache(cached, WeatherFailureReason.NETWORK)
        }

        val latitude = roundCoordinate(location.latitude)
        val longitude = roundCoordinate(location.longitude)
        val city = if (settings.current().useGeocoder) {
            resolveCity(latitude, longitude)
        } else {
            appContext.getString(br.com.jabsdev.euplantei.R.string.weather_city_approximate)
        }

        val apiWeather = fetchFromApi(latitude, longitude)
            ?: return fallbackFromCache(cached, WeatherFailureReason.API)

        val enriched = apiWeather.copy(
            city = city,
            latitude = latitude,
            longitude = longitude,
            source = WeatherSource.API,
            timestampMillis = System.currentTimeMillis(),
        )
        settings.saveWeatherCache(enriched)
        return WeatherResult(weather = enriched)
    }

    suspend fun saveManual(input: ManualWeatherInput): WeatherContext {
        val cached = settings.cachedWeather.first()
        val latitude = cached?.weather?.latitude
        val longitude = cached?.weather?.longitude
        val today = LocalDate.now()
        val sun = if (latitude != null && longitude != null) {
            SolarCalculator.sunTimes(latitude, longitude, today)
        } else {
            null
        }
        val weather = WeatherContext(
            city = input.city.ifBlank { appContext.getString(br.com.jabsdev.euplantei.R.string.common_unknown) },
            latitude = latitude,
            longitude = longitude,
            temperatureC = input.temperatureC,
            apparentTemperatureC = null,
            humidityPercent = input.humidityPercent,
            conditionCode = input.conditionCode,
            precipitationMm = null,
            cloudCoverPercent = null,
            windSpeedMs = null,
            shortwaveRadiationWm2 = null,
            sunrise = sun?.sunrise,
            sunset = sun?.sunset,
            uvIndexMax = null,
            precipitationProbabilityPercent = input.precipitationProbabilityPercent,
            timestampMillis = System.currentTimeMillis(),
            source = WeatherSource.MANUAL,
        )
        // O clima manual vale para esta consulta (e fica no rascunho), mas não
        // substitui o cache do último clima real do Open-Meteo.
        return weather
    }

    private suspend fun fallbackFromCache(
        cached: CachedWeather?,
        reason: WeatherFailureReason,
    ): WeatherResult {
        if (cached != null) {
            return WeatherResult(
                weather = cached.weather.copy(source = WeatherSource.CACHE),
                fromCache = true,
                failureReason = reason,
                fetchedAtMillis = cached.savedAtMillis,
            )
        }
        return WeatherResult(
            weather = null,
            fromCache = false,
            failureReason = reason,
        )
    }

    @SuppressLint("MissingPermission")
    private suspend fun getBestLocation(): Location? {
        val last = runCatching { fusedLocation.lastLocation.awaitResult() }.getOrNull()
        if (last != null) return last

        val cancellation = CancellationTokenSource()
        return try {
            withTimeoutOrNull(LOCATION_TIMEOUT_MS) {
                fusedLocation.getCurrentLocation(Priority.PRIORITY_LOW_POWER, cancellation.token)
                    .awaitResult()
            }
        } catch (error: SecurityException) {
            null
        } finally {
            cancellation.cancel()
        }
    }

    private fun hasLocationPermission(): Boolean =
        ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED

    private suspend fun fetchFromApi(latitude: Double, longitude: Double): WeatherContext? =
        withContext(Dispatchers.IO) {
            val url = API_BASE.toHttpUrl().newBuilder()
                .addQueryParameter("latitude", formatCoordinate(latitude))
                .addQueryParameter("longitude", formatCoordinate(longitude))
                .addQueryParameter(
                    "current",
                    "temperature_2m,relative_humidity_2m,apparent_temperature,precipitation," +
                        "weather_code,cloud_cover,wind_speed_10m",
                )
                .addQueryParameter("hourly", "shortwave_radiation")
                .addQueryParameter(
                    "daily",
                    "sunrise,sunset,uv_index_max,precipitation_probability_max",
                )
                .addQueryParameter("timezone", "auto")
                .addQueryParameter("wind_speed_unit", "ms")
                .addQueryParameter("forecast_days", "1")
                .build()
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "EuPlantei/1.0 (Android)")
                .build()

            repeat(API_ATTEMPTS) { attempt ->
                val response = runCatching { client.newCall(request).execute() }.getOrNull()
                if (response != null) {
                    response.use { result ->
                        if (result.isSuccessful) {
                            val body = result.body?.string().orEmpty()
                            return@withContext runCatching {
                                parseResponse(body, latitude, longitude)
                            }.getOrNull()
                        }
                    }
                }
                if (attempt < API_ATTEMPTS - 1) {
                    kotlinx.coroutines.delay(RETRY_DELAY_MS)
                }
            }
            null
        }

    private fun parseResponse(body: String, latitude: Double, longitude: Double): WeatherContext {
        val response = json.decodeFromString<OpenMeteoResponse>(body)
        val current = response.current ?: return WeatherContext(
            city = appContext.getString(br.com.jabsdev.euplantei.R.string.weather_city_approximate),
            latitude = latitude,
            longitude = longitude,
            source = WeatherSource.API,
        )
        val date = runCatching { LocalDate.parse(current.time.substringBefore('T')) }
            .getOrElse { LocalDate.now() }
        val fallbackSun = SolarCalculator.sunTimes(latitude, longitude, date)
        val solarIndex = response.hourly?.time?.indexOfFirst { hourly ->
            hourly.take(13) == current.time.take(13)
        } ?: -1
        val radiation = response.hourly?.shortwaveRadiation?.getOrNull(solarIndex)

        return WeatherContext(
            city = appContext.getString(br.com.jabsdev.euplantei.R.string.weather_city_approximate),
            latitude = latitude,
            longitude = longitude,
            temperatureC = current.temperature2m,
            apparentTemperatureC = current.apparentTemperature,
            humidityPercent = current.relativeHumidity,
            conditionCode = current.weatherCode,
            precipitationMm = current.precipitation,
            cloudCoverPercent = current.cloudCover,
            windSpeedMs = current.windSpeed,
            shortwaveRadiationWm2 = radiation,
            sunrise = response.daily?.sunrise?.firstOrNull()?.substringAfter('T')?.take(5)
                ?: fallbackSun.sunrise,
            sunset = response.daily?.sunset?.firstOrNull()?.substringAfter('T')?.take(5)
                ?: fallbackSun.sunset,
            uvIndexMax = response.daily?.uvIndexMax?.firstOrNull(),
            precipitationProbabilityPercent = response.daily?.precipitationProbabilityMax?.firstOrNull(),
            timestampMillis = System.currentTimeMillis(),
            source = WeatherSource.API,
        )
    }

    private suspend fun resolveCity(latitude: Double, longitude: Double): String =
        withTimeoutOrNull(GEOCODER_TIMEOUT_MS) {
            withContext(Dispatchers.IO) {
                if (!Geocoder.isPresent()) {
                    return@withContext appContext.getString(br.com.jabsdev.euplantei.R.string.weather_city_approximate)
                }
                runCatching {
                    @Suppress("DEPRECATION")
                    val address = geocoder.getFromLocation(latitude, longitude, 1)?.firstOrNull()
                    val locality = address?.locality
                        ?: address?.subAdminArea
                        ?: address?.adminArea
                        ?: return@withContext appContext.getString(br.com.jabsdev.euplantei.R.string.weather_city_approximate)
                    val state = address?.adminArea?.takeIf { it.length <= 3 }?.uppercase(Locale.ROOT)
                    if (state != null && state != locality) "$locality/$state" else locality
                }.getOrElse {
                    appContext.getString(br.com.jabsdev.euplantei.R.string.weather_city_approximate)
                }
            }
        } ?: appContext.getString(br.com.jabsdev.euplantei.R.string.weather_city_approximate)

    private fun roundCoordinate(value: Double): Double =
        (value * 100.0).roundToInt() / 100.0

    private fun formatCoordinate(value: Double): String =
        String.format(Locale.US, "%.2f", value)

    @Serializable
    private data class OpenMeteoResponse(
        val current: CurrentDto? = null,
        val hourly: HourlyDto? = null,
        val daily: DailyDto? = null,
        val timezone: String? = null,
    )

    @Serializable
    private data class CurrentDto(
        val time: String,
        @SerialName("temperature_2m") val temperature2m: Double? = null,
        @SerialName("relative_humidity_2m") val relativeHumidity: Int? = null,
        @SerialName("apparent_temperature") val apparentTemperature: Double? = null,
        val precipitation: Double? = null,
        @SerialName("weather_code") val weatherCode: Int? = null,
        @SerialName("cloud_cover") val cloudCover: Int? = null,
        @SerialName("wind_speed_10m") val windSpeed: Double? = null,
    )

    @Serializable
    private data class HourlyDto(
        val time: List<String> = emptyList(),
        @SerialName("shortwave_radiation") val shortwaveRadiation: List<Double?> = emptyList(),
    )

    @Serializable
    private data class DailyDto(
        val sunrise: List<String> = emptyList(),
        val sunset: List<String> = emptyList(),
        @SerialName("uv_index_max") val uvIndexMax: List<Double?> = emptyList(),
        @SerialName("precipitation_probability_max")
        val precipitationProbabilityMax: List<Int?> = emptyList(),
    )

    private suspend fun <T> Task<T>.awaitResult(): T = suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { result -> continuation.resume(result) }
        addOnFailureListener { error -> continuation.resumeWithException(error) }
        continuation.invokeOnCancellation { }
    }

    companion object {
        private const val API_BASE = "https://api.open-meteo.com/v1/forecast"
        private const val LOCATION_TIMEOUT_MS = 8_000L
        private const val GEOCODER_TIMEOUT_MS = 3_000L
        private const val FRESH_CACHE_MS = 10 * 60 * 1000L
        private const val API_ATTEMPTS = 2
        private const val RETRY_DELAY_MS = 600L
    }
}
