package com.triathlonplanner.feature.progress

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
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
                        WeeklyLoadChart(state.weeklyLoad, modifier = Modifier.fillMaxWidth().height(200.dp).padding(vertical = 12.dp))
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
private fun WeeklyLoadChart(points: List<WeeklyLoadPoint>, modifier: Modifier = Modifier) {
    if (points.isEmpty()) {
        Text("Not enough data yet.", modifier = modifier)
        return
    }
    val maxLoad = (points.maxOf { maxOf(it.plannedLoad, it.completedLoad) }).coerceAtLeast(1)
    val plannedColor = MaterialTheme.colorScheme.primary
    val completedColor = MaterialTheme.colorScheme.tertiary

    Canvas(modifier = modifier) {
        val barGroupWidth = size.width / points.size
        val barWidth = barGroupWidth * 0.35f
        points.forEachIndexed { index, point ->
            val groupStart = index * barGroupWidth
            val plannedHeight = size.height * (point.plannedLoad.toFloat() / maxLoad)
            val completedHeight = size.height * (point.completedLoad.toFloat() / maxLoad)

            drawRect(
                color = plannedColor,
                topLeft = androidx.compose.ui.geometry.Offset(groupStart + barGroupWidth * 0.1f, size.height - plannedHeight),
                size = androidx.compose.ui.geometry.Size(barWidth, plannedHeight),
            )
            drawRect(
                color = completedColor,
                topLeft = androidx.compose.ui.geometry.Offset(groupStart + barGroupWidth * 0.55f, size.height - completedHeight),
                size = androidx.compose.ui.geometry.Size(barWidth, completedHeight),
            )
        }
    }
}
