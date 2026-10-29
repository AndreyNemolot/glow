package com.kitchen.clock.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

// Четверть 3 — reverseLandscape; контент доворачивается против сенсора.
class CoverOrientationTest {
    @Test
    fun alreadyReverseLandscapeDoesNotRotate() {
        assertEquals(0f, coverContentRotationDegrees(REVERSE_LANDSCAPE_QUARTER))
        assertFalse(coverContentSwapsAxes(REVERSE_LANDSCAPE_QUARTER))
    }

    @Test
    fun otherQuartersCounterRotateTowardReverseLandscape() {
        assertEquals(-270f, coverContentRotationDegrees(0))
        assertTrue(coverContentSwapsAxes(0))
        assertEquals(-180f, coverContentRotationDegrees(1))
        assertFalse(coverContentSwapsAxes(1))
        assertEquals(-90f, coverContentRotationDegrees(2))
        assertTrue(coverContentSwapsAxes(2))
    }
}
