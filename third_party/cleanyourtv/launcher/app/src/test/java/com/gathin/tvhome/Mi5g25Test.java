package com.gathin.tvhome;
import static org.junit.Assert.*;
import android.content.Context;
import android.content.Intent;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** 2.4 M2: safe mode, Arabic names, hide without disabling, dock rows, All apps, update restore. */
@RunWith(RobolectricTestRunner.class) @Config(sdk=34)
public class Mi5g25Test {
  private Context ctx() { return RuntimeEnvironment.getApplication(); }

  @After public void tidy() {
    AllAppsActivity.testApps = null;
    TileMenu.restoreAll(ctx());
    SafeMode.setActive(ctx(), false);
  }

  @Test public void crashTimesKeepOnlyTheLastFiveMinutes() {
    long now = 10 * 60_000L;
    String raw = SafeMode.add("", now - SafeMode.WINDOW_MS - 1);
    raw = SafeMode.add(raw, now - 1000);
    assertEquals(Collections.singletonList(now - 1000), SafeMode.recent(raw, now));
    assertEquals(2, SafeMode.recent(SafeMode.add(raw, now), now).size());
    assertTrue(SafeMode.recent("junk,,12x", now).isEmpty());
  }

  @Test public void twoCrashesTurnOnSafeModeAndThreeHandHomeOver() {
    long now = 1_000_000L;
    SafeMode.record(ctx(), now);
    assertFalse(SafeMode.active(ctx()));
    SafeMode.record(ctx(), now + 1000);
    assertTrue(SafeMode.active(ctx()));
    assertFalse(SafeMode.handOver(ctx(), now + 2000));
    SafeMode.record(ctx(), now + 2000);
    assertTrue(SafeMode.handOver(ctx(), now + 3000));
    assertFalse("after five quiet minutes Mi5G tries again", SafeMode.handOver(ctx(), now + SafeMode.WINDOW_MS + 1000));
    SafeMode.setActive(ctx(), false);
    assertEquals(0, SafeMode.crashes(ctx(), now + 3000));
  }

  @Test public void onlyAHomePressIsHandedOver() {
    assertTrue(SafeMode.isHomeLaunch(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)));
    assertFalse(SafeMode.isHomeLaunch(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)));
    assertFalse(SafeMode.isHomeLaunch(null));
  }

  @Test public void arabicNamesOnlyInArabic() {
    assertEquals("نتفليكس", ArabicNames.of("com.netflix.ninja", "ar"));
    assertEquals("متجر بلاي", ArabicNames.of("com.android.vending", "ar"));
    assertNull(ArabicNames.of("com.netflix.ninja", "en"));
    assertNull(ArabicNames.of("com.unknown.app", "ar"));
    for (String name : ArabicNames.NAMES.values()) assertFalse(name, name.matches(".*[A-Za-z].*"));
  }

  @Test public void settingsAndPlayStoreCanNeverBeHidden() {
    assertFalse(TileMenu.remove(ctx(), "com.android.vending"));
    assertFalse(TileMenu.remove(ctx(), "com.android.tv.settings"));
    assertFalse(TileMenu.removed(ctx()).contains("com.android.vending"));
    assertTrue(TileMenu.remove(ctx(), "com.netflix.ninja"));
    assertTrue(TileMenu.removed(ctx()).contains("com.netflix.ninja"));
    TileMenu.restore(ctx(), "com.netflix.ninja");
    assertFalse(TileMenu.removed(ctx()).contains("com.netflix.ninja"));
  }

  @Test public void dockRowsGoOneToThreeAndRoundAgain() {
    assertEquals(1, LauncherSwitches.clampDockRows(0));
    assertEquals(3, LauncherSwitches.clampDockRows(9));
    assertEquals(2, LauncherSwitches.nextDockRows(1));
    assertEquals(3, LauncherSwitches.nextDockRows(2));
    assertEquals(1, LauncherSwitches.nextDockRows(3));
    LauncherSwitches.setDockRows(ctx(), 5);
    assertEquals(3, LauncherSwitches.dockRows(ctx()));
    LauncherSwitches.setDockRows(ctx(), 1);
  }

  @Test public void searchFoldsArabicLetterShapesAndAccents() {
    assertTrue(AllAppsActivity.matches("أنغامي", "com.anghami", "انغامي"));
    assertTrue(AllAppsActivity.matches("مكتبة", "x", "مكتبه"));
    assertTrue(AllAppsActivity.matches("Café TV", "x", "cafe"));
    assertTrue(AllAppsActivity.matches("Kodi", "org.xbmc.kodi", "xbmc"));
    assertTrue(AllAppsActivity.matches("Kodi", "org.xbmc.kodi", "  "));
    assertFalse(AllAppsActivity.matches("Kodi", "org.xbmc.kodi", "netflix"));
  }

  @Test public void hiddenAppsStayOutOfAllAppsButThePlayStoreNever() {
    HashSet<String> removed = new HashSet<>(Arrays.asList("com.netflix.ninja", "com.android.vending"));
    assertFalse(AllAppsActivity.visible("com.netflix.ninja", "Netflix", removed, false, new HashSet<>()));
    assertTrue(AllAppsActivity.visible("com.android.vending", "Play Store", removed, false, new HashSet<>()));
    assertTrue(AllAppsActivity.visible("org.xbmc.kodi", "Kodi", removed, false, new HashSet<>()));
  }

  @Test public void allAppsScreenFiltersAsYouType() {
    AllAppsActivity.testApps = Arrays.asList(new String[]{"org.xbmc.kodi", "Kodi"}, new String[]{"com.anghami", "أنغامي"});
    AllAppsActivity a = Robolectric.buildActivity(AllAppsActivity.class).setup().get();
    assertEquals(2, a.shownCount());
    android.widget.EditText box = findEdit(a.getWindow().getDecorView());
    box.setText("انغ");
    assertEquals(1, a.shownCount());
    box.setText("zzz");
    assertEquals(0, a.shownCount());
  }

  private static android.widget.EditText findEdit(android.view.View v) {
    if (v instanceof android.widget.EditText) return (android.widget.EditText) v;
    if (v instanceof android.view.ViewGroup) {
      android.view.ViewGroup g = (android.view.ViewGroup) v;
      for (int i = 0; i < g.getChildCount(); i++) { android.widget.EditText e = findEdit(g.getChildAt(i)); if (e != null) return e; }
    }
    return null;
  }

  @Test public void aChangedFingerprintMeansTheSystemWasUpdated() {
    assertTrue(KeepAlive.updatedSince("philips/a:11/1", "philips/a:11/2"));
    assertFalse(KeepAlive.updatedSince("philips/a:11/1", "philips/a:11/1"));
    assertFalse("first boot ever is not an update", KeepAlive.updatedSince(null, "philips/a:11/1"));
    assertFalse(KeepAlive.updatedSince("", "philips/a:11/1"));
  }

  @Test public void keepAliveIsOnByDefaultOnPhilipsToo() {
    assertTrue(LauncherSwitches.keepAliveDefault("Philips"));
    assertTrue(LauncherSwitches.keepAliveDefault("TP Vision"));
    assertTrue(LauncherSwitches.keepAliveDefault("Xiaomi"));
    assertFalse(LauncherSwitches.keepAliveDefault("Sony"));
  }

  @Test public void cleanModeArabicNamesAndSoundsHaveTheirDefaults() {
    assertFalse(LauncherSwitches.minimal(ctx()));
    assertTrue(LauncherSwitches.arabicNames(ctx()));
    assertFalse(LauncherSwitches.sounds(ctx()));
    LauncherSwitches.setMinimal(ctx(), true);
    assertTrue(LauncherSwitches.minimal(ctx()));
    LauncherSwitches.setMinimal(ctx(), false);
  }

  @Test public void homeWithThreeDockRowsCleanModeAndSafeModeStillOpens() {
    LauncherSwitches.setDockRows(ctx(), 3);
    MainActivity a = Robolectric.buildActivity(MainActivity.class).setup().visible().get();
    assertEquals(MainActivity.COLUMNS * 3, MainActivity.DOCK);
    a.finish();
    LauncherSwitches.setDockRows(ctx(), 1);
    LauncherSwitches.setMinimal(ctx(), true);
    SafeMode.setActive(ctx(), true);
    MainActivity b = Robolectric.buildActivity(MainActivity.class).setup().get();
    assertEquals(MainActivity.COLUMNS, MainActivity.DOCK);
    assertFalse(b.isFinishing());
    assertEquals(ctx().getString(R.string.mi5g_safe_mode_on), org.robolectric.shadows.ShadowToast.getTextOfLatestToast());
    b.finish();
    LauncherSwitches.setMinimal(ctx(), false);
  }

  @Test public void threeCrashesHandAHomePressToTheStockHome() {
    long now = System.currentTimeMillis();
    for (int k = 0; k < 3; k++) SafeMode.record(ctx(), now - 30 + k);
    Intent home = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME);
    MainActivity none = Robolectric.buildActivity(MainActivity.class, home).setup().get();
    assertFalse("no stock home to hand over to: Mi5G stays", none.isFinishing());
    none.finish();
    android.content.pm.ResolveInfo ri = new android.content.pm.ResolveInfo();
    ri.activityInfo = new android.content.pm.ActivityInfo();
    ri.activityInfo.packageName = "com.google.android.tvlauncher";
    ri.activityInfo.name = "com.google.android.tvlauncher.MainActivity";
    ri.activityInfo.applicationInfo = new android.content.pm.ApplicationInfo();
    ri.activityInfo.applicationInfo.flags = android.content.pm.ApplicationInfo.FLAG_SYSTEM;
    android.content.pm.PackageInfo pi = new android.content.pm.PackageInfo();
    pi.packageName = ri.activityInfo.packageName;
    pi.applicationInfo = ri.activityInfo.applicationInfo;
    pi.applicationInfo.packageName = pi.packageName;
    org.robolectric.shadows.ShadowPackageManager spm = org.robolectric.Shadows.shadowOf(ctx().getPackageManager());
    spm.installPackage(pi);
    android.content.ComponentName stockName = new android.content.ComponentName(pi.packageName, ri.activityInfo.name);
    spm.addActivityIfNotPresent(stockName);
    android.content.IntentFilter filter = new android.content.IntentFilter(Intent.ACTION_MAIN);
    filter.addCategory(Intent.CATEGORY_HOME);
    filter.addCategory(Intent.CATEGORY_DEFAULT);
    spm.addIntentFilterForActivity(stockName, filter);
    Intent stock = SafeMode.stockHome(ctx());
    assertNotNull(stock);
    assertEquals("com.google.android.tvlauncher", stock.getComponent().getPackageName());
    MainActivity a = Robolectric.buildActivity(MainActivity.class, home).create().get();
    assertTrue(a.isFinishing());
    MainActivity b = Robolectric.buildActivity(MainActivity.class, new Intent(Intent.ACTION_MAIN)).setup().get();
    assertFalse("opening Mi5G from an app list always opens it", b.isFinishing());
    b.finish();
  }

  @Test public void settingsAndHiddenAppsScreensShowTheNewRows() {
    Robolectric.buildActivity(Mi5gSettingsActivity.class).setup().get().finish();
    Robolectric.buildActivity(GridSettingsActivity.class).setup().get().finish();
    Robolectric.buildActivity(ControlCenterActivity.class).setup().get().finish();
    TileMenu.remove(ctx(), "com.netflix.ninja");
    HiddenAppsActivity h = Robolectric.buildActivity(HiddenAppsActivity.class).setup().get();
    assertNotNull(findText(h.getWindow().getDecorView(), ctx().getString(R.string.mi5g_hidden_removed_title)));
    h.finish();
  }

  private static android.view.View findText(android.view.View v, String text) {
    if (v instanceof android.widget.TextView && text.contentEquals(((android.widget.TextView) v).getText())) return v;
    if (v instanceof android.view.ViewGroup) {
      android.view.ViewGroup g = (android.view.ViewGroup) v;
      for (int i = 0; i < g.getChildCount(); i++) { android.view.View e = findText(g.getChildAt(i), text); if (e != null) return e; }
    }
    return null;
  }
}
