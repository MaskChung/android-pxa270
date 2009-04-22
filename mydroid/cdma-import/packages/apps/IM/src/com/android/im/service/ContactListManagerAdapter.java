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
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Vector;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.RemoteException;
import android.provider.Im;
import android.util.Log;
import android.widget.Toast;

import com.android.im.IContactList;
import com.android.im.IContactListListener;
import com.android.im.IContactListManager;
import com.android.im.ISubscriptionListener;
import com.android.im.R;
import com.android.im.engine.Address;
import com.android.im.engine.Contact;
import com.android.im.engine.ContactList;
import com.android.im.engine.ContactListListener;
import com.android.im.engine.ContactListManager;
import com.android.im.engine.ImErrorInfo;
import com.android.im.engine.ImException;
import com.android.im.engine.Presence;
import com.android.im.engine.SubscriptionRequestListener;

public class ContactListManagerAdapter extends IContactListManager.Stub {
    static final String TAG = RemoteImService.TAG;

    ImConnectionAdapter mConn;
    ContentResolver     mResolver;

    private ContactListManager          mAdaptee;
    private ContactListListenerAdapter  mContactListListenerAdapter;
    private SubscriptionRequestListenerAdapter mSubscriptionListenerAdapter;

    HashMap<Address, ContactListAdapter> mContactLists;
    HashMap<String, Contact> mTemporaryContacts;

    HashSet<String> mValidatedContactLists;
    HashSet<String> mValidatedContacts;
    HashSet<String> mValidatedBlockedContacts;

    private long mAccountId;
    private long mProviderId;

    private Uri mAvatarUrl;
    private Uri mContactUrl;

    static final long FAKE_TEMPORARY_LIST_ID = -1;
    static final String[] CONTACT_LIST_ID_PROJECTION  = { Im.ContactList._ID };

    RemoteImService mContext;

    public ContactListManagerAdapter(ImConnectionAdapter conn) {
        mAdaptee  = conn.getAdaptee().getContactListManager();
        mConn     = conn;
        mContext  = conn.getContext();
        mResolver = mContext.getContentResolver();

        mContactListListenerAdapter = new ContactListListenerAdapter();
        mSubscriptionListenerAdapter = new SubscriptionRequestListenerAdapter();
        mContactLists = new HashMap<Address, ContactListAdapter>();
        mTemporaryContacts = new HashMap<String, Contact>();
        mValidatedContacts = new HashSet<String>();
        mValidatedContactLists = new HashSet<String>();
        mValidatedBlockedContacts = new HashSet<String>();

        mAdaptee.addContactListListener(mContactListListenerAdapter);
        mAdaptee.setSubscriptionRequestListener(mSubscriptionListenerAdapter);

        mAccountId  = mConn.getAccountId();
        mProviderId = mConn.getProviderId();

        Uri.Builder builder = Im.Avatars.CONTENT_URI_AVATARS_BY.buildUpon();
        ContentUris.appendId(builder, mProviderId);
        ContentUris.appendId(builder, mAccountId);

        mAvatarUrl = builder.build();

        builder = Im.Contacts.CONTENT_URI_CONTACTS_BY.buildUpon();
        ContentUris.appendId(builder, mProviderId);
        ContentUris.appendId(builder, mAccountId);

        mContactUrl = builder.build();
    }

    public int createContactList(String name, List<Contact> contacts) {
        try {
            mAdaptee.createContactListAsync(name, contacts);
        } catch (ImException e) {
            return e.getImError().getCode();
        }

        return ImErrorInfo.NO_ERROR;
    }

    public int deleteContactList(String name) {
        try {
            mAdaptee.deleteContactListAsync(name);
        } catch (ImException e) {
            return e.getImError().getCode();
        }

        return ImErrorInfo.NO_ERROR;
    }

    public List getContactLists() {
        synchronized (mContactLists) {
            return new ArrayList<ContactListAdapter>(mContactLists.values());
        }
    }

    public int removeContact(String address) {
        if(isTemporary(address)) {
            // For temporary contact, just close the session and delete him in
            // database.
            closeChatSession(address);

            String selection = Im.Contacts.USERNAME + "=?";
            String[] selectionArgs = { address };
            mResolver.delete(mContactUrl, selection, selectionArgs);
            synchronized (mTemporaryContacts) {
                mTemporaryContacts.remove(address);
            }
        } else {
            synchronized (mContactLists) {
                Contact c = getContactByAddress(address);
                for(ContactListAdapter list : mContactLists.values()) {
                    int resCode = list.removeContact(c);
                    if (ImErrorInfo.NO_ERROR != resCode) {
                        return resCode;
                    }
                }
            }
        }

        return ImErrorInfo.NO_ERROR;
    }

    public void approveSubscription(String address) {
        mAdaptee.approveSubscriptionRequest(address);
    }

    public void declineSubscription(String address) {
        mAdaptee.declineSubscriptionRequest(address);
    }

    public int blockContact(String address) {
        try {
            mAdaptee.blockContactAsync(address);
        } catch (ImException e) {
            return e.getImError().getCode();
        }

        return ImErrorInfo.NO_ERROR;
    }

    public int unBlockContact(String address) {
        try {
            mAdaptee.unblockContactAsync(address);
        } catch (ImException e) {
            Log.e(TAG, e.getMessage());
            return e.getImError().getCode();
        }

        return ImErrorInfo.NO_ERROR;
    }

    public boolean isBlocked(String address) {
        try {
            return mAdaptee.isBlocked(address);
        } catch (ImException e) {
            Log.e(TAG, e.getMessage());
            return false;
        }
    }

    public void registerContactListListener(IContactListListener listener) {
        mContactListListenerAdapter.addRemoteListener(listener);
    }

    public void unregisterContactListListener(IContactListListener listener) {
        mContactListListenerAdapter.removeRemoteListener(listener);
    }

    public void registerSubscriptionListener(ISubscriptionListener listener) {
        mSubscriptionListenerAdapter.addRemoteListener(listener);
    }

    public void unregisterSubscriptionListener(ISubscriptionListener listener) {
        mSubscriptionListenerAdapter.removeRemoteListener(listener);
    }

    public IContactList getContactList(String name) {
        return getContactListAdapter(name);
    }

    public void loadContactLists() {
        if(mAdaptee.getState() == ContactListManager.LISTS_NOT_LOADED){
            clearValidatedContactsAndLists();
            mAdaptee.loadContactListsAsync();
        }
    }

    public int getState() {
        return mAdaptee.getState();
    }

    public Contact getContactByAddress(String address) {
        Contact c = mAdaptee.getContact(address);
        if(c == null) {
            synchronized (mTemporaryContacts) {
                return mTemporaryContacts.get(address);
            }
        } else {
            return c;
        }
    }

    public Contact createTemporaryContact(String address) {
        Contact c = mAdaptee.createTemporaryContact(address);
        insertTemporary(c);
        return c;
    }

    public long queryOrInsertContact(Contact c) {
        long result;

        String username = c.getAddress().getFullName();
        String selection = Im.Contacts.USERNAME + "=?";
        String[] selectionArgs = { username };
        String[] projection = {Im.Contacts._ID};

        Cursor cursor = mResolver.query(mContactUrl, projection, selection,
                selectionArgs, null);

        if(cursor != null && cursor.moveToFirst()) {
            result = cursor.getLong(0);
        } else {
            result = insertTemporary(c);
        }

        if(cursor != null) {
            cursor.close();
        }
        return result;
    }

    private long insertTemporary(Contact c) {
        synchronized (mTemporaryContacts) {
            mTemporaryContacts.put(c.getAddress().getFullName(), c);
        }
        Uri uri = insertContactContent(c, FAKE_TEMPORARY_LIST_ID);
        return ContentUris.parseId(uri);
    }

    /**
     * Tells if a contact is a temporary one which is not in the list of
     * contacts that we subscribe presence for. Usually created because of the
     * user is having a chat session with this contact.
     *
     * @param address
     *            the address of the contact.
     * @return <code>true</code> if it's a temporary contact;
     *         <code>false</code> otherwise.
     */
    public boolean isTemporary(String address) {
        synchronized (mTemporaryContacts) {
            return mTemporaryContacts.containsKey(address);
        }
    }

    ContactListAdapter getContactListAdapter(String name) {
        synchronized (mContactLists) {
            for (ContactListAdapter list : mContactLists.values()) {
                if (name.equals(list.getName())) {
                    return list;
                }
            }

            return null;
        }
    }

    ContactListAdapter getContactListAdapter(Address address) {
        synchronized (mContactLists) {
            return mContactLists.get(address);
        }
    }

    private class Exclusion {
        private StringBuilder mSelection;
        private List mSelectionArgs;
        private String mExclusionColumn;

        Exclusion(String exclusionColumn, Collection<String> items) {
            mSelection = new StringBuilder();
            mSelectionArgs = new ArrayList();
            mExclusionColumn = exclusionColumn;
            for (String s : items) {
                add(s);
            }
        }

        public void add(String exclusionItem) {
            if (mSelection.length()==0) {
                mSelection.append(mExclusionColumn + "!=?");
            } else {
                mSelection.append(" AND " + mExclusionColumn + "!=?");
            }
            mSelectionArgs.add(exclusionItem);
        }

        public String getSelection() {
            return mSelection.toString();
        }

        public String[] getSelectionArgs() {
            return (String []) mSelectionArgs.toArray(new String[0]);
        }
    }

    private void removeObsoleteContactsAndLists() {
        // remove all contacts for this provider & account which have not been
        // added since login, yet still exist in db from a prior login
        Exclusion exclusion = new Exclusion(Im.Contacts.USERNAME, mValidatedContacts);
        mResolver.delete(mContactUrl, exclusion.getSelection(), exclusion.getSelectionArgs());

        // remove all blocked contacts for this provider & account which have not been
        // added since login, yet still exist in db from a prior login
        exclusion = new Exclusion(Im.BlockedList.USERNAME, mValidatedBlockedContacts);
        Uri.Builder builder = Im.BlockedList.CONTENT_URI.buildUpon();
        ContentUris.appendId(builder, mProviderId);
        ContentUris.appendId(builder, mAccountId);
        Uri uri = builder.build();
        mResolver.delete(uri, exclusion.getSelection(), exclusion.getSelectionArgs());

        // remove all contact lists for this provider & account which have not been
        // added since login, yet still exist in db from a prior login
        exclusion = new Exclusion(Im.ContactList.NAME, mValidatedContactLists);
        builder = Im.ContactList.CONTENT_URI.buildUpon();
        ContentUris.appendId(builder, mProviderId);
        ContentUris.appendId(builder, mAccountId);
        uri = builder.build();
        mResolver.delete(uri, exclusion.getSelection(), exclusion.getSelectionArgs());

    }

    final class ContactListListenerAdapter
            extends RemoteListenerManager<IContactListListener>
            implements ContactListListener {
        private boolean mAllContactsLoaded;

        // class to hold contact changes made before mAllContactsLoaded
        private class StoredContactChange {
            int mType;
            ContactList mList;
            Contact mContact;

            StoredContactChange(int type, ContactList list, Contact contact) {
                mType = type;
                mList = list;
                mContact = contact;
            }
        }
        private Vector<StoredContactChange> mDelayedContactChanges =
                new Vector<StoredContactChange>();

        public void onContactsPresenceUpdate(final Contact[] contacts) {
            // The client listens only to presence updates for now. Update
            // the avatars first to ensure it can get the new avatar when
            // presence updated.
            // TODO: Don't update avatar now since none of the server supports it
            // updateAvatarsContent(contacts);
            updatePresenceContent(contacts);

            notifyRemoteListeners(new ListenerInvocation<IContactListListener>() {

                public void invoke(IContactListListener remoteListener)
                        throws RemoteException {
                    remoteListener.onContactsPresenceUpdate(contacts);
                }

            });
        }

        public void onContactChange(final int type, final ContactList list,
                final Contact contact) {
            ContactListAdapter removed = null;
            String notificationText = null;

            switch (type) {
            case LIST_LOADED:
            case LIST_CREATED:
                addContactListContent(list);
                break;

            case LIST_DELETED:
                removed = removeContactListFromDataBase(list.getName());
                // handle case where a list is deleted before mAllContactsLoaded
                if (!mAllContactsLoaded) {
                    // if a cached contact list is deleted before the actual contact list is
                    // downloaded from the server, we will have to remove the list again once
                    // once mAllContactsLoaded is true
                    if (!mValidatedContactLists.contains(list.getName())) {
                        mDelayedContactChanges.add(new StoredContactChange(type, list, contact));
                    }
                }
                break;

            case LIST_CONTACT_ADDED:
                long listId = getContactListAdapter(list.getAddress()).getDataBaseId();
                String contactAddress = contact.getAddress().getFullName();
                if(isTemporary(contactAddress)){
                    moveTemporaryContactToList(contactAddress, listId);
                } else {
                    insertContactContent(contact, listId);
                }
                notificationText = mContext.getResources().getString(
                        R.string.add_contact_success, contact.getName());
                // handle case where a contact is added before mAllContactsLoaded
                if (!mAllContactsLoaded) {
                    // if a contact is added to a cached contact list before the actual contact
                    // list is downloaded from the server, we will have to add the contact to
                    // the contact list once mAllContactsLoaded is true
                    if (!mValidatedContactLists.contains(list.getName())) {
                        mDelayedContactChanges.add(new StoredContactChange(type, list, contact));
                    }
                }
                break;

            case LIST_CONTACT_REMOVED:
                deleteContactFromDataBase(contact, list);
                // handle case where a contact is removed before mAllContactsLoaded
                if (!mAllContactsLoaded) {
                    // if a contact is added to a cached contact list before the actual contact
                    // list is downloaded from the server, we will have to add the contact to
                    // the contact list once mAllContactsLoaded is true
                    if (!mValidatedContactLists.contains(list.getName())) {
                        mDelayedContactChanges.add(new StoredContactChange(type, list, contact));
                    }
                }

                // Clear ChatSession if any.
                String address = contact.getAddress().getFullName();
                closeChatSession(address);

                notificationText = mContext.getResources().getString(
                        R.string.delete_contact_success, contact.getName());
                break;

            case LIST_RENAMED:
                updateListNameInDataBase(list);
                // handle case where a list is renamed before mAllContactsLoaded
                if (!mAllContactsLoaded) {
                    // if a contact list name is updated before the actual contact list is
                    // downloaded from the server, we will have to update the list name again
                    // once mAllContactsLoaded is true
                    if (!mValidatedContactLists.contains(list.getName())) {
                        mDelayedContactChanges.add(new StoredContactChange(type, list, contact));
                    }
                }
                break;

            case CONTACT_BLOCKED:
                insertBlockedContactToDataBase(contact);
                address = contact.getAddress().getFullName();
                updateContactType(address, Im.Contacts.TYPE_BLOCKED);
                closeChatSession(address);
                notificationText = mContext.getResources().getString(
                        R.string.block_contact_success, contact.getName());
                break;

            case CONTACT_UNBLOCKED:
                removeBlockedContactFromDataBase(contact);
                notificationText = mContext.getResources().getString(
                        R.string.unblock_contact_success, contact.getName());
                // handle case where a contact is unblocked before mAllContactsLoaded
                if (!mAllContactsLoaded) {
                    // if a contact list name is updated before the actual contact list is
                    // downloaded from the server, we will have to update the list name again
                    // once mAllContactsLoaded is true
                    if (!mValidatedBlockedContacts.contains(contact.getName())) {
                        mDelayedContactChanges.add(new StoredContactChange(type, list, contact));
                    }
                }
                break;

            default:
                Log.e(TAG, "Unknown list update event!");
                break;
            }

            final ContactListAdapter listAdapter;
            if (type == LIST_DELETED) {
                listAdapter = removed;
            } else {
                listAdapter = (list == null) ? null
                        : getContactListAdapter(list.getAddress());
            }

            notifyRemoteListeners(new ListenerInvocation<IContactListListener>() {
                public void invoke(IContactListListener remoteListener)
                        throws RemoteException {
                    remoteListener.onContactChange(type, listAdapter, contact);
                }
            });

            if (mAllContactsLoaded && notificationText != null) {
                mContext.showToast(notificationText, Toast.LENGTH_SHORT);
            }
        }

        public void onContactError(final int errorType, final ImErrorInfo error,
                final String listName, final Contact contact) {
            notifyRemoteListeners(new ListenerInvocation<IContactListListener>() {
                public void invoke(IContactListListener remoteListener)
                        throws RemoteException {
                    remoteListener.onContactError(errorType, error, listName,
                            contact);
                }
            });
        }

        public void handleDelayedContactChanges() {
            for (StoredContactChange change : mDelayedContactChanges) {
                onContactChange(change.mType, change.mList, change.mContact);
            }
        }

        public void onAllContactListsLoaded() {
            mAllContactsLoaded = true;
            handleDelayedContactChanges();
            removeObsoleteContactsAndLists();
            notifyRemoteListeners(new ListenerInvocation<IContactListListener>() {
                public void invoke(IContactListListener remoteListener)
                        throws RemoteException {
                    remoteListener.onAllContactListsLoaded();
                }
            });
        }
    }

    final class SubscriptionRequestListenerAdapter
        extends RemoteListenerManager<ISubscriptionListener>
        implements SubscriptionRequestListener {

        public void onSubScriptionRequest(final Contact from) {
            String username = from.getAddress().getFullName();
            String nickname = from.getName();
            Uri uri = insertOrUpdateSubscription(username, nickname,
                    Im.Contacts.SUBSCRIPTION_TYPE_FROM,
                    Im.Contacts.SUBSCRIPTION_STATUS_SUBSCRIBE_PENDING);
            mContext.getStatusBarNotifier().notifySubscriptionRequest(mProviderId, mAccountId,
                    ContentUris.parseId(uri), username, nickname);

            notifyRemoteListeners(new ListenerInvocation<ISubscriptionListener>() {
                public void invoke(ISubscriptionListener remoteListener)
                        throws RemoteException {
                    remoteListener.onSubScriptionRequest(from);
                }
            });
        }

        public void onSubscriptionApproved(final String contact) {
            insertOrUpdateSubscription(contact, null,
                    Im.Contacts.SUBSCRIPTION_TYPE_NONE,
                    Im.Contacts.SUBSCRIPTION_STATUS_NONE);

            notifyRemoteListeners(new ListenerInvocation<ISubscriptionListener>() {
                public void invoke(ISubscriptionListener remoteListener)
                        throws RemoteException {
                    remoteListener.onSubscriptionApproved(contact);
                }
            });
        }

        public void onSubscriptionDeclined(final String contact) {
            insertOrUpdateSubscription(contact, null,
                    Im.Contacts.SUBSCRIPTION_TYPE_NONE,
                    Im.Contacts.SUBSCRIPTION_STATUS_NONE);

            notifyRemoteListeners(new ListenerInvocation<ISubscriptionListener>() {
                public void invoke(ISubscriptionListener remoteListener)
                        throws RemoteException {
                    remoteListener.onSubscriptionDeclined(contact);
                }
            });
        }

        public void onApproveSubScriptionError(final String contact, final ImErrorInfo error) {
            String displayableAddress = getDisplayableAddress(contact);
            String msg = mContext.getString(R.string.approve_subscription_error, displayableAddress);
            mContext.showToast(msg, Toast.LENGTH_SHORT);
        }

        public void onDeclineSubScriptionError(final String contact, final ImErrorInfo error) {
            String displayableAddress = getDisplayableAddress(contact);
            String msg = mContext.getString(R.string.decline_subscription_error, displayableAddress);
            mContext.showToast(msg, Toast.LENGTH_SHORT);
        }
    }

    String getDisplayableAddress(String impsAddress) {
        if (impsAddress.startsWith("wv:")) {
            return impsAddress.substring(3);
        }
        return impsAddress;
    }

    void insertBlockedContactToDataBase(Contact contact) {
        // Remove the blocked contact if it already exists, to avoid duplicates and
        // handle the odd case where a blocked contact's nickname has changed
        removeBlockedContactFromDataBase(contact);

        Uri.Builder builder = Im.BlockedList.CONTENT_URI.buildUpon();
        ContentUris.appendId(builder, mProviderId);
        ContentUris.appendId(builder, mAccountId);
        Uri uri = builder.build();

        String username = contact.getAddress().getFullName();
        ContentValues values = new ContentValues(2);
        values.put(Im.BlockedList.USERNAME, username);
        values.put(Im.BlockedList.NICKNAME, contact.getName());

        mResolver.insert(uri, values);

        mValidatedBlockedContacts.add(username);
    }

    void removeBlockedContactFromDataBase(Contact contact) {
        String address = contact.getAddress().getFullName();

        Uri.Builder builder = Im.BlockedList.CONTENT_URI.buildUpon();
        ContentUris.appendId(builder, mProviderId);
        ContentUris.appendId(builder, mAccountId);

        Uri uri = builder.build();
        mResolver.delete(uri, Im.BlockedList.USERNAME + "=?", new String[]{ address });

        int type = isTemporary(address) ? Im.Contacts.TYPE_TEMPORARY
                : Im.Contacts.TYPE_NORMAL;
        updateContactType(address, type);
    }

    void moveTemporaryContactToList(String address, long listId) {
        synchronized (mTemporaryContacts) {
            mTemporaryContacts.remove(address);
        }
        ContentValues values = new ContentValues(2);
        values.put(Im.Contacts.TYPE, Im.Contacts.TYPE_NORMAL);
        values.put(Im.Contacts.CONTACTLIST, listId);

        String selection = Im.Contacts.USERNAME + "=? AND " + Im.Contacts.TYPE + "="
                + Im.Contacts.TYPE_TEMPORARY;
        String[] selectionArgs = { address };

        mResolver.update(mContactUrl, values, selection, selectionArgs);
    }

    void updateContactType(String address, int type) {
        ContentValues values = new ContentValues(1);
        values.put(Im.Contacts.TYPE, type);
        updateContact(address, values);
    }

    /**
     * Insert or update subscription request from user into the database.
     *
     * @param username
     * @param nickname
     * @param subscriptionType
     * @param subscriptionStatus
     */
    Uri insertOrUpdateSubscription(String username, String nickname, int subscriptionType,
            int subscriptionStatus) {
        Cursor cursor = mResolver.query(mContactUrl, new String[]{ Im.Contacts._ID },
                Im.Contacts.USERNAME + "=?", new String[]{username}, null);
        if (cursor == null) {
            Log.w(TAG, "query contact " + username + " failed");
            return null;
        }

        Uri uri;
        if (cursor.moveToFirst()) {
            ContentValues values = new ContentValues(2);
            values.put(Im.Contacts.SUBSCRIPTION_TYPE, subscriptionType);
            values.put(Im.Contacts.SUBSCRIPTION_STATUS, subscriptionStatus);

            long contactId = cursor.getLong(0);
            uri = ContentUris.withAppendedId(Im.Contacts.CONTENT_URI, contactId);
            mResolver.update(uri, values, null, null);
        } else {
            ContentValues values = new ContentValues(6);
            values.put(Im.Contacts.USERNAME, username);
            values.put(Im.Contacts.NICKNAME, nickname);
            values.put(Im.Contacts.TYPE, Im.Contacts.TYPE_NORMAL);
            values.put(Im.Contacts.CONTACTLIST, FAKE_TEMPORARY_LIST_ID);
            values.put(Im.Contacts.SUBSCRIPTION_TYPE, subscriptionType);
            values.put(Im.Contacts.SUBSCRIPTION_STATUS, subscriptionStatus);

            uri = mResolver.insert(mContactUrl, values);
        }
        cursor.close();
        return uri;
    }

    void updateContact(String username, ContentValues values) {
        String selection = Im.Contacts.USERNAME + "=?";
        String[] selectionArgs = { username };
        mResolver.update(mContactUrl, values, selection, selectionArgs);
    }

    void updatePresenceContent(Contact[] contacts) {
        ArrayList<String> usernames = new ArrayList<String>();
        ArrayList<String> statusArray = new ArrayList<String>();
        ArrayList<String> customStatusArray = new ArrayList<String>();
        ArrayList<String> clientTypeArray = new ArrayList<String>();

        for(Contact c : contacts) {
            String username = c.getAddress().getFullName();
            Presence p = c.getPresence();
            int status = getContactStatus(p);
            String customStatus = p.getStatusText();
            int clientType = translateClientType(p);

            usernames.add(username);
            statusArray.add(String.valueOf(status));
            customStatusArray.add(customStatus);
            clientTypeArray.add(String.valueOf(clientType));
        }

        ContentValues values = new ContentValues();
        values.put(Im.Contacts.ACCOUNT, mAccountId);
        values.putStringArrayList(Im.Contacts.USERNAME, usernames);
        values.putStringArrayList(Im.Presence.PRESENCE_STATUS, statusArray);
        values.putStringArrayList(Im.Presence.PRESENCE_CUSTOM_STATUS, customStatusArray);
        values.putStringArrayList(Im.Presence.CONTENT_TYPE, clientTypeArray);

        mResolver.update(Im.Presence.BULK_CONTENT_URI, values, null, null);
    }

    void updateAvatarsContent(Contact[] contacts) {
        ArrayList<ContentValues> avatars = new ArrayList<ContentValues>();
        ArrayList<String> usernames = new ArrayList<String>();

        for (Contact contact : contacts) {
            byte[] avatarData = contact.getPresence().getAvatarData();
            if (avatarData == null) {
                continue;
            }

            String username = contact.getAddress().getFullName();

            ContentValues values = new ContentValues(2);
            values.put(Im.Avatars.CONTACT, username);
            values.put(Im.Avatars.DATA, avatarData);
            avatars.add(values);
            usernames.add(username);
        }
        if (avatars.size() > 0) {
            // ImProvider will replace the avatar content if it already exist.
            mResolver.bulkInsert(mAvatarUrl, avatars.toArray(
                    new ContentValues[avatars.size()]));

            // notify avatar changed
            Intent i = new Intent(ImServiceConstants.ACTION_AVATAR_CHANGED);
            i.putExtra(ImServiceConstants.EXTRA_INTENT_FROM_ADDRESS, usernames);
            i.putExtra(ImServiceConstants.EXTRA_INTENT_PROVIDER_ID, mProviderId);
            i.putExtra(ImServiceConstants.EXTRA_INTENT_ACCOUNT_ID, mAccountId);
            mContext.sendBroadcast(i);
        }
    }

    ContactListAdapter removeContactListFromDataBase(String name) {
        ContactListAdapter listAdapter = getContactListAdapter(name);
        if (listAdapter == null) {
            return null;
        }
        long id = listAdapter.getDataBaseId();

        // delete contacts of this list first
        mResolver.delete(mContactUrl,
            Im.Contacts.CONTACTLIST + "=?", new String[]{Long.toString(id)});

        mResolver.delete(ContentUris.withAppendedId(Im.ContactList.CONTENT_URI, id), null, null);
        synchronized (mContactLists) {
            return mContactLists.remove(listAdapter.getAddress());
        }
    }

    void addContactListContent(ContactList list) {
        String selection = Im.ContactList.NAME + "=? AND "
                + Im.ContactList.PROVIDER + "=? AND "
                + Im.ContactList.ACCOUNT + "=?";
        String[] selectionArgs = { list.getName(),
                Long.toString(mProviderId),
                Long.toString(mAccountId) };
        Cursor cursor = mResolver.query(Im.ContactList.CONTENT_URI,
                                        CONTACT_LIST_ID_PROJECTION,
                                        selection,
                                        selectionArgs,
                                        null); // no sort order
        long listId = 0;
        Uri uri = null;
        try {
            if (cursor.moveToFirst()) {
                listId = cursor.getLong(0);
                uri = ContentUris.withAppendedId(Im.ContactList.CONTENT_URI, listId);
                //Log.d(TAG,"Found and removing ContactList with name "+list.getName());
            }
        } finally {
            cursor.close();
        }
        if (uri != null) {
            // remove existing ContactList and Contacts of that list for replacement by the newly
            // downloaded list
            mResolver.delete(mContactUrl, Im.Contacts.CONTACTLIST + "=?",
                    new String[]{Long.toString(listId)});
            mResolver.delete(uri, selection, selectionArgs);
        }

        ContentValues contactListValues = new ContentValues(3);
        contactListValues.put(Im.ContactList.NAME, list.getName());
        contactListValues.put(Im.ContactList.PROVIDER, mProviderId);
        contactListValues.put(Im.ContactList.ACCOUNT, mAccountId);

        //Log.d(TAG, "Adding ContactList name="+list.getName());
        mValidatedContactLists.add(list.getName());
        uri = mResolver.insert(Im.ContactList.CONTENT_URI, contactListValues);
        listId = ContentUris.parseId(uri);

        synchronized (mContactLists) {
            mContactLists.put(list.getAddress(),
                    new ContactListAdapter(list, listId));
        }

        Collection<Contact> contacts = list.getContacts();
        if (contacts == null || contacts.size() == 0) {
            return;
        }

        Iterator<Contact> iter = contacts.iterator();
        while(iter.hasNext()) {
            Contact c = iter.next();
            String address = c.getAddress().getFullName();
            if(isTemporary(address)) {
                moveTemporaryContactToList(address, listId);
                iter.remove();
            }
            mValidatedContacts.add(address);
        }

        ArrayList<String> usernames = new ArrayList<String>();
        ArrayList<String> nicknames = new ArrayList<String>();
        ArrayList<String> contactTypeArray = new ArrayList<String>();
        for (Contact c : contacts) {
            String username = c.getAddress().getFullName();
            String nickname = c.getName();
            int type = Im.Contacts.TYPE_NORMAL;
            if(isTemporary(username)) {
                type = Im.Contacts.TYPE_TEMPORARY;
            }
            if (isBlocked(username)) {
                type = Im.Contacts.TYPE_BLOCKED;
            }

            usernames.add(username);
            nicknames.add(nickname);
            contactTypeArray.add(String.valueOf(type));
        }
        ContentValues values = new ContentValues(6);

        values.put(Im.Contacts.PROVIDER, mProviderId);
        values.put(Im.Contacts.ACCOUNT, mAccountId);
        values.put(Im.Contacts.CONTACTLIST, listId);
        values.putStringArrayList(Im.Contacts.USERNAME, usernames);
        values.putStringArrayList(Im.Contacts.NICKNAME, nicknames);
        values.putStringArrayList(Im.Contacts.TYPE, contactTypeArray);

        mResolver.insert(Im.Contacts.BULK_CONTENT_URI, values);
    }

    void updateListNameInDataBase(ContactList list) {
        ContactListAdapter listAdapter = getContactListAdapter(list.getAddress());

        Uri uri = ContentUris.withAppendedId(Im.ContactList.CONTENT_URI, listAdapter.getDataBaseId());
        ContentValues values = new ContentValues(1);
        values.put(Im.ContactList.NAME, list.getName());

        mResolver.update(uri, values, null, null);
    }

    void deleteContactFromDataBase(Contact contact, ContactList list) {
        String selection = Im.Contacts.USERNAME
                + "=? AND " + Im.Contacts.CONTACTLIST + "=?";
        long listId = getContactListAdapter(list.getAddress()).getDataBaseId();
        String username = contact.getAddress().getFullName();
        String[] selectionArgs = {username, Long.toString(listId)};

        mResolver.delete(mContactUrl, selection, selectionArgs);

        // clear the history message if the contact doesn't exist in any list
        // anymore.
        if(mAdaptee.getContact(contact.getAddress()) == null) {
            clearHistoryMessages(username);
        }
    }

    Uri insertContactContent(Contact contact, long listId) {
        ContentValues values = getContactContentValues(contact, listId);

        Uri uri = mResolver.insert(mContactUrl, values);

        ContentValues presenceValues = getPresenceValues(ContentUris.parseId(uri),
                contact.getPresence());

        mResolver.insert(Im.Presence.CONTENT_URI, presenceValues);

        return uri;
    }

    private ContentValues getContactContentValues(Contact contact, long listId) {
        final String username = contact.getAddress().getFullName();
        final String nickname = contact.getName();
        int type = Im.Contacts.TYPE_NORMAL;
        if(isTemporary(username)) {
            type = Im.Contacts.TYPE_TEMPORARY;
        }
        if (isBlocked(username)) {
            type = Im.Contacts.TYPE_BLOCKED;
        }

        ContentValues values = new ContentValues(4);
        values.put(Im.Contacts.USERNAME, username);
        values.put(Im.Contacts.NICKNAME, nickname);
        values.put(Im.Contacts.CONTACTLIST, listId);
        values.put(Im.Contacts.TYPE, type);
        return values;
    }

    void clearHistoryMessages(String contact) {
        Uri uri = Im.Messages.getContentUriByContact(mProviderId,
            mAccountId, contact);
        mResolver.delete(uri, null, null);
    }

    private ContentValues getPresenceValues(long contactId, Presence p) {
        ContentValues values = new ContentValues(3);
        values.put(Im.Presence.CONTACT_ID, contactId);
        values.put(Im.Contacts.PRESENCE_STATUS, getContactStatus(p));
        values.put(Im.Contacts.PRESENCE_CUSTOM_STATUS, p.getStatusText());
        values.put(Im.Presence.CLIENT_TYPE, translateClientType(p));
        return values;
    }

    private int translateClientType(Presence presence) {
        int clientType = presence.getClientType();
        switch (clientType) {
            case Presence.CLIENT_TYPE_MOBILE:
                return Im.Presence.CLIENT_TYPE_MOBILE;
            default:
                return Im.Presence.CLIENT_TYPE_DEFAULT;
        }
    }

    private int getContactStatus(Presence presence) {
        switch (presence.getStatus()) {
        case Presence.AVAILABLE:
            return Im.Presence.AVAILABLE;

        case Presence.IDLE:
            return Im.Presence.IDLE;

        case Presence.AWAY:
            return Im.Presence.AWAY;

        case Presence.DO_NOT_DISTURB:
            return Im.Presence.DO_NOT_DISTURB;

        case Presence.OFFLINE:
            return Im.Presence.OFFLINE;
        }

        // impossible...
        Log.e(TAG, "Illegal presence status value " + presence.getStatus());
        return Im.Presence.AVAILABLE;
    }

    public void clearOnLogout() {
        clearValidatedContactsAndLists();
        clearTemporaryContacts();
        clearPresence();
    }

    /**
     * Clears the list of validated contacts and contact lists.
     * As contacts and contacts lists are added after login, contacts and contact lists are
     * stored as "validated contacts". After initial download of contacts is complete, any contacts
     * and contact lists that remain in the database, but are not in the validated list, are
     * obsolete and should be removed.  This function resets that list for use upon login.
     */
    private void clearValidatedContactsAndLists() {
        // clear the list of validated contacts, contact lists, and blocked contacts
        mValidatedContacts.clear();
        mValidatedContactLists.clear();
        mValidatedBlockedContacts.clear();
    }

    /**
     * Clear the temporary contacts in the database. As contacts are persist between
     * IM sessions, the temporary contacts need to be cleared after logout.
     */
    private void clearTemporaryContacts() {
        String selection = Im.Contacts.CONTACTLIST + "=" + FAKE_TEMPORARY_LIST_ID;
        mResolver.delete(mContactUrl, selection, null);
    }

    /**
     * Clears the presence of the all contacts. As contacts are persist between
     * IM sessions, the presence need to be cleared after logout.
     */
    void clearPresence() {
        StringBuilder where = new StringBuilder();
        where.append(Im.Presence.CONTACT_ID);
        where.append(" in (select _id from contacts where ");
        where.append(Im.Contacts.ACCOUNT);
        where.append("=");
        where.append(mAccountId);
        where.append(")");
        mResolver.delete(Im.Presence.CONTENT_URI, where.toString(), null);
    }

    void closeChatSession(String address) {
        ChatSessionManagerAdapter sessionManager =
            (ChatSessionManagerAdapter) mConn.getChatSessionManager();
        ChatSessionAdapter session =
            (ChatSessionAdapter) sessionManager.getChatSession(address);
        if(session != null) {
            session.leave();
        }
    }

    void updateChatPresence(String address, String nickname, Presence p) {
        ChatSessionManagerAdapter sessionManager =
            (ChatSessionManagerAdapter) mConn.getChatSessionManager();
        // TODO: This only find single chat sessions, we need to go through all
        // active chat sessions and find if the contact is a participant of the
        // session.
        ChatSessionAdapter session =
            (ChatSessionAdapter) sessionManager.getChatSession(address);
        if(session != null) {
            session.insertPresenceUpdatesMsg(nickname, p);
        }
    }


}
