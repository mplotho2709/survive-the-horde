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
                OnboardingStep.TRAINING_AVAILABILITY -> TrainingAvailabilityStep(state, viewModel)
                OnboardingStep.MAX_HR -> MaxHrStep(state, viewModel)
                OnboardingStep.FTP_CSS -> FtpCssStep(state, viewModel)
                OnboardingStep.HEALTH_CONNECT -> HealthConnectStep(
                    granted = state.healthConnectPermissionsGranted,
                    onRefresh = viewModel::refreshHealthConnectStatus,
                    onPermissionsResult = viewModel::onHealthConnectPermissionsResult,
                )
            }

            Spacer(Modifier.height(24.dp))

            state.errorMessage?.let {
                Text(it, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(8.dp))
            }

            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                if (state.step != OnboardingStep.DISTANCE) {
                    TextButton(onClick = viewModel::back) { Text("Back") }
                } else {
                    Spacer(Modifier.width(1.dp))
                }

                val isFinalStep = state.step == OnboardingStep.HEALTH_CONNECT ||
                    (state.step == OnboardingStep.TRAINING_AVAILABILITY && state.hasExistingProfile)

                val canProceed = when (state.step) {
                    OnboardingStep.DISTANCE -> state.canProceedFromDistance
                    OnboardingStep.RACE_DATE -> state.canProceedFromDate
                    OnboardingStep.TRAINING_AVAILABILITY -> state.canProceedFromAvailability && !state.isSaving
                    OnboardingStep.MAX_HR -> state.canProceedFromMaxHr
                    OnboardingStep.FTP_CSS -> true
                    OnboardingStep.HEALTH_CONNECT -> !state.isSaving
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
    val recommended = state.selectedDistance?.let(::recommendedFor)
    Text("How much time can you train?", style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(8.dp))
    Text(
        recommended?.let {
            "Recommended for ${state.selectedDistance?.displayName}: ${it.weeklyHoursTarget}h across ${it.daysPerWeekTarget} days/week."
        } ?: "Set a weekly hours target and how many days you can train.",
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
