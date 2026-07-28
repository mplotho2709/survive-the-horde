package com.triathlonplanner.core.model

import java.time.LocalDate

/** Lightweight, persistence-agnostic views for feature modules - never expose Room entities past :data:repository. */
data class ActivePlanSummary(
    val id: Long,
    val startDate: LocalDate,
    val totalWeeks: Int,
    val status: PlanStatus,
)

data class PlanWeekSummary(
    val id: Long,
    val weekIndex: Int,
    val startDate: LocalDate,
    val phase: TrainingPhase,
    val isRecoveryWeek: Boolean,
    val plannedWeeklyLoad: Int,
)

/** Planned vs. completed load for one week - the Progress screen's chart data point. */
data class WeeklyLoadPoint(
    val weekIndex: Int,
    val plannedLoad: Int,
    val completedLoad: Int,
)
