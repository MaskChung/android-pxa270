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

package com.android.calendar;

import dalvik.system.VMRuntime;

import android.accounts.AccountMonitor;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.database.ContentObserver;
import android.os.Bundle;
import android.os.Handler;
import android.pim.Time;
import android.provider.Calendar;
import android.view.GestureDetector;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ProgressBar;
import android.widget.ViewSwitcher;

/**
 * This is the base class for Day and Week Activities.
 */
public class CalendarActivity extends Activity implements Navigator {

    private static final long INITIAL_HEAP_SIZE = 4*1024*1024;

    protected static final String BUNDLE_KEY_RESTORE_TIME = "key_restore_time";

    private ContentResolver mContentResolver;

    private AccountMonitor mAccountMonitor;

    protected ProgressBar mProgressBar;
    protected ViewSwitcher mViewSwitcher;
    protected Animation mInAnimationForward;
    protected Animation mOutAnimationForward;
    protected Animation mInAnimationBackward;
    protected Animation mOutAnimationBackward;
    EventLoader mEventLoader;

    Time mSelectedDay = new Time();

    /* package */ GestureDetector mGestureDetector;

    /**
     * Listens for intent broadcasts
     */
    private BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(Intent.ACTION_TIME_CHANGED)
                    || action.equals(Intent.ACTION_DATE_CHANGED)
                    || action.equals(Intent.ACTION_TIMEZONE_CHANGED)) {
                eventsChanged();
            }
        }
    };

    // Create an observer so that we can update the views whenever a
    // Calendar event changes.
    private ContentObserver mObserver = new ContentObserver(new Handler())
    {
        @Override
        public boolean deliverSelfNotifications() {
            return true;
        }

        @Override
        public void onChange(boolean selfChange) {
            eventsChanged();
        }
    };

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        // Eliminate extra GCs during startup by setting the initial heap size to 4MB.
        // TODO: We should restore the old heap size once the activity reaches the idle state
        long oldHeapSize = VMRuntime.getRuntime().setMinimumHeapSize(INITIAL_HEAP_SIZE);

        setDefaultKeyMode(DEFAULT_KEYS_SHORTCUT);
        mContentResolver = getContentResolver();

        mInAnimationForward = AnimationUtils.loadAnimation(this, R.anim.slide_left_in);
        mOutAnimationForward = AnimationUtils.loadAnimation(this, R.anim.slide_left_out);
        mInAnimationBackward = AnimationUtils.loadAnimation(this, R.anim.slide_right_in);
        mOutAnimationBackward = AnimationUtils.loadAnimation(this, R.anim.slide_right_out);

        mGestureDetector = new GestureDetector(new CalendarGestureListener());
        mEventLoader = new EventLoader(this);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);

        CalendarView view = (CalendarView) mViewSwitcher.getCurrentView();
        Time time = new Time();
        time.set(savedInstanceState.getLong(BUNDLE_KEY_RESTORE_TIME));
        view.setSelectedDay(time);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mEventLoader.startBackgroundThread();
        eventsChanged();

        // Register for Intent broadcasts
        IntentFilter filter = new IntentFilter();

        filter.addAction(Intent.ACTION_TIME_CHANGED);
        filter.addAction(Intent.ACTION_DATE_CHANGED);
        filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        registerReceiver(mIntentReceiver, filter);

        mContentResolver.registerContentObserver(Calendar.Events.CONTENT_URI,
                true, mObserver);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        outState.putLong(BUNDLE_KEY_RESTORE_TIME, getSelectedTimeInMillis());
    }

    @Override
    protected void onDestroy() {
        if (mAccountMonitor != null) {
            mAccountMonitor.close();
        }
        super.onDestroy();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mContentResolver.unregisterContentObserver(mObserver);
        unregisterReceiver(mIntentReceiver);

        CalendarView view = (CalendarView) mViewSwitcher.getCurrentView();
        view.cleanup();
        view = (CalendarView) mViewSwitcher.getNextView();
        view.cleanup();
        mEventLoader.stopBackgroundThread();
    }

    void startProgressSpinner() {
        // start the progress spinner
        mProgressBar.setVisibility(View.VISIBLE);
    }

    void stopProgressSpinner() {
        // stop the progress spinner
        mProgressBar.setVisibility(View.GONE);
    }

    /* Navigator interface methods */
    public void goTo(Time time) {
        CalendarView current = (CalendarView) mViewSwitcher.getCurrentView();

        if (current.getSelectedTime().before(time)) {
            mViewSwitcher.setInAnimation(mInAnimationForward);
            mViewSwitcher.setOutAnimation(mOutAnimationForward);
        } else {
            mViewSwitcher.setInAnimation(mInAnimationBackward);
            mViewSwitcher.setOutAnimation(mOutAnimationBackward);
        }

        CalendarView next = (CalendarView) mViewSwitcher.getNextView();
        next.setSelectedDay(time);
        next.reloadEvents();
        mViewSwitcher.showNext();
        next.requestFocus();
    }

    /**
     * Returns the selected time in milliseconds. The milliseconds are measured
     * in UTC milliseconds from the epoch and uniquely specifies any selectable
     * time.
     *
     * @return the selected time in milliseconds
     */
    public long getSelectedTimeInMillis() {
        CalendarView view = (CalendarView) mViewSwitcher.getCurrentView();
        return view.getSelectedTimeInMillis();
    }

    public long getSelectedTime() {
        return getSelectedTimeInMillis();
    }

    public void goToToday() {
        mSelectedDay.set(System.currentTimeMillis());
        CalendarView view = (CalendarView) mViewSwitcher.getCurrentView();
        view.setSelectedDay(mSelectedDay);
        view.reloadEvents();
    }

    public boolean getAllDay() {
        CalendarView view = (CalendarView) mViewSwitcher.getCurrentView();
        return view.mSelectionAllDay;
    }

    void eventsChanged() {
        CalendarView view = (CalendarView) mViewSwitcher.getCurrentView();
        view.clearCachedEvents();
        view.reloadEvents();
    }

    Event getSelectedEvent() {
        CalendarView view = (CalendarView) mViewSwitcher.getCurrentView();
        return view.getSelectedEvent();
    }

    boolean isEventSelected() {
        CalendarView view = (CalendarView) mViewSwitcher.getCurrentView();
        return view.isEventSelected();
    }

    Event getNewEvent() {
        CalendarView view = (CalendarView) mViewSwitcher.getCurrentView();
        return view.getNewEvent();
    }

    public CalendarView getNextView() {
        return (CalendarView) mViewSwitcher.getNextView();
    }

    public View switchViews(boolean forward, float xOffSet, float width) {
        long offset = 0;
        if (xOffSet != 0) {

            // The user might have scrolled the view to the left or right
            // in which case we just want to animate the bit left over
            // instead of animating all of it. So calculate how much
            // it's been moved already and animate the remaining portion
            double progress = ((width - (Math.abs(xOffSet))) / width);
            long duration = mInAnimationForward.getDuration();
            offset = -1 * (long) (duration - (duration * progress));
        }
        if (forward) {
            mInAnimationForward.setStartOffset(offset);
            mOutAnimationForward.setStartOffset(offset);
            mViewSwitcher.setInAnimation(mInAnimationForward);
            mViewSwitcher.setOutAnimation(mOutAnimationForward);
        } else {
            mInAnimationBackward.setStartOffset(offset);
            mOutAnimationBackward.setStartOffset(offset);
            mViewSwitcher.setInAnimation(mInAnimationBackward);
            mViewSwitcher.setOutAnimation(mOutAnimationBackward);
        }
        CalendarView view = (CalendarView) mViewSwitcher.getCurrentView();
        view.cleanup();
        mViewSwitcher.showNext();
        view = (CalendarView) mViewSwitcher.getCurrentView();
        view.requestFocus();
        view.reloadEvents();
        return view;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuHelper.onPrepareOptionsMenu(this, menu);
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        if (! MenuHelper.onCreateOptionsMenu(menu)) {
            return false;
        }
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (MenuHelper.onOptionsItemSelected(this, item, this)) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (mGestureDetector.onTouchEvent(ev)) {
            return true;
        }
        return super.onTouchEvent(ev);
    }

    class CalendarGestureListener extends GestureDetector.SimpleOnGestureListener {
        @Override
        public boolean onSingleTapUp(MotionEvent ev) {
            CalendarView view = (CalendarView) mViewSwitcher.getCurrentView();
            view.doSingleTapUp(ev);
            return true;
        }

        @Override
        public void onShowPress(MotionEvent ev) {
            CalendarView view = (CalendarView) mViewSwitcher.getCurrentView();
            view.doShowPress(ev);
        }

        @Override
        public void onLongPress(MotionEvent ev) {
            CalendarView view = (CalendarView) mViewSwitcher.getCurrentView();
            view.doLongPress(ev);
        }

        @Override
        public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
            CalendarView view = (CalendarView) mViewSwitcher.getCurrentView();
            view.doScroll(e1, e2, distanceX, distanceY);
            return true;
        }

        @Override
        public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
            CalendarView view = (CalendarView) mViewSwitcher.getCurrentView();
            view.doFling(e1, e2, velocityX, velocityY);
            return true;
        }

        @Override
        public boolean onDown(MotionEvent ev) {
            CalendarView view = (CalendarView) mViewSwitcher.getCurrentView();
            view.doDown(ev);
            return true;
        }
    }
}

