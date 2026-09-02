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
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        recyclerView = view.findViewById(R.id.communityRecycler);
        progress = view.findViewById(R.id.communityProgress);
        empty = view.findViewById(R.id.communityEmpty);

        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new CommunityAdapter(item -> {
            // Fetch detailed itinerary and open ItineraryActivity in view-only mode
            fetchAndOpenItinerary(item.optInt("id"));
        });
        recyclerView.setAdapter(adapter);

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
                    JSONObject data = response.optJSONObject("itinerary");
                    if (data != null) {
                        JSONObject plannerData = data.optJSONObject("planner_itinerary");
                        if (plannerData != null) {
                            String json = plannerData.toString();
                            mainHandler.post(() -> {
                                progress.setVisibility(View.GONE);
                                Intent intent = new Intent(requireContext(), ItineraryActivity.class);
                                intent.putExtra(ItineraryActivity.EXTRA_ITINERARY, json);
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
                    Toast.makeText(getContext(), R.string.publish_failed, Toast.LENGTH_SHORT).show();
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
