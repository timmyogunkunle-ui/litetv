package com.timmy.litetv;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.*;
import android.content.pm.*;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.net.*;
import android.os.*;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.*;
import android.view.animation.DecelerateInterpolator;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.*;

/** LiteTV 2 - glass + coverflow home screen. Lists every installed app, no ads. */
public class MainActivity extends Activity {

    static final int ACCENT = Color.rgb(124, 77, 255);
    static final int BG = Color.rgb(10, 12, 20);

    static class App {
        String label, pkg, cls;
        Drawable icon, banner;
        boolean tv;
        int color;
        Bitmap blur;
    }

    private final List<App> all = new ArrayList<>();
    private final List<App> shown = new ArrayList<>();
    private final Map<String, App> byPkg = new HashMap<>();
    private int tab = 0; // 0 all, 1 recent, 2 pinned
    private SharedPreferences prefs;
    private final Handler h = new Handler(Looper.getMainLooper());

    private ImageView screenBg, heroBg, heroArt;
    private TextView heroTitle, heroSub, statusPill;
    private LinearLayout chips;
    private TextView[] tabs = new TextView[3];
    private TextView openBtn;
    private Coverflow flow;
    private Typeface black, medium;

    private final Runnable tick = new Runnable() { public void run() { updateStatus(); h.postDelayed(this, 15000); } };
    private final BroadcastReceiver pkgRx = new BroadcastReceiver() { public void onReceive(Context c, Intent i) { loadApps(); } };

    int dp(float v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }

    // ---------------------------------------------------------------- UI

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("lite", MODE_PRIVATE);
        black = Typeface.create("sans-serif-black", Typeface.BOLD);
        medium = Typeface.create("sans-serif-medium", Typeface.NORMAL);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(BG);

        // blurred full-screen backdrop (image 4 vibe)
        screenBg = new ImageView(this);
        screenBg.setScaleType(ImageView.ScaleType.CENTER_CROP);
        screenBg.setAlpha(0.55f);
        root.addView(screenBg, new FrameLayout.LayoutParams(-1, -1));
        View shade = new View(this);
        shade.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{0x99000000, 0x55000000, 0xEE05060A}));
        root.addView(shade, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout row = new LinearLayout(this);
        row.setPadding(dp(24), dp(22), dp(28), dp(14));
        root.addView(row, new FrameLayout.LayoutParams(-1, -1));

        // ---- left rail
        LinearLayout rail = new LinearLayout(this);
        rail.setOrientation(LinearLayout.VERTICAL);
        rail.setGravity(Gravity.CENTER);
        rail.setPadding(dp(6), dp(10), dp(6), dp(10));
        rail.setBackground(glass(dp(30), 0x1FFFFFFF));
        rail.addView(railBtn(IconDrawable.HOME, new View.OnClickListener() { public void onClick(View v) { setTab(0); flow.requestFocus(); } }));
        rail.addView(railBtn(IconDrawable.STAR, new View.OnClickListener() { public void onClick(View v) { setTab(2); flow.requestFocus(); } }));
        rail.addView(railBtn(IconDrawable.WIFI, new View.OnClickListener() { public void onClick(View v) { start(new Intent(Settings.ACTION_WIFI_SETTINGS)); } }));
        rail.addView(railBtn(IconDrawable.GEAR, new View.OnClickListener() { public void onClick(View v) { start(new Intent(Settings.ACTION_SETTINGS)); } }));
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(-2, -2);
        rlp.gravity = Gravity.CENTER_VERTICAL;
        rlp.rightMargin = dp(20);
        row.addView(rail, rlp);

        // ---- main column
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setClipChildren(false);
        row.setClipChildren(false);
        row.addView(col, new LinearLayout.LayoutParams(0, -1, 1));

        // top bar: tabs + status
        LinearLayout top = new LinearLayout(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout tabWrap = new LinearLayout(this);
        tabWrap.setPadding(dp(4), dp(4), dp(4), dp(4));
        tabWrap.setBackground(glass(dp(22), 0x1AFFFFFF));
        String[] names = {"All apps", "Recent", "Pinned"};
        for (int i = 0; i < 3; i++) {
            final int t = i;
            TextView tv = pill(names[i], 2);
            tv.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { setTab(t); } });
            tabs[i] = tv;
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, dp(34));
            if (i > 0) lp.leftMargin = dp(4);
            tabWrap.addView(tv, lp);
        }
        top.addView(tabWrap);
        top.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
        statusPill = new TextView(this);
        statusPill.setTextColor(Color.WHITE);
        statusPill.setTextSize(15);
        statusPill.setTypeface(medium);
        statusPill.setGravity(Gravity.CENTER);
        statusPill.setPadding(dp(18), 0, dp(18), 0);
        statusPill.setBackground(glass(dp(21), 0x1AFFFFFF));
        top.addView(statusPill, new LinearLayout.LayoutParams(-2, dp(42)));
        col.addView(top, new LinearLayout.LayoutParams(-1, dp(42)));

        // hero card
        FrameLayout hero = new FrameLayout(this);
        hero.setClipToOutline(true);
        hero.setOutlineProvider(round(dp(26)));
        hero.setBackgroundColor(0xFF161A26);
        heroBg = new ImageView(this);
        heroBg.setScaleType(ImageView.ScaleType.CENTER_CROP);
        hero.addView(heroBg, new FrameLayout.LayoutParams(-1, -1));
        View heroShade = new View(this);
        heroShade.setBackground(new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT,
                new int[]{0xF0080A12, 0xB0080A12, 0x30080A12}));
        hero.addView(heroShade, new FrameLayout.LayoutParams(-1, -1));
        View heroEdge = new View(this);
        GradientDrawable edge = new GradientDrawable();
        edge.setCornerRadius(dp(26));
        edge.setStroke(dp(1), 0x33FFFFFF);
        heroEdge.setBackground(edge);
        hero.addView(heroEdge, new FrameLayout.LayoutParams(-1, -1));

        LinearLayout heroText = new LinearLayout(this);
        heroText.setOrientation(LinearLayout.VERTICAL);
        heroText.setGravity(Gravity.CENTER_VERTICAL);
        heroText.setPadding(dp(30), dp(20), dp(20), dp(20));
        chips = new LinearLayout(this);
        heroText.addView(chips);
        heroTitle = new TextView(this);
        heroTitle.setTypeface(black);
        heroTitle.setTextColor(Color.WHITE);
        heroTitle.setTextSize(38);
        heroTitle.setSingleLine(true);
        heroTitle.setEllipsize(TextUtils.TruncateAt.END);
        heroTitle.setLetterSpacing(-0.02f);
        heroTitle.setPadding(0, dp(8), 0, dp(2));
        heroText.addView(heroTitle);
        heroSub = new TextView(this);
        heroSub.setTextColor(0xB3FFFFFF);
        heroSub.setTextSize(13);
        heroText.addView(heroSub);
        LinearLayout btns = new LinearLayout(this);
        btns.setPadding(0, dp(16), 0, 0);
        btns.setClipChildren(false);
        openBtn = pill("▶  Open", 0);
        openBtn.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { App a = flow.current(); if (a != null) launch(a); } });
        btns.addView(openBtn, new LinearLayout.LayoutParams(-2, dp(42)));
        TextView pinBtn = pill("★  Pin", 1);
        pinBtn.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { App a = flow.current(); if (a != null) togglePin(a); } });
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-2, dp(42));
        blp.leftMargin = dp(10);
        btns.addView(pinBtn, blp);
        TextView infoBtn = pill("App info", 1);
        infoBtn.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { App a = flow.current(); if (a != null) appInfo(a); } });
        LinearLayout.LayoutParams blp2 = new LinearLayout.LayoutParams(-2, dp(42));
        blp2.leftMargin = dp(10);
        btns.addView(infoBtn, blp2);
        heroText.addView(btns);
        FrameLayout.LayoutParams htp = new FrameLayout.LayoutParams(-1, -1);
        htp.rightMargin = dp(330);
        hero.addView(heroText, htp);

        heroArt = new ImageView(this);
        heroArt.setScaleType(ImageView.ScaleType.CENTER_CROP);
        heroArt.setClipToOutline(true);
        heroArt.setOutlineProvider(round(dp(18)));
        heroArt.setElevation(dp(10));
        FrameLayout.LayoutParams hap = new FrameLayout.LayoutParams(dp(288), dp(162), Gravity.RIGHT | Gravity.CENTER_VERTICAL);
        hap.rightMargin = dp(28);
        hero.addView(heroArt, hap);

        LinearLayout.LayoutParams hlp = new LinearLayout.LayoutParams(-1, dp(220));
        hlp.topMargin = dp(14);
        col.addView(hero, hlp);

        // coverflow
        flow = new Coverflow(this);
        col.addView(flow, new LinearLayout.LayoutParams(-1, 0, 1));

        setContentView(root);

        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED);
        f.addAction(Intent.ACTION_PACKAGE_REMOVED);
        f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addDataScheme("package");
        registerReceiver(pkgRx, f);

        setTabUi();
        loadApps();
        flow.requestFocus();
    }

    @Override protected void onResume() { super.onResume(); h.post(tick); if (tab == 1) applyTab(); }
    @Override protected void onPause() { super.onPause(); h.removeCallbacks(tick); }
    @Override protected void onDestroy() { unregisterReceiver(pkgRx); super.onDestroy(); }
    @Override public void onBackPressed() { flow.requestFocus(); }

    // ---------------------------------------------------------------- styling helpers

    static ViewOutlineProvider round(final float r) {
        return new ViewOutlineProvider() {
            public void getOutline(View v, Outline o) { o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), r); }
        };
    }

    GradientDrawable glass(float r, int fill) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(r);
        g.setColor(fill);
        g.setStroke(dp(1), 0x26FFFFFF);
        return g;
    }

    GradientDrawable solid(float r, int fill, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(r);
        g.setColor(fill);
        if (stroke != 0) g.setStroke(dp(2), stroke);
        return g;
    }

    final View.OnFocusChangeListener grow = new View.OnFocusChangeListener() {
        public void onFocusChange(View v, boolean has) {
            v.animate().scaleX(has ? 1.08f : 1f).scaleY(has ? 1.08f : 1f).setDuration(150).start();
        }
    };

    /** style 0 = primary white, 1 = glass, 2 = tab */
    TextView pill(String text, int style) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setFocusable(true);
        t.setClickable(true);
        t.setGravity(Gravity.CENTER);
        t.setTypeface(medium);
        t.setTextSize(style == 2 ? 14 : 15);
        t.setPadding(dp(style == 2 ? 18 : 22), 0, dp(style == 2 ? 18 : 22), 0);
        StateListDrawable s = new StateListDrawable();
        int[] F = {android.R.attr.state_focused};
        int[] S = {android.R.attr.state_selected};
        int dark = Color.rgb(12, 14, 22);
        if (style == 0) {
            s.addState(F, solid(dp(21), ACCENT, Color.WHITE));
            s.addState(new int[]{}, solid(dp(21), Color.WHITE, 0));
            t.setTextColor(new ColorStateList(new int[][]{F, {}}, new int[]{Color.WHITE, dark}));
        } else if (style == 1) {
            s.addState(F, solid(dp(21), Color.WHITE, 0));
            s.addState(new int[]{}, glass(dp(21), 0x24FFFFFF));
            t.setTextColor(new ColorStateList(new int[][]{F, {}}, new int[]{dark, Color.WHITE}));
        } else {
            s.addState(F, solid(dp(17), Color.WHITE, 0));
            s.addState(S, solid(dp(17), ACCENT, 0));
            s.addState(new int[]{}, solid(dp(17), Color.TRANSPARENT, 0));
            t.setTextColor(new ColorStateList(new int[][]{F, S, {}}, new int[]{dark, Color.WHITE, 0xB3FFFFFF}));
        }
        t.setBackground(s);
        t.setOnFocusChangeListener(grow);
        return t;
    }

    View railBtn(int icon, View.OnClickListener l) {
        ImageView b = new ImageView(this);
        b.setImageDrawable(new IconDrawable(icon));
        b.setPadding(dp(11), dp(11), dp(11), dp(11));
        b.setFocusable(true);
        b.setClickable(true);
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[]{android.R.attr.state_focused}, solid(dp(22), Color.WHITE, 0));
        s.addState(new int[]{}, solid(dp(22), Color.TRANSPARENT, 0));
        b.setBackground(s);
        b.setOnClickListener(l);
        b.setOnFocusChangeListener(grow);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(44), dp(44));
        lp.topMargin = dp(5);
        lp.bottomMargin = dp(5);
        b.setLayoutParams(lp);
        return b;
    }

    TextView chip(String s, boolean accent) {
        TextView c = new TextView(this);
        c.setText(s);
        c.setTextSize(11);
        c.setTypeface(medium);
        c.setLetterSpacing(0.08f);
        c.setTextColor(Color.WHITE);
        c.setPadding(dp(10), dp(4), dp(10), dp(4));
        c.setBackground(accent ? solid(dp(12), ACCENT, 0) : glass(dp(12), 0x26FFFFFF));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.rightMargin = dp(6);
        c.setLayoutParams(lp);
        return c;
    }

    Drawable copy(Drawable d) {
        if (d == null) return null;
        Drawable.ConstantState cs = d.getConstantState();
        return cs != null ? cs.newDrawable(getResources()).mutate() : d;
    }

    // ---------------------------------------------------------------- data

    private void loadApps() {
        new Thread(new Runnable() { public void run() {
            final PackageManager pm = getPackageManager();
            final List<App> list = new ArrayList<>();
            Set<String> seen = new HashSet<>();
            for (String cat : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
                Intent i = new Intent(Intent.ACTION_MAIN).addCategory(cat);
                for (ResolveInfo r : pm.queryIntentActivities(i, 0)) {
                    String pkg = r.activityInfo.packageName;
                    if (pkg.equals(getPackageName()) || !seen.add(pkg)) continue;
                    App a = new App();
                    a.pkg = pkg;
                    a.cls = r.activityInfo.name;
                    a.tv = cat.equals(Intent.CATEGORY_LEANBACK_LAUNCHER);
                    a.label = String.valueOf(r.loadLabel(pm));
                    try { a.icon = r.loadIcon(pm); } catch (Exception e) { a.icon = pm.getDefaultActivityIcon(); }
                    try {
                        a.banner = r.activityInfo.loadBanner(pm);
                        if (a.banner == null) a.banner = r.activityInfo.applicationInfo.loadBanner(pm);
                    } catch (Exception ignored) {}
                    makeArt(a);
                    list.add(a);
                }
            }
            h.post(new Runnable() { public void run() {
                all.clear();
                all.addAll(list);
                byPkg.clear();
                for (App a : all) byPkg.put(a.pkg, a);
                applyTab();
            } });
        } }).start();
    }

    /** Tiny bitmap -> soft blurred backdrop + dominant colour (cheap, works on every Android version). */
    private void makeArt(App a) {
        try {
            Drawable src = copy(a.banner != null ? a.banner : a.icon);
            Bitmap small = Bitmap.createBitmap(16, 9, Bitmap.Config.ARGB_8888);
            Canvas c = new Canvas(small);
            c.drawColor(Color.rgb(30, 34, 48));
            if (a.banner != null) src.setBounds(0, 0, 16, 9); else src.setBounds(-2, -6, 18, 15);
            src.draw(c);
            long r = 0, g = 0, b = 0;
            int n = 0;
            for (int y = 0; y < 9; y++) for (int x = 0; x < 16; x++) {
                int p = small.getPixel(x, y);
                r += Color.red(p); g += Color.green(p); b += Color.blue(p); n++;
            }
            float[] hsv = new float[3];
            Color.RGBToHSV((int) (r / n), (int) (g / n), (int) (b / n), hsv);
            hsv[1] = Math.min(1f, hsv[1] * 1.5f + 0.15f);
            hsv[2] = Math.max(0.45f, Math.min(0.8f, hsv[2] * 1.2f));
            a.color = Color.HSVToColor(hsv);
            Bitmap mid = Bitmap.createScaledBitmap(small, 48, 27, true);
            a.blur = Bitmap.createScaledBitmap(mid, 160, 90, true);
        } catch (Exception e) {
            a.color = ACCENT;
        }
    }

    private Set<String> pins() { return new HashSet<>(prefs.getStringSet("pins", new HashSet<String>())); }

    private List<String> recent() {
        String s = prefs.getString("recent", "");
        List<String> l = new ArrayList<>();
        for (String p : s.split(",")) if (p.length() > 0) l.add(p);
        return l;
    }

    private void setTab(int t) { tab = t; setTabUi(); applyTab(); }

    private void setTabUi() { for (int i = 0; i < 3; i++) tabs[i].setSelected(i == tab); }

    private void applyTab() {
        App cur = flow.current();
        final Set<String> pins = pins();
        shown.clear();
        if (tab == 1) {
            for (String p : recent()) { App a = byPkg.get(p); if (a != null) shown.add(a); }
        } else {
            for (App a : all) if (tab == 0 || pins.contains(a.pkg)) shown.add(a);
            Collections.sort(shown, new Comparator<App>() { public int compare(App x, App y) {
                boolean px = pins.contains(x.pkg), py = pins.contains(y.pkg);
                if (px != py) return px ? -1 : 1;
                return x.label.compareToIgnoreCase(y.label);
            } });
        }
        int keep = cur == null ? 0 : Math.max(0, shown.indexOf(cur));
        flow.setApps(shown, keep);
    }

    // ---------------------------------------------------------------- hero

    void showHero(App a) {
        chips.removeAllViews();
        if (a == null) {
            heroTitle.setText(tab == 2 ? "No pinned apps yet" : tab == 1 ? "Nothing opened yet" : "Loading…");
            heroSub.setText(tab == 2 ? "Hold OK on any app to pin it here" : "");
            heroArt.setImageDrawable(null);
            heroArt.setBackground(null);
            return;
        }
        Set<String> pins = pins();
        chips.addView(chip(a.tv ? "TV APP" : "APP", false));
        if (pins.contains(a.pkg)) chips.addView(chip("★ PINNED", true));
        if (recent().indexOf(a.pkg) == 0) chips.addView(chip("LAST USED", false));
        heroTitle.setText(a.label);
        heroSub.setText((shown.indexOf(a) + 1) + " of " + shown.size() + "   ·   OK to open   ·   hold OK for options");
        if (a.banner != null) {
            heroArt.setScaleType(ImageView.ScaleType.CENTER_CROP);
            heroArt.setPadding(0, 0, 0, 0);
            heroArt.setBackground(null);
            heroArt.setImageDrawable(copy(a.banner));
        } else {
            heroArt.setScaleType(ImageView.ScaleType.FIT_CENTER);
            heroArt.setPadding(dp(80), dp(40), dp(80), dp(40));
            heroArt.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{a.color, darker(a.color)}));
            heroArt.setImageDrawable(copy(a.icon));
        }
        heroBg.setImageBitmap(a.blur);
        screenBg.setImageBitmap(a.blur);
        heroTitle.setAlpha(0.3f);
        heroTitle.animate().alpha(1f).setDuration(220).start();
        heroArt.setAlpha(0.4f);
        heroArt.setTranslationX(dp(14));
        heroArt.animate().alpha(1f).translationX(0).setDuration(260).setInterpolator(new DecelerateInterpolator()).start();
    }

    static int darker(int c) {
        float[] hsv = new float[3];
        Color.colorToHSV(c, hsv);
        hsv[2] *= 0.45f;
        return Color.HSVToColor(hsv);
    }

    // ---------------------------------------------------------------- actions

    void launch(App a) {
        List<String> r = recent();
        r.remove(a.pkg);
        r.add(0, a.pkg);
        while (r.size() > 20) r.remove(r.size() - 1);
        prefs.edit().putString("recent", TextUtils.join(",", r)).apply();
        Intent i = new Intent(Intent.ACTION_MAIN);
        i.setComponent(new ComponentName(a.pkg, a.cls));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        if (!start(i)) {
            Intent l = getPackageManager().getLeanbackLaunchIntentForPackage(a.pkg);
            if (l == null) l = getPackageManager().getLaunchIntentForPackage(a.pkg);
            if (l != null) start(l);
        }
    }

    boolean start(Intent i) {
        try { i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK); startActivity(i); return true; }
        catch (Exception e) { Toast.makeText(this, "Couldn't open", Toast.LENGTH_SHORT).show(); return false; }
    }

    void togglePin(App a) {
        Set<String> p = pins();
        if (!p.remove(a.pkg)) p.add(a.pkg);
        prefs.edit().putStringSet("pins", p).apply();
        applyTab();
    }

    void appInfo(App a) { start(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + a.pkg))); }

    void menu(final App a) {
        boolean pinned = pins().contains(a.pkg);
        new AlertDialog.Builder(this).setTitle(a.label)
                .setItems(new String[]{"Open", pinned ? "Unpin" : "Pin to top", "App info"}, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface d, int w) {
                        if (w == 0) launch(a); else if (w == 1) togglePin(a); else appInfo(a);
                    }
                }).show();
    }

    void updateStatus() {
        String s = "Offline";
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            NetworkInfo ni = cm.getActiveNetworkInfo();
            if (ni != null && ni.isConnected())
                s = ni.getType() == ConnectivityManager.TYPE_ETHERNET ? "Ethernet" : ni.getType() == ConnectivityManager.TYPE_WIFI ? "Wi-Fi" : "Online";
        } catch (Exception ignored) {}
        String t = new SimpleDateFormat("EEE d MMM   HH:mm", Locale.getDefault()).format(new Date());
        statusPill.setText("● " + s + "    " + t);
    }

    // ---------------------------------------------------------------- coverflow (images 1-3)

    class Coverflow extends FrameLayout {
        final List<View> cards = new ArrayList<>();
        List<App> apps = new ArrayList<>();
        int index = 0;
        boolean longFired;
        final int cw = dp(230), ch = dp(130);

        Coverflow(Context c) {
            super(c);
            setFocusable(true);
            setFocusableInTouchMode(true);
            setClipChildren(false);
            setClipToPadding(false);
        }

        App current() { return apps.isEmpty() ? null : apps.get(index); }

        void setApps(List<App> list, int keep) {
            removeAllViews();
            cards.clear();
            apps = new ArrayList<>(list);
            for (App a : apps) {
                View v = makeCard(a);
                addView(v, new LayoutParams(cw, ch, Gravity.CENTER));
                v.setCameraDistance(9000 * getResources().getDisplayMetrics().density);
                cards.add(v);
            }
            index = apps.isEmpty() ? 0 : Math.min(keep, apps.size() - 1);
            layoutCards(false);
            showHero(current());
        }

        View makeCard(App a) {
            FrameLayout f = new FrameLayout(getContext());
            f.setClipToOutline(true);
            f.setOutlineProvider(round(dp(16)));
            if (a.banner != null) {
                ImageView art = new ImageView(getContext());
                art.setScaleType(ImageView.ScaleType.CENTER_CROP);
                art.setImageDrawable(copy(a.banner));
                f.addView(art, new LayoutParams(-1, -1));
            } else {
                f.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{a.color, darker(a.color)}));
                ImageView ic = new ImageView(getContext());
                ic.setImageDrawable(copy(a.icon));
                f.addView(ic, new LayoutParams(dp(60), dp(60), Gravity.CENTER));
                TextView t = new TextView(getContext());
                t.setText(a.label);
                t.setTextColor(0xDDFFFFFF);
                t.setTextSize(11);
                t.setSingleLine(true);
                t.setEllipsize(TextUtils.TruncateAt.END);
                t.setPadding(dp(10), 0, dp(10), dp(8));
                f.addView(t, new LayoutParams(-1, -2, Gravity.BOTTOM));
            }
            return f;
        }

        void layoutCards(boolean anim) {
            for (int i = 0; i < cards.size(); i++) {
                FrameLayout v = (FrameLayout) cards.get(i);
                int k = i - index, ak = Math.abs(k);
                if (ak > 5) {
                    v.setVisibility(INVISIBLE);
                    v.setLayerType(LAYER_TYPE_NONE, null);
                    continue;
                }
                v.setVisibility(VISIBLE);
                float tx = k == 0 ? 0 : Math.signum(k) * (cw * 0.72f + (ak - 1) * cw * 0.34f);
                float ry = k == 0 ? 0 : (k < 0 ? 48f : -48f);
                float sc = k == 0 ? 1.12f : Math.max(0.62f, 0.86f - 0.05f * (ak - 1));
                float al = k == 0 ? 1f : Math.max(0f, 0.85f - 0.16f * (ak - 1));
                v.setTranslationZ(k == 0 ? dp(12) : -ak);
                Paint p = new Paint();
                ColorMatrix cm = new ColorMatrix();
                cm.setSaturation(k == 0 ? 1f : 0.1f);
                p.setColorFilter(new ColorMatrixColorFilter(cm));
                v.setLayerType(LAYER_TYPE_HARDWARE, p);
                if (k == 0 && hasFocus()) {
                    GradientDrawable ring = new GradientDrawable();
                    ring.setCornerRadius(dp(16));
                    ring.setStroke(dp(3), Color.WHITE);
                    v.setForeground(ring);
                } else v.setForeground(null);
                if (anim) {
                    v.animate().translationX(tx).rotationY(ry).scaleX(sc).scaleY(sc).alpha(al)
                            .setDuration(260).setInterpolator(new DecelerateInterpolator(1.6f)).start();
                } else {
                    v.setTranslationX(tx); v.setRotationY(ry); v.setScaleX(sc); v.setScaleY(sc); v.setAlpha(al);
                }
            }
        }

        void move(int d) {
            int ni = index + d;
            if (ni < 0 || ni >= apps.size()) return;
            index = ni;
            layoutCards(true);
            showHero(current());
        }

        boolean ok(int code) {
            return code == KeyEvent.KEYCODE_DPAD_CENTER || code == KeyEvent.KEYCODE_ENTER
                    || code == KeyEvent.KEYCODE_NUMPAD_ENTER || code == KeyEvent.KEYCODE_BUTTON_A;
        }

        @Override public boolean onKeyDown(int code, KeyEvent e) {
            if (code == KeyEvent.KEYCODE_DPAD_LEFT) {
                if (index > 0) { move(-1); return true; }
                return false; // fall through to the rail
            }
            if (code == KeyEvent.KEYCODE_DPAD_RIGHT) { move(1); return true; }
            if (ok(code)) { e.startTracking(); return true; }
            return super.onKeyDown(code, e);
        }

        @Override public boolean onKeyLongPress(int code, KeyEvent e) {
            if (ok(code)) { longFired = true; App a = current(); if (a != null) menu(a); return true; }
            return super.onKeyLongPress(code, e);
        }

        @Override public boolean onKeyUp(int code, KeyEvent e) {
            if (ok(code)) {
                if (!longFired && !e.isCanceled()) { App a = current(); if (a != null) launch(a); }
                longFired = false;
                return true;
            }
            return super.onKeyUp(code, e);
        }

        @Override protected void onFocusChanged(boolean f, int dir, Rect prev) {
            super.onFocusChanged(f, dir, prev);
            layoutCards(false);
        }
    }

    // ---------------------------------------------------------------- tiny vector icons for the rail

    static class IconDrawable extends Drawable {
        static final int HOME = 0, STAR = 1, WIFI = 2, GEAR = 3;
        final int type;
        boolean focused;
        final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);

        IconDrawable(int t) {
            type = t;
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
        }

        @Override public boolean isStateful() { return true; }

        @Override protected boolean onStateChange(int[] state) {
            boolean f = false;
            for (int s : state) if (s == android.R.attr.state_focused) f = true;
            if (f != focused) { focused = f; invalidateSelf(); return true; }
            return false;
        }

        @Override public void draw(Canvas c) {
            Rect b = getBounds();
            float s = Math.min(b.width(), b.height()) / 24f;
            c.save();
            c.translate(b.left + (b.width() - 24 * s) / 2f, b.top + (b.height() - 24 * s) / 2f);
            c.scale(s, s);
            p.setColor(focused ? Color.rgb(12, 14, 22) : Color.WHITE);
            p.setStrokeWidth(2f);
            p.setStyle(Paint.Style.STROKE);
            Path path = new Path();
            switch (type) {
                case HOME:
                    path.moveTo(3, 11); path.lineTo(12, 3.5f); path.lineTo(21, 11);
                    path.moveTo(5.5f, 9.5f); path.lineTo(5.5f, 20.5f); path.lineTo(18.5f, 20.5f); path.lineTo(18.5f, 9.5f);
                    path.moveTo(10, 20.5f); path.lineTo(10, 15); path.lineTo(14, 15); path.lineTo(14, 20.5f);
                    c.drawPath(path, p);
                    break;
                case STAR:
                    for (int i = 0; i < 10; i++) {
                        double ang = Math.PI / 2 + i * Math.PI / 5;
                        float r = (i % 2 == 0) ? 9.5f : 4f;
                        float x = 12 + (float) (r * Math.cos(ang)), y = 12.5f - (float) (r * Math.sin(ang));
                        if (i == 0) path.moveTo(x, y); else path.lineTo(x, y);
                    }
                    path.close();
                    c.drawPath(path, p);
                    break;
                case WIFI:
                    c.drawArc(new RectF(2, 5, 22, 25), 225, 90, false, p);
                    c.drawArc(new RectF(6, 9, 18, 21), 225, 90, false, p);
                    p.setStyle(Paint.Style.FILL);
                    c.drawCircle(12, 18, 1.8f, p);
                    break;
                case GEAR:
                    c.drawCircle(12, 12, 3f, p);
                    c.drawCircle(12, 12, 7f, p);
                    for (int i = 0; i < 8; i++) {
                        double ang = i * Math.PI / 4;
                        c.drawLine(12 + (float) (7 * Math.cos(ang)), 12 + (float) (7 * Math.sin(ang)),
                                12 + (float) (9.8 * Math.cos(ang)), 12 + (float) (9.8 * Math.sin(ang)), p);
                    }
                    break;
            }
            c.restore();
        }

        @Override public void setAlpha(int a) { p.setAlpha(a); }
        @Override public void setColorFilter(ColorFilter cf) { p.setColorFilter(cf); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
