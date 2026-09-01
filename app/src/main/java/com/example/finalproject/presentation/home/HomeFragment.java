package com.example.finalproject.presentation.home;

import android.os.Bundle;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.finalproject.R;
import com.example.finalproject.presentation.planner.PlannerActivity;
import com.example.finalproject.presentation.catalog.CatalogActivity;
import com.example.finalproject.infrastructure.local.repository.CachingWeatherRepository;
import com.example.finalproject.domain.model.WeatherSnapshot;
import com.example.finalproject.domain.model.WeatherCodeMapper;
import com.example.finalproject.domain.callback.RepositoryCallback;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

public class HomeFragment extends Fragment {
    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_home, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        view.findViewById(R.id.startPlanningButton).setOnClickListener(v ->
            startActivity(new Intent(requireContext(), PlannerActivity.class))
        );
        view.findViewById(R.id.explorePlacesButton).setOnClickListener(v ->
            startActivity(new Intent(requireContext(), CatalogActivity.class))
        );
        view.findViewById(R.id.weatherRetryButton).setOnClickListener(v -> loadWeather(view));
        loadWeather(view);
    }

    private void loadWeather(View view) {
        view.findViewById(R.id.weatherRetryButton).setVisibility(View.GONE);
        text(view, R.id.weatherTemp).setText(R.string.weather_loading);
        new CachingWeatherRepository(requireContext()).load(new RepositoryCallback<WeatherSnapshot>() {
            @Override public void onSuccess(WeatherSnapshot snapshot) {
                // The request outlives the fragment when the user navigates away mid-flight.
                if (!isAdded()) return;
                showWeather(view, snapshot);
            }

            @Override public void onError(Exception error) {
                if (!isAdded()) return;
                showWeatherError(view);
            }
        }, cachedAt -> {
            if (isAdded()) Toast.makeText(requireContext(),
                R.string.offline_weather_notice, Toast.LENGTH_SHORT).show();
        });
    }

    private void showWeather(View view, WeatherSnapshot snapshot) {
        text(view, R.id.weatherIcon).setText(WeatherCodeMapper.icon(snapshot.code, snapshot.day));
        text(view, R.id.weatherTemp).setText(getString(R.string.weather_temperature,
            Math.round(snapshot.temperature)));
        text(view, R.id.weatherCondition).setText(getString(R.string.weather_condition_format,
            getString(WeatherCodeMapper.labelRes(snapshot.code)), Math.round(snapshot.apparent)));
        text(view, R.id.weatherDetails).setText(getString(R.string.weather_details_format,
            snapshot.humidity, Math.round(snapshot.wind)));
        text(view, R.id.weatherForecast).setText(formatForecast(snapshot.forecast));
    }

    private void showWeatherError(View view) {
        text(view, R.id.weatherTemp).setText(R.string.weather_unavailable);
        text(view, R.id.weatherCondition).setText(R.string.weather_check_connection);
        view.findViewById(R.id.weatherRetryButton).setVisibility(View.VISIBLE);
    }

    /** Turns the API's ISO dates into short weekday names, skipping any entry that fails to parse. */
    private String formatForecast(List<WeatherSnapshot.Day> forecast) {
        SimpleDateFormat isoDate = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        SimpleDateFormat weekday = new SimpleDateFormat("EEE", Locale.getDefault());
        StringBuilder lines = new StringBuilder();
        for (WeatherSnapshot.Day day : forecast) {
            String label;
            try {
                label = weekday.format(isoDate.parse(day.date));
            } catch (ParseException | NullPointerException ignored) {
                continue;
            }
            if (lines.length() > 0) lines.append('\n');
            lines.append(getString(R.string.weather_forecast_format,
                label, Math.round(day.min), Math.round(day.max), day.rain));
        }
        return lines.toString();
    }

    private TextView text(View view, int id) {
        return view.findViewById(id);
    }
}
