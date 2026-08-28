package com.example.finalproject.domain.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ItineraryDay implements Serializable {
    private static final long serialVersionUID = 1L;
    private final int dayNumber;
    private final List<ItineraryStop> stops;

    public ItineraryDay(int dayNumber, List<ItineraryStop> stops) {
        this.dayNumber = dayNumber;
        this.stops = Collections.unmodifiableList(new ArrayList<>(stops));
    }

    public int getDayNumber() { return dayNumber; }
    public List<ItineraryStop> getStops() { return stops; }
}
