package com.halo.tvhome;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.media.AudioManager;
import android.provider.Settings;
import android.widget.Toast;

/** System shortcuts used by the sidebar and Control Center. */
final class Actions {
    static boolean start(Context c, Intent... candidates) {
        for (Intent i : candidates) {
            try {
                c.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
                return true;
            } catch (Exception ignored) {
            }
        }
        Toast.makeText(c, "Not available on this device", Toast.LENGTH_SHORT).show();
        return false;
    }

    static void settings(Context c) { start(c, new Intent(Settings.ACTION_SETTINGS)); }
    static void wifi(Context c) { start(c, new Intent(Settings.ACTION_WIFI_SETTINGS), new Intent(Settings.ACTION_SETTINGS)); }
    static void bluetooth(Context c) { start(c, new Intent(Settings.ACTION_BLUETOOTH_SETTINGS), new Intent(Settings.ACTION_SETTINGS)); }
    static void display(Context c) { start(c, new Intent(Settings.ACTION_DISPLAY_SETTINGS), new Intent(Settings.ACTION_SETTINGS)); }
    static void sound(Context c) { start(c, new Intent(Settings.ACTION_SOUND_SETTINGS), new Intent(Settings.ACTION_SETTINGS)); }
    static void screensaverSettings(Context c) { start(c, new Intent(Settings.ACTION_DREAM_SETTINGS), new Intent(Settings.ACTION_SETTINGS)); }
    static void accessibility(Context c) { start(c, new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); }
    static void apps(Context c) { start(c, new Intent(Settings.ACTION_APPLICATION_SETTINGS), new Intent(Settings.ACTION_SETTINGS)); }

    /** The TV's own "go to sleep" action (as used by voice commands); screensaver if missing. */
    static void sleep(Context c) {
        start(c, new Intent("com.google.android.pano.action.SLEEP"), new Intent(Settings.ACTION_DREAM_SETTINGS));
    }

    static void screensaverNow(Context c) {
        start(c, new Intent(Settings.ACTION_DREAM_SETTINGS));
    }

    static void search(Context c) {
        start(c, new Intent(Intent.ACTION_ASSIST), new Intent("android.search.action.GLOBAL_SEARCH"),
                new Intent(Intent.ACTION_WEB_SEARCH));
    }

    static void volume(Context c, int direction) {
        AudioManager am = c.getSystemService(AudioManager.class);
        am.adjustSuggestedStreamVolume(direction, AudioManager.STREAM_MUSIC, AudioManager.FLAG_SHOW_UI);
    }

    private Actions() {}
}
