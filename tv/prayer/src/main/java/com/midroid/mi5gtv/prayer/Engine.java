package com.midroid.mi5gtv.prayer;

import com.batoulapps.adhan.CalculationMethod;
import com.batoulapps.adhan.CalculationParameters;
import com.batoulapps.adhan.Coordinates;
import com.batoulapps.adhan.HighLatitudeRule;
import com.batoulapps.adhan.Madhab;
import com.batoulapps.adhan.PrayerAdjustments;
import com.batoulapps.adhan.PrayerTimes;
import com.batoulapps.adhan.data.DateComponents;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.chrono.HijrahDate;
import java.time.temporal.ChronoField;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/**
 * Prayer times on the Mawaqiti (Mi5G Prayer 2.0) engine: the same methods, Oman Ministry fit and
 * Hijri rule as the phone app, ported to Java for the TV (adhan-java, MIT, Batoul Apps).
 */
final class Engine {
    private Engine() {}

    static final int FAJR = 0, SUNRISE = 1, DHUHR = 2, ASR = 3, MAGHRIB = 4, ISHA = 5, COUNT = 6;
    static final String[] NAMES_AR = {"الفجر", "الشروق", "الظهر", "العصر", "المغرب", "العشاء"};
    static final String[] NAMES_EN = {"Fajr", "Sunrise", "Dhuhr", "Asr", "Maghrib", "Isha"};
    static final String[] HIJRI_MONTHS_AR = {"محرم", "صفر", "ربيع الأول", "ربيع الآخر", "جمادى الأولى", "جمادى الآخرة",
            "رجب", "شعبان", "رمضان", "شوال", "ذو القعدة", "ذو الحجة"};

    /** Method names match the phone app's enum (Mi5G Prayer 2.0 domain/PrayerEngine.kt). */
    static CalculationParameters params(String method) {
        CalculationParameters p;
        switch (method) {
            case "UMM_AL_QURA": p = CalculationMethod.UMM_AL_QURA.getParameters(); break;
            case "MWL": p = CalculationMethod.MUSLIM_WORLD_LEAGUE.getParameters(); break;
            case "DUBAI": p = CalculationMethod.DUBAI.getParameters(); break;
            case "QATAR": p = CalculationMethod.QATAR.getParameters(); break;
            case "KUWAIT": p = CalculationMethod.KUWAIT.getParameters(); break;
            case "EGYPTIAN": p = new CalculationParameters(19.5, 17.5); break;
            case "KARACHI": p = CalculationMethod.KARACHI.getParameters(); break;
            case "ISNA": p = CalculationMethod.NORTH_AMERICA.getParameters(); break;
            default: // OMAN: 18/18 with the Ministry's +5 on Dhuhr, Asr, Maghrib
                p = new CalculationParameters(18.0, 18.0);
                p.withMethodAdjustments(new PrayerAdjustments(0, 0, 5, 5, 5, 0));
        }
        return p;
    }

    /** Oman Ministry per-wilayat fit (same table as the phone app), only with the OMAN method. */
    static int[] fit(String placeId, int month) {
        switch (placeId) {
            case "rakhyut": return new int[]{-2, -2, -2, -2, -2, -2};
            case "jazer": return new int[]{-1, -1, -1, -1, -1, -1};
            case "mahout": return new int[]{1, 0, 0, 1, 0, 0};
            case "hamra": return HAMRA[month - 1];
            default: return new int[COUNT];
        }
    }

    private static final int[][] HAMRA = {
            {1, 1, 0, -2, -2, -1}, {0, 1, 0, -1, -1, -1}, {0, 0, 0, 0, 0, 0}, {-1, -1, 0, 1, 0, 1},
            {-2, -1, 0, 1, 1, 1}, {-2, -1, 0, 1, 1, 2}, {-2, -1, 0, 2, 1, 2}, {-1, -1, 0, 2, 1, 2},
            {-1, 0, 0, 1, 1, 1}, {0, 0, 0, 0, 0, 1}, {1, 1, 0, -1, -1, 0}, {1, 1, 0, -1, -1, -1},
    };

    /** The six times of [date] at the place in [s], truncated to the minute. */
    static ZonedDateTime[] day(Settings s, LocalDate date) {
        CalculationParameters p = params(s.method);
        p.madhab = s.hanafi ? Madhab.HANAFI : Madhab.SHAFI;
        if ("MIDDLE_OF_NIGHT".equals(s.highLat)) p.highLatitudeRule = HighLatitudeRule.MIDDLE_OF_THE_NIGHT;
        else if ("SEVENTH_OF_NIGHT".equals(s.highLat)) p.highLatitudeRule = HighLatitudeRule.SEVENTH_OF_THE_NIGHT;
        else if ("TWILIGHT_ANGLE".equals(s.highLat)) p.highLatitudeRule = HighLatitudeRule.TWILIGHT_ANGLE;
        int[] f = "OMAN".equals(s.method) ? fit(s.placeId, date.getMonthValue()) : new int[COUNT];
        int[] a = new int[COUNT];
        for (int i = 0; i < COUNT; i++) a[i] = s.adjust[i] + f[i];
        p.adjustments = new PrayerAdjustments(a[0], a[1], a[2], a[3], a[4], a[5]);
        PrayerTimes t = new PrayerTimes(new Coordinates(s.lat, s.lon),
                new DateComponents(date.getYear(), date.getMonthValue(), date.getDayOfMonth()), p);
        ZoneId z = ZoneId.of(s.zone);
        Date[] d = {t.fajr, t.sunrise, t.dhuhr, t.asr, t.maghrib, t.isha};
        ZonedDateTime[] out = new ZonedDateTime[COUNT];
        for (int i = 0; i < COUNT; i++) out[i] = d[i].toInstant().atZone(z).truncatedTo(ChronoUnit.MINUTES);
        return out;
    }

    /** Index of the next time strictly after now (today or tomorrow's Fajr = index 0 with tomorrow=true). */
    static final class Next {
        final int prayer; final ZonedDateTime at;
        Next(int prayer, ZonedDateTime at) { this.prayer = prayer; this.at = at; }
    }

    static Next next(Settings s, ZonedDateTime now) {
        ZonedDateTime local = now.withZoneSameInstant(ZoneId.of(s.zone));
        ZonedDateTime[] today = day(s, local.toLocalDate());
        for (int i = 0; i < COUNT; i++) if (today[i].isAfter(local)) return new Next(i, today[i]);
        return new Next(FAJR, day(s, local.toLocalDate().plusDays(1))[FAJR]);
    }

    /** Hijri date (Umm al-Qura) with the day starting at Maghrib and the user's +/- days. */
    static String hijri(Settings s, ZonedDateTime now) {
        ZonedDateTime local = now.withZoneSameInstant(ZoneId.of(s.zone));
        boolean afterMaghrib = !local.isBefore(day(s, local.toLocalDate())[MAGHRIB]);
        HijrahDate h = HijrahDate.from(local.toLocalDate().plusDays((afterMaghrib ? 1 : 0) + s.hijriAdjust));
        return h.get(ChronoField.DAY_OF_MONTH) + " " + HIJRI_MONTHS_AR[h.get(ChronoField.MONTH_OF_YEAR) - 1] + " "
                + h.get(ChronoField.YEAR) + " هـ";
    }

    static String countdown(long seconds) {
        long s = Math.max(0, seconds);
        return String.format(java.util.Locale.ROOT, "%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60);
    }
}
