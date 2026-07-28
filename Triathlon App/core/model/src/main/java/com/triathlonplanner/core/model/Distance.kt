package com.triathlonplanner.core.model

/**
 * Target race distance. [minWeeks]/[idealWeeks] and the phase split drive periodization in
 * PlanGenerator; taperWeeks is fixed regardless of available runway (Base is compressed first).
 */
enum class Distance(
    val displayName: String,
    val swimMeters: Int,
    val bikeMeters: Int,
    val runMeters: Int,
    val minWeeks: Int,
    val idealWeeks: Int,
    val basePercent: Double,
    val buildPercent: Double,
    val peakPercent: Double,
    val taperWeeks: Int,
) {
    SPRINT(
        displayName = "Sprint",
        swimMeters = 750,
        bikeMeters = 20_000,
        runMeters = 5_000,
        minWeeks = 8,
        idealWeeks = 12,
        basePercent = 0.40,
        buildPercent = 0.35,
        peakPercent = 0.15,
        taperWeeks = 1,
    ),
    OLYMPIC(
        displayName = "Olympic",
        swimMeters = 1_500,
        bikeMeters = 40_000,
        runMeters = 10_000,
        minWeeks = 10,
        idealWeeks = 16,
        basePercent = 0.40,
        buildPercent = 0.35,
        peakPercent = 0.15,
        taperWeeks = 1,
    ),
    HALF_IRON(
        displayName = "70.3 (Half-Iron)",
        swimMeters = 1_900,
        bikeMeters = 90_000,
        runMeters = 21_097,
        minWeeks = 12,
        idealWeeks = 20,
        basePercent = 0.45,
        buildPercent = 0.30,
        peakPercent = 0.15,
        taperWeeks = 2,
    ),
    FULL_IRON(
        displayName = "Full Iron-distance",
        swimMeters = 3_800,
        bikeMeters = 180_000,
        runMeters = 42_195,
        minWeeks = 16,
        idealWeeks = 28,
        basePercent = 0.50,
        buildPercent = 0.25,
        peakPercent = 0.15,
        taperWeeks = 3,
    ),
}
