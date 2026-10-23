#!/bin/bash
# Puts the Chromecast back the way it was: re-enables everything in debloat-list.txt,
# restores animation speed and hands Home back to the Google TV launcher.
cd "$(dirname "$0")"
for p in $(grep -v '^#' debloat-list.txt | awk '{print $1}'); do
    adb shell pm enable "$p" >/dev/null 2>&1 || adb shell cmd package install-existing "$p"
    echo "enabled $p"
done
adb shell settings put global window_animation_scale 1.0
adb shell settings put global transition_animation_scale 1.0
adb shell settings put global animator_duration_scale 1.0
adb shell settings put secure screensaver_components com.google.android.apps.tv.dreamx/.service.Backdrop
adb shell cmd package set-home-activity com.google.android.apps.tv.launcherx/.home.HomeActivity
echo "Restored. Optional: adb uninstall com.halo.tvhome"
