package com.midroid.mi5gtv.prayer;

import android.app.Activity;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Live Quran radio: the public reciter streams listed by mp3quran.net's radio API, played as they
 * are. Names and URLs come from the API (cached for offline start); nothing is bundled or generated.
 */
public class RadioActivity extends Activity {
    static final String API = "https://mp3quran.net/api/v3/radios?language=ar";
    private final Handler main = new Handler(Looper.getMainLooper());
    private LinearLayout list;
    private TextView status;
    private MediaPlayer player;
    private Typeface cairo;

    static final class Station { final String name, url; Station(String n, String u) { name = n; url = u; } }

    /** Only https/http stream URLs with a name are kept. Pure, for tests. */
    static List<Station> parse(String json) {
        List<Station> out = new ArrayList<>();
        try {
            JSONArray a = new JSONObject(json).getJSONArray("radios");
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                String n = o.optString("name", "").trim(), u = o.optString("url", "").trim();
                if (!n.isEmpty() && (u.startsWith("https://") || u.startsWith("http://"))) out.add(new Station(n, u));
            }
        } catch (Exception ignored) {
        }
        return out;
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        float dp = getResources().getDisplayMetrics().density;
        cairo = getResources().getFont(R.font.cairo);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundResource(R.drawable.bg_teal);
        root.setPadding((int) (48 * dp), (int) (28 * dp), (int) (48 * dp), (int) (20 * dp));
        TextView title = Ui.text(this, getString(R.string.radio), 28, 0xFFD4AF37, cairo, true);
        title.setGravity(Gravity.START);
        root.addView(title);
        status = Ui.text(this, getString(R.string.loading), 16, 0xCCFFFFFF, cairo, false);
        status.setGravity(Gravity.START);
        root.addView(status);
        ScrollView sv = new ScrollView(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        sv.addView(list);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));
        setContentView(root);
        File cache = new File(getCacheDir(), "radios.json");
        if (cache.isFile()) {
            try { show(parse(new String(Files.readAllBytes(cache.toPath()), StandardCharsets.UTF_8))); } catch (Exception ignored) { }
        }
        new Thread(() -> {
            String json = fetch(API);
            if (json == null) {
                main.post(() -> { if (list.getChildCount() == 0) status.setText(R.string.offline); });
                return;
            }
            try { Files.write(cache.toPath(), json.getBytes(StandardCharsets.UTF_8)); } catch (Exception ignored) { }
            List<Station> st = parse(json);
            main.post(() -> show(st));
        }).start();
    }

    static String fetch(String url) {
        try {
            HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
            c.setConnectTimeout(8000);
            c.setReadTimeout(8000);
            c.setInstanceFollowRedirects(true);
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream o = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int r;
                while ((r = in.read(buf)) > 0 && o.size() < 2_000_000) o.write(buf, 0, r);
                return o.toString("UTF-8");
            }
        } catch (Exception e) {
            return null;
        }
    }

    void show(List<Station> stations) {
        list.removeAllViews();
        status.setText(getString(R.string.stations, stations.size()));
        float dp = getResources().getDisplayMetrics().density;
        for (Station st : stations) {
            TextView row = Ui.text(this, st.name, 18, 0xFFFFFFFF, cairo, false);
            row.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
            row.setPadding((int) (18 * dp), (int) (10 * dp), (int) (18 * dp), (int) (10 * dp));
            row.setFocusable(true);
            row.setClickable(true);
            GradientDrawable focus = new GradientDrawable();
            focus.setColor(0xFFD4AF37);
            focus.setCornerRadius(14 * dp);
            row.setOnFocusChangeListener((v, f) -> {
                v.setBackground(f ? focus : null);
                ((TextView) v).setTextColor(f ? 0xFF0B2B29 : 0xFFFFFFFF);
            });
            row.setOnClickListener(v -> play(st));
            list.addView(row);
        }
        if (list.getChildCount() > 0) list.getChildAt(0).requestFocus();
    }

    private void play(Station st) {
        if (player != null) player.release();
        status.setText(getString(R.string.connecting, st.name));
        player = new MediaPlayer();
        player.setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build());
        try {
            player.setDataSource(st.url);
            player.setOnPreparedListener(p -> { p.start(); status.setText(getString(R.string.playing, st.name)); });
            player.setOnErrorListener((p, w, e) -> { status.setText(R.string.stream_error); return true; });
            player.prepareAsync();
        } catch (Exception e) {
            status.setText(R.string.stream_error);
        }
    }

    @Override
    protected void onDestroy() {
        if (player != null) { player.release(); player = null; }
        super.onDestroy();
    }
}
