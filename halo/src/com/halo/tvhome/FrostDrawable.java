package com.halo.tvhome;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;
import android.view.View;

/**
 * Glass material for panels: draws a blurred snapshot of the screen exactly where the panel
 * sits (so it reads as a blur of what is behind it), then a tint wash, a top sheen and a
 * hairline rim. The snapshot can be smaller than the screen; it is scaled up to fit.
 */
final class FrostDrawable extends Drawable {
    private final View host;
    private final Paint frost = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint wash = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sheen = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final BitmapShader shader;
    private final float scale;
    private final float radius;
    private final int washTop, washBottom;
    private int rimColor;
    private final Matrix m = new Matrix();
    private final RectF r = new RectF();
    private final int[] loc = new int[2];

    FrostDrawable(View host, Bitmap snapshot, float radius, int washTop, int washBottom, int rimColor) {
        this.host = host;
        this.radius = radius;
        this.washTop = washTop;
        this.washBottom = washBottom;
        shader = snapshot == null ? null
                : new BitmapShader(snapshot, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        scale = snapshot == null ? 1f
                : host.getResources().getDisplayMetrics().widthPixels / (float) snapshot.getWidth();
        frost.setShader(shader);
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(Math.max(1, Ui.dp(1f)));
        rim.setColor(rimColor);
        this.rimColor = rimColor;
    }

    @Override
    protected void onBoundsChange(Rect b) {
        wash.setShader(new LinearGradient(0, b.top, 0, b.bottom, washTop, washBottom, Shader.TileMode.CLAMP));
        sheen.setShader(new LinearGradient(0, b.top, 0, b.top + b.height() * 0.4f,
                0x1FFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
    }

    @Override
    public void draw(Canvas c) {
        r.set(getBounds());
        if (shader != null) {
            // Resting position on screen (ignoring slide-in translation), so the glass
            // shows what will be behind it once it settles.
            host.getLocationOnScreen(loc);
            float x = loc[0] - host.getTranslationX(), y = loc[1] - host.getTranslationY();
            m.setScale(scale, scale);
            m.postTranslate(-x, -y);
            shader.setLocalMatrix(m);
            c.drawRoundRect(r, radius, radius, frost);
        }
        c.drawRoundRect(r, radius, radius, wash);
        c.drawRoundRect(r, radius, radius, sheen);
        if (rimColor != 0) {
            float in = rim.getStrokeWidth() / 2;
            r.inset(in, in);
            c.drawRoundRect(r, radius - in, radius - in, rim);
        }
    }

    private int alpha = 255;

    /** Fades the whole material (used when the top bar dissolves into separate buttons). */
    @Override public void setAlpha(int a) {
        alpha = a;
        frost.setAlpha(a);
        wash.setAlpha(a);
        sheen.setAlpha(a);
        rim.setAlpha(Math.round(Color.alpha(rimColor) * a / 255f));
        invalidateSelf();
    }

    @Override public int getAlpha() { return alpha; }
    @Override public void setColorFilter(ColorFilter f) {}
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
