package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.CyanStroke35
import com.example.ui.theme.GlassContainerFill
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.NeonPurple
import com.example.ui.theme.PurpleStroke35

fun swoxGlassGradientStroke(
    highlightProgress: Float = 0f
): Brush {
    val startColor = if (highlightProgress > 0f) {
        NeonCyan.copy(alpha = 0.35f + 0.65f * highlightProgress)
    } else {
        CyanStroke35
    }
    val endColor = if (highlightProgress > 0f) {
        NeonPurple.copy(alpha = 0.35f + 0.65f * highlightProgress)
    } else {
        PurpleStroke35
    }
    return Brush.linearGradient(colors = listOf(startColor, endColor))
}

@Composable
fun GlassmorphicContainer(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    containerColor: Color = GlassContainerFill,
    strokeWidth: Dp = 1.dp,
    highlightProgress: Float = 0f,
    content: @Composable BoxScope.() -> Unit
) {
    val borderBrush = swoxGlassGradientStroke(highlightProgress)
    Box(
        modifier = modifier
            .clip(shape)
            .background(containerColor)
            .border(
                border = BorderStroke(width = strokeWidth, brush = borderBrush),
                shape = shape
            ),
        content = content
    )
}
