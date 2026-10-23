package com.halo.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

/**
 * The dock's glass: draws the frosted wallpaper exactly where the dock sits on screen (so it
 * reads as a blur of what is behind it), then a pale tint, a top sheen and a bright hairline.
 * Corners are concentric with the icons: dock radius = icon radius + padding.
 */
final class FrostDock extends View {
    private final Paint frost = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint tint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint sheen = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix m = new Matrix();
    private final RectF r = new RectF();
    private final int[] loc = new int[2];
    private BitmapShader shader;
    float radius;

    FrostDock(Context c) {
        super(c);
        tint.setColor(0x24FFFFFF);
        rim.setStyle(Paint.Style.STROKE);
        rim.setStrokeWidth(Math.max(1, Ui.dp(0.75f)));
        rim.setColor(0x47FFFFFF);
    }

    void setFrost(Bitmap b) {
        shader = b == null ? null : new BitmapShader(b, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        frost.setShader(shader);
        invalidate();
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        sheen.setShader(new LinearGradient(0, 0, 0, h, 0x1FFFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP));
    }

    @Override
    protected void onDraw(Canvas c) {
        r.set(0, 0, getWidth(), getHeight());
        if (shader != null) {
            getLocationOnScreen(loc);
            m.setTranslate(-loc[0], -loc[1]);
            shader.setLocalMatrix(m);
            c.drawRoundRect(r, radius, radius, frost);
        }
        c.drawRoundRect(r, radius, radius, tint);
        c.drawRoundRect(r, radius, radius, sheen);
        float in = rim.getStrokeWidth() / 2;
        r.inset(in, in);
        c.drawRoundRect(r, radius - in, radius - in, rim);
    }
}
