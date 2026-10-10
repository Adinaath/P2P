package org.airbornemesh.location

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import androidx.core.content.ContextCompat
import org.airbornemesh.data.model.MessageLocation

class LocationProvider(private val context: Context) {

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    @SuppressLint("MissingPermission")
    fun getCurrentLocation(): MessageLocation? {
        if (!hasLocationPermission() || locationManager == null) return null

        val gpsLocation: Location? = try {
            locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
        } catch (_: Exception) {
            null
        }

        val networkLocation: Location? = try {
            locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
        } catch (_: Exception) {
            null
        }

        val bestLocation = when {
            gpsLocation != null && networkLocation != null -> {
                if (gpsLocation.time > networkLocation.time) gpsLocation else networkLocation
            }
            gpsLocation != null -> gpsLocation
            else -> networkLocation
        }

        return bestLocation?.let {
            MessageLocation(
                lat = it.latitude,
                lng = it.longitude,
                accuracyM = if (it.hasAccuracy()) it.accuracy.toDouble() else 20.0,
                observedAt = it.time / 1000
            )
        }
    }
}
