package com.halo.tvhome;

import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;

/** Shared Liquid Glass-style surfaces: translucent fill, hairline edge, white when focused. */
final class Glass {
    static final ColorStateList FG = new ColorStateList(
            new int[][] {new int[] {android.R.attr.state_focused}, new int[] {}},
            new int[] {0xFF000000, 0xFFFFFFFF});
    static final ColorStateList FG_DIM = new ColorStateList(
            new int[][] {new int[] {android.R.attr.state_focused}, new int[] {}},
            new int[] {0xFF000000, 0xD9FFFFFF});

    static GradientDrawable flat(float radiusDp, int fill) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(Ui.dp(radiusDp));
        g.setColor(fill);
        g.setStroke(Math.max(1, Ui.dp(0.75f)), 0x33FFFFFF);
        return g;
    }

    /** Translucent tint, a specular sheen across the top half and a bright hairline rim. */
    static Drawable panel(float radiusDp, int fill) {
        float r = Ui.dp(radiusDp);
        GradientDrawable base = new GradientDrawable();
        base.setCornerRadius(r);
        base.setColor(fill);
        GradientDrawable sheen = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {0x29FFFFFF, 0x08FFFFFF, 0x00FFFFFF});
        sheen.setCornerRadius(r);
        GradientDrawable rim = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {0x00000000, 0x00000000});
        rim.setCornerRadius(r);
        rim.setStroke(Math.max(1, Ui.dp(0.75f)), 0x40FFFFFF);
        return new android.graphics.drawable.LayerDrawable(new Drawable[] {base, sheen, rim});
    }

    /** Pill or circle button background (radiusDp large enough = circle). */
    static Drawable button(float radiusDp, int idleFill) {
        StateListDrawable s = new StateListDrawable();
        GradientDrawable on = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {0xF0FFFFFF, 0xDEF4F4F7});
        on.setCornerRadius(Ui.dp(radiusDp));
        s.addState(new int[] {android.R.attr.state_focused}, on);
        s.addState(new int[] {}, panel(radiusDp, idleFill));
        return s;
    }

    /**
     * Apple-style list highlight: not flat white but a slightly translucent, softly graded
     * white, so the frosted panel still faintly shows through.
     */
    static Drawable softRow(float radiusDp) {
        StateListDrawable s = new StateListDrawable();
        GradientDrawable on = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {0xF0FFFFFF, 0xDEF4F4F7});
        on.setCornerRadius(Ui.dp(radiusDp));
        s.addState(new int[] {android.R.attr.state_focused}, on);
        s.addState(new int[] {}, new android.graphics.drawable.ColorDrawable(0));
        return s;
    }

    /** Translucent glass platter shown only under a focused glyph button. */
    static Drawable focusPlatter(float radiusDp) {
        StateListDrawable s = new StateListDrawable();
        GradientDrawable on = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {0x47FFFFFF, 0x2EFFFFFF});
        on.setCornerRadius(Ui.dp(radiusDp));
        on.setStroke(Math.max(1, Ui.dp(0.5f)), 0x33FFFFFF);
        s.addState(new int[] {android.R.attr.state_focused}, on);
        s.addState(new int[] {}, new android.graphics.drawable.ColorDrawable(0));
        s.setEnterFadeDuration(160);
        s.setExitFadeDuration(140);
        return s;
    }

    /** White text/icons on dark glass, in both states: tvOS 26 focus brightens the glass instead. */
    static final ColorStateList ON_GLASS = ColorStateList.valueOf(0xFFFFFFFF);

    /** tvOS 26 focus on dark glass: a brighter, still-translucent frosted fill with a bright rim. */
    static GradientDrawable glassLit(float radiusDp) {
        GradientDrawable on = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {0x80FFFFFF, 0x52FFFFFF});
        on.setCornerRadius(Ui.dp(radiusDp));
        on.setStroke(Math.max(1, Ui.dp(0.75f)), 0x99FFFFFF);
        return on;
    }

    static Drawable glassButton(float radiusDp, int idleFill) {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[] {android.R.attr.state_focused}, glassLit(radiusDp));
        s.addState(new int[] {}, panel(radiusDp, idleFill));
        s.setEnterFadeDuration(120);
        s.setExitFadeDuration(120);
        return s;
    }

    static Drawable glassRow(float radiusDp) {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[] {android.R.attr.state_focused}, glassLit(radiusDp));
        s.addState(new int[] {}, new android.graphics.drawable.ColorDrawable(0));
        s.setEnterFadeDuration(120);
        s.setExitFadeDuration(120);
        return s;
    }

    /** Row highlight used in lists: transparent until focused. */
    static Drawable row(float radiusDp) {
        StateListDrawable s = new StateListDrawable();
        GradientDrawable on = new GradientDrawable();
        on.setCornerRadius(Ui.dp(radiusDp));
        on.setColor(0xFFFFFFFF);
        s.addState(new int[] {android.R.attr.state_focused}, on);
        s.addState(new int[] {}, new android.graphics.drawable.ColorDrawable(0));
        return s;
    }

    /**
     * tvOS focus: the highlight grows slightly and lifts off the glass on a soft shadow.
     * The shadow follows the background's outline, so idle (transparent) rows cast none.
     */
    static void lift(android.view.View v, float scale) {
        v.setOnFocusChangeListener((x, f) -> x.animate().scaleX(f ? scale : 1f).scaleY(f ? scale : 1f)
                .translationZ(f ? Ui.dp(2) : 0).setDuration(180).setInterpolator(AppTile.EASE).start());
    }

    private Glass() {}
}
