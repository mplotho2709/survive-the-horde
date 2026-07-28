package com.triathlonplanner.feature.plan

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.triathlonplanner.data.repository.PlanRepository
import com.triathlonplanner.data.repository.ProfileRepository
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
class PlanWeekDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    planRepository: PlanRepository,
    profileRepository: ProfileRepository,
) : ViewModel() {

    private val weekIndex: Int = checkNotNull(savedStateHandle["weekIndex"])

    val uiState: StateFlow<PlanWeekDetailUiState> = planRepository.observeActivePlan()
        .flatMapLatest { plan ->
            if (plan == null) {
                flowOf(PlanWeekDetailUiState(weekIndex = weekIndex, isLoading = false))
            } else {
                combine(
                    planRepository.observeWeeksForPlan(plan.id),
                    planRepository.observeWorkoutsForPlan(plan.id),
                    profileRepository.observeProfile(),
                ) { weeks, workouts, profile ->
                    val week = weeks.firstOrNull { it.weekIndex == weekIndex }
                    PlanWeekDetailUiState(
                        weekIndex = weekIndex,
                        phase = week?.phase,
                        isRecoveryWeek = week?.isRecoveryWeek ?: false,
                        plannedWeeklyLoad = week?.plannedWeeklyLoad ?: 0,
                        workouts = workouts
                            .filter { it.weekIndex == weekIndex }
                            .sortedWith(compareBy({ it.date }, { it.sortOrderInDay }))
                            .map { it.toDetailView(profile) },
                        isLoading = false,
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PlanWeekDetailUiState(weekIndex = weekIndex))
}
