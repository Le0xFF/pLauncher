# pLauncher Watch App

A Pebble watch app that displays a list of smartphone apps and lets you launch them directly from your watch.

## Overview

pLauncher shows the configured list of smartphone apps on your Pebble watch. Navigate through the list using the watch buttons, view each app's name and icon, and launch the selected app on your paired smartphone via the companion app.

## Features

- Circular navigation through up to 20 configured apps
- Adaptive 32×32 app icons (1-bit B/W or color GColor8 depending on display). UI icons from [pebble-dev/iconography](https://github.com/pebble-dev/iconography).
- Configurable vibration feedback (None, Short, Long, Double)
- Auto-close after a successful launch
- Auto-launch of a selected app on startup
- Loading and empty list states

## Button Controls

| Button | Action |
|--------|--------|
| UP | Navigate to previous app (repeating for fast scroll) |
| DOWN | Navigate to next app (repeating for fast scroll) |
| SELECT | Launch the currently displayed app |

Button input is disabled while the app list is still loading.

## Requirements

- **Companion app**: `com.le0xff.plauncher` installed on your Android smartphone
- **Pebble app**: Core Pebble v1.0.7.7+ or microPebble v1.0.0-alpha35+ to manage and install the watch app
- **Supported platforms**: `basalt` (Pebble Time Steel), `emery` (Pebble Time 2)
- **SDK**: Pebble SDK v3, written in C

## Build & Install

```sh
cd pbw/
pebble build
pebble install
```

See [../COMPILE_INSTRUCTIONS.md](../COMPILE_INSTRUCTIONS.md) for detailed build steps, prerequisites, icon generation, and troubleshooting.

## Communication

The watch app communicates with the companion app via AppMessage. See [../COMMUNICATION_PROTOCOL.md](../COMMUNICATION_PROTOCOL.md) for the full protocol specification.

## License

This project is licensed under AGPLv3. See [../LICENSE.md](../LICENSE.md).
