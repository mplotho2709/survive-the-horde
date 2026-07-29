package com.triathlonplanner.core.model

/**
 * The inputs needed to compute concrete training zones. FTP, CSS and run threshold pace are all
 * optional - PlanGenerator and the zone calculators degrade gracefully (falling back to
 * heart-rate prescriptions) when they're absent. Each discipline has its own anchor because each
 * measures external work differently: watts on the bike, pace per 100m in the water, pace per km
 * on the run.
 */
data class UserZoneProfile(
    val maxHr: Int,
    val restingHr: Int? = null,
    val ftpWatts: Int? = null,
    val ftpSource: FtpSource? = null,
    val cssPaceSecPer100m: Int? = null,
    val cssSource: CssSource? = null,
    /** Daniels' T-pace: ~one-hour race pace. See RunPaceZoneCalculator for how it's derived. */
    val thresholdRunPaceSecPerKm: Int? = null,
    val weightKg: Double? = null,
)
