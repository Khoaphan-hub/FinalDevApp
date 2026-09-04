package com.example.finalproject.presentation.importtrip;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import com.example.finalproject.R;
import com.example.finalproject.presentation.SystemBarInsets;
import com.example.finalproject.presentation.itinerary.ItineraryActivity;
import com.google.android.material.appbar.MaterialToolbar;
import com.journeyapps.barcodescanner.ScanContract;
import com.journeyapps.barcodescanner.ScanOptions;

public class ImportTripActivity extends AppCompatActivity {
    private ImportTripViewModel model;
    private ActivityResultLauncher<ScanOptions> scanner;
    private ActivityResultLauncher<String> imagePicker;
    private ActivityResultLauncher<String> cameraPermission;
    private int lastMessage;

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_import_trip);
        SystemBarInsets.apply(findViewById(R.id.importTripRoot));
        model = new ViewModelProvider(this).get(ImportTripViewModel.class);
        ((MaterialToolbar) findViewById(R.id.importToolbar)).setNavigationOnClickListener(v -> finish());
        scanner = registerForActivityResult(new ScanContract(), result -> {
            if (result.getContents() != null) model.importText(result.getContents());
            else model.message(R.string.qr_cancelled);
        });
        imagePicker = registerForActivityResult(new ActivityResultContracts.GetContent(), uri -> {
            if (uri != null) model.importImage(uri);
            else model.message(R.string.qr_cancelled);
        });
        cameraPermission = registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
            if (granted) openScanner();
            else model.message(R.string.qr_camera_denied);
        });
        findViewById(R.id.qrCameraButton).setOnClickListener(v -> {
            if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
                model.message(R.string.qr_no_camera);
            } else if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                openScanner();
            } else cameraPermission.launch(Manifest.permission.CAMERA);
        });
        findViewById(R.id.qrImageButton).setOnClickListener(v -> {
            try { imagePicker.launch("image/*"); }
            catch (android.content.ActivityNotFoundException error) { model.message(R.string.qr_image_error); }
        });
        findViewById(R.id.qrRetryButton).setOnClickListener(v -> model.retry());
        model.state().observe(this, result -> {
            findViewById(R.id.qrCameraButton).setEnabled(!result.loading);
            findViewById(R.id.qrImageButton).setEnabled(!result.loading);
            findViewById(R.id.qrStatusCard).setVisibility(result.message == 0 ? View.GONE : View.VISIBLE);
            findViewById(R.id.qrProgress).setVisibility(result.loading ? View.VISIBLE : View.GONE);
            findViewById(R.id.qrRetryButton).setVisibility(result.retry ? View.VISIBLE : View.GONE);
            if (result.message != 0) ((TextView) findViewById(R.id.qrStatusText)).setText(result.message);
            if (result.message != 0 && result.message != lastMessage) {
                View card = findViewById(R.id.qrStatusCard);
                card.post(() -> ((android.widget.ScrollView) findViewById(R.id.qrImportScroll))
                        .smoothScrollTo(0, card.getTop()));
            }
            lastMessage = result.message;
            if (result.itinerary != null) {
                Intent preview = new Intent(this, ItineraryActivity.class);
                preview.putExtra(ItineraryActivity.EXTRA_ITINERARY, result.itinerary);
                preview.putExtra(ItineraryActivity.EXTRA_IMPORTED, true);
                model.clearPreview();
                startActivity(preview);
                finish();
            }
        });
    }
    private void openScanner() {
        scanner.launch(new ScanOptions().setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                .setCaptureActivity(QrCameraActivity.class).setOrientationLocked(false)
                .setBeepEnabled(false).setBarcodeImageEnabled(false).setPrompt(""));
    }
}
