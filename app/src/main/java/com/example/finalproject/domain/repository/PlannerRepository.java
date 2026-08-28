package com.example.finalproject.domain.repository;

import com.example.finalproject.domain.callback.RepositoryCallback;
import com.example.finalproject.domain.model.Itinerary;
import com.example.finalproject.domain.model.TripRequest;

public interface PlannerRepository {
    void generate(TripRequest request, RepositoryCallback<Itinerary> callback);
}
