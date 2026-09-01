package com.example.finalproject.domain.model;

/**
 * The geographic area Journify actually has data for.
 *
 * The catalog only contains Da Lat attractions and eateries, so a starting point far outside
 * the city produces an itinerary whose first leg is hundreds of kilometres long. This class
 * lets the UI detect that case and ask the user before sending such a request to Django.
 */
public final class TripArea {
    /** Da Lat Market. Same value as DEFAULT_FALLBACK_COORDS in the Django backend constants. */
    public static final double CENTRE_LATITUDE = 11.9404;
    public static final double CENTRE_LONGITUDE = 108.4583;

    /**
     * Roughly covers Da Lat plus the surrounding Lam Dong communes people stay in
     * (Trai Mat, Tuyen Lam, Datanla). Anything past this is another province.
     */
    public static final double SERVICE_RADIUS_KM = 30.0;

    private TripArea() { }

    public static double distanceFromCentreKm(double latitude, double longitude) {
        return GeoDistance.kilometresBetween(CENTRE_LATITUDE, CENTRE_LONGITUDE, latitude, longitude);
    }

    public static boolean isInsideServiceArea(double latitude, double longitude) {
        return distanceFromCentreKm(latitude, longitude) <= SERVICE_RADIUS_KM;
    }
}
