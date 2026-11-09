package com.gathin.tvhome;
import static org.junit.Assert.*;
import android.view.KeyEvent;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class) @Config(sdk=34)
public class Mi5g22Test {
  @Test public void channelKeysAreChannelUpAndDownOnly() {
    assertTrue(RemoteKeys.isChannelKey(KeyEvent.KEYCODE_CHANNEL_UP));
    assertTrue(RemoteKeys.isChannelKey(KeyEvent.KEYCODE_CHANNEL_DOWN));
    assertFalse(RemoteKeys.isChannelKey(KeyEvent.KEYCODE_TV));
    assertFalse(RemoteKeys.isChannelKey(KeyEvent.KEYCODE_VOLUME_UP));
  }

  @Test public void ignoreChannelKeysOffByDefaultAndToggleable() {
    android.content.Context c = RuntimeEnvironment.getApplication();
    assertFalse(LauncherSwitches.homeIgnoreChannelKeys(c));
    LauncherSwitches.setHomeIgnoreChannelKeys(c, true);
    assertTrue(LauncherSwitches.homeIgnoreChannelKeys(c));
    LauncherSwitches.setHomeIgnoreChannelKeys(c, false);
    assertFalse(LauncherSwitches.homeIgnoreChannelKeys(c));
  }

  @Test public void settingsClearTaskOffByDefaultAndToggleable() {
    android.content.Context c = RuntimeEnvironment.getApplication();
    assertFalse(LauncherSwitches.settingsKeyClearTask(c));
    LauncherSwitches.setSettingsKeyClearTask(c, true);
    assertTrue(LauncherSwitches.settingsKeyClearTask(c));
  }

  @Test public void homeSwallowsChannelKeysWhenEnabled() {
    android.content.Context c = RuntimeEnvironment.getApplication();
    LauncherSwitches.setHomeIgnoreChannelKeys(c, true);
    MainActivity a = Robolectric.buildActivity(MainActivity.class).setup().get();
    assertTrue(a.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_CHANNEL_UP)));
    assertTrue(a.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_CHANNEL_DOWN)));
    a.finish();
  }

  @Test @Config(qualifiers="ar-rSA-ldrtl-w960dp-h540dp-land")
  public void arabicSettingsShowsNewToggles() throws Exception {
    Mi5gSettingsActivity a = Robolectric.buildActivity(Mi5gSettingsActivity.class).setup().visible().get();
    org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
    android.view.View root = a.getWindow().getDecorView();
    // Force a rebuild path that includes the new rows by toggling.
    LauncherSwitches.setHomeIgnoreChannelKeys(a, true);
    a.onResume();
    org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
    Mi5gScreensTest.save(root, "v220-settings-toggles-ar");
    a.finish();
  }
}
