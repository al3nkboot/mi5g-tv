package com.gathin.tvhome;
import static org.junit.Assert.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import java.net.InetAddress;
import java.util.*;
@RunWith(RobolectricTestRunner.class) @Config(sdk=34)
public class Mi5g21Test {
 @Test public void quietHoursWrapAndBoundaries() {
  assertTrue(QuietHours.isQuiet(QuietHours.DEFAULT, 23*60));
  assertTrue(QuietHours.isQuiet(QuietHours.DEFAULT, 6*60+59));
  assertFalse(QuietHours.isQuiet(QuietHours.DEFAULT, 7*60));
  assertFalse(QuietHours.isQuiet(QuietHours.OFF, 0));
  assertFalse(QuietHours.isQuiet("bad", 0));
  assertTrue(QuietHours.isQuiet("10:00-12:00", 660));
  assertFalse(QuietHours.isQuiet("10:00-12:00", 720));
 }
 @Test public void weatherProviderOrderAndContact() {
  List<String> calls = new ArrayList<>();
  String s = Weather.fetch((url, ua) -> { calls.add(url); assertTrue(ua.contains("https://github.com/al3nkboot/mi5g-tv")); if(url.contains("open-meteo")) throw new Exception(); return "{\"properties\":{\"timeseries\":[{\"data\":{\"instant\":{\"details\":{\"air_temperature\":28}}}}]}}"; }, 23,58);
  assertFalse(s.isEmpty()); assertEquals(2,calls.size()); assertTrue(calls.get(1).contains("api.met.no"));
  calls.clear(); Weather.ipLookup((url,ua)->{calls.add(url); return "{}";});
  assertEquals(Arrays.asList(Weather.IP_URLS), calls);
  Weather.Place user=new Weather.Place("user",23,58,1), prayer=new Weather.Place("prayer",24,59,2);
  assertSame(user, Weather.choose(user,prayer,null)); assertSame(prayer,Weather.choose(null,prayer,null));
  assertFalse(Weather.ipDue(100000,99999)); assertTrue(Weather.ipDue(Weather.IP_EVERY_MS+1,1));
 }
 @Test public void makerExtrasNeverHideArtOrCore() {
  assertTrue(MakerExtras.isExtra("com.tcl.factory", "Factory"));
  assertFalse(MakerExtras.isExtra("org.example.factory","Factory demo"));
  for(String p: new String[]{"com.tcl.exhibit","com.android.settings","com.android.vending","com.midroid.mi5gtv.prayer"}) assertFalse(MakerExtras.isExtra(p,"Factory demo"));
  assertFalse(MakerExtras.hidden("com.tcl.factory","Factory",true,Collections.singleton("com.tcl.factory")));
 }
 @Test public void nonstandardSubnetIsLocalButOtherNetworkIsNot() throws Exception {
  assertTrue(ArrangeServer.sameSubnet(ip("100.65.4.1"),ip("100.65.4.55"),24));
  assertFalse(ArrangeServer.sameSubnet(ip("100.65.4.1"),ip("100.65.5.55"),24));
  assertTrue(ArrangeServer.sameSubnet(ip("203.0.113.10"),ip("203.0.113.11"),31));
  assertFalse(ArrangeServer.sameSubnet(ip("203.0.113.10"),ip("203.0.113.12"),31));
  assertFalse(ArrangeServer.sameSubnet(ip("10.0.0.1"),ip("10.0.0.2"),0));
 }
 @Test public void memoryFallback(){assertEquals(44,MemoryStats.availableMb("MemTotal: 100000 kB\nMemFree: 10000 kB\nBuffers: 5000 kB\nCached: 30000 kB\nSReclaimable: 2000 kB\nShmem: 1000 kB"));assertEquals(4,MemoryStats.availableMb("MemAvailable: 4096 kB\nMemFree: 1 kB"));assertEquals(-1,MemoryStats.availableMb("bad"));}
 @Test public void ignoreAndInputMappingsValidated(){assertEquals("ignore",ButtonMap.validAction("ignore")); assertEquals("input:com.vendor.input/.HDMI/HW1",ButtonMap.validAction("input:com.vendor.input/.HDMI/HW1")); assertNull(ButtonMap.validAction("app:com.1bad"));assertNull(ButtonMap.validAction("app:com.a;reboot"));}
 @Test public void watchNextDeduplicatesNormalizedTitles(){
  ContinueWatching.Item one=new ContinueWatching.Item("  Example  Show ","com.example.tv","https://example.com/1",100,1000);
  ContinueWatching.Item two=new ContinueWatching.Item("example show","com.other.tv","https://example.com/2",0,0);
  assertEquals(1,ContinueWatching.dedupe(Arrays.asList(one,two)).size());assertEquals(10,one.progress());assertEquals(-1,two.progress());
 }
 @Test public void transferRejectsOversizeAndTraversal() throws Exception {
  assertFalse(FileTransfer.cleanName("../../bad\\name.apk").contains("/"));assertFalse(FileTransfer.cleanName("../../bad\\name.apk").contains("\\"));
  try{FileTransfer.receive(RuntimeEnvironment.getApplication(),"bad",new java.io.ByteArrayInputStream(new byte[0]),FileTransfer.MAX_BYTES+1);fail();}catch(java.io.IOException expected){}
  try{FileTransfer.receive(RuntimeEnvironment.getApplication(),"bad",new java.io.ByteArrayInputStream(new byte[0]),0);fail();}catch(java.io.IOException expected){}
 }
 @Test public void clockRedrawAndRtlTopBar() {
  android.widget.TextClock clock=new android.widget.TextClock(RuntimeEnvironment.getApplication());clock.setFormat12Hour("HH:mm");clock.setFormat24Hour("HH:mm");clock.setTimeZone("UTC");MinuteClock.redraw(clock,new Date(0));assertEquals("00:00",clock.getText().toString());
 }
 @Test @org.robolectric.annotation.Config(qualifiers="ar-rSA-ldrtl-w960dp-h540dp-land") public void arabicRightFromSettingsReachesVisibleNeighbor() {
  MainActivity a=org.robolectric.Robolectric.buildActivity(MainActivity.class).setup().visible().get();
  org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
  android.view.View settings=a.findViewById(R.id.btnSettings);
  try{Mi5gScreensTest.save(a.getWindow().getDecorView(),"rtl-focus-test");}catch(Exception e){throw new AssertionError(e);}
  // Robolectric starts in touch mode; the physical TV remote is non-touch.
  for(int id:new int[]{R.id.btnSettings,R.id.btnWifi,R.id.btnInputs,R.id.btnReorder})a.findViewById(id).setFocusableInTouchMode(true);
  assertTrue(settings.requestFocus()); assertTrue(settings.isFocused());
  settings.dispatchKeyEvent(new android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN,android.view.KeyEvent.KEYCODE_DPAD_RIGHT));
  assertFalse(settings.isFocused());assertTrue(a.findViewById(R.id.btnWifi).isFocused());a.finish();
 }
 @Test public void homeUsesSavedGridColumns() throws Exception {
  android.content.Context c=RuntimeEnvironment.getApplication();
  HomeLayout layout=HomeLayout.decode(new org.json.JSONObject("{\"options\":{\"columns\":6}}")); HomeLayout.save(HomeLayout.file(c),layout);
  MainActivity a=org.robolectric.Robolectric.buildActivity(MainActivity.class).setup().get();
  androidx.recyclerview.widget.RecyclerView grid=a.findViewById(R.id.grid);
  assertEquals(6,((androidx.recyclerview.widget.GridLayoutManager)grid.getLayoutManager()).getSpanCount());a.finish();
 }
 @Test public void continueWatchingRequiresSuccessfulPermissionConsent() {
  Mi5gSettingsActivity a=org.robolectric.Robolectric.buildActivity(Mi5gSettingsActivity.class).setup().get();
  a.onRequestPermissionsResult(61,new String[]{"android.permission.READ_TV_LISTINGS"},new int[]{android.content.pm.PackageManager.PERMISSION_DENIED});
  assertFalse(ContinueWatching.enabled(a));
  a.onRequestPermissionsResult(61,new String[]{"android.permission.READ_TV_LISTINGS"},new int[]{android.content.pm.PackageManager.PERMISSION_GRANTED});
  assertTrue(ContinueWatching.enabled(a));a.finish();
 }
 private byte[] ip(String s)throws Exception{return InetAddress.getByName(s).getAddress();}
 @Test public void deviceCardOnlyOffersNewConnections() {
  DeviceOnCard.Tracker t=new DeviceOnCard.Tracker(); Set<String> hidden=Collections.emptySet();
  assertFalse(t.onState("hdmi",0,100,hidden)); t.seed("hdmi",2); assertTrue(t.onState("hdmi",0,200,hidden));
  t.seed("hdmi",2);assertFalse(t.onState("hdmi",0,201,hidden));
  assertFalse(t.onDeviceAdded("child","port",200,Collections.singleton("port")));
 }
 @Test public void slowEffectsRequireEvidence(){assertFalse(EffectsMode.slow(Collections.nCopies(11,50_000_000L))); assertTrue(EffectsMode.slow(Collections.nCopies(20,50_000_000L))); assertFalse(EffectsMode.slow(Collections.nCopies(20,16_000_000L)));}
}
