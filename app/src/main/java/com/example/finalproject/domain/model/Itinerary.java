package com.example.finalproject.domain.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class Itinerary implements Serializable {
    private final String title;
    private final List<ItineraryDay> days;
    private final long totalBudgetVnd;
    private final long estimatedCostVnd;
    private final boolean offlineDemo;

    public Itinerary(String title, List<ItineraryDay> days, long totalBudgetVnd,
                     long estimatedCostVnd, boolean offlineDemo) {
        this.title = title;
        this.days = Collections.unmodifiableList(new ArrayList<>(days));
        this.totalBudgetVnd = totalBudgetVnd;
        this.estimatedCostVnd = estimatedCostVnd;
        this.offlineDemo = offlineDemo;
    }

    public String getTitle() { return title; }
    public List<ItineraryDay> getDays() { return days; }
    public long getTotalBudgetVnd() { return totalBudgetVnd; }
    public long getEstimatedCostVnd() { return estimatedCostVnd; }
    public long getRemainingBudgetVnd() { return totalBudgetVnd - estimatedCostVnd; }
    public boolean isOfflineDemo() { return offlineDemo; }
}
