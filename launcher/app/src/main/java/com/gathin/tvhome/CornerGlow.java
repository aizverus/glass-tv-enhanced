package com.gathin.tvhome;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;

/**
 * A thin, soft light hugging the frame of the screen, like a panel's edge bleed: the selected app's
 * colour with a touch of the wallpaper's, brightest in the corners and fading towards the middle of
 * each side. Drawn once into a small picture and stretched to the screen, which softens it further. It does not move; Home fades it in on a new app and out after a minute.
 */
final class CornerGlow extends Drawable {
    /** The wallpaper shrunk to a few pixels (set by Home with each picture), for the colour near each edge. */
    static volatile Bitmap wallpaper;
    /** The slow breath Home gives the glow while it is in front: 0.5 to 1 of its strength. */
    static float pulse = 1f;

    private static final int W = 384, H = 216;
    private final Bitmap light;
    private final Paint paint = new Paint(Paint.FILTER_BITMAP_FLAG | Paint.ANTI_ALIAS_FLAG);

    CornerGlow(int color) {
        float[] hsv = new float[3];
        Color.colorToHSV(0xFF000000 | color, hsv);
        // Livelier than the banner's often muted average, never washed out.
        hsv[1] = Math.max(hsv[1], 0.6f);
        hsv[2] = Math.max(hsv[2], 0.95f);
        int icon = Color.HSVToColor(hsv);
        Bitmap wall = wallpaper;
        int[] px = new int[W * H];
        // A thin light hugging the frame, like a panel's own edge bleed: brightest in the corners,
        // thinning towards the middle of each side.
        float aspect = 16f / 9f, k = 0.008f, spread = 0.0075f;
        for (int y = 0; y < H; y++) {
            for (int x = 0; x < W; x++) {
                float nx = (x + 0.5f) / W, ny = (y + 0.5f) / H;
                float ex = Math.min(nx, 1 - nx) * aspect, ey = Math.min(ny, 1 - ny);
                if (Math.min(ex, ey) > 0.12f) continue;
                // Distance to the nearest edge, in screen heights, with a soft minimum so corners round off.
                float dist = Math.max(0f, (float) (-k * Math.log(Math.exp(-ex / k) + Math.exp(-ey / k))));
                // How near a corner, measured along the edge this point lies on.
                float along = ex < ey ? ey : ex / aspect;
                float corner = 0.25f + 0.75f * (float) Math.exp(-along / 0.16f);
                float a = (float) Math.exp(-dist / spread) * corner;
                if (a < 0.004f) continue;
                // A touch of the wallpaper's own colour near that part of the edge.
                int c = wall != null ? mix(icon, lively(sample(wall, nx, ny)), 0.25f) : icon;
                int alpha = Math.round(0xA8 * a);
                px[y * W + x] = Color.argb(alpha, Color.red(c), Color.green(c), Color.blue(c));
            }
        }
        light = Bitmap.createBitmap(px, W, H, Bitmap.Config.ARGB_8888);
    }

    /** The wallpaper's colour at (nx, ny), blended smoothly between its few pixels: no steps. */
    private static int sample(Bitmap wall, float nx, float ny) {
        float fx = Math.max(0f, Math.min(wall.getWidth() - 1f, nx * wall.getWidth() - 0.5f));
        float fy = Math.max(0f, Math.min(wall.getHeight() - 1f, ny * wall.getHeight() - 0.5f));
        int x0 = (int) fx, y0 = (int) fy;
        int x1 = Math.min(wall.getWidth() - 1, x0 + 1), y1 = Math.min(wall.getHeight() - 1, y0 + 1);
        float tx = fx - x0, ty = fy - y0;
        int top = mix(wall.getPixel(x0, y0), wall.getPixel(x1, y0), tx);
        int bottom = mix(wall.getPixel(x0, y1), wall.getPixel(x1, y1), tx);
        return mix(top, bottom, ty);
    }

    /** The wallpaper's colour brightened enough to read as light, not mud. */
    private static int lively(int c) {
        float[] hsv = new float[3];
        Color.colorToHSV(c, hsv);
        hsv[1] = Math.min(1f, Math.max(hsv[1] * 1.3f, 0.4f));
        hsv[2] = Math.max(hsv[2], 0.8f);
        return Color.HSVToColor(hsv);
    }

    private static int mix(int a, int b, float t) {
        return Color.rgb(Math.round(Color.red(a) + (Color.red(b) - Color.red(a)) * t),
                Math.round(Color.green(a) + (Color.green(b) - Color.green(a)) * t),
                Math.round(Color.blue(a) + (Color.blue(b) - Color.blue(a)) * t));
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        Rect b = getBounds();
        if (b.isEmpty()) return;
        int alpha = paint.getAlpha();
        paint.setAlpha(Math.round(alpha * pulse));
        canvas.drawBitmap(light, null, b, paint);
        paint.setAlpha(alpha);
    }

    @Override
    public void setAlpha(int a) {
        paint.setAlpha(a);
        invalidateSelf();
    }

    @Override
    public void setColorFilter(ColorFilter cf) {}

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
