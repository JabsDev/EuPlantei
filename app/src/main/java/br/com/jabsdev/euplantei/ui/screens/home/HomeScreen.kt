package br.com.jabsdev.euplantei.ui.screens.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.WifiOff
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Spa
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import br.com.jabsdev.euplantei.AppContainer
import br.com.jabsdev.euplantei.data.prefs.AppSettings
import br.com.jabsdev.euplantei.data.prefs.CachedWeather
import br.com.jabsdev.euplantei.ui.components.ConfidenceBadge
import br.com.jabsdev.euplantei.ui.screens.result.temperatureLabel
import br.com.jabsdev.euplantei.util.ImageUtils
import java.io.File
import kotlinx.coroutines.launch
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import br.com.jabsdev.euplantei.R
import br.com.jabsdev.euplantei.ui.components.BrandMark
import br.com.jabsdev.euplantei.ui.components.DisclaimerText
import br.com.jabsdev.euplantei.ui.components.EmptyStateArt
import br.com.jabsdev.euplantei.ui.components.LeafArt
import br.com.jabsdev.euplantei.ui.components.LeafWatermark
import br.com.jabsdev.euplantei.ui.components.SectionHeader
import br.com.jabsdev.euplantei.ui.components.TrustPill
import br.com.jabsdev.euplantei.ui.components.UpdateAvailableCard
import br.com.jabsdev.euplantei.data.repo.UpdateState
import java.time.LocalTime

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    container: AppContainer,
    onTakePhoto: () -> Unit,
    onGalleryReady: (String) -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenConsultation: (Long) -> Unit,
    onResumeDraft: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: HomeViewModel = viewModel(factory = HomeViewModel.factory(container))
    val ui by viewModel.ui.collectAsStateWithLifecycle()
    val settings by container.settingsRepository.settings.collectAsStateWithLifecycle(
        initialValue = AppSettings(),
    )
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val updateState by container.updateManager.state.collectAsStateWithLifecycle()
    val showUpdate = updateState is UpdateState.Available ||
        updateState is UpdateState.Downloading ||
        updateState is UpdateState.Ready

    LaunchedEffect(Unit) {
        container.updateManager.checkIfDue()
    }
    val snackbarHostState = remember { SnackbarHostState() }
    var preparing by remember { mutableStateOf(false) }
    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            preparing = true
            scope.launch {
                runCatching { ImageUtils.prepareFromUri(context, uri) }
                    .onSuccess { file ->
                        preparing = false
                        onGalleryReady(file.absolutePath)
                    }
                    .onFailure { error ->
                        android.util.Log.e("HomeScreen", "Falha ao preparar imagem da galeria", error)
                        preparing = false
                        snackbarHostState.showSnackbar(
                            context.getString(R.string.error_image_decode),
                        )
                    }
            }
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                            text = stringResource(R.string.app_name),
                            style = MaterialTheme.typography.titleLarge,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onOpenHistory) {
                        Icon(
                            imageVector = Icons.Rounded.History,
                            contentDescription = stringResource(R.string.nav_history),
                        )
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
        Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                Text(
                    text = greeting(),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.home_title),
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(top = 2.dp),
                )
                Text(
                    text = stringResource(R.string.home_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }

            item {
                HeroCard(
                    onTakePhoto = onTakePhoto,
                    onOpenGallery = {
                        galleryLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                        )
                    },
                )
            }

            item {
                androidx.compose.foundation.layout.FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    TrustPill(Icons.Outlined.Memory, stringResource(R.string.home_local_feature))
                    TrustPill(Icons.Outlined.WifiOff, stringResource(R.string.home_offline_feature))
                    TrustPill(Icons.Outlined.Lock, stringResource(R.string.home_privacy_feature))
                }
            }

            if (showUpdate) {
                item {
                    UpdateAvailableCard(
                        state = updateState,
                        onDownload = container.updateManager::downloadAndInstall,
                        onInstall = container.updateManager::installReady,
                    )
                }
            }

            item {
                ModelStatusCard(
                    configured = ui.modelInfo != null,
                    onClick = onOpenSettings,
                )
            }

            item {
                val draft = ui.draft
                if (draft != null) {
                    DraftResumeCard(
                        imagePath = draft.imagePath,
                        species = draft.species,
                        onClick = { onResumeDraft(draft.imagePath) },
                    )
                }
            }

            item {
                WeatherPreviewCard(
                    cached = ui.recentWeather,
                    online = ui.online,
                    unit = settings.temperatureUnit,
                )
            }

            item {
                SectionHeader(
                    title = stringResource(R.string.home_recent_title),
                    actionLabel = stringResource(R.string.home_see_all),
                    onAction = onOpenHistory,
                )
            }

            item {
                val recent = ui.recent
                if (recent == null) {
                    RecentEmptyCard(onTakePhoto = onTakePhoto)
                } else {
                    RecentConsultationCard(
                        item = recent,
                        onClick = { onOpenConsultation(recent.id) },
                    )
                }
            }

            item {
                TipCard()
            }

            item {
                Spacer(Modifier.height(4.dp))
                DisclaimerText()
            }
        }
        if (preparing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.32f)),
                contentAlignment = Alignment.Center,
            ) {
                Surface(shape = RoundedCornerShape(18.dp)) {
                    Row(
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                        Text(stringResource(R.string.camera_processing))
                    }
                }
            }
        }
        }
    }
}

@Composable
private fun greeting(): String {
    val hour = LocalTime.now().hour
    return stringResource(
        when {
            hour < 12 -> R.string.home_greeting_morning
            hour < 18 -> R.string.home_greeting_afternoon
            else -> R.string.home_greeting_evening
        },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeroCard(
    onTakePhoto: () -> Unit,
    onOpenGallery: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary,
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
                        ),
                        start = Offset.Zero,
                        end = Offset(900f, 900f),
                    ),
                ),
        ) {
            LeafWatermark(Modifier.matchParentSize())
            Canvas(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(180.dp)
                    .padding(top = 12.dp, end = 8.dp),
            ) {
                drawCircle(
                    color = Color.White.copy(alpha = 0.08f),
                    radius = size.minDimension * 0.5f,
                )
            }
            LeafArt(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 22.dp, end = 24.dp)
                    .size(96.dp),
                leafColor = Color.White.copy(alpha = 0.18f),
                stemColor = Color.White.copy(alpha = 0.38f),
            )
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Surface(
                    shape = CircleShape,
                    color = Color.White.copy(alpha = 0.16f),
                    contentColor = Color.White,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(Icons.Rounded.Spa, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text(
                            text = stringResource(R.string.home_take_photo),
                            style = MaterialTheme.typography.labelMedium,
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.home_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(end = 76.dp),
                )
                Text(
                    text = stringResource(R.string.home_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.84f),
                    modifier = Modifier.padding(end = 38.dp),
                )
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = onTakePhoto,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            contentColor = MaterialTheme.colorScheme.primary,
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PhotoCamera,
                            contentDescription = null,
                            modifier = Modifier.size(19.dp),
                        )
                        Text(
                            text = stringResource(R.string.home_take_photo),
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                    OutlinedButton(
                        onClick = onOpenGallery,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            Color.White.copy(alpha = 0.62f),
                        ),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.PhotoLibrary,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Text(
                            text = stringResource(R.string.home_gallery_short),
                            modifier = Modifier.padding(start = 7.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DraftResumeCard(
    imagePath: String,
    species: String,
    onClick: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center,
            ) {
                if (imagePath.isNotBlank() && File(imagePath).exists()) {
                    AsyncImage(
                        model = File(imagePath),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    LeafArt(
                        modifier = Modifier.size(32.dp),
                        leafColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                        stemColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f),
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.home_resume_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = species.ifBlank { stringResource(R.string.home_resume_draft) },
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = stringResource(R.string.home_resume_body),
                    style = MaterialTheme.typography.labelSmall.copy(
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.8f),
                    ),
                )
            }
            Text(
                text = stringResource(R.string.home_resume_action),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Icon(
                imageVector = Icons.Rounded.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun WeatherPreviewCard(
    cached: CachedWeather?,
    online: Boolean,
    unit: br.com.jabsdev.euplantei.domain.model.TemperatureUnit,
) {
    val weather = cached?.weather
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer,
                modifier = Modifier.size(46.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (weather != null) Icons.Rounded.WbSunny else Icons.Rounded.CloudOff,
                        contentDescription = stringResource(R.string.cd_weather_icon),
                        tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.home_weather_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                if (weather != null) {
                    val condition = stringResource(
                        br.com.jabsdev.euplantei.domain.WeatherCodeMapper.descriptionRes(weather.conditionCode),
                    )
                    val humidityText = weather.humidityPercent?.let {
                        stringResource(R.string.home_weather_humidity_suffix, it)
                    }
                    Text(
                        text = buildString {
                            weather.temperatureC?.let {
                                append(temperatureLabel(it, unit))
                                append(" · ")
                            }
                            append(condition)
                            if (humidityText != null) {
                                append(" · ")
                                append(humidityText)
                            }
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = if (!online) {
                            stringResource(R.string.home_weather_offline_cached)
                        } else {
                            stringResource(
                                R.string.home_weather_cached_at,
                                java.time.format.DateTimeFormatter.ofPattern(
                                    "dd/MM HH:mm",
                                    java.util.Locale.forLanguageTag("pt-BR"),
                                ).format(
                                    java.time.Instant.ofEpochMilli(cached.savedAtMillis)
                                        .atZone(java.time.ZoneId.systemDefault()),
                                ),
                            )
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        text = stringResource(R.string.home_weather_idle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun ModelStatusCard(configured: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Memory,
                contentDescription = stringResource(R.string.cd_model_status),
                tint = if (configured) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                modifier = Modifier.size(20.dp),
            )
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(
                        if (configured) R.string.home_model_ready else R.string.home_model_missing,
                    ),
                    style = MaterialTheme.typography.titleSmall,
                )
                if (configured) {
                    Text(
                        text = stringResource(R.string.home_model_ready_desc),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (!configured) {
                Text(
                    text = stringResource(R.string.home_model_configure),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
                Icon(
                    imageVector = Icons.Rounded.ArrowForward,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun RecentConsultationCard(
    item: br.com.jabsdev.euplantei.data.db.ConsultationEntity,
    onClick: () -> Unit,
) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(78.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                if (File(item.imagePath).exists()) {
                    AsyncImage(
                        model = File(item.imagePath),
                        contentDescription = stringResource(R.string.cd_history_thumbnail),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    LeafArt(
                        modifier = Modifier.size(40.dp),
                        leafColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                        stemColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.65f),
                    )
                }
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                Text(
                    text = item.species,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = item.condition,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = item.advice,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                ConfidenceBadge(item.confidencePercent, item.manual)
            }
        }
    }
}

@Composable
private fun RecentEmptyCard(onTakePhoto: () -> Unit) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            EmptyStateArt(modifier = Modifier.size(74.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.home_empty_title),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = stringResource(R.string.home_empty_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        FilledTonalButton(
            onClick = onTakePhoto,
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
        ) {
            Text(stringResource(R.string.home_empty_cta))
            Icon(
                imageVector = Icons.Rounded.ArrowForward,
                contentDescription = null,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(18.dp),
            )
        }
    }
}

@Composable
private fun TipCard() {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f),
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(
                imageVector = Icons.Rounded.Spa,
                contentDescription = null,
                modifier = Modifier.size(22.dp),
            )
            Column {
                Text(
                    text = stringResource(R.string.home_tip_title),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = stringResource(R.string.home_tip_body),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}
