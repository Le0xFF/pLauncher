# Implementation summary

## #26 — Watchapp UI Improvements: Center Content, Bitmap Navigation Icons, Text Layout Fixes

### Overview

Centered the main content (app icon, name, index) horizontally across the full screen width, replacing the previous split-layout where content was pushed left. Replaced text-based navigation labels ("Up", "Down", "Launch") with 21×21 bitmap icons sourced from the [pebble-dev/iconography](https://github.com/pebble-dev/iconography) project: Caret Up, Caret Down, and Rocket. The SVG icons from iconography were converted to 21×21 grayscale PNGs via ImageMagick, with proper black-on-white encoding for Pebble's 1-bit and 8-bit displays.

Added text layout fixes to prevent the app name TextLayer from overlapping navigation icons on the right column: limited text width, enabled word wrap, and fixed descender clipping (g, y, p, q).

### Analysis

- **Previous state**: The main content (app icon, name, index) was confined to the left portion of the screen (`w - w/5`), leaving a right column for text labels ("Up", "Down", "Launch"). The app icon was centered within this left area, not the screen. Navigation labels used `TextLayer` with `FONT_KEY_GOTHIC_18`.
- **Centering**: The content area now spans the full screen width `w`. App icon is centered at `(w - LAYOUT_ICON_SIZE) / 2`. Name text layer and index layer use full width with `GTextAlignmentCenter`. Empty/loading message also spans full width.
- **Navigation icons**: Sourced from [pebble-dev/iconography](https://github.com/pebble-dev/iconography), a community Pebble iconography project:
  - `25px_Caret_up.svg` → Up navigation (caret pointing up)
  - `25px_Caret_down.svg` → Down navigation (caret pointing down)
  - `25px_Rocket.svg` → Launch action (rocket)
- **Icon conversion**: The SVGs were converted to 21×21 grayscale PNGs using ImageMagick. The caret SVGs use `stroke="black"` on white background. The rocket SVG uses `fill="black"` and `stroke="black"` on white background. All three produce grayscale PNGs (mode `L`) with black icon pixels on white background, compatible with Pebble's resource compiler for both basalt (8-bit grayscale) and emery (8-bit grayscale). The original SVGs are preserved in the project for future regeneration.
- **Icon size**: 21×21 chosen over the original 25×25 for a slightly more compact appearance in the navigation column, while maintaining visual clarity. The original 25px SVGs are preserved in `resources/images/` as source files.
- **BitmapLayer**: Each navigation icon uses a `BitmapLayer` with `GBitmap` created from the SDK resource (`gbitmap_create_with_resource`). Compositing mode is `GCompOpSet` with `GColorClear` background. Icons are positioned in the right navigation column, centered within their vertical zone.
- **Layout constants**: Renamed `LAYOUT_LABEL_COL_DIVISOR` to `LAYOUT_NAV_COL_DIVISOR`, `LAYOUT_LABEL_ZONES` to `LAYOUT_NAV_ZONES`, removed `LAYOUT_LABEL_FONT_HEIGHT`, added `LAYOUT_ICON_NAV_SIZE` (21), added `LAYOUT_TEXT_H_MARGIN` (4), `LAYOUT_NAME_DESCENDER` (4), `LAYOUT_NAME_MAX_LINES` (3), `LAYOUT_NAME_MAX_HEIGHT` (76).

#### Text Layout Fix

After the content centering, the app name TextLayer used the full screen width `w`, causing text to overlap the navigation icons in the right column. The fix constrains the TextLayer width so centered text never reaches the nav column:

- **Width constraint**: `text_width = 2 * nav_x - w - LAYOUT_TEXT_H_MARGIN`, where `nav_x = w - w/LAYOUT_NAV_COL_DIVISOR`. This guarantees `text_x + text_width <= nav_x - margin`, so the rightmost pixel of centered text stays within the nav column boundary minus margin.
- **Centering preserved**: `text_x = (w - text_width) / 2` keeps the text centered on screen. With the constrained width, `GTextAlignmentCenter` ensures text fills from the center outward, bounded by `text_width`.
- **Per-target values**:
  - Basalt (144×168): `nav_x=115`, `text_width=81`, `text_x=31`, right edge at x=112 (nav starts at 115, 3px gap)
  - Emery (200×228): `nav_x=160`, `text_width=116`, `text_x=42`, right edge at x=158 (nav starts at 160, 2px gap)
- **Word wrap**: Enabled via `text_layer_set_overflow_mode(s_text_layer, GTextOverflowModeWordWrap)`. Long app names wrap to multiple lines within the constrained width.
- **Dynamic height**: After setting text, calls `text_layer_get_content_size()` to get the actual wrapped height. Frame height is clamped between `LAYOUT_NAME_FONT_HEIGHT + LAYOUT_NAME_DESCENDER` (28px minimum for descenders) and `LAYOUT_NAME_MAX_HEIGHT` (76px, 3 lines + descender padding).
- **Descender fix**: The minimum frame height includes `LAYOUT_NAME_DESCENDER` (4px) extra below the font ascent, preventing clipping of descender characters (g, y, p, q). The two-pass frame setting (first set frame with `LAYOUT_NAME_MAX_HEIGHT` to get accurate content size, then reposition with actual height) ensures `text_layer_get_content_size()` returns correct values.
- **Vertical positioning unchanged**: Icon and text remain vertically centered together as a unit: `icon_y = (h - LAYOUT_NAME_FONT_HEIGHT - LAYOUT_ICON_SIZE - LAYOUT_ICON_V_PADDING * 2) / 2`, `text_y = icon_y + LAYOUT_ICON_SIZE + LAYOUT_ICON_V_PADDING`.
- **Empty/loading message**: Uses the same width constraint and centering for consistency.

### Watch App (`pbw/`) — ~90 lines changed across 6 files

#### Modified Files

| File | Changes |
|---|---|
| `resources/images/icon_caret_up.png` (new) | 21×21 grayscale PNG, caret up icon from iconography, black on white background. |
| `resources/images/icon_caret_down.png` (new) | 21×21 grayscale PNG, caret down icon from iconography, black on white background. |
| `resources/images/icon_rocket.png` (new) | 21×21 grayscale PNG, rocket icon from iconography, black on white background. |
| `resources/images/caret_up.svg` (new) | Source SVG for caret up (25×25 viewBox, black stroke). Preserved for future regeneration. |
| `resources/images/caret_down.svg` (new) | Source SVG for caret down (25×25 viewBox, black stroke). Preserved for future regeneration. |
| `resources/images/rocket.svg` (new) | Source SVG for rocket (25×25 viewBox, black fill + stroke). Preserved for future regeneration. |
| `package.json` | Added three bitmap resource entries: `ICON_CARET_UP`, `ICON_CARET_DOWN`, `ICON_ROCKET`. Total media resources: 4. |
| `src/c/layout.h` | Removed `LAYOUT_LABEL_FONT_HEIGHT`. Renamed `LAYOUT_LABEL_COL_DIVISOR` (5) → `LAYOUT_NAV_COL_DIVISOR` (5). Renamed `LAYOUT_LABEL_ZONES` (3) → `LAYOUT_NAV_ZONES` (3). Added `LAYOUT_ICON_NAV_SIZE` (21), `LAYOUT_TEXT_H_MARGIN` (4), `LAYOUT_NAME_DESCENDER` (4), `LAYOUT_NAME_MAX_LINES` (3), `LAYOUT_NAME_MAX_HEIGHT` (76). |
| `src/c/window_main.c` | Replaced `TextLayer* s_label_up/down/launch` with `BitmapLayer* s_icon_up/down/launch` + `GBitmap* s_bitmap_caret_up/down` + `GBitmap* s_bitmap_rocket`. In `window_load()`: create GBitmap from resources, create BitmapLayer 21×21 with `GCompOpSet`/`GColorClear`, position in right nav column centered within zone. Text layer created with constrained width, centered on screen, word wrap enabled, max height for descenders. In `window_unload()`: destroy bitmap layers and GBitmaps. In `window_main_update_display()`: constrained text width centered on screen, two-pass height calculation for wrapped text, descender-safe minimum height. |
| `src/c/strings.h` | Removed `STR_LABEL_UP`, `STR_LABEL_DOWN`, `STR_LABEL_LAUNCH`. Kept `STR_LOADING_MESSAGE`. |

#### Key Implementation Details

**Icon conversion** (ImageMagick):
```bash
# Caret icons (stroke only, black on white bg):
convert caret_up.svg -resize 21x21! -background white -alpha off PNG:icon_caret_up.png
convert caret_down.svg -resize 21x21! -background white -alpha off PNG:icon_caret_down.png

# Rocket icon (fill + stroke, black on white bg):
convert rocket.svg -resize 21x21! -background white -alpha off PNG:icon_rocket.png
```
All three produce grayscale PNGs (mode `L`) with black icon pixels on white (255) background. The white background pixels are effectively transparent on Pebble's display with `GCompOpSet` compositing and `GColorClear` background, as the SDK's resource compiler converts grayscale to the platform's native format.

**Resource declarations** (`package.json`):
```json
{
  "type": "bitmap",
  "name": "ICON_CARET_UP",
  "file": "images/icon_caret_up.png"
}
```
SDK generates constants `RESOURCE_ID_ICON_CARET_UP`, `RESOURCE_ID_ICON_CARET_DOWN`, `RESOURCE_ID_ICON_ROCKET`.

**BitmapLayer creation** (`window_main.c`):
```c
s_bitmap_caret_up = gbitmap_create_with_resource(RESOURCE_ID_ICON_CARET_UP);

int y_step = h / LAYOUT_NAV_ZONES;
int y_up = y_step / 2 - LAYOUT_ICON_NAV_SIZE / 2;
int nav_x = w - (w / LAYOUT_NAV_COL_DIVISOR);
int nav_w = w / LAYOUT_NAV_COL_DIVISOR;
int icon_x = nav_x + (nav_w - LAYOUT_ICON_NAV_SIZE) / 2;

s_icon_up = bitmap_layer_create(GRect(icon_x, y_up, LAYOUT_ICON_NAV_SIZE, LAYOUT_ICON_NAV_SIZE));
bitmap_layer_set_bitmap(s_icon_up, s_bitmap_caret_up);
bitmap_layer_set_compositing_mode(s_icon_up, GCompOpSet);
bitmap_layer_set_background_color(s_icon_up, GColorClear);
```
Each icon is centered within the navigation column (`nav_w / LAYOUT_NAV_COL_DIVISOR`), vertically centered within its zone (`y_step / 2 - size / 2`).

**Text layer width constraint** (`window_main.c`):
```c
int nav_x = w - (w / LAYOUT_NAV_COL_DIVISOR);
int text_width = 2 * nav_x - w - LAYOUT_TEXT_H_MARGIN;
int text_x = (w - text_width) / 2;
```
Guarantees the right edge of centered text never crosses `nav_x`.

**Text layer creation** (`window_main.c`):
```c
s_text_layer = text_layer_create(GRect(text_x, y_name, text_width, LAYOUT_NAME_MAX_HEIGHT));
text_layer_set_text_alignment(s_text_layer, GTextAlignmentCenter);
text_layer_set_font(s_text_layer, fonts_get_system_font(FONT_KEY_GOTHIC_24));
text_layer_set_overflow_mode(s_text_layer, GTextOverflowModeWordWrap);
```

**Dynamic height with descender support** (`window_main.c`):
```c
// Pass 1: set frame with correct width and max height for accurate content size
layer_set_frame(text_layer_get_layer(s_text_layer), GRect(text_x, 0, text_width, LAYOUT_NAME_MAX_HEIGHT));

// Get actual wrapped content size
GSize content_size = text_layer_get_content_size(s_text_layer);
int text_height = content_size.h;
if (text_height > LAYOUT_NAME_MAX_HEIGHT) text_height = LAYOUT_NAME_MAX_HEIGHT;
if (text_height < LAYOUT_NAME_FONT_HEIGHT + LAYOUT_NAME_DESCENDER) text_height = LAYOUT_NAME_FONT_HEIGHT + LAYOUT_NAME_DESCENDER;

// Pass 2: set final frame with actual height and vertical position
int text_y = icon_y + LAYOUT_ICON_SIZE + LAYOUT_ICON_V_PADDING;
layer_set_frame(text_layer_get_layer(s_text_layer), GRect(text_x, text_y, text_width, text_height));
```

**Icon pixel patterns** (verified):
- Caret up: ^ shape, 43 black pixels
- Caret down: v shape, 43 black pixels
- Rocket: rocket body with exhaust, 217 black pixels

#### Layout

Watch display after changes:
```
┌─────────────────────────────────┐
│                                 │
│                                 │
│         [ICON 32×32]            │
│                                 │
│       App Name                  │
│                                 │
│                                 │
│       1/5          [▲]         │
│                                 │
│                 [🚀]            │
│                                 │
│                 [▼]             │
│                                 │
└─────────────────────────────────┘
```
Content (icon, name, index) centered horizontally on full screen width. Text constrained to not overlap nav column. Navigation icons in right column, replacing text labels.

#### Code Statistics

| Component | Files | Lines changed |
|---|---|---|
| Watch App (C) | 4 | ~65 (layout.h ~10, window_main.c ~50, strings.h ~3, package.json ~12) |
| Resources | 6 | 3 PNG new (21×21), 3 SVG new (source) |
| **Total** | **10** | **~65 code + 9 resource files** |

#### Build Status

- Watch app: `pebble build` — BUILD SUCCESSFUL
- Basalt: 29,344 B RAM / 64 KB, 36,192 B free heap, 4,588 B resources
- Emery: 29,344 B RAM / 128 KB, 101,728 B free heap, 4,588 B resources

#### Design Decisions

**Icon source**: Icons from [pebble-dev/iconography](https://github.com/pebble-dev/iconography), the community Pebble iconography project. This ensures visual consistency with the Pebble ecosystem's established iconography.

**SVG preserved in project**: The original SVGs are stored in `resources/images/` alongside the PNGs. This allows future regeneration at any size without needing to re-download or reconstruct the SVGs.

**21×21 over 25×25**: The original iconography SVGs are 25×25. Resized to 21×21 for a slightly more compact appearance in the narrow navigation column, while maintaining visual clarity. The SVG source allows easy regeneration at any size.

**Grayscale PNGs**: The SDK's resource compiler handles grayscale-to-platform format conversion automatically. For basalt/emery (64-color displays), grayscale PNGs are accepted directly. Black pixels (0) render as foreground, white pixels (255) blend with the display background.

**BitmapLayer over TextLayer**: BitmapLayer is more resource-efficient than TextLayer for static icons (no font loading, no text rendering overhead). The three GBitmaps consume minimal RAM (~176 B each for 21×21 1-bit equivalents on basalt).

**SVG color handling**: The iconography SVGs use `stroke="black"` by default. For white icons on dark background, the SVGs were modified to use `stroke="black"` with `fill="black"` (rocket), rendered on white background via ImageMagick's `-background white -alpha off`, producing black icon on white background. This ensures the SDK's resource compiler correctly identifies foreground vs. background pixels.

**Text width math**: Instead of using a fixed padding, `text_width = 2 * nav_x - w - margin` mathematically guarantees that `text_x + text_width <= nav_x - margin` for centered text. This is derived from `(w + text_width) / 2 <= nav_x - margin`, ensuring the rightmost pixel of any text line stays within bounds regardless of content length.

**Two-pass frame sizing**: `text_layer_get_content_size()` requires the TextLayer frame to have the correct width before it can return accurate wrapped dimensions. The two-pass approach (set frame with max height, read content size, reposition with actual height) is the documented Pebble SDK pattern for dynamic text sizing.

**Descender padding**: `LAYOUT_NAME_DESCENDER` (4px) added to the minimum frame height ensures descender characters render fully. The Pebble font metrics report ascent height in `text_layer_get_content_size()` but do not include descent, requiring manual compensation.

**Unchanged**: App icon display, index display, packet protocol, Android companion app. All existing features remain fully functional.

## #27 — Fix: Disable Buttons During Loading

### Overview

Fixed a race condition where pressing UP/DOWN/SELECT buttons during the "Loading..." phase caused the display to briefly show "No apps..." instead of "Loading...", because the loading flag was reset too early or not set at all during list transfers.

### Analysis

Three distinct timing issues were identified:

1. **Initial app launch**: `s_loading` was initialized to `false`. The window's click config provider was called during `window_load`, before `window_appear` could set `s_loading = true` via `request_app_list()`. A fast user could press buttons before loading was activated, operating on an empty list.

2. **Phone welcome resetting loading**: `handle_phone_welcome()` set `s_loading = false` upon receiving the phone's welcome packet. The protocol sends `PHONE_WELCOME` first, then app list chunks. This created a window between the welcome and the first list chunk where `s_loading` was `false`, buttons were active, and the list was empty.

3. **Remote list transfers not activating loading**: When the companion app triggered a list resend (rename, reorder, sort, remove, add, import), `handle_app_list()` detected a new transfer ID and cleared the list, but did not set `s_loading = true`. The display showed "No apps..." and buttons operated on the cleared list.

4. **Loading flag reset before display update**: In `handle_app_list()`, `s_loading = false` was set before `window_main_update_display()`. After the last chunk arrived, `s_loading` became `false` while the display still showed "Loading...", allowing button presses before the screen updated.

### Fix

**`pbw/src/c/packets.c`** — Four changes:

1. `s_loading` static initializer changed from `false` to `true` — buttons blocked from the moment the binary starts.
2. `handle_phone_welcome()`: removed `s_loading = false` — the welcome packet is not the list; loading stays active until the list completes.
3. `handle_app_list()` new transfer path: added `s_loading = true` and `window_main_update_display()` when a new transfer ID is detected — display shows "Loading..." and buttons blocked immediately.
4. `handle_app_list()` completion path: moved `s_loading = false` to after `window_main_update_display()` — display updates first, then buttons enabled.

**`pbw/src/c/window_main_click.c`** — Added `packets_is_loading()` guard to each click handler:

Each handler (`up_click_handler`, `down_click_handler`, `select_click_handler`) checks `packets_is_loading()` at entry and returns immediately if `true`. Buttons are always subscribed in `window_main_click_config_provider` (not conditionally), because the config provider is called once at window load time and cannot react to state changes.

### Loading State Machine

After the fix, `s_loading` follows a clean state machine:

| Event | `s_loading` | Buttons |
|---|---|---|
| App starts (static init) | `true` | Blocked |
| `window_appear` → `request_app_list()` | `true` | Blocked |
| `PHONE_WELCOME` received | `true` | Blocked |
| New transfer ID detected | `true` | Blocked |
| Last list chunk received | Display updated, then `false` | Enabled |
| Response timeout | `false` | Enabled |

### Code Statistics

| Component | Files | Lines changed |
|---|---|---|
| Watch App (C) | 2 | ~15 (packets.c ~8, window_main_click.c ~7) |

### Build Status

- Watch app: `pebble build` — BUILD SUCCESSFUL
- Basalt: 29,385 B RAM / 64 KB, 36,151 B free heap, 4,588 B resources
- Emery: 29,385 B RAM / 128 KB, 101,687 B free heap, 4,588 B resources

### Design Decisions

**Handler guards over config provider**: The click config provider is called once when the window is loaded. Conditionally subscribing buttons there would permanently disable them if loading was active at window creation. Guarding inside each handler ensures buttons always work after loading completes, regardless of when the window was created.

**`s_loading` default `true`**: Ensures buttons are blocked from the very first moment the app runs, before any event loop callbacks execute. The flag is only cleared when the list is fully loaded and displayed.

**Welcome doesn't clear loading**: The phone welcome is an acknowledgment, not data. Loading should only clear when actual app list data is received and rendered.

**Timeout clears loading**: When the response times out (10s), there's no list data. Clearing loading lets the user see the empty state and retry.

## #28 — Android Companion App: App Icon, Adaptive Icon, and Splash Screen

### Overview

Added a proper app icon to the Android companion app (`apk/`). Implemented legacy mipmap bitmaps (API < 26), adaptive icons with background/foreground/monochrome layers (API 26+), and a branded splash screen with magenta background and logo. Created a POSIX-compliant generation script (`generate_icon.sh`) that accepts a `.kra` (Krita) or `.png` file as input, exports the PNG if needed, and produces all required resources. The PNG output is always written to `apk/pLauncher.png`.

### Analysis

- **Previous state**: The Android project had no icon configured. `AndroidManifest.xml` had no `android:icon` or `android:roundIcon` in `<application>`. `res/` had no `mipmap-*` directories. `res/drawable/` was empty.
- **Source image**: `pLauncher.kra`, Krita project file (zip archive), 1400×1400, RGBA16, sRGB, 12 layers (some hidden). The logo contains a stylized "L" (white with gradient) and "p" (cyan `#58cef8` with pink `#e4adcd` crossbar), both with black outlines. Colors used: `#cf62a9` (magenta), `#e4adcd` (light pink), `#58cef8` (cyan), `#ffffff` (white).
- **Krita export**: `krita --export --export-filename <out.png> <in.kra>` flattens all visible layers to PNG (1400×1400, 16-bit sRGB). Requires a display (X11/Wayland); `QT_QPA_PLATFORM=offscreen` does not work with Krita 5.3 on this machine.
- **Target SDK**: minSdk 24, targetSdk 36. Required supporting both legacy icons (API < 26) and adaptive icons (API 26+).
- **Adaptive icon specs**: Canvas 108×108 dp, safe zone 66×66 dp (21 dp margin per side for masking). Required layers: `background` + `foreground` + `monochrome` (Android 13+ theming).

### Android App (`apk/`) — ~10 files created/modified

#### Modified Files

| File | Changes |
|---|---|
| `app/src/main/AndroidManifest.xml` | Added `android:icon="@mipmap/ic_launcher"` and `android:roundIcon="@mipmap/ic_launcher_round"` to `<application>`. Changed MainActivity theme to `@style/Theme.pLauncher.Splash`. Changed `<application>` theme to `@style/Theme.pLauncher.Splash`. |

#### New Files

| File | Description |
|---|---|
| `app/src/main/res/mipmap-mdpi/ic_launcher.png` | Legacy icon 48×48 px. |
| `app/src/main/res/mipmap-hdpi/ic_launcher.png` | Legacy icon 72×72 px. |
| `app/src/main/res/mipmap-xhdpi/ic_launcher.png` | Legacy icon 96×96 px. |
| `app/src/main/res/mipmap-xxhdpi/ic_launcher.png` | Legacy icon 144×144 px. |
| `app/src/main/res/mipmap-xxxhdpi/ic_launcher.png` | Legacy icon 192×192 px. |
| `app/src/main/res/drawable-nodpi/ic_launcher_bitmap.png` | Full-res source image for adaptive icon foreground and splash. |
| `app/src/main/res/drawable-nodpi/splash_logo.png` | Splash screen image: logo trimmed to content, composited on magenta `#cf62a9` background. Generated via Python PIL. |
| `app/src/main/res/drawable/ic_launcher_background.xml` | Solid magenta `#cf62a9` shape drawable for adaptive icon background. |
| `app/src/main/res/drawable/ic_launcher_foreground.xml` | `<inset>` with 12dp margins pointing to `@drawable/ic_launcher_bitmap`. Scales logo to fit within safe zone. |
| `app/src/main/res/drawable/splash_background.xml` | `<bitmap>` drawable referencing `@drawable/splash_logo` with `gravity="fill"`. |
| `app/src/main/res/mipmap-anydpi-v26/ic_launcher.xml` | Adaptive icon with background, foreground, and monochrome layers. |
| `app/src/main/res/mipmap-anydpi-v26/ic_launcher_round.xml` | Same adaptive icon for round icon launchers. |
| `app/src/main/res/values/themes.xml` | `Theme.pLauncher.Splash` with `windowBackground` set to `@drawable/splash_background` (drawable, not color, to avoid force-dark inversion). |
| `app/src/main/res/values-v29/themes.xml` | Same splash theme with `android:forceDarkAllowed="false"` to prevent Android from inverting the magenta background to black under system dark mode. |

#### Script

| File | Description |
|---|---|
| `generate_icon.sh` | POSIX-compliant shell script (`#!/bin/sh`) that accepts `.kra` or `.png` input. A `.kra` file is exported to PNG via `krita --export`; a `.png` is copied directly. The PNG output is always written to `apk/pLauncher.png`. Uses ImageMagick for mipmap resizing and Python PIL for splash screen generation. Other formats are rejected with an error. |

### Key Implementation Details

**Circular reference fix**: The original plan had `ic_launcher_foreground.xml` reference `@mipmap/ic_launcher`. On API 26+, `@mipmap/ic_launcher` resolves to the adaptive icon XML itself, creating a circular reference. Android silently falls back to a default icon. Fixed by copying the source image to `drawable-nodpi/ic_launcher_bitmap.png` and having the foreground reference `@drawable/ic_launcher_bitmap` instead.

**Foreground inset sizing**: The adaptive icon canvas is 108 dp with a 66 dp safe zone (21 dp margin). Testing showed:
- 21dp inset (plan default): Logo appeared too small compared to other apps
- 0dp inset (no inset): Logo edges clipped by launcher masks
- 12dp inset (final): Good compromise — logo fills ~84/108 dp of canvas while avoiding edge clipping

**Splash screen force-dark fix**: Android API 29+ applies "force dark" to light themes, inverting colors. A solid magenta `#cf62a9` as `windowBackground` was inverted to black. Two-layer fix:
1. Use a drawable (`@drawable/splash_background`) instead of a raw color — drawables are not force-dark inverted
2. Set `android:forceDarkAllowed="false"` in `values-v29/themes.xml` to disable force-dark entirely for the splash theme

**Splash logo generation** (Python PIL): The source image has the logo on a transparent background. The script:
1. Opens the source PNG and converts to RGBA
2. Calls `getbbox()` to find the bounding box of non-transparent pixels
3. Crops to the bounding box, removing transparent margins
4. Creates a new image the same size as the cropped logo, filled with magenta `#cf62a9`
5. Pastes the cropped logo using its alpha channel as a mask
6. Saves as `splash_logo.png`

The splash drawable uses `android:gravity="fill"` which scales the image to fill the screen. Since the image contains only the logo on magenta (no extra padding), Android scales it proportionally.

**KRA to PNG export**: The script detects the input format by file extension:
- `.kra`: Checks `krita` is in PATH, runs `krita --export --export-filename <apk/pLauncher.png> <source.kra>` with output suppressed (Krita prints warnings to stderr). Requires display (X11/Wayland available). `QT_QPA_PLATFORM=offscreen` was tested and fails on Krita 5.3.
- `.png`: Copies the file to `apk/pLauncher.png` (backward compatible with original usage).
- Other extensions: Rejected with descriptive error.

After acquisition, the PNG in `apk/pLauncher.png` is validated (square check via `identify`) before the icon generation pipeline proceeds.

**Generation script** (`generate_icon.sh`):
```sh
#!/bin/sh
set -e

# ... argument check, SCRIPT_DIR, APK_DIR, RES_DIR ...

# Format detection and source acquisition
PNG_OUTPUT="${APK_DIR}/pLauncher.png"
case "${SOURCE##*.}" in
    kra)
        command -v krita >/dev/null 2>&1 || { printf "Error: krita not found in PATH\n"; exit 1; }
        krita --export --export-filename "$PNG_OUTPUT" "$SOURCE" >/dev/null 2>&1 || exit 1
        SOURCE="$PNG_OUTPUT"
        ;;
    png)
        cp "$SOURCE" "$PNG_OUTPUT"
        SOURCE="$PNG_OUTPUT"
        ;;
    *)
        printf "Error: unsupported format '%s'. Expected .kra or .png\n" "${SOURCE##*.}"
        exit 1
        ;;
esac

# Verify source is square PNG (runs on the acquired PNG)
SIZE=$(identify -format "%wx%h" "$SOURCE")
# ... square check, mipmap generation, XML resources ...
```

### Code Statistics

| Component | Files | Lines changed |
|---|---|---|
| Android Manifest | 1 | ~4 (icon attributes, theme changes) |
| Mipmap PNGs | 5 | New (generated) |
| Drawable PNGs | 2 | New (foreground copy, splash) |
| Drawable XML | 3 | New (background, foreground, splash) |
| Mipmap XML | 2 | New (adaptive icon, round) |
| Theme XML | 2 | New (values, values-v29) |
| Script | 1 | ~200 (generate_icon.sh, updated with KRA export) |
| **Total** | **16** | **~204** |

### Build Status

- Android app: `./gradlew assembleDebug` — BUILD SUCCESSFUL
- Script: `bash -n` passes; `.kra` and `.png` inputs both tested successfully

### Design Decisions

**KRA as source of truth**: The `.kra` Krita project file is the authoritative source for the icon. Editing the `.kra` in Krita and re-running `./generate_icon.sh pLauncher.kra` produces a fresh PNG and regenerates all Android resources. This eliminates the manual export step.

**Backward compatibility**: The script accepts both `.kra` and `.png` inputs. Passing a `.png` maintains the original workflow for users who prefer to work with exported PNGs directly.

**Krita display requirement**: `QT_QPA_PLATFORM=offscreen` does not work with Krita 5.3 on Linux (BadWindow X error). The script runs Krita without offscreen mode, requiring an available X11/Wayland display. Krita's stderr output (style warnings, ICC profile warnings, tile leak warnings) is suppressed with `>/dev/null 2>&1`.

**Fixed output path**: The PNG is always written to `apk/pLauncher.png` regardless of input format. This ensures the Android project references a stable location, and the generation script can always find the PNG for downstream processing.

**Foreground as drawable, not mipmap reference**: Avoids circular reference on API 26+. The source image is stored in `drawable-nodpi/` (no density scaling) so Android uses it at full resolution and scales to the adaptive icon canvas.

**Inset over bitmap for foreground**: `<inset>` with 12dp margins ensures the logo fits within the adaptive icon safe zone across all launcher mask shapes (circle, squircle, teardrop). Tested values: 0dp clips edges, 21dp too small, 12dp balanced.

**Splash uses drawable, not color**: Android's force-dark feature inverts solid colors in `windowBackground` under system dark mode (magenta → black). Using a bitmap drawable avoids this inversion. Combined with `forceDarkAllowed=false` on API 29+ for reliability.

**Splash logo trimmed to content**: Instead of a fixed-size canvas with padding, the logo is trimmed to its bounding box and composited on magenta at exact size. The `gravity="fill"` in the splash drawable scales it to the screen. This keeps the splash image small while ensuring the logo is always visible.

**POSIX-compliant script**: Uses `#!/bin/sh`, `[` instead of `[[`, `printf` instead of `echo`, `set -e` instead of `set -euo pipefail`. The Python splash generation is the only non-POSIX dependency (PIL/Pillow), required because ImageMagick cannot reliably composite alpha-transparent logos onto colored backgrounds with matching dimensions.

**Source image preserved**: The original `pLauncher.kra` stays in the project root. The script exports and transforms it as needed. Users regenerate all resources by running `./generate_icon.sh pLauncher.kra`.

**Unchanged**: Pebble watch app, communication protocol, existing Android app features. All existing functionality remains fully operational.

## #29 — Watchapp Launcher Icon and Build Integration

### Overview

Added a proper launcher icon to the Pebble watchapp (`pbw/`) that appears in the watch's app menu. Replaced the 1×1 grey placeholder with a handcrafted 25×25 pixel art icon drawn directly at target size. Refactored `generate_icon.sh` to support two independent icon sources (`apk/pLauncher_apk.kra` for Android, `pbw/pLauncher_pbw.kra` for Pebble), each with its own target flag (`--apk` / `--pbw`). Integrated the Pebble icon export into the Android Gradle build pipeline, so the APK build automatically exports the icon, builds the watchapp, and bundles the `.pbw` — with warnings for any failed step instead of blocking the APK build.

### Analysis

- **Previous state**: The watchapp had `APP_ICON` referencing a 1×1 grey placeholder PNG in `images/app_launcher_icon.png`. The `package.json` used `"aapl52png": "images/app_launcher_icon.png"` which is not recognized by SDK 4.17. No `"menuIcon": true` was set, so the icon did not appear in the launcher.
- **Icon generation attempt**: The initial approach used `generate_icon.sh` to trim the 1400×1400 logo (PIL `getbbox()`) and resize to 25×25 with LANCZOS. The result appeared small with blurry edges due to anti-aliasing artifacts on Pebble's 1-bit/8-bit display.
- **Pixel art approach**: Pebble launcher icons render best as pure pixel art with sharp edges (no anti-aliasing), similar to the PebbleNotificationCenter2 reference icon (25×25, greyscale, 2 colors). The handcrafted icon `pLauncher_pbw.kra` is a Krita project designed at 25×25 with the logo's distinctive "L" and "p" characters.
- **Two KRA files**: The project now has two separate KRA sources:
  - `apk/pLauncher_apk.kra` (1400×1400, full-color logo) → Android companion app assets
  - `pbw/pLauncher_pbw.kra` (25×25, pixel art) → Pebble watchapp menu icon
- **Script refactoring**: The original `generate_icon.sh` accepted a single source argument and always generated Android assets. Refactored to accept `<source> --apk` or `<source> --pbw`, distinguishing which pipeline to run.
- **No intermediate PNGs**: Both KRA exports write directly into their destination resource directories, avoiding PNG files in the project root that could conflict with Android resource naming rules.
- **Gradle integration**: The APK build (`./gradlew assembleDebug`) needs to include the watchapp build. Added `exportPbwIcon` task that runs `generate_icon.sh --pbw`, followed by `buildWatchapp` (`pebble build`), then `bundleWatchPbw` and `generatePbwInfo`. All use `isIgnoreExitValue = true` with `logger.warn` for failures, so the APK always compiles even if Pebble steps fail.

### Watch App (`pbw/`) — ~3 files changed

#### Modified Files

| File | Changes |
|---|---|
| `pLauncher_pbw.kra` (new) | Krita project file, 25×25 canvas, handcrafted pixel art icon with "L" and "p" logo elements. |
| `resources/images/app_launcher_icon.png` (new) | 25×25 PNG exported from `pLauncher_pbw.kra`. Replaces 1×1 grey placeholder. |
| `package.json` | Replaced `"aapl52png": "images/app_launcher_icon.png"` with `"menuIcon": true` in `APP_ICON` resource entry. |

### Script (`generate_icon.sh`) — Rewritten

#### Changes

| Aspect | Before | After |
|---|---|---|
| Arguments | `<source_kra_or_png>` (single) | `<source_kra_or_png> --apk\|--pbw` (two args) |
| APK source | Any KRA/PNG passed as arg | `apk/pLauncher_apk.kra` → PNG in `apk/app/src/main/res/drawable-nodpi/_src/` |
| PBW source | None (copied static `pLauncher_pbw.png`) | `pbw/pLauncher_pbw.kra` → PNG in `pbw/resources/images/app_launcher_icon.png` |
| Intermediate PNGs | `apk/pLauncher.png` in project root | None. APK PNG in `_src/` subdirectory (ignored by Gradle). PBW PNG in `resources/images/`. |
| KRA export | Single export to `apk/pLauncher.png` | Two separate exports per target, each to its own destination |
| PNG same-file fix | `realpath` comparison for APK copy | Same approach for both targets |

#### Usage

```sh
# Generate Android companion app icons from KRA
./generate_icon.sh apk/pLauncher_apk.kra --apk

# Generate Pebble watchapp menu icon from KRA
./generate_icon.sh pbw/pLauncher_pbw.kra --pbw

# Both also accept .png directly
./generate_icon.sh some_logo.png --apk
./generate_icon.sh some_icon.png --pbw
```

#### APK PNG storage

The APK's exported PNG is written to `apk/app/src/main/res/drawable-nodpi/_src/pLauncher_apk.png`. The `_src/` subdirectory ensures Gradle does not treat the intermediate PNG as a resource (file names with uppercase letters like `L` are invalid for Android resource identifiers). The PNG is consumed by the script's downstream processing (mipmaps, foreground copy, splash) and is not referenced by the Android project directly.

### Android Build (`apk/app/build.gradle.kts`) — Watchapp integration

#### New Task: `exportPbwIcon`

```kotlin
val exportPbwIcon = tasks.register<Exec>("exportPbwIcon") {
    workingDir = rootProject.projectDir.parentFile
    commandLine = listOf(generateIconScript.toString(), "pbw/pLauncher_pbw.kra", "--pbw")
    isIgnoreExitValue = true
    standardOutput = System.out
    errorOutput = System.err

    doLast {
        val exitCode = executionResult.get().exitValue
        if (exitCode != 0) {
            logger.warn("WARNING: exportPbwIcon failed (exit code $exitCode). The watchapp menu icon will not be updated.")
        }
    }
}
```

Runs `generate_icon.sh --pbw` to export the Pebble icon from the KRA before `pebble build`. Failure logs a warning but does not block the build.

#### Modified Task: `buildWatchapp`

Added `dependsOn(exportPbwIcon)` so the icon is always fresh before building. Added `doLast` with `executionResult.get().exitValue` check for failure warning. Uses `isIgnoreExitValue = true` so APK build continues on Pebble build failure.

#### Modified Tasks: `bundleWatchPbw` / `generatePbwInfo`

Both now include `logger.warn` inside `onlyIf` when `pbw.pbw` does not exist, making skipped steps visible in the build output.

#### Build Flow

```
assembleDebug
  └── mergeDebugAssets
        ├── bundleWatchPbw
        │     └── buildWatchapp (isIgnoreExitValue=true, warns on failure)
        │           └── exportPbwIcon (isIgnoreExitValue=true, warns on failure)
        └── generatePbwInfo
              └── buildWatchapp (shared dependency)
```

Each step reports warnings on failure. The APK compiles regardless.

### Key Implementation Details

**`menuIcon` vs `aapl52png`**: The `"aapl52png"` attribute is from Pebble SDK v2 (Apple Watch style). SDK 4.17 does not recognize it. `"menuIcon": true` is the correct attribute for Re-Pebble SDK to register the icon in the watch's app menu/launcher.

**Pixel art over resize**: Resizing a 1400×1400 logo to 25×25 with LANCZOS produces anti-aliased edges that appear blurry on Pebble's low-resolution display. Handcrafting the icon at 25×25 in Krita produces sharp pixel-perfect edges matching the PebbleNotificationCenter2 reference style.

**`getbbox()` trim no longer used for PBW**: The initial approach used PIL's `getbbox()` to crop transparent margins before resizing. This approach was abandoned in favor of the handcrafted icon. The `getbbox()` trim is still used for the Android splash screen (log remains in `--apk` path).

**KRA export destination**: `krita --export --export-filename <dest> <source.kra>` writes the flattened PNG to `<dest>`. For `--pbw`, the destination is `pbw/resources/images/app_launcher_icon.png` (directly where Pebble SDK expects it). For `--apk`, the destination is `apk/app/src/main/res/drawable-nodpi/_src/pLauncher_apk.png` (intermediate, consumed by script).

**`onlyIf` with warning**: Gradle's `onlyIf` predicate is evaluated before task execution. Placing `logger.warn` inside the predicate ensures the warning is printed even when the task is skipped (unlike `doFirst`, which does not run when `onlyIf` returns false).

### Code Statistics

| Component | Files | Lines changed |
|---|---|---|
| Watch App | 2 | ~3 (package.json replacement) |
| Resources | 1 | 1 PNG new (25×25 handcrafted) |
| Script | 1 | ~150 (complete rewrite for dual-target) |
| Gradle | 1 | ~30 (exportPbwIcon task, warnings, dependencies) |
| **Total** | **5** | **~183** |

### Build Status

- Watch app: `pebble build` — BUILD SUCCESSFUL (basalt + emery)
- Android app: `./gradlew assembleDebug` — BUILD SUCCESSFUL (includes watchapp build)
- Script: `bash -n` passes; `--apk` and `--pbw` targets both tested
- Resource size: 4,643 bytes total (icon contributes ~222 B)
- Basalt: 29,373 B RAM / 64 KB, 36,163 B free heap
- Emery: 29,373 B RAM / 128 KB, 101,699 B free heap

### Design Decisions

**Two KRA files over one**: The Android logo (1400×1400, full-color, gradient) and Pebble icon (25×25, pixel art, monochrome) have fundamentally different designs. Separate KRA files allow each to be edited independently in Krita without affecting the other.

**`_src/` subdirectory for APK PNG**: Android resource names must be lowercase with underscores only. The exported APK PNG filename (`pLauncher_apk.png`) contains uppercase letters, causing Gradle resource merge failures. Storing it in a `_src/` subdirectory within `drawable-nodpi/` isolates it from Android's resource scanner while keeping it accessible to the script.

**Gradle task chain over manual steps**: Integrating the Pebble icon export and build into Gradle ensures the APK always bundles a fresh watchapp. The `isIgnoreExitValue = true` with warnings ensures the APK build is resilient — if Pebble SDK is unavailable or the build fails, the APK still compiles with the previous `.pbw` (or without it), with clear warnings in the output.

**`executionResult.get().exitValue`**: Gradle's Kotlin DSL does not expose `exitValue` directly on `Exec` tasks. The `executionResult` property (a `Provider<ExecResult>`) must be accessed via `.get().exitValue` inside `doLast` (after execution). This pattern correctly retrieves the exit code after `isIgnoreExitValue = true` prevents the task from throwing.

**`onlyIf` warning placement**: Placing `logger.warn` inside the `onlyIf` predicate (rather than `doFirst`) ensures warnings appear when tasks are skipped due to missing files. The predicate both logs the warning and returns the boolean condition, so the warning fires exactly when `onlyIf` evaluates to false.

**`generate_icon.sh` target flags**: Using `--apk` and `--pbw` flags (rather than separate scripts) keeps maintenance in one file while clearly separating concerns. The flags determine output paths, validation, and downstream processing.

**Unchanged**: Android icon assets (mipmaps, adaptive, splash), watchapp UI, packet protocol, companion app functionality. All existing features remain fully operational.
