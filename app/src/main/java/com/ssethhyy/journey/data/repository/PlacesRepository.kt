/*
 * Feature Module: Data
 * File: PlacesRepository.kt
 * Description: Saved places and recent journey destinations, stored as JSON.
 */

package com.ssethhyy.journey.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ssethhyy.journey.data.planner.PlanLocation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.UUID

/** Icons a saved place can use. Any place can be a Home or Work. */
enum class PlaceIcon(val label: String) {
    HOME("Home"),
    WORK("Work"),
    SCHOOL("School"),
    STAR("Star"),
    HEART("Favourite"),
    FITNESS("Gym"),
    SHOPPING("Shopping"),
    FOOD("Food"),
    COFFEE("Coffee"),
    PARK("Park"),
    HOSPITAL("Health"),
    FAMILY("Family"),
}

@Serializable
data class SavedPlace(
    val id: String,
    val name: String,
    val address: String? = null,
    val latitude: Double,
    val longitude: Double,
    val icon: PlaceIcon = PlaceIcon.STAR,
) {
    fun toPlanLocation() = PlanLocation(
        name = name,
        latitude = latitude,
        longitude = longitude,
        subtitle = address,
        placeId = id,
    )
}

@Serializable
data class RecentTrip(
    val fromName: String,
    val fromLat: Double,
    val fromLon: Double,
    val fromIsCurrent: Boolean,
    val toName: String,
    val toLat: Double,
    val toLon: Double,
    val toSubtitle: String? = null,
) {
    val key: String get() = "${if (fromIsCurrent) "here" else "%.4f,%.4f".format(fromLat, fromLon)}>${"%.4f,%.4f".format(toLat, toLon)}"
}

/** Pure list logic, unit tested without Android. */
object PlacesCodec {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val placesSerializer = ListSerializer(SavedPlace.serializer())
    private val recentSerializer = ListSerializer(RecentTrip.serializer())

    fun encodePlaces(list: List<SavedPlace>): String = json.encodeToString(placesSerializer, list)
    fun decodePlaces(raw: String?): List<SavedPlace> =
        if (raw.isNullOrBlank()) emptyList() else runCatching { json.decodeFromString(placesSerializer, raw) }.getOrDefault(emptyList())

    fun encodeRecents(list: List<RecentTrip>): String = json.encodeToString(recentSerializer, list)
    fun decodeRecents(raw: String?): List<RecentTrip> =
        if (raw.isNullOrBlank()) emptyList() else runCatching { json.decodeFromString(recentSerializer, raw) }.getOrDefault(emptyList())

    /** Saved places are unique by id: saving an existing id replaces it. */
    fun upsert(list: List<SavedPlace>, place: SavedPlace): List<SavedPlace> {
        val index = list.indexOfFirst { it.id == place.id }
        if (index < 0) return list + place
        return list.mapIndexed { i, other -> if (i == index) place else other }
    }

    fun sorted(list: List<SavedPlace>): List<SavedPlace> = list

    fun addRecent(list: List<RecentTrip>, trip: RecentTrip, max: Int = 6): List<RecentTrip> =
        (listOf(trip) + list.filterNot { it.key == trip.key }).take(max)
}

private val Context.placesStore: DataStore<Preferences> by preferencesDataStore(name = "journey_places")

class PlacesRepository(context: Context) {
    private val store = context.applicationContext.placesStore
    private val placesKey = stringPreferencesKey("saved_places")
    private val recentsKey = stringPreferencesKey("recent_trips")

    val places: Flow<List<SavedPlace>> = store.data.map { PlacesCodec.sorted(PlacesCodec.decodePlaces(it[placesKey])) }
    val recentTrips: Flow<List<RecentTrip>> = store.data.map { PlacesCodec.decodeRecents(it[recentsKey]) }

    suspend fun save(place: SavedPlace) = store.edit { p ->
        p[placesKey] = PlacesCodec.encodePlaces(PlacesCodec.upsert(PlacesCodec.decodePlaces(p[placesKey]), place))
    }

    suspend fun remove(id: String) = store.edit { p ->
        p[placesKey] = PlacesCodec.encodePlaces(PlacesCodec.decodePlaces(p[placesKey]).filterNot { it.id == id })
    }

    suspend fun addRecent(trip: RecentTrip) = store.edit { p ->
        p[recentsKey] = PlacesCodec.encodeRecents(PlacesCodec.addRecent(PlacesCodec.decodeRecents(p[recentsKey]), trip))
    }

    suspend fun clearRecents() = store.edit { it.remove(recentsKey) }

    companion object {
        fun newId(): String = UUID.randomUUID().toString()
    }
}
