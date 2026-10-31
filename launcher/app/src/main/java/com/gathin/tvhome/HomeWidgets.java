package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Which of Home's cards the family keeps on screen: the Premier League card, the prayer times and
 * the verse shelf. Each is hidden by holding OK on it (the verse from its page in All apps) and
 * comes back from its own tile in All apps.
 */
final class HomeWidgets {
    static final String EPL = "epl", PRAYER = "prayer", QUOTES = "quotes";

    private HomeWidgets() {}

    static boolean shown(Context ctx, String card) {
        return prefs(ctx).getBoolean(card, true);
    }

    static void setShown(Context ctx, String card, boolean on) {
        prefs(ctx).edit().putBoolean(card, on).apply();
    }

    private static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences("home_widgets", Context.MODE_PRIVATE);
    }
}
