/*
 * Feature Module: Data
 * File: FavouritesRepository.kt
 * Description: Persists favourite stops (per transport mode) as JSON in DataStore.
 */

package com.ssethhyy.journey.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ssethhyy.journey.data.model.Departure
import com.ssethhyy.journey.data.model.Stop
import com.ssethhyy.journey.data.model.TransportMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

/** One line (and optionally one direction) a favourite is limited to. */
@Serializable
data class FavouriteLine(
    val routeId: Int,
    /** "Frankston", "96", "246" as shown on route badges. */
    val routeLabel: String,
    /** Null means both directions of the line. */
    val directionId: Int? = null,
    val directionName: String? = null,
) {
    fun matches(departure: Departure): Boolean =
        departure.route?.id == routeId && (directionId == null || departure.directionId == directionId)

    /** "Frankston to City", or just "Frankston" for both directions. */
    val label: String get() = listOfNotNull(routeLabel, directionName?.let { "to $it" }).joinToString(" ")
}

@Serializable
data class FavouriteStop(
    val stopId: Int,
    val routeType: Int,
    val name: String,
    val suburb: String? = null,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val nickname: String? = null,
    val addedAtEpochMs: Long = 0,
    /** The lines and directions to show. Empty means every departure from the stop. */
    val lines: List<FavouriteLine> = emptyList(),
) {
    val mode: TransportMode get() = TransportMode.fromRouteType(routeType)
    val key: String get() = Stop.stopKey(mode, stopId)
    val displayName: String get() = nickname?.takeIf { it.isNotBlank() } ?: name

    val showsEverything: Boolean get() = lines.isEmpty()

    /** Departures this favourite cares about. */
    fun filter(departures: List<Departure>): List<Departure> =
        if (lines.isEmpty()) departures else departures.filter { d -> lines.any { it.matches(d) } }

    /** "Frankston to City · 96" for a favourite limited to some lines, null when it shows everything. */
    val linesSummary: String? get() = lines.takeIf { it.isNotEmpty() }?.joinToString(" · ") { it.label }

    fun toStop(): Stop = Stop(
        id = stopId,
        mode = mode,
        name = name,
        suburb = suburb,
        latitude = latitude,
        longitude = longitude,
    )

    companion object {
        fun from(stop: Stop, now: Long = System.currentTimeMillis(), lines: List<FavouriteLine> = emptyList()) = FavouriteStop(
            stopId = stop.id,
            routeType = stop.mode.routeType,
            name = stop.name,
            suburb = stop.suburb,
            latitude = stop.latitude,
            longitude = stop.longitude,
            addedAtEpochMs = now,
            lines = lines,
        )
    }
}

/** Pure list operations, kept separate so they can be unit tested without Android. */
object FavouritesCodec {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = false }
    private val prettyJson = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = true }
    private val serializer = ListSerializer(FavouriteStop.serializer())

    fun encode(list: List<FavouriteStop>, pretty: Boolean = false): String =
        (if (pretty) prettyJson else json).encodeToString(serializer, list)

    fun decode(raw: String?): List<FavouriteStop> =
        if (raw.isNullOrBlank()) emptyList() else runCatching { json.decodeFromString(serializer, raw) }.getOrDefault(emptyList())

    fun add(list: List<FavouriteStop>, item: FavouriteStop): List<FavouriteStop> =
        if (list.any { it.key == item.key }) list else list + item

    fun remove(list: List<FavouriteStop>, key: String): List<FavouriteStop> = list.filterNot { it.key == key }

    fun setLines(list: List<FavouriteStop>, key: String, lines: List<FavouriteLine>): List<FavouriteStop> =
        list.map { if (it.key == key) it.copy(lines = lines.distinctBy { l -> l.routeId to l.directionId }) else it }

    fun rename(list: List<FavouriteStop>, key: String, nickname: String?): List<FavouriteStop> =
        list.map { if (it.key == key) it.copy(nickname = nickname?.trim()?.takeIf { n -> n.isNotEmpty() }) else it }

    /** Moves an item by [delta] positions within its own transport mode group. */
    fun move(list: List<FavouriteStop>, key: String, delta: Int): List<FavouriteStop> {
        val item = list.firstOrNull { it.key == key } ?: return list
        val sameMode = list.withIndex().filter { it.value.routeType == item.routeType }
        val groupPos = sameMode.indexOfFirst { it.value.key == key }
        val targetPos = (groupPos + delta).coerceIn(0, sameMode.lastIndex)
        if (targetPos == groupPos) return list
        val from = sameMode[groupPos].index
        val to = sameMode[targetPos].index
        return list.toMutableList().apply { add(to, removeAt(from)) }
    }

    /** Merges imported favourites, keeping existing entries and their order. */
    fun merge(existing: List<FavouriteStop>, imported: List<FavouriteStop>): List<FavouriteStop> =
        imported.fold(existing) { acc, item -> add(acc, item) }
}

private val Context.favouritesStore: DataStore<Preferences> by preferencesDataStore(name = "journey_favourites")

class FavouritesRepository(context: Context) {
    private val store = context.applicationContext.favouritesStore
    private val key = stringPreferencesKey("favourite_stops")

    val favourites: Flow<List<FavouriteStop>> = store.data.map { FavouritesCodec.decode(it[key]) }

    val favouriteKeys: Flow<Set<String>> = favourites.map { list -> list.map { it.key }.toSet() }

    private suspend fun update(transform: (List<FavouriteStop>) -> List<FavouriteStop>) {
        store.edit { prefs ->
            prefs[key] = FavouritesCodec.encode(transform(FavouritesCodec.decode(prefs[key])))
        }
    }

    suspend fun add(stop: Stop, lines: List<FavouriteLine> = emptyList()) = update { list ->
        if (list.any { it.key == stop.key }) FavouritesCodec.setLines(list, stop.key, lines) else FavouritesCodec.add(list, FavouriteStop.from(stop, lines = lines))
    }

    suspend fun setLines(stopKey: String, lines: List<FavouriteLine>) = update { FavouritesCodec.setLines(it, stopKey, lines) }

    suspend fun isFavourite(stopKey: String): Boolean = favourites.first().any { it.key == stopKey }

    suspend fun remove(stopKey: String) = update { FavouritesCodec.remove(it, stopKey) }

    /** Returns true if the stop is now a favourite. */
    suspend fun toggle(stop: Stop): Boolean {
        var added = false
        update { list ->
            if (list.any { it.key == stop.key }) {
                FavouritesCodec.remove(list, stop.key)
            } else {
                added = true
                FavouritesCodec.add(list, FavouriteStop.from(stop))
            }
        }
        return added
    }

    suspend fun rename(stopKey: String, nickname: String?) = update { FavouritesCodec.rename(it, stopKey, nickname) }

    suspend fun move(stopKey: String, delta: Int) = update { FavouritesCodec.move(it, stopKey, delta) }

    suspend fun clear() = update { emptyList() }

    suspend fun exportJson(): String = FavouritesCodec.encode(favourites.first(), pretty = true)

    /** Returns the number of favourites added. */
    suspend fun importJson(raw: String): Int {
        val imported = FavouritesCodec.decode(raw)
        var added = 0
        update { existing ->
            val merged = FavouritesCodec.merge(existing, imported)
            added = merged.size - existing.size
            merged
        }
        return added
    }
}
