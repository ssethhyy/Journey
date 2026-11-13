/*
 * Feature Module: Updates
 * File: UpdateManager.kt
 * Description: Checks GitHub for a newer Journey when the app is opened (at most every few hours, so it costs no
 * battery in the background), notifies once per new version, and installs an update entirely inside the app: the
 * APK is downloaded straight into Journey's storage and handed to Android's package installer, which shows its own
 * "Update Journey?" prompt. No browser is involved.
 */

package com.ssethhyy.journey.data.update

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import com.ssethhyy.journey.MainActivity
import com.ssethhyy.journey.R
import com.ssethhyy.journey.appContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.concurrent.TimeUnit

sealed interface InstallState {
    data object Idle : InstallState

    /** [progress] is 0..1, or null while the size isn't known. */
    data class Downloading(val progress: Float?) : InstallState

    /** Waiting for Android's "Update Journey?" prompt (or for it to finish). */
    data object Installing : InstallState

    /** Android needs the user to allow Journey to install apps first. */
    data object NeedsPermission : InstallState

    data class Failed(val message: String) : InstallState
}

class UpdateManager(
    context: Context,
    private val api: GitHubReleasesApi,
    private val installedVersion: String,
) {
    private val app = context.applicationContext
    private val prefs = app.getSharedPreferences("journey_updates", Context.MODE_PRIVATE)
    private val download = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS).readTimeout(60, TimeUnit.SECONDS).build()

    private val _available = MutableStateFlow<AvailableUpdate?>(null)
    /** The newest update found, if any. */
    val available: StateFlow<AvailableUpdate?> = _available.asStateFlow()

    private val _prompt = MutableStateFlow<AvailableUpdate?>(null)
    /** The update sheet is open for this release. */
    val prompt: StateFlow<AvailableUpdate?> = _prompt.asStateFlow()

    private val _install = MutableStateFlow<InstallState>(InstallState.Idle)
    val install: StateFlow<InstallState> = _install.asStateFlow()

    /**
     * Called when Journey opens. Skips the network entirely if it checked recently, so opening the app many times a
     * day stays cheap. Notifies once for each new version.
     */
    suspend fun checkOnOpen(includePrereleases: Boolean) {
        val last = prefs.getLong(KEY_LAST_CHECK, 0L)
        if (System.currentTimeMillis() - last < MIN_INTERVAL_MS) return
        val update = runCatching { check(includePrereleases) }.getOrNull() ?: return
        if (prefs.getString(KEY_NOTIFIED, null) != update.version) {
            prefs.edit().putString(KEY_NOTIFIED, update.version).apply()
            notify(update)
        }
    }

    /** Looks now, whatever the time since the last check. Returns the update, or null if Journey is up to date. */
    suspend fun check(includePrereleases: Boolean): AvailableUpdate? {
        val update = UpdateLogic.newest(api.releases(), installedVersion, includePrereleases)
        prefs.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
        _available.value = update
        return update
    }

    fun showPrompt(update: AvailableUpdate? = _available.value) {
        if (update != null) _prompt.value = update
    }

    fun dismissPrompt() {
        _prompt.value = null
        if (_install.value !is InstallState.Downloading && _install.value !is InstallState.Installing) _install.value = InstallState.Idle
    }

    fun canInstallApps(): Boolean = Build.VERSION.SDK_INT < Build.VERSION_CODES.O || app.packageManager.canRequestPackageInstalls()

    /** Android's page where the user lets Journey install updates. */
    fun installPermissionIntent(): Intent =
        Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${app.packageName}")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Downloads the APK into Journey's own storage, then asks Android to install it. */
    suspend fun downloadAndInstall(update: AvailableUpdate) {
        if (!canInstallApps()) {
            _install.value = InstallState.NeedsPermission
            return
        }
        _install.value = InstallState.Downloading(0f)
        val apk = runCatching { fetch(update) }.getOrElse {
            _install.value = InstallState.Failed(it.message ?: "The download failed")
            return
        }
        _install.value = InstallState.Installing
        runCatching { installApk(apk) }.onFailure { _install.value = InstallState.Failed(it.message ?: "Couldn't start the install") }
    }

    private suspend fun fetch(update: AvailableUpdate): File = withContext(Dispatchers.IO) {
        val dir = File(app.cacheDir, "updates").apply { mkdirs() }
        // Only ever keep the update being installed.
        dir.listFiles()?.forEach { it.delete() }
        val target = File(dir, "journey-${update.version}.apk")
        val request = Request.Builder().url(update.apkUrl).header("User-Agent", "Journey-Android").build()
        download.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw UpdateException("Download failed (${response.code})")
            val body = response.body ?: throw UpdateException("The download was empty")
            val total = body.contentLength().takeIf { it > 0 } ?: update.apkSize.takeIf { it > 0 }
            body.byteStream().use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var read = 0L
                    var lastReported = -1
                    while (true) {
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        read += n
                        val percent = total?.let { (read * 100 / it).toInt() } ?: -1
                        if (percent != lastReported) {
                            lastReported = percent
                            _install.value = InstallState.Downloading(total?.let { (read.toFloat() / it).coerceIn(0f, 1f) })
                        }
                    }
                }
            }
        }
        target
    }

    /** Streams the APK into a PackageInstaller session; Android then shows its own confirmation. */
    private suspend fun installApk(apk: File) = withContext(Dispatchers.IO) {
        val installer = app.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(app.packageName)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            apk.inputStream().use { input ->
                session.openWrite("journey.apk", 0, apk.length()).use { output ->
                    input.copyTo(output)
                    session.fsync(output)
                }
            }
            val callback = PendingIntent.getBroadcast(
                app,
                sessionId,
                Intent(app, InstallResultReceiver::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE,
            )
            session.commit(callback.intentSender)
        }
    }

    internal fun onInstallResult(status: Int, message: String?) {
        _install.value = when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> InstallState.Installing
            PackageInstaller.STATUS_SUCCESS -> InstallState.Idle
            PackageInstaller.STATUS_FAILURE_ABORTED -> InstallState.Idle
            else -> InstallState.Failed(message?.takeIf { it.isNotBlank() } ?: "Android couldn't install the update")
        }
    }

    private fun notify(update: AvailableUpdate) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(app, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        val nm = app.getSystemService(NotificationManager::class.java)
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "App updates", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "When a new version of Journey is available"
                },
            )
        }
        val open = PendingIntent.getActivity(
            app,
            11,
            Intent(app, MainActivity::class.java).putExtra(EXTRA_SHOW_UPDATE, true).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = android.app.Notification.Builder(app, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_tracking)
            .setContentTitle("Journey ${update.version} is available")
            .setContentText(if (update.prerelease) "A pre-release is ready to install. Tap to see what's new." else "Tap to see what's new and install it.")
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        runCatching { nm.notify(NOTIFICATION_ID, notification) }
    }

    companion object {
        const val EXTRA_SHOW_UPDATE = "show_update"
        private const val CHANNEL_ID = "app_updates"
        private const val NOTIFICATION_ID = 4204
        private const val KEY_LAST_CHECK = "last_check"
        private const val KEY_NOTIFIED = "notified_version"

        /** Opening the app checks GitHub at most this often. */
        const val MIN_INTERVAL_MS = 4 * 60 * 60 * 1000L
    }
}

/** Receives Android's answer from the package installer and shows its confirmation prompt when asked. */
class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            val confirm = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(Intent.EXTRA_INTENT)
            }
            confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let { runCatching { context.startActivity(it) } }
        }
        context.appContainer.updateManager.onInstallResult(status, intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE))
    }
}
