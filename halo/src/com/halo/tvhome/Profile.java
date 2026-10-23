package com.halo.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;

import java.io.File;

/** The profile picture pushed by set-avatar.sh, as a circle; falls back to a gradient disc. */
final class Profile {
    private static Bitmap circle;
    private static long stamp = -1;

    static Drawable avatar(Context c) {
        File f = new File(c.getExternalFilesDir("profile"), "avatar.png");
        if (f.lastModified() != stamp) {
            stamp = f.lastModified();
            circle = null;
            Bitmap src = f.canRead() ? BitmapFactory.decodeFile(f.getPath()) : null;
            if (src != null) {
                int s = Math.min(src.getWidth(), src.getHeight());
                circle = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888);
                Paint p = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
                BitmapShader sh = new BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
                android.graphics.Matrix m = new android.graphics.Matrix();
                m.setTranslate(-(src.getWidth() - s) / 2f, -(src.getHeight() - s) / 2f);
                sh.setLocalMatrix(m);
                p.setShader(sh);
                new Canvas(circle).drawCircle(s / 2f, s / 2f, s / 2f, p);
            }
        }
        if (circle != null) return new BitmapDrawable(c.getResources(), circle);
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[] {0xFFFFB340, 0xFFFF375F});
        g.setShape(GradientDrawable.OVAL);
        return g;
    }

    static boolean hasPhoto(Context c) {
        avatar(c);
        return circle != null;
    }

    private Profile() {}
}
