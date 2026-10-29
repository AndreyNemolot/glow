package com.kitchen.clock.domain

import java.time.LocalDate
import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

// Шкала яркости и оттенка, подписи календаря внешнего экрана.
class DisplayLookTest {
    @Test
    fun dragUpRaisesBrightnessAndHueUntilTheEnds() {
        val travel = 100f
        val brighter = DisplayLook.brightnessAfterDrag(
            current = 0.5f,
            deltaYPx = -50f,
            travelPx = travel,
        )
        val dimmer = DisplayLook.brightnessAfterDrag(
            current = 0.5f,
            deltaYPx = 1_000f,
            travelPx = travel,
        )
        assertEquals(0.5f + 0.475f, brighter, 0.001f)
        assertEquals(DisplayLook.MIN_EXTERNAL_BRIGHTNESS, dimmer, 0.001f)

        val shifted = DisplayLook.hueAfterDrag(current = 20f, deltaYPx = -10f, travelPx = travel)
        val floored = DisplayLook.hueAfterDrag(current = 20f, deltaYPx = 1_000f, travelPx = travel)
        assertEquals(56f, shifted, 0.001f)
        assertEquals(0f, floored, 0.001f)
    }

    @Test
    fun thumbSitsOnTheFinger() {
        val atTop = DisplayLook.scaleTopLeft(
            fingerX = 100f,
            fingerY = 200f,
            fraction = 1f,
            trackWidthPx = 40f,
            trackHeightPx = 200f,
            thumbPx = 20f,
        )
        assertEquals(80f, atTop.leftPx, 0.001f)
        assertEquals(190f, atTop.topPx, 0.001f)

        val atBottom = DisplayLook.scaleTopLeft(
            fingerX = 100f,
            fingerY = 200f,
            fraction = 0f,
            trackWidthPx = 40f,
            trackHeightPx = 200f,
            thumbPx = 20f,
        )
        assertEquals(10f, atBottom.topPx, 0.001f)
    }

    @Test
    fun dragRightRaisesBrightnessAndHueUntilTheEnds() {
        val travel = 100f
        val brighter = DisplayLook.brightnessAfterHorizontalDrag(
            current = 0.5f,
            deltaXPx = 50f,
            travelPx = travel,
        )
        val dimmer = DisplayLook.brightnessAfterHorizontalDrag(
            current = 0.5f,
            deltaXPx = -1_000f,
            travelPx = travel,
        )
        assertEquals(0.5f + 0.475f, brighter, 0.001f)
        assertEquals(DisplayLook.MIN_EXTERNAL_BRIGHTNESS, dimmer, 0.001f)

        val shifted = DisplayLook.hueAfterHorizontalDrag(current = 20f, deltaXPx = 10f, travelPx = travel)
        val floored = DisplayLook.hueAfterHorizontalDrag(current = 20f, deltaXPx = -1_000f, travelPx = travel)
        assertEquals(56f, shifted, 0.001f)
        assertEquals(0f, floored, 0.001f)
    }

    @Test
    fun horizontalThumbSitsOnTheFinger() {
        val atRight = DisplayLook.horizontalScaleTopLeft(
            fingerX = 100f,
            fingerY = 200f,
            fraction = 1f,
            trackWidthPx = 200f,
            trackHeightPx = 40f,
            thumbPx = 20f,
        )
        assertEquals(-90f, atRight.leftPx, 0.001f)
        assertEquals(180f, atRight.topPx, 0.001f)

        val atLeft = DisplayLook.horizontalScaleTopLeft(
            fingerX = 100f,
            fingerY = 200f,
            fraction = 0f,
            trackWidthPx = 200f,
            trackHeightPx = 40f,
            thumbPx = 20f,
        )
        assertEquals(90f, atLeft.leftPx, 0.001f)
    }

    @Test
    fun defaultHueMatchesTheOrangeLamp() {
        val palette = DisplayLook.nightPalette(DisplayLook.DEFAULT_INNER_HUE)
        assertColor(0xFFFF8C00.toInt(), palette.backgroundArgb)
        assertColor(0xFFC45A00.toInt(), palette.deepArgb)
        assertColor(0xFFFFC56A.toInt(), palette.brightArgb)
    }

    @Test
    fun greenHueShiftsTheLamp() {
        val palette = DisplayLook.nightPalette(120f)
        assertColor(0xFF00FF00.toInt(), palette.backgroundArgb)
    }

    @Test
    fun russianCoverReadsLikeTheReference() {
        val date = LocalDate.of(2026, 10, 28)
        val locale = Locale.forLanguageTag("ru-RU")
        assertEquals("Среда, 28 октября", ClockFace.dateLine(date, locale))
        assertEquals("Октябрь 2026", ClockFace.monthTitle(date, locale))
        assertEquals(
            listOf("ПН", "ВТ", "СР", "ЧТ", "ПТ", "СБ", "ВС"),
            ClockFace.weekdayLabels(locale),
        )
        val cells = ClockFace.monthGrid(date, locale)
        assertEquals(null, cells[0].day)
        assertEquals(1, cells[3].day)
        assertTrue(cells[30].isToday)
        assertEquals(28, cells[30].day)
    }

    private fun assertColor(expected: Int, actual: Int) {
        assertChannel((expected shr 16) and 0xFF, (actual shr 16) and 0xFF)
        assertChannel((expected shr 8) and 0xFF, (actual shr 8) and 0xFF)
        assertChannel(expected and 0xFF, actual and 0xFF)
    }

    private fun assertChannel(expected: Int, actual: Int) {
        assertTrue("expected $expected but was $actual", kotlin.math.abs(expected - actual) <= 1)
    }
}
