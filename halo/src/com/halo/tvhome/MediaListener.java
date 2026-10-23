package com.halo.tvhome;

import android.app.NotificationManager;
import android.content.Context;
import android.service.notification.NotificationListenerService;

/**
 * Lets Halo read the active media sessions for Now Playing. While connected it can also switch
 * Do Not Disturb, which notification listeners are allowed to do.
 */
public class MediaListener extends NotificationListenerService {
    private static MediaListener connected;

    @Override
    public void onListenerConnected() {
        connected = this;
    }

    @Override
    public void onListenerDisconnected() {
        connected = null;
    }

    static boolean dndOn(Context c) {
        return c.getSystemService(NotificationManager.class).getCurrentInterruptionFilter()
                > NotificationManager.INTERRUPTION_FILTER_ALL;
    }

    /** @return false when the listener is not connected, so DND could not be changed. */
    static boolean setDnd(Context c, boolean on) {
        if (connected == null) return false;
        connected.requestInterruptionFilter(on ? INTERRUPTION_FILTER_PRIORITY : INTERRUPTION_FILTER_ALL);
        return true;
    }
}
