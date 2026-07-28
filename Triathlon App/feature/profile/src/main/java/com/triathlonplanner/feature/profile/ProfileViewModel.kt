package com.triathlonplanner.feature.profile

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.triathlonplanner.core.model.CssSource
import com.triathlonplanner.core.model.FtpSource
import com.triathlonplanner.core.model.UserZoneProfile
import com.triathlonplanner.data.healthconnect.HealthConnectAvailability
import com.triathlonplanner.data.healthconnect.HealthConnectDataSource
import com.triathlonplanner.data.healthconnect.checkHealthConnectAvailability
import com.triathlonplanner.data.repository.PlanRepository
import com.triathlonplanner.data.repository.ProfileRepository
import com.triathlonplanner.domain.zones.HrZoneCalculator
import com.triathlonplanner.domain.zones.PowerZoneCalculator
import com.triathlonplanner.domain.zones.SwimPaceZoneCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val planRepository: PlanRepository,
    private val healthConnectDataSource: HealthConnectDataSource,
    @ApplicationContext private val appContext: Context,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val profile = profileRepository.getProfileOnce()
            _uiState.update {
                it.copy(
                    maxHrInput = profile?.maxHr?.toString().orEmpty(),
                    restingHrInput = profile?.restingHr?.toString().orEmpty(),
                    ftpInput = profile?.ftpWatts?.toString().orEmpty(),
                    cssMinutesInput = profile?.cssPaceSecPer100m?.let { css -> (css / 60).toString() }.orEmpty(),
                    cssSecondsInput = profile?.cssPaceSecPer100m?.let { css -> (css % 60).toString() }.orEmpty(),
                    isLoading = false,
                )
            }
            recomputeZones()
            refreshHealthConnectStatus()
        }
    }

    fun onHealthConnectPermissionsResult(granted: Boolean) {
        _uiState.update { it.copy(healthConnectPermissionsGranted = granted) }
    }

    fun refreshHealthConnectStatus() {
        val availability = checkHealthConnectAvailability(appContext)
        _uiState.update { it.copy(healthConnectAvailability = availability) }
        if (availability == HealthConnectAvailability.AVAILABLE) {
            viewModelScope.launch {
                val granted = healthConnectDataSource.hasAllPermissions()
                _uiState.update { it.copy(healthConnectPermissionsGranted = granted) }
            }
        }
    }

    fun updateMaxHr(value: String) = updateAndRecompute { it.copy(maxHrInput = value.filter(Char::isDigit)) }
    fun updateRestingHr(value: String) = updateAndRecompute { it.copy(restingHrInput = value.filter(Char::isDigit)) }
    fun updateFtp(value: String) = updateAndRecompute { it.copy(ftpInput = value.filter(Char::isDigit)) }
    fun updateCssMinutes(value: String) = updateAndRecompute { it.copy(cssMinutesInput = value.filter(Char::isDigit)) }
    fun updateCssSeconds(value: String) = updateAndRecompute { it.copy(cssSecondsInput = value.filter(Char::isDigit)) }

    fun updateFtpTestAvgPower(value: String) = _uiState.update { it.copy(ftpTestAvgPowerInput = value.filter(Char::isDigit)) }
    fun updateCssTest400Sec(value: String) = _uiState.update { it.copy(cssTest400SecInput = value.filter(Char::isDigit)) }
    fun updateCssTest200Sec(value: String) = _uiState.update { it.copy(cssTest200SecInput = value.filter(Char::isDigit)) }

    fun applyFtpTestResult() {
        val avgPower = _uiState.value.ftpTestAvgPowerInput.toIntOrNull() ?: return
        val ftp = PowerZoneCalculator.estimateFtpFrom20MinTest(avgPower)
        updateAndRecompute { it.copy(ftpInput = ftp.toString(), ftpTestAvgPowerInput = "") }
    }

    fun applyCssTestResult() {
        val time400 = _uiState.value.cssTest400SecInput.toIntOrNull() ?: return
        val time200 = _uiState.value.cssTest200SecInput.toIntOrNull() ?: return
        if (time400 <= time200) return
        val css = SwimPaceZoneCalculator.estimateCssFromTimeTrial(time400, time200)
        updateAndRecompute {
            it.copy(cssMinutesInput = (css / 60).toString(), cssSecondsInput = (css % 60).toString(), cssTest400SecInput = "", cssTest200SecInput = "")
        }
    }

    fun save() {
        val state = _uiState.value
        val maxHr = state.maxHrInput.toIntOrNull() ?: return
        viewModelScope.launch {
            val ftpWatts = state.ftpInput.toIntOrNull()
            val cssMin = state.cssMinutesInput.toIntOrNull()
            val cssSec = state.cssSecondsInput.toIntOrNull()
            val cssPace = if (cssMin != null && cssSec != null) cssMin * 60 + cssSec else null

            profileRepository.saveProfile(
                UserZoneProfile(
                    maxHr = maxHr,
                    restingHr = state.restingHrInput.toIntOrNull(),
                    ftpWatts = ftpWatts,
                    ftpSource = if (ftpWatts != null) FtpSource.MANUAL else null,
                    cssPaceSecPer100m = cssPace,
                    cssSource = if (cssPace != null) CssSource.MANUAL else null,
                ),
            )
            _uiState.update { it.copy(isSaved = true) }
        }
    }

    fun abandonPlan() = viewModelScope.launch { planRepository.abandonActivePlan() }

    private fun updateAndRecompute(block: (ProfileUiState) -> ProfileUiState) {
        _uiState.update(block)
        recomputeZones()
    }

    private fun recomputeZones() {
        val state = _uiState.value
        val maxHr = state.maxHrInput.toIntOrNull()
        val restingHr = state.restingHrInput.toIntOrNull()
        val ftp = state.ftpInput.toIntOrNull()
        val cssMin = state.cssMinutesInput.toIntOrNull()
        val cssSec = state.cssSecondsInput.toIntOrNull()
        val cssPace = if (cssMin != null && cssSec != null) cssMin * 60 + cssSec else null

        _uiState.update {
            it.copy(
                hrZones = if (maxHr != null) HrZoneCalculator.calculateZones(maxHr, restingHr) else emptyList(),
                powerZones = if (ftp != null) PowerZoneCalculator.calculateZones(ftp) else emptyList(),
                swimZones = if (cssPace != null) SwimPaceZoneCalculator.calculateZones(cssPace) else emptyList(),
            )
        }
    }
}
