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

package com.android.im.service;

import java.util.HashMap;
import java.util.Map;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.RemoteException;
import android.provider.Im;
import android.util.Log;

import com.android.im.IChatSessionManager;
import com.android.im.IConnectionListener;
import com.android.im.IContactListManager;
import com.android.im.IImConnection;
import com.android.im.IInvitationListener;
import com.android.im.engine.ChatGroupManager;
import com.android.im.engine.ConnectionListener;
import com.android.im.engine.Contact;
import com.android.im.engine.ContactListManager;
import com.android.im.engine.ImConnection;
import com.android.im.engine.ImErrorInfo;
import com.android.im.engine.ImException;
import com.android.im.engine.Invitation;
import com.android.im.engine.InvitationListener;
import com.android.im.engine.LoginInfo;
import com.android.im.engine.Presence;

public class ImConnectionAdapter extends IImConnection.Stub {
    private static final String TAG = RemoteImService.TAG;

    private static final String[] SESSION_COOKIE_PROJECTION = {
        Im.SessionCookies.NAME,
        Im.SessionCookies.VALUE,
    };

    private static final int COLUMN_SESSION_COOKIE_NAME = 0;
    private static final int COLUMN_SESSION_COOKIE_VALUE = 1;

    ImConnection mConnection;
    private ConnectionListenerAdapter mConnectionListener;
    private InvitationListenerAdapter mInvitationListener;

    ChatSessionManagerAdapter mChatSessionManager;
    ContactListManagerAdapter mContactListManager;

    ChatGroupManager mGroupManager;
    RemoteImService mService;

    long mProviderId = -1;
    long mAccountId = -1;
    boolean mAutoLoadContacts;
    int mConnectionState = ImConnection.DISCONNECTED;

    public ImConnectionAdapter(long providerId, ImConnection connection,
            RemoteImService service) {
        mProviderId = providerId;
        mConnection = connection;
        mService = service;
        mConnectionListener = new ConnectionListenerAdapter();
        mConnection.addConnectionListener(mConnectionListener);
        if ((connection.getCapability() & ImConnection.CAPABILITY_GROUP_CHAT) != 0) {
            mGroupManager = mConnection.getChatGroupManager();
            mInvitationListener = new InvitationListenerAdapter();
            mGroupManager.setInvitationListener(mInvitationListener);
        }
    }

    public ImConnection getAdaptee() {
        return mConnection;
    }

    public RemoteImService getContext() {
        return mService;
    }

    public long getProviderId() {
        return mProviderId;
    }

    public long getAccountId() {
        return mAccountId;
    }

    public int[] getSupportedPresenceStatus() {
        return mConnection.getSupportedPresenceStatus();
    }

    public void networkTypeChanged() {
        mConnection.networkTypeChanged();
    }

    void reestablishSession() {
        mConnectionState = ImConnection.LOGGING_IN;

        ContentResolver cr = mService.getContentResolver();
        if ((mConnection.getCapability() & ImConnection.CAPABILITY_SESSION_REESTABLISHMENT) != 0) {
            HashMap<String, String> cookie = querySessionCookie(cr);
            if (cookie != null) {
                Log.d(TAG, "re-establish session");
                try {
                    mConnection.reestablishSessionAsync(cookie);
                } catch (IllegalArgumentException e) {
                    Log.e(TAG, "Invalid session cookie, probably modified by others.");
                    clearSessionCookie(cr);
                }
            }
        }
    }

    private Uri getSessionCookiesUri() {
        Uri.Builder builder = Im.SessionCookies.CONTENT_URI_SESSION_COOKIES_BY.buildUpon();
        ContentUris.appendId(builder, mProviderId);
        ContentUris.appendId(builder, mAccountId);

        return builder.build();
    }

    public void login(long accountId, String userName, String password,
            boolean autoLoadContacts) {
        mAccountId = accountId;
        mAutoLoadContacts = autoLoadContacts;
        mConnectionState = ImConnection.LOGGING_IN;

        mConnection.loginAsync(new LoginInfo(userName, password));

        mChatSessionManager = new ChatSessionManagerAdapter(this);
        mContactListManager = new ContactListManagerAdapter(this);
    }

    private HashMap<String, String> querySessionCookie(ContentResolver cr) {
        Cursor c = cr.query(getSessionCookiesUri(), SESSION_COOKIE_PROJECTION, null, null, null);
        if (c == null) {
            return null;
        }

        HashMap<String, String> cookie = null;
        if (c.getCount() > 0) {
            cookie = new HashMap<String, String>();
            while(c.moveToNext()) {
                cookie.put(c.getString(COLUMN_SESSION_COOKIE_NAME),
                    c.getString(COLUMN_SESSION_COOKIE_VALUE));
            }
        }

        c.close();
        return cookie;
    }

    public void logout() {
        mConnectionState = ImConnection.LOGGING_OUT;
        mConnection.logoutAsync();
    }

    public synchronized void cancelLogin() {
        if (mConnectionState >= ImConnection.LOGGED_IN) {
            // too late
            return;
        }

        logout();
    }

    void suspend() {
        mConnectionState = ImConnection.SUSPENDING;
        mConnection.suspend();
        if (mContactListManager != null) {
            mContactListManager.clearPresence();
        }
    }

    public void registerConnectionListener(IConnectionListener listener) {
        mConnectionListener.addRemoteListener(listener);
    }

    public void unregisterConnectionListener(IConnectionListener listener) {
        mConnectionListener.removeRemoteListener(listener);
    }

    public void setInvitationListener(IInvitationListener listener) {
        if(mInvitationListener != null) {
            mInvitationListener.mRemoteListener = listener;
        }
    }

    public IChatSessionManager getChatSessionManager() {
        return mChatSessionManager;
    }

    public IContactListManager getContactListManager() {
        return mContactListManager;
    }

    public int getChatSessionCount() {
        if (mChatSessionManager == null) {
            return 0;
        }
        return mChatSessionManager.getChatSessionCount();
    }

    public Contact getLoginUser() {
        return mConnection.getLoginUser();
    }

    public Presence getUserPresence() {
        return mConnection.getUserPresence();
    }

    public int updateUserPresence(Presence newPresence) {
        try {
            mConnection.updateUserPresenceAsync(newPresence);
        } catch (ImException e) {
            return e.getImError().getCode();
        }

        return ImErrorInfo.NO_ERROR;
    }

    public int getState() {
        return mConnectionState;
    }

    public void rejectInvitation(long id){
        handleInvitation(id, false);
    }

    public void acceptInvitation(long id) {
        handleInvitation(id, true);
    }

    private void handleInvitation(long id, boolean accept) {
        if(mGroupManager == null) {
            return;
        }
        ContentResolver cr = mService.getContentResolver();
        Cursor c = cr.query(ContentUris.withAppendedId(Im.Invitation.CONTENT_URI, id), null, null, null, null);
        if(c == null) {
            return;
        }
        if(c.moveToFirst()) {
            String inviteId = c.getString(c.getColumnIndexOrThrow(Im.Invitation.INVITE_ID));
            int status;
            if(accept) {
                mGroupManager.acceptInvitationAsync(inviteId);
                status = Im.Invitation.STATUS_ACCEPTED;
            } else {
                mGroupManager.rejectInvitationAsync(inviteId);
                status = Im.Invitation.STATUS_REJECTED;
            }
            c.updateInt(c.getColumnIndexOrThrow(Im.Invitation.STATUS), status);
            c.commitUpdates();
        }
        c.close();
    }

    void saveSessionCookie(ContentResolver cr) {
        HashMap<String, String> cookies = mConnection.getSessionContext();

        int i = 0;
        ContentValues[] valuesList = new ContentValues[cookies.size()];

        for(Map.Entry<String,String> entry : cookies.entrySet()){
            ContentValues values = new ContentValues(2);

            values.put(Im.SessionCookies.NAME, entry.getKey());
            values.put(Im.SessionCookies.VALUE, entry.getValue());

            valuesList[i++] = values;
        }

        cr.bulkInsert(getSessionCookiesUri(), valuesList);
    }

    void clearSessionCookie(ContentResolver cr) {
        cr.delete(getSessionCookiesUri(), null, null);
    }

    final class ConnectionListenerAdapter
            extends RemoteListenerManager<IConnectionListener>
            implements ConnectionListener{
        public void onStateChanged(final int state, final ImErrorInfo error) {
            synchronized (this) {
                if (state == ImConnection.LOGGED_IN
                        && mConnectionState == ImConnection.LOGGING_OUT) {
                    // A bit tricky here. The engine did login successfully
                    // but the notification comes a bit late; user has already
                    // issued a cancelLogin() and that cannot be undone. Here
                    // we have to ignore the LOGGED_IN event and wait for
                    // the upcoming DISCONNECTED.
                    return;
                }

                if (state != ImConnection.DISCONNECTED) {
                    mConnectionState = state;
                }
            }

            ContentResolver cr = mService.getContentResolver();
            if(state == ImConnection.LOGGED_IN) {
                if ((mConnection.getCapability() & ImConnection.CAPABILITY_SESSION_REESTABLISHMENT) != 0){
                    saveSessionCookie(cr);
                }

                if(mAutoLoadContacts && mContactListManager.getState()
                        != ContactListManager.LISTS_LOADED) {
                    mContactListManager.loadContactLists();
                }

                for (ChatSessionAdapter session : mChatSessionManager.mActiveSessions.values()) {
                    session.sendPostponedMessages();
                }
            } else if(state == ImConnection.DISCONNECTED) {
                mService.removeConnection(ImConnectionAdapter.this);

                clearSessionCookie(cr);
                // mContactListManager might still be null if we fail
                // immediately in loginAsync (say, an invalid host URL)
                if (mContactListManager != null) {
                    mContactListManager.clearOnLogout();
                }
                if (mChatSessionManager != null) {
                    mChatSessionManager.closeAllChatSessions();
                }

                mConnectionState = state;
            } else if(state == ImConnection.SUSPENDED && error != null) {
                // re-establish failed, schedule to retry
                // TODO increase delay after retry failed.
                mService.scheduleReconnect(15000);
            }

            notifyRemoteListeners(new ListenerInvocation<IConnectionListener>() {

                public void invoke(IConnectionListener remoteListener)
                        throws RemoteException {
                    remoteListener.onStateChanged(ImConnectionAdapter.this,
                            state, error);
                }

            });
        }

        public void onUserPresenceUpdated() {
            notifyRemoteListeners(new ListenerInvocation<IConnectionListener>() {

                public void invoke(IConnectionListener remoteListener)
                        throws RemoteException {
                    remoteListener.onUserPresenceUpdated(ImConnectionAdapter.this);
                }

            });
        }

        public void onUpdatePresenceError(final ImErrorInfo error) {
            notifyRemoteListeners(new ListenerInvocation<IConnectionListener>() {

                public void invoke(IConnectionListener remoteListener)
                        throws RemoteException {
                    remoteListener.onUpdatePresenceError(ImConnectionAdapter.this,
                            error);
                }

            });
        }
    }

    final class InvitationListenerAdapter implements InvitationListener {
        IInvitationListener mRemoteListener;

        public void onGroupInvitation(Invitation invitation) {
            String sender = invitation.getSender().getScreenName();
            ContentValues values = new ContentValues(7);
            values.put(Im.Invitation.PROVIDER, mProviderId);
            values.put(Im.Invitation.ACCOUNT, mAccountId);
            values.put(Im.Invitation.INVITE_ID, invitation.getInviteID());
            values.put(Im.Invitation.SENDER, sender);
            values.put(Im.Invitation.GROUP_NAME, invitation.getGroupAddress().getScreenName());
            values.put(Im.Invitation.NOTE, invitation.getReason());
            values.put(Im.Invitation.STATUS, Im.Invitation.STATUS_PENDING);
            ContentResolver resolver = mService.getContentResolver();
            Uri uri = resolver.insert(Im.Invitation.CONTENT_URI, values);
            long id = ContentUris.parseId(uri);
            try {
                if (mRemoteListener != null) {
                    mRemoteListener.onGroupInvitation(id);
                    return;
                }
            } catch (RemoteException e) {
                Log.i(TAG, "onGroupInvitation: dead listener "
                        + mRemoteListener +"; removing");
                mRemoteListener = null;
            }
            // No listener registered or failed to notify the listener, send a
            // notification instead.
            mService.getStatusBarNotifier().notifyGroupInvitation(mProviderId, mAccountId, id, sender);
        }
    }
}
