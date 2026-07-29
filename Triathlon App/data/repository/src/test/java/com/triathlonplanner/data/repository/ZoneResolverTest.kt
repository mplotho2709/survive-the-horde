package com.triathlonplanner.data.repository

import com.google.common.truth.Truth.assertThat
import com.triathlonplanner.core.model.Discipline
import com.triathlonplanner.core.model.IntensityZone
import com.triathlonplanner.core.model.UserZoneProfile
import org.junit.Test

class ZoneResolverTest {

    private val zone = IntensityZone(3)

    @Test
    fun `bike falls back to heart rate when FTP is missing`() {
        val profile = UserZoneProfile(maxHr = 185, restingHr = 55)

        val resolved = ZoneResolver.resolve(Discipline.BIKE, zone, profile)

        assertThat(resolved?.kind).isEqualTo(ZoneKind.HEART_RATE)
    }

    @Test
    fun `bike uses power zones when FTP is set`() {
        val profile = UserZoneProfile(maxHr = 185, restingHr = 55, ftpWatts = 220)

        val resolved = ZoneResolver.resolve(Discipline.BIKE, zone, profile)

        assertThat(resolved?.kind).isEqualTo(ZoneKind.POWER)
    }

    @Test
    fun `swim falls back to heart rate when CSS is missing`() {
        val profile = UserZoneProfile(maxHr = 185, restingHr = 55)

        val resolved = ZoneResolver.resolve(Discipline.SWIM, zone, profile)

        assertThat(resolved?.kind).isEqualTo(ZoneKind.HEART_RATE)
    }

    @Test
    fun `swim uses pace zones when CSS is set`() {
        val profile = UserZoneProfile(maxHr = 185, restingHr = 55, cssPaceSecPer100m = 90)

        val resolved = ZoneResolver.resolve(Discipline.SWIM, zone, profile)

        assertThat(resolved?.kind).isEqualTo(ZoneKind.PACE)
    }

    @Test
    fun `brick bike and brick run legs fall back the same way as their base disciplines`() {
        val profile = UserZoneProfile(maxHr = 185, restingHr = 55)

        assertThat(ZoneResolver.resolve(Discipline.BRICK_BIKE, zone, profile)?.kind).isEqualTo(ZoneKind.HEART_RATE)
        assertThat(ZoneResolver.resolve(Discipline.BRICK_RUN, zone, profile)?.kind).isEqualTo(ZoneKind.HEART_RATE)
    }
}
