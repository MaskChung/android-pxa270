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


import android.app.Activity;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Config;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.MediaController;
import android.widget.VideoView;

public class MovieView extends Activity implements MediaPlayer.OnErrorListener
{
    private static final String TAG = "MovieView";

    private VideoView   mVideoView;
    private View        mProgressView;
    public MovieView()
    {
    }

    @Override
    public void onCreate(Bundle icicle)
    {
        super.onCreate(icicle);
        
        setContentView(R.layout.movie_view);

        mVideoView = (VideoView) findViewById(R.id.surface_view);
        mProgressView = findViewById(R.id.progress_indicator);
        Uri uri = getIntent().getData();
        
        // For streams that we expect to be slow to start up, show a
        // progress spinner until playback starts.
        String scheme = uri.getScheme();
        if ("http".equalsIgnoreCase(scheme) ||
                "rtsp".equalsIgnoreCase(scheme)) {
            mHandler.postDelayed(mPlayingChecker, 250);
        } else {
            mProgressView.setVisibility(View.GONE);
        }
        
        mVideoView.setOnErrorListener(this);
        mVideoView.setVideoURI(uri);
        mVideoView.setMediaController(new MediaController(this));
        mVideoView.requestFocus(); // make the video view handle keys for seeking and pausing

        Intent i = new Intent(MediaPlaybackService.SERVICECMD);
        i.putExtra(MediaPlaybackService.CMDNAME, MediaPlaybackService.CMDPAUSE);
        sendBroadcast(i);
        
        mVideoView.start();
    }
    
    @Override
    public void onPause() {
        mHandler.removeCallbacksAndMessages(null);
        super.onPause();
    }
    
    Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
        }
    };

    Runnable mPlayingChecker = new Runnable() {
        public void run() {
            if (mVideoView.isPlaying()) {
                mProgressView.setVisibility(View.GONE);
            } else {
                mHandler.postDelayed(mPlayingChecker, 250);
            }
        }
    };
    
    public boolean onError(MediaPlayer player, int arg1, int arg2) {
        mHandler.removeCallbacksAndMessages(null);
        mProgressView.setVisibility(View.GONE);
        return false;
    }
}
