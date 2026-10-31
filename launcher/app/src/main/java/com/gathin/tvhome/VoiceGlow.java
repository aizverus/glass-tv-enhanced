package com.gathin.tvhome;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.media.AudioManager;
import android.media.AudioRecordingConfiguration;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;

import java.util.List;

/**
 * A soft glow round the screen's edges while something listens through the microphone (the remote's
 * voice search), in the spirit of Siri's: blue, violet and pink, slowly turning, kept dim and thin.
 * It is drawn in an overlay window, so it shows over the voice screen too; that needs the
 * "display over other apps" permission, granted once over ADB (appops SYSTEM_ALERT_WINDOW).
 */
final class VoiceGlow {
    private static final String TAG = "TvHome.Voice";
    private final Context ctx;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final AudioManager audio;
    private EdgeView view;
    private boolean shown;

    private final AudioManager.AudioRecordingCallback callback = new AudioManager.AudioRecordingCallback() {
        @Override
        public void onRecordingConfigChanged(List<AudioRecordingConfiguration> configs) {
            boolean listening = false;
            for (AudioRecordingConfiguration c : configs) {
                int src = c.getClientAudioSource();
                Log.i(TAG, "recording source " + src);
                // The voice search; the always-on hotword (1999) does not count.
                if (src == android.media.MediaRecorder.AudioSource.VOICE_RECOGNITION
                        || src == android.media.MediaRecorder.AudioSource.MIC
                        || src == android.media.MediaRecorder.AudioSource.DEFAULT) listening = true;
            }
            set(listening);
        }
    };

    VoiceGlow(Context ctx) {
        this.ctx = ctx.getApplicationContext();
        audio = (AudioManager) ctx.getSystemService(Context.AUDIO_SERVICE);
    }

    void start() {
        if (audio != null) audio.registerAudioRecordingCallback(callback, main);
    }

    void stop() {
        if (audio != null) audio.unregisterAudioRecordingCallback(callback);
        set(false);
    }

    private void set(boolean on) {
        if (on == shown) return;
        shown = on;
        if (on) show();
        else hide();
    }

    private void show() {
        if (!Settings.canDrawOverlays(ctx)) {
            Log.w(TAG, "no overlay permission: appops set com.gathin.tvhome SYSTEM_ALERT_WINDOW allow");
            return;
        }
        WindowManager wm = ctx.getSystemService(WindowManager.class);
        if (view == null) {
            view = new EdgeView(ctx);
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT, WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE
                            | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT);
            try {
                wm.addView(view, lp);
            } catch (Exception e) {
                Log.w(TAG, "overlay: " + e);
                view = null;
                return;
            }
        }
        view.animate().cancel();
        view.setAlpha(0f);
        view.animate().alpha(1f).setDuration(350).start();
        view.spin(true);
    }

    private void hide() {
        if (view == null) return;
        final EdgeView v = view;
        v.animate().cancel();
        v.animate().alpha(0f).setDuration(450).withEndAction(() -> {
            v.spin(false);
            if (!shown && view == v) {
                try {
                    ctx.getSystemService(WindowManager.class).removeView(v);
                } catch (Exception ignored) {
                }
                view = null;
            }
        }).start();
    }

    /** The four edges, each a short gradient from a colour to clear, the colours drifting round. */
    private static final class EdgeView extends View {
        private static final int[] COLOURS = {0xFF5AA9FF, 0xFF9B6BFF, 0xFFFF6FB5, 0xFFFFB86B, 0xFF5AA9FF};
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float band;
        private float phase;
        private ValueAnimator turn;

        EdgeView(Context c) {
            super(c);
            band = 34 * c.getResources().getDisplayMetrics().density;
        }

        void spin(boolean on) {
            if (turn != null) turn.cancel();
            turn = null;
            if (!on) return;
            turn = ValueAnimator.ofFloat(0f, 1f);
            turn.setDuration(4000);
            turn.setRepeatCount(ValueAnimator.INFINITE);
            turn.setInterpolator(null);
            turn.addUpdateListener(a -> {
                phase = (float) a.getAnimatedValue();
                invalidate();
            });
            turn.start();
        }

        private int colour(float t) {
            t = ((t + phase) % 1f + 1f) % 1f * (COLOURS.length - 1);
            int i = (int) t;
            float f = t - i;
            int a = COLOURS[i], b = COLOURS[i + 1];
            int r = (int) (((a >> 16) & 0xFF) * (1 - f) + ((b >> 16) & 0xFF) * f);
            int g = (int) (((a >> 8) & 0xFF) * (1 - f) + ((b >> 8) & 0xFF) * f);
            int bl = (int) ((a & 0xFF) * (1 - f) + (b & 0xFF) * f);
            return 0x8C000000 | (r << 16) | (g << 8) | bl;   // about 55%: present, not bright
        }

        @Override
        protected void onDraw(Canvas canvas) {
            int w = getWidth(), h = getHeight();
            int top = colour(0f), right = colour(0.25f), bottom = colour(0.5f), left = colour(0.75f);
            paint.setShader(new LinearGradient(0, 0, 0, band, top, 0, Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, w, band, paint);
            paint.setShader(new LinearGradient(0, h, 0, h - band, bottom, 0, Shader.TileMode.CLAMP));
            canvas.drawRect(0, h - band, w, h, paint);
            paint.setShader(new LinearGradient(0, 0, band, 0, left, 0, Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, band, h, paint);
            paint.setShader(new LinearGradient(w, 0, w - band, 0, right, 0, Shader.TileMode.CLAMP));
            canvas.drawRect(w - band, 0, w, h, paint);
            // A little extra at each corner, where two edges meet.
            float r = band * 2.4f;
            int[] corners = {top, right, bottom, left};
            float[][] at = {{0, 0}, {w, 0}, {w, h}, {0, h}};
            for (int i = 0; i < 4; i++) {
                paint.setShader(new RadialGradient(at[i][0], at[i][1], r, corners[i], 0, Shader.TileMode.CLAMP));
                canvas.drawCircle(at[i][0], at[i][1], r, paint);
            }
        }
    }
}
