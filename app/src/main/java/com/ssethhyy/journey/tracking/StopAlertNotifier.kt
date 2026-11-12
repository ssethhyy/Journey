/*
 * Feature Module: Tracking
 * File: StopAlertNotifier.kt
 * Description: Shows the "get off here" alert: a silent, high-priority notification with a full-screen intent
 * (the alert screen opens over the lock screen, or as a heads-up while you're using the phone), and a short
 * vibration unless the phone is on silent or vibration is turned off in Settings. It never plays a sound.
 */

package com.ssethhyy.journey.tracking

import android.app.ActivityManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import com.ssethhyy.journey.R

object StopAlertNotifier {
    private const val CHANNEL_ID = "stop_alerts"
    private const val NOTIFICATION_ID = 4203

    /** Four strong pulses over about three seconds: noticeable in a pocket, but not an endless alarm. */
    private val PATTERN = longArrayOf(0, 450, 250, 450, 250, 450, 250, 650)
    private val AMPLITUDES = intArrayOf(0, 255, 0, 255, 0, 255, 0, 255)

    fun ringMode(context: Context): RingMode {
        val audio = context.getSystemService(AudioManager::class.java) ?: return RingMode.NORMAL
        return when (audio.ringerMode) {
            AudioManager.RINGER_MODE_SILENT -> RingMode.SILENT
            AudioManager.RINGER_MODE_VIBRATE -> RingMode.VIBRATE
            else -> RingMode.NORMAL
        }
    }

    /** Android 14+ asks the user to allow full-screen alerts; before that it's always allowed. */
    fun canShowFullScreen(context: Context): Boolean =
        Build.VERSION.SDK_INT < 34 || context.getSystemService(NotificationManager::class.java).canUseFullScreenIntent()

    /** Opens the system page where full-screen alerts can be allowed (Android 14+). */
    fun fullScreenSettingsIntent(context: Context): Intent =
        if (Build.VERSION.SDK_INT >= 34) {
            Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, android.net.Uri.parse("package:${context.packageName}"))
        } else {
            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) != null) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Stop alerts", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "A full-screen alert when it's time to get off"
                // Silent: Journey vibrates itself, following your ring mode, and never plays a sound.
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            },
        )
    }

    fun show(context: Context, alert: StopAlert, vibrate: Boolean) {
        ensureChannel(context)
        val screen = StopAlertActivity.intent(context, alert)
        val fullScreen = PendingIntent.getActivity(context, 7, screen, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = Notification.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_tracking)
            .setContentTitle("Get off at ${alert.stopName}")
            .setContentText(listOf(alert.instruction, alert.detail).filter { it.isNotBlank() }.joinToString(" · "))
            .setCategory(Notification.CATEGORY_ALARM)
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(fullScreen)
            .setFullScreenIntent(fullScreen, true)
            .build()
        runCatching { context.getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification) }
        // While Journey is open the full-screen intent would only show as a heads-up, so open the alert directly.
        if (isAppInForeground()) runCatching { context.startActivity(screen) }
        if (StopAlertVibration.shouldVibrate(vibrate, ringMode(context))) vibrate(context)
    }

    fun cancel(context: Context) {
        runCatching { context.getSystemService(NotificationManager::class.java).cancel(NOTIFICATION_ID) }
    }

    private fun isAppInForeground(): Boolean {
        val info = ActivityManager.RunningAppProcessInfo()
        ActivityManager.getMyMemoryState(info)
        return info.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
    }

    private fun vibrate(context: Context) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Vibrator::class.java)
        }
        if (!vibrator.hasVibrator()) return
        val effect = if (vibrator.hasAmplitudeControl()) VibrationEffect.createWaveform(PATTERN, AMPLITUDES, -1) else VibrationEffect.createWaveform(PATTERN, -1)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // Alarm usage so Do Not Disturb's "alarms" rule applies, as for any alarm. Still no sound.
                vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
            } else {
                vibrator.vibrate(effect)
            }
        }
    }
}
