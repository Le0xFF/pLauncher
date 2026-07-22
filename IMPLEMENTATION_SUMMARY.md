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