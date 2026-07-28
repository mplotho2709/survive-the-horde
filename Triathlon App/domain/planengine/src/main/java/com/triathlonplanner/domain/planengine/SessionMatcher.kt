package com.triathlonplanner.domain.planengine

import com.triathlonplanner.core.model.CompletedActivity
import com.triathlonplanner.core.model.Discipline
import com.triathlonplanner.core.model.MatchStatus
import com.triathlonplanner.core.model.PlannedWorkoutSnapshot
import com.triathlonplanner.core.model.WorkoutStatus

/**
 * Matches a single day's completed activities against that day's still-PLANNED workouts.
 * A brick day's planned BRICK_BIKE/BRICK_RUN legs are matched against plain BIKE/RUN activities
 * (Health Connect has no "brick" exercise type - it's a planning-side concept). When multiple
 * activities share a discipline (e.g. two bike sessions in one day), both lists are sorted and
 * paired in order rather than matched arbitrarily, so a brick's bike-then-run ordering is
 * preserved. Unmatched activities are kept (marked UNMATCHED_EXTRA) since they still count
 * toward the rolling load accumulator even though they don't complete a plan entry.
 */
object SessionMatcher {

    fun matchDay(
        activities: List<CompletedActivity>,
        plannedWorkouts: List<PlannedWorkoutSnapshot>,
    ): List<CompletedActivity> {
        val stillPlanned = plannedWorkouts.filter { it.status == WorkoutStatus.PLANNED }
        val result = mutableListOf<CompletedActivity>()

        val activitiesByDiscipline = activities.groupBy { it.discipline }
        val consumedPlannedIds = mutableSetOf<Long>()

        for ((activityDiscipline, sameDisciplineActivities) in activitiesByDiscipline) {
            val candidates = stillPlanned
                .filter { disciplineMatches(activityDiscipline, it.discipline) && it.id !in consumedPlannedIds }
                .sortedBy { it.sortOrderInDay }

            val sortedActivities = sameDisciplineActivities.sortedBy { it.startTime }

            sortedActivities.forEachIndexed { index, activity ->
                val candidate = candidates.getOrNull(index)
                if (candidate == null) {
                    result += activity.copy(matchStatus = MatchStatus.UNMATCHED_EXTRA)
                } else {
                    consumedPlannedIds += candidate.id
                    result += activity.copy(
                        matchedPlannedWorkoutId = candidate.id,
                        matchStatus = MatchStatus.MATCHED,
                    )
                }
            }
        }

        return result
    }

    private fun disciplineMatches(activityDiscipline: Discipline, plannedDiscipline: Discipline): Boolean =
        when (plannedDiscipline) {
            Discipline.BRICK_BIKE -> activityDiscipline == Discipline.BIKE
            Discipline.BRICK_RUN -> activityDiscipline == Discipline.RUN
            else -> activityDiscipline == plannedDiscipline
        }
}
