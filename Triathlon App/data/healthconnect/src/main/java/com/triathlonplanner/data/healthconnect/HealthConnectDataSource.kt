package com.triathlonplanner.data.healthconnect

import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.aggregate.AggregateMetric
import androidx.health.connect.client.changes.UpsertionChange
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.PowerRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ChangesTokenRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.triathlonplanner.core.model.CompletedActivity
import com.triathlonplanner.core.model.Discipline
import com.triathlonplanner.domain.planengine.NormalizedPowerCalculator
import java.time.Instant
import javax.inject.Inject

data class ChangesResult(val newOrUpdatedSessions: List<ExerciseSessionRecord>, val nextChangeToken: String)

/**
 * Thin wrapper over [HealthConnectClient]. Deliberately does NOT compute [CompletedActivity.calculatedLoad] -
 * that needs the user's zone profile (max HR, FTP), which lives in Room/:data:repository, not here.
 * Callers must recompute it before persisting; this class leaves it at 0 as an explicit placeholder.
 */
class HealthConnectDataSource @Inject constructor(
    private val healthConnectClient: HealthConnectClient,
) {

    suspend fun getGrantedPermissions(): Set<String> = healthConnectClient.permissionController.getGrantedPermissions()

    suspend fun hasAllPermissions(): Boolean = getGrantedPermissions().containsAll(HealthConnectPermissions.REQUIRED)

    suspend fun getChangesToken(): String = healthConnectClient.getChangesToken(
        ChangesTokenRequest(
            recordTypes = setOf(
                ExerciseSessionRecord::class,
                HeartRateRecord::class,
                PowerRecord::class,
                DistanceRecord::class,
            ),
        ),
    )

    /** Historical (non-diff) read, used for both the first-ever sync backfill and the explicit
     * "import history before this plan" backfill - the Changes API used by [getChangedSessions]
     * only reports events going forward from token creation, so it would otherwise silently miss
     * any session Health Connect already had before that moment. */
    suspend fun getCompletedActivitiesSince(startTime: Instant): List<CompletedActivity> {
        val sessions = healthConnectClient.readRecords(
            ReadRecordsRequest(ExerciseSessionRecord::class, timeRangeFilter = TimeRangeFilter.after(startTime)),
        ).records
        return sessions.mapNotNull { buildCompletedActivity(it) }
    }

    suspend fun getChangedSessions(changeToken: String): ChangesResult {
        val newSessions = mutableListOf<ExerciseSessionRecord>()
        var token = changeToken
        while (true) {
            val response = healthConnectClient.getChanges(token)
            newSessions += response.changes.filterIsInstance<UpsertionChange>().mapNotNull { it.record as? ExerciseSessionRecord }
            token = response.nextChangesToken
            if (!response.hasMore) break
        }
        return ChangesResult(newSessions, token)
    }

    /** Maps one session into our domain shape via aggregates (+ raw power samples for Normalized Power on rides). */
    suspend fun buildCompletedActivity(session: ExerciseSessionRecord): CompletedActivity? {
        val discipline = mapExerciseTypeToDiscipline(session.exerciseType) ?: return null
        val timeRange = TimeRangeFilter.between(session.startTime, session.endTime)

        val metrics = mutableSetOf<AggregateMetric<*>>(
            HeartRateRecord.BPM_AVG,
            HeartRateRecord.BPM_MAX,
            DistanceRecord.DISTANCE_TOTAL,
        )
        if (discipline == Discipline.BIKE) metrics += PowerRecord.POWER_AVG

        val aggregate = healthConnectClient.aggregate(AggregateRequest(metrics = metrics, timeRangeFilter = timeRange))

        val avgHr = runCatching { aggregate[HeartRateRecord.BPM_AVG] }.getOrNull()
        val maxHr = runCatching { aggregate[HeartRateRecord.BPM_MAX] }.getOrNull()
        val distanceM = runCatching { aggregate[DistanceRecord.DISTANCE_TOTAL]?.inMeters }.getOrNull()
        val avgPowerW = if (discipline == Discipline.BIKE) {
            runCatching { aggregate[PowerRecord.POWER_AVG]?.inWatts?.toInt() }.getOrNull()
        } else {
            null
        }

        val normalizedPowerW = if (discipline == Discipline.BIKE) {
            val powerRecords = healthConnectClient
                .readRecords(ReadRecordsRequest(PowerRecord::class, timeRangeFilter = timeRange))
                .records
            val samples = powerRecords.flatMap { record ->
                record.samples.map { NormalizedPowerCalculator.PowerSample(it.time, it.power.inWatts.toInt()) }
            }
            NormalizedPowerCalculator.calculate(samples)
        } else {
            null
        }

        return CompletedActivity(
            healthConnectRecordId = session.metadata.id,
            discipline = discipline,
            startTime = session.startTime,
            endTime = session.endTime,
            distanceM = distanceM,
            avgHr = avgHr?.toInt(),
            maxHr = maxHr?.toInt(),
            avgPowerW = avgPowerW,
            normalizedPowerW = normalizedPowerW,
            calculatedLoad = 0, // placeholder - see class doc; :data:repository recomputes from the user's profile
        )
    }
}
