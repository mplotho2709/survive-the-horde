package com.triathlonplanner.feature.onboarding

import com.triathlonplanner.core.model.Distance
import java.time.LocalDate

enum class OnboardingStep {
    DISTANCE,
    RACE_DATE,
    MAX_HR,
    FTP_CSS,
    GOAL_TYPE,
    CURRENT_FITNESS,
    TRAINING_AVAILABILITY,
    HEALTH_CONNECT,
    PLAN_READY,
}

enum class GoalType { JUST_FINISH, TARGET_TIME }

enum class FitnessEstimateMode { DIRECT_ENTRY, CALCULATED }

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
    val goalType: GoalType? = null,
    val targetTimeHoursInput: String = "",
    val targetTimeMinutesInput: String = "",
    val targetTimeSecondsInput: String = "",
    val fitnessEstimateMode: FitnessEstimateMode? = null,
    val currentEstimateHoursInput: String = "",
    val currentEstimateMinutesInput: String = "",
    val currentEstimateSecondsInput: String = "",
    val runPbDistanceM: Double? = null,
    val runPbMinutesInput: String = "",
    val runPbSecondsInput: String = "",
    val resolvedCurrentEstimateSec: Int? = null,
    // Derived from the run PB on the CURRENT_FITNESS step; persisted onto the profile so run
    // workouts can be prescribed in pace rather than heart rate.
    val resolvedThresholdRunPaceSecPerKm: Int? = null,
    val planWarnings: List<String> = emptyList(),
    val isSaving: Boolean = false,
    val isComplete: Boolean = false,
    val errorMessage: String? = null,
    val healthConnectPermissionsGranted: Boolean = false,
    // True when a UserZoneProfile already exists (e.g. re-onboarding after a plan reset) - in
    // that case max HR/FTP/CSS/Health Connect are already set up and those steps are skipped;
    // the existing profile is reused as-is for the new plan.
    val hasExistingProfile: Boolean = false,
    // Populated at init when hasExistingProfile is true, so the calculated fitness-estimate path
    // still has a CSS pace to work with even though FTP_CSS is skipped for returning users.
    val existingCssPaceSecPer100m: Int? = null,
) {
    val canProceedFromDistance: Boolean get() = selectedDistance != null
    val canProceedFromDate: Boolean get() = raceDate != null && raceDate.isAfter(LocalDate.now())
    val canProceedFromAvailability: Boolean get() =
        weeklyHoursInput.toDoubleOrNull()?.let { it in 1.0..40.0 } == true &&
            daysPerWeekInput.toIntOrNull()?.let { it in 2..7 } == true
    val canProceedFromMaxHr: Boolean get() = maxHrInput.toIntOrNull()?.let { it in 100..230 } == true

    val effectiveCssPaceSecPer100m: Int?
        get() {
            val direct = cssMinutesInput.toIntOrNull()?.let { m -> cssSecondsInput.toIntOrNull()?.let { s -> m * 60 + s } }
            return direct ?: existingCssPaceSecPer100m
        }

    val canProceedFromGoalType: Boolean
        get() = when (goalType) {
            GoalType.JUST_FINISH -> true
            GoalType.TARGET_TIME -> hmsToSecondsOrNull(targetTimeHoursInput, targetTimeMinutesInput, targetTimeSecondsInput) != null
            null -> false
        }

    val canProceedFromCurrentFitness: Boolean
        get() = when (fitnessEstimateMode) {
            FitnessEstimateMode.DIRECT_ENTRY ->
                hmsToSecondsOrNull(currentEstimateHoursInput, currentEstimateMinutesInput, currentEstimateSecondsInput) != null
            FitnessEstimateMode.CALCULATED ->
                runPbDistanceM != null && msToSecondsOrNull(runPbMinutesInput, runPbSecondsInput) != null && effectiveCssPaceSecPer100m != null
            null -> false
        }
}

/** Blank fields count as 0; an entirely blank triple is "not entered" rather than "0 seconds". */
internal fun hmsToSecondsOrNull(hours: String, minutes: String, seconds: String): Int? {
    if (hours.isBlank() && minutes.isBlank() && seconds.isBlank()) return null
    val h = hours.toIntOrNull() ?: 0
    val m = minutes.toIntOrNull() ?: 0
    val s = seconds.toIntOrNull() ?: 0
    return (h * 3600 + m * 60 + s).takeIf { it > 0 }
}

internal fun msToSecondsOrNull(minutes: String, seconds: String): Int? {
    val m = minutes.toIntOrNull() ?: return null
    val s = seconds.toIntOrNull() ?: 0
    return (m * 60 + s).takeIf { it > 0 }
}
