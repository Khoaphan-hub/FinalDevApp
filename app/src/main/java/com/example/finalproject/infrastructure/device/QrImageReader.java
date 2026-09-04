package com.example.finalproject.infrastructure.device;

import android.content.ContentResolver;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import com.google.zxing.BinaryBitmap;
import com.google.zxing.DecodeHintType;
import com.google.zxing.NotFoundException;
import com.google.zxing.RGBLuminanceSource;
import com.google.zxing.Result;
import com.google.zxing.common.HybridBinarizer;
import com.google.zxing.multi.qrcode.QRCodeMultiReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;

/** Decodes locally; image bytes are never uploaded. Run off the main thread. */
public final class QrImageReader {
    private QrImageReader() { }

    public static String read(ContentResolver resolver, Uri uri) throws Exception {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        try (InputStream stream = resolver.openInputStream(uri)) {
            if (stream == null) throw new IOException();
            BitmapFactory.decodeStream(stream, null, options);
        }
        if (options.outWidth < 1 || options.outHeight < 1) throw new IOException();
        options.inSampleSize = 1;
        while (Math.max(options.outWidth, options.outHeight) / options.inSampleSize > 2048) {
            options.inSampleSize *= 2;
        }
        options.inJustDecodeBounds = false;
        options.inPreferredConfig = Bitmap.Config.ARGB_8888;
        Bitmap bitmap;
        try (InputStream stream = resolver.openInputStream(uri)) {
            if (stream == null) throw new IOException();
            bitmap = BitmapFactory.decodeStream(stream, null, options);
        }
        if (bitmap == null) throw new IOException();
        try {
            int w = bitmap.getWidth(), h = bitmap.getHeight();
            int[] pixels = new int[w * h];
            bitmap.getPixels(pixels, 0, w, 0, 0, w, h);
            RGBLuminanceSource source = new RGBLuminanceSource(w, h, pixels);
            Result[] results;
            try {
                results = new QRCodeMultiReader().decodeMultiple(new BinaryBitmap(new HybridBinarizer(source)),
                        Collections.singletonMap(DecodeHintType.TRY_HARDER, Boolean.TRUE));
            } catch (NotFoundException ignored) {
                results = new QRCodeMultiReader().decodeMultiple(new BinaryBitmap(new HybridBinarizer(source.invert())),
                        Collections.singletonMap(DecodeHintType.TRY_HARDER, Boolean.TRUE));
            }
            if (results.length != 1) throw new IllegalArgumentException("MULTIPLE_QR");
            return results[0].getText();
        } finally { bitmap.recycle(); }
    }
}
