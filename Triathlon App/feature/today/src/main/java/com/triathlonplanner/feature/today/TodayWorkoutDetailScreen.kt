package com.triathlonplanner.feature.today

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import com.triathlonplanner.core.model.Discipline
import com.triathlonplanner.core.model.MatchStatus
import com.triathlonplanner.core.model.WorkoutStatus
import com.triathlonplanner.core.model.WorkoutStepType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TodayWorkoutDetailScreen(onBack: () -> Unit, viewModel: TodayWorkoutDetailViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Workout Detail") },
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
                state.notFound -> Text("Workout not found.", modifier = Modifier.padding(24.dp))
                else -> Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    PlannedCard(state.planned!!, state.steps)
                    Spacer(Modifier.height(12.dp))
                    ActualCard(state.planned!!.status, state.actual)
                }
            }
        }
    }
}

@Composable
internal fun PlannedCard(planned: TodayWorkoutView, steps: List<StepView>) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Planned", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text(planned.title, style = MaterialTheme.typography.bodyLarge)
            Text("${planned.durationMin} min", style = MaterialTheme.typography.bodyMedium)
            planned.zoneLabel?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
            }
            if (steps.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                steps.forEach { StepRow(it) }
            }
        }
    }
}

@Composable
internal fun StepRow(step: StepView) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        val label = when (step.stepType) {
            WorkoutStepType.WARMUP -> "Warmup"
            WorkoutStepType.DRILL -> "Drill"
            WorkoutStepType.MAIN -> "Main set"
            WorkoutStepType.INTERVAL -> "Interval" + (step.repeatCount?.let { " (${it}x)" } ?: "")
            WorkoutStepType.RECOVERY -> "Recovery" + (step.repeatCount?.let { " (${it}x)" } ?: "")
            WorkoutStepType.COOLDOWN -> "Cooldown"
        }
        Text("$label - ${step.durationMin} min", style = MaterialTheme.typography.bodyMedium)
        step.zoneLabel?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
        step.cueText?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
internal fun ActualCard(plannedStatus: WorkoutStatus, actual: ActualWorkoutView?) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("What you actually did", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            if (actual == null) {
                Text(
                    when (plannedStatus) {
                        WorkoutStatus.MISSED -> "No activity recorded - this session was missed."
                        WorkoutStatus.PLANNED -> "Not completed yet."
                        else -> "No matching activity found."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                if (actual.matchStatus == MatchStatus.SUBSTITUTED) {
                    Text(
                        "Substituted: ${actual.discipline.name.lowercase().replaceFirstChar(Char::uppercase)}",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                } else {
                    Text(actual.discipline.name.lowercase().replaceFirstChar(Char::uppercase), style = MaterialTheme.typography.bodyLarge)
                }
                Text("${actual.durationMin} min", style = MaterialTheme.typography.bodyMedium)
                actual.distanceM?.let { meters ->
                    val distanceLabel = if (actual.discipline == Discipline.SWIM) {
                        "${meters.toInt()} m"
                    } else {
                        "%.1f km".format(meters / 1000)
                    }
                    Text(distanceLabel, style = MaterialTheme.typography.bodyMedium)
                }
                actual.avgHr?.let { Text("Avg HR: $it bpm", style = MaterialTheme.typography.bodyMedium) }
                actual.avgPowerW?.let { Text("Avg power: $it W", style = MaterialTheme.typography.bodyMedium) }
                Text("Load: ${actual.calculatedLoad}", style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
