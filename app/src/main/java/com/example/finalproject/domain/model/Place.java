package com.example.finalproject.domain.model;

import java.io.Serializable;

public final class Place implements Serializable {
    private final int id;
    private final String type;
    private final String name;
    private final String address;
    private final double rating;
    private final long priceVnd;
    private final String imageUrl;
    private final double latitude;
    private final double longitude;
    private final String openHours;
    private final String tags;
    private final String highlight;
    private final String mediaUrl;

    public Place(int id, String type, String name, String address, double rating, long priceVnd,
                 String imageUrl, double latitude, double longitude, String openHours,
                 String tags, String highlight, String mediaUrl) {
        this.id = id;
        this.type = type;
        this.name = name;
        this.address = address;
        this.rating = rating;
        this.priceVnd = priceVnd;
        this.imageUrl = imageUrl;
        this.latitude = latitude;
        this.longitude = longitude;
        this.openHours = openHours;
        this.tags = tags;
        this.highlight = highlight;
        this.mediaUrl = mediaUrl;
    }

    public int getId() { return id; }
    public String getType() { return type; }
    public String getName() { return name; }
    public String getAddress() { return address; }
    public double getRating() { return rating; }
    public long getPriceVnd() { return priceVnd; }
    public String getImageUrl() { return imageUrl; }
    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }
    public String getOpenHours() { return openHours; }
    public String getTags() { return tags; }
    public String getHighlight() { return highlight; }
    public String getMediaUrl() { return mediaUrl; }
}
