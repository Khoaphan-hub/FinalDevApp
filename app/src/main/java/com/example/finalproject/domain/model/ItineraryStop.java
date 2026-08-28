package com.example.finalproject.domain.model;

import java.io.Serializable;

public final class ItineraryStop implements Serializable {
    public enum Type { ACCOMMODATION, POI, EATERY }

    private final int id;
    private final Type type;
    private final String name;
    private final String address;
    private final double latitude;
    private final double longitude;
    private final double travelToNextKm;
    private final String mealSlot;

    public ItineraryStop(int id, Type type, String name, String address, double latitude,
                         double longitude, double travelToNextKm, String mealSlot) {
        this.id = id;
        this.type = type;
        this.name = name;
        this.address = address;
        this.latitude = latitude;
        this.longitude = longitude;
        this.travelToNextKm = travelToNextKm;
        this.mealSlot = mealSlot;
    }

    public int getId() { return id; }
    public Type getType() { return type; }
    public String getName() { return name; }
    public String getAddress() { return address; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public double getTravelToNextKm() { return travelToNextKm; }
    public String getMealSlot() { return mealSlot; }
}
