/*
 * Copyright (C) 2008 The Android Open Source Project
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

package com.android.providers.telephony;

import com.google.android.mms.pdu.PduHeaders;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.content.UriMatcher;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.provider.BaseColumns;
import android.provider.Telephony.CanonicalAddressesColumns;
import android.provider.Telephony.Mms;
import android.provider.Telephony.MmsSms;
import android.provider.Telephony.Sms;
import android.provider.Telephony.Threads;
import android.provider.Telephony.ThreadsColumns;
import android.provider.Telephony.MmsSms.PendingMessages;
import android.provider.Telephony.Sms.Conversations;
import android.text.TextUtils;
import android.util.Log;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * This class provides the ability to query the MMS and SMS databases
 * at the same time, mixing messages from both in a single thread
 * (A.K.A. conversation).
 *
 * A virtual column, MmsSms.TYPE_DISCRIMINATOR_COLUMN, may be
 * requested in the projection for a query.  Its value is either "mms"
 * or "sms", depending on whether the message represented by the row
 * is an MMS message or an SMS message, respectively.
 *
 * This class also provides the ability to find out what addresses
 * participated in a particular thread.  It doesn't support updates
 * for either of these.
 *
 * This class provides a way to allocate and retrieve thread IDs.
 * This is done atomically through a query.  There is no insert URI
 * for this.
 *
 * Finally, this class provides a way to delete or update all messages
 * in a thread.
 */
public class MmsSmsProvider extends ContentProvider {
    private static final UriMatcher URI_MATCHER =
            new UriMatcher(UriMatcher.NO_MATCH);
    private static final String LOG_TAG = "MmsSmsProvider";

    private static final String NO_DELETES_INSERTS_OR_UPDATES =
            "MmsSmsProvider does not support deletes, inserts, or updates for this URI.";
    private static final int URI_CONVERSATIONS              = 0;
    private static final int URI_CONVERSATIONS_MESSAGES     = 1;
    private static final int URI_CONVERSATIONS_RECIPIENTS   = 2;
    private static final int URI_MESSAGES_BY_PHONE          = 3;
    private static final int URI_THREAD_ID                  = 4;
    private static final int URI_CANONICAL_ADDRESS          = 5;
    private static final int URI_PENDING_MSG                = 6;
    private static final int URI_COMPLETE_CONVERSATIONS     = 7;
    private static final int URI_UNDELIVERED_MSG            = 8;
    private static final int URI_CONVERSATIONS_SUBJECT      = 9;
    private static final int URI_NOTIFICATIONS              = 10;
    private static final int URI_OBSOLETE_THREADS           = 11;
    private static final int URI_DRAFT                      = 12;

    /**
     * the name of the table that is used to store the queue of
     * messages(both MMS and SMS) to be sent/downloaded.
     */
    public static final String TABLE_PENDING_MSG = "pending_msgs";

    // These constants are used to construct union queries across the
    // MMS and SMS base tables.

    // These are the columns that appear in both the MMS ("pdu") and
    // SMS ("sms") message tables.
    private static final String[] MMS_SMS_COLUMNS =
            { BaseColumns._ID, Mms.DATE, Mms.READ, Mms.THREAD_ID };

    // These are the columns that appear only in the MMS message
    // table.
    private static final String[] MMS_ONLY_COLUMNS = {
        Mms.CONTENT_CLASS, Mms.CONTENT_LOCATION, Mms.CONTENT_TYPE,
        Mms.DELIVERY_REPORT, Mms.EXPIRY, Mms.MESSAGE_CLASS, Mms.MESSAGE_ID,
        Mms.MESSAGE_SIZE, Mms.MESSAGE_TYPE, Mms.MESSAGE_BOX, Mms.PRIORITY,
        Mms.READ_STATUS, Mms.RESPONSE_STATUS, Mms.RESPONSE_TEXT,
        Mms.RETRIEVE_STATUS, Mms.RETRIEVE_TEXT_CHARSET, Mms.REPORT_ALLOWED,
        Mms.READ_REPORT, Mms.STATUS, Mms.SUBJECT, Mms.SUBJECT_CHARSET,
        Mms.TRANSACTION_ID, Mms.MMS_VERSION };

    // These are the columns that appear only in the SMS message
    // table.
    private static final String[] SMS_ONLY_COLUMNS =
            { "address", "body", "person", "reply_path_present",
              "service_center", "status", "subject", "type" };

    // These are all the columns that appear in the "threads" table.
    private static final String[] THREADS_COLUMNS = {
        BaseColumns._ID,
        ThreadsColumns.DATE,
        ThreadsColumns.RECIPIENT_IDS,
        ThreadsColumns.MESSAGE_COUNT
    };

    // These are all the columns that appear in the MMS and SMS
    // message tables.
    private static final String[] UNION_COLUMNS =
            new String[MMS_SMS_COLUMNS.length
                       + MMS_ONLY_COLUMNS.length
                       + SMS_ONLY_COLUMNS.length];

    // These are all the columns that appear in the MMS table.
    private static final Set<String> MMS_COLUMNS = new HashSet<String>();

    // These are all the columns that appear in the SMS table.
    private static final Set<String> SMS_COLUMNS = new HashSet<String>();

    private static final String VND_ANDROID_DIR_MMS_SMS =
            "vnd.android-dir/mms-sms";

    private static final String[] ID_PROJECTION = { BaseColumns._ID };

    private static final String[] EMPTY_STRING_ARRAY = new String[0];

    private static final String SMS_CONVERSATION_CONSTRAINT = "(" +
            Sms.TYPE + " != " + Sms.MESSAGE_TYPE_DRAFT + ")";

    private static final String MMS_CONVERSATION_CONSTRAINT = "(" +
            Mms.MESSAGE_BOX + " != " + Mms.MESSAGE_BOX_DRAFTS + " AND (" +
            Mms.MESSAGE_TYPE + " = " + PduHeaders.MESSAGE_TYPE_SEND_REQ + " OR " +
            Mms.MESSAGE_TYPE + " = " + PduHeaders.MESSAGE_TYPE_RETRIEVE_CONF + " OR " +
            Mms.MESSAGE_TYPE + " = " + PduHeaders.MESSAGE_TYPE_NOTIFICATION_IND + "))";

    private static final String AUTHORITY = "mms-sms";

    static {
        URI_MATCHER.addURI(AUTHORITY, "conversations", URI_CONVERSATIONS);
        URI_MATCHER.addURI(AUTHORITY, "complete-conversations", URI_COMPLETE_CONVERSATIONS);

        // In these patterns, "#" is the thread ID.
        URI_MATCHER.addURI(
                AUTHORITY, "conversations/#", URI_CONVERSATIONS_MESSAGES);
        URI_MATCHER.addURI(
                AUTHORITY, "conversations/#/recipients",
                URI_CONVERSATIONS_RECIPIENTS);

        URI_MATCHER.addURI(
                AUTHORITY, "conversations/#/subject",
                URI_CONVERSATIONS_SUBJECT);

        // URI for deleting obsolete threads.
        URI_MATCHER.addURI(AUTHORITY, "conversations/obsolete", URI_OBSOLETE_THREADS);

        URI_MATCHER.addURI(
                AUTHORITY, "messages/byphone/*",
                URI_MESSAGES_BY_PHONE);

        // In this pattern, two query parameter names are expected:
        // "subject" and "recipient."  Multiple "recipient" parameters
        // may be present.
        URI_MATCHER.addURI(AUTHORITY, "threadID", URI_THREAD_ID);

        // Use this pattern to query the canonical address by given ID.
        URI_MATCHER.addURI(AUTHORITY, "canonical-address/#", URI_CANONICAL_ADDRESS);

        // In this pattern, two query parameters may be supplied:
        // "protocol" and "message." For example:
        //   content://mms-sms/pending?
        //       -> Return all pending messages;
        //   content://mms-sms/pending?protocol=sms
        //       -> Only return pending SMs;
        //   content://mms-sms/pending?protocol=mms&message=1
        //       -> Return the the pending MM which ID equals '1'.
        //
        URI_MATCHER.addURI(AUTHORITY, "pending", URI_PENDING_MSG);

        // Use this pattern to get a list of undelivered messages.
        URI_MATCHER.addURI(AUTHORITY, "undelivered", URI_UNDELIVERED_MSG);

        // Use this pattern to see what delivery status reports (for
        // both MMS and SMS) have not been delivered to the user.
        URI_MATCHER.addURI(AUTHORITY, "notifications", URI_NOTIFICATIONS);

        URI_MATCHER.addURI(AUTHORITY, "draft", URI_DRAFT);
        initializeColumnSets();
    }

    private SQLiteOpenHelper mOpenHelper;

    @Override
    public boolean onCreate() {
        mOpenHelper = MmsSmsDatabaseHelper.getInstance(getContext());
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection,
            String selection, String[] selectionArgs, String sortOrder) {
        SQLiteDatabase db = mOpenHelper.getReadableDatabase();
        Cursor cursor = null;

        switch(URI_MATCHER.match(uri)) {
            case URI_COMPLETE_CONVERSATIONS:
                cursor = getCompleteConversations(
                        projection, selection, selectionArgs, sortOrder);
                break;
            case URI_CONVERSATIONS:
                String simple = uri.getQueryParameter("simple");
                if ((simple != null) && simple.equals("true")) {
                    String threadType = uri.getQueryParameter("thread_type");
                    if (!TextUtils.isEmpty(threadType)) {
                        selection = concatSelections(
                                selection, Threads.TYPE + "=" + threadType);
                    }
                    cursor = getSimpleConversations(
                            projection, selection, selectionArgs, sortOrder);
                } else {
                    cursor = getConversations(
                            projection, selection, selectionArgs, sortOrder);
                }
                break;
            case URI_CONVERSATIONS_MESSAGES:
                cursor = getConversationMessages(
                        uri.getPathSegments().get(1), projection, selection,
                        selectionArgs, sortOrder);
                break;
            case URI_CONVERSATIONS_RECIPIENTS:
                cursor = getConversationById(
                        uri.getPathSegments().get(1), projection, selection,
                        selectionArgs, sortOrder);
                break;
            case URI_CONVERSATIONS_SUBJECT:
                cursor = getConversationById(
                        uri.getPathSegments().get(1), projection, selection,
                        selectionArgs, sortOrder);
                break;
            case URI_MESSAGES_BY_PHONE:
                cursor = getMessagesByPhoneNumber(
                        uri.getPathSegments().get(2), projection, selection,
                        selectionArgs, sortOrder);
                break;
            case URI_THREAD_ID:
                List<String> recipients = uri.getQueryParameters("recipient");

                cursor = getThreadId(recipients);
                break;
            case URI_CANONICAL_ADDRESS: {
                String extraSelection = "_id=" + uri.getPathSegments().get(1);
                String finalSelection = TextUtils.isEmpty(selection)
                        ? extraSelection : extraSelection + " AND " + selection;
                cursor = db.query("canonical_addresses",
                        new String[] {"address"}, finalSelection, selectionArgs,
                        null, null, sortOrder);
                break;
            }
            case URI_PENDING_MSG: {
                String protoName = uri.getQueryParameter("protocol");
                String msgId = uri.getQueryParameter("message");
                int proto = TextUtils.isEmpty(protoName) ? -1
                        : (protoName.equals("sms") ? MmsSms.SMS_PROTO : MmsSms.MMS_PROTO);

                String extraSelection = (proto != -1) ?
                        (PendingMessages.PROTO_TYPE + "=" + proto) : " 0=0 ";
                if (!TextUtils.isEmpty(msgId)) {
                    extraSelection += " AND " + PendingMessages.MSG_ID + "=" + msgId;
                }

                String finalSelection = TextUtils.isEmpty(selection)
                        ? extraSelection : ("(" + extraSelection + ") AND " + selection);
                String finalOrder = TextUtils.isEmpty(sortOrder)
                        ? PendingMessages.DUE_TIME : sortOrder;
                cursor = db.query(TABLE_PENDING_MSG, null,
                        finalSelection, selectionArgs, null, null, finalOrder);
                break;
            }
            case URI_UNDELIVERED_MSG: {
                cursor = getUndeliveredMessages(projection, selection,
                        selectionArgs, sortOrder);
                break;
            }
            case URI_DRAFT: {
                cursor = getDraftThread(projection, selection, selectionArgs, sortOrder);
                break;
            }
            default:
                throw new IllegalStateException("Unrecognized URI:" + uri);
        }

        cursor.setNotificationUri(getContext().getContentResolver(), MmsSms.CONTENT_URI);
        return cursor;
    }

    /**
     * Return the canonical address ID for this address.
     */
    private long getSingleAddressId(String address) {
        boolean isEmail = Mms.isEmailAddress(address);
        String refinedAddress = isEmail ? address.toLowerCase() : address;
        String selection =
                isEmail
                ? "address = ?"
                : "PHONE_NUMBERS_EQUAL(address, ?)";
        String[] selectionArgs = new String[] { refinedAddress };
        Cursor cursor = null;

        try {
            SQLiteDatabase db = mOpenHelper.getReadableDatabase();
            cursor = db.query(
                    "canonical_addresses", ID_PROJECTION,
                    selection, selectionArgs, null, null, null);

            if (cursor.getCount() == 0) {
                ContentValues contentValues = new ContentValues(1);
                contentValues.put(CanonicalAddressesColumns.ADDRESS, refinedAddress);

                db = mOpenHelper.getWritableDatabase();
                return db.insert("canonical_addresses",
                        CanonicalAddressesColumns.ADDRESS, contentValues);
            }

            if (cursor.moveToFirst()) {
                return cursor.getLong(cursor.getColumnIndexOrThrow(BaseColumns._ID));
            }
        } finally {
            if (cursor != null) {
                cursor.close();
            }
        }

        return -1L;
    }

    /**
     * Return the canonical address IDs for these addresses.
     */
    private Set<Long> getAddressIds(List<String> addresses) {
        Set<Long> result = new HashSet<Long>(addresses.size());

        for (String address : addresses) {
            if (!address.equals(PduHeaders.FROM_INSERT_ADDRESS_TOKEN_STR)) {
                long id = getSingleAddressId(address);
                if (id != -1L) {
                    result.add(id);
                } else {
                    Log.e(LOG_TAG, "Address ID not found for: " + address);
                }
            }
        }
        return result;
    }

    /**
     * Return a sorted array of the given Set of Longs.
     */
    private long[] getSortedSet(Set<Long> numbers) {
        int size = numbers.size();
        long[] result = new long[size];
        int i = 0;

        for (Long number : numbers) {
            result[i++] = number;
        }
        Arrays.sort(result);
        return result;
    }

    /**
     * Return a String of the numbers in the given array, in order,
     * separated by spaces.
     */
    private String getSpaceSeparatedNumbers(long[] numbers) {
        int size = numbers.length;
        StringBuilder buffer = new StringBuilder();

        for (int i = 0; i < size; i++) {
            if (i != 0) {
                buffer.append(' ');
            }
            buffer.append(numbers[i]);
        }
        return buffer.toString();
    }

    /**
     * Insert a record for a new thread.
     */
    private void insertThread(String recipientIds, int numberOfRecipients) {
        ContentValues values = new ContentValues(4);

        long date = System.currentTimeMillis();
        values.put(ThreadsColumns.DATE, date - date % 1000);
        values.put(ThreadsColumns.RECIPIENT_IDS, recipientIds);
        if (numberOfRecipients > 1) {
            values.put(Threads.TYPE, Threads.BROADCAST_THREAD);
        }
        values.put(ThreadsColumns.MESSAGE_COUNT, 0);

        mOpenHelper.getWritableDatabase().insert("threads", null, values);
        getContext().getContentResolver().notifyChange(MmsSms.CONTENT_URI, null);
    }

    /**
     * Return the thread ID for this list of
     * recipients IDs.  If no thread exists with this ID, create
     * one and return it.  Callers should always use
     * Threads.getThreadId to access this information.
     */
    private synchronized Cursor getThreadId(List<String> recipients) {
        String recipientIds =
                getSpaceSeparatedNumbers(
                        getSortedSet(getAddressIds(recipients)));
        String THREAD_QUERY = "SELECT _id FROM threads " +
                "WHERE recipient_ids = ?";

        SQLiteDatabase db = mOpenHelper.getReadableDatabase();
        Cursor cursor = db.rawQuery(THREAD_QUERY, new String[] { recipientIds });

        if (cursor.getCount() == 0) {
            cursor.close();
            insertThread(recipientIds, recipients.size());
            db = mOpenHelper.getReadableDatabase();  // In case insertThread closed it
            cursor = db.rawQuery(THREAD_QUERY, new String[] { recipientIds });
        }

        return cursor;
    }

    private static String concatSelections(String selection1, String selection2) {
        if (TextUtils.isEmpty(selection1)) {
            return selection2;
        } else if (TextUtils.isEmpty(selection2)) {
            return selection1;
        } else {
            return selection1 + " AND " + selection2;
        }
    }

    /**
     * If a null projection is given, return the union of all columns
     * in both the MMS and SMS messages tables.  Otherwise, return the
     * given projection.
     */
    private static String[] handleNullMessageProjection(
            String[] projection) {
        return projection == null ? UNION_COLUMNS : projection;
    }

    /**
     * If a null projection is given, return the set of all columns in
     * the threads table.  Otherwise, return the given projection.
     */
    private static String[] handleNullThreadsProjection(
            String[] projection) {
        return projection == null ? THREADS_COLUMNS : projection;
    }

    /**
     * If a null sort order is given, return "normalized_date ASC".
     * Otherwise, return the given sort order.
     */
    private static String handleNullSortOrder (String sortOrder) {
        return sortOrder == null ? "normalized_date ASC" : sortOrder;
    }

    /**
     * Return existing threads in the database.
     */
    private Cursor getSimpleConversations(String[] projection, String selection,
            String[] selectionArgs, String sortOrder) {
        return mOpenHelper.getReadableDatabase().query("threads", projection,
                selection, selectionArgs, null, null, " date DESC");
    }

    /**
     * Return the thread which has draft in both MMS and SMS.
     *
     * Use this query:
     *
     *   SELECT ...
     *     FROM (SELECT _id, thread_id, ...
     *             FROM pdu
     *             WHERE msg_box = 3 AND ...
     *           UNION
     *           SELECT _id, thread_id, ...
     *             FROM sms
     *             WHERE type = 3 AND ...
     *          )
     *   ;
     */
    private Cursor getDraftThread(String[] projection, String selection,
            String[] selectionArgs, String sortOrder) {
        String[] innerProjection = new String[] {BaseColumns._ID, Conversations.THREAD_ID};
        SQLiteQueryBuilder mmsQueryBuilder = new SQLiteQueryBuilder();
        SQLiteQueryBuilder smsQueryBuilder = new SQLiteQueryBuilder();

        mmsQueryBuilder.setTables(MmsProvider.TABLE_PDU);
        smsQueryBuilder.setTables(SmsProvider.TABLE_SMS);

        String mmsSubQuery = mmsQueryBuilder.buildUnionSubQuery(
                MmsSms.TYPE_DISCRIMINATOR_COLUMN, innerProjection,
                MMS_COLUMNS, 1, "mms",
                concatSelections(selection, Mms.MESSAGE_BOX + "=" + Mms.MESSAGE_BOX_DRAFTS),
                selectionArgs, null, null);
        String smsSubQuery = smsQueryBuilder.buildUnionSubQuery(
                MmsSms.TYPE_DISCRIMINATOR_COLUMN, innerProjection,
                SMS_COLUMNS, 1, "sms",
                concatSelections(selection, Sms.TYPE + "=" + Sms.MESSAGE_TYPE_DRAFT),
                selectionArgs, null, null);
        SQLiteQueryBuilder unionQueryBuilder = new SQLiteQueryBuilder();

        unionQueryBuilder.setDistinct(true);

        String unionQuery = unionQueryBuilder.buildUnionQuery(
                new String[] { mmsSubQuery, smsSubQuery }, null, null);

        SQLiteQueryBuilder outerQueryBuilder = new SQLiteQueryBuilder();

        outerQueryBuilder.setTables("(" + unionQuery + ")");

        String outerQuery = outerQueryBuilder.buildQuery(
                projection, null, null, null, null, sortOrder, null);

        return mOpenHelper.getReadableDatabase().rawQuery(outerQuery, EMPTY_STRING_ARRAY);
    }

    /**
     * Return the most recent message in each conversation in both MMS
     * and SMS.
     *
     * Use this query:
     *
     *   SELECT ...
     *     FROM (SELECT thread_id AS tid, date * 1000 AS normalized_date, ...
     *             FROM pdu
     *             WHERE msg_box != 3 AND ...
     *             GROUP BY thread_id
     *             HAVING date = MAX(date)
     *           UNION
     *           SELECT thread_id AS tid, date AS normalized_date, ...
     *             FROM sms
     *             WHERE ...
     *             GROUP BY thread_id
     *             HAVING date = MAX(date))
     *     GROUP BY tid
     *     HAVING normalized_date = MAX(normalized_date);
     *
     * The msg_box != 3 comparisons ensure that we don't include draft
     * messages.
     */
    private Cursor getConversations(String[] projection, String selection,
            String[] selectionArgs, String sortOrder) {
        SQLiteQueryBuilder mmsQueryBuilder = new SQLiteQueryBuilder();
        SQLiteQueryBuilder smsQueryBuilder = new SQLiteQueryBuilder();

        mmsQueryBuilder.setTables(MmsProvider.TABLE_PDU);
        smsQueryBuilder.setTables(SmsProvider.TABLE_SMS);

        String[] columns = handleNullMessageProjection(projection);
        String[] innerMmsProjection = makeProjectionWithDateAndThreadId(
                UNION_COLUMNS, 1000);
        String[] innerSmsProjection = makeProjectionWithDateAndThreadId(
                UNION_COLUMNS, 1);
        String mmsSubQuery = mmsQueryBuilder.buildUnionSubQuery(
                MmsSms.TYPE_DISCRIMINATOR_COLUMN, innerMmsProjection,
                MMS_COLUMNS, 1, "mms",
                concatSelections(selection, MMS_CONVERSATION_CONSTRAINT), selectionArgs,
                "thread_id", "date = MAX(date)");
        String smsSubQuery = smsQueryBuilder.buildUnionSubQuery(
                MmsSms.TYPE_DISCRIMINATOR_COLUMN, innerSmsProjection,
                SMS_COLUMNS, 1, "sms",
                concatSelections(selection, SMS_CONVERSATION_CONSTRAINT), selectionArgs,
                "thread_id", "date = MAX(date)");
        SQLiteQueryBuilder unionQueryBuilder = new SQLiteQueryBuilder();

        unionQueryBuilder.setDistinct(true);

        String unionQuery = unionQueryBuilder.buildUnionQuery(
                new String[] { mmsSubQuery, smsSubQuery }, null, null);

        SQLiteQueryBuilder outerQueryBuilder = new SQLiteQueryBuilder();

        outerQueryBuilder.setTables("(" + unionQuery + ")");

        String outerQuery = outerQueryBuilder.buildQuery(
                columns, null, null, "tid",
                "normalized_date = MAX(normalized_date)", sortOrder, null);

        return mOpenHelper.getReadableDatabase().rawQuery(outerQuery, EMPTY_STRING_ARRAY);
    }

    /**
     * Return every message in each conversation in both MMS
     * and SMS.
     */
    private Cursor getCompleteConversations(String[] projection,
            String selection, String[] selectionArgs, String sortOrder) {
        String unionQuery = buildConversationQuery(
                projection, selection, selectionArgs, sortOrder);

        return mOpenHelper.getReadableDatabase().rawQuery(unionQuery, EMPTY_STRING_ARRAY);
    }

    /**
     * Add normalized date and thread_id to the list of columns for an
     * inner projection.  This is necessary so that the outer query
     * can have access to these columns even if the caller hasn't
     * requested them in the result.
     */
    private String[] makeProjectionWithDateAndThreadId(
            String[] projection, int dateMultiple) {
        int projectionSize = projection.length;
        String[] result = new String[projectionSize + 2];

        result[0] = "thread_id AS tid";
        result[1] = "date * " + dateMultiple + " AS normalized_date";
        for (int i = 0; i < projectionSize; i++) {
            result[i + 2] = projection[i];
        }
        return result;
    }

    /**
     * Return the union of MMS and SMS messages for this thread ID.
     */
    private Cursor getConversationMessages(
            String threadIdString, String[] projection, String selection,
            String[] selectionArgs, String sortOrder) {
        try {
            Long.parseLong(threadIdString);
        } catch (NumberFormatException exception) {
            Log.e(LOG_TAG, "Thread ID must be a Long.");
            return null;
        }

        String finalSelection = concatSelections(
                selection, "thread_id = " + threadIdString);
        String unionQuery = buildConversationQuery(
                projection, finalSelection, selectionArgs, sortOrder);

        return mOpenHelper.getReadableDatabase().rawQuery(unionQuery, EMPTY_STRING_ARRAY);
    }

    /**
     * Return the union of MMS and SMS messages whose recipients
     * included this phone number.
     *
     * Use this query:
     *
     * SELECT ...
     *   FROM pdu, (SELECT _id AS address_id
     *              FROM addr
     *              WHERE PHONE_NUMBERS_EQUAL(addr.address, '<phoneNumber>'))
     *             AS matching_addresses
     *   WHERE pdu._id = matching_addresses.address_id
     * UNION
     * SELECT ...
     *   FROM sms
     *   WHERE PHONE_NUMBERS_EQUAL(sms.address, '<phoneNumber>');
     */
    private Cursor getMessagesByPhoneNumber(
            String phoneNumber, String[] projection, String selection,
            String[] selectionArgs, String sortOrder) {
        String escapedPhoneNumber = DatabaseUtils.sqlEscapeString(phoneNumber);
        String finalMmsSelection =
                concatSelections(
                        selection,
                        "pdu._id = matching_addresses.address_id");
        String finalSmsSelection =
                concatSelections(
                        selection,
                        "PHONE_NUMBERS_EQUAL(address, " +
                        escapedPhoneNumber + ")");
        SQLiteQueryBuilder mmsQueryBuilder = new SQLiteQueryBuilder();
        SQLiteQueryBuilder smsQueryBuilder = new SQLiteQueryBuilder();

        mmsQueryBuilder.setDistinct(true);
        smsQueryBuilder.setDistinct(true);
        mmsQueryBuilder.setTables(
                MmsProvider.TABLE_PDU +
                ", (SELECT _id AS address_id " +
                "FROM addr WHERE PHONE_NUMBERS_EQUAL(addr.address, " +
                escapedPhoneNumber + ")) " +
                "AS matching_addresses");
        smsQueryBuilder.setTables(SmsProvider.TABLE_SMS);

        String[] columns = handleNullMessageProjection(projection);
        String mmsSubQuery = mmsQueryBuilder.buildUnionSubQuery(
                MmsSms.TYPE_DISCRIMINATOR_COLUMN, columns, MMS_COLUMNS,
                0, "mms", finalMmsSelection, selectionArgs, null, null);
        String smsSubQuery = smsQueryBuilder.buildUnionSubQuery(
                MmsSms.TYPE_DISCRIMINATOR_COLUMN, columns, SMS_COLUMNS,
                0, "sms", finalSmsSelection, selectionArgs, null, null);
        SQLiteQueryBuilder unionQueryBuilder = new SQLiteQueryBuilder();

        unionQueryBuilder.setDistinct(true);

        String unionQuery = unionQueryBuilder.buildUnionQuery(
                new String[] { mmsSubQuery, smsSubQuery }, sortOrder, null);

        return mOpenHelper.getReadableDatabase().rawQuery(unionQuery, EMPTY_STRING_ARRAY);
    }

    /**
     * Return the conversation of certain thread ID.
     */
    private Cursor getConversationById(
            String threadIdString, String[] projection, String selection,
            String[] selectionArgs, String sortOrder) {
        try {
            Long.parseLong(threadIdString);
        } catch (NumberFormatException exception) {
            Log.e(LOG_TAG, "Thread ID must be a Long.");
            return null;
        }

        String extraSelection = "_id=" + threadIdString;
        String finalSelection = concatSelections(selection, extraSelection);
        SQLiteQueryBuilder queryBuilder = new SQLiteQueryBuilder();
        String[] columns = handleNullThreadsProjection(projection);

        queryBuilder.setDistinct(true);
        queryBuilder.setTables("threads");
        return queryBuilder.query(
                mOpenHelper.getReadableDatabase(), columns, finalSelection,
                selectionArgs, sortOrder, null, null);
    }

    private static String joinPduAndPendingMsgTables() {
        return MmsProvider.TABLE_PDU + " LEFT JOIN " + TABLE_PENDING_MSG
                + " ON pdu._id = pending_msgs.msg_id";
    }

    private static String[] createMmsProjection(String[] old) {
        String[] newProjection = new String[old.length];
        for (int i = 0; i < old.length; i++) {
            if (old[i].equals(BaseColumns._ID)) {
                newProjection[i] = "pdu._id";
            } else {
                newProjection[i] = old[i];
            }
        }
        return newProjection;
    }

    private Cursor getUndeliveredMessages(
            String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        String[] mmsProjection = createMmsProjection(projection);

        SQLiteQueryBuilder mmsQueryBuilder = new SQLiteQueryBuilder();
        SQLiteQueryBuilder smsQueryBuilder = new SQLiteQueryBuilder();

        mmsQueryBuilder.setTables(joinPduAndPendingMsgTables());
        smsQueryBuilder.setTables(SmsProvider.TABLE_SMS);

        String finalMmsSelection = concatSelections(
                selection, Mms.MESSAGE_BOX + " = " + Mms.MESSAGE_BOX_OUTBOX);
        String finalSmsSelection = concatSelections(
                selection, "(" + Sms.TYPE + " = " + Sms.MESSAGE_TYPE_OUTBOX
                + " OR " + Sms.TYPE + " = " + Sms.MESSAGE_TYPE_FAILED
                + " OR " + Sms.TYPE + " = " + Sms.MESSAGE_TYPE_QUEUED + ")");

        String[] smsColumns = handleNullMessageProjection(projection);
        String[] mmsColumns = handleNullMessageProjection(mmsProjection);
        String[] innerMmsProjection = makeProjectionWithDateAndThreadId(
                mmsColumns, 1000);
        String[] innerSmsProjection = makeProjectionWithDateAndThreadId(
                smsColumns, 1);

        Set<String> columnsPresentInTable = new HashSet<String>(MMS_COLUMNS);
        columnsPresentInTable.add("pdu._id");
        columnsPresentInTable.add(PendingMessages.ERROR_TYPE);
        String mmsSubQuery = mmsQueryBuilder.buildUnionSubQuery(
                MmsSms.TYPE_DISCRIMINATOR_COLUMN, innerMmsProjection,
                columnsPresentInTable, 1, "mms", finalMmsSelection, selectionArgs,
                null, null);
        String smsSubQuery = smsQueryBuilder.buildUnionSubQuery(
                MmsSms.TYPE_DISCRIMINATOR_COLUMN, innerSmsProjection,
                SMS_COLUMNS, 1, "sms", finalSmsSelection, selectionArgs,
                null, null);
        SQLiteQueryBuilder unionQueryBuilder = new SQLiteQueryBuilder();

        unionQueryBuilder.setDistinct(true);

        String unionQuery = unionQueryBuilder.buildUnionQuery(
                new String[] { smsSubQuery, mmsSubQuery }, null, null);

        SQLiteQueryBuilder outerQueryBuilder = new SQLiteQueryBuilder();

        outerQueryBuilder.setTables("(" + unionQuery + ")");

        String outerQuery = outerQueryBuilder.buildQuery(
                smsColumns, null, null, null, null, sortOrder, null);

        return mOpenHelper.getReadableDatabase().rawQuery(outerQuery, EMPTY_STRING_ARRAY);
    }

    /**
     * Add normalized date to the list of columns for an inner
     * projection.
     */
    private static String[] makeProjectionWithNormalizedDate(
            String[] projection, int dateMultiple) {
        int projectionSize = projection.length;
        String[] result = new String[projectionSize + 1];

        result[0] = "date * " + dateMultiple + " AS normalized_date";
        System.arraycopy(projection, 0, result, 1, projectionSize);
        return result;
    }

    private static String buildConversationQuery(String[] projection,
            String selection, String[] selectionArgs, String sortOrder) {
        String[] mmsProjection = createMmsProjection(projection);

        SQLiteQueryBuilder mmsQueryBuilder = new SQLiteQueryBuilder();
        SQLiteQueryBuilder smsQueryBuilder = new SQLiteQueryBuilder();

        mmsQueryBuilder.setDistinct(true);
        smsQueryBuilder.setDistinct(true);
        mmsQueryBuilder.setTables(joinPduAndPendingMsgTables());
        smsQueryBuilder.setTables(SmsProvider.TABLE_SMS);

        String[] smsColumns = handleNullMessageProjection(projection);
        String[] mmsColumns = handleNullMessageProjection(mmsProjection);
        String[] innerMmsProjection = makeProjectionWithNormalizedDate(mmsColumns, 1000);
        String[] innerSmsProjection = makeProjectionWithNormalizedDate(smsColumns, 1);

        Set<String> columnsPresentInTable = new HashSet<String>(MMS_COLUMNS);
        columnsPresentInTable.add("pdu._id");
        columnsPresentInTable.add(PendingMessages.ERROR_TYPE);

        String mmsSelection = concatSelections(selection,
                                Mms.MESSAGE_BOX + " != " + Mms.MESSAGE_BOX_DRAFTS);
        String mmsSubQuery = mmsQueryBuilder.buildUnionSubQuery(
                MmsSms.TYPE_DISCRIMINATOR_COLUMN, innerMmsProjection,
                columnsPresentInTable, 0, "mms",
                concatSelections(mmsSelection, MMS_CONVERSATION_CONSTRAINT),
                selectionArgs, null, null);
        String smsSubQuery = smsQueryBuilder.buildUnionSubQuery(
                MmsSms.TYPE_DISCRIMINATOR_COLUMN, innerSmsProjection, SMS_COLUMNS,
                0, "sms", concatSelections(selection, SMS_CONVERSATION_CONSTRAINT),
                selectionArgs, null, null);
        SQLiteQueryBuilder unionQueryBuilder = new SQLiteQueryBuilder();

        unionQueryBuilder.setDistinct(true);

        String unionQuery = unionQueryBuilder.buildUnionQuery(
                new String[] { smsSubQuery, mmsSubQuery },
                handleNullSortOrder(sortOrder), null);

        SQLiteQueryBuilder outerQueryBuilder = new SQLiteQueryBuilder();

        outerQueryBuilder.setTables("(" + unionQuery + ")");

        return outerQueryBuilder.buildQuery(
                smsColumns, null, null, null, null, sortOrder, null);
    }

    @Override
    public String getType(Uri uri) {
        return VND_ANDROID_DIR_MMS_SMS;
    }

    @Override
    public int delete(Uri uri, String selection,
            String[] selectionArgs) {
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        Context context = getContext();
        int affectedRows = 0;

        switch(URI_MATCHER.match(uri)) {
            case URI_CONVERSATIONS_MESSAGES:
                affectedRows = deleteConversation(uri, selection, selectionArgs);
                break;
            case URI_CONVERSATIONS:
                affectedRows = MmsProvider.deleteMessages(context, db,
                                        selection, selectionArgs, uri)
                        + db.delete("sms", selection, selectionArgs);
                break;
            case URI_OBSOLETE_THREADS:
                affectedRows = db.delete("threads",
                        "_id NOT IN (SELECT DISTINCT thread_id FROM sms " +
                        "UNION SELECT DISTINCT thread_id FROM pdu)", null);
                break;
            default:
                throw new UnsupportedOperationException(NO_DELETES_INSERTS_OR_UPDATES);
        }

        if (affectedRows > 0) {
            context.getContentResolver().notifyChange(MmsSms.CONTENT_URI, null);
        }
        return affectedRows;
    }

    /**
     * Delete the conversation with the given thread ID.
     */
    private int deleteConversation(Uri uri, String selection, String[] selectionArgs) {
        String threadId = uri.getLastPathSegment();
        try {
            Long.parseLong(threadId);
        } catch (NumberFormatException e) {
            Log.e(LOG_TAG, "Thread ID must be a Long.", e);
            return 0;
        }

        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        String finalSelection = concatSelections(selection, "thread_id = " + threadId);
        return MmsProvider.deleteMessages(getContext(), db, finalSelection,
                                          selectionArgs, uri)
                + db.delete("sms", finalSelection, selectionArgs);
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        throw new UnsupportedOperationException(NO_DELETES_INSERTS_OR_UPDATES);
    }

    @Override
    public int update(Uri uri, ContentValues values,
            String selection, String[] selectionArgs) {
        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        int affectedRows = 0;
        switch(URI_MATCHER.match(uri)) {
            case URI_CONVERSATIONS_MESSAGES:
                String threadIdString = uri.getPathSegments().get(1);
                affectedRows = updateConversation(threadIdString, values,
                        selection, selectionArgs);
                break;
            case URI_PENDING_MSG:
                affectedRows = db.update(TABLE_PENDING_MSG, values, selection, null);
                break;
            default:
                throw new UnsupportedOperationException(
                        NO_DELETES_INSERTS_OR_UPDATES);
        }

        if (affectedRows > 0) {
            getContext().getContentResolver().notifyChange(
                    MmsSms.CONTENT_URI, null);
        }
        return affectedRows;
    }

    private int updateConversation(
            String threadIdString, ContentValues values, String selection,
            String[] selectionArgs) {
        try {
            Long.parseLong(threadIdString);
        } catch (NumberFormatException exception) {
            Log.e(LOG_TAG, "Thread ID must be a Long.");
            return 0;
        }

        SQLiteDatabase db = mOpenHelper.getWritableDatabase();
        String finalSelection = concatSelections(selection, "thread_id=" + threadIdString);
        return db.update(MmsProvider.TABLE_PDU, values, finalSelection, selectionArgs)
                + db.update("sms", values, finalSelection, selectionArgs);
    }

    /**
     * Construct Sets of Strings containing exactly the columns
     * present in each table.  We will use this when constructing
     * UNION queries across the MMS and SMS tables.
     */
    private static void initializeColumnSets() {
        int commonColumnCount = MMS_SMS_COLUMNS.length;
        int mmsOnlyColumnCount = MMS_ONLY_COLUMNS.length;
        int smsOnlyColumnCount = SMS_ONLY_COLUMNS.length;
        Set<String> unionColumns = new HashSet<String>();

        for (int i = 0; i < commonColumnCount; i++) {
            MMS_COLUMNS.add(MMS_SMS_COLUMNS[i]);
            SMS_COLUMNS.add(MMS_SMS_COLUMNS[i]);
            unionColumns.add(MMS_SMS_COLUMNS[i]);
        }
        for (int i = 0; i < mmsOnlyColumnCount; i++) {
            MMS_COLUMNS.add(MMS_ONLY_COLUMNS[i]);
            unionColumns.add(MMS_ONLY_COLUMNS[i]);
        }
        for (int i = 0; i < smsOnlyColumnCount; i++) {
            SMS_COLUMNS.add(SMS_ONLY_COLUMNS[i]);
            unionColumns.add(SMS_ONLY_COLUMNS[i]);
        }

        int i = 0;
        for (String columnName : unionColumns) {
            UNION_COLUMNS[i++] = columnName;
        }
    }
}
