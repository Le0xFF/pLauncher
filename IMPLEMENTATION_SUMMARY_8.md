# Implementation summary

## 40 - Play on Launch: Automatic media playback resume in the companion app

### Overview

Added a "Play on Launch" feature to the pLauncher companion app. When enabled and the user launches an app from the Pebble watch, the companion app automatically resumes media playback for that app without any phone interaction. The feature is entirely companion-side: no protocol change, no watchapp modification.

The section previously labeled "General" in Settings was renamed to "Companion app settings" to better reflect that it holds phone-local preferences (as opposed to "Watchapp settings" which hold values synced to the watch).

Previous implementations of this feature relied on `MediaSessionManager.getActiveSessions(ComponentName)` called directly from the app process (with Media3-based service discovery), which fails on modern Android with `SecurityException` ("Missing permission to control media") because the signature-level `MEDIA_CONTENT_CONTROL` permission is required. This implementation replaces that approach with a bound `NotificationListenerService`, which may call `getActiveSessions()` and receive usable cross-app `MediaController`s (including transport controls) **without** `MEDIA_CONTENT_CONTROL`.

Two refinements were added after on-device testing against TIDAL cold starts from a locked phone:

- **Screen wake/sleep.** A fully killed target app often needs its player UI/player to initialize before it registers a usable media session. To make that deterministic, when "Play on Launch" is active and the phone screen is off, the companion turns the display on (keyguard stays up) *before* launching the app, keeps it on for the whole resume flow, and turns it back off at the end — but only if it is the one that turned it on. If the screen was already on, its state is never touched.
- **Two-phase resume + strict success detection.** A single fixed wait proved unreliable: a cold-start session can appear early yet stay unplayable (PAUSED with no track queued) for ~15–20 s, or vanish entirely. The resume now runs in two phases (see below) and only declares success on a *genuine* playing state (`STATE_PLAYING`, or a seek with a real position), so a transient `STATE_BUFFERING`/initializing flap is no longer mistaken for a successful resume.

A follow-up hardening pass reduced battery cost and added one new constraint:

- **Battery / code optimizations.** The screen wake lock is a single idempotent lock guarded by an atomic "held" flag plus a watchdog that force-releases it if it is still held after the maximum legitimate flow length; the screen is never turned on when it is not needed (no notification access, failed launch, non-media app, phone already on); the notification listener self-manages (best-effort stop when the feature is off, automatic re-bind when it is on, and it is never stopped while a resume flow is in flight); and the polling loop was made event-driven (single IPC call per tick, reused `MediaController`s, immediate reaction to a newly appeared session, with the wake signal consumed so bursts of session-change events cannot drive the loop into a no-delay hot spin).
- **Permission gating.** Activating "Play on Launch" is now blocked unless the app has notification access: toggling it on without the permission shows an informative toast and leaves the switch OFF. If the permission is later revoked while the preference is still ON, the preference is automatically flipped back to OFF (persisted) on the next check, so the feature can never be left enabled in a state where it cannot work.

### Architecture: NotificationListenerService-based resume

The resume mechanism relies on a platform loophole: a bound `NotificationListenerService` may call `MediaSessionManager.getActiveSessions(ComponentName)` and receive usable cross-app `MediaController`s (including transport controls) **without** the signature-level `MEDIA_CONTENT_CONTROL` permission.

Flow when "Play on Launch" is enabled and a launch succeeds:

1. Before starting `LaunchActivity`, `PebbleListenerService.handleLaunchApp` checks whether the screen is off; if so (and the feature is on) `ScreenWakeHelper.wakeScreen` acquires a `SCREEN_DIM_WAKE_LOCK | ACQUIRE_CAUSES_WAKEUP` wake lock that turns the display on and keeps it on. Whether the companion woke the screen (`wokeUs`) is passed to `LaunchActivity` via `EXTRA_SCREEN_WAKED_BY_US` and carried through the `ACTION_LAUNCH_RESULT` broadcast.
2. `PebbleListenerService.launchResultReceiver` (fed by `LaunchActivity`, which includes `EXTRA_PACKAGE_NAME` and `EXTRA_SCREEN_WAKED_BY_US` in the `ACTION_LAUNCH_RESULT` broadcast) calls `MediaResumeHandler.resumeInBackground(packageName, scope, wokenByUs)` if `play_on_launch` is enabled.
3. The handler reads the two user-configured timers from `AppDataStore` — the resume timeout and the first-phase wait (see "Timer configuration") — applies a safety floor of **20 s** to the resume timeout, and calls `MediaControlListenerService.requestResume(context, packageName, timeoutMs, firstPhaseMs, onFlowFinished)`. `onFlowFinished` turns the screen off again, but only when `wokenByUs` is true.
4. `requestResume` checks whether the listener service is bound (`instance != null`):
    - **Bound**: hands the parameters to the service (same-process hand-off via fields set right before the broadcast) and sends an internal broadcast (`ACTION_REQUEST_RESUME`) carrying package name + clamped timeout. The service's receiver starts the two-phase resume.
    - **Not bound**: logs a warning and returns false without opening any screen — it is now the handler that reacts, in this order: (a) send the legacy `ACTION_MEDIA_BUTTON` broadcast first (it never turns the display on), (b) fire `onFlowFinished` so the screen wake lock is released, and only then (c) open the system notification-access settings informally (`FLAG_ACTIVITY_NEW_TASK`, wrapped in try/catch). If the OEM blocks background activity starts the start fails silently and is logged; the flow is already concluded by then. Branching aside, this case is nearly unreachable because activation of the feature is gated on notification access (see "Permission gating") and a revocation auto-flips the preference OFF; it remains as a safety net for edge cases such as a revocation that happens mid-flow or a process restart.
5. **First phase** (bounded by the *first-phase wait*): the service waits for a session of the target package, reacting both to its polling ticks and, immediately, to the platform `OnActiveSessionsChangedListener` event when a session appears/disappears (so it no longer waits up to a full tick for the session to show up). Each tick performs a single `getActiveSessions()` IPC call from which both the "session present?" check and the package match are derived, and reuses a cached `MediaController` while the session token is unchanged (a new controller is created only when the token changes). While no session exists it simply waits. Once one exists it sends `play()` (or `KEYCODE_MEDIA_PLAY` DOWN/UP when the session does not advertise `ACTION_PLAY`) and probes the resulting state once, non-blockingly. It succeeds as soon as a *genuine* playing state is seen. If the session has been present but unplayable for the whole first-phase window, the phase gives up early rather than burning the full budget on no-op `play()` calls.
6. **Second phase** (only if the first phase failed): the target app is **re-launched** via its launcher intent (`FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TOP`) and polling runs again using the same single-call-per-tick / reused-controller logic, bounded by the full *resume timeout*. On match it sends `play()` through the cached controller and verifies via follow-up polls (up to 10 s each) that playback actually became genuinely playing; otherwise `play()` is retried on subsequent polls.
7. Only if both phases fail does the last-resort fallback fire: `LegacyBroadcastFallback.sendPlay()` sends the targeted `ACTION_MEDIA_BUTTON` broadcast. After any terminal outcome (success, or fallback sent) `onFlowFinished` runs exactly once (idempotent) and turns the screen off if the companion had woken it. If the listener service is unbound mid-flow (e.g. the system reclaims it), `onUnbind` invokes a stored abort callback that performs the same screen release, so the wake lock cannot leak when the flow's coroutine never reaches its terminal step.

Required user setup: enable **Notification access** for pLauncher under *Settings → Accessibility/Notifications* (guided by the Grant button in the companion Settings → Permissions section). This permission is now a hard prerequisite for using the feature (see "Permission gating"); without it the switch cannot be turned on, and the only remaining runtime degradation is the legacy broadcast fallback for the rare not-bound edge case (log-only, no crash).

#### Permission gating

The "Play on Launch" switch can only be activated while the app holds notification access:

- **Activation blocked**: toggling OFF→ON when the permission is absent shows a toast ("Grant notification access first (Settings → Permissions) to use Play on launch") and leaves both the UI state and the persisted preference unchanged — the controlled switch visually snaps back to OFF. Deactivation (ON→OFF) is never blocked.
- **Auto-flip on revocation**: if the permission is revoked while the preference is ON, it is automatically flipped back to OFF (and persisted) at the next check: when the app starts (`onCreate`, after loading persisted prefs) and whenever `MainActivity` resumes. The flip is idempotent (a no-op once the preference is already false), so repeated resumes do not re-trigger it. With this in place the "feature ON but listener not bound" branch of the resume flow is only reachable as a safety net (e.g. revocation mid-flow, process restart without activity).

#### Notification listener self-management

To avoid keeping the `NotificationListenerService` alive when the feature is off, and to recover it when it is needed:

- **Best-effort stop**: when the feature is switched OFF from Settings, or when the watch disconnects, and no resume flow is in flight, the app calls `stopSelf()` on the listener. This asks the system that the listener no longer wants notifications; some systems/OEMs keep a granted listener bound anyway, so the outcome is logged but never required. The "flow in flight" check uses a process-level flag that survives the service instance being unbound, so a mid-flow watch disconnect can never trigger a stop that would abort the resume and leak the screen wake lock.
- **Automatic re-bind**: `requestRebind(component)` is issued on discrete events only — watch connection (`WatchWelcome` handling), device boot (`BootReceiver`), — whenever the feature is ON, the permission is granted, and the listener is not currently bound. No rebind is ever triggered in reaction to `onListenerDisconnected`, which only logs "listener disconnected — will be rebound on next need", to prevent rebind loops.

#### Battery optimizations summary

| Optimization | Effect |
|---|---|
| Screen wake lock with watchdog | The `SCREEN_DIM_WAKE_LOCK \| ACQUIRE_CAUSES_WAKEUP` lock is a single idempotent resource guarded by an atomic "held" flag: `wakeScreen` acquires it once (concurrent flows reuse the same lock) and `turnScreenOff` releases it exactly once; a watchdog force-releases it if it is still held after `MAX_PLAY_ON_LAUNCH_FIRST_PHASE_S + MAX_PLAY_ON_LAUNCH_TIMEOUT_S` seconds (= 120 s today), so a stalled or aborted flow can never leave the display on indefinitely. All transitions are logged. An earlier reference-counted design was replaced after on-device testing showed the counter could be zeroed before the release ran, leaking the lock. |
| No spurious screen wake-ups | The screen is turned on only when the feature is ON, the screen was actually off, and the launch index is valid. It is never touched for non-media apps, failed launches, or an already-on phone; when the companion did wake it, turning it off is simply the wake-lock release followed by the system's own screen-off timeout (no device policy, no `goToSleep()`/`lockNow()`). |
| Event-driven polling | One `getActiveSessions()` IPC call per tick instead of two; `MediaController`s are cached per session token and reused while the token is stable; the `OnActiveSessionsChangedListener` callback wakes the poll loop immediately when a session appears (previously the app waited up to one extra 500 ms tick). The wake signal is consumed on read, so a burst of session-change events (e.g. the target app flapping its session during cold start) cannot keep the loop in a no-delay hot spin — verified on device: ~2 polls/s cadence with resume landing right after first session visibility. |
| Listener self-stop / re-bind | With the feature off and nothing in flight the listener stops consuming every system notification (best effort); with the feature on it is guaranteed available at launch time via automatic re-bind. |

#### Timer configuration

Both timers are local numeric preferences (seconds), shown in "Companion app settings" directly below the "Play on Launch" switch, each as a max-2-digit `OutlinedTextField` that is disabled while the switch is OFF. In-range values are saved instantly as typed; out-of-range or empty input resets the ViewModel and data store to the default immediately, so the stored value never diverges from what the user sees. Neither value is ever pushed to the watch.

| Preference | Prefs key | Default | Valid range | Role |
|---|---|---|---|---|
| Initial wait before re-launch (s) | `play_on_launch_first_phase_s` | **30** | 1–60 | Bounds the **first phase**: how long to wait for the launched app's media session to become playable before re-launching the app. Lower = faster resume for apps that start quickly, but risks re-launching a slow app (e.g. TIDAL) before its session becomes usable. |
| Resume timeout (s) | `play_on_launch_timeout_s` | **60** | 1–60 | Bounds the **second phase**: the maximum time to keep waiting for the app's media session *after* the automatic re-launch before giving up and sending the legacy play command. A safety floor of **20 s** is applied (`MIN_EFFECTIVE_TIMEOUT_S`), so even a low/invalid stored value waits at least 20 s — enough for a target app's media session to appear after a cold start (TIDAL needs roughly 4–10 s once its player has initialized). |

Clamping to the valid ranges happens in `AppDataStore` on set/load; a final clamp to [1000, 60000] ms is applied in the service at resume time. The effective first-phase window uses the stored first-phase value directly (clamped to [1, 60] s); if it were ever absent it falls back to a value derived from the resume timeout.

### Files added

| File | Purpose |
|---|---|
| `media/MediaControlListenerService.kt` | `NotificationListenerService` that runs the two-phase resume: waits for active media sessions (poll ticks + `OnActiveSessionsChangedListener` events, with the wake signal consumed so event bursts cannot hot-spin the loop), sends `play()` (or media-key events) to the target app's session through a token-cached `MediaController`, confirms a genuine playing state, re-launches the app once if the first phase fails; exposes static `requestResume(context, packageName, timeoutMs, firstPhaseMs, onFlowFinished)` plus `requestRebindIfUnbound(context)` / `stopListenerBestEffort()` / `isFlowActive` (process-level, survives unbind) for listener self-management; `onUnbind` runs an abort callback so a mid-flow unbind still releases the screen wake lock |
| `media/LegacyBroadcastFallback.kt` | Sends an `ACTION_MEDIA_BUTTON` broadcast with a `KeyEvent` extra, addressed to a specific package via `setPackage()`; last-resort fallback |
| `media/MediaResumeHandler.kt` | Public entry point `resumeInBackground(packageName, scope, wokenByUs)`; reads both timer prefs, applies the 20 s effective floor to the resume timeout, builds the end-of-flow screen-off callback, and dispatches via `requestResume`; when the NLS is not bound it sends the legacy broadcast first, releases the flow, and only then opens the notification-access settings informally |
| `util/ScreenWakeHelper.kt` | Turns the display on (`SCREEN_DIM_WAKE_LOCK \| ACQUIRE_CAUSES_WAKEUP` wake lock) via `wakeScreen` (idempotent, concurrent flows share one lock), reports `isScreenOn`, and releases it via `turnScreenOff` (idempotent); guarded by an atomic "held" flag plus a watchdog (120 s, derived from the two max timer values) that force-releases a stale lock so a stalled or aborted flow cannot leave the screen on indefinitely |

### Files removed

| File | Reason |
|---|---|
| `media/PlaybackController.kt` | Replaced by the NLS-based controller binding inside `MediaControlListenerService` |
| `media/ServiceDiscovery.kt` | Service discovery/polling replaced by `getActiveSessions()` polling inside the NLS |
| `gradle/libs.versions.toml` / `app/build.gradle.kts` entries for `media3-session` | Media3 dependency no longer needed (zero code references) |
| `AndroidManifest.xml` `<queries>` intents for `MediaSessionService` / `MediaLibraryService` / `MediaBrowserService` | Orphaned after the discovery approach was dropped |

### Files modified

| File | Change |
|---|---|
| `res/values/strings.xml` | Renamed `settings_section_general` to "Companion app settings"; added `settings_play_on_launch` / `_desc`, `settings_notification_access` / `_desc`; added both timer fields — `settings_play_on_launch_first_phase` / `_desc` / `_placeholder` (initial wait before re-launch) and `settings_play_on_launch_timeout` / `_desc` / `_placeholder` (resume timeout); added `screen_wake_reason` / `screen_sleep_reason` used as the wake-lock tags/reasons; added `play_on_launch_requires_notification_access` (toast shown when activation is blocked) |
| `AndroidManifest.xml` | Added `<service .media.MediaControlListenerService>` with the `NotificationListenerService` intent-filter (exported=false, `BIND_NOTIFICATION_LISTENER_SERVICE` permission) |
| `data/AppDataStore.kt` | Added `playOnLaunch` StateFlow (key `play_on_launch`, default `false`) plus two timer StateFlows: `playOnLaunchTimeoutS` (key `play_on_launch_timeout_s`, default **60**, constants `MIN/MAX/DEFAULT_PLAY_ON_LAUNCH_TIMEOUT_S` = 1/60/60) and `playOnLaunchFirstPhaseS` (key `play_on_launch_first_phase_s`, default **30**, constants `MIN/MAX/DEFAULT_PLAY_ON_LAUNCH_FIRST_PHASE_S` = 1/60/30), each with getter/setter/load; setters and loaders clamp to their ranges |
| `MainActivity.kt` | `AppViewModel` gained `playOnLaunch`, `playOnLaunchTimeoutS` (+ `onPlayOnLaunchTimeoutInvalid` reset) and `playOnLaunchFirstPhaseS` (+ `onPlayOnLaunchFirstPhaseInvalid` reset); all loaded in `loadPersistedPrefs()`; wired to `SettingsScreen` (value + instant-apply callbacks, no watch push); recomputes `notificationAccessGranted` on resume. The `onPlayOnLaunchChange` callback blocks OFF→ON activation without notification access (toast, state/pref unchanged); `autoFlipPlayOnLaunchIfAccessMissing()` flips a persisted ON preference back to OFF (logged) when the permission is missing, run from `onCreate` after `loadPersistedPrefs()` and from `onResume`; deactivation also triggers a best-effort listener stop |
| `ui/SettingsScreen.kt` | New params for the switch and both timers; the "Initial wait before re-launch (s)" field is placed directly above "Resume timeout (s)" so the ordering matches the flow; both are disabled-by-switch numeric fields; new "Notification access" row (Grant/Revoke → `ACTION_NOTIFICATION_LISTENER_SETTINGS`) in "Permissions"; helper `checkNotificationListenerAccess(context)` |
| `LaunchActivity.kt` | Reads `EXTRA_SCREEN_WAKED_BY_US` from the start intent and includes both `EXTRA_PACKAGE_NAME` and `EXTRA_SCREEN_WAKED_BY_US` in the `ACTION_LAUNCH_RESULT` broadcast |
| `PebbleListenerService.kt` | In `handleLaunchApp`, wakes the screen (if off and the feature is on) via `maybeWakeScreenForPlayOnLaunch()` and passes `EXTRA_SCREEN_WAKED_BY_US` to `LaunchActivity`; instantiates `MediaResumeHandler(applicationContext, dataStore)` in `onCreate()` (logs NLS grant state); in `launchResultReceiver.onReceive`, if launch succeeded and `play_on_launch` is enabled, calls `resumeInBackground(packageName, coroutineScope, wokenByUs)`; `handleWatchWelcome` self-heals the listener (`requestRebindIfUnbound` when feature ON + granted + not bound); `onAppClosed` best-effort stops the listener when the feature is off and no flow is in flight |
| `BootReceiver.kt` | After `BOOT_COMPLETED`, in addition to starting `PebbleListenerService`, requests a listener re-bind when the feature is ON and granted (covers slow OEMs that drop the binding at boot) |
| `COMMUNICATION_PROTOCOL.md` | Documented that "Play on Launch" is companion-app-only and never sent to the watch |
@impl8
### Known limitations

- **Notification access is a hard prerequisite**: activating "Play on Launch" without it is blocked (toast; switch stays OFF), and if it is later revoked while the preference is ON the preference is automatically flipped back to OFF (persisted) at the next check (`onCreate` / `onResume`). The runtime not-bound branch of the resume flow therefore remains only as a safety net for edge cases (revocation mid-flow, process restart): it sends the legacy broadcast first and then opens the notification-access settings informally, which many modern music apps ignore. Some OEM skins hide/relocate the notification-listener toggle.
- The target app must expose a usable, genuinely playable `MediaSession` within the configured windows (first-phase wait before the re-launch, then the resume timeout after it). Apps whose player initializes later than that will miss the play command — raise the relevant timer to cover very slow cold starts.
- **Cold-start flakiness of the target app itself is out of scope.** If the app crashes or restarts during its own startup (observed with TIDAL behind the keyguard on some runs), its media session can appear unplayable or vanish entirely; even a re-launch may not produce a playable session in time, and the flow ends at the legacy-broadcast fallback. Raising the timers widens the window but cannot force an app to finish authenticating/initializing.
- **Screen wake/sleep uses a wake lock** (`SCREEN_DIM_WAKE_LOCK | ACQUIRE_CAUSES_WAKEUP`) plus the system's own screen-off timeout: turning the screen off is nothing more than releasing the wake lock (no device admin, no `goToSleep()`/`lockNow()` call). Residual limit: on some devices/OEMs (notably while charging) the display may stay on for a few minutes longer than the end of the flow, until the platform screen-off timeout elapses.
- Fully killed apps are launched first by `LaunchActivity`; the poll waits for their session to appear. A dedicated wake lock for the polling phase beyond the screen wake lock is out of scope (the foreground service keeps the process alive).
- One resume target per launch event; simultaneous multi-app resumes are out of scope (the shared wake lock does tolerate concurrent flows, but the resume strategy itself targets a single package per launch).
- **Listener stop is best-effort**: some systems/OEMs keep a granted notification listener bound even after `stopSelf()`, so with the feature off the process may still receive system notifications; the guaranteed part of the self-management is the automatic re-bind when the feature is on.
- **Background activity start may be blocked**: the informational opening of the notification-access settings from the background (not-bound safety-net path) can be denied by the system/OEM; this is logged and has no functional impact, since the fallback broadcast already ran.
- The "Play on Launch" setting and both timers are local to the phone and are never synchronized with the watch. The `LAUNCH_APP` packet payload is unchanged.

### Test procedure (manual, requires hardware)

1. **Switch OFF**: launch a music app from the watch → no resume (pre-feature behavior), no screen wake.
2. **Switch ON + notification access granted, TIDAL closed, phone locked**: launch from the watch → screen turns on, playback resumes within the configured windows (the event-driven session listener makes it land right after the session first becomes visible), screen turns off again at the end. Verify in the logs: "Poll:" timestamps at ~2/s cadence (no hot loop), the "Resumed playback" line, and a final "Screen off released via …" line; then confirm via `adb shell dumpsys power` that no pLauncher wake lock remains held.
3. **Permission absent → activation attempt**: with notification access NOT granted, try to toggle "Play on Launch" ON → toast "Grant notification access first (Settings → Permissions) to use Play on launch", switch stays OFF, preference remains false; then grant the permission via Settings → Permissions and toggle ON → works.
4. **Auto-flip on revocation**: with the switch ON and the permission granted, revoke notification access from the system settings → return to the companion app (or reopen it) → the switch shows OFF and the persisted preference is false (log "Play on launch auto-disabled").
5. **Launch with feature ON but NLS not bound** (safety net, e.g. revocation mid-flow or process restart): launch → legacy broadcast sent first, no crash, log consistent ("deferring to caller fallback"); the notification-access settings may open informally if the OEM allows background activity starts.
6. **Non-media app** (e.g. calculator): opens normally, no side effects; logs show poll timeout / fallback.
7. **Listener self-stop**: feature toggled off → verify (battery stats / `dumpsys notification` / logs) that the notification listener is no longer invoked by notifications (best effort; some OEMs keep it bound); feature ON → launch from the watch → works.
8. **Reboot with feature ON**: after reboot, the first launch from the watch resumes correctly (listener re-bound by `BootReceiver`).
9. **Phone already unlocked/screen on**: no screen wake and no screen-off at the end; resume proceeds as in case 2 otherwise.
10. **Custom timers**: e.g. lower the initial wait to re-launch a fast app sooner, or raise the resume timeout to cover a very slow cold start; verify via the session logs that the re-launch happens at the expected moment.
11. **Watch disconnects mid-flow** (PebbleKit closes the sender ~5 s after a launch): with the screen woken by the companion, a mid-flow watch disconnect must not stop the listener or leak the wake lock — the flow completes and the log ends with "Screen off released via …" (or the abort path releases it if the system unbinds the service).
12. **Playback never resumes even though the flow completed cleanly**: check whether the target app is actually logged in / authenticated (TIDAL may come up on its login screen, exposing a session that can never reach a genuine playing state) — this is the documented target-app limit, not a companion bug; the legacy-broadcast fallback cannot start playback either.
@impl8
## 41 - Touch input (swipe + tap) for emery in the watchapp

### Overview

Added touchscreen support to the pLauncher watchapp for Pebble Time 2 (`emery`):

- **Swipe up** (finger moves bottom→top) → list advances to the next app (`app_list_next()`).
- **Swipe down** (finger moves top→bottom) → list goes back to the previous app (`app_list_prev()`). Both wrap around at the ends.
- **Single tap** anywhere on the screen → launches the selected app (`send_launch_app(current_index)`), identical behavior to the physical SELECT button.

All gestures are ignored while the app list is loading (`packets_is_loading()` guard), matching the existing click-handler guards. On basalt (no touchscreen) the whole module compiles to SDK no-ops and is simply inert; the build succeeds unchanged for both targets.

### Why the raw touch stream instead of the recognizer API

The original plan used the window recognizer API (`window_attach_recognizer` with `tap_recognizer_create` / `pan_recognizer_create`, plus `window_set_touch_bridge_disabled`). On a real emery device this produced **zero recognizer callbacks** even though:

- `touch_service_is_enabled()` returned true,
- the raw `touch_service_subscribe` stream delivered every touchdown/liftoff with `non_navigational = 0` (interaction session active),
- the system's own touch-navigation bridge (`app_touch_navigation_enable(true)`) also did not respond to touches.

Device testing (via `pebble logs --phone=<ip>`) proved the recognizer path is non-functional on the current firmware while the raw stream works fine. The fix — confirmed against three working emery reference apps (touch-tone, pebble-calculator, pebble-2048-touch, all of which use only the raw stream and no recognizers/bridge calls) — was to reimplement gesture detection directly on the raw `TouchEvent` stream.

### Implementation

A single `touch_handler(const TouchEvent *, void *)` classifies each gesture:

- **Touchdown**: records the start position, timestamp, and resets the max-movement tracker; marks the gesture active.
- **PositionUpdate**: tracks the maximum displacement from the touchdown point (for tap validation).
- **Liftoff**: finishes the gesture:
  - `|Δy| >= TOUCH_SWIPE_MIN_DELTA_PX` → swipe; negative Δy = up = `app_list_next()`, positive Δy = down = `app_list_prev()`, then `window_main_update_display()`.
  - otherwise, if `dt <= TOUCH_TAP_TIMEOUT_MS` and max movement `<= TOUCH_TAP_MAX_MOVE_PX` → valid tap → `send_launch_app(app_list_get_current_index())` (skipped when the list is empty or loading).
  - touches flagged `non_navigational` (idle-watchface contacts) are never acted upon.

Constants live in `window_main_touch.h` (initial estimates, not yet tuned on device): `TOUCH_SWIPE_MIN_DELTA_PX 40`, `TOUCH_TAP_MAX_MOVE_PX 16`, `TOUCH_TAP_TIMEOUT_MS 300`.

Subscription is guarded by `touch_service_is_enabled()` and happens in `window_load` via `window_main_touch_init()`; `window_unload` calls `window_main_touch_deinit()` which unsubscribes. No `#ifdef` platform guards are needed: on basalt every touch API compiles to a `(0)` no-op, so the same source builds for both platforms.

### Files added

| File | Purpose |
|---|---|
| `pbw/src/c/window_main_touch.h` | Gesture threshold constants and `window_main_touch_init()` / `window_main_touch_deinit()` declarations |
| `pbw/src/c/window_main_touch.c` | Raw-stream touch handler with tap/swipe classification, loading guards, and subscribe/unsubscribe lifecycle |

### Files modified

| File | Change |
|---|---|
| `pbw/src/c/window_main.c` | Includes `window_main_touch.h`; calls `window_main_touch_init()` in `window_load` after the click-config provider and `window_main_touch_deinit()` in `window_unload` |

### Known limitations

- Thresholds are initial estimates, not tuned on device; smaller/slower swipes may need `TOUCH_SWIPE_MIN_DELTA_PX` lowered.
- A slow swipe that stops mid-gesture and then lifts is still recognized as a swipe (no dwell/in-motion check at liftoff); impact is limited to one extra list step.
- The window recognizer API remains unusable on the tested firmware; any future feature relying on it would hit the same wall.