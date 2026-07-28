package com.triathlonplanner.feature.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.triathlonplanner.data.healthconnect.HealthConnectAvailability
import com.triathlonplanner.data.healthconnect.HealthConnectPermissions
import com.triathlonplanner.data.healthconnect.healthConnectPermissionRequestContract
import com.triathlonplanner.domain.zones.ZoneRange

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(viewModel: ProfileViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val launcher = rememberLauncherForActivityResult(
        contract = healthConnectPermissionRequestContract(),
    ) { granted -> viewModel.onHealthConnectPermissionsResult(granted.containsAll(HealthConnectPermissions.REQUIRED)) }

    Scaffold(topBar = { TopAppBar(title = { Text("Profile") }) }) { padding ->
        if (state.isLoading) {
            CircularProgressIndicator(Modifier.padding(padding).padding(24.dp))
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
        ) {
            Text("Zones", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(state.maxHrInput, viewModel::updateMaxHr, label = { Text("Max HR (bpm)") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(state.restingHrInput, viewModel::updateRestingHr, label = { Text("Resting HR (bpm)") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(state.ftpInput, viewModel::updateFtp, label = { Text("FTP (watts)") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            Row {
                OutlinedTextField(state.cssMinutesInput, viewModel::updateCssMinutes, label = { Text("CSS min") }, modifier = Modifier.width(120.dp))
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(state.cssSecondsInput, viewModel::updateCssSeconds, label = { Text("CSS sec") }, modifier = Modifier.width(120.dp))
            }
            Spacer(Modifier.height(12.dp))
            Button(onClick = viewModel::save) { Text(if (state.isSaved) "Saved" else "Save") }

            HorizontalDivider(modifier = Modifier.padding(vertical = 20.dp))

            Text("Don't know your FTP? Guided 20-min test", style = MaterialTheme.typography.titleMedium)
            Text(
                "Ride 20 minutes as hard as you can sustain, then enter your average power below.",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(8.dp))
            Row {
                OutlinedTextField(
                    state.ftpTestAvgPowerInput,
                    viewModel::updateFtpTestAvgPower,
                    label = { Text("Avg power (W)") },
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = viewModel::applyFtpTestResult) { Text("Apply") }
            }

            Spacer(Modifier.height(16.dp))
            Text("Don't know your CSS? Guided time trial", style = MaterialTheme.typography.titleMedium)
            Text(
                "Swim 400m all-out, rest, then swim 200m all-out. Enter both times in seconds.",
                style = MaterialTheme.typography.bodySmall,
            )
            Spacer(Modifier.height(8.dp))
            Row {
                OutlinedTextField(state.cssTest400SecInput, viewModel::updateCssTest400Sec, label = { Text("400m (sec)") }, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                OutlinedTextField(state.cssTest200SecInput, viewModel::updateCssTest200Sec, label = { Text("200m (sec)") }, modifier = Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                OutlinedButton(onClick = viewModel::applyCssTestResult) { Text("Apply") }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 20.dp))

            Text("Health Connect", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            when (state.healthConnectAvailability) {
                HealthConnectAvailability.NOT_INSTALLED -> Text("Health Connect isn't installed on this device.")
                HealthConnectAvailability.UPDATE_REQUIRED -> Text("Health Connect needs an update.")
                HealthConnectAvailability.AVAILABLE -> {
                    if (state.healthConnectPermissionsGranted) {
                        Text("Connected - your Garmin workouts will sync automatically.")
                    } else {
                        Button(onClick = { launcher.launch(HealthConnectPermissions.REQUIRED) }) {
                            Text("Grant Health Connect access")
                        }
                    }
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 20.dp))

            ZoneTable("Heart Rate Zones", state.hrZones, "bpm")
            ZoneTable("Power Zones", state.powerZones, "W")
            ZoneTable("Swim Pace Zones", state.swimZones, "sec/100m")
        }
    }
}

@Composable
private fun ZoneTable(title: String, zones: List<ZoneRange>, unit: String) {
    if (zones.isEmpty()) return
    Text(title, style = MaterialTheme.typography.titleSmall)
    zones.forEach { zone ->
        Text("Z${zone.zone.level} ${zone.label}: ${zone.lowerBound}-${zone.upperBound} $unit", style = MaterialTheme.typography.bodySmall)
    }
    Spacer(Modifier.height(12.dp))
}
