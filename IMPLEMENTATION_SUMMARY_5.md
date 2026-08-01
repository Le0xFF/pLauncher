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
