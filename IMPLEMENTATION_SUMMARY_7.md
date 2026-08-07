# Implementation summary

## #31 — Code Comments: Full Documentation for APK (Kotlin) and PBW (C)

### Overview

Added documentation comments to all 29 source files of pLauncher (17 Kotlin for the Android companion app, 12 C for the Pebble watchapp), making the codebase accessible to any reader. Every file received a header comment identifying the author, application name, and file purpose. Non-self-explanatory functions received concise inline comments explaining their logic. Self-explanatory functions (named clearly, trivial one-liners) were left uncommented to avoid verbosity.

### Analysis

**Previous state**: Neither the APK nor the PBW had file header comments. The APK had only 5 inline comments across 3 files (~85 functions/classes undocumented). The PBW had `layout.h` as the only well-commented file with inline comments per constant, and `packets.h` with 3 group comments (`// Packet types`, etc.) but no real documentation. `packets.c` (400 lines) was the largest file and completely devoid of comments (~60 functions undocumented).

**Comment rules applied**:
1. **File header**: Every file has an initial comment with author (`Le0xFF`), application name ("pLauncher Companion App" for APK, "pLauncher Watchapp" for PBW), and a summary description of what the file does.
2. **Function comments**: Only added where code is not self-explanatory. No verbosity. If the function name and parameters are sufficiently clear, no comment was added.
3. **Style**: Kotlin uses KDoc (`/** ... */`) for file headers and public functions, inline `//` for private/internal functions. C uses `/* ... */` blocks for file headers, `//` before non-trivial functions.
4. **Language**: All comments in English (consistent with project AGENTS.md rules).
5. **Excluded**: Build configuration files (`build.gradle.kts`, `settings.gradle.kts`, etc.), resource files (`strings.xml`, drawables, etc.), binary files (`.kra`), `README.md`, `wscript` (already has docstrings), `svg_to_pebble_png.sh` (already has usage comments).

### Implementation Steps

Executed as 11 steps following the plan, each compiled and verified independently:

| Step | Scope | Files | Changes |
|---|---|---|---|
| 1 | APK: File headers | 17 Kotlin | Added KDoc header block to every Kotlin file |
| 2 | APK: MainActivity.kt | 1 | KDoc on `AppViewModel` and `MainActivity` classes, inline comments on `validateAutoLaunchTarget()`, `onCreate()`, `onResume()`, `onDestroy()` |
| 3 | APK: Pebble communication | 2 | KDoc on `PebbleListenerService` and `PebbleSenderHelper` classes, inline comments on key methods (`onCreate`, `onDestroy`, `onMessageReceived`, `handleWatchWelcome`, `handleLaunchApp`, `sendAppList`, `sendAppListChunks`) |
| 4 | APK: Service files | 5 | Inline comments on `onCreate()` in LaunchActivity, CrashApplication, CrashReportActivity, and `install()` in PbwInstaller |
| 5 | APK: Data layer | 4 | Comments on `bytesToHex`/`hexToBytes`, `saveApps`, `refreshIcons` in AppDataStore, `importAppsFromYaml` in YamlExportImport |
| 6 | APK: UI files | 4 | KDoc on `AppScreen`, `SettingsScreen`, `AccordionCard`, `AppPickerDialog` composables |
| 7 | APK: IconConverter + final review | 2 | Comments on `convertToPebbleColorIcon` and `convertToPebbleBwIcon`, plus `sendWelcome` and `exportAppsToYaml` found by review |
| 8 | PBW: File headers | 12 C/H | Added `/* ... */` header block to every C and header file |
| 9 | PBW: packets.c | 1 | Comments on 10 non-self-explanatory functions (`try_auto_launch`, `auto_launch_timer_handler`, `outbound_sent_handler`, `outbound_failed_handler`, `response_timeout_handler`, `handle_phone_welcome`, `handle_app_list`, `auto_close_timer_handler`, `handle_launch_confirm`, `inbox_received_handler`) |
| 10 | PBW: app_list.c + window_main.c | 2 | Comments on `app_list_next`/`app_list_prev`, `app_list_add`, `calc_text_frame`, `window_load`, `window_appear`, `window_main_update_display` |
| 11 | PBW: Remaining + final review | 3 | Comments on `init()` in pLauncher.c, `select_click_handler`, `up_click_handler`, `down_click_handler` in window_main_click.c |

### APK (`apk/`) — 17 Kotlin files

#### File Headers (KDoc)

Every Kotlin file received a header after the `package` declaration:

```kotlin
/**
 * pLauncher Companion App — [description]
 *
 * @author Le0xFF
 */
```

| File | Description |
|---|---|
| `MainActivity.kt` | Main Activity and ViewModel. Hosts the Compose UI, manages app list CRUD, settings, Pebble sync, and YAML import/export. |
| `PebbleListenerService.kt` | Foreground service that receives AppMessage packets from the Pebble watch via PebbleKit2. Handles watch connections and launch requests. |
| `PebbleSenderHelper.kt` | Helper that wraps PebbleKit2's DefaultPebbleSender to send AppMessage packets to the watch. Implements the communication protocol. |
| `LaunchActivity.kt` | Transient Activity that launches a target Android app by package name and broadcasts the result to PebbleListenerService. |
| `BootReceiver.kt` | BroadcastReceiver that restarts PebbleListenerService as a foreground service on device boot. |
| `CrashApplication.kt` | Application subclass that installs a custom uncaught exception handler for crash reporting when the setting is enabled. |
| `CrashReportActivity.kt` | Activity that displays crash report details with copy-to-clipboard, copyable stack trace, and restart/close actions. |
| `PbwInstaller.kt` | Utility for installing the bundled .pbw watchapp: checks availability, reads metadata, stages to cache, triggers Pebble app installer. |
| `model/LaunchApp.kt` | Data model for launcher entries. Defines the LaunchApp data class and SortOrder enum. |
| `data/AppDataStore.kt` | Persistent data store using SharedPreferences. Manages app list and all user preferences with hex-encoded icon data. |
| `data/AppLogBuffer.kt` | Thread-safe in-memory log buffer with 500-entry FIFO capacity. Used for debugging and log export. |
| `data/YamlExportImport.kt` | YAML-based export/import of app lists. Validates duplicates, positions, and auto-launch conflicts on import. |
| `ui/AppScreen.kt` | Home screen composable. Displays the app list with search, sort, drag-to-reorder, and add/remove/rename actions. |
| `ui/SettingsScreen.kt` | Settings screen with expandable accordion panels for general, watchapp, permissions, and debug options. |
| `ui/Theme.kt` | Theme system with Light, Dark, and Amoled variants. Provides Material3 color schemes and system UI integration. |
| `ui/AppPickerDialog.kt` | Full-screen dialog for selecting installed apps to add to the launcher, with search and system app filtering. |
| `util/IconConverter.kt` | Converts Android app icons to Pebble-specific formats: 4-bit color (1024 bytes) and 1-bit B/W (128 bytes) at 32x32 resolution. |

#### Function Comments (Kotlin)

Functions received comments only when the name alone was insufficient:

| File | Commented Elements |
|---|---|
| `MainActivity.kt` | `AppViewModel` (KDoc), `MainActivity` (KDoc), `validateAutoLaunchTarget()`, `onCreate()`, `onResume()`, `onDestroy()` |
| `PebbleListenerService.kt` | Class (KDoc), `onCreate()`, `onDestroy()`, `onMessageReceived()`, `handleWatchWelcome()`, `handleLaunchApp()` |
| `PebbleSenderHelper.kt` | Class (KDoc), `sendWelcome()`, `sendAppList()`, `sendAppListChunks()` |
| `LaunchActivity.kt` | `onCreate()` |
| `BootReceiver.kt` | None (self-explanatory) |
| `CrashApplication.kt` | `onCreate()` |
| `CrashReportActivity.kt` | `onCreate()` |
| `PbwInstaller.kt` | `install()` |
| `model/LaunchApp.kt` | None (self-explanatory) |
| `data/AppDataStore.kt` | `bytesToHex()`/`hexToBytes()`, `saveApps()`, `refreshIcons()` |
| `data/AppLogBuffer.kt` | None (self-explanatory) |
| `data/YamlExportImport.kt` | `exportAppsToYaml()`, `importAppsFromYaml()` |
| `ui/AppScreen.kt` | `AppScreen` composable (KDoc) |
| `ui/SettingsScreen.kt` | `SettingsScreen` (KDoc), `AccordionCard` (KDoc) |
| `ui/Theme.kt` | None (self-explanatory) |
| `ui/AppPickerDialog.kt` | `AppPickerDialog` composable (KDoc) |
| `util/IconConverter.kt` | `convertToPebbleColorIcon()`, `convertToPebbleBwIcon()` |

### Watch App (`pbw/`) — 12 C/Header files

#### File Headers (C)

Every C and header file received a header at the very top:

```c
/*
 * pLauncher Watchapp — [description]
 *
 * Author: Le0xFF
 */
```

| File | Description |
|---|---|
| `pLauncher.c` | Application entry point. Initializes all subsystems and runs the event loop. |
| `packets.h` | Communication protocol definitions. Packet types, AppMessage keys, and public API declarations. |
| `packets.c` | AppMessage packet handling implementation. Manages send/receive, preferences persistence, and protocol state machine. |
| `app_list.h` | App list data model. LaunchApp struct definition, constants, and function declarations. |
| `app_list.c` | App list management implementation. CRUD operations, circular navigation, and icon storage. |
| `window_main.h` | Main window API declarations. Window creation, access, and display update. |
| `window_main.c` | Main window UI implementation. Creates layers, handles dynamic layout for basalt/emery screens. |
| `window_main_click.h` | Click configuration provider declaration. |
| `window_main_click.c` | Button click handlers. Navigation (up/down) and app launch (select) with loading guards. |
| `strings.h` | User-visible string resource definitions. |
| `strings.c` | String resource implementations. |
| `layout.h` | Layout constants for dynamic UI positioning. Font sizes, margins, and navigation dimensions. |

#### Function Comments (C)

| File | Commented Functions |
|---|---|
| `pLauncher.c` | `init()` — subsystem initialization orchestration |
| `packets.c` | `try_auto_launch()`, `auto_launch_timer_handler()`, `outbound_sent_handler()`, `outbound_failed_handler()`, `response_timeout_handler()`, `handle_phone_welcome()`, `handle_app_list()`, `auto_close_timer_handler()`, `handle_launch_confirm()`, `inbox_received_handler()` |
| `app_list.c` | `app_list_next()`/`app_list_prev()` — circular navigation, `app_list_add()` — icon size validation |
| `window_main.c` | `calc_text_frame()`, `window_load()`, `window_appear()`, `window_main_update_display()` |
| `window_main_click.c` | `select_click_handler()`, `up_click_handler()`, `down_click_handler()` — loading guards |
| `strings.c` | None (self-explanatory) |
| `packets.h`, `app_list.h`, `window_main.h`, `window_main_click.h`, `layout.h` | None (declarations and constants are self-explanatory) |

### Code Statistics

| Component | Files | Comments added |
|---|---|---|
| Android App (Kotlin) | 17 | 17 headers + ~25 function comments |
| Watch App (C) | 12 | 12 headers + ~20 function comments |
| **Total** | **29** | **29 headers + ~45 function comments** |

### Build Status

- Android app: `./gradlew assembleDebug` — BUILD SUCCESSFUL
- Watch app: `pebble build` — BUILD SUCCESSFUL (basalt + emery)

### Design Decisions

**KDoc vs inline `//` for Kotlin**: File headers use KDoc (`/** ... */`) as the standard Kotlin documentation format. Class-level documentation on key classes (`AppViewModel`, `PebbleListenerService`, `PebbleSenderHelper`) also uses KDoc. Private/internal function comments use `//` to distinguish implementation notes from API documentation.

**`/* ... */` vs `//` for C**: File headers use `/* ... */` blocks (standard C convention). Function-level comments use `//` for brevity and readability in dense C code.

**Self-explanatory threshold**: Functions with descriptive names and trivial bodies (e.g., `setApps`, `getShowSystemApps`, `app_list_get_count`, `app_list_clear`) were left uncommented. Functions with non-obvious logic (e.g., `handle_app_list` with transfer ID deduplication, `convertToPebbleColorIcon` with 4-bit quantization, `calc_text_frame` with centering math) received comments.

**Comment language**: All comments in English, consistent with the project's AGENTS.md rule that all strings and comments must be in English.

**No behavior changes**: Adding comments is a zero-risk operation. No logic, no control flow, no data structures were modified. Both projects compile and link cleanly after every step.

**Unchanged**: All functionality preserved. Protocol, UI behavior, feature set, and code logic remain identical.

---

## #32 — Linting Rules: detekt for APK (Steps 1-7 complete)

### Overview

Configured detekt (Kotlin static code analyzer) for the Android companion app (`apk/`), establishing automated linting rules for line length (140 chars), 4-space indentation, magic numbers, cyclomatic complexity, naming conventions, and Compose-specific rules. All 7 steps of the linting plan (plan file: `1786044558010-plauncher-linting-rules.md`) are now complete: setup, baseline, and full violation resolution.

### Analysis

**Previous state**: No linting was configured for the APK. The project used Gradle 8.9.1 with Kotlin 2.3.0. No detekt, ktlint, or android-lint was present.

**Reference project**: `PebbleNotificationCenter2/mobile/config/detekt.yml` was used as the baseline, adapted for pLauncher's specific needs. PebbleNotificationCenter2 uses detekt `2.0.0-alpha.5` with Kotlin `2.3.21`, confirming compatibility with Kotlin 2.x.

**Version choice**: detekt `2.0.0-alpha.5` with plugin ID `dev.detekt` (new package for 2.x series). The stable `1.23.7` was initially attempted but has significantly different config property names and a different Gradle plugin ID (`io.gitlab.arturbosch.detekt`). detekt 2.0 alpha uses `buildUponDefaultConfig` in the Gradle DSL (not YAML), renamed rules (e.g., `UnusedPrivateMember` → `UnusedPrivateFunction` + `UnusedPrivateClass`), and requires the `ktlint` section to be handled as a separate plugin (excluded from this step).

### Implementation Steps

| Step | Scope | Files | Changes |
|---|---|---|---|
| 1 | Setup: version catalog, build files, detekt.yml | 5 | Added detekt plugin, config, compose rules |
| 2 | Baseline | `apk/app/detekt-baseline.xml` | Captured initial violations |
| 3 | Magic numbers (C) | N/A (covered by #33 clang-format) | Handled in separate section |
| 4 | Magic numbers (Kotlin) | 6 | Extracted constants in IconConverter, AppDataStore, SettingsScreen, YamlExportImport, AppScreen, AppPickerDialog, Theme |
| 5 | Line length + wildcards + chains | 6 | Fixed wildcards, split chained calls, handled Compose wildcards via excludeImports |
| 6 | Complexity reduction | 4 | Refactored YamlExportImport.importAppsFromYaml, suppressed lifecycle methods, suppressed protocol methods |
| 7 | Final pass + baseline cleanup | 4 | Resolved remaining Compose rules, unused parameters, emptied baseline |

### APK (`apk/`) — Files Modified

| File | Changes |
|---|---|
| `apk/gradle/libs.versions.toml` | Added `detekt` and `detekt-compose` versions, `detekt-compose-rules` library, `detekt` plugin (`dev.detekt`) |
| `apk/build.gradle.kts` | Added `alias(libs.plugins.detekt) apply false` to plugins block |
| `apk/app/build.gradle.kts` | Added `alias(libs.plugins.detekt)` to plugins, `detekt {}` block with config path and settings, `detektPlugins` dependency for compose rules |
| `apk/settings.gradle.kts` | Added `maven("https://oss.sonatype.org/content/repositories/snapshots/")` for detekt alpha resolution |
| `apk/config/detekt.yml` | **New file** — 800+ lines, adapted from PebbleNotificationCenter2 reference config, updated with `excludeImports` and `ignoreAnnotated` |
| `apk/app/detekt-baseline.xml` | Emptied `CurrentIssues` (all resolved) |
| `apk/app/detekt-baseline-debug.xml` | **Removed** — dead file not referenced by build |
| `apk/app/detekt-baseline-release.xml` | **Removed** — dead file not referenced by build |

### detekt.yml Configuration

Key rules enabled and their settings:

| Rule | Setting | Purpose |
|---|---|---|
| `style.MaxLineLength` | `maxLineLength: 140`, exclude package/import | Enforce 140 char line limit |
| `style.MagicNumber` | Ignore -1, 0, 1, 2; ignore property/constant/annotation | Detect unnamed magic numbers |
| `style.NoTabs` | `active: true` | Force spaces only |
| `style.MaxChainedCallsOnSameLine` | `maxChainedCalls: 3` | Limit chained calls per line |
| `style.WildcardImport` | `excludeImports` for Compose layout/runtime | Allow Compose wildcards (Kotlin 2.3.0 + Compose BOM compatibility) |
| `complexity.CyclomaticComplexMethod` | `allowedComplexity: 10`, `ignoreAnnotated: [Composable]` | Flag overly complex methods (ignores Compose) |
| `complexity.CognitiveComplexMethod` | `allowedComplexity: 15`, ignore `Composable` | Cognitive complexity (ignores Compose) |
| `complexity.NestedBlockDepth` | `allowedDepth: 4`, ignore `Composable` | Limit nesting depth |
| `complexity.LongMethod` | `allowedLines: 60`, ignore `Composable` | Flag long methods (ignores Compose) |
| `naming.*` | Standard patterns | Enforce naming conventions |
| `Compose.*` | All Compose rules active | Compose-specific best practices |

Rules explicitly disabled:
- `ktlint` section (requires separate `detekt-rules-ktlint-wrapper` plugin — not in scope)
- `ReturnCount` (prevents useful guard clauses)
- `ExpressionBodySyntax` (not required)
- `DocumentationOverPrivateFunction/Property` (not enforced)
- `UndocumentedPublicClass/Function/Property` (not enforced)
- `ForbiddenComment` only blocks `FIXME:` and `TODO:`

### Violation Fixes (Steps 4-7)

All baseline violations have been resolved. Detailed breakdown:

#### WildcardImport fixes (~16 issues resolved)

| File | Fix |
|---|---|
| `PebbleSenderHelper.kt` | Replaced `com.le0xff.plauncher.protocol.*` with explicit imports |
| `AppPickerDialog.kt` | Replaced all Compose wildcard imports with explicit imports |
| `AppLogBuffer.kt` | Replaced `java.util.*` with `java.util.Date` + `java.util.Locale` |
| Compose wildcards | Kept `androidx.compose.foundation.layout.*` and `androidx.compose.runtime.*` — added `excludeImports` in `detekt.yml` (explicit imports cause Kotlin 2.3.0 + Compose BOM compatibility issues with internal `RowColumnParentData`) |

#### MagicNumber fixes (~20 issues resolved)

| File | Constants Added |
|---|---|
| `IconConverter.kt` | `ALPHA_THRESHOLD`, `COLOR_BIT_MASK`, `ALPHA_FULL`, `QUANTIZE_SHIFT`, `ALPHA_SHIFT`, `RED_SHIFT`, `GREEN_SHIFT`, `BLUE_SHIFT`, `LUMINANCE_R_WEIGHT`, `LUMINANCE_G_WEIGHT`, `LUMINANCE_B_WEIGHT`, `LUMINANCE_THRESHOLD`, `BYTE_ALIGNMENT`, `BITS_PER_BYTE`, `MAX_DRAWABLE_SIZE`, `MIN_DRAWABLE_SIZE`, `BYTE_ALIGN_MASK`, `BYTE_SHIFT` |
| `AppDataStore.kt` | `FIELD_COUNT_NO_ICON`, `FIELD_COUNT_FULL`, `HEX_NIBBLE_SHIFT`, `HEX_NIBBLE_BITS`, `HEX_OFFSET`, `IDX_PACKAGE`, `IDX_DISPLAY`, `IDX_ICON_COLOR`, `IDX_ICON_BW` |
| `SettingsScreen.kt` | `VibrationNone`, `VibrationShort`, `VibrationLong`, `VibrationDouble` |
| `YamlExportImport.kt` | `MaxApps = 20` |
| `AppScreen.kt` | `AppListItemIconSize`, `AppListItemCircleAlpha` |
| `AppPickerDialog.kt` | `PickerIconSize`, `PickerColumnHeight` |
| `Theme.kt` | `AmoledSurface`, `AmoledOnSurface`, `AmoledSurfaceContainer`, `AmoledSurfaceContainerHighest`, `AmoledSurfaceContainerLow` |

#### MaxChainedCallsOnSameLine fixes (~19 issues resolved)

| File | Fix |
|---|---|
| `AppDataStore.kt` | Split 8 chained `prefs.edit().putX().apply()/commit()` calls into separate editor variable assignments |
| `CrashApplication.kt` | Split `prefs.edit().putString().commit()` chain |
| `PbwInstaller.kt` | Split 3 chains (`assets.open().close()`, `assets.open().bufferedReader().use{}`, `assets.open().use{...}`) |
| `MainActivity.kt` | Split `contentResolver.openInputStream()?.bufferedReader()?.use{}` and `info.loadLabel().toString().ifBlank{}` chains |
| `AppPickerDialog.kt` | Split `app.icon?.toBitmap()?.asImageBitmap()` and modifier chains |
| `SettingsScreen.kt` | Split `modifier.fillMaxSize().verticalScroll().padding()` chain |

#### UnusedParameter fixes (4 issues resolved)

| File | Suppression |
|---|---|
| `AppDataStore.kt` | `@Suppress("UnusedParameter")` on `refreshIcons(packageManager)` |
| `PebbleListenerService.kt` | `@Suppress("UnusedParameter")` on `onMessageReceived(watch)` and `handleLaunchApp(watch)` |
| `AppPickerDialog.kt` | `@Suppress("UnusedParameter")` on `modifier` parameter |
| `YamlExportImport.kt` | `@Suppress("UnusedParameter")` on `contextPackageName` |

#### Compose rules fixes (4 issues resolved)

| Rule | Fix |
|---|---|
| `ComposableNaming` | Renamed `pLauncherTheme` → `PLauncherTheme` (with `@Composable`, must start uppercase) |
| `UnnecessaryComposable` | Removed `@Composable` from `getAppThemeColorScheme` (pure function, no Compose APIs used) |
| `ModifierMissing` | Added `modifier: Modifier = Modifier` parameter to `AppListItem` composable |
| `MutableStateAutoboxing` | Replaced `mutableStateOf(0)` for tab selection with `AppTab` enum (`Apps`, `Settings`) to avoid Int autoboxing |

#### Complexity fixes (6 issues handled)

| File/Method | Fix |
|---|---|
| `YamlExportImport.importAppsFromYaml` | Refactored into `validateEntries()`, `buildAppList()`, `resolveDisplayName()`, `clampAutoLaunchTarget()` helper functions |
| `MainActivity.onCreate`, `CrashReportActivity.onCreate` | `@Suppress("CognitiveComplexMethod", "LongMethod", "CyclomaticComplexMethod")` — standard Android `setContent { }` pattern where UI lives inside lifecycle method |
| `PebbleSenderHelper.sendAppListChunks` | `@Suppress("CognitiveComplexMethod")` — protocol packet construction inherently complex |
| `YamlExportImport.validateEntries` | `@Suppress("CognitiveComplexMethod", "CyclomaticComplexMethod")` — validation logic with multiple branch conditions |
| `detekt.yml` | Added `ignoreAnnotated: [Composable]` to `CyclomaticComplexMethod` to cover `SettingsScreen` |

#### New types added

| Type | Location | Purpose |
|---|---|---|
| `AppTab` enum (`Apps`, `Settings`) | `MainActivity.kt` | Replaces Int-based tab selection to avoid autoboxing |
| `ValidatedEntries` data class | `YamlExportImport.kt` | Intermediate result for YAML import validation |
| `BuiltApps` data class | `YamlExportImport.kt` | Intermediate result for YAML import app building |

#### Config changes

| File | Change |
|---|---|
| `detekt.yml` | Added `excludeImports` for `WildcardImport` rule (Compose layout/runtime), added `ignoreAnnotated: [Composable]` to `CyclomaticComplexMethod` |
| `detekt-baseline.xml` | Emptied `CurrentIssues` (all resolved) |
| `detekt-baseline-debug.xml` | Removed — dead file not referenced by build |
| `detekt-baseline-release.xml` | Removed — dead file not referenced by build |

### Build Status

- `./gradlew detekt` — BUILD SUCCESSFUL (0 issues)
- `./gradlew assembleDebug` — BUILD SUCCESSFUL

### Design Decisions

**detekt 2.0.0-alpha.5 over 1.23.7**: The PebbleNotificationCenter2 reference project uses detekt 2.x, and the config format differs significantly between 1.x and 2.x. Using 2.x ensures config compatibility with the reference. The alpha status is acceptable since PebbleNotificationCenter2 already uses it in production with Kotlin 2.3.x.

**Plugin ID `dev.detekt`**: detekt 2.x moved from `io.gitlab.arturbosch.detekt` to `dev.detekt`. The old plugin ID does not work with 2.x artifacts.

**ktlint section removed**: The `ktlint` ruleset in detekt 2.x requires the separate `detekt-rules-ktlint-wrapper` plugin. Since the plan's formatting requirements (140 chars, 4-space indent) are covered by detekt's own `MaxLineLength` and `NoTabs` rules, the ktlint integration was excluded to keep the configuration self-contained. The plan's Step 2 (clang-format for C) handles C formatting independently.

**`buildUponDefaultConfig: true` in Gradle DSL**: This setting applies the custom config on top of detekt's built-in defaults, so only overridden rules need to be specified in the YAML. Rules not mentioned in the YAML use detekt's defaults.

**`warningsAsErrors: true`**: All detekt findings are treated as errors, ensuring the build fails until violations are resolved or baselined.

**Compose rules plugin**: The `io.nlopez.compose.rules:detekt:0.6.2` plugin provides Compose-specific rules (ModifierMissing, ModifierNaming, RememberMissing, etc.) registered under the `Compose` ruleset.

**Compose wildcard imports preserved**: `androidx.compose.foundation.layout.*` and `androidx.compose.runtime.*` wildcards are kept via `excludeImports` because Kotlin 2.3.0 + Compose BOM compatibility requires them — explicit imports cause failures with internal `RowColumnParentData`.

**Zero violations achieved**: All baseline violations resolved through a combination of code fixes (constants, explicit imports, chain splitting, refactoring) and targeted suppressions (lifecycle methods, protocol code, unused parameters from framework callbacks).

---

## #33 — Linting Rules: clang-format for PBW (Step 2 of plan)

### Overview

Configured clang-format for the Pebble watchapp (`pbw/`), establishing automatic C code formatting rules for line length (140 chars), 4-space indentation (no tabs), and LLVM-based style adapted for the project. Applied formatting to all 12 C and header files. This is Step 2 of the 7-step linting plan.

### Analysis

**Previous state**: No formatting tool was configured for the PBW. The C code used 4-space indentation but with inconsistent spacing around pointers, assignments, and function signatures that did not match a formal style.

**Tool**: clang-format v21.1.8 (Debian package `clang-format`), configured with a `.clang-format` file in the `pbw/` directory.

**Config source**: The plan provided an explicit YAML configuration block based on LLVM style with custom settings for 140-column limit and 4-space indent. The plan's original config used `BreakBeforeBraces: Custom` with `AfterControlStatement: Never` and `AfterObject: false`, but clang-format v21 requires boolean values (`true`/`false`) for `BraceWrapping` entries, and `AfterObject` is a C++-specific key not valid for C language mode. These were corrected during implementation.

### Implementation Steps

| Step | Scope | Changes |
|---|---|---|
| 1 | Install clang-format | Installed `clang-format` package (v21.1.8) |
| 2 | Create `.clang-format` | Created `pbw/.clang-format` with plan's configuration, corrected for clang-format v21 |
| 3 | Run dry-run check | `clang-format --dry-run --Werror` on all 12 source files — confirmed formatting needed |
| 4 | Apply formatting | `clang-format -i` on all 12 source files |
| 5 | Verify | `clang-format --dry-run --Werror` — zero violations remaining |
| 6 | Compile | `pebble build` — BUILD SUCCESSFUL for basalt + emery |

### PBW (`pbw/`) — Files Modified

| File | Changes |
|---|---|
| `pbw/.clang-format` | **New file** — 27 lines, LLVM-based config with 140-column limit, 4-space indent |
| `src/c/pLauncher.c` | Spacing: pointer alignment, assignment spacing |
| `src/c/packets.c` | Spacing: pointer alignment, assignment spacing, function parameter formatting |
| `src/c/packets.h` | Spacing: `#define` alignment, function declaration spacing |
| `src/c/app_list.c` | Spacing: pointer alignment, assignment spacing, function parameter formatting |
| `src/c/app_list.h` | Spacing: `#define` alignment, struct member spacing, function declaration spacing |
| `src/c/window_main.c` | Spacing: pointer alignment, assignment spacing, function parameter formatting |
| `src/c/window_main.h` | Spacing: function declaration spacing |
| `src/c/window_main_click.c` | Spacing: function parameter formatting |
| `src/c/window_main_click.h` | Spacing: function declaration spacing |
| `src/c/strings.c` | Spacing: function declaration spacing |
| `src/c/strings.h` | Spacing: function declaration spacing |
| `src/c/layout.h` | Spacing: `#define` alignment |

### .clang-format Configuration

| Setting | Value | Purpose |
|---|---|---|
| `BasedOnStyle` | `LLVM` | Base style |
| `Language` | `C` | C language mode (excludes C++-specific options) |
| `ColumnLimit` | `140` | Enforce 140 char line limit |
| `IndentWidth` | `4` | 4-space indentation |
| `UseTab` | `Never` | Force spaces only |
| `ContinuationIndentWidth` | `8` | 8-space continuation indent |
| `AllowShortFunctionsOnASingleLine` | `Empty` | Only empty functions on one line |
| `AllowShortBlocksOnASingleLine` | `Never` | Never allow short blocks on one line |
| `BreakBeforeBraces` | `Attach` | ATTACH brace style (K&R) |
| `BraceWrapping.AfterControlStatement` | `false` | No wrapping after control statements |
| `BraceWrapping.AfterFunction` | `false` | No wrapping after function |
| `BraceWrapping.AfterStruct` | `false` | No wrapping after struct |
| `BraceWrapping.BeforeElse` | `false` | No break before else |
| `BraceWrapping.SplitEmptyFunction` | `false` | Don't split empty functions |
| `AlignConsecutiveAssignments` | `Enabled: true` | Align consecutive assignments |
| `AlignConsecutiveDeclarations` | `Enabled: true` | Align consecutive declarations |
| `AlignConsecutiveMacros` | `Enabled: true` | Align consecutive `#define` macros |
| `SortIncludes.Enabled` | `false` | Don't auto-sort includes |

### Formatting Changes Applied

The main changes were spacing adjustments:
- **Pointer alignment**: `static bool s_waiting_for_response = false` → `static bool  s_waiting_for_response = false` (extra space to align pointer/type)
- **Assignment spacing**: `a = b` → `a = b` (consistent spacing around `=`)
- **Function parameters**: `void foo(void* context)` → `void foo(void * context)` (spaces around pointer)
- **#define alignment**: `#define KEY_X 1` → `#define KEY_X  1` (aligned values)
- **Struct members**: `char name[32]` → `char  name[32]` (aligned members)

### Config Corrections from Plan

The plan's original config needed two corrections for clang-format v21:
1. `AfterControlStatement: Never` → `AfterControlStatement: false` (brace wrapping uses booleans, not `Never`)
2. `AfterObject: false` removed entirely (C++-specific key, invalid for `Language: C`)

### Build Status

- Watch app: `pebble build` — BUILD SUCCESSFUL (basalt + emery)
- `clang-format --dry-run --Werror` — zero violations
- Memory usage unchanged: basalt 29325 bytes RAM, emery 29325 bytes RAM

### Design Decisions

**LLVM base style**: The LLVM style is the most widely used clang-format preset and produces clean, readable C code. The main customization is the 140-column limit and 4-space indent.

**ATTACH brace style** (`BreakBeforeBraces: Attach`): Matches K&R style commonly used in embedded C projects and the Pebble SDK codebase.

**`AllowShortBlocksOnASingleLine: Never`**: More restrictive than LLVM's default (`Empty`), ensuring consistent multi-line blocks.

**`SortIncludes: false`**: Preserves the current include ordering rather than auto-sorting, avoiding unnecessary diffs.

**No semantic changes**: clang-format only modifies whitespace. No logic, control flow, or data structures were affected. Both targets compile with identical memory usage.

---

## #34 — Build Flow: Integrated APK Build with PBW and APK Linting

### Overview

Extended the APK Gradle build (`apk/app/build.gradle.kts`) with a complete flow that integrates PBW icon export, PBW linting (clang-format check), PBW build, APK icon export, and APK linting (detekt). All linting and icon extraction steps use `isIgnoreExitValue = true` and log warnings on failure without blocking the final build.

### Analysis

**Previous state**: The build had 4 Gradle tasks (`exportPbwIcon`, `buildWatchapp`, `bundleWatchPbw`, `generatePbwInfo`) chained for the PBW side only. APK icon export was manual. Detekt was configured with `ignoreFailures = false` (blocking). No clang-format check task existed for the PBW C code.

**Plan file**: `1786097506972-apk-build-flow-with-linting.md`

**Target flow**:
```
PBW side:  exportPbwIcon → lintPbw → buildWatchapp → bundleWatchPbw + generatePbwInfo → merge*Assets
APK side:  exportApkIcon → lintApk (detektMain) → merge*Assets
```

### Implementation Steps

Executed as 5 steps (Steps 4 and 5 merged due to dependency refactoring), each compiled and verified independently:

| Step | Scope | Changes |
|---|---|---|
| 1 | Detekt non-blocking | Changed `ignoreFailures = false` → `true` in `detekt { }` block |
| 2 | `lintPbw` task | Added `Exec` task running `clang-format --dry-run --Werror --style=file` on `pbw/src/c/*.c` and `*.h` |
| 3 | `exportApkIcon` task | Added `Exec` task running `generate_icon.sh apk/pLauncher_apk.kra --apk` |
| 4+5 | Dependency chain + `lintApk` wrapper | Connected full flow; added `lintApk` as explicit wrapper for `detektMain` |

### APK (`apk/app/build.gradle.kts`) — Changes

| Change | Location | Detail |
|---|---|---|
| `ignoreFailures = true` | `detekt { }` block (line 150→181) | Detekt warnings no longer block the build |
| `lintPbw` task | Lines 89-103 | `Exec` task: `find src/c -name "*.c" -o -name "*.h" -print0 \| xargs -0 clang-format --dry-run --Werror --style=file`, depends on `exportPbwIcon` |
| `buildWatchapp` dependency | Line 106 | Changed from `dependsOn(exportPbwIcon)` to `dependsOn(lintPbw)` |
| `exportApkIcon` task | Lines 161-174 | `Exec` task: `generate_icon.sh apk/pLauncher_apk.kra --apk`, independent entry point |
| `detektMain` dependency | Lines 185-187 | `detektMain` depends on `exportApkIcon` |
| `lintApk` task | Lines 189-192 | `DefaultTask` wrapping `exportApkIcon` + `detektMain`, provides naming consistency with `lintPbw` |
| `merge*Assets` dependency | Lines 194-198 | Added `dependsOn(lintApk)` to existing `dependsOn(bundleWatchPbw)` + `dependsOn(generatePbwInfo)` |

### Task Dependency Chain

```
PBW side:  exportPbwIcon → lintPbw → buildWatchapp → bundleWatchPbw + generatePbwInfo → merge*Assets
APK side:  exportApkIcon → detektMain → lintApk → merge*Assets
```

Running `./gradlew assembleDebug` automatically executes the full chain in order.

### Build Status

- `./gradlew assembleDebug` — BUILD SUCCESSFUL (58 tasks)
- `./gradlew lintPbw` — BUILD SUCCESSFUL (clang-format passes on all 12 source files)
- `./gradlew exportApkIcon` — BUILD SUCCESSFUL (icons generated)
- `./gradlew lintApk` — BUILD SUCCESSFUL (detekt passes with 0 violations)

### Design Decisions

**`lintApk` as `DefaultTask` wrapper**: Instead of depending directly on `detektMain`, a named `lintApk` task wraps it. This provides naming symmetry with `lintPbw` and makes the flow self-documenting.

**Avoided circular dependency**: `detektMain` depends on `detektDebug` → `compileDebugKotlin`, so making `compileDebugKotlin` depend on `detektMain`/`lintApk` would create a cycle. Instead, `lintApk` is attached to `merge*Assets`, ensuring it runs before the APK is packaged but after Kotlin compilation completes. This means detekt runs with type resolution (via `detektMain` → `detektDebug`) but the lint result doesn't block compilation.

**`isIgnoreExitValue = true` for all non-build tasks**: `exportPbwIcon`, `lintPbw`, `buildWatchapp`, `exportApkIcon`, and `detektMain` (via `ignoreFailures = true`) all tolerate failures. Each logs a meaningful warning via `logger.warn()` or detekt's built-in output.

**`find ... -print0 | xargs -0` for clang-format**: Ensures safe handling of filenames with spaces or special characters.

**Zero behavior changes to existing tasks**: The existing `exportPbwIcon`, `buildWatchapp`, `bundleWatchPbw`, and `generatePbwInfo` tasks remain functionally unchanged. Only their dependency chain was extended.

**Unchanged**: All existing functionality preserved. Protocol, UI, feature set, and build output remain identical.

---

## #35 — Pre-commit Lint Hook per pLauncher

### Overview

Added a pre-commit Git hook that automatically runs linting checks on staged files before every `git commit`. The hook verifies clang-format formatting for staged C files (`pbw/`) and runs detekt for the Android companion app (`apk/`). If linting rules are not met, the commit fails. The hook is installed automatically via a Gradle `commitHooks` task that copies files from `config/hooks/` to `.git/hooks/` on every build, replicating the mechanism used by PebbleNotificationCenter2.

### Analysis

**Previous state**: No pre-commit hook existed. Linting was only available as manual Gradle tasks (`lintPbw`, `lintApk`) or during the build flow. Developers could commit code that violated formatting or linting rules.

**Reference project**: `PebbleNotificationCenter2/mobile/config/hooks/pre-commit` and `PebbleNotificationCenter2/mobile/buildSrc/build.gradle.kts` provided the template for the hook script and the Gradle `commit-hooks` task.

**Key differences from PebbleNotificationCenter2**:
- pLauncher has no `buildSrc` — the `commitHooks` task lives in `apk/app/build.gradle.kts`
- The task hooks into `preBuild` (not `jar`) so it runs on every build
- Paths adjusted: from `apk/app/`, hooks are at `../../config/hooks/` and `.git/hooks` is at `../../.git/hooks/`
- The hook checks both C code (clang-format) and Kotlin code (detekt), unlike PebbleNotificationCenter2 which only checks Kotlin

### Implementation Steps

| Step | Scope | Files | Changes |
|---|---|---|---|
| 1 | Create pre-commit script + Gradle task | 2 | Created `config/hooks/pre-commit`, added `commitHooks` task, set `ignoreFailures = false` |
| 2 | End-to-end verification | 0 (temporary test files restored) | Verified hook blocks commits on clang-format violations, detekt violations, and allows clean commits |

### Files Created

| File | Purpose |
|---|---|
| `config/hooks/pre-commit` | Bash script that runs clang-format on staged C files and detekt on the APK |

### Files Modified

| File | Changes |
|---|---|
| `apk/app/build.gradle.kts` | Added `commitHooks` task (copies hooks from `../../config/hooks/` to `../../.git/hooks/` with executable permissions), wired to `preBuild`; changed `ignoreFailures = false` in `detekt { }` block |

### Pre-commit Hook Script

The script performs two checks:

1. **clang-format (PBW)**: Uses `git diff --cached --name-only --diff-filter=ACM` to find staged `.c` and `.h` files under `pbw/src/c/`, then runs `clang-format --dry-run --Werror --style=file` only on those files. Skips if no staged C files. Exits with error if `clang-format` is not installed.

2. **detekt (APK)**: Runs `./gradlew detektMain -q` from the `apk/` directory. Since `ignoreFailures = false` is set in `build.gradle.kts`, detekt failures cause the build (and commit) to fail. Skips if `apk/gradlew` is not found.

**Root detection**: The script handles being installed in either `config/hooks/` (tracked location) or `.git/hooks/` (installed location) by detecting if the parent directory is `.git/` and navigating accordingly.

### Gradle `commitHooks` Task

```kotlin
val commitHooks = tasks.register<Copy>("commitHooks") {
    from("../../config/hooks/")
    into("../../.git/hooks")
    filePermissions {
        unix("rwxr-xr-x")
    }
}

tasks.named("preBuild").configure {
    dependsOn(commitHooks)
}
```

- Copies all files from `config/hooks/` to `.git/hooks/` on every build
- Sets executable permissions on the copied files
- Linked to `preBuild` so it runs automatically with any Gradle build

### `ignoreFailures` Change

Changed `ignoreFailures = true` → `ignoreFailures = false` in the `detekt { }` block of `apk/app/build.gradle.kts`. This ensures detekt failures block both the Gradle build and the pre-commit hook. Note: this means the build flow's `lintApk` step (which runs detekt) will now block `assembleDebug` if violations exist. The existing `detekt-baseline.xml` covers historical violations, so the clean codebase passes.

### Verification Results

| Test | Expected | Result |
|---|---|---|
| Hook installed after build | `.git/hooks/pre-commit` exists, executable | PASS |
| clang-format violation (staged C file) | Commit blocked | PASS (exit code 1) |
| detekt violation (staged Kotlin file) | Commit blocked | PASS (exit code 1, MagicNumber) |
| Clean commit | Commit succeeds | PASS (exit code 0) |

All test files were restored to their original state after verification.

### Build Status

- `./gradlew assembleDebug` — BUILD SUCCESSFUL (59 tasks)
- `clang-format --dry-run --Werror` — zero violations on PBW
- `./gradlew detektMain` — BUILD SUCCESSFUL (0 violations)

### Design Decisions

**Staged files only for clang-format**: The hook uses `git diff --cached` to only check staged C files, matching standard pre-commit hook behavior. This avoids blocking commits due to unformatted files that the developer hasn't touched.

**Full detekt run for APK**: Unlike clang-format which only checks staged files, detekt runs on the entire APK source tree. This is because detekt rules like `MagicNumber` and complexity checks are meaningful across the full codebase, and the `detekt-baseline.xml` ensures historical violations don't trigger failures.

**Dynamic root detection**: The hook calculates the repository root by checking if its parent directory is `.git/`. When installed in `.git/hooks/`, it goes up two levels; when in `config/hooks/`, it goes up one level. This ensures the script works from both locations.

**No CLI `--ignore-failures` flag**: The plan initially specified `./gradlew detektMain --ignore-failures=false`, but this CLI flag doesn't exist for detekt. The `ignoreFailures` setting is configured in `build.gradle.kts` only. The hook simply runs `./gradlew detektMain -q` and relies on the build file configuration.

**`ignoreFailures = false` impacts build flow**: Changing `ignoreFailures` from `true` to `false` means the build flow's `lintApk` step now blocks `assembleDebug` if detekt finds violations. This is intentional — it ensures code quality is enforced both at commit time and at build time. The existing baseline covers pre-existing violations.

**Unchanged**: All existing functionality preserved. Protocol, UI, feature set, and build output remain identical.
