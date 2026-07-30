package com.triathlonplanner.core.designsystem

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The shared visual vocabulary. Screens compose these rather than styling raw Material widgets, so
 * radius, borders, spacing and type weights stay identical across tabs - which is most of what
 * separates a polished app from a merely working one.
 */

/** A metric: big value, optional unit, quiet label. */
data class Metric(val value: String, val unit: String?, val label: String)

/** Flat bordered container. Deliberately no elevation - stacked shadows down a list read as noise. */
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(AppSpacing.lg),
    content: @Composable ColumnScope.() -> Unit,
) {
    Card(
        modifier = if (onClick != null) modifier.clickable(onClick = onClick) else modifier,
        shape = RoundedCornerShape(AppRadius.card),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        Column(modifier = Modifier.padding(contentPadding), content = content)
    }
}

/** Small letter-spaced muted heading that labels a group without competing with it. */
@Composable
fun SectionHeader(
    text: String,
    modifier: Modifier = Modifier,
    trailing: @Composable (() -> Unit)? = null,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        trailing?.invoke()
    }
}

/** Value-over-label readout. The value carries the weight; the label recedes. */
@Composable
fun MetricTile(
    metric: Metric,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    centered: Boolean = false,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = if (centered) Alignment.CenterHorizontally else Alignment.Start,
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(metric.value, style = MetricValueStyle, color = valueColor)
            metric.unit?.let {
                Spacer(Modifier.width(2.dp))
                Text(
                    it,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 3.dp),
                )
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            metric.label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = if (centered) TextAlign.Center else TextAlign.Start,
        )
    }
}

/** Evenly divided metric strip with hairline separators - the standard "activity stats" row. */
@Composable
fun MetricRow(metrics: List<Metric>, modifier: Modifier = Modifier) {
    if (metrics.isEmpty()) return
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        metrics.forEachIndexed { index, metric ->
            MetricTile(
                metric = metric,
                modifier = Modifier.weight(1f),
                centered = index != 0,
            )
            if (index < metrics.lastIndex) {
                Box(
                    Modifier
                        .height(30.dp)
                        .width(1.dp)
                        .background(MaterialTheme.colorScheme.outline),
                )
            }
        }
    }
}

/** Circular discipline marker. Callers always pair it with a text label - never colour alone. */
@Composable
fun DisciplineBadge(
    icon: ImageVector,
    tint: Color,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    size: Dp = 38.dp,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(size * 0.52f))
    }
}

/** Compact status chip; [tone] tints fill and text together so it reads as one object. */
@Composable
fun Pill(
    text: String,
    modifier: Modifier = Modifier,
    tone: Color = MaterialTheme.colorScheme.onSurfaceVariant,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(AppRadius.pill))
            .background(tone.copy(alpha = 0.13f))
            .padding(horizontal = AppSpacing.sm, vertical = 3.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelSmall, color = tone)
    }
}

/** Hairline rule matched to the card border, for separating rows inside one container. */
@Composable
fun AppDivider(modifier: Modifier = Modifier) {
    HorizontalDivider(modifier = modifier, thickness = 1.dp, color = MaterialTheme.colorScheme.outline)
}

/** Centred placeholder so "nothing here" still looks designed rather than broken. */
@Composable
fun EmptyState(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(AppSpacing.xl),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.size(34.dp),
            )
            Spacer(Modifier.height(AppSpacing.md))
        }
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
        subtitle?.let {
            Spacer(Modifier.height(AppSpacing.xs))
            Text(
                it,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Thin meter for a completed-vs-target ratio. Track stays recessive; the fill carries tone. */
@Composable
fun ProgressBar(
    fraction: Float,
    modifier: Modifier = Modifier,
    tone: Color = MaterialTheme.colorScheme.primary,
    height: Dp = 6.dp,
) {
    val safeFraction = if (fraction.isNaN()) 0f else fraction.coerceIn(0f, 1f)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(RoundedCornerShape(AppRadius.pill))
            .background(MaterialTheme.accents.track),
    ) {
        Box(
            Modifier
                .fillMaxWidth(safeFraction)
                .height(height)
                .clip(RoundedCornerShape(AppRadius.pill))
                .background(tone),
        )
    }
}
