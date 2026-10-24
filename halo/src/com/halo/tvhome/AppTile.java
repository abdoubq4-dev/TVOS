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
    private ValueAnimator sheenAnim, wiggleAnim;

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
        // tvOS "pull and land": the tile losing focus is tugged a few dp toward where focus
        // went, the new one arrives from that side; both settle on a spring.
        float pull = Ui.dp(5);
        int dx = direction == FOCUS_RIGHT ? 1 : direction == FOCUS_LEFT ? -1 : 0;
        int dy = direction == FOCUS_DOWN ? 1 : direction == FOCUS_UP ? -1 : 0;
        if (gained && (dx != 0 || dy != 0)) {
            setTranslationX(-dx * pull);
            setTranslationY(-dy * pull);
        }
        animate().scaleX(gained ? GridPanel.FOCUS_SCALE : 1f).scaleY(gained ? GridPanel.FOCUS_SCALE : 1f)
                .translationZ(gained ? Ui.dp(22) : 0).translationX(0).translationY(0)
                .setDuration(gained ? 320 : 280).setInterpolator(Spring.FOCUS).start();
        label.animate().alpha(gained && showLabel ? 1f : 0f).translationY(gained ? Ui.dp(8) : 0)
                .setDuration(gained ? 220 : 120).setInterpolator(EASE).start();
        stop(sheenAnim);
        sheenAnim = ValueAnimator.ofFloat(glow, gained ? 1f : 0f);
        sheenAnim.setDuration(gained ? 220 : 150);
        sheenAnim.setInterpolator(EASE);
        sheenAnim.addUpdateListener(a -> {
            glow = (float) a.getAnimatedValue();
            invalidate();
        });
        sheenAnim.start();
        // No continuous sway: with a button remote there is no thumb to follow, and an endless
        // animation keeps the TV GPU busy, which delayed every remote press.
        placeLight(0f);
    }

    /** A nudge toward a d-pad press that hits the edge of the grid. */
    void nudge(int dx, int dy) {
        animate().translationX(dx * Ui.dp(7)).translationY(dy * Ui.dp(7)).setDuration(80)
                .setInterpolator(EASE)
                .withEndAction(() -> animate().translationX(0).translationY(0).setDuration(340)
                        .setInterpolator(new Spring(0.45f, 4f)).start())
                .start();
    }

    /** Losing focus toward (dx, dy): a quick tug that way before the spring brings it home. */
    void tug(int dx, int dy) {
        setTranslationX(dx * Ui.dp(5));
        setTranslationY(dy * Ui.dp(5));
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
