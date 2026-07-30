package com.triathlonplanner.feature.plan

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
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

/** Weeks rendered per month - fixed so the grid is the same height for every month. */
private const val CALENDAR_ROWS = 6

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
                    SwipeableCalendar(
                        month = state.visibleMonth,
                        today = today,
                        selectedDate = state.selectedDate,
                        dayInfo = state.dayInfo,
                        onDayClick = viewModel::selectDate,
                        onPreviousMonth = viewModel::goToPreviousMonth,
                        onNextMonth = viewModel::goToNextMonth,
                    )
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

/** Horizontal drag distance that commits a month change. Short enough to feel light, long enough
 * that a slightly-off vertical scroll doesn't flip the month by accident. */
private val MONTH_SWIPE_THRESHOLD = 56.dp

/**
 * The calendar card, swipeable left/right to change month.
 *
 * Uses a horizontal [draggable] rather than a pager: the calendar lives inside a vertically
 * scrolling column, and an orientation-locked drag cooperates with that scroller instead of
 * competing with it for the same gesture. The trade-off is that the grid doesn't track the finger -
 * so the month slides in on release, which is what makes the swipe feel like it registered.
 */
@Composable
private fun SwipeableCalendar(
    month: YearMonth,
    today: LocalDate,
    selectedDate: LocalDate,
    dayInfo: Map<LocalDate, CalendarDayInfo>,
    onDayClick: (LocalDate) -> Unit,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
) {
    val thresholdPx = with(LocalDensity.current) { MONTH_SWIPE_THRESHOLD.toPx() }
    // Held as the state object rather than a delegated var so the remembered drag lambda mutates
    // the same instance across recompositions.
    val dragTotal = remember { mutableFloatStateOf(0f) }

    AppCard(
        modifier = Modifier
            .fillMaxWidth()
            .draggable(
                orientation = Orientation.Horizontal,
                state = rememberDraggableState { delta -> dragTotal.floatValue += delta },
                onDragStarted = { dragTotal.floatValue = 0f },
                onDragStopped = {
                    val travelled = dragTotal.floatValue
                    dragTotal.floatValue = 0f
                    when {
                        travelled <= -thresholdPx -> onNextMonth()
                        travelled >= thresholdPx -> onPreviousMonth()
                    }
                },
            ),
        contentPadding = PaddingValues(AppSpacing.sm),
    ) {
        // Weekday labels are identical every month, so they stay put while only the grid animates.
        WeekdayHeaderRow()
        Spacer(Modifier.height(AppSpacing.xs))
        AnimatedContent(
            targetState = month,
            transitionSpec = {
                val forward = targetState > initialState
                val spec = tween<IntOffset>(durationMillis = 220)
                slideInHorizontally(spec) { width -> if (forward) width else -width } togetherWith
                    slideOutHorizontally(spec) { width -> if (forward) -width else width }
            },
            label = "monthGrid",
        ) { animatedMonth ->
            CalendarGrid(
                month = animatedMonth,
                today = today,
                selectedDate = selectedDate,
                dayInfo = dayInfo,
                onDayClick = onDayClick,
            )
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

    Column {
        // Always six rows. A month needing only five would otherwise shrink the card mid-swipe and
        // jerk the day panel below it upward, which reads as a glitch rather than a transition.
        for (row in 0 until CALENDAR_ROWS) {
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
