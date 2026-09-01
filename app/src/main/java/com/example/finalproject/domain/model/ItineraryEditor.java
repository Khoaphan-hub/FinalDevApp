package com.example.finalproject.domain.model;

import java.util.ArrayList;
import java.util.List;

public final class ItineraryEditor {
    private ItineraryEditor() {}

    public static Itinerary replace(Itinerary source, int dayNumber, int stopIndex, Place place) {
        List<ItineraryDay> days = new ArrayList<>();
        long oldPrice = 0;
        for (ItineraryDay day : source.getDays()) {
            if (day.getDayNumber() != dayNumber) {
                days.add(day);
                continue;
            }
            List<ItineraryStop> stops = new ArrayList<>(day.getStops());
            ItineraryStop old = stops.get(stopIndex);
            oldPrice = old.getPriceVnd();
            ItineraryStop.Type type = "EATERY".equals(place.getType())
                ? ItineraryStop.Type.EATERY : ItineraryStop.Type.POI;
            stops.set(stopIndex, new ItineraryStop(place.getId(), type, place.getName(), place.getAddress(),
                place.getLatitude(), place.getLongitude(), 0, old.getMealSlot(), place.getImageUrl(),
                place.getRating(), place.getPriceVnd(), place.getOpenHours(), place.getTags(),
                place.getHighlight(), place.getMediaUrl()));
            days.add(new ItineraryDay(dayNumber, recalculateDistances(stops)));
        }
        long estimate = Math.max(0, source.getEstimatedCostVnd() - oldPrice + place.getPriceVnd());
        return new Itinerary(source.getTitle(), days, source.getTotalBudgetVnd(), estimate, source.isOfflineDemo());
    }

    private static List<ItineraryStop> recalculateDistances(List<ItineraryStop> stops) {
        List<ItineraryStop> result = new ArrayList<>();
        for (int i = 0; i < stops.size(); i++) {
            ItineraryStop current = stops.get(i);
            double distance = i + 1 < stops.size() ? haversine(current, stops.get(i + 1)) : 0;
            result.add(new ItineraryStop(current.getId(), current.getType(), current.getName(),
                current.getAddress(), current.getLatitude(), current.getLongitude(), distance,
                current.getMealSlot(), current.getImageUrl(), current.getRating(), current.getPriceVnd(),
                current.getOpenHours(), current.getTags(), current.getHighlight(), current.getMediaUrl()));
        }
        return result;
    }

    private static double haversine(ItineraryStop a, ItineraryStop b) {
        return GeoDistance.kilometresBetween(a.getLatitude(), a.getLongitude(),
            b.getLatitude(), b.getLongitude());
    }
}
