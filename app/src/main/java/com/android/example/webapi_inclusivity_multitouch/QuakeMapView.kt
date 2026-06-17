package com.android.example.webapi_inclusivity_multitouch

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import kotlin.math.hypot

class QuakeMapView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    var onQuakeSelected: ((QuakeEvent) -> Unit)? = null

    private val quakes = mutableListOf<QuakeEvent>()

    private var selectedQuakeId: String? = null
    private var highContrast = false

    private var scaleFactor = 1f
    private var offsetX = 0f
    private var offsetY = 0f

    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var totalMove = 0f

    private val mapPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val selectedPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                scaleFactor = (scaleFactor * detector.scaleFactor).coerceIn(0.8f, 7f)
                invalidate()
                return true
            }
        }
    )

    init {
        isClickable = true
        isFocusable = true
    }

    fun setQuakes(newQuakes: List<QuakeEvent>) {
        quakes.clear()
        quakes.addAll(newQuakes)
        contentDescription =
            "Interactive earthquake map showing ${quakes.size} earthquake events. Pinch to zoom, drag to pan, and tap a marker for details."
        invalidate()
    }

    fun selectQuake(quake: QuakeEvent) {
        selectedQuakeId = quake.id
        invalidate()
    }

    fun setHighContrast(enabled: Boolean) {
        highContrast = enabled
        invalidate()
    }

    fun resetView() {
        scaleFactor = 1f
        offsetX = 0f
        offsetY = 0f
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        val backgroundColor = if (highContrast) Color.BLACK else Color.rgb(230, 239, 244)
        canvas.drawColor(backgroundColor)

        drawInstructions(canvas)

        canvas.save()
        canvas.translate(width / 2f + offsetX, height / 2f + offsetY)
        canvas.scale(scaleFactor, scaleFactor)
        canvas.translate(-width / 2f, -height / 2f)

        drawMapBase(canvas)
        drawQuakeMarkers(canvas)

        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        parent?.requestDisallowInterceptTouchEvent(true)
        scaleDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.x
                downY = event.y
                lastX = event.x
                lastY = event.y
                totalMove = 0f
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount == 1 && !scaleDetector.isInProgress) {
                    val dx = event.x - lastX
                    val dy = event.y - lastY
                    offsetX += dx
                    offsetY += dy
                    totalMove += hypot(dx, dy)
                    lastX = event.x
                    lastY = event.y
                    invalidate()
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                val tapDistance = hypot(event.x - downX, event.y - downY)
                if (tapDistance < dp(12) && totalMove < dp(12)) {
                    findNearestQuake(event.x, event.y)?.let { quake ->
                        selectedQuakeId = quake.id
                        onQuakeSelected?.invoke(quake)
                        performClick()
                        invalidate()
                    }
                }
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }

        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    private fun drawInstructions(canvas: Canvas) {
        textPaint.color = if (highContrast) Color.WHITE else Color.rgb(35, 35, 35)
        textPaint.textSize = dp(13).toFloat()
        textPaint.textAlign = Paint.Align.CENTER
        canvas.drawText(
            "Pinch to zoom • Drag to pan • Tap a marker",
            width / 2f,
            dp(26).toFloat(),
            textPaint
        )
    }

    private fun drawMapBase(canvas: Canvas) {
        val plot = mapRect()

        mapPaint.style = Paint.Style.FILL
        mapPaint.color = if (highContrast) Color.rgb(10, 10, 10) else Color.rgb(205, 226, 235)
        canvas.drawRoundRect(plot, dp(18).toFloat(), dp(18).toFloat(), mapPaint)

        mapPaint.style = Paint.Style.STROKE
        mapPaint.strokeWidth = dp(2).toFloat()
        mapPaint.color = if (highContrast) Color.WHITE else Color.rgb(80, 100, 110)
        canvas.drawRoundRect(plot, dp(18).toFloat(), dp(18).toFloat(), mapPaint)

        gridPaint.style = Paint.Style.STROKE
        gridPaint.strokeWidth = dp(1).toFloat()
        gridPaint.color = if (highContrast) Color.rgb(120, 120, 120) else Color.rgb(145, 165, 175)

        for (longitude in -150..150 step 30) {
            val x = longitudeToX(longitude.toDouble())
            canvas.drawLine(x, plot.top, x, plot.bottom, gridPaint)
        }

        for (latitude in -60..60 step 30) {
            val y = latitudeToY(latitude.toDouble())
            canvas.drawLine(plot.left, y, plot.right, y, gridPaint)
        }

        gridPaint.strokeWidth = dp(2).toFloat()
        gridPaint.color = if (highContrast) Color.YELLOW else Color.rgb(90, 120, 135)

        val equatorY = latitudeToY(0.0)
        val primeMeridianX = longitudeToX(0.0)

        canvas.drawLine(plot.left, equatorY, plot.right, equatorY, gridPaint)
        canvas.drawLine(primeMeridianX, plot.top, primeMeridianX, plot.bottom, gridPaint)

        textPaint.color = if (highContrast) Color.WHITE else Color.rgb(50, 60, 65)
        textPaint.textSize = dp(12).toFloat()
        textPaint.textAlign = Paint.Align.LEFT
        canvas.drawText("Simplified world view", plot.left + dp(12), plot.top + dp(22), textPaint)
    }

    private fun drawQuakeMarkers(canvas: Canvas) {
        quakes.forEach { quake ->
            val x = longitudeToX(quake.longitude)
            val y = latitudeToY(quake.latitude)
            val radius = markerRadius(quake.magnitude)
            val selected = quake.id == selectedQuakeId

            markerPaint.style = Paint.Style.FILL
            markerPaint.color = markerColor(quake.magnitude)
            canvas.drawCircle(x, y, radius, markerPaint)

            markerPaint.style = Paint.Style.STROKE
            markerPaint.strokeWidth = dp(1.5f)
            markerPaint.color = if (highContrast) Color.WHITE else Color.rgb(45, 45, 45)
            canvas.drawCircle(x, y, radius, markerPaint)

            if (selected) {
                selectedPaint.style = Paint.Style.STROKE
                selectedPaint.strokeWidth = dp(3).toFloat()
                selectedPaint.color = if (highContrast) Color.YELLOW else Color.rgb(30, 80, 180)
                canvas.drawCircle(x, y, radius + dp(7), selectedPaint)
            }
        }
    }

    private fun findNearestQuake(screenX: Float, screenY: Float): QuakeEvent? {
        val baseX = ((screenX - width / 2f - offsetX) / scaleFactor) + width / 2f
        val baseY = ((screenY - height / 2f - offsetY) / scaleFactor) + height / 2f

        var nearest: QuakeEvent? = null
        var nearestDistance = Float.MAX_VALUE

        quakes.forEach { quake ->
            val quakeX = longitudeToX(quake.longitude)
            val quakeY = latitudeToY(quake.latitude)
            val distance = hypot(baseX - quakeX, baseY - quakeY)

            if (distance < nearestDistance) {
                nearestDistance = distance
                nearest = quake
            }
        }

        return if (nearestDistance <= dp(36)) nearest else null
    }

    private fun markerRadius(magnitude: Double): Float {
        return (dp(6) + magnitude.toFloat() * dp(2.2f)).coerceIn(dp(8f), dp(24f))
    }

    private fun markerColor(magnitude: Double): Int {
        return when {
            highContrast && magnitude >= 5.0 -> Color.RED
            highContrast -> Color.YELLOW
            magnitude >= 5.0 -> Color.rgb(210, 60, 45)
            magnitude >= 4.0 -> Color.rgb(230, 145, 45)
            else -> Color.rgb(70, 130, 210)
        }
    }

    private fun mapRect(): RectF {
        return RectF(
            width * 0.07f,
            height * 0.16f,
            width * 0.93f,
            height * 0.86f
        )
    }

    private fun longitudeToX(longitude: Double): Float {
        val plot = mapRect()
        val normalized = ((longitude + 180.0) / 360.0).coerceIn(0.0, 1.0)
        return (plot.left + normalized * plot.width()).toFloat()
    }

    private fun latitudeToY(latitude: Double): Float {
        val plot = mapRect()
        val normalized = ((90.0 - latitude) / 180.0).coerceIn(0.0, 1.0)
        return (plot.top + normalized * plot.height()).toFloat()
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun dp(value: Float): Float {
        return value * resources.displayMetrics.density
    }
}