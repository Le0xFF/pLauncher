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
import android.service.notification.NotificationListenerService
import android.view.KeyEvent
import com.le0xff.plauncher.data.AppLogBuffer
import java.util.concurrent.atomic.AtomicLong
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
    private var pendingOnFinish: (() -> Unit)? = null
    // Like pendingOnFinish but handed to the flow so onUnbind() can release the screen if the flow
    // is aborted mid-run (service unbound before the flow reached its terminal callback).
    private var pendingOnAbort: (() -> Unit)? = null

    // Reused MediaController instances keyed by session token. Avoids re-creating a controller
    // (and its binder) on every polling tick. Invalidated in [onUnbind] and when a fetched session
    // list no longer contains the token (evicted by [fetchActiveSessions]).
    private val controllerByToken = HashMap<String, MediaController>()
    // Set while any resume flow is running so the session-changed listener can wake the poll loop.
    @Volatile
    private var flowActive = false
    // One-shot, main-thread-safe signal consumed by the poll loops: the session listener sets it
    // (CAS) and a waiting tick takes it (getAndSet). A fresh token is minted per take so a wake-up
    // delivered while a loop is in its IPC call is not lost, and no coroutine is ever blocked.
    private val sessionSignal = AtomicLong(0L)

    private val activeSessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { sessions ->
        handleSessionsChanged(sessions)
    }

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
                pendingOnFinish.also { pendingOnFinish = null },
                pendingOnAbort.also { pendingOnAbort = null }
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
        // Intentionally no automatic rebind here: reacting to a disconnect could loop. The listener is
        // rebound on the next discrete need (watch connection, boot, or feature toggle) instead.
        AppLogBuffer.info(TAG, "listener disconnected — will be rebound on next need")
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
        controllerByToken.clear()
        flowActive = false
        // The scope is about to be cancelled: any in-flight flow will never reach its terminal
        // onFlowFinished, so release the screen wake lock here on its behalf (no-op when we did not
        // wake the screen). After this, callers must treat the process as having no flow in flight.
        runCatching { pendingOnAbort?.invoke() }
            .onFailure { e -> AppLogBuffer.warn(TAG, "onAbortedFlow failed: ${e.message}") }
        pendingOnAbort = null
        flowInProgress = false
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

    // Runs on the main thread whenever the active session set changes. While a flow is running it
    // arms the wake signal so the poll loop reacts to a newly appeared (or removed) session without
    // waiting for the next timer tick. Cached controllers are evicted lazily by [fetchActiveSessions].
    // The signal re-arms only when nothing was pending, so a burst of change events (e.g. the target
    // app flapping its session during cold start) cannot keep the poll loops in a no-delay hot loop.
    private fun handleSessionsChanged(sessions: List<MediaController>?) {
        if (!flowActive || sessions.isNullOrEmpty()) return
        AppLogBuffer.debug(TAG, "Active sessions changed, waking poll loop")
        sessionSignal.compareAndSet(0L, System.nanoTime())
    }

    /**
     * Blocks until [timeoutMs] elapses or the session listener fires. Returns true when woken by
     * an event. The signal is consumed (getAndSet 0) before yielding, so a wake-up arriving during
     * the IPC call is not lost; the loop re-waits with whatever time remains in the tick budget.
     */
    private suspend fun awaitSessionOrTimeout(timeoutMs: Long): Boolean {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (true) {
            if (sessionSignal.getAndSet(0L) != 0L) return true
            val remaining = deadline - System.currentTimeMillis()
            if (remaining <= 0L) return false
            delay(remaining.coerceAtMost(POLL_INTERVAL_MS))
        }
    }

    private fun startResume(
        packageName: String,
        timeoutMs: Int,
        firstPhaseMs: Long? = null,
        onFlowFinished: (() -> Unit)? = null,
        onFlowAborted: (() -> Unit)? = null
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
        flowInProgress = true
        if (onFlowAborted != null) {
            pendingOnAbort = onFlowAborted
        }
        scope.launch {
            pendingResume = null
            flowActive = true
            val succeeded = firstPhase(packageName, stuckGiveUpMs)
            if (succeeded) {
                AppLogBuffer.info(TAG, "Resumed playback for $packageName")
                finishOnce()
                flowInProgress = false
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
            flowActive = false
            flowInProgress = false
        }
    }
/**
     * First phase: wait until the target's media session becomes genuinely playable. While the
     * session is absent we simply wait (it has not booted yet) and also react immediately to a
     * session-changed event instead of only on the next timer tick. Once it exists but keeps
     * rejecting play() (PAUSED with no track queued during cold start) for [stuckGiveUpMs], we stop
     * and let the caller re-launch instead of burning the whole window on no-op attempts. Returns
     * true only if playback was confirmed.
     */
    private suspend fun firstPhase(packageName: String, stuckGiveUpMs: Long): Boolean {
        val self = this
        if (instance !== self) return false
        var played = false
        var presentSinceMs: Long? = null
        withTimeoutOrNull(stuckGiveUpMs) {
            while (true) {
                awaitSessionOrTimeout(POLL_INTERVAL_MS)
                if (instance !== self) break
                val sessions = fetchActiveSessions()
                when (firstPhaseTick(sessions, packageName, presentSinceMs, stuckGiveUpMs)) {
                    FirstPhaseOutcome.PLAYING -> {
                        played = true
                        break
                    }

                    FirstPhaseOutcome.STUCK_GIVEUP -> break
                    FirstPhaseOutcome.CONTINUE -> presentSinceMs = currentPresentSince(sessions, packageName, presentSinceMs)
                }
            }
        }
        // `played` is only set after play() was confirmed to reach a genuine playing state, so it
        // is itself the success signal here.
        return played
    }

    private enum class FirstPhaseOutcome { PLAYING, STUCK_GIVEUP, CONTINUE }

    // Advances the "session present since" marker, or returns null when no session is visible yet.
    private fun currentPresentSince(
        sessions: List<MediaController>,
        packageName: String,
        previous: Long?
    ): Long? {
        return if (findInList(sessions, packageName) == null) null else previous ?: System.currentTimeMillis()
    }

    /**
     * One polling tick for [firstPhase]. Sends at most one play() command and does a single short
     * probe of the resulting state (no long verify loop that would starve the wall-clock give-up
     * timer). Returns PLAYING when playback is confirmed, STUCK_GIVEUP when the session has been
     * present but unplayable for [stuckGiveUpMs] or more, otherwise CONTINUE.
     */
    private suspend fun firstPhaseTick(
        sessions: List<MediaController>,
        packageName: String,
        presentSinceMs: Long?,
        stuckGiveUpMs: Long
    ): FirstPhaseOutcome {
        if (findInList(sessions, packageName) == null) return FirstPhaseOutcome.CONTINUE
        val controller = tryPlay(sessions, packageName) ?: return FirstPhaseOutcome.CONTINUE
        if (isGenuinelyPlaying(controller.playbackState)) return FirstPhaseOutcome.PLAYING
        val since = presentSinceMs ?: return FirstPhaseOutcome.CONTINUE
        return if (System.currentTimeMillis() - since >= stuckGiveUpMs) {
            FirstPhaseOutcome.STUCK_GIVEUP
        } else {
            FirstPhaseOutcome.CONTINUE
        }
    }

    private suspend fun pollAndPlay(packageName: String, timeoutMs: Long): Boolean {
        val self = this
        if (instance !== self) return false
        var played = false
        withTimeoutOrNull(timeoutMs) {
            while (true) {
                awaitSessionOrTimeout(POLL_INTERVAL_MS)
                if (instance !== self) break
                val sessions = fetchActiveSessions()
                val controller = tryPlay(sessions, packageName) ?: continue
                played = true
                if (!verifyPlaying(controller, VERIFY_PLAYING_MS)) {
                    AppLogBuffer.warn(TAG, "play() did not take effect for $packageName, retrying")
                    played = false
                } else {
                    break
                }
            }
        }
        return played
    }

    private suspend fun verifyPlaying(controller: MediaController, timeoutMs: Long): Boolean {
        var result = false
        withTimeoutOrNull(timeoutMs) {
            while (true) {
                awaitSessionOrTimeout(POLL_INTERVAL_MS)
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

    // Single IPC call returning every visible session. The token set is also used to evict cached
    // controllers whose session has disappeared, so each tick performs exactly one binder round-trip.
    private fun fetchActiveSessions(): List<MediaController> {
        val sessions = runCatching { manager.getActiveSessions(componentSelf) }.getOrNull() ?: return emptyList()
        val visibleTokens = sessions.mapTo(mutableSetOf()) { it.sessionToken.toString() }
        controllerByToken.keys.retainAll(visibleTokens)
        return sessions
    }

    // Picks the target's best-matching session (playable state preferred), same matching rules as
    // before but operating on an already-fetched list instead of another IPC call.
    private fun findInList(sessions: List<MediaController>, packageName: String): MediaController? {
        val active = sessions.firstOrNull {
            it.packageName == packageName &&
                it.playbackState?.state != PlaybackState.STATE_NONE &&
                it.playbackState?.state != PlaybackState.STATE_STOPPED
        }
        return active ?: sessions.firstOrNull { it.packageName == packageName }
    }

    // Returns a [MediaController] for the given session, reusing a cached instance while its
    // token is still valid. A new controller is created and cached only when the token changes.
    private fun controllerFor(session: MediaController): MediaController {
        val token = session.sessionToken.toString()
        controllerByToken[token]?.let { return it }
        val created = runCatching {
            MediaController(applicationContext, session.sessionToken)
        }.getOrNull() ?: return session
        controllerByToken[token] = created
        return created
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

    // Sends a play command via the target's (cached) MediaController. Returns the controller used so
    // callers can probe its playback state without an extra IPC call, or null when no session matched.
    private suspend fun tryPlay(sessions: List<MediaController>, packageName: String): MediaController? {
        val match = findInList(sessions, packageName) ?: return null
        val sessionPkgs = sessions.joinToString(", ") { it.packageName ?: "?" }
        AppLogBuffer.debug(TAG, "Poll: $packageName | visible sessions: [$sessionPkgs]")
        val controller = controllerFor(match)
        val playbackState = match.playbackState
        val state = playbackState?.state
        val hasPlayAction = playbackState?.actions?.and(PlaybackState.ACTION_PLAY) != 0L
        return try {
            if (hasPlayAction || state == PlaybackState.STATE_NONE || state == PlaybackState.STATE_STOPPED) {
                controller.transportControls.play()
            } else {
                controller.dispatchMediaButtonEvent(KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_MEDIA_PLAY))
                controller.dispatchMediaButtonEvent(KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_MEDIA_PLAY))
            }
            controller
        } catch (e: Exception) {
            AppLogBuffer.warn(TAG, "Failed to send play to $packageName: ${e.message}")
            null
        }
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

        // Process-level "a resume flow was started and has not yet terminated" flag. Lives in the
        // companion (not the service instance) so it survives onUnbind() nulling `instance`, giving
        // callers (e.g. PebbleListenerService.onAppClosed) a stable view even after the service is
        // unbound mid-flow. Set by startResume(), cleared when the flow reaches its terminal
        // callback or when onUnbind() aborts it.
        @Volatile
        private var flowInProgress = false

        // True while a resume flow is running in this process (or was started and not yet terminated).
        // Callers must not stop/unbind the listener while this is true, or an in-flight flow would
        // be aborted mid-run and the screen wake lock leaked.
        val isFlowActive: Boolean
            get() = flowInProgress || (instance?.flowActive ?: false)

        /**
         * Best-effort: asks the system to bind this notification listener again if it was unbound
         * (e.g. after a process restart or on slow OEMs that drop the binding at boot). Safe to call
         * before [onListenerConnected] or after [onListenerDisconnected]; no-op when already bound.
         */
        fun requestRebindIfUnbound(context: Context) {
            if (instance != null) return
            runCatching {
                requestRebind(ComponentName(context.packageName, MediaControlListenerService::class.java.name))
                AppLogBuffer.info(TAG, "Requested rebind of notification listener")
            }.onFailure { e ->
                AppLogBuffer.warn(TAG, "Failed to rebind notification listener: ${e.message}")
            }
        }

        // Best-effort: tells the system the listener no longer wants notifications so the process can
        // idle while the feature is off. Some systems/OEMs keep a granted listener bound anyway, so the
        // result is only logged, never required.
        fun stopListenerBestEffort() {
            val service = instance ?: run {
                AppLogBuffer.warn(TAG, "Cannot stop notification listener: not bound")
                return
            }
            runCatching {
                service.stopSelf()
                AppLogBuffer.info(TAG, "Notification listener stop requested (best effort)")
            }.onFailure { e ->
                AppLogBuffer.warn(TAG, "Failed to stop notification listener: ${e.message}")
            }
        }

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
                // NLS not bound: do NOT open the settings screen here (it would turn the display
                // on before the fallback runs). The caller sends the legacy broadcast first and
                // then opens the notification-access settings informally; the returned false only
                // signals that no session-based resume was dispatched.
                AppLogBuffer.warn(TAG, "Notification listener not bound, deferring to caller fallback")
                return false
            }
            val clampedTimeout = timeoutMs.coerceIn(MIN_TIMEOUT_MS, MAX_TIMEOUT_MS).toInt()
            // Same-process handoff: the receiver runs in this same service process and reads
            // pendingFirstPhaseMs / pendingOnFinish / pendingOnAbort on the main thread; no lock needed.
            instance?.pendingFirstPhaseMs = firstPhaseMs
            instance?.pendingOnFinish = onFlowFinished
            // The abort callback mirrors onFlowFinished so that if the service is unbound mid-flow
            // (watch disconnect, system reclaim), onUnbind() still releases the screen wake lock.
            instance?.pendingOnAbort = onFlowFinished
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