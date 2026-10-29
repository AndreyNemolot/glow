package com.kitchen.clock.domain

import org.junit.Assert.assertEquals
import org.junit.Test

// Границы гистерезиса: 20% включает, 15% и ниже выключает, null и мёртвая зона хранят прошлое.
class UpdateNightLightTest {
    private val updateNightLight = UpdateNightLight()

    private companion object {
        const val OPEN_DEG = 180f * 0.20f
        const val CLOSE_DEG = 180f * 0.15f
    }

    @Test
    fun sampleTrioAndDeadbandBoundaries() {
        assertNightLight(previous = false, angle = OPEN_DEG, expected = true)
        assertNightLight(previous = true, angle = 30f, expected = true)
        assertNightLight(previous = true, angle = CLOSE_DEG, expected = false)
        assertNightLight(previous = false, angle = OPEN_DEG - 0.1f, expected = false)
        assertNightLight(previous = true, angle = OPEN_DEG - 0.1f, expected = true)
        assertNightLight(previous = true, angle = CLOSE_DEG + 0.1f, expected = true)
        assertNightLight(previous = false, angle = CLOSE_DEG + 0.1f, expected = false)
        assertNightLight(previous = false, angle = CLOSE_DEG, expected = false)
        assertNightLight(previous = true, angle = null, expected = true)
        assertNightLight(previous = false, angle = null, expected = false)
    }

    private fun assertNightLight(previous: Boolean, angle: Float?, expected: Boolean) {
        assertEquals(
            "previous=$previous angle=$angle",
            expected,
            updateNightLight.update(previous, angle),
        )
    }
}
