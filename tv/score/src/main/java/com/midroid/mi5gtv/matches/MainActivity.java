package com.midroid.mi5gtv.matches;

import android.app.Activity;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

public final class MainActivity extends Activity {

    // League definitions: {code, arabicName, espnSlug}
    static final String[][] LEAGUES = {
        {"ksa.1", "دوري روشن السعودي", "ksa.1"},
        {"uefa.champions", "دوري أبطال أوروبا", "uefa.champions"},
        {"eng.1", "الدوري الإنجليزي الممتاز", "eng.1"},
        {"esp.1", "الدوري الإسباني", "esp.1"},
        {"ita.1", "الدوري الإيطالي", "ita.1"},
        {"egy.1", "الدوري المصري", ""},
        {"qat.1", "دوري نجوم قطر", ""},
        {"uae.1", "دوري أدنوك الإماراتي", ""},
        {"afc.champions", "دوري أبطال آسيا للنخبة", "afc.champions"},
        {"fifa.world", "البطولات الدولية والمنتخبات", "fifa.world"}
    };

    // Additional ESPN leagues to query so Bayern Munich & PSG live matches appear automatically
    private static final String[][] EXTRA_ESPN_LEAGUES = {
        {"ger.1", "الدوري الألماني", "ger.1"},
        {"fra.1", "الدوري الفرنسي", "fra.1"}
    };

    // 24 Popular selectable teams: {arabicName, leagueCode, englishAliasesPipeSeparated}
    private static final String[][] TEAMS = {
        {"الهلال", "ksa.1", "Al Hilal|Al-Hilal|Hilal"},
        {"النصر", "ksa.1", "Al Nassr|Al-Nassr|Nassr"},
        {"الاتحاد", "ksa.1", "Al Ittihad|Al-Ittihad|Ittihad"},
        {"الأهلي السعودي", "ksa.1", "Al Ahli|Al-Ahli|Al-Ahli Saudi|Ahli Jeddah"},
        {"الشباب", "ksa.1", "Al Shabab|Al-Shabab|Shabab"},
        {"ريال مدريد", "esp.1", "Real Madrid"},
        {"برشلونة", "esp.1", "Barcelona|FC Barcelona"},
        {"ليفربول", "eng.1", "Liverpool"},
        {"مانشستر سيتي", "eng.1", "Manchester City|Man City"},
        {"أرسنال", "eng.1", "Arsenal"},
        {"تشيلسي", "eng.1", "Chelsea"},
        {"مانشستر يونايتد", "eng.1", "Manchester United|Man United|Man Utd"},
        {"بايرن ميونخ", "ger.1", "Bayern Munich|FC Bayern Munich|Bayern München|Bayern"},
        {"باريس سان جيرمان", "fra.1", "Paris Saint-Germain|PSG|Paris SG"},
        {"إنتر ميلان", "ita.1", "Inter Milan|Internazionale|Inter"},
        {"ميلان", "ita.1", "AC Milan|Milan"},
        {"يوفنتوس", "ita.1", "Juventus"},
        {"الأهلي المصري", "egy.1", "Al Ahly|Al Ahly SC|Ahly Cairo"},
        {"الزمالك", "egy.1", "Zamalek|Zamalek SC"},
        {"السد", "qat.1", "Al Sadd|Al-Sadd"},
        {"العين", "uae.1", "Al Ain|Al-Ain"},
        {"الرجاء", "afc.champions", "Raja Casablanca|Raja CA|Raja"},
        {"الوداد", "afc.champions", "Wydad AC|Wydad Casablanca|Wydad"},
        {"الترجي", "afc.champions", "Esperance|ES Tunis|Espérance"}
    };

    private static final String[] DEFAULT_FAV_TEAMS = {
        "الهلال", "النصر", "الاتحاد", "الأهلي السعودي",
        "ريال مدريد", "برشلونة", "ليفربول", "مانشستر سيتي", "الأهلي المصري"
    };

    private static final String[] DEFAULT_FAV_LEAGUES = {
        "ksa.1", "uefa.champions", "eng.1", "esp.1", "ita.1", "egy.1", "afc.champions"
    };

    private static final Map<String, String> EN_TO_AR_TEAMS = new HashMap<String, String>();
    static {
        for (int i = 0; i < TEAMS.length; i++) {
            String ar = TEAMS[i][0];
            String[] aliases = TEAMS[i][2].split("\\|");
            for (int j = 0; j < aliases.length; j++) {
                EN_TO_AR_TEAMS.put(aliases[j].toLowerCase(Locale.US).trim(), ar);
            }
        }
        // Extra well-known clubs so ESPN live schedules read naturally in Arabic
        EN_TO_AR_TEAMS.put("tottenham hotspur", "توتنهام");
        EN_TO_AR_TEAMS.put("spurs", "توتنهام");
        EN_TO_AR_TEAMS.put("newcastle united", "نيوكاسل يونايتد");
        EN_TO_AR_TEAMS.put("aston villa", "أستون فيلا");
        EN_TO_AR_TEAMS.put("west ham united", "وست هام يونايتد");
        EN_TO_AR_TEAMS.put("brighton & hove albion", "برايتون");
        EN_TO_AR_TEAMS.put("brighton", "برايتون");
        EN_TO_AR_TEAMS.put("crystal palace", "كريستال بالاس");
        EN_TO_AR_TEAMS.put("everton", "إيفرتون");
        EN_TO_AR_TEAMS.put("fulham", "فولهام");
        EN_TO_AR_TEAMS.put("brentford", "برينتفورد");
        EN_TO_AR_TEAMS.put("wolverhampton wanderers", "وولفرهامبتون");
        EN_TO_AR_TEAMS.put("nottingham forest", "نوتنغهام فورست");
        EN_TO_AR_TEAMS.put("bournemouth", "بورنموث");
        EN_TO_AR_TEAMS.put("atletico madrid", "أتلتيكو مدريد");
        EN_TO_AR_TEAMS.put("atlético madrid", "أتلتيكو مدريد");
        EN_TO_AR_TEAMS.put("sevilla", "إشبيلية");
        EN_TO_AR_TEAMS.put("real sociedad", "ريال سوسيداد");
        EN_TO_AR_TEAMS.put("villarreal", "فياريال");
        EN_TO_AR_TEAMS.put("real betis", "ريال بيتيس");
        EN_TO_AR_TEAMS.put("athletic club", "أتلتيك بلباو");
        EN_TO_AR_TEAMS.put("valencia", "فالنسيا");
        EN_TO_AR_TEAMS.put("girona", "جيرونا");
        EN_TO_AR_TEAMS.put("napoli", "نابولي");
        EN_TO_AR_TEAMS.put("as roma", "روما");
        EN_TO_AR_TEAMS.put("roma", "روما");
        EN_TO_AR_TEAMS.put("lazio", "لاتسيو");
        EN_TO_AR_TEAMS.put("atalanta", "أتالانتا");
        EN_TO_AR_TEAMS.put("fiorentina", "فيورنتينا");
        EN_TO_AR_TEAMS.put("borussia dortmund", "بوروسيا دورتموند");
        EN_TO_AR_TEAMS.put("bayer leverkusen", "باير ليفركوزن");
        EN_TO_AR_TEAMS.put("rb leipzig", "لايبزيغ");
        EN_TO_AR_TEAMS.put("marseille", "مارسيليا");
        EN_TO_AR_TEAMS.put("lyon", "ليون");
        EN_TO_AR_TEAMS.put("monaco", "موناكو");
        EN_TO_AR_TEAMS.put("al ettifaq", "الاتفاق");
        EN_TO_AR_TEAMS.put("al taawoun", "التعاون");
        EN_TO_AR_TEAMS.put("al fateh", "الفتح");
        EN_TO_AR_TEAMS.put("al qadsiah", "القادسية");
        EN_TO_AR_TEAMS.put("al riyadh", "الرياض");
        EN_TO_AR_TEAMS.put("al khaleej", "الخليج");
        EN_TO_AR_TEAMS.put("al feiha", "الفيحاء");
        EN_TO_AR_TEAMS.put("al raed", "الرائد");
        EN_TO_AR_TEAMS.put("al wehda", "الوحدة");
        EN_TO_AR_TEAMS.put("damac", "ضمك");
        EN_TO_AR_TEAMS.put("pyramids fc", "بيراميدز");
        EN_TO_AR_TEAMS.put("al duhail", "الدحيل");
        EN_TO_AR_TEAMS.put("al rayyan", "الريان");
        EN_TO_AR_TEAMS.put("al gharafa", "الغرافة");
        EN_TO_AR_TEAMS.put("al arabi", "العربي");
        EN_TO_AR_TEAMS.put("al wasl", "الوصل");
        EN_TO_AR_TEAMS.put("shabab al ahli", "شباب الأهلي");
        EN_TO_AR_TEAMS.put("al jazira", "الجزيرة");
        EN_TO_AR_TEAMS.put("sharjah", "الشارقة");
    }

    private static final class MatchItem {
        final String leagueCode;
        final String leagueAr;
        final String homeAr;
        final String awayAr;
        final String homeScore;
        final String awayScore;
        final String state; // "in", "post", "pre"
        final String statusText; // e.g., "مباشر الآن 🔴 68'" or "انتهت" or "9:00 م"
        final String timeText;   // local kickoff time e.g., "9:00 م"
        final boolean isLiveSource;
        String eventId = "", slug = "";

        MatchItem(String leagueCode, String leagueAr, String homeAr, String awayAr,
                  String homeScore, String awayScore, String state, String statusText,
                  String timeText, boolean isLiveSource) {
            this.leagueCode = leagueCode;
            this.leagueAr = leagueAr;
            this.homeAr = homeAr;
            this.awayAr = awayAr;
            this.homeScore = homeScore;
            this.awayScore = awayScore;
            this.state = state;
            this.statusText = statusText;
            this.timeText = timeText;
            this.isLiveSource = isLiveSource;
        }
    }

    private SharedPreferences prefs;
    private final Set<String> favTeams = new HashSet<String>();
    private final Set<String> favLeagues = new HashSet<String>();
    private int activeTab = 0; // 0: فرقي المفضلة, 1: مباريات اليوم, 2: مباريات الغد, 3: تحديد الفرق والدوريات

    private final List<MatchItem> todayMatches = new ArrayList<MatchItem>();
    private final List<MatchItem> tomorrowMatches = new ArrayList<MatchItem>();
    private boolean lastFetchOnline = false;
    private boolean isFetching = false;
    private String lastUpdatedTime = "--:--";

    private TextView statusBannerText;
    private TextView subStatusText;
    private TextView refreshBtn;
    private final TextView[] tabButtons = new TextView[4];
    private LinearLayout contentContainer;
    private final Handler handler = new Handler(Looper.getMainLooper());

    private final Runnable autoRefreshRunnable = new Runnable() {
        @Override
        public void run() {
            fetchMatchesAsync(false);
            handler.postDelayed(this, 60000L);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("matches_prefs", 0);
        loadPreferences();

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Color.parseColor("#060D0A"));
        scroll.setFillViewport(true);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setPadding(dp(40), dp(28), dp(40), dp(28));

        // Header Row: Title + Manual Refresh Button
        LinearLayout headerRow = new LinearLayout(this);
        headerRow.setOrientation(LinearLayout.HORIZONTAL);
        headerRow.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout titleCol = new LinearLayout(this);
        titleCol.setOrientation(LinearLayout.VERTICAL);
        TextView title = makeText("⚽ Mi5G Score — مباريات فرقك المفضلة", 25, "#F2F7F5", true);
        subStatusText = makeText("", 13, "#95B0A4", false);
        subStatusText.setPadding(0, dp(3), 0, 0);
        titleCol.addView(title);
        titleCol.addView(subStatusText);

        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        headerRow.addView(titleCol, titleLp);

        refreshBtn = makeText("🔄 تحديث النتائج", 14, "#F2F7F5", true);
        refreshBtn.setGravity(Gravity.CENTER);
        refreshBtn.setPadding(dp(18), dp(10), dp(18), dp(10));
        styleInteractiveButton(refreshBtn, "#0E241B", "#2EC4B6", "#16382B", "#D4AF37", dp(999));
        refreshBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                fetchMatchesAsync(true);
            }
        });
        headerRow.addView(refreshBtn);
        TextView tableBtn = makeText("📊 جدول الترتيب", 14, "#F2F7F5", true);
        tableBtn.setGravity(Gravity.CENTER);
        tableBtn.setPadding(dp(18), dp(10), dp(18), dp(10));
        styleInteractiveButton(tableBtn, "#0E241B", "#2EC4B6", "#16382B", "#D4AF37", dp(999));
        tableBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new android.content.Intent(MainActivity.this, StandingsActivity.class));
            }
        });
        LinearLayout.LayoutParams tbl = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        tbl.setMarginStart(dp(10));
        headerRow.addView(tableBtn, tbl);
        root.addView(headerRow);

        // Status Hero Banner
        LinearLayout hero = new LinearLayout(this);
        hero.setOrientation(LinearLayout.VERTICAL);
        hero.setPadding(dp(20), dp(14), dp(20), dp(14));
        hero.setBackground(roundRect("#0B1A14", "#2EC4B6", dp(16), dp(2)));
        statusBannerText = makeText("جاري تحميل جدول المباريات والنتائج المباشرة...", 15, "#E8F5EE", true);
        hero.addView(statusBannerText);
        LinearLayout.LayoutParams heroLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        heroLp.setMargins(0, dp(14), 0, dp(16));
        root.addView(hero, heroLp);

        // Filter Tabs Row
        LinearLayout tabsRow = new LinearLayout(this);
        tabsRow.setOrientation(LinearLayout.HORIZONTAL);
        String[] tabLabels = {
            "⭐ فرقي المفضلة",
            "🔥 مباريات اليوم",
            "📅 مباريات الغد",
            "⚙️ تحديد الفرق والدوريات"
        };
        for (int i = 0; i < tabLabels.length; i++) {
            final int tabIdx = i;
            final TextView tab = makeText(tabLabels[i], 15, "#F2F7F5", true);
            tab.setGravity(Gravity.CENTER);
            tab.setFocusable(true);
            tab.setClickable(true);
            tab.setPadding(dp(14), dp(12), dp(14), dp(12));
            tabButtons[i] = tab;
            tab.setOnFocusChangeListener(new View.OnFocusChangeListener() {
                @Override
                public void onFocusChange(View v, boolean hasFocus) {
                    updateTabStyles();
                }
            });
            tab.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    activeTab = tabIdx;
                    prefs.edit().putInt("active_tab", activeTab).apply();
                    updateTabStyles();
                    renderContent();
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            lp.setMargins(dp(4), 0, dp(4), 0);
            tabsRow.addView(tab, lp);
        }
        root.addView(tabsRow);

        contentContainer = new LinearLayout(this);
        contentContainer.setOrientation(LinearLayout.VERTICAL);
        contentContainer.setPadding(0, dp(16), 0, dp(16));
        root.addView(contentContainer);

        scroll.addView(root);
        setContentView(scroll);

        updateTabStyles();
        if (activeTab >= 0 && activeTab < tabButtons.length) {
            tabButtons[activeTab].requestFocus();
        }

        // Populate immediate schedule so screen is instant, then fetch live API
        rebuildFallbackSchedule(false);
        renderContent();
        fetchMatchesAsync(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        handler.removeCallbacks(autoRefreshRunnable);
        handler.postDelayed(autoRefreshRunnable, 60000L);
    }

    @Override
    protected void onPause() {
        super.onPause();
        handler.removeCallbacks(autoRefreshRunnable);
    }

    private void loadPreferences() {
        activeTab = prefs.getInt("active_tab", 0);
        if (activeTab < 0 || activeTab > 3) activeTab = 0;

        boolean initialized = prefs.getBoolean("initialized_v1", false);
        favTeams.clear();
        favLeagues.clear();
        if (!initialized) {
            favTeams.addAll(Arrays.asList(DEFAULT_FAV_TEAMS));
            favLeagues.addAll(Arrays.asList(DEFAULT_FAV_LEAGUES));
            savePreferences();
            prefs.edit().putBoolean("initialized_v1", true).apply();
        } else {
            String teamsCsv = prefs.getString("fav_teams_csv", "");
            if (!TextUtils.isEmpty(teamsCsv)) {
                String[] parts = teamsCsv.split(",");
                for (int i = 0; i < parts.length; i++) {
                    String t = parts[i].trim();
                    if (!t.isEmpty()) favTeams.add(t);
                }
            }
            String leaguesCsv = prefs.getString("fav_leagues_csv", "");
            if (!TextUtils.isEmpty(leaguesCsv)) {
                String[] parts = leaguesCsv.split(",");
                for (int i = 0; i < parts.length; i++) {
                    String l = parts[i].trim();
                    if (!l.isEmpty()) favLeagues.add(l);
                }
            }
        }
    }

    private void savePreferences() {
        prefs.edit()
            .putString("fav_teams_csv", TextUtils.join(",", favTeams))
            .putString("fav_leagues_csv", TextUtils.join(",", favLeagues))
            .apply();
    }

    private void updateTabStyles() {
        for (int i = 0; i < tabButtons.length; i++) {
            TextView btn = tabButtons[i];
            if (btn == null) continue;
            boolean selected = (i == activeTab);
            boolean focused = btn.hasFocus();
            String fill = focused ? "#16382B" : (selected ? "#0F291E" : "#0B1410");
            String stroke = focused ? "#D4AF37" : (selected ? "#2EC4B6" : "#1E3329");
            int strokeW = dp(focused ? 3 : (selected ? 2 : 1));
            btn.setBackground(roundRect(fill, stroke, dp(12), strokeW));
            btn.setTextColor(Color.parseColor(focused ? "#FFD166" : (selected ? "#2EC4B6" : "#D5E5DD")));
        }
    }

    private void updateHeaderBanner() {
        TimeZone tz = TimeZone.getDefault();
        int offsetHours = tz.getOffset(System.currentTimeMillis()) / (1000 * 3600);
        String tzLabel = "UTC" + (offsetHours >= 0 ? "+" : "") + offsetHours;
        String connLabel = isFetching
            ? "⏳ جاري تحديث النتائج المباشرة..."
            : (lastFetchOnline
                ? "🟢 متصل • تحديث مباشر + جدول الدوريات (" + lastUpdatedTime + ")"
                : "📡 وضع عدم الاتصال • جدول المباريات المدمج (" + lastUpdatedTime + ")");
        subStatusText.setText("التوقيت المحلي للتلفاز (" + tzLabel + ")  •  تحديث تلقائي كل 60 ثانية  •  " + connLabel);

        statusBannerText.setText(
            "⭐ فرقك المفضلة (" + favTeams.size() + ")   |   🏆 الدوريات المفضلة (" + favLeagues.size() + ")   •   "
            + "استخدم أسهم الريموت للتنقل وزر (موافق OK) لاختيار الفرق أو تحديث النتائج"
        );
    }

    private void renderContent() {
        updateHeaderBanner();
        contentContainer.removeAllViews();

        if (activeTab == 3) {
            renderSelectionPanel();
            return;
        }

        List<MatchItem> sourceList = (activeTab == 2) ? tomorrowMatches : todayMatches;
        List<MatchItem> filtered = new ArrayList<MatchItem>();

        if (activeTab == 0) {
            if (favTeams.isEmpty() && favLeagues.isEmpty()) {
                renderEmptyFavoritesPrompt();
                return;
            }
            // Show today's + tomorrow's matches for favorite teams (or favorite leagues if team matches)
            for (int i = 0; i < todayMatches.size(); i++) {
                MatchItem m = todayMatches.get(i);
                if (isFavoriteMatch(m)) filtered.add(m);
            }
            if (filtered.size() < 4) {
                for (int i = 0; i < tomorrowMatches.size(); i++) {
                    MatchItem m = tomorrowMatches.get(i);
                    if (isFavoriteMatch(m)) filtered.add(m);
                }
            }
        } else {
            filtered.addAll(sourceList);
        }

        if (filtered.isEmpty()) {
            renderEmptyFavoritesPrompt();
            return;
        }

        // Section subtitle + quick action to customize teams
        LinearLayout topInfoRow = new LinearLayout(this);
        topInfoRow.setOrientation(LinearLayout.HORIZONTAL);
        topInfoRow.setGravity(Gravity.CENTER_VERTICAL);
        topInfoRow.setPadding(0, 0, 0, dp(10));

        String sectionHeader = (activeTab == 0)
            ? "⭐ مباريات فرقك ودورياتك المفضلة (" + filtered.size() + " مباراة)"
            : (activeTab == 1
                ? "🔥 جدول ونتائج مباريات اليوم (" + filtered.size() + " مباراة)"
                : "📅 جدول ومواعيد مباريات الغد (" + filtered.size() + " مباراة)");
        TextView secTv = makeText(sectionHeader, 17, "#2EC4B6", true);
        LinearLayout.LayoutParams secLp = new LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        topInfoRow.addView(secTv, secLp);

        TextView quickPickBtn = makeText("⚙️ تعديل الفرق المفضلة (" + favTeams.size() + ")", 13, "#D4AF37", true);
        quickPickBtn.setPadding(dp(14), dp(8), dp(14), dp(8));
        styleInteractiveButton(quickPickBtn, "#102119", "#284B3C", "#183629", "#D4AF37", dp(999));
        quickPickBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                activeTab = 3;
                prefs.edit().putInt("active_tab", activeTab).apply();
                updateTabStyles();
                renderContent();
            }
        });
        topInfoRow.addView(quickPickBtn);
        contentContainer.addView(topInfoRow);

        // Render match cards in 2-column TV grid rows for easy D-Pad navigation
        LinearLayout currentRow = null;
        for (int i = 0; i < filtered.size(); i++) {
            if (i % 2 == 0) {
                currentRow = new LinearLayout(this);
                currentRow.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                rowLp.setMargins(0, 0, 0, dp(10));
                contentContainer.addView(currentRow, rowLp);
            }
            View card = buildMatchCard(filtered.get(i));
            LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            cardLp.setMargins(dp(5), 0, dp(5), 0);
            currentRow.addView(card, cardLp);
        }
        if (filtered.size() % 2 == 1 && currentRow != null) {
            View spacer = new View(this);
            currentRow.addView(spacer, new LinearLayout.LayoutParams(0, 1, 1f));
        }
    }

    private void renderEmptyFavoritesPrompt() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(28), dp(28), dp(28), dp(28));
        box.setBackground(roundRect("#0B1712", "#234235", dp(16), dp(2)));

        TextView msg = makeText("لم تقم بتحديد أي فرق مفضلة بعد", 20, "#F2F7F5", true);
        TextView sub = makeText("اختر أنديتك ومنتخباتك المفضلة (مثل الهلال، النصر، ريال مدريد، برشلونة، ليفربول، الأهلي) لتظهر مبارياتها هنا فورًا.", 15, "#A3BEB2", false);
        sub.setPadding(0, dp(8), 0, dp(18));
        sub.setGravity(Gravity.CENTER);

        TextView pickBtn = makeText("⚙️ تحديد الفرق والدوريات المفضلة بالريموت", 16, "#06140E", true);
        pickBtn.setPadding(dp(24), dp(12), dp(24), dp(12));
        styleInteractiveButton(pickBtn, "#2EC4B6", "#D4AF37", "#D4AF37", "#FFFFFF", dp(999));
        pickBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                activeTab = 3;
                prefs.edit().putInt("active_tab", activeTab).apply();
                updateTabStyles();
                renderContent();
            }
        });

        box.addView(msg);
        box.addView(sub);
        box.addView(pickBtn);
        contentContainer.addView(box);
    }

    private View buildMatchCard(final MatchItem m) {
        final LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp(18), dp(14), dp(18), dp(14));
        card.setFocusable(true);
        card.setClickable(true);

        final boolean homeFav = favTeams.contains(m.homeAr);
        final boolean awayFav = favTeams.contains(m.awayAr);
        final boolean hasFavTeam = homeFav || awayFav;
        final boolean isLive = "in".equals(m.state);

        final String baseFill = isLive ? "#11261D" : (hasFavTeam ? "#0E2018" : "#0B1511");
        final String baseStroke = isLive ? "#EF4444" : (hasFavTeam ? "#D4AF37" : "#1D352B");

        card.setBackground(roundRect(baseFill, baseStroke, dp(14), dp(isLive || hasFavTeam ? 2 : 1)));
        card.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                card.setBackground(roundRect(
                    hasFocus ? "#18382A" : baseFill,
                    hasFocus ? "#2EC4B6" : baseStroke,
                    dp(14),
                    dp(hasFocus ? 3 : (isLive || hasFavTeam ? 2 : 1))
                ));
            }
        });
        card.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!TextUtils.isEmpty(m.eventId)) {
                    startActivity(new android.content.Intent(MainActivity.this, MatchActivity.class)
                        .putExtra("slug", m.slug).putExtra("event", m.eventId)
                        .putExtra("title", m.homeAr + "  " + m.homeScore + " - " + m.awayScore + "  " + m.awayAr)
                        .putExtra("league", m.leagueAr));
                }
            }
        });
        card.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                // Hold OK: toggle both teams in favorites
                if (favTeams.contains(m.homeAr) && favTeams.contains(m.awayAr)) {
                    favTeams.remove(m.homeAr);
                    favTeams.remove(m.awayAr);
                } else {
                    favTeams.add(m.homeAr);
                    favTeams.add(m.awayAr);
                }
                savePreferences();
                renderContent();
                return true;
            }
        });

        // Top Row: League badge + Status badge
        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView leagueTv = makeText("🏆 " + m.leagueAr, 13, "#88B3A0", true);
        LinearLayout.LayoutParams lLp = new LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        topRow.addView(leagueTv, lLp);

        String statusColor = isLive ? "#FF6B6B" : ("post".equals(m.state) ? "#9CA3AF" : "#D4AF37");
        TextView statusTv = makeText(m.statusText, 13, statusColor, true);
        topRow.addView(statusTv);
        card.addView(topRow);

        // Middle Row: Home Team vs Score/Time vs Away Team
        LinearLayout midRow = new LinearLayout(this);
        midRow.setOrientation(LinearLayout.HORIZONTAL);
        midRow.setGravity(Gravity.CENTER_VERTICAL);
        midRow.setPadding(0, dp(10), 0, dp(6));

        String homeLabel = (homeFav ? "⭐ " : "") + m.homeAr;
        TextView homeTv = makeText(homeLabel, 17, homeFav ? "#FFD166" : "#F2F7F5", true);
        homeTv.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams hLp = new LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.15f);
        midRow.addView(homeTv, hLp);

        String centerText = "pre".equals(m.state)
            ? m.timeText
            : (m.homeScore + "  -  " + m.awayScore);
        TextView scoreBox = makeText(centerText, 17, isLive ? "#FFFFFF" : "#2EC4B6", true);
        scoreBox.setGravity(Gravity.CENTER);
        scoreBox.setPadding(dp(12), dp(6), dp(12), dp(6));
        scoreBox.setBackground(roundRect(isLive ? "#7F1D1D" : "#07120E", isLive ? "#EF4444" : "#1E3A2F", dp(10), dp(1)));
        midRow.addView(scoreBox);

        String awayLabel = m.awayAr + (awayFav ? " ⭐" : "");
        TextView awayTv = makeText(awayLabel, 17, awayFav ? "#FFD166" : "#F2F7F5", true);
        awayTv.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams aLp = new LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1.15f);
        midRow.addView(awayTv, aLp);

        card.addView(midRow);

        // Subtle footer hint
        String footerInfo = ("pre".equals(m.state) ? "موعد انطلاق المباراة بالتوقيت المحلي: " + m.timeText : "انطلقت الساعة " + m.timeText)
            + (hasFavTeam ? "  •  ⭐ ضمن فرقك المفضلة  •  OK للتفاصيل" : "  •  OK للتفاصيل، ضغط مطوّل للمفضلة");
        TextView footTv = makeText(footerInfo, 11, "#6E8F80", false);
        card.addView(footTv);

        return card;
    }

    private void renderSelectionPanel() {
        // Quick action bar inside the selection panel
        LinearLayout actionsRow = new LinearLayout(this);
        actionsRow.setOrientation(LinearLayout.HORIZONTAL);
        actionsRow.setGravity(Gravity.CENTER_VERTICAL);
        actionsRow.setPadding(0, 0, 0, dp(12));

        TextView panelTitle = makeText("اختر دورياتك وفرقك المفضلة بضغطة زر (موافق OK) بالريموت:", 16, "#D4AF37", true);
        LinearLayout.LayoutParams ptLp = new LinearLayout.LayoutParams(
            0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        actionsRow.addView(panelTitle, ptLp);

        TextView selectAllBtn = makeText("تحديد الكل", 13, "#F2F7F5", true);
        selectAllBtn.setPadding(dp(14), dp(8), dp(14), dp(8));
        styleInteractiveButton(selectAllBtn, "#0E241B", "#2EC4B6", "#16382B", "#D4AF37", dp(999));
        selectAllBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                for (int i = 0; i < TEAMS.length; i++) favTeams.add(TEAMS[i][0]);
                for (int i = 0; i < LEAGUES.length; i++) favLeagues.add(LEAGUES[i][0]);
                savePreferences();
                renderContent();
            }
        });
        actionsRow.addView(selectAllBtn);

        TextView doneBtn = makeText("عرض مبارياتي المفضلة ⭐", 13, "#06140E", true);
        doneBtn.setPadding(dp(16), dp(8), dp(16), dp(8));
        styleInteractiveButton(doneBtn, "#2EC4B6", "#D4AF37", "#D4AF37", "#FFFFFF", dp(999));
        LinearLayout.LayoutParams doneLp = new LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        doneLp.setMargins(dp(8), 0, 0, 0);
        doneBtn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                activeTab = 0;
                prefs.edit().putInt("active_tab", 0).apply();
                updateTabStyles();
                renderContent();
            }
        });
        actionsRow.addView(doneBtn, doneLp);
        contentContainer.addView(actionsRow);

        // Section 1: Leagues
        TextView leaguesHeader = makeText("🏆 الدوريات والبطولات (" + favLeagues.size() + "/" + LEAGUES.length + "):", 15, "#2EC4B6", true);
        leaguesHeader.setPadding(0, dp(4), 0, dp(8));
        contentContainer.addView(leaguesHeader);

        LinearLayout leagueRow = null;
        for (int i = 0; i < LEAGUES.length; i++) {
            if (i % 5 == 0) {
                leagueRow = new LinearLayout(this);
                leagueRow.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                rLp.setMargins(0, 0, 0, dp(8));
                contentContainer.addView(leagueRow, rLp);
            }
            final String code = LEAGUES[i][0];
            final String nameAr = LEAGUES[i][1];
            final boolean isSel = favLeagues.contains(code);

            final TextView chip = makeText((isSel ? "✅ " : "➕ ") + nameAr, 13, isSel ? "#FFD166" : "#D5E5DD", true);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(dp(10), dp(10), dp(10), dp(10));
            styleToggleChip(chip, isSel);
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (favLeagues.contains(code)) favLeagues.remove(code);
                    else favLeagues.add(code);
                    savePreferences();
                    renderContent();
                }
            });
            LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            cLp.setMargins(dp(4), 0, dp(4), 0);
            leagueRow.addView(chip, cLp);
        }

        // Section 2: Teams (24 Arab & European clubs)
        TextView teamsHeader = makeText("⭐ أشهر الفرق العربية والأوروبية (" + favTeams.size() + "/" + TEAMS.length + "):", 15, "#2EC4B6", true);
        teamsHeader.setPadding(0, dp(12), 0, dp(8));
        contentContainer.addView(teamsHeader);

        LinearLayout teamRow = null;
        for (int i = 0; i < TEAMS.length; i++) {
            if (i % 6 == 0) {
                teamRow = new LinearLayout(this);
                teamRow.setOrientation(LinearLayout.HORIZONTAL);
                LinearLayout.LayoutParams rLp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
                rLp.setMargins(0, 0, 0, dp(8));
                contentContainer.addView(teamRow, rLp);
            }
            final String teamAr = TEAMS[i][0];
            final boolean isSel = favTeams.contains(teamAr);

            final TextView chip = makeText((isSel ? "⭐ " : "") + teamAr, 14, isSel ? "#FFD166" : "#E5F0EB", true);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(dp(8), dp(10), dp(8), dp(10));
            styleToggleChip(chip, isSel);
            chip.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    if (favTeams.contains(teamAr)) favTeams.remove(teamAr);
                    else favTeams.add(teamAr);
                    savePreferences();
                    renderContent();
                }
            });
            LinearLayout.LayoutParams cLp = new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            cLp.setMargins(dp(4), 0, dp(4), 0);
            teamRow.addView(chip, cLp);
        }
    }

    private boolean isFavoriteMatch(MatchItem m) {
        if (favTeams.contains(m.homeAr) || favTeams.contains(m.awayAr)) return true;
        return favTeams.isEmpty() && favLeagues.contains(m.leagueCode);
    }

    private void fetchMatchesAsync(final boolean manual) {
        if (isFetching) return;
        isFetching = true;
        updateHeaderBanner();

        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<MatchItem> liveToday = new ArrayList<MatchItem>();
                final List<MatchItem> liveTomorrow = new ArrayList<MatchItem>();
                boolean anyNetworkSuccess = false;

                SimpleDateFormat ymdFormat = new SimpleDateFormat("yyyyMMdd", Locale.US);
                Calendar cal = Calendar.getInstance();
                String todayYmd = ymdFormat.format(cal.getTime());
                cal.add(Calendar.DAY_OF_YEAR, 1);
                String tomorrowYmd = ymdFormat.format(cal.getTime());

                List<String[]> allEspnLeagues = new ArrayList<String[]>();
                allEspnLeagues.addAll(Arrays.asList(LEAGUES));
                allEspnLeagues.addAll(Arrays.asList(EXTRA_ESPN_LEAGUES));

                for (int i = 0; i < allEspnLeagues.size(); i++) {
                    String leagueCode = allEspnLeagues.get(i)[0];
                    String leagueAr = allEspnLeagues.get(i)[1];
                    String slug = allEspnLeagues.get(i)[2];
                    if (TextUtils.isEmpty(slug)) continue;

                    if (fetchEspnDay(slug, leagueCode, leagueAr, todayYmd, liveToday)) {
                        anyNetworkSuccess = true;
                    }
                    if (fetchEspnDay(slug, leagueCode, leagueAr, tomorrowYmd, liveTomorrow)) {
                        anyNetworkSuccess = true;
                    }
                }

                final boolean online = anyNetworkSuccess;
                final String nowHm = new SimpleDateFormat("HH:mm", Locale.US).format(new Date());

                handler.post(new Runnable() {
                    @Override
                    public void run() {
                        isFetching = false;
                        lastFetchOnline = online;
                        lastUpdatedTime = nowHm;
                        mergeLiveWithFallback(liveToday, liveTomorrow, online);
                        renderContent();
                    }
                });
            }
        }).start();
    }

    private boolean fetchEspnDay(String slug, String leagueCode, String leagueAr, String ymd, List<MatchItem> out) {
        HttpURLConnection conn = null;
        try {
            String urlStr = "https://site.api.espn.com/apis/site/v2/sports/soccer/" + slug + "/scoreboard?dates=" + ymd;
            conn = (HttpURLConnection) new URL(urlStr).openConnection();
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android TV; Mi5G TV)");
            conn.setConnectTimeout(4500);
            conn.setReadTimeout(4500);
            if (conn.getResponseCode() != 200) return false;

            BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) sb.append(line);
            br.close();

            JSONObject root = new JSONObject(sb.toString());
            JSONArray events = root.optJSONArray("events");
            if (events == null) return true;

            for (int i = 0; i < events.length(); i++) {
                JSONObject ev = events.optJSONObject(i);
                if (ev == null) continue;
                String isoDate = ev.optString("date", "");
                String localTimeStr = formatIsoToLocalTime(isoDate);

                JSONArray comps = ev.optJSONArray("competitions");
                if (comps == null || comps.length() == 0) continue;
                JSONObject comp = comps.getJSONObject(0);

                JSONObject statusObj = comp.optJSONObject("status");
                String displayClock = statusObj != null ? statusObj.optString("displayClock", "") : "";
                JSONObject typeObj = statusObj != null ? statusObj.optJSONObject("type") : null;
                String state = typeObj != null ? typeObj.optString("state", "pre") : "pre";

                JSONArray competitors = comp.optJSONArray("competitors");
                if (competitors == null || competitors.length() < 2) continue;

                String homeNameEn = "";
                String awayNameEn = "";
                String homeScore = "0";
                String awayScore = "0";

                for (int c = 0; c < competitors.length(); c++) {
                    JSONObject teamEntry = competitors.getJSONObject(c);
                    String side = teamEntry.optString("homeAway", c == 0 ? "home" : "away");
                    String score = teamEntry.optString("score", "0");
                    JSONObject teamObj = teamEntry.optJSONObject("team");
                    String tName = teamObj != null
                        ? teamObj.optString("displayName", teamObj.optString("name", ""))
                        : "";
                    if ("home".equalsIgnoreCase(side)) {
                        homeNameEn = tName;
                        homeScore = score;
                    } else {
                        awayNameEn = tName;
                        awayScore = score;
                    }
                }

                String homeAr = translateTeamName(homeNameEn);
                String awayAr = translateTeamName(awayNameEn);

                String statusText;
                if ("in".equalsIgnoreCase(state)) {
                    statusText = "مباشر الآن 🔴 " + (!TextUtils.isEmpty(displayClock) ? displayClock : "");
                } else if ("post".equalsIgnoreCase(state)) {
                    statusText = "انتهت";
                } else {
                    statusText = "⏰ " + localTimeStr;
                    state = "pre";
                }

                MatchItem item = new MatchItem(
                    leagueCode, leagueAr, homeAr, awayAr,
                    homeScore, awayScore, state, statusText, localTimeStr, true
                );
                item.eventId = ev.optString("id", "");
                item.slug = slug;
                out.add(item);
            }
            return true;
        } catch (Exception ignored) {
            return false;
        } finally {
            if (conn != null) conn.disconnect();
        }
    }

    private void rebuildFallbackSchedule(boolean online) {
        mergeLiveWithFallback(new ArrayList<MatchItem>(), new ArrayList<MatchItem>(), online);
    }

    private void mergeLiveWithFallback(List<MatchItem> liveToday, List<MatchItem> liveTomorrow, boolean online) {
        todayMatches.clear();
        tomorrowMatches.clear();

        todayMatches.addAll(liveToday);
        tomorrowMatches.addAll(liveTomorrow);

        // Mi5G 2.0: only fixtures the source returned are shown; nothing is made up when offline.
    }

    static String translateTeamName(String enName) {
        if (TextUtils.isEmpty(enName)) return "فريق";
        String key = enName.toLowerCase(Locale.US).trim();
        String direct = EN_TO_AR_TEAMS.get(key);
        if (direct != null) return direct;

        for (Map.Entry<String, String> entry : EN_TO_AR_TEAMS.entrySet()) {
            if (key.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return enName;
    }

    private static String formatIsoToLocalTime(String isoUtc) {
        if (TextUtils.isEmpty(isoUtc)) return "8:00 م";
        try {
            String clean = isoUtc.replace("Z", "+0000");
            SimpleDateFormat sdf;
            if (clean.length() >= 20 && clean.charAt(16) == ':') {
                sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US);
            } else {
                sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mmZ", Locale.US);
            }
            Date d = sdf.parse(clean);
            if (d == null) return "8:00 م";
            Calendar localCal = Calendar.getInstance(TimeZone.getDefault());
            localCal.setTime(d);
            int h24 = localCal.get(Calendar.HOUR_OF_DAY);
            int min = localCal.get(Calendar.MINUTE);
            String ampm = (h24 >= 12) ? "م" : "ص";
            int h12 = h24 % 12;
            if (h12 == 0) h12 = 12;
            return String.format(Locale.US, "%d:%02d %s", h12, min, ampm);
        } catch (Exception e) {
            return "8:00 م";
        }
    }

    private void styleInteractiveButton(final TextView btn, final String fillHex, final String strokeHex,
                                        final String focusFillHex, final String focusStrokeHex, final int radiusPx) {
        btn.setFocusable(true);
        btn.setClickable(true);
        btn.setBackground(roundRect(fillHex, strokeHex, radiusPx, dp(2)));
        btn.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                btn.setBackground(roundRect(
                    hasFocus ? focusFillHex : fillHex,
                    hasFocus ? focusStrokeHex : strokeHex,
                    radiusPx,
                    dp(hasFocus ? 3 : 2)
                ));
            }
        });
    }

    private void styleToggleChip(final TextView chip, final boolean selected) {
        chip.setFocusable(true);
        chip.setClickable(true);
        final String fill = selected ? "#123124" : "#0B1511";
        final String stroke = selected ? "#2EC4B6" : "#1E3329";
        chip.setBackground(roundRect(fill, stroke, dp(10), dp(selected ? 2 : 1)));
        chip.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                chip.setBackground(roundRect(
                    hasFocus ? "#1A4231" : fill,
                    hasFocus ? "#D4AF37" : stroke,
                    dp(10),
                    dp(hasFocus ? 3 : (selected ? 2 : 1))
                ));
            }
        });
    }

    private TextView makeText(String text, int sp, String colorHex, boolean bold) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        tv.setTextColor(Color.parseColor(colorHex));
        if (bold) tv.setTypeface(Typeface.DEFAULT_BOLD);
        return tv;
    }

    private GradientDrawable roundRect(String fillHex, String strokeHex, int radiusPx, int strokePx) {
        GradientDrawable gd = new GradientDrawable();
        gd.setColor(Color.parseColor(fillHex));
        gd.setCornerRadius(radiusPx);
        gd.setStroke(strokePx, Color.parseColor(strokeHex));
        return gd;
    }

    private int dp(int v) {
        return Math.round(v * getResources().getDisplayMetrics().density);
    }
}
