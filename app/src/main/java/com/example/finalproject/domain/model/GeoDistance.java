package com.example.finalproject.domain.model;

/** Great-circle distance between two coordinates. Shared so the formula lives in one place. */
public final class GeoDistance {
    private static final double EARTH_RADIUS_KM = 6371.0;

    private GeoDistance() { }

    /**
     * Haversine distance in kilometres. This is straight-line distance, not road distance:
     * good enough for ordering stops and for checking how far the user is from the city,
     * while the real driving route comes from OSRM on the map screen.
     */
    public static double kilometresBetween(double fromLatitude, double fromLongitude,
                                           double toLatitude, double toLongitude) {
        double deltaLatitude = Math.toRadians(toLatitude - fromLatitude);
        double deltaLongitude = Math.toRadians(toLongitude - fromLongitude);
        double value = Math.sin(deltaLatitude / 2) * Math.sin(deltaLatitude / 2)
            + Math.cos(Math.toRadians(fromLatitude)) * Math.cos(Math.toRadians(toLatitude))
            * Math.sin(deltaLongitude / 2) * Math.sin(deltaLongitude / 2);
        return EARTH_RADIUS_KM * 2 * Math.atan2(Math.sqrt(value), Math.sqrt(1 - value));
    }
}
