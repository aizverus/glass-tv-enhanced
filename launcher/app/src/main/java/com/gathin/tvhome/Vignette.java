package com.gathin.tvhome;

import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;

/**
 * A faint darkening towards the screen's edges and corners, over the wallpaper and under everything
 * else: an oval the shape of the screen, clear across the middle, so the picture keeps its colours
 * and the clock and tiles sit a little quieter on bright photos.
 */
final class Vignette extends Drawable {
    /** How dark the very corners get (0 to 255). */
    private static final int CORNER = 0x80;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG);

    @Override
    protected void onBoundsChange(@NonNull Rect b) {
        if (b.isEmpty()) return;
        // A circle of radius 1, stretched to an oval reaching the corners.
        RadialGradient g = new RadialGradient(0, 0, 1f,
                new int[]{0x00000000, 0x00000000, CORNER * 2 / 5 << 24, CORNER << 24},
                new float[]{0f, 0.5f, 0.78f, 1f}, Shader.TileMode.CLAMP);
        Matrix m = new Matrix();
        m.setScale(b.width() / 2f * 1.414f, b.height() / 2f * 1.414f);
        m.postTranslate(b.exactCenterX(), b.exactCenterY());
        g.setLocalMatrix(m);
        paint.setShader(g);
    }

    @Override
    public void draw(@NonNull Canvas canvas) {
        canvas.drawRect(getBounds(), paint);
    }

    @Override
    public void setAlpha(int a) {
        paint.setAlpha(a);
    }

    @Override
    public void setColorFilter(ColorFilter cf) {}

    @Override
    public int getOpacity() {
        return PixelFormat.TRANSLUCENT;
    }
}
