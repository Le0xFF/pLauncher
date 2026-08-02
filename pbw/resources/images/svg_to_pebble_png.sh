#!/bin/bash
# Converts a Pebble iconography SVG to a grayscale PNG suitable for the Pebble SDK.
#
# Usage: ./svg_to_pebble_png.sh <input.svg> <output.png> [size]
#   <input.svg>  - path to the SVG file
#   <output.png> - path for the output PNG
#   [size]       - target dimension (default: 21)
#
# Example:
#   ./svg_to_pebble_png.sh caret_up.svg icon_caret_up.png 21
#   ./svg_to_pebble_png.sh rocket.svg icon_rocket.png 25

set -euo pipefail

if [[ $# -lt 2 ]]; then
    echo "Usage: $0 <input.svg> <output.png> [SizexSize]"
    echo ""
    echo "Converts a Pebble iconography SVG to a grayscale PNG for the Pebble SDK."
    echo "The SVG should use stroke='black' and/or fill='black' on a transparent background."
    echo ""
    echo "Example: $0 caret_up.svg icon_caret_up.png 21x21"
    exit 1
fi

INPUT="$1"
OUTPUT="$2"
SIZE="${3:-21x21}"

if [[ ! -f "$INPUT" ]]; then
    echo "Error: input file '$INPUT' not found."
    exit 1
fi

convert "$INPUT" \
    -resize "${SIZE}!" \
    -background white \
    -alpha off \
    PNG:"$OUTPUT" 2>/dev/null

echo "OK: $OUTPUT (${SIZE})"
