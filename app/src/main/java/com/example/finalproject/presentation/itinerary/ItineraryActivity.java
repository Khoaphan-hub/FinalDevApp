package com.example.finalproject.presentation.itinerary;

import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.finalproject.R;
import com.example.finalproject.domain.model.Itinerary;
import com.example.finalproject.domain.model.ItineraryDay;
import com.example.finalproject.domain.model.ItineraryStop;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.text.NumberFormat;
import java.util.Locale;

public class ItineraryActivity extends AppCompatActivity {
    public static final String EXTRA_ITINERARY = "itinerary";

    private Itinerary itinerary;
    private LinearLayout stopsContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_itinerary);

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
        ((TextView) findViewById(R.id.estimatedCostText)).setText(money(itinerary.getEstimatedCostVnd()));
        ((TextView) findViewById(R.id.remainingCostText)).setText(money(itinerary.getRemainingBudgetVnd()));
        findViewById(R.id.offlineBadge).setVisibility(itinerary.isOfflineDemo() ? TextView.VISIBLE : TextView.GONE);
        findViewById(R.id.resultBackButton).setOnClickListener(v -> finish());

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
            card.addView(row);
            stopsContainer.addView(card);
        }
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
