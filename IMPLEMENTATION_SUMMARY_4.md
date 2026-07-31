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
