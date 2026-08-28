package com.example.finalproject.infrastructure.local.db;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.example.finalproject.infrastructure.local.dao.SavedTripDao;
import com.example.finalproject.infrastructure.local.entity.SavedTripEntity;

@Database(entities = {SavedTripEntity.class}, version = 1, exportSchema = false)
public abstract class JournifyDatabase extends RoomDatabase {
    private static volatile JournifyDatabase instance;
    public abstract SavedTripDao savedTripDao();

    public static JournifyDatabase get(Context context) {
        if (instance == null) {
            synchronized (JournifyDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(context.getApplicationContext(),
                        JournifyDatabase.class, "journify.db").build();
                }
            }
        }
        return instance;
    }
}
