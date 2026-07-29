package com.triathlonplanner.feature.onboarding

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.triathlonplanner.core.model.Distance
import com.triathlonplanner.data.healthconnect.HealthConnectPermissions
import com.triathlonplanner.data.healthconnect.healthConnectPermissionRequestContract
import java.time.Instant
import java.time.ZoneOffset

private val RUN_PB_DISTANCE_OPTIONS = listOf(
    "5K" to 5_000.0,
    "10K" to 10_000.0,
    "Half Marathon" to Distance.HALF_IRON.runMeters.toDouble(),
    "Marathon" to Distance.FULL_IRON.runMeters.toDouble(),
)

@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.isComplete) {
        if (state.isComplete) onFinished()
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            when (state.step) {
                OnboardingStep.DISTANCE -> DistanceStep(state.selectedDistance, viewModel::selectDistance)
                OnboardingStep.RACE_DATE -> RaceDateStep(viewModel::selectRaceDate)
                OnboardingStep.MAX_HR -> MaxHrStep(state, viewModel)
                OnboardingStep.FTP_CSS -> FtpCssStep(state, viewModel)
                OnboardingStep.GOAL_TYPE -> GoalTypeStep(state, viewModel)
                OnboardingStep.CURRENT_FITNESS -> CurrentFitnessStep(state, viewModel)
                OnboardingStep.TRAINING_AVAILABILITY -> TrainingAvailabilityStep(state, viewModel)
                OnboardingStep.HEALTH_CONNECT -> HealthConnectStep(
                    granted = state.healthConnectPermissionsGranted,
                    onRefresh = viewModel::refreshHealthConnectStatus,
                    onPermissionsResult = viewModel::onHealthConnectPermissionsResult,
                )
                OnboardingStep.PLAN_READY -> PlanReadyStep(state.planWarnings)
            }

            Spacer(Modifier.height(24.dp))

            state.errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(8.dp))
            }

            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                if (state.step != OnboardingStep.DISTANCE && state.step != OnboardingStep.PLAN_READY) {
                    TextButton(onClick = viewModel::back) { Text("Back") }
                } else {
                    Spacer(Modifier.width(1.dp))
                }

                if (state.step == OnboardingStep.PLAN_READY) {
                    Button(onClick = viewModel::confirmPlanReady) { Text("Let's go") }
                } else {
                    val isFinalStep = state.step == OnboardingStep.HEALTH_CONNECT ||
                        (state.step == OnboardingStep.TRAINING_AVAILABILITY && state.hasExistingProfile)

                    val canProceed = when (state.step) {
                        OnboardingStep.DISTANCE -> state.canProceedFromDistance
                        OnboardingStep.RACE_DATE -> state.canProceedFromDate
                        OnboardingStep.MAX_HR -> state.canProceedFromMaxHr
                        OnboardingStep.FTP_CSS -> true
                        OnboardingStep.GOAL_TYPE -> state.canProceedFromGoalType
                        OnboardingStep.CURRENT_FITNESS -> state.canProceedFromCurrentFitness
                        OnboardingStep.TRAINING_AVAILABILITY -> state.canProceedFromAvailability && !state.isSaving
                        OnboardingStep.HEALTH_CONNECT -> !state.isSaving
                        OnboardingStep.PLAN_READY -> true
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (state.step == OnboardingStep.CURRENT_FITNESS) {
                            TextButton(onClick = viewModel::skipCurrentFitness) { Text("Skip") }
                            Spacer(Modifier.width(8.dp))
                        }
                        Button(onClick = viewModel::advance, enabled = canProceed) {
                            if (state.isSaving) {
                                CircularProgressIndicator(modifier = Modifier.height(20.dp).width(20.dp))
                            } else {
                                Text(if (isFinalStep) "Create my plan" else "Next")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DistanceStep(selected: Distance?, onSelect: (Distance) -> Unit) {
    Text("What are you training for?", style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(16.dp))
    Distance.entries.forEach { distance ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .selectable(selected = selected == distance, onClick = { onSelect(distance) })
                .padding(vertical = 8.dp),
        ) {
            RadioButton(selected = selected == distance, onClick = { onSelect(distance) })
            Spacer(Modifier.width(8.dp))
            Column {
                Text(distance.displayName, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${distance.swimMeters}m swim / ${distance.bikeMeters / 1000}km bike / ${distance.runMeters / 1000.0}km run",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RaceDateStep(onSelect: (java.time.LocalDate) -> Unit) {
    Text("When is race day?", style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(16.dp))
    val datePickerState = rememberDatePickerState()
    DatePicker(state = datePickerState)
    LaunchedEffect(datePickerState.selectedDateMillis) {
        datePickerState.selectedDateMillis?.let { millis ->
            onSelect(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
        }
    }
}

@Composable
private fun TrainingAvailabilityStep(state: OnboardingUiState, viewModel: OnboardingViewModel) {
    Text("How much time can you train?", style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(8.dp))
    Text(
        "We've pre-filled a recommendation based on your race distance" +
            (if (state.goalType == GoalType.TARGET_TIME) " and target time" else "") +
            " - adjust it to fit your schedule.",
        style = MaterialTheme.typography.bodySmall,
    )
    Spacer(Modifier.height(16.dp))
    OutlinedTextField(
        value = state.weeklyHoursInput,
        onValueChange = viewModel::updateWeeklyHours,
        label = { Text("Weekly hours") },
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = state.daysPerWeekInput,
        onValueChange = viewModel::updateDaysPerWeek,
        label = { Text("Days per week") },
        modifier = Modifier.fillMaxWidth(),
    )
    if (state.hasExistingProfile) {
        Spacer(Modifier.height(16.dp))
        Text(
            "Using your existing max HR, FTP, and swim pace from your profile - update those " +
                "anytime from the Profile tab.",
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun MaxHrStep(state: OnboardingUiState, viewModel: OnboardingViewModel) {
    Text("What's your max heart rate?", style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(8.dp))
    Text(
        "From a recent field test or race - if unsure, a common estimate is 220 minus your age.",
        style = MaterialTheme.typography.bodySmall,
    )
    Spacer(Modifier.height(16.dp))
    OutlinedTextField(
        value = state.maxHrInput,
        onValueChange = viewModel::updateMaxHr,
        label = { Text("Max HR (bpm)") },
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = state.restingHrInput,
        onValueChange = viewModel::updateRestingHr,
        label = { Text("Resting HR (bpm) - optional, improves zone accuracy") },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun FtpCssStep(state: OnboardingUiState, viewModel: OnboardingViewModel) {
    Text("Cycling power & swim pace", style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(8.dp))
    Text(
        "Optional - leave blank if you don't know these yet. You can add them later, or take a " +
            "guided field test from your profile.",
        style = MaterialTheme.typography.bodySmall,
    )
    Spacer(Modifier.height(16.dp))
    OutlinedTextField(
        value = state.ftpInput,
        onValueChange = viewModel::updateFtp,
        label = { Text("FTP (watts)") },
        modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(12.dp))
    Text("Critical Swim Speed pace (per 100m)", style = MaterialTheme.typography.labelLarge)
    Row {
        OutlinedTextField(
            value = state.cssMinutesInput,
            onValueChange = viewModel::updateCssMinutes,
            label = { Text("min") },
            modifier = Modifier.width(100.dp),
        )
        Spacer(Modifier.width(8.dp))
        OutlinedTextField(
            value = state.cssSecondsInput,
            onValueChange = viewModel::updateCssSeconds,
            label = { Text("sec") },
            modifier = Modifier.width(100.dp),
        )
    }
}

@Composable
private fun GoalTypeStep(state: OnboardingUiState, viewModel: OnboardingViewModel) {
    Text("What's your goal?", style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(16.dp))
    listOf(
        GoalType.JUST_FINISH to "Just finish",
        GoalType.TARGET_TIME to "Hit a target time",
    ).forEach { (goalType, label) ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .selectable(selected = state.goalType == goalType, onClick = { viewModel.selectGoalType(goalType) })
                .padding(vertical = 8.dp),
        ) {
            RadioButton(selected = state.goalType == goalType, onClick = { viewModel.selectGoalType(goalType) })
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.titleMedium)
        }
    }
    if (state.goalType == GoalType.TARGET_TIME) {
        Spacer(Modifier.height(16.dp))
        Text("Target finish time", style = MaterialTheme.typography.labelLarge)
        Row {
            OutlinedTextField(
                value = state.targetTimeHoursInput,
                onValueChange = viewModel::updateTargetTimeHours,
                label = { Text("hr") },
                modifier = Modifier.width(90.dp),
            )
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(
                value = state.targetTimeMinutesInput,
                onValueChange = viewModel::updateTargetTimeMinutes,
                label = { Text("min") },
                modifier = Modifier.width(90.dp),
            )
            Spacer(Modifier.width(8.dp))
            OutlinedTextField(
                value = state.targetTimeSecondsInput,
                onValueChange = viewModel::updateTargetTimeSeconds,
                label = { Text("sec") },
                modifier = Modifier.width(90.dp),
            )
        }
    }
}

@Composable
private fun CurrentFitnessStep(state: OnboardingUiState, viewModel: OnboardingViewModel) {
    Text("Where do you stand today?", style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(8.dp))
    Text(
        "This helps us judge how big a jump your target is, and adjust your training volume " +
            "accordingly. You can skip this if you'd rather not estimate.",
        style = MaterialTheme.typography.bodySmall,
    )
    Spacer(Modifier.height(16.dp))

    listOf(
        FitnessEstimateMode.DIRECT_ENTRY to "I know my estimated finish time",
        FitnessEstimateMode.CALCULATED to "Calculate it from a recent run PB",
    ).forEach { (mode, label) ->
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .selectable(selected = state.fitnessEstimateMode == mode, onClick = { viewModel.selectFitnessEstimateMode(mode) })
                .padding(vertical = 8.dp),
        ) {
            RadioButton(selected = state.fitnessEstimateMode == mode, onClick = { viewModel.selectFitnessEstimateMode(mode) })
            Spacer(Modifier.width(8.dp))
            Text(label, style = MaterialTheme.typography.bodyMedium)
        }
    }

    when (state.fitnessEstimateMode) {
        FitnessEstimateMode.DIRECT_ENTRY -> {
            Spacer(Modifier.height(8.dp))
            Text("Estimated finish time", style = MaterialTheme.typography.labelLarge)
            Row {
                OutlinedTextField(
                    value = state.currentEstimateHoursInput,
                    onValueChange = viewModel::updateCurrentEstimateHours,
                    label = { Text("hr") },
                    modifier = Modifier.width(90.dp),
                )
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    value = state.currentEstimateMinutesInput,
                    onValueChange = viewModel::updateCurrentEstimateMinutes,
                    label = { Text("min") },
                    modifier = Modifier.width(90.dp),
                )
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    value = state.currentEstimateSecondsInput,
                    onValueChange = viewModel::updateCurrentEstimateSeconds,
                    label = { Text("sec") },
                    modifier = Modifier.width(90.dp),
                )
            }
        }
        FitnessEstimateMode.CALCULATED -> {
            Spacer(Modifier.height(8.dp))
            Text(
                "Estimated from your swim CSS pace and a recent run personal best - the bike leg " +
                    "isn't included since it depends too much on the course to estimate reliably.",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(8.dp))
            Text("Run PB distance", style = MaterialTheme.typography.labelLarge)
            Row {
                RUN_PB_DISTANCE_OPTIONS.forEach { (label, meters) ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.selectable(selected = state.runPbDistanceM == meters, onClick = { viewModel.updateRunPbDistance(meters) }),
                    ) {
                        RadioButton(selected = state.runPbDistanceM == meters, onClick = { viewModel.updateRunPbDistance(meters) })
                        Text(label, style = MaterialTheme.typography.bodySmall)
                    }
                    Spacer(Modifier.width(4.dp))
                }
            }
            Spacer(Modifier.height(8.dp))
            Text("PB time", style = MaterialTheme.typography.labelLarge)
            Row {
                OutlinedTextField(
                    value = state.runPbMinutesInput,
                    onValueChange = viewModel::updateRunPbMinutes,
                    label = { Text("min") },
                    modifier = Modifier.width(100.dp),
                )
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(
                    value = state.runPbSecondsInput,
                    onValueChange = viewModel::updateRunPbSeconds,
                    label = { Text("sec") },
                    modifier = Modifier.width(100.dp),
                )
            }
            if (state.effectiveCssPaceSecPer100m == null) {
                Spacer(Modifier.height(8.dp))
                Text(
                    "We don't have a swim CSS pace for you yet - add one on the previous step or " +
                        "your Profile tab to use this option.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
        null -> Unit
    }
}

@Composable
private fun PlanReadyStep(planWarnings: List<String>) {
    Text("Your plan is ready", style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(16.dp))
    if (planWarnings.isEmpty()) {
        Text("Everything checks out - good luck with training!", style = MaterialTheme.typography.bodyMedium)
    } else {
        planWarnings.forEach { warning ->
            Text(warning, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.tertiary)
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun HealthConnectStep(granted: Boolean, onRefresh: () -> Unit, onPermissionsResult: (Boolean) -> Unit) {
    val launcher = rememberLauncherForActivityResult(
        contract = healthConnectPermissionRequestContract(),
    ) { result -> onPermissionsResult(result.containsAll(HealthConnectPermissions.REQUIRED)) }

    LaunchedEffect(Unit) { onRefresh() }

    Text("Connect Garmin via Health Connect", style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(8.dp))
    Text(
        "If your Garmin Connect app is set to sync with Health Connect, we can automatically read " +
            "your completed workouts (heart rate, power, pace) and adapt your plan. This stays " +
            "entirely on your device - nothing is uploaded anywhere. You can also skip this and " +
            "connect it later from Profile.",
        style = MaterialTheme.typography.bodyMedium,
    )
    Spacer(Modifier.height(16.dp))
    if (granted) {
        Text("Already connected - your Garmin workouts will keep syncing automatically.", style = MaterialTheme.typography.bodyMedium)
    } else {
        Button(onClick = { launcher.launch(HealthConnectPermissions.REQUIRED) }) {
            Text("Grant Health Connect access")
        }
    }
}
