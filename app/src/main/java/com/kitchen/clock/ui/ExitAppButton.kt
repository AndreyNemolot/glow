package com.kitchen.clock.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

// Глиф выхода рисуется с непрозрачностью 50% и закрывает приложение по нажатию.
@Composable
fun ExitAppButton(
    onExit: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier = modifier
            .padding(top = CornerInset, end = CornerInset)
            .size(TouchSize)
            .semantics {
                role = Role.Button
                contentDescription = "Выход"
            }
            .then(
                if (enabled) {
                    Modifier.clickable(
                        interactionSource = interaction,
                        indication = null,
                        onClick = onExit,
                    )
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        ExitGlyph()
    }
}

@Composable
private fun ExitGlyph() {
    Canvas(
        modifier = Modifier
            .size(GlyphSize)
            .graphicsLayer { alpha = 0.5f },
    ) {
        val tint = Color.White
        val stroke = size.minDimension * 0.08f
        val left = size.width * 0.08f
        val top = size.height * 0.14f
        val bottom = size.height * 0.86f
        val doorRight = size.width * 0.46f
        drawLine(
            color = tint,
            start = Offset(doorRight, top),
            end = Offset(left, top),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = tint,
            start = Offset(left, top),
            end = Offset(left, bottom),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = tint,
            start = Offset(left, bottom),
            end = Offset(doorRight, bottom),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        val arrowY = center.y
        val arrowStart = size.width * 0.28f
        val arrowEnd = size.width * 0.92f
        drawLine(
            color = tint,
            start = Offset(arrowStart, arrowY),
            end = Offset(arrowEnd, arrowY),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        val head = size.minDimension * 0.16f
        drawLine(
            color = tint,
            start = Offset(arrowEnd, arrowY),
            end = Offset(arrowEnd - head, arrowY - head),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = tint,
            start = Offset(arrowEnd, arrowY),
            end = Offset(arrowEnd - head, arrowY + head),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
        )
    }
}

private val CornerInset = 16.dp
private val TouchSize = 48.dp
private val GlyphSize = 28.dp
