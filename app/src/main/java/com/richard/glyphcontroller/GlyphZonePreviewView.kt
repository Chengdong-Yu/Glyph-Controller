package com.richard.glyphcontroller

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PathMeasure
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import com.google.android.material.color.MaterialColors
import kotlin.math.hypot
import kotlin.math.roundToInt

/** Phone (2) back image with 33 selectable zones at their physical locations. */
class GlyphZonePreviewView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {
    var onZoneSelected: ((Int) -> Unit)? = null

    private val phoneImage = BitmapFactory.decodeResource(resources, R.drawable.phone2_glyph_map)
    private val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val paths = Array(GlyphPowerState.ZONE_COUNT) { Path() }
    private var frame = IntArray(GlyphPowerState.ZONE_COUNT)
    private var selectedZone = 0

    init {
        contentDescription = context.getString(R.string.zone_preview_description)
        isClickable = true
        buildPaths()
    }

    fun showFrame(values: IntArray) {
        if (values.size != GlyphPowerState.ZONE_COUNT) return
        frame = values.copyOf()
        invalidate()
    }

    fun selectZone(index: Int) {
        if (index !in paths.indices) return
        selectedZone = index
        invalidate()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val measuredWidth = MeasureSpec.getSize(widthMeasureSpec).takeIf { it > 0 } ?: phoneImage.width
        val desiredHeight = (measuredWidth * CROP_HEIGHT / phoneImage.width.toFloat()).roundToInt()
        setMeasuredDimension(measuredWidth, resolveSize(desiredHeight, heightMeasureSpec))
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(22, 25, 30)
        paint.alpha = 255
        canvas.drawRoundRect(RectF(0f, 0f, width.toFloat(), height.toFloat()), 20f, 20f, paint)

        val scale = width / phoneImage.width.toFloat()
        canvas.save()
        canvas.scale(scale, scale)
        canvas.translate(0f, -CROP_TOP.toFloat())
        canvas.clipRect(0f, CROP_TOP.toFloat(), phoneImage.width.toFloat(), CROP_BOTTOM.toFloat())
        canvas.drawBitmap(phoneImage, 0f, 0f, imagePaint)

        val selectedColor = MaterialColors.getColor(
            this, com.google.android.material.R.attr.colorPrimary, Color.CYAN
        )
        for (index in paths.indices) drawZone(canvas, index, selectedColor)
        canvas.restore()
    }

    private fun drawZone(canvas: Canvas, index: Int, selectedColor: Int) {
        val path = paths[index]
        val width = if (index in 3..18 || index in 25..32) 12f else 15f
        paint.style = Paint.Style.STROKE
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND

        if (index == selectedZone) {
            paint.color = selectedColor
            paint.alpha = 230
            paint.strokeWidth = width + 11f
            canvas.drawPath(path, paint)
        }

        // The reference photo has every light on. Cover each photographed strip
        // before drawing its current brightness so an off zone appears off.
        paint.color = Color.rgb(43, 48, 55)
        paint.alpha = 245
        paint.strokeWidth = width + 2f
        canvas.drawPath(path, paint)

        val level = frame[index]
        paint.color = Color.WHITE
        paint.alpha = if (level == 0) 25 else 45 + level * 210 / 255
        paint.strokeWidth = width
        canvas.drawPath(path, paint)
        paint.alpha = 255
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

        // D1_1 is at the bottom; D1_8 is at the top.
        for (part in 0 until 8) {
            val bottom = 1258f - part * 13.2f
            line(25 + part, 507f, bottom, 507f, bottom - 11.7f)
        }
    }

    private fun line(index: Int, x1: Float, y1: Float, x2: Float, y2: Float) {
        paths[index].moveTo(x1, y1)
        paths[index].lineTo(x2, y2)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) return true
        if (event.action != MotionEvent.ACTION_UP) return super.onTouchEvent(event)
        performClick()

        val scale = width / phoneImage.width.toFloat()
        val x = event.x / scale
        val y = event.y / scale + CROP_TOP
        val point = FloatArray(2)
        var nearest = -1
        var distance = 30f
        for (index in paths.indices) {
            val measure = PathMeasure(paths[index], false)
            val samples = (measure.length / 5f).roundToInt().coerceAtLeast(1)
            for (sample in 0..samples) {
                measure.getPosTan(measure.length * sample / samples, point, null)
                val candidate = hypot(x - point[0], y - point[1])
                if (candidate < distance) {
                    nearest = index
                    distance = candidate
                }
            }
        }
        if (nearest >= 0) onZoneSelected?.invoke(nearest)
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private companion object {
        const val CROP_TOP = 330
        const val CROP_BOTTOM = 1515
        const val CROP_HEIGHT = CROP_BOTTOM - CROP_TOP
    }
}
