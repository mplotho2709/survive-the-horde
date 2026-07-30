package com.triathlonplanner.core.designsystem

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/*
 * Semantic colours - the ones that carry meaning rather than decoration.
 *
 * The three discipline hues are a *validated* categorical palette rather than a taste call. Run
 * through a colour-vision validator, blue/orange/aqua clear, in both light and dark mode: the
 * lightness band, the chroma floor, all-pairs colourblind separation (worst dE 9.2 light / 9.4
 * dark against an >=8 target) and all-pairs normal-vision separation (24.0 / 20.9 against a >=15
 * floor). The dark values are separately chosen steps for the dark surface, not a mechanical
 * lightening of the light ones.
 *
 * Only three disciplines get a hue, and that is a deliberate cap: no four-colour set cleared the
 * floors in both modes, and the domain supplies the honest tiebreak - swim, bike and run *are*
 * triathlon, so they carry identity, while strength and rest are supporting work and fold to
 * neutral. Brick legs reuse the bike and run hues because that is literally what they are.
 *
 * Colour is never the sole signal: every badge sits beside a text label, which is also what
 * discharges the validator's "relief required" note on light-mode aqua's sub-3:1 contrast.
 */
data class AppAccents(
    val swim: Color,
    val bike: Color,
    val run: Color,
    val neutral: Color,
    /** Recessive track behind a progress fill - a reference value, never competing with actuals. */
    val track: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
    /** Ordered intensity ramp for training phases: one hue, light to dark, plus a race-week accent. */
    val phaseBase: Color,
    val phaseBuild: Color,
    val phasePeak: Color,
    val phaseTaper: Color,
    val phaseRaceWeek: Color,
    /**
     * Intensity-zone ramp, indexed 0-based for zones 1..7 (power uses all seven; heart rate, swim
     * pace and run pace use the first five).
     *
     * This follows the grey/blue/green/amber/red/magenta/violet convention every training platform
     * shares, because an athlete arriving from Garmin or TrainingPeaks already reads "red" as "very
     * hard" - inventing a single-hue ramp would be technically tidier and practically worse. It is
     * an *ordinal* scale, so the categorical validator's chroma-floor and lightness-band checks
     * don't apply (a deliberately grey Zone 1 is the point). What does apply, and what was
     * verified: adjacent-pair colourblind separation and adjacent-pair normal-vision separation both
     * clear their floors in both modes, and every step clears 3:1 contrast in dark mode.
     *
     * Three light-mode steps sit under 3:1 on the light surface, which the validator flags as
     * "relief required". That is satisfied structurally: a zone colour never appears without its
     * "Z<n>" number or "Zone <n>" label beside it, so the colour reinforces an identity the text
     * already carries rather than being the sole signal.
     */
    val zones: List<Color>,
)

private val LightAccents = AppAccents(
    swim = Color(0xFF2A78D6),
    bike = Color(0xFFEB6834),
    run = Color(0xFF1BAF7A),
    neutral = Color(0xFF7C8794),
    track = Color(0xFFDDE1E7),
    success = Color(0xFF1B7F4B),
    warning = Color(0xFFB26A00),
    danger = Color(0xFFC0362C),
    phaseBase = Color(0xFFF6DECF),
    phaseBuild = Color(0xFFF3B592),
    phasePeak = Color(0xFFEB6834),
    phaseTaper = Color(0xFFAFC6E6),
    phaseRaceWeek = Color(0xFFC7431C),
    zones = listOf(
        Color(0xFF8A94A0), // Z1 recovery
        Color(0xFF2A78D6), // Z2 aerobic
        Color(0xFF1BAF7A), // Z3 tempo
        Color(0xFFDE9526), // Z4 threshold
        Color(0xFFD9422E), // Z5 VO2max
        Color(0xFFB0399F), // Z6 anaerobic
        Color(0xFF5B2E9E), // Z7 neuromuscular
    ),
)

private val DarkAccents = AppAccents(
    swim = Color(0xFF3987E5),
    bike = Color(0xFFD95926),
    run = Color(0xFF199E70),
    neutral = Color(0xFF808B99),
    track = Color(0xFF2C343E),
    success = Color(0xFF2E9E63),
    warning = Color(0xFFD69324),
    danger = Color(0xFFE3675C),
    phaseBase = Color(0xFF46332A),
    phaseBuild = Color(0xFF87492C),
    phasePeak = Color(0xFFD95926),
    phaseTaper = Color(0xFF3B5474),
    phaseRaceWeek = Color(0xFFFF8B5E),
    zones = listOf(
        Color(0xFF5F6A77), // Z1 recovery
        Color(0xFF3987E5), // Z2 aerobic
        Color(0xFF199E70), // Z3 tempo
        Color(0xFFD4A017), // Z4 threshold
        Color(0xFFE45744), // Z5 VO2max
        Color(0xFFD473C4), // Z6 anaerobic
        Color(0xFF6E5AD8), // Z7 neuromuscular
    ),
)

internal fun accentsFor(darkTheme: Boolean): AppAccents = if (darkTheme) DarkAccents else LightAccents

internal val LocalAppAccents = staticCompositionLocalOf { LightAccents }

/** Semantic colours for the active theme. Prefer this over hard-coding any [Color] in a screen. */
val MaterialTheme.accents: AppAccents
    @Composable @ReadOnlyComposable get() = LocalAppAccents.current
