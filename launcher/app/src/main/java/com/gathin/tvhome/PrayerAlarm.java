package com.gathin.tvhome;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.util.Log;

import java.util.Calendar;

/**
 * A soft chime as each prayer time comes in (Syuruk, which is not a prayer, stays quiet), whatever is
 * on screen. One exact alarm at a time: each chime sets the next from the stored JAKIM year.
 */
public class PrayerAlarm extends BroadcastReceiver {
    private static final String TAG = "TvHome.Prayer";
    private static final String PREFS = "prayer_times", ZONE = "alarm_zone";

    /** Sets the alarm for the next prayer after now; nothing when the zone is off or no times are stored. */
    static void schedule(Context ctx, String zone) {
        ctx = ctx.getApplicationContext();
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(ZONE, zone).apply();
        AlarmManager am = ctx.getSystemService(AlarmManager.class);
        PendingIntent pi = PendingIntent.getBroadcast(ctx, 0, new Intent(ctx, PrayerAlarm.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        if (am == null) return;
        long at = next(ctx, zone);
        if (at <= 0) {
            am.cancel(pi);
            return;
        }
        am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pi);
    }

    /** When the next prayer begins, in wall-clock milliseconds, today or tomorrow; 0 when unknown. */
    static long next(Context ctx, String zone) {
        if (zone.isEmpty()) return 0;
        long now = System.currentTimeMillis();
        Calendar day = Calendar.getInstance();
        for (int d = 0; d < 2; d++) {
            PrayerTimes.Day times = PrayerTimes.find(ctx, zone, day);
            if (times != null) {
                for (int i = 0; i < times.minutes.length; i++) {
                    if (i == 1) continue;   // Syuruk
                    Calendar at = (Calendar) day.clone();
                    at.set(Calendar.HOUR_OF_DAY, times.minutes[i] / 60);
                    at.set(Calendar.MINUTE, times.minutes[i] % 60);
                    at.set(Calendar.SECOND, 0);
                    at.set(Calendar.MILLISECOND, 0);
                    if (at.getTimeInMillis() > now + 1000) return at.getTimeInMillis();
                }
            }
            day.add(Calendar.DAY_OF_MONTH, 1);
        }
        return 0;
    }

    @Override
    public void onReceive(Context ctx, Intent intent) {
        Log.i(TAG, "prayer time: chime");
        play(ctx);
        String zone = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(ZONE, "");
        schedule(ctx, zone);
    }

    /** The sounds a prayer time can make, by the name kept in the prefs. */
    static final String[] SOUNDS = {"azan_long", "azan_short", "azan_aaqib", "azan_andrewler", "azan_jarih", "chime", "off"};
    private static final int[] SOUND_NAMES = {R.string.snd_1, R.string.snd_2, R.string.snd_3, R.string.snd_4,
            R.string.snd_5, R.string.snd_chime, R.string.snd_off};

    /** The built-in sounds' names, in {@link #SOUNDS} order. */
    static String[] soundNames(android.content.Context ctx) {
        String[] out = new String[SOUND_NAMES.length];
        for (int i = 0; i < out.length; i++) out[i] = ctx.getString(SOUND_NAMES[i]);
        return out;
    }
    /** Azan recordings added by the family: files in the app's "azan" folder, played as "file:<name>". */
    static final String FILE = "file:";

    static java.io.File azanDir(Context ctx) {
        java.io.File base = Config.dir(ctx);
        if (base == null) return null;
        java.io.File d = new java.io.File(base, "azan");
        d.mkdirs();
        return d;
    }

    /** Sounds the family added, newest last; each as {key, name}. */
    static java.util.List<String[]> ownSounds(Context ctx) {
        java.util.List<String[]> out = new java.util.ArrayList<>();
        java.io.File d = azanDir(ctx);
        java.io.File[] files = d != null ? d.listFiles() : null;
        if (files == null) return out;
        java.util.Arrays.sort(files);
        for (java.io.File f : files) {
            if (!f.isFile()) continue;
            String n = f.getName();
            int dot = n.lastIndexOf('.');
            out.add(new String[]{FILE + n, "\u266A " + (dot > 0 ? n.substring(0, dot) : n)});
        }
        return out;
    }

    /** Audio files in Download/ (a browser, LocalSend) copied into the azan folder. Returns how many. */
    static int importFromDownloads(Context ctx) {
        java.io.File d = azanDir(ctx);
        if (d == null) return 0;
        int added = 0;
        java.io.File root = new java.io.File("/storage/emulated/0/Download");
        for (java.io.File folder : new java.io.File[]{root, new java.io.File(root, "LocalSend")}) {
            java.io.File[] files = folder.listFiles();
            if (files == null) continue;
            for (java.io.File f : files) {
                String n = f.getName().toLowerCase(java.util.Locale.ROOT);
                if (!f.isFile() || f.length() > 15 * 1024 * 1024 || !(n.endsWith(".mp3") || n.endsWith(".ogg")
                        || n.endsWith(".oga") || n.endsWith(".m4a") || n.endsWith(".aac") || n.endsWith(".wav"))) continue;
                java.io.File out = new java.io.File(d, f.getName());
                if (out.exists()) continue;
                try (java.io.InputStream in = new java.io.FileInputStream(f);
                     java.io.OutputStream os = new java.io.FileOutputStream(out)) {
                    byte[] buf = new byte[64 * 1024];
                    for (int r; (r = in.read(buf)) > 0; ) os.write(buf, 0, r);
                    added++;
                } catch (Exception e) {
                    Log.w(TAG, "import " + f + ": " + e);
                }
            }
        }
        return added;
    }

    static String sound(Context ctx) {
        return ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString("sound", "azan_long");
    }

    /** Built-in azans the family took off the list (they live in the app, so they are hidden, not deleted). */
    static java.util.Set<String> hidden(Context ctx) {
        return new java.util.HashSet<>(ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getStringSet("hidden_sounds", java.util.Collections.emptySet()));
    }

    static void hide(Context ctx, String sound) {
        java.util.Set<String> h = hidden(ctx);
        h.add(sound);
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putStringSet("hidden_sounds", h).apply();
    }

    static void setSound(Context ctx, String sound) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString("sound", sound).apply();
    }

    static void play(Context ctx) {
        play(ctx, sound(ctx));
    }

    private static MediaPlayer playing;

    /** Plays one of {@link #SOUNDS}; a new one stops the last, so previews never overlap. */
    static void play(Context ctx, String sound) {
        int res;
        java.io.File own = null;
        switch (sound) {
            case "azan_short": res = R.raw.azan_short; break;
            case "azan_aaqib": res = R.raw.azan_aaqib; break;
            case "azan_andrewler": res = R.raw.azan_andrewler; break;
            case "azan_jarih": res = R.raw.azan_jarih; break;
            case "chime": res = R.raw.prayer_chime; break;
            case "off": return;
            default:
                res = R.raw.azan_long;
                if (sound.startsWith(FILE) && azanDir(ctx) != null) {
                    own = new java.io.File(azanDir(ctx), sound.substring(FILE.length()));
                    if (!own.isFile()) own = null;
                }
                break;
        }
        if (playing != null) {
            try {
                playing.release();
            } catch (Exception ignored) {
            }
            playing = null;
        }
        try {
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            MediaPlayer mp;
            if (own != null) {
                mp = new MediaPlayer();
                mp.setAudioAttributes(attrs);
                mp.setDataSource(own.getPath());
                mp.prepare();
            } else {
                mp = MediaPlayer.create(ctx.getApplicationContext(), res, attrs,
                        ctx.getSystemService(android.media.AudioManager.class).generateAudioSessionId());
            }
            if (mp == null) return;
            mp.setVolume(0.6f, 0.6f);
            mp.setOnCompletionListener(p -> {
                p.release();
                if (playing == p) playing = null;
            });
            playing = mp;
            mp.start();
        } catch (Exception e) {
            Log.w(TAG, "chime: " + e);
        }
    }
}
