package com.gathin.tvhome;
import android.accessibilityservice.AccessibilityService;
import android.content.*;
import android.os.SystemClock;
import android.view.accessibility.AccessibilityEvent;
import java.util.*;
/** Opt-in package-only Home fallback. It never reads window text or changes installed packages. */
public final class FastHomeService extends AccessibilityService {
 static final String KEY="mi5g_fast_home";
 static final Set<String> STOCK=new HashSet<>(Arrays.asList("com.google.android.tvlauncher","com.google.android.apps.tv.launcherx","com.google.android.leanbacklauncher","com.mitv.tvhome","com.tcl.launcher","com.tcl.tvhome","com.android.tvlauncher"));
 private long last=-5000;
 static boolean enabled(Context c){return c.getSharedPreferences(Config.SETTINGS_NAME,Context.MODE_PRIVATE).getBoolean(KEY,false);}
 public void onAccessibilityEvent(AccessibilityEvent e){
  if(!enabled(this)||e.getEventType()!=AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED||e.getPackageName()==null||!STOCK.contains(e.getPackageName().toString()))return;
  long now=SystemClock.elapsedRealtime();if(now-last<1500)return;last=now;
  try{startActivity(new Intent(this,MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK|Intent.FLAG_ACTIVITY_CLEAR_TOP));}catch(RuntimeException ignored){}
 }
 public void onInterrupt(){}
}
