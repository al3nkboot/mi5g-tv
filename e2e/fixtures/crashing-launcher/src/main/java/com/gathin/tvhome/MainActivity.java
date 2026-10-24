package com.gathin.tvhome;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

// Starts normally when launched by name (the takeover's `am start -W` precondition passes), and
// throws when it is started as HOME, which is the failure the guarded section must survive.
public class MainActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        crashIfHome(getIntent());
        TextView view = new TextView(this);
        view.setText("e2e crashing launcher");
        setContentView(view);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        crashIfHome(intent);
    }

    private static void crashIfHome(Intent intent) {
        if (intent != null && intent.hasCategory(Intent.CATEGORY_HOME)) {
            throw new IllegalStateException("e2e fixture: this launcher crashes as HOME");
        }
    }
}
