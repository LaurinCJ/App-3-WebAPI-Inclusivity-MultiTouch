package com.android.example.webapi_inclusivity_multitouch

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {
    /*
        These lateinit variables store references to views from activity_main.xml.

        lateinit is safe here because each variable is assigned in onCreate()
        after setContentView() loads the XML layout.
    */
    private lateinit var mainContent: LinearLayout
    private lateinit var quakeMapView: QuakeMapView
    private lateinit var statusText: TextView
    private lateinit var dataSummaryText: TextView
    private lateinit var currentFilterText: TextView
    private lateinit var selectedQuakeDetails: TextView
    private lateinit var quakeListContainer: LinearLayout
    private lateinit var highContrastSwitch: SwitchCompat
    private lateinit var largeTextSwitch: SwitchCompat

    private lateinit var filterAllButton: Button
    private lateinit var filter25Button: Button
    private lateinit var filter40Button: Button
    private lateinit var filter50Button: Button

    /*
        quakeEvents stores the full data set currently loaded.

        visibleQuakeEvents stores the smaller filtered data set currently shown
        on the map and in the list.
    */
    private var quakeEvents: List<QuakeEvent> = emptyList()
    private var visibleQuakeEvents: List<QuakeEvent> = emptyList()

    /*
        selectedQuake tracks the event currently selected from the map or list.
    */
    private var selectedQuake: QuakeEvent? = null

    /*
        This controls which events are visible.

        0.0 means "All".
        2.5 means only earthquakes magnitude 2.5 and higher.
        4.0 means only earthquakes magnitude 4.0 and higher.
        5.0 means only earthquakes magnitude 5.0 and higher.
    */
    private var minimumMagnitudeFilter = 0.0

    /*
        USGS all_day GeoJSON feed. This gives us recent live earthquake data
        for the Web API requirement.
    */
    private val usgsFeedUrl =
        "https://earthquake.usgs.gov/earthquakes/feed/v1.0/summary/all_day.geojson"

    /*
        Sample earthquakes are still useful because they make the app usable
        immediately and provide a fallback if the live API request fails.
    */
    private val sampleQuakes = listOf(
        QuakeEvent(
            id = "sample-1",
            place = "Southern Alaska",
            magnitude = 4.8,
            timeText = "Sample event • 14 minutes ago",
            latitude = 61.21,
            longitude = -149.90,
            depthKm = 35.0
        ),
        QuakeEvent(
            id = "sample-2",
            place = "Near the coast of central Chile",
            magnitude = 5.6,
            timeText = "Sample event • 42 minutes ago",
            latitude = -33.45,
            longitude = -71.66,
            depthKm = 22.0
        ),
        QuakeEvent(
            id = "sample-3",
            place = "Honshu, Japan region",
            magnitude = 4.3,
            timeText = "Sample event • 1 hour ago",
            latitude = 38.27,
            longitude = 142.82,
            depthKm = 48.0
        ),
        QuakeEvent(
            id = "sample-4",
            place = "Central California",
            magnitude = 3.1,
            timeText = "Sample event • 2 hours ago",
            latitude = 36.77,
            longitude = -119.42,
            depthKm = 12.0
        ),
        QuakeEvent(
            id = "sample-5",
            place = "New Zealand region",
            magnitude = 4.9,
            timeText = "Sample event • 3 hours ago",
            latitude = -41.29,
            longitude = 174.78,
            depthKm = 30.0
        )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        /*
            Let the app draw edge-to-edge, then apply system bar padding below.
        */
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        /*
            This keeps content from being hidden by the status bar or bottom
            navigation controls.
        */
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        /*
            Connect Kotlin variables to XML views.
        */
        mainContent = findViewById(R.id.mainContent)
        quakeMapView = findViewById(R.id.quakeMapView)
        statusText = findViewById(R.id.statusText)
        dataSummaryText = findViewById(R.id.dataSummaryText)
        currentFilterText = findViewById(R.id.currentFilterText)
        selectedQuakeDetails = findViewById(R.id.selectedQuakeDetails)
        quakeListContainer = findViewById(R.id.quakeListContainer)
        highContrastSwitch = findViewById(R.id.highContrastSwitch)
        largeTextSwitch = findViewById(R.id.largeTextSwitch)

        filterAllButton = findViewById(R.id.filterAllButton)
        filter25Button = findViewById(R.id.filter25Button)
        filter40Button = findViewById(R.id.filter40Button)
        filter50Button = findViewById(R.id.filter50Button)

        /*
            The custom map reports marker taps back to MainActivity through this
            callback.
        */
        quakeMapView.onQuakeSelected = { quake ->
            selectQuake(quake)
        }

        findViewById<Button>(R.id.resetMapButton).setOnClickListener {
            quakeMapView.resetView()
        }

        findViewById<Button>(R.id.refreshDataButton).setOnClickListener {
            loadLiveEarthquakeData()
        }

        /*
            Filter buttons change the minimum visible earthquake magnitude.
            The same filter is applied to both the map and the list.
        */
        filterAllButton.setOnClickListener {
            applyMagnitudeFilter(0.0)
        }

        filter25Button.setOnClickListener {
            applyMagnitudeFilter(2.5)
        }

        filter40Button.setOnClickListener {
            applyMagnitudeFilter(4.0)
        }

        filter50Button.setOnClickListener {
            applyMagnitudeFilter(5.0)
        }

        /*
            These switches change display/accessibility behavior without
            reloading data.
        */
        highContrastSwitch.setOnCheckedChangeListener { _, _ ->
            updateInclusiveDisplay()
        }

        largeTextSwitch.setOnCheckedChangeListener { _, _ ->
            updateInclusiveDisplay()
        }

        /*
            Start with sample data so the app immediately shows a useful UI.
            Then attempt to replace it with live data.
        */
        showQuakeEvents(sampleQuakes)
        statusText.text = getString(R.string.sample_data_loaded)

        loadLiveEarthquakeData()
        updateInclusiveDisplay()
    }

    private fun loadLiveEarthquakeData() {
        statusText.text = getString(R.string.loading_live_data)

        /*
            Network requests cannot run on Android's main UI thread.
            This background thread performs the HTTP request and JSON parsing.
        */
        thread {
            try {
                val liveEvents = fetchEarthquakesFromUsgs()

                /*
                    UI changes must happen on the UI thread.
                */
                runOnUiThread {
                    if (liveEvents.isEmpty()) {
                        statusText.text = getString(R.string.no_live_quakes)
                        showQuakeEvents(emptyList())
                    } else {
                        statusText.text = getString(R.string.live_data_loaded)
                        showQuakeEvents(liveEvents)
                    }

                    updateInclusiveDisplay()
                }
            } catch (exception: Exception) {
                /*
                    If the request fails, fall back to sample data instead of
                    leaving the app empty.
                */
                runOnUiThread {
                    statusText.text = getString(R.string.live_data_failed)
                    showQuakeEvents(sampleQuakes)
                    updateInclusiveDisplay()
                }
            }
        }
    }

    private fun fetchEarthquakesFromUsgs(): List<QuakeEvent> {
        /*
            HttpURLConnection is built into Android/Java, so it lets us complete
            the Web API requirement without adding another dependency.
        */
        val connection = URL(usgsFeedUrl).openConnection() as HttpURLConnection

        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IllegalStateException("HTTP error ${connection.responseCode}")
            }

            val jsonText = connection.inputStream.bufferedReader().use { reader ->
                reader.readText()
            }

            parseEarthquakeGeoJson(jsonText)
        } finally {
            connection.disconnect()
        }
    }

    private fun parseEarthquakeGeoJson(jsonText: String): List<QuakeEvent> {
        /*
            The USGS GeoJSON feed stores earthquake events in a "features" array.
        */
        val root = JSONObject(jsonText)
        val features = root.getJSONArray("features")
        val events = mutableListOf<QuakeEvent>()

        /*
            Limit the app to 30 events so the map/list remain readable.
        */
        val maxEventsToShow = minOf(features.length(), 30)

        for (index in 0 until maxEventsToShow) {
            val feature = features.getJSONObject(index)
            val properties = feature.getJSONObject("properties")
            val geometry = feature.getJSONObject("geometry")
            val coordinates = geometry.getJSONArray("coordinates")

            /*
                USGS coordinate order is:
                [longitude, latitude, depth]
            */
            val id = feature.optString("id", "event-$index")
            val place = properties.optString("place", "Unknown location")

            val rawMagnitude = properties.optDouble("mag", 0.0)
            val magnitude = if (rawMagnitude.isNaN()) 0.0 else rawMagnitude

            val timeMillis = properties.optLong("time", 0L)
            val timeText = formatTime(timeMillis)

            val longitude = coordinates.optDouble(0, 0.0)
            val latitude = coordinates.optDouble(1, 0.0)
            val depthKm = coordinates.optDouble(2, 0.0)

            val eventUrl = properties.optString("url", "")

            events.add(
                QuakeEvent(
                    id = id,
                    place = place,
                    magnitude = magnitude,
                    timeText = timeText,
                    latitude = latitude,
                    longitude = longitude,
                    depthKm = depthKm,
                    eventUrl = eventUrl
                )
            )
        }

        /*
            Stronger earthquakes are usually more important to users, so they
            appear first.
        */
        return events.sortedByDescending { it.magnitude }
    }

    private fun formatTime(timeMillis: Long): String {
        if (timeMillis <= 0L) {
            return "Time not available"
        }

        val formatter = SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.US)
        return "USGS reported ${formatter.format(Date(timeMillis))}"
    }

    private fun showQuakeEvents(events: List<QuakeEvent>) {
        /*
            Store the complete loaded dataset, then apply the active filter.
        */
        quakeEvents = events
        applyCurrentFilter(selectFirstVisibleEvent = true)
    }

    private fun applyMagnitudeFilter(minimumMagnitude: Double) {
        /*
            Update filter state, then rebuild the visible map/list from the full
            loaded dataset.
        */
        minimumMagnitudeFilter = minimumMagnitude
        applyCurrentFilter(selectFirstVisibleEvent = true)
    }

    private fun applyCurrentFilter(selectFirstVisibleEvent: Boolean) {
        /*
            Filtering happens from quakeEvents, which is the full loaded data set.
            The result becomes visibleQuakeEvents, which powers the map/list.
        */
        visibleQuakeEvents = if (minimumMagnitudeFilter <= 0.0) {
            quakeEvents
        } else {
            quakeEvents.filter { quake ->
                quake.magnitude >= minimumMagnitudeFilter
            }
        }

        /*
            Update the custom map with only the visible events.
        */
        quakeMapView.setQuakes(visibleQuakeEvents)

        updateSummary()
        updateFilterButtons()

        /*
            Try to keep the same selected quake when possible.
            If the selected quake is filtered out, choose the first visible one.
        */
        val selectedStillVisible = selectedQuake?.let { selected ->
            visibleQuakeEvents.any { quake -> quake.id == selected.id }
        } == true

        val quakeToSelect = when {
            visibleQuakeEvents.isEmpty() -> null
            selectFirstVisibleEvent -> visibleQuakeEvents.first()
            selectedStillVisible -> selectedQuake
            else -> visibleQuakeEvents.first()
        }

        if (quakeToSelect != null) {
            selectQuake(quakeToSelect)
        } else {
            selectedQuake = null
            selectedQuakeDetails.text = getString(R.string.no_quake_selected)
            selectedQuakeDetails.contentDescription = getString(R.string.no_quake_selected)
            renderQuakeList(visibleQuakeEvents)
        }
    }

    private fun selectQuake(quake: QuakeEvent) {
        /*
            Selection is shared by the map, details card, and list row.
        */
        selectedQuake = quake
        quakeMapView.selectQuake(quake)

        selectedQuakeDetails.text = formatQuakeDetails(quake)
        selectedQuakeDetails.contentDescription = formatQuakeDetails(quake)

        renderQuakeList(visibleQuakeEvents)
    }

    private fun renderQuakeList(events: List<QuakeEvent>) {
        /*
            This app uses a simple LinearLayout list.

            Since we only show up to 30 earthquakes, rebuilding the list is fine.
            A much larger app would probably use RecyclerView instead.
        */
        quakeListContainer.removeAllViews()

        if (events.isEmpty()) {
            val emptyMessage = TextView(this).apply {
                text = getString(R.string.no_live_quakes)
                textSize = if (largeTextSwitch.isChecked) 20f else 16f
                setTextColor(if (highContrastSwitch.isChecked) Color.WHITE else Color.rgb(32, 32, 32))
                setPadding(dp(16), dp(14), dp(16), dp(14))
                background = createRowBackground(false)
            }

            quakeListContainer.addView(
                emptyMessage,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )

            return
        }

        events.forEach { quake ->
            val isSelected = quake.id == selectedQuake?.id

            val row = TextView(this).apply {
                text = getString(R.string.quake_list_item, quake.magnitude, quake.place)

                contentDescription = getString(
                    R.string.quake_list_item_content_description,
                    quake.magnitude,
                    quake.place
                )

                textSize = if (largeTextSwitch.isChecked) 20f else 16f
                typeface = if (isSelected) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
                setTextColor(if (highContrastSwitch.isChecked) Color.WHITE else Color.rgb(32, 32, 32))
                setPadding(dp(16), dp(14), dp(16), dp(14))
                background = createRowBackground(isSelected)

                isClickable = true
                isFocusable = true

                setOnClickListener {
                    selectQuake(quake)
                }
            }

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dp(8)
            }

            quakeListContainer.addView(row, params)
        }
    }

    private fun updateSummary() {
        /*
            The summary turns raw API data into quick useful information.
            It also gives screen-reader users a text-based overview of the data.
        */
        val summaryText = when {
            quakeEvents.isEmpty() -> {
                getString(R.string.summary_no_events)
            }

            visibleQuakeEvents.isEmpty() -> {
                getString(R.string.summary_no_visible, quakeEvents.size)
            }

            else -> {
                val strongest = quakeEvents.maxByOrNull { quake -> quake.magnitude }
                val averageDepth = quakeEvents.map { quake -> quake.depthKm }.average()

                getString(
                    R.string.summary_loaded,
                    quakeEvents.size,
                    visibleQuakeEvents.size,
                    strongest?.magnitude ?: 0.0,
                    strongest?.place ?: "Unknown location",
                    averageDepth
                )
            }
        }

        dataSummaryText.text = summaryText
        dataSummaryText.contentDescription = summaryText

        currentFilterText.text = getString(R.string.current_filter, filterLabel())
        currentFilterText.contentDescription = currentFilterText.text
    }

    private fun updateFilterButtons() {
        /*
            Keep the filter buttons visually synced with the active filter.
        */
        styleFilterButton(filterAllButton, minimumMagnitudeFilter == 0.0)
        styleFilterButton(filter25Button, minimumMagnitudeFilter == 2.5)
        styleFilterButton(filter40Button, minimumMagnitudeFilter == 4.0)
        styleFilterButton(filter50Button, minimumMagnitudeFilter == 5.0)
    }

    private fun styleFilterButton(button: Button, isSelected: Boolean) {
        /*
            Filter buttons use a custom background so the selected state is shown
            with a border instead of a filled purple button.

            Important detail:
            backgroundTintList must be set to null. If we set it to transparent,
            Android tints the whole custom drawable transparent, which hides both
            the light gray fill and the border.
        */
        val highContrast = highContrastSwitch.isChecked

        val fillColor = if (highContrast) {
            Color.BLACK
        } else {
            Color.rgb(235, 235, 235)
        }

        val textColor = if (highContrast) {
            Color.WHITE
        } else {
            Color.BLACK
        }

        val strokeColor = when {
            highContrast && isSelected -> Color.YELLOW
            highContrast -> Color.WHITE
            isSelected -> Color.rgb(103, 58, 183)
            else -> Color.rgb(185, 185, 185)
        }

        val strokeWidth = if (isSelected) {
            dp(4)
        } else {
            dp(1)
        }

        button.backgroundTintList = null
        button.setTextColor(textColor)
        button.textSize = if (largeTextSwitch.isChecked) 22f else 16f
        button.minHeight = if (largeTextSwitch.isChecked) dp(64) else dp(56)
        button.setPadding(dp(8), dp(6), dp(8), dp(6))
        button.isAllCaps = false

        button.background = GradientDrawable().apply {
            cornerRadius = dp(10).toFloat()
            setColor(fillColor)
            setStroke(strokeWidth, strokeColor)
        }

        button.backgroundTintList = null
    }

    private fun styleMainActionButton(button: Button) {
        /*
            Main action buttons are the Refresh and Reset buttons.

            Large text mode should affect buttons too, not just TextViews. A 48dp
            button with 15sp text still feels small, so large text mode uses 24sp
            and slightly taller buttons for readability.
        */
        button.textSize = if (largeTextSwitch.isChecked) 24f else 12f
        button.minHeight = if (largeTextSwitch.isChecked) dp(64) else dp(48)
        button.setPadding(dp(8), dp(6), dp(8), dp(6))
    }

    private fun filterLabel(): String {
        /*
            Convert the numeric filter state into a user-facing label.
        */
        return when (minimumMagnitudeFilter) {
            2.5 -> getString(R.string.filter_2_5)
            4.0 -> getString(R.string.filter_4_0)
            5.0 -> getString(R.string.filter_5_0)
            else -> getString(R.string.filter_all)
        }
    }

    private fun updateInclusiveDisplay() {
        val highContrast = highContrastSwitch.isChecked
        val largeText = largeTextSwitch.isChecked

        quakeMapView.setHighContrast(highContrast)

        mainContent.setBackgroundColor(if (highContrast) Color.BLACK else Color.rgb(247, 244, 238))

        statusText.setBackgroundColor(if (highContrast) Color.rgb(25, 25, 25) else Color.WHITE)
        statusText.setTextColor(if (highContrast) Color.WHITE else Color.rgb(63, 58, 52))
        statusText.textSize = if (largeText) 19f else 15f

        dataSummaryText.setBackgroundColor(if (highContrast) Color.rgb(25, 25, 25) else Color.WHITE)
        dataSummaryText.setTextColor(if (highContrast) Color.WHITE else Color.rgb(42, 42, 42))
        dataSummaryText.textSize = if (largeText) 20f else 16f

        currentFilterText.setTextColor(if (highContrast) Color.WHITE else Color.rgb(63, 58, 52))
        currentFilterText.textSize = if (largeText) 19f else 15f

        selectedQuakeDetails.setBackgroundColor(if (highContrast) Color.rgb(25, 25, 25) else Color.WHITE)
        selectedQuakeDetails.setTextColor(if (highContrast) Color.WHITE else Color.rgb(42, 42, 42))
        selectedQuakeDetails.textSize = if (largeText) 20f else 16f

        val titleColor = if (highContrast) Color.WHITE else Color.rgb(31, 31, 31)
        val bodyColor = if (highContrast) Color.WHITE else Color.rgb(63, 58, 52)

        val titleIds = listOf(
            R.id.appTitle,
            R.id.dataSummaryTitle,
            R.id.filterTitle,
            R.id.selectedQuakeTitle,
            R.id.inclusivityTitle,
            R.id.recentQuakesTitle
        )

        titleIds.forEach { id ->
            findViewById<TextView>(id).setTextColor(titleColor)
        }

        findViewById<TextView>(R.id.appSubtitle).apply {
            setTextColor(bodyColor)
            textSize = if (largeText) 20f else 16f
        }

        highContrastSwitch.setTextColor(bodyColor)
        largeTextSwitch.setTextColor(bodyColor)

        findViewById<TextView>(R.id.appTitle).textSize = if (largeText) 34f else 30f
        findViewById<TextView>(R.id.dataSummaryTitle).textSize = if (largeText) 24f else 20f
        findViewById<TextView>(R.id.filterTitle).textSize = if (largeText) 24f else 20f
        findViewById<TextView>(R.id.selectedQuakeTitle).textSize = if (largeText) 24f else 20f
        findViewById<TextView>(R.id.inclusivityTitle).textSize = if (largeText) 24f else 20f
        findViewById<TextView>(R.id.recentQuakesTitle).textSize = if (largeText) 24f else 20f

        selectedQuakeDetails.text = selectedQuake?.let { formatQuakeDetails(it) }
            ?: getString(R.string.no_quake_selected)

        updateSummary()
        styleMainActionButton(findViewById(R.id.refreshDataButton))
        styleMainActionButton(findViewById(R.id.resetMapButton))
        updateFilterButtons()
        renderQuakeList(visibleQuakeEvents)
    }

    private fun formatQuakeDetails(quake: QuakeEvent): String {
        val urlText = if (quake.eventUrl.isBlank()) {
            "Source URL not available"
        } else {
            "Source: ${quake.eventUrl}"
        }

        return String.format(
            Locale.US,
            "Magnitude %.1f\n%s\n%s\nLatitude %.2f, Longitude %.2f\nDepth %.1f km\n%s",
            quake.magnitude,
            quake.place,
            quake.timeText,
            quake.latitude,
            quake.longitude,
            quake.depthKm,
            urlText
        )
    }

    private fun createRowBackground(isSelected: Boolean): GradientDrawable {
        val highContrast = highContrastSwitch.isChecked

        val fillColor = when {
            highContrast && isSelected -> Color.rgb(45, 45, 0)
            highContrast -> Color.rgb(18, 18, 18)
            isSelected -> Color.rgb(255, 242, 204)
            else -> Color.WHITE
        }

        val strokeColor = when {
            highContrast && isSelected -> Color.YELLOW
            highContrast -> Color.WHITE
            isSelected -> Color.rgb(160, 100, 0)
            else -> Color.rgb(210, 205, 195)
        }

        return GradientDrawable().apply {
            cornerRadius = dp(12).toFloat()
            setColor(fillColor)
            setStroke(dp(2), strokeColor)
        }
    }

    private fun dp(value: Int): Int {
        /*
            Convert density-independent pixels to real pixels for programmatic
            padding, borders, and rounded corners.
        */
        return (value * resources.displayMetrics.density).toInt()
    }
}