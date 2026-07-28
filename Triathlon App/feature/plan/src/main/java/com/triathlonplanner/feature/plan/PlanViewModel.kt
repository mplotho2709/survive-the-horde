package com.triathlonplanner.feature.plan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.triathlonplanner.data.repository.PlanRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class PlanViewModel @Inject constructor(
    planRepository: PlanRepository,
) : ViewModel() {

    val uiState: StateFlow<PlanUiState> = planRepository.observeActivePlan()
        .flatMapLatest { plan ->
            if (plan == null) {
                flowOf(PlanUiState(isLoading = false, hasActivePlan = false))
            } else {
                combine(
                    planRepository.observeWeeksForPlan(plan.id),
                    planRepository.observeWorkoutsForPlan(plan.id),
                ) { weeks, workouts ->
                    val workoutsByWeek = workouts.groupBy { it.weekIndex }
                    PlanUiState(
                        weeks = weeks.sortedBy { it.weekIndex }.map { week ->
                            PlanWeekUiModel(
                                weekIndex = week.weekIndex,
                                phase = week.phase,
                                isRecoveryWeek = week.isRecoveryWeek,
                                plannedWeeklyLoad = week.plannedWeeklyLoad,
                                workouts = workoutsByWeek[week.weekIndex].orEmpty(),
                            )
                        },
                        isLoading = false,
                        hasActivePlan = true,
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlanUiState())
}
