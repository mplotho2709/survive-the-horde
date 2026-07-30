package com.triathlonplanner.feature.today

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.triathlonplanner.core.designsystem.AppCard
import com.triathlonplanner.core.designsystem.AppSpacing
import com.triathlonplanner.core.designsystem.DisciplineBadge
import com.triathlonplanner.core.designsystem.EmptyState
import com.triathlonplanner.core.designsystem.Pill
import com.triathlonplanner.core.designsystem.WorkoutLegDetail
import com.triathlonplanner.core.designsystem.formatDuration
import com.triathlonplanner.core.designsystem.statusToneFor
import com.triathlonplanner.core.designsystem.visualFor
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun TodayScreen(onWorkoutClick: (Long) -> Unit, viewModel: TodayViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val today = LocalDate.now()

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when {
                state.isLoading -> CircularProgressIndicator(Modifier.padding(AppSpacing.xl))

                state.workouts.isEmpty() -> Column(
                    Modifier.fillMaxWidth().padding(horizontal = AppSpacing.gutter),
                ) {
                    TodayHeader(today, sessionCount = 0)
                    Spacer(Modifier.height(AppSpacing.lg))
                    AppCard(Modifier.fillMaxWidth()) {
                        EmptyState(
                            title = "Rest day",
                            subtitle = "Nothing scheduled. Recovery is when the training you already did actually lands.",
                            icon = Icons.Filled.Bedtime,
                        )
                    }
                }

                // Exactly one session today - show the full breakdown inline, no extra tap needed.
                state.workouts.size == 1 && state.singleWorkoutDetail != null -> Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = AppSpacing.gutter),
                ) {
                    TodayHeader(today, sessionCount = 1)
                    Spacer(Modifier.height(AppSpacing.lg))
                    AppCard(Modifier.fillMaxWidth()) {
                        Text(state.singleWorkoutDetail!!.title, style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.height(AppSpacing.md))
                        WorkoutLegDetail(state.singleWorkoutDetail!!, showHeading = false)
                    }
                    Spacer(Modifier.height(AppSpacing.xl))
                }

                else -> LazyColumn(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(
                        start = AppSpacing.gutter,
                        end = AppSpacing.gutter,
                        bottom = AppSpacing.xl,
                    ),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.md),
                ) {
                    item { TodayHeader(today, sessionCount = state.workouts.size) }
                    items(state.workouts, key = { it.id }) { workout ->
                        WorkoutRowCard(workout, onClick = { onWorkoutClick(workout.id) })
                    }
                }
            }
        }
    }
}

/** Large date and session count - sets "what am I doing today" before any detail. */
@Composable
private fun TodayHeader(date: LocalDate, sessionCount: Int) {
    Column(Modifier.padding(top = AppSpacing.xl)) {
        Text(
            date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault()).uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
        )
        Spacer(Modifier.height(AppSpacing.xs))
        Text(
            "${date.dayOfMonth} ${date.month.getDisplayName(TextStyle.FULL, Locale.getDefault())}",
            style = MaterialTheme.typography.displaySmall,
        )
        Spacer(Modifier.height(AppSpacing.xs))
        Text(
            when (sessionCount) {
                0 -> "No sessions scheduled"
                1 -> "1 session"
                else -> "$sessionCount sessions"
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Compact tappable session row, used when the day has more than one session. */
@Composable
private fun WorkoutRowCard(workout: TodayWorkoutView, onClick: () -> Unit) {
    val visual = visualFor(workout.discipline)
    val (statusLabel, statusTone) = statusToneFor(workout.status)

    AppCard(modifier = Modifier.fillMaxWidth(), onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Neutral like every other sport badge in the app - the glyph names the sport, and
            // colour is reserved for training intensity.
            DisciplineBadge(visual.icon, MaterialTheme.colorScheme.onSurfaceVariant, visual.label)
            Spacer(Modifier.width(AppSpacing.md))
            Column(Modifier.weight(1f)) {
                Text(workout.title, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(2.dp))
                Text(
                    buildString {
                        append(formatDuration(workout.durationMin))
                        workout.zoneLabel?.let { append("  ·  $it") }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(AppSpacing.sm))
            Pill(statusLabel, tone = statusTone)
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = AppSpacing.xs),
            )
        }
    }
}
