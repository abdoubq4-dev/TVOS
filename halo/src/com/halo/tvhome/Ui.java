package com.halo.tvhome;

import android.content.Context;
import android.graphics.Typeface;
import android.util.SparseArray;

import java.io.File;

final class Ui {
    static float density = 2f;
    private static File fontFile;
    /** Google Sans runs heavy next to SF Pro, Inter only slightly; nominal weights are scaled by this. */
    private static float weightScale = 0.78f;
    private static final SparseArray<Typeface> fonts = new SparseArray<>();

    static void init(Context c) {
        density = c.getResources().getDisplayMetrics().density;
        // A font pushed by install-font.sh (e.g. Inter, the closest open font to SF Pro) wins.
        File custom = c.getExternalFilesDir("fonts");
        File[] fs = custom != null ? custom.listFiles((d, n) -> n.toLowerCase().endsWith(".ttf")) : null;
        if (fs != null && fs.length > 0) {
            fontFile = fs[0];
            weightScale = 0.85f;
            return;
        }
        for (String p : new String[] {
                "/system/fonts/GoogleSansFlex-Regular.ttf",
                "/product/fonts/GoogleSansFlex-Regular.ttf",
                "/system/fonts/GoogleSans-Regular.ttf",
                "/product/fonts/GoogleSans-Regular.ttf"}) {
            File f = new File(p);
            if (f.canRead()) { fontFile = f; break; }
        }
    }

    static int dp(float v) {
        return Math.round(v * density);
    }

    /**
     * Inter if it was installed (the closest open font to SF Pro), else Google Sans Flex.
     * Nominal weights are scaled to match SF Pro's apparent weight in each face.
     */
    static Typeface font(int nominal) {
        int weight = Math.max(300, Math.round(nominal * weightScale));
        Typeface t = fonts.get(weight);
        if (t != null) return t;
        if (fontFile != null) {
            try {
                t = new Typeface.Builder(fontFile)
                        .setFontVariationSettings("'wght' " + weight)
                        .setWeight(weight)
                        .build();
            } catch (Exception ignored) {
                t = null;
            }
        }
        if (t == null) t = Typeface.create(Typeface.DEFAULT, weight, false);
        fonts.put(weight, t);
        return t;
    }

    private Ui() {}
}
