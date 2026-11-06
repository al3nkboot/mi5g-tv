package com.midroid.mi5gtv.prayer;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import java.nio.charset.StandardCharsets;

/**
 * Arabic banners from the phone's TV tools. adb shell lines are printable ASCII only, so the phone sends the
 * UTF-8 text as hex: am broadcast -a com.midroid.mi5gtv.prayer.BANNER -p com.midroid.mi5gtv.prayer --es hex 'd8aa…'.
 * Guarded by android.permission.DUMP so only the shell (adb) can send it, not other TV apps.
 */
public class BannerReceiver extends BroadcastReceiver {
    static final int MAX_CHARS = 160;

    static String decode(String hex) {
        if (hex == null || hex.isEmpty() || hex.length() % 2 != 0 || hex.length() > MAX_CHARS * 8) return null;
        byte[] b = new byte[hex.length() / 2];
        for (int i = 0; i < b.length; i++) {
            int hi = Character.digit(hex.charAt(2 * i), 16), lo = Character.digit(hex.charAt(2 * i + 1), 16);
            if (hi < 0 || lo < 0) return null;
            b[i] = (byte) (hi * 16 + lo);
        }
        String s = new String(b, StandardCharsets.UTF_8).replaceAll("[\\p{Cntrl}\\uFFFD]", " ").trim();
        if (s.isEmpty()) return null;
        return s.length() > MAX_CHARS ? s.substring(0, MAX_CHARS) : s;
    }

    @Override
    public void onReceive(Context ctx, Intent intent) {
        String text = decode(intent.getStringExtra("hex"));
        if (text != null) Banner.show(ctx, text, 10);
    }
}
