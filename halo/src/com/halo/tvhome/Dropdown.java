package com.halo.tvhome;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Control Center panel that drops from the top-right capsule. Rows are dark glass, focus turns white. */
final class Dropdown extends FrameLayout {
    /**
     * One spacing system for every panel: a 10dp inset on all sides, 8dp between items, and
     * highlights whose corners are concentric with the panel (radius minus inset). Focused items
     * never grow past the inset, so nothing touches or gets clipped at the panel edge.
     */
    private static final int WIDTH = 290, RADIUS = 24, INSET = 10, GAP = 8, ROW_H = 48;
    private static final float ROW_RADIUS = RADIUS - INSET;
    private final LinearLayout card;
    private Runnable onClose;

    Dropdown(Context c) {
        super(c);
        setClickable(true);
        setClipChildren(false);  // focused tiles grow past the panel's edge
        card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Glass.panel(RADIUS, 0xB32C2C2E));
        card.setElevation(Ui.dp(20));
        // Every item carries GAP below it, so the bottom padding makes up the rest of the inset.
        card.setPadding(Ui.dp(INSET), Ui.dp(INSET), Ui.dp(INSET), Ui.dp(INSET - GAP));
        card.setClipChildren(false);
        card.setClipToPadding(false);
        LayoutParams lp = new LayoutParams(Ui.dp(WIDTH), LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.END);
        lp.topMargin = Ui.dp(28 + ControlBar.HEIGHT + 8);
        lp.rightMargin = Ui.dp(28);
        addView(card, lp);
        setVisibility(GONE);
    }

    boolean isOpen() {
        return getVisibility() == VISIBLE;
    }

    Dropdown begin(android.graphics.Bitmap behind) {
        return begin(behind, WIDTH);
    }

    Dropdown begin(android.graphics.Bitmap behind, int widthDp) {
        card.removeAllViews();
        card.getLayoutParams().width = Ui.dp(widthDp);
        card.requestLayout();
        card.setBackground(new FrostDrawable(card, behind, Ui.dp(RADIUS), 0x8C3A3A3E, 0x992C2C30, 0x40FFFFFF));
        card.setElevation(Ui.dp(20));
        card.setPadding(Ui.dp(INSET), Ui.dp(INSET), Ui.dp(INSET), Ui.dp(INSET - GAP));
        this.behind = behind;
        return this;
    }

    private android.graphics.Bitmap behind;

    Dropdown header(int icon, CharSequence title, CharSequence subtitle) {
        LinearLayout h = line(icon, title, subtitle, 0, false);
        h.setPadding(Ui.dp(12), Ui.dp(4), Ui.dp(12), Ui.dp(10));
        card.addView(h);
        View div = new View(getContext());
        div.setBackgroundColor(0x26FFFFFF);
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(-1, Math.max(1, Ui.dp(0.75f)));
        dl.setMargins(Ui.dp(12), 0, Ui.dp(12), Ui.dp(GAP));
        card.addView(div, dl);
        return this;
    }

    Dropdown section(String text) {
        TextView t = text(11, 650, 0x99FFFFFF);
        t.setText(text.toUpperCase());
        t.setLetterSpacing(0.08f);
        t.setPadding(Ui.dp(12), Ui.dp(GAP), 0, Ui.dp(GAP / 2f + 2));
        card.addView(t);
        return this;
    }

    Dropdown row(int icon, CharSequence title, CharSequence subtitle, Runnable click) {
        LinearLayout r = line(icon, title, subtitle, 0x26FFFFFF, true);
        r.setOnClickListener(v -> click.run());
        // Every row is the same height, one line or two, so the list reads as an even stack.
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, Ui.dp(ROW_H));
        lp.bottomMargin = Ui.dp(GAP);
        card.addView(r, lp);
        return this;
    }

    /** Square Control Center toggles, three per row. */
    Dropdown tiles(int[] icons, String[] labels, Runnable[] clicks) {
        return tiles(icons, labels, clicks, 74);
    }

    /** A row of equal tiles; a short height (e.g. ROW_H) makes compact icon-only buttons. */
    Dropdown tiles(int[] icons, String[] labels, Runnable[] clicks, int heightDp) {
        GridLayout g = new GridLayout(getContext());
        g.setColumnCount(3);
        g.setClipChildren(false);
        for (int i = 0; i < icons.length; i++) {
            final int idx = i;
            LinearLayout t = new LinearLayout(getContext());
            t.setOrientation(LinearLayout.VERTICAL);
            t.setGravity(Gravity.CENTER);
            t.setBackground(Glass.button(ROW_RADIUS, 0x26FFFFFF));
            t.setFocusable(true);
            t.setOnClickListener(v -> clicks[idx].run());
            Glass.lift(t, 1f);
            ImageView iv = new ImageView(getContext());
            iv.setImageResource(icons[i]);
            iv.setImageTintList(Glass.FG);
            iv.setDuplicateParentStateEnabled(true);
            t.addView(iv, new LinearLayout.LayoutParams(Ui.dp(22), Ui.dp(22)));
            if (labels[i] != null) {
                TextView l = text(10.5f, 600, 0);
                l.setTextColor(Glass.FG_DIM);
                l.setDuplicateParentStateEnabled(true);
                l.setText(labels[i]);
                l.setGravity(Gravity.CENTER);
                l.setPadding(Ui.dp(4), Ui.dp(6), Ui.dp(4), 0);
                t.addView(l);
            }
            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            int inner = Ui.dp(WIDTH - 2 * INSET);
            lp.width = (inner - 2 * Ui.dp(GAP)) / 3;
            lp.height = Ui.dp(heightDp);
            lp.setMargins(0, 0, i % 3 < 2 ? Ui.dp(GAP) : 0, Ui.dp(GAP));
            g.addView(t, lp);
        }
        card.addView(g);
        return this;
    }

    // ---- tvOS 26 Control Center grid: 4 columns of 46dp units, 9dp gaps -------------------

    /**
     * tvOS 26 Control Center: no panel behind it; every control is its own floating piece of
     * glass. Power Off is a rounded square, the wide controls are capsules, the shortcuts circles.
     */
    static final int CC_UNIT = 56, CC_GAP = 12;
    static final int CC_WIDTH = 4 * CC_UNIT + 3 * CC_GAP;
    private static final float CC_RADIUS = 26;
    private GridLayout ccGrid;

    Dropdown ccGrid() {
        card.setBackground(null);
        card.setElevation(0);
        card.setPadding(0, 0, 0, 0);
        ccGrid = new GridLayout(getContext());
        ccGrid.setColumnCount(4);
        ccGrid.setClipChildren(false);
        card.addView(ccGrid);
        return this;
    }

    private View ccTile(int row, int col, int rowSpan, int colSpan, Runnable click) {
        LinearLayout t = new LinearLayout(getContext());
        int w = Ui.dp(colSpan * CC_UNIT + (colSpan - 1) * CC_GAP);
        int h = Ui.dp(rowSpan * CC_UNIT + (rowSpan - 1) * CC_GAP);
        // Rounded square for the big tile; capsules and circles (radius = half height) otherwise.
        final float r = rowSpan > 1 ? Ui.dp(CC_RADIUS) : h / 2f;
        android.graphics.drawable.StateListDrawable bg = new android.graphics.drawable.StateListDrawable();
        android.graphics.drawable.GradientDrawable on = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TOP_BOTTOM, new int[] {0xF5FFFFFF, 0xE6F2F2F5});
        on.setCornerRadius(r);
        bg.addState(new int[] {android.R.attr.state_focused}, on);
        // Frosted, mostly opaque glass (Apple's Control Center reads well over any background):
        // a smoky grey fill over the blur, lighter at the top, with a bright rim.
        bg.addState(new int[] {}, new FrostDrawable(t, behind, r, 0xA6545458, 0xB33A3A3E, 0x59FFFFFF));
        bg.setEnterFadeDuration(150);
        bg.setExitFadeDuration(150);
        t.setBackground(bg);
        // Explicit outline: the glass drawable has none, which would give a square shadow.
        t.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(View v, android.graphics.Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), r);
            }
        });
        t.setElevation(Ui.dp(6));
        t.setFocusable(true);
        t.setOnClickListener(v -> click.run());
        t.setOnFocusChangeListener((v, f) -> v.animate().scaleX(f ? 1.06f : 1f).scaleY(f ? 1.06f : 1f)
                .translationZ(f ? Ui.dp(8) : 0).setStartDelay(0).setDuration(300).setInterpolator(Spring.FOCUS).start());
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams(
                GridLayout.spec(row, rowSpan), GridLayout.spec(col, colSpan));
        lp.width = w;
        lp.height = h;
        lp.setMargins(0, 0, col + colSpan < 4 ? Ui.dp(CC_GAP) : 0, Ui.dp(CC_GAP));
        ccGrid.addView(t, lp);
        return t;
    }

    private ImageView ccIcon(int res, int sizeDp, android.content.res.ColorStateList tint) {
        ImageView iv = new ImageView(getContext());
        iv.setImageResource(res);
        iv.setImageTintList(tint);
        iv.setDuplicateParentStateEnabled(true);
        iv.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(sizeDp), Ui.dp(sizeDp)));
        return iv;
    }

    /** The big square: icon over a label (Power Off). */
    Dropdown ccLarge(int row, int col, int icon, String label, Runnable click) {
        LinearLayout t = (LinearLayout) ccTile(row, col, 2, 2, click);
        t.setOrientation(LinearLayout.VERTICAL);
        t.setGravity(Gravity.CENTER);
        t.addView(ccIcon(icon, 46, Glass.FG));
        TextView l = text(12.5f, 640, 0);
        l.setTextColor(Glass.FG);
        l.setDuplicateParentStateEnabled(true);
        l.setText(label);
        l.setGravity(Gravity.CENTER);
        l.setPadding(0, Ui.dp(8), 0, 0);
        t.addView(l, new LinearLayout.LayoutParams(-2, -2));
        return this;
    }

    /** A wide tile: icon, then a title over a status line. Returns the status TextView. */
    TextView ccWide(int row, int col, int icon, int iconTint, String title, String status, Runnable click) {
        LinearLayout t = (LinearLayout) ccTile(row, col, 1, 2, click);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setPadding(Ui.dp(16), 0, Ui.dp(12), 0);
        // White icons turn dark on the white focused tile; coloured ones keep their colour.
        int focusedTint = iconTint == 0xFFFFFFFF ? 0xFF000000 : iconTint;
        t.addView(ccIcon(icon, 24, new android.content.res.ColorStateList(
                new int[][] {new int[] {android.R.attr.state_focused}, new int[] {}},
                new int[] {focusedTint, iconTint})));
        LinearLayout col2 = new LinearLayout(getContext());
        col2.setOrientation(LinearLayout.VERTICAL);
        col2.setDuplicateParentStateEnabled(true);
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0, -2, 1f);
        cl.leftMargin = Ui.dp(11);
        t.addView(col2, cl);
        TextView a = text(12, 640, 0);
        a.setTextColor(Glass.FG);
        a.setDuplicateParentStateEnabled(true);
        a.setText(title);
        if (status == null) {  // title-only tiles wrap to two lines, like "Do Not / Disturb"
            a.setSingleLine(false);
            a.setMaxLines(2);
        }
        col2.addView(a);
        if (status == null) return a;
        TextView b = text(10.5f, 500, 0);
        b.setTextColor(new android.content.res.ColorStateList(
                new int[][] {new int[] {android.R.attr.state_focused}, new int[] {}},
                new int[] {0x99000000, 0x99FFFFFF}));
        b.setDuplicateParentStateEnabled(true);
        b.setText(status);
        b.setPadding(0, Ui.dp(2), 0, 0);
        col2.addView(b);
        return b;
    }

    /** A small square icon button. */
    Dropdown ccSmall(int row, int col, int icon, Runnable click) {
        LinearLayout t = (LinearLayout) ccTile(row, col, 1, 1, click);
        t.setGravity(Gravity.CENTER);
        t.addView(ccIcon(icon, 22, Glass.FG));
        return this;
    }

    /** Control Center tiles arrive one after another, each popping in on a spring. */
    private void cascade() {
        if (ccGrid == null || ccGrid.getParent() != card) return;
        for (int i = 0; i < ccGrid.getChildCount(); i++) {
            View t = ccGrid.getChildAt(i);
            t.setAlpha(0f);
            t.setScaleX(0.86f);
            t.setScaleY(0.86f);
            t.animate().alpha(1f).scaleX(1f).scaleY(1f).setStartDelay(20 + i * 18L).setDuration(320)
                    .setInterpolator(Spring.PANEL).start();
        }
    }

    void show(Runnable onClose) {
        this.onClose = onClose;
        setVisibility(VISIBLE);
        card.setPivotX(card.getWidth() > 0 ? card.getWidth() : Ui.dp(290));
        card.setPivotY(0);
        card.setAlpha(0f);
        card.setScaleX(0.92f);
        card.setScaleY(0.92f);
        card.setTranslationY(-Ui.dp(8));
        cascade();
        card.animate().alpha(1f).scaleX(1f).scaleY(1f).translationY(0).setDuration(340)
                .setInterpolator(Spring.PANEL).start();
        post(() -> {
            View first = firstFocusable(card);
            if (first != null) first.requestFocus();
        });
    }

    void hide() {
        if (!isOpen()) return;
        setVisibility(GONE);
        Runnable r = onClose;
        onClose = null;
        if (r != null) r.run();
    }

    private static View firstFocusable(android.view.ViewGroup g) {
        for (int i = 0; i < g.getChildCount(); i++) {
            View v = g.getChildAt(i);
            if (v.isFocusable()) return v;
            if (v instanceof android.view.ViewGroup) {
                View f = firstFocusable((android.view.ViewGroup) v);
                if (f != null) return f;
            }
        }
        return null;
    }

    private LinearLayout line(int icon, CharSequence title, CharSequence subtitle, int fill, boolean focusable) {
        Context c = getContext();
        LinearLayout r = new LinearLayout(c);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(Ui.dp(12), 0, Ui.dp(12), 0);
        if (focusable) {
            r.setBackground(Glass.button(ROW_RADIUS, fill));
            r.setFocusable(true);
            Glass.lift(r, 1f);
        }
        ImageView iv = new ImageView(c);
        iv.setImageResource(icon);
        iv.setImageTintList(focusable ? new android.content.res.ColorStateList(
                new int[][] {new int[] {android.R.attr.state_focused}, new int[] {}},
                new int[] {0xFF0A84FF, 0xFF64B5FF}) : android.content.res.ColorStateList.valueOf(0xFFFFFFFF));
        iv.setDuplicateParentStateEnabled(true);
        r.addView(iv, new LinearLayout.LayoutParams(Ui.dp(22), Ui.dp(22)));
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setDuplicateParentStateEnabled(true);
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(0, -2, 1f);
        cl.leftMargin = Ui.dp(12);
        r.addView(col, cl);
        TextView t = text(focusable ? 14.5f : 16, 600, 0);
        t.setTextColor(Glass.FG);
        t.setDuplicateParentStateEnabled(true);
        t.setText(title);
        col.addView(t);
        if (!TextUtils.isEmpty(subtitle)) {
            TextView s = text(12, 480, 0);
            s.setTextColor(new android.content.res.ColorStateList(
                    new int[][] {new int[] {android.R.attr.state_focused}, new int[] {}},
                    new int[] {0x99000000, 0x99FFFFFF}));
            s.setDuplicateParentStateEnabled(true);
            s.setText(subtitle);
            s.setPadding(0, Ui.dp(2), 0, 0);
            col.addView(s);
        }
        return r;
    }

    private TextView text(float sp, int weight, int color) {
        TextView t = new TextView(getContext());
        t.setTextSize(sp);
        t.setTypeface(Ui.font(weight));
        t.setTextColor(color);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setIncludeFontPadding(false);
        return t;
    }
}
