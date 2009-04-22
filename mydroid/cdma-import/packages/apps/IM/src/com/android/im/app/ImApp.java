/*
 * Copyright (C) 2007-2008 Esmertec AG.
 * Copyright (C) 2007-2008 The Android Open Source Project
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

package com.android.im.app;

import android.app.Activity;
import android.app.Application;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Resources;
import android.database.Cursor;
import android.net.Uri;
import android.os.Broadcaster;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.provider.Im;
import android.util.Log;

import com.android.im.IConnectionCreationListener;
import com.android.im.IImConnection;
import com.android.im.IRemoteImService;
import com.android.im.R;
import com.android.im.app.adapter.ConnectionListenerAdapter;
import com.android.im.engine.ImConnection;
import com.android.im.engine.ImErrorInfo;
import com.android.im.plugin.BrandingResourceIDs;
import com.android.im.plugin.ImPluginInfo;
import com.android.im.service.ImServiceConstants;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

public class ImApp extends Application {
    public static final String LOG_TAG = "ImApp";

    public static final String EXTRA_INTENT_SEND_TO_USER = "Send2_U";
    public static final String EXTRA_INTENT_PASSWORD = "password";

    private static ImApp sImApp;

    IRemoteImService mImService;

    HashMap<Long, IImConnection> mConnections;
    MyConnListener mConnectionListener;
    HashMap<Long, ProviderDef> mProviders;

    Broadcaster mBroadcaster;

    /** A queue of messages that are waiting to be sent when service is connected.*/
    ArrayList<Message> mQueue = new ArrayList<Message>();

    /** A flag indicates that we have called to start the service.*/
    private boolean mServiceStarted;
    private Context mApplicationContext;
    private Resources mPrivateResources;

    private HashMap<Long, BrandingResources> mBrandingResources;
    private BrandingResources mDefaultBrandingResources;

    public static final int EVENT_SERVICE_CONNECTED = 100;
    public static final int EVENT_CONNECTION_CREATED = 150;
    public static final int EVENT_CONNECTION_LOGGING_IN = 200;
    public static final int EVENT_CONNECTION_LOGGED_IN = 201;
    public static final int EVENT_CONNECTION_LOGGING_OUT = 202;
    public static final int EVENT_CONNECTION_DISCONNECTED = 203;
    public static final int EVENT_CONNECTION_SUSPENDED = 204;
    public static final int EVENT_USER_PRESENCE_UPDATED = 300;
    public static final int EVENT_UPDATE_USER_PRESENCE_ERROR = 301;

    private static final String[] PROVIDER_PROJECTION = {
        Im.Provider._ID,
        Im.Provider.NAME,
        Im.Provider.FULLNAME,
        Im.Provider.SIGNUP_URL,
    };

    private static final String[] ACCOUNT_PROJECTION = {
        Im.Account._ID,
        Im.Account.PROVIDER,
        Im.Account.NAME,
        Im.Account.USERNAME,
        Im.Account.PASSWORD,
    };

    static final void log(String log) {
        Log.d(LOG_TAG, log);
    }

    public static ImApp getApplication(Activity activity) {
        // TODO should this be synchronized?
        if (sImApp == null) {
            initialize(activity);
        }

        return sImApp;
    }

    /**
     * Initialize performs the manual ImApp instantiation and initialization. When the
     * ImApp is started first in the process, the ImApp public constructor should be called,
     * and sImApp initialized. So calling initialize() later should have no effect. However,
     * if another application runs in the same process and is started first, the ImApp
     * application object won't be instantiated, and we need to call initialize() manually to
     * instantiate and initialize it.
     */
    private static void initialize(Activity activity) {
        // construct the TalkApp manually and call onCreate().
        sImApp = new ImApp();
        sImApp.mApplicationContext = activity.getApplication();
        sImApp.mPrivateResources = activity.getResources();
        sImApp.onCreate();
    }

    @Override
    public Resources getResources() {
        if (mApplicationContext == this) {
            return super.getResources();
        }

        return mPrivateResources;
    }

    @Override
    public ContentResolver getContentResolver() {
        if (mApplicationContext == this) {
            return super.getContentResolver();
        }

        return mApplicationContext.getContentResolver();
    }

    public ImApp() {
        super();
        mConnections = new HashMap<Long, IImConnection>();
        mApplicationContext = this;
        sImApp = this;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        mBroadcaster = new Broadcaster();
        loadDefaultBrandingRes();
        mBrandingResources = new HashMap<Long, BrandingResources>();
    }

    @Override
    public void onTerminate() {
        stopImServiceIfInactive();
        if (mImService != null) {
            try {
                mImService.removeConnectionCreatedListener(mConnCreationListener);
            } catch (RemoteException e) {
                Log.w(LOG_TAG, "failed to remove ConnectionCreatedListener");
            }
        }

        super.onTerminate();
    }

    public synchronized void startImServiceIfNeed() {
        if(!mServiceStarted) {
            if(Log.isLoggable(LOG_TAG, Log.DEBUG)) log("start ImService");

            Intent serviceIntent = new Intent();
            serviceIntent.setComponent(ImServiceConstants.IM_SERVICE_COMPONENT);
            mApplicationContext.startService(serviceIntent);
            mApplicationContext.bindService(serviceIntent, mImServiceConn, Context.BIND_AUTO_CREATE);
            mServiceStarted = true;

            mConnectionListener = new MyConnListener(new Handler());
        }
    }

    public synchronized void stopImServiceIfInactive() {
        boolean hasActiveConnection = true;
        synchronized (mConnections) {
            hasActiveConnection = !mConnections.isEmpty();
        }

        if (!hasActiveConnection && mServiceStarted) {
            if (Log.isLoggable(LOG_TAG, Log.DEBUG))
                log("stop ImService because there's no active connections");

            if(mImService != null) {
                mApplicationContext.unbindService(mImServiceConn);
                mImService = null;
            }
            Intent intent = new Intent();
            intent.setComponent(ImServiceConstants.IM_SERVICE_COMPONENT);
            mApplicationContext.stopService(intent);
            mServiceStarted = false;
        }
    }

    private ServiceConnection mImServiceConn = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder service) {
            if(Log.isLoggable(LOG_TAG, Log.DEBUG))
                log("service connected");

            mImService = IRemoteImService.Stub.asInterface(service);
            fetchActiveConnections();
            loadBrandingResources();

            synchronized (mQueue) {
                for (Message msg : mQueue) {
                    msg.sendToTarget();
                }
                mQueue.clear();
            }
            Message msg = Message.obtain(null, EVENT_SERVICE_CONNECTED);
            mBroadcaster.broadcast(msg);
        }

        public void onServiceDisconnected(ComponentName className) {
            if(Log.isLoggable(LOG_TAG, Log.DEBUG))
                log("service disconnected");

            mConnections.clear();
            mImService = null;
        }
    };

    public boolean serviceConnected() {
        return mImService != null;
    }

    public static long insertOrUpdateAccount(ContentResolver cr,
            long providerId, String userName, String pw) {
        String selection = Im.Account.PROVIDER + "=? AND " + Im.Account.USERNAME + "=?";
        String[] selectionArgs = {Long.toString(providerId), userName };

        Cursor c = cr.query(Im.Account.CONTENT_URI, ACCOUNT_PROJECTION,
                selection, selectionArgs, null);
        if (c != null && c.moveToFirst()) {
            // Update the password
            c.updateString(c.getColumnIndexOrThrow(Im.Account.PASSWORD), pw);
            c.commitUpdates();

            long id = c.getLong(c.getColumnIndexOrThrow(Im.Account._ID));
            c.close();
            return id;
        } else {
            ContentValues values = new ContentValues(4);
            values.put(Im.Account.PROVIDER, providerId);
            values.put(Im.Account.NAME, userName);
            values.put(Im.Account.USERNAME, userName);
            values.put(Im.Account.PASSWORD, pw);

            Uri result = cr.insert(Im.Account.CONTENT_URI, values);
            return ContentUris.parseId(result);
        }
    }

    private void loadImProviderSettings() {
        if (mProviders != null) {
            return;
        }
        mProviders = new HashMap<Long, ProviderDef>();
        ContentResolver cr = getContentResolver();
        Cursor c = cr.query(Im.Provider.CONTENT_URI, PROVIDER_PROJECTION,
                null, null, null);
        if (c == null) {
            return;
        }

        while (c.moveToNext()) {
            long id = c.getLong(0);
            String providerName = c.getString(1);
            String fullName = c.getString(2);
            String signUpUrl = c.getString(3);

            mProviders.put(id, new ProviderDef(id, providerName, fullName, signUpUrl));
        }

        c.close();
    }

    private void loadDefaultBrandingRes() {
        HashMap<Integer, Integer> resMapping = new HashMap<Integer, Integer>();

        resMapping.put(BrandingResourceIDs.DRAWABLE_LOGO, R.drawable.imlogo_s);
        resMapping.put(BrandingResourceIDs.DRAWABLE_PRESENCE_ONLINE,
                android.R.drawable.presence_online);
        resMapping.put(BrandingResourceIDs.DRAWABLE_PRESENCE_AWAY,
                android.R.drawable.presence_away);
        resMapping.put(BrandingResourceIDs.DRAWABLE_PRESENCE_BUSY,
                android.R.drawable.presence_busy);
        resMapping.put(BrandingResourceIDs.DRAWABLE_PRESENCE_INVISIBLE,
                android.R.drawable.presence_invisible);
        resMapping.put(BrandingResourceIDs.DRAWABLE_PRESENCE_OFFLINE,
                android.R.drawable.presence_offline);
        resMapping.put(BrandingResourceIDs.DRAWABLE_BLOCK, R.drawable.ic_im_block);

        resMapping.put(BrandingResourceIDs.STRING_ARRAY_SMILEY_NAMES, R.array.default_smiley_names);
        resMapping.put(BrandingResourceIDs.STRING_ARRAY_SMILEY_TEXTS, R.array.default_smiley_texts);

        resMapping.put(BrandingResourceIDs.STRING_PRESENCE_AVAILABLE, R.string.presence_available);
        resMapping.put(BrandingResourceIDs.STRING_PRESENCE_BUSY, R.string.presence_busy);
        resMapping.put(BrandingResourceIDs.STRING_PRESENCE_AWAY, R.string.presence_away);
        resMapping.put(BrandingResourceIDs.STRING_PRESENCE_IDLE, R.string.presence_idle);
        resMapping.put(BrandingResourceIDs.STRING_PRESENCE_OFFLINE, R.string.presence_offline);
        resMapping.put(BrandingResourceIDs.STRING_PRESENCE_INVISIBLE, R.string.presence_invisible);
        resMapping.put(BrandingResourceIDs.STRING_LABEL_USERNAME, R.string.label_username);
        resMapping.put(BrandingResourceIDs.STRING_ONGOING_CONVERSATION,
                R.string.ongoing_conversation);
        resMapping.put(BrandingResourceIDs.STRING_ADD_CONTACT_TITLE, R.string.add_contact_title);
        resMapping.put(BrandingResourceIDs.STRING_LABEL_INPUT_CONTACT,
                R.string.input_contact_label);
        resMapping.put(BrandingResourceIDs.STRING_BUTTON_ADD_CONTACT, R.string.invite_label);
        resMapping.put(BrandingResourceIDs.STRING_CONTACT_INFO_TITLE,
                R.string.contact_profile_title);

        resMapping.put(BrandingResourceIDs.STRING_MENU_ADD_CONTACT, R.string.menu_add_contact);
        resMapping.put(BrandingResourceIDs.STRING_MENU_BLOCK_CONTACT, R.string.menu_block_contact);
        resMapping.put(BrandingResourceIDs.STRING_MENU_CONTACT_LIST,
                R.string.menu_view_contact_list);
        resMapping.put(BrandingResourceIDs.STRING_MENU_DELETE_CONTACT,
                R.string.menu_remove_contact);
        resMapping.put(BrandingResourceIDs.STRING_MENU_END_CHAT, R.string.menu_end_conversation);
        resMapping.put(BrandingResourceIDs.STRING_MENU_INSERT_SMILEY, R.string.menu_insert_smiley);
        resMapping.put(BrandingResourceIDs.STRING_MENU_START_CHAT, R.string.menu_start_chat);
        resMapping.put(BrandingResourceIDs.STRING_MENU_VIEW_PROFILE, R.string.menu_view_profile);
        resMapping.put(BrandingResourceIDs.STRING_MENU_SWITCH_CHATS, R.string.menu_switch_chats);

        resMapping.put(BrandingResourceIDs.STRING_TOAST_CHECK_AUTO_SIGN_IN,
                R.string.check_auto_sign_in);
        resMapping.put(BrandingResourceIDs.STRING_LABEL_SIGN_UP, R.string.sign_up);

        mDefaultBrandingResources = new BrandingResources(this, resMapping, null /* default res */);
    }

    void loadBrandingResources() {
        try {
            List<ImPluginInfo> plugins = mImService.getAllPlugins();
            for (ImPluginInfo plugin : plugins) {
                long providerId = getProviderId(plugin.mProviderName);
                if (!mBrandingResources.containsKey(providerId)) {
                    BrandingResources res = new BrandingResources(this,
                            plugin, mDefaultBrandingResources);
                    mBrandingResources.put(providerId, res);
                }
            }
        } catch (RemoteException e) {
            Log.e(LOG_TAG, "Service died while loading branding resource");
        }
    }

    public long getProviderId(String name) {
        loadImProviderSettings();
        for (ProviderDef provider: mProviders.values()) {
            if(provider.mName.equals(name)) {
                return provider.mId;
            }
        }
        return -1;
    }

    public ProviderDef getProvider(long id) {
        loadImProviderSettings();
        return mProviders.get(id);
    }

    public List<ProviderDef> getProviders() {
        loadImProviderSettings();
        ArrayList<ProviderDef> result = new ArrayList<ProviderDef>();
        result.addAll(mProviders.values());
        return result;
    }

    public BrandingResources getBrandingResource(long providerId) {
        BrandingResources res = mBrandingResources.get(providerId);
        return res == null ? mDefaultBrandingResources : res;
    }

    public IImConnection createConnection(long providerId) throws RemoteException {
        IImConnection conn = getConnection(providerId);
        if (conn == null) {
            conn = mImService.createConnection(providerId);
        }
        return conn;
    }

    IImConnection getConnection(long providerId) {
        synchronized (mConnections) {
            return mConnections.get(providerId);
        }
    }

    public IImConnection getConnectionByAccount(long accountId) {
        synchronized (mConnections) {
            for (IImConnection conn : mConnections.values()) {
                try {
                    if (conn.getAccountId() == accountId) {
                        return conn;
                    }
                } catch (RemoteException e) {
                    // No server!
                }
            }
            return null;
        }
    }

    public List<IImConnection> getActiveConnections() {
        synchronized (mConnections) {
            ArrayList<IImConnection> result = new ArrayList<IImConnection>();
            result.addAll(mConnections.values());
            return result;
        }
    }

    public void callWhenServiceConnected(Handler target, Runnable callback) {
        Message msg = Message.obtain(target, callback);
        if (serviceConnected()) {
            msg.sendToTarget();
        } else {
            startImServiceIfNeed();
            synchronized (mQueue) {
                mQueue.add(msg);
            }
        }
    }

    public void removePendingCall(Handler target) {
        synchronized (mQueue) {
           Iterator<Message> iter = mQueue.iterator();
           while (iter.hasNext()) {
               Message msg = iter.next();
               if (msg.getTarget() == target) {
                   iter.remove();
               }
           }
       }
   }

    public void registerForBroadcastEvent(int what, Handler target) {
        mBroadcaster.request(what, target, what);
    }

    public void unregisterForBroadcastEvent(int what, Handler target) {
        mBroadcaster.cancelRequest(what, target, what);
    }

    public void registerForConnEvents(Handler handler) {
        mBroadcaster.request(EVENT_CONNECTION_CREATED, handler,
                EVENT_CONNECTION_CREATED);
        mBroadcaster.request(EVENT_CONNECTION_LOGGING_IN, handler,
                EVENT_CONNECTION_LOGGING_IN);
        mBroadcaster.request(EVENT_CONNECTION_LOGGED_IN, handler,
                EVENT_CONNECTION_LOGGED_IN);
        mBroadcaster.request(EVENT_CONNECTION_LOGGING_OUT, handler,
                EVENT_CONNECTION_LOGGING_OUT);
        mBroadcaster.request(EVENT_CONNECTION_SUSPENDED, handler,
                EVENT_CONNECTION_SUSPENDED);
        mBroadcaster.request(EVENT_CONNECTION_DISCONNECTED, handler,
                EVENT_CONNECTION_DISCONNECTED);
        mBroadcaster.request(EVENT_USER_PRESENCE_UPDATED, handler,
                EVENT_USER_PRESENCE_UPDATED);
        mBroadcaster.request(EVENT_UPDATE_USER_PRESENCE_ERROR, handler,
                EVENT_UPDATE_USER_PRESENCE_ERROR);
    }

    public void unregisterForConnEvents(Handler handler) {
        mBroadcaster.cancelRequest(EVENT_CONNECTION_CREATED, handler,
                EVENT_CONNECTION_CREATED);
        mBroadcaster.cancelRequest(EVENT_CONNECTION_LOGGING_IN, handler,
                EVENT_CONNECTION_LOGGING_IN);
        mBroadcaster.cancelRequest(EVENT_CONNECTION_LOGGED_IN, handler,
                EVENT_CONNECTION_LOGGED_IN);
        mBroadcaster.cancelRequest(EVENT_CONNECTION_LOGGING_OUT, handler,
                EVENT_CONNECTION_LOGGING_OUT);
        mBroadcaster.cancelRequest(EVENT_CONNECTION_SUSPENDED, handler,
                EVENT_CONNECTION_SUSPENDED);
        mBroadcaster.cancelRequest(EVENT_CONNECTION_DISCONNECTED, handler,
                EVENT_CONNECTION_DISCONNECTED);
        mBroadcaster.cancelRequest(EVENT_USER_PRESENCE_UPDATED, handler,
                EVENT_USER_PRESENCE_UPDATED);
        mBroadcaster.cancelRequest(EVENT_UPDATE_USER_PRESENCE_ERROR, handler,
                EVENT_UPDATE_USER_PRESENCE_ERROR);
    }

    void broadcastConnEvent(int what, long providerId, ImErrorInfo error) {
        if(Log.isLoggable(LOG_TAG, Log.DEBUG)){
            log("broadcasting connection event " + what + ", provider id " + providerId);
        }
        android.os.Message msg = android.os.Message.obtain(
                null,
                what,
                (int)(providerId >> 32), (int)providerId,
                error);
        mBroadcaster.broadcast(msg);
    }

    public void dismissNotifications(long providerId) {
        if (mImService != null) {
            try {
                mImService.dismissNotifications(providerId);
            } catch (RemoteException e) {
            }
        }
    }

    private void fetchActiveConnections() {
        try {
            // register the listener before fetch so that we won't miss any connection.
            mImService.addConnectionCreatedListener(mConnCreationListener);
            synchronized (mConnections) {
                for(IBinder binder: (List<IBinder>) mImService.getActiveConnections()) {
                    IImConnection conn = IImConnection.Stub.asInterface(binder);
                    long providerId = conn.getProviderId();
                    if (!mConnections.containsKey(providerId)) {
                        mConnections.put(providerId, conn);
                        conn.registerConnectionListener(mConnectionListener);
                    }
                }
            }
        } catch (RemoteException e) {
            Log.e(LOG_TAG, "fetching active connections", e);
        }
    }

    private final IConnectionCreationListener mConnCreationListener
            = new IConnectionCreationListener.Stub() {
        public void onConnectionCreated(IImConnection conn)
                throws RemoteException {
            long providerId = conn.getProviderId();
            synchronized (mConnections) {
                if (!mConnections.containsKey(providerId)) {
                    mConnections.put(providerId, conn);
                    conn.registerConnectionListener(mConnectionListener);
                }
            }
            broadcastConnEvent(EVENT_CONNECTION_CREATED, providerId, null);
        }
      };

    private final class MyConnListener extends ConnectionListenerAdapter {
        public MyConnListener(Handler handler) {
            super(handler);
        }

        @Override
        public void onConnectionStateChange(IImConnection conn, int state,
                ImErrorInfo error) {
            if(Log.isLoggable(LOG_TAG, Log.DEBUG)){
                log("onConnectionStateChange(" + state + ", " + error + ")");
            }

            try {
                int what = -1;
                long providerId = conn.getProviderId();
                switch (state) {
                case ImConnection.LOGGED_IN:
                    what = EVENT_CONNECTION_LOGGED_IN;

                    // Update the active value. We restrict to only one active
                    // account per provider right now, so update all accounts of
                    // this provider to inactive first and then update this
                    // account to active.
                    ContentValues values = new ContentValues(1);
                    values.put(Im.Account.ACTIVE, 0);
                    ContentResolver cr = getContentResolver();
                    cr.update(Im.Account.CONTENT_URI, values,
                            Im.Account.PROVIDER + "=" + providerId, null);

                    values.put(Im.Account.ACTIVE, 1);
                    cr.update(ContentUris.withAppendedId(Im.Account.CONTENT_URI, conn.getAccountId()),
                            values, null, null);

                    break;

                case ImConnection.LOGGING_IN:
                    what = EVENT_CONNECTION_LOGGING_IN;
                    break;

                case ImConnection.LOGGING_OUT:
                    what = EVENT_CONNECTION_LOGGING_OUT;
                    break;

                case ImConnection.DISCONNECTED:
                    what = EVENT_CONNECTION_DISCONNECTED;
                    synchronized (mConnections) {
                        mConnections.remove(providerId);
                    }
                    // stop the service if there isn't an active connection anymore.
                    stopImServiceIfInactive();
                    break;

                case ImConnection.SUSPENDED:
                    what = EVENT_CONNECTION_SUSPENDED;
                    break;
                }
                if (what != -1) {
                    broadcastConnEvent(what, providerId, error);
                }
            } catch (RemoteException e) {
                Log.e(LOG_TAG, "onConnectionStateChange", e);
            }
        }

        @Override
        public void onUpdateSelfPresenceError(IImConnection connection,
                ImErrorInfo error) {
            if(Log.isLoggable(LOG_TAG, Log.DEBUG)){
                log("onUpdateUserPresenceError(" + error + ")");
            }
            try {
                long providerId = connection.getProviderId();
                broadcastConnEvent(EVENT_UPDATE_USER_PRESENCE_ERROR, providerId,
                        error);
            } catch (RemoteException e) {
                Log.e(LOG_TAG, "onUpdateUserPresenceError", e);
            }
        }

        @Override
        public void onSelfPresenceUpdated(IImConnection connection) {
            if(Log.isLoggable(LOG_TAG, Log.DEBUG)) log("onUserPresenceUpdated");

            try {
                long providerId = connection.getProviderId();
                broadcastConnEvent(EVENT_USER_PRESENCE_UPDATED, providerId,
                        null);
            } catch (RemoteException e) {
                Log.e(LOG_TAG, "onUserPresenceUpdated", e);
            }
        }
    }
}
