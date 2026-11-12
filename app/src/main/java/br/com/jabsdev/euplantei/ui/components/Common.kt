package br.com.jabsdev.euplantei.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Eco
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import br.com.jabsdev.euplantei.R

@Composable
fun BrandMark(
    modifier: Modifier = Modifier,
    size: Int = 40,
) {
    val appName = stringResource(R.string.app_name)
    Surface(
        modifier = modifier
            .size(size.dp)
            .semantics { contentDescription = appName },
        shape = RoundedCornerShape((size * 0.32f).dp),
        color = MaterialTheme.colorScheme.primaryContainer,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Rounded.Eco,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                modifier = Modifier.size((size * 0.62f).dp),
            )
        }
    }
}

/** Ilustração vetorial de folha usada no hero e em estados vazios. */
@Composable
fun LeafArt(
    modifier: Modifier = Modifier,
    leafColor: Color = Color(0x55FFFFFF),
    stemColor: Color = Color(0x88FFFFFF),
) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            moveTo(w * 0.50f, h * 0.06f)
            cubicTo(w * 0.88f, h * 0.12f, w * 0.96f, h * 0.56f, w * 0.64f, h * 0.84f)
            cubicTo(w * 0.44f, h * 1.00f, w * 0.18f, h * 0.92f, w * 0.10f, h * 0.68f)
            cubicTo(w * 0.02f, h * 0.42f, w * 0.18f, h * 0.14f, w * 0.50f, h * 0.06f)
            close()
        }
        drawPath(path, color = leafColor)
        drawLine(
            color = stemColor,
            start = Offset(w * 0.53f, h * 0.93f),
            end = Offset(w * 0.53f, h * 0.27f),
            strokeWidth = w * 0.014f,
        )
        // Nervuras laterais
        repeat(4) { i ->
            val y = h * (0.35f + i * 0.13f)
            drawLine(
                color = stemColor,
                start = Offset(w * 0.53f, y),
                end = Offset(w * (0.33f + i * 0.03f), y - h * 0.10f),
                strokeWidth = w * 0.008f,
            )
            drawLine(
                color = stemColor,
                start = Offset(w * 0.53f, y + h * 0.02f),
                end = Offset(w * (0.72f - i * 0.02f), y - h * 0.08f),
                strokeWidth = w * 0.008f,
            )
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        if (actionLabel != null && onAction != null) {
            androidx.compose.material3.TextButton(onClick = onAction) {
                Text(actionLabel, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

@Composable
fun TrustPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(14.dp),
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
fun EmptyStateArt(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(150.dp)
            .clip(CircleShape)
            .background(
                Brush.radialGradient(
                    listOf(
                        MaterialTheme.colorScheme.primaryContainer,
                        MaterialTheme.colorScheme.surfaceContainerHigh,
                    ),
                ),
            ),
        contentAlignment = Alignment.Center,
    ) {
        LeafArt(
            modifier = Modifier.size(82.dp),
            leafColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
            stemColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
        )
    }
}

@Composable
fun EmptyState(
    title: String,
    body: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        EmptyStateArt()
        Spacer(Modifier.height(4.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (actionLabel != null && onAction != null) {
            androidx.compose.material3.Button(onClick = onAction) {
                Text(actionLabel)
            }
        }
    }
}

@Composable
fun DisclaimerText(modifier: Modifier = Modifier) {
    Text(
        text = stringResource(R.string.result_disclaimer),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = modifier.fillMaxWidth(),
    )
}

/** Marca d'água de folhas para o topo das telas. */
@Composable
fun LeafWatermark(modifier: Modifier = Modifier, color: Color = Color(0x14FFFFFF)) {
    Canvas(modifier = modifier) {
        rotate(18f, pivot = Offset(size.width * 0.7f, size.height * 0.15f)) {
            drawCircle(
                color = color,
                radius = size.minDimension * 0.45f,
                center = Offset(size.width * 0.78f, size.height * 0.05f),
            )
        }
        rotate(-24f, pivot = Offset(size.width * 0.15f, size.height * 0.8f)) {
            drawCircle(
                color = color,
                radius = size.minDimension * 0.30f,
                center = Offset(size.width * 0.08f, size.height * 0.95f),
            )
        }
    }
}

@Composable
fun PlantFilterChip(
    selected: Boolean,
    onClick: () -> Unit,
    label: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    androidx.compose.material3.FilterChip(
        selected = selected,
        onClick = onClick,
        label = label,
        modifier = modifier,
        colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
        ),
    )
}
