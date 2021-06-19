package com.micudasoftware.musicplayer;

import android.app.Activity;
import android.graphics.Bitmap;
import android.support.v4.media.MediaBrowserCompat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import org.jetbrains.annotations.NotNull;
import java.util.List;

public class CustomAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private Activity activity;
    private final List<MediaBrowserCompat.MediaItem> mediaItems;
    private ItemClickListener mClickListener;
    public static final int TYPE_ITEM = 0;

    public CustomAdapter(Activity context, List<MediaBrowserCompat.MediaItem> mediaItems) {
        this.activity = context;
        this.mediaItems = mediaItems;
    }

    @NotNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NotNull ViewGroup viewGroup, int viewType) {
        if (mediaItems.get(0).getMediaId().contains(":") &&
                mediaItems.get(0).getMediaId().split(":")[0].contains(MusicDatabase.COLUMN_ALBUM)) {
            View view = LayoutInflater.from(viewGroup.getContext())
                    .inflate(R.layout.card_list, viewGroup, false);
            return new ItemViewHolder(view);
        }
        else {
            View view = LayoutInflater.from(viewGroup.getContext())
                    .inflate(R.layout.item_list, viewGroup, false);
            return new ItemViewHolder(view);
        }
    }

    @Override
    public void onBindViewHolder(@NotNull RecyclerView.ViewHolder viewHolder, int position) {
        ItemViewHolder itemViewHolder = (ItemViewHolder) viewHolder;
        itemViewHolder.getTitle().setText(mediaItems.get(position).getDescription().getTitle());
        itemViewHolder.getTitle().setSelected(false);
        itemViewHolder.getSubTitle().setText(mediaItems.get(position).getDescription().getSubtitle());
        itemViewHolder.getSubTitle().setSelected(false);
        itemViewHolder.getImage().setImageResource(R.drawable.sound);

        new Thread(() -> {
            Bitmap bitmap = MainActivity.musicProvider
                    .getImage(mediaItems.get(position).getDescription().getIconUri());
            if (bitmap != null)
                activity.runOnUiThread(() -> itemViewHolder.getImage().setImageBitmap(bitmap));
        }).start();
    }



    @Override
    public int getItemCount() {
        return  mediaItems.size();
    }

    @Override
    public int getItemViewType(int position) {
        return TYPE_ITEM;
    }

    void setClickListener(ItemClickListener itemClickListener) {
        this.mClickListener = itemClickListener;
    }

    public class ItemViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        private final TextView title;
        private final TextView subTitle;
        private final ImageView image;

        public ItemViewHolder(View view)  {
            super(view);
            view.setOnClickListener(this);
            title = view.findViewById(R.id.text);
            subTitle = view.findViewById(R.id.subText);
            image = view.findViewById(R.id.icon);
        }

        @Override
        public void onClick(View view) {
            if (mClickListener != null) mClickListener.onItemClick(view, getAdapterPosition());
        }

        public TextView getTitle() {
            return title;
        }

        public TextView getSubTitle() {
            return subTitle;
        }

        public ImageView getImage() {
            return image;
        }
    }

    public interface ItemClickListener {
        void onItemClick(View view, int position);
    }
}
