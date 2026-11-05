package com.gathin.tvhome;
import android.content.*;
import android.content.pm.*;
import android.os.Build;
import android.provider.Settings;
final class PictureSettings {
 static Intent target(Context c){
  PackageManager pm=c.getPackageManager();
  boolean xiaomi=(Build.MANUFACTURER+" "+Build.BRAND).toLowerCase(java.util.Locale.ROOT).matches(".*(xiaomi|redmi).*" );
  if(xiaomi){
   for(String action:new String[]{"mitv.intent.action.PICTURE","com.xiaomi.mitv.settings.ACTION_PICTURE_SETTINGS",Settings.ACTION_DISPLAY_SETTINGS}){
    for(String pkg:new String[]{"com.xiaomi.mitv.settings","com.mitv.tvsetting"}){
     Intent i=new Intent(action).setPackage(pkg);ResolveInfo r=pm.resolveActivity(i,PackageManager.MATCH_DEFAULT_ONLY);
     if(r!=null&&r.activityInfo!=null&&r.activityInfo.exported)return i.setComponent(new ComponentName(r.activityInfo.packageName,r.activityInfo.name));
    }
   }
  }
  Intent display=new Intent(Settings.ACTION_DISPLAY_SETTINGS);
  return display.resolveActivity(pm)!=null?display:new Intent(Settings.ACTION_SETTINGS);
 }
}
