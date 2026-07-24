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