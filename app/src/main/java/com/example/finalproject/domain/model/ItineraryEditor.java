package com.example.finalproject.domain.model;

import java.util.ArrayList;
import java.util.List;

public final class ItineraryEditor {
    private ItineraryEditor() {}

    public static org.json.JSONObject toJson(Itinerary itinerary) {
        org.json.JSONObject json = new org.json.JSONObject();
        try {
            json.put("budget_amount", itinerary.getTotalBudgetVnd());
            json.put("budget_remaining", itinerary.getRemainingBudgetVnd());
            org.json.JSONObject results = new org.json.JSONObject();
            for (ItineraryDay day : itinerary.getDays()) {
                org.json.JSONArray stopsArr = new org.json.JSONArray();
                for (ItineraryStop stop : day.getStops()) {
                    org.json.JSONObject stopObj = new org.json.JSONObject();
                    stopObj.put("id", stop.getId());
                    stopObj.put("type", stop.getType().name());
                    stopObj.put("name", stop.getName());
                    stopObj.put("address", stop.getAddress());
                    if (!Double.isNaN(stop.getLatitude())) stopObj.put("latitude", stop.getLatitude());
                    if (!Double.isNaN(stop.getLongitude())) stopObj.put("longitude", stop.getLongitude());
                    if (stop.getMealSlot() != null) stopObj.put("meal_slot", stop.getMealSlot());
                    if (stop.getImageUrl() != null) stopObj.put("image_url", stop.getImageUrl());
                    stopObj.put("price", stop.getPriceVnd());
                    if (!Double.isNaN(stop.getRating())) stopObj.put("rating", stop.getRating());
                    if (stop.getOpenHours() != null) stopObj.put("open_hours", stop.getOpenHours());
                    if (stop.getTags() != null) stopObj.put("tags", stop.getTags());
                    if (stop.getHighlight() != null) stopObj.put("highlight", stop.getHighlight());
                    stopsArr.put(stopObj);
                }
                results.put(String.valueOf(day.getDayNumber()), stopsArr);
            }
            json.put("results", results);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return json;
    }

    public static Itinerary rename(Itinerary source, String newTitle) {
        return new Itinerary(newTitle, source.getDays(), source.getTotalBudgetVnd(), source.getEstimatedCostVnd(), source.isOfflineDemo());
    }

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
                place.getHighlight(), place.getMediaUrl(), place.getMapName(), place.getMapAddress()));
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
                current.getOpenHours(), current.getTags(), current.getHighlight(), current.getMediaUrl(),
                current.getMapName(), current.getMapAddress()));
        }
        return result;
    }

    private static double haversine(ItineraryStop a, ItineraryStop b) {
        return GeoDistance.kilometresBetween(a.getLatitude(), a.getLongitude(),
            b.getLatitude(), b.getLongitude());
    }
}
