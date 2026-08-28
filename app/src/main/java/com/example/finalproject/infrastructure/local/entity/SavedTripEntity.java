package com.example.finalproject.infrastructure.local.entity;

import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "saved_trips")
public class SavedTripEntity {
    @PrimaryKey(autoGenerate = true)
    public long id;
    public String title;
    public long savedAt;
    public byte[] itineraryPayload;

    public SavedTripEntity(String title, long savedAt, byte[] itineraryPayload) {
        this.title = title;
        this.savedAt = savedAt;
        this.itineraryPayload = itineraryPayload;
    }
}
