# Implementation summary

## #6 — Settings Page: Expandable Dropdown Menus

### Overview

Restructured the Android companion app's Settings page (`SettingsScreen.kt`) from a flat `Column` layout with manual separators into expandable accordion panels grouped by logical categories. The new layout groups settings under "General" and "Permissions" collapsible sections, each using a custom `AccordionCard` composable with animated expand/collapse transitions. Added interactive "Revoke" buttons for granted permissions, allowing users to navigate to system settings to revoke permissions.

### Analysis

- **Previous state**: `SettingsScreen.kt` displayed all settings in a flat `Column` with manual `Spacer` and `Text` elements for section titles. The "Background Launch Permissions" section used two separate `Text` elements for title and description.
- **Technology**: Jetpack Compose Material3. The native `ExpansionPanel` component (introduced in Material3 1.2+) is not available in the project's Material3 version (1.3.1, resolved from BOM 2025.02.00). A manual accordion implementation using `Card` + `AnimatedVisibility` was used instead.
- **Categories identified**:
  1. **General** — "Show system apps" (Switch), "Generate crash reports" (Switch)
  2. **Permissions** — "Draw Over Other Apps" (Grant/Revoke button), "Ignore Battery Optimizations" (Grant/Revoke button)
- **Version text**: Moved outside panels, pinned to the bottom of the page, centered horizontally, app name removed.

### Android Companion App (`apk/`) — ~50 lines changed across 4 files

#### Modified Files

| File | Changes |
|---|---|
| `strings.xml` | Added `settings_section_general` ("General"), `settings_section_permissions` ("Permissions"), `button_revoke` ("Revoke"). Removed unused `settings_perm_section_title` and `settings_perm_section_desc`. Updated `settings_version` from "pLauncher v1.0.0" to "v1.0.0" (removed app name). |
| `SettingsScreen.kt` | Complete restructuring: replaced flat `Column` with `AccordionCard` panels, custom `Row`-based item layout for consistent vertical alignment, `HorizontalDivider` separators, version text pinned to bottom, "Revoke" buttons for granted permissions. |
| `build.gradle.kts` | Added `material-icons-extended` dependency for `ExpandLess`/`ExpandMore` icons. |
| `libs.versions.toml` | Added `androidx-material-icons-extended` version catalog entry. |

#### Key Implementation Details

**AccordionCard composable**: A private reusable composable that wraps content in a `Card` with:
- A clickable header `Row` containing the section title and a chevron icon (`ExpandMore`/`ExpandLess` based on state)
- `AnimatedVisibility` with `fadeIn` + `expandVertically` (enter) and `shrinkVertically` + `fadeOut` (exit) using `spring()` animation
- Content padding for the expanded body

Each panel maintains independent expansion state via `remember { mutableStateOf(false) }` (non-exclusive accordion — multiple panels can be open simultaneously).

**Custom Row-based item layout**: The original `ListItem` composable does not vertically center `trailingContent` when `supportingContent` is present, causing misalignment of buttons and switches. Replaced all `ListItem` with custom `Row(verticalAlignment = Alignment.CenterVertically)`:
- Items without description: `Row` with `Text` (weighted) + `Switch`/trailing content, centered vertically
- Items with description: `Row` with `Column` (headline + description, weighted) + `Switch`/button/trailing content, centered vertically
- `HorizontalDivider` replaces `Spacer` as the visual separator between items

This layout ensures the trailing content (switch, button, or text) is always centered relative to the full text block, regardless of whether a description is present.

**Permission buttons (Grant/Revoke)**: Each permission setting shows either a "Grant" or "Revoke" button depending on the current state:
- **Not granted**: Filled `Button` with default colors, labeled "Grant", opens the corresponding system settings page to request the permission
  - "Draw Over Other Apps" → `ACTION_MANAGE_OVERLAY_PERMISSION` (package-specific)
  - "Ignore Battery Optimizations" → `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` (package-specific)
- **Granted**: Filled `Button` with `containerColor = MaterialTheme.colorScheme.error` (red background, white text), labeled "Revoke", opens the corresponding system settings page to revoke the permission
  - "Draw Over Other Apps" → `ACTION_MANAGE_OVERLAY_PERMISSION` (package-specific)
  - "Ignore Battery Optimizations" → `ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS` (general list, user must find and remove the app)

All four buttons use uniform `contentPadding = PaddingValues(horizontal = 12.dp)` for consistent sizing. "Grant" buttons use `18.dp` horizontal padding, "Revoke" buttons use `12.dp` to remain visually compact.

**Version text**: Removed app name prefix. Pinned to bottom using `Spacer(weight = 1f)` above it. Centered horizontally with `Modifier.align(Alignment.CenterHorizontally)`.

**Dependency additions**: `material-icons-extended` is required for `Icons.Default.ExpandLess` and `Icons.Default.ExpandMore` (not included in the base `material-icons-core`). The BOM manages the version automatically.

**Unchanged**: `SettingsScreen` function signature remains identical, so `MainActivity.kt` requires no modifications. Helper functions `checkCanDrawOverlays()` and `checkIgnoringBatteryOptimizations()` are preserved. Data persistence (`AppDataStore`, `AppViewModel`) is unaffected.

#### Layout

```
┌─────────────────────────────────┐
│ Settings                        │
│                                 │
│ ┌───────────────────────────┐   │
│ │ General              [▼]  │   │  ← Collapsed
│ └───────────────────────────┘   │
│                                 │
│ ┌───────────────────────────┐   │
│ │ Permissions            [▲]  │   │  ← Expanded
│ │ ┌─────────────────────┐   │   │
│ │ │ Draw Over Other Apps│ [Revoke] │
│ │ │ Allows launching... │   │   │
│ │ ├─────────────────────┤   │   │
│ │ │ Ignore Battery Opt.  │ [Revoke] │
│ │ │ Prevents aggressive │   │   │
│ │ └─────────────────────┘   │   │
│ └───────────────────────────┘   │
│                                 │
│                                 │
│           v1.0.0                │  ← Centered, bottom
└─────────────────────────────────┘
```

#### API Availability Note

`ExpansionPanel` from `androidx.compose.material3` was verified unavailable in Material3 1.3.1 (the version resolved by BOM 2025.02.00). The component was introduced in Material3 1.2.0 but the BOM's version mapping still resolves to 1.3.1 which does not include it in its compiled classes. The manual `AccordionCard` provides equivalent functionality with full control over animations and styling.

#### Code Statistics

| Component | Files | Lines changed |
|---|---|---|
| Watch App (C) | 0 | 0 (unchanged) |
| Android App (Kotlin) | 4 | ~50 (SettingsScreen restructured, strings updated, dependencies added, revoke buttons) |
| **Total** | **4** | **~50** |

#### Build Status

- Watch app: `pebble build` — compiles cleanly for `basalt` and `emery` (unchanged)
- Android app: `./gradlew assembleDebug` — BUILD SUCCESSFUL

---

## #7 — Permission Dialog: Dual Grant Buttons

### Overview

Restructured the permission dialog in `MainActivity.kt` that appears on first launch. Previously, the dialog listed both required permissions ("Draw Over Other Apps" and "Ignore Battery Optimizations") but only provided a single button that opened the overlay permission screen. The battery optimization permission was inaccessible from the dialog, forcing users to manually navigate to the Settings tab.

The new implementation shows two stacked `Button` widgets (matching the "Grant" button styling from the Settings page), one per missing permission. A "Dismiss" button always appears as `confirmButton`. The dialog re-evaluates on return from system settings, updating to reflect newly granted or revoked permissions. It only appears on cold start (`onCreate`), not on every foreground transition.

### Analysis

- **Previous state**: Single `AlertDialog` with one `confirmButton` ("Open Settings") launching `ACTION_MANAGE_OVERLAY_PERMISSION`. Battery permission had no grant path from the dialog. `perm_dialog_settings_hint` told users to use the Settings tab instead.
- **Problem**: Users who clicked the button only ever reached the overlay permission screen. The battery optimization permission was effectively hidden from the onboarding flow, leading to non-functional installations.
- **Solution**: Two `Button` widgets stacked vertically inside the dialog's `text` content, each opening the correct system Intent. Dialog re-evaluates on `onResume` via `resumeCounter`. A `dismissedOnce` flag prevents re-appearance after explicit dismiss ("Dismiss" button), while allowing re-appearance after revoke from Settings.

### Android Companion App (`apk/`) — ~20 lines changed across 2 files

#### Modified Files

| File | Changes |
|---|---|
| `strings.xml` | Added `perm_button_grant_overlay` ("Grant Overlay Permission"), `perm_button_grant_battery` ("Grant Battery Permission"). |
| `MainActivity.kt` | Replaced single-button permission dialog with two stacked Grant buttons inside `text` content, dynamic button visibility based on missing permissions, `LaunchedEffect(resumeCounter)` for re-evaluation, `dismissedOnce` flag for cold-start-only behavior. |

#### Key Implementation Details

**Dynamic button layout**: The `AlertDialog`'s `text` content now contains a `Column` with:
- Permission description text (only lists permissions that are actually missing)
- Two `Button` widgets stacked vertically, each with `Modifier.fillMaxWidth()` and `contentPadding = PaddingValues(horizontal = 18.dp)`
- Each button only renders if its corresponding permission is missing (`needsOverlay`, `needsBattery`)
- Button styling matches the Settings page "Grant" buttons: filled `Button` with default colors (dark background, white text)

**Button actions**:
- "Grant Overlay Permission" → `ACTION_MANAGE_OVERLAY_PERMISSION` with package URI
- "Grant Battery Permission" → `ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` with package URI
- "Dismiss" (`confirmButton`) → closes dialog, sets `dismissedOnce = true`

**Re-evaluation on resume**: The existing `resumeCounter` mechanism (incremented in `onResume()`) drives re-evaluation:
- `canDrawOverlays` and `ignoringBatteryOpt` are wrapped with `remember(resumeCounter)`, so they re-check on each resume
- `LaunchedEffect(resumeCounter)` updates `showPermissionDialog` based on current permission states, but only when `dismissedOnce` is `false`
- The dialog does NOT close when launching a grant Intent — it stays open and updates on return

**Cold-start-only behavior**: A `dismissedOnce` flag prevents the dialog from reappearing on every foreground transition:
- `dismissedOnce` starts as `false` on `onCreate`
- Setting `dismissedOnce = true` only happens when the user clicks the "Dismiss" button
- Grant button clicks do NOT set `dismissedOnce`, so the dialog reappears if permissions are revoked (e.g., from Settings page)
- `onDismissRequest` (swipe away / back gesture) also does NOT set `dismissedOnce`, allowing re-appearance after revoke
- On cold start (app killed and relaunched), `dismissedOnce` resets to `false`, and the dialog appears if permissions are still missing

#### Layout

```
┌─────────────────────────────────┐
│ Background Launch Permissions   │
│                                 │
│ To launch apps from Pebble      │
│ when this app is in background, │
│ you need to grant special       │
│ permissions:                    │
│                                 │
│ • Draw Over Other Apps          │
│ • Ignore Battery Optimizations  │
│                                 │
│ ┌───────────────────────────┐   │
│ │ Grant Overlay Permission  │   │  ← Full width
│ └───────────────────────────┘   │
│ ┌───────────────────────────┐   │
│ │ Grant Battery Permission  │   │  ← Full width
│ └───────────────────────────┘   │
│                                 │
│        [Dismiss]                │  ← confirmButton
└─────────────────────────────────┘
```

#### Behavior Matrix

| Overlay | Battery | Dialog shown | Buttons shown | Dismiss sets flag |
|---------|---------|-------------|---------------|-------------------|
| Missing | Missing | Yes | Grant Overlay, Grant Battery, Dismiss | Yes |
| Missing | Granted | Yes | Grant Overlay, Dismiss | Yes |
| Granted | Missing | Yes | Grant Battery, Dismiss | Yes |
| Granted | Granted | No | — | — |

After revoke from Settings:
| Action | `dismissedOnce` | Dialog on resume |
|--------|----------------|-------------------|
| Click Grant button | `false` | Reappears if permission revoked |
| Click Dismiss | `true` | Does NOT reappear |
| Swipe away / back | `false` | Reappears if permission revoked |

#### Code Statistics

| Component | Files | Lines changed |
|---|---|---|
| Watch App (C) | 0 | 0 (unchanged) |
| Android App (Kotlin) | 2 | ~20 (dialog restructured, strings added) |
| **Total** | **2** | **~20** |

#### Build Status

- Watch app: `pebble build` — compiles cleanly for `basalt` and `emery` (unchanged)
- Android app: `./gradlew assembleDebug` — BUILD SUCCESSFUL
