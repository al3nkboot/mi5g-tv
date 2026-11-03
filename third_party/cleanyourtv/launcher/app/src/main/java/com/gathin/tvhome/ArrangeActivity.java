package com.gathin.tvhome;

import android.app.Activity;
import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel;

import java.util.EnumMap;
import java.util.Map;

/**
 * Mi5G 2.0.1: "Arrange Home from the phone". Shows a QR code with the address of ArrangeServer;
 * the server runs only while this screen is open (and stops after 10 idle minutes even then).
 * Opened from Mi5G settings on the TV, or by the phone app over adb (am start, shell only).
 */
public final class ArrangeActivity extends Activity {
    private ArrangeServer server;
    private TextView status;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        float dp = getResources().getDisplayMetrics().density;
        Typeface cairo = getResources().getFont(R.font.cairo);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.HORIZONTAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(0xFF0B1F1E);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        int pad = (int) (48 * dp);
        root.setPadding(pad, pad, pad, pad);

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        text.setGravity(Gravity.START);
        TextView title = new TextView(this);
        title.setText(R.string.mi5g_arrange_title);
        title.setTextColor(0xFFD4AF37);
        title.setTextSize(28f);
        title.setTypeface(cairo, Typeface.BOLD);
        text.addView(title);
        TextView body = new TextView(this);
        body.setText(R.string.mi5g_arrange_body);
        body.setTextColor(0xE6FFFFFF);
        body.setTextSize(16f);
        body.setTypeface(cairo);
        body.setLineSpacing(0, 1.25f);
        body.setPadding(0, (int) (12 * dp), 0, (int) (12 * dp));
        text.addView(body);
        status = new TextView(this);
        status.setTextColor(0xFF7FE3D6);
        status.setTextSize(14f);
        status.setTypeface(cairo);
        status.setTextDirection(View.TEXT_DIRECTION_LTR);
        text.addView(status);
        Button stop = new Button(this);
        stop.setText(R.string.mi5g_arrange_stop);
        stop.setTypeface(cairo, Typeface.BOLD);
        stop.setAllCaps(false);
        stop.setOnClickListener(v -> finish());
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(-2, -2);
        blp.topMargin = (int) (20 * dp);
        text.addView(stop, blp);
        root.addView(text, new LinearLayout.LayoutParams(0, -2, 1f));

        ImageView qr = new ImageView(this);
        qr.setId(R.id.mi5g_qr);
        int size = (int) (260 * dp);
        LinearLayout.LayoutParams qlp = new LinearLayout.LayoutParams(size, size);
        qlp.setMarginStart((int) (40 * dp));
        root.addView(qr, qlp);
        setContentView(root);
        stop.requestFocus();
    }

    @Override
    protected void onStart() {
        super.onStart();
        server = ArrangeServer.start(this, () -> { if (!isFinishing()) finish(); });
        String url = server == null ? null : server.url();
        ImageView qr = findViewById(R.id.mi5g_qr);
        if (url == null) {
            status.setText(R.string.mi5g_arrange_no_network);
            qr.setImageDrawable(null);
            return;
        }
        status.setText(url);
        qr.setImageBitmap(qr(url, 520));
    }

    @Override
    protected void onStop() {
        super.onStop();
        // Off when not in use: leaving this screen stops the server.
        ArrangeServer.stopRunning();
        server = null;
    }

    /** A black-on-white QR code of the text, with a quiet zone, size x size pixels. */
    static Bitmap qr(String text, int size) {
        Map<EncodeHintType, Object> hints = new EnumMap<>(EncodeHintType.class);
        hints.put(EncodeHintType.ERROR_CORRECTION, ErrorCorrectionLevel.M);
        hints.put(EncodeHintType.MARGIN, 2);
        try {
            BitMatrix m = new QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints);
            int[] px = new int[size * size];
            for (int y = 0; y < size; y++) for (int x = 0; x < size; x++) px[y * size + x] = m.get(x, y) ? 0xFF000000 : 0xFFFFFFFF;
            return Bitmap.createBitmap(px, size, size, Bitmap.Config.ARGB_8888);
        } catch (WriterException e) {
            return null;
        }
    }
}
