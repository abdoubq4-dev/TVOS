package com.halo.tvhome;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.Outline;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/**
 * Apple TV app sidebar, measured from tvOS and scaled to this 960x540dp screen: a 169dp light
 * frosted panel (a real blur of what is behind it under a pale tint), 39dp rows with 24dp icon
 * circles, 12.5sp labels, and a white highlight on the focused row.
 */
final class Sidebar extends FrameLayout {
    static final int MARGIN = 18, WIDTH = 178, RADIUS = 24;
    private static final int ROW_H = 35, ROW_GAP = 4, INSET = 6, ROW_PAD = 7, AVATAR = 26;
    /** Highlight corners stay concentric with the panel: panel radius minus the inset. */
    private static final float HIGHLIGHT_RADIUS = RADIUS - INSET - 3;
    private static final ColorStateList LABEL = states(0xFF000000, 0xD93A3A3C);
    private static final ColorStateList ICON = states(0xFF000000, 0xE63A3A3C);
    private static final ColorStateList BUBBLE = states(0x1A000000, 0x40FFFFFF);

    interface Host {
        void onSidebarClosed();
        void showHomeScreenSettings();
        void launchApp(AppEntry e);
    }

    private final Host host;
    private final LinearLayout list;
    private final FrameLayout panel;
    private final ScrollView scroller;
    private View homeRow;

    Sidebar(Context c, Host host) {
        super(c);
        this.host = host;
        setClickable(true);

        panel = new FrameLayout(c);
        panel.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), Ui.dp(RADIUS));
            }
        });
        // No outline clip: the frost draws its own rounded shape, and a clip would cut the
        // focused row's shadow.
        panel.setElevation(Ui.dp(18));
        panel.setClipChildren(false);
        LayoutParams plp = new LayoutParams(Ui.dp(WIDTH), -1);
        plp.setMargins(Ui.dp(MARGIN), Ui.dp(MARGIN), 0, Ui.dp(MARGIN));
        addView(panel, plp);

        ScrollView sv = scroller = new ScrollView(c);
        sv.setVerticalScrollBarEnabled(false);
        sv.setClipToPadding(false);
        sv.setClipChildren(false);
        // The avatar sits as far from the panel's top edge as from its left edge (inset + row
        // padding), the way tvOS aligns the profile with the row icons below it.
        sv.setPadding(Ui.dp(INSET), Ui.dp(INSET + ROW_PAD), Ui.dp(INSET), Ui.dp(14));
        panel.addView(sv, new LayoutParams(-1, -1));
        list = new LinearLayout(c);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setClipChildren(false);
        sv.addView(list, new ScrollView.LayoutParams(-1, -2));
        setVisibility(GONE);
    }

    private static ColorStateList states(int focused, int idle) {
        return new ColorStateList(new int[][] {new int[] {android.R.attr.state_focused}, new int[] {}},
                new int[] {focused, idle});
    }

    boolean isOpen() {
        return getVisibility() == VISIBLE;
    }

    /** @param behind a small, already blurred snapshot of the whole screen, or null. */
    void open(String name, CharSequence clock, List<AppEntry> apps, Bitmap behind) {
        Context c = getContext();
        panel.setBackground(frost(behind));
        list.removeAllViews();

        LinearLayout head = new LinearLayout(c);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setPadding(Ui.dp(ROW_PAD), 0, Ui.dp(10), 0);
        TextView av = text(11, 600);
        av.setTextColor(0xFFFFFFFF);
        av.setGravity(Gravity.CENTER);
        if (!Profile.hasPhoto(c)) av.setText(name.substring(0, 1).toUpperCase());
        av.setBackground(Profile.avatar(c));
        head.addView(av, new LinearLayout.LayoutParams(Ui.dp(AVATAR), Ui.dp(AVATAR)));
        TextView nm = text(12, 640);
        nm.setTextColor(0xE6303033);
        nm.setText(name.split(" ")[0]);
        LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(0, -2, 1f);
        nl.leftMargin = Ui.dp(10);
        head.addView(nm, nl);
        list.addView(head, new LinearLayout.LayoutParams(-1, Ui.dp(AVATAR)));
        spacer(24);

        row(R.drawable.ic_search, "Search", () -> { close(); Actions.search(c); });
        homeRow = row(R.drawable.ic_home, "Home", this::close);
        row(R.drawable.ic_photo, "Art Mode", () -> { close(); Actions.start(c, new android.content.Intent(c, ArtModeActivity.class)); });
        row(R.drawable.ic_wifi, "Network", () -> { close(); Actions.wifi(c); });
        row(R.drawable.ic_bluetooth, "Remotes", () -> { close(); Actions.bluetooth(c); });
        row(R.drawable.ic_tv, "Display & Sound", () -> { close(); Actions.display(c); });
        row(R.drawable.ic_moon, "Screensaver", () -> { close(); Actions.screensaverSettings(c); });
        row(R.drawable.ic_grid, "Home Screen", () -> { close(); host.showHomeScreenSettings(); });
        row(R.drawable.ic_gear, "Settings", () -> { close(); Actions.settings(c); });

        TextView sec = text(12, 640);
        sec.setTextColor(0x80303033);
        sec.setText("Channels & Apps");
        sec.setPadding(Ui.dp(INSET + 1), Ui.dp(18), 0, Ui.dp(8));
        list.addView(sec);
        int n = Math.min(apps.size(), 6);
        for (int i = 0; i < n; i++) {
            AppEntry e = apps.get(i);
            appRow(e, () -> { close(); host.launchApp(e); });
        }

        scroller.scrollTo(0, 0);
        setVisibility(VISIBLE);
        panel.setAlpha(0f);
        panel.setTranslationX(-Ui.dp(28));
        panel.animate().alpha(1f).translationX(0).setDuration(380).setInterpolator(Spring.PANEL).start();
        homeRow.requestFocus();
    }

    /** Light vibrant material over a live blur of what is behind the panel. */
    private Drawable frost(Bitmap behind) {
        return new FrostDrawable(panel, behind, Ui.dp(RADIUS), 0xA6DCDCE0, 0x8CB4B4BA, 0x66FFFFFF);
    }

    void close() {
        if (!isOpen()) return;
        panel.animate().alpha(0f).translationX(-Ui.dp(20)).setDuration(180).setInterpolator(AppTile.EASE)
                .withEndAction(() -> setVisibility(GONE)).start();
        host.onSidebarClosed();
    }

    private int firstRowIndex() {
        for (int i = 0; i < list.getChildCount(); i++) if (list.getChildAt(i).isFocusable()) return i;
        return 0;
    }

    private void spacer(int dp) {
        list.addView(new View(getContext()), new LinearLayout.LayoutParams(1, Ui.dp(dp)));
    }

    private View row(int icon, String label, Runnable click) {
        Context c = getContext();
        LinearLayout r = base();
        FrameLayout bubble = new FrameLayout(c);
        GradientDrawable g = new GradientDrawable();
        g.setShape(GradientDrawable.OVAL);
        g.setColor(BUBBLE);
        bubble.setBackground(g);
        bubble.setDuplicateParentStateEnabled(true);
        ImageView iv = new ImageView(c);
        iv.setImageResource(icon);
        iv.setImageTintList(ICON);
        iv.setDuplicateParentStateEnabled(true);
        bubble.addView(iv, new LayoutParams(Ui.dp(12), Ui.dp(12), Gravity.CENTER));
        r.addView(bubble, new LinearLayout.LayoutParams(Ui.dp(24), Ui.dp(24)));
        finish(r, label, click);
        return r;
    }

    private void appRow(AppEntry e, Runnable click) {
        Context c = getContext();
        LinearLayout r = base();
        ImageView iv = new ImageView(c);
        try {
            iv.setImageDrawable(c.getPackageManager().getApplicationIcon(e.pkg));
        } catch (Exception ex) {
            iv.setImageBitmap(e.art);
        }
        iv.setScaleType(ImageView.ScaleType.CENTER_CROP);
        iv.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline o) {
                o.setOval(0, 0, v.getWidth(), v.getHeight());
            }
        });
        iv.setClipToOutline(true);
        r.addView(iv, new LinearLayout.LayoutParams(Ui.dp(24), Ui.dp(24)));
        finish(r, e.label, click);
    }

    private LinearLayout base() {
        LinearLayout r = new LinearLayout(getContext());
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(Ui.dp(ROW_PAD), 0, Ui.dp(10), 0);
        r.setBackground(Glass.softRow(HIGHLIGHT_RADIUS));
        r.setFocusable(true);
        r.setOnFocusChangeListener((v, f) -> {
            v.animate().translationZ(f ? Ui.dp(2) : 0).setDuration(180).setInterpolator(AppTile.EASE).start();
            // The header above the first row can't take focus, so scroll it back into view
            // ourselves when the top row is reached; otherwise the list stops short of the top.
            if (f && list.indexOfChild(v) <= firstRowIndex()) scroller.post(() -> scroller.smoothScrollTo(0, 0));
        });
        return r;
    }

    private void finish(LinearLayout r, String label, Runnable click) {
        TextView t = text(12.5f, 640);
        t.setTextColor(LABEL);
        t.setDuplicateParentStateEnabled(true);
        t.setText(label);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0, -2, 1f);
        tl.leftMargin = Ui.dp(10);
        r.addView(t, tl);
        r.setOnClickListener(v -> click.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, Ui.dp(ROW_H));
        lp.bottomMargin = Ui.dp(ROW_GAP);
        list.addView(r, lp);
    }

    private TextView text(float sp, int weight) {
        TextView t = new TextView(getContext());
        t.setTextSize(sp);
        t.setTypeface(Ui.font(weight));
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setIncludeFontPadding(false);
        return t;
    }
}
