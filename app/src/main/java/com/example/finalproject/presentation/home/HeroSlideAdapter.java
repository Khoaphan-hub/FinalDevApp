package com.example.finalproject.presentation.home;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.finalproject.R;

/**
 * The rotating scenery on the home screen.
 *
 * Images are bundled rather than fetched so the carousel looks the same on a first launch with
 * no network as it does later. All five are Da Lat landscapes taken from the project's own
 * catalog, and each one has its own kicker and caption in both languages: a single caption
 * reused across photos reads as filler the second time round.
 */
public final class HeroSlideAdapter extends RecyclerView.Adapter<HeroSlideAdapter.SlideHolder> {

    /** Order must match the hero_kickers and hero_captions arrays. */
    private static final int[] IMAGES = {
        R.drawable.hero_cherry_blossom,
        R.drawable.hero_xuan_huong_lake,
        R.drawable.hero_pongour_waterfall,
        R.drawable.hero_pink_grass_hill,
        R.drawable.hero_tuyen_lam_lake,
    };

    private final String[] kickers;
    private final String[] captions;

    public HeroSlideAdapter(String[] kickers, String[] captions) {
        this.kickers = kickers;
        this.captions = captions;
    }

    public static int slideCount() {
        return IMAGES.length;
    }

    @NonNull
    @Override
    public SlideHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_hero_slide, parent, false);
        return new SlideHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull SlideHolder holder, int position) {
        holder.image.setImageResource(IMAGES[position]);
        // Guard the text arrays: a translation with fewer entries would otherwise crash here.
        holder.kicker.setText(position < kickers.length ? kickers[position] : "");
        holder.caption.setText(position < captions.length ? captions[position] : "");
        holder.image.setContentDescription(
            position < captions.length ? captions[position] : null);
    }

    @Override
    public int getItemCount() {
        return IMAGES.length;
    }

    static final class SlideHolder extends RecyclerView.ViewHolder {
        final ImageView image;
        final TextView kicker;
        final TextView caption;

        SlideHolder(@NonNull View itemView) {
            super(itemView);
            image = itemView.findViewById(R.id.heroSlideImage);
            kicker = itemView.findViewById(R.id.heroSlideKicker);
            caption = itemView.findViewById(R.id.heroSlideCaption);
        }
    }
}
