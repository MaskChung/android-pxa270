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

package com.android.contacts;

import android.app.ListActivity;
import android.content.ActivityNotFoundException;
import android.content.AsyncQueryHandler;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.pim.DateUtils;
import android.provider.CallLog.Calls;
import android.provider.Contacts.People;
import android.provider.Contacts.Phones;
import android.provider.Contacts.Intents.Insert;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.ContextMenu;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.AdapterView;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ResourceCursorAdapter;
import android.widget.TextView;

import com.android.internal.telephony.CallerInfo;
import com.android.internal.telephony.ITelephony;

import java.util.HashMap;
import java.util.LinkedList;

/**
 * Displays a list of call log entries.
 */
public class RecentCallsListActivity extends ListActivity 
        implements View.OnCreateContextMenuListener {
    private static final String TAG = "RecentCallsList";
    
    /** The projection to use when querying the call log table */
    static final String[] CALL_LOG_PROJECTION = new String[] {
            Calls._ID,
            Calls.NUMBER,
            Calls.DATE,
            Calls.DURATION,
            Calls.TYPE,
            Calls.CACHED_NAME,
            Calls.CACHED_NUMBER_TYPE,
            Calls.CACHED_NUMBER_LABEL
    };
    
    static final int ID_COLUMN_INDEX = 0;
    static final int NUMBER_COLUMN_INDEX = 1;
    static final int DATE_COLUMN_INDEX = 2;
    static final int DURATION_COLUMN_INDEX = 3;
    static final int CALL_TYPE_COLUMN_INDEX = 4;
    static final int CALLER_NAME_COLUMN_INDEX = 5;
    static final int CALLER_NUMBERTYPE_COLUMN_INDEX = 6;
    static final int CALLER_NUMBERLABEL_COLUMN_INDEX = 7;

    /** The projection to use when querying the phones table */
    static final String[] PHONES_PROJECTION = new String[] {
            Phones.PERSON_ID,
            Phones.NAME,
            Phones.TYPE,
            Phones.LABEL,
            Phones.NUMBER
    };
    
    static final int PERSON_ID_COLUMN_INDEX = 0;
    static final int NAME_COLUMN_INDEX = 1;
    static final int PHONE_TYPE_COLUMN_INDEX = 2;
    static final int LABEL_COLUMN_INDEX = 3;
    static final int MATCHED_NUMBER_COLUMN_INDEX = 4;

    private static final int MENU_ITEM_DELETE = 1;
    private static final int MENU_ITEM_DELETE_ALL = 2;
    private static final int MENU_ITEM_VIEW_CONTACTS = 3;
    
    private static final int QUERY_TOKEN = 53;
    private static final int UPDATE_TOKEN = 54;

    private RecentCallsAdapter mAdapter;
    private QueryHandler mQueryHandler;
    private String mVoiceMailNumber;
    
    private CharSequence[] mLabelArray;
    
    private Drawable mDrawableIncoming;
    private Drawable mDrawableOutgoing;
    private Drawable mDrawableMissed;    
    
    private static final class ContactInfo {
        public long personId;
        public String name;
        public int type;
        public String label;
        public String number;

        public static ContactInfo EMPTY = new ContactInfo();
    }

    public static final class RecentCallsListItemViews {
        TextView line1View;
        TextView line2View;
        TextView durationView;
        TextView dateView;
        ImageView iconView;
    }    
    
    private static final class CallerInfoQuery {
        String number;
        int position;
        String name;
        int numberType;
        String numberLabel;
    }

    /** Adapter class to fill in data for the Call Log */
    private final class RecentCallsAdapter extends ResourceCursorAdapter 
            implements Runnable, ViewTreeObserver.OnPreDrawListener {
        HashMap<String,ContactInfo> mContactInfo;
        private LinkedList<CallerInfoQuery> mRequests;
        private boolean mDone;
        private boolean mLoading = true;
        ViewTreeObserver.OnPreDrawListener mPreDrawListener;
        private static final int REDRAW = 1;
        private static final int START_THREAD = 2;
        private boolean mFirst;
        
        public boolean onPreDraw() {
            if (mFirst) {
                mHandler.sendEmptyMessageDelayed(START_THREAD, 1000);
                mFirst = false;
            }
            return true;            
        }
        
        private Handler mHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case REDRAW:
                        notifyDataSetChanged();
                        break;
                    case START_THREAD:
                        startRequestProcessing();
                        break;
                }
            }
        };
        
        public RecentCallsAdapter() {
            super(RecentCallsListActivity.this, R.layout.recent_calls_list_item, null);

            mContactInfo = new HashMap<String,ContactInfo>();
            mRequests = new LinkedList<CallerInfoQuery>();
            mPreDrawListener = null;
        }

        void setLoading(boolean loading) {
            mLoading = loading;
        }
        
        @Override
        public boolean isEmpty() {
            if (mLoading) {
                // We don't want the empty state to show when loading.
                return false;
            } else {
                return super.isEmpty();
            }
        }

        public ContactInfo getContactInfo(String number) {
            return mContactInfo.get(number);
        }

        public void startRequestProcessing() {
            mDone = false;
            Thread callerIdThread = new Thread(this);
            callerIdThread.setPriority(Thread.MIN_PRIORITY);
            callerIdThread.start();
        }

        public void stopRequestProcessing() {
            mDone = true;
        }

        public void clearCache() {
            synchronized (mContactInfo) {
                mContactInfo.clear();
            }
        }

        private void updateCallLog(CallerInfoQuery ciq, ContactInfo ci) {
            // Check if they are different. If not, don't update.
            if (TextUtils.equals(ciq.name, ci.name) 
                    && TextUtils.equals(ciq.numberLabel, ci.label)
                    && ciq.numberType == ci.type) {
                return;
            }
            ContentValues values = new ContentValues(3);
            values.put(Calls.CACHED_NAME, ci.name);
            values.put(Calls.CACHED_NUMBER_TYPE, ci.type);
            values.put(Calls.CACHED_NUMBER_LABEL, ci.label);
            RecentCallsListActivity.this.getContentResolver().update(
                    Calls.CONTENT_URI, 
                    values, Calls.NUMBER + "='" + ciq.number + "'", null);
        }
        
        private void enqueueRequest(String number, int position,
                String name, int numberType, String numberLabel) {
            CallerInfoQuery ciq = new CallerInfoQuery();
            ciq.number = number;
            ciq.position = position;
            ciq.name = name;
            ciq.numberType = numberType;
            ciq.numberLabel = numberLabel;
            synchronized (mRequests) {
                mRequests.add(ciq);
                mRequests.notifyAll();
            }
        }
        
        private void queryContactInfo(CallerInfoQuery ciq) {
            // First check if there was a prior request for the same number
            // that was already satisfied
            ContactInfo info = mContactInfo.get(ciq.number);
            if (info != null && info != ContactInfo.EMPTY) {
                synchronized (mRequests) {
                    if (mRequests.isEmpty()) {
                        mHandler.sendEmptyMessage(REDRAW);
                    }
                }
            } else {
                Cursor phonesCursor = 
                    RecentCallsListActivity.this.getContentResolver().query(
                            Uri.withAppendedPath(Phones.CONTENT_FILTER_URL, 
                                    ciq.number),
                    PHONES_PROJECTION, null, null, null);
                if (phonesCursor != null) {
                    if (phonesCursor.moveToFirst()) {
                        info = new ContactInfo();
                        info.personId = phonesCursor.getLong(PERSON_ID_COLUMN_INDEX);
                        info.name = phonesCursor.getString(NAME_COLUMN_INDEX);
                        info.type = phonesCursor.getInt(PHONE_TYPE_COLUMN_INDEX);
                        info.label = phonesCursor.getString(LABEL_COLUMN_INDEX);
                        info.number = phonesCursor.getString(MATCHED_NUMBER_COLUMN_INDEX);
                        
                        mContactInfo.put(ciq.number, info);
                        // Inform list to update this item, if in view
                        synchronized (mRequests) {
                            if (mRequests.isEmpty()) {
                                mHandler.sendEmptyMessage(REDRAW);
                            }
                        }
                    }
                    phonesCursor.close();
                }
            }
            if (info != null) {
                updateCallLog(ciq, info);
            }
        }

        /*
         * Handles requests for contact name and number type
         * @see java.lang.Runnable#run()
         */
        public void run() {
            while (!mDone) {
                CallerInfoQuery ciq = null;
                synchronized (mRequests) {
                    if (!mRequests.isEmpty()) {
                        ciq = mRequests.removeFirst();
                    } else {
                        try {
                            mRequests.wait(1000);
                        } catch (InterruptedException ie) {
                            // Ignore and continue processing requests
                        }                        
                    }
                }
                if (ciq != null) {
                    queryContactInfo(ciq);
                }
            }
        }
        
        @Override
        public View newView(Context context, Cursor cursor, ViewGroup parent) {
            View view = super.newView(context, cursor, parent);
            
            // Get the views to bind to
            RecentCallsListItemViews views = new RecentCallsListItemViews();
            views.line1View = (TextView) view.findViewById(R.id.line1);
            views.line2View = (TextView) view.findViewById(R.id.line2);
            views.durationView = (TextView) view.findViewById(R.id.duration);
            views.dateView = (TextView) view.findViewById(R.id.date);
            views.iconView = (ImageView) view.findViewById(R.id.call_type_icon);

            view.setTag(views);

            return view;
        }

        
        @Override
        public void bindView(View view, Context context, Cursor c) {
            final RecentCallsListItemViews views = (RecentCallsListItemViews) view.getTag();

            String number = c.getString(NUMBER_COLUMN_INDEX);
            String callerName = c.getString(CALLER_NAME_COLUMN_INDEX);
            int callerNumberType = c.getInt(CALLER_NUMBERTYPE_COLUMN_INDEX);
            String callerNumberLabel = c.getString(CALLER_NUMBERLABEL_COLUMN_INDEX);
            
            // Lookup contacts with this number
            ContactInfo info = mContactInfo.get(number);
            if (info == null) {
                // Mark it as empty and queue up a request to find the name
                // The db request should happen on a non-UI thread
                info = ContactInfo.EMPTY;
                mContactInfo.put(number, info);
                enqueueRequest(number, c.getPosition(),
                        callerName, callerNumberType, callerNumberLabel);
            } else if (info != ContactInfo.EMPTY) { // Has been queried
                // Check if any data is different from the data cached in the
                // calls db. If so, queue the request so that we can update
                // the calls db.
                if (!TextUtils.equals(info.name, callerName) 
                        || info.type != callerNumberType
                        || !TextUtils.equals(info.label, callerNumberLabel)) {
                    // Something is amiss, so sync up.
                    enqueueRequest(number, c.getPosition(), 
                            callerName, callerNumberType, callerNumberLabel);
                }
            }

            String name = info.name;
            int ntype = info.type;
            String label = info.label;
            // If there's no name cached in our hashmap, but there's one in the
            // calls db, use the one in the calls db. Otherwise the name in our
            // hashmap is more recent, so it has precedence.
            if (TextUtils.isEmpty(name) && !TextUtils.isEmpty(callerName)) {
                name = callerName;
                ntype = callerNumberType;
                label = callerNumberLabel;
            }
            // Set the text lines
            if (!TextUtils.isEmpty(name)) {
                views.line1View.setText(name);
                CharSequence numberLabel = Phones.getDisplayLabel(context, ntype, label, 
                        mLabelArray);
                if (!TextUtils.isEmpty(numberLabel)) {
                    views.line2View.setText(numberLabel);
                } else {
                    views.line2View.setText(number);
                }

                // Set the presence icon
/*
                int serverStatus;
                if (!c.isNull(SERVER_STATUS_COLUMN_INDEX)) {
                    serverStatus = c.getInt(SERVER_STATUS_COLUMN_INDEX);
                    views.line2View.setCompoundDrawablesWithIntrinsicBounds(
                            getResources().getDrawable(
                                    Presence.getPresenceIconResourceId(serverStatus)),
                            null, null, null);
                } else {
                    views.line2View.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null);
                }
*/
            } else {
                if (number.equals(CallerInfo.UNKNOWN_NUMBER)) {
                    number = getString(R.string.unknown);
                } else if (number.equals(CallerInfo.PRIVATE_NUMBER)) {
                    number = getString(R.string.private_num);
                } else if (number.equals(mVoiceMailNumber)) {
                    number = getString(R.string.voicemail);
                }

                views.line1View.setText(number);
                views.line2View.setText(null);

                // Clear the presence icon
//                views.line2View.setCompoundDrawablesWithIntrinsicBounds(null, null, null, null);
            }

            int type = c.getInt(CALL_TYPE_COLUMN_INDEX);
            long date = c.getLong(DATE_COLUMN_INDEX);

            // Set the duration
            if (type == Calls.MISSED_TYPE) {
                views.durationView.setVisibility(View.GONE);
            } else {
                views.durationView.setVisibility(View.VISIBLE);
                views.durationView.setText(DateUtils.formatElapsedTime(c.getLong(DURATION_COLUMN_INDEX)));
            }

            // Set the time and date
            views.dateView.setText(DateUtils.getRelativeTimeSpanString(date));

            // Set the icon
            switch (type) {
                case Calls.INCOMING_TYPE:
                    views.iconView.setImageDrawable(mDrawableIncoming);
                    break;

                case Calls.OUTGOING_TYPE:
                    views.iconView.setImageDrawable(mDrawableOutgoing);
                    break;

                case Calls.MISSED_TYPE:
                    views.iconView.setImageDrawable(mDrawableMissed);
                    break;
            }
            // Listen for the first draw 
            if (mPreDrawListener == null) {
                mFirst = true;
                mPreDrawListener = this;
                view.getViewTreeObserver().addOnPreDrawListener(this);
            }
        }
    }
    
    private final class QueryHandler extends AsyncQueryHandler {
        public QueryHandler(Context context) {
            super(context.getContentResolver());
        }

        @Override
        protected void onQueryComplete(int token, Object cookie, Cursor cursor) {
            if (!isFinishing()) {
                mAdapter.setLoading(false);
                mAdapter.changeCursor(cursor);    
            } else {
                cursor.close();
            }
        }
    }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);

        setContentView(R.layout.recent_calls);
        
        mDrawableIncoming = getResources().getDrawable(android.R.drawable.sym_call_incoming);
        mDrawableOutgoing = getResources().getDrawable(android.R.drawable.sym_call_outgoing);
        mDrawableMissed = getResources().getDrawable(android.R.drawable.sym_call_missed);
        mLabelArray = getResources().getTextArray(com.android.internal.R.array.phoneTypes);
        
        // Typing here goes to the dialer
        setDefaultKeyMode(DEFAULT_KEYS_DIALER);

        mAdapter = new RecentCallsAdapter();
        getListView().setOnCreateContextMenuListener(this);
        setListAdapter(mAdapter);

        mVoiceMailNumber = ((TelephonyManager)getSystemService(Context.TELEPHONY_SERVICE))
                .getVoiceMailNumber();
        mQueryHandler = new QueryHandler(this);
    }

    @Override
    protected void onResume() {
        // The adapter caches looked up numbers, clear it so they will get
        // looked up again.
        if (mAdapter != null) {
            mAdapter.clearCache();
        }

        startQuery();
        resetNewCallsFlag();
        
        super.onResume();
        try {
            ITelephony.Stub.asInterface(ServiceManager.getService("phone"))
                    .cancelMissedCallsNotification();
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to clear missed calls notification due to remote excetpion");
        }
        mAdapter.mPreDrawListener = null; // Let it restart the thread after next draw
    }
    
    @Override
    protected void onPause() {
        super.onPause();
        
        // Kill the requests thread
        mAdapter.stopRequestProcessing();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Cursor cursor = mAdapter.getCursor();
        if (cursor != null && !cursor.isClosed()) {
            cursor.close();
        }
    }

    private void resetNewCallsFlag() {
        // Mark all "new" missed calls as not new anymore
        StringBuilder where = new StringBuilder("type=");
        where.append(Calls.MISSED_TYPE);
        where.append(" AND new=1");

        ContentValues values = new ContentValues(1);
        values.put(Calls.NEW, "0");
        mQueryHandler.startUpdate(UPDATE_TOKEN, null, Calls.CONTENT_URI, 
                values, where.toString(), null);
    }

    private void startQuery() {
        mAdapter.setLoading(true);
        
        // Cancel any pending queries
        mQueryHandler.cancelOperation(QUERY_TOKEN);
        mQueryHandler.startQuery(QUERY_TOKEN, null, Calls.CONTENT_URI, 
                CALL_LOG_PROJECTION, null, null, Calls.DEFAULT_SORT_ORDER);
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, MENU_ITEM_DELETE_ALL, 0, R.string.recentCalls_deleteAll)
                .setIcon(android.R.drawable.ic_menu_close_clear_cancel);
        return true;
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View view, ContextMenuInfo menuInfoIn) {
        AdapterView.AdapterContextMenuInfo menuInfo;
        try {
             menuInfo = (AdapterView.AdapterContextMenuInfo) menuInfoIn;
        } catch (ClassCastException e) {
            Log.e(TAG, "bad menuInfoIn", e);
            return;
        }

        Cursor cursor = (Cursor) mAdapter.getItem(menuInfo.position);

        String number = cursor.getString(NUMBER_COLUMN_INDEX);
        Uri numberUri = null;
        boolean isVoicemail = false;
        if (number.equals(CallerInfo.UNKNOWN_NUMBER)) {
            number = getString(R.string.unknown);
        } else if (number.equals(CallerInfo.PRIVATE_NUMBER)) {
            number = getString(R.string.private_num);
        } else if (number.equals(mVoiceMailNumber)) {
            number = getString(R.string.voicemail);
            numberUri = Uri.parse("voicemail:x");
            isVoicemail = true;
        } else {
            numberUri = Uri.fromParts("tel", number, null);
        }

        ContactInfo info = mAdapter.getContactInfo(number);
        boolean contactInfoPresent = (info != null && info != ContactInfo.EMPTY); 
        if (contactInfoPresent) {
            menu.setHeaderTitle(info.name);
        } else {
            menu.setHeaderTitle(number);
        }

        if (numberUri != null) {
            Intent intent = new Intent(Intent.ACTION_CALL_PRIVILEGED, numberUri);
            menu.add(0, 0, 0, getResources().getString(R.string.recentCalls_callNumber, number))
                    .setIntent(intent);
        }

        if (contactInfoPresent) {
            menu.add(0, 0, 0, R.string.menu_viewContact)
                    .setIntent(new Intent(Intent.ACTION_VIEW,
                            ContentUris.withAppendedId(People.CONTENT_URI, info.personId)));
        }

        if (numberUri != null && !isVoicemail) {
            menu.add(0, 0, 0, R.string.recentCalls_editNumberBeforeCall)
                    .setIntent(new Intent(Intent.ACTION_DIAL, numberUri));
            menu.add(0, 0, 0, R.string.menu_sendTextMessage)
                    .setIntent(new Intent(Intent.ACTION_SENDTO,
                            Uri.fromParts("sms", number, null)));
        }
        if (!contactInfoPresent && numberUri != null && !isVoicemail) {
            Intent intent = new Intent(Intent.ACTION_INSERT_OR_EDIT);
            intent.setType(People.CONTENT_ITEM_TYPE);
            intent.putExtra(Insert.PHONE, number);
            menu.add(0, 0, 0, R.string.recentCalls_addToContact)
                    .setIntent(intent);
        }
        menu.add(0, MENU_ITEM_DELETE, 0, R.string.recentCalls_removeFromRecentList);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case MENU_ITEM_DELETE_ALL: {
                getContentResolver().delete(Calls.CONTENT_URI, null, null);
                //TODO The change notification should do this automatically, but it isn't working
                // right now. Remove this when the change notification is working properly. 
                startQuery();
                return true;
            }

            case MENU_ITEM_VIEW_CONTACTS: {
                Intent intent = new Intent(Intent.ACTION_VIEW, People.CONTENT_URI);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
                return true;
            }
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        // Convert the menu info to the proper type
        AdapterView.AdapterContextMenuInfo menuInfo;
        try {
             menuInfo = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        } catch (ClassCastException e) {
            Log.e(TAG, "bad menuInfoIn", e);
            return false;
        }

        switch (item.getItemId()) {
            case MENU_ITEM_DELETE: {
                Cursor cursor = mAdapter.getCursor();
                if (cursor != null) {
                    cursor.moveToPosition(menuInfo.position);
                    cursor.deleteRow();
                }
                return true;
            }
        }
        return super.onContextItemSelected(item);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_CALL: {
                long callPressDiff = SystemClock.uptimeMillis() - event.getDownTime();
                if (callPressDiff >= ViewConfiguration.getLongPressTimeout()) {
                    // Launch voice dialer
                    Intent intent = new Intent(Intent.ACTION_VOICE_COMMAND);
                    intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    try {
                        startActivity(intent);
                    } catch (ActivityNotFoundException e) {
                    }
                    return true;
                }
            }
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_CALL:
                try {
                    ITelephony phone = ITelephony.Stub.asInterface(
                            ServiceManager.checkService("phone"));
                    if (phone != null && !phone.isIdle()) {
                        // Let the super class handle it
                        break;
                    }
                } catch (RemoteException re) {
                    // Fall through and try to call the contact
                }
                
                callEntry(getListView().getSelectedItemPosition());
                return true;
        }
        return super.onKeyUp(keyCode, event);
    }
    
    /*
     * Get the number from the Contacts, if available, since sometimes
     * the number provided by caller id may not be formatted properly
     * depending on the carrier (roaming) in use at the time of the 
     * incoming call.
     * Logic : If the caller-id number starts with a "+", use it
     *         Else if the number in the contacts starts with a "+", use that one
     *         Else if the number in the contacts is longer, use that one
     */
    private String getBetterNumberFromContacts(String number) {
        String matchingNumber = null;
        // Look in the cache first. If it's not found then query the Phones db
        ContactInfo ci = mAdapter.mContactInfo.get(number);
        if (ci != null && ci != ContactInfo.EMPTY) {
            matchingNumber = ci.number;
        } else {
            try {
                Cursor phonesCursor = 
                    RecentCallsListActivity.this.getContentResolver().query(
                            Uri.withAppendedPath(Phones.CONTENT_FILTER_URL, 
                                    number),
                    PHONES_PROJECTION, null, null, null);
                if (phonesCursor != null) {
                    if (phonesCursor.moveToFirst()) {
                        matchingNumber = phonesCursor.getString(MATCHED_NUMBER_COLUMN_INDEX);
                    }
                    phonesCursor.close();
                }
            } catch (Exception e) {
                // Use the number from the call log
            }
        }
        if (!TextUtils.isEmpty(matchingNumber) && 
                (matchingNumber.startsWith("+") 
                        || matchingNumber.length() > number.length())) {
            number = matchingNumber;
        }
        return number;
    }
    
    private void callEntry(int position) {
        if (position < 0) {
            // In touch mode you may often not have something selected, so
            // just call the first entry to make sure that [send] [send] calls the
            // most recent entry.
            position = 0;
        }
        final Cursor cursor = mAdapter.getCursor();
        if (cursor != null && cursor.moveToPosition(position)) {
            String number = cursor.getString(NUMBER_COLUMN_INDEX);
            if (TextUtils.isEmpty(number)
                    || number.equals(CallerInfo.UNKNOWN_NUMBER)
                    || number.equals(CallerInfo.PRIVATE_NUMBER)) {
                // This number can't be called, do nothing
                return;
            }

            int callType = cursor.getInt(CALL_TYPE_COLUMN_INDEX);
            if (!number.startsWith("+") && 
                    (callType == Calls.INCOMING_TYPE 
                            || callType == Calls.MISSED_TYPE)) {
                // If the caller-id matches a contact with a better qualified number, use it
                number = getBetterNumberFromContacts(number);
            }
            Intent intent = new Intent(Intent.ACTION_CALL_PRIVILEGED,
                    Uri.fromParts("tel", number, null));
            intent.setFlags(
                    Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);
            startActivity(intent);
        }
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        callEntry(position);
    }
}
