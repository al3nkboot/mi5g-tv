package com.midroid.mi5gtv.matches;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import java.util.ArrayList;
import java.util.List;

/** Mi5G 2.4 (M6): today's prayer times from the Mi5G Prayer app on the same TV, when it is installed. */
final class Prayers {
    private Prayers() {}

    static final Uri TODAY = Uri.parse("content://com.midroid.mi5gtv.prayer.times/today");

    /** {name, "HH:mm"} rows; empty when Mi5G Prayer is missing or says nothing. */
    static List<String[]> today(Context c) {
        List<String[]> out = new ArrayList<>();
        try (Cursor cur = c.getContentResolver().query(TODAY, null, null, null, null)) {
            if (cur == null) return out;
            int n = cur.getColumnIndex("name"), t = cur.getColumnIndex("time");
            if (n < 0 || t < 0) return out;
            while (cur.moveToNext()) out.add(new String[]{cur.getString(n), cur.getString(t)});
        } catch (Exception ignored) {
        }
        return out;
    }
}
