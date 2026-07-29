package com.triathlonplanner.feature.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.triathlonplanner.core.model.PlanWeekSummary
import com.triathlonplanner.core.model.PlannedWorkoutSnapshot
import com.triathlonplanner.core.model.UserZoneProfile
import com.triathlonplanner.data.repository.PlanRepository
import com.triathlonplanner.data.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject

private data class LocalNav(val visibleMonth: YearMonth, val selectedDate: LocalDate)

private data class CalendarData(
    val weeks: List<PlanWeekSummary>,
    val workouts: List<PlannedWorkoutSnapshot>,
    val profile: UserZoneProfile?,
    val nav: LocalNav,
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlanViewModel @Inject constructor(
    private val planRepository: PlanRepository,
    private val profileRepository: ProfileRepository,
) : ViewModel() {

    private val localNav = MutableStateFlow(LocalNav(YearMonth.now(), LocalDate.now()))

    val uiState: StateFlow<PlanUiState> = planRepository.observeActivePlan()
        .flatMapLatest { plan ->
            if (plan == null) {
                flowOf(PlanUiState(isLoading = false, hasActivePlan = false))
            } else {
                combine(
                    planRepository.observeWeeksForPlan(plan.id),
                    planRepository.observeWorkoutsForPlan(plan.id),
                    profileRepository.observeProfile(),
                    localNav,
                ) { weeks, workouts, profile, nav -> CalendarData(weeks, workouts, profile, nav) }
                    .flatMapLatest { data -> buildUiState(data) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlanUiState())

    /** Builds the calendar's day-info map immediately, then (if the selected day has any
     * training) fetches full step/cue/actual detail per leg reactively - this is what lets the
     * Plan tab show complete workout detail directly on day selection, no extra tap needed. */
    private fun buildUiState(data: CalendarData): Flow<PlanUiState> {
        val workoutsByDate = data.workouts.groupBy { it.date }
        val dayInfo = data.weeks.flatMap { week ->
            (0..6).map { offset ->
                val date = week.startDate.plusDays(offset.toLong())
                date to CalendarDayInfo(
                    phase = week.phase,
                    isRecoveryWeek = week.isRecoveryWeek,
                    hasTraining = workoutsByDate[date]?.isNotEmpty() == true,
                )
            }
        }.toMap()

        val baseState = PlanUiState(
            hasActivePlan = true,
            isLoading = false,
            visibleMonth = data.nav.visibleMonth,
            dayInfo = dayInfo,
            selectedDate = data.nav.selectedDate,
        )

        val selectedWorkouts = workoutsByDate[data.nav.selectedDate].orEmpty().sortedBy { it.sortOrderInDay }
        if (selectedWorkouts.isEmpty()) {
            return flowOf(baseState)
        }

        val legFlows = selectedWorkouts.map { workout ->
            combine(
                planRepository.observeStepsForWorkout(workout.id),
                planRepository.observeWorkoutDetail(workout.id),
            ) { steps, (_, activity) -> workout.toLegView(data.profile, steps, activity) }
        }
        return combine(legFlows) { legs -> baseState.copy(selectedDay = legs.toList().toDayPlanView(data.nav.selectedDate)) }
    }

    fun selectDate(date: LocalDate) = localNav.update { it.copy(selectedDate = date) }
    fun goToPreviousMonth() = localNav.update { it.copy(visibleMonth = it.visibleMonth.minusMonths(1)) }
    fun goToNextMonth() = localNav.update { it.copy(visibleMonth = it.visibleMonth.plusMonths(1)) }
}
