package tv.cleanyour.curtain;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.graphics.Typeface;
import android.view.View;

// The Lantern (design revision 3, app-design/mockups/tv-curtain): scattered tilted cards, a lantern
// that reads each with a laser, a "Restore point" chip, a small black hole that pulls a card in per
// switch-off, the lantern becoming the dock band, "All clean.". Canvas 2D only, in 1080p design
// units scaled to the screen; every paint, shader and path is built once, nothing is allocated per
// frame. Times are seconds from the curtain's start; the activity drives frames at 30 fps and hands
// in each cue as the queue releases it. Reduced motion: no bobbing, breathing or spinning, and every
// change is instant; the stages are the same.
final class CurtainView extends View {
    // ---- design constants (1920 by 1080) ---------------------------------------------------------
    private static final float W = 1920f, H = 1080f;
    private static final int WARM = 0xFFF2B35C;
    private static final float HOME_X = 1380f, HOME_Y = 330f;
    private static final float BAND_X0 = 108f, BAND_X1 = 1812f, BAND_BOTTOM = 980f, BAND_H = 340f, BAND_R = 36f;
    private static final float LINE_Y = 976f;
    private static final float TEXT_X = 150f, TEXT_Y = 926f, PCT_Y = 929f, HINT_Y = 1009f, BRAND_Y = 85f, CLEAN_Y = 584f;
    private static final float CHIP_TOP = 818f, CHIP_BOTTOM = 868f;

    // The cards of the old home screen, in the lantern's scan order (a zigzag): x, y, w, h, tilt.
    private static final float[][] SLOTS = {
        {170, 150, 420, 230, -4}, {690, 110, 260, 150, 3}, {1040, 170, 300, 170, -2}, {1470, 120, 240, 140, 5},
        {1540, 430, 280, 160, -3}, {1190, 440, 220, 130, 2}, {820, 390, 300, 170, -5}, {430, 450, 240, 140, 4},
        {210, 680, 280, 160, -3}, {660, 650, 220, 130, 2},
    };
    // The order the black hole takes them in (the mockup's, group by group).
    private static final int[] ABSORB = {0, 1, 2, 5, 3, 6, 8, 4, 7, 9};

    // Easings (cubic-bezier control points, as the mockup's CSS).
    private static final float[] STD = {.4f, 0f, .2f, 1f};
    private static final float[] EASE = {.25f, .1f, .25f, 1f};
    private static final float[] IN_OUT = {.42f, 0f, .58f, 1f};
    private static final float[] OUT = {0f, 0f, .58f, 1f};
    private static final float[] SUCK = {.6f, 0f, .9f, .5f};
    private static final float[] RISE = {.05f, .7f, .1f, 1f};
    private static final float[] SHEEN = {.3f, 0f, .2f, 1f};

    interface Host {
        void finaleDone();
    }

    private final boolean motion;
    private final Host host;

    // ---- paints and shaders, built once --------------------------------------------------------
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shade = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final RectF rect = new RectF();
    private final Path tick = new Path();
    private final Path bandClip = new Path();
    private final Shader halo, lanternGlow, holeGlow, flashGlow, acc, acc2, silver, bandGlow, dotGlow, sheen;
    private final Shader[] cardFill = new Shader[SLOTS.length];
    private final Shader[] litFill = new Shader[SLOTS.length];
    private final Typeface light, regular;

    // ---- scene state (seconds; NaN: not yet) ------------------------------------------------------
    private int cards;
    private float scanAt = Float.NaN, scanCut = Float.NaN;
    private final float[] readAt = new float[SLOTS.length];
    private final float[] goneAt = new float[SLOTS.length];
    private final float[] goneBase = new float[SLOTS.length];
    private final float[] goneDx = new float[SLOTS.length], goneDy = new float[SLOTS.length];
    private final float[] goneMx = new float[SLOTS.length], goneMy = new float[SLOTS.length];
    private final float[] goneAng = new float[SLOTS.length];
    private int pulled;
    private int lastPerch = -1;
    // The lantern's move: from, to, start, length; its scale the same way.
    private float fromX = HOME_X, fromY = HOME_Y, toX = HOME_X, toY = HOME_Y, moveAt = 0f, moveDur = 0f;
    private float fromSx = 1f, fromSy = 1f, toSx = 1f, toSy = 1f;
    private float holeFrom, holeTo, holeAt = Float.NaN;
    private float flashAt = Float.NaN;
    private float flashed;
    private float fadeAt = Float.NaN;           // the lantern fading into the band
    private float cardsOutAt = Float.NaN;       // cards left when the new home screen comes
    private float chipAt = Float.NaN, chipOutAt = Float.NaN;
    private float bandAt = Float.NaN, glowOnAt = Float.NaN, glowOffAt = Float.NaN, silverAt = Float.NaN;
    private float finaleAt = Float.NaN;
    private boolean finaleReported;
    // The progress furniture.
    private String stageText = "";
    private boolean stageDone;
    private float stageDoneAt = Float.NaN;
    private float fillFrom, fillTo, fillAt;
    private boolean scanning;
    private int pctShown = -1;
    private String pctText = "0%";
    private final String hint, brand, chip, allClean;
    private float chipTextW;

    CurtainView(Context context, boolean motion, Host host, String hint, String brand, String chip, String allClean) {
        super(context);
        this.motion = motion;
        this.host = host;
        this.hint = hint;
        this.brand = brand.toUpperCase(java.util.Locale.ROOT);
        this.chip = chip.toUpperCase(java.util.Locale.ROOT);
        this.allClean = allClean;
        setBackgroundColor(0xFF000000);
        light = context.getResources().getFont(R.font.curtain_outfit_light);
        regular = context.getResources().getFont(R.font.curtain_outfit_regular);
        java.util.Arrays.fill(readAt, Float.NaN);
        java.util.Arrays.fill(goneAt, Float.NaN);
        stroke.setStyle(Paint.Style.STROKE);

        halo = new RadialGradient(0, 0, 460, new int[]{argb(.50f, WARM), argb(.22f, WARM), argb(.07f, WARM), 0, 0}, new float[]{0f, .30f, .54f, .70f, 1f}, Shader.TileMode.CLAMP);
        // Glows of unit radius, scaled when drawn: a disc's soft shadow, solid to the disc's edge.
        lanternGlow = glow(argb(.8f, 0xFFFFDCA0), 13f / 93f);
        holeGlow = glow(argb(.5f, WARM), 52f / 122f);
        flashGlow = glow(argb(.8f, WARM), 52f / 162f);
        dotGlow = glow(argb(.6f, WARM), 9f / 33f);
        acc = new SweepGradient(0, 0, new int[]{0, 0, argb(.95f, 0xFFFFE4B8), 0, argb(.5f, WARM), 0, 0}, new float[]{0f, .30f, .45f, .58f, .78f, .92f, 1f});
        acc2 = new SweepGradient(0, 0, new int[]{0, 0, argb(.55f, WARM), 0, 0}, new float[]{0f, .40f, .55f, .70f, 1f});
        silver = new RadialGradient(0, 0, 1, new int[]{argb(.26f, 0xFFB9C2CC), argb(.08f, 0xFFB9C2CC), 0, 0}, new float[]{0f, .5f, .7f, 1f}, Shader.TileMode.CLAMP);
        bandGlow = new RadialGradient(0, 0, 1, new int[]{argb(.35f, WARM), 0, 0}, new float[]{0f, .7f, 1f}, Shader.TileMode.CLAMP);
        // The sheen: a 105 degree band across an element 40 percent wider and 20 percent taller than
        // the screen on each side, moved from -70 to +70 percent of its width.
        float len = 3456f * .966f + 1512f * .259f, dx = .966f * len / 2f, dy = .259f * len / 2f;
        sheen = new LinearGradient(960f - dx, 540f - dy, 960f + dx, 540f + dy,
            new int[]{0, 0, argb(.10f, 0xFFFFFFFF), argb(.16f, 0xFFFFFFFF), argb(.10f, 0xFFFFFFFF), 0, 0},
            new float[]{0f, .40f, .48f, .50f, .52f, .60f, 1f}, Shader.TileMode.CLAMP);
        for (int i = 0; i < SLOTS.length; i++) {
            float h = SLOTS[i][3];
            cardFill[i] = new LinearGradient(0, -h / 2, 0, h / 2, argb(.08f, 0xFFFFFFFF), argb(.025f, 0xFFFFFFFF), Shader.TileMode.CLAMP);
            litFill[i] = new LinearGradient(0, -h / 2, 0, h / 2, argb(.26f, WARM), argb(.08f, WARM), Shader.TileMode.CLAMP);
        }
        // The tick: an L of the right and bottom borders of a box, turned 42 degrees (unit box of 28).
        tick.moveTo(8.4f, 18.48f);
        tick.lineTo(16.8f, 18.48f);
        tick.lineTo(16.8f, 4.48f);
        android.graphics.Matrix m = new android.graphics.Matrix();
        m.setRotate(42f, 12.6f, 11.48f);
        tick.transform(m);
        bandClip.addRoundRect(new RectF(BAND_X0, BAND_BOTTOM - BAND_H, BAND_X1, BAND_BOTTOM), BAND_R, BAND_R, Path.Direction.CW);
        text.setTypeface(regular);
        text.setTextSize(22f);
        text.setLetterSpacing(.12f);
        chipTextW = text.measureText(this.chip);
        text.setLetterSpacing(0f);
    }

    // ---- cues ------------------------------------------------------------------------------------

    // Applies one cue at time `now`; `words` is its line under the picture, `done` shows the tick.
    void apply(Cue cue, float now, String words, boolean done) {
        if (cards == 0) begin(cue, now);
        if (cue.stage != Cue.Stage.SCAN) cutScan(now);
        switch (cue.stage) {
            case SCAN:
                break;
            case RESTORE_SAVED:
                chipAt = now;
                break;
            case OFF:
                if (Float.isNaN(bandAt) && (Float.isNaN(holeAt) || holeTo == 0f)) hole(now, 1f);
                int target = Progress.pulled(cue.count, cue.total, cards);
                float stagger = 0f;
                while (pulled < target) { pull(now + stagger); stagger += .15f; }
                break;
            case GROUP_DONE:
                break;
            case INSTALL:
                install(now, false);
                break;
            case INSTALLED:
                if (Float.isNaN(bandAt)) install(now, true);
                installed(now);
                break;
            case FINALE:
                if (Float.isNaN(bandAt)) install(now, true);
                if (Float.isNaN(silverAt)) installed(now);
                finaleAt = now;
                chipOutAt = now + 0.9f;
                break;
        }
        scanning = cue.stage == Cue.Stage.SCAN;
        if (!words.equals(stageText) || done != stageDone) {
            stageText = words;
            if (done && !stageDone) stageDoneAt = now;
            stageDone = done;
        }
        float pct = Progress.percent(cue.stage, cue.count, cue.total, 0f);
        if (cue.stage != Cue.Stage.SCAN) setFill(now, pct);
    }

    // The first cue: the cards, and the scan when it is the SCAN (any other first cue: all read).
    private void begin(Cue cue, float now) {
        cards = Progress.cards(cue.stage == Cue.Stage.SCAN ? cue.total : Math.max(cue.total, cue.count));
        scanAt = now;
        if (cue.stage != Cue.Stage.SCAN || !motion) for (int i = 0; i < cards; i++) readAt[i] = now;
        fillFrom = 0f; fillTo = 0f; fillAt = now;
    }

    // A later cue while the scan still runs: every card is read at once and the lantern goes home.
    private void cutScan(float now) {
        if (!Float.isNaN(scanCut) || Float.isNaN(scanAt)) return;
        scanCut = now;
        for (int i = 0; i < cards; i++) if (Float.isNaN(readAt[i]) || readAt[i] > now) readAt[i] = now;
        if (Float.isNaN(holeAt)) move(now, HOME_X, HOME_Y, .5f, 1f, 1f);
    }

    private void hole(float now, float to) {
        holeFrom = holeAmount(now);
        holeTo = to;
        holeAt = now;
    }

    // The next card in the hole's order goes in: out on a short spiral, turned to face the core,
    // stretched along the pull, entering as a streak; the ring flashes as it vanishes.
    private void pull(float at) {
        int n = 0;
        int slot = -1;
        for (int k : ABSORB) {
            if (k >= cards) continue;
            if (n++ == pulled) { slot = k; break; }
        }
        pulled++;
        if (slot < 0) return;
        float[] s = SLOTS[slot];
        float bob = bob(slot, at);
        float cx = s[0] + s[2] / 2f, cy = s[1] + s[3] / 2f + bob;
        float dx = lanternX(at) - cx, dy = lanternY(at) - cy;
        float len = (float) Math.hypot(dx, dy);
        if (len < 1f) len = 1f;
        float side = (slot % 2 == 1 ? 1f : -1f) * 120f;
        goneAt[slot] = at;
        goneBase[slot] = bob;
        goneDx[slot] = dx; goneDy[slot] = dy;
        goneMx[slot] = dx * .38f - dy / len * side;
        goneMy[slot] = dy * .38f + dx / len * side;
        goneAng[slot] = (float) Math.toDegrees(Math.atan2(dy, dx));
        flashAt = at + 1.05f;
    }

    // The hole closes into a lantern, which glides down and flattens into the band; the band rises
    // out of it, warm. `quick`: no install cue came (the launcher was already there), so the band is
    // up at once.
    private void install(float now, boolean quick) {
        if (!Float.isNaN(bandAt)) return;
        hole(now, 0f);
        flashed = 0f;
        cardsOutAt = now;
        float go = quick ? now : now + .5f;
        moveAt(go, 960f, 810f, quick ? .6f : 2.4f, 2.2f, .45f);
        fadeAt = go + (quick ? 0f : 1.6f);
        bandAt = quick ? now : now + 1.8f;
        glowOnAt = bandAt;
    }

    // The band cools and the launcher's silver glow comes in.
    private void installed(float now) {
        if (Float.isNaN(glowOffAt)) glowOffAt = Math.max(now, glowOnAt + .8f);
        silverAt = now;
    }

    private void setFill(float now, float pct) {
        fillFrom = fillNow(now);
        fillTo = pct;
        fillAt = now;
    }

    // ---- frame -----------------------------------------------------------------------------------

    private float now;

    void frame(float t) {
        now = t;
        // The scan: the lantern visits each card in turn, then goes home.
        if (scanning && Float.isNaN(scanCut) && !Float.isNaN(scanAt) && motion) {
            float sc = t - scanAt;
            int perch = (int) Math.floor((sc - Progress.SCAN_LEAD) / Progress.SCAN_PER_CARD);
            if (perch >= cards) perch = cards;
            // Every visit in order, even when a slow frame skipped one.
            for (int q = lastPerch + 1; q <= perch; q++) {
                float at = scanAt + (float) (Progress.SCAN_LEAD + q * Progress.SCAN_PER_CARD);
                if (q < cards) {
                    float[] s = SLOTS[q];
                    float cx = s[0] + s[2] / 2f, cy = s[1] + s[3] / 2f, side = cx > 1250f ? -1f : 1f;
                    float px = clamp(cx + side * (s[2] / 2f + 130f), 150f, 1770f);
                    float py = clamp(cy - s[3] / 2f - 70f, 120f, 820f);
                    move(at, px, py, .5f, 1f, 1f);
                    readAt[q] = at + 1.0f;
                } else {
                    move(at + .1f, HOME_X, HOME_Y, .5f, 1f, 1f);
                }
            }
            if (perch > lastPerch) lastPerch = perch;
            float done = (float) (sc / Progress.scanSeconds(cards));
            float pct = Progress.percent(Cue.Stage.SCAN, 0, 0, done);
            fillFrom = pct; fillTo = pct; fillAt = t;
        }
        if (!Float.isNaN(finaleAt) && !finaleReported && t - finaleAt >= 6f) {
            finaleReported = true;
            host.finaleDone();
        }
        invalidate();
    }

    private void move(float at, float x, float y, float dur, float sx, float sy) {
        moveAt(at, x, y, dur, sx, sy);
    }

    private void moveAt(float at, float x, float y, float dur, float sx, float sy) {
        fromX = lanternX(at); fromY = lanternY(at);
        fromSx = lanternSx(at); fromSy = lanternSy(at);
        toX = x; toY = y; toSx = sx; toSy = sy;
        moveAt = at; moveDur = dur;
    }

    // ---- drawing ---------------------------------------------------------------------------------

    @Override protected void onDraw(Canvas canvas) {
        float t = now;
        float s = Math.min(getWidth() / W, getHeight() / H);
        canvas.save();
        canvas.translate((getWidth() - W * s) / 2f, (getHeight() - H * s) / 2f);
        canvas.scale(s, s);
        canvas.clipRect(0, 0, W, H);

        drawSilver(canvas, t);
        drawCards(canvas, t);
        drawLantern(canvas, t);
        drawBand(canvas, t);
        drawFurniture(canvas, t);
        drawFinale(canvas, t);
        canvas.restore();
    }

    private void drawSilver(Canvas c, float t) {
        float a = tween(t, silverAt, 1.4f, EASE);
        if (a <= 0f) return;
        shade.setShader(silver);
        shade.setAlpha(alpha(a));
        c.save();
        c.translate(400f, 290f);
        c.scale(990f, 778f);
        c.drawCircle(0, 0, 1f, shade);
        c.restore();
        shade.setShader(null);
    }

    private void drawCards(Canvas c, float t) {
        float out = 1f - tween(t, cardsOutAt, .6f, EASE);
        if (out <= 0f) return;
        for (int i = 0; i < cards; i++) {
            float[] s = SLOTS[i];
            float w = s[2], h = s[3];
            float alpha = out;
            c.save();
            if (!Float.isNaN(goneAt[i]) && t >= goneAt[i]) {
                float p = motion ? (t - goneAt[i]) / 1.15f : 1f;
                if (p >= 1f) { c.restore(); continue; }
                // Keyframes 0, 38, 86, 100 percent of the pull, each segment eased like the mockup.
                float tx, ty, rot, sx, sy;
                float r0 = s[4], a = goneAng[i];
                if (p < .38f) {
                    float e = bezier(SUCK, p / .38f);
                    tx = lerp(0, goneMx[i], e); ty = lerp(0, goneMy[i], e); rot = lerp(r0, a + 25f, e); sx = lerp(1f, 1.15f, e); sy = lerp(1f, .85f, e);
                } else if (p < .86f) {
                    float e = bezier(SUCK, (p - .38f) / .48f);
                    tx = lerp(goneMx[i], goneDx[i] * .94f, e); ty = lerp(goneMy[i], goneDy[i] * .94f, e); rot = lerp(a + 25f, a + 150f, e);
                    sx = lerp(1.15f, 1.8f, e); sy = lerp(.85f, .14f, e); alpha *= lerp(1f, .9f, e);
                } else {
                    float e = bezier(SUCK, (p - .86f) / .14f);
                    tx = lerp(goneDx[i] * .94f, goneDx[i], e); ty = lerp(goneDy[i] * .94f, goneDy[i], e); rot = lerp(a + 150f, a + 190f, e);
                    sx = lerp(1.8f, 2f, e); sy = lerp(.14f, .04f, e); alpha *= lerp(.9f, 0f, e);
                }
                c.translate(s[0] + w / 2f + tx, s[1] + h / 2f + goneBase[i] + ty);
                c.rotate(rot);
                c.scale(sx, sy);
            } else {
                c.translate(s[0] + w / 2f, s[1] + h / 2f + bob(i, t));
                c.rotate(s[4]);
            }
            drawCard(c, i, w, h, t, alpha);
            c.restore();
        }
    }

    private void drawCard(Canvas c, int i, float w, float h, float t, float alpha) {
        rect.set(-w / 2f, -h / 2f, w / 2f, h / 2f);
        fill.setShader(cardFill[i]);
        fill.setAlpha(alpha(alpha));
        c.drawRoundRect(rect, 16f, 16f, fill);
        fill.setShader(null);
        boolean read = !Float.isNaN(readAt[i]) && t >= readAt[i];
        stroke.setStrokeWidth(1.5f);
        stroke.setColor(read ? argb(.35f * alpha, WARM) : argb(.15f * alpha, 0xFFFFFFFF));
        c.drawRoundRect(rect, 16f, 16f, stroke);
        // The little bar of a card.
        float bh = .09f * h;
        rect.set(-w / 2f + .10f * w, h / 2f - .16f * h - bh, -w / 2f + .36f * w, h / 2f - .16f * h);
        fill.setColor(argb(.13f * alpha, 0xFFFFFFFF));
        c.drawRoundRect(rect, bh / 2f, bh / 2f, fill);
        // The laser: the card lights amber behind a line going top to bottom in 0.55 s.
        float laserAt = Float.isNaN(readAt[i]) ? Float.NaN : readAt[i] - .55f;
        boolean scanned = Float.isNaN(scanCut) || t < scanCut;
        if (!Float.isNaN(laserAt) && scanned && motion && t >= laserAt && t < readAt[i]) {
            float p = (t - laserAt) / .55f;
            c.save();
            c.clipRect(-w / 2f - 2f, -h / 2f - 2f, w / 2f + 2f, -h / 2f + p * h);
            drawLit(c, i, w, h, alpha);
            c.restore();
            float y = -h / 2f + p * h;
            float x0 = -w / 2f - .03f * w, x1 = w / 2f + .03f * w;
            stroke.setStrokeCap(Paint.Cap.ROUND);
            stroke.setStrokeWidth(30f); stroke.setColor(argb(.14f * alpha, WARM)); c.drawLine(x0, y, x1, y, stroke);
            stroke.setStrokeWidth(12f); stroke.setColor(argb(.35f * alpha, 0xFFFFE4B8)); c.drawLine(x0, y, x1, y, stroke);
            stroke.setStrokeWidth(3f); stroke.setColor(argb(alpha, 0xFFFFF1D8)); c.drawLine(x0, y, x1, y, stroke);
            stroke.setStrokeCap(Paint.Cap.BUTT);
        } else if (read) {
            drawLit(c, i, w, h, .3f * alpha);
            float m = tween(t, readAt[i], .3f, EASE) * alpha;
            fill.setColor(argb(m, WARM));
            c.drawCircle(w / 2f - 18f, -h / 2f + 18f, 6f, fill);
        }
    }

    private void drawLit(Canvas c, int i, float w, float h, float alpha) {
        rect.set(-w / 2f - 1f, -h / 2f - 1f, w / 2f + 1f, h / 2f + 1f);
        fill.setShader(litFill[i]);
        fill.setAlpha(alpha(alpha));
        c.drawRoundRect(rect, 17f, 17f, fill);
        fill.setShader(null);
        stroke.setStrokeWidth(1.5f);
        stroke.setColor(argb(.55f * alpha, WARM));
        c.drawRoundRect(rect, 17f, 17f, stroke);
    }

    private void drawLantern(Canvas c, float t) {
        float op = 1f - tween(t, fadeAt, .8f, EASE);
        if (op <= 0f) return;
        float x = lanternX(t), y = lanternY(t);
        c.save();
        c.translate(x, y);
        c.scale(lanternSx(t), lanternSy(t));
        // The halo, breathing.
        float br = motion ? .5f - .5f * (float) Math.cos(2 * Math.PI * t / 4.0) : 1f;
        c.save();
        float hs = 1f + .05f * br;
        c.scale(hs, hs);
        shade.setShader(halo);
        shade.setAlpha(alpha(op * (.9f + .1f * br)));
        c.drawCircle(0, 0, 460f, shade);
        c.restore();
        float hole = holeAmount(t);
        float flash = flashAmount(t);
        // The glows of the lantern's core and of the hole.
        drawGlow(c, lanternGlow, 93f, op * (1f - hole));
        drawGlow(c, holeGlow, 122f, op * hole * (1f - flash));
        drawGlow(c, flashGlow, 162f, op * hole * flash);
        // The accretion ring: two arcs turning in opposite directions.
        if (hole > 0f) {
            float spin = motion ? t : 0f;
            c.save();
            c.rotate(-90f + 360f * (spin % 1.1f) / 1.1f);
            stroke.setShader(acc);
            stroke.setStrokeWidth(14f);
            stroke.setAlpha(alpha(op * hole));
            c.drawCircle(0, 0, 60.7f, stroke);
            c.restore();
            c.save();
            c.rotate(90f - 360f * (spin % 2.6f) / 2.6f);
            stroke.setShader(acc2);
            stroke.setStrokeWidth(10f);
            stroke.setAlpha(alpha(op * hole * .8f));
            c.drawCircle(0, 0, 89.7f, stroke);
            c.restore();
            stroke.setShader(null);
        }
        // The core: a warm pearl, or the hole's black disc with its rim.
        float r = lerp(13f, 52f, hole);
        int red = (int) lerp(255, 0, hole), green = (int) lerp(228, 0, hole), blue = (int) lerp(184, 0, hole);
        fill.setColor(argb(op * lerp(.85f, 1f, hole), 0xFF000000 | (red << 16) | (green << 8) | blue));
        c.drawCircle(0, 0, r, fill);
        if (hole > 0f) {
            stroke.setStrokeWidth(lerp(2f, 3f, flash));
            stroke.setColor(argb(op * hole * lerp(.6f, .9f, flash), flash > .5f ? 0xFFFFE4B8 : WARM));
            c.drawCircle(0, 0, r + 1f, stroke);
        }
        // The burst as a card vanishes.
        if (!Float.isNaN(flashAt) && t >= flashAt && motion) {
            float p = (t - flashAt) / .5f;
            if (p < 1f) {
                float e = bezier(OUT, p), sc = lerp(.5f, 3.2f, e);
                stroke.setStrokeWidth(3f * sc);
                stroke.setColor(argb(op * lerp(.9f, 0f, e), 0xFFFFE4B8));
                c.drawCircle(0, 0, 68.5f * sc, stroke);
            }
        }
        c.restore();
    }

    private void drawGlow(Canvas c, Shader glow, float radius, float a) {
        if (a <= 0f) return;
        shade.setShader(glow);
        shade.setAlpha(alpha(a));
        c.drawCircle(0, 0, radius, shade);
    }

    private void drawBand(Canvas c, float t) {
        if (Float.isNaN(bandAt) || t < bandAt) return;
        float rise = tween(t, bandAt, .9f, RISE);
        float op = tween(t, bandAt, .3f, EASE);
        float h = BAND_H * rise;
        if (h > 0.5f) {
            rect.set(BAND_X0, BAND_BOTTOM - h, BAND_X1, BAND_BOTTOM);
            float r = Math.min(BAND_R, h / 2f);
            fill.setColor(argb(op, 0xFF131315));
            c.drawRoundRect(rect, r, r, fill);
            stroke.setStrokeWidth(1.5f);
            stroke.setColor(argb(op * .08f * tween(t, bandAt, .9f, EASE), 0xFFFFFFFF));
            c.drawRoundRect(rect, r, r, stroke);
        }
        // The warm glow in the band: on with it, breathing while the install runs, then cooling.
        float on = tween(t, glowOnAt, .8f, EASE);
        float off = Float.isNaN(glowOffAt) ? 0f : tween(t, glowOffAt, 1.6f, EASE);
        float breath = Float.isNaN(glowOffAt) && motion && t > glowOnAt + .8f ? .85f + .15f * (float) Math.cos(2 * Math.PI * (t - glowOnAt - .8f) / 3.0) : 1f;
        float a = on * (1f - off) * breath;
        if (a > 0f) {
            c.save();
            c.clipPath(bandClip);
            c.translate(960f, BAND_BOTTOM);
            c.scale(1205f, 481f);
            shade.setShader(bandGlow);
            shade.setAlpha(alpha(a));
            c.drawCircle(0, 0, 1f, shade);
            c.restore();
            shade.setShader(null);
        }
    }

    private void drawFurniture(Canvas c, float t) {
        // The caption, top left.
        text.setTypeface(regular);
        text.setTextSize(24f);
        text.setLetterSpacing(.22f);
        text.setTextAlign(Paint.Align.LEFT);
        text.setColor(argb(.38f, 0xFFF5F5F7));
        c.drawText(brand, 108f, BRAND_Y, text);
        text.setLetterSpacing(0f);
        // The restore point chip, bottom left.
        float chipIn = tween(t, chipAt, .4f, EASE) * (1f - tween(t, chipOutAt, .4f, EASE));
        if (chipIn > 0f) {
            float dy = 8f * (1f - tween(t, chipAt, .4f, EASE));
            c.save();
            c.translate(0, dy);
            rect.set(108f, CHIP_TOP, 108f + 16f + 22f + 12f + chipTextW + 22f, CHIP_BOTTOM);
            stroke.setStrokeWidth(1.5f);
            stroke.setColor(argb(.45f * chipIn, WARM));
            c.drawRoundRect(rect, 25f, 25f, stroke);
            drawTick(c, 135f, 843f, 22f, chipIn, 2f);
            text.setTypeface(regular);
            text.setTextSize(22f);
            text.setLetterSpacing(.12f);
            text.setColor(argb(.9f * chipIn, WARM));
            c.drawText(chip, 158f, 851f, text);
            text.setLetterSpacing(0f);
            c.restore();
        }
        // The progress: the stage line with its tick, the percent, the bar, the hint.
        float shown = Float.isNaN(finaleAt) ? 1f : 1f - tween(t, finaleAt + .6f, .5f, EASE);
        if (shown <= 0f) return;
        if (stageDone) {
            float k = tween(t, stageDoneAt, .3f, EASE);
            drawTick(c, 122f, 912f, 28f * lerp(.6f, 1f, k), shown * k, 2f);
        }
        text.setTypeface(light);
        text.setTextSize(40f);
        text.setColor(argb(.92f * shown, 0xFFF5F5F7));
        c.drawText(stageText, TEXT_X, TEXT_Y, text);
        float pct = fillNow(t);
        int p = Math.round(Float.isNaN(fillTo) ? 0 : fillTo);
        if (p != pctShown) { pctShown = p; pctText = p + "%"; }
        text.setTextSize(34f);
        text.setTextAlign(Paint.Align.RIGHT);
        text.setColor(argb(.55f * shown, 0xFFF5F5F7));
        c.drawText(pctText, BAND_X1, PCT_Y, text);
        text.setTextAlign(Paint.Align.LEFT);
        rect.set(BAND_X0, LINE_Y, BAND_X1, LINE_Y + 4f);
        fill.setColor(argb(.10f * shown, 0xFFFFFFFF));
        c.drawRoundRect(rect, 2f, 2f, fill);
        float end = BAND_X0 + (BAND_X1 - BAND_X0) * pct / 100f;
        if (end > BAND_X0 + 1f) {
            rect.set(BAND_X0, LINE_Y, end, LINE_Y + 4f);
            fill.setColor(argb(shown, WARM));
            c.drawRoundRect(rect, 2f, 2f, fill);
        }
        float bop = motion ? -2.5f * (1f - (float) Math.cos(2 * Math.PI * t / 1.2)) : 0f;
        c.save();
        c.translate(end - 1f, LINE_Y + 2f + bop);
        c.scale(33f, 33f);
        shade.setShader(dotGlow);
        shade.setAlpha(alpha(shown));
        c.drawCircle(0, 0, 1f, shade);
        shade.setShader(null);
        c.restore();
        fill.setColor(argb(shown, WARM));
        c.drawCircle(end - 1f, LINE_Y + 2f + bop, 9f, fill);
        text.setTextSize(26f);
        text.setColor(argb(.42f * shown, 0xFFF5F5F7));
        c.drawText(hint, BAND_X0, HINT_Y, text);
    }

    private void drawTick(Canvas c, float cx, float cy, float size, float a, float width) {
        if (a <= 0f) return;
        stroke.setStrokeWidth(width);
        stroke.setColor(argb(a, WARM));
        c.drawCircle(cx, cy, size / 2f - width / 2f, stroke);
        c.save();
        c.translate(cx - size / 2f, cy - size / 2f);
        c.scale(size / 28f, size / 28f);
        stroke.setStrokeWidth(2.5f);
        stroke.setStrokeJoin(Paint.Join.MITER);
        c.drawPath(tick, stroke);
        c.restore();
    }

    private void drawFinale(Canvas c, float t) {
        if (Float.isNaN(finaleAt)) return;
        // The sheen crosses the glass.
        float at = finaleAt + .2f;
        if (t >= at && motion) {
            float p = (t - at) / 1.3f;
            if (p < 1f) {
                float tx = lerp(-.7f, .7f, bezier(SHEEN, p)) * 3456f;
                c.save();
                c.translate(tx, 0);
                shade.setShader(sheen);
                shade.setAlpha(255);
                c.drawRect(-768f, -216f, 2688f, 1296f, shade);
                shade.setShader(null);
                c.restore();
            }
        }
        // "All clean." where the launcher's title will be, until the launcher covers the curtain.
        float in = tween(t, finaleAt + .6f, .4f, EASE);
        if (in > 0f) {
            text.setTypeface(light);
            text.setTextSize(84f);
            text.setColor(argb(in, 0xFFF5F5F7));
            c.drawText(allClean, TEXT_X + 18f * (1f - in), CLEAN_Y, text);
        }
        // Nothing covered it in time: a short fade to black, then the curtain finishes itself.
        float black = tween(t, finaleAt + 5.4f, .6f, EASE);
        if (black > 0f) {
            fill.setColor(argb(black, 0xFF000000));
            c.drawRect(0, 0, W, H, fill);
        }
    }

    // ---- the lantern's path ------------------------------------------------------------------------

    private float moveP(float t) {
        if (moveDur <= 0f || !motion) return t >= moveAt ? 1f : 0f;
        if (t <= moveAt) return 0f;
        return bezier(STD, Math.min(1f, (t - moveAt) / moveDur));
    }

    private float lanternX(float t) { return lerp(fromX, toX, moveP(t)); }
    private float lanternY(float t) { return lerp(fromY, toY, moveP(t)); }
    private float lanternSx(float t) { return lerp(fromSx, toSx, moveP(t)); }
    private float lanternSy(float t) { return lerp(fromSy, toSy, moveP(t)); }

    private float holeAmount(float t) {
        if (Float.isNaN(holeAt)) return 0f;
        return lerp(holeFrom, holeTo, tween(t, holeAt, .6f, STD));
    }

    private float flashAmount(float t) {
        if (Float.isNaN(flashAt) || t < flashAt) return flashed;
        return Math.max(flashed, tween(t, flashAt, .6f, STD) * (holeTo > 0f ? 1f : 0f));
    }

    private float fillNow(float t) {
        if (!motion) return fillTo;
        return lerp(fillFrom, fillTo, Math.min(1f, Math.max(0f, (t - fillAt) / .9f)));
    }

    // A card's slow 12 px bob, each with its own period and phase.
    private float bob(int i, float t) {
        if (!motion) return 0f;
        double fd = 4.5 + (i % 4) * 0.7;
        return (float) (-6.0 * (1.0 - Math.cos(2 * Math.PI * (t + i * 0.9) / fd)));
    }

    // ---- helpers -----------------------------------------------------------------------------------

    // 0 before `start`, 1 after `start + dur`, eased between; instant under reduced motion.
    private float tween(float t, float start, float dur, float[] ease) {
        if (Float.isNaN(start) || t < start) return 0f;
        if (!motion || dur <= 0f) return 1f;
        float p = (t - start) / dur;
        return p >= 1f ? 1f : bezier(ease, p);
    }

    // CSS cubic-bezier: solve x(u) = p by Newton then bisection, return y(u). No allocation.
    static float bezier(float[] b, float p) {
        if (p <= 0f) return 0f;
        if (p >= 1f) return 1f;
        float x1 = b[0], y1 = b[1], x2 = b[2], y2 = b[3];
        float u = p;
        for (int i = 0; i < 8; i++) {
            float x = cubic(x1, x2, u) - p;
            float d = dcubic(x1, x2, u);
            if (Math.abs(x) < 1e-4f) break;
            if (Math.abs(d) < 1e-6f) break;
            u -= x / d;
        }
        if (u < 0f || u > 1f || Math.abs(cubic(x1, x2, u) - p) > 1e-3f) {
            float lo = 0f, hi = 1f;
            u = p;
            for (int i = 0; i < 20; i++) {
                float x = cubic(x1, x2, u);
                if (x < p) lo = u; else hi = u;
                u = (lo + hi) / 2f;
            }
        }
        return cubic(y1, y2, u);
    }

    private static float cubic(float a, float b, float u) {
        float v = 1f - u;
        return 3f * v * v * u * a + 3f * v * u * u * b + u * u * u;
    }

    private static float dcubic(float a, float b, float u) {
        float v = 1f - u;
        return 3f * v * v * a + 6f * v * u * (b - a) + 3f * u * u * (1f - b);
    }

    private static Shader glow(int color, float solid) {
        return new RadialGradient(0, 0, 1f, new int[]{color, color, color & 0x00FFFFFF}, new float[]{0f, solid, 1f}, Shader.TileMode.CLAMP);
    }

    private static int argb(float a, int rgb) {
        int alpha = alpha(a * (((rgb >>> 24) & 0xFF) / 255f));
        return (alpha << 24) | (rgb & 0x00FFFFFF);
    }

    private static int alpha(float a) {
        if (a <= 0f) return 0;
        if (a >= 1f) return 255;
        return Math.round(a * 255f);
    }

    private static float lerp(float a, float b, float p) {
        return a + (b - a) * p;
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
