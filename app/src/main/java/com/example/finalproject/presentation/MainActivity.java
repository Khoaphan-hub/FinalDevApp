package com.example.finalproject.presentation;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;
import androidx.fragment.app.Fragment;

import com.example.finalproject.R;
import com.example.finalproject.presentation.home.HomeFragment;
import com.example.finalproject.presentation.saved.SavedTripsFragment;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    public static final String EXTRA_DESTINATION = "main_destination";
    public static final String DESTINATION_TRIPS = "trips";
    public static final String EXTRA_MESSAGE = "main_message";

    private MaterialToolbar topAppBar;
    private BottomNavigationView bottomNav;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        SystemBarInsets.apply(findViewById(R.id.mainRoot));

        topAppBar = findViewById(R.id.topAppBar);
        topAppBar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() != R.id.action_language) return false;
            String target = "en".equals(Locale.getDefault().getLanguage()) ? "vi" : "en";
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(target));
            return true;
        });

        bottomNav = findViewById(R.id.bottom_navigation);
        bottomNav.setOnItemSelectedListener(item -> showDestination(item.getItemId()));

        if (savedInstanceState == null) {
            navigateFromIntent(getIntent());
        }
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        navigateFromIntent(intent);
    }

    private void navigateFromIntent(Intent intent) {
        boolean openTrips = DESTINATION_TRIPS.equals(intent.getStringExtra(EXTRA_DESTINATION));
        int destination = openTrips ? R.id.nav_trips : R.id.nav_home;
        if (bottomNav.getSelectedItemId() == destination) showDestination(destination);
        else bottomNav.setSelectedItemId(destination);
        String message = intent.getStringExtra(EXTRA_MESSAGE);
        if (message != null && !message.isEmpty()) {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            intent.removeExtra(EXTRA_MESSAGE);
        }
    }

    private boolean showDestination(int itemId) {
        Fragment selectedFragment;
        if (itemId == R.id.nav_home) {
            selectedFragment = new HomeFragment();
            topAppBar.setTitle("Journify");
        } else if (itemId == R.id.nav_trips) {
            selectedFragment = new SavedTripsFragment();
            topAppBar.setTitle(R.string.trips);
        } else if (itemId == R.id.nav_profile) {
            selectedFragment = ComingSoonFragment.newInstance(
                getString(R.string.profile_title),
                getString(R.string.profile_message)
            );
            topAppBar.setTitle(R.string.profile);
        } else return false;
        getSupportFragmentManager().beginTransaction()
            .replace(R.id.fragment_container, selectedFragment).commit();
        return true;
    }
}
