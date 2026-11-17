package com.gathin.tvhome;

import static org.junit.Assert.*;

import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import org.json.JSONObject;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

/** 2.4 M7: family quiz, kids profile, time-of-day order, Advanced settings. */
@RunWith(RobolectricTestRunner.class) @Config(sdk = 34)
public class Mi5g30Test {
  private Context ctx() { return RuntimeEnvironment.getApplication(); }

  @After public void tidy() {
    KidsProfile.forgetUnlock();
    AllAppsActivity.testApps = null;
  }

  // ---- time of day ----

  @Test public void slotsCoverTheDay() {
    assertEquals(TimeOfDay.NIGHT, TimeOfDay.slot(4));
    assertEquals(TimeOfDay.MORNING, TimeOfDay.slot(5));
    assertEquals(TimeOfDay.MORNING, TimeOfDay.slot(11));
    assertEquals(TimeOfDay.AFTERNOON, TimeOfDay.slot(12));
    assertEquals(TimeOfDay.EVENING, TimeOfDay.slot(17));
    assertEquals(TimeOfDay.EVENING, TimeOfDay.slot(21));
    assertEquals(TimeOfDay.NIGHT, TimeOfDay.slot(22));
    assertEquals(TimeOfDay.NIGHT, TimeOfDay.slot(0));
  }

  @Test public void usageSortsBySlotAndOldOpensFade() {
    for (int i = 0; i < 3; i++) TimeOfDay.record(ctx(), "com.news", TimeOfDay.MORNING);
    TimeOfDay.record(ctx(), "com.movies", TimeOfDay.EVENING);
    TimeOfDay.record(ctx(), "com.kids", TimeOfDay.MORNING);
    List<String> apps = new ArrayList<>(Arrays.asList("com.kids", "com.movies", "com.news"));
    TimeOfDay.sort(ctx(), apps, TimeOfDay.MORNING);
    assertEquals(Arrays.asList("com.news", "com.kids", "com.movies"), apps);
    TimeOfDay.sort(ctx(), apps, TimeOfDay.EVENING);
    assertEquals("com.movies", apps.get(0));
    int before = TimeOfDay.score(ctx(), "com.news", TimeOfDay.MORNING);
    TimeOfDay.record(ctx(), "com.kids", TimeOfDay.MORNING);
    assertTrue("other apps in the slot fade", TimeOfDay.score(ctx(), "com.news", TimeOfDay.MORNING) < before);
    assertEquals(10, TimeOfDay.score(ctx(), "com.movies", TimeOfDay.EVENING));
    TimeOfDay.record(ctx(), ctx().getPackageName(), TimeOfDay.MORNING);
    assertEquals(0, TimeOfDay.score(ctx(), ctx().getPackageName(), TimeOfDay.MORNING));
    TimeOfDay.clear(ctx());
    assertEquals(0, TimeOfDay.score(ctx(), "com.news", TimeOfDay.MORNING));
  }

  @Test public void openingFromHomeFeedsTheCounts() {
    LastOpened.record(ctx(), "com.example.tv");
    assertEquals(10, TimeOfDay.score(ctx(), "com.example.tv", TimeOfDay.slotNow()));
  }

  @Test public void allAppsPutsThisTimesAppsFirstWhenOn() {
    AllAppsActivity.testApps = Arrays.asList(new String[]{"a.alpha", "Alpha"}, new String[]{"b.beta", "Beta"}, new String[]{"c.gamma", "Gamma"});
    TimeOfDay.record(ctx(), "c.gamma", TimeOfDay.slotNow());
    TimeOfDay.setOn(ctx(), true);
    AllAppsActivity a = Robolectric.buildActivity(AllAppsActivity.class).setup().get();
    assertEquals(3, a.shownCount());
    assertEquals("c.gamma", firstApp(a));
  }

  private static String firstApp(AllAppsActivity a) {
    try {
      java.lang.reflect.Field f = AllAppsActivity.class.getDeclaredField("shown");
      f.setAccessible(true);
      Object app = ((List<?>) f.get(a)).get(0);
      java.lang.reflect.Field pkg = app.getClass().getDeclaredField("pkg");
      pkg.setAccessible(true);
      return (String) pkg.get(app);
    } catch (Exception e) { throw new AssertionError(e); }
  }

  // ---- kids profile ----

  @Test public void pinIsHashedCheckedAndLocksAfterFiveTries() {
    assertFalse(KidsProfile.setPin(ctx(), "12a4"));
    assertFalse(KidsProfile.setPin(ctx(), "123"));
    assertTrue(KidsProfile.setPin(ctx(), "2468"));
    String stored = LauncherSwitches.prefs(ctx()).getString(KidsProfile.HASH, "");
    assertEquals(64, stored.length());
    assertFalse(stored.contains("2468"));
    long now = 1_000_000L;
    assertTrue(KidsProfile.check(ctx(), "2468", now));
    KidsProfile.forgetUnlock();
    for (int i = 0; i < 4; i++) assertFalse(KidsProfile.check(ctx(), "0000", now));
    assertEquals(0, KidsProfile.lockedFor(ctx(), now));
    assertFalse(KidsProfile.check(ctx(), "0000", now));
    assertTrue(KidsProfile.lockedFor(ctx(), now) > 0);
    assertFalse("locked: even the right PIN waits", KidsProfile.check(ctx(), "2468", now + 1000));
    assertTrue(KidsProfile.check(ctx(), "2468", now + KidsProfile.LOCKOUT_MS + 1));
  }

  @Test public void kidsModeNeedsAPinAndShowsOnlyAllowedApps() {
    KidsProfile.setOn(ctx(), true);
    assertFalse("no PIN, no kids mode", KidsProfile.on(ctx()));
    KidsProfile.setPin(ctx(), "1357");
    KidsProfile.setAllowed(ctx(), new HashSet<>(Collections.singletonList("b.cartoons")));
    KidsProfile.setOn(ctx(), true);
    assertTrue(KidsProfile.on(ctx()));
    assertTrue(KidsProfile.blocks(ctx(), "a.news"));
    assertFalse(KidsProfile.blocks(ctx(), "b.cartoons"));
    assertTrue("Home edits locked too", LauncherSwitches.homeLocked(ctx()));
    AllAppsActivity.testApps = Arrays.asList(new String[]{"a.news", "News"}, new String[]{"b.cartoons", "Cartoons"});
    AllAppsActivity a = Robolectric.buildActivity(AllAppsActivity.class).setup().get();
    assertEquals(1, a.shownCount());
    KidsProfile.setOn(ctx(), false);
    assertFalse(KidsProfile.blocks(ctx(), "a.news"));
    assertFalse(LauncherSwitches.homeLocked(ctx()));
  }

  @Test public void settingsAskForThePinInKidsMode() {
    KidsProfile.setPin(ctx(), "1357");
    KidsProfile.setOn(ctx(), true);
    Mi5gSettingsActivity a = Robolectric.buildActivity(Mi5gSettingsActivity.class).setup().get();
    android.app.Dialog d = org.robolectric.shadows.ShadowDialog.getLatestDialog();
    assertNotNull("PIN dialog shown", d);
    assertTrue(d.isShowing());
    ((android.app.AlertDialog) d).getButton(android.app.AlertDialog.BUTTON_NEGATIVE).performClick();
    org.robolectric.Shadows.shadowOf(android.os.Looper.getMainLooper()).idle();
    assertTrue("Back/cancel closes settings", a.isFinishing());
    KidsProfile.check(ctx(), "1357", System.currentTimeMillis());
    Mi5gSettingsActivity b = Robolectric.buildActivity(Mi5gSettingsActivity.class).setup().get();
    assertFalse("unlocked a moment ago: no second prompt", b.isFinishing());
  }

  // ---- family quiz ----

  @Test public void questionLinesParse() {
    FamilyQuiz.Question q = FamilyQuiz.parse("Q?|a|b|c|d|2", "T", "F");
    assertEquals(4, q.options.length);
    assertEquals(2, q.answer);
    FamilyQuiz.Question tf = FamilyQuiz.parse("Sky is blue.|1", "T", "F");
    assertArrayEquals(new String[]{"T", "F"}, tf.options);
    assertEquals(0, tf.answer);
    assertEquals(1, FamilyQuiz.parse("No.|0", "T", "F").answer);
    assertNull(FamilyQuiz.parse("Q?|a|b|c|d|4", "T", "F"));
    assertNull(FamilyQuiz.parse("Q?|a|b", "T", "F"));
    assertNull(FamilyQuiz.parse("|1", "T", "F"));
  }

  @Test public void bothLanguagesHaveTheSameFullBanks() {
    Resources en = ctx().getResources();
    Configuration c = new Configuration(en.getConfiguration());
    c.setLocale(new Locale("ar"));
    Resources ar = ctx().createConfigurationContext(c).getResources();
    for (int g : new int[]{FamilyQuiz.GAME_CHOICE, FamilyQuiz.GAME_TRUE_FALSE}) {
      List<FamilyQuiz.Question> e = FamilyQuiz.bank(en, g), a = FamilyQuiz.bank(ar, g);
      assertTrue(e.size() >= FamilyQuiz.ROUND);
      assertEquals(en.getStringArray(g == 0 ? R.array.mi5g_quiz_choice : R.array.mi5g_quiz_true_false).length, e.size());
      assertEquals(e.size(), a.size());
      for (int i = 0; i < e.size(); i++) assertEquals("same answer at " + i, e.get(i).answer, a.get(i).answer);
      assertNotEquals(e.get(0).text, a.get(0).text);
    }
  }

  @Test public void namesAreCleanedAndUnique() {
    assertEquals("Sara", FamilyQuiz.cleanName("  <Sara>  "));
    assertEquals("a b", FamilyQuiz.cleanName("a \n  b"));
    assertEquals(FamilyQuiz.MAX_NAME, FamilyQuiz.cleanName("abcdefghijklmnopqrstuvwxyz").length());
    assertEquals("", FamilyQuiz.cleanName("\u202e"));
    FamilyQuiz q = new FamilyQuiz(new Random(1));
    assertNotNull(q.join("Sara"));
    assertNull(q.join("sara"));
    assertNull(q.join("   "));
    for (int i = 1; i < FamilyQuiz.MAX_PLAYERS; i++) assertNotNull(q.join("P" + i));
    assertNull("room full", q.join("Late"));
  }

  @Test public void aRoundScoresRightAndFastAnswers() throws Exception {
    FamilyQuiz q = new FamilyQuiz(new Random(7));
    String sara = q.join("Sara"), ali = q.join("Ali"), mona = q.join("Mona");
    List<FamilyQuiz.Question> bank = FamilyQuiz.bank(ctx().getResources(), FamilyQuiz.GAME_CHOICE);
    long t = 1_000_000L;
    assertFalse("needs players and questions", new FamilyQuiz(new Random()).start(0, bank, t));
    assertTrue(q.start(FamilyQuiz.GAME_CHOICE, bank, t));
    assertEquals(FamilyQuiz.ROUND, q.total());
    assertEquals(FamilyQuiz.QUESTION, q.phase());
    int right = q.current().answer, wrong = (right + 1) % 4;
    assertTrue(q.answer(sara, 0, right, t));
    assertFalse("one answer each", q.answer(sara, 0, wrong, t + 10));
    assertFalse("old question", q.answer(ali, 1, right, t));
    assertFalse("unknown player", q.answer("nobody", 0, right, t));
    assertTrue(q.answer(ali, 0, right, t + 10_000));
    assertFalse("too late", q.answer(mona, 0, wrong, t + 21_000));
    assertFalse(q.allAnswered());
    assertTrue(q.timeUp(t + 20_000));
    q.reveal();
    assertEquals(FamilyQuiz.REVEAL, q.phase());
    assertEquals(150, q.standings().get(0).score);
    assertEquals("Sara", q.standings().get(0).name);
    assertEquals(125, q.standings().get(1).score);
    assertEquals(0, q.standings().get(2).score);
    JSONObject s = q.state(ali);
    assertEquals(right, s.getInt("answer"));
    assertEquals(2, s.getInt("rank"));
    assertEquals(125, s.getInt("gained"));
    assertFalse(q.state("x").getBoolean("joined"));
    for (int i = 1; i < FamilyQuiz.ROUND; i++) { assertTrue(q.next(t)); q.reveal(); }
    assertFalse(q.next(t));
    assertEquals(FamilyQuiz.DONE, q.phase());
    assertNull("no joining a finished game", q.join("Late"));
    q.reset();
    assertEquals(FamilyQuiz.LOBBY, q.phase());
    assertEquals(0, q.standings().get(0).score);
  }

  @Test public void pointsForSpeed() {
    assertEquals(0, FamilyQuiz.points(false, 0));
    assertEquals(150, FamilyQuiz.points(true, 0));
    assertEquals(125, FamilyQuiz.points(true, 10_000));
    assertEquals(100, FamilyQuiz.points(true, 30_000));
  }

  @Test public void serverNeedsTheTokenAndPlaysARound() throws Exception {
    FamilyQuiz quiz = new FamilyQuiz(new Random(3));
    try (ServerSocket ss = new ServerSocket(0)) {
      QuizServer s = new QuizServer(ctx(), quiz, "0123456789abcdef0123456789abcdef", ss);
      int[] changes = {0};
      s.onChange = () -> changes[0]++;
      assertEquals(404, s.route("GET", "/wrong/", new HashMap<>(), "").code);
      assertEquals(404, s.route("GET", "/", new HashMap<>(), "").code);
      QuizServer.Reply page = s.route("GET", "/" + s.token + "/", new HashMap<>(), "");
      assertEquals(200, page.code);
      String html = new String(page.body, "UTF-8");
      assertTrue(html.contains(ctx().getString(R.string.mi5g_quiz_join)));
      assertFalse("no outside scripts", html.contains("src="));
      JSONObject j = new JSONObject(new String(s.route("POST", "/" + s.token + "/join", new HashMap<>(), "{\"name\":\"Sara\"}").body, "UTF-8"));
      assertTrue(j.getBoolean("ok"));
      String id = j.getString("id");
      assertEquals(400, s.route("POST", "/" + s.token + "/join", new HashMap<>(), "not json").code);
      quiz.start(FamilyQuiz.GAME_TRUE_FALSE, FamilyQuiz.bank(ctx().getResources(), FamilyQuiz.GAME_TRUE_FALSE), System.currentTimeMillis());
      HashMap<String, String> query = new HashMap<>();
      query.put("id", id);
      JSONObject st = new JSONObject(new String(s.route("GET", "/" + s.token + "/state", query, "").body, "UTF-8"));
      assertEquals(FamilyQuiz.QUESTION, st.getInt("phase"));
      assertEquals(2, st.getJSONArray("options").length());
      assertFalse("the answer stays secret while the question is open", st.has("answer"));
      JSONObject ans = new JSONObject(new String(s.route("POST", "/" + s.token + "/answer", new HashMap<>(),
              "{\"id\":\"" + id + "\",\"q\":0,\"c\":1}").body, "UTF-8"));
      assertTrue(ans.getBoolean("ok"));
      assertEquals(2, changes[0]);
      assertTrue(quiz.allAnswered());
      s.stop();
    }
  }

  // ---- advanced ----

  @Test public void accessibilityListHelpers() {
    String pkg = "com.gathin.tvhome";
    String list = "com.other/.A:com.gathin.tvhome/.HomeKeyService:com.x/.B";
    assertTrue(AdvancedActivity.listed(list, pkg));
    assertFalse(AdvancedActivity.listed("com.gathin.tvhomeX/.A", pkg));
    assertFalse(AdvancedActivity.listed(null, pkg));
    assertEquals("com.other/.A:com.x/.B", AdvancedActivity.without(list, pkg));
    assertEquals("", AdvancedActivity.without(null, pkg));
  }

  @Test public void beforeUninstallTurnsHelpersOff() {
    LauncherSwitches.setKeepAlive(ctx(), true);
    KidsProfile.setPin(ctx(), "1111");
    KidsProfile.setOn(ctx(), true);
    LauncherSwitches.prefs(ctx()).edit().putBoolean(FastHomeService.KEY, true).apply();
    assertEquals(3, AdvancedActivity.prepareUninstall(ctx()));
    assertFalse(LauncherSwitches.keepAlive(ctx()));
    assertFalse(KidsProfile.on(ctx()));
    assertFalse(FastHomeService.enabled(ctx()));
    assertEquals(0, AdvancedActivity.prepareUninstall(ctx()));
  }

  @Test public void advancedPageListsTheChecks() {
    AdvancedActivity a = Robolectric.buildActivity(AdvancedActivity.class).setup().get();
    List<AdvancedActivity.Check> checks = AdvancedActivity.checks(ctx());
    assertEquals(6, checks.size());
    assertNotNull(findText(a.getWindow().getDecorView(), ctx().getString(R.string.mi5g_adv_uninstall)));
  }

  static android.view.View findText(android.view.View v, String text) {
    if (v instanceof android.widget.TextView && text.equals(((android.widget.TextView) v).getText().toString())) return v;
    if (v instanceof android.view.ViewGroup) {
      android.view.ViewGroup g = (android.view.ViewGroup) v;
      for (int i = 0; i < g.getChildCount(); i++) { android.view.View r = findText(g.getChildAt(i), text); if (r != null) return r; }
    }
    return null;
  }
}
