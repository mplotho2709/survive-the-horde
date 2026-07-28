package com.triathlonplanner.feature.profile

import com.triathlonplanner.data.healthconnect.HealthConnectAvailability
import com.triathlonplanner.domain.zones.ZoneRange

data class ProfileUiState(
    val healthConnectAvailability: HealthConnectAvailability = HealthConnectAvailability.NOT_INSTALLED,
    val healthConnectPermissionsGranted: Boolean = false,
    val maxHrInput: String = "",
    val restingHrInput: String = "",
    val ftpInput: String = "",
    val cssMinutesInput: String = "",
    val cssSecondsInput: String = "",
    val hrZones: List<ZoneRange> = emptyList(),
    val powerZones: List<ZoneRange> = emptyList(),
    val swimZones: List<ZoneRange> = emptyList(),
    val ftpTestAvgPowerInput: String = "",
    val cssTest400SecInput: String = "",
    val cssTest200SecInput: String = "",
    val isLoading: Boolean = true,
    val isSaved: Boolean = false,
)
