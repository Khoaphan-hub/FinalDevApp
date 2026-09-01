package com.example.finalproject.infrastructure.local.repository;

import android.content.Context;
import android.content.SharedPreferences;

import com.example.finalproject.domain.callback.RepositoryCallback;
import com.example.finalproject.domain.model.WeatherSnapshot;
import com.example.finalproject.infrastructure.remote.RemoteWeatherRepository;

/**
 * Keeps the last successful Open-Meteo response so the home card shows the most recent reading
 * instead of an error when the device is offline.
 *
 * The payload is a few hundred bytes, so it lives in SharedPreferences rather than in Room.
 */
public final class CachingWeatherRepository {
    private static final String PREFS = "journify_weather";
    private static final String KEY_JSON = "last_response";
    private static final String KEY_AT = "last_response_at";

    /** Told when the card is showing a stored reading, with when it was taken. */
    public interface StaleListener {
        void onServedFromCache(long cachedAt);
    }

    private final SharedPreferences preferences;
    private final RemoteWeatherRepository remote = new RemoteWeatherRepository();

    public CachingWeatherRepository(Context context) {
        this.preferences = context.getApplicationContext()
            .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    public void load(RepositoryCallback<WeatherSnapshot> callback, StaleListener staleListener) {
        remote.load(new RepositoryCallback<WeatherSnapshot>() {
            @Override public void onSuccess(WeatherSnapshot snapshot) {
                callback.onSuccess(snapshot);
            }

            @Override public void onError(Exception error) {
                String stored = preferences.getString(KEY_JSON, null);
                if (stored == null) {
                    callback.onError(error);
                    return;
                }
                try {
                    WeatherSnapshot snapshot = RemoteWeatherRepository.parse(stored);
                    if (staleListener != null) {
                        staleListener.onServedFromCache(preferences.getLong(KEY_AT, 0L));
                    }
                    callback.onSuccess(snapshot);
                } catch (Exception parseError) {
                    // A stored payload we can no longer read is worse than none: drop it.
                    preferences.edit().remove(KEY_JSON).remove(KEY_AT).apply();
                    callback.onError(error);
                }
            }
        }, json -> preferences.edit()
            .putString(KEY_JSON, json)
            .putLong(KEY_AT, System.currentTimeMillis())
            .apply());
    }
}
