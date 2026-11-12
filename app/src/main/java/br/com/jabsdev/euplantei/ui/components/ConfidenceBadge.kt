package br.com.jabsdev.euplantei.ui.components

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import br.com.jabsdev.euplantei.R
import br.com.jabsdev.euplantei.domain.model.ConfidenceLevel
import br.com.jabsdev.euplantei.domain.model.ConfidenceRules

@Composable
fun ConfidenceBadge(
    confidencePercent: Int,
    manual: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val level = ConfidenceRules.level(confidencePercent / 100f)
    val dark = isSystemInDarkTheme()
    val (background, content) = when {
        manual -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
        level == ConfidenceLevel.HIGH ->
            MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        level == ConfidenceLevel.MEDIUM ->
            if (dark) Color(0xFF5B3B00) to Color(0xFFFFDDB0)
            else Color(0xFFFFE0B2) to Color(0xFF5B3B00)
        else -> MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
    }
    val label = when {
        manual -> stringResource(R.string.result_confidence_manual)
        level == ConfidenceLevel.HIGH -> stringResource(R.string.result_confidence_high)
        level == ConfidenceLevel.MEDIUM -> stringResource(R.string.result_confidence_medium)
        else -> stringResource(R.string.result_confidence_low)
    }
    val text = if (manual) label else stringResource(
        R.string.result_confidence_format,
        label,
        confidencePercent.coerceIn(0, 100),
    )
    val description = stringResource(R.string.cd_confidence_badge)
    Surface(
        modifier = modifier.semantics { contentDescription = "$description: $text" },
        shape = CircleShape,
        color = background,
        contentColor = content,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 11.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = text, style = MaterialTheme.typography.labelMedium)
        }
    }
}
