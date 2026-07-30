package com.triathlonplanner.feature.profile

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.triathlonplanner.core.designsystem.AppCard
import com.triathlonplanner.core.designsystem.AppRadius
import com.triathlonplanner.core.designsystem.AppSpacing
import com.triathlonplanner.core.designsystem.SectionHeader
import com.triathlonplanner.core.designsystem.accents
import com.triathlonplanner.data.healthconnect.HealthConnectAvailability
import com.triathlonplanner.data.healthconnect.HealthConnectPermissions
import com.triathlonplanner.data.healthconnect.healthConnectPermissionRequestContract
import com.triathlonplanner.domain.zones.ZoneRange

@Composable
fun ProfileScreen(viewModel: ProfileViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showCancelPlanDialog by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(
        contract = healthConnectPermissionRequestContract(),
    ) { granted -> viewModel.onHealthConnectPermissionsResult(granted.containsAll(HealthConnectPermissions.REQUIRED)) }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { padding ->
        if (state.isLoading) {
            CircularProgressIndicator(Modifier.padding(padding).padding(AppSpacing.xl))
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = AppSpacing.gutter),
        ) {
            Text(
                "Profile",
                style = MaterialTheme.typography.displaySmall,
                modifier = Modifier.padding(top = AppSpacing.xl, bottom = AppSpacing.md),
            )

            AppCard(Modifier.fillMaxWidth()) {
                SectionHeader("Your thresholds")
                Spacer(Modifier.height(AppSpacing.md))
                NumberField(state.maxHrInput, viewModel::updateMaxHr, "Max HR", "bpm")
                Spacer(Modifier.height(AppSpacing.sm))
                NumberField(state.restingHrInput, viewModel::updateRestingHr, "Resting HR", "bpm")
                Spacer(Modifier.height(AppSpacing.sm))
                NumberField(state.ftpInput, viewModel::updateFtp, "FTP", "W")
                Spacer(Modifier.height(AppSpacing.md))
                Text(
                    "Critical Swim Speed per 100m",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(AppSpacing.xs))
                Row {
                    NumberField(state.cssMinutesInput, viewModel::updateCssMinutes, "min", null, Modifier.weight(1f))
                    Spacer(Modifier.width(AppSpacing.sm))
                    NumberField(state.cssSecondsInput, viewModel::updateCssSeconds, "sec", null, Modifier.weight(1f))
                }
                Spacer(Modifier.height(AppSpacing.lg))
                Button(
                    onClick = viewModel::save,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(AppRadius.cell),
                ) {
                    if (state.isSaved) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(AppSpacing.sm))
                    }
                    Text(if (state.isSaved) "Saved" else "Save", style = MaterialTheme.typography.labelLarge)
                }
            }

            Spacer(Modifier.height(AppSpacing.md))

            AppCard(Modifier.fillMaxWidth()) {
                SectionHeader("Don't know your numbers?")
                Spacer(Modifier.height(AppSpacing.md))

                Text("20-minute bike test", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Ride 20 minutes as hard as you can hold, then enter your average power.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(AppSpacing.sm))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NumberField(state.ftpTestAvgPowerInput, viewModel::updateFtpTestAvgPower, "Avg power", "W", Modifier.weight(1f))
                    Spacer(Modifier.width(AppSpacing.sm))
                    OutlinedButton(onClick = viewModel::applyFtpTestResult, shape = RoundedCornerShape(AppRadius.cell)) {
                        Text("Apply")
                    }
                }

                Spacer(Modifier.height(AppSpacing.lg))

                Text("Swim time trial", style = MaterialTheme.typography.titleSmall)
                Text(
                    "Swim 400m all out, rest fully, then 200m all out. Enter both times in seconds.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(AppSpacing.sm))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    NumberField(state.cssTest400SecInput, viewModel::updateCssTest400Sec, "400m", "s", Modifier.weight(1f))
                    Spacer(Modifier.width(AppSpacing.sm))
                    NumberField(state.cssTest200SecInput, viewModel::updateCssTest200Sec, "200m", "s", Modifier.weight(1f))
                }
                Spacer(Modifier.height(AppSpacing.sm))
                OutlinedButton(
                    onClick = viewModel::applyCssTestResult,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(AppRadius.cell),
                ) { Text("Apply swim result") }
            }

            Spacer(Modifier.height(AppSpacing.md))

            ZoneCard("Heart rate zones", state.hrZones, "bpm", MaterialTheme.accents.run)
            ZoneCard("Power zones", state.powerZones, "W", MaterialTheme.accents.bike)
            ZoneCard("Swim pace zones", state.swimZones, "/100m", MaterialTheme.accents.swim)

            AppCard(Modifier.fillMaxWidth()) {
                SectionHeader("Health Connect")
                Spacer(Modifier.height(AppSpacing.sm))
                when (state.healthConnectAvailability) {
                    HealthConnectAvailability.NOT_INSTALLED -> Text(
                        "Health Connect isn't installed on this device.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    HealthConnectAvailability.UPDATE_REQUIRED -> Text(
                        "Health Connect needs an update before it can sync.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    HealthConnectAvailability.AVAILABLE -> if (state.healthConnectPermissionsGranted) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(8.dp).background(MaterialTheme.accents.success, CircleShape))
                            Spacer(Modifier.width(AppSpacing.sm))
                            Text(
                                "Connected - your Garmin workouts sync automatically.",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    } else {
                        Button(
                            onClick = { launcher.launch(HealthConnectPermissions.REQUIRED) },
                            modifier = Modifier.fillMaxWidth().height(48.dp),
                            shape = RoundedCornerShape(AppRadius.cell),
                        ) { Text("Grant access", style = MaterialTheme.typography.labelLarge) }
                    }
                }
            }

            Spacer(Modifier.height(AppSpacing.md))

            AppCard(Modifier.fillMaxWidth()) {
                SectionHeader("Training plan")
                Spacer(Modifier.height(AppSpacing.sm))
                Text(
                    "Cancelling keeps all your history, but you'll set a new goal to get a new plan.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(AppSpacing.md))
                OutlinedButton(
                    onClick = { showCancelPlanDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(AppRadius.cell),
                ) { Text("Cancel current plan", color = MaterialTheme.colorScheme.error) }
            }

            Spacer(Modifier.height(AppSpacing.xl))
        }
    }

    if (showCancelPlanDialog) {
        AlertDialog(
            onDismissRequest = { showCancelPlanDialog = false },
            title = { Text("Cancel your training plan?") },
            text = { Text("Your progress and workout history are kept, but you'll need to set up a new goal to get a new plan.") },
            shape = RoundedCornerShape(AppRadius.card),
            confirmButton = {
                TextButton(onClick = {
                    showCancelPlanDialog = false
                    viewModel.abandonPlan()
                }) { Text("Cancel plan", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showCancelPlanDialog = false }) { Text("Keep plan") }
            },
        )
    }
}

@Composable
private fun NumberField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    suffix: String?,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        suffix = suffix?.let { unit -> { Text(unit, style = MaterialTheme.typography.labelMedium) } },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        shape = RoundedCornerShape(AppRadius.cell),
        modifier = modifier.fillMaxWidth(),
    )
}

/**
 * Zone table drawn as a stack of proportional bars. Bar length encodes where each band sits relative
 * to the widest one and the accent deepens with zone level, so the intensity ramp is visible at a
 * glance instead of having to be reconstructed from five pairs of numbers.
 */
@Composable
private fun ZoneCard(title: String, zones: List<ZoneRange>, unit: String, accent: Color) {
    if (zones.isEmpty()) return
    val maxBound = zones.maxOf { maxOf(it.lowerBound, it.upperBound) }.coerceAtLeast(1)

    AppCard(Modifier.fillMaxWidth()) {
        SectionHeader(title)
        Spacer(Modifier.height(AppSpacing.md))
        zones.forEachIndexed { index, zone ->
            val ramp = 0.30f + 0.70f * (index.toFloat() / (zones.size - 1).coerceAtLeast(1))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Z${zone.zone.level}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = accent,
                    modifier = Modifier.width(26.dp),
                )
                Column(Modifier.weight(1f)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(zone.label, style = MaterialTheme.typography.bodySmall)
                        Text(
                            "${zone.lowerBound}-${zone.upperBound} $unit",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(3.dp))
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .background(MaterialTheme.accents.track, RoundedCornerShape(AppRadius.pill)),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(
                                    (maxOf(zone.lowerBound, zone.upperBound).toFloat() / maxBound).coerceIn(0f, 1f),
                                )
                                .height(4.dp)
                                .background(accent.copy(alpha = ramp), RoundedCornerShape(AppRadius.pill)),
                        )
                    }
                }
            }
            if (index < zones.lastIndex) Spacer(Modifier.height(AppSpacing.sm))
        }
    }
    Spacer(Modifier.height(AppSpacing.md))
}
