package com.gathin.tvhome;

import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.util.List;
import java.util.Random;

/**
 * 2.4 M7 (backlog 37, UrTV idea, own code): family quiz on the TV. The lobby shows a QR for phones; the TV shows
 * each question and the four (or two) answers in colours, the phones answer, then the right answer and the scores.
 */
public final class QuizActivity extends Activity {
    static final int[] COLOURS = {0xFFC0392B, 0xFF2E86C1, 0xFF239B56, 0xFFB7950B};
    static final String[] LETTERS = {"A", "B", "C", "D"};

    private final Handler main = new Handler(Looper.getMainLooper());
    private final FamilyQuiz quiz = new FamilyQuiz(new Random());
    private QuizServer server;
    private LinearLayout body;
    private float dp;
    private int drawnPhase = -1, drawnIndex = -2;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            if (quiz.phase() == FamilyQuiz.QUESTION) {
                long now = System.currentTimeMillis();
                if (quiz.timeUp(now) || quiz.allAnswered()) quiz.reveal();
                render();
            }
            main.postDelayed(this, 500);
        }
    };

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        dp = getResources().getDisplayMetrics().density;
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF0B1F1E);
        root.setPadding((int) (48 * dp), (int) (28 * dp), (int) (48 * dp), (int) (24 * dp));
        root.addView(text(getString(R.string.mi5g_quiz), 28, 0xFFD4AF37, true));
        body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        root.addView(body, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);
        server = QuizServer.start(this, quiz);
        if (server != null) server.onChange = () -> main.post(this::render);
        render();
    }

    FamilyQuiz quiz() { return quiz; }

    /** Screenshot tests have no Wi-Fi address. */
    static String testUrl;

    @Override protected void onResume() { super.onResume(); main.post(tick); }

    @Override protected void onPause() { super.onPause(); main.removeCallbacks(tick); }

    @Override
    protected void onDestroy() {
        main.removeCallbacksAndMessages(null);
        if (server != null) server.stop();
        server = null;
        super.onDestroy();
    }

    /** Redraws for the current phase; within a question only the counters change. */
    void render() {
        int phase = quiz.phase();
        boolean same = phase == drawnPhase && quiz.index() == drawnIndex;
        if (same && phase == FamilyQuiz.QUESTION) { updateCounters(); return; }
        if (same && phase != FamilyQuiz.LOBBY) return;
        drawnPhase = phase;
        drawnIndex = quiz.index();
        body.removeAllViews();
        if (phase == FamilyQuiz.LOBBY) lobby();
        else if (phase == FamilyQuiz.QUESTION || phase == FamilyQuiz.REVEAL) question(phase == FamilyQuiz.REVEAL);
        else done();
    }

    private void lobby() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        String url = testUrl != null ? testUrl : server == null ? null : server.url();
        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.addView(text(getString(url == null ? R.string.mi5g_quiz_no_wifi : R.string.mi5g_quiz_scan), 18, 0xFFFFFFFF, true));
        if (url != null) info.addView(text(url, 13, 0x99FFFFFF, false));
        List<FamilyQuiz.Player> ps = quiz.players();
        StringBuilder names = new StringBuilder();
        for (FamilyQuiz.Player p : ps) names.append(names.length() > 0 ? "  •  " : "").append(p.name);
        info.addView(text(getResources().getQuantityString(R.plurals.mi5g_quiz_players, ps.size(), ps.size())
                + (ps.isEmpty() ? "" : ":  " + names), 17, 0xFFBFE9E3, false));
        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        TextView choice = button(getString(R.string.mi5g_quiz_game_choice), () -> begin(FamilyQuiz.GAME_CHOICE));
        TextView tf = button(getString(R.string.mi5g_quiz_game_true_false), () -> begin(FamilyQuiz.GAME_TRUE_FALSE));
        addInOrder(buttons, false, choice, tf);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-2, -2);
        blp.topMargin = (int) (18 * dp);
        info.addView(buttons, blp);
        info.addView(text(getString(R.string.mi5g_quiz_rules), 13, 0x99FFFFFF, false));
        ImageView qr = new ImageView(this);
        if (url != null) qr.setImageBitmap(ArrangeActivity.qr(url, (int) (220 * dp)));
        int q = (int) (220 * dp);
        LinearLayout.LayoutParams ilp = new LinearLayout.LayoutParams(0, -2, 1f);
        ilp.setMargins((int) (24 * dp), 0, (int) (24 * dp), 0);
        if (Rows.rtl()) { row.addView(qr, new LinearLayout.LayoutParams(q, q)); row.addView(info, ilp); }
        else { row.addView(info, ilp); row.addView(qr, new LinearLayout.LayoutParams(q, q)); }
        body.addView(row, new LinearLayout.LayoutParams(-1, -1));
        choice.requestFocus();
    }

    private void begin(int game) {
        if (quiz.playerCount() == 0) { Toast.makeText(this, R.string.mi5g_quiz_need_players, Toast.LENGTH_LONG).show(); return; }
        quiz.start(game, FamilyQuiz.bank(getResources(), game), System.currentTimeMillis());
        render();
    }

    private TextView counter;

    private void question(boolean reveal) {
        FamilyQuiz.Question q = quiz.current();
        if (q == null) return;
        counter = text("", 15, 0xFFBFE9E3, true);
        body.addView(counter);
        updateCounters();
        TextView qt = text(q.text, 30, 0xFFFFFFFF, true);
        qt.setPadding(0, (int) (10 * dp), 0, (int) (14 * dp));
        body.addView(qt);
        LinearLayout grid = new LinearLayout(this);
        grid.setOrientation(LinearLayout.VERTICAL);
        for (int r = 0; r < q.options.length; r += 2) {
            LinearLayout line = new LinearLayout(this);
            line.setOrientation(LinearLayout.HORIZONTAL);
            View a = option(q, r, reveal), b = r + 1 < q.options.length ? option(q, r + 1, reveal) : new View(this);
            addInOrder(line, true, a, b);
            LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(-1, -2);
            llp.bottomMargin = (int) (10 * dp);
            grid.addView(line, llp);
        }
        body.addView(grid);
        if (reveal) {
            LinearLayout scores = new LinearLayout(this);
            scores.setOrientation(LinearLayout.VERTICAL);
            for (FamilyQuiz.Player p : quiz.standings()) {
                scores.addView(text(p.name + "   " + p.score + (p.gained > 0 ? "  (+" + p.gained + ")" : ""), 17,
                        p.gained > 0 ? 0xFFBFE9E3 : 0x99FFFFFF, p.gained > 0));
            }
            body.addView(scores);
            boolean last = quiz.index() + 1 >= quiz.total();
            TextView next = button(getString(last ? R.string.mi5g_quiz_results : R.string.mi5g_quiz_next), () -> {
                quiz.next(System.currentTimeMillis());
                render();
            });
            LinearLayout wrap = new LinearLayout(this);
            wrap.setGravity(Rows.start());
            wrap.addView(next);
            body.addView(wrap);
            next.requestFocus();
        }
    }

    private void updateCounters() {
        if (counter == null) return;
        String head = getString(R.string.mi5g_quiz_question_of, quiz.index() + 1, quiz.total());
        if (quiz.phase() == FamilyQuiz.QUESTION) {
            int left = Math.max(0, FamilyQuiz.SECONDS - (int) ((System.currentTimeMillis() - quiz.shownAt()) / 1000));
            head += "   •   " + getString(R.string.mi5g_quiz_left, left) + "   •   "
                    + getString(R.string.mi5g_quiz_answered, quiz.answered(), quiz.playerCount());
        }
        counter.setText(head);
    }

    private View option(FamilyQuiz.Question q, int i, boolean reveal) {
        TextView t = text(LETTERS[i] + "   " + q.options[i], 22, 0xFFFFFFFF, true);
        t.setPadding((int) (18 * dp), (int) (16 * dp), (int) (18 * dp), (int) (16 * dp));
        GradientDrawable g = new GradientDrawable();
        g.setCornerRadius(14 * dp);
        boolean right = i == q.answer;
        g.setColor(reveal && !right ? (COLOURS[i] & 0x00FFFFFF) | 0x55000000 : COLOURS[i]);
        if (reveal && right) g.setStroke((int) (4 * dp), 0xFFFFFFFF);
        t.setBackground(g);
        if (reveal && right) t.setText("✓  " + t.getText());
        return t;
    }

    private void done() {
        body.addView(text(getString(R.string.mi5g_quiz_final), 24, 0xFFFFFFFF, true));
        int rank = 0;
        for (FamilyQuiz.Player p : quiz.standings()) {
            rank++;
            String medal = rank == 1 ? "🥇 " : rank == 2 ? "🥈 " : rank == 3 ? "🥉 " : rank + ".  ";
            body.addView(text(medal + p.name + "   " + p.score, rank == 1 ? 26 : 20, rank == 1 ? 0xFFD4AF37 : 0xFFFFFFFF, rank == 1));
        }
        LinearLayout buttons = new LinearLayout(this);
        TextView again = button(getString(R.string.mi5g_quiz_again), () -> { quiz.reset(); render(); });
        TextView close = button(getString(R.string.mi5g_quiz_close), this::finish);
        addInOrder(buttons, false, again, close);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.topMargin = (int) (18 * dp);
        lp.gravity = Rows.start();
        body.addView(buttons, lp);
        again.requestFocus();
    }

    /** Adds start to end in reading order (the launcher lays out LTR, so Arabic is mirrored by hand). */
    private void addInOrder(LinearLayout row, boolean weighted, View... views) {
        for (int i = 0; i < views.length; i++) {
            View v = views[Rows.rtl() ? views.length - 1 - i : i];
            LinearLayout.LayoutParams lp = weighted ? new LinearLayout.LayoutParams(0, -2, 1f) : new LinearLayout.LayoutParams(-2, -2);
            lp.setMargins((int) (5 * dp), 0, (int) (5 * dp), 0);
            row.addView(v, lp);
        }
    }

    private TextView text(String s, float sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        t.setTypeface(getResources().getFont(R.font.cairo), bold ? Typeface.BOLD : Typeface.NORMAL);
        t.setGravity(Rows.start() | Gravity.CENTER_VERTICAL);
        return t;
    }

    private TextView button(String label, Runnable action) {
        TextView b = text(label, 18, 0xFFFFFFFF, true);
        b.setGravity(Gravity.CENTER);
        b.setPadding((int) (22 * dp), (int) (10 * dp), (int) (22 * dp), (int) (10 * dp));
        b.setFocusable(true);
        b.setClickable(true);
        GradientDrawable idle = new GradientDrawable(), focus = new GradientDrawable();
        idle.setCornerRadius(999);
        idle.setColor(0x26FFFFFF);
        focus.setCornerRadius(999);
        focus.setColor(0x55D4AF37);
        focus.setStroke((int) (2 * dp), 0xFFD4AF37);
        b.setBackground(idle);
        b.setOnFocusChangeListener((v, f) -> v.setBackground(f ? focus : idle));
        b.setOnClickListener(v -> { Sounds.click(this); action.run(); });
        return b;
    }
}
