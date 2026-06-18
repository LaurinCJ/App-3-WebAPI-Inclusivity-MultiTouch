package com.android.example.webapi_inclusivity_multitouch

/*
    QuakeEvent is our app's internal model for one earthquake.

    The USGS API returns a lot of GeoJSON data, but the UI does not need all of it.
    This data class stores only the fields our app currently needs:
    - list display
    - selected earthquake details
    - map marker placement
    - accessibility descriptions
*/
data class QuakeEvent( // Data class is primarily for holding and storing data
    val id: String, // Doesn't utilize functions or do complex calculations

    // Human-readable location from the USGS API, such as "10 km S of Idyllwild, CA".
    val place: String,

    // Earthquake magnitude. This controls both the text display and the map marker size/color.
    val magnitude: Double,

    // Already-formatted time string shown in the detail area.
    val timeText: String,

    // Latitude and longitude are used by QuakeMapView to place the earthquake marker.
    val latitude: Double,
    val longitude: Double,

    // Depth is optional because early sample data did not require it.
    // The live USGS feed provides depth as the third coordinate value.
    val depthKm: Double = 0.0,

    // Link to the official USGS event page.
    // This is blank for sample data unless we manually provide one.
    val eventUrl: String = ""
)