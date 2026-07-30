package com.triathlonplanner.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.triathlonplanner.core.model.CssSource
import com.triathlonplanner.core.model.Discipline
import com.triathlonplanner.core.model.Distance
import com.triathlonplanner.core.model.FtpSource
import com.triathlonplanner.core.model.RaceGoal
import com.triathlonplanner.core.model.TrainingAvailability
import com.triathlonplanner.core.model.UserZoneProfile
import com.triathlonplanner.data.healthconnect.HealthConnectDataSource
import com.triathlonplanner.data.repository.ActivitySyncRepository
import com.triathlonplanner.data.repository.PlanRepository
import com.triathlonplanner.data.repository.ProfileRepository
import com.triathlonplanner.domain.zones.RaceTimePredictor
import com.triathlonplanner.domain.zones.RunPaceZoneCalculator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val profileRepository: ProfileRepository,
    private val planRepository: PlanRepository,
    private val healthConnectDataSource: HealthConnectDataSource,
    private val activitySyncRepository: ActivitySyncRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OnboardingUiState())
    val uiState: StateFlow<OnboardingUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val profile = profileRepository.getProfileOnce()
            _uiState.update {
                it.copy(
                    hasExistingProfile = profile != null,
                    existingCssPaceSecPer100m = profile?.cssPaceSecPer100m,
                )
            }
        }
    }

    fun refreshHealthConnectStatus() {
        viewModelScope.launch {
            val granted = healthConnectDataSource.hasAllPermissions()
            _uiState.update { it.copy(healthConnectPermissionsGranted = granted) }
        }
    }

    fun onHealthConnectPermissionsResult(granted: Boolean) {
        _uiState.update { it.copy(healthConnectPermissionsGranted = granted) }
    }

    fun selectDistance(distance: Distance) {
        val recommended = recommendedFor(distance)
        _uiState.update {
            it.copy(
                selectedDistance = distance,
                weeklyHoursInput = recommended.weeklyHoursTarget.toString(),
                daysPerWeekInput = recommended.daysPerWeekTarget.toString(),
            )
        }
    }

    fun selectRaceDate(date: LocalDate) {
        _uiState.update { it.copy(raceDate = date) }
    }

    fun updateWeeklyHours(value: String) = _uiState.update { it.copy(weeklyHoursInput = value.filter { c -> c.isDigit() || c == '.' }) }
    fun updateDaysPerWeek(value: String) = _uiState.update { it.copy(daysPerWeekInput = value.filter(Char::isDigit)) }

    fun updateMaxHr(value: String) = _uiState.update { it.copy(maxHrInput = value.filter(Char::isDigit)) }
    fun updateRestingHr(value: String) = _uiState.update { it.copy(restingHrInput = value.filter(Char::isDigit)) }
    fun updateFtp(value: String) = _uiState.update { it.copy(ftpInput = value.filter(Char::isDigit)) }
    fun updateCssMinutes(value: String) = _uiState.update { it.copy(cssMinutesInput = value.filter(Char::isDigit)) }
    fun updateCssSeconds(value: String) = _uiState.update { it.copy(cssSecondsInput = value.filter(Char::isDigit)) }

    fun selectGoalType(goalType: GoalType) = _uiState.update { it.copy(goalType = goalType) }
    fun updateTargetTimeHours(value: String) = _uiState.update { it.copy(targetTimeHoursInput = value.filter(Char::isDigit)) }
    fun updateTargetTimeMinutes(value: String) = _uiState.update { it.copy(targetTimeMinutesInput = value.filter(Char::isDigit)) }
    fun updateTargetTimeSeconds(value: String) = _uiState.update { it.copy(targetTimeSecondsInput = value.filter(Char::isDigit)) }

    fun selectFitnessEstimateMode(mode: FitnessEstimateMode) = _uiState.update { it.copy(fitnessEstimateMode = mode) }
    fun updateCurrentEstimateHours(value: String) = _uiState.update { it.copy(currentEstimateHoursInput = value.filter(Char::isDigit)) }
    fun updateCurrentEstimateMinutes(value: String) = _uiState.update { it.copy(currentEstimateMinutesInput = value.filter(Char::isDigit)) }
    fun updateCurrentEstimateSeconds(value: String) = _uiState.update { it.copy(currentEstimateSecondsInput = value.filter(Char::isDigit)) }
    fun updateRunPbDistance(meters: Double) = _uiState.update { it.copy(runPbDistanceM = meters) }
    fun updateRunPbMinutes(value: String) = _uiState.update { it.copy(runPbMinutesInput = value.filter(Char::isDigit)) }
    fun updateRunPbSeconds(value: String) = _uiState.update { it.copy(runPbSecondsInput = value.filter(Char::isDigit)) }

    /** Toggling a day off is how an athlete says "not this one", so both directions are supported. */
    fun toggleTrainingDay(discipline: Discipline, day: DayOfWeek) = _uiState.update { state ->
        when (discipline) {
            Discipline.SWIM -> state.copy(swimDays = state.swimDays.toggled(day))
            Discipline.BIKE -> state.copy(bikeDays = state.bikeDays.toggled(day))
            else -> state.copy(runDays = state.runDays.toggled(day))
        }
    }

    fun toggleLongSessionDay(day: DayOfWeek) = _uiState.update {
        it.copy(longSessionDays = it.longSessionDays.toggled(day))
    }

    private fun Set<DayOfWeek>.toggled(day: DayOfWeek): Set<DayOfWeek> =
        if (day in this) this - day else this + day

    fun skipCurrentFitness() {
        _uiState.update { it.copy(resolvedCurrentEstimateSec = null) }
        applyAvailabilityDefaults()
        _uiState.update { it.copy(step = OnboardingStep.TRAINING_AVAILABILITY) }
    }

    fun goToStep(step: OnboardingStep) = _uiState.update { it.copy(step = step) }

    fun advance() {
        val state = _uiState.value
        val next = when (state.step) {
            OnboardingStep.DISTANCE -> OnboardingStep.RACE_DATE
            OnboardingStep.RACE_DATE -> if (state.hasExistingProfile) OnboardingStep.GOAL_TYPE else OnboardingStep.MAX_HR
            OnboardingStep.MAX_HR -> OnboardingStep.FTP_CSS
            OnboardingStep.FTP_CSS -> OnboardingStep.GOAL_TYPE
            OnboardingStep.GOAL_TYPE -> {
                if (state.goalType == GoalType.JUST_FINISH) {
                    applyAvailabilityDefaults()
                    OnboardingStep.TRAINING_AVAILABILITY
                } else {
                    OnboardingStep.CURRENT_FITNESS
                }
            }
            OnboardingStep.CURRENT_FITNESS -> {
                resolveCurrentFitnessEstimate()
                applyAvailabilityDefaults()
                OnboardingStep.TRAINING_AVAILABILITY
            }
            OnboardingStep.TRAINING_AVAILABILITY -> OnboardingStep.TRAINING_DAYS
            OnboardingStep.TRAINING_DAYS -> {
                if (state.hasExistingProfile) {
                    finish()
                    return
                }
                OnboardingStep.HEALTH_CONNECT
            }
            OnboardingStep.HEALTH_CONNECT -> {
                finish()
                return
            }
            OnboardingStep.PLAN_READY -> return
        }
        _uiState.update { it.copy(step = next) }
    }

    fun back() {
        val state = _uiState.value
        val previous = when (state.step) {
            OnboardingStep.DISTANCE -> return
            OnboardingStep.RACE_DATE -> OnboardingStep.DISTANCE
            OnboardingStep.MAX_HR -> OnboardingStep.RACE_DATE
            OnboardingStep.FTP_CSS -> OnboardingStep.MAX_HR
            OnboardingStep.GOAL_TYPE -> if (state.hasExistingProfile) OnboardingStep.RACE_DATE else OnboardingStep.FTP_CSS
            OnboardingStep.CURRENT_FITNESS -> OnboardingStep.GOAL_TYPE
            OnboardingStep.TRAINING_AVAILABILITY -> if (state.goalType == GoalType.JUST_FINISH) OnboardingStep.GOAL_TYPE else OnboardingStep.CURRENT_FITNESS
            OnboardingStep.TRAINING_DAYS -> OnboardingStep.TRAINING_AVAILABILITY
            OnboardingStep.HEALTH_CONNECT -> OnboardingStep.TRAINING_DAYS
            OnboardingStep.PLAN_READY -> return
        }
        _uiState.update { it.copy(step = previous) }
    }

    private fun resolveCurrentFitnessEstimate() {
        val state = _uiState.value
        val distance = state.selectedDistance ?: return
        val estimate = when (state.fitnessEstimateMode) {
            FitnessEstimateMode.DIRECT_ENTRY -> hmsToSecondsOrNull(
                state.currentEstimateHoursInput,
                state.currentEstimateMinutesInput,
                state.currentEstimateSecondsInput,
            )
            FitnessEstimateMode.CALCULATED -> {
                val css = state.effectiveCssPaceSecPer100m
                val runPbDistanceM = state.runPbDistanceM
                val runPbTimeSec = msToSecondsOrNull(state.runPbMinutesInput, state.runPbSecondsInput)
                if (css != null && runPbDistanceM != null && runPbTimeSec != null) {
                    RaceTimePredictor.estimateCurrentFinishTimeSec(distance, css, runPbDistanceM, runPbTimeSec)
                } else {
                    null
                }
            }
            null -> null
        }
        // A run PB gives us threshold pace regardless of which estimate path was chosen.
        val thresholdPace = state.runPbDistanceM?.let { pbDistance ->
            msToSecondsOrNull(state.runPbMinutesInput, state.runPbSecondsInput)?.let { pbTime ->
                RunPaceZoneCalculator.thresholdPaceFromRacePb(pbDistance, pbTime)
            }
        }
        _uiState.update { it.copy(resolvedCurrentEstimateSec = estimate, resolvedThresholdRunPaceSecPerKm = thresholdPace) }
    }

    /** Recomputes the recommended weekly-hours/days-per-week defaults for TRAINING_AVAILABILITY -
     * plain per-distance recommendation for "just finish", bumped by the fitness gap when a target
     * time and a resolved current-fitness estimate are both known. */
    private fun applyAvailabilityDefaults() {
        val state = _uiState.value
        val distance = state.selectedDistance ?: return
        val targetTimeSec = if (state.goalType == GoalType.TARGET_TIME) {
            hmsToSecondsOrNull(state.targetTimeHoursInput, state.targetTimeMinutesInput, state.targetTimeSecondsInput)
        } else {
            null
        }
        val currentEstimateSec = state.resolvedCurrentEstimateSec

        val recommended = if (targetTimeSec != null && currentEstimateSec != null) {
            val requiredImprovement = RaceTimePredictor.requiredImprovementPercent(currentEstimateSec, targetTimeSec)
            adjustForFitnessGap(distance, requiredImprovement)
        } else {
            recommendedFor(distance)
        }
        _uiState.update {
            it.copy(
                weeklyHoursInput = recommended.weeklyHoursTarget.toString(),
                daysPerWeekInput = recommended.daysPerWeekTarget.toString(),
            )
        }
    }

    private fun finish() {
        val state = _uiState.value
        val distance = state.selectedDistance ?: return
        val raceDate = state.raceDate ?: return
        // When re-onboarding with an existing profile, HR/FTP/CSS were never collected this run -
        // the maxHr guard below only applies to the fresh-profile path.
        if (!state.hasExistingProfile && state.maxHrInput.toIntOrNull() == null) return

        _uiState.update { it.copy(isSaving = true, errorMessage = null) }
        viewModelScope.launch {
            try {
                val profile = if (state.hasExistingProfile) {
                    profileRepository.getProfileOnce() ?: return@launch
                } else {
                    val maxHr = state.maxHrInput.toIntOrNull() ?: return@launch
                    val ftpWatts = state.ftpInput.toIntOrNull()
                    val cssMin = state.cssMinutesInput.toIntOrNull()
                    val cssSec = state.cssSecondsInput.toIntOrNull()
                    val cssPaceSecPer100m = if (cssMin != null && cssSec != null) cssMin * 60 + cssSec else null

                    val newProfile = UserZoneProfile(
                        maxHr = maxHr,
                        restingHr = state.restingHrInput.toIntOrNull(),
                        ftpWatts = ftpWatts,
                        ftpSource = if (ftpWatts != null) FtpSource.MANUAL else null,
                        cssPaceSecPer100m = cssPaceSecPer100m,
                        cssSource = if (cssPaceSecPer100m != null) CssSource.MANUAL else null,
                        thresholdRunPaceSecPerKm = state.resolvedThresholdRunPaceSecPerKm,
                    )
                    profileRepository.saveProfile(newProfile)
                    newProfile
                }
                val availability = TrainingAvailability(
                    weeklyHoursTarget = state.weeklyHoursInput.toDoubleOrNull() ?: recommendedFor(distance).weeklyHoursTarget,
                    daysPerWeekTarget = state.daysPerWeekInput.toIntOrNull() ?: recommendedFor(distance).daysPerWeekTarget,
                    dayPreferences = state.dayPreferences.takeUnless { it.isEmpty },
                )
                val targetFinishTimeSec = if (state.goalType == GoalType.TARGET_TIME) {
                    hmsToSecondsOrNull(state.targetTimeHoursInput, state.targetTimeMinutesInput, state.targetTimeSecondsInput)
                } else {
                    null
                }

                val result = planRepository.createPlanForGoal(
                    RaceGoal(
                        distance,
                        raceDate,
                        trainingAvailability = availability,
                        targetFinishTimeSec = targetFinishTimeSec,
                        currentFitnessEstimateSec = state.resolvedCurrentEstimateSec,
                    ),
                    profile,
                    LocalDate.now(),
                )
                // Pull in recent Health Connect history now that a plan exists to match/load
                // against - otherwise training done before this plan (or before Health Connect
                // was even connected) would never be stored at all.
                activitySyncRepository.backfillHistory()
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        step = OnboardingStep.PLAN_READY,
                        planWarnings = result.warnings,
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isSaving = false, errorMessage = "Couldn't create your plan: ${e.message}") }
            }
        }
    }

    fun confirmPlanReady() = _uiState.update { it.copy(isComplete = true) }
}
