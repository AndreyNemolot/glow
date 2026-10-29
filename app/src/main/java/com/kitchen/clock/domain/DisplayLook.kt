package com.kitchen.clock.domain

import kotlin.math.abs
import kotlin.math.roundToInt

// Яркость внешнего экрана и оттенок ночника, без android.*.
object DisplayLook {
    const val MIN_EXTERNAL_BRIGHTNESS = 0.05f
    const val MAX_EXTERNAL_BRIGHTNESS = 1f
    const val DEFAULT_EXTERNAL_BRIGHTNESS = 0.7f
    const val DEFAULT_INNER_HUE = 60f * 140f / 255f

    private const val DEEP_HUE = 60f * 90f / 196f
    private const val BRIGHT_HUE = 60f * 91f / 149f
    private const val DEEP_VALUE = 196f / 255f
    private const val BRIGHT_SATURATION = 149f / 255f

    fun coerceBrightness(value: Float): Float {
        if (value.isNaN()) {
            return DEFAULT_EXTERNAL_BRIGHTNESS
        }
        return value.coerceIn(MIN_EXTERNAL_BRIGHTNESS, MAX_EXTERNAL_BRIGHTNESS)
    }

    fun coerceHue(value: Float): Float {
        if (value.isNaN()) {
            return DEFAULT_INNER_HUE
        }
        return value.coerceIn(0f, 360f)
    }

    fun brightnessFraction(brightness: Float): Float {
        val span = MAX_EXTERNAL_BRIGHTNESS - MIN_EXTERNAL_BRIGHTNESS
        return (coerceBrightness(brightness) - MIN_EXTERNAL_BRIGHTNESS) / span
    }

    fun hueFraction(hue: Float): Float = coerceHue(hue) / 360f

    // Палец вверх увеличивает значение: ноль шкалы внизу.
    fun brightnessAfterDrag(current: Float, deltaYPx: Float, travelPx: Float): Float {
        if (travelPx <= 0f) {
            return coerceBrightness(current)
        }
        val span = MAX_EXTERNAL_BRIGHTNESS - MIN_EXTERNAL_BRIGHTNESS
        return coerceBrightness(current - deltaYPx / travelPx * span)
    }

    fun hueAfterDrag(current: Float, deltaYPx: Float, travelPx: Float): Float {
        if (travelPx <= 0f) {
            return coerceHue(current)
        }
        return coerceHue(current - deltaYPx / travelPx * 360f)
    }

    // Палец вправо увеличивает значение: ноль шкалы слева.
    fun brightnessAfterHorizontalDrag(current: Float, deltaXPx: Float, travelPx: Float): Float {
        return brightnessAfterDrag(current, deltaYPx = -deltaXPx, travelPx = travelPx)
    }

    fun hueAfterHorizontalDrag(current: Float, deltaXPx: Float, travelPx: Float): Float {
        return hueAfterDrag(current, deltaYPx = -deltaXPx, travelPx = travelPx)
    }

    // Ползунок стоит так, чтобы центр бегунка совпал с пальцем.
    fun scaleTopLeft(
        fingerX: Float,
        fingerY: Float,
        fraction: Float,
        trackWidthPx: Float,
        trackHeightPx: Float,
        thumbPx: Float,
    ): ScalePlacement {
        val travel = (trackHeightPx - thumbPx).coerceAtLeast(0f)
        val thumbCenterFromTop = thumbPx / 2f + (1f - fraction.coerceIn(0f, 1f)) * travel
        return ScalePlacement(
            leftPx = fingerX - trackWidthPx / 2f,
            topPx = fingerY - thumbCenterFromTop,
        )
    }

    // Горизонтальный ползунок: бегунок на пальце, ноль шкалы слева.
    fun horizontalScaleTopLeft(
        fingerX: Float,
        fingerY: Float,
        fraction: Float,
        trackWidthPx: Float,
        trackHeightPx: Float,
        thumbPx: Float,
    ): ScalePlacement {
        val travel = (trackWidthPx - thumbPx).coerceAtLeast(0f)
        val thumbCenterFromLeft = thumbPx / 2f + fraction.coerceIn(0f, 1f) * travel
        return ScalePlacement(
            leftPx = fingerX - thumbCenterFromLeft,
            topPx = fingerY - trackHeightPx / 2f,
        )
    }

    // Сдвиг оттенка сохраняет соотношение оранжевого ночника #FF8C00 / #C45A00 / #FFC56A.
    fun nightPalette(hueDegrees: Float): NightPalette {
        val hue = coerceHue(hueDegrees)
        val shift = hue - DEFAULT_INNER_HUE
        return NightPalette(
            backgroundArgb = colorArgb(hue, saturation = 1f, value = 1f),
            deepArgb = colorArgb(DEEP_HUE + shift, saturation = 1f, value = DEEP_VALUE),
            brightArgb = colorArgb(BRIGHT_HUE + shift, saturation = BRIGHT_SATURATION, value = 1f),
        )
    }

    fun colorArgb(hue: Float, saturation: Float, value: Float): Int {
        val safeHue = ((hue % 360f) + 360f) % 360f
        val chroma = value * saturation
        val x = chroma * (1f - abs((safeHue / 60f) % 2f - 1f))
        val match = value - chroma
        val (red, green, blue) = when {
            safeHue < 60f -> Triple(chroma, x, 0f)
            safeHue < 120f -> Triple(x, chroma, 0f)
            safeHue < 180f -> Triple(0f, chroma, x)
            safeHue < 240f -> Triple(0f, x, chroma)
            safeHue < 300f -> Triple(x, 0f, chroma)
            else -> Triple(chroma, 0f, x)
        }
        return packArgb(red + match, green + match, blue + match)
    }

    private fun packArgb(red: Float, green: Float, blue: Float): Int {
        val r = (red * 255f).roundToInt().coerceIn(0, 255)
        val g = (green * 255f).roundToInt().coerceIn(0, 255)
        val b = (blue * 255f).roundToInt().coerceIn(0, 255)
        return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }
}

data class NightPalette(
    val backgroundArgb: Int,
    val deepArgb: Int,
    val brightArgb: Int,
)

data class ScalePlacement(
    val leftPx: Float,
    val topPx: Float,
)
