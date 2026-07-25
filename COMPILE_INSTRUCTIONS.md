# Compilation Instructions

## Watch App Compilation

### Prerequisites

- Pebble SDK installed (`pebble` command available in PATH)
- SDK location: `${HOME}/.local/share/pebble-sdk/SDKs/current/`
- ARM GCC toolchain (`arm-none-eabi-gcc`, `arm-none-eabi-ar`)

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

### Build Command

```bash
cd apk/
./gradlew --no-daemon assembleDebug 2>&1 | tee build/gradle_build.log
```

This runs without spawning a Gradle daemon, produces verbose output, and saves the full log to `apk/build/gradle_build.log` for later review.

### Output

- `.apk` file is generated in `apk/app/build/outputs/apk/debug/app-debug.apk`

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
