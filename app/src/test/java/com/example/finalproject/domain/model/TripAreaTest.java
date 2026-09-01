package com.example.finalproject.domain.model;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TripAreaTest {

    @Test public void centreItselfIsInsideTheServiceArea() {
        assertTrue(TripArea.isInsideServiceArea(TripArea.CENTRE_LATITUDE, TripArea.CENTRE_LONGITUDE));
        assertEquals(0.0, TripArea.distanceFromCentreKm(
            TripArea.CENTRE_LATITUDE, TripArea.CENTRE_LONGITUDE), 0.001);
    }

    @Test public void nearbyDaLatSpotsAreInsideTheServiceArea() {
        // Ho Xuan Huong lake, a few hundred metres from the market.
        assertTrue(TripArea.isInsideServiceArea(11.9430, 108.4450));
        // Trai Mat, on the far edge of the city but still a normal place to stay.
        assertTrue(TripArea.isInsideServiceArea(11.9560, 108.5180));
    }

    @Test public void hoChiMinhCityIsOutsideTheServiceArea() {
        assertFalse(TripArea.isInsideServiceArea(10.7769, 106.7009));
    }

    @Test public void distanceToHoChiMinhCityIsRoughlyTwoHundredThirtyKilometres() {
        double distance = TripArea.distanceFromCentreKm(10.7769, 106.7009);
        // Great-circle Da Lat to Saigon is about 231 km. Road distance is longer (~300 km);
        // the band is wide so the test checks the formula, not the exact city coordinates.
        assertTrue("distance was " + distance, distance > 220 && distance < 240);
    }

    @Test public void distanceIsSymmetric() {
        double there = GeoDistance.kilometresBetween(11.9404, 108.4583, 10.7769, 106.7009);
        double back = GeoDistance.kilometresBetween(10.7769, 106.7009, 11.9404, 108.4583);
        assertEquals(there, back, 0.001);
    }
}
