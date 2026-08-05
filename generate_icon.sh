#!/bin/sh
#
# Icon generation script for pLauncher.
#
# Usage:
#   ./generate_icon.sh <source_kra_or_png> --apk
#   ./generate_icon.sh <source_kra_or_png> --pbw
#
# <source_kra_or_png> is either a .kra (Krita) or .png file.
#   - .kra is exported to PNG via Krita headless.
#   - .png is used directly.
#
# --apk  : generates Android companion app assets from the source.
#          PNG output written to apk/<source_basename>.png.
#          Generates mipmaps, adaptive icon, splash, foreground drawable.
#
# --pbw  : generates Pebble watchapp menu icon from the source.
#          PNG output written to pbw/<source_basename>.png.
#          Copies the exported PNG to pbw/resources/images/app_launcher_icon.png.
#
# Expected usage with project KRA files:
#   ./generate_icon.sh apk/pLauncher_apk.kra --apk
#   ./generate_icon.sh pbw/pLauncher_pbw.kra --pbw

set -e

if [ $# -lt 2 ]; then
    printf "Usage: %s <source_kra_or_png> --apk|--pbw\n" "$0"
    exit 1
fi

SOURCE="$1"
TARGET="$2"

if [ ! -f "$SOURCE" ]; then
    printf "Error: file not found: %s\n" "$SOURCE"
    exit 1
fi

case "$TARGET" in
    --apk|--pbw) ;;
    *)
        printf "Error: target must be --apk or --pbw, got: %s\n" "$TARGET"
        exit 1
        ;;
esac

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
APK_DIR="${SCRIPT_DIR}/apk"
RES_DIR="${APK_DIR}/app/src/main/res"
PBW_DIR="${SCRIPT_DIR}/pbw"

# --- Export KRA or copy PNG to the target directory ---
SOURCE_BASE="${SOURCE##*/}"
SOURCE_NAME="${SOURCE_BASE%.*}"
SOURCE_EXT="${SOURCE_BASE##*.}"

case "$TARGET" in
    --apk)
        mkdir -p "${RES_DIR}/drawable-nodpi/_src"
        PNG_OUTPUT="${RES_DIR}/drawable-nodpi/_src/${SOURCE_NAME}.png"
        ;;
    --pbw)
        mkdir -p "${PBW_DIR}/resources/images"
        PNG_OUTPUT="${PBW_DIR}/resources/images/app_launcher_icon.png"
        ;;
esac

case "$SOURCE_EXT" in
    kra)
        if ! command -v krita >/dev/null 2>&1; then
            printf "Error: krita not found in PATH\n"
            exit 1
        fi
        printf "Exporting KRA to PNG...\n"
        if ! krita --export --export-filename "$PNG_OUTPUT" "$SOURCE" >/dev/null 2>&1; then
            printf "Error: krita export failed for %s\n" "$SOURCE"
            exit 1
        fi
        SOURCE="$PNG_OUTPUT"
        ;;
    png)
        if [ "$(realpath "$SOURCE")" != "$(realpath "$PNG_OUTPUT")" ]; then
            cp "$SOURCE" "$PNG_OUTPUT"
        fi
        SOURCE="$PNG_OUTPUT"
        ;;
    *)
        printf "Error: unsupported format '%s'. Expected .kra or .png\n" "$SOURCE_EXT"
        exit 1
        ;;
esac

# --- Verify source ---
SIZE=$(identify -format "%wx%h" "$SOURCE")
W=$(identify -format "%w" "$SOURCE")
H=$(identify -format "%h" "$SOURCE")
if [ "$W" != "$H" ]; then
    printf "Error: expected square PNG, got %s: %s\n" "$SIZE" "$SOURCE"
    exit 1
fi

printf "Source: %s (%s) -> %s\n" "$SOURCE" "$SIZE" "$TARGET"

# --- Handle --apk ---
if [ "$TARGET" = "--apk" ]; then
    # --- Create directories ---
    mkdir -p "${RES_DIR}/drawable"
    mkdir -p "${RES_DIR}/drawable-nodpi"
    mkdir -p "${RES_DIR}/mipmap-mdpi"
    mkdir -p "${RES_DIR}/mipmap-hdpi"
    mkdir -p "${RES_DIR}/mipmap-xhdpi"
    mkdir -p "${RES_DIR}/mipmap-xxhdpi"
    mkdir -p "${RES_DIR}/mipmap-xxxhdpi"
    mkdir -p "${RES_DIR}/mipmap-anydpi-v26"

    # --- Legacy mipmap icons ---
    printf "Generating legacy mipmap icons...\n"
    convert "$SOURCE" -resize 48x48   "${RES_DIR}/mipmap-mdpi/ic_launcher.png"
    convert "$SOURCE" -resize 72x72   "${RES_DIR}/mipmap-hdpi/ic_launcher.png"
    convert "$SOURCE" -resize 96x96   "${RES_DIR}/mipmap-xhdpi/ic_launcher.png"
    convert "$SOURCE" -resize 144x144 "${RES_DIR}/mipmap-xxhdpi/ic_launcher.png"
    convert "$SOURCE" -resize 192x192 "${RES_DIR}/mipmap-xxxhdpi/ic_launcher.png"

    # --- Foreground drawable (full-res, nodpi) ---
    printf "Copying foreground drawable...\n"
    cp "$SOURCE" "${RES_DIR}/drawable-nodpi/ic_launcher_bitmap.png"

    # --- Background drawable XML ---
    printf "Writing background drawable...\n"
    cat > "${RES_DIR}/drawable/ic_launcher_background.xml" << 'EOF'
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="#cf62a9" />
</shape>
EOF

    # --- Foreground inset XML ---
    printf "Writing foreground drawable...\n"
    cat > "${RES_DIR}/drawable/ic_launcher_foreground.xml" << 'EOF'
<?xml version="1.0" encoding="utf-8"?>
<inset xmlns:android="http://schemas.android.com/apk/res/android"
    android:insetTop="12dp"
    android:insetBottom="12dp"
    android:insetLeft="12dp"
    android:insetRight="12dp"
    android:drawable="@drawable/ic_launcher_bitmap" />
EOF

    # --- Adaptive icon XML ---
    printf "Writing adaptive icon XML...\n"
    cat > "${RES_DIR}/mipmap-anydpi-v26/ic_launcher.xml" << 'EOF'
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
    <monochrome android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
EOF

    cat > "${RES_DIR}/mipmap-anydpi-v26/ic_launcher_round.xml" << 'EOF'
<?xml version="1.0" encoding="utf-8"?>
<adaptive-icon xmlns:android="http://schemas.android.com/apk/res/android">
    <background android:drawable="@drawable/ic_launcher_background" />
    <foreground android:drawable="@drawable/ic_launcher_foreground" />
    <monochrome android:drawable="@drawable/ic_launcher_foreground" />
</adaptive-icon>
EOF

    # --- Splash screen image (logo on magenta background) ---
    printf "Generating splash screen...\n"
    python3 -c "
from PIL import Image

img = Image.open('${SOURCE}').convert('RGBA')
coords = img.getbbox()
trimmed = img.crop(coords) if coords else img
lw, lh = trimmed.size
out = Image.new('RGBA', (lw, lh), (207, 98, 169, 255))
alpha = trimmed.split()[3]
out.paste(trimmed, (0, 0), alpha)
out.save('${RES_DIR}/drawable-nodpi/splash_logo.png', 'PNG')
print('Splash: %dx%d (logo only, magenta bg)' % (out.size[0], out.size[1]))
"

    # --- Splash XML ---
    printf "Writing splash XML...\n"
    cat > "${RES_DIR}/drawable/splash_background.xml" << 'EOF'
<?xml version="1.0" encoding="utf-8"?>
<bitmap xmlns:android="http://schemas.android.com/apk/res/android"
    android:src="@drawable/splash_logo"
    android:gravity="fill"
    android:antialias="true" />
EOF

    # --- Splash theme ---
    printf "Writing splash themes...\n"
    cat > "${RES_DIR}/values/themes.xml" << 'EOF'
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.pLauncher.Splash" parent="android:style/Theme.Material.Light.NoActionBar">
        <item name="android:windowBackground">@drawable/splash_background</item>
    </style>
</resources>
EOF

    mkdir -p "${RES_DIR}/values-v29"
    cat > "${RES_DIR}/values-v29/themes.xml" << 'EOF'
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <style name="Theme.pLauncher.Splash" parent="android:style/Theme.Material.Light.NoActionBar">
        <item name="android:windowBackground">@drawable/splash_background</item>
        <item name="android:forceDarkAllowed">false</item>
    </style>
</resources>
EOF

    # --- Verify outputs ---
    printf "\nGenerated files:\n"
    identify "${RES_DIR}/mipmap-mdpi/ic_launcher.png"
    identify "${RES_DIR}/mipmap-hdpi/ic_launcher.png"
    identify "${RES_DIR}/mipmap-xhdpi/ic_launcher.png"
    identify "${RES_DIR}/mipmap-xxhdpi/ic_launcher.png"
    identify "${RES_DIR}/mipmap-xxxhdpi/ic_launcher.png"
    identify "${RES_DIR}/drawable-nodpi/ic_launcher_bitmap.png"
    identify "${RES_DIR}/drawable-nodpi/splash_logo.png"
    printf "\nXML files:\n"
    find "${RES_DIR}/drawable" -name "*.xml" -print
    find "${RES_DIR}/mipmap-anydpi-v26" -name "*.xml" -print
    find "${RES_DIR}/values" -maxdepth 1 -name "*.xml" -print
    find "${RES_DIR}/values-v29" -name "*.xml" -print
    printf "\nDone. Run './gradlew assembleDebug' from %s/ to build.\n" "$APK_DIR"
fi

# --- Handle --pbw ---
if [ "$TARGET" = "--pbw" ]; then
    # --- Pebble watchapp menu icon (already exported to resources/images/) ---
    printf "Pebble menu icon: app_launcher_icon.png\n"
    ICON_SIZE=$(identify -format "%wx%h" "${PBW_DIR}/resources/images/app_launcher_icon.png")
    printf "Pebble icon: %s\n" "$ICON_SIZE"

    # --- Verify output ---
    printf "\nGenerated files:\n"
    identify "${PBW_DIR}/resources/images/app_launcher_icon.png"
    printf "\nDone.\n"
fi
