package com.midroid.mi5gtv.prayer;

import java.time.DayOfWeek;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

/** The next alarms to arm, without Android types so it is unit-tested. */
final class Plan {
    static final int KIND_PRAYER = 0, KIND_PRE = 1, KIND_KAHF = 2;

    static final class Alarm {
        final int kind, prayer; final ZonedDateTime at;
        Alarm(int kind, int prayer, ZonedDateTime at) { this.kind = kind; this.prayer = prayer; this.at = at; }
        int code() { return kind * 10 + prayer; }
    }

    /** The first upcoming alarm of each kind/prayer within two days, earliest first. */
    static List<Alarm> upcoming(Settings s, ZonedDateTime now) {
        ZonedDateTime local = now.withZoneSameInstant(ZoneId.of(s.zone));
        List<Alarm> out = new ArrayList<>();
        boolean[] armed = new boolean[30];
        for (int off = 0; off <= 2; off++) {
            ZonedDateTime[] d = Engine.day(s, local.toLocalDate().plusDays(off));
            for (int p = 0; p < Engine.COUNT; p++) {
                if (s.modes[p] == Settings.MODE_OFF) continue;
                add(out, armed, new Alarm(KIND_PRAYER, p, d[p]), local);
                if (s.preMinutes > 0 && p != Engine.SUNRISE) add(out, armed, new Alarm(KIND_PRE, p, d[p].minusMinutes(s.preMinutes)), local);
            }
            ZonedDateTime day = local.toLocalDate().plusDays(off).atStartOfDay(local.getZone());
            if (s.kahf && day.getDayOfWeek() == DayOfWeek.FRIDAY) add(out, armed, new Alarm(KIND_KAHF, 0, day.plusHours(s.kahfHour)), local);
        }
        out.sort((a, b) -> a.at.compareTo(b.at));
        return out;
    }

    private static void add(List<Alarm> out, boolean[] armed, Alarm a, ZonedDateTime now) {
        if (!a.at.isAfter(now) || armed[a.code()]) return;
        armed[a.code()] = true;
        out.add(a);
    }
}
