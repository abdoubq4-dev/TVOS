package com.halo.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Frame TV-style Art Mode: a painting hung on a softly lit gallery wall, inside a thin black
 * frame with a wide off-white mat. Pictures come from files/art/ (or the wallpapers if that is
 * empty) and change every few minutes with a slow crossfade.
 */
final class ArtFrameView extends FrameLayout {
    private static final long CHANGE_MS = 3 * 60 * 1000;

    private final FrameLayout frame;
    private final ImageView picture;
    private final TextView caption;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final List<File> files = new ArrayList<>();
    private int index = -1;
    private Bitmap current;
    private final Runnable advance = new Runnable() {
        @Override
        public void run() {
            show(1);
            handler.postDelayed(this, CHANGE_MS);
        }
    };

    ArtFrameView(Context c) {
        super(c);
        Ui.init(c);
        GradientDrawable wall = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {0xFF3A3733, 0xFF2A2825, 0xFF1D1C1A});
        GradientDrawable light = new GradientDrawable();
        light.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        light.setGradientRadius(Ui.dp(520));
        light.setGradientCenter(0.5f, 0.05f);
        light.setColors(new int[] {0x30FFF4E0, 0x00FFF4E0});
        setBackground(new LayerDrawable(new android.graphics.drawable.Drawable[] {wall, light}));

        frame = new FrameLayout(c);
        frame.setBackgroundColor(0xFF000000);
        picture = new ImageView(c);
        picture.setScaleType(ImageView.ScaleType.FIT_XY);
        picture.setElevation(Ui.dp(1));
        frame.addView(picture);
        addView(frame, new LayoutParams(0, 0, Gravity.CENTER));

        caption = new TextView(c);
        caption.setTextSize(11);
        caption.setTypeface(Ui.font(500));
        caption.setTextColor(0xCCFFFFFF);
        caption.setShadowLayer(Ui.dp(6), 0, Ui.dp(1), 0x99000000);
        caption.setAlpha(0f);
        LayoutParams clp = new LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.END);
        clp.setMargins(0, 0, Ui.dp(40), Ui.dp(22));
        addView(caption, clp);
    }

    void start() {
        files.clear();
        files.addAll(list(getContext().getExternalFilesDir("art")));
        if (files.isEmpty()) files.addAll(list(getContext().getExternalFilesDir("wallpapers")));
        if (files.isEmpty()) {
            caption.setText("Add pictures to Art Mode with the art script on your Mac");
            caption.setAlpha(1f);
            return;
        }
        show(1);
        handler.postDelayed(advance, CHANGE_MS);
    }

    void stop() {
        handler.removeCallbacksAndMessages(null);
    }

    /** Step through the collection: +1 next, -1 previous. */
    void show(int step) {
        if (files.isEmpty()) return;
        // Skip anything unreadable rather than getting stuck on it.
        File f = null;
        Bitmap b = null;
        for (int tries = 0; tries < files.size() && b == null; tries++) {
            index = ((index + step) % files.size() + files.size()) % files.size();
            f = files.get(index);
            b = decode(f);
            if (step == 0) step = 1;
        }
        if (b == null) return;
        final Bitmap next = b;
        frame.animate().cancel();
        frame.animate().alpha(0f).setStartDelay(0).setDuration(current == null ? 0 : 500).withEndAction(() -> {
            Bitmap old = current;
            current = next;
            picture.setImageBitmap(next);
            fit(next);
            if (old != null) old.recycle();
            frame.animate().alpha(1f).setStartDelay(0).setDuration(900).start();
        }).start();
        caption.setText(captionFor(f));
        caption.animate().cancel();
        caption.animate().alpha(1f).setStartDelay(0).setDuration(800).withEndAction(() ->
                caption.animate().alpha(0f).setStartDelay(8000).setDuration(1200).start()).start();
        handler.removeCallbacks(advance);
        handler.postDelayed(advance, CHANGE_MS);
    }

    /** Full screen: the painting fills the whole display, cropped to fit, with no mat or wall. */
    private void fit(Bitmap b) {
        LayoutParams flp = (LayoutParams) frame.getLayoutParams();
        flp.width = LayoutParams.MATCH_PARENT;
        flp.height = LayoutParams.MATCH_PARENT;
        frame.setLayoutParams(flp);
        picture.setScaleType(ImageView.ScaleType.CENTER_CROP);
        picture.setLayoutParams(new FrameLayout.LayoutParams(-1, -1));
    }

    private static List<File> list(File dir) {
        if (dir == null) return new ArrayList<>();
        File[] fs = dir.listFiles((d, n) -> n.toLowerCase().matches(".*\\.(jpe?g|png|webp)$"));
        if (fs == null) return new ArrayList<>();
        Arrays.sort(fs);
        return new ArrayList<>(Arrays.asList(fs));
    }

    private static Bitmap decode(File f) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(f.getPath(), o);
        if (o.outWidth <= 0) return null;
        int sample = 1;
        while (Math.max(o.outWidth, o.outHeight) / (sample * 2) >= 1400) sample *= 2;
        o = new BitmapFactory.Options();
        o.inSampleSize = sample;
        return BitmapFactory.decodeFile(f.getPath(), o);
    }

    /** "The Great Wave - Hokusai, 1831.jpg" becomes "The Great Wave  ·  Hokusai, 1831". */
    private static String captionFor(File f) {
        String n = f.getName().replaceAll("\\.[^.]+$", "");
        int dash = n.indexOf(" - ");
        return dash > 0 ? n.substring(0, dash) + "  ·  " + n.substring(dash + 3) : n;
    }
}
