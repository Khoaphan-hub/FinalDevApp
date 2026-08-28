package com.example.finalproject.infrastructure.local.dao;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import com.example.finalproject.infrastructure.local.entity.SavedTripEntity;

import java.util.List;

@Dao
public interface SavedTripDao {
    @Insert long insert(SavedTripEntity entity);
    @Query("SELECT * FROM saved_trips ORDER BY savedAt DESC") List<SavedTripEntity> loadAll();
}
