/*
 * Feature Module: Data
 * File: LocationProvider.kt
 * Description: One-shot device location with fused provider and platform fallback.
 */

package com.ssethhyy.journey.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import androidx.core.content.ContextCompat
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

data class LatLng(val latitude: Double, val longitude: Double)

class LocationProvider(private val context: Context) {

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    private fun hasPlayServices(): Boolean =
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS

    /** Returns the current location, the last known location, or null if unavailable. */
    @SuppressLint("MissingPermission")
    suspend fun currentLocation(): LatLng? {
        if (!hasPermission()) return null
        if (hasPlayServices()) {
            val client = LocationServices.getFusedLocationProviderClient(context)
            val cts = CancellationTokenSource()
            val fresh = runCatching {
                withTimeoutOrNull(8_000) {
                    client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token).await()
                }
            }.getOrNull()
            cts.cancel()
            fresh?.let { return LatLng(it.latitude, it.longitude) }
            runCatching { client.lastLocation.await() }.getOrNull()?.let { return LatLng(it.latitude, it.longitude) }
        }
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        return manager.getProviders(true)
            .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
            ?.let { LatLng(it.latitude, it.longitude) }
    }

    companion object {
        /** Flinders Street Station, used when location is unavailable. */
        val MELBOURNE_CBD = LatLng(-37.8183, 144.9671)
    }
}
