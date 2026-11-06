package com.gathin.tvhome;
import android.content.*;
import android.content.pm.*;
import android.media.tv.TvContract;
import java.util.*;
/** Always chooses a real handler, never Android's resolver/chooser. */
final class InputLauncher {
 static boolean open(Context c,String id){
  if(!HomeLayout.validId(id))return false;
  Config cfg=Config.load(c);
  Intent view=new Intent(Intent.ACTION_VIEW).setDataAndType(TvContract.buildChannelUriForPassthroughInput(id),"vnd.android.cursor.item/channel").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
  PackageManager pm=c.getPackageManager();
  List<ResolveInfo> handlers=pm.queryIntentActivities(view,PackageManager.MATCH_DEFAULT_ONLY);
  for(String pkg:new String[]{cfg.liveTvPackage,"com.android.tv","com.google.android.tv","com.mediatek.tv"}){
   for(ResolveInfo r:handlers)if(r.activityInfo!=null && pkg.equals(r.activityInfo.packageName) && launch(c,view,r))return true;
  }
  for(ResolveInfo r:handlers)if(r.activityInfo!=null && r.activityInfo.applicationInfo!=null && (r.activityInfo.applicationInfo.flags&ApplicationInfo.FLAG_SYSTEM)!=0 && launch(c,view,r))return true;
  if(handlers.size()==1 && launch(c,view,handlers.get(0)))return true;
  if(!cfg.inputAction.isEmpty()){
   Intent custom=new Intent(cfg.inputAction).setDataAndType(view.getData(),view.getType()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
   List<ResolveInfo> rs=pm.queryIntentActivities(custom,PackageManager.MATCH_DEFAULT_ONLY);
   if(rs.size()==1)return launch(c,custom,rs.get(0));
  }
  return false;
 }
 private static boolean launch(Context c,Intent i,ResolveInfo r){if(r.activityInfo==null||!r.activityInfo.exported)return false;try{c.startActivity(new Intent(i).setComponent(new ComponentName(r.activityInfo.packageName,r.activityInfo.name)));return true;}catch(ActivityNotFoundException|SecurityException e){return false;}}
}
