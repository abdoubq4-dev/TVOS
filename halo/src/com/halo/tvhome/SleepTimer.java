package com.halo.tvhome;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;

/** Control Center sleep timer: pauses whatever is playing, then puts the TV to sleep. */
public class SleepTimer extends BroadcastReceiver {
    private static final int[] STEPS = {0, 15, 30, 60, 90};

    /** Minutes left, or 0 when off. */
    static int remaining(Context c) {
        long end = c.getSharedPreferences("halo", 0).getLong("sleep_at", 0);
        long left = end - System.currentTimeMillis();
        return left > 0 ? (int) Math.ceil(left / 60000.0) : 0;
    }

    /** Off → 15 → 30 → 60 → 90 → off. Returns the new setting in minutes. */
    static int cycle(Context c) {
        int current = remaining(c) == 0 ? 0 : c.getSharedPreferences("halo", 0).getInt("sleep_step", 0);
        int idx = 0;
        for (int i = 0; i < STEPS.length; i++) if (STEPS[i] == current) idx = i;
        int next = STEPS[(idx + 1) % STEPS.length];
        AlarmManager am = c.getSystemService(AlarmManager.class);
        PendingIntent pi = PendingIntent.getBroadcast(c, 0, new Intent(c, SleepTimer.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        am.cancel(pi);
        long end = next == 0 ? 0 : System.currentTimeMillis() + next * 60000L;
        if (end > 0) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, end, pi);
        c.getSharedPreferences("halo", 0).edit().putLong("sleep_at", end).putInt("sleep_step", next).apply();
        return next;
    }

    static String label(Context c) {
        int m = remaining(c);
        return m == 0 ? "Off" : m >= 60 ? (m / 60) + " hr " + (m % 60 == 0 ? "" : (m % 60) + " min") : m + " min";
    }

    @Override
    public void onReceive(Context c, Intent i) {
        c.getSharedPreferences("halo", 0).edit().putLong("sleep_at", 0).apply();
        try {
            MediaSessionManager msm = c.getSystemService(MediaSessionManager.class);
            for (MediaController mc : msm.getActiveSessions(new ComponentName(c, MediaListener.class))) {
                PlaybackState s = mc.getPlaybackState();
                if (s != null && s.getState() == PlaybackState.STATE_PLAYING) mc.getTransportControls().pause();
            }
        } catch (SecurityException ignored) {
        }
        Actions.sleep(c);
    }
}
