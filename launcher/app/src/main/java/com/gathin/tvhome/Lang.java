package com.gathin.tvhome;

import android.content.Context;
import android.content.res.Configuration;

import java.util.Locale;

/**
 * The launcher's own language, chosen from the Language tile in All apps: Auto follows the TV's
 * language. Every screen of the launcher wraps its context with {@link #wrap}, so a choice applies
 * to the whole launcher without changing the TV's own setting.
 */
final class Lang {
    static final String[] CODES = {"", "en", "ms", "zh"};
    /** Each language in its own words, so anyone can find theirs. */
    static final String[] NAMES = {"Auto (TV)", "English", "Bahasa Melayu", "中文"};

    private Lang() {}

    static String code(Context ctx) {
        return ctx.getSharedPreferences("lang", Context.MODE_PRIVATE).getString("code", "");
    }

    static void set(Context ctx, String code) {
        ctx.getSharedPreferences("lang", Context.MODE_PRIVATE).edit().putString("code", code).commit();
    }

    static Context wrap(Context base) {
        String code = code(base);
        if (code.isEmpty()) return base;
        Locale locale = code.equals("zh") ? Locale.SIMPLIFIED_CHINESE : new Locale(code);
        Configuration cfg = new Configuration(base.getResources().getConfiguration());
        cfg.setLocale(locale);
        return base.createConfigurationContext(cfg);
    }
}
