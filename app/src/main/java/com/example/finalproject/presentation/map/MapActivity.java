package com.example.finalproject.presentation.map;

import android.annotation.SuppressLint;
import android.os.Build;
import android.os.Bundle;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.finalproject.R;
import com.example.finalproject.domain.model.ItineraryDay;
import com.example.finalproject.domain.model.ItineraryStop;
import com.example.finalproject.presentation.SystemBarInsets;

import org.json.JSONArray;
import org.json.JSONObject;

public class MapActivity extends AppCompatActivity {
    public static final String EXTRA_DAY = "itinerary_day";

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_map);
        SystemBarInsets.apply(findViewById(R.id.mapRoot));
        findViewById(R.id.mapBackButton).setOnClickListener(v -> finish());

        ItineraryDay day;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            day = getIntent().getSerializableExtra(EXTRA_DAY, ItineraryDay.class);
        } else {
            day = (ItineraryDay) getIntent().getSerializableExtra(EXTRA_DAY);
        }
        if (day == null) { finish(); return; }
        ((TextView) findViewById(R.id.mapTitle)).setText("Tuyến đường ngày " + day.getDayNumber());

        JSONArray stops = new JSONArray();
        for (ItineraryStop stop : day.getStops()) {
            try {
                JSONObject item = new JSONObject();
                item.put("name", stop.getName());
                item.put("type", stop.getType().name());
                item.put("lat", stop.getLatitude());
                item.put("lon", stop.getLongitude());
                stops.put(item);
            } catch (Exception ignored) { }
        }

        WebView webView = findViewById(R.id.routeWebView);
        webView.getSettings().setJavaScriptEnabled(true);
        webView.getSettings().setDomStorageEnabled(true);
        webView.setWebViewClient(new WebViewClient() {
            @Override public void onPageFinished(WebView view, String url) {
                view.evaluateJavascript("renderRoute(" + stops + ")", null);
            }
        });
        webView.loadUrl("file:///android_asset/journify_map.html");
    }
}
