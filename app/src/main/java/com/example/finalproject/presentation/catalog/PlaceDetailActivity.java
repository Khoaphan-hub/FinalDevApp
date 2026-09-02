package com.example.finalproject.presentation.catalog;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.finalproject.R;
import com.example.finalproject.domain.model.Place;
import com.example.finalproject.infrastructure.link.ExternalPlaceLinks;
import com.example.finalproject.infrastructure.remote.RemoteImageLoader;
import com.example.finalproject.presentation.SystemBarInsets;
import com.google.android.material.button.MaterialButton;

import java.text.NumberFormat;
import java.util.Locale;

public class PlaceDetailActivity extends AppCompatActivity {
    private static final String EXTRA_PLACE = "place";

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
