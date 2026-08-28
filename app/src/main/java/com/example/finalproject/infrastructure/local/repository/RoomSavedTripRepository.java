package com.example.finalproject.infrastructure.local.repository;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.example.finalproject.domain.callback.RepositoryCallback;
import com.example.finalproject.domain.model.Itinerary;
import com.example.finalproject.domain.model.SavedTrip;
import com.example.finalproject.domain.repository.SavedTripRepository;
import com.example.finalproject.infrastructure.local.db.JournifyDatabase;
import com.example.finalproject.infrastructure.local.entity.SavedTripEntity;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class RoomSavedTripRepository implements SavedTripRepository {
    private final JournifyDatabase database;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    public RoomSavedTripRepository(Context context) {
        database = JournifyDatabase.get(context);
    }

    @Override public void save(Itinerary itinerary, RepositoryCallback<Long> callback) {
        executor.execute(() -> {
            try {
                long id = database.savedTripDao().insert(new SavedTripEntity(
                    itinerary.getTitle(), System.currentTimeMillis(), serialize(itinerary)));
                main.post(() -> callback.onSuccess(id));
            } catch (Exception error) {
                main.post(() -> callback.onError(error));
            }
        });
    }

    @Override public void loadAll(RepositoryCallback<List<SavedTrip>> callback) {
        executor.execute(() -> {
            try {
                List<SavedTrip> results = new ArrayList<>();
                for (SavedTripEntity entity : database.savedTripDao().loadAll()) {
                    try {
                        results.add(new SavedTrip(entity.id, deserialize(entity.itineraryPayload), entity.savedAt));
                    } catch (Exception incompatibleSavedTrip) {
                        // Keep loading newer valid entries if an older serialized model is incompatible.
                    }
                }
                main.post(() -> callback.onSuccess(results));
            } catch (Exception error) {
                main.post(() -> callback.onError(error));
            }
        });
    }

    @Override public void delete(long id, RepositoryCallback<Void> callback) {
        executor.execute(() -> {
            try {
                database.savedTripDao().delete(id);
                main.post(() -> callback.onSuccess(null));
            } catch (Exception error) {
                main.post(() -> callback.onError(error));
            }
        });
    }

    private byte[] serialize(Itinerary itinerary) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) { output.writeObject(itinerary); }
        return bytes.toByteArray();
    }

    private Itinerary deserialize(byte[] bytes) throws Exception {
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return (Itinerary) input.readObject();
        }
    }
}
