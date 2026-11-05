package com.gathin.tvhome;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.Toast;

/**
 * Mi5G 2.0.2: where the screensaver weather is for. A city the user sets wins; then the prayer
 * module's city; then an approximate place from the IP address («قرب مسقط»), which can be removed.
 */
public final class WeatherPlaceActivity extends Activity {
    private android.widget.LinearLayout rows;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        rows = Mi5gPanel.build(this, getString(R.string.mi5g_weather_place), getString(R.string.mi5g_weather_place_note));
        build();
    }

    private void build() {
        rows.removeAllViews();
        Weather.Place user = Weather.userPlace(this), ip = Weather.ipPlace(this);
        Rows.add(this, rows, R.drawable.ic_home_settings, getString(R.string.mi5g_weather_city),
                user == null ? getString(R.string.mi5g_weather_city_none) : user.name, this::askCity);
        if (user != null) {
            Rows.add(this, rows, 0, getString(R.string.mi5g_weather_city_clear), null, () -> { Weather.setUser(this, null); build(); });
        }
        boolean auto = Weather.ipAllowed(this);
        String near = ip == null ? getString(R.string.mi5g_weather_near_unknown) : ip.shown();
        Rows.add(this, rows, R.drawable.ic_wifi, getString(R.string.mi5g_weather_auto),
                auto ? near : getString(R.string.mi5g_off), () -> { Weather.setIpAllowed(this, !Weather.ipAllowed(this)); build(); });
        rows.getChildAt(0).requestFocus();
    }

    private void askCity() {
        EditText e = new EditText(this);
        e.setSingleLine(true);
        e.setHint(R.string.mi5g_weather_city_hint);
        new AlertDialog.Builder(this).setTitle(R.string.mi5g_weather_city).setView(e)
                .setPositiveButton(android.R.string.ok, (d, w) -> {
                    String typed = e.getText().toString();
                    new Thread(() -> {
                        Weather.Place p = Weather.geocode(Weather.HTTP, typed);
                        runOnUiThread(() -> {
                            if (p == null) Toast.makeText(this, R.string.mi5g_weather_city_not_found, Toast.LENGTH_SHORT).show();
                            else { Weather.setUser(this, p); build(); }
                        });
                    }, "Mi5gGeocode").start();
                })
                .setNegativeButton(android.R.string.cancel, null).show();
    }
}
