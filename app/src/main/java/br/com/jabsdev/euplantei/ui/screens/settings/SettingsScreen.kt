package br.com.jabsdev.euplantei.ui.screens.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LocationOn
import androidx.compose.material.icons.rounded.Memory
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Thermostat
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import br.com.jabsdev.euplantei.AppContainer
import br.com.jabsdev.euplantei.BuildConfig
import br.com.jabsdev.euplantei.R
import br.com.jabsdev.euplantei.ui.components.PlantFilterChip
import br.com.jabsdev.euplantei.ui.components.UpdateSettingsCard
import br.com.jabsdev.euplantei.data.repo.DownloadFailure
import br.com.jabsdev.euplantei.data.repo.ModelDownloadState
import br.com.jabsdev.euplantei.domain.model.LlmRuntimeState
import br.com.jabsdev.euplantei.domain.model.ModelInfo
import br.com.jabsdev.euplantei.domain.model.ModelSource
import br.com.jabsdev.euplantei.domain.model.TemperatureUnit
import br.com.jabsdev.euplantei.domain.model.ThemeMode
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    container: AppContainer,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.factory(container))
    val state by viewModel.ui.collectAsStateWithLifecycle()

    val updateState by container.updateManager.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { container.updateManager.checkIfDue() }
    val source by viewModel.modelSource.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }

    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }
    var showClearDialog by rememberSaveable { mutableStateOf(false) }
    var showSourceDialog by rememberSaveable { mutableStateOf(false) }
    var hasCamera by remember { mutableStateOf(hasPermission(context, Manifest.permission.CAMERA)) }
    var hasLocation by remember {
        mutableStateOf(hasPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        hasCamera = hasPermission(context, Manifest.permission.CAMERA)
        hasLocation = hasPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { message ->
            snackbar.showSnackbar(context.getString(message))
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) viewModel.importModel(uri)
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { viewModel.startDownload() }

    val gardenNotificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) viewModel.setGardenReminders(true)
    }

    fun setGardenReminders(enabled: Boolean) {
        if (!enabled) {
            viewModel.setGardenReminders(false)
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            gardenNotificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.setGardenReminders(true)
        }
    }

    fun startDownloadWithNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.startDownload()
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.settings_model_delete_title)) },
            text = { Text(stringResource(R.string.settings_model_delete_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.deleteModel()
                    },
                ) {
                    Text(stringResource(R.string.common_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text(stringResource(R.string.settings_clear_history_title)) },
            text = { Text(stringResource(R.string.settings_clear_history_body)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        showClearDialog = false
                        viewModel.clearHistory()
                    },
                ) {
                    Text(stringResource(R.string.settings_clear_history_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(stringResource(R.string.common_cancel))
                }
            },
        )
    }

    if (showSourceDialog) {
        ModelSourceDialog(
            initial = source,
            onDismiss = { showSourceDialog = false },
            onSave = { url, sha ->
                viewModel.saveSource(url, sha) { success ->
                    if (success) showSourceDialog = false
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
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = stringResource(R.string.cd_back),
                        )
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 4.dp,
                bottom = 28.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                ModelSettingsCard(
                    state = state,
                    source = source,
                    onDownload = ::startDownloadWithNotificationPermission,
                    onCancelDownload = viewModel::cancelDownload,
                    onImport = { importLauncher.launch(arrayOf("*/*")) },
                    onDelete = { showDeleteDialog = true },
                    onEditSource = { showSourceDialog = true },
                    failureMessage = viewModel::failureMessage,
                )
            }

            item {
                SettingsCard(
                    title = stringResource(R.string.settings_theme_title),
                    icon = Icons.Rounded.Tune,
                ) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeMode.entries.forEach { mode ->
                            PlantFilterChip(
                                selected = state.settings.themeMode == mode,
                                onClick = { viewModel.setTheme(mode) },
                                label = {
                                    Text(
                                        stringResource(
                                            when (mode) {
                                                ThemeMode.SYSTEM -> R.string.settings_theme_system
                                                ThemeMode.LIGHT -> R.string.settings_theme_light
                                                ThemeMode.DARK -> R.string.settings_theme_dark
                                            },
                                        ),
                                    )
                                },
                            )
                        }
                    }
                }
            }

            item {
                SettingsCard(
                    title = stringResource(R.string.settings_units_title),
                    icon = Icons.Rounded.Thermostat,
                ) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TemperatureUnit.entries.forEach { unit ->
                            PlantFilterChip(
                                selected = state.settings.temperatureUnit == unit,
                                onClick = { viewModel.setUnit(unit) },
                                label = {
                                    Text(
                                        stringResource(
                                            when (unit) {
                                                TemperatureUnit.CELSIUS -> R.string.settings_units_celsius
                                                TemperatureUnit.FAHRENHEIT -> R.string.settings_units_fahrenheit
                                            },
                                        ),
                                    )
                                },
                            )
                        }
                    }
                    state.batteryTemperatureC?.let { temperature ->
                        Text(
                            text = stringResource(
                                R.string.settings_battery_temp,
                                br.com.jabsdev.euplantei.ui.screens.result.formatNumber(temperature.toDouble()),
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }

            item {
                SettingsCard(
                    title = stringResource(R.string.settings_permissions_title),
                    icon = Icons.Rounded.Security,
                ) {
                    PermissionRow(
                        icon = Icons.Rounded.CameraAlt,
                        label = stringResource(R.string.settings_permission_camera),
                        granted = hasCamera,
                        onManage = { openAppSettings(context) },
                    )
                    PermissionRow(
                        icon = Icons.Rounded.LocationOn,
                        label = stringResource(R.string.settings_permission_location),
                        granted = hasLocation,
                        onManage = { openAppSettings(context) },
                    )
                }
            }

            item {
                SettingsCard(
                    title = stringResource(R.string.settings_data_title),
                    icon = Icons.Rounded.DeleteOutline,
                ) {
                    Text(
                        text = stringResource(R.string.settings_clear_history_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { showClearDialog = true }) {
                        Text(stringResource(R.string.settings_clear_history))
                    }
                }
            }

            item {
                SettingsCard(
                    title = stringResource(R.string.settings_garden_title),
                    icon = Icons.Rounded.NotificationsActive,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.garden_reminders_title),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                text = stringResource(R.string.garden_reminders_body),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = state.settings.gardenRemindersEnabled,
                            onCheckedChange = ::setGardenReminders,
                        )
                    }
                }
            }

            item {
                UpdateSettingsCard(
                    state = updateState,
                    currentVersion = BuildConfig.VERSION_NAME,
                    onCheck = container.updateManager::checkNow,
                    onDownload = container.updateManager::downloadAndInstall,
                    onInstall = container.updateManager::installReady,
                )
            }

            item {
                SettingsCard(
                    title = stringResource(R.string.settings_privacy_title),
                    icon = Icons.Rounded.PrivacyTip,
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.settings_geocoder_title),
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                text = stringResource(R.string.settings_geocoder_body),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Switch(
                            checked = state.settings.useGeocoder,
                            onCheckedChange = viewModel::setUseGeocoder,
                        )
                    }
                    Text(
                        text = stringResource(R.string.settings_privacy_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                SettingsCard(
                    title = stringResource(R.string.settings_about_title),
                    icon = Icons.Rounded.Info,
                ) {
                    Text(
                        text = stringResource(R.string.settings_about_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            item {
                SettingsCard(
                    title = stringResource(R.string.settings_licenses_title),
                    icon = Icons.Rounded.Memory,
                ) {
                    Text(
                        text = stringResource(R.string.settings_licenses_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            item {
                SettingsCard(
                    title = stringResource(R.string.settings_legal_title),
                    icon = Icons.Rounded.Security,
                ) {
                    Text(
                        text = stringResource(R.string.settings_legal_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ModelSettingsCard(
    state: SettingsUiState,
    source: ModelSource?,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onImport: () -> Unit,
    onDelete: () -> Unit,
    onEditSource: () -> Unit,
    failureMessage: (DownloadFailure, Int?) -> Int,
) {
    SettingsCard(
        title = stringResource(R.string.settings_model_title),
        icon = Icons.Rounded.Memory,
    ) {
        val model = state.modelInfo
        if (model != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.size(48.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Memory,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(12.dp),
                    )
                }
                Column(Modifier.weight(1f)) {
                    Text(
                        text = model.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = stringResource(
                            R.string.settings_model_size,
                            formatMegabytes(model.sizeBytes),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(
                            R.string.settings_model_date,
                            formatDate(model.addedAtMillis),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (state.llmState == LlmRuntimeState.READY ||
                        state.llmState == LlmRuntimeState.GENERATING
                    ) {
                        Text(
                            text = stringResource(R.string.settings_model_active),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        } else {
            Text(
                text = stringResource(R.string.settings_model_none),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        when (val download = state.downloadState) {
            is ModelDownloadState.Downloading -> {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    LinearProgressIndicator(
                        progress = {
                            if (download.totalBytes > 0) download.percent / 100f else 0f
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = if (download.totalBytes > 0) {
                                stringResource(
                                    R.string.settings_download_progress,
                                    download.percent,
                                    formatMegabytes(download.downloadedBytes),
                                    formatMegabytes(download.totalBytes),
                                )
                            } else {
                                stringResource(
                                    R.string.settings_download_progress_unknown,
                                    formatMegabytes(download.downloadedBytes),
                                )
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        TextButton(onClick = onCancelDownload) {
                            Text(stringResource(R.string.settings_model_download_cancel))
                        }
                    }
                }
            }
            ModelDownloadState.Verifying -> {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    Text(stringResource(R.string.settings_model_verifying))
                }
            }
            is ModelDownloadState.Failed -> {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(failureMessage(download.reason, download.httpCode), download.httpCode ?: 0),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                    Button(onClick = onDownload) {
                        Icon(
                            imageVector = Icons.Rounded.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.settings_model_download_retry))
                    }
                }
            }
            is ModelDownloadState.Completed -> {
                Text(
                    text = stringResource(R.string.settings_model_ready_generic),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            ModelDownloadState.Idle -> Unit
        }

        if (state.importing) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                Text(stringResource(R.string.settings_model_importing))
            }
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (state.modelInfo == null) {
                Button(
                    onClick = onDownload,
                    enabled = state.downloadState !is ModelDownloadState.Downloading,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_model_download))
                }
                OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_model_import))
                }
            } else {
                OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_model_replace))
                }
                OutlinedButton(onClick = onDownload, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_model_redownload))
                }
                OutlinedButton(onClick = onDelete, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_model_delete))
                }
            }
        }

        TextButton(onClick = onEditSource, modifier = Modifier.align(Alignment.End)) {
            Text(stringResource(R.string.settings_model_source_title))
        }
        if (source != null) {
            Text(
                text = stringResource(R.string.settings_model_source_current),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = source.url,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (source.sha256.isBlank()) {
                    stringResource(R.string.settings_model_sha_none)
                } else {
                    stringResource(R.string.settings_model_sha_value, source.sha256.take(16))
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SettingsCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Text(text = title, style = MaterialTheme.typography.titleMedium)
            }
            content()
        }
    }
}

@Composable
private fun PermissionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    granted: Boolean,
    onManage: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = stringResource(
                    if (granted) R.string.settings_permission_granted else R.string.settings_permission_denied,
                ),
                style = MaterialTheme.typography.labelSmall,
                color = if (granted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
        }
        TextButton(onClick = onManage) {
            Text(stringResource(R.string.settings_permission_manage))
        }
    }
}

@Composable
private fun ModelSourceDialog(
    initial: ModelSource?,
    onDismiss: () -> Unit,
    onSave: (String, String) -> Unit,
) {
    var url by rememberSaveable(initial) { mutableStateOf(initial?.url ?: BuildConfig.MODEL_URL) }
    var sha by rememberSaveable(initial) { mutableStateOf(initial?.sha256 ?: BuildConfig.MODEL_SHA256) }
    var invalid by rememberSaveable { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_model_source_title)) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = stringResource(R.string.settings_model_source_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it; invalid = false },
                    label = { Text(stringResource(R.string.settings_model_url)) },
                    singleLine = true,
                    isError = invalid,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = sha,
                    onValueChange = { sha = it },
                    label = { Text(stringResource(R.string.settings_model_sha)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(
                    onClick = {
                        url = BuildConfig.MODEL_URL
                        sha = BuildConfig.MODEL_SHA256
                        invalid = false
                    },
                ) {
                    Text(stringResource(R.string.settings_model_source_default))
                }
                if (invalid) {
                    Text(
                        text = stringResource(R.string.settings_model_source_invalid),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (!url.startsWith("https://")) {
                        invalid = true
                    } else {
                        onSave(url, sha)
                    }
                },
            ) {
                Text(stringResource(R.string.common_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.common_cancel))
            }
        },
    )
}

private fun hasPermission(context: android.content.Context, permission: String): Boolean =
    ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

private fun openAppSettings(context: android.content.Context) {
    context.startActivity(
        Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", context.packageName, null),
        ),
    )
}

private fun formatMegabytes(bytes: Long): String =
    String.format(Locale.forLanguageTag("pt-BR"), "%.0f", bytes / 1_000_000.0)

private fun formatDate(millis: Long): String =
    DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.forLanguageTag("pt-BR"))
        .format(Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()))
