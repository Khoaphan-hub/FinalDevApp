package com.example.finalproject.infrastructure.local.export;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.pdf.PdfDocument;

import com.example.finalproject.R;
import com.example.finalproject.domain.model.Itinerary;
import com.example.finalproject.domain.model.ItineraryDay;
import com.example.finalproject.domain.model.ItineraryStop;
import com.example.finalproject.domain.model.WeatherCodeMapper;
import com.example.finalproject.domain.model.WeatherSnapshot;
import com.example.finalproject.infrastructure.link.ExternalPlaceLinks;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class ItineraryPdfExporter {
    private static final int PAGE_WIDTH = 595;
    private static final int PAGE_HEIGHT = 842;
    private static final int MARGIN = 38;
    private static final int CONTENT_WIDTH = PAGE_WIDTH - MARGIN * 2;
    private static final int CONTENT_BOTTOM = 805;
    private static final int ROW_HEIGHT = 126;
    private static final int PRIMARY = Color.rgb(31, 93, 80);
    private static final int PRIMARY_DARK = Color.rgb(21, 63, 55);
    private static final int PRIMARY_SOFT = Color.rgb(226, 239, 233);
    private static final int ACCENT = Color.rgb(242, 140, 104);
    private static final int TEXT = Color.rgb(30, 45, 41);
    private static final int MUTED = Color.rgb(104, 122, 116);
    private static final int BACKGROUND = Color.rgb(250, 247, 240);
    private static final int WHITE = Color.WHITE;

    public File export(Context context, Itinerary itinerary, Bitmap qr, String resumeUrl,
                       WeatherSnapshot weather) throws IOException {
        File directory = new File(context.getCacheDir(), "exports");
        if (!directory.exists() && !directory.mkdirs()) {
            throw new IOException("Unable to create PDF directory");
        }
        File file = new File(directory, "Journify-visual-itinerary-" + System.currentTimeMillis() + ".pdf");
        Map<String, Bitmap> images = loadImages(itinerary);
        PdfDocument document = new PdfDocument();
        Writer writer = new Writer(document, context);
        try {
            drawCover(writer, itinerary, images);
            for (int dayIndex = 0; dayIndex < itinerary.getDays().size(); dayIndex++) {
                drawDay(writer, itinerary.getDays().get(dayIndex), dayIndex, weather, images);
            }
            drawQrPage(writer, itinerary, qr, resumeUrl);
            writer.finish();
            try (FileOutputStream output = new FileOutputStream(file)) {
                document.writeTo(output);
            }
        } finally {
            document.close();
            Set<Bitmap> unique = new HashSet<>(images.values());
            for (Bitmap image : unique) {
                if (image != null && image != qr && !image.isRecycled()) image.recycle();
            }
        }
        return file;
    }

    public File export(Context context, Itinerary itinerary, Bitmap qr, String resumeUrl) throws IOException {
        return export(context, itinerary, qr, resumeUrl, null);
    }

    private void drawCover(Writer writer, Itinerary itinerary, Map<String, Bitmap> images) {
        writer.newPage();
        writer.single("JOURNIFY", MARGIN, 42, 12, true, PRIMARY, CONTENT_WIDTH);
        int titleBottom = writer.block(itinerary.getTitle(), MARGIN, 68, 25, true, TEXT,
            CONTENT_WIDTH, 2, 5);
        writer.block(writer.context.getString(R.string.pdf_subtitle), MARGIN, titleBottom + 4,
            10, false, MUTED, CONTENT_WIDTH, 2, 3);

        int metricsTop = Math.max(145, titleBottom + 34);
        int gap = 8;
        int cardWidth = (CONTENT_WIDTH - gap) / 2;
        drawMetric(writer, MARGIN, metricsTop, cardWidth,
            writer.context.getString(R.string.pdf_total_budget), money(itinerary.getTotalBudgetVnd()));
        drawMetric(writer, MARGIN + cardWidth + gap, metricsTop, cardWidth,
            writer.context.getString(R.string.pdf_estimated_cost), money(itinerary.getEstimatedCostVnd()));
        drawMetric(writer, MARGIN, metricsTop + 62, cardWidth,
            writer.context.getString(R.string.pdf_remaining_budget), money(itinerary.getRemainingBudgetVnd()));
        drawMetric(writer, MARGIN + cardWidth + gap, metricsTop + 62, cardWidth,
            writer.context.getString(R.string.pdf_total_distance), distance(totalDistance(itinerary)));

        int previewLabelY = metricsTop + 143;
        writer.single(writer.context.getString(R.string.pdf_trip_overview), MARGIN, previewLabelY,
            9, true, PRIMARY, CONTENT_WIDTH);
        int previewTop = previewLabelY + 16;
        drawImageMosaic(writer, itinerary, images, previewTop, 176);

        int overviewTop = previewTop + 192;
        writer.fillRoundRect(MARGIN, overviewTop, CONTENT_WIDTH, 92, 18, PRIMARY_DARK);
        int places = countPlaces(itinerary);
        writer.single(writer.context.getString(R.string.pdf_days_count, itinerary.getDays().size()),
            MARGIN + 22, overviewTop + 29, 16, true, WHITE, 135);
        writer.single(writer.context.getString(R.string.pdf_places_count, places),
            MARGIN + 180, overviewTop + 29, 16, true, WHITE, 145);
        writer.single(distance(totalDistance(itinerary)), MARGIN + 350, overviewTop + 29,
            16, true, WHITE, 130);
        writer.block(writer.context.getString(R.string.pdf_route_note), MARGIN + 22,
            overviewTop + 50, 8, false, Color.rgb(221, 235, 229), CONTENT_WIDTH - 44, 2, 3);
        writer.block(writer.context.getString(R.string.pdf_weather_note), MARGIN, overviewTop + 116,
            8, false, MUTED, CONTENT_WIDTH, 2, 3);
    }

    private void drawMetric(Writer writer, int x, int y, int width, String label, String value) {
        writer.fillRoundRect(x, y, width, 54, 13, PRIMARY_SOFT);
        writer.single(label, x + 13, y + 16, 7.5f, false, MUTED, width - 26);
        writer.single(value, x + 13, y + 36, 13, true, PRIMARY_DARK, width - 26);
    }

    private void drawImageMosaic(Writer writer, Itinerary itinerary, Map<String, Bitmap> images,
                                 int top, int height) {
        List<Bitmap> preview = new ArrayList<>();
        for (ItineraryDay day : itinerary.getDays()) {
            for (ItineraryStop stop : day.getStops()) {
                Bitmap image = images.get(stop.getImageUrl());
                if (image != null && !preview.contains(image)) preview.add(image);
                if (preview.size() == 3) break;
            }
            if (preview.size() == 3) break;
        }
        int gap = 7;
        int width = (CONTENT_WIDTH - gap * 2) / 3;
        for (int i = 0; i < 3; i++) {
            int x = MARGIN + i * (width + gap);
            Bitmap bitmap = i < preview.size() ? preview.get(i) : null;
            drawImageOrPlaceholder(writer, bitmap, new RectF(x, top, x + width, top + height), 14);
        }
    }

    private void drawDay(Writer writer, ItineraryDay day, int dayIndex, WeatherSnapshot weather,
                         Map<String, Bitmap> images) {
        writer.newPage();
        int rowTop = drawDayOpening(writer, day, dayIndex, weather);
        int placeIndex = 1;
        for (ItineraryStop stop : day.getStops()) {
            if (rowTop + ROW_HEIGHT > CONTENT_BOTTOM) {
                writer.newPage();
                rowTop = drawContinuationHeader(writer, day);
            }
            String marker = stop.getType() == ItineraryStop.Type.ACCOMMODATION
                ? "S" : String.valueOf(placeIndex++);
            drawStopRow(writer, stop, marker, rowTop, images.get(stop.getImageUrl()));
            rowTop += ROW_HEIGHT + 8;
        }
    }

    private int drawDayOpening(Writer writer, ItineraryDay day, int dayIndex, WeatherSnapshot weather) {
        writer.single("JOURNIFY", PAGE_WIDTH - MARGIN - 75, 34, 8, true, PRIMARY, 75);
        writer.single(writer.context.getString(R.string.pdf_day, day.getDayNumber()), MARGIN, 55,
            22, true, TEXT, 250);
        writer.single(writer.context.getString(R.string.pdf_day_distance, totalDistance(day)),
            PAGE_WIDTH - MARGIN - 150, 57, 10, true, PRIMARY, 150);
        drawWeatherCard(writer, dayIndex, weather, 79);
        drawRouteDiagram(writer, day, 153);
        writer.single(writer.context.getString(R.string.pdf_places_section), MARGIN, 343,
            9, true, PRIMARY, CONTENT_WIDTH);
        return 361;
    }

    private int drawContinuationHeader(Writer writer, ItineraryDay day) {
        writer.single("JOURNIFY", PAGE_WIDTH - MARGIN - 75, 34, 8, true, PRIMARY, 75);
        writer.single(writer.context.getString(R.string.pdf_day, day.getDayNumber()), MARGIN, 52,
            18, true, TEXT, 210);
        writer.single(writer.context.getString(R.string.pdf_continued), MARGIN, 73,
            8, true, PRIMARY, CONTENT_WIDTH);
        return 91;
    }

    private void drawWeatherCard(Writer writer, int dayIndex, WeatherSnapshot weather, int top) {
        writer.fillRoundRect(MARGIN, top, CONTENT_WIDTH, 60, 14, Color.WHITE);
        writer.strokeRoundRect(MARGIN, top, CONTENT_WIDTH, 60, 14,
            Color.rgb(218, 228, 223), 1);
        writer.single(writer.context.getString(R.string.pdf_weather_title), MARGIN + 15, top + 18,
            7.5f, true, PRIMARY, 140);
        if (weather != null && dayIndex < weather.forecast.size()) {
            WeatherSnapshot.Day forecast = weather.forecast.get(dayIndex);
            String condition = writer.context.getString(WeatherCodeMapper.labelRes(forecast.code));
            String date = formatDate(forecast.date);
            writer.single(date, MARGIN + 15, top + 39, 11, true, TEXT, 115);
            writer.single(writer.context.getString(R.string.pdf_weather_line, condition,
                    Math.round(forecast.min), Math.round(forecast.max), forecast.rain),
                MARGIN + 144, top + 39, 10, false, TEXT, CONTENT_WIDTH - 159);
        } else {
            writer.single(writer.context.getString(R.string.pdf_weather_unavailable), MARGIN + 15,
                top + 39, 10, false, MUTED, CONTENT_WIDTH - 30);
        }
    }

    private void drawRouteDiagram(Writer writer, ItineraryDay day, int top) {
        int height = 174;
        writer.fillRoundRect(MARGIN, top, CONTENT_WIDTH, height, 15, PRIMARY_SOFT);
        writer.single(writer.context.getString(R.string.pdf_route_title), MARGIN + 14, top + 19,
            8, true, PRIMARY, 170);
        writer.single(writer.context.getString(R.string.pdf_day_distance, totalDistance(day)),
            PAGE_WIDTH - MARGIN - 145, top + 19, 8, true, PRIMARY, 145);

        RectF map = new RectF(MARGIN + 14, top + 31, PAGE_WIDTH - MARGIN - 14, top + 137);
        writer.fillRoundRect((int) map.left, (int) map.top, (int) map.width(), (int) map.height(),
            10, Color.rgb(247, 250, 248));
        Paint paint = writer.paint;
        paint.setStrokeWidth(0.7f);
        paint.setColor(Color.rgb(225, 234, 230));
        for (int i = 1; i < 5; i++) {
            float x = map.left + map.width() * i / 5f;
            float y = map.top + map.height() * i / 5f;
            writer.canvas.drawLine(x, map.top, x, map.bottom, paint);
            writer.canvas.drawLine(map.left, y, map.right, y, paint);
        }

        List<ItineraryStop> stops = day.getStops();
        if (!stops.isEmpty()) {
            double minLat = Double.MAX_VALUE, maxLat = -Double.MAX_VALUE;
            double minLon = Double.MAX_VALUE, maxLon = -Double.MAX_VALUE;
            for (ItineraryStop stop : stops) {
                minLat = Math.min(minLat, stop.getLatitude());
                maxLat = Math.max(maxLat, stop.getLatitude());
                minLon = Math.min(minLon, stop.getLongitude());
                maxLon = Math.max(maxLon, stop.getLongitude());
            }
            double latSpan = Math.max(0.002, maxLat - minLat);
            double lonSpan = Math.max(0.002, maxLon - minLon);
            List<float[]> points = new ArrayList<>();
            for (ItineraryStop stop : stops) {
                float x = (float) (map.left + 17 + (stop.getLongitude() - minLon)
                    / lonSpan * (map.width() - 34));
                float y = (float) (map.bottom - 17 - (stop.getLatitude() - minLat)
                    / latSpan * (map.height() - 34));
                points.add(new float[]{x, y});
            }
            Path route = new Path();
            route.moveTo(points.get(0)[0], points.get(0)[1]);
            for (int i = 1; i < points.size(); i++) route.lineTo(points.get(i)[0], points.get(i)[1]);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(3);
            paint.setStrokeCap(Paint.Cap.ROUND);
            paint.setStrokeJoin(Paint.Join.ROUND);
            paint.setColor(PRIMARY);
            writer.canvas.drawPath(route, paint);
            paint.setStyle(Paint.Style.FILL);
            int placeIndex = 1;
            for (int i = 0; i < stops.size(); i++) {
                boolean start = stops.get(i).getType() == ItineraryStop.Type.ACCOMMODATION;
                String marker = start ? "S" : String.valueOf(placeIndex++);
                paint.setColor(start ? ACCENT : PRIMARY);
                writer.canvas.drawCircle(points.get(i)[0], points.get(i)[1], 9, paint);
                writer.center(marker, points.get(i)[0], points.get(i)[1] + 2.7f,
                    7, true, WHITE);
            }
        }
        writer.block(writer.context.getString(R.string.pdf_route_note), MARGIN + 14, top + 145,
            7, false, MUTED, CONTENT_WIDTH - 28, 2, 2);
    }

    private void drawStopRow(Writer writer, ItineraryStop stop, String marker, int top, Bitmap image) {
        writer.fillRoundRect(MARGIN, top, CONTENT_WIDTH, ROW_HEIGHT, 15, WHITE);
        writer.strokeRoundRect(MARGIN, top, CONTENT_WIDTH, ROW_HEIGHT, 15,
            Color.rgb(225, 231, 228), 0.8f);
        RectF imageRect = new RectF(MARGIN + 8, top + 8, MARGIN + 124, top + 96);
        drawImageOrPlaceholder(writer, image, imageRect, 10);
        writer.fillCircle(imageRect.left + 13, imageRect.top + 13, 10,
            stop.getType() == ItineraryStop.Type.ACCOMMODATION ? ACCENT : PRIMARY);
        writer.center(marker, imageRect.left + 13, imageRect.top + 15.5f,
            7, true, WHITE);
        writer.single(typeLabel(writer.context, stop), MARGIN + 8, top + 112,
            7, true, PRIMARY, 116);

        int textX = MARGIN + 138;
        int textWidth = CONTENT_WIDTH - 148;
        writer.block(safe(stop.getName()), textX, top + 10, 11, true, TEXT,
            textWidth, 2, 2.5f);
        writer.block(safe(stop.getAddress()), textX, top + 39, 7.2f, false, MUTED,
            textWidth, 2, 2);

        String rating = stop.getRating() > 0
            ? writer.context.getString(R.string.pdf_rating, stop.getRating())
            : writer.context.getString(R.string.pdf_no_rating);
        String price = stop.getPriceVnd() > 0
            ? writer.context.getString(R.string.pdf_price, money(stop.getPriceVnd()))
            : writer.context.getString(R.string.pdf_price_unknown);
        String next = stop.getTravelToNextKm() > 0
            ? writer.context.getString(R.string.pdf_next_short, stop.getTravelToNextKm()) : "";
        writer.fit(joinNonEmpty(" | ", rating, price, next), textX, top + 72,
            7.5f, 6, true, PRIMARY_DARK, textWidth);

        String hours = isBlank(stop.getOpenHours())
            ? writer.context.getString(R.string.pdf_hours_unknown)
            : writer.context.getString(R.string.pdf_hours, stop.getOpenHours());
        if (!isBlank(stop.getMealSlot())) hours += " | " + stop.getMealSlot();
        writer.fit(hours, textX, top + 87, 7.2f, 5.8f, false, TEXT, textWidth);

        String reviewUrl = ExternalPlaceLinks.cleanReviewUrl(stop.getMediaUrl());
        String review = isBlank(reviewUrl)
            ? writer.context.getString(R.string.pdf_review_unavailable)
            : reviewLabel(writer.context, reviewUrl) + ": " + reviewUrl;
        writer.fit(review, textX, top + 102, 6.4f, 4.8f, false,
            isBlank(reviewUrl) ? MUTED : Color.rgb(31, 93, 145), textWidth);
        String mapUrl = ExternalPlaceLinks.googleMapsSearchUrl(
            stop.getMapName(), stop.getMapAddress(), stop.getLatitude(), stop.getLongitude());
        writer.fit(writer.context.getString(R.string.pdf_map_link) + ": " + mapUrl,
            textX, top + 116, 6.4f, 4.8f, false, Color.rgb(31, 93, 145), textWidth);
    }

    private void drawQrPage(Writer writer, Itinerary itinerary, Bitmap qr, String resumeUrl) {
        writer.newPage();
        writer.single("JOURNIFY", MARGIN, 46, 12, true, PRIMARY, CONTENT_WIDTH);
        writer.block(writer.context.getString(R.string.pdf_reopen), MARGIN, 82, 24,
            true, TEXT, CONTENT_WIDTH, 2, 4);
        writer.block(writer.context.getString(R.string.pdf_qr_help), MARGIN, 139, 10,
            false, MUTED, CONTENT_WIDTH, 3, 3);
        if (qr != null) {
            Rect source = new Rect(0, 0, qr.getWidth(), qr.getHeight());
            RectF destination = new RectF((PAGE_WIDTH - 210) / 2f, 206,
                (PAGE_WIDTH + 210) / 2f, 416);
            writer.canvas.drawBitmap(qr, source, destination, writer.paint);
        }
        writer.block(resumeUrl, MARGIN, 444, 7.5f, false, Color.rgb(31, 93, 145),
            CONTENT_WIDTH, 4, 2);
        writer.fillRoundRect(MARGIN, 526, CONTENT_WIDTH, 94, 16, PRIMARY_SOFT);
        writer.single(itinerary.getTitle(), MARGIN + 18, 552, 13, true, TEXT, CONTENT_WIDTH - 36);
        writer.single(writer.context.getString(R.string.pdf_days_count, itinerary.getDays().size()),
            MARGIN + 18, 578, 10, true, PRIMARY, 120);
        writer.single(distance(totalDistance(itinerary)), MARGIN + 170, 578,
            10, true, PRIMARY, 120);
        writer.single(money(itinerary.getEstimatedCostVnd()), MARGIN + 330, 578,
            10, true, PRIMARY, 150);
        writer.block(writer.context.getString(R.string.pdf_link_help), MARGIN, 654, 9,
            false, MUTED, CONTENT_WIDTH, 3, 3);
    }

    private Map<String, Bitmap> loadImages(Itinerary itinerary) {
        Set<String> urls = new HashSet<>();
        for (ItineraryDay day : itinerary.getDays()) {
            for (ItineraryStop stop : day.getStops()) {
                if (!isBlank(stop.getImageUrl())) urls.add(stop.getImageUrl());
            }
        }
        if (urls.isEmpty()) return Collections.emptyMap();
        Map<String, Bitmap> images = new ConcurrentHashMap<>();
        ExecutorService pool = Executors.newFixedThreadPool(Math.min(4, urls.size()));
        for (String url : urls) {
            pool.execute(() -> {
                Bitmap bitmap = downloadBitmap(url);
                if (bitmap != null) images.put(url, bitmap);
            });
        }
        pool.shutdown();
        try {
            pool.awaitTermination(Math.max(12, urls.size() * 2L), TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
        return new LinkedHashMap<>(images);
    }

    private Bitmap downloadBitmap(String urlText) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(urlText).openConnection();
            connection.setConnectTimeout(4500);
            connection.setReadTimeout(6500);
            connection.setInstanceFollowRedirects(true);
            try (InputStream input = connection.getInputStream();
                 ByteArrayOutputStream output = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int read;
                int total = 0;
                while ((read = input.read(buffer)) >= 0) {
                    total += read;
                    if (total > 6 * 1024 * 1024) return null;
                    output.write(buffer, 0, read);
                }
                byte[] bytes = output.toByteArray();
                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                BitmapFactory.decodeByteArray(bytes, 0, bytes.length, bounds);
                BitmapFactory.Options options = new BitmapFactory.Options();
                options.inSampleSize = 1;
                while (bounds.outWidth / options.inSampleSize > 520
                    || bounds.outHeight / options.inSampleSize > 420) {
                    options.inSampleSize *= 2;
                }
                return BitmapFactory.decodeByteArray(bytes, 0, bytes.length, options);
            }
        } catch (Exception ignored) {
            return null;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private void drawImageOrPlaceholder(Writer writer, Bitmap bitmap, RectF destination, float radius) {
        Path clip = new Path();
        clip.addRoundRect(destination, radius, radius, Path.Direction.CW);
        int save = writer.canvas.save();
        writer.canvas.clipPath(clip);
        if (bitmap == null || bitmap.isRecycled()) {
            writer.paint.setColor(Color.rgb(221, 235, 229));
            writer.canvas.drawRect(destination, writer.paint);
            writer.center(writer.context.getString(R.string.pdf_no_image), destination.centerX(),
                destination.centerY() + 3, 8, true, PRIMARY);
        } else {
            float sourceRatio = bitmap.getWidth() / (float) bitmap.getHeight();
            float destinationRatio = destination.width() / destination.height();
            Rect source;
            if (sourceRatio > destinationRatio) {
                int sourceWidth = Math.round(bitmap.getHeight() * destinationRatio);
                int left = (bitmap.getWidth() - sourceWidth) / 2;
                source = new Rect(left, 0, left + sourceWidth, bitmap.getHeight());
            } else {
                int sourceHeight = Math.round(bitmap.getWidth() / destinationRatio);
                int top = (bitmap.getHeight() - sourceHeight) / 2;
                source = new Rect(0, top, bitmap.getWidth(), top + sourceHeight);
            }
            writer.canvas.drawBitmap(bitmap, source, destination, writer.paint);
        }
        writer.canvas.restoreToCount(save);
    }

    private static int countPlaces(Itinerary itinerary) {
        int count = 0;
        for (ItineraryDay day : itinerary.getDays()) {
            for (ItineraryStop stop : day.getStops()) {
                if (stop.getType() != ItineraryStop.Type.ACCOMMODATION) count++;
            }
        }
        return count;
    }

    private static double totalDistance(Itinerary itinerary) {
        double total = 0;
        for (ItineraryDay day : itinerary.getDays()) total += totalDistance(day);
        return total;
    }

    private static double totalDistance(ItineraryDay day) {
        double total = 0;
        for (ItineraryStop stop : day.getStops()) {
            total += Math.max(0, stop.getTravelToNextKm());
        }
        return total;
    }

    private static String typeLabel(Context context, ItineraryStop stop) {
        if (stop.getType() == ItineraryStop.Type.ACCOMMODATION) {
            return context.getString(R.string.starting_location_type);
        }
        if (stop.getType() == ItineraryStop.Type.EATERY) {
            return context.getString(R.string.eatery_stop_type);
        }
        return context.getString(R.string.poi_stop_type);
    }

    private static String reviewLabel(Context context, String url) {
        String lower = url.toLowerCase(Locale.US);
        if (lower.contains("tiktok.com")) return context.getString(R.string.pdf_review_tiktok);
        if (lower.contains("google.com") || lower.contains("goo.gl")) {
            return context.getString(R.string.pdf_review_google);
        }
        return context.getString(R.string.pdf_review_link);
    }

    private static String formatDate(String value) {
        try {
            Date date = new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(value);
            return new SimpleDateFormat("EEE, dd/MM", Locale.getDefault()).format(date);
        } catch (Exception ignored) {
            return value;
        }
    }

    private static String money(long value) {
        return NumberFormat.getNumberInstance(Locale.getDefault()).format(Math.max(0, value)) + " đ";
    }

    private static String distance(double value) {
        return String.format(Locale.getDefault(), "%.1f km", Math.max(0, value));
    }

    private static String joinNonEmpty(String separator, String... values) {
        StringBuilder result = new StringBuilder();
        for (String value : values) {
            if (isBlank(value)) continue;
            if (result.length() > 0) result.append(separator);
            result.append(value);
        }
        return result.toString();
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty() || "null".equalsIgnoreCase(value.trim());
    }

    private static String safe(String value) {
        return isBlank(value) ? "" : value.trim();
    }

    private static final class Writer {
        private final PdfDocument document;
        private final Context context;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private PdfDocument.Page page;
        private Canvas canvas;
        private int pageNumber;

        private Writer(PdfDocument document, Context context) {
            this.document = document;
            this.context = context;
        }

        private void newPage() {
            if (page != null) closePage();
            pageNumber++;
            page = document.startPage(new PdfDocument.PageInfo.Builder(
                PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create());
            canvas = page.getCanvas();
            canvas.drawColor(BACKGROUND);
        }

        private void closePage() {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(Color.rgb(218, 224, 221));
            canvas.drawRect(MARGIN, 812, PAGE_WIDTH - MARGIN, 813, paint);
            single(context.getString(R.string.pdf_page, pageNumber), MARGIN, 828,
                7, false, MUTED, CONTENT_WIDTH);
            document.finishPage(page);
            page = null;
        }

        private void finish() {
            if (page != null) closePage();
        }

        private void configure(float size, boolean bold, int color) {
            paint.setStyle(Paint.Style.FILL);
            paint.setTextSize(size);
            paint.setColor(color);
            paint.setTypeface(Typeface.create("sans-serif", bold ? Typeface.BOLD : Typeface.NORMAL));
        }

        private void single(String value, float x, float baseline, float size, boolean bold,
                            int color, float maxWidth) {
            fit(value, x, baseline, size, Math.max(4.5f, size * 0.72f), bold, color, maxWidth);
        }

        private void fit(String value, float x, float baseline, float size, float minSize,
                         boolean bold, int color, float maxWidth) {
            if (isBlank(value)) return;
            float current = size;
            configure(current, bold, color);
            while (current > minSize && paint.measureText(value) > maxWidth) {
                current -= 0.25f;
                configure(current, bold, color);
            }
            canvas.drawText(value, x, baseline, paint);
        }

        private int block(String value, float x, float top, float size, boolean bold, int color,
                          float maxWidth, int maxLines, float lineGap) {
            if (isBlank(value)) return Math.round(top);
            configure(size, bold, color);
            List<String> lines = wrap(value, maxWidth, maxLines);
            float lineHeight = size + lineGap;
            float baseline = top - paint.ascent();
            for (String line : lines) {
                canvas.drawText(line, x, baseline, paint);
                baseline += lineHeight;
            }
            return Math.round(top + lines.size() * lineHeight);
        }

        private List<String> wrap(String value, float maxWidth, int maxLines) {
            List<String> lines = new ArrayList<>();
            for (String word : value.replace('\n', ' ').trim().split("\\s+")) {
                if (lines.isEmpty()) {
                    lines.add(word);
                    continue;
                }
                int last = lines.size() - 1;
                String candidate = lines.get(last) + " " + word;
                if (paint.measureText(candidate) <= maxWidth) {
                    lines.set(last, candidate);
                } else if (lines.size() < maxLines) {
                    lines.add(word);
                } else {
                    String clipped = lines.get(last);
                    while (!clipped.isEmpty() && paint.measureText(clipped + "...") > maxWidth) {
                        clipped = clipped.substring(0, clipped.length() - 1);
                    }
                    lines.set(last, clipped + "...");
                    break;
                }
            }
            return lines;
        }

        private void center(String value, float centerX, float baseline, float size,
                            boolean bold, int color) {
            configure(size, bold, color);
            canvas.drawText(value, centerX - paint.measureText(value) / 2f, baseline, paint);
        }

        private void fillRoundRect(int x, int y, int width, int height, float radius, int color) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(color);
            canvas.drawRoundRect(x, y, x + width, y + height, radius, radius, paint);
        }

        private void strokeRoundRect(int x, int y, int width, int height, float radius,
                                     int color, float strokeWidth) {
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(strokeWidth);
            paint.setColor(color);
            canvas.drawRoundRect(x, y, x + width, y + height, radius, radius, paint);
            paint.setStyle(Paint.Style.FILL);
        }

        private void fillCircle(float x, float y, float radius, int color) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(color);
            canvas.drawCircle(x, y, radius, paint);
        }
    }
}
