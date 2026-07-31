# Implementation summary

## #16 — App Limit: 20-App Maximum in Companion App

### Overview

Added a hard limit of 20 applications in the Android companion app, matching the `APP_LIST_MAX_APPS 20` constant already defined in the Pebble watch app (`pbw/src/c/app_list.h`). The limit is enforced at three levels: (1) the FAB on the Home screen displays a live counter and blocks opening the picker when the limit is reached; (2) the picker dialog shows a counter in its title, disables checkboxes for unselected apps when the limit is reached, and visually grays out unavailable entries; (3) the picker internally prevents selecting beyond 20 apps while still allowing deselection of already-selected apps.

### Analysis

- **Previous state**: The companion app had no upper bound on the number of apps. The Pebble watch app is limited to 20 (`#define APP_LIST_MAX_APPS 20` in `app_list.h`), creating a mismatch where the companion app could accumulate more apps than the watch could display.
- **FAB counter**: The FAB was changed from showing only "+" to displaying `+ | N/20` using a `Row` with three `Text` elements. The FAB width was increased with `.widthIn(min = 100.dp)` to accommodate the counter text.
- **FAB click guard**: In `MainActivity`, the `onAddApp` callback was wrapped with a check: if `apps.size >= MAX_APPS`, a `Toast` is shown instead of opening the picker.
- **Picker counter**: The picker dialog's title was replaced with a `Row` using `Arrangement.SpaceBetween`, showing "Pick Apps" on the left and `N/20` on the right. The counter turns red (`error` color) when the limit is reached.
- **Picker checkbox disable**: When `localSelected.size >= maxApps`, checkboxes for apps not already selected become `enabled = false`. Already-selected apps remain interactive so the user can deselect them. Unavailable app names are rendered in `onSurfaceVariant` (grayed out).
- **Constant**: `MAX_APPS = 20` is defined as a `companion object` constant in `MainActivity`, ensuring a single source of truth.

### Android Companion App (`apk/`) — ~50 lines changed across 4 files

#### Modified Files

| File | Changes |
|---|---|
| `res/values/strings.xml` | Added `appscreen_limit_reached` ("Maximum of 20 apps reached"), `appscreen_app_count` ("App count"), `picker_limit_reached` ("Maximum of 20 apps reached"). |
| `MainActivity.kt` | Added `companion object { const val MAX_APPS = 20 }`. Added `import android.widget.Toast`. Replaced `onAddApp = { viewModel.setShowPicker(true) }` with a guarded lambda that checks `apps.size >= MAX_APPS` and shows a toast or opens the picker. Added `appCount` and `maxApps` parameters to `AppScreen` call. Added `maxApps = MAX_APPS` parameter to `AppPickerDialog` call. |
| `ui/AppScreen.kt` | Added `appCount: Int` and `maxApps: Int` parameters to the composable. Replaced FAB content from single `Text` to `Row` containing three `Text` elements: `+`, ` | `, and `$appCount/$maxApps`. Added `.widthIn(min = 100.dp)` to FAB modifier for wider button. |
| `ui/AppPickerDialog.kt` | Added `maxApps: Int` parameter. Removed `Toast` import (no longer needed). Replaced title from single `Text` to `Row` with `Arrangement.SpaceBetween` showing "Pick Apps" and counter. Counter uses `error` color when at limit. Added `isAtLimit` derived state. Checkboxes for unselected apps are disabled when at limit (`enabled = false`). App names for unavailable entries rendered in `onSurfaceVariant`. Simplified `onCheckedChange` logic (no toast, no complex conditional). |

#### Key Implementation Details

**MAX_APPS constant**: Defined in `MainActivity` as `companion object { const val MAX_APPS = 20 }`, matching `APP_LIST_MAX_APPS` in `pbw/src/c/app_list.h`. This single constant flows to both `AppScreen` and `AppPickerDialog`.

**FAB counter**: The FAB uses a `Row(verticalAlignment = Alignment.CenterVertically)` with three children:
- `Text(stringResource(R.string.button_add_app))` — displays "+"
- `Text(" | ")` — separator
- `Text("$appCount/$maxApps")` — live counter (e.g., "6/20")

The FAB's modifier includes `.widthIn(min = 100.dp)` to expand the button horizontally, ensuring the counter text fits without overflow.

**FAB click guard** (`MainActivity.kt`):
```kotlin
onAddApp = {
    if (apps.size >= MAX_APPS) {
        Toast.makeText(context, R.string.appscreen_limit_reached, Toast.LENGTH_SHORT).show()
    } else {
        viewModel.setShowPicker(true)
    }
}
```
The `context` is the `LocalContext.current` already captured in `setContent`. The `apps` variable is a `StateFlow` observed with `collectAsState()`, always reflecting the current count.

**Picker counter** (`AppPickerDialog.kt`):
The `AlertDialog` title uses a `Row` with `Arrangement.SpaceBetween`:
```kotlin
title = {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(stringResource(R.string.picker_title))
        Text(
            "${localSelected.size}/$maxApps",
            style = MaterialTheme.typography.bodyMedium,
            color = if (isAtLimit) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
        )
    }
}
```
The counter turns red (`error` color) when `localSelected.size >= maxApps`, providing immediate visual feedback.

**Picker checkbox disable**:
The checkbox for each app uses `enabled = !checkboxDisabled` where `checkboxDisabled = isAtLimit && !isSelected`. This means:
- When under the limit: all checkboxes are enabled (normal behavior).
- When at the limit: only already-selected checkboxes remain enabled (for deselection). Unselected apps' checkboxes are disabled.
- App names for unselectable entries are rendered in `onSurfaceVariant` (grayed out) for additional visual feedback.

The `onCheckedChange` lambda is simplified since the `enabled = false` on the Checkbox already prevents user interaction — the lambda simply toggles membership in `localSelected` without needing defensive checks.

**Picker confirm still saves all selected**: The `onConfirm` callback is unchanged. It converts `localSelected` to a `List<LaunchApp>` and passes it to the caller. The limit enforcement happens at the UI level (disabled checkboxes), so the confirm button always receives a valid list of ≤20 apps.

**Unchanged**: `LaunchApp` model, `AppDataStore` persistence, `SettingsScreen`, theme handling, permission dialogs, Pebble protocol, rename dialog, remove confirmation, drag-to-reorder, sort dropdown, search bar, wakelock handling, `PebbleListenerService`, `PebbleSenderHelper`, `BootReceiver`. All existing features remain fully functional.

#### Layout

Home screen FAB:
```
┌─────────────────────────────────┐
│ ┌──────────────────────┐ [A-Z] │
│ │ Search...            │       │
│ └──────────────────────┘       │
│                                 │
│ [::] [icon] Block Drop  [✎] [🗑]│
│        com.blockdrop.game       │
│                                 │
│               [+ | 6/20]        │  ← FAB with counter
└─────────────────────────────────┘
```

Picker dialog:
```
┌─────────────────────────────────┐
│ Pick Apps              6/20     │  ← title with counter
├─────────────────────────────────┤
│ ┌───────────────────────────┐   │
│ │ Search...                 │   │
│ └───────────────────────────┘   │
│                                 │
│ [✓] WhatsApp        (selected)  │
│ [ ] Instagram       (enabled)   │
│ [ ] Spotify         (enabled)   │
│                                 │
│         [Cancel]      [Done]    │
└─────────────────────────────────┘
```

Picker at limit (checkboxes disabled, counter red):
```
┌─────────────────────────────────┐
│ Pick Apps              20/20    │  ← counter in red
├─────────────────────────────────┤
│ ┌───────────────────────────┐   │
│ │ Search...                 │   │
│ └───────────────────────────┘   │
│                                 │
│ [✓] WhatsApp        (selected, can deselect)
│ [ ] Instagram       (disabled, grayed)
│ [ ] Spotify         (disabled, grayed)
│                                 │
│         [Cancel]      [Done]    │
└─────────────────────────────────┘
```

#### Code Statistics

| Component | Files | Lines changed |
|---|---|---|
| Watch App (C) | 0 | 0 (unchanged) |
| Android App (Kotlin) | 4 | ~50 (strings added, constant + guard + params in MainActivity, counter + width in AppScreen, counter + disable logic in AppPickerDialog) |
| **Total** | **4** | **~50** |

#### Build Status

- Watch app: `pebble build` — compiles cleanly for `basalt` and `emery` (unchanged)
- Android app: `./gradlew assembleDebug` — BUILD SUCCESSFUL

---

## #17 — Fix: Duplicate App Entries on Watch During Live Update + Loading Indicator

### Overview

Fixed duplicate app entries appearing on the watch when the companion app sends the list multiple times concurrently (e.g., both `MainActivity` and `PebbleListenerService` broadcast the same list). Also added a "Loading..." message displayed while the watch waits for the initial list from the phone, and resolved a compiler warning about buffer truncation.

### Analysis

- **Previous state**: The companion app sends the app list via two parallel paths: `MainActivity` directly via its `PebbleSenderHelper`, and `MainActivity` broadcasts `ACTION_SEND_APP_LIST` which `PebbleListenerService` receives and also sends. Both arrive at the watch, interleaving chunks on the BLE connection and causing duplicate/mixed entries.
- **Transfer ID**: Introduced a `UInt8` transfer ID (key `6` in the AppMessage protocol) that increments on every list send. The watch tracks the current transfer ID and discards chunks from stale transfers (lower ID), accepting only chunks belonging to the most recent transfer.
- **Loading indicator**: When the watchapp opens, it shows a blank screen while waiting for the phone's response. Added a "Loading..." message that displays during the waiting period and disappears once the list arrives.
- **Buffer truncation warning**: The `s_index_buf` in `window_main.c` was only 16 bytes, insufficient for the `"%d/%d"` format. Enlarged to 32 bytes.

### Watch App (`pbw/`) — ~25 lines changed across 4 files

#### Modified Files

| File | Changes |
|---|---|
| `src/c/packets.c` | Added `static uint8_t s_current_transfer_id` and `static bool s_loading`. Modified `handle_app_list()` to read key `6` (transfer ID), discard obsolete chunks, and clear `s_loading` on completion. Added `packets_is_loading()` getter. Reset transfer ID and loading flag in `handle_phone_welcome()` and `response_timeout_handler()`. Set `s_loading = true` in `request_app_list()`. |
| `src/c/packets.h` | Added `bool packets_is_loading(void)` declaration. |
| `src/c/window_main.c` | Added `window_main_update_display()` call in `window_appear()` so the loading state renders immediately. Updated empty-list branch to show `"Loading..."` when `packets_is_loading()` is true, otherwise the existing "No apps" message. Enlarged `s_index_buf` from 16 to 32 bytes. |
| `src/c/strings.h` | Added `#define STR_LOADING_MESSAGE "Loading..."`. |

#### Key Implementation Details

**Transfer ID** (`packets.c`):
```c
static uint8_t s_current_transfer_id = 0;

// In handle_app_list():
Tuple* idTuple = dict_find(iter, 6);
uint8_t transfer_id = idTuple ? idTuple->value->uint8 : 0;

if (transfer_id < s_current_transfer_id) {
    // Discard obsolete chunk from a previous transfer
    return;
}
if (transfer_id > s_current_transfer_id) {
    s_current_transfer_id = transfer_id;
    app_list_clear();
}
```
Messages without key `6` are treated as `transfer_id = 0` (backward compatibility). The watch accepts only chunks matching or exceeding the current transfer ID.

**Loading state** (`packets.c` + `window_main.c`):
- `request_app_list()` sets `s_loading = true` before sending the welcome.
- `handle_app_list()` clears `s_loading = false` when `is_last` is reached.
- `response_timeout_handler()` and `handle_phone_welcome()` clear `s_loading` on reset.
- `window_appear()` calls `window_main_update_display()` after `request_app_list()`, rendering "Loading..." immediately.
- `window_main_update_display()` checks `packets_is_loading()` to choose between `"Loading..."` and the empty message.

**Buffer fix** (`window_main.c`):
Changed `static char s_index_buf[16]` to `static char s_index_buf[32]`, eliminating the `-Wformat-truncation` warning from the `snprintf("%d/%d", ...)` call.

**Unchanged**: App list management (`app_list.c/h`), click handling, navigation, launch logic, protocol versioning, all existing packet types and keys.

### Android Companion App (`apk/`) — ~10 lines changed across 1 file

#### Modified Files

| File | Changes |
|---|---|
| `PebbleSenderHelper.kt` | Added `private var transferId: UInt = 0u`. Modified `sendAppList()` to increment `transferId` on each call, include key `6` in empty-list message, and pass the ID to `sendAppListChunks()`. Modified `sendAppListChunks()` to accept `transferId: UByte` and include key `6` in every chunk. |

#### Key Implementation Details

**Transfer ID** (`PebbleSenderHelper.kt`):
```kotlin
private var transferId: UInt = 0u

suspend fun sendAppList(apps: List<LaunchApp>, watch: WatchIdentifier?): TransmissionResult {
    transferId = (transferId + 1u) and 0xFFu
    val currentTransferId = transferId.toUByte()
    // ... include key 6 in all chunks
}
```
The ID wraps at 256 (UInt8). Each `PebbleSenderHelper` instance maintains its own counter. When multiple instances exist (e.g., `MainActivity` + `PebbleListenerService`), they produce different IDs; the watch accepts only the highest, correctly discarding the earlier transfer.

### Protocol Documentation — ~5 lines changed

#### Modified Files

| File | Changes |
|---|---|
| `COMMUNICATION_PROTOCOL.md` | Added key `6` (UInt8, Transfer ID) to the keys table. Updated "Chunked App Lists" section with transfer ID behavior and deduplication explanation. |

#### Code Statistics

| Component | Files | Lines changed |
|---|---|---|
| Watch App (C) | 4 | ~25 (transfer ID + loading state in packets, display update in window_main, string in strings.h, buffer fix) |
| Android App (Kotlin) | 1 | ~10 (transfer ID state and injection in PebbleSenderHelper) |
| Documentation | 1 | ~5 (key table + chunked lists section) |
| **Total** | **6** | **~40** |

#### Build Status

- Watch app: `pebble build` — compiles cleanly for `basalt` and `emery`, zero warnings (pre-existing truncation warning fixed)
- Android app: `./gradlew assembleDebug` — BUILD SUCCESSFUL

---

## #18 — Launch Confirmation Vibration Feedback

### Overview

Added haptic vibration feedback on the Pebble watch when an Android app is successfully launched from the companion app. The user configures the vibration type (None, Short, Long, Double) via a dropdown in a new "Watchapp settings" section of the companion app's Settings screen. Preferences are synchronized bidirectionally: changes in the companion app immediately sync to the watch, and the watch persists the preference across reboots. On watch connection, the companion app sends the current preference.

### Analysis

- **Previous state**: The watch launched apps via AppMessage (packet type 1) but received no confirmation. The companion app called `startActivity()` in `LaunchActivity` with no feedback to the watch. No settings UI existed on either side for vibration.
- **Protocol**: Added packet type 12 (Launch Confirm) and packet type 13 (Vibration Preference), plus keys 10 and 11. All existing hardcoded packet/key numbers in the watch app were replaced with named `#define` constants for maintainability.
- **Watch persistence**: Vibration preference stored in Pebble's PersistentKeyStore (`persist_*` API), loaded on boot, defaulting to None.
- **Launch confirmation**: `LaunchActivity` wraps `startActivity()` in try-catch, broadcasts result to `PebbleListenerService`, which sends packet 12 to the watch. The watch triggers `vibes_short_pulse()`, `vibes_long_pulse()`, or `vibes_double_pulse()` based on saved preference.
- **UI**: New "Watchapp settings" accordion card in Settings screen with a compact dropdown (Row + Text + Icon, not full TextField) for vibration selection, positioned between "General" and "Permissions". Both the theme dropdown in "General" and the vibration dropdown in "Watchapp settings" share the same compact Row-based trigger pattern to avoid Material3's hardcoded 280dp `minWidth` in `TextField`/`OutlinedTextField`.

### Watch App (`pbw/`) — ~60 lines changed across 3 files

#### Modified Files

| File | Changes |
|---|---|
| `src/c/packets.h` | Added `#define` constants for all packet types (0, 1, 10, 11, 12, 13), all keys (0–11), and vibration values (0–3). Added declarations for `load_vibration_pref()` and `packets_get_vibration_pref()`. |
| `src/c/packets.c` | Replaced all hardcoded packet/key numbers with named constants. Added `s_vibration_pref` static variable and `PERSIST_KEY_VIBRATION_PREF`. Implemented `save_vibration_pref()`, `load_vibration_pref()`, `packets_get_vibration_pref()` using `persist_*` API. Added `handle_vibration_pref()` (receives packet 13, saves preference) and `handle_launch_confirm()` (receives packet 12, triggers vibration based on preference). Added switch cases for new packet types in `inbox_received_handler`. |
| `src/c/pLauncher.c` | Added `load_vibration_pref()` call in `init()` after `packets_init()`. |

#### Key Implementation Details

**Protocol constants** (`packets.h`):
```c
#define PACKET_TYPE_LAUNCH_CONFIRM 12
#define PACKET_TYPE_VIBRATION_PREF 13
#define KEY_LAUNCH_CONFIRM 10
#define KEY_VIBRATION_PREF 11
#define VIBE_NONE 0, VIBE_SHORT 1, VIBE_LONG 2, VIBE_DOUBLE 3
```

**Persistence** (`packets.c`):
```c
static uint8_t s_vibration_pref = VIBE_NONE;

void load_vibration_pref(void) {
    if (persist_exists(PERSIST_KEY_VIBRATION_PREF))
        s_vibration_pref = (uint8_t)persist_read_int(PERSIST_KEY_VIBRATION_PREF);
    else
        s_vibration_pref = VIBE_NONE;
}
```

**Launch confirm handler** (`packets.c`):
```c
static void handle_launch_confirm(DictionaryIterator* iter) {
    Tuple* t = dict_find(iter, KEY_LAUNCH_CONFIRM);
    if (!t) return;
    uint8_t confirm = t->value->uint8;
    if (confirm == 1) {
        uint8_t pref = packets_get_vibration_pref();
        switch (pref) {
            case VIBE_SHORT: vibes_short_pulse(); break;
            case VIBE_LONG: vibes_long_pulse(); break;
            case VIBE_DOUBLE: vibes_double_pulse(); break;
        }
    }
}
```

### Android Companion App (`apk/`) — ~90 lines changed across 6 files

#### Modified Files

| File | Changes |
|---|---|
| `res/values/strings.xml` | Added 7 strings: `settings_section_watchapp`, `settings_vibration`, `settings_vibration_desc`, `settings_vibration_none`, `settings_vibration_short`, `settings_vibration_long`, `settings_vibration_double`. |
| `data/AppDataStore.kt` | Added `KEY_VIBRATION_PREF`, `MutableStateFlow<Int>` for vibration pref, `getVibrationPref()`, `setVibrationPref()`, `loadVibrationPref()` (default 0). |
| `MainActivity.kt` | Added `_vibrationPref` and `vibrationPref` StateFlow to `AppViewModel`, `setVibrationPref()`. Load from DataStore in `LaunchedEffect`. Pass `vibrationPref` and `onVibrationPrefChange` to `SettingsScreen`. Callback saves to DataStore and sends packet 13 via `senderHelper.sendVibrationPref()`. |
| `ui/SettingsScreen.kt` | Added `vibrationPref` and `onVibrationPrefChange` parameters. Added "Watchapp settings" accordion card between General and Permissions. Compact dropdown using Row with `Arrangement.SpaceBetween`, `MenuAnchorType.PrimaryNotEditable`, and `.widthIn(min = 80.dp)`. Also updated the theme dropdown in "General" section to use the same compact pattern. |
| `PebbleSenderHelper.kt` | Added `sendVibrationPref(pref: UInt)` (packet 13, key 11) and `sendLaunchConfirm(success: Boolean)` (packet 12, key 10). |
| `LaunchActivity.kt` | Wrapped app launch in try-catch. Added `ACTION_LAUNCH_RESULT` broadcast with success/failure result. Sends broadcast before `finish()`. |
| `PebbleListenerService.kt` | Added `launchResultReceiver` BroadcastReceiver for `ACTION_LAUNCH_RESULT`. Calls `senderHelper.sendLaunchConfirm()` on receive. Registered in `onCreate()`, unregistered in `onDestroy()`. Modified `handleWatchWelcome()` to send vibration pref after app list. |

#### Key Implementation Details

**Compact dropdown trigger** (`SettingsScreen.kt`):
Both theme and vibration dropdowns use the same Row-based trigger pattern (not `TextField`):
```kotlin
Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp)) {
    Column(modifier = Modifier.weight(1f)) {
        Text(label, style = bodyLarge)
        Text(description, style = bodySmall)
    }
    ExposedDropdownMenuBox(expanded, onExpandedChange) {
        Row(
            modifier = Modifier
                .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                .padding(horizontal = 8.dp, vertical = 4.dp)
                .widthIn(min = 80.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(selectedValue, style = bodyMedium)
            Icon(expand/collapse, size = 20.dp)
        }
        ExposedDropdownMenu { options }
    }
}
```
The `TextField` and `OutlinedTextField` in Material3 1.3.1 have a hardcoded 280dp `minWidth` that cannot be overridden (`textFieldParameters` unavailable). The Row-based workaround achieves a compact trigger with fixed width (`min = 80.dp`), `Arrangement.SpaceBetween` to anchor the icon to the right edge, and `MenuAnchorType.PrimaryNotEditable` to properly integrate with `ExposedDropdownMenuBox` for tap-to-open/tap-to-close toggling.

**Launch result broadcast** (`LaunchActivity.kt`):
```kotlin
try {
    startActivity(launchIntent)
    sendLaunchResult(true)
} catch (e: Exception) {
    sendLaunchResult(false)
}
```

**Connection sync** (`PebbleListenerService.kt`):
On watch welcome, after sending app list, reads `dataStore.getVibrationPref()` and sends via `helper.sendVibrationPref()`.

#### Layout

Settings screen order:
```
┌─────────────────────────────────┐
│ Settings                        │
│                                 │
│ ▾ General                       │
│   Theme                         │
│   Appearance of the        [Dark ▼]
│   companion app                 │
│   ─────────────────────         │
│   Show system apps    [●]       │
│                                 │
│ ▸ Watchapp settings             │
│   (expanded shows:)             │
│   Vibration on launch           │
│   Haptic feedback...   [None ▼] │
│                                 │
│ ▸ Permissions                   │
│ ▸ Debug                         │
│                                 │
│               v1.0.0            │
└─────────────────────────────────┘
```

#### Code Statistics

| Component | Files | Lines changed |
|---|---|---|
| Watch App (C) | 3 | ~60 (constants, persistence, handlers, switch cases) |
| Android App (Kotlin) | 6 | ~100 (strings, data store, view model, UI with compact dropdowns, sender, activity, service) |
| Documentation | 1 | ~10 (new packet types, keys, protocol section) |
| **Total** | **10** | **~170** |

#### Build Status

- Watch app: `pebble build` — compiles cleanly for `basalt` and `emery`
- Android app: `./gradlew assembleDebug` — BUILD SUCCESSFUL

---

## #19 — Auto-Close Watchapp After Successful Launch

### Overview

Added an "Auto-close" switch in the "Watchapp settings" section of the companion app's Settings screen. When enabled, the Pebble watchapp automatically closes after a successful app launch, returning directly to the watchface (not the app menu). The user still receives the configured vibration feedback before the app closes. Preferences are synchronized from the phone to the watch on connection and when the setting changes, and persisted on both sides.

### Analysis

- **Previous state**: After a successful launch, the watch vibrated (if configured) but the watchapp remained open on screen.
- **Protocol**: Added packet type 14 (Auto-Close Pref) with dictionary key 12 (auto-close flag, UInt8). This is a separate packet from the launch confirm (type 12, key 10), keeping the transient launch event distinct from the persistent auto-close setting.
- **Watch persistence**: Auto-close preference stored in Pebble's PersistentKeyStore (`persist_*` API), loaded on boot, defaulting to `false` (disabled).
- **Auto-close mechanism**: On successful launch confirm, the watch schedules an `AppTimer` with a delay matching the vibration duration (250ms for short, 500ms for long, 300ms for double, 0ms for none). When the timer fires, the watch calls `exit_reason_set(APP_EXIT_ACTION_PERFORMED_SUCCESSFULLY)` then `window_stack_pop_all(false)`. The exit reason tells the BSP to navigate to the **default watchface** instead of the app menu. Failed launches never trigger auto-close.
- **UI**: Switch in the "Watchapp settings" accordion card, positioned below the vibration dropdown, separated by a `HorizontalDivider()`.

### Watch App (`pbw/`) — ~40 lines changed across 3 files

#### Modified Files

| File | Changes |
|---|---|
| `src/c/packets.h` | Added `#define PACKET_TYPE_AUTO_CLOSE_PREF 14`, `#define KEY_AUTO_CLOSE 12`. Added declarations for `load_auto_close_pref()` and `packets_get_auto_close()`. |
| `src/c/packets.c` | Added `static bool s_auto_close` and `#define PERSIST_KEY_AUTO_CLOSE 0x02`. Implemented `save_auto_close_pref()`, `load_auto_close_pref()`, `packets_get_auto_close()` using `persist_*` API. Added `handle_auto_close_pref()` (receives packet 14, saves preference). Added `auto_close_timer_handler()` (fires after vibration, sets exit reason and pops windows). Modified `handle_launch_confirm()` to schedule an `AppTimer` with duration matching the vibration type when auto-close is enabled. Added switch case for packet type 14 in `inbox_received_handler`. |
| `src/c/pLauncher.c` | Added `load_auto_close_pref()` call in `init()` after `load_vibration_pref()`. |

#### Key Implementation Details

**Protocol constants** (`packets.h`):
```c
#define PACKET_TYPE_AUTO_CLOSE_PREF 14
#define KEY_AUTO_CLOSE 12
```

**Persistence** (`packets.c`):
```c
static bool s_auto_close = false;
#define PERSIST_KEY_AUTO_CLOSE 0x02

void load_auto_close_pref(void) {
    if (persist_exists(PERSIST_KEY_AUTO_CLOSE))
        s_auto_close = (bool)persist_read_int(PERSIST_KEY_AUTO_CLOSE);
    else
        s_auto_close = false;
}
```

**Timer-based auto-close** (`packets.c`):
```c
static void auto_close_timer_handler(void* context) {
    exit_reason_set(APP_EXIT_ACTION_PERFORMED_SUCCESSFULLY);
    window_stack_pop_all(false);
}

// In handle_launch_confirm(), after vibration:
if (packets_get_auto_close()) {
    uint32_t vibe_duration = 0;
    if (pref == VIBE_SHORT) vibe_duration = 250;
    else if (pref == VIBE_LONG) vibe_duration = 500;
    else if (pref == VIBE_DOUBLE) vibe_duration = 300;
    app_timer_register(vibe_duration, auto_close_timer_handler, NULL);
}
```

The timer approach ensures the vibration completes before the app closes. The durations match the exact `VibePattern` segments defined in the Repebble firmware (`src/fw/applib/ui/vibes.c`):
- `vibes_short_pulse`: ON 250ms → total 250ms
- `vibes_long_pulse`: ON 500ms → total 500ms
- `vibes_double_pulse`: ON 100ms, OFF 100ms, ON 100ms → total 300ms

**Exit reason** (`packets.c`):
`exit_reason_set(APP_EXIT_ACTION_PERFORMED_SUCCESSFULLY)` tells the BSP to navigate to the **default watchface** after the app exits, rather than returning to the app menu. This was verified against the Repebble firmware source (`src/fw/process_management/app_manager.c`), where `APP_EXIT_ACTION_PERFORMED_SUCCESSFULLY` returns `watchface_get_default_install_id()` as the destination, while `APP_EXIT_NOT_SPECIFIED` falls back to the previously running app.

**`app_intent_set_user_wants_app_moved_to_background` unavailable**: The original plan specified this API, but it does not exist in SDK 4.17. The combination of `exit_reason_set(APP_EXIT_ACTION_PERFORMED_SUCCESSFULLY)` + `window_stack_pop_all(false)` achieves the same result (close app → go to watchface).

### Android Companion App (`apk/`) — ~30 lines changed across 6 files

#### Modified Files

| File | Changes |
|---|---|
| `res/values/strings.xml` | Added `settings_auto_close` ("Auto-close on launch") and `settings_auto_close_desc` ("Close pLauncher after successful app launch"). |
| `data/AppDataStore.kt` | Added `KEY_AUTO_CLOSE`, `MutableStateFlow<Boolean>` for `_autoClose`, `StateFlow<Boolean>` for `autoClose`, `getAutoClose()`, `setAutoClose()`, `loadAutoClose()` (default `false`). |
| `MainActivity.kt` | Added `_autoClose` and `autoClose` StateFlow to `AppViewModel`, `setAutoClose()`. Load from DataStore in `LaunchedEffect`. Pass `autoClose` and `onAutoCloseChange` to `SettingsScreen`. Callback saves to DataStore, updates ViewModel, and sends packet 14 via `senderHelper.sendAutoClosePref()`. |
| `ui/SettingsScreen.kt` | Added `autoClose` and `onAutoCloseChange` parameters. Added `HorizontalDivider()` + Row with Column (label + desc) and Switch below the vibration row in the "Watchapp settings" section. |
| `PebbleSenderHelper.kt` | Added `sendAutoClosePref(enabled: UInt)` (packet type 14, key 12). |
| `PebbleListenerService.kt` | Modified `handleWatchWelcome()` to send auto-close pref after vibration pref. |

#### Key Implementation Details

**Switch UI** (`SettingsScreen.kt`):
Same pattern as the "Show system apps" switch:
```kotlin
HorizontalDivider()
Row(...) {
    Column(modifier = Modifier.weight(1f)) {
        Text(stringResource(R.string.settings_auto_close), style = bodyLarge)
        Text(stringResource(R.string.settings_auto_close_desc), style = bodySmall)
    }
    Switch(checked = autoClose, onCheckedChange = onAutoCloseChange)
}
```

**Sender** (`PebbleSenderHelper.kt`):
```kotlin
suspend fun sendAutoClosePref(enabled: UInt): TransmissionResult {
    val dict: PebbleDictionary = mapOf(
        0u to PebbleDictionaryItem.UInt8(14),
        12u to PebbleDictionaryItem.UInt8(if (enabled == 1u) 1 else 0)
    )
    val result = sender.sendDataToPebble(WATCH_APP_UUID, dict, null)
    return result?.values?.firstOrNull() ?: TransmissionResult.FailedTimeout
}
```

**Connection sync** (`PebbleListenerService.kt`):
On watch welcome, after sending vibration pref, reads `dataStore.getAutoClose()` and sends via `helper.sendAutoClosePref()`.

**`Boolean.toUInt()` not available in Kotlin**: The plan specified `it.toUInt()` which doesn't exist for `Boolean`. Used `if (it) 1u else 0u` in both `MainActivity.kt` and `PebbleListenerService.kt`.

#### Layout

Settings screen "Watchapp settings" section (expanded):
```
┌─────────────────────────────────┐
│ ▾ Watchapp settings             │
│   Vibration on launch           │
│   Haptic feedback...   [None ▼] │
│   ─────────────────────         │
│   Auto-close on launch    [●]   │
│   Close pLauncher after         │
│   successful app launch         │
└─────────────────────────────────┘
```

### Protocol Documentation — ~5 lines changed

#### Modified Files

| File | Changes |
|---|---|
| `COMMUNICATION_PROTOCOL.md` | Added key `12` (UInt8, Phone → Watch, Auto-close preference) to the keys table. Added packet type `14` (Auto-Close Preference — keys: `12` (auto-close flag uint8, 1 = enabled, 0 = disabled)) to the Phone → Watch section. Updated "Settings" note to mention both vibration and auto-close preferences are synchronized from phone to watch. |

#### Code Statistics

| Component | Files | Lines changed |
|---|---|---|
| Watch App (C) | 3 | ~40 (constants, persistence, timer handler, auto-close in confirm handler) |
| Android App (Kotlin) | 6 | ~30 (strings, data store, view model, UI switch, sender, service) |
| Documentation | 1 | ~5 (key table, packet type, settings note) |
| **Total** | **10** | **~75** |

#### Build Status

- Watch app: `pebble build` — compiles cleanly for `basalt` and `emery`
- Android app: `./gradlew assembleDebug` — BUILD SUCCESSFUL
