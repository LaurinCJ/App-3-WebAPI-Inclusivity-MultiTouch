package com.android.example.webapi_inclusivity_multitouch

data class QuakeEvent( //Data Class is Primarily for holding and storing data
    val id: String,    //Doesn't utilize functions or do complex calculations
    val place: String,
    val magnitude: Double,
    val timeText: String,
    val latitude: Double,
    val longitude: Double
)