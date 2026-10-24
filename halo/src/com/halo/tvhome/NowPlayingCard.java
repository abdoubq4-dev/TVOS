package com.halo.tvhome;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.media.MediaMetadata;
import android.media.session.MediaController;
import android.media.session.MediaSessionManager;
import android.media.session.PlaybackState;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.InputStream;
import java.util.List;

/** Now Playing card on the Top Shelf: artwork, title, progress and transport controls. */
final class NowPlayingCard extends LinearLayout {
    interface Listener {
        void onNowPlayingShown(boolean shown);

        /** The current track's artwork changed (null when there is none or nothing plays). */
        void onArtwork(Bitmap art);
    }

    static final int ART = 150, COL = 400, CONTROLS_H = 32, CONTROLS_GAP = 12;

    private final MediaSessionManager msm;
    private final ComponentName listenerName;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Listener listener;

    private final ImageView art;
    private final TextView source, title, subtitle, elapsed, remaining, open;
    private final Progress bar;
    private final ImageView playIcon;
    private MediaController controller;
    private LinearLayout controls, info;
    /** The playhead time: hidden at rest, shown with the controls. */
    private FrameLayout timeRow;
    private Bitmap lastArt;
    private boolean controlsShown;
    private boolean started, shown;

    private final MediaController.Callback callback = new MediaController.Callback() {
        @Override public void onPlaybackStateChanged(PlaybackState s) { refresh(); }
        @Override public void onMetadataChanged(MediaMetadata m) { refresh(); }
        @Override public void onSessionDestroyed() { pick(null); }
    };
    private final MediaSessionManager.OnActiveSessionsChangedListener sessions = this::pick;
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            updateProgress();
            handler.postDelayed(this, 500);
        }
    };

    NowPlayingCard(Context c, Listener listener) {
        super(c);
        this.listener = listener;
        msm = c.getSystemService(MediaSessionManager.class);
        listenerName = new ComponentName(c, MediaListener.class);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setClipChildren(false);
        setClipToPadding(false);

        art = new ImageView(c);
        art.setScaleType(ImageView.ScaleType.CENTER_CROP);
        art.setOutlineProvider(new ViewOutlineProvider() {
            @Override
            public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), Ui.dp(12));
            }
        });
        art.setClipToOutline(true);
        art.setElevation(Ui.dp(10));
        art.setBackgroundColor(0xFF3A3A3C);
        art.setFocusable(true);
        art.setClickable(true);
        art.setOnClickListener(v -> openApp());
        art.setOnFocusChangeListener((v, f) -> v.animate().scaleX(f ? 1.08f : 1f).scaleY(f ? 1.08f : 1f)
                .translationZ(f ? Ui.dp(18) : 0).setDuration(200).setInterpolator(AppTile.EASE).start());
        addView(art, new LayoutParams(Ui.dp(ART), Ui.dp(ART)));

        // The text column is exactly as tall as the artwork, with its three groups
        // (labels, progress, controls) separated by equal flexible gaps.
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(VERTICAL);
        col.setClipChildren(false);
        col.setClipToPadding(false);
        LayoutParams clp = new LayoutParams(Ui.dp(COL), Ui.dp(ART));
        clp.leftMargin = Ui.dp(24);
        addView(col, clp);

        source = label(c, 10.5f, 650, 0xCCFFFFFF);
        source.setLetterSpacing(0.12f);
        // SF-style Now Playing type: semibold title with display tracking, regular secondary
        // line at 60%, small medium-weight times with tabular digits so they don't jiggle.
        title = label(c, 23, 690, 0xFFFFFFFF);
        title.setLetterSpacing(-0.015f);
        title.setShadowLayer(Ui.dp(8), 0, Ui.dp(1), 0x80000000);
        subtitle = label(c, 14.5f, 470, 0x99FFFFFF);
        subtitle.setLetterSpacing(-0.005f);
        // Apple-clean: no source label; the artwork itself is the "open app" button.
        // At rest the track details sit low, level with the bottom of the artwork; when the
        // card is focused they slide up and the transport controls fade in underneath.
        info = new LinearLayout(c);
        info.setOrientation(VERTICAL);
        info.setGravity(android.view.Gravity.BOTTOM);
        info.setClipChildren(false);
        col.addView(info, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));
        info.addView(title, margins(0, 0));
        info.addView(subtitle, margins(0, 5));

        bar = new Progress(c);
        info.addView(bar, new LayoutParams(Ui.dp(COL - 40), Ui.dp(17)) {{ topMargin = Ui.dp(10); }});
        FrameLayout times = timeRow = new FrameLayout(c);
        elapsed = label(c, 10, 590, 0x99FFFFFF);
        remaining = label(c, 10, 590, 0x99FFFFFF);
        elapsed.setFontFeatureSettings("'tnum'");
        remaining.setFontFeatureSettings("'tnum'");
        // Fixed-size labels: their twice-a-second updates then only redraw, instead of forcing a
        // layout pass over the whole screen (which made animations stutter).
        remaining.setGravity(Gravity.END);
        times.addView(elapsed, new FrameLayout.LayoutParams(Ui.dp(64), Ui.dp(14), Gravity.START));
        times.addView(remaining, new FrameLayout.LayoutParams(Ui.dp(64), Ui.dp(14), Gravity.END));
        // Only the elapsed time, riding under the playhead, as in tvOS 26.
        info.addView(times, new LayoutParams(Ui.dp(COL - 40), Ui.dp(15)) {{ topMargin = Ui.dp(3); }});
        remaining.setVisibility(GONE);
        elapsed.setGravity(Gravity.CENTER_HORIZONTAL);
        elapsed.setTextColor(0xD9FFFFFF);

        LinearLayout buttons = controls = new LinearLayout(c);
        buttons.setOrientation(HORIZONTAL);
        buttons.setGravity(Gravity.CENTER_VERTICAL);
        buttons.setClipChildren(false);
        col.addView(buttons, new LayoutParams(LayoutParams.WRAP_CONTENT, Ui.dp(CONTROLS_H)) {{ topMargin = Ui.dp(CONTROLS_GAP); }});
        buttons.addView(circle(c, R.drawable.ic_prev, v -> controls(0)));
        View play = circle(c, R.drawable.ic_pause, v -> controls(1));
        playIcon = (ImageView) ((FrameLayout) play).getChildAt(0);
        buttons.addView(play);
        buttons.addView(circle(c, R.drawable.ic_next, v -> controls(2)));
        open = label(c, 12, 600, 0);
        open.setTextColor(Glass.FG_DIM);
        open.setBackground(Glass.button(16, 0x29FFFFFF));
        open.setGravity(Gravity.CENTER);
        open.setPadding(Ui.dp(14), 0, Ui.dp(14), 0);
        open.setFocusable(true);
        open.setOnClickListener(v -> openApp());
        focusScale(open, 1.06f);
        LayoutParams olp = new LayoutParams(LayoutParams.WRAP_CONTENT, Ui.dp(32));
        open.setVisibility(GONE);
        buttons.addView(open, olp);

        // Transport controls stay hidden until focus moves onto the card, as on tvOS.
        controls.setAlpha(0f);
        timeRow.setAlpha(0f);
        info.setTranslationY(Ui.dp(CONTROLS_H + CONTROLS_GAP));
        getViewTreeObserver().addOnGlobalFocusChangeListener((o, n) -> showControls(n != null && contains(n)));
        setVisibility(GONE);
    }

    private boolean contains(View v) {
        for (Object p = v; p instanceof View; p = ((View) p).getParent()) if (p == this) return true;
        return false;
    }

    private static final long IDLE_HIDE_MS = 5000;
    private final Runnable idleHide = () -> showControls(false);

    /**
     * A key was pressed. While the card has focus this brings the controls back and restarts the
     * idle timer. Returns true when the press should only reveal hidden controls, so an OK press
     * can't trigger a button the viewer can't see.
     */
    boolean poke() {
        if (!hasFocus()) return false;
        boolean wasHidden = !controlsShown;
        showControls(true);
        View f = findFocus();
        return wasHidden && f != null && f != art && isInsideControls(f);
    }

    private boolean isInsideControls(View v) {
        for (Object p = v; p instanceof View; p = ((View) p).getParent()) if (p == controls) return true;
        return false;
    }

    /** Long, soft ease-out for the reveal; a touch quicker going away. */
    private static final android.view.animation.PathInterpolator SMOOTH =
            new android.view.animation.PathInterpolator(0.25f, 0.1f, 0.25f, 1f);

    private void showControls(boolean show) {
        handler.removeCallbacks(idleHide);
        if (show) handler.postDelayed(idleHide, IDLE_HIDE_MS);
        if (show == controlsShown) return;
        controlsShown = show;
        controls.animate().cancel();
        info.animate().cancel();
        info.animate().translationY(show ? 0 : Ui.dp(CONTROLS_H + CONTROLS_GAP)).setStartDelay(show ? 0 : 120)
                .setDuration(show ? 520 : 460).setInterpolator(SMOOTH).start();
        timeRow.animate().cancel();
        timeRow.animate().alpha(show ? 1f : 0f).setStartDelay(show ? 120 : 0)
                .setDuration(show ? 360 : 220).setInterpolator(SMOOTH).start();
        controls.animate().alpha(show ? 1f : 0f).translationY(show ? 0 : Ui.dp(10))
                .setStartDelay(show ? 160 : 0).setDuration(show ? 420 : 260).setInterpolator(SMOOTH).start();
    }

    private static TextView label(Context c, float sp, int weight, int color) {
        TextView t = new TextView(c);
        t.setTextSize(sp);
        t.setTypeface(Ui.font(weight));
        t.setTextColor(color);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        t.setIncludeFontPadding(false);
        return t;
    }

    private static LayoutParams margins(int left, int top) {
        LayoutParams lp = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        lp.leftMargin = Ui.dp(left);
        lp.topMargin = Ui.dp(top);
        return lp;
    }

    /**
     * Transport control: a plain white symbol at rest; focus lifts it onto a soft translucent
     * glass platter (rounded square, not a solid white disc) and the symbol grows slightly.
     */
    private View circle(Context c, int icon, OnClickListener click) {
        FrameLayout f = new FrameLayout(c);
        f.setBackground(Glass.focusPlatter(10));
        f.setFocusable(true);
        f.setOnClickListener(click);
        ImageView i = new ImageView(c);
        i.setImageResource(icon);
        i.setImageTintList(android.content.res.ColorStateList.valueOf(0xFFFFFFFF));
        i.setDuplicateParentStateEnabled(true);
        f.addView(i, new FrameLayout.LayoutParams(Ui.dp(18), Ui.dp(18), Gravity.CENTER));
        focusScale(f, 1.12f);
        LayoutParams lp = new LayoutParams(Ui.dp(32), Ui.dp(32));
        lp.rightMargin = Ui.dp(10);
        f.setLayoutParams(lp);
        return f;
    }

    /** Up from any part of the card goes to the top-right capsule, as on tvOS. */
    void setUpTarget(View target) {
        if (target.getId() == View.NO_ID) target.setId(View.generateViewId());
        art.setNextFocusUpId(target.getId());
        ViewParentWalker.apply(this, v -> { if (v.isFocusable()) v.setNextFocusUpId(target.getId()); });
    }

    /** Tiny helper to visit every view under a group. */
    interface ViewParentWalker {
        void visit(View v);
        static void apply(View root, ViewParentWalker w) {
            w.visit(root);
            if (root instanceof android.view.ViewGroup) {
                android.view.ViewGroup g = (android.view.ViewGroup) root;
                for (int i = 0; i < g.getChildCount(); i++) apply(g.getChildAt(i), w);
            }
        }
    }

    static void focusScale(View v, float s) {
        v.setOnFocusChangeListener((x, f) -> x.animate().scaleX(f ? s : 1f).scaleY(f ? s : 1f)
                .setDuration(160).setInterpolator(AppTile.EASE).start());
    }

    boolean hasMedia() {
        return shown;
    }

    CharSequence title() { return title.getText(); }
    CharSequence subtitle() { return subtitle.getText(); }
    CharSequence sourceApp() { return controller != null ? appLabel(controller.getPackageName()) : ""; }
    boolean isPlaying() {
        PlaybackState st = controller != null ? controller.getPlaybackState() : null;
        return st != null && st.getState() == PlaybackState.STATE_PLAYING;
    }
    void playPause() { controls(1); }
    String sourcePackage() { return controller != null ? controller.getPackageName() : null; }
    void stopPlayback() { if (controller != null) controller.getTransportControls().stop(); }
    void next() { controls(2); }
    void previous() { controls(0); }
    void open() { openApp(); }

    void start() {
        if (started) return;
        started = true;
        try {
            msm.addOnActiveSessionsChangedListener(sessions, listenerName, handler);
            pick(msm.getActiveSessions(listenerName));
        } catch (SecurityException e) {
            // Notification access not granted: no Now Playing, everything else still works.
            started = false;
        }
        handler.post(tick);
    }

    void stop() {
        handler.removeCallbacks(tick);
        if (!started) return;
        started = false;
        try {
            msm.removeOnActiveSessionsChangedListener(sessions);
        } catch (Exception ignored) {
        }
    }

    private void pick(List<MediaController> list) {
        MediaController best = null;
        if (list != null) {
            for (MediaController mc : list) {
                PlaybackState s = mc.getPlaybackState();
                if (s != null && s.getState() == PlaybackState.STATE_PLAYING) { best = mc; break; }
                if (best == null && mc.getMetadata() != null) best = mc;
            }
        }
        if (controller != null && (best == null || !best.getSessionToken().equals(controller.getSessionToken()))) {
            controller.unregisterCallback(callback);
        }
        if (best != null && (controller == null || !best.getSessionToken().equals(controller.getSessionToken()))) {
            best.registerCallback(callback, handler);
        }
        controller = best;
        refresh();
    }

    private void refresh() {
        MediaMetadata m = controller != null ? controller.getMetadata() : null;
        CharSequence t = m != null ? m.getText(MediaMetadata.METADATA_KEY_TITLE) : null;
        PlaybackState st = controller != null ? controller.getPlaybackState() : null;
        boolean active = st != null && st.getState() != PlaybackState.STATE_STOPPED
                && st.getState() != PlaybackState.STATE_NONE && st.getState() != PlaybackState.STATE_ERROR;
        boolean show = !TextUtils.isEmpty(t) && active;
        if (show) {
            String app = appLabel(controller.getPackageName());
            source.setText((app + "  ·  NOW PLAYING").toUpperCase());
            title.setText(t);
            CharSequence artist = m.getText(MediaMetadata.METADATA_KEY_ARTIST);
            CharSequence album = m.getText(MediaMetadata.METADATA_KEY_ALBUM);
            subtitle.setText(TextUtils.isEmpty(album) ? artist : artist + " · " + album);
            open.setText("Open in " + app);
            playIcon.setImageResource(st.getState() == PlaybackState.STATE_PLAYING
                    ? R.drawable.ic_pause : R.drawable.ic_play);
            setArt(m);
            updateProgress();
        }
        if (show != shown) {
            shown = show;
            if (show) {
                setVisibility(VISIBLE);
                setAlpha(0f);
                setTranslationY(Ui.dp(10));
                animate().alpha(1f).translationY(0).setDuration(320).setInterpolator(AppTile.EASE).start();
            } else {
                setVisibility(GONE);
            }
            if (!show && lastArt != null) {
                lastArt = null;
                listener.onArtwork(null);
            }
            listener.onNowPlayingShown(show);
        }
    }

    private void setArt(MediaMetadata m) {
        Bitmap b = m.getBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART);
        if (b == null) b = m.getBitmap(MediaMetadata.METADATA_KEY_ART);
        if (b == null) b = m.getBitmap(MediaMetadata.METADATA_KEY_DISPLAY_ICON);
        if (b == null) {
            String uri = m.getString(MediaMetadata.METADATA_KEY_ALBUM_ART_URI);
            if (uri == null) uri = m.getString(MediaMetadata.METADATA_KEY_ART_URI);
            if (uri != null && (uri.startsWith("content:") || uri.startsWith("android.resource:"))) {
                try (InputStream in = getContext().getContentResolver().openInputStream(Uri.parse(uri))) {
                    b = BitmapFactory.decodeStream(in);
                } catch (Exception ignored) {
                }
            }
        }
        // Always pass it on: players may reuse one Bitmap object across tracks, so identity
        // says nothing. The home screen compares the actual pictures.
        lastArt = b;
        listener.onArtwork(b);
        if (b != null) {
            art.setImageBitmap(b);
        } else {
            try {
                Drawable d = getContext().getPackageManager().getApplicationIcon(controller.getPackageName());
                art.setImageDrawable(d);
            } catch (PackageManager.NameNotFoundException e) {
                art.setImageDrawable(null);
            }
        }
    }

    private void updateProgress() {
        if (!shown || controller == null) return;
        PlaybackState st = controller.getPlaybackState();
        MediaMetadata m = controller.getMetadata();
        if (st == null || m == null) return;
        long dur = m.getLong(MediaMetadata.METADATA_KEY_DURATION);
        long pos = st.getPosition();
        if (st.getState() == PlaybackState.STATE_PLAYING && st.getLastPositionUpdateTime() > 0) {
            pos += (long) ((SystemClock.elapsedRealtime() - st.getLastPositionUpdateTime()) * st.getPlaybackSpeed());
        }
        if (dur > 0) pos = Math.max(0, Math.min(pos, dur));
        bar.setFraction(dur > 0 ? pos / (float) dur : 0f);
        elapsed.setText(time(pos));
        float lw = elapsed.getWidth() > 0 ? elapsed.getWidth() : Ui.dp(64);
        float tx = bar.playheadX() - lw / 2;
        elapsed.setTranslationX(Math.max(0, Math.min(tx, bar.getWidth() - lw)));
    }

    private static String time(long ms) {
        long s = ms / 1000;
        return s >= 3600 ? String.format("%d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60)
                : String.format("%d:%02d", s / 60, s % 60);
    }

    private void controls(int which) {
        if (controller == null) return;
        MediaController.TransportControls tc = controller.getTransportControls();
        PlaybackState st = controller.getPlaybackState();
        if (which == 0) tc.skipToPrevious();
        else if (which == 2) tc.skipToNext();
        else if (st != null && st.getState() == PlaybackState.STATE_PLAYING) tc.pause();
        else tc.play();
    }

    private void openApp() {
        if (controller == null) return;
        PackageManager pm = getContext().getPackageManager();
        String pkg = controller.getPackageName();
        Intent i = pm.getLeanbackLaunchIntentForPackage(pkg);
        if (i == null) i = pm.getLaunchIntentForPackage(pkg);
        try {
            if (i != null) {
                getContext().startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } else if (controller.getSessionActivity() != null) {
                controller.getSessionActivity().send();
            }
        } catch (Exception ignored) {
        }
    }

    private String appLabel(String pkg) {
        try {
            PackageManager pm = getContext().getPackageManager();
            return String.valueOf(pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)));
        } catch (Exception e) {
            return "Music";
        }
    }

    /** Thin rounded progress track. */
    /**
     * tvOS 26 scrubber: a thin glass track (dark tint, hairline rim, lighter played part) with a
     * short white playhead line standing proud of it.
     */
    private static final class Progress extends View {
        private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint played = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint rim = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint head = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();
        private float fraction;

        Progress(Context c) {
            super(c);
            track.setColor(0x47000000);
            played.setColor(0x47FFFFFF);
            rim.setStyle(Paint.Style.STROKE);
            rim.setStrokeWidth(Math.max(1, Ui.dp(0.5f)));
            rim.setColor(0x40FFFFFF);
            head.setColor(0xFFFFFFFF);
            head.setShadowLayer(Ui.dp(3), 0, 0, 0x66000000);
            setLayerType(LAYER_TYPE_SOFTWARE, null);  // the playhead's soft shadow
        }

        void setFraction(float f) {
            fraction = f;
            invalidate();
        }

        float playheadX() {
            float w = getWidth(), pad = Ui.dp(1.5f);
            return pad + (w - 2 * pad) * fraction;
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), th = Ui.dp(7), top = (h - th) / 2, rad = th / 2;
            r.set(0, top, w, top + th);
            c.drawRoundRect(r, rad, rad, track);
            float x = playheadX();
            r.set(0, top, Math.max(th, x), top + th);
            c.drawRoundRect(r, rad, rad, played);
            r.set(0, top, w, top + th);
            r.inset(rim.getStrokeWidth() / 2, rim.getStrokeWidth() / 2);
            c.drawRoundRect(r, rad, rad, rim);
            float hw = Ui.dp(2.5f);
            r.set(x - hw / 2, 0, x + hw / 2, h);
            c.drawRoundRect(r, hw / 2, hw / 2, head);
        }
    }
}
