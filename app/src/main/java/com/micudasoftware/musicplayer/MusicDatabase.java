package com.micudasoftware.musicplayer;

import android.Manifest;
import android.app.Activity;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Process;
import android.provider.MediaStore;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import androidx.documentfile.provider.DocumentFile;
import java.util.HashMap;

public class MusicDatabase extends SQLiteOpenHelper {

     private static Activity context;
     private static SQLiteDatabase database;
     private static final String DATABASE_NAME = "Music.db";
     private static final int DATABASE_VERSION = 1;
     public static final String TABLE_NAME = "songs";
     public static final String COLUMN_ID = "_id";
     public static final String COLUMN_URI = "uri";
     public static final String COLUMN_PATH = "path";
     public static final String COLUMN_IMAGE_URI = "image_uri";
     public static final String COLUMN_TITLE = "title";
     public static final String COLUMN_ALBUM = "album";
     public static final String COLUMN_ALBUM_ID = "album_id";
     public static final String COLUMN_ARTIST = "artist";
     public static final String COLUMN_ARTIST_ID = "artist_id";
     public static final String COLUMN_GENRE = "genre";
     public static final String COLUMN_GENRE_ID = "genre_id";
     public static final String COLUMN_DURATION = "duration";

    public MusicDatabase(@Nullable Activity context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
        MusicDatabase.context = context;
        database = getWritableDatabase();
        onCreate(database);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        Cursor cursor = db.rawQuery(
                "SELECT DISTINCT tbl_name " +
                        " FROM sqlite_master " +
                        " WHERE tbl_name = '" + TABLE_NAME + "'",
                null);

        if (cursor.getCount() == 0 &&
                ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE)
                        == PackageManager.PERMISSION_GRANTED) {
            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
            context.startActivityForResult(intent, 1);
        }
        cursor.close();
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {

    }

    public static class UpdateDatabase extends Service {

        private ServiceHandler serviceHandler;
        private Uri directory;

        @Override
        public int onStartCommand(Intent intent, int flags, int startId) {
            directory =  intent.getData();
            Message msg = serviceHandler.obtainMessage();
            msg.arg1 = startId;
            serviceHandler.sendMessage(msg);
            return super.onStartCommand(intent, flags, startId);
        }

        private final class ServiceHandler extends Handler {
            public ServiceHandler(Looper looper) {
                super(looper);
            }

            HashMap<String, Integer> albumIds = new HashMap<>();
            HashMap<String, Integer> artistIds = new HashMap<>();
            HashMap<String, Integer> genreIds = new HashMap<>();

            @Override
            public void handleMessage(Message msg) {
                SQLiteDatabase db = database;
                db.execSQL("DROP TABLE IF EXISTS " + TABLE_NAME);
                db.execSQL("CREATE TABLE " + TABLE_NAME + " (" +
                        COLUMN_ID + " INTEGER PRIMARY KEY," +
                        COLUMN_URI + " TEXT, " +
                        COLUMN_PATH + " TEXT, " +
                        COLUMN_IMAGE_URI + " TEXT, " +
                        COLUMN_TITLE + " TEXT," +
                        COLUMN_ALBUM + " TEXT," +
                        COLUMN_ALBUM_ID + " INTEGER," +
                        COLUMN_ARTIST + " TEXT," +
                        COLUMN_ARTIST_ID + " INTEGER," +
                        COLUMN_GENRE + " TEXT," +
                        COLUMN_GENRE_ID + " INTEGER," +
                        COLUMN_DURATION + " INTEGER)");

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    getSystemService(NotificationManager.class).createNotificationChannel(
                            new NotificationChannel("2",
                                    "Searching Music Notification",
                                    NotificationManager.IMPORTANCE_DEFAULT));
                }

                findSongs(directory, database);
                stopSelf(msg.arg1);
            }

            private void findSongs(Uri uri, SQLiteDatabase database) {
                String selection;
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q)
                    selection = MediaStore.Audio.Media.RELATIVE_PATH;
                else
                    selection = MediaStore.Audio.Media.DATA;

                Cursor cursor = context.getContentResolver().query(
                            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                            null,
                            selection + " LIKE ?",
                            new String[] {"%" + uri.getLastPathSegment().split(":")[1]+ "%"},
                            null);


                while (cursor.moveToNext()) {
                    Notification notification =
                            new NotificationCompat.Builder(UpdateDatabase.this, "2")
                                    .setContentTitle("Finding songs...")
//                                    .setContentInfo(file.getName())
                                    .setContentText(cursor.getPosition() + " of " + cursor.getCount())
                                    .setSmallIcon(R.drawable.ic_launcher_foreground)
//                                     .setContentIntent()
                                    .setTicker("Finding songs... ")
                                    .setProgress(cursor.getCount(), cursor.getPosition(), false)
                                    .build();
                    startForeground(2, notification);

                    String path;
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q)
                        path = cursor.getString(cursor.getColumnIndexOrThrow(
                                        MediaStore.Audio.Media.RELATIVE_PATH)) +
                                cursor.getString(cursor.getColumnIndexOrThrow(
                                        MediaStore.Audio.Media.DISPLAY_NAME));
                    else
                        path = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA));

                    addSong(database,
                            ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                                    cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID))),
                            path,
                            cursor.getPosition());
                }
                cursor.close();
            }

            private void addSong(SQLiteDatabase database,Uri uri, String path, int id) {
                ContentValues contentValues = new ContentValues();

                MediaMetadataRetriever metadataRetriever = new MediaMetadataRetriever();
                try {
                    metadataRetriever.setDataSource(context, uri);
                } catch (RuntimeException e) {
                    Log.v("debug", uri + " " + e.getMessage());
                    return;
                }

                contentValues.put(COLUMN_ID, id);
                contentValues.put(COLUMN_URI, uri.toString());
                contentValues.put(COLUMN_PATH, path);
                contentValues.put(COLUMN_IMAGE_URI, getImageUri(metadataRetriever, uri, path));

                String title = metadataRetriever.extractMetadata(
                        MediaMetadataRetriever.METADATA_KEY_TITLE);
                title = (title == null) ? "Unknown" : title;
                contentValues.put(COLUMN_TITLE, title);

                String album = metadataRetriever.extractMetadata(
                        MediaMetadataRetriever.METADATA_KEY_ALBUM);
                album = (album == null) ? "Unknown" : album;
                contentValues.put(COLUMN_ALBUM, album);
                contentValues.put(COLUMN_ALBUM_ID, getIdOrAdd(COLUMN_ALBUM_ID, album));

                String artist = metadataRetriever.extractMetadata(
                        MediaMetadataRetriever.METADATA_KEY_ARTIST);
                artist = (artist == null) ? "Unknown" : artist;
                contentValues.put(COLUMN_ARTIST, artist);
                contentValues.put(COLUMN_ARTIST_ID, getIdOrAdd(COLUMN_ARTIST_ID, artist));

                String genre = metadataRetriever.extractMetadata(
                        MediaMetadataRetriever.METADATA_KEY_GENRE);
                genre = (genre == null) ? "Unknown" : genre;
                contentValues.put(COLUMN_GENRE, genre);
                contentValues.put(COLUMN_GENRE_ID, getIdOrAdd(COLUMN_GENRE_ID, genre));

                contentValues.put(COLUMN_DURATION,
                        metadataRetriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION));

                metadataRetriever.release();
                database.insert(TABLE_NAME, null, contentValues);
            }

            private String getImageUri(MediaMetadataRetriever metadataRetriever, Uri uri, String path) {
                if (metadataRetriever.getEmbeddedPicture() != null)
                    return uri.toString();

                String selection;
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q)
                    selection = MediaStore.Audio.Media.RELATIVE_PATH;
                else
                    selection = MediaStore.Audio.Media.DATA;
                Cursor cursor = context.getContentResolver().query(
                        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                        null,
                        selection + " LIKE ?",
                        new String[] {"%" + path.substring(0, path.lastIndexOf('/')) + "%"},
                        null);

                if (cursor.moveToFirst()) {
                    uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                            cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)));
                    cursor.close();
                    return uri.toString();
                }

                return null;
            }

            private int getIdOrAdd(String column, String name) {
                int id = -1;
                switch (column) {
                    case COLUMN_ALBUM_ID:
                        id = getId(albumIds, name);
                        break;
                    case COLUMN_ARTIST_ID:
                        id = getId(artistIds, name);
                        break;
                    case COLUMN_GENRE_ID:
                        id = getId(genreIds, name);
                        break;
                }

                if (id != -1)
                    return id;
                else {
                    switch (column) {
                        case COLUMN_ALBUM_ID:
                            id = albumIds.size();
                            albumIds.put(name, id);
                            return id;
                        case COLUMN_ARTIST_ID:
                            id = artistIds.size();
                            artistIds.put(name, artistIds.size());
                            return id;
                        case COLUMN_GENRE_ID:
                            id = genreIds.size();
                            genreIds.put(name, genreIds.size());
                            return id;
                    }
                }

                return -1;
            }

            private int getId(HashMap<String, Integer> map, String name) {
                if (map.containsKey(name))
                    return map.get(name);
                return -1;
            }
        }


        @Override
        public void onCreate() {
            HandlerThread thread = new HandlerThread("ServiceStartArguments1",
                    Process.THREAD_PRIORITY_BACKGROUND);
            thread.start();

            serviceHandler = new ServiceHandler(thread.getLooper());
        }

        @Nullable
        @Override
        public IBinder onBind(Intent intent) {
            return null;
        }
    }
}
