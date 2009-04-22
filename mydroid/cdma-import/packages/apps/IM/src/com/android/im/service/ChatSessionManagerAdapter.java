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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import android.os.RemoteException;

import com.android.im.IChatSession;
import com.android.im.IChatSessionListener;
import com.android.im.IChatSessionManager;
import com.android.im.engine.Address;
import com.android.im.engine.ChatGroup;
import com.android.im.engine.ChatGroupManager;
import com.android.im.engine.ChatSession;
import com.android.im.engine.ChatSessionListener;
import com.android.im.engine.ChatSessionManager;
import com.android.im.engine.Contact;
import com.android.im.engine.GroupListener;
import com.android.im.engine.ImConnection;
import com.android.im.engine.ImErrorInfo;

public class ChatSessionManagerAdapter extends IChatSessionManager.Stub {
    static final String TAG = RemoteImService.TAG;

    ImConnectionAdapter mConnection;
    ChatSessionManager mSessionManager;
    ChatGroupManager mGroupManager;
    HashMap<String, ChatSessionAdapter> mActiveSessions;
    ChatSessionListenerAdapter mSessionListenerAdapter;

    public ChatSessionManagerAdapter(ImConnectionAdapter connection) {
        mConnection = connection;
        ImConnection connAdaptee = connection.getAdaptee();
        mSessionManager = connAdaptee.getChatSessionManager();
        mActiveSessions = new HashMap<String, ChatSessionAdapter>();
        mSessionListenerAdapter = new ChatSessionListenerAdapter();
        mSessionManager.addChatSessionListener(mSessionListenerAdapter);

        if((connAdaptee.getCapability() & ImConnection.CAPABILITY_GROUP_CHAT) != 0) {
            mGroupManager = connAdaptee.getChatGroupManager();
            mGroupManager.addGroupListener(new ChatGroupListenerAdpater());
        }
    }

    public IChatSession createChatSession(String contactAddress) {
        ContactListManagerAdapter listManager =
            (ContactListManagerAdapter) mConnection.getContactListManager();
        Contact contact = listManager.getContactByAddress(contactAddress);
        if(contact == null) {
            try {
                contact = listManager.createTemporaryContact(contactAddress);
            } catch (IllegalArgumentException e) {
                mSessionListenerAdapter.notifyChatSessionCreateFailed(contactAddress,
                        new ImErrorInfo(ImErrorInfo.ILLEGAL_CONTACT_ADDRESS,
                                "Invalid contact address:" + contactAddress));
                return null;
            }
        }
        ChatSession session = mSessionManager.createChatSession(contact);
        return getChatSessionAdapter(session);
    }

    public void closeChatSession(ChatSessionAdapter adapter) {
        synchronized (mActiveSessions) {
            ChatSession session = adapter.getAdaptee();
            mSessionManager.closeChatSession(session);
            mActiveSessions.remove(adapter.getAddress());
        }
    }

    public void closeAllChatSessions() {
        synchronized (mActiveSessions) {
            ArrayList<ChatSessionAdapter> sessions =
                new ArrayList<ChatSessionAdapter>(mActiveSessions.values());
            for (ChatSessionAdapter ses : sessions) {
                ses.leave();
            }
        }
    }

    public void updateChatSession(String oldAddress, ChatSessionAdapter adapter) {
        synchronized (mActiveSessions) {
            mActiveSessions.remove(oldAddress);
            mActiveSessions.put(adapter.getAddress(), adapter);
        }
    }

    public IChatSession getChatSession(String address) {
        synchronized (mActiveSessions) {
            return mActiveSessions.get(address);
        }
    }

    public List getActiveChatSessions() {
        synchronized (mActiveSessions) {
            return new ArrayList<ChatSessionAdapter>(mActiveSessions.values());
        }
    }

    public int getChatSessionCount() {
        synchronized (mActiveSessions) {
            return mActiveSessions.size();
        }
    }

    public void registerChatSessionListener(IChatSessionListener listener) {
        if (listener != null) {
            mSessionListenerAdapter.addRemoteListener(listener);
        }
    }

    public void unregisterChatSessionListener(IChatSessionListener listener) {
        mSessionListenerAdapter.removeRemoteListener(listener);
    }

    ChatSessionAdapter getChatSessionAdapter(ChatSession session) {
        synchronized (mActiveSessions) {
            Address participantAddress = session.getParticipant().getAddress();
            String key = participantAddress.getFullName();
            ChatSessionAdapter adapter = mActiveSessions.get(key);
            if (adapter == null) {
                adapter = new ChatSessionAdapter(session, mConnection);
                mActiveSessions.put(key, adapter);
            }
            return adapter;
        }
    }

    class ChatSessionListenerAdapter
            extends RemoteListenerManager<IChatSessionListener>
            implements ChatSessionListener {

        public void onChatSessionCreated(ChatSession session) {
            final IChatSession sessionAdapter = getChatSessionAdapter(session);
            notifyRemoteListeners(new ListenerInvocation<IChatSessionListener>() {
                public void invoke(IChatSessionListener remoteListener)
                        throws RemoteException {
                    remoteListener.onChatSessionCreated(sessionAdapter);
                }
            });
        }

        public void notifyChatSessionCreateFailed(final String name, final ImErrorInfo error) {
            notifyRemoteListeners(new ListenerInvocation<IChatSessionListener>() {
                public void invoke(IChatSessionListener remoteListener)
                        throws RemoteException {
                    remoteListener.onChatSessionCreateError(name, error);
                }
            });
        }
    }

    class ChatGroupListenerAdpater implements GroupListener {
        public void onGroupCreated(ChatGroup group) {
        }

        public void onGroupDeleted(ChatGroup group) {
            closeSession(group);
        }

        public void onGroupError(int errorType, String name, ImErrorInfo error) {
            if(errorType == ERROR_CREATING_GROUP) {
                mSessionListenerAdapter.notifyChatSessionCreateFailed(name, error);
            }
        }

        public void onJoinedGroup(ChatGroup group) {
            mSessionManager.createChatSession(group);
        }

        public void onLeftGroup(ChatGroup group) {
            closeSession(group);
        }

        private void closeSession(ChatGroup group) {
            String address = group.getAddress().getFullName();
            IChatSession session = getChatSession(address);
            if(session != null) {
                closeChatSession((ChatSessionAdapter) session);
            }
        }
    }
}
