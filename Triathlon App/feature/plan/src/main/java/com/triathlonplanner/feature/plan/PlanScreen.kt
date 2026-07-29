package com.triathlonplanner.feature.plan

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.triathlonplanner.core.model.Discipline
import com.triathlonplanner.core.model.MatchStatus
import com.triathlonplanner.core.model.TrainingPhase
import com.triathlonplanner.core.model.WorkoutStatus
import com.triathlonplanner.core.model.WorkoutStepType
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

private val WEEKDAY_LABELS = listOf(DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY)
    .map { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlanScreen(viewModel: PlanViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val today = LocalDate.now()

    Scaffold { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading -> CircularProgressIndicator(Modifier.padding(24.dp))
                !state.hasActivePlan -> Text("No active plan yet.", modifier = Modifier.padding(24.dp))
                else -> Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .verticalScroll(rememberScrollState()),
                ) {
                    MonthHeader(state.visibleMonth, onPrev = viewModel::goToPreviousMonth, onNext = viewModel::goToNextMonth)
                    WeekdayHeaderRow()
                    CalendarGrid(
                        month = state.visibleMonth,
                        today = today,
                        selectedDate = state.selectedDate,
                        dayInfo = state.dayInfo,
                        onDayClick = viewModel::selectDate,
                    )
                    Spacer(Modifier.height(8.dp))
                    SelectedDayPanel(state.selectedDate, state.selectedDay, state.dayInfo[state.selectedDate] != null)
                }
            }
        }
    }
}

@Composable
private fun MonthHeader(month: YearMonth, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrev) { Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous month") }
        Text(
            "${month.month.getDisplayName(TextStyle.FULL, Locale.getDefault())} ${month.year}",
            style = MaterialTheme.typography.titleMedium,
        )
        IconButton(onClick = onNext) { Icon(Icons.Filled.ChevronRight, contentDescription = "Next month") }
    }
}

@Composable
private fun WeekdayHeaderRow() {
    Row(modifier = Modifier.fillMaxWidth()) {
        WEEKDAY_LABELS.forEach { label ->
            Text(
                label,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun CalendarGrid(
    month: YearMonth,
    today: LocalDate,
    selectedDate: LocalDate,
    dayInfo: Map<LocalDate, CalendarDayInfo>,
    onDayClick: (LocalDate) -> Unit,
) {
    val firstOfMonth = month.atDay(1)
    val leadingBlanks = (firstOfMonth.dayOfWeek.value - DayOfWeek.MONDAY.value + 7) % 7
    val daysInMonth = month.lengthOfMonth()
    val totalCells = leadingBlanks + daysInMonth
    val rows = (totalCells + 6) / 7

    Column {
        for (row in 0 until rows) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (col in 0 until 7) {
                    val cellIndex = row * 7 + col
                    val dayNum = cellIndex - leadingBlanks + 1
                    Box(modifier = Modifier.weight(1f).aspectRatio(1f).padding(2.dp)) {
                        if (dayNum in 1..daysInMonth) {
                            val date = month.atDay(dayNum)
                            DayCell(
                                date = date,
                                isToday = date == today,
                                isSelected = date == selectedDate,
                                info = dayInfo[date],
                                onClick = { onDayClick(date) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCell(date: LocalDate, isToday: Boolean, isSelected: Boolean, info: CalendarDayInfo?, onClick: () -> Unit) {
    val backgroundColor = if (info != null) phaseColor(info.phase).copy(alpha = if (isSelected) 0.9f else 0.35f) else Color.Transparent
    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(8.dp))
            .background(backgroundColor, RoundedCornerShape(8.dp))
            .then(
                if (isToday) Modifier.border(2.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp)) else Modifier,
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(date.dayOfMonth.toString(), style = MaterialTheme.typography.bodyMedium)
            if (info?.hasTraining == true) {
                Box(modifier = Modifier.size(4.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
            }
        }
    }
}

private fun phaseColor(phase: TrainingPhase): Color = when (phase) {
    TrainingPhase.BASE -> Color(0xFF4C6479)
    TrainingPhase.BUILD -> Color(0xFF1B5FA8)
    TrainingPhase.PEAK -> Color(0xFFB8572C)
    TrainingPhase.TAPER -> Color(0xFF8E24AA)
    TrainingPhase.RACE_WEEK -> Color(0xFFC62828)
}

@Composable
private fun SelectedDayPanel(date: LocalDate, day: DayPlanView?, isWithinPlan: Boolean) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text("$date", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        when {
            day != null -> {
                Text(day.title, style = MaterialTheme.typography.titleMedium)
                Text("${day.totalDurationMin} min - load ${day.totalLoad}", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(12.dp))
                day.legs.forEachIndexed { index, leg ->
                    LegDetail(leg, showHeading = day.legs.size > 1)
                    if (index < day.legs.lastIndex) {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))
                    }
                }
            }
            isWithinPlan -> Text("Rest Day", style = MaterialTheme.typography.titleMedium)
            else -> Text("No plan for this day.", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun LegDetail(leg: DayLegView, showHeading: Boolean) {
    Column(modifier = Modifier.fillMaxWidth()) {
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
            leg.steps.forEach { StepRow(it) }
        }
        Spacer(Modifier.height(12.dp))
        Text("What you actually did", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(4.dp))
        ActualSection(leg.status, leg.actual)
    }
}

@Composable
private fun StepRow(step: PlanStepView) {
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
private fun ActualSection(plannedStatus: WorkoutStatus, actual: PlanActualWorkoutView?) {
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
