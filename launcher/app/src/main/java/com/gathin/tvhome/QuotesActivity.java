package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;
import java.util.Set;

/**
 * "Wisdom" in All apps: whether Home shows its verse shelf, which kinds of words it picks
 * from (Quran, hadith, motivation, wisdom), and the words themselves, which can be deleted (OK on
 * one) or added (+ Tambah, typed on the TV).
 */
public class QuotesActivity extends Activity {

    @Override
    protected void attachBaseContext(android.content.Context base) {
        super.attachBaseContext(Lang.wrap(base));
    }
    private float d;
    private Typeface outfit;
    private LinearLayout list, chips;
    private TextView showPill;
    private List<QuoteStore.Item> items;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        d = getResources().getDisplayMetrics().density;
        outfit = getResources().getFont(R.font.outfit);
        items = QuoteStore.load(this);

        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.setBackgroundColor(0xFF080A10);
        page.setPadding(px(56), px(30), px(56), 0);

        TextView title = text(getString(R.string.tile_wisdom), 32f, 0xFFFFFFFF, 300);
        page.addView(title);
        TextView hint = text(getString(R.string.q_hint), 13f, 0x99FFFFFF, 400);
        hint.setPadding(0, px(2), 0, px(10));
        page.addView(hint);

        HorizontalScrollView bar = new HorizontalScrollView(this);
        bar.setHorizontalScrollBarEnabled(false);
        bar.setClipChildren(false);
        bar.setClipToPadding(false);
        chips = new LinearLayout(this);
        chips.setClipChildren(false);
        chips.setClipToPadding(false);
        chips.setPadding(px(4), px(8), px(4), px(8));
        bar.addView(chips);
        page.addView(bar);
        buildChips();

        ScrollView scroll = new ScrollView(this);
        scroll.setClipToPadding(false);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(px(4), px(8), px(4), px(40));
        scroll.addView(list);
        page.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(page);
        buildList();
        showPill.requestFocus();
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

    /** A pill in the top row: white when selected, a soft glass when not. */
    private TextView pill(String label, Runnable act) {
        TextView p = text(label, 15f, 0xFFFFFFFF, 400);
        p.setPadding(px(16), px(9), px(16), px(9));
        p.setFocusable(true);
        p.setClickable(true);
        p.setBackground(round(0x26FFFFFF, 0x4DFFFFFF, 22));
        p.setOnFocusChangeListener((v, has) -> {
            v.setBackground(has ? round(0xFFFFFFFF, 0xFFFFC46B, 22) : round(0x26FFFFFF, 0x4DFFFFFF, 22));
            ((TextView) v).setTextColor(has ? 0xFF14110C : 0xFFFFFFFF);
            v.animate().scaleX(has ? 1.06f : 1f).scaleY(has ? 1.06f : 1f).setDuration(150).start();
        });
        p.setOnClickListener(v -> act.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMarginEnd(px(12));
        chips.addView(p, lp);
        return p;
    }

    private void buildChips() {
        View had = getCurrentFocus();
        int focusAt = had != null && had.getParent() == chips ? chips.indexOfChild(had) : -1;
        chips.removeAllViews();
        boolean shown = HomeWidgets.shown(this, HomeWidgets.QUOTES);
        showPill = pill(getString(shown ? R.string.show_on_home : R.string.hidden_from_home), () -> {
            HomeWidgets.setShown(this, HomeWidgets.QUOTES, !HomeWidgets.shown(this, HomeWidgets.QUOTES));
            buildChips();
        });
        boolean pinnedNow = !QuoteStore.pinned(this).isEmpty();
        pill(pinnedNow ? getString(R.string.q_pinned) : "\u27F3 " + QuoteStore.intervalName(this), this::chooseInterval);
        pill(getString(R.string.q_add), this::chooseCategory);
        pill(getString(R.string.q_download), this::chooseDownload);
        Set<String> on = QuoteStore.enabled(this);
        for (int i = 0; i < QuoteStore.CATS.length; i++) {
            String cat = QuoteStore.CATS[i];
            int n = 0;
            for (QuoteStore.Item it : items) if (it.cat.equals(cat)) n++;
            boolean enabled = on.contains(cat);
            pill((enabled ? "✓ " : "✕ ") + QuoteStore.labels(this)[i] + "  " + n, () -> {
                QuoteStore.setEnabled(this, cat, !QuoteStore.enabled(this).contains(cat));
                buildChips();
            }).setAlpha(enabled ? 1f : 0.6f);
        }
        if (focusAt >= 0 && focusAt < chips.getChildCount()) chips.getChildAt(focusAt).requestFocus();
    }

    private void buildList() {
        list.removeAllViews();
        for (QuoteStore.Item it : items) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(px(18), px(12), px(18), px(12));
            row.setFocusable(true);
            row.setClickable(true);
            row.setBackground(round(0x14FFFFFF, 0x00000000, 14));
            row.setOnFocusChangeListener((v, has) -> v.setBackground(has ? round(0x33FFFFFF, 0xFFFFFFFF, 14)
                    : round(0x14FFFFFF, 0x00000000, 14)));
            boolean onHome = QuoteStore.key(it).equals(QuoteStore.pinned(this));
            TextView head = text((onHome ? getString(R.string.q_on_home) + "  \u00B7  " : "") + QuoteStore.label(this, it.cat).toUpperCase()
                    + (it.source.isEmpty() ? "" : "  \u00B7  " + it.source), 11.5f, 0xFFFFC46B, 500);
            row.addView(head);
            if (!it.arabic.isEmpty()) {
                TextView ar = text(it.arabic, 20f, 0xFFFFFFFF, 400);
                ar.setPadding(0, px(4), 0, 0);
                row.addView(ar);
            }
            TextView body = text(it.text, 15f, 0xE6FFFFFF, 400);
            body.setPadding(0, px(3), 0, 0);
            row.addView(body);
            row.setOnClickListener(v -> chooseFor(it));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.bottomMargin = px(8);
            list.addView(row, lp);
        }
    }

    /** OK on an entry: keep it on Home, or delete it. */
    private void chooseFor(QuoteStore.Item it) {
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(QuoteStore.label(this, it.cat))
                .setMessage(it.text)
                .setPositiveButton(R.string.q_use_home, (dlg, w) -> {
                    QuoteStore.pin(this, it);
                    QuoteStore.setEnabled(this, it.cat, true);
                    HomeWidgets.setShown(this, HomeWidgets.QUOTES, true);
                    Toast.makeText(this, R.string.q_shown_home, Toast.LENGTH_SHORT).show();
                    int at = items.indexOf(it);
                    buildChips();
                    buildList();
                    if (at >= 0 && at < list.getChildCount()) list.getChildAt(at).requestFocus();
                })
                .setNeutralButton(R.string.delete, (dlg, w) -> confirmDelete(it))
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    /** How often Home picks new words; choosing one also lets go of a picked entry. */
    private void chooseInterval() {
        long v = QuoteStore.interval(this);
        int checked = 0;
        for (int i = 0; i < QuoteStore.INTERVALS.length; i++) if (QuoteStore.INTERVALS[i] == v) checked = i;
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(R.string.q_random_title)
                .setSingleChoiceItems(QuoteStore.intervalNames(this), checked, (dlg, which) -> {
                    QuoteStore.setInterval(this, QuoteStore.INTERVALS[which]);
                    dlg.dismiss();
                    buildChips();
                    buildList();
                })
                .show();
    }

    private void chooseDownload() {
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(R.string.q_download_title)
                .setItems(QuoteStore.downloads(this), (dlg, which) -> {
                    Toast.makeText(this, R.string.q_downloading, Toast.LENGTH_SHORT).show();
                    new Thread(() -> {
                        int n = QuoteStore.download(getApplicationContext(), which);
                        runOnUiThread(() -> {
                            if (isDestroyed()) return;
                            Toast.makeText(this, n == 0 ? getString(R.string.q_none_new) : getString(R.string.q_added_n, n), Toast.LENGTH_LONG).show();
                            items = QuoteStore.load(this);
                            buildChips();
                            buildList();
                        });
                    }, "wisdom-download").start();
                })
                .show();
    }

    private void confirmDelete(QuoteStore.Item it) {
        AlertDialog box = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(R.string.q_delete_this)
                .setMessage(it.text)
                .setPositiveButton(R.string.delete, (dlg, w) -> {
                    int at = items.indexOf(it);
                    items.remove(it);
                    if (QuoteStore.key(it).equals(QuoteStore.pinned(this))) QuoteStore.pin(this, null);
                    QuoteStore.save(this, items);
                    buildChips();
                    buildList();
                    int next = Math.min(at, list.getChildCount() - 1);
                    if (next >= 0) list.getChildAt(next).requestFocus();
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
        // Batal is selected first, so a stray OK keeps the words.
        View cancel = box.getButton(AlertDialog.BUTTON_NEGATIVE);
        if (cancel != null) cancel.requestFocus();
    }

    private void chooseCategory() {
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(R.string.q_add_to)
                .setItems(QuoteStore.labels(this), (dlg, which) -> addTo(QuoteStore.CATS[which]))
                .show();
    }

    /** The words, where they are from, and (for a verse or hadith) the Arabic, typed on the TV. */
    private void addTo(String cat) {
        boolean verse = cat.equals("quran") || cat.equals("hadis");
        LinearLayout form = new LinearLayout(this);
        form.setOrientation(LinearLayout.VERTICAL);
        form.setPadding(px(22), px(8), px(22), 0);
        EditText words = field(form, getString(verse ? R.string.q_meaning : R.string.q_words));
        EditText from = field(form, getString(verse ? R.string.q_source_verse : R.string.q_source));
        EditText arabic = verse ? field(form, getString(R.string.q_arabic)) : null;
        AlertDialog box = new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(getString(R.string.q_add_title, QuoteStore.label(this, cat)))
                .setView(form)
                .setPositiveButton(R.string.save, null)
                .setNegativeButton(R.string.cancel, null)
                .show();
        box.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            if (words.getText().toString().trim().isEmpty()) {
                Toast.makeText(this, R.string.q_write_first, Toast.LENGTH_SHORT).show();
                return;
            }
            QuoteStore.Item it = new QuoteStore.Item(cat, arabic == null ? "" : arabic.getText().toString(),
                    words.getText().toString(), from.getText().toString());
            items.add(0, it);
            QuoteStore.save(this, items);
            QuoteStore.setEnabled(this, cat, true);
            box.dismiss();
            buildChips();
            buildList();
            Toast.makeText(this, getString(R.string.q_added_to, QuoteStore.label(this, cat)), Toast.LENGTH_SHORT).show();
        });
    }

    private EditText field(LinearLayout form, String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setSingleLine(false);
        e.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES | InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        e.setTextSize(16f);
        form.addView(e, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return e;
    }
}
