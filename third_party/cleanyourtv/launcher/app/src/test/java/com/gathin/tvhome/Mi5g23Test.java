package com.gathin.tvhome;
import static org.junit.Assert.*;
import android.content.Context;
import android.os.Bundle;
import android.media.tv.TvInputInfo;
import android.provider.Settings;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class) @Config(sdk=34)
public class Mi5g23Test {
  private static final String PKG = "com.gathin.tvhome";

  @Test public void cyclesOnlyWhenOursListedAndSwitchIsAConfirmedZero() {
    Set<String> ours = new LinkedHashSet<>(Collections.singleton(PKG + "/.HomeKeyService"));
    assertTrue(Revival.needsCycle(ours, 0));
    assertFalse(Revival.needsCycle(ours, 1));
    assertFalse(Revival.needsCycle(ours, -1));
    assertFalse(Revival.needsCycle(Collections.emptySet(), 0));
  }

  @Test public void withoutDropsOnlyOurServices() {
    String enabled = "com.other/.A11y:" + PKG + "/.HomeKeyService:" + PKG + "/.InputKeyService:com.x/.B";
    assertEquals("com.other/.A11y:com.x/.B", Revival.without(enabled, PKG));
    assertEquals("", Revival.without(null, PKG));
  }

  @Test public void restoreCyclesAHelperTheSwitchLeftAtZero() {
    Context c = RuntimeEnvironment.getApplication();
    org.robolectric.Shadows.shadowOf(RuntimeEnvironment.getApplication())
        .grantPermissions(android.Manifest.permission.WRITE_SECURE_SETTINGS);
    String enabled = PKG + "/.HomeKeyService";
    Settings.Secure.putString(c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, enabled);
    Settings.Secure.putInt(c.getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, 0);
    Revival.restore(c);
    assertEquals(1, Settings.Secure.getInt(c.getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, -1));
    assertEquals(enabled, Settings.Secure.getString(c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES));
  }

  @Test public void restoreLeavesAnUnsetSwitchAlone() {
    Context c = RuntimeEnvironment.getApplication();
    org.robolectric.Shadows.shadowOf(RuntimeEnvironment.getApplication())
        .grantPermissions(android.Manifest.permission.WRITE_SECURE_SETTINGS);
    Settings.Secure.putString(c.getContentResolver(), Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, PKG + "/.HomeKeyService");
    Revival.restore(c);
    assertEquals(-1, Settings.Secure.getInt(c.getContentResolver(), Settings.Secure.ACCESSIBILITY_ENABLED, -1));
  }

  @Test public void homeLockAndCecSwitchAreOffByDefault() {
    Context c = RuntimeEnvironment.getApplication();
    assertFalse(LauncherSwitches.homeLocked(c));
    assertFalse(LauncherSwitches.cecSwitch(c));
    LauncherSwitches.setHomeLocked(c, true);
    LauncherSwitches.setCecSwitch(c, true);
    assertTrue(LauncherSwitches.homeLocked(c));
    assertTrue(LauncherSwitches.cecSwitch(c));
  }

  private static final List<String> IDS = Arrays.asList(
      "com.droidlogic.tvinput/.services.Hdmi1InputService/HW5",
      "com.droidlogic.tvinput/.services.Hdmi2InputService/HW6");

  // Cases from Aerial TV's CecSwitchTest (MIT, Copyright (c) 2026 pengchujin).
  @Test public void cecPicksTheNamedInputOrThePortADeviceSitsUnder() {
    assertEquals(IDS.get(1), CecSwitch.pick(IDS, Arrays.asList("HDMI2", IDS.get(1)), null));
    String device = "com.droidlogic.tvinput/.services.Hdmi2InputService/HDMI100004";
    assertEquals(IDS.get(1), CecSwitch.pick(IDS, Collections.singletonList(device), d -> d.equals(device) ? IDS.get(1) : null));
    assertNull(CecSwitch.pick(IDS, Arrays.asList("HDMI2", "NintendoSwitch"), null));
    assertNull(CecSwitch.pick(IDS, Collections.emptyList(), null));
  }

  @Test public void cecTextsPutAndroidsInputIdFirst() {
    Bundle b = new Bundle();
    b.putString("label", "HDMI2");
    b.putString(TvInputInfo.EXTRA_INPUT_ID, IDS.get(0));
    b.putInt("port", 2);
    List<String> t = CecSwitch.texts(b);
    assertEquals(IDS.get(0), t.get(0));
    assertTrue(t.contains("HDMI2"));
    assertEquals(2, t.size());
  }
}
