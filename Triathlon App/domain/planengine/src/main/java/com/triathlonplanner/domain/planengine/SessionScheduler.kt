package com.triathlonplanner.domain.planengine

import com.triathlonplanner.core.model.DayPreferences
import com.triathlonplanner.core.model.Discipline
import com.triathlonplanner.core.model.WorkoutType
import java.time.DayOfWeek

data class ScheduleResult(val specs: List<WorkoutSpec>, val warnings: List<String>)

/**
 * Places a week's sessions onto actual weekdays, honouring the athlete's per-discipline day
 * availability and their chosen long-session days, while keeping the arrangement physiologically
 * sound - and varying it week to week so a 20-week block isn't the same seven days repeated.
 *
 * **Why a scheduler at all.** [WeeklyTemplate] emits sessions on fixed weekdays, which meant every
 * week of every plan looked identical and ignored when the athlete can actually get to a pool. This
 * separates *what* training is needed (the template's job) from *when* it happens (this).
 *
 * **The principles it encodes**, in the order they win when they conflict:
 * 1. Brick legs stay on one day, bike before run - a brick that isn't back-to-back isn't a brick.
 * 2. Never two hard sessions, or two long sessions, on the same day.
 * 3. Long sessions land on the athlete's designated long days.
 * 4. No quality session the day after a long one; adaptation needs the easy day to happen.
 * 5. Hard days get spread rather than stacked - consecutive hard days blunt both of them.
 * 6. Repeats of one discipline get spread across the week; frequency beats clustering, and it
 *    matters most in the water where technique decays between sessions.
 * 7. Remaining sessions balance across the available days.
 *
 * **Where the variance comes from.** Constraints decide which days are *acceptable*; when several
 * are equally acceptable the tie is broken by an ordering rotated by week index. So an athlete who
 * can run Tue/Thu/Sat but has two runs scheduled gets Tue+Sat one week and Thu+Sat the next. The
 * rotation is arithmetic on the week index, never a random draw, so regenerating a plan reproduces
 * it exactly. Long days are deliberately *excluded* from rotation: the athlete chose those on
 * purpose, and shuffling the long ride around would undo the point of asking.
 */
object SessionScheduler {

    private val HARD_TYPES = setOf(
        WorkoutType.THRESHOLD,
        WorkoutType.VO2MAX,
        WorkoutType.TEMPO,
        WorkoutType.RACE_PACE,
        WorkoutType.FTP_TEST,
        WorkoutType.CSS_TEST,
    )

    // Hard blocks: arrangements that are physiologically wrong rather than merely suboptimal.
    private const val PENALTY_SAME_DAY_HARD = -1000
    private const val PENALTY_SAME_DAY_LONG = -1000

    // Soft costs, weighted by how much each actually degrades the week.
    private const val PENALTY_HARD_AFTER_LONG = -60
    private const val PENALTY_HARD_ADJACENT = -40
    private const val PENALTY_SAME_DISCIPLINE_SAME_DAY = -30
    private const val PENALTY_PER_EXISTING_SESSION = -8

    /**
     * Rewrites each spec's [WorkoutSpec.dayOfWeek]. Returns the input untouched when the athlete
     * expressed no preferences, so existing plans and the template's own layout are unaffected.
     */
    fun schedule(
        specs: List<WorkoutSpec>,
        preferences: DayPreferences?,
        weekIndex: Int,
        maxTrainingDays: Int? = null,
    ): ScheduleResult {
        if (preferences == null || preferences.isEmpty || specs.isEmpty()) {
            return ScheduleResult(specs, emptyList())
        }

        val warnings = mutableListOf<String>()
        val week = WeekBoard()
        val placed = mutableListOf<WorkoutSpec>()

        // Cap distinct training days: day preferences say *where* training may go, the days-per-week
        // target says how much of the week it should occupy. Both are the athlete's own input.
        val allowedDays = preferences.allTrainingDays.ifEmpty { DayOfWeek.entries.toSet() }
        val dayBudget = maxTrainingDays?.coerceAtLeast(1)

        val (brickLegs, singles) = specs.partition { it.isBrickLeg }

        // 1. Bricks first - they're the most constrained (two disciplines, one day) and the most
        //    race-specific thing in the week, so they get first choice.
        brickLegs.groupBy { it.dayOfWeek }.forEach { (_, legs) ->
            val day = chooseBrickDay(legs, preferences, allowedDays, week, weekIndex, warnings)
            legs.forEach { leg ->
                placed += leg.copy(dayOfWeek = day.value)
                week.record(day, leg)
            }
        }

        // 2. Long sessions onto the designated long days, bike before run so a long run lands on
        //    legs already carrying some fatigue - which is what race day asks of them.
        val (longs, rest) = singles.partition { it.workoutType == WorkoutType.LONG }
        longs.sortedBy { disciplineOrder(it.discipline) }.forEach { spec ->
            val day = chooseLongDay(spec, preferences, allowedDays, week, warnings)
            placed += spec.copy(dayOfWeek = day.value)
            week.record(day, spec)
        }

        // 3. Then quality, then everything else. Hard sessions are placed before easy ones because
        //    they have the most spacing requirements and easy work can fill whatever is left.
        val (hard, easy) = rest.partition { it.workoutType in HARD_TYPES }
        (hard + easy).forEach { spec ->
            val day = chooseDay(spec, preferences, allowedDays, week, weekIndex, dayBudget)
            placed += spec.copy(dayOfWeek = day.value)
            week.record(day, spec)
        }

        return ScheduleResult(placed.sortedWith(compareBy({ it.dayOfWeek }, { it.sortOrderInDay })), warnings.distinct())
    }

    /** A brick needs both its disciplines on one day; a long day is preferred but not required. */
    private fun chooseBrickDay(
        legs: List<WorkoutSpec>,
        preferences: DayPreferences,
        allowedDays: Set<DayOfWeek>,
        week: WeekBoard,
        weekIndex: Int,
        warnings: MutableList<String>,
    ): DayOfWeek {
        val bothDisciplines = legs
            .map { preferences.daysFor(it.discipline).ifEmpty { allowedDays } }
            .reduce { acc, days -> acc intersect days }

        val longAndBoth = bothDisciplines intersect preferences.longSessionDays
        val candidates = when {
            longAndBoth.isNotEmpty() -> longAndBoth
            bothDisciplines.isNotEmpty() -> bothDisciplines
            else -> {
                warnings += "Your bike and run days don't share a day, so the brick session " +
                    "(a ride followed straight by a run) had to be placed on a day you didn't pick for both."
                preferences.longSessionDays.ifEmpty { allowedDays }
            }
        }
        return bestDay(candidates.ifEmpty { allowedDays }, legs.first(), week, weekIndex, rotate = false)
    }

    private fun chooseLongDay(
        spec: WorkoutSpec,
        preferences: DayPreferences,
        allowedDays: Set<DayOfWeek>,
        week: WeekBoard,
        warnings: MutableList<String>,
    ): DayOfWeek {
        val disciplineDays = preferences.daysFor(spec.discipline).ifEmpty { allowedDays }
        val longDays = preferences.longSessionDays

        val ideal = disciplineDays intersect longDays
        val candidates = when {
            ideal.isNotEmpty() -> ideal
            longDays.isNotEmpty() -> {
                warnings += "None of your long-session days line up with your " +
                    "${spec.discipline.name.lowercase()} days, so the long " +
                    "${spec.discipline.name.lowercase()} was scheduled on a long day anyway."
                longDays
            }
            else -> disciplineDays
        }
        // Chronological, not rotated: the athlete picked these days deliberately, and keeping the
        // long ride ahead of the long run is worth more than novelty.
        return bestDay(candidates, spec, week, weekIndex = 0, rotate = false)
    }

    private fun chooseDay(
        spec: WorkoutSpec,
        preferences: DayPreferences,
        allowedDays: Set<DayOfWeek>,
        week: WeekBoard,
        weekIndex: Int,
        dayBudget: Int?,
    ): DayOfWeek {
        val disciplineDays = preferences.daysFor(spec.discipline).ifEmpty { allowedDays }

        // Once the day budget is spent, new sessions double up on days already in use rather than
        // opening another training day the athlete didn't ask for.
        val withinBudget = if (dayBudget != null && week.daysUsed >= dayBudget) {
            disciplineDays intersect week.usedDays
        } else {
            disciplineDays
        }
        return bestDay(withinBudget.ifEmpty { disciplineDays }, spec, week, weekIndex, rotate = true)
    }

    /**
     * Highest-scoring day, with ties broken by a week-rotated ordering. The rotation is what makes
     * consecutive weeks differ; without it the same day would always win an equal contest.
     */
    private fun bestDay(
        candidates: Set<DayOfWeek>,
        spec: WorkoutSpec,
        week: WeekBoard,
        weekIndex: Int,
        rotate: Boolean,
    ): DayOfWeek {
        val ordered = candidates.sortedBy { it.value }
        val tieBreakOrder = if (rotate && ordered.isNotEmpty()) {
            val shift = weekIndex.mod(ordered.size)
            ordered.drop(shift) + ordered.take(shift)
        } else {
            ordered
        }
        return tieBreakOrder.maxByOrNull { day -> week.score(day, spec) } ?: DayOfWeek.MONDAY
    }

    /** Bike before run when both need a long day. */
    private fun disciplineOrder(discipline: Discipline): Int = when (discipline) {
        Discipline.BIKE, Discipline.BRICK_BIKE -> 0
        Discipline.RUN, Discipline.BRICK_RUN -> 1
        Discipline.SWIM -> 2
        else -> 3
    }

    /** Mutable view of what has been placed so far, and the scoring rules that read it. */
    private class WeekBoard {
        private val hardDays = mutableSetOf<DayOfWeek>()
        private val longDays = mutableSetOf<DayOfWeek>()
        private val disciplinesByDay = mutableMapOf<DayOfWeek, MutableSet<Discipline>>()
        private val countByDay = mutableMapOf<DayOfWeek, Int>()

        val usedDays: Set<DayOfWeek> get() = countByDay.keys
        val daysUsed: Int get() = countByDay.size

        fun record(day: DayOfWeek, spec: WorkoutSpec) {
            if (spec.workoutType in HARD_TYPES) hardDays += day
            if (spec.workoutType == WorkoutType.LONG) longDays += day
            disciplinesByDay.getOrPut(day) { mutableSetOf() } += baseDiscipline(spec.discipline)
            countByDay[day] = (countByDay[day] ?: 0) + 1
        }

        fun score(day: DayOfWeek, spec: WorkoutSpec): Int {
            var score = 0
            val isHard = spec.workoutType in HARD_TYPES
            val isLong = spec.workoutType == WorkoutType.LONG

            if (isHard && day in hardDays) score += PENALTY_SAME_DAY_HARD
            if (isLong && day in longDays) score += PENALTY_SAME_DAY_LONG

            if (isHard) {
                val previous = day.minus(1)
                val next = day.plus(1)
                if (previous in longDays) score += PENALTY_HARD_AFTER_LONG
                if (previous in hardDays) score += PENALTY_HARD_ADJACENT
                if (next in hardDays) score += PENALTY_HARD_ADJACENT
            }

            if (baseDiscipline(spec.discipline) in disciplinesByDay[day].orEmpty()) {
                score += PENALTY_SAME_DISCIPLINE_SAME_DAY
            }
            score += (countByDay[day] ?: 0) * PENALTY_PER_EXISTING_SESSION
            return score
        }

        /** Brick legs count as their underlying sport for spacing purposes. */
        private fun baseDiscipline(discipline: Discipline): Discipline = when (discipline) {
            Discipline.BRICK_BIKE -> Discipline.BIKE
            Discipline.BRICK_RUN -> Discipline.RUN
            else -> discipline
        }
    }
}
