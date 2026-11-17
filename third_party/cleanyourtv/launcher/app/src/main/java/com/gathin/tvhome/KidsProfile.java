package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.text.InputType;
import android.widget.EditText;
import android.widget.Toast;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.HashSet;
import java.util.Set;

/**
 * 2.4 M7 (backlog 3): a kids profile. When it is on, Home, the dock and All apps show only the apps a parent
 * allowed, holding OK on Home does nothing, and Mi5G settings, Control Center and arrange-from-phone ask for a
 * 4-digit PIN. The PIN is kept as a salted SHA-256; five wrong tries lock it for a minute. This is a simple lock
 * for little ones on Home, not device security: the TV's own Settings and remote keys stay as they are.
 */
final class KidsProfile {
    private KidsProfile() {}

    static final String ON = "mi5g_kids_on";
    static final String ALLOWED = "mi5g_kids_allowed";
    static final String SALT = "mi5g_kids_salt";
    static final String HASH = "mi5g_kids_pin";
    static final String FAILS = "mi5g_kids_fails";
    static final String LOCKED_UNTIL = "mi5g_kids_locked_until";
    static final int MAX_FAILS = 5;
    static final long LOCKOUT_MS = 60_000L;

    /** Unlocked PIN stays good for this long, so moving between settings pages asks once. */
    static final long GRACE_MS = 5 * 60_000L;
    private static long unlockedAt;

    static SharedPreferences prefs(Context ctx) { return LauncherSwitches.prefs(ctx); }

    static boolean on(Context ctx) { return prefs(ctx).getBoolean(ON, false) && hasPin(ctx); }

    static boolean hasPin(Context ctx) { return !prefs(ctx).getString(HASH, "").isEmpty(); }

    static void setOn(Context ctx, boolean on) {
        if (on && !hasPin(ctx)) return;
        prefs(ctx).edit().putBoolean(ON, on).apply();
    }

    static Set<String> allowed(Context ctx) { return new HashSet<>(prefs(ctx).getStringSet(ALLOWED, new HashSet<>())); }

    static void setAllowed(Context ctx, Set<String> pkgs) {
        prefs(ctx).edit().putStringSet(ALLOWED, new HashSet<>(pkgs)).apply();
    }

    /** True when the kids profile hides this app. */
    static boolean blocks(Context ctx, String pkg) { return on(ctx) && (pkg == null || !allowed(ctx).contains(pkg)); }

    static boolean validPin(String pin) { return pin != null && pin.matches("\\d{4}"); }

    static String hash(String salt, String pin) {
        try {
            MessageDigest d = MessageDigest.getInstance("SHA-256");
            byte[] h = d.digest((salt + ":" + pin).getBytes(StandardCharsets.UTF_8));
            StringBuilder b = new StringBuilder();
            for (byte x : h) b.append(String.format("%02x", x & 0xff));
            return b.toString();
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    static boolean setPin(Context ctx, String pin) {
        if (!validPin(pin)) return false;
        byte[] s = new byte[16];
        new SecureRandom().nextBytes(s);
        StringBuilder salt = new StringBuilder();
        for (byte x : s) salt.append(String.format("%02x", x & 0xff));
        prefs(ctx).edit().putString(SALT, salt.toString()).putString(HASH, hash(salt.toString(), pin))
                .putInt(FAILS, 0).putLong(LOCKED_UNTIL, 0).apply();
        return true;
    }

    /** Milliseconds left on a lockout, or 0. */
    static long lockedFor(Context ctx, long now) { return Math.max(0, prefs(ctx).getLong(LOCKED_UNTIL, 0) - now); }

    /** Checks a typed PIN; counts failures and locks after {@link #MAX_FAILS}. */
    static boolean check(Context ctx, String pin, long now) {
        SharedPreferences p = prefs(ctx);
        if (lockedFor(ctx, now) > 0 || !hasPin(ctx)) return false;
        boolean ok = validPin(pin) && MessageDigest.isEqual(hash(p.getString(SALT, ""), pin).getBytes(StandardCharsets.UTF_8),
                p.getString(HASH, "").getBytes(StandardCharsets.UTF_8));
        if (ok) {
            p.edit().putInt(FAILS, 0).apply();
            unlockedAt = now;
            return true;
        }
        int fails = p.getInt(FAILS, 0) + 1;
        SharedPreferences.Editor e = p.edit().putInt(FAILS, fails >= MAX_FAILS ? 0 : fails);
        if (fails >= MAX_FAILS) e.putLong(LOCKED_UNTIL, now + LOCKOUT_MS);
        e.apply();
        return false;
    }

    static boolean recentlyUnlocked(long now) { return unlockedAt > 0 && now - unlockedAt < GRACE_MS; }

    static void forgetUnlock() { unlockedAt = 0; }

    /**
     * Called first in a guarded screen's onCreate: with the kids profile on, asks for the PIN and closes the
     * screen on a wrong PIN or Back. Returns true when the screen may show straight away.
     */
    static boolean guard(Activity a) {
        if (!on(a) || recentlyUnlocked(System.currentTimeMillis())) return true;
        askPin(a, a.getString(R.string.mi5g_kids_pin_ask), a::finish, () -> { });
        return false;
    }

    static void askPin(Activity a, CharSequence title, Runnable onCancel, Runnable onOk) {
        long left = lockedFor(a, System.currentTimeMillis());
        if (left > 0) {
            Toast.makeText(a, a.getString(R.string.mi5g_kids_locked, (int) Math.ceil(left / 1000.0)), Toast.LENGTH_LONG).show();
            onCancel.run();
            return;
        }
        EditText in = pinField(a);
        new AlertDialog.Builder(a).setTitle(title).setView(in).setCancelable(false)
                .setNegativeButton(android.R.string.cancel, (d, w) -> onCancel.run())
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    if (check(a, in.getText().toString(), System.currentTimeMillis())) onOk.run();
                    else {
                        Toast.makeText(a, R.string.mi5g_kids_pin_wrong, Toast.LENGTH_LONG).show();
                        onCancel.run();
                    }
                }).show();
        in.requestFocus();
    }

    static EditText pinField(Context c) {
        EditText in = new EditText(c);
        in.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        in.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(4)});
        in.setHint("••••");
        in.setGravity(android.view.Gravity.CENTER);
        return in;
    }
}
