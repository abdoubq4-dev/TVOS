package com.halo.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.TimeZone;

/**
 * Current conditions from Open-Meteo (free, no account). The place comes from the TV's time
 * zone city (e.g. Australia/Melbourne → Melbourne), or the "weather_city" preference.
 */
final class Weather {
    static final class Now {
        final int temp, code;
        final boolean day;

        Now(int temp, int code, boolean day) {
            this.temp = temp;
            this.code = code;
            this.day = day;
        }

        String condition() {
            return conditionFor(code, day);
        }

        int icon() {
            return iconFor(code, day);
        }
    }

    static String conditionFor(int code, boolean day) {
        {
            if (code == 0) return day ? "Sunny" : "Clear";
            if (code == 1) return day ? "Mostly Sunny" : "Mostly Clear";
            if (code == 2) return "Partly Cloudy";
            if (code == 3) return "Cloudy";
            if (code == 45 || code == 48) return "Foggy";
            if (code >= 51 && code <= 57) return "Drizzle";
            if (code >= 61 && code <= 67) return "Rain";
            if (code >= 71 && code <= 77) return "Snow";
            if (code >= 80 && code <= 82) return "Showers";
            if (code == 85 || code == 86) return "Snow Showers";
            if (code >= 95) return "Thunderstorms";
            return "";
        }
    }

    static int iconFor(int code, boolean day) {
        {
            if (code <= 1) return day ? R.drawable.ic_w_sun : R.drawable.ic_moon;
            if (code <= 3) return R.drawable.ic_w_cloud;
            if (code == 45 || code == 48) return R.drawable.ic_w_fog;
            if ((code >= 71 && code <= 77) || code == 85 || code == 86) return R.drawable.ic_w_snow;
            if (code >= 95) return R.drawable.ic_w_storm;
            return R.drawable.ic_w_rain;
        }
    }

    /** The full forecast saved by the last successful fetch, or null. */
    static JSONObject cachedFull(Context c) {
        String s = c.getSharedPreferences("halo", 0).getString("wx_full", null);
        try {
            return s == null ? null : new JSONObject(s);
        } catch (Exception e) {
            return null;
        }
    }

    static String city(Context c) {
        SharedPreferences p = c.getSharedPreferences("halo", 0);
        String city = p.getString("weather_city", p.getString("wx_city", null));
        if (city == null) {
            String id = TimeZone.getDefault().getID();
            city = id.substring(id.lastIndexOf('/') + 1).replace('_', ' ');
        }
        return city;
    }

    private static final long FRESH_MS = 25 * 60 * 1000;

    static Now cached(Context c) {
        SharedPreferences p = c.getSharedPreferences("halo", 0);
        if (!p.contains("wx_temp")) return null;
        return new Now(p.getInt("wx_temp", 0), p.getInt("wx_code", 0), p.getBoolean("wx_day", true));
    }

    static boolean fresh(Context c) {
        return System.currentTimeMillis() - c.getSharedPreferences("halo", 0).getLong("wx_at", 0) < FRESH_MS;
    }

    /** Blocking; call off the main thread. Returns null on any failure. */
    static Now fetch(Context c) {
        SharedPreferences p = c.getSharedPreferences("halo", 0);
        try {
            if (!p.contains("wx_lat")) {
                String city = p.getString("weather_city", null);
                if (city == null) {
                    String id = TimeZone.getDefault().getID();
                    city = id.substring(id.lastIndexOf('/') + 1).replace('_', ' ');
                }
                p.edit().putString("wx_city", city).apply();
                JSONObject g = get("https://geocoding-api.open-meteo.com/v1/search?count=1&format=json&name="
                        + URLEncoder.encode(city, "UTF-8"));
                JSONObject r = g.getJSONArray("results").getJSONObject(0);
                p.edit().putFloat("wx_lat", (float) r.getDouble("latitude"))
                        .putFloat("wx_lon", (float) r.getDouble("longitude")).apply();
            }
            JSONObject f = get("https://api.open-meteo.com/v1/forecast"
                    + "?current=temperature_2m,apparent_temperature,relative_humidity_2m,wind_speed_10m,weather_code,is_day"
                    + "&hourly=temperature_2m,weather_code,precipitation_probability,is_day"
                    + "&daily=weather_code,temperature_2m_max,temperature_2m_min,sunrise,sunset,"
                    + "precipitation_probability_max,uv_index_max"
                    + "&forecast_days=6&timezone=auto&latitude=" + p.getFloat("wx_lat", 0) + "&longitude=" + p.getFloat("wx_lon", 0));
            p.edit().putString("wx_full", f.toString()).apply();
            JSONObject cur = f.getJSONObject("current");
            Now n = new Now((int) Math.round(cur.getDouble("temperature_2m")), cur.getInt("weather_code"),
                    cur.getInt("is_day") == 1);
            p.edit().putInt("wx_temp", n.temp).putInt("wx_code", n.code).putBoolean("wx_day", n.day)
                    .putLong("wx_at", System.currentTimeMillis()).apply();
            return n;
        } catch (Exception e) {
            return null;
        }
    }

    private static JSONObject get(String url) throws Exception {
        HttpURLConnection h = (HttpURLConnection) new URL(url).openConnection();
        h.setConnectTimeout(8000);
        h.setReadTimeout(8000);
        try (InputStream in = h.getInputStream()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[4096];
            for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
            return new JSONObject(out.toString("UTF-8"));
        } finally {
            h.disconnect();
        }
    }

    private Weather() {}
}
