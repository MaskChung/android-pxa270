package com.android.server.status;

import android.os.IBinder;
import android.util.Log;
import android.view.View;
import java.util.ArrayList;

class NotificationViewList {
    private ArrayList<StatusBarNotification> mOngoing = new ArrayList();
    private ArrayList<StatusBarNotification> mLatest = new ArrayList();

    NotificationViewList() {
    }

    private static final int indexInList(ArrayList<StatusBarNotification> list, NotificationData n){
        final int N = list.size();
        for (int i=0; i<N; i++) {
            StatusBarNotification that = list.get(i);
            if (that.data == n) {
                return i;
            }
        }
        return -1;
    }

    int getIconIndex(NotificationData n) {
        final int ongoingSize = mOngoing.size();
        final int latestSize = mLatest.size();
        if (n.ongoingEvent) {
            int index = indexInList(mOngoing, n);
            if (index >= 0) {
                return latestSize + index + 1;
            } else {
                return -1;
            }
        } else {
            return indexInList(mLatest, n) + 1;
        }
    }

    void remove(StatusBarNotification notification) {
        NotificationData n = notification.data;
        int index;
        index = indexInList(mOngoing, n);
        if (index >= 0) {
            mOngoing.remove(index);
            return;
        }
        index = indexInList(mLatest, n);
        if (index >= 0) {
            mLatest.remove(index);
            return;
        }
    }

    ArrayList<StatusBarNotification> notificationsForPackage(String packageName) {
        ArrayList<StatusBarNotification> list = new ArrayList<StatusBarNotification>();
        int N = mOngoing.size();
        for (int i=0; i<N; i++) {
            if (matchPackage(mOngoing.get(i), packageName)) {
                list.add(mOngoing.get(i));
            }
        }
        N = mLatest.size();
        for (int i=0; i<N; i++) {
            if (matchPackage(mLatest.get(i), packageName)) {
                list.add(mLatest.get(i));
            }
        }
        return list;
    }
    
    private final boolean matchPackage(StatusBarNotification snb, String packageName) {
        if (snb.data.contentIntent != null) {
            if (snb.data.contentIntent.getTargetPackage().equals(packageName)) {
                return true;
            }
        } else if (snb.data.pkg != null && snb.data.pkg.equals(packageName)) {
            return true;
        }
        return false;
    }
    
    private static final int indexForKey(ArrayList<StatusBarNotification> list, IBinder key) {
        final int N = list.size();
        for (int i=0; i<N; i++) {
            if (list.get(i).key == key) {
                return i;
            }
        }
        return -1;
    }

    StatusBarNotification get(IBinder key) {
        int index;
        index = indexForKey(mOngoing, key);
        if (index >= 0) {
            return mOngoing.get(index);
        }
        index = indexForKey(mLatest, key);
        if (index >= 0) {
            return mLatest.get(index);
        }
        return null;
    }

    // gets the index of the notification in its expanded parent view
    int getExpandedIndex(StatusBarNotification notification) {
        ArrayList<StatusBarNotification> list = notification.data.ongoingEvent ? mOngoing : mLatest;
        return list.size() - indexForKey(list, notification.key) - 1;
    }

    void clearViews() {
        int N = mOngoing.size();
        for (int i=0; i<N; i++) {
            mOngoing.get(i).view = null;
        }
        N = mLatest.size();
        for (int i=0; i<N; i++) {
            mLatest.get(i).view = null;
        }
    }
    
    int ongoingCount() {
        return mOngoing.size();
    }

    int latestCount() {
        return mLatest.size();
    }

    StatusBarNotification getOngoing(int index) {
        return mOngoing.get(index);
    }

    StatusBarNotification getLatest(int index) {
        return mLatest.get(index);
    }

    int size() {
        return mOngoing.size() + mLatest.size();
    }

    void add(StatusBarNotification notification) {
        ArrayList<StatusBarNotification> list = notification.data.ongoingEvent ? mOngoing : mLatest;
        long when = notification.data.when;
        final int N = list.size();
        int index = N;
        for (int i=0; i<N; i++) {
            StatusBarNotification that = list.get(i);
            if (that.data.when > when) {
                index = i;
                break;
            }
        }
        list.add(index, notification);

        if (StatusBarService.SPEW) {
            String s = "";
            for (int i=0; i<mOngoing.size(); i++) {
                StatusBarNotification that = mOngoing.get(i);
                if (that.key == notification.key) {
                    s += "[";
                }
                s += that.data.when;
                if (that.key == notification.key) {
                    s += "]";
                }
                s += " ";
            }
            Log.d(StatusBarService.TAG, "NotificationViewList ongoing index=" + index + ": " + s);

            s = "";
            for (int i=0; i<mLatest.size(); i++) {
                StatusBarNotification that = mLatest.get(i);
                if (that.key == notification.key) {
                    s += "[";
                }
                s += that.data.when;
                if (that.key == notification.key) {
                    s += "]";
                }
                s += " ";
            }
            Log.d(StatusBarService.TAG, "NotificationViewList latest  index=" + index + ": " + s);
        }
    }

    StatusBarNotification get(View view) {
        int N = mOngoing.size();
        for (int i=0; i<N; i++) {
            StatusBarNotification notification = mOngoing.get(i);
            View v = notification.view;
            if (v == view) {
                return notification;
            }
        }
        N = mLatest.size();
        for (int i=0; i<N; i++) {
            StatusBarNotification notification = mLatest.get(i);
            View v = notification.view;
            if (v == view) {
                return notification;
            }
        }
        return null;
    }

    void update(StatusBarNotification notification) {
        remove(notification);
        add(notification);
    }

    boolean hasClearableItems() {
        int N = mLatest.size();
        for (int i=0; i<N; i++) {
            if (mLatest.get(i).data.clearable) {
                return true;
            }
        }
        return false;
    }
}
