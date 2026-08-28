package com.example.finalproject.domain.model;

import java.io.Serializable;

public final class ItineraryStop implements Serializable {
    private static final long serialVersionUID = 1L;
    public enum Type { ACCOMMODATION, POI, EATERY }

    private final int id;
    private final Type type;
    private final String name;
    private final String address;
    private final double latitude;
    private final double longitude;
    private final double travelToNextKm;
    private final String mealSlot;
    private final String imageUrl;
    private final double rating;
    private final long priceVnd;
    private final String openHours;
    private final String tags;
    private final String highlight;
    private final String mediaUrl;

    public ItineraryStop(int id, Type type, String name, String address, double latitude,
                         double longitude, double travelToNextKm, String mealSlot) {
        this(id, type, name, address, latitude, longitude, travelToNextKm, mealSlot, null);
    }

    public ItineraryStop(int id, Type type, String name, String address, double latitude,
                         double longitude, double travelToNextKm, String mealSlot, String imageUrl) {
        this(id, type, name, address, latitude, longitude, travelToNextKm, mealSlot, imageUrl,
            0, 0, null, null, null, null);
    }

    public ItineraryStop(int id, Type type, String name, String address, double latitude,
                         double longitude, double travelToNextKm, String mealSlot, String imageUrl,
                         double rating, long priceVnd, String openHours, String tags,
                         String highlight, String mediaUrl) {
        this.id = id;
        this.type = type;
        this.name = name;
        this.address = address;
        this.latitude = latitude;
        this.longitude = longitude;
        this.travelToNextKm = travelToNextKm;
        this.mealSlot = mealSlot;
        this.imageUrl = imageUrl;
        this.rating = rating;
        this.priceVnd = priceVnd;
        this.openHours = openHours;
        this.tags = tags;
        this.highlight = highlight;
        this.mediaUrl = mediaUrl;
    }

    public int getId() { return id; }
    public Type getType() { return type; }
    public String getName() { return name; }
    public String getAddress() { return address; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public double getTravelToNextKm() { return travelToNextKm; }
    public String getMealSlot() { return mealSlot; }
    public String getImageUrl() { return imageUrl; }
    public double getRating() { return rating; }
    public long getPriceVnd() { return priceVnd; }
    public String getOpenHours() { return openHours; }
    public String getTags() { return tags; }
    public String getHighlight() { return highlight; }
    public String getMediaUrl() { return mediaUrl; }
}
