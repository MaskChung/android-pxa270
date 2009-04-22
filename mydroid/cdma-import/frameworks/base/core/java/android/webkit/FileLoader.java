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

package android.webkit;

import android.content.Context;
import android.content.res.AssetManager;
import android.net.http.EventHandler;
import android.net.http.Headers;
import android.os.Environment;

import java.io.File;
import java.io.FileInputStream;

/**
 * This class is a concrete implementation of StreamLoader that uses a
 * file or asset as the source for the stream.
 *
 */
class FileLoader extends StreamLoader {

    private String mPath;  // Full path to the file to load
    private Context mContext;  // Application context, used for asset loads
    private boolean mIsAsset;  // Indicates if the load is an asset or not

    /**
     * Construct a FileLoader with the file URL specified as the content
     * source.
     *
     * @param url Full file url pointing to content to be loaded
     * @param loadListener LoadListener to pass the content to
     * @param context Context to use to access the asset.
     * @param asset true if url points to an asset.
     */
    FileLoader(String url, LoadListener loadListener, Context context,
            boolean asset) {
        super(loadListener);
        mIsAsset = asset;
        mContext = context;

        // clean the Url
        int index = url.indexOf('?');
        if (mIsAsset) {
            mPath = index > 0 ? URLUtil.stripAnchor(
                    url.substring(URLUtil.ASSET_BASE.length(), index)) :
                    URLUtil.stripAnchor(url.substring(
                            URLUtil.ASSET_BASE.length()));
        } else {
            mPath = index > 0 ? URLUtil.stripAnchor(
                    url.substring(URLUtil.FILE_BASE.length(), index)) :
                    URLUtil.stripAnchor(url.substring(
                            URLUtil.FILE_BASE.length()));
        }
    }

    @Override
    protected boolean setupStreamAndSendStatus() {
        try {
            if (mIsAsset) {
                mDataStream = mContext.getAssets().open(mPath,
                        AssetManager.ACCESS_STREAMING);
            } else {
                mHandler.error(EventHandler.FILE_ERROR,
                        mContext.getString(
                                com.android.internal.R.string.httpErrorFileNotFound));
                return false;
/*
                if (!mPath.startsWith(
                        Environment.getExternalStorageDirectory().getPath())) {
                    mHandler.error(EventHandler.FILE_ERROR,
                            mContext.getString(
                                    com.android.internal.R.string.httpErrorFileNotFound));
                    return false;
                }
                mDataStream = new FileInputStream(mPath);
                mContentLength = (new File(mPath)).length();
*/
            }
            mHandler.status(1, 1, 0, "OK");

        } catch (java.io.FileNotFoundException ex) {
            mHandler.error(
                    EventHandler.FILE_NOT_FOUND_ERROR,
                    mContext.getString(com.android.internal.R.string.httpErrorFileNotFound) +
                    " " + ex.getMessage());
            return false;

        } catch (java.io.IOException ex) {
            mHandler.error(EventHandler.FILE_ERROR,
                           mContext.getString(
                                   com.android.internal.R.string.httpErrorFileNotFound) +
                           " " + ex.getMessage());
            return false;
        }
        return true;
    }

    @Override
    protected void buildHeaders(Headers headers) {
        // do nothing.
    }


    /**
     * Construct a FileLoader and instruct it to start loading.
     *
     * @param url Full file url pointing to content to be loaded
     * @param loadListener LoadListener to pass the content to
     * @param context Context to use to access the asset.
     * @param asset true if url points to an asset.
     */
    public static void requestUrl(String url, LoadListener loadListener,
            Context context, boolean asset) {
        FileLoader loader = new FileLoader(url, loadListener, context, asset);
        loader.load();
    }

}
