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
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public final class RemoteImageLoader {
    private static final int PARALLEL_DOWNLOADS = 4;

    /**
     * Decode target when the ImageView has not been measured yet, i.e. the very first layout
     * pass. Full screen width, so a hero image is not visibly soft; recycled list rows report
     * their real width and get a far smaller bitmap than this.
     */
    private static final int DEFAULT_TARGET_PX = 1080;

    /**
     * Newest request first.
     *
     * ThreadPoolExecutor enqueues through offer(), so routing offer() to offerFirst() turns the
     * queue into a stack. With a plain FIFO queue, scrolling past fifty rows leaves the images
     * for the rows now on screen at the back of the queue, behind every row that already
     * scrolled away, which is why pictures appeared to fill in from the top downwards long
     * after scrolling stopped.
     */
    private static final ExecutorService EXECUTOR = new ThreadPoolExecutor(
        PARALLEL_DOWNLOADS, PARALLEL_DOWNLOADS, 0L, TimeUnit.MILLISECONDS,
        new LinkedBlockingDeque<Runnable>() {
            @Override public boolean offer(Runnable task) { return offerFirst(task); }
        });
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
        // Read the view size here, on the main thread, while touching the view is safe.
        // A recycled row is already measured; a brand new one reports 0 and takes the default.
        int measured = Math.max(target.getWidth(), target.getHeight());
        final int targetPx = measured > 0 ? measured : DEFAULT_TARGET_PX;

        EXECUTOR.execute(() -> {
            // The view may have been rebound while this task waited in the queue. Checking the
            // tag BEFORE any disk or network work means a fast scroll costs nothing for rows
            // that are already gone, instead of decoding and transferring bytes nobody sees.
            if (!url.equals(target.getTag())) return;

            // Disk first: offline this is the only source, and online it saves a round trip.
            byte[] stored = ImageDiskCache.read(url);
            if (stored != null) {
                Bitmap bitmap = decodeScaled(stored, targetPx);
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
                    Bitmap bitmap = decodeScaled(bytes, targetPx);
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

    /**
     * Decodes at the smallest power-of-two reduction that still covers targetPx.
     *
     * The catalog photos are full-resolution originals; the largest is 2592x1944, which would
     * occupy about 19 MB as a bitmap while being shown in a 104dp thumbnail. Decoding it at 1/8
     * scale looks identical on screen for a fraction of the memory, and decodes far faster,
     * which is most of why the list felt slow.
     */
    private static Bitmap decodeScaled(byte[] data, int targetPx) {
        BitmapFactory.Options bounds = new BitmapFactory.Options();
        // Reads the header only: fills in outWidth/outHeight without allocating any pixels.
        bounds.inJustDecodeBounds = true;
        BitmapFactory.decodeByteArray(data, 0, data.length, bounds);

        int sampleSize = 1;
        while (bounds.outWidth / (sampleSize * 2) >= targetPx
            && bounds.outHeight / (sampleSize * 2) >= targetPx) {
            sampleSize *= 2;
        }

        BitmapFactory.Options options = new BitmapFactory.Options();
        // BitmapFactory only honours powers of two, which is why the loop doubles.
        options.inSampleSize = sampleSize;
        return BitmapFactory.decodeByteArray(data, 0, data.length, options);
    }

    private static byte[] readAll(InputStream stream) throws java.io.IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int count;
        while ((count = stream.read(chunk)) != -1) buffer.write(chunk, 0, count);
        return buffer.toByteArray();
    }
}
