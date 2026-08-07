# pLauncher project

* This folder contains an implementation of a launcher for Pebble smartwatch.

Two different kind of applications are available:

* The one for Pebble smartwatch itself, which can be found in `pbw` directory.
  * Programming language must be `C`.
* The one for Android smartphone, called "companion app", which can be found in `apk` directory. This application is needed for the Pebble smartwatch application to select which smartphone application to launch from the smartwatch.
  * Programming language must be `Kotlin`.

## Project structure

- `pbw/` — Pebble watch app (C, Pebble SDK v3)
- `apk/` — Android companion app (Kotlin + Jetpack Compose)
- `README.md` — Project overview and usage
- `AGENTS.md` — This file (development guidelines)
- `COMPILE_INSTRUCTIONS.md` — Detailed build instructions for both apps
- `COMMUNICATION_PROTOCOL.md` — AppMessage protocol specification
- `IMPLEMENTATION_SUMMARY_*.md` — Implementation summaries (numbered parts, may grow over time)

## Package name

- Android companion app: `com.le0xff.plauncher`

## Watch app details

- SDK: Pebble SDK v3
- Source: `pbw/src/c/`
- Target platforms: `basalt` and `emery` only
- Build command: `pebble build` (run from `pbw/` directory)
- UUID: `07b1efa9-3d32-423c-b0e7-572cbc0893b8`

## Android app details

- Language: Kotlin
- UI: Jetpack Compose
- Pebble integration: PebbleKit2 (`io.rebble.pebblekit2:client:1.2.0`)
- Build system: Gradle
- Build command: `./gradlew assembleDebug` (run from `apk/` directory)

## Protocol

See `COMMUNICATION_PROTOCOL.md` for the AppMessage dictionary keys and packet types.

## Compile instructions

See `COMPILE_INSTRUCTIONS.md` for detailed build steps, prerequisites, and troubleshooting.

## String resources

- All user-visible strings must be externalized, never hardcoded.
- **Android companion app (`apk/`)**: every string must be defined as a `<string>` entry in `app/src/main/res/values/strings.xml` and referenced via `stringResource(R.string.name)` (Compose) or `context.getString(R.string.name)` (non-Compose).
- **Pebble watch app (`pbw/`)**: every string must be defined as a `#define` or `const` in a dedicated header and referenced from the source code.
- All strings must be written in **English** regardless of the developer's language.

## Linting rules

Both apps must pass linting checks before code can be committed. A pre-commit hook enforces this automatically.

- **Pebble watch app (`pbw/`)**: C code must pass `clang-format --dry-run --Werror --style=file` (config in `pbw/.clang-format`). Format code with `clang-format -i`.
- **Android companion app (`apk/`)**: Kotlin code must pass `detekt` (config in `apk/config/detekt.yml`, baseline in `apk/config/detekt-baseline.xml`). Run `./gradlew detektMain` from `apk/`.
- The pre-commit hook (`config/hooks/pre-commit`) runs both checks on every `git commit`. It is installed automatically by the `commitHooks` Gradle task on every build.
- New code must comply with all active linting rules. Do not bypass rules by modifying the config or baseline without explicit approval.

## Development rules

- One feature per subagent
- Step-by-step implementation following the plan
- Use webfetch/MCP for research when needed
- File organization: each functionality in its own file, no monolithic files
- Every step must compile; errors must be resolved before proceeding
- All code must pass linting checks (clang-format for PBW, detekt for APK)
- User review after every step

## Pebble SDK include paths

- `basalt`: `${HOME}/.local/share/pebble-sdk/SDKs/current/sdk-core/pebble/basalt/include`
- `emery`: `${HOME}/.local/share/pebble-sdk/SDKs/current/sdk-core/pebble/emery/include`

## Useful Pebble documentation links

- Pebble Developer Site: `https://developer.repebble.com/`
- SDK Download: `https://developer.repebble.com/sdk/`
- Developer Guides: `https://developer.repebble.com/guides/`
- API Documentation: `https://developer.repebble.com/docs/`
- Communication Guide: `https://developer.repebble.com/guides/communication/`
- PebbleKit Android Guide: `https://developer.repebble.com/guides/communication/using-pebblekit-android/`
- Sending and Receiving Data: `https://developer.repebble.com/guides/communication/sending-and-receiving-data/`
- PebbleKit Android 2 Repository: `https://github.com/pebble-dev/PebbleKitAndroid2`
- CloudPebble (browser IDE): `https://cloudpebble.repebble.com`
- AppMessage API Reference: `https://developer.repebble.com/docs/c/Foundation/AppMessage/`
- App Publishing: `https://developer.repebble.com/dashboard`
- PebbleKit Android API: `https://developer.rebble.io/docs/pebblekit-android/com/getpebble/android/kit/PebbleKit/`
- Pebble hardware documentation: `https://developer.repebble.com/guides/tools-and-resources/hardware-information/`

## Android library references

- [Reorderable](https://github.com/Calvin-LL/Reorderable) — Drag-and-drop reorder for LazyColumn/LazyGrid in Jetpack Compose (v3.1.0, used in AppScreen.kt)
- [Reorderable Maven Central](https://central.sonatype.com/artifact/sh.calvin.reorderable/reorderable/3.1.0) — Artifact metadata

## Pebble additional details

The complete explanation about Pebble platform and its SDK can be found in: `${HOME}/PEBBLE/AGENTS.md`
