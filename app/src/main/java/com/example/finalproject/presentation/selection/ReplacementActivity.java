package com.example.finalproject.presentation.selection;

import android.content.Context;
import android.content.Intent;
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
import com.example.finalproject.infrastructure.local.repository.CachingCatalogRepository;
import com.example.finalproject.infrastructure.remote.RemoteCatalogRepository;
import com.example.finalproject.infrastructure.remote.RemotePlannerRepository;
import com.example.finalproject.presentation.SystemBarInsets;
import com.example.finalproject.presentation.catalog.PlaceAdapter;
import com.google.android.material.textfield.TextInputEditText;

import java.util.Collections;
import java.util.List;

public class ReplacementActivity extends AppCompatActivity {
    public static final String EXTRA_PLACE = "replacement_place";
    private static final String EXTRA_TYPE = "replacement_type";

    private PlaceAdapter adapter;
    private View progress;
    private TextView state;
    private TextInputEditText search;
    private String type;
    private View statePanel;

    public static Intent intent(Context context, String type) {
        return new Intent(context, ReplacementActivity.class).putExtra(EXTRA_TYPE, type);
    }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_replacement);
        SystemBarInsets.apply(findViewById(R.id.replacementRoot));
        type = getIntent().getStringExtra(EXTRA_TYPE);
        if (!"eatery".equals(type)) type = "poi";

        ((TextView) findViewById(R.id.replacementTitle)).setText(
            "eatery".equals(type) ? R.string.replacement_eatery : R.string.replacement_poi);
        progress = findViewById(R.id.replacementProgress);
        state = findViewById(R.id.replacementState);
        statePanel = findViewById(R.id.replacementStatePanel);
        search = findViewById(R.id.replacementSearch);
        RecyclerView recycler = findViewById(R.id.replacementRecycler);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new PlaceAdapter(false, new PlaceAdapter.Listener() {
            @Override public void onOpen(Place place) {
                setResult(RESULT_OK, new Intent().putExtra(EXTRA_PLACE, place));
                finish();
            }
            @Override public boolean onSelectionChangeRequested(Place place, boolean selected) { return true; }
        });
        recycler.setAdapter(adapter);
        findViewById(R.id.replacementBack).setOnClickListener(v -> finish());
        findViewById(R.id.replacementRetry).setOnClickListener(v -> load());
        search.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) { load(); return true; }
            return false;
        });
        load();
    }

    private void load() {
        progress.setVisibility(View.VISIBLE);
        statePanel.setVisibility(View.GONE);
        String query = search.getText() == null ? "" : search.getText().toString().trim();
        new CachingCatalogRepository(this,
            new RemoteCatalogRepository(RemotePlannerRepository.DEFAULT_BASE_URL), null).load(type, query,
            new RepositoryCallback<List<Place>>() {
                @Override public void onSuccess(List<Place> places) {
                    progress.setVisibility(View.GONE);
                    adapter.submit(places);
                    if (places.isEmpty()) showState(getString(R.string.replacement_no_results));
                }
                @Override public void onError(Exception error) {
                    progress.setVisibility(View.GONE);
                    adapter.submit(Collections.emptyList());
                    showState(getString(R.string.replacement_error));
                }
            });
    }

    private void showState(String message) {
        state.setText(message);
        statePanel.setVisibility(View.VISIBLE);
    }
}
