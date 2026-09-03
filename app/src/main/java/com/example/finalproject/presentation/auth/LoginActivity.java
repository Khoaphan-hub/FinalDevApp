package com.example.finalproject.presentation.auth;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.finalproject.R;
import com.example.finalproject.infrastructure.remote.RemotePlannerRepository;
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

public class LoginActivity extends AppCompatActivity {

    private TextInputEditText usernameInput;
    private TextInputEditText passwordInput;
    private TextView loginErrorText;
    private MaterialButton loginButton;
    private ProgressBar loginProgressBar;
    
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        SystemBarInsets.apply(findViewById(R.id.loginRoot));

        usernameInput = findViewById(R.id.usernameInput);
        passwordInput = findViewById(R.id.passwordInput);
        loginErrorText = findViewById(R.id.loginErrorText);
        loginButton = findViewById(R.id.loginButton);
        loginProgressBar = findViewById(R.id.loginProgressBar);

        loginButton.setOnClickListener(v -> performLogin());
        
        findViewById(R.id.signupButton).setOnClickListener(v -> {
            startActivity(new Intent(this, RegisterActivity.class));
        });
    }

    private void performLogin() {
        String username = usernameInput.getText() != null ? usernameInput.getText().toString().trim() : "";
        String password = passwordInput.getText() != null ? passwordInput.getText().toString().trim() : "";

        if (username.isEmpty() || password.isEmpty()) {
            showError(getString(R.string.error_empty_fields));
            return;
        }

        loginErrorText.setVisibility(View.GONE);
        setLoading(true);

        executor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                // Use the same base URL as planner
                String baseUrl = RemotePlannerRepository.DEFAULT_BASE_URL;
                if (!baseUrl.endsWith("/")) baseUrl += "/";
                
                connection = (HttpURLConnection) new URL(baseUrl + "api/login/").openConnection();
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(6000);
                connection.setReadTimeout(15000);
                connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded");
                connection.setDoOutput(true);

                String postData = "username=" + URLEncoder.encode(username, "UTF-8") +
                        "&password=" + URLEncoder.encode(password, "UTF-8");

                try (OutputStream output = connection.getOutputStream()) {
                    output.write(postData.getBytes(StandardCharsets.UTF_8));
                }

                int status = connection.getResponseCode();
                InputStream stream = status >= 200 && status < 300
                        ? connection.getInputStream() : connection.getErrorStream();
                
                JSONObject response = new JSONObject(readStream(stream));
                
                if (status >= 200 && status < 300 && response.optBoolean("success")) {
                    mainHandler.post(this::onLoginSuccess);
                } else {
                    String errorMsg = response.optString("error", "Login failed. Please try again.");
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
    
    private void onLoginSuccess() {
        // This screen is now opened from wherever an account was needed, rather than being
        // the app's entry point, so finishing returns the user to what they were doing.
        setResult(RESULT_OK);
        finish();
    }
    
    private void showError(String message) {
        loginErrorText.setText(message);
        loginErrorText.setVisibility(View.VISIBLE);
    }
    
    private void setLoading(boolean isLoading) {
        if (isLoading) {
            loginButton.setText("");
            loginButton.setEnabled(false);
            loginProgressBar.setVisibility(View.VISIBLE);
        } else {
            loginButton.setText(R.string.login_button);
            loginButton.setEnabled(true);
            loginProgressBar.setVisibility(View.GONE);
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
