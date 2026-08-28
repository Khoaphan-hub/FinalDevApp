package com.example.finalproject.presentation;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.example.finalproject.R;
import com.example.finalproject.presentation.home.HomeFragment;
import com.example.finalproject.presentation.saved.SavedTripsFragment;
import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        SystemBarInsets.apply(findViewById(R.id.mainRoot));

        MaterialToolbar topAppBar = findViewById(R.id.topAppBar);

        BottomNavigationView bottomNav = findViewById(R.id.bottom_navigation);
        bottomNav.setOnItemSelectedListener(item -> {
            Fragment selectedFragment = null;
            int itemId = item.getItemId();
            if (itemId == R.id.nav_home) {
                selectedFragment = new HomeFragment();
                topAppBar.setTitle("Journify");
            } else if (itemId == R.id.nav_trips) {
                selectedFragment = new SavedTripsFragment();
                topAppBar.setTitle("Chuyến đi");
            } else if (itemId == R.id.nav_profile) {
                selectedFragment = ComingSoonFragment.newInstance(
                    "Hồ sơ du lịch",
                    "Đăng nhập và đồng bộ tài khoản sẽ được kết nối với Django ở mốc API."
                );
                topAppBar.setTitle("Hồ sơ");
            }
            if (selectedFragment != null) {
                getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container, selectedFragment).commit();
            }
            return true;
        });

        if (savedInstanceState == null) {
            getSupportFragmentManager().beginTransaction().replace(R.id.fragment_container, new HomeFragment()).commit();
        }
    }
}
