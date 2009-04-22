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

package com.android.mms.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.method.NumberKeyListener;
import android.util.Config;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.View.OnClickListener;
import android.view.View.OnKeyListener;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import com.android.mms.R;

/**
 * This activity provides the function to edit the duration of given slide.
 */
public class EditSlideDurationActivity  extends Activity {
    public static final String SLIDE_INDEX = "slide_index";
    public static final String SLIDE_TOTAL = "slide_total";
    public static final String SLIDE_DUR   = "dur";

    private TextView mLabel;
    private Button mDone;
    private EditText mDur;

    private int mCurSlide;
    private int mTotal;

    private Bundle mState;
    //  State.
    private final static String STATE = "state";
    private final static String TAG = "EditSlideDurationActivity";
    private static final boolean DEBUG = false;
    private static final boolean LOCAL_LOGV = DEBUG ? Config.LOGD : Config.LOGV;

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.edit_slide_duration);

        int dur;
        if (icicle == null) {
            // Get extra from intent.
            Intent intent = getIntent();
            mCurSlide = intent.getIntExtra(SLIDE_INDEX, 1);
            mTotal = intent.getIntExtra(SLIDE_TOTAL, 1);
            dur = intent.getIntExtra(SLIDE_DUR, 8);
        } else {
            mState = icicle.getBundle(STATE);

            mCurSlide = mState.getInt(SLIDE_INDEX, 1);
            mTotal = mState.getInt(SLIDE_TOTAL, 1);
            dur = mState.getInt(SLIDE_DUR, 8);
        }

        // Label.
        mLabel = (TextView) findViewById(R.id.label);
        mLabel.setText("Duration for slide " + (mCurSlide + 1) + "/" + mTotal);

        // Input text field.
        mDur = (EditText) findViewById(R.id.text);
        mDur.setText(String.valueOf(dur));
        mDur.setKeyListener(DigitsKeyListener.getInstance());
        mDur.setFilters(new InputFilter[] {new InputFilter.LengthFilter(4)});
        mDur.setOnKeyListener(mOnKeyListener);

        // Done button.
        mDone = (Button) findViewById(R.id.done);
        mDone.setOnClickListener(mOnDoneClickListener);
    }

    /*
     * (non-Javadoc)
     * @see android.app.Activity#onSaveInstanceState(android.os.Bundle)
     */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        mState = new Bundle();
        mState.putInt(SLIDE_INDEX, mCurSlide);
        mState.putInt(SLIDE_TOTAL, mTotal);
        mState.putInt(SLIDE_DUR, Integer.parseInt(mDur.getText().toString()));

        outState.putBundle(STATE, mState);
    }

    private final OnKeyListener mOnKeyListener = new OnKeyListener() {
        public boolean onKey(View v, int keyCode, KeyEvent event) {
            if (event.getAction() != KeyEvent.ACTION_DOWN) {
                return false;
            }

            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_CENTER:
                    // Edit complete.
                    editDone();
                    break;
            }
            return false;
        }
    };

    private final OnClickListener mOnDoneClickListener = new OnClickListener() {
        public void onClick(View v) {
            // Edit complete.
            editDone();
        }
    };

    protected void editDone() {
        // Set result to parent, and close window.
        // Check the duration.
        String dur = mDur.getText().toString();
        try {
            Integer.valueOf(dur);
        } catch (NumberFormatException e) {
            notifyUser("Invalid duration! Please input again.");
            mDur.requestFocus();
            mDur.selectAll();
            return;
        }

        // Set result.
        setResult(RESULT_OK, new Intent(mDur.getText().toString()));
        finish();
    }

    private void notifyUser(String message) {
        if (LOCAL_LOGV) {
            Log.v(TAG, "notifyUser: message=" + message);
        }
    }

    private static class DigitsKeyListener extends NumberKeyListener {
        private static DigitsKeyListener sInstance;
        private final char[] mAccepted = CHARACTERS;

        @Override
        protected char[] getAcceptedChars() {
            return mAccepted;
        }

        /**
         * The characters that are used.
         *
         * @see KeyEvent#getMatch
         * @see #getAcceptedChars
         */
        private static final char[] CHARACTERS = new char[] {
                '0', '1', '2', '3', '4', '5', '6', '7', '8', '9'
        };

        /**
         * Returns a DigitsKeyListener that accepts the digits 0 through 9
         * and the plus sign.
         */
        public static DigitsKeyListener getInstance() {
            if (sInstance != null) {
                return sInstance;
            }

            sInstance = new DigitsKeyListener();
            return sInstance;
        }
    }
}
