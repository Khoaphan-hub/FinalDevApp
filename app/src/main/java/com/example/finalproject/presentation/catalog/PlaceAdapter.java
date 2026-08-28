package com.example.finalproject.presentation.catalog;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.finalproject.R;
import com.example.finalproject.domain.model.Place;
import com.example.finalproject.infrastructure.remote.RemoteImageLoader;
import com.google.android.material.checkbox.MaterialCheckBox;

import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class PlaceAdapter extends RecyclerView.Adapter<PlaceAdapter.Holder> {
    public interface Listener {
        void onOpen(Place place);
        boolean onSelectionChangeRequested(Place place, boolean selected);
    }

    private final List<Place> places = new ArrayList<>();
    private final java.util.Set<String> selectedKeys = new java.util.HashSet<>();
    private final boolean selectionMode;
    private final Listener listener;

    public PlaceAdapter(boolean selectionMode, Listener listener) {
        this.selectionMode = selectionMode;
        this.listener = listener;
    }

    public void submit(List<Place> newPlaces) {
        places.clear();
        places.addAll(newPlaces);
        notifyDataSetChanged();
    }

    @NonNull @Override public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_place, parent, false));
    }

    @Override public void onBindViewHolder(@NonNull Holder holder, int position) {
        Place place = places.get(position);
        holder.name.setText(place.getName());
        holder.address.setText(place.getAddress());
        String rating = place.getRating() > 0 ? String.format(Locale.getDefault(), "★ %.1f", place.getRating()) : "Chưa có đánh giá";
        String price = place.getPriceVnd() > 0 ? "  •  " + NumberFormat.getNumberInstance(new Locale("vi", "VN")).format(place.getPriceVnd()) + " ₫" : "  •  Miễn phí";
        holder.meta.setText(rating + price);
        holder.image.setTag(null);
        holder.image.setImageResource("EATERY".equals(place.getType()) ? R.drawable.sample_eatery : R.drawable.sample_poi);
        RemoteImageLoader.load(place.getImageUrl(), holder.image);
        holder.checkBox.setVisibility(selectionMode ? View.VISIBLE : View.GONE);
        holder.checkBox.setOnCheckedChangeListener(null);
        holder.checkBox.setChecked(selectedKeys.contains(key(place)));
        holder.checkBox.setOnCheckedChangeListener((button, checked) -> {
            if (listener == null || listener.onSelectionChangeRequested(place, checked)) {
                if (checked) selectedKeys.add(key(place)); else selectedKeys.remove(key(place));
            } else {
                button.setOnCheckedChangeListener(null);
                button.setChecked(!checked);
                notifyItemChanged(holder.getBindingAdapterPosition());
            }
        });
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onOpen(place);
        });
    }

    public List<Integer> selectedIds(String type) {
        List<Integer> ids = new ArrayList<>();
        String prefix = type.toUpperCase(Locale.ROOT) + ":";
        for (String key : selectedKeys) {
            if (key.startsWith(prefix)) ids.add(Integer.parseInt(key.substring(prefix.length())));
        }
        return ids;
    }

    public int selectedCount(String type) { return selectedIds(type).size(); }

    private String key(Place place) { return place.getType().toUpperCase(Locale.ROOT) + ":" + place.getId(); }

    @Override public int getItemCount() { return places.size(); }

    static final class Holder extends RecyclerView.ViewHolder {
        final ImageView image;
        final TextView name;
        final TextView address;
        final TextView meta;
        final MaterialCheckBox checkBox;
        Holder(View itemView) {
            super(itemView);
            image = itemView.findViewById(R.id.placeImage);
            name = itemView.findViewById(R.id.placeName);
            address = itemView.findViewById(R.id.placeAddress);
            meta = itemView.findViewById(R.id.placeMeta);
            checkBox = itemView.findViewById(R.id.placeCheckBox);
        }
    }
}
