package com.gathin.tvhome;
import android.content.*;
import android.os.*;
import android.view.*;
import android.widget.TextClock;
import java.util.*;
/** Own minute heartbeat; does not depend on firmware resuming TIME_TICK after standby. */
final class MinuteClock implements View.OnAttachStateChangeListener {
 private final View root;
 private final Handler handler=new Handler(Looper.getMainLooper());
 private boolean active;
 private final BroadcastReceiver receiver=new BroadcastReceiver(){public void onReceive(Context c,Intent i){tick.run();}};
 private final Runnable tick=new Runnable(){public void run(){if(!active)return; redraw(root,new Date()); handler.removeCallbacks(this); handler.postDelayed(this,60_000-System.currentTimeMillis()%60_000);}};
 static void watch(View root){MinuteClock clock=new MinuteClock(root);root.addOnAttachStateChangeListener(clock);if(root.isAttachedToWindow())clock.onViewAttachedToWindow(root);}
 private MinuteClock(View v){root=v;}
 public void onViewAttachedToWindow(View v){if(active)return;active=true;IntentFilter f=new IntentFilter(Intent.ACTION_SCREEN_ON);f.addAction(Intent.ACTION_TIME_CHANGED);f.addAction(Intent.ACTION_TIMEZONE_CHANGED);root.getContext().registerReceiver(receiver,f);tick.run();}
 public void onViewDetachedFromWindow(View v){if(!active)return;active=false;handler.removeCallbacks(tick);root.getContext().unregisterReceiver(receiver);}
 static void redraw(View v, Date now){
  if(v instanceof TextClock){TextClock t=(TextClock)v;boolean h24=android.text.format.DateFormat.is24HourFormat(v.getContext());CharSequence format=h24?t.getFormat24Hour():t.getFormat12Hour();if(format==null)format=h24?"HH:mm":"h:mm a";Calendar c=Calendar.getInstance(t.getTimeZone()==null?TimeZone.getDefault():TimeZone.getTimeZone(t.getTimeZone()));c.setTime(now);t.setText(android.text.format.DateFormat.format(format,c));}
  if(v instanceof ViewGroup){ViewGroup g=(ViewGroup)v;for(int i=0;i<g.getChildCount();i++)redraw(g.getChildAt(i),now);}
 }
}
