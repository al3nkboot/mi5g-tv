package com.gathin.tvhome;

import java.util.HashSet;
import java.util.Arrays;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.annotation.GraphicsMode;

/** 2.4 M7 review screens in Arabic at TV size: the family quiz, the kids profile and Advanced. */
@RunWith(RobolectricTestRunner.class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = 34, qualifiers = "ar-rSA-ldrtl-w960dp-h540dp-land-television-xhdpi")
public class Mi5g30ScreensTest {
    @After public void tidy() { QuizActivity.testUrl = null; KidsProfile.forgetUnlock(); }

    private QuizActivity quizWithPlayers() {
        QuizActivity.testUrl = "http://192.168.1.20:41234/0123456789abcdef0123456789abcdef/";
        QuizActivity a = Robolectric.buildActivity(QuizActivity.class).setup().get();
        for (String n : new String[]{"سارة", "علي", "منى"}) a.quiz().join(n);
        a.render();
        return a;
    }

    @Test public void quizLobby() throws Exception {
        QuizActivity a = quizWithPlayers();
        Mi5gScreensTest.save(a.getWindow().getDecorView(), "m7-quiz-lobby");
    }

    @Test public void quizQuestionAndReveal() throws Exception {
        QuizActivity a = quizWithPlayers();
        FamilyQuiz q = a.quiz();
        long now = System.currentTimeMillis();
        q.start(FamilyQuiz.GAME_CHOICE, FamilyQuiz.bank(a.getResources(), FamilyQuiz.GAME_CHOICE), now);
        java.util.List<FamilyQuiz.Player> ps = q.players();
        int right = q.current().answer;
        q.answer(ps.get(0).id, 0, right, now + 3000);
        q.answer(ps.get(1).id, 0, (right + 1) % 4, now + 6000);
        a.render();
        Mi5gScreensTest.save(a.getWindow().getDecorView(), "m7-quiz-question");
        q.answer(ps.get(2).id, 0, right, now + 9000);
        q.reveal();
        a.render();
        Mi5gScreensTest.save(a.getWindow().getDecorView(), "m7-quiz-reveal");
    }

    @Test public void quizTrueFalseAndFinal() throws Exception {
        QuizActivity a = quizWithPlayers();
        FamilyQuiz q = a.quiz();
        long now = System.currentTimeMillis();
        q.start(FamilyQuiz.GAME_TRUE_FALSE, FamilyQuiz.bank(a.getResources(), FamilyQuiz.GAME_TRUE_FALSE), now);
        a.render();
        Mi5gScreensTest.save(a.getWindow().getDecorView(), "m7-quiz-true-false");
        java.util.List<FamilyQuiz.Player> ps = q.players();
        for (int i = 0; i < q.total(); i++) {
            int right = q.current().answer;
            q.answer(ps.get(i % 3).id, i, right, now);
            if (i % 2 == 0) q.answer(ps.get((i + 1) % 3).id, i, right, now + 8000);
            q.reveal();
            q.next(now);
        }
        a.render();
        Mi5gScreensTest.save(a.getWindow().getDecorView(), "m7-quiz-final");
    }

    @Test public void kidsAndAdvanced() throws Exception {
        android.content.Context c = RuntimeEnvironment.getApplication();
        KidsProfile.setPin(c, "1357");
        KidsProfile.setAllowed(c, new HashSet<>(Arrays.asList("com.google.android.youtube.tvkids", "com.example.cartoons")));
        KidsActivity k = Robolectric.buildActivity(KidsActivity.class).setup().get();
        Mi5gScreensTest.save(k.getWindow().getDecorView(), "m7-kids-profile");
        AdvancedActivity adv = Robolectric.buildActivity(AdvancedActivity.class).setup().get();
        Mi5gScreensTest.save(adv.getWindow().getDecorView(), "m7-advanced");
    }
}
