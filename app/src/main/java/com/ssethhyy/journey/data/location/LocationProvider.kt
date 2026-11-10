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
import androidx.core.location.LocationManagerCompat
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull

data class LatLng(val latitude: Double, val longitude: Double)

/** Whether a location can be asked for right now, and if not, what is in the way. */
enum class LocationAccess { READY, NO_PERMISSION, SERVICES_OFF }

class LocationProvider(private val context: Context) {

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    /** The system Location switch. Permission alone isn't enough: with this off no fix ever arrives. */
    fun isLocationEnabled(): Boolean {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return false
        return LocationManagerCompat.isLocationEnabled(manager)
    }

    fun access(): LocationAccess = when {
        !hasPermission() -> LocationAccess.NO_PERMISSION
        !isLocationEnabled() -> LocationAccess.SERVICES_OFF
        else -> LocationAccess.READY
    }

    private fun hasPlayServices(): Boolean =
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS

    /** The last fix any app got, instantly, or null. Good enough to point the map while a fresh fix arrives. */
    @SuppressLint("MissingPermission")
    suspend fun lastKnownLocation(): LatLng? {
        if (!hasPermission()) return null
        if (hasPlayServices()) {
            runCatching { withTimeoutOrNull(1_500) { LocationServices.getFusedLocationProviderClient(context).lastLocation.await() } }
                .getOrNull()?.let { return LatLng(it.latitude, it.longitude) }
        }
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null
        return manager.getProviders(true)
            .mapNotNull { runCatching { manager.getLastKnownLocation(it) }.getOrNull() }
            .maxByOrNull { it.time }
            ?.let { LatLng(it.latitude, it.longitude) }
    }

    /** Returns the current location, the last known location, or null if unavailable. */
    @SuppressLint("MissingPermission")
    suspend fun currentLocation(): LatLng? {
        if (!hasPermission()) return null
        // With Location switched off a fresh fix never comes, so don't make the user wait for one.
        if (!isLocationEnabled()) return lastKnownLocation()
        if (hasPlayServices()) {
            val client = LocationServices.getFusedLocationProviderClient(context)
            val cts = CancellationTokenSource()
            val fresh = runCatching {
                withTimeoutOrNull(6_000) {
                    client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, cts.token).await()
                }
            }.getOrNull()
            cts.cancel()
            fresh?.let { return LatLng(it.latitude, it.longitude) }
        }
        return lastKnownLocation()
    }

    companion object {
        /** Flinders Street Station, used when location is unavailable. */
        val MELBOURNE_CBD = LatLng(-37.8183, 144.9671)
    }
}
