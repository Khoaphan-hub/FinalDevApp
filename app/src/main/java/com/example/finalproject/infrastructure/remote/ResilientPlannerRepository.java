package com.example.finalproject.infrastructure.remote;

import com.example.finalproject.domain.callback.RepositoryCallback;
import com.example.finalproject.domain.model.Itinerary;
import com.example.finalproject.domain.model.TripRequest;
import com.example.finalproject.domain.repository.PlannerRepository;

public final class ResilientPlannerRepository implements PlannerRepository {
    private final PlannerRepository remote;
    private final PlannerRepository offline;

    public ResilientPlannerRepository(PlannerRepository remote, PlannerRepository offline) {
        this.remote = remote;
        this.offline = offline;
    }

    @Override
    public void generate(TripRequest request, RepositoryCallback<Itinerary> callback) {
        remote.generate(request, new RepositoryCallback<Itinerary>() {
            @Override public void onSuccess(Itinerary result) { callback.onSuccess(result); }
            @Override public void onError(Exception remoteError) {
                offline.generate(request, callback);
            }
        });
    }
}
