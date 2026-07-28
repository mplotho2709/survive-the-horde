package com.triathlonplanner.feature.plan

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanWeekDetailScreen(onBack: () -> Unit, viewModel: PlanWeekDetailViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Week ${state.weekIndex}" +
                            (state.phase?.let { " - ${it.name.lowercase().replaceFirstChar(Char::uppercase)}" }.orEmpty()) +
                            if (state.isRecoveryWeek) " (recovery)" else "",
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading -> CircularProgressIndicator(Modifier.padding(24.dp))
                state.workouts.isEmpty() -> Text("No workouts this week.", modifier = Modifier.padding(24.dp))
                else -> LazyColumn(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    items(state.workouts, key = { it.id }) { workout -> WorkoutDetailCard(workout) }
                }
            }
        }
    }
}

@Composable
private fun WorkoutDetailCard(workout: PlanWorkoutDetailView) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "${workout.date} - ${workout.workoutType.name.lowercase().replaceFirstChar(Char::uppercase)} " +
                    workout.discipline.name.lowercase().replaceFirstChar(Char::uppercase),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.padding(top = 4.dp))
            Text("${workout.durationMin} min - load ${workout.plannedLoad}", style = MaterialTheme.typography.bodyMedium)
            workout.zoneLabel?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}
