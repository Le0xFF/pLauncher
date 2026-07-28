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

---

## #8 — Dark and AMOLED Themes

### Overview

Added two new themes ("Dark" and "AMOLED") to the Android companion app, alongside the existing "Light" theme. Theme selection is available in a new "Themes" accordion section in the Settings page, positioned after "Permissions". The selected theme is persisted in `SharedPreferences` and applies immediately to the entire UI without requiring a restart.

### Analysis

- **Previous state**: `MainActivity.kt` used `MaterialTheme` with no customization (Light by default). No theme selection existed. The AndroidManifest used `@android:style/Theme.Material.Light.NoActionBar` for all activities.
- **Technology**: Jetpack Compose Material3 `ColorScheme`. Three themes: Light (`lightColorScheme()`), Dark (`darkColorScheme()`), AMOLED (dark with pure black surfaces).
- **AMOLED specifics**: All surface colors set to pure black (`#000000`) except `surfaceContainer`/`surfaceContainerHigh` (`#1A1A1A`) and `surfaceContainerHighest` (`#222222`) for visual distinction of elevated components (NavigationBar, dropdown menus). Tonal elevation disabled to prevent surfaces from appearing grey. System dynamic color (accent) preserved via `darkColorScheme()` base.
- **System UI**: `WindowCompat.getInsetsController` used to set status bar and navigation bar icon appearance (light icons for dark/AMOLED, dark icons for Light). Status bar and navigation bar colors set to transparent for edge-to-edge rendering.

### Android Companion App (`apk/`) — ~120 lines changed across 5 files

#### Files Created

| File | Description |
|---|---|
| `ui/Theme.kt` | New file containing `AppTheme` enum (`Light`, `Dark`, `Amoled`), `getAppThemeColorScheme()` helper, and `pLauncherTheme()` composable with system UI handling. |

#### Modified Files

| File | Changes |
|---|---|
| `data/AppDataStore.kt` | Added `KEY_THEME`, `_appTheme`/`appTheme` StateFlow, `loadAppTheme()`, `getAppTheme()`, `setAppTheme()`. Persists theme enum name in SharedPreferences with `Light` default. |
| `MainActivity.kt` | Added `_appTheme`/`appTheme` to `AppViewModel` with `setAppTheme()`. Replaced `MaterialTheme` with `pLauncherTheme`. Collects theme on startup from `AppDataStore`. Passes `currentTheme` and `onThemeChange` to `SettingsScreen`. |
| `ui/SettingsScreen.kt` | Added `currentTheme` and `onThemeChange` parameters. Added "Themes" accordion with `@OptIn(ExperimentalMaterial3Api::class)` `ExposedDropdownMenuBox` containing three options using `DropdownMenuItem`. |
| `res/values/strings.xml` | Added `settings_section_themes` ("Themes"), `settings_theme` ("Theme"), `settings_theme_light` ("Light"), `settings_theme_dark` ("Dark"), `settings_theme_amoled` ("AMOLED"). |

#### Key Implementation Details

**AppTheme enum**: Three values — `Light`, `Dark`, `Amoled`. Serialized as enum name string in SharedPreferences.

**Color schemes**:
- **Light**: Standard `lightColorScheme()` — unchanged from original behavior
- **Dark**: Standard `darkColorScheme()` with system dynamic color accent
- **AMOLED**: `darkColorScheme().copy()` with surface overrides:
  - Pure black (`#000000`): `surface`, `surfaceVariant`, `surfaceContainerLowest`, `surfaceBright`, `surfaceDim`, `background`, `inverseSurface`
  - Near black (`#0A0A0A`): `surfaceContainerLow`
  - Dark grey (`#1A1A1A`): `surfaceContainer`, `surfaceContainerHigh` (NavigationBar matches dropdown menus)
  - Slightly lighter (`#222222`): `surfaceContainerHighest`
  - White text: `onSurface`, `onSurfaceVariant`, `onBackground`, `inverseOnSurface`
  - Accent colors (primary, secondary, tertiary, etc.) inherited from `darkColorScheme()` (system dynamic color)

**System UI integration** (`pLauncherTheme`):
- Sets `WindowCompat.setDecorFitsSystemWindows(window, false)` for edge-to-edge
- `controller.isAppearanceLightStatusBars = !isDark` — white icons on dark/AMOLED, black on Light
- `controller.isAppearanceLightNavigationBars = !isDark` — same for bottom navigation gestures
- `window.statusBarColor = TRANSPARENT` and `window.navigationBarColor = TRANSPARENT`
- `LocalTonalElevationEnabled provides (theme != AppTheme.Amoled)` — disables tonal elevation for AMOLED to keep surfaces pure black

**Settings Themes accordion**: Uses `ExposedDropdownMenuBox` (requires `@OptIn(ExperimentalMaterial3Api::class)`) with a `TextField` showing the current theme label and a dropdown with three `DropdownMenuItem` entries. Selection updates both the ViewModel and `AppDataStore` simultaneously, triggering an immediate UI refresh via `pLauncherTheme`.

**Persistence**: Theme name stored as string in SharedPreferences under key `"theme"`. On first launch (key absent), defaults to `Light` — preserving existing behavior.

#### AMOLED Theme Visual Layout

```
┌─────────────────────────────────┐
│ Status bar (white icons)  ░░░░░ │  ← Transparent background
│                                 │
│ ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓ │  ← Pure black content (surface)
│ ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓ │
│ ▓▓▓▓▓ No apps configured ▓▓▓▓▓ │
│ ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓ │
│ ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓ │
│ ▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓ │
│ ███ Apps  │  Settings ███      │  ← Dark grey bar (#1A1A1A)
└─────────────────────────────────┘
  ▓ = Black (#000000), █ = Grey (#1A1A1A)
```

#### Code Statistics

| Component | Files | Lines changed |
|---|---|---|
| Watch App (C) | 0 | 0 (unchanged) |
| Android App (Kotlin) | 5 | ~120 (Theme.kt created, AppDataStore/MainActivity/SettingsScreen modified, strings added) |
| **Total** | **5** | **~120** |

#### Build Status

- Watch app: `pebble build` — compiles cleanly for `basalt` and `emery` (unchanged)
- Android app: `./gradlew assembleDebug` — BUILD SUCCESSFUL

---

## #9 — Home Screen: App Icons

### Overview

Added app icon display to the Home screen (`AppScreen.kt`). Each app entry now shows the application's icon (36dp) to the left of the display name and package name. Icons are loaded dynamically from `PackageManager` at display time using the app's `packageName`, without modifying the data model or persistence layer.

### Analysis

- **Previous state**: `AppScreen.kt` used a `ListItem` composable showing only `headlineContent` (display name) and `supportingContent` (package name). No icon was visible.
- **Model**: `LaunchApp` contains only `packageName` and `displayName`. Icons cannot be stored in `SharedPreferences`, so persistence layer (`AppDataStore`) and model remain unchanged.
- **Strategy**: Load icons on-demand from `PackageManager` using `packageName` at render time. This is consistent with `AppPickerDialog` which already loads icons the same way. Icons are always up-to-date with the installed app.
- **Error handling**: `PackageManager.NameNotFoundException` (uninstalled app) and generic exceptions (drawable conversion failure) are caught, returning `null`. The icon is simply omitted for apps where loading fails, preventing crashes.

### Android Companion App (`apk/`) — ~30 lines changed in 1 file

#### Modified Files

| File | Changes |
|---|---|
| `ui/AppScreen.kt` | Added imports for `Image`, `asImageBitmap`, `LocalContext`, `toBitmap`, `PackageManager`. Replaced `ListItem` with `Row` containing icon `Image` (36dp) + `Column` with display name and package name. Icon loaded via `remember(app.packageName)` with `try/catch` for safety. |

#### Key Implementation Details

**Icon loading**: Inside the `LazyColumn`'s `items` lambda, each app's icon is loaded using:
- `LocalContext.current` to get the `Context`
- `remember(app.packageName)` to cache the bitmap per app, avoiding redundant `PackageManager` calls
- `pm.getApplicationInfo(packageName, 0).loadIcon(pm)` to retrieve the `Drawable`
- `drawable?.toBitmap(72, 72)?.asImageBitmap()` to convert to `ImageBitmap` (72x72 source for crisp 36dp display)
- Two `catch` blocks: `PackageManager.NameNotFoundException` (package uninstalled) and generic `Exception` (drawable issues), both returning `null`

**Row layout**: Replaced `ListItem` with `Row(verticalAlignment = Alignment.CenterVertically)`:
- `Image` with `Modifier.size(36.dp).padding(end = 8.dp)` — shown only when icon loads successfully (`?.let`)
- `Column(Modifier.weight(1f))` with `Text(app.displayName)` and `Text(app.packageName, style = MaterialTheme.typography.bodySmall)`
- `Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)` for consistent spacing

**Unchanged**: `LaunchApp` model, `AppDataStore` persistence, `AppPickerDialog`, `MainActivity`, `AppScreen` function signature. Search, FAB, and empty state logic are unaffected.

#### Layout

```
┌─────────────────────────────────┐
│ [Search field]                  │
│                                 │
│  [icon] WhatsApp         │      │
│          com.whatsapp         │  │
│  [icon] Google Maps        │      │
│          com.google.android... │  │
│  [icon] Spotify            │      │
│          com.spotify.mobile... │  │
│                                 │
│                    [+ Add App]  │  ← FAB
└─────────────────────────────────┘
```

#### Code Statistics

| Component | Files | Lines changed |
|---|---|---|
| Watch App (C) | 0 | 0 (unchanged) |
| Android App (Kotlin) | 1 | ~30 (AppScreen.kt imports + layout replaced) |
| **Total** | **1** | **~30** |

#### Build Status

- Watch app: `pebble build` — compiles cleanly for `basalt` and `emery` (unchanged)
- Android app: `./gradlew assembleDebug` — BUILD SUCCESSFUL
