package com.roomu.app.data.preferences

import android.content.Context
import android.content.SharedPreferences
import com.google.android.gms.maps.model.LatLng
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LocationPreferences @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences("location_prefs", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_LATITUDE = "latitude"
        private const val KEY_LONGITUDE = "longitude"
    }

    fun saveLocation(lat: Double, lng: Double) {
        prefs.edit().apply {
            putFloat(KEY_LATITUDE, lat.toFloat())
            putFloat(KEY_LONGITUDE, lng.toFloat())
            apply()
        }
    }

    fun getLocation(): LatLng? {
        val lat = prefs.getFloat(KEY_LATITUDE, Float.MAX_VALUE)
        val lng = prefs.getFloat(KEY_LONGITUDE, Float.MAX_VALUE)

        return if (lat != Float.MAX_VALUE && lng != Float.MAX_VALUE) {
            LatLng(lat.toDouble(), lng.toDouble())
        } else {
            null
        }
    }
}