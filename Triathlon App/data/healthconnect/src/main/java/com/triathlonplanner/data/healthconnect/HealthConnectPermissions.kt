package com.triathlonplanner.data.healthconnect

import android.content.Context
import androidx.activity.result.contract.ActivityResultContract
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.PowerRecord
import androidx.health.connect.client.records.SpeedRecord

/**
 * Read-only permission set. Cadence is intentionally not requested as its own permission - per
 * Health Connect's permission model, CyclingPedalingCadenceRecord is covered by the Exercise
 * permission already requested here.
 */
object HealthConnectPermissions {
    val REQUIRED: Set<String> = setOf(
        HealthPermission.getReadPermission(ExerciseSessionRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(PowerRecord::class),
        HealthPermission.getReadPermission(SpeedRecord::class),
        HealthPermission.getReadPermission(DistanceRecord::class),
    )
}

enum class HealthConnectAvailability {
    AVAILABLE,
    NOT_INSTALLED,
    UPDATE_REQUIRED,
}

fun Int.toHealthConnectAvailability(): HealthConnectAvailability = when (this) {
    HealthConnectClient.SDK_AVAILABLE -> HealthConnectAvailability.AVAILABLE
    HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> HealthConnectAvailability.UPDATE_REQUIRED
    else -> HealthConnectAvailability.NOT_INSTALLED
}

/** Static SDK/app-installation check - separate from [HealthConnectDataSource] since it needs a Context, not a client instance (there may be no client yet if HC isn't installed). */
fun checkHealthConnectAvailability(context: Context): HealthConnectAvailability =
    HealthConnectClient.getSdkStatus(context).toHealthConnectAvailability()

/**
 * Wraps [PermissionController.createRequestPermissionResultContract] so feature/UI modules don't
 * need a direct dependency on the Health Connect SDK just to launch the permission request -
 * :data:healthconnect declared it as `implementation`, not `api`, so it isn't visible transitively.
 */
fun healthConnectPermissionRequestContract(): ActivityResultContract<Set<String>, Set<String>> =
    PermissionController.createRequestPermissionResultContract()
