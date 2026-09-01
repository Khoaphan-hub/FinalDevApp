package com.example.finalproject.presentation.saved;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.finalproject.R;
import com.example.finalproject.domain.model.Itinerary;
import com.example.finalproject.domain.model.ItineraryDay;
import com.example.finalproject.domain.model.ItineraryStop;
import com.example.finalproject.domain.model.SavedTrip;

import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

final class SavedTripAdapter extends RecyclerView.Adapter<SavedTripAdapter.Holder> {
    interface Listener {
        void onOpen(SavedTrip trip);
        void onDelete(SavedTrip trip);
    }
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
        holder.title.setText(titleFor(holder.itemView.getContext(), trip.getItinerary()));
        String date = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date(trip.getSavedAt()));
        holder.meta.setText(holder.itemView.getContext().getString(R.string.saved_meta,
            trip.getItinerary().getDays().size(), date));
        holder.itemView.setOnClickListener(v -> listener.onOpen(trip));
        holder.delete.setOnClickListener(v -> listener.onDelete(trip));
    }

    @Override public int getItemCount() { return trips.size(); }

    /** Saved trips share one backend title, so append the first stop to tell them apart. */
    private static String titleFor(Context context, Itinerary itinerary) {
        for (ItineraryDay day : itinerary.getDays()) {
            for (ItineraryStop stop : day.getStops()) {
                if (stop.getType() != ItineraryStop.Type.ACCOMMODATION
                    && stop.getName() != null && !stop.getName().trim().isEmpty()) {
                    return context.getString(R.string.saved_title_with_highlight,
                        itinerary.getTitle(), stop.getName());
                }
            }
        }
        return itinerary.getTitle();
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView meta;
        final View delete;
        Holder(View view) {
            super(view);
            title = view.findViewById(R.id.savedTripTitle);
            meta = view.findViewById(R.id.savedTripMeta);
            delete = view.findViewById(R.id.savedTripDelete);
        }
    }
}
