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
- `IMPLEMENTATION_SUMMARY_1.md` — Implementation summary part 1
- `IMPLEMENTATION_SUMMARY_2.md` — Implementation summary part 2
- `IMPLEMENTATION_SUMMARY_3.md` — Implementation summary part 3

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

## Development rules

- One feature per subagent
- Step-by-step implementation following the plan
- Use webfetch/MCP for research when needed
- File organization: each functionality in its own file, no monolithic files
- Every step must compile; errors must be resolved before proceeding
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

## Pebble additional details

The complete explanation about Pebble platform and its SDK can be found in: `${HOME}/PEBBLE/AGENTS.md`
