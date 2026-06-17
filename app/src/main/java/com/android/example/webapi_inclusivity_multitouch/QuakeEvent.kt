package com.android.example.webapi_inclusivity_multitouch

data class QuakeEvent(  // Data class is primarily for holding and storing data
    val id: String,     // Doesn't utilize functions or do complex calculations
    val place: String,
    val magnitude: Double,
    val timeText: String,
    val latitude: Double,
    val longitude: Double,
    val depthKm: Double = 0.0,
    val eventUrl: String = ""
)