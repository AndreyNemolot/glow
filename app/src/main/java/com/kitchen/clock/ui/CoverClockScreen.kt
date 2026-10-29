package com.kitchen.clock.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.kitchen.clock.domain.DayCell
import com.kitchen.clock.domain.DisplayLook
import kotlin.math.PI
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

// Широкое окно: часы слева от календаря. Высокое: часы над календарём, шкалы снизу.
@Composable
fun CoverClockScreen(
    state: FoldClockUiState,
    onExternalBrightnessChange: (Float) -> Unit,
    onInnerHueChange: (Float) -> Unit,
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var coverCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var openScale by remember { mutableStateOf<OpenScale?>(null) }
    var controlsVisible by remember { mutableStateOf(true) }
    var revealGeneration by remember { mutableIntStateOf(0) }
    val scaleOpen = openScale != null
    val revealControls = rememberUpdatedState {
        controlsVisible = true
        revealGeneration++
    }
    // Показ сбрасывается касанием и не гаснет, пока палец держит шкалу.
    LaunchedEffect(revealGeneration, scaleOpen) {
        if (scaleOpen) {
            return@LaunchedEffect
        }
        delay(CONTROLS_VISIBLE_MS)
        controlsVisible = false
    }
    val controlsAlpha by animateFloatAsState(
        targetValue = if (controlsVisible) 1f else 0f,
        animationSpec = tween(durationMillis = CONTROLS_FADE_MS),
        label = "controlsAlpha",
    )
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(PageColor)
            .onGloballyPositioned { coverCoordinates = it }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(
                        requireUnconsumed = false,
                        pass = PointerEventPass.Initial,
                    )
                    revealControls.value.invoke()
                }
            },
    ) {
        val portrait = maxHeight > maxWidth
        if (portrait) {
            PortraitCover(
                state = state,
                controlsAlpha = controlsAlpha,
                coverCoordinates = coverCoordinates,
                openScale = openScale,
                controlsVisible = controlsVisible,
                onExternalBrightnessChange = onExternalBrightnessChange,
                onInnerHueChange = onInnerHueChange,
                onScaleChange = { openScale = it },
            )
        } else {
        // Альбом: часы слева от календаря, вертикальные шкалы слева.
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 8.dp, end = 36.dp)
                .offset(x = state.burnInXDp.dp, y = state.burnInYDp.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(
                modifier = Modifier.graphicsLayer { alpha = controlsAlpha },
                verticalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                LookIcon(
                    kind = LookKind.Brightness,
                    brightness = state.externalBrightness,
                    hue = state.innerHueDegrees,
                    coverCoordinates = coverCoordinates,
                    active = openScale?.kind == LookKind.Brightness,
                    enabled = controlsVisible,
                    onExternalBrightnessChange = onExternalBrightnessChange,
                    onInnerHueChange = onInnerHueChange,
                    onScaleChange = { openScale = it },
                )
                LookIcon(
                    kind = LookKind.Color,
                    brightness = state.externalBrightness,
                    hue = state.innerHueDegrees,
                    coverCoordinates = coverCoordinates,
                    active = openScale?.kind == LookKind.Color,
                    enabled = controlsVisible,
                    onExternalBrightnessChange = onExternalBrightnessChange,
                    onInnerHueChange = onInnerHueChange,
                    onScaleChange = { openScale = it },
                )
            }
            Column(
                modifier = Modifier.weight(1.15f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = state.timeLabel,
                    style = TimeStyle,
                    maxLines = 1,
                    softWrap = false,
                )
                Text(
                    text = state.dateLine,
                    color = TextColor,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Light,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
            Column(
                modifier = Modifier.weight(0.9f),
                horizontalAlignment = Alignment.Start,
            ) {
                Text(
                    text = state.monthTitle,
                    modifier = Modifier.padding(start = 6.dp, bottom = 12.dp),
                    color = TextColor,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Normal,
                    maxLines = 1,
                )
                WeekdayHeader(labels = state.weekdayLabels)
                MonthDays(cells = state.dayCells)
            }
        }
        }
        ExitAppButton(
            onExit = onExit,
            enabled = controlsVisible,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = state.burnInXDp.dp, y = state.burnInYDp.dp)
                .graphicsLayer { alpha = controlsAlpha },
        )
        val scale = openScale
        if (scale != null) {
            val scaleModifier = Modifier
                .zIndex(1f)
                .offset {
                    IntOffset(scale.leftPx.roundToInt(), scale.topPx.roundToInt())
                }
            if (portrait) {
                HorizontalScale(
                    kind = scale.kind,
                    brightness = state.externalBrightness,
                    hue = state.innerHueDegrees,
                    modifier = scaleModifier.size(TrackHeight, TrackWidth),
                )
            } else {
                VerticalScale(
                    kind = scale.kind,
                    brightness = state.externalBrightness,
                    hue = state.innerHueDegrees,
                    modifier = scaleModifier.size(TrackWidth, TrackHeight),
                )
            }
        }
    }
}

// В портрете часы над календарём, иконки шкал внизу в ряд.
@Composable
private fun PortraitCover(
    state: FoldClockUiState,
    controlsAlpha: Float,
    coverCoordinates: LayoutCoordinates?,
    openScale: OpenScale?,
    controlsVisible: Boolean,
    onExternalBrightnessChange: (Float) -> Unit,
    onInnerHueChange: (Float) -> Unit,
    onScaleChange: (OpenScale?) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 12.dp)
            .offset(x = state.burnInXDp.dp, y = state.burnInYDp.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                val timeSp = (maxWidth.value / 3.4f).coerceIn(48f, 116f)
                Text(
                    text = state.timeLabel,
                    style = TimeStyle.copy(fontSize = timeSp.sp, lineHeight = timeSp.sp),
                    maxLines = 1,
                    softWrap = false,
                )
            }
            Text(
                text = state.dateLine,
                color = TextColor,
                fontSize = 20.sp,
                fontWeight = FontWeight.Light,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 6.dp),
            )
            Column(
                modifier = Modifier
                    .padding(top = 28.dp)
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = state.monthTitle,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    color = TextColor,
                    fontSize = 26.sp,
                    fontWeight = FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                )
                WeekdayHeader(labels = state.weekdayLabels)
                MonthDays(cells = state.dayCells)
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { alpha = controlsAlpha },
            horizontalArrangement = Arrangement.spacedBy(32.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            LookIcon(
                kind = LookKind.Brightness,
                brightness = state.externalBrightness,
                hue = state.innerHueDegrees,
                coverCoordinates = coverCoordinates,
                active = openScale?.kind == LookKind.Brightness,
                enabled = controlsVisible,
                horizontal = true,
                onExternalBrightnessChange = onExternalBrightnessChange,
                onInnerHueChange = onInnerHueChange,
                onScaleChange = onScaleChange,
            )
            LookIcon(
                kind = LookKind.Color,
                brightness = state.externalBrightness,
                hue = state.innerHueDegrees,
                coverCoordinates = coverCoordinates,
                active = openScale?.kind == LookKind.Color,
                enabled = controlsVisible,
                horizontal = true,
                onExternalBrightnessChange = onExternalBrightnessChange,
                onInnerHueChange = onInnerHueChange,
                onScaleChange = onScaleChange,
            )
        }
    }
}

@Composable
private fun LookIcon(
    kind: LookKind,
    brightness: Float,
    hue: Float,
    coverCoordinates: LayoutCoordinates?,
    active: Boolean,
    enabled: Boolean,
    horizontal: Boolean = false,
    onExternalBrightnessChange: (Float) -> Unit,
    onInnerHueChange: (Float) -> Unit,
    onScaleChange: (OpenScale?) -> Unit,
) {
    val coverState = rememberUpdatedState(coverCoordinates)
    val brightnessState = rememberUpdatedState(brightness)
    val hueState = rememberUpdatedState(hue)
    val onBrightnessState = rememberUpdatedState(onExternalBrightnessChange)
    val onHueState = rememberUpdatedState(onInnerHueChange)
    val onScaleState = rememberUpdatedState(onScaleChange)
    var iconCoordinates by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val description = if (kind == LookKind.Brightness) "Яркость" else "Цвет"
    Box(
        modifier = Modifier
            .size(IconTouch)
            .semantics {
                role = Role.Button
                contentDescription = description
            }
            .onGloballyPositioned { iconCoordinates = it }
            .then(if (enabled) Modifier.pointerInput(kind, horizontal) {
                val trackWidthPx = if (horizontal) TrackHeight.toPx() else TrackWidth.toPx()
                val trackHeightPx = if (horizontal) TrackWidth.toPx() else TrackHeight.toPx()
                val thumbPx = ThumbSize.toPx()
                val travelPx = (
                    (if (horizontal) trackWidthPx else trackHeightPx) - thumbPx
                    ).coerceAtLeast(1f)
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val cover = coverState.value
                    val icon = iconCoordinates
                    if (cover == null || icon == null || !cover.isAttached || !icon.isAttached) {
                        return@awaitEachGesture
                    }
                    val finger = cover.localPositionOf(icon, down.position)
                    val fraction = when (kind) {
                        LookKind.Brightness -> DisplayLook.brightnessFraction(brightnessState.value)
                        LookKind.Color -> DisplayLook.hueFraction(hueState.value)
                    }
                    val placement = if (horizontal) {
                        DisplayLook.horizontalScaleTopLeft(
                            fingerX = finger.x,
                            fingerY = finger.y,
                            fraction = fraction,
                            trackWidthPx = trackWidthPx,
                            trackHeightPx = trackHeightPx,
                            thumbPx = thumbPx,
                        )
                    } else {
                        DisplayLook.scaleTopLeft(
                            fingerX = finger.x,
                            fingerY = finger.y,
                            fraction = fraction,
                            trackWidthPx = trackWidthPx,
                            trackHeightPx = trackHeightPx,
                            thumbPx = thumbPx,
                        )
                    }
                    onScaleState.value(
                        OpenScale(
                            kind = kind,
                            leftPx = placement.leftPx,
                            topPx = placement.topPx,
                        ),
                    )
                    var currentBrightness = brightnessState.value
                    var currentHue = hueState.value
                    try {
                        drag(down.id) { change ->
                            if (horizontal) {
                                val deltaX = change.position.x - change.previousPosition.x
                                if (deltaX != 0f) {
                                    when (kind) {
                                        LookKind.Brightness -> {
                                            currentBrightness = DisplayLook.brightnessAfterHorizontalDrag(
                                                current = currentBrightness,
                                                deltaXPx = deltaX,
                                                travelPx = travelPx,
                                            )
                                            onBrightnessState.value(currentBrightness)
                                        }
                                        LookKind.Color -> {
                                            currentHue = DisplayLook.hueAfterHorizontalDrag(
                                                current = currentHue,
                                                deltaXPx = deltaX,
                                                travelPx = travelPx,
                                            )
                                            onHueState.value(currentHue)
                                        }
                                    }
                                }
                            } else {
                                val deltaY = change.position.y - change.previousPosition.y
                                if (deltaY != 0f) {
                                    when (kind) {
                                        LookKind.Brightness -> {
                                            currentBrightness = DisplayLook.brightnessAfterDrag(
                                                current = currentBrightness,
                                                deltaYPx = deltaY,
                                                travelPx = travelPx,
                                            )
                                            onBrightnessState.value(currentBrightness)
                                        }
                                        LookKind.Color -> {
                                            currentHue = DisplayLook.hueAfterDrag(
                                                current = currentHue,
                                                deltaYPx = deltaY,
                                                travelPx = travelPx,
                                            )
                                            onHueState.value(currentHue)
                                        }
                                    }
                                }
                            }
                            change.consume()
                        }
                    } finally {
                        onScaleState.value(null)
                    }
                }
            } else {
                Modifier
            }),
        contentAlignment = Alignment.Center,
    ) {
        val tint = if (active) Color.White else IconColor
        when (kind) {
            LookKind.Brightness -> BrightnessGlyph(tint)
            LookKind.Color -> ColorGlyph(hue = hue, ring = tint)
        }
    }
}

@Composable
private fun BrightnessGlyph(tint: Color) {
    Canvas(modifier = Modifier.size(GlyphSize)) {
        val radius = size.minDimension * 0.2f
        drawCircle(color = tint, radius = radius, center = center)
        val inner = size.minDimension * 0.34f
        val outer = size.minDimension * 0.48f
        val stroke = size.minDimension * 0.07f
        for (index in 0 until RAY_COUNT) {
            val angle = index * (PI / 4.0)
            val dx = cos(angle).toFloat()
            val dy = sin(angle).toFloat()
            drawLine(
                color = tint,
                start = Offset(center.x + dx * inner, center.y + dy * inner),
                end = Offset(center.x + dx * outer, center.y + dy * outer),
                strokeWidth = stroke,
                cap = StrokeCap.Round,
            )
        }
    }
}

@Composable
private fun ColorGlyph(hue: Float, ring: Color) {
    Canvas(modifier = Modifier.size(GlyphSize)) {
        val colors = List(HUE_STOPS) { index ->
            val stopHue = 360f * index / (HUE_STOPS - 1)
            Color(DisplayLook.colorArgb(stopHue, saturation = 1f, value = 1f))
        }
        drawCircle(brush = Brush.sweepGradient(colors))
        drawCircle(color = PageColor, radius = size.minDimension * 0.32f)
        drawCircle(
            color = Color(DisplayLook.colorArgb(hue, saturation = 1f, value = 1f)),
            radius = size.minDimension * 0.2f,
        )
        drawCircle(
            color = ring,
            radius = size.minDimension * 0.48f,
            style = Stroke(width = size.minDimension * 0.06f),
        )
    }
}

@Composable
private fun VerticalScale(
    kind: LookKind,
    brightness: Float,
    hue: Float,
    modifier: Modifier = Modifier,
) {
    val fraction = when (kind) {
        LookKind.Brightness -> DisplayLook.brightnessFraction(brightness)
        LookKind.Color -> DisplayLook.hueFraction(hue)
    }
    val hueStops = remember {
        List(HUE_STOPS) { index ->
            val stopHue = 360f * (HUE_STOPS - 1 - index) / (HUE_STOPS - 1)
            Color(DisplayLook.colorArgb(stopHue, saturation = 1f, value = 1f))
        }
    }
    Canvas(modifier = modifier) {
        val thumbPx = ThumbSize.toPx()
        val trackPx = TrackStroke.toPx()
        val inset = thumbPx / 2f
        val trackHeight = (size.height - thumbPx).coerceAtLeast(0f)
        val trackTop = inset
        val left = (size.width - trackPx) / 2f
        val brush = when (kind) {
            LookKind.Brightness -> Brush.verticalGradient(
                colors = listOf(Color.White, Color(0xFF2A2A2A)),
                startY = trackTop,
                endY = trackTop + trackHeight,
            )
            LookKind.Color -> Brush.verticalGradient(
                colors = hueStops,
                startY = trackTop,
                endY = trackTop + trackHeight,
            )
        }
        drawRoundRect(
            brush = brush,
            topLeft = Offset(left, trackTop),
            size = Size(trackPx, trackHeight),
            cornerRadius = CornerRadius(trackPx / 2f, trackPx / 2f),
        )
        val thumbCenter = Offset(
            x = size.width / 2f,
            y = inset + (1f - fraction) * trackHeight,
        )
        val thumbRadius = thumbPx / 2f
        val thumbColor = when (kind) {
            LookKind.Brightness -> Color.White
            LookKind.Color -> Color(DisplayLook.colorArgb(hue, saturation = 1f, value = 1f))
        }
        drawCircle(color = PageColor, radius = thumbRadius + 2.dp.toPx(), center = thumbCenter)
        drawCircle(color = thumbColor, radius = thumbRadius, center = thumbCenter)
        drawCircle(
            color = Color.White,
            radius = thumbRadius,
            center = thumbCenter,
            style = Stroke(width = 2.dp.toPx()),
        )
    }
}

@Composable
private fun HorizontalScale(
    kind: LookKind,
    brightness: Float,
    hue: Float,
    modifier: Modifier = Modifier,
) {
    val fraction = when (kind) {
        LookKind.Brightness -> DisplayLook.brightnessFraction(brightness)
        LookKind.Color -> DisplayLook.hueFraction(hue)
    }
    val hueStops = remember {
        List(HUE_STOPS) { index ->
            val stopHue = 360f * index / (HUE_STOPS - 1)
            Color(DisplayLook.colorArgb(stopHue, saturation = 1f, value = 1f))
        }
    }
    Canvas(modifier = modifier) {
        val thumbPx = ThumbSize.toPx()
        val trackPx = TrackStroke.toPx()
        val inset = thumbPx / 2f
        val trackWidth = (size.width - thumbPx).coerceAtLeast(0f)
        val trackLeft = inset
        val top = (size.height - trackPx) / 2f
        val brush = when (kind) {
            LookKind.Brightness -> Brush.horizontalGradient(
                colors = listOf(Color(0xFF2A2A2A), Color.White),
                startX = trackLeft,
                endX = trackLeft + trackWidth,
            )
            LookKind.Color -> Brush.horizontalGradient(
                colors = hueStops,
                startX = trackLeft,
                endX = trackLeft + trackWidth,
            )
        }
        drawRoundRect(
            brush = brush,
            topLeft = Offset(trackLeft, top),
            size = Size(trackWidth, trackPx),
            cornerRadius = CornerRadius(trackPx / 2f, trackPx / 2f),
        )
        val thumbCenter = Offset(
            x = inset + fraction * trackWidth,
            y = size.height / 2f,
        )
        val thumbRadius = thumbPx / 2f
        val thumbColor = when (kind) {
            LookKind.Brightness -> Color.White
            LookKind.Color -> Color(DisplayLook.colorArgb(hue, saturation = 1f, value = 1f))
        }
        drawCircle(color = PageColor, radius = thumbRadius + 2.dp.toPx(), center = thumbCenter)
        drawCircle(color = thumbColor, radius = thumbRadius, center = thumbCenter)
        drawCircle(
            color = Color.White,
            radius = thumbRadius,
            center = thumbCenter,
            style = Stroke(width = 2.dp.toPx()),
        )
    }
}

@Composable
private fun WeekdayHeader(labels: List<String>) {
    Row(modifier = Modifier.fillMaxWidth()) {
        labels.forEach { label ->
            Text(
                text = label,
                modifier = Modifier.weight(1f),
                color = MutedColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun MonthDays(cells: List<DayCell>) {
    Column(modifier = Modifier.fillMaxWidth().padding(top = 6.dp)) {
        cells.chunked(WEEK_LENGTH).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { cell ->
                    DayCellView(
                        cell = cell,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

@Composable
private fun DayCellView(
    cell: DayCell,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier.height(34.dp),
        contentAlignment = Alignment.Center,
    ) {
        if (cell.isToday) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .border(width = 1.5.dp, color = TextColor, shape = CircleShape),
            )
        }
        if (cell.day != null) {
            Text(
                text = cell.day.toString(),
                color = TextColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.Normal,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private enum class LookKind {
    Brightness,
    Color,
}

private data class OpenScale(
    val kind: LookKind,
    val leftPx: Float,
    val topPx: Float,
)

private const val CONTROLS_VISIBLE_MS = 3_000L
private const val CONTROLS_FADE_MS = 500
private const val WEEK_LENGTH = 7
private const val RAY_COUNT = 8
private const val HUE_STOPS = 7
private val IconTouch = 48.dp
private val GlyphSize = 28.dp
private val TrackWidth = 48.dp
private val TrackHeight = 260.dp
private val TrackStroke = 14.dp
private val ThumbSize = 26.dp
private val PageColor = Color(0xFF000000)
private val TextColor = Color(0xFFE6E6E6)
private val MutedColor = Color(0xFF9A9A9A)
private val IconColor = Color(0xFFC8C8C8)
private val TimeStyle = TextStyle(
    color = TextColor,
    fontSize = 116.sp,
    lineHeight = 116.sp,
    fontWeight = FontWeight.ExtraLight,
    textAlign = TextAlign.Center,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)
