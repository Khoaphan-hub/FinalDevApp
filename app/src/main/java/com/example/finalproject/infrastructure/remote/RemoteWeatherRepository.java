package com.example.finalproject.infrastructure.remote;

import android.os.Handler;
import android.os.Looper;

import com.example.finalproject.domain.callback.RepositoryCallback;
import com.example.finalproject.domain.model.WeatherSnapshot;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Current conditions and a three day forecast for Da Lat, straight from Open-Meteo (no API key). */
public final class RemoteWeatherRepository {
    private static final String URL_TEXT = "https://api.open-meteo.com/v1/forecast"
        + "?latitude=11.9404&longitude=108.4583"
        + "&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,"
        + "wind_speed_10m,is_day"
        + "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max"
        + "&timezone=Asia%2FBangkok&forecast_days=3";

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    /** Reports the raw response so a caller can keep it for offline use. */
    public interface RawListener {
        void onRawResponse(String json);
    }

    public void load(RepositoryCallback<WeatherSnapshot> callback) {
        load(callback, null);
    }

    public void load(RepositoryCallback<WeatherSnapshot> callback, RawListener rawListener) {
        executor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(URL_TEXT).openConnection();
                connection.setConnectTimeout(7000);
                connection.setReadTimeout(10000);
                String json = read(connection.getInputStream());
                WeatherSnapshot snapshot = parse(json);
                if (rawListener != null) rawListener.onRawResponse(json);
                main.post(() -> callback.onSuccess(snapshot));
            } catch (Exception error) {
                main.post(() -> callback.onError(error));
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    /** Shared with the offline cache, which stores the response verbatim and replays it later. */
    public static WeatherSnapshot parse(String json) throws Exception {
        JSONObject root = new JSONObject(json);
        JSONObject now = root.getJSONObject("current");
        JSONObject daily = root.getJSONObject("daily");
        JSONArray dates = daily.getJSONArray("time");
        JSONArray codes = daily.getJSONArray("weather_code");
        JSONArray mins = daily.getJSONArray("temperature_2m_min");
        JSONArray maxs = daily.getJSONArray("temperature_2m_max");
        JSONArray rains = daily.getJSONArray("precipitation_probability_max");

        List<WeatherSnapshot.Day> days = new ArrayList<>();
        for (int index = 0; index < dates.length(); index++) {
            days.add(new WeatherSnapshot.Day(dates.getString(index), codes.getInt(index),
                mins.getDouble(index), maxs.getDouble(index), rains.optInt(index)));
        }
        return new WeatherSnapshot(now.getDouble("temperature_2m"),
            now.getDouble("apparent_temperature"), now.getInt("relative_humidity_2m"),
            now.getDouble("wind_speed_10m"), now.getInt("weather_code"),
            now.getInt("is_day") == 1, days);
    }

    private String read(InputStream in) throws Exception {
        StringBuilder result = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) result.append(line);
        }
        return result.toString();
    }
}
