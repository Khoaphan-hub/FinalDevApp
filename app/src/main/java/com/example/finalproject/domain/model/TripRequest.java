package com.example.finalproject.domain.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class TripRequest implements Serializable {
    private final int days;
    private final int dailyPoiLimit;
    private final long budgetVnd;
    private final List<Mood> moods;
    private final String startAddress;
    private final boolean useDefaultCenter;
    private final List<Integer> selectedPoiIds;
    private final List<Integer> selectedEateryIds;

    public TripRequest(int days, int dailyPoiLimit, long budgetVnd, List<Mood> moods,
                       String startAddress, boolean useDefaultCenter,
                       List<Integer> selectedPoiIds, List<Integer> selectedEateryIds) {
        this.days = days;
        this.dailyPoiLimit = dailyPoiLimit;
        this.budgetVnd = budgetVnd;
        this.moods = immutableCopy(moods);
        this.startAddress = startAddress == null ? "" : startAddress.trim();
        this.useDefaultCenter = useDefaultCenter;
        this.selectedPoiIds = immutableCopy(selectedPoiIds);
        this.selectedEateryIds = immutableCopy(selectedEateryIds);
    }

    private static <T> List<T> immutableCopy(List<T> source) {
        return Collections.unmodifiableList(new ArrayList<>(source == null ? Collections.emptyList() : source));
    }

    public int getDays() { return days; }
    public int getDailyPoiLimit() { return dailyPoiLimit; }
    public long getBudgetVnd() { return budgetVnd; }
    public List<Mood> getMoods() { return moods; }
    public String getStartAddress() { return startAddress; }
    public boolean isUseDefaultCenter() { return useDefaultCenter; }
    public List<Integer> getSelectedPoiIds() { return selectedPoiIds; }
    public List<Integer> getSelectedEateryIds() { return selectedEateryIds; }
}
