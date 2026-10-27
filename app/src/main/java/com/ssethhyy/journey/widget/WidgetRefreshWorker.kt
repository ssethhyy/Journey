/*
 * Feature Module: Widget
 * File: WidgetRefreshWorker.kt
 * Description: Periodic background refresh of the widget's live departures.
 */

package com.ssethhyy.journey.widget

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class WidgetRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        WidgetUpdater.refresh(applicationContext)
        WidgetRedrawWorker.start(applicationContext)
        return Result.success()
    }

    companion object {
        private const val PERIODIC = "widget_refresh_periodic"
        private const val ONE_TIME = "widget_refresh_now"

        private val network = Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()

        /** Every 15 minutes (the WorkManager minimum) while a widget is on the home screen. */
        fun schedule(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                PeriodicWorkRequestBuilder<WidgetRefreshWorker>(15, TimeUnit.MINUTES).setConstraints(network).build(),
            )
        }

        fun refreshNow(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                ONE_TIME,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<WidgetRefreshWorker>()
                    .setConstraints(network)
                    .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                    .build(),
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
        }
    }
}

/**
 * Redraws the widget once a minute (no network) so countdowns stay accurate between the
 * 15-minute live refreshes. Stops itself once the data is older than the refresh interval.
 */
class WidgetRedrawWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        WidgetUpdater.redraw(applicationContext)
        val age = System.currentTimeMillis() / 1000 - WidgetStore.read(applicationContext).updatedAtEpochSec
        if (age < 16 * 60) start(applicationContext)
        return Result.success()
    }

    companion object {
        private const val NAME = "widget_redraw"

        fun start(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                NAME,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<WidgetRedrawWorker>().setInitialDelay(60, TimeUnit.SECONDS).build(),
            )
        }
    }
}
