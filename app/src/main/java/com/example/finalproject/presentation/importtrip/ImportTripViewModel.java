package com.example.finalproject.presentation.importtrip;

import android.app.Application;
import android.net.Uri;
import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import com.example.finalproject.R;
import com.example.finalproject.domain.model.Itinerary;
import com.example.finalproject.domain.model.ItineraryQrLink;
import com.example.finalproject.infrastructure.device.QrImageReader;
import com.example.finalproject.infrastructure.remote.RemotePlannerRepository;
import com.google.zxing.NotFoundException;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Retains work/results over rotation without retaining an Activity or its views. */
public class ImportTripViewModel extends AndroidViewModel {
    public static final class State {
        final boolean loading;
        final int message;
        final Itinerary itinerary;
        final boolean retry;
        State(boolean loading, int message, Itinerary itinerary, boolean retry) {
            this.loading = loading; this.message = message; this.itinerary = itinerary; this.retry = retry;
        }
    }
    private final MutableLiveData<State> state = new MutableLiveData<>(new State(false, 0, null, false));
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private String pendingToken;
    private volatile boolean busy;
    public ImportTripViewModel(@NonNull Application app) { super(app); }
    public LiveData<State> state() { return state; }
    public void message(int message) { if (!busy) state.setValue(new State(false, message, null, false)); }
    public void clearPreview() { state.setValue(new State(false, 0, null, false)); }

    public void importText(String text) { begin(text, null); }
    public void importImage(Uri image) { begin(null, image); }
    public void retry() {
        if (busy || pendingToken == null) return;
        busy = true;
        state.setValue(new State(true, R.string.qr_loading_trip, null, false));
        worker.execute(this::loadToken);
    }
    private void begin(String text, Uri image) {
        if (busy) return;
        busy = true;
        pendingToken = null;
        state.setValue(new State(true, image == null ? R.string.qr_loading_trip : R.string.qr_reading_image, null, false));
        worker.execute(() -> {
            try {
                String contents = image == null ? text : QrImageReader.read(getApplication().getContentResolver(), image);
                pendingToken = ItineraryQrLink.token(contents);
            } catch (NotFoundException error) { fail(R.string.qr_no_code, false); return;
            } catch (IllegalArgumentException error) {
                fail("MULTIPLE_QR".equals(error.getMessage()) ? R.string.qr_multiple : R.string.qr_invalid, false); return;
            } catch (Exception | OutOfMemoryError error) { fail(R.string.qr_image_error, false); return; }
            state.postValue(new State(true, R.string.qr_loading_trip, null, false));
            loadToken();
        });
    }
    private void loadToken() {
        HttpURLConnection connection = null;
        try {
            String base = RemotePlannerRepository.DEFAULT_BASE_URL;
            if (!base.endsWith("/")) base += "/";
            connection = (HttpURLConnection) new URL(base + "api/mobile/itineraries/import/" + pendingToken + "/").openConnection();
            connection.setConnectTimeout(7000);
            connection.setReadTimeout(15000);
            connection.setInstanceFollowRedirects(false);
            connection.setRequestProperty("Accept", "application/json");
            int status = connection.getResponseCode();
            if (status == 410) { fail(R.string.qr_expired, false); return; }
            if (status == 404) { fail(R.string.qr_missing, true); return; }
            if (status == 400 || status == 422) { fail(R.string.qr_invalid, false); return; }
            if (status != 200) { fail(R.string.qr_server_error, true); return; }
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (InputStream stream = connection.getInputStream()) {
                byte[] buffer = new byte[8192]; int count;
                while ((count = stream.read(buffer)) != -1) {
                    if (bytes.size() + count > 2_000_000) throw new IllegalArgumentException();
                    bytes.write(buffer, 0, count);
                }
            }
            JSONObject response = new JSONObject(bytes.toString(StandardCharsets.UTF_8.name()));
            Itinerary itinerary = RemotePlannerRepository.parseItinerary(response.getJSONObject("data"));
            if (!response.optBoolean("success") || itinerary.getDays().isEmpty()) throw new IllegalArgumentException();
            busy = false;
            state.postValue(new State(false, 0, itinerary, false));
        } catch (java.io.IOException error) { fail(R.string.qr_network_error, true);
        } catch (Exception error) { fail(R.string.qr_invalid, false);
        } finally { if (connection != null) connection.disconnect(); }
    }
    private void fail(int message, boolean retry) {
        busy = false;
        state.postValue(new State(false, message, null, retry));
    }
    @Override protected void onCleared() { worker.shutdownNow(); }
}
