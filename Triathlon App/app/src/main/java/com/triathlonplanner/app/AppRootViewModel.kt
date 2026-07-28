package com.triathlonplanner.app

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.triathlonplanner.data.repository.PlanRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class AppRootViewModel @Inject constructor(
    planRepository: PlanRepository,
) : ViewModel() {

    /** null = still loading; the onboarding-vs-main decision is driven reactively off Room, so
     * finishing onboarding automatically flips this to true with no manual navigation needed. */
    val hasActivePlan: StateFlow<Boolean?> = planRepository.observeActivePlan()
        .map { it != null }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
}
