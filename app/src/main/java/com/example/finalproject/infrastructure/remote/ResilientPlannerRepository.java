package com.example.finalproject.infrastructure.remote;

import com.example.finalproject.domain.callback.RepositoryCallback;
import com.example.finalproject.domain.model.Itinerary;
import com.example.finalproject.domain.model.TripRequest;
import com.example.finalproject.domain.repository.PlannerRepository;

public final class ResilientPlannerRepository implements PlannerRepository {
    /** Notified when the server could not be reached and offline sample data is used instead. */
    public interface FallbackListener {
        void onOfflineFallback(Exception remoteError);
    }

    private final PlannerRepository remote;
    private final PlannerRepository offline;
    private final FallbackListener fallbackListener;

    public ResilientPlannerRepository(PlannerRepository remote, PlannerRepository offline) {
        this(remote, offline, null);
    }

    public ResilientPlannerRepository(PlannerRepository remote, PlannerRepository offline,
                                      FallbackListener fallbackListener) {
        this.remote = remote;
        this.offline = offline;
        this.fallbackListener = fallbackListener;
    }

    @Override
    public void generate(TripRequest request, RepositoryCallback<Itinerary> callback) {
        remote.generate(request, new RepositoryCallback<Itinerary>() {
            @Override public void onSuccess(Itinerary result) { callback.onSuccess(result); }
            @Override public void onError(Exception remoteError) {
                // The offline planner always succeeds, so without this the user would silently
                // receive sample data and believe the server answered.
                if (fallbackListener != null) fallbackListener.onOfflineFallback(remoteError);
                offline.generate(request, callback);
            }
        });
    }
}
