package com.halo.tvhome;

import android.animation.ValueAnimator;
import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * The tvOS 26 top-right capsule. At rest it is just the time and your avatar; moving up into it
 * slides open the Home and Control Center buttons, each of which drops down its panel.
 */
final class ControlBar extends LinearLayout {
    static final int HOME = 0, AUDIO = 1, HEADPHONES = 2, CONTROLS = 3, PROFILE = 4;
    static final int HEIGHT = 30;
    private static final int BTN = 24, BTN_GAP = 4;

    interface Host {
        void openPanel(int which, View anchor);
    }

    private final TextView time;
    private final TextView avatar;
    private final LinearLayout icons;

    private boolean expanded;
    private ValueAnimator anim;
    final View[] buttons = new View[5];

    ControlBar(Context c, Host host) {
        super(c);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setPadding(Ui.dp(12), Ui.dp(3), Ui.dp(3), Ui.dp(3));
        setClipChildren(false);
        setClipToPadding(false);

        time = new TextView(c);
        time.setTextSize(12.5f);
        time.setTypeface(Ui.font(640));
        time.setTextColor(0xF2FFFFFF);
        time.setIncludeFontPadding(false);
        LayoutParams tlp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        tlp.rightMargin = Ui.dp(6);
        addView(time, tlp);

        icons = new LinearLayout(c);
        icons.setOrientation(HORIZONTAL);
        icons.setGravity(Gravity.CENTER_VERTICAL);
        icons.setClipChildren(false);
        icons.setAlpha(0f);
        icons.setDescendantFocusability(FOCUS_BLOCK_DESCENDANTS);
        addView(icons, new LayoutParams(0, Ui.dp(BTN)));
        // As in tvOS 26: Home and Control Center always; audio and headphones only while relevant.
        buttons[AUDIO] = icon(c, R.drawable.ic_wave, AUDIO, host);
        buttons[HEADPHONES] = icon(c, R.drawable.ic_headphones, HEADPHONES, host);
        buttons[HOME] = icon(c, R.drawable.ic_home, HOME, host);
        buttons[CONTROLS] = icon(c, R.drawable.ic_toggle, CONTROLS, host);
        buttons[AUDIO].setVisibility(GONE);
        buttons[HEADPHONES].setVisibility(GONE);

        avatar = new TextView(c);
        avatar.setGravity(Gravity.CENTER);
        avatar.setTextSize(11);
        avatar.setTypeface(Ui.font(700));
        avatar.setTextColor(0xFFFFFFFF);
        avatar.setBackground(Profile.avatar(c));
        FrameLayout ring = new FrameLayout(c);
        ring.setBackground(Glass.button(BTN / 2f, 0x00000000));
        ring.setPadding(Ui.dp(1.5f), Ui.dp(1.5f), Ui.dp(1.5f), Ui.dp(1.5f));
        ring.addView(avatar, new FrameLayout.LayoutParams(-1, -1));
        ring.setFocusable(true);
        ring.setOnClickListener(x -> host.openPanel(PROFILE, x));
        NowPlayingCard.focusScale(ring, 1.12f);
        addView(ring, new LayoutParams(Ui.dp(BTN), Ui.dp(BTN)));
        buttons[PROFILE] = ring;
    }

    private View icon(Context c, int res, int which, Host host) {
        FrameLayout f = new FrameLayout(c);
        f.setBackground(Glass.row(BTN / 2f));
        ImageView i = new ImageView(c);
        i.setImageResource(res);
        i.setImageTintList(Glass.FG_DIM);
        i.setDuplicateParentStateEnabled(true);
        f.addView(i, new FrameLayout.LayoutParams(Ui.dp(13), Ui.dp(13), Gravity.CENTER));
        f.setFocusable(true);
        f.setOnClickListener(x -> host.openPanel(which, x));
        NowPlayingCard.focusScale(f, 1.12f);
        LayoutParams lp = new LayoutParams(Ui.dp(BTN), Ui.dp(BTN));
        lp.rightMargin = Ui.dp(BTN_GAP);
        icons.addView(f, lp);
        return f;
    }

    /** Slide the Control Center buttons open (focus entered the capsule) or closed. */
    void setExpanded(boolean e) {
        if (e == expanded) return;
        expanded = e;
        icons.setDescendantFocusability(e ? FOCUS_AFTER_DESCENDANTS : FOCUS_BLOCK_DESCENDANTS);
        if (anim != null) anim.cancel();
        final ViewGroup.LayoutParams lp = icons.getLayoutParams();
        anim = ValueAnimator.ofInt(lp.width, e ? iconsWidth() : 0);
        anim.setDuration(e ? 280 : 200);
        anim.setInterpolator(AppTile.EASE);
        anim.addUpdateListener(a -> {
            lp.width = (int) a.getAnimatedValue();
            icons.setLayoutParams(lp);
        });
        anim.start();
        icons.animate().alpha(e ? 1f : 0f).setStartDelay(e ? 80 : 0).setDuration(e ? 220 : 120).start();
    }

    private int iconsWidth() {
        int n = 0;
        for (int i = 0; i < icons.getChildCount(); i++) if (icons.getChildAt(i).getVisibility() == VISIBLE) n++;
        return Ui.dp(n * (BTN + BTN_GAP));
    }

    /** Show the audio button while something plays and the headphones button while connected. */
    void setContext(boolean playing, boolean headphones) {
        boolean changed = (buttons[AUDIO].getVisibility() == VISIBLE) != playing
                || (buttons[HEADPHONES].getVisibility() == VISIBLE) != headphones;
        if (!changed) return;
        buttons[AUDIO].setVisibility(playing ? VISIBLE : GONE);
        buttons[HEADPHONES].setVisibility(headphones ? VISIBLE : GONE);
        if (expanded) {
            ViewGroup.LayoutParams lp = icons.getLayoutParams();
            lp.width = iconsWidth();
            icons.setLayoutParams(lp);
        }
    }

    void setTime(CharSequence t) {
        time.setText(t);
    }

    void setInitial(String s) {
        avatar.setText(Profile.hasPhoto(getContext()) ? "" : s);
        avatar.setBackground(Profile.avatar(getContext()));
    }
}
