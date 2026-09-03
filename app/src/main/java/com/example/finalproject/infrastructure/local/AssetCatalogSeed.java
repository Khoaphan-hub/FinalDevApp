package com.example.finalproject.infrastructure.local;

import android.content.Context;
import android.content.res.AssetManager;

import com.example.finalproject.domain.model.Place;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * Reads the catalog copy bundled inside the APK.
 *
 * A freshly installed app has an empty Room cache, so if Django cannot be reached on first
 * launch there is nothing to show: Explore renders an error and trip generation falls back to
 * nine hardcoded sample places. This asset closes that gap with the real 242 places.
 *
 * The file is produced by `python manage.py export_catalog_seed`, which runs the same
 * serializer the catalog endpoint uses, so the field names here match a live response and the
 * parsing below mirrors RemoteCatalogRepository.
 */
public final class AssetCatalogSeed {
    public static final String ASSET_NAME = "catalog_seed.json";

    private AssetCatalogSeed() { }

    /**
     * Parses the bundled catalog. Blocking: callers must already be off the main thread.
     * Returns an empty list if the asset is missing or unreadable, so a packaging mistake
     * degrades to the previous behaviour instead of crashing.
     */
    public static List<Place> load(Context context) {
        AssetManager assets = context.getApplicationContext().getAssets();
        String json;
        try (InputStream stream = assets.open(ASSET_NAME)) {
            json = readFully(stream);
        } catch (Exception missingOrUnreadable) {
            return Collections.emptyList();
        }

        try {
            // Picking name/name_en per locale here, rather than storing two files, keeps the
            // asset small and matches how a live response is localized.
            boolean english = "en".equals(Locale.getDefault().getLanguage());
            JSONArray items = new JSONObject(json).getJSONArray("items");
            List<Place> places = new ArrayList<>(items.length());
            for (int i = 0; i < items.length(); i++) {
                JSONObject item = items.getJSONObject(i);
                places.add(new Place(
                    item.optInt("id"),
                    item.optString("type"),
                    localized(item, english ? "name_en" : "name", "name"),
                    localized(item, english ? "address_en" : "address", "address"),
                    item.optDouble("rating", 0),
                    Math.round(item.optDouble("price", 0)),
                    nullable(item, "image_url"),
                    item.optDouble("latitude", 0),
                    item.optDouble("longitude", 0),
                    nullable(item, "open_hours"),
                    nullable(item, "tags"),
                    nullable(item, "highlight"),
                    nullable(item, "media_url"),
                    localized(item, "map_name", "name"),
                    localized(item, "map_address", "address")));
            }
            return places;
        } catch (Exception malformed) {
            return Collections.emptyList();
        }
    }

    private static String readFully(InputStream stream) throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[8192];
        int read;
        while ((read = stream.read(chunk)) != -1) {
            buffer.write(chunk, 0, read);
        }
        return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
    }

    private static String nullable(JSONObject object, String key) {
        return object.isNull(key) ? null : object.optString(key, null);
    }

    private static String localized(JSONObject object, String preferred, String fallback) {
        String value = nullable(object, preferred);
        return value == null || value.trim().isEmpty() ? object.optString(fallback) : value;
    }
}
