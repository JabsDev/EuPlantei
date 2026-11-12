package br.com.jabsdev.euplantei.ui.screens.garden

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Thunderstorm
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import br.com.jabsdev.euplantei.R
import br.com.jabsdev.euplantei.domain.GardenWeatherAdvisor
import br.com.jabsdev.euplantei.domain.WeatherCodeMapper
import br.com.jabsdev.euplantei.domain.model.TemperatureUnit
import br.com.jabsdev.euplantei.ui.screens.result.temperatureLabel
import br.com.jabsdev.euplantei.util.bodyRes
import br.com.jabsdev.euplantei.util.titleRes

@Composable
fun GardenWeatherCard(
    state: GardenWeatherState,
    unit: TemperatureUnit,
    modifier: Modifier = Modifier,
) {
    val weather = state.weather
    val alerts = GardenWeatherAdvisor.alerts(weather)
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    modifier = Modifier.size(40.dp),
                ) {
                    androidx.compose.foundation.layout.Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (alerts.isEmpty()) {
                                Icons.Rounded.WbSunny
                            } else {
                                Icons.Rounded.Thunderstorm
                            },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.garden_weather_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    if (weather != null) {
                        val condition = stringResource(
                            WeatherCodeMapper.descriptionRes(weather.conditionCode),
                        )
                        val details = buildString {
                            weather.temperatureC?.let {
                                append(temperatureLabel(it, unit))
                                append(" · ")
                            }
                            append(condition)
                            weather.humidityPercent?.let {
                                append(" · ")
                                append(stringResource(R.string.home_weather_humidity_suffix, it))
                            }
                        }
                        Text(
                            text = details,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            when {
                weather == null -> Text(
                    text = stringResource(R.string.garden_weather_none),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                alerts.isEmpty() -> Text(
                    text = stringResource(R.string.garden_weather_ok),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                else -> alerts.forEach { alert ->
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f),
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(3.dp),
                        ) {
                            Text(
                                text = stringResource(alert.kind.titleRes),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                text = stringResource(alert.kind.bodyRes),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
            }

            if (weather != null && state.fromCache) {
                Text(
                    text = stringResource(R.string.garden_weather_cached),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
