package com.halo.tvhome;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * tvOS context menu: a compact frosted panel that opens beside the held app icon, one action
 * per row with the label on the left and its symbol on the right; destructive actions in red.
 */
final class AppMenu extends FrameLayout {
    static final class Item {
        final String label;
        final int icon;
        final boolean destructive;
        final Runnable action;

        Item(String label, int icon, boolean destructive, Runnable action) {
            this.label = label;
            this.icon = icon;
            this.destructive = destructive;
            this.action = action;
        }
    }

    private static final int WIDTH = 236, RADIUS = 20, INSET = 6, ROW_H = 36, GAP = 2;
    private static final ColorStateList TEXT = states(0xFFFFFFFF, 0xF2FFFFFF);
    private static final ColorStateList RED = states(0xFFFF3B30, 0xFFFF453A);

    private final LinearLayout card;
    private Runnable onClose;

    AppMenu(Context c) {
        super(c);
        setClickable(true);
        setBackgroundColor(0x33000000);
        card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Ui.dp(INSET), Ui.dp(INSET), Ui.dp(INSET), Ui.dp(INSET - GAP));
        card.setElevation(Ui.dp(20));
        card.setClipChildren(false);
        addView(card, new LayoutParams(Ui.dp(WIDTH), LayoutParams.WRAP_CONTENT));
        setVisibility(GONE);
    }

    private static ColorStateList states(int focused, int idle) {
        return new ColorStateList(new int[][] {new int[] {android.R.attr.state_focused}, new int[] {}},
                new int[] {focused, idle});
    }

    boolean isOpen() {
        return getVisibility() == VISIBLE;
    }

    /** Opens above the anchor tile (or below it if there is no room), aligned to its left edge. */
    void show(View anchor, String title, List<Item> items, Bitmap behind, Runnable onClose) {
        this.onClose = onClose;
        card.removeAllViews();
        card.setBackground(new FrostDrawable(card, behind, Ui.dp(RADIUS), 0x8C3A3A3E, 0x992C2C30, 0x33FFFFFF));

        TextView head = text(11.5f, 600, 0x99FFFFFF);
        head.setText(title);
        head.setPadding(Ui.dp(10), Ui.dp(4), Ui.dp(10), Ui.dp(6));
        card.addView(head);

        View first = null;
        for (Item it : items) {
            View r = row(it);
            if (first == null) first = r;
        }

        setVisibility(VISIBLE);
        final View focusFirst = first;
        card.measure(MeasureSpec.makeMeasureSpec(Ui.dp(WIDTH), MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        int[] loc = new int[2];
        anchor.getLocationOnScreen(loc);
        int sw = getResources().getDisplayMetrics().widthPixels;
        int ch = card.getMeasuredHeight(), gap = Ui.dp(14);
        float scaleH = anchor.getHeight() * (anchor.getScaleY() - 1) / 2;
        int x = Math.max(Ui.dp(24), Math.min(loc[0], sw - Ui.dp(WIDTH) - Ui.dp(24)));
        int above = (int) (loc[1] - scaleH) - gap - ch;
        int y = above > Ui.dp(24) ? above : (int) (loc[1] + anchor.getHeight() + scaleH) + gap;
        card.setTranslationX(x);
        card.setTranslationY(y);
        card.setPivotX(Ui.dp(30));
        card.setPivotY(above > Ui.dp(24) ? ch : 0);
        card.setAlpha(0f);
        card.setScaleX(0.9f);
        card.setScaleY(0.9f);
        card.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(340).setInterpolator(Spring.PANEL).start();
        if (focusFirst != null) post(focusFirst::requestFocus);
    }

    void hide() {
        if (!isOpen()) return;
        setVisibility(GONE);
        Runnable r = onClose;
        onClose = null;
        if (r != null) r.run();
    }

    private View row(Item it) {
        Context c = getContext();
        LinearLayout r = new LinearLayout(c);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(Ui.dp(10), 0, Ui.dp(10), 0);
        r.setBackground(Glass.glassRow(RADIUS - INSET));
        r.setFocusable(true);
        r.setOnFocusChangeListener((v, f) -> v.animate().translationZ(f ? Ui.dp(2) : 0).setDuration(160).start());
        TextView t = text(13.5f, 560, 0);
        t.setTextColor(it.destructive ? RED : TEXT);
        t.setDuplicateParentStateEnabled(true);
        t.setText(it.label);
        r.addView(t, new LinearLayout.LayoutParams(0, -2, 1f));
        ImageView iv = new ImageView(c);
        iv.setImageResource(it.icon);
        iv.setImageTintList(it.destructive ? RED : TEXT);
        iv.setDuplicateParentStateEnabled(true);
        r.addView(iv, new LinearLayout.LayoutParams(Ui.dp(15), Ui.dp(15)));
        r.setOnClickListener(v -> {
            Runnable close = onClose;
            onClose = null;
            setVisibility(GONE);
            it.action.run();
            if (close != null && !isOpen()) close.run();
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, Ui.dp(ROW_H));
        lp.bottomMargin = Ui.dp(GAP);
        card.addView(r, lp);
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
