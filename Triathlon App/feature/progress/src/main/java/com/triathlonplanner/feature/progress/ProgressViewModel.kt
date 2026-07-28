package com.triathlonplanner.feature.progress

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.triathlonplanner.core.model.WeeklyLoadPoint
import com.triathlonplanner.data.repository.AdaptationEventRepository
import com.triathlonplanner.data.repository.PlanRepository
import com.triathlonplanner.data.repository.ProgressRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProgressViewModel @Inject constructor(
    planRepository: PlanRepository,
    private val progressRepository: ProgressRepository,
    adaptationEventRepository: AdaptationEventRepository,
) : ViewModel() {

    private val weeklyLoad = MutableStateFlow<List<WeeklyLoadPoint>>(emptyList())

    val uiState: StateFlow<ProgressUiState> = combine(
        weeklyLoad,
        adaptationEventRepository.observeAll(),
    ) { load, events ->
        ProgressUiState(weeklyLoad = load, events = events, isLoading = false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProgressUiState())

    init {
        viewModelScope.launch {
            planRepository.observeActivePlan().collectLatest { plan ->
                weeklyLoad.value = if (plan != null) progressRepository.getWeeklyLoadHistory(plan.id) else emptyList()
            }
        }
    }
}
