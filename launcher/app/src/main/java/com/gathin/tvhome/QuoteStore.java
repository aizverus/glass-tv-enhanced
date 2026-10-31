package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The words on Home's shelf: Quran verses, hadith, motivation and wisdom, which the family can add
 * to and delete from on the "Wisdom" page. Kept as quotes.json in the app's own files,
 * started from the built-in verses ({@link Quotes}) and a few sayings the first time.
 */
final class QuoteStore {
    static final String[] CATS = {"quran", "hadis", "motivasi", "hikmah"};
    private static final int[] LABELS = {R.string.cat_quran, R.string.cat_hadis, R.string.cat_motivasi, R.string.cat_hikmah};

    static String[] labels(Context ctx) {
        String[] out = new String[LABELS.length];
        for (int i = 0; i < out.length; i++) out[i] = ctx.getString(LABELS[i]);
        return out;
    }

    static final class Item {
        final String cat, arabic, text, source;

        Item(String cat, String arabic, String text, String source) {
            this.cat = cat;
            this.arabic = arabic == null ? "" : arabic.trim();
            this.text = text == null ? "" : text.trim();
            this.source = source == null ? "" : source.trim();
        }
    }

    /** Sayings to start with; peribahasa are the Malay people's own, free for anyone. */
    private static final String[][] STARTERS = {
            {"motivasi", "Mulakan hari ini, bukan esok.", "Kata-kata motivasi"},
            {"motivasi", "Usaha kecil setiap hari membawa kejayaan besar.", "Kata-kata motivasi"},
            {"motivasi", "Jangan berhenti sebelum berjaya.", "Kata-kata motivasi"},
            {"motivasi", "Setiap pakar dahulunya seorang pemula.", "Kata-kata motivasi"},
            {"motivasi", "Buat yang terbaik, serahkan selebihnya kepada Allah.", "Kata-kata motivasi"},
            {"motivasi", "Kejayaan bermula dengan langkah pertama.", "Kata-kata motivasi"},
            {"motivasi", "Bersyukur hari ini, berusaha untuk esok.", "Kata-kata motivasi"},
            {"motivasi", "Tenang, satu langkah pada satu masa.", "Kata-kata motivasi"},
            {"hikmah", "Sedikit-sedikit, lama-lama jadi bukit.", "Peribahasa Melayu"},
            {"hikmah", "Di mana ada kemahuan, di situ ada jalan.", "Peribahasa Melayu"},
            {"hikmah", "Hendak seribu daya, tak hendak seribu dalih.", "Peribahasa Melayu"},
            {"hikmah", "Berat sama dipikul, ringan sama dijinjing.", "Peribahasa Melayu"},
            {"hikmah", "Bersatu teguh, bercerai roboh.", "Peribahasa Melayu"},
            {"hikmah", "Air yang tenang jangan disangka tiada buaya.", "Peribahasa Melayu"},
            {"hikmah", "Melentur buluh biarlah dari rebungnya.", "Peribahasa Melayu"},
            {"hikmah", "Jatuh tujuh kali, bangun lapan kali.", "Pepatah Jepun"},
    };

    private QuoteStore() {}

    static String label(Context ctx, String cat) {
        for (int i = 0; i < CATS.length; i++) if (CATS[i].equals(cat)) return ctx.getString(LABELS[i]);
        return cat;
    }

    private static File file(Context ctx) {
        return new File(ctx.getFilesDir(), "quotes.json");
    }

    static List<Item> load(Context ctx) {
        List<Item> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(new String(Files.readAllBytes(file(ctx).toPath()), StandardCharsets.UTF_8));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                out.add(new Item(o.optString("c"), o.optString("ar"), o.optString("t"), o.optString("s")));
            }
            return out;
        } catch (Exception e) {
            // None saved yet (or unreadable): the built-in verses and sayings.
            for (String[] q : Quotes.ALL) {
                out.add(new Item(q[1].startsWith("Surah") ? "quran" : "hadis", q[0], q[2], q[1]));
            }
            for (String[] s : STARTERS) out.add(new Item(s[0], "", s[1], s[2]));
            return out;
        }
    }

    static void save(Context ctx, List<Item> items) {
        JSONArray a = new JSONArray();
        try {
            for (Item it : items) {
                a.put(new JSONObject().put("c", it.cat).put("ar", it.arabic).put("t", it.text).put("s", it.source));
            }
            Files.write(file(ctx).toPath(), a.toString(1).getBytes(StandardCharsets.UTF_8));
        } catch (Exception ignored) {
        }
    }

    /** Categories shown on Home; all of them until some are switched off. */
    static Set<String> enabled(Context ctx) {
        Set<String> all = new HashSet<>(java.util.Arrays.asList(CATS));
        return new HashSet<>(prefs(ctx).getStringSet("enabled", all));
    }

    static void setEnabled(Context ctx, String cat, boolean on) {
        Set<String> s = enabled(ctx);
        if (on) s.add(cat);
        else s.remove(cat);
        prefs(ctx).edit().putStringSet("enabled", s).apply();
    }

    /**
     * Words for this picture today, as the shelf takes them: {title, description, source}. A verse
     * shows its Arabic over its meaning; a short saying is the title itself; a long one goes under
     * its category's name. Null when nothing is switched on.
     */
    static String[] forPicture(Context ctx, String pictureName, Calendar day, int shift) {
        Set<String> on = enabled(ctx);
        List<Item> all = load(ctx), pool = new ArrayList<>();
        for (Item it : all) if (on.contains(it.cat) && !it.text.isEmpty()) pool.add(it);
        // Words chosen on the Wisdom page stay until "Rawak" is chosen again (OK on Home still moves on).
        String pin = pinned(ctx);
        if (!pin.isEmpty() && shift == 0) {
            for (Item it : all) if (key(it).equals(pin)) return shelf(ctx, it);
        }
        if (pool.isEmpty()) return null;
        long every = interval(ctx);
        long seed = every > 0
                ? (System.currentTimeMillis() + java.util.TimeZone.getDefault().getOffset(System.currentTimeMillis())) / every
                : (pictureName == null ? 0 : pictureName.hashCode()) + day.get(Calendar.DAY_OF_YEAR) * 7L;
        Item it = pool.get((int) Math.floorMod(seed + shift, (long) pool.size()));
        return shelf(ctx, it);
    }

    private static String[] shelf(Context ctx, Item it) {
        if (!it.arabic.isEmpty()) return new String[]{it.arabic, it.text, it.source};
        if (it.text.length() <= 48) return new String[]{it.text, "", it.source};
        return new String[]{label(ctx, it.cat), it.text, it.source};
    }

    // ---- the one chosen for Home, and how often the words change ---------------------------------

    static String key(Item it) {
        return it.cat + "|" + it.text;
    }

    static String pinned(Context ctx) {
        return prefs(ctx).getString("pinned", "");
    }

    static void pin(Context ctx, Item it) {
        prefs(ctx).edit().putString("pinned", it == null ? "" : key(it)).apply();
    }

    /** 0: with each wallpaper (and a new day); else every so many milliseconds. */
    static final long[] INTERVALS = {0, 5 * 60_000L, 10 * 60_000L, 30 * 60_000L, 3600_000L, 24 * 3600_000L};
    private static final int[] INTERVAL_NAMES = {R.string.q_every_wall, R.string.wp_every_5, R.string.wp_every_10,
            R.string.wp_every_30, R.string.wp_every_hour, R.string.q_every_day};

    static String[] intervalNames(Context ctx) {
        String[] out = new String[INTERVAL_NAMES.length];
        for (int i = 0; i < out.length; i++) out[i] = ctx.getString(INTERVAL_NAMES[i]);
        return out;
    }

    static long interval(Context ctx) {
        return prefs(ctx).getLong("interval_ms", 0);
    }

    static void setInterval(Context ctx, long ms) {
        prefs(ctx).edit().putLong("interval_ms", ms).putString("pinned", "").apply();
    }

    static String intervalName(Context ctx) {
        long v = interval(ctx);
        for (int i = 0; i < INTERVALS.length; i++) if (INTERVALS[i] == v) return ctx.getString(INTERVAL_NAMES[i]);
        return ctx.getString(INTERVAL_NAMES[0]);
    }

    // ---- more words from the internet -----------------------------------------------------------

    static String[] downloads(Context ctx) {
        return new String[]{ctx.getString(R.string.q_dl_quran_ms), ctx.getString(R.string.q_dl_quran_en), ctx.getString(R.string.q_dl_zen)};
    }

    /**
     * Adds about ten new entries of the chosen kind and says how many; call off the main thread.
     * Verses come from AlQuran.cloud (the Arabic with Abdullah Basmeih's Malay or Saheeh
     * International's English meaning), short ones only so the Arabic fits the widget; quotes from
     * ZenQuotes.io, which asks to be credited.
     */
    static int download(Context ctx, int kind) {
        List<Item> items = load(ctx);
        Set<String> have = new HashSet<>();
        for (Item it : items) have.add(it.text);
        List<Item> found = new ArrayList<>();
        java.util.Random rnd = new java.util.Random();
        try {
            if (kind == 2) {
                JSONArray a = new JSONArray(get("https://zenquotes.io/api/quotes"));
                for (int i = 0; i < a.length() && found.size() < 12; i++) {
                    JSONObject o = a.getJSONObject(i);
                    String q = o.optString("q").trim(), by = o.optString("a").trim();
                    if (q.isEmpty() || q.length() > 110 || have.contains(q) || by.equalsIgnoreCase("zenquotes.io")) continue;
                    found.add(new Item("hikmah", "", q, by));
                }
            } else {
                String meaning = kind == 0 ? "ms.basmeih" : "en.sahih";
                for (int tries = 0; tries < 60 && found.size() < 10; tries++) {
                    JSONArray eds = new JSONObject(get("https://api.alquran.cloud/v1/ayah/" + (1 + rnd.nextInt(6236))
                            + "/editions/quran-simple," + meaning)).getJSONArray("data");
                    JSONObject ar = eds.getJSONObject(0), tr = eds.getJSONObject(1);
                    // The first verse of a surah carries the Basmala in this text; and long verses do not fit.
                    if (ar.optInt("numberInSurah") == 1) continue;
                    String arabic = ar.optString("text").trim(), text = tr.optString("text").trim();
                    if (arabic.length() > 75 || text.length() > 170 || have.contains(text)) continue;
                    JSONObject surah = ar.getJSONObject("surah");
                    String src = "Surah " + surah.optString("englishName") + " " + surah.optInt("number") + ":" + ar.optInt("numberInSurah");
                    found.add(new Item("quran", arabic, text, src));
                    have.add(text);
                }
            }
        } catch (Exception e) {
            android.util.Log.w("TvHome.Wisdom", "download: " + e);
        }
        if (found.isEmpty()) return 0;
        items.addAll(0, found);
        save(ctx, items);
        setEnabled(ctx, found.get(0).cat, true);
        return found.size();
    }

    private static String get(String url) throws Exception {
        java.net.HttpURLConnection c = (java.net.HttpURLConnection) new java.net.URL(url).openConnection();
        c.setConnectTimeout(15000);
        c.setReadTimeout(20000);
        try {
            if (c.getResponseCode() != 200) throw new IllegalStateException("HTTP " + c.getResponseCode());
            try (java.io.InputStream in = c.getInputStream(); java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
                byte[] buf = new byte[16 * 1024];
                for (int r; (r = in.read(buf)) > 0; ) {
                    out.write(buf, 0, r);
                    if (out.size() > 1024 * 1024) throw new IllegalStateException("response too large");
                }
                return new String(out.toByteArray(), StandardCharsets.UTF_8);
            }
        } finally {
            c.disconnect();
        }
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences("quotes", Context.MODE_PRIVATE);
    }
}
