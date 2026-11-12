package br.com.jabsdev.euplantei.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import br.com.jabsdev.euplantei.R
import br.com.jabsdev.euplantei.data.repo.UpdateInfo
import br.com.jabsdev.euplantei.data.repo.UpdateState

/** Cartão de novidade exibido na Home quando há uma release mais nova. */
@Composable
fun UpdateAvailableCard(
    state: UpdateState,
    onDownload: (UpdateInfo) -> Unit,
    onInstall: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        is UpdateState.Available -> UpdateSurface(
            title = stringResource(R.string.update_title),
            body = stringResource(R.string.update_version_available, state.info.versionName),
            notes = state.info.notes,
            action = {
                Button(onClick = { onDownload(state.info) }) {
                    Text(stringResource(R.string.update_download))
                }
            },
            modifier = modifier,
        )

        is UpdateState.Downloading -> UpdateSurface(
            title = stringResource(R.string.update_title),
            body = stringResource(R.string.update_downloading, state.percent),
            notes = state.info.notes,
            progress = state.percent,
            modifier = modifier,
        )

        is UpdateState.Ready -> UpdateSurface(
            title = stringResource(R.string.update_title),
            body = stringResource(R.string.update_ready),
            notes = stringResource(R.string.update_permission),
            action = {
                Button(onClick = onInstall) {
                    Text(stringResource(R.string.update_install))
                }
            },
            modifier = modifier,
        )

        else -> Unit
    }
}

/** Cartão completo em Configurações, com verificação manual. */
@Composable
fun UpdateSettingsCard(
    state: UpdateState,
    currentVersion: String,
    onCheck: () -> Unit,
    onDownload: (UpdateInfo) -> Unit,
    onInstall: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.SystemUpdate,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = stringResource(R.string.update_settings_title),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Text(
                text = stringResource(R.string.update_current_version, currentVersion),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            when (state) {
                UpdateState.Idle -> Text(
                    text = stringResource(R.string.update_settings_body),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                UpdateState.Checking -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                    )
                    Text(
                        text = stringResource(R.string.update_checking),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                UpdateState.UpToDate -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = stringResource(R.string.update_up_to_date, currentVersion),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }

                is UpdateState.Failed -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.ErrorOutline,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp),
                    )
                    Text(
                        text = stringResource(state.messageRes),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                    )
                }

                is UpdateState.Available -> {
                    Text(
                        text = stringResource(R.string.update_version_available, state.info.versionName),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (state.info.notes.isNotBlank()) {
                        Text(
                            text = state.info.notes,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Button(
                        onClick = { onDownload(state.info) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.update_download))
                    }
                }

                is UpdateState.Downloading -> {
                    Text(
                        text = stringResource(R.string.update_downloading, state.percent),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    LinearProgressIndicator(
                        progress = { state.percent / 100f },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                is UpdateState.Ready -> {
                    Text(
                        text = stringResource(R.string.update_ready),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = stringResource(R.string.update_permission),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(
                        onClick = onInstall,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(stringResource(R.string.update_install))
                    }
                }
            }
            Text(
                text = stringResource(R.string.update_auto_check),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = onCheck,
                enabled = state !is UpdateState.Checking &&
                    state !is UpdateState.Downloading,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.update_check))
            }
        }
    }
}

@Composable
private fun UpdateSurface(
    title: String,
    body: String,
    notes: String,
    modifier: Modifier = Modifier,
    progress: Int? = null,
    action: @Composable (() -> Unit)? = null,
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.65f),
        contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
        modifier = modifier.fillMaxWidth(),
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
                    imageVector = Icons.Rounded.SystemUpdate,
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                )
                Text(text = title, style = MaterialTheme.typography.titleMedium)
            }
            Text(text = body, style = MaterialTheme.typography.bodyMedium)
            if (notes.isNotBlank()) {
                Text(
                    text = notes,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (progress != null) {
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            action?.invoke()
        }
    }
}
