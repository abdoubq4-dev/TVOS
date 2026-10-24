package com.halo.tvhome;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.provider.Settings;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Finds launchable apps, builds their artwork and keeps the user's order and hidden list. */
final class AppRepo {
    static final String SETTINGS_PKG = "com.android.tv.settings";

    /** Default first row, like the tvOS dock. */
    private static final List<String> PRIORITY = Arrays.asList(
            "com.apple.atve.androidtv.appletv",
            "com.netflix.ninja",
            "com.google.android.youtube.tv",
            "org.smarttube.stable",
            "com.disney.disneyplus",
            "com.amazon.amazonvideo.livingroom",
            "au.com.stan.and",
            "com.wbd.stream",
            "com.spotify.tv.android");
    private static final List<String> LAST = Arrays.asList("com.android.vending", SETTINGS_PKG);
    private static final Set<String> DEFAULT_HIDDEN = new HashSet<>(Arrays.asList(
            "com.google.android.play.games",
            "com.google.android.videos",
            "com.google.android.tvrecommendations"));

    private final Context ctx;
    private final SharedPreferences prefs;
    private final Map<String, AppEntry> cache = new HashMap<>();
    final int artW, artH;

    AppRepo(Context ctx, SharedPreferences prefs, int artW, int artH) {
        this.ctx = ctx;
        this.prefs = prefs;
        this.artW = artW;
        this.artH = artH;
    }

    /** All launchable apps, in display order, including hidden ones. */
    List<AppEntry> loadAll() {
        PackageManager pm = ctx.getPackageManager();
        LinkedHashMap<String, AppEntry> byPkg = new LinkedHashMap<>();

        Intent tv = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        for (ResolveInfo ri : pm.queryIntentActivities(tv, 0)) add(pm, ri, byPkg, true);

        // Sideloaded phone apps that have no TV entry point still deserve a tile.
        Intent phone = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER);
        for (ResolveInfo ri : pm.queryIntentActivities(phone, 0)) {
            if ((ri.activityInfo.applicationInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0) continue;
            add(pm, ri, byPkg, false);
        }

        if (!byPkg.containsKey(SETTINGS_PKG)) {
            AppEntry s = new AppEntry(SETTINGS_PKG, SETTINGS_PKG, "Settings",
                    new Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            s.art = Art.round(Art.settingsArt(ctx, artW, artH), Ui.dp(GridPanel.TILE_RADIUS) * artW / (float) Ui.dp(GridPanel.TILE_W));
            s.blur = Art.blur(s.art);
            byPkg.put(SETTINGS_PKG, s);
        }

        // Saved order first, then priority apps, then everything else alphabetically, then the tail.
        List<AppEntry> out = new ArrayList<>();
        for (String k : savedOrder()) {
            AppEntry e = byPkg.remove(k);
            if (e != null) out.add(e);
        }
        // A newly installed priority app (e.g. SmartTube) slots in right after the priority app
        // before it, instead of landing at the end of a layout that was already saved.
        for (int i = 0; i < PRIORITY.size(); i++) {
            AppEntry e = byPkg.remove(PRIORITY.get(i));
            if (e == null) continue;
            int at = out.size();
            for (int j = i - 1; j >= 0; j--) {
                int idx = indexOf(out, PRIORITY.get(j));
                if (idx >= 0) { at = idx + 1; break; }
            }
            out.add(at, e);
        }
        List<AppEntry> tail = new ArrayList<>();
        for (String k : LAST) {
            AppEntry e = byPkg.remove(k);
            if (e != null) tail.add(e);
        }
        List<AppEntry> rest = new ArrayList<>(byPkg.values());
        rest.sort((a, b) -> a.label.compareToIgnoreCase(b.label));
        out.addAll(rest);
        out.addAll(tail);
        return out;
    }

    /** A tile image pushed by set-icon.sh to files/icons/<package>.png replaces the app's own. */
    private Bitmap customIcon(String pkg) {
        java.io.File f = new java.io.File(ctx.getExternalFilesDir("icons"), pkg + ".png");
        if (!f.canRead()) return null;
        Bitmap src = android.graphics.BitmapFactory.decodeFile(f.getPath());
        if (src == null) return null;
        Bitmap out = Bitmap.createScaledBitmap(src, artW, artH, true);
        if (out != src) src.recycle();
        return out.isMutable() ? out : out.copy(Bitmap.Config.ARGB_8888, true);
    }

    private static int indexOf(List<AppEntry> list, String pkg) {
        for (int i = 0; i < list.size(); i++) if (list.get(i).pkg.equals(pkg)) return i;
        return -1;
    }

    private void add(PackageManager pm, ResolveInfo ri, Map<String, AppEntry> into, boolean tv) {
        String pkg = ri.activityInfo.packageName;
        if (pkg.equals(ctx.getPackageName()) || into.containsKey(pkg)) return;
        ComponentName cn = new ComponentName(pkg, ri.activityInfo.name);
        String key = pkg;
        AppEntry cached = cache.get(cn.flattenToShortString());
        if (cached != null) {
            into.put(key, cached);
            return;
        }
        String label = SETTINGS_PKG.equals(pkg) ? "Settings" : String.valueOf(ri.loadLabel(pm));
        Intent i = new Intent(Intent.ACTION_MAIN)
                .addCategory(tv ? Intent.CATEGORY_LEANBACK_LAUNCHER : Intent.CATEGORY_LAUNCHER)
                .setComponent(cn)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        AppEntry e = new AppEntry(key, pkg, label, i);
        try {
            Bitmap custom = customIcon(pkg);
            if (custom != null) {
                e.art = custom;
            } else if (SETTINGS_PKG.equals(pkg)) {
                e.art = Art.settingsArt(ctx, artW, artH);
            } else {
                Drawable banner = ri.activityInfo.loadBanner(pm);
                if (banner == null) banner = ri.activityInfo.applicationInfo.loadBanner(pm);
                e.art = banner != null
                        ? Art.fromBanner(banner, artW, artH)
                        : Art.fromIcon(ri.loadIcon(pm), artW, artH);
            }
            e.blur = Art.blur(e.art);
            e.art = Art.round(e.art, Ui.dp(GridPanel.TILE_RADIUS) * artW / (float) Ui.dp(GridPanel.TILE_W));
        } catch (Exception ex) {
            e.art = Art.settingsArt(ctx, artW, artH);
            e.blur = Art.blur(e.art);
        }
        cache.put(cn.flattenToShortString(), e);
        into.put(key, e);
    }

    void forget(String pkg) {
        cache.values().removeIf(e -> e.pkg.equals(pkg));
    }

    List<String> savedOrder() {
        String s = prefs.getString("order", "");
        return s.isEmpty() ? new ArrayList<>() : new ArrayList<>(Arrays.asList(s.split(",")));
    }

    void saveOrder(List<AppEntry> apps) {
        StringBuilder sb = new StringBuilder();
        for (AppEntry e : apps) {
            if (sb.length() > 0) sb.append(',');
            sb.append(e.key);
        }
        prefs.edit().putString("order", sb.toString()).apply();
    }

    Set<String> hidden() {
        if (!prefs.contains("hidden")) return new HashSet<>(DEFAULT_HIDDEN);
        return new HashSet<>(prefs.getStringSet("hidden", new HashSet<>()));
    }

    void setHidden(String key, boolean hide) {
        Set<String> h = hidden();
        if (hide) h.add(key); else h.remove(key);
        prefs.edit().putStringSet("hidden", h).apply();
    }

    void reset() {
        prefs.edit().remove("order").remove("hidden").apply();
    }
}
