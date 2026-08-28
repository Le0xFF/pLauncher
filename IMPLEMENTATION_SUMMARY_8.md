# Implementation summary

## 40 - Play on Launch: Automatic media playback resume in the companion app

### Overview

Added a "Play on Launch" feature to the pLauncher companion app. When enabled and the user launches an app from the Pebble watch, the companion app automatically resumes media playback for that app without any phone interaction. The feature is entirely companion-side: no protocol change, no watchapp modification.

The section previously labeled "General" in Settings was renamed to "Companion app settings" to better reflect that it holds phone-local preferences (as opposed to "Watchapp settings" which hold values synced to the watch).

Previous implementations of this feature relied on `MediaSessionManager.getActiveSessions(ComponentName)` called directly from the app process (with Media3-based service discovery), which fails on modern Android with `SecurityException` ("Missing permission to control media") because the signature-level `MEDIA_CONTENT_CONTROL` permission is required. This implementation replaces that approach with a bound `NotificationListenerService`, which may call `getActiveSessions()` and receive usable cross-app `MediaController`s (including transport controls) **without** `MEDIA_CONTENT_CONTROL`.

Two refinements were added after on-device testing against TIDAL cold starts from a locked phone:

- **Screen wake/sleep.** A fully killed target app often needs its player UI/player to initialize before it registers a usable media session. To make that deterministic, when "Play on Launch" is active and the phone screen is off, the companion turns the display on (keyguard stays up) *before* launching the app, keeps it on for the whole resume flow, and turns it back off at the end — but only if it is the one that turned it on. If the screen was already on, its state is never touched.
- **Two-phase resume + strict success detection.** A single fixed wait proved unreliable: a cold-start session can appear early yet stay unplayable (PAUSED with no track queued) for ~15–20 s, or vanish entirely. The resume now runs in two phases (see below) and only declares success on a *genuine* playing state (`STATE_PLAYING`, or a seek with a real position), so a transient `STATE_BUFFERING`/initializing flap is no longer mistaken for a successful resume.

### Architecture: NotificationListenerService-based resume

The resume mechanism relies on a platform loophole: a bound `NotificationListenerService` may call `MediaSessionManager.getActiveSessions(ComponentName)` and receive usable cross-app `MediaController`s (including transport controls) **without** the signature-level `MEDIA_CONTENT_CONTROL` permission.

Flow when "Play on Launch" is enabled and a launch succeeds:

1. Before starting `LaunchActivity`, `PebbleListenerService.handleLaunchApp` checks whether the screen is off; if so (and the feature is on) `ScreenWakeHelper.wakeScreen` acquires a `SCREEN_DIM_WAKE_LOCK | ACQUIRE_CAUSES_WAKEUP` wake lock that turns the display on and keeps it on. Whether the companion woke the screen (`wokeUs`) is passed to `LaunchActivity` via `EXTRA_SCREEN_WAKED_BY_US` and carried through the `ACTION_LAUNCH_RESULT` broadcast.
2. `PebbleListenerService.launchResultReceiver` (fed by `LaunchActivity`, which includes `EXTRA_PACKAGE_NAME` and `EXTRA_SCREEN_WAKED_BY_US` in the `ACTION_LAUNCH_RESULT` broadcast) calls `MediaResumeHandler.resumeInBackground(packageName, scope, wokenByUs)` if `play_on_launch` is enabled.
3. The handler reads the two user-configured timers from `AppDataStore` — the resume timeout and the first-phase wait (see "Timer configuration") — applies a safety floor of **20 s** to the resume timeout, and calls `MediaControlListenerService.requestResume(context, packageName, timeoutMs, firstPhaseMs, onFlowFinished)`. `onFlowFinished` turns the screen off again, but only when `wokenByUs` is true.
4. `requestResume` checks whether the listener service is bound (`instance != null`):
    - **Bound**: hands the parameters to the service (same-process hand-off via fields set right before the broadcast) and sends an internal broadcast (`ACTION_REQUEST_RESUME`) carrying package name + clamped timeout. The service's receiver starts the two-phase resume.
    - **Not bound**: opens the system notification-access settings screen so the user can grant/re-grant the permission, logs a warning, and returns false → the handler falls back to the legacy `ACTION_MEDIA_BUTTON` broadcast only and still fires `onFlowFinished` so the screen is released. (Note: the plan proposed `requestRebind` self-heal here; the implemented behavior instead surfaces the settings screen, since a granted-but-unbound listener is rare and rebind alone cannot help if the permission was never granted.)
5. **First phase** (bounded by the *first-phase wait*): every 500 ms the service looks for a session of the target package. While none exists it simply waits. Once one exists it sends `play()` (or `KEYCODE_MEDIA_PLAY` DOWN/UP when the session does not advertise `ACTION_PLAY`) and probes the resulting state once, non-blockingly. It succeeds as soon as a *genuine* playing state is seen. If the session has been present but unplayable for the whole first-phase window, the phase gives up early rather than burning the full budget on no-op `play()` calls.
6. **Second phase** (only if the first phase failed): the target app is **re-launched** via its launcher intent (`FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TOP`) and polling runs again, bounded by the full *resume timeout*. On match it binds a `MediaController` on the session token, sends `play()`, and verifies via follow-up polls (up to 10 s each) that playback actually became genuinely playing; otherwise `play()` is retried on subsequent polls.
7. Only if both phases fail does the last-resort fallback fire: `LegacyBroadcastFallback.sendPlay()` sends the targeted `ACTION_MEDIA_BUTTON` broadcast. After any terminal outcome (success, or fallback sent) `onFlowFinished` runs exactly once (idempotent) and turns the screen off if the companion had woken it.

Required user setup: enable **Notification access** for pLauncher under *Settings → Accessibility/Notifications* (guided by the Grant button in the companion Settings → Permissions section). Without this permission the feature degrades to the legacy broadcast fallback (log-only, no crash).

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
| `media/MediaControlListenerService.kt` | `NotificationListenerService` that runs the two-phase resume: polls active media sessions, sends `play()` (or media-key events) to the target app's session, confirms a genuine playing state, re-launches the app once if the first phase fails, and exposes static `requestResume(context, packageName, timeoutMs, firstPhaseMs, onFlowFinished)` |
| `media/LegacyBroadcastFallback.kt` | Sends an `ACTION_MEDIA_BUTTON` broadcast with a `KeyEvent` extra, addressed to a specific package via `setPackage()`; last-resort fallback |
| `media/MediaResumeHandler.kt` | Public entry point `resumeInBackground(packageName, scope, wokenByUs)`; reads both timer prefs, applies the 20 s effective floor to the resume timeout, builds the end-of-flow screen-off callback, and dispatches via `requestResume` (broadcast fallback when NLS is unavailable) |
| `util/ScreenWakeHelper.kt` | Turns the display on (`SCREEN_DIM_WAKE_LOCK \| ACQUIRE_CAUSES_WAKEUP` wake lock, held for the whole flow) via `wakeScreen`, reports `isScreenOn`, and releases it via `turnScreenOff` so the screen returns to its previous state |

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
| `res/values/strings.xml` | Renamed `settings_section_general` to "Companion app settings"; added `settings_play_on_launch` / `_desc`, `settings_notification_access` / `_desc`; added both timer fields — `settings_play_on_launch_first_phase` / `_desc` / `_placeholder` (initial wait before re-launch) and `settings_play_on_launch_timeout` / `_desc` / `_placeholder` (resume timeout); added `screen_wake_reason` / `screen_sleep_reason` used as the wake-lock tags/reasons |
| `AndroidManifest.xml` | Added `<service .media.MediaControlListenerService>` with the `NotificationListenerService` intent-filter (exported=false, `BIND_NOTIFICATION_LISTENER_SERVICE` permission) |
| `data/AppDataStore.kt` | Added `playOnLaunch` StateFlow (key `play_on_launch`, default `false`) plus two timer StateFlows: `playOnLaunchTimeoutS` (key `play_on_launch_timeout_s`, default **60**, constants `MIN/MAX/DEFAULT_PLAY_ON_LAUNCH_TIMEOUT_S` = 1/60/60) and `playOnLaunchFirstPhaseS` (key `play_on_launch_first_phase_s`, default **30**, constants `MIN/MAX/DEFAULT_PLAY_ON_LAUNCH_FIRST_PHASE_S` = 1/60/30), each with getter/setter/load; setters and loaders clamp to their ranges |
| `MainActivity.kt` | `AppViewModel` gained `playOnLaunch`, `playOnLaunchTimeoutS` (+ `onPlayOnLaunchTimeoutInvalid` reset) and `playOnLaunchFirstPhaseS` (+ `onPlayOnLaunchFirstPhaseInvalid` reset); all loaded in `loadPersistedPrefs()`; wired to `SettingsScreen` (value + instant-apply callbacks, no watch push); recomputes `notificationAccessGranted` on resume |
| `ui/SettingsScreen.kt` | New params for the switch and both timers; the "Initial wait before re-launch (s)" field is placed directly above "Resume timeout (s)" so the ordering matches the flow; both are disabled-by-switch numeric fields; new "Notification access" row (Grant/Revoke → `ACTION_NOTIFICATION_LISTENER_SETTINGS`) in "Permissions"; helper `checkNotificationListenerAccess(context)` |
| `LaunchActivity.kt` | Reads `EXTRA_SCREEN_WAKED_BY_US` from the start intent and includes both `EXTRA_PACKAGE_NAME` and `EXTRA_SCREEN_WAKED_BY_US` in the `ACTION_LAUNCH_RESULT` broadcast |
| `PebbleListenerService.kt` | In `handleLaunchApp`, wakes the screen (if off and the feature is on) via `maybeWakeScreenForPlayOnLaunch()` and passes `EXTRA_SCREEN_WAKED_BY_US` to `LaunchActivity`; instantiates `MediaResumeHandler(applicationContext, dataStore)` in `onCreate()` (logs NLS grant state); in `launchResultReceiver.onReceive`, if launch succeeded and `play_on_launch` is enabled, calls `resumeInBackground(packageName, coroutineScope, wokenByUs)` |
| `COMMUNICATION_PROTOCOL.md` | Documented that "Play on Launch" is companion-app-only and never sent to the watch |
@impl8
### Known limitations

- **Notification access must be granted**: without it, no silent resume is possible (platform rule); the app opens the system notification-access settings and falls back to the legacy broadcast, which many modern music apps ignore. Some OEM skins hide/relocate the notification-listener toggle.
- The target app must expose a usable, genuinely playable `MediaSession` within the configured windows (first-phase wait before the re-launch, then the resume timeout after it). Apps whose player initializes later than that will miss the play command — raise the relevant timer to cover very slow cold starts.
- **Cold-start flakiness of the target app itself is out of scope.** If the app crashes or restarts during its own startup (observed with TIDAL behind the keyguard on some runs), its media session can appear unplayable or vanish entirely; even a re-launch may not produce a playable session in time, and the flow ends at the legacy-broadcast fallback. Raising the timers widens the window but cannot force an app to finish authenticating/initializing.
- **Screen wake/sleep uses a wake lock** (`SCREEN_DIM_WAKE_LOCK | ACQUIRE_CAUSES_WAKEUP`). This turns the display on over the keyguard and keeps it on until released; the platform `PowerManager.wakeUp()`/`goToSleep()` APIs are not used because they are not exposed by the compile SDK. The screen is only turned off again when the companion was the one that woke it.
- Fully killed apps are launched first by `LaunchActivity`; the poll waits for their session to appear. A dedicated wake lock for the polling phase beyond the screen wake lock is out of scope (the foreground service keeps the process alive).
- One resume target per launch event; simultaneous multi-app resumes are out of scope.
- The "Play on Launch" setting and both timers are local to the phone and are never synchronized with the watch. The `LAUNCH_APP` packet payload is unchanged.

### Test procedure (manual, requires hardware)

1. **Switch OFF**: launch a music app from the watch → no resume (pre-feature behavior), no screen wake.
2. **Switch ON + notification access granted, TIDAL closed, phone locked**: launch from the watch → screen turns on, playback resumes within the configured windows, screen turns off again at the end.
3. **Switch ON + notification access NOT granted**: launch → system notification-access settings open, no crash, log "Notification listener not bound", fallback broadcast sent (likely ignored).
4. **Non-media app** (e.g. calculator): opens normally, no side effects; logs show poll timeout / fallback.
5. **Auto-launch from watchapp**: behaves consistently with the switch state.
6. **Phone already unlocked/screen on**: no screen wake and no screen-off at the end; resume proceeds as in case 2 otherwise.
7. **Custom timers**: e.g. lower the initial wait to re-launch a fast app sooner, or raise the resume timeout to cover a very slow cold start; verify via the session logs that the re-launch happens at the expected moment.
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