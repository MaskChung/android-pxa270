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

package com.android.server;

import android.app.ActivityManagerNative;
import android.app.AlarmManager;
import android.app.IAlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.PowerManager;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.text.TextUtils;
import android.util.Config;
import android.util.Log;

import java.io.FileDescriptor;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.TimeZone;

class AlarmManagerService extends IAlarmManager.Stub {
    private static final int RTC_WAKEUP_MASK = 1 << AlarmManager.RTC_WAKEUP;
    private static final int RTC_MASK = 1 << AlarmManager.RTC;
    private static final int ELAPSED_REALTIME_WAKEUP_MASK = 1 << AlarmManager.ELAPSED_REALTIME_WAKEUP; 
    private static final int ELAPSED_REALTIME_MASK = 1 << AlarmManager.ELAPSED_REALTIME;
    private static final int TIME_CHANGED_MASK = 1 << 16;
    
    private static final String TAG = "AlarmManager";
    private static final String ClockReceiver_TAG = "ClockReceiver";
    private static final boolean localLOGV = false;
    private static final int ALARM_EVENT = 1;
    private static final String TIMEZONE_PROPERTY = "persist.sys.timezone";
    
    private static final Intent mBackgroundIntent
            = new Intent().addFlags(Intent.FLAG_FROM_BACKGROUND);
    
    private final Context mContext;
    
    private Object mLock = new Object();
    
    private final ArrayList<Alarm> mRtcWakeupAlarms = new ArrayList<Alarm>();
    private final ArrayList<Alarm> mRtcAlarms = new ArrayList<Alarm>();
    private final ArrayList<Alarm> mElapsedRealtimeWakeupAlarms = new ArrayList<Alarm>();
    private final ArrayList<Alarm> mElapsedRealtimeAlarms = new ArrayList<Alarm>();
    
    private int mDescriptor;
    private int mBroadcastRefCount = 0;
    private PowerManager.WakeLock mWakeLock;
    private final AlarmThread mWaitThread = new AlarmThread();
    private final AlarmHandler mHandler = new AlarmHandler();
    private ClockReceiver mClockReceiver;
    private UninstallReceiver mUninstallReceiver;
    private final ResultReceiver mResultReceiver = new ResultReceiver();
    private final PendingIntent mTimeTickSender;
    private final PendingIntent mDateChangeSender;
    
    private static final class FilterStats {
        int count;
    }
    
    private static final class BroadcastStats {
        long aggregateTime;
        int numWakeup;
        long startTime;
        int nesting;
        HashMap<Intent.FilterComparison, FilterStats> filterStats
                = new HashMap<Intent.FilterComparison, FilterStats>();
    }
    
    private final HashMap<String, BroadcastStats> mBroadcastStats
            = new HashMap<String, BroadcastStats>();
    
    public AlarmManagerService(Context context) {
        mContext = context;
        mDescriptor = init();
        PowerManager pm = (PowerManager)context.getSystemService(Context.POWER_SERVICE);
        mWakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, TAG);
        
        mTimeTickSender = PendingIntent.getBroadcast(context, 0,
                new Intent(Intent.ACTION_TIME_TICK).addFlags(
                        Intent.FLAG_RECEIVER_REGISTERED_ONLY), 0);
        mDateChangeSender = PendingIntent.getBroadcast(context, 0,
                new Intent(Intent.ACTION_DATE_CHANGED), 0);
        
        // now that we have initied the driver schedule the alarm
        mClockReceiver= new ClockReceiver();
        mClockReceiver.scheduleTimeTickEvent();
        mClockReceiver.scheduleDateChangedEvent();
        mUninstallReceiver = new UninstallReceiver();
        
        if (mDescriptor != -1) {
            mWaitThread.start();
        } else {
            Log.w(TAG, "Failed to open alarm driver. Falling back to a handler.");
        }
    }
    
    protected void finalize() throws Throwable {
        try {
            close(mDescriptor);
        } finally {
            super.finalize();
        }
    }
    
    public void set(int type, long triggerAtTime, PendingIntent operation) {
        setRepeating(type, triggerAtTime, 0, operation);
    }
    
    public void setRepeating(int type, long triggerAtTime, long interval, 
            PendingIntent operation) {
        if (operation == null) {
            Log.w(TAG, "set/setRepeating ignored because there is no intent");
            return;
        }
        synchronized (mLock) {
            Alarm alarm = new Alarm();
            alarm.type = type;
            alarm.when = triggerAtTime;
            alarm.repeatInterval = interval;
            alarm.operation = operation;

            // Remove this alarm if already scheduled.
            removeLocked(operation);

            if (localLOGV) Log.v(TAG, "set: " + alarm);

            int index = addAlarmLocked(alarm);
            if (index == 0) {
                setLocked(alarm);
            }
        }
    }
    
    public void setTimeZone(String tz) {
        mContext.enforceCallingOrSelfPermission(
                "android.permission.SET_TIME_ZONE",
                "setTimeZone");

        if (TextUtils.isEmpty(tz)) return;
        TimeZone zone = TimeZone.getTimeZone(tz);
        // Prevent reentrant calls from stepping on each other when writing
        // the time zone property
        synchronized (this) {
            SystemProperties.set(TIMEZONE_PROPERTY, zone.getID());
        }
        
        TimeZone.setDefault(null);
        
        Intent intent = new Intent(Intent.ACTION_TIMEZONE_CHANGED);
        intent.putExtra("time-zone", zone.getID());
        mContext.sendBroadcast(intent);
    }
    
    public void remove(PendingIntent operation) {
        if (operation == null) {
            return;
        }
        synchronized (mLock) {
            removeLocked(operation);
        }
    }
    
    public void removeLocked(PendingIntent operation) {
        removeLocked(mRtcWakeupAlarms, operation);
        removeLocked(mRtcAlarms, operation);
        removeLocked(mElapsedRealtimeWakeupAlarms, operation);
        removeLocked(mElapsedRealtimeAlarms, operation);
    }

    private void removeLocked(ArrayList<Alarm> alarmList,
            PendingIntent operation) {
        if (alarmList.size() <= 0) {
            return;
        }

        // iterator over the list removing any it where the intent match
        Iterator<Alarm> it = alarmList.iterator();
        
        while (it.hasNext()) {
            Alarm alarm = it.next();
            if (alarm.operation.equals(operation)) {
                it.remove();
            }
        }
    }
    
    public void removeLocked(String packageName) {
        removeLocked(mRtcWakeupAlarms, packageName);
        removeLocked(mRtcAlarms, packageName);
        removeLocked(mElapsedRealtimeWakeupAlarms, packageName);
        removeLocked(mElapsedRealtimeAlarms, packageName);
    }

    private void removeLocked(ArrayList<Alarm> alarmList,
            String packageName) {
        if (alarmList.size() <= 0) {
            return;
        }

        // iterator over the list removing any it where the intent match
        Iterator<Alarm> it = alarmList.iterator();
        
        while (it.hasNext()) {
            Alarm alarm = it.next();
            if (alarm.operation.getTargetPackage().equals(packageName)) {
                it.remove();
            }
        }
    }
    
    private ArrayList<Alarm> getAlarmList(int type) {
        switch (type) {
            case AlarmManager.RTC_WAKEUP:              return mRtcWakeupAlarms;
            case AlarmManager.RTC:                     return mRtcAlarms;
            case AlarmManager.ELAPSED_REALTIME_WAKEUP: return mElapsedRealtimeWakeupAlarms;
            case AlarmManager.ELAPSED_REALTIME:        return mElapsedRealtimeAlarms;
        }
        
        return null;
    }
    
    private int addAlarmLocked(Alarm alarm) {
        ArrayList<Alarm> alarmList = getAlarmList(alarm.type);
        
        int index = Collections.binarySearch(alarmList, alarm);
        index = (index < 0) ? ((index + 1) * -1) : index;
        if (localLOGV) Log.v(
            TAG, "Adding alarm " + alarm + " at " + index);
        alarmList.add(index, alarm);
        
        return index;
    }
    
    public long timeToNextAlarm() {
        long nextAlarm = 0xfffffffffffffffl;
        synchronized (mLock) {
            for (int i=AlarmManager.RTC_WAKEUP;
                    i<=AlarmManager.ELAPSED_REALTIME; i++) {
                ArrayList<Alarm> alarmList = getAlarmList(i);
                if (alarmList.size() > 0) {
                    Alarm a = alarmList.get(0);
                    if (a.when < nextAlarm) {
                        nextAlarm = a.when;
                    }
                }
            }
        }
        return nextAlarm;
    }
    
    private void setLocked(Alarm alarm)
    {
        if (mDescriptor != -1)
        {
            set(mDescriptor, alarm.type, (alarm.when * 1000 * 1000));
        }
        else
        {
            Message msg = Message.obtain();
            msg.what = ALARM_EVENT;
            
            mHandler.removeMessages(ALARM_EVENT);
            mHandler.sendMessageAtTime(msg, alarm.when);
        }
    }
    
    @Override
    protected void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        if (mContext.checkCallingPermission("android.permission.DUMP")
                != PackageManager.PERMISSION_GRANTED) {
            pw.println("Permission Denial: can't dump AlarmManager from from pid="
                    + Binder.getCallingPid()
                    + ", uid=" + Binder.getCallingUid());
            return;
        }
        
        synchronized (mLock) {
            pw.println("Current Alarm Manager state:");
            if (mRtcWakeupAlarms.size() > 0) {
                pw.println(" ");
                pw.println("  Realtime wakeup alarms that are scheduled:");
                dumpAlarmList(pw, mRtcWakeupAlarms, "  ", "RTC_WAKEUP");
            }
            if (mRtcAlarms.size() > 0) {
                pw.println(" ");
                pw.println("  Realtime alarms that are scheduled:");
                dumpAlarmList(pw, mRtcAlarms, "  ", "RTC");
            }
            if (mElapsedRealtimeWakeupAlarms.size() > 0) {
                pw.println(" ");
                pw.println("  Elapsed realtime wakeup alarms that are scheduled:");
                dumpAlarmList(pw, mElapsedRealtimeWakeupAlarms, "  ", "ELAPSED_REALTIME_WAKEUP");
            }
            if (mElapsedRealtimeAlarms.size() > 0) {
                pw.println(" ");
                pw.println("  Elapsed realtime alarms that are scheduled:");
                dumpAlarmList(pw, mElapsedRealtimeAlarms, "  ", "ELAPSED_REALTIME");
            }
            
            pw.println(" ");
            pw.println("  Broadcast ref count: " + mBroadcastRefCount);
            
            pw.println(" ");
            pw.println("  Alarm Stats:");
            for (Map.Entry<String, BroadcastStats> be : mBroadcastStats.entrySet()) {
                BroadcastStats bs = be.getValue();
                pw.println("  " + be.getKey());
                pw.println("    " + bs.aggregateTime + "ms running, "
                        + bs.numWakeup + " wakeups");
                for (Map.Entry<Intent.FilterComparison, FilterStats> fe
                        : bs.filterStats.entrySet()) {
                    pw.println("    " + fe.getValue().count + " alarms: "
                            + fe.getKey().getIntent());
                }
            }
        }
    }

    private static final void dumpAlarmList(PrintWriter pw, ArrayList<Alarm> list, String prefix, String label) {
        for (int i=list.size()-1; i>=0; i--) {
            Alarm a = list.get(i);
            pw.println(prefix + label + " #" + i + ":");
            a.dump(pw, prefix + "  ");
        }
    }
    
    private native int init();
    private native void close(int fd);
    private native void set(int fd, int type, long nanoseconds);
    private native int waitForAlarm(int fd);
   
    private void triggerAlarmsLocked(ArrayList<Alarm> alarmList,
                                     ArrayList<Alarm> triggerList,
                                     long now)
    {
        Iterator<Alarm> it = alarmList.iterator();
        ArrayList<Alarm> repeats = new ArrayList<Alarm>();
        
        while (it.hasNext())
        {
            Alarm alarm = it.next();

            if (localLOGV) Log.v(TAG, "Checking active alarm when=" + alarm.when + " " + alarm);

            if (alarm.when > now)
            {
                // don't fire alarms in the future
                break;
            }

            // add it to the trigger list so we can trigger it without the lock held.
            // recurring alarms may have passed several alarm intervals while the
            // phone was asleep or off, so pass a trigger count when sending them.
            if (localLOGV) Log.v(TAG, "Alarm triggering: " + alarm);
            alarm.count = 1;
            if (alarm.repeatInterval > 0) {
                // this adjustment will be zero if we're late by
                // less than one full repeat interval
                alarm.count += (now - alarm.when) / alarm.repeatInterval;
            }
            triggerList.add(alarm);
            
            // remove the alarm from the list
            it.remove();
            
            // if it repeats queue it up to be read-added to the list
            if (alarm.repeatInterval > 0)
            {
                repeats.add(alarm);
            }
        }

        // reset any repeating alarms.
        it = repeats.iterator();
        while (it.hasNext())
        {
            Alarm alarm = it.next();
            alarm.when += alarm.count * alarm.repeatInterval;
            addAlarmLocked(alarm);
        }
        
        if (alarmList.size() > 0)
        {
            setLocked(alarmList.get(0));
        }
    }
    
    private class Alarm implements Comparable<Alarm> {
        public int type;
        public int count;
        public long when;
        public long repeatInterval;
        public PendingIntent operation;
        
        public Alarm() {
            when = 0;
            repeatInterval = 0;
            operation = null;
        }
        
        public int compareTo(Alarm obj)
        {
            if (obj.when > this.when) return -1;
            if (obj.when < this.when) return 1;
            if (obj.operation.equals(this.operation)
                    && obj.repeatInterval == this.repeatInterval) return 0;
            return -1;
        }
        
        public String toString()
        {
            return "Alarm{"
                + Integer.toHexString(System.identityHashCode(this))
                + " type " + type + " " + operation.getTargetPackage() + "}";
        }

        public void dump(PrintWriter pw, String prefix)
        {
            pw.println(prefix + this);
            pw.println(prefix + "type=" + type + " when=" + when
                  + " repeatInterval=" + repeatInterval
                  + " count=" + count);
            pw.println(prefix + "operation=" + operation);
        }
    }
    
    private class AlarmThread extends Thread
    {
        public AlarmThread()
        {
            super("AlarmManager");
        }
        
        public void run()
        {
            while (true)
            {
                int result = waitForAlarm(mDescriptor);
                
                ArrayList<Alarm> triggerList = new ArrayList<Alarm>();
                
                if ((result & TIME_CHANGED_MASK) != 0) {
                    remove(mTimeTickSender);
                    mClockReceiver.scheduleTimeTickEvent();
                    mContext.sendBroadcast(new Intent(Intent.ACTION_TIME_CHANGED));
                }
                
                synchronized (mLock) {
                    final long nowRTC = System.currentTimeMillis();
                    final long nowELAPSED = SystemClock.elapsedRealtime();
                    if (localLOGV) Log.v(
                        TAG, "Checking for alarms... rtc=" + nowRTC
                        + ", elapsed=" + nowELAPSED);

                    if ((result & RTC_WAKEUP_MASK) != 0)
                        triggerAlarmsLocked(mRtcWakeupAlarms, triggerList, nowRTC);
                    
                    if ((result & RTC_MASK) != 0)
                        triggerAlarmsLocked(mRtcAlarms, triggerList, nowRTC);
                    
                    if ((result & ELAPSED_REALTIME_WAKEUP_MASK) != 0)
                        triggerAlarmsLocked(mElapsedRealtimeWakeupAlarms, triggerList, nowELAPSED);
                    
                    if ((result & ELAPSED_REALTIME_MASK) != 0)
                        triggerAlarmsLocked(mElapsedRealtimeAlarms, triggerList, nowELAPSED);
                    
                    // now trigger the alarms
                    Iterator<Alarm> it = triggerList.iterator();
                    while (it.hasNext()) {
                        Alarm alarm = it.next();
                        try {
                            if (localLOGV) Log.v(TAG, "sending alarm " + alarm);
                            alarm.operation.send(mContext, 0,
                                    mBackgroundIntent.putExtra(
                                            Intent.EXTRA_ALARM_COUNT, alarm.count),
                                    mResultReceiver, mHandler);
                            
                            // we have an active broadcast so stay awake. 
                            if (mBroadcastRefCount == 0) {
                                mWakeLock.acquire();
                            }
                            mBroadcastRefCount++;
                            
                            BroadcastStats bs = getStatsLocked(alarm.operation);
                            if (bs.nesting == 0) {
                                bs.startTime = nowELAPSED;
                            } else {
                                bs.nesting++;
                            }
                            if (alarm.type == AlarmManager.ELAPSED_REALTIME_WAKEUP
                                    || alarm.type == AlarmManager.RTC_WAKEUP) {
                                bs.numWakeup++;
                                ActivityManagerNative.noteWakeupAlarm(
                                        alarm.operation);
                            }
                        } catch (PendingIntent.CanceledException e) {
                            if (alarm.repeatInterval > 0) {
                                // This IntentSender is no longer valid, but this
                                // is a repeating alarm, so toss the hoser.
                                remove(alarm.operation);
                            }
                        } catch (RuntimeException e) {
                            Log.w(TAG, "Failure sending alarm.", e);
                        }
                    }
                }
            }
        }
    }
    
    private class AlarmHandler extends Handler {
        public static final int ALARM_EVENT = 1;
        public static final int MINUTE_CHANGE_EVENT = 2;
        public static final int DATE_CHANGE_EVENT = 3;
        
        public AlarmHandler() {
        }
        
        public void handleMessage(Message msg) {
            if (msg.what == ALARM_EVENT) {
                ArrayList<Alarm> triggerList = new ArrayList<Alarm>();
                synchronized (mLock) {
                    final long nowRTC = System.currentTimeMillis();
                    triggerAlarmsLocked(mRtcWakeupAlarms, triggerList, nowRTC);
                    triggerAlarmsLocked(mRtcAlarms, triggerList, nowRTC);
                    triggerAlarmsLocked(mElapsedRealtimeWakeupAlarms, triggerList, nowRTC);
                    triggerAlarmsLocked(mElapsedRealtimeAlarms, triggerList, nowRTC);
                }
                
                // now trigger the alarms without the lock held
                Iterator<Alarm> it = triggerList.iterator();
                while (it.hasNext())
                {
                    Alarm alarm = it.next();
                    try {
                        alarm.operation.send();
                    } catch (PendingIntent.CanceledException e) {
                        if (alarm.repeatInterval > 0) {
                            // This IntentSender is no longer valid, but this
                            // is a repeating alarm, so toss the hoser.
                            remove(alarm.operation);
                        }
                    }
                }
            }
        }
    }
    
    class ClockReceiver extends BroadcastReceiver {
        public ClockReceiver() {
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_TIME_TICK);
            mContext.registerReceiver(this, filter);
        }
        
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getAction().equals(Intent.ACTION_TIME_TICK)) {
            	scheduleTimeTickEvent();
            } else if (intent.getAction().equals(Intent.ACTION_DATE_CHANGED)) {
            	scheduleDateChangedEvent();
            }
        }
        
        public void scheduleTimeTickEvent() {
            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(System.currentTimeMillis());
            calendar.add(Calendar.MINUTE, 1);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);
      
            set(AlarmManager.RTC, calendar.getTimeInMillis(), mTimeTickSender);
        }
	
        public void scheduleDateChangedEvent() {
            Calendar calendar = Calendar.getInstance();
            calendar.setTimeInMillis(System.currentTimeMillis());
            calendar.add(Calendar.DAY_OF_MONTH, 1);
            calendar.set(Calendar.HOUR, 0);
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);
      
            set(AlarmManager.RTC, calendar.getTimeInMillis(), mDateChangeSender);
        }
    }
    
    class UninstallReceiver extends BroadcastReceiver {
        public UninstallReceiver() {
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_PACKAGE_REMOVED);
            filter.addAction(Intent.ACTION_PACKAGE_RESTARTED);
            filter.addDataScheme("package");
            mContext.registerReceiver(this, filter);
        }
        
        @Override
        public void onReceive(Context context, Intent intent) {
            synchronized (mLock) {
                Uri data = intent.getData();
                if (data != null) {
                    String pkg = data.getSchemeSpecificPart();
                    removeLocked(pkg);
                    mBroadcastStats.remove(pkg);
                }
            }
        }
    }
    
    private final BroadcastStats getStatsLocked(PendingIntent pi) {
        String pkg = pi.getTargetPackage();
        BroadcastStats bs = mBroadcastStats.get(pkg);
        if (bs == null) {
            bs = new BroadcastStats();
            mBroadcastStats.put(pkg, bs);
        }
        return bs;
    }
    
    class ResultReceiver implements PendingIntent.OnFinished {
        public void onSendFinished(PendingIntent pi, Intent intent, int resultCode,
                String resultData, Bundle resultExtras) {
            synchronized (mLock) {
                BroadcastStats bs = getStatsLocked(pi);
                if (bs != null) {
                    bs.nesting--;
                    if (bs.nesting <= 0) {
                        bs.nesting = 0;
                        bs.aggregateTime += SystemClock.elapsedRealtime()
                                - bs.startTime;
                        Intent.FilterComparison fc = new Intent.FilterComparison(intent);
                        FilterStats fs = bs.filterStats.get(fc);
                        if (fs == null) {
                            fs = new FilterStats();
                            bs.filterStats.put(fc, fs);
                        }
                        fs.count++;
                    }
                }
                mBroadcastRefCount--;
                if (mBroadcastRefCount == 0) {
                    mWakeLock.release();
                }
            }
        }
    }
}
