package com.triathlonplanner.core.model

/**
 * The inputs needed to compute concrete training zones. FTP and CSS are optional - PlanGenerator
 * and the zone calculators degrade gracefully (HR/RPE-based prescriptions) when they're absent.
 */
data class UserZoneProfile(
    val maxHr: Int,
    val restingHr: Int? = null,
    val ftpWatts: Int? = null,
    val ftpSource: FtpSource? = null,
    val cssPaceSecPer100m: Int? = null,
    val cssSource: CssSource? = null,
    val weightKg: Double? = null,
)
