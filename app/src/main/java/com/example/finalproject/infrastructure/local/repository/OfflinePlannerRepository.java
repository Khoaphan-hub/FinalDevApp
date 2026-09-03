package com.example.finalproject.infrastructure.local.repository;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.example.finalproject.R;
import com.example.finalproject.domain.callback.RepositoryCallback;
import com.example.finalproject.domain.model.Itinerary;
import com.example.finalproject.domain.model.OfflineItineraryBuilder;
import com.example.finalproject.domain.model.Place;
import com.example.finalproject.domain.model.TripRequest;
import com.example.finalproject.domain.repository.PlannerRepository;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Plans a trip from the catalog cached on the device.
 *
 * Used when Django cannot be reached. If the user has never been online there is nothing cached,
 * and the request is handed to the built-in sample planner instead so the screen still shows
 * something rather than an error.
 */
public final class OfflinePlannerRepository implements PlannerRepository {
    /** How many cached places to consider; enough for a week-long trip without loading everything. */
    private static final int CANDIDATE_LIMIT = 200;

    /** Reports which offline source answered, so the screen can explain it to the user. */
    public interface SourceListener {
        void onOfflineResult(boolean fromCachedCatalog);
    }

    private final Context context;
    private final PlaceCache cache;
    private final PlannerRepository sampleFallback;
    private final SourceListener sourceListener;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    public OfflinePlannerRepository(Context context, PlannerRepository sampleFallback,
                                   SourceListener sourceListener) {
        this.context = context.getApplicationContext();
        this.cache = new PlaceCache(this.context);
        this.sampleFallback = sampleFallback;
        this.sourceListener = sourceListener;
    }

    @Override
    public void generate(TripRequest request, RepositoryCallback<Itinerary> callback) {
        executor.execute(() -> {
            // Same reason as in CachingCatalogRepository: without this a fresh install plans
            // the whole trip from nine hardcoded sample places.
            cache.seedFromAssetsIfEmpty();
            List<Place> pois = cache.load("poi", "", CANDIDATE_LIMIT);
            List<Place> eateries = cache.load("eatery", "", CANDIDATE_LIMIT);
            Itinerary itinerary = OfflineItineraryBuilder.build(request, pois, eateries, labels());
            main.post(() -> {
                // Reported from here rather than from the caller: deciding whether a cached
                // catalog exists is a database read and must not happen on the main thread.
                if (sourceListener != null) sourceListener.onOfflineResult(itinerary != null);
                if (itinerary == null) {
                    sampleFallback.generate(request, callback);
                    return;
                }
                callback.onSuccess(itinerary);
            });
        });
    }

    private OfflineItineraryBuilder.Labels labels() {
        return new OfflineItineraryBuilder.Labels(
            context.getString(R.string.offline_trip_title),
            context.getString(R.string.default_centre_name),
            context.getString(R.string.offline_start_label),
            context.getString(R.string.offline_return_label));
    }
}
