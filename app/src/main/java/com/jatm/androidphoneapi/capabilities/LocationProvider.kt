package com.jatm.androidphoneapi.capabilities

interface LocationProvider {
    fun lastKnownLocation(): LocationInfo?
}

data class LocationInfo(
    val latitude: Double,
    val longitude: Double,
    val accuracy: Float?,
    val altitude: Double?,
    val bearing: Float?,
    val speed: Float?,
    val timestampEpochMillis: Long,
    val provider: String?,
)
