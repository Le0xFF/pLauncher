#ifndef PLAUNCHER_LAYOUT_H
#define PLAUNCHER_LAYOUT_H

// Font heights
#define LAYOUT_NAME_FONT_HEIGHT    24   // GOTIC_24
#define LAYOUT_INDEX_FONT_HEIGHT   16   // GOTIC_14
// Name app vertical centering offset (h / 2 - LAYOUT_NAME_V_OFFSET)
#define LAYOUT_NAME_V_OFFSET       12

// Index layer distance from bottom edge
#define LAYOUT_INDEX_BOTTOM_MARGIN 20

// Spacing between name and index layers
#define LAYOUT_NAME_INDEX_SPACING  2

// Navigation column width as fraction of screen width (w / LAYOUT_NAV_COL_DIVISOR)
#define LAYOUT_NAV_COL_DIVISOR     5

// Number of vertical zones for nav icons (h / LAYOUT_NAV_ZONES)
#define LAYOUT_NAV_ZONES           3

// Navigation icon dimensions
#define LAYOUT_ICON_NAV_SIZE       21

// Icon dimensions
#define LAYOUT_ICON_SIZE           32
#define LAYOUT_ICON_V_PADDING      8

// Extra horizontal margin on each side of the centered text area
#define LAYOUT_TEXT_H_MARGIN       4

// Descender padding per line (extra pixels below font height for g, y, p, q)
#define LAYOUT_NAME_DESCENDER      4

// Maximum height for app name text (allows multi-line word wrap + descenders)
#define LAYOUT_NAME_MAX_LINES      3
#define LAYOUT_NAME_MAX_HEIGHT     (LAYOUT_NAME_FONT_HEIGHT * LAYOUT_NAME_MAX_LINES + LAYOUT_NAME_DESCENDER)

#endif
