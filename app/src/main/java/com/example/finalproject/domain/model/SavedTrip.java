package com.example.finalproject.domain.model;

public final class SavedTrip {
    private final long id;
    private final Itinerary itinerary;
    private final long savedAt;

    public SavedTrip(long id, Itinerary itinerary, long savedAt) {
        this.id = id;
        this.itinerary = itinerary;
        this.savedAt = savedAt;
    }

    public long getId() { return id; }
    public Itinerary getItinerary() { return itinerary; }
    public long getSavedAt() { return savedAt; }
}
