package com.triathlonplanner.core.designsystem

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.triathlonplanner.core.model.Discipline
import com.triathlonplanner.core.model.MatchStatus
import com.triathlonplanner.core.model.WorkoutStatus
import com.triathlonplanner.core.model.WorkoutStepType

data class WorkoutStepView(
    val stepType: WorkoutStepType,
    val durationMin: Int,
    val zoneLabel: String?,
    /** 1..7. Drives the step's colour, so it must travel alongside the pre-formatted label. */
    val zoneLevel: Int?,
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

/**
 * One planned workout leg - usually a whole day's session, but a brick day has two (bike + run).
 * Shared between the Today and Plan tabs so "what was planned" vs "what you actually did"
 * (including substitutions) always renders identically wherever it appears.
 */
data class WorkoutLegView(
    val title: String,
    val discipline: Discipline,
    val durationMin: Int,
    val zoneLabel: String?,
    val zoneLevel: Int?,
    val status: WorkoutStatus,
    val steps: List<WorkoutStepView> = emptyList(),
    val actual: ActualWorkoutView? = null,
)

/** Badge, optional title, duration/zone and status - the identity line for a session. */
@Composable
fun WorkoutLegHeader(leg: WorkoutLegView, showTitle: Boolean, modifier: Modifier = Modifier) {
    val visual = visualFor(leg.discipline)
    val (statusLabel, statusTone) = statusToneFor(leg.status)
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        DisciplineBadge(visual.icon, visual.color, visual.label)
        Spacer(Modifier.width(AppSpacing.md))
        Column(Modifier.weight(1f)) {
            if (showTitle) {
                Text(leg.title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(2.dp))
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    formatDuration(leg.durationMin),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                leg.zoneLabel?.let { label ->
                    Text(
                        "  ·  ",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        label,
                        style = MaterialTheme.typography.bodySmall,
                        color = leg.zoneLevel?.let { colorForZone(it) } ?: MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        Spacer(Modifier.width(AppSpacing.sm))
        Pill(statusLabel, tone = statusTone)
    }
}

/**
 * Full session detail: header, step breakdown, then the completed-activity comparison.
 *
 * [showHeading] controls only the title inside the header - turn it on when a caller shows several
 * legs (a brick day) and each needs naming; leave it off when the title is already printed once
 * above this block.
 */
@Composable
fun WorkoutLegDetail(leg: WorkoutLegView, showHeading: Boolean, modifier: Modifier = Modifier) {
    Column(modifier = modifier.fillMaxWidth()) {
        WorkoutLegHeader(leg, showTitle = showHeading)

        if (leg.steps.isNotEmpty()) {
            Spacer(Modifier.height(AppSpacing.lg))
            SectionHeader("Session")
            Spacer(Modifier.height(AppSpacing.sm))
            leg.steps.forEachIndexed { index, step ->
                WorkoutStepRow(step)
                if (index < leg.steps.lastIndex) Spacer(Modifier.height(AppSpacing.sm))
            }
        }

        Spacer(Modifier.height(AppSpacing.lg))
        SectionHeader("What you actually did")
        Spacer(Modifier.height(AppSpacing.sm))
        ActualWorkoutSection(leg.status, leg.actual)
    }
}

/**
 * One step of the session. The rail is coloured by the step's *zone*, which turns a long interval
 * breakdown into a readable intensity spine - you can see the shape of the session down the left
 * edge before reading a word of it. Steps with no prescribed zone fall back to the hairline colour.
 */
@Composable
fun WorkoutStepRow(step: WorkoutStepView, modifier: Modifier = Modifier) {
    val label = when (step.stepType) {
        WorkoutStepType.WARMUP -> "Warm-up"
        WorkoutStepType.DRILL -> "Drill"
        WorkoutStepType.MAIN -> "Main set"
        WorkoutStepType.INTERVAL -> "Interval"
        WorkoutStepType.RECOVERY -> "Recovery"
        WorkoutStepType.COOLDOWN -> "Cool-down"
    }
    val zoneColor = step.zoneLevel?.let { colorForZone(it) }
    Row(modifier = modifier.fillMaxWidth()) {
        Box(
            Modifier
                .width(3.dp)
                .height(if (step.cueText != null) 50.dp else 32.dp)
                .clip(RoundedCornerShape(AppRadius.pill))
                .background(zoneColor ?: MaterialTheme.colorScheme.outline),
        )
        Spacer(Modifier.width(AppSpacing.md))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, style = MaterialTheme.typography.titleSmall)
                step.repeatCount?.let {
                    Spacer(Modifier.width(AppSpacing.sm))
                    Pill("${it}x", tone = zoneColor ?: MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.weight(1f))
                Text(
                    formatDuration(step.durationMin),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            step.zoneLabel?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = zoneColor ?: MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            step.cueText?.let {
                Spacer(Modifier.height(2.dp))
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Completed-activity summary, or a plain explanation of why there isn't one. */
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
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        return
    }

    val accents = MaterialTheme.accents
    Column(Modifier.fillMaxWidth()) {
        if (actual.matchStatus == MatchStatus.SUBSTITUTED) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(accents.warning.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(SubstitutionIcon, contentDescription = null, tint = accents.warning, modifier = Modifier.size(13.dp))
                }
                Spacer(Modifier.width(AppSpacing.sm))
                Text(
                    "You did a ${actual.disciplineLabel.lowercase()} instead",
                    style = MaterialTheme.typography.bodyMedium,
                    color = accents.warning,
                )
            }
            Spacer(Modifier.height(AppSpacing.md))
        }

        // Built as an explicitly typed mutable list so the first entry's null unit can't pin the
        // element type, and capped at four so the strip never crowds on a narrow screen.
        val metrics = mutableListOf<Metric>()
        metrics += Metric(formatDuration(actual.durationMin), null, "Time")
        actual.distanceM?.let { meters ->
            metrics += if (actual.discipline == Discipline.SWIM) {
                Metric("${meters.toInt()}", "m", "Distance")
            } else {
                Metric("%.1f".format(meters / 1000), "km", "Distance")
            }
        }
        actual.avgHr?.let { metrics += Metric("$it", "bpm", "Avg HR") }
        actual.avgPowerW?.let { metrics += Metric("$it", "W", "Avg power") }
        if (metrics.size < 4) metrics += Metric("${actual.calculatedLoad}", null, "Load")

        MetricRow(metrics = metrics.take(4))
    }
}
