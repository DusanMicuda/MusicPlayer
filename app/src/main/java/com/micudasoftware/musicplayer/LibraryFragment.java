package com.micudasoftware.musicplayer;

import android.graphics.Bitmap;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import com.google.android.material.appbar.AppBarLayout;
import com.google.android.material.appbar.CollapsingToolbarLayout;
import org.jetbrains.annotations.NotNull;
import java.util.List;

public class LibraryFragment extends Fragment implements CustomAdapter.ItemClickListener{

    private CustomAdapter adapter;
    private List<MediaBrowserCompat.MediaItem> mediaItems;
    private MediaControllerCompat controller;
    private RecyclerView recyclerView;
    public GridLayoutManager layoutManager;
    private Header header;
    private int position;
    private int offset;
    private int spanCount = 1;
    private String contentType;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        Bundle bundle = this.getArguments();
        controller = MediaControllerCompat.getMediaController(getActivity());

        if (header == null)
            header = new Header(bundle);

        if (mediaItems == null) {
            if (bundle == null)
                MainActivity.mediaBrowser.subscribe(MainActivity.mediaBrowser.getRoot(), subscriptionCallback);
            else
                MainActivity.mediaBrowser.subscribe(bundle.getString("mediaId"), subscriptionCallback);
        }

        return inflater.inflate(R.layout.library_fragment, container, false);
    }

    @Override
    public void onResume() {
        super.onResume();

        recyclerView = getView().findViewById(R.id.recyclerView);
        layoutManager = new GridLayoutManager(getContext(), spanCount);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setAdapter(adapter);
        recyclerView.setHasFixedSize(true);
        recyclerView.addOnScrollListener(onScrollListener);

        if (mediaItems != null)
            header.setHeader();

        if (position != 0)
            layoutManager.scrollToPositionWithOffset(position, offset);


        MainActivity.navigationView.getMenu().findItem(R.id.library).setChecked(true);
        if (mediaItems != null)
            BottomNavigationBehavior.setToDefault();

    }

    private final MediaBrowserCompat.SubscriptionCallback subscriptionCallback =
            new MediaBrowserCompat.SubscriptionCallback() {


        @Override
        public void onChildrenLoaded(@NotNull String parentId, @NotNull List<MediaBrowserCompat.MediaItem> children) {
            recyclerView = getView().findViewById(R.id.recyclerView);
            mediaItems = children;
            contentType = getContentType(children.get(0).getMediaId());
            if (contentType.contains(MusicProvider.CONTENT_TYPE_ALBUMS))
                spanCount = 2;
            layoutManager = new GridLayoutManager(getContext(), spanCount);
            adapter = new CustomAdapter(getActivity(), mediaItems);
            adapter.setClickListener(LibraryFragment.this);
            recyclerView.setAdapter(adapter);
            recyclerView.setLayoutManager(layoutManager);
            recyclerView.postDelayed(setVisibleItemsSelected, 500);
            header.getData();
            header.setHeader();
        }
    };

    private final Runnable setVisibleItemsSelected = new Runnable() {
        @Override
        public void run() {
            for (int i = layoutManager.findFirstVisibleItemPosition(); i <= layoutManager.findLastVisibleItemPosition(); i++) {
                if (layoutManager.findViewByPosition(i) != null && i != adapter.getItemCount()) {
                    TextView title = layoutManager.findViewByPosition(i).findViewById(R.id.text);
                    title.setSelected(true);
                    TextView subTitle = layoutManager.findViewByPosition(i).findViewById(R.id.subText);
                    subTitle.setSelected(true);
                }
            }
        }
    };

    private final RecyclerView.OnScrollListener onScrollListener = new RecyclerView.OnScrollListener() {
        @Override
        public void onScrollStateChanged(@NonNull @NotNull RecyclerView recyclerView, int newState) {
            super.onScrollStateChanged(recyclerView, newState);
            if (newState == 0)
                recyclerView.postDelayed(setVisibleItemsSelected, 500);
        }
    };

    @Override
    public void onItemClick(View view, int position) {
        MediaBrowserCompat.MediaItem mediaItem = mediaItems.get(position);
        if (mediaItem.isBrowsable()) {
            Bundle bundle = new Bundle();
            bundle.putString("mediaId", mediaItem.getMediaId());
            if (mediaItem.getDescription().getIconUri() != null)
                bundle.putString("iconUri", mediaItem.getDescription().getIconUri().toString());
            bundle.putString("title", (String) mediaItem.getDescription().getTitle());
            bundle.putString("subtitle", (String) mediaItem.getDescription().getSubtitle());
            Fragment fragment = new LibraryFragment();
            fragment.setArguments(bundle);
            MainActivity.library.add(fragment);


            this.offset = (int) layoutManager.findViewByPosition(position).getY();
            this.position = position;


            FragmentManager fragmentManager = getActivity().getSupportFragmentManager();
            fragmentManager.beginTransaction()
                    .replace(R.id.container,
                            MainActivity.library.get(MainActivity.library.size() -1))
                    .commit();
            BottomNavigationBehavior.setToDefault();
        } else if (mediaItem.isPlayable()) {
            controller.removeQueueItem(null);
            for (MediaBrowserCompat.MediaItem item : mediaItems)
                controller.addQueueItem(item.getDescription());

            Bundle extras = new Bundle();
            extras.putInt("position", position);
            controller.getTransportControls().
                    sendCustomAction("PlayFromQueue", extras);

            FragmentManager fragmentManager = getActivity().getSupportFragmentManager();
            fragmentManager.beginTransaction()
                    .replace(R.id.container,
                            MainActivity.player)
                    .commit();
            BottomNavigationBehavior.setToDefault();
        }
    }

    private String getContentType(String mediaId) {
        if (mediaId.contains(":"))
            return mediaId.split(":")[0];
        else
            return MusicProvider.CONTENT_TYPE_ROOT;
    }

    private class Header {

        Bundle bundle;
        Bitmap backgroundImage;
        String title;
        String subtitle;

        public Header(Bundle bundle) {
            this.bundle = bundle;
        }

        public void getData() {
            if (contentType.equals(MusicProvider.CONTENT_TYPE_ROOT))
                title = "Library";
            else {
                title = bundle.getString("title");
                subtitle = bundle.getString("subtitle");
            }


            if (bundle != null) {
                if (bundle.containsKey("iconUri"))
                    this.backgroundImage = MainActivity.musicProvider
                            .getImage(Uri.parse(bundle.getString("iconUri")));
            }
        }

        public void setHeader() {
            ConstraintLayout backgroundLayout = getView().findViewById(R.id.backgroundLayout);
            backgroundLayout.removeAllViewsInLayout();

            if (contentType.equals(MusicProvider.CONTENT_TYPE_ALBUMS) || contentType.equals(MusicProvider.CONTENT_TYPE_SONGS)) {
                backgroundLayout = (ConstraintLayout) getLayoutInflater()
                        .inflate(R.layout.header_image, backgroundLayout, true);
                ImageView imageView = getView().findViewById(R.id.backgroundImage);
                if (this.backgroundImage != null)
                    imageView.setImageBitmap(this.backgroundImage);
                else
                    imageView.setImageResource(R.drawable.sound);

                backgroundLayout = (ConstraintLayout) getLayoutInflater()
                        .inflate(R.layout.header_textview, backgroundLayout, true);
                TextView title = getView().findViewById(R.id.header_text);
                title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
                title.setSelected(true);
                title.setText(this.title);

                ConstraintLayout.LayoutParams titleLayoutParams =
                        (ConstraintLayout.LayoutParams) title.getLayoutParams();
                if (this.subtitle != null) {
                    title.setId(View.generateViewId());
                    backgroundLayout = (ConstraintLayout) getLayoutInflater()
                            .inflate(R.layout.header_textview, backgroundLayout, true);
                    TextView subtitle = getView().findViewById(R.id.header_text);
                    ConstraintLayout.LayoutParams subtitleLayoutParams =
                            (ConstraintLayout.LayoutParams) subtitle.getLayoutParams();
                    subtitleLayoutParams.bottomToTop = R.id.menu_button;
                    subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
                    subtitle.setSelected(true);
                    subtitle.setText(this.subtitle);

                    titleLayoutParams.bottomToTop = R.id.header_text;
                } else
                    titleLayoutParams.bottomToTop = R.id.menu_button;

                backgroundLayout = (ConstraintLayout) getLayoutInflater()
                        .inflate(R.layout.header_media_buttons, backgroundLayout, true);
                ConstraintLayout.LayoutParams mediaButtonsParams = (ConstraintLayout.LayoutParams)
                        getView().findViewById(R.id.menu_button).getLayoutParams();
                mediaButtonsParams.startToStart = R.id.backgroundLayout;
                mediaButtonsParams.bottomToBottom = R.id.backgroundLayout;

                ImageButton playAll = getView().findViewById(R.id.play_all);
                playAll.setOnClickListener(v -> {
                    playQueue();
                });

                ImageButton playRandom = getView().findViewById(R.id.play_random);
                playRandom.setOnClickListener(v -> {
                    controller.getTransportControls().setShuffleMode(PlaybackStateCompat.SHUFFLE_MODE_ALL);
                    playQueue();
                });
            } else {
                backgroundLayout = (ConstraintLayout) getLayoutInflater()
                        .inflate(R.layout.header_textview, backgroundLayout, true);

                CollapsingToolbarLayout.LayoutParams backgroundLayoutParams =
                        (CollapsingToolbarLayout.LayoutParams) backgroundLayout.getLayoutParams();
                backgroundLayoutParams.topMargin = getResources().getDimensionPixelSize(
                        getResources().getIdentifier(
                                "status_bar_height", "dimen", "android"));
                backgroundLayoutParams.setCollapseMode(
                        CollapsingToolbarLayout.LayoutParams.COLLAPSE_MODE_OFF);

                TextView title = getView().findViewById(R.id.header_text);
                ConstraintLayout.LayoutParams titleLayoutParams =
                        (ConstraintLayout.LayoutParams) title.getLayoutParams();
                titleLayoutParams.topToTop = R.id.backgroundLayout;
                titleLayoutParams.leftMargin = 44;
                titleLayoutParams.bottomMargin = 22;
                titleLayoutParams.topMargin = 22;
                title.setBackgroundColor(0);
                title.setTextSize(TypedValue.COMPLEX_UNIT_SP,24);
                title.setTypeface(title.getTypeface(), Typeface.BOLD);
                title.setText(this.title);

                backgroundLayout = (ConstraintLayout) getLayoutInflater()
                        .inflate(R.layout.header_menu_button, backgroundLayout, true);
            }

            AppBarLayout appBarLayout = getView().findViewById(R.id.appBar);
            appBarLayout.requestApplyInsets();
        }

        private void playQueue() {
            if (mediaItems.get(0).isPlayable()) {
                controller.removeQueueItem(null);
                for (MediaBrowserCompat.MediaItem item : mediaItems)
                    controller.addQueueItem(item.getDescription());

                Bundle extras = new Bundle();
                extras.putInt("position", 0);
                controller.getTransportControls().
                        sendCustomAction("PlayFromQueue", extras);
            } else {
                controller.removeQueueItem(null);

                MediaBrowserCompat.SubscriptionCallback subscriptionCallback = new MediaBrowserCompat.SubscriptionCallback() {
                    @Override
                    public void onChildrenLoaded(@NonNull String parentId, @NonNull List<MediaBrowserCompat.MediaItem> children) {
                        for (MediaBrowserCompat.MediaItem item : children)
                            controller.addQueueItem(item.getDescription());

                        Bundle extras = new Bundle();
                        extras.putInt("position", 0);
                        controller.getTransportControls().sendCustomAction("PlayFromQueue", extras);
                    }
                };
                for (MediaBrowserCompat.MediaItem mediaItem : mediaItems)
                    MainActivity.mediaBrowser.subscribe(mediaItem.getMediaId(), subscriptionCallback);
            }

            FragmentManager fragmentManager = getActivity().getSupportFragmentManager();
            fragmentManager.beginTransaction()
                    .replace(R.id.container,
                            MainActivity.player)
                    .commit();
            BottomNavigationBehavior.setToDefault();
        }
    }
}
