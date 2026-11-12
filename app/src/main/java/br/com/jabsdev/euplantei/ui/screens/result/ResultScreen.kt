package br.com.jabsdev.euplantei.ui.screens.result

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.ContentCut
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Landscape
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.StopCircle
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import br.com.jabsdev.euplantei.AppContainer
import br.com.jabsdev.euplantei.R
import br.com.jabsdev.euplantei.data.prefs.AppSettings
import br.com.jabsdev.euplantei.domain.model.ConsultationFailureKind
import br.com.jabsdev.euplantei.domain.model.PlantPrediction
import br.com.jabsdev.euplantei.domain.model.TemperatureUnit
import br.com.jabsdev.euplantei.ui.components.AnalysisSkeleton
import br.com.jabsdev.euplantei.ui.components.ConfidenceBadge
import br.com.jabsdev.euplantei.ui.components.DisclaimerText
import br.com.jabsdev.euplantei.ui.components.LeafArt
import br.com.jabsdev.euplantei.ui.components.CareCard
import br.com.jabsdev.euplantei.ui.components.MarkdownText
import br.com.jabsdev.euplantei.ui.components.PlantFilterChip
import br.com.jabsdev.euplantei.ui.components.SkeletonBlock
import br.com.jabsdev.euplantei.util.ImageUtils
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ResultScreen(
    container: AppContainer,
    imagePath: String,
    onBack: () -> Unit,
    onNewConsultation: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenGarden: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: ConsultationViewModel = viewModel(
        key = "result_$imagePath",
        factory = ConsultationViewModel.factory(container, imagePath),
    )
    val state by viewModel.ui.collectAsStateWithLifecycle()
    val settings by container.settingsRepository.settings.collectAsStateWithLifecycle(initialValue = AppSettings())
    val context = LocalContext.current
    val haptics = LocalHapticFeedback.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var showWeatherDialog by rememberSaveable { mutableStateOf(false) }
    var showGardenDialog by rememberSaveable { mutableStateOf(false) }
    var gardenAddedName by rememberSaveable { mutableStateOf<String?>(null) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> viewModel.onLocationPermissionResult(granted) }

    LaunchedEffect(Unit) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            viewModel.onLocationPermissionResult(true)
        } else {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }

    val errorMessage = state.errorMessageRes?.let { stringResource(it) }
    LaunchedEffect(state.errorMessageRes) {
        if (errorMessage != null) snackbar.showSnackbar(errorMessage)
    }
    LaunchedEffect(state.stage is ConsultationStage.Success) {
        if (state.stage is ConsultationStage.Success) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    fun leaveScreen(action: () -> Unit) {
        scope.launch {
            container.settingsRepository.clearDraft()
            action()
        }
    }

    if (showWeatherDialog) {
        WeatherManualDialog(
            onDismiss = { showWeatherDialog = false },
            onApply = {
                viewModel.applyManualWeather(it)
                showWeatherDialog = false
            },
        )
    }

    if (showGardenDialog) {
        GardenAddDialog(
            defaultName = state.species,
            recommendedDays = state.careProfile?.intervaloRegaDias,
            wateringHint = state.careProfile?.rega.orEmpty(),
            onDismiss = { showGardenDialog = false },
            onConfirm = { name, interval ->
                showGardenDialog = false
                scope.launch {
                    val result = runCatching {
                        container.gardenRepository.addPlant(
                            name = name,
                            species = state.species,
                            speciesId = state.selected?.speciesId.orEmpty(),
                            sourceImagePath = state.imagePath,
                            wateringIntervalDays = interval,
                        )
                    }
                    if (result.isSuccess) {
                        gardenAddedName = name.trim().ifBlank { state.species }
                        snackbar.showSnackbar(
                            context.getString(R.string.garden_add_success, gardenAddedName),
                        )
                    } else {
                        snackbar.showSnackbar(context.getString(R.string.garden_add_error))
                    }
                }
            },
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.result_title)) },
                navigationIcon = {
                    IconButton(onClick = { leaveScreen(onBack) }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back),
                        )
                    }
                },
                actions = {
                    if (state.isGenerating) {
                        IconButton(onClick = viewModel::cancelGeneration) {
                            Icon(
                                imageVector = Icons.Rounded.StopCircle,
                                contentDescription = stringResource(R.string.result_cancel_generation),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    IconButton(onClick = onOpenSettings) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = stringResource(R.string.cd_settings),
                        )
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(2.dp))
            PhotoHeader(
                imagePath = state.imagePath,
                species = state.species,
                condition = state.condition,
                confidencePercent = state.confidencePercent,
                manual = state.manual,
                showBadge = state.stage is ConsultationStage.Success ||
                    state.stage is ConsultationStage.Generating ||
                    state.stage is ConsultationStage.Error,
            )

            ThermalBanner(state.batteryTemperatureC)

            when (val stage = state.stage) {
                ConsultationStage.Idle,
                ConsultationStage.Classifying,
                -> LoadingCard(
                    title = stringResource(R.string.result_analyzing),
                    body = stringResource(R.string.result_analyzing_body),
                    showSkeleton = true,
                )

                is ConsultationStage.AwaitingConfirmation -> {
                    ConfirmationCard(
                        predictions = stage.predictions,
                        onConfirm = viewModel::confirmPrediction,
                        onManual = viewModel::chooseManual,
                    )
                }

                ConsultationStage.AwaitingSymptoms -> {
                    SpeciesHeader(state)
                    SymptomForm(
                        species = state.species,
                        onSubmit = viewModel::submitSymptoms,
                    )
                }

                ConsultationStage.AwaitingManual -> {
                    ManualForm(
                        lowConfidence = state.topPredictions.isNotEmpty(),
                        onSubmit = viewModel::submitManual,
                    )
                }

                ConsultationStage.FetchingWeather -> {
                    SpeciesHeader(state)
                    LoadingCard(
                        title = stringResource(R.string.result_fetching_weather),
                        body = stringResource(R.string.home_weather_loading),
                        showSkeleton = false,
                    )
                }

                is ConsultationStage.Generating -> {
                    SpeciesHeader(state)
                    WeatherSection(
                        weather = state.weather,
                        failure = state.weatherFailure,
                        fromCache = state.weatherFromCache,
                        fetchedAtMillis = state.weatherFetchedAtMillis,
                        unit = settings.temperatureUnit,
                        onManualWeather = { showWeatherDialog = true },
                    )
                    state.careProfile?.let { CareCard(it) }
                    AdviceCard(
                        advice = stage.partial,
                        streaming = true,
                        elapsedSeconds = state.elapsedSeconds,
                        onCancel = viewModel::cancelGeneration,
                    )
                    AddToGardenCard(
                        addedName = gardenAddedName,
                        recommendedDays = state.careProfile?.intervaloRegaDias,
                        onAdd = { showGardenDialog = true },
                        onOpenGarden = { leaveScreen(onOpenGarden) },
                    )
                }

                is ConsultationStage.Success -> {
                    SpeciesHeader(state)
                    WeatherSection(
                        weather = state.weather,
                        failure = state.weatherFailure,
                        fromCache = state.weatherFromCache,
                        fetchedAtMillis = state.weatherFetchedAtMillis,
                        unit = settings.temperatureUnit,
                        onManualWeather = { showWeatherDialog = true },
                    )
                    state.careProfile?.let { CareCard(it) }
                    AdviceCard(
                        advice = stage.advice,
                        streaming = false,
                        elapsedSeconds = state.elapsedSeconds,
                        onCancel = null,
                    )
                    SaveStatus(
                        saved = state.savedId != null,
                        saveFailed = state.saveFailed,
                        onRetrySave = viewModel::saveToHistory,
                    )
                    AddToGardenCard(
                        addedName = gardenAddedName,
                        recommendedDays = state.careProfile?.intervaloRegaDias,
                        onAdd = { showGardenDialog = true },
                        onOpenGarden = { leaveScreen(onOpenGarden) },
                    )
                    ResultActions(
                        canShare = state.advice.isNotBlank(),
                        onShare = { shareResult(context, state.species, state.advice, state.imagePath) },
                        onNew = { leaveScreen(onNewConsultation) },
                    )
                }

                is ConsultationStage.Error -> {
                    if (state.species.isNotBlank()) {
                        SpeciesHeader(state)
                        WeatherSection(
                            weather = state.weather,
                            failure = state.weatherFailure,
                            fromCache = state.weatherFromCache,
                            fetchedAtMillis = state.weatherFetchedAtMillis,
                            unit = settings.temperatureUnit,
                            onManualWeather = { showWeatherDialog = true },
                        )
                        state.careProfile?.let { CareCard(it) }
                    }
                    ErrorCard(
                        stage = stage,
                        onRetry = viewModel::retry,
                        onOpenSettings = onOpenSettings,
                        onNew = { leaveScreen(onNewConsultation) },
                    )
                    if (state.species.isNotBlank()) {
                        AddToGardenCard(
                            addedName = gardenAddedName,
                            recommendedDays = state.careProfile?.intervaloRegaDias,
                            onAdd = { showGardenDialog = true },
                            onOpenGarden = { leaveScreen(onOpenGarden) },
                        )
                    }
                    if (stage.partial.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.surface,
                            tonalElevation = 1.dp,
                        ) {
                            Column(Modifier.padding(16.dp)) {
                                Text(
                                    text = stringResource(R.string.result_partial_advice),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                Spacer(Modifier.height(8.dp))
                                MarkdownText(stage.partial)
                                if (state.savedId == null) {
                                    TextButton(
                                        onClick = viewModel::saveToHistory,
                                        modifier = Modifier.align(Alignment.End),
                                    ) {
                                        Text(stringResource(R.string.result_save_partial))
                                    }
                                }
                            }
                        }
                    }
                    ResultActions(
                        canShare = state.advice.isNotBlank(),
                        onShare = { shareResult(context, state.species, state.advice, state.imagePath) },
                        onNew = { leaveScreen(onNewConsultation) },
                    )
                }
            }

            Spacer(Modifier.height(4.dp))
            DisclaimerText()
            Spacer(Modifier.height(40.dp))
        }
    }
}

@Composable
private fun PhotoHeader(
    imagePath: String,
    species: String,
    condition: String,
    confidencePercent: Int,
    manual: Boolean,
    showBadge: Boolean,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(4f / 3f),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Box(Modifier.fillMaxSize()) {
            if (imagePath.isNotBlank() && File(imagePath).exists()) {
                AsyncImage(
                    model = File(imagePath),
                    contentDescription = stringResource(R.string.cd_plant_photo),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(
                                listOf(
                                    MaterialTheme.colorScheme.primaryContainer,
                                    MaterialTheme.colorScheme.surfaceContainerHigh,
                                ),
                            ),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    LeafArt(
                        modifier = Modifier.size(120.dp),
                        leafColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                        stemColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                    )
                }
            }
            if (showBadge && species.isNotBlank()) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.68f)),
                            ),
                        )
                        .padding(14.dp),
                ) {
                    Column {
                        Text(
                            text = species,
                            style = MaterialTheme.typography.titleLarge,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                text = condition,
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.86f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f, fill = false),
                            )
                            ConfidenceBadge(confidencePercent, manual)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SpeciesHeader(state: ConsultationUiState) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = state.species,
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = state.condition,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ConfidenceBadge(state.confidencePercent, state.manual)
        }
        Text(
            text = if (state.manual) {
                stringResource(R.string.result_identification_manual)
            } else {
                stringResource(R.string.result_identification_confidence, state.confidencePercent)
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun ThermalBanner(temperature: Float?) {
    if (temperature == null || temperature < br.com.jabsdev.euplantei.util.ThermalMonitor.HOT_THRESHOLD_C) return
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(Icons.Rounded.Thermostat, contentDescription = null, modifier = Modifier.size(20.dp))
            Column {
                Text(
                    text = stringResource(R.string.result_thermal_warning),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    text = stringResource(R.string.result_thermal_temp, formatNumber(temperature.toDouble())),
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}

@Composable
private fun LoadingCard(title: String, body: String, showSkeleton: Boolean) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                Text(text = title, style = MaterialTheme.typography.titleMedium)
            }
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (showSkeleton) {
                Spacer(Modifier.height(2.dp))
                AnalysisSkeleton()
            }
        }
    }
}

@Composable
private fun ConfirmationCard(
    predictions: List<PlantPrediction>,
    onConfirm: (PlantPrediction) -> Unit,
    onManual: () -> Unit,
) {
    var selectedLabel by rememberSaveable(predictions.joinToString("|") { it.rawLabel }) {
        mutableStateOf(predictions.firstOrNull()?.rawLabel)
    }
    val selected = predictions.firstOrNull { it.rawLabel == selectedLabel } ?: predictions.firstOrNull()

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.result_confirm_plant_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = stringResource(R.string.result_confirm_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = stringResource(R.string.result_choose_option),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
            )
            predictions.forEach { prediction ->
                val isSelected = prediction.rawLabel == selectedLabel
                Surface(
                    onClick = { selectedLabel = prediction.rawLabel },
                    shape = RoundedCornerShape(14.dp),
                    color = if (isSelected) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                    } else {
                        MaterialTheme.colorScheme.surfaceContainerLow
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        RadioButton(
                            selected = isSelected,
                            onClick = { selectedLabel = prediction.rawLabel },
                        )
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = prediction.species,
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                text = prediction.condition,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = stringResource(
                                    if (prediction.source == br.com.jabsdev.euplantei.domain.model.PredictionSource.HOUSEPLANT) {
                                        R.string.result_source_houseplant
                                    } else {
                                        R.string.result_source_crop
                                    },
                                ),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary,
                            )
                        }
                        ConfidenceBadge(
                            confidencePercent = (prediction.confidence * 100).toInt(),
                        )
                    }
                }
            }
            Button(
                onClick = { selected?.let(onConfirm) },
                enabled = selected != null,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.result_use_this))
            }
            TextButton(onClick = onManual, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.result_choose_other))
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SymptomForm(
    species: String,
    onSubmit: (String, Boolean) -> Unit,
) {
    val symptoms = listOf(
        R.string.result_symptom_yellow,
        R.string.result_symptom_wilted,
        R.string.result_symptom_spots,
        R.string.result_symptom_dry_tips,
        R.string.result_symptom_mold,
        R.string.result_symptom_pests,
        R.string.result_symptom_sunburn,
    )
    var selectedCsv by rememberSaveable { mutableStateOf("") }
    var healthy by rememberSaveable { mutableStateOf(false) }
    var extra by rememberSaveable { mutableStateOf("") }
    val selected = selectedCsv.split('|').filter { it.isNotBlank() }.toSet()
    val labelByKey = symptoms.associate { "symptom_$it" to stringResource(it) }

    fun toggle(resId: Int) {
        val label = "symptom_$resId"
        healthy = false
        val next = if (label in selected) selected - label else selected + label
        selectedCsv = next.joinToString("|")
    }

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.result_symptoms_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = stringResource(R.string.result_symptoms_body, species),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                symptoms.forEach { resId ->
                    val label = "symptom_$resId"
                    PlantFilterChip(
                        selected = label in selected,
                        onClick = { toggle(resId) },
                        label = { Text(stringResource(resId)) },
                    )
                }
                PlantFilterChip(
                    selected = healthy,
                    onClick = {
                        healthy = !healthy
                        if (healthy) selectedCsv = ""
                    },
                    label = { Text(stringResource(R.string.result_symptom_healthy)) },
                )
            }
            OutlinedTextField(
                value = extra,
                onValueChange = { extra = it },
                label = { Text(stringResource(R.string.result_symptom_other)) },
                placeholder = { Text(stringResource(R.string.result_symptom_other_hint)) },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    val labels = selected.mapNotNull { key -> labelByKey[key] }
                    val text = buildString {
                        if (labels.isNotEmpty()) append(labels.joinToString(", "))
                        if (extra.isNotBlank()) {
                            if (isNotEmpty()) append(". ")
                            append(extra.trim())
                        }
                        if (isEmpty()) append(
                            "Sem descrição detalhada; quero orientações de cuidado.",
                        )
                    }
                    onSubmit(text, healthy)
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.result_symptoms_continue))
            }
        }
    }
}

@Composable
private fun ManualForm(
    lowConfidence: Boolean,
    onSubmit: (String, String, String) -> Unit,
) {
    var species by rememberSaveable { mutableStateOf("") }
    var symptom by rememberSaveable { mutableStateOf("") }
    var question by rememberSaveable { mutableStateOf("") }
    var attempted by rememberSaveable { mutableStateOf(false) }
    val speciesError = attempted && species.isBlank()
    val symptomError = attempted && symptom.isBlank()

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.result_manual_title),
                style = MaterialTheme.typography.headlineSmall,
            )
            Text(
                text = if (lowConfidence) {
                    stringResource(R.string.result_manual_low_confidence)
                } else {
                    stringResource(R.string.result_manual_body)
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = species,
                onValueChange = { species = it },
                label = { Text(stringResource(R.string.result_manual_species)) },
                placeholder = { Text(stringResource(R.string.result_manual_species_hint)) },
                isError = speciesError,
                supportingText = if (speciesError) {
                    { Text(stringResource(R.string.result_manual_species_required)) }
                } else {
                    null
                },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = symptom,
                onValueChange = { symptom = it },
                label = { Text(stringResource(R.string.result_manual_symptom)) },
                placeholder = { Text(stringResource(R.string.result_manual_symptom_hint)) },
                isError = symptomError,
                supportingText = if (symptomError) {
                    { Text(stringResource(R.string.result_manual_symptom_required)) }
                } else {
                    null
                },
                minLines = 3,
                maxLines = 5,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = question,
                onValueChange = { question = it },
                label = { Text(stringResource(R.string.result_question_label)) },
                placeholder = { Text(stringResource(R.string.result_question_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                onClick = {
                    attempted = true
                    if (species.isNotBlank() && symptom.isNotBlank()) {
                        onSubmit(species, symptom, question)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.result_manual_continue))
            }
        }
    }
}

@Composable
private fun AdviceCard(
    advice: String,
    streaming: Boolean,
    elapsedSeconds: Int,
    onCancel: (() -> Unit)?,
) {
    Surface(
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.result_advice_title),
                    style = MaterialTheme.typography.headlineSmall,
                )
                if (streaming) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                        )
                        Text(
                            text = stringResource(R.string.result_streaming),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            if (advice.isBlank() && streaming) {
                Text(
                    text = stringResource(R.string.result_advice_waiting),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                AnalysisSkeleton()
            } else {
                MarkdownText(advice)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                if (streaming) {
                    Text(
                        text = stringResource(R.string.result_generation_elapsed, elapsedSeconds),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = { onCancel?.invoke() }) {
                        Text(stringResource(R.string.result_cancel_generation))
                    }
                } else {
                    Text(
                        text = stringResource(R.string.result_elapsed, elapsedSeconds),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun SaveStatus(saved: Boolean, saveFailed: Boolean, onRetrySave: () -> Unit) {
    when {
        saved -> Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Icon(
                imageVector = Icons.Rounded.Save,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
            Text(
                text = stringResource(R.string.result_saved_history),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        saveFailed -> Surface(
            shape = RoundedCornerShape(14.dp),
            color = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.result_save_failed),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onRetrySave) {
                    Text(stringResource(R.string.result_save_retry))
                }
            }
        }
    }
}

@Composable
private fun ErrorCard(
    stage: ConsultationStage.Error,
    onRetry: () -> Unit,
    onOpenSettings: () -> Unit,
    onNew: () -> Unit,
) {
    val title = when (stage.failure) {
        ConsultationFailureKind.MODEL_MISSING -> stringResource(R.string.result_model_missing_title)
        ConsultationFailureKind.THERMAL -> stringResource(R.string.result_thermal_blocked, formatNumber((stage.temperatureC ?: 0f).toDouble()))
        ConsultationFailureKind.RATE_LIMITED -> stringResource(R.string.error_rate_limit)
        ConsultationFailureKind.CANCELLED -> stringResource(R.string.result_cancelled)
        else -> stringResource(R.string.result_error_title)
    }
    val body = when (stage.failure) {
        ConsultationFailureKind.MODEL_MISSING -> stringResource(R.string.result_model_missing_body)
        ConsultationFailureKind.CLASSIFICATION -> stringResource(R.string.result_error_classification)
        ConsultationFailureKind.IMAGE -> stringResource(R.string.result_error_image)
        ConsultationFailureKind.THERMAL -> stringResource(R.string.result_error_retry_hint)
        ConsultationFailureKind.RATE_LIMITED -> stringResource(
            R.string.result_rate_limit_wait,
            stage.waitSeconds ?: 15,
        )
        ConsultationFailureKind.BUSY -> stringResource(R.string.result_error_busy)
        ConsultationFailureKind.CANCELLED -> stringResource(R.string.result_error_retry_hint)
        ConsultationFailureKind.GENERATION -> stringResource(R.string.result_error_generation)
        ConsultationFailureKind.UNKNOWN -> stringResource(R.string.result_error_unknown)
    }
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f),
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Rounded.ErrorOutline, contentDescription = null)
                Text(text = title, style = MaterialTheme.typography.titleMedium)
            }
            Text(text = body, style = MaterialTheme.typography.bodyMedium)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                when (stage.failure) {
                    ConsultationFailureKind.MODEL_MISSING -> {
                        Button(onClick = onOpenSettings) {
                            Text(stringResource(R.string.result_model_missing_action))
                        }
                    }
                    ConsultationFailureKind.IMAGE -> {
                        OutlinedButton(onClick = onNew) {
                            Text(stringResource(R.string.result_new_consultation))
                        }
                    }
                    else -> {
                        Button(onClick = onRetry) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                stringResource(
                                    if (stage.failure == ConsultationFailureKind.CLASSIFICATION) {
                                        R.string.result_retry_classification
                                    } else {
                                        R.string.result_retry_generation
                                    },
                                ),
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ResultActions(
    canShare: Boolean,
    onShare: () -> Unit,
    onNew: () -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        if (canShare) {
            OutlinedButton(onClick = onShare) {
                Icon(
                    imageVector = Icons.Rounded.Share,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.result_share))
            }
        }
        Button(onClick = onNew) {
            Icon(
                imageVector = Icons.Rounded.PhotoCamera,
                contentDescription = null,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.result_new_consultation))
        }
    }
}

private fun shareResult(
    context: android.content.Context,
    species: String,
    advice: String,
    imagePath: String,
) {
    val text = context.getString(R.string.result_share_text, species, advice)
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/jpeg"
        putExtra(Intent.EXTRA_TEXT, text)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (imagePath.isNotBlank() && File(imagePath).exists()) {
            val uri = ImageUtils.shareUri(context, imagePath)
            putExtra(Intent.EXTRA_STREAM, uri)
            clipData = android.content.ClipData.newUri(context.contentResolver, "consulta", uri)
        } else {
            type = "text/plain"
        }
    }
    runCatching {
        context.startActivity(Intent.createChooser(intent, context.getString(R.string.result_share_chooser)))
    }.onFailure {
        Toast.makeText(context, R.string.result_share_failed, Toast.LENGTH_SHORT).show()
    }
}

@Composable
private fun AddToGardenCard(
    addedName: String?,
    recommendedDays: Int?,
    onAdd: () -> Unit,
    onOpenGarden: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = if (addedName != null) Icons.Rounded.CheckCircle else Icons.Rounded.Spa,
                contentDescription = null,
                modifier = Modifier.size(24.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(
                        if (addedName != null) R.string.result_in_garden else R.string.result_add_to_garden,
                    ),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = addedName?.let {
                        stringResource(R.string.garden_added_named, it)
                    } ?: recommendedDays?.let {
                        stringResource(R.string.result_add_to_garden_body_recommended, it)
                    } ?: stringResource(R.string.result_add_to_garden_body_unknown),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.82f),
                )
            }
            if (addedName == null) {
                Button(onClick = onAdd) {
                    Text(stringResource(R.string.garden_add_dialog_confirm))
                }
            } else {
                TextButton(onClick = onOpenGarden) {
                    Text(stringResource(R.string.result_open_garden))
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun GardenAddDialog(
    defaultName: String,
    recommendedDays: Int?,
    wateringHint: String,
    onDismiss: () -> Unit,
    onConfirm: (String, Int) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(defaultName) }
    var interval by rememberSaveable(recommendedDays) {
        mutableStateOf((recommendedDays ?: 7).coerceIn(1, 60))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.garden_add_dialog_title)) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = stringResource(R.string.garden_add_dialog_body_recommended),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.garden_add_dialog_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                if (recommendedDays != null) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.garden_add_dialog_recommendation),
                                style = MaterialTheme.typography.labelMedium,
                            )
                            Text(
                                text = stringResource(
                                    R.string.garden_add_dialog_recommended_value,
                                    recommendedDays,
                                ),
                                style = MaterialTheme.typography.headlineSmall,
                            )
                            if (wateringHint.isNotBlank()) {
                                Text(
                                    text = wateringHint,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            Text(
                                text = stringResource(R.string.garden_add_dialog_recommendation_note),
                                style = MaterialTheme.typography.labelSmall,
                            )
                        }
                    }
                } else {
                    Text(
                        text = stringResource(R.string.garden_add_dialog_no_recommendation),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = stringResource(R.string.garden_add_dialog_adjust),
                        style = MaterialTheme.typography.labelLarge,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End,
                    ) {
                        IconButton(
                            onClick = { interval = (interval - 1).coerceAtLeast(1) },
                            enabled = interval > 1,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Remove,
                                contentDescription = stringResource(R.string.garden_interval_decrease),
                            )
                        }
                        Text(
                            text = stringResource(R.string.garden_add_dialog_days, interval),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        IconButton(
                            onClick = { interval = (interval + 1).coerceAtMost(60) },
                            enabled = interval < 60,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Add,
                                contentDescription = stringResource(R.string.garden_interval_increase),
                            )
                        }
                    }
                }
                if (recommendedDays != null && interval != recommendedDays) {
                    TextButton(
                        onClick = { interval = recommendedDays.coerceIn(1, 60) },
                    ) {
                        Text(
                            stringResource(
                                R.string.garden_add_dialog_use_recommendation,
                                recommendedDays,
                            ),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(name.trim().ifBlank { defaultName }, interval)
                },
                enabled = name.isNotBlank() || defaultName.isNotBlank(),
            ) {
                Text(stringResource(R.string.garden_add_dialog_confirm))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.garden_add_dialog_cancel))
            }
        },
    )
}
