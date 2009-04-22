/*
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

package com.android.settings;

import com.android.providers.subscribedfeeds.R;

import android.app.ActivityThread;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.ProviderInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.RemoteException;
import android.pim.DateFormat;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.PreferenceActivity;
import android.preference.PreferenceScreen;
import android.provider.Sync;
import android.provider.SubscribedFeeds;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Observable;
import java.util.Observer;

public class SyncSettings
        extends PreferenceActivity
        implements Observer {

    List<String>       mProviderNames;
    List<ProviderInfo> mProviderInfos;

    CheckBoxPreference mAutoSyncCheckBox;
    TextView mErrorInfoView;

    java.text.DateFormat mDateFormat;
    java.text.DateFormat mTimeFormat;

    private static final String SYNC_CONNECTION_SETTING_CHANGED
        = "com.android.sync.SYNC_CONN_STATUS_CHANGED";

    private static final String SYNC_KEY_PREFIX = "sync_";
    private static final String SYNC_CHECKBOX_KEY = "autoSyncCheckBox";

    Sync.Settings.QueryMap mSyncSettings;

    private static final int MENU_SYNC_NOW_ID = Menu.FIRST;
    private static final int MENU_SYNC_CANCEL_ID = Menu.FIRST + 1;

    private Sync.Active.QueryMap mActiveSyncQueryMap = null;
    private Sync.Status.QueryMap mStatusSyncQueryMap = null;

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        setContentView(R.layout.sync_settings_list_content);
        addPreferencesFromResource(R.xml.sync_settings);

        mErrorInfoView = (TextView)findViewById(R.id.sync_settings_error_info);
        mErrorInfoView.setVisibility(View.GONE);
        mErrorInfoView.setCompoundDrawablesWithIntrinsicBounds(
                getResources().getDrawable(R.drawable.ic_list_syncerror), null, null, null);

        mDateFormat = DateFormat.getDateFormat(this);
        mTimeFormat = DateFormat.getTimeFormat(this);

        mStatusSyncQueryMap = new Sync.Status.QueryMap(getContentResolver(),
                false /* don't keep updated yet, we will change this in onResume()/onPause() */,
                null /* use this thread's handler for notifications */);
        mStatusSyncQueryMap.addObserver(mSyncSuccessesObserver);

        mActiveSyncQueryMap = new Sync.Active.QueryMap(getContentResolver(),
                false /* don't keep updated yet, we will change this in onResume()/onPause() */,
                null /* use this thread's handler for notifications */);
        mActiveSyncQueryMap.addObserver(mSyncActiveObserver);

        mSyncSettings = new Sync.Settings.QueryMap(getContentResolver(),
                true /* keep updated */, null);
        mSyncSettings.addObserver(this);

        mAutoSyncCheckBox = (CheckBoxPreference) findPreference(SYNC_CHECKBOX_KEY);
        initProviders();
        initUI();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        menu.add(0, MENU_SYNC_NOW_ID, 0, getString(R.string.sync_menu_sync_now))
                .setIcon(R.drawable.ic_menu_refresh);
        menu.add(0, MENU_SYNC_CANCEL_ID, 0, getString(R.string.sync_menu_sync_cancel))
                .setIcon(android.R.drawable.ic_menu_close_clear_cancel);
        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        final boolean syncActive = mActiveSyncQueryMap.getActiveSyncInfo() != null;
        menu.findItem(MENU_SYNC_NOW_ID).setVisible(!syncActive);
        menu.findItem(MENU_SYNC_CANCEL_ID).setVisible(syncActive);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_SYNC_NOW_ID:
                startSyncForEnabledProviders();
                return true;
            case MENU_SYNC_CANCEL_ID:
                cancelSyncForEnabledProviders();
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void initProviders() {
        mProviderNames = new ArrayList<String>();
        mProviderInfos = new ArrayList<ProviderInfo>();

        try {
            ActivityThread.getPackageManager().querySyncProviders(mProviderNames,
                    mProviderInfos);
        } catch (RemoteException e) {
        }
        /*
        for (int i = 0; i < mProviderNames.size(); i++) {
            Log.i("SyncProviders", mProviderNames.get(i));
        }
        */
    }

    @Override
    protected void onResume() {
        super.onResume();
        mActiveSyncQueryMap.setKeepUpdated(true);
        mStatusSyncQueryMap.setKeepUpdated(true);
        onSyncStateUpdated();
    }

    @Override
    protected void onPause() {
        super.onPause();
        mActiveSyncQueryMap.setKeepUpdated(false);
        mStatusSyncQueryMap.setKeepUpdated(false);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mSyncSettings != null) {
            mSyncSettings.close();
            mSyncSettings = null;
        }
        if (mActiveSyncQueryMap != null) {
            mActiveSyncQueryMap.close();
            mActiveSyncQueryMap = null;
        }
        if (mStatusSyncQueryMap != null) {
            mStatusSyncQueryMap.close();
            mStatusSyncQueryMap = null;
        }
    }

    private void initUI() {
        // Set the Auto Sync toggle state
        CheckBoxPreference autoSync = (CheckBoxPreference) findPreference(SYNC_CHECKBOX_KEY);
        autoSync.setChecked(mSyncSettings.getListenForNetworkTickles());

        // Find individual sync provider's states and initialize the toggles
        for (int i = 0; i < getPreferenceScreen().getPreferenceCount(); i++) {
            Preference pref = getPreferenceScreen().getPreference(i);
            if (pref.hasKey() && pref.getKey().startsWith(SYNC_KEY_PREFIX)) {
                CheckBoxPreference toggle = (CheckBoxPreference) pref;
                String providerName = toggle.getKey().substring(SYNC_KEY_PREFIX.length());
                boolean enabled =
                    mSyncSettings.getSyncProviderAutomatically(providerName);
                toggle.setChecked(enabled);
            }
        }

    }

    public boolean onPreferenceTreeClick(PreferenceScreen preferences, Preference preference) {
        CheckBoxPreference togglePreference = (CheckBoxPreference) preference;
        String key = preference.getKey();
        if (key.equals(SYNC_CHECKBOX_KEY)) {
            boolean oldListenForTickles = mSyncSettings.getListenForNetworkTickles();
            boolean listenForTickles = togglePreference.isChecked();
            if (oldListenForTickles != listenForTickles) {
                mSyncSettings.setListenForNetworkTickles(listenForTickles);
                Intent intent = new Intent();
                intent.setAction(SYNC_CONNECTION_SETTING_CHANGED);
                sendBroadcast(intent);
                if (listenForTickles) {
                    startSyncForEnabledProviders();
                }
            }
            if (!listenForTickles) {
                cancelSyncForEnabledProviders();
            }
        } else if (key.startsWith(SYNC_KEY_PREFIX)) {
            String providerName = key.substring(SYNC_KEY_PREFIX.length());
            boolean syncOn = togglePreference.isChecked();

            boolean oldSyncState = mSyncSettings.getSyncProviderAutomatically(providerName);
            if (syncOn != oldSyncState) {
                mSyncSettings.setSyncProviderAutomatically(providerName, syncOn);
                if (syncOn) {
                    startSync(providerName);
                } else {
                    cancelSync(providerName);
                }
            }
        } else {
            return false;
        }
        return true;
    }

    private void startSyncForEnabledProviders() {
        cancelOrStartSyncForEnabledProviders(true /* start them */);
    }

    private void cancelSyncForEnabledProviders() {
        cancelOrStartSyncForEnabledProviders(false /* cancel them */);
    }

    private void cancelOrStartSyncForEnabledProviders(boolean startSync) {
        for (int i = 0; i < getPreferenceScreen().getPreferenceCount(); i++) {
            Preference pref = getPreferenceScreen().getPreference(i);
            if (pref.hasKey() && pref.getKey().startsWith(SYNC_KEY_PREFIX)) {
                CheckBoxPreference toggle = (CheckBoxPreference) pref;
                if (!toggle.isChecked()) {
                    continue;
                }
                final String authority = toggle.getKey().substring(SYNC_KEY_PREFIX.length());
                if (startSync) {
                    startSync(authority);
                } else {
                    cancelSync(authority);
                }
            }
        }

        // treat SubscribedFeeds as an enabled provider
        final String authority = SubscribedFeeds.Feeds.CONTENT_URI.getAuthority();
        if (startSync) {
            startSync(authority);
        } else {
            cancelSync(authority);
        }
    }

    private void startSync(String providerName) {
        Uri uriToSync = (providerName != null) ? Uri.parse("content://" + providerName) : null;
        Bundle extras = new Bundle();
        extras.putBoolean(ContentResolver.SYNC_EXTRAS_FORCE, true);
        getContentResolver().startSync(uriToSync, extras);
    }

    private void cancelSync(String authority) {
        getContentResolver().cancelSync(Uri.parse("content://" + authority));
    }

    private Observer mSyncSuccessesObserver = new Observer() {
        public void update(Observable o, Object arg) {
            onSyncStateUpdated();
        }
    };

    private Observer mSyncActiveObserver = new Observer() {
        public void update(Observable o, Object arg) {
            onSyncStateUpdated();
        }
    };

    /**
     * Returns the status row that matches the authority. If there are multiples accounts for
     * the authority, the row with the latest LAST_SUCCESS_TIME column is returned.
     * @param authority the authority whose row should be selected
     * @return the ContentValues for the authority, or null if none exists
     */
    private ContentValues getStatusByAuthority(String authority) {
        ContentValues row = null;
        Map<String, ContentValues> rows = mStatusSyncQueryMap.getRows();
        for (ContentValues values : rows.values()) {
            if (values.getAsString(Sync.Status.AUTHORITY).equals(authority)) {
                if (row == null) {
                    row = values;
                    continue;
                }
                final Long curTime = row.getAsLong(Sync.Status.LAST_SUCCESS_TIME);
                if (curTime == null) {
                    row = values;
                    continue;
                }
                final Long newTime = values.getAsLong(Sync.Status.LAST_SUCCESS_TIME);
                if (newTime == null) continue;
                if (newTime > curTime) {
                    row = values;
                }
            }
        }
        return row;
    }

    boolean isAuthorityPending(String authority) {
        Map<String, ContentValues> rows = mStatusSyncQueryMap.getRows();
        for (ContentValues values : rows.values()) {
            if (values.getAsString(Sync.Status.AUTHORITY).equals(authority)
                    && values.getAsLong(Sync.Status.PENDING) != 0) {
                return true;
            }
        }
        return false;
    }

    private void onSyncStateUpdated() {
        // iterate over all the preferences, setting the state properly for each
        Date date = new Date();
        ContentValues activeSyncValues = mActiveSyncQueryMap.getActiveSyncInfo();
        boolean syncIsFailing = false;
        for (int i = 0; i < getPreferenceScreen().getPreferenceCount(); i++) {
            Preference pref = getPreferenceScreen().getPreference(i);
            final String prefKey = pref.getKey();
            if (!TextUtils.isEmpty(prefKey) && prefKey.startsWith(SYNC_KEY_PREFIX)) {
                String authority = prefKey.substring(SYNC_KEY_PREFIX.length());
                SyncStateCheckBoxPreference toggle = (SyncStateCheckBoxPreference)pref;
                ContentValues status = getStatusByAuthority(authority);

                boolean syncEnabled = mSyncSettings.getSyncProviderAutomatically(authority);

                boolean authorityIsPending = isAuthorityPending(authority);
                boolean activelySyncing = activeSyncValues != null
                        && activeSyncValues.getAsString(Sync.Active.AUTHORITY).equals(authority);
                boolean lastSyncFailed = status != null
                        && status.getAsString(Sync.Status.LAST_FAILURE_MESG) != null
                        && status.getAsLong(Sync.Status.LAST_FAILURE_MESG)
                           != Sync.Status.ERROR_SYNC_ALREADY_IN_PROGRESS;
                if (!syncEnabled) lastSyncFailed = false;
                if (lastSyncFailed && !activelySyncing && !authorityIsPending) {
                    syncIsFailing = true;
                }
                final Long successEndTime =
                        status == null ? null : status.getAsLong(Sync.Status.LAST_SUCCESS_TIME);
                if (successEndTime != null) {
                    date.setTime(successEndTime);
                    final String timeString = mDateFormat.format(date) + " "
                            + mTimeFormat.format(date);
                    toggle.setSummary(timeString);
                } else {
                    toggle.setSummary("");
                }
                toggle.setActive(activelySyncing);
                toggle.setPending(authorityIsPending);
                toggle.setFailed(lastSyncFailed);
            }
        }
        mErrorInfoView.setVisibility(syncIsFailing ? View.VISIBLE : View.GONE);
    }

    /** called when the sync settings change */
    public void update(Observable o, Object arg) {
        onSyncStateUpdated();
    }
}
