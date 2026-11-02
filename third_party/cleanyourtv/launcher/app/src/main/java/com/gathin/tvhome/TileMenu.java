package com.gathin.tvhome;

import android.app.Activity;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.util.HashSet;
import java.util.Set;

/**
 * Mi5G 2.0: the menu hold OK opens on an app tile. Remove from Home only hides the tile (the app
 * stays installed; nothing here can uninstall). A custom image is a file the phone app pushes to
 * files/tiles/&lt;package&gt;.png.
 */
final class TileMenu {
    static final String REMOVED_KEY = "mi5g_removed_apps";

    private TileMenu() {}

    static File imageFile(Context ctx, String pkg) {
        File dir = Config.dir(ctx);
        return dir == null ? null : new File(new File(dir, "tiles"), pkg + ".png");
    }

    static Drawable customImage(Context ctx, String pkg) {
        File f = imageFile(ctx, pkg);
        if (f == null || !f.isFile() || f.length() == 0 || f.length() > 4_000_000) return null;
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inSampleSize = 1;
        Bitmap b = BitmapFactory.decodeFile(f.getPath(), o);
        return b == null ? null : new BitmapDrawable(ctx.getResources(), b);
    }

    static SharedPreferences prefs(Context ctx) {
        return ctx.getSharedPreferences(Config.SETTINGS_NAME, Context.MODE_PRIVATE);
    }

    static void remove(Context ctx, String pkg) {
        Set<String> s = new HashSet<>(prefs(ctx).getStringSet(REMOVED_KEY, new HashSet<>()));
        s.add(pkg);
        prefs(ctx).edit().putStringSet(REMOVED_KEY, s).apply();
    }

    static Set<String> removed(Context ctx) { return prefs(ctx).getStringSet(REMOVED_KEY, new HashSet<>()); }

    static boolean anyRemoved(Context ctx) { return !prefs(ctx).getStringSet(REMOVED_KEY, new HashSet<>()).isEmpty(); }

    static void restoreAll(Context ctx) { prefs(ctx).edit().remove(REMOVED_KEY).apply(); }

    /** A glass panel in the middle of the screen with a title and rows. */
    static Dialog panel(Activity a, CharSequence title, LinearLayout[] rowsOut) {
        Dialog d = new Dialog(a);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        View panel = a.getLayoutInflater().inflate(R.layout.popup_inputs, null, false);
        LinearLayout rows = panel.findViewById(R.id.rows);
        TextView t = new TextView(a);
        t.setText(title);
        t.setTextColor(0xFFD4AF37);
        t.setTextSize(16f);
        t.setTypeface(a.getResources().getFont(R.font.cairo), android.graphics.Typeface.BOLD);
        int p = (int) (12 * a.getResources().getDisplayMetrics().density);
        t.setPadding(p, p / 2, p, p / 2);
        t.setGravity(Rows.start());
        rows.addView(t);
        d.setContentView(panel);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            w.setLayout((int) (300 * a.getResources().getDisplayMetrics().density), -2);
        }
        rowsOut[0] = rows;
        return d;
    }

    static void show(Activity a, String pkg, String name, Runnable move, Runnable reload) {
        LinearLayout[] r = new LinearLayout[1];
        Dialog d = panel(a, name, r);
        LinearLayout rows = r[0];
        Rows.add(a, rows, R.drawable.ic_swap_vert, a.getString(R.string.mi5g_tile_move), null, () -> { d.dismiss(); move.run(); });
        Rows.add(a, rows, R.drawable.ic_remove, a.getString(R.string.mi5g_tile_remove), null, () -> {
            d.dismiss();
            remove(a, pkg);
            Toast.makeText(a, R.string.mi5g_tile_removed, Toast.LENGTH_LONG).show();
            reload.run();
        });
        Rows.add(a, rows, R.drawable.ic_info, a.getString(R.string.mi5g_tile_info), null, () -> {
            d.dismiss();
            try {
                a.startActivity(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + pkg))
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            } catch (ActivityNotFoundException e) {
                Toast.makeText(a, R.string.not_available, Toast.LENGTH_SHORT).show();
            }
        });
        File img = imageFile(a, pkg);
        boolean has = img != null && img.isFile();
        Rows.add(a, rows, R.drawable.ic_image, a.getString(has ? R.string.mi5g_tile_image_reset : R.string.mi5g_tile_image),
                null, () -> {
                    d.dismiss();
                    if (has) { //noinspection ResultOfMethodCallIgnored
                        img.delete();
                        reload.run();
                    } else {
                        Toast.makeText(a, R.string.mi5g_tile_image_hint, Toast.LENGTH_LONG).show();
                    }
                });
        d.show();
        rows.getChildAt(1).requestFocus();
    }
}
