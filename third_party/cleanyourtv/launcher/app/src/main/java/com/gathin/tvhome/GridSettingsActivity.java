package com.gathin.tvhome;
import android.app.Activity;
import android.os.Bundle;
import android.widget.LinearLayout;
import org.json.JSONObject;
public final class GridSettingsActivity extends Activity {
 protected void onCreate(Bundle b){super.onCreate(b);build();}
 private void build(){
  HomeLayout l=HomeLayout.load(this);HomeLayout.Options o=l.options;
  LinearLayout rows=Mi5gPanel.build(this,getString(R.string.mi5g_grid),null);
  Rows.add(this,rows,R.drawable.ic_apps,getString(R.string.mi5g_columns),String.valueOf(o.columns==0?5:o.columns),()->save("columns",o.columns>=6?4:o.columns<4?4:o.columns+1));
  Rows.add(this,rows,R.drawable.ic_apps,getString(R.string.mi5g_icon_size),getString("s".equals(o.iconSize)?R.string.mi5g_size_small:"l".equals(o.iconSize)?R.string.mi5g_size_large:R.string.mi5g_size_medium),()->save("iconSize","s".equals(o.iconSize)?"m":"m".equals(o.iconSize)?"l":"s"));
  rows.getChildAt(0).requestFocus();
 }
 private void save(String k,Object v){try{JSONObject json=HomeLayout.encode(HomeLayout.load(this));json.getJSONObject("options").put(k,v);if(HomeLayout.save(HomeLayout.file(this),HomeLayout.decode(json)))HomeLayout.changed();build();}catch(Exception ignored){}}
}
