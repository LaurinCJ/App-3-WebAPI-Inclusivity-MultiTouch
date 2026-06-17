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

/*
    QuakeMapView is a custom Android View.

    Instead of using normal XML widgets, this class draws its own simplified
    world map and earthquake markers on a Canvas.

    This file is where the multi-touch requirement is mainly handled:
    - pinch to zoom uses ScaleGestureDetector
    - drag to pan uses MotionEvent movement
    - tapping markers selects earthquakes
*/
class QuakeMapView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    /*
        This callback lets MainActivity know when the user taps a marker.

        QuakeMapView handles drawing and touch detection.
        MainActivity handles updating the details text and list selection.
    */
    var onQuakeSelected: ((QuakeEvent) -> Unit)? = null

    /*
        Internal list of earthquakes currently shown on the map.
    */
    private val quakes = mutableListOf<QuakeEvent>()

    /*
        selectedQuakeId is used to draw a highlight ring around the selected
        marker.
    */
    private var selectedQuakeId: String? = null

    /*
        highContrast changes the drawing colors for accessibility.
    */
    private var highContrast = false

    /*
        These variables control map transformations.

        scaleFactor:
        - 1f means normal size
        - larger values mean zoomed in

        offsetX and offsetY:
        - track how far the user has dragged/panned the map
    */
    private var scaleFactor = 1f
    private var offsetX = 0f
    private var offsetY = 0f

    /*
        These variables help separate a tap from a drag.

        If the finger moves only a small amount, we treat it as a tap.
        If it moves farther, we treat it as panning.
    */
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var totalMove = 0f

    /*
        Paint objects store drawing settings such as color, stroke width,
        fill/stroke style, and text size.

        Reusing Paint objects is better than creating new ones every time
        onDraw() runs.
    */
    private val mapPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val gridPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val selectedPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    /*
        ScaleGestureDetector is Android's helper class for detecting pinch zoom.

        It examines multi-touch MotionEvents and reports a scale factor when
        the user moves two fingers closer together or farther apart.
    */
    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                /*
                    detector.scaleFactor is a multiplier from the current gesture.

                    coerceIn keeps the zoom level within a reasonable range so
                    the map cannot become impossibly tiny or huge.
                */
                scaleFactor = (scaleFactor * detector.scaleFactor).coerceIn(0.8f, 7f)
                invalidate()
                return true
            }
        }
    )

    init {
        /*
            These help accessibility and interaction.

            clickable/focusable allows the View to behave like something the user
            can interact with rather than a purely decorative drawing.
        */
        isClickable = true
        isFocusable = true
    }

    fun setQuakes(newQuakes: List<QuakeEvent>) {
        /*
            Replace the current map data with new data.
        */
        quakes.clear()
        quakes.addAll(newQuakes)

        /*
            Update the View's content description so screen readers get useful
            information about what the map contains.
        */
        contentDescription =
            "Interactive earthquake map showing ${quakes.size} earthquake events. Pinch to zoom, drag to pan, and tap a marker for details."

        /*
            invalidate() tells Android to redraw this View.
        */
        invalidate()
    }

    fun selectQuake(quake: QuakeEvent) {
        /*
            Store which marker should be highlighted.
        */
        selectedQuakeId = quake.id
        invalidate()
    }

    fun setHighContrast(enabled: Boolean) {
        /*
            MainActivity calls this when the high contrast switch changes.
        */
        highContrast = enabled
        invalidate()
    }

    fun resetView() {
        /*
            Restore the map to its original zoom and pan state.
        */
        scaleFactor = 1f
        offsetX = 0f
        offsetY = 0f
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        /*
            Draw the background first. Everything else appears on top.
        */
        val backgroundColor = if (highContrast) Color.BLACK else Color.rgb(230, 239, 244)
        canvas.drawColor(backgroundColor)

        /*
            Instructions are drawn outside the zoomed/panned map area so they
            stay readable even when the map is moved.
        */
        drawInstructions(canvas)

        /*
            The canvas transformation below applies pan and zoom to the map.

            save() stores the current canvas state.
            restore() returns the canvas to that saved state after drawing.
        */
        canvas.save()

        /*
            Move the drawing origin based on user panning.
            The width/height math keeps zoom centered around the view center.
        */
        canvas.translate(width / 2f + offsetX, height / 2f + offsetY)
        canvas.scale(scaleFactor, scaleFactor)
        canvas.translate(-width / 2f, -height / 2f)

        /*
            Draw the map and markers inside the transformed canvas.
            This means they respond to pinch zoom and drag pan.
        */
        drawMapBase(canvas)
        drawQuakeMarkers(canvas)

        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        /*
            This asks the parent NestedScrollView not to steal touch events while
            the user interacts with the map.

            Without this, vertical dragging on the map could scroll the page
            instead of panning the map.
        */
        parent?.requestDisallowInterceptTouchEvent(true)

        /*
            Always pass the event to the ScaleGestureDetector so it can detect
            two-finger pinch gestures.
        */
        scaleDetector.onTouchEvent(event)

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                /*
                    Record the starting point of the touch.
                */
                downX = event.x
                downY = event.y
                lastX = event.x
                lastY = event.y
                totalMove = 0f
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                /*
                    One-finger movement pans the map.

                    If scaleDetector.isInProgress is true, the user is doing a
                    pinch gesture, so we avoid also treating it as a drag.
                */
                if (event.pointerCount == 1 && !scaleDetector.isInProgress) {
                    val dx = event.x - lastX
                    val dy = event.y - lastY

                    offsetX += dx
                    offsetY += dy

                    /*
                        totalMove helps us decide later whether this interaction
                        was a tap or a drag.
                    */
                    totalMove += hypot(dx, dy)

                    lastX = event.x
                    lastY = event.y

                    invalidate()
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                /*
                    If the finger barely moved, treat the gesture as a tap.
                    Then try to select the nearest earthquake marker.
                */
                val tapDistance = hypot(event.x - downX, event.y - downY)
                if (tapDistance < dp(12) && totalMove < dp(12)) {
                    findNearestQuake(event.x, event.y)?.let { quake ->
                        selectedQuakeId = quake.id
                        onQuakeSelected?.invoke(quake)

                        /*
                            performClick() is important for accessibility and
                            keeps Android lint happy for custom clickable views.
                        */
                        performClick()

                        invalidate()
                    }
                }

                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }

            MotionEvent.ACTION_CANCEL -> {
                /*
                    ACTION_CANCEL means Android interrupted the gesture.
                    Release the parent scroll lock.
                */
                parent?.requestDisallowInterceptTouchEvent(false)
                return true
            }
        }

        return true
    }

    override fun performClick(): Boolean {
        /*
            Calling super keeps accessibility click behavior intact.
        */
        super.performClick()
        return true
    }

    private fun drawInstructions(canvas: Canvas) {
        /*
            Draw a small instruction line at the top of the custom map.
        */
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
        /*
            mapRect() defines the simplified "world map" drawing area.
        */
        val plot = mapRect()

        /*
            Fill the map background.
        */
        mapPaint.style = Paint.Style.FILL
        mapPaint.color = if (highContrast) Color.rgb(10, 10, 10) else Color.rgb(205, 226, 235)
        canvas.drawRoundRect(plot, dp(18).toFloat(), dp(18).toFloat(), mapPaint)

        /*
            Draw the border around the map.
        */
        mapPaint.style = Paint.Style.STROKE
        mapPaint.strokeWidth = dp(2).toFloat()
        mapPaint.color = if (highContrast) Color.WHITE else Color.rgb(80, 100, 110)
        canvas.drawRoundRect(plot, dp(18).toFloat(), dp(18).toFloat(), mapPaint)

        /*
            Draw longitude/latitude grid lines. These give the map a geographic
            reference even though it is simplified.
        */
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

        /*
            Highlight the equator and prime meridian slightly more strongly.
        */
        gridPaint.strokeWidth = dp(2).toFloat()
        gridPaint.color = if (highContrast) Color.YELLOW else Color.rgb(90, 120, 135)

        val equatorY = latitudeToY(0.0)
        val primeMeridianX = longitudeToX(0.0)

        canvas.drawLine(plot.left, equatorY, plot.right, equatorY, gridPaint)
        canvas.drawLine(primeMeridianX, plot.top, primeMeridianX, plot.bottom, gridPaint)

        /*
            Label the map so users understand it is not a detailed street map.
        */
        textPaint.color = if (highContrast) Color.WHITE else Color.rgb(50, 60, 65)
        textPaint.textSize = dp(12).toFloat()
        textPaint.textAlign = Paint.Align.LEFT

        canvas.drawText("Simplified world view", plot.left + dp(12), plot.top + dp(22), textPaint)
    }

    private fun drawQuakeMarkers(canvas: Canvas) {
        /*
            Draw each earthquake as a circle.

            Marker position comes from latitude/longitude.
            Marker size and color are based on magnitude.
        */
        quakes.forEach { quake ->
            val x = longitudeToX(quake.longitude)
            val y = latitudeToY(quake.latitude)
            val radius = markerRadius(quake.magnitude)
            val selected = quake.id == selectedQuakeId

            /*
                Fill marker.
            */
            markerPaint.style = Paint.Style.FILL
            markerPaint.color = markerColor(quake.magnitude)
            canvas.drawCircle(x, y, radius, markerPaint)

            /*
                Marker outline.
            */
            markerPaint.style = Paint.Style.STROKE
            markerPaint.strokeWidth = dp(1.5f)
            markerPaint.color = if (highContrast) Color.WHITE else Color.rgb(45, 45, 45)
            canvas.drawCircle(x, y, radius, markerPaint)

            /*
                Extra ring around the selected earthquake.
            */
            if (selected) {
                selectedPaint.style = Paint.Style.STROKE
                selectedPaint.strokeWidth = dp(3).toFloat()
                selectedPaint.color = if (highContrast) Color.YELLOW else Color.rgb(30, 80, 180)
                canvas.drawCircle(x, y, radius + dp(7), selectedPaint)
            }
        }
    }

    private fun findNearestQuake(screenX: Float, screenY: Float): QuakeEvent? {
        /*
            Touch coordinates arrive in screen/view space.

            But the map markers are drawn after applying zoom and pan. So to
            compare the tap position with marker positions, we reverse the
            transformation and convert the tap back into base map coordinates.
        */
        val baseX = ((screenX - width / 2f - offsetX) / scaleFactor) + width / 2f
        val baseY = ((screenY - height / 2f - offsetY) / scaleFactor) + height / 2f

        var nearest: QuakeEvent? = null
        var nearestDistance = Float.MAX_VALUE

        /*
            Find the marker closest to the tap.
        */
        quakes.forEach { quake ->
            val quakeX = longitudeToX(quake.longitude)
            val quakeY = latitudeToY(quake.latitude)
            val distance = hypot(baseX - quakeX, baseY - quakeY)

            if (distance < nearestDistance) {
                nearestDistance = distance
                nearest = quake
            }
        }

        /*
            Only return a marker if the tap was close enough.
            This prevents random taps far away from selecting something.
        */
        return if (nearestDistance <= dp(36)) nearest else null
    }

    private fun markerRadius(magnitude: Double): Float {
        /*
            Larger earthquakes get larger markers.

            coerceIn prevents markers from becoming too tiny or too huge.
        */
        return (dp(6) + magnitude.toFloat() * dp(2.2f)).coerceIn(dp(8f), dp(24f))
    }

    private fun markerColor(magnitude: Double): Int {
        /*
            Color gives another visual hint about magnitude.

            High contrast mode avoids subtle colors and uses stronger contrast.
        */
        return when {
            highContrast && magnitude >= 5.0 -> Color.RED
            highContrast -> Color.YELLOW
            magnitude >= 5.0 -> Color.rgb(210, 60, 45)
            magnitude >= 4.0 -> Color.rgb(230, 145, 45)
            else -> Color.rgb(70, 130, 210)
        }
    }

    private fun mapRect(): RectF {
        /*
            The map does not fill the entire View. Leaving space at the top keeps
            room for the instruction text.
        */
        return RectF(
            width * 0.07f,
            height * 0.16f,
            width * 0.93f,
            height * 0.86f
        )
    }

    private fun longitudeToX(longitude: Double): Float {
        /*
            Convert longitude from -180..180 to an x position inside mapRect.
        */
        val plot = mapRect()
        val normalized = ((longitude + 180.0) / 360.0).coerceIn(0.0, 1.0)
        return (plot.left + normalized * plot.width()).toFloat()
    }

    private fun latitudeToY(latitude: Double): Float {
        /*
            Convert latitude from 90..-90 to a y position inside mapRect.

            Y coordinates increase downward on Android canvases, so 90 degrees
            north should be near the top, and -90 degrees south near the bottom.
        */
        val plot = mapRect()
        val normalized = ((90.0 - latitude) / 180.0).coerceIn(0.0, 1.0)
        return (plot.top + normalized * plot.height()).toFloat()
    }

    private fun dp(value: Int): Int {
        /*
            Convert density-independent pixels to actual pixels.
        */
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun dp(value: Float): Float {
        /*
            Float version of the same conversion, useful for stroke widths and
            marker radius calculations.
        */
        return value * resources.displayMetrics.density
    }
}