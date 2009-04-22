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

package com.android.contacts;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.DialogInterface;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.Contacts;
import android.provider.Contacts.Groups;
import android.provider.Contacts.Settings;
import android.text.TextUtils;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import com.android.internal.app.AlertActivity;
import com.android.internal.app.AlertController;

public final class ContactsGroupSyncSelector extends AlertActivity implements 
        ListView.OnItemClickListener, DialogInterface.OnClickListener {

    private static final String[] PROJECTION = new String[] {
            Groups._ID, // 0
            Groups.NAME, // 1
            Groups.SHOULD_SYNC, // 2
            Groups.SYSTEM_ID, // 3
    };
    private static final int COLUMN_INDEX_ID = 0;
    private static final int COLUMN_INDEX_NAME = 1;
    private static final int COLUMN_INDEX_SHOULD_SYNC = 2;
    private static final int COLUMN_INDEX_SYSTEM_ID = 3;

    private ContentResolver mResolver;
    private ListView mListView;
    private GroupsAdapter mAdapter;

    boolean[] mChecked;
    boolean mSyncAllGroups;
    long[] mGroupIds;
    
    private final class GroupsAdapter extends ArrayAdapter<CharSequence> {
        public GroupsAdapter(CharSequence[] items) {
            super(new ContextThemeWrapper(ContactsGroupSyncSelector.this,
                    android.R.style.Theme_Light),
                    android.R.layout.simple_list_item_checked,
                    android.R.id.text1, items);
        }

        @Override
        public boolean areAllItemsEnabled() {
            return mSyncAllGroups; 
        }

        @Override
        public boolean isEnabled(int pos) {
            if (mSyncAllGroups && pos != 0) {
                return false;
            } else {
                return true;
            }
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View v = super.getView(position, convertView, parent);
            if (mSyncAllGroups && position != 0) {
                v.setEnabled(false);
            } else {
                v.setEnabled(true);
            }
            return v;
        }
    }

    /**
     * Handles clicks on the list items
     */
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        boolean isChecked = mListView.isItemChecked(position);
        mChecked[position] = isChecked;
        if (position == 0) {
            mSyncAllGroups = isChecked;
            adjustChecks();
        }
    }

    /**
     * Handles clicks on the OK button
     */
    public void onClick(DialogInterface dialog, int which) {
        if (mSyncAllGroups) {
            // For now we only support a single account and the UI doesn't know what
            // the account name is, so we're using a global setting for SYNC_EVERYTHING.
            // Some day when we add multiple accounts to the UI this should use the per
            // account setting.
            Settings.setSetting(mResolver, null, Settings.SYNC_EVERYTHING, "1");
        } else {
            final ContentResolver resolver = mResolver;
            ContentValues values = new ContentValues();
            int count = mChecked.length;
            for (int i = 1; i < count; i++) {
                values.clear();
                values.put(Groups.SHOULD_SYNC, mChecked[i]);
                resolver.update(ContentUris.withAppendedId(Groups.CONTENT_URI, mGroupIds[i]),
                        values, null, null);
            }
            // For now we only support a single account and the UI doesn't know what
            // the account name is, so we're using a global setting for SYNC_EVERYTHING.
            // Some day when we add multiple accounts to the UI this should use the per
            // account setting.
            Settings.setSetting(resolver, null, Settings.SYNC_EVERYTHING, "0");
        }
    }

    @Override
    protected void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        mResolver = getContentResolver();

        // Set the alert parameters
        AlertController.AlertParams params = mAlertParams;
        params.mTitle = getText(R.string.syncGroupChooserTitle);
        params.mIcon = getResources().getDrawable(R.drawable.ic_tab_unselected_contacts);
        params.mPositiveButtonText = getText(R.string.okButtonText);
        params.mPositiveButtonListener = this;
        params.mNegativeButtonText = getText(R.string.cancelButtonText);
        buildItems(params);

        // Takes the info in mAlertParams and creates the layout
        setupAlert();

        mListView = mAlert.getListView();
        mListView.setChoiceMode(ListView.CHOICE_MODE_MULTIPLE);
        mListView.setOnItemClickListener(this);
        adjustChecks();
    }

    private void buildItems(AlertController.AlertParams params) {
        Cursor cursor = mResolver.query(Groups.CONTENT_URI, PROJECTION, null, null, Groups.NAME);
        if (cursor != null) {
            try {
                int count = cursor.getCount() + 1;
                CharSequence[] items = new String[count];
                boolean[] checked = new boolean[count];
                long[] groupIds = new long[count];
    
                int i = 0;
                items[i++] = getString(R.string.syncAllGroups);
                items[i++] = getString(R.string.groupNameMyContacts);
    
                while (cursor.moveToNext()) {
                    String name = cursor.getString(COLUMN_INDEX_NAME);
                    String systemId = cursor.isNull(COLUMN_INDEX_SYSTEM_ID) ?
                            null : cursor.getString(COLUMN_INDEX_SYSTEM_ID);
                    if (systemId == null || !Groups.GROUP_MY_CONTACTS.equals(systemId)) {
                        items[i] = name;
                        checked[i] = cursor.getInt(COLUMN_INDEX_SHOULD_SYNC) != 0;
                        groupIds[i] = cursor.getLong(COLUMN_INDEX_ID);
                        i++;
                    } else {
                        checked[1] = cursor.getInt(COLUMN_INDEX_SHOULD_SYNC) != 0;
                        groupIds[1] = cursor.getLong(COLUMN_INDEX_ID);
                    }
                }
                mChecked = checked;
                mSyncAllGroups = getShouldSyncEverything(mResolver);
                checked[0] = mSyncAllGroups;
                mGroupIds = groupIds;
    
                // Setup the adapter
                mAdapter = new GroupsAdapter(items);
                params.mAdapter = mAdapter;
            } finally {
                cursor.close();
            }
        }
    }

    private void adjustChecks() {
        ListView list = mListView;
        if (mSyncAllGroups) {
            int count = list.getCount();
            for (int i = 0; i < count; i++) {
                list.setItemChecked(i, true);
            }
        } else {
            boolean[] checked = mChecked;
            int count = list.getCount();
            for (int i = 0; i < count; i++) {
                list.setItemChecked(i, checked[i]);
            }
        }
    }

    private static boolean getShouldSyncEverything(ContentResolver cr) {
        // For now we only support a single account and the UI doesn't know what
        // the account name is, so we're using a global setting for SYNC_EVERYTHING.
        // Some day when we add multiple accounts to the UI this should use the per
        // account setting.
        String value = Contacts.Settings.getSetting(cr, null, Contacts.Settings.SYNC_EVERYTHING);
        if (value == null) {
            // If nothing is set yet we default to syncing everything
            return true;
        }
        return !TextUtils.isEmpty(value) && !"0".equals(value);
    }
}
