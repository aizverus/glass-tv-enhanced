package com.gathin.tvhome;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

/**
 * New pictures for the wallpaper folder: from Bing's daily images (free, no key), from photos a phone
 * sends with LocalSend (they land in Download/), or uploaded from a phone's browser to a small page
 * served while Home or the picker is on screen. Every picture is cropped to the screen, dimmed a little so Home stays
 * readable, and saved as a JPEG in the theme's wallpapers folder.
 */
final class WallpaperImport {
    private static final String TAG = "TvHome.WallImport";
    static final int PORT = 8765;
    private static final int MAX_UPLOAD = 30 * 1024 * 1024;

    private WallpaperImport() {}

    static File dir(Context ctx) {
        File base = Config.dir(ctx);
        if (base == null) return null;
        File d = new File(base, Config.WALLPAPERS_DIR);
        d.mkdirs();
        return d;
    }

    /** Crops to 16:9 at most 1920 wide, dims to 66%, and writes it; false if it is not a picture. */
    static boolean save(byte[] data, File out) {
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, o);
        if (o.outWidth <= 0 || o.outHeight <= 0) return false;
        int sample = 1;
        while (o.outWidth / (sample * 2) >= 1920 && o.outHeight / (sample * 2) >= 1080) sample *= 2;
        o = new BitmapFactory.Options();
        o.inSampleSize = sample;
        Bitmap src = BitmapFactory.decodeByteArray(data, 0, data.length, o);
        if (src == null) return false;
        int w = 1920, h = 1080;
        float scale = Math.max(w / (float) src.getWidth(), h / (float) src.getHeight());
        int cw = Math.round(w / scale), ch = Math.round(h / scale);
        int cx = (src.getWidth() - cw) / 2, cy = (src.getHeight() - ch) / 2;
        Bitmap dst = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Paint p = new Paint(Paint.FILTER_BITMAP_FLAG);
        ColorMatrix dim = new ColorMatrix();
        dim.setScale(0.66f, 0.66f, 0.66f, 1f);
        p.setColorFilter(new ColorMatrixColorFilter(dim));
        new Canvas(dst).drawBitmap(src, new Rect(cx, cy, cx + cw, cy + ch), new Rect(0, 0, w, h), p);
        src.recycle();
        try (OutputStream os = new FileOutputStream(out)) {
            dst.compress(Bitmap.CompressFormat.JPEG, 88, os);
            return true;
        } catch (Exception e) {
            Log.w(TAG, "save " + out + ": " + e);
            return false;
        } finally {
            dst.recycle();
        }
    }

    /** Bing's latest daily pictures (UHD), skipping any with people in them. Returns how many were added. */
    static int fromBing(Context ctx) {
        File d = dir(ctx);
        if (d == null) return 0;
        int added = 0;
        try {
            JSONObject js = new JSONObject(new String(PlaceWallpapers.get(
                    "https://www.bing.com/HPImageArchive.aspx?format=js&idx=0&n=8&mkt=en-US", 256 * 1024),
                    StandardCharsets.UTF_8));
            JSONArray images = js.optJSONArray("images");
            for (int i = 0; images != null && i < images.length(); i++) {
                JSONObject im = images.getJSONObject(i);
                String base = im.optString("urlbase"), title = im.optString("title") + " " + im.optString("copyright");
                String date = im.optString("startdate");
                if (base.isEmpty() || PlaceWallpapers.PEOPLE.matcher(title).find()) continue;
                File out = new File(d, "bing-" + date + ".jpg");
                if (out.exists()) continue;
                byte[] jpg = PlaceWallpapers.get("https://www.bing.com" + base + "_UHD.jpg", 12 * 1024 * 1024);
                if (PlaceWallpapers.faces(jpg) != 0) continue;
                if (save(jpg, out)) added++;
            }
        } catch (Exception e) {
            Log.w(TAG, "bing: " + e);
        }
        return added;
    }

    /** Photos a phone sent with LocalSend (saved under Download/) that are not in the folder yet. */
    static int fromLocalSend(Context ctx) {
        File d = dir(ctx);
        if (d == null) return 0;
        int added = 0;
        File root = new File("/storage/emulated/0/Download");
        for (File folder : new File[]{root, new File(root, "LocalSend")}) {
            File[] files = folder.listFiles();
            if (files == null) continue;
            for (File f : files) {
                String n = f.getName().toLowerCase(Locale.ROOT);
                if (!f.isFile() || !(n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".webp"))) continue;
                File out = new File(d, "phone-" + Integer.toHexString((f.getName() + f.length()).hashCode()) + ".jpg");
                if (out.exists() || f.length() > MAX_UPLOAD) continue;
                try (InputStream in = new FileInputStream(f)) {
                    if (save(readAll(in, (int) f.length()), out)) added++;
                } catch (Exception e) {
                    Log.w(TAG, "localsend " + f + ": " + e);
                }
            }
        }
        return added;
    }

    private static byte[] readAll(InputStream in, int length) throws Exception {
        byte[] buf = new byte[length];
        int n = 0, r;
        while (n < length && (r = in.read(buf, n, length - n)) > 0) n += r;
        if (n != length) throw new IllegalStateException("short read " + n + "/" + length);
        return buf;
    }

    /** The page a phone opens to send pictures; served only while the picker is on screen. */
    static final class Server extends Thread {
        private final Context ctx;
        private final Runnable onAdded;
        private ServerSocket socket;

        Server(Context ctx, Runnable onAdded) {
            super("wallpaper-upload");
            this.ctx = ctx.getApplicationContext();
            this.onAdded = onAdded;
        }

        void close() {
            try {
                if (socket != null) socket.close();
            } catch (Exception ignored) {
            }
        }

        @Override
        public void run() {
            try {
                // Home and the picker hand the port between them: reuse it at once, and wait a moment
                // if the other has not let go yet.
                for (int tries = 0; ; tries++) {
                    try {
                        socket = new ServerSocket();
                        socket.setReuseAddress(true);
                        socket.bind(new java.net.InetSocketAddress(PORT));
                        break;
                    } catch (java.io.IOException busy) {
                        if (socket != null) socket.close();
                        if (tries >= 10) throw busy;
                        Thread.sleep(300);
                    }
                }
                while (!socket.isClosed()) {
                    try (Socket s = socket.accept()) {
                        s.setSoTimeout(30000);
                        handle(s);
                    } catch (Exception e) {
                        if (!socket.isClosed()) Log.w(TAG, "request: " + e);
                    }
                }
            } catch (Exception e) {
                Log.w(TAG, "server: " + e);
            }
        }

        private void handle(Socket s) throws Exception {
            InputStream in = s.getInputStream();
            String request = line(in);
            int length = 0;
            for (String h; !(h = line(in)).isEmpty(); ) {
                int c = h.indexOf(':');
                if (c > 0 && h.substring(0, c).trim().equalsIgnoreCase("Content-Length")) {
                    length = Integer.parseInt(h.substring(c + 1).trim());
                }
            }
            OutputStream out = s.getOutputStream();
            if (request.startsWith("POST /upload")) {
                if (length <= 0 || length > MAX_UPLOAD) {
                    reply(out, "413 Too Large", "text/plain", "too large");
                    return;
                }
                byte[] body = readAll(in, length);
                boolean ok;
                String audio = audioType(body);
                if (audio != null) {
                    // An azan recording: kept as it is, for the prayer-time sound list.
                    File a = PrayerAlarm.azanDir(ctx);
                    ok = a != null;
                    if (ok) try (OutputStream os = new FileOutputStream(new File(a, "azan-" + System.currentTimeMillis() + audio))) {
                        os.write(body);
                    }
                } else {
                    File d = dir(ctx);
                    ok = d != null && save(body, new File(d, "phone-" + System.currentTimeMillis() + ".jpg"));
                }
                reply(out, ok ? "200 OK" : "400 Bad Request", "text/plain", ok ? "ok" : "not a picture");
                if (ok) onAdded.run();
            } else {
                reply(out, "200 OK", "text/html; charset=utf-8", PAGE);
            }
        }

        /** ".mp3", ".ogg", ".m4a" or ".wav" when the bytes start like one of those, else null. */
        private static String audioType(byte[] b) {
            if (b.length < 12) return null;
            if ((b[0] == 'I' && b[1] == 'D' && b[2] == '3') || ((b[0] & 0xFF) == 0xFF && (b[1] & 0xE0) == 0xE0)) return ".mp3";
            if (b[0] == 'O' && b[1] == 'g' && b[2] == 'g' && b[3] == 'S') return ".ogg";
            if (b[4] == 'f' && b[5] == 't' && b[6] == 'y' && b[7] == 'p') return ".m4a";
            if (b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F' && b[8] == 'W') return ".wav";
            return null;
        }

        private static String line(InputStream in) throws Exception {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            for (int c; (c = in.read()) != -1 && c != '\n'; ) if (c != '\r') b.write(c);
            return b.toString("UTF-8");
        }

        private static void reply(OutputStream out, String status, String type, String body) throws Exception {
            byte[] data = body.getBytes(StandardCharsets.UTF_8);
            out.write(("HTTP/1.1 " + status + "\r\nContent-Type: " + type + "\r\nContent-Length: " + data.length
                    + "\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            out.write(data);
            out.flush();
        }

        private static final String PAGE = "<!doctype html><html><head><meta charset=utf-8>"
                + "<meta name=viewport content='width=device-width,initial-scale=1'><title>Glass TV wallpaper</title>"
                + "<style>body{font-family:system-ui,sans-serif;background:#0b0e16;color:#fff;margin:0;padding:24px}"
                + "h1{font-weight:300;font-size:26px}label{display:block;padding:22px;border:2px dashed #ffc46b;"
                + "border-radius:16px;text-align:center;font-size:18px}input{display:none}#s{margin-top:18px;opacity:.85}</style>"
                + "</head><body><h1>Send wallpapers to the TV</h1>"
                + "<label>Choose photos or azan audio<input id=f type=file accept='image/*,audio/*' multiple></label><div id=s></div>"
                + "<script>f.onchange=async()=>{let n=0;for(const x of f.files){s.textContent='Sending '+(++n)+' of '"
                + "+f.files.length+'…';await fetch('/upload',{method:'POST',body:x});}"
                + "s.textContent='Done: '+n+' photo(s) are on the TV.';}</script></body></html>";
    }
}
