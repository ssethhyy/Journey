/*
 * Feature Module: UI Components
 * File: LegStops.kt
 * Description: Opens the stop page for where a planned leg starts or ends, matching the planner's stop to PTV's.
 */

package com.ssethhyy.journey.ui.components

import android.widget.Toast
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import com.ssethhyy.journey.appContainer
import com.ssethhyy.journey.data.planner.JourneyLeg
import com.ssethhyy.journey.data.repository.findStop
import com.ssethhyy.journey.ui.navigation.currentNavigator
import kotlinx.coroutines.launch

/** Returns a function that opens the stop a leg is boarded at (true) or left at (false). */
@Composable
fun rememberLegStopOpener(): (JourneyLeg, Boolean) -> Unit {
    val context = LocalContext.current
    val container = context.appContainer
    val navigator = currentNavigator
    val scope = rememberCoroutineScope()
    return remember(navigator) {
        { leg, boarding ->
            scope.launch {
                val stop = container.ptvRepository.findStop(leg, boarding)
                if (stop != null) {
                    navigator.openStop(stop)
                } else {
                    val name = if (boarding) leg.fromName else leg.toName
                    Toast.makeText(context, "Couldn't find ${name.ifBlank { "that stop" }} in PTV's stops", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
}
