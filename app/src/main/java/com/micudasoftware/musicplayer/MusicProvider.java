package com.micudasoftware.musicplayer;

import android.app.Activity;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaMetadataRetriever;
import android.net.Uri;
import android.provider.MediaStore;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaDescriptionCompat;
import android.util.Log;
import android.widget.Toast;
import java.io.IOException;
import java.util.LinkedList;
import java.util.List;

public class MusicProvider {

    private final Activity activity;
    private SQLiteDatabase database;

    public static final String CONTENT_TYPE_ROOT = "root";
    public static final String CONTENT_TYPE_SONGS = "songs";
    public static final String CONTENT_TYPE_ALBUMS = "albums";
    public static final String CONTENT_TYPE_ARTISTS = "artists";
    public static final String CONTENT_TYPE_GENRES = "genres";
    public static final String CONTENT_TYPE_FOLDERS = "folders";

    public MusicProvider(Activity activity) {
        this.activity = activity;
        database = new MusicDatabase(this.activity).getReadableDatabase();
    }

    public MediaBrowserCompat.MediaItem getMenuItem(String title, String mediaId) {
        MediaDescriptionCompat description = new MediaDescriptionCompat.Builder()
                .setTitle(title)
                .setMediaId(mediaId)
                .build();
        return new MediaBrowserCompat.MediaItem(description, MediaBrowserCompat.MediaItem.FLAG_BROWSABLE);
    }

    public List<MediaBrowserCompat.MediaItem> getSongs(String selection, String[] selectionArgs) {
        String query = "SELECT " +
                MusicDatabase.COLUMN_TITLE + "," +
                MusicDatabase.COLUMN_ID + "," +
                MusicDatabase.COLUMN_URI + "," +
                MusicDatabase.COLUMN_IMAGE_URI + "," +
                MusicDatabase.COLUMN_ALBUM + "," +
                MusicDatabase.COLUMN_ARTIST +
                " FROM " + MusicDatabase.TABLE_NAME;
        if (selection != null)
            query = query +
                    " WHERE " + selection;
        query = query +
                " ORDER BY " + MusicDatabase.COLUMN_TITLE;

        Cursor cursor;
        try {
            cursor = database.rawQuery(query, selectionArgs);
        } catch (SQLiteException e) {
            Toast.makeText(activity, "Music database is empty!", Toast.LENGTH_SHORT).show();
            database = new MusicDatabase(activity).getReadableDatabase();
            return null;
        }

        String contentType;
        if (selection == null)
            contentType = CONTENT_TYPE_ROOT + CONTENT_TYPE_SONGS;
        else
            contentType = CONTENT_TYPE_SONGS;

        LinkedList<MediaBrowserCompat.MediaItem> mediaItems = new LinkedList<>();
        while (cursor.moveToNext()) {
            MediaDescriptionCompat.Builder descriptionBuilder = new MediaDescriptionCompat.Builder()
                    .setMediaId(contentType + ":" +
                            cursor.getString(cursor.getColumnIndexOrThrow(MusicDatabase.COLUMN_ID)))
                    .setMediaUri(Uri.parse(
                            cursor.getString(cursor.getColumnIndexOrThrow(MusicDatabase.COLUMN_URI))))
                    .setTitle(cursor.getString(cursor.getColumnIndexOrThrow(MusicDatabase.COLUMN_TITLE)))
                    .setSubtitle(
                            cursor.getString(
                                    cursor.getColumnIndexOrThrow(MusicDatabase.COLUMN_ARTIST)) +
                                    " - " +
                                    cursor.getString(
                                            cursor.getColumnIndexOrThrow(MusicDatabase.COLUMN_ALBUM)));

            String iconUri = cursor.getString(cursor.getColumnIndexOrThrow(MusicDatabase.COLUMN_IMAGE_URI));
            if (iconUri != null)
                descriptionBuilder.setIconUri(Uri.parse(iconUri));

            mediaItems.add(new MediaBrowserCompat.MediaItem(descriptionBuilder.build(),
                    MediaBrowserCompat.MediaItem.FLAG_PLAYABLE));
        }
        cursor.close();
        return mediaItems;
    }

    public List<MediaBrowserCompat.MediaItem> getAlbums(String selection, String[] selectionArgs) {
        String query = "SELECT " +
                MusicDatabase.COLUMN_ALBUM + "," +
                MusicDatabase.COLUMN_ALBUM_ID + "," +
                MusicDatabase.COLUMN_ARTIST +
                " FROM " + MusicDatabase.TABLE_NAME;
        if (selection != null)
            query = query +
                    " WHERE " + selection;
        query = query +
                " GROUP BY " + MusicDatabase.COLUMN_ALBUM_ID +
                " ORDER BY " + MusicDatabase.COLUMN_ALBUM;

        Cursor cursor;
        try {
            cursor = database.rawQuery(query, selectionArgs);
        } catch (SQLiteException e) {
            Toast.makeText(activity, "Music database is empty!", Toast.LENGTH_SHORT).show();
            database = new MusicDatabase(activity).getReadableDatabase();
            return null;
        }

        String contentType;
        if (selection == null)
            contentType = CONTENT_TYPE_ROOT + CONTENT_TYPE_ALBUMS;
        else
            contentType = CONTENT_TYPE_ALBUMS;

        LinkedList<MediaBrowserCompat.MediaItem> mediaItems = new LinkedList<>();
        while (cursor.moveToNext()) {
            MediaDescriptionCompat.Builder descriptionBuilder = new MediaDescriptionCompat.Builder()
                    .setMediaId(contentType + ":" +
                            cursor.getString(cursor.getColumnIndexOrThrow(MusicDatabase.COLUMN_ALBUM_ID)))
                    .setTitle(cursor.getString(cursor.getColumnIndexOrThrow(MusicDatabase.COLUMN_ALBUM)))
                    .setSubtitle(cursor.getString(cursor.getColumnIndexOrThrow(MusicDatabase.COLUMN_ARTIST)))
                    .setIconUri(getIconUri(
                            MusicDatabase.COLUMN_ALBUM_ID,
                            cursor.getString(cursor.getColumnIndexOrThrow(MusicDatabase.COLUMN_ALBUM_ID)),
                            MusicDatabase.COLUMN_ALBUM));

            mediaItems.add(new MediaBrowserCompat.MediaItem(descriptionBuilder.build(),
                    MediaBrowserCompat.MediaItem.FLAG_BROWSABLE));
        }
        cursor.close();
        return mediaItems;
    }

    public List<MediaBrowserCompat.MediaItem> getArtists(String selection, String[] selectionArgs) {
        String query = "SELECT " +
                MusicDatabase.COLUMN_ARTIST + "," +
                MusicDatabase.COLUMN_ARTIST_ID +
                " FROM " + MusicDatabase.TABLE_NAME;
        if (selection != null)
            query = query +
                    " WHERE " + selection;
        query = query +
                " GROUP BY " + MusicDatabase.COLUMN_ARTIST_ID +
                " ORDER BY " + MusicDatabase.COLUMN_ARTIST;

        Cursor cursor;
        try {
            cursor = database.rawQuery(query, selectionArgs);
        } catch (SQLiteException e) {
            Toast.makeText(activity, "Music database is empty!", Toast.LENGTH_SHORT).show();
            database = new MusicDatabase(activity).getReadableDatabase();
            return null;
        }

        LinkedList<MediaBrowserCompat.MediaItem> mediaItems = new LinkedList<>();
        while (cursor.moveToNext()) {
            MediaDescriptionCompat.Builder descriptionBuilder = new MediaDescriptionCompat.Builder()
                    .setMediaId(CONTENT_TYPE_ARTISTS + ":" +
                            cursor.getString(cursor.getColumnIndexOrThrow(MusicDatabase.COLUMN_ARTIST_ID)))
                    .setTitle(cursor.getString(cursor.getColumnIndexOrThrow(MusicDatabase.COLUMN_ARTIST)))
                    .setIconUri(getIconUri(
                            MusicDatabase.COLUMN_ARTIST_ID,
                            cursor.getString(cursor.getColumnIndexOrThrow(MusicDatabase.COLUMN_ARTIST_ID)),
                            MusicDatabase.COLUMN_ARTIST));

            mediaItems.add(new MediaBrowserCompat.MediaItem(descriptionBuilder.build(),
                    MediaBrowserCompat.MediaItem.FLAG_BROWSABLE));
        }
        cursor.close();
        return mediaItems;
    }

    public List<MediaBrowserCompat.MediaItem> getGenres(String selection, String[] selectionArgs) {
        String query = "SELECT " +
                MusicDatabase.COLUMN_GENRE + "," +
                MusicDatabase.COLUMN_GENRE_ID +
                " FROM " + MusicDatabase.TABLE_NAME;
        if (selection != null)
            query = query +
                    " WHERE " + selection;
        query = query +
                " GROUP BY " + MusicDatabase.COLUMN_GENRE_ID +
                " ORDER BY " + MusicDatabase.COLUMN_GENRE;

        Cursor cursor;
        try {
            cursor = database.rawQuery(query, selectionArgs);
        } catch (SQLiteException e) {
            Toast.makeText(activity, "Music database is empty!", Toast.LENGTH_SHORT).show();
            database = new MusicDatabase(activity).getReadableDatabase();
            return null;
        }

        LinkedList<MediaBrowserCompat.MediaItem> mediaItems = new LinkedList<>();
        while (cursor.moveToNext()) {
            MediaDescriptionCompat.Builder descriptionBuilder = new MediaDescriptionCompat.Builder()
                    .setMediaId(CONTENT_TYPE_GENRES + ":" +
                            cursor.getString(cursor.getColumnIndexOrThrow(MusicDatabase.COLUMN_GENRE_ID)))
                    .setTitle(cursor.getString(cursor.getColumnIndexOrThrow(MusicDatabase.COLUMN_GENRE)))
                    .setIconUri(getIconUri(
                            MusicDatabase.COLUMN_GENRE_ID,
                            cursor.getString(cursor.getColumnIndexOrThrow(MusicDatabase.COLUMN_GENRE_ID)),
                            MusicDatabase.COLUMN_GENRE));

            mediaItems.add(new MediaBrowserCompat.MediaItem(descriptionBuilder.build(),
                    MediaBrowserCompat.MediaItem.FLAG_BROWSABLE));
        }
        cursor.close();
        return mediaItems;
    }

    public List<MediaBrowserCompat.MediaItem> getFolders(String selection, String[] selectionArgs) {
        String query = "SELECT " +
                MusicDatabase.COLUMN_PATH + "," +
                MusicDatabase.COLUMN_URI + "," +
                "SUBSTR(" + MusicDatabase.COLUMN_PATH +
                ",INSTR(" + MusicDatabase.COLUMN_PATH + ",\"" + selectionArgs[0] + "\") + " +
                (selectionArgs[0].length() + 1) + ") Name," +

                "SUBSTR(" + MusicDatabase.COLUMN_PATH +
                ",INSTR(" + MusicDatabase.COLUMN_PATH + ",\"" + selectionArgs[0] + "\") + " +
                (selectionArgs[0].length() + 1) +
                ",-(INSTR(" + MusicDatabase.COLUMN_PATH + ",\"" + selectionArgs[0] + "\") + " +
                (selectionArgs[0].length() + 1) + ")) Directory," +

                "SUBSTR(SUBSTR(" + MusicDatabase.COLUMN_PATH +
                ",INSTR(" + MusicDatabase.COLUMN_PATH + ",\"" + selectionArgs[0] + "\") + " +
                (selectionArgs[0].length() + 1) + ")" +
                ",INSTR(SUBSTR(" + MusicDatabase.COLUMN_PATH +
                ",INSTR(" + MusicDatabase.COLUMN_PATH + ",\"" + selectionArgs[0] + "\") + " +
                (selectionArgs[0].length() + 1) + "), '/')" +
                ",-(INSTR(SUBSTR(" + MusicDatabase.COLUMN_PATH +
                ",INSTR(" + MusicDatabase.COLUMN_PATH + ",\"" + selectionArgs[0] + "\") + " +
                (selectionArgs[0].length() + 1) + "), '/')) ) File" +
                " FROM " + MusicDatabase.TABLE_NAME +
                " WHERE " + selection + " LIKE \"%" + selectionArgs[0] + "%\"" +
                " GROUP BY (CASE WHEN Name LIKE '%/%' THEN File ELSE Name END)" +
                " ORDER BY File";

        Cursor cursor;
        try {
            cursor = database.rawQuery(query, null);
        } catch (SQLiteException e) {
            Toast.makeText(activity, "Music database is empty!", Toast.LENGTH_SHORT).show();
            database = new MusicDatabase(activity).getReadableDatabase();
            return null;
        }

        LinkedList<MediaBrowserCompat.MediaItem> mediaItems = new LinkedList<>();
        while (cursor.moveToNext()) {
            int flag = MediaBrowserCompat.MediaItem.FLAG_BROWSABLE;
            Uri uri = null;
            String name = cursor.getString(cursor.getColumnIndexOrThrow("Name"));
            if (name.contains("/"))
                name = cursor.getString(cursor.getColumnIndexOrThrow("File"));
            else {
                flag = MediaBrowserCompat.MediaItem.FLAG_PLAYABLE;
                uri = Uri.parse(cursor.getString(cursor.getColumnIndexOrThrow(MusicDatabase.COLUMN_URI)));
            }

            MediaDescriptionCompat.Builder descriptionBuilder = new MediaDescriptionCompat.Builder()
                    .setMediaId(CONTENT_TYPE_FOLDERS + ":" +
                            cursor.getString(cursor.getColumnIndexOrThrow("Directory")) +
                            name)
                    .setTitle(name)
                    .setMediaUri(uri)
                    .setIconUri(getIconUri(MusicDatabase.COLUMN_PATH, "\"%" + name + "%\"", MusicDatabase.COLUMN_PATH));

            mediaItems.add(new MediaBrowserCompat.MediaItem(descriptionBuilder.build(), flag));
        }
        cursor.close();
        return mediaItems;
    }

    private Uri getIconUri(String selection, String selectionArgs, String orderBy) {
        Cursor cursor = database.rawQuery(
                "SELECT " + MusicDatabase.COLUMN_ID + "," +
                        orderBy + "," +
                        selection + "," +
                        MusicDatabase.COLUMN_IMAGE_URI +
                        " FROM " + MusicDatabase.TABLE_NAME +
                        " WHERE " + selection + " LIKE " + selectionArgs +
                        " ORDER BY " + orderBy,
                null);

        while (cursor.moveToNext()) {
            String uri = cursor.getString(cursor.getColumnIndexOrThrow(MusicDatabase.COLUMN_IMAGE_URI));
            if (uri != null) {
                Uri imageUri = Uri.parse(uri);
                cursor.close();
                return imageUri;
            }
        }
        cursor.close();
        return null;
    }

    public Bitmap getImage(Uri uri) {
        if (uri != null) {
            if (uri.toString().contains("audio")) {
                MediaMetadataRetriever metadataRetriever = new MediaMetadataRetriever();
                metadataRetriever.setDataSource(activity, uri);
                byte[] image = metadataRetriever.getEmbeddedPicture();
                return BitmapFactory.decodeByteArray(image, 0, image.length);
            } else {
                try {
                    return MediaStore.Images.Media.getBitmap(activity.getContentResolver(), uri);
                } catch (IOException ignored) {
                }
            }
        }

        return null;
    }
}