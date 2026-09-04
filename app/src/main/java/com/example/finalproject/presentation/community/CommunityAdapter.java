package com.example.finalproject.presentation.community;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.finalproject.R;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

public class CommunityAdapter extends RecyclerView.Adapter<CommunityAdapter.ViewHolder> {

    public interface Listener {
        void onClick(JSONObject item);
        void onRate(JSONObject item, float rating);
    }

    private final List<JSONObject> items = new ArrayList<>();
    private final Listener listener;

    public CommunityAdapter(Listener listener) {
        this.listener = listener;
    }

    public void setItems(List<JSONObject> newItems) {
        items.clear();
        items.addAll(newItems);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_community_itinerary, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        JSONObject item = items.get(position);
        holder.title.setText(item.optString("title", holder.itemView.getContext().getString(R.string.untitled_trip)));
        JSONObject owner = item.optJSONObject("owner");
        String author = owner != null ? owner.optString("username", holder.itemView.getContext().getString(R.string.unknown_author)) : holder.itemView.getContext().getString(R.string.unknown_author);
        holder.author.setText(holder.itemView.getContext().getString(R.string.by_author, author));
        holder.description.setText(item.optString("description", holder.itemView.getContext().getString(R.string.no_description)));
        
        double avgRating = item.optDouble("average_rating", 0.0);
        int ratingCount = item.optInt("rating_count", 0);
        if (ratingCount > 0) {
            holder.ratingInfo.setText(holder.itemView.getContext().getString(R.string.average_rating_text, avgRating, ratingCount));
        } else {
            holder.ratingInfo.setText(R.string.no_ratings_yet);
        }
        
        holder.ratingBar.setOnRatingBarChangeListener(null);
        holder.ratingBar.setRating(0); // Optional: show user's own previous rating if available. Currently we don't have it in collection.
        holder.ratingBar.setOnRatingBarChangeListener((ratingBar, rating, fromUser) -> {
            if (fromUser && listener != null) {
                listener.onRate(item, rating);
            }
        });

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) listener.onClick(item);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView title;
        final TextView author;
        final TextView description;
        final TextView ratingInfo;
        final android.widget.RatingBar ratingBar;

        ViewHolder(View view) {
            super(view);
            title = view.findViewById(R.id.communityTitle);
            author = view.findViewById(R.id.communityAuthor);
            description = view.findViewById(R.id.communityDescription);
            ratingInfo = view.findViewById(R.id.communityRatingInfo);
            ratingBar = view.findViewById(R.id.communityRatingBar);
        }
    }
}
