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

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.os.PowerManager;
import android.provider.Calendar.CalendarAlerts;

/**
 * Receives android.intent.action.EVENT_REMINDER intents and handles
 * event reminders.  The intent URI specifies an alert id in the 
 * CalendarAlerts database table.  This class also receives the
 * BOOT_COMPLETED intent so that it can add a status bar notification
 * if there are Calendar event alarms that have not been dismissed.
 * It also receives the TIME_CHANGED action so that it can fire off
 * snoozed alarms that have become ready.  The real work is done in
 * the AlertService class.
 */
public class AlertReceiver extends BroadcastReceiver {
    
    private static final String[] ALERT_PROJECTION = new String[] { 
        CalendarAlerts.TITLE,           // 0
        CalendarAlerts.EVENT_LOCATION,  // 1
    };
    private static final int ALERT_INDEX_TITLE = 0;
    private static final int ALERT_INDEX_EVENT_LOCATION = 1;
    
    private static final String DELETE_ACTION = "delete";
    
    private static final String[] PROJECTION = new String[] { 
        CalendarAlerts._ID,              // 0
        CalendarAlerts.STATE,            // 1
    };
    
    public static final int INDEX_STATE = 1;
    
    static final Object mStartingServiceSync = new Object();
    static PowerManager.WakeLock mStartingService;
    
    @Override
    public void onReceive(Context context, Intent intent) {
        if (DELETE_ACTION.equals(intent.getAction())) {
            
            /* The user has clicked the "Clear All Notifications"
             * buttons so dismiss all Calendar alerts.
             */
            dismissAllEvents(context);
        } else {
            Intent i = new Intent();
            i.setClass(context, AlertService.class);
            i.putExtras(intent);
            i.putExtra("action", intent.getAction());
            Uri uri = intent.getData();
            
            // This intent might be a BOOT_COMPLETED so it might not have a Uri.
            if (uri != null) {
                i.putExtra("uri", uri.toString());
            }
            beginStartingService(context, i);
        }
    }
    
    private void dismissAllEvents(Context context) {
        Uri uri = CalendarAlerts.CONTENT_URI_BY_INSTANCE;
        String selection = CalendarAlerts.STATE + "=" + CalendarAlerts.FIRED;
        ContentResolver resolver = context.getContentResolver();
        Cursor cursor = resolver.query(uri, PROJECTION, selection, null, null);
        if (cursor != null) {
            cursor.moveToPosition(-1);
            while (cursor.moveToNext()) {
                cursor.updateInt(INDEX_STATE, CalendarAlerts.DISMISSED);
            }
            cursor.commitUpdates();
            cursor.close();
        }
    }

    /**
     * Start the service to process the current event notifications, acquiring
     * the wake lock before returning to ensure that the service will run.
     */
    public static void beginStartingService(Context context, Intent intent) {
        synchronized (mStartingServiceSync) {
            if (mStartingService == null) {
                PowerManager pm =
                    (PowerManager)context.getSystemService(Context.POWER_SERVICE);
                mStartingService = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                        "StartingAlertService");
                mStartingService.setReferenceCounted(false);
            }
            mStartingService.acquire();
            context.startService(intent);
        }
    }
    
    /**
     * Called back by the service when it has finished processing notifications,
     * releasing the wake lock if the service is now stopping.
     */
    public static void finishStartingService(Service service, int startId) {
        synchronized (mStartingServiceSync) {
            if (mStartingService != null) {
                if (service.stopSelfResult(startId)) {
                    mStartingService.release();
                }
            }
        }
    }
    
    public static void updateAlertNotification(Context context) {
        // This can be called regularly to synchronize the alert notification
        // with the contents of the CalendarAlerts table.
        
        ContentResolver cr = context.getContentResolver();
        
        if (cr == null) {
            return;
        }
        
        String selection = CalendarAlerts.STATE + "=" + CalendarAlerts.FIRED;
        Cursor alertCursor = CalendarAlerts.query(cr, ALERT_PROJECTION, selection, null);
        
        NotificationManager nm = 
                (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        
        if (alertCursor == null) {
            nm.cancel(AlertActivity.NOTIFICATION_ID);
            return;
        }

        if (!alertCursor.moveToFirst()) {
            alertCursor.close();
            nm.cancel(AlertActivity.NOTIFICATION_ID);
            return;
        }
        
        String title = alertCursor.getString(ALERT_INDEX_TITLE);
        String location = alertCursor.getString(ALERT_INDEX_EVENT_LOCATION);
        
        Notification notification = AlertReceiver.makeNewAlertNotification(context, title, 
                location, alertCursor.getCount());
        alertCursor.close();
        
        nm.notify(0, notification);
    }
    
    public static Notification makeNewAlertNotification(Context context, String title, 
            String location, int numReminders) {
        Resources res = context.getResources();
        
        // Create an intent triggered by clicking on the status icon.
        Intent clickIntent = new Intent();
        clickIntent.setClass(context, AlertActivity.class);
        clickIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        
        // Create an intent triggered by clicking on the "Clear All Notifications" button
        Intent deleteIntent = new Intent();
        deleteIntent.setClass(context, AlertReceiver.class);
        deleteIntent.setAction(DELETE_ACTION);
        
        if (title == null || title.length() == 0) {
            title = res.getString(R.string.no_title_label);
        }
        
        String helperString;
        if (numReminders > 1) {
            String format;
            if (numReminders == 2) {
                format = res.getString(R.string.alert_missed_events_single);
            } else {
                format = res.getString(R.string.alert_missed_events_multiple);
            }
            helperString = String.format(format, numReminders - 1);
        } else {
            helperString = location;
        }
        
        Notification notification = new Notification(
                R.drawable.stat_notify_calendar,
                null,
                System.currentTimeMillis());
        notification.setLatestEventInfo(context,
                title,
                helperString,
                PendingIntent.getActivity(context, 0, clickIntent, 0));
        notification.deleteIntent = PendingIntent.getBroadcast(context, 0, deleteIntent, 0);
        
        return notification;
    }
}

