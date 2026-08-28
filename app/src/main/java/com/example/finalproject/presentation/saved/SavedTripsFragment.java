package com.example.finalproject.presentation.saved;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.finalproject.R;
import com.example.finalproject.domain.callback.RepositoryCallback;
import com.example.finalproject.domain.model.SavedTrip;
import com.example.finalproject.infrastructure.local.repository.RoomSavedTripRepository;
import com.example.finalproject.presentation.itinerary.ItineraryActivity;

import java.util.List;

public class SavedTripsFragment extends Fragment {
    private SavedTripAdapter adapter;
    private View progress;
    private View empty;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_saved_trips, container, false);
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        RecyclerView recycler = view.findViewById(R.id.savedTripsRecycler);
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new SavedTripAdapter(new SavedTripAdapter.Listener() {
            @Override public void onOpen(SavedTrip trip) {
                Intent intent = new Intent(requireContext(), ItineraryActivity.class);
                intent.putExtra(ItineraryActivity.EXTRA_ITINERARY, trip.getItinerary());
                startActivity(intent);
            }

            @Override public void onDelete(SavedTrip trip) {
                new com.google.android.material.dialog.MaterialAlertDialogBuilder(requireContext())
                    .setTitle("Xoá chuyến đi?")
                    .setMessage("Lịch trình này sẽ bị xoá khỏi thiết bị.")
                    .setNegativeButton("Giữ lại", null)
                    .setPositiveButton("Xoá", (dialog, which) -> deleteTrip(trip))
                    .show();
            }
        });
        recycler.setAdapter(adapter);
        progress = view.findViewById(R.id.savedTripsProgress);
        empty = view.findViewById(R.id.savedTripsEmpty);
    }

    private void deleteTrip(SavedTrip trip) {
        progress.setVisibility(View.VISIBLE);
        new RoomSavedTripRepository(requireContext()).delete(trip.getId(), new RepositoryCallback<Void>() {
            @Override public void onSuccess(Void ignored) {
                Toast.makeText(requireContext(), "Đã xoá chuyến đi.", Toast.LENGTH_SHORT).show();
                load();
            }

            @Override public void onError(Exception error) {
                progress.setVisibility(View.GONE);
                Toast.makeText(requireContext(), "Không thể xoá chuyến đi.", Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override public void onResume() {
        super.onResume();
        load();
    }

    private void load() {
        progress.setVisibility(View.VISIBLE);
        empty.setVisibility(View.GONE);
        new RoomSavedTripRepository(requireContext()).loadAll(new RepositoryCallback<List<SavedTrip>>() {
            @Override public void onSuccess(List<SavedTrip> trips) {
                progress.setVisibility(View.GONE);
                adapter.submit(trips);
                empty.setVisibility(trips.isEmpty() ? View.VISIBLE : View.GONE);
            }
            @Override public void onError(Exception error) {
                progress.setVisibility(View.GONE);
                empty.setVisibility(View.VISIBLE);
                Toast.makeText(requireContext(), "Không thể đọc chuyến đi đã lưu.", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
