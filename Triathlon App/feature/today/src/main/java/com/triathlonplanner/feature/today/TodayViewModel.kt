package com.triathlonplanner.feature.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.triathlonplanner.data.repository.PlanRepository
import com.triathlonplanner.data.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class TodayViewModel @Inject constructor(
    planRepository: PlanRepository,
    profileRepository: ProfileRepository,
) : ViewModel() {

    val uiState: StateFlow<TodayUiState> = combine(
        planRepository.observeWorkoutsForDate(LocalDate.now()),
        profileRepository.observeProfile(),
    ) { workouts, profile ->
        TodayUiState(
            workouts = workouts.map { it.toTodayView(profile) },
            isLoading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState())
}
