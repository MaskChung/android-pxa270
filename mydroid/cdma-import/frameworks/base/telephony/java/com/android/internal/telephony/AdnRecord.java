/*
 * Copyright (C) 2006 The Android Open Source Project
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

package com.android.internal.telephony;

import android.os.Parcel;
import android.os.Parcelable;
import android.telephony.PhoneNumberUtils;
import android.util.Log;

import com.android.internal.telephony.GsmAlphabet;


/**
 *
 * Used to load or store ADNs (Abbreviated Dialing Numbers).
 *
 * {@hide}
 *
 */
public class AdnRecord implements Parcelable {
    static final String LOG_TAG = "GSM";

    //***** Instance Variables

    String alphaTag = "";
    String number = "";
    int extRecord = 0xff;
    int efid;                   // or 0 if none
    int recordNumber;           // or 0 if none


    //***** Constants

    // In an ADN record, everything but the alpha identifier
    // is in a footer that's 14 bytes
    static final int FOOTER_SIZE_BYTES = 14;

    // Maximum size of the un-extended number field
    static final int MAX_NUMBER_SIZE_BYTES = 11;

    static final int EXT_RECORD_LENGTH_BYTES = 13;
    static final int EXT_RECORD_TYPE_ADDITIONAL_DATA = 2;
    static final int EXT_RECORD_TYPE_MASK = 3;
    static final int MAX_EXT_CALLED_PARTY_LENGTH = 0xa;

    // ADN offset
    static final int ADN_BCD_NUMBER_LENGTH = 0;
    static final int ADN_TON_AND_NPI = 1;
    static final int ADN_DAILING_NUMBER_START = 2;
    static final int ADN_DAILING_NUMBER_END = 11;
    static final int ADN_CAPABILITY_ID = 12;
    static final int ADN_EXTENSION_ID = 13;

    //***** Static Methods

    public static final Parcelable.Creator<AdnRecord> CREATOR
            = new Parcelable.Creator<AdnRecord>() {
        public AdnRecord createFromParcel(Parcel source) {
            int efid;
            int recordNumber;
            String alphaTag;
            String number;

            efid = source.readInt();
            recordNumber = source.readInt();
            alphaTag = source.readString();
            number = source.readString();

            return new AdnRecord(efid, recordNumber, alphaTag, number);
        }

        public AdnRecord[] newArray(int size) {
            return new AdnRecord[size];
        }
    };


    //***** Constructor
    public
    AdnRecord (byte[] record) {
        this(0, 0, record);
    }

    public
    AdnRecord (int efid, int recordNumber, byte[] record) {
        this.efid = efid;
        this.recordNumber = recordNumber;
        parseRecord(record);
    }

    public
    AdnRecord (String alphaTag, String number) {
        this(0, 0, alphaTag, number);
    }

    public
    AdnRecord (int efid, int recordNumber, String alphaTag, String number) {
        this.efid = efid;
        this.recordNumber = recordNumber;
        this.alphaTag = alphaTag;
        this.number = number;
    }

    //***** Instance Methods

    public String getAlphaTag() {
        return alphaTag;
    }

    public String getNumber() {
        return number;
    }

    public String toString() {
        return "ADN Record '" + alphaTag + "' '" + number + "'";
    }

    public boolean isEmpty() {
        return alphaTag.equals("") && number.equals("");
    }

    public boolean hasExtendedRecord() {
        return extRecord != 0 && extRecord != 0xff;
    }

    public boolean isEqual(AdnRecord adn) {
        return ( alphaTag.equals(adn.getAlphaTag()) &&
                number.equals(adn.getNumber()) );
    }
    //***** Parcelable Implementation

    public int describeContents() {
        return 0;
    }

    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(efid);
        dest.writeInt(recordNumber);
        dest.writeString(alphaTag);
        dest.writeString(number);
    }

    /**
     * Build adn hex byte array based on record size
     * The format of byte array is defined in 51.011 10.5.1
     *
     * @param recordSize is the size X of EF record
     * @return hex byte[recordSize] to be written to EF record
     *          return nulll for wrong format of dialing nubmer or tag
     */
    public byte[] buildAdnString(int recordSize) {
        byte[] bcdNumber;
        byte[] byteTag;
        byte[] adnString = null;
        int footerOffset = recordSize - FOOTER_SIZE_BYTES;

        if (number == null || number.equals("") ||
                alphaTag == null || alphaTag.equals("")) {

            Log.w(LOG_TAG, "[buildAdnString] Empty alpha tag or number");
            adnString = new byte[recordSize];
            for (int i = 0; i < recordSize; i++) {
                adnString[i] = (byte) 0xFF;
            }
        } else if (number.length()
                > (ADN_DAILING_NUMBER_END - ADN_DAILING_NUMBER_START + 1) * 2) {
            Log.w(LOG_TAG,
                    "[buildAdnString] Max length of dailing number is 20");
        } else if (alphaTag.length() > footerOffset) {
            Log.w(LOG_TAG,
                    "[buildAdnString] Max length of tag is " + footerOffset);
        } else {

            adnString = new byte[recordSize];
            for (int i = 0; i < recordSize; i++) {
                adnString[i] = (byte) 0xFF;
            }

            bcdNumber = PhoneNumberUtils.numberToCalledPartyBCD(number);

            System.arraycopy(bcdNumber, 0, adnString,
                    footerOffset + ADN_TON_AND_NPI, bcdNumber.length);

            adnString[footerOffset + ADN_BCD_NUMBER_LENGTH]
                    = (byte) (bcdNumber.length);
            adnString[footerOffset + ADN_CAPABILITY_ID]
                    = (byte) 0xFF; // Capacility Id
            adnString[footerOffset + ADN_EXTENSION_ID]
                    = (byte) 0xFF; // Extension Record Id

            byteTag = GsmAlphabet.stringToGsm8BitPacked(alphaTag);
            System.arraycopy(byteTag, 0, adnString, 0, byteTag.length);

        }

        return adnString;
    }

    /**
     * See TS 51.011 10.5.10
     */
    public void
    appendExtRecord (byte[] extRecord) {
        try {
            if (extRecord.length != EXT_RECORD_LENGTH_BYTES) {
                return;
            }

            if ((extRecord[0] & EXT_RECORD_TYPE_MASK)
                    != EXT_RECORD_TYPE_ADDITIONAL_DATA) {
                return;
            }

            if ((0xff & extRecord[1]) > MAX_EXT_CALLED_PARTY_LENGTH) {
                // invalid or empty record
                return;
            }

            number += PhoneNumberUtils.calledPartyBCDFragmentToString(
                                        extRecord, 2, 0xff & extRecord[1]);

            // We don't support ext record chaining.

        } catch (RuntimeException ex) {
            Log.w(LOG_TAG, "Error parsing AdnRecord ext record", ex);
        }
    }

    //***** Private Methods

    /**
     * alphaTag and number are set to null on invalid format
     */
    private void
    parseRecord(byte[] record) {
        try {
            alphaTag = IccUtils.adnStringFieldToString(
                            record, 0, record.length - FOOTER_SIZE_BYTES);

            int footerOffset = record.length - FOOTER_SIZE_BYTES;

            int numberLength = 0xff & record[footerOffset];

            if (numberLength > MAX_NUMBER_SIZE_BYTES) {
                // Invalid number length
                number = "";
                return;
            }

            // Please note 51.011 10.5.1:
            //
            // "If the Dialling Number/SSC String does not contain
            // a dialling number, e.g. a control string deactivating
            // a service, the TON/NPI byte shall be set to 'FF' by
            // the ME (see note 2)."

            number = PhoneNumberUtils.calledPartyBCDToString(
                            record, footerOffset + 1, numberLength);


            extRecord = 0xff & record[record.length - 1];

        } catch (RuntimeException ex) {
            Log.w(LOG_TAG, "Error parsing AdnRecord", ex);
            number = "";
            alphaTag = "";
        }
    }
}
