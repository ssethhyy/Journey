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
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
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
            }
            val status = when (target) {
                is TrackingTarget.Service -> TrackingLogic.serviceStatus(target, departure, startedAt, now)
                is TrackingTarget.Trip -> journey?.let { TrackingLogic.journeyStatus(it, target.toName, now) }
                    ?: TrackingStatus("Journey unavailable", "Plan the trip again to keep tracking", "—", 1f, false, true, null, now)
            }
            if (status != lastStatus) {
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
            .setContentText(status.detail + if (status.isLive) " · Live" else "")
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
                        .setProgressTrackerIcon(Icon.createWithResource(this, R.drawable.ic_stat_tracking))
                        .setProgressSegments(listOf(Notification.ProgressStyle.Segment(1000).setColor(accent))),
                )
            }
        } else {
            builder.setProgress(1000, (status.progress * 1000).toInt(), false)
        }
        return builder.build()
    }

    companion object {
        const val ACTION_START = "com.ssethhyy.journey.tracking.START"
        const val ACTION_STOP = "com.ssethhyy.journey.tracking.STOP"
        private const val CHANNEL_ID = "live_tracking"
        private const val NOTIFICATION_ID = 4201
        private const val CROWDING_CHANNEL_ID = "crowding_alerts"
        private const val CROWDING_NOTIFICATION_ID = 4202
    }
}
