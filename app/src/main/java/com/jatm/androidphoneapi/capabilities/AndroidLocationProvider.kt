package com.jatm.androidphoneapi.capabilities

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationManager

class AndroidLocationProvider(
    private val context: Context,
) : LocationProvider {
    @SuppressLint("MissingPermission")
    override fun lastKnownLocation(): LocationInfo? {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager

        val location: Location? = try {
            manager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: manager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        } catch (_: SecurityException) {
            // Location permission not granted — the app/UI is responsible for requesting it.
            return null
        }

        return location?.let {
            LocationInfo(
                latitude = it.latitude,
                longitude = it.longitude,
                accuracy = if (it.hasAccuracy()) it.accuracy else null,
                altitude = if (it.hasAltitude()) it.altitude else null,
                bearing = if (it.hasBearing()) it.bearing else null,
                speed = if (it.hasSpeed()) it.speed else null,
                timestampEpochMillis = it.time,
                provider = it.provider,
            )
        }
    }
}
