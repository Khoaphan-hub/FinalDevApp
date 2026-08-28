package com.example.finalproject.presentation.planner;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.finalproject.R;
import com.example.finalproject.domain.model.Mood;
import com.example.finalproject.domain.model.TripRequest;
import com.example.finalproject.presentation.selection.PlaceSelectionActivity;
import com.example.finalproject.presentation.SystemBarInsets;
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

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_planner);
        SystemBarInsets.apply(findViewById(R.id.plannerRoot));
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
        findViewById(R.id.generateButton).setOnClickListener(v -> continueToSelection());
    }

    private void continueToSelection() {
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
        Intent intent = new Intent(this, PlaceSelectionActivity.class);
        intent.putExtra(PlaceSelectionActivity.EXTRA_REQUEST, request);
        startActivity(intent);
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
