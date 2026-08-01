# Communication Protocol

Watch and companion app communicate via Pebble AppMessage using numeric dictionary keys. No Bucketsync, no binary framing.

## AppMessage Keys

| Key | Type   | Direction       | Meaning                              |
|-----|--------|-----------------|--------------------------------------|
| 0   | UInt8  | Both            | Packet type                          |
| 1   | UInt16 | Both            | Protocol version                     |
| 2   | UInt8  | Watch → Phone   | App index to launch                  |
| 3   | UInt8  | Phone → Watch   | Number of apps in list               |
| 4   | CStr   | Phone → Watch   | App display name (per app)           |
| 5   | CStr   | Phone → Watch   | App package name (per app)           |
| 6   | UInt8  | Phone → Watch   | Transfer ID (for deduplication)      |
| 8   | UInt16 | Phone → Watch   | Offset for chunked lists             |
| 9   | UInt8  | Phone → Watch   | Completion flag (1 = last chunk)     |
| 10  | UInt8  | Phone → Watch   | Launch confirm flag (1 = success, 0 = failure) |
| 11  | UInt8  | Phone → Watch   | Vibration preference (0 = None, 1 = Short, 2 = Long, 3 = Double) |
| 12  | UInt8  | Phone → Watch   | Auto-close preference (1 = enabled, 0 = disabled) |
| 13  | UInt8  | Phone → Watch   | Auto-launch enabled (1 = enabled, 0 = disabled)   |
| 14  | UInt8  | Phone → Watch   | Auto-launch target index (0-based)                |
| 15  | UInt8  | Watch → Phone   | Display type (0 = B/W, 1 = Color) — sent in Watch Welcome |
| 16  | Data   | Phone → Watch   | App icon binary data (32×32 pixels). Format depends on KEY_DISPLAY_TYPE: Color = 1,024 bytes (GColor8, 1 byte/pixel, 0bAARRGGBB), B/W = 128 bytes (1-bit, 4-byte row padding, MSB first) |

## Packet Types

### Watch → Phone
- `0`: Watch Welcome — keys: `1` (protocol version uint16), `15` (display type uint8: 0=B/W, 1=Color)
- `1`: Launch App — keys: `2` (app index uint8)

### Phone → Watch
- `10`: Phone Welcome — keys: `1` (protocol version uint16)
- `11`: App List — keys: `3` (count), then per app: `4` (name), `5` (package), `16` (icon data, optional)
- `12`: Launch Confirm — keys: `10` (confirm flag uint8, 1 = success, 0 = failure)
- `13`: Vibration Preference — keys: `11` (vibration pref uint8, 0 = None, 1 = Short, 2 = Long, 3 = Double)
- `14`: Auto-Close Preference — keys: `12` (auto-close flag uint8, 1 = enabled, 0 = disabled)
- `15`: Auto-Launch Enabled — keys: `13` (auto-launch enabled uint8, 1 = enabled, 0 = disabled)
- `16`: Auto-Launch Target — keys: `14` (auto-launch target index uint8, 0-based)

## Protocol Version

Current version: `1`

## Chunked App Lists

If the app list exceeds AppMessage size limits (~1400 bytes), send multiple packet 11 messages:
- Each chunk includes key `8` (offset) for starting position
- Each chunk includes key `6` (transfer ID, UInt8) — incremented by the phone on every new list send
- Last chunk includes key `9` set to `1` (completion flag)
- Watch accumulates chunks until completion flag received
- The watch discards chunks with a transfer ID lower than the current transfer, allowing overlapping transfers to be resolved without duplicates

## Settings

No settings sync from watch to phone. The watch only launches apps. Settings (e.g., "show system apps") are managed entirely in the Android companion app. The vibration preference and auto-close preference are synchronized from the phone to the watch on connection and when changed.

## Icon Formats

The phone stores both formats of every app icon and sends the one matching the watch's display type (from key `15`).

### Color (GColor8)
- 32×32 pixels, row-major, 1 byte/pixel = **1,024 bytes**
- Each byte: `0bAARRGGBB` (AA=alpha 2-bit, RR=red 2-bit, GG=green 2-bit, BB=blue 2-bit)
- Alpha: 3 = opaque, 0 = transparent

### B/W (1-bit)
- 32×32 pixels, row-major, 1 bit/pixel (MSB first) = **128 bytes**
- Each row padded to 4 bytes (32-bit alignment): 4 bytes data, no extra padding
- Bit 1 = white, 0 = black
