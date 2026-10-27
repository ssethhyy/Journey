/*
 * Feature Module: myki
 * File: MykiManager.kt
 * Description: Everything that changes a myki's balance goes through here, so the low-balance alert
 * fires no matter what moved the balance: logged trips, custom deductions, top ups and balance edits.
 * Also turns journeys and picked stops into fare-engine trips using PTV's fare zones.
 */

package com.ssethhyy.journey.data.myki

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.ssethhyy.journey.MainActivity
import com.ssethhyy.journey.R
import com.ssethhyy.journey.data.fare.FareConfigRepository
import com.ssethhyy.journey.data.fare.FareEngine
import com.ssethhyy.journey.data.fare.FareLeg
import com.ssethhyy.journey.data.fare.FareQuote
import com.ssethhyy.journey.data.fare.FareTrip
import com.ssethhyy.journey.data.fare.FareZone
import com.ssethhyy.journey.data.fare.StopFare
import com.ssethhyy.journey.data.model.Stop
import com.ssethhyy.journey.data.model.TransportMode
import com.ssethhyy.journey.data.planner.Journey
import com.ssethhyy.journey.data.planner.LegKind
import com.ssethhyy.journey.data.repository.PtvRepository
import kotlinx.coroutines.flow.first

/** A trip built from a journey or picked stops, and whether every zone came from PTV. */
data class ResolvedTrip(val trip: FareTrip, val zonesKnown: Boolean)

class MykiNotifier(private val context: Context) {
    private fun canPost(): Boolean =
        android.os.Build.VERSION.SDK_INT < 33 ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun lowBalance(card: MykiCard) {
        if (!canPost()) return
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "myki balance", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Alerts when a myki's balance runs low"
                },
            )
        }
        val open = PendingIntent.getActivity(
            context,
            card.id.hashCode(),
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
                .putExtra(EXTRA_MYKI_CARD_ID, card.id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val balance = card.balanceCents?.let { formatDollars(it) } ?: return
        nm.notify(
            card.id.hashCode(),
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_stat_tracking)
                .setContentTitle("Low myki balance")
                .setContentText("${card.nickname} has $balance left. Top up before you travel.")
                .setContentIntent(open)
                .setAutoCancel(true)
                .setCategory(NotificationCompat.CATEGORY_STATUS)
                .build(),
        )
    }

    companion object {
        const val CHANNEL_ID = "myki_balance"
        const val EXTRA_MYKI_CARD_ID = "com.ssethhyy.journey.extra.MYKI_CARD_ID"
    }
}

class MykiManager(
    val repository: MykiRepository,
    val fares: FareConfigRepository,
    private val ptv: PtvRepository,
    private val notifier: MykiNotifier,
) {
    suspend fun cardsNow(): List<MykiCard> = repository.cards.first()

    /** Applies [transform] to a card and sends the low-balance alert if that pushed it under its threshold. */
    suspend fun change(id: String, transform: (MykiCard) -> MykiCard): MykiCard? {
        val result = repository.change(id, transform)
        if (result.alert) result.card?.let(notifier::lowBalance)
        return result.card
    }

    suspend fun setBalance(id: String, cents: Long) = change(id) { MykiLedger.setBalance(it, cents, System.currentTimeMillis()) }
    suspend fun topUp(id: String, cents: Long) = change(id) { MykiLedger.topUp(it, cents, System.currentTimeMillis()) }
    suspend fun setLowBalance(id: String, cents: Long) = change(id) { it.copy(lowBalanceCents = cents.coerceAtLeast(0), lowBalanceAlerted = false) }

    suspend fun quote(card: MykiCard, trip: FareTrip): FareQuote =
        FareEngine.quote(fares.config.first(), card.fareType, trip, MykiLedger.priorTrips(card))

    /** Deducts a trip's fare ([amountOverride] replaces the calculated one). Returns null when there is no amount. */
    suspend fun logTrip(
        cardId: String,
        trip: FareTrip,
        quote: FareQuote,
        title: String,
        detail: String? = null,
        amountOverride: Long? = null,
    ): MykiCard? {
        val charge = amountOverride ?: quote.chargeCents ?: return null
        val paid = if (amountOverride != null) charge > 0 else quote.isPaid
        val record = MykiTripRecord(trip.startMillis, trip.endMillis, trip.zone, trip.modes, paid)
        val now = System.currentTimeMillis()
        return change(cardId) { MykiLedger.deduct(it, charge, now, MykiActivityKind.TRIP, title, detail, record) }
    }

    suspend fun deductCustom(cardId: String, cents: Long, title: String, detail: String? = null): MykiCard? {
        val now = System.currentTimeMillis()
        return change(cardId) { MykiLedger.deduct(it, cents, now, MykiActivityKind.CHARGE, title.ifBlank { "Custom deduction" }, detail) }
    }

    // ---- Building trips ----

    private fun fareMode(mode: TransportMode) = if (mode == TransportMode.NIGHT_BUS) TransportMode.BUS else mode

    /** A planned journey as a fare trip; zones come from the stops nearest each leg's ends. */
    suspend fun tripFor(journey: Journey): ResolvedTrip? {
        val transit = journey.legs.filter { it.kind == LegKind.TRANSIT && it.mode != null }
        if (transit.isEmpty()) return null
        var known = true
        val legs = transit.map { leg ->
            val mode = fareMode(leg.mode!!)
            val ends = listOfNotNull(leg.path.firstOrNull(), leg.path.lastOrNull())
            val fares: List<StopFare> = ends.mapNotNull { ptv.stopFareNear(mode, it.latitude, it.longitude) }
            if (fares.size < 2) known = false
            FareLeg(mode, FareEngine.legZone(mode, fares))
        }
        return ResolvedTrip(FareTrip(transit.first().start.toEpochMilli(), transit.last().end.toEpochMilli(), legs), known)
    }

    /** One leg between two stops the user picked. */
    suspend fun tripFor(mode: TransportMode, from: Stop, to: Stop, startMillis: Long, minutes: Long = 30): ResolvedTrip {
        val m = fareMode(mode)
        val fares = listOfNotNull(ptv.stopFare(from.mode, from.id), ptv.stopFare(to.mode, to.id))
        val leg = FareLeg(m, FareEngine.legZone(m, fares))
        return ResolvedTrip(FareTrip(startMillis, startMillis + minutes * 60_000, listOf(leg)), fares.size == 2)
    }

    /** The user's own choice of zone for a trip whose zones can't be seen. */
    fun withZone(resolved: ResolvedTrip, zone: FareZone): FareTrip =
        resolved.trip.copy(legs = resolved.trip.legs.map { if (it.mode == TransportMode.VLINE) it else it.copy(zone = zone) })
}
