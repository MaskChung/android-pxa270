/*
 * Copyright (C) 2008 Esmertec AG.
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.mms.model;

import com.android.mms.R;
import com.android.mms.drm.DrmUtils;
import com.android.mms.drm.DrmWrapper;
import com.google.android.mms.MmsException;

import org.w3c.dom.events.EventListener;

import android.content.ContentResolver;
import android.content.Context;
import android.drm.mobile1.DrmException;
import android.media.MediaPlayer;
import android.net.Uri;
import android.util.Log;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

public abstract class MediaModel extends Model implements EventListener {
    private static final String TAG = "MediaModel";

    protected Context mContext;
    protected int mBegin;
    protected int mDuration;
    protected String mTag;
    protected String mSrc;
    protected String mContentType;
    private Uri mUri;
    private byte[] mData;
    protected short mFill;
    protected int mSize;
    protected int mSeekTo;
    protected DrmWrapper mDrmObjectWrapper;

    private final ArrayList<MediaAction> mMediaActions;
    public static enum MediaAction {
        NO_ACTIVE_ACTION,
        START,
        STOP,
        PAUSE,
        SEEK,
    }

    public MediaModel(Context context, String tag, Uri uri) {
        this(context, tag, null, null, uri);
    }

    public MediaModel(Context context, String tag, String contentType,
            String src, Uri uri) {
        mContext = context;
        mTag = tag;
        mContentType = contentType;
        mSrc = src;
        mUri = uri;
        initMediaSize();
        mMediaActions = new ArrayList<MediaAction>();
    }

    public MediaModel(Context context, String tag, String contentType,
            String src, byte[] data) {
        if (data == null) {
            throw new IllegalArgumentException("data may not be null.");
        }

        mContext = context;
        mTag = tag;
        mContentType = contentType;
        mSrc = src;
        mData = data;
        mSize = data.length;
        mMediaActions = new ArrayList<MediaAction>();
    }

    public MediaModel(Context context, String tag, String contentType,
            String src, DrmWrapper wrapper) throws IOException {
        mContext = context;
        mTag = tag;
        mContentType = contentType;
        mSrc = src;
        mDrmObjectWrapper = wrapper;
        mUri = DrmUtils.insert(context, wrapper);
        mSize = wrapper.getOriginalData().length;
        mMediaActions = new ArrayList<MediaAction>();
    }

    public int getBegin() {
        return mBegin;
    }

    public void setBegin(int begin) {
        mBegin = begin;
        notifyModelChanged(true);
    }

    public int getDuration() {
        return mDuration;
    }

    public void setDuration(int duration) {
        if (isPlayable() && (duration < 0)) {
            // 'indefinite' duration, we should try to find its exact value;
            try {
                initMediaDuration();
            } catch (MmsException e) {
                // On error, keep default duration.
                Log.e(TAG, e.getMessage(), e);
                return;
            }
        } else {
            mDuration = duration;
        }
        notifyModelChanged(true);
    }

    public String getTag() {
        return mTag;
    }

    public String getContentType() {
        return mContentType;
    }

    /**
     * Get the URI of the media without checking DRM rights. Use this method
     * only if the media is NOT DRM protected.
     *
     * @return The URI of the media.
     */
    public Uri getUri() {
        return mUri;
    }

    /**
     * Get the URI of the media with checking DRM rights. Use this method
     * if the media is probably DRM protected.
     *
     * @return The URI of the media.
     * @throws DrmException Insufficient DRM rights detected.
     */
    public Uri getUriWithDrmCheck() throws DrmException {
        if (mUri != null) {
            if (isDrmProtected() && !mDrmObjectWrapper.consumeRights()) {
                throw new DrmException("Insufficient DRM rights.");
            }
        }
        return mUri;
    }

    public byte[] getData() throws DrmException {
        if (mData != null) {
            if (isDrmProtected() && !mDrmObjectWrapper.consumeRights()) {
                throw new DrmException(
                        mContext.getString(R.string.insufficient_drm_rights));
            }

            byte[] data = new byte[mData.length];
            System.arraycopy(mData, 0, data, 0, mData.length);
            return data;
        }
        return null;
    }

    /**
     * @param uri the mUri to set
     */
    void setUri(Uri uri) {
        mUri = uri;
    }

    /**
     * @return the mSrc
     */
    public String getSrc() {
        return mSrc;
    }

    /**
     * @return the mFill
     */
    public short getFill() {
        return mFill;
    }

    /**
     * @param fill the mFill to set
     */
    public void setFill(short fill) {
        mFill = fill;
        notifyModelChanged(true);
    }

    public int getMediaSize() {
        return mSize;
    }

    public boolean isText() {
        return mTag.equals(SmilHelper.ELEMENT_TAG_TEXT);
    }

    public boolean isImage() {
        return mTag.equals(SmilHelper.ELEMENT_TAG_IMAGE);
    }

    public boolean isVideo() {
        return mTag.equals(SmilHelper.ELEMENT_TAG_VIDEO);
    }

    public boolean isAudio() {
        return mTag.equals(SmilHelper.ELEMENT_TAG_AUDIO);
    }

    public boolean isDrmProtected() {
        return mDrmObjectWrapper != null;
    }

    public boolean isAllowedToForward() {
        return mDrmObjectWrapper.isAllowedToForward();
    }

    protected void initMediaDuration() throws MmsException {
        if (mUri == null) {
            throw new IllegalArgumentException("Uri may not be null.");
        }

        MediaPlayer mediaPlayer = new MediaPlayer();
        try {
            mediaPlayer.setDataSource(mContext, mUri);
            mediaPlayer.prepare();
            mDuration = mediaPlayer.getDuration();
        } catch (IOException e) {
            Log.e(TAG, "Unexpected IOException.", e);
            throw new MmsException(e);
        } finally {
            mediaPlayer.release();
        }
    }

    private void initMediaSize() {
        ContentResolver cr = mContext.getContentResolver();
        InputStream input = null;
        try {
            input = cr.openInputStream(mUri);
            if (input instanceof FileInputStream) {
                // avoid reading the whole stream to get its length
                FileInputStream f = (FileInputStream) input;
                mSize = (int) f.getChannel().size();
            } else {
                while (-1 != input.read()) {
                    mSize++;
                }
            }
        } catch (IOException e) {
            // Ignore
            Log.e(TAG, "IOException caught while opening or reading stream", e);
        } finally {
            if (null != input) {
                try {
                    input.close();
                } catch (IOException e) {
                    // Ignore
                    Log.e(TAG, "IOException caught while closing stream", e);
                }
            }
        }
    }

    public static boolean isMmsUri(Uri uri) {
        return uri.getAuthority().startsWith("mms");
    }

    public int getSeekTo() {
        return mSeekTo;
    }

    public void appendAction(MediaAction action) {
        mMediaActions.add(action);
    }

    public MediaAction getCurrentAction() {
        if (0 == mMediaActions.size()) {
            return MediaAction.NO_ACTIVE_ACTION;
        }
        return mMediaActions.remove(0);
    }

    protected boolean isPlayable() {
        return false;
    }

    public DrmWrapper getDrmObject() {
        return mDrmObjectWrapper;
    }
}
