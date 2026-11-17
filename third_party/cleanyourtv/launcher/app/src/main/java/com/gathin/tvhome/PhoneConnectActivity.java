package com.gathin.tvhome;

import android.app.Activity;
import android.graphics.Typeface;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * 2.4 M3 (backlog 15, SUL idea, own code): "Connect a phone". A QR code with this TV's address; the phone's camera
 * opens a small page served by ArrangeServer at /connect (no token, so it cannot change Home), whose button opens
 * the Mi5G TV phone app on mi5gtv://connect?host=..., where the person says yes and allows it on the TV.
 * The server runs only while this screen is open, as for arrange-from-phone.
 */
public final class PhoneConnectActivity extends Activity {
    private TextView status;
    private ImageView qr;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        float dp = getResources().getDisplayMetrics().density;
        Typeface cairo = getResources().getFont(R.font.cairo);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(0xFF0B1F1E);
        root.setLayoutDirection(Rows.rtl() ? View.LAYOUT_DIRECTION_RTL : View.LAYOUT_DIRECTION_LTR);
        int pad = (int) (48 * dp);
        root.setPadding(pad, pad, pad, pad);
        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        TextView title = new TextView(this);
        title.setText(R.string.mi5g_connect_phone);
        title.setTextColor(0xFFD4AF37);
        title.setTextSize(28f);
        title.setTypeface(cairo, Typeface.BOLD);
        title.setGravity(Rows.start());
        text.addView(title);
        TextView body = new TextView(this);
        body.setText(R.string.mi5g_connect_phone_body);
        body.setTextColor(0xE6FFFFFF);
        body.setTextSize(16f);
        body.setTypeface(cairo);
        body.setLineSpacing(0, 1.25f);
        body.setGravity(Rows.start());
        body.setPadding(0, (int) (12 * dp), 0, (int) (12 * dp));
        text.addView(body);
        status = new TextView(this);
        status.setTextColor(0xFF7FE3D6);
        status.setTextSize(14f);
        status.setTypeface(cairo);
        status.setTextDirection(View.TEXT_DIRECTION_LTR);
        text.addView(status);
        Button done = new Button(this);
        done.setText(R.string.mi5g_arrange_stop);
        done.setTypeface(cairo, Typeface.BOLD);
        done.setAllCaps(false);
        done.setOnClickListener(v -> finish());
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-2, -2);
        blp.topMargin = (int) (20 * dp);
        text.addView(done, blp);
        root.addView(text, new LinearLayout.LayoutParams(0, -2, 1f));
        qr = new ImageView(this);
        qr.setId(R.id.mi5g_qr);
        int size = (int) (260 * dp);
        LinearLayout.LayoutParams qlp = new LinearLayout.LayoutParams(size, size);
        qlp.setMarginStart((int) (40 * dp));
        root.addView(qr, qlp);
        setContentView(root);
        done.requestFocus();
    }

    @Override
    protected void onStart() {
        super.onStart();
        ArrangeServer server = ArrangeServer.start(this, () -> { if (!isFinishing()) finish(); });
        String url = server == null ? null : server.connectUrl();
        if (url == null) {
            status.setText(R.string.mi5g_arrange_no_network);
            qr.setImageDrawable(null);
            return;
        }
        status.setText(ArrangeServer.localAddress());
        qr.setImageBitmap(ArrangeActivity.qr(url, 520));
    }

    @Override
    protected void onStop() {
        super.onStop();
        ArrangeServer.stopRunning();
    }

    /** The phone app link for this TV: mi5gtv://connect?host=..&port=5555&name=.. (name trimmed to 40). */
    static String appLink(String ip, String name) {
        String n = name == null ? "" : name.trim();
        if (n.length() > 40) n = n.substring(0, 40);
        try {
            return "mi5gtv://connect?host=" + ip + "&port=5555&name=" + URLEncoder.encode(n, StandardCharsets.UTF_8.name()).replace("+", "%20");
        } catch (java.io.UnsupportedEncodingException e) {
            return "mi5gtv://connect?host=" + ip + "&port=5555";
        }
    }

    /** The page behind the QR: a button that opens the phone app (Chrome's intent link, then the plain link). */
    static byte[] page(String ip, String name) {
        String link = appLink(ip, name);
        String intent = "intent://" + link.substring("mi5gtv://".length()) + "#Intent;scheme=mi5gtv;end";
        String safeName = esc(name == null || name.trim().isEmpty() ? ip : name.trim());
        String html = "<!doctype html><html lang=\"ar\" dir=\"rtl\"><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>Mi5G TV</title>"
                + "<style>body{font-family:sans-serif;background:#0B1F1E;color:#fff;padding:24px;line-height:1.6}"
                + "a.b{display:block;background:#D4AF37;color:#0B1F1E;text-align:center;padding:16px;border-radius:14px;font-weight:bold;text-decoration:none;margin:16px 0}"
                + "p.n{color:#ccc;font-size:14px}</style></head><body>"
                + "<h2>اربط تطبيق الجوال بهذا التلفاز</h2><p>" + safeName + " · <span dir=\"ltr\">" + esc(ip) + "</span></p>"
                + "<a class=\"b\" href=\"" + esc(intent) + "\">افتح تطبيق Mi5G TV</a>"
                + "<p class=\"n\">إن لم يفتح التطبيق: <a href=\"" + esc(link) + "\" style=\"color:#D4AF37\">هذا الرابط</a>، أو أدخل العنوان يدوياً في التطبيق.</p>"
                + "<hr><div dir=\"ltr\"><h3>Connect the phone app to this TV</h3>"
                + "<a class=\"b\" href=\"" + esc(intent) + "\">Open Mi5G TV</a>"
                + "<p class=\"n\">If the app does not open, use <a href=\"" + esc(link) + "\" style=\"color:#D4AF37\">this link</a> or type the address in the app.</p></div>"
                + "</body></html>";
        return html.getBytes(StandardCharsets.UTF_8);
    }

    static String esc(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;").replace("'", "&#39;");
    }

    static String tvName() { return Build.MODEL == null ? "" : Build.MODEL; }
}
