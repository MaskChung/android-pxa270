/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package android.provider;

import android.annotation.SdkConstant;
import android.annotation.SdkConstant.SdkConstantType;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.ContentUris;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.os.Environment;
import android.util.Log;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.text.Collator;

/**
 * The Media provider contains meta data for all available media on both internal
 * and external storage devices.
 */
public final class MediaStore
{
    private final static String TAG = "MediaStore";
    
    public static final String AUTHORITY = "media";
    
    private static final String CONTENT_AUTHORITY_SLASH = "content://" + AUTHORITY + "/";
    
    /**
     * Standard Intent action that can be sent to have the media application
     * capture an image and return it.  The image is returned as a Bitmap
     * object in the extra field.
     * @hide
     */
    public final static String ACTION_IMAGE_CAPTURE = "android.media.action.IMAGE_CAPTURE";
    
    /** 
     * Common fields for most MediaProvider tables
     */
     
     public interface MediaColumns extends BaseColumns {
        /**
         * The data stream for the file
         * <P>Type: DATA STREAM</P>
         */
        public static final String DATA = "_data";

        /**
         * The size of the file in bytes
         * <P>Type: INTEGER (long)</P>
         */
        public static final String SIZE = "_size";

        /**
         * The display name of the file
         * <P>Type: TEXT</P>
         */
        public static final String DISPLAY_NAME = "_display_name";

        /**
         * The title of the content
         * <P>Type: TEXT</P>
         */
        public static final String TITLE = "title";

        /**
         * The time the file was added to the media provider
         * Units are seconds since 1970.
         * <P>Type: INTEGER (long)</P>
         */
        public static final String DATE_ADDED = "date_added";

        /**
         * The time the file was last modified
         * Units are seconds since 1970.
         * NOTE: This is for internal use by the media scanner.  Do not modify this field.
         * <P>Type: INTEGER (long)</P>
         */
        public static final String DATE_MODIFIED = "date_modified";

        /**
         * The MIME type of the file
         * <P>Type: TEXT</P>
         */
        public static final String MIME_TYPE = "mime_type";
     }
    
    /**
     * Contains meta data for all available images.
     */
    public static final class Images
    {
        public interface ImageColumns extends MediaColumns {
            /**
             * The description of the image
             * <P>Type: TEXT</P>
             */
            public static final String DESCRIPTION = "description";
    
            /**
             * The picasa id of the image
             * <P>Type: TEXT</P>
             */
            public static final String PICASA_ID = "picasa_id";
            
            /**
             * Whether the video should be published as public or private
             * <P>Type: INTEGER</P>
             */
            public static final String IS_PRIVATE = "isprivate";
            
            /**
             * The latitude where the image was captured.
             * <P>Type: DOUBLE</P>
             */
            public static final String LATITUDE = "latitude";

            /**
             * The longitude where the image was captured.
             * <P>Type: DOUBLE</P>
             */
            public static final String LONGITUDE = "longitude";
            
            /**
             * The date & time that the image was taken in units
             * of milliseconds since jan 1, 1970.
             * <P>Type: INTEGER</P>
             */
            public static final String DATE_TAKEN = "datetaken";
            
            /**
             * The orientation for the image expressed as degrees.
             * Only degrees 0, 90, 180, 270 will work.
             * <P>Type: INTEGER</P>
             */
            public static final String ORIENTATION = "orientation";

            /**
             * The mini thumb id.
             * <P>Type: INTEGER</P>
             */
            public static final String MINI_THUMB_MAGIC = "mini_thumb_magic";
            
            /**
             * The bucket id of the image
             * <P>Type: TEXT</P>
             */
            public static final String BUCKET_ID = "bucket_id";
            
            /**
             * The bucket display name of the image
             * <P>Type: TEXT</P>
             */
            public static final String BUCKET_DISPLAY_NAME = "bucket_display_name";
        }

        public static final class Media implements ImageColumns {
            public static final Cursor query(ContentResolver cr, Uri uri, String[] projection)
            {
                return cr.query(uri, projection, null, null, DEFAULT_SORT_ORDER);
            }
    
            public static final Cursor query(ContentResolver cr, Uri uri, String[] projection,
                                           String where, String orderBy)
            {
                return cr.query(uri, projection, where,
                                             null, orderBy == null ? DEFAULT_SORT_ORDER : orderBy);
            }
            
            public static final Cursor query(ContentResolver cr, Uri uri, String[] projection,
                    String selection, String [] selectionArgs, String orderBy)
            {
                return cr.query(uri, projection, selection,
                        selectionArgs, orderBy == null ? DEFAULT_SORT_ORDER : orderBy);
            }

            /**
             * Retrieves an image for the given url as a {@link Bitmap}.
             * 
             * @param cr The content resolver to use
             * @param url The url of the image
             * @throws FileNotFoundException
             * @throws IOException
             */
            public static final Bitmap getBitmap(ContentResolver cr, Uri url)
                    throws FileNotFoundException, IOException
            {
                InputStream input = cr.openInputStream(url);
                Bitmap bitmap = BitmapFactory.decodeStream(input);
                input.close();
                return bitmap;
            }
            
            /**
             * Insert an image and create a thumbnail for it.
             * 
             * @param cr The content resolver to use
             * @param imagePath The path to the image to insert
             * @param name The name of the image
             * @param description The description of the image
             * @return The URL to the newly created image
             * @throws FileNotFoundException
             */
            public static final String insertImage(ContentResolver cr, String imagePath, String name,
                                                   String description) throws FileNotFoundException
            {
                // Check if file exists with a FileInputStream
                FileInputStream stream = new FileInputStream(imagePath);
                try {
                    return insertImage(cr, BitmapFactory.decodeFile(imagePath), name, description);
                } finally {
                    try {
                        stream.close();
                    } catch (IOException e) {
                    }
                }
            }
            
            private static final Bitmap StoreThumbnail(
                    ContentResolver cr, 
                    Bitmap source,
                    long id,
                    float width, float height, 
                    int kind) {
                // create the matrix to scale it
                Matrix matrix = new Matrix();
                
                float scaleX = width / source.getWidth();
                float scaleY = height / source.getHeight();
                
                matrix.setScale(scaleX, scaleY);
                
                Bitmap thumb = Bitmap.createBitmap(source, 0, 0, 
                                                   source.getWidth(),
                                                   source.getHeight(), matrix,
                                                   true);
                
                ContentValues values = new ContentValues(4);
                values.put(Images.Thumbnails.KIND,     kind);
                values.put(Images.Thumbnails.IMAGE_ID, (int)id);
                values.put(Images.Thumbnails.HEIGHT,   thumb.getHeight());
                values.put(Images.Thumbnails.WIDTH,    thumb.getWidth());
                
                Uri url = cr.insert(Images.Thumbnails.EXTERNAL_CONTENT_URI, values);
                
                try {
                    OutputStream thumbOut = cr.openOutputStream(url);
                
                    thumb.compress(Bitmap.CompressFormat.JPEG, 100, thumbOut);   
                    thumbOut.close();
                    return thumb;
                }
                catch (FileNotFoundException ex) {
                    return null;
                }
                catch (IOException ex) {
                    return null;
                }
            }
            
            /**
             * Insert an image and create a thumbnail for it.
             * 
             * @param cr The content resolver to use
             * @param source The stream to use for the image
             * @param title The name of the image
             * @param description The description of the image
             * @return The URL to the newly created image, or <code>null</code> if the image failed to be stored
             *              for any reason.
             */
            public static final String insertImage(ContentResolver cr, Bitmap source,
                                                   String title, String description)
            {
                ContentValues values = new ContentValues();
                values.put(Images.Media.TITLE, title);
                values.put(Images.Media.DESCRIPTION, description);
                values.put(Images.Media.MIME_TYPE, "image/jpeg");

                Uri url = null;
                String stringUrl = null;    /* value to be returned */

                try
                {
                    url = cr.insert(EXTERNAL_CONTENT_URI, values);
             
                    if (source != null) {
                        OutputStream imageOut = cr.openOutputStream(url);
                        try {
                            source.compress(Bitmap.CompressFormat.JPEG, 50, imageOut);
                        } finally {
                            imageOut.close();
                        }

                        long id = ContentUris.parseId(url);
                        Bitmap miniThumb  = StoreThumbnail(cr, source, id, 320F, 240F, Images.Thumbnails.MINI_KIND);
                        Bitmap microThumb = StoreThumbnail(cr, miniThumb, id, 50F, 50F, Images.Thumbnails.MICRO_KIND);
                    } else {
                        Log.e(TAG, "Failed to create thumbnail, removing original");
                        cr.delete(url, null, null);
                        url = null;
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Failed to insert image", e);
                    if (url != null) {
                        cr.delete(url, null, null);
                        url = null;
                    }
                }

                if (url != null) {
                    stringUrl = url.toString();
                }

                return stringUrl;
            }
    
            /**
             * Get the content:// style URI for the image media table on the 
             * given volume.
             * 
             * @param volumeName the name of the volume to get the URI for
             * @return the URI to the image media table on the given volume
             */
            public static Uri getContentUri(String volumeName) {
                return Uri.parse(CONTENT_AUTHORITY_SLASH + volumeName +
                        "/images/media");
            }

            /**
             * The content:// style URI for the internal storage.
             */
            public static final Uri INTERNAL_CONTENT_URI =
                    getContentUri("internal");
            
            /**
             * The content:// style URI for the "primary" external storage
             * volume.
             */
            public static final Uri EXTERNAL_CONTENT_URI =
                    getContentUri("external");

            /**
             * The MIME type of of this directory of
             * images.  Note that each entry in this directory will have a standard
             * image MIME type as appropriate -- for example, image/jpeg.
             */
            public static final String CONTENT_TYPE = "vnd.android.cursor.dir/image";
    
            /**
             * The default sort order for this table
             */
            public static final String DEFAULT_SORT_ORDER = "name ASC";
       }

        public static class Thumbnails implements BaseColumns
        {
            public static final Cursor query(ContentResolver cr, Uri uri, String[] projection)
            {
                return cr.query(uri, projection, null, null, DEFAULT_SORT_ORDER);
            }
    
            public static final Cursor queryMiniThumbnails(ContentResolver cr, Uri uri, int kind, String[] projection)
            {
                return cr.query(uri, projection, "kind = " + kind, null, DEFAULT_SORT_ORDER);
            }
    
            public static final Cursor queryMiniThumbnail(ContentResolver cr, long origId, int kind, String[] projection)
            {
                return cr.query(EXTERNAL_CONTENT_URI, projection,
                        IMAGE_ID + " = " + origId + " AND " + KIND + " = " +
                        kind, null, null);
            }
    
            /**
             * Get the content:// style URI for the image media table on the 
             * given volume.
             * 
             * @param volumeName the name of the volume to get the URI for
             * @return the URI to the image media table on the given volume
             */
            public static Uri getContentUri(String volumeName) {
                return Uri.parse(CONTENT_AUTHORITY_SLASH + volumeName +
                        "/images/thumbnails");
            }
    
            /**
             * The content:// style URI for the internal storage.
             */
            public static final Uri INTERNAL_CONTENT_URI =
                    getContentUri("internal");
            
            /**
             * The content:// style URI for the "primary" external storage
             * volume.
             */
            public static final Uri EXTERNAL_CONTENT_URI =
                    getContentUri("external");

            /**
             * The default sort order for this table
             */
            public static final String DEFAULT_SORT_ORDER = "image_id ASC";
    
            /**
             * The data stream for the thumbnail
             * <P>Type: DATA STREAM</P>
             */
            public static final String DATA = "_data";
    
            /**
             * The original image for the thumbnal
             * <P>Type: INTEGER (ID from Images table)</P>
             */
            public static final String IMAGE_ID = "image_id";
    
            /**
             * The kind of the thumbnail
             * <P>Type: INTEGER (One of the values below)</P>
             */
            public static final String KIND = "kind";
    
            public static final int MINI_KIND = 1;
            public static final int FULL_SCREEN_KIND = 2;
            public static final int MICRO_KIND = 3;
    
            /**
             * The width of the thumbnal
             * <P>Type: INTEGER (long)</P>
             */
            public static final String WIDTH = "width";
    
            /**
             * The height of the thumbnail
             * <P>Type: INTEGER (long)</P>
             */
            public static final String HEIGHT = "height";
        }
    }
    
    /**
     * Container for all audio content.
     */
    public static final class Audio {
        /**
         * Columns for audio file that show up in multiple tables.
         */
        public interface AudioColumns extends MediaColumns {

            /**
             * A non human readable key calculated from the TITLE, used for
             * searching, sorting and grouping
             * <P>Type: TEXT</P>
             */
            public static final String TITLE_KEY = "title_key";

            /**
             * The duration of the audio file, in ms
             * <P>Type: INTEGER (long)</P>
             */
            public static final String DURATION = "duration";

            /**
             * The id of the artist who created the audio file, if any
             * <P>Type: INTEGER (long)</P>
             */
            public static final String ARTIST_ID = "artist_id";

            /**
             * The artist who created the audio file, if any
             * <P>Type: TEXT</P>
             */
            public static final String ARTIST = "artist";

            /**
             * A non human readable key calculated from the ARTIST, used for
             * searching, sorting and grouping
             * <P>Type: TEXT</P>
             */
            public static final String ARTIST_KEY = "artist_key";

            /**
             * The composer of the audio file, if any
             * <P>Type: TEXT</P>
             */
            public static final String COMPOSER = "composer";

            /**
             * The id of the album the audio file is from, if any
             * <P>Type: INTEGER (long)</P>
             */
            public static final String ALBUM_ID = "album_id";

            /**
             * The album the audio file is from, if any
             * <P>Type: TEXT</P>
             */
            public static final String ALBUM = "album";

            /**
             * A non human readable key calculated from the ALBUM, used for
             * searching, sorting and grouping
             * <P>Type: TEXT</P>
             */
            public static final String ALBUM_KEY = "album_key";

            /**
             * A URI to the album art, if any
             * <P>Type: TEXT</P>
             */
            public static final String ALBUM_ART = "album_art";
            
            /**
             * The track number of this song on the album, if any.
             * This number encodes both the track number and the
             * disc number. For multi-disc sets, this number will
             * be 1xxx for tracks on the first disc, 2xxx for tracks
             * on the second disc, etc.
             * <P>Type: INTEGER</P>
             */
            public static final String TRACK = "track";

            /**
             * The year the audio file was recorded, if any
             * <P>Type: INTEGER</P>
             */
            public static final String YEAR = "year";

            /**
             * Non-zero if the audio file is music
             * <P>Type: INTEGER (boolean)</P>
             */
            public static final String IS_MUSIC = "is_music";

            /**
             * Non-zero id the audio file may be a ringtone
             * <P>Type: INTEGER (boolean)</P>
             */
            public static final String IS_RINGTONE = "is_ringtone";

            /**
             * Non-zero id the audio file may be an alarm
             * <P>Type: INTEGER (boolean)</P>
             */
            public static final String IS_ALARM = "is_alarm";

            /**
             * Non-zero id the audio file may be a notification sound
             * <P>Type: INTEGER (boolean)</P>
             */
            public static final String IS_NOTIFICATION = "is_notification";
        }

        /**
         * Converts a name to a "key" that can be used for grouping, sorting
         * and searching.
         * The rules that govern this conversion are:
         * - remove 'special' characters like ()[]'!?.,
         * - remove leading/trailing spaces
         * - convert everything to lowercase
         * - remove leading "the ", "an " and "a "
         * - remove trailing ", the|an|a"
         * - remove accents. This step leaves us with CollationKey data,
         *   which is not human readable
         *
         * @param name The artist or album name to convert
         * @return The "key" for the given name.
         */
        public static String keyFor(String name) {
            if (name != null)  {
                if (name.equals(android.media.MediaFile.UNKNOWN_STRING)) {
                    return "\001";
                }
                name = name.trim().toLowerCase();
                if (name.startsWith("the ")) {
                    name = name.substring(4);
                }
                if (name.startsWith("an ")) {
                    name = name.substring(3);
                }
                if (name.startsWith("a ")) {
                    name = name.substring(2);
                }
                if (name.endsWith(", the") || name.endsWith(",the") ||
                    name.endsWith(", an") || name.endsWith(",an") ||
                    name.endsWith(", a") || name.endsWith(",a")) {
                    name = name.substring(0, name.lastIndexOf(','));
                }
                name = name.replaceAll("[\\[\\]\\(\\)'.,?!]", "").trim();
                if (name.length() > 0) {
                    // Insert a separator between the characters to avoid
                    // matches on a partial character. If we ever change
                    // to start-of-word-only matches, this can be removed.
                    StringBuilder b = new StringBuilder();
                    b.append('.');
                    int nl = name.length();
                    for (int i = 0; i < nl; i++) {
                        b.append(name.charAt(i));
                        b.append('.');
                    }
                    name = b.toString();
                    return DatabaseUtils.getCollationKey(name);
               } else {
                    return "";
                }
            }
            return null;
        }

        public static final class Media implements AudioColumns {
            /**
             * Get the content:// style URI for the audio media table on the 
             * given volume.
             * 
             * @param volumeName the name of the volume to get the URI for
             * @return the URI to the audio media table on the given volume
             */
            public static Uri getContentUri(String volumeName) {
                return Uri.parse(CONTENT_AUTHORITY_SLASH + volumeName +
                        "/audio/media");
            }
            
            public static Uri getContentUriForPath(String path) {
                return (path.startsWith(Environment.getExternalStorageDirectory().getPath()) ?
                        EXTERNAL_CONTENT_URI : INTERNAL_CONTENT_URI);
            }
            
            /**
             * The content:// style URI for the internal storage.
             */
            public static final Uri INTERNAL_CONTENT_URI =
                    getContentUri("internal");
            
            /**
             * The content:// style URI for the "primary" external storage
             * volume.
             */
            public static final Uri EXTERNAL_CONTENT_URI =
                    getContentUri("external");
            
            /**
             * The MIME type for this table.
             */
            public static final String CONTENT_TYPE = "vnd.android.cursor.dir/audio";
            
            /**
             * The default sort order for this table
             */
            public static final String DEFAULT_SORT_ORDER = TITLE;
            
            /**
             * Activity Action: Start SoundRecorder application.
             * <p>Input: nothing.
             * <p>Output: An uri to the recorded sound stored in the Media Library
             * if the recording was successful.
             * 
             */
            public static final String RECORD_SOUND_ACTION = 
                    "android.provider.MediaStore.RECORD_SOUND";
        }
    
        /**
         * Columns representing an audio genre
         */
        public interface GenresColumns {
            /**
             * The name of the genre
             * <P>Type: TEXT</P>
             */
            public static final String NAME = "name";
        }

        /**
         * Contains all genres for audio files
         */
        public static final class Genres implements BaseColumns, GenresColumns {
            /**
             * Get the content:// style URI for the audio genres table on the 
             * given volume.
             * 
             * @param volumeName the name of the volume to get the URI for
             * @return the URI to the audio genres table on the given volume
             */
            public static Uri getContentUri(String volumeName) {
                return Uri.parse(CONTENT_AUTHORITY_SLASH + volumeName +
                        "/audio/genres");
            }

            /**
             * The content:// style URI for the internal storage.
             */
            public static final Uri INTERNAL_CONTENT_URI =
                    getContentUri("internal");
            
            /**
             * The content:// style URI for the "primary" external storage
             * volume.
             */
            public static final Uri EXTERNAL_CONTENT_URI =
                    getContentUri("external");

            /**
             * The MIME type for this table.
             */
            public static final String CONTENT_TYPE = "vnd.android.cursor.dir/genre";
            
            /**
             * The MIME type for entries in this table.
             */
            public static final String ENTRY_CONTENT_TYPE = "vnd.android.cursor.item/genre";

            /**
             * The default sort order for this table
             */
            public static final String DEFAULT_SORT_ORDER = NAME;

            /**
             * Sub-directory of each genre containing all members.
             */
            public static final class Members implements AudioColumns {

                public static final Uri getContentUri(String volumeName,
                        long genreId) {
                    return Uri.parse(CONTENT_AUTHORITY_SLASH + volumeName
                            + "/audio/genres/" + genreId + "/members");
                }

                /**
                 * A subdirectory of each genre containing all member audio files.
                 */
                public static final String CONTENT_DIRECTORY = "members";

                /**
                 * The default sort order for this table
                 */
                public static final String DEFAULT_SORT_ORDER = TITLE;

                /**
                 * The ID of the audio file
                 * <P>Type: INTEGER (long)</P>
                 */
                public static final String AUDIO_ID = "audio_id";

                /**
                 * The ID of the genre
                 * <P>Type: INTEGER (long)</P>
                 */
                public static final String GENRE_ID = "genre_id";
            }
        }

        /**
         * Columns representing a playlist
         */
        public interface PlaylistsColumns {
            /**
             * The name of the playlist
             * <P>Type: TEXT</P>
             */
            public static final String NAME = "name";

            /**
             * The data stream for the playlist file
             * <P>Type: DATA STREAM</P>
             */
            public static final String DATA = "_data";

            /**
             * The time the file was added to the media provider
             * Units are seconds since 1970.
             * <P>Type: INTEGER (long)</P>
             */
            public static final String DATE_ADDED = "date_added";
    
            /**
             * The time the file was last modified
             * Units are seconds since 1970.
             * NOTE: This is for internal use by the media scanner.  Do not modify this field.
             * <P>Type: INTEGER (long)</P>
             */
            public static final String DATE_MODIFIED = "date_modified";
        }
        
        /**
         * Contains playlists for audio files
         */
        public static final class Playlists implements BaseColumns,
                PlaylistsColumns {
            /**
             * Get the content:// style URI for the audio playlists table on the 
             * given volume.
             * 
             * @param volumeName the name of the volume to get the URI for
             * @return the URI to the audio playlists table on the given volume
             */
            public static Uri getContentUri(String volumeName) {
                return Uri.parse(CONTENT_AUTHORITY_SLASH + volumeName +
                        "/audio/playlists");
            }

            /**
             * The content:// style URI for the internal storage.
             */
            public static final Uri INTERNAL_CONTENT_URI =
                    getContentUri("internal");
            
            /**
             * The content:// style URI for the "primary" external storage
             * volume.
             */
            public static final Uri EXTERNAL_CONTENT_URI =
                    getContentUri("external");

            /**
             * The MIME type for this table.
             */
            public static final String CONTENT_TYPE = "vnd.android.cursor.dir/playlist";
            
            /**
             * The MIME type for entries in this table.
             */
            public static final String ENTRY_CONTENT_TYPE = "vnd.android.cursor.item/playlist";

            /**
             * The default sort order for this table
             */
            public static final String DEFAULT_SORT_ORDER = NAME;

            /**
             * Sub-directory of each playlist containing all members.
             */
            public static final class Members implements AudioColumns {
                public static final Uri getContentUri(String volumeName,
                        long playlistId) {
                    return Uri.parse(CONTENT_AUTHORITY_SLASH + volumeName
                            + "/audio/playlists/" + playlistId + "/members");
                }

                /**
                 * The ID within the playlist.
                 */
                public static final String _ID = "_id";
                
                /**
                 * A subdirectory of each playlist containing all member audio
                 * files.
                 */
                public static final String CONTENT_DIRECTORY = "members";

                /**
                 * The ID of the audio file
                 * <P>Type: INTEGER (long)</P>
                 */
                public static final String AUDIO_ID = "audio_id";

                /**
                 * The ID of the playlist
                 * <P>Type: INTEGER (long)</P>
                 */
                public static final String PLAYLIST_ID = "playlist_id";
                
                /**
                 * The order of the songs in the playlist
                 * <P>Type: INTEGER (long)></P>
                 */
                public static final String PLAY_ORDER = "play_order";

                /**
                 * The default sort order for this table
                 */
                public static final String DEFAULT_SORT_ORDER = PLAY_ORDER;
            }
        }

        /**
         * Columns representing an artist
         */
        public interface ArtistColumns {
            /**
             * The artist who created the audio file, if any
             * <P>Type: TEXT</P>
             */
            public static final String ARTIST = "artist";

            /**
             * A non human readable key calculated from the ARTIST, used for
             * searching, sorting and grouping
             * <P>Type: TEXT</P>
             */
            public static final String ARTIST_KEY = "artist_key";

            /**
             * The number of albums in the database for this artist
             */
            public static final String NUMBER_OF_ALBUMS = "number_of_albums";

            /**
             * The number of albums in the database for this artist
             */
            public static final String NUMBER_OF_TRACKS = "number_of_tracks";
        }
        
        /**
         * Contains artists for audio files
         */
        public static final class Artists implements BaseColumns, ArtistColumns {
            /**
             * Get the content:// style URI for the artists table on the 
             * given volume.
             * 
             * @param volumeName the name of the volume to get the URI for
             * @return the URI to the audio artists table on the given volume
             */
            public static Uri getContentUri(String volumeName) {
                return Uri.parse(CONTENT_AUTHORITY_SLASH + volumeName +
                        "/audio/artists");
            }

            /**
             * The content:// style URI for the internal storage.
             */
            public static final Uri INTERNAL_CONTENT_URI =
                    getContentUri("internal");
            
            /**
             * The content:// style URI for the "primary" external storage
             * volume.
             */
            public static final Uri EXTERNAL_CONTENT_URI =
                    getContentUri("external");

            /**
             * The MIME type for this table.
             */
            public static final String CONTENT_TYPE = "vnd.android.cursor.dir/artists";
            
            /**
             * The MIME type for entries in this table.
             */
            public static final String ENTRY_CONTENT_TYPE = "vnd.android.cursor.item/artist";

            /**
             * The default sort order for this table
             */
            public static final String DEFAULT_SORT_ORDER = ARTIST_KEY;

            /**
             * Sub-directory of each artist containing all albums on which
             * a song by the artist appears.
             */
            public static final class Albums implements AlbumColumns {
                public static final Uri getContentUri(String volumeName,
                        long artistId) {
                    return Uri.parse(CONTENT_AUTHORITY_SLASH + volumeName
                            + "/audio/artists/" + artistId + "/albums");
                }
            }
        }
        
        /**
         * Columns representing an album
         */
        public interface AlbumColumns {

            /**
             * The id for the album
             * <P>Type: INTEGER</P>
             */
            public static final String ALBUM_ID = "album_id";

            /**
             * The album on which the audio file appears, if any
             * <P>Type: TEXT</P>
             */
            public static final String ALBUM = "album";

            /**
             * The artist whose songs appear on this album
             * <P>Type: TEXT</P>
             */
            public static final String ARTIST = "artist";

            /**
             * The number of songs on this album
             * <P>Type: INTEGER</P>
             */
            public static final String NUMBER_OF_SONGS = "numsongs";

            /**
             * The year in which the earliest and latest songs
             * on this album were released. These will often
             * be the same, but for compilation albums they
             * might differ.
             * <P>Type: INTEGER</P>
             */
            public static final String FIRST_YEAR = "minyear";
            public static final String LAST_YEAR = "maxyear";

            /**
             * A non human readable key calculated from the ALBUM, used for
             * searching, sorting and grouping
             * <P>Type: TEXT</P>
             */
            public static final String ALBUM_KEY = "album_key";
            
            /**
             * Cached album art.
             * <P>Type: TEXT</P>
             */
            public static final String ALBUM_ART = "album_art";
        }
        
        /**
         * Contains artists for audio files
         */
        public static final class Albums implements BaseColumns, AlbumColumns {
            /**
             * Get the content:// style URI for the albums table on the 
             * given volume.
             * 
             * @param volumeName the name of the volume to get the URI for
             * @return the URI to the audio albums table on the given volume
             */
            public static Uri getContentUri(String volumeName) {
                return Uri.parse(CONTENT_AUTHORITY_SLASH + volumeName +
                        "/audio/albums");
            }

            /**
             * The content:// style URI for the internal storage.
             */
            public static final Uri INTERNAL_CONTENT_URI =
                    getContentUri("internal");
            
            /**
             * The content:// style URI for the "primary" external storage
             * volume.
             */
            public static final Uri EXTERNAL_CONTENT_URI =
                    getContentUri("external");

            /**
             * The MIME type for this table.
             */
            public static final String CONTENT_TYPE = "vnd.android.cursor.dir/albums";
            
            /**
             * The MIME type for entries in this table.
             */
            public static final String ENTRY_CONTENT_TYPE = "vnd.android.cursor.item/album";

            /**
             * The default sort order for this table
             */
            public static final String DEFAULT_SORT_ORDER = ALBUM_KEY;
        }
    }

    public static final class Video {
        /**
         * The default sort order for this table
         */
        public static final String DEFAULT_SORT_ORDER = "name ASC";

        public static final Cursor query(ContentResolver cr, Uri uri, String[] projection)
        {
            return cr.query(uri, projection, null, null, DEFAULT_SORT_ORDER);
        }

        public interface VideoColumns extends MediaColumns {

            /**
             * The duration of the video file, in ms
             * <P>Type: INTEGER (long)</P>
             */
            public static final String DURATION = "duration";

            /**
             * The artist who created the video file, if any
             * <P>Type: TEXT</P>
             */
            public static final String ARTIST = "artist";

            /**
             * The album the video file is from, if any
             * <P>Type: TEXT</P>
             */
            public static final String ALBUM = "album";

            /**
             * The resolution of the video file, formatted as "XxY"
             * <P>Type: TEXT</P>
             */
            public static final String RESOLUTION = "resolution";

            /**
             * The description of the video recording
             * <P>Type: TEXT</P>
             */
            public static final String DESCRIPTION = "description";

            /**
             * Whether the video should be published as public or private
             * <P>Type: INTEGER</P>
             */
            public static final String IS_PRIVATE = "isprivate";

            /**
             * The user-added tags associated with a video
             * <P>Type: TEXT</P>
             */
            public static final String TAGS = "tags";

            /**
             * The YouTube category of the video
             * <P>Type: TEXT</P>
             */
            public static final String CATEGORY = "category";

            /**
             * The language of the video
             * <P>Type: TEXT</P>
             */
            public static final String LANGUAGE = "language";
            
            /**
             * The latitude where the image was captured.
             * <P>Type: DOUBLE</P>
             */
            public static final String LATITUDE = "latitude";

            /**
             * The longitude where the image was captured.
             * <P>Type: DOUBLE</P>
             */
            public static final String LONGITUDE = "longitude";
            
            /**
             * The date & time that the image was taken in units
             * of milliseconds since jan 1, 1970.
             * <P>Type: INTEGER</P>
             */
            public static final String DATE_TAKEN = "datetaken";

            /**
             * The mini thumb id.
             * <P>Type: INTEGER</P>
             */
            public static final String MINI_THUMB_MAGIC = "mini_thumb_magic";
        }

        public static final class Media implements VideoColumns {
            /**
             * Get the content:// style URI for the video media table on the 
             * given volume.
             * 
             * @param volumeName the name of the volume to get the URI for
             * @return the URI to the video media table on the given volume
             */
            public static Uri getContentUri(String volumeName) {
                return Uri.parse(CONTENT_AUTHORITY_SLASH + volumeName +
                        "/video/media");
            }

            /**
             * The content:// style URI for the internal storage.
             */
            public static final Uri INTERNAL_CONTENT_URI =
                    getContentUri("internal");
            
            /**
             * The content:// style URI for the "primary" external storage
             * volume.
             */
            public static final Uri EXTERNAL_CONTENT_URI =
                    getContentUri("external");

            /**
             * The MIME type for this table.
             */
            public static final String CONTENT_TYPE = "vnd.android.cursor.dir/video";

            /**
             * The default sort order for this table
             */
            public static final String DEFAULT_SORT_ORDER = TITLE;
        }
    }

    /**
     * Uri for querying the state of the media scanner.
     */
    public static Uri getMediaScannerUri() {
        return Uri.parse(CONTENT_AUTHORITY_SLASH + "none/media_scanner");
    }

    /**
     * Name of current volume being scanned by the media scanner.
     */
    public static final String MEDIA_SCANNER_VOLUME = "volume";
}
