package com.gathin.tvhome;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.text.Collator;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 2.4 M2 (backlog 25): All apps as a grid with a search box. Every launchable app, TV entry first, without the
 * hidden ones (Remove from Home, TV-maker extras) except Settings and the Play Store, which always show. Typing
 * filters by name (Arabic letters folded: أ إ آ to ا, ة to ه, ى to ي, no diacritics) or package. Own code.
 */
public final class AllAppsActivity extends Activity {
    static final int COLUMNS = 6;
    /** Screenshot tests: {package, label} pairs instead of the installed apps. */
    static List<String[]> testApps;

    static final class App {
        final String pkg, label;
        final Intent launch;
        final Drawable icon;
        App(String pkg, String label, Intent launch, Drawable icon) { this.pkg = pkg; this.label = label; this.launch = launch; this.icon = icon; }
    }

    private final List<App> all = new ArrayList<>();
    private final List<App> shown = new ArrayList<>();
    private Grid adapter;
    private TextView empty;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        float dp = getResources().getDisplayMetrics().density;
        boolean rtl = Rows.rtl();
        Typeface cairo = getResources().getFont(R.font.cairo);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setBackgroundColor(0xFF0B1F1E);
        int pad = (int) (40 * dp);
        col.setPadding(pad, (int) (24 * dp), pad, (int) (16 * dp));
        TextView title = new TextView(this);
        title.setText(R.string.mi5g_all_apps);
        title.setTextColor(0xFFD4AF37);
        title.setTextSize(24f);
        title.setTypeface(cairo, Typeface.BOLD);
        title.setGravity(Rows.start());
        col.addView(title, new LinearLayout.LayoutParams(-1, -2));
        EditText search = new EditText(this);
        search.setHint(R.string.mi5g_all_apps_search);
        search.setSingleLine(true);
        search.setTextColor(0xFFFFFFFF);
        search.setHintTextColor(0x99FFFFFF);
        search.setTypeface(cairo);
        search.setTextSize(16f);
        search.setGravity((rtl ? Gravity.RIGHT : Gravity.LEFT) | Gravity.CENTER_VERTICAL);
        search.setImeOptions(EditorInfo.IME_ACTION_SEARCH | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        GradientDrawable box = new GradientDrawable();
        box.setColor(0x26FFFFFF);
        box.setCornerRadius(14 * dp);
        box.setStroke((int) dp, 0x40D4AF37);
        search.setBackground(box);
        int sp = (int) (14 * dp);
        search.setPadding(sp, 0, sp, 0);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(-1, (int) (48 * dp));
        slp.topMargin = (int) (12 * dp);
        col.addView(search, slp);
        empty = new TextView(this);
        empty.setText(R.string.mi5g_all_apps_none);
        empty.setTextColor(0xCCE8D9A8);
        empty.setTypeface(cairo);
        empty.setTextSize(15f);
        empty.setGravity(Rows.start());
        empty.setVisibility(View.GONE);
        LinearLayout.LayoutParams elp = new LinearLayout.LayoutParams(-1, -2);
        elp.topMargin = (int) (16 * dp);
        col.addView(empty, elp);
        RecyclerView grid = new RecyclerView(this);
        grid.setLayoutManager(new GridLayoutManager(this, COLUMNS) {
            @Override protected boolean isLayoutRTL() { return rtl; }
        });
        grid.setItemAnimator(null);
        grid.setClipToPadding(false);
        grid.setPadding(0, (int) (12 * dp), 0, (int) (12 * dp));
        adapter = new Grid();
        grid.setAdapter(adapter);
        col.addView(grid, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(col);
        load();
        filter("");
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) { }
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) { filter(s.toString()); }
            @Override public void afterTextChanged(Editable s) { }
        });
        // Down from the box goes into the grid; the first app gets focus when the screen opens.
        search.setOnKeyListener((v, code, e) -> {
            if (e.getAction() == KeyEvent.ACTION_DOWN && code == KeyEvent.KEYCODE_DPAD_DOWN && !shown.isEmpty()) {
                RecyclerView.ViewHolder h = grid.findViewHolderForAdapterPosition(0);
                if (h != null) { h.itemView.requestFocus(); return true; }
            }
            return false;
        });
        grid.post(() -> {
            RecyclerView.ViewHolder h = grid.findViewHolderForAdapterPosition(0);
            if (h != null) h.itemView.requestFocus(); else search.requestFocus();
        });
    }

    private void load() {
        all.clear();
        PackageManager pm = getPackageManager();
        Set<String> removed = TileMenu.removed(this);
        boolean extrasFilter = MakerExtras.filterOn(this);
        Set<String> shownAgain = MakerExtras.shownAgain(this);
        if (testApps != null) {
            for (String[] a : testApps) all.add(new App(a[0], a[1], null, null));
        } else {
            Map<String, ResolveInfo> unique = new LinkedHashMap<>();
            for (String cat : new String[]{Intent.CATEGORY_LEANBACK_LAUNCHER, Intent.CATEGORY_LAUNCHER}) {
                for (ResolveInfo ri : pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(cat), 0)) {
                    if (ri.activityInfo == null || !ri.activityInfo.exported) continue;
                    String pkg = ri.activityInfo.packageName;
                    if (pkg.equals(getPackageName()) || unique.containsKey(pkg)) continue;
                    unique.put(pkg, ri);
                }
            }
            for (Map.Entry<String, ResolveInfo> e : unique.entrySet()) {
                String pkg = e.getKey();
                ResolveInfo ri = e.getValue();
                String label;
                try { label = Cards.cleanLabel(String.valueOf(ri.loadLabel(pm)), pkg); } catch (RuntimeException x) { label = pkg; }
                if (!visible(pkg, label, removed, extrasFilter, shownAgain)) continue;
                String ar = ArabicNames.of(this, pkg);
                Intent launch = new Intent(Intent.ACTION_MAIN).setClassName(pkg, ri.activityInfo.name)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED);
                Drawable icon;
                try { icon = ri.loadIcon(pm); } catch (RuntimeException x) { icon = null; }
                all.add(new App(pkg, ar != null ? ar : label, launch, icon));
            }
        }
        Collator c = Collator.getInstance();
        all.sort((x, y) -> c.compare(x.label, y.label));
    }

    /** Hidden apps stay out, except the ones that always show (Settings, Play Store). */
    static boolean visible(String pkg, String label, Set<String> removed, boolean extrasFilter, Set<String> shownAgain) {
        if (LauncherSwitches.alwaysVisible(pkg)) return true;
        if (removed.contains(pkg)) return false;
        return !MakerExtras.hidden(pkg, label, extrasFilter, shownAgain);
    }

    /** Lower case, no diacritics, Arabic letter shapes folded, for matching typed text. */
    static String fold(String s) {
        if (s == null) return "";
        String n = Normalizer.normalize(s.toLowerCase(Locale.ROOT), Normalizer.Form.NFD);
        StringBuilder sb = new StringBuilder(n.length());
        for (int i = 0; i < n.length(); i++) {
            char ch = n.charAt(i);
            if (Character.getType(ch) == Character.NON_SPACING_MARK) continue;   // Latin accents, Arabic harakat
            if (ch == '\u0640') continue;                                           // tatweel
            if (ch == '\u0623' || ch == '\u0625' || ch == '\u0622' || ch == '\u0671') ch = '\u0627';
            else if (ch == '\u0629') ch = '\u0647';
            else if (ch == '\u0649') ch = '\u064A';
            sb.append(ch);
        }
        return sb.toString().trim();
    }

    static boolean matches(String label, String pkg, String query) {
        String q = fold(query);
        if (q.isEmpty()) return true;
        return fold(label).contains(q) || (pkg != null && pkg.toLowerCase(Locale.ROOT).contains(q));
    }

    private void filter(String query) {
        shown.clear();
        for (App a : all) if (matches(a.label, a.pkg, query)) shown.add(a);
        adapter.notifyDataSetChanged();
        empty.setVisibility(shown.isEmpty() ? View.VISIBLE : View.GONE);
    }

    int shownCount() { return shown.size(); }

    private void open(App a) {
        Sounds.click(this);
        if (a.launch == null) return;
        try {
            startActivity(a.launch);
        } catch (RuntimeException e) {
            Toast.makeText(this, getString(R.string.cant_open, a.label), Toast.LENGTH_SHORT).show();
        }
    }

    private final class Grid extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        @NonNull @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            float dp = getResources().getDisplayMetrics().density;
            LinearLayout cell = new LinearLayout(AllAppsActivity.this);
            cell.setOrientation(LinearLayout.VERTICAL);
            cell.setGravity(Gravity.CENTER_HORIZONTAL);
            int p = (int) (8 * dp);
            cell.setPadding(p, p, p, p);
            cell.setFocusable(true);
            cell.setClickable(true);
            GradientDrawable focus = new GradientDrawable();
            focus.setCornerRadius(16 * dp);
            focus.setColor(0x00000000);
            cell.setBackground(focus);
            cell.setOnFocusChangeListener((v, f) -> {
                focus.setColor(f ? 0x40D4AF37 : 0x00000000);
                focus.setStroke(f ? (int) (2 * dp) : 0, 0xFFD4AF37);
                v.animate().scaleX(f ? 1.06f : 1f).scaleY(f ? 1.06f : 1f).setDuration(120).start();
                if (f) Sounds.nav(AllAppsActivity.this);
            });
            ImageView icon = new ImageView(AllAppsActivity.this);
            icon.setId(R.id.icon);
            cell.addView(icon, new LinearLayout.LayoutParams((int) (64 * dp), (int) (64 * dp)));
            TextView name = new TextView(AllAppsActivity.this);
            name.setId(R.id.title);
            name.setTextColor(0xFFFFFFFF);
            name.setTextSize(13f);
            name.setTypeface(getResources().getFont(R.font.cairo));
            name.setGravity(Gravity.CENTER);
            name.setMaxLines(2);
            name.setEllipsize(android.text.TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams nlp = new LinearLayout.LayoutParams(-1, -2);
            nlp.topMargin = (int) (6 * dp);
            cell.addView(name, nlp);
            cell.setLayoutParams(new RecyclerView.LayoutParams(-1, (int) (132 * dp)));
            return new RecyclerView.ViewHolder(cell) { };
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int position) {
            App a = shown.get(position);
            ImageView icon = h.itemView.findViewById(R.id.icon);
            if (a.icon != null) icon.setImageDrawable(a.icon); else icon.setImageResource(R.drawable.ic_apps);
            ((TextView) h.itemView.findViewById(R.id.title)).setText(a.label);
            h.itemView.setContentDescription(a.label);
            h.itemView.setOnClickListener(v -> open(a));
        }

        @Override public int getItemCount() { return shown.size(); }
    }
}
