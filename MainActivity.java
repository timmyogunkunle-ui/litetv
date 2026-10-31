package com.timmy.litetv;

import android.app.Activity;
import android.content.*;
import android.content.pm.*;
import android.graphics.Color;
import android.graphics.drawable.*;
import android.net.*;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.text.SimpleDateFormat;
import java.util.*;

/** LiteTV: minimal, ad-free home screen. Lists every installed app (TV + phone apps). */
public class MainActivity extends Activity {
    static class App { String label, pkg, cls; Drawable icon; }

    private final List<App> apps = new ArrayList<>();
    private GridView grid;
    private TextView clock, net;
    private SharedPreferences prefs;
    private final Handler h = new Handler(Looper.getMainLooper());
    private final Runnable tick = new Runnable() { public void run() { updateBar(); h.postDelayed(this, 15000); } };
    private final BroadcastReceiver pkgRx = new BroadcastReceiver() { public void onReceive(Context c, Intent i) { loadApps(); } };

    private int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density); }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        prefs = getSharedPreferences("lite", MODE_PRIVATE);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(Color.rgb(14, 16, 20));
        root.setPadding(dp(40), dp(24), dp(40), dp(16));

        LinearLayout bar = new LinearLayout(this);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        clock = text(34, true);
        net = text(16, false);
        net.setPadding(dp(24), 0, 0, 0);
        View spacer = new View(this);
        Button settings = new Button(this);
        settings.setText("Settings");
        settings.setAllCaps(false);
        settings.setBackground(focusBg());
        settings.setTextColor(Color.WHITE);
        settings.setOnClickListener(v -> startSafe(new Intent(Settings.ACTION_SETTINGS)));
        bar.addView(clock); bar.addView(net);
        bar.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1));
        bar.addView(settings);
        root.addView(bar);

        TextView hint = text(13, false);
        hint.setText("OK = open  ·  Hold OK = pin to top / app info");
        hint.setTextColor(Color.GRAY);
        hint.setPadding(0, dp(4), 0, dp(12));
        root.addView(hint);

        grid = new GridView(this);
        grid.setNumColumns(6);
        grid.setVerticalSpacing(dp(14));
        grid.setHorizontalSpacing(dp(14));
        grid.setSelector(new ColorDrawable(Color.TRANSPARENT));
        grid.setDrawSelectorOnTop(false);
        grid.setOnItemClickListener((p, v, pos, id) -> launch(apps.get(pos)));
        grid.setOnItemLongClickListener((p, v, pos, id) -> { menu(apps.get(pos)); return true; });
        root.addView(grid, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);

        IntentFilter f = new IntentFilter();
        f.addAction(Intent.ACTION_PACKAGE_ADDED); f.addAction(Intent.ACTION_PACKAGE_REMOVED); f.addAction(Intent.ACTION_PACKAGE_CHANGED);
        f.addDataScheme("package");
        registerReceiver(pkgRx, f);
        loadApps();
    }

    private TextView text(int sp, boolean bold) {
        TextView t = new TextView(this);
        t.setTextColor(Color.WHITE); t.setTextSize(sp);
        if (bold) t.setTypeface(null, android.graphics.Typeface.BOLD);
        return t;
    }

    private Drawable focusBg() {
        StateListDrawable s = new StateListDrawable();
        GradientDrawable on = new GradientDrawable(); on.setCornerRadius(dp(10)); on.setColor(Color.rgb(45, 50, 62)); on.setStroke(dp(3), Color.WHITE);
        GradientDrawable off = new GradientDrawable(); off.setCornerRadius(dp(10)); off.setColor(Color.rgb(28, 31, 38));
        s.addState(new int[]{android.R.attr.state_focused}, on);
        s.addState(new int[]{android.R.attr.state_selected}, on);
        s.addState(new int[]{android.R.attr.state_pressed}, on);
        s.addState(new int[]{}, off);
        return s;
    }

    @Override protected void onResume() { super.onResume(); h.post(tick); }
    @Override protected void onPause() { super.onPause(); h.removeCallbacks(tick); }
    @Override protected void onDestroy() { unregisterReceiver(pkgRx); super.onDestroy(); }
    @Override public void onBackPressed() { /* home screen: ignore back */ }

    private void updateBar() {
        clock.setText(new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date()));
        String s = "Offline";
        try {
            ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
            NetworkInfo ni = cm.getActiveNetworkInfo();
            if (ni != null && ni.isConnected()) s = ni.getType() == ConnectivityManager.TYPE_ETHERNET ? "Ethernet" : ni.getType() == ConnectivityManager.TYPE_WIFI ? "Wi-Fi" : "Online";
        } catch (Exception ignored) {}
        net.setText(s);
    }

    private void loadApps() {
        PackageManager pm = getPackageManager();
        Map<String, App> map = new LinkedHashMap<>();
        // TV apps first (their banners), then any phone-style apps so nothing installed goes missing
        for (String cat : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
            Intent i = new Intent(Intent.ACTION_MAIN).addCategory(cat);
            for (ResolveInfo r : pm.queryIntentActivities(i, 0)) {
                String pkg = r.activityInfo.packageName;
                if (pkg.equals(getPackageName()) || map.containsKey(pkg)) continue;
                App a = new App();
                a.pkg = pkg; a.cls = r.activityInfo.name;
                a.label = String.valueOf(r.loadLabel(pm));
                a.icon = r.loadIcon(pm);
                map.put(pkg, a);
            }
        }
        final Set<String> pins = prefs.getStringSet("pins", new HashSet<String>());
        apps.clear(); apps.addAll(map.values());
        Collections.sort(apps, (x, y) -> {
            boolean px = pins.contains(x.pkg), py = pins.contains(y.pkg);
            if (px != py) return px ? -1 : 1;
            return x.label.compareToIgnoreCase(y.label);
        });
        grid.setAdapter(new Adapter(pins));
    }

    private void launch(App a) {
        Intent i = new Intent(Intent.ACTION_MAIN);
        i.setComponent(new ComponentName(a.pkg, a.cls));
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
        if (!startSafe(i)) {
            Intent l = getPackageManager().getLeanbackLaunchIntentForPackage(a.pkg);
            if (l == null) l = getPackageManager().getLaunchIntentForPackage(a.pkg);
            if (l != null) startSafe(l);
        }
    }

    private boolean startSafe(Intent i) {
        try { startActivity(i); return true; }
        catch (Exception e) { Toast.makeText(this, "Couldn't open", Toast.LENGTH_SHORT).show(); return false; }
    }

    private void menu(final App a) {
        final Set<String> pins = new HashSet<>(prefs.getStringSet("pins", new HashSet<String>()));
        final boolean pinned = pins.contains(a.pkg);
        new android.app.AlertDialog.Builder(this).setTitle(a.label)
            .setItems(new String[]{pinned ? "Unpin" : "Pin to top", "App info"}, (d, w) -> {
                if (w == 0) {
                    if (pinned) pins.remove(a.pkg); else pins.add(a.pkg);
                    prefs.edit().putStringSet("pins", pins).apply();
                    loadApps();
                } else {
                    startSafe(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + a.pkg)));
                }
            }).show();
    }

    class Adapter extends BaseAdapter {
        final Set<String> pins;
        Adapter(Set<String> p) { pins = p; }
        public int getCount() { return apps.size(); }
        public Object getItem(int i) { return apps.get(i); }
        public long getItemId(int i) { return i; }
        public View getView(int pos, View v, ViewGroup parent) {
            LinearLayout cell;
            if (v == null) {
                cell = new LinearLayout(MainActivity.this);
                cell.setOrientation(LinearLayout.VERTICAL);
                cell.setGravity(Gravity.CENTER);
                cell.setPadding(dp(8), dp(12), dp(8), dp(10));
                cell.setBackground(focusBg());
                cell.setDuplicateParentStateEnabled(false);
                ImageView iv = new ImageView(MainActivity.this);
                iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
                cell.addView(iv, new LinearLayout.LayoutParams(dp(64), dp(64)));
                TextView tv = text(13, false);
                tv.setSingleLine(true); tv.setEllipsize(android.text.TextUtils.TruncateAt.END);
                tv.setGravity(Gravity.CENTER); tv.setPadding(0, dp(6), 0, 0);
                cell.addView(tv, new LinearLayout.LayoutParams(-1, -2));
                cell.setLayoutParams(new AbsListView.LayoutParams(-1, dp(120)));
                v = cell;
            }
            cell = (LinearLayout) v;
            App a = apps.get(pos);
            ((ImageView) cell.getChildAt(0)).setImageDrawable(a.icon);
            ((TextView) cell.getChildAt(1)).setText((pins.contains(a.pkg) ? "★ " : "") + a.label);
            return v;
        }
    }

    // GridView handles item focus itself; mirror selection onto the cell background
    @Override public boolean dispatchKeyEvent(KeyEvent e) {
        boolean r = super.dispatchKeyEvent(e);
        if (grid != null) for (int i = 0; i < grid.getChildCount(); i++) {
            View c = grid.getChildAt(i);
            c.setSelected(grid.hasFocus() && grid.getFirstVisiblePosition() + i == grid.getSelectedItemPosition());
        }
        return r;
    }
}
