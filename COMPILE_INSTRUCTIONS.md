# Compilation Instructions

## Icon Generation

Icons are generated manually from the Krita project files using `generate_icon.sh`. Icon generation is **not** part of the automatic Gradle build flow — it must be run explicitly whenever icons are modified:

```bash
# Generate Android companion app icons (mipmaps, adaptive icon, splash)
./generate_icon.sh apk/pLauncher_apk.kra --apk

# Generate Pebble watchapp menu icon (25×25 pixel art)
./generate_icon.sh pbw/pLauncher_pbw.kra --pbw
```

The script accepts either `.kra` (Krita project, exported via `krita --export`) or `.png` files. Each target flag determines which pipeline runs:

- `--apk`: Exports PNG to `apk/app/src/main/res/drawable-nodpi/_src/` (hidden from Gradle), generates legacy mipmaps, adaptive icon XML, foreground drawable, and splash screen.
- `--pbw`: Exports PNG directly to `pbw/resources/images/app_launcher_icon.png` for the Pebble SDK.

**Prerequisites for icon generation**:

- `krita` (for `.kra` export, requires X11/Wayland display)
- `identify` (ImageMagick, for PNG validation)
- `convert` (ImageMagick, for mipmap resizing — `--apk` only)
- `python3` with PIL/Pillow (for splash screen generation — `--apk` only)

## Watch App Compilation

### Prerequisites

- Pebble SDK installed (`pebble` command available in PATH)
- SDK location: `${HOME}/.local/share/pebble-sdk/SDKs/current/`
- ARM GCC toolchain (`arm-none-eabi-gcc`, `arm-none-eabi-ar`)
- Icon generated first (see "Icon Generation" above, `--pbw` target)

### Target Platforms

- `basalt` (Pebble Time Steel)
- `emery` (Pebble Time 2)

### Build Command

```bash
cd pbw/
pebble build 2>&1 | tee build/build.log
```

This runs a verbose build and saves the full log to `pbw/build/build.log` for later review.

### Output

- `.pbw` files are generated in `pbw/build/`

### Run on Emulator

```bash
pebble install --emulator emery
```

### Install to Device

```bash
pebble install --phone <phone-ip-address>
```

### Troubleshooting

- **Missing headers**: Ensure the SDK is properly installed. Include paths are at:
  - `basalt`: `${HOME}/.local/share/pebble-sdk/SDKs/current/sdk-core/pebble/basalt/include`
  - `emery`: `${HOME}/.local/share/pebble-sdk/SDKs/current/sdk-core/pebble/emery/include`
- **SDK version mismatch**: The project uses Pebble SDK v3. Check `package.json` `sdkVersion`.
- **Build output errors**: Read the compiler output carefully. Common issues include undefined symbols (missing function declarations), type mismatches, or missing includes.
- **Missing icon**: If `pebble build` fails with icon size errors, ensure you ran `./generate_icon.sh pbw/pLauncher_pbw.kra --pbw` first. The icon must be a 25×25 PNG in `pbw/resources/images/app_launcher_icon.png`.

## Android Companion App Compilation

### Environment Variables

Before building, set the required variable:

```bash
export ANDROID_HOME=${HOME}/ANDROID/sdk
```

### Prerequisites

- Android SDK installed at `${HOME}/ANDROID/sdk`
- JDK 17 installed (the project's `gradle.properties` configures `org.gradle.java.home` to use Java 17 automatically)
- `ANDROID_HOME` environment variable set (see above)
- Android Studio (optional, for IDE-based development)
- Icon generated first (see "Icon Generation" above, `--apk` target)

### Build Command

```bash
cd apk/
./gradlew --no-daemon assembleDebug 2>&1 | tee build/gradle_build.log
```

This runs without spawning a Gradle daemon, produces verbose output, and saves the full log to `apk/build/gradle_build.log` for later review.

### Integrated Build Flow

The Gradle build automatically handles the watchapp and linting as part of the APK compilation:

1. **`commitHooks`**: Copies files from `config/hooks/` to `.git/hooks/` (e.g., the pre-commit hook). Runs on every build so hooks are installed automatically after a fresh clone.
2. **`lintPbw`**: Runs `clang-format --dry-run --Werror` on `pbw/src/c/*.c` and `*.h` to verify C code formatting. Uses `isIgnoreExitValue = true` and logs a warning on failure.
3. **`buildWatchapp`**: Runs `pebble build` to compile the watchapp for `basalt` and `emery`. Depends on `lintPbw`.
4. **`bundleWatchPbw`**: Copies the resulting `.pbw` into `apk/app/src/main/assets/` as `plauncher.pbw`. Skipped with a warning if the `.pbw` does not exist.
5. **`generatePbwInfo`**: Generates `pbw_info.txt` with version and MD5 checksum. Skipped with a warning if the `.pbw` does not exist.
6. **`lintApk`**: Runs `detektMain` to verify Kotlin code against `apk/config/detekt.yml`. Uses `ignoreFailures = false` — violations block the build.

Icon export tasks (`exportPbwIcon`, `exportApkIcon`) are **commented out** in the build file to prevent icon regeneration on every build. Icons must be generated manually using `generate_icon.sh` (see "Icon Generation" above).

`buildWatchapp` uses `isIgnoreExitValue = true` and logs warnings on failure. The APK still compiles even if the Pebble SDK is unavailable or the watchapp build fails — the warnings make failed steps visible in the output.

### Pre-commit Hook

After the first build, a pre-commit hook is automatically installed in `.git/hooks/pre-commit`. The hook runs on every `git commit`:

- **clang-format**: Checks staged `.c` and `.h` files in `pbw/src/c/` against `pbw/.clang-format`. Blocks the commit if formatting rules are violated.
- **detekt**: Runs `./gradlew detektMain` on the APK. Blocks the commit if linting rules are violated.

The hook script lives in `config/hooks/pre-commit` (tracked in git) and is copied to `.git/hooks/` by the `commitHooks` Gradle task on every build.

**Prerequisites for the hook**: `clang-format` must be available in PATH. The hook exits with a clear error message if it is not installed.

### Manual Lint Commands

You can run linting independently of commits or builds:

```bash
# Check C code formatting (dry run)
cd pbw/
find src/c -name "*.c" -o -name "*.h" | xargs clang-format --dry-run --Werror --style=file

# Fix C code formatting (in-place)
find src/c -name "*.c" -o -name "*.h" | xargs clang-format -i

# Check Kotlin code (runs detektMain)
cd apk/
./gradlew detektMain
```

### Output

- `.apk` file is generated in `apk/app/build/outputs/apk/debug/app-debug.apk`
- Bundled watchapp: `apk/app/src/main/assets/plauncher.pbw`

### Install to Device

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### Dependencies

- PebbleKit2: `io.rebble.pebblekit2:client:1.2.0`
- Jetpack Compose (via Android Gradle plugin)
- minSdk 21, targetSdk 34+

### Required Mobile App

- Core Pebble app v1.0.7.7 or newer, OR
- microPebble v1.0.0-alpha35 or newer

### Troubleshooting

- **Gradle sync issues**: Ensure `ANDROID_HOME` points to a valid SDK. Run `./gradlew --refresh-dependencies` if dependencies are stale.
- **Missing SDK**: Install the required Android SDK components via `sdkmanager` or Android Studio SDK Manager.
- **PebbleKit2 version conflicts**: The project uses `io.rebble.pebblekit2:client:1.2.0`. Check the [PebbleKitAndroid2 repository](https://github.com/pebble-dev/PebbleKitAndroid2) for compatibility notes.
- **Compose compilation errors**: Ensure Kotlin version matches Compose compiler version in `app/build.gradle.kts`.
- **`buildWatchapp` warning**: If the Pebble SDK is not installed or `pebble build` fails, you'll see a warning. The APK bundles the previous `.pbw` (if any) or no watchapp at all.
- **`bundleWatchPbw` / `generatePbwInfo` skipped**: These tasks skip silently if `pbw/build/pbw.pbw` does not exist (e.g., after a failed `pebble build`). Warnings are logged.
- **`lintPbw` warning**: If `clang-format` is not installed or finds formatting violations, you'll see a warning. The build still completes. Install `clang-format` or fix formatting with `clang-format -i`.
- **`lintApk` failure**: Detekt runs with `ignoreFailures = false`. If violations are found, the build fails. Fix the violations or update the baseline with `./gradlew detektGenerateBaseline`.
- **Pre-commit hook fails**: Ensure `clang-format` is in PATH. If detekt blocks commits, run `./gradlew detektMain` from `apk/` to see the violations and fix them before committing.
