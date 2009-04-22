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

import android.app.Activity;
import android.app.AlertDialog;
import android.content.AsyncQueryHandler;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.DialogInterface;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Calendar.Calendars;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.MenuItem.OnMenuItemClickListener;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.ListView;


public class SelectCalendarsActivity extends Activity implements ListView.OnItemClickListener {

    private static final String TAG = "Calendar";
    private View mView = null;
    private Cursor mCursor = null;
    private QueryHandler mQueryHandler;
    private SelectCalendarsAdapter mAdapter;
    private static final String[] PROJECTION = new String[] {
        Calendars._ID,
        Calendars.DISPLAY_NAME,
        Calendars.COLOR,
        Calendars.SELECTED,
        Calendars.SYNC_EVENTS
    };

    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.calendars_activity);
        getWindow().setFeatureInt(Window.FEATURE_INDETERMINATE_PROGRESS,
                Window.PROGRESS_INDETERMINATE_ON);
        mQueryHandler = new QueryHandler(getContentResolver());
        mView = findViewById(R.id.calendars);
        ListView items = (ListView) mView.findViewById(R.id.items);
        Context context = mView.getContext();
        mCursor = managedQuery(Calendars.CONTENT_URI, PROJECTION,
                Calendars.SYNC_EVENTS + "=1",
                null /* selectionArgs */,
                Calendars.DEFAULT_SORT_ORDER);
                                     
        mAdapter = new SelectCalendarsAdapter(context, mCursor);
        items.setAdapter(mAdapter);
        items.setOnItemClickListener(this);
        
        // Start a background sync to get the list of calendars from the server.
        startCalendarSync();
    }
    
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        CheckBox box = (CheckBox) view.findViewById(R.id.checkbox);
        box.toggle();
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        MenuItem item;
        item = menu.add(0, 0, 0, R.string.add_calendars)
                .setOnMenuItemClickListener(new ChangeCalendarAction(false /* not remove */));
        item.setIcon(android.R.drawable.ic_menu_add);
        
        item = menu.add(0, 0, 0, R.string.remove_calendars)
                .setOnMenuItemClickListener(new ChangeCalendarAction(true /* remove */));
        item.setIcon(android.R.drawable.ic_menu_delete);
        return true;
    }

    /**
     * ChangeCalendarAction is used both for adding and removing calendars.
     * The constructor takes a boolean argument that is false if adding
     * calendars and true if removing calendars.  The user selects calendars
     * to be added or removed from a pop-up list. 
     */
    public class ChangeCalendarAction implements OnMenuItemClickListener,
            DialogInterface.OnClickListener, DialogInterface.OnMultiChoiceClickListener {
        
        int mNumItems;
        long[] mCalendarIds;
        boolean[] mIsChecked;
        ContentResolver mContentResolver;
        boolean mRemove;
        
        public ChangeCalendarAction(boolean remove) {
            mContentResolver = SelectCalendarsActivity.this.getContentResolver();
            mRemove = remove;
        }

        /*
         * This is called when the user selects a calendar from either the
         * "Add calendars" or "Remove calendars" popup dialog. 
         */
        public void onClick(DialogInterface dialog, int position, boolean isChecked) {
            mIsChecked[position] = isChecked;
        }

        /*
         * This is called when the user presses the OK or Cancel button on the
         * "Add calendars" or "Remove calendars" popup dialog. 
         */
        public void onClick(DialogInterface dialog, int which) {
            // If the user cancelled the dialog, then do nothing.
            if (which == DialogInterface.BUTTON2) {
                return;
            }
            
            boolean changesFound = false;
            for (int position = 0; position < mNumItems; position++) {
                // If this calendar wasn't selected, then skip it.
                if (!mIsChecked[position]) {
                    continue;
                }
                changesFound = true;
                
                long id = mCalendarIds[position];
                Uri uri = ContentUris.withAppendedId(Calendars.CONTENT_URI, id);
                ContentValues values = new ContentValues();
                int selected = 1;
                if (mRemove) {
                    selected = 0;
                }
                values.put(Calendars.SELECTED, selected);
                values.put(Calendars.SYNC_EVENTS, selected);
                mContentResolver.update(uri, values, null, null);
            }
            
            // If there were any changes, then update the list of calendars
            // that are synced.
            if (changesFound) {
                mCursor.requery();
            }
        }
        
        public boolean onMenuItemClick(MenuItem item) {
            AlertDialog.Builder builder = new AlertDialog.Builder(SelectCalendarsActivity.this);
            String selection;
            if (mRemove) {
                builder.setTitle(R.string.remove_calendars)
                    .setIcon(android.R.drawable.ic_dialog_alert);
                selection = Calendars.SYNC_EVENTS + "=1";
            } else {
                builder.setTitle(R.string.add_calendars);
                selection = Calendars.SYNC_EVENTS + "=0";
            }
            ContentResolver cr = getContentResolver();
            Cursor cursor = cr.query(Calendars.CONTENT_URI, PROJECTION,
                    selection, null /* selectionArgs */,
                    Calendars.DEFAULT_SORT_ORDER);
            if (cursor == null) {
                Log.w(TAG, "Cannot get cursor for calendars");
                return true;
            }

            int count = cursor.getCount();
            mNumItems = count;
            CharSequence[] calendarNames = new CharSequence[count];
            mCalendarIds = new long[count];
            mIsChecked = new boolean[count];
            try {
                int pos = 0;
                while (cursor.moveToNext()) {
                    mCalendarIds[pos] = cursor.getLong(0);
                    calendarNames[pos] = cursor.getString(1);
                    pos += 1;
                }
            } finally {
                cursor.close();
            }
            
            builder.setMultiChoiceItems(calendarNames, null, this)
                .setPositiveButton(R.string.ok_label, this)
                .setNegativeButton(R.string.cancel_label, this)
                .show();
            return true;
        }
    }
    
    private class QueryHandler extends AsyncQueryHandler {
        public QueryHandler(ContentResolver cr) {
            super(cr);
        }

        @Override
        protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
            getWindow().setFeatureInt(Window.FEATURE_INDETERMINATE_PROGRESS,
                    Window.PROGRESS_VISIBILITY_OFF);

            // If the Activity is finishing, then close the cursor.
            // Otherwise, use the new cursor in the adapter.
            if (isFinishing()) {
                stopManagingCursor(cursor);
                cursor.close();
            } else {
                if (cursor.getCount() == 0) {
                    // There are no calendars.  This might happen if we lost
                    // the wireless connection (in airplane mode, for example).
                    // Leave the current list of calendars alone and pop up
                    // a dialog explaining that the connection is down.
                    // But allow the user to add and remove calendars.
                    return;
                }
                if (mCursor != null) {
                    stopManagingCursor(mCursor);
                }
                mCursor = cursor;
                startManagingCursor(cursor);
                mAdapter.changeCursor(cursor);
            }
        }
    }

    // This class implements the menu option "Refresh list from server".
    // (No longer used.)
    public class RefreshAction implements Runnable {
        public void run() {
            startCalendarSync();
        }
    }
    
    // startCalendarSync() checks the server for an updated list of Calendars
    // (in the background) using an AsyncQueryHandler.
    //
    // Calendars are never removed from the phone due to a server sync.
    // But if a Calendar is added on the web (and it is selected and not
    // hidden) then it will be added to the list of calendars on the phone
    // (when this asynchronous query finishes).  When a new calendar from the
    // web is added to the phone, then the events for that calendar are also
    // downloaded from the web.
    // 
    // This sync is done automatically in the background when the
    // SelectCalendars activity is started.
    private void startCalendarSync() {
        getWindow().setFeatureInt(Window.FEATURE_INDETERMINATE_PROGRESS,
                Window.PROGRESS_VISIBILITY_ON);

        // TODO: make sure the user has login info.
        
        Uri uri = Calendars.LIVE_CONTENT_URI;
        mQueryHandler.startQuery(0, null, uri, PROJECTION,
                Calendars.SYNC_EVENTS + "=1",
                null, Calendars.DEFAULT_SORT_ORDER);
    }
}
