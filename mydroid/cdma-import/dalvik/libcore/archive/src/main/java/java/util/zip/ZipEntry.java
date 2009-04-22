/* 
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * 
 *     http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package java.util.zip;

// BEGIN android-added
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.io.UnsupportedEncodingException;
// END android-added
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;

/**
 * ZipEntry represents an entry in a zip file.
 * 
 * @see ZipFile
 * @see ZipInputStream
 */
public class ZipEntry implements ZipConstants, Cloneable {
	String name, comment;

	long compressedSize = -1, crc = -1, size = -1;
// BEGIN android-removed
//     long dataOffset = -1;
// END android-removed

	int compressionMethod = -1, time = -1, modDate = -1;

	byte[] extra;

// BEGIN android-added
    /*
     * Fields, present in the Central Directory Entry and Local File Entry.
     *
     * Not all of these are part of the interface, but we need them if we
     * want to be able to copy entries from one archive to another without
     * losing any meta-data.
     *
     * We use over-sized fields so we can indicate whether a field has been
     * initialized or not.
     */
    private int mVersionMadeBy;             // CDE
    private int mVersionToExtract;          // CDE, LFE
    private int mGPBitFlag;                 // CDE, LFE
//    private int mCompressionMethod;         // CDE, LFE   = compressionMethod
//    private int mLastModFileTime;           // CDE, LFE   = time
//    private int mLastModFileDate;           // CDE, LFE   = modDate
//    private long mCRC32;                    // CDE, LFE   = crc
//    private long mCompressedSize;           // CDE, LFE   = compressedSize
//    private long mUncompressedSize;         // CDE, LFE   = size
    int nameLen, extraLen, commentLen;
    //private int mFileNameLength;            // CDE, LFE
    //private int mExtraFieldLength;          // CDE, LFE
    //private int mFileCommentLength;         // CDE
    private int mDiskNumberStart;           // CDE
    private int mInternalAttrs;             // CDE
    private long mExternalAttrs;            // CDE
    long mLocalHeaderRelOffset;     // CDE  ? dataOffset
//    private String mFileName;               // CDE, LFE   = name
//    private byte[] mExtraField;             // CDE, LFE   = extra
//    private String mFileComment;            // CDE   = comment


    // GPBitFlag 3: uses a Data Descriptor block (need for deflated data)
    /*package*/ static final int USES_DATA_DESCR = 0x0008;

//    private static Calendar mCalendar = Calendar.getInstance();
// END android-added

	/**
	 * Zip entry state: Deflated
	 */
	public static final int DEFLATED = 8;

	/**
	 * Zip entry state: Stored
	 */
	public static final int STORED = 0;

	/**
	 * Constructs a new ZipEntry with the specified name.
	 * 
	 * @param name
	 *            the name of the zip entry
	 */
	public ZipEntry(String name) {
		if (name == null) {
            throw new NullPointerException();
        }
		if (name.length() > 0xFFFF) {
            throw new IllegalArgumentException();
        }
		this.name = name;

// BEGIN android-added
        mVersionMadeBy = 0x0317;        // 03=UNIX, 17=spec v2.3
        mVersionToExtract = 20;         // need deflate, not much else
        mGPBitFlag = 0;
        compressionMethod = -1;
        time = -1;
        modDate = -1;
        crc = -1L;
        compressedSize = -1L;
        size = -1L;
        extraLen = -1;
        nameLen = -1;
        mDiskNumberStart = 0;
        mInternalAttrs = 0;
        mExternalAttrs = 0x81b60020L;       // matches WinZip
        mLocalHeaderRelOffset = -1;
        extra = null;
        comment = null;
// END android-added
	}

	/**
	 * Gets the comment for this ZipEntry.
	 * 
	 * @return the comment for this ZipEntry, or null if there is no comment
     *
     * Note the comment does not live in the
     * LFH, only the CDE.  This means that, if we're reading an archive
     * with ZipInputStream, we won't be able to see the comments.
	 */
	public String getComment() {
		return comment;
	}

	/**
	 * Gets the compressed size of this ZipEntry.
	 * 
	 * @return the compressed size, or -1 if the compressed size has not been
	 *         set
	 */
	public long getCompressedSize() {
		return compressedSize;
	}

	/**
	 * Gets the crc for this ZipEntry.
	 * 
	 * @return the crc, or -1 if the crc has not been set
	 */
	public long getCrc() {
		return crc;
	}

	/**
	 * Gets the extra information for this ZipEntry.
	 * 
	 * @return a byte array containing the extra information, or null if there
	 *         is none
	 */
	public byte[] getExtra() {
		return extra;
	}

	/**
	 * Gets the compression method for this ZipEntry.
	 * 
	 * @return the compression method, either DEFLATED, STORED or -1 if the
	 *         compression method has not been set
	 */
	public int getMethod() {
		return compressionMethod;
	}

	/**
	 * Gets the name of this ZipEntry.
	 * 
	 * @return the entry name
	 */
	public String getName() {
		return name;
	}

	/**
	 * Gets the uncompressed size of this ZipEntry.
	 * 
	 * @return the uncompressed size, or -1 if the size has not been set
	 */
	public long getSize() {
		return size;
	}

	/**
	 * Gets the last modification time of this ZipEntry.
	 * 
	 * @return the last modification time as the number of milliseconds since
	 *         Jan. 1, 1970
	 */
	public long getTime() {
		if (time != -1) {
			GregorianCalendar cal = new GregorianCalendar();
			cal.set(Calendar.MILLISECOND, 0);
			cal.set(1980 + ((modDate >> 9) & 0x7f), ((modDate >> 5) & 0xf) - 1,
					modDate & 0x1f, (time >> 11) & 0x1f, (time >> 5) & 0x3f,
					(time & 0x1f) << 1);
			return cal.getTime().getTime();
		}
		return -1;
	}

	/**
     * Determine whether or not this ZipEntry is a directory.
	 * 
	 * @return <code>true</code> when this ZipEntry is a directory,
	 *         <code>false<code> otherwise
	 */
	public boolean isDirectory() {
		return name.charAt(name.length() - 1) == '/';
	}

	/**
	 * Sets the comment for this ZipEntry.
	 * 
	 * @param string
	 *            the comment
	 */
	public void setComment(String string) {
		if (string == null || string.length() <= 0xFFFF) {
            comment = string;
        } else {
            throw new IllegalArgumentException();
        }
	}

	/**
	 * Sets the compressed size for this ZipEntry.
	 * 
	 * @param value
	 *            the compressed size
	 */
	public void setCompressedSize(long value) {
		compressedSize = value;
	}

	/**
	 * Sets the crc for this ZipEntry.
	 * 
	 * @param value
	 *            the crc
	 * 
	 * @throws IllegalArgumentException
	 *             if value is < 0 or > 0xFFFFFFFFL
	 */
	public void setCrc(long value) {
		if (value >= 0 && value <= 0xFFFFFFFFL) {
            crc = value;
        } else {
            throw new IllegalArgumentException();
        }
	}

	/**
	 * Sets the extra information for this ZipEntry.
	 * 
	 * @param data
	 *            a byte array containing the extra information
	 * 
	 * @throws IllegalArgumentException
	 *             when the length of data is > 0xFFFF bytes
	 */
	public void setExtra(byte[] data) {
		if (data == null || data.length <= 0xFFFF) {
            extra = data;
        } else {
            throw new IllegalArgumentException();
        }
	}

	/**
	 * Sets the compression method for this ZipEntry.
	 * 
	 * @param value
	 *            the compression method, either DEFLATED or STORED
	 * 
	 * @throws IllegalArgumentException
	 *             when value is not DEFLATED or STORED
	 */
	public void setMethod(int value) {
		if (value != STORED && value != DEFLATED) {
            throw new IllegalArgumentException();
        }
		compressionMethod = value;
	}

	/**
	 * Sets the uncompressed size of this ZipEntry.
	 * 
	 * @param value
	 *            the uncompressed size
	 * 
	 * @throws IllegalArgumentException
	 *             if value is < 0 or > 0xFFFFFFFFL
	 */
	public void setSize(long value) {
		if (value >= 0 && value <= 0xFFFFFFFFL) {
            size = value;
        } else {
            throw new IllegalArgumentException();
        }
	}

	/**
	 * Sets the last modification time of this ZipEntry.
	 * 
	 * @param value
	 *            the last modification time as the number of milliseconds since
	 *            Jan. 1, 1970
	 */
	public void setTime(long value) {
		GregorianCalendar cal = new GregorianCalendar();
		cal.setTime(new Date(value));
		int year = cal.get(Calendar.YEAR);
		if (year < 1980) {
			modDate = 0x21;
			time = 0;
		} else {
			modDate = cal.get(Calendar.DATE);
			modDate = (cal.get(Calendar.MONTH) + 1 << 5) | modDate;
			modDate = ((cal.get(Calendar.YEAR) - 1980) << 9) | modDate;
			time = cal.get(Calendar.SECOND) >> 1;
			time = (cal.get(Calendar.MINUTE) << 5) | time;
			time = (cal.get(Calendar.HOUR_OF_DAY) << 11) | time;
		}
	}

	/**
	 * Returns the string representation of this ZipEntry.
	 * 
	 * @return the string representation of this ZipEntry
	 */
	@Override
    public String toString() {
		return name;
	}

// BEGIN android-removed
//	ZipEntry(String name, String comment, byte[] extra, long modTime,
//			long size, long compressedSize, long crc, int compressionMethod,
//			long modDate, long offset) {
//		this.name = name;
//		this.comment = comment;
//		this.extra = extra;
//		time = (int) modTime;
//		this.size = size;
//		this.compressedSize = compressedSize;
//		this.crc = crc;
//		this.compressionMethod = compressionMethod;
//		this.modDate = (int) modDate;
//		dataOffset = offset;
//	}
// END android-removed

	/**
	 * Constructs a new ZipEntry using the values obtained from ze.
	 * 
	 * @param ze
	 *            ZipEntry from which to obtain values.
	 */
	public ZipEntry(ZipEntry ze) {
		name = ze.name;
		comment = ze.comment;
		time = ze.time;
		size = ze.size;
		compressedSize = ze.compressedSize;
		crc = ze.crc;
		compressionMethod = ze.compressionMethod;
		modDate = ze.modDate;
		extra = ze.extra;
// BEGIN android-removed
//		dataOffset = ze.dataOffset;
// END android-removed
// BEGIN android-added
        mVersionMadeBy = ze.mVersionMadeBy;
        mVersionToExtract = ze.mVersionToExtract;
        mGPBitFlag = ze.mGPBitFlag;
        extraLen = ze.extraLen;
        nameLen = ze.nameLen;
        mDiskNumberStart = ze.mDiskNumberStart;
        mInternalAttrs = ze.mInternalAttrs;
        mExternalAttrs = ze.mExternalAttrs;
        mLocalHeaderRelOffset = ze.mLocalHeaderRelOffset;
//        if (e.mExtraField != null)
//            mExtraField = e.mExtraField.clone();
//        else
//            mExtraField = null;
//        mFileComment = e.mFileComment;
// END android-added
	}

	/**
	 * Returns a shallow copy of this entry
	 * 
	 * @return a copy of this entry
	 */
	@Override
    public Object clone() {
		return new ZipEntry(this);
	}
// BEGIN android-added
//    /**
//     * Return a copy of this entry.  The "extra" field requires special
//     * handling; unlike the Strings it's mutable.
//     */
//    public Object clone() {
//        try {
//            ZipEntry clonedEntry = (ZipEntry) super.clone();
//            if (mExtraField != null)
//                clonedEntry.mExtraField = (byte[]) mExtraField.clone();
//            return clonedEntry;
//        }
//        catch (CloneNotSupportedException ex) {
//            /* should never happen */
//            throw new InternalError();
//        }
//    }
// END android-added

	/**
	 * Returns the hashCode for this ZipEntry.
	 * 
	 * @return the hashCode of the entry
	 */
	@Override
    public int hashCode() {
		return name.hashCode();
	}

// BEGIN android-added
    /*
     * Internal constructor.  Creates a new ZipEntry by reading the
     * Central Directory Entry from "in", which must be positioned at
     * the CDE signature.
     *
     * On exit, "in" will be positioned at the start of the next entry.
     */
    /*package*/ ZipEntry(LittleEndianReader ler, InputStream in) throws IOException {

        /*
         * We're seeing performance issues when we call readShortLE and
         * readIntLE, so we're going to read the entire header at once
         * and then parse the results out without using any function calls.
         * Uglier, but should be much faster.
         * 
         * Note that some lines look a bit different, because the corresponding
         * fields or locals are long and so we need to do & 0xffffffffl to avoid
         * problems induced by sign extension.
         */

        byte[] hdrBuf = ler.hdrBuf;
        myReadFully(in, hdrBuf);

        long sig = (hdrBuf[0] & 0xff) | ((hdrBuf[1] & 0xff) << 8) |
            ((hdrBuf[2] & 0xff) << 16) | ((hdrBuf[3] << 24) & 0xffffffffL);
        if (sig != CENSIG)
             throw new ZipException("Central Directory Entry not found");

        mVersionMadeBy = (hdrBuf[4] & 0xff) | ((hdrBuf[5] & 0xff) << 8);
        mVersionToExtract = (hdrBuf[6] & 0xff) | ((hdrBuf[7] & 0xff) << 8);
        mGPBitFlag = (hdrBuf[8] & 0xff) | ((hdrBuf[9] & 0xff) << 8);
        compressionMethod = (hdrBuf[10] & 0xff) | ((hdrBuf[11] & 0xff) << 8);
        time = (hdrBuf[12] & 0xff) | ((hdrBuf[13] & 0xff) << 8);
        modDate = (hdrBuf[14] & 0xff) | ((hdrBuf[15] & 0xff) << 8);
        crc = (hdrBuf[16] & 0xff) | ((hdrBuf[17] & 0xff) << 8)
                | ((hdrBuf[18] & 0xff) << 16)
                | ((hdrBuf[19] << 24) & 0xffffffffL);
        compressedSize = (hdrBuf[20] & 0xff) | ((hdrBuf[21] & 0xff) << 8)
                | ((hdrBuf[22] & 0xff) << 16)
                | ((hdrBuf[23] << 24) & 0xffffffffL);
        size = (hdrBuf[24] & 0xff) | ((hdrBuf[25] & 0xff) << 8)
                | ((hdrBuf[26] & 0xff) << 16)
                | ((hdrBuf[27] << 24) & 0xffffffffL);
        nameLen = (hdrBuf[28] & 0xff) | ((hdrBuf[29] & 0xff) << 8);
        extraLen = (hdrBuf[30] & 0xff) | ((hdrBuf[31] & 0xff) << 8);
        commentLen = (hdrBuf[32] & 0xff) | ((hdrBuf[33] & 0xff) << 8);
        mDiskNumberStart = (hdrBuf[34] & 0xff) | ((hdrBuf[35] & 0xff) << 8);
        mInternalAttrs = (hdrBuf[36] & 0xff) | ((hdrBuf[37] & 0xff) << 8);
        mExternalAttrs = (hdrBuf[38] & 0xff) | ((hdrBuf[39] & 0xff) << 8)
                | ((hdrBuf[40] & 0xff) << 16)
                | ((hdrBuf[41] << 24) & 0xffffffffL);
        mLocalHeaderRelOffset = (hdrBuf[42] & 0xff) | ((hdrBuf[43] & 0xff) << 8)
                | ((hdrBuf[44] & 0xff) << 16)
                | ((hdrBuf[45] << 24) & 0xffffffffL);

        byte[] nameBytes = new byte[nameLen];
        myReadFully(in, nameBytes);

        byte[] commentBytes = null;
        if (commentLen > 0) {
            commentBytes = new byte[commentLen];
            myReadFully(in, commentBytes);
        }

        if (extraLen > 0) {
            extra = new byte[extraLen];
            myReadFully(in, extra);
        }

        try {
            /*
             * The actual character set is "IBM Code Page 437".  As of
             * Sep 2006, the Zip spec (APPNOTE.TXT) supports UTF-8.  When
             * bit 11 of the GP flags field is set, the file name and
             * comment fields are UTF-8.
             *
             * TODO: add correct UTF-8 support.
             */
            name = new String(nameBytes, "ISO-8859-1");
            if (commentBytes != null)
                comment = new String(commentBytes, "ISO-8859-1");
            else
                comment = null;
        }
        catch (UnsupportedEncodingException uee) {
            throw new InternalError(uee.getMessage());
        }
    }
    private void myReadFully(InputStream in, byte[] b) throws IOException {
        int count;
        int len = b.length;
        int off = 0;
    
        while (len > 0) {
            count = in.read(b, off, len);
            if (count <= 0)
                throw new EOFException();
            off += count;
            len -= count;
        }
    }




    /*package*/ void setVersionToExtract(int version) {
        mVersionToExtract = version;
    }

    /*package*/ int getGPBitFlag() {
        return mGPBitFlag;
    }
    /*package*/ void setGPBitFlag(int flags) {
        mGPBitFlag = flags;
    }

    /*package*/ long getLocalHeaderRelOffset() {
        return mLocalHeaderRelOffset;
    }
    /*package*/ void setLocalHeaderRelOffset(long offset) {
        mLocalHeaderRelOffset = offset;
    }

    /*package*/ void setDateTime(int lastModFileDate, int lastModFileTime) {
        time = lastModFileTime;
        modDate = lastModFileDate;
    }



// BEGIN android-added
// Note: readShortLE is not used!
// readIntLE is used only once in ZipFile.
// END android-added

    /*
     * Read a two-byte short in little-endian order.
     *
     * The DataInput interface, which RandomAccessFile implements, provides
     * a readInt() function.  Unfortunately, it's defined to use big-endian.
     */
    /*package*/ static int readShortLE(RandomAccessFile raf) throws IOException
    {
        int b0, b1;

        b0 = raf.read();
        b1 = raf.read();
        if (b1 < 0)
            throw new EOFException("in ZipEntry.readShortLE(RandomAccessFile)");
        return b0 | (b1 << 8);
    }

    /*
     * Read a four-byte int in little-endian order.
     */
    /*package*/ static long readIntLE(RandomAccessFile raf) throws IOException
    {
        int b0, b1, b2, b3;

        b0 = raf.read();
        b1 = raf.read();
        b2 = raf.read();
        b3 = raf.read();
        if (b3 < 0)
            throw new EOFException("in ZipEntry.readIntLE(RandomAccessFile)");
        return b0 | (b1 << 8) | (b2 << 16) | (b3 << 24); // ATTENTION: DOES SIGN EXTENSION: IS THIS WANTED?
    }


// BEGIN android-changed
    static class LittleEndianReader {
        private byte[] b = new byte[4];
        byte[] hdrBuf = new byte[CENHDR];

        /*
         * Read a two-byte short in little-endian order.
         */
        int readShortLE(InputStream in) throws IOException {
            if (in.read(b, 0, 2) == 2) {
                return (b[0] & 0XFF) | ((b[1] & 0XFF) << 8);
            } else {
                throw new EOFException("in ZipEntry.readShortLE(InputStream)");
            }
        }

        /*
         * Read a four-byte int in little-endian order.
         */
        long readIntLE(InputStream in) throws IOException {
            if (in.read(b, 0, 4) == 4) {
                return (   ((b[0] & 0XFF))
                         | ((b[1] & 0XFF) << 8)
                         | ((b[2] & 0XFF) << 16)
                         | ((b[3] & 0XFF) << 24))
                       & 0XFFFFFFFFL; // Here for sure NO sign extension is wanted.
            } else {
                throw new EOFException("in ZipEntry.readIntLE(InputStream)");
            }
        }
    }
// END android-changed


    /*
     * Write a two-byte short in little-endian order.
     */
    /*package*/ static void writeShortLE(OutputStream out, int val)
        throws IOException
    {
        out.write(val & 0xff);
        out.write((val >> 8) & 0xff);
    }

    /*
     * Write a 4-byte int in little-endian order.  This takes a long because
     * all of our 4-byte values are stored locally in longs.
     */
    /*package*/ static void writeIntLE(OutputStream out, long val)
        throws IOException
    {
        if (val < 0)
            throw new InternalError();
        out.write((int) val & 0xff);
        out.write(((int) val >> 8) & 0xff);
        out.write(((int) val >> 16) & 0xff);
        out.write(((int) val >> 24) & 0xff);
    }

    /*
     * Write the Local File Header for this entry to the specified stream.
     *
     * Returns the #of bytes written.
     */
    /*package*/ int writeLFH(OutputStream out) throws IOException {
        if (compressionMethod < 0 ||
            time < 0 ||
            modDate < 0 ||
            crc < 0 ||
            compressedSize < 0 ||
            size < 0)
            throw new InternalError();

        writeIntLE(out, LOCSIG);
        writeShortLE(out, mVersionToExtract);
        writeShortLE(out, mGPBitFlag);
        writeShortLE(out, compressionMethod);
        writeShortLE(out, time);
        writeShortLE(out, modDate);
        writeIntLE(out, crc);
        writeIntLE(out, compressedSize);
        writeIntLE(out, size);

        byte[] nameBytes;
        try {
            nameBytes = name.getBytes("ISO-8859-1");
        }
        catch (UnsupportedEncodingException uee) {
            throw new InternalError(uee.getMessage());
        }

        int extraLen = 0;
        if (extra != null)
            extraLen = extra.length;

        writeShortLE(out, nameBytes.length);
        writeShortLE(out, extraLen);
        out.write(nameBytes);
        if (extra != null)
            out.write(extra);

        return LOCHDR + nameBytes.length + extraLen;
    }

    /*
     * Write the Data Descriptor for this entry to the specified stream.
     *
     * Returns the #of bytes written.
     */
    /*package*/ int writeDD(OutputStream out) throws IOException {
        writeIntLE(out, EXTSIG);
        writeIntLE(out, crc);
        writeIntLE(out, compressedSize);
        writeIntLE(out, size);
        return EXTHDR;
    }

    /*
     * Write the Central Directory Entry for this entry.
     *
     * Returns the #of bytes written.
     */
    /*package*/ int writeCDE(OutputStream out) throws IOException {
        writeIntLE(out, CENSIG);
        writeShortLE(out, mVersionMadeBy);
        writeShortLE(out, mVersionToExtract);
        writeShortLE(out, mGPBitFlag);
        writeShortLE(out, compressionMethod);
        writeShortLE(out, time);
        writeShortLE(out, modDate);
        writeIntLE(out, crc);
        writeIntLE(out, compressedSize);
        writeIntLE(out, size);

        byte[] nameBytes = null, commentBytes = null;
        try {
            nameBytes = name.getBytes("ISO-8859-1");
            if (comment != null)
                commentBytes = comment.getBytes("ISO-8859-1");
        }
        catch (UnsupportedEncodingException uee) {
            throw new InternalError(uee.getMessage());
        }

        int extraLen = 0, commentLen = 0;
        if (extra != null)
            extraLen = extra.length;
        if (commentBytes != null)
            commentLen = commentBytes.length;

        writeShortLE(out, nameBytes.length);
        writeShortLE(out, extraLen);
        writeShortLE(out, commentLen);
        writeShortLE(out, mDiskNumberStart);
        writeShortLE(out, mInternalAttrs);
        writeIntLE(out, mExternalAttrs);
        writeIntLE(out, mLocalHeaderRelOffset);
        out.write(nameBytes);
        if (extra != null)
            out.write(extra);
        if (commentBytes != null)
            out.write(commentBytes);

        return CENHDR + nameBytes.length + extraLen + commentLen;
    }
// END android-added

}
