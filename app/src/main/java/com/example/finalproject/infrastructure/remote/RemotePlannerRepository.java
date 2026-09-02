package com.example.finalproject.infrastructure.remote;

import android.os.Handler;
import android.os.Looper;
import android.os.Build;

import com.example.finalproject.BuildConfig;
import com.example.finalproject.domain.callback.RepositoryCallback;
import com.example.finalproject.domain.model.Itinerary;
import com.example.finalproject.domain.model.ItineraryDay;
import com.example.finalproject.domain.model.ItineraryStop;
import com.example.finalproject.domain.model.Mood;
import com.example.finalproject.domain.model.TripRequest;
import com.example.finalproject.domain.repository.PlannerRepository;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class RemotePlannerRepository implements PlannerRepository {
    // Both values come from build.gradle.kts. The phone address is read from local.properties
    // so it follows whichever Wi-Fi network the development machine is on, and the release
    // build type replaces both with the deployed HTTPS origin.
    private static final String EMULATOR_BASE_URL = "http://10.0.2.2:8000/";
    private static final String PHYSICAL_PHONE_BASE_URL = "http://127.0.0.1:8000/";
    public static final String DEFAULT_BASE_URL = isEmulator()
        ? EMULATOR_BASE_URL : PHYSICAL_PHONE_BASE_URL;
    private final String baseUrl;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public RemotePlannerRepository(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
    }

    private static boolean isEmulator() {
        String fingerprint = Build.FINGERPRINT.toLowerCase(Locale.US);
        String model = Build.MODEL.toLowerCase(Locale.US);
        String product = Build.PRODUCT.toLowerCase(Locale.US);
        return fingerprint.startsWith("generic")
            || fingerprint.contains("emulator")
            || model.contains("emulator")
            || model.contains("google_sdk")
            || product.contains("sdk_gphone")
            || product.equals("google_sdk");
    }

    @Override
    public void generate(TripRequest request, RepositoryCallback<Itinerary> callback) {
        executor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(baseUrl + "api/mobile/itineraries/generate/").openConnection();
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(6000);
                connection.setReadTimeout(45000);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                connection.setRequestProperty("Accept", "application/json");
                connection.setDoOutput(true);
                byte[] body = requestJson(request).toString().getBytes(StandardCharsets.UTF_8);
                try (OutputStream output = connection.getOutputStream()) { output.write(body); }

                int status = connection.getResponseCode();
                InputStream stream = status >= 200 && status < 300
                    ? connection.getInputStream() : connection.getErrorStream();
                JSONObject response = new JSONObject(read(stream));
                if (status < 200 || status >= 300 || !response.optBoolean("success")) {
                    throw new IllegalStateException(response.optString("message", "Django không thể tạo lịch trình."));
                }
                Itinerary itinerary = parseItinerary(response.getJSONObject("data"));
                mainHandler.post(() -> callback.onSuccess(itinerary));
            } catch (Exception error) {
                mainHandler.post(() -> callback.onError(error));
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private JSONObject requestJson(TripRequest request) throws Exception {
        JSONObject json = new JSONObject();
        json.put("language", "en".equals(java.util.Locale.getDefault().getLanguage()) ? "en" : "vi");
        json.put("days", request.getDays());
        json.put("daily_poi_limit", request.getDailyPoiLimit());
        json.put("budget", request.getBudgetVnd());
        json.put("start_address", request.getStartAddress());
        json.put("use_default_center", request.isUseDefaultCenter());
        JSONArray moods = new JSONArray();
        for (Mood mood : request.getMoods()) moods.put(mood.getApiValue());
        json.put("moods", moods);
        json.put("selected_poi_ids", new JSONArray(request.getSelectedPoiIds()));
        json.put("selected_eatery_ids", new JSONArray(request.getSelectedEateryIds()));
        return json;
    }

    private Itinerary parseItinerary(JSONObject data) throws Exception {
        List<ItineraryDay> days = new ArrayList<>();
        JSONArray dayArray = data.getJSONArray("days");
        for (int i = 0; i < dayArray.length(); i++) {
            JSONObject dayJson = dayArray.getJSONObject(i);
            List<ItineraryStop> stops = new ArrayList<>();
            JSONArray stopArray = dayJson.getJSONArray("stops");
            for (int j = 0; j < stopArray.length(); j++) {
                JSONObject stop = stopArray.getJSONObject(j);
                ItineraryStop.Type type;
                try { type = ItineraryStop.Type.valueOf(stop.optString("type", "POI")); }
                catch (IllegalArgumentException ignored) { type = ItineraryStop.Type.POI; }
                stops.add(new ItineraryStop(
                    stop.optInt("id"), type, stop.optString("name"), stop.optString("address"),
                    stop.optDouble("latitude"), stop.optDouble("longitude"),
                    stop.optDouble("travel_to_next_km"), nullable(stop, "meal_slot"),
                    nullable(stop, "image_url"), stop.optDouble("rating"),
                    Math.round(stop.optDouble("price")), nullable(stop, "open_hours"),
                    nullable(stop, "tags"), nullable(stop, "highlight"), nullable(stop, "media_url"),
                    localized(stop, "map_name", "name"), localized(stop, "map_address", "address")
                ));
            }
            days.add(new ItineraryDay(dayJson.getInt("day"), stops));
        }
        JSONObject budget = data.getJSONObject("budget");
        return new Itinerary(data.optString("title", "Đà Lạt theo cách của bạn"), days,
            Math.round(budget.optDouble("total")), Math.round(budget.optDouble("estimated")), false);
    }

    private String nullable(JSONObject object, String key) {
        return object.isNull(key) ? null : object.optString(key, null);
    }

    private String localized(JSONObject object, String preferred, String fallback) {
        String value = nullable(object, preferred);
        return value == null || value.trim().isEmpty() ? object.optString(fallback) : value;
    }

    private String read(InputStream input) throws Exception {
        if (input == null) return "{}";
        StringBuilder result = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) result.append(line);
        }
        return result.toString();
    }
}
