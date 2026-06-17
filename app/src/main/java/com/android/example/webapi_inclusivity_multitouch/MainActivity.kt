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
        These lateinit variables store references to the views from activity_main.xml.

        lateinit means:
        - we promise these variables will be initialized before we use them
        - they do not need nullable types like TextView?
        - they are assigned in onCreate() after setContentView()
    */
    private lateinit var mainContent: LinearLayout
    private lateinit var quakeMapView: QuakeMapView
    private lateinit var statusText: TextView
    private lateinit var selectedQuakeDetails: TextView
    private lateinit var quakeListContainer: LinearLayout
    private lateinit var highContrastSwitch: SwitchCompat
    private lateinit var largeTextSwitch: SwitchCompat

    /*
        quakeEvents stores whatever list the app is currently showing.

        At first, this is sample data. After the web API succeeds, this becomes
        live USGS data.
    */
    private var quakeEvents: List<QuakeEvent> = emptyList()

    /*
        selectedQuake keeps track of the event currently selected from either:
        - the map marker
        - the list row
    */
    private var selectedQuake: QuakeEvent? = null

    /*
        USGS provides earthquake feeds as GeoJSON.

        This feed contains all earthquakes from the past day. It is useful for
        this project because it changes over time and gives us real data for
        the Web API requirement.
    */
    private val usgsFeedUrl =
        "https://earthquake.usgs.gov/earthquakes/feed/v1.0/summary/all_day.geojson"

    /*
        Sample data is shown immediately so the app is not blank while the
        network request is running.

        It also gives us a fallback if the emulator/device has no internet,
        the USGS feed is temporarily unreachable, or parsing fails.
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
            enableEdgeToEdge() lets the app draw behind system bars.
            The inset listener below adds padding so content does not get hidden
            behind the status/navigation bars.
        */
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        /*
            This handles the system bar area safely.

            Without this, content can appear underneath the top status bar or
            bottom navigation controls on some devices.
        */
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        /*
            Connect Kotlin variables to the XML views.
            After this point, these view references are safe to use.
        */
        mainContent = findViewById(R.id.mainContent)
        quakeMapView = findViewById(R.id.quakeMapView)
        statusText = findViewById(R.id.statusText)
        selectedQuakeDetails = findViewById(R.id.selectedQuakeDetails)
        quakeListContainer = findViewById(R.id.quakeListContainer)
        highContrastSwitch = findViewById(R.id.highContrastSwitch)
        largeTextSwitch = findViewById(R.id.largeTextSwitch)

        /*
            QuakeMapView is a custom View, so it cannot directly update the
            activity's TextViews.

            Instead, the custom View exposes onQuakeSelected. MainActivity
            assigns a function here so the map can tell the activity when a
            marker was tapped.
        */
        quakeMapView.onQuakeSelected = { quake ->
            selectQuake(quake)
        }

        /*
            Reset restores the custom map's zoom and pan state.
        */
        findViewById<Button>(R.id.resetMapButton).setOnClickListener {
            quakeMapView.resetView()
        }

        /*
            Refresh runs the live Web API request again.
        */
        findViewById<Button>(R.id.refreshDataButton).setOnClickListener {
            loadLiveEarthquakeData()
        }

        /*
            These switches do not reload data. They only change how the current
            data is displayed.
        */
        highContrastSwitch.setOnCheckedChangeListener { _, _ ->
            updateInclusiveDisplay()
        }

        largeTextSwitch.setOnCheckedChangeListener { _, _ ->
            updateInclusiveDisplay()
        }

        /*
            Show sample data immediately so the app looks useful even before
            the network request finishes.
        */
        showQuakeEvents(sampleQuakes)
        statusText.text = getString(R.string.sample_data_loaded)

        /*
            Then attempt to replace the sample data with live data.
        */
        loadLiveEarthquakeData()
    }

    private fun loadLiveEarthquakeData() {
        statusText.text = getString(R.string.loading_live_data)

        /*
            Android does not allow network requests on the main UI thread.

            This thread block runs the API request in the background so the UI
            does not freeze while waiting for the internet response.
        */
        thread {
            try {
                val liveEvents = fetchEarthquakesFromUsgs()

                /*
                    UI updates must happen on the main thread.
                    runOnUiThread safely switches back to the UI thread.
                */
                runOnUiThread {
                    if (liveEvents.isEmpty()) {
                        statusText.text = getString(R.string.no_live_quakes)
                        showQuakeEvents(emptyList())
                    } else {
                        statusText.text = getString(R.string.live_data_loaded)
                        showQuakeEvents(liveEvents)
                    }

                    /*
                        Reapply display settings because the list/map contents
                        may have just changed.
                    */
                    updateInclusiveDisplay()
                }
            } catch (exception: Exception) {
                /*
                    If anything goes wrong, the app still remains usable by
                    falling back to sample data.
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
            HttpURLConnection is Android/Java's built-in way to make a basic
            HTTP request without adding another dependency.

            Later, larger apps might use Retrofit, but this is enough for a
            focused course project.
        */
        val connection = URL(usgsFeedUrl).openConnection() as HttpURLConnection

        return try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            /*
                HTTP_OK means status code 200.
                Anything else is treated as a failed request.
            */
            if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                throw IllegalStateException("HTTP error ${connection.responseCode}")
            }

            /*
                Read the entire JSON response into a String.
            */
            val jsonText = connection.inputStream.bufferedReader().use { reader ->
                reader.readText()
            }

            parseEarthquakeGeoJson(jsonText)
        } finally {
            /*
                Always disconnect when finished so the connection is cleaned up.
            */
            connection.disconnect()
        }
    }

    private fun parseEarthquakeGeoJson(jsonText: String): List<QuakeEvent> {
        /*
            The USGS feed is GeoJSON.

            Its top-level object contains an array called "features".
            Each feature represents one earthquake event.
        */
        val root = JSONObject(jsonText)
        val features = root.getJSONArray("features")
        val events = mutableListOf<QuakeEvent>()

        /*
            Limit the displayed results so the list and map stay readable.
            The feed can contain many small earthquakes.
        */
        val maxEventsToShow = minOf(features.length(), 30)

        for (index in 0 until maxEventsToShow) {
            val feature = features.getJSONObject(index)
            val properties = feature.getJSONObject("properties")
            val geometry = feature.getJSONObject("geometry")
            val coordinates = geometry.getJSONArray("coordinates")

            /*
                GeoJSON structure used here:

                feature.id
                feature.properties.place
                feature.properties.mag
                feature.properties.time
                feature.properties.url
                feature.geometry.coordinates = [longitude, latitude, depth]
            */
            val id = feature.optString("id", "event-$index")
            val place = properties.optString("place", "Unknown location")

            /*
                optDouble can produce NaN if the value is missing or invalid.
                Replacing NaN with 0.0 prevents display/math problems.
            */
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
            Sorting by magnitude makes the strongest earthquakes appear first.
            This makes the list more useful than leaving it in feed order.
        */
        return events.sortedByDescending { it.magnitude }
    }

    private fun formatTime(timeMillis: Long): String {
        if (timeMillis <= 0L) {
            return "Time not available"
        }

        /*
            Convert the USGS timestamp into a readable date/time string.
        */
        val formatter = SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.US)
        return "USGS reported ${formatter.format(Date(timeMillis))}"
    }

    private fun showQuakeEvents(events: List<QuakeEvent>) {
        /*
            Update the shared app state first.
        */
        quakeEvents = events

        /*
            Send the same data to both the visual map and the accessible list.
        */
        quakeMapView.setQuakes(events)
        renderQuakeList(events)

        /*
            Select the first event automatically so the details area is useful
            immediately.
        */
        if (events.isNotEmpty()) {
            selectQuake(events.first())
        } else {
            selectedQuake = null
            selectedQuakeDetails.text = getString(R.string.no_quake_selected)
        }
    }

    private fun selectQuake(quake: QuakeEvent) {
        /*
            Store the selected event so switches/list re-rendering can remember it.
        */
        selectedQuake = quake

        /*
            Tell the map which marker should be highlighted.
        */
        quakeMapView.selectQuake(quake)

        /*
            Update both visible text and screen-reader text.
        */
        selectedQuakeDetails.text = formatQuakeDetails(quake)
        selectedQuakeDetails.contentDescription = formatQuakeDetails(quake)

        /*
            Re-render list rows so the selected row can be highlighted.
        */
        renderQuakeList(quakeEvents)
    }

    private fun renderQuakeList(events: List<QuakeEvent>) {
        /*
            The list is rebuilt from scratch whenever data, selection, or display
            settings change.

            This is simple and fine for a small list of around 30 events.
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

            /*
                Each earthquake row is a TextView instead of a RecyclerView row.
                That keeps the project smaller while still proving the feature.

                For larger datasets, RecyclerView would be better.
            */
            val row = TextView(this).apply {
                text = getString(R.string.quake_list_item, quake.magnitude, quake.place)

                /*
                    Content descriptions help TalkBack/screen readers describe
                    what the row means and what tapping it will do.
                */
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

                /*
                    These make the row behave like an accessible button/list item.
                */
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

    private fun updateInclusiveDisplay() {
        /*
            Read the current switch states once, then apply them across the UI.
        */
        val highContrast = highContrastSwitch.isChecked
        val largeText = largeTextSwitch.isChecked

        /*
            The map draws itself manually, so we pass the high contrast setting
            into the custom View.
        */
        quakeMapView.setHighContrast(highContrast)

        /*
            Main background and status text.
        */
        mainContent.setBackgroundColor(if (highContrast) Color.BLACK else Color.rgb(247, 244, 238))
        statusText.setBackgroundColor(if (highContrast) Color.rgb(25, 25, 25) else Color.WHITE)
        statusText.setTextColor(if (highContrast) Color.WHITE else Color.rgb(63, 58, 52))
        statusText.textSize = if (largeText) 19f else 15f

        /*
            Selected earthquake details card.
        */
        selectedQuakeDetails.setBackgroundColor(if (highContrast) Color.rgb(25, 25, 25) else Color.WHITE)
        selectedQuakeDetails.setTextColor(if (highContrast) Color.WHITE else Color.rgb(42, 42, 42))
        selectedQuakeDetails.textSize = if (largeText) 20f else 16f

        val titleColor = if (highContrast) Color.WHITE else Color.rgb(31, 31, 31)
        val bodyColor = if (highContrast) Color.WHITE else Color.rgb(63, 58, 52)

        /*
            These are the main section headings.
        */
        val titleIds = listOf(
            R.id.appTitle,
            R.id.selectedQuakeTitle,
            R.id.inclusivityTitle,
            R.id.recentQuakesTitle
        )

        titleIds.forEach { id ->
            findViewById<TextView>(id).setTextColor(titleColor)
        }

        /*
            Subtitle uses body styling rather than title styling.
        */
        findViewById<TextView>(R.id.appSubtitle).apply {
            setTextColor(bodyColor)
            textSize = if (largeText) 20f else 16f
        }

        /*
            Switch labels should also remain readable in high contrast mode.
        */
        highContrastSwitch.setTextColor(bodyColor)
        largeTextSwitch.setTextColor(bodyColor)

        /*
            Apply larger text sizes where appropriate.
        */
        findViewById<TextView>(R.id.appTitle).textSize = if (largeText) 34f else 30f
        findViewById<TextView>(R.id.selectedQuakeTitle).textSize = if (largeText) 24f else 20f
        findViewById<TextView>(R.id.inclusivityTitle).textSize = if (largeText) 24f else 20f
        findViewById<TextView>(R.id.recentQuakesTitle).textSize = if (largeText) 24f else 20f

        /*
            Reformat the details text so it stays in sync with the selected event.
        */
        selectedQuakeDetails.text = selectedQuake?.let { formatQuakeDetails(it) }
            ?: getString(R.string.no_quake_selected)

        /*
            Rebuild list rows so high contrast, large text, and selection styling
            are applied to every row.
        */
        renderQuakeList(quakeEvents)
    }

    private fun formatQuakeDetails(quake: QuakeEvent): String {
        val urlText = if (quake.eventUrl.isBlank()) {
            "Source URL not available"
        } else {
            "Source: ${quake.eventUrl}"
        }

        /*
            This text is shown in the details card and also used for the card's
            contentDescription.
        */
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
        /*
            GradientDrawable lets us create a rounded rectangle background in code.

            This avoids needing several XML drawable files while still making the
            selected row and high contrast mode visually clear.
        */
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
            Android layouts use density-independent pixels, but drawing and
            programmatic padding need actual pixels.

            This helper converts dp to pixels based on the device screen density.
        */
        return (value * resources.displayMetrics.density).toInt()
    }
}