package com.gathin.tvhome;

import static org.junit.Assert.*;

import android.content.Context;
import android.database.MatrixCursor;
import android.graphics.Bitmap;

import org.robolectric.RuntimeEnvironment;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.net.InetAddress;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Set;

/** Mi5G 2.0.1: arrange-from-phone, folders, inputs, wallpapers, revival and the city screensaver. */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34)
public class Mi5g201Test {

    private final Context ctx = RuntimeEnvironment.getApplication();

    @Test public void layoutRoundTripsAndDropsJunk() throws Exception {
        JSONObject o = new JSONObject()
                .put("names", new JSONObject().put("com.netflix.ninja", "نتفليكس").put("bad pkg", "x").put("com.a.b", ""))
                .put("folders", new JSONArray()
                        .put(new JSONObject().put("id", "kids").put("name", "أطفال").put("apps", new JSONArray().put("com.a.b").put("com.c.d")))
                        .put(new JSONObject().put("id", "BAD!").put("name", "x"))
                        .put(new JSONObject().put("id", "two").put("name", "ثاني").put("apps", new JSONArray().put("com.a.b").put("com.e.f"))))
                .put("inputs", new JSONArray().put(new JSONObject().put("id", "com.mediatek.tvinput/.hdmi.HDMIInputService/HW5").put("name", "بلايستيشن")))
                .put("options", new JSONObject().put("columns", 9).put("rows", 2).put("clockSize", "l").put("clockCorner", "end").put("names", "never").put("focus", "border"));
        HomeLayout l = HomeLayout.decode(o);
        assertEquals(Collections.singletonMap("com.netflix.ninja", "نتفليكس"), l.names);
        assertEquals(2, l.folders.size());
        assertEquals(Arrays.asList("com.a.b", "com.c.d"), l.folders.get(0).apps);
        assertEquals("an app is in one folder only", Collections.singletonList("com.e.f"), l.folders.get(1).apps);
        assertEquals("kids", l.folderOf("com.c.d").id);
        assertNull(l.folderOf("com.x.y"));
        assertEquals("بلايستيشن", l.inputs.get(0).name);
        assertEquals("out-of-range columns keep the theme's", 0, l.options.columns);
        assertEquals(2, l.options.rows);
        assertEquals("l", l.options.clockSize);
        assertEquals("end", l.options.clockCorner);
        assertEquals("never", l.options.names);
        assertEquals("border", l.options.focus);

        HomeLayout again = HomeLayout.decode(HomeLayout.encode(l));
        assertEquals(l.names, again.names);
        assertEquals(l.folders.get(1).apps, again.folders.get(1).apps);
        assertEquals(l.inputs.get(0).id, again.inputs.get(0).id);
        assertEquals(l.options.focus, again.options.focus);
        assertSame(HomeLayout.EMPTY, HomeLayout.decode(null));
    }

    @Test public void cleanNameStripsControlAndBidiAndCaps() {
        assertEquals("abc", HomeLayout.cleanName("a\u202Eb\u0000c"));
        assertEquals(40, HomeLayout.cleanName(new String(new char[90]).replace('\0', 'x')).length());
        assertEquals("", HomeLayout.cleanName(null));
    }

    @Test public void layoutSavesAndLoads() {
        File f = new File(ctx.getFilesDir(), "t.json");
        HomeLayout l = HomeLayout.decode(new org.json.JSONObject(new HashMap<String, Object>() {{
            put("names", new HashMap<String, String>() {{ put("com.a.b", "Mine"); }});
        }}));
        assertTrue(HomeLayout.save(f, l));
        assertEquals("Mine", HomeLayout.load(f).names.get("com.a.b"));
        assertSame(HomeLayout.EMPTY.options, HomeLayout.load(new File(ctx.getFilesDir(), "none.json")).options);
    }

    @Test public void serverOnlyAnswersLocalPeersWithTheToken() throws Exception {
        assertTrue(ArrangeServer.localPeer(InetAddress.getByName("192.168.1.20")));
        assertTrue(ArrangeServer.localPeer(InetAddress.getByName("10.0.0.5")));
        assertTrue(ArrangeServer.localPeer(InetAddress.getByName("127.0.0.1")));
        assertFalse(ArrangeServer.localPeer(InetAddress.getByName("8.8.8.8")));
        assertFalse(ArrangeServer.localPeer(null));
        String t = ArrangeServer.newToken(new SecureRandom());
        assertTrue(t.length() >= 22);
        assertNotEquals(t, ArrangeServer.newToken(new SecureRandom()));
        assertTrue(ArrangeServer.tokenMatches(t, t));
        assertFalse(ArrangeServer.tokenMatches(t, t.substring(1)));
        assertFalse(ArrangeServer.tokenMatches(t, null));
    }

    @Test public void requestLineParses() {
        ArrangeServer.Request r = ArrangeServer.parseLine("GET /tok/api/tile.png?key=com.a.b%2F.Main HTTP/1.1", new HashMap<>());
        assertEquals("GET", r.method);
        assertEquals("/tok/api/tile.png", r.path);
        assertEquals("com.a.b/.Main", r.query.get("key"));
        assertNull(ArrangeServer.parseLine("GARBAGE", new HashMap<>()));
        assertNull(ArrangeServer.parseLine("GET / SMTP", new HashMap<>()));
    }

    @Test public void serverStartsAndStops() {
        ArrangeServer s = ArrangeServer.start(ctx, null);
        assertNotNull(s);
        assertTrue(ArrangeServer.isRunning());
        ArrangeServer.stopRunning();
        assertFalse("the server is off when the screen closes", ArrangeServer.isRunning());
    }

    @Test public void applyLayoutWritesAllStores() throws Exception {
        JSONObject body = new JSONObject()
                .put("order", new JSONArray().put("com.b.b/.Main").put("com.a.a/.Main").put("not a key"))
                .put("hidden", new JSONArray().put("com.c.c").put("bad pkg"))
                .put("names", new JSONObject().put("com.a.a", "أ"))
                .put("options", new JSONObject().put("columns", 5).put("rows", 1));
        final boolean[] changed = {false};
        HomeLayout.setListener(() -> changed[0] = true);
        assertTrue(ArrangeServer.applyLayout(ctx, body, Collections.emptyList()));
        org.robolectric.shadows.ShadowLooper.idleMainLooper();
        HomeLayout.setListener(null);
        assertTrue(changed[0]);
        assertEquals("أ", HomeLayout.load(ctx).names.get("com.a.a"));
        assertEquals(Collections.singleton("com.c.c"), TileMenu.prefs(ctx).getStringSet(TileMenu.REMOVED_KEY, null));
        assertEquals(5, TileMenu.prefs(ctx).getInt("columns", 0));
        assertEquals(1, TileMenu.prefs(ctx).getInt(com.gathin.tvhome.Config.ROWS_BELOW_DOCK_KEY, 0));
    }

    @Test public void wallpaperTypesAndBing() {
        assertEquals("jpg", Mi5gWallpapers.kindOf(new byte[]{(byte) 0xFF, (byte) 0xD8, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0}));
        assertEquals("png", Mi5gWallpapers.kindOf(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0, 0, 0, 0, 0, 0, 0, 0}));
        assertEquals("mp4", Mi5gWallpapers.kindOf(new byte[]{0, 0, 0, 0x18, 'f', 't', 'y', 'p', 0, 0, 0, 0}));
        assertNull("an executable is not a wallpaper", Mi5gWallpapers.kindOf("MZ\u0090\u0000aaaaaaaaaaaa".getBytes()));
        assertNull(Mi5gWallpapers.kindOf(new byte[3]));
        assertEquals("https://www.bing.com/th?id=OHR.X_1920x1080.jpg",
                Mi5gWallpapers.bingUrl("{\"images\":[{\"url\":\"/th?id=OHR.X_1920x1080.jpg\"}]}"));
        assertNull(Mi5gWallpapers.bingUrl("{\"images\":[{\"url\":\"https://evil.example/x.jpg\"}]}"));
        assertNull(Mi5gWallpapers.bingUrl("{\"images\":[{\"url\":\"/th?id=//evil\"}]}"));
        assertNull(Mi5gWallpapers.bingUrl("nope"));
        assertFalse(Mi5gWallpapers.safeName("../x.png"));
        assertTrue(Mi5gWallpapers.safeName("phone-1.jpg"));
    }

    @Test public void builtInWallpaperChoice() {
        assertTrue(Mi5gWallpapers.choose(ctx, "mi5g-teal.png"));
        assertEquals("mi5g-teal.png", TileMenu.prefs(ctx).getString(com.gathin.tvhome.Config.WALLPAPER_CHOICE_KEY, ""));
        assertFalse(Mi5gWallpapers.choose(ctx, "../../etc/passwd"));
        assertTrue(Mi5gWallpapers.choose(ctx, "none"));
        assertNull(Mi5gWallpapers.video(ctx));
    }

    @Test public void qrCodeIsDrawn() {
        Bitmap b = ArrangeActivity.qr("http://192.168.1.20:41234/abcdef/", 300);
        assertNotNull(b);
        assertEquals(300, b.getWidth());
    }

    @Test public void revivalPutsBackOnlyOurMissingServices() {
        String pkg = "com.gathin.tvhome";
        assertEquals(new LinkedHashSet<>(Collections.singletonList("com.gathin.tvhome/.HomeKeyService")),
                Revival.ours(pkg, "com.other/.Svc:com.gathin.tvhome/com.gathin.tvhome.HomeKeyService"));
        Set<String> wanted = new LinkedHashSet<>(Arrays.asList("com.gathin.tvhome/.HomeKeyService", "com.gathin.tvhome/.InputKeyService"));
        assertEquals("com.other/.Svc:com.gathin.tvhome/.HomeKeyService:com.gathin.tvhome/.InputKeyService",
                Revival.merge("com.other/.Svc", wanted, pkg));
        assertEquals("com.gathin.tvhome/.HomeKeyService:com.gathin.tvhome/.InputKeyService",
                Revival.merge("", wanted, pkg));
        assertEquals("nothing twice", "com.gathin.tvhome/.HomeKeyService:com.gathin.tvhome/.InputKeyService",
                Revival.merge("com.gathin.tvhome/.HomeKeyService:com.gathin.tvhome/.InputKeyService", wanted, pkg));
    }

    @Test public void cityScreenReadsThePrayerModuleAsIs() {
        MatrixCursor c = new MatrixCursor(new String[]{"name", "time", "is_next", "city", "lat", "lon"});
        c.addRow(new Object[]{"الفجر", "04:58", 0, "مسقط", 23.588, 58.383});
        c.addRow(new Object[]{"الظهر", "11:55", 1, "مسقط", 23.588, 58.383});
        CityScreen.Data d = new CityScreen.Data();
        CityScreen.readPrayer(c, d);
        assertEquals("مسقط", d.city);
        assertEquals("الظهر  11:55", d.prayer);
        assertEquals(23.588, d.lat, 1e-9);
        CityScreen.Data none = CityScreen.load(ctx);   // no module installed: nothing shown, no crash
        assertEquals("", none.prayer);
    }

    @Test public void weatherAndDates() {
        String w = CityScreen.parseWeather("{\"current\":{\"temperature_2m\":28.4,\"weather_code\":0}}");
        assertTrue(w, w.endsWith("صحو"));
        assertEquals("", CityScreen.parseWeather("{}"));
        assertEquals("مطر", CityScreen.sky(61));
        assertEquals("", CityScreen.sky(500 - 600));
        Date d = new Date(1793678400000L);   // 2026-11-03 04:00 UTC
        String h = CityScreen.hijriDate(d);
        assertTrue(h, h.endsWith("هـ"));
        assertTrue(h, h.contains("جمادى"));
        assertTrue(CityScreen.gregorianDate(d), CityScreen.gregorianDate(d).contains("نوفمبر"));
    }
}
