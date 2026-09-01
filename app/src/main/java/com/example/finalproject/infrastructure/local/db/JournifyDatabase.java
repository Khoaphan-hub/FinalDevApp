package com.example.finalproject.infrastructure.local.db;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.example.finalproject.infrastructure.local.dao.CachedPlaceDao;
import com.example.finalproject.infrastructure.local.dao.SavedTripDao;
import com.example.finalproject.infrastructure.local.entity.CachedPlaceEntity;
import com.example.finalproject.infrastructure.local.entity.SavedTripEntity;

@Database(entities = {SavedTripEntity.class, CachedPlaceEntity.class}, version = 2, exportSchema = false)
public abstract class JournifyDatabase extends RoomDatabase {
    private static volatile JournifyDatabase instance;
    public abstract SavedTripDao savedTripDao();
    public abstract CachedPlaceDao cachedPlaceDao();

    /**
     * Adds the offline catalog table. Written as a real migration rather than a destructive
     * fallback so that trips a user already saved survive the upgrade.
     */
    static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `cached_places` ("
                + "`cacheKey` TEXT NOT NULL, `placeId` INTEGER NOT NULL, `type` TEXT, "
                + "`language` TEXT, `name` TEXT, `address` TEXT, `rating` REAL NOT NULL, "
                + "`priceVnd` INTEGER NOT NULL, `imageUrl` TEXT, `latitude` REAL NOT NULL, "
                + "`longitude` REAL NOT NULL, `openHours` TEXT, `tags` TEXT, `highlight` TEXT, "
                + "`mediaUrl` TEXT, `cachedAt` INTEGER NOT NULL, PRIMARY KEY(`cacheKey`))");
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_cached_places_type_language` "
                + "ON `cached_places` (`type`, `language`)");
        }
    };

    public static JournifyDatabase get(Context context) {
        if (instance == null) {
            synchronized (JournifyDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(context.getApplicationContext(),
                            JournifyDatabase.class, "journify.db")
                        .addMigrations(MIGRATION_1_2)
                        .build();
                }
            }
        }
        return instance;
    }
}
