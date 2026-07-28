package com.triathlonplanner.feature.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.triathlonplanner.core.model.TrainingPhase

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanScreen(onWeekClick: (Int) -> Unit, viewModel: PlanViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(topBar = { TopAppBar(title = { Text("Plan") }) }) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading -> CircularProgressIndicator(Modifier.padding(24.dp))
                !state.hasActivePlan -> Text("No active plan yet.", modifier = Modifier.padding(24.dp))
                else -> LazyColumn(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    items(state.weeks, key = { it.weekIndex }) { week ->
                        WeekCard(week, onClick = { onWeekClick(week.weekIndex) })
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekCard(week: PlanWeekUiModel, onClick: () -> Unit) {
    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp).clickable(onClick = onClick)) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PhaseDot(week.phase)
                androidx.compose.foundation.layout.Spacer(Modifier.padding(horizontal = 4.dp))
                Text(
                    "Week ${week.weekIndex} - ${week.phase.name.lowercase().replaceFirstChar(Char::uppercase)}" +
                        if (week.isRecoveryWeek) " (recovery)" else "",
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Text("Planned load: ${week.plannedWeeklyLoad}", style = MaterialTheme.typography.bodySmall)
            Text("${week.workouts.size} sessions - tap for details", style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun PhaseDot(phase: TrainingPhase) {
    val color = when (phase) {
        TrainingPhase.BASE -> Color(0xFF4C6479)
        TrainingPhase.BUILD -> Color(0xFF1B5FA8)
        TrainingPhase.PEAK -> Color(0xFFB8572C)
        TrainingPhase.TAPER -> Color(0xFF8E24AA)
        TrainingPhase.RACE_WEEK -> Color(0xFFC62828)
    }
    Box(modifier = Modifier.size(12.dp).background(color, CircleShape))
}
