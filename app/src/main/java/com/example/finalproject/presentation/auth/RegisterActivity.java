package com.example.finalproject.presentation.auth;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.example.finalproject.R;
import com.example.finalproject.infrastructure.remote.RemotePlannerRepository;
import com.example.finalproject.presentation.MainActivity;
import com.example.finalproject.presentation.SystemBarInsets;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.textfield.TextInputEditText;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class RegisterActivity extends AppCompatActivity {

    private TextInputEditText usernameInput;
    private TextInputEditText passwordInput;
    private TextInputEditText confirmPasswordInput;
    private TextView registerErrorText;
    private MaterialButton registerButton;
    private ProgressBar registerProgressBar;
    
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_register);
        SystemBarInsets.apply(findViewById(R.id.registerRoot));

        usernameInput = findViewById(R.id.usernameInput);
        passwordInput = findViewById(R.id.passwordInput);
        confirmPasswordInput = findViewById(R.id.confirmPasswordInput);
        registerErrorText = findViewById(R.id.registerErrorText);
        registerButton = findViewById(R.id.registerButton);
        registerProgressBar = findViewById(R.id.registerProgressBar);

        registerButton.setOnClickListener(v -> performRegister());
        
        findViewById(R.id.loginPromptButton).setOnClickListener(v -> {
            finish(); // Go back to login screen
        });
    }

    private void performRegister() {
        String username = usernameInput.getText() != null ? usernameInput.getText().toString().trim() : "";
        String password = passwordInput.getText() != null ? passwordInput.getText().toString().trim() : "";
        String confirmPassword = confirmPasswordInput.getText() != null ? confirmPasswordInput.getText().toString().trim() : "";

        if (username.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
            showError(getString(R.string.error_empty_fields));
            return;
        }

        if (!password.equals(confirmPassword)) {
            showError(getString(R.string.error_password_mismatch));
            return;
        }

        registerErrorText.setVisibility(View.GONE);
        setLoading(true);

        executor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                // Use the same base URL as planner
                String baseUrl = RemotePlannerRepository.DEFAULT_BASE_URL;
                if (!baseUrl.endsWith("/")) baseUrl += "/";
                
                connection = (HttpURLConnection) new URL(baseUrl + "api/register/").openConnection();
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(6000);
                connection.setReadTimeout(15000);
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                connection.setDoOutput(true);

                // Django SimpleUserCreationForm expects username, password1, password2
                String postData = "username=" + URLEncoder.encode(username, "UTF-8") +
                        "&password1=" + URLEncoder.encode(password, "UTF-8") +
                        "&password2=" + URLEncoder.encode(confirmPassword, "UTF-8");

                try (OutputStream output = connection.getOutputStream()) {
                    output.write(postData.getBytes(StandardCharsets.UTF_8));
                }

                int status = connection.getResponseCode();
                InputStream stream = status >= 200 && status < 300
                        ? connection.getInputStream() : connection.getErrorStream();
                
                JSONObject response = new JSONObject(readStream(stream));
                
                if (status >= 200 && status < 300 && response.optBoolean("success")) {
                    mainHandler.post(this::onRegisterSuccess);
                } else {
                    String errorMsg = response.optString("error", "Registration failed. Please try again.");
                    mainHandler.post(() -> showError(errorMsg));
                }
            } catch (Exception error) {
                mainHandler.post(() -> showError("Network error: " + error.getMessage()));
            } finally {
                if (connection != null) connection.disconnect();
                mainHandler.post(() -> setLoading(false));
            }
        });
    }
    
    private void onRegisterSuccess() {
        // Go to main activity directly after successful registration
        Intent intent = new Intent(this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }
    
    private void showError(String message) {
        registerErrorText.setText(message);
        registerErrorText.setVisibility(View.VISIBLE);
    }
    
    private void setLoading(boolean isLoading) {
        if (isLoading) {
            registerButton.setText("");
            registerButton.setEnabled(false);
            registerProgressBar.setVisibility(View.VISIBLE);
        } else {
            registerButton.setText(R.string.register_button);
            registerButton.setEnabled(true);
            registerProgressBar.setVisibility(View.GONE);
        }
    }

    private String readStream(InputStream input) throws Exception {
        if (input == null) return "{}";
        StringBuilder result = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) result.append(line);
        }
        return result.toString();
    }
}
