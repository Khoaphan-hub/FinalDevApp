package com.example.finalproject.infrastructure.device;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;

import com.example.finalproject.domain.callback.RepositoryCallback;
import com.example.finalproject.domain.model.DeviceLocation;
import com.example.finalproject.domain.repository.LocationRepository;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Reads the position with the platform LocationManager, then turns it into a street address
 * with Geocoder so the value can be typed straight into the Django "start_address" field.
 *
 * No Google Play Services dependency on purpose: FusedLocationProviderClient would need an
 * extra library, and the accuracy of a plain provider is more than enough for picking the
 * starting point of a Da Lat trip.
 */
public final class AndroidLocationRepository implements LocationRepository {
    /** How long to wait for a fresh fix before giving up and telling the user to retry. */
    private static final long FIX_TIMEOUT_MS = 15_000L;

    private final Context context;
    private final LocationManager locationManager;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    private LocationListener activeListener;
    private Runnable timeoutRunnable;

    public AndroidLocationRepository(Context context) {
        this.context = context.getApplicationContext();
        this.locationManager = (LocationManager) this.context.getSystemService(Context.LOCATION_SERVICE);
    }

    @Override
    public void currentLocation(RepositoryCallback<DeviceLocation> callback) {
        if (!hasPermission()) {
            callback.onError(new SecurityException("Location permission has not been granted."));
            return;
        }
        if (locationManager == null) {
            callback.onError(new IllegalStateException("This device has no location service."));
            return;
        }
        // A cached fix is instant and accurate enough here, so only fall back to a live
        // request when the system has nothing stored yet (fresh boot, location just enabled).
        Location cached = lastKnownLocation();
        if (cached != null) {
            resolveAddress(cached, callback);
            return;
        }
        requestSingleFix(callback);
    }

    private boolean hasPermission() {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
            || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    /** Returns the most recent cached fix across every enabled provider, or null if there is none. */
    private Location lastKnownLocation() {
        Location best = null;
        try {
            for (String provider : locationManager.getProviders(true)) {
                Location candidate = locationManager.getLastKnownLocation(provider);
                if (candidate == null) continue;
                if (best == null || candidate.getTime() > best.getTime()) best = candidate;
            }
        } catch (SecurityException ignored) {
            // hasPermission() already passed; a revoke racing with this call just means "no fix".
        }
        return best;
    }

    private void requestSingleFix(RepositoryCallback<DeviceLocation> callback) {
        List<String> providers = new ArrayList<>();
        if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            providers.add(LocationManager.NETWORK_PROVIDER);
        }
        if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            providers.add(LocationManager.GPS_PROVIDER);
        }
        if (providers.isEmpty()) {
            callback.onError(new IllegalStateException("Location is turned off on this device."));
            return;
        }

        // LocationManager has no "one shot" request before API 30, so we subscribe to updates
        // and unsubscribe ourselves inside the first callback.
        activeListener = new LocationListener() {
            @Override public void onLocationChanged(@NonNull Location location) {
                stopListening();
                resolveAddress(location, callback);
            }
            // Kept as no-ops: these three are abstract on API levels below 29.
            @Override public void onStatusChanged(String provider, int status, Bundle extras) { }
            @Override public void onProviderEnabled(@NonNull String provider) { }
            @Override public void onProviderDisabled(@NonNull String provider) { }
        };

        try {
            for (String provider : providers) {
                // 0/0 = report the very first fix, no distance or time filtering.
                locationManager.requestLocationUpdates(provider, 0L, 0f, activeListener, Looper.getMainLooper());
            }
        } catch (SecurityException error) {
            stopListening();
            callback.onError(error);
            return;
        }

        timeoutRunnable = () -> {
            stopListening();
            callback.onError(new IllegalStateException("Timed out while waiting for a location fix."));
        };
        main.postDelayed(timeoutRunnable, FIX_TIMEOUT_MS);
    }

    /**
     * Reverse geocoding hits the network, so it runs on the executor and the result is posted
     * back to the main thread, matching how the other repositories in this project behave.
     */
    private void resolveAddress(Location location, RepositoryCallback<DeviceLocation> callback) {
        executor.execute(() -> {
            String label = coordinateLabel(location);
            if (Geocoder.isPresent()) {
                try {
                    Geocoder geocoder = new Geocoder(context, Locale.getDefault());
                    // The synchronous overload is deprecated on API 33+ but still supported, and
                    // it is safe here because this call is already off the main thread.
                    List<Address> matches = geocoder.getFromLocation(
                        location.getLatitude(), location.getLongitude(), 1);
                    if (matches != null && !matches.isEmpty()) {
                        String resolved = formatAddress(matches.get(0));
                        if (!resolved.isEmpty()) label = resolved;
                    }
                } catch (Exception ignored) {
                    // Offline or geocoder backend missing: the coordinate label stays as fallback.
                }
            }
            String result = label;
            main.post(() -> callback.onSuccess(new DeviceLocation(
                location.getLatitude(), location.getLongitude(), result)));
        });
    }

    /** Joins the address lines the geocoder filled in, skipping the ones it left null. */
    private String formatAddress(Address address) {
        StringBuilder builder = new StringBuilder();
        for (int line = 0; line <= address.getMaxAddressLineIndex(); line++) {
            String value = address.getAddressLine(line);
            if (value == null || value.trim().isEmpty()) continue;
            if (builder.length() > 0) builder.append(", ");
            builder.append(value.trim());
        }
        return builder.toString();
    }

    private String coordinateLabel(Location location) {
        // Locale.US keeps the decimal separator a dot; a Vietnamese locale would write "11,94"
        // and Django would read the comma as a field separator.
        return String.format(Locale.US, "%.5f, %.5f", location.getLatitude(), location.getLongitude());
    }

    private void stopListening() {
        if (timeoutRunnable != null) {
            main.removeCallbacks(timeoutRunnable);
            timeoutRunnable = null;
        }
        if (activeListener != null) {
            try {
                locationManager.removeUpdates(activeListener);
            } catch (SecurityException ignored) {
                // Nothing to clean up if the permission disappeared underneath us.
            }
            activeListener = null;
        }
    }

    @Override
    public void release() {
        stopListening();
        executor.shutdown();
    }
}
