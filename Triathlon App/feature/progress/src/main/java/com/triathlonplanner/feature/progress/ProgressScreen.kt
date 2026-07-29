package com.triathlonplanner.feature.progress

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.triathlonplanner.core.model.WeeklyLoadPoint

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgressScreen(viewModel: ProgressViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(topBar = { TopAppBar(title = { Text("Progress") }) }) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (state.isLoading) {
                CircularProgressIndicator(Modifier.padding(24.dp))
            } else {
                LazyColumn(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    item {
                        Text("Weekly load: planned vs. completed", style = MaterialTheme.typography.titleMedium)
                        ChartLegend(modifier = Modifier.padding(top = 8.dp))
                        WeeklyLoadChart(state.weeklyLoad, modifier = Modifier.fillMaxWidth().height(220.dp).padding(top = 8.dp))
                    }
                    item {
                        Text("Why did my plan change?", style = MaterialTheme.typography.titleMedium)
                    }
                    if (state.events.isEmpty()) {
                        item { Text("No adaptations yet - your plan is on track.", style = MaterialTheme.typography.bodyMedium) }
                    } else {
                        items(state.events) { event ->
                            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(event.description, style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ChartLegend(modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(16.dp)) {
        LegendEntry(MaterialTheme.colorScheme.primary, "Planned")
        LegendEntry(MaterialTheme.colorScheme.tertiary, "Completed")
    }
}

@Composable
private fun LegendEntry(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(10.dp).background(color, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

// Below this, week labels and bar pairs start overlapping - long plans (e.g. a 28-week Full Iron
// build) scroll horizontally instead of squeezing every week into the screen width.
private val MIN_WEEK_WIDTH = 44.dp

@Composable
private fun WeeklyLoadChart(points: List<WeeklyLoadPoint>, modifier: Modifier = Modifier) {
    if (points.isEmpty()) {
        Text("Not enough data yet.", modifier = modifier)
        return
    }
    val maxLoad = (points.maxOf { maxOf(it.plannedLoad, it.completedLoad) }).coerceAtLeast(1)
    val plannedColor = MaterialTheme.colorScheme.primary
    val completedColor = MaterialTheme.colorScheme.tertiary
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val textMeasurer = rememberTextMeasurer()
    val axisLabelSizePx = with(androidx.compose.ui.platform.LocalDensity.current) { 11.sp.toPx() }

    BoxWithConstraints(modifier = modifier) {
        // Fills the available width when everything fits comfortably (short plans, unchanged
        // behavior); once weeks would drop below MIN_WEEK_WIDTH, the chart grows wider than the
        // screen and this Box's horizontalScroll takes over instead of cramming bars together.
        val weekWidth = (maxWidth / points.size).coerceAtLeast(MIN_WEEK_WIDTH)
        val chartWidth = weekWidth * points.size

        Box(modifier = Modifier.fillMaxHeight().horizontalScroll(rememberScrollState())) {
            Canvas(modifier = Modifier.width(chartWidth).fillMaxHeight()) {
                val axisLabelHeight = axisLabelSizePx * 1.6f
                val barAreaHeight = size.height - axisLabelHeight
                val barGroupWidth = size.width / points.size
                val barWidth = barGroupWidth * 0.35f

                points.forEachIndexed { index, point ->
                    val groupStart = index * barGroupWidth
                    val plannedHeight = barAreaHeight * (point.plannedLoad.toFloat() / maxLoad)
                    val completedHeight = barAreaHeight * (point.completedLoad.toFloat() / maxLoad)

                    drawRect(
                        color = plannedColor,
                        topLeft = Offset(groupStart + barGroupWidth * 0.1f, barAreaHeight - plannedHeight),
                        size = Size(barWidth, plannedHeight),
                    )
                    drawRect(
                        color = completedColor,
                        topLeft = Offset(groupStart + barGroupWidth * 0.55f, barAreaHeight - completedHeight),
                        size = Size(barWidth, completedHeight),
                    )

                    val label = "W${point.weekIndex}"
                    val measured = textMeasurer.measure(
                        label,
                        style = androidx.compose.ui.text.TextStyle(fontSize = 11.sp, color = labelColor),
                    )
                    drawText(
                        measured,
                        topLeft = Offset(groupStart + barGroupWidth / 2 - measured.size.width / 2, barAreaHeight + axisLabelHeight * 0.2f),
                    )
                }
            }
        }
    }
}
