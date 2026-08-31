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
import com.example.finalproject.infrastructure.remote.RemoteWeatherRepository;
import com.example.finalproject.domain.model.WeatherSnapshot;
import com.example.finalproject.domain.model.WeatherCodeMapper;
import com.example.finalproject.domain.callback.RepositoryCallback;
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
        ((TextView)view.findViewById(R.id.weatherTemp)).setText(R.string.weather_loading);
        new RemoteWeatherRepository().load(new RepositoryCallback<WeatherSnapshot>() {
            @Override public void onSuccess(WeatherSnapshot w) { if(!isAdded())return; ((TextView)view.findViewById(R.id.weatherIcon)).setText(WeatherCodeMapper.icon(w.code,w.day));((TextView)view.findViewById(R.id.weatherTemp)).setText(Math.round(w.temperature)+"°C");((TextView)view.findViewById(R.id.weatherCondition)).setText(getString(R.string.weather_condition_format,getString(WeatherCodeMapper.labelRes(w.code)),Math.round(w.apparent)));((TextView)view.findViewById(R.id.weatherDetails)).setText(getString(R.string.weather_details_format,w.humidity,Math.round(w.wind)));StringBuilder f=new StringBuilder();SimpleDateFormat input=new SimpleDateFormat("yyyy-MM-dd",Locale.US),out=new SimpleDateFormat("EEE",Locale.getDefault());for(WeatherSnapshot.Day d:w.forecast){try{if(f.length()>0)f.append('\n');f.append(getString(R.string.weather_forecast_format,out.format(input.parse(d.date)),Math.round(d.min),Math.round(d.max),d.rain));}catch(Exception ignored){}}((TextView)view.findViewById(R.id.weatherForecast)).setText(f.toString()); }
            @Override public void onError(Exception e) { if(!isAdded())return;((TextView)view.findViewById(R.id.weatherTemp)).setText(R.string.weather_unavailable);((TextView)view.findViewById(R.id.weatherCondition)).setText(R.string.weather_check_connection);view.findViewById(R.id.weatherRetryButton).setVisibility(View.VISIBLE); }
        });
    }
}
