package com.gathin.tvhome;

import android.app.Activity;
import android.os.Bundle;
import android.view.KeyEvent;

/** Mi5G 2.0.1: a preview of the city screensaver for TVs without a screensaver picker. Any key closes it. */
public class CityDreamActivity extends Activity {
    CityScreen screen;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        screen = new CityScreen(this);
        setContentView(screen.root);
    }

    @Override protected void onStart() { super.onStart(); screen.start(); }
    @Override protected void onStop() { screen.stop(); super.onStop(); }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        finish();
        return true;
    }
}
