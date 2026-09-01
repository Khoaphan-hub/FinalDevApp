package com.example.finalproject.domain.model;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class OfflineItineraryBuilderTest {

    private static final OfflineItineraryBuilder.Labels LABELS =
        new OfflineItineraryBuilder.Labels("Trip", "Da Lat Market", "Start", "Return");

    private static Place poi(int id, String name, String tags, double latitude, long price) {
        return new Place(id, "POI", name, "address " + id, 4.0, price, null,
            latitude, 108.45, "08:00 - 17:00", tags, null, null);
    }

    private static Place eatery(int id, String name, String timeTags) {
        return new Place(id, "EATERY", name, "address " + id, 4.5, 90_000L, null,
            11.94, 108.45, "07:00 - 21:00", timeTags, null, null);
    }

    private static TripRequest request(int days, int perDay, List<Mood> moods) {
        return new TripRequest(days, perDay, 3_000_000L, moods, "", true,
            Collections.emptyList(), Collections.emptyList());
    }

    private static List<Place> samplePois() {
        return new ArrayList<>(Arrays.asList(
            poi(1, "Lake", "Pure nature", 11.941, 0L),
            poi(2, "Museum", "History, Manmade", 11.950, 100_000L),
            poi(3, "Pine hill", "50% nature", 11.960, 50_000L),
            poi(4, "Old cafe", "Cafe, Eating", 11.970, 70_000L),
            poi(5, "Temple", "Spiritual, Healing", 11.980, 0L)));
    }

    @Test public void returnsNullWhenNothingIsCached() {
        assertNull("with no cached places the caller must fall back to the built-in sample",
            OfflineItineraryBuilder.build(request(2, 2, Collections.singletonList(Mood.RELAXED)),
                OfflineItineraryBuilder.none(), OfflineItineraryBuilder.none(), LABELS));
    }

    @Test public void buildsOneDayPerRequestedDay() {
        Itinerary itinerary = OfflineItineraryBuilder.build(
            request(3, 2, Collections.singletonList(Mood.RELAXED)),
            samplePois(), Collections.singletonList(eatery(9, "Pho", "morning")), LABELS);

        assertNotNull(itinerary);
        assertEquals(3, itinerary.getDays().size());
        assertTrue("an offline plan must be flagged so the UI can label it",
            itinerary.isOfflineDemo());
    }

    @Test public void everyDayStartsAndEndsAtTheAccommodation() {
        Itinerary itinerary = OfflineItineraryBuilder.build(
            request(2, 2, Collections.singletonList(Mood.CULTURE)),
            samplePois(), Collections.singletonList(eatery(9, "Pho", "afternoon")), LABELS);

        assertNotNull(itinerary);
        for (ItineraryDay day : itinerary.getDays()) {
            List<ItineraryStop> stops = day.getStops();
            assertEquals(ItineraryStop.Type.ACCOMMODATION, stops.get(0).getType());
            assertEquals(ItineraryStop.Type.ACCOMMODATION, stops.get(stops.size() - 1).getType());
        }
    }

    @Test public void prefersPlacesMatchingTheChosenMood() {
        Itinerary itinerary = OfflineItineraryBuilder.build(
            request(1, 2, Collections.singletonList(Mood.CULTURE)),
            samplePois(), OfflineItineraryBuilder.none(), LABELS);

        assertNotNull(itinerary);
        List<String> names = new ArrayList<>();
        for (ItineraryStop stop : itinerary.getDays().get(0).getStops()) {
            if (stop.getType() == ItineraryStop.Type.POI) names.add(stop.getName());
        }
        assertTrue("Culture maps to History/Manmade, so the museum must be picked",
            names.contains("Museum"));
    }

    @Test public void topsUpWithOtherPlacesWhenTheMoodHasTooFewMatches() {
        // Only one place is tagged Bizarre-adjacent, yet four slots need filling.
        Itinerary itinerary = OfflineItineraryBuilder.build(
            request(2, 2, Collections.singletonList(Mood.BIZARRE)),
            samplePois(), OfflineItineraryBuilder.none(), LABELS);

        assertNotNull(itinerary);
        int poiStops = 0;
        for (ItineraryDay day : itinerary.getDays()) {
            for (ItineraryStop stop : day.getStops()) {
                if (stop.getType() == ItineraryStop.Type.POI) poiStops++;
            }
        }
        assertEquals("each day must still be filled to the requested size", 4, poiStops);
    }

    @Test public void addsAnEateryWithItsServingSlot() {
        Itinerary itinerary = OfflineItineraryBuilder.build(
            request(1, 2, Collections.singletonList(Mood.FOODIE)),
            samplePois(), Collections.singletonList(eatery(9, "Pho", "morning,evening")), LABELS);

        assertNotNull(itinerary);
        ItineraryStop meal = null;
        for (ItineraryStop stop : itinerary.getDays().get(0).getStops()) {
            if (stop.getType() == ItineraryStop.Type.EATERY) meal = stop;
        }
        assertNotNull("a day should include a meal when eateries are cached", meal);
        assertEquals("morning", meal.getMealSlot());
    }

    @Test public void estimatesCostFromCachedPrices() {
        Itinerary itinerary = OfflineItineraryBuilder.build(
            request(1, 2, Collections.singletonList(Mood.CULTURE)),
            samplePois(), Collections.singletonList(eatery(9, "Pho", "afternoon")), LABELS);

        assertNotNull(itinerary);
        long expected = 0;
        for (ItineraryStop stop : itinerary.getDays().get(0).getStops()) {
            if (stop.getType() != ItineraryStop.Type.ACCOMMODATION) expected += stop.getPriceVnd();
        }
        assertEquals(expected, itinerary.getEstimatedCostVnd());
    }

    @Test public void carriesPhotosAndDetailsIntoTheStops() {
        List<Place> pois = Collections.singletonList(
            new Place(7, "POI", "Valley", "somewhere", 4.2, 120_000L, "http://host/p.png",
                11.95, 108.46, "08:00 - 18:00", "Pure nature", "worth it", "http://tiktok"));

        Itinerary itinerary = OfflineItineraryBuilder.build(
            request(1, 1, Collections.singletonList(Mood.RELAXED)), pois,
            OfflineItineraryBuilder.none(), LABELS);

        assertNotNull(itinerary);
        ItineraryStop stop = itinerary.getDays().get(0).getStops().get(1);
        assertEquals("http://host/p.png", stop.getImageUrl());
        assertEquals("worth it", stop.getHighlight());
        assertEquals(120_000L, stop.getPriceVnd());
    }
}
