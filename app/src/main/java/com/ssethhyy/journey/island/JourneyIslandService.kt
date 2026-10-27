/*
 * Feature Module: Island
 * File: JourneyIslandService.kt
 * Description: Essentials island provider. Publishes live tracking and service disruptions to the
 * island (compact pill + line), peeks at key moments, and draws the expanded views in Compose.
 */

package com.ssethhyy.journey.island

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import com.sameerasw.island.sdk.HostInfo
import com.sameerasw.island.sdk.IslandHostHandle
import com.sameerasw.island.sdk.IslandPayload
import com.sameerasw.island.sdk.compose.ComposeIslandProviderService
import com.ssethhyy.journey.appContainer
import com.ssethhyy.journey.data.model.Disruption
import com.ssethhyy.journey.data.repository.AppSettings
import com.ssethhyy.journey.data.repository.IslandDisruptionScope
import com.ssethhyy.journey.tracking.TrackingStatus
import com.ssethhyy.journey.tracking.TrackingTarget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant

class JourneyIslandService : ComposeIslandProviderService() {
    private var scope: CoroutineScope? = null

    // Essentials calls attach/detach on separate binder threads; a late detach must not wipe out
    // the scope (and connected state) of an attach that has already started.
    private val lifecycleLock = Any()
    private val disruptions = MutableStateFlow<List<Disruption>>(emptyList())
    private val refreshLock = Mutex()
    private var refreshJob: Job? = null

    /** Latest values for the expanded views (read by Compose in this process). */
    internal val expandedState = mutableStateOf(ExpandedState())

    private var lastTrackingStatus: TrackingStatus? = null
    private var lastTrackingTargetId: String? = null
    private var knownDisruptionIds: Set<Long>? = null
    private var lastPeekAt = Long.MIN_VALUE / 2 // "never", without overflowing the gap check

    override fun onAttached(host: IslandHostHandle, info: HostInfo): Unit = synchronized(lifecycleLock) {
        IslandConnection.update { it.copy(attached = true, hostPackage = info.hostPackage) }
        scope?.cancel()
        val newScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        scope = newScope
        val container = applicationContext.appContainer

        // Publish whenever settings, tracking or disruptions change.
        newScope.launch {
            combine(
                container.settingsRepository.settings,
                container.trackingManager.target,
                container.trackingManager.status,
                disruptions,
                previewTicker(),
            ) { settings, target, status, list, preview ->
                // A real tracking session always wins over the Settings preview.
                if (target == null && preview != null) Snapshot(settings, preview.first, preview.second, list)
                else Snapshot(settings, target, status, list)
            }.collect { snapshot -> publish(snapshot) }
        }

        // "Peek now" from Settings.
        newScope.launch {
            IslandConnection.peekRequests.collect { request ->
                val key = request?.first ?: return@collect
                lastPeekAt = android.os.SystemClock.elapsedRealtime()
                runCatching { host.peek(key, PEEK_DURATION_MS) }
                IslandConnection.update { it.copy(lastPeek = "$key at ${Instant.now()}") }
                IslandConnection.consumePeek()
            }
        }

        // Keep disruptions fresh while attached: now, every 4 minutes, and when the scope or favourites change.
        newScope.launch {
            combine(
                container.settingsRepository.settings.map { (it.islandEnabled && it.islandDisruptions) to it.islandDisruptionScope }.distinctUntilChanged(),
                container.favouritesRepository.favouriteKeys,
            ) { setting, _ -> setting }.collect { refreshDisruptions(force = false) }
        }
        newScope.launch {
            while (isActive) {
                delay(DISRUPTION_REFRESH_MS)
                refreshDisruptions(force = true)
            }
        }
    }

    override fun onInfoChanged(info: HostInfo) {
        // Theme or layout changed (e.g. accent colour): the expanded view reads hostInfoState directly.
    }

    override fun onHostDetached(): Unit = synchronized(lifecycleLock) {
        super.onHostDetached()
        scope?.cancel()
        scope = null
        lastTrackingStatus = null
        lastTrackingTargetId = null
        knownDisruptionIds = null
        IslandConnection.update { it.copy(attached = false, itemsShown = emptyList()) }
    }

    override fun onDestroy() {
        scope?.cancel()
        IslandConnection.update { it.copy(attached = false, itemsShown = emptyList()) }
        super.onDestroy()
    }

    override fun onAction(itemKey: String, actionId: String) {
        when (actionId) {
            JourneyIsland.ACTION_STOP_TRACKING -> stopTracking()
        }
    }

    override fun hasExpandedContent(itemKey: String): Boolean =
        itemKey == JourneyIsland.ITEM_TRACKING || itemKey == JourneyIsland.ITEM_DISRUPTIONS

    @Composable
    override fun ExpandedContent(itemKey: String) {
        val state by expandedState
        IslandExpandedContent(
            itemKey = itemKey,
            state = state,
            info = hostInfoState.value,
            host = host,
            onStopTracking = ::stopTracking,
        )
    }

    /** Stops real tracking, or ends the Settings preview when that's what is showing. */
    private fun stopTracking() {
        val manager = applicationContext.appContainer.trackingManager
        if (manager.target.value != null) manager.stop() else IslandConnection.stopPreview()
    }

    private suspend fun refreshDisruptions(force: Boolean) {
        refreshLock.withLock {
            val settings = applicationContext.appContainer.settingsRepository.current()
            if (!settings.islandEnabled || !settings.islandDisruptions) {
                disruptions.value = emptyList()
                return
            }
            runCatching { loadDisruptions(applicationContext, settings, force) }
                .onSuccess { disruptions.value = it }
        }
    }

    private fun publish(snapshot: Snapshot) {
        val host = host ?: return
        val context = applicationContext
        val payload = JourneyIsland.payload(snapshot.settings, snapshot.target, snapshot.status, snapshot.disruptions)
        val shownDisruptions = JourneyIsland.filterDisruptions(snapshot.disruptions, snapshot.settings.islandMinSeverity)
        expandedState.value = ExpandedState(
            target = snapshot.target,
            status = snapshot.status,
            disruptions = shownDisruptions,
            scopeLabel = if (snapshot.settings.islandDisruptionScope == IslandDisruptionScope.FAVOURITES) "On your stops" else "Across the network",
            iconStyle = snapshot.settings.islandIconStyle,
        )
        runCatching {
            host.publish(payload, IslandIcons.bitmaps(context, JourneyIsland.iconIds(payload)))
        }.onFailure { return }
        IslandConnection.update { state ->
            state.copy(lastPublishAt = Instant.now(), itemsShown = payload.items.map { it.key })
        }
        maybePeek(host, snapshot, payload, shownDisruptions)
    }

    private fun maybePeek(host: IslandHostHandle, snapshot: Snapshot, payload: IslandPayload, shown: List<Disruption>) {
        val keys = payload.items.map { it.key }.toSet()
        val status = snapshot.status
        val targetId = snapshot.target?.id
        // A new tracking target starts from scratch.
        if (targetId != lastTrackingTargetId) {
            lastTrackingTargetId = targetId
            lastTrackingStatus = null
        }
        var peekKey: String? = null
        if (JourneyIsland.ITEM_TRACKING in keys && status != null) {
            if (TrackingPeekPolicy.shouldPeekTracking(lastTrackingStatus, status, snapshot.settings.islandStyle)) peekKey = JourneyIsland.ITEM_TRACKING
            lastTrackingStatus = status
        }
        val known = knownDisruptionIds
        if (JourneyIsland.ITEM_DISRUPTIONS in keys && known != null && peekKey == null &&
            TrackingPeekPolicy.shouldPeekDisruptions(known, shown, snapshot.settings.islandAlerts)
        ) {
            peekKey = JourneyIsland.ITEM_DISRUPTIONS
        }
        // The first load only records what exists, so opening the island doesn't peek old news.
        if (shown.isNotEmpty() || known != null) knownDisruptionIds = (known ?: emptySet()) + shown.map { it.id }

        val now = android.os.SystemClock.elapsedRealtime()
        if (peekKey != null && now - lastPeekAt >= PEEK_GAP_MS) {
            lastPeekAt = now
            runCatching { host.peek(peekKey, PEEK_DURATION_MS) }
            IslandConnection.update { it.copy(lastPeek = "$peekKey at ${Instant.now()}") }
        }
    }

    /** Emits a demo target/status every few seconds while a Settings preview is running. */
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    private fun previewTicker() = IslandConnection.previewUntil.transformLatest { until ->
            if (until == null) {
                emit(null)
                return@transformLatest
            }
            val started = Instant.now()
            val departs = started.plusSeconds(5 * 60 + 20)
            val target = TrackingTarget.Service("preview", com.ssethhyy.journey.data.model.TransportMode.TRAIN, 0, "Flinders Street", "Hurstbridge line", "Macleod")
            while (Instant.now().isBefore(until)) {
                val now = Instant.now()
                // Fast-forward so the preview passes the 5 and 2 minute marks within a minute and a half.
                val simulated = now.plusSeconds(java.time.Duration.between(started, now).seconds * 3)
                val departure = com.ssethhyy.journey.data.model.Departure(
                    stopId = 0, route = null, runRef = "preview", directionId = 0, directionName = null,
                    destination = "Macleod", scheduled = departs.minusSeconds(60), estimated = departs,
                    platform = "4", atPlatform = false, flags = null, disruptionIds = emptyList(),
                    isExpress = false, isCancelled = false, vehicle = null,
                )
                emit(target to com.ssethhyy.journey.tracking.TrackingLogic.serviceStatus(target, departure, started, simulated))
                delay(5_000)
            }
            emit(null)
            IslandConnection.stopPreview()
    }

    private data class Snapshot(
        val settings: AppSettings,
        val target: TrackingTarget?,
        val status: TrackingStatus?,
        val disruptions: List<Disruption>,
    )

    companion object {
        private const val DISRUPTION_REFRESH_MS = 4 * 60_000L

        // Essentials allows one peek per 10 s, 8 s at most.
        private const val PEEK_GAP_MS = 10_500L
        private const val PEEK_DURATION_MS = 6_000L

        /**
         * Disruptions for the island: those on the routes serving the user's favourite stops (the
         * same set the For You status card counts), or every current disruption on the network.
         */
        suspend fun loadDisruptions(context: Context, settings: AppSettings, force: Boolean): List<Disruption> {
            val container = context.appContainer
            val all = container.ptvRepository.disruptions(force = force)
            if (settings.islandDisruptionScope == IslandDisruptionScope.NETWORK) return all
            val favourites = container.favouritesRepository.favourites.first()
            if (favourites.isEmpty()) return emptyList()
            val boards = favourites.take(8).mapNotNull { fav ->
                runCatching { container.ptvRepository.departures(fav.mode, fav.stopId, maxResults = 4) }.getOrNull()
            }
            val routeKeys = boards.flatMap { board -> board.routes.map { it.key } + board.departures.mapNotNull { it.route?.key } }.toSet()
            val referenced = boards.flatMap { Disruption.forStop(emptyList(), it) }
            val onRoutes = all.filter { d -> d.routes.any { it.key in routeKeys } }
            return (referenced + onRoutes).filter { it.isCurrent }.distinctBy { it.id }
        }
    }
}
