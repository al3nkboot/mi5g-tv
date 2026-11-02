package com.midroid.mi5gtv.prayer;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.TextView;

/** Full-screen adhan over any app; Back or OK stops it. Closes itself when the adhan ends. */
public class AdhanActivity extends Activity {
    static final String FINISH = "com.midroid.mi5gtv.prayer.ADHAN_DONE";
    private final BroadcastReceiver done = new BroadcastReceiver() {
        @Override public void onReceive(Context c, Intent i) { finish(); }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED);
        int prayer = getIntent().getIntExtra("prayer", 0);
        Typeface cairo = getResources().getFont(R.font.cairo);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setGravity(Gravity.CENTER);
        col.setBackgroundResource(R.drawable.bg_teal);
        TextView title = Ui.text(this, getString(R.string.time_of, Engine.NAMES_AR[prayer]), 54, 0xFFD4AF37, cairo, true);
        Settings s = Settings.load(this);
        TextView city = Ui.text(this, s.cityAr, 26, 0xFFE6F4F1, cairo, false);
        TextView hint = Ui.text(this, getString(R.string.stop_hint), 18, 0x99FFFFFF, cairo, false);
        hint.setPadding(0, 48, 0, 0);
        col.addView(title);
        col.addView(city);
        col.addView(hint);
        setContentView(col);
        registerReceiver(done, new IntentFilter(FINISH), RECEIVER_NOT_EXPORTED);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK || keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
            startService(new Intent(this, AdhanService.class).setAction(AdhanService.STOP));
            finish();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override protected void onDestroy() { unregisterReceiver(done); super.onDestroy(); }
}
