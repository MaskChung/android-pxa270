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

import android.content.Context;
import android.content.Intent;
import android.content.BroadcastReceiver;
import android.content.SharedPreferences;
import android.util.Log;
import android.view.KeyEvent;
import android.os.Handler;
import android.os.Message;

/**
 * 
 */
public class MediaButtonIntentReceiver extends BroadcastReceiver {

    private static final int MSG_LONGPRESS_TIMEOUT = 1;
    private static final int LONG_PRESS_DELAY = 1000;

    private static long mLastClickTime = 0;
    private static boolean mDown = false;
    private static boolean mLaunched = false;

    private static Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case MSG_LONGPRESS_TIMEOUT:
                    if (!mLaunched) {
                        Context context = (Context)msg.obj;
                        Intent i = new Intent();
                        i.putExtra("autoshuffle", "true");
                        i.setClass(context, MusicBrowserActivity.class);
                        i.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        context.startActivity(i);
                        mLaunched = true;
                    }
                    break;
            }
        }
    };
    
    @Override
    public void onReceive(Context context, Intent intent) {
        KeyEvent event = (KeyEvent)
                intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
        
        if (event == null) {
            return;
        }

        int keycode = event.getKeyCode();
        int action = event.getAction();
        long eventtime = event.getEventTime();

        // single quick press: pause/resume. 
        // double press: next track
        // long press: start auto-shuffle mode.

        if (keycode == KeyEvent.KEYCODE_HEADSETHOOK) {
            if (action == KeyEvent.ACTION_DOWN) {
                if (!mDown) {
                    // only if this isn't a repeat event
                    
                    // We're not using the original time of the event as the
                    // base here, because in some cases it can take more than
                    // one second for us to receive the event, in which case
                    // we would go immediately to auto shuffle mode, even if
                    // the user didn't long press.
                    mHandler.sendMessageDelayed(
                            mHandler.obtainMessage(MSG_LONGPRESS_TIMEOUT, context),
                            LONG_PRESS_DELAY);

                    
                    SharedPreferences pref = context.getSharedPreferences("Music", 
                            Context.MODE_WORLD_READABLE | Context.MODE_WORLD_WRITEABLE);
                    String q = pref.getString("queue", "");
                    // The service may or may not be running, but we need to send it
                    // a command.
                    Intent i = new Intent(context, MediaPlaybackService.class);
                    i.setAction(MediaPlaybackService.SERVICECMD);
                    if (eventtime - mLastClickTime < 300) {
                        i.putExtra(MediaPlaybackService.CMDNAME, MediaPlaybackService.CMDNEXT);
                        context.startService(i);
                        mLastClickTime = 0;
                    } else {
                        i.putExtra(MediaPlaybackService.CMDNAME,
                                MediaPlaybackService.CMDTOGGLEPAUSE);
                        context.startService(i);
                        mLastClickTime = eventtime;
                    }

                    mLaunched = false;
                    mDown = true;
                }
            } else {
                mHandler.removeMessages(MSG_LONGPRESS_TIMEOUT);
                mDown = false;
            }
            abortBroadcast();
        }
    }
}
