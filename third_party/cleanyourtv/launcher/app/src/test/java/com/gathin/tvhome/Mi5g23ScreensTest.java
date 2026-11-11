package com.gathin.tvhome;
import android.app.Activity;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.nio.file.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
import static org.junit.Assert.*;

// Mi5G 2.3: the Home lock and HDMI-CEC switch rows in Mi5G settings (Arabic, TV size).
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=34,qualifiers="ar-rSA-ldrtl-w960dp-h540dp-land-television-xhdpi")
public class Mi5g23ScreensTest {
 static void save(Activity a,String name)throws Exception{
  Mi5gScreensTest.save(a.getWindow().getDecorView(),name);
  File out=new File("/workspace/mi5g-tv-shots/v230");out.mkdirs();
  Files.copy(new File(Mi5gScreensTest.OUT,name+".png").toPath(),new File(out,name+".png").toPath(),StandardCopyOption.REPLACE_EXISTING);
 }
 static View find(View v,String text){
  if(v instanceof TextView&&text.equals(((TextView)v).getText().toString()))return v;
  if(v instanceof ViewGroup)for(int i=0;i<((ViewGroup)v).getChildCount();i++){View f=find(((ViewGroup)v).getChildAt(i),text);if(f!=null)return f;}
  return null;
 }
 @Test public void homeLockAndCecRows()throws Exception{
  LauncherSwitches.setHomeLocked(RuntimeEnvironment.getApplication(),true);
  Activity a=Robolectric.buildActivity(Mi5gSettingsActivity.class).setup().get();
  View lock=find(a.getWindow().getDecorView(),"قفل ترتيب الرئيسية");
  assertNotNull(lock);
  assertNotNull(find(a.getWindow().getDecorView(),"التحويل لجهاز يُشغَّل عبر HDMI-CEC"));
  lock.getParent().requestChildFocus((View)lock.getParent(),lock);
  View row=(View)lock.getParent();
  for(ViewParent p=row.getParent();p!=null;p=p.getParent())if(p instanceof ScrollView){((ScrollView)p).scrollTo(0,Math.max(0,row.getTop()-200));break;}
  Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
  save(a,"05_tv_settings_home_lock_cec_ar");
  a.finish();
 }
}
