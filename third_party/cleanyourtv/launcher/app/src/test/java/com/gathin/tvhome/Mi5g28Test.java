package com.gathin.tvhome;
import static org.junit.Assert.*;
import android.content.Context;
import android.content.Intent;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

/** 2.4 M5: Home wakes the Mi5G prayer module's Keep Alive whenever it comes back. */
@RunWith(RobolectricTestRunner.class) @Config(sdk=34)
public class Mi5g28Test {
  @Test public void homeEventPingsThePrayerModuleEvenWhenStopped() {
    Intent i = KeepAlive.prayerPing();
    assertEquals("com.midroid.mi5gtv.prayer.KEEP_ALIVE", i.getAction());
    assertEquals("com.midroid.mi5gtv.prayer", i.getPackage());
    assertTrue((i.getFlags() & Intent.FLAG_INCLUDE_STOPPED_PACKAGES) != 0);
    Context ctx = RuntimeEnvironment.getApplication();
    KeepAlive.onEvent(ctx);
    List<Intent> sent = Shadows.shadowOf(RuntimeEnvironment.getApplication()).getBroadcastIntents();
    assertTrue(sent.stream().anyMatch(x -> KeepAlive.PRAYER_KEEP_ALIVE.equals(x.getAction()) && KeepAlive.PRAYER_PKG.equals(x.getPackage())));
  }
}
