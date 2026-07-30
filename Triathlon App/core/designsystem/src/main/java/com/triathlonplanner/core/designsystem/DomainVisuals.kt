package com.triathlonplanner.core.designsystem

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Pool
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.triathlonplanner.core.model.Discipline
import com.triathlonplanner.core.model.TrainingPhase
import com.triathlonplanner.core.model.WorkoutStatus

/** Icon, colour and label for a discipline, so every surface renders one identically. */
data class DisciplineVisual(val icon: ImageVector, val color: Color, val label: String)

@Composable
@ReadOnlyComposable
fun visualFor(discipline: Discipline): DisciplineVisual {
    val accents = LocalAppAccents.current
    return when (discipline) {
        Discipline.SWIM -> DisciplineVisual(Icons.Filled.Pool, accents.swim, "Swim")
        Discipline.BIKE -> DisciplineVisual(Icons.Filled.DirectionsBike, accents.bike, "Bike")
        Discipline.RUN -> DisciplineVisual(Icons.Filled.DirectionsRun, accents.run, "Run")
        // A brick leg *is* a bike or a run, so it carries that hue rather than a fourth identity -
        // which is also what keeps the categorical palette at three validated slots.
        Discipline.BRICK_BIKE -> DisciplineVisual(Icons.Filled.DirectionsBike, accents.bike, "Bike")
        Discipline.BRICK_RUN -> DisciplineVisual(Icons.Filled.DirectionsRun, accents.run, "Run")
        Discipline.STRENGTH -> DisciplineVisual(Icons.Filled.FitnessCenter, accents.neutral, "Strength")
        Discipline.REST -> DisciplineVisual(Icons.Filled.Bedtime, accents.neutral, "Rest")
    }
}

@Composable
@ReadOnlyComposable
fun colorFor(phase: TrainingPhase): Color {
    val accents = LocalAppAccents.current
    return when (phase) {
        TrainingPhase.BASE -> accents.phaseBase
        TrainingPhase.BUILD -> accents.phaseBuild
        TrainingPhase.PEAK -> accents.phasePeak
        TrainingPhase.TAPER -> accents.phaseTaper
        TrainingPhase.RACE_WEEK -> accents.phaseRaceWeek
    }
}

fun labelFor(phase: TrainingPhase): String = when (phase) {
    TrainingPhase.BASE -> "Base"
    TrainingPhase.BUILD -> "Build"
    TrainingPhase.PEAK -> "Peak"
    TrainingPhase.TAPER -> "Taper"
    TrainingPhase.RACE_WEEK -> "Race week"
}

/** Status label plus the tone to draw it in. All six [WorkoutStatus] values are handled. */
@Composable
@ReadOnlyComposable
fun statusToneFor(status: WorkoutStatus): Pair<String, Color> {
    val accents = LocalAppAccents.current
    return when (status) {
        WorkoutStatus.PLANNED -> "Planned" to accents.neutral
        WorkoutStatus.COMPLETED -> "Done" to accents.success
        WorkoutStatus.MISSED -> "Missed" to accents.danger
        WorkoutStatus.SUBSTITUTED -> "Swapped" to accents.warning
        // Both mean the adaptation engine rewrote the session rather than the athlete missing it,
        // so they read as neutral information, not as a failure.
        WorkoutStatus.MODIFIED -> "Adjusted" to accents.neutral
        WorkoutStatus.SKIPPED_BY_ADAPTATION -> "Adjusted" to accents.neutral
    }
}

/**
 * Colour for an intensity zone level (1..7). Clamped rather than throwing: a zone level is data from
 * the database, and a rendering helper is the wrong place to crash over an out-of-range value.
 */
@Composable
@ReadOnlyComposable
fun colorForZone(level: Int): Color {
    val zones = LocalAppAccents.current.zones
    return zones[(level - 1).coerceIn(0, zones.lastIndex)]
}

val SubstitutionIcon: ImageVector get() = Icons.Filled.SwapHoriz

/** "1h 25m" / "45m" - compact duration used wherever a session length appears. */
fun formatDuration(minutes: Int): String {
    if (minutes < 60) return "${minutes}m"
    val hours = minutes / 60
    val remainder = minutes % 60
    return if (remainder == 0) "${hours}h" else "${hours}h ${remainder}m"
}
