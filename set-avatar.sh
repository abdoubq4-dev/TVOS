#!/bin/bash
# Copies a profile picture to the TV for Halo's avatar.
# Usage: ./set-avatar.sh [image]   (defaults to the Google account picture Chrome keeps on this Mac)
set -e
SRC=${1:-"$HOME/Library/Application Support/Google/Chrome/Default/Google Profile Picture.png"}
TMP=$(mktemp -d)
sips -s format png -Z 256 "$SRC" --out "$TMP/avatar.png" >/dev/null
DIR=/sdcard/Android/data/com.halo.tvhome/files/profile
adb shell mkdir -p "$DIR"
adb push "$TMP/avatar.png" "$DIR/avatar.png" >/dev/null && echo "avatar set"
rm -rf "$TMP"
