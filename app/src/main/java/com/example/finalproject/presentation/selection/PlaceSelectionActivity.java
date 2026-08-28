package com.example.finalproject.presentation.selection;

import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
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
import com.example.finalproject.domain.repository.CatalogRepository;
import com.example.finalproject.infrastructure.demo.DemoPlannerRepository;
import com.example.finalproject.infrastructure.remote.RemoteCatalogRepository;
import com.example.finalproject.infrastructure.remote.RemotePlannerRepository;
import com.example.finalproject.infrastructure.remote.ResilientPlannerRepository;
import com.example.finalproject.presentation.SystemBarInsets;
import com.example.finalproject.presentation.catalog.PlaceAdapter;
import com.example.finalproject.presentation.catalog.PlaceDetailActivity;
import com.example.finalproject.presentation.itinerary.ItineraryActivity;
import com.google.android.material.chip.Chip;

import java.util.Collections;
import java.util.List;

public class PlaceSelectionActivity extends AppCompatActivity {
    public static final String EXTRA_REQUEST = "trip_request";

    private TripRequest request;
    private PlaceAdapter adapter;
    private CatalogRepository catalogRepository;
    private GenerateItineraryUseCase generateUseCase;
    private View progress;
    private View loadingOverlay;
    private TextView selectionSummary;
    private TextView stateMessage;
    private View statePanel;
    private List<Place> cachedPois = Collections.emptyList();
    private List<Place> cachedEateries = Collections.emptyList();

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
                        "Bạn đã chọn đủ " + max + " địa điểm loại này.", Toast.LENGTH_SHORT).show();
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
        updateSummary();
        loadCurrent(false);
    }

    private void showOrLoad(String type) {
        List<Place> cached = "poi".equals(type) ? cachedPois : cachedEateries;
        if (cached.isEmpty()) load(type); else adapter.submit(cached);
    }

    private void loadCurrent(boolean force) {
        String type = ((Chip) findViewById(R.id.selectionEateryChip)).isChecked() ? "eatery" : "poi";
        if (force) {
            if ("poi".equals(type)) cachedPois = Collections.emptyList(); else cachedEateries = Collections.emptyList();
        }
        showOrLoad(type);
    }

    private void load(String type) {
        progress.setVisibility(View.VISIBLE);
        statePanel.setVisibility(View.GONE);
        catalogRepository.load(type, "", new RepositoryCallback<List<Place>>() {
            @Override public void onSuccess(List<Place> places) {
                progress.setVisibility(View.GONE);
                if ("poi".equals(type)) cachedPois = places; else cachedEateries = places;
                adapter.submit(places);
                if (places.isEmpty()) {
                    stateMessage.setText("Chưa có dữ liệu. Bạn vẫn có thể để Journify tự tạo.");
                    statePanel.setVisibility(View.VISIBLE);
                }
            }

            @Override public void onError(Exception error) {
                progress.setVisibility(View.GONE);
                stateMessage.setText("Không tải được danh sách từ Django. Bật backend rồi thử lại, hoặc chọn tự động tạo.");
                statePanel.setVisibility(View.VISIBLE);
            }
        });
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
                    error.getMessage() == null ? "Không thể tạo lịch trình." : error.getMessage(), Toast.LENGTH_LONG).show();
            }
        });
    }

    private void updateSummary() {
        int poiMax = request.getDays() * request.getDailyPoiLimit();
        int eateryMax = request.getDays() * 3;
        selectionSummary.setText("Đã chọn " + adapter.selectedCount("poi") + "/" + poiMax
            + " điểm tham quan • " + adapter.selectedCount("eatery") + "/" + eateryMax
            + " quán ăn\nJournify sẽ tự điền những chỗ còn thiếu.");
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
}
