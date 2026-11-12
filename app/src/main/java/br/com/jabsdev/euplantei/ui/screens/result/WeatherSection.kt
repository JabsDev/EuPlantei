package br.com.jabsdev.euplantei.ui.screens.result

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Air
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Umbrella
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import br.com.jabsdev.euplantei.R
import br.com.jabsdev.euplantei.domain.WeatherCodeMapper
import br.com.jabsdev.euplantei.domain.model.TemperatureUnit
import br.com.jabsdev.euplantei.domain.model.WeatherContext
import br.com.jabsdev.euplantei.domain.model.WeatherFailureReason
import br.com.jabsdev.euplantei.domain.model.WeatherSource
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WeatherSection(
    weather: WeatherContext?,
    failure: WeatherFailureReason?,
    fromCache: Boolean,
    fetchedAtMillis: Long,
    unit: TemperatureUnit,
    onManualWeather: () -> Unit,
    modifier: Modifier = Modifier,
    allowManual: Boolean = true,
) {
    if (weather == null) {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = RoundedCornerShape(18.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow,
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CloudOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(
                            when (failure) {
                                WeatherFailureReason.NETWORK -> R.string.result_weather_offline_notice
                                WeatherFailureReason.LOCATION_DENIED -> R.string.result_weather_location_notice
                                WeatherFailureReason.LOCATION_UNAVAILABLE -> R.string.result_weather_location_unavailable
                                else -> R.string.result_weather_missing_notice
                            },
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (allowManual) {
                    TextButton(onClick = onManualWeather, modifier = Modifier.align(Alignment.End)) {
                        Text(stringResource(R.string.result_weather_missing_action))
                    }
                }
            }
        }
        return
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.result_weather_title),
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = weather.city,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = stringResource(WeatherCodeMapper.descriptionRes(weather.conditionCode)),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                weather.temperatureC?.let {
                    WeatherChip(
                        icon = Icons.Rounded.Thermostat,
                        text = temperatureLabel(it, unit),
                    )
                }
                weather.humidityPercent?.let {
                    WeatherChip(
                        icon = Icons.Rounded.WaterDrop,
                        text = stringResource(R.string.result_weather_humidity, it),
                    )
                }
                if (weather.sunrise != null || weather.sunset != null) {
                    WeatherChip(
                        icon = Icons.Rounded.WbSunny,
                        text = stringResource(
                            R.string.result_weather_sun,
                            weather.sunrise ?: "—",
                            weather.sunset ?: "—",
                        ),
                    )
                }
                weather.precipitationProbabilityPercent?.let {
                    WeatherChip(
                        icon = Icons.Rounded.Umbrella,
                        text = stringResource(R.string.result_weather_rain, it),
                    )
                }
                weather.windSpeedMs?.let {
                    WeatherChip(
                        icon = Icons.Rounded.Air,
                        text = stringResource(R.string.result_weather_chip_wind, formatNumber(it)),
                    )
                }
                weather.cloudCoverPercent?.let {
                    WeatherChip(
                        icon = Icons.Rounded.Cloud,
                        text = stringResource(R.string.result_weather_chip_clouds, it),
                    )
                }
                weather.shortwaveRadiationWm2?.let {
                    WeatherChip(
                        icon = Icons.Rounded.WbSunny,
                        text = stringResource(
                            R.string.result_weather_radiation,
                            it.roundToInt(),
                        ),
                    )
                }
            }

            when {
                weather.source == WeatherSource.MANUAL -> {
                    Text(
                        text = stringResource(R.string.result_weather_manual),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                fromCache -> {
                    Text(
                        text = stringResource(
                            R.string.result_weather_cache_notice,
                            formatCacheTime(fetchedAtMillis),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (failure == WeatherFailureReason.NETWORK) {
                        Text(
                            text = stringResource(R.string.result_weather_offline_notice),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (allowManual) {
                TextButton(onClick = onManualWeather, modifier = Modifier.align(Alignment.End)) {
                    Text(stringResource(R.string.result_weather_change_manual))
                }
            }
        }
    }
}

@Composable
private fun WeatherChip(icon: ImageVector, text: String) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(15.dp),
                tint = MaterialTheme.colorScheme.primary,
            )
            Text(text = text, style = MaterialTheme.typography.labelMedium)
        }
    }
}

fun temperatureLabel(celsius: Double, unit: TemperatureUnit): String {
    val value = when (unit) {
        TemperatureUnit.CELSIUS -> celsius
        TemperatureUnit.FAHRENHEIT -> celsius * 9.0 / 5.0 + 32.0
    }
    val suffix = if (unit == TemperatureUnit.CELSIUS) "°C" else "°F"
    return "${formatNumber(value)}$suffix"
}

fun formatNumber(value: Double): String {
    val rounded = value.roundToInt().toDouble()
    return if (abs(value - rounded) < 0.05) {
        rounded.toInt().toString()
    } else {
        String.format(Locale.forLanguageTag("pt-BR"), "%.1f", value)
    }
}

private fun formatCacheTime(millis: Long): String =
    DateTimeFormatter.ofPattern("dd/MM 'às' HH:mm", Locale.forLanguageTag("pt-BR"))
        .format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))
