package com.kitchen.clock.ui

import androidx.compose.runtime.Immutable
import com.kitchen.clock.domain.DayCell
import com.kitchen.clock.domain.DisplayMode

// Неизменяемое состояние экрана, чтобы Compose мог пропускать рекомпозицию.
@Immutable
data class FoldClockUiState(
    val timeLabel: String,
    val dateLine: String,
    val weekdayLabels: List<String>,
    val dayCells: List<DayCell>,
    val nightLightOn: Boolean,
    val onInnerDisplay: Boolean,
    val burnInXDp: Int,
    val burnInYDp: Int,
    val displayMode: DisplayMode,
    val monthTitle: String,
    val externalBrightness: Float,
    val innerHueDegrees: Float,
)
