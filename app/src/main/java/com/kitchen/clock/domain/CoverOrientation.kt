package com.kitchen.clock.domain

// Компенсация сенсора заднего экрана к reverseLandscape, четверть 3.
const val REVERSE_LANDSCAPE_QUARTER = 3

fun coverContentRotationDegrees(currentQuarter: Int): Float = -coverQuarterDelta(currentQuarter) * 90f

fun coverContentSwapsAxes(currentQuarter: Int): Boolean = coverQuarterDelta(currentQuarter) % 2 == 1

private fun coverQuarterDelta(currentQuarter: Int): Int {
    val current = currentQuarter.mod(4)
    return (REVERSE_LANDSCAPE_QUARTER - current + 4) % 4
}
