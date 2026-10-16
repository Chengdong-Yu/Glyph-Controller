package com.richard.glyphcontroller

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.IconButton
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.ArrowRight
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
internal fun GlyphStatusPill(
    text: String,
    active: Boolean,
    modifier: Modifier = Modifier,
) {
    val color = if (active) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.error
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(7.dp).background(color, CircleShape))
        Text(text = text, style = MiuixTheme.textStyles.footnote1, color = color)
    }
}

@Composable
internal fun GlyphBackButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            imageVector = MiuixIcons.Basic.ArrowRight,
            contentDescription = null,
            modifier = Modifier.graphicsLayer { scaleX = -1f },
            tint = MiuixTheme.colorScheme.onSurface,
        )
    }
}

@Composable
internal fun GlyphMiniature(
    lit: Boolean,
    modifier: Modifier = Modifier,
) {
    val intensity by animateFloatAsState(
        targetValue = if (lit) 1f else 0.28f,
        label = "glyphMiniatureIntensity",
    )
    val color = if (lit) MiuixTheme.colorScheme.primary else MiuixTheme.colorScheme.onSurfaceVariantActions
    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .background(MiuixTheme.colorScheme.surfaceContainerHigh)
            .padding(20.dp),
    ) {
        val stroke = size.minDimension * 0.085f
        val style = Stroke(width = stroke, cap = StrokeCap.Round)
        val alpha = intensity
        drawArc(
            color = color,
            startAngle = -43f,
            sweepAngle = 274f,
            useCenter = false,
            topLeft = androidx.compose.ui.geometry.Offset(size.width * 0.22f, size.height * 0.13f),
            size = androidx.compose.ui.geometry.Size(size.width * 0.58f, size.width * 0.58f),
            alpha = alpha,
            style = style,
        )
        drawLine(
            color = color,
            start = androidx.compose.ui.geometry.Offset(size.width * 0.62f, size.height * 0.16f),
            end = androidx.compose.ui.geometry.Offset(size.width * 0.84f, size.height * 0.02f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
            alpha = alpha,
        )
        drawLine(
            color = color,
            start = androidx.compose.ui.geometry.Offset(size.width * 0.50f, size.height * 0.75f),
            end = androidx.compose.ui.geometry.Offset(size.width * 0.50f, size.height * 0.98f),
            strokeWidth = stroke,
            cap = StrokeCap.Round,
            alpha = alpha,
        )
    }
}
