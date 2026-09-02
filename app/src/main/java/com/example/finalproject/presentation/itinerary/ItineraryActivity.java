package com.example.finalproject.presentation.itinerary;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;
import android.net.Uri;
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
import androidx.core.content.FileProvider;

import com.example.finalproject.R;
import com.example.finalproject.domain.model.Itinerary;
import com.example.finalproject.domain.model.ItineraryDay;
import com.example.finalproject.domain.model.ItineraryEditor;
import com.example.finalproject.domain.model.ItineraryStop;
import com.example.finalproject.domain.model.Place;
import com.example.finalproject.presentation.SystemBarInsets;
import com.example.finalproject.presentation.MainActivity;
import com.example.finalproject.presentation.map.MapActivity;
import com.example.finalproject.presentation.catalog.PlaceDetailActivity;
import com.example.finalproject.presentation.selection.ReplacementActivity;
import com.example.finalproject.infrastructure.remote.RemoteImageLoader;
import com.example.finalproject.infrastructure.remote.RemotePlannerRepository;
import com.example.finalproject.infrastructure.remote.RemoteItineraryShareRepository;
import com.example.finalproject.infrastructure.remote.RemoteWeatherRepository;
import com.example.finalproject.infrastructure.local.export.ItineraryPdfExporter;
import com.example.finalproject.domain.model.ItineraryShareData;
import com.example.finalproject.domain.model.WeatherSnapshot;
import com.example.finalproject.infrastructure.local.repository.RoomSavedTripRepository;
import com.example.finalproject.domain.callback.RepositoryCallback;
import android.widget.Toast;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;

import java.text.NumberFormat;
import java.util.Locale;
import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ItineraryActivity extends AppCompatActivity {
    public static final String EXTRA_ITINERARY = "itinerary";

    private Itinerary itinerary;
    private LinearLayout stopsContainer;
    private ItineraryDay selectedDay;
    private int pendingDayNumber;
    private int pendingStopIndex;
    private ActivityResultLauncher<Intent> replacementLauncher;
    private final ExecutorService pdfExecutor = Executors.newSingleThreadExecutor();

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
                Toast.makeText(this, getString(R.string.replaced_with, place.getName()), Toast.LENGTH_SHORT).show();
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
                    Intent home = new Intent(ItineraryActivity.this, MainActivity.class);
                    home.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                    home.putExtra(MainActivity.EXTRA_DESTINATION, MainActivity.DESTINATION_TRIPS);
                    home.putExtra(MainActivity.EXTRA_MESSAGE, getString(R.string.trip_saved_message));
                    startActivity(home);
                    finish();
                }
                @Override public void onError(Exception error) {
                    v.setEnabled(true);
                    Toast.makeText(ItineraryActivity.this, R.string.trip_save_error, Toast.LENGTH_SHORT).show();
                }
            });
        });
        findViewById(R.id.openMapButton).setOnClickListener(v -> {
            if (selectedDay == null) return;
            Intent intent = new Intent(this, MapActivity.class);
            intent.putExtra(MapActivity.EXTRA_DAY, selectedDay);
            startActivity(intent);
        });
        findViewById(R.id.shareTripButton).setOnClickListener(v -> shareItinerary());
        findViewById(R.id.exportPdfButton).setOnClickListener(v -> exportPdf());

        ChipGroup chipGroup = findViewById(R.id.dayChipGroup);
        for (ItineraryDay day : itinerary.getDays()) {
            Chip chip = new Chip(this);
            chip.setId(android.view.View.generateViewId());
            chip.setText(getString(R.string.day_label, day.getDayNumber()));
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
        TextView heading = text(getString(R.string.day_itinerary, day.getDayNumber()), 20, true, R.color.text_primary);
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
                TextView distance = text(getString(R.string.next_distance, stop.getTravelToNextKm()),
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
                details.setText(R.string.details);
                details.setTextSize(12);
                details.setOnClickListener(v -> openDetails(stop));
                MaterialButton replace = new MaterialButton(this);
                replace.setText(R.string.replace);
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
            stop.getOpenHours(), stop.getTags(), stop.getHighlight(), stop.getMediaUrl(),
            stop.getMapName(), stop.getMapAddress());
        startActivity(PlaceDetailActivity.intent(this, place));
    }

    private void updateBudget() {
        ((TextView) findViewById(R.id.estimatedCostText)).setText(money(itinerary.getEstimatedCostVnd()));
        ((TextView) findViewById(R.id.remainingCostText)).setText(money(itinerary.getRemainingBudgetVnd()));
    }

    private void shareItinerary() {
        StringBuilder message = new StringBuilder(itinerary.getTitle())
            .append('\n').append(getString(R.string.share_cost_line, money(itinerary.getEstimatedCostVnd()))).append('\n');
        for (ItineraryDay day : itinerary.getDays()) {
            message.append('\n').append(getString(R.string.day_label, day.getDayNumber())).append(":\n");
            for (ItineraryStop stop : day.getStops()) {
                if (stop.getType() != ItineraryStop.Type.ACCOMMODATION) {
                    message.append("• ").append(stop.getName());
                    if (stop.getMealSlot() != null) message.append(" (").append(stop.getMealSlot()).append(")");
                    message.append('\n');
                }
            }
        }
        Intent share = new Intent(Intent.ACTION_SEND);
        share.setType("text/plain");
        share.putExtra(Intent.EXTRA_SUBJECT, itinerary.getTitle());
        share.putExtra(Intent.EXTRA_TEXT, message.toString());
        startActivity(Intent.createChooser(share, getString(R.string.share_via)));
    }

    private void exportPdf() {
        MaterialButton button = findViewById(R.id.exportPdfButton);
        button.setEnabled(false); button.setText(R.string.creating_pdf);
        new RemoteItineraryShareRepository(RemotePlannerRepository.DEFAULT_BASE_URL).create(itinerary,
            new RepositoryCallback<ItineraryShareData>() {
                @Override public void onSuccess(ItineraryShareData data) {
                    new RemoteWeatherRepository().load(itinerary.getDays().size(), new RepositoryCallback<WeatherSnapshot>() {
                        @Override public void onSuccess(WeatherSnapshot weather) {
                            createVisualPdf(button, data, weather);
                        }
                        @Override public void onError(Exception error) {
                            createVisualPdf(button, data, null);
                        }
                    });
                }
                @Override public void onError(Exception e) { showPdfError(button, e); }
            });
    }

    private void createVisualPdf(MaterialButton button, ItineraryShareData data, WeatherSnapshot weather) {
        pdfExecutor.execute(() -> {
            try {
                byte[] bytes = Base64.decode(data.getQrBase64(), Base64.DEFAULT);
                Bitmap qr = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
                File file = new ItineraryPdfExporter().export(ItineraryActivity.this, itinerary, qr,
                    data.getShareUrl(), weather);
                runOnUiThread(() -> { resetPdfButton(button); sharePdf(file); });
            } catch (Exception error) {
                runOnUiThread(() -> showPdfError(button, error));
            }
        });
    }

    private void resetPdfButton(MaterialButton button) { button.setEnabled(true); button.setText(R.string.export_pdf_qr); }
    private void showPdfError(MaterialButton button, Exception e) { resetPdfButton(button); Toast.makeText(this, R.string.pdf_error, Toast.LENGTH_LONG).show(); }
    private void sharePdf(File file) {
        Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".fileprovider", file);
        Intent share = new Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM, uri)
            .putExtra(Intent.EXTRA_SUBJECT, itinerary.getTitle()).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        share.setClipData(android.content.ClipData.newRawUri("Journify PDF", uri));
        startActivity(Intent.createChooser(share, getString(R.string.share_pdf)));
    }

    private String marker(ItineraryStop stop) {
        if (stop.getType() == ItineraryStop.Type.ACCOMMODATION) return "⌂";
        if (stop.getType() == ItineraryStop.Type.EATERY) return "☕";
        return "●";
    }

    private String typeLabel(ItineraryStop stop) {
        if (stop.getType() == ItineraryStop.Type.ACCOMMODATION) return getString(R.string.starting_location_type);
        if (stop.getType() == ItineraryStop.Type.EATERY) return getString(R.string.eatery_stop_type);
        return getString(R.string.poi_stop_type);
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
        return NumberFormat.getNumberInstance(Locale.getDefault()).format(Math.max(0, value)) + " ₫";
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
