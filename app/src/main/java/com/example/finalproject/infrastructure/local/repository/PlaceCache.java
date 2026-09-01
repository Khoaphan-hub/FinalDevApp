package com.example.finalproject.infrastructure.local.repository;

import android.content.Context;

import com.example.finalproject.domain.model.Place;
import com.example.finalproject.infrastructure.local.dao.CachedPlaceDao;
import com.example.finalproject.infrastructure.local.db.JournifyDatabase;
import com.example.finalproject.infrastructure.local.entity.CachedPlaceEntity;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Translates between the domain {@link Place} and the Room cache.
 *
 * Every method here blocks, so callers must already be off the main thread.
 */
public final class PlaceCache {
    private final CachedPlaceDao dao;

    public PlaceCache(Context context) {
        this.dao = JournifyDatabase.get(context).cachedPlaceDao();
    }

    /** The catalog is stored per language because Django returns localized names and addresses. */
    public static String currentLanguage() {
        return "en".equals(Locale.getDefault().getLanguage()) ? "en" : "vi";
    }

    public void save(String type, List<Place> places) {
        if (places.isEmpty()) return;
        String language = currentLanguage();
        long now = System.currentTimeMillis();
        List<CachedPlaceEntity> rows = new ArrayList<>(places.size());
        for (Place place : places) {
            CachedPlaceEntity row = new CachedPlaceEntity();
            row.placeId = place.getId();
            row.type = place.getType() == null ? type.toUpperCase(Locale.ROOT) : place.getType();
            row.language = language;
            row.cacheKey = CachedPlaceEntity.keyOf(row.placeId, row.type, language);
            row.name = place.getName();
            row.address = place.getAddress();
            row.rating = finite(place.getRating());
            row.priceVnd = place.getPriceVnd();
            row.imageUrl = place.getImageUrl();
            row.latitude = finite(place.getLatitude());
            row.longitude = finite(place.getLongitude());
            row.openHours = place.getOpenHours();
            row.tags = place.getTags();
            row.highlight = place.getHighlight();
            row.mediaUrl = place.getMediaUrl();
            row.cachedAt = now;
            rows.add(row);
        }
        dao.upsertAll(rows);
    }

    public List<Place> load(String type, String query, int limit) {
        String catalogType = type.toUpperCase(Locale.ROOT);
        String language = currentLanguage();
        List<CachedPlaceEntity> rows = query == null || query.trim().isEmpty()
            ? dao.byType(catalogType, language, limit)
            : dao.search(catalogType, language, query.trim(), limit);
        List<Place> places = new ArrayList<>(rows.size());
        for (CachedPlaceEntity row : rows) places.add(toPlace(row));
        return places;
    }

    /** Epoch millis of the newest cached row, or 0 when nothing has been downloaded yet. */
    public long lastUpdatedAt() {
        Long value = dao.lastUpdatedAt(currentLanguage());
        return value == null ? 0L : value;
    }

    /**
     * SQLite stores NaN as NULL, which the NOT NULL columns reject. A place with no rating
     * arrives from Django as JSON null, so guard every double before it reaches Room.
     */
    private static double finite(double value) {
        return Double.isNaN(value) || Double.isInfinite(value) ? 0 : value;
    }

    private Place toPlace(CachedPlaceEntity row) {
        return new Place(row.placeId, row.type, row.name, row.address, row.rating, row.priceVnd,
            row.imageUrl, row.latitude, row.longitude, row.openHours, row.tags, row.highlight,
            row.mediaUrl);
    }
}
