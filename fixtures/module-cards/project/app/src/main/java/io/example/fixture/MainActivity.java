package io.example.fixture;

import android.app.Activity;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.TextView;

/** A fixture module: one screen that names the fixture and its package. */
public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        TextView t = new TextView(this);
        t.setText(getString(R.string.app_name) + "\n" + getPackageName());
        t.setTextSize(28f);
        t.setGravity(Gravity.CENTER);
        setContentView(t);
    }
}
