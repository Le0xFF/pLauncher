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

## #12 — Home Screen: Drag-to-Reorder with Reorderable Library

### Overview

Replaced the manual drag-and-drop implementation in the Home screen (`AppScreen.kt`) of the Android companion app with the [Reorderable](https://github.com/Calvin-LL/Reorderable) library (v3.1.0) by Calvin-LL. Each list item has a "drag handle" icon (six-dot `DragIndicator`) on the far left. A long press on this handle activates a vertical drag. The library handles reordering, visual feedback (elevation, background, translation), zIndex for proper draw ordering, and auto-scroll when dragging near screen edges. When the drag ends, the new order is persisted to `SharedPreferences`, synced to the Pebble watch via `PebbleSenderHelper`, and broadcast to `PebbleListenerService`. The drag handle is hidden and drag is disabled when a search filter is active.

### Analysis

- **Previous state**: Drag-and-drop was implemented manually using `Modifier.draggable` with `rememberDraggableState()`, `graphicsLayer { translationY }` for visual offset, and `onGloballyPositioned` for item height measurement. Two structural bugs existed: (1) the dragged item's background was invisible when dragging downward because `zIndex` was missing, causing the item to render **behind** the item it overlapped in the `LazyColumn` draw order; (2) dragging the first item upward triggered `LazyColumn`'s own scroll handler instead of the drag, because the gesture was consumed by the list's scroll before reaching the `draggable` modifier.
- **Root cause Bug 1 (background missing downward)**: Without `zIndex(1f)` applied as a `Modifier` before `graphicsLayer`, the dragged item has no elevated draw order. When translated downward (`translationY` positive), it overlaps the next item in the `LazyColumn`, but renders behind it because `LazyColumn` draws items sequentially. Dragging upward worked "by accident" because negative `translationY` moves into empty space above.
- **Root cause Bug 2 (list scrolls instead of item)**: `LazyColumn` has its own vertical scroll gesture handler that intercepts touch gestures. When dragging the first item upward, the gesture is consumed by the `LazyColumn` scroll rather than the `draggable` modifier. `LazyColumn` provides no mechanism to disable scroll during a drag of a specific item.
- **Solution chosen**: Replace the manual implementation entirely with the [Reorderable](https://github.com/Calvin-LL/Reorderable) library. This is the standard solution for drag-and-drop in `LazyColumn` in Jetpack Compose, used by high-profile projects including [Lawnchair](https://github.com/LawnchairLauncher/lawnchair), [Home Assistant](https://github.com/home-assistant/android), [ProtonVPN](https://github.com/ProtonVPN/android-app), [Pocket Casts](https://github.com/Automattic/pocket-casts-android), [Aniyomi](https://github.com/aniyomiorg/aniyomi), [Mihon](https://github.com/mihonapp/mihon), [Neo Launcher](https://github.com/NeoApplications/Neo-Launcher), and others.
- **Why Reorderable fixes both bugs**: (1) It automatically applies `zIndex(1f)` to the dragged item, ensuring correct draw order in all directions. (2) It manages scroll internally, performing auto-scroll when dragging near screen edges, rather than letting `LazyColumn` consume the gesture. It also provides `isDragging` for conditional styling and `Modifier.draggableHandle()` scoped to `ReorderableCollectionItemScope`.
- **Model**: `LaunchApp` with `packageName` and `displayName`. Order is determined by the position in `List<LaunchApp>`. The `packageName` is the unique key (used in `items()`).
- **Persistence**: `AppDataStore` persists the list in SharedPreferences (`KEY_APPS`) as newline-separated strings. Order is preserved.
- **Sync**: `PebbleSenderHelper` sends the list to the watch using indices (`apps.indices`). Order is critical.
- **Architecture**: Unchanged from before: ViewModel exposes pure `reorderApp(fromIndex, toIndex): List<LaunchApp>` that returns the reordered list without modifying internal state. `MainActivity` handles persistence and Pebble sync atomically. The `onReorderApp` callback signature remains `(fromIndex: Int, toIndex: Int) -> Unit`.
- **UI**: Reorderable library's `ReorderableItem` wraps each list item. `rememberReorderableLazyListState` connects the `LazyListState` with the reorder callback. `Modifier.draggableHandle()` is applied to the drag handle `Box` within the `ReorderableCollectionItemScope`. The `enabled` parameter on `ReorderableItem` disables drag when `searchQuery` is not blank.

### Android Companion App (`apk/`) — ~40 lines changed across 3 files

#### Modified Files

| File | Changes |
|---|---|
| `gradle/libs.versions.toml` | Added `reorderable = "3.1.0"` in `[versions]` and `reorderable = { module = "sh.calvin.reorderable:reorderable", version.ref = "reorderable" }` in `[libraries]`. |
| `app/build.gradle.kts` | Added `implementation(libs.reorderable)` in `dependencies`. |
| `ui/AppScreen.kt` | Replaced manual drag implementation with Reorderable. Removed manual drag state (`measuredItemHeightPx`, `draggedAppPackageName`, `dragOffsetY`). Removed old imports (`Orientation`, `draggable`, `rememberDraggableState`, `MutableInteractionSource`, `GraphicsLayerScope`, `onGloballyPositioned`, `clip`). Added imports for `ReorderableItem`, `rememberReorderableLazyListState`. Added `rememberReorderableLazyListState` with `onReorderApp` callback. Wrapped items with `ReorderableItem(state, key, enabled)` where `enabled` is `searchQuery.isBlank()`. Used `scope.draggableHandle()` for the drag handle. Used `isDragging` for conditional background. Placed Divider before `ReorderableItem` (outside draggable scope). |

#### Key Implementation Details

**Reorderable dependency**: Version 3.1.0 of `sh.calvin.reorderable:reorderable`, added via Gradle version catalog. Compatible with Compose BOM 2025.02.00, Kotlin 2.3.0, and AGP 8.9.1. Verified via [GitHub README](https://github.com/Calvin-LL/Reorderable) and [Maven Central](https://central.sonatype.com/artifact/sh.calvin.reorderable/reorderable/3.1.0).

**Reorderable state**: `rememberReorderableLazyListState(lazyListState)` takes the existing `LazyListState` and a callback `(LazyListItemInfo, LazyListItemInfo) -> Unit` that receives `from` and `to` item info. The callback calls `onReorderApp(from.index, to.index)`, passing the `LazyColumn` indices. Since drag is disabled during search (`enabled = false`), the indices always correspond to the full `apps` list.

**ReorderableItem API** (v3.1.0): The function signature is `ReorderableItem(state: ReorderableLazyListState, key: Any, modifier: Modifier = Modifier, enabled: Boolean = true, contentModifier: Modifier = Modifier, content: @Composable ReorderableCollectionItemScope.(Boolean) -> Unit)`. Key parameters:
- `state`: The `ReorderableLazyListState` from `rememberReorderableLazyListState`.
- `key`: The unique item key (`packageName`), matching the `items()` key.
- `enabled`: When `false`, the item cannot be dragged. Used to disable drag during search.
- `content`: A lambda with `ReorderableCollectionItemScope` as receiver and `Boolean` (`isDragging`) as parameter.

**Drag handle**: `Modifier.draggableHandle()` is called as `scope.draggableHandle()` within the `ReorderableCollectionItemScope` receiver of the content lambda. The `ReorderableCollectionItemScope` interface provides `draggableHandle(modifier: Modifier, enabled: Boolean, ...): Modifier` that returns a modified `Modifier`. Applied to the drag handle `Box` using `.then(scope.draggableHandle(Modifier))`. Visible only when `searchQuery.isBlank()`.

**Conditional background**: The `isDragging` boolean parameter is used to conditionally apply `Modifier.background(surfaceContainer, shape = RoundedCornerShape(8.dp))` to the item's `Row`. This ensures the background is visible in all themes, including AMOLED where `surfaceContainer` is `0xFF1A1A1A`.

**Divider placement**: The `Divider` is placed **before** the `ReorderableItem` call within the `items()` lambda, using `filtered.indexOf(app) > 0` as the condition. This keeps the divider outside the draggable item, avoiding layout issues during drag.

**Search mode handling**: When `searchQuery` is not blank, `isDragEnabled` is `false`, which sets `enabled = false` on every `ReorderableItem`. This prevents drag gestures on filtered subsets where indices don't correspond to the `apps` list. The drag handle `Box` is also hidden in this mode.

**ViewModel pure function**: Unchanged. `reorderApp(fromIndex, toIndex)` returns a new list with the item moved from `fromIndex` to `toIndex`.

**Sync on reorder**: Unchanged. Follows the exact pattern of remove/rename:
1. `viewModel.reorderApp(fromIndex, toIndex)` computes the new list.
2. `reordered !== apps` check avoids redundant operations.
3. `viewModel.setApps(reordered)` updates UI state.
4. `dataStore.saveApps(reordered)` persists to SharedPreferences.
5. `senderHelper.sendAppList(reordered, null)` sends to Pebble (async).
6. `sendBroadcast(PebbleListenerService.ACTION_SEND_APP_LIST)` notifies the running service.

**Reorderable library API discovery**: The actual API was determined by decompiling the compiled classes from the AAR (`classes.jar`) using `javap`, since the Compose multiplatform entry points differ from the commonMain source. Key findings:
- `ReorderableLazyListKt.ReorderableItem` takes `(LazyItemScope, ReorderableLazyListState, Any, Modifier, Boolean, Modifier, Function4<...>)` — the `enabled` parameter is the 5th positional parameter.
- `ReorderableCollectionItemScope.draggableHandle` takes `(Modifier, Boolean, MutableInteractionSource?, Function1<Offset>, Function0<Unit>, DragGestureDetector)` and returns `Modifier`.
- The library uses `Modifier.animateItem` internally for smooth item transition animations.

**Reorderable features inherited**:
- Automatic `zIndex(1f)` on dragged item (fixes Bug 1).
- Auto-scroll when dragging near screen edges (fixes Bug 2).
- Animated item transitions via `Modifier.animateItem`.
- Support for items of different sizes.
- Drag gesture with long press activation (default behavior).

**Unchanged**: `LaunchApp` model, `AppDataStore` persistence format, `AppPickerDialog`, `SettingsScreen`, theme handling, permission dialogs, Pebble protocol, rename dialog, remove confirmation, sort dropdown. All existing features remain fully functional.

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

During drag (item elevated with background, correct zIndex):
```
┌─────────────────────────────────┐
│ [Search field]                  │
│                                 │
│ [::] [icon] Block Drop  [✎] [🗑]│  ← item moved down
│        com.blockdrop.game       │
│  ───────────────────────────    │
│                                 │  ← gap where TIDAL was
│                                 │
│ ┌───────────────────────────┐   │  ← elevated TIDAL (bg visible)
│ │ [::] TIDAL    [✎] [🗑]  │   │  ← correct zIndex, above siblings
│ │    com.aspiro.tidal      │   │
│ └───────────────────────────┘   │
│  ───────────────────────────    │
│ [::] [icon] Breakout 71 [✎] [🗑]│
│        me.lecaro.breakout       │
│                                 │
│                    [+ Add App]  │
└─────────────────────────────────┘
```

#### Bugs Fixed

| Bug | Root cause | Fix |
|---|---|---|
| Background invisible dragging downward | Missing `zIndex` in manual `graphicsLayer`; item rendered behind overlapped sibling | Reorderable applies `zIndex(1f)` automatically |
| List scrolls instead of item reordering | `LazyColumn` scroll handler consumed drag gesture | Reorderable manages scroll internally with auto-scroll at edges |

#### Code Statistics

| Component | Files | Lines changed |
|---|---|---|
| Watch App (C) | 0 | 0 (unchanged) |
| Android App (Kotlin) | 3 | ~40 (version catalog + build.gradle updated, AppScreen rewritten with Reorderable) |
| **Total** | **3** | **~40** |

#### References

- [Reorderable GitHub](https://github.com/Calvin-LL/Reorderable) — Library source, README, demo app examples
- [Reorderable Maven Central](https://central.sonatype.com/artifact/sh.calvin.reorderable/reorderable/3.1.0) — Artifact metadata and version verification

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

---

## #14 — Home Screen: Material3 Search Bar + Alphabetical Sort

### Overview

Improved the Home screen search bar styling and added an alphabetical sort feature. The search field was changed from `OutlinedTextField` to Material3 `TextField` with a pill shape (`RoundedCornerShape(28.dp)`) and transparent indicator line. A sort button (`Icons.Filled.SortByAlpha`) was added to the right of the search field, opening a `DropdownMenu` with two options: "Alphabetical" (A-Z) and "Alphabetical (Z-A)". The sort reorders all apps by `displayName` (case-insensitive, ties broken by `packageName`), persists the new order to `SharedPreferences`, and syncs to the Pebble watch.

### Analysis

- **Previous state**: The search field used `OutlinedTextField` with `padding(16.dp)` on all sides, taking the full width of the screen. No automatic sort was available — the only way to reorder apps was manual drag-and-drop (#12) or the picker dialog.
- **Search bar styling**: Replaced `OutlinedTextField` with `TextField` (filled variant) for a more compact Material3 appearance. Applied `RoundedCornerShape(28.dp)` for a pill shape. Reduced padding from `16.dp` all around to `horizontal = 16.dp, vertical = 8.dp` to tighten the header area. Removed the default indicator line (horizontal underline) by setting all four indicator color states to `Color.Transparent` via `TextFieldDefaults.colors().copy(...)`.
- **Sort button**: Placed to the right of the search field in the same `Row`, using `IconButton` with `Icons.Filled.SortByAlpha` (`24.dp`). Opens a `DropdownMenu` with two items. Uses a wrapper `Box` to position the dropdown relative to the button.
- **Sort logic**: Pure function `sortApps(order: SortOrder): List<LaunchApp>` in the ViewModel. Sorts by `displayName.lowercase()` first, then `packageName` as tiebreaker. `SortOrder.Descending` reverses the sorted list. The `SortOrder` enum (`Ascending`, `Descending`) is defined in `LaunchApp.kt` alongside the `LaunchApp` data class.
- **Architecture**: Follows the same pattern as reorder: ViewModel exposes pure function, `MainActivity` handles persistence and sync. The callback `onSortApps: (SortOrder) -> Unit` is passed to `AppScreen`.

### Android Companion App (`apk/`) — ~60 lines changed across 4 files

#### Modified Files

| File | Changes |
|---|---|
| `res/values/strings.xml` | Added `appscreen_sort_menu` ("Sort"), `appscreen_sort_ascending` ("Alphabetical"), `appscreen_sort_descending` ("Alphabetical (Z-A)"). |
| `model/LaunchApp.kt` | Added `SortOrder` enum with `Ascending` and `Descending` values. |
| `MainActivity.kt` | Added `sortApps(order: SortOrder): List<LaunchApp>` pure function to `AppViewModel`. Wired `onSortApps` callback in `AppScreen` call: computes sorted list, updates ViewModel, persists to DataStore, sends to Pebble, broadcasts to PebbleListenerService. |
| `ui/AppScreen.kt` | Replaced `OutlinedTextField` with Material3 `TextField` in a `Row` with reduced padding. Added `shape = RoundedCornerShape(28.dp)` and `TextFieldDefaults.colors().copy(...)` with all indicator colors set to `Color.Transparent`. Added sort button (`IconButton` with `Icons.Filled.SortByAlpha`) in the same row. Added `DropdownMenu` with two `DropdownMenuItem` entries. Added `onSortApps: (SortOrder) -> Unit` parameter. Added imports for `Color`, `TextFieldDefaults`, `Icons.Filled.SortByAlpha`, `SortOrder`. |

#### Key Implementation Details

**Search bar redesign**:
- Changed from `OutlinedTextField` to `TextField` for a filled, compact appearance.
- `shape = RoundedCornerShape(28.dp)` creates a pill-shaped field.
- Padding reduced from full `16.dp` to a `Row` with `horizontal = 16.dp, vertical = 8.dp`, placing the search and sort button on the same line.
- `TextFieldDefaults.colors().copy(focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, disabledIndicatorColor = Color.Transparent, errorIndicatorColor = Color.Transparent)` removes the horizontal indicator line that `TextField` draws by default.
- `TextFieldDefaults.colors()` returns the default color palette; `.copy()` overrides only the indicator colors, preserving all other defaults (text color, cursor color, placeholder color, etc.).

**Sort button and menu**:
- `IconButton` with `Icons.Filled.SortByAlpha` (the "A-Z with arrow" icon), `48.dp` hit area, `24.dp` icon size.
- Wrapped in a `Box` to anchor the `DropdownMenu` to the button's position.
- `DropdownMenu` toggles via `showSortMenu` state. Dismisses on menu item selection or outside tap.
- Two menu items: "Alphabetical" calls `onSortApps(SortOrder.Ascending)`, "Alphabetical (Z-A)" calls `onSortApps(SortOrder.Descending)`.

**ViewModel sort function**:
```kotlin
fun sortApps(order: SortOrder): List<LaunchApp> {
    return _apps.value.sortedWith(
        compareBy<LaunchApp> { it.displayName.lowercase() }.thenBy { it.packageName }
    ).let { sorted ->
        if (order == SortOrder.Descending) sorted.reversed() else sorted
    }
}
```
- Primary sort: `displayName.lowercase()` (case-insensitive alphabetical).
- Secondary sort: `packageName` (deterministic tiebreaker for same-named apps).
- `SortOrder.Descending` reverses the entire sorted list.
- Pure function: returns new list, does not modify `_apps.value`.

**Sync on sort**: Follows the exact pattern of reorder/rename:
1. `viewModel.sortApps(order)` computes the sorted list.
2. `sorted !== apps` check avoids redundant operations.
3. `viewModel.setApps(sorted)` updates UI state.
4. `dataStore.saveApps(sorted)` persists to SharedPreferences.
5. `senderHelper.sendAppList(sorted, null)` sends to Pebble (async).
6. `sendBroadcast(PebbleListenerService.ACTION_SEND_APP_LIST)` notifies the running service.

**Compose API notes** (BOM 2025.02.00 / foundation 1.11.2):
- `TextFieldDefaults.colors()` returns a `TextFieldColors` with all defaults populated.
- `TextFieldColors` has no no-arg constructor — must use `TextFieldDefaults.colors().copy(...)` to override specific properties.
- `TextFieldDefaults.colors().copy()` accepts all 30+ color parameters with default values via Kotlin's `$default` mechanism, allowing partial overrides.

**Unchanged**: `LaunchApp` data class fields, `AppDataStore` persistence format, `AppPickerDialog`, `SettingsScreen`, theme handling, permission dialogs, Pebble protocol, rename dialog, remove confirmation, drag-to-reorder. All existing features remain fully functional.

#### Layout

```
┌─────────────────────────────────┐
│ ┌──────────────────────┐ [A-Z] │  ← pill TextField + sort button
│ │ Search...            │       │
│ └──────────────────────┘       │
│                                 │
│ [::] [icon] Block Drop  [✎] [🗑]│
│        com.blockdrop.game       │
│  ───────────────────────────    │
│ [::] [icon] Breakout 71 [✎] [🗑]│
│        me.lecaro.breakout       │
│  ───────────────────────────    │
│ [::] [icon] TIDAL     [✎] [🗑] │
│        com.aspiro.tidal         │
│                                 │
│                    [+ Add App]  │  ← FAB
└─────────────────────────────────┘
```

Sort dropdown:
```
┌────────────────────┐
│ Alphabetical       │
│ Alphabetical (Z-A) │
└────────────────────┘
```

#### Code Statistics

| Component | Files | Lines changed |
|---|---|---|
| Watch App (C) | 0 | 0 (unchanged) |
| Android App (Kotlin) | 4 | ~60 (strings added, SortOrder enum, ViewModel method + callback in MainActivity, search bar redesign + sort dropdown in AppScreen) |
| **Total** | **4** | **~60** |

#### Build Status

- Watch app: `pebble build` — compiles cleanly for `basalt` and `emery` (unchanged)
- Android app: `./gradlew assembleDebug` — BUILD SUCCESSFUL