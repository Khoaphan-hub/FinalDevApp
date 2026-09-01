package com.example.finalproject.infrastructure.local.entity;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * A catalog entry that was downloaded from Django and kept for offline use.
 *
 * Nothing is shipped inside the APK: the table starts empty and fills up as the user browses,
 * so the download cost is paid once at runtime instead of by every person installing the app.
 * Names and addresses are language specific, so the same place is cached once per language.
 */
@Entity(tableName = "cached_places", indices = {@Index({"type", "language"})})
public class CachedPlaceEntity {
    /** "POI-12-vi" — the place, its catalog and the language it was downloaded in. */
    @PrimaryKey
    @NonNull
    public String cacheKey = "";

    public int placeId;
    public String type;
    public String language;
    public String name;
    public String address;
    public double rating;
    public long priceVnd;
    public String imageUrl;
    public double latitude;
    public double longitude;
    public String openHours;
    public String tags;
    public String highlight;
    public String mediaUrl;
    /** Lets the UI say how old the offline copy is. */
    public long cachedAt;

    public static String keyOf(int placeId, String type, String language) {
        return type + "-" + placeId + "-" + language;
    }
}
