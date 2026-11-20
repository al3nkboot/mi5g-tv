package com.gathin.tvhome;
import static org.junit.Assert.*;
import android.content.Context;
import android.content.Intent;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import org.json.JSONObject;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;

/** 2.4 M4: occasions, reminders after the adhan, the weekly schedule, Home widgets, Control Center panels. */
@RunWith(RobolectricTestRunner.class) @Config(sdk=34)
public class Mi5g27Test {
  private Context ctx() { return RuntimeEnvironment.getApplication(); }

  @Test public void hijriDatesRoundTripAndNationalDays() {
    // 1 Ramadan 1447 is 18 Feb 2026 in Umm al-Qura.
    assertEquals(LocalDate.of(2026, 2, 18), Occasions.gregorian(1447, 9, 1));
    assertArrayEquals(new int[]{1447, 9, 1}, Occasions.hijri(LocalDate.of(2026, 2, 18)));
    // On 18 Nov 2026 the next Ramadan is 1448 (early Feb 2027).
    LocalDate r = Occasions.nextHijri(LocalDate.of(2026, 11, 18), 9, 1);
    assertEquals(2027, r.getYear());
    assertEquals(LocalDate.of(2026, 11, 20), Occasions.nextNational(LocalDate.of(2026, 11, 18), "OM"));
    assertEquals(LocalDate.of(2027, 11, 20), Occasions.nextNational(LocalDate.of(2026, 11, 21), "OM"));
    assertEquals(LocalDate.of(2027, 2, 25), Occasions.nextNational(LocalDate.of(2026, 11, 18), "KW"));
    assertNull(Occasions.nextNational(LocalDate.of(2026, 11, 18), ""));
  }

  @Test public void countdownsAreSortedAndOffByDefault() {
    List<Occasions.Countdown> list = Occasions.upcoming(LocalDate.of(2026, 11, 18), "OM");
    assertEquals(5, list.size());
    assertEquals(R.string.mi5g_nd_om, list.get(0).labelRes);
    assertEquals(2, list.get(0).days);
    assertFalse(list.get(0).approximate);
    for (int i = 1; i < list.size(); i++) assertTrue(list.get(i - 1).days <= list.get(i).days);
    assertEquals("", Occasions.homeLine(ctx(), LocalDate.of(2026, 11, 18)));
    assertEquals("", Occasions.saverLine(ctx(), LocalDate.of(2026, 11, 18)));
    Occasions.setCountdowns(ctx(), true);
    Occasions.setCountry(ctx(), "OM");
    assertEquals("Oman National Day in 2 days", Occasions.homeLine(ctx(), LocalDate.of(2026, 11, 18)));
    assertEquals("Today: Oman National Day", Occasions.homeLine(ctx(), LocalDate.of(2026, 11, 20)));
    Occasions.setCountry(ctx(), "XX");
    assertEquals("", Occasions.country(ctx()));
    assertEquals("OM", Occasions.nextCountry(""));
    assertEquals("", Occasions.nextCountry("KW"));
  }

  @Test public void hijriCountdownsSayAbout() {
    Occasions.setCountdowns(ctx(), true);
    String s = Occasions.homeLine(ctx(), LocalDate.of(2027, 1, 1));
    assertTrue(s, s.startsWith("Ramadan in about "));
  }

  @Test public void arabicCountdownUsesArabicPlurals() {
    Resources ar = arabic();
    Occasions.Countdown c = new Occasions.Countdown(R.string.mi5g_occ_ramadan, LocalDate.now(), 5, true);
    assertEquals("رمضان بعد ٥ أيام تقريبًا", Occasions.line(ar, c));
    assertEquals("رمضان بعد يومين تقريبًا", Occasions.line(ar, new Occasions.Countdown(R.string.mi5g_occ_ramadan, LocalDate.now(), 2, true)));
  }

  @Test public void saverLineDuringRamadanAndWhiteDays() {
    Occasions.setSaverInfo(ctx(), true);
    assertEquals("Day 5 of Ramadan", Occasions.saverLine(ctx(), Occasions.gregorian(1447, 9, 5)));
    assertEquals(ctx().getString(R.string.mi5g_occ_white_days), Occasions.saverLine(ctx(), Occasions.gregorian(1448, 4, 14)));
    assertEquals(ctx().getString(R.string.mi5g_occ_white_days_tomorrow), Occasions.saverLine(ctx(), Occasions.gregorian(1448, 4, 12)));
  }

  @Test public void cityScreenShowsTheCalendarLineOnlyWhenOn() {
    CityScreen s = new CityScreen(ctx());
    CityScreen.Data d = new CityScreen.Data();
    s.bind(d);
    assertEquals(View.GONE, s.info.getVisibility());
    d.info = "Day 5 of Ramadan";
    s.bind(d);
    assertEquals(View.VISIBLE, s.info.getVisibility());
    assertEquals("Day 5 of Ramadan", s.info.getText().toString());
  }

  @Test public void remindersWaitForTheAdhan() {
    List<Integer> prayers = Arrays.asList(5 * 60, 12 * 60 + 10, 15 * 60 + 30, 17 * 60 + 50, 19 * 60 + 10);
    assertEquals(10 * 60, Reminders.afterAdhan(10 * 60, prayers));          // nowhere near
    assertEquals(17 * 60 + 47, Reminders.afterAdhan(17 * 60 + 47, prayers)); // 3 minutes before: fine
    assertEquals(18 * 60 + 10, Reminders.afterAdhan(17 * 60 + 48, prayers)); // 2 minutes before: after the window
    assertEquals(18 * 60 + 10, Reminders.afterAdhan(18 * 60, prayers));
    assertEquals(18 * 60 + 10, Reminders.afterAdhan(18 * 60 + 10, prayers)); // window over
    // Two prayers close together: it keeps waiting.
    assertEquals(13 * 60 + 12, Reminders.afterAdhan(12 * 60 + 40, Arrays.asList(12 * 60 + 30, 12 * 60 + 52)));
    assertEquals(12 * 60 + 50, Reminders.afterAdhan(12 * 60 + 40, Arrays.asList(12 * 60 + 30, 12 * 60 + 55)));
    assertEquals(9 * 60, Reminders.afterAdhan(9 * 60, Collections.emptyList()));
  }

  @Test public void prayerTimesParseAsciiAndArabicDigits() {
    assertEquals(17 * 60 + 40, Reminders.minuteOf("17:40"));
    assertEquals(5 * 60 + 12, Reminders.minuteOf("٠٥:١٢"));
    assertEquals(-1, Reminders.minuteOf("25:00"));
    assertEquals(-1, Reminders.minuteOf(""));
    assertEquals(-1, Reminders.minuteOf(null));
  }

  @Test public void remindersParseCleanAndFindTheNext() {
    String json = "[{\"text\":\"Medicine\u202e\",\"minute\":1110,\"days\":127,\"on\":true},"
        + "{\"text\":\"\",\"minute\":600},{\"text\":\"Bad\",\"minute\":2000},{\"text\":\"Fri\",\"minute\":540,\"days\":16,\"on\":true},"
        + "{\"text\":\"Off\",\"minute\":1000,\"on\":false}]";
    List<Reminders.Item> items = Reminders.parse(json);
    assertEquals(3, items.size());
    assertEquals("Medicine", items.get(0).text);
    // Wed 18 Nov 2026 10:00: next is Medicine at 18:30 today.
    Object[] n = Reminders.next(items, LocalDateTime.of(2026, 11, 18, 10, 0));
    assertEquals(LocalDateTime.of(2026, 11, 18, 18, 30), n[0]);
    // After 18:30 on Thursday the Friday one comes first.
    n = Reminders.next(items, LocalDateTime.of(2026, 11, 19, 19, 0));
    assertEquals(LocalDateTime.of(2026, 11, 20, 9, 0), n[0]);
    assertEquals("Fri", ((Reminders.Item) n[1]).text);
    assertEquals(items.size(), Reminders.parse(Reminders.toJson(items)).size());
    StringBuilder many = new StringBuilder("[");
    for (int i = 0; i < 20; i++) many.append(i == 0 ? "" : ",").append("{\"text\":\"x\",\"minute\":60}");
    assertEquals(Reminders.MAX, Reminders.parse(many.append("]").toString()).size());
    assertTrue(Reminders.parse("not json").isEmpty());
  }

  @Test public void reminderHomeLineAndSettingsCount() {
    Reminders.save(ctx(), Reminders.parse("[{\"text\":\"Call mum\",\"minute\":1439,\"days\":127}]"));
    String line = Reminders.homeLine(ctx(), LocalDateTime.of(LocalDate.now(), java.time.LocalTime.of(8, 0)));
    assertEquals("Next reminder 23:59: Call mum", line);
    assertEquals("", Reminders.homeLine(ctx(), LocalDateTime.of(LocalDate.now(), java.time.LocalTime.of(23, 59, 30))));
  }

  @Test public void reminderCardShowsTheText() {
    Intent i = new Intent(ctx(), NoticeActivity.class).putExtra("kind", NoticeActivity.REMINDER).putExtra("text", "Take the medicine");
    NoticeActivity a = Robolectric.buildActivity(NoticeActivity.class, i).setup().get();
    assertEquals("Take the medicine", ((TextView) a.findViewById(android.R.id.message)).getText().toString());
  }

  @Test public void sleepCardCountsDown() {
    Intent i = new Intent(ctx(), NoticeActivity.class).putExtra("kind", NoticeActivity.SLEEP);
    NoticeActivity a = Robolectric.buildActivity(NoticeActivity.class, i).setup().get();
    assertEquals("The TV sleeps in 60 seconds", ((TextView) a.findViewById(android.R.id.message)).getText().toString());
  }

  @Test public void weeklyScheduleRoundTripsAndFindsTheNext() {
    Schedule s = Schedule.parse("{\"sleep\":[\"23:30\",\"\",\"\",\"\",\"01:00\",\"\",\"\"],\"wake\":[\"07:00\",\"\",\"\",\"\",\"\",\"\",\"bad\"]}");
    assertEquals(23 * 60 + 30, s.sleep[0]);
    assertEquals(-1, s.sleep[1]);
    assertEquals(-1, s.wake[6]);
    assertFalse(s.empty());
    assertTrue(new Schedule().empty());
    Schedule back = Schedule.parse(s.toJson());
    assertArrayEquals(s.sleep, back.sleep);
    assertArrayEquals(s.wake, back.wake);
    // Wed 18 Nov 2026 12:00: next is Friday 01:00 sleep.
    Object[] n = s.next(LocalDateTime.of(2026, 11, 18, 12, 0));
    assertEquals(LocalDateTime.of(2026, 11, 20, 1, 0), n[0]);
    assertEquals(true, n[1]);
    // Monday 06:00: the wake at 07:00 first.
    n = s.next(LocalDateTime.of(2026, 11, 23, 6, 0));
    assertEquals(LocalDateTime.of(2026, 11, 23, 7, 0), n[0]);
    assertEquals(false, n[1]);
    assertNull(new Schedule().next(LocalDateTime.now()));
  }

  @Test public void arrangeApiSavesRemindersAndSchedule() throws Exception {
    assertTrue(ArrangeServer.applyReminders(ctx(), new JSONObject("{\"reminders\":[{\"text\":\"Water\",\"minute\":600,\"days\":127,\"on\":true}]}")));
    assertFalse(ArrangeServer.applyReminders(ctx(), new JSONObject("{}")));
    assertTrue(ArrangeServer.applySchedule(ctx(), new JSONObject("{\"sleep\":[\"23:00\",\"\",\"\",\"\",\"\",\"\",\"\"],\"wake\":[],\"on\":true}")));
    assertFalse(ArrangeServer.applySchedule(ctx(), new JSONObject("{\"on\":true}")));
    JSONObject p = ArrangeServer.plans(ctx());
    assertEquals("Water", p.getJSONArray("reminders").getJSONObject(0).getString("text"));
    assertEquals("23:00", p.getJSONObject("schedule").getJSONArray("sleep").getString(0));
    assertTrue(p.getBoolean("scheduleOn"));
    assertTrue(Schedule.on(ctx()));
    // Alarms are armed for both.
    assertEquals(2, Shadows.shadowOf((android.app.AlarmManager) ctx().getSystemService(Context.ALARM_SERVICE)).getScheduledAlarms().size());
  }

  @Test public void arrangePageHasThePlansTab() throws Exception {
    String html = new String(ArrangeServer.readAll(ctx().getAssets().open("mi5g_arrange.html")), java.nio.charset.StandardCharsets.UTF_8);
    assertTrue(html.contains("data-tab=\"plans\""));
    assertTrue(html.contains("api/reminders"));
    assertTrue(html.contains("api/schedule"));
    assertTrue(html.contains("result.saved"));
  }

  @Test public void widgetsOrderAndDefaults() {
    assertEquals(HomeWidgets.ALL, HomeWidgets.parseOrder(""));
    assertEquals(Arrays.asList("news", "countdown", "reminder", "sports", "stats", "race"), HomeWidgets.parseOrder("news,bogus,news"));
    assertEquals(Arrays.asList("reminder", "countdown", "sports", "news", "stats", "race"), HomeWidgets.moveUp(HomeWidgets.ALL, "reminder"));
    assertEquals(Arrays.asList("reminder", "sports", "news", "stats", "race", "countdown"), HomeWidgets.moveUp(HomeWidgets.ALL, "countdown"));
    assertFalse(HomeWidgets.shown(ctx(), HomeWidgets.COUNTDOWN));
    assertFalse(HomeWidgets.shown(ctx(), HomeWidgets.SPORTS));
    assertFalse(HomeWidgets.shown(ctx(), HomeWidgets.NEWS));
    assertTrue(HomeWidgets.shown(ctx(), HomeWidgets.REMINDER));
    assertTrue(HomeWidgets.shown(ctx(), HomeWidgets.STATS));
    HomeWidgets.setShown(ctx(), HomeWidgets.COUNTDOWN, true);
    assertTrue(Occasions.countdownsOn(ctx()));   // one switch for both
  }

  @Test public void scoreLinePrefersLiveThenNextThenLast() throws Exception {
    String post = ev("post", "FT", "Al Hilal", "2", "Al Nassr", "1");
    String pre = ev("pre", "Sat 8:00 PM", "Al Ahli", "0", "Al Ittihad", "0");
    String live = ev("in", "67'", "Al Shabab", "0", "Al Fateh", "1");
    assertEquals("● Al Shabab 0 – 1 Al Fateh  ·  67'", HomeWidgets.parseScore("{\"events\":[" + post + "," + pre + "," + live + "]}"));
    assertEquals("Al Ahli – Al Ittihad  ·  Sat 8:00 PM", HomeWidgets.parseScore("{\"events\":[" + post + "," + pre + "]}"));
    assertEquals("Al Hilal 2 – 1 Al Nassr  ·  FT", HomeWidgets.parseScore("{\"events\":[" + post + "]}"));
    assertEquals("", HomeWidgets.parseScore("{}"));
    assertEquals("https://site.api.espn.com/apis/site/v2/sports/soccer/ksa.1/scoreboard", HomeWidgets.scoreUrl("ksa.1"));
  }

  private static String ev(String state, String detail, String home, String hs, String away, String as) {
    return "{\"status\":{\"type\":{\"state\":\"" + state + "\",\"shortDetail\":\"" + detail + "\"}},\"competitions\":[{\"competitors\":["
        + "{\"homeAway\":\"away\",\"score\":\"" + as + "\",\"team\":{\"shortDisplayName\":\"" + away + "\"}},"
        + "{\"homeAway\":\"home\",\"score\":\"" + hs + "\",\"team\":{\"shortDisplayName\":\"" + home + "\"}}]}]}";
  }

  @Test public void headlineFromRss() {
    String rss = "<rss><channel><title>BBC</title><item><title><![CDATA[عنوان &amp; خبر\u202e]]></title></item><item><title>ثانٍ</title></item></channel></rss>";
    assertEquals("عنوان & خبر", HomeWidgets.parseHeadline(rss));
    assertEquals("", HomeWidgets.parseHeadline("<rss></rss>"));
    assertEquals("", HomeWidgets.parseHeadline(null));
  }

  @Test public void widgetLinesFollowOrderAndUseTheCache() throws Exception {
    HomeWidgets.setShown(ctx(), HomeWidgets.SPORTS, true);
    HomeWidgets.setShown(ctx(), HomeWidgets.NEWS, true);
    int[] calls = {0};
    Weather.Fetcher f = (url, ua) -> {
      calls[0]++;
      if (url.contains("espn")) return "{\"events\":[" + ev("post", "FT", "A", "1", "B", "0") + "]}";
      return "<rss><item><title>خبر</title></item></rss>";
    };
    List<String> l = HomeWidgets.lines(ctx(), f, 1_000_000L);
    assertEquals(Arrays.asList("A 1 – 0 B  ·  FT", "BBC Arabic: خبر"), l);
    HomeWidgets.saveOrder(ctx(), HomeWidgets.moveUp(HomeWidgets.order(ctx()), HomeWidgets.NEWS));
    assertEquals(Arrays.asList("BBC Arabic: خبر", "A 1 – 0 B  ·  FT"), HomeWidgets.lines(ctx(), f, 1_060_000L));
    assertEquals(2, calls[0]);   // the second read came from the cache
  }

  @Test public void widgetsScreenTogglesAndMoves() {
    HomeWidgetsActivity a = Robolectric.buildActivity(HomeWidgetsActivity.class).setup().get();
    View countdown = findRow(a.getWindow().getDecorView(), ctx().getString(R.string.mi5g_widget_countdown));
    assertNotNull(countdown);
    countdown.performClick();
    assertTrue(HomeWidgets.shown(ctx(), HomeWidgets.COUNTDOWN));
    View reminder = findRow(a.getWindow().getDecorView(), ctx().getString(R.string.mi5g_widget_reminder));
    reminder.performLongClick();
    assertEquals(HomeWidgets.REMINDER, HomeWidgets.order(ctx()).get(0));
  }

  @Test public void controlCenterListsRecentApps() {
    android.content.pm.PackageInfo pi = new android.content.pm.PackageInfo();
    pi.packageName = "com.example.video";
    pi.applicationInfo = new android.content.pm.ApplicationInfo();
    pi.applicationInfo.packageName = pi.packageName;
    pi.applicationInfo.nonLocalizedLabel = "Video";
    Shadows.shadowOf(ctx().getPackageManager()).installPackage(pi);
    Intent launch = new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER).setPackage(pi.packageName);
    android.content.pm.ResolveInfo ri = new android.content.pm.ResolveInfo();
    ri.activityInfo = new android.content.pm.ActivityInfo();
    ri.activityInfo.packageName = pi.packageName;
    ri.activityInfo.name = pi.packageName + ".Main";
    ri.activityInfo.applicationInfo = pi.applicationInfo;
    Shadows.shadowOf(ctx().getPackageManager()).addResolveInfoForIntent(launch, ri);
    LastOpened.record(ctx(), "com.example.video");
    assertEquals(Collections.singletonList("com.example.video"), LastOpened.recent(ctx(), 8));
    ControlCenterActivity a = Robolectric.buildActivity(ControlCenterActivity.class).setup().get();
    View row = findRow(a.getWindow().getDecorView(), ctx().getString(R.string.mi5g_cc_recent_apps));
    assertNotNull(row);
    row.performClick();
    assertNotNull(findRow(a.getWindow().getDecorView(), "Video"));
    assertNull(findRow(a.getWindow().getDecorView(), ctx().getString(R.string.mi5g_cc_inputs)));
  }

  @Test public void controlCenterInputsPanelSaysWhenThereAreNone() {
    ControlCenterActivity a = Robolectric.buildActivity(ControlCenterActivity.class).setup().get();
    findRow(a.getWindow().getDecorView(), ctx().getString(R.string.mi5g_cc_inputs)).performClick();
    assertNotNull(findRow(a.getWindow().getDecorView(), ctx().getString(R.string.mi5g_cc_no_inputs)));
  }

  @Test public void settingsHaveTheM4Rows() {
    Mi5gSettingsActivity a = Robolectric.buildActivity(Mi5gSettingsActivity.class).setup().get();
    View root = a.getWindow().getDecorView();
    for (int id : new int[]{R.string.mi5g_widgets, R.string.mi5g_saver_info, R.string.mi5g_reminders, R.string.mi5g_schedule}) {
      assertNotNull(ctx().getString(id), findRow(root, ctx().getString(id)));
    }
    findRow(root, ctx().getString(R.string.mi5g_saver_info)).performClick();
    assertTrue(Occasions.saverInfoOn(ctx()));
  }

  @Test public void everyM4StringIsTranslated() throws Exception {
    Resources en = ctx().getResources(), ar = arabic();
    for (java.lang.reflect.Field f : R.string.class.getFields()) {
      String n = f.getName();
      if (!n.startsWith("mi5g_occ_") && !n.startsWith("mi5g_nd_") && !n.startsWith("mi5g_widget") && !n.startsWith("mi5g_league_")
          && !n.startsWith("mi5g_reminder") && !n.startsWith("mi5g_schedule") && !n.startsWith("mi5g_cc_recent") && !n.startsWith("mi5g_cc_no_")
          && !n.equals("mi5g_cc_inputs") && !n.equals("mi5g_saver_info") && !n.equals("mi5g_set_on_phone") && !n.equals("mi5g_national_day")) continue;
      int id = f.getInt(null);
      assertNotEquals(n, en.getString(id), ar.getString(id));
    }
    assertEquals("ينام التلفاز بعد ٣ ثوانٍ", ar.getQuantityString(R.plurals.mi5g_seconds, 3, 3));
  }

  private Resources arabic() {
    Configuration c = new Configuration(ctx().getResources().getConfiguration());
    c.setLocale(new Locale("ar"));
    return ctx().createConfigurationContext(c).getResources();
  }

  private static View findRow(View v, String text) {
    if (v instanceof TextView && text.equals(((TextView) v).getText().toString())) {
      View p = v;
      while (p.getParent() instanceof View && !p.isClickable()) p = (View) p.getParent();
      return p;
    }
    if (v instanceof ViewGroup) {
      ViewGroup g = (ViewGroup) v;
      for (int i = 0; i < g.getChildCount(); i++) { View r = findRow(g.getChildAt(i), text); if (r != null) return r; }
    }
    return null;
  }
}
