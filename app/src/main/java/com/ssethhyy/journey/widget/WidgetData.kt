/*
 * Feature Module: Widget
 * File: WidgetData.kt
 * Description: Snapshot of favourite stops' next departures shown by the home-screen widget,
 * plus the logic that builds and stores it.
 */

package com.ssethhyy.journey.widget

import android.content.Context
import com.ssethhyy.journey.AppContainer
import com.ssethhyy.journey.data.api.PtvException
import com.ssethhyy.journey.data.model.Departure
import com.ssethhyy.journey.data.repository.FavouriteStop
import com.ssethhyy.journey.utils.DistanceFormatter
import com.ssethhyy.journey.utils.GeoUtils
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Duration
import java.time.Instant

@Serializable
data class WidgetRow(
    val stopId: Int,
    val routeType: Int,
    val name: String,
    /** "400m away", or the suburb when location isn't available. */
    val subtitle: String?,
    val departureEpochSec: Long? = null,
    val isLive: Boolean = false,
    val headsign: String? = null,
    val routeLabel: String? = null,
    /** Following departures (epoch seconds) used to fill spare space in larger widgets. */
    val laterEpochSec: List<Long> = emptyList(),
)

@Serializable
data class WidgetSnapshot(
    val rows: List<WidgetRow> = emptyList(),
    val updatedAtEpochSec: Long = 0,
    val error: String? = null,
    val refreshing: Boolean = false,
)

object WidgetFormat {
    /** Mockup-style countdown: "Now", "1 min", "12 mins", "1 hour", "3 hours". */
    fun countdown(departure: Instant, now: Instant): String {
        val minutes = Math.floorDiv(Duration.between(now, departure).seconds, 60L)
        return when {
            minutes <= 0 -> "Now"
            minutes == 1L -> "1 min"
            minutes < 60 -> "$minutes mins"
            minutes < 120 -> "1 hour"
            else -> "${minutes / 60} hours"
        }
    }

    /** "Then 12, 27 mins" for the departures after the next one. */
    fun later(departures: List<Instant>, now: Instant): String? {
        val minutes = departures.map { Math.floorDiv(Duration.between(now, it).seconds, 60L) }.filter { it > 0 }.take(3)
        if (minutes.isEmpty()) return null
        return if (minutes.all { it < 60 }) "Then ${minutes.joinToString(", ")} mins" else "Then " + departures.take(2).joinToString(", ") { TimeFormatterBridge.clock(it) }
    }

    /** Mockup-style distance: "50m away", "5.2km away". */
    fun distance(meters: Double): String = DistanceFormatter.distance(meters).replace(" ", "") + " away"
}

object WidgetStore {
    private const val PREFS = "journey_widget"
    private const val KEY = "snapshot"
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun read(context: Context): WidgetSnapshot =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
            ?.let { runCatching { json.decodeFromString(WidgetSnapshot.serializer(), it) }.getOrNull() }
            ?: WidgetSnapshot()

    fun write(context: Context, snapshot: WidgetSnapshot) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY, json.encodeToString(WidgetSnapshot.serializer(), snapshot))
            .apply()
    }

    fun setRefreshing(context: Context, refreshing: Boolean) = write(context, read(context).copy(refreshing = refreshing))
}

object WidgetSnapshotBuilder {
    const val MAX_ROWS = 8

    fun row(favourite: FavouriteStop, next: Departure?, distanceMeters: Double?, later: List<Departure> = emptyList()): WidgetRow = WidgetRow(
        stopId = favourite.stopId,
        routeType = favourite.routeType,
        name = favourite.displayName,
        subtitle = distanceMeters?.let(WidgetFormat::distance) ?: favourite.suburb,
        departureEpochSec = next?.bestTime?.epochSecond,
        isLive = next?.isLive == true,
        headsign = next?.headsign?.takeIf { it.isNotBlank() },
        routeLabel = next?.route?.badgeLabel,
        laterEpochSec = later.mapNotNull { it.bestTime?.epochSecond }.take(3),
    )

    /** Fetches the next departure for each favourite (in parallel) and builds a snapshot. */
    suspend fun build(container: AppContainer, now: Instant = Instant.now()): WidgetSnapshot {
        val favourites = container.favouritesRepository.favourites.first().take(MAX_ROWS)
        if (favourites.isEmpty()) return WidgetSnapshot(updatedAtEpochSec = now.epochSecond)
        val here = withTimeoutOrNull(4_000) { container.locationProvider.currentLocation() }
        var failure: Throwable? = null
        val rows = coroutineScope {
            favourites.map { fav ->
                async {
                    val upcoming = runCatching {
                        container.ptvRepository.departures(fav.mode, fav.stopId, maxResults = 4, force = true)
                            .departures
                            .filter { !it.isCancelled && it.bestTime?.isAfter(now.minusSeconds(30)) == true }
                    }.onFailure { failure = it }.getOrNull().orEmpty()
                    val next = upcoming.firstOrNull()
                    val distance = here?.let { GeoUtils.haversineMeters(it.latitude, it.longitude, fav.latitude, fav.longitude) }
                        ?.takeIf { fav.latitude != 0.0 }
                    row(fav, next, distance, later = upcoming.drop(1))
                }
            }.awaitAll()
        }
        val error = when {
            rows.any { it.departureEpochSec != null } -> null
            failure is PtvException.MissingCredentials -> "Add your PTV key in Journey"
            failure != null -> "Couldn't refresh"
            else -> null
        }
        return WidgetSnapshot(rows = rows, updatedAtEpochSec = now.epochSecond, error = error)
    }
}

/** Small indirection so WidgetFormat stays free of Android locale lookups in tests. */
internal object TimeFormatterBridge {
    fun clock(instant: Instant): String = com.ssethhyy.journey.utils.TimeFormatter.clock(instant)
}
