package com.kitchen.clock.domain

import org.junit.Assert.assertEquals
import org.junit.Test

// Открытая крышка включает внутренний, закрытая гасит его переносом.
class DisplayModeTest {
    @Test
    fun lidOpenUsesBothDisplays() {
        assertEquals(
            DisplayMode.DualScreen,
            displayMode(lidOpen = true, transferPossible = true),
        )
        assertEquals(
            DisplayMode.DualScreen,
            displayMode(lidOpen = true, transferPossible = false),
        )
    }

    @Test
    fun lidClosedBlanksTheInnerDisplay() {
        assertEquals(
            DisplayMode.RearTransfer,
            displayMode(lidOpen = false, transferPossible = true),
        )
        assertEquals(
            DisplayMode.SingleScreen,
            displayMode(lidOpen = false, transferPossible = false),
        )
    }

    @Test
    fun nightLightFollowsTheInnerDisplay() {
        assertEquals(
            false,
            activityShowsNightLight(
                onInnerDisplay = false,
                mode = DisplayMode.DualScreen,
                nightLightOn = true,
            ),
        )
        assertEquals(
            false,
            activityShowsNightLight(
                onInnerDisplay = true,
                mode = DisplayMode.RearTransfer,
                nightLightOn = true,
            ),
        )
        assertEquals(
            false,
            activityShowsNightLight(
                onInnerDisplay = true,
                mode = DisplayMode.DualScreen,
                nightLightOn = false,
            ),
        )
        assertEquals(
            true,
            activityShowsNightLight(
                onInnerDisplay = true,
                mode = DisplayMode.DualScreen,
                nightLightOn = true,
            ),
        )
        assertEquals(
            true,
            activityShowsNightLight(
                onInnerDisplay = true,
                mode = DisplayMode.SingleScreen,
                nightLightOn = true,
            ),
        )
    }
}
