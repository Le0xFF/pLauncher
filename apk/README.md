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
- **Play on Launch**: Automatically resumes a launched app's music playback from the watch, waking the screen if needed and turning it back off afterwards (see [Play on Launch](#play-on-launch)).
- **Watchapp Installer**: Install the bundled watch app (`.pbw`) directly from the companion app settings. Inspired by [pebble-steer](https://github.com/bquelhas/pebble-steer).
- **Permissions**: Guides you to grant "Draw Over Other Apps" and "Ignore Battery Optimizations", required for launching apps from the background service.
- **Debug**: Crash reports, session log saving.
- **Boot Receiver**: Automatically starts the foreground service on device boot.

## Play on Launch

When enabled (Companion app settings → "Play on Launch"), launching an app from the watch also makes the companion app automatically resume that app's music playback — with no touch on the phone. The whole flow runs on the phone; nothing is synced to the watch and the launch protocol is unchanged.

### How it works

1. **Screen wake.** If the phone is locked/asleep when the launch arrives, the app turns the screen on (over the keyguard) before starting the target app and keeps it on for the duration of the flow. This gives a cold-started player time to initialize and register its media session. If the screen was already on, its state is never touched.
2. **First phase — wait for the media session.** A `NotificationListenerService` polls (every 500 ms) for the target app's active `MediaSession`. As soon as one is present it sends `play()` and confirms the playback actually became *playing*. This phase is bounded by the **Initial wait before re-launch** timer: if the session has been present but not playable for that long, the phase stops (re-sending `play()` to an un-initialized session does nothing).
3. **Re-launch + second phase.** If the first phase didn't succeed, the target app is re-launched via its launcher intent and polling resumes, now bounded by the **Resume timeout**. On success the same `play()` + verification is applied.
4. **Fallback.** Only if both phases fail does the app send a legacy `ACTION_MEDIA_BUTTON` play broadcast to the target package (many modern music apps ignore this, so the two phases above are the primary path).
5. **Screen sleep.** Once the flow reaches any final outcome (playback resumed, or fallback sent), the screen is turned back off — but only if the app itself had woken it. If you launched while the screen was already on, it stays on.

The companion must have **Notification access** granted (Settings → Permissions → Notification access); without it the feature degrades to the legacy broadcast fallback.

### User-configurable timers

Both values are set in Companion app settings, directly below the "Play on Launch" switch (the fields are disabled while the switch is off). They are stored locally on the phone and are never sent to the watch.

| Setting | What it controls | Default | Range |
|---|---|---|---|
| **Initial wait before re-launch (s)** | How long the first phase waits for the launched app's media session to become playable *before* the app is re-launched. Lower values resume faster for apps that start quickly, but can re-launch a slow app before its player is ready. Raise it for apps like TIDAL whose cold start takes longer. | 30 s | 1–60 s |
| **Resume timeout (s)** | The maximum time the second phase keeps waiting for the media session *after* the automatic re-launch, before giving up and sending the legacy play command. A built-in floor of 20 s always applies, so even a low value waits at least 20 s. | 60 s | 1–60 s |

Typical tuning: leave both at their defaults. If your music app starts reliably fast, lower the initial wait to get playback sooner. If it still isn't playing after the re-launch, raise the resume timeout to give the second pass more room.

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
