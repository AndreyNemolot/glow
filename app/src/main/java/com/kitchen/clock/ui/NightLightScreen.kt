package com.kitchen.clock.ui

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.isActive

private const val NANOS_PER_SECOND = 1_000_000_000f
private const val RIBBON_COUNT = 4
private const val RIBBON_STEPS = 32
private val RibbonSpeeds = floatArrayOf(0.628f, 0.47f, 0.71f, 0.55f)
private const val TAU = (PI * 2.0).toFloat()

private const val WATER_SHADER = """
    uniform float2 resolution;
    uniform float time;
    uniform float3 deepColor;
    uniform float3 brightColor;

    half4 main(float2 fragCoord) {
        float2 uv = fragCoord / resolution;
        float t = time;
        float w1 = sin(uv.x * 21.9911 + uv.y * 2.4 + t * 0.628);
        float w2 = sin(uv.y * 18.8496 - uv.x * 2.1 + t * 0.47);
        float w3 = sin((uv.x + uv.y) * 7.54 + t * 0.71);
        float w = (w1 + w2 + w3) / 3.0;
        float crest = clamp(pow(max(w, 0.0), 1.6), 0.0, 1.0);
        half4 deep = half4(deepColor.x, deepColor.y, deepColor.z, 1.0);
        half4 bright = half4(brightColor.x, brightColor.y, brightColor.z, 1.0);
        return mix(deep, bright, half(crest));
    }
"""

// Волны ночника красятся снаружи, время кадра читается только при рисовании.
@Composable
fun NightLightScreen(
    deep: Color,
    bright: Color,
    modifier: Modifier = Modifier,
) {
    val timeNanos = remember { mutableLongStateOf(0L) }
    // Фаза от первого кадра, чтобы синус не терял точность на большом nanoTime.
    LaunchedEffect(Unit) {
        var startNanos = -1L
        while (isActive) {
            withFrameNanos { frameNanos ->
                if (startNanos < 0L) {
                    startNanos = frameNanos
                }
                timeNanos.longValue = frameNanos - startNanos
            }
        }
    }
    val waves = modifier.fillMaxSize()
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        ShaderWaves(modifier = waves, timeNanos = timeNanos, deep = deep, bright = bright)
    } else {
        RibbonWaves(modifier = waves, timeNanos = timeNanos, deep = deep, bright = bright)
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun ShaderWaves(
    modifier: Modifier,
    timeNanos: MutableLongState,
    deep: Color,
    bright: Color,
) {
    val shader = remember { RuntimeShader(WATER_SHADER) }
    val brush = remember(shader) { ShaderBrush(shader) }
    Box(
        modifier = modifier.drawWithCache {
            shader.setFloatUniform("deepColor", deep.red, deep.green, deep.blue)
            shader.setFloatUniform("brightColor", bright.red, bright.green, bright.blue)
            if (size.width > 0f && size.height > 0f) {
                shader.setFloatUniform("resolution", size.width, size.height)
            }
            onDrawBehind {
                if (size.width <= 0f || size.height <= 0f) {
                    drawRect(deep)
                } else {
                    shader.setFloatUniform("time", timeNanos.longValue / NANOS_PER_SECOND)
                    drawRect(brush)
                }
            }
        },
    )
}

@Composable
private fun RibbonWaves(
    modifier: Modifier,
    timeNanos: MutableLongState,
    deep: Color,
    bright: Color,
) {
    val paths = remember { Array(RIBBON_COUNT) { Path() } }
    Box(
        modifier = modifier.drawWithCache {
            onDrawBehind {
                drawRect(deep)
                val timeSec = timeNanos.longValue / NANOS_PER_SECOND
                for (index in 0 until RIBBON_COUNT) {
                    val path = paths[index]
                    path.rewind()
                    ribbon(path, index, timeSec)
                    drawPath(path, bright.copy(alpha = 0.6f))
                }
            }
        },
    )
}

private fun DrawScope.ribbon(path: Path, index: Int, timeSec: Float) {
    val widthPx = size.width
    val heightPx = size.height
    val phase = timeSec * RibbonSpeeds[index]
    val amplitude = heightPx * 0.07f
    val halfThickness = heightPx * 0.07f
    val centerY = heightPx * (0.18f + 0.22f * index)
    val cycles = 3.5f * TAU
    val steps = RIBBON_STEPS
    path.moveTo(0f, centerY + sin(phase) * amplitude - halfThickness)
    var step = 1
    while (step <= steps) {
        val x = widthPx * step / steps
        val along = (step.toFloat() / steps) * cycles + phase
        path.lineTo(x, centerY + sin(along) * amplitude - halfThickness)
        step++
    }
    step = steps
    while (step >= 0) {
        val x = widthPx * step / steps
        val along = (step.toFloat() / steps) * cycles + phase
        path.lineTo(x, centerY + sin(along) * amplitude + halfThickness)
        step--
    }
    path.close()
}
