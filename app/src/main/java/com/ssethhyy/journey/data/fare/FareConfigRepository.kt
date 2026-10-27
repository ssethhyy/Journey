/*
 * Feature Module: Fares
 * File: FareConfigRepository.kt
 * Description: Stores the fare table and discount rules. Built-in rules always follow the app's
 * defaults (the user can only switch them on or off); the table follows the defaults until the
 * user edits a price; custom rules (a free-travel week, a new half-price scheme) are fully editable.
 */

package com.ssethhyy.journey.data.fare

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate

/** What is stored: only the parts the user can change. */
@Serializable
data class StoredFares(
    val editedTable: FareTable? = null,
    /** Built-in rule ids the user has switched off. */
    val disabledBuiltIns: Set<String> = emptySet(),
    val customRules: List<FareRule> = emptyList(),
    val confirmedOn: String? = null,
)

object FareConfigs {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    fun encode(stored: StoredFares): String = json.encodeToString(StoredFares.serializer(), stored)
    fun decode(raw: String?): StoredFares =
        if (raw.isNullOrBlank()) StoredFares() else runCatching { json.decodeFromString(StoredFares.serializer(), raw) }.getOrDefault(StoredFares())

    /** The config in force: current defaults with the user's table edits, switches and custom rules on top. */
    fun resolve(stored: StoredFares): FareConfig = FareConfig(
        table = stored.editedTable ?: FareDefaults.table,
        rules = FareDefaults.rules.map { it.copy(enabled = it.id !in stored.disabledBuiltIns) } + stored.customRules.map { it.copy(builtIn = false) },
        confirmedOn = stored.confirmedOn,
    )

    /** Fares normally change on 1 January; ask the user to confirm the prices once after each one. */
    fun needsReview(config: FareConfig, today: LocalDate): Boolean {
        val nextChange = LocalDate.of(LocalDate.parse(config.table.effectiveFrom).year + 1, 1, 1)
        if (today.isBefore(nextChange)) return false
        val confirmed = config.confirmedOn?.let(LocalDate::parse)
        return confirmed == null || confirmed.isBefore(nextChange)
    }
}

private val Context.faresStore: DataStore<Preferences> by preferencesDataStore(name = "journey_fares")

class FareConfigRepository(context: Context) {
    private val store = context.applicationContext.faresStore
    private val key = stringPreferencesKey("fares")

    val config: Flow<FareConfig> = store.data.map { FareConfigs.resolve(FareConfigs.decode(it[key])) }

    private suspend fun edit(transform: (StoredFares) -> StoredFares) = store.edit { p ->
        p[key] = FareConfigs.encode(transform(FareConfigs.decode(p[key])))
    }

    suspend fun setTable(table: FareTable) = edit { it.copy(editedTable = table, confirmedOn = LocalDate.now().toString()) }
    suspend fun resetTable() = edit { it.copy(editedTable = null) }
    suspend fun confirmPrices(today: LocalDate = LocalDate.now()) = edit { it.copy(confirmedOn = today.toString()) }

    suspend fun setRuleEnabled(rule: FareRule, enabled: Boolean) = edit { stored ->
        if (rule.builtIn) {
            stored.copy(disabledBuiltIns = if (enabled) stored.disabledBuiltIns - rule.id else stored.disabledBuiltIns + rule.id)
        } else {
            stored.copy(customRules = stored.customRules.map { if (it.id == rule.id) it.copy(enabled = enabled) else it })
        }
    }

    suspend fun saveCustomRule(rule: FareRule) = edit { stored ->
        val clean = rule.copy(builtIn = false)
        val exists = stored.customRules.any { it.id == clean.id }
        stored.copy(customRules = if (exists) stored.customRules.map { if (it.id == clean.id) clean else it } else stored.customRules + clean)
    }

    suspend fun removeCustomRule(id: String) = edit { it.copy(customRules = it.customRules.filterNot { rule -> rule.id == id }) }
}
