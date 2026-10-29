package com.kitchen.clock.domain

// Гистерезис от полного хода 180°: включение с 20%, выключение при 15% и ниже.
class UpdateNightLight {
    fun update(previous: Boolean, angle: Float?): Boolean = when {
        angle == null -> previous
        angle >= OPEN_THRESHOLD_DEG -> true
        angle <= CLOSE_THRESHOLD_DEG -> false
        else -> previous
    }

    private companion object {
        const val FULL_OPEN_DEG = 180f
        const val OPEN_THRESHOLD_DEG = FULL_OPEN_DEG * 0.20f
        const val CLOSE_THRESHOLD_DEG = FULL_OPEN_DEG * 0.15f
    }
}
