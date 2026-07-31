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

## Packet Types

### Watch → Phone
- `0`: Watch Welcome — keys: `1` (protocol version uint16)
- `1`: Launch App — keys: `2` (app index uint8)

### Phone → Watch
- `10`: Phone Welcome — keys: `1` (protocol version uint16)
- `11`: App List — keys: `3` (count), then pairs of `4`/`5` per app

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

No settings sync from watch to phone. The watch only launches apps. Settings (e.g., "show system apps") are managed entirely in the Android companion app.
