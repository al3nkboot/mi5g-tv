package com.gathin.tvhome;

import android.app.Activity;
import android.os.Bundle;

import java.util.ArrayList;
import java.util.List;

/**
 * Mi5G 2.0.2: Edit inputs on the TV. Each input (HDMI ports, CEC devices, AV, Live TV) can be hidden
 * from the Inputs menu or shown again; names are still given from the phone page (Inputs tab).
 */
public final class InputsEditActivity extends Activity {
    /** Screenshot tests have no TV inputs: they put rows here first. */
    static List<ArrangeServer.InputRow> testRows;
    private android.widget.LinearLayout rows;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        rows = Mi5gPanel.build(this, getString(R.string.mi5g_inputs_edit), getString(R.string.mi5g_inputs_edit_note));
        build();
    }

    private List<ArrangeServer.InputRow> inputs() {
        List<ArrangeServer.InputRow> out = new ArrayList<>(testRows != null ? testRows : ArrangeServer.inputs(this));
        out.add(new ArrangeServer.InputRow(HomeLayout.LIVE_TV, getString(R.string.live_tv), false));
        return out;
    }

    private void build() {
        rows.removeAllViews();
        HomeLayout layout = HomeLayout.load(this);
        java.util.Map<String, String> names = new java.util.HashMap<>();
        for (HomeLayout.PinnedInput p : layout.inputs) if (!p.name.isEmpty()) names.put(p.id, p.name);
        for (ArrangeServer.InputRow in : inputs()) {
            if(!layout.inputName(in.id).isEmpty()) names.put(in.id,layout.inputName(in.id));
            boolean hidden = layout.hiddenInputs.contains(in.id);
            String title = names.containsKey(in.id) ? names.get(in.id) + " \u00B7 " + in.label : in.label;
            int icon = HomeLayout.LIVE_TV.equals(in.id) ? R.drawable.ic_livetv : in.hdmi ? R.drawable.ic_input : R.drawable.ic_av;
            Rows.add(this, rows, icon, title, getString(hidden ? R.string.mi5g_input_hidden : R.string.mi5g_input_shown), () -> {
                new android.app.AlertDialog.Builder(this).setTitle(title).setItems(new String[]{getString(R.string.mi5g_hide_input), getString(R.string.mi5g_rename_input)}, (dialog, which) -> {
                    if (which == 0) {
                        HomeLayout now = HomeLayout.load(this);
                        HomeLayout.save(HomeLayout.file(this), now.withInputHidden(in.id, !now.hiddenInputs.contains(in.id)));
                        HomeLayout.changed(); build();
                    } else {
                        android.widget.EditText input = new android.widget.EditText(this);
                        input.setSingleLine(true); input.setText(names.getOrDefault(in.id, ""));
                        input.setFilters(new android.text.InputFilter[]{new android.text.InputFilter.LengthFilter(40)});
                        new android.app.AlertDialog.Builder(this).setTitle(R.string.mi5g_rename_input).setView(input)
                            .setPositiveButton(android.R.string.ok, (d,w) -> {
                                HomeLayout now=HomeLayout.load(this);
                                java.util.Map<String,String> labels=new java.util.LinkedHashMap<>(now.names);
                                String name=HomeLayout.cleanName(input.getText().toString());
                                labels.put("input:"+in.id,name);
                                java.util.List<HomeLayout.PinnedInput> pins=new ArrayList<>();
                                for(HomeLayout.PinnedInput pin:now.inputs)pins.add(pin.id.equals(in.id)?new HomeLayout.PinnedInput(pin.id,name):pin);
                                HomeLayout.save(HomeLayout.file(this),new HomeLayout(labels,now.folders,pins,now.options,now.hiddenInputs));
                                HomeLayout.changed(); build();
                            }).setNegativeButton(android.R.string.cancel,null).show();
                    }
                }).show();
            });
        }
        if (rows.getChildCount() > 0) rows.getChildAt(0).requestFocus();
    }
}
