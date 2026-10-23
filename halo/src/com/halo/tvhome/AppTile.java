package com.halo.tvhome;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.view.animation.LinearInterpolator;
import android.view.animation.PathInterpolator;
import android.widget.TextView;

/** One app icon: rounded, lifts and tilts on focus with a specular sweep, like tvOS. */
final class AppTile extends View {
    static final PathInterpolator EASE = new PathInterpolator(0.2f, 0.9f, 0.25f, 1f);

    AppEntry app;
    final TextView label;
    /** tvOS shows names under focused icons in the grid, but never in the dock. */
    boolean showLabel = true;

    private final Paint bmp = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);
    private final Paint edge = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint gloss = new Paint();
    private final Matrix sheenMatrix = new Matrix();
    private final RectF rect = new RectF();
    private final float radius = Ui.dp(GridPanel.TILE_RADIUS);
    private float glow;
    private ValueAnimator sheenAnim, tiltAnim, wiggleAnim;

    AppTile(Context c, AppEntry app, TextView label) {
        super(c);
        this.app = app;
        this.label = label;
        setFocusable(true);
        setClickable(true);
        setLongClickable(true);
        setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), radius);
            }
        });
        // Corners are baked into the art (see Art.round), so no per-frame outline clipping;
        // only the focused tile casts a shadow, which keeps drawing cheap on a TV GPU.
        setElevation(0);
        setCameraDistance(9000 * Ui.density);
        edge.setStyle(Paint.Style.STROKE);
        edge.setStrokeWidth(Math.max(1, Ui.dp(0.75f)));
        edge.setColor(0x38FFFFFF);
    }

    /**
     * tvOS focus, per Apple's HIG: the icon lifts toward the viewer, sways gently, and its surface
     * lights up. The light is a soft round glow above the top edge that drifts with the sway
     * (no diagonal sweep).
     */
    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        gloss.setShader(new android.graphics.RadialGradient(0, 0, w * 0.85f,
                new int[] {0x47FFFFFF, 0x1AFFFFFF, 0x00FFFFFF}, new float[] {0f, 0.45f, 1f},
                Shader.TileMode.CLAMP));
        placeLight(0f);
    }

    /** Light position for sway phase t (radians): above centre, drifting side to side. */
    private void placeLight(float t) {
        int w = getWidth(), h = getHeight();
        sheenMatrix.setTranslate(w * (0.5f + 0.16f * (float) Math.sin(t)), -h * (0.12f + 0.06f * (float) Math.cos(t)));
        if (gloss.getShader() != null) gloss.getShader().setLocalMatrix(sheenMatrix);
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        rect.set(0, 0, w, h);
        if (app.art != null) c.drawBitmap(app.art, null, rect, bmp);
        if (glow > 0f) {
            gloss.setAlpha(Math.round(255 * glow));
            c.drawRoundRect(rect, radius, radius, gloss);
        }
        float in = edge.getStrokeWidth() / 2;
        rect.inset(in, in);
        c.drawRoundRect(rect, radius, radius, edge);
    }

    @Override
    protected void onFocusChanged(boolean gained, int direction, Rect previous) {
        super.onFocusChanged(gained, direction, previous);
        animate().scaleX(gained ? GridPanel.FOCUS_SCALE : 1f).scaleY(gained ? GridPanel.FOCUS_SCALE : 1f)
                .translationZ(gained ? Ui.dp(22) : 0)
                .setDuration(gained ? 220 : 160).setInterpolator(EASE).start();
        label.animate().alpha(gained && showLabel ? 1f : 0f).translationY(gained ? Ui.dp(8) : 0)
                .setDuration(gained ? 220 : 120).setInterpolator(EASE).start();
        stop(sheenAnim);
        sheenAnim = ValueAnimator.ofFloat(glow, gained ? 1f : 0f);
        sheenAnim.setDuration(gained ? 360 : 180);
        sheenAnim.setInterpolator(EASE);
        sheenAnim.addUpdateListener(a -> {
            glow = (float) a.getAnimatedValue();
            invalidate();
        });
        sheenAnim.start();
        if (gained) {
            startTilt();
        } else {
            stop(tiltAnim);
            placeLight(0f);
            animate().rotationX(0).rotationY(0);
        }
    }

    /** Gentle sway while focused, standing in for the Siri Remote touch tilt; the light follows. */
    private void startTilt() {
        stop(tiltAnim);
        tiltAnim = ValueAnimator.ofFloat(0f, (float) (Math.PI * 2));
        tiltAnim.setDuration(7000);
        tiltAnim.setStartDelay(300);
        tiltAnim.setRepeatCount(ValueAnimator.INFINITE);
        tiltAnim.setInterpolator(new LinearInterpolator());
        tiltAnim.addUpdateListener(a -> {
            float t = (float) a.getAnimatedValue();
            setRotationY((float) Math.sin(t) * 1.6f);
            setRotationX((float) Math.cos(t) * 1.0f);
            placeLight(t);
            invalidate();
        });
        tiltAnim.start();
    }

    /** A nudge toward a d-pad press that hits the edge of the grid. */
    void nudge(int dx, int dy) {
        animate().translationX(dx * Ui.dp(6)).translationY(dy * Ui.dp(6)).setDuration(90)
                .withEndAction(() -> animate().translationX(0).translationY(0).setDuration(160).start())
                .start();
    }

    void wiggle(boolean on) {
        stop(wiggleAnim);
        if (!on) {
            setRotation(0);
            return;
        }
        wiggleAnim = ValueAnimator.ofFloat(-1.6f, 1.6f);
        wiggleAnim.setDuration(130);
        wiggleAnim.setRepeatMode(ValueAnimator.REVERSE);
        wiggleAnim.setRepeatCount(ValueAnimator.INFINITE);
        wiggleAnim.addUpdateListener(a -> setRotation((float) a.getAnimatedValue()));
        wiggleAnim.start();
    }

    private static void stop(ValueAnimator a) {
        if (a != null) a.cancel();
    }
}
