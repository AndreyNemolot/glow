package com.kitchen.clock.ui

import android.hardware.display.DisplayManager
import android.view.Surface
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import com.kitchen.clock.domain.coverContentRotationDegrees
import com.kitchen.clock.domain.coverContentSwapsAxes

// Часы на крышке остаются в reverseLandscape, пока дисплей крутит гироскоп.
@Composable
fun FixedCoverOrientation(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val rotation = coverDisplayRotation()
    val degrees = coverContentRotationDegrees(rotation)
    val swap = coverContentSwapsAxes(rotation)
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .requiredSize(
                    width = if (swap) maxHeight else maxWidth,
                    height = if (swap) maxWidth else maxHeight,
                )
                .graphicsLayer { rotationZ = degrees },
        ) {
            content()
        }
    }
}

@Composable
private fun coverDisplayRotation(): Int {
    val view = LocalView.current
    var rotation by remember(view) { mutableIntStateOf(view.display?.rotation ?: Surface.ROTATION_0) }
    DisposableEffect(view) {
        val publish = {
            val next = view.display?.rotation
            if (next != null) {
                rotation = next
            }
        }
        publish()
        val attach = object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) = publish()

            override fun onViewDetachedFromWindow(v: View) = Unit
        }
        view.addOnAttachStateChangeListener(attach)
        val manager = view.context.applicationContext.getSystemService(DisplayManager::class.java)
        val listener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) = Unit

            override fun onDisplayRemoved(displayId: Int) = Unit

            override fun onDisplayChanged(displayId: Int) {
                val currentId = view.display?.displayId ?: return
                if (displayId == currentId) {
                    publish()
                }
            }
        }
        manager.registerDisplayListener(listener, view.handler)
        onDispose {
            view.removeOnAttachStateChangeListener(attach)
            manager.unregisterDisplayListener(listener)
        }
    }
    return rotation
}
