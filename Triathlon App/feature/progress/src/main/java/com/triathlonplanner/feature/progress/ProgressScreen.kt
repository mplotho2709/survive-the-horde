package com.triathlonplanner.feature.progress

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.triathlonplanner.core.designsystem.AppCard
import com.triathlonplanner.core.designsystem.AppSpacing
import com.triathlonplanner.core.designsystem.EmptyState
import com.triathlonplanner.core.designsystem.Metric
import com.triathlonplanner.core.designsystem.MetricRow
import com.triathlonplanner.core.designsystem.ProgressBar
import com.triathlonplanner.core.designsystem.SectionHeader
import com.triathlonplanner.core.designsystem.accents
import com.triathlonplanner.core.model.WeeklyLoadPoint

@Composable
fun ProgressScreen(viewModel: ProgressViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (state.isLoading) {
                CircularProgressIndicator(Modifier.padding(AppSpacing.xl))
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(
                        start = AppSpacing.gutter,
                        end = AppSpacing.gutter,
                        bottom = AppSpacing.xl,
                    ),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.md),
                ) {
                    item {
                        Text(
                            "Progress",
                            style = MaterialTheme.typography.displaySmall,
                            modifier = Modifier.padding(top = AppSpacing.xl, bottom = AppSpacing.xs),
                        )
                    }

                    item { AdherenceCard(state.weeklyLoad) }

                    item {
                        AppCard(Modifier.fillMaxWidth()) {
                            SectionHeader("Weekly load")
                            Spacer(Modifier.height(AppSpacing.sm))
                            ChartLegend()
                            Spacer(Modifier.height(AppSpacing.md))
                            WeeklyLoadChart(
                                points = state.weeklyLoad,
                                modifier = Modifier.fillMaxWidth().height(190.dp),
                            )
                        }
                    }

                    item {
                        SectionHeader(
                            "Why your plan changed",
                            modifier = Modifier.padding(top = AppSpacing.sm, bottom = AppSpacing.xs),
                        )
                    }

                    if (state.events.isEmpty()) {
                        item {
                            AppCard(Modifier.fillMaxWidth()) {
                                EmptyState(
                                    title = "No adjustments yet",
                                    subtitle = "Your plan is tracking as written.",
                                    icon = Icons.Filled.AutoAwesome,
                                )
                            }
                        }
                    } else {
                        items(state.events) { event ->
                            AppCard(Modifier.fillMaxWidth()) {
                                Text(event.description, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Headline adherence figure - the "how am I doing" answer, before any chart. */
@Composable
private fun AdherenceCard(points: List<WeeklyLoadPoint>) {
    val planned = points.sumOf { it.plannedLoad }
    val completed = points.sumOf { it.completedLoad }
    val hasData = planned > 0
    val adherence = if (hasData) (completed * 100.0 / planned).toInt() else 0
    val tone = when {
        !hasData -> MaterialTheme.colorScheme.onSurfaceVariant
        adherence >= 90 -> MaterialTheme.accents.success
        adherence >= 70 -> MaterialTheme.accents.warning
        else -> MaterialTheme.accents.danger
    }

    AppCard(Modifier.fillMaxWidth()) {
        MetricRow(
            metrics = listOf(
                Metric(if (hasData) "$adherence" else "-", if (hasData) "%" else null, "Adherence"),
                Metric("$completed", null, "Load done"),
                Metric("$planned", null, "Load planned"),
            ),
        )
        if (hasData) {
            Spacer(Modifier.height(AppSpacing.md))
            ProgressBar(fraction = completed.toFloat() / planned, tone = tone)
        }
    }
}

@Composable
private fun ChartLegend() {
    Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.lg)) {
        LegendEntry(MaterialTheme.accents.track, "Planned")
        LegendEntry(MaterialTheme.colorScheme.primary, "Completed")
    }
}

@Composable
private fun LegendEntry(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).background(color, CircleShape))
        Spacer(Modifier.width(AppSpacing.xs))
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// Below this, week labels and bars start colliding - long plans (a 28-week Iron build) scroll
// horizontally rather than squeezing every week into the screen width.
private val MIN_WEEK_WIDTH = 34.dp

/**
 * Planned vs completed weekly load.
 *
 * The two series are *overlaid*, not paired side by side: planned is a reference the athlete is
 * measured against rather than a peer series, so it sits behind as a recessive track with completed
 * drawn in front. That reads as "how full is this week" at a glance and needs half the horizontal
 * room a paired-bar chart would. Bars are anchored square to the baseline with rounded tops, so bar
 * height still encodes magnitude honestly.
 */
@Composable
private fun WeeklyLoadChart(points: List<WeeklyLoadPoint>, modifier: Modifier = Modifier) {
    if (points.isEmpty()) {
        EmptyState(
            title = "Not enough data yet",
            subtitle = "Complete a few sessions to see your load trend.",
            modifier = modifier,
        )
        return
    }

    val maxLoad = points.maxOf { maxOf(it.plannedLoad, it.completedLoad) }.coerceAtLeast(1)
    val plannedColor = MaterialTheme.accents.track
    val completedColor = MaterialTheme.colorScheme.primary
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val baselineColor = MaterialTheme.colorScheme.outline
    val textMeasurer = rememberTextMeasurer()
    val labelPx = with(LocalDensity.current) { 10.sp.toPx() }
    val cornerPx = with(LocalDensity.current) { 4.dp.toPx() }

    BoxWithConstraints(modifier = modifier) {
        val weekWidth = (maxWidth / points.size).coerceAtLeast(MIN_WEEK_WIDTH)
        val chartWidth = weekWidth * points.size

        Box(modifier = Modifier.fillMaxHeight().horizontalScroll(rememberScrollState())) {
            Canvas(modifier = Modifier.width(chartWidth).fillMaxHeight()) {
                val axisHeight = labelPx * 2f
                val plotHeight = size.height - axisHeight
                val groupWidth = size.width / points.size
                val barWidth = groupWidth * 0.56f

                // Baseline only. The comparison here is bar-to-bar; horizontal rules would add ink
                // without helping read it.
                drawRect(color = baselineColor, topLeft = Offset(0f, plotHeight), size = Size(size.width, 1f))

                // Label density is derived from actual cell width, so ticks never overlap.
                val labelEvery = if (groupWidth < 40f) 2 else 1

                points.forEachIndexed { index, point ->
                    val centerX = index * groupWidth + groupWidth / 2f
                    val left = centerX - barWidth / 2f
                    val plannedHeight = plotHeight * (point.plannedLoad.toFloat() / maxLoad)
                    val completedHeight = plotHeight * (point.completedLoad.toFloat() / maxLoad)

                    if (plannedHeight > 0f) {
                        drawRoundedBar(left, plotHeight, barWidth, plannedHeight, cornerPx, plannedColor)
                    }
                    if (completedHeight > 0f) {
                        drawRoundedBar(left, plotHeight, barWidth, completedHeight, cornerPx, completedColor)
                    }

                    if (index % labelEvery == 0) {
                        val measured = textMeasurer.measure(
                            "${point.weekIndex}",
                            style = TextStyle(fontSize = 10.sp, color = labelColor),
                        )
                        drawText(
                            measured,
                            topLeft = Offset(centerX - measured.size.width / 2f, plotHeight + labelPx * 0.5f),
                        )
                    }
                }
            }
        }
    }
}

/** Bar with rounded top corners, square-anchored to the baseline. */
private fun DrawScope.drawRoundedBar(
    left: Float,
    baseline: Float,
    width: Float,
    height: Float,
    corner: Float,
    color: Color,
) {
    val radius = minOf(corner, height / 2f, width / 2f)
    val path = Path().apply {
        addRoundRect(
            RoundRect(
                left = left,
                top = baseline - height,
                right = left + width,
                bottom = baseline,
                topLeftCornerRadius = CornerRadius(radius, radius),
                topRightCornerRadius = CornerRadius(radius, radius),
                bottomLeftCornerRadius = CornerRadius.Zero,
                bottomRightCornerRadius = CornerRadius.Zero,
            ),
        )
    }
    drawPath(path, color)
}
