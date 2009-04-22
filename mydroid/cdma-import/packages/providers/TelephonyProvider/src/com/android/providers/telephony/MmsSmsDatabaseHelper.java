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

import static com.google.android.mms.pdu.PduHeaders.MESSAGE_TYPE_DELIVERY_IND;
import static com.google.android.mms.pdu.PduHeaders.MESSAGE_TYPE_NOTIFICATION_IND;
import static com.google.android.mms.pdu.PduHeaders.MESSAGE_TYPE_READ_ORIG_IND;
import static com.google.android.mms.pdu.PduHeaders.MESSAGE_TYPE_READ_REC_IND;
import static com.google.android.mms.pdu.PduHeaders.MESSAGE_TYPE_RETRIEVE_CONF;
import static com.google.android.mms.pdu.PduHeaders.MESSAGE_TYPE_SEND_REQ;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.provider.BaseColumns;
import android.provider.Telephony.Mms;
import android.provider.Telephony.MmsSms;
import android.provider.Telephony.Sms;
import android.provider.Telephony.Threads;
import android.provider.Telephony.Mms.Addr;
import android.provider.Telephony.Mms.Part;
import android.provider.Telephony.Mms.Rate;
import android.provider.Telephony.MmsSms.PendingMessages;
import android.util.Log;

public class MmsSmsDatabaseHelper extends SQLiteOpenHelper {
    private static final String TAG = "MmsSmsDatabaseHelper";

    private static final String SMS_UPDATE_THREAD_READ_BODY =
                        "  UPDATE threads SET read = " +
                        "    CASE (SELECT COUNT(*)" +
                        "          FROM sms" +
                        "          WHERE " + Sms.READ + " = 0" +
                        "            AND " + Sms.THREAD_ID + " = threads._id)" +
                        "      WHEN 0 THEN 1" +
                        "      ELSE 0" +
                        "    END" +
                        "  WHERE threads._id = new." + Sms.THREAD_ID + "; ";

    private static final String UPDATE_THREAD_COUNT_ON_NEW =
                        "  UPDATE threads SET message_count = " +
                        "     (SELECT COUNT(sms._id) FROM sms LEFT JOIN threads " +
                        "      ON threads._id = " + Sms.THREAD_ID +
                        "      WHERE " + Sms.THREAD_ID + " = new.thread_id" +
                        "        AND sms." + Sms.TYPE + " != 3) + " +
                        "     (SELECT COUNT(pdu._id) FROM pdu LEFT JOIN threads " +
                        "      ON threads._id = " + Mms.THREAD_ID +
                        "      WHERE " + Mms.THREAD_ID + " = new.thread_id" +
                        "        AND (m_type=132 OR m_type=130 OR m_type=128)" +
                        "        AND " + Mms.MESSAGE_BOX + " != 3" +
                        "        AND pdu.m_id is NULL) + " +
                        "     (SELECT COUNT(DISTINCT pdu.m_id) FROM pdu LEFT JOIN threads " +
                        "      ON threads._id = " + Mms.THREAD_ID +
                        "      WHERE " + Mms.THREAD_ID + " = new.thread_id" +
                        "        AND (m_type=132 OR m_type=130 OR m_type=128)" +
                        "        AND " + Mms.MESSAGE_BOX + " != 3" +
                        "        AND pdu.m_id is not NULL) " +
                        "  WHERE threads._id = new.thread_id; ";

    private static final String UPDATE_THREAD_COUNT_ON_OLD =
                        "  UPDATE threads SET message_count = " +
                        "     (SELECT COUNT(sms._id) FROM sms LEFT JOIN threads " +
                        "      ON threads._id = " + Sms.THREAD_ID +
                        "      WHERE " + Sms.THREAD_ID + " = old.thread_id" +
                        "        AND sms." + Sms.TYPE + " != 3) + " +
                        "     (SELECT COUNT(pdu._id) FROM pdu LEFT JOIN threads " +
                        "      ON threads._id = " + Mms.THREAD_ID +
                        "      WHERE " + Mms.THREAD_ID + " = old.thread_id" +
                        "        AND (m_type=132 OR m_type=130 OR m_type=128)" +
                        "        AND " + Mms.MESSAGE_BOX + " != 3) " +
                        "  WHERE threads._id = old.thread_id; ";

    private static final String SMS_UPDATE_THREAD_DATE_SNIPPET_COUNT_ON_UPDATE =
                        "BEGIN" +
                        "  UPDATE threads SET" +
                        "    date = (strftime('%s','now') * 1000), " +
                        "    snippet = new." + Sms.BODY + ", " +
                        "    snippet_cs = 0" +
                        "  WHERE threads._id = new." + Sms.THREAD_ID + "; " +
                        UPDATE_THREAD_COUNT_ON_NEW +
                        SMS_UPDATE_THREAD_READ_BODY +
                        "END;";

    private static final String PDU_UPDATE_THREAD_CONSTRAINTS =
                        "  WHEN new." + Mms.MESSAGE_TYPE + "=" + MESSAGE_TYPE_RETRIEVE_CONF +
                        "    OR new." + Mms.MESSAGE_TYPE + "=" + MESSAGE_TYPE_NOTIFICATION_IND +
                        "    OR new." + Mms.MESSAGE_TYPE + "=" + MESSAGE_TYPE_SEND_REQ + " ";

    private static final String PDU_UPDATE_THREAD_READ_BODY =
                        "  UPDATE threads SET read = " +
                        "    CASE (SELECT COUNT(*)" +
                        "          FROM " + MmsProvider.TABLE_PDU +
                        "          WHERE " + Mms.READ + " = 0" +
                        "            AND " + Mms.THREAD_ID + " = threads._id)" +
                        "      WHEN 0 THEN 1" +
                        "      ELSE 0" +
                        "    END" +
                        "  WHERE threads._id = new." + Mms.THREAD_ID + "; ";

    private static final String PDU_UPDATE_THREAD_DATE_SNIPPET_COUNT_ON_UPDATE =
                        "BEGIN" +
                        "  UPDATE threads SET" +
                        "    date = (strftime('%s','now') * 1000), " +
                        "    snippet = new." + Mms.SUBJECT + ", " +
                        "    snippet_cs = new." + Mms.SUBJECT_CHARSET +
                        "  WHERE threads._id = new." + Mms.THREAD_ID + "; " +
                        UPDATE_THREAD_COUNT_ON_NEW +
                        PDU_UPDATE_THREAD_READ_BODY +
                        "END;";

    private static final String UPDATE_THREAD_SNIPPET_SNIPPET_CS_ON_DELETE =
                        "  UPDATE threads SET snippet = " +
                        "   (SELECT snippet FROM" +
                        "     (SELECT date * 1000 AS date, sub AS snippet, thread_id FROM pdu" +
                        "      UNION SELECT date, body AS snippet, thread_id FROM sms)" +
                        "    WHERE thread_id = OLD.thread_id ORDER BY date DESC LIMIT 1) " +
                        "  WHERE threads._id = OLD.thread_id; " +
                        "  UPDATE threads SET snippet_cs = " +
                        "   (SELECT snippet_cs FROM" +
                        "     (SELECT date * 1000 AS date, sub_cs AS snippet_cs, thread_id FROM pdu" +
                        "      UNION SELECT date, 0 AS snippet_cs, thread_id FROM sms)" +
                        "    WHERE thread_id = OLD.thread_id ORDER BY date DESC LIMIT 1) " +
                        "  WHERE threads._id = OLD.thread_id; ";

    private static MmsSmsDatabaseHelper mInstance = null;

    static final String DATABASE_NAME = "mmssms.db";
    static final int DATABASE_VERSION = 41;

    private MmsSmsDatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    /**
     * Return a singleton helper for the combined MMS and SMS
     * database.
     */
    /* package */ static synchronized MmsSmsDatabaseHelper getInstance(Context context) {
        if (mInstance == null) {
            mInstance = new MmsSmsDatabaseHelper(context);
        }
        return mInstance;
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        createMmsTables(db);
        createSmsTables(db);
        createCommonTables(db);
        createCommonTriggers(db);
        createMmsTriggers(db);
    }

    private void createMmsTables(SQLiteDatabase db) {
        // N.B.: Whenever the columns here are changed, the columns in
        // {@ref MmsSmsProvider} must be changed to match.
        db.execSQL("CREATE TABLE " + MmsProvider.TABLE_PDU + " (" +
                   Mms._ID + " INTEGER PRIMARY KEY," +
                   Mms.THREAD_ID + " INTEGER," +
                   Mms.DATE + " INTEGER," +
                   Mms.MESSAGE_BOX + " INTEGER," +
                   Mms.READ + " INTEGER DEFAULT 0," +
                   Mms.MESSAGE_ID + " TEXT," +
                   Mms.SUBJECT + " TEXT," +
                   Mms.SUBJECT_CHARSET + " INTEGER," +
                   Mms.CONTENT_TYPE + " TEXT," +
                   Mms.CONTENT_LOCATION + " TEXT," +
                   Mms.EXPIRY + " INTEGER," +
                   Mms.MESSAGE_CLASS + " TEXT," +
                   Mms.MESSAGE_TYPE + " INTEGER," +
                   Mms.MMS_VERSION + " INTEGER," +
                   Mms.MESSAGE_SIZE + " INTEGER," +
                   Mms.PRIORITY + " INTEGER," +
                   Mms.READ_REPORT + " INTEGER," +
                   Mms.REPORT_ALLOWED + " INTEGER," +
                   Mms.RESPONSE_STATUS + " INTEGER," +
                   Mms.STATUS + " INTEGER," +
                   Mms.TRANSACTION_ID + " TEXT," +
                   Mms.RETRIEVE_STATUS + " INTEGER," +
                   Mms.RETRIEVE_TEXT + " TEXT," +
                   Mms.RETRIEVE_TEXT_CHARSET + " INTEGER," +
                   Mms.READ_STATUS + " INTEGER," +
                   Mms.CONTENT_CLASS + " INTEGER," +
                   Mms.RESPONSE_TEXT + " TEXT," +
                   Mms.DELIVERY_TIME + " INTEGER," +
                   Mms.DELIVERY_REPORT + " INTEGER);");

        db.execSQL("CREATE TABLE " + MmsProvider.TABLE_ADDR + " (" +
                   Addr._ID + " INTEGER PRIMARY KEY," +
                   Addr.MSG_ID + " INTEGER," +
                   Addr.CONTACT_ID + " INTEGER," +
                   Addr.ADDRESS + " TEXT," +
                   Addr.TYPE + " INTEGER," +
                   Addr.CHARSET + " INTEGER);");

        db.execSQL("CREATE TABLE " + MmsProvider.TABLE_PART + " (" +
                   Part._ID + " INTEGER PRIMARY KEY," +
                   Part.MSG_ID + " INTEGER," +
                   Part.SEQ + " INTEGER DEFAULT 0," +
                   Part.CONTENT_TYPE + " TEXT," +
                   Part.NAME + " TEXT," +
                   Part.CHARSET + " INTEGER," +
                   Part.CONTENT_DISPOSITION + " TEXT," +
                   Part.FILENAME + " TEXT," +
                   Part.CONTENT_ID + " TEXT," +
                   Part.CONTENT_LOCATION + " TEXT," +
                   Part.CT_START + " INTEGER," +
                   Part.CT_TYPE + " TEXT," +
                   Part._DATA + " TEXT);");

        db.execSQL("CREATE TABLE " + MmsProvider.TABLE_RATE + " (" +
                   Rate.SENT_TIME + " INTEGER);");

        db.execSQL("CREATE TABLE " + MmsProvider.TABLE_DRM + " (" +
                   BaseColumns._ID + " INTEGER PRIMARY KEY," +
                   "_data TEXT);");
    }

    private void createMmsTriggers(SQLiteDatabase db) {
        // Cleans up parts when a MM is deleted.
        db.execSQL("CREATE TRIGGER part_cleanup DELETE ON " + MmsProvider.TABLE_PDU + " " +
                   "BEGIN " +
                   "  DELETE FROM " + MmsProvider.TABLE_PART +
                   "  WHERE " + Part.MSG_ID + "=old._id;" +
                   "END;");

        // Cleans up address info when a MM is deleted.
        db.execSQL("CREATE TRIGGER addr_cleanup DELETE ON " + MmsProvider.TABLE_PDU + " " +
                   "BEGIN " +
                   "  DELETE FROM " + MmsProvider.TABLE_ADDR +
                   "  WHERE " + Addr.MSG_ID + "=old._id;" +
                   "END;");

        // Delete obsolete delivery-report, read-report while deleting their
        // associated Send.req.
        db.execSQL("CREATE TRIGGER cleanup_delivery_and_read_report " +
                   "AFTER DELETE ON " + MmsProvider.TABLE_PDU + " " +
                   "WHEN old." + Mms.MESSAGE_TYPE + "=" + MESSAGE_TYPE_SEND_REQ + " " +
                   "BEGIN " +
                   "  DELETE FROM " + MmsProvider.TABLE_PDU +
                   "  WHERE (" + Mms.MESSAGE_TYPE + "=" + MESSAGE_TYPE_DELIVERY_IND +
                   "    OR " + Mms.MESSAGE_TYPE + "=" + MESSAGE_TYPE_READ_ORIG_IND + ")" +
                   "    AND " + Mms.MESSAGE_ID + "=old." + Mms.MESSAGE_ID + "; " +
                   "END;");
    }

    private void createSmsTables(SQLiteDatabase db) {
        // N.B.: Whenever the columns here are changed, the columns in
        // {@ref MmsSmsProvider} must be changed to match.
        db.execSQL("CREATE TABLE sms (" +
                   "_id INTEGER PRIMARY KEY," +
                   "thread_id INTEGER," +
                   "address TEXT," +
                   "person INTEGER," +
                   "date INTEGER," +
                   "protocol INTEGER," +
                   "read INTEGER DEFAULT 0," +
                   "status INTEGER DEFAULT -1," + // a TP-Status value
                                                  // or -1 if it
                                                  // status hasn't
                                                  // been received
                   "type INTEGER," +
                   "reply_path_present INTEGER," +
                   "subject TEXT," +
                   "body TEXT," +
                   "service_center TEXT);");

        /**
         * This table is used by the SMS dispatcher to hold
         * incomplete partial messages until all the parts arrive.
         */
        db.execSQL("CREATE TABLE raw (" +
                   "_id INTEGER PRIMARY KEY," +
                   "date INTEGER," +
                   "reference_number INTEGER," + // one per full message
                   "count INTEGER," + // the number of parts
                   "sequence INTEGER," + // the part number of this message
                   "destination_port INTEGER," +
                   "address TEXT," +
                   "pdu TEXT);"); // the raw PDU for this part

        db.execSQL("CREATE TABLE attachments (" +
                   "sms_id INTEGER," +
                   "content_url TEXT," +
                   "offset INTEGER);");

        /**
         * This table is used by the SMS dispatcher to hold pending
         * delivery status report intents.
         */
        db.execSQL("CREATE TABLE sr_pending (" +
                   "reference_number INTEGER," +
                   "action TEXT," +
                   "data TEXT);");
    }

    private void createCommonTables(SQLiteDatabase db) {
        // TODO Ensure that each entry is removed when the last use of
        // any address equivalent to its address is removed.

        /**
         * This table maps the first instance seen of any particular
         * MMS/SMS address to an ID, which is then used as its
         * canonical representation.  If the same address or an
         * equivalent address (as determined by our Sqlite
         * PHONE_NUMBERS_EQUAL extension) is seen later, this same ID
         * will be used.
         */
        db.execSQL("CREATE TABLE canonical_addresses (" +
                   "_id INTEGER PRIMARY KEY," +
                   "address TEXT);");

        /**
         * This table maps the subject and an ordered set of recipient
         * IDs, separated by spaces, to a unique thread ID.  The IDs
         * come from the canonical_addresses table.  This works
         * because messages are considered to be part of the same
         * thread if they have the same subject (or a null subject)
         * and the same set of recipients.
         */
        db.execSQL("CREATE TABLE threads (" +
                   Threads._ID + " INTEGER PRIMARY KEY," +
                   Threads.DATE + " INTEGER DEFAULT 0," +
                   Threads.MESSAGE_COUNT + " INTEGER DEFAULT 0," +
                   Threads.RECIPIENT_IDS + " TEXT," +
                   Threads.SNIPPET + " TEXT," +
                   Threads.SNIPPET_CHARSET + " INTEGER DEFAULT 0," +
                   Threads.READ + " INTEGER DEFAULT 1," +
                   Threads.TYPE + " INTEGER DEFAULT 0," +
                   Threads.ERROR + " INTEGER DEFAULT 0);");

        /**
         * This table stores the queue of messages to be sent/downloaded.
         */
        db.execSQL("CREATE TABLE " + MmsSmsProvider.TABLE_PENDING_MSG +" (" +
                   PendingMessages._ID + " INTEGER PRIMARY KEY," +
                   PendingMessages.PROTO_TYPE + " INTEGER," +
                   PendingMessages.MSG_ID + " INTEGER," +
                   PendingMessages.MSG_TYPE + " INTEGER," +
                   PendingMessages.ERROR_TYPE + " INTEGER," +
                   PendingMessages.ERROR_CODE + " INTEGER," +
                   PendingMessages.RETRY_INDEX + " INTEGER NOT NULL DEFAULT 0," +
                   PendingMessages.DUE_TIME + " INTEGER," +
                   PendingMessages.LAST_TRY + " INTEGER);");

    }

    // TODO Check the query plans for these triggers.
    private void createCommonTriggers(SQLiteDatabase db) {
        // Updates threads table whenever a message is added to pdu.
        db.execSQL("CREATE TRIGGER pdu_update_thread_on_insert AFTER INSERT ON " +
                   MmsProvider.TABLE_PDU + " " +
                   PDU_UPDATE_THREAD_CONSTRAINTS +
                   PDU_UPDATE_THREAD_DATE_SNIPPET_COUNT_ON_UPDATE);

        // Updates threads table whenever a message is added to sms.
        db.execSQL("CREATE TRIGGER sms_update_thread_on_insert AFTER INSERT ON sms " +
                   SMS_UPDATE_THREAD_DATE_SNIPPET_COUNT_ON_UPDATE);

        // Updates threads table whenever a message in pdu is updated.
        db.execSQL("CREATE TRIGGER pdu_update_thread_date_subject_on_update AFTER" +
                   "  UPDATE OF " + Mms.DATE + ", " + Mms.SUBJECT + ", " + Mms.MESSAGE_BOX +
                   "  ON " + MmsProvider.TABLE_PDU + " " +
                   PDU_UPDATE_THREAD_CONSTRAINTS +
                   PDU_UPDATE_THREAD_DATE_SNIPPET_COUNT_ON_UPDATE);

        // Updates threads table whenever a message in sms is updated.
        db.execSQL("CREATE TRIGGER sms_update_thread_date_subject_on_update AFTER" +
                   "  UPDATE OF " + Sms.DATE + ", " + Sms.BODY + ", " + Sms.TYPE +
                   "  ON sms " +
                   SMS_UPDATE_THREAD_DATE_SNIPPET_COUNT_ON_UPDATE);

        // Updates threads table whenever a message in pdu is updated.
        db.execSQL("CREATE TRIGGER pdu_update_thread_read_on_update AFTER" +
                   "  UPDATE OF " + Mms.READ +
                   "  ON " + MmsProvider.TABLE_PDU + " " +
                   PDU_UPDATE_THREAD_CONSTRAINTS +
                   "BEGIN " +
                   PDU_UPDATE_THREAD_READ_BODY +
                   "END;");

        // Updates threads table whenever a message in sms is updated.
        db.execSQL("CREATE TRIGGER sms_update_thread_read_on_update AFTER" +
                   "  UPDATE OF " + Sms.READ +
                   "  ON sms " +
                   "BEGIN " +
                   SMS_UPDATE_THREAD_READ_BODY +
                   "END;");

        // Update threads table whenever a message in sms is deleted
        // (Usually an abandoned draft.)
        db.execSQL("CREATE TRIGGER sms_update_thread_on_delete " +
                   "AFTER DELETE ON sms " +
                   "BEGIN " +
                   "  UPDATE threads SET " +
                   "     date = (strftime('%s','now') * 1000) " +
                   "  WHERE threads._id = old." + Sms.THREAD_ID + "; " +
                   UPDATE_THREAD_COUNT_ON_OLD +
                   UPDATE_THREAD_SNIPPET_SNIPPET_CS_ON_DELETE +
                   "END;");

        // Update threads table whenever a message in pdu is deleted
        db.execSQL("CREATE TRIGGER pdu_update_thread_on_delete " +
                   "AFTER DELETE ON pdu " +
                   "BEGIN " +
                   "  UPDATE threads SET " +
                   "     date = (strftime('%s','now') * 1000)" +
                   "  WHERE threads._id = old." + Mms.THREAD_ID + "; " +
                   UPDATE_THREAD_COUNT_ON_OLD +
                   UPDATE_THREAD_SNIPPET_SNIPPET_CS_ON_DELETE +
                   "END;");

        // When the last message in a thread is deleted, these
        // triggers ensure that the entry for its thread ID is removed
        // from the threads table.
        db.execSQL("CREATE TRIGGER delete_obsolete_threads_pdu " +
                   "AFTER DELETE ON pdu " +
                   "BEGIN " +
                   "  DELETE FROM threads " +
                   "  WHERE " +
                   "    _id = old.thread_id " +
                   "    AND _id NOT IN " +
                   "    (SELECT thread_id FROM sms " +
                   "     UNION SELECT thread_id from pdu); " +
                   "END;");
        db.execSQL("CREATE TRIGGER delete_obsolete_threads_when_update_pdu " +
                   "AFTER UPDATE OF " + Mms.THREAD_ID + " ON pdu " +
                   "WHEN old." + Mms.THREAD_ID + " != new." + Mms.THREAD_ID + " " +
                   "BEGIN " +
                   "  DELETE FROM threads " +
                   "  WHERE " +
                   "    _id = old.thread_id " +
                   "    AND _id NOT IN " +
                   "    (SELECT thread_id FROM sms " +
                   "     UNION SELECT thread_id from pdu); " +
                   "END;");
        db.execSQL("CREATE TRIGGER delete_obsolete_threads_sms " +
                   "AFTER DELETE ON sms " +
                   "BEGIN " +
                   "  DELETE FROM threads " +
                   "  WHERE " +
                   "    _id = old.thread_id " +
                   "    AND _id NOT IN " +
                   "    (SELECT thread_id FROM sms " +
                   "     UNION SELECT thread_id from pdu); " +
                   "END;");
        // Insert pending status for M-Notification.ind or M-ReadRec.ind
        // when they are inserted into Inbox/Outbox.
        db.execSQL("CREATE TRIGGER insert_mms_pending_on_insert " +
                   "AFTER INSERT ON pdu " +
                   "WHEN new." + Mms.MESSAGE_TYPE + "=" + MESSAGE_TYPE_NOTIFICATION_IND +
                   "  OR new." + Mms.MESSAGE_TYPE + "=" + MESSAGE_TYPE_READ_REC_IND + " " +
                   "BEGIN " +
                   "  INSERT INTO " + MmsSmsProvider.TABLE_PENDING_MSG +
                   "    (" + PendingMessages.PROTO_TYPE + "," +
                   "     " + PendingMessages.MSG_ID + "," +
                   "     " + PendingMessages.MSG_TYPE + "," +
                   "     " + PendingMessages.ERROR_TYPE + "," +
                   "     " + PendingMessages.ERROR_CODE + "," +
                   "     " + PendingMessages.RETRY_INDEX + "," +
                   "     " + PendingMessages.DUE_TIME + ") " +
                   "  VALUES " +
                   "    (" + MmsSms.MMS_PROTO + "," +
                   "      new." + BaseColumns._ID + "," +
                   "      new." + Mms.MESSAGE_TYPE + ",0,0,0,0);" +
                   "END;");

        // Insert pending status for M-Send.req when it is moved into Outbox.
        db.execSQL("CREATE TRIGGER insert_mms_pending_on_update " +
                   "AFTER UPDATE ON pdu " +
                   "WHEN new." + Mms.MESSAGE_TYPE + "=" + MESSAGE_TYPE_SEND_REQ +
                   "  AND new." + Mms.MESSAGE_BOX + "=" + Mms.MESSAGE_BOX_OUTBOX +
                   "  AND old." + Mms.MESSAGE_BOX + "!=" + Mms.MESSAGE_BOX_OUTBOX + " " +
                   "BEGIN " +
                   "  INSERT INTO " + MmsSmsProvider.TABLE_PENDING_MSG +
                   "    (" + PendingMessages.PROTO_TYPE + "," +
                   "     " + PendingMessages.MSG_ID + "," +
                   "     " + PendingMessages.MSG_TYPE + "," +
                   "     " + PendingMessages.ERROR_TYPE + "," +
                   "     " + PendingMessages.ERROR_CODE + "," +
                   "     " + PendingMessages.RETRY_INDEX + "," +
                   "     " + PendingMessages.DUE_TIME + ") " +
                   "  VALUES " +
                   "    (" + MmsSms.MMS_PROTO + "," +
                   "      new." + BaseColumns._ID + "," +
                   "      new." + Mms.MESSAGE_TYPE + ",0,0,0,0);" +
                   "END;");

        // When a message is moved out of Outbox, delete its pending status.
        db.execSQL("CREATE TRIGGER delete_mms_pending_on_update " +
                   "AFTER UPDATE ON " + MmsProvider.TABLE_PDU + " " +
                   "WHEN old." + Mms.MESSAGE_BOX + "=" + Mms.MESSAGE_BOX_OUTBOX +
                   "  AND new." + Mms.MESSAGE_BOX + "!=" + Mms.MESSAGE_BOX_OUTBOX + " " +
                   "BEGIN " +
                   "  DELETE FROM " + MmsSmsProvider.TABLE_PENDING_MSG +
                   "  WHERE " + PendingMessages.MSG_ID + "=new._id; " +
                   "END;");

        // Delete pending status for a message when it is deleted.
        db.execSQL("CREATE TRIGGER delete_mms_pending_on_delete " +
                   "AFTER DELETE ON " + MmsProvider.TABLE_PDU + " " +
                   "BEGIN " +
                   "  DELETE FROM " + MmsSmsProvider.TABLE_PENDING_MSG +
                   "  WHERE " + PendingMessages.MSG_ID + "=old._id; " +
                   "END;");

        // TODO Add triggers for SMS retry-status management.

        // Update the error flag of threads when the error type of
        // a pending MM is updated.
        db.execSQL("CREATE TRIGGER update_threads_error_on_update_mms " +
                   "  AFTER UPDATE OF err_type ON pending_msgs " +
                   "  WHEN (OLD.err_type < 10 AND NEW.err_type >= 10)" +
                   "    OR (OLD.err_type >= 10 AND NEW.err_type < 10) " +
                   "BEGIN" +
                   "  UPDATE threads SET error = " +
                   "    CASE" +
                   "      WHEN NEW.err_type >= 10 THEN error + 1" +
                   "      ELSE error - 1" +
                   "    END " +
                   "  WHERE _id =" +
                   "   (SELECT DISTINCT thread_id" +
                   "    FROM pdu" +
                   "    WHERE _id = NEW.msg_id); " +
                   "END;");

        // Update the error flag of threads when delete pending message.
        db.execSQL("CREATE TRIGGER update_threads_error_on_delete_mms " +
                   "  BEFORE DELETE ON pdu" +
                   "  WHEN OLD._id IN (SELECT DISTINCT msg_id" +
                   "                   FROM pending_msgs" +
                   "                   WHERE err_type >= 10) " +
                   "BEGIN " +
                   "  UPDATE threads SET error = error - 1" +
                   "  WHERE _id = OLD.thread_id; " +
                   "END;");

        // Update the error flag of threads while moving an MM out of Outbox,
        // which was failed to be sent permanently.
        db.execSQL("CREATE TRIGGER update_threads_error_on_move_mms " +
                   "  BEFORE UPDATE OF msg_box ON pdu " +
                   "  WHEN (OLD.msg_box = 4 AND NEW.msg_box != 4) " +
                   "  AND (OLD._id IN (SELECT DISTINCT msg_id" +
                   "                   FROM pending_msgs" +
                   "                   WHERE err_type >= 10)) " +
                   "BEGIN " +
                   "  UPDATE threads SET error = error - 1" +
                   "  WHERE _id = OLD.thread_id; " +
                   "END;");

        // Update the error flag of threads after a text message was
        // failed to send/receive.
        db.execSQL("CREATE TRIGGER update_threads_error_on_update_sms " +
                   "  AFTER UPDATE OF type ON sms" +
                   "  WHEN (OLD.type != 5 AND NEW.type = 5)" +
                   "    OR (OLD.type = 5 AND NEW.type != 5) " +
                   "BEGIN " +
                   "  UPDATE threads SET error = " +
                   "    CASE" +
                   "      WHEN NEW.type = 5 THEN error + 1" +
                   "      ELSE error - 1" +
                   "    END " +
                   "  WHERE _id = NEW.thread_id; " +
                   "END;");

        // Update the error flag of threads when delete a text message.
        db.execSQL("CREATE TRIGGER update_threads_error_on_delete_sms " +
                   "  AFTER DELETE ON sms" +
                   "  WHEN (OLD.type = 5) " +
                   "BEGIN " +
                   "  UPDATE threads SET error = error - 1" +
                   "  WHERE _id = OLD.thread_id; " +
                   "END;");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int currentVersion) {
        Log.w(TAG, "Upgrading database from version " + oldVersion
                + " to " + currentVersion + ".");

        switch (oldVersion) {
            case 24:
                if (currentVersion <= 24) {
                    return;
                }

                db.beginTransaction();
                try {
                    upgradeDatabaseToVersion25(db);
                    db.setTransactionSuccessful();
                } catch (Throwable ex) {
                    Log.e(TAG, ex.getMessage(), ex);
                    break; // force to destroy all old data;
                } finally {
                    db.endTransaction();
                }
                // fall-through
            case 25:
                if (currentVersion <= 25) {
                    return;
                }

                db.beginTransaction();
                try {
                    upgradeDatabaseToVersion26(db);
                    db.setTransactionSuccessful();
                } catch (Throwable ex) {
                    Log.e(TAG, ex.getMessage(), ex);
                    break; // force to destroy all old data;
                } finally {
                    db.endTransaction();
                }
                // fall-through
            case 26:
                if (currentVersion <= 26) {
                    return;
                }

                db.beginTransaction();
                try {
                    upgradeDatabaseToVersion27(db);
                    db.setTransactionSuccessful();
                } catch (Throwable ex) {
                    Log.e(TAG, ex.getMessage(), ex);
                    break; // force to destroy all old data;
                } finally {
                    db.endTransaction();
                }
                // fall-through
            case 27:
                if (currentVersion <= 27) {
                    return;
                }

                db.beginTransaction();
                try {
                    upgradeDatabaseToVersion28(db);
                    db.setTransactionSuccessful();
                } catch (Throwable ex) {
                    Log.e(TAG, ex.getMessage(), ex);
                    break; // force to destroy all old data;
                } finally {
                    db.endTransaction();
                }
                // fall-through
            case 28:
                if (currentVersion <= 28) {
                    return;
                }

                // Test whether this database file is from TC2 branch.
                Cursor c = db.rawQuery("SELECT * FROM threads", null);
                if (c != null) {
                    try {
                        c.getColumnIndexOrThrow("snippet_cs");
                    } catch (IllegalArgumentException e) {
                        // Column 'snippet_cs' doesn't exist, which means
                        // this database file was maintained by TC2 branch
                        // and its version is inconsistent.
                        Log.w(TAG, "Upgrade database file from TC2!!!");
                        db.beginTransaction();
                        try {
                            upgradeDatabaseToVersion28(db);
                            db.setTransactionSuccessful();
                        } catch (Throwable ex) {
                            Log.e(TAG, ex.getMessage(), ex);
                            break; // force to destroy all old data;
                        } finally {
                            db.endTransaction();
                        }
                    } finally {
                        c.close();
                    }
                }

                db.beginTransaction();
                try {
                    upgradeDatabaseToVersion29(db);
                    db.setTransactionSuccessful();
                } catch (Throwable ex) {
                    Log.e(TAG, ex.getMessage(), ex);
                    break; // force to destroy all old data;
                } finally {
                    db.endTransaction();
                }
                // fall-through
            case 29:
                if (currentVersion <= 29) {
                    return;
                }

                db.beginTransaction();
                try {
                    upgradeDatabaseToVersion30(db);
                    db.setTransactionSuccessful();
                } catch (Throwable ex) {
                    Log.e(TAG, ex.getMessage(), ex);
                    break; // force to destroy all old data;
                } finally {
                    db.endTransaction();
                }
                // fall-through
            case 30:
                if (currentVersion <= 30) {
                    return;
                }

                db.beginTransaction();
                try {
                    upgradeDatabaseToVersion31(db);
                    db.setTransactionSuccessful();
                } catch (Throwable ex) {
                    Log.e(TAG, ex.getMessage(), ex);
                    break; // force to destroy all old data;
                } finally {
                    db.endTransaction();
                }
                // fall-through
            case 31:
                if (currentVersion <= 31) {
                    return;
                }

                db.beginTransaction();
                try {
                    upgradeDatabaseToVersion32(db);
                    db.setTransactionSuccessful();
                } catch (Throwable ex) {
                    Log.e(TAG, ex.getMessage(), ex);
                    break; // force to destroy all old data;
                } finally {
                    db.endTransaction();
                }
                // fall-through
            case 32:
                if (currentVersion <= 32) {
                    return;
                }

                db.beginTransaction();
                try {
                    upgradeDatabaseToVersion33(db);
                    db.setTransactionSuccessful();
                } catch (Throwable ex) {
                    Log.e(TAG, ex.getMessage(), ex);
                    break; // force to destroy all old data;
                } finally {
                    db.endTransaction();
                }
                // fall-through
            case 33:
                if (currentVersion <= 33) {
                    return;
                }

                db.beginTransaction();
                try {
                    upgradeDatabaseToVersion34(db);
                    db.setTransactionSuccessful();
                } catch (Throwable ex) {
                    Log.e(TAG, ex.getMessage(), ex);
                    break; // force to destroy all old data;
                } finally {
                    db.endTransaction();
                }
                // fall-through
            case 34:
                if (currentVersion <= 34) {
                    return;
                }

                db.beginTransaction();
                try {
                    upgradeDatabaseToVersion35(db);
                    db.setTransactionSuccessful();
                } catch (Throwable ex) {
                    Log.e(TAG, ex.getMessage(), ex);
                    break; // force to destroy all old data;
                } finally {
                    db.endTransaction();
                }
                // fall-through
            case 35:
                if (currentVersion <= 35) {
                    return;
                }

                db.beginTransaction();
                try {
                    upgradeDatabaseToVersion36(db);
                    db.setTransactionSuccessful();
                } catch (Throwable ex) {
                    Log.e(TAG, ex.getMessage(), ex);
                    break; // force to destroy all old data;
                } finally {
                    db.endTransaction();
                }
                // fall-through
            case 36:
                if (currentVersion <= 36) {
                    return;
                }

                db.beginTransaction();
                try {
                    upgradeDatabaseToVersion37(db);
                    db.setTransactionSuccessful();
                } catch (Throwable ex) {
                    Log.e(TAG, ex.getMessage(), ex);
                    break; // force to destroy all old data;
                } finally {
                    db.endTransaction();
                }
                // fall-through
            case 37:
                if (currentVersion <= 37) {
                    return;
                }

                db.beginTransaction();
                try {
                    upgradeDatabaseToVersion38(db);
                    db.setTransactionSuccessful();
                } catch (Throwable ex) {
                    Log.e(TAG, ex.getMessage(), ex);
                    break; // force to destroy all old data;
                } finally {
                    db.endTransaction();
                }
                // fall-through
            case 38:
                if (currentVersion <= 38) {
                    return;
                }

                db.beginTransaction();
                try {
                    upgradeDatabaseToVersion39(db);
                    db.setTransactionSuccessful();
                } catch (Throwable ex) {
                    Log.e(TAG, ex.getMessage(), ex);
                    break; // force to destroy all old data;
                } finally {
                    db.endTransaction();
                }
                // fall-through
            case 39:
                if (currentVersion <= 39) {
                    return;
                }

                db.beginTransaction();
                try {
                    upgradeDatabaseToVersion40(db);
                    db.setTransactionSuccessful();
                } catch (Throwable ex) {
                    Log.e(TAG, ex.getMessage(), ex);
                    break; // force to destroy all old data;
                } finally {
                    db.endTransaction();
                }
                // fall-through
            case 40:
                if (currentVersion <= 40) {
                    return;
                }

                db.beginTransaction();
                try {
                    upgradeDatabaseToVersion41(db);
                    db.setTransactionSuccessful();
                } catch (Throwable ex) {
                    Log.e(TAG, ex.getMessage(), ex);
                    break; // force to destroy all old data;
                } finally {
                    db.endTransaction();
                }
                return;
        }

        Log.w(TAG, "Destroying all old data.");
        dropCommonTriggers(db);
        dropMmsTriggers(db);
        dropCommonTables(db);
        dropMmsTables(db);
        dropSmsTables(db);
        onCreate(db);
    }

    private void dropCommonTables(SQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS canonical_addresses");
        db.execSQL("DROP TABLE IF EXISTS threads");
        db.execSQL("DROP TABLE IF EXISTS " + MmsSmsProvider.TABLE_PENDING_MSG);
    }

    private void dropCommonTriggers(SQLiteDatabase db) {
        db.execSQL("DROP TRIGGER IF EXISTS delete_obsolete_threads_pdu");
        db.execSQL("DROP TRIGGER IF EXISTS delete_obsolete_threads_when_update_pdu");
        db.execSQL("DROP TRIGGER IF EXISTS delete_obsolete_threads_sms");
        db.execSQL("DROP TRIGGER IF EXISTS pdu_update_thread_on_insert");
        db.execSQL("DROP TRIGGER IF EXISTS sms_update_thread_on_insert");
        db.execSQL("DROP TRIGGER IF EXISTS pdu_update_thread_date_subject_on_update");
        db.execSQL("DROP TRIGGER IF EXISTS sms_update_thread_date_subject_on_update");
        db.execSQL("DROP TRIGGER IF EXISTS pdu_update_thread_read_on_update");
        db.execSQL("DROP TRIGGER IF EXISTS sms_update_thread_read_on_update");
        db.execSQL("DROP TRIGGER IF EXISTS sms_update_thread_on_delete");
        db.execSQL("DROP TRIGGER IF EXISTS insert_mms_pending_on_insert");
        db.execSQL("DROP TRIGGER IF EXISTS insert_mms_pending_on_update");
        db.execSQL("DROP TRIGGER IF EXISTS delete_mms_pending_on_update");
        db.execSQL("DROP TRIGGER IF EXISTS delete_mms_pending_on_delete");
        db.execSQL("DROP TRIGGER IF EXISTS update_threads_error_on_update_mms");
        db.execSQL("DROP TRIGGER IF EXISTS update_threads_error_on_delete_mms");
        db.execSQL("DROP TRIGGER IF EXISTS update_threads_error_on_move_mms");
        db.execSQL("DROP TRIGGER IF EXISTS update_threads_error_on_update_sms");
        db.execSQL("DROP TRIGGER IF EXISTS update_threads_error_on_delete_sms");
    }

    private void dropSmsTables(SQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS sms");
        db.execSQL("DROP TABLE IF EXISTS newSmsIndicator");
        db.execSQL("DROP TABLE IF EXISTS raw");
        db.execSQL("DROP TABLE IF EXISTS attachments");
        db.execSQL("DROP TABLE IF EXISTS thread_ids");
        db.execSQL("DROP TABLE IF EXISTS sr_pending");
    }

    private void dropMmsTables(SQLiteDatabase db) {
        db.execSQL("DROP TABLE IF EXISTS " + MmsProvider.TABLE_PDU + ";");
        db.execSQL("DROP TABLE IF EXISTS " + MmsProvider.TABLE_ADDR + ";");
        db.execSQL("DROP TABLE IF EXISTS " + MmsProvider.TABLE_PART + ";");
        db.execSQL("DROP TABLE IF EXISTS " + MmsProvider.TABLE_RATE + ";");
        db.execSQL("DROP TABLE IF EXISTS " + MmsProvider.TABLE_DRM + ";");
    }

    private void dropMmsTriggers(SQLiteDatabase db) {
        db.execSQL("DROP TRIGGER IF EXISTS part_cleanup;");
        db.execSQL("DROP TRIGGER IF EXISTS addr_cleanup;");
        db.execSQL("DROP TRIGGER IF EXISTS cleanup_delivery_and_read_report;");
    }

    private void upgradeDatabaseToVersion25(SQLiteDatabase db) {
        db.execSQL("ALTER TABLE threads " +
                   "ADD COLUMN type INTEGER NOT NULL DEFAULT 0;");
    }

    private void upgradeDatabaseToVersion26(SQLiteDatabase db) {
        db.execSQL("ALTER TABLE threads " +
                   "ADD COLUMN error INTEGER DEFAULT 0;");

        // Do NOT use defined symbols when upgrading database
        // because they may be changed and cannot be applied
        // to old database.
        db.execSQL("UPDATE threads SET error = 1 WHERE _id IN" +
                   "  (SELECT thread_id FROM pdu LEFT JOIN pending_msgs" +
                   "     ON pdu.thread_id = pending_msgs.msg_id" +
                   "     WHERE proto_type = 1 AND err_type >= 10" +
                   "     GROUP BY thread_id); " +
                   "UPDATE threads SET error = 1 WHERE _id IN" +
                   "  (SELECT thread_id FROM sms LEFT JOIN pending_msgs" +
                   "     ON sms.thread_id = pending_msgs.msg_id" +
                   "     WHERE proto_type = 0 AND err_type >= 10" +
                   "     GROUP BY thread_id); ");

        db.execSQL("CREATE TRIGGER update_threads_error_on_update " +
                   "  AFTER UPDATE OF err_type ON pending_msgs " +
                   "BEGIN " +
                   "UPDATE threads SET error = 1 WHERE _id IN" +
                   "  (SELECT thread_id FROM pdu LEFT JOIN pending_msgs" +
                   "     ON pdu.thread_id = pending_msgs.msg_id" +
                   "     WHERE proto_type = 1 AND err_type >= 10" +
                   "     GROUP BY thread_id); " +
                   "UPDATE threads SET error = 1 WHERE _id IN" +
                   "  (SELECT thread_id FROM sms LEFT JOIN pending_msgs" +
                   "     ON sms.thread_id = pending_msgs.msg_id" +
                   "     WHERE proto_type = 0 AND err_type >= 10" +
                   "     GROUP BY thread_id); " +
                   "END;");

        db.execSQL("CREATE TRIGGER update_threads_error_on_delete " +
                   "  AFTER DELETE ON pending_msgs " +
                   "BEGIN " +
                   "UPDATE threads SET error = 1 WHERE _id IN" +
                   "  (SELECT thread_id FROM pdu LEFT JOIN pending_msgs" +
                   "     ON pdu.thread_id = pending_msgs.msg_id" +
                   "     WHERE proto_type = 1 AND err_type >= 10" +
                   "     GROUP BY thread_id); " +
                   "UPDATE threads SET error = 1 WHERE _id IN" +
                   "  (SELECT thread_id FROM sms LEFT JOIN pending_msgs" +
                   "     ON sms.thread_id = pending_msgs.msg_id" +
                   "     WHERE proto_type = 0 AND err_type >= 10" +
                   "     GROUP BY thread_id); " +
                   "END;");
    }

    private void upgradeDatabaseToVersion27(SQLiteDatabase db) {
        db.execSQL("UPDATE threads SET error = 1 WHERE _id IN" +
                   "  (SELECT thread_id FROM pdu LEFT JOIN pending_msgs" +
                   "     ON pdu._id = pending_msgs.msg_id" +
                   "     WHERE proto_type = 1 AND err_type >= 10" +
                   "     GROUP BY thread_id); " +
                   "UPDATE threads SET error = 1 WHERE _id IN" +
                   "  (SELECT thread_id FROM sms LEFT JOIN pending_msgs" +
                   "     ON sms._id = pending_msgs.msg_id" +
                   "     WHERE proto_type = 0 AND err_type >= 10" +
                   "     GROUP BY thread_id); ");

        db.execSQL("DROP TRIGGER IF EXISTS update_threads_error_on_update");
        db.execSQL("DROP TRIGGER IF EXISTS update_threads_error_on_delete");

        db.execSQL("CREATE TRIGGER update_threads_error_on_update " +
                   "  AFTER UPDATE OF err_type ON pending_msgs " +
                   "BEGIN " +
                   "UPDATE threads SET error = 1 WHERE _id IN" +
                   "  (SELECT thread_id FROM pdu LEFT JOIN pending_msgs" +
                   "     ON pdu._id = pending_msgs.msg_id" +
                   "     WHERE proto_type = 1 AND err_type >= 10" +
                   "     GROUP BY thread_id); " +
                   "UPDATE threads SET error = 1 WHERE _id IN" +
                   "  (SELECT thread_id FROM sms LEFT JOIN pending_msgs" +
                   "     ON sms._id = pending_msgs.msg_id" +
                   "     WHERE proto_type = 0 AND err_type >= 10" +
                   "     GROUP BY thread_id); " +
                   "END;");

        db.execSQL("CREATE TRIGGER update_threads_error_on_delete " +
                   "  AFTER DELETE ON pending_msgs " +
                   "BEGIN " +
                   "UPDATE threads SET error = 1 WHERE _id IN" +
                   "  (SELECT thread_id FROM pdu LEFT JOIN pending_msgs" +
                   "     ON pdu._id = pending_msgs.msg_id" +
                   "     WHERE proto_type = 1 AND err_type >= 10" +
                   "     GROUP BY thread_id); " +
                   "UPDATE threads SET error = 1 WHERE _id IN" +
                   "  (SELECT thread_id FROM sms LEFT JOIN pending_msgs" +
                   "     ON sms._id = pending_msgs.msg_id" +
                   "     WHERE proto_type = 0 AND err_type >= 10" +
                   "     GROUP BY thread_id); " +
                   "END;");
    }

    private void upgradeDatabaseToVersion28(SQLiteDatabase db) {
        db.execSQL("ALTER TABLE threads " +
                   "ADD COLUMN snippet_cs INTEGER NOT NULL DEFAULT 0;");

        db.execSQL("DROP TRIGGER IF EXISTS pdu_update_thread_on_insert");
        db.execSQL("DROP TRIGGER IF EXISTS pdu_update_thread_date_subject_on_update");
        db.execSQL("DROP TRIGGER IF EXISTS pdu_update_thread_read_on_update");
        db.execSQL("DROP TRIGGER IF EXISTS sms_update_thread_on_delete");

        db.execSQL("CREATE TRIGGER pdu_update_thread_on_insert AFTER INSERT ON pdu " +
                   "  WHEN new.msg_box!=5 AND new.msg_box!=3" +
                   "    AND (new.m_type=132 OR new.m_type=130 OR new.m_type=128) " +
                   "BEGIN" +
                   "  UPDATE threads SET" +
                   "    date = (strftime('%s','now') * 1000), " +
                   "    snippet = new.sub, " +
                   "    snippet_cs = new.sub_cs" +
                   "  WHERE threads._id = new.thread_id; " +
                   "  UPDATE threads SET read = " +
                   "    CASE (SELECT COUNT(*)" +
                   "          FROM pdu" +
                   "          WHERE read = 0 AND thread_id = threads._id)" +
                   "      WHEN 0 THEN 1 ELSE 0" +
                   "    END" +
                   "  WHERE threads._id = new.thread_id; " +
                   "END;");

        db.execSQL("CREATE TRIGGER pdu_update_thread_date_subject_on_update AFTER" +
                   "  UPDATE OF date, sub, msg_box ON pdu " +
                   "  WHEN new.msg_box!=5 AND new.msg_box!=3" +
                   "    AND (new.m_type=132 OR new.m_type=130 OR new.m_type=128) " +
                   "BEGIN" +
                   "  UPDATE threads SET" +
                   "    date = (strftime('%s','now') * 1000), " +
                   "    snippet = new.sub, " +
                   "    snippet_cs = new.sub_cs" +
                   "  WHERE threads._id = new.thread_id; " +
                   "  UPDATE threads SET read = " +
                   "    CASE (SELECT COUNT(*)" +
                   "          FROM pdu" +
                   "          WHERE read = 0 AND thread_id = threads._id)" +
                   "      WHEN 0 THEN 1 ELSE 0" +
                   "    END" +
                   "  WHERE threads._id = new.thread_id; " +
                   "END;");

        db.execSQL("CREATE TRIGGER pdu_update_thread_read_on_update AFTER" +
                   "  UPDATE OF read ON pdu " +
                   "  WHEN new.msg_box!=5 AND new.msg_box!=3" +
                   "    AND (new.m_type=132 OR new.m_type=130 OR new.m_type=128) " +
                   "BEGIN " +
                   "  UPDATE threads SET read = " +
                   "    CASE (SELECT COUNT(*)" +
                   "          FROM pdu" +
                   "          WHERE read = 0 AND thread_id = threads._id)" +
                   "      WHEN 0 THEN 1 ELSE 0" +
                   "    END" +
                   "  WHERE threads._id = new.thread_id; " +
                   "END;");

        db.execSQL("CREATE TRIGGER sms_update_thread_on_delete " +
                   "AFTER DELETE ON sms " +
                   "BEGIN " +
                   "  UPDATE threads SET " +
                   "     date = (strftime('%s','now') * 1000), " +
                   "     snippet = (SELECT body FROM SMS ORDER BY date DESC LIMIT 1)" +
                   "  WHERE threads._id = old.thread_id; " +
                   "END;");
    }

    private void upgradeDatabaseToVersion29(SQLiteDatabase db) {
        db.execSQL("DROP TRIGGER IF EXISTS pdu_update_thread_on_insert");
        db.execSQL("DROP TRIGGER IF EXISTS pdu_update_thread_date_subject_on_update");
        db.execSQL("DROP TRIGGER IF EXISTS pdu_update_thread_read_on_update");

        db.execSQL("CREATE TRIGGER pdu_update_thread_on_insert AFTER INSERT ON pdu " +
                   "  WHEN new.m_type=132 OR new.m_type=130 OR new.m_type=128 " +
                   "BEGIN" +
                   "  UPDATE threads SET" +
                   "    date = (strftime('%s','now') * 1000), " +
                   "    snippet = new.sub, " +
                   "    snippet_cs = new.sub_cs" +
                   "  WHERE threads._id = new.thread_id; " +
                   "  UPDATE threads SET read = " +
                   "    CASE (SELECT COUNT(*)" +
                   "          FROM pdu" +
                   "          WHERE read = 0 AND thread_id = threads._id)" +
                   "      WHEN 0 THEN 1 ELSE 0" +
                   "    END" +
                   "  WHERE threads._id = new.thread_id; " +
                   "END;");

        db.execSQL("CREATE TRIGGER pdu_update_thread_date_subject_on_update AFTER" +
                   "  UPDATE OF date, sub, msg_box ON pdu " +
                   "  WHEN new.m_type=132 OR new.m_type=130 OR new.m_type=128 " +
                   "BEGIN" +
                   "  UPDATE threads SET" +
                   "    date = (strftime('%s','now') * 1000), " +
                   "    snippet = new.sub, " +
                   "    snippet_cs = new.sub_cs" +
                   "  WHERE threads._id = new.thread_id; " +
                   "  UPDATE threads SET read = " +
                   "    CASE (SELECT COUNT(*)" +
                   "          FROM pdu" +
                   "          WHERE read = 0 AND thread_id = threads._id)" +
                   "      WHEN 0 THEN 1 ELSE 0" +
                   "    END" +
                   "  WHERE threads._id = new.thread_id; " +
                   "END;");

        db.execSQL("CREATE TRIGGER pdu_update_thread_read_on_update AFTER" +
                   "  UPDATE OF read ON pdu " +
                   "  WHEN new.m_type=132 OR new.m_type=130 OR new.m_type=128 " +
                   "BEGIN " +
                   "  UPDATE threads SET read = " +
                   "    CASE (SELECT COUNT(*)" +
                   "          FROM pdu" +
                   "          WHERE read = 0 AND thread_id = threads._id)" +
                   "      WHEN 0 THEN 1 ELSE 0" +
                   "    END" +
                   "  WHERE threads._id = new.thread_id; " +
                   "END;");
    }

    private void upgradeDatabaseToVersion30(SQLiteDatabase db) {
        // Since SQLite doesn't support altering constraints
        // of an existing table, I have to create a new table
        // with updated constraints, copy old data into this
        // table, drop old table and then rename the new table
        // to 'threads'.
        db.execSQL("CREATE TABLE temp_threads (" +
                   "_id INTEGER PRIMARY KEY," +
                   "date INTEGER DEFAULT 0," +
                   "subject TEXT," +
                   "recipient_ids TEXT," +
                   "snippet TEXT," +
                   "snippet_cs INTEGER DEFAULT 0," +
                   "read INTEGER DEFAULT 1," +
                   "type INTEGER DEFAULT 0," +
                   "error INTEGER DEFAULT 0);");
        db.execSQL("INSERT INTO temp_threads SELECT * FROM threads;");
        db.execSQL("DROP TABLE IF EXISTS threads;");
        db.execSQL("ALTER TABLE temp_threads RENAME TO threads;");
    }

    private void upgradeDatabaseToVersion31(SQLiteDatabase db) {
        db.execSQL("DROP TRIGGER IF EXISTS sms_update_thread_on_delete");

        // Update threads table whenever a message in sms is deleted
        // (Usually an abandoned draft.)
        db.execSQL("CREATE TRIGGER sms_update_thread_on_delete " +
                   "AFTER DELETE ON sms " +
                   "BEGIN " +
                   "  UPDATE threads SET " +
                   "     date = (strftime('%s','now') * 1000) " +
                   "  WHERE threads._id = old.thread_id; " +
                   "  UPDATE threads SET" +
                   "    snippet = (SELECT snippet FROM" +
                   "      (SELECT date * 1000 AS date, sub AS snippet," +
                   "         sub_cs AS snippet_cs FROM pdu" +
                   "       UNION SELECT date, body AS snippet, NULL AS snippet_cs" +
                   "         FROM sms) ORDER BY date DESC LIMIT 1) " +
                   "  WHERE threads._id = old.thread_id; " +
                   "  UPDATE threads SET" +
                   "    snippet_cs = (SELECT snippet_cs FROM" +
                   "      (SELECT date * 1000 AS date, sub AS snippet," +
                   "         sub_cs AS snippet_cs FROM pdu" +
                   "       UNION SELECT date, body AS snippet, NULL AS snippet_cs" +
                   "         FROM sms) ORDER BY date DESC LIMIT 1) " +
                   "  WHERE threads._id = old.thread_id; " +
                   "END;");

        // Update threads table whenever a message in pdu is deleted
        db.execSQL("CREATE TRIGGER pdu_update_thread_on_delete " +
                   "AFTER DELETE ON pdu " +
                   "BEGIN " +
                   "  UPDATE threads SET " +
                   "     date = (strftime('%s','now') * 1000)" +
                   "  WHERE threads._id = old.thread_id;" +
                   "  UPDATE threads SET" +
                   "    snippet = (SELECT snippet FROM" +
                   "      (SELECT date * 1000 AS date, sub AS snippet," +
                   "         sub_cs AS snippet_cs FROM pdu" +
                   "       UNION SELECT date, body AS snippet, NULL AS snippet_cs" +
                   "         FROM sms) ORDER BY date DESC LIMIT 1) " +
                   "  WHERE threads._id = old.thread_id; " +
                   "  UPDATE threads SET" +
                   "    snippet_cs = (SELECT snippet_cs FROM" +
                   "      (SELECT date * 1000 AS date, sub AS snippet," +
                   "         sub_cs AS snippet_cs FROM pdu" +
                   "       UNION SELECT date, body AS snippet, NULL AS snippet_cs" +
                   "         FROM sms) ORDER BY date DESC LIMIT 1) " +
                   "  WHERE threads._id = old.thread_id; " +
                   "END;");
    }

    private void upgradeDatabaseToVersion32(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS rate (sent_time INTEGER);");
    }

    private void upgradeDatabaseToVersion33(SQLiteDatabase db) {
        db.execSQL("DROP TRIGGER IF EXISTS update_threads_error_on_update");
        db.execSQL("DROP TRIGGER IF EXISTS update_threads_error_on_delete");

        db.execSQL("CREATE TRIGGER update_threads_error_on_update_mms " +
                   "  AFTER UPDATE OF err_type ON pending_msgs " +
                   "  WHEN (OLD.err_type < 10 AND NEW.err_type >= 10)" +
                   "    OR (OLD.err_type >= 10 AND NEW.err_type < 10) " +
                   "BEGIN" +
                   "  UPDATE threads SET error = " +
                   "    CASE" +
                   "      WHEN NEW.err_type >= 10 THEN error + 1" +
                   "      ELSE error - 1" +
                   "    END " +
                   "  WHERE _id =" +
                   "   (SELECT DISTINCT thread_id" +
                   "    FROM pdu" +
                   "    WHERE _id = NEW.msg_id); " +
                   "END;");

        db.execSQL("CREATE TRIGGER update_threads_error_on_delete_mms " +
                   "  BEFORE DELETE ON pdu" +
                   "  WHEN OLD._id IN (SELECT DISTINCT msg_id" +
                   "                   FROM pending_msgs" +
                   "                   WHERE err_type >= 10) " +
                   "BEGIN " +
                   "  UPDATE threads SET error = error - 1" +
                   "  WHERE _id = OLD.thread_id; " +
                   "END;");

        db.execSQL("CREATE TRIGGER update_threads_error_on_update_sms " +
                   "  AFTER UPDATE OF type ON sms" +
                   "  WHEN (OLD.type != 5 AND NEW.type = 5)" +
                   "    OR (OLD.type = 5 AND NEW.type != 5) " +
                   "BEGIN " +
                   "  UPDATE threads SET error = " +
                   "    CASE" +
                   "      WHEN NEW.type = 5 THEN error + 1" +
                   "      ELSE error - 1" +
                   "    END " +
                   "  WHERE _id = NEW.thread_id; " +
                   "END;");

        db.execSQL("CREATE TRIGGER update_threads_error_on_delete_sms " +
                   "  AFTER DELETE ON sms" +
                   "  WHEN (OLD.type = 5) " +
                   "BEGIN " +
                   "  UPDATE threads SET error = error - 1" +
                   "  WHERE _id = OLD.thread_id; " +
                   "END;");
    }

    private void upgradeDatabaseToVersion34(SQLiteDatabase db) {
        db.execSQL("DROP TRIGGER IF EXISTS sms_update_thread_on_insert");
        db.execSQL("DROP TRIGGER IF EXISTS sms_update_thread_date_subject_on_update");

        db.execSQL("CREATE TRIGGER sms_update_thread_on_insert AFTER INSERT ON sms " +
                   "BEGIN" +
                   "  UPDATE threads SET" +
                   "    date = (strftime('%s','now') * 1000), " +
                   "    snippet = new.body," +
                   "    snippet_cs = 0" +
                   "  WHERE threads._id = new.thread_id; " +
                   "  UPDATE threads SET read = " +
                   "    CASE (SELECT COUNT(*)" +
                   "          FROM sms" +
                   "          WHERE read = 0" +
                   "            AND thread_id = threads._id)" +
                   "      WHEN 0 THEN 1" +
                   "      ELSE 0" +
                   "    END" +
                   "  WHERE threads._id = new.thread_id; " +
                   "END;");

        db.execSQL("CREATE TRIGGER sms_update_thread_date_subject_on_update AFTER" +
                   "  UPDATE OF date, body, msg_box" +
                   "  ON sms " +
                   "BEGIN" +
                   "  UPDATE threads SET" +
                   "    date = (strftime('%s','now') * 1000), " +
                   "    snippet = new.body," +
                   "    snippet_cs = 0" +
                   "  WHERE threads._id = new.thread_id; " +
                   "  UPDATE threads SET read = " +
                   "    CASE (SELECT COUNT(*)" +
                   "          FROM sms" +
                   "          WHERE read = 0" +
                   "            AND thread_id = threads._id)" +
                   "      WHEN 0 THEN 1" +
                   "      ELSE 0" +
                   "    END" +
                   "  WHERE threads._id = new.thread_id; " +
                   "END;");
    }

    private void upgradeDatabaseToVersion35(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE temp_threads (" +
                   "_id INTEGER PRIMARY KEY," +
                   "date INTEGER DEFAULT 0," +
                   "message_count INTEGER DEFAULT 0," +
                   "recipient_ids TEXT," +
                   "snippet TEXT," +
                   "snippet_cs INTEGER DEFAULT 0," +
                   "read INTEGER DEFAULT 1," +
                   "type INTEGER DEFAULT 0," +
                   "error INTEGER DEFAULT 0);");
        db.execSQL("INSERT INTO temp_threads " +
                   "SELECT _id, date, 0 AS message_count, recipient_ids," +
                   "       snippet, snippet_cs, read, type, error " +
                   "FROM threads;");
        db.execSQL("DROP TABLE IF EXISTS threads;");
        db.execSQL("ALTER TABLE temp_threads RENAME TO threads;");

        db.execSQL("DROP TRIGGER IF EXISTS pdu_update_thread_on_insert");
        db.execSQL("DROP TRIGGER IF EXISTS sms_update_thread_on_insert");
        db.execSQL("DROP TRIGGER IF EXISTS sms_update_thread_on_delete");
        db.execSQL("DROP TRIGGER IF EXISTS pdu_update_thread_on_delete");
        db.execSQL("DROP TRIGGER IF EXISTS sms_update_thread_date_subject_on_update");
        db.execSQL("DROP TRIGGER IF EXISTS pdu_update_thread_date_subject_on_update");

        db.execSQL("CREATE TRIGGER pdu_update_thread_on_insert AFTER INSERT ON pdu " +
                   "  WHEN new.m_type=132 OR new.m_type=130 OR new.m_type=128 " +
                   "BEGIN" +
                   "  UPDATE threads SET" +
                   "    date = (strftime('%s','now') * 1000), " +
                   "    snippet = new.sub, " +
                   "    snippet_cs = new.sub_cs" +
                   "  WHERE threads._id = new.thread_id; " +
                   "  UPDATE threads SET message_count = " +
                   "     (SELECT COUNT(sms._id) FROM sms LEFT JOIN threads " +
                   "      ON threads._id = thread_id" +
                   "      WHERE thread_id = new.thread_id" +
                   "        AND sms.type != 3) + " +
                   "     (SELECT COUNT(pdu._id) FROM pdu LEFT JOIN threads " +
                   "      ON threads._id = thread_id" +
                   "      WHERE thread_id = new.thread_id" +
                   "        AND (m_type=132 OR m_type=130 OR m_type=128)" +
                   "        AND msg_box != 3) " +
                   "  WHERE threads._id = new.thread_id; " +
                   "  UPDATE threads SET read = " +
                   "    CASE (SELECT COUNT(*)" +
                   "          FROM pdu" +
                   "          WHERE read = 0 AND thread_id = threads._id)" +
                   "      WHEN 0 THEN 1 ELSE 0" +
                   "    END" +
                   "  WHERE threads._id = new.thread_id; " +
                   "END;");

        db.execSQL("CREATE TRIGGER sms_update_thread_on_insert AFTER INSERT ON sms " +
                   "BEGIN" +
                   "  UPDATE threads SET" +
                   "    date = (strftime('%s','now') * 1000), " +
                   "    snippet = new.body," +
                   "    snippet_cs = 0" +
                   "  WHERE threads._id = new.thread_id; " +
                   "  UPDATE threads SET message_count = " +
                   "     (SELECT COUNT(sms._id) FROM sms LEFT JOIN threads " +
                   "      ON threads._id = thread_id" +
                   "      WHERE thread_id = new.thread_id" +
                   "        AND sms.type != 3) + " +
                   "     (SELECT COUNT(pdu._id) FROM pdu LEFT JOIN threads " +
                   "      ON threads._id = thread_id" +
                   "      WHERE thread_id = new.thread_id" +
                   "        AND (m_type=132 OR m_type=130 OR m_type=128)" +
                   "        AND msg_box != 3) " +
                   "  WHERE threads._id = new.thread_id; " +
                   "  UPDATE threads SET read = " +
                   "    CASE (SELECT COUNT(*)" +
                   "          FROM sms" +
                   "          WHERE read = 0" +
                   "            AND thread_id = threads._id)" +
                   "      WHEN 0 THEN 1" +
                   "      ELSE 0" +
                   "    END" +
                   "  WHERE threads._id = new.thread_id; " +
                   "END;");

        db.execSQL("CREATE TRIGGER sms_update_thread_on_delete " +
                   "AFTER DELETE ON sms " +
                   "BEGIN " +
                   "  UPDATE threads SET " +
                   "     date = (strftime('%s','now') * 1000) " +
                   "  WHERE threads._id = old.thread_id; " +
                   "  UPDATE threads SET message_count = " +
                   "     (SELECT COUNT(sms._id) FROM sms LEFT JOIN threads " +
                   "      ON threads._id = thread_id" +
                   "      WHERE thread_id = old.thread_id" +
                   "        AND sms.type != 3) + " +
                   "     (SELECT COUNT(pdu._id) FROM pdu LEFT JOIN threads " +
                   "      ON threads._id = thread_id" +
                   "      WHERE thread_id = old.thread_id" +
                   "        AND (m_type=132 OR m_type=130 OR m_type=128)" +
                   "        AND msg_box != 3) " +
                   "  WHERE threads._id = old.thread_id; " +
                   "  UPDATE threads SET" +
                   "    snippet = (SELECT snippet FROM" +
                   "      (SELECT date * 1000 AS date, sub AS snippet," +
                   "         sub_cs AS snippet_cs FROM pdu" +
                   "       UNION SELECT date, body AS snippet, NULL AS snippet_cs" +
                   "         FROM sms) ORDER BY date DESC LIMIT 1) " +
                   "  WHERE threads._id = old.thread_id; " +
                   "  UPDATE threads SET" +
                   "    snippet_cs = (SELECT snippet_cs FROM" +
                   "      (SELECT date * 1000 AS date, sub AS snippet," +
                   "         sub_cs AS snippet_cs FROM pdu" +
                   "       UNION SELECT date, body AS snippet, NULL AS snippet_cs" +
                   "         FROM sms) ORDER BY date DESC LIMIT 1) " +
                   "  WHERE threads._id = old.thread_id; " +
                   "END;");

        db.execSQL("CREATE TRIGGER pdu_update_thread_on_delete " +
                   "AFTER DELETE ON pdu " +
                   "BEGIN " +
                   "  UPDATE threads SET " +
                   "     date = (strftime('%s','now') * 1000)" +
                   "  WHERE threads._id = old.thread_id;" +
                   "  UPDATE threads SET message_count = " +
                   "     (SELECT COUNT(sms._id) FROM sms LEFT JOIN threads " +
                   "      ON threads._id = thread_id" +
                   "      WHERE thread_id = old.thread_id" +
                   "        AND sms.type != 3) + " +
                   "     (SELECT COUNT(pdu._id) FROM pdu LEFT JOIN threads " +
                   "      ON threads._id = thread_id" +
                   "      WHERE thread_id = old.thread_id" +
                   "        AND (m_type=132 OR m_type=130 OR m_type=128)" +
                   "        AND msg_box != 3) " +
                   "  WHERE threads._id = old.thread_id; " +
                   "  UPDATE threads SET" +
                   "    snippet = (SELECT snippet FROM" +
                   "      (SELECT date * 1000 AS date, sub AS snippet," +
                   "         sub_cs AS snippet_cs FROM pdu" +
                   "       UNION SELECT date, body AS snippet, NULL AS snippet_cs" +
                   "         FROM sms) ORDER BY date DESC LIMIT 1) " +
                   "  WHERE threads._id = old.thread_id; " +
                   "  UPDATE threads SET" +
                   "    snippet_cs = (SELECT snippet_cs FROM" +
                   "      (SELECT date * 1000 AS date, sub AS snippet," +
                   "         sub_cs AS snippet_cs FROM pdu" +
                   "       UNION SELECT date, body AS snippet, NULL AS snippet_cs" +
                   "         FROM sms) ORDER BY date DESC LIMIT 1) " +
                   "  WHERE threads._id = old.thread_id; " +
                   "END;");

        db.execSQL("CREATE TRIGGER sms_update_thread_date_subject_on_update AFTER" +
                   "  UPDATE OF date, body, type" +
                   "  ON sms " +
                   "BEGIN" +
                   "  UPDATE threads SET" +
                   "    date = (strftime('%s','now') * 1000), " +
                   "    snippet = new.body," +
                   "    snippet_cs = 0" +
                   "  WHERE threads._id = new.thread_id; " +
                   "  UPDATE threads SET message_count = " +
                   "     (SELECT COUNT(sms._id) FROM sms LEFT JOIN threads " +
                   "      ON threads._id = thread_id" +
                   "      WHERE thread_id = new.thread_id" +
                   "        AND sms.type != 3) + " +
                   "     (SELECT COUNT(pdu._id) FROM pdu LEFT JOIN threads " +
                   "      ON threads._id = thread_id" +
                   "      WHERE thread_id = new.thread_id" +
                   "        AND (m_type=132 OR m_type=130 OR m_type=128)" +
                   "        AND msg_box != 3) " +
                   "  WHERE threads._id = new.thread_id; " +
                   "  UPDATE threads SET read = " +
                   "    CASE (SELECT COUNT(*)" +
                   "          FROM sms" +
                   "          WHERE read = 0" +
                   "            AND thread_id = threads._id)" +
                   "      WHEN 0 THEN 1" +
                   "      ELSE 0" +
                   "    END" +
                   "  WHERE threads._id = new.thread_id; " +
                   "END;");

        db.execSQL("CREATE TRIGGER pdu_update_thread_date_subject_on_update AFTER" +
                   "  UPDATE OF date, sub, msg_box ON pdu " +
                   "  WHEN new.m_type=132 OR new.m_type=130 OR new.m_type=128 " +
                   "BEGIN" +
                   "  UPDATE threads SET" +
                   "    date = (strftime('%s','now') * 1000), " +
                   "    snippet = new.sub, " +
                   "    snippet_cs = new.sub_cs" +
                   "  WHERE threads._id = new.thread_id; " +
                   "  UPDATE threads SET message_count = " +
                   "     (SELECT COUNT(sms._id) FROM sms LEFT JOIN threads " +
                   "      ON threads._id = thread_id" +
                   "      WHERE thread_id = new.thread_id" +
                   "        AND sms.type != 3) + " +
                   "     (SELECT COUNT(pdu._id) FROM pdu LEFT JOIN threads " +
                   "      ON threads._id = thread_id" +
                   "      WHERE thread_id = new.thread_id" +
                   "        AND (m_type=132 OR m_type=130 OR m_type=128)" +
                   "        AND msg_box != 3) " +
                   "  WHERE threads._id = new.thread_id; " +
                   "  UPDATE threads SET read = " +
                   "    CASE (SELECT COUNT(*)" +
                   "          FROM pdu" +
                   "          WHERE read = 0 AND thread_id = threads._id)" +
                   "      WHEN 0 THEN 1 ELSE 0" +
                   "    END" +
                   "  WHERE threads._id = new.thread_id; " +
                   "END;");
    }

    private void upgradeDatabaseToVersion36(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE IF NOT EXISTS drm (_id INTEGER PRIMARY KEY, _data TEXT);");
        db.execSQL("CREATE TRIGGER IF NOT EXISTS drm_file_cleanup DELETE ON drm " +
                   "BEGIN SELECT _DELETE_FILE(old._data); END;");
    }

    private void upgradeDatabaseToVersion37(SQLiteDatabase db) {
        db.execSQL("DROP TRIGGER IF EXISTS sms_update_thread_on_delete");
        db.execSQL("DROP TRIGGER IF EXISTS pdu_update_thread_on_delete");

        db.execSQL("CREATE TRIGGER sms_update_thread_on_delete " +
                   "AFTER DELETE ON sms " +
                   "BEGIN " +
                   "  UPDATE threads SET " +
                   "     date = (strftime('%s','now') * 1000) " +
                   "  WHERE threads._id = old.thread_id; " +
                   "  UPDATE threads SET message_count = " +
                   "     (SELECT COUNT(sms._id) FROM sms LEFT JOIN threads " +
                   "      ON threads._id = thread_id" +
                   "      WHERE thread_id = old.thread_id" +
                   "        AND sms.type != 3) + " +
                   "     (SELECT COUNT(pdu._id) FROM pdu LEFT JOIN threads " +
                   "      ON threads._id = thread_id" +
                   "      WHERE thread_id = old.thread_id" +
                   "        AND (m_type=132 OR m_type=130 OR m_type=128)" +
                   "        AND msg_box != 3) " +
                   "  WHERE threads._id = old.thread_id; " +
                   "  UPDATE threads SET snippet = " +
                   "   (SELECT snippet FROM" +
                   "     (SELECT date * 1000 AS date, sub AS snippet, thread_id FROM pdu" +
                   "      UNION SELECT date, body AS snippet, thread_id FROM sms)" +
                   "    WHERE thread_id = OLD.thread_id ORDER BY date DESC LIMIT 1) " +
                   "  WHERE threads._id = OLD.thread_id; " +
                   "  UPDATE threads SET snippet_cs = " +
                   "   (SELECT snippet_cs FROM" +
                   "     (SELECT date * 1000 AS date, sub_cs AS snippet_cs, thread_id FROM pdu" +
                   "      UNION SELECT date, 0 AS snippet_cs, thread_id FROM sms)" +
                   "    WHERE thread_id = OLD.thread_id ORDER BY date DESC LIMIT 1) " +
                   "  WHERE threads._id = OLD.thread_id; " +
                   "END;");

        db.execSQL("CREATE TRIGGER pdu_update_thread_on_delete " +
                   "AFTER DELETE ON pdu " +
                   "BEGIN " +
                   "  UPDATE threads SET " +
                   "     date = (strftime('%s','now') * 1000)" +
                   "  WHERE threads._id = old.thread_id;" +
                   "  UPDATE threads SET message_count = " +
                   "     (SELECT COUNT(sms._id) FROM sms LEFT JOIN threads " +
                   "      ON threads._id = thread_id" +
                   "      WHERE thread_id = old.thread_id" +
                   "        AND sms.type != 3) + " +
                   "     (SELECT COUNT(pdu._id) FROM pdu LEFT JOIN threads " +
                   "      ON threads._id = thread_id" +
                   "      WHERE thread_id = old.thread_id" +
                   "        AND (m_type=132 OR m_type=130 OR m_type=128)" +
                   "        AND msg_box != 3) " +
                   "  WHERE threads._id = old.thread_id; " +
                   "  UPDATE threads SET snippet = " +
                   "   (SELECT snippet FROM" +
                   "     (SELECT date * 1000 AS date, sub AS snippet, thread_id FROM pdu" +
                   "      UNION SELECT date, body AS snippet, thread_id FROM sms)" +
                   "    WHERE thread_id = OLD.thread_id ORDER BY date DESC LIMIT 1) " +
                   "  WHERE threads._id = OLD.thread_id; " +
                   "  UPDATE threads SET snippet_cs = " +
                   "   (SELECT snippet_cs FROM" +
                   "     (SELECT date * 1000 AS date, sub_cs AS snippet_cs, thread_id FROM pdu" +
                   "      UNION SELECT date, 0 AS snippet_cs, thread_id FROM sms)" +
                   "    WHERE thread_id = OLD.thread_id ORDER BY date DESC LIMIT 1) " +
                   "  WHERE threads._id = OLD.thread_id; " +
                   "END;");

        db.execSQL("CREATE TABLE temp_part (" +
                   "_id INTEGER PRIMARY KEY," +
                   "mid INTEGER," +
                   "seq INTEGER DEFAULT 0," +
                   "ct TEXT," +
                   "name TEXT," +
                   "chset INTEGER," +
                   "cd TEXT," +
                   "fn TEXT," +
                   "cid TEXT," +
                   "cl TEXT," +
                   "ctt_s INTEGER," +
                   "ctt_t TEXT," +
                   "_data TEXT);");
        db.execSQL("INSERT INTO temp_part SELECT * FROM part;");
        db.execSQL("UPDATE temp_part SET seq='0';");
        db.execSQL("UPDATE temp_part SET seq='-1' WHERE ct='application/smil';");
        db.execSQL("DROP TABLE IF EXISTS part;");
        db.execSQL("ALTER TABLE temp_part RENAME TO part;");
    }

    private void upgradeDatabaseToVersion38(SQLiteDatabase db) {
        db.execSQL("DROP TRIGGER IF EXISTS part_file_cleanup;");
        db.execSQL("DROP TRIGGER IF EXISTS drm_file_cleanup;");
    }

    private void upgradeDatabaseToVersion39(SQLiteDatabase db) {
        db.execSQL("DROP TRIGGER IF EXISTS sms_update_thread_on_insert");
        db.execSQL("DROP TRIGGER IF EXISTS sms_update_thread_date_subject_on_update");
        db.execSQL("DROP TRIGGER IF EXISTS pdu_update_thread_on_insert");
        db.execSQL("DROP TRIGGER IF EXISTS pdu_update_thread_date_subject_on_update");

        db.execSQL("CREATE TRIGGER sms_update_thread_on_insert AFTER INSERT ON sms " +
                "BEGIN" +
                "  UPDATE threads SET" +
                "    date = (strftime('%s','now') * 1000), " +
                "    snippet = new.body," +
                "    snippet_cs = 0" +
                "  WHERE threads._id = new.thread_id; " +
                "  UPDATE threads SET message_count = " +
                "     (SELECT COUNT(sms._id) FROM sms LEFT JOIN threads " +
                "      ON threads._id = thread_id" +
                "      WHERE thread_id = new.thread_id" +
                "        AND sms.type != 3) + " +
                "     (SELECT COUNT(pdu._id) FROM pdu LEFT JOIN threads " +
                "      ON threads._id = thread_id" +
                "      WHERE thread_id = new.thread_id" +
                "        AND (m_type=132 OR m_type=130 OR m_type=128)" +
                "        AND msg_box != 3 " +
                "        AND pdu.m_id is NULL) + " +
                "     (SELECT COUNT(DISTINCT pdu.m_id) FROM pdu LEFT JOIN threads " +
                "      ON threads._id = thread_id" +
                "      WHERE thread_id = new.thread_id" +
                "        AND (m_type=132 OR m_type=130 OR m_type=128)" +
                "        AND msg_box != 3 " +
                "        AND pdu.m_id is not NULL) " +
                "  WHERE threads._id = new.thread_id; " +
                "  UPDATE threads SET read = " +
                "    CASE (SELECT COUNT(*)" +
                "          FROM sms" +
                "          WHERE read = 0" +
                "            AND thread_id = threads._id)" +
                "      WHEN 0 THEN 1" +
                "      ELSE 0" +
                "    END" +
                "  WHERE threads._id = new.thread_id; " +
                "END;");

        db.execSQL("CREATE TRIGGER sms_update_thread_date_subject_on_update AFTER" +
                "  UPDATE OF date, body, type" +
                "  ON sms " +
                "BEGIN" +
                "  UPDATE threads SET" +
                "    date = (strftime('%s','now') * 1000), " +
                "    snippet = new.body," +
                "    snippet_cs = 0" +
                "  WHERE threads._id = new.thread_id; " +
                "  UPDATE threads SET message_count = " +
                "     (SELECT COUNT(sms._id) FROM sms LEFT JOIN threads " +
                "      ON threads._id = thread_id" +
                "      WHERE thread_id = new.thread_id" +
                "        AND sms.type != 3) + " +
                "     (SELECT COUNT(pdu._id) FROM pdu LEFT JOIN threads " +
                "      ON threads._id = thread_id" +
                "      WHERE thread_id = new.thread_id" +
                "        AND (m_type=132 OR m_type=130 OR m_type=128)" +
                "        AND msg_box != 3 " +
                "        AND pdu.m_id is NULL) + " +
                "     (SELECT COUNT(DISTINCT pdu.m_id) FROM pdu LEFT JOIN threads " +
                "      ON threads._id = thread_id" +
                "      WHERE thread_id = new.thread_id" +
                "        AND (m_type=132 OR m_type=130 OR m_type=128)" +
                "        AND msg_box != 3 " +
                "        AND pdu.m_id is not NULL) " +
                "  WHERE threads._id = new.thread_id; " +
                "  UPDATE threads SET read = " +
                "    CASE (SELECT COUNT(*)" +
                "          FROM sms" +
                "          WHERE read = 0" +
                "            AND thread_id = threads._id)" +
                "      WHEN 0 THEN 1" +
                "      ELSE 0" +
                "    END" +
                "  WHERE threads._id = new.thread_id; " +
                "END;");

        db.execSQL("CREATE TRIGGER pdu_update_thread_on_insert AFTER INSERT ON pdu " +
                "  WHEN new.m_type=132 OR new.m_type=130 OR new.m_type=128 " +
                "BEGIN" +
                "  UPDATE threads SET" +
                "    date = (strftime('%s','now') * 1000), " +
                "    snippet = new.sub, " +
                "    snippet_cs = new.sub_cs" +
                "  WHERE threads._id = new.thread_id; " +
                "  UPDATE threads SET message_count = " +
                "     (SELECT COUNT(sms._id) FROM sms LEFT JOIN threads " +
                "      ON threads._id = thread_id" +
                "      WHERE thread_id = new.thread_id" +
                "        AND sms.type != 3) + " +
                "     (SELECT COUNT(pdu._id) FROM pdu LEFT JOIN threads " +
                "      ON threads._id = thread_id" +
                "      WHERE thread_id = new.thread_id" +
                "        AND (m_type=132 OR m_type=130 OR m_type=128)" +
                "        AND msg_box != 3 " +
                "        AND pdu.m_id is NULL) + " +
                "     (SELECT COUNT(DISTINCT pdu.m_id) FROM pdu LEFT JOIN threads " +
                "      ON threads._id = thread_id" +
                "      WHERE thread_id = new.thread_id" +
                "        AND (m_type=132 OR m_type=130 OR m_type=128)" +
                "        AND msg_box != 3 " +
                "        AND pdu.m_id is not NULL) " +
                "  WHERE threads._id = new.thread_id; " +
                "  UPDATE threads SET read = " +
                "    CASE (SELECT COUNT(*)" +
                "          FROM pdu" +
                "          WHERE read = 0 AND thread_id = threads._id)" +
                "      WHEN 0 THEN 1 ELSE 0" +
                "    END" +
                "  WHERE threads._id = new.thread_id; " +
                "END;");

        db.execSQL("CREATE TRIGGER pdu_update_thread_date_subject_on_update AFTER" +
                "  UPDATE OF date, sub, msg_box ON pdu " +
                "  WHEN new.m_type=132 OR new.m_type=130 OR new.m_type=128 " +
                "BEGIN" +
                "  UPDATE threads SET" +
                "    date = (strftime('%s','now') * 1000), " +
                "    snippet = new.sub, " +
                "    snippet_cs = new.sub_cs" +
                "  WHERE threads._id = new.thread_id; " +
                "  UPDATE threads SET message_count = " +
                "     (SELECT COUNT(sms._id) FROM sms LEFT JOIN threads " +
                "      ON threads._id = thread_id" +
                "      WHERE thread_id = new.thread_id" +
                "        AND sms.type != 3) + " +
                "     (SELECT COUNT(pdu._id) FROM pdu LEFT JOIN threads " +
                "      ON threads._id = thread_id" +
                "      WHERE thread_id = new.thread_id" +
                "        AND (m_type=132 OR m_type=130 OR m_type=128)" +
                "        AND msg_box != 3 " +
                "        AND pdu.m_id is NULL) + " +
                "     (SELECT COUNT(DISTINCT pdu.m_id) FROM pdu LEFT JOIN threads " +
                "      ON threads._id = thread_id" +
                "      WHERE thread_id = new.thread_id" +
                "        AND (m_type=132 OR m_type=130 OR m_type=128)" +
                "        AND msg_box != 3 " +
                "        AND pdu.m_id is not NULL) " +
                "  WHERE threads._id = new.thread_id; " +
                "  UPDATE threads SET read = " +
                "    CASE (SELECT COUNT(*)" +
                "          FROM pdu" +
                "          WHERE read = 0 AND thread_id = threads._id)" +
                "      WHEN 0 THEN 1 ELSE 0" +
                "    END" +
                "  WHERE threads._id = new.thread_id; " +
                "END;");
    }

    private void upgradeDatabaseToVersion40(SQLiteDatabase db) {
        db.execSQL("DROP TRIGGER IF EXISTS update_threads_error_on_move_mms");
        db.execSQL("CREATE TRIGGER update_threads_error_on_move_mms " +
                   "  BEFORE UPDATE OF msg_box ON pdu " +
                   "  WHEN (OLD.msg_box = 4 AND NEW.msg_box != 4) " +
                   "  AND (OLD._id IN (SELECT DISTINCT msg_id" +
                   "                   FROM pending_msgs" +
                   "                   WHERE err_type >= 10)) " +
                   "BEGIN " +
                   "  UPDATE threads SET error = error - 1" +
                   "  WHERE _id = OLD.thread_id; " +
                   "END;");
    }

    private void upgradeDatabaseToVersion41(SQLiteDatabase db) {
        // To align the database version on mainline and TC3 branch.
    }
}
