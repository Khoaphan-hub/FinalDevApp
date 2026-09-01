package com.example.finalproject.infrastructure.local;

import android.content.Context;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.Locale;

/**
 * Keeps downloaded place photos in the app cache directory so they still appear offline.
 *
 * Nothing ships inside the APK: files arrive only as the user browses. The directory lives under
 * {@code getCacheDir()}, so Android may reclaim it when storage runs low, and it is capped here
 * so a long browsing session cannot grow without limit.
 */
public final class ImageDiskCache {
    private static final String DIRECTORY = "place-images";
    /** Roughly 40 photos at the sizes Django serves. */
    private static final long MAX_BYTES = 25L * 1024 * 1024;

    private static volatile File root;

    private ImageDiskCache() { }

    /** Must be called before the loader can use the disk; safe to call repeatedly. */
    public static void init(Context context) {
        if (root != null) return;
        synchronized (ImageDiskCache.class) {
            if (root == null) {
                File directory = new File(context.getApplicationContext().getCacheDir(), DIRECTORY);
                //noinspection ResultOfMethodCallIgnored
                directory.mkdirs();
                root = directory;
            }
        }
    }

    public static boolean isReady() {
        return root != null;
    }

    public static File fileFor(String url) {
        if (root == null) return null;
        return new File(root, Integer.toHexString(url.hashCode()) + ".img");
    }

    public static byte[] read(String url) {
        File file = fileFor(url);
        if (file == null || !file.exists()) return null;
        try {
            byte[] bytes = new byte[(int) file.length()];
            try (java.io.FileInputStream in = new java.io.FileInputStream(file)) {
                int read = 0;
                while (read < bytes.length) {
                    int count = in.read(bytes, read, bytes.length - read);
                    if (count < 0) break;
                    read += count;
                }
            }
            // Touch it so the eviction pass treats it as recently used.
            //noinspection ResultOfMethodCallIgnored
            file.setLastModified(System.currentTimeMillis());
            return bytes;
        } catch (IOException error) {
            return null;
        }
    }

    public static void write(String url, byte[] bytes) {
        File file = fileFor(url);
        if (file == null) return;
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(bytes);
        } catch (IOException error) {
            //noinspection ResultOfMethodCallIgnored
            file.delete();
            return;
        }
        trim();
    }

    /** Deletes the least recently used files once the directory grows past the cap. */
    private static void trim() {
        File directory = root;
        if (directory == null) return;
        File[] files = directory.listFiles();
        if (files == null) return;

        long total = 0;
        for (File file : files) total += file.length();
        if (total <= MAX_BYTES) return;

        Arrays.sort(files, (left, right) -> Long.compare(left.lastModified(), right.lastModified()));
        for (File file : files) {
            if (total <= MAX_BYTES) break;
            long size = file.length();
            //noinspection ResultOfMethodCallIgnored
            if (file.delete()) total -= size;
        }
    }

    /** Only used for diagnostics and tests. */
    public static String describe() {
        File directory = root;
        if (directory == null) return "uninitialised";
        File[] files = directory.listFiles();
        long total = 0;
        for (File file : files == null ? new File[0] : files) total += file.length();
        return String.format(Locale.ROOT, "%d files, %d KB",
            files == null ? 0 : files.length, total / 1024);
    }
}
