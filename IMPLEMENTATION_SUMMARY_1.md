# Implementation summary

## #1 — Initial implementation: watch app, companion app, protocol, bug fixes

### Overview

This is the first implementation of pLauncher, a Pebble smartwatch app and Android companion app that lets you launch smartphone applications directly from your Pebble watch. The implementation follows an 8-step plan, plus subsequent bug fixes.

### Architecture

Two applications communicate via Pebble AppMessage over Bluetooth:

- **Watch App** (`pbw/`): C code, Pebble SDK v3, targets `basalt` and `emery`
- **Android Companion App** (`apk/`): Kotlin + Jetpack Compose, PebbleKit2 (`io.rebble.pebblekit2:client:1.2.0`)

### Watch App (`pbw/`) — 348 lines C, 9 source files

#### Modular Structure

The original monolithic `pLauncher.c` (60 lines) was refactored into focused modules:

| File | Lines | Purpose |
|---|---|---|
| `pLauncher.c` | 24 | Thin main: init modules, send welcome, event loop, deinit |
| `app_list.h` / `app_list.c` | 100 | App list data (max 20 apps, 32-char name, 64-char package), navigation with wrap, add/clear/reset |
| `window_main.h` / `window_main.c` | 73 | Window with two TextLayers: app name (GOTHIC_24 centered) + index indicator (GOTHIC_14), display update |
| `window_main_click.h` / `window_main_click.c` | 33 | Click config provider: UP/DOWN with repeating (200ms), SELECT launches app |
| `packets.h` / `packets.c` | 118 | AppMessage init (1024/1024 buffer), send welcome/launch, receive dispatcher, phone welcome parsing, app list parsing with chunk support |

#### Packet Receive Implementation

- Dispatches on packet type (key `0`): type `10` → phone welcome, type `11` → app list
- Phone welcome: validates protocol version matches `1`
- App list: handles chunked delivery with offset (key `8`) and completion flag (key `9`). First chunk clears list. Each chunk adds one app (name from key `4`, package from key `5`). Last chunk resets index to 0 and updates display.

#### Configuration

- `package.json`: `enableMultiJS: false`, companion app registered (`com.le0xff.plauncher`), targets `basalt` + `emery` only, launcher icon resource
- UUID preserved: `07b1efa9-3d32-423c-b0e7-572cbc0893b8`

### Android Companion App (`apk/`) — 598 lines Kotlin, 8 source files

#### Project Structure

| File | Lines | Purpose |
|---|---|---|
| `MainActivity.kt` | 155 | AppViewModel with StateFlow, Scaffold with bottom NavigationBar, reactive StateFlow collection, AppPickerDialog integration, watch sync |
| `PebbleListenerService.kt` | 97 | Extends BasePebbleListenerService; receives watch packets; handles welcome (sends response + app list) and launch (starts phone activity) |
| `PebbleSenderHelper.kt` | 74 | Wraps DefaultPebbleSender; sends welcome/app list; one-app-per-chunk due to dictionary key uniqueness |
| `data/AppDataStore.kt` | 50 | SharedPreferences persistence with StateFlow; stores apps as pipe-delimited string; system apps toggle |
| `model/LaunchApp.kt` | 6 | Data class: packageName + displayName |
| `ui/AppScreen.kt` | 63 | LazyColumn of configured apps, search TextField, empty state, FAB to add apps |
| `ui/SettingsScreen.kt` | 31 | Show system apps toggle switch, version text |
| `ui/AppPickerDialog.kt` | 122 | AlertDialog with installed apps list, search, checkboxes, system app filtering, Done/Cancel |

#### Key Implementation Details

**Reactive UI**: All StateFlow properties are collected with `.collectAsState()` in MainActivity, ensuring composables re-render on data changes.

**TopAppBar removed**: Scaffold has no top bar — screens manage their own titles. Bottom NavigationBar provides Apps/Settings tabs.

**App Picker**: Queries all installed packages via `PackageManager.getInstalledPackages()`, displays icons and labels, filters system apps based on setting, tracks local checkbox selection with `mutableStateOf` + `LaunchedEffect` sync.

**PebbleKit2 Integration**:
- `PebbleListenerService`: Bound by Pebble mobile app via `io.rebble.pebblekit2.RECEIVE_DATA_FROM_WATCH` intent. Parses incoming `PebbleDictionary` (Map<UInt, PebbleDictionaryItem>). Numbers from watch arrive as UInt32/Int32 regardless of original size.
- `PebbleSenderHelper`: Uses `DefaultPebbleSender` with watch app UUID. Sends one app per chunk (Pebble dictionary enforces unique keys). Empty list sends single packet with count=0 + completion flag.

**Data Persistence**: Apps stored as newline-separated `packageName|displayName` strings in SharedPreferences. Settings stored as boolean.

### Communication Protocol

Defined in `COMMUNICATION_PROTOCOL.md`:

- Key `0` (UInt8): Packet type
- Key `1` (UInt16): Protocol version (currently `1`)
- Key `2` (UInt8): App index to launch
- Key `3` (UInt8): App count
- Key `4` (CStr): App display name
- Key `5` (CStr): App package name
- Key `8` (UInt16): Chunk offset
- Key `9` (UInt8): Completion flag (1 = last chunk)

Packet types: `0` = Watch Welcome, `1` = Launch App, `10` = Phone Welcome, `11` = App List

### Bug Fixes Applied

1. **Duplicate title bar**: Removed `TopAppBar` from Scaffold. Removed `ExperimentalMaterial3Api` opt-in.
2. **Non-reactive screens**: Changed all `viewModel.xxx.value` to `by viewModel.xxx.collectAsState()` for proper recomposition.
3. **Empty app picker**: Moved `getInstalledLaunchableApps()` inside dialog with `remember(LocalContext.current)`. Changed from `queryIntentActivities` to `getInstalledPackages` for broader app coverage. Fixed `localSelected` state with `LaunchedEffect` sync.

### Code Statistics

| Component | Files | Lines |
|---|---|---|
| Watch App (C) | 9 | 348 |
| Android App (Kotlin) | 8 | 598 |
| **Total** | **17** | **946** |

### Build Status

- Watch app: `pebble build` — compiles cleanly for `basalt` and `emery`
- Android app: `./gradlew --no-daemon assembleDebug` — BUILD SUCCESSFUL

### Known Limitations

- App list capped at 20 apps (watch RAM constraint)
- App names truncated to 32 characters, package names to 64 characters
- Each app sent as separate chunk (one app per AppMessage due to key uniqueness)
- No connection status indicator visible in UI (TopAppBar was removed)
- No visual feedback on watch when app is launched
- Settings screen shows static version text, no dynamic app info

---

## #2 — UI Improvements: button labels, empty state, layout constants

### Overview

Improved the watch app UI with button action labels, an informative empty-state message, and a centralized layout/string configuration system. Implementation follows a 2-step plan from `.kilo/plans/1784873002325-plauncher-ui-improvements.md`, plus iterative refinements based on user feedback.

### Watch App (`pbw/`) — 171 lines C, 12 source files (3 new)

#### New Files

| File | Lines | Purpose |
|---|---|---|
| `strings.h` / `strings.c` | 19 | Centralized text strings: `STR_LABEL_UP`, `STR_LABEL_DOWN`, `STR_LABEL_LAUNCH` defines, `str_empty_message()` function for the empty-state message |
| `layout.h` | 24 | Layout constants: font heights, margins, spacing, column divisors, zone counts — all magic numbers extracted from `window_main.c` |

#### Modified Files

| File | Lines | Changes |
|---|---|---|
| `window_main.c` | 128 | Added 3 label TextLayers (`s_label_up`, `s_label_down`, `s_label_launch`), screen bounds tracking (`s_screen_bounds`), dynamic layout calculations, empty-state handling, null guard for early calls |

#### Layout Architecture

The UI uses a **dynamic layout** calculated from `layer_get_bounds()` in `window_load()`, ensuring compatibility with both Basalt (144×168) and Emery (200×228) screens. All dimensional values come from `layout.h` constants (`LAYOUT_*` prefix).

**Screen layout** (when apps present):
- **App name** (`s_text_layer`): GOTHIC_24, centered vertically, occupies left portion of screen (width = `w - w/5`)
- **Button labels** (`s_label_up`, `s_label_launch`, `s_label_down`): GOTHIC_18, right column (width = `w/5`), each centered vertically within 1/3 zones of the screen height
  - Up: top zone, Launch: middle zone, Down: bottom zone
- **Index** (`s_index_layer`): GOTHIC_14, bottom edge, width matches app name (not full screen), centered horizontally relative to the name

**Screen layout** (when no apps):
- **Empty message** (`s_text_layer`): multi-line `"No apps\nAdd via\nCompanion"` in GOTHIC_24, centered both vertically and horizontally within the left portion of the screen. Uses a two-step centering approach: first expands the layer to full available height to let `text_layer_get_content_size()` calculate the correct multi-line height, then repositions the frame to center vertically.
- **Button labels**: remain visible
- **Index**: hidden

#### Key Implementation Details

**Early call guard**: `window_main_update_display()` is called from `window_main_create()` before `window_load()` runs (layers are NULL). A null check (`if (!s_text_layer) return;`) prevents crashes. This was the root cause of an initial crash that also affected the companion app.

**Two-step vertical centering**: `text_layer_get_content_size()` calculates dimensions based on the *current* frame. To correctly measure a multi-line text, the frame is first expanded to full height, the content size is read, then the frame is repositioned to center the content vertically.

**Dynamic frame restoration**: When transitioning from empty to populated state, `window_main_update_display()` recalculates all frames from `s_screen_bounds`, ensuring clean transitions without visual artifacts.

**API compatibility**: Used `layer_set_hidden()` (base Layer API) instead of the non-existent `text_layer_set_hidden()`. Used `layer_set_frame()` instead of `text_layer_set_frame()`.

#### Bug Fixes Applied

1. **Crash on launch**: `window_main_update_display()` called before layers initialized. Fixed with null guard.
2. **Text truncation (empty message)**: `text_layer_get_content_size()` returned wrong height when called on a narrow frame. Fixed with two-step expand-measure-center approach.
3. **Label positioning**: Labels were anchored to zone top instead of centered. Fixed by calculating `y = zone_center - label_height/2`.
4. **Index alignment**: Index was full-screen width instead of matching app name width. Fixed to use `w_name` width at bottom edge.

#### Configuration

- `layout.h`: 8 constants with `LAYOUT_` prefix for all layout dimensions
- `strings.h`/`strings.c`: 3 label defines (`STR_LABEL_*`) + 1 empty message function

### Code Statistics

| Component | Files | Lines |
|---|---|---|
| Watch App (C) | 12 | 519 (348 existing + 171 new/changed) |
| Android App (Kotlin) | 8 | 598 (unchanged) |
| **Total** | **20** | **1117** |

### Build Status

- Watch app: `pebble build` — compiles cleanly for `basalt` and `emery`
- Android app: `./gradlew --no-daemon assembleDebug` — BUILD SUCCESSFUL (unchanged)

---

## #3 — Crash Report Screen for Companion App

### Overview

Implemented a crash reporting system for the Android companion app that displays crash details on screen when the app crashes. The feature is user-controlled via a "Generate crash reports" toggle in Settings (disabled by default). When enabled, uncaught exceptions on the main thread are caught, and a dedicated CrashReportActivity shows the exception type, message, stack trace, and device information, with options to restart or close the app.

### Android Companion App (`apk/`) — ~233 lines Kotlin, 2 new source files

#### New Files

| File | Lines | Purpose |
|---|---|---|
| `CrashApplication.kt` | 62 | Custom Application class with conditional `UncaughtExceptionHandler` on the main thread. Only activates when `generate_crash_reports` preference is `true`. Captures exception type, message, truncated stack trace (2000 chars), device model, Android version, and app version. Saves report to SharedPreferences and launches `CrashReportActivity` via Intent with `NEW_TASK` + `CLEAR_TASK` flags. |
| `CrashReportActivity.kt` | 171 | Compose-based activity that parses and displays the crash report in structured sections: exception (error-colored headline), message, monospace stack trace in a surface-variant card, device info. Two action buttons: "Restart App" (relaunches MainActivity) and "Close App" (terminates via `finishAndRemoveTask()`). Uses separate `taskAffinity` for isolation from the main app task. Falls back to generic error message if crash report extra is missing. |

#### Modified Files

| File | Lines | Changes |
|---|---|---|
| `AppDataStore.kt` | +6 | Added `KEY_GENERATE_CRASH_REPORTS` constant, `_generateCrashReports` StateFlow, `generateCrashReports` exposed property, `getGenerateCrashReports()`, `setGenerateCrashReports()`, `loadGenerateCrashReports()` (default `false`) |
| `SettingsScreen.kt` | +10 | Added `generateCrashReports` and `onGenerateCrashReportsChange` parameters, new `ListItem` with switch and supporting text "Show crash details when the app crashes" |
| `MainActivity.kt` | +9 | Added `_generateCrashReports`/`generateCrashReports`/`setGenerateCrashReports()` to AppViewModel, collection in Compose body, load in `LaunchedEffect`, pass-through to `SettingsScreen` |
| `AndroidManifest.xml` | +7 | Registered `.CrashApplication` as application class, declared `.CrashReportActivity` with `exported=false`, NoActionBar theme, and separate `taskAffinity="com.le0xff.plauncher.crash"` |

#### Key Implementation Details

**Conditional handler**: `CrashApplication.onCreate()` reads the `generate_crash_reports` boolean from SharedPreferences (`"plauncher"` prefs). If `false`, no handler is installed and Android's default crash behavior is preserved. If `true`, a custom `UncaughtExceptionHandler` is installed on the main thread.

**Crash report format**: The handler builds a structured multi-line string with labeled sections (`Exception:`, `Message:`, `Stack Trace:`, `Device:`, `Android:`, `App Version:`), separated by blank lines. Stack trace is truncated to 2000 characters. App version is read from `PackageManager` with try-catch fallback to `"unknown"`.

**Process lifecycle**: The handler calls `startActivity()` for `CrashReportActivity` within a try-catch (the process may already be shutting down), then delegates to the original handler to let Android terminate the crashed process. `CrashReportActivity` runs in a separate task (`taskAffinity`) so it survives the original process death.

**Report parsing**: `CrashReportActivity` parses the report string line-by-line, extracting sections by prefix matching (`Exception:`, `Message:`, `Stack Trace:`, `Device:`). Stack trace lines are collected between the "Stack Trace:" header and the "Device:" section. Device info lines are collected from "Device:" onward.

**UI layout**: `Scaffold` with `TopAppBar` ("pLauncher has crashed"), scrollable `Column` body with styled sections, `Divider` separators, monospace stack trace in a `Card` with `surfaceVariant` background, and a bottom `Row` with two equal-width buttons.

**Persistence**: Crash reports are saved to SharedPreferences (`"last_crash_report"` key) for potential future log inspection. The toggle preference uses the same prefs file as other settings (`"plauncher"`, key `"generate_crash_reports"`).

#### Architecture

```
[User enables toggle in Settings]
        |
        v
[AppDataStore saves to SharedPreferences]
        |
        v (next app launch)
[CrashApplication.onCreate() reads preference]
        |
        v (if true)
[UncaughtExceptionHandler installed on main thread]
        |
        v (on crash)
[Handler builds report string, saves to prefs, launches CrashReportActivity]
        |
        v
[CrashReportActivity parses and displays report]
        |
  +-----+-----+
  |           |
[Restart]   [Close]
  |           |
[MainActivity] [Terminate]
```

#### Code Statistics

| Component | Files | Lines |
|---|---|---|
| Watch App (C) | 12 | 519 (unchanged) |
| Android App (Kotlin) | 10 | 831 (598 existing + 233 new/changed) |
| **Total** | **22** | **1350** |

#### Build Status

- Watch app: `pebble build` — compiles cleanly for `basalt` and `emery` (unchanged)
- Android app: `./gradlew --no-daemon assembleDebug` — BUILD SUCCESSFUL

---

## #4 — Background Communication: launch apps from Pebble when companion is in background

### Overview

Implemented the ability to launch smartphone applications from the Pebble watch even when the pLauncher companion app is in background, minimized, or not visible. Previously, launches only worked when pLauncher was in the foreground. Includes an iterative fix for permission state refresh after returning from system settings.

### Problem Analysis

The original architecture relied on `LauncherActivity` (started from `CrashApplication.onCreate()`) with a code-registered `BroadcastReceiver` to intercept `LAUNCH_APP` broadcasts from `PebbleListenerService`. This failed in background because:

1. **Activity destruction**: When `MainActivity` went to background, `LauncherActivity` could be destroyed by the system, taking its registered receiver with it.
2. **Broadcast delivery blocked**: Android 13 blocks delivery of custom broadcasts to manifest-registered receivers when the app is in background (`BroadcastQueue: Background execution not allowed`).
3. **`startActivity` from background**: Android prevents background services from calling `startActivity()` without `SYSTEM_ALERT_WINDOW` (Draw Over Other Apps) permission.

### Solution Architecture

The solution replaces the broadcast-based approach with a direct service-to-activity launch chain, backed by foreground service and special permissions:

```
[Pebble watch sends Launch App]
        |
        v
[PebbleListenerService.onMessageReceived() — foreground service, always alive]
        |
        v (direct startActivity, no broadcast)
[LaunchActivity — transparent, singleInstance, no UI]
        |
        v (startActivity for target app)
[Target app launches]
        |
        v (immediate finish)
[LaunchActivity destroyed, leaves no trace]
```

### Android Companion App (`apk/`) — ~261 lines Kotlin, 2 new source files

#### New Files

| File | Lines | Purpose |
|---|---|---|
| `PebbleListenerService` (upgraded) | 163 (+80) | Converted from plain `BasePebbleListenerService` to **foreground service**: notification channel, `startForeground()` with persistent notification (status: "Waiting for Pebble..." / "Connected" / "Disconnected — waiting..."), `PARTIAL_WAKE_LOCK` to prevent CPU sleep during watch sessions, notification updates on connect/disconnect/launch |
| `LaunchActivity.kt` | 17 | Transparent `singleInstance` `ComponentActivity` that receives `package_name`, resolves the launch intent via `PackageManager`, starts the target app, then immediately calls `finish()`. Uses `Theme.Translucent.NoTitleBar`, separate `taskAffinity`, `excludeFromRecents=true`. No UI rendered. |
| `BootReceiver.kt` | 15 | Manifest-registered `BroadcastReceiver` for `BOOT_COMPLETED`. Restarts `PebbleListenerService` via `ContextCompat.startForegroundService()` after device reboot, ensuring the service is available before the Pebble app re-binds. |

#### Modified Files

| File | Lines | Changes |
|---|---|---|
| `AndroidManifest.xml` | +21 | Added 6 permissions (`WAKE_LOCK`, `FOREGROUND_SERVICE`, `POST_NOTIFICATIONS`, `SYSTEM_ALERT_WINDOW`, `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`, `RECEIVE_BOOT_COMPLETED`). Registered `LaunchActivity` (transparent, singleInstance, separate taskAffinity). Registered `BootReceiver` with `BOOT_COMPLETED` filter. Added `foregroundServiceType="dataSync"` to service. |
| `PebbleListenerService.kt` | +80 | Added foreground service infrastructure: notification channel creation, `startForeground()` in `onCreate()`, `stopForeground()` in `onDestroy()`, `PARTIAL_WAKE_LOCK` management (acquire on connect, release on disconnect), notification status updates. Changed `handleLaunchApp()` to start `LaunchActivity` directly instead of sending a broadcast. |
| `MainActivity.kt` | +69 | Added permission state tracking with `resumeCounter` pattern in `AppViewModel` for reactive recomposition. Added startup `AlertDialog` prompting user to grant special permissions. Added `onResume()` to trigger permission re-check after returning from settings. Added `try-catch` around `senderHelper.close()` in `onDestroy()`. Removed `LauncherActivity` startup from app lifecycle. |
| `SettingsScreen.kt` | +74 | Added "Background Launch Permissions" section with two `ListItem` entries: "Draw Over Other Apps" and "Ignore Battery Optimizations", each showing "Granted" status or "Grant" button that opens the corresponding system settings. Added `checkCanDrawOverlays()` and `checkIgnoringBatteryOptimizations()` helper functions. Permission states passed as parameters from `MainActivity` for reactive updates. |
| `CrashApplication.kt` | -2 | Removed `LauncherActivity` startup code (no longer needed). |

#### Removed Files

| File | Reason |
|---|---|
| `LauncherActivity.kt` (original) | Initially replaced by a manifest-registered `LaunchReceiver`, then fully replaced by direct service-to-`LaunchActivity` approach after discovering Android blocks broadcast delivery in background |

#### Key Implementation Details

**Foreground Service**: `PebbleListenerService` now calls `startForeground()` with a persistent notification in `onCreate()`. This serves two purposes: (1) keeps the service alive even when the companion app is not in the foreground (Android won't kill foreground services), and (2) provides the user with a visible indicator of Pebble connection status. The notification has `PRIORITY_LOW`, no sound/vibration, and taps to open `MainActivity`.

**Wake Lock**: A `PARTIAL_WAKE_LOCK` prevents the CPU from sleeping while the watch is connected. Acquired when `handleWatchWelcome()` fires (watch connects), released in `onAppClosed()` and `onDestroy()`. Uses `setReferenceCounted(false)` for simple acquire/release semantics.

**Transparent Launch Activity**: `LaunchActivity` uses `singleInstance` launch mode with a unique `taskAffinity` so it doesn't interfere with either the pLauncher task or the target app's task. It's themed `Theme.Translucent.NoTitleBar` so no UI flicker is visible. After starting the target app, it calls `finish()` immediately. The `SYSTEM_ALERT_WINDOW` permission classifies the app as a "special access utility", allowing `startActivity()` from the foreground service context.

**Permission State Management**: Permission states (`canDrawOverlays`, `ignoringBatteryOpt`) are managed in `MainActivity` using a `resumeCounter` pattern: `AppViewModel` holds a `MutableStateFlow<Int>` that increments on `onResume()`. The composable uses `remember(resumeCounter) { mutableStateOf(...) }` so the values re-evaluate each time the activity resumes (e.g., after returning from system settings). The states are passed as parameters to `SettingsScreen` for reactive updates.

**Startup Permission Dialog**: On first launch (or if permissions are missing), an `AlertDialog` appears explaining that "Draw Over Other Apps" and "Ignore Battery Optimizations" are needed for background launch functionality. The dialog has "Open Settings" (opens overlay permission settings) and "Dismiss" buttons. The dialog re-evaluates on each `onResume()`.

**Boot Recovery**: `BootReceiver` listens for `BOOT_COMPLETED` and restarts `PebbleListenerService` as a foreground service. This ensures the service is ready when the Pebble app re-establishes its binding after a device reboot. The PebbleKit2 binding mechanism then takes over normally.

#### Debugging Discovery

The initial implementation used a `BroadcastReceiver` (`LaunchReceiver`) registered in the manifest to catch `LAUNCH_APP` broadcasts. Testing revealed Android 13 blocks delivery of custom broadcasts to background apps (`BroadcastQueue: Background execution not allowed`). The fix was to have `PebbleListenerService` start `LaunchActivity` directly via `startActivity()`, bypassing the broadcast entirely. The foreground service status ensures the `startActivity()` call is allowed.

#### Permissions Added

| Permission | Purpose |
|---|---|
| `SYSTEM_ALERT_WINDOW` | Classifies app as special access utility, allows `startActivity()` from foreground service |
| `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` | Prevents aggressive service killing by battery optimization |
| `RECEIVE_BOOT_COMPLETED` | Allows `BootReceiver` to restart service after reboot |
| `WAKE_LOCK` | Allows partial wake lock to keep CPU awake during watch sessions |
| `FOREGROUND_SERVICE` | Required for foreground service on Android 8+ |
| `POST_NOTIFICATIONS` | Required for foreground service notification on Android 13+ |

#### Code Statistics

| Component | Files | Lines |
|---|---|---|
| Watch App (C) | 12 | 519 (unchanged) |
| Android App (Kotlin) | 12 | 1112 (831 existing + 281 new/changed) |
| **Total** | **24** | **1631** |

#### Build Status

- Watch app: `pebble build` — compiles cleanly for `basalt` and `emery` (unchanged)
- Android app: `./gradlew --no-daemon assembleDebug` — BUILD SUCCESSFUL

---

## #5 — Watch-Companion Sync Fix: app list refresh, PebbleKit2 crash, data store consistency

### Overview

Fixed three interrelated issues that prevented the watch app from showing or launching apps correctly on first use:

1. **Stale app list on watch**: The watch only requested the app list at initial `init()`. If the user opened the watch app before configuring the companion, or navigated away and back, the watch never refreshed the list.
2. **PebbleKit2 crash on `close()`**: `DefaultPebbleSender.close()` is not idempotent — calling it after `onBindingDied()` already unbound the service threw `IllegalArgumentException: Service not registered`, crashing the companion app on teardown.
3. **Data store inconsistency between components**: `MainActivity` and `PebbleListenerService` each had their own `AppDataStore` instance with separate `StateFlow` caches. When `MainActivity` saved apps, the service's cache remained stale, causing `handleLaunchApp()` to look up indices in an empty list.

### Root Cause Analysis

**Race condition in app list delivery**: The original `onAppOpened()` callback in `PebbleListenerService` proactively sent `sendWelcome()` + `sendAppList()`. Simultaneously, the watch's `init()` also sent `send_watch_welcome()` (packet type 0), triggering `handleWatchWelcome()` which also sent the list. Two overlapping triggers caused the watch to receive duplicate or interleaved app list messages, corrupting the local state.

**Missing refresh on app return**: The watch's `send_watch_welcome()` was only called in `init()` (first launch). On Re-Pebble, when the user navigates away and returns to the app, `main()`/`init()` are not re-called. Without a refresh trigger, the watch kept showing stale data.

**PebbleKit2 double-close bug**: Decompile of `DefaultPebbleSender` revealed that `close()` unconditionally calls `context.unbindService(connection)` with no idempotency guard. When Android calls `onBindingDied()` (e.g., Pebble service crash), it triggers `close()` internally. A subsequent explicit `close()` from `onDestroy()` then throws.

**SharedPreferences async write**: `prefs.edit().apply()` writes asynchronously. When `PebbleListenerService` called `reloadApps()` to read prefs, the `MainActivity`'s `apply()` write might not have committed yet, resulting in stale reads.

### Watch App (`pbw/`) — 5 files modified

**`pLauncher.c`**: Removed `send_watch_welcome()` from `init()`. The watch no longer sends welcome at init — it now relies on the `.appear` handler which fires both on first push and on every return from background.

**`packets.c`** / **`packets.h`**: Added `request_app_list()` function with anti-duplicate and timeout logic:
- `s_waiting_for_response` flag prevents double sends during the same response cycle
- `s_response_timer` starts a 10-second timeout on each request
- `response_timeout_handler()` resets the flag if the companion doesn't respond, allowing retry on next `appeared`
- `handle_phone_welcome()` cancels the timer and resets the flag on successful response
- Exported `request_app_list()` in header for use by `window_main.c`

**`window_main.c`** / **`window_main.h`**: Added `window_appear` handler registered in `WindowHandlers.appear`. This fires when the window becomes visible — both on first push and on every return from background. The handler calls `request_app_list()` to fetch the latest app list. Also added `window_main_get_window()` accessor and `packets.h` include.

### Android Companion App (`apk/`) — 5 files modified

**`PebbleListenerService.kt`**:
- Removed proactive sending from `onAppOpened()` — now empty. The watch drives the sync via `request_app_list()` → `send_watch_welcome()` → `handleWatchWelcome()`.
- Added `BroadcastReceiver` for `ACTION_SEND_APP_LIST` — listens for broadcasts from `MainActivity` when the user saves apps. On receipt, reloads from prefs and sends the updated list to all watches.
- Added `reloadApps()` calls before reading `apps?.value` in both `handleWatchWelcome()` and `handleLaunchApp()` — ensures the service always reads the latest data from SharedPreferences, not a stale cache.
- Registered/unregistered the broadcast receiver in `onCreate()`/`onDestroy()`.

**`MainActivity.kt`**: When the user confirms app selection, now performs dual delivery:
1. Direct send via its own `PebbleSenderHelper.sendAppList()` — reaches the watch immediately regardless of service state
2. Broadcast to `PebbleListenerService` — if the service is running, it also sends the list using its stable connection

**`PebbleSenderHelper.kt`**: Wrapped `close()` in try-catch for `IllegalArgumentException` to handle PebbleKit2's non-idempotent `DefaultPebbleSender.close()`.

**`data/AppDataStore.kt`**:
- Changed `saveApps()` from `apply()` to `commit()` — ensures synchronous write to SharedPreferences so `reloadApps()` in other components reads committed data
- Added `reloadApps()` method — re-reads apps from prefs and updates the `StateFlow`

**`AndroidManifest.xml`**: Added intent-filter for `com.le0xff.plauncher.SEND_APP_LIST` action on `PebbleListenerService`.

### New Communication Flow

```
[Watch app opens / returns from background]
        |
        v
[window.appear → request_app_list() → sends WatchWelcome (type 0)]
        |
        v
[PebbleListenerService.handleWatchWelcome()]
        |
        v (reloadApps → read latest from prefs)
[Send PhoneWelcome (type 10) + AppList (type 11) to watch]
        |
        v
[Watch receives list → displays apps → timer cancelled, flag reset]
```

```
[User saves apps in MainActivity]
        |
        +──→ [Direct sendAppList via MainActivity's PebbleSenderHelper] ──→ Watch receives list
        |
        +──→ [Broadcast ACTION_SEND_APP_LIST] ──→ PebbleListenerService receives → reloadApps → sendAppList to watch
```

```
[User presses SELECT on watch]
        |
        v
[Watch sends Launch App (type 1) with index]
        |
        v
[PebbleListenerService.handleLaunchApp()]
        |
        v (reloadApps → read latest from prefs → commit() ensures data is current)
[Look up app by index → start LaunchActivity → target app launches]
```

### Build Status

- Watch app: `pebble build` — compiles cleanly for `basalt` and `emery`
- Android app: `./gradlew --no-daemon assembleDebug` — BUILD SUCCESSFUL