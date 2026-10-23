package com.halo.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

/**
 * Painted landscape wallpapers. They are drawn small and upscaled, which gives the soft,
 * out-of-focus photo look of tvOS backgrounds without shipping any images.
 * Photos copied into Android/data/com.halo.tvhome/files/wallpapers/ are offered too.
 */
final class Scenery {
    static final String[] PAINTED = {"Coast", "Alpine", "Dunes"};
    private static final int W = 384, H = 216;

    static List<String> all(Context c) {
        List<String> out = new ArrayList<>(Arrays.asList(PAINTED));
        out.addAll(Arrays.asList(Art.WALLPAPERS));
        for (File f : photos(c)) out.add("Photo: " + f.getName());
        return out;
    }

    static File[] photos(Context c) {
        File dir = c.getExternalFilesDir("wallpapers");
        if (dir == null) return new File[0];
        File[] fs = dir.listFiles((d, n) -> n.toLowerCase().matches(".*\\.(jpe?g|png|webp)$"));
        if (fs == null) return new File[0];
        Arrays.sort(fs);
        return fs;
    }

    static Bitmap load(Context c, String name) {
        if (name.startsWith("Photo: ")) {
            Bitmap b = photo(c, name.substring(7));
            if (b != null) return b;
            name = "Coast";
        }
        switch (name) {
            case "Coast": return soften(coast());
            case "Alpine": return soften(alpine());
            case "Dunes": return soften(dunes());
            default: return Art.wallpaper(name, 960, 540);
        }
    }

    private static Bitmap photo(Context c, String file) {
        File dir = c.getExternalFilesDir("wallpapers");
        if (dir == null) return null;
        File f = new File(dir, file);
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getPath(), o);
        if (o.outWidth <= 0) return null;
        int sample = 1;
        while (o.outWidth / (sample * 2) >= 1920) sample *= 2;
        o = new BitmapFactory.Options();
        o.inSampleSize = sample;
        o.inPreferredConfig = Bitmap.Config.RGB_565;
        return BitmapFactory.decodeFile(f.getPath(), o);
    }

    private static Bitmap soften(Bitmap small) {
        Art.blurInPlace(small, 1, 2);
        Bitmap big = Bitmap.createScaledBitmap(small, 1280, 720, true);
        small.recycle();
        return big;
    }

    private static Paint paint() {
        return new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);
    }

    private static void vgrad(Canvas c, float y0, float y1, int top, int bottom) {
        Paint p = paint();
        p.setShader(new LinearGradient(0, y0, 0, y1, top, bottom, Shader.TileMode.CLAMP));
        c.drawRect(0, y0, W, y1, p);
    }

    private static void glow(Canvas c, float x, float y, float r, int color) {
        Paint p = paint();
        p.setShader(new RadialGradient(x, y, r, color, color & 0x00FFFFFF, Shader.TileMode.CLAMP));
        c.drawCircle(x, y, r, p);
    }

    /** Scatter soft blobs inside a path: vegetation, rocks, ripples. */
    private static void speckle(Canvas c, Path clip, Random rnd, int count, float minR, float maxR,
                                float x0, float y0, float x1, float y1, int... colors) {
        c.save();
        c.clipPath(clip);
        Paint p = paint();
        for (int i = 0; i < count; i++) {
            p.setColor(colors[rnd.nextInt(colors.length)]);
            float x = x0 + rnd.nextFloat() * (x1 - x0), y = y0 + rnd.nextFloat() * (y1 - y0);
            c.drawCircle(x, y, minR + rnd.nextFloat() * (maxR - minR), p);
        }
        c.restore();
    }

    private static Bitmap coast() {
        Bitmap b = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Random rnd = new Random(7);
        float horizon = H * 0.40f;

        vgrad(c, 0, horizon + 2, 0xFF79AEDC, 0xFFE3EEF3);
        glow(c, W * 0.18f, H * 0.10f, W * 0.26f, 0xAAFFFFFF);
        glow(c, W * 0.55f, H * 0.02f, W * 0.30f, 0x88FFFFFF);
        glow(c, W * 0.88f, H * 0.22f, W * 0.20f, 0x99FFFFFF);

        Path head = new Path();
        head.moveTo(0, horizon - 6);
        head.quadTo(W * 0.22f, horizon - 16, W * 0.42f, horizon);
        head.lineTo(0, horizon + 2);
        head.close();
        Paint hp = paint();
        hp.setColor(0xFF8DA6B6);
        c.drawPath(head, hp);

        vgrad(c, horizon, H, 0xFF2A80AE, 0xFF3FA9C4);
        glow(c, W * 0.72f, H * 0.78f, W * 0.42f, 0xCC79D3CF);
        Path seaClip = new Path();
        seaClip.addRect(0, horizon, W, H, Path.Direction.CW);

        Path land = new Path();
        land.moveTo(W * 0.36f, H);
        land.quadTo(W * 0.56f, H * 0.64f, W * 0.80f, H * 0.47f);
        land.quadTo(W * 0.92f, H * 0.39f, W, H * 0.40f);
        land.lineTo(W, H);
        land.close();

        Paint surf = paint();
        surf.setStyle(Paint.Style.STROKE);
        surf.setStrokeWidth(7);
        surf.setColor(0xCCF4FBFC);
        c.drawPath(land, surf);
        surf.setStrokeWidth(14);
        surf.setColor(0x55E8F7F7);
        c.drawPath(land, surf);

        Paint lp = paint();
        lp.setShader(new LinearGradient(W * 0.5f, H * 0.45f, W, H, 0xFF9AA266, 0xFF5F7A3C,
                Shader.TileMode.CLAMP));
        c.drawPath(land, lp);
        speckle(c, land, rnd, 420, 1.5f, 5.5f, W * 0.35f, H * 0.38f, W, H,
                0xFF4E6A32, 0xFF3D5828, 0xFF8E8B55, 0xFFA99B6C, 0xFF6F8A45, 0xFFB7A57A);
        vgrad(c, 0, H * 0.55f, 0x26FFFFFF, 0x00FFFFFF);
        return b;
    }

    private static Bitmap alpine() {
        Bitmap b = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        Random rnd = new Random(11);
        vgrad(c, 0, H * 0.6f, 0xFF5E93CF, 0xFFD3E4F0);
        glow(c, W * 0.75f, H * 0.12f, W * 0.3f, 0x77FFFFFF);

        Paint p = paint();
        Path far = ridge(rnd, H * 0.30f, H * 0.16f, 7);
        p.setColor(0xFF8094AC);
        c.drawPath(far, p);
        speckle(c, far, rnd, 120, 2f, 6f, 0, H * 0.1f, W, H * 0.32f, 0xEEF4F7FA, 0xCCE3EAF2);
        Path near = ridge(rnd, H * 0.44f, H * 0.14f, 5);
        p.setColor(0xFF4F6176);
        c.drawPath(near, p);
        speckle(c, near, rnd, 80, 2f, 5f, 0, H * 0.26f, W, H * 0.40f, 0xDDF0F4F8);

        vgrad(c, H * 0.56f, H, 0xFF2D6A88, 0xFF1F4D66);
        glow(c, W * 0.5f, H * 0.62f, W * 0.5f, 0x5596C3DD);
        Path forest = new Path();
        forest.addRect(0, H * 0.50f, W, H * 0.60f, Path.Direction.CW);
        speckle(c, forest, rnd, 380, 2f, 5f, 0, H * 0.50f, W, H * 0.62f, 0xFF1F3A26, 0xFF2B4A30, 0xFF365A38);
        Path shore = new Path();
        shore.moveTo(0, H * 0.78f);
        shore.quadTo(W * 0.3f, H * 0.82f, W * 0.45f, H);
        shore.lineTo(0, H);
        shore.close();
        p.setColor(0xFF3F5A33);
        c.drawPath(shore, p);
        speckle(c, shore, rnd, 120, 2f, 5f, 0, H * 0.75f, W * 0.5f, H, 0xFF2F4A28, 0xFF56703F, 0xFF7C8A55);
        return b;
    }

    private static Path ridge(Random rnd, float base, float amp, int peaks) {
        Path p = new Path();
        p.moveTo(0, H);
        p.lineTo(0, base);
        float step = W / (float) (peaks * 2);
        for (int i = 0; i < peaks * 2; i++) {
            float x = step * (i + 1);
            float y = i % 2 == 0 ? base - amp * (0.5f + rnd.nextFloat() * 0.6f) : base - amp * rnd.nextFloat() * 0.25f;
            p.lineTo(x, y);
        }
        p.lineTo(W, H);
        p.close();
        return p;
    }

    private static Bitmap dunes() {
        Bitmap b = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        vgrad(c, 0, H * 0.55f, 0xFFE9A86B, 0xFFFBE3C2);
        glow(c, W * 0.70f, H * 0.42f, W * 0.35f, 0xCCFFF3D6);
        int[] cols = {0xFFDDA36C, 0xFFCC8550, 0xFFB46A3E, 0xFF94512F};
        float[] tops = {0.48f, 0.58f, 0.70f, 0.84f};
        Paint p = paint();
        for (int i = 0; i < cols.length; i++) {
            Path d = new Path();
            float y = H * tops[i];
            d.moveTo(0, y + 10);
            d.cubicTo(W * 0.25f, y - 18 + i * 4, W * 0.55f, y + 22, W, y - 6 + i * 3);
            d.lineTo(W, H);
            d.lineTo(0, H);
            d.close();
            p.setShader(new LinearGradient(0, y - 20, 0, H, cols[i] | 0xFF000000, darken(cols[i]),
                    Shader.TileMode.CLAMP));
            c.drawPath(d, p);
        }
        return b;
    }

    private static int darken(int c) {
        int r = (int) (((c >> 16) & 0xFF) * 0.7f), g = (int) (((c >> 8) & 0xFF) * 0.7f), b = (int) ((c & 0xFF) * 0.7f);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private Scenery() {}
}
