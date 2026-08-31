package com.example.finalproject.presentation.selection;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.finalproject.R;
import com.example.finalproject.application.usecase.GenerateItineraryUseCase;
import com.example.finalproject.domain.callback.RepositoryCallback;
import com.example.finalproject.domain.model.Itinerary;
import com.example.finalproject.domain.model.Place;
import com.example.finalproject.domain.model.TripRequest;
import com.example.finalproject.infrastructure.demo.DemoPlannerRepository;
import com.example.finalproject.infrastructure.remote.RemoteCatalogRepository;
import com.example.finalproject.infrastructure.remote.RemotePlannerRepository;
import com.example.finalproject.infrastructure.remote.ResilientPlannerRepository;
import com.example.finalproject.presentation.SystemBarInsets;
import com.example.finalproject.presentation.catalog.PlaceAdapter;
import com.example.finalproject.presentation.catalog.PlaceDetailActivity;
import com.example.finalproject.presentation.itinerary.ItineraryActivity;
import com.google.android.material.chip.Chip;
import com.google.android.material.textfield.TextInputEditText;

import java.util.Collections;
import java.util.List;

public class PlaceSelectionActivity extends AppCompatActivity {
    public static final String EXTRA_REQUEST = "trip_request";

    private TripRequest request;
    private PlaceAdapter adapter;
    private RemoteCatalogRepository catalogRepository;
    private GenerateItineraryUseCase generateUseCase;
    private View progress;
    private View loadingOverlay;
    private TextView selectionSummary;
    private TextView stateMessage;
    private View statePanel;
    private TextInputEditText searchInput;
    private List<Place> cachedPois = Collections.emptyList();
    private List<Place> cachedEateries = Collections.emptyList();
    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingSearch;
    private int requestVersion;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_place_selection);
        SystemBarInsets.apply(findViewById(R.id.selectionRoot));
        request = readRequest();
        if (request == null) { finish(); return; }

        catalogRepository = new RemoteCatalogRepository(RemotePlannerRepository.DEFAULT_BASE_URL);
        generateUseCase = new GenerateItineraryUseCase(new ResilientPlannerRepository(
            new RemotePlannerRepository(RemotePlannerRepository.DEFAULT_BASE_URL), new DemoPlannerRepository()));
        progress = findViewById(R.id.selectionProgress);
        loadingOverlay = findViewById(R.id.selectionGenerating);
        selectionSummary = findViewById(R.id.selectionSummary);
        stateMessage = findViewById(R.id.selectionStateMessage);
        statePanel = findViewById(R.id.selectionStatePanel);
        searchInput = findViewById(R.id.selectionSearchInput);

        RecyclerView recycler = findViewById(R.id.selectionRecyclerView);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new PlaceAdapter(true, new PlaceAdapter.Listener() {
            @Override public void onOpen(Place place) {
                startActivity(PlaceDetailActivity.intent(PlaceSelectionActivity.this, place));
            }

            @Override public boolean onSelectionChangeRequested(Place place, boolean selected) {
                int max = "EATERY".equals(place.getType()) ? request.getDays() * 3
                    : request.getDays() * request.getDailyPoiLimit();
                int current = adapter.selectedCount(place.getType().toLowerCase());
                if (selected && current >= max) {
                    Toast.makeText(PlaceSelectionActivity.this,
                        getString(R.string.selection_limit, max), Toast.LENGTH_SHORT).show();
                    return false;
                }
                recycler.post(PlaceSelectionActivity.this::updateSummary);
                return true;
            }
        });
        recycler.setAdapter(adapter);

        findViewById(R.id.selectionBackButton).setOnClickListener(v -> finish());
        findViewById(R.id.selectionRetryButton).setOnClickListener(v -> loadCurrent(true));
        findViewById(R.id.selectionGenerateButton).setOnClickListener(v -> generate());
        findViewById(R.id.selectionSkipButton).setOnClickListener(v -> {
            generateWithIds(Collections.emptyList(), Collections.emptyList());
        });
        findViewById(R.id.selectionPoiChip).setOnClickListener(v -> showOrLoad("poi"));
        findViewById(R.id.selectionEateryChip).setOnClickListener(v -> showOrLoad("eatery"));
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence value, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence value, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable value) { scheduleSearch(); }
        });
        updateSummary();
        loadCurrent(false);
    }

    private void showOrLoad(String type) {
        String query = currentQuery();
        if (!query.isEmpty()) {
            search(type, query);
            return;
        }
        List<Place> cached = "poi".equals(type) ? cachedPois : cachedEateries;
        if (cached.isEmpty()) {
            load(type);
        } else {
            requestVersion++;
            progress.setVisibility(View.GONE);
            statePanel.setVisibility(View.GONE);
            adapter.submit(cached);
        }
    }

    private void loadCurrent(boolean force) {
        String type = ((Chip) findViewById(R.id.selectionEateryChip)).isChecked() ? "eatery" : "poi";
        if (force) {
            if ("poi".equals(type)) cachedPois = Collections.emptyList(); else cachedEateries = Collections.emptyList();
        }
        showOrLoad(type);
    }

    private void load(String type) {
        int version = ++requestVersion;
        progress.setVisibility(View.VISIBLE);
        statePanel.setVisibility(View.GONE);
        catalogRepository.load(type, "", new RepositoryCallback<List<Place>>() {
            @Override public void onSuccess(List<Place> places) {
                if (!isCurrent(version, type, "")) return;
                progress.setVisibility(View.GONE);
                if ("poi".equals(type)) cachedPois = places; else cachedEateries = places;
                adapter.submit(places);
                if (places.isEmpty()) {
                    stateMessage.setText(R.string.selection_empty);
                    statePanel.setVisibility(View.VISIBLE);
                }
            }

            @Override public void onError(Exception error) {
                if (!isCurrent(version, type, "")) return;
                progress.setVisibility(View.GONE);
                adapter.submit(Collections.emptyList());
                stateMessage.setText(R.string.selection_load_error);
                statePanel.setVisibility(View.VISIBLE);
            }
        });
    }

    private void scheduleSearch() {
        requestVersion++;
        if (pendingSearch != null) searchHandler.removeCallbacks(pendingSearch);
        String query = currentQuery();
        String type = currentType();
        if (query.isEmpty()) {
            showOrLoad(type);
            return;
        }
        pendingSearch = () -> search(type, query);
        searchHandler.postDelayed(pendingSearch, 280);
    }

    private void search(String type, String query) {
        int version = ++requestVersion;
        progress.setVisibility(View.VISIBLE);
        statePanel.setVisibility(View.GONE);
        catalogRepository.suggest(type, query, new RepositoryCallback<List<Place>>() {
            @Override public void onSuccess(List<Place> places) {
                if (!isCurrent(version, type, query)) return;
                progress.setVisibility(View.GONE);
                adapter.submit(places);
                if (places.isEmpty()) {
                    stateMessage.setText(getString(R.string.selection_no_suggestion, query));
                    statePanel.setVisibility(View.VISIBLE);
                }
            }

            @Override public void onError(Exception error) {
                if (!isCurrent(version, type, query)) return;
                progress.setVisibility(View.GONE);
                adapter.submit(Collections.emptyList());
                stateMessage.setText(R.string.selection_search_error);
                statePanel.setVisibility(View.VISIBLE);
            }
        });
    }

    private boolean isCurrent(int version, String type, String query) {
        return version == requestVersion && type.equals(currentType()) && query.equals(currentQuery())
            && !isFinishing() && !isDestroyed();
    }

    private String currentType() {
        return ((Chip) findViewById(R.id.selectionEateryChip)).isChecked() ? "eatery" : "poi";
    }

    private String currentQuery() {
        return searchInput.getText() == null ? "" : searchInput.getText().toString().trim();
    }

    private void generate() {
        generateWithIds(adapter.selectedIds("poi"), adapter.selectedIds("eatery"));
    }

    private void generateWithIds(List<Integer> poiIds, List<Integer> eateryIds) {
        TripRequest selectedRequest = new TripRequest(request.getDays(), request.getDailyPoiLimit(),
            request.getBudgetVnd(), request.getMoods(), request.getStartAddress(), request.isUseDefaultCenter(),
            poiIds, eateryIds);
        setGenerating(true);
        generateUseCase.execute(selectedRequest, new RepositoryCallback<Itinerary>() {
            @Override public void onSuccess(Itinerary itinerary) {
                setGenerating(false);
                Intent intent = new Intent(PlaceSelectionActivity.this, ItineraryActivity.class);
                intent.putExtra(ItineraryActivity.EXTRA_ITINERARY, itinerary);
                startActivity(intent);
            }

            @Override public void onError(Exception error) {
                setGenerating(false);
                Toast.makeText(PlaceSelectionActivity.this,
                    error.getMessage() == null ? getString(R.string.generation_error) : error.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void updateSummary() {
        int poiMax = request.getDays() * request.getDailyPoiLimit();
        int eateryMax = request.getDays() * 3;
        selectionSummary.setText(getString(R.string.selection_summary, adapter.selectedCount("poi"), poiMax,
            adapter.selectedCount("eatery"), eateryMax));
    }

    private void setGenerating(boolean generating) {
        loadingOverlay.setVisibility(generating ? View.VISIBLE : View.GONE);
        findViewById(R.id.selectionGenerateButton).setEnabled(!generating);
        findViewById(R.id.selectionSkipButton).setEnabled(!generating);
    }

    private TripRequest readRequest() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return getIntent().getSerializableExtra(EXTRA_REQUEST, TripRequest.class);
        }
        return (TripRequest) getIntent().getSerializableExtra(EXTRA_REQUEST);
    }

    @Override protected void onDestroy() {
        requestVersion++;
        if (pendingSearch != null) searchHandler.removeCallbacks(pendingSearch);
        super.onDestroy();
    }
}
