package com.halo.tvhome;

import android.app.Activity;
import android.bluetooth.BluetoothClass;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioManager;
import android.net.ConnectivityManager;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.provider.Settings;
import android.text.format.DateFormat;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntConsumer;

public class HomeActivity extends Activity
        implements ControlBar.Host, Sidebar.Host, NowPlayingCard.Listener {
    private SharedPreferences prefs;
    private AppRepo repo;
    private final List<AppEntry> apps = new ArrayList<>();
    private final Map<String, AppTile> tiles = new HashMap<>();

    private FrameLayout root, content;
    /** Sharp-ish wallpaper underneath; the frosted, dimmed copy on top fades in over the grid. */
    private ImageView bgSharp, bgFrost;
    /** Now-playing artwork backdrop, between the wallpaper and the frost. */
    private ImageView bgArt;
    private Bitmap wallpaperDock;
    private String artKey;
    private final java.util.concurrent.ExecutorService worker =
            java.util.concurrent.Executors.newSingleThreadExecutor();
    private TextView moveHint;
    private GridPanel grid;
    private NowPlayingCard nowPlaying;
    private ControlBar controlBar;
    private LinearLayout weather;
    private ImageView weatherIcon;
    private TextView weatherTemp;
    private final android.os.Handler ui = new android.os.Handler(android.os.Looper.getMainLooper());
    private final Runnable weatherTick = new Runnable() {
        @Override
        public void run() {
            refreshWeather();
            ui.postDelayed(this, 30 * 60 * 1000);
        }
    };
    private Dropdown dropdown;
    private Sidebar sidebar;
    private MenuOverlay menu;
    private AppMenu appMenu;
    private WeatherPanel weatherPanel;
    /** Package last opened from Halo: Android protects it as the "previous" app. */
    private String lastLaunched;

    private Bitmap wallpaper, wallpaperSoft;
    private int currentRow = -1;
    private AppTile focused, moving;
    private View sidebarReturn;
    private boolean dirty;

    private final BroadcastReceiver packages = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            Uri d = i.getData();
            if (d != null) repo.forget(d.getSchemeSpecificPart());
            if (hasWindowFocus()) reload(); else dirty = true;
        }
    };
    private final BroadcastReceiver ticks = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            updateClock();
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Ui.init(this);
        prefs = getSharedPreferences("halo", MODE_PRIVATE);
        repo = new AppRepo(this, prefs, Ui.dp(GridPanel.TILE_W * GridPanel.FOCUS_SCALE), Ui.dp(GridPanel.TILE_H * GridPanel.FOCUS_SCALE));
        buildViews();
        takeWallpaperExtra(getIntent());
        applyWallpaper(prefs.getString("wallpaper", "Coast"));
        reload();

        IntentFilter pf = new IntentFilter();
        pf.addAction(Intent.ACTION_PACKAGE_ADDED);
        pf.addAction(Intent.ACTION_PACKAGE_REMOVED);
        pf.addAction(Intent.ACTION_PACKAGE_CHANGED);
        pf.addAction(Intent.ACTION_PACKAGE_REPLACED);
        pf.addDataScheme("package");
        registerReceiver(packages, pf);
        IntentFilter tf = new IntentFilter(Intent.ACTION_TIME_TICK);
        tf.addAction(Intent.ACTION_TIME_CHANGED);
        tf.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        registerReceiver(ticks, tf);
    }

    @Override
    protected void onDestroy() {
        unregisterReceiver(packages);
        unregisterReceiver(ticks);
        super.onDestroy();
    }

    @Override
    protected void onStart() {
        super.onStart();
        nowPlaying.start();
        ui.post(weatherTick);
        scheduleRotation();
    }

    @Override
    protected void onStop() {
        ui.removeCallbacks(weatherTick);
        ui.removeCallbacks(rotateTick);
        nowPlaying.stop();
        super.onStop();
    }

    @Override
    protected void onResume() {
        super.onResume();
        updateClock();
        controlBar.setInitial(profileName().substring(0, 1).toUpperCase());
        if (dirty) reload();
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (takeWallpaperExtra(intent)) applyWallpaper(prefs.getString("wallpaper", "Coast"));
        // Home pressed while already home: close everything and go back to the top, like tvOS.
        menu.hide(false);
        appMenu.hide();
        weatherPanel.hide();
        dropdown.hide();
        sidebar.close();
        if (moving != null) endMove();
        setMainFocus(true);
        focusFirst();
    }

    // ---- views ---------------------------------------------------------------------------

    private void buildViews() {
        root = new FrameLayout(this);
        getWindow().setBackgroundDrawable(null);  // the opaque wallpaper covers everything
        root.setClipChildren(false);

        bgSharp = backdrop();
        bgArt = backdrop();
        bgArt.setAlpha(0f);
        bgFrost = backdrop();
        bgFrost.setColorFilter(new android.graphics.PorterDuffColorFilter(0x59000000,
                android.graphics.PorterDuff.Mode.SRC_ATOP));
        bgFrost.setAlpha(0f);


        content = new FrameLayout(this);
        content.setClipChildren(false);
        content.setClipToPadding(false);
        root.addView(content, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        nowPlaying = new NowPlayingCard(this, this);
        FrameLayout.LayoutParams nlp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        // Artwork lines up with the first dock icon, and sits the dock's own 19dp inner gap above it.
        float screenDp = getResources().getDisplayMetrics().widthPixels / Ui.density;
        nlp.leftMargin = Ui.dp(GridPanel.firstIconLeftDp(screenDp));
        nlp.topMargin = Ui.dp(GridPanel.DOCK_TOP_DP - 19 - NowPlayingCard.ART);
        content.addView(nowPlaying, nlp);

        grid = new GridPanel(this);
        content.addView(grid, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));

        controlBar = new ControlBar(this, this);
        controlBar.setInitial(profileName().substring(0, 1).toUpperCase());
        FrameLayout.LayoutParams clp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.END);
        clp.topMargin = Ui.dp(28);
        clp.rightMargin = Ui.dp(28);
        root.addView(controlBar, clp);
        nowPlaying.setUpTarget(controlBar.buttons[ControlBar.PROFILE]);

        // Weather, top left, level with the capsule: icon, temperature, conditions.
        // A glass pill matching the top-right capsule: just the conditions icon and temperature.
        weather = new LinearLayout(this);
        weather.setGravity(Gravity.CENTER_VERTICAL);
        weather.setPadding(Ui.dp(11), 0, Ui.dp(13), 0);
        weatherIcon = new ImageView(this);
        weatherIcon.setImageTintList(android.content.res.ColorStateList.valueOf(0xF2FFFFFF));
        weather.addView(weatherIcon, new LinearLayout.LayoutParams(Ui.dp(14), Ui.dp(14)));
        weatherTemp = new TextView(this);
        weatherTemp.setTextSize(15);
        weatherTemp.setTypeface(Ui.font(640));
        weatherTemp.setTextColor(0xF2FFFFFF);
        weatherTemp.setTextSize(12.5f);
        weatherTemp.setIncludeFontPadding(false);
        LinearLayout.LayoutParams wt = new LinearLayout.LayoutParams(-2, -2);
        wt.leftMargin = Ui.dp(6);
        weather.addView(weatherTemp, wt);
        weather.setVisibility(View.INVISIBLE);
        // Selectable: lifts with a lit-glass highlight; OK opens the forecast.
        weather.setFocusable(true);
        android.graphics.drawable.StateListDrawable wf = new android.graphics.drawable.StateListDrawable();
        wf.addState(new int[] {android.R.attr.state_focused}, Glass.glassLit(ControlBar.HEIGHT / 2f));
        wf.setEnterFadeDuration(120);
        wf.setExitFadeDuration(120);
        weather.setForeground(wf);
        weather.setOnFocusChangeListener((v, f) -> v.animate().scaleX(f ? 1.08f : 1f).scaleY(f ? 1.08f : 1f)
                .setDuration(300).setInterpolator(Spring.FOCUS).start());
        weather.setOnClickListener(v -> openWeather());
        FrameLayout.LayoutParams wlp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                Ui.dp(ControlBar.HEIGHT), Gravity.TOP | Gravity.START);
        wlp.topMargin = Ui.dp(28);
        wlp.leftMargin = Ui.dp(28);
        root.addView(weather, wlp);
        showWeather(Weather.cached(this));

        moveHint = new TextView(this);
        moveHint.setTextSize(15);
        moveHint.setTypeface(Ui.font(560));
        moveHint.setTextColor(0xFFFFFFFF);
        moveHint.setText("Use arrows to move  ·  Press OK when done");
        moveHint.setBackground(Glass.panel(20, 0xB32C2C2E));
        moveHint.setPadding(Ui.dp(20), Ui.dp(10), Ui.dp(20), Ui.dp(10));
        moveHint.setVisibility(View.GONE);
        FrameLayout.LayoutParams mlp = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
        mlp.bottomMargin = Ui.dp(28);
        root.addView(moveHint, mlp);

        dropdown = new Dropdown(this);
        root.addView(dropdown, match());
        sidebar = new Sidebar(this, this);
        root.addView(sidebar, match());
        menu = new MenuOverlay(this);
        root.addView(menu, match());
        appMenu = new AppMenu(this);
        root.addView(appMenu, match());
        weatherPanel = new WeatherPanel(this);
        root.addView(weatherPanel, match());

        root.getViewTreeObserver().addOnGlobalFocusChangeListener((oldV, newV) -> {
            boolean inBar = newV != null && (isInside(newV, controlBar) || dropdown.isOpen());
            if (inBar) {
                refreshCapsule();
            }
            controlBar.setExpanded(inBar);
            if (newV instanceof AppTile) onTileFocused((AppTile) newV);
            else if (newV != null && (isInside(newV, nowPlaying) || isInside(newV, controlBar))) setRow(0);
        });
        setContentView(root);
    }

    private static boolean isInside(View v, View parent) {
        for (Object p = v; p instanceof View; p = ((View) p).getParent()) if (p == parent) return true;
        return false;
    }

    private ImageView backdrop() {
        ImageView v = new ImageView(this);
        v.setScaleType(ImageView.ScaleType.CENTER_CROP);
        root.addView(v, match());
        return v;
    }

    private static FrameLayout.LayoutParams match() {
        return new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
    }

    /**
     * One glass for the dock and both top pills: the same frosted backdrop, tint and sheen,
     * with no outline on the pills.
     */
    private void setGlass(Bitmap frost) {
        grid.dock.setFrost(frost);
        // The top pills are Apple's *clear* glass: the backdrop shows through almost unchanged
        // (a whisper of darkening), with a bright rim catching the light.
        Bitmap sharp = wallpaper;
        if (artKey != null && bgArt.getDrawable() instanceof android.graphics.drawable.BitmapDrawable) {
            sharp = ((android.graphics.drawable.BitmapDrawable) bgArt.getDrawable()).getBitmap();
        }
        float r = Ui.dp(ControlBar.HEIGHT / 2f);
        int bgAlpha = controlBar.getBackground() != null ? controlBar.getBackground().getAlpha() : 255;
        weather.setBackground(new FrostDrawable(weather, sharp, r, 0x1A000000, 0x26000000, 0x8CFFFFFF));
        controlBar.setBackground(new FrostDrawable(controlBar, sharp, r, 0x1A000000, 0x26000000, 0x8CFFFFFF));
        controlBar.getBackground().setAlpha(bgAlpha);
    }

    private void refreshWeather() {
        if (Weather.fresh(this) && Weather.cached(this) != null && Weather.cachedFull(this) != null) {
            showWeather(Weather.cached(this));
            return;
        }
        worker.execute(() -> {
            Weather.Now n = Weather.fetch(this);
            if (n != null) ui.post(() -> showWeather(n));
        });
    }

    /** Left from the first capsule button reaches the weather pill, and right comes back. */
    private void linkWeather() {
        if (weather.getId() == View.NO_ID) weather.setId(View.generateViewId());
        View first = null;
        for (View b : controlBar.buttons) {
            if (b == null) continue;
            if (b.getId() == View.NO_ID) b.setId(View.generateViewId());
            if (b.getVisibility() == View.VISIBLE && b.isShown() && (first == null || b.getLeft() + ((View) b.getParent()).getLeft() < first.getLeft() + ((View) first.getParent()).getLeft())) first = b;
        }
        if (first == null) return;
        first.setNextFocusLeftId(weather.getId());
        weather.setNextFocusRightId(first.getId());
    }

    private void openWeather() {
        org.json.JSONObject f = Weather.cachedFull(this);
        if (f == null) {
            Toast.makeText(this, "Loading weather…", Toast.LENGTH_SHORT).show();
            refreshWeather();
            return;
        }
        setMainFocus(false);
        weatherPanel.show(f, Weather.city(this), snapshotBlurred(), () -> {
            setMainFocus(true);
            weather.requestFocus();
        });
    }

    private void showWeather(Weather.Now n) {
        if (n == null) return;
        weatherIcon.setImageResource(n.icon());
        weatherTemp.setText(n.temp + "°");
        if (weather.getVisibility() != View.VISIBLE) {
            weather.setAlpha(0f);
            weather.setVisibility(View.VISIBLE);
            weather.animate().alpha(currentRow > 0 ? 0f : 1f).setDuration(400).start();
        }
    }

    private String clockText() {
        return String.valueOf(DateFormat.format(DateFormat.is24HourFormat(this) ? "H:mm" : "h:mm a", new Date()));
    }

    private void updateClock() {
        controlBar.setTime(clockText());
    }

    private String profileName() {
        return prefs.getString("profile_name", "Areen Yapa");
    }

    private String deviceName() {
        String n = Settings.Global.getString(getContentResolver(), Settings.Global.DEVICE_NAME);
        return n == null || n.trim().isEmpty() ? "Home" : n.trim();
    }

    /** Lets set-wallpaper.sh pick the background: am start ... --es wallpaper "Photo: x.jpg". */
    private boolean takeWallpaperExtra(Intent i) {
        String w = i != null ? i.getStringExtra("wallpaper") : null;
        if (w == null) return false;
        prefs.edit().putString("wallpaper", w).apply();
        return true;
    }

    private void applyWallpaper(String name) {
        Bitmap[] set = buildWallpaper(name);
        showWallpaper(set, false);
    }

    /**
     * Builds [home, scrolled-grid frost, dock frost], each pre-scaled once to the exact screen
     * size (stretching a small bitmap every frame costs this GPU ~80ms). "Clear" style keeps the
     * home backdrop sharp, so the glass on top has real detail to bend; "Soft" blurs it.
     */
    private Bitmap[] buildWallpaper(String name) {
        Bitmap full = Scenery.load(this, name);
        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        Bitmap soft = Art.toScreen(Art.blur(full), dm);
        Bitmap dock = Art.toScreen(Art.dockBlur(full), dm);
        Bitmap home = clearStyle() ? Art.scrim(Art.toScreen(full, dm))
                : Art.scrim(Art.toScreen(Art.glassBlur(full), dm));
        if (!full.isRecycled()) full.recycle();
        return new Bitmap[] {home, soft, dock};
    }

    private void showWallpaper(Bitmap[] set, boolean crossfade) {
        Bitmap old = wallpaper;
        wallpaper = set[0];
        wallpaperSoft = set[1];
        wallpaperDock = set[2];
        if (crossfade && old != null && artKey == null) {
            android.graphics.drawable.TransitionDrawable td = new android.graphics.drawable.TransitionDrawable(
                    new android.graphics.drawable.Drawable[] {
                            new android.graphics.drawable.BitmapDrawable(getResources(), old),
                            new android.graphics.drawable.BitmapDrawable(getResources(), wallpaper)});
            td.setCrossFadeEnabled(true);
            bgSharp.setImageDrawable(td);
            td.startTransition(1600);
        } else {
            bgSharp.setImageBitmap(wallpaper);
        }
        if (artKey == null) {
            setGlass(wallpaperDock);
            bgFrost.setImageBitmap(wallpaperSoft);
        }
    }

    /** Builds off the main thread, then crossfades in. */
    private void applyWallpaperAsync(String name) {
        worker.execute(() -> {
            Bitmap[] set = buildWallpaper(name);
            ui.post(() -> showWallpaper(set, true));
        });
    }

    private boolean clearStyle() {
        return prefs.getBoolean("wallpaper_clear", false);
    }

    // ---- wallpaper rotation ----------------------------------------------------------------

    private static final String[] ROTATE_LABELS = {"Off", "Every 15 Minutes", "Every Hour", "Every Day"};
    private static final long[] ROTATE_MS = {0, 15 * 60_000L, 60 * 60_000L, 24 * 60 * 60_000L};

    private final Runnable rotateTick = new Runnable() {
        @Override
        public void run() {
            rotateWallpaper();
            scheduleRotation();
        }
    };

    private long rotateMs() {
        return ROTATE_MS[Math.max(0, Math.min(ROTATE_MS.length - 1, prefs.getInt("wallpaper_rotate", 0)))];
    }

    /** "Every Hour · next in 34 min" (or just "Off"). */
    private String rotationStatus() {
        String label = ROTATE_LABELS[prefs.getInt("wallpaper_rotate", 0)];
        long every = rotateMs();
        if (every == 0) return label;
        long due = prefs.getLong("wallpaper_rotated_at", System.currentTimeMillis()) + every - System.currentTimeMillis();
        long min = Math.max(1, (due + 59_999) / 60_000);
        return label + " · next in " + (min >= 120 ? (min / 60) + " hr" : min + " min");
    }

    private void scheduleRotation() {
        ui.removeCallbacks(rotateTick);
        long every = rotateMs();
        if (every == 0) return;
        long last = prefs.getLong("wallpaper_rotated_at", System.currentTimeMillis());
        ui.postDelayed(rotateTick, Math.max(5_000, last + every - System.currentTimeMillis()));
    }

    /** Next photo wallpaper in order (falls back to every wallpaper if there are no photos). */
    private void rotateWallpaper() {
        List<String> pool = new ArrayList<>();
        for (java.io.File f : Scenery.photos(this)) pool.add("Photo: " + f.getName());
        if (pool.size() < 2) pool = Scenery.all(this);
        String cur = prefs.getString("wallpaper", "Coast");
        String next = pool.get((pool.indexOf(cur) + 1) % pool.size());
        prefs.edit().putString("wallpaper", next).putLong("wallpaper_rotated_at", System.currentTimeMillis()).apply();
        applyWallpaperAsync(next);
    }

    private void showWallpaperStyle() {
        openMenu("Wallpaper Style", "Clear lets the glass show the full picture",
                new String[] {"Clear (Unblurred)", "Soft (Blurred)"}, i -> {
                    prefs.edit().putBoolean("wallpaper_clear", i == 0).apply();
                    applyWallpaperAsync(prefs.getString("wallpaper", "Coast"));
                });
    }

    private void showRotation() {
        openMenu("Rotate Wallpapers", "Cycles through your wallpaper photos", ROTATE_LABELS, i -> {
            prefs.edit().putInt("wallpaper_rotate", i).putLong("wallpaper_rotated_at", System.currentTimeMillis()).apply();
            scheduleRotation();
        });
    }

    // ---- data ----------------------------------------------------------------------------

    private void reload() {
        dirty = false;
        String keep = focused != null ? focused.app.key : null;
        Set<String> hidden = repo.hidden();
        apps.clear();
        for (AppEntry e : repo.loadAll()) if (!hidden.contains(e.key)) apps.add(e);

        Map<String, AppTile> old = new HashMap<>(tiles);
        tiles.clear();
        List<AppTile> list = new ArrayList<>();
        for (AppEntry e : apps) {
            AppTile t = old.get(e.key);
            if (t == null) t = newTile(e); else t.app = e;
            tiles.put(e.key, t);
            list.add(t);
        }
        grid.setTiles(list);
        AppTile target = keep != null ? tiles.get(keep) : null;
        if (target == null && !list.isEmpty()) target = list.get(0);
        if (target != null) {
            final AppTile ft = target;
            grid.post(() -> {
                if (anyOverlayOpen()) return;
                ft.requestFocus();
                onTileFocused(ft);
            });
        }
    }

    private AppTile newTile(AppEntry e) {
        TextView label = new TextView(this);
        label.setTextSize(14);
        label.setTypeface(Ui.font(560));
        label.setTextColor(0xFFFFFFFF);
        label.setIncludeFontPadding(false);
        label.setGravity(Gravity.CENTER_HORIZONTAL);
        label.setSingleLine(true);
        label.setEllipsize(android.text.TextUtils.TruncateAt.END);
        label.setAlpha(0f);
        label.setText(e.label);
        AppTile t = new AppTile(this, e, label);
        t.setOnClickListener(v -> {
            if (moving == null) launchApp(((AppTile) v).app);
        });
        t.setOnLongClickListener(v -> {
            if (moving == null) showAppOptions((AppTile) v);
            return true;
        });
        return t;
    }

    @Override
    public void launchApp(AppEntry e) {
        lastLaunched = e.pkg;
        try {
            startActivity(e.intent);
        } catch (Exception ex) {
            Toast.makeText(this, "Couldn't open " + e.label, Toast.LENGTH_SHORT).show();
        }
    }

    // ---- focus, scrolling, backdrop ------------------------------------------------------

    private void onTileFocused(AppTile t) {
        focused = t;
        int idx = grid.tiles.indexOf(t);
        if (idx < 0) return;
        t.label.setText(t.app.label);
        setRow(idx / GridPanel.COLS);
    }

    /** Row 0 shows the Top Shelf over the sharp wallpaper; lower rows slide up over a frosted one. */
    private void setRow(int row) {
        if (row == currentRow) return;
        currentRow = row;
        float ty = row == 0 ? 0 : Ui.dp(150) - grid.rowY(row);
        content.animate().translationY(ty).setDuration(440).setInterpolator(Spring.SCROLL)
                .setUpdateListener(a -> grid.dock.invalidate()).start();
        float top = row == 0 ? 1f : 0f;
        nowPlaying.animate().alpha(top).setDuration(row == 0 ? 320 : 160).start();
        weather.animate().alpha(top).translationY(row == 0 ? 0 : -Ui.dp(12))
                .setDuration(row == 0 ? 320 : 180).setInterpolator(AppTile.EASE).start();
        controlBar.animate().alpha(top).translationY(row == 0 ? 0 : -Ui.dp(12))
                .setDuration(row == 0 ? 320 : 180).setInterpolator(AppTile.EASE).start();
        // One fade (ImageView alpha needs no offscreen layer), on the same curve and timing as the scroll, so the frost
        // deepens exactly as the grid slides up.
        bgFrost.animate().cancel();
        bgFrost.animate().alpha(row == 0 ? 0f : 1f).setStartDelay(0).setDuration(420)
                .setInterpolator(AppTile.EASE).start();
    }

    private void focusFirst() {
        if (!grid.tiles.isEmpty()) grid.tiles.get(0).requestFocus();
    }

    private boolean anyOverlayOpen() {
        return menu.isOpen() || dropdown.isOpen() || sidebar.isOpen() || appMenu.isOpen() || weatherPanel.isOpen();
    }

    /** Blocks the grid, Now Playing and the capsule while a panel owns focus. */
    private void setMainFocus(boolean on) {
        int mode = on ? ViewGroup.FOCUS_AFTER_DESCENDANTS : ViewGroup.FOCUS_BLOCK_DESCENDANTS;
        content.setDescendantFocusability(mode);
        controlBar.setDescendantFocusability(mode);
    }

    /**
     * Spotify-style backdrop: while a song plays, its artwork fills the screen, blurred and
     * darkened. Built off the main thread; the dock and scrolled-grid frost follow it.
     */
    @Override
    public void onArtwork(Bitmap art) {
        // Players send a small placeholder while the real cover loads; keep the current
        // backdrop until the real one arrives rather than flashing to the placeholder.
        if (art != null && Math.max(art.getWidth(), art.getHeight()) < 300) return;
        String key = art == null ? null : artKey(art);
        if (key == null ? artKey == null : key.equals(artKey)) return;
        artKey = key;
        if (art == null) {
            bgSharp.setVisibility(View.VISIBLE);
            bgArt.animate().cancel();
            bgArt.animate().alpha(0f).setStartDelay(0).setDuration(450).withEndAction(() -> {
                if (artKey == null) bgArt.setImageDrawable(null);
            }).start();
            bgFrost.setImageBitmap(wallpaperSoft);
            setGlass(wallpaperDock);
            return;
        }
        Bitmap[] cached = artCache.get(key);
        if (cached != null) {
            applyArt(cached);
            return;
        }
        final Bitmap src = art.copy(Bitmap.Config.ARGB_8888, false);
        final android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        worker.execute(() -> {
            if (!key.equals(artKey)) return;  // already superseded by a newer track: skip the work
            long t0 = android.os.SystemClock.uptimeMillis();
            int w = src.getWidth(), h = Math.min(src.getHeight(), Math.round(src.getWidth() * 9f / 16f));
            Bitmap crop = Bitmap.createBitmap(src, 0, (src.getHeight() - h) / 2, w, h);
            // The main backdrop first, so the screen changes as soon as possible...
            // Darken on the small image, then scale once: far cheaper than working at full size.
            Bitmap bg = Art.toScreen(Art.dim(Art.scrim(Art.glassBlur(crop)), 0x4D000000), dm);
            long t1 = android.os.SystemClock.uptimeMillis();
            root.post(() -> { if (key.equals(artKey)) applyArt(new Bitmap[] {bg, null, null}); });
            // ...then the frosted versions for the scrolled grid and the dock glass.
            Bitmap soft = Art.toScreen(Art.blur(crop), dm);
            Bitmap dock = Art.toScreen(Art.dockBlur(crop), dm);
            Bitmap[] set = {bg, soft, dock};
            android.util.Log.d("Halo", "backdrop " + (t1 - t0) + " ms, frost +" + (android.os.SystemClock.uptimeMillis() - t1) + " ms");
            root.post(() -> {
                artCache.put(key, set);
                if (key.equals(artKey)) {
                    bgFrost.setImageBitmap(soft);
                    setGlass(dock);
                }
            });
        });
    }

    /** Last few artwork backdrops, so a cover that comes back (players resend) shows instantly. */
    private final java.util.LinkedHashMap<String, Bitmap[]> artCache =
            new java.util.LinkedHashMap<String, Bitmap[]>(4, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(java.util.Map.Entry<String, Bitmap[]> e) {
                    return size() > 2;
                }
            };

    private void applyArt(Bitmap[] set) {
        bgArt.animate().cancel();
        boolean wasShowing = bgArt.getAlpha() > 0.99f && bgArt.getDrawable() != null;
        if (wasShowing) {
            // Already on an artwork backdrop: crossfade by fading the new one over the wallpaper
            // layer briefly showing the old picture.
            bgSharp.setImageBitmap(((android.graphics.drawable.BitmapDrawable) bgArt.getDrawable()).getBitmap());
            bgSharp.setVisibility(View.VISIBLE);
            bgArt.setAlpha(0f);
        }
        bgArt.setImageBitmap(set[0]);
        if (set[1] != null) bgFrost.setImageBitmap(set[1]);
        if (set[2] != null) setGlass(set[2]);
        bgArt.animate().alpha(1f).setStartDelay(0).setDuration(450).withEndAction(() -> {
            bgSharp.setImageBitmap(wallpaper);
            bgSharp.setVisibility(View.INVISIBLE);
        }).start();
    }

    /** Cheap identity for artwork, since players resend the same picture as new bitmaps. */
    private static String artKey(Bitmap b) {
        StringBuilder k = new StringBuilder().append(b.getWidth()).append('x').append(b.getHeight());
        for (int y = 1; y < 4; y++)
            for (int x = 1; x < 4; x++) k.append(':').append(b.getPixel(b.getWidth() * x / 4, b.getHeight() * y / 4));
        return k.toString();
    }


    @Override
    public void onNowPlayingShown(boolean shown) {
        refreshCapsule();
        if (!shown && getCurrentFocus() == null && !anyOverlayOpen()) focusFirst();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        if (e.getAction() == KeyEvent.ACTION_DOWN && nowPlaying.poke()) return true;
        if (moving != null) {
            if (e.getAction() == KeyEvent.ACTION_DOWN) {
                switch (e.getKeyCode()) {
                    case KeyEvent.KEYCODE_DPAD_LEFT: moveBy(-1); break;
                    case KeyEvent.KEYCODE_DPAD_RIGHT: moveBy(1); break;
                    case KeyEvent.KEYCODE_DPAD_UP: moveBy(-GridPanel.COLS); break;
                    case KeyEvent.KEYCODE_DPAD_DOWN: moveBy(GridPanel.COLS); break;
                    case KeyEvent.KEYCODE_DPAD_CENTER:
                    case KeyEvent.KEYCODE_ENTER:
                    case KeyEvent.KEYCODE_BACK:
                        endMove();
                        break;
                    default:
                        return super.dispatchKeyEvent(e);
                }
            }
            return true;
        }
        // Top bar: step explicitly through weather ↔ capsule buttons (they may still be mid-
        // expansion, with zero width, so focus search can't find them).
        if (e.getAction() == KeyEvent.ACTION_DOWN && !anyOverlayOpen() && moving == null) {
            int dir = direction(e.getKeyCode());
            View cur = getCurrentFocus();
            if ((dir == View.FOCUS_LEFT || dir == View.FOCUS_RIGHT) && cur != null
                    && (cur == weather || isInside(cur, controlBar))) {
                List<View> bar = new ArrayList<>();
                if (weather.getVisibility() == View.VISIBLE) bar.add(weather);
                for (int b : new int[] {ControlBar.AUDIO, ControlBar.HEADPHONES, ControlBar.HOME,
                        ControlBar.CONTROLS, ControlBar.PROFILE}) {
                    View v = controlBar.buttons[b];
                    if (v != null && v.getVisibility() == View.VISIBLE) bar.add(v);
                }
                int i = bar.indexOf(cur) + (dir == View.FOCUS_LEFT ? -1 : 1);
                if (i < 0) openSidebar(cur);
                else if (i < bar.size()) {
                    controlBar.setExpanded(true);
                    bar.get(i).requestFocus();
                }
                return true;
            }
            // Up to the top row goes to whichever side is nearer: the weather pill on the left
            // half of the screen, the top-right capsule on the right half.
            if (dir == View.FOCUS_UP && cur != null && weather.getVisibility() == View.VISIBLE
                    && (isInside(cur, nowPlaying) || (cur instanceof AppTile && grid.tiles.indexOf(cur) >= 0
                        && grid.tiles.indexOf(cur) < GridPanel.COLS && !nowPlaying.hasMedia()))) {
                int[] l = new int[2];
                cur.getLocationOnScreen(l);
                boolean leftSide = l[0] + cur.getWidth() / 2 < getResources().getDisplayMetrics().widthPixels / 2;
                if (leftSide) weather.requestFocus();
                else controlBar.buttons[ControlBar.PROFILE].requestFocus();
                return true;
            }
            if (dir == View.FOCUS_DOWN && cur == weather) {
                if (nowPlaying.hasMedia()) nowPlaying.requestFocus(); else focusFirst();
                return true;
            }
        }
        if (weatherPanel.isOpen() && direction(e.getKeyCode()) != 0) {
            if (e.getAction() == KeyEvent.ACTION_DOWN && e.getKeyCode() == KeyEvent.KEYCODE_DPAD_DOWN) weatherPanel.hide();
            return true;
        }
        if (e.getAction() == KeyEvent.ACTION_DOWN && (menu.isOpen() || appMenu.isOpen())) {
            int dir = direction(e.getKeyCode());
            View cur = getCurrentFocus();
            if (dir != 0 && cur != null) {
                View next = cur.focusSearch(dir);
                View box = menu.isOpen() ? menu : appMenu;
                if (next == null || !isInside(next, box)) return true;
            }
        }
        if (e.getAction() == KeyEvent.ACTION_DOWN && !menu.isOpen() && !appMenu.isOpen()) {
            int dir = direction(e.getKeyCode());
            View cur = getCurrentFocus();
            if (dir != 0 && cur != null) {
                View next = cur.focusSearch(dir);
                // Left at the grid's edge opens the sidebar; it must not hop up to the weather pill.
                if (next == null || (dir == View.FOCUS_LEFT && next == weather && cur != weather)
                        || (sidebar.isOpen() && !isInside(next, sidebar))
                        || (dropdown.isOpen() && !isInside(next, dropdown))) {
                    if (sidebar.isOpen()) {
                        if (dir == View.FOCUS_RIGHT) sidebar.close();
                        return true;
                    }
                    if (dropdown.isOpen()) {
                        if (dir == View.FOCUS_UP) dropdown.hide();
                        return true;
                    }
                    if (dir == View.FOCUS_LEFT) {
                        openSidebar(cur);
                        return true;
                    }
                    if (cur instanceof AppTile) {
                        ((AppTile) cur).nudge(dir == View.FOCUS_RIGHT ? 1 : 0,
                                dir == View.FOCUS_UP ? -1 : dir == View.FOCUS_DOWN ? 1 : 0);
                        return true;
                    }
                }
                // The tile being left gets a tug toward the new one; the system then moves focus
                // (and plays the TV's own navigation sound).
                if (cur instanceof AppTile && next instanceof AppTile) {
                    ((AppTile) cur).tug(dir == View.FOCUS_RIGHT ? 1 : dir == View.FOCUS_LEFT ? -1 : 0,
                            dir == View.FOCUS_DOWN ? 1 : dir == View.FOCUS_UP ? -1 : 0);
                }
            }
        }
        return super.dispatchKeyEvent(e);
    }

    private static int direction(int key) {
        switch (key) {
            case KeyEvent.KEYCODE_DPAD_LEFT: return View.FOCUS_LEFT;
            case KeyEvent.KEYCODE_DPAD_RIGHT: return View.FOCUS_RIGHT;
            case KeyEvent.KEYCODE_DPAD_UP: return View.FOCUS_UP;
            case KeyEvent.KEYCODE_DPAD_DOWN: return View.FOCUS_DOWN;
            default: return 0;
        }
    }

    @Override
    public void onBackPressed() {
        if (weatherPanel.isOpen()) weatherPanel.hide();
        else if (appMenu.isOpen()) appMenu.hide();
        else if (menu.isOpen()) menu.hide(true);
        else if (dropdown.isOpen()) dropdown.hide();
        else if (sidebar.isOpen()) sidebar.close();
        else if (currentRow > 0 || !(getCurrentFocus() instanceof AppTile)) focusFirst();
        // Never leave the home screen.
    }

    // ---- sidebar -------------------------------------------------------------------------

    private void openSidebar(View from) {
        sidebarReturn = from;
        setMainFocus(false);
        sidebar.open(profileName(), clockText(), apps, snapshotBlurred());
    }

    /** A small blurred picture of the screen as it is now, for frosted panels to sit on. */
    private Bitmap snapshotBlurred() {
        try {
            int w = root.getWidth() / 6, h = root.getHeight() / 6;
            Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
            android.graphics.Canvas c = new android.graphics.Canvas(b);
            c.scale(w / (float) root.getWidth(), h / (float) root.getHeight());
            root.draw(c);
            Art.blurInPlace(b, 4, 3);
            return b;
        } catch (Exception e) {
            return null;
        }
    }

    @Override
    public void onSidebarClosed() {
        setMainFocus(true);
        if (sidebarReturn != null && sidebarReturn.isShown()) sidebarReturn.requestFocus(); else focusFirst();
    }

    @Override
    public void showHomeScreenSettings() {
        showSettings();
    }

    // ---- Control Center ------------------------------------------------------------------

    @Override
    public void openPanel(int which, View anchor) {
        Dropdown d = dropdown.begin(snapshotBlurred(), Dropdown.CC_WIDTH);
        switch (which) {
            case ControlBar.HOME: buildHome(d); break;
            case ControlBar.AUDIO: buildAudio(d); break;
            case ControlBar.HEADPHONES: buildHeadphones(d); break;
            case ControlBar.CONTROLS: buildControls(d); break;
            default: buildProfile(d);
        }
        setMainFocus(false);
        // The time slides over to line up with the open panel's left edge, and back on close.
        int sw = getResources().getDisplayMetrics().widthPixels;
        controlBar.slideTime(true, sw - Ui.dp(28) - Ui.dp(Dropdown.CC_WIDTH));
        d.show(() -> {
            controlBar.slideTime(false, 0);
            setMainFocus(true);
            if (!menu.isOpen()) anchor.requestFocus();
        });
    }

    /** Closes the panel, then runs the action (used for anything that leaves the panel). */
    private Runnable then(Runnable r) {
        return () -> {
            dropdown.hide();
            r.run();
        };
    }

    /** tvOS 26 Control Center: Power Off, Wi-Fi, Do Not Disturb, Accessories, Sleep Timer, shortcuts. */
    private void buildControls(Dropdown d) {
        d.ccGrid();
        d.ccLarge(0, 0, R.drawable.ic_power, "Power Off", then(() -> Actions.sleep(this)));
        String net = networkName();
        d.ccWide(0, 2, "Ethernet".equals(net) ? R.drawable.ic_ethernet : R.drawable.ic_wifi, 0xFF0A84FF,
                "Ethernet".equals(net) ? "Ethernet" : "Wi-Fi", "Ethernet".equals(net) ? "Connected" : net,
                then(() -> Actions.wifi(this)));
        d.ccWide(1, 2, R.drawable.ic_dnd, MediaListener.dndOn(this) ? 0xFF8E7CFF : 0xFFFFFFFF,
                "Do Not Disturb", null, then(() -> {
                    boolean on = !MediaListener.dndOn(this);
                    if (MediaListener.setDnd(this, on)) {
                        Toast.makeText(this, "Do Not Disturb " + (on ? "on" : "off"), Toast.LENGTH_SHORT).show();
                    } else {
                        Toast.makeText(this, "Do Not Disturb isn't available", Toast.LENGTH_SHORT).show();
                    }
                }));
        d.ccWide(2, 0, R.drawable.ic_airplay, 0xFFFFFFFF, "Audio", null,
                () -> { dropdown.hide(); ui.post(() -> openPanel(ControlBar.AUDIO, controlBar.buttons[ControlBar.CONTROLS])); });
        final TextView[] timer = new TextView[1];
        timer[0] = d.ccWide(2, 2, R.drawable.ic_timer, SleepTimer.remaining(this) > 0 ? 0xFFFF9F0A : 0xFFFFFFFF,
                "Sleep Timer", SleepTimer.remaining(this) > 0 ? SleepTimer.label(this) : null, () -> {
            SleepTimer.cycle(this);
            Toast.makeText(this, "Sleep Timer: " + SleepTimer.label(this), Toast.LENGTH_SHORT).show();
        });
        d.ccSmall(3, 0, R.drawable.ic_photo, then(() -> Actions.start(this, new Intent(this, ArtModeActivity.class))));
        d.ccSmall(3, 1, R.drawable.ic_access, then(() -> Actions.accessibility(this)));
        d.ccSmall(3, 2, R.drawable.ic_gear, then(() -> Actions.settings(this)));
        d.ccSmall(3, 3, R.drawable.ic_search, then(() -> Actions.search(this)));
    }

    private String networkName() {
        try {
            ConnectivityManager cm = getSystemService(ConnectivityManager.class);
            NetworkCapabilities nc = cm.getNetworkCapabilities(cm.getActiveNetwork());
            if (nc == null) return "Not Connected";
            if (nc.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) return "Ethernet";
            @SuppressWarnings("deprecation")
            String ssid = getSystemService(WifiManager.class).getConnectionInfo().getSSID();
            return ssid == null || ssid.contains("unknown ssid") ? "Connected" : ssid.replace("\"", "");
        } catch (Exception e) {
            return "Connected";
        }
    }

    private String pairedSummary() {
        try {
            BluetoothManager bm = getSystemService(BluetoothManager.class);
            int n = bm.getAdapter().getBondedDevices().size();
            return n == 0 ? "None paired" : n + " paired";
        } catch (Exception e) {
            return "Bluetooth";
        }
    }

    /** "Photo: Big Sur Horizon.jpg" reads as "Big Sur Horizon". */
    private String prettyWallpaper() {
        return prefs.getString("wallpaper", "Coast").replaceFirst("^Photo: ", "").replaceFirst("\\.[^.]+$", "");
    }

    /** Now playing: track, transport and volume, laid out as even tile rows. */
    private void buildAudio(Dropdown d) {
        if (nowPlaying.hasSession()) {
            d.header(R.drawable.ic_music, nowPlaying.title(), nowPlaying.subtitle());
            if (!nowPlaying.hasMedia()) {
                d.row(R.drawable.ic_home, "Show on Home Screen", null, then(nowPlaying::showOnHome));
            }
            d.tiles(new int[] {R.drawable.ic_prev, nowPlaying.isPlaying() ? R.drawable.ic_pause : R.drawable.ic_play,
                            R.drawable.ic_next},
                    new String[] {null, null, null},
                    new Runnable[] {nowPlaying::previous, nowPlaying::playPause, nowPlaying::next}, 48);
            d.row(R.drawable.ic_music, "Open in " + nowPlaying.sourceApp(), null, then(nowPlaying::open));
        } else {
            d.header(R.drawable.ic_music, "Not Playing", null);
        }
        d.section("Volume");
        d.tiles(new int[] {R.drawable.ic_voldown, R.drawable.ic_mute, R.drawable.ic_volup},
                new String[] {null, null, null},
                new Runnable[] {
                        () -> Actions.volume(this, AudioManager.ADJUST_LOWER),
                        () -> Actions.volume(this, AudioManager.ADJUST_TOGGLE_MUTE),
                        () -> Actions.volume(this, AudioManager.ADJUST_RAISE)}, 48);
    }

    /** Connected headphones, like the AirPods panel on tvOS. */
    private void buildHeadphones(Dropdown d) {
        String name = headphoneName();
        d.header(R.drawable.ic_headphones, name != null ? name : "Headphones", name != null ? "Connected" : "Not connected");
        d.tiles(new int[] {R.drawable.ic_voldown, R.drawable.ic_mute, R.drawable.ic_volup},
                new String[] {null, null, null},
                new Runnable[] {
                        () -> Actions.volume(this, AudioManager.ADJUST_LOWER),
                        () -> Actions.volume(this, AudioManager.ADJUST_TOGGLE_MUTE),
                        () -> Actions.volume(this, AudioManager.ADJUST_RAISE)}, 48);
        d.row(R.drawable.ic_bluetooth, "Bluetooth Settings…", null, then(() -> Actions.bluetooth(this)));
    }

    /** Name of the connected Bluetooth headphones, or null. */
    private String headphoneName() {
        AudioManager am = getSystemService(AudioManager.class);
        for (android.media.AudioDeviceInfo dev : am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)) {
            int t = dev.getType();
            if (t == android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP
                    || t == android.media.AudioDeviceInfo.TYPE_BLE_HEADSET) {
                return String.valueOf(dev.getProductName());
            }
        }
        return null;
    }

    private void refreshCapsule() {
        controlBar.setContext(nowPlaying.hasSession(), headphoneName() != null);
        linkWeather();
    }

    /** Home: the home screen itself — wallpaper, Art Mode, hidden apps, layout. */
    private void buildHome(Dropdown d) {
        d.header(R.drawable.ic_home, "Home Screen", null);
        d.row(R.drawable.ic_photo, "Wallpaper", prettyWallpaper(), then(this::showWallpapers));
        d.row(R.drawable.ic_photo, "Wallpaper Style", clearStyle() ? "Clear" : "Soft", then(this::showWallpaperStyle));
        d.row(R.drawable.ic_timer, "Rotate Wallpapers", rotationStatus(),
                then(this::showRotation));
        d.row(R.drawable.ic_photo, "Art Mode", null,
                then(() -> Actions.start(this, new Intent(this, ArtModeActivity.class))));
        d.row(R.drawable.ic_grid, "Hidden Apps", hiddenEntries(repo.hidden()).size() + " hidden", then(this::showHidden));
        d.row(R.drawable.ic_home, "Reset Layout", null, then(() -> {
            repo.reset();
            focused = null;
            reload();
        }));
    }

    /** Your account. */
    private void buildProfile(Dropdown d) {
        d.header(R.drawable.ic_home, profileName(), "Google account");
        d.row(R.drawable.ic_gear, "Accounts & Sign-in", null,
                then(() -> Actions.start(this, new Intent(Settings.ACTION_SYNC_SETTINGS), new Intent(Settings.ACTION_SETTINGS))));
        d.row(R.drawable.ic_gear, "Settings", null, then(() -> Actions.settings(this)));
    }

    // ---- moving --------------------------------------------------------------------------

    private void startMove(AppTile t) {
        moving = t;
        t.requestFocus();
        t.wiggle(true);
        moveHint.setVisibility(View.VISIBLE);
        moveHint.setAlpha(0f);
        moveHint.animate().alpha(1f).setDuration(200).start();
    }

    private void moveBy(int delta) {
        int i = grid.tiles.indexOf(moving);
        int j = Math.max(0, Math.min(grid.tiles.size() - 1, i + delta));
        if (i == j) {
            moving.nudge(Math.abs(delta) == 1 ? delta : 0, Math.abs(delta) > 1 ? Integer.signum(delta) : 0);
            return;
        }
        List<AppTile> order = new ArrayList<>(grid.tiles);
        Map<AppTile, int[]> before = new HashMap<>();
        for (AppTile t : order) before.put(t, new int[] {t.getLeft(), t.getTop()});
        order.add(j, order.remove(i));
        apps.add(j, apps.remove(i));
        grid.setTiles(order);
        moving.requestFocus();
        grid.post(() -> {
            for (AppTile t : order) {
                int[] p = before.get(t);
                float ox = p[0] - t.getLeft(), oy = p[1] - t.getTop();
                if (ox == 0 && oy == 0) continue;
                t.setTranslationX(ox);
                t.setTranslationY(oy);
                t.animate().translationX(0).translationY(0).setDuration(240)
                        .setInterpolator(AppTile.EASE).start();
            }
            onTileFocused(moving);
        });
    }

    private void endMove() {
        moving.wiggle(false);
        moving = null;
        saveOrder();
        moveHint.animate().alpha(0f).setDuration(150).withEndAction(() -> moveHint.setVisibility(View.GONE));
    }

    private void saveOrder() {
        // Keep hidden apps' positions in the saved order too.
        List<AppEntry> all = new ArrayList<>(apps);
        for (AppEntry e : repo.loadAll()) if (!all.contains(e)) all.add(e);
        repo.saveOrder(all);
    }

    // ---- menus ---------------------------------------------------------------------------

    private void openMenu(String title, String sub, String[] items, IntConsumer pick) {
        final AppTile back = focused;
        setMainFocus(false);
        Runnable restore = () -> {
            setMainFocus(true);
            if (back != null && back.getParent() != null) back.requestFocus(); else focusFirst();
        };
        menu.show(title, sub, items, i -> {
            setMainFocus(true);
            pick.accept(i);
            if (!menu.isOpen() && moving == null) restore.run();
        }, restore);
    }

    /** tvOS-style context menu beside the held icon. */
    private void showAppOptions(AppTile t) {
        AppEntry e = t.app;
        boolean system = isSystem(e.pkg);
        List<AppMenu.Item> items = new ArrayList<>();
        items.add(new AppMenu.Item("Move", R.drawable.ic_move, false, () -> startMove(t)));
        items.add(new AppMenu.Item("Close App", R.drawable.ic_close, false, () -> closeApp(e)));
        items.add(new AppMenu.Item("Hide from Home Screen", R.drawable.ic_hide, false, () -> {
            repo.setHidden(e.key, true);
            focused = null;
            reload();
        }));
        items.add(new AppMenu.Item("App Info", R.drawable.ic_info, false, () -> Actions.start(this,
                new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", e.pkg, null)))));
        if (!system) {
            items.add(new AppMenu.Item("Delete App", R.drawable.ic_trash, true, () -> Actions.start(this,
                    new Intent(Intent.ACTION_DELETE, Uri.fromParts("package", e.pkg, null)))));
        }
        setMainFocus(false);
        appMenu.show(t, e.label, items, snapshotBlurred(), () -> {
            setMainFocus(true);
            if (moving == null && t.getParent() != null) t.requestFocus();
        });
    }

    /**
     * Closes an app running in the background (Halo is in front while the menu is open, so the
     * app is). Android only lets launchers end background processes; a player with an active
     * notification, like music still playing, may keep running.
     */
    private void closeApp(AppEntry e) {
        if (nowPlaying.hasMedia() && e.pkg.equals(String.valueOf(nowPlaying.sourcePackage()))) nowPlaying.stopPlayback();
        if (e.pkg.equals(lastLaunched)) {
            // Android shields the most recently used app from launchers, so hand over to its
            // settings page, where Force stop closes it.
            Actions.start(this, new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                    Uri.fromParts("package", e.pkg, null)));
            Toast.makeText(this, "Choose Force stop to close " + e.label, Toast.LENGTH_LONG).show();
            return;
        }
        getSystemService(android.app.ActivityManager.class).killBackgroundProcesses(e.pkg);
        Toast.makeText(this, e.label + " closed", Toast.LENGTH_SHORT).show();
    }

    private void showSettings() {
        Set<String> hidden = repo.hidden();
        String[] items = {
                "Wallpaper: " + prefs.getString("wallpaper", "Coast"),
                "Hidden Apps (" + hiddenEntries(hidden).size() + ")",
                "Reset Home Screen Layout",
                "System Settings"};
        openMenu("Home Screen", "Halo Home", items, i -> {
            switch (i) {
                case 0: showWallpapers(); break;
                case 1: showHidden(); break;
                case 2:
                    repo.reset();
                    focused = null;
                    reload();
                    break;
                case 3:
                    Actions.settings(this);
                    break;
            }
        });
    }

    private void showWallpapers() {
        List<String> names = Scenery.all(this);
        openMenu("Wallpaper", "Add your own photos with the setup script", names.toArray(new String[0]), i -> {
            prefs.edit().putString("wallpaper", names.get(i)).putLong("wallpaper_rotated_at", System.currentTimeMillis()).apply();
            applyWallpaperAsync(names.get(i));
        });
    }

    private List<AppEntry> hiddenEntries(Set<String> hidden) {
        List<AppEntry> out = new ArrayList<>();
        for (AppEntry e : repo.loadAll()) if (hidden.contains(e.key)) out.add(e);
        return out;
    }

    private void showHidden() {
        List<AppEntry> list = hiddenEntries(repo.hidden());
        if (list.isEmpty()) {
            Toast.makeText(this, "No hidden apps", Toast.LENGTH_SHORT).show();
            return;
        }
        String[] names = new String[list.size()];
        for (int i = 0; i < names.length; i++) names[i] = list.get(i).label;
        openMenu("Hidden Apps", "Choose an app to show it again", names, i -> {
            repo.setHidden(list.get(i).key, false);
            reload();
        });
    }

    private boolean isSystem(String pkg) {
        try {
            return (getPackageManager().getApplicationInfo(pkg, 0).flags
                    & android.content.pm.ApplicationInfo.FLAG_SYSTEM) != 0;
        } catch (Exception e) {
            return true;
        }
    }
}
