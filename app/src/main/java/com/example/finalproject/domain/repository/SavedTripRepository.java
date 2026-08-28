package com.example.finalproject.domain.repository;

import com.example.finalproject.domain.callback.RepositoryCallback;
import com.example.finalproject.domain.model.Itinerary;
import com.example.finalproject.domain.model.SavedTrip;

import java.util.List;

public interface SavedTripRepository {
    void save(Itinerary itinerary, RepositoryCallback<Long> callback);
    void loadAll(RepositoryCallback<List<SavedTrip>> callback);
}
