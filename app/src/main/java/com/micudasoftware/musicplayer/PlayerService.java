
package com.micudasoftware.musicplayer;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.core.app.NotificationCompat;
import androidx.media.MediaBrowserServiceCompat;
import androidx.media.session.MediaButtonReceiver;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;


public class PlayerService extends MediaBrowserServiceCompat {

    private MediaSessionCompat mediaSession;
    private PlaybackStateCompat.Builder stateBuilder;
    private MusicProvider musicProvider;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        MediaButtonReceiver.handleIntent(mediaSession, intent);
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        musicProvider = MainActivity.musicProvider;
        mediaSession = new MediaSessionCompat(this, "tag");
        mediaSession.setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS |
                        MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS |
                        MediaSessionCompat.FLAG_HANDLES_QUEUE_COMMANDS);

        stateBuilder = new PlaybackStateCompat.Builder()
                .setActions(PlaybackStateCompat.ACTION_PLAY_PAUSE |
                        PlaybackStateCompat.ACTION_PLAY |
                        PlaybackStateCompat.ACTION_PAUSE |
                        PlaybackStateCompat.ACTION_STOP |
                        PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS |
                        PlaybackStateCompat.ACTION_SKIP_TO_NEXT |
                        PlaybackStateCompat.ACTION_REWIND |
                        PlaybackStateCompat.ACTION_FAST_FORWARD |
                        PlaybackStateCompat.ACTION_SEEK_TO |
                        PlaybackStateCompat.ACTION_SET_SHUFFLE_MODE |
                        PlaybackStateCompat.ACTION_SET_REPEAT_MODE);
        mediaSession.setPlaybackState(stateBuilder.build());

        mediaSession.setCallback(callback);

        setSessionToken(mediaSession.getSessionToken());
        createNotificationChannel();
    }

    @Nullable
    @Override
    public BrowserRoot onGetRoot(@NonNull String clientPackageName, int clientUid, @Nullable Bundle rootHints) {
        return new BrowserRoot(MusicProvider.CONTENT_TYPE_ROOT, null);
    }

    @Override
    public void onLoadChildren(@NonNull String parentId, @NonNull Result<List<MediaBrowserCompat.MediaItem>> result) {
        List<MediaBrowserCompat.MediaItem> mediaItems = new ArrayList<>();
        String parent = null;
        if (parentId.contains(":")) {
            String[] s = parentId.split(":");
            parent = s[0];
            parentId = s[1];
        }

        switch (parentId) {
            case MusicProvider.CONTENT_TYPE_ROOT:
                mediaItems.add(musicProvider.getMenuItem("All Songs", MusicProvider.CONTENT_TYPE_SONGS));
                mediaItems.add(musicProvider.getMenuItem("Artists", MusicProvider.CONTENT_TYPE_ARTISTS));
                mediaItems.add(musicProvider.getMenuItem("Albums", MusicProvider.CONTENT_TYPE_ALBUMS));
                mediaItems.add(musicProvider.getMenuItem("Genres", MusicProvider.CONTENT_TYPE_GENRES));
                mediaItems.add(musicProvider.getMenuItem("Folders", MusicProvider.CONTENT_TYPE_FOLDERS));
                break;
            case MusicProvider.CONTENT_TYPE_SONGS:
                mediaItems = musicProvider.getSongs(null, null);
                break;
            case MusicProvider.CONTENT_TYPE_ARTISTS:
                mediaItems = musicProvider.getArtists(null, null);
                break;
            case MusicProvider.CONTENT_TYPE_ALBUMS:
                mediaItems = musicProvider.getAlbums(null, null);
                break;
            case MusicProvider.CONTENT_TYPE_GENRES:
                mediaItems = musicProvider.getGenres(null, null);
                break;
            case MusicProvider.CONTENT_TYPE_FOLDERS:
                mediaItems = musicProvider.getFolders(MusicDatabase.COLUMN_PATH, new String[] {musicProvider.getDirectory()});
                break;
            default:
                switch (parent) {
                    case MusicProvider.CONTENT_TYPE_ARTISTS:
                        mediaItems = musicProvider.getAlbums(MusicDatabase.COLUMN_ARTIST_ID + " LIKE ?",
                                new String[] {parentId});
                        break;
                    case MusicProvider.CONTENT_TYPE_ALBUMS:
                    case MusicProvider.CONTENT_TYPE_ROOT + MusicProvider.CONTENT_TYPE_ALBUMS:
                        mediaItems = musicProvider.getSongs(MusicDatabase.COLUMN_ALBUM_ID + " LIKE ?",
                                new String[] {parentId});
                        break;
                    case MusicProvider.CONTENT_TYPE_GENRES:
                        mediaItems = musicProvider.getAlbums(MusicDatabase.COLUMN_GENRE_ID + " LIKE ?",
                                new String[] {parentId});
                        break;
                    case MusicProvider.CONTENT_TYPE_FOLDERS:
                        mediaItems = musicProvider.getFolders(MusicDatabase.COLUMN_PATH,
                                new String[] {parentId});
                        break;
                }
        }
        result.sendResult(mediaItems);
    }

    MediaSessionCompat.Callback callback = new MediaSessionCompat.Callback() {

        private final BecomingNoisyReceiver myNoisyAudioStreamReceiver = new BecomingNoisyReceiver();
        private AudioManager audioManager;
        private AudioFocusRequest audioFocusRequest;
        private final MediaPlayer player = new MediaPlayer();
        private ArrayList<MediaDescriptionCompat> queue = new ArrayList<>();
        private int queuePosition;
        private int shuffleMode = 0;
        private int repeatMode = 0;
        private ArrayList<Integer> shuffleList = new ArrayList<>();
        private MediaDescriptionCompat description;

        @Override
        public void onPlay() {
            if (requestAudioFocus() == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                registerReceiver(myNoisyAudioStreamReceiver,
                        new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY));
                player.start();
                stateBuilder.setState(
                        PlaybackStateCompat.STATE_PLAYING,
                        player.getCurrentPosition(),
                        1f);
                mediaSession.setPlaybackState(stateBuilder.build());
                buildNotification();
            }
        }

        @Override
        public void onCustomAction(String action, Bundle extras) {
            Log.v("debug", "customAction");
            if (requestAudioFocus() == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                startService(new Intent(getApplicationContext(), PlayerService.class));
                mediaSession.setActive(true);
                registerReceiver(myNoisyAudioStreamReceiver,
                        new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY));

                queuePosition = extras.getInt("position");
                if (queuePosition < 0)
                    queuePosition = new Random().nextInt(queue.size());

                player.reset();
                try {
                    ParcelFileDescriptor fileDescriptor = getApplicationContext().getContentResolver()
                            .openFileDescriptor(queue.get(queuePosition).getMediaUri(), "r");
                    player.setDataSource(fileDescriptor.getFileDescriptor());
                    player.prepare();
                    player.start();
                    player.setOnCompletionListener(mp -> {
                        onSkipToNext();
                    });
                } catch (IOException e) {
                    e.printStackTrace();
                }

                description = queue.get(queuePosition);
                mediaSession.setMetadata(new MediaMetadataCompat.Builder()
                        .putString(MediaMetadataCompat.METADATA_KEY_TITLE, description.getTitle().toString())
                        .putString(MediaMetadataCompat.METADATA_KEY_ALBUM_ARTIST, description.getSubtitle().toString())
                        .putLong(MediaMetadataCompat.METADATA_KEY_DURATION, player.getDuration())
                        .putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART, musicProvider.getImage(description.getIconUri()))
                        .build());

                stateBuilder.setState(
                        PlaybackStateCompat.STATE_PLAYING,
                        player.getCurrentPosition(),
                        1f);
                mediaSession.setPlaybackState(stateBuilder.build());
                buildNotification();
            }
        }

        private int requestAudioFocus() {
            audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build();

            audioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setOnAudioFocusChangeListener(afChangeListener)
                    .setAudioAttributes(attrs)
                    .build();
            return audioManager.requestAudioFocus(audioFocusRequest);
        }

        AudioManager.OnAudioFocusChangeListener afChangeListener = focusChange -> {
            if (focusChange == AudioManager.AUDIOFOCUS_GAIN)
                onPlay();
            else if (focusChange == AudioManager.AUDIOFOCUS_LOSS)
                onPause();
        };

        @Override
        public void onAddQueueItem(MediaDescriptionCompat description) {
            queue.add(description);
        }

        @Override
        public void onRemoveQueueItem(MediaDescriptionCompat description) {
            queue = new ArrayList<>();
        }

        @RequiresApi(api = Build.VERSION_CODES.O)
        @Override
        public void onStop() {
            audioManager.abandonAudioFocusRequest(audioFocusRequest);
//            unregisterReceiver(myNoisyAudioStreamReceiver);
            mediaSession.setActive(false);
            player.pause();
            stateBuilder.setState(
                    PlaybackStateCompat.STATE_STOPPED,
                    player.getCurrentPosition(),
                    1f);
            mediaSession.setPlaybackState(stateBuilder.build());
            stopForeground(false);
        }

        @Override
        public void onPause() {
            audioManager.abandonAudioFocusRequest(audioFocusRequest);
            player.pause();
            stateBuilder.setState(
                    PlaybackStateCompat.STATE_PAUSED,
                    player.getCurrentPosition(),
                    1f);
            mediaSession.setPlaybackState(stateBuilder.build());
//            unregisterReceiver(myNoisyAudioStreamReceiver);
            buildNotification();
            stopForeground(false);
        }

        @Override
        public void onRewind() {
            player.seekTo(player.getCurrentPosition() - 10000);
            stateBuilder.setState(
                    mediaSession.getController().getPlaybackState().getState(),
                    player.getCurrentPosition(),
                    1f);
            mediaSession.setPlaybackState(stateBuilder.build());
        }

        @Override
        public void onFastForward() {
            player.seekTo(player.getCurrentPosition() + 10000);
            stateBuilder.setState(
                    mediaSession.getController().getPlaybackState().getState(),
                    player.getCurrentPosition(),
                    1f);
            mediaSession.setPlaybackState(stateBuilder.build());
        }

        @Override
        public void onSkipToPrevious() {
            audioManager.abandonAudioFocusRequest(audioFocusRequest);
            if (shuffleMode == PlaybackStateCompat.SHUFFLE_MODE_NONE) {
                if (queuePosition > 0) {
                    Bundle extras = new Bundle();
                    extras.putInt("position", queuePosition - 1);
                    mediaSession.getController().getTransportControls().sendCustomAction("PlayFromQueue", extras);
                }
            } else if (shuffleMode == PlaybackStateCompat.SHUFFLE_MODE_ALL) {
                if (!shuffleList.contains(queuePosition))
                    shuffleList.add(queuePosition);
                if (shuffleList.indexOf(queuePosition) != 0) {
                    Bundle bundle = new Bundle();
                    bundle.putInt("position", shuffleList.get(shuffleList.indexOf(queuePosition) - 1));
                    mediaSession.getController().getTransportControls().sendCustomAction("PlayFromQueue", bundle);
                }
            }
        }

        @Override
        public void onSkipToNext() {
            audioManager.abandonAudioFocusRequest(audioFocusRequest);
            if (shuffleMode == PlaybackStateCompat.SHUFFLE_MODE_NONE) {
                if (repeatMode == PlaybackStateCompat.REPEAT_MODE_ONE) {
                    Bundle bundle = new Bundle();
                    bundle.putInt("position", queuePosition);
                    mediaSession.getController().getTransportControls().sendCustomAction("PlayFromQueue", bundle);
                } else if (queuePosition < queue.size() - 1) {
                    Bundle bundle = new Bundle();
                    bundle.putInt("position", queuePosition + 1);
                    mediaSession.getController().getTransportControls().sendCustomAction("PlayFromQueue", bundle);
                } else if (repeatMode == PlaybackStateCompat.REPEAT_MODE_ALL) {
                    Bundle bundle = new Bundle();
                    bundle.putInt("position", 0);
                    mediaSession.getController().getTransportControls().sendCustomAction("PlayFromQueue", bundle);
                } else
                    onStop();
            } else if (shuffleMode == PlaybackStateCompat.SHUFFLE_MODE_ALL) {
                if (repeatMode == PlaybackStateCompat.REPEAT_MODE_ONE) {
                    Bundle bundle = new Bundle();
                    bundle.putInt("position", queuePosition);
                    mediaSession.getController().getTransportControls().sendCustomAction("PlayFromQueue", bundle);
                }else if (!shuffleList.contains(queuePosition)) {
                    shuffleList.add(queuePosition);
                    randomSong();
                } else {
                    if (shuffleList.indexOf(queuePosition) != shuffleList.size() - 1) {
                        Bundle bundle = new Bundle();
                        bundle.putInt("position", shuffleList.get(shuffleList.indexOf(queuePosition) + 1));
                        mediaSession.getController().getTransportControls().sendCustomAction("PlayFromQueue", bundle);
                    } else
                        randomSong();
                }

            }
        }

        private void randomSong() {
            if (shuffleList.size() < queue.size()) {
                boolean notPlayed;
                int rand;
                do {
                    notPlayed = false;
                    rand = new Random().nextInt(queue.size());
                    for (int played : shuffleList) {
                        if (played == rand) {
                            notPlayed = true;
                            break;
                        }
                    }
                } while (notPlayed);
                Bundle bundle = new Bundle();
                bundle.putInt("position", rand);
                mediaSession.getController().getTransportControls().sendCustomAction("PlayFromQueue", bundle);
            } else if (repeatMode == PlaybackStateCompat.REPEAT_MODE_ALL) {
                shuffleList = new ArrayList<>();
                randomSong();
            } else
                onStop();
        }

        @Override
        public void onSeekTo(long pos) {
            player.seekTo((int) pos);
            stateBuilder.setState(
                    mediaSession.getController().getPlaybackState().getState(),
                    player.getCurrentPosition(),
                    1f);
            mediaSession.setPlaybackState(stateBuilder.build());
        }

        @Override
        public void onSetShuffleMode(int shuffleMode) {
            shuffleList = new ArrayList<>();
            if (shuffleMode == PlaybackStateCompat.SHUFFLE_MODE_INVALID) {
                if (this.shuffleMode == PlaybackStateCompat.SHUFFLE_MODE_NONE)
                    shuffleMode = PlaybackStateCompat.SHUFFLE_MODE_ALL;
                else if (this.shuffleMode == PlaybackStateCompat.SHUFFLE_MODE_ALL)
                    shuffleMode = PlaybackStateCompat.SHUFFLE_MODE_NONE;
            }

            if (this.shuffleMode != shuffleMode) {
                this.shuffleMode = shuffleMode;
                if (shuffleMode == PlaybackStateCompat.SHUFFLE_MODE_NONE)
                    Toast.makeText(getApplicationContext(), "ShuffleMode: Off", Toast.LENGTH_SHORT).show();
                else if (shuffleMode == PlaybackStateCompat.SHUFFLE_MODE_ALL)
                    Toast.makeText(getApplicationContext(), "ShuffleMode: On", Toast.LENGTH_SHORT).show();
            }
        }

        @Override
        public void onSetRepeatMode(int repeatMode) {
            if (this.repeatMode == PlaybackStateCompat.REPEAT_MODE_ALL) {
                this.repeatMode = PlaybackStateCompat.REPEAT_MODE_NONE;
                Toast.makeText(getApplicationContext(), "RepeatMode: Off", Toast.LENGTH_SHORT).show();
            } else if (this.repeatMode == PlaybackStateCompat.REPEAT_MODE_NONE) {
                this.repeatMode = PlaybackStateCompat.REPEAT_MODE_ONE;
                Toast.makeText(getApplicationContext(), "RepeatMode: One", Toast.LENGTH_SHORT).show();
            } else if (this.repeatMode == PlaybackStateCompat.REPEAT_MODE_ONE) {
                this.repeatMode = PlaybackStateCompat.REPEAT_MODE_ALL;
                Toast.makeText(getApplicationContext(), "RepeatMode: All", Toast.LENGTH_SHORT).show();
            }
        }

        private void buildNotification() {
            MediaControllerCompat controller = mediaSession.getController();

            int icon;
            String title;
            if (mediaSession.getController().getPlaybackState().getState() == PlaybackStateCompat.STATE_PLAYING) {
                icon = android.R.drawable.ic_media_pause;
                title = "pause";
            } else {
                icon = android.R.drawable.ic_media_play;
                title = "play";
            }

            NotificationCompat.Builder notificationBuilder = new NotificationCompat.Builder(PlayerService.this, "1")
                    .setContentTitle(description.getTitle())
                    .setContentText(description.getSubtitle())
                    .setLargeIcon(musicProvider.getImage(description.getIconUri()))
                    .setContentIntent(controller.getSessionActivity())
                    .setDeleteIntent(MediaButtonReceiver.buildMediaButtonPendingIntent(
                            PlayerService.this,
                            PlaybackStateCompat.ACTION_STOP))
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setSmallIcon(R.drawable.ic_baseline_volume_up_24)
                    .addAction(new NotificationCompat.Action(
                            android.R.drawable.ic_media_previous,
                            "previous",
                            MediaButtonReceiver.buildMediaButtonPendingIntent(PlayerService.this,
                                    PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)))
                    .addAction(new NotificationCompat.Action(
                            android.R.drawable.ic_media_rew,
                            "rewind",
                            MediaButtonReceiver.buildMediaButtonPendingIntent(PlayerService.this,
                                    PlaybackStateCompat.ACTION_REWIND)))
                    .addAction(new NotificationCompat.Action(
                            icon,
                            title,
                            MediaButtonReceiver.buildMediaButtonPendingIntent(PlayerService.this,
                                    PlaybackStateCompat.ACTION_PLAY_PAUSE)))
                    .addAction(new NotificationCompat.Action(
                            android.R.drawable.ic_media_ff,
                            "forward",
                            MediaButtonReceiver.buildMediaButtonPendingIntent(PlayerService.this,
                                    PlaybackStateCompat.ACTION_FAST_FORWARD)))
                    .addAction(new NotificationCompat.Action(
                            android.R.drawable.ic_media_next,
                            "next",
                            MediaButtonReceiver.buildMediaButtonPendingIntent(PlayerService.this,
                                    PlaybackStateCompat.ACTION_SKIP_TO_NEXT)))
                    .setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                            .setMediaSession(mediaSession.getSessionToken())
                            .setShowActionsInCompactView(0)
                            .setShowCancelButton(true)
                            .setCancelButtonIntent(MediaButtonReceiver.buildMediaButtonPendingIntent(
                                    PlayerService.this,
                                    PlaybackStateCompat.ACTION_STOP)));

            startForeground(1, notificationBuilder.build());
        }
    };

    private class BecomingNoisyReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction()))
                mediaSession.getController().getTransportControls().pause();
            else if (AudioManager.ACTION_HEADSET_PLUG.equals(intent.getAction()))
                mediaSession.getController().getTransportControls().play();
            Log.v("debug", intent.getAction());
        }
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager.class).createNotificationChannel(
                    new NotificationChannel("1",
                            "Music Player Notification",
                            NotificationManager.IMPORTANCE_DEFAULT));
        }
    }
}
