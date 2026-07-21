# pLauncher

A Pebble smartwatch app and Android companion app that lets you launch smartphone applications directly from your Pebble watch.

## How It Works

The Android companion app manages a list of apps you want to launch from your watch. The list is synced to the Pebble watch via AppMessage. On the watch, you scroll through the list and select an app to launch it on your phone.

## Features

- **Watch App**: Scroll through your configured apps and launch them with button presses
- **Companion App**: Manage which apps appear on the watch, pick from installed apps, configure settings
- **System Apps Toggle**: Optionally include system applications in the app picker
- **Search**: Filter apps by name in the companion app

## Button Controls (Watch)

- **UP/DOWN**: Scroll through the list of configured apps
- **SELECT**: Launch the currently highlighted app on your phone
- **BACK**: Close the launcher and return to the watch face

## Companion App Screens

- **App List**: Shows the apps currently configured for the watch, with a "+" button to add more
- **Settings**: Toggle options like "Show system apps"
- **App Picker Dialog**: Browse installed apps, search, and select which ones to add to the watch

## Communication

The watch and companion app communicate via PebbleKit Android 2 and AppMessage. See `COMMUNICATION_PROTOCOL.md` for the protocol specification.

## Build Instructions

See `COMPILE_INSTRUCTIONS.md` for detailed build steps for both the watch app and Android companion app.

## Quick Build Commands

- **Watch App**: `pebble build` (run from `pbw/` directory)
- **Android App**: `./gradlew assembleDebug` (run from `apk/` directory)
