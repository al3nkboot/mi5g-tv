package com.gathin.tvhome;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;

import java.util.List;

/** 2.4 M4 (backlog 26): Home widgets. OK shows or hides one; holding OK moves it up a place. */
public final class HomeWidgetsActivity extends Activity {
    private LinearLayout rows;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        rows = Mi5gPanel.build(this, getString(R.string.mi5g_widgets), getString(R.string.mi5g_widgets_note));
        build(null);
    }

    private void build(String focus) {
        rows.removeAllViews();
        View focusRow = null;
        for (String id : HomeWidgets.order(this)) {
            boolean on = HomeWidgets.shown(this, id);
            View row = Rows.add(this, rows, R.drawable.ic_swap_vert, getString(HomeWidgets.titleRes(id)),
                    getString(on ? R.string.mi5g_on : R.string.mi5g_off), () -> {
                        HomeWidgets.setShown(this, id, !HomeWidgets.shown(this, id));
                        build(id);
                    });
            row.setOnLongClickListener(v -> {
                List<String> o = HomeWidgets.moveUp(HomeWidgets.order(this), id);
                HomeWidgets.saveOrder(this, o);
                build(id);
                return true;
            });
            if (id.equals(focus)) focusRow = row;
        }
        String league = HomeWidgets.league(this);
        View leagueRow = Rows.add(this, rows, R.drawable.ic_play, getString(R.string.mi5g_widget_league), getString(HomeWidgets.leagueRes(league)), () -> {
            List<String> l = HomeWidgets.LEAGUES;
            HomeWidgets.setLeague(this, l.get((l.indexOf(HomeWidgets.league(this)) + 1) % l.size()));
            HomeLayout.changed();
            build("league");
        });
        if ("league".equals(focus)) focusRow = leagueRow;
        String country = Occasions.country(this);
        View countryRow = Rows.add(this, rows, R.drawable.ic_clock, getString(R.string.mi5g_national_day), getString(Occasions.countryRes(country)), () -> {
            Occasions.setCountry(this, Occasions.nextCountry(Occasions.country(this)));
            HomeLayout.changed();
            build("country");
        });
        if ("country".equals(focus)) focusRow = countryRow;
        (focusRow != null ? focusRow : rows.getChildAt(0)).requestFocus();
        setResult(RESULT_OK);
    }
}
