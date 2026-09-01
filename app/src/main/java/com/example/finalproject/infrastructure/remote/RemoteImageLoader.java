package com.example.finalproject.infrastructure.remote;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import com.example.finalproject.infrastructure.local.ImageDiskCache;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class RemoteImageLoader {
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(3);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    /**
     * An eighth of the heap, measured in kilobytes. LruCache counts entries by default, so a
     * plain "24 images" limit lets two dozen large photos hold far more memory than intended.
     */
    private static final int CACHE_SIZE_KB = (int) (Runtime.getRuntime().maxMemory() / 1024 / 8);
    private static final LruCache<String, Bitmap> CACHE = new LruCache<String, Bitmap>(CACHE_SIZE_KB) {
        @Override protected int sizeOf(String key, Bitmap value) {
            // Never 0: an entry reported as weightless would never be evicted.
            return Math.max(1, value.getByteCount() / 1024);
        }
    };

    private RemoteImageLoader() { }

    public static void load(String url, ImageView target) {
        // Stamp the view with the URL it is bound to RIGHT NOW, before any early return.
        // RecyclerView reuses ImageViews, so a view still carrying the previous row's tag
        // would accept that row's download and paint the wrong photo over the new row.
        target.setTag(url);
        if (url == null || url.trim().isEmpty()) return;
        // The view is the only Context this static loader has; init is cheap and idempotent.
        ImageDiskCache.init(target.getContext());
        Bitmap cached = CACHE.get(url);
        if (cached != null) {
            target.setImageBitmap(cached);
            return;
        }
        EXECUTOR.execute(() -> {
            // Disk first: offline this is the only source, and online it saves a round trip.
            byte[] stored = ImageDiskCache.read(url);
            if (stored != null) {
                Bitmap bitmap = BitmapFactory.decodeByteArray(stored, 0, stored.length);
                if (bitmap != null) {
                    publish(url, bitmap, target);
                    return;
                }
            }

            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(url).openConnection();
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(8000);
                connection.setInstanceFollowRedirects(true);
                try (InputStream stream = connection.getInputStream()) {
                    byte[] bytes = readAll(stream);
                    Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                    if (bitmap != null) {
                        ImageDiskCache.write(url, bytes);
                        publish(url, bitmap, target);
                    }
                }
            } catch (Exception ignored) {
                // The local fallback remains visible when an image cannot be downloaded.
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private static void publish(String url, Bitmap bitmap, ImageView target) {
        CACHE.put(url, bitmap);
        MAIN.post(() -> {
            if (url.equals(target.getTag())) target.setImageBitmap(bitmap);
        });
    }

    private static byte[] readAll(InputStream stream) throws java.io.IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int count;
        while ((count = stream.read(chunk)) != -1) buffer.write(chunk, 0, count);
        return buffer.toByteArray();
    }
}
