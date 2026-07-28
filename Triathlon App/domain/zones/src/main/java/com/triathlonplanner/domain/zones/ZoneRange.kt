package com.triathlonplanner.domain.zones

import com.triathlonplanner.core.model.IntensityZone

/**
 * A concrete, resolved zone boundary. [lowerBound]/[upperBound] units depend on the calculator
 * that produced this range: bpm for HR zones, watts for power zones, seconds-per-100m for swim
 * pace zones (where a *lower* value means faster/harder, unlike the other two).
 */
data class ZoneRange(
    val zone: IntensityZone,
    val label: String,
    val lowerBound: Int,
    val upperBound: Int,
)
