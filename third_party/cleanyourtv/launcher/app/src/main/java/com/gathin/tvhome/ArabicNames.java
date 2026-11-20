package com.gathin.tvhome;

import android.content.Context;

import java.util.HashMap;
import java.util.Map;

/**
 * 2.4 M2 (v1.7 #3): Arabic names for well-known TV apps on the tiles, used only while the TV speaks Arabic and the
 * owner kept the switch on. A name given on the phone, or by the theme, always wins. Legal apps only. Own list.
 */
final class ArabicNames {
    private ArabicNames() {}

    static final Map<String, String> NAMES = new HashMap<>();
    static {
        put("يوتيوب", "com.google.android.youtube.tv", "com.google.android.youtube.googletv");
        put("يوتيوب للأطفال", "com.google.android.youtube.tvkids");
        put("يوتيوب ميوزك", "com.google.android.youtube.tvmusic");
        put("نتفليكس", "com.netflix.ninja");
        put("شاهد", "net.mbc.shahid", "net.mbc.shahid.tv", "net.mbc.shahidTV", "com.mbc.shahid");
        put("برايم فيديو", "com.amazon.amazonvideo.livingroom");
        put("ديزني بلس", "com.disney.disneyplus");
        put("أو إس إن بلس", "com.osn.go", "com.anghami.osnplus");
        put("ستارز بلاي", "com.parsifal.starz", "com.starzplay.arabia");
        put("تود", "tv.tod.android", "com.todtv.tod");
        put("إس تي سي تي في", "com.stctv.app", "tv.stc.android", "com.intigral.jawwytv");
        put("ووتش إت", "com.watchit.android", "com.watchit.tv");
        put("الأولى", "sa.sba.aloula");
        put("أوان", "ae.dmi.awaan");
        put("أنغامي", "com.anghami");
        put("سبوتيفاي", "com.spotify.tv.android");
        put("بي إن كونكت", "com.bein.connect");
        put("يانغو بلاي", "com.yango.play");
        put("متجر بلاي", "com.android.vending");
        put("سمارت تيوب", "com.teamsmart.videomanager.tv", "org.smarttube.beta", "com.liskovsoft.smarttubetv.beta", "com.liskovsoft.smarttubetv");
        put("تنزيل التطبيقات", "com.esaba.downloader");
        put("كودي", "org.xbmc.kodi");
        put("مشغل في إل سي", "org.videolan.vlc");
        put("مشغل إم إكس", "com.mxtech.videoplayer.ad", "com.mxtech.videoplayer.pro");
        put("بليكس", "com.plexapp.android");
        put("الطقس والصلاة", "com.midroid.mi5gtv.prayer");
        put("مدير الملفات", "com.midroid.mi5gtv.files");
        put("قياس السرعة", "com.midroid.mi5gtv.speedtest");
        put("مانع الإعلانات", "com.midroid.mi5gtv.dns");
        put("مباريات اليوم", "com.midroid.mi5gtv.matches");
    }

    private static void put(String name, String... packages) { for (String p : packages) NAMES.put(p, name); }

    /** The Arabic name for pkg when the language is Arabic, or null. */
    static String of(String pkg, String language) {
        if (pkg == null || !"ar".equals(language)) return null;
        return NAMES.get(pkg);
    }

    /** The Arabic name for pkg on this TV right now (switch on, Arabic language), or null. */
    static String of(Context ctx, String pkg) {
        if (!LauncherSwitches.arabicNames(ctx)) return null;
        return of(pkg, ctx.getResources().getConfiguration().getLocales().get(0).getLanguage());
    }
}
