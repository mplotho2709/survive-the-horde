package com.triathlonplanner.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.triathlonplanner.core.model.CssSource
import com.triathlonplanner.core.model.Distance
import com.triathlonplanner.core.model.FtpSource
import com.triathlonplanner.core.model.RaceGoal
import com.triathlonplanner.core.model.UserZoneProfile
import com.triathlonplanner.data.repository.PlanRepository
import com.triathlonplanner.data.repository.ProfileRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val planRepository: PlanRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    fun selectDistance(distance: Distance) {
        _uiState.update { it.copy(selectedDistance = distance) }
    }

    fun selectRaceDate(date: LocalDate) {
        _uiState.update { it.copy(raceDate = date) }
    }

    fun updateMaxHr(value: String) = _uiState.update { it.copy(maxHrInput = value.filter(Char::isDigit)) }
    fun updateRestingHr(value: String) = _uiState.update { it.copy(restingHrInput = value.filter(Char::isDigit)) }
    fun updateFtp(value: String) = _uiState.update { it.copy(ftpInput = value.filter(Char::isDigit)) }
    fun updateCssMinutes(value: String) = _uiState.update { it.copy(cssMinutesInput = value.filter(Char::isDigit)) }
    fun updateCssSeconds(value: String) = _uiState.update { it.copy(cssSecondsInput = value.filter(Char::isDigit)) }

    fun goToStep(step: OnboardingStep) = _uiState.update { it.copy(step = step) }

    fun advance() {
        val current = _uiState.value.step
        val next = when (current) {
            OnboardingStep.DISTANCE -> OnboardingStep.RACE_DATE
            OnboardingStep.RACE_DATE -> OnboardingStep.MAX_HR
            OnboardingStep.MAX_HR -> OnboardingStep.FTP_CSS
            OnboardingStep.FTP_CSS -> OnboardingStep.HEALTH_CONNECT
            OnboardingStep.HEALTH_CONNECT -> {
                finish()
                return
            }
        }
        _uiState.update { it.copy(step = next) }
    }

    fun back() {
        val current = _uiState.value.step
        val previous = when (current) {
            OnboardingStep.DISTANCE -> return
            OnboardingStep.RACE_DATE -> OnboardingStep.DISTANCE
            OnboardingStep.MAX_HR -> OnboardingStep.RACE_DATE
            OnboardingStep.FTP_CSS -> OnboardingStep.MAX_HR
            OnboardingStep.HEALTH_CONNECT -> OnboardingStep.FTP_CSS
        }
        _uiState.update { it.copy(step = previous) }
    }

    private fun finish() {
        val state = _uiState.value
        val distance = state.selectedDistance ?: return
        val raceDate = state.raceDate ?: return
        val maxHr = state.maxHrInput.toIntOrNull() ?: return

        _uiState.update { it.copy(isSaving = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                val ftpWatts = state.ftpInput.toIntOrNull()
                val cssMin = state.cssMinutesInput.toIntOrNull()
                val cssSec = state.cssSecondsInput.toIntOrNull()
                val cssPaceSecPer100m = if (cssMin != null && cssSec != null) cssMin * 60 + cssSec else null

                val profile = UserZoneProfile(
                    maxHr = maxHr,
                    restingHr = state.restingHrInput.toIntOrNull(),
                    ftpWatts = ftpWatts,
                    ftpSource = if (ftpWatts != null) FtpSource.MANUAL else null,
                    cssPaceSecPer100m = cssPaceSecPer100m,
                    cssSource = if (cssPaceSecPer100m != null) CssSource.MANUAL else null,
                )
                profileRepository.saveProfile(profile)
                planRepository.createPlanForGoal(RaceGoal(distance, raceDate), profile, LocalDate.now())
                _uiState.update { it.copy(isSaving = false, isComplete = true) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, errorMessage = "Couldn't create your plan: ${e.message}") }
            }
        }
    }
}
