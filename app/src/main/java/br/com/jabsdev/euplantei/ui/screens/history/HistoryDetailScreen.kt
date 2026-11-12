package br.com.jabsdev.euplantei.ui.screens.history

import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import br.com.jabsdev.euplantei.AppContainer
import br.com.jabsdev.euplantei.R
import br.com.jabsdev.euplantei.data.db.ConsultationEntity
import br.com.jabsdev.euplantei.domain.model.TemperatureUnit
import br.com.jabsdev.euplantei.ui.components.ConfidenceBadge
import br.com.jabsdev.euplantei.ui.components.DisclaimerText
import br.com.jabsdev.euplantei.ui.components.MarkdownText
import br.com.jabsdev.euplantei.ui.screens.result.WeatherSection
import br.com.jabsdev.euplantei.util.ImageUtils
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryDetailScreen(
    container: AppContainer,
    id: Long,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: HistoryDetailViewModel = viewModel(
        key = "history_detail_$id",
        factory = HistoryDetailViewModel.factory(container, id),
    )
    val entity by viewModel.consultation.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val appSettings by container.settingsRepository.settings.collectAsStateWithLifecycle(
        initialValue = br.com.jabsdev.euplantei.data.prefs.AppSettings(),
    )
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/plain"),
    ) { uri ->
        if (uri != null) viewModel.export(uri)
    }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { message ->
            snackbar.showSnackbar(context.getString(message), duration = SnackbarDuration.Short)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.history_detail_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { exportLauncher.launch(viewModel.exportFileName()) }) {
                        Icon(
                            imageVector = Icons.Rounded.Download,
                            contentDescription = stringResource(R.string.common_export),
                        )
                    }
                    IconButton(
                        onClick = {
                            entity?.let {
                                shareHistory(context, it, viewModel.buildText())
                            }
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Share,
                            contentDescription = stringResource(R.string.common_share),
                        )
                    }
                    IconButton(
                        onClick = {
                            viewModel.delete()
                            scope.launch {
                                val result = snackbar.showSnackbar(
                                    message = context.getString(R.string.history_deleted),
                                    actionLabel = context.getString(R.string.common_undo),
                                    duration = SnackbarDuration.Short,
                                )
                                if (result == SnackbarResult.ActionPerformed) {
                                    viewModel.undoDelete()
                                }
                            }
                        },
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.DeleteOutline,
                            contentDescription = stringResource(R.string.common_delete),
                        )
                    }
                },
            )
        },
    ) { padding ->
        val item = entity
        if (item == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                br.com.jabsdev.euplantei.ui.components.EmptyState(
                    title = stringResource(R.string.history_detail_missing_title),
                    body = stringResource(R.string.history_detail_missing_body),
                )
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Spacer(Modifier.height(2.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(4f / 3f)
                    .clip(RoundedCornerShape(22.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            ) {
                if (File(item.imagePath).exists()) {
                    AsyncImage(
                        model = File(item.imagePath),
                        contentDescription = stringResource(R.string.cd_plant_photo),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Transparent, Color.Black.copy(alpha = 0.65f)),
                            ),
                        )
                        .padding(14.dp),
                ) {
                    Column {
                        Text(
                            text = item.species,
                            style = MaterialTheme.typography.headlineSmall,
                            color = Color.White,
                        )
                        Text(
                            text = item.condition,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.85f),
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                ConfidenceBadge(item.confidencePercent, item.manual)
                Text(
                    text = formatHistoryDate(item.createdAt),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (item.symptom.isNotBlank()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(Modifier.padding(14.dp)) {
                        Text(
                            text = stringResource(R.string.result_symptom_label),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(item.symptom, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            val weather = remember(item) { viewModel.weather() }
            if (weather != null) {
                WeatherSection(
                    weather = weather,
                    failure = null,
                    fromCache = false,
                    fetchedAtMillis = item.createdAt,
                    unit = appSettings.temperatureUnit,
                    onManualWeather = {},
                    allowManual = false,
                )
            }

            Surface(
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 1.dp,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = stringResource(R.string.result_advice_title),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    MarkdownText(item.advice)
                }
            }

            OutlinedButton(
                onClick = { shareHistory(context, item, viewModel.buildText()) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Share,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.common_share))
            }

            DisclaimerText()
            Spacer(Modifier.height(20.dp))
        }
    }
}

private fun shareHistory(
    context: android.content.Context,
    entity: ConsultationEntity,
    text: String,
) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "image/jpeg"
        putExtra(Intent.EXTRA_TEXT, text)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        if (File(entity.imagePath).exists()) {
            val uri = ImageUtils.shareUri(context, entity.imagePath)
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
