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
import java.util.List;

import android.util.Log;

import com.android.im.IContactList;
import com.android.im.engine.Address;
import com.android.im.engine.Contact;
import com.android.im.engine.ContactList;
import com.android.im.engine.ImErrorInfo;
import com.android.im.engine.ImException;

public class ContactListAdapter extends IContactList.Stub {
    private ContactList mAdaptee;
    private long mDataBaseId;

    public ContactListAdapter(ContactList adaptee, long dataBaseId) {
        mAdaptee = adaptee;
        mDataBaseId = dataBaseId;
    }

    public long getDataBaseId() {
        return mDataBaseId;
    }

    public Address getAddress() {
        return mAdaptee.getAddress();
    }

    public int addContact(String address) {
        if (address == null) {
            Log.e(RemoteImService.TAG, "Address can't be null!");
            return ImErrorInfo.ILLEGAL_CONTACT_ADDRESS;
        }

        try {
            mAdaptee.addContact(address);
        } catch (IllegalArgumentException e) {
            return ImErrorInfo.ILLEGAL_CONTACT_ADDRESS;
        } catch (ImException e) {
            return e.getImError().getCode();
        }

        return ImErrorInfo.NO_ERROR;
    }

    public List<Contact> getContacts() {
        ArrayList<Contact> result = new ArrayList<Contact>();
        result.addAll(mAdaptee.getContacts());
        return result;
    }

    public String getName() {
        return mAdaptee.getName();
    }

    public int removeContact(Contact contact) {
        if (contact == null) {
            Log.e(RemoteImService.TAG, "Contact can't be null!");
            return ImErrorInfo.ILLEGAL_CONTACT_ADDRESS;
        }

        try {
            mAdaptee.removeContact(contact);
        } catch (ImException e) {
            return e.getImError().getCode();
        }

        return ImErrorInfo.NO_ERROR;
    }

    public void setDefault(boolean isDefault) {
        mAdaptee.setDefault(isDefault);
    }

    public boolean isDefault() {
        return mAdaptee.isDefault();
    }

    public void setName(String name) {
        if (name == null) {
            Log.e(RemoteImService.TAG, "Name can't be null!");
            return;
        }

        mAdaptee.setName(name);
    }
}
