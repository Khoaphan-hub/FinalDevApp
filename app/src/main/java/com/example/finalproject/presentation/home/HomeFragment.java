package com.example.finalproject.presentation.home;

import android.os.Bundle;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.example.finalproject.R;
import com.example.finalproject.presentation.planner.PlannerActivity;

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
            Toast.makeText(requireContext(), "Danh sách địa điểm sẽ được nối với Django API.", Toast.LENGTH_SHORT).show()
        );
    }
}
