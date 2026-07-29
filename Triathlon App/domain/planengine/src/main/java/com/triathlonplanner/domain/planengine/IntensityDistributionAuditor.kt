package com.triathlonplanner.domain.planengine

import com.triathlonplanner.core.model.GeneratedWeek
import com.triathlonplanner.core.model.TrainingPhase

/**
 * Measured time-in-zone split for a block of training. Fractions sum to 1.0 (or all-zero when no
 * zoned time exists at all).
 */
data class IntensityDistribution(
    val easySec: Int,
    val moderateSec: Int,
    val hardSec: Int,
) {
    val totalSec: Int get() = easySec + moderateSec + hardSec

    val easyFraction: Double get() = if (totalSec == 0) 0.0 else easySec.toDouble() / totalSec
    val moderateFraction: Double get() = if (totalSec == 0) 0.0 else moderateSec.toDouble() / totalSec
    val hardFraction: Double get() = if (totalSec == 0) 0.0 else hardSec.toDouble() / totalSec

    operator fun plus(other: IntensityDistribution) = IntensityDistribution(
        easySec + other.easySec,
        moderateSec + other.moderateSec,
        hardSec + other.hardSec,
    )

    companion object {
        val ZERO = IntensityDistribution(0, 0, 0)
    }
}

/**
 * Measures and enforces polarized intensity distribution.
 *
 * Seiler's polarized model - reproduced across elite endurance cohorts in rowing, running,
 * cycling, XC skiing and triathlon - finds roughly 80% of training time below the first lactate
 * turnpoint (Zone 1-2 here) and roughly 20% at or above the second turnpoint (Zone 4-5), with
 * deliberately *little* time in the Zone 3 "grey zone": moderate work is hard enough to generate
 * meaningful fatigue but not hard enough to drive the top-end adaptations that make the fatigue
 * worth paying for.
 *
 * Three measurement decisions matter for this to be honest:
 *
 * 1. **Time in zone, not session labels.** A "threshold session" is mostly warmup, recoveries and
 *    cooldown; counting its whole duration as hard overstates intensity by 2-3x. This walks the
 *    actual [com.triathlonplanner.core.model.GeneratedWorkoutStep] tree, multiplying each step by
 *    its repeat count.
 * 2. **Grey-zone time is reported separately** rather than folded into "easy", so a plan cannot
 *    pass the 80/20 check by hiding volume in Zone 3.
 * 3. **Unzoned work is excluded.** Strength sessions carry no cardiovascular zone and would
 *    otherwise inflate the easy fraction.
 */
object IntensityDistributionAuditor {

    /**
     * The 80/20 rule proper: measured across the **whole plan**, not week by week.
     *
     * This distinction is not a convenience - it is what the underlying research actually claims.
     * Seiler's distributions are computed over training years and mesocycles, and within those
     * datasets competition-phase weeks routinely run hotter than the average while base blocks run
     * cooler. Holding every individual week to 80/20 would forbid a peak week from doing the one
     * thing a peak week exists to do. So the rule is enforced here, plan-wide and for every
     * distance, and individual weeks are held to the looser per-phase floors below.
     */
    const val MIN_EASY_FRACTION = 0.80

    /**
     * Per-week floors. Build blocks introduce race-specific intensity on top of a still-large
     * aerobic base; peak blocks are dominated by race-specific work, which for short course sits
     * at or above threshold by definition. Short-course plans sit near these floors and long-course
     * plans far above them - correctly, since an Ironman is raced at an aerobic intensity and a
     * sprint is not.
     */
    const val BASE_MIN_EASY_FRACTION = 0.80
    const val BUILD_MIN_EASY_FRACTION = 0.75
    const val PEAK_MIN_EASY_FRACTION = 0.68

    /** Grey-zone ceiling: the whole point of polarization is that this stays small. */
    const val MAX_MODERATE_FRACTION = 0.10

    /** Taper and race week are dominated by deliberate volume reduction, not distribution. */
    private val UNPOLICED_PHASES = setOf(TrainingPhase.TAPER, TrainingPhase.RACE_WEEK)

    fun distributionFor(week: GeneratedWeek): IntensityDistribution =
        week.workouts.fold(IntensityDistribution.ZERO) { acc, workout ->
            acc + workout.steps.fold(IntensityDistribution.ZERO) { stepAcc, step ->
                val level = step.intensityZone?.level ?: return@fold stepAcc
                val seconds = (step.durationSec ?: 0) * (step.repeatCount ?: 1)
                stepAcc + when (level) {
                    1, 2 -> IntensityDistribution(seconds, 0, 0)
                    3 -> IntensityDistribution(0, seconds, 0)
                    else -> IntensityDistribution(0, 0, seconds)
                }
            }
        }

    fun distributionFor(weeks: List<GeneratedWeek>): IntensityDistribution =
        weeks.fold(IntensityDistribution.ZERO) { acc, week -> acc + distributionFor(week) }

    /** The minimum easy fraction this phase is held to, or null if the phase isn't policed. */
    fun minEasyFractionFor(phase: TrainingPhase): Double? = when (phase) {
        in UNPOLICED_PHASES -> null
        TrainingPhase.PEAK -> PEAK_MIN_EASY_FRACTION
        TrainingPhase.BUILD -> BUILD_MIN_EASY_FRACTION
        else -> BASE_MIN_EASY_FRACTION
    }

    /**
     * Human-readable warnings for any week whose distribution violates the model, plus one for the
     * plan as a whole. Returns an empty list for a compliant plan. Surfaced through
     * [com.triathlonplanner.core.model.GeneratedPlan.warnings] rather than thrown: a plan that
     * skews hard is a coaching concern to flag, not a reason to refuse to generate anything.
     */
    fun auditPlan(weeks: List<GeneratedWeek>): List<String> {
        val warnings = mutableListOf<String>()

        weeks.forEach { week ->
            val minEasy = minEasyFractionFor(week.phase) ?: return@forEach
            val distribution = distributionFor(week)
            if (distribution.totalSec == 0) return@forEach

            if (distribution.easyFraction < minEasy) {
                warnings += "Week ${week.weekIndex} (${week.phase.name}) is only " +
                    "${percent(distribution.easyFraction)} easy training, below the " +
                    "${percent(minEasy)} this phase targets - that skews harder than a polarized plan should."
            }
            if (distribution.moderateFraction > MAX_MODERATE_FRACTION) {
                warnings += "Week ${week.weekIndex} (${week.phase.name}) spends " +
                    "${percent(distribution.moderateFraction)} in Zone 3 - moderate 'grey zone' work " +
                    "adds fatigue without the adaptation that harder work buys."
            }
        }

        val overall = distributionFor(weeks)
        if (overall.totalSec > 0 && overall.easyFraction < MIN_EASY_FRACTION) {
            warnings += "Across the whole plan only ${percent(overall.easyFraction)} of training is easy, " +
                "below the 80% a polarized plan targets."
        }
        return warnings
    }

    private fun percent(fraction: Double): String = "${Math.round(fraction * 100)}%"
}
