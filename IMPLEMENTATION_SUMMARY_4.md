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
