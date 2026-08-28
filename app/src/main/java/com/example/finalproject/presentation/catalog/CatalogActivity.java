package com.example.finalproject.presentation.catalog;

import android.os.Bundle;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.finalproject.R;
import com.example.finalproject.domain.callback.RepositoryCallback;
import com.example.finalproject.domain.model.Place;
import com.example.finalproject.domain.repository.CatalogRepository;
import com.example.finalproject.infrastructure.remote.RemoteCatalogRepository;
import com.example.finalproject.infrastructure.remote.RemotePlannerRepository;
import com.example.finalproject.presentation.SystemBarInsets;
import com.google.android.material.textfield.TextInputEditText;

import java.util.List;

public class CatalogActivity extends AppCompatActivity {
    private CatalogRepository repository;
    private PlaceAdapter adapter;
    private TextInputEditText searchInput;
    private View progress;
    private View statePanel;
    private TextView stateTitle;
    private TextView stateMessage;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_catalog);
        SystemBarInsets.apply(findViewById(R.id.catalogRoot));
        repository = new RemoteCatalogRepository(RemotePlannerRepository.DEFAULT_BASE_URL);
        searchInput = findViewById(R.id.catalogSearchInput);
        progress = findViewById(R.id.catalogProgress);
        statePanel = findViewById(R.id.catalogStatePanel);
        stateTitle = findViewById(R.id.catalogStateTitle);
        stateMessage = findViewById(R.id.catalogStateMessage);

        RecyclerView recycler = findViewById(R.id.catalogRecyclerView);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new PlaceAdapter(false, new PlaceAdapter.Listener() {
            @Override public void onOpen(Place place) {
                startActivity(PlaceDetailActivity.intent(CatalogActivity.this, place));
            }
            @Override public boolean onSelectionChangeRequested(Place place, boolean selected) { return true; }
        });
        recycler.setAdapter(adapter);

        findViewById(R.id.catalogBackButton).setOnClickListener(v -> finish());
        findViewById(R.id.catalogRetryButton).setOnClickListener(v -> load());
        findViewById(R.id.poiChip).setOnClickListener(v -> load());
        findViewById(R.id.eateryChip).setOnClickListener(v -> load());
        searchInput.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                load();
                return true;
            }
            return false;
        });
        load();
    }

    private void load() {
        showLoading();
        String type = findViewById(R.id.eateryChip).isSelected() ||
            ((com.google.android.material.chip.Chip) findViewById(R.id.eateryChip)).isChecked()
            ? "eatery" : "poi";
        String query = searchInput.getText() == null ? "" : searchInput.getText().toString().trim();
        repository.load(type, query, new RepositoryCallback<List<Place>>() {
            @Override public void onSuccess(List<Place> places) {
                progress.setVisibility(View.GONE);
                if (places.isEmpty()) {
                    showState("Không tìm thấy địa điểm", "Hãy thử từ khóa khác hoặc đổi loại địa điểm.", false);
                } else {
                    statePanel.setVisibility(View.GONE);
                    adapter.submit(places);
                }
            }

            @Override public void onError(Exception error) {
                progress.setVisibility(View.GONE);
                adapter.submit(java.util.Collections.emptyList());
                showState("Không thể kết nối Django", "Hãy bật backend hoặc kiểm tra cùng mạng Wi-Fi rồi thử lại.", true);
            }
        });
    }

    private void showLoading() {
        statePanel.setVisibility(View.GONE);
        progress.setVisibility(View.VISIBLE);
    }

    private void showState(String title, String message, boolean retry) {
        stateTitle.setText(title);
        stateMessage.setText(message);
        findViewById(R.id.catalogRetryButton).setVisibility(retry ? View.VISIBLE : View.GONE);
        statePanel.setVisibility(View.VISIBLE);
    }
}
