package br.com.jabsdev.euplantei.domain

import androidx.annotation.StringRes
import br.com.jabsdev.euplantei.R

enum class WeatherCondition {
    CLEAR,
    MAINLY_CLEAR,
    PARTLY_CLOUDY,
    OVERCAST,
    FOG,
    DRIZZLE,
    RAIN,
    SNOW,
    SHOWERS,
    THUNDERSTORM,
    UNKNOWN,
}

/** Tabela WMO usada pelo Open-Meteo. Códigos não listados caem em UNKNOWN. */
object WeatherCodeMapper {
    fun conditionFor(code: Int?): WeatherCondition = when (code) {
        0 -> WeatherCondition.CLEAR
        1, 2, 3 -> WeatherCondition.PARTLY_CLOUDY
        45, 48 -> WeatherCondition.FOG
        in 51..57 -> WeatherCondition.DRIZZLE
        in 61..67 -> WeatherCondition.RAIN
        in 71..77 -> WeatherCondition.SNOW
        in 80..82 -> WeatherCondition.SHOWERS
        in 95..99 -> WeatherCondition.THUNDERSTORM
        else -> WeatherCondition.UNKNOWN
    }

    @StringRes
    fun descriptionRes(code: Int?): Int = when (conditionFor(code)) {
        WeatherCondition.CLEAR -> R.string.weather_condition_clear
        WeatherCondition.MAINLY_CLEAR -> R.string.weather_condition_mainly_clear
        WeatherCondition.PARTLY_CLOUDY -> R.string.weather_condition_partly_cloudy
        WeatherCondition.OVERCAST -> R.string.weather_condition_overcast
        WeatherCondition.FOG -> R.string.weather_condition_fog
        WeatherCondition.DRIZZLE -> R.string.weather_condition_drizzle
        WeatherCondition.RAIN -> R.string.weather_condition_rain
        WeatherCondition.SNOW -> R.string.weather_condition_snow
        WeatherCondition.SHOWERS -> R.string.weather_condition_showers
        WeatherCondition.THUNDERSTORM -> R.string.weather_condition_thunderstorm
        WeatherCondition.UNKNOWN -> R.string.weather_condition_unknown
    }
}
