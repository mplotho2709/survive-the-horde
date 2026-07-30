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
)

internal fun accentsFor(darkTheme: Boolean): AppAccents = if (darkTheme) DarkAccents else LightAccents

internal val LocalAppAccents = staticCompositionLocalOf { LightAccents }

/** Semantic colours for the active theme. Prefer this over hard-coding any [Color] in a screen. */
val MaterialTheme.accents: AppAccents
    @Composable @ReadOnlyComposable get() = LocalAppAccents.current
