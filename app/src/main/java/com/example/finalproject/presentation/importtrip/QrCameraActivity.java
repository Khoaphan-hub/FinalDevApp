package com.example.finalproject.presentation.importtrip;

import android.os.Bundle;
import android.view.KeyEvent;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import com.example.finalproject.R;
import com.example.finalproject.presentation.SystemBarInsets;
import com.journeyapps.barcodescanner.CaptureManager;
import com.journeyapps.barcodescanner.DecoratedBarcodeView;

/** AppCompat preserves Journify's selected language; CaptureManager owns camera lifecycle. */
public class QrCameraActivity extends AppCompatActivity {
    private CaptureManager capture;
    private DecoratedBarcodeView scanner;
    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_qr_camera);
        SystemBarInsets.apply(findViewById(R.id.qrCameraRoot));
        // Older Android versions draw system bars outside the dark camera content.
        getWindow().setStatusBarColor(android.graphics.Color.rgb(16, 29, 25));
        getWindow().setNavigationBarColor(android.graphics.Color.rgb(16, 29, 25));
        androidx.core.view.WindowInsetsControllerCompat bars =
                new androidx.core.view.WindowInsetsControllerCompat(getWindow(), getWindow().getDecorView());
        bars.setAppearanceLightStatusBars(false);
        bars.setAppearanceLightNavigationBars(false);
        scanner = findViewById(R.id.qrBarcodeView);
        findViewById(R.id.qrCameraClose).setOnClickListener(v -> finish());
        capture = new CaptureManager(this, scanner);
        capture.initializeFromIntent(getIntent(), state);
        capture.decode();
    }
    @Override protected void onResume() { super.onResume(); capture.onResume(); }
    @Override protected void onPause() { capture.onPause(); super.onPause(); }
    @Override protected void onDestroy() { capture.onDestroy(); super.onDestroy(); }
    @Override protected void onSaveInstanceState(@NonNull Bundle state) {
        super.onSaveInstanceState(state); capture.onSaveInstanceState(state);
    }
    @Override public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        capture.onRequestPermissionsResult(requestCode, permissions, results);
    }
    @Override public boolean onKeyDown(int code, KeyEvent event) {
        return scanner.onKeyDown(code, event) || super.onKeyDown(code, event);
    }
}
