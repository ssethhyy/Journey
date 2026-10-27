/*
 * Feature Module: myki
 * File: Myki.kt
 * Description: myki cards kept on this phone: card number rules (prefix + Luhn), the card and
 * activity model, and pure ledger logic for balance updates and top ups. Stored as JSON.
 *
 * There is no public myki API, and balances/trips on the card are encrypted, so the balance is
 * whatever the user last entered (after checking at a station or in the PTV app) plus top ups
 * they record here.
 */

package com.ssethhyy.journey.data.myki

import android.content.Context
import com.ssethhyy.journey.data.fare.FareZone
import com.ssethhyy.journey.data.fare.PriorTrip
import com.ssethhyy.journey.data.model.TransportMode
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.util.UUID

enum class MykiFareType(val label: String) {
    FULL("Full fare"),
    CONCESSION("Concession"),
    CHILD("Child"),
    SENIORS("Seniors"),
}

enum class MykiActivityKind(val label: String) {
    TOP_UP("Top up"),
    BALANCE("Balance updated"),
    TRIP("Trip"),
    CHARGE("Deduction"),
}

/** What the fare engine needs to remember about a logged trip. */
@Serializable
data class MykiTripRecord(
    val startMillis: Long,
    val endMillis: Long,
    val zone: FareZone,
    val modes: List<TransportMode>,
    /** False for free trips (Early Bird, Free Tram Zone, children...), which don't open a 2-hour fare. */
    val paid: Boolean,
)

@Serializable
data class MykiActivity(
    val id: String,
    val kind: MykiActivityKind,
    /** Signed change in cents; null when the previous balance was unknown. */
    val amountCents: Long?,
    val atMillis: Long,
    val balanceAfterCents: Long? = null,
    /** For trips and deductions: what happened, e.g. "Flinders Street to Westgarth". */
    val title: String? = null,
    val detail: String? = null,
    val trip: MykiTripRecord? = null,
    /** The fare charged, kept even when the balance wasn't known so the day's cap stays right. */
    val chargedCents: Long? = null,
)

@Serializable
data class MykiCard(
    val id: String,
    val number: String,
    val nickname: String,
    val fareType: MykiFareType = MykiFareType.FULL,
    val balanceCents: Long? = null,
    val balanceUpdatedMillis: Long? = null,
    val activity: List<MykiActivity> = emptyList(),
    val addedMillis: Long = 0,
    val tipDismissed: Boolean = false,
    /** The card trips are logged to unless another is picked. Exactly one card is the default. */
    val isDefault: Boolean = false,
    /** Alert when the balance falls below this many cents; 0 turns the alert off. */
    val lowBalanceCents: Long = DEFAULT_LOW_BALANCE_CENTS,
    /** True once the alert for the current low spell has been sent. */
    val lowBalanceAlerted: Boolean = false,
) {
    val formattedNumber: String get() = MykiNumber.format(number)
    val lastFour: String get() = number.takeLast(5).take(4)

    /** Below the alert threshold right now (never when the balance is unknown or alerts are off). */
    val isLow: Boolean get() = balanceCents != null && lowBalanceCents > 0 && balanceCents < lowBalanceCents

    companion object {
        const val DEFAULT_LOW_BALANCE_CENTS = 1_000L
    }
}

/** myki card numbers: 15 digits, starting 308425, ending in a Luhn check digit. */
object MykiNumber {
    const val PREFIX = "308425"
    const val LENGTH = 15

    fun digits(input: String): String = input.filter(Char::isDigit)

    /** Luhn check digit for [payload] (the digits before the check digit). */
    fun luhnDigit(payload: String): Int {
        var sum = 0
        payload.reversed().forEachIndexed { i, c ->
            var d = c.digitToInt()
            if (i % 2 == 0) {
                d *= 2
                if (d > 9) d -= 9
            }
            sum += d
        }
        return (10 - sum % 10) % 10
    }

    fun isValid(input: String): Boolean {
        val n = digits(input)
        return n.length == LENGTH && n.startsWith(PREFIX) && luhnDigit(n.dropLast(1)) == n.last().digitToInt()
    }

    /** Why [input] is not a valid number yet, or null when it is. */
    fun problem(input: String): String? {
        val n = digits(input)
        return when {
            n.isEmpty() -> "Enter the 15-digit number on the back of your myki"
            !PREFIX.startsWith(n.take(PREFIX.length)) -> "myki numbers start with 3 08425"
            n.length < LENGTH -> "${LENGTH - n.length} more digits"
            n.length > LENGTH -> "That's more than 15 digits"
            !isValid(n) -> "Check the number, the last digit doesn't match"
            else -> null
        }
    }

    /** As printed on the card: 3 08425 1234 5678 0 (partial input is grouped the same way). */
    fun format(input: String): String {
        val n = digits(input)
        val groups = listOf(1, 5, 4, 4, 1)
        val out = StringBuilder()
        var i = 0
        for (size in groups) {
            if (i >= n.length) break
            if (out.isNotEmpty()) out.append(' ')
            out.append(n, i, minOf(n.length, i + size))
            i += size
        }
        if (i < n.length) out.append(' ').append(n.substring(i))
        return out.toString()
    }
}

/** Pure list logic, unit tested without Android. */
object MykiLedger {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }
    private val serializer = ListSerializer(MykiCard.serializer())

    fun encode(list: List<MykiCard>): String = json.encodeToString(serializer, list)
    fun decode(raw: String?): List<MykiCard> =
        if (raw.isNullOrBlank()) emptyList() else runCatching { json.decodeFromString(serializer, raw) }.getOrDefault(emptyList())

    fun find(list: List<MykiCard>, number: String): MykiCard? = MykiNumber.digits(number).let { n -> list.firstOrNull { it.number == n } }

    /** Adds a card unless its number is already saved (then the list is unchanged). The first card becomes the default. */
    fun add(list: List<MykiCard>, card: MykiCard): List<MykiCard> =
        if (find(list, card.number) != null) list
        else normalizeDefault(list + card.copy(number = MykiNumber.digits(card.number), isDefault = false))

    /** Exactly one default card whenever there are cards; the first one if none (or several) are marked. */
    fun normalizeDefault(list: List<MykiCard>): List<MykiCard> {
        if (list.isEmpty()) return list
        val keep = list.firstOrNull { it.isDefault }?.id ?: list.first().id
        return list.map { if (it.isDefault == (it.id == keep)) it else it.copy(isDefault = it.id == keep) }
    }

    fun setDefault(list: List<MykiCard>, id: String): List<MykiCard> =
        if (list.none { it.id == id }) list else list.map { it.copy(isDefault = it.id == id) }

    /** The default card first, the rest in the order they were added. */
    fun ordered(list: List<MykiCard>): List<MykiCard> = normalizeDefault(list).sortedByDescending { it.isDefault }

    fun defaultCard(list: List<MykiCard>): MykiCard? = ordered(list).firstOrNull()

    fun update(list: List<MykiCard>, id: String, transform: (MykiCard) -> MykiCard): List<MykiCard> =
        list.map { if (it.id == id) transform(it) else it }

    fun remove(list: List<MykiCard>, id: String): List<MykiCard> = normalizeDefault(list.filterNot { it.id == id })

    /** Records a balance the user read elsewhere; the change is shown when the old balance was known. */
    fun setBalance(card: MykiCard, cents: Long, atMillis: Long, id: String = newId()): MykiCard {
        val change = card.balanceCents?.let { cents - it }
        if (change == 0L) return card.copy(balanceUpdatedMillis = atMillis)
        val entry = MykiActivity(id, MykiActivityKind.BALANCE, change, atMillis, cents)
        return card.copy(balanceCents = cents, balanceUpdatedMillis = atMillis, activity = listOf(entry) + card.activity)
    }

    /** Adds a top up; the balance moves with it when it is known. */
    fun topUp(card: MykiCard, cents: Long, atMillis: Long, id: String = newId()): MykiCard {
        require(cents > 0) { "Top ups must be positive" }
        val after = card.balanceCents?.plus(cents)
        val entry = MykiActivity(id, MykiActivityKind.TOP_UP, cents, atMillis, after)
        return card.copy(
            balanceCents = after,
            balanceUpdatedMillis = if (after != null) atMillis else card.balanceUpdatedMillis,
            activity = listOf(entry) + card.activity,
        )
    }

    /**
     * Takes a fare off the card. With an unknown balance the balance stays unknown, but the entry is
     * still kept (with [chargedCents]) so the day's 2-hour fare and cap stay right.
     */
    fun deduct(
        card: MykiCard,
        cents: Long,
        atMillis: Long,
        kind: MykiActivityKind,
        title: String,
        detail: String? = null,
        trip: MykiTripRecord? = null,
        id: String = newId(),
    ): MykiCard {
        require(cents >= 0) { "Deductions must not be negative" }
        val after = card.balanceCents?.minus(cents)
        val entry = MykiActivity(
            id = id,
            kind = kind,
            amountCents = if (cents == 0L) 0L else -cents,
            atMillis = atMillis,
            balanceAfterCents = after,
            title = title,
            detail = detail,
            trip = trip,
            chargedCents = cents,
        )
        return card.copy(
            balanceCents = after,
            balanceUpdatedMillis = if (after != null) atMillis else card.balanceUpdatedMillis,
            activity = listOf(entry) + card.activity,
        )
    }

    /** Trips this card has been charged for, in the form the fare engine reads. */
    fun priorTrips(card: MykiCard): List<PriorTrip> = card.activity.mapNotNull { a ->
        val trip = a.trip ?: return@mapNotNull null
        PriorTrip(trip.startMillis, trip.zone, a.chargedCents ?: 0L, trip.paid)
    }

    /** Sets the one-alert-per-low-spell flag after a balance change; true when an alert should be sent now. */
    fun evaluateLowBalance(card: MykiCard): Pair<MykiCard, Boolean> = when {
        card.isLow && !card.lowBalanceAlerted -> card.copy(lowBalanceAlerted = true) to true
        !card.isLow && card.lowBalanceAlerted -> card.copy(lowBalanceAlerted = false) to false
        else -> card to false
    }

    fun newId(): String = UUID.randomUUID().toString()
}

/** Dollars text ("26.16", "$26", "26.1") to cents, or null. */
fun parseDollars(text: String): Long? {
    val clean = text.trim().removePrefix("$").replace(",", "")
    if (clean.isEmpty() || clean.count { it == '.' } > 1 || clean.any { !it.isDigit() && it != '.' }) return null
    val parts = clean.split('.')
    val dollars = parts[0].ifEmpty { "0" }.toLongOrNull() ?: return null
    val centsText = parts.getOrNull(1).orEmpty()
    if (centsText.length > 2) return null
    val cents = centsText.padEnd(2, '0').ifEmpty { "00" }.toLong()
    return dollars * 100 + cents
}

fun formatDollars(cents: Long, signed: Boolean = false): String {
    val sign = when {
        cents < 0 -> "−"
        signed && cents > 0 -> "+"
        else -> ""
    }
    val abs = kotlin.math.abs(cents)
    return "$sign$${abs / 100}.${(abs % 100).toString().padStart(2, '0')}"
}

private val Context.mykiStore: DataStore<Preferences> by preferencesDataStore(name = "journey_myki")

/** A card after a change, and whether the change crossed below its low-balance threshold. */
data class MykiChange(val card: MykiCard?, val alert: Boolean)

class MykiRepository(context: Context) {
    private val store = context.applicationContext.mykiStore
    private val cardsKey = stringPreferencesKey("cards")

    /** Default card first, with exactly one default whenever there are cards. */
    val cards: Flow<List<MykiCard>> = store.data.map { MykiLedger.ordered(MykiLedger.decode(it[cardsKey])) }

    private suspend fun edit(transform: (List<MykiCard>) -> List<MykiCard>) = store.edit { p ->
        p[cardsKey] = MykiLedger.encode(transform(MykiLedger.decode(p[cardsKey])))
    }

    suspend fun add(card: MykiCard) = edit { MykiLedger.add(it, card) }
    suspend fun update(id: String, transform: (MykiCard) -> MykiCard) = edit { MykiLedger.update(it, id, transform) }
    suspend fun setDefault(id: String) = edit { MykiLedger.setDefault(it, id) }

    /** Applies [transform] and updates the low-balance flag in one write; null card when [id] is gone. */
    suspend fun change(id: String, transform: (MykiCard) -> MykiCard): MykiChange {
        var result = MykiChange(null, false)
        store.edit { p ->
            val list = MykiLedger.decode(p[cardsKey])
            val target = list.firstOrNull { it.id == id } ?: return@edit
            val (checked, alert) = MykiLedger.evaluateLowBalance(transform(target))
            result = MykiChange(checked, alert)
            p[cardsKey] = MykiLedger.encode(MykiLedger.update(list, id) { checked })
        }
        return result
    }
    suspend fun remove(id: String) = edit { MykiLedger.remove(it, id) }
    suspend fun clear() = store.edit { it.remove(cardsKey) }
}
