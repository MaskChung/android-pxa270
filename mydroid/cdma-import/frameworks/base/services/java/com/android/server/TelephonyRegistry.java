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

package com.android.server;

import android.app.ActivityManagerNative;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Binder;
import android.os.IBinder;
import android.os.RemoteException;
import android.telephony.CellLocation;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.TelephonyManager;
import android.util.Log;

import java.util.ArrayList;
import java.io.FileDescriptor;
import java.io.PrintWriter;

import com.android.internal.telephony.ITelephonyRegistry;
import com.android.internal.telephony.IPhoneStateListener;
import com.android.internal.telephony.DefaultPhoneNotifier;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneStateIntentReceiver;
import com.android.internal.telephony.TelephonyIntents;

import static android.Manifest.permission.READ_PHONE_STATE;
import static android.Manifest.permission.ACCESS_COARSE_LOCATION;


/**
 * Since phone process can be restarted, this class provides a centralized
 * place that applications can register and be called back from.
 */
class TelephonyRegistry extends ITelephonyRegistry.Stub {
    private static final String TAG = "TelephonyRegistry";

    private static class Record {
        String pkgForDebug;
        IBinder binder;
        IPhoneStateListener callback;
        int events;
    }

    private Context mContext;
    private ArrayList<Record> mRecords = new ArrayList();

    private int mCallState = TelephonyManager.CALL_STATE_IDLE;
    private String mCallIncomingNumber = "";
    private ServiceState mServiceState = new ServiceState();
    private int mSignalStrength = -1;
    private boolean mMessageWaiting = false;
    private boolean mCallForwarding = false;
    private int mDataActivity = TelephonyManager.DATA_ACTIVITY_NONE;
    private int mDataConnectionState = TelephonyManager.DATA_CONNECTED;
    private boolean mDataConnectionPossible = false;
    private String mDataConnectionReason = "";
    private String mDataConnectionApn = "";
    private String mDataConnectionInterfaceName = "";
    private Bundle mCellLocation = new Bundle();

    // we keep a copy of all of the sate so we can send it out when folks register for it
    //
    // In these calls we call with the lock held.  This is safe becasuse remote
    // calls go through a oneway interface and local calls going through a handler before
    // they get to app code.

    TelephonyRegistry(Context context) {
        CellLocation.getEmpty().fillInNotifierBundle(mCellLocation);
        mContext = context;
    }

    public void listen(String pkgForDebug, IPhoneStateListener callback, int events,
            boolean notifyNow) {
        //Log.d(TAG, "listen pkg=" + pkgForDebug + " events=0x" + Integer.toHexString(events));
        if (events != 0) {
            // check permissions
            if ((events & PhoneStateListener.LISTEN_CELL_LOCATION) != 0) {
                mContext.enforceCallingOrSelfPermission(
                        android.Manifest.permission.ACCESS_COARSE_LOCATION, null);

            }

            synchronized (mRecords) {
                // register
                Record r = null;
                find_and_add: {
                    IBinder b = callback.asBinder();
                    final int N = mRecords.size();
                    for (int i=0; i<N; i++) {
                        r = mRecords.get(i);
                        if (b == r.binder) {
                            break find_and_add;
                        }
                    }
                    r = new Record();
                    r.binder = b;
                    r.callback = callback;
                    r.pkgForDebug = pkgForDebug;
                    mRecords.add(r);
                }
                int send = events & (events ^ r.events);
                r.events = events;
                if (notifyNow) {
                    if ((events & PhoneStateListener.LISTEN_SERVICE_STATE) != 0) {
                        sendServiceState(r, mServiceState);
                    }
                    if ((events & PhoneStateListener.LISTEN_SIGNAL_STRENGTH) != 0) {
                        try {
                            r.callback.onSignalStrengthChanged(mSignalStrength);
                        } catch (RemoteException ex) {
                            remove(r.binder);
                        }
                    }
                    if ((events & PhoneStateListener.LISTEN_MESSAGE_WAITING_INDICATOR) != 0) {
                        try {
                            r.callback.onMessageWaitingIndicatorChanged(mMessageWaiting);
                        } catch (RemoteException ex) {
                            remove(r.binder);
                        }
                    }
                    if ((events & PhoneStateListener.LISTEN_CALL_FORWARDING_INDICATOR) != 0) {
                        try {
                            r.callback.onCallForwardingIndicatorChanged(mCallForwarding);
                        } catch (RemoteException ex) {
                            remove(r.binder);
                        }
                    }
                    if ((events & PhoneStateListener.LISTEN_CELL_LOCATION) != 0) {
                        sendCellLocation(r, mCellLocation);
                    }
                    if ((events & PhoneStateListener.LISTEN_CALL_STATE) != 0) {
                        try {
                            r.callback.onCallStateChanged(mCallState, mCallIncomingNumber);
                        } catch (RemoteException ex) {
                            remove(r.binder);
                        }
                    }
                    if ((events & PhoneStateListener.LISTEN_DATA_CONNECTION_STATE) != 0) {
                        try {
                            r.callback.onDataConnectionStateChanged(mDataConnectionState);
                        } catch (RemoteException ex) {
                            remove(r.binder);
                        }
                    }
                    if ((events & PhoneStateListener.LISTEN_DATA_ACTIVITY) != 0) {
                        try {
                            r.callback.onDataActivity(mDataActivity);
                        } catch (RemoteException ex) {
                            remove(r.binder);
                        }
                    }
                }
            }
        } else {
            remove(callback.asBinder());
        }
    }

    private void remove(IBinder binder) {
        synchronized (mRecords) {
            final int N = mRecords.size();
            for (int i=0; i<N; i++) {
                if (mRecords.get(i).binder == binder) {
                    mRecords.remove(i);
                    return;
                }
            }
        }
    }

    public void notifyCallState(int state, String incomingNumber) {
        synchronized (mRecords) {
            mCallState = state;
            mCallIncomingNumber = incomingNumber;
            final int N = mRecords.size();
            for (int i=N-1; i>=0; i--) {
                Record r = mRecords.get(i);
                if ((r.events & PhoneStateListener.LISTEN_CALL_STATE) != 0) {
                    try {
                        r.callback.onCallStateChanged(state, incomingNumber);
                    } catch (RemoteException ex) {
                        remove(r.binder);
                    }
                }
            }
        }
        broadcastCallStateChanged(state, incomingNumber);
    }

    public void notifyServiceState(ServiceState state) {
        synchronized (mRecords) {
            mServiceState = state;
            final int N = mRecords.size();
            for (int i=N-1; i>=0; i--) {
                Record r = mRecords.get(i);
                if ((r.events & PhoneStateListener.LISTEN_SERVICE_STATE) != 0) {
                    sendServiceState(r, state);
                }
            }
        }
        broadcastServiceStateChanged(state);
    }

    public void notifySignalStrength(int signalStrengthASU) {
        synchronized (mRecords) {
            mSignalStrength = signalStrengthASU;
            final int N = mRecords.size();
            for (int i=N-1; i>=0; i--) {
                Record r = mRecords.get(i);
                if ((r.events & PhoneStateListener.LISTEN_SIGNAL_STRENGTH) != 0) {
                    try {
                        r.callback.onSignalStrengthChanged(signalStrengthASU);
                    } catch (RemoteException ex) {
                        remove(r.binder);
                    }
                }
            }
        }
        broadcastSignalStrengthChanged(signalStrengthASU);
    }

    public void notifyMessageWaitingChanged(boolean mwi) {
        synchronized (mRecords) {
            mMessageWaiting = mwi;
            final int N = mRecords.size();
            for (int i=N-1; i>=0; i--) {
                Record r = mRecords.get(i);
                if ((r.events & PhoneStateListener.LISTEN_MESSAGE_WAITING_INDICATOR) != 0) {
                    try {
                        r.callback.onMessageWaitingIndicatorChanged(mwi);
                    } catch (RemoteException ex) {
                        remove(r.binder);
                    }
                }
            }
        }
    }

    public void notifyCallForwardingChanged(boolean cfi) {
        synchronized (mRecords) {
            mCallForwarding = cfi;
            final int N = mRecords.size();
            for (int i=N-1; i>=0; i--) {
                Record r = mRecords.get(i);
                if ((r.events & PhoneStateListener.LISTEN_CALL_FORWARDING_INDICATOR) != 0) {
                    try {
                        r.callback.onCallForwardingIndicatorChanged(cfi);
                    } catch (RemoteException ex) {
                        remove(r.binder);
                    }
                }
            }
        }
    }

    public void notifyDataActivity(int state) {
        synchronized (mRecords) {
            mDataActivity = state;
            final int N = mRecords.size();
            for (int i=N-1; i>=0; i--) {
                Record r = mRecords.get(i);
                if ((r.events & PhoneStateListener.LISTEN_DATA_ACTIVITY) != 0) {
                    try {
                        r.callback.onDataActivity(state);
                    } catch (RemoteException ex) {
                        remove(r.binder);
                    }
                }
            }
        }
    }

    public void notifyDataConnection(int state, boolean isDataConnectivityPissible,
            String reason, String apn, String interfaceName) {
        synchronized (mRecords) {
            mDataConnectionState = state;
            mDataConnectionPossible = isDataConnectivityPissible;
            mDataConnectionReason = reason;
            mDataConnectionApn = apn;
            mDataConnectionInterfaceName = interfaceName;
            final int N = mRecords.size();
            for (int i=N-1; i>=0; i--) {
                Record r = mRecords.get(i);
                if ((r.events & PhoneStateListener.LISTEN_DATA_CONNECTION_STATE) != 0) {
                    try {
                        r.callback.onDataConnectionStateChanged(state);
                    } catch (RemoteException ex) {
                        remove(r.binder);
                    }
                }
            }
        }
        broadcastDataConnectionStateChanged(state, isDataConnectivityPissible,
                reason, apn, interfaceName);
    }

    public void notifyDataConnectionFailed(String reason) {
        /*
         * This is commented out because there is on onDataConnectionFailed callback
         * on PhoneStateListener.  There should be.
        synchronized (mRecords) {
            mDataConnectionFailedReason = reason;
            final int N = mRecords.size();
            for (int i=N-1; i>=0; i--) {
                Record r = mRecords.get(i);
                if ((r.events & PhoneStateListener.LISTEN_DATA_CONNECTION_FAILED) != 0) {
                    // XXX
                }
            }
        }
        */
        broadcastDataConnectionFailed(reason);
    }

    public void notifyCellLocation(Bundle cellLocation) {
        synchronized (mRecords) {
            mCellLocation = cellLocation;
            final int N = mRecords.size();
            for (int i=N-1; i>=0; i--) {
                Record r = mRecords.get(i);
                if ((r.events & PhoneStateListener.LISTEN_CELL_LOCATION) != 0) {
                    sendCellLocation(r, cellLocation);
                }
            }
        }
    }

    //
    // the new callback broadcasting
    //
    // copy the service state object so they can't mess it up in the local calls
    // 
    public void sendServiceState(Record r, ServiceState state) {
        try {
            r.callback.onServiceStateChanged(new ServiceState(state));
        } catch (RemoteException ex) {
            remove(r.binder);
        }
    }

    public void sendCellLocation(Record r, Bundle cellLocation) {
        try {
            r.callback.onCellLocationChanged(new Bundle(cellLocation));
        } catch (RemoteException ex) {
            remove(r.binder);
        }
    }


    @Override
    public void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        if (mContext.checkCallingPermission(android.Manifest.permission.DUMP)
                != PackageManager.PERMISSION_GRANTED) {
            pw.println("Permission Denial: can't dump telephony.registry from from pid="
                    + Binder.getCallingPid()
                    + ", uid=" + Binder.getCallingUid());
            return;
        }
        synchronized (mRecords) {
            final int N = mRecords.size();
            pw.println("last known state:");
            pw.println("  mCallState=" + mCallState);
            pw.println("  mCallIncomingNumber=" + mCallIncomingNumber);
            pw.println("  mServiceState=" + mServiceState);
            pw.println("  mSignalStrength=" + mSignalStrength);
            pw.println("  mMessageWaiting=" + mMessageWaiting);
            pw.println("  mCallForwarding=" + mCallForwarding);
            pw.println("  mDataActivity=" + mDataActivity);
            pw.println("  mDataConnectionState=" + mDataConnectionState);
            pw.println("  mDataConnectionPossible=" + mDataConnectionPossible);
            pw.println("  mDataConnectionReason=" + mDataConnectionReason);
            pw.println("  mDataConnectionApn=" + mDataConnectionApn);
            pw.println("  mDataConnectionInterfaceName=" + mDataConnectionInterfaceName);
            pw.println("  mCellLocation=" + mCellLocation);
            pw.println("registrations: count=" + N);
            for (int i=0; i<N; i++) {
                Record r = mRecords.get(i);
                pw.println("  " + r.pkgForDebug + " 0x" + Integer.toHexString(r.events));
            }
        }
    }

    
    //
    // the legacy intent broadcasting
    //

    private void broadcastServiceStateChanged(ServiceState state) {
        Intent intent = new Intent(TelephonyIntents.ACTION_SERVICE_STATE_CHANGED);
        Bundle data = new Bundle();
        state.fillInNotifierBundle(data);
        intent.putExtras(data);
        broadcastStickyIntent(intent);
    }

    private void broadcastSignalStrengthChanged(int asu) {
        Intent intent = new Intent(TelephonyIntents.ACTION_SIGNAL_STRENGTH_CHANGED);
        intent.putExtra(PhoneStateIntentReceiver.INTENT_KEY_ASU, asu);
        broadcastStickyIntent(intent);
    }

    private void broadcastCallStateChanged(int state, String incomingNumber) {
        Intent intent = new Intent(TelephonyIntents.ACTION_PHONE_STATE_CHANGED);
        intent.putExtra(Phone.STATE_KEY,
                DefaultPhoneNotifier.convertCallState(state).toString());
        intent.putExtra(PhoneStateIntentReceiver.INTENT_KEY_NUM, incomingNumber);
        broadcastStickyIntent(intent);
    }

    private void broadcastDataConnectionStateChanged(int state, boolean isDataConnectivityPossible,
            String reason, String apn, String interfaceName) {
        Intent intent = new Intent(TelephonyIntents.ACTION_ANY_DATA_CONNECTION_STATE_CHANGED);
        intent.putExtra(Phone.STATE_KEY, DefaultPhoneNotifier.convertDataState(state).toString());
        if (!isDataConnectivityPossible) {
            intent.putExtra(Phone.NETWORK_UNAVAILABLE_KEY, true);
        }
        if (reason != null) {
            intent.putExtra(Phone.STATE_CHANGE_REASON_KEY, reason);
        }
        intent.putExtra(Phone.DATA_APN_KEY, apn);
        intent.putExtra(Phone.DATA_IFACE_NAME_KEY, interfaceName);
        broadcastStickyIntent(intent);
    }

    private void broadcastDataConnectionFailed(String reason) {
        Intent intent = new Intent(TelephonyIntents.ACTION_DATA_CONNECTION_FAILED);
        intent.putExtra(Phone.FAILURE_REASON_KEY, reason);
        broadcastStickyIntent(intent);
    }

    private static void broadcastStickyIntent(Intent intent) {
        ActivityManagerNative.broadcastStickyIntent(intent, null);
    }
}
