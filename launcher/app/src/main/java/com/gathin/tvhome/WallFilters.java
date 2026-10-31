package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;

/**
 * Looks a wallpaper can be given in the picker (Sepia, Mono, Noir...). The picture file is never
 * changed: the chosen look is kept per picture name in the "wallpaper" prefs and laid over the
 * picture each time Home decodes it, so the glass and edge glow follow it too. A look is written
 * "Sepia" at full strength or "Sepia@60" at 60%.
 */
final class WallFilters {
    static final String[] NAMES = {"Original", "Vivid", "Warm", "Cool", "Sepia", "Mono", "Black & White",
            "Noir", "Dramatic", "Fade"};

    private static final int[] LABELS = {R.string.f_original, R.string.f_vivid, R.string.f_warm, R.string.f_cool,
            R.string.f_sepia, R.string.f_mono, R.string.f_bw, R.string.f_noir, R.string.f_dramatic, R.string.f_fade};

    private WallFilters() {}

    /** A look's name as shown, in the launcher's language (the stored names stay English). */
    static String label(Context ctx, String name) {
        for (int i = 0; i < NAMES.length; i++) if (NAMES[i].equals(name)) return ctx.getString(LABELS[i]);
        return name;
    }

    /** A look's name without its strength: "Sepia" for "Sepia@60". */
    static String name(String look) {
        int at = look.indexOf('@');
        return at < 0 ? look : look.substring(0, at);
    }

    /** A look's strength in percent, 100 when not given. */
    static int strength(String look) {
        int at = look.indexOf('@');
        try {
            return at < 0 ? 100 : Math.max(0, Math.min(100, Integer.parseInt(look.substring(at + 1))));
        } catch (NumberFormatException e) {
            return 100;
        }
    }

    static String look(String name, int strength) {
        return strength >= 100 ? name : name + "@" + strength;
    }

    /** The look kept for a picture, "Original" when none. */
    static String of(Context ctx, String picture) {
        if (picture == null) return NAMES[0];
        return prefs(ctx).getString("filter:" + picture, NAMES[0]);
    }

    static void set(Context ctx, String picture, String filter) {
        if (NAMES[0].equals(name(filter)) || strength(filter) == 0) prefs(ctx).edit().remove("filter:" + picture).apply();
        else prefs(ctx).edit().putString("filter:" + picture, filter).apply();
    }

    /** The colour change for a look at its strength (blended with no change), or null for Original. */
    static ColorMatrix matrix(String look) {
        ColorMatrix full = full(name(look));
        int s = strength(look);
        if (full == null || s <= 0) return null;
        if (s >= 100) return full;
        float t = s / 100f;
        float[] a = full.getArray(), id = new ColorMatrix().getArray(), out = new float[20];
        for (int i = 0; i < 20; i++) out[i] = id[i] + (a[i] - id[i]) * t;
        return new ColorMatrix(out);
    }

    private static ColorMatrix full(String filter) {
        ColorMatrix m = new ColorMatrix();
        switch (filter) {
            case "Vivid":
                m.setSaturation(1.45f);
                m.postConcat(contrast(1.08f, 0));
                return m;
            case "Warm":
                m.setScale(1.08f, 1.0f, 0.86f, 1f);
                return m;
            case "Cool":
                m.setScale(0.9f, 1.0f, 1.1f, 1f);
                return m;
            case "Sepia":
                m.set(new float[]{
                        0.393f, 0.769f, 0.189f, 0, 0,
                        0.349f, 0.686f, 0.168f, 0, 0,
                        0.272f, 0.534f, 0.131f, 0, 0,
                        0, 0, 0, 1, 0});
                return m;
            case "Mono":
                m.setSaturation(0f);
                return m;
            case "Black & White":
                m.setSaturation(0f);
                m.postConcat(contrast(1.6f, 0));
                return m;
            case "Noir":
                m.setSaturation(0f);
                m.postConcat(contrast(1.75f, -30));
                return m;
            case "Dramatic":
                m.setSaturation(0.8f);
                m.postConcat(contrast(1.35f, -14));
                return m;
            case "Fade":
                m.setSaturation(0.75f);
                m.postConcat(contrast(0.82f, 18));
                return m;
            default:
                return null;
        }
    }

    /** Contrast about mid-grey, then {@code shift} added to every channel (0 to 255 scale). */
    private static ColorMatrix contrast(float c, float shift) {
        float t = 128f * (1 - c) + shift;
        return new ColorMatrix(new float[]{
                c, 0, 0, 0, t,
                0, c, 0, 0, t,
                0, 0, c, 0, t,
                0, 0, 0, 1, 0});
    }

    /** {@code bmp} with the look laid over it (a new picture), or {@code bmp} itself for Original. */
    static Bitmap apply(Bitmap bmp, String filter) {
        ColorMatrix m = bmp == null ? null : matrix(filter);
        if (m == null) return bmp;
        try {
            Bitmap out = Bitmap.createBitmap(bmp.getWidth(), bmp.getHeight(), Bitmap.Config.RGB_565);
            Paint p = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.DITHER_FLAG);
            p.setColorFilter(new ColorMatrixColorFilter(m));
            new Canvas(out).drawBitmap(bmp, 0, 0, p);
            bmp.recycle();
            return out;
        } catch (OutOfMemoryError e) {
            return bmp;
        }
    }

    private static android.content.SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(WallpaperPickerActivity.PREFS, Context.MODE_PRIVATE);
    }
}
