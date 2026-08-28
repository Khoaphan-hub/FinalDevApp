package com.example.finalproject.presentation.saved;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.finalproject.R;
import com.example.finalproject.domain.model.SavedTrip;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

final class SavedTripAdapter extends RecyclerView.Adapter<SavedTripAdapter.Holder> {
    interface Listener { void onOpen(SavedTrip trip); }
    private final List<SavedTrip> trips = new ArrayList<>();
    private final Listener listener;

    SavedTripAdapter(Listener listener) { this.listener = listener; }

    void submit(List<SavedTrip> values) {
        trips.clear();
        trips.addAll(values);
        notifyDataSetChanged();
    }

    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_saved_trip, parent, false));
    }

    @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
        SavedTrip trip = trips.get(position);
        holder.title.setText(trip.getItinerary().getTitle());
        String date = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date(trip.getSavedAt()));
        holder.meta.setText(trip.getItinerary().getDays().size() + " ngày  •  Lưu " + date);
        holder.itemView.setOnClickListener(v -> listener.onOpen(trip));
    }

    @Override public int getItemCount() { return trips.size(); }

    static final class Holder extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView meta;
        Holder(View view) {
            super(view);
            title = view.findViewById(R.id.savedTripTitle);
            meta = view.findViewById(R.id.savedTripMeta);
        }
    }
}
