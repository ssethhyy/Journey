/*
 * Feature Module: UI Components
 * File: LocationEnabler.kt
 * Description: One call that gets location working: asks for permission if needed, then asks Android to turn the
 * Location switch on (the system "Turn on location?" dialog) if it is off, and finally runs the caller's action.
 */

package com.ssethhyy.journey.ui.components

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import com.google.android.gms.common.api.ResolvableApiException
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.LocationSettingsRequest
import com.google.android.gms.location.Priority
import com.ssethhyy.journey.appContainer

/**
 * Returns a function that makes location usable and then calls [onReady]. [onUnavailable] runs if the user says
 * no to either step, so the caller can fall back (for example to the city centre).
 */
@Composable
fun rememberLocationEnabler(onUnavailable: () -> Unit = {}, onReady: () -> Unit): () -> Unit {
    val context = LocalContext.current
    val provider = context.appContainer.locationProvider
    val ready by rememberUpdatedState(onReady)
    val unavailable by rememberUpdatedState(onUnavailable)

    val settingsLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK || provider.isLocationEnabled()) {
            ready()
        } else {
            Toast.makeText(context, "Location is off, so stops near the city are shown", Toast.LENGTH_SHORT).show()
            unavailable()
        }
    }

    fun ensureServicesOn() {
        if (provider.isLocationEnabled()) {
            ready()
            return
        }
        val request = LocationSettingsRequest.Builder()
            .addLocationRequest(LocationRequest.Builder(Priority.PRIORITY_BALANCED_POWER_ACCURACY, 10_000).build())
            .setAlwaysShow(true)
            .build()
        runCatching {
            LocationServices.getSettingsClient(context).checkLocationSettings(request)
                .addOnSuccessListener { ready() }
                .addOnFailureListener { e ->
                    if (e is ResolvableApiException) {
                        runCatching { settingsLauncher.launch(IntentSenderRequest.Builder(e.resolution).build()) }
                            .onFailure { openLocationSettings(context) }
                    } else {
                        openLocationSettings(context)
                        unavailable()
                    }
                }
        }.onFailure {
            openLocationSettings(context)
            unavailable()
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants.values.any { it }) ensureServicesOn() else unavailable()
    }

    return remember(provider) {
        {
            if (!provider.hasPermission()) {
                permissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            } else {
                ensureServicesOn()
            }
        }
    }
}

private fun openLocationSettings(context: android.content.Context) {
    runCatching { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
}
