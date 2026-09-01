package com.example.finalproject.domain.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

/**
 * Builds an itinerary from places already downloaded to the device.
 *
 * This is the offline counterpart of the Django planner. It cannot reproduce the server's
 * clustering and budget solver, but it uses the same catalog, the same mood vocabulary and a
 * nearest-neighbour route, so an offline trip is made of real Da Lat places instead of a
 * hard-coded sample. Pure Java on purpose, so it can be unit tested without a device.
 */
public final class OfflineItineraryBuilder {
    /** Da Lat Market, the default centre the backend also falls back to. */
    public static final double DEFAULT_LATITUDE = 11.9404;
    public static final double DEFAULT_LONGITUDE = 108.4583;

    /** Text the caller supplies already localized, so this class stays free of Android resources. */
    public static final class Labels {
        public final String title;
        public final String accommodationName;
        public final String startLabel;
        public final String returnLabel;

        public Labels(String title, String accommodationName, String startLabel, String returnLabel) {
            this.title = title;
            this.accommodationName = accommodationName;
            this.startLabel = startLabel;
            this.returnLabel = returnLabel;
        }
    }

    private OfflineItineraryBuilder() { }

    /**
     * @return an itinerary flagged as offline, or null when there are no cached attractions to
     *         work with — the caller then falls back to the built-in sample.
     */
    public static Itinerary build(TripRequest request, List<Place> cachedPois,
                                  List<Place> cachedEateries, Labels labels) {
        List<Place> pois = pickByMood(cachedPois, MoodTags.forMoods(request.getMoods()),
            request.getDays() * request.getDailyPoiLimit());
        if (pois.isEmpty()) return null;

        double startLatitude = DEFAULT_LATITUDE;
        double startLongitude = DEFAULT_LONGITUDE;
        List<Place> route = orderByProximity(pois, startLatitude, startLongitude);
        List<Place> eateries = new ArrayList<>(cachedEateries);
        eateries.sort(Comparator.comparingDouble(Place::getRating).reversed());

        List<ItineraryDay> days = new ArrayList<>();
        long estimated = 0L;
        int poiCursor = 0;
        int eateryCursor = 0;

        for (int dayNumber = 1; dayNumber <= request.getDays(); dayNumber++) {
            List<ItineraryStop> stops = new ArrayList<>();
            stops.add(accommodation(labels.accommodationName, labels.startLabel,
                startLatitude, startLongitude));

            int mealAfter = Math.max(1, request.getDailyPoiLimit() / 2);
            for (int index = 0; index < request.getDailyPoiLimit(); index++) {
                if (route.isEmpty()) break;
                Place poi = route.get(poiCursor++ % route.size());
                stops.add(toStop(poi, ItineraryStop.Type.POI, null));
                estimated += poi.getPriceVnd();

                if (index + 1 == mealAfter && !eateries.isEmpty()) {
                    Place eatery = eateries.get(eateryCursor++ % eateries.size());
                    stops.add(toStop(eatery, ItineraryStop.Type.EATERY, slotOf(eatery)));
                    estimated += eatery.getPriceVnd();
                }
            }

            stops.add(accommodation(labels.accommodationName, labels.returnLabel,
                startLatitude, startLongitude));
            days.add(new ItineraryDay(dayNumber, withTravelDistances(stops)));
        }

        return new Itinerary(labels.title, days, request.getBudgetVnd(), estimated, true);
    }

    /**
     * Prefers places whose tags match the chosen moods, then tops the list up with the best
     * rated remainder so a narrow mood still produces a full trip.
     */
    private static List<Place> pickByMood(List<Place> candidates, Set<String> wantedTags, int needed) {
        List<Place> matching = new ArrayList<>();
        List<Place> rest = new ArrayList<>();
        for (Place place : candidates) {
            if (MoodTags.matches(place.getTags(), wantedTags)) matching.add(place);
            else rest.add(place);
        }
        matching.sort(Comparator.comparingDouble(Place::getRating).reversed());
        if (matching.size() >= needed) return matching;

        rest.sort(Comparator.comparingDouble(Place::getRating).reversed());
        for (Place place : rest) {
            if (matching.size() >= needed) break;
            matching.add(place);
        }
        return matching;
    }

    /** Greedy nearest neighbour: cheap, deterministic, and good enough to avoid zig-zag routes. */
    private static List<Place> orderByProximity(List<Place> places, double latitude, double longitude) {
        List<Place> remaining = new ArrayList<>(places);
        List<Place> ordered = new ArrayList<>(remaining.size());
        double currentLatitude = latitude;
        double currentLongitude = longitude;
        while (!remaining.isEmpty()) {
            int best = 0;
            double bestDistance = Double.MAX_VALUE;
            for (int index = 0; index < remaining.size(); index++) {
                Place candidate = remaining.get(index);
                double distance = GeoDistance.kilometresBetween(currentLatitude, currentLongitude,
                    candidate.getLatitude(), candidate.getLongitude());
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = index;
                }
            }
            Place chosen = remaining.remove(best);
            ordered.add(chosen);
            currentLatitude = chosen.getLatitude();
            currentLongitude = chosen.getLongitude();
        }
        return ordered;
    }

    /** Rewrites each stop with the straight-line distance to the stop that follows it. */
    private static List<ItineraryStop> withTravelDistances(List<ItineraryStop> stops) {
        List<ItineraryStop> result = new ArrayList<>(stops.size());
        for (int index = 0; index < stops.size(); index++) {
            ItineraryStop stop = stops.get(index);
            double distance = 0;
            if (index + 1 < stops.size()) {
                ItineraryStop next = stops.get(index + 1);
                distance = GeoDistance.kilometresBetween(stop.getLatitude(), stop.getLongitude(),
                    next.getLatitude(), next.getLongitude());
            }
            result.add(new ItineraryStop(stop.getId(), stop.getType(), stop.getName(),
                stop.getAddress(), stop.getLatitude(), stop.getLongitude(), distance,
                stop.getMealSlot(), stop.getImageUrl(), stop.getRating(), stop.getPriceVnd(),
                stop.getOpenHours(), stop.getTags(), stop.getHighlight(), stop.getMediaUrl()));
        }
        return result;
    }

    /** Eateries carry their serving times as tags; the first one decides where the meal sits. */
    private static String slotOf(Place eatery) {
        String tags = eatery.getTags();
        if (tags == null) return null;
        for (String slot : new String[]{"morning", "afternoon", "evening"}) {
            if (tags.toLowerCase(java.util.Locale.ROOT).contains(slot)) return slot;
        }
        return null;
    }

    private static ItineraryStop toStop(Place place, ItineraryStop.Type type, String mealSlot) {
        return new ItineraryStop(place.getId(), type, place.getName(), place.getAddress(),
            place.getLatitude(), place.getLongitude(), 0, mealSlot, place.getImageUrl(),
            place.getRating(), place.getPriceVnd(), place.getOpenHours(), place.getTags(),
            place.getHighlight(), place.getMediaUrl());
    }

    private static ItineraryStop accommodation(String name, String address,
                                               double latitude, double longitude) {
        return new ItineraryStop(0, ItineraryStop.Type.ACCOMMODATION, name, address,
            latitude, longitude, 0, null);
    }

    /** Exposed for tests that need a stable, empty catalog. */
    public static List<Place> none() {
        return Collections.emptyList();
    }
}
