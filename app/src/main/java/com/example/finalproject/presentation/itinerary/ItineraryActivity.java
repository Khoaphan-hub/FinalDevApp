package com.example.finalproject.presentation.itinerary;

import android.content.Intent;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.example.finalproject.R;
import com.example.finalproject.domain.model.Itinerary;
import com.example.finalproject.domain.model.ItineraryDay;
import com.example.finalproject.domain.model.ItineraryEditor;
import com.example.finalproject.domain.model.ItineraryStop;
import com.example.finalproject.domain.model.Place;
import com.example.finalproject.presentation.SystemBarInsets;
import com.example.finalproject.presentation.map.MapActivity;
import com.example.finalproject.presentation.catalog.PlaceDetailActivity;
import com.example.finalproject.presentation.selection.ReplacementActivity;
import com.example.finalproject.infrastructure.remote.RemoteImageLoader;
import com.example.finalproject.infrastructure.local.repository.RoomSavedTripRepository;
import com.example.finalproject.domain.callback.RepositoryCallback;
import android.widget.Toast;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.text.NumberFormat;
import java.util.Locale;

public class ItineraryActivity extends AppCompatActivity {
    public static final String EXTRA_ITINERARY = "itinerary";

    private Itinerary itinerary;
    private LinearLayout stopsContainer;
    private ItineraryDay selectedDay;
    private int pendingDayNumber;
    private int pendingStopIndex;
    private ActivityResultLauncher<Intent> replacementLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_itinerary);
        SystemBarInsets.apply(findViewById(R.id.itineraryRoot));
        replacementLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() != RESULT_OK || result.getData() == null) return;
                Place place;
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    place = result.getData().getSerializableExtra(ReplacementActivity.EXTRA_PLACE, Place.class);
                } else {
                    place = (Place) result.getData().getSerializableExtra(ReplacementActivity.EXTRA_PLACE);
                }
                if (place == null) return;
                itinerary = ItineraryEditor.replace(itinerary, pendingDayNumber, pendingStopIndex, place);
                updateBudget();
                for (ItineraryDay day : itinerary.getDays()) {
                    if (day.getDayNumber() == pendingDayNumber) { renderDay(day); break; }
                }
                Toast.makeText(this, "Đã thay bằng " + place.getName(), Toast.LENGTH_SHORT).show();
            });

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            itinerary = getIntent().getSerializableExtra(EXTRA_ITINERARY, Itinerary.class);
        } else {
            itinerary = (Itinerary) getIntent().getSerializableExtra(EXTRA_ITINERARY);
        }
        if (itinerary == null || itinerary.getDays().isEmpty()) {
            finish();
            return;
        }

        stopsContainer = findViewById(R.id.stopsContainer);
        ((TextView) findViewById(R.id.itineraryTitle)).setText(itinerary.getTitle());
        updateBudget();
        findViewById(R.id.offlineBadge).setVisibility(itinerary.isOfflineDemo() ? TextView.VISIBLE : TextView.GONE);
        findViewById(R.id.resultBackButton).setOnClickListener(v -> finish());
        findViewById(R.id.saveTripButton).setOnClickListener(v -> {
            v.setEnabled(false);
            new RoomSavedTripRepository(this).save(itinerary, new RepositoryCallback<Long>() {
                @Override public void onSuccess(Long id) {
                    v.setEnabled(true);
                    ((com.google.android.material.button.MaterialButton) v).setText("Đã lưu");
                    Toast.makeText(ItineraryActivity.this, "Đã lưu chuyến đi trên thiết bị.", Toast.LENGTH_SHORT).show();
                }
                @Override public void onError(Exception error) {
                    v.setEnabled(true);
                    Toast.makeText(ItineraryActivity.this, "Không thể lưu chuyến đi.", Toast.LENGTH_SHORT).show();
                }
            });
        });
        findViewById(R.id.openMapButton).setOnClickListener(v -> {
            if (selectedDay == null) return;
            Intent intent = new Intent(this, MapActivity.class);
            intent.putExtra(MapActivity.EXTRA_DAY, selectedDay);
            startActivity(intent);
        });

        ChipGroup chipGroup = findViewById(R.id.dayChipGroup);
        for (ItineraryDay day : itinerary.getDays()) {
            Chip chip = new Chip(this);
            chip.setId(android.view.View.generateViewId());
            chip.setText("Ngày " + day.getDayNumber());
            chip.setCheckable(true);
            chip.setTag(day);
            chip.setOnClickListener(v -> renderDay((ItineraryDay) v.getTag()));
            chipGroup.addView(chip);
        }
        ((Chip) chipGroup.getChildAt(0)).setChecked(true);
        renderDay(itinerary.getDays().get(0));
    }

    private void renderDay(ItineraryDay day) {
        selectedDay = day;
        stopsContainer.removeAllViews();
        TextView heading = text("Lịch trình ngày " + day.getDayNumber(), 20, true, R.color.text_primary);
        heading.setPadding(0, dp(8), 0, dp(12));
        stopsContainer.addView(heading);

        for (int index = 0; index < day.getStops().size(); index++) {
            ItineraryStop stop = day.getStops().get(index);
            MaterialCardView card = new MaterialCardView(this);
            card.setRadius(dp(20));
            card.setCardElevation(0);
            card.setCardBackgroundColor(getColor(stop.getType() == ItineraryStop.Type.EATERY
                ? R.color.accent_soft : R.color.card_background));
            LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            cardParams.bottomMargin = dp(10);
            card.setLayoutParams(cardParams);

            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.TOP);
            row.setPadding(dp(16), dp(16), dp(16), dp(16));

            TextView marker = text(marker(stop), 20, false, R.color.primary);
            marker.setGravity(Gravity.CENTER);
            row.addView(marker, new LinearLayout.LayoutParams(dp(44), dp(44)));

            LinearLayout content = new LinearLayout(this);
            content.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams contentParams = new LinearLayout.LayoutParams(0,
                ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            contentParams.leftMargin = dp(10);
            row.addView(content, contentParams);

            TextView type = text(typeLabel(stop), 11, true,
                stop.getType() == ItineraryStop.Type.EATERY ? R.color.accent : R.color.primary);
            content.addView(type);
            TextView name = text(stop.getName(), 17, true, R.color.text_primary);
            name.setPadding(0, dp(4), 0, 0);
            content.addView(name);
            TextView address = text(stop.getAddress(), 13, false, R.color.text_secondary);
            address.setPadding(0, dp(4), 0, 0);
            content.addView(address);
            if (stop.getTravelToNextKm() > 0) {
                TextView distance = text(String.format(Locale.getDefault(), "Tiếp theo • %.1f km", stop.getTravelToNextKm()),
                    12, true, R.color.primary);
                distance.setPadding(0, dp(9), 0, 0);
                content.addView(distance);
            }
            if (stop.getType() != ItineraryStop.Type.ACCOMMODATION) {
                LinearLayout actions = new LinearLayout(this);
                actions.setOrientation(LinearLayout.HORIZONTAL);
                actions.setPadding(0, dp(10), 0, 0);
                MaterialButton details = new MaterialButton(this, null,
                    com.google.android.material.R.attr.materialButtonOutlinedStyle);
                details.setText("Chi tiết");
                details.setTextSize(12);
                details.setOnClickListener(v -> openDetails(stop));
                MaterialButton replace = new MaterialButton(this);
                replace.setText("Thay đổi");
                replace.setTextSize(12);
                final int selectedIndex = index;
                replace.setOnClickListener(v -> openReplacement(day.getDayNumber(), selectedIndex, stop));
                LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(0, dp(42), 1);
                actionParams.rightMargin = dp(8);
                actions.addView(details, actionParams);
                actions.addView(replace, new LinearLayout.LayoutParams(0, dp(42), 1));
                content.addView(actions);
            }
            LinearLayout cardContent = new LinearLayout(this);
            cardContent.setOrientation(LinearLayout.VERTICAL);
            if (stop.getType() != ItineraryStop.Type.ACCOMMODATION) {
                ImageView image = new ImageView(this);
                image.setScaleType(ImageView.ScaleType.CENTER_CROP);
                image.setContentDescription(stop.getName());
                image.setImageResource(stop.getType() == ItineraryStop.Type.EATERY
                    ? R.drawable.sample_eatery : R.drawable.sample_poi);
                cardContent.addView(image, new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(112)));
                RemoteImageLoader.load(stop.getImageUrl(), image);
            }
            cardContent.addView(row);
            card.addView(cardContent);
            stopsContainer.addView(card);
        }
    }

    private void openReplacement(int dayNumber, int stopIndex, ItineraryStop stop) {
        pendingDayNumber = dayNumber;
        pendingStopIndex = stopIndex;
        String type = stop.getType() == ItineraryStop.Type.EATERY ? "eatery" : "poi";
        replacementLauncher.launch(ReplacementActivity.intent(this, type));
    }

    private void openDetails(ItineraryStop stop) {
        Place place = new Place(stop.getId(), stop.getType().name(), stop.getName(), stop.getAddress(),
            stop.getRating(), stop.getPriceVnd(), stop.getImageUrl(), stop.getLatitude(), stop.getLongitude(),
            stop.getOpenHours(), stop.getTags(), stop.getHighlight(), stop.getMediaUrl());
        startActivity(PlaceDetailActivity.intent(this, place));
    }

    private void updateBudget() {
        ((TextView) findViewById(R.id.estimatedCostText)).setText(money(itinerary.getEstimatedCostVnd()));
        ((TextView) findViewById(R.id.remainingCostText)).setText(money(itinerary.getRemainingBudgetVnd()));
    }

    private String marker(ItineraryStop stop) {
        if (stop.getType() == ItineraryStop.Type.ACCOMMODATION) return "⌂";
        if (stop.getType() == ItineraryStop.Type.EATERY) return "☕";
        return "●";
    }

    private String typeLabel(ItineraryStop stop) {
        if (stop.getType() == ItineraryStop.Type.ACCOMMODATION) return "ĐIỂM XUẤT PHÁT";
        if (stop.getType() == ItineraryStop.Type.EATERY) return "ĐIỂM ĂN UỐNG";
        return "ĐỊA ĐIỂM THAM QUAN";
    }

    private TextView text(String value, int size, boolean bold, int colorResource) {
        TextView view = new TextView(this);
        view.setText(value == null ? "" : value);
        view.setTextSize(size);
        view.setTextColor(getColor(colorResource));
        if (bold) view.setTypeface(view.getTypeface(), android.graphics.Typeface.BOLD);
        return view;
    }

    private String money(long value) {
        return NumberFormat.getNumberInstance(new Locale("vi", "VN")).format(Math.max(0, value)) + " ₫";
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
