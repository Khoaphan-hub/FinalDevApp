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
        set(R.id.placeDetailType, "EATERY".equals(place.getType()) ? "ĂN UỐNG" : "ĐIỂM THAM QUAN");
        set(R.id.placeDetailName, place.getName());
        set(R.id.placeDetailAddress, place.getAddress());
        set(R.id.placeDetailRating, place.getRating() > 0
            ? String.format(Locale.getDefault(), "★ %.1f", place.getRating()) : "Chưa có đánh giá");
        set(R.id.placeDetailPrice, place.getPriceVnd() > 0
            ? NumberFormat.getNumberInstance(new Locale("vi", "VN")).format(place.getPriceVnd()) + " ₫/người"
            : "Miễn phí hoặc chưa có giá");
        bindOptional(R.id.placeDetailHours, "Giờ mở cửa: ", place.getOpenHours());
        bindOptional(R.id.placeDetailTags, "Phù hợp: ", place.getTags());
        bindOptional(R.id.placeDetailHighlight, "Điểm nổi bật\n", place.getHighlight());

        MaterialButton mediaButton = findViewById(R.id.placeMediaButton);
        if (isBlank(place.getMediaUrl())) mediaButton.setVisibility(View.GONE);
        else {
            String url = place.getMediaUrl();
            mediaButton.setText(url.contains("tiktok.com") ? "Xem TikTok review"
                : url.contains("google.com/maps") || url.contains("maps.app.goo.gl")
                ? "Xem đánh giá trên Google Maps" : "Mở link đánh giá");
            mediaButton.setOnClickListener(v -> openUrl(url));
        }
        findViewById(R.id.placeDirectionsButton).setOnClickListener(v -> {
            Uri uri = Uri.parse("geo:" + place.getLatitude() + "," + place.getLongitude()
                + "?q=" + Uri.encode(place.getName()));
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
        catch (Exception error) { Toast.makeText(this, "Thiết bị chưa có ứng dụng phù hợp để mở.", Toast.LENGTH_SHORT).show(); }
    }
}
