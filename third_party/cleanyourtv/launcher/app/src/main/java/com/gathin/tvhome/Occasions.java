package com.gathin.tvhome;

import android.content.Context;
import android.content.res.Resources;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 2.4 M4 (backlog 16 and 5, own code): countdowns to Ramadan, Eid al-Fitr, the Day of Arafah (Hajj), Eid al-Adha and
 * one Gulf national day, and the screensaver's calendar notes (the day of Ramadan, the white days). Hijri dates come
 * from ICU's Umm al-Qura calendar, so they are approximate: the real day follows the moon sighting. Only dates and
 * names are shown; no religious text is written or generated here.
 */
final class Occasions {
    static final String COUNTDOWNS = "mi5g_countdowns";
    static final String COUNTRY = "mi5g_national_day";
    static final String SAVER_INFO = "mi5g_saver_islamic_info";

    /** Gulf national days: country code, month, day. Oman: 20 November (Royal Decree 15/2025). */
    static final List<String> COUNTRIES = Arrays.asList("", "OM", "SA", "AE", "QA", "BH", "KW");
    private static final int[][] NATIONAL = {{11, 20}, {9, 23}, {12, 2}, {12, 18}, {12, 16}, {2, 25}};

    static final class Countdown {
        final int labelRes;
        final LocalDate date;
        final long days;
        final boolean approximate;
        Countdown(int labelRes, LocalDate date, long days, boolean approximate) {
            this.labelRes = labelRes; this.date = date; this.days = days; this.approximate = approximate;
        }
    }

    private Occasions() {}

    static boolean countdownsOn(Context ctx) { return LauncherSwitches.prefs(ctx).getBoolean(COUNTDOWNS, false); }
    static void setCountdowns(Context ctx, boolean on) { LauncherSwitches.prefs(ctx).edit().putBoolean(COUNTDOWNS, on).apply(); }
    static boolean saverInfoOn(Context ctx) { return LauncherSwitches.prefs(ctx).getBoolean(SAVER_INFO, false); }
    static void setSaverInfo(Context ctx, boolean on) { LauncherSwitches.prefs(ctx).edit().putBoolean(SAVER_INFO, on).apply(); }
    static String country(Context ctx) {
        String c = LauncherSwitches.prefs(ctx).getString(COUNTRY, "");
        return COUNTRIES.contains(c) ? c : "";
    }
    static void setCountry(Context ctx, String c) { LauncherSwitches.prefs(ctx).edit().putString(COUNTRY, COUNTRIES.contains(c) ? c : "").apply(); }
    static String nextCountry(String c) {
        int i = COUNTRIES.indexOf(c);
        return COUNTRIES.get((i + 1) % COUNTRIES.size());
    }

    static int countryRes(String c) {
        switch (c) {
            case "OM": return R.string.mi5g_nd_om;
            case "SA": return R.string.mi5g_nd_sa;
            case "AE": return R.string.mi5g_nd_ae;
            case "QA": return R.string.mi5g_nd_qa;
            case "BH": return R.string.mi5g_nd_bh;
            case "KW": return R.string.mi5g_nd_kw;
            default: return R.string.mi5g_nd_none;
        }
    }

    /** {hijri year, month 1..12, day} of a Gregorian date (Umm al-Qura). */
    static int[] hijri(LocalDate d) {
        android.icu.util.IslamicCalendar cal = new android.icu.util.IslamicCalendar();
        cal.setCalculationType(android.icu.util.IslamicCalendar.CalculationType.ISLAMIC_UMALQURA);
        cal.setTimeZone(android.icu.util.TimeZone.GMT_ZONE);
        cal.setTimeInMillis(d.toEpochDay() * 86_400_000L + 12 * 3_600_000L);
        return new int[]{cal.get(android.icu.util.Calendar.EXTENDED_YEAR), cal.get(android.icu.util.Calendar.MONTH) + 1, cal.get(android.icu.util.Calendar.DAY_OF_MONTH)};
    }

    /** The Gregorian date of a Hijri day in a Hijri year (Umm al-Qura). */
    static LocalDate gregorian(int year, int month, int day) {
        android.icu.util.IslamicCalendar cal = new android.icu.util.IslamicCalendar();
        cal.setCalculationType(android.icu.util.IslamicCalendar.CalculationType.ISLAMIC_UMALQURA);
        cal.setTimeZone(android.icu.util.TimeZone.GMT_ZONE);
        cal.clear();
        cal.set(android.icu.util.Calendar.EXTENDED_YEAR, year);
        cal.set(android.icu.util.Calendar.MONTH, month - 1);
        cal.set(android.icu.util.Calendar.DAY_OF_MONTH, day);
        cal.set(android.icu.util.Calendar.HOUR_OF_DAY, 12);
        return LocalDate.ofEpochDay(Math.floorDiv(cal.getTimeInMillis(), 86_400_000L));
    }

    /** The next (or today's) Gregorian date of a Hijri month and day. */
    static LocalDate nextHijri(LocalDate today, int month, int day) {
        int y = hijri(today)[0];
        LocalDate d = gregorian(y, month, day);
        return d.isBefore(today) ? gregorian(y + 1, month, day) : d;
    }

    static LocalDate nextNational(LocalDate today, String country) {
        int i = COUNTRIES.indexOf(country) - 1;
        if (i < 0) return null;
        LocalDate d = LocalDate.of(today.getYear(), NATIONAL[i][0], NATIONAL[i][1]);
        return d.isBefore(today) ? d.plusYears(1) : d;
    }

    /** Every countdown from today, nearest first. */
    static List<Countdown> upcoming(LocalDate today, String country) {
        List<Countdown> out = new ArrayList<>();
        int[][] hijriDays = {{9, 1}, {10, 1}, {12, 9}, {12, 10}};
        int[] labels = {R.string.mi5g_occ_ramadan, R.string.mi5g_occ_eid_fitr, R.string.mi5g_occ_arafah, R.string.mi5g_occ_eid_adha};
        for (int i = 0; i < hijriDays.length; i++) {
            LocalDate d = nextHijri(today, hijriDays[i][0], hijriDays[i][1]);
            out.add(new Countdown(labels[i], d, ChronoUnit.DAYS.between(today, d), true));
        }
        LocalDate n = nextNational(today, country);
        if (n != null) out.add(new Countdown(countryRes(country), n, ChronoUnit.DAYS.between(today, n), false));
        Collections.sort(out, (a, b) -> Long.compare(a.days, b.days));
        return out;
    }

    /** "Ramadan in 12 days" / "Today: Eid al-Fitr", with "about" for the Hijri ones. */
    static String line(Resources res, Countdown c) {
        String name = res.getString(c.labelRes);
        if (c.days == 0) return res.getString(R.string.mi5g_occ_today, name);
        String days = res.getQuantityString(R.plurals.mi5g_days, (int) Math.min(c.days, Integer.MAX_VALUE), (int) c.days);
        return res.getString(c.approximate ? R.string.mi5g_occ_in_about : R.string.mi5g_occ_in, name, days);
    }

    /** The Home line: the nearest countdown, or "" when off. */
    static String homeLine(Context ctx, LocalDate today) {
        if (!countdownsOn(ctx)) return "";
        try {
            List<Countdown> list = upcoming(today, country(ctx));
            return list.isEmpty() ? "" : line(ctx.getResources(), list.get(0));
        } catch (Throwable e) {
            return "";   // no ICU Islamic calendar on this TV
        }
    }

    /**
     * The screensaver's calendar note (off by default): "Day 5 of Ramadan" during Ramadan, the white days (13th to
     * 15th of each Hijri month, and the day before), otherwise the nearest countdown. Dates only.
     */
    static String saverLine(Context ctx, LocalDate today) {
        if (!saverInfoOn(ctx)) return "";
        try {
            int[] h = hijri(today);
            Resources res = ctx.getResources();
            if (h[1] == 9) return res.getString(R.string.mi5g_occ_ramadan_day, h[2]);
            if (h[2] >= 13 && h[2] <= 15) return res.getString(R.string.mi5g_occ_white_days);
            if (h[2] == 12) return res.getString(R.string.mi5g_occ_white_days_tomorrow);
            List<Countdown> list = upcoming(today, country(ctx));
            return list.isEmpty() ? "" : line(res, list.get(0));
        } catch (Throwable e) {
            return "";
        }
    }
}
