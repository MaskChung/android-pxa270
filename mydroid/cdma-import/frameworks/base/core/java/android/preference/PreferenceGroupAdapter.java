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

package android.preference;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import android.os.Handler;
import android.preference.Preference.OnPreferenceChangeInternalListener;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Adapter;
import android.widget.BaseAdapter;
import android.widget.ListView;

/**
 * An adapter that returns the {@link Preference} contained in this group.
 * In most cases, this adapter should be the base class for any custom
 * adapters from {@link Preference#getAdapter()}.
 * <p>
 * This adapter obeys the
 * {@link Preference}'s adapter rule (the
 * {@link Adapter#getView(int, View, ViewGroup)} should be used instead of
 * {@link Preference#getView(ViewGroup)} if a {@link Preference} has an
 * adapter via {@link Preference#getAdapter()}).
 * <p>
 * This adapter also propagates data change/invalidated notifications upward.
 * <p>
 * This adapter does not include this {@link PreferenceGroup} in the returned
 * adapter, use {@link PreferenceCategoryAdapter} instead.
 * 
 * @see PreferenceCategoryAdapter
 */
class PreferenceGroupAdapter extends BaseAdapter implements OnPreferenceChangeInternalListener {
    
    private static final String TAG = "PreferenceGroupAdapter";

    /**
     * The group that we are providing data from.
     */
    private PreferenceGroup mPreferenceGroup;
    
    /**
     * Maps a position into this adapter -> {@link Preference}. These
     * {@link Preference}s don't have to be direct children of this
     * {@link PreferenceGroup}, they can be grand children or younger)
     */
    private List<Preference> mPreferenceList;
    
    /**
     * List of unique Preference and its subclasses' names. This is used to find
     * out how many types of views this adapter can return. Once the count is
     * returned, this cannot be modified (since the ListView only checks the
     * count once--when the adapter is being set). We will not recycle views for
     * Preference subclasses seen after the count has been returned.
     */
    private List<String> mPreferenceClassNames;

    /**
     * Blocks the mPreferenceClassNames from being changed anymore.
     */
    private boolean mHasReturnedViewTypeCount = false;
    
    private volatile boolean mIsSyncing = false;
    
    private Handler mHandler = new Handler(); 
    
    private Runnable mSyncRunnable = new Runnable() {
        public void run() {
            syncMyPreferences();
        }
    };

    public PreferenceGroupAdapter(PreferenceGroup preferenceGroup) {
        mPreferenceGroup = preferenceGroup;
        mPreferenceList = new ArrayList<Preference>();
        mPreferenceClassNames = new ArrayList<String>();
        
        syncMyPreferences();
    }

    private void syncMyPreferences() {
        synchronized(this) {
            if (mIsSyncing) {
                return;
            }
        
            mIsSyncing = true;
        }

        List<Preference> newPreferenceList = new ArrayList<Preference>(mPreferenceList.size());
        flattenPreferenceGroup(newPreferenceList, mPreferenceGroup);
        mPreferenceList = newPreferenceList;
        
        notifyDataSetChanged();

        synchronized(this) {
            mIsSyncing = false;
            notifyAll();
        }
    }
    
    private void flattenPreferenceGroup(List<Preference> preferences, PreferenceGroup group) {
        // TODO: shouldn't always?
        group.sortPreferences();

        final int groupSize = group.getPreferenceCount();
        for (int i = 0; i < groupSize; i++) {
            final Preference preference = group.getPreference(i);
            
            preferences.add(preference);
            
            if (!mHasReturnedViewTypeCount) {
                addPreferenceClassName(preference);
            }
            
            if (preference instanceof PreferenceGroup) {
                final PreferenceGroup preferenceAsGroup = (PreferenceGroup) preference;
                if (preferenceAsGroup.isOnSameScreenAsChildren()) {
                    flattenPreferenceGroup(preferences, preferenceAsGroup);
                    preference.setOnPreferenceChangeInternalListener(this);
                }
            } else {
                preference.setOnPreferenceChangeInternalListener(this);
            }
        }
    }

    private void addPreferenceClassName(Preference preference) {
        final String name = preference.getClass().getName();
        int insertPos = Collections.binarySearch(mPreferenceClassNames, name);
        
        // Only insert if it doesn't exist (when it is negative).
        if (insertPos < 0) {
            // Convert to insert index
            insertPos = insertPos * -1 - 1;
            mPreferenceClassNames.add(insertPos, name);
        }
    }
    
    public int getCount() {
        return mPreferenceList.size();
    }

    public Preference getItem(int position) {
        if (position < 0 || position >= getCount()) return null;
        return mPreferenceList.get(position);
    }

    public long getItemId(int position) {
        if (position < 0 || position >= getCount()) return ListView.INVALID_ROW_ID;
        return this.getItem(position).getId();
    }

    public View getView(int position, View convertView, ViewGroup parent) {
        final Preference preference = this.getItem(position);
        
        if (preference.hasSpecifiedLayout()) {
            // If the preference had specified a layout (as opposed to the
            // default), don't use convert views.
            convertView = null;
        } else {
            // TODO: better way of doing this
            final String name = preference.getClass().getName();
            if (Collections.binarySearch(mPreferenceClassNames, name) < 0) {
                convertView = null;
            }
        }
        
        return preference.getView(convertView, parent);
    }

    @Override
    public boolean isEnabled(int position) {
        if (position < 0 || position >= getCount()) return true;
        return this.getItem(position).isSelectable();
    }

    @Override
    public boolean areAllItemsEnabled() {
        // There should always be a preference group, and these groups are always
        // disabled
        return false;
    }

    public void onPreferenceChange(Preference preference) {
        notifyDataSetChanged();
    }

    public void onPreferenceHierarchyChange(Preference preference) {
        mHandler.removeCallbacks(mSyncRunnable);
        mHandler.post(mSyncRunnable);
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }

    @Override
    public int getItemViewType(int position) {
        if (!mHasReturnedViewTypeCount) {
            mHasReturnedViewTypeCount = true;
        }
        
        final Preference preference = this.getItem(position);
        if (preference.hasSpecifiedLayout()) {
            return IGNORE_ITEM_VIEW_TYPE;
        }

        final String name = preference.getClass().getName();
        int viewType = Collections.binarySearch(mPreferenceClassNames, name);
        if (viewType < 0) {
            // This is a class that was seen after we returned the count, so
            // don't recycle it.
            return IGNORE_ITEM_VIEW_TYPE;
        } else {
            return viewType;
        }
    }

    @Override
    public int getViewTypeCount() {
        if (!mHasReturnedViewTypeCount) {
            mHasReturnedViewTypeCount = true;
        }
        
        return mPreferenceClassNames.size();
    }

}
