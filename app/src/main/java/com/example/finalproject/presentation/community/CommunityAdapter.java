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
        holder.title.setText(item.optString("title", "Untitled Trip"));
        JSONObject owner = item.optJSONObject("owner");
        String author = owner != null ? owner.optString("username", "Unknown") : "Unknown";
        holder.author.setText("By " + author);
        holder.description.setText(item.optString("description", "No description"));
        
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

        ViewHolder(View view) {
            super(view);
            title = view.findViewById(R.id.communityTitle);
            author = view.findViewById(R.id.communityAuthor);
            description = view.findViewById(R.id.communityDescription);
        }
    }
}
