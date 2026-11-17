package com.richard.glyphcontroller

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.graphics.withSave
import java.lang.ref.WeakReference
import kotlin.math.roundToInt
import ui.feice.mochi.shape.MochiRoundedRectangle
import ui.feice.mochi.theme.MochiTheme
import ui.feice.mochi.utils.rememberMochiHaptics

/**
 * Interactive Nothing Phone (2) backplate stage with white LED bloom, Nothing Red selection ring,
 * tap picking, and continuous drag-to-scrub across all 33 hardware zones.
 */
@Composable
fun GlyphPhonePreview(
    snapshot: GlyphSnapshot,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    highlightedZones: IntRange? = null,
) {
    val context = LocalContext.current
    val configuration = LocalConfiguration.current
    val bitmap = remember(context, configuration) { GlyphBackplateBitmapCache.obtain(context) }
    val paints = remember { GlyphCanvasPaints() }
    val accentArgb = MochiTheme.colorScheme.primary.toArgb()
    val haptics = rememberMochiHaptics()
    val currentSelected by rememberUpdatedState(selected)
    val currentOnSelect by rememberUpdatedState(onSelect)
    val description = stringResource(R.string.zone_preview_description)
    val stageShape = remember { MochiRoundedRectangle(18.dp) }

    Canvas(
        modifier
            .fillMaxWidth()
            .aspectRatio(bitmap.width / 1185f)
            .clip(stageShape)
            .semantics { contentDescription = description }
            .pointerInput(bitmap.width) {
                detectTapGestures { tap ->
                    val scale = size.width / bitmap.width.toFloat()
                    GlyphPhonePaths.findZone(
                        x = tap.x / scale,
                        y = tap.y / scale + 330f,
                        maxDistance = 38f,
                    )?.let { hit ->
                        if (hit != currentSelected) {
                            haptics.tick()
                        }
                        currentOnSelect(hit)
                    }
                }
            }
            .pointerInput(bitmap.width) {
                var lastScrubbed = -1
                detectDragGestures(
                    onDragStart = { pos ->
                        val scale = size.width / bitmap.width.toFloat()
                        GlyphPhonePaths.findZone(
                            x = pos.x / scale,
                            y = pos.y / scale + 330f,
                            maxDistance = 44f,
                        )?.let { hit ->
                            lastScrubbed = hit
                            if (hit != currentSelected) {
                                haptics.tick()
                                currentOnSelect(hit)
                            }
                        }
                    },
                    onDragEnd = { lastScrubbed = -1 },
                    onDragCancel = { lastScrubbed = -1 },
                    onDrag = { change, _ ->
                        val scale = size.width / bitmap.width.toFloat()
                        val hit = GlyphPhonePaths.findZone(
                            x = change.position.x / scale,
                            y = change.position.y / scale + 330f,
                            maxDistance = 44f,
                        )
                        if (hit != null && hit != lastScrubbed) {
                            lastScrubbed = hit
                            change.consume()
                            haptics.tick()
                            currentOnSelect(hit)
                        }
                    },
                )
            },
    ) {
        drawRect(Color(0xFF0A0A0C))
        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            native.withSave {
                val imageScale = size.width / bitmap.width.toFloat()
                scale(imageScale, imageScale)
                translate(0f, -330f)
                clipRect(0f, 330f, bitmap.width.toFloat(), 1515f)
                drawBitmap(bitmap, 0f, 0f, paints.imagePaint)
                paints.drawZones(
                    canvas = this,
                    snapshot = snapshot,
                    selected = selected,
                    highlightedRange = highlightedZones,
                    accentArgb = accentArgb,
                    trackArgb = android.graphics.Color.rgb(38, 38, 42),
                )
            }
        }
    }
}

/**
 * Resolution-independent 33-zone vector topology renderer used for the Overview hero card,
 * Studio preset selector frames, and Saved Scene thumbnails.
 */
@Composable
fun GlyphVectorPreview(
    snapshot: GlyphSnapshot,
    modifier: Modifier = Modifier,
    selected: Int = -1,
    showChassis: Boolean = true,
    onTapZone: ((Int) -> Unit)? = null,
) {
    val cs = MochiTheme.colorScheme
    val paints = remember { GlyphCanvasPaints() }
    val accentArgb = cs.primary.toArgb()
    val chassisFill = if (cs.isDark) Color(0xFF131315).toArgb() else Color(0xFF1A1A1E).toArgb()
    val chassisStroke = if (cs.isDark) Color(0x2EFFFFFF).toArgb() else Color(0x3DFFFFFF).toArgb()
    val trackArgb = if (cs.isDark) android.graphics.Color.rgb(42, 42, 46) else android.graphics.Color.rgb(52, 52, 58)
    val haptics = rememberMochiHaptics()
    val currentOnTap by rememberUpdatedState(onTapZone)

    val viewportLeft = 250f
    val viewportTop = 335f
    val viewportW = 510f
    val viewportH = 1010f

    Canvas(
        modifier = modifier.then(
            if (onTapZone != null) {
                Modifier.pointerInput(Unit) {
                    detectTapGestures { tap ->
                        val scale = minOf(size.width / viewportW, size.height / viewportH)
                        val ox = (size.width - viewportW * scale) * 0.5f
                        val oy = (size.height - viewportH * scale) * 0.5f
                        val gx = (tap.x - ox) / scale + viewportLeft
                        val gy = (tap.y - oy) / scale + viewportTop
                        val hit = GlyphPhonePaths.findZone(gx, gy, maxDistance = 58f)
                        haptics.tick()
                        currentOnTap?.invoke(hit ?: 0)
                    }
                }
            } else {
                Modifier
            },
        ),
    ) {
        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            native.withSave {
                val scale = minOf(size.width / viewportW, size.height / viewportH)
                val ox = (size.width - viewportW * scale) * 0.5f
                val oy = (size.height - viewportH * scale) * 0.5f
                translate(ox, oy)
                scale(scale, scale)
                translate(-viewportLeft, -viewportTop)
                if (showChassis) {
                    paints.drawChassis(this, chassisFill, chassisStroke)
                }
                paints.drawZones(
                    canvas = this,
                    snapshot = snapshot,
                    selected = selected,
                    highlightedRange = null,
                    accentArgb = accentArgb,
                    trackArgb = trackArgb,
                )
            }
        }
    }
}

/** Decodes `phone2_glyph_map` once per density and shares it via a GC-friendly [WeakReference]. */
private object GlyphBackplateBitmapCache {
    private var cachedDpi: Int = -1
    private var cachedRef: WeakReference<Bitmap>? = null

    @Synchronized
    fun obtain(context: Context): Bitmap {
        val resources = context.resources
        val dpi = resources.displayMetrics.densityDpi
        val existing = cachedRef?.get()
        if (existing != null && !existing.isRecycled && cachedDpi == dpi) {
            return existing
        }
        return BitmapFactory.decodeResource(resources, R.drawable.phone2_glyph_map).also { decoded ->
            cachedDpi = dpi
            cachedRef = WeakReference(decoded)
        }
    }
}

/**
 * Process-wide immutable geometry for all 33 Nothing Phone (2) LED zones and pre-sampled
 * hit-testing points. Built once on first access rather than re-allocated per Composable.
 */
internal object GlyphPhonePaths {
    val chassisRect = RectF(262f, 348f, 748f, 1332f)
    val cameraPillRect = RectF(295f, 368f, 428f, 596f)
    val paths: Array<Path> = Array(GlyphFrame.ZONE_COUNT) { Path() }
    private val samples: Array<FloatArray>

    init {
        buildPaths()
        samples = Array(GlyphFrame.ZONE_COUNT) { index ->
            val measure = PathMeasure(paths[index], false)
            val count = (measure.length / 5f).roundToInt().coerceAtLeast(1)
            val point = FloatArray(2)
            FloatArray((count + 1) * 2).also { points ->
                for (step in 0..count) {
                    measure.getPosTan(measure.length * step / count, point, null)
                    points[step * 2] = point[0]
                    points[step * 2 + 1] = point[1]
                }
            }
        }
    }

    fun findZone(x: Float, y: Float, maxDistance: Float = 32f): Int? {
        var nearest = -1
        var bestSq = maxDistance * maxDistance
        for (index in samples.indices) {
            val points = samples[index]
            var step = 0
            while (step < points.size) {
                val dx = x - points[step]
                val dy = y - points[step + 1]
                val distSq = dx * dx + dy * dy
                if (distSq < bestSq) {
                    nearest = index
                    bestSq = distSq
                }
                step += 2
            }
        }
        return nearest.takeIf { it >= 0 }
    }

    private fun buildPaths() {
        paths[0].apply {
            moveTo(311f, 494f)
            lineTo(311f, 443f)
            cubicTo(311f, 404f, 329f, 383f, 360f, 383f)
            cubicTo(394f, 383f, 416f, 405f, 420f, 434f)
        }
        paths[1].apply {
            moveTo(412f, 488f)
            lineTo(412f, 532f)
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
            val start = arcMeasure.length * (part + 0.09f) / 16f
            val end = arcMeasure.length * (part + 0.91f) / 16f
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
        paths[24].addCircle(507f, 1293f, 3.5f, Path.Direction.CW)
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

private class GlyphCanvasPaints {
    val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
        maskFilter = BlurMaskFilter(22f, BlurMaskFilter.Blur.NORMAL)
    }
    private val chassisPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun drawChassis(canvas: android.graphics.Canvas, fillArgb: Int, strokeArgb: Int) {
        chassisPaint.style = Paint.Style.FILL
        chassisPaint.color = fillArgb
        canvas.drawRoundRect(GlyphPhonePaths.chassisRect, 82f, 82f, chassisPaint)

        chassisPaint.style = Paint.Style.STROKE
        chassisPaint.strokeWidth = 3.5f
        chassisPaint.color = strokeArgb
        canvas.drawRoundRect(GlyphPhonePaths.chassisRect, 82f, 82f, chassisPaint)

        chassisPaint.strokeWidth = 2.2f
        chassisPaint.alpha = (android.graphics.Color.alpha(strokeArgb) * 0.55f).toInt()
        canvas.drawRoundRect(GlyphPhonePaths.cameraPillRect, 66f, 66f, chassisPaint)
        canvas.drawCircle(505f, 845f, 152f, chassisPaint)
        chassisPaint.alpha = 255
    }

    fun drawZones(
        canvas: android.graphics.Canvas,
        snapshot: GlyphSnapshot,
        selected: Int,
        highlightedRange: IntRange?,
        accentArgb: Int,
        trackArgb: Int,
    ) {
        val paths = GlyphPhonePaths.paths
        for (index in paths.indices) {
            val path = paths[index]
            val width = if (index in 3..18 || index in 25..32) 12.5f else 15.5f
            val level = snapshot[index].coerceIn(0, 255)
            val isPrimarySelected = index == selected
            val isGroupSelected = !isPrimarySelected && highlightedRange?.contains(index) == true

            if (level > 12 || isPrimarySelected) {
                glowPaint.color = if (isPrimarySelected) accentArgb else android.graphics.Color.WHITE
                glowPaint.alpha = if (isPrimarySelected) 210 else (32 + level * 155 / 255).coerceIn(0, 200)
                glowPaint.strokeWidth = width + if (isPrimarySelected) 20f else 14f
                canvas.drawPath(path, glowPaint)
            }

            if (isPrimarySelected || isGroupSelected) {
                strokePaint.color = accentArgb
                strokePaint.alpha = if (isPrimarySelected) 240 else 140
                strokePaint.strokeWidth = width + if (isPrimarySelected) 11f else 7.5f
                canvas.drawPath(path, strokePaint)
            }

            strokePaint.color = trackArgb
            strokePaint.alpha = 240
            strokePaint.strokeWidth = width + 2.5f
            canvas.drawPath(path, strokePaint)

            strokePaint.color = android.graphics.Color.WHITE
            strokePaint.alpha = if (level == 0) 30 else (58 + level * 197 / 255).coerceIn(0, 255)
            strokePaint.strokeWidth = width
            canvas.drawPath(path, strokePaint)
        }
        strokePaint.alpha = 255
    }
}
