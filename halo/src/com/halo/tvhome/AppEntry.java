package com.halo.tvhome;

import android.content.Intent;
import android.graphics.Bitmap;

final class AppEntry {
    final String key;
    final String pkg;
    final String label;
    final Intent intent;
    Bitmap art;
    Bitmap blur;

    AppEntry(String key, String pkg, String label, Intent intent) {
        this.key = key;
        this.pkg = pkg;
        this.label = label;
        this.intent = intent;
    }
}
