/*
 * Feature Module: Tracking
 * File: StopAlertActivity.kt
 * Description: The full-screen "get off here" alert. It turns the screen on over the lock screen, names the stop in
 * large expressive type, says what to do (press the stop button, or get ready), and closes with one big button.
 * It never makes a sound; vibration is handled by StopAlertNotifier.
 */

package com.ssethhyy.journey.tracking

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssethhyy.journey.MainActivity
import com.ssethhyy.journey.appContainer
import com.ssethhyy.journey.data.model.TransportMode
import com.ssethhyy.journey.ui.components.pictogramRes
import com.ssethhyy.journey.ui.theme.JourneyTheme
import com.ssethhyy.journey.utils.TimeFormatter
import kotlinx.coroutines.delay
import java.time.Instant

class StopAlertActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Show over the lock screen and wake the display, like an alarm.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        enableEdgeToEdge()
        val stopName = intent.getStringExtra(EXTRA_STOP).orEmpty()
        val instruction = intent.getStringExtra(EXTRA_INSTRUCTION).orEmpty()
        val detail = intent.getStringExtra(EXTRA_DETAIL).orEmpty()
        val route = intent.getStringExtra(EXTRA_ROUTE).orEmpty()
        val mode = intent.getIntExtra(EXTRA_ROUTE_TYPE, -1).takeIf { it >= 0 }?.let(TransportMode::fromRouteType)
        val arrival = intent.getLongExtra(EXTRA_ARRIVAL, 0L).takeIf { it > 0 }?.let(Instant::ofEpochSecond)
        setContent {
            val settings by appContainer.settingsRepository.settings.collectAsStateWithLifecycle(initialValue = null)
            val current = settings ?: return@setContent
            JourneyTheme(themeMode = current.themeMode, dynamicColor = current.dynamicColor, pitchBlack = current.pitchBlack) {
                StopAlertScreen(
                    stopName = stopName,
                    instruction = instruction,
                    detail = detail,
                    route = route,
                    mode = mode,
                    arrival = arrival,
                    onDismiss = {
                        StopAlertNotifier.cancel(this)
                        finish()
                    },
                    onOpen = {
                        StopAlertNotifier.cancel(this)
                        startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP))
                        finish()
                    },
                )
            }
        }
    }

    companion object {
        const val EXTRA_STOP = "stop_alert_stop"
        const val EXTRA_INSTRUCTION = "stop_alert_instruction"
        const val EXTRA_DETAIL = "stop_alert_detail"
        const val EXTRA_ROUTE = "stop_alert_route"
        const val EXTRA_ROUTE_TYPE = "stop_alert_route_type"
        const val EXTRA_ARRIVAL = "stop_alert_arrival"

        fun intent(context: Context, alert: StopAlert): Intent = Intent(context, StopAlertActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_USER_ACTION)
            .putExtra(EXTRA_STOP, alert.stopName)
            .putExtra(EXTRA_INSTRUCTION, alert.instruction)
            .putExtra(EXTRA_DETAIL, alert.detail)
            .putExtra(EXTRA_ROUTE, alert.routeLabel)
            .putExtra(EXTRA_ROUTE_TYPE, alert.mode?.routeType ?: -1)
            .putExtra(EXTRA_ARRIVAL, alert.arrival.epochSecond)
    }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun StopAlertScreen(
    stopName: String,
    instruction: String,
    detail: String,
    route: String,
    mode: TransportMode?,
    arrival: Instant?,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    var now by remember { mutableStateOf(Instant.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(5_000)
            now = Instant.now()
        }
    }
    // A slowly turning expressive shape behind the mode pictogram, so the alert reads at a glance.
    val spin = rememberInfiniteTransition(label = "stop_alert")
    val angle by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(12_000, easing = LinearEasing), RepeatMode.Restart), label = "shape_angle")
    val pulse by spin.animateFloat(0.94f, 1.04f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "shape_pulse")

    Surface(color = colors.primaryContainer, contentColor = colors.onPrimaryContainer, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 28.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))
            Box(contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(200.dp)
                        .graphicsLayer {
                            rotationZ = angle
                            scaleX = pulse
                            scaleY = pulse
                        }
                        .background(colors.primary, MaterialShapes.Cookie9Sided.toShape()),
                )
                if (mode != null) {
                    Image(painterResource(mode.pictogramRes), contentDescription = mode.label, modifier = Modifier.size(104.dp))
                }
            }
            Spacer(Modifier.height(40.dp))
            Text("Get off at", style = MaterialTheme.typography.titleLargeEmphasized, color = colors.onPrimaryContainer.copy(alpha = 0.8f))
            Spacer(Modifier.height(8.dp))
            Text(
                stopName,
                style = MaterialTheme.typography.displayMediumEmphasized,
                textAlign = TextAlign.Center,
                maxLines = 3,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(20.dp))
            Surface(shape = MaterialTheme.shapes.extraLarge, color = colors.primary, contentColor = colors.onPrimary) {
                Text(
                    instruction,
                    style = MaterialTheme.typography.titleMediumEmphasized,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
                )
            }
            Spacer(Modifier.height(16.dp))
            val countdown = arrival?.let { TrackingLogic.countdown(it, now) }
            Text(
                listOfNotNull(
                    route.takeIf { it.isNotBlank() },
                    countdown?.let { if (it == "Now") "Arriving now" else "In $it" },
                    detail.takeIf { it.isNotBlank() } ?: arrival?.let { "Arriving ${TimeFormatter.clock(it)}" },
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.weight(1f))
            Button(
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth().height(72.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.primary, contentColor = colors.onPrimary),
            ) { Text("Got it", style = MaterialTheme.typography.titleLargeEmphasized) }
            Spacer(Modifier.height(12.dp))
            FilledTonalButton(onClick = onOpen, modifier = Modifier.fillMaxWidth().height(56.dp)) {
                Text("Open journey", style = MaterialTheme.typography.labelLargeEmphasized)
            }
        }
    }
}
