package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * The weather page, opened from Home's weather line: the chosen place's weather now, a week of days
 * from the chosen date (the forecast reaches 16 days ahead), and the selected day in full.
 * "Tukar lokasi" searches for another place; "Pilih tarikh" picks the day to look at.
 */
public class WeatherActivity extends Activity {

    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(Lang.wrap(base));
    }
    private static final int SHOWN = 7;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private float d;
    private Typeface outfit;
    private TextView title, now, nowDetail, dayDetail, status;
    private LinearLayout strip;
    private Weather.Report report;
    /** The first day in the strip, and the selected one (indexes into the report's days). */
    private int from, selected;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        d = getResources().getDisplayMetrics().density;
        outfit = getResources().getFont(R.font.outfit);
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(0xF0080A10);
        page.setPadding(px(64), px(36), px(64), px(24));

        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        title = text(34f, 300, 0xFFFFFFFF);
        top.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        TextView place = pill(getString(R.string.w_change_place));
        place.setOnClickListener(v -> askPlace());
        top.addView(place);
        TextView date = pill(getString(R.string.w_pick_date));
        date.setOnClickListener(v -> askDate());
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dl.setMarginStart(px(14));
        top.addView(date, dl);
        page.addView(top);

        now = text(54f, 300, 0xFFFFFFFF);
        LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        nl.topMargin = px(18);
        page.addView(now, nl);
        nowDetail = text(16f, 400, 0xCCFFFFFF);
        page.addView(nowDetail);

        strip = new LinearLayout(this);
        strip.setClipChildren(false);
        strip.setClipToPadding(false);
        strip.setPadding(px(4), px(10), px(4), px(10));
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sl.topMargin = px(22);
        page.addView(strip, sl);

        dayDetail = text(17f, 400, 0xE6FFFFFF);
        dayDetail.setLineSpacing(6 * d, 1f);
        LinearLayout.LayoutParams ddl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        ddl.topMargin = px(14);
        page.addView(dayDetail, ddl);

        View space = new View(this);
        page.addView(space, new LinearLayout.LayoutParams(1, 0, 1f));
        status = text(12f, 400, 0x80FFFFFF);
        status.setText("Data: Open-Meteo.com");
        page.addView(status);
        setContentView(page);

        report = Weather.cached(this);
        show();
        if (report == null || Weather.due(this)) load();
    }

    private void load() {
        status.setText(R.string.w_updating);
        new Thread(() -> {
            Weather.Report r = Weather.refresh(getApplicationContext());
            handler.post(() -> {
                if (isDestroyed()) return;
                status.setText(r == null ? getString(R.string.w_offline) : "Data: Open-Meteo.com");
                if (r != null) {
                    report = r;
                    show();
                }
            });
        }, "weather").start();
    }

    private void show() {
        title.setText(Weather.place(this));
        if (report == null) {
            now.setText("—");
            nowDetail.setText(R.string.w_loading);
            return;
        }
        now.setText(Weather.icon(report.code, report.day) + "  " + Math.round(report.temp) + "°C  "
                + Weather.text(this, report.code));
        nowDetail.setText(getString(R.string.w_now, Weather.temp(report.feels), report.humidity, (int) Math.round(report.wind)));
        selected = Math.min(selected, report.days.size() - 1);
        buildStrip(selected);
    }

    /** A week of day cards starting at {@link #from}, with {@code focus} selected. */
    private void buildStrip(int focus) {
        strip.removeAllViews();
        List<Weather.Day> days = report.days;
        from = Math.max(0, Math.min(from, days.size() - SHOWN));
        if (focus < from) from = focus;
        if (focus >= from + SHOWN) from = focus - SHOWN + 1;
        View focusView = null;
        for (int i = from; i < Math.min(days.size(), from + SHOWN); i++) {
            Weather.Day day = days.get(i);
            final int index = i;
            TextView card = text(16f, 400, 0xFFFFFFFF);
            card.setGravity(Gravity.CENTER);
            card.setLineSpacing(4 * d, 1f);
            card.setText(dayName(day.date, i) + "\n" + shortDate(day.date) + "\n"
                    + Weather.icon(day.code, true) + "\n" + Weather.temp(day.max) + " / " + Weather.temp(day.min)
                    + "\n💧 " + day.rainChance + "%");
            card.setPadding(px(8), px(14), px(8), px(14));
            card.setFocusable(true);
            card.setBackground(cardBg(false));
            card.setOnFocusChangeListener((v, has) -> {
                v.setBackground(cardBg(has));
                v.animate().scaleX(has ? 1.06f : 1f).scaleY(has ? 1.06f : 1f).setDuration(150).start();
                if (has) {
                    selected = index;
                    detail(day, index);
                }
            });
            card.setOnKeyListener((v, keyCode, event) -> {
                if (event.getAction() != android.view.KeyEvent.ACTION_DOWN) return false;
                // Past either end of the strip, the week slides by a day.
                if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_RIGHT && index == from + SHOWN - 1 && index + 1 < days.size()) {
                    from++;
                    buildStrip(index + 1);
                    return true;
                }
                if (keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT && index == from && index > 0) {
                    from--;
                    buildStrip(index - 1);
                    return true;
                }
                return false;
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            lp.setMarginEnd(i == from + SHOWN - 1 ? 0 : px(12));
            strip.addView(card, lp);
            if (i == focus) focusView = card;
        }
        if (focusView != null) {
            View f = focusView;
            f.post(f::requestFocus);
            detail(days.get(focus), focus);
        }
    }

    private void detail(Weather.Day day, int index) {
        String rain = getString(R.string.w_rain_chance, day.rainChance) + (day.rain > 0 ? String.format(Locale.ROOT, " (%.1f mm)", day.rain) : "");
        dayDetail.setText(fullDate(day.date, index) + "  \u00B7  " + Weather.text(this, day.code) + "\n"
                + getString(R.string.w_day_detail, Weather.temp(day.min), Weather.temp(day.max), rain,
                (int) Math.round(day.wind), (int) Math.round(day.uv), day.sunrise, day.sunset));
    }

    /** "Pilih tarikh": every day the forecast reaches, today first. */
    private void askDate() {
        if (report == null) return;
        String[] names = new String[report.days.size()];
        for (int i = 0; i < names.length; i++) names[i] = fullDate(report.days.get(i).date, i);
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(getString(R.string.w_pick_date_title, names.length))
                .setItems(names, (dlg, which) -> {
                    from = which;
                    selected = which;
                    buildStrip(which);
                })
                .show();
    }

    /** "Tukar lokasi": type a town, pick it from what Open-Meteo finds. */
    private void askPlace() {
        EditText input = new EditText(this);
        input.setHint(R.string.w_city_hint);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_WORDS);
        input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        AlertDialog dialog = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(R.string.w_change_place_title)
                .setView(input)
                .setPositiveButton(R.string.w_search, (dlg, w) -> search(input.getText().toString()))
                .setNeutralButton("Kulim", (dlg, w) -> choose(new Weather.Place("Kulim", "Kedah", 5.36499, 100.56177)))
                .setNegativeButton(R.string.cancel, null)
                .create();
        input.setOnEditorActionListener((v, action, e) -> {
            dialog.dismiss();
            search(input.getText().toString());
            return true;
        });
        dialog.show();
    }

    private void search(String name) {
        if (name.trim().isEmpty()) return;
        status.setText(getString(R.string.w_searching, name.trim()));
        new Thread(() -> {
            List<Weather.Place> found = Weather.search(name);
            handler.post(() -> {
                if (isDestroyed()) return;
                status.setText("Data: Open-Meteo.com");
                if (found.isEmpty()) {
                    Toast.makeText(this, getString(R.string.w_not_found, name.trim()), Toast.LENGTH_LONG).show();
                    return;
                }
                String[] names = new String[found.size()];
                for (int i = 0; i < names.length; i++) {
                    Weather.Place p = found.get(i);
                    names[i] = p.name + (p.detail.isEmpty() ? "" : "  —  " + p.detail);
                }
                new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                        .setTitle(R.string.w_pick_place)
                        .setItems(names, (dlg, which) -> choose(found.get(which)))
                        .show();
            });
        }, "weather-search").start();
    }

    private void choose(Weather.Place p) {
        Weather.setPlace(this, p);
        report = null;
        from = selected = 0;
        show();
        load();
    }

    /** A weekday's name in the launcher's language. */
    private String weekday(Date d) {
        return new SimpleDateFormat("EEEE", getResources().getConfiguration().getLocales().get(0)).format(d);
    }

    private static Date parse(String iso) {
        try {
            return new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).parse(iso);
        } catch (Exception e) {
            return new Date();
        }
    }

    private String dayName(String iso, int index) {
        if (index == 0) return getString(R.string.today);
        if (index == 1) return getString(R.string.tomorrow);
        return weekday(parse(iso));
    }

    private static String shortDate(String iso) {
        return new SimpleDateFormat("dd.MM", Locale.ROOT).format(parse(iso));
    }

    private String fullDate(String iso, int index) {
        Date d = parse(iso);
        String when = new SimpleDateFormat("dd.MM.yy", Locale.ROOT).format(d);
        return (index == 0 ? getString(R.string.today) + ", " : index == 1 ? getString(R.string.tomorrow) + ", " : "")
                + weekday(d) + " " + when;
    }

    private TextView text(float size, int weight, int color) {
        TextView t = new TextView(this);
        t.setTypeface(outfit);
        t.setFontVariationSettings("'wght' " + weight);
        t.setTextSize(size);
        t.setTextColor(color);
        return t;
    }

    private TextView pill(String label) {
        TextView t = text(16f, 500, 0xFFFFFFFF);
        t.setText(label);
        t.setPadding(px(20), px(10), px(20), px(10));
        t.setFocusable(true);
        t.setClickable(true);
        t.setBackground(cardBg(false));
        t.setOnFocusChangeListener((v, has) -> v.setBackground(cardBg(has)));
        return t;
    }

    private GradientDrawable cardBg(boolean focused) {
        GradientDrawable bg = new GradientDrawable();
        bg.setCornerRadius(16 * d);
        bg.setColor(focused ? 0x4DFFC46B : 0x1FFFFFFF);
        bg.setStroke(Math.round(focused ? 2.5f * d : 1.5f * d), focused ? 0xFFFFC46B : 0x33FFFFFF);
        return bg;
    }

    private int px(float dp) {
        return Math.round(dp * d);
    }
}
