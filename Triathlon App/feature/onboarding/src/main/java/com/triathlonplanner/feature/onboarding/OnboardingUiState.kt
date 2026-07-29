package com.triathlonplanner.feature.onboarding

import com.triathlonplanner.core.model.Distance
import java.time.LocalDate

enum class OnboardingStep {
    DISTANCE,
    RACE_DATE,
    TRAINING_AVAILABILITY,
    MAX_HR,
    FTP_CSS,
    HEALTH_CONNECT,
}

data class OnboardingUiState(
    val step: OnboardingStep = OnboardingStep.DISTANCE,
    val selectedDistance: Distance? = null,
    val raceDate: LocalDate? = null,
    val weeklyHoursInput: String = "",
    val daysPerWeekInput: String = "",
    val maxHrInput: String = "",
    val restingHrInput: String = "",
    val ftpInput: String = "",
    val cssMinutesInput: String = "",
    val cssSecondsInput: String = "",
    val isSaving: Boolean = false,
    val isComplete: Boolean = false,
    val errorMessage: String? = null,
    val healthConnectPermissionsGranted: Boolean = false,
    // True when a UserZoneProfile already exists (e.g. re-onboarding after a plan reset) - in
    // that case max HR/FTP/CSS/Health Connect are already set up and those steps are skipped;
    // the existing profile is reused as-is for the new plan.
    val hasExistingProfile: Boolean = false,
) {
    val canProceedFromDistance: Boolean get() = selectedDistance != null
    val canProceedFromDate: Boolean get() = raceDate != null && raceDate.isAfter(LocalDate.now())
    val canProceedFromAvailability: Boolean get() =
        weeklyHoursInput.toDoubleOrNull()?.let { it in 1.0..40.0 } == true &&
            daysPerWeekInput.toIntOrNull()?.let { it in 2..7 } == true
    val canProceedFromMaxHr: Boolean get() = maxHrInput.toIntOrNull()?.let { it in 100..230 } == true
}
