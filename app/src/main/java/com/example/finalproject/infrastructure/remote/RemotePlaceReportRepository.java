package com.example.finalproject.infrastructure.remote;

import android.os.Handler;
import android.os.Looper;

import com.example.finalproject.domain.callback.RepositoryCallback;
import com.example.finalproject.domain.model.Place;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class RemotePlaceReportRepository {
    private final String baseUrl;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public RemotePlaceReportRepository(String baseUrl) {
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl : baseUrl + "/";
    }

    public void submit(Place place, String category, String description,
                       RepositoryCallback<Integer> callback) {
        executor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                connection = (HttpURLConnection) new URL(
                    baseUrl + "api/mobile/place-reports/").openConnection();
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(7000);
                connection.setReadTimeout(15000);
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8");
                connection.setRequestProperty("Accept", "application/json");
                connection.setDoOutput(true);

                JSONObject payload = new JSONObject()
                    .put("target_type", place.getType())
                    .put("target_id", place.getId())
                    .put("category", category)
                    .put("description", description)
                    .put("language", "en".equals(Locale.getDefault().getLanguage()) ? "en" : "vi");
                byte[] body = payload.toString().getBytes(StandardCharsets.UTF_8);
                try (OutputStream output = connection.getOutputStream()) {
                    output.write(body);
                }

                int status = connection.getResponseCode();
                InputStream stream = status >= 200 && status < 300
                    ? connection.getInputStream() : connection.getErrorStream();
                JSONObject response = new JSONObject(read(stream));
                if (status < 200 || status >= 300 || !response.optBoolean("success")) {
                    throw new IllegalStateException(response.optString(
                        "message", "Unable to submit this report."));
                }
                int reportId = response.getJSONObject("data").getInt("report_id");
                mainHandler.post(() -> callback.onSuccess(reportId));
            } catch (Exception error) {
                mainHandler.post(() -> callback.onError(error));
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private String read(InputStream stream) throws Exception {
        if (stream == null) return "{}";
        StringBuilder text = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
            new InputStreamReader(stream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) text.append(line);
        }
        return text.toString();
    }
}
