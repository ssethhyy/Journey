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
import com.ssethhyy.journey.data.model.Stop
import com.ssethhyy.journey.data.model.TransportMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json

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
) {
    val mode: TransportMode get() = TransportMode.fromRouteType(routeType)
    val key: String get() = Stop.stopKey(mode, stopId)
    val displayName: String get() = nickname?.takeIf { it.isNotBlank() } ?: name

    fun toStop(): Stop = Stop(
        id = stopId,
        mode = mode,
        name = name,
        suburb = suburb,
        latitude = latitude,
        longitude = longitude,
    )

    companion object {
        fun from(stop: Stop, now: Long = System.currentTimeMillis()) = FavouriteStop(
            stopId = stop.id,
            routeType = stop.mode.routeType,
            name = stop.name,
            suburb = stop.suburb,
            latitude = stop.latitude,
            longitude = stop.longitude,
            addedAtEpochMs = now,
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

    suspend fun add(stop: Stop) = update { FavouritesCodec.add(it, FavouriteStop.from(stop)) }

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
