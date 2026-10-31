package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Weather for Home's line under the Hijri date and the weather page, from Open-Meteo (free, no key):
 * the place chosen on the weather page (Kulim until one is chosen), its weather now and the next 16
 * days. The last answer is kept, so Home shows it straight away after a restart.
 */
final class Weather {
    private static final String TAG = "TvHome.Weather";
    static final String PREFS = "weather";
    private static final String PLACE = "place", LAT = "lat", LON = "lon", LAST = "last", AT = "at";
    static final long REFRESH_MS = 30 * 60_000L;

    static final class Day {
        final String date;   // yyyy-MM-dd
        final int code;
        final double max, min, rain, wind, uv;
        final int rainChance;
        final String sunrise, sunset;

        Day(JSONObject d, int i) throws Exception {
            date = d.getJSONArray("time").getString(i);
            code = d.getJSONArray("weather_code").optInt(i);
            max = d.getJSONArray("temperature_2m_max").optDouble(i);
            min = d.getJSONArray("temperature_2m_min").optDouble(i);
            rainChance = d.getJSONArray("precipitation_probability_max").optInt(i);
            rain = d.getJSONArray("precipitation_sum").optDouble(i);
            wind = d.getJSONArray("wind_speed_10m_max").optDouble(i);
            uv = d.getJSONArray("uv_index_max").optDouble(i);
            sunrise = clock(d.getJSONArray("sunrise").optString(i));
            sunset = clock(d.getJSONArray("sunset").optString(i));
        }
    }

    static final class Report {
        final String place;
        final double temp, feels, wind;
        final int humidity, code;
        final boolean day;
        final List<Day> days = new ArrayList<>();

        Report(String place, JSONObject js) throws Exception {
            this.place = place;
            JSONObject c = js.getJSONObject("current");
            temp = c.optDouble("temperature_2m");
            feels = c.optDouble("apparent_temperature");
            wind = c.optDouble("wind_speed_10m");
            humidity = c.optInt("relative_humidity_2m");
            code = c.optInt("weather_code");
            day = c.optInt("is_day", 1) == 1;
            JSONObject d = js.getJSONObject("daily");
            for (int i = 0; i < d.getJSONArray("time").length(); i++) days.add(new Day(d, i));
        }

        /** "☁ Kulim · 25°C · Berawan". */
        String line(Context ctx) {
            return icon(code, day) + "  " + place + " · " + Math.round(temp) + "°C · " + text(ctx, code);
        }
    }

    /** A place found by name: what to show and where it is. */
    static final class Place {
        final String name, detail;
        final double lat, lon;

        Place(String name, String detail, double lat, double lon) {
            this.name = name;
            this.detail = detail;
            this.lat = lat;
            this.lon = lon;
        }
    }

    private Weather() {}

    static String place(Context ctx) {
        return prefs(ctx).getString(PLACE, "Kulim");
    }

    static void setPlace(Context ctx, Place p) {
        prefs(ctx).edit().putString(PLACE, p.name).putString(LAT, String.valueOf(p.lat))
                .putString(LON, String.valueOf(p.lon)).remove(LAST).putLong(AT, 0).apply();
    }

    /** The last answer kept, or null. */
    static Report cached(Context ctx) {
        String last = prefs(ctx).getString(LAST, null);
        if (last == null) return null;
        try {
            return new Report(place(ctx), new JSONObject(last));
        } catch (Exception e) {
            return null;
        }
    }

    static boolean due(Context ctx) {
        return System.currentTimeMillis() - prefs(ctx).getLong(AT, 0) > REFRESH_MS;
    }

    /** Fetches the chosen place's weather; call off the main thread. Null when offline. */
    static Report refresh(Context ctx) {
        SharedPreferences p = prefs(ctx);
        String lat = p.getString(LAT, "5.36499"), lon = p.getString(LON, "100.56177");
        try {
            String body = get("https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon
                    + "&current=temperature_2m,relative_humidity_2m,apparent_temperature,weather_code,wind_speed_10m,is_day"
                    + "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max,"
                    + "precipitation_sum,wind_speed_10m_max,sunrise,sunset,uv_index_max"
                    + "&timezone=auto&forecast_days=16");
            Report r = new Report(place(ctx), new JSONObject(body));
            p.edit().putString(LAST, body).putLong(AT, System.currentTimeMillis()).apply();
            return r;
        } catch (Exception e) {
            Log.w(TAG, "refresh: " + e);
            return null;
        }
    }

    /** Places matching a name, for the weather page's "Tukar lokasi"; call off the main thread. */
    static List<Place> search(String name) {
        List<Place> out = new ArrayList<>();
        try {
            JSONArray rs = new JSONObject(get("https://geocoding-api.open-meteo.com/v1/search?count=10&language=en&name="
                    + URLEncoder.encode(name.trim(), "UTF-8"))).optJSONArray("results");
            for (int i = 0; rs != null && i < rs.length(); i++) {
                JSONObject r = rs.getJSONObject(i);
                StringBuilder detail = new StringBuilder();
                for (String k : new String[]{"admin2", "admin1", "country"}) {
                    String v = r.optString(k);
                    if (!v.isEmpty() && !v.equals(r.optString("name"))) detail.append(detail.length() > 0 ? ", " : "").append(v);
                }
                out.add(new Place(r.optString("name"), detail.toString(), r.optDouble("latitude"), r.optDouble("longitude")));
            }
        } catch (Exception e) {
            Log.w(TAG, "search: " + e);
        }
        return out;
    }

    /** WMO weather code in Malay. */
    static String text(Context ctx, int code) {
        int id = 0;
        if (code == 0) id = R.string.w_clear;
        else if (code == 1) id = R.string.w_mainly_clear;
        else if (code == 2) id = R.string.w_partly;
        else if (code == 3) id = R.string.w_overcast;
        else if (code == 45 || code == 48) id = R.string.w_fog;
        else if (code >= 51 && code <= 57) id = R.string.w_drizzle;
        else if (code == 61 || code == 80) id = R.string.w_light_rain;
        else if (code == 63 || code == 81) id = R.string.w_rain;
        else if (code == 65 || code == 82) id = R.string.w_heavy_rain;
        else if (code >= 66 && code <= 77) id = R.string.w_freezing;
        else if (code >= 85 && code <= 86) id = R.string.w_snow;
        else if (code >= 95) id = R.string.w_storm;
        return id == 0 ? "\u2014" : ctx.getString(id);
    }

    static String icon(int code, boolean day) {
        if (code == 0) return day ? "☀️" : "🌙";
        if (code == 1 || code == 2) return day ? "⛅" : "☁️";
        if (code == 3) return "☁️";
        if (code == 45 || code == 48) return "🌫️";
        if (code >= 95) return "⛈️";
        if (code >= 85 && code <= 86) return "❄️";
        if (code >= 51) return "🌧️";
        return "☁️";
    }

    private static String clock(String iso) {
        int t = iso.indexOf('T');
        return t >= 0 ? iso.substring(t + 1) : iso;
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static String get(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(20000);
        try {
            if (c.getResponseCode() != 200) throw new IllegalStateException("HTTP " + c.getResponseCode());
            try (InputStream in = c.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buf = new byte[16 * 1024];
                for (int r; (r = in.read(buf)) > 0; ) out.write(buf, 0, r);
                return new String(out.toByteArray(), StandardCharsets.UTF_8);
            }
        } finally {
            c.disconnect();
        }
    }

    static String temp(double t) {
        return String.format(Locale.ROOT, "%d°", Math.round(t));
    }
}
