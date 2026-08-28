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
    // User-configurable first-phase window (ms), set by requestResume() on the main thread before
    // sendBroadcast; consumed by commandReceiver (same process). Null means "use default scaling".
    private var pendingFirstPhaseMs: Long? = null
    // Set by requestResume() on the main thread before sendBroadcast; consumed by commandReceiver (same process).
    private var pendingOnFinish: (() -> Unit)? = null

    private val activeSessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { }

    private val commandReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val pkg = intent.getStringExtra(EXTRA_PACKAGE_NAME) ?: return
            val timeoutMs = intent.getIntExtra(EXTRA_TIMEOUT_MS, DEFAULT_TIMEOUT_MS.toInt())
            AppLogBuffer.info(TAG, "commandReceiver: pkg=$pkg timeout=${timeoutMs}ms")
            pendingResume = pkg to timeoutMs
            pendingResumeStamp = System.currentTimeMillis()
            startResume(
                pkg,
                timeoutMs,
                pendingFirstPhaseMs.also { pendingFirstPhaseMs = null },
                pendingOnFinish.also { pendingOnFinish = null }
            )
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

    private fun startResume(
        packageName: String,
        timeoutMs: Int,
        firstPhaseMs: Long? = null,
        onFlowFinished: (() -> Unit)? = null
    ) {
        val effectiveTimeout = timeoutMs.toLong().coerceIn(MIN_TIMEOUT_MS, MAX_TIMEOUT_MS)
        // First phase gives up (and re-launches) once a session has been present but unplayable for
        // this long. Uses the user-configured value when provided; otherwise scales from the full
        // timeout so a slow-cold-start app (e.g. Tidal) still gets enough time to become playable
        // before we re-launch. The full timeout stays as the ceiling for the second phase.
        val stuckGiveUpMs = firstPhaseMs ?: minOf(FIRST_POLL_GIVEUP_MS, maxOf(STUCK_SESSION_MIN_GIVEUP_MS, effectiveTimeout / 2))
        var finished = false
        fun finishOnce() {
            if (finished) return
            finished = true
            runCatching { onFlowFinished?.invoke() }
                .onFailure { e -> AppLogBuffer.warn(TAG, "onFlowFinished failed: ${e.message}") }
        }
        scope.launch {
            pendingResume = null
            if (firstPhase(packageName, stuckGiveUpMs)) {
                AppLogBuffer.info(TAG, "Resumed playback for $packageName")
                finishOnce()
                return@launch
            }
            AppLogBuffer.info(TAG, "No playable session in ${stuckGiveUpMs}ms, re-launching $packageName")
            relaunchApp(packageName)
            if (pollAndPlay(packageName, effectiveTimeout)) {
                AppLogBuffer.info(TAG, "Resumed playback for $packageName after re-launch")
            } else {
                LegacyBroadcastFallback.sendPlay(this@MediaControlListenerService, packageName)
                AppLogBuffer.warn(TAG, "All resume attempts failed for $packageName, fallback broadcast sent")
            }
            finishOnce()
        }
    }

    /**
     * First phase: poll until the target's media session becomes genuinely playable. While the
     * session is absent we simply wait (it has not booted yet). Once it exists but keeps rejecting
     * play() (PAUSED with no track queued during cold start) for [stuckGiveUpMs], we stop and let
     * the caller re-launch instead of burning the whole window on no-op attempts. Returns true only
     * if playback was confirmed.
     */
    private suspend fun firstPhase(packageName: String, stuckGiveUpMs: Long): Boolean {
        val self = this
        if (instance !== self) return false
        var played = false
        var presentSinceMs: Long? = null
        withTimeoutOrNull(stuckGiveUpMs) {
            while (true) {
                delay(POLL_INTERVAL_MS)
                if (instance !== self) break
                when (firstPhaseTick(packageName, presentSinceMs, stuckGiveUpMs)) {
                    FirstPhaseOutcome.PLAYING -> {
                        played = true
                        break
                    }
                    FirstPhaseOutcome.STUCK_GIVEUP -> break
                    FirstPhaseOutcome.CONTINUE -> presentSinceMs = currentPresentSince(packageName, presentSinceMs)
                }
            }
        }
        // `played` is only set after play() was confirmed to reach a genuine playing state, so it
        // is itself the success signal here.
        return played
    }

    private enum class FirstPhaseOutcome { PLAYING, STUCK_GIVEUP, CONTINUE }

    // Advances the "session present since" marker, or returns null when no session is visible yet.
    private suspend fun currentPresentSince(packageName: String, previous: Long?): Long? {
        return if (findController(packageName) == null) null else previous ?: System.currentTimeMillis()
    }

    /**
     * One polling tick for [firstPhase]. Sends at most one play() command and does a single short
     * probe of the resulting state (no long verify loop that would starve the wall-clock give-up
     * timer). Returns PLAYING when playback is confirmed, STUCK_GIVEUP when the session has been
     * present but unplayable for [stuckGiveUpMs] or more, otherwise CONTINUE.
     */
    private suspend fun firstPhaseTick(
        packageName: String,
        presentSinceMs: Long?,
        stuckGiveUpMs: Long
    ): FirstPhaseOutcome {
        if (findController(packageName) == null) return FirstPhaseOutcome.CONTINUE
        if (tryPlay(packageName) && probeGenuinelyPlaying(packageName)) return FirstPhaseOutcome.PLAYING
        val since = presentSinceMs ?: return FirstPhaseOutcome.CONTINUE
        return if (System.currentTimeMillis() - since >= stuckGiveUpMs) {
            FirstPhaseOutcome.STUCK_GIVEUP
        } else {
            FirstPhaseOutcome.CONTINUE
        }
    }

    // Single non-blocking probe: is the session genuinely playing right now? Unlike
    // [verifyPlaying] this does not wait, so it cannot consume the phase budget.
    private suspend fun probeGenuinelyPlaying(packageName: String): Boolean {
        return isGenuinelyPlaying(findController(packageName)?.playbackState)
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
                val controller = findController(packageName) ?: continue
                if (isGenuinelyPlaying(controller.playbackState)) {
                    result = true
                    break
                }
            }
        }
        return result
    }

    // True only for states where real media is actively moving: PLAYING, or a seek
    // (FAST_FORWARD/REWINDING) on a track with a known duration. Transient states such as
    // BUFFERING are intentionally rejected so a cold-start session that briefly flaps
    // through them is not mistaken for successful playback.
    private fun isGenuinelyPlaying(state: PlaybackState?): Boolean {
        if (state == null) return false
        return when (state.state) {
            PlaybackState.STATE_PLAYING -> true
            PlaybackState.STATE_FAST_FORWARDING,
            PlaybackState.STATE_REWINDING -> state.position > 0L
            else -> false
        }
    }

    private suspend fun findController(packageName: String): MediaController? {
        val controllers = runCatching { manager.getActiveSessions(componentSelf) }.getOrNull() ?: return null
        return controllers.firstOrNull { it.packageName == packageName }
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
        private const val FIRST_POLL_GIVEUP_MS = 20000L
        private const val STUCK_SESSION_MIN_GIVEUP_MS = 10000L
        private const val TAG = "MediaResumer"

        @Suppress("SwallowedException")
        var instance: MediaControlListenerService? = null
            private set

        fun requestResume(
            context: Context,
            packageName: String,
            timeoutMs: Long = DEFAULT_TIMEOUT_MS,
            firstPhaseMs: Long? = null,
            onFlowFinished: (() -> Unit)? = null
        ): Boolean {
            val firstPhaseLabel = firstPhaseMs?.toString() ?: "default"
            AppLogBuffer.info(
                TAG,
                "requestResume: pkg=$packageName timeout=${timeoutMs}ms firstPhase=$firstPhaseLabel instance=${instance != null}"
            )
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
            // Same-process handoff: the receiver runs in this same service process and reads
            // pendingFirstPhaseMs / pendingOnFinish on the main thread; no lock needed.
            instance?.pendingFirstPhaseMs = firstPhaseMs
            instance?.pendingOnFinish = onFlowFinished
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