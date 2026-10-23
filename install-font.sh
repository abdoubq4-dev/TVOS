#!/bin/bash
# Copies a .ttf to the TV for Halo to use as its UI font, then restarts Halo.
# Usage: ./install-font.sh [font.ttf]   (defaults to halo/fonts/InterVariable.ttf)
set -e
cd "$(dirname "$0")"
FONT=${1:-halo/fonts/InterVariable.ttf}
DIR=/sdcard/Android/data/com.halo.tvhome/files/fonts
adb shell "rm -rf $DIR && mkdir -p $DIR"
adb push "$FONT" "$DIR/" >/dev/null && echo "installed $(basename "$FONT")"
adb shell am force-stop com.halo.tvhome
adb shell input keyevent HOME
