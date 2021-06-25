package com.micudasoftware.musicplayer;

import android.Manifest;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.RemoteException;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;
import android.view.MenuItem;
import android.view.WindowManager;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.documentfile.provider.DocumentFile;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    public static List<Fragment> library;
    public static Fragment player;
    public static Fragment search;
    public static FragmentManager fragmentManager;
    public static MediaBrowserCompat mediaBrowser;
    public static MusicProvider musicProvider;
    public static BottomNavigationView navigationView;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        getWindow().setFlags(
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);

        if (ContextCompat.checkSelfPermission(MainActivity.this, Manifest.permission.READ_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED)
            getPermission();

        musicProvider = new MusicProvider(this);

        mediaBrowser = new MediaBrowserCompat(this,
                new ComponentName(this, PlayerService.class),
                connectionCallbacks,
                null);
        mediaBrowser.connect();
        this.setVolumeControlStream(AudioManager.STREAM_MUSIC);


        navigationView = findViewById(R.id.navigationView);
        navigationView.setOnNavigationItemSelectedListener(navListener);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable @org.jetbrains.annotations.Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 1 && data != null) {
            Intent intent = new Intent(this, MusicDatabase.UpdateDatabase.class);
            intent.setData(data.getData());
            Toast.makeText(this, "Searching music...", Toast.LENGTH_SHORT).show();
            startService(intent);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (MediaControllerCompat.getMediaController(this) != null) {
            MediaControllerCompat.getMediaController(this).unregisterCallback(controllerCallback);
        }
        mediaBrowser.disconnect();
    }

    @Override
    public void onBackPressed() {
        if (navigationView.getSelectedItemId() == R.id.library && library.size() > 1) {
            library.remove(library.size() -1);
            library.get(library.size() -1).setArguments(new Bundle());
            fragmentManager.beginTransaction()
                    .replace(R.id.container, library.get(library.size() - 1))
                    .commit();
        } else
            super.onBackPressed();
    }

    public static BottomNavigationView.OnNavigationItemSelectedListener navListener =
            new BottomNavigationView.OnNavigationItemSelectedListener() {
                @Override
                public boolean onNavigationItemSelected(@NonNull MenuItem item) {

                    Fragment selectedFragment = null;
                    switch (item.getItemId()) {
                        case R.id.library:
                            selectedFragment = library.get(library.size() - 1);
                            break;
                        case R.id.player:
                            selectedFragment = player;
                            break;
                        case R.id.search:
                            selectedFragment = search;
                            break;
                    }

                    fragmentManager.beginTransaction()
                            .replace(R.id.container, selectedFragment)
                            .commit();
                    return true;
                }
    };

    public void getPermission() {
        ActivityCompat.requestPermissions(
                this,
                new String[] {Manifest.permission.READ_EXTERNAL_STORAGE},
                1);
    }

    private final MediaBrowserCompat.ConnectionCallback connectionCallbacks =
            new MediaBrowserCompat.ConnectionCallback() {
                @Override
                public void onConnected() {
                    fragmentManager = getSupportFragmentManager();
                    library = new ArrayList<>();
                    library.add(new LibraryFragment());
                    player = new PlayerFragment();
                    search = new SearchFragment();

                    fragmentManager.beginTransaction()
                            .replace(R.id.container, library.get(0))
                            .commit();

                    MediaSessionCompat.Token token = mediaBrowser.getSessionToken();
                    MediaControllerCompat mediaController =
                            null;
                    try {
                        mediaController = new MediaControllerCompat(MainActivity.this, token);
                    } catch (RemoteException e) {
                        e.printStackTrace();
                    }

                    MediaControllerCompat.setMediaController(MainActivity.this, mediaController);
                    mediaController.registerCallback(controllerCallback);
                }
            };

    MediaControllerCompat.Callback controllerCallback =
            new MediaControllerCompat.Callback() {
                @Override
                public void onMetadataChanged(MediaMetadataCompat metadata) {}

                @Override
                public void onPlaybackStateChanged(PlaybackStateCompat state) {}
            };
}