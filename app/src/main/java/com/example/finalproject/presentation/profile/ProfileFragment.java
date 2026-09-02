package com.example.finalproject.presentation.profile;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.finalproject.R;
import com.example.finalproject.infrastructure.remote.RemotePlannerRepository;
import com.example.finalproject.infrastructure.remote.RemoteImageLoader;
import com.example.finalproject.presentation.auth.LoginActivity;
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

public class ProfileFragment extends Fragment {

    private ImageView profileAvatar;
    private TextView profileUsername;
    private TextInputEditText profileEmailInput;
    private TextInputEditText profilePhoneInput;
    private MaterialButton profileSaveButton;
    private ProgressBar profileSaveProgress;
    private MaterialButton profileLogoutButton;

    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private android.net.Uri selectedAvatarUri;
    private androidx.activity.result.ActivityResultLauncher<androidx.activity.result.PickVisualMediaRequest> pickMedia;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        pickMedia = registerForActivityResult(new androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia(), uri -> {
            if (uri != null) {
                selectedAvatarUri = uri;
                profileAvatar.setImageURI(uri);
            }
        });
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_profile, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        profileAvatar = view.findViewById(R.id.profileAvatar);
        profileUsername = view.findViewById(R.id.profileUsername);
        profileEmailInput = view.findViewById(R.id.profileEmailInput);
        profilePhoneInput = view.findViewById(R.id.profilePhoneInput);
        profileSaveButton = view.findViewById(R.id.profileSaveButton);
        profileSaveProgress = view.findViewById(R.id.profileSaveProgress);
        profileLogoutButton = view.findViewById(R.id.profileLogoutButton);

        profileAvatar.setOnClickListener(v -> {
            pickMedia.launch(new androidx.activity.result.PickVisualMediaRequest.Builder()
                .setMediaType(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly.INSTANCE)
                .build());
        });

        profileSaveButton.setOnClickListener(v -> saveProfile());
        profileLogoutButton.setOnClickListener(v -> logout());

        loadProfile();
    }

    private void loadProfile() {
        executor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                String baseUrlRaw = RemotePlannerRepository.DEFAULT_BASE_URL;
                if (!baseUrlRaw.endsWith("/")) baseUrlRaw += "/";
                final String baseUrl = baseUrlRaw;
                
                connection = (HttpURLConnection) new URL(baseUrl + "api/mobile/profile/").openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(6000);
                connection.setReadTimeout(15000);

                int status = connection.getResponseCode();
                InputStream stream = status >= 200 && status < 300
                        ? connection.getInputStream() : connection.getErrorStream();
                
                JSONObject response = new JSONObject(readStream(stream));
                
                if (status >= 200 && status < 300) {
                    final String username = response.optString("username");
                    final String email = response.optString("email");
                    final String phone = response.optString("phone_number");
                    final String avatarUrl = response.optString("avatar_url");

                    mainHandler.post(() -> {
                        profileUsername.setText(username);
                        profileEmailInput.setText(email);
                        profilePhoneInput.setText(phone);
                        
                        if (avatarUrl != null && !avatarUrl.isEmpty()) {
                            String fullUrl = avatarUrl.startsWith("http") ? avatarUrl : baseUrl.substring(0, baseUrl.length()-1) + avatarUrl;
                            RemoteImageLoader.load(fullUrl, profileAvatar);
                        } else {
                            profileAvatar.setImageResource(android.R.drawable.sym_def_app_icon);
                        }
                    });
                } else {
                    mainHandler.post(() -> Toast.makeText(getContext(), "Failed to load profile", Toast.LENGTH_SHORT).show());
                }
            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                if (connection != null) connection.disconnect();
            }
        });
    }

    private void saveProfile() {
        String email = profileEmailInput.getText() != null ? profileEmailInput.getText().toString().trim() : "";
        String phone = profilePhoneInput.getText() != null ? profilePhoneInput.getText().toString().trim() : "";

        setLoading(true);

        executor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                String baseUrl = RemotePlannerRepository.DEFAULT_BASE_URL;
                if (!baseUrl.endsWith("/")) baseUrl += "/";
                
                connection = (HttpURLConnection) new URL(baseUrl + "api/mobile/profile/").openConnection();
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(6000);
                connection.setReadTimeout(15000);
                String boundary = "----WebKitFormBoundary" + System.currentTimeMillis();
                connection.setRequestProperty("Content-Type", "multipart/form-data; boundary=" + boundary);
                connection.setDoOutput(true);

                try (java.io.OutputStream output = connection.getOutputStream();
                     java.io.PrintWriter writer = new java.io.PrintWriter(new java.io.OutputStreamWriter(output, StandardCharsets.UTF_8), true)) {
                    
                    writer.append("--").append(boundary).append("\r\n");
                    writer.append("Content-Disposition: form-data; name=\"email\"\r\n\r\n");
                    writer.append(email).append("\r\n");

                    writer.append("--").append(boundary).append("\r\n");
                    writer.append("Content-Disposition: form-data; name=\"phone_number\"\r\n\r\n");
                    writer.append(phone).append("\r\n");

                    if (selectedAvatarUri != null) {
                        writer.append("--").append(boundary).append("\r\n");
                        writer.append("Content-Disposition: form-data; name=\"avatar\"; filename=\"avatar.jpg\"\r\n");
                        writer.append("Content-Type: image/jpeg\r\n\r\n");
                        writer.flush();
                        
                        try (InputStream is = requireContext().getContentResolver().openInputStream(selectedAvatarUri)) {
                            byte[] buffer = new byte[4096];
                            int bytesRead;
                            while ((bytesRead = is.read(buffer)) != -1) {
                                output.write(buffer, 0, bytesRead);
                            }
                        }
                        output.flush();
                        writer.append("\r\n");
                    }
                    
                    writer.append("--").append(boundary).append("--\r\n");
                    writer.flush();
                }

                int status = connection.getResponseCode();
                if (status >= 200 && status < 300) {
                    mainHandler.post(() -> Toast.makeText(getContext(), R.string.profile_updated, Toast.LENGTH_SHORT).show());
                } else {
                    mainHandler.post(() -> Toast.makeText(getContext(), "Failed to update profile", Toast.LENGTH_SHORT).show());
                }
            } catch (Exception e) {
                mainHandler.post(() -> Toast.makeText(getContext(), R.string.network_error, Toast.LENGTH_SHORT).show());
            } finally {
                if (connection != null) connection.disconnect();
                mainHandler.post(() -> setLoading(false));
            }
        });
    }

    private void logout() {
        executor.execute(() -> {
            HttpURLConnection connection = null;
            try {
                String baseUrl = RemotePlannerRepository.DEFAULT_BASE_URL;
                if (!baseUrl.endsWith("/")) baseUrl += "/";
                
                connection = (HttpURLConnection) new URL(baseUrl + "api/logout/").openConnection();
                connection.setRequestMethod("POST");
                connection.setConnectTimeout(6000);
                
                connection.getResponseCode(); // Execute the request
            } catch (Exception ignored) {
            } finally {
                if (connection != null) connection.disconnect();
                
                // Clear cookies and return to login
                java.net.CookieHandler.setDefault(new java.net.CookieManager());
                
                mainHandler.post(() -> {
                    if (getActivity() != null) {
                        Intent intent = new Intent(getActivity(), LoginActivity.class);
                        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                        startActivity(intent);
                        getActivity().finish();
                    }
                });
            }
        });
    }

    private void setLoading(boolean isLoading) {
        if (isLoading) {
            profileSaveButton.setText("");
            profileSaveButton.setEnabled(false);
            profileSaveProgress.setVisibility(View.VISIBLE);
        } else {
            profileSaveButton.setText(R.string.save_profile);
            profileSaveButton.setEnabled(true);
            profileSaveProgress.setVisibility(View.GONE);
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
