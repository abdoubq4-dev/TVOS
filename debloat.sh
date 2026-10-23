#!/bin/bash
# Makes Halo Home the home screen, disables the packages in debloat-list.txt, speeds up transitions.
cd "$(dirname "$0")"
adb shell cmd package set-home-activity com.halo.tvhome/.HomeActivity
for p in $(grep -v '^#' debloat-list.txt | awk '{print $1}'); do
    printf '%s: ' "$p"; adb shell pm disable-user --user 0 "$p"
done
adb shell settings put global window_animation_scale 0.5
adb shell settings put global transition_animation_scale 0.5
