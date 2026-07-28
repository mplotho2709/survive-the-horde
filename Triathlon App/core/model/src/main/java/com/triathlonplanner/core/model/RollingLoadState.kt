package com.triathlonplanner.core.model

import java.time.LocalDate

data class RollingLoadState(
    /** Trailing 7-day *average daily* load (not a raw window sum - keeps ACWR comparable). */
    val acuteLoad7d: Double = 0.0,
    /** Trailing 28-day average daily load. */
    val chronicLoad28d: Double = 0.0,
    val consecutiveMissedDays: Int = 0,
    val lastEvaluatedDate: LocalDate? = null,
) {
    /** Acute:Chronic Workload Ratio. >1.3-1.5 flags overreach risk; persistently <0.8 flags detraining. */
    val acwr: Double get() = if (chronicLoad28d > 0) acuteLoad7d / chronicLoad28d else 0.0
}
