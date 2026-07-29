package com.triathlonplanner.feature.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.triathlonplanner.data.repository.PlanRepository
import com.triathlonplanner.data.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
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
                ) { weeks, workouts, profile, nav ->
                    val workoutsByDate = workouts.groupBy { it.date }
                    val dayInfo = weeks.flatMap { week ->
                        (0..6).map { offset ->
                            val date = week.startDate.plusDays(offset.toLong())
                            date to CalendarDayInfo(
                                phase = week.phase,
                                isRecoveryWeek = week.isRecoveryWeek,
                                hasTraining = workoutsByDate[date]?.isNotEmpty() == true,
                            )
                        }
                    }.toMap()
                    PlanUiState(
                        hasActivePlan = true,
                        isLoading = false,
                        visibleMonth = nav.visibleMonth,
                        dayInfo = dayInfo,
                        selectedDate = nav.selectedDate,
                        selectedDay = workoutsByDate[nav.selectedDate]
                            ?.takeIf { it.isNotEmpty() }
                            ?.toDayPlanView(nav.selectedDate, profile),
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlanUiState())

    fun selectDate(date: LocalDate) = localNav.update { it.copy(selectedDate = date) }
    fun goToPreviousMonth() = localNav.update { it.copy(visibleMonth = it.visibleMonth.minusMonths(1)) }
    fun goToNextMonth() = localNav.update { it.copy(visibleMonth = it.visibleMonth.plusMonths(1)) }
}
