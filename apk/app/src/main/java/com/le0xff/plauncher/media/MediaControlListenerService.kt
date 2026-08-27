package com.le0xff.plauncher.media

/**
 * pLauncher Companion App — [NotificationListenerService] that waits for a target app's
 * active [MediaSession] after launch and resumes playback via its transport controls.
 *
 * @author Le0xFF
 */

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.view.KeyEvent
import com.le0xff.plauncher.data.AppLogBuffer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

class MediaControlListenerService : NotificationListenerService() {

    private lateinit var manager: MediaSessionManager
    private val componentSelf: ComponentName by lazy {
        ComponentName(applicationContext.packageName, MediaControlListenerService::class.java.name)
    }
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var pendingResume: Pair<String, Int>? = null
    private var pendingResumeStamp: Long = 0L

    private val activeSessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { }

    private val commandReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val pkg = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: return
            val timeoutMs = intent.getIntExtra(EXTRA_TIMEOUT_MS, DEFAULT_TIMEOUT_MS.toInt())
            AppLogBuffer.info(TAG, "commandReceiver: pkg=$pkg timeout=${timeoutMs}ms")
            pendingResume = pkg to timeoutMs
            pendingResumeStamp = System.currentTimeMillis()
            startResume(pkg, timeoutMs)
        }
    }

    override fun onCreate() {
        super.onCreate()
        manager = getSystemService(MEDIA_SESSION_SERVICE) as MediaSessionManager
        registerReceiver(commandReceiver, IntentFilter(ACTION_REQUEST_RESUME))
    }

    override fun onListenerConnected() {
        try {
            manager.addOnActiveSessionsChangedListener(activeSessionsListener, componentSelf)
            AppLogBuffer.info(TAG, "onListenerConnected: session listener registered")
        } catch (e: SecurityException) {
            AppLogBuffer.warn(TAG, "Permission revoked while connected: ${e.message}")
        }
        instance = this
        resumePendingIfFresh()
    }

    override fun onListenerDisconnected() {
        runCatching {
            manager.removeOnActiveSessionsChangedListener(activeSessionsListener)
        }.onFailure { e ->
            AppLogBuffer.warn(TAG, "Failed to remove session listener: ${e.message}")
        }
    }

    override fun onUnbind(intent: Intent?): Boolean {
        if (instance === this) {
            instance = null
        }
        runCatching {
            manager.removeOnActiveSessionsChangedListener(activeSessionsListener)
        }
        runCatching { unregisterReceiver(commandReceiver) }
        scope.cancel()
        return super.onUnbind(intent)
    }

    private fun resumePendingIfFresh() {
        val pending = pendingResume ?: return
        if (System.currentTimeMillis() - pendingResumeStamp > STALE_PENDING_MS) {
            pendingResume = null
            return
        }
        startResume(pending.first, pending.second)
    }

    private fun startResume(packageName: String, timeoutMs: Int) {
        val effectiveTimeout = timeoutMs.toLong().coerceIn(MIN_TIMEOUT_MS, MAX_TIMEOUT_MS)
        scope.launch {
            pendingResume = null
            if (pollAndPlay(packageName, effectiveTimeout)) {
                AppLogBuffer.info(TAG, "Resumed playback for $packageName")
                return@launch
            }
            AppLogBuffer.info(TAG, "No session found in ${effectiveTimeout}ms, re-launching $packageName")
            relaunchApp(packageName)
            if (pollAndPlay(packageName, effectiveTimeout)) {
                AppLogBuffer.info(TAG, "Resumed playback for $packageName after re-launch")
            } else {
                LegacyBroadcastFallback.sendPlay(this@MediaControlListenerService, packageName)
                AppLogBuffer.warn(TAG, "All resume attempts failed for $packageName, fallback broadcast sent")
            }
        }
    }

    private suspend fun pollAndPlay(packageName: String, timeoutMs: Long): Boolean {
        val self = this
        if (instance !== self) return false
        var played = false
        withTimeoutOrNull(timeoutMs) {
            while (true) {
                delay(POLL_INTERVAL_MS)
                if (instance !== self) break
                if (tryPlay(packageName)) {
                    played = true
                    if (!verifyPlaying(packageName, VERIFY_PLAYING_MS)) {
                        AppLogBuffer.warn(TAG, "play() did not take effect for $packageName, retrying")
                        played = false
                    } else {
                        break
                    }
                }
            }
        }
        return played
    }

    private suspend fun verifyPlaying(packageName: String, timeoutMs: Long): Boolean {
        var result = false
        withTimeoutOrNull(timeoutMs) {
            while (true) {
                delay(POLL_INTERVAL_MS)
                val state = findPlaybackState(packageName) ?: continue
                if (state == PlaybackState.STATE_PLAYING) {
                    result = true
                    break
                }
                if (state != PlaybackState.STATE_PAUSED &&
                    state != PlaybackState.STATE_NONE &&
                    state != PlaybackState.STATE_STOPPED
                ) {
                    result = true
                    break
                }
            }
        }
        return result
    }

    private suspend fun findPlaybackState(packageName: String): Int? {
        val controllers = runCatching { manager.getActiveSessions(componentSelf) }.getOrNull() ?: return null
        val match = controllers.firstOrNull { it.packageName == packageName } ?: return null
        return match.playbackState?.state
    }

    private fun relaunchApp(packageName: String) {
        try {
            val intent = packageManager.getLaunchIntentForPackage(packageName) ?: return
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            startActivity(intent)
            AppLogBuffer.info(TAG, "Re-launched $packageName via launch intent")
        } catch (e: Exception) {
            AppLogBuffer.warn(TAG, "Failed to re-launch $packageName: ${e.message}")
        }
    }

    private suspend fun tryPlay(packageName: String): Boolean {
        val controllers = runCatching { manager.getActiveSessions(componentSelf) }.getOrElse { e ->
            AppLogBuffer.warn(TAG, "getActiveSessions failed: ${e.message}")
            return false
        }
        val sessionPkgs = controllers.joinToString(", ") { it.packageName ?: "?" }
        AppLogBuffer.debug(TAG, "Poll: $packageName | visible sessions: [$sessionPkgs]")
        val match = findMatch(controllers, packageName) ?: return false
        val controller = runCatching {
            MediaController(applicationContext, match.sessionToken)
        }.getOrNull() ?: return false
        return try {
            val playbackState = match.playbackState
            val state = playbackState?.state
            val hasPlayAction = playbackState?.actions?.and(PlaybackState.ACTION_PLAY) != 0L
            if (hasPlayAction || state == PlaybackState.STATE_NONE || state == PlaybackState.STATE_STOPPED) {
                controller.transportControls.play()
            } else {
                controller.dispatchMediaButtonEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY))
                controller.dispatchMediaButtonEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY))
            }
            true
        } catch (e: Exception) {
            AppLogBuffer.warn(TAG, "Failed to send play to $packageName: ${e.message}")
            false
        }
    }

    private fun findMatch(controllers: List<MediaController>, packageName: String): MediaController? {
        val active = controllers.firstOrNull {
            it.packageName == packageName &&
                it.playbackState?.state != PlaybackState.STATE_NONE &&
                it.playbackState?.state != PlaybackState.STATE_STOPPED
        }
        return active ?: controllers.firstOrNull { it.packageName == packageName }
    }

    companion object {
        const val ACTION_REQUEST_RESUME = "com.le0xff.plauncher.REQUEST_MEDIA_RESUME"
        const val EXTRA_PACKAGE_NAME = "package_name"
        const val EXTRA_TIMEOUT_MS = "timeout_ms"

        const val DEFAULT_TIMEOUT_MS = 30000L
        private const val MIN_TIMEOUT_MS = 1000L
        private const val MAX_TIMEOUT_MS = 60000L
        private const val POLL_INTERVAL_MS = 500L
        private const val STALE_PENDING_MS = 8000L
        private const val VERIFY_PLAYING_MS = 10000L
        private const val TAG = "MediaResumer"

        @Suppress("SwallowedException")
        var instance: MediaControlListenerService? = null
            private set

        fun requestResume(context: Context, packageName: String, timeoutMs: Long = DEFAULT_TIMEOUT_MS): Boolean {
            AppLogBuffer.info(TAG, "requestResume: pkg=$packageName timeout=${timeoutMs}ms instance=${instance != null}")
            if (instance == null) {
                AppLogBuffer.warn(TAG, "Notification listener not bound, opening notification access settings")
                runCatching {
                    context.startActivity(
                        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                }
                return false
            }
            val clampedTimeout = timeoutMs.coerceIn(MIN_TIMEOUT_MS, MAX_TIMEOUT_MS).toInt()
            context.sendBroadcast(
                Intent(ACTION_REQUEST_RESUME)
                    .setPackage(context.packageName)
                    .putExtra(EXTRA_PACKAGE_NAME, packageName)
                    .putExtra(EXTRA_TIMEOUT_MS, clampedTimeout)
            )
            return true
        }
    }
}