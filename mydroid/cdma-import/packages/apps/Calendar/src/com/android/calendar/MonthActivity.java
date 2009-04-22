/*
 * Copyright (C) 2006 The Android Open Source Project
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

import static android.provider.Calendar.EVENT_BEGIN_TIME;
import dalvik.system.VMRuntime;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.database.ContentObserver;
import android.os.Bundle;
import android.os.Handler;
import android.pim.DateUtils;
import android.pim.Time;
import android.preference.PreferenceManager;
import android.provider.Calendar.Events;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.Animation.AnimationListener;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.ViewSwitcher;
import android.widget.Gallery.LayoutParams;

import java.util.Calendar;

public class MonthActivity extends Activity implements ViewSwitcher.ViewFactory,
        Navigator, AnimationListener {
    private static final int INITIAL_HEAP_SIZE = 4 * 1024 * 1024;
    private Animation mInAnimationPast;
    private Animation mInAnimationFuture;
    private Animation mOutAnimationPast;
    private Animation mOutAnimationFuture;
    private ViewSwitcher mSwitcher;
    private Time mTime;

    private ContentResolver mContentResolver;
    EventLoader mEventLoader;
    private int mStartDay;

    private ProgressBar mProgressBar;

    protected void startProgressSpinner() {
        // start the progress spinner
        mProgressBar.setVisibility(View.VISIBLE);
    }

    protected void stopProgressSpinner() {
        // stop the progress spinner
        mProgressBar.setVisibility(View.GONE);
    }

    /* ViewSwitcher.ViewFactory interface methods */
    public View makeView() {
        MonthView mv = new MonthView(this, this);
        mv.setLayoutParams(new ViewSwitcher.LayoutParams(
                LayoutParams.FILL_PARENT, LayoutParams.FILL_PARENT));
        mv.setSelectedTime(mTime);
        return mv;
    }

    /* Navigator interface methods */
    public void goTo(Time time) {
        TextView title = (TextView) findViewById(R.id.title);
        title.setText(Utils.formatMonthYear(time));

        MonthView current = (MonthView) mSwitcher.getCurrentView();
        current.dismissPopup();

        Time currentTime = current.getTime();

        // Compute a month number that is monotonically increasing for any
        // two adjacent months.
        // This is faster than calling getSelectedTime() because we avoid
        // a call to Time#normalize().
        int currentMonth = currentTime.month + currentTime.year * 12;
        int nextMonth = time.month + time.year * 12;
        if (nextMonth < currentMonth) {
            mSwitcher.setInAnimation(mInAnimationPast);
            mSwitcher.setOutAnimation(mOutAnimationPast);
        } else {
            mSwitcher.setInAnimation(mInAnimationFuture);
            mSwitcher.setOutAnimation(mOutAnimationFuture);
        }

        MonthView next = (MonthView) mSwitcher.getNextView();
        next.setSelectionMode(current.getSelectionMode());
        next.setSelectedTime(time);
        next.reloadEvents();
        next.animationStarted();
        mSwitcher.showNext();
        next.requestFocus();
        mTime = time;
    }

    public void goToToday() {
        Time now = new Time();
        now.set(System.currentTimeMillis());

        TextView title = (TextView) findViewById(R.id.title);
        title.setText(Utils.formatMonthYear(now));
        mTime = now;

        MonthView view = (MonthView) mSwitcher.getCurrentView();
        view.setSelectedTime(now);
        view.reloadEvents();
    }

    public long getSelectedTime() {
        MonthView mv = (MonthView) mSwitcher.getCurrentView();
        return mv.getSelectedTimeInMillis();
    }

    public boolean getAllDay() {
        return false;
    }

    int getStartDay() {
        return mStartDay;
    }

    void eventsChanged() {
        MonthView view = (MonthView) mSwitcher.getCurrentView();
        view.reloadEvents();
    }

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

    public void onAnimationStart(Animation animation) {
    }

    // Notifies the MonthView when an animation has finished.
    public void onAnimationEnd(Animation animation) {
        MonthView monthView = (MonthView) mSwitcher.getCurrentView();
        monthView.animationFinished();
    }

    public void onAnimationRepeat(Animation animation) {
    }

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        // Eliminate extra GCs during startup by setting the initial heap size to 4MB.
        // TODO: We should restore the old heap size once the activity reaches the idle state
        long oldHeapSize = VMRuntime.getRuntime().setMinimumHeapSize(INITIAL_HEAP_SIZE);

        setContentView(R.layout.month_activity);
        mContentResolver = getContentResolver();

        long time;
        if (icicle != null) {
            time = icicle.getLong(EVENT_BEGIN_TIME);
        } else {
            time = Utils.timeFromIntentInMillis(getIntent());
        }

        mTime = new Time();
        mTime.set(time);
        mTime.normalize(true);

        // Get first day of week based on locale and populate the day headers
        mStartDay = Calendar.getInstance().getFirstDayOfWeek();
        int diff = mStartDay - Calendar.SUNDAY - 1;

        String dayString = DateUtils.getDayOfWeekString((Calendar.SUNDAY + diff) % 7 + 1,
                DateUtils.LENGTH_MEDIUM);
        ((TextView) findViewById(R.id.day0)).setText(dayString);
        dayString = DateUtils.getDayOfWeekString((Calendar.MONDAY + diff) % 7 + 1,
                DateUtils.LENGTH_MEDIUM);
        ((TextView) findViewById(R.id.day1)).setText(dayString);
        dayString = DateUtils.getDayOfWeekString((Calendar.TUESDAY + diff) % 7 + 1,
                DateUtils.LENGTH_MEDIUM);
        ((TextView) findViewById(R.id.day2)).setText(dayString);
        dayString = DateUtils.getDayOfWeekString((Calendar.WEDNESDAY + diff) % 7 + 1,
                DateUtils.LENGTH_MEDIUM);
        ((TextView) findViewById(R.id.day3)).setText(dayString);
        dayString = DateUtils.getDayOfWeekString((Calendar.THURSDAY + diff) % 7 + 1,
                DateUtils.LENGTH_MEDIUM);
        ((TextView) findViewById(R.id.day4)).setText(dayString);
        dayString = DateUtils.getDayOfWeekString((Calendar.FRIDAY + diff) % 7 + 1,
                DateUtils.LENGTH_MEDIUM);
        ((TextView) findViewById(R.id.day5)).setText(dayString);
        dayString = DateUtils.getDayOfWeekString((Calendar.SATURDAY + diff) % 7 + 1,
                DateUtils.LENGTH_MEDIUM);
        ((TextView) findViewById(R.id.day6)).setText(dayString);

        // Set the initial title
        TextView title = (TextView) findViewById(R.id.title);
        title.setText(Utils.formatMonthYear(mTime));

        mEventLoader = new EventLoader(this);
        mProgressBar = (ProgressBar) findViewById(R.id.progress_circular);

        mSwitcher = (ViewSwitcher) findViewById(R.id.switcher);
        mSwitcher.setFactory(this);
        mSwitcher.getCurrentView().requestFocus();

        mInAnimationPast = AnimationUtils.loadAnimation(this, R.anim.slide_down_in);
        mOutAnimationPast = AnimationUtils.loadAnimation(this, R.anim.slide_down_out);
        mInAnimationFuture = AnimationUtils.loadAnimation(this, R.anim.slide_up_in);
        mOutAnimationFuture = AnimationUtils.loadAnimation(this, R.anim.slide_up_out);

        mInAnimationPast.setAnimationListener(this);
        mInAnimationFuture.setAnimationListener(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (isFinishing()) {
            mEventLoader.stopBackgroundThread();
        }
        mContentResolver.unregisterContentObserver(mObserver);
        unregisterReceiver(mIntentReceiver);

        MonthView view = (MonthView) mSwitcher.getCurrentView();
        view.dismissPopup();
        view = (MonthView) mSwitcher.getNextView();
        view.dismissPopup();
        mEventLoader.stopBackgroundThread();
    }

    @Override
    protected void onResume() {
        super.onResume();
        mEventLoader.startBackgroundThread();
        eventsChanged();

        MonthView view1 = (MonthView) mSwitcher.getCurrentView();
        MonthView view2 = (MonthView) mSwitcher.getNextView();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
        String str = prefs.getString(CalendarPreferenceActivity.KEY_DETAILED_VIEW,
                CalendarPreferenceActivity.DEFAULT_DETAILED_VIEW);
        view1.setDetailedView(str);
        view2.setDetailedView(str);

        // Record Month View as the (new) start view
        String activityString = CalendarApplication.ACTIVITY_NAMES[CalendarApplication.MONTH_VIEW_ID];
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(CalendarPreferenceActivity.KEY_START_VIEW, activityString);
        editor.commit();

        // Register for Intent broadcasts
        IntentFilter filter = new IntentFilter();

        filter.addAction(Intent.ACTION_TIME_CHANGED);
        filter.addAction(Intent.ACTION_DATE_CHANGED);
        filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        registerReceiver(mIntentReceiver, filter);

        mContentResolver.registerContentObserver(Events.CONTENT_URI,
                true, mObserver);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putLong(EVENT_BEGIN_TIME, mTime.toMillis(true));
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_BACK:
                finish();
                return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        MenuHelper.onPrepareOptionsMenu(this, menu);
        return super.onPrepareOptionsMenu(menu);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuHelper.onCreateOptionsMenu(menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        MenuHelper.onOptionsItemSelected(this, item, this);
        return super.onOptionsItemSelected(item);
    }
}
