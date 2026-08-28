package com.example.offlinebustracker.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.widget.Toast

class UserLocationManager(private val context: Context) {

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    var currentLocation: Location? = null
        private set

    private var onLocationUpdated: ((Location) -> Unit)? = null

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            currentLocation = location
            onLocationUpdated?.invoke(location)
            Toast.makeText(context, "Location Found!", Toast.LENGTH_SHORT).show()
        }

        @Deprecated("Deprecated in API 29")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    @SuppressLint("MissingPermission")
    fun startLocationUpdates(onLocation: (Location) -> Unit) {
        this.onLocationUpdated = onLocation
        try {
            val isGpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
            val isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

            // DEBUG 1: Master GPS is completely off
            if (!isGpsEnabled && !isNetworkEnabled) {
                Toast.makeText(context, "ERROR: Phone GPS is turned OFF in settings!", Toast.LENGTH_LONG).show()
                return
            }

            // DEBUG 2: Tell us which provider is searching
            if (isGpsEnabled && isNetworkEnabled) {
                Toast.makeText(context, "Searching using GPS & Network...", Toast.LENGTH_SHORT).show()
            } else if (isGpsEnabled) {
                Toast.makeText(context, "Searching using GPS only...", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Searching using Network only...", Toast.LENGTH_SHORT).show()
            }

            if (isGpsEnabled) {
                locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 3000L, 5f, locationListener)
            }

            if (isNetworkEnabled) {
                locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 3000L, 5f, locationListener)
            }

            val lastGps = if (isGpsEnabled) locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER) else null
            val lastNet = if (isNetworkEnabled) locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) else null

            val best = lastGps ?: lastNet
            if (best != null) {
                currentLocation = best
                onLocation.invoke(best)
            }
        } catch (e: Exception) {
            Toast.makeText(context, "Location Error: ${e.message}", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }

    fun stopLocationUpdates() {
        try {
            locationManager.removeUpdates(locationListener)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}