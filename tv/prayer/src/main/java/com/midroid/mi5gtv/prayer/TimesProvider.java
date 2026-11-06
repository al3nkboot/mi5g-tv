package com.midroid.mi5gtv.prayer;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Read-only: today's times for the Mi5G launcher's city screensaver.
 * content://com.midroid.mi5gtv.prayer.times/today: one row per prayer (name, time, next) plus city, lat, lon.
 */
public class TimesProvider extends ContentProvider {
    static final String[] COLUMNS = {"name", "time", "is_next", "city", "lat", "lon"};

    @Override public boolean onCreate() { return true; }

    static MatrixCursor rows(Settings s, ZonedDateTime now) {
        MatrixCursor c = new MatrixCursor(COLUMNS);
        ZonedDateTime[] d = Engine.day(s, now.withZoneSameInstant(java.time.ZoneId.of(s.zone)).toLocalDate());
        Engine.Next next = Engine.next(s, now);
        DateTimeFormatter f = DateTimeFormatter.ofPattern("HH:mm");
        for (int i = 0; i < Engine.COUNT; i++) {
            c.addRow(new Object[]{Engine.NAMES_AR[i], d[i].format(f), i == next.prayer && next.at.equals(d[i]) ? 1 : 0, s.cityAr, s.lat, s.lon});
        }
        return c;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        return rows(Settings.load(getContext()), ZonedDateTime.now());
    }

    @Override public String getType(Uri uri) { return "vnd.android.cursor.dir/vnd.mi5g.prayer"; }
    @Override public Uri insert(Uri uri, ContentValues v) { return null; }
    @Override public int delete(Uri uri, String s, String[] a) { return 0; }
    @Override public int update(Uri uri, ContentValues v, String s, String[] a) { return 0; }
}
