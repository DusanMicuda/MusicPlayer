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
    MediaPlayer mediaPlayer = new MediaPlayer();
    File file;
    Handler handler = new Handler();
    private MediaBrowserCompat mediaBrowser;

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



//        Bundle bundle = this.getArguments();
//        String uri = null;
//        try {
//            uri = bundle.getString("songUri");
//        } catch (NullPointerException e) {
//            e.printStackTrace();
//        }

//        mediaBrowser = new MediaBrowserCompat(context,
//                new ComponentName(getActivity(), PlayerService.class),
//                connectionCallbacks,
//                null); // optional Bundle
//        mediaBrowser.connect();
//        getActivity().setVolumeControlStream(AudioManager.STREAM_MUSIC);
//
//        if (uri != null) {
//            if (mediaPlayer.isPlaying()){
//                if (!uri.contains(file.getName()))
//                    prepareFile(uri);
//            } else
//                prepareFile(uri);
//
//            try {
//                ImageView imageView = getView().findViewById(R.id.imageView);
//                imageView.setImageBitmap(context.getContentResolver()
//                        .loadThumbnail(Uri.parse(uri), new Size(1000, 1000), null));
//            } catch (IOException e) {
//                e.printStackTrace();
//            }
//
//            TextView duration = getView().findViewById(R.id.duration);
//            duration.setText(getDuration(mediaPlayer.getDuration()));
//            TextView songName = getView().findViewById(R.id.songName);
//            songName.setText(LibraryFragment.getSongData(context, getSongId(uri), MediaStore.Audio.Media.TITLE));
//            TextView artistAlbum = getView().findViewById(R.id.artistAlbum);
//            artistAlbum.setText(LibraryFragment.getSongData(context, getSongId(uri), MediaStore.Audio.Media.ARTIST) + " - "
//                    + LibraryFragment.getSongData(context, getSongId(uri), MediaStore.Audio.Media.ALBUM));
//        }
//
//        SeekBar seekBar = getView().findViewById(R.id.seekBar);
//        seekBar.setIndeterminate(false);
//        seekBar.setMax(mediaPlayer.getDuration());
//        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
//            @Override
//            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
//                if (fromUser) {
//                    mediaPlayer.seekTo(progress);
//                    seekBar.setProgress(progress);
//                }
//            }
//
//            @Override
//            public void onStartTrackingTouch(SeekBar seekBar) {}
//
//            @Override
//            public void onStopTrackingTouch(SeekBar seekBar) {}
//        });
//
//        ImageButton button = getView().findViewById(R.id.play);
//        button.setOnClickListener(v -> play());
//
//        update();
    }

    @Override
    public void onPause() {
        super.onPause();
        handler.removeCallbacksAndMessages(null);
    }

    public void play() {
        ImageButton button = getView().findViewById(R.id.play);
        if (mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            button.setImageResource(android.R.drawable.ic_media_play);
        }
        else {
            mediaPlayer.start();
            button.setImageResource(android.R.drawable.ic_media_pause);
        }
        update();
    }

    private void prepareFile(String uri) {
        file = new File(context.getCacheDir(), getSongId(uri));
        ParcelFileDescriptor fileDescriptor = null;
        try {
            fileDescriptor = context.getContentResolver()
                    .openFileDescriptor(Uri.parse(uri), "r");
        } catch (IOException e) {
            e.printStackTrace();
        }

        try {
            mediaPlayer.reset();
            mediaPlayer.setDataSource(fileDescriptor.getFileDescriptor());
            mediaPlayer.prepare();
            play();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void update() {
        SeekBar seekBar = getView().findViewById(R.id.seekBar);
        seekBar.setProgress(mediaPlayer.getCurrentPosition());
        TextView currentPosition = getView().findViewById(R.id.currentPosition);
        currentPosition.setText(getDuration(mediaPlayer.getCurrentPosition()));

        if (!mediaPlayer.isPlaying()) {
            ImageButton imageButton = getView().findViewById(R.id.play);
            imageButton.setImageResource(android.R.drawable.ic_media_play);
        }
        Runnable runnable = () -> update();
        handler.postDelayed(runnable, 10);
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

    private String getSongId(String uri) {
        String[] fileName = uri.split("/");
        return fileName[fileName.length - 1];
    }

    private final MediaBrowserCompat.ConnectionCallback connectionCallbacks =
            new MediaBrowserCompat.ConnectionCallback() {
                @Override
                public void onConnected() {

                    // Get the token for the MediaSession
                    MediaSessionCompat.Token token = mediaBrowser.getSessionToken();

                    // Create a MediaControllerCompat
                    MediaControllerCompat mediaController = null;
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
        ImageButton playPause = getActivity().findViewById(R.id.play);

        // Attach a listener to the button
        playPause.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Since this is a play/pause button, you'll need to test the current state
                // and choose the action accordingly

                int pbState = MediaControllerCompat.getMediaController(getActivity()).getPlaybackState().getState();
                if (pbState == PlaybackStateCompat.STATE_PLAYING) {
                    MediaControllerCompat.getMediaController(getActivity()).getTransportControls().pause();
                } else {
                    MediaControllerCompat.getMediaController(getActivity()).getTransportControls().play();
                }
            }
        });

        MediaControllerCompat mediaController = MediaControllerCompat.getMediaController(getActivity());

        // Display the initial state
        MediaMetadataCompat metadata = mediaController.getMetadata();
        PlaybackStateCompat pbState = mediaController.getPlaybackState();

        // Register a Callback to stay in sync
        mediaController.registerCallback(controllerCallback);
    }

    MediaControllerCompat.Callback controllerCallback =
            new MediaControllerCompat.Callback() {
                @Override
                public void onMetadataChanged(MediaMetadataCompat metadata) {}

                @Override
                public void onPlaybackStateChanged(PlaybackStateCompat state) {}
            };
}