/*
 * Feature Module: Commutes
 * File: CommuteRepository.kt
 * Description: Stores the user's commutes and the journey history they are learned from.
 */

package com.ssethhyy.journey.data.commute

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.ssethhyy.journey.data.planner.PlanLocation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json
import java.time.Instant
import java.time.ZoneId
import java.util.UUID

private val Context.commuteStore: DataStore<Preferences> by preferencesDataStore(name = "journey_commutes")

class CommuteRepository(context: Context) {
    private val store = context.applicationContext.commuteStore
    private val key = stringPreferencesKey("commutes")
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private fun decode(raw: String?): CommuteData =
        if (raw.isNullOrBlank()) CommuteData() else runCatching { json.decodeFromString(CommuteData.serializer(), raw) }.getOrDefault(CommuteData())

    val data: Flow<CommuteData> = store.data.map { decode(it[key]) }

    private suspend fun update(block: (CommuteData) -> CommuteData) {
        store.edit { p -> p[key] = json.encodeToString(CommuteData.serializer(), block(decode(p[key]))) }
    }

    /** Remembers that the user planned this trip, so frequent ones become commutes. */
    suspend fun record(from: PlanLocation, to: PlanLocation, now: Instant = Instant.now(), zone: ZoneId = ZoneId.systemDefault()) =
        update { CommuteLogic.record(it, CommuteEnd.from(from), CommuteEnd.from(to), now, zone) }

    suspend fun save(commute: Commute) = update { CommuteLogic.upsertManual(it, commute) }
    suspend fun removeManual(id: String) = update { CommuteLogic.removeManual(it, id) }
    suspend fun hide(key: String) = update { CommuteLogic.hide(it, key) }
    suspend fun clear() {
        store.edit { it.remove(key) }
    }

    companion object {
        fun newId(): String = UUID.randomUUID().toString()
    }
}
