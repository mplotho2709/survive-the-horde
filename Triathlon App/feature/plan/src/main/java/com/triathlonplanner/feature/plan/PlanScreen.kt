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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.triathlonplanner.core.designsystem.AppCard
import com.triathlonplanner.core.designsystem.AppDivider
import com.triathlonplanner.core.designsystem.AppRadius
import com.triathlonplanner.core.designsystem.AppSpacing
import com.triathlonplanner.core.designsystem.EmptyState
import com.triathlonplanner.core.designsystem.Metric
import com.triathlonplanner.core.designsystem.MetricRow
import com.triathlonplanner.core.designsystem.Pill
import com.triathlonplanner.core.designsystem.WorkoutLegDetail
import com.triathlonplanner.core.designsystem.colorFor
import com.triathlonplanner.core.designsystem.formatDuration
import com.triathlonplanner.core.designsystem.labelFor
import com.triathlonplanner.core.designsystem.visualFor
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

private val WEEKDAYS = listOf(
    DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY, DayOfWeek.THURSDAY,
    DayOfWeek.FRIDAY, DayOfWeek.SATURDAY, DayOfWeek.SUNDAY,
)

@Composable
fun PlanScreen(viewModel: PlanViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val today = LocalDate.now()

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading -> CircularProgressIndicator(Modifier.padding(AppSpacing.xl))
                !state.hasActivePlan -> EmptyState(
                    title = "No active plan",
                    subtitle = "Set a race goal to generate a training plan.",
                )
                else -> Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = AppSpacing.gutter),
                ) {
                    MonthHeader(
                        month = state.visibleMonth,
                        onPrev = viewModel::goToPreviousMonth,
                        onNext = viewModel::goToNextMonth,
                    )
                    AppCard(
                        modifier = Modifier.fillMaxWidth(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(AppSpacing.sm),
                    ) {
                        WeekdayHeaderRow()
                        Spacer(Modifier.height(AppSpacing.xs))
                        CalendarGrid(
                            month = state.visibleMonth,
                            today = today,
                            selectedDate = state.selectedDate,
                            dayInfo = state.dayInfo,
                            onDayClick = viewModel::selectDate,
                        )
                    }
                    Spacer(Modifier.height(AppSpacing.md))
                    SelectedDayPanel(
                        date = state.selectedDate,
                        day = state.selectedDay,
                        info = state.dayInfo[state.selectedDate],
                    )
                    Spacer(Modifier.height(AppSpacing.xl))
                }
            }
        }
    }
}

@Composable
private fun MonthHeader(month: YearMonth, onPrev: () -> Unit, onNext: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = AppSpacing.sm),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onPrev) {
            Icon(Icons.Filled.ChevronLeft, contentDescription = "Previous month")
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                month.month.getDisplayName(TextStyle.FULL, Locale.getDefault()),
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                month.year.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onNext) {
            Icon(Icons.Filled.ChevronRight, contentDescription = "Next month")
        }
    }
}

@Composable
private fun WeekdayHeaderRow() {
    Row(modifier = Modifier.fillMaxWidth()) {
        WEEKDAYS.forEach { day ->
            Text(
                day.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
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
    val leadingBlanks = (month.atDay(1).dayOfWeek.value - DayOfWeek.MONDAY.value + 7) % 7
    val daysInMonth = month.lengthOfMonth()
    val rows = (leadingBlanks + daysInMonth + 6) / 7

    Column {
        for (row in 0 until rows) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (col in 0 until 7) {
                    val dayNum = row * 7 + col - leadingBlanks + 1
                    Box(modifier = Modifier.weight(1f).aspectRatio(1f).padding(1.dp)) {
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

/**
 * A day cell. The phase tint is a faint wash so the month reads as blocks of training at a glance;
 * selection is a solid ring rather than a fill, so the discipline dots stay legible instead of
 * being swamped by a saturated background.
 */
@Composable
private fun DayCell(
    date: LocalDate,
    isToday: Boolean,
    isSelected: Boolean,
    info: CalendarDayInfo?,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(AppRadius.cell)
    val phaseWash = info?.let { colorFor(it.phase).copy(alpha = 0.30f) } ?: Color.Transparent
    val borderColor = when {
        isSelected -> MaterialTheme.colorScheme.onSurface
        isToday -> MaterialTheme.colorScheme.primary
        else -> Color.Transparent
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clip(shape)
            .background(phaseWash, shape)
            .border(if (isSelected || isToday) 2.dp else 0.dp, borderColor, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                date.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Normal,
            )
            if (info != null && info.disciplines.isNotEmpty()) {
                Spacer(Modifier.height(1.dp))
                // Sport is shown as a *symbol* rather than a colour: a bike icon says "bike"
                // outright, where a coloured dot needs a legend the calendar has no room for.
                // Drawn in ink rather than the discipline hue for the same reason - the shape is
                // doing the work. Capped at three, which is more sessions than any day holds.
                Row(horizontalArrangement = Arrangement.spacedBy(1.dp)) {
                    info.disciplines.take(3).forEach { discipline ->
                        val visual = visualFor(discipline)
                        Icon(
                            visual.icon,
                            contentDescription = visual.label,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(11.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectedDayPanel(date: LocalDate, day: DayPlanView?, info: CalendarDayInfo?) {
    AppCard(Modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()),
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    "${date.dayOfMonth} ${date.month.getDisplayName(TextStyle.SHORT, Locale.getDefault())}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            info?.let {
                if (it.isRecoveryWeek) {
                    Pill("Recovery", tone = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.size(AppSpacing.xs))
                }
                Pill(labelFor(it.phase), tone = MaterialTheme.colorScheme.primary)
            }
        }

        when {
            day != null -> {
                if (day.legs.size > 1) {
                    Spacer(Modifier.height(AppSpacing.lg))
                    MetricRow(
                        metrics = listOf(
                            Metric(formatDuration(day.totalDurationMin), null, "Total"),
                            Metric("${day.legs.size}", null, "Sessions"),
                            Metric("${day.totalLoad}", null, "Load"),
                        ),
                    )
                }
                Spacer(Modifier.height(AppSpacing.lg))
                day.legs.forEachIndexed { index, leg ->
                    WorkoutLegDetail(leg.detail, showHeading = day.legs.size > 1)
                    if (index < day.legs.lastIndex) {
                        Spacer(Modifier.height(AppSpacing.lg))
                        AppDivider()
                        Spacer(Modifier.height(AppSpacing.lg))
                    }
                }
            }
            info != null -> {
                Spacer(Modifier.height(AppSpacing.sm))
                EmptyState(title = "Rest day", subtitle = "No training scheduled.")
            }
            else -> {
                Spacer(Modifier.height(AppSpacing.sm))
                EmptyState(title = "Outside your plan", subtitle = "This day falls before or after your training block.")
            }
        }
    }
}
