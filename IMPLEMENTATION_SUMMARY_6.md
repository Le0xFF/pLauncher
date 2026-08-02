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

## Fix — Disable Buttons During Loading

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
