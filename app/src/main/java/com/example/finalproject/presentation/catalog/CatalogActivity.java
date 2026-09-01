package com.example.finalproject.presentation.catalog;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.finalproject.R;
import com.example.finalproject.domain.callback.RepositoryCallback;
import com.example.finalproject.domain.model.Place;
import com.example.finalproject.domain.repository.CatalogRepository;
import com.example.finalproject.infrastructure.local.repository.CachingCatalogRepository;
import com.example.finalproject.infrastructure.remote.RemoteCatalogRepository;
import com.example.finalproject.infrastructure.remote.RemotePlannerRepository;
import com.example.finalproject.presentation.SystemBarInsets;
import com.google.android.material.textfield.TextInputEditText;

import java.util.List;
import java.util.Collections;

public class CatalogActivity extends AppCompatActivity {
    private CatalogRepository repository;
    private PlaceAdapter adapter;
    private TextInputEditText searchInput;
    private View progress;
    private View statePanel;
    private TextView stateTitle;
    private TextView stateMessage;
    private final Handler searchHandler = new Handler(Looper.getMainLooper());
    private Runnable pendingSearch;
    private int requestVersion;
    /** The notice is per screen visit, not per request, so scrolling does not spam toasts. */
    private boolean offlineNoticeShown;
    private List<Place> cachedPois = Collections.emptyList();
    private List<Place> cachedEateries = Collections.emptyList();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_catalog);
        SystemBarInsets.apply(findViewById(R.id.catalogRoot));
        repository = new CachingCatalogRepository(this,
            new RemoteCatalogRepository(RemotePlannerRepository.DEFAULT_BASE_URL),
            cachedAt -> showOfflineNotice());
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
        findViewById(R.id.catalogRetryButton).setOnClickListener(v -> loadCurrent(true));
        findViewById(R.id.poiChip).setOnClickListener(v -> showOrLoad("poi"));
        findViewById(R.id.eateryChip).setOnClickListener(v -> showOrLoad("eatery"));
        searchInput.setOnEditorActionListener((view, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                searchOrRestore();
                return true;
            }
            return false;
        });
        searchInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) { }
            @Override public void afterTextChanged(Editable s) { scheduleSearch(); }
        });
        loadCurrent(false);
    }

    private void loadCurrent(boolean force) {
        String type = currentType();
        if (force) {
            if ("poi".equals(type)) cachedPois = Collections.emptyList(); else cachedEateries = Collections.emptyList();
        }
        showOrLoad(type);
    }

    private void showOfflineNotice() {
        if (offlineNoticeShown) return;
        offlineNoticeShown = true;
        Toast.makeText(this, R.string.offline_catalog_notice, Toast.LENGTH_LONG).show();
    }

    private void showOrLoad(String type) {
        String query = currentQuery();
        if (!query.isEmpty()) { search(type, query); return; }
        List<Place> cached = "poi".equals(type) ? cachedPois : cachedEateries;
        if (cached.isEmpty()) load(type); else {
            requestVersion++;
            progress.setVisibility(View.GONE);
            statePanel.setVisibility(View.GONE);
            adapter.submit(cached);
        }
    }

    private void load(String type) {
        int version = ++requestVersion;
        showLoading();
        repository.load(type, "", new RepositoryCallback<List<Place>>() {
            @Override public void onSuccess(List<Place> places) {
                if (!isCurrent(version, type, "")) return;
                progress.setVisibility(View.GONE);
                if ("poi".equals(type)) cachedPois = places; else cachedEateries = places;
                if (places.isEmpty()) {
                    showState(getString(R.string.catalog_no_results_title), getString(R.string.catalog_no_results_message), false);
                } else {
                    statePanel.setVisibility(View.GONE);
                    adapter.submit(places);
                }
            }

            @Override public void onError(Exception error) {
                if (!isCurrent(version, type, "")) return;
                progress.setVisibility(View.GONE);
                adapter.submit(java.util.Collections.emptyList());
                showState(getString(R.string.catalog_connection_title), getString(R.string.catalog_connection_message), true);
            }
        });
    }

    private void scheduleSearch() {
        requestVersion++;
        if (pendingSearch != null) searchHandler.removeCallbacks(pendingSearch);
        if (currentQuery().isEmpty()) { showOrLoad(currentType()); return; }
        String type = currentType(), query = currentQuery();
        pendingSearch = () -> search(type, query);
        searchHandler.postDelayed(pendingSearch, 280);
    }

    private void searchOrRestore() { if (currentQuery().isEmpty()) showOrLoad(currentType()); else search(currentType(), currentQuery()); }

    private void search(String type, String query) {
        int version = ++requestVersion;
        showLoading();
        repository.suggest(type, query, new RepositoryCallback<List<Place>>() {
            @Override public void onSuccess(List<Place> places) {
                if (!isCurrent(version, type, query)) return;
                progress.setVisibility(View.GONE);
                adapter.submit(places);
                if (places.isEmpty()) showState(getString(R.string.catalog_no_results_title), getString(R.string.catalog_no_results_message), false);
                else statePanel.setVisibility(View.GONE);
            }
            @Override public void onError(Exception error) {
                if (!isCurrent(version, type, query)) return;
                progress.setVisibility(View.GONE);
                adapter.submit(Collections.emptyList());
                showState(getString(R.string.catalog_connection_title), getString(R.string.catalog_connection_message), true);
            }
        });
    }

    private String currentType() { return ((com.google.android.material.chip.Chip) findViewById(R.id.eateryChip)).isChecked() ? "eatery" : "poi"; }
    private String currentQuery() { return searchInput.getText() == null ? "" : searchInput.getText().toString().trim(); }
    private boolean isCurrent(int version, String type, String query) {
        return version == requestVersion && type.equals(currentType()) && query.equals(currentQuery()) && !isFinishing() && !isDestroyed();
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
