package com.example.finalproject.presentation.planner;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.finalproject.R;
import com.example.finalproject.domain.callback.RepositoryCallback;
import com.example.finalproject.domain.model.DeviceLocation;
import com.example.finalproject.domain.model.Mood;
import com.example.finalproject.domain.model.TripArea;
import com.example.finalproject.domain.model.TripRequest;
import com.example.finalproject.domain.repository.LocationRepository;
import com.example.finalproject.infrastructure.device.AndroidLocationRepository;
import com.example.finalproject.presentation.selection.PlaceSelectionActivity;
import com.example.finalproject.presentation.SystemBarInsets;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
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
    private MaterialButton useCurrentLocationButton;
    private LocationRepository locationRepository;
    private ActivityResultLauncher<String[]> locationPermissionLauncher;

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

        locationRepository = new AndroidLocationRepository(this);
        // Both permissions are requested together: the system shows one dialog and the user
        // may grant only the coarse one, which is still good enough for a starting address.
        locationPermissionLauncher = registerForActivityResult(
            new ActivityResultContracts.RequestMultiplePermissions(), granted -> {
                boolean allowed = Boolean.TRUE.equals(granted.get(Manifest.permission.ACCESS_FINE_LOCATION))
                    || Boolean.TRUE.equals(granted.get(Manifest.permission.ACCESS_COARSE_LOCATION));
                if (allowed) readCurrentLocation();
                else showError(getString(R.string.location_permission_denied));
            });
        useCurrentLocationButton = findViewById(R.id.useCurrentLocationButton);
        useCurrentLocationButton.setOnClickListener(v -> {
            if (hasLocationPermission()) readCurrentLocation();
            else locationPermissionLauncher.launch(new String[]{
                Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION});
        });
    }

    private boolean hasLocationPermission() {
        return ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
            || ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void readCurrentLocation() {
        errorText.setVisibility(View.GONE);
        setLocationLoading(true);
        locationRepository.currentLocation(new RepositoryCallback<DeviceLocation>() {
            @Override public void onSuccess(DeviceLocation location) {
                // The fix can arrive after the user left the screen, so touch views only while alive.
                if (isFinishing() || isDestroyed()) return;
                setLocationLoading(false);
                if (TripArea.isInsideServiceArea(location.getLatitude(), location.getLongitude())) {
                    applyStartLocation(location);
                } else {
                    confirmDistantLocation(location);
                }
            }

            @Override public void onError(Exception error) {
                if (isFinishing() || isDestroyed()) return;
                setLocationLoading(false);
                showError(getString(R.string.location_unavailable));
            }
        });
    }

    private void applyStartLocation(DeviceLocation location) {
        // Django only reads start_address when use_default_center is false, so the switch
        // has to go off for the detected address to actually be used.
        defaultCenterSwitch.setChecked(false);
        // setText leaves the cursor at 0 so the field shows the street number first;
        // moving it to the end would scroll a long address past the house number.
        addressInput.setText(location.getAddress());
        Toast.makeText(this, R.string.location_filled, Toast.LENGTH_SHORT).show();
    }

    /**
     * The catalog only covers Da Lat, so a position far outside it would put a several hundred
     * kilometre leg at the front of the trip. Warn instead of blocking: a user planning a Da Lat
     * trip from home is a normal case, and they may still want their own address in there.
     */
    private void confirmDistantLocation(DeviceLocation location) {
        int distanceKm = (int) Math.round(
            TripArea.distanceFromCentreKm(location.getLatitude(), location.getLongitude()));
        new MaterialAlertDialogBuilder(this)
            .setTitle(R.string.location_far_title)
            .setMessage(getString(R.string.location_far_message, distanceKm))
            .setPositiveButton(R.string.location_use_dalat, (dialog, which) -> {
                // Leaves the switch on, which makes Django fall back to the Da Lat centre.
                defaultCenterSwitch.setChecked(true);
                addressInput.setText("");
            })
            .setNegativeButton(R.string.location_use_anyway, (dialog, which) -> applyStartLocation(location))
            .show();
    }

    private void setLocationLoading(boolean loading) {
        useCurrentLocationButton.setEnabled(!loading);
        useCurrentLocationButton.setText(loading ? R.string.locating : R.string.use_current_location);
    }

    @Override
    protected void onDestroy() {
        // Drops the location listener and its executor so a pending fix cannot outlive the screen.
        locationRepository.release();
        super.onDestroy();
    }

    private void continueToSelection() {
        errorText.setVisibility(View.GONE);
        Integer days = parseInteger(daysInput);
        Integer places = parseInteger(placesInput);
        Long budget = parseLong(budgetInput);
        if (days == null || places == null || budget == null) {
            showError(getString(R.string.planner_required_error));
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
