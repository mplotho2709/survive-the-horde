package com.triathlonplanner.feature.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.triathlonplanner.data.repository.PlanRepository
import com.triathlonplanner.data.repository.ProfileRepository
import com.triathlonplanner.data.repository.RaceGoalRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TodayViewModel @Inject constructor(
    private val planRepository: PlanRepository,
    private val profileRepository: ProfileRepository,
    private val raceGoalRepository: RaceGoalRepository,
) : ViewModel() {

    val uiState: StateFlow<TodayUiState> = combine(
        planRepository.observeWorkoutsForDate(LocalDate.now()),
        profileRepository.observeProfile(),
        // Race-pace targets depend on the race, not just the athlete: the same FTP is ridden at a
        // very different fraction over a sprint and over an Iron-distance day.
        raceGoalRepository.observeActive(),
    ) { workouts, profile, goal -> Triple(workouts, profile, goal) }
        .flatMapLatest { (workouts, profile, goal) ->
            val single = workouts.singleOrNull()
            if (single == null) {
                flowOf(TodayUiState(workouts = workouts.map { it.toTodayView(profile, goal) }, isLoading = false))
            } else {
                // Only one workout today - fetch its steps/actual-activity too, so Today can show
                // the full breakdown directly instead of requiring a tap into a detail screen.
                combine(
                    planRepository.observeStepsForWorkout(single.id),
                    planRepository.observeWorkoutDetail(single.id),
                ) { steps, (_, activity) ->
                    TodayUiState(
                        workouts = listOf(single.toTodayView(profile, goal)),
                        singleWorkoutDetail = single.toWorkoutLegView(profile, goal, steps, activity),
                        isLoading = false,
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState())
}
