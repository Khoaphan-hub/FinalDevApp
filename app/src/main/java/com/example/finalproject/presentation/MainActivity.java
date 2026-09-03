package com.example.finalproject.presentation;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;
import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.viewpager2.adapter.FragmentStateAdapter;
import androidx.viewpager2.widget.ViewPager2;

import com.example.finalproject.R;
import com.example.finalproject.presentation.home.HomeFragment;
import com.example.finalproject.presentation.saved.SavedTripsFragment;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.button.MaterialButtonToggleGroup;
import com.google.android.material.bottomnavigation.BottomNavigationView;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    public static final String EXTRA_DESTINATION = "main_destination";
    public static final String DESTINATION_TRIPS = "trips";
    public static final String EXTRA_MESSAGE = "main_message";

    private MaterialToolbar topAppBar;
    private BottomNavigationView bottomNav;
    private ViewPager2 tabPager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        SystemBarInsets.apply(findViewById(R.id.mainRoot));

        topAppBar = findViewById(R.id.topAppBar);
        setupLanguageSwitch();

        bottomNav = findViewById(R.id.bottom_navigation);
        tabPager = findViewById(R.id.tabPager);
        // FragmentStateAdapter keeps each tab's state while it is off screen and restores it
        // after a rotation, which the old replace() transaction threw away every time.
        tabPager.setAdapter(new TabAdapter(this));
        // One page either side stays built, so a swipe reveals a laid-out screen rather than
        // a blank one. Anything further away is rebuilt from its saved state.
        tabPager.setOffscreenPageLimit(1);

        // The two controls drive each other: tapping a tab pages across, and swiping moves the
        // highlight. setCurrentItem below re-enters this listener, which is harmless because
        // both paths end on the same index.
        bottomNav.setOnItemSelectedListener(item -> {
            int position = positionOf(item.getItemId());
            if (position < 0) return false;
            tabPager.setCurrentItem(position, true);
            return true;
        });
        tabPager.registerOnPageChangeCallback(new ViewPager2.OnPageChangeCallback() {
            @Override public void onPageSelected(int position) {
                bottomNav.getMenu().getItem(position).setChecked(true);
                topAppBar.setTitle(TITLES[position]);
            }
        });

        if (savedInstanceState == null) {
            navigateFromIntent(getIntent());
        }
    }

    private void setupLanguageSwitch() {
        MaterialButtonToggleGroup languageToggle = findViewById(R.id.languageToggleGroup);
        boolean english = "en".equals(Locale.getDefault().getLanguage());
        languageToggle.check(english ? R.id.languageEnglishButton : R.id.languageVietnameseButton);
        languageToggle.addOnButtonCheckedListener((group, checkedId, isChecked) -> {
            if (!isChecked) return;
            String target = checkedId == R.id.languageEnglishButton ? "en" : "vi";
            if (target.equals(Locale.getDefault().getLanguage())) return;
            AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags(target));
        });
    }

    @Override protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        navigateFromIntent(intent);
    }

    private void navigateFromIntent(Intent intent) {
        boolean openTrips = DESTINATION_TRIPS.equals(intent.getStringExtra(EXTRA_DESTINATION));
        int position = positionOf(openTrips ? R.id.nav_trips : R.id.nav_home);
        // No animation here: this runs as the screen appears, so a slide would look like a stray
        // swipe rather than a deliberate move.
        tabPager.setCurrentItem(position, false);
        bottomNav.getMenu().getItem(position).setChecked(true);
        topAppBar.setTitle(TITLES[position]);
        String message = intent.getStringExtra(EXTRA_MESSAGE);
        if (message != null && !message.isEmpty()) {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
            intent.removeExtra(EXTRA_MESSAGE);
        }
    }

    /** Tab order, shared by the pager, the menu and the titles below. */
    private static final int[] TAB_MENU_IDS = {
        R.id.nav_home, R.id.nav_community, R.id.nav_trips, R.id.nav_profile
    };
    private static final int[] TITLES = {
        R.string.app_name, R.string.community, R.string.trips, R.string.profile
    };

    private static int positionOf(int menuItemId) {
        for (int i = 0; i < TAB_MENU_IDS.length; i++) {
            if (TAB_MENU_IDS[i] == menuItemId) return i;
        }
        return -1;
    }

    /** Builds each tab on demand and hands its state back to ViewPager2 to hold. */
    private static final class TabAdapter extends FragmentStateAdapter {
        TabAdapter(androidx.fragment.app.FragmentActivity activity) { super(activity); }

        @NonNull @Override public Fragment createFragment(int position) {
            switch (position) {
                case 1: return new com.example.finalproject.presentation.community.CommunityFragment();
                case 2: return new SavedTripsFragment();
                case 3: return new com.example.finalproject.presentation.profile.ProfileFragment();
                default: return new HomeFragment();
            }
        }

        @Override public int getItemCount() { return TAB_MENU_IDS.length; }
    }
}
