package com.example.finalproject.infrastructure.remote;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Handler;
import android.os.Looper;
import android.util.LruCache;
import android.widget.ImageView;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class RemoteImageLoader {
    private static final ExecutorService EXECUTOR = Executors.newFixedThreadPool(3);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final LruCache<String, Bitmap> CACHE = new LruCache<>(24);

    private RemoteImageLoader() { }

    public static void load(String url, ImageView target) {
        if (url == null || url.trim().isEmpty()) return;
        Bitmap cached = CACHE.get(url);
        if (cached != null) {
            target.setImageBitmap(cached);
            return;
        }
        target.setTag(url);
        EXECUTOR.execute(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(url).openConnection();
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(8000);
                connection.setInstanceFollowRedirects(true);
                try (InputStream stream = connection.getInputStream()) {
                    Bitmap bitmap = BitmapFactory.decodeStream(stream);
                    if (bitmap != null) {
                        CACHE.put(url, bitmap);
                        MAIN.post(() -> {
                            if (url.equals(target.getTag())) target.setImageBitmap(bitmap);
                        });
                    }
                }
            } catch (Exception ignored) {
                // The local fallback remains visible when an image cannot be downloaded.
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }
}
