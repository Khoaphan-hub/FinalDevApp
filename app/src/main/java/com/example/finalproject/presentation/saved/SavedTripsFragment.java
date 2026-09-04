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
    private androidx.activity.result.ActivityResultLauncher<Intent> signInLauncher;
    private SavedTripAdapter adapter;
    private View progress;
    private View empty;

    @Nullable @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_saved_trips, container, false);
    }

    @Override public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        signInLauncher = registerForActivityResult(
            new androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (getView() != null) applySignedInState(getView());
            });
    }

    @Override public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        view.findViewById(R.id.importTripButton).setOnClickListener(v -> startActivity(new Intent(
                requireContext(), com.example.finalproject.presentation.importtrip.ImportTripActivity.class)));
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
                    .setTitle(R.string.delete_trip_title)
                    .setMessage(R.string.delete_trip_message)
                    .setNegativeButton(R.string.keep, null)
                    .setPositiveButton(R.string.delete, (dialog, which) -> deleteTrip(trip))
                    .show();
            }
        });
        recycler.setAdapter(adapter);
        progress = view.findViewById(R.id.savedTripsProgress);
        empty = view.findViewById(R.id.savedTripsEmpty);

        ((android.widget.TextView) view.findViewById(R.id.signedOutMessage))
            .setText(R.string.trips_signed_out_message);
        view.findViewById(R.id.signedOutSignInButton).setOnClickListener(v ->
            signInLauncher.launch(new Intent(requireContext(),
                com.example.finalproject.presentation.auth.LoginActivity.class)));
        applySignedInState(view);
    }

    /**
     * Draws the sign-in prompt instead of the list when there is no account yet. Trips are
     * saved against an account, so an empty list would be misleading while signed out.
     */
    private void applySignedInState(View root) {
        boolean signedIn = com.example.finalproject.infrastructure.local.SessionState
            .isSignedIn(requireContext());
        root.findViewById(R.id.signedOutPanel).setVisibility(signedIn ? View.GONE : View.VISIBLE);
        root.findViewById(R.id.savedTripsRecycler).setVisibility(signedIn ? View.VISIBLE : View.GONE);
        progress.setVisibility(signedIn ? View.VISIBLE : View.GONE);
        if (!signedIn) empty.setVisibility(View.GONE);
        if (signedIn) load();
    }

    private void deleteTrip(SavedTrip trip) {
        progress.setVisibility(View.VISIBLE);
        new RoomSavedTripRepository(requireContext()).delete(trip.getId(), new RepositoryCallback<Void>() {
            @Override public void onSuccess(Void ignored) {
                Toast.makeText(requireContext(), R.string.trip_deleted, Toast.LENGTH_SHORT).show();
                load();
            }

            @Override public void onError(Exception error) {
                progress.setVisibility(View.GONE);
                Toast.makeText(requireContext(), R.string.trip_delete_error, Toast.LENGTH_SHORT).show();
            }
        });
    }

    @Override public void onResume() {
        super.onResume();
        // Goes through the gate rather than straight to load(), so returning to this tab while
        // signed out shows the prompt instead of an empty list.
        if (getView() != null) applySignedInState(getView());
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
                Toast.makeText(requireContext(), R.string.saved_read_error, Toast.LENGTH_SHORT).show();
            }
        });
    }
}
