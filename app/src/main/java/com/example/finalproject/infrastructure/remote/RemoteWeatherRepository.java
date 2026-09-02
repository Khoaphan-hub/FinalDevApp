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

public final class RemoteWeatherRepository {
    private static final String URL_PREFIX = "https://api.open-meteo.com/v1/forecast?latitude=11.9404&longitude=108.4583&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,wind_speed_10m,is_day&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max&timezone=Asia%2FBangkok&forecast_days=";
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    public void load(RepositoryCallback<WeatherSnapshot> callback) {
        load(3, callback);
    }

    public void load(int forecastDays, RepositoryCallback<WeatherSnapshot> callback) {
        int safeDays = Math.max(1, Math.min(forecastDays, 7));
        executor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(URL_PREFIX + safeDays).openConnection();
                connection.setConnectTimeout(7000);
                connection.setReadTimeout(10000);
                JSONObject root = new JSONObject(read(connection.getInputStream()));
                JSONObject now = root.getJSONObject("current");
                JSONObject daily = root.getJSONObject("daily");
                JSONArray dates = daily.getJSONArray("time");
                JSONArray codes = daily.getJSONArray("weather_code");
                JSONArray mins = daily.getJSONArray("temperature_2m_min");
                JSONArray maxs = daily.getJSONArray("temperature_2m_max");
                JSONArray rains = daily.getJSONArray("precipitation_probability_max");
                List<WeatherSnapshot.Day> days = new ArrayList<>();
                for (int i = 0; i < dates.length(); i++) {
                    days.add(new WeatherSnapshot.Day(dates.getString(i), codes.getInt(i),
                        mins.getDouble(i), maxs.getDouble(i), rains.optInt(i)));
                }
                WeatherSnapshot snapshot = new WeatherSnapshot(now.getDouble("temperature_2m"),
                    now.getDouble("apparent_temperature"), now.getInt("relative_humidity_2m"),
                    now.getDouble("wind_speed_10m"), now.getInt("weather_code"),
                    now.getInt("is_day") == 1, days);
                main.post(() -> callback.onSuccess(snapshot));
            } catch (Exception error) {
                main.post(() -> callback.onError(error));
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private String read(InputStream input) throws Exception {
        StringBuilder result = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) result.append(line);
        }
        return result.toString();
    }
}
