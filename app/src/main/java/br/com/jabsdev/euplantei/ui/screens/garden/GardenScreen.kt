package br.com.jabsdev.euplantei.ui.screens.garden

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.jabsdev.euplantei.AppContainer
import br.com.jabsdev.euplantei.R
import br.com.jabsdev.euplantei.data.db.GardenPlantEntity
import br.com.jabsdev.euplantei.domain.WateringStatus
import br.com.jabsdev.euplantei.domain.daysUntilWatering
import br.com.jabsdev.euplantei.domain.wateringStatus
import br.com.jabsdev.euplantei.ui.components.BrandMark
import br.com.jabsdev.euplantei.ui.components.EmptyState
import br.com.jabsdev.euplantei.ui.components.SectionHeader
import coil.compose.AsyncImage
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GardenScreen(
    container: AppContainer,
    onAddPlant: () -> Unit,
    onOpenPlant: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: GardenViewModel = viewModel(factory = GardenViewModel.factory(container))
    val plants by viewModel.plants.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val weather by viewModel.weather.collectAsStateWithLifecycle()

    val context = LocalContext.current
    var notificationsEnabled by remember {
        mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled())
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        notificationsEnabled = granted ||
            NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    val now = System.currentTimeMillis()
    val due = plants.filter { it.wateringStatus(now) != WateringStatus.LATER }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        BrandMark(size = 36)
                        Text(
                            text = stringResource(R.string.garden_title),
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = stringResource(R.string.cd_settings),
                        )
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddPlant,
                icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.garden_add_plant)) },
            )
        },
    ) { padding ->
        if (plants.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                EmptyState(
                    title = stringResource(R.string.garden_empty_title),
                    body = stringResource(R.string.garden_empty_body),
                    actionLabel = stringResource(R.string.garden_empty_cta),
                    onAction = onAddPlant,
                )
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 104.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Text(
                    text = stringResource(R.string.garden_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (settings.gardenRemindersEnabled && !notificationsEnabled) {
                item {
                    NotificationPermissionCard(
                        onEnable = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                        },
                    )
                }
            }

            item {
                GardenWeatherCard(
                    state = weather,
                    unit = settings.temperatureUnit,
                )
            }

            item {
                DueSummaryCard(
                    due = due,
                    onWater = viewModel::water,
                )
            }

            item {
                SectionHeader(title = stringResource(R.string.garden_plants_title))
            }

            items(plants, key = { it.id }) { plant ->
                GardenPlantCard(
                    plant = plant,
                    onClick = { onOpenPlant(plant.id) },
                    onWater = { viewModel.water(plant.id) },
                )
            }
        }
    }
}

@Composable
private fun NotificationPermissionCard(onEnable: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f),
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.NotificationsActive,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.garden_notifications_off_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = stringResource(R.string.garden_notifications_off_body),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Button(onClick = onEnable) {
                Text(stringResource(R.string.garden_notifications_enable))
            }
        }
    }
}

@Composable
private fun DueSummaryCard(
    due: List<GardenPlantEntity>,
    onWater: (Long) -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = if (due.isEmpty()) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
        } else {
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
        },
        contentColor = if (due.isEmpty()) {
            MaterialTheme.colorScheme.onPrimaryContainer
        } else {
            MaterialTheme.colorScheme.onErrorContainer
        },
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = if (due.isEmpty()) {
                    stringResource(R.string.garden_due_none_title)
                } else {
                    stringResource(R.string.garden_due_count, due.size)
                },
                style = MaterialTheme.typography.titleMedium,
            )
            if (due.isEmpty()) {
                Text(
                    text = stringResource(R.string.garden_due_none),
                    style = MaterialTheme.typography.bodySmall,
                )
            } else {
                due.forEach { plant ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = plant.name,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = wateringText(plant),
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Button(onClick = { onWater(plant.id) }) {
                            Icon(
                                imageVector = Icons.Rounded.WaterDrop,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp),
                            )
                            Text(
                                text = stringResource(R.string.garden_water_action),
                                modifier = Modifier.padding(start = 6.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun GardenPlantCard(
    plant: GardenPlantEntity,
    onClick: () -> Unit,
    onWater: () -> Unit,
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                if (File(plant.coverImagePath).exists()) {
                    AsyncImage(
                        model = File(plant.coverImagePath),
                        contentDescription = stringResource(R.string.cd_plant_photo),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.WaterDrop,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(
                    text = plant.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = plant.species,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                WateringStatusChip(plant)
            }
            IconButton(onClick = onWater) {
                Icon(
                    imageVector = Icons.Rounded.WaterDrop,
                    contentDescription = stringResource(R.string.garden_water_action),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

@Composable
fun WateringStatusChip(plant: GardenPlantEntity) {
    val status = plant.wateringStatus()
    val container = when (status) {
        WateringStatus.OVERDUE -> MaterialTheme.colorScheme.errorContainer
        WateringStatus.TODAY -> MaterialTheme.colorScheme.secondaryContainer
        WateringStatus.TOMORROW -> MaterialTheme.colorScheme.tertiaryContainer
        WateringStatus.LATER -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val content = when (status) {
        WateringStatus.OVERDUE -> MaterialTheme.colorScheme.onErrorContainer
        WateringStatus.TODAY -> MaterialTheme.colorScheme.onSecondaryContainer
        WateringStatus.TOMORROW -> MaterialTheme.colorScheme.onTertiaryContainer
        WateringStatus.LATER -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        shape = RoundedCornerShape(50),
        color = container,
        contentColor = content,
    ) {
        Text(
            text = wateringText(plant),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
        )
    }
}

@Composable
fun wateringText(plant: GardenPlantEntity): String {
    val days = plant.daysUntilWatering()
    return when {
        days < 0 -> stringResource(R.string.garden_next_overdue, (-days).toInt())
        days == 0L -> stringResource(R.string.garden_next_today)
        days == 1L -> stringResource(R.string.garden_next_tomorrow)
        else -> stringResource(R.string.garden_next_in_days, days.toInt())
    }
}
