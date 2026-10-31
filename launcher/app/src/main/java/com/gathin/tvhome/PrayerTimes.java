package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Calendar;
import java.util.Locale;

/**
 * Malaysian prayer times from JAKIM's e-Solat (www.e-solat.gov.my) for one zone, such as KDH05 for
 * Kulim and Bandar Baharu. The whole year is fetched once and kept in the app's files, so the times show
 * offline and the launcher goes online again only when the year turns or the zone changes. A failed
 * fetch is retried no sooner than {@link #RETRY_MS}.
 *
 * Off unless a theme sets {@code "prayer_zone"}; then nothing here runs.
 */
final class PrayerTimes {
    private static final String TAG = "TvHome.Prayer";
    private static final String URL_YEAR = "https://www.e-solat.gov.my/index.php?r=esolatApi/TakwimSolat&period=year&zone=";
    private static final long RETRY_MS = 30 * 60_000L;
    private static final int MAX_BYTES = 512 * 1024;

    /** Names shown on the TV, in the day's order. Syuruk is sunrise, not a prayer, but every Malaysian list shows it. */
    static final String[] NAMES = {"Subuh", "Syuruk", "Zohor", "Asar", "Maghrib", "Isyak"};
    private static final String[] KEYS = {"fajr", "syuruk", "dhuhr", "asr", "maghrib", "isha"};
    private static final String[] HIJRI_MONTHS = {"Muharram", "Safar", "Rabiulawal", "Rabiulakhir", "Jamadilawal",
            "Jamadilakhir", "Rejab", "Syaaban", "Ramadan", "Syawal", "Zulkaedah", "Zulhijjah"};

    /** One day: minutes after midnight for each of {@link #NAMES}, and the Hijri date as JAKIM gives it. */
    static final class Day {
        final int[] minutes;
        final String hijri;

        Day(int[] minutes, String hijri) {
            this.minutes = minutes;
            this.hijri = hijri;
        }
    }

    private PrayerTimes() {}

    private static File file(Context ctx, String zone) {
        return new File(ctx.getFilesDir(), "prayer-" + zone + ".json");
    }

    /** Whether today is missing from the stored year and a fetch has not just failed. */
    static boolean due(Context ctx, String zone) {
        if (zone.isEmpty()) return false;
        if (find(ctx, zone, Calendar.getInstance()) != null) return false;
        return System.currentTimeMillis() - prefs(ctx).getLong("tried_" + zone, 0) > RETRY_MS;
    }

    /** Fetches this year's times; call off the main thread. True when new times were stored. */
    static boolean refresh(Context ctx, String zone) {
        prefs(ctx).edit().putLong("tried_" + zone, System.currentTimeMillis()).apply();
        try {
            byte[] body = get(URL_YEAR + zone);
            JSONObject json = new JSONObject(new String(body, StandardCharsets.UTF_8));
            JSONArray days = json.optJSONArray("prayerTime");
            if (days == null || days.length() == 0) throw new IllegalStateException("no prayerTime in answer");
            File out = file(ctx, zone), tmp = new File(out.getPath() + ".tmp");
            try (OutputStream os = new FileOutputStream(tmp)) {
                os.write(body);
            }
            if (!tmp.renameTo(out)) throw new IllegalStateException("could not store " + out);
            cached = null;
            Log.i(TAG, "stored " + days.length() + " days for " + zone);
            return true;
        } catch (Exception e) {
            Log.w(TAG, "fetch failed for " + zone + ": " + e);
            return false;
        }
    }

    private static String cachedKey;
    private static JSONArray cached;

    /** The stored times for that calendar day, or null. */
    static Day find(Context ctx, String zone, Calendar when) {
        JSONArray days = load(ctx, zone);
        if (days == null) return null;
        int d = when.get(Calendar.DAY_OF_MONTH), m = when.get(Calendar.MONTH), y = when.get(Calendar.YEAR);
        // The year is in date order, so the day of the year is nearly always the right index.
        int guess = when.get(Calendar.DAY_OF_YEAR) - 1;
        for (int k = 0; k < days.length(); k++) {
            int i = (guess + k) % days.length();
            JSONObject o = days.optJSONObject(i);
            if (o == null || !sameDay(o.optString("date"), d, m, y)) continue;
            int[] mins = new int[KEYS.length];
            for (int p = 0; p < KEYS.length; p++) {
                mins[p] = minutes(o.optString(KEYS[p]));
                if (mins[p] < 0) return null;
            }
            return new Day(mins, o.optString("hijri"));
        }
        return null;
    }

    private static JSONArray load(Context ctx, String zone) {
        File f = file(ctx, zone);
        String key = zone + "@" + f.lastModified();
        if (key.equals(cachedKey) && cached != null) return cached;
        if (!f.isFile() || f.length() == 0 || f.length() > MAX_BYTES) return null;
        try (InputStream in = new FileInputStream(f)) {
            byte[] buf = new byte[(int) f.length()];
            int n = 0, r;
            while (n < buf.length && (r = in.read(buf, n, buf.length - n)) > 0) n += r;
            cached = new JSONObject(new String(buf, 0, n, StandardCharsets.UTF_8)).optJSONArray("prayerTime");
            cachedKey = key;
            return cached;
        } catch (Exception e) {
            Log.w(TAG, "unreadable " + f + ": " + e);
            return null;
        }
    }

    /** JAKIM writes dates as "31-Okt-2026": Malay month names, sometimes English ones. */
    static boolean sameDay(String date, int day, int month, int year) {
        String[] p = date.split("-");
        if (p.length != 3) return false;
        try {
            return Integer.parseInt(p[0]) == day && Integer.parseInt(p[2]) == year && month(p[1]) == month;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    static int month(String name) {
        String n = name.toLowerCase(Locale.ROOT);
        String[][] names = {{"jan"}, {"feb"}, {"mac", "mar"}, {"apr"}, {"mei", "may"}, {"jun"}, {"jul"},
                {"ogo", "aug"}, {"sep"}, {"okt", "oct"}, {"nov"}, {"dis", "dec"}};
        for (int i = 0; i < names.length; i++) for (String s : names[i]) if (n.startsWith(s)) return i;
        return -1;
    }

    /** "13:03:00" as minutes after midnight, or -1. */
    static int minutes(String hms) {
        String[] p = hms.split(":");
        if (p.length < 2) return -1;
        try {
            int h = Integer.parseInt(p[0]), m = Integer.parseInt(p[1]);
            return h < 0 || h > 23 || m < 0 || m > 59 ? -1 : h * 60 + m;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** "1448-05-20" as "20 Jamadilawal 1448"; empty when it doesn't parse. */
    static String hijri(String ymd) {
        String[] p = ymd.split("-");
        if (p.length != 3) return "";
        try {
            int m = Integer.parseInt(p[1]);
            if (m < 1 || m > 12) return "";
            return Integer.parseInt(p[2]) + " " + HIJRI_MONTHS[m - 1] + " " + Integer.parseInt(p[0]);
        } catch (NumberFormatException e) {
            return "";
        }
    }

    /** Index of the next time today after {@code now} minutes, or -1 once Isyak has passed. */
    static int next(Day day, int now) {
        for (int i = 0; i < day.minutes.length; i++) if (day.minutes[i] > now) return i;
        return -1;
    }

    /** "5:53" or "05:53", as the TV's clock is set. */
    static String format(int minutes, boolean is24) {
        int h = minutes / 60, m = minutes % 60;
        if (is24) return String.format(Locale.ROOT, "%02d:%02d", h, m);
        int h12 = h % 12 == 0 ? 12 : h % 12;
        return String.format(Locale.ROOT, "%d:%02d", h12, m);
    }

    /** "1 j 54 min" or "12 min". */
    static String until(android.content.Context ctx, int minutes) {
        int h = minutes / 60, m = minutes % 60;
        return h > 0 ? ctx.getString(R.string.dur_hm, h, m) : ctx.getString(R.string.dur_m, m);
    }

    private static byte[] get(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(30000);
        c.setRequestProperty("Accept", "application/json");
        try {
            if (c.getResponseCode() != 200) throw new IllegalStateException("HTTP " + c.getResponseCode());
            try (InputStream in = c.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buf = new byte[16 * 1024];
                int r;
                while ((r = in.read(buf)) > 0) {
                    out.write(buf, 0, r);
                    if (out.size() > MAX_BYTES) throw new IllegalStateException("response too large");
                }
                return out.toByteArray();
            }
        } finally {
            c.disconnect();
        }
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences("prayer_times", Context.MODE_PRIVATE);
    }
}
