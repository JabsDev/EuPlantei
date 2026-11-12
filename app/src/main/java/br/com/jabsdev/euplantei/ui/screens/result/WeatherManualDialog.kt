package br.com.jabsdev.euplantei.ui.screens.result

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import br.com.jabsdev.euplantei.R
import br.com.jabsdev.euplantei.ui.components.PlantFilterChip
import br.com.jabsdev.euplantei.domain.WeatherCodeMapper
import br.com.jabsdev.euplantei.domain.model.ManualWeatherInput

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WeatherManualDialog(
    onDismiss: () -> Unit,
    onApply: (ManualWeatherInput) -> Unit,
) {
    var city by rememberSaveable { mutableStateOf("") }
    var temperature by rememberSaveable { mutableStateOf("") }
    var humidity by rememberSaveable { mutableStateOf("") }
    var rain by rememberSaveable { mutableStateOf("") }
    var conditionCode by rememberSaveable { mutableIntStateOf(2) }
    var error by rememberSaveable { mutableStateOf(false) }

    val conditionCodes = listOf(0, 2, 45, 51, 61, 80, 95)

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 520.dp),
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.result_weather_manual_title),
                    style = MaterialTheme.typography.headlineSmall,
                )
                OutlinedTextField(
                    value = city,
                    onValueChange = { city = it },
                    label = { Text(stringResource(R.string.result_weather_manual_city)) },
                    placeholder = { Text(stringResource(R.string.result_weather_manual_city_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = temperature,
                    onValueChange = { temperature = it.filter { ch -> ch.isDigit() || ch == '-' || ch == '.' } },
                    label = { Text(stringResource(R.string.result_weather_manual_temperature)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = humidity,
                    onValueChange = { humidity = it.filter(Char::isDigit).take(3) },
                    label = { Text(stringResource(R.string.result_weather_manual_humidity)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = rain,
                    onValueChange = { rain = it.filter(Char::isDigit).take(3) },
                    label = { Text(stringResource(R.string.result_weather_manual_rain)) },
                    placeholder = { Text(stringResource(R.string.result_weather_manual_rain_unknown)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = stringResource(R.string.result_weather_manual_condition),
                    style = MaterialTheme.typography.titleSmall,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    conditionCodes.forEach { code ->
                        PlantFilterChip(
                            selected = conditionCode == code,
                            onClick = { conditionCode = code },
                            label = { Text(stringResource(WeatherCodeMapper.descriptionRes(code))) },
                        )
                    }
                }
                if (error) {
                    Text(
                        text = stringResource(R.string.result_weather_manual_invalid),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                RowActions(
                    onDismiss = onDismiss,
                    onApply = {
                        val temp = temperature.replace(',', '.').toDoubleOrNull()
                        val hum = humidity.toIntOrNull()
                        val rainValue = rain.toIntOrNull()
                        if (temp == null || temp !in -20.0..60.0 || hum == null || hum !in 0..100 ||
                            (rain.isNotBlank() && (rainValue == null || rainValue !in 0..100))
                        ) {
                            error = true
                        } else {
                            onApply(
                                ManualWeatherInput(
                                    city = city.trim(),
                                    temperatureC = temp,
                                    humidityPercent = hum,
                                    conditionCode = conditionCode,
                                    precipitationProbabilityPercent = rainValue,
                                ),
                            )
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun RowActions(onDismiss: () -> Unit, onApply: () -> Unit) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        TextButton(onClick = onDismiss) {
            Text(stringResource(R.string.common_cancel))
        }
        TextButton(onClick = onApply) {
            Text(stringResource(R.string.result_weather_manual_apply))
        }
    }
}
