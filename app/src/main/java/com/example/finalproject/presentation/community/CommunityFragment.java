package com.example.finalproject.presentation.community;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.finalproject.R;
import com.example.finalproject.infrastructure.remote.RemotePlannerRepository;
import com.example.finalproject.presentation.itinerary.ItineraryActivity;
import android.content.Intent;

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

public class CommunityFragment extends Fragment {
    private androidx.activity.result.ActivityResultLauncher<android.content.Intent> signInLauncher;

    private RecyclerView recyclerView;
    private View progress;
    private View empty;
    private CommunityAdapter adapter;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_community, container, false);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        signInLauncher = registerForActivityResult(
            new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (getView() != null) applySignedInState(getView());
            });
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        recyclerView = view.findViewById(R.id.communityRecycler);
        progress = view.findViewById(R.id.communityProgress);
        empty = view.findViewById(R.id.communityEmpty);

        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new CommunityAdapter(new CommunityAdapter.Listener() {
            @Override
            public void onClick(JSONObject item) {
                // Fetch detailed itinerary and open ItineraryActivity in view-only mode
                fetchAndOpenItinerary(item.optInt("id"));
            }

            @Override
            public void onRate(JSONObject item, float rating) {
                submitRating(item.optInt("id"), (int) rating);
            }
        });
        recyclerView.setAdapter(adapter);

        ((android.widget.TextView) view.findViewById(R.id.signedOutMessage))
            .setText(R.string.community_signed_out_message);
        view.findViewById(R.id.signedOutSignInButton).setOnClickListener(v ->
            signInLauncher.launch(new android.content.Intent(requireContext(),
                com.example.finalproject.presentation.auth.LoginActivity.class)));
        applySignedInState(view);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (getView() != null) applySignedInState(getView());
    }

    /**
     * Community itineraries are fetched with the signed-in session, so without an account the
     * request only comes back empty. Showing the prompt says why, instead of an empty list.
     */
    private void applySignedInState(View root) {
        boolean signedIn = com.example.finalproject.infrastructure.local.SessionState
            .isSignedIn(requireContext());
        root.findViewById(R.id.signedOutPanel).setVisibility(signedIn ? View.GONE : View.VISIBLE);
        recyclerView.setVisibility(signedIn ? View.VISIBLE : View.GONE);
        progress.setVisibility(signedIn ? View.VISIBLE : View.GONE);
        if (!signedIn) {
            empty.setVisibility(View.GONE);
            return;
        }
        loadCommunityItineraries();
    }

    private void loadCommunityItineraries() {
        progress.setVisibility(View.VISIBLE);
        empty.setVisibility(View.GONE);

        executor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                String baseUrl = RemotePlannerRepository.DEFAULT_BASE_URL;
                if (!baseUrl.endsWith("/")) baseUrl += "/";
                
                connection = (HttpURLConnection) new URL(baseUrl + "api/shared-itineraries/").openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(6000);

                int status = connection.getResponseCode();
                if (status >= 200 && status < 300) {
                    JSONObject response = new JSONObject(readStream(connection.getInputStream()));
                    JSONArray array = response.optJSONArray("itineraries");
                    List<JSONObject> items = new ArrayList<>();
                    if (array != null) {
                        for (int i = 0; i < array.length(); i++) {
                            items.add(array.getJSONObject(i));
                        }
                    }
                    mainHandler.post(() -> {
                        adapter.setItems(items);
                        progress.setVisibility(View.GONE);
                        if (items.isEmpty()) empty.setVisibility(View.VISIBLE);
                    });
                } else {
                    mainHandler.post(() -> {
                        progress.setVisibility(View.GONE);
                        Toast.makeText(getContext(), R.string.publish_failed, Toast.LENGTH_SHORT).show();
                    });
                }
            } catch (Exception e) {
                mainHandler.post(() -> {
                    progress.setVisibility(View.GONE);
                    Toast.makeText(getContext(), R.string.network_error, Toast.LENGTH_SHORT).show();
                });
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private void submitRating(int id, int rating) {
        executor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                String baseUrl = com.example.finalproject.infrastructure.remote.RemotePlannerRepository.DEFAULT_BASE_URL;
                if (!baseUrl.endsWith("/")) baseUrl += "/";
                
                connection = (HttpURLConnection) new URL(baseUrl + "api/shared-itineraries/" + id + "/feedback/").openConnection();
                connection.setRequestMethod("POST");
                connection.setRequestProperty("Content-Type", "application/json");
                connection.setDoOutput(true);
                
                JSONObject payload = new JSONObject();
                payload.put("rating", rating);
                
                try (java.io.OutputStream os = connection.getOutputStream()) {
                    byte[] input = payload.toString().getBytes(StandardCharsets.UTF_8);
                    os.write(input, 0, input.length);
                }
                
                int status = connection.getResponseCode();
                if (status >= 200 && status < 300) {
                    mainHandler.post(() -> {
                        android.widget.Toast.makeText(getContext(), R.string.rating_success, android.widget.Toast.LENGTH_SHORT).show();
                        if (getView() != null) applySignedInState(getView()); // Refresh the list
                    });
                } else {
                    mainHandler.post(() -> {
                        android.widget.Toast.makeText(getContext(), R.string.rating_failed, android.widget.Toast.LENGTH_SHORT).show();
                    });
                }
            } catch (Exception e) {
                mainHandler.post(() -> {
                    android.widget.Toast.makeText(getContext(), R.string.rating_failed, android.widget.Toast.LENGTH_SHORT).show();
                });
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private void fetchAndOpenItinerary(int id) {
        progress.setVisibility(View.VISIBLE);
        executor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                String baseUrl = RemotePlannerRepository.DEFAULT_BASE_URL;
                if (!baseUrl.endsWith("/")) baseUrl += "/";
                
                connection = (HttpURLConnection) new URL(baseUrl + "api/shared-itineraries/" + id + "/").openConnection();
                connection.setRequestMethod("GET");

                int status = connection.getResponseCode();
                if (status >= 200 && status < 300) {
                    JSONObject response = new JSONObject(readStream(connection.getInputStream()));
                    JSONObject plannerData = response.optJSONObject("planner_itinerary");
                    if (plannerData != null) {
                        org.json.JSONObject results = plannerData.optJSONObject("results");
                        if (results != null) {
                            java.util.List<com.example.finalproject.domain.model.ItineraryDay> days = new java.util.ArrayList<>();
                            java.util.Iterator<String> keys = results.keys();
                            while (keys.hasNext()) {
                                String key = keys.next();
                                int dayNum = Integer.parseInt(key);
                                org.json.JSONArray stopsArr = results.optJSONArray(key);
                                java.util.List<com.example.finalproject.domain.model.ItineraryStop> stops = new java.util.ArrayList<>();
                                if (stopsArr != null) {
                                    for (int i = 0; i < stopsArr.length(); i++) {
                                        org.json.JSONObject stopObj = stopsArr.optJSONObject(i);
                                        if (stopObj != null) {
                                            com.example.finalproject.domain.model.ItineraryStop.Type type = com.example.finalproject.domain.model.ItineraryStop.Type.POI;
                                            try { type = com.example.finalproject.domain.model.ItineraryStop.Type.valueOf(stopObj.optString("type", "POI")); } catch (Exception ignored) {}
                                            
                                            String image = stopObj.optString("image_url", null);
                                            if (image == null || image.isEmpty()) image = stopObj.optString("image_code", null);
                                            if (image != null && image.isEmpty()) image = null;
                                            
                                            String mealSlot = stopObj.optString("meal_slot", null);
                                            if (mealSlot != null && mealSlot.isEmpty()) mealSlot = null;

                                            stops.add(new com.example.finalproject.domain.model.ItineraryStop(
                                                stopObj.optInt("id"), type, stopObj.optString("name"), stopObj.optString("address"),
                                                stopObj.optDouble("latitude"), stopObj.optDouble("longitude"), 0, mealSlot, image,
                                                stopObj.optDouble("rating", 0), Math.round(stopObj.optDouble("price", 0)),
                                                stopObj.optString("open_hours", null), stopObj.optString("tags", null),
                                                stopObj.optString("highlight", null), stopObj.optString("media_url", null)
                                            ));
                                        }
                                    }
                                }
                                days.add(new com.example.finalproject.domain.model.ItineraryDay(dayNum, stops));
                            }
                            java.util.Collections.sort(days, (a, b) -> Integer.compare(a.getDayNumber(), b.getDayNumber()));
                            
                            long totalBudget = Math.round(response.optDouble("budget_amount", 0));
                            if (totalBudget == 0) totalBudget = Math.round(plannerData.optDouble("budget_amount", 0));
                            
                            long remainingBudget = Math.round(response.optDouble("budget_remaining", 0));
                            if (remainingBudget == 0) remainingBudget = Math.round(plannerData.optDouble("budget_remaining", 0));
                            
                            com.example.finalproject.domain.model.Itinerary itinerary = new com.example.finalproject.domain.model.Itinerary(
                                response.optString("title", "Itinerary"), days, totalBudget, totalBudget - remainingBudget, false
                            );

                            mainHandler.post(() -> {
                                progress.setVisibility(View.GONE);
                                Intent intent = new Intent(requireContext(), ItineraryActivity.class);
                                intent.putExtra(ItineraryActivity.EXTRA_ITINERARY, itinerary);
                                intent.putExtra("IS_COMMUNITY", true);
                                intent.putExtra("COMMUNITY_ID", id);
                                startActivity(intent);
                            });
                            return;
                        }
                    }
                }
                mainHandler.post(() -> {
                    progress.setVisibility(View.GONE);
                    Toast.makeText(getContext(), R.string.network_error, Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    progress.setVisibility(View.GONE);
                    Toast.makeText(getContext(), R.string.network_error, Toast.LENGTH_SHORT).show();
                });
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private String readStream(InputStream input) throws Exception {
        if (input == null) return "{}";
        StringBuilder result = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) result.append(line);
        }
        return result.toString();
    }
}
