package com.gathin.tvhome;
import static org.junit.Assert.*;
import android.content.Context;
import android.content.Intent;
import android.view.KeyEvent;
import android.widget.TextView;
import java.io.File;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;

/** 2.4 M3: phone companion on the TV side. */
@RunWith(RobolectricTestRunner.class) @Config(sdk=34)
public class Mi5g26Test {
  private Context ctx() { return RuntimeEnvironment.getApplication(); }
  @After public void stop() { ArrangeServer.stopRunning(); }

  @Test public void liveBoardReadsThePhonesStrokeFormat() {
    List<int[]> s = LiveBoardActivity.decode("000000500500999999-250749");
    assertEquals(2, s.size());
    assertArrayEquals(new int[]{0, 0, 500, 500, 999, 999}, s.get(0));
    assertArrayEquals(new int[]{250, 749}, s.get(1));
    assertTrue(LiveBoardActivity.decode("12345").isEmpty());
    assertTrue(LiveBoardActivity.decode("000000;rm").isEmpty());
    assertTrue(LiveBoardActivity.decode(null).isEmpty());
  }

  @Test public void liveBoardTextIsCleanedUtf8() {
    assertEquals("سلام", LiveBoardActivity.fromHex("d8b3d984d8a7d985"));
    assertEquals("ab", LiveBoardActivity.fromHex("e280ae610a62"));   // RTL override and newline dropped
    assertEquals("", LiveBoardActivity.fromHex("zz"));
    assertEquals("", LiveBoardActivity.fromHex("abc"));
  }

  @Test public void liveBoardShowsThenClosesFromThePhone() {
    Intent show = new Intent(ctx(), LiveBoardActivity.class).putExtra("strokes", "000000999999").putExtra("hex", "d8b3d984d8a7d985");
    ActivityController<LiveBoardActivity> c = Robolectric.buildActivity(LiveBoardActivity.class, show).setup();
    LiveBoardActivity a = c.get();
    assertNotNull(findText(a.getWindow().getDecorView(), "سلام"));
    c.newIntent(new Intent(ctx(), LiveBoardActivity.class).putExtra("close", true));
    assertTrue(a.isFinishing());
    LiveBoardActivity closed = Robolectric.buildActivity(LiveBoardActivity.class, new Intent().putExtra("close", true)).create().get();
    assertTrue(closed.isFinishing());
  }

  @Test public void connectLinkAndPageCarryOnlyTheAddress() {
    assertEquals("mi5gtv://connect?host=192.168.1.50&port=5555&name=Mi%20TV%204A", PhoneConnectActivity.appLink("192.168.1.50", "Mi TV 4A"));
    String page = new String(PhoneConnectActivity.page("192.168.1.50", "<b>TV\"</b>"), StandardCharsets.UTF_8);
    assertTrue(page.contains("intent://connect?host=192.168.1.50&amp;port=5555"));
    assertTrue(page.contains("#Intent;scheme=mi5gtv;end"));
    assertFalse(page.contains("<b>TV"));
    assertTrue(page.contains("&lt;b&gt;TV&quot;"));
  }

  @Test public void connectPageNeedsNoTokenButEverythingElseStillDoes() throws Exception {
    ArrangeServer server = ArrangeServer.start(ctx(), null);
    assertTrue(get(server, "/connect").startsWith("HTTP/1.1 200"));
    assertTrue(get(server, "/connect").contains("mi5gtv"));
    assertTrue(get(server, "/api/state").startsWith("HTTP/1.1 404"));
    assertTrue(get(server, "/connect/api/state").startsWith("HTTP/1.1 404"));
  }

  @Test public void wallpaperUploadsKeepTwentyAndReportWhatWasSaved() {
    byte[] jpg = new byte[64];
    jpg[0] = (byte) 0xFF; jpg[1] = (byte) 0xD8;
    String last = null;
    for (int i = 0; i < 25; i++) { last = Mi5gWallpapers.saveUpload(ctx(), jpg); assertNotNull(last); try { Thread.sleep(2); } catch (InterruptedException ignored) { } }
    File[] kept = Mi5gWallpapers.dir(ctx()).listFiles((d, n) -> n.startsWith("phone-"));
    assertEquals(20, kept.length);
    assertEquals(64, Mi5gWallpapers.savedSize(ctx(), last));
    assertEquals(-1, Mi5gWallpapers.savedSize(ctx(), "../x"));
    assertEquals("{\"saved\":false}", ArrangeServer.savedReply(false, null, -1));
    assertTrue(ArrangeServer.savedReply(true, last, 64).contains("\"bytes\":64"));
  }

  @Test public void searchKeyOnHomeOpensAllApps() {
    MainActivity a = Robolectric.buildActivity(MainActivity.class).setup().get();
    a.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SEARCH));
    a.dispatchKeyEvent(new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_SEARCH));
    Intent next = Shadows.shadowOf(a).getNextStartedActivity();
    while (next != null && (next.getComponent() == null || !next.getComponent().getClassName().endsWith("AllAppsActivity"))) next = Shadows.shadowOf(a).getNextStartedActivity();
    assertNotNull(next);
    a.finish();
  }

  @Test public void connectScreenOpensWithAQrOrANoNetworkNote() {
    PhoneConnectActivity a = Robolectric.buildActivity(PhoneConnectActivity.class).setup().get();
    assertNotNull(a.findViewById(R.id.mi5g_qr));
    a.finish();
  }

  private static String get(ArrangeServer server, String path) throws Exception {
    java.lang.reflect.Field f = ArrangeServer.class.getDeclaredField("socket"); f.setAccessible(true);
    int port = ((ServerSocket) f.get(server)).getLocalPort();
    try (Socket s = new Socket("127.0.0.1", port)) {
      s.setSoTimeout(5000);
      s.getOutputStream().write(("GET " + path + " HTTP/1.1\r\nHost: localhost\r\nContent-Length: 0\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
      s.getOutputStream().flush();
      return new String(s.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
    }
  }

  private static android.view.View findText(android.view.View v, String text) {
    if (v instanceof TextView && text.contentEquals(((TextView) v).getText())) return v;
    if (v instanceof android.view.ViewGroup) {
      android.view.ViewGroup g = (android.view.ViewGroup) v;
      for (int i = 0; i < g.getChildCount(); i++) { android.view.View e = findText(g.getChildAt(i), text); if (e != null) return e; }
    }
    return null;
  }
}
