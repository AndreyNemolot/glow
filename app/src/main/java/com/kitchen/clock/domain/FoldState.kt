package com.kitchen.clock.domain

// Угол шарнира и источник; отсутствие складки кодируется как 0°.
data class FoldState(
    val angleDegrees: Float?,
    val isOpenedEnough: Boolean,
    val source: FoldSource,
)

enum class FoldSource {
    SENSOR,
    WINDOW_MANAGER,
}

enum class DisplayMode {
    DualScreen,
    RearTransfer,
    SingleScreen,
}

// Тот же угол, что в отладке: открытая крышка включает внутренний, закрытая гасит переносом.
fun displayMode(lidOpen: Boolean, transferPossible: Boolean): DisplayMode =
    if (lidOpen) DisplayMode.DualScreen
    else if (transferPossible) DisplayMode.RearTransfer
    else DisplayMode.SingleScreen

// Ночник только после порога угла и только на внутреннем окне.
fun activityShowsNightLight(
    onInnerDisplay: Boolean,
    mode: DisplayMode,
    nightLightOn: Boolean,
): Boolean = nightLightOn && onInnerDisplay && mode != DisplayMode.RearTransfer
