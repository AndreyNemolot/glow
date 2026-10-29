package com.kitchen.clock.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import com.kitchen.clock.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlinx.coroutines.isActive

private const val SUCK_DURATION_NANOS = 1_000_000_000L
private const val RAY_COUNT = 18
private const val TAU = (PI * 2.0).toFloat()

// Доля кадра foreground, где лежит светящийся циферблат, без чёрных полей.
private const val ICON_CROP_LEFT = 0.16f
private const val ICON_CROP_TOP = 0.14f
private const val ICON_CROP_RIGHT = 0.84f
private const val ICON_CROP_BOTTOM = 0.82f

private val GlowHot = Color(0xFFFFF6D2)
private val GlowBright = Color(0xFFF7D258)
private val GlowMid = Color(0xFFFFB42A)
private val GlowDeep = Color(0xFFE08A10)

// Золотое поле за секунду втягивается в иконку по центру.
@Composable
fun StartupGlow(
    modifier: Modifier = Modifier,
    onFinished: () -> Unit,
) {
    val progress = remember { mutableFloatStateOf(0f) }
    val icon = ImageBitmap.imageResource(R.mipmap.ic_launcher_foreground)
    val rayPaths = remember { Array(RAY_COUNT) { Path() } }
    LaunchedEffect(Unit) {
        var startNanos = -1L
        var linear = 0f
        while (isActive && linear < 1f) {
            linear = withFrameNanos { frameNanos ->
                if (startNanos < 0L) {
                    startNanos = frameNanos
                }
                val elapsed = ((frameNanos - startNanos).toFloat() / SUCK_DURATION_NANOS).coerceIn(0f, 1f)
                progress.floatValue = elapsed
                elapsed
            }
        }
        if (isActive) {
            onFinished()
        }
    }
    Box(
        modifier = modifier
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        event.changes.forEach { change -> change.consume() }
                    }
                }
            }
            .drawWithCache {
                onDrawBehind {
                    val linear = progress.floatValue
                    if (size.width <= 0f || size.height <= 0f) {
                        return@onDrawBehind
                    }
                    drawStartupGlow(
                        eased = suckEase(linear),
                        alpha = veilAlpha(suckEase(linear)),
                        icon = icon,
                        rayPaths = rayPaths,
                    )
                }
            },
    )
}

// Разгон t^1.5: экран сначала сплошь золотой, к концу секунды свет ускоряется в иконку.
private fun suckEase(linear: Float): Float {
    val t = linear.coerceIn(0f, 1f)
    return t * sqrt(t)
}

// Хвост схождения гасит слой, чтобы под ним открылся циферблат.
private fun veilAlpha(eased: Float): Float {
    val fadeStart = 0.78f
    if (eased <= fadeStart) {
        return 1f
    }
    return ((1f - eased) / (1f - fadeStart)).coerceIn(0f, 1f)
}

private fun DrawScope.drawStartupGlow(
    eased: Float,
    alpha: Float,
    icon: ImageBitmap,
    rayPaths: Array<Path>,
) {
    if (alpha <= 0f) {
        return
    }
    val center = Offset(size.width / 2f, size.height / 2f)
    val corner = hypot(size.width, size.height) * 0.5f
    val iconSide = (min(size.width, size.height) * 0.42f)
        .coerceIn(120.dp.toPx(), 220.dp.toPx())
    val iconRadius = iconSide * 0.5f
    val radius = lerp(corner * 1.22f, iconRadius * 0.42f, eased)
    drawCircle(
        brush = Brush.radialGradient(
            0.00f to GlowHot.copy(alpha = alpha),
            0.50f to GlowBright.copy(alpha = alpha),
            0.78f to GlowMid.copy(alpha = alpha),
            0.88f to GlowDeep.copy(alpha = alpha),
            1.00f to Color.Transparent,
            center = center,
            radius = radius,
        ),
        radius = radius,
        center = center,
    )
    drawInwardRays(
        center = center,
        outer = radius,
        inner = iconRadius * 0.62f,
        alpha = alpha,
        eased = eased,
        rayPaths = rayPaths,
    )
    drawCollapsingShell(
        center = center,
        eased = eased,
        corner = corner,
        iconRadius = iconRadius,
        alpha = alpha,
    )
    drawIcon(
        icon = icon,
        center = center,
        iconRadius = iconRadius,
        iconSide = iconSide,
        alpha = alpha,
    )
}

private fun DrawScope.drawInwardRays(
    center: Offset,
    outer: Float,
    inner: Float,
    alpha: Float,
    eased: Float,
    rayPaths: Array<Path>,
) {
    if (outer <= inner + 1f) {
        return
    }
    val halfWidth = lerp(18.dp.toPx(), 2.dp.toPx(), eased)
    val twist = eased * 0.45f
    for (index in 0 until RAY_COUNT) {
        val angle = TAU * index / RAY_COUNT + twist
        val reach = outer * (0.86f + 0.14f * sin(index * 1.7f))
        val path = rayPaths[index]
        path.rewind()
        funnel(
            path = path,
            center = center,
            angle = angle,
            inner = inner,
            outer = reach,
            halfWidth = halfWidth,
        )
        drawPath(
            path = path,
            color = GlowHot.copy(alpha = alpha * 0.20f),
            blendMode = BlendMode.Plus,
        )
    }
}

private fun funnel(
    path: Path,
    center: Offset,
    angle: Float,
    inner: Float,
    outer: Float,
    halfWidth: Float,
) {
    val cosA = cos(angle)
    val sinA = sin(angle)
    val nx = -sinA
    val ny = cosA
    path.moveTo(center.x + cosA * outer + nx * halfWidth, center.y + sinA * outer + ny * halfWidth)
    path.lineTo(center.x + cosA * outer - nx * halfWidth, center.y + sinA * outer - ny * halfWidth)
    path.lineTo(center.x + cosA * inner, center.y + sinA * inner)
    path.close()
}

private fun DrawScope.drawCollapsingShell(
    center: Offset,
    eased: Float,
    corner: Float,
    iconRadius: Float,
    alpha: Float,
) {
    val shellRadius = lerp(corner * 0.94f, iconRadius * 0.7f, eased)
    drawCircle(
        color = GlowHot.copy(alpha = alpha * 0.34f),
        radius = shellRadius,
        center = center,
        style = Stroke(width = lerp(34.dp.toPx(), 2.dp.toPx(), eased)),
        blendMode = BlendMode.Plus,
    )
}

private fun DrawScope.drawIcon(
    icon: ImageBitmap,
    center: Offset,
    iconRadius: Float,
    iconSide: Float,
    alpha: Float,
) {
    if (icon.width <= 0 || icon.height <= 0) {
        return
    }
    // Чёрный диск иконки читается на золоте; Screen оставляет только светящийся циферблат.
    val disc = iconRadius * 1.02f
    drawCircle(
        brush = Brush.radialGradient(
            0.00f to Color.Black.copy(alpha = alpha),
            0.78f to Color.Black.copy(alpha = alpha),
            1.00f to Color.Transparent,
            center = center,
            radius = disc,
        ),
        radius = disc,
        center = center,
    )
    val side = iconSide.roundToInt().coerceAtLeast(1)
    val srcLeft = (icon.width * ICON_CROP_LEFT).roundToInt().coerceIn(0, icon.width - 1)
    val srcTop = (icon.height * ICON_CROP_TOP).roundToInt().coerceIn(0, icon.height - 1)
    val srcRight = (icon.width * ICON_CROP_RIGHT).roundToInt().coerceIn(srcLeft + 1, icon.width)
    val srcBottom = (icon.height * ICON_CROP_BOTTOM).roundToInt().coerceIn(srcTop + 1, icon.height)
    drawImage(
        image = icon,
        srcOffset = IntOffset(srcLeft, srcTop),
        srcSize = IntSize(srcRight - srcLeft, srcBottom - srcTop),
        dstOffset = IntOffset(
            (center.x - side / 2f).roundToInt(),
            (center.y - side / 2f).roundToInt(),
        ),
        dstSize = IntSize(side, side),
        alpha = alpha,
        blendMode = BlendMode.Screen,
        filterQuality = FilterQuality.High,
    )
}
