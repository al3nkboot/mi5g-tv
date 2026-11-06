package com.gathin.tvhome;
import android.app.Activity;
import android.view.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.*;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk=34,qualifiers="ar-rSA-ldrtl-w960dp-h540dp-land-television-xhdpi")
public class Mi5g21ScreensTest {
 static void save(Activity a,String name)throws Exception{
  Mi5gScreensTest.save(a.getWindow().getDecorView(),name);
  File out=new File("/workspace/out/mi5g-tv-2.1/screens");out.mkdirs();
  Files.copy(new File(Mi5gScreensTest.OUT,name+".png").toPath(),new File(out,name+".png").toPath(),StandardCopyOption.REPLACE_EXISTING);
 }
 @Test public void deviceOn()throws Exception{MainActivity a=Robolectric.buildActivity(MainActivity.class).setup().get();View card=a.showDeviceOnForTest("جهاز الألعاب","HDMI 2");Shadows.shadowOf(android.os.Looper.getMainLooper()).idleFor(java.time.Duration.ofMillis(300));card.animate().cancel();card.setAlpha(1f);save(a,"device_on_card_ar");a.finish();}
 @Test public void inputs()throws Exception{InputsEditActivity.testRows=Arrays.asList(new ArrangeServer.InputRow("com.tv/.Input/HDMI1","HDMI 1 · ARC",true),new ArrangeServer.InputRow("com.tv/.Input/HDMI2","HDMI 2 · جهاز الألعاب",true));InputsEditActivity a=Robolectric.buildActivity(InputsEditActivity.class).setup().get();save(a,"edit_inputs_ar");InputsEditActivity.testRows=null;a.finish();}
 @Test public void hiddenApps()throws Exception{HiddenAppsActivity.testApps=Arrays.asList(new String[]{"com.tcl.manual","دليل المستخدم"},new String[]{"com.tcl.factory","أدوات المصنع"},new String[]{"com.tcl.storedemo","عرض المتجر"});Activity a=Robolectric.buildActivity(HiddenAppsActivity.class).setup().get();save(a,"hidden_apps_ar");HiddenAppsActivity.testApps=null;a.finish();}
 @Test public void weather()throws Exception{android.content.Context c=RuntimeEnvironment.getApplication();Weather.setUser(c,new Weather.Place("مسقط",23.58,58.4,1));Weather.saveIp(c,new Weather.Place("مسقط",23.58,58.4,3),System.currentTimeMillis());Activity a=Robolectric.buildActivity(WeatherPlaceActivity.class).setup().get();save(a,"weather_location_ar");a.finish();}
 @Test public void screensaverMessageQuiet()throws Exception{CityDreamActivity a=Robolectric.buildActivity(CityDreamActivity.class).create().get();CityScreen.Data d=new CityScreen.Data();d.city="مسقط";d.line1="أهلًا بكم";d.line2="@midroid5G";d.weather="٢٨°";d.quiet=true;a.screen.bind(d);save(a,"screensaver_message_quiet_ar");a.finish();}
 @Test public void continueWatching()throws Exception{MainActivity a=Robolectric.buildActivity(MainActivity.class).setup().get();ContinueWatching.bind(a,a.findViewById(R.id.continueRow),Arrays.asList(new ContinueWatching.Item("رحلة إلى الطبيعة","com.example.tv","https://example.com",240,1800),new ContinueWatching.Item("استكشاف المدن","com.example.tv","https://example.com",0,0)));save(a,"continue_watching_ar");a.finish();}
 @Test public void grid()throws Exception{Activity a=Robolectric.buildActivity(GridSettingsActivity.class).setup().get();save(a,"grid_settings_ar");a.finish();}
 @Test public void qrFileControls()throws Exception{Activity a=Robolectric.buildActivity(ArrangeActivity.class).setup().get();save(a,"qr_file_transfer_tv_ar");a.finish();ArrangeServer.stopRunning();}
}
