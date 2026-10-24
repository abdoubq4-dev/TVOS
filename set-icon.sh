#!/bin/bash
# Usage: ./set-icon.sh <package> <image>   e.g. ./set-icon.sh com.android.vending appstore.png
# Pushes a 5:3 tile image that Halo shows instead of the app's own banner, then restarts Halo.
set -e
DIR=/sdcard/Android/data/com.halo.tvhome/files/icons
adb shell mkdir -p "$DIR"
adb push "$2" "$DIR/$1.png" >/dev/null && adb shell chmod 644 "$DIR/$1.png" && echo "icon set for $1"
adb shell am force-stop com.halo.tvhome
adb shell input keyevent HOME
