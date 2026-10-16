package com.richard.glyphcontroller

import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.graphics.withSave
import top.yukonga.miuix.kmp.theme.MiuixTheme
import kotlin.math.hypot
import kotlin.math.roundToInt

/** Compose preview of the actual Phone (2) back, with the original 33-zone hit map. */
@Composable
fun GlyphPhonePreview(frame: IntArray, selected: Int, onSelect: (Int) -> Unit) {
    val resources = LocalResources.current
    val bitmap = remember(resources) { BitmapFactory.decodeResource(resources, R.drawable.phone2_glyph_map) }
    val geometry = remember { GlyphPhoneGeometry() }
    val accent = MiuixTheme.colorScheme.primary.toArgb()
    val description = stringResource(R.string.zone_preview_description)
    Canvas(Modifier.fillMaxWidth().aspectRatio(bitmap.width / 1185f).clip(RoundedCornerShape(22.dp))
        .semantics { contentDescription = description }
        .pointerInput(geometry, onSelect) {
            detectTapGestures { tap ->
                val scale = size.width / bitmap.width.toFloat()
                geometry.findZone(tap.x / scale, tap.y / scale + 330f)?.let(onSelect)
            }
        }) {
        drawRoundRect(Color(0xFF16191E))
        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            native.withSave {
                val imageScale = size.width / bitmap.width.toFloat()
                scale(imageScale, imageScale)
                translate(0f, -330f)
                clipRect(0f, 330f, bitmap.width.toFloat(), 1515f)
                drawBitmap(bitmap, 0f, 0f, geometry.imagePaint)
                geometry.draw(this, frame, selected, accent)
            }
        }
    }
}

private class GlyphPhoneGeometry {
    val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paths = Array(GlyphFrame.ZONE_COUNT) { Path() }
    private val samples = Array(GlyphFrame.ZONE_COUNT) { FloatArray(0) }

    init {
        buildPaths()
        paths.forEachIndexed { index, path ->
            val measure = PathMeasure(path, false)
            val count = (measure.length / 5f).roundToInt().coerceAtLeast(1)
            val point = FloatArray(2)
            samples[index] = FloatArray((count + 1) * 2).also { points ->
                for (step in 0..count) {
                    measure.getPosTan(measure.length * step / count, point, null)
                    points[step * 2] = point[0]
                    points[step * 2 + 1] = point[1]
                }
            }
        }
    }

    fun findZone(x: Float, y: Float): Int? {
        var nearest = -1
        var distance = 30f
        samples.forEachIndexed { index, points ->
            for (step in points.indices step 2) {
                val candidate = hypot(x - points[step], y - points[step + 1])
                if (candidate < distance) {
                    nearest = index
                    distance = candidate
                }
            }
        }
        return nearest.takeIf { it >= 0 }
    }

    fun draw(canvas: android.graphics.Canvas, frame: IntArray, selected: Int, accent: Int) {
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        paths.forEachIndexed { index, path ->
            val width = if (index in 3..18 || index in 25..32) 12f else 15f
            if (index == selected) {
                paint.color = accent
                paint.alpha = 230
                paint.strokeWidth = width + 11f
                canvas.drawPath(path, paint)
            }
            paint.color = android.graphics.Color.rgb(43, 48, 55)
            paint.alpha = 245
            paint.strokeWidth = width + 2f
            canvas.drawPath(path, paint)
            paint.color = android.graphics.Color.WHITE
            val level = frame[index]
            paint.alpha = if (level == 0) 25 else 45 + level * 210 / 255
            paint.strokeWidth = width
            canvas.drawPath(path, paint)
        }
        paint.alpha = 255
    }

    private fun buildPaths() {
        paths[0].apply {
            moveTo(311f, 494f); lineTo(311f, 443f)
            cubicTo(311f, 404f, 329f, 383f, 360f, 383f)
            cubicTo(394f, 383f, 416f, 405f, 420f, 434f)
        }
        paths[1].apply {
            moveTo(412f, 488f); lineTo(412f, 532f)
            cubicTo(411f, 562f, 390f, 582f, 360f, 582f)
            cubicTo(346f, 582f, 336f, 578f, 329f, 571f)
        }
        line(2, 590f, 510f, 675f, 412f)
        val topArc = Path().apply {
            moveTo(703f, 689f)
            cubicTo(648f, 620f, 592f, 587f, 470f, 598f)
        }
        val arcMeasure = PathMeasure(topArc, false)
        for (part in 0 until 16) {
            val start = arcMeasure.length * (part + .09f) / 16f
            val end = arcMeasure.length * (part + .91f) / 16f
            arcMeasure.getSegment(start, end, paths[3 + part], true)
        }
        paths[19].apply {
            moveTo(311f, 691f)
            cubicTo(340f, 652f, 374f, 630f, 405f, 617f)
        }
        line(20, 305f, 734f, 305f, 839f)
        paths[21].apply {
            moveTo(310f, 1000f)
            cubicTo(371f, 1080f, 453f, 1106f, 545f, 1093f)
        }
        paths[22].apply {
            moveTo(610f, 1072f)
            cubicTo(648f, 1053f, 680f, 1023f, 703f, 1000f)
        }
        line(23, 706f, 854f, 706f, 934f)
        paths[24].addCircle(507f, 1293f, 2f, Path.Direction.CW)
        for (part in 0 until 8) {
            val bottom = 1258f - part * 13.2f
            line(25 + part, 507f, bottom, 507f, bottom - 11.7f)
        }
    }

    private fun line(index: Int, x1: Float, y1: Float, x2: Float, y2: Float) {
        paths[index].moveTo(x1, y1)
        paths[index].lineTo(x2, y2)
    }
}
