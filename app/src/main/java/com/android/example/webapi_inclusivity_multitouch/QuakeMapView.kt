package com.android.example.webapi_inclusivity_multitouch

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import kotlin.math.hypot

/*
    QuakeMapView is a custom Android View.

    This view draws a fixed "window" or viewport. Inside that viewport, it draws
    the Mercator world map image and earthquake markers.

    The important interaction design idea is:
    - the box stays still
    - the map image moves inside the box
    - the earthquake markers move with the map image
    - the image is clipped so it never draws outside the map box

    This makes panning feel like looking around inside a map window instead of
    sliding the entire UI element around the screen.
*/
class QuakeMapView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : View(context, attrs) {

    /*
        This callback lets MainActivity know when the user taps a marker.

        The custom View handles touch detection and drawing. MainActivity handles
        updating the selected earthquake details and list.
    */
    var onQuakeSelected: ((QuakeEvent) -> Unit)? = null

    /*
        Internal earthquake list currently displayed on the map.
    */
    private val quakes = mutableListOf<QuakeEvent>()

    /*
        The selected earthquake id is used to draw a highlight ring and label.
    */
    private var selectedQuakeId: String? = null

    /*
        High contrast mode changes marker/label colors and darkens the map area.
    */
    private var highContrast = false

    /*
        The map starts slightly zoomed in so the world image feels less tiny inside
        the viewport.

        MIN_SCALE is the normal reset/default view.
        MAX_SCALE is the farthest the user can pinch zoom.
    */
    private val defaultScaleFactor = 1.05f
    private val minScaleFactor = 1.05f
    private val maxScaleFactor = 5f

    /*
        This bitmap is the Mercator projection image stored in res/drawable.

        The image is drawn into a destination rectangle that can be zoomed and
        panned inside the fixed map box.
    */
    private val mapBitmap: Bitmap = BitmapFactory.decodeResource(
        resources,
        R.drawable.mercator_projection_square
    )

    /*
        Source rectangle for the whole bitmap image.
    */
    private val bitmapSourceRect = Rect(0, 0, mapBitmap.width, mapBitmap.height)

    /*
        scaleFactor controls zoom.

        1f means the image is fitted to the normal map box.
        Larger values zoom in by drawing the image larger than the box.
    */
    private var scaleFactor = defaultScaleFactor

    /*
        offsetX and offsetY pan the image inside the fixed viewport.

        These values are clamped so the user cannot slide the map infinitely.
    */
    private var offsetX = 0f
    private var offsetY = 0f

    /*
        These touch variables help separate tapping from dragging.
    */
    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var totalMove = 0f

    /*
        Paint objects store drawing settings.

        Reusing Paint objects avoids repeatedly creating new objects inside
        onDraw(), which can hurt performance.
    */
    private val mapPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val bitmapPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        /*
            These settings make the JPG look smoother when Android scales it during
            zooming and panning.

            This does not add image detail that is not in the file, but it reduces
            harsh pixel edges and makes scaling look cleaner.
        */
        isFilterBitmap = true
        isDither = true
    }

    private val markerPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val selectedPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    /*
        ScaleGestureDetector handles the two-finger pinch gesture.

        Android gives us raw MotionEvents, and ScaleGestureDetector interprets
        them as zoom changes.
    */
    private val scaleDetector = ScaleGestureDetector(
        context,
        object : ScaleGestureDetector.SimpleOnScaleGestureListener() {
            override fun onScale(detector: ScaleGestureDetector): Boolean {
                scaleFactor = (scaleFactor * detector.scaleFactor).coerceIn(minScaleFactor, maxScaleFactor)
                clampPanOffsets()
                invalidate()
                return true
            }
        }
    )

    init {
        /*
            These make the custom View behave like an interactive UI element.
        */
        isClickable = true
        isFocusable = true
    }

    fun setQuakes(newQuakes: List<QuakeEvent>) {
        /*
            Replace the map's data with the current visible earthquake list.
        */
        quakes.clear()
        quakes.addAll(newQuakes)

        contentDescription =
            "Interactive earthquake map showing ${quakes.size} earthquake events. Pinch to zoom, drag to pan, and tap a marker for details."

        invalidate()
    }

    fun selectQuake(quake: QuakeEvent) {
        /*
            Store which earthquake should be highlighted.
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
            Return the map image to the centered, unzoomed starting position.
        */
        scaleFactor = defaultScaleFactor
        offsetX = 0f
        offsetY = 0f
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        /*
            Draw the overall custom view background.
        */
        canvas.drawColor(if (highContrast) Color.BLACK else Color.rgb(230, 239, 244))

        /*
            These UI hints stay outside the panned/zoomed map content, so they
            remain readable no matter where the user moves the map.
        */
        drawInstructions(canvas)
        drawScreenLegend(canvas)

        /*
            Draw the fixed map viewport and all content inside it.
        */
        drawMapViewport(canvas)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        /*
            Prevent the surrounding NestedScrollView from stealing gestures while
            the user is interacting with the map.
        */
        parent?.requestDisallowInterceptTouchEvent(true)

        /*
            Always pass touch events to the scale detector so it can recognize
            two-finger pinch zoom.
        */
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
                /*
                    One-finger movement pans the map image inside the viewport.
                    During pinch zoom, we do not also treat movement as a pan.
                */
                if (event.pointerCount == 1 && !scaleDetector.isInProgress) {
                    val dx = event.x - lastX
                    val dy = event.y - lastY

                    offsetX += dx
                    offsetY += dy

                    clampPanOffsets()

                    totalMove += hypot(dx, dy)
                    lastX = event.x
                    lastY = event.y

                    invalidate()
                }
                return true
            }

            MotionEvent.ACTION_UP -> {
                /*
                    A tiny movement is treated as a tap. A larger movement is
                    treated as a pan.
                */
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
        /*
            Calling super supports accessibility behavior for custom clickable
            views.
        */
        super.performClick()
        return true
    }

    private fun drawMapViewport(canvas: Canvas) {
        val viewport = mapRect()
        val content = mapContentRect()

        /*
            Draw a background behind the map image. If the user pans far enough
            that an image edge reaches the center, this background shows in the
            empty part of the viewport.
        */
        mapPaint.style = Paint.Style.FILL
        mapPaint.color = if (highContrast) Color.rgb(5, 5, 5) else Color.rgb(185, 210, 220)
        canvas.drawRoundRect(viewport, dp(14).toFloat(), dp(14).toFloat(), mapPaint)

        /*
            Clip everything inside the viewport. This is the "mask" behavior:
            the map image and markers can move, but they cannot draw outside the
            fixed map box.
        */
        canvas.save()
        canvas.clipRect(viewport)

        /*
            In high contrast mode, the bitmap is dimmed slightly so the bright
            marker colors and labels remain easy to see.
        */
        bitmapPaint.alpha = if (highContrast) 135 else 255

        canvas.drawBitmap(mapBitmap, bitmapSourceRect, content, bitmapPaint)

        /*
            Markers are drawn after the bitmap so they appear on top of the map.
            Because marker positions use the same content rectangle, they pan and
            zoom with the map image.
        */
        drawQuakeMarkers(canvas)

        canvas.restore()

        /*
            Draw the viewport border after restoring so the border is always
            visible and not clipped weirdly.
        */
        mapPaint.style = Paint.Style.STROKE
        mapPaint.strokeWidth = dp(2).toFloat()
        mapPaint.color = if (highContrast) Color.WHITE else Color.rgb(55, 75, 85)
        canvas.drawRoundRect(viewport, dp(14).toFloat(), dp(14).toFloat(), mapPaint)
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
            markerPaint.color = if (highContrast) Color.WHITE else Color.rgb(30, 30, 30)
            canvas.drawCircle(x, y, radius, markerPaint)

            if (selected) {
                selectedPaint.style = Paint.Style.STROKE
                selectedPaint.strokeWidth = dp(3).toFloat()
                selectedPaint.color = if (highContrast) Color.YELLOW else Color.rgb(103, 58, 183)
                canvas.drawCircle(x, y, radius + dp(7), selectedPaint)

                drawSelectedMarkerLabel(canvas, quake, x, y, radius)
            }
        }
    }

    private fun drawInstructions(canvas: Canvas) {
        textPaint.color = if (highContrast) Color.WHITE else Color.rgb(35, 35, 35)
        textPaint.textSize = dp(13).toFloat()
        textPaint.textAlign = Paint.Align.CENTER

        canvas.drawText(
            "Pinch to zoom • Drag the map • Tap a marker",
            width / 2f,
            dp(24).toFloat(),
            textPaint
        )
    }

    private fun drawScreenLegend(canvas: Canvas) {
        /*
            The legend is drawn outside the map transform so it always stays
            readable.
        */
        val legendLeft = dp(12).toFloat()
        val legendTop = dp(42).toFloat()
        val lineHeight = dp(17).toFloat()

        textPaint.textAlign = Paint.Align.LEFT
        textPaint.textSize = dp(11).toFloat()
        textPaint.color = if (highContrast) Color.WHITE else Color.rgb(35, 35, 35)

        canvas.drawText("Marker guide:", legendLeft, legendTop, textPaint)

        drawLegendMarker(canvas, legendLeft + dp(8), legendTop + lineHeight, 3.0, "M < 4")
        drawLegendMarker(canvas, legendLeft + dp(8), legendTop + lineHeight * 2, 4.5, "M 4-4.9")
        drawLegendMarker(canvas, legendLeft + dp(8), legendTop + lineHeight * 3, 5.5, "M 5+")
    }

    private fun drawLegendMarker(
        canvas: Canvas,
        x: Float,
        y: Float,
        magnitude: Double,
        label: String
    ) {
        markerPaint.style = Paint.Style.FILL
        markerPaint.color = markerColor(magnitude)
        canvas.drawCircle(x, y - dp(4), dp(5).toFloat(), markerPaint)

        markerPaint.style = Paint.Style.STROKE
        markerPaint.strokeWidth = dp(1).toFloat()
        markerPaint.color = if (highContrast) Color.WHITE else Color.rgb(45, 45, 45)
        canvas.drawCircle(x, y - dp(4), dp(5).toFloat(), markerPaint)

        textPaint.textAlign = Paint.Align.LEFT
        textPaint.textSize = dp(11).toFloat()
        textPaint.color = if (highContrast) Color.WHITE else Color.rgb(35, 35, 35)

        canvas.drawText(label, x + dp(12), y, textPaint)
    }

    private fun drawSelectedMarkerLabel(
        canvas: Canvas,
        quake: QuakeEvent,
        x: Float,
        y: Float,
        radius: Float
    ) {
        /*
            Label the selected marker so the highlighted dot is easier to connect
            to the details section below the map.
        */
        val label = String.format("M%.1f", quake.magnitude)

        textPaint.textAlign = Paint.Align.LEFT
        textPaint.textSize = dp(12).toFloat()
        textPaint.color = if (highContrast) Color.YELLOW else Color.rgb(103, 58, 183)

        canvas.drawText(label, x + radius + dp(8), y - radius - dp(4), textPaint)
    }

    private fun findNearestQuake(screenX: Float, screenY: Float): QuakeEvent? {
        /*
            Because markers are drawn directly into screen coordinates using the
            current mapContentRect(), we can compare the tap location against
            the current marker positions directly.
        */
        var nearest: QuakeEvent? = null
        var nearestDistance = Float.MAX_VALUE

        quakes.forEach { quake ->
            val quakeX = longitudeToX(quake.longitude)
            val quakeY = latitudeToY(quake.latitude)
            val distance = hypot(screenX - quakeX, screenY - quakeY)

            if (distance < nearestDistance) {
                nearestDistance = distance
                nearest = quake
            }
        }

        return if (nearestDistance <= dp(36)) nearest else null
    }

    private fun mapRect(): RectF {
        /*
            This is the fixed viewport rectangle.

            It leaves room above for the instructions and marker legend.
        */
        return RectF(
            width * 0.04f,
            dp(96).toFloat(),
            width * 0.96f,
            height * 0.92f
        )
    }

    private fun mapContentRect(): RectF {
        /*
            This rectangle is where the map bitmap is drawn.

            The viewport stays fixed, but this content rectangle grows/shrinks
            with zoom and moves with pan offsets.
        */
        val viewport = mapRect()
        val contentWidth = viewport.width() * scaleFactor
        val contentHeight = viewport.height() * scaleFactor

        val centerX = viewport.centerX() + offsetX
        val centerY = viewport.centerY() + offsetY

        return RectF(
            centerX - contentWidth / 2f,
            centerY - contentHeight / 2f,
            centerX + contentWidth / 2f,
            centerY + contentHeight / 2f
        )
    }

    private fun clampPanOffsets() {
        /*
            Limit panning so the map cannot slide forever.

            The requested rule was: the farthest the map can move is such that
            the edge of the map can reach the middle of the viewport.

            Since offsetX/offsetY move the center of the map image, the maximum
            allowed offset is half the current content width/height.
        */
        val viewport = mapRect()
        val contentWidth = viewport.width() * scaleFactor
        val contentHeight = viewport.height() * scaleFactor

        val maxOffsetX = contentWidth / 2f
        val maxOffsetY = contentHeight / 2f

        offsetX = offsetX.coerceIn(-maxOffsetX, maxOffsetX)
        offsetY = offsetY.coerceIn(-maxOffsetY, maxOffsetY)
    }

    private fun longitudeToX(longitude: Double): Float {
        /*
            Convert longitude from -180..180 into an x position inside the
            current map image rectangle.
        */
        val content = mapContentRect()
        val normalized = ((longitude + 180.0) / 360.0).coerceIn(0.0, 1.0)
        return (content.left + normalized * content.width()).toFloat()
    }

    private fun latitudeToY(latitude: Double): Float {
        /*
            Convert latitude from 90..-90 into a y position inside the current
            map image rectangle.

            This matches the uploaded square projection image closely enough for
            the project because the image is being used as a visual reference
            map rather than a precision GIS layer.
        */
        val content = mapContentRect()
        val normalized = ((90.0 - latitude) / 180.0).coerceIn(0.0, 1.0)
        return (content.top + normalized * content.height()).toFloat()
    }

    private fun markerRadius(magnitude: Double): Float {
        /*
            Larger earthquakes get larger markers.
        */
        return (dp(6) + magnitude.toFloat() * dp(2.2f)).coerceIn(dp(8f), dp(24f))
    }

    private fun markerColor(magnitude: Double): Int {
        /*
            Marker color gives a quick visual magnitude category.
        */
        return when {
            highContrast && magnitude >= 5.0 -> Color.RED
            highContrast -> Color.YELLOW
            magnitude >= 5.0 -> Color.rgb(210, 60, 45)
            magnitude >= 4.0 -> Color.rgb(230, 145, 45)
            else -> Color.rgb(70, 130, 210)
        }
    }

    private fun dp(value: Int): Int {
        /*
            Convert density-independent pixels to actual pixels.
        */
        return (value * resources.displayMetrics.density).toInt()
    }

    private fun dp(value: Float): Float {
        /*
            Float version used for marker radius and stroke widths.
        */
        return value * resources.displayMetrics.density
    }
}