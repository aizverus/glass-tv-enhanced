package com.gathin.tvhome;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/**
 * "Waktu Solat" in All apps: this week's or this month's prayer times from JAKIM (the year Home
 * already keeps), whether Home shows its prayer row, and the azan sound.
 */
public class PrayerActivity extends Activity {

    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(Lang.wrap(base));
    }
    private float d;
    private Typeface outfit;
    private LinearLayout pills, table;
    private String zone;
    private boolean month;
    private String[] head() {
        return new String[]{getString(R.string.p_date), "Hijri", "Subuh", "Syuruk", "Zohor", "Asar", "Maghrib", "Isyak"};
    }
    private static final float[] WEIGHTS = {1.5f, 1.9f, 1, 1, 1, 1, 1, 1};

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        d = getResources().getDisplayMetrics().density;
        outfit = getResources().getFont(R.font.outfit);
        zone = Config.load(this).prayerZone;

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(0xFF080A10);
        page.setPadding(px(56), px(30), px(56), 0);
        page.addView(text(getString(R.string.tile_prayer), 32f, 0xFFFFFFFF, 300));
        TextView sub = text(getString(R.string.p_zone, zone), 13f, 0x99FFFFFF, 400);
        sub.setPadding(0, px(2), 0, px(10));
        page.addView(sub);

        pills = new LinearLayout(this);
        pills.setClipChildren(false);
        pills.setClipToPadding(false);
        pills.setPadding(px(4), px(8), px(4), px(12));
        page.addView(pills);
        buildPills();

        page.addView(row(head(), 0x99FFFFFF, 500, false));
        ScrollView scroll = new ScrollView(this);
        table = new LinearLayout(this);
        table.setOrientation(LinearLayout.VERTICAL);
        table.setPadding(0, px(4), 0, px(40));
        scroll.addView(table);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(page);
        buildTable();
        pills.getChildAt(0).requestFocus();
    }

    private int px(float dp) {
        return Math.round(dp * d);
    }

    private TextView text(String s, float sp, int color, int weight) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTypeface(outfit);
        t.setFontVariationSettings("'wght' " + weight);
        t.setTextSize(sp);
        t.setTextColor(color);
        return t;
    }

    private GradientDrawable round(int fill, int stroke, float radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(radius * d);
        g.setStroke(px(1.5f), stroke);
        return g;
    }

    private void pill(String label, boolean lit, Runnable act) {
        TextView p = text(label, 15f, 0xFFFFFFFF, lit ? 600 : 400);
        p.setPadding(px(16), px(9), px(16), px(9));
        p.setFocusable(true);
        p.setClickable(true);
        int fill = lit ? 0x4DFFC46B : 0x26FFFFFF, stroke = lit ? 0xFFFFC46B : 0x4DFFFFFF;
        p.setBackground(round(fill, stroke, 22));
        p.setOnFocusChangeListener((v, has) -> {
            v.setBackground(has ? round(0xFFFFFFFF, 0xFFFFC46B, 22) : round(fill, stroke, 22));
            ((TextView) v).setTextColor(has ? 0xFF14110C : 0xFFFFFFFF);
            v.animate().scaleX(has ? 1.06f : 1f).scaleY(has ? 1.06f : 1f).setDuration(150).start();
        });
        p.setOnClickListener(v -> act.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMarginEnd(px(12));
        pills.addView(p, lp);
    }

    private void buildPills() {
        View had = getCurrentFocus();
        int at = had != null && had.getParent() == pills ? pills.indexOfChild(had) : 0;
        pills.removeAllViews();
        boolean shown = HomeWidgets.shown(this, HomeWidgets.PRAYER);
        pill(getString(shown ? R.string.show_on_home : R.string.hidden_from_home), shown, () -> {
            HomeWidgets.setShown(this, HomeWidgets.PRAYER, !HomeWidgets.shown(this, HomeWidgets.PRAYER));
            buildPills();
        });
        pill(getString(R.string.p_week), !month, () -> {
            month = false;
            buildPills();
            buildTable();
        });
        pill(getString(R.string.p_month), month, () -> {
            month = true;
            buildPills();
            buildTable();
        });
        pill(getString(R.string.menu_azan), false, () -> {
            startActivity(new Intent(this, MainActivity.class).putExtra(MainActivity.CHOOSE_AZAN, true)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP));
            finish();
        });
        if (at < pills.getChildCount()) pills.getChildAt(at).requestFocus();
    }

    private LinearLayout row(String[] cells, int color, int weight, boolean lit) {
        LinearLayout r = new LinearLayout(this);
        r.setPadding(px(14), px(9), px(14), px(9));
        for (int i = 0; i < cells.length; i++) {
            TextView t = text(cells[i], i < 2 ? 14f : 16f, color, weight);
            t.setGravity(i < 2 ? Gravity.START : Gravity.CENTER);
            r.addView(t, new LinearLayout.LayoutParams(0, -2, WEIGHTS[i]));
        }
        if (lit) r.setBackground(round(0x33FFC46B, 0xFFFFC46B, 12));
        return r;
    }

    private void buildTable() {
        table.removeAllViews();
        Calendar today = Calendar.getInstance(), day = (Calendar) today.clone();
        int days = 7;
        if (month) {
            day.set(Calendar.DAY_OF_MONTH, 1);
            days = day.getActualMaximum(Calendar.DAY_OF_MONTH);
        }
        boolean is24 = android.text.format.DateFormat.is24HourFormat(this);
        SimpleDateFormat date = new SimpleDateFormat("EEE dd.MM", getResources().getConfiguration().getLocales().get(0));
        View todayRow = null;
        for (int n = 0; n < days; n++, day.add(Calendar.DAY_OF_MONTH, 1)) {
            PrayerTimes.Day t = zone.isEmpty() ? null : PrayerTimes.find(this, zone, day);
            String[] cells = new String[8];
            cells[0] = date.format(day.getTime());
            cells[1] = t == null ? "" : PrayerTimes.hijri(t.hijri);
            for (int i = 0; i < 6; i++) cells[i + 2] = t == null ? "—" : PrayerTimes.format(t.minutes[i], is24);
            boolean isToday = day.get(Calendar.YEAR) == today.get(Calendar.YEAR)
                    && day.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR);
            LinearLayout r = row(cells, isToday ? 0xFFFFFFFF : 0xD9FFFFFF, isToday ? 600 : 400, isToday);
            // Rows can be selected, so a month scrolls with the remote.
            r.setFocusable(true);
            GradientDrawable base = isToday ? round(0x33FFC46B, 0xFFFFC46B, 12) : null;
            r.setOnFocusChangeListener((v, has) -> v.setBackground(has ? round(0x33FFFFFF, 0xFFFFFFFF, 12) : base));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.bottomMargin = px(3);
            table.addView(r, lp);
            if (isToday) todayRow = r;
        }
        if (todayRow != null && month) {
            View target = todayRow;
            table.post(() -> ((ScrollView) table.getParent()).smoothScrollTo(0, Math.max(0, target.getTop() - px(40))));
        }
    }
}
