package br.com.jabsdev.euplantei.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.Landscape
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import br.com.jabsdev.euplantei.R
import br.com.jabsdev.euplantei.domain.model.PlantProfile

/**
 * Ficha de cuidados compartilhada entre o resultado da consulta e o detalhe
 * do jardim.
 */
@Composable
fun CareCard(
    profile: PlantProfile,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = false,
) {
    var expanded by rememberSaveable(profile.id) { mutableStateOf(initiallyExpanded) }
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Spa,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp),
                    )
                    Text(
                        text = stringResource(R.string.result_care_title),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
                TextButton(onClick = { expanded = !expanded }) {
                    Text(
                        stringResource(
                            if (expanded) R.string.result_care_show_less else R.string.result_care_show_more,
                        ),
                    )
                }
            }
            CareRow(Icons.Rounded.WbSunny, R.string.result_care_light, profile.luz)
            CareRow(Icons.Rounded.WaterDrop, R.string.result_care_water, profile.rega)
            CareRow(Icons.Rounded.ContentCut, R.string.result_care_pruning, profile.poda)
            CareRow(Icons.Rounded.Eco, R.string.result_care_fertilizing, profile.adubacao)
            if (expanded) {
                CareRow(Icons.Rounded.Thermostat, R.string.result_care_temperature, profile.faixaTemperatura)
                CareRow(Icons.Rounded.Landscape, R.string.result_care_soil, profile.solo)
                CareRow(Icons.Rounded.Pets, R.string.result_care_pets, profile.toxicidadePet)
            }
        }
    }
}

@Composable
private fun CareRow(
    icon: ImageVector,
    label: Int,
    value: String,
) {
    if (value.isBlank()) return
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(18.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = stringResource(label),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}
