package com.triathlonplanner.core.designsystem

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.triathlonplanner.core.model.Discipline
import com.triathlonplanner.core.model.MatchStatus
import com.triathlonplanner.core.model.WorkoutStatus
import com.triathlonplanner.core.model.WorkoutStepType

data class WorkoutStepView(
    val stepType: WorkoutStepType,
    val durationMin: Int,
    val zoneLabel: String?,
    val repeatCount: Int?,
    val cueText: String?,
)

data class ActualWorkoutView(
    val discipline: Discipline,
    val disciplineLabel: String,
    val durationMin: Int,
    val distanceM: Double?,
    val avgHr: Int?,
    val avgPowerW: Int?,
    val calculatedLoad: Int,
    val matchStatus: MatchStatus,
)

/** One planned workout leg - usually a whole day's session, but a brick day has two (bike + run).
 * Shared between the Today and Plan tabs so "what was planned" vs. "what you actually did"
 * (including substitutions) always renders the same way regardless of where it's shown. */
data class WorkoutLegView(
    val title: String,
    val durationMin: Int,
    val zoneLabel: String?,
    val status: WorkoutStatus,
    val steps: List<WorkoutStepView> = emptyList(),
    val actual: ActualWorkoutView? = null,
)

/** [showHeading] repeats [WorkoutLegView.title] above the duration/zone/steps block - turn it on
 * when a caller already shows its own heading above multiple legs (e.g. a brick day) and needs
 * each leg individually labeled; leave it off when the title is shown once, above this block. */
@Composable
fun WorkoutLegDetail(leg: WorkoutLegView, showHeading: Boolean, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (showHeading) {
            Text(leg.title, style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(4.dp))
        }
        Text("${leg.durationMin} min", style = MaterialTheme.typography.bodyMedium)
        leg.zoneLabel?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
        }
        if (leg.steps.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            leg.steps.forEach { WorkoutStepRow(it) }
        }
        Spacer(Modifier.height(12.dp))
        Text("What you actually did", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(4.dp))
        ActualWorkoutSection(leg.status, leg.actual)
    }
}

@Composable
fun WorkoutStepRow(step: WorkoutStepView) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        val label = when (step.stepType) {
            WorkoutStepType.WARMUP -> "Warmup"
            WorkoutStepType.DRILL -> "Drill"
            WorkoutStepType.MAIN -> "Main set"
            WorkoutStepType.INTERVAL -> "Interval" + (step.repeatCount?.let { " (${it}x)" } ?: "")
            WorkoutStepType.RECOVERY -> "Recovery" + (step.repeatCount?.let { " (${it}x)" } ?: "")
            WorkoutStepType.COOLDOWN -> "Cooldown"
        }
        Text("$label - ${step.durationMin} min", style = MaterialTheme.typography.bodySmall)
        step.zoneLabel?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
        step.cueText?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
    }
}

@Composable
fun ActualWorkoutSection(plannedStatus: WorkoutStatus, actual: ActualWorkoutView?) {
    if (actual == null) {
        Text(
            when (plannedStatus) {
                WorkoutStatus.MISSED -> "No activity recorded - this session was missed."
                WorkoutStatus.PLANNED -> "Not completed yet."
                else -> "No matching activity found."
            },
            style = MaterialTheme.typography.bodyMedium,
        )
        return
    }
    if (actual.matchStatus == MatchStatus.SUBSTITUTED) {
        Text("Substituted: ${actual.disciplineLabel}", style = MaterialTheme.typography.bodyMedium)
    } else {
        Text(actual.disciplineLabel, style = MaterialTheme.typography.bodyMedium)
    }
    Text("${actual.durationMin} min", style = MaterialTheme.typography.bodyMedium)
    actual.distanceM?.let { meters ->
        val distanceLabel = if (actual.discipline == Discipline.SWIM) "${meters.toInt()} m" else "%.1f km".format(meters / 1000)
        Text(distanceLabel, style = MaterialTheme.typography.bodyMedium)
    }
    actual.avgHr?.let { Text("Avg HR: $it bpm", style = MaterialTheme.typography.bodyMedium) }
    actual.avgPowerW?.let { Text("Avg power: $it W", style = MaterialTheme.typography.bodyMedium) }
    Text("Load: ${actual.calculatedLoad}", style = MaterialTheme.typography.bodyMedium)
}
