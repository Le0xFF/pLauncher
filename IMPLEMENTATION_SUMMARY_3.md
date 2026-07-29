# Implementation summary

## #11 — Home Screen: Rename Button with Custom Name Dialog

### Overview

Added a rename button to each app entry on the Home screen (`AppScreen.kt`). Tapping the button opens a `AlertDialog` with a text field allowing the user to set a custom display name for the app. The custom name is stored in `LaunchApp.displayName` and transmitted to the Pebble watch via the existing AppMessage protocol (key 4). The dialog supports three behaviors: saving a custom name, resetting to the app's original name from `PackageManager`, and cancelling without changes.

### Analysis

- **Previous state**: Each app entry on the Home screen showed icon, display name, package name, and a Delete button. The display name was set automatically from the app's label when added via the picker dialog and could not be modified afterward.
- **Model**: `LaunchApp` already contains `displayName` which is sent to the watch (key `4`, CStr, max 32 characters). The model and persistence layer (`AppDataStore`) required no changes — the `displayName` field simply takes on a user-provided value instead of the system label.
- **Original name retrieval**: The app's original system name is not stored in `LaunchApp`. It must be retrieved dynamically from `PackageManager` using `packageName` at dialog open time. This ensures the "Default" button always resets to the current system label, even if the app was updated.
- **Architecture**: Follows the existing pattern used for `removeAppTarget`: state in `AppViewModel`, callback passed to `AppScreen`, action handled in `MainActivity`. The Pebble sync pattern (`senderHelper.sendAppList` + broadcast) mirrors the remove and picker confirm flows.
- **UI**: Jetpack Compose Material3 `AlertDialog` with `OutlinedTextField`, placeholder for original name, and three action buttons (Cancel, Default, Save).

### Android Companion App (`apk/`) — ~80 lines changed across 3 files

#### Modified Files

| File | Changes |
|---|---|
| `res/values/strings.xml` | Added `appscreen_rename` ("Rename"), `rename_dialog_title` ("Rename app"), `rename_placeholder` ("App original name"), `rename_button_save` ("Save"), `rename_button_reset` ("Default"). |
| `MainActivity.kt` | Added `renameAppTarget` state (`MutableStateFlow<LaunchApp?>`) and `setRenameAppTarget()` to `AppViewModel`. Wired `onRenameApp` callback to `AppScreen`. Added `AlertDialog` with `OutlinedTextField`, original name lookup via `PackageManager`, and three-button layout (Cancel, Default, Save) with full save/sync logic. |
| `ui/AppScreen.kt` | Added `onRenameApp` parameter. Added `IconButton` with `Icons.Filled.Edit` before the Delete button, wrapped both buttons in a `Row` with `padding(start = 8.dp)`. Added import for `Icons.Filled.Edit`. |

#### Key Implementation Details

**ViewModel state**: Added `renameAppTarget` as `MutableStateFlow<LaunchApp?>` (nullable). When `null`, no dialog is shown. When set to a `LaunchApp`, the rename dialog appears. Reset to `null` on Save, Default, or Cancel.

**Original name lookup**: On dialog open, the original system name is retrieved using `PackageManager.getApplicationInfo(packageName, 0).loadLabel(pm)`. The result is cached via `remember(targetApp)` so it doesn't re-query on every recomposition. Falls back to `packageName` if the app is uninstalled (`NameNotFoundException`). The context is captured from `LocalContext.current` before the `remember` call (since `remember`'s factory lambda is not a composable function).

**Text field behavior**: The `OutlinedTextField` starts with content based on the current `displayName`:
- If `displayName` differs from `originalName`: the field is pre-filled with the current custom name, allowing the user to edit it directly.
- If `displayName` equals `originalName`: the field is empty, showing the original name as a faded placeholder (`onSurfaceVariant` with `alpha = 0.5f`).

**Placeholder styling**: The original name appears as the `placeholder` text with `MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)`, making it visibly lighter than normal text. This clearly distinguishes the default name from user-entered text.

**Three-button layout**: Uses `AlertDialog`'s `dismissButton` for Cancel and Default (in a `Row`), and `confirmButton` for Save:
- **Cancel**: Closes the dialog without changes.
- **Default**: Resets `displayName` to the original system name, saves to `AppDataStore`, updates ViewModel, syncs to Pebble, and closes the dialog.
- **Save**: If field is blank, uses the original name. If field has content, uses the entered text (truncated to 32 characters for protocol compliance). Saves, updates ViewModel, syncs to Pebble, and closes the dialog.

**Save logic** (on Save):
1. Determine final name: blank field → `originalName`, non-blank → `editName.take(32)` (truncation for Pebble protocol 32-char limit).
2. Map apps list, replacing the target app's `displayName` with the final name.
3. Save to `AppDataStore.saveApps()`.
4. Update ViewModel with `setApps()`.
5. Reset `renameAppTarget` to `null` (closes dialog).
6. Launch coroutine to send updated list to Pebble via `senderHelper.sendAppList()`.
7. Send broadcast `PebbleListenerService.ACTION_SEND_APP_LIST` to notify the running service.

This mirrors the exact sync pattern used in the remove confirmation and `AppPickerDialog` confirm flows.

**Default logic**: Same as Save but always uses `originalName` as the final name. This allows the user to quickly revert a custom name to the system label.

**Edit button**: `IconButton` with `Icons.Filled.Edit` (standard tint, not error-colored), placed in a `Row` before the Delete button. Both buttons use `56.dp` hit area with `28.dp` icon size, wrapped in a `Row` with `padding(start = 8.dp)` for spacing from the text column. The Delete button retains its `error` tint for visual distinction.

**Unchanged**: `LaunchApp` model, `AppDataStore` persistence format, `AppPickerDialog`, `SettingsScreen`, theme handling, permission dialogs, Pebble protocol. The `onAddApp` FAB, picker flow, and remove confirmation remain fully functional alongside the new rename feature.

#### Layout

```
┌─────────────────────────────────┐
│ [Search field]                  │
│                                 │
│  [icon] WhatsApp   [✎] [🗑]    │
│          com.whatsapp           │
│  ───────────────────────────    │
│  [icon] Google Maps  [✎] [🗑]  │
│          com.google.android...  │
│  ───────────────────────────    │
│  [icon] Spotify     [✎] [🗑]    │
│          com.spotify.mobile...  │
│                                 │
│                    [+ Add App]  │  ← FAB
└─────────────────────────────────┘
```

#### Rename Dialog

```
┌─────────────────────────────────┐
│ Rename app                      │
│                                 │
│ ┌───────────────────────────┐   │
│ │ My Custom Name            │   │  ← TextField (pre-filled if custom)
│ └───────────────────────────┘   │
│                                 │
│  [Cancel] [Default]     [Save]  │
└─────────────────────────────────┘
```

When the field is empty, the original name appears as a faded placeholder:
```
┌─────────────────────────────────┐
│ Rename app                      │
│                                 │
│ ┌───────────────────────────┐   │
│ │ WhatsApp (faded)          │   │  ← Placeholder (alpha 0.5)
│ └───────────────────────────┘   │
│                                 │
│  [Cancel] [Default]     [Save]  │
└─────────────────────────────────┘
```

#### Button Behavior Matrix

| Action | Field state | Result | Dialog |
|--------|------------|--------|--------|
| Save | Blank | Saves original name | Closes |
| Save | Custom text | Saves text (truncated to 32 chars) | Closes |
| Default | Any | Saves original name | Closes |
| Cancel | Any | No change | Closes |

#### Code Statistics

| Component | Files | Lines changed |
|---|---|---|
| Watch App (C) | 0 | 0 (unchanged) |
| Android App (Kotlin) | 3 | ~80 (strings added, ViewModel state + dialog in MainActivity, edit button + button row in AppScreen) |
| **Total** | **3** | **~80** |

#### Build Status

- Watch app: `pebble build` — compiles cleanly for `basalt` and `emery` (unchanged)
- Android app: `./gradlew assembleDebug` — BUILD SUCCESSFUL

---

## #12 — Home Screen: Drag-to-Reorder with Live Reorder

### Overview

Added the ability to manually reorder apps in the Home screen (`AppScreen.kt`) of the Android companion app. Each list item has a "drag handle" icon (six-dot `DragIndicator`) on the far left. A long press on this handle activates a vertical drag. As the user drags past adjacent items, the list reorders in real-time (live reorder on cross). The dragged item is visually elevated with shadow, background, and translation. When the drag ends, the new order is persisted to `SharedPreferences`, synced to the Pebble watch via `PebbleSenderHelper`, and broadcast to `PebbleListenerService`. The drag handle is hidden when a search filter is active.

### Analysis

- **Previous state**: Each app entry on the Home screen had icon, display name, package name, Edit and Delete buttons. The order was determined by the position in `List<LaunchApp>` and could only be changed via the picker dialog. No drag-and-drop was available.
- **Model**: `LaunchApp` with `packageName` and `displayName`. Order is determined by the position in `List<LaunchApp>`. The `packageName` is the unique key (used in `items()`).
- **Persistence**: `AppDataStore` persists the list in SharedPreferences (`KEY_APPS`) as newline-separated strings. Order is preserved.
- **Sync**: `PebbleSenderHelper` sends the list to the watch using indices (`apps.indices`). Order is critical.
- **Architecture**: Follows the existing pattern: ViewModel exposes pure `reorderApp(fromIndex, toIndex): List<LaunchApp>` that returns the reordered list without modifying internal state. `MainActivity` handles persistence and Pebble sync atomically.
- **UI**: Jetpack Compose `Modifier.draggable` with `startDragImmediately = false` (long press required). Live reorder on cross: as the drag crosses one full item height, the list immediately reorders. `Modifier.graphicsLayer` with `shadowElevation` and `translationY` promotes the dragged item to a separate GPU compositing layer that renders above siblings.

### Android Companion App (`apk/`) — ~50 lines changed across 3 files

#### Modified Files

| File | Changes |
|---|---|
| `res/values/strings.xml` | Added `appscreen_drag_handle` ("Reorder") for the drag handle content description. |
| `MainActivity.kt` | Added `reorderApp(fromIndex: Int, toIndex: Int): List<LaunchApp>` pure function to `AppViewModel`. Wired `onReorderApp` callback in `AppScreen` call: computes reordered list, updates ViewModel, persists to DataStore, sends to Pebble, broadcasts to PebbleListenerService. |
| `ui/AppScreen.kt` | Added `onReorderApp` parameter. Added drag handle `Box` with `Icons.Filled.DragIndicator` (visible only when `searchQuery.isBlank()`). Implemented drag state (`draggedAppPackageName`, `dragOffsetY`) at the `Column` scope. Applied `Modifier.draggable` to the drag handle with live reorder on cross logic in `onDelta`. Applied `Modifier.graphicsLayer` with shadow, clip, shape, and `translationY` to the dragged row for elevated rendering. Applied `Modifier.background(surfaceContainer)` for the dragged item background. Added item height measurement via `onGloballyPositioned`. |

#### Key Implementation Details

**ViewModel pure function**: `reorderApp(fromIndex, toIndex)` returns a new list with the item moved from `fromIndex` to `toIndex`. Handles edge cases: equal indices, out-of-bounds indices. Does not modify `_apps.value` — follows the same pattern as remove/rename where the caller manages state updates.

**Drag handle**: A `Box` of `56.dp` containing `Icons.Filled.DragIndicator` (`28.dp`), rendered with `onSurfaceVariant` tint. Only visible when `searchQuery.isBlank()` — hidden during active search to avoid meaningless reorders on filtered subsets.

**Drag activation**: `Modifier.draggable` with `startDragImmediately = false` on the drag handle `Box`. Requires a long press to start dragging, preventing accidental drags during normal scrolling. `MutableInteractionSource` is provided to avoid ripple interference with the `LazyColumn` scroll.

**Live reorder on cross**: In `onDelta`, `dragOffsetY` accumulates the drag delta. When `|dragOffsetY|` exceeds one full item height (`itemHeightPx`), the code calculates the target index (`fromIndex + itemsCrossed`), calls `onReorderApp` to swap the item in the list, and resets `dragOffsetY` to 0. The `LazyColumn` re-layouts with the new order immediately. This provides smooth, responsive reordering without complex animation state.

**Item height measurement**: Uses `Modifier.onGloballyPositioned` on the first rendered row to measure the actual row height in pixels. Falls back to `64.dp` (4dp padding + ~56dp content + 4dp padding) until measured. The measured value is stored in a `mutableStateOf` so all items use the same height for cross detection.

**Dragged item visual feedback**:
- `Modifier.graphicsLayer { shadowElevation = 8.dp.toPx(); clip = true; shape = RoundedCornerShape(8.dp); translationY = dragOffsetY }` — promotes to a separate GPU layer with shadow, clips to rounded corners, and translates at the compositor level (not layout level) so the item renders **above** siblings.
- `Modifier.background(surfaceContainer)` — uses `surfaceContainer` instead of `surfaceVariant` so the background is visible in all themes. In the AMOLED theme, `surfaceVariant` is pure black (same as background), making the dragged item invisible. `surfaceContainer` is `0xFF1A1A1A` in AMOLED, matching the navigation bar color.

**Drag lifecycle**:
- `onDragStarted`: Sets `draggedAppPackageName` to identify the dragged item across recompositions, resets `dragOffsetY` to 0.
- `onDelta`: Accumulates offset, triggers live reorder when crossing item boundaries.
- `onDragStopped`: Clears `draggedAppPackageName`, resets `dragOffsetY` to 0, ending the drag session.

**Sync on reorder**: Follows the exact pattern of remove/rename:
1. `viewModel.reorderApp(fromIndex, toIndex)` computes the new list.
2. `reordered !== apps` check avoids redundant operations.
3. `viewModel.setApps(reordered)` updates UI state.
4. `dataStore.saveApps(reordered)` persists to SharedPreferences.
5. `senderHelper.sendAppList(reordered, null)` sends to Pebble (async).
6. `sendBroadcast(PebbleListenerService.ACTION_SEND_APP_LIST)` notifies the running service.

**Compose API notes** (BOM 2025.02.00 / foundation 1.11.2):
- `rememberDraggableState` takes only `onDelta: (Float) -> Unit` (no `onDragStarted`/`onDragEnd` parameters).
- `Modifier.draggable` takes `state`, `orientation`, `startDragImmediately`, `interactionSource`, plus suspending `onDragStarted` and `onDragStopped: suspend CoroutineScope.(Float) -> Unit` (velocity as `Float`, not `DragEvent`).
- `DragEvent` class is internal in this version — cannot be imported directly.
- `LazyColumn` has no `animateItemPlacement` parameter in this version.
- `Modifier.graphicsLayer` with lambda takes `GraphicsLayerScope` — use `translationY` (not `Modifier.offset`) to move at the compositor level for proper draw ordering.
- `Modifier.shadow()` is a layout modifier that does not change draw order — replaced with `graphicsLayer { shadowElevation = ... }`.

**Unchanged**: `LaunchApp` model, `AppDataStore` persistence format, `AppPickerDialog`, `SettingsScreen`, theme handling, permission dialogs, Pebble protocol, rename dialog, remove confirmation. All existing features remain fully functional.

#### Layout

```
┌─────────────────────────────────┐
│ [Search field]                  │
│                                 │
│ [::] [icon] TIDAL     [✎] [🗑] │  ← drag handle (::) + app row
│        com.aspiro.tidal         │
│  ───────────────────────────    │
│ [::] [icon] Block Drop  [✎] [🗑]│
│        com.blockdrop.game       │
│  ───────────────────────────    │
│ [::] [icon] Breakout 71 [✎] [🗑]│
│        me.lecaro.breakout       │
│                                 │
│                    [+ Add App]  │  ← FAB
└─────────────────────────────────┘
```

During drag (item elevated, translated, with shadow):
```
┌─────────────────────────────────┐
│ [Search field]                  │
│                                 │
│ [::] [icon] Block Drop  [✎] [🗑]│  ← item moved down
│        com.blockdrop.game       │
│  ───────────────────────────    │
│                                 │  ← gap where TIDAL was
│                                 │
│ ┌───────────────────────────┐   │  ← elevated TIDAL (shadow + bg)
│ │ [::] TIDAL    [✎] [🗑]  │   │
│ │    com.aspiro.tidal      │   │
│ └───────────────────────────┘   │
│  ───────────────────────────    │
│ [::] [icon] Breakout 71 [✎] [🗑]│
│        me.lecaro.breakout       │
│                                 │
│                    [+ Add App]  │
└─────────────────────────────────┘
```

#### Code Statistics

| Component | Files | Lines changed |
|---|---|---|
| Watch App (C) | 0 | 0 (unchanged) |
| Android App (Kotlin) | 3 | ~50 (string added, ViewModel method + callback in MainActivity, drag logic + handle + visual feedback in AppScreen) |
| **Total** | **3** | **~50** |

#### Build Status

- Watch app: `pebble build` — compiles cleanly for `basalt` and `emery` (unchanged)
- Android app: `./gradlew assembleDebug` — BUILD SUCCESSFUL

---

## #13 — Settings Screen Restructure + Crash Report Improvements

### Overview

Restructured the Settings screen of the Android companion app and improved the crash report system. The Settings screen was reorganized: the theme selector was moved from its dedicated "Themes" section into "General" (above "Show system apps"), the "Themes" accordion was removed, and a new "Debug" accordion was created below "Permissions". "Generate crash reports" was moved from "General" into "Debug". A "Crash" button was added in the Debug section to trigger a test crash for verifying crash report functionality.

Additionally, the crash report system received several critical fixes and improvements: the crash handler was fixed to catch crashes on all threads (not just the startup thread), the crash report screen was enhanced with device info displayed inside the stack trace card, a copy button was added to copy the stack trace to clipboard, and the handler was fixed to respect the switch state at crash time.

### Analysis

- **Previous state**: Settings screen had three accordion sections: General (Show system apps, Generate crash reports), Permissions (Draw Overlays, Ignore Battery), Themes (theme dropdown). The crash report screen showed exception, message, stack trace, and device info as separate sections.
- **Settings restructure**: The theme `ExposedDropdownMenu` was moved from the "Themes" accordion into "General" as the first item. The "Themes" accordion and its state variable were removed. A new "Debug" accordion was added with "Generate crash reports" switch and "Crash" button.
- **Crash handler not firing**: `CrashApplication` used `Thread.currentThread().uncaughtExceptionHandler` which only set the handler on the thread running `onCreate()`. Crashes from UI interactions occur on the main thread. Fixed by switching to `Thread.setDefaultUncaughtExceptionHandler()` which catches uncaught exceptions on all threads.
- **Android system crash dialog overriding**: `originalHandler?.uncaughtException(thread, ex)` delegated to Android's default handler, which shows the system "App has stopped" dialog and kills the process. Fixed by replacing with `Process.killProcess()` + `System.exit(2)`, letting `CrashReportActivity` be the sole crash screen.
- **Crash report firing when switch is off**: The handler checked the preference at startup but stayed installed even after the user disabled the switch. Fixed by re-checking the preference inside the handler at crash time.
- **Empty stack trace**: `buildCrashReport` used an indented multiline string template (`"""..."""` with `trimIndent()`). `trimIndent()` stripped leading whitespace from the template lines but NOT from the interpolated `$truncatedStack` (output of `ex.stackTraceToString()`). This caused the label lines to have leading spaces while stack trace lines had none. The parser's exact-match search for `"Stack Trace:"` failed because the actual line was `"            Stack Trace:"`. Fixed by using `buildString` with explicit `\n` separators, producing clean lines with no indentation.
- **`apply()` vs `commit()`**: Changed `prefs.edit().putString(...).apply()` to `.commit()` to ensure the crash report is persisted to disk before the process is killed.
- **Device info placement**: Moved device info inside the stack trace card, appearing at the top before the stack trace, separated by `"---"`. This consolidates all diagnostic info into one scrollable card.
- **Copy to clipboard**: Added a "Copy" button with `ContentCopy` icon aligned to the right of the "Stack Trace:" label. Uses `ClipboardManager` to copy the raw stack trace. Shows a confirmation text overlay at the bottom-left corner when copied.

### Android Companion App (`apk/`) — ~100 lines changed across 4 files

#### Modified Files

| File | Changes |
|---|---|
| `res/values/strings.xml` | Added `settings_section_debug` ("Debug"), `settings_crash_the_app` ("Crash the application"), `settings_crash_the_app_desc` ("Triggers a test crash to verify crash reports"), `settings_crash_button` ("Crash"), `crash_button_copy` ("Copy"), `crash_copied_snackbar` ("Copied to clipboard"). |
| `ui/SettingsScreen.kt` | Restructured accordions: moved theme dropdown into "General" (above Show system apps), removed "Themes" accordion and `themesExpanded` state, added "Debug" accordion with `debugExpanded` state, moved "Generate crash reports" switch into Debug, added "Crash" button with error-colored styling that throws `RuntimeException`. |
| `CrashApplication.kt` | Changed `Thread.currentThread().uncaughtExceptionHandler` to `Thread.setDefaultUncaughtExceptionHandler()`. Added preference re-check inside the handler. Replaced `originalHandler?.uncaughtException()` with `Process.killProcess()` + `System.exit(2)`. Changed `apply()` to `commit()`. Refactored `buildCrashReport` template from indented multiline string to `buildString` block. |
| `CrashReportActivity.kt` | Added `ClipboardManager` integration, copy button with `ContentCopy` icon in a `Row` with `Arrangement.SpaceBetween` alongside the "Stack Trace:" label. Moved device info inside the stack trace card (top, before `"---"` separator, then stack trace). Replaced snackbar with a `Text` overlay at `Alignment.BottomStart` inside an outer `Box`. |

#### Key Implementation Details

**Settings Screen Restructure**:

The `SettingsScreen` composable's parameter signature remains unchanged (same 8 parameters). Only the internal layout was reorganized:

- **General** (modified): Theme `ExposedDropdownMenu` as first item, `HorizontalDivider`, "Show system apps" switch.
- **Permissions** (unchanged): Draw Overlays and Ignore Battery Optimizations with Grant/Revoke buttons.
- **Debug** (new): "Generate crash reports" switch with description, `HorizontalDivider`, "Crash the application" with description and error-colored "Crash" button. The button is always visible regardless of switch state.

The `themesExpanded` state variable was removed. A new `debugExpanded` state variable was added.

**Crash Handler Architecture**:

The handler is installed at `Application.onCreate()` (only if the switch was ON at startup). At crash time, it re-checks the preference from `SharedPreferences`. If OFF, it terminates the process cleanly without showing `CrashReportActivity`. If ON, it builds the crash report, persists it with `.commit()`, starts `CrashReportActivity` with the report as an intent extra, then kills the process.

The `buildCrashReport` method uses `buildString` with explicit newlines, producing output like:
```
Exception: RuntimeException
Message: Test crash from settings

Stack Trace:
java.lang.RuntimeException: Test crash from settings
at com.le0xff.plauncher.ui.SettingsScreenKt...
at ...

Device: moto g52
Android: 13
App Version: 1.0
```

The parser in `CrashReportActivity` splits by lines and uses exact match (`it == labelStacktrace`) and prefix match (`it.startsWith(labelDevice)`) to locate sections. The clean output format ensures reliable parsing.

**Crash Report Screen Layout**:

```
┌─────────────────────────────────┐
│ pLauncher has crashed          │  ← TopAppBar
├─────────────────────────────────┤
│ Exception: RuntimeException    │  ← headlineSmall, error color
│ Message: Test crash from...    │  ← bodyLarge
│ ───────────────────────────    │  ← Divider
│                                │
│ Stack Trace:        [📋 Copy] │  ← Row with SpaceBetween
│                                │
│ ┌───────────────────────────┐  │  ← Card (surfaceVariant)
│ │ Device: moto g52          │  │  ← device info at top
│ │ Android: 13               │  │
│ │ App Version: 1.0          │  │
│ │ ---                       │  │  ← separator
│ │ java.lang.RuntimeExcept..│  │  ← stack trace (monospace)
│ │   at ...                  │  │
│ │   at ...                  │  │
│ └───────────────────────────┘  │
│                                │
│  [Restart App]   [Close App]  │
└─────────────────────────────────┘
```

**Copy Confirmation**:

The entire screen is wrapped in a `Box(modifier = Modifier.fillMaxSize())`. The main content (`Column` with `Scaffold`) fills the box. When `snackbarMessage` is non-null, a `Text` overlay appears at `Alignment.BottomStart` with `padding(16.dp)`, showing "Copied to clipboard" in `bodySmall` style with `onSurfaceVariant` color. This overlay sits on top of the button row area.

**Crash Button Behavior**:

The "Crash" button in the Debug section calls `throw RuntimeException("Test crash from settings")`. This throws an uncaught exception on the main thread, which is caught by `Thread.getDefaultUncaughtExceptionHandler()` (set in `CrashApplication`). The handler builds the report, starts `CrashReportActivity`, and terminates the process. The `CrashReportActivity` uses `taskAffinity="com.le0xff.plauncher.crash"` and `FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK` to appear in a separate task, ensuring it survives the process kill/restart cycle.

**Unchanged**: `LaunchApp` model, `AppDataStore` persistence, `AppPickerDialog`, theme handling (`Theme.kt`), permission dialogs, Pebble protocol, rename dialog, remove confirmation, drag-to-reorder, `MainActivity.kt` (no changes needed), `PebbleSenderHelper`, `PebbleListenerService`, `BootReceiver`. All existing features remain fully functional.

#### Code Statistics

| Component | Files | Lines changed |
|---|---|---|
| Watch App (C) | 0 | 0 (unchanged) |
| Android App (Kotlin) | 4 | ~100 (strings added, SettingsScreen restructured, CrashApplication handler fixed, CrashReportActivity enhanced) |
| **Total** | **4** | **~100** |

#### Build Status

- Watch app: `pebble build` — compiles cleanly for `basalt` and `emery` (unchanged)
- Android app: `./gradlew assembleDebug` — BUILD SUCCESSFUL