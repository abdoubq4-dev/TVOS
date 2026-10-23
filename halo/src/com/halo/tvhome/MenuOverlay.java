package com.halo.tvhome;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.function.IntConsumer;

/** A tvOS-style glass action sheet: dark frosted card, the focused row turns white. */
final class MenuOverlay extends FrameLayout {
    private final LinearLayout card;
    private Runnable onClose;

    MenuOverlay(Context c) {
        super(c);
        setBackgroundColor(0xA6000000);
        setClickable(true);
        card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(18);
        card.setPadding(pad, Ui.dp(22), pad, pad);
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(Ui.dp(28));
        bg.setColor(0xF02C2C2E);
        bg.setStroke(Math.max(1, Ui.dp(0.75f)), 0x33FFFFFF);
        card.setBackground(bg);
        card.setElevation(Ui.dp(24));
        addView(card, new LayoutParams(Ui.dp(400), LayoutParams.WRAP_CONTENT, Gravity.CENTER));
        setVisibility(GONE);
    }

    boolean isOpen() {
        return getVisibility() == VISIBLE;
    }

    void show(String title, String subtitle, String[] items, IntConsumer onPick, Runnable onClose) {
        this.onClose = onClose;
        card.removeAllViews();
        Context c = getContext();

        TextView t = new TextView(c);
        t.setText(title);
        t.setTextColor(0xFFFFFFFF);
        t.setTextSize(21);
        t.setTypeface(Ui.font(700));
        t.setGravity(Gravity.CENTER);
        card.addView(t);
        if (subtitle != null) {
            TextView s = new TextView(c);
            s.setText(subtitle);
            s.setTextColor(0x99FFFFFF);
            s.setTextSize(13);
            s.setTypeface(Ui.font(450));
            s.setGravity(Gravity.CENTER);
            s.setPadding(0, Ui.dp(4), 0, 0);
            card.addView(s);
        }
        View spacer = new View(c);
        card.addView(spacer, new LinearLayout.LayoutParams(1, Ui.dp(14)));

        ColorStateList text = new ColorStateList(
                new int[][] {new int[] {android.R.attr.state_focused}, new int[] {}},
                new int[] {0xFF000000, 0xFFFFFFFF});
        for (int i = 0; i < items.length; i++) {
            final int idx = i;
            TextView row = new TextView(c);
            row.setText(items[i]);
            row.setTextSize(17);
            row.setTypeface(Ui.font(560));
            row.setTextColor(text);
            row.setGravity(Gravity.CENTER);
            row.setPadding(Ui.dp(18), Ui.dp(12), Ui.dp(18), Ui.dp(12));
            row.setFocusable(true);
            row.setClickable(true);
            StateListDrawable sl = new StateListDrawable();
            GradientDrawable on = new GradientDrawable();
            on.setCornerRadius(Ui.dp(14));
            on.setColor(0xFFFFFFFF);
            sl.addState(new int[] {android.R.attr.state_focused}, on);
            sl.addState(new int[] {}, new ColorDrawable(0));
            row.setBackground(sl);
            row.setOnFocusChangeListener((v, f) -> v.animate().scaleX(f ? 1.04f : 1f).scaleY(f ? 1.04f : 1f)
                    .setDuration(140).setInterpolator(AppTile.EASE).start());
            row.setOnClickListener(v -> {
                hide(false);
                onPick.accept(idx);
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.topMargin = Ui.dp(4);
            card.addView(row, lp);
        }

        setVisibility(VISIBLE);
        setAlpha(0f);
        card.setScaleX(0.94f);
        card.setScaleY(0.94f);
        animate().alpha(1f).setDuration(160).start();
        card.animate().scaleX(1f).scaleY(1f).setDuration(220).setInterpolator(AppTile.EASE).start();
        View first = card.getChildAt(subtitle != null ? 3 : 2);
        if (first != null) first.requestFocus();
    }

    /** @param notify run the close callback (used for Back; picks handle their own focus). */
    void hide(boolean notify) {
        if (!isOpen()) return;
        setVisibility(GONE);
        Runnable r = onClose;
        onClose = null;
        if (notify && r != null) r.run();
    }
}
