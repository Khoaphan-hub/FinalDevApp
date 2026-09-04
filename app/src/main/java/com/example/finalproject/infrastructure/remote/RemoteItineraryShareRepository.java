package com.example.finalproject.infrastructure.remote;

import android.os.Handler;
import android.os.Looper;

import com.example.finalproject.domain.callback.RepositoryCallback;
import com.example.finalproject.domain.model.Itinerary;
import com.example.finalproject.domain.model.ItineraryDay;
import com.example.finalproject.domain.model.ItineraryShareData;
import com.example.finalproject.domain.model.ItineraryStop;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class RemoteItineraryShareRepository {
    private final String baseUrl;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    public RemoteItineraryShareRepository(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
    }

    public void create(Itinerary itinerary, RepositoryCallback<ItineraryShareData> callback) {
        executor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(baseUrl + "api/mobile/itineraries/share/").openConnection();
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(7000);
                connection.setReadTimeout(20000);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                connection.setDoOutput(true);
                byte[] body = toJson(itinerary).toString().getBytes(StandardCharsets.UTF_8);
                try (OutputStream out = connection.getOutputStream()) { out.write(body); }
                int status = connection.getResponseCode();
                InputStream stream = status >= 200 && status < 300 ? connection.getInputStream() : connection.getErrorStream();
                JSONObject response = new JSONObject(read(stream));
                if (status < 200 || status >= 300 || !response.optBoolean("success")) {
                    throw new IllegalStateException(response.optString("message", "Không thể tạo mã QR."));
                }
                JSONObject data = response.getJSONObject("data");
                ItineraryShareData result = new ItineraryShareData(data.getString("share_url"),
                    data.getString("qr_base64"), data.optString("expires_at"));
                main.post(() -> callback.onSuccess(result));
            } catch (Exception e) {
                main.post(() -> callback.onError(e));
            } finally { if (connection != null) connection.disconnect(); }
        });
    }

    private JSONObject toJson(Itinerary itinerary) throws Exception {
        JSONObject root = new JSONObject();
        root.put("title", itinerary.getTitle());
        root.put("total_budget_vnd", itinerary.getTotalBudgetVnd());
        root.put("estimated_cost_vnd", itinerary.getEstimatedCostVnd());
        JSONArray days = new JSONArray();
        for (ItineraryDay day : itinerary.getDays()) {
            JSONObject d = new JSONObject().put("day_number", day.getDayNumber());
            JSONArray stops = new JSONArray();
            for (ItineraryStop stop : day.getStops()) {
                stops.put(new JSONObject().put("id", stop.getId()).put("type", stop.getType().name())
                    .put("name", stop.getName()).put("address", stop.getAddress())
                    .put("latitude", stop.getLatitude()).put("longitude", stop.getLongitude())
                    .put("travel_to_next_km", stop.getTravelToNextKm())
                    .put("price", stop.getPriceVnd()).put("rating", stop.getRating())
                    .put("open_hours", stop.getOpenHours()).put("tags", stop.getTags())
                    .put("highlight", stop.getHighlight()).put("map_name", stop.getMapName())
                    .put("map_address", stop.getMapAddress())
                    .put("meal_slot", stop.getMealSlot() == null ? JSONObject.NULL : stop.getMealSlot()));
            }
            days.put(d.put("stops", stops));
        }
        return root.put("days", days);
    }

    private String read(InputStream stream) throws Exception {
        if (stream == null) return "{}";
        StringBuilder text = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line; while ((line = reader.readLine()) != null) text.append(line);
        }
        return text.toString();
    }
}
