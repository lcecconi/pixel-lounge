package com.essential.tvlauncher;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.widget.ImageView;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WatchNextLoader {

    private static final Uri WATCH_NEXT_URI = Uri.parse("content://android.media.tv/watch_next_program");
    private static final ExecutorService IO_EXECUTOR = Executors.newFixedThreadPool(3);
    private static final Handler MAIN_HANDLER = new Handler(Looper.getMainLooper());
    private static final Map<String, Bitmap> BITMAP_CACHE = new HashMap<>();

    public static class WatchNextItem {
        public final long id;
        public final String packageName;
        public final String title;
        public final String subtitle;
        public final String posterUri;
        public final String intentUri;
        public final float progress; // 0f..1f

        public WatchNextItem(long id, String packageName, String title, String subtitle,
                             String posterUri, String intentUri, float progress) {
            this.id = id;
            this.packageName = packageName;
            this.title = title;
            this.subtitle = subtitle;
            this.posterUri = posterUri;
            this.intentUri = intentUri;
            this.progress = progress;
        }
    }

    public static List<WatchNextItem> loadWatchNext(Context context) {
        List<WatchNextItem> items = new ArrayList<>();
        String[] projection = new String[] {
                "_id",
                "package_name",
                "title",
                "episode_title",
                "poster_art_uri",
                "thumbnail_uri",
                "intent_uri",
                "last_playback_position_millis",
                "duration_millis"
        };

        Cursor cursor = null;
        try {
            ContentResolver cr = context.getContentResolver();
            cursor = cr.query(
                    WATCH_NEXT_URI,
                    projection,
                    null,
                    null,
                    "last_engagement_time_utc_millis DESC"
            );
            if (cursor != null) {
                while (cursor.moveToNext() && items.size() < 10) {
                    long id = cursor.getLong(0);
                    String pkg = cursor.getString(1);
                    String title = cursor.getString(2);
                    String episode = cursor.getString(3);
                    String poster = cursor.getString(4);
                    String thumb = cursor.getString(5);
                    String intentUri = cursor.getString(6);
                    long pos = cursor.isNull(7) ? 0 : cursor.getLong(7);
                    long dur = cursor.isNull(8) ? 0 : cursor.getLong(8);

                    if (title == null || title.isEmpty()) continue;
                    String art = (poster != null && !poster.isEmpty()) ? poster : thumb;
                    float progress = (dur > 0 && pos > 0) ? Math.min(1f, (float) pos / (float) dur) : 0f;

                    items.add(new WatchNextItem(id, pkg, title, episode, art, intentUri, progress));
                }
            }
        } catch (Exception ignored) {
            // Permission not granted or provider unavailable on this device
        } finally {
            if (cursor != null) {
                try { cursor.close(); } catch (Exception ignored) {}
            }
        }
        return items;
    }

    public static void loadPosterAsync(final Context context, final String uriStr, final ImageView target) {
        if (uriStr == null || uriStr.isEmpty()) return;

        synchronized (BITMAP_CACHE) {
            Bitmap cached = BITMAP_CACHE.get(uriStr);
            if (cached != null) {
                target.setImageBitmap(cached);
                return;
            }
        }

        target.setTag(uriStr);
        IO_EXECUTOR.execute(new Runnable() {
            @Override
            public void run() {
                Bitmap bmp = null;
                InputStream is = null;
                try {
                    if (uriStr.startsWith("http://") || uriStr.startsWith("https://")) {
                        HttpURLConnection conn = (HttpURLConnection) new URL(uriStr).openConnection();
                        conn.setConnectTimeout(4000);
                        conn.setReadTimeout(4000);
                        is = conn.getInputStream();
                    } else {
                        is = context.getContentResolver().openInputStream(Uri.parse(uriStr));
                    }
                    if (is != null) {
                        BitmapFactory.Options opts = new BitmapFactory.Options();
                        opts.inSampleSize = 2;
                        bmp = BitmapFactory.decodeStream(is, null, opts);
                    }
                } catch (Exception ignored) {
                } finally {
                    if (is != null) {
                        try { is.close(); } catch (Exception ignored) {}
                    }
                }

                if (bmp != null) {
                    synchronized (BITMAP_CACHE) {
                        if (BITMAP_CACHE.size() > 24) BITMAP_CACHE.clear();
                        BITMAP_CACHE.put(uriStr, bmp);
                    }
                    final Bitmap finalBmp = bmp;
                    MAIN_HANDLER.post(new Runnable() {
                        @Override
                        public void run() {
                            if (uriStr.equals(target.getTag())) {
                                target.setImageBitmap(finalBmp);
                            }
                        }
                    });
                }
            }
        });
    }
}
