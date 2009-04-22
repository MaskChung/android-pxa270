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

package com.android.music;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.BroadcastReceiver;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.database.Cursor;
import android.media.AudioManager;
import android.media.MediaFile;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Environment;
import android.os.FileUtils;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.PowerManager;
import android.os.SystemClock;
import android.os.PowerManager.WakeLock;
import android.provider.MediaStore;
import android.provider.Settings;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneStateIntentReceiver;
import android.util.Log;
import android.widget.RemoteViews;
import android.widget.Toast;

import java.io.IOException;
import java.util.Random;
import java.util.Vector;

/**
 * Provides "background" audio playback capabilities, allowing the
 * user to switch between activities without stopping playback.
 */
public class MediaPlaybackService extends Service {
    /** used to specify whether enqueue() should start playing
     * the new list of files right away, next or once all the currently
     * queued files have been played
     */
    public static final int NOW = 1;
    public static final int NEXT = 2;
    public static final int LAST = 3;
    public static final int PLAYBACKSERVICE_STATUS = 1;
    
    public static final int SHUFFLE_NONE = 0;
    public static final int SHUFFLE_NORMAL = 1;
    public static final int SHUFFLE_AUTO = 2;
    
    public static final int REPEAT_NONE = 0;
    public static final int REPEAT_CURRENT = 1;
    public static final int REPEAT_ALL = 2;

    public static final String PLAYSTATE_CHANGED = "com.android.music.playstatechanged";
    public static final String META_CHANGED = "com.android.music.metachanged";
    public static final String QUEUE_CHANGED = "com.android.music.queuechanged";
    public static final String PLAYBACK_COMPLETE = "com.android.music.playbackcomplete";
    public static final String ASYNC_OPEN_COMPLETE = "com.android.music.asyncopencomplete";

    public static final String SERVICECMD = "com.android.music.musicservicecommand";
    public static final String CMDNAME = "command";
    public static final String CMDTOGGLEPAUSE = "togglepause";
    public static final String CMDPAUSE = "pause";
    public static final String CMDNEXT = "next";
    
    private static final int PHONE_CHANGED = 1;
    private static final int TRACK_ENDED = 1;
    private static final int RELEASE_WAKELOCK = 2;
    private static final int SERVER_DIED = 3;
    private static final int MAX_HISTORY_SIZE = 10;

    private MultiPlayer mPlayer;
    private String mFileToPlay;
    private PhoneStateIntentReceiver mPsir;
    private int mShuffleMode = SHUFFLE_NONE;
    private int mRepeatMode = REPEAT_NONE;
    private int mMediaMountedCount = 0;
    private int [] mAutoShuffleList = null;
    private boolean mOneShot;
    private int [] mPlayList = null;
    private int mPlayListLen = 0;
    private Vector<Integer> mHistory = new Vector<Integer>(MAX_HISTORY_SIZE);
    private Cursor mCursor;
    private int mPlayPos = -1;
    private static final String LOGTAG = "MediaPlaybackService";
    private final Shuffler mRand = new Shuffler();
    private int mOpenFailedCounter = 0;
    String[] mCursorCols = new String[] {
            "audio._id AS _id",
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.MIME_TYPE,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.ARTIST_ID
    };
    private BroadcastReceiver mUnmountReceiver = null;
    private WakeLock mWakeLock;
    private int mServiceStartId = -1;
    private boolean mServiceInUse = false;
    private boolean mResumeAfterCall = false;
    private boolean mWasPlaying = false;
    
    private SharedPreferences mPreferences;
    // We use this to distinguish between different cards when saving/restoring playlists.
    // This will have to change if we want to support multiple simultaneous cards.
    private int mCardId;
    
    // interval after which we stop the service when idle
    private static final int IDLE_DELAY = 60000; 

    private Handler mPhoneHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case PHONE_CHANGED:
                    Phone.State state = mPsir.getPhoneState();
                    if (state == Phone.State.RINGING) {
                        AudioManager audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
                        int ringvolume = audioManager.getStreamVolume(AudioManager.STREAM_RING);
                        if (ringvolume > 0) {
                            mResumeAfterCall = (isPlaying() || mResumeAfterCall) && (getAudioId() >= 0);
                            pause();
                        }
                    } else if (state == Phone.State.OFFHOOK) {
                        // pause the music while a conversation is in progress
                        mResumeAfterCall = (isPlaying() || mResumeAfterCall) && (getAudioId() >= 0);
                        pause();
                    } else if (state == Phone.State.IDLE) {
                        // start playing again
                        if (mResumeAfterCall) {
                            // resume playback only if music was playing
                            // when the call was answered
                            play();
                            mResumeAfterCall = false;
                        }
                    }
                    break;
                default:
                    break;
            }
        }
    };

    private Handler mMediaplayerHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case SERVER_DIED:
                    if (mWasPlaying) {
                        next(true);
                    } else {
                        // the server died when we were idle, so just
                        // reopen the same song (it will start again
                        // from the beginning though when the user
                        // restarts)
                        openCurrent();
                    }
                    break;
                case TRACK_ENDED:
                    if (mRepeatMode == REPEAT_CURRENT) {
                        seek(0);
                        play();
                    } else if (!mOneShot) {
                        next(false);
                    } else {
                        notifyChange(PLAYBACK_COMPLETE);
                    }
                    break;
                case RELEASE_WAKELOCK:
                    mWakeLock.release();
                    break;
                default:
                    break;
            }
        }
    };

    private BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String cmd = intent.getStringExtra("command");
            if (CMDNEXT.equals(cmd)) {
                next(true);
            } else if (CMDTOGGLEPAUSE.equals(cmd)) {
                if (isPlaying()) {
                    pause();
                } else {
                    play();
                }
            } else if (CMDPAUSE.equals(cmd)) {
                pause();
            }
        }
    };

    public MediaPlaybackService() {
        mPsir = new PhoneStateIntentReceiver(this, mPhoneHandler);
        mPsir.notifyPhoneCallState(PHONE_CHANGED);
    }

    @Override
    public void onCreate() {
        super.onCreate();
        
        mPreferences = getSharedPreferences("Music", MODE_WORLD_READABLE | MODE_WORLD_WRITEABLE);
        mCardId = FileUtils.getFatVolumeId(Environment.getExternalStorageDirectory().getPath());
        
        registerExternalStorageListener();

        // Needs to be done in this thread, since otherwise ApplicationContext.getPowerManager() crashes.
        mPlayer = new MultiPlayer();
        mPlayer.setHandler(mMediaplayerHandler);

        // Clear leftover notification in case this service previously got killed while playing
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        nm.cancel(PLAYBACKSERVICE_STATUS);
        
        reloadQueue();

        registerReceiver(mIntentReceiver, new IntentFilter(SERVICECMD));
        mPsir.registerIntent();
        PowerManager pm = (PowerManager)getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, this.getClass().getName());
        mWakeLock.setReferenceCounted(false);
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(mIntentReceiver);
        if (mUnmountReceiver != null) {
            unregisterReceiver(mUnmountReceiver);
            mUnmountReceiver = null;
        }
        mPsir.unregisterIntent();
        mWakeLock.release();
        super.onDestroy();
    }
    
    private final char hexdigits [] = new char [] {
            '0', '1', '2', '3',
            '4', '5', '6', '7',
            '8', '9', 'a', 'b',
            'c', 'd', 'e', 'f'
    };

    private void saveQueue(boolean full) {
        if (mOneShot) {
            return;
        }
        Editor ed = mPreferences.edit();
        //long start = System.currentTimeMillis();
        if (full) {
            StringBuilder q = new StringBuilder();
            
            // The current playlist is saved as a list of "reverse hexadecimal"
            // numbers, which we can generate faster than normal decimal or
            // hexadecimal numbers, which in turn allows us to save the playlist
            // more often without worrying too much about performance.
            // (saving the full state takes about 40 ms under no-load conditions
            // on the phone)
            int len = mPlayListLen;
            for (int i = 0; i < len; i++) {
                int n = mPlayList[i];
                if (n == 0) {
                    q.append("0;");
                } else {
                    while (n != 0) {
                        int digit = n & 0xf;
                        n >>= 4;
                        q.append(hexdigits[digit]);
                    }
                    q.append(";");
                }
            }
            //Log.i("@@@@ service", "created queue string in " + (System.currentTimeMillis() - start) + " ms");
            ed.putString("queue", q.toString());
            ed.putInt("cardid", mCardId);
        }
        ed.putInt("curpos", mPlayPos);
        if (mPlayer.isInitialized()) {
            ed.putLong("seekpos", mPlayer.position());
        }
        ed.putInt("repeatmode", mRepeatMode);
        ed.putInt("shufflemode", mShuffleMode);
        ed.commit();
  
        //Log.i("@@@@ service", "saved state in " + (System.currentTimeMillis() - start) + " ms");
    }

    private void reloadQueue() {
        String q = null;
        
        boolean newstyle = false;
        int id = mCardId;
        if (mPreferences.contains("cardid")) {
            newstyle = true;
            id = mPreferences.getInt("cardid", ~mCardId);
        }
        if (id == mCardId) {
            // Only restore the saved playlist if the card is still
            // the same one as when the playlist was saved
            q = mPreferences.getString("queue", "");
        }
        if (q != null && q.length() > 1) {
            //Log.i("@@@@ service", "loaded queue: " + q);
            String [] entries = q.split(";");
            int len = entries.length;
            ensurePlayListCapacity(len);
            for (int i = 0; i < len; i++) {
                if (newstyle) {
                    String revhex = entries[i];
                    int n = 0;
                    for (int j = revhex.length() - 1; j >= 0 ; j--) {
                        n <<= 4;
                        char c = revhex.charAt(j);
                        if (c >= '0' && c <= '9') {
                            n += (c - '0');
                        } else if (c >= 'a' && c <= 'f') {
                            n += (10 + c - 'a');
                        } else {
                            // bogus playlist data
                            len = 0;
                            break;
                        }
                    }
                    mPlayList[i] = n;
                } else {
                    mPlayList[i] = Integer.parseInt(entries[i]);
                }
            }
            mPlayListLen = len;

            int pos = mPreferences.getInt("curpos", 0);
            if (pos < 0 || pos >= len) {
                // The saved playlist is bogus, discard it
                mPlayListLen = 0;
                return;
            }
            mPlayPos = pos;
            
            // When reloadQueue is called in response to a card-insertion,
            // we might not be able to query the media provider right away.
            // To deal with this, try querying for the current file, and if
            // that fails, wait a while and try again. If that too fails,
            // assume there is a problem and don't restore the state.
            Cursor c = MusicUtils.query(this,
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        new String [] {"_id"}, "_id=" + mPlayList[mPlayPos] , null, null);
            if (c == null || c.getCount() == 0) {
                // wait a bit and try again
                SystemClock.sleep(3000);
                c = getContentResolver().query(
                        MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                        mCursorCols, "_id=" + mPlayList[mPlayPos] , null, null);
            }
            if (c != null) {
                c.close();
            }

            // Make sure we don't auto-skip to the next song, since that
            // also starts playback. What could happen in that case is:
            // - music is paused
            // - go to UMS and delete some files, including the currently playing one
            // - come back from UMS
            // (time passes)
            // - music app is killed for some reason (out of memory)
            // - music service is restarted, service restores state, doesn't find
            //   the "current" file, goes to the next and: playback starts on its
            //   own, potentially at some random inconvenient time.
            mOpenFailedCounter = 20;
            openCurrent();
            if (!mPlayer.isInitialized()) {
                // couldn't restore the saved state
                mPlayListLen = 0;
                return;
            }
            
            long seekpos = mPreferences.getLong("seekpos", 0);
            seek(seekpos >= 0 && seekpos < duration() ? seekpos : 0);
            
            int repmode = mPreferences.getInt("repeatmode", REPEAT_NONE);
            if (repmode != REPEAT_ALL && repmode != REPEAT_CURRENT) {
                repmode = REPEAT_NONE;
            }
            mRepeatMode = repmode;

            int shufmode = mPreferences.getInt("shufflemode", SHUFFLE_NONE);
            if (shufmode != SHUFFLE_AUTO && shufmode != SHUFFLE_NORMAL) {
                shufmode = SHUFFLE_NONE;
            }
            if (shufmode == SHUFFLE_AUTO) {
                if (! makeAutoShuffleList()) {
                    shufmode = SHUFFLE_NONE;
                }
            }
            mShuffleMode = shufmode;
        }
    }
    
    @Override
    public IBinder onBind(Intent intent) {
        mDelayedStopHandler.removeCallbacksAndMessages(null);
        mServiceInUse = true;
        return mBinder;
    }

    @Override
    public void onRebind(Intent intent) {
        mDelayedStopHandler.removeCallbacksAndMessages(null);
        mServiceInUse = true;
    }

    @Override
    public void onStart(Intent intent, int startId) {
        mServiceStartId = startId;
        mDelayedStopHandler.removeCallbacksAndMessages(null);
        String cmd = intent.getStringExtra("command");
        if (CMDNEXT.equals(cmd)) {
            next(true);
        } else if (CMDTOGGLEPAUSE.equals(cmd)) {
            if (isPlaying()) {
                pause();
            } else {
                play();
            }
        } else if (CMDPAUSE.equals(cmd)) {
            pause();
        }
        // make sure the service will shut down on its own if it was
        // just started but not bound to and nothing is playing
        mDelayedStopHandler.removeCallbacksAndMessages(null);
        Message msg = mDelayedStopHandler.obtainMessage();
        mDelayedStopHandler.sendMessageDelayed(msg, IDLE_DELAY);
    }
    
    @Override
    public boolean onUnbind(Intent intent) {
        mServiceInUse = false;

        // Take a snapshot of the current playlist
        saveQueue(true);

        if (isPlaying() || mResumeAfterCall) {
            // something is currently playing, or will be playing once 
            // an in-progress call ends, so don't stop the service now.
            return true;
        }
        
        // If there is a playlist but playback is paused, then wait a while
        // before stopping the service, so that pause/resume isn't slow.
        // Also delay stopping the service if we're transitioning between tracks.
        if (mPlayListLen > 0  || mMediaplayerHandler.hasMessages(TRACK_ENDED)) {
            Message msg = mDelayedStopHandler.obtainMessage();
            mDelayedStopHandler.sendMessageDelayed(msg, IDLE_DELAY);
            return true;
        }
        
        // No active playlist, OK to stop the service right now
        stopSelf(mServiceStartId);
        return true;
    }
    
    private Handler mDelayedStopHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            // Check again to make sure nothing is playing right now
            if (isPlaying() || mResumeAfterCall || mServiceInUse
                    || mMediaplayerHandler.hasMessages(TRACK_ENDED)) {
                return;
            }
            // save the queue again, because it might have change
            // since the user exited the music app (because of
            // party-shuffle or because the play-position changed)
            saveQueue(true);
            stopSelf(mServiceStartId);
        }
    };
    
    /**
     * Called when we receive a ACTION_MEDIA_EJECT notification.
     *
     * @param storagePath path to mount point for the removed media
     */
    public void closeExternalStorageFiles(String storagePath) {
        // stop playback and clean up if the SD card is going to be unmounted.
        stop(true);
        notifyChange(QUEUE_CHANGED);
        notifyChange(META_CHANGED);
    }

    /**
     * Registers an intent to listen for ACTION_MEDIA_EJECT notifications.
     * The intent will call closeExternalStorageFiles() if the external media
     * is going to be ejected, so applications can clean up any files they have open.
     */
    public void registerExternalStorageListener() {
        if (mUnmountReceiver == null) {
            mUnmountReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    String action = intent.getAction();
                    if (action.equals(Intent.ACTION_MEDIA_EJECT)) {
                        saveQueue(true);
                        mOneShot = true; // This makes us not save the state again later,
                                         // which would be wrong because the song ids and
                                         // card id might not match. 
                        closeExternalStorageFiles(intent.getData().getPath());
                    } else if (action.equals(Intent.ACTION_MEDIA_MOUNTED)) {
                        mMediaMountedCount++;
                        mCardId = FileUtils.getFatVolumeId(intent.getData().getPath());
                        reloadQueue();
                        notifyChange(QUEUE_CHANGED);
                        notifyChange(META_CHANGED);
                    }
                }
            };
            IntentFilter iFilter = new IntentFilter();
            iFilter.addAction(Intent.ACTION_MEDIA_EJECT);
            iFilter.addAction(Intent.ACTION_MEDIA_MOUNTED);
            iFilter.addDataScheme("file");
            registerReceiver(mUnmountReceiver, iFilter);
        }
    }

    /**
     * Notify the change-receivers that something has changed.
     * The intent that is sent contains the following data
     * for the currently playing track:
     * "id" - Integer: the database row ID
     * "artist" - String: the name of the artist
     * "album" - String: the name of the album
     * "track" - String: the name of the track
     * The intent has an action that is one of
     * "com.android.music.metachanged"
     * "com.android.music.queuechanged",
     * "com.android.music.playbackcomplete"
     * "com.android.music.playstatechanged"
     * respectively indicating that a new track has
     * started playing, that the playback queue has
     * changed, that playback has stopped because
     * the last file in the list has been played,
     * or that the play-state changed (paused/resumed).
     */
    private void notifyChange(String what) {
        
        Intent i = new Intent(what);
        i.putExtra("id", Integer.valueOf(getAudioId()));
        i.putExtra("artist", getArtistName());
        i.putExtra("album",getAlbumName());
        i.putExtra("track", getTrackName());
        sendBroadcast(i);
        
        if (what.equals(QUEUE_CHANGED)) {
            saveQueue(true);
        } else {
            saveQueue(false);
        }
    }

    private void ensurePlayListCapacity(int size) {
        if (mPlayList == null || size > mPlayList.length) {
            // reallocate at 2x requested size so we don't
            // need to grow and copy the array for every
            // insert
            int [] newlist = new int[size * 2];
            int len = mPlayListLen;
            for (int i = 0; i < len; i++) {
                newlist[i] = mPlayList[i];
            }
            mPlayList = newlist;
        }
        // FIXME: shrink the array when the needed size is much smaller
        // than the allocated size
    }
    
    private void addToPlayList(int id) {
        synchronized(this) {
            ensurePlayListCapacity(mPlayListLen + 1);
            mPlayList[mPlayListLen++] = id;
        }
    }
    
    private void addToPlayList(int [] list, int position) {
        int addlen = list.length;
        if (position < 0) { // overwrite
            mPlayListLen = 0;
            position = 0;
        }
        ensurePlayListCapacity(mPlayListLen + addlen);
        if (position > mPlayListLen) {
            position = mPlayListLen;
        }
        
        // move part of list after insertion point
        int tailsize = mPlayListLen - position;
        for (int i = tailsize ; i > 0 ; i--) {
            mPlayList[position + i] = mPlayList[position + i - addlen]; 
        }
        
        // copy list into playlist
        for (int i = 0; i < addlen; i++) {
            mPlayList[position + i] = list[i];
        }
        mPlayListLen += addlen;
    }
    
    /**
     * Appends a list of tracks to the current playlist.
     * If nothing is playing currently, playback will be started at
     * the first track.
     * If the action is NOW, playback will switch to the first of
     * the new tracks immediately.
     * @param list The list of tracks to append.
     * @param action NOW, NEXT or LAST
     */
    public void enqueue(int [] list, int action) {
        synchronized(this) {
            if (action == NEXT && mPlayPos + 1 < mPlayListLen) {
                addToPlayList(list, mPlayPos + 1);
                notifyChange(QUEUE_CHANGED);
            } else {
                // action == LAST || action == NOW || mPlayPos + 1 == mPlayListLen
                addToPlayList(list, Integer.MAX_VALUE);
                notifyChange(QUEUE_CHANGED);
                if (action == NOW) {
                    mPlayPos = mPlayListLen - list.length;
                    openCurrent();
                    play();
                    notifyChange(META_CHANGED);
                    return;
                }
            }
            if (mPlayPos < 0) {
                mPlayPos = 0;
                openCurrent();
                play();
                notifyChange(META_CHANGED);
            }
        }
    }

    /**
     * Replaces the current playlist with a new list,
     * and prepares for starting playback at the specified
     * position in the list.
     * @param list The new list of tracks.
     */
    public void open(int [] list, int position) {
        synchronized (this) {
            if (mShuffleMode == SHUFFLE_AUTO) {
                mShuffleMode = SHUFFLE_NORMAL;
            }
            addToPlayList(list, -1);
            mPlayPos = position;
            mHistory.clear();

            openCurrent();
        }
    }
    
    /**
     * Moves the item at index1 to index2.
     * @param index1
     * @param index2
     */
    public void moveQueueItem(int index1, int index2) {
        synchronized (this) {
            if (index1 >= mPlayListLen) {
                index1 = mPlayListLen - 1;
            }
            if (index2 >= mPlayListLen) {
                index2 = mPlayListLen - 1;
            }
            if (index1 < index2) {
                int tmp = mPlayList[index1];
                for (int i = index1; i < index2; i++) {
                    mPlayList[i] = mPlayList[i+1];
                }
                mPlayList[index2] = tmp;
                if (mPlayPos == index1) {
                    mPlayPos = index2;
                } else if (mPlayPos >= index1 && mPlayPos <= index2) {
                        mPlayPos--;
                }
            } else if (index2 < index1) {
                int tmp = mPlayList[index1];
                for (int i = index1; i > index2; i--) {
                    mPlayList[i] = mPlayList[i-1];
                }
                mPlayList[index2] = tmp;
                if (mPlayPos == index1) {
                    mPlayPos = index2;
                } else if (mPlayPos >= index2 && mPlayPos <= index1) {
                        mPlayPos++;
                }
            }
            notifyChange(QUEUE_CHANGED);
        }
    }

    /**
     * Returns the current play list
     * @return An array of integers containing the IDs of the tracks in the play list
     */
    public int [] getQueue() {
        synchronized (this) {
            int len = mPlayListLen;
            int [] list = new int[len];
            for (int i = 0; i < len; i++) {
                list[i] = mPlayList[i];
            }
            return list;
        }
    }

    private void openCurrent() {
        synchronized (this) {
            if (mCursor != null) {
                mCursor.close();
                mCursor = null;
            }
            if (mPlayListLen == 0) {
                return;
            }
            stop(false);

            String id = String.valueOf(mPlayList[mPlayPos]);
            
            mCursor = getContentResolver().query(
                    MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    mCursorCols, "_id=" + id , null, null);
            if (mCursor != null) {
                mCursor.moveToFirst();
                open(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI + "/" + id, false);
            }
        }
    }

    public void openAsync(String path) {
        synchronized (this) {
            if (path == null) {
                return;
            }
            
            mRepeatMode = REPEAT_NONE;
            ensurePlayListCapacity(1);
            mPlayListLen = 1;
            mPlayPos = -1;
            
            mFileToPlay = path;
            mCursor = null;
            mPlayer.setDataSourceAsync(mFileToPlay);
            mOneShot = true;
        }
    }
    
    /**
     * Opens the specified file and readies it for playback.
     *
     * @param path The full path of the file to be opened.
     * @param oneshot when set to true, playback will stop after this file completes, instead
     * of moving on to the next track in the list 
     */
    public void open(String path, boolean oneshot) {
        synchronized (this) {
            if (path == null) {
                return;
            }
            
            if (oneshot) {
                mRepeatMode = REPEAT_NONE;
                ensurePlayListCapacity(1);
                mPlayListLen = 1;
                mPlayPos = -1;
            }
            
            // if mCursor is null, try to associate path with a database cursor
            if (mCursor == null) {

                ContentResolver resolver = getContentResolver();
                Uri uri;
                String where;
                String selectionArgs[];
                if (path.startsWith("content://media/")) {
                    uri = Uri.parse(path);
                    where = null;
                    selectionArgs = null;
                } else {
                   uri = MediaStore.Audio.Media.getContentUriForPath(path);
                   where = MediaStore.Audio.Media.DATA + "=?";
                   selectionArgs = new String[] { path };
                }
                
                try {
                    mCursor = resolver.query(uri, mCursorCols, where, selectionArgs, null);
                    if  (mCursor != null) {
                        if (mCursor.getCount() == 0) {
                            mCursor.close();
                            mCursor = null;
                        } else {
                            mCursor.moveToNext();
                            ensurePlayListCapacity(1);
                            mPlayListLen = 1;
                            mPlayList[0] = mCursor.getInt(0);
                            mPlayPos = 0;
                        }
                    }
                } catch (UnsupportedOperationException ex) {
                }
            }
            mFileToPlay = path;
            mPlayer.setDataSource(mFileToPlay);
            mOneShot = oneshot;
            if (! mPlayer.isInitialized()) {
                stop(true);
                if (mOpenFailedCounter++ < 10 &&  mPlayListLen > 1) {
                    // beware: this ends up being recursive because next() calls open() again.
                    next(false);
                }
                if (! mPlayer.isInitialized() && mOpenFailedCounter != 0) {
                    // need to make sure we only shows this once
                    mOpenFailedCounter = 0;
                    Toast.makeText(this, R.string.playback_failed, Toast.LENGTH_SHORT).show();
                }
            } else {
                mOpenFailedCounter = 0;
            }
        }
    }

    /**
     * Starts playback of a previously opened file.
     */
    public void play() {
        if (mPlayer.isInitialized()) {
            mPlayer.start();
            setForeground(true);
            mWasPlaying = true;

            NotificationManager nm = (NotificationManager)
            getSystemService(Context.NOTIFICATION_SERVICE);
    
            RemoteViews views = new RemoteViews(getPackageName(), R.layout.statusbar);
            views.setImageViewResource(R.id.icon, R.drawable.stat_notify_musicplayer);
            views.setTextViewText(R.id.trackname, getTrackName());
            String artist = getArtistName();
            if (artist == null || artist.equals(MediaFile.UNKNOWN_STRING)) {
                artist = getString(R.string.unknown_artist_name);
            }
            String album = getAlbumName();
            if (album == null || album.equals(MediaFile.UNKNOWN_STRING)) {
                album = getString(R.string.unknown_album_name);
            }
            
            views.setTextViewText(R.id.artistalbum,
                    getString(R.string.notification_artist_album, artist, album)
                    );
            
            Intent statusintent = new Intent("com.android.music.PLAYBACK_VIEWER");
            statusintent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
            Notification status = new Notification();
            status.contentView = views;
            status.flags |= Notification.FLAG_ONGOING_EVENT;
            status.icon = R.drawable.stat_notify_musicplayer;
            status.contentIntent = PendingIntent.getActivity(this, 0,
                    new Intent("com.android.music.PLAYBACK_VIEWER"), 0);
            nm.notify(PLAYBACKSERVICE_STATUS, status);
            notifyChange(PLAYSTATE_CHANGED);
        }
    }

    private void stop(boolean remove_status_icon) {
        if (mPlayer.isInitialized()) {
            mPlayer.stop();
        }
        mFileToPlay = null;
        if (mCursor != null) {
            mCursor.close();
            mCursor = null;
        }
        if (remove_status_icon) {
            gotoIdleState();
        }
        setForeground(false);
        mWasPlaying = false;
    }

    /**
     * Stops playback.
     */
    public void stop() {
        stop(true);
    }

    /**
     * Pauses playback (call play() to resume)
     */
    public void pause() {
        if (isPlaying()) {
            mPlayer.pause();
            gotoIdleState();
            setForeground(false);
            mWasPlaying = false;
            notifyChange(PLAYSTATE_CHANGED);
        }
    }

    /** Returns whether playback is currently paused
     *
     * @return true if playback is paused, false if not
     */
    public boolean isPlaying() {
        if (mPlayer.isInitialized()) {
            return mPlayer.isPlaying();
        }
        return false;
    }

    /*
      Desired behavior for prev/next/shuffle:

      - NEXT will move to the next track in the list when not shuffling, and to
        a track randomly picked from the not-yet-played tracks when shuffling.
        If all tracks have already been played, pick from the full set, but
        avoid picking the previously played track if possible.
      - when shuffling, PREV will go to the previously played track. Hitting PREV
        again will go to the track played before that, etc. When the start of the
        history has been reached, PREV is a no-op.
        When not shuffling, PREV will go to the sequentially previous track (the
        difference with the shuffle-case is mainly that when not shuffling, the
        user can back up to tracks that are not in the history).

        Example:
        When playing an album with 10 tracks from the start, and enabling shuffle
        while playing track 5, the remaining tracks (6-10) will be shuffled, e.g.
        the final play order might be 1-2-3-4-5-8-10-6-9-7.
        When hitting 'prev' 8 times while playing track 7 in this example, the
        user will go to tracks 9-6-10-8-5-4-3-2. If the user then hits 'next',
        a random track will be picked again. If at any time user disables shuffling
        the next/previous track will be picked in sequential order again.
     */

    public void prev() {
        synchronized (this) {
            if (mOneShot) {
                // we were playing a specific file not part of a playlist, so there is no 'previous'
                seek(0);
                play();
                return;
            }
            if (mShuffleMode == SHUFFLE_NORMAL) {
                // go to previously-played track and remove it from the history
                int histsize = mHistory.size();
                if (histsize == 0) {
                    // prev is a no-op
                    return;
                }
                Integer pos = mHistory.remove(histsize - 1);
                mPlayPos = pos.intValue();
            } else {
                if (mPlayPos > 0) {
                    mPlayPos--;
                } else {
                    mPlayPos = mPlayListLen - 1;
                }
            }
            stop(false);
            openCurrent();
            play();
            notifyChange(META_CHANGED);
        }
    }

    public void next(boolean force) {
        synchronized (this) {
            if (mOneShot) {
                // we were playing a specific file not part of a playlist, so there is no 'next'
                seek(0);
                play();
                return;
            }

            // Store the current file in the history, but keep the history at a
            // reasonable size
            mHistory.add(Integer.valueOf(mPlayPos));
            if (mHistory.size() > MAX_HISTORY_SIZE) {
                mHistory.removeElementAt(0);
            }

            if (mShuffleMode == SHUFFLE_NORMAL) {
                // Pick random next track from the not-yet-played ones
                // TODO: make it work right after adding/removing items in the queue.

                int numTracks = mPlayListLen;
                int[] tracks = new int[numTracks];
                for (int i=0;i < numTracks; i++) {
                    tracks[i] = i;
                }

                int numHistory = mHistory.size();
                int numUnplayed = numTracks;
                for (int i=0;i < numHistory; i++) {
                    int idx = mHistory.get(i).intValue();
                    if (idx < numTracks && tracks[idx] >= 0) {
                        numUnplayed--;
                        tracks[idx] = -1;
                    }
                }

                // 'numUnplayed' now indicates how many tracks have not yet
                // been played, and 'tracks' contains the indices of those
                // tracks.
                if (numUnplayed <=0) {
                    // everything's already been played
                    if (mRepeatMode == REPEAT_ALL || force) {
                        //pick from full set
                        numUnplayed = numTracks;
                        for (int i=0;i < numTracks; i++) {
                            tracks[i] = i;
                        }
                    } else {
                        // all done
                        gotoIdleState();
                        return;
                    }
                }
                int skip = mRand.nextInt(numUnplayed);
                int cnt = -1;
                while (true) {
                    while (tracks[++cnt] < 0)
                        ;
                    skip--;
                    if (skip < 0) {
                        break;
                    }
                }
                mPlayPos = cnt;
            } else if (mShuffleMode == SHUFFLE_AUTO) {
                doAutoShuffleUpdate();
                mPlayPos++;
            } else {
                if (mPlayPos >= mPlayListLen - 1) {
                    // we're at the end of the list
                    if (mRepeatMode == REPEAT_NONE && !force) {
                        // all done
                        gotoIdleState();
                        notifyChange(PLAYBACK_COMPLETE);
                        return;
                    } else if (mRepeatMode == REPEAT_ALL || force) {
                        mPlayPos = 0;
                    }
                } else {
                    mPlayPos++;
                }
            }
            stop(false);
            openCurrent();
            play();
            notifyChange(META_CHANGED);
        }
    }
    
    private void gotoIdleState() {
        NotificationManager nm =
            (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        nm.cancel(PLAYBACKSERVICE_STATUS);
        mDelayedStopHandler.removeCallbacksAndMessages(null);
        Message msg = mDelayedStopHandler.obtainMessage();
        mDelayedStopHandler.sendMessageDelayed(msg, IDLE_DELAY);
    }
    
    // Make sure there are at least 5 items after the currently playing item
    // and no more than 10 items before.
    private void doAutoShuffleUpdate() {
        // remove old entries
        if (mPlayPos > 10) {
            removeTracks(0, mPlayPos - 9);
        }
        // add new entries if needed
        int to_add = 7 - (mPlayListLen - (mPlayPos < 0 ? -1 : mPlayPos));
        if (to_add > 0) {
            for (int i = 0; i < to_add; i++) {
                // pick something at random from the list
                int idx = mRand.nextInt(mAutoShuffleList.length);
                Integer which = mAutoShuffleList[idx];
                addToPlayList(which);
            }
            notifyChange(QUEUE_CHANGED);
        }
    }

    // A simple variation of Random that makes sure that the
    // value it returns is not equal to the value it returned
    // previously, unless the interval is 1.
    private class Shuffler {
        private int mPrevious;
        private Random mRandom = new Random();
        public int nextInt(int interval) {
            int ret;
            do {
                ret = mRandom.nextInt(interval);
            } while (ret == mPrevious && interval > 1);
            mPrevious = ret;
            return ret;
        }
    };

    private boolean makeAutoShuffleList() {
        ContentResolver res = getContentResolver();
        Cursor c = null;
        try {
            c = res.query(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                    new String[] {MediaStore.Audio.Media._ID}, MediaStore.Audio.Media.IS_MUSIC + "=1",
                    null, null);
            if (c == null || c.getCount() == 0) {
                return false;
            }
            int len = c.getCount();
            int[] list = new int[len];
            for (int i = 0; i < len; i++) {
                c.moveToNext();
                list[i] = c.getInt(0);
            }
            mAutoShuffleList = list;
            return true;
        } catch (RuntimeException ex) {
        } finally {
            if (c != null) {
                c.close();
            }
        }
        return false;
    }
    
    /**
     * Removes the range of tracks specified from the play list. If a file within the range is
     * the file currently being played, playback will move to the next file after the
     * range. 
     * @param first The first file to be removed
     * @param last The last file to be removed
     * @return the number of tracks deleted
     */
    public int removeTracks(int first, int last) {
        synchronized (this) {
            if (last < first) return 0;
            if (first < 0) first = 0;
            if (last >= mPlayListLen) last = mPlayListLen - 1;

            boolean gotonext = false;
            if (first <= mPlayPos && mPlayPos <= last) {
                mPlayPos = first;
                gotonext = true;
            } else if (mPlayPos > last) {
                mPlayPos -= (last - first + 1);
            }
            int num = mPlayListLen - last - 1;
            for (int i = 0; i < num; i++) {
                mPlayList[first + i] = mPlayList[last + 1 + i];
            }
            mPlayListLen -= last - first + 1;
            
            if (gotonext) {
                if (mPlayListLen == 0) {
                    stop(true);
                    mPlayPos = -1;
                } else {
                    if (mPlayPos >= mPlayListLen) {
                        mPlayPos = 0;
                    }
                    stop(false);
                    openCurrent();
                    play();
                }
            }
            notifyChange(QUEUE_CHANGED);
            return last - first + 1;
        }
    }
    
    /**
     * Removes all instances of the track with the given id
     * from the playlist.
     * @param id The id to be removed
     * @return how many instances of the track were removed
     */
    public int removeTrack(int id) {
        int numremoved = 0;
        synchronized (this) {
            for (int i = 0; i < mPlayListLen; i++) {
                if (mPlayList[i] == id) {
                    numremoved += removeTracks(i, i);
                    i--;
                }
            }
        }
        return numremoved;
    }
    
    public void setShuffleMode(int shufflemode) {
        synchronized(this) {
            if (mShuffleMode == shufflemode) {
                return;
            }
            mShuffleMode = shufflemode;
            if (mShuffleMode == SHUFFLE_AUTO) {
                if (makeAutoShuffleList()) {
                    mPlayListLen = 0;
                    doAutoShuffleUpdate();
                    mPlayPos = 0;
                    openCurrent();
                    play();
                    notifyChange(META_CHANGED);
                } else {
                    // failed to build a list of files to shuffle
                    mShuffleMode = SHUFFLE_NONE;
                }
            }
        }
    }
    public int getShuffleMode() {
        return mShuffleMode;
    }
    
    public void setRepeatMode(int repeatmode) {
        synchronized(this) {
            mRepeatMode = repeatmode;
        }
    }
    public int getRepeatMode() {
        return mRepeatMode;
    }

    public int getMediaMountedCount() {
        return mMediaMountedCount;
    }

    /**
     * Returns the path of the currently playing file, or null if
     * no file is currently playing.
     */
    public String getPath() {
        return mFileToPlay;
    }
    
    /**
     * Returns the rowid of the currently playing file, or -1 if
     * no file is currently playing.
     */
    public int getAudioId() {
        synchronized (this) {
            if (mPlayPos >= 0 && mPlayer.isInitialized()) {
                return mPlayList[mPlayPos];
            }
        }
        return -1;
    }
    
    /**
     * Returns the position in the queue 
     * @return the position in the queue
     */
    public int getQueuePosition() {
        synchronized(this) {
            return mPlayPos;
        }
    }
    
    /**
     * Starts playing the track at the given position in the queue.
     * @param pos The position in the queue of the track that will be played.
     */
    public void setQueuePosition(int pos) {
        synchronized(this) {
            stop(false);
            mPlayPos = pos;
            openCurrent();
            play();
            notifyChange(META_CHANGED);
        }
    }

    public String getArtistName() {
        if (mCursor == null) {
            return null;
        }
        return mCursor.getString(mCursor.getColumnIndex(MediaStore.Audio.Media.ARTIST));
    }
    
    public int getArtistId() {
        if (mCursor == null) {
            return -1;
        }
        return mCursor.getInt(mCursor.getColumnIndex(MediaStore.Audio.Media.ARTIST_ID));
    }

    public String getAlbumName() {
        if (mCursor == null) {
            return null;
        }
        return mCursor.getString(mCursor.getColumnIndex(MediaStore.Audio.Media.ALBUM));
    }

    public int getAlbumId() {
        if (mCursor == null) {
            return -1;
        }
        return mCursor.getInt(mCursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID));
    }

    public String getTrackName() {
        if (mCursor == null) {
            return null;
        }
        return mCursor.getString(mCursor.getColumnIndex(MediaStore.Audio.Media.TITLE));
    }

    
    
    /**
     * Returns the duration of the file in milliseconds.
     * Currently this method returns -1 for the duration of MIDI files.
     */
    public long duration() {
        if (mPlayer.isInitialized()) {
            return mPlayer.duration();
        }
        // TODO: when the MIDI engine supports it, return MIDI duration.
        return -1;
    }

    /**
     * Returns the current playback position in milliseconds
     */
    public long position() {
        if (mPlayer.isInitialized()) {
            return mPlayer.position();
        }
        return -1;
    }

    /**
     * Seeks to the position specified.
     *
     * @param pos The position to seek to, in milliseconds
     */
    public long seek(long pos) {
        if (mPlayer.isInitialized()) {
            if (pos < 0) pos = 0;
            if (pos > mPlayer.duration()) pos = mPlayer.duration();
            return mPlayer.seek(pos);
        }
        return -1;
    }

    /**
     * Provides a unified interface for dealing with midi files and
     * other media files.
     */
    private class MultiPlayer {
        private MediaPlayer mMediaPlayer = new MediaPlayer();
        private Handler mHandler;
        private boolean mIsInitialized = false;

        public MultiPlayer() {
            mMediaPlayer.setWakeMode(MediaPlaybackService.this, PowerManager.PARTIAL_WAKE_LOCK);
        }

        public void setDataSourceAsync(String path) {
            try {
                mMediaPlayer.reset();
                mMediaPlayer.setDataSource(path);
                mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
                mMediaPlayer.setOnPreparedListener(preparedlistener);
                mMediaPlayer.prepareAsync();
            } catch (IOException ex) {
                // TODO: notify the user why the file couldn't be opened
                mIsInitialized = false;
                return;
            } catch (IllegalArgumentException ex) {
                // TODO: notify the user why the file couldn't be opened
                mIsInitialized = false;
                return;
            }
            mMediaPlayer.setOnCompletionListener(listener);
            mMediaPlayer.setOnErrorListener(errorListener);
            
            mIsInitialized = true;
        }
        
        public void setDataSource(String path) {
            try {
                mMediaPlayer.reset();
                mMediaPlayer.setOnPreparedListener(null);
                if (path.startsWith("content://")) {
                    mMediaPlayer.setDataSource(MediaPlaybackService.this, Uri.parse(path));
                } else {
                    mMediaPlayer.setDataSource(path);
                }
                mMediaPlayer.setAudioStreamType(AudioManager.STREAM_MUSIC);
                mMediaPlayer.prepare();
            } catch (IOException ex) {
                // TODO: notify the user why the file couldn't be opened
                mIsInitialized = false;
                return;
            } catch (IllegalArgumentException ex) {
                // TODO: notify the user why the file couldn't be opened
                mIsInitialized = false;
                return;
            }
            mMediaPlayer.setOnCompletionListener(listener);
            mMediaPlayer.setOnErrorListener(errorListener);
            
            mIsInitialized = true;
        }
        
        public boolean isInitialized() {
            return mIsInitialized;
        }

        public void start() {
            mMediaPlayer.start();
        }

        public void stop() {
            mMediaPlayer.reset();
            mIsInitialized = false;
        }

        public void pause() {
            mMediaPlayer.pause();
        }
        
        public boolean isPlaying() {
            return mMediaPlayer.isPlaying();
        }

        public void setHandler(Handler handler) {
            mHandler = handler;
        }

        MediaPlayer.OnCompletionListener listener = new MediaPlayer.OnCompletionListener() {
            public void onCompletion(MediaPlayer mp) {
                // Acquire a temporary wakelock, since when we return from
                // this callback the MediaPlayer will release its wakelock
                // and allow the device to go to sleep.
                // This temporary wakelock is released when the RELEASE_WAKELOCK
                // message is processed, but just in case, put a timeout on it.
                mWakeLock.acquire(30000);
                mHandler.sendEmptyMessage(TRACK_ENDED);
                mHandler.sendEmptyMessage(RELEASE_WAKELOCK);
            }
        };

        MediaPlayer.OnPreparedListener preparedlistener = new MediaPlayer.OnPreparedListener() {
            public void onPrepared(MediaPlayer mp) {
                notifyChange(ASYNC_OPEN_COMPLETE);
            }
        };
 
        MediaPlayer.OnErrorListener errorListener = new MediaPlayer.OnErrorListener() {
            public boolean onError(MediaPlayer mp, int what, int extra) {
                switch (what) {
                case MediaPlayer.MEDIA_ERROR_SERVER_DIED:
                    mIsInitialized = false;
                    mMediaPlayer.release();
                    // Creating a new MediaPlayer and settings its wakemode does not
                    // require the media service, so it's OK to do this now, while the
                    // service is still being restarted
                    mMediaPlayer = new MediaPlayer(); 
                    mMediaPlayer.setWakeMode(MediaPlaybackService.this, PowerManager.PARTIAL_WAKE_LOCK);
                    mHandler.sendMessageDelayed(mHandler.obtainMessage(SERVER_DIED), 2000);
                    return true;
                default:
                    break;
                }
                return false;
           }
        };

        public long duration() {
            return mMediaPlayer.getDuration();
        }

        public long position() {
            return mMediaPlayer.getCurrentPosition();
        }

        public long seek(long whereto) {
            mMediaPlayer.seekTo((int) whereto);
            return whereto;
        }
    }

    private final IMediaPlaybackService.Stub mBinder = new IMediaPlaybackService.Stub()
    {
        public void openfileAsync(String path)
        {
            MediaPlaybackService.this.openAsync(path);
        }
        public void openfile(String path)
        {
            MediaPlaybackService.this.open(path, true);
        }
        public void open(int [] list, int position) {
            MediaPlaybackService.this.open(list, position);
        }
        public int getQueuePosition() {
            return MediaPlaybackService.this.getQueuePosition();
        }
        public void setQueuePosition(int index) {
            MediaPlaybackService.this.setQueuePosition(index);
        }
        public boolean isPlaying() {
            return MediaPlaybackService.this.isPlaying();
        }
        public void stop() {
            MediaPlaybackService.this.stop();
        }
        public void pause() {
            MediaPlaybackService.this.pause();
        }
        public void play() {
            MediaPlaybackService.this.play();
        }
        public void prev() {
            MediaPlaybackService.this.prev();
        }
        public void next() {
            MediaPlaybackService.this.next(true);
        }
        public String getTrackName() {
            return MediaPlaybackService.this.getTrackName();
        }
        public String getAlbumName() {
            return MediaPlaybackService.this.getAlbumName();
        }
        public int getAlbumId() {
            return MediaPlaybackService.this.getAlbumId();
        }
        public String getArtistName() {
            return MediaPlaybackService.this.getArtistName();
        }
        public int getArtistId() {
            return MediaPlaybackService.this.getArtistId();
        }
        public void enqueue(int [] list , int action) {
            MediaPlaybackService.this.enqueue(list, action);
        }
        public int [] getQueue() {
            return MediaPlaybackService.this.getQueue();
        }
        public void moveQueueItem(int from, int to) {
            MediaPlaybackService.this.moveQueueItem(from, to);
        }
        public String getPath() {
            return MediaPlaybackService.this.getPath();
        }
        public int getAudioId() {
            return MediaPlaybackService.this.getAudioId();
        }
        public long position() {
            return MediaPlaybackService.this.position();
        }
        public long duration() {
            return MediaPlaybackService.this.duration();
        }
        public long seek(long pos) {
            return MediaPlaybackService.this.seek(pos);
        }
        public void setShuffleMode(int shufflemode) {
            MediaPlaybackService.this.setShuffleMode(shufflemode);
        }
        public int getShuffleMode() {
            return MediaPlaybackService.this.getShuffleMode();
        }
        public int removeTracks(int first, int last) {
            return MediaPlaybackService.this.removeTracks(first, last);
        }
        public int removeTrack(int id) {
            return MediaPlaybackService.this.removeTrack(id);
        }
        public void setRepeatMode(int repeatmode) {
            MediaPlaybackService.this.setRepeatMode(repeatmode);
        }
        public int getRepeatMode() {
            return MediaPlaybackService.this.getRepeatMode();
        }
        public int getMediaMountedCount() {
            return MediaPlaybackService.this.getMediaMountedCount();
        }
    };
}

