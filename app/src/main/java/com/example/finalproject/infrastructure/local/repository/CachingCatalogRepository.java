package com.example.finalproject.infrastructure.local.repository;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.example.finalproject.domain.callback.RepositoryCallback;
import com.example.finalproject.domain.model.Place;
import com.example.finalproject.domain.repository.CatalogRepository;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Wraps the Django catalog with a Room copy of whatever came back.
 *
 * Online the network answer wins and is written to the cache. Offline the cache answers instead,
 * so Explore, place selection and replacement keep working with real places rather than an error
 * panel. The error is only surfaced when the cache has nothing to offer either.
 */
public final class CachingCatalogRepository implements CatalogRepository {
    /** Reports that an answer came from disk, so the screen can label it. */
    public interface OfflineListener {
        void onServedFromCache(long cachedAt);
    }

    private final CatalogRepository remote;
    private final PlaceCache cache;
    private final OfflineListener offlineListener;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    public CachingCatalogRepository(Context context, CatalogRepository remote,
                                    OfflineListener offlineListener) {
        this.remote = remote;
        this.cache = new PlaceCache(context);
        this.offlineListener = offlineListener;
    }

    @Override
    public void load(String type, String query, RepositoryCallback<List<Place>> callback) {
        remote.load(type, query, new CachingCallback(type, query, callback));
    }

    @Override
    public void suggest(String type, String query, RepositoryCallback<List<Place>> callback) {
        remote.suggest(type, query, new CachingCallback(type, query, callback));
    }

    private final class CachingCallback implements RepositoryCallback<List<Place>> {
        private final String type;
        private final String query;
        private final RepositoryCallback<List<Place>> delegate;

        CachingCallback(String type, String query, RepositoryCallback<List<Place>> delegate) {
            this.type = type;
            this.query = query;
            this.delegate = delegate;
        }

        @Override public void onSuccess(List<Place> places) {
            // Hand the fresh list to the UI first; persisting it must not delay rendering.
            delegate.onSuccess(places);
            executor.execute(() -> cache.save(type, places));
        }

        @Override public void onError(Exception error) {
            executor.execute(() -> {
                List<Place> cached = cache.load(type, query, 80);
                long cachedAt = cache.lastUpdatedAt();
                main.post(() -> {
                    if (cached.isEmpty()) {
                        delegate.onError(error);
                        return;
                    }
                    if (offlineListener != null) offlineListener.onServedFromCache(cachedAt);
                    delegate.onSuccess(cached);
                });
            });
        }
    }
}
