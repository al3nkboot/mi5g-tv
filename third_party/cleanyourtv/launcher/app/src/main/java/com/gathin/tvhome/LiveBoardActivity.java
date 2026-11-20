package com.gathin.tvhome;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * 2.4 M3 (backlog 32, UrTV idea, own code): a message and/or a finger drawing from the phone, shown at once over
 * whatever is on the TV. Opened only by the phone over adb (am start; the activity needs DUMP, which the shell has
 * and apps don't). Strokes use the phone's LiveBoard format: x then y on a 0..999 grid, three digits each, strokes
 * joined with "-". Back closes it; so does "close" from the phone, and ten quiet minutes (no burn-in).
 */
public final class LiveBoardActivity extends Activity {
    static final long AUTO_CLOSE_MS = 10 * 60_000L;
    static final int MAX_TEXT = 120;
    private final Handler main = new Handler(Looper.getMainLooper());
    private Board board;
    private TextView message;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (getIntent().getBooleanExtra("close", false)) { finish(); return; }
        float dp = getResources().getDisplayMetrics().density;
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(0xF20B1F1E);
        board = new Board(this);
        root.addView(board, new FrameLayout.LayoutParams(-1, -1));
        message = new TextView(this);
        message.setTextColor(0xFFFFFFFF);
        message.setTextSize(34f);
        message.setTypeface(getResources().getFont(R.font.cairo), Typeface.BOLD);
        message.setGravity(Gravity.CENTER);
        message.setTextDirection(View.TEXT_DIRECTION_FIRST_STRONG);
        message.setShadowLayer(8 * dp, 0, 0, 0xFF000000);
        int pad = (int) (48 * dp);
        message.setPadding(pad, pad, pad, pad);
        root.addView(message, new FrameLayout.LayoutParams(-1, -2, Gravity.TOP | Gravity.CENTER_HORIZONTAL));
        TextView hint = new TextView(this);
        hint.setText(R.string.mi5g_live_hint);
        hint.setTextColor(0x99FFFFFF);
        hint.setTextSize(13f);
        hint.setTypeface(getResources().getFont(R.font.cairo));
        hint.setPadding(pad, 0, pad, (int) (24 * dp));
        root.addView(hint, new FrameLayout.LayoutParams(-2, -2, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL));
        setContentView(root);
        show(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (intent.getBooleanExtra("close", false)) { finish(); return; }
        if (board != null) show(intent);
    }

    private void show(Intent intent) {
        board.strokes = decode(intent.getStringExtra("strokes"));
        board.invalidate();
        String text = fromHex(intent.getStringExtra("hex"));
        message.setText(text);
        message.setVisibility(text.isEmpty() ? View.GONE : View.VISIBLE);
        main.removeCallbacksAndMessages(null);
        main.postDelayed(this::finish, AUTO_CLOSE_MS);
    }

    @Override
    protected void onDestroy() {
        main.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    /** The strokes of a LiveBoard token on the 0..999 grid; empty for a missing or malformed token. */
    static List<int[]> decode(String token) {
        List<int[]> out = new ArrayList<>();
        if (token == null || token.isEmpty() || token.length() > 4096 || !token.matches("\\d{6}(\\d{6})*(-\\d{6}(\\d{6})*)*")) return out;
        for (String s : token.split("-")) {
            int[] pts = new int[s.length() / 3];
            for (int i = 0; i < pts.length; i++) pts[i] = Integer.parseInt(s.substring(i * 3, i * 3 + 3));
            out.add(pts);
        }
        return out;
    }

    /** UTF-8 text from hex, without control or bidi override characters, at most MAX_TEXT characters. */
    static String fromHex(String hex) {
        if (hex == null || hex.isEmpty() || hex.length() % 2 != 0 || hex.length() > MAX_TEXT * 8 || !hex.matches("[0-9a-fA-F]+")) return "";
        byte[] b = new byte[hex.length() / 2];
        for (int i = 0; i < b.length; i++) b[i] = (byte) Integer.parseInt(hex.substring(i * 2, i * 2 + 2), 16);
        String s = new String(b, StandardCharsets.UTF_8);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (Character.isISOControl(c) || (c >= '\u202A' && c <= '\u202E') || (c >= '\u2066' && c <= '\u2069')) continue;
            sb.append(c);
        }
        String t = sb.toString().trim();
        return t.length() > MAX_TEXT ? t.substring(0, MAX_TEXT) : t;
    }

    /** Draws the strokes in Mi5G gold, scaled to the screen (16:9 like the phone's pad). */
    static final class Board extends View {
        List<int[]> strokes = new ArrayList<>();
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

        Board(Context c) {
            super(c);
            float dp = c.getResources().getDisplayMetrics().density;
            paint.setColor(0xFFD4AF37);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(6 * dp);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float w = getWidth(), h = getHeight();
            for (int[] s : strokes) {
                if (s.length < 2) continue;
                Path p = new Path();
                p.moveTo(s[0] / 999f * w, s[1] / 999f * h);
                for (int i = 2; i + 1 < s.length; i += 2) p.lineTo(s[i] / 999f * w, s[i + 1] / 999f * h);
                if (s.length == 2) p.lineTo(s[0] / 999f * w + 1, s[1] / 999f * h);
                canvas.drawPath(p, paint);
            }
        }
    }
}
