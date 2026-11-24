package com.essential.tvlauncher;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

/**
 * Multi-scene procedural 8-bit pixel-art background renderer.
 * Supports:
 *  - Digital Sunset (dithered sky, striped retro sun, mountains, shimmering water)
 *  - Monkey Island Lookout (giant moon, Lookout cliff, gnarled tree, flickering campfire,
 *    anchored pirate ship silhouette, distant Melee village lights, moonlit bay)
 *  - Neo Tokyo 1988 (Mount Fuji silhouette, pixel skyline with glowing windows, bay reflections)
 *  - Forest Fireflies (pine forest silhouettes, crescent moon, drifting fireflies)
 */
public class RetroSunsetView extends View {

    private static final int PIXEL_W = 240;
    private static final int PIXEL_H = 135;
    private static final int HORIZON_Y = 82;

    private static final int[][] BAYER_4X4 = {
            {  0,  8,  2, 10 },
            { 12,  4, 14,  6 },
            {  3, 11,  1,  9 },
            { 15,  7, 13,  5 }
    };

    private final Bitmap frameBuffer;
    private final int[] staticPixels = new int[PIXEL_W * PIXEL_H];
    private final int[] framePixels = new int[PIXEL_W * PIXEL_H];

    private final Paint pixelPaint;
    private final Paint scanlinePaint;
    private final Rect srcRect = new Rect(0, 0, PIXEL_W, PIXEL_H);
    private final Rect dstRect = new Rect();

    private ThemeCatalog.ThemeSpec theme = ThemeCatalog.get(0);
    private boolean animateEnabled = true;
    private boolean scanlinesEnabled = true;
    private int animTick = 0;

    private final int[][] stars = new int[44][3];
    private final int[][] fireflies = new int[18][3];

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable ticker = new Runnable() {
        @Override
        public void run() {
            if (animateEnabled && isAttachedToWindow()) {
                animTick++;
                renderAnimatedFrame();
                invalidate();
                handler.postDelayed(this, 240);
            }
        }
    };

    public RetroSunsetView(Context context) {
        super(context);
        frameBuffer = Bitmap.createBitmap(PIXEL_W, PIXEL_H, Bitmap.Config.ARGB_8888);

        pixelPaint = new Paint();
        pixelPaint.setFilterBitmap(false);
        pixelPaint.setAntiAlias(false);
        pixelPaint.setDither(false);

        scanlinePaint = new Paint();
        scanlinePaint.setColor(0x14000000);
        scanlinePaint.setAntiAlias(false);

        initParticles();
        rebuildStaticScene();
        renderAnimatedFrame();
    }

    private void initParticles() {
        int seed = 1337;
        for (int i = 0; i < stars.length; i++) {
            seed = (seed * 1103515245 + 12345) & 0x7FFFFFFF;
            stars[i][0] = (seed >> 8) % PIXEL_W;
            seed = (seed * 1103515245 + 12345) & 0x7FFFFFFF;
            stars[i][1] = 3 + ((seed >> 8) % 42);
            seed = (seed * 1103515245 + 12345) & 0x7FFFFFFF;
            stars[i][2] = (seed >> 8) % 12;
        }
        for (int i = 0; i < fireflies.length; i++) {
            seed = (seed * 1103515245 + 12345) & 0x7FFFFFFF;
            fireflies[i][0] = 8 + ((seed >> 8) % (PIXEL_W - 16));
            seed = (seed * 1103515245 + 12345) & 0x7FFFFFFF;
            fireflies[i][1] = 65 + ((seed >> 8) % 58);
            seed = (seed * 1103515245 + 12345) & 0x7FFFFFFF;
            fireflies[i][2] = (seed >> 8) % 16;
        }
    }

    public void setTheme(int themeIndex) {
        this.theme = ThemeCatalog.get(themeIndex);
        rebuildStaticScene();
        renderAnimatedFrame();
        invalidate();
    }

    public void setAnimateEnabled(boolean enabled) {
        this.animateEnabled = enabled;
        handler.removeCallbacks(ticker);
        if (enabled && isAttachedToWindow()) {
            handler.postDelayed(ticker, 240);
        }
    }

    public void setScanlinesEnabled(boolean enabled) {
        this.scanlinesEnabled = enabled;
        invalidate();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        if (animateEnabled) {
            handler.removeCallbacks(ticker);
            handler.postDelayed(ticker, 240);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        handler.removeCallbacks(ticker);
        super.onDetachedFromWindow();
    }

    private void putPx(int[] buf, int x, int y, int color) {
        if (x >= 0 && x < PIXEL_W && y >= 0 && y < PIXEL_H) {
            buf[y * PIXEL_W + x] = color;
        }
    }

    private void fillRect(int[] buf, int x0, int y0, int w, int h, int color) {
        for (int y = y0; y < y0 + h; y++) {
            for (int x = x0; x < x0 + w; x++) {
                putPx(buf, x, y, color);
            }
        }
    }

    private void drawDitheredSky(int horizonY) {
        int[] bands = theme.skyBands;
        int numBands = bands.length;
        for (int y = 0; y < horizonY; y++) {
            float pos = ((float) y / horizonY) * (numBands - 1);
            int idx = Math.min(numBands - 2, (int) pos);
            float frac = pos - idx;
            int c1 = bands[idx];
            int c2 = bands[idx + 1];

            for (int x = 0; x < PIXEL_W; x++) {
                int threshold = BAYER_4X4[y & 3][x & 3];
                int color;
                if (frac < 0.25f) {
                    color = c1;
                } else if (frac > 0.75f) {
                    color = c2;
                } else {
                    float ditherFrac = (frac - 0.25f) * 2.0f;
                    color = (ditherFrac * 16f > threshold) ? c2 : c1;
                }
                staticPixels[y * PIXEL_W + x] = color;
            }
        }
    }

    private void drawWaterFloor(int horizonY) {
        for (int y = horizonY; y < PIXEL_H; y++) {
            float depth = (float) (y - horizonY) / (PIXEL_H - horizonY);
            for (int x = 0; x < PIXEL_W; x++) {
                int baseColor = (depth < 0.22f && ((BAYER_4X4[y & 3][x & 3]) > depth * 72f))
                        ? theme.mountainFar
                        : theme.waterBase;
                staticPixels[y * PIXEL_W + x] = baseColor;
            }
        }
    }

    private void drawPixelCloud(int cx, int cy, int halfW, int hi, int lo) {
        for (int y = cy - 3; y <= cy + 2; y++) {
            if (y < 0 || y >= HORIZON_Y) continue;
            int span = halfW - Math.abs(y - cy) * 5;
            if (span <= 0) continue;
            for (int x = cx - span; x <= cx + span; x++) {
                putPx(staticPixels, x, y, (y <= cy) ? hi : lo);
            }
        }
    }

    private void rebuildStaticScene() {
        switch (theme.sceneType) {
            case ThemeCatalog.SCENE_MONKEY_ISLAND:
                buildMonkeyIslandScene();
                break;
            case ThemeCatalog.SCENE_CYBER_CITY:
                buildCyberCityScene();
                break;
            case ThemeCatalog.SCENE_FOREST_NIGHT:
                buildForestNightScene();
                break;
            case ThemeCatalog.SCENE_SUNSET:
            default:
                buildDigitalSunsetScene();
                break;
        }
    }

    // ==================== 1. DIGITAL SUNSET ====================

    private void buildDigitalSunsetScene() {
        drawDitheredSky(HORIZON_Y);

        // Giant striped 8-bit sun placed slightly left-of-center to balance the right-justified app bar!
        int sunCx = 94;
        int sunCy = 55;
        int sunR = 29;
        int[] sunColors = theme.sunColors;

        for (int y = sunCy - sunR; y <= Math.min(HORIZON_Y - 1, sunCy + sunR); y++) {
            if (y < 0) continue;
            int dy = y - sunCy;
            if (dy > 6) {
                int sliceOffset = dy - 6;
                if ((sliceOffset % 4 == 0) || (dy > 16 && sliceOffset % 4 == 1)) continue;
            }
            float sunPos = (float) (y - (sunCy - sunR)) / (sunR * 2f);
            int sIdx = Math.min(sunColors.length - 1, Math.max(0, (int) (sunPos * sunColors.length)));
            int sunColor = sunColors[sIdx];
            for (int x = sunCx - sunR; x <= sunCx + sunR; x++) {
                int dx = x - sunCx;
                if (dx * dx + dy * dy <= sunR * sunR) {
                    putPx(staticPixels, x, y, sunColor);
                }
            }
        }

        int[] bands = theme.skyBands;
        drawPixelCloud(34, 30, 22, bands[bands.length - 1], bands[bands.length - 2]);
        drawPixelCloud(176, 36, 26, bands[bands.length - 1], bands[bands.length - 2]);
        drawPixelCloud(150, 52, 18, bands[bands.length - 1], bands[bands.length - 2]);

        // Far mountains framing the sun
        for (int x = 0; x < PIXEL_W; x++) {
            float dist = Math.abs(x - sunCx) / 140f;
            double h = 4 + Math.sin(x * 0.055) * 6.0 + Math.sin(x * 0.13 + 1.2) * 3.5 + dist * 16.0;
            int peakY = HORIZON_Y - Math.max(2, (int) h);
            for (int y = peakY; y < HORIZON_Y; y++) putPx(staticPixels, x, y, theme.mountainFar);
        }
        // Near ridge
        for (int x = 0; x < PIXEL_W; x++) {
            float dist = Math.abs(x - sunCx) / 140f;
            double h = 1 + Math.sin(x * 0.09 + 2.4) * 3.5 + Math.cos(x * 0.19) * 2.0 + dist * dist * 14.0;
            int peakY = HORIZON_Y - Math.max(1, (int) h);
            for (int y = peakY; y < HORIZON_Y; y++) putPx(staticPixels, x, y, theme.mountainNear);
        }

        drawWaterFloor(HORIZON_Y);
        for (int x = sunCx - 48; x <= sunCx + 48; x++) {
            putPx(staticPixels, x, HORIZON_Y, theme.waterHighlight);
        }
    }

    // ==================== 2. MONKEY ISLAND LOOKOUT ====================

    private void buildMonkeyIslandScene() {
        int horizon = 84;
        drawDitheredSky(horizon);

        // Giant glowing Monkey Island full moon / twilight orb on the left-center sky
        int moonCx = 92;
        int moonCy = 34;
        int moonR = 20;
        for (int y = moonCy - moonR; y <= moonCy + moonR; y++) {
            for (int x = moonCx - moonR; x <= moonCx + moonR; x++) {
                int dx = x - moonCx;
                int dy = y - moonCy;
                int d2 = dx * dx + dy * dy;
                if (d2 <= moonR * moonR) {
                    // Subtle pixel crater dithering
                    boolean crater = ((dx + 5) * (dx + 5) + (dy - 3) * (dy - 3) < 22)
                            || ((dx - 7) * (dx - 7) + (dy + 6) * (dy + 6) < 16);
                    int col = crater ? theme.sunColors[2]
                            : (d2 > (moonR - 3) * (moonR - 3) ? theme.sunColors[1] : theme.sunColors[0]);
                    putPx(staticPixels, x, y, col);
                }
            }
        }

        // Wispy Caribbean night clouds drifting past the moon
        drawPixelCloud(140, 26, 28, theme.skyBands[4], theme.skyBands[2]);
        drawPixelCloud(52, 46, 20, theme.skyBands[4], theme.skyBands[2]);

        // Distant Melee Island volcanic hills & village headland on the far shore
        for (int x = 50; x < PIXEL_W; x++) {
            double h = 3 + Math.sin((x - 50) * 0.045) * 8.0 + Math.cos(x * 0.11) * 3.0;
            if (x > 140 && x < 215) h += 6.0; // Distant Melee Island peak
            int peakY = horizon - Math.max(1, (int) h);
            for (int y = peakY; y < horizon; y++) {
                putPx(staticPixels, x, y, theme.mountainFar);
            }
        }

        // Water bay below horizon
        drawWaterFloor(horizon);

        // Classic Pirate Ship silhouette anchored in the moonlit bay (around x=116, y=horizon+4)
        int shipX = 114;
        int shipY = horizon + 3;
        int shipCol = theme.mountainNear;
        // Hull
        fillRect(staticPixels, shipX - 10, shipY - 2, 20, 3, shipCol);
        fillRect(staticPixels, shipX - 8, shipY + 1, 16, 2, shipCol);
        // Stern castle & bowsprit
        fillRect(staticPixels, shipX + 6, shipY - 5, 5, 4, shipCol);
        for (int i = 0; i < 6; i++) putPx(staticPixels, shipX - 10 - i, shipY - 2 - (i / 2), shipCol);
        // 3 Masts & sails
        fillRect(staticPixels, shipX - 5, shipY - 14, 1, 12, shipCol);
        fillRect(staticPixels, shipX, shipY - 17, 1, 15, shipCol);
        fillRect(staticPixels, shipX + 4, shipY - 12, 1, 10, shipCol);
        // Furled yards / sails
        fillRect(staticPixels, shipX - 8, shipY - 11, 7, 2, shipCol);
        fillRect(staticPixels, shipX - 4, shipY - 14, 9, 2, shipCol);
        fillRect(staticPixels, shipX - 3, shipY - 9, 7, 2, shipCol);
        fillRect(staticPixels, shipX + 2, shipY - 10, 5, 1, shipCol);
        // Tiny warm glowing captain's cabin window!
        putPx(staticPixels, shipX + 8, shipY - 3, 0xFFFFCA6B);
        putPx(staticPixels, shipX + 9, shipY - 3, 0xFFFF9800);

        // ICONIC LOOKOUT POINT CLIFF on the left foreground (x = 0..74)
        for (int x = 0; x < 76; x++) {
            int cliffTop;
            if (x < 46) {
                cliffTop = 76 + (int) (Math.sin(x * 0.15) * 2.0);
            } else {
                // Steep rocky drop down into the bay
                float t = (x - 46) / 30f;
                cliffTop = 76 + (int) (t * t * 58f);
            }
            for (int y = cliffTop; y < PIXEL_H; y++) {
                // Rocky texture highlights near cliff edge
                boolean edge = (y < cliffTop + 2) || (x > 42 && x < 54 && ((x + y) % 5 == 0));
                putPx(staticPixels, x, y, edge ? theme.mountainFar : theme.mountainNear);
            }
        }

        // Gnarled Lookout Tree / Palm silhouette on the cliff (x = 18, y = 76)
        int tx = 18;
        int ty = 76;
        for (int i = 0; i < 24; i++) {
            int lean = (int) (Math.sin(i * 0.14) * 3.5);
            fillRect(staticPixels, tx + lean, ty - i, 3 - (i / 14), 1, theme.mountainNear);
        }
        // Tropical palm / gnarled canopy fronds
        int topX = tx + 2;
        int topY = ty - 24;
        for (int i = -14; i <= 16; i++) {
            int droop = (i * i) / 36;
            fillRect(staticPixels, topX + i, topY + droop, 2, 2, theme.mountainNear);
            fillRect(staticPixels, topX + i / 2, topY - 3 + droop / 2, 2, 2, theme.mountainNear);
        }

        // Campfire log base on Lookout Point (x = 38, y = 76) — flame animated in renderAnimatedFrame!
        fillRect(staticPixels, 36, 75, 6, 2, 0xFF3E2723);

        // Distant Melee village warm dock lights along far shore
        int[] villageLightsX = { 148, 154, 161, 166, 174, 182, 190 };
        for (int lx : villageLightsX) {
            putPx(staticPixels, lx, horizon - 2, 0xFFFFB74D);
            putPx(staticPixels, lx, horizon + 2, 0x88FF9800);
        }
    }

    // ==================== 3. NEO TOKYO 1988 ====================

    private void buildCyberCityScene() {
        drawDitheredSky(HORIZON_Y);

        // Mount Fuji silhouette on the left-center!
        int fujiCx = 78;
        for (int y = 44; y < HORIZON_Y; y++) {
            int dy = y - 44;
            int halfW = 6 + (int) (dy * 1.45f);
            for (int x = fujiCx - halfW; x <= fujiCx + halfW; x++) {
                boolean snowCap = (dy < 11) && (dy + ((x * 7) & 3) < 11);
                putPx(staticPixels, x, y, snowCap ? theme.sunColors[0] : theme.mountainFar);
            }
        }

        // Pixel city skyline silhouettes
        int[] bldgHeights = { 18, 28, 14, 34, 22, 12, 26, 40, 19, 31, 16, 25, 36, 20, 29, 15 };
        int bx = 0;
        for (int i = 0; i < bldgHeights.length && bx < PIXEL_W; i++) {
            int bw = 11 + ((i * 5) % 9);
            int bh = bldgHeights[i];
            int by = HORIZON_Y - bh;
            fillRect(staticPixels, bx, by, bw, bh, theme.mountainNear);

            // Lit pixel windows
            for (int wy = by + 3; wy < HORIZON_Y - 2; wy += 3) {
                for (int wx = bx + 2; wx < bx + bw - 2; wx += 3) {
                    int hash = (wx * 31 + wy * 17 + i * 13) & 15;
                    if (hash < 4) {
                        putPx(staticPixels, wx, wy, (hash == 0) ? 0xFF38BDF8 : 0xFFFFD166);
                    }
                }
            }
            bx += bw + 1;
        }

        drawWaterFloor(HORIZON_Y);
    }

    // ==================== 4. FOREST FIREFLIES ====================

    private void buildForestNightScene() {
        int horizon = 92;
        drawDitheredSky(horizon);

        // Crescent moon in upper-left sky
        int mcx = 64;
        int mcy = 30;
        int mr = 14;
        for (int y = mcy - mr; y <= mcy + mr; y++) {
            for (int x = mcx - mr; x <= mcx + mr; x++) {
                int d1 = (x - mcx) * (x - mcx) + (y - mcy) * (y - mcy);
                int d2 = (x - (mcx + 6)) * (x - (mcx + 6)) + (y - (mcy - 2)) * (y - (mcy - 2));
                if (d1 <= mr * mr && d2 > (mr - 2) * (mr - 2)) {
                    putPx(staticPixels, x, y, theme.sunColors[0]);
                }
            }
        }

        // Layered pine tree silhouettes
        drawPineRange(horizon, 16, theme.mountainFar, 11);
        drawPineRange(horizon + 14, 24, theme.mountainNear, 15);
        fillRect(staticPixels, 0, horizon + 12, PIXEL_W, PIXEL_H - (horizon + 12), theme.waterBase);
    }

    private void drawPineRange(int baseY, int maxH, int color, int spacing) {
        for (int tx = 4; tx < PIXEL_W; tx += spacing) {
            int h = maxH - ((tx * 7) % 10);
            for (int row = 0; row < h; row++) {
                int halfW = (row * 5) / Math.max(1, h);
                if (row % 3 == 0 && halfW > 1) halfW--;
                for (int x = tx - halfW; x <= tx + halfW; x++) {
                    putPx(staticPixels, x, baseY - h + row, color);
                }
            }
            fillRect(staticPixels, tx - 1, baseY, 3, PIXEL_H - baseY, color);
        }
    }

    // ==================== ANIMATION PASS ====================

    private void renderAnimatedFrame() {
        System.arraycopy(staticPixels, 0, framePixels, 0, staticPixels.length);

        // 1. Twinkling 8-bit stars in upper sky (all scenes)
        for (int i = 0; i < stars.length; i++) {
            int sx = stars[i][0];
            int sy = stars[i][1];
            int phase = (stars[i][2] + animTick) % 12;
            if (phase < 8) {
                // Only draw star if pixel is still sky (don't overwrite moon/sun)
                int existing = framePixels[sy * PIXEL_W + sx];
                if (existing == theme.skyBands[0] || existing == theme.skyBands[1]
                        || existing == theme.skyBands[2]) {
                    framePixels[sy * PIXEL_W + sx] = theme.starColor;
                }
            }
        }

        if (theme.sceneType == ThemeCatalog.SCENE_SUNSET) {
            renderWaterShimmer(94, HORIZON_Y, 28);
        } else if (theme.sceneType == ThemeCatalog.SCENE_MONKEY_ISLAND) {
            renderWaterShimmer(92, 84, 22);
            // Animated flickering campfire on Lookout Point (x = 38, y = 74)
            int flameH = 3 + (animTick % 3);
            for (int dy = 0; dy < flameH; dy++) {
                int w = Math.max(1, 3 - dy);
                int ox = ((animTick + dy) & 1) == 0 ? 0 : 1;
                int col = (dy == 0) ? 0xFFFFF08A : (dy == 1 ? 0xFFFFA726 : 0xFFFF5722);
                fillRect(framePixels, 38 - (w / 2) + ox, 74 - dy, w, 1, col);
            }
            // Rising pixel ember spark
            int sparkY = 70 - (animTick % 7);
            int sparkX = 39 + ((animTick & 1) == 0 ? 1 : -1);
            putPx(framePixels, sparkX, sparkY, 0xFFFFCA6B);
        } else if (theme.sceneType == ThemeCatalog.SCENE_CYBER_CITY) {
            // Neon reflections rippling in the dark bay
            for (int y = HORIZON_Y + 2; y < PIXEL_H; y += 3) {
                int shift = (int) Math.round(Math.sin(y * 0.4 + animTick * 0.8) * 2.0);
                for (int x = 10; x < PIXEL_W - 10; x += 18) {
                    int col = ((x + y) % 2 == 0) ? 0xFF38BDF8 : 0xFFFF4D8D;
                    fillRect(framePixels, x + shift, y, 4, 1, col);
                }
            }
        } else if (theme.sceneType == ThemeCatalog.SCENE_FOREST_NIGHT) {
            // Drifting glowing pixel fireflies
            for (int i = 0; i < fireflies.length; i++) {
                int phase = (fireflies[i][2] + animTick) % 16;
                if (phase < 10) {
                    int fx = fireflies[i][0] + (int) Math.round(Math.sin((animTick + i) * 0.5) * 3.0);
                    int fy = fireflies[i][1] + (int) Math.round(Math.cos((animTick + i) * 0.4) * 2.0);
                    putPx(framePixels, fx, fy, (phase < 5) ? 0xFFBEF264 : 0xFF4ADE80);
                }
            }
        }

        frameBuffer.setPixels(framePixels, 0, PIXEL_W, 0, 0, PIXEL_W, PIXEL_H);
    }

    private void renderWaterShimmer(int centerX, int horizonY, int baseHalfW) {
        for (int y = horizonY + 1; y < PIXEL_H; y++) {
            int dy = y - horizonY;
            if (dy % 2 == 1) continue;
            int maxHalfW = Math.max(4, baseHalfW - (int) (dy * 0.42f));
            int shift = (int) Math.round(Math.sin(dy * 0.55 + animTick * 0.7) * 2.2);
            int halfW = Math.max(3, maxHalfW + ((dy + animTick) % 3 == 0 ? 2 : -1));
            int reflColor = (dy < 14) ? theme.sunColors[1]
                    : (dy < 28 ? theme.waterHighlight : theme.sunColors[3]);
            for (int x = centerX - halfW + shift; x <= centerX + halfW + shift; x++) {
                if (x >= 0 && x < PIXEL_W) {
                    // Don't overwrite the Monkey Island foreground cliff!
                    if (theme.sceneType == ThemeCatalog.SCENE_MONKEY_ISLAND && x < 74) continue;
                    if (Math.abs(x - (centerX + shift)) > halfW - 3 && ((x + y + animTick) & 1) == 0) continue;
                    framePixels[y * PIXEL_W + x] = reflColor;
                }
            }
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        int w = getWidth();
        int h = getHeight();
        dstRect.set(0, 0, w, h);
        canvas.drawBitmap(frameBuffer, srcRect, dstRect, pixelPaint);

        if (scanlinesEnabled) {
            for (int y = 0; y < h; y += 6) {
                canvas.drawRect(0, y, w, y + 2, scanlinePaint);
            }
        }
    }
}
