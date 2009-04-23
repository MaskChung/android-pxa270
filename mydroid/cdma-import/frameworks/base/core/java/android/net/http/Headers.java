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

package android.net.http;

import android.util.Config;
import android.util.Log;

import java.util.ArrayList;

import org.apache.http.HeaderElement;
import org.apache.http.entity.ContentLengthStrategy;
import org.apache.http.message.BasicHeaderValueParser;
import org.apache.http.message.ParserCursor;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.CharArrayBuffer;

/**
 * Manages received headers
 * 
 * {@hide}
 */
public final class Headers {
    private static final String LOGTAG = "Http";

    // header parsing constant
    /**
     * indicate HTTP 1.0 connection close after the response
     */
    public final static int CONN_CLOSE = 1;
    /**
     * indicate HTTP 1.1 connection keep alive 
     */
    public final static int CONN_KEEP_ALIVE = 2;
    
    // initial values.
    public final static int NO_CONN_TYPE = 0;
    public final static long NO_TRANSFER_ENCODING = 0;
    public final static long NO_CONTENT_LENGTH = -1;

    // header string
    public final static String TRANSFER_ENCODING = "transfer-encoding";
    public final static String CONTENT_LEN = "content-length";
    public final static String CONTENT_TYPE = "content-type";
    public final static String CONTENT_ENCODING = "content-encoding";
    public final static String CONN_DIRECTIVE = "connection";

    public final static String LOCATION = "location";
    public final static String PROXY_CONNECTION = "proxy-connection";

    public final static String WWW_AUTHENTICATE = "www-authenticate";
    public final static String PROXY_AUTHENTICATE = "proxy-authenticate";
    public final static String CONTENT_DISPOSITION = "content-disposition";
    public final static String ACCEPT_RANGES = "accept-ranges";
    public final static String EXPIRES = "expires";
    public final static String CACHE_CONTROL = "cache-control";
    public final static String LAST_MODIFIED = "last-modified";
    public final static String ETAG = "etag";
    public final static String SET_COOKIE = "set-cookie";
    public final static String PRAGMA = "pragma";
    public final static String REFRESH = "refresh";

    // following hash are generated by String.hashCode()
    private final static int HASH_TRANSFER_ENCODING = 1274458357;
    private final static int HASH_CONTENT_LEN = -1132779846;
    private final static int HASH_CONTENT_TYPE = 785670158;
    private final static int HASH_CONTENT_ENCODING = 2095084583;
    private final static int HASH_CONN_DIRECTIVE = -775651618;
    private final static int HASH_LOCATION = 1901043637;
    private final static int HASH_PROXY_CONNECTION = 285929373;
    private final static int HASH_WWW_AUTHENTICATE = -243037365;
    private final static int HASH_PROXY_AUTHENTICATE = -301767724;
    private final static int HASH_CONTENT_DISPOSITION = -1267267485;
    private final static int HASH_ACCEPT_RANGES = 1397189435;
    private final static int HASH_EXPIRES = -1309235404;
    private final static int HASH_CACHE_CONTROL = -208775662;
    private final static int HASH_LAST_MODIFIED = 150043680;
    private final static int HASH_ETAG = 3123477;
    private final static int HASH_SET_COOKIE = 1237214767;
    private final static int HASH_PRAGMA = -980228804;
    private final static int HASH_REFRESH = 1085444827;

    private long transferEncoding;
    private long contentLength; // Content length of the incoming data
    private int connectionType;

    private String contentType;
    private String contentEncoding;
    private String location;
    private String wwwAuthenticate;
    private String proxyAuthenticate;
    private String contentDisposition;
    private String acceptRanges;
    private String expires;
    private String cacheControl;
    private String lastModified;
    private String etag;
    private String pragma;
    private String refresh;
    private ArrayList<String> cookies = new ArrayList<String>(2);

    public Headers() {
        transferEncoding = NO_TRANSFER_ENCODING;
        contentLength = NO_CONTENT_LENGTH;
        connectionType = NO_CONN_TYPE;
    }

    public void parseHeader(CharArrayBuffer buffer) {
        int pos = CharArrayBuffers.setLowercaseIndexOf(buffer, ':');
        if (pos == -1) {
            return;
        }
        String name = buffer.substringTrimmed(0, pos);
        if (name.length() == 0) {
            return;
        }
        pos++;

        if (HttpLog.LOGV) {
            String val = buffer.substringTrimmed(pos, buffer.length());
            HttpLog.v("hdr " + buffer.length() + " " + buffer);
        }

        switch (name.hashCode()) {
        case HASH_TRANSFER_ENCODING:
            if (name.equals(TRANSFER_ENCODING)) {
                // headers.transferEncoding =
                HeaderElement[] encodings = BasicHeaderValueParser.DEFAULT
                        .parseElements(buffer, new ParserCursor(pos, 
                                buffer.length()));
                // The chunked encoding must be the last one applied RFC2616,
                // 14.41
                int len = encodings.length;
                if (HTTP.IDENTITY_CODING.equalsIgnoreCase(buffer
                        .substringTrimmed(pos, buffer.length()))) {
                    transferEncoding = ContentLengthStrategy.IDENTITY;
                } else if ((len > 0)
                        && (HTTP.CHUNK_CODING
                                .equalsIgnoreCase(encodings[len - 1].getName()))) {
                    transferEncoding = ContentLengthStrategy.CHUNKED;
                } else {
                    transferEncoding = ContentLengthStrategy.IDENTITY;
                }
            }
            break;
        case HASH_CONTENT_LEN:
            if (name.equals(CONTENT_LEN)) {
                try {
                    contentLength = Long.parseLong(buffer.substringTrimmed(pos,
                            buffer.length()));
                } catch (NumberFormatException e) {
                    if (Config.LOGV) {
                        Log.v(LOGTAG, "Headers.headers(): error parsing"
                                + " content length: " + buffer.toString());
                    }
                }
            }
            break;
        case HASH_CONTENT_TYPE:
            if (name.equals(CONTENT_TYPE)) {
                contentType = buffer.substringTrimmed(pos, buffer.length());
            }
            break;
        case HASH_CONTENT_ENCODING:
            if (name.equals(CONTENT_ENCODING)) {
                contentEncoding = buffer.substringTrimmed(pos, buffer.length());
            }
            break;
        case HASH_CONN_DIRECTIVE:
            if (name.equals(CONN_DIRECTIVE)) {
                setConnectionType(buffer, pos);
            }
            break;
        case HASH_LOCATION:
            if (name.equals(LOCATION)) {
                location = buffer.substringTrimmed(pos, buffer.length());
            }
            break;
        case HASH_PROXY_CONNECTION:
            if (name.equals(PROXY_CONNECTION)) {
                setConnectionType(buffer, pos);
            }
            break;
        case HASH_WWW_AUTHENTICATE:
            if (name.equals(WWW_AUTHENTICATE)) {
                wwwAuthenticate = buffer.substringTrimmed(pos, buffer.length());
            }
            break;
        case HASH_PROXY_AUTHENTICATE:
            if (name.equals(PROXY_AUTHENTICATE)) {
                proxyAuthenticate = buffer.substringTrimmed(pos, buffer
                        .length());
            }
            break;
        case HASH_CONTENT_DISPOSITION:
            if (name.equals(CONTENT_DISPOSITION)) {
                contentDisposition = buffer.substringTrimmed(pos, buffer
                        .length());
            }
            break;
        case HASH_ACCEPT_RANGES:
            if (name.equals(ACCEPT_RANGES)) {
                acceptRanges = buffer.substringTrimmed(pos, buffer.length());
            }
            break;
        case HASH_EXPIRES:
            if (name.equals(EXPIRES)) {
                expires = buffer.substringTrimmed(pos, buffer.length());
            }
            break;
        case HASH_CACHE_CONTROL:
            if (name.equals(CACHE_CONTROL)) {
                cacheControl = buffer.substringTrimmed(pos, buffer.length());
            }
            break;
        case HASH_LAST_MODIFIED:
            if (name.equals(LAST_MODIFIED)) {
                lastModified = buffer.substringTrimmed(pos, buffer.length());
            }
            break;
        case HASH_ETAG:
            if (name.equals(ETAG)) {
                etag = buffer.substringTrimmed(pos, buffer.length());
            }
            break;
        case HASH_SET_COOKIE:
            if (name.equals(SET_COOKIE)) {
                cookies.add(buffer.substringTrimmed(pos, buffer.length()));
            }
            break;
        case HASH_PRAGMA:
            if (name.equals(PRAGMA)) {
                pragma = buffer.substringTrimmed(pos, buffer.length());
            }
            break;
        case HASH_REFRESH:
            if (name.equals(REFRESH)) {
                refresh = buffer.substringTrimmed(pos, buffer.length());
            }
            break;
        default:
            // ignore
        }
    }

    public long getTransferEncoding() {
        return transferEncoding;
    }

    public long getContentLength() {
        return contentLength;
    }

    public int getConnectionType() {
        return connectionType;
    }

    private void setConnectionType(CharArrayBuffer buffer, int pos) {
        if (CharArrayBuffers.containsIgnoreCaseTrimmed(
                buffer, pos, HTTP.CONN_CLOSE)) {
            connectionType = CONN_CLOSE;
        } else if (CharArrayBuffers.containsIgnoreCaseTrimmed(
                buffer, pos, HTTP.CONN_KEEP_ALIVE)) {
            connectionType = CONN_KEEP_ALIVE;
        }
    }

    public String getContentType() {
        return this.contentType;
    }

    public String getContentEncoding() {
        return this.contentEncoding;
    }

    public String getLocation() {
        return this.location;
    }

    public String getWwwAuthenticate() {
        return this.wwwAuthenticate;
    }

    public String getProxyAuthenticate() {
        return this.proxyAuthenticate;
    }

    public String getContentDisposition() {
        return this.contentDisposition;
    }

    public String getAcceptRanges() {
        return this.acceptRanges;
    }

    public String getExpires() {
        return this.expires;
    }

    public String getCacheControl() {
        return this.cacheControl;
    }

    public String getLastModified() {
        return this.lastModified;
    }

    public String getEtag() {
        return this.etag;
    }

    public ArrayList<String> getSetCookie() {
        return this.cookies;
    }
    
    public String getPragma() {
        return this.pragma;
    }
    
    public String getRefresh() {
        return this.refresh;
    }

    public void setContentLength(long value) {
        this.contentLength = value;
    }

    public void setContentType(String value) {
        this.contentType = value;
    }

    public void setContentEncoding(String value) {
        this.contentEncoding = value;
    }

    public void setLocation(String value) {
        this.location = value;
    }

    public void setWwwAuthenticate(String value) {
        this.wwwAuthenticate = value;
    }

    public void setProxyAuthenticate(String value) {
        this.proxyAuthenticate = value;
    }

    public void setContentDisposition(String value) {
        this.contentDisposition = value;
    }

    public void setAcceptRanges(String value) {
        this.acceptRanges = value;
    }

    public void setExpires(String value) {
        this.expires = value;
    }

    public void setCacheControl(String value) {
        this.cacheControl = value;
    }

    public void setLastModified(String value) {
        this.lastModified = value;
    }

    public void setEtag(String value) {
        this.etag = value;
    }
}