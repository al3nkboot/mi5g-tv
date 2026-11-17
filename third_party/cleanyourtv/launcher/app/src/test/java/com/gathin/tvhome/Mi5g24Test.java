package com.gathin.tvhome;
import static org.junit.Assert.*;
import android.content.Context;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** 2.4: Keep Alive, Home lock refusals and Keep screen awake. */
@RunWith(RobolectricTestRunner.class) @Config(sdk=34)
public class Mi5g24Test {
  private Context ctx() { return RuntimeEnvironment.getApplication(); }

  @Test public void keepAliveIsOnByDefaultOnXiaomi() {
    assertTrue(LauncherSwitches.keepAliveDefault("Xiaomi"));
    assertTrue(LauncherSwitches.keepAliveDefault(" xiaomi "));
    assertFalse(LauncherSwitches.keepAliveDefault("Sony"));
    assertFalse(LauncherSwitches.keepAliveDefault(null));
  }

  @Test public void keepAliveSwitchOverridesTheDefault() {
    LauncherSwitches.setKeepAlive(ctx(), true);
    assertTrue(LauncherSwitches.keepAlive(ctx()));
    LauncherSwitches.setKeepAlive(ctx(), false);
    assertFalse(LauncherSwitches.keepAlive(ctx()));
    assertFalse(KeepAlive.homeLost(ctx()));
  }

  @Test public void keepAliveDebouncesButNeverLoops() {
    assertTrue(KeepAlive.due(10_000, 0));
    assertFalse(KeepAlive.due(10_000 + KeepAlive.DEBOUNCE_MS - 1, 10_000));
    assertTrue(KeepAlive.due(10_000 + KeepAlive.DEBOUNCE_MS, 10_000));
    assertTrue(KeepAlive.due(500, 10_000));   // clock went back: rebooted
  }

  @Test public void homeLockRefusesEditsWithANote() {
    LauncherSwitches.setHomeLocked(ctx(), false);
    assertFalse(LauncherSwitches.refuseIfLocked(ctx()));
    LauncherSwitches.setHomeLocked(ctx(), true);
    assertTrue(LauncherSwitches.refuseIfLocked(ctx()));
    assertEquals(ctx().getString(R.string.mi5g_home_locked_hint), org.robolectric.shadows.ShadowToast.getTextOfLatestToast());
    LauncherSwitches.setHomeLocked(ctx(), false);
  }

  @Test public void homeLockCoversThePhoneLayoutEditsOnly() {
    assertTrue(ArrangeServer.lockedEdit("api/layout"));
    assertTrue(ArrangeServer.lockedEdit("api/icon"));
    assertTrue(ArrangeServer.lockedEdit("api/icon/reset"));
    assertFalse(ArrangeServer.lockedEdit("api/message"));
    assertFalse(ArrangeServer.lockedEdit("api/stop"));
  }

  @Test public void caffeineCyclesOffOneTwoFourHoursAndUntilOff() {
    long c = 0;
    long[] seen = new long[5];
    for (int i = 0; i < 5; i++) { c = Caffeine.next(c); seen[i] = c; }
    assertArrayEquals(new long[]{Caffeine.HOUR, 2 * Caffeine.HOUR, 4 * Caffeine.HOUR, Caffeine.FOREVER, 0}, seen);
  }

  @Test public void caffeineEndsOnTimeAndNotAcrossAReboot() {
    long u = Caffeine.until(1_000, Caffeine.HOUR);
    assertEquals(Caffeine.HOUR, Caffeine.left(u, 1_000, 7, 7));
    assertEquals(0, Caffeine.left(u, 1_000 + Caffeine.HOUR, 7, 7));
    assertEquals(Caffeine.FOREVER, Caffeine.left(Caffeine.until(5, Caffeine.FOREVER), 99_999_999, 7, 7));
    assertEquals(0, Caffeine.left(Caffeine.FOREVER, 5, 7, 8));   // rebooted
    assertEquals(0, Caffeine.until(5, 0));
  }

  @Test public void caffeineSetAndOff() {
    Caffeine.set(ctx(), Caffeine.FOREVER);
    assertTrue(Caffeine.active(ctx()));
    assertEquals(Caffeine.FOREVER, Caffeine.choice(ctx()));
    Caffeine.set(ctx(), 0);
    assertFalse(Caffeine.active(ctx()));
    assertEquals(R.string.mi5g_off, Caffeine.labelRes(Caffeine.choice(ctx())));
  }
}
