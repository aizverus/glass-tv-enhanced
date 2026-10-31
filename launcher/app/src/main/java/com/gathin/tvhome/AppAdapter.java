package com.gathin.tvhome;

import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * App tiles. The first {@link MainActivity#COLUMNS} positions are the pinned dock (favourites, fixed order),
 * then the module cards the owner chose to show (by label), the rest is alphabetical. Once anyone has
 * moved a tile (hold OK, then Move), the order they made is kept instead, and apps installed later
 * join at the end.
 */
public class AppAdapter extends RecyclerView.Adapter<AppAdapter.Holder> {

    interface TileListener {
        void onTileFocused(int position, View card);

        /** Called for a D-pad direction key on a tile. Return true when handled (focus stays put). */
        boolean onTileVerticalKey(int position, int keyCode);

        /** OK was held on a tile. Return false to open the app's info page, as before. */
        boolean onTileHeld(int position, View card);
    }

    static final class Entry {
        static final int APP = 0, HOME_SETTINGS = 1, CARD = 2, MORE = 3, ADD = 4;

        final int kind;
        final String label;
        final ComponentName component;
        final String pkg;
        final Drawable banner;
        final Drawable icon;
        /** Text under the tile when it is focused in the grid: the label, plus the caption for a card. */
        final String name;
        /** Ambient glow colour; 0 until sampled from the banner, or the card's accent from the start. */
        int ambient;
        /** The banner with its plain background made see-through, for the glass look when selected. */
        Bitmap glass;

        Entry(String label, ComponentName component, Drawable banner, Drawable icon) {
            this(APP, label, component, banner, icon, null, 0);
        }

        Entry(int kind, String label, ComponentName component, Drawable banner, Drawable icon, String caption, int accent) {
            this.kind = kind;
            this.label = label;
            this.component = component;
            this.pkg = component.getPackageName();
            this.banner = banner;
            this.icon = icon;
            this.name = caption == null ? label : label + " \u00B7 " + caption;
            this.ambient = accent;
        }
    }

    static final float FOCUS_SCALE = 1.12f;
    /** The dock apps either side of the selected one, a little raised, like the macOS Dock (dock_magnify). */
    static final float NEIGHBOUR_SCALE = 1.05f;
    /** Shared by every tile's zoom, so a focus move makes no new objects. */
    private static final DecelerateInterpolator ZOOM_REDUCED = new DecelerateInterpolator();
    private static final OvershootInterpolator ZOOM_IN = new OvershootInterpolator(1.6f);
    private static final OvershootInterpolator ZOOM_OUT = new OvershootInterpolator(0f);
    /** Design size of a tile (dp); tiles shrink from here when the columns don't fit, see tileWidthPx. */
    static final float DESIGN_W_DP = 180f, DESIGN_H_DP = 101f;

    /**
     * Tile width for a grid of this inner width: the cell minus room for the focus zoom and the glass
     * highlight, capped at the design size so 4 columns on a 1080p TV look exactly as before.
     */
    /** Most tiles on Home (max_tiles); past it the last slot is "More", which lists every app. 0: no limit. */
    static int MAX_TILES = 0;

    /** Glass tiles (tile_sheen): a soft shine across the top, a fine bright rim and livelier colour. */
    static boolean SHEEN = false;
    /** The selected app's banner shows its logo in colour on glass: its plain background turns see-through. */
    static boolean GLASS_ICON = false;
    /** The selected app breathes: a slow, slight swell of its zoom. */
    static boolean PULSE = false;

    /**
     * The banner drawn at the card's size with its plain background (the colour of its corners) turned
     * to glass, the logo kept in full colour: a light background becomes frosted white, a dark or
     * coloured one see-through. Edges blend over a soft band, so the logo's antialiasing stays clean.
     */
    static Bitmap glassBanner(Drawable banner, int w, int h) {
        if (banner == null || w <= 0 || h <= 0) return null;
        Bitmap b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(b);
        banner.setBounds(0, 0, w, h);
        banner.draw(c);
        int[] px = new int[w * h];
        b.getPixels(px, 0, w, 0, 0, w, h);
        int[] corners = {px[0], px[w - 1], px[(h - 1) * w], px[h * w - 1]};
        int r0 = 0, g0 = 0, b0 = 0;
        for (int k : corners) {
            r0 += Color.red(k);
            g0 += Color.green(k);
            b0 += Color.blue(k);
        }
        r0 /= 4;
        g0 /= 4;
        b0 /= 4;
        boolean light = (0.299f * r0 + 0.587f * g0 + 0.114f * b0) > 190f;
        // Frosted white for a light background; the background's own colour, thinner, otherwise.
        int glassRgb = light ? 0xFFFFFF : (r0 << 16) | (g0 << 8) | b0;
        float glassAlpha = light ? 0.55f : 0.35f;
        for (int i = 0; i < px.length; i++) {
            int p = px[i];
            int dr = Color.red(p) - r0, dg = Color.green(p) - g0, db = Color.blue(p) - b0;
            float dist = (float) Math.sqrt(dr * dr + dg * dg + db * db);
            if (dist >= 90f) continue;   // the logo
            float f = dist <= 36f ? 0f : (dist - 36f) / 54f;   // 0 = background, 1 = logo
            int bg = (Math.round(255 * glassAlpha) << 24) | glassRgb;
            px[i] = blend(bg, p, f);
        }
        b.setPixels(px, 0, w, 0, 0, w, h);
        return b;
    }

    private static android.graphics.ColorMatrixColorFilter saturation(float s) {
        android.graphics.ColorMatrix m = new android.graphics.ColorMatrix();
        m.setSaturation(s);
        return new android.graphics.ColorMatrixColorFilter(m);
    }

    /** The shine of a glass tile, drawn over the artwork and rounded like the card. */
    static final class Sheen extends Drawable {
        private final float density;
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG), rim = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final android.graphics.RectF box = new android.graphics.RectF();

        Sheen(float density) {
            this.density = density;
            rim.setStyle(Paint.Style.STROKE);
            rim.setStrokeWidth(1.2f * density);
        }

        @Override
        public void draw(@NonNull android.graphics.Canvas c) {
            android.graphics.Rect b = getBounds();
            if (b.isEmpty()) return;
            float r = cornerRadius(b.width(), density);
            box.set(b);
            fill.setShader(new android.graphics.LinearGradient(0, b.top, 0, b.top + b.height() * 0.55f,
                    0x40FFFFFF, 0x00FFFFFF, android.graphics.Shader.TileMode.CLAMP));
            c.drawRoundRect(box, r, r, fill);
            rim.setShader(new android.graphics.LinearGradient(0, b.top, 0, b.bottom,
                    0x80FFFFFF, 0x1AFFFFFF, android.graphics.Shader.TileMode.CLAMP));
            float h = rim.getStrokeWidth() / 2f;
            box.inset(h, h);
            c.drawRoundRect(box, r - h, r - h, rim);
        }

        @Override
        public void setAlpha(int alpha) {}

        @Override
        public void setColorFilter(android.graphics.ColorFilter cf) {}

        @Override
        public int getOpacity() {
            return android.graphics.PixelFormat.TRANSLUCENT;
        }
    }

    /** Mixes two ARGB colours: {@code f} of the way from {@code a} to {@code b}. */
    private static int blend(int a, int b, float f) {
        return Color.argb(Math.round(Color.alpha(a) + (Color.alpha(b) - Color.alpha(a)) * f),
                Math.round(Color.red(a) + (Color.red(b) - Color.red(a)) * f),
                Math.round(Color.green(a) + (Color.green(b) - Color.green(a)) * f),
                Math.round(Color.blue(a) + (Color.blue(b) - Color.blue(a)) * f));
    }

    /** Share of the room each tile fills, 0.6 to 1 (tile_size_percent); smaller tiles leave wider gaps. */
    static float TILE_SCALE = 1f;

    static int tileWidthPx(int innerWidthPx, int columns, float density) {
        int design = Math.round(DESIGN_W_DP * density);
        if (innerWidthPx <= 0 || columns <= 0) return Math.round(design * TILE_SCALE);
        int cell = innerWidthPx / columns;
        int fit = (int) ((cell - 12 * density) / FOCUS_SCALE);
        return Math.max(Math.round(80 * density), Math.round(Math.min(design, fit) * TILE_SCALE));
    }

    static int tileHeightPx(int tileWidthPx) {
        return Math.round(tileWidthPx * DESIGN_H_DP / DESIGN_W_DP);
    }

    /** Height of one grid item: the tile, the name label under it and the item's own padding. */
    static int itemHeightPx(int tileHeightPx, float density) {
        return tileHeightPx + Math.round(45 * density);
    }

    private final Context ctx;
    private final TileListener listener;
    private final int dockSize;
    /** Dock order (config.xml dock_apps, or a theme's config.json). */
    private final List<String> favourites;
    /** Never shown as tiles (res/values/config.xml, hidden_apps). */
    private final Set<String> hidden;
    private final boolean showHomeSettings;
    /** Calmer motion for weak GPUs (config.xml, reduced_motion): no overshoot, shorter fades. */
    private final boolean reducedMotion;
    private final List<Entry> items = new ArrayList<>();
    /** The order someone arranged the tiles in, by key (see {@link #keyAt}); empty until they do. */
    private final List<String> arranged = new ArrayList<>();
    private static final String ORDER_PREFS = "tiles", ORDER_KEY = "order";
    private static final String HIDDEN_KEY = "off_home", ADDED_KEY = "added", REV_KEY = "rev";
    private int builtRev = -1;

    /** Takes an app off Home; it stays in All apps. */
    static void removeFromHome(Context ctx, ComponentName c) {
        SharedPreferences p = ctx.getSharedPreferences(ORDER_PREFS, Context.MODE_PRIVATE);
        Set<String> off = new HashSet<>(p.getStringSet(HIDDEN_KEY, Collections.emptySet()));
        off.add(c.flattenToShortString());
        List<String> added = new ArrayList<>();
        for (String s : p.getString(ADDED_KEY, "").split("\n")) if (!s.isEmpty() && !s.equals(c.flattenToShortString())) added.add(s);
        p.edit().putStringSet(HIDDEN_KEY, off).putString(ADDED_KEY, android.text.TextUtils.join("\n", added)).putInt(REV_KEY, p.getInt(REV_KEY, 0) + 1).apply();
    }

    /** Puts an app on Home, in front of "More": after the dock when no order was arranged, else at the end of Home. */
    static void addToHome(Context ctx, ComponentName c) {
        SharedPreferences p = ctx.getSharedPreferences(ORDER_PREFS, Context.MODE_PRIVATE);
        String key = c.flattenToShortString();
        Set<String> off = new HashSet<>(p.getStringSet(HIDDEN_KEY, Collections.emptySet()));
        off.remove(key);
        SharedPreferences.Editor e = p.edit().putStringSet(HIDDEN_KEY, off);
        String order = p.getString(ORDER_KEY, "");
        if (!order.isEmpty()) {
            List<String> list = new ArrayList<>(Arrays.asList(order.split("\n")));
            list.remove(key);
            int at = MAX_TILES > 1 ? Math.min(list.size(), MAX_TILES - 2) : list.size();
            list.add(at, key);
            e.putString(ORDER_KEY, android.text.TextUtils.join("\n", list));
        }
        // Newest first; reload() keeps these in sight in front of "More" whatever the order says.
        List<String> added = new ArrayList<>();
        for (String s : p.getString(ADDED_KEY, "").split("\n")) if (!s.isEmpty() && !s.equals(key)) added.add(s);
        added.add(0, key);
        e.putString(ADDED_KEY, android.text.TextUtils.join("\n", added));
        e.putInt(REV_KEY, p.getInt(REV_KEY, 0) + 1).apply();
    }

    /** Whether the app can be uninstalled: not part of the TV's system image. */
    static boolean removable(Context ctx, String pkg) {
        try {
            int flags = ctx.getPackageManager().getApplicationInfo(pkg, 0).flags;
            return (flags & android.content.pm.ApplicationInfo.FLAG_SYSTEM) == 0;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    /** Android's own uninstall confirmation for the package. */
    static void uninstall(Context ctx, String pkg) {
        Intent i = new Intent(Intent.ACTION_DELETE, Uri.parse("package:" + pkg)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            ctx.startActivity(i);
        } catch (ActivityNotFoundException ignored) {
        }
    }

    ComponentName componentAt(int position) {
        return position >= 0 && position < items.size() ? items.get(position).component : null;
    }

    boolean isApp(int position) {
        return position >= 0 && position < items.size() && items.get(position).kind == Entry.APP;
    }
    /** Rebind payload: only whether the tile's name shows, which depends on being in the dock. */
    private static final Object DOCK_CHANGED = new Object();

    AppAdapter(Context ctx, TileListener listener, int dockSize, Config cfg) {
        this.ctx = ctx;
        this.listener = listener;
        this.dockSize = dockSize;
        this.favourites = cfg.dockApps;
        this.hidden = new HashSet<>(cfg.hiddenApps);
        this.showHomeSettings = cfg.showHomeSettingsShortcut;
        this.reducedMotion = cfg.reducedMotion;
        // Tiles keep their identity across reload(), so a focused tile stays on its app instead of
        // its view being rebound to whatever app now sits in that slot.
        setHasStableIds(true);
        String saved = ctx.getSharedPreferences(ORDER_PREFS, Context.MODE_PRIVATE).getString(ORDER_KEY, "");
        if (!saved.isEmpty()) arranged.addAll(Arrays.asList(saved.split("\n")));
    }

    /**
     * Rebuilds the tiles. A shown module card stands in for every tile of its package (same component,
     * so focus follows it); hidden_apps wins over a card, and a card's package in dock_apps takes that slot.
     */
    /** What the tiles were last built from; the same list next time means there is nothing to redo. */
    private String builtFrom;
    /**
     * The app tiles of the last build, by component and install path: a rebuild (one app updated,
     * installed or removed) loads only what changed, and the rest keep their artwork and sampled glow.
     */
    private Map<String, Entry> builtApps = new HashMap<>();

    /**
     * Rebuilds the tiles, unless the launchable apps, their installed versions and the cards are
     * exactly what the tiles were last built from. Loading every banner, icon and label is most of
     * what coming back to Home costs, it runs before Home can draw, and almost every return finds
     * nothing changed.
     *
     * @return whether the tiles were rebuilt
     */
    boolean reload(List<Cards.Card> cards) {
        PackageManager pm = ctx.getPackageManager();
        Intent probe = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER);
        List<ResolveInfo> found = new ArrayList<>(pm.queryIntentActivities(probe, 0));
        // A phone-only app named in dock_apps (e.g. the phone Play Store some boxes ship) has no leanback
        // entry; take its ordinary launcher activity instead. Apps not named stay hidden.
        Set<String> listed = new HashSet<>();
        for (ResolveInfo ri : found) if (ri.activityInfo != null) listed.add(ri.activityInfo.packageName);
        for (String pkg : favourites) {
            if (listed.contains(pkg)) continue;
            Intent phone = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(pkg);
            List<ResolveInfo> more = pm.queryIntentActivities(phone, 0);
            if (!more.isEmpty() && more.get(0).activityInfo != null) {
                found.add(more.get(0));
                listed.add(pkg);
            }
        }
        // The install path moves when an app is updated, so it stands in for the version: an update
        // that brought a new banner still gets a rebuild.
        List<String> parts = new ArrayList<>();
        for (ResolveInfo ri : found) {
            if (ri.activityInfo == null) continue;
            parts.add(ri.activityInfo.packageName + '/' + ri.activityInfo.name + '@'
                    + ri.activityInfo.applicationInfo.sourceDir);
        }
        for (Cards.Card c : cards) {
            parts.add('#' + c.pkg + '/' + c.component.flattenToShortString() + '|' + c.label + '|'
                    + c.caption + '|' + c.accent);
        }
        Collections.sort(parts);
        // Apps taken off or put back on Home (All apps, or the hold-OK menu) change the build too.
        SharedPreferences tilePrefs = ctx.getSharedPreferences(ORDER_PREFS, Context.MODE_PRIVATE);
        int rev = tilePrefs.getInt(REV_KEY, 0);
        if (rev != builtRev) {
            arranged.clear();
            String saved = tilePrefs.getString(ORDER_KEY, "");
            if (!saved.isEmpty()) arranged.addAll(Arrays.asList(saved.split("\n")));
        }
        Set<String> offHome = new HashSet<>(tilePrefs.getStringSet(HIDDEN_KEY, Collections.emptySet()));
        List<String> added = Arrays.asList(tilePrefs.getString(ADDED_KEY, "").split("\n"));
        parts.add("#rev" + rev);
        String signature = android.text.TextUtils.join(";", parts);
        if (signature.equals(builtFrom) && !items.isEmpty()) return false;
        builtFrom = signature;
        builtRev = rev;
        Map<String, Cards.Card> cardByPkg = new HashMap<>();
        for (Cards.Card c : cards) cardByPkg.put(c.pkg, c);
        List<Entry> fresh = new ArrayList<>();
        Map<String, Entry> apps = new HashMap<>();
        for (ResolveInfo ri : found) {
            if (ri.activityInfo == null) continue;
            String pkg = ri.activityInfo.packageName;
            if (pkg.equals(ctx.getPackageName()) || hidden.contains(pkg)) continue;
            ComponentName component = new ComponentName(pkg, ri.activityInfo.name);
            if (offHome.contains(component.flattenToShortString())) continue;   // still in All apps
            Cards.Card card = cardByPkg.get(pkg);
            if (card != null && !card.component.equals(component)) continue;   // the card stands in for this tile
            String key = component.flattenToShortString() + '@' + ri.activityInfo.applicationInfo.sourceDir;
            Entry built = card == null ? builtApps.get(key) : null;
            if (built != null) {
                fresh.add(built);
                apps.put(key, built);
                continue;
            }
            Drawable banner = loadBanner(pm, ri);
            // The icon only shows on a tile without a banner.
            Drawable icon = banner == null ? ri.loadIcon(pm) : null;
            if (card != null) {
                fresh.add(new Entry(Entry.CARD, card.label, component, banner, icon, card.caption, card.accent));
            } else {
                // Same cleaner as the card prompt: an app label with a bidi override or line separator must not
                // redraw the tile name as something else (#117).
                Entry entry = new Entry(Cards.cleanLabel(String.valueOf(ri.loadLabel(pm)), pkg), component, banner, icon);
                fresh.add(entry);
                apps.put(key, entry);
            }
        }
        builtApps = apps;
        Map<String, Integer> place = new HashMap<>();
        for (int i = 0; i < arranged.size(); i++) place.put(arranged.get(i), i);
        Collections.sort(fresh, (a, b) -> {
            // The order someone arranged comes first; apps it has not seen yet go after it.
            Integer pa = place.get(a.component.flattenToShortString()), pb = place.get(b.component.flattenToShortString());
            if (pa != null || pb != null) {
                if (pa == null) return 1;
                if (pb == null) return -1;
                return Integer.compare(pa, pb);
            }
            int fa = favourites.indexOf(a.pkg), fb = favourites.indexOf(b.pkg);
            if (fa >= 0 || fb >= 0) {
                if (fa < 0) return 1;
                if (fb < 0) return -1;
                return Integer.compare(fa, fb);
            }
            // Apps added to Home from All apps come right after the dock apps.
            int aa = added.indexOf(a.component.flattenToShortString()), ab = added.indexOf(b.component.flattenToShortString());
            if (aa >= 0 || ab >= 0) {
                if (aa < 0) return 1;
                if (ab < 0) return -1;
                return Integer.compare(aa, ab);
            }
            // Cards come right after the configured dock apps, before the alphabetical grid.
            boolean ca = a.kind == Entry.CARD, cb = b.kind == Entry.CARD;
            if (ca != cb) return ca ? -1 : 1;
            return a.label.compareToIgnoreCase(b.label);
        });
        // Optional "Default home" tile (config.xml), only when this TV actually has a home-screen picker.
        if (showHomeSettings
                && pm.resolveActivity(new Intent(Settings.ACTION_HOME_SETTINGS), 0) != null) {
            fresh.add(new Entry(Entry.HOME_SETTINGS, ctx.getString(R.string.home_settings),
                    new ComponentName(ctx, HomeSettingsActivity.class), null,
                    ctx.getDrawable(R.drawable.ic_home_settings), null, 0));
        }
        // No "Add" box on Home (the family asked for it gone): apps go back on Home from All apps'
        // hold-OK menu, "Add to Home".
        boolean addBox = false;
        // Two rows on Home: the rest, and every app installed later, wait behind "More".
        int slots = MAX_TILES - (addBox ? 1 : 0);   // what the apps may fill when no "More" is needed
        boolean more = MAX_TILES > 0 && fresh.size() > slots;
        if (more) {
            int keep = Math.max(0, slots - 1);
            // An app put on Home from All apps that sorted past the cut (it was behind "More", or the
            // arranged order puts it there) moves to the last places before "More", newest last.
            List<Entry> promote = new ArrayList<>();
            for (String k : added) {
                if (k.isEmpty() || promote.size() >= keep - dockSize) continue;
                for (int i = keep; i < fresh.size(); i++) {
                    if (fresh.get(i).component.flattenToShortString().equals(k)) {
                        promote.add(0, fresh.remove(i));
                        break;
                    }
                }
            }
            fresh.addAll(Math.max(dockSize, keep - promote.size()), promote);
            fresh = new ArrayList<>(fresh.subList(0, keep));
        }
        if (addBox) {
            fresh.add(new Entry(Entry.ADD, "Add", new ComponentName(ctx, AllAppsActivity.class.getName() + ".Add"),
                    null, ctx.getDrawable(R.drawable.ic_add), null, 0xFF8FE3B0));
        }
        if (more) {
            fresh.add(new Entry(Entry.MORE, ctx.getString(R.string.more_apps),
                    new ComponentName(ctx, AllAppsActivity.class), null,
                    ctx.getDrawable(R.drawable.ic_more), null, 0xFF8FA8FF));
        }
        items.clear();
        items.addAll(fresh);
        notifyDataSetChanged();
        return true;
    }

    private Drawable loadBanner(PackageManager pm, ResolveInfo ri) {
        int id = ri.activityInfo.banner;
        if (id == 0) id = ri.activityInfo.applicationInfo.banner;
        if (id != 0) {
            try {
                Resources resources = pm.getResourcesForApplication(ri.activityInfo.applicationInfo);
                // Keep at least xhdpi detail rather than downsampling artwork before the focus zoom.
                int density = Math.max(DisplayMetrics.DENSITY_XHIGH, ctx.getResources().getDisplayMetrics().densityDpi);
                return resources.getDrawableForDensity(id, density, null);
            } catch (PackageManager.NameNotFoundException | Resources.NotFoundException ignored) {
            }
        }
        return ri.activityInfo.loadBanner(pm);
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_app, parent, false);
        float density = parent.getResources().getDisplayMetrics().density;
        int width = parent.getWidth() > 0 ? parent.getWidth() : parent.getMeasuredWidth();
        int inner = width - parent.getPaddingLeft() - parent.getPaddingRight();
        int w = tileWidthPx(inner, dockSize, density), h = tileHeightPx(w);
        View card = v.findViewById(R.id.card);
        ViewGroup.LayoutParams cardLp = card.getLayoutParams();
        cardLp.width = w;
        cardLp.height = h;
        card.setLayoutParams(cardLp);
        View name = v.findViewById(R.id.name);
        ViewGroup.MarginLayoutParams nameLp = (ViewGroup.MarginLayoutParams) name.getLayoutParams();
        nameLp.width = w;
        nameLp.topMargin = h + Math.round(7 * density);
        name.setLayoutParams(nameLp);
        return new Holder(v, listener, reducedMotion);
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        Entry e = items.get(position);
        h.entry = e;
        h.name.setText(e.name);
        h.name.setVisibility(position < dockSize ? View.INVISIBLE : View.VISIBLE);   // the dock has no labels
        boolean focused = h.card.isFocused();   // a rebind after reload() can hit the focused tile
        h.name.setAlpha(focused ? 1f : 0f);
        h.card.setScaleX(focused ? FOCUS_SCALE : 1f);
        h.card.setScaleY(focused ? FOCUS_SCALE : 1f);
        if (e.banner != null) {
            h.banner.setImageDrawable(e.banner);
            h.banner.setVisibility(View.VISIBLE);
            h.icon.setVisibility(View.GONE);
            h.label.setVisibility(View.GONE);
        } else {
            h.banner.setImageDrawable(null);
            h.banner.setVisibility(View.GONE);
            h.icon.setImageDrawable(e.icon);
            h.icon.setVisibility(View.VISIBLE);
            h.label.setText(e.label);
            h.label.setVisibility(View.VISIBLE);
        }
        h.card.setOnClickListener(v -> launch(e));
        h.card.setOnLongClickListener(v -> {
            int pos = h.getBindingAdapterPosition();
            if (listener != null && pos != RecyclerView.NO_POSITION && listener.onTileHeld(pos, v)) return true;
            openAppInfo(e);
            return true;
        });
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position, @NonNull List<Object> payloads) {
        if (payloads.size() == 1 && payloads.get(0) == DOCK_CHANGED) {
            h.name.setVisibility(position < dockSize ? View.INVISIBLE : View.VISIBLE);
            return;
        }
        onBindViewHolder(h, position);
    }

    /** Android's page for the app at {@code position}: storage, permissions, uninstall. */
    void openAppInfo(int position) {
        if (position >= 0 && position < items.size()) openAppInfo(items.get(position));
    }

    private void openAppInfo(Entry e) {
        Intent i = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + e.pkg));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        try {
            ctx.startActivity(i);
        } catch (ActivityNotFoundException ignored) {
        }
    }

    /** Whether the tile at {@code position} can be moved: every app can, the Default home tile cannot. */
    boolean canMove(int position) {
        return position >= 0 && position < items.size() && items.get(position).kind != Entry.HOME_SETTINGS
                && items.get(position).kind != Entry.MORE && items.get(position).kind != Entry.ADD;
    }

    /** The last position a moved tile can take: before the Default home tile, which stays last. */
    int lastMovable() {
        int last = items.size() - 1;
        while (last >= 0 && (items.get(last).kind == Entry.HOME_SETTINGS || items.get(last).kind == Entry.MORE
                || items.get(last).kind == Entry.ADD)) last--;
        return last;
    }

    /**
     * Moves the tile at {@code from} to {@code to}, the tiles between shifting to make room, and keeps
     * the new order. Returns false when it cannot go there.
     */
    boolean move(int from, int to) {
        if (from == to || !canMove(from) || to < 0 || to > lastMovable()) return false;
        Entry e = items.remove(from);
        items.add(to, e);
        notifyItemMoved(from, to);
        // A tile crossing into or out of the dock shows or hides its name.
        int lo = Math.min(from, to), hi = Math.max(from, to);
        if (lo < dockSize && hi >= dockSize) notifyItemRangeChanged(lo, hi - lo + 1, DOCK_CHANGED);
        arranged.clear();
        for (Entry x : items) {
            if (x.kind != Entry.HOME_SETTINGS && x.kind != Entry.MORE && x.kind != Entry.ADD) {
                arranged.add(x.component.flattenToShortString());
            }
        }
        ctx.getSharedPreferences(ORDER_PREFS, Context.MODE_PRIVATE).edit()
                .putString(ORDER_KEY, android.text.TextUtils.join("\n", arranged)).apply();
        return true;
    }

    /** Opens the tile's activity by explicit component, with no extras: a card can name nothing else. */
    private void launch(Entry e) {
        if (e.kind == Entry.MORE) {
            ctx.startActivity(new Intent(ctx, AllAppsActivity.class));
            return;
        }
        if (e.kind == Entry.ADD) {
            ctx.startActivity(new Intent(ctx, AllAppsActivity.class).putExtra(AllAppsActivity.PICK, true));
            return;
        }
        if (e.kind == Entry.HOME_SETTINGS) {
            ctx.startActivity(new Intent(ctx, HomeSettingsActivity.class)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return;
        }
        Intent i = new Intent(Intent.ACTION_MAIN)
                .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                .setComponent(e.component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        try {
            ctx.startActivity(i);
        } catch (Exception ex) {
            Toast.makeText(ctx, ctx.getString(R.string.cant_open, e.label), Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    @Override
    public long getItemId(int position) {
        return items.get(position).component.flattenToShortString().hashCode();
    }

    /** Stable key for the app at a position (its component), or null. */
    String keyAt(int position) {
        return position >= 0 && position < items.size() ? items.get(position).component.flattenToShortString() : null;
    }

    /** Position of the app with this key, or -1 when it is gone. */
    int positionOf(String key) {
        if (key == null) return -1;
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).component.flattenToShortString().equals(key)) return i;
        }
        return -1;
    }

    String labelAt(int position) {
        return position >= 0 && position < items.size() ? items.get(position).label : "";
    }

    /** A vivid colour sampled from the app's banner (or icon), cached per entry; a card's accent when it set one. */
    int ambientColorAt(int position) {
        if (position < 0 || position >= items.size()) return 0xFF444444;
        Entry e = items.get(position);
        if (e.ambient == 0) e.ambient = vividColor(e.banner != null ? e.banner : e.icon);
        return e.ambient;
    }

    private static int vividColor(Drawable live) {
        if (live == null) return 0xFF444444;
        // Draw a private copy: changing the bounds of the drawable an ImageView shows would blank the tile.
        Drawable d = live.getConstantState() != null ? live.getConstantState().newDrawable().mutate() : live;
        Rect saved = new Rect(d.getBounds());
        int n = 12;
        Bitmap bmp = Bitmap.createBitmap(n, n, Bitmap.Config.ARGB_8888);
        Canvas c = new Canvas(bmp);
        d.setBounds(0, 0, n, n);
        d.draw(c);
        d.setBounds(saved);
        float[] hsv = new float[3];
        int best = 0xFF444444;
        float bestScore = -1f;
        long r = 0, g = 0, b = 0;
        for (int y = 0; y < n; y++) {
            for (int x = 0; x < n; x++) {
                int p = bmp.getPixel(x, y);
                r += Color.red(p);
                g += Color.green(p);
                b += Color.blue(p);
                Color.colorToHSV(p, hsv);
                float score = hsv[1] * Math.min(1f, hsv[2] * 1.4f);   // saturated and not too dark
                if (score > bestScore) {
                    bestScore = score;
                    best = p;
                }
            }
        }
        bmp.recycle();
        if (bestScore < 0.25f) {   // nearly monochrome banner (e.g. white): use its average, lifted
            int avg = Color.rgb((int) (r / (n * n)), (int) (g / (n * n)), (int) (b / (n * n)));
            Color.colorToHSV(avg, hsv);
            hsv[2] = Math.max(hsv[2], 0.55f);
            return Color.HSVToColor(hsv);
        }
        Color.colorToHSV(best, hsv);
        hsv[1] = Math.min(1f, hsv[1] * 1.1f);
        hsv[2] = Math.max(0.5f, hsv[2]);
        return Color.HSVToColor(hsv);
    }

    /** Corner radius for a tile of this width: 16dp at the design width, proportionally less when smaller. */
    static float cornerRadius(int tileWidthPx, float density) {
        return 16f * density * Math.min(1f, tileWidthPx / (DESIGN_W_DP * density));
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final View card;
        final ImageView banner;
        final ImageView icon;
        final TextView label;
        final TextView name;
        final boolean reducedMotion;
        private boolean muted;
        Entry entry;
        private android.animation.ValueAnimator pulse;

        /** On focus: the banner's logo on glass (its background see-through, the glass behind showing). */
        private void glassLook(boolean on) {
            if (!GLASS_ICON || entry == null || entry.banner == null) return;
            if (on) {
                if (entry.glass == null) entry.glass = glassBanner(entry.banner, card.getWidth(), card.getHeight());
                if (entry.glass == null) return;
                banner.setImageBitmap(entry.glass);
                card.setBackgroundColor(0x00000000);
            } else {
                banner.setImageDrawable(entry.banner);
                card.setBackgroundResource(R.drawable.tile_bg);
            }
        }

        /** A slow, slight swell and ease of the zoom while selected: 2.8 seconds a breath, ±1.5%. */
        private void pulse(boolean on) {
            if (pulse != null) pulse.cancel();
            pulse = null;
            if (!on || !PULSE || reducedMotion) return;
            pulse = android.animation.ValueAnimator.ofFloat(0f, 1f);
            pulse.setDuration(2800);
            pulse.setRepeatCount(android.animation.ValueAnimator.INFINITE);
            pulse.setInterpolator(null);
            pulse.addUpdateListener(a -> {
                float s = FOCUS_SCALE * (1f + 0.015f * (float) Math.sin((float) a.getAnimatedValue() * 2 * Math.PI));
                card.setScaleX(s);
                card.setScaleY(s);
            });
            pulse.start();
        }

        /** Greys the tile out while another is selected, so the selected one stands apart. */
        void setMuted(boolean mute) {
            if (mute == muted) return;
            muted = mute;
            android.graphics.ColorMatrixColorFilter tone = mute ? saturation(0.4f) : null;
            banner.setColorFilter(tone);
            card.animate().alpha(mute ? 0.8f : 1f).setDuration(200).start();
        }

        Holder(View v, TileListener listener, boolean reducedMotion) {
            super(v);
            card = v.findViewById(R.id.card);
            banner = v.findViewById(R.id.banner);
            icon = v.findViewById(R.id.icon);
            label = v.findViewById(R.id.label);
            name = v.findViewById(R.id.name);
            this.reducedMotion = reducedMotion;
            if (SHEEN) {
                card.setForeground(new Sheen(v.getResources().getDisplayMetrics().density));
            }

            final float density = v.getResources().getDisplayMetrics().density;
            card.setOutlineProvider(new ViewOutlineProvider() {
                @Override
                public void getOutline(View view, Outline outline) {
                    // 16dp at the design width, scaled down with the tile so small tiles keep the same shape.
                    outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), cornerRadius(view.getWidth(), density));
                }
            });
            card.setClipToOutline(true);

            card.setOnKeyListener((view, keyCode, event) -> {
                if (event.getAction() != KeyEvent.ACTION_DOWN) return false;
                if (keyCode != KeyEvent.KEYCODE_DPAD_UP && keyCode != KeyEvent.KEYCODE_DPAD_DOWN
                        && keyCode != KeyEvent.KEYCODE_DPAD_LEFT && keyCode != KeyEvent.KEYCODE_DPAD_RIGHT) return false;
                int pos = getBindingAdapterPosition();
                return listener != null && pos != RecyclerView.NO_POSITION && listener.onTileVerticalKey(pos, keyCode);
            });

            card.setOnFocusChangeListener((view, focused) -> {
                float s = focused ? FOCUS_SCALE : 1.0f;
                pulse(false);
                glassLook(focused);
                view.animate().cancel();
                name.animate().cancel();
                if (reducedMotion) {
                    // No overshoot: the extra frames redraw the banner and the dock band on a weak GPU.
                    view.animate().scaleX(s).scaleY(s)
                            .setDuration(focused ? 120 : 80)
                            .setInterpolator(ZOOM_REDUCED)
                            .withLayer()
                            .start();
                    name.animate().alpha(focused ? 1f : 0f).setDuration(100).start();
                } else {
                    view.animate().scaleX(s).scaleY(s)
                            .setDuration(focused ? 260 : 180)
                            .setInterpolator(focused ? ZOOM_IN : ZOOM_OUT)
                            .withLayer()
                            .withEndAction(() -> {
                                if (focused && view.isFocused()) pulse(true);
                            })
                            .start();
                    name.animate().alpha(focused ? 1f : 0f)
                            .translationY(focused ? density * 4f : -density * 6f)
                            .setDuration(220).start();
                }
                int pos = getBindingAdapterPosition();
                if (focused && listener != null && pos != RecyclerView.NO_POSITION) {
                    listener.onTileFocused(pos, view);
                }
            });
        }
    }
}
