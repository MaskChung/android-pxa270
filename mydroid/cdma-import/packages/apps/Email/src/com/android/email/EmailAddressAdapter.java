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

package com.android.email;

import static android.provider.Contacts.ContactMethods.CONTENT_EMAIL_URI;
import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.provider.Contacts.ContactMethods;
import android.provider.Contacts.People;
import android.view.View;
import android.widget.ResourceCursorAdapter;
import android.widget.TextView;

import com.android.email.mail.Address;

public class EmailAddressAdapter extends ResourceCursorAdapter {
    public static final int NAME_INDEX = 1;

    public static final int DATA_INDEX = 2;

    private static final String SORT_ORDER = People.TIMES_CONTACTED + " DESC, " + People.NAME;

    private ContentResolver mContentResolver;

    private static final String[] PROJECTION = {
            ContactMethods._ID, // 0
            ContactMethods.NAME, // 1
            ContactMethods.DATA
    // 2
    };

    public EmailAddressAdapter(Context context) {
        super(context, R.layout.recipient_dropdown_item, null);
        mContentResolver = context.getContentResolver();
    }

    @Override
    public final String convertToString(Cursor cursor) {
        String name = cursor.getString(NAME_INDEX);
        String address = cursor.getString(DATA_INDEX);

        return new Address(address, name).toString();
    }

    @Override
    public final void bindView(View view, Context context, Cursor cursor) {
        TextView text1 = (TextView)view.findViewById(R.id.text1);
        TextView text2 = (TextView)view.findViewById(R.id.text2);
        text1.setText(cursor.getString(NAME_INDEX));
        text2.setText(cursor.getString(DATA_INDEX));
    }

    @Override
    public Cursor runQueryOnBackgroundThread(CharSequence constraint) {
        String where = null;

        if (constraint != null) {
            String filter = DatabaseUtils.sqlEscapeString(constraint.toString() + '%');

            StringBuilder s = new StringBuilder();
            s.append("(people.name LIKE ");
            s.append(filter);
            s.append(") OR (contact_methods.data LIKE ");
            s.append(filter);
            s.append(")");

            where = s.toString();
        }

        return mContentResolver.query(CONTENT_EMAIL_URI, PROJECTION, where, null, SORT_ORDER);
    }
}
