package com.example.finalproject.domain.model;

import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class ItineraryEditorTest {
    @Test public void replace_updatesStopCostAndDistancesWithoutChangingOtherDays() {
        ItineraryStop hotel = new ItineraryStop(0, ItineraryStop.Type.ACCOMMODATION,
            "Chợ Đà Lạt", "Đà Lạt", 11.9429, 108.4368, 1, null);
        ItineraryStop oldEatery = new ItineraryStop(10, ItineraryStop.Type.EATERY,
            "Quán cũ", "Đà Lạt", 11.95, 108.45, 1, "morning", null,
            4.2, 100_000, null, null, null, null);
        ItineraryDay dayOne = new ItineraryDay(1, Arrays.asList(hotel, oldEatery));
        ItineraryDay dayTwo = new ItineraryDay(2, Collections.singletonList(hotel));
        Itinerary source = new Itinerary("Test", Arrays.asList(dayOne, dayTwo),
            1_000_000, 400_000, false);
        Place replacement = new Place(20, "EATERY", "Quán mới", "Đà Lạt", 5,
            20_000, "image", 11.97, 108.47, "6:00-20:00", "morning",
            null, "https://www.tiktok.com/test");

        Itinerary edited = ItineraryEditor.replace(source, 1, 1, replacement);

        assertEquals("Quán mới", edited.getDays().get(0).getStops().get(1).getName());
        assertEquals(320_000, edited.getEstimatedCostVnd());
        assertTrue(edited.getDays().get(0).getStops().get(0).getTravelToNextKm() > 0);
        assertEquals(source.getDays().get(1), edited.getDays().get(1));
    }
}
