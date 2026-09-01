package com.example.finalproject.domain.model;

import java.io.Serializable;

/**
 * A position read from the device, already resolved to a human readable address.
 * Pure Java on purpose: the domain layer must not know about android.location.Location.
 */
public final class DeviceLocation implements Serializable {
    private static final long serialVersionUID = 1L;

    private final double latitude;
    private final double longitude;
    private final String address;

    public DeviceLocation(double latitude, double longitude, String address) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.address = address;
    }

    public double getLatitude() { return latitude; }
    public double getLongitude() { return longitude; }

    /** Never null: falls back to the raw coordinates when reverse geocoding finds no street address. */
    public String getAddress() { return address; }
}
