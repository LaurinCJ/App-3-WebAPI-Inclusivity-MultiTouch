package com.android.example.webapi_inclusivity_multitouch

import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.util.Locale

class MainActivity : AppCompatActivity() {
    private lateinit var mainContent: LinearLayout
    private lateinit var quakeMapView: QuakeMapView
    private lateinit var selectedQuakeDetails: TextView
    private lateinit var quakeListContainer: LinearLayout
    private lateinit var highContrastSwitch: SwitchCompat
    private lateinit var largeTextSwitch: SwitchCompat

    private var quakeEvents: List<QuakeEvent> = emptyList()
    private var selectedQuake: QuakeEvent? = null

    private val sampleQuakes = listOf(
        QuakeEvent(
            id = "sample-1",
            place = "Southern Alaska",
            magnitude = 4.8,
            timeText = "Sample event • 14 minutes ago",
            latitude = 61.21,
            longitude = -149.90
        ),
        QuakeEvent(
            id = "sample-2",
            place = "Near the coast of central Chile",
            magnitude = 5.6,
            timeText = "Sample event • 42 minutes ago",
            latitude = -33.45,
            longitude = -71.66
        ),
        QuakeEvent(
            id = "sample-3",
            place = "Honshu, Japan region",
            magnitude = 4.3,
            timeText = "Sample event • 1 hour ago",
            latitude = 38.27,
            longitude = 142.82
        ),
        QuakeEvent(
            id = "sample-4",
            place = "Central California",
            magnitude = 3.1,
            timeText = "Sample event • 2 hours ago",
            latitude = 36.77,
            longitude = -119.42
        ),
        QuakeEvent(
            id = "sample-5",
            place = "New Zealand region",
            magnitude = 4.9,
            timeText = "Sample event • 3 hours ago",
            latitude = -41.29,
            longitude = 174.78
        )
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        mainContent = findViewById(R.id.mainContent)
        quakeMapView = findViewById(R.id.quakeMapView)
        selectedQuakeDetails = findViewById(R.id.selectedQuakeDetails)
        quakeListContainer = findViewById(R.id.quakeListContainer)
        highContrastSwitch = findViewById(R.id.highContrastSwitch)
        largeTextSwitch = findViewById(R.id.largeTextSwitch)

        quakeMapView.onQuakeSelected = { quake ->
            selectQuake(quake)
        }

        findViewById<Button>(R.id.resetMapButton).setOnClickListener {
            quakeMapView.resetView()
        }

        findViewById<Button>(R.id.refreshDataButton).setOnClickListener {
            Toast.makeText(this, getString(R.string.live_data_next), Toast.LENGTH_SHORT).show()
        }

        highContrastSwitch.setOnCheckedChangeListener { _, _ ->
            updateInclusiveDisplay()
        }

        largeTextSwitch.setOnCheckedChangeListener { _, _ ->
            updateInclusiveDisplay()
        }

        showQuakeEvents(sampleQuakes)
    }

    private fun showQuakeEvents(events: List<QuakeEvent>) {
        quakeEvents = events
        quakeMapView.setQuakes(events)
        renderQuakeList(events)

        if (events.isNotEmpty()) {
            selectQuake(events.first())
        } else {
            selectedQuake = null
            selectedQuakeDetails.text = getString(R.string.no_quake_selected)
        }
    }

    private fun selectQuake(quake: QuakeEvent) {
        selectedQuake = quake
        quakeMapView.selectQuake(quake)
        selectedQuakeDetails.text = formatQuakeDetails(quake)
        selectedQuakeDetails.contentDescription = formatQuakeDetails(quake)
        renderQuakeList(quakeEvents)
    }

    private fun renderQuakeList(events: List<QuakeEvent>) {
        quakeListContainer.removeAllViews()

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

    private fun updateInclusiveDisplay() {
        val highContrast = highContrastSwitch.isChecked
        val largeText = largeTextSwitch.isChecked

        quakeMapView.setHighContrast(highContrast)

        mainContent.setBackgroundColor(if (highContrast) Color.BLACK else Color.rgb(247, 244, 238))
        selectedQuakeDetails.setBackgroundColor(if (highContrast) Color.rgb(25, 25, 25) else Color.WHITE)
        selectedQuakeDetails.setTextColor(if (highContrast) Color.WHITE else Color.rgb(42, 42, 42))
        selectedQuakeDetails.textSize = if (largeText) 20f else 16f

        val titleColor = if (highContrast) Color.WHITE else Color.rgb(31, 31, 31)
        val bodyColor = if (highContrast) Color.WHITE else Color.rgb(63, 58, 52)

        val titleIds = listOf(
            R.id.appTitle,
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
        findViewById<TextView>(R.id.selectedQuakeTitle).textSize = if (largeText) 24f else 20f
        findViewById<TextView>(R.id.inclusivityTitle).textSize = if (largeText) 24f else 20f
        findViewById<TextView>(R.id.recentQuakesTitle).textSize = if (largeText) 24f else 20f

        selectedQuakeDetails.text = selectedQuake?.let { formatQuakeDetails(it) }
            ?: getString(R.string.no_quake_selected)

        renderQuakeList(quakeEvents)
    }

    private fun formatQuakeDetails(quake: QuakeEvent): String {
        return String.format(
            Locale.US,
            "Magnitude %.1f\n%s\n%s\nLatitude %.2f, Longitude %.2f",
            quake.magnitude,
            quake.place,
            quake.timeText,
            quake.latitude,
            quake.longitude
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
        return (value * resources.displayMetrics.density).toInt()
    }
}