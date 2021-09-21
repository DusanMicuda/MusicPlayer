package com.micudasoftware.musicplayer;

import android.content.ComponentName;
import android.content.Context;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.provider.MediaStore;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;
import android.util.Size;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.io.File;
import java.io.IOException;

public class PlayerFragment extends Fragment {

    Context context;
    Handler handler = new Handler();
    private MediaBrowserCompat mediaBrowser;
    MediaControllerCompat mediaController;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.player, container, false);
    }

    @Override
    public void onResume() {
        super.onResume();
        BottomNavigationView navigationView = getView().getRootView().findViewById(R.id.navigationView);
        navigationView.getMenu().findItem(R.id.player).setChecked(true);
        context = getContext();

        mediaBrowser = new MediaBrowserCompat(context,
                new ComponentName(getActivity(), PlayerService.class),
                connectionCallbacks,
                null); // optional Bundle
        mediaBrowser.connect();
        getActivity().setVolumeControlStream(AudioManager.STREAM_MUSIC);

        SeekBar seekBar = getView().findViewById(R.id.seekBar);
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    mediaController.getTransportControls().seekTo(progress);
                    seekBar.setProgress(progress);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        buildTransportControls();
    }

    @Override
    public void onPause() {
        super.onPause();
        mediaBrowser.disconnect();
        handler.removeCallbacksAndMessages(null);
    }

    private void update() {
        TextView currentPosition = getView().findViewById(R.id.currentPosition);
        currentPosition.setText(getDuration((int) mediaController.getPlaybackState().getCurrentPosition(null)));

        SeekBar seekBar = getView().findViewById(R.id.seekBar);
        seekBar.setProgress((int) mediaController.getPlaybackState().getCurrentPosition(null));

        if (mediaController.getPlaybackState().getState() == PlaybackStateCompat.STATE_PLAYING) {
            Runnable runnable = () -> update();
            handler.postDelayed(runnable, 10);
        }
    }

    private String getDuration(long duration) {
        long seconds = duration / 1000;
        long minutes = seconds / 60;
        seconds = seconds % 60;
        if (seconds < 10)
            return minutes + ":0" + seconds;
        else
            return minutes + ":" + seconds;
    }

    private final MediaBrowserCompat.ConnectionCallback connectionCallbacks =
            new MediaBrowserCompat.ConnectionCallback() {
                @Override
                public void onConnected() {

                    // Get the token for the MediaSession
                    MediaSessionCompat.Token token = mediaBrowser.getSessionToken();

                    // Create a MediaControllerCompat
                    mediaController = null;
                    try {
                        mediaController = new MediaControllerCompat(getContext(), // Context
                                token);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }

                    // Save the controller
                    MediaControllerCompat.setMediaController(getActivity(), mediaController);

                    // Finish building the UI
                    buildTransportControls();
                    init();
                    update();
                }

                @Override
                public void onConnectionSuspended() {
                    // The Service has crashed. Disable transport controls until it automatically reconnects
                }

                @Override
                public void onConnectionFailed() {
                    // The Service has refused our connection
                }
            };

    void buildTransportControls() {
        // Grab the view for the play/pause button
        ImageButton button = getActivity().findViewById(R.id.play);
        button.setOnClickListener(v -> {
            if (mediaController.getPlaybackState().getState() == PlaybackStateCompat.STATE_PLAYING)
                mediaController.getTransportControls().pause();
            else
                mediaController.getTransportControls().play();
        });
        button = getView().findViewById(R.id.skipBack);
        button.setOnClickListener(v -> mediaController.getTransportControls().skipToPrevious());
        button = getView().findViewById(R.id.back);
        button.setOnClickListener(v -> mediaController.getTransportControls().rewind());
        button = getView().findViewById(R.id.forward);
        button.setOnClickListener(v -> mediaController.getTransportControls().fastForward());
        button = getView().findViewById(R.id.skipForward);
        button.setOnClickListener(v -> mediaController.getTransportControls().skipToNext());

        MediaControllerCompat mediaController = MediaControllerCompat.getMediaController(getActivity());

        // Register a Callback to stay in sync
        mediaController.registerCallback(controllerCallback);
    }

    private void init() {
        TextView duration = getView().findViewById(R.id.duration);
        duration.setText(getDuration(mediaController.getMetadata().getLong(MediaMetadataCompat.METADATA_KEY_DURATION)));
        TextView songName = getView().findViewById(R.id.songName);
        songName.setText(mediaController.getMetadata().getString(MediaMetadataCompat.METADATA_KEY_TITLE));
        TextView artistAlbum = getView().findViewById(R.id.artistAlbum);
        artistAlbum.setText(mediaController.getMetadata().getString(MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST));

        ImageView imageView = getView().findViewById(R.id.imageView);
        imageView.setImageBitmap(mediaController.getMetadata().getBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART));

        SeekBar seekBar = getView().findViewById(R.id.seekBar);
        seekBar.setIndeterminate(false);
        seekBar.setMax((int) mediaController.getMetadata().getLong(MediaMetadataCompat.METADATA_KEY_DURATION));
    }

    MediaControllerCompat.Callback controllerCallback =
            new MediaControllerCompat.Callback() {
                @Override
                public void onMetadataChanged(MediaMetadataCompat metadata) {
                    init();
                }

                @Override
                public void onPlaybackStateChanged(PlaybackStateCompat state) {
                    ImageButton imageButton = getView().findViewById(R.id.play);
                    if (state.getState() == PlaybackStateCompat.STATE_PLAYING){
                        imageButton.setImageResource(android.R.drawable.ic_media_pause);
                        update();
                    } else
                        imageButton.setImageResource(android.R.drawable.ic_media_play);
                }
            };
}