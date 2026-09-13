package com.triathlonplanner.feature.today

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.triathlonplanner.data.repository.PlanRepository
import com.triathlonplanner.data.repository.ProfileRepository
import com.triathlonplanner.data.repository.RaceGoalRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class TodayWorkoutDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    planRepository: PlanRepository,
    profileRepository: ProfileRepository,
    raceGoalRepository: RaceGoalRepository,
) : ViewModel() {

    private val workoutId: Long = checkNotNull(savedStateHandle["workoutId"])

    val uiState: StateFlow<TodayWorkoutDetailUiState> = combine(
        planRepository.observeWorkoutDetail(workoutId),
        profileRepository.observeProfile(),
        planRepository.observeStepsForWorkout(workoutId),
        raceGoalRepository.observeActive(),
    ) { (planned, activity), profile, steps, goal ->
        if (planned == null) {
            TodayWorkoutDetailUiState(isLoading = false, notFound = true)
        } else {
            TodayWorkoutDetailUiState(
                legDetail = planned.toWorkoutLegView(profile, goal, steps, activity),
                isLoading = false,
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayWorkoutDetailUiState())
}
