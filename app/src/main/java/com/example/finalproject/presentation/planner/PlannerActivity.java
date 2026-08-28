package com.example.finalproject.presentation.planner;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.finalproject.R;
import com.example.finalproject.application.usecase.GenerateItineraryUseCase;
import com.example.finalproject.domain.callback.RepositoryCallback;
import com.example.finalproject.domain.model.Itinerary;
import com.example.finalproject.domain.model.Mood;
import com.example.finalproject.domain.model.TripRequest;
import com.example.finalproject.infrastructure.demo.DemoPlannerRepository;
import com.example.finalproject.presentation.itinerary.ItineraryActivity;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.material.textfield.TextInputEditText;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class PlannerActivity extends AppCompatActivity {
    private TextInputEditText daysInput;
    private TextInputEditText placesInput;
    private TextInputEditText budgetInput;
    private TextInputEditText addressInput;
    private ChipGroup moodGroup;
    private SwitchMaterial defaultCenterSwitch;
    private TextView errorText;
    private View loadingPanel;
    private GenerateItineraryUseCase generateUseCase;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_planner);
        generateUseCase = new GenerateItineraryUseCase(new DemoPlannerRepository());

        daysInput = findViewById(R.id.daysInput);
        placesInput = findViewById(R.id.placesInput);
        budgetInput = findViewById(R.id.budgetInput);
        addressInput = findViewById(R.id.addressInput);
        moodGroup = findViewById(R.id.moodGroup);
        defaultCenterSwitch = findViewById(R.id.defaultCenterSwitch);
        errorText = findViewById(R.id.plannerErrorText);
        loadingPanel = findViewById(R.id.loadingPanel);

        findViewById(R.id.backButton).setOnClickListener(v -> finish());
        defaultCenterSwitch.setOnCheckedChangeListener((button, checked) -> {
            addressInput.setEnabled(!checked);
            if (checked) addressInput.setText("");
        });
        findViewById(R.id.generateButton).setOnClickListener(v -> generate());
    }

    private void generate() {
        errorText.setVisibility(View.GONE);
        Integer days = parseInteger(daysInput);
        Integer places = parseInteger(placesInput);
        Long budget = parseLong(budgetInput);
        if (days == null || places == null || budget == null) {
            showError("Hãy nhập đầy đủ số ngày, số địa điểm và ngân sách.");
            return;
        }

        List<Mood> moods = selectedMoods();
        TripRequest request = new TripRequest(days, places, budget, moods,
            text(addressInput), defaultCenterSwitch.isChecked(),
            Collections.emptyList(), Collections.emptyList());

        setLoading(true);
        generateUseCase.execute(request, new RepositoryCallback<Itinerary>() {
            @Override public void onSuccess(Itinerary itinerary) {
                setLoading(false);
                Intent intent = new Intent(PlannerActivity.this, ItineraryActivity.class);
                intent.putExtra(ItineraryActivity.EXTRA_ITINERARY, itinerary);
                startActivity(intent);
            }

            @Override public void onError(Exception error) {
                setLoading(false);
                showError(error.getMessage() == null ? "Không thể tạo lịch trình." : error.getMessage());
            }
        });
    }

    private List<Mood> selectedMoods() {
        List<Mood> result = new ArrayList<>();
        for (int i = 0; i < moodGroup.getChildCount(); i++) {
            View child = moodGroup.getChildAt(i);
            if (child instanceof Chip && ((Chip) child).isChecked()) {
                Object tag = child.getTag();
                if (tag != null) result.add(Mood.valueOf(tag.toString()));
            }
        }
        return result;
    }

    private Integer parseInteger(TextInputEditText input) {
        try { return Integer.parseInt(text(input)); } catch (NumberFormatException ignored) { return null; }
    }

    private Long parseLong(TextInputEditText input) {
        try { return Long.parseLong(text(input).replace(".", "").replace(",", "")); }
        catch (NumberFormatException ignored) { return null; }
    }

    private String text(TextInputEditText input) {
        return input.getText() == null ? "" : input.getText().toString().trim();
    }

    private void setLoading(boolean loading) {
        loadingPanel.setVisibility(loading ? View.VISIBLE : View.GONE);
        findViewById(R.id.generateButton).setEnabled(!loading);
    }

    private void showError(String message) {
        errorText.setText(message);
        errorText.setVisibility(View.VISIBLE);
    }
}
