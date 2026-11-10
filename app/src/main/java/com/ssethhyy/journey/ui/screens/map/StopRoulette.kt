/*
 * Feature Module: Map
 * File: StopRoulette.kt
 * Description: Stop roulette on the Map tab (from MelbourneTrainGame): roll a random stop within the search
 * radius, watch it shuffle through candidates, then fly to where it landed with an optional travel challenge.
 */

package com.ssethhyy.journey.ui.screens.map

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.ssethhyy.journey.AppContainer
import com.ssethhyy.journey.data.game.RollResult
import com.ssethhyy.journey.data.game.StopRoulette
import com.ssethhyy.journey.data.location.LocationProvider
import com.ssethhyy.journey.data.model.Stop
import com.ssethhyy.journey.data.model.TransportMode
import com.ssethhyy.journey.ui.components.HapticIconButton
import com.ssethhyy.journey.ui.components.ModePictogram
import com.ssethhyy.journey.ui.components.Sym
import com.ssethhyy.journey.ui.components.hapticClick
import com.ssethhyy.journey.ui.components.hapticConfirm
import com.ssethhyy.journey.utils.DistanceFormatter
import com.ssethhyy.journey.utils.HapticUtil
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface RouletteState {
    data object Idle : RouletteState

    /** Shuffling; [preview] is the stop currently flashing past. */
    data class Rolling(val mode: TransportMode, val preview: Stop?) : RouletteState

    data class Landed(val result: RollResult) : RouletteState

    data class NothingInRange(val mode: TransportMode, val radiusMeters: Int) : RouletteState

    data class Failed(val message: String) : RouletteState
}

class StopRouletteViewModel(private val container: AppContainer) : ViewModel() {
    private val _state = MutableStateFlow<RouletteState>(RouletteState.Idle)
    val state: StateFlow<RouletteState> = _state.asStateFlow()
    private var job: Job? = null

    fun roll(onLanded: (Stop) -> Unit) {
        job?.cancel()
        job = viewModelScope.launch {
            val settings = container.settingsRepository.current()
            _state.value = RouletteState.Rolling(settings.gameMode, null)
            val here = container.locationProvider.currentLocation() ?: LocationProvider.MELBOURNE_CBD
            val stops = runCatching {
                container.ptvRepository.nearbyStops(here.latitude, here.longitude, setOf(settings.gameMode), settings.gameRadiusMeters, maxResults = 500)
            }.getOrElse {
                _state.value = RouletteState.Failed("Couldn't load stops to roll from")
                return@launch
            }
            val candidates = StopRoulette.eligible(stops, here.latitude, here.longitude, settings.gameRadiusMeters, settings.gameSkipNearby)
            val result = StopRoulette.roll(candidates, settings.gameChallenges)
            if (result == null) {
                _state.value = RouletteState.NothingInRange(settings.gameMode, settings.gameRadiusMeters)
                return@launch
            }
            if (settings.gameRollAnimation && candidates.size > 1) {
                // Shuffle through candidates, slowing down towards the end like a wheel settling.
                var wait = 70L
                repeat(16) {
                    _state.value = RouletteState.Rolling(settings.gameMode, candidates.random().first)
                    delay(wait)
                    wait = (wait * 1.12).toLong()
                }
            }
            _state.value = RouletteState.Landed(result)
            onLanded(result.stop)
        }
    }

    fun dismiss() {
        job?.cancel()
        _state.value = RouletteState.Idle
    }
}

private fun TransportMode.stopNoun(plural: Boolean): String = when (this) {
    TransportMode.TRAIN, TransportMode.VLINE -> if (plural) "stations" else "station"
    else -> if (plural) "stops" else "stop"
}

/** The roll as it happens and where it landed. Shown under the map's filters. */
@Composable
fun StopRouletteCard(
    state: RouletteState,
    onPlan: (Stop) -> Unit,
    onOpenStop: (Stop) -> Unit,
    onRollAgain: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val view = LocalView.current
    val context = LocalContext.current
    // A tick for every stop that flashes past, and a success pulse when it lands.
    LaunchedEffect((state as? RouletteState.Rolling)?.preview) {
        if (state is RouletteState.Rolling && state.preview != null) HapticUtil.performLightHaptic(view)
    }
    LaunchedEffect(state is RouletteState.Landed) {
        if (state is RouletteState.Landed) HapticUtil.performSuccessHaptic(context)
    }
    if (state is RouletteState.Idle) return

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shadowElevation = 6.dp,
    ) {
        Column(Modifier.padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Dice(spinning = state is RouletteState.Rolling)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        when (state) {
                            is RouletteState.Rolling -> "Rolling a ${state.mode.stopNoun(false)}…"
                            is RouletteState.Landed -> "Your destination"
                            else -> "Stop roulette"
                        },
                        style = MaterialTheme.typography.labelLargeEmphasized,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    AnimatedContent(
                        targetState = when (state) {
                            is RouletteState.Rolling -> state.preview?.name ?: "Finding ${state.mode.stopNoun(true)} near you"
                            is RouletteState.Landed -> state.result.stop.name
                            is RouletteState.NothingInRange -> "No ${state.mode.label.lowercase()} ${state.mode.stopNoun(true)} in range"
                            is RouletteState.Failed -> state.message
                            RouletteState.Idle -> ""
                        },
                        transitionSpec = { (slideInVertically { -it / 2 } + fadeIn(tween(90))) togetherWith (slideOutVertically { it / 2 } + fadeOut(tween(90))) },
                        label = "roulette_name",
                    ) { name ->
                        // The stop's departures show in the map's stop preview below, so this card stays about the game.
                        Text(name, style = MaterialTheme.typography.headlineSmallEmphasized, maxLines = if (state is RouletteState.Landed) 2 else 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                HapticIconButton(Sym.Close, "Close stop roulette", onClick = onClose, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            when (state) {
                is RouletteState.Landed -> {
                    val result = state.result
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(end = 12.dp)) {
                        ModePictogram(result.stop.mode, size = 22.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            listOfNotNull("${DistanceFormatter.distance(result.distanceMeters)} away", result.stop.suburb).joinToString(" · "),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    result.challenge?.let { challenge ->
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.fillMaxWidth().padding(end = 12.dp),
                        ) {
                            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                                Text("Challenge · ${challenge.title}", style = MaterialTheme.typography.titleSmallEmphasized)
                                Text(challenge.description, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(end = 12.dp)) {
                        Button(onClick = hapticConfirm { onPlan(result.stop) }, modifier = Modifier.weight(1f)) {
                            Icon(Sym.Directions, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Plan trip")
                        }
                        FilledTonalButton(onClick = hapticClick(onRollAgain), modifier = Modifier.weight(1f)) {
                            Icon(Sym.Casino, null, Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Roll again")
                        }
                    }
                }
                is RouletteState.NothingInRange -> {
                    Text(
                        "Nothing within ${DistanceFormatter.distance(state.radiusMeters.toDouble())}. Widen the radius or change the mode in Settings → Game.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 12.dp),
                    )
                }
                is RouletteState.Failed -> FilledTonalButton(onClick = hapticClick(onRollAgain)) { Text("Try again") }
                else -> Unit
            }
        }
    }
}

@Composable
private fun Dice(spinning: Boolean) {
    val transition = rememberInfiniteTransition(label = "dice")
    val angle by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(700, easing = LinearEasing), RepeatMode.Restart), label = "dice_angle")
    Box(
        Modifier
            .size(44.dp)
            .background(MaterialTheme.colorScheme.primaryContainer, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Sym.Casino,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.size(24.dp).graphicsLayer { rotationZ = if (spinning) angle else 0f },
        )
    }
}
