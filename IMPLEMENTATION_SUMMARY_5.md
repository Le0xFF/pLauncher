# Implementation summary

## #21 — Import/Export App List as YAML

### Overview

Added the ability to import and export the app list in YAML format from the companion app's Settings screen. The feature includes a new "Import/Export" accordion section between "Watchapp settings" and "Permissions", with Export sharing the YAML file via Android's share intent (using `FileProvider`), and Import opening a file picker to select a YAML file. The import process includes a confirmation dialog before overwriting the current list, comprehensive validation (duplicate packages, duplicate positions, out-of-range positions, max 20 apps, multiple auto-launch targets), position normalization, and a structured warnings dialog that displays all issues after import. The auto-launch target from the YAML is correctly preserved and synchronized to both the ViewModel, DataStore, and Pebble watch.

### Analysis

- **Previous state**: The companion app managed the app list locally (SharedPreferences) but had no mechanism to back up, restore, or transfer the list between devices. Users could only add/remove apps one at a time.
- **YAML format**: Each app entry contains four fields: `package` (Android package name), `custom_name` (renamed display name, empty if unchanged), `position` (0-based index), `auto_launch` (boolean, true for auto-launch target). No `name` field is stored — the original name is recovered from `PackageManager` during import if `custom_name` is empty.
- **Export**: Generates YAML manually (not via SnakeYAML, which produces verbose output) with 2-space indentation. The YAML is written to a temporary file in the app's cache directory and shared via `FileProvider` with `EXTRA_STREAM`, ensuring file managers like Amaze can save the file correctly.
- **Import**: Parses YAML using SnakeYAML (`org.yaml:snakeyaml:2.3`), validates entries with 6 distinct checks, normalizes positions, resolves display names from `PackageManager`, and returns structured data (`ImportResult`) that MainActivity formats into localized warning messages.
- **Confirmation**: Before applying an import, a dialog warns the user that the current list will be replaced, showing the number of apps from the file. The YAML is parsed upfront but not applied until confirmed.
- **Warnings dialog**: After a confirmed import, if any validation warnings exist, a single AlertDialog displays all warnings with a ⚠️ emoji in the title ("Import warnings"), each warning reason separated by `HorizontalDivider`, labels in bold, and associated package names listed one per line in monospace.
- **Auto-launch preservation**: The import correctly tracks which app had `auto_launch: true` in the YAML, determines its normalized index after position reordering, and applies it to the ViewModel, DataStore, and Pebble watch.

### Android Companion App (`apk/`) — ~250 lines changed across 9 files

#### Modified Files

| File | Changes |
|---|---|
| `gradle/libs.versions.toml` | Added `snakeyaml = "2.3"` in `[versions]` and `snakeyaml = { group = "org.yaml", name = "snakeyaml", version.ref = "snakeyaml" }` in `[libraries]`. |
| `app/build.gradle.kts` | Added `implementation(libs.snakeyaml)` in dependencies. |
| `app/src/main/AndroidManifest.xml` | Added `FileProvider` with authority `com.le0xff.plauncher.fileprovider`, `android:exported="false"`, `android:grantUriPermissions="true"`, and `meta-data` referencing `@xml/file_paths`. |
| `app/src/main/res/xml/file_paths.xml` (new) | Defined `<cache-path name="exports" path="exports/" />` for FileProvider to expose temporary YAML files. |
| `app/src/main/res/values/strings.xml` | Added 18 new strings: `settings_section_import_export`, `settings_import_export_desc`, `settings_yaml_example`, `button_import`, `button_export`, `import_success`, `import_failed`, `export_success`, `import_empty_file`, `import_skipped_apps`, `import_duplicate_packages`, `import_duplicate_positions`, `import_position_out_of_range`, `import_multiple_auto_launch`, `import_max_apps_exceeded`, `import_confirm_title`, `import_confirm_text`, `import_button_replace`, `import_warnings_title`. |
| `data/YamlExportImport.kt` (new) | Created utility object with `exportAppsToYaml()` (manual YAML generation) and `importAppsFromYaml()` (SnakeYAML parsing with full validation). Created `ImportResult` data class with 9 fields for structured results. |
| `data/AppDataStore.kt` | Added `import android.content.pm.PackageManager`. Added `exportAppsToYaml()` and `importAppsFromYaml()` methods delegating to `YamlExportImport`. |
| `ui/SettingsScreen.kt` | Added `onExportClick` and `onImportClick` parameters (default `{}`). Added `importExportExpanded` state. Added "Import/Export" accordion card between "Watchapp settings" and "Permissions" with description, YAML example in monospace, and two side-by-side buttons. |
| `MainActivity.kt` | Added imports for `PackageManager`, `Uri`, `FileProvider`, `File`, `YamlExportImport`, `rememberLauncherForActivityResult`, `ActivityResultContracts`. Added `importPendingResult` and `importWarningsResult` state variables. Added `importLauncher` using `StartActivityForResult()` with `ACTION_GET_CONTENT` (`*/*` MIME). Added `applyImportResult()` function that applies import to ViewModel, DataStore, and watch. Added `buildOriginalNames()` helper. Added `onExportClick` (writes YAML to temp file, shares via FileProvider). Added `onImportClick` (opens file picker). Added confirmation dialog and warnings dialog. |

#### Deprecation Fixes (pre-existing)

| File | Changes |
|---|---|
| `CrashReportActivity.kt` | Replaced `Divider()` with `HorizontalDivider()` (line 68). |
| `ui/AppScreen.kt` | Replaced `Divider(...)` with `HorizontalDivider(...)` (line 139). |
| `PebbleListenerService.kt` | Replaced `stopForeground(false)` with version-check: `STOP_FOREGROUND_REMOVE` for API 30+, `@Suppress("DEPRECATION") stopForeground(false)` for earlier. |
| `ui/Theme.kt` | Added `@Suppress("DEPRECATION")` to `window.statusBarColor` and `window.navigationBarColor` assignments. |

#### Key Implementation Details

**YAML format**:
The export generates YAML manually with 2-space indentation. Each entry has four fields:
```yaml
- package: "com.whatsapp"
  custom_name: ""
  position: 0
  auto_launch: false
```
The `custom_name` is only populated if the app's `displayName` differs from the original name (queried from `PackageManager`). This keeps the YAML minimal — unchanged apps have empty `custom_name`.

**Export with FileProvider** (`MainActivity.kt`):
```kotlin
val onExportClick: () -> Unit = {
    val originalNames = buildOriginalNames(apps)
    val yamlContent = dataStore.exportAppsToYaml(originalNames, autoLaunchTarget)
    val exportsDir = File(context.cacheDir, "exports")
    if (!exportsDir.exists()) exportsDir.mkdirs()
    val file = File(exportsDir, "plauncher_apps.yaml")
    file.writeText(yamlContent)
    val uri = FileProvider.getUriForFile(
        context, "com.le0xff.plauncher.fileprovider", file
    )
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/yaml"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    startActivity(Intent.createChooser(shareIntent, getString(R.string.button_export)))
}
```
The file is written to `cacheDir/exports/plauncher_apps.yaml` and shared via `FileProvider` with `EXTRA_STREAM`. This provides a valid URI that file managers (Amaze, etc.) can save correctly, unlike the previous `EXTRA_TEXT` approach.

**Import with file picker** (`MainActivity.kt`):
```kotlin
val importLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.StartActivityForResult()
) { result ->
    // Read file content, parse YAML, show confirmation dialog
}

val onImportClick: () -> Unit = {
    val intent = Intent(Intent.ACTION_GET_CONTENT).apply {
        type = "*/*"
        addCategory(Intent.CATEGORY_OPENABLE)
    }
    importLauncher.launch(intent)
}
```
Uses `StartActivityForResult()` with manual `ACTION_GET_CONTENT` and `*/*` MIME type because `GetContent()` with `text/yaml` failed to find `.yaml` files in common file managers. The `*/*` MIME type ensures all files are visible, and YAML validation happens after selection.

**Confirmation dialog**:
```kotlin
AlertDialog(
    title = { Text(stringResource(R.string.import_confirm_title)) },
    text = { Text(stringResource(R.string.import_confirm_text, result.apps.size)) },
    confirmButton = { TextButton(onClick = { applyImportResult(result) }) { ... } },
    dismissButton = { TextButton(onClick = { importPendingResult = null }) { ... } }
)
```
Shows "Replace app list" with the number of apps from the file. The YAML is parsed upfront but the import is not applied until the user confirms.

**Warnings dialog**:
```kotlin
AlertDialog(
    title = {
        Row { Text(text = "⚠️"); Spacer(8dp); Text("Import warnings") }
    },
    text = {
        Column {
            HorizontalDivider()
            // Each warning reason with HorizontalDivider, bold label,
            // and package names listed one per line in monospace
        }
    },
    confirmButton = { TextButton("Done") { ... } }
)
```
Title uses ⚠️ emoji + "Import warnings". Each warning reason is separated by `HorizontalDivider`. Labels are in `bodySmall` + `FontWeight.Medium`. Associated packages are listed one per line in monospace with `onSurfaceVariant` color.

**ImportResult data class** (`YamlExportImport.kt`):
```kotlin
data class ImportResult(
    val apps: List<LaunchApp>,
    val skippedPackages: List<String>,
    val duplicatePackages: List<String>,
    val duplicatePositionPackages: List<String>,
    val outOfRangePackages: List<String>,
    val multipleAutoLaunch: Boolean,
    val multipleAutoLaunchPackages: List<String>,
    val maxAppsExceeded: Boolean,
    val autoLaunchTarget: Int
)
```
Returns structured data instead of formatted strings, allowing MainActivity to localize all messages using `strings.xml`.

**Validation** (`YamlExportImport.kt`):
Six validation checks applied in order during the first pass:
1. **Max 20 apps**: Only first 20 valid entries are imported. Entries beyond are skipped.
2. **Duplicate packages**: First occurrence kept, duplicates tracked.
3. **Duplicate positions**: First occurrence kept, duplicates tracked.
4. **Out-of-range positions**: Positions >= 20 are rejected.
5. **Multiple auto-launch**: First `auto_launch: true` entry kept, all others tracked in `autoLaunchPackages`.
6. **Name resolution**: If `custom_name` is empty, name is resolved from `PackageManager`. If the package is not found, the app is skipped.

After validation, entries are sorted by original `position`, then positions are normalized (0 to N-1). The auto-launch target index is recalculated based on the normalized position.

**Auto-launch preservation** (`applyImportResult` in `MainActivity.kt`):
```kotlin
viewModel.setApps(result.apps)
viewModel.setAutoLaunchTarget(result.autoLaunchTarget)
dataStore.setAutoLaunchTarget(result.autoLaunchTarget)
dataStore.saveApps(result.apps)
coroutineScope.launch {
    senderHelper.sendAppList(result.apps, null)
    senderHelper.sendAutoLaunchTarget(result.autoLaunchTarget.toUInt())
}
```
The auto-launch target from the YAML is applied to ViewModel, DataStore, and sent to the watch, ensuring the setting is preserved across the import.

**String resources** (`strings.xml`):
All user-visible strings are externalized. The YAML example string uses `\u0020\u0020` for indentation (Android strips literal leading spaces in string resources):
```xml
<string name="settings_yaml_example">- package: "com.example.app"\n\u0020\u0020custom_name: "My App"\n\u0020\u0020position: 0\n\u0020\u0020auto_launch: false</string>
```

**Unchanged**: `LaunchApp` model, `PebbleSenderHelper` protocol, `PebbleListenerService`, `BootReceiver`, `CrashApplication`, `CrashReportActivity`, watch app (`pbw/`). All existing features remain fully functional.

#### Layout

Settings screen order:
```
┌─────────────────────────────────┐
│ Settings                        │
│                                 │
│ ▾ General                       │
│   Theme                         │
│   Show system apps              │
│                                 │
│ ▸ Watchapp settings             │
│   Vibration on launch           │
│   Auto-close on launch          │
│   Auto-launch on open           │
│                                 │
│ ▾ Import/Export                 │
│   Import or export the app list │
│   - package: "com.example.app"  │
│     custom_name: "My App"       │
│     position: 0                 │
│     auto_launch: false          │
│   [Import]      [Export]        │
│                                 │
│ ▸ Permissions                   │
│ ▸ Debug                         │
│                                 │
│               v1.0.0            │
└─────────────────────────────────┘
```

Import confirmation dialog:
```
┌──────────────────────────────┐
│ Replace app list             │
│                              │
│ Importing will replace your  │
│ current app list with 5 apps │
│ from the file. This action   │
│ cannot be undone.            │
│                              │
│     [Replace]    [Cancel]    │
└──────────────────────────────┘
```

Import warnings dialog:
```
┌──────────────────────────────┐
│ ⚠️ Import warnings           │
│ ──────────────────────────── │
│ ──────────────────────────── │
│ Some apps were skipped       │
│   com.uninstalled.app        │
│ ──────────────────────────── │
│ Duplicate packages skipped   │
│   com.duplicate.app          │
│ ──────────────────────────── │
│ Multiple auto-launch targets │
│   com.first.app              │
│   com.second.app             │
│ ──────────────────────────── │
│           [Done]             │
└──────────────────────────────┘
```

#### Code Statistics

| Component | Files | Lines changed |
|---|---|---|
| Watch App (C) | 0 | 0 (unchanged) |
| Android App (Kotlin) | 9 | ~250 (new utility 198 lines, gradle 2 lines, manifest 10 lines, xml 5 lines, strings 18 lines, AppDataStore 12 lines, SettingsScreen 40 lines, MainActivity 80 lines, deprecation fixes 8 lines) |
| **Total** | **9** | **~250** |

#### Build Status

- Watch app: `pebble build` — unchanged
- Android app: `./gradlew clean assembleDebug` — BUILD SUCCESSFUL, zero Kotlin compiler warnings

#### Known Issues Resolved

**FileProvider for export**: The initial implementation used `EXTRA_TEXT` with `ACTION_SEND`, which worked for messaging apps but failed with file managers (Amaze) that expect `EXTRA_STREAM` with a file URI. Fixed by writing YAML to a temporary file in `cacheDir/exports/` and sharing via `FileProvider`.

**File picker MIME type**: The initial implementation used `GetContent()` with `text/yaml` MIME type, which failed to find `.yaml` files in common file managers that don't register this MIME type. Fixed by using `StartActivityForResult()` with `ACTION_GET_CONTENT` and `*/*` MIME type, with YAML validation happening after file selection.

**Auto-launch target lost on import**: The initial implementation parsed `auto_launch: true` from YAML but never applied the target to the ViewModel, DataStore, or watch. Fixed by tracking the normalized index of the first `auto_launch: true` entry and applying it during `applyImportResult()`.

**Hardcoded warning strings**: The initial implementation formatted warning messages in `YamlExportImport.kt` with hardcoded English strings. Fixed by returning structured data (`ImportResult` with separate fields for each warning type) and formatting all messages in `MainActivity.kt` using localized string resources.

**Split Toast/dialog for warnings**: The initial implementation showed warnings as a mix of Toast and AlertDialog depending on count. Fixed by always showing a single AlertDialog with all warnings after import confirmation, with structured layout (emoji title, dividers, bold labels, monospace packages).

---

## #22 — APK Startup Performance and Scroll Optimization

### Overview

Eliminated the startup flash (light theme and empty list briefly visible before data loads), removed O(n²) complexity from the app list scroll, replaced heavy `Canvas` rendering with lightweight composables, cached icon bitmaps in the app picker dialog, and further optimized the `LazyColumn` to prevent per-item recompositions by hoisting shared state and extracting item content into a dedicated composable.

### Analysis

- **Startup flash**: The ViewModel was initialized with default values (`AppTheme.Light`, `emptyList()`), and Compose rendered the first frame with these defaults before `LaunchedEffect(Unit)` loaded persisted data from `AppDataStore`. Fixed by loading data synchronously in `onCreate()` before `setContent`, since `AppDataStore` uses `SharedPreferences` with synchronous reads populated in the constructor.
- **Scroll stutter — O(n²) `indexOf()`**: Two calls to `indexOf()` were executed per item per composition: `filtered.indexOf(app)` for divider logic and `apps.indexOf(app)` for auto-launch index. With up to 20 items, this produced ~400 operations per recomposition. Fixed by switching to `itemsIndexed()` (O(1) index from parameter) and building a `Map<String, Int>` (packageName → index) with `remember` for O(1) lookups.
- **Scroll stutter — Canvas per item**: Each list item contained a `Canvas` that drew circles for the auto-launch indicator. `Canvas` has higher composition and rendering cost than standard composables. Fixed by replacing with `Box` + `border()` for the outer ring and `Box` + `background()` for the inner dot.
- **AppPickerDialog — uncached bitmap conversion**: Every row converted `Drawable → Bitmap` on every recomposition. Fixed by wrapping in `remember(app.packageName)` to cache bitmaps per package.
- **Per-item recomposition — hoisted state**: `LocalContext.current`, `MaterialTheme.colorScheme`, and `MaterialTheme.typography` were read inside every item's lambda. Each read forces per-item recomposition when the source changes. Fixed by hoisting these reads to `AppScreen` and passing them as parameters to an extracted `AppListItem` composable. Additionally, icon bitmaps are cached in a persistent `mutableMapOf` remembered against `context`, so bitmaps survive viewport recycling (unlike per-item `remember` which loses cache when items scroll off-screen).

### Android Companion App (`apk/`) — ~50 lines changed across 4 files

#### Modified Files

| File | Changes |
|---|---|
| `MainActivity.kt` | Moved data loading from `LaunchedEffect(Unit)` inside `setContent` to `onCreate()` before `setContent`. Added 9 `viewModel.set*()` calls and `viewModel.setConnectionStatus()` using `getString(R.string.status_disconnected)` (Activity method, not composable). Removed `LaunchedEffect(Unit)` block and `dataStore` remember reference used only for initial load. |
| `ui/AppScreen.kt` | Added imports: `android.content.Context`, `androidx.compose.foundation.lazy.LazyItemScope`, `androidx.compose.foundation.border`, `androidx.compose.ui.graphics.ImageBitmap`, `sh.calvin.reorderable.ReorderableCollectionItemScope`. Removed imports: `androidx.compose.foundation.Canvas`, `androidx.compose.ui.graphics.drawscope.Stroke`, `androidx.compose.foundation.lazy.items`. Hoisted `colorScheme`, `typography`, `context`, `appIndexMap`, and `iconCache` outside `LazyColumn`. Replaced `items(filtered, ...)` with `itemsIndexed(filtered, ...)`. Replaced `filtered.indexOf(app)` with `index` parameter. Replaced `apps.indexOf(app)` with `appIndexMap[app.packageName]`. Replaced `Canvas` circle drawing with `Box` + `border()`/`background()`. Extracted item content into new `AppListItem` composable receiving hoisted parameters. Icon bitmaps cached in persistent `mutableMapOf` via `getOrPut`. |
| `ui/AppPickerDialog.kt` | Wrapped `Drawable → Bitmap` conversion in `remember(app.packageName)` to cache bitmaps per package, preventing recomputation on every recomposition. |
| `data/AppDataStore.kt` | Unchanged (already synchronous; methods used for pre-loading: `apps.value`, `getShowSystemApps()`, `getGenerateCrashReports()`, `getAppTheme()`, `getVibrationPref()`, `getAutoClose()`, `getAutoLaunchEnabled()`, `getAutoLaunchTarget()`). |

#### Key Implementation Details

**Startup data loading** (`MainActivity.kt`):
```kotlin
// In onCreate(), BEFORE setContent:
viewModel.setApps(appDataStore.apps.value)
viewModel.setShowSystemApps(appDataStore.getShowSystemApps())
viewModel.setGenerateCrashReports(appDataStore.getGenerateCrashReports())
viewModel.setAppTheme(appDataStore.getAppTheme())
viewModel.setVibrationPref(appDataStore.getVibrationPref())
viewModel.setAutoClose(appDataStore.getAutoClose())
viewModel.setAutoLaunchEnabled(appDataStore.getAutoLaunchEnabled())
viewModel.setAutoLaunchTarget(appDataStore.getAutoLaunchTarget())
viewModel.setConnectionStatus(getString(R.string.status_disconnected))
```
Uses `getString()` (Activity method) instead of `stringResource()` (composable) since we're outside Compose context.

**O(n²) elimination** (`ui/AppScreen.kt`):
```kotlin
// itemsIndexed provides index directly (O(1))
itemsIndexed(filtered, key = { _, app -> app.packageName }) { index, app ->
    if (index > 0) { ... } // was: filtered.indexOf(app) > 0
}

// Map lookup for auto-launch index (O(1))
val appIndexMap = remember(apps) {
    apps.mapIndexed { idx, app -> app.packageName to idx }.toMap()
}
val appIndex = appIndexMap[app.packageName] ?: -1 // was: apps.indexOf(app)
```

**Canvas replacement** (`ui/AppScreen.kt`):
```kotlin
// Was: Canvas with drawCircle(Stroke) + drawCircle(filled)
Box(
    modifier = Modifier.size(28.dp).border(2.dp, ringColor.copy(alpha = circleAlpha), CircleShape),
    contentAlignment = Alignment.Center
) {
    if (isAutoLaunchTarget) {
        Box(modifier = Modifier.size(12.dp).background(ringColor.copy(alpha = circleAlpha), CircleShape))
    }
}
```

**Hoisted state and extracted composable** (`ui/AppScreen.kt`):
```kotlin
// In AppScreen, OUTSIDE LazyColumn:
val colorScheme = MaterialTheme.colorScheme
val typography = MaterialTheme.typography
val context = LocalContext.current
val iconCache = remember(context) { mutableMapOf<String, ImageBitmap?>() }

// Inside itemsIndexed:
val iconBitmap = remember(app.packageName) {
    iconCache.getOrPut(app.packageName) { /* load icon */ }
}

AppListItem(
    app = app, iconBitmap = iconBitmap, isDragging = isDragging,
    colorScheme = colorScheme, typography = typography, ...
)
```
The `AppListItem` composable receives all shared values as parameters, scoping recomposition to only items whose parameters changed. The `iconCache` persists across viewport recycling.

**Picker bitmap caching** (`ui/AppPickerDialog.kt`):
```kotlin
val iconBitmap = remember(app.packageName) {
    app.icon?.toBitmap(48, 48)?.asImageBitmap()
}
```

#### Code Statistics

| Component | Files | Lines changed |
|---|---|---|
| Watch App (C) | 0 | 0 (unchanged) |
| Android App (Kotlin) | 4 | ~50 (MainActivity ~15 removed/added, AppScreen ~30 restructured, AppPickerDialog ~5 changed) |
| **Total** | **4** | **~50** |

#### Build Status

- Watch app: `pebble build` — unchanged
- Android app: `./gradlew assembleDebug` — BUILD SUCCESSFUL

#### Performance Impact

- **Startup flash**: Eliminated. First frame renders with correct theme and populated list.
- **Scroll complexity**: Reduced from O(n²) to O(n) per recomposition via `itemsIndexed()` and `Map` lookups.
- **Per-item composition cost**: Reduced by replacing `Canvas` with `Box`/`border`/`background`, hoisting `MaterialTheme`/`LocalContext` reads, and extracting `AppListItem` composable.
- **Picker performance**: Icon bitmaps cached per package, eliminating redundant `Drawable → Bitmap` conversions during scroll and search.
- **Icon cache persistence**: Bitmaps cached in a persistent `mutableMapOf` survive viewport recycling, unlike per-item `remember` which loses cache when items scroll off-screen.

#### Known Issues Resolved

**Startup flash**: The first Compose frame used ViewModel defaults. Fixed by loading persisted data in `onCreate()` before `setContent`.

**Scroll stutter from O(n²)**: Two `indexOf()` calls per item per composition. Fixed with `itemsIndexed()` and `Map` lookups.

**Canvas overhead**: `Canvas` per item caused composition lag on scroll. Fixed with lightweight `Box` composables.

**Picker bitmap recomputation**: Every recomposition reconverted all visible icons. Fixed with `remember` caching.

**Per-item recomposition cascade**: `MaterialTheme` and `LocalContext` reads inside every item caused all visible items to recompose together on theme/context changes. Fixed by hoisting reads and extracting `AppListItem` with parameterized recomposition scoping.

---

## #23 — Save Logs Debug Button

### Overview

Added a "Save Logs" button in the Debug section of the companion app's Settings screen. Pressing the button saves the current session's in-memory log buffer to a `.txt` file and shares it via Android's share intent, following the same `FileProvider` pattern as the YAML export. The log buffer (`AppLogBuffer`) collects significant events throughout the app lifecycle, including app startup, Pebble connection/disconnection, launch requests, import/export operations, and crashes.

### Analysis

- **Previous state**: The companion app had no internal logging mechanism. The Debug section only contained a crash reports switch and a test crash button. Debugging issues required relying solely on `adb logcat`, which is impractical for field testing.
- **Log buffer**: Created `AppLogBuffer` as a Kotlin `object` (singleton) with a 500-entry FIFO queue backed by `CopyOnWriteArrayList` with synchronized access for thread safety. Each entry records timestamp (formatted `yyyy-MM-dd HH:mm:ss.SSS`), level (`INFO`, `WARN`, `ERROR`, `DEBUG`), tag (component name), and message. Logs are volatile — not persisted between sessions.
- **Export**: Writes `plauncher_logs.txt` to `cacheDir/exports/` (reusing the existing FileProvider `<cache-path>`), and shares via `Intent.ACTION_SEND` with `text/plain` MIME type. Empty buffer shows a Toast. Errors during save show a Toast.
- **Instrumentation**: Key points across `MainActivity`, `PebbleListenerService`, `CrashApplication`, and `LaunchActivity` are instrumented with `AppLogBuffer.info/warn/error/debug` calls using descriptive tags.

### Android Companion App (`apk/`) — ~100 lines changed across 6 files

#### Modified Files

| File | Changes |
|---|---|
| `app/src/main/java/com/le0xff/plauncher/data/AppLogBuffer.kt` (new) | Created singleton `AppLogBuffer` with `LogEntry` data class, 500-entry FIFO buffer (`CopyOnWriteArrayList` + `synchronized`), `info/warn/error/debug` methods, `getEntries()`, `getLogsAsString()` (formatted as `[timestamp] LEVEL/TAG: message`), and `clear()`. |
| `app/src/main/res/values/strings.xml` | Added 6 new strings: `button_save_logs` ("Save"), `settings_save_logs` ("Save logs"), `settings_save_logs_desc` ("Save current session logs to a text file"), `logs_saved_success` ("Logs saved successfully"), `logs_saved_error` ("Failed to save logs"), `logs_empty` ("No logs available"). |
| `ui/SettingsScreen.kt` | Added `onSaveLogsClick` parameter (default `{}`). Added "Save logs" row in Debug accordion between crash reports switch and crash button, with title, description, and "Save" button. |
| `MainActivity.kt` | Added import for `AppLogBuffer`. Added `onSaveLogsClick` lambda that reads buffer, checks empty, writes `plauncher_logs.txt` to `cacheDir/exports/`, obtains URI via `FileProvider`, and shares via `Intent.ACTION_SEND`. Passed `onSaveLogsClick` to `SettingsScreen`. Added log calls: `onCreate` (app started), `onResume` (activity resumed), export initiated, import initiated, import parsed/applied, save logs initiated. |
| `PebbleListenerService.kt` | Added import for `AppLogBuffer`. Added log calls: `onCreate` (service created), `onDestroy` (service destroyed), `handleWatchWelcome` (watch connected), `onAppClosed` (watch disconnected), `handleLaunchApp` (launch request for index), `onMessageReceived` catch block (error processing message), unknown packet type. |
| `CrashApplication.kt` | Added import for `AppLogBuffer`. Added log call in uncaught exception handler (exception type and message). |
| `LaunchActivity.kt` | Added import for `AppLogBuffer`. Added log calls: missing package name (warn), launching package, launch success, launch failed (error), no launch intent (warn). |

#### Key Implementation Details

**AppLogBuffer singleton** (`data/AppLogBuffer.kt`):
```kotlin
object AppLogBuffer {
    private val _entries = CopyOnWriteArrayList<LogEntry>()
    private const val MAX_ENTRIES = 500

    fun info(tag: String, message: String) = add("INFO", tag, message)
    fun warn(tag: String, message: String) = add("WARN", tag, message)
    fun error(tag: String, message: String) = add("ERROR", tag, message)
    fun debug(tag: String, message: String) = add("DEBUG", tag, message)

    fun getLogsAsString(): String {
        return _entries.joinToString("\n") { entry ->
            "[${entry.timestamp}] ${entry.level}/${entry.tag}: ${entry.message}"
        }
    }
}
```
Thread-safe via `CopyOnWriteArrayList` + `synchronized` on mutable operations. FIFO eviction at 500 entries. Output format: `[2026-08-01 12:00:00.000] INFO/MainActivity: App started`.

**Save logs callback** (`MainActivity.kt`):
```kotlin
val onSaveLogsClick: () -> Unit = onSaveLogs@{
    val entries = AppLogBuffer.getEntries()
    if (entries.isEmpty()) {
        Toast.makeText(context, R.string.logs_empty, Toast.LENGTH_SHORT).show()
        return@onSaveLogs
    }
    val logContent = AppLogBuffer.getLogsAsString()
    val exportsDir = File(context.cacheDir, "exports")
    if (!exportsDir.exists()) exportsDir.mkdirs()
    val file = File(exportsDir, "plauncher_logs.txt")
    file.writeText(logContent)
    val uri = FileProvider.getUriForFile(
        context, "com.le0xff.plauncher.fileprovider", file
    )
    val shareIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    startActivity(Intent.createChooser(shareIntent, getString(R.string.button_save_logs)))
}
```
Follows the exact same pattern as `onExportClick`: write to `cacheDir/exports/`, get URI via `FileProvider`, share with `ACTION_SEND`. Reuses existing `file_paths.xml` `<cache-path>` configuration.

**Instrumentation points**:
| Component | Events Logged |
|---|---|
| `MainActivity` | App started, activity resumed, export initiated, import initiated, import parsed (app count), import applied (app count), save logs initiated |
| `PebbleListenerService` | Service created/destroyed, watch connected/disconnected, launch request (index), unknown packet type, message processing error |
| `CrashApplication` | Uncaught exception (type + message) |
| `LaunchActivity` | Missing package name, launching package, launch success, launch failed, no launch intent |

**Debug section layout**:
```
┌─────────────────────────────────┐
│ ▾ Debug                         │
│   Generate crash reports [switch]│
│   ──────────────────────────── │
│   Save logs                     │
│   Save current session logs...  │
│                        [Save]   │
│   ──────────────────────────── │
│   Crash the application         │
│   Triggers a test crash...      │
│                    [Crash]      │
└─────────────────────────────────┘
```

#### Code Statistics

| Component | Files | Lines changed |
|---|---|---|
| Watch App (C) | 0 | 0 (unchanged) |
| Android App (Kotlin) | 6 | ~100 (AppLogBuffer ~55 new lines, strings 6 lines, SettingsScreen ~18 lines, MainActivity ~20 lines, PebbleListenerService ~8 lines, CrashApplication ~2 lines, LaunchActivity ~5 lines) |
| **Total** | **6** | **~100** |

#### Build Status

- Watch app: `pebble build` — unchanged
- Android app: `./gradlew assembleDebug` — BUILD SUCCESSFUL

#### Design Decisions

**Volatile buffer**: Logs are in-memory only, not persisted to storage. This avoids permission complexity and keeps logs relevant to the current session (the typical debugging scenario).

**500-entry cap**: Balances between capturing enough history for debugging and avoiding unbounded memory growth. FIFO eviction ensures the most recent entries are always available.

**Thread safety**: The buffer is accessed from both the main thread (Activities) and background threads (Service message handling). `CopyOnWriteArrayList` + `synchronized` on mutations ensures safe concurrent access without external coordination.

**Reused FileProvider**: The existing `file_paths.xml` already defines `<cache-path name="exports" path="exports/">`, so no manifest or XML changes were needed.

**No-op safe**: `AppLogBuffer` calls never throw exceptions, so instrumentation cannot break existing flows.

---

## #24 — App Icons in Watchapp (Color and B/W)

### Overview

Added 32×32 app icons to the Pebble watchapp, transmitted from the Android companion app via AppMessage. The system supports **two icon formats** for future-proofing: Color (`GBitmapFormat8Bit`, 1,024 bytes/icon) for 64-color displays (basalt, emery), and B/W (`GBitmapFormat1Bit`, 128 bytes/icon) for monochrome displays (aplite, diorite, flint). The watch communicates its display capabilities in the Watch Welcome message. The phone stores both versions of every icon, converts Android app icons using `IconConverter`, and sends the correct format based on the watch's display type. The icon is displayed centered horizontally within the name area, vertically centered above the app name, with `GCompOpSet` compositing for transparency on color displays.

### Analysis

- **Previous state**: The watchapp displayed only app names and index. No visual identification of apps was possible beyond text.
- **Icon size**: 32×32 chosen as the best compromise between visibility and resource constraints. 24×24 was too small on both displays. 48×48 would require icon chunking (exceeding AppMessage limits) and consume excessive RAM on basalt. 32×32 fits within the AppMessage buffer (1,024 B icon + ~120 B header = ~1,144 B < 2,048 B buffer) and leaves sufficient heap.
- **Color format (GColor8)**: Each pixel = 1 byte `0bAARRGGBB` (AA=alpha 2-bit, RR=red 2-bit, GG=green 2-bit, BB=blue 2-bit). 32×32 = 1,024 bytes. Android conversion: scale to 32×32, quantize ARGB_8888 → GColor8 (`r8=(r>>6)&3`, `g8=(g>>6)&3`, `b8=(b>>6)&3`, `a8=if(a>=128) 3 else 0`).
- **B/W format (1-bit)**: Each pixel = 1 bit (MSB first), rows padded to 4 bytes (32-bit alignment). 32 pixels = 4 bytes/row × 32 rows = 128 bytes. Android conversion: scale to 32×32, luminance threshold (`Y = 0.299R + 0.587G + 0.114B`, `Y>=128 → 1`), pack row-major with padding.
- **Memory watch**: Basalt (color): 20 apps × (1,024 + 1 + 32 + 64) = 22,860 B icons + metadata. Total RAM footprint ~29,165 B, free heap ~36,371 B (within 64 KB). Emery (color): same footprint, 128 KB heap — no concern.
- **AppMessage**: Increased buffer from 1,024 to 2,048 bytes to accommodate 32×32 color icons (1,024 B) plus name, package, and dictionary header (~120 B) within a single message.
- **Persistence**: Both icon formats stored in SharedPreferences as hex-encoded strings. Backward-compatible: old records without icons (2 fields) load with null icons; new records (4 fields) decode both formats.
- **Icon regeneration**: On each watch connection, icons are regenerated from Android's `PackageManager` using `IconConverter.getAppIconBitmaps()`, ensuring icons reflect current app states.
- **Display type negotiation**: Watch sends `KEY_DISPLAY_TYPE` (key 15) in Watch Welcome: 1 for color, 0 for B/W. Phone reads this value and selects the correct icon format for `KEY_APP_ICON` (key 16).

### Watch App (`pbw/`) — ~100 lines changed across 6 files

#### Modified Files

| File | Changes |
|---|---|
| `COMMUNICATION_PROTOCOL.md` | Added key 15 (`KEY_DISPLAY_TYPE`, UInt8, Watch→Phone) and key 16 (`KEY_APP_ICON`, Data, Phone→Watch). Added Icon Formats section documenting Color (GColor8, 1,024 B) and B/W (1-bit, 128 B) formats. Updated key 16 description with 32×32 dimensions and byte counts. |
| `src/c/packets.h` | Added `#define KEY_DISPLAY_TYPE 15` and `#define KEY_APP_ICON 16`. |
| `src/c/packets.c` | Added `dict_write_uint8(iter, KEY_DISPLAY_TYPE, PBL_IF_COLOR_ELSE(1, 0))` to `send_watch_welcome()`. Added `APP_LOG` for display type sent. In `handle_app_list()`: extract `KEY_APP_ICON` tuple, verify length, pass to `app_list_add()`. In `packets_init()`: increased `app_message_open(1024, 1024)` to `app_message_open(2048, 2048)`. |
| `src/c/app_list.h` | Added `#include <pebble.h>`. Added icon constants: `APP_ICON_COLOR_SIZE` (1,024), `APP_ICON_BW_SIZE` (128), `APP_ICON_SIZE` (conditional), `APP_ICON_WIDTH` (32), `APP_ICON_HEIGHT` (32). Added `icon[APP_ICON_SIZE]` and `has_icon` fields to `LaunchApp` struct. Added `app_list_get_icon()` and `app_list_has_icon()` declarations. Updated `app_list_add()` signature to accept `icon_data` and `icon_len`. |
| `src/c/app_list.c` | Added `#include <pebble.h>`. Added `app_list_get_icon()` and `app_list_has_icon()` implementations. Updated `app_list_add()` to copy icon data when `icon_len == APP_ICON_SIZE`, set `has_icon` flag. Added `APP_LOG` for icon stored/rejected with size mismatch debug info. |
| `src/c/layout.h` | Added `LAYOUT_ICON_SIZE` (32) and `LAYOUT_ICON_V_PADDING` (8). |
| `src/c/window_main.c` | Added `BitmapLayer* s_icon_layer` and `GBitmap* s_icon_bitmap` static variables. In `window_load()`: create `GBitmap` with `PBL_IF_COLOR_ELSE(GBitmapFormat8Bit, GBitmapFormat1Bit)`, create `BitmapLayer`, set `GCompOpSet` compositing, set `GColorClear` background. In `window_unload()`: destroy bitmap layer and bitmap. In `window_main_update_display()`: dynamic layout with icon centered on name area (`(w_name - LAYOUT_ICON_SIZE) / 2`), vertically centered with text below. Copy icon data to bitmap when `has_icon`, hide layer otherwise. Added `APP_LOG` for icon displayed/hidden. |

#### Key Implementation Details

**Conditional icon size** (`app_list.h`):
```c
#define APP_ICON_COLOR_SIZE 1024  // 32x32, 1 byte/pixel
#define APP_ICON_BW_SIZE    128   // 32x32, 1-bit, 4-byte row padding
#define APP_ICON_SIZE PBL_IF_COLOR_ELSE(APP_ICON_COLOR_SIZE, APP_ICON_BW_SIZE)
```
Uses `PBL_IF_COLOR_ELSE` to select the correct buffer size at compile time. Color platforms allocate 1,024 bytes per icon; B/W platforms allocate 128 bytes.

**Conditional GBitmap format** (`window_main.c`):
```c
s_icon_bitmap = gbitmap_create_blank(
    GSize(APP_ICON_WIDTH, APP_ICON_HEIGHT),
    PBL_IF_COLOR_ELSE(GBitmapFormat8Bit, GBitmapFormat1Bit)
);
bitmap_layer_set_compositing_mode(s_icon_layer, GCompOpSet);
bitmap_layer_set_background_color(s_icon_layer, GColorClear);
```
`GCompOpSet` ensures alpha transparency works correctly on color displays (pixels with alpha=0 are transparent).

**Dynamic layout** (`window_main.c`):
```c
int icon_x = (w_name - LAYOUT_ICON_SIZE) / 2;
int icon_y = (h - LAYOUT_NAME_FONT_HEIGHT - LAYOUT_ICON_SIZE - LAYOUT_ICON_V_PADDING * 2) / 2;
int text_y = icon_y + LAYOUT_ICON_SIZE + LAYOUT_ICON_V_PADDING;
```
Icon centered horizontally within the name area (not full screen). Vertically, icon + padding + text are centered within the screen height.

**Icon validation** (`packets.c` / `app_list.c`):
```c
// packets.c: extract and pass icon
Tuple* iconTuple = dict_find(iter, KEY_APP_ICON);
if (iconTuple) {
    const uint8_t* iconData = iconTuple->value->data;
    uint16_t iconLen = iconTuple->length;
    app_list_add(name, package, iconData, iconLen);
}

// app_list.c: accept only correct size
if (icon_data != NULL && icon_len == APP_ICON_SIZE) {
    memcpy(s_apps[s_count].icon, icon_data, APP_ICON_SIZE);
    s_apps[s_count].has_icon = true;
} else {
    s_apps[s_count].has_icon = false;
}
```
Icons with mismatched sizes are silently rejected (has_icon = false), preventing corruption from stale or incompatible icon data.

**AppMessage buffer increase** (`packets.c`):
```c
app_message_open(2048, 2048);  // was 1024, 1024
```
Required because 32×32 color icons (1,024 B) plus dictionary header (~120 B) exceed the original 1,024 B buffer. 2,048 B is the maximum supported by Pebble SDK.

### Android Companion App (`apk/`) — ~150 lines changed across 6 files

#### Modified Files

| File | Changes |
|---|---|
| `model/LaunchApp.kt` | Added `val iconColorData: ByteArray? = null` (1,024 bytes GColor8) and `val iconBwData: ByteArray? = null` (128 bytes 1-bit padded). Default null maintains backward compatibility with existing code. |
| `util/IconConverter.kt` (new) | Created utility object with `convertToPebbleColorIcon()` (32×32 GColor8, 1,024 bytes), `convertToPebbleBwIcon()` (32×32 1-bit padded, 128 bytes), and `getAppIconBitmaps()` (retrieves icon from PackageManager, handles `AdaptiveIconDrawable`, converts both formats). Added `AppLogBuffer.debug` for conversion results. |
| `data/AppDataStore.kt` | Added hex encoding utilities (`bytesToHex`, `hexToBytes` with `decodeDigit`). Updated `saveApps()` to store `packageName|displayName|colorHex|bwHex`. Updated `loadApps()` to handle both 2-field (legacy, no icons) and 4-field (with icons) records. Added `refreshIcons()` that regenerates icons from `PackageManager` for all apps, persists, and returns updated list. |
| `PebbleSenderHelper.kt` | Added `var watchDisplayType: Int = 1` property (1=color, 0=B/W). In `sendAppListChunks()`: select icon data based on `watchDisplayType` (`app.iconColorData` for color, `app.iconBwData` for B/W), include as `PebbleDictionaryItem.Bytes` in key 16. Added `AppLogBuffer.info` for send summary. |
| `PebbleListenerService.kt` | In `handleWatchWelcome()`: read `KEY_DISPLAY_TYPE` (key 15) from incoming dictionary, set `senderHelper.watchDisplayType`. Call `dataStore.refreshIcons(packageManager)` before sending app list. Added `AppLogBuffer.info` for display type and icon refresh summary. In `updateReceiver`: also call `refreshIcons()` before resending app list. |

#### Key Implementation Details

**Icon conversion — Color** (`IconConverter.kt`):
```kotlin
fun convertToPebbleColorIcon(bitmap: Bitmap): ByteArray {
    val scaled = Bitmap.createScaledBitmap(bitmap, 32, 32, true)
    val pixels = IntArray(32 * 32)
    scaled.getPixels(pixels, 0, 32, 0, 0, 32, 32)
    scaled.recycle()
    val result = ByteArray(32 * 32)
    for (i in pixels.indices) {
        val a8 = if (Color.alpha(pixels[i]) >= 128) 3 else 0
        val r8 = (Color.red(pixels[i]) shr 6) and 3
        val g8 = (Color.green(pixels[i]) shr 6) and 3
        val b8 = (Color.blue(pixels[i]) shr 6) and 3
        result[i] = ((a8 shl 6) or (r8 shl 4) or (g8 shl 2) or b8).toByte()
    }
    return result
}
```
Scales to 32×32, quantizes ARGB_8888 to GColor8 (4 colors per channel = 16 colors × 2 alpha levels = 32 possible pixel values).

**Icon conversion — B/W** (`IconConverter.kt`):
```kotlin
fun convertToPebbleBwIcon(bitmap: Bitmap): ByteArray {
    val scaled = Bitmap.createScaledBitmap(bitmap, 32, 32, true)
    val result = ByteArray(32 * 4)  // 32px → 4 bytes/row (padded to 32-bit alignment)
    for (y in 0 until 32) {
        for (x in 0 until 32) {
            val luminance = 0.299 * R + 0.587 * G + 0.114 * B
            val bit = if (luminance >= 128) 1 else 0
            val byteOffset = y * 4 + (x shr 3)
            result[byteOffset] = (result[byteOffset] or (bit shl (7 - (x and 7)))).toByte()
        }
    }
    return result
}
```
Scales to 32×32, threshold luminance, pack MSB-first. 32 pixels fit exactly in 4 bytes per row (no extra padding needed).

**Persistence with backward compatibility** (`AppDataStore.kt`):
```kotlin
// Save: packageName|displayName|colorHex|bwHex
val lines = apps.joinToString("\n") { app ->
    "${app.packageName}|${app.displayName}|${bytesToHex(app.iconColorData)}|${bytesToHex(app.iconBwData)}"
}

// Load: handle 2-field (legacy) and 4-field (with icons)
return@mapNotNull when (parts.size) {
    2 -> LaunchApp(parts[0], parts[1])
    4 -> LaunchApp(parts[0], parts[1], hexToBytes(parts[2]), hexToBytes(parts[3]))
    else -> null
}
```
Old records without icon data (2 fields) load with null icons. New records (4 fields) decode both formats. Empty hex fields decode to null.

**Display type negotiation** (`PebbleListenerService.kt`):
```kotlin
val displayTypeItem = data[15u]
val displayType = when (displayTypeItem) {
    is PebbleDictionaryItem.UInt32 -> displayTypeItem.value.toInt()
    is PebbleDictionaryItem.Int32  -> displayTypeItem.value
    else -> 1  // default to color
}
senderHelper.watchDisplayType = displayType
```
Reads key 15 from Watch Welcome. Defaults to color (1) if not present (backward-compatible with older watches).

**Icon selection** (`PebbleSenderHelper.kt`):
```kotlin
val iconData = if (watchDisplayType == 1) app.iconColorData else app.iconBwData
if (iconData != null) {
    put(16u, PebbleDictionaryItem.Bytes(iconData))
}
```
Selects the correct format based on display type. `PebbleDictionaryItem.Bytes` serializes binary data in the AppMessage dictionary.

#### Debug Logging

| Component | Events Logged |
|---|---|
| `packets.c` | Watch welcome sent (display type), icon received (length vs. expected), app list chunk received |
| `app_list.c` | Icon stored (app name, length), icon rejected (mismatched size) |
| `window_main.c` | Icon displayed (app name), no icon (app name) |
| `PebbleListenerService.kt` | Watch display type (Color/B/W), icons refreshed (count, with-color, with-B/W) |
| `PebbleSenderHelper.kt` | App list sent (app count, icon count, format, bytes per icon) |
| `IconConverter.kt` | Icon converted (package name, byte sizes), icon not found (package name) |

#### Layout

Watch display with icon:
```
┌─────────────────────────────────┐
│                                 │
│                                 │
│          [ICON 32×32]           │
│                                 │
│         App Name                │
│                                 │
│                                 │
│        1/5         UP           │
│                                 │
│                    LAUNCH       │
│                                 │
│                    DOWN         │
│                                 │
└─────────────────────────────────┘
```
Icon centered on name area (left portion), vertically centered with text below. Right column contains navigation labels.

#### Code Statistics

| Component | Files | Lines changed |
|---|---|---|
| Watch App (C) | 6 | ~100 (packets.h 2, packets.c 10, app_list.h 10, app_list.c 20, layout.h 2, window_main.c 30, protocol doc 15, debug logs 15) |
| Android App (Kotlin) | 6 | ~150 (LaunchApp.kt 2, IconConverter.kt 95 new, AppDataStore.kt 30, PebbleSenderHelper.kt 10, PebbleListenerService.kt 12, debug logs 10) |
| **Total** | **12** | **~250** |

#### Build Status

- Watch app: `pebble build` — BUILD SUCCESSFUL. Basalt: 29,165 B RAM / 64 KB, 36,371 B free heap. Emery: 29,165 B RAM / 128 KB, 111,291 B free heap.
- Android app: `./gradlew assembleDebug` — BUILD SUCCESSFUL, zero Kotlin compiler warnings

#### Design Decisions

**32×32 uniform size**: Chosen over per-device sizing (32 basalt / 48 emery) to avoid icon chunking complexity. 32×32 color icons (1,024 B) fit within the 2,048 B AppMessage buffer with ~40% headroom for name + package + header.

**AppMessage buffer 2,048 B**: Required for 32×32 color icons. The original 1,024 B buffer was insufficient. 2,048 B is the Pebble SDK maximum.

**Both formats stored**: Phone stores both color and B/W versions of every icon. This allows sending the correct format based on display type, and future-proofs for B/W platforms without requiring icon regeneration.

**Icon regeneration on connection**: Icons are regenerated from `PackageManager` on each watch connection, ensuring they reflect current app states (e.g., if an app is uninstalled, the icon gracefully becomes null).

**Hex encoding for persistence**: Binary icon data is stored as hex strings in SharedPreferences, avoiding base64 dependency and keeping the serialization simple.

**Graceful degradation**: Apps without icons still display correctly (hidden icon layer, name + index as before). Icon size mismatches are silently rejected, preventing corruption from stale data.

**AdaptiveIconDrawable handling**: Android adaptive icons are rendered to a bitmap using the full drawable (both foreground and background layers), ensuring consistent appearance across icon types.

---

## #25 — Watchapp Installation via Companion App

### Overview

Integrated the watchapp installation mechanism into the pLauncher companion app, inspired by [Pebble Steer](https://github.com/bquelhas/pebble-steer). The compiled `.pbw` is automatically bundled into the APK at build time, and installed on the watch via a button in the Settings screen. The section also displays the bundled watchapp's version and MD5 hash.

### Analysis

- **Previous state**: The companion app had no mechanism to install the watchapp. Users had to manually build, locate, and share the `.pbw` file to the Pebble/Core Devices app.
- **Reference implementation**: [Pebble Steer](https://github.com/bquelhas/pebble-steer) implements `.pbw` installation with a Gradle `bundleWatchPbw` task (Copy), `PbwInstaller.kt` (runtime install), UI button, manifest queries, and `file_paths.xml`. Steer does not expose version/MD5 information.
- **Build integration**: Three Gradle tasks automate the build: `buildWatchapp` (Exec, runs `pebble build`), `bundleWatchPbw` (Copy, copies `.pbw` to assets), `generatePbwInfo` (generates `pbw_info.txt` with version from `appinfo.json` and MD5 from `.pbw`). The chain is mandatory — if `pebble build` fails, the APK build fails.
- **Runtime installation**: `PbwInstaller` copies the bundled `.pbw` from assets to `cacheDir/pbw/`, exposes it via `FileProvider`, and launches `ACTION_VIEW` with `application/octet-stream` MIME type. The system shows a chooser to select the Pebble or Core Devices app.
- **Version/MD5 display**: `pbw_info.txt` is generated at build time with `key=value` format. At runtime, `PbwInstaller.getInfo()` reads and parses it. The Settings screen displays both values with aligned labels and monospace values.

### Android Companion App (`apk/`) — ~110 lines changed across 7 files

#### Modified Files

| File | Changes |
|---|---|
| `app/build.gradle.kts` | Added imports `java.security.MessageDigest`, `java.util.regex.Pattern`. Added three Gradle tasks: `buildWatchapp` (Exec, runs `pebble build` in `pbw/` directory), `bundleWatchPbw` (Copy, copies `.pbw` to `assets/` as `plauncher.pbw`), `generatePbwInfo` (DefaultTask, extracts `versionLabel` from `appinfo.json` via regex, computes MD5 of `.pbw`, writes `pbw_info.txt`). All tasks are hard dependencies of `merge*Assets`. Path resolution uses `rootProject.projectDir.parentFile?.resolve("pbw")` to correctly locate `pbw/` from the `app/` subproject. |
| `app/src/main/assets/.gitkeep` (new) | Empty file to keep the `assets/` directory in git. |
| `app/src/main/java/com/le0xff/plauncher/PbwInstaller.kt` (new) | Created `object PbwInstaller` with `isBundled()`, `getInfo()`, `stage()`, `install()` functions. Created `data class PbwInfo(version, md5)`. Installation stages the `.pbw` to `cacheDir/pbw/`, obtains URI via `FileProvider`, and launches `ACTION_VIEW` with a chooser. |
| `app/src/main/res/values/strings.xml` | Added 7 new strings: `settings_section_install_watchapp`, `settings_install_watchapp_desc`, `button_install_watchapp`, `install_watchapp_missing`, `install_watchapp_none`, `install_watchapp_chooser`. |
| `app/src/main/res/xml/file_paths.xml` | Added `<cache-path name="pbw" path="pbw/" />` alongside existing exports path. |
| `app/src/main/AndroidManifest.xml` | Added `<queries>` entry for `ACTION_VIEW` with `application/octet-stream` MIME type, required for `resolveActivity()` on Android 11+. |
| `ui/SettingsScreen.kt` | Added imports for `Toast`, `PbwInstaller`. Added `installExpanded` state. Added "Install watchapp" accordion card between Permissions and Debug with description, version/MD5 (aligned rows with `widthIn(min = 60.dp)` on labels, monospace values, `Modifier.weight(1f)`), and Install button. Button handles three cases: pbw missing (Toast), no handler app (Toast), success (silently launches chooser). |

#### Key Implementation Details

**Gradle build tasks** (`build.gradle.kts`):
```kotlin
val buildWatchapp = tasks.register<Exec>("buildWatchapp") {
    workingDir = pbwDir
    commandLine = listOf("pebble", "build")
}

val bundleWatchPbw = tasks.register<Copy>("bundleWatchPbw") {
    from(watchPbw)
    into(layout.projectDirectory.dir("src/main/assets"))
    rename { "plauncher.pbw" }
    dependsOn(buildWatchapp)
}

val generatePbwInfo = tasks.register("generatePbwInfo") {
    dependsOn(buildWatchapp)
    doLast {
        // Extract versionLabel from appinfo.json via regex
        // Compute MD5 of pbw.pbw via MessageDigest
        // Write pbw_info.txt with version=... and md5=...
    }
}

tasks.matching { it.name.startsWith("merge") && it.name.endsWith("Assets") }.configureEach {
    dependsOn(bundleWatchPbw)
    dependsOn(generatePbwInfo)
}
```
The dependency chain is mandatory: `merge*Assets` → `bundleWatchPbw` + `generatePbwInfo` → `buildWatchapp`. If `pebble build` fails, the APK build fails.

**PbwInstaller** (`PbwInstaller.kt`):
```kotlin
object PbwInstaller {
    fun isBundled(context: Context): Boolean
    fun getInfo(context: Context): PbwInfo
    fun stage(context: Context): File?
    fun install(context: Context): Boolean
}
```
`isBundled()` checks asset existence. `getInfo()` parses `pbw_info.txt` with `runCatching`, returns `"unknown"` on failure. `stage()` copies to `cacheDir/pbw/`. `install()` stages, builds FileProvider URI, checks for handler via `resolveActivity()`, launches chooser.

**Version/MD5 alignment** (`SettingsScreen.kt`):
```kotlin
Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    Text(text = "Version: ", ..., modifier = Modifier.widthIn(min = 60.dp))
    Text(text = pbwInfo.version, ..., fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
}
Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
    Text(text = "MD5:     ", ..., modifier = Modifier.widthIn(min = 60.dp))
    Text(text = pbwInfo.md5, ..., fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
}
```
Both labels use `widthIn(min = 60.dp)` for equal width. Values use monospace font with `weight(1f)` for consistent alignment.

**Install button behavior**:
```kotlin
when {
    !PbwInstaller.isBundled(context) -> Toast(..., R.string.install_watchapp_missing, ...)
    !PbwInstaller.install(context)   -> Toast(..., R.string.install_watchapp_none, ...)
    else -> PbwInstaller.install(context)  // silently launches chooser
}
```
Success case is silent (no toast). Only error cases show feedback.

#### Layout

Settings screen order:
```
┌─────────────────────────────────┐
│ Settings                        │
│                                 │
│ ▸ General                       │
│ ▸ Watchapp settings             │
│ ▸ Import/Export                 │
│ ▸ Permissions                   │
│ ▸ Install watchapp              │
│   Installs the bundled...       │
│   Version: 1.0.0                │
│   MD5: f9ebd622cb5efac...      │
│   [Install]                     │
│ ▸ Debug                         │
│                                 │
│               v1.0.0            │
└─────────────────────────────────┘
```

#### Code Statistics

| Component | Files | Lines changed |
|---|---|---|
| Watch App (C) | 0 | 0 (unchanged) |
| Android App (Kotlin) | 7 | ~110 (build.gradle.kts ~40, PbwInstaller.kt 64 new, strings 7 lines, file_paths.xml 1 line, manifest 4 lines, SettingsScreen ~25 lines, assets 1 line) |
| **Total** | **7** | **~110** |

#### Build Status

- Watch app: `pebble build` — invoked automatically by `buildWatchapp` Gradle task
- Android app: `./gradlew clean assembleDebug` — BUILD SUCCESSFUL
- APK contains `assets/plauncher.pbw` and `assets/pbw_info.txt`
- MD5 in `pbw_info.txt` matches `md5sum` of source `.pbw`

#### Design Decisions

**Mandatory build dependency**: `buildWatchapp` is a hard dependency of `bundleWatchPbw` and `generatePbwInfo`. If `pebble build` fails (e.g., compilation error), the APK build fails. This ensures the bundled `.pbw` always matches the current source.

**Build-time info file**: Version and MD5 are computed at build time and written to `pbw_info.txt`. At runtime, the app reads this simple `key=value` file instead of decompressing the ZIP or computing hash. This avoids runtime dependencies and keeps the UI responsive.

**Silent success**: The Install button's success case (chooser launched) produces no toast, avoiding redundant feedback. Only error cases (missing pbw, no handler app) show Toast messages.

**Aligned version/MD5**: Labels use fixed minimum width (`60.dp`) so values start at the same horizontal position. Values use monospace font for readability of the MD5 hash.

**Reference**: Inspired by [Pebble Steer](https://github.com/bquelhas/pebble-steer) (`PbwInstaller.kt`, `bundleWatchPbw` task, manifest queries, `file_paths.xml`). Extended with version/MD5 display and mandatory build integration.

**Unchanged**: All existing features remain fully functional. Watch app (`pbw/`) is unchanged.
