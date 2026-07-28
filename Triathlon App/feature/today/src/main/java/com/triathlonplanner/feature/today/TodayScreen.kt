package com.triathlonplanner.feature.today

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.triathlonplanner.core.model.WorkoutStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayScreen(onWorkoutClick: (Long) -> Unit, viewModel: TodayViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(topBar = { TopAppBar(title = { Text("Today") }) }) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading -> CircularProgressIndicator(Modifier.padding(24.dp))
                state.workouts.isEmpty() -> Text(
                    "No workout scheduled today - enjoy the rest.",
                    modifier = Modifier.padding(24.dp),
                )
                else -> LazyColumn(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    items(state.workouts, key = { it.id }) { workout ->
                        WorkoutCard(workout, onClick = { onWorkoutClick(workout.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun WorkoutCard(workout: TodayWorkoutView, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable(onClick = onClick)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Column {
                androidx.compose.foundation.layout.Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    StatusIcon(workout.status)
                    Text(workout.title, style = MaterialTheme.typography.titleMedium)
                }
            }
            androidx.compose.foundation.layout.Spacer(Modifier.padding(top = 4.dp))
            Text("${workout.durationMin} min", style = MaterialTheme.typography.bodyMedium)
            workout.zoneLabel?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun StatusIcon(status: WorkoutStatus) {
    when (status) {
        WorkoutStatus.COMPLETED -> Icon(Icons.Filled.CheckCircle, contentDescription = "Completed", tint = Color(0xFF2E7D32))
        WorkoutStatus.MISSED -> Icon(Icons.Filled.Warning, contentDescription = "Missed", tint = MaterialTheme.colorScheme.error)
        WorkoutStatus.SUBSTITUTED -> Icon(Icons.Filled.SwapHoriz, contentDescription = "Substituted", tint = MaterialTheme.colorScheme.tertiary)
        else -> Icon(Icons.Filled.Circle, contentDescription = "Planned", tint = MaterialTheme.colorScheme.outline)
    }
}
