#!/bin/bash
# Usage: ./set-wallpaper.sh <image> [more images...]
# Converts each image to a 1920px JPEG, copies it to the TV and makes the first one the background.
set -e
DIR=/sdcard/Android/data/com.halo.tvhome/files/wallpapers
TMP=$(mktemp -d)
adb shell mkdir -p "$DIR"
first=""
for src in "$@"; do
    name="$(basename "${src%.*}").jpg"
    sips -s format jpeg -s formatOptions 85 --resampleWidth 1920 "$src" --out "$TMP/$name" >/dev/null
    h=$(sips -g pixelHeight "$TMP/$name" | awk '/pixelHeight/{print $2}')
    [ "$h" -gt 1080 ] && sips -c 1080 1920 "$TMP/$name" >/dev/null  # centre-crop square art to 16:9
    adb push "$TMP/$name" "$DIR/$name" >/dev/null && echo "copied $name"
    [ -z "$first" ] && first="$name"
done
rm -rf "$TMP"
# Quote for the TV's shell too, or the space in "Photo: x" splits the argument.
adb shell "am start -n com.halo.tvhome/.HomeActivity --es wallpaper 'Photo: $first'" >/dev/null
echo "background set to $first"
