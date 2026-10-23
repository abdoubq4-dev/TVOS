package com.halo.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/** Builds tile artwork, blurred backdrops and the procedural wallpapers. */
final class Art {
    static final String[] WALLPAPERS = {"Midnight", "Aurora", "Ocean", "Ember", "Graphite"};

    /**
     * Android banners are 16:9 but tvOS icons are 5:3, so the banner is fitted to the width and
     * the bars above and below are filled with the banner's own edge colours.
     */
    static Bitmap fromBanner(Drawable banner, int w, int h) {
        int bh = Math.round(w * 9f / 16f);
        Bitmap probe = Bitmap.createBitmap(32, 18, Bitmap.Config.ARGB_8888);
        Canvas pc = new Canvas(probe);
        pc.drawColor(0xFF2C2C2E);
        banner.setBounds(0, 0, 32, 18);
        banner.draw(pc);
        int top = rowAverage(probe, 0), bottom = rowAverage(probe, 17);
        probe.recycle();

        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint();
        p.setColor(top);
        c.drawRect(0, 0, w, h / 2f, p);
        p.setColor(bottom);
        c.drawRect(0, h / 2f, w, h, p);
        int y = (h - bh) / 2;
        banner.setBounds(0, y, w, y + bh);
        banner.draw(c);
        return b;
    }

    private static int rowAverage(Bitmap b, int y) {
        long r = 0, g = 0, bl = 0;
        for (int x = 0; x < b.getWidth(); x++) {
            int px = b.getPixel(x, y);
            r += Color.red(px); g += Color.green(px); bl += Color.blue(px);
        }
        int n = b.getWidth();
        return Color.rgb((int) (r / n), (int) (g / n), (int) (bl / n));
    }

    /** tvOS-style full-bleed tile for apps that only ship a square icon. */
    static Bitmap fromIcon(Drawable icon, int w, int h) {
        int base = dominantColor(icon);
        float[] hsv = new float[3];
        Color.colorToHSV(base, hsv);
        hsv[2] = Math.min(hsv[2], 0.62f);
        int top = Color.HSVToColor(new float[] {hsv[0], hsv[1], Math.min(1f, hsv[2] + 0.18f)});
        int bottom = Color.HSVToColor(new float[] {hsv[0], Math.min(1f, hsv[1] + 0.1f), hsv[2] * 0.6f});

        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint();
        p.setShader(new LinearGradient(0, 0, 0, h, top, bottom, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        int s = Math.round(h * 0.62f);
        icon.setBounds((w - s) / 2, (h - s) / 2, (w + s) / 2, (h + s) / 2);
        icon.draw(c);
        return b;
    }

    static Bitmap settingsArt(Context ctx, int w, int h) {
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint();
        p.setShader(new LinearGradient(0, 0, 0, h, 0xFFA1A1A6, 0xFF5A5A5F, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        Drawable gear = ctx.getDrawable(R.drawable.ic_gear);
        int s = Math.round(h * 0.6f);
        gear.setTint(0xFF3A3A3C);
        gear.setBounds((w - s) / 2, (h - s) / 2 + 2, (w + s) / 2, (h + s) / 2 + 2);
        gear.draw(c);
        gear.setTint(0xFFF2F2F7);
        gear.setBounds((w - s) / 2, (h - s) / 2, (w + s) / 2, (h + s) / 2);
        gear.draw(c);
        return b;
    }

    /** Frost for the dock glass: blurred more than the backdrop, less than the scrolled grid. */
    static Bitmap dockBlur(Bitmap src) {
        Bitmap s = Bitmap.createScaledBitmap(src, 160, 90, true);
        if (s == src || !s.isMutable()) s = s.copy(Bitmap.Config.ARGB_8888, true);
        blurInPlace(s, 3, 3);
        return s;
    }

    /** Darkens a mutable bitmap in place with a flat overlay. */
    static Bitmap dim(Bitmap b, int overlay) {
        new Canvas(b).drawColor(overlay);
        return b;
    }

    /** Bakes the legibility gradient into the wallpaper instead of drawing it every frame. */
    static Bitmap scrim(Bitmap b) {
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.DITHER_FLAG);
        p.setShader(new LinearGradient(0, 0, 0, b.getHeight(),
                new int[] {0x33000000, 0x00000000, 0x1A000000, 0x8C000000}, null, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, b.getWidth(), b.getHeight(), p);
        return b;
    }

    /** Center-crops and scales to exactly the screen, as opaque RGB_565, recycling the source. */
    static Bitmap toScreen(Bitmap src, android.util.DisplayMetrics dm) {
        int w = dm.widthPixels, h = dm.heightPixels;
        float s = Math.max(w / (float) src.getWidth(), h / (float) src.getHeight());
        Bitmap out = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565);
        Canvas c = new Canvas(out);
        android.graphics.Matrix m = new android.graphics.Matrix();
        m.setScale(s, s);
        m.postTranslate((w - src.getWidth() * s) / 2f, (h - src.getHeight() * s) / 2f);
        c.drawBitmap(src, m, new Paint(Paint.FILTER_BITMAP_FLAG));
        src.recycle();
        return out;
    }

    /** Bakes rounded corners into the bitmap so tiles never need a live clip. */
    static Bitmap round(Bitmap src, float radius) {
        Bitmap out = Bitmap.createBitmap(src.getWidth(), src.getHeight(), Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(out);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
        p.setShader(new android.graphics.BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP));
        c.drawRoundRect(0, 0, src.getWidth(), src.getHeight(), radius, radius, p);
        src.recycle();
        return out;
    }

    static int dominantColor(Drawable d) {
        Bitmap b = Bitmap.createBitmap(12, 12, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        d.setBounds(0, 0, 12, 12);
        d.draw(c);
        float r = 0, g = 0, bl = 0, wsum = 0;
        float[] hsv = new float[3];
        for (int y = 0; y < 12; y++) {
            for (int x = 0; x < 12; x++) {
                int px = b.getPixel(x, y);
                if (Color.alpha(px) < 128) continue;
                Color.colorToHSV(px, hsv);
                float wt = 0.15f + hsv[1] * hsv[2];
                r += Color.red(px) * wt;
                g += Color.green(px) * wt;
                bl += Color.blue(px) * wt;
                wsum += wt;
            }
        }
        b.recycle();
        if (wsum == 0) return 0xFF3A3A3C;
        return Color.rgb((int) (r / wsum), (int) (g / wsum), (int) (bl / wsum));
    }

    /** Cheap, heavy blur: shrink to 48x27, then three box-blur passes. */
    static Bitmap blur(Bitmap src) {
        int w = 48, h = 27;
        Bitmap s = Bitmap.createScaledBitmap(src, w, h, true);
        if (!s.isMutable() || s == src) s = s.copy(Bitmap.Config.ARGB_8888, true);
        int[] px = new int[w * h];
        int[] tmp = new int[w * h];
        s.getPixels(px, 0, w, 0, 0, w, h);
        for (int pass = 0; pass < 3; pass++) {
            box(px, tmp, w, h, 3, true);
            box(tmp, px, w, h, 3, false);
        }
        s.setPixels(px, 0, w, 0, 0, w, h);
        return s;
    }

    /** Soft, out-of-focus backdrop: still recognisable, like a glass pane in front of the photo. */
    static Bitmap glassBlur(Bitmap src) {
        Bitmap s = Bitmap.createScaledBitmap(src, 480, 270, true);
        if (s == src || !s.isMutable()) s = s.copy(Bitmap.Config.ARGB_8888, true);
        blurInPlace(s, 2, 2);
        return s;
    }

    static void blurInPlace(Bitmap b, int radius, int passes) {
        int w = b.getWidth(), h = b.getHeight();
        int[] px = new int[w * h];
        int[] tmp = new int[w * h];
        b.getPixels(px, 0, w, 0, 0, w, h);
        for (int pass = 0; pass < passes; pass++) {
            box(px, tmp, w, h, radius, true);
            box(tmp, px, w, h, radius, false);
        }
        b.setPixels(px, 0, w, 0, 0, w, h);
    }

    private static void box(int[] in, int[] out, int w, int h, int r, boolean horizontal) {
        int len = horizontal ? w : h, lines = horizontal ? h : w;
        int div = r * 2 + 1;
        for (int l = 0; l < lines; l++) {
            int sr = 0, sg = 0, sb = 0;
            for (int i = -r; i <= r; i++) {
                int p = in[idx(l, clamp(i, len), w, horizontal)];
                sr += (p >> 16) & 0xFF; sg += (p >> 8) & 0xFF; sb += p & 0xFF;
            }
            for (int i = 0; i < len; i++) {
                out[idx(l, i, w, horizontal)] = 0xFF000000 | ((sr / div) << 16) | ((sg / div) << 8) | (sb / div);
                int add = in[idx(l, clamp(i + r + 1, len), w, horizontal)];
                int sub = in[idx(l, clamp(i - r, len), w, horizontal)];
                sr += ((add >> 16) & 0xFF) - ((sub >> 16) & 0xFF);
                sg += ((add >> 8) & 0xFF) - ((sub >> 8) & 0xFF);
                sb += (add & 0xFF) - (sub & 0xFF);
            }
        }
    }

    private static int clamp(int i, int len) {
        return i < 0 ? 0 : (i >= len ? len - 1 : i);
    }

    private static int idx(int line, int i, int w, boolean horizontal) {
        return horizontal ? line * w + i : i * w + line;
    }

    static Bitmap wallpaper(String name, int w, int h) {
        int bg0, bg1;
        int[][] blobs; // x%, y%, radius%, color
        switch (name) {
            case "Aurora":
                bg0 = 0xFF04121A; bg1 = 0xFF020407;
                blobs = new int[][] {{20, 15, 70, 0x9900B894}, {75, 25, 60, 0x886C5CE7}, {50, 90, 60, 0x55009DB0}};
                break;
            case "Ocean":
                bg0 = 0xFF031B33; bg1 = 0xFF01060E;
                blobs = new int[][] {{25, 20, 70, 0x990A84FF}, {85, 70, 60, 0x6600C7BE}, {50, 110, 70, 0x55203AFF}};
                break;
            case "Ember":
                bg0 = 0xFF1E0905; bg1 = 0xFF070202;
                blobs = new int[][] {{25, 20, 70, 0x99FF6B2C}, {80, 35, 55, 0x77FF2D55}, {55, 100, 70, 0x55FF9F0A}};
                break;
            case "Graphite":
                bg0 = 0xFF26262A; bg1 = 0xFF0B0B0C;
                blobs = new int[][] {{30, 10, 70, 0x33FFFFFF}, {85, 80, 60, 0x228E8E93}};
                break;
            default: // Midnight
                bg0 = 0xFF0B1030; bg1 = 0xFF020208;
                blobs = new int[][] {{20, 10, 70, 0x885E5CE6}, {85, 35, 60, 0x66BF5AF2}, {45, 105, 70, 0x55147EFB}};
        }
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
        p.setShader(new LinearGradient(0, 0, 0, h, bg0, bg1, Shader.TileMode.CLAMP));
        c.drawRect(0, 0, w, h, p);
        for (int[] bl : blobs) {
            float cx = w * bl[0] / 100f, cy = h * bl[1] / 100f, rad = w * bl[2] / 100f;
            p.setShader(new RadialGradient(cx, cy, rad, bl[3], bl[3] & 0x00FFFFFF, Shader.TileMode.CLAMP));
            c.drawCircle(cx, cy, rad, p);
        }
        return b;
    }

    private Art() {}
}
