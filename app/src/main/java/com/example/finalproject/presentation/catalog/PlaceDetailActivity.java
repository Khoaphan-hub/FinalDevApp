package com.example.finalproject.presentation.catalog;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.finalproject.R;
import com.example.finalproject.domain.model.Place;
import com.example.finalproject.infrastructure.link.ExternalPlaceLinks;
import com.example.finalproject.infrastructure.remote.RemoteImageLoader;
import com.example.finalproject.infrastructure.remote.RemotePlaceReportRepository;
import com.example.finalproject.infrastructure.remote.RemotePlannerRepository;
import com.example.finalproject.presentation.SystemBarInsets;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.MaterialAutoCompleteTextView;
import com.google.android.material.textfield.TextInputEditText;
import com.google.android.material.textfield.TextInputLayout;

import java.text.NumberFormat;
import java.util.Locale;

public class PlaceDetailActivity extends AppCompatActivity {
    private static final String EXTRA_PLACE = "place";
    private static final String[] REPORT_CATEGORIES = {
        "CLOSED", "TEMPORARILY_CLOSED", "WRONG_PRICE", "WRONG_HOURS",
        "WRONG_ADDRESS", "BROKEN_REVIEW", "WRONG_IMAGE", "DUPLICATE", "OTHER"
    };

    public static Intent intent(Context context, Place place) {
        return new Intent(context, PlaceDetailActivity.class).putExtra(EXTRA_PLACE, place);
    }

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_place_detail);
        SystemBarInsets.apply(findViewById(R.id.placeDetailRoot));
        Place place = readPlace();
        if (place == null) { finish(); return; }

        findViewById(R.id.placeDetailBack).setOnClickListener(v -> finish());
        ImageView image = findViewById(R.id.placeDetailImage);
        image.setImageResource("EATERY".equals(place.getType()) ? R.drawable.sample_eatery : R.drawable.sample_poi);
        RemoteImageLoader.load(place.getImageUrl(), image);
        set(R.id.placeDetailType, getString("EATERY".equals(place.getType()) ? R.string.detail_eatery : R.string.detail_poi));
        set(R.id.placeDetailName, place.getName());
        set(R.id.placeDetailAddress, place.getAddress());
        set(R.id.placeDetailRating, place.getRating() > 0
            ? String.format(Locale.getDefault(), "★ %.1f", place.getRating()) : getString(R.string.no_rating));
        set(R.id.placeDetailPrice, place.getPriceVnd() > 0
            ? getString(R.string.price_per_person, NumberFormat.getNumberInstance(Locale.getDefault()).format(place.getPriceVnd()))
            : getString(R.string.price_unknown));
        bindOptional(R.id.placeDetailHours, getString(R.string.opening_hours), place.getOpenHours());
        bindOptional(R.id.placeDetailTags, getString(R.string.suitable_for), place.getTags());
        bindOptional(R.id.placeDetailHighlight, getString(R.string.highlight), place.getHighlight());

        MaterialButton mediaButton = findViewById(R.id.placeMediaButton);
        if (isBlank(place.getMediaUrl())) mediaButton.setVisibility(View.GONE);
        else {
            String url = ExternalPlaceLinks.cleanReviewUrl(place.getMediaUrl());
            mediaButton.setText(url.contains("tiktok.com") ? getString(R.string.view_tiktok_review)
                : url.contains("google.com/maps") || url.contains("maps.app.goo.gl")
                ? getString(R.string.view_google_review) : getString(R.string.open_review_link));
            mediaButton.setOnClickListener(v -> openUrl(url));
        }
        findViewById(R.id.placeDirectionsButton).setOnClickListener(v -> {
            Uri uri = Uri.parse(ExternalPlaceLinks.googleMapsSearchUrl(
                place.getMapName(), place.getMapAddress(),
                place.getLatitude(), place.getLongitude()));
            openIntent(new Intent(Intent.ACTION_VIEW, uri));
        });
        findViewById(R.id.placeReportButton).setOnClickListener(v -> showReportDialog(place));
    }

    private void showReportDialog(Place place) {
        View content = LayoutInflater.from(this).inflate(R.layout.dialog_place_report, null);
        ((TextView) content.findViewById(R.id.reportPlaceName)).setText(place.getName());
        TextInputLayout categoryLayout = content.findViewById(R.id.reportCategoryLayout);
        MaterialAutoCompleteTextView categoryInput = content.findViewById(R.id.reportCategoryInput);
        TextInputLayout descriptionLayout = content.findViewById(R.id.reportDescriptionLayout);
        TextInputEditText descriptionInput = content.findViewById(R.id.reportDescriptionInput);
        String[] labels = getResources().getStringArray(R.array.place_report_categories);
        categoryInput.setAdapter(new ArrayAdapter<>(
            this, android.R.layout.simple_dropdown_item_1line, labels));
        final int[] selectedCategory = {-1};
        categoryInput.setOnItemClickListener((parent, view, position, id) -> {
            selectedCategory[0] = position;
            categoryLayout.setError(null);
        });

        androidx.appcompat.app.AlertDialog dialog = new MaterialAlertDialogBuilder(this)
            .setTitle(R.string.report_dialog_title)
            .setView(content)
            .setNegativeButton(R.string.report_cancel, null)
            .setPositiveButton(R.string.report_send, null)
            .create();
        dialog.setOnShowListener(ignored -> dialog.getButton(
            DialogInterface.BUTTON_POSITIVE).setOnClickListener(v -> {
                String description = descriptionInput.getText() == null
                    ? "" : descriptionInput.getText().toString().trim();
                boolean valid = true;
                if (selectedCategory[0] < 0) {
                    categoryLayout.setError(getString(R.string.report_category_required));
                    valid = false;
                }
                if (description.isEmpty()) {
                    descriptionLayout.setError(getString(R.string.report_description_required));
                    valid = false;
                } else {
                    descriptionLayout.setError(null);
                }
                if (!valid) return;

                Button sendButton = dialog.getButton(DialogInterface.BUTTON_POSITIVE);
                sendButton.setEnabled(false);
                sendButton.setText(R.string.report_submitting);
                categoryInput.setEnabled(false);
                descriptionInput.setEnabled(false);
                new RemotePlaceReportRepository(RemotePlannerRepository.DEFAULT_BASE_URL).submit(
                    place, REPORT_CATEGORIES[selectedCategory[0]], description,
                    new com.example.finalproject.domain.callback.RepositoryCallback<Integer>() {
                        @Override public void onSuccess(Integer reportId) {
                            if (isFinishing() || isDestroyed()) return;
                            dialog.dismiss();
                            Toast.makeText(PlaceDetailActivity.this,
                                R.string.report_success, Toast.LENGTH_LONG).show();
                        }

                        @Override public void onError(Exception error) {
                            if (isFinishing() || isDestroyed()) return;
                            sendButton.setEnabled(true);
                            sendButton.setText(R.string.report_send);
                            categoryInput.setEnabled(true);
                            descriptionInput.setEnabled(true);
                            Toast.makeText(PlaceDetailActivity.this,
                                R.string.report_submit_failed, Toast.LENGTH_LONG).show();
                        }
                    });
            }));
        dialog.show();
    }

    private Place readPlace() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            return getIntent().getSerializableExtra(EXTRA_PLACE, Place.class);
        }
        return (Place) getIntent().getSerializableExtra(EXTRA_PLACE);
    }

    private void bindOptional(int id, String prefix, String value) {
        TextView view = findViewById(id);
        if (isBlank(value)) view.setVisibility(View.GONE); else view.setText(prefix + value);
    }

    private boolean isBlank(String value) { return value == null || value.trim().isEmpty(); }
    private void set(int id, String value) { ((TextView) findViewById(id)).setText(value == null ? "" : value); }
    private void openUrl(String url) { openIntent(new Intent(Intent.ACTION_VIEW, Uri.parse(url))); }
    private void openIntent(Intent intent) {
        try { startActivity(intent); }
        catch (Exception error) { Toast.makeText(this, R.string.no_compatible_app, Toast.LENGTH_SHORT).show(); }
    }
}
