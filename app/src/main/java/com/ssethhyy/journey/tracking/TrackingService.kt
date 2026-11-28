/*
 * Feature Module: Tracking
 * File: TrackingService.kt
 * Description: Foreground service that follows a service or journey and keeps a Live Update
 * notification current (promoted ongoing + ProgressStyle on Android 16+).
 */

package com.ssethhyy.journey.tracking

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.graphics.drawable.Icon
import android.os.Build
import android.os.IBinder
import androidx.core.content.ContextCompat
import com.ssethhyy.journey.MainActivity
import com.ssethhyy.journey.R
import com.ssethhyy.journey.appContainer
import com.ssethhyy.journey.data.crowding.CrowdingAlertPolicy
import com.ssethhyy.journey.data.crowding.CrowdingResult
import com.ssethhyy.journey.data.crowding.Occupancy
import com.ssethhyy.journey.data.crowding.VehicleCrowding
import com.ssethhyy.journey.ui.theme.ModeColors
import com.ssethhyy.journey.utils.HapticUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import androidx.compose.ui.graphics.toArgb
import java.time.Instant

/** App-wide tracking state, observed by the UI (banner, Track buttons). */
class TrackingManager(private val context: Context) {
    private val _target = MutableStateFlow<TrackingTarget?>(null)
    val target: StateFlow<TrackingTarget?> = _target.asStateFlow()

    private val _status = MutableStateFlow<TrackingStatus?>(null)
    val status: StateFlow<TrackingStatus?> = _status.asStateFlow()

    fun start(target: TrackingTarget) {
        HapticUtil.performSuccessHaptic(context)
        _target.value = target
        _status.value = null
        ContextCompat.startForegroundService(context, Intent(context, TrackingService::class.java).setAction(TrackingService.ACTION_START))
    }

    fun stop() {
        context.startService(Intent(context, TrackingService::class.java).setAction(TrackingService.ACTION_STOP))
    }

    internal fun publish(status: TrackingStatus?) {
        _status.value = status
    }

    internal fun clear() {
        _target.value = null
        _status.value = null
    }

    fun canPostNotifications(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
}

class TrackingService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var loop: Job? = null
    private val manager get() = appContainer.trackingManager

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopTracking()
            return START_NOT_STICKY
        }
        val target = manager.target.value
        if (target == null) {
            stopTracking()
            return START_NOT_STICKY
        }
        ensureChannel()
        val placeholder = TrackingStatus("Tracking…", "Getting live times", "…", 0f, false, false, null, Instant.now())
        val notification = buildNotification(target, placeholder)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Location (when allowed) drives the "get off here" alert, the pace to aim for and when to leave.
            val type = if (appContainer.locationProvider.hasPermission()) {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE or ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            } else {
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            }
            runCatching { startForeground(NOTIFICATION_ID, notification, type) }
                .onFailure { startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) }
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
        loop?.cancel()
        loop = scope.launch { track(target) }
        return START_NOT_STICKY
    }

    private suspend fun track(target: TrackingTarget) {
        val container = appContainer
        val startedAt = Instant.now()
        var finishedAt: Instant? = null
        var lastStatus: TrackingStatus? = null
        var lastFetch = Instant.EPOCH
        var departure: com.ssethhyy.journey.data.model.Departure? = null
        var lastCrowdAlert: Occupancy? = null
        val stopAlertsShown = mutableSetOf<String>()
        val leaveAlertsShown = mutableSetOf<String>()
        var here: com.ssethhyy.journey.data.location.LatLng? = null
        var journey = (target as? TrackingTarget.Trip)?.let { container.journeyRepository.journey(it.journeyId) }

        while (scope.isActive) {
            val now = Instant.now()
            // Fetch fresh live data every 30 s; re-render the countdown every 15 s in between.
            if (now.isAfter(lastFetch.plusSeconds(29))) {
                lastFetch = now
                when (target) {
                    is TrackingTarget.Service -> runCatching {
                        container.ptvRepository.departures(target.mode, target.stopId, maxResults = 12, force = true)
                    }.onSuccess { board ->
                        departure = board.departures.firstOrNull { it.runRef == target.runRef }
                    }.also { lastCrowdAlert = checkCrowding(target, lastCrowdAlert) }
                    is TrackingTarget.Trip -> runCatching { container.journeyRepository.refreshJourney(target.journeyId) }
                        .onSuccess { refreshed -> if (refreshed != null) journey = refreshed }
                }
                // Where you are, for the pace and the leave alert (only while you're still on your way to board).
                val settings = container.settingsRepository.current()
                here = if ((settings.trackingPace || settings.trackingLeaveAlert) && container.locationProvider.hasPermission() &&
                    boarding(target, departure, journey, now) != null
                ) runCatching { container.locationProvider.currentLocation() }.getOrNull() ?: here else null
            }
            // "Get off here": once per ride, when you're close to the stop or it's about to arrive.
            if (target is TrackingTarget.Trip) journey?.let { j -> checkStopAlert(j, now, stopAlertsShown) }
            val baseStatus = when (target) {
                is TrackingTarget.Service -> TrackingLogic.serviceStatus(target, departure, startedAt, now)
                is TrackingTarget.Trip -> journey?.let { TrackingLogic.journeyStatus(it, target.toName, now) }
                    ?: TrackingStatus("Journey unavailable", "Plan the trip again to keep tracking", "—", 1f, false, true, null, now)
            }
            val board = boarding(target, departure, journey, now)
            val distance = board?.let { b -> here?.let { h -> com.ssethhyy.journey.utils.GeoUtils.haversineMeters(h.latitude, h.longitude, b.latitude, b.longitude) } }
            val settings = container.settingsRepository.current()
            val status = if (settings.trackingPace && !baseStatus.finished && board != null && distance != null) {
                baseStatus.copy(pace = PaceLogic.pace(distance, board.departs, now))
            } else baseStatus
            // "Time to leave": once per service, when an ordinary walk from here would only just make it.
            if (settings.trackingLeaveAlert && board != null && distance != null && distance >= PaceLogic.AT_STOP_METERS &&
                board.key !in leaveAlertsShown && now.isBefore(board.departs) && !now.isBefore(PaceLogic.leaveAt(distance, board.departs).minusSeconds(15))
            ) {
                leaveAlertsShown += board.key
                notifyLeave(board, distance)
            }
            if (status != lastStatus) {
                // Taps on the wrist of the phone: leaving now needs you; arriving is a small "well done".
                if (status.chip == "Now" && !status.finished && lastStatus?.chip != "Now") HapticUtil.performAttentionHaptic(this@TrackingService)
                if (status.finished && lastStatus?.finished != true && status.chip == "Arrived") HapticUtil.performSuccessHaptic(this@TrackingService)
                lastStatus = status
                manager.publish(status)
                notify(target, status)
            }
            if (status.finished) {
                finishedAt = finishedAt ?: now
                if (now.isAfter(finishedAt.plusSeconds(120))) break
            }
            delay(15_000)
        }
        stopTracking()
    }

    /** The stop you're heading to board at, and when the service leaves it. */
    private data class Boarding(val key: String, val departs: Instant, val latitude: Double, val longitude: Double, val stopName: String, val label: String)

    /** Where and when you board next; null once you're on board (or there's nothing left to catch). */
    private suspend fun boarding(
        target: TrackingTarget,
        departure: com.ssethhyy.journey.data.model.Departure?,
        journey: com.ssethhyy.journey.data.planner.Journey?,
        now: Instant,
    ): Boarding? = when (target) {
        is TrackingTarget.Service -> {
            val time = departure?.bestTime?.takeIf { it.isAfter(now) }
            val stop = time?.let { runCatching { appContainer.ptvRepository.stopDetails(target.mode, target.stopId).stop }.getOrNull() }
            if (time == null || stop == null || (stop.latitude == 0.0 && stop.longitude == 0.0)) null
            else Boarding(target.id, time, stop.latitude, stop.longitude, target.stopName, target.routeLabel)
        }
        is TrackingTarget.Trip -> journey?.legs
            ?.firstOrNull { it.kind == com.ssethhyy.journey.data.planner.LegKind.TRANSIT && it.start.isAfter(now) }
            ?.let { leg ->
                val from = leg.path.firstOrNull() ?: return@let null
                Boarding("${target.id}@${leg.start.epochSecond}", leg.start, from.latitude, from.longitude, leg.fromName.ifBlank { "your stop" }, leg.label)
            }
    }

    /** "Leave now": a separate alert, since it needs you to act. */
    private fun notifyLeave(board: Boarding, distance: Double) {
        if (!manager.canPostNotifications()) return
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(LEAVE_CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(LEAVE_CHANNEL_ID, "Time to leave", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "When to set off to catch the service you're tracking, from where you are"
                },
            )
        }
        val open = PendingIntent.getActivity(
            this,
            3,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = Notification.Builder(this, LEAVE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_tracking)
            .setContentTitle("Leave now for the ${board.label}")
            .setContentText("About ${PaceLogic.walkMinutes(distance)} min walk to ${board.stopName} · leaves ${com.ssethhyy.journey.utils.TimeFormatter.clock(board.departs)}")
            .setCategory(Notification.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        nm.notify(LEAVE_NOTIFICATION_ID, notification)
        HapticUtil.performAttentionHaptic(this)
    }

    private suspend fun checkStopAlert(journey: com.ssethhyy.journey.data.planner.Journey, now: Instant, shown: MutableSet<String>) {
        val container = appContainer
        val settings = container.settingsRepository.current()
        if (!settings.stopAlerts) return
        // Only look up the location while a ride is under way, since that's the only time it matters.
        val riding = journey.legs.any { it.kind == com.ssethhyy.journey.data.planner.LegKind.TRANSIT && !now.isBefore(it.start) && !now.isAfter(it.end.plusSeconds(120)) }
        if (!riding) return
        val here = runCatching { container.locationProvider.currentLocation() }.getOrNull()
        val alert = StopAlertLogic.due(journey, here, now, shown) ?: return
        shown += alert.key
        StopAlertNotifier.show(this, alert, vibrate = settings.stopAlertVibrate)
    }

    /** Alerts once when the tracked service reaches the user's crowding level, again only if it gets worse. */
    private suspend fun checkCrowding(target: TrackingTarget.Service, lastAlerted: Occupancy?): Occupancy? {
        val container = appContainer
        val settings = container.settingsRepository.current()
        if (!settings.crowdingAlerts || !container.crowdingRepository.isConfigured()) return lastAlerted
        val result = runCatching {
            val pattern = container.ptvRepository.stoppingPattern(target.runRef, target.mode, withPath = false)
            container.crowdingRepository.crowdingFor(
                target.mode,
                pattern.vehicle?.id,
                pattern.vehiclePosition?.let { it.latitude to it.longitude },
            )
        }.getOrNull() as? CrowdingResult.Found ?: return lastAlerted
        val decision = CrowdingAlertPolicy.next(lastAlerted, result.crowding.overall, settings.crowdingAlertLevel.minimum)
        if (decision.alert) notifyCrowding(target, result.crowding)
        return decision.lastAlerted
    }

    private fun notifyCrowding(target: TrackingTarget.Service, crowding: VehicleCrowding) {
        if (!manager.canPostNotifications()) return
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CROWDING_CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CROWDING_CHANNEL_ID, "Crowding alerts", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "When the service you're tracking gets busy"
                },
            )
        }
        val open = PendingIntent.getActivity(
            this,
            2,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = Notification.Builder(this, CROWDING_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_tracking)
            .setContentTitle("${target.routeLabel} to ${target.headsign} is busy")
            .setContentText(CrowdingAlertPolicy.message(crowding))
            .setCategory(Notification.CATEGORY_STATUS)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        nm.notify(CROWDING_NOTIFICATION_ID, notification)
    }

    private fun stopTracking() {
        loop?.cancel()
        manager.clear()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private fun notify(target: TrackingTarget, status: TrackingStatus) {
        if (!manager.canPostNotifications()) return
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, buildNotification(target, status))
    }

    private fun ensureChannel() {
        val nm = getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Live tracking", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Live countdown for the service or journey you're tracking"
                    setSound(null, null)
                    enableVibration(false)
                },
            )
        }
    }

    private fun buildNotification(target: TrackingTarget, status: TrackingStatus): Notification {
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, TrackingService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val accent = status.mode?.let { ModeColors.of(it).toArgb() } ?: 0xFF285EA8.toInt()
        val builder = Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_tracking)
            .setContentTitle(status.title)
            // The pace to aim for leads the line, so it's the first thing you read.
            .setContentText(listOfNotNull(status.pace?.label, status.detail + if (status.isLive) " · Live" else "").joinToString(" · "))
            .setSubText(if (target is TrackingTarget.Trip) "To ${target.toName}" else null)
            .setColor(accent)
            .setOngoing(!status.finished)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_NAVIGATION)
            .setContentIntent(open)
            .setShowWhen(false)
            .addAction(Notification.Action.Builder(null as Icon?, "Stop tracking", stop).build())

        if (Build.VERSION.SDK_INT >= 36) {
            // Android 16 Live Updates: promoted ongoing notification, status bar chip and ProgressStyle.
            runCatching {
                builder.setShortCriticalText(status.chip)
                builder.setRequestPromotedOngoing(!status.finished)
                val progress = (status.progress * 1000).toInt()
                builder.setStyle(
                    Notification.ProgressStyle()
                        .setStyledByProgress(true)
                        .setProgress(progress)
                        // The tracker shows how to move: walking, or running when you need to hurry.
                        .setProgressTrackerIcon(Icon.createWithResource(this, paceIcon(status.pace)))
                        .setProgressSegments(listOf(Notification.ProgressStyle.Segment(1000).setColor(accent))),
                )
            }
        } else {
            builder.setProgress(1000, (status.progress * 1000).toInt(), false)
        }
        return builder.build()
    }

    private fun paceIcon(pace: Pace?): Int = when (pace) {
        null -> R.drawable.ic_stat_tracking
        Pace.LEISURELY, Pace.WALK, Pace.BRISK -> R.drawable.rounded_directions_walk_24px
        Pace.JOG, Pace.RUN, Pace.TOO_LATE -> R.drawable.ic_pace_run
    }

    companion object {
        const val ACTION_START = "com.ssethhyy.journey.tracking.START"
        const val ACTION_STOP = "com.ssethhyy.journey.tracking.STOP"
        private const val CHANNEL_ID = "live_tracking"
        private const val NOTIFICATION_ID = 4201
        private const val CROWDING_CHANNEL_ID = "crowding_alerts"
        private const val CROWDING_NOTIFICATION_ID = 4202
        private const val LEAVE_CHANNEL_ID = "time_to_leave"
        private const val LEAVE_NOTIFICATION_ID = 4203
    }
}
