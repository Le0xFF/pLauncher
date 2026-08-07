# pLauncher Companion App

An Android companion app that manages the list of smartphone apps launchable from your Pebble watch, maintains the Bluetooth connection, and installs the watch app.

## Overview

The companion app lets you configure which smartphone apps appear in pLauncher on your Pebble watch. It maintains a persistent connection with the watch via a foreground service, handles launch requests, and syncs settings like vibration feedback and auto-launch. The app also bundles the watch app (`.pbw`) for direct installation.

## Features

- **App List Management**: Add, remove, rename, and reorder apps using drag-and-drop. Sort alphabetically (A-Z / Z-A). Search within the list. Maximum of 20 apps.
- **App Picker**: Dialog to select installed apps with search and system app filter.
- **Watchapp Settings**: Configure vibration feedback (None, Short, Long, Double), auto-close on launch, and auto-launch on open. Settings sync to the watch in real time.
- **Appearance**: Three themes — Light, Dark, AMOLED.
- **Import/Export**: Export and import the entire app list in YAML format, including display names, positions, and auto-launch target.
- **Background Service**: Foreground service maintains the Pebble Bluetooth connection even when the app is in the background, using a wake lock to prevent processing delays.
- **Watchapp Installer**: Install the bundled watch app (`.pbw`) directly from the companion app settings. Inspired by [pebble-steer](https://github.com/bquelhas/pebble-steer).
- **Permissions**: Guides you to grant "Draw Over Other Apps" and "Ignore Battery Optimizations", required for launching apps from the background service.
- **Debug**: Crash reports, session log saving.
- **Boot Receiver**: Automatically starts the foreground service on device boot.

## Requirements

- Watch app pLauncher installed on the Pebble
- Pebble app (Core Pebble v1.0.7.7+ or microPebble v1.0.0-alpha35+) for watch management
- Android 7.0+ (minSdk 24)
- Required permissions for background launching: Draw Over Other Apps, Ignore Battery Optimizations

## Build & Install

```sh
cd apk/
./gradlew assembleDebug
```

See [../COMPILE_INSTRUCTIONS.md](../COMPILE_INSTRUCTIONS.md) for detailed build steps, prerequisites, and troubleshooting.

## Communication

The companion app communicates with the watch app via AppMessage. See [../COMMUNICATION_PROTOCOL.md](../COMMUNICATION_PROTOCOL.md) for the full protocol specification.

## License

This project is licensed under AGPLv3. See [../LICENSE.md](../LICENSE.md).
