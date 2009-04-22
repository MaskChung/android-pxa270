/*
**
** Copyright 2006, The Android Open Source Project
**
** Licensed under the Apache License, Version 2.0 (the "License");
** you may not use this file except in compliance with the License.
** You may obtain a copy of the License at
**
**     http://www.apache.org/licenses/LICENSE-2.0
**
** Unless required by applicable law or agreed to in writing, software
** distributed under the License is distributed on an "AS IS" BASIS,
** See the License for the specific language governing permissions and
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
** limitations under the License.
*/

package com.android.providers.calendar;

import com.google.android.collect.Sets;
import com.google.android.gdata.client.AndroidGDataClient;
import com.google.android.gdata.client.AndroidXmlParserFactory;
import com.google.android.googlelogin.GoogleLoginServiceBlockingHelper;
import com.google.android.googlelogin.GoogleLoginServiceNotFoundException;
import com.google.android.providers.AbstractGDataSyncAdapter;
import com.google.android.providers.AbstractGDataSyncAdapter.GDataSyncData;
import com.google.wireless.gdata.calendar.client.CalendarClient;
import com.google.wireless.gdata.calendar.data.CalendarEntry;
import com.google.wireless.gdata.calendar.data.CalendarsFeed;
import com.google.wireless.gdata.calendar.parser.xml.XmlCalendarGDataParserFactory;
import com.google.wireless.gdata.client.AllDeletedUnavailableException;
import com.google.wireless.gdata.client.AuthenticationException;
import com.google.wireless.gdata.data.Entry;
import com.google.wireless.gdata.parser.GDataParser;
import com.google.wireless.gdata.parser.ParseException;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.AbstractTableMerger;
import android.content.BroadcastReceiver;
import android.content.ContentProvider;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SyncAdapter;
import android.content.SyncContext;
import android.content.SyncableContentProvider;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Debug;
import android.os.Process;
import android.pim.DateException;
import android.pim.RecurrenceSet;
import android.pim.Time;
import android.provider.Calendar;
import android.provider.SyncConstValue;
import android.provider.Calendar.Attendees;
import android.provider.Calendar.BusyBits;
import android.provider.Calendar.CalendarAlerts;
import android.provider.Calendar.Calendars;
import android.provider.Calendar.Events;
import android.provider.Calendar.ExtendedProperties;
import android.provider.Calendar.Instances;
import android.provider.Calendar.Reminders;
import android.text.TextUtils;
import android.util.Config;
import android.util.Log;
import android.util.TimeFormatException;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;
import java.util.TreeSet;

public class CalendarProvider extends SyncableContentProvider {

    private static final boolean PROFILE = false;
    private static final boolean DEBUG_ALARMS = false;
    private static final boolean MULTIPLE_ATTENDEES_PER_EVENT = false;
    private static final String[] ACCOUNTS_PROJECTION = new String[] { Calendars._SYNC_ACCOUNT};

    private static final String[] EVENTS_PROJECTION = new String[] {
        Events._SYNC_ID,
        Events._SYNC_VERSION,
        Events._SYNC_ACCOUNT,
        Events.CALENDAR_ID };
    private static final int EVENTS_SYNC_ID_INDEX = 0;
    private static final int EVENTS_SYNC_VERSION_INDEX = 1;
    private static final int EVENTS_SYNC_ACCOUNT_INDEX = 2;
    private static final int EVENTS_CALENDAR_ID_INDEX = 3;

    private DatabaseUtils.InsertHelper mCalendarsInserter;
    private DatabaseUtils.InsertHelper mEventsInserter;
    private DatabaseUtils.InsertHelper mEventsRawTimesInserter;
    private DatabaseUtils.InsertHelper mDeletedEventsInserter;
    private DatabaseUtils.InsertHelper mInstancesInserter;
    private DatabaseUtils.InsertHelper mAttendeesInserter;
    private DatabaseUtils.InsertHelper mRemindersInserter;
    private DatabaseUtils.InsertHelper mCalendarAlertsInserter;
    private DatabaseUtils.InsertHelper mExtendedPropertiesInserter;

    /**
     * The cached copy of the CalendarMetaData database table.
     * Make this "package private" instead of "private" so that test code
     * can access it.
     */
    MetaData mMetaData;

    // The interval in minutes for calculating busy bits
    private static final int BUSYBIT_INTERVAL = 60;

    // A lookup table for getting a bit mask of length N, for N <= 32
    // For example, BIT_MASKS[4] gives 0xf (which has 4 bits set to 1).
    // We use this for computing the busy bits for events.
    private static final int[] BIT_MASKS = {
        0,
        0x00000001, 0x00000003, 0x00000007, 0x0000000f,
        0x0000001f, 0x0000003f, 0x0000007f, 0x000000ff,
        0x000001ff, 0x000003ff, 0x000007ff, 0x00000fff,
        0x00001fff, 0x00003fff, 0x00007fff, 0x0000ffff,
        0x0001ffff, 0x0003ffff, 0x0007ffff, 0x000fffff,
        0x001fffff, 0x003fffff, 0x007fffff, 0x00ffffff,
        0x01ffffff, 0x03ffffff, 0x07ffffff, 0x0fffffff,
        0x1fffffff, 0x3fffffff, 0x7fffffff, 0xffffffff,
    };

    public static final class TimeRange {
        public long begin;
        public long end;
        public boolean allDay;
    }

    public static final class InstancesRange {
        public long begin;
        public long end;

        public InstancesRange(long begin, long end) {
            this.begin = begin;
            this.end = end;
        }
    }

    public static final class InstancesList
            extends ArrayList<ContentValues> {
    }

    public static final class EventInstancesMap
            extends HashMap<String, InstancesList> {
        public void add(String syncId, ContentValues values) {
            InstancesList instances = get(syncId);
            if (instances == null) {
                instances = new InstancesList();
                put(syncId, instances);
            }
            instances.add(values);
        }
    }

    // A thread that runs in the background and schedules the next
    // calendar event alarm.
    private class AlarmScheduler implements Runnable {

        public AlarmScheduler() {
        }

        public void run() {
            try {
                Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
                runScheduleNextAlarm();
            } catch (SQLException e) {
                Log.e(TAG, "runScheduleNextAlarm() failed", e);
            }
        }
    }

    /**
     * We search backward in time for event reminders that we may have missed
     * and schedule them if the event has not yet expired.  The amount in
     * the past to search backwards is controlled by this constant.  It
     * should be at least a few minutes to allow for an event that was
     * recently created on the web to make its way to the phone.  Two hours
     * might seem like overkill, but it is useful in the case where the user
     * just crossed into a new timezone and might have just missed an alarm.
     */
    private static final long SCHEDULE_ALARM_SLACK = 2 * android.pim.DateUtils.HOUR_IN_MILLIS;

    /**
     * Alarms older than this threshold will be deleted from the CalendarAlerts
     * table.  This should be about a day because if the timezone is
     * wrong and the user corrects it we might delete good alarms that
     * appear to be old because the device time was incorrectly in the future.
     * This threshold must also be larger than SCHEDULE_ALARM_SLACK.  We add
     * the SCHEDULE_ALARM_SLACK to ensure this.
     */
    private static final long CLEAR_OLD_ALARM_THRESHOLD = android.pim.DateUtils.DAY_IN_MILLIS
            + SCHEDULE_ALARM_SLACK;

    // A lock for synchronizing access to fields that are shared
    // with the AlarmScheduler thread.
    private Object mAlarmLock = new Object();

    private static final String TAG = "CalendarProvider";
    private static final String DATABASE_NAME = "calendar.db";
    
    // Note: if you update the version number, you must also update the code
    // in upgradeDatabase() to modify the database (gracefully, if possible).
    private static final int DATABASE_VERSION = 50;

    private static final String EXPECTED_PROJECTION = "/full";

    private static final String DESIRED_PROJECTION = "/full-selfattendance";

    private static final String FEEDS_SUBSTRING = "/feeds/";

    // Make sure we load at least two months worth of data.
    // Client apps can load more data in a background thread.
    private static final long MINIMUM_EXPANSION_SPAN =
            2L * 31 * 24 * 60 * 60 * 1000;

    private static final String[] sCalendarsIdProjection = new String[] { Calendars._ID };
    private static final int CALENDARS_INDEX_ID = 0;
    
    // Allocate the string constant once here instead of on the heap
    private static final String CALENDAR_ID_SELECTION = "calendar_id=?";

    private static final String[] sInstancesProjection =
        new String[] { Instances.START_DAY, Instances.END_DAY,
        Instances.START_MINUTE, Instances.END_MINUTE, Instances.ALL_DAY };

    private static final int INSTANCES_INDEX_START_DAY = 0;
    private static final int INSTANCES_INDEX_END_DAY = 1;
    private static final int INSTANCES_INDEX_START_MINUTE = 2;
    private static final int INSTANCES_INDEX_END_MINUTE = 3;
    private static final int INSTANCES_INDEX_ALL_DAY = 4;

    private static final String[] sBusyBitProjection = new String[] {
        BusyBits.DAY, BusyBits.BUSYBITS, BusyBits.ALL_DAY_COUNT };

    private static final int BUSYBIT_INDEX_DAY = 0;
    private static final int BUSYBIT_INDEX_BUSYBITS= 1;
    private static final int BUSYBIT_INDEX_ALL_DAY_COUNT = 2;

    private CalendarClient mCalendarClient = null;

    private AlarmManager mAlarmManager;

    private CalendarSyncAdapter mSyncAdapter;

    /**
     * Listens for timezone changes and disk-no-longer-full events
     */
    private BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_TIMEZONE_CHANGED.equals(action)) {
                updateTimezoneDependentFields();
                scheduleNextAlarm();
            } else if (Intent.ACTION_DEVICE_STORAGE_OK.equals(action)) {
                // Try to clean up if things were screwy due to a full disk
                updateTimezoneDependentFields();
                scheduleNextAlarm();
            } else if (Intent.ACTION_TIME_CHANGED.equals(action)) {
                scheduleNextAlarm();
            }
        }
    };

    public CalendarProvider() {
        super(DATABASE_NAME, DATABASE_VERSION, Calendars.CONTENT_URI);
    }

    @Override
    public boolean onCreate() {
        super.onCreate();

        // Register for Intent broadcasts
        IntentFilter filter = new IntentFilter();

        filter.addAction(Intent.ACTION_TIMEZONE_CHANGED);
        filter.addAction(Intent.ACTION_DEVICE_STORAGE_OK);
        filter.addAction(Intent.ACTION_TIME_CHANGED);
        final Context c = getContext();

        // We don't ever unregister this because this thread always wants
        // to receive notifications, even in the background.  And if this
        // thread is killed then the whole process will be killed and the
        // memory resources will be reclaimed.
        c.registerReceiver(mIntentReceiver, filter);

        mMetaData = new MetaData(mOpenHelper);
        updateTimezoneDependentFields();

        return true;
    }

    /**
     * This creates a background thread to check the timezone and update
     * the timezone dependent fields in the Instances table if the timezone
     * has changes.
     */
    private void updateTimezoneDependentFields() {
        Thread thread = new TimezoneCheckerThread();
        thread.start();
    }

    private class TimezoneCheckerThread extends Thread {
        @Override
        public void run() {
            Process.setThreadPriority(Process.THREAD_PRIORITY_BACKGROUND);
            try {
                doUpdateTimezoneDependentFields();
            } catch (SQLException e) {
                Log.e(TAG, "doUpdateTimezoneDependentFields() failed", e);
                try {
                    // Clear at least the in-memory data (and if possible the
                    // database fields) to force a re-computation of Instances.
                    mMetaData.clearInstanceRange();
                } catch (SQLException e2) {
                    Log.e(TAG, "clearInstanceRange() also failed: " + e2);
                }
            }
        }
    }

    /**
     * This method runs in a background thread.  If the timezone has changed
     * then the Instances table will be regenerated.
     */
    private void doUpdateTimezoneDependentFields() {
        MetaData.Fields fields = mMetaData.getFields();
        String localTimezone = TimeZone.getDefault().getID();
        if (TextUtils.equals(fields.timezone, localTimezone)) {
            return;
        }

        // The database timezone is different from the current timezone.
        // Regenerate the Instances table for this month.  Include events
        // starting at the beginning of this month.
        long now = System.currentTimeMillis();
        Time time = new Time();
        time.set(now);
        time.monthDay = 1;
        time.hour = 0;
        time.minute = 0;
        time.second = 0;
        long begin = time.normalize(true);
        long end = begin + MINIMUM_EXPANSION_SPAN;
        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        handleInstanceQuery(qb, begin, end, new String[] { Instances._ID },
                null /* selection */, null /* sort */);

        // Also pre-compute the BusyBits table for this month.
        int startDay = Time.getJulianDay(begin, time.gmtoff);
        int endDay = startDay + 31;
        qb = new SQLiteQueryBuilder();
        handleBusyBitsQuery(qb, startDay, endDay, sBusyBitProjection,
                null /* selection */, null /* sort */);
    }

    @Override
    protected void onDatabaseOpened(SQLiteDatabase db) {
        db.markTableSyncable("Events", "DeletedEvents");

        if (!isTemporary()) {
            mCalendarClient = new CalendarClient(
                new AndroidGDataClient(getContext().getContentResolver()),
                new XmlCalendarGDataParserFactory(
                new AndroidXmlParserFactory()));
        }

        mCalendarsInserter = new DatabaseUtils.InsertHelper(db, "Calendars");
        mEventsInserter = new DatabaseUtils.InsertHelper(db, "Events");
        mEventsRawTimesInserter = new DatabaseUtils.InsertHelper(db, "EventsRawTimes");
        mDeletedEventsInserter = new DatabaseUtils.InsertHelper(db, "DeletedEvents");
        mInstancesInserter = new DatabaseUtils.InsertHelper(db, "Instances");
        mAttendeesInserter = new DatabaseUtils.InsertHelper(db, "Attendees");
        mRemindersInserter = new DatabaseUtils.InsertHelper(db, "Reminders");
        mCalendarAlertsInserter = new DatabaseUtils.InsertHelper(db, "CalendarAlerts");
        mExtendedPropertiesInserter =
            new DatabaseUtils.InsertHelper(db, "ExtendedProperties");
    }

    @Override
    protected boolean upgradeDatabase(SQLiteDatabase db, int oldVersion, int newVersion) {
        Log.i(TAG, "Upgrading DB from version " + oldVersion
                    + " to " + newVersion);
        if (oldVersion < 46) {
            dropTables(db);
            bootstrapDatabase(db);
            return false; // this was lossy
        }

        if (oldVersion == 46) {
            Log.w(TAG, "Upgrading CalendarAlerts table");
            db.execSQL("UPDATE CalendarAlerts SET reminder_id=NULL;");
            db.execSQL("ALTER TABLE CalendarAlerts ADD COLUMN minutes INTEGER DEFAULT 0;");
            oldVersion += 1;
        }
        
        if (oldVersion == 47) {
            // Changing to version 48 was intended to force a data wipe
            dropTables(db);
            bootstrapDatabase(db);
            return false; // this was lossy
        }
        
        if (oldVersion == 48) {
            // Changing to version 49 was intended to force a data wipe
            dropTables(db);
            bootstrapDatabase(db);
            return false; // this was lossy
        }

        if (oldVersion == 49) {
            Log.w(TAG, "Upgrading DeletedEvents table");

            // We don't have enough information to fill in the correct
            // value of the calendar_id for old rows in the DeletedEvents
            // table, but rows in that table are transient so it is unlikely
            // that there are any rows.  Plus, the calendar_id is used only
            // when deleting a calendar, which is a rare event.  All new rows
            // will have the correct calendar_id.
            db.execSQL("ALTER TABLE DeletedEvents ADD COLUMN calendar_id INTEGER;");

            // Trigger to remove a calendar's events when we delete the calendar
            db.execSQL("DROP TRIGGER IF EXISTS calendar_cleanup");
            db.execSQL("CREATE TRIGGER calendar_cleanup DELETE ON Calendars " +
                        "BEGIN " +
                            "DELETE FROM Events WHERE calendar_id = old._id;" +
                            "DELETE FROM DeletedEvents WHERE calendar_id = old._id;" +
                        "END");
            oldVersion += 1;
        }
        return true; // this was lossless
    }

    private void dropTables(SQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS Calendars;");
        db.execSQL("DROP TABLE IF EXISTS Events;");
        db.execSQL("DROP TABLE IF EXISTS EventsRawTimes;");
        db.execSQL("DROP TABLE IF EXISTS DeletedEvents;");
        db.execSQL("DROP TABLE IF EXISTS Instances;");
        db.execSQL("DROP TABLE IF EXISTS CalendarMetaData;");
        db.execSQL("DROP TABLE IF EXISTS BusyBits;");
        db.execSQL("DROP TABLE IF EXISTS Attendees;");
        db.execSQL("DROP TABLE IF EXISTS Reminders;");
        db.execSQL("DROP TABLE IF EXISTS CalendarAlerts;");
        db.execSQL("DROP TABLE IF EXISTS ExtendedProperties;");
    }

    @Override
    protected void bootstrapDatabase(SQLiteDatabase db) {
        super.bootstrapDatabase(db);
        db.execSQL("CREATE TABLE Calendars (" +
                        "_id INTEGER PRIMARY KEY," +
                        "_sync_account TEXT," +
                        "_sync_id TEXT," +
                        "_sync_version TEXT," +
                        "_sync_time TEXT," +            // UTC
                        "_sync_local_id INTEGER," +
                        "_sync_dirty INTEGER," +
                        "_sync_mark INTEGER," + // Used to filter out new rows
                        "url TEXT," +
                        "name TEXT," +
                        "displayName TEXT," +
                        "hidden INTEGER NOT NULL DEFAULT 0," +
                        "color INTEGER," +
                        "access_level INTEGER," +
                        "selected INTEGER NOT NULL DEFAULT 1," +
                        "sync_events INTEGER NOT NULL DEFAULT 0," +
                        "location TEXT," +
                        "timezone TEXT" +
                        ");");

        // Trigger to remove a calendar's events when we delete the calendar
        db.execSQL("CREATE TRIGGER calendar_cleanup DELETE ON Calendars " +
                    "BEGIN " +
                        "DELETE FROM Events WHERE calendar_id = old._id;" +
                        "DELETE FROM DeletedEvents WHERE calendar_id = old._id;" +
                    "END");

        // TODO: do we need both dtend and duration?
        db.execSQL("CREATE TABLE Events (" +
                        "_id INTEGER PRIMARY KEY," +
                        "_sync_account TEXT," +
                        "_sync_id TEXT," +
                        "_sync_version TEXT," +
                        "_sync_time TEXT," +            // UTC
                        "_sync_local_id INTEGER," +
                        "_sync_dirty INTEGER," +
                        "_sync_mark INTEGER," + // To filter out new rows
                        "calendar_id INTEGER," +
                        "htmlUri TEXT," +
                        "title TEXT," +
                        "eventLocation TEXT," +
                        "description TEXT," +
                        "eventStatus INTEGER," +
                        "selfAttendeeStatus INTEGER NOT NULL DEFAULT 0," +
                        "commentsUri TEXT," +
                        "dtstart INTEGER," +               // millis since epoch
                        "dtend INTEGER," +                 // millis since epoch
                        "eventTimezone TEXT," +         // timezone for event
                        "duration TEXT," +
                        "allDay INTEGER NOT NULL DEFAULT 0," +
                        "visibility INTEGER NOT NULL DEFAULT 0," +
                        "transparency INTEGER NOT NULL DEFAULT 0," +
                        "hasAlarm INTEGER NOT NULL DEFAULT 0," +
                        "hasExtendedProperties INTEGER NOT NULL DEFAULT 0," +
                        "rrule TEXT," +
                        "rdate TEXT," +
                        "exrule TEXT," +
                        "exdate TEXT," +
                        "originalEvent TEXT," +
                        "originalInstanceTime INTEGER," +  // millis since epoch
                        "lastDate INTEGER" +               // millis since epoch
                    ");");

        db.execSQL("CREATE INDEX eventsCalendarIdIndex ON Events (" +
                   Events.CALENDAR_ID +
                   ");");

        db.execSQL("CREATE TABLE EventsRawTimes (" +
                        "_id INTEGER PRIMARY KEY," +
                        "event_id INTEGER NOT NULL," +
                        "dtstart2445 TEXT," +
                        "dtend2445 TEXT," +
                        "originalInstanceTime2445 TEXT," +
                        "lastDate2445 TEXT," +
                        "UNIQUE (event_id)" +
                    ");");

        // NOTE: we do not create a trigger to delete an event's instances upon update,
        // as all rows currently get updated during a merge.

        db.execSQL("CREATE TABLE DeletedEvents (" +
                        "_sync_id TEXT," +
                        "_sync_version TEXT," +
                        "_sync_account TEXT," +
                        "_sync_mark INTEGER," + // To filter out new rows
                        "calendar_id INTEGER" +
                    ");");

        db.execSQL("CREATE TABLE Instances (" +
                        "_id INTEGER PRIMARY KEY," +
                        "event_id INTEGER," +
                        "begin INTEGER," +         // UTC millis
                        "end INTEGER," +           // UTC millis
                        "startDay INTEGER," +      // Julian start day
                        "endDay INTEGER," +        // Julian end day
                        "startMinute INTEGER," +   // minutes from midnight
                        "endMinute INTEGER," +     // minutes from midnight
                        "UNIQUE (event_id, begin, end)" +
                    ");");

        db.execSQL("CREATE INDEX instancesStartDayIndex ON Instances (" +
                   Instances.START_DAY +
                   ");");

        db.execSQL("CREATE TABLE CalendarMetaData (" +
                        "_id INTEGER PRIMARY KEY," +
                        "localTimezone TEXT," +
                        "minInstance INTEGER," +      // UTC millis
                        "maxInstance INTEGER," +      // UTC millis
                        "minBusyBits INTEGER," +      // UTC millis
                        "maxBusyBits INTEGER" +       // UTC millis
        ");");

        db.execSQL("CREATE TABLE BusyBits(" +
                        "day INTEGER PRIMARY KEY," +  // the Julian day
                        "busyBits INTEGER," +         // 24 bits for 60-minute intervals
                        "allDayCount INTEGER" +       // number of all-day events
        ");");

        db.execSQL("CREATE TABLE Attendees (" +
                        "_id INTEGER PRIMARY KEY," +
                        "event_id INTEGER," +
                        "attendeeName TEXT," +
                        "attendeeEmail TEXT," +
                        "attendeeStatus INTEGER," +
                        "attendeeRelationship INTEGER," +
                        "attendeeType INTEGER" +
                   ");");

        db.execSQL("CREATE INDEX attendeesEventIdIndex ON Attendees (" +
                   Attendees.EVENT_ID +
                   ");");

        db.execSQL("CREATE TABLE Reminders (" +
                        "_id INTEGER PRIMARY KEY," +
                        "event_id INTEGER," +
                        "minutes INTEGER," +
                        "method INTEGER NOT NULL" +
                        " DEFAULT " + Reminders.METHOD_DEFAULT +
                   ");");

        db.execSQL("CREATE INDEX remindersEventIdIndex ON Reminders (" +
                   Reminders.EVENT_ID +
                   ");");

        // This table stores the Calendar notifications that have gone off.
        db.execSQL("CREATE TABLE CalendarAlerts (" +
                        "_id INTEGER PRIMARY KEY," +
                        "event_id INTEGER," +
                        "begin INTEGER NOT NULL," +        // UTC millis
                        "end INTEGER NOT NULL," +          // UTC millis
                        "alarmTime INTEGER NOT NULL," +    // UTC millis
                        "state INTEGER NOT NULL," +
                        "minutes INTEGER," +
                        "UNIQUE (alarmTime, begin, event_id)" +
                   ");");

        db.execSQL("CREATE INDEX calendarAlertsEventIdIndex ON CalendarAlerts (" +
                   CalendarAlerts.EVENT_ID +
                   ");");

        db.execSQL("CREATE TABLE ExtendedProperties (" +
                        "_id INTEGER PRIMARY KEY," +
                        "event_id INTEGER," +
                        "name TEXT," +
                        "value TEXT" +
                   ");");

        db.execSQL("CREATE INDEX extendedPropertiesEventIdIndex ON ExtendedProperties (" +
                   ExtendedProperties.EVENT_ID +
                   ");");

        // Trigger to remove data tied to an event when we delete that event.
        db.execSQL("CREATE TRIGGER events_cleanup_delete DELETE ON Events " +
                    "BEGIN " +
                        "DELETE FROM Instances WHERE event_id = old._id;" +
                        "DELETE FROM EventsRawTimes WHERE event_id = old._id;" +
                        "DELETE FROM Attendees WHERE event_id = old._id;" +
                        "DELETE FROM Reminders WHERE event_id = old._id;" +
                        "DELETE FROM CalendarAlerts WHERE event_id = old._id;" +
                        "DELETE FROM ExtendedProperties WHERE event_id = old._id;" +
                    "END");

        // Triggers to set the _sync_dirty flag when an attendee is changed,
        // inserted or deleted
        db.execSQL("CREATE TRIGGER attendees_update UPDATE ON Attendees " +
                    "BEGIN " +
                        "UPDATE Events SET _sync_dirty=1 WHERE Events._id=old.event_id;" +
                    "END");
        db.execSQL("CREATE TRIGGER attendees_insert INSERT ON Attendees " +
                    "BEGIN " +
                        "UPDATE Events SET _sync_dirty=1 WHERE Events._id=new.event_id;" +
                    "END");
        db.execSQL("CREATE TRIGGER attendees_delete DELETE ON Attendees " +
                    "BEGIN " +
                        "UPDATE Events SET _sync_dirty=1 WHERE Events._id=old.event_id;" +
                    "END");

        // Triggers to set the _sync_dirty flag when a reminder is changed,
        // inserted or deleted
        db.execSQL("CREATE TRIGGER reminders_update UPDATE ON Reminders " +
                    "BEGIN " +
                        "UPDATE Events SET _sync_dirty=1 WHERE Events._id=old.event_id;" +
                    "END");
        db.execSQL("CREATE TRIGGER reminders_insert INSERT ON Reminders " +
                    "BEGIN " +
                        "UPDATE Events SET _sync_dirty=1 WHERE Events._id=new.event_id;" +
                    "END");
        db.execSQL("CREATE TRIGGER reminders_delete DELETE ON Reminders " +
                    "BEGIN " +
                        "UPDATE Events SET _sync_dirty=1 WHERE Events._id=old.event_id;" +
                    "END");
        // Triggers to set the _sync_dirty flag when an extended property is changed,
        // inserted or deleted
        db.execSQL("CREATE TRIGGER extended_properties_update UPDATE ON ExtendedProperties " +
                    "BEGIN " +
                        "UPDATE Events SET _sync_dirty=1 WHERE Events._id=old.event_id;" +
                    "END");
        db.execSQL("CREATE TRIGGER extended_properties_insert UPDATE ON ExtendedProperties " +
                    "BEGIN " +
                        "UPDATE Events SET _sync_dirty=1 WHERE Events._id=new.event_id;" +
                    "END");
        db.execSQL("CREATE TRIGGER extended_properties_delete UPDATE ON ExtendedProperties " +
                    "BEGIN " +
                        "UPDATE Events SET _sync_dirty=1 WHERE Events._id=old.event_id;" +
                    "END");
    }

    /**
     * Make sure that there are no entries for accounts that no longer
     * exist. We are overriding this since we need to delete from the
     * Calendars table, which is not syncable, which has triggers that
     * will delete from the Events and DeletedEvents tables, which are
     * syncable.
     */
    @Override
    protected void onAccountsChanged(String[] accountsArray) {
        super.onAccountsChanged(accountsArray);

        Map<String, Boolean> accounts = new HashMap<String, Boolean>();
        for (String account : accountsArray) {
            accounts.put(account, false);
        }

        mDb.beginTransaction();
        try {
            deleteRowsForRemovedAccounts(accounts, "Calendars",
                    SyncConstValue._SYNC_ACCOUNT);
            mDb.setTransactionSuccessful();
        } finally {
            mDb.endTransaction();
        }

        if (mCalendarClient == null) {
            return;
        }

        // If we have calendars for unknown accounts, delete them.
        // If there are no calendars at all for a given account, add the
        // default calendar.

        for (Map.Entry<String, Boolean> entry : accounts.entrySet()) {
            entry.setValue(false);
            // TODO: remove this break when Calendar supports multiple accounts. Until then
            // pretend that only the first account exists.
            break;
        }

        Set<String> handledAccounts = Sets.newHashSet();
        ContentResolver cr = getContext().getContentResolver();
        if (Config.LOGV) Log.v(TAG, "querying calendars");
        Cursor c = cr.query(Calendars.CONTENT_URI, ACCOUNTS_PROJECTION, null, null, null);
        try {
            while (c.moveToNext()) {
                String account = c.getString(0);
                if (handledAccounts.contains(account)) {
                    continue;
                }
                handledAccounts.add(account);
                if (accounts.containsKey(account)) {
                    if (Config.LOGV) {
                        Log.v(TAG, "calendars for account " + account
                                + " exist");
                    }
                    accounts.put(account, true /* hasCalendar */);
                }
            }
        } finally {
            c.close();
            c = null;
        }

        if (Config.LOGV) {
            Log.v(TAG, "scanning over " + accounts.size() + " account(s)");
        }
        for (Map.Entry<String, Boolean> entry : accounts.entrySet()) {
            String account = entry.getKey();
            boolean hasCalendar = entry.getValue();
            if (hasCalendar) {
                if (Config.LOGV) {
                    Log.v(TAG, "ignoring account " + account +
                         " since it matched an existing calendar");
                }
                continue;
            }
            String feedUrl = mCalendarClient.getDefaultCalendarUrl(account,
                    CalendarClient.PROJECTION_PRIVATE_SELF_ATTENDANCE, null/* query params */);
            feedUrl = CalendarSyncAdapter.rewriteUrlforAccount(account, feedUrl);
            if (Config.LOGV) {
                Log.v(TAG, "adding default calendar for account " + account);
            }
            ContentValues values = new ContentValues();
            values.put(Calendars._SYNC_ACCOUNT, account);
            values.put(Calendars.URL, feedUrl);
            values.put(Calendars.DISPLAY_NAME, "Default");
            values.put(Calendars.SYNC_EVENTS, 1);
            values.put(Calendars.SELECTED, 1);
            values.put(Calendars.HIDDEN, 0);
            values.put(Calendars.COLOR, -14069085 /* blue */);
            // this is just our best guess.  the real value will get updated
            // when the user does a sync.
            values.put(Calendars.TIMEZONE, Time.getCurrentTimezone());
            values.put(Calendars.ACCESS_LEVEL, Calendars.OWNER_ACCESS);
            cr.insert(Calendars.CONTENT_URI, values);

            scheduleSync(account, false /* do a full sync */, null /* no url */);
        }
    }

    @Override
    public Cursor queryInternal(Uri url, String[] projectionIn,
            String selection, String[] selectionArgs, String sort) {
        final SQLiteDatabase db = getDatabase();
        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();

        Cursor ret;

        // Generate the body of the query
        int match = sURLMatcher.match(url);
        switch (match)
        {
            case EVENTS:
                qb.setTables("Events, Calendars");
                qb.setProjectionMap(sEventsProjectionMap);
                qb.appendWhere("Events.calendar_id=Calendars._id");
                break;
            case EVENTS_ID:
                qb.setTables("Events, Calendars");
                qb.setProjectionMap(sEventsProjectionMap);
                qb.appendWhere("Events.calendar_id=Calendars._id");
                qb.appendWhere(" AND Events._id=");
                qb.appendWhere(url.getPathSegments().get(1));
                break;
            case DELETED_EVENTS:
                if (isTemporary()) {
                    qb.setTables("DeletedEvents");
                    break;
                } else {
                    throw new IllegalArgumentException("Unknown URL " + url);
                }
            case CALENDARS:
                qb.setTables("Calendars");
                // see if we want to update the list of calendars from the
                // server.
                String update = null;
                update = url.getQueryParameter("update");
                if ("1".equals(update)) {
                    fetchCalendarsFromServer();
                }

                break;
            case CALENDARS_ID:
                qb.setTables("Calendars");
                qb.appendWhere("_id=");
                qb.appendWhere(url.getPathSegments().get(1));
                break;
            case INSTANCES:
                long begin;
                long end;
                try {
                    begin = Long.valueOf(url.getPathSegments().get(2));
                } catch (NumberFormatException nfe) {
                    throw new IllegalArgumentException("Cannot parse begin "
                            + url.getPathSegments().get(2));
                }
                try {
                    end = Long.valueOf(url.getPathSegments().get(3));
                } catch (NumberFormatException nfe) {
                    throw new IllegalArgumentException("Cannot parse end "
                            + url.getPathSegments().get(3));
                }
                return handleInstanceQuery(qb, begin, end, projectionIn,
                                           selection, sort);
            case BUSYBITS:
                int startDay;
                int endDay;
                try {
                    startDay = Integer.valueOf(url.getPathSegments().get(2));
                } catch (NumberFormatException nfe) {
                    throw new IllegalArgumentException("Cannot parse start day "
                            + url.getPathSegments().get(2));
                }
                try {
                    endDay = Integer.valueOf(url.getPathSegments().get(3));
                } catch (NumberFormatException nfe) {
                    throw new IllegalArgumentException("Cannot parse end day "
                            + url.getPathSegments().get(3));
                }
                return handleBusyBitsQuery(qb, startDay, endDay, projectionIn,
                                           selection, sort);
            case ATTENDEES:
                qb.setTables("Attendees, Events, Calendars");
                qb.setProjectionMap(sAttendeesProjectionMap);
                qb.appendWhere("Events.calendar_id=Calendars._id");
                qb.appendWhere(" AND Events._id=Attendees.event_id");
                break;
            case ATTENDEES_ID:
                qb.setTables("Attendees, Events, Calendars");
                qb.setProjectionMap(sAttendeesProjectionMap);
                qb.appendWhere("Attendees._id=");
                qb.appendWhere(url.getPathSegments().get(1));
                qb.appendWhere(" AND Events.calendar_id=Calendars._id");
                qb.appendWhere(" AND Events._id=Attendees.event_id");
                break;
            case REMINDERS:
                qb.setTables("Reminders");
                break;
            case REMINDERS_ID:
                qb.setTables("Reminders, Events, Calendars");
                qb.setProjectionMap(sRemindersProjectionMap);
                qb.appendWhere("Reminders._id=");
                qb.appendWhere(url.getLastPathSegment());
                qb.appendWhere(" AND Events.calendar_id=Calendars._id");
                qb.appendWhere(" AND Events._id=Reminders.event_id");
                break;
            case CALENDAR_ALERTS:
                qb.setTables("CalendarAlerts, Events, Calendars");
                qb.setProjectionMap(sCalendarAlertsProjectionMap);
                qb.appendWhere("Events.calendar_id=Calendars._id");
                qb.appendWhere(" AND Events._id=CalendarAlerts.event_id");
                break;
            case CALENDAR_ALERTS_BY_INSTANCE:
                qb.setTables("CalendarAlerts, Events, Calendars");
                qb.setProjectionMap(sCalendarAlertsProjectionMap);
                qb.appendWhere("Events.calendar_id=Calendars._id");
                qb.appendWhere(" AND Events._id=CalendarAlerts.event_id");
                String groupBy = CalendarAlerts.EVENT_ID + "," + CalendarAlerts.BEGIN;
                return qb.query(db, projectionIn, selection, selectionArgs,
                        groupBy, null, sort);
            case CALENDAR_ALERTS_ID:
                qb.setTables("CalendarAlerts, Events, Calendars");
                qb.setProjectionMap(sCalendarAlertsProjectionMap);
                qb.appendWhere("CalendarAlerts._id=");
                qb.appendWhere(url.getLastPathSegment());
                qb.appendWhere(" AND Events.calendar_id=Calendars._id");
                qb.appendWhere(" AND Events._id=CalendarAlerts.event_id");
                break;
            case EXTENDED_PROPERTIES:
                qb.setTables("ExtendedProperties");
                break;
            case EXTENDED_PROPERTIES_ID:
                qb.setTables("ExtendedProperties, Events, Calendars");
                // not sure if we need a projection map or a join.  see what callers want.
//                qb.setProjectionMap(sExtendedPropertiesProjectionMap);
                qb.appendWhere("ExtendedProperties._id=");
                qb.appendWhere(url.getPathSegments().get(1));
//                qb.appendWhere(" AND Events.calendar_id = Calendars._id");
//                qb.appendWhere(" AND Events._id=ExtendedProperties.event_id");
                break;

            default:
                throw new IllegalArgumentException("Unknown URL " + url);
        }

        // run the query
        ret = qb.query(db, projectionIn, selection, selectionArgs, null, null, sort);

        return ret;
    }

    private void fetchCalendarsFromServer() {
        if (mCalendarClient == null) {
            Log.w(TAG, "Cannot fetch calendars -- calendar url defined.");
            return;
        }

        GoogleLoginServiceBlockingHelper loginHelper = null;
        String username = null;
        String authToken = null;

        try {
            loginHelper = new GoogleLoginServiceBlockingHelper(getContext());

            // TODO: allow caller to specify which account's feeds should be updated
            username = loginHelper.getAccount(false);
            if (TextUtils.isEmpty(username)) {
                Log.w(TAG, "Unable to update calendars from server -- "
                      + "no users configured.");
                return;
            }

            try {
                authToken = loginHelper.getAuthToken(username,
                                                     mCalendarClient.getServiceName());
            } catch (GoogleLoginServiceBlockingHelper.AuthenticationException e) {
                Log.w(TAG, "Unable to update calendars from server -- could not "
                      + "authenticate user " + username, e);
                return;
            }
        } catch (GoogleLoginServiceNotFoundException e) {
            Log.e(TAG, "Could not find Google login service", e);
            return;
        } finally {
            if (loginHelper != null) {
                loginHelper.close();
            }
        }

        // get the current set of calendars.  we'll need to pay attention to
        // which calendars we get back from the server, so we can delete
        // calendars that have been deleted from the server.
        Set<Long> existingCalendarIds = new HashSet<Long>();

        final SQLiteDatabase db = getDatabase();
        db.beginTransaction();
        try {
            getCurrentCalendars(existingCalendarIds);

            // get and process the calendars meta feed
            GDataParser parser = null;
            try {
                String feedUrl = mCalendarClient.getUserCalendarsUrl(username);
                feedUrl = CalendarSyncAdapter.rewriteUrlforAccount(username, feedUrl);
                parser = mCalendarClient.getParserForUserCalendars(feedUrl, authToken);
                // process the calendars
                processCalendars(username, parser, existingCalendarIds);
            } catch (AuthenticationException ae) {
                Log.w(TAG, "Unable to process calendars from server -- could not "
                        + "authenticate user.", ae);
                return;
            } catch (ParseException pe) {
                Log.w(TAG, "Unable to process calendars from server -- could not "
                        + "parse calendar feed.", pe);
                return;
            } catch (IOException ioe) {
                Log.w(TAG, "Unable to process calendars from server -- encountered "
                        + "i/o error", ioe);
                return;
            } catch (AllDeletedUnavailableException e) {
                Log.w(TAG, "Unable to process calendars from server -- encountered "
                        + "an AllDeletedUnavailableException, this should never happen", e);
                return;
            } finally {
                if (parser != null) {
                    parser.close();
                }
            }

            // delete calendars that are no longer sent from the server.
            final Uri calendarContentUri = Calendars.CONTENT_URI;
            for (long calId : existingCalendarIds) {
                // NOTE: triggers delete all events, instances for this calendar.
                delete(ContentUris.withAppendedId(calendarContentUri, calId),
                        null /* where */, null /* selectionArgs */);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private void getCurrentCalendars(Set<Long> calendarIds) {
        Cursor cursor = query(Calendars.CONTENT_URI,
                new String[] { Calendars._ID },
                null /* selection */,
                null /* selectionArgs */,
                null /* sort */);
        if (cursor != null) {
            try {
                while (cursor.moveToNext()) {
                    calendarIds.add(cursor.getLong(0));
                }
            } finally {
                cursor.close();
            }
        }
    }

    private void processCalendars(String username,
                                  GDataParser parser,
                                  Set<Long> existingCalendarIds)
            throws ParseException, IOException {
        CalendarsFeed feed = (CalendarsFeed) parser.init();
        Entry entry = null;
        ContentValues map = new ContentValues();
        final Uri calendarContentUri = Calendars.CONTENT_URI;
        while (parser.hasMoreData()) {
            entry = parser.readNextEntry(entry);
            if (Config.LOGV) Log.v(TAG, "Read entry: " + entry.toString());
            CalendarEntry calendarEntry = (CalendarEntry) entry;
            String feedUrl = calendarEntryToContentValues(username, feed, calendarEntry, map);
            if (TextUtils.isEmpty(feedUrl)) {
                continue;
            }
            long calId = -1;

            Cursor c = query(calendarContentUri,
                    new String[] { Calendars._ID },
                    Calendars.URL + "='"
                            + feedUrl + '\'' /* selection */,
                    null /* selectionArgs */,
                    null /* sort */);
            if (c != null) {
                try {
                    if (c.moveToFirst()) {
                        calId = c.getLong(0);
                        existingCalendarIds.remove(calId);
                    }
                } finally {
                    c.close();
                }
            }

            if (calId != -1) {
                if (Config.LOGV) Log.v(TAG, "Updating calendar " + map);
                // don't override the existing "selected" or "hidden" settings.
                map.remove(Calendars.SELECTED);
                map.remove(Calendars.HIDDEN);
                // write to db directly, so we don't send a notification.
                updateInternal(ContentUris.withAppendedId(calendarContentUri, calId), map,
                       null /* where */, null /* selectionArgs */);
            } else {
                // Select this calendar for syncing and display if it is
                // selected and not hidden.
                int syncAndDisplay = 0;
                if (calendarEntry.isSelected() && !calendarEntry.isHidden()) {
                    syncAndDisplay = 1;
                }
                map.put(Calendars.SYNC_EVENTS, syncAndDisplay);
                map.put(Calendars.SELECTED, syncAndDisplay);
                map.put(Calendars.HIDDEN, 0);
                map.put(Calendars._SYNC_ACCOUNT, username);
                if (Config.LOGV) Log.v(TAG, "Adding calendar " + map);
                // write to db directly, so we don't send a notification.
                Uri row = insertInternal(calendarContentUri, map);
            }
        }
    }

    // TODO: unit test.
    protected static final String convertCalendarIdToFeedUrl(String url) {
        // id: http://www.google.com/calendar/feeds/<username>/<cal id>
        // desired feed:
        //   http://www.google.com/calendar/feeds/<cal id>/<projection>
        int start = url.indexOf(FEEDS_SUBSTRING);
        if (start != -1) {
            // strip out the */ in /feeds/*/
            start += FEEDS_SUBSTRING.length();
            int end = url.indexOf('/', start);
            if (end != -1) {
                url = url.replace(url.substring(start, end + 1), "");
            }
            url = url + "/private" + DESIRED_PROJECTION;
        }
        return url;
    }

    /**
     * Convert the CalenderEntry to a Bundle that can be inserted/updated into the
     * Calendars table.
     */
    private String calendarEntryToContentValues(String account, CalendarsFeed feed,
            CalendarEntry entry,
            ContentValues map) {
        map.clear();

        String url = entry.getAlternateLink();

        // we only want to fetch the full-selfattendance calendar feeds
        if (!TextUtils.isEmpty(url)) {
            if (url.endsWith(EXPECTED_PROJECTION)) {
                url = url.replace(EXPECTED_PROJECTION, DESIRED_PROJECTION);
            }
        } else {
            // yuck.  the alternate link was not available.  we should
            // reconstruct from the id.
            url = entry.getId();
            if (!TextUtils.isEmpty(url)) {
                url = convertCalendarIdToFeedUrl(url);
            } else {
                if (Config.LOGV) {
                    Log.v(TAG, "Cannot generate url for calendar feed.");
                }
                return null;
            }
        }

        url = CalendarSyncAdapter.rewriteUrlforAccount(account, url);

        map.put(Calendars.URL, url);
        map.put(Calendars.NAME, entry.getTitle());

        // TODO:
        map.put(Calendars.DISPLAY_NAME, entry.getTitle());

        map.put(Calendars.TIMEZONE, entry.getTimezone());

        String colorStr = entry.getColor();
        if (!TextUtils.isEmpty(colorStr)) {
            int color = Color.parseColor(colorStr);
            // Ensure the alpha is set to max
            color |= 0xff000000;
            map.put(Calendars.COLOR, color);
        }

        map.put(Calendars.SELECTED, entry.isSelected() ? 1 : 0);

        map.put(Calendars.HIDDEN, entry.isHidden() ? 1 : 0);

        int accesslevel;
        switch (entry.getAccessLevel()) {
        case CalendarEntry.ACCESS_NONE:
            accesslevel = Calendars.NO_ACCESS;
            break;
        case CalendarEntry.ACCESS_READ:
            accesslevel = Calendars.READ_ACCESS;
            break;
        case CalendarEntry.ACCESS_FREEBUSY:
            accesslevel = Calendars.FREEBUSY_ACCESS;
            break;
        case CalendarEntry.ACCESS_CONTRIBUTOR:
            accesslevel = Calendars.CONTRIBUTOR_ACCESS;
            break;
        case CalendarEntry.ACCESS_OWNER:
            accesslevel = Calendars.OWNER_ACCESS;
            break;
        default:
            accesslevel = Calendars.NO_ACCESS;
        }
        map.put(Calendars.ACCESS_LEVEL, accesslevel);
        // TODO: use the update time, when calendar actually supports this.
        // right now, calendar modifies the update time frequently.
        map.put(Calendars._SYNC_TIME, System.currentTimeMillis());

        return url;
    }

    /*
     * Fills the Instances table, if necessary, for the given range and then
     * queries the Instances table.
     */
    private Cursor handleInstanceQuery(SQLiteQueryBuilder qb, long rangeBegin,
                                       long rangeEnd, String[] projectionIn,
                                       String selection, String sort) {
        final SQLiteDatabase db = getDatabase();
        // will lock the database.
        acquireInstanceRange(rangeBegin, rangeEnd, true /* use minimum expansion window */);
        qb.setTables("Instances INNER JOIN Events ON (Instances.event_id=Events._id) " +
                     "INNER JOIN Calendars ON (Events.calendar_id = Calendars._id)");
        qb.setProjectionMap(sInstancesProjectionMap);
        qb.appendWhere("begin <= ");
        qb.appendWhere(String.valueOf(rangeEnd));
        qb.appendWhere(" AND end >= ");
        qb.appendWhere(String.valueOf(rangeBegin));
        return qb.query(db, projectionIn, selection, null, null, null, sort);
    }

    private Cursor handleBusyBitsQuery(SQLiteQueryBuilder qb, int startDay,
                                       int endDay, String[] projectionIn,
                                       String selection, String sort) {
        final SQLiteDatabase db = getDatabase();
        acquireBusyBitRange(startDay, endDay);
        qb.setTables("BusyBits");
        qb.setProjectionMap(sBusyBitsProjectionMap);
        qb.appendWhere("day >= ");
        qb.appendWhere(String.valueOf(startDay));
        qb.appendWhere(" AND day <= ");
        qb.appendWhere(String.valueOf(endDay));
        return qb.query(db, projectionIn, selection, null, null, null, sort);
    }

    /**
     * Ensure that the date range given has all elements in the instance
     * table.  Acquires the database lock and calls {@link #acquireInstanceRangeLocked}.
     */
    private void acquireInstanceRange(final long begin,
                                      final long end,
                                      final boolean useMinimumExpansionWindow) {
        mDb.beginTransaction();
        try {
            acquireInstanceRangeLocked(begin, end, useMinimumExpansionWindow);
            mDb.setTransactionSuccessful();
        } finally {
            mDb.endTransaction();
        }
    }

    /**
     * Expands the Instances table (if needed) and the BusyBits table.
     * Acquires the database lock and calls {@link #acquireBusyBitRangeLocked}.
     */
    private void acquireBusyBitRange(final int startDay, final int endDay) {
        mDb.beginTransaction();
        try {
            acquireBusyBitRangeLocked(startDay, endDay);
            mDb.setTransactionSuccessful();
        } finally {
            mDb.endTransaction();
        }
    }

    /**
     * Ensure that the date range given has all elements in the instance
     * table.  The database lock must be held when calling this method.
     */
    private void acquireInstanceRangeLocked(long begin, long end,
                                            boolean useMinimumExpansionWindow) {
        long expandBegin = begin;
        long expandEnd = end;

        if (useMinimumExpansionWindow) {
            // if we end up having to expand events into the instances table, expand
            // events for a minimal amount of time, so we do not have to perform
            // expansions frequently.
            long span = end - begin;
            if (span < MINIMUM_EXPANSION_SPAN) {
                long additionalRange = (MINIMUM_EXPANSION_SPAN - span) / 2;
                expandBegin -= additionalRange;
                expandEnd += additionalRange;
            }
        }

        // Check if the timezone has changed.
        // We do this check here because the database is locked and we can
        // safely delete all the entries in the Instances table.
        MetaData.Fields fields = mMetaData.getFieldsLocked();
        String dbTimezone = fields.timezone;
        long maxInstance = fields.maxInstance;
        long minInstance = fields.minInstance;
        String localTimezone = TimeZone.getDefault().getID();
        boolean timezoneChanged = (dbTimezone == null) || !dbTimezone.equals(localTimezone);

        if (maxInstance == 0 || timezoneChanged) {
            // Empty the Instances table and expand from scratch.
            mDb.execSQL("DELETE FROM Instances;");
            mDb.execSQL("DELETE FROM BusyBits;");
            if (Config.LOGV) {
                Log.v(TAG, "acquireInstanceRangeLocked() deleted Instances and Busybits,"
                        + " timezone changed: " + timezoneChanged);
            }
            expandInstanceRangeLocked(expandBegin, expandEnd, localTimezone);

            mMetaData.writeLocked(localTimezone, expandBegin, expandEnd,
                    0 /* startDay */, 0 /* endDay */);
            return;
        }

        // If the desired range [begin, end] has already been
        // expanded, then simply return.  The range is inclusive, that is,
        // events that touch either endpoint are included in the expansion.
        // This means that a zero-duration event that starts and ends at
        // the endpoint will be included.
        // We use [begin, end] here and not [expandBegin, expandEnd] for
        // checking the range because a common case is for the client to
        // request successive days or weeks, for example.  If we checked
        // that the expanded range [expandBegin, expandEnd] then we would
        // always be expanding because there would always be one more day
        // or week that hasn't been expanded.
        if ((begin >= minInstance) && (end <= maxInstance)) {
            if (Config.LOGV) {
                Log.v(TAG, "Canceled instance query (" + expandBegin + ", " + expandEnd
                        + ") falls within previously expanded range.");
            }
            return;
        }

        // If the requested begin point has not been expanded, then include
        // more events than requested in the expansion (use "expandBegin").
        if (begin < minInstance) {
            expandInstanceRangeLocked(expandBegin, minInstance, localTimezone);
            minInstance = expandBegin;
        }

        // If the requested end point has not been expanded, then include
        // more events than requested in the expansion (use "expandEnd").
        if (end > maxInstance) {
            expandInstanceRangeLocked(maxInstance, expandEnd, localTimezone);
            maxInstance = expandEnd;
        }

        // Update the bounds on the Instances table.
        mMetaData.writeLocked(localTimezone, minInstance, maxInstance,
                fields.minBusyBit, fields.maxBusyBit);
    }

    private void acquireBusyBitRangeLocked(int firstDay, int lastDay) {
        if (firstDay > lastDay) {
            throw new IllegalArgumentException("firstDay must not be greater than lastDay");
        }
        String localTimezone = TimeZone.getDefault().getID();
        MetaData.Fields fields = mMetaData.getFieldsLocked();
        String dbTimezone = fields.timezone;
        int minBusyBit = fields.minBusyBit;
        int maxBusyBit = fields.maxBusyBit;
        boolean timezoneChanged = (dbTimezone == null) || !dbTimezone.equals(localTimezone);
        if (firstDay >= minBusyBit && lastDay <= maxBusyBit && !timezoneChanged) {
            if (Config.LOGV) {
                Log.v(TAG, "acquireBusyBitRangeLocked() no expansion needed");
            }
            return;
        }

        // Avoid gaps in the BusyBit table and avoid recomputing the busy bits
        // that are already in the table.  If the busy bit range has been cleared,
        // don't bother checking.
        if (maxBusyBit != 0) {
            if (firstDay > maxBusyBit) {
                firstDay = maxBusyBit;
            } else if (lastDay < minBusyBit) {
                lastDay = minBusyBit;
            } else if (firstDay < minBusyBit && lastDay <= maxBusyBit) {
                lastDay = minBusyBit;
            } else if (lastDay > maxBusyBit && firstDay >= minBusyBit) {
                firstDay = maxBusyBit;
            }
        }

        // Allocate space for the busy bits, one 32-bit integer for each day.
        int numDays = lastDay - firstDay + 1;
        int[] busybits = new int[numDays];
        int[] allDayCounts = new int[numDays];

        // Convert the first and last Julian day range to a range that uses
        // UTC milliseconds.
        Time time = new Time();
        long begin = time.setJulianDay(firstDay);

        // We add one to lastDay because the time is set to 12am on the given
        // Julian day and we want to include all the events on the last day.
        long end = time.setJulianDay(lastDay + 1);

        // Make sure the Instances table includes events in the range
        // [begin, end].
        acquireInstanceRange(begin, end, true /* use minimum expansion window */);

        SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
        qb.setTables("Instances INNER JOIN Events ON (Instances.event_id=Events._id) " +
                "INNER JOIN Calendars ON (Events.calendar_id = Calendars._id)");
        qb.setProjectionMap(sInstancesProjectionMap);
        qb.appendWhere("begin <= ");
        qb.appendWhere(String.valueOf(end));
        qb.appendWhere(" AND end >= ");
        qb.appendWhere(String.valueOf(begin));
        qb.appendWhere(" AND ");
        qb.appendWhere(Instances.SELECTED);
        qb.appendWhere("=1");

        final SQLiteDatabase db = getDatabase();
        // Get all the instances that overlap the range [begin,end]
        Cursor cursor = qb.query(db, sInstancesProjection, null, null, null, null, null);
        int count = 0;
        try {
            count = cursor.getCount();
            while (cursor.moveToNext()) {
                int startDay = cursor.getInt(INSTANCES_INDEX_START_DAY);
                int endDay = cursor.getInt(INSTANCES_INDEX_END_DAY);
                int startMinute = cursor.getInt(INSTANCES_INDEX_START_MINUTE);
                int endMinute = cursor.getInt(INSTANCES_INDEX_END_MINUTE);
                boolean allDay = cursor.getInt(INSTANCES_INDEX_ALL_DAY) != 0;
                fillBusyBits(firstDay, startDay, endDay, startMinute, endMinute,
                        allDay, busybits, allDayCounts);
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        if (count == 0) {
            return;
        }

        // Read the busybit range again because that may have changed when we
        // called acquireInstanceRange().
        fields = mMetaData.getFieldsLocked();
        minBusyBit = fields.minBusyBit;
        maxBusyBit = fields.maxBusyBit;

        // If the busybit range was cleared, then delete all the entries.
        if (maxBusyBit == 0) {
            mDb.execSQL("DELETE FROM BusyBits;");
        }

        // Merge the busy bits with the database.
        mergeBusyBits(firstDay, lastDay, busybits, allDayCounts);
        if (maxBusyBit == 0) {
            minBusyBit = firstDay;
            maxBusyBit = lastDay;
        } else {
            if (firstDay < minBusyBit) {
                minBusyBit = firstDay;
            }
            if (lastDay > maxBusyBit) {
                maxBusyBit = lastDay;
            }
        }
        // Update the busy bit range
        mMetaData.writeLocked(fields.timezone, fields.minInstance, fields.maxInstance,
                minBusyBit, maxBusyBit);
    }

    private static final String[] EXPAND_COLUMNS = new String[] {
                    Events._ID,
                    Events._SYNC_ID,
                    Events.STATUS,
                    Events.DTSTART,
                    Events.DTEND,
                    Events.EVENT_TIMEZONE,
                    Events.RRULE,
                    Events.RDATE,
                    Events.EXRULE,
                    Events.EXDATE,
                    Events.DURATION,
                    Events.ALL_DAY,
                    Events.ORIGINAL_EVENT,
                    Events.ORIGINAL_INSTANCE_TIME
                };

    private static class CanceledInstance {
        public final long begin;
        public final String syncId;

        public CanceledInstance(long b, String id) {
            begin = b;
            syncId = id;
        }
    };

    /**
     * Make instances for the given range.
     */
    private void expandInstanceRangeLocked(long begin, long end, String localTimezone) {

        if (PROFILE) {
            Debug.startMethodTracing("expandInstanceRangeLocked");
        }

        final SQLiteDatabase db = getDatabase();
        Cursor entries = null;

        if (Config.LOGV) {
            Log.v(TAG, "Expanding events between " + begin + " and " + end);
        }

        try {
            SQLiteQueryBuilder qb = new SQLiteQueryBuilder();
            qb.setTables("Events INNER JOIN Calendars ON (calendar_id = Calendars._id)");
            qb.setProjectionMap(sEventsProjectionMap);

            qb.appendWhere("dtstart <= ");
            qb.appendWhere(String.valueOf(end));
            qb.appendWhere(" AND ");
            qb.appendWhere("(lastDate IS NULL OR lastDate >= ");
            qb.appendWhere(String.valueOf(begin));
            qb.appendWhere(")");

            entries = qb.query(db, EXPAND_COLUMNS, null, null, null, null, null);

            RecurrenceProcessor rp = new RecurrenceProcessor();

            TreeSet<Long> dates = new TreeSet<Long>();

            int statusColumn = entries.getColumnIndex(Events.STATUS);
            int dtstartColumn = entries.getColumnIndex(Events.DTSTART);
            int dtendColumn = entries.getColumnIndex(Events.DTEND);
            int eventTimezoneColumn = entries.getColumnIndex(Events.EVENT_TIMEZONE);
            int durationColumn = entries.getColumnIndex(Events.DURATION);
            int rruleColumn = entries.getColumnIndex(Events.RRULE);
            int rdateColumn = entries.getColumnIndex(Events.RDATE);
            int exruleColumn = entries.getColumnIndex(Events.EXRULE);
            int exdateColumn = entries.getColumnIndex(Events.EXDATE);
            int allDayColumn = entries.getColumnIndex(Events.ALL_DAY);
            int idColumn = entries.getColumnIndex(Events._ID);
            int syncIdColumn = entries.getColumnIndex(Events._SYNC_ID);
            int originalEventColumn = entries.getColumnIndex(Events.ORIGINAL_EVENT);
            int originalInstanceTimeColumn = entries.getColumnIndex(Events.ORIGINAL_INSTANCE_TIME);

            ContentValues initialValues;
            EventInstancesMap instancesMap = new EventInstancesMap();
            ArrayList<CanceledInstance> canceled = new ArrayList<CanceledInstance>();

            Duration duration = new Duration();
            Time eventTime = new Time();

            while (entries.moveToNext()) {
                initialValues = null;

                boolean allDay = entries.getInt(allDayColumn) != 0;

                String eventTimezone = entries.getString(eventTimezoneColumn);
                if (allDay || TextUtils.isEmpty(eventTimezone)) {
                  // in the events table, allDay events start at midnight.
                  // this forces them to stay at midnight for all day events
                  // TODO: check that this actually does the right thing.
                  eventTimezone = Time.TIMEZONE_UTC;
                }

                long dtstartMillis = entries.getLong(dtstartColumn);
                Long eventId = Long.valueOf(entries.getLong(idColumn));

                String durationStr = entries.getString(durationColumn);
                if (durationStr != null) {
                    try {
                        duration.parse(durationStr);
                    }
                    catch (DateException e) {
                        Log.w(TAG, "error parsing duration for event "
                                + eventId + "'" + durationStr + "'", e);
                        duration.sign = 1;
                        duration.weeks = 0;
                        duration.days = 0;
                        duration.hours = 0;
                        duration.minutes = 0;
                        duration.seconds = 0;
                        durationStr = "+P0S";
                    }
                }

                String syncId = entries.getString(syncIdColumn);
                String originalEvent = entries.getString(originalEventColumn);
                long originalInstanceTimeMillis = -1;
                if (!entries.isNull(originalInstanceTimeColumn)) {
                    originalInstanceTimeMillis= entries.getLong(originalInstanceTimeColumn);
                }
                int status = entries.getInt(statusColumn);

                String rruleStr = entries.getString(rruleColumn);
                String rdateStr = entries.getString(rdateColumn);
                String exruleStr = entries.getString(exruleColumn);
                String exdateStr = entries.getString(exdateColumn);

                RecurrenceSet recur = new RecurrenceSet(rruleStr, rdateStr, exruleStr, exdateStr);

                if (recur.hasRecurrence()) {
                    // the event is repeating

                    if (status == Events.STATUS_CANCELED) {
                        // should not happen!
                        Log.e(TAG, "Found canceled recurring event in "
                        + "Events table.  Ignoring.");
                        continue;
                    }

                    // need to parse the event into a local calendar.
                    eventTime.timezone = eventTimezone;
                    eventTime.set(dtstartMillis);
                    eventTime.allDay = allDay;

                    if (durationStr == null) {
                        // should not happen.
                        Log.e(TAG, "Repeating event has no duration -- "
                                + "should not happen.");
                        if (allDay) {
                            // set to one day.
                            duration.sign = 1;
                            duration.weeks = 0;
                            duration.days = 1;
                            duration.hours = 0;
                            duration.minutes = 0;
                            duration.seconds = 0;
                            durationStr = "+P1D";
                        } else {
                            // compute the duration from dtend, if we can.
                            // otherwise, use 0s.
                            duration.sign = 1;
                            duration.weeks = 0;
                            duration.days = 0;
                            duration.hours = 0;
                            duration.minutes = 0;
                            if (!entries.isNull(dtendColumn)) {
                                long dtendMillis = entries.getLong(dtendColumn);
                                duration.seconds = (int) ((dtendMillis - dtstartMillis) / 1000);
                                durationStr = "+P" + duration.seconds + "S";
                            } else {
                                duration.seconds = 0;
                                durationStr = "+P0S";
                            }
                        }
                    }

                    try {
                        rp.expand(eventTime, recur,
                                  begin /* range start */, end /* range end */, dates);
                    }
                    catch (DateException e) {
                        Log.w(TAG, "RecurrenceProcessor.expand skipping",e);
                        continue;
                    }

                    // Initialize the "eventTime" timezone outside the loop.
                    // This is used in computeTimezoneDependentFields().
                    if (allDay) {
                        eventTime.timezone = Time.TIMEZONE_UTC;
                    } else {
                        eventTime.timezone = localTimezone;
                    }

                    for (long date : dates) {
                        initialValues = new ContentValues();
                        initialValues.put(Instances.EVENT_ID, eventId);

                        initialValues.put(Instances.BEGIN, date);
                        long dtendMillis = duration.addTo(date);
                        initialValues.put(Instances.END, dtendMillis);

                        computeTimezoneDependentFields(date, dtendMillis,
                                eventTime, initialValues);
                        instancesMap.add(syncId, initialValues);
                    }
                } else {
                    // the event is not repeating

                    // if this event has an "original" field, then record
                    // that we need to cancel the original event (we can't
                    // do that here because the order of this loop isn't
                    // defined)
                    if (originalEvent != null && originalInstanceTimeMillis != -1) {
                        canceled.add(new CanceledInstance(originalInstanceTimeMillis,
                                     originalEvent));
                    }

                    // do not create an instance in the Instances table if this
                    // is a canceled event/exception to a recurrence.
                    if (status == Events.STATUS_CANCELED) {
                        continue;
                    }

                    initialValues = new ContentValues();
                    initialValues.put(Instances.EVENT_ID, eventId);
                    initialValues.put(Instances.BEGIN, dtstartMillis);

                    long dtendMillis = dtstartMillis;
                    if (durationStr == null) {
                        if (!entries.isNull(dtendColumn)) {
                            dtendMillis = entries.getLong(dtendColumn);
                        }
                    } else {
                        dtendMillis = duration.addTo(dtstartMillis);
                    }
                    initialValues.put(Instances.END, dtendMillis);

                    if (allDay) {
                        eventTime.timezone = Time.TIMEZONE_UTC;
                    } else {
                        eventTime.timezone = localTimezone;
                    }
                    computeTimezoneDependentFields(dtstartMillis, dtendMillis,
                            eventTime, initialValues);

                    instancesMap.add(syncId, initialValues);
                }
            }

            // remove the ones that should be canceled

            int numCanceled = canceled.size();
            for (int i=0; i<numCanceled; i++) {
                CanceledInstance cancellation = canceled.get(i);
                if (TextUtils.isEmpty(cancellation.syncId)) {
                    // should this ever happen?
                    continue;
                }
                InstancesList instancesList = instancesMap.get(cancellation.syncId);
                if (instancesList == null) {
                    continue;
                }
                int numInstances = instancesList.size();
                for (int j=numInstances-1; j>=0; j--) {
                    ContentValues m = instancesList.get(j);
                    if (m.get(Instances.BEGIN).equals(cancellation.begin)) {
                        instancesList.remove(j);
                    }
                }
            }

            // Now do the inserts.  Since the db lock is held when this method is executed,
            // this will be done in a transaction.
            // NOTE: if there is lock contention (e.g., a sync is trying to merge into the db
            // while the calendar app is trying to query the db (expanding instances)), we will
            // not be "polite" and yield the lock until we're done.  This will favor local query
            // operations over sync/write operations.
            Collection<InstancesList> lists = instancesMap.values();
            for (InstancesList list : lists) {
                for (ContentValues m : list) {
                    mInstancesInserter.replace(m);
                    if (false) {
                        // yield the lock if anyone else is trying to
                        // perform a db operation here.
                        db.yieldIfContended();
                    }
                }
            }
        } catch (TimeFormatException e) {
            Log.w(TAG, "Exception in instance query preparation", e);
        }
        finally {
            if (entries != null) {
                entries.close();
            }
        }
        if (PROFILE) {
            Debug.stopMethodTracing();
        }
        //System.out.println("EXIT  insertInstanceRange begin=" + begin + " end=" + end);
    }

    /**
     * Computes the timezone-dependent fields of an instance of an event and
     * updates the "values" map to contain those fields.
     *
     * @param begin the start time of the instance (in UTC milliseconds)
     * @param end the end time of the instance (in UTC milliseconds)
     * @param local a Time object with the timezone set to the local timezone
     * @param values a map that will contain the timezone-dependent fields
     */
    private void computeTimezoneDependentFields(long begin, long end,
            Time local, ContentValues values) {
        local.set(begin);
        int startDay = Time.getJulianDay(begin, local.gmtoff);
        int startMinute = local.hour * 60 + local.minute;

        local.set(end);
        int endDay = Time.getJulianDay(end, local.gmtoff);
        int endMinute = local.hour * 60 + local.minute;

        // Special case for midnight, which has endMinute == 0.  Change
        // that to +24 hours on the previous day to make everything simpler.
        // Exception: if start and end minute are both 0 on the same day,
        // then leave endMinute alone.
        if (endMinute == 0 && endDay > startDay) {
            endMinute = 24 * 60;
            endDay -= 1;
        }

        values.put(Instances.START_DAY, startDay);
        values.put(Instances.END_DAY, endDay);
        values.put(Instances.START_MINUTE, startMinute);
        values.put(Instances.END_MINUTE, endMinute);
    }

    private void fillBusyBits(int minDay, int startDay, int endDay, int startMinute,
            int endMinute, boolean allDay, int[] busybits, int[] allDayCounts) {

        // The startDay can be less than the minDay if we have an event
        // that starts earlier than the time range we are interested in.
        // In that case, we ignore the time range that falls outside the
        // the range we are interested in.
        if (startDay < minDay) {
            startDay = minDay;
            startMinute = 0;
        }

        // Likewise, truncate the event's end day so that it doesn't go past
        // the expected range.
        int numDays = busybits.length;
        int stopDay = endDay;
        if (stopDay > minDay + numDays - 1) {
            stopDay = minDay + numDays - 1;
        }
        int dayIndex = startDay - minDay;

        if (allDay) {
            for (int day = startDay; day <= stopDay; day++, dayIndex++) {
                allDayCounts[dayIndex] += 1;
            }
            return;
        }

        for (int day = startDay; day <= stopDay; day++, dayIndex++) {
            int endTime = endMinute;
            // If the event ends on a future day, then show it extending to
            // the end of this day.
            if (endDay > day) {
                endTime = 24 * 60;
            }

            int startBit = startMinute / BUSYBIT_INTERVAL ;
            int endBit = (endTime + BUSYBIT_INTERVAL - 1) / BUSYBIT_INTERVAL;
            int len = endBit - startBit;
            if (len == 0) {
                len = 1;
            }
            if (len < 0 || len > 24) {
                Log.e("Cal", "fillBusyBits() error: len " + len
                        + " startMinute,endTime " + startMinute + " , " + endTime
                        + " startDay,endDay " + startDay + " , " + endDay);
            } else {
                int oneBits = BIT_MASKS[len];
                busybits[dayIndex] |= oneBits << startBit;
            }

            // Set the start minute to the beginning of the day, in
            // case this event spans multiple days.
            startMinute = 0;
        }
    }

    private void mergeBusyBits(int startDay, int endDay, int[] busybits, int[] allDayCounts) {
        mDb.beginTransaction();
        try {
            mergeBusyBitsLocked(startDay, endDay, busybits, allDayCounts);
            mDb.setTransactionSuccessful();
        } finally {
            mDb.endTransaction();
        }
    }

    private void mergeBusyBitsLocked(int startDay, int endDay, int[] busybits,
            int[] allDayCounts) {
        final SQLiteDatabase db = getDatabase();
        Cursor cursor = null;
        try {
            String selection = "day>=" + startDay + " AND day<=" + endDay;
            cursor = db.query("BusyBits", sBusyBitProjection, selection, null, null, null, null);
            if (cursor == null) {
                return;
            }
            while (cursor.moveToNext()) {
                int day = cursor.getInt(BUSYBIT_INDEX_DAY);
                int busy = cursor.getInt(BUSYBIT_INDEX_BUSYBITS);
                int allDayCount = cursor.getInt(BUSYBIT_INDEX_ALL_DAY_COUNT);

                int dayIndex = day - startDay;
                busybits[dayIndex] |= busy;
                allDayCounts[dayIndex] += allDayCount;
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        // Allocate a map that we can reuse
        ContentValues values = new ContentValues();

        // Write the busy bits to the database
        int len = busybits.length;
        for (int dayIndex = 0; dayIndex < len; dayIndex++) {
            int busy = busybits[dayIndex];
            int allDayCount = allDayCounts[dayIndex];
            if (busy == 0 && allDayCount == 0) {
                continue;
            }
            int day = startDay + dayIndex;

            values.clear();
            values.put(BusyBits.DAY, day);
            values.put(BusyBits.BUSYBITS, busy);
            values.put(BusyBits.ALL_DAY_COUNT, allDayCount);
            db.replace("BusyBits", null, values);
        }
    }

    /**
     * Updates the BusyBit table when a new event is inserted into the Events
     * table.  This is called after the event has been entered into the Events
     * table.  If the event time is not within the date range of the current
     * BusyBits table, then the busy bits are not updated.  The BusyBits
     * table is not automatically expanded to include this event.
     *
     * @param eventId the id of the newly created event
     * @param values the ContentValues for the new event
     */
    private void insertBusyBitsLocked(long eventId, ContentValues values) {
        MetaData.Fields fields = mMetaData.getFieldsLocked();
        if (fields.maxBusyBit == 0) {
            return;
        }

        // If this is a recurrence event, then the expanded Instances range
        // should be 0 because this is called after updateInstancesLocked().
        // But for now check this condition and report an error if it occurs.
        // In the future, we could even support recurring events by
        // expanding them here and updating the busy bits for each instance.
        if (isRecurrenceEvent(values))  {
            Log.e(TAG, "insertBusyBitsLocked(): unexpected recurrence event\n");
            return;
        }

        long dtstartMillis = values.getAsLong(Events.DTSTART);
        Long dtendMillis = values.getAsLong(Events.DTEND);
        if (dtendMillis == null) {
            dtendMillis = dtstartMillis;
        }

        boolean allDay = false;
        Integer allDayInteger = values.getAsInteger(Events.ALL_DAY);
        if (allDayInteger != null) {
            allDay = allDayInteger != 0;
        }

        Time time = new Time();
        if (allDay) {
            time.timezone = Time.TIMEZONE_UTC;
        }

        ContentValues busyValues = new ContentValues();
        computeTimezoneDependentFields(dtstartMillis, dtendMillis, time, busyValues);

        int startDay = busyValues.getAsInteger(Instances.START_DAY);
        int endDay = busyValues.getAsInteger(Instances.END_DAY);

        // If the event time is not in the expanded BusyBits range,
        // then return.
        if (startDay > fields.maxBusyBit || endDay < fields.minBusyBit) {
            return;
        }

        // Allocate space for the busy bits, one 32-bit integer for each day,
        // plus 24 bytes for the count of events that occur in each time slot.
        int numDays = endDay - startDay + 1;
        int[] busybits = new int[numDays];
        int[] allDayCounts = new int[numDays];

        int startMinute = busyValues.getAsInteger(Instances.START_MINUTE);
        int endMinute = busyValues.getAsInteger(Instances.END_MINUTE);
        fillBusyBits(startDay, startDay, endDay, startMinute, endMinute,
                allDay, busybits, allDayCounts);
        mergeBusyBits(startDay, endDay, busybits, allDayCounts);
    }

    /**
     * Updates the busy bits for an event that is being updated.  This is
     * called before the event is updated in the Events table because we need
     * to know the time of the event before it was changed.
     *
     * @param eventId the id of the event being updated
     * @param values the ContentValues for the updated event
     */
    private void updateBusyBitsLocked(long eventId, ContentValues values) {
        MetaData.Fields fields = mMetaData.getFieldsLocked();
        if (fields.maxBusyBit == 0) {
            return;
        }

        // If this is a recurring event, then clear the BusyBits table.
        if (isRecurrenceEvent(values))  {
            mMetaData.writeLocked(fields.timezone, fields.minInstance, fields.maxInstance,
                    0 /* startDay */, 0 /* endDay */);
            return;
        }

        // If the event fields being updated don't contain the start or end
        // time, then we don't need to bother updating the BusyBits table.
        Long dtstartLong = values.getAsLong(Events.DTSTART);
        Long dtendLong = values.getAsLong(Events.DTEND);
        if (dtstartLong == null && dtendLong == null) {
            return;
        }

        // If the timezone has changed, then clear the busy bits table
        // and return.
        String dbTimezone = fields.timezone;
        String localTimezone = TimeZone.getDefault().getID();
        boolean timezoneChanged = (dbTimezone == null) || !dbTimezone.equals(localTimezone);
        if (timezoneChanged) {
            mMetaData.writeLocked(fields.timezone, fields.minInstance, fields.maxInstance,
                    0 /* startDay */, 0 /* endDay */);
            return;
        }

        // Read the existing event start and end times from the Events table.
        TimeRange eventRange = readEventStartEnd(eventId);

        // Fill in the new start time (if missing) or the new end time (if
        // missing) from the existing event start and end times.
        long dtstartMillis;
        if (dtstartLong != null) {
            dtstartMillis = dtstartLong;
        } else {
            dtstartMillis = eventRange.begin;
        }

        long dtendMillis;
        if (dtendLong != null) {
            dtendMillis = dtendLong;
        } else {
            dtendMillis = eventRange.end;
        }

        // Compute the start and end Julian days for the event.
        Time time = new Time();
        if (eventRange.allDay) {
            time.timezone = Time.TIMEZONE_UTC;
        }
        ContentValues busyValues = new ContentValues();
        computeTimezoneDependentFields(eventRange.begin, eventRange.end, time, busyValues);
        int oldStartDay = busyValues.getAsInteger(Instances.START_DAY);
        int oldEndDay = busyValues.getAsInteger(Instances.END_DAY);

        boolean allDay = false;
        Integer allDayInteger = values.getAsInteger(Events.ALL_DAY);
        if (allDayInteger != null) {
            allDay = allDayInteger != 0;
        }

        if (allDay) {
            time.timezone = Time.TIMEZONE_UTC;
        } else {
            time.timezone = TimeZone.getDefault().getID();
        }

        computeTimezoneDependentFields(dtstartMillis, dtendMillis, time, busyValues);
        int newStartDay = busyValues.getAsInteger(Instances.START_DAY);
        int newEndDay = busyValues.getAsInteger(Instances.END_DAY);

        // If both the old and new event times are outside the expanded
        // BusyBits table, then return.
        if ((oldStartDay > fields.maxBusyBit || oldEndDay < fields.minBusyBit)
                && (newStartDay > fields.maxBusyBit || newEndDay < fields.minBusyBit)) {
            return;
        }

        // If the old event time is within the expanded Instances range,
        // then clear the BusyBits table and return.
        if (oldStartDay <= fields.maxBusyBit && oldEndDay >= fields.minBusyBit) {
            // We could recompute the busy bits for the days containing the
            // old event time.  For now, just clear the BusyBits table.
            mMetaData.writeLocked(fields.timezone, fields.minInstance, fields.maxInstance,
                    0 /* startDay */, 0 /* endDay */);
            return;
        }

        // The new event time is within the expanded Instances range.
        // So insert the busy bits for that day (or days).

        // Allocate space for the busy bits, one 32-bit integer for each day,
        // plus 24 bytes for the count of events that occur in each time slot.
        int numDays = newEndDay - newStartDay + 1;
        int[] busybits = new int[numDays];
        int[] allDayCounts = new int[numDays];

        int startMinute = busyValues.getAsInteger(Instances.START_MINUTE);
        int endMinute = busyValues.getAsInteger(Instances.END_MINUTE);
        fillBusyBits(newStartDay, newStartDay, newEndDay, startMinute, endMinute,
                allDay, busybits, allDayCounts);
        mergeBusyBits(newStartDay, newEndDay, busybits, allDayCounts);
    }

    /**
     * This method is called just before an event is deleted.
     *
     * @param eventId
     */
    private void deleteBusyBitsLocked(long eventId) {
        MetaData.Fields fields = mMetaData.getFieldsLocked();
        if (fields.maxBusyBit == 0) {
            return;
        }

        // TODO: if the event being deleted is not a recurring event and the
        // start and end time are outside the BusyBit range, then we could
        // avoid clearing the BusyBits table.  For now, always clear the
        // BusyBits table because deleting events is relatively rare.
        mMetaData.writeLocked(fields.timezone, fields.minInstance, fields.maxInstance,
                0 /* startDay */, 0 /* endDay */);
    }

    // Read the start and end time for an event from the Events table.
    // Also read the "all-day" indicator.
    private TimeRange readEventStartEnd(long eventId) {
        Cursor cursor = null;
        TimeRange range = new TimeRange();
        try {
            cursor = query(ContentUris.withAppendedId(Events.CONTENT_URI, eventId),
                    new String[] { Events.DTSTART, Events.DTEND, Events.ALL_DAY },
                    null /* selection */,
                    null /* selectionArgs */,
                    null /* sort */);
            if (cursor == null) {
                return null;
            }
            cursor.moveToFirst();
            range.begin = cursor.getLong(0);
            range.end = cursor.getLong(1);
            range.allDay = cursor.getInt(2) != 0;
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }
        return range;
    }

    @Override
    public String getType(Uri url) {
        int match = sURLMatcher.match(url);
        switch (match) {
        case EVENTS:
            return "vnd.android.cursor.dir/event";
        case EVENTS_ID:
            return "vnd.android.cursor.item/event";
        case REMINDERS:
            return "vnd.android.cursor.dir/reminder";
        case REMINDERS_ID:
            return "vnd.android.cursor.item/reminder";
        case CALENDAR_ALERTS:
            return "vnd.android.cursor.dir/calendar-alert";
        case CALENDAR_ALERTS_BY_INSTANCE:
            return "vnd.android.cursor.dir/calendar-alert-by-instance";
        case CALENDAR_ALERTS_ID:
            return "vnd.android.cursor.item/calendar-alert";
        case INSTANCES:
            return "vnd.android.cursor.dir/event-instance";
        case BUSYBITS:
            return "vnd.android.cursor.dir/busybits";
        default:
            throw new IllegalArgumentException("Unknown URL " + url);
        }
    }

    public static boolean isRecurrenceEvent(ContentValues values) {
        return (!TextUtils.isEmpty(values.getAsString(Events.RRULE))||
                !TextUtils.isEmpty(values.getAsString(Events.RDATE))||
                !TextUtils.isEmpty(values.getAsString(Events.ORIGINAL_EVENT)));
    }

    @Override
    public Uri insertInternal(Uri url, ContentValues initialValues) {
        final SQLiteDatabase db = getDatabase();
        long rowID;

        int match = sURLMatcher.match(url);
        switch (match) {
            case EVENTS:
                if (!isTemporary()) {
                    initialValues.put(Events._SYNC_DIRTY, 1);

                    // Disallow inserting the attendee status in the Events
                    // table because that makes it harder to keep the value
                    // consistent with the corresponding entry in the
                    // Attendees table.  Note that it's okay (and expected)
                    // for the temporary table to contain the attendee status
                    // because that comes from the server sync and the Events
                    // table is already consistent with the Attendees table.
                    if (initialValues.containsKey(Events.SELF_ATTENDEE_STATUS)) {
                        throw new IllegalArgumentException("Inserting "
                                + Events.SELF_ATTENDEE_STATUS
                                + " in Events table is not allowed");
                    }

                    if (!initialValues.containsKey(Events.DTSTART)) {
                        throw new RuntimeException("DTSTART field missing from event");
                    }
                }
                // TODO: avoid the call to updateBundleFromEvent if this is just finding local
                // changes.  or avoid for temp providers altogether, if we can compute this
                // during a merge.
                // TODO: do we really need to make a copy?
                ContentValues updatedValues = updateContentValuesFromEvent(initialValues);
                if (updatedValues == null) {
                    throw new RuntimeException("Could not insert event.");
                    // return null;
                }
                long rowId = mEventsInserter.insert(updatedValues);
                Uri uri = Uri.parse("content://" + url.getAuthority() + "/events/" + rowId);
                if (!isTemporary() && rowId != -1) {
                    updateEventRawTimesLocked(rowId, updatedValues);
                    updateInstancesLocked(updatedValues, rowId, true /* new event */, db);
                    insertBusyBitsLocked(rowId, updatedValues);
                }

                return uri;
            case CALENDARS:
                if (!isTemporary()) {
                    Integer syncEvents = initialValues.getAsInteger(Calendars.SYNC_EVENTS);
                    if (syncEvents != null && syncEvents == 1) {
                        String account = initialValues.getAsString(Calendars._SYNC_ACCOUNT);
                        String calendarUrl = initialValues.getAsString(Calendars.URL);
                        scheduleSync(account, false /* two-way sync */, calendarUrl);
                    }
                }
                rowID = mCalendarsInserter.insert(initialValues);
                return Uri.parse("content://calendar/calendars/" + rowID);
            case ATTENDEES:
                // currently, only sync may insert attendees.  we do not support
                // inserting or removing attendees (which can only be yourself)
                // from the app.
                // TODO: remove this restriction when we deal with the full
                // attendees feed.  we'll also need to put in some protection to
                // prevent updates to the attendees that the server might reject.
                if (!isTemporary()) {
                    throw new IllegalArgumentException("Can only insert attendees into "
                    + "the temporary provider.");
                }
                if (!initialValues.containsKey(Attendees.EVENT_ID)) {
                    throw new IllegalArgumentException("Attendees values must "
                        + "contain an event_id");
                }
                rowID = mAttendeesInserter.insert(initialValues);

                // Copy the attendee status value to the Events table.
                updateEventAttendeeStatus(db, initialValues);

                return Uri.parse("content://calendars/attendees/" + rowID);
            case REMINDERS:
                if (!initialValues.containsKey(Reminders.EVENT_ID)) {
                    throw new IllegalArgumentException("Reminders values must "
                        + "contain an event_id");
                }
                rowID = mRemindersInserter.insert(initialValues);

                if (!isTemporary()) {
                    // Schedule another event alarm, if necessary
                    scheduleNextAlarm();
                }
                return Uri.parse("content://calendars/reminders/" + rowID);
            case CALENDAR_ALERTS:
                if (!initialValues.containsKey(CalendarAlerts.EVENT_ID)) {
                    throw new IllegalArgumentException("CalendarAlerts values must "
                        + "contain an event_id");
                }
                rowID = mCalendarAlertsInserter.insert(initialValues);

                return Uri.parse(CalendarAlerts.CONTENT_URI + "/" + rowID);
            case EXTENDED_PROPERTIES:
                if (!initialValues.containsKey(Calendar.ExtendedProperties.EVENT_ID)) {
                    throw new IllegalArgumentException("ExtendedProperties values must "
                        + "contain an event_id");
                }
                rowID = mExtendedPropertiesInserter.insert(initialValues);

                return Uri.parse("content://calendars/extendedproperties/" + rowID);
            case DELETED_EVENTS:
                if (isTemporary()) {
                    rowID = mDeletedEventsInserter.insert(initialValues);
                    return Uri.parse("content://calendar/deleted_events/" + rowID);
                }
                // fallthrough
            case EVENTS_ID:
            case REMINDERS_ID:
            case CALENDAR_ALERTS_ID:
            case EXTENDED_PROPERTIES_ID:
            case INSTANCES:
                throw new UnsupportedOperationException("Cannot insert into that URL");
            default:
                throw new IllegalArgumentException("Unknown URL " + url);
        }
    }

    /**
     * Extracts the calendar email from a calendar feed url.
     * @param feed the calendar feed url
     * @return the calendar email that is in the feed url or null if it can't
     * find the email address.
     */
    private String calendarEmailAddressFromFeedUrl(String feed) {
        // Example feed url:
        // https://www.google.com/calendar/feeds/foo%40gmail.com/private/full-noattendees
        String[] pathComponents = feed.split("/");
        if (pathComponents.length > 5 && "feeds".equals(pathComponents[4])) {
            try {
                return URLDecoder.decode(pathComponents[5], "UTF-8");
            } catch (UnsupportedEncodingException e) {
                Log.e(TAG, "unable to url decode the email address in calendar " + feed);
                return null;
            }
        }

        Log.e(TAG, "unable to find the email address in calendar " + feed);
        return null;
    }


    /**
     * Updates the attendee status in the Events table to be consistent with
     * the value in the Attendees table.
     *
     * @param db the database
     * @param attendeeValues the column values for one row in the Attendees
     * table.
     */
    private void updateEventAttendeeStatus(SQLiteDatabase db, ContentValues attendeeValues) {
        // Get the event id for this attendee
        long eventId = attendeeValues.getAsLong(Attendees.EVENT_ID);

        // Currently, we only fetch the attendee for the owner of the calendar
        // so all the following expensive code is just wasted overhead.
        // When we actually support multiple attendees for an event, we will
        // have to execute this code (and perhaps tune it to make it as
        // efficient as possible).
        if (MULTIPLE_ATTENDEES_PER_EVENT) {
            // Get the calendar id for this event
            Cursor cursor = null;
            long calId;
            try {
                cursor = query(ContentUris.withAppendedId(Events.CONTENT_URI, eventId),
                        new String[] { Events.CALENDAR_ID },
                        null /* selection */,
                        null /* selectionArgs */,
                        null /* sort */);
                if (cursor == null) {
                    return;
                }
                cursor.moveToFirst();
                calId = cursor.getLong(0);
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }

            // Get the feed for this Calendar
            String calendarUrl = null;
            cursor = null;
            try {
                cursor = query(ContentUris.withAppendedId(Calendars.CONTENT_URI, calId),
                        new String[] { Calendars.URL },
                        null /* selection */,
                        null /* selectionArgs */,
                        null /* sort */);
                if (cursor == null) {
                    return;
                }
                cursor.moveToFirst();
                calendarUrl = cursor.getString(0);
            } finally {
                if (cursor != null) {
                    cursor.close();
                }
            }

            // Get the email address from the calendar feed
            String calendarEmail = calendarEmailAddressFromFeedUrl(calendarUrl);
            if (calendarEmail == null) {
                return;
            }

            // Get the email address for this attendee
            String attendeeEmail = null;
            if (attendeeValues.containsKey(Attendees.ATTENDEE_EMAIL)) {
                attendeeEmail = attendeeValues.getAsString(Attendees.ATTENDEE_EMAIL);
            }

            // If the attendee email does not match the calendar email, then this
            // attendee is not the owner of this calendar so we don't update the
            // selfAttendeeStatus in the event.
            if (!calendarEmail.equals(attendeeEmail)) {
                return;
            }
        }

        int status = Attendees.ATTENDEE_STATUS_NONE;
        if (attendeeValues.containsKey(Attendees.ATTENDEE_RELATIONSHIP)) {
            int rel = attendeeValues.getAsInteger(Attendees.ATTENDEE_RELATIONSHIP);
            if (rel == Attendees.RELATIONSHIP_ORGANIZER) {
                status = Attendees.ATTENDEE_STATUS_ACCEPTED;
            }
        }

        if (attendeeValues.containsKey(Attendees.ATTENDEE_STATUS)) {
            status = attendeeValues.getAsInteger(Attendees.ATTENDEE_STATUS);
        }

        ContentValues values = new ContentValues();
        values.put(Events.SELF_ATTENDEE_STATUS, status);
        db.update("Events", values, "_id="+eventId, null);
    }

    private void updateInstancesLocked(ContentValues values,
                                       long rowId,
                                       boolean newEvent,
                                       SQLiteDatabase db) {
        if (isRecurrenceEvent(values))  {
            // TODO: insert the new recurrence into the instances table.
            mMetaData.clearInstanceRange();
            return;
        }

        // If there are no expanded Instances, then return.
        MetaData.Fields fields = mMetaData.getFieldsLocked();
        if (fields.maxInstance == 0) {
            return;
        }

        // if the event is in the expanded range, insert
        // into the instances table.
        // TODO: deal with durations.  currently, durations are only used in
        // recurrences.

        Long dtstartMillis = values.getAsLong(Events.DTSTART);

        if (dtstartMillis == null) {
            if (newEvent) {
                // must be present for a new event.
                throw new RuntimeException("DTSTART missing.");
            }
            if (Config.LOGV) Log.v(TAG, "Missing DTSTART.  "
                    + "No need to update instance.");
            return;
        }

        if (!newEvent) {
            db.delete("Instances", "event_id=" + rowId, null /* selectionArgs */);
        }

        Long dtendMillis = values.getAsLong(Events.DTEND);
        if (dtendMillis == null) {
            dtendMillis = dtstartMillis;
        }

        if (dtstartMillis <= fields.maxInstance && dtendMillis >= fields.minInstance) {
            ContentValues instanceValues = new ContentValues();
            instanceValues.put(Instances.EVENT_ID, rowId);
            instanceValues.put(Instances.BEGIN, dtstartMillis);
            instanceValues.put(Instances.END, dtendMillis);

            boolean allDay = false;
            Integer allDayInteger = values.getAsInteger(Events.ALL_DAY);
            if (allDayInteger != null) {
                allDay = allDayInteger != 0;
            }

            // Update the timezone-dependent fields.
            Time local = new Time();
            if (allDay) {
                local.timezone = Time.TIMEZONE_UTC;
            } else {
                local.timezone = fields.timezone;
            }

            computeTimezoneDependentFields(dtstartMillis, dtendMillis, local, instanceValues);
            mInstancesInserter.insert(instanceValues);
        }
    }

    long calculateLastDate(ContentValues values)
                    throws DateException {
        // Allow updates to some event fields like the title or hasAlarm
        // without requiring DTSTART.
        if (!values.containsKey(Events.DTSTART)) {
            if (values.containsKey(Events.DTEND) || values.containsKey(Events.RRULE)
                    || values.containsKey(Events.DURATION)
                    || values.containsKey(Events.EVENT_TIMEZONE)
                    || values.containsKey(Events.RDATE)
                    || values.containsKey(Events.EXRULE)
                    || values.containsKey(Events.EXDATE)) {
                throw new RuntimeException("DTSTART field missing from event");
            }
            return -1;
        }
        long dtstartMillis = values.getAsLong(Events.DTSTART);
        long lastMillis = -1;

        // Can we use dtend with a repeating event?  What does that even
        // mean?
        // NOTE: if the repeating event has a dtend, we convert it to a
        // duration during event processing, so this situation should not
        // occur.
        Long dtEnd = values.getAsLong(Events.DTEND);
        if (dtEnd != null) {
            lastMillis = dtEnd;
        } else {
            // find out how long it is
            Duration duration = new Duration();
            String durationStr = values.getAsString(Events.DURATION);
            if (durationStr != null) {
                duration.parse(durationStr);
            }

            RecurrenceSet recur = new RecurrenceSet(values);

            if (recur.hasRecurrence()) {
                // the event is repeating, so find the last date it
                // could appear on

                String tz = values.getAsString(Events.EVENT_TIMEZONE);

                if (TextUtils.isEmpty(tz)) {
                    // floating timezone
                    tz = Time.TIMEZONE_UTC;
                }
                Time dtstartLocal = new Time(tz);

                dtstartLocal.set(dtstartMillis);

                RecurrenceProcessor rp = new RecurrenceProcessor();
                lastMillis = rp.getLastOccurence(dtstartLocal, recur);
                if (lastMillis == -1) {
                    return lastMillis;  // -1
                }
            } else {
                // the event is not repeating, just use dtstartMillis
                lastMillis = dtstartMillis;
            }

            // that was the beginning of the event.  this is the end.
            lastMillis = duration.addTo(lastMillis);
        }
        return lastMillis;
    }

    private ContentValues updateContentValuesFromEvent(ContentValues initialValues) {
        try {
            ContentValues values = new ContentValues(initialValues);

            long last = calculateLastDate(values);
            if (last != -1) {
                values.put(Events.LAST_DATE, last);
            }

            return values;
        } catch (DateException e) {
            // don't add it if there was an error
            Log.w(TAG, "Could not calculate last date.", e);
            return null;
        }
    }

    private void updateEventRawTimesLocked(long eventId, ContentValues values) {
        ContentValues rawValues = new ContentValues();

        rawValues.put("event_id", eventId);

        String timezone = values.getAsString(Events.EVENT_TIMEZONE);

        if (TextUtils.isEmpty(timezone)) {
            // floating timezone
            timezone = Time.TIMEZONE_UTC;
        }

        Time time = new Time(timezone);
        Long dtstartMillis = values.getAsLong(Events.DTSTART);
        if (dtstartMillis != null) {
            time.set(dtstartMillis);
            rawValues.put("dtstart2445", time.format2445());
        }

        Long dtendMillis = values.getAsLong(Events.DTEND);
        if (dtendMillis != null) {
            time.set(dtendMillis);
            rawValues.put("dtend2445", time.format2445());
        }

        Long originalInstanceMillis = values.getAsLong(Events.ORIGINAL_INSTANCE_TIME);
        if (originalInstanceMillis != null) {
            time.set(originalInstanceMillis);
            rawValues.put("originalInstanceTime2445", time.format2445());
        }

        Long lastDateMillis = values.getAsLong(Events.LAST_DATE);
        if (lastDateMillis != null) {
            time.set(lastDateMillis);
            rawValues.put("lastDate2445", time.format2445());
        }

        mEventsRawTimesInserter.replace(rawValues);
    }

    @Override
    public int deleteInternal(Uri url, String where, String[] whereArgs) {
        final SQLiteDatabase db = getDatabase();
        int match = sURLMatcher.match(url);
        switch (match)
        {
            case EVENTS_ID:
            {
                String id = url.getLastPathSegment();
                if (where != null) {
                    throw new UnsupportedOperationException("CalendarProvider "
                            + "doesn't support where based deletion for type "
                            + match);
                }
                if (!isTemporary()) {
                    deleteBusyBitsLocked(Integer.parseInt(id));

                    // Query this event to get the fields needed for inserting
                    // a new row in the DeletedEvents table.
                    Cursor cursor = db.query("Events", EVENTS_PROJECTION,
                            "_id=" + id, null, null, null, null);
                    try {
                        if (cursor.moveToNext()) {
                            String syncId = cursor.getString(EVENTS_SYNC_ID_INDEX);
                            String syncVersion = cursor.getString(EVENTS_SYNC_VERSION_INDEX);
                            String syncAccount = cursor.getString(EVENTS_SYNC_ACCOUNT_INDEX);
                            Long calId = cursor.getLong(EVENTS_CALENDAR_ID_INDEX);
                            
                            ContentValues values = new ContentValues();
                            values.put(Events._SYNC_ID, syncId);
                            values.put(Events._SYNC_VERSION, syncVersion);
                            values.put(Events._SYNC_ACCOUNT, syncAccount);
                            values.put(Events.CALENDAR_ID, calId);
                            mDeletedEventsInserter.insert(values);
                        }
                    } finally {
                        cursor.close();
                        cursor = null;
                    }
                }

                // There is a delete trigger that will cause all instances
                // matching this event id to get deleted as well.  In fact, all
                // of the following tables will remove entries matching this
                // event id: Instances, EventsRawTimes, Attendees, Reminders,
                // CalendarAlerts, and ExtendedProperties.
                int result = db.delete("Events", "_id=" + id, null);
                return result;
            }
            case ATTENDEES_ID:
            {
              // we currently don't support deletions to the attendees list.
              // TODO: remove this restriction when we handle the full attendees
              // feed.  we'll need to put in some logic to check that the
              // modification will be allowed by the server.
              throw new IllegalArgumentException("Cannot delete attendees.");
              //                String id = url.getPathSegments().get(1);
              //                int result = db.delete("Attendees", "_id="+id, null);
              //                return result;
            }
            case REMINDERS:
            {
                int result = db.delete("Reminders", where, whereArgs);
                return result;
            }
            case REMINDERS_ID:
            {
                String id = url.getLastPathSegment();
                int result = db.delete("Reminders", "_id="+id, null);
                return result;
            }
            case CALENDAR_ALERTS:
            {
                int result = db.delete("CalendarAlerts", where, whereArgs);
                return result;
            }
            case CALENDAR_ALERTS_ID:
            {
                String id = url.getLastPathSegment();
                int result = db.delete("CalendarAlerts", "_id="+id, null);
                return result;
            }
            case DELETED_EVENTS:
            case EVENTS:
                throw new UnsupportedOperationException("Cannot delete that URL");
            case CALENDARS_ID:
                StringBuilder whereSb = new StringBuilder("_id=");
                whereSb.append(url.getPathSegments().get(1));
                if (!TextUtils.isEmpty(where)) {
                    whereSb.append(" AND (");
                    whereSb.append(where);
                    whereSb.append(')');
                }
                where = whereSb.toString();
                // fall through to CALENDARS for the actual delete
            case CALENDARS:
                return deleteMatchingCalendars(where);
            case INSTANCES:
                throw new UnsupportedOperationException("Cannot delete that URL");
            default:
                throw new IllegalArgumentException("Unknown URL " + url);
        }
    }

    private int deleteMatchingCalendars(String where) {
        // query to find all the calendars that match, for each
        // - delete calendar subscription
        // - delete calendar

        int numDeleted = 0;
        final SQLiteDatabase db = getDatabase();
        Cursor c = db.query("Calendars", sCalendarsIdProjection, where, null,
                null, null, null);
        if (c == null) {
            return 0;
        }
        try {
            while (c.moveToNext()) {
                long id = c.getLong(CALENDARS_INDEX_ID);
                if (!isTemporary()) {
                    modifyCalendarSubscription(id, false /* not selected */);
                }
                c.deleteRow();
                numDeleted++;
            }
        } finally {
            c.close();
        }
        return numDeleted;
    }

    // TODO: call calculateLastDate()!
    @Override
    public int updateInternal(Uri url, ContentValues values,
            String where, String[] selectionArgs) {
        // TODO: remove this restriction
        if (!TextUtils.isEmpty(where)) {
            throw new IllegalArgumentException(
                    "WHERE based updates not supported");
        }
        final SQLiteDatabase db = getDatabase();

        int match = sURLMatcher.match(url);
        switch (match) {
            case CALENDARS_ID:
            {
                long id = ContentUris.parseId(url);
                Integer syncEvents = values.getAsInteger(Calendars.SYNC_EVENTS);
                if (syncEvents != null && !isTemporary()) {
                    modifyCalendarSubscription(id, syncEvents == 1);
                }

                int result = db.update("Calendars", values, "_id="+ id, null);
                if (!isTemporary()) {
                    // When we change the display status of a Calendar
                    // we need to update the busy bits.
                    if (values.containsKey(Calendars.SELECTED) || (syncEvents != null)) {
                        // Clear the BusyBits table.
                        mMetaData.clearBusyBitRange();
                    }
                }

                return result;
            }
            case EVENTS_ID:
            {
                long id = ContentUris.parseId(url);
                if (!isTemporary()) {
                    values.put(Events._SYNC_DIRTY, 1);

                    // Disallow updating the attendee status in the Events
                    // table.  In the future, we could support this but we
                    // would have to query and update the attendees table
                    // to keep the values consistent.
                    if (values.containsKey(Events.SELF_ATTENDEE_STATUS)) {
                        throw new IllegalArgumentException("Updating "
                                + Events.SELF_ATTENDEE_STATUS
                                + " in Events table is not allowed.");
                    }

                    if (values.containsKey(Events.HTML_URI)) {
                        throw new IllegalArgumentException("Updating "
                                + Events.HTML_URI
                                + " in Events table is not allowed.");
                    }

                    updateBusyBitsLocked(id, values);
                }

                ContentValues updatedValues = updateContentValuesFromEvent(values);
                if (updatedValues == null) {
                    Log.w(TAG, "Could not update event.");
                    return 0;
                }

                int result = db.update("Events", updatedValues, "_id="+id, null);
                if (!isTemporary()) {
                    if (result > 0) {
                        updateEventRawTimesLocked(id, updatedValues);
                        updateInstancesLocked(updatedValues, id, false /* not a new event */, db);

                        if (values.containsKey(Events.DTSTART)) {
                            // The start time of the event changed, so run the
                            // event alarm scheduler.
                            scheduleNextAlarm();
                        }
                    }
                }
                return result;
            }
            case ATTENDEES_ID:
            {
                // Copy the attendee status value to the Events table.
                updateEventAttendeeStatus(db, values);

                long id = ContentUris.parseId(url);
                return db.update("Attendees", values, "_id="+id, null);
            }
            case CALENDAR_ALERTS_ID:
            {
                long id = ContentUris.parseId(url);
                return db.update("CalendarAlerts", values, "_id="+id, null);
            }
            case REMINDERS_ID:
            {
                long id = ContentUris.parseId(url);
                int result = db.update("Reminders", values, "_id="+id, null);
                if (!isTemporary()) {
                    // Reschedule the event alarms because the
                    // "minutes" field may have changed.
                    scheduleNextAlarm();
                }
                return result;
            }
            case EXTENDED_PROPERTIES_ID:
            {
                long id = ContentUris.parseId(url);
                return db.update("ExtendedProperties", values, "_id="+id, null);
            }
            default:
                throw new IllegalArgumentException("Unknown URL " + url);
        }
    }
    
    /**
     * Schedule a calendar sync for the account.
     * @param account the account for which to schedule a sync
     * @param uploadChangesOnly if set, specify that the sync should only send
     *   up local changes
     * @param url the url feed for the calendar to sync (may be null)
     */
    private void scheduleSync(String account, boolean uploadChangesOnly, String url) {
        Bundle extras = new Bundle();
        extras.putString(ContentResolver.SYNC_EXTRAS_ACCOUNT, account);
        extras.putBoolean(ContentResolver.SYNC_EXTRAS_UPLOAD, uploadChangesOnly);
        if (url != null) {
            extras.putString("feed", url);
            extras.putBoolean(ContentResolver.SYNC_EXTRAS_FORCE, true);
        }
        getContext().getContentResolver().startSync(android.provider.Calendar.CONTENT_URI, extras);
    }

    private void modifyCalendarSubscription(long id, boolean syncEvents) {
        // get the account, url, and current selected state
        // for this calendar.
        Cursor cursor = query(ContentUris.withAppendedId(Calendars.CONTENT_URI, id),
                new String[] { Calendars._SYNC_ACCOUNT,
                               Calendars.URL,
                               Calendars.SYNC_EVENTS},
                null /* selection */,
                null /* selectionArgs */,
                null /* sort */);

        String account = null;
        String calendarUrl = null;
        boolean oldSyncEvents = false;
        if (cursor != null) {
            try {
                cursor.moveToFirst();
                account = cursor.getString(0);
                calendarUrl = cursor.getString(1);
                oldSyncEvents = (cursor.getInt(2) != 0);
            } finally {
                cursor.close();
            }
        }

        if (TextUtils.isEmpty(account) || TextUtils.isEmpty(calendarUrl)) {
            // should not happen?
            Log.w(TAG, "Cannot update subscription because account "
            + "or calendar url empty -- should not happen.");
            return;
        }

        if (oldSyncEvents == syncEvents) {
            // nothing to do
            return;
        }

        // If we are no longer syncing a calendar then make sure that the
        // old calendar sync data is cleared.  Then if we later add this
        // calendar back, we will sync all the events.
        if (!syncEvents) {
            byte[] data = readSyncDataBytes(account);
            GDataSyncData syncData = AbstractGDataSyncAdapter.newGDataSyncDataFromBytes(data);
            if (syncData != null) {
                syncData.feedData.remove(calendarUrl);
                data = AbstractGDataSyncAdapter.newBytesFromGDataSyncData(syncData);
                writeSyncDataBytes(account, data);
            }

            // Delete all of the events in this calendar to save space.
            // This is the closest we can come to deleting a calendar.
            // Clients should never actually delete a calendar.  That won't
            // work.  We need to keep the calendar entry in the Calendars table
            // in order to know not to sync the events for that calendar from
            // the server.
            final SQLiteDatabase db = getDatabase();
            String[] args = new String[] {Long.toString(id)};
            db.delete("Events", CALENDAR_ID_SELECTION, args);
            // Note that we do not delete the matching entries
            // in the DeletedEvents table.  We will let those
            // deleted events propagate to the server.
            
            // TODO: there is a corner case to deal with here: namely, if
            // we edit or delete an event on the phone and then remove
            // (that is, stop syncing) a calendar, and if we also make a
            // change on the server to that event at about the same time,
            // then we will never propagate the changes from the phone to
            // the server.
        }

        // If the calendar is not selected for syncing, then don't download
        // events.
        scheduleSync(account, !syncEvents, calendarUrl);
    }

    @Override
    public synchronized SyncAdapter getSyncAdapter() {
        if (mSyncAdapter == null) {
            mSyncAdapter = new CalendarSyncAdapter(getContext(), this);
        }
        return mSyncAdapter;
    }

    @Override
    public void onSyncStop(SyncContext context, boolean success) {
        super.onSyncStop(context, success);
        scheduleNextAlarm();
    }

    @Override
    protected Iterable<EventMerger> getMergers() {
        return Collections.singletonList(new EventMerger());
    }

    /* Retrieve and cache the alarm manager */
    private AlarmManager getAlarmManager() {
        synchronized(mAlarmLock) {
            if (mAlarmManager == null) {
                Context context = getContext();
                if (context == null) {
                    Log.e(TAG, "getAlarmManager() cannot get Context");
                    return null;
                }
                Object service = context.getSystemService(Context.ALARM_SERVICE);
                mAlarmManager = (AlarmManager) service;
            }
            return mAlarmManager;
        }
    }

    void scheduleNextAlarmCheck(long triggerTime) {
        AlarmManager manager = getAlarmManager();
        if (manager == null) {
            Log.e(TAG, "scheduleNextAlarmCheck() cannot get AlarmManager");
            return;
        }
        Context context = getContext();
        Intent intent = new Intent(CalendarReceiver.SCHEDULE);
        intent.setClass(context, CalendarReceiver.class);
        PendingIntent pending = PendingIntent.getBroadcast(context,
                0, intent, PendingIntent.FLAG_NO_CREATE);
        if (pending != null) {
            if (DEBUG_ALARMS) {
                Log.i(TAG, "cancelling pending alarm " + pending);
            }
            // Cancel any previous alarms that do the same thing.
            manager.cancel(pending);
        }
        pending = PendingIntent.getBroadcast(context,
                0, intent, PendingIntent.FLAG_CANCEL_CURRENT);

        if (DEBUG_ALARMS) {
            Time time = new Time();
            time.set(triggerTime);
            String timeStr = time.format(" %a, %b %d, %Y %I:%M%P");
            Log.i(TAG, "scheduleNextAlarmCheck at: " + triggerTime + timeStr);
        }

        manager.set(AlarmManager.RTC_WAKEUP, triggerTime, pending);
    }

    /*
     * This method runs the alarm scheduler in a background thread.
     */
    void scheduleNextAlarm() {
        Thread thread = new Thread(new AlarmScheduler());
        thread.start();
    }

    /**
     * This method runs in a background thread and schedules an alarm for
     * the next calendar event, if necessary.
     */
    private void runScheduleNextAlarm() {
        // Do not schedule any events while syncing or if this is a temporary
        // database.
        if (isTemporary())
            return;

        final SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        db.beginTransaction();
        try {
            scheduleNextAlarmLocked(db);
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /**
     * This method looks at the 24-hour window from now for any events that it
     * needs to schedule.  This method runs within a database transaction.
     * 
     * @param db the database
     */
    private void scheduleNextAlarmLocked(SQLiteDatabase db) {
        AlarmManager alarmManager = getAlarmManager();
        if (alarmManager == null) {
            Log.e(TAG, "Failed to find the AlarmManager. Could not schedule the next alarm!");
            return;
        }

        final long currentMillis = System.currentTimeMillis();
        final long start = currentMillis - SCHEDULE_ALARM_SLACK;
        final long end = start + (24 * 60 * 60 * 1000);
        ContentResolver cr = getContext().getContentResolver();
        if (DEBUG_ALARMS) {
            Time time = new Time();
            time.set(start);
            String startTimeStr = time.format(" %a, %b %d, %Y %I:%M%P");
            Log.i(TAG, "runScheduleNextAlarm() start search: " + startTimeStr);
        }

        // Clear old alarms but keep alarms around for a while to prevent
        // multiple alerts for the same reminder.  The "clearUpToTime'
        // should be further in the past than the point in time where
        // we start searching for events (the "start" variable defined above).
        long clearUpToTime = currentMillis - CLEAR_OLD_ALARM_THRESHOLD;
        db.delete("CalendarAlerts", CalendarAlerts.ALARM_TIME + "<" + clearUpToTime, null);

        long nextAlarmTime = end;
        long alarmTime = CalendarAlerts.findNextAlarmTime(cr, currentMillis);
        if (alarmTime != -1 && alarmTime < nextAlarmTime) {
            nextAlarmTime = alarmTime;
        }

        // Extract events from the database sorted by alarm time.  The
        // alarm times are computed from Instances.begin (whose units
        // are milliseconds) and Reminders.minutes (whose units are
        // minutes).
        //
        // Also, ignore events whose end time is already in the past.
        // Also, ignore events alarms that we have already scheduled.
        //
        // Note 1: we can add support for the case where Reminders.minutes
        // equals -1 to mean use Calendars.minutes by adding a UNION for
        // that case where the two halves restrict the WHERE clause on
        // Reminders.minutes != -1 and Reminders.minutes = 1, respectively.
        //
        // Note 2: we have to name "myAlarmTime" different from the
        // "alarmTime" column in CalendarAlerts because otherwise the
        // query won't find multiple alarms for the same event.
        String query = "SELECT begin-(minutes*60000) AS myAlarmTime,"
            + " Instances.event_id AS eventId, begin, end,"
            + " title, allDay, method, minutes"
            + " FROM Instances INNER JOIN Events"
            + " ON (Events._id = Instances.event_id)"
            + " INNER JOIN Reminders"
            + " ON (Instances.event_id = Reminders.event_id)"
            + " WHERE method=" + Reminders.METHOD_ALERT
            + " AND myAlarmTime>=" + start
            + " AND myAlarmTime<=" + nextAlarmTime
            + " AND end>=" + currentMillis
            + " AND 0=(SELECT count(*) from CalendarAlerts CA"
            + " where CA.event_id=Instances.event_id AND CA.begin=Instances.begin"
            + " AND CA.alarmTime=myAlarmTime)"
            + " ORDER BY myAlarmTime,begin,title";

        acquireInstanceRange(start, end, false /* don't use minimum expansion windows */);
        Cursor cursor = null;
        try {
            cursor = db.rawQuery(query, null);

            int beginIndex = cursor.getColumnIndex(Instances.BEGIN);
            int endIndex = cursor.getColumnIndex(Instances.END);
            int eventIdIndex = cursor.getColumnIndex("eventId");
            int alarmTimeIndex = cursor.getColumnIndex("myAlarmTime");
            int minutesIndex = cursor.getColumnIndex(Reminders.MINUTES);

            if (DEBUG_ALARMS) {
                Time time = new Time();
                time.set(nextAlarmTime);
                String alarmTimeStr = time.format(" %a, %b %d, %Y %I:%M%P");
                Log.i(TAG, "nextAlarmTime: " + alarmTimeStr
                        + " query: " + query
                        + " cursor results: " + cursor.getCount());
            }

            while (cursor.moveToNext()) {
                // Schedule all alarms whose alarm time is as early as any
                // scheduled alarm.  For example, if the earliest alarm is at
                // 1pm, then we will schedule all alarms that occur at 1pm
                // but no alarms that occur later than 1pm.
                // Actually, we allow alarms up to a minute later to also
                // be scheduled so that we don't have to check immediately
                // again after an event alarm goes off.
                alarmTime = cursor.getLong(alarmTimeIndex);
                long eventId = cursor.getLong(eventIdIndex);
                int minutes = cursor.getInt(minutesIndex);
                long startTime = cursor.getLong(beginIndex);

                if (DEBUG_ALARMS) {
                    int titleIndex = cursor.getColumnIndex(Events.TITLE);
                    String title = cursor.getString(titleIndex);
                    Time time = new Time();
                    time.set(alarmTime);
                    String schedTime = time.format(" %a, %b %d, %Y %I:%M%P");
                    time.set(startTime);
                    String startTimeStr = time.format(" %a, %b %d, %Y %I:%M%P");
                    long endTime = cursor.getLong(endIndex);
                    time.set(endTime);
                    String endTimeStr = time.format(" - %a, %b %d, %Y %I:%M%P");
                    time.set(currentMillis);
                    String currentTimeStr = time.format(" %a, %b %d, %Y %I:%M%P");
                    Log.i(TAG, "  looking at id: " + eventId + " " + title
                            + " " + startTime
                            + startTimeStr + endTimeStr + " alarm: "
                            + alarmTime + schedTime
                            + " currentTime: " + currentTimeStr);
                }

                if (alarmTime < nextAlarmTime) {
                    nextAlarmTime = alarmTime;
                } else if (alarmTime > nextAlarmTime + android.pim.DateUtils.MINUTE_IN_MILLIS) {
                    // This event alarm (and all later ones) will be scheduled
                    // later.
                    break;
                }
                
                // Avoid an SQLiteContraintException by checking if this alarm
                // already exists in the table.
                if (CalendarAlerts.alarmExists(cr, eventId, startTime, alarmTime)) {
                    continue;
                }

                // Insert this alarm into the CalendarAlerts table
                long endTime = cursor.getLong(endIndex);
                Uri uri = CalendarAlerts.insert(cr, eventId, startTime,
                        endTime, alarmTime, minutes);
                if (uri == null) {
                    Log.e(TAG, "runScheduleNextAlarm() insert into CalendarAlerts table failed");
                    continue;
                }

                Intent intent = new Intent(android.provider.Calendar.EVENT_REMINDER_ACTION);
                intent.setData(uri);

                // Also include the begin and end time of this event, because
                // we cannot determine that from the Events database table.
                intent.putExtra(android.provider.Calendar.EVENT_BEGIN_TIME, startTime);
                intent.putExtra(android.provider.Calendar.EVENT_END_TIME, endTime);
                if (DEBUG_ALARMS) {
                    int titleIndex = cursor.getColumnIndex(Events.TITLE);
                    String title = cursor.getString(titleIndex);
                    Time time = new Time();
                    time.set(alarmTime);
                    String schedTime = time.format(" %a, %b %d, %Y %I:%M%P");
                    time.set(startTime);
                    String startTimeStr = time.format(" %a, %b %d, %Y %I:%M%P");
                    time.set(endTime);
                    String endTimeStr = time.format(" %a, %b %d, %Y %I:%M%P");
                    time.set(currentMillis);
                    String currentTimeStr = time.format(" %a, %b %d, %Y %I:%M%P");
                    Log.i(TAG, "  scheduling " + title
                            + startTimeStr  + " - " + endTimeStr + " alarm: " + schedTime
                            + " currentTime: " + currentTimeStr
                            + " uri: " + uri);
                }
                PendingIntent sender = PendingIntent.getBroadcast(getContext(),
                        0, intent, PendingIntent.FLAG_CANCEL_CURRENT);
                alarmManager.set(AlarmManager.RTC_WAKEUP, alarmTime, sender);
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        // If we scheduled an event alarm, then schedule the next alarm check
        // for one minute past that alarm.  Otherwise, if there were no
        // event alarms scheduled, then check again in 24 hours.  If a new
        // event is inserted before the next alarm check, then this method
        // will be run again when the new event is inserted.
        if (nextAlarmTime != Long.MAX_VALUE) {
            scheduleNextAlarmCheck(nextAlarmTime + android.pim.DateUtils.MINUTE_IN_MILLIS);
        } else {
            scheduleNextAlarmCheck(currentMillis + android.pim.DateUtils.DAY_IN_MILLIS);
        }
    }

    private static String sEventsTable = "Events";
    private static Uri sEventsURL =
            Uri.parse("content://calendar/events/");
    private static String sDeletedEventsTable = "DeletedEvents";
    private static Uri sDeletedEventsURL =
            Uri.parse("content://calendar/deleted_events/");
    private static String sAttendeesTable = "Attendees";
    private static String sRemindersTable = "Reminders";
    private static String sCalendarAlertsTable = "CalendarAlerts";
    private static String sExtendedPropertiesTable = "ExtendedProperties";

    private class EventMerger extends AbstractTableMerger {

        private ContentValues mValues = new ContentValues();
        EventMerger() {
            super(getDatabase(), sEventsTable, sEventsURL, sDeletedEventsTable, sDeletedEventsURL);
        }

        @Override
        protected void notifyChanges() {
            getContext().getContentResolver().notifyChange(Events.CONTENT_URI,
                    null /* observer */, false /* do not sync to network */);
        }

        @Override
        protected void cursorRowToContentValues(Cursor cursor, ContentValues map) {
            rowToContentValues(cursor, map);
        }

        @Override
        public void insertRow(ContentProvider diffs, Cursor diffsCursor) {
            rowToContentValues(diffsCursor, mValues);
            final SQLiteDatabase db = getDatabase();
            long rowId = mEventsInserter.insert(mValues);
            if (rowId <= 0) {
                Log.e(TAG, "Unable to insert values into calendar db: " + mValues);
                return;
            }

            long diffsRowId = diffsCursor.getLong(
                diffsCursor.getColumnIndex(Events._ID));

            insertAttendees(diffs, diffsRowId, rowId, db);
            insertRemindersIfNecessary(diffs, diffsRowId, rowId, db);
            insertExtendedPropertiesIfNecessary(diffs, diffsRowId, rowId, db);
            updateEventRawTimesLocked(rowId, mValues);
            updateInstancesLocked(mValues, rowId, true /* new event */, db);
            insertBusyBitsLocked(rowId, mValues);

            // Update the _SYNC_DIRTY flag of the event. We have to do this
            // after inserting since the update of the reminders and extended properties
            // methods will fire a sql trigger that will cause this flag to
            // be set.
            clearSyncDirtyFlag(db, rowId);
        }

        private void clearSyncDirtyFlag(SQLiteDatabase db, long rowId) {
            mValues.clear();
            mValues.put(Events._SYNC_DIRTY, 0);
            db.update(mTable, mValues, Events._ID + '=' + rowId, null);
        }

        private void insertAttendees(ContentProvider diffs,
                long diffsRowId,
                long rowId,
                SQLiteDatabase db) {
            // query attendees in diffs
            Cursor attendeesCursor =
                    diffs.query(Attendees.CONTENT_URI, null,
                            "event_id=" + diffsRowId, null, null);
            ContentValues attendeesValues = new ContentValues();
            try {
                while (attendeesCursor.moveToNext()) {
                    attendeesValues.clear();
                    DatabaseUtils.cursorStringToContentValues(attendeesCursor,
                            Attendees.ATTENDEE_NAME,
                            attendeesValues);
                    DatabaseUtils.cursorStringToContentValues(attendeesCursor,
                            Attendees.ATTENDEE_EMAIL,
                            attendeesValues);
                    DatabaseUtils.cursorIntToContentValues(attendeesCursor,
                            Attendees.ATTENDEE_STATUS,
                            attendeesValues);
                    DatabaseUtils.cursorIntToContentValues(attendeesCursor,
                            Attendees.ATTENDEE_TYPE,
                            attendeesValues);
                    DatabaseUtils.cursorIntToContentValues(attendeesCursor,
                            Attendees.ATTENDEE_RELATIONSHIP,
                            attendeesValues);
                    attendeesValues.put(Attendees.EVENT_ID, rowId);
                    mAttendeesInserter.insert(attendeesValues);
                }
            } finally {
                if (attendeesCursor != null) {
                    attendeesCursor.close();
                }
            }
        }

        private void insertRemindersIfNecessary(ContentProvider diffs,
                                                long diffsRowId,
                                                long rowId,
                                                SQLiteDatabase db) {
            // insert reminders, if necessary.
            Integer hasAlarm = mValues.getAsInteger(Events.HAS_ALARM);
            if (hasAlarm != null && hasAlarm.intValue() == 1) {
                // query reminders in diffs
                Cursor reminderCursor =
                        diffs.query(Reminders.CONTENT_URI, null,
                                "event_id=" + diffsRowId, null, null);
                ContentValues reminderValues = new ContentValues();
                try {
                    while (reminderCursor.moveToNext()) {
                        reminderValues.clear();
                        DatabaseUtils.cursorIntToContentValues(reminderCursor,
                                Reminders.METHOD,
                                reminderValues);
                        DatabaseUtils.cursorIntToContentValues(reminderCursor,
                                Reminders.MINUTES,
                                reminderValues);
                        reminderValues.put(Reminders.EVENT_ID, rowId);
                        mRemindersInserter.insert(reminderValues);
                    }
                } finally {
                    if (reminderCursor != null) {
                        reminderCursor.close();
                    }
                }
            }
        }

        private void insertExtendedPropertiesIfNecessary(ContentProvider diffs,
                                                         long diffsRowId,
                                                         long rowId,
                                                         SQLiteDatabase db) {
            // insert extended properties, if necessary.
            Integer hasExtendedProperties = mValues.getAsInteger(Events.HAS_EXTENDED_PROPERTIES);
            if (hasExtendedProperties != null && hasExtendedProperties.intValue() != 0) {
                // query reminders in diffs
                Cursor extendedPropertiesCursor =
                        diffs.query(Calendar.ExtendedProperties.CONTENT_URI, null,
                                "event_id=" + diffsRowId, null, null);
                ContentValues extendedPropertiesValues = new ContentValues();
                try {
                    while (extendedPropertiesCursor.moveToNext()) {
                        extendedPropertiesValues.clear();
                        DatabaseUtils.cursorStringToContentValues(extendedPropertiesCursor,
                                Calendar.ExtendedProperties.NAME, extendedPropertiesValues);
                        DatabaseUtils.cursorStringToContentValues(extendedPropertiesCursor,
                                Calendar.ExtendedProperties.VALUE, extendedPropertiesValues);
                        mExtendedPropertiesInserter.insert(extendedPropertiesValues);
                    }
                } finally {
                    if (extendedPropertiesCursor != null) {
                        extendedPropertiesCursor.close();
                    }
                }
            }
        }

        @Override
        public void updateRow(long localId, ContentProvider diffs,
                                 Cursor diffsCursor) {
            rowToContentValues(diffsCursor, mValues);
            final SQLiteDatabase db = getDatabase();
            updateBusyBitsLocked(localId, mValues);
            int numRows = db.update(mTable, mValues, "_id=" + localId, null /* selectionArgs */);

            if (numRows <= 0) {
                Log.e(TAG, "Unable to update calendar db: " + mValues);
                return;
            }

            long diffsRowId = diffsCursor.getLong(
                diffsCursor.getColumnIndex(Events._ID));
            // TODO: only update the attendees, reminders, and extended properties if they have
            // changed?
            // delete the existing attendees, reminders, and extended properties
            db.delete(sAttendeesTable, "event_id=" + localId, null /* selectionArgs */);
            db.delete(sRemindersTable, "event_id=" + localId, null /* selectionArgs */);
            db.delete(sExtendedPropertiesTable, "event_id=" + localId,
                    null /* selectionArgs */);

            // process attendees sent by the server.
            insertAttendees(diffs, diffsRowId, localId, db);
            // process reminders sent by the server.
            insertRemindersIfNecessary(diffs, diffsRowId, localId, db);

            // process extended properties sent by the server.
            insertExtendedPropertiesIfNecessary(diffs, diffsRowId, localId, db);

            updateEventRawTimesLocked(localId, mValues);
            updateInstancesLocked(mValues, localId, false /* not a new event */, db);

            // Update the _SYNC_DIRTY flag of the event. We have to do this
            // after updating since the update of the reminders and extended properties
            // methods will fire a sql trigger that will cause this flag to
            // be set.
            clearSyncDirtyFlag(db, localId);
        }

        @Override
        public void resolveRow(long localId, String syncId,
                                  ContentProvider diffs, Cursor diffsCursor) {
            // server wins
            updateRow(localId, diffs, diffsCursor);
        }

        @Override
        public void deleteRow(Cursor localCursor) {
            int idIndex = localCursor.getColumnIndexOrThrow(Events._ID);
            long localId = localCursor.getLong(idIndex);
            deleteBusyBitsLocked(localId);
            super.deleteRow(localCursor);
        }

        private void rowToContentValues(Cursor diffsCursor, ContentValues values) {
            values.clear();

            DatabaseUtils.cursorStringToContentValues(diffsCursor, Events._SYNC_ID, values);
            DatabaseUtils.cursorStringToContentValues(diffsCursor, Events._SYNC_TIME, values);
            DatabaseUtils.cursorStringToContentValues(diffsCursor, Events._SYNC_VERSION, values);
            DatabaseUtils.cursorStringToContentValues(diffsCursor, Events._SYNC_DIRTY, values);
            DatabaseUtils.cursorStringToContentValues(diffsCursor, Events._SYNC_ACCOUNT, values);
            DatabaseUtils.cursorStringToContentValues(diffsCursor, Events.HTML_URI, values);
            DatabaseUtils.cursorStringToContentValues(diffsCursor, Events.TITLE, values);
            DatabaseUtils.cursorStringToContentValues(diffsCursor, Events.EVENT_LOCATION, values);
            DatabaseUtils.cursorStringToContentValues(diffsCursor, Events.DESCRIPTION, values);
            DatabaseUtils.cursorLongToContentValues(diffsCursor, Events.STATUS, values);
            DatabaseUtils.cursorIntToContentValues(diffsCursor, Events.SELF_ATTENDEE_STATUS,
                    values);
            DatabaseUtils.cursorStringToContentValues(diffsCursor, Events.COMMENTS_URI, values);
            DatabaseUtils.cursorLongToContentValues(diffsCursor, Events.DTSTART, values);
            DatabaseUtils.cursorLongToContentValues(diffsCursor, Events.DTEND, values);
            DatabaseUtils.cursorStringToContentValues(diffsCursor, Events.EVENT_TIMEZONE, values);
            DatabaseUtils.cursorStringToContentValues(diffsCursor, Events.DURATION, values);
            DatabaseUtils.cursorIntToContentValues(diffsCursor, Events.ALL_DAY, values);
            DatabaseUtils.cursorIntToContentValues(diffsCursor, Events.VISIBILITY, values);
            DatabaseUtils.cursorIntToContentValues(diffsCursor, Events.TRANSPARENCY, values);
            DatabaseUtils.cursorIntToContentValues(diffsCursor, Events.HAS_ALARM, values);
            DatabaseUtils.cursorIntToContentValues(diffsCursor, Events.HAS_EXTENDED_PROPERTIES,
                    values);
            DatabaseUtils.cursorStringToContentValues(diffsCursor, Events.RRULE, values);
            DatabaseUtils.cursorStringToContentValues(diffsCursor, Events.ORIGINAL_EVENT, values);
            DatabaseUtils.cursorLongToContentValues(diffsCursor, Events.ORIGINAL_INSTANCE_TIME,
                    values);
            DatabaseUtils.cursorLongToContentValues(diffsCursor, Events.LAST_DATE, values);
            DatabaseUtils.cursorLongToContentValues(diffsCursor, Events.CALENDAR_ID, values);
        }
    }

    private static final int EVENTS = 1;
    private static final int EVENTS_ID = 2;
    private static final int INSTANCES = 3;
    private static final int DELETED_EVENTS = 4;
    private static final int CALENDARS = 5;
    private static final int CALENDARS_ID = 6;
    private static final int ATTENDEES = 7;
    private static final int ATTENDEES_ID = 8;
    private static final int REMINDERS = 9;
    private static final int REMINDERS_ID = 10;
    private static final int EXTENDED_PROPERTIES = 11;
    private static final int EXTENDED_PROPERTIES_ID = 12;
    private static final int CALENDAR_ALERTS = 13;
    private static final int CALENDAR_ALERTS_ID = 14;
    private static final int CALENDAR_ALERTS_BY_INSTANCE = 15;
    private static final int BUSYBITS = 16;

    private static final UriMatcher sURLMatcher = new UriMatcher(UriMatcher.NO_MATCH);
    private static final HashMap<String, String> sInstancesProjectionMap;
    private static final HashMap<String, String> sEventsProjectionMap;
    private static final HashMap<String, String> sAttendeesProjectionMap;
    private static final HashMap<String, String> sRemindersProjectionMap;
    private static final HashMap<String, String> sCalendarAlertsProjectionMap;
    private static final HashMap<String, String> sBusyBitsProjectionMap;

    static {
        sURLMatcher.addURI("calendar", "instances/when/*/*", INSTANCES);
        sURLMatcher.addURI("calendar", "events", EVENTS);
        sURLMatcher.addURI("calendar", "events/#", EVENTS_ID);
        sURLMatcher.addURI("calendar", "calendars", CALENDARS);
        sURLMatcher.addURI("calendar", "calendars/#", CALENDARS_ID);
        sURLMatcher.addURI("calendar", "deleted_events", DELETED_EVENTS);
        sURLMatcher.addURI("calendar", "attendees", ATTENDEES);
        sURLMatcher.addURI("calendar", "attendees/#", ATTENDEES_ID);
        sURLMatcher.addURI("calendar", "reminders", REMINDERS);
        sURLMatcher.addURI("calendar", "reminders/#", REMINDERS_ID);
        sURLMatcher.addURI("calendar", "extendedproperties", EXTENDED_PROPERTIES);
        sURLMatcher.addURI("calendar", "extendedproperties/#", EXTENDED_PROPERTIES_ID);
        sURLMatcher.addURI("calendar", "calendar_alerts", CALENDAR_ALERTS);
        sURLMatcher.addURI("calendar", "calendar_alerts/#", CALENDAR_ALERTS_ID);
        sURLMatcher.addURI("calendar", "calendar_alerts/by_instance", CALENDAR_ALERTS_BY_INSTANCE);
        sURLMatcher.addURI("calendar", "busybits/when/*/*", BUSYBITS);


        sEventsProjectionMap = new HashMap<String, String>();
        // Events columns
        sEventsProjectionMap.put(Events.HTML_URI, "htmlUri");
        sEventsProjectionMap.put(Events.TITLE, "title");
        sEventsProjectionMap.put(Events.EVENT_LOCATION, "eventLocation");
        sEventsProjectionMap.put(Events.DESCRIPTION, "description");
        sEventsProjectionMap.put(Events.STATUS, "eventStatus");
        sEventsProjectionMap.put(Events.SELF_ATTENDEE_STATUS, "selfAttendeeStatus");
        sEventsProjectionMap.put(Events.COMMENTS_URI, "commentsUri");
        sEventsProjectionMap.put(Events.DTSTART, "dtstart");
        sEventsProjectionMap.put(Events.DTEND, "dtend");
        sEventsProjectionMap.put(Events.EVENT_TIMEZONE, "eventTimezone");
        sEventsProjectionMap.put(Events.DURATION, "duration");
        sEventsProjectionMap.put(Events.ALL_DAY, "allDay");
        sEventsProjectionMap.put(Events.VISIBILITY, "visibility");
        sEventsProjectionMap.put(Events.TRANSPARENCY, "transparency");
        sEventsProjectionMap.put(Events.HAS_ALARM, "hasAlarm");
        sEventsProjectionMap.put(Events.HAS_EXTENDED_PROPERTIES, "hasExtendedProperties");
        sEventsProjectionMap.put(Events.RRULE, "rrule");
        sEventsProjectionMap.put(Events.RDATE, "rdate");
        sEventsProjectionMap.put(Events.EXRULE, "exrule");
        sEventsProjectionMap.put(Events.EXDATE, "exdate");
        sEventsProjectionMap.put(Events.ORIGINAL_EVENT, "originalEvent");
        sEventsProjectionMap.put(Events.ORIGINAL_INSTANCE_TIME, "originalInstanceTime");
        sEventsProjectionMap.put(Events.LAST_DATE, "lastDate");
        sEventsProjectionMap.put(Events.CALENDAR_ID, "calendar_id");
        // Calendar columns
        sEventsProjectionMap.put(Events.COLOR, "color");
        sEventsProjectionMap.put(Events.ACCESS_LEVEL, "access_level");
        sEventsProjectionMap.put(Events.SELECTED, "selected");
        sEventsProjectionMap.put(Calendars.URL, "url");
        sEventsProjectionMap.put(Calendars.TIMEZONE, "timezone");

        // Put the shared items into the Instances projection map
        sInstancesProjectionMap = new HashMap<String, String>(sEventsProjectionMap);
        sAttendeesProjectionMap = new HashMap<String, String>(sEventsProjectionMap);
        sRemindersProjectionMap = new HashMap<String, String>(sEventsProjectionMap);
        sCalendarAlertsProjectionMap = new HashMap<String, String>(sEventsProjectionMap);

        sEventsProjectionMap.put(Events._ID, "Events._id AS _id");
        sEventsProjectionMap.put(Events._SYNC_ID, "Events._sync_id AS _sync_id");
        sEventsProjectionMap.put(Events._SYNC_VERSION, "Events._sync_version AS _sync_version");
        sEventsProjectionMap.put(Events._SYNC_TIME, "Events._sync_time AS _sync_time");
        sEventsProjectionMap.put(Events._SYNC_LOCAL_ID, "Events._sync_local_id AS _sync_local_id");
        sEventsProjectionMap.put(Events._SYNC_DIRTY, "Events._sync_dirty AS _sync_dirty");
        sEventsProjectionMap.put(Events._SYNC_ACCOUNT, "Events._sync_account AS _sync_account");

        // Instances columns
        sInstancesProjectionMap.put(Instances.BEGIN, "begin");
        sInstancesProjectionMap.put(Instances.END, "end");
        sInstancesProjectionMap.put(Instances.EVENT_ID, "Instances.event_id AS event_id");
        sInstancesProjectionMap.put(Instances._ID, "Instances._id AS _id");
        sInstancesProjectionMap.put(Instances.START_DAY, "startDay");
        sInstancesProjectionMap.put(Instances.END_DAY, "endDay");
        sInstancesProjectionMap.put(Instances.START_MINUTE, "startMinute");
        sInstancesProjectionMap.put(Instances.END_MINUTE, "endMinute");

        // BusyBits columns
        sBusyBitsProjectionMap = new HashMap<String, String>();
        sBusyBitsProjectionMap.put(BusyBits.DAY, "day");
        sBusyBitsProjectionMap.put(BusyBits.BUSYBITS, "busyBits");
        sBusyBitsProjectionMap.put(BusyBits.ALL_DAY_COUNT, "allDayCount");

        // Attendees columns
        sAttendeesProjectionMap.put(Attendees.EVENT_ID, "event_id");
        sAttendeesProjectionMap.put(Attendees._ID, "Attendees._id AS _id");
        sAttendeesProjectionMap.put(Attendees.ATTENDEE_NAME, "attendeeName");
        sAttendeesProjectionMap.put(Attendees.ATTENDEE_EMAIL, "attendeeEmail");
        sAttendeesProjectionMap.put(Attendees.ATTENDEE_STATUS, "attendeeStatus");
        sAttendeesProjectionMap.put(Attendees.ATTENDEE_RELATIONSHIP, "attendeeRelationship");
        sAttendeesProjectionMap.put(Attendees.ATTENDEE_TYPE, "attendeeType");

        // Reminders columns
        sRemindersProjectionMap.put(Reminders.EVENT_ID, "event_id");
        sRemindersProjectionMap.put(Reminders._ID, "Reminders._id AS _id");
        sRemindersProjectionMap.put(Reminders.MINUTES, "minutes");
        sRemindersProjectionMap.put(Reminders.METHOD, "method");

        // CalendarAlerts columns
        sCalendarAlertsProjectionMap.put(CalendarAlerts.EVENT_ID, "event_id");
        sCalendarAlertsProjectionMap.put(CalendarAlerts._ID, "CalendarAlerts._id AS _id");
        sCalendarAlertsProjectionMap.put(CalendarAlerts.BEGIN, "begin");
        sCalendarAlertsProjectionMap.put(CalendarAlerts.END, "end");
        sCalendarAlertsProjectionMap.put(CalendarAlerts.ALARM_TIME, "alarmTime");
        sCalendarAlertsProjectionMap.put(CalendarAlerts.STATE, "state");
        sCalendarAlertsProjectionMap.put(CalendarAlerts.MINUTES, "minutes");
    }
}
