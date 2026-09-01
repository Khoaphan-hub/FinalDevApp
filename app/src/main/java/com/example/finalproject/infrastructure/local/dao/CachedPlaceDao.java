package com.example.finalproject.infrastructure.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.example.finalproject.infrastructure.local.entity.CachedPlaceEntity;

import java.util.List;

@Dao
public interface CachedPlaceDao {
    /** A fresh download replaces what was stored, so prices and names never go stale silently. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void upsertAll(List<CachedPlaceEntity> places);

    @Query("SELECT * FROM cached_places WHERE type = :type AND language = :language"
        + " ORDER BY rating DESC, name ASC LIMIT :limit")
    List<CachedPlaceEntity> byType(String type, String language, int limit);

    /**
     * Offline search. Django matches accent-insensitively with a Trie; SQLite LIKE cannot do
     * that, so this is a plain substring match and the UI says results are limited offline.
     */
    @Query("SELECT * FROM cached_places WHERE type = :type AND language = :language"
        + " AND (name LIKE '%' || :query || '%' OR address LIKE '%' || :query || '%')"
        + " ORDER BY rating DESC, name ASC LIMIT :limit")
    List<CachedPlaceEntity> search(String type, String language, String query, int limit);

    @Query("SELECT COUNT(*) FROM cached_places WHERE type = :type AND language = :language")
    int count(String type, String language);

    @Query("SELECT MAX(cachedAt) FROM cached_places WHERE language = :language")
    Long lastUpdatedAt(String language);
}
