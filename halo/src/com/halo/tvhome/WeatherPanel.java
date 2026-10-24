package com.halo.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.Locale;

/**
 * Apple Weather-style panel under the weather pill: place, big temperature, condition and
 * high/low; the next six hours; five days with range bars; and one quiet line of details.
 */
final class WeatherPanel extends FrameLayout {
    static final int WIDTH = 222, RADIUS = 22, PAD = 12;

    private final LinearLayout card;
    private Runnable onClose;

    WeatherPanel(Context c) {
        super(c);
        setClickable(true);
        card = new LinearLayout(c);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(Ui.dp(PAD), Ui.dp(PAD), Ui.dp(PAD), Ui.dp(PAD));
        card.setElevation(Ui.dp(20));
        card.setFocusable(true);  // holds focus so Back/Down close it
        LayoutParams lp = new LayoutParams(Ui.dp(WIDTH), LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START);
        lp.topMargin = Ui.dp(28 + ControlBar.HEIGHT + 10);
        lp.leftMargin = Ui.dp(28);
        addView(card, lp);
        setVisibility(GONE);
    }

    boolean isOpen() {
        return getVisibility() == VISIBLE;
    }

    void show(JSONObject f, String city, Bitmap behind, Runnable onClose) {
        this.onClose = onClose;
        card.removeAllViews();
        card.setBackground(new FrostDrawable(card, behind, Ui.dp(RADIUS), 0x8C3A3A3E, 0x992C2C30, 0x40FFFFFF));
        try {
            build(f, city);
        } catch (Exception e) {
            card.removeAllViews();
            TextView t = text(11, 560, 0xCCFFFFFF);
            t.setText("Weather isn't available right now");
            card.addView(t);
        }
        setVisibility(VISIBLE);
        card.setPivotX(Ui.dp(24));
        card.setPivotY(0);
        card.setAlpha(0f);
        card.setScaleX(0.92f);
        card.setScaleY(0.92f);
        card.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(340).setInterpolator(Spring.PANEL).start();
        post(card::requestFocus);
    }

    void hide() {
        if (!isOpen()) return;
        setVisibility(GONE);
        Runnable r = onClose;
        onClose = null;
        if (r != null) r.run();
    }

    private void build(JSONObject f, String city) throws Exception {
        Context c = getContext();
        JSONObject cur = f.getJSONObject("current");
        JSONObject hourly = f.getJSONObject("hourly");
        JSONObject daily = f.getJSONObject("daily");
        boolean day = cur.getInt("is_day") == 1;
        int code = cur.getInt("weather_code");

        // Header: place, condition and high/low on the left; icon and temperature on the right.
        LinearLayout head = new LinearLayout(c);
        head.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout left = new LinearLayout(c);
        left.setOrientation(LinearLayout.VERTICAL);
        TextView place = text(12, 640, 0xFFFFFFFF);
        place.setText(city);
        left.addView(place);
        TextView cond = text(10.5f, 560, 0xE6FFFFFF);
        cond.setText(Weather.conditionFor(code, day));
        left.addView(cond, lp(0, 4));
        TextView hl = text(10, 520, 0xA6FFFFFF);
        hl.setText("H:" + Math.round(daily.getJSONArray("temperature_2m_max").getDouble(0)) + "°  L:"
                + Math.round(daily.getJSONArray("temperature_2m_min").getDouble(0)) + "°");
        left.addView(hl, lp(0, 3));
        head.addView(left, new LinearLayout.LayoutParams(0, -2, 1f));
        ImageView big = icon(Weather.iconFor(code, day), 22);
        LinearLayout.LayoutParams il0 = new LinearLayout.LayoutParams(Ui.dp(22), Ui.dp(22));
        il0.rightMargin = Ui.dp(6);
        head.addView(big, il0);
        TextView temp = text(28, 340, 0xFFFFFFFF);
        temp.setText(Math.round(cur.getDouble("temperature_2m")) + "°");
        head.addView(temp);
        card.addView(head);
        divider();

        // Next six hours.
        JSONArray ht = hourly.getJSONArray("time");
        String nowHour = cur.getString("time").substring(0, 13);
        int start = 0;
        for (int i = 0; i < ht.length(); i++) if (ht.getString(i).startsWith(nowHour)) { start = i; break; }
        LinearLayout hrs = new LinearLayout(c);
        for (int i = 0; i < 6 && start + i < ht.length(); i++) {
            int k = start + i;
            LinearLayout col = new LinearLayout(c);
            col.setOrientation(LinearLayout.VERTICAL);
            col.setGravity(Gravity.CENTER_HORIZONTAL);
            TextView h = text(9.5f, 600, 0xCCFFFFFF);
            h.setText(i == 0 ? "Now" : hourLabel(ht.getString(k)));
            col.addView(h);
            ImageView ic = icon(Weather.iconFor(hourly.getJSONArray("weather_code").getInt(k),
                    hourly.getJSONArray("is_day").getInt(k) == 1), 15);
            LinearLayout.LayoutParams il = new LinearLayout.LayoutParams(Ui.dp(15), Ui.dp(15));
            il.topMargin = Ui.dp(6);
            col.addView(ic, il);
            int pop = hourly.getJSONArray("precipitation_probability").optInt(k, 0);
            TextView p = text(8.5f, 640, 0xFF5AC8FA);
            p.setText(pop >= 30 ? pop + "%" : " ");
            col.addView(p, lp(0, 2));
            TextView t = text(11, 600, 0xFFFFFFFF);
            t.setText(Math.round(hourly.getJSONArray("temperature_2m").getDouble(k)) + "°");
            col.addView(t, lp(0, 2));
            hrs.addView(col, new LinearLayout.LayoutParams(0, -2, 1f));
        }
        card.addView(hrs);
        divider();

        // Five days with range bars against the week's span.
        JSONArray dt = daily.getJSONArray("time"), lo = daily.getJSONArray("temperature_2m_min"),
                hi = daily.getJSONArray("temperature_2m_max"), dc = daily.getJSONArray("weather_code");
        int days = Math.min(5, dt.length());
        double min = 99, max = -99;
        for (int i = 0; i < days; i++) { min = Math.min(min, lo.getDouble(i)); max = Math.max(max, hi.getDouble(i)); }
        for (int i = 0; i < days; i++) {
            LinearLayout row = new LinearLayout(c);
            row.setGravity(Gravity.CENTER_VERTICAL);
            TextView d = text(11, 600, 0xFFFFFFFF);
            d.setText(i == 0 ? "Today" : LocalDate.parse(dt.getString(i))
                    .getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.getDefault()));
            row.addView(d, new LinearLayout.LayoutParams(Ui.dp(40), -2));
            row.addView(icon(Weather.iconFor(dc.getInt(i), true), 14), new LinearLayout.LayoutParams(Ui.dp(14), Ui.dp(14)));
            TextView l = text(10.5f, 560, 0x99FFFFFF);
            l.setText(Math.round(lo.getDouble(i)) + "°");
            l.setGravity(Gravity.END);
            row.addView(l, new LinearLayout.LayoutParams(Ui.dp(30), -2));
            Range bar = new Range(c, min, max, lo.getDouble(i), hi.getDouble(i));
            LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(0, Ui.dp(4), 1f);
            bl.leftMargin = Ui.dp(8);
            bl.rightMargin = Ui.dp(8);
            row.addView(bar, bl);
            TextView h = text(10.5f, 600, 0xFFFFFFFF);
            h.setText(Math.round(hi.getDouble(i)) + "°");
            row.addView(h, new LinearLayout.LayoutParams(Ui.dp(24), -2));
            card.addView(row, new LinearLayout.LayoutParams(-1, Ui.dp(22)));
        }
        divider();

        // One quiet line of details.
        TextView det = text(9.5f, 520, 0xB3FFFFFF);
        det.setGravity(Gravity.CENTER_HORIZONTAL);
        det.setText("Feels " + Math.round(cur.getDouble("apparent_temperature")) + "°  ·  Humidity "
                + cur.getInt("relative_humidity_2m") + "%  ·  Wind " + Math.round(cur.getDouble("wind_speed_10m")) + " km/h");
        card.addView(det, new LinearLayout.LayoutParams(-1, -2));
    }

    private static String hourLabel(String iso) {
        int h = Integer.parseInt(iso.substring(11, 13));
        return (h % 12 == 0 ? 12 : h % 12) + (h < 12 ? "AM" : "PM");
    }

    private void divider() {
        View v = new View(getContext());
        v.setBackgroundColor(0x26FFFFFF);
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(-1, Math.max(1, Ui.dp(0.75f)));
        l.setMargins(0, Ui.dp(10), 0, Ui.dp(10));
        card.addView(v, l);
    }

    private static LinearLayout.LayoutParams lp(int left, int top) {
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(-2, -2);
        l.leftMargin = Ui.dp(left);
        l.topMargin = Ui.dp(top);
        return l;
    }

    private ImageView icon(int res, int dp) {
        ImageView iv = new ImageView(getContext());
        iv.setImageResource(res);
        iv.setImageTintList(android.content.res.ColorStateList.valueOf(0xFFFFFFFF));
        return iv;
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

    /** Apple-style temperature range: a dim track with the day's span coloured cool→warm. */
    private static final class Range extends View {
        private final double min, max, lo, hi;
        private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG), fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF r = new RectF();

        Range(Context c, double min, double max, double lo, double hi) {
            super(c);
            this.min = min;
            this.max = max;
            this.lo = lo;
            this.hi = hi;
            track.setColor(0x33FFFFFF);
        }

        @Override
        protected void onSizeChanged(int w, int h, int ow, int oh) {
            fill.setShader(new LinearGradient(0, 0, w, 0, new int[] {0xFF5AC8FA, 0xFF9BE15D, 0xFFFFD60A, 0xFFFF9F0A},
                    null, Shader.TileMode.CLAMP));
        }

        @Override
        protected void onDraw(Canvas c) {
            float w = getWidth(), h = getHeight(), span = (float) Math.max(1, max - min);
            r.set(0, 0, w, h);
            c.drawRoundRect(r, h / 2, h / 2, track);
            r.set((float) ((lo - min) / span) * w, 0, (float) ((hi - min) / span) * w, h);
            c.drawRoundRect(r, h / 2, h / 2, fill);
        }
    }
}
