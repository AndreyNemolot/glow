package com.kitchen.clock

import android.content.pm.ActivityInfo
import android.view.Window
import android.view.WindowManager
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat

// Альбомный полноэкранный режим без системных рамок и выреза.
internal fun Window.applyClockChrome() {
    addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    WindowCompat.setDecorFitsSystemWindows(this, false)
    val params = attributes
    var changed = false
    if (params.layoutInDisplayCutoutMode !=
        WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
    ) {
        params.layoutInDisplayCutoutMode =
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        changed = true
    }
    if (changed) {
        attributes = params
    }
    val controller = WindowCompat.getInsetsController(this, decorView)
    controller.hide(WindowInsetsCompat.Type.systemBars())
    controller.systemBarsBehavior =
        WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
}

// Оба экрана держат один reverseLandscape, без поворота на 180°.
internal fun Window.lockReverseLandscape() {
    val params = attributes
    if (params.screenOrientation == ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE) {
        return
    }
    params.screenOrientation = ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
    attributes = params
}
