package com.example.finalproject.infrastructure.local.repository;

import android.content.Context;

import com.example.finalproject.domain.model.Place;
import com.example.finalproject.infrastructure.local.AssetCatalogSeed;
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
    /**
     * Timestamp written on rows that came from the bundled asset rather than from Django.
     * Zero keeps lastUpdatedAt() honest: nothing has actually been downloaded yet.
     */
    private static final long BUNDLED_AT = 0L;

    private final Context context;
    private final CachedPlaceDao dao;

    public PlaceCache(Context context) {
        this.context = context.getApplicationContext();
        this.dao = JournifyDatabase.get(context).cachedPlaceDao();
    }

    /**
     * Loads the catalog shipped inside the APK when this language has no rows yet.
     *
     * Without it a first launch with no reachable backend has nothing to fall back on, because
     * the cache only ever fills from a successful download. Blocking, like the rest of this
     * class, so callers must already be off the main thread.
     */
    public void seedFromAssetsIfEmpty() {
        String language = currentLanguage();
        if (dao.count("POI", language) > 0 || dao.count("EATERY", language) > 0) return;

        List<Place> bundled = AssetCatalogSeed.load(context);
        if (bundled.isEmpty()) return;
        // The type argument is unused here: every bundled row carries its own type.
        save("POI", bundled, BUNDLED_AT);
    }

    /** The catalog is stored per language because Django returns localized names and addresses. */
    public static String currentLanguage() {
        return "en".equals(Locale.getDefault().getLanguage()) ? "en" : "vi";
    }

    public void save(String type, List<Place> places) {
        save(type, places, System.currentTimeMillis());
    }

    private void save(String type, List<Place> places, long cachedAt) {
        if (places.isEmpty()) return;
        String language = currentLanguage();
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
            row.cachedAt = cachedAt;
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
