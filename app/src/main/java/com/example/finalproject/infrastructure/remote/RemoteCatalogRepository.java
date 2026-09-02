package com.example.finalproject.infrastructure.remote;

import android.os.Handler;
import android.os.Looper;

import com.example.finalproject.domain.callback.RepositoryCallback;
import com.example.finalproject.domain.model.Place;
import com.example.finalproject.domain.repository.CatalogRepository;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.Locale;

public final class RemoteCatalogRepository implements CatalogRepository {
    private final String baseUrl;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler main = new Handler(Looper.getMainLooper());

    public RemoteCatalogRepository(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
    }

    @Override
    public void load(String type, String query, RepositoryCallback<List<Place>> callback) {
        String encoded;
        try {
            encoded = URLEncoder.encode(query == null ? "" : query, StandardCharsets.UTF_8.name());
        } catch (Exception error) {
            callback.onError(error);
            return;
        }
        request("api/mobile/catalog/?type=" + type + "&query=" + encoded + "&limit=80", callback);
    }

    public void suggest(String type, String query, RepositoryCallback<List<Place>> callback) {
        String encoded;
        try {
            encoded = URLEncoder.encode(query == null ? "" : query, StandardCharsets.UTF_8.name());
        } catch (Exception error) {
            callback.onError(error);
            return;
        }
        request("api/mobile/search-suggestions/?type=" + type + "&q=" + encoded + "&limit=25", callback);
    }

    private void request(String path, RepositoryCallback<List<Place>> callback) {
        executor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                URL url = new URL(baseUrl + path);
                connection = (HttpURLConnection) url.openConnection();
                connection.setConnectTimeout(6000);
                connection.setReadTimeout(12000);
                JSONObject root = new JSONObject(read(connection));
                if (connection.getResponseCode() != 200 || !root.optBoolean("success")) {
                    throw new IllegalStateException(root.optString("message", "Không tải được địa điểm."));
                }
                JSONArray items = root.getJSONObject("data").getJSONArray("items");
                List<Place> places = new ArrayList<>();
                for (int i = 0; i < items.length(); i++) {
                    JSONObject item = items.getJSONObject(i);
                    boolean english = "en".equals(Locale.getDefault().getLanguage());
                    String name = localized(item, english ? "name_en" : "name", "name");
                    String address = localized(item, english ? "address_en" : "address", "address");
                    places.add(new Place(item.optInt("id"), item.optString("type"),
                        name, address, item.optDouble("rating"),
                        Math.round(item.optDouble("price")), nullable(item, "image_url"),
                        item.optDouble("latitude"), item.optDouble("longitude"),
                        nullable(item, "open_hours"), nullable(item, "tags"),
                        nullable(item, "highlight"), nullable(item, "media_url"),
                        localized(item, "map_name", "name"),
                        localized(item, "map_address", "address")));
                }
                main.post(() -> callback.onSuccess(places));
            } catch (Exception error) {
                main.post(() -> callback.onError(error));
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private String read(HttpURLConnection connection) throws Exception {
        StringBuilder result = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(
            connection.getResponseCode() < 400 ? connection.getInputStream() : connection.getErrorStream(),
            StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) result.append(line);
        }
        return result.toString();
    }

    private String nullable(JSONObject object, String key) {
        return object.isNull(key) ? null : object.optString(key, null);
    }

    private String localized(JSONObject object, String preferred, String fallback) {
        String value = nullable(object, preferred);
        return value == null || value.trim().isEmpty() ? object.optString(fallback) : value;
    }
}
