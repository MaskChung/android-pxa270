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

package android.net.http;

import android.content.Context;

import junit.framework.Assert;

import java.io.IOException;

import java.security.cert.X509Certificate;

import java.net.Socket;
import java.net.InetSocketAddress;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.apache.http.Header;
import org.apache.http.HttpClientConnection;
import org.apache.http.HttpException;
import org.apache.http.HttpHost;
import org.apache.http.HttpRequest;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.ParseException;
import org.apache.http.ProtocolVersion;
import org.apache.http.StatusLine;
import org.apache.http.impl.DefaultHttpClientConnection;
import org.apache.http.message.BasicHttpRequest;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpParams;
import org.apache.http.params.HttpConnectionParams;

/**
 * Simple exception we throw if the SSL connection is closed by the user.
 * 
 * {@hide}
 */
class SSLConnectionClosedByUserException extends SSLException {

    public SSLConnectionClosedByUserException(String reason) {
        super(reason);
    }
}

/**
 * A Connection connecting to a secure http server or tunneling through
 * a http proxy server to a https server.
 */
class HttpsConnection extends Connection {

    /**
     * SSL context
     */
    private static SSLContext mSslContext = null;

    /**
     * SSL socket factory
     */
    private static SSLSocketFactory mSslSocketFactory = null;

    static {
        // initialize the socket factory
        try {
            mSslContext = SSLContext.getInstance("TLS");
            if (mSslContext != null) {
                // here, trust managers is a single trust-all manager
                TrustManager[] trustManagers = new TrustManager[] {
                    new X509TrustManager() {
                        public X509Certificate[] getAcceptedIssuers() {
                            return null;
                        }

                        public void checkClientTrusted(
                            X509Certificate[] certs, String authType) {
                        }

                        public void checkServerTrusted(
                            X509Certificate[] certs, String authType) {
                        }
                    }
                };

                mSslContext.init(null, trustManagers, null);
                mSslSocketFactory = mSslContext.getSocketFactory();
            }
        } catch (Exception t) {
            if (HttpLog.LOGV) {
                HttpLog.v("HttpsConnection: failed to initialize the socket factory");
            }
        }
    }

    /**
     * @return The shared SSL context.
     */
    /*package*/ static SSLContext getContext() {
        return mSslContext;
    }

    /**
     * Object to wait on when suspending the SSL connection
     */
    private Object mSuspendLock = new Object();

    /**
     * True if the connection is suspended pending the result of asking the
     * user about an error.
     */
    private boolean mSuspended = false;

    /**
     * True if the connection attempt should be aborted due to an ssl
     * error.
     */
    private boolean mAborted = false;

    /**
     * Contructor for a https connection.
     */
    HttpsConnection(Context context, HttpHost host,
                    RequestQueue.ConnectionManager connectionManager,
                    RequestFeeder requestFeeder) {
        super(context, host, connectionManager, requestFeeder);
    }

    /**
     * Sets the server SSL certificate associated with this
     * connection.
     * @param certificate The SSL certificate
     */
    /* package */ void setCertificate(SslCertificate certificate) {
        mCertificate = certificate;
    }

    /**
     * Opens the connection to a http server or proxy.
     *
     * @return the opened low level connection
     * @throws IOException if the connection fails for any reason.
     */
    @Override
    AndroidHttpClientConnection openConnection(Request req) throws IOException {
        SSLSocket sslSock = null;

        HttpHost proxyHost = mConnectionManager.getProxyHost();
        if (proxyHost != null) {
            // If we have a proxy set, we first send a CONNECT request
            // to the proxy; if the proxy returns 200 OK, we negotiate
            // a secure connection to the target server via the proxy.
            // If the request fails, we drop it, but provide the event
            // handler with the response status and headers. The event
            // handler is then responsible for cancelling the load or
            // issueing a new request.
            AndroidHttpClientConnection proxyConnection = null;
            Socket proxySock = null;
            try {
                proxySock = new Socket
                    (proxyHost.getHostName(), proxyHost.getPort());

                proxySock.setSoTimeout(60 * 1000);

                proxyConnection = new AndroidHttpClientConnection();
                HttpParams params = new BasicHttpParams();
                HttpConnectionParams.setSocketBufferSize(params, 8192);

                proxyConnection.bind(proxySock, params);
            } catch(IOException e) {
                if (proxyConnection != null) {
                    proxyConnection.close();
                }

                String errorMessage = e.getMessage();
                if (errorMessage == null) {
                    errorMessage =
                        "failed to establish a connection to the proxy";
                }

                throw new IOException(errorMessage);
            }

            StatusLine statusLine = null;
            int statusCode = 0;
            Headers headers = new Headers();
            try {
                BasicHttpRequest proxyReq = new BasicHttpRequest
                    ("CONNECT", mHost.toHostString());

                // add all 'proxy' headers from the original request
                for (Header h : req.mHttpRequest.getAllHeaders()) {
                    String headerName = h.getName().toLowerCase();
                    if (headerName.startsWith("proxy") || headerName.equals("keep-alive")) {
                        proxyReq.addHeader(h);
                    }
                }

                proxyConnection.sendRequestHeader(proxyReq);
                proxyConnection.flush();

                // it is possible to receive informational status
                // codes prior to receiving actual headers;
                // all those status codes are smaller than OK 200
                // a loop is a standard way of dealing with them
                do {
                    statusLine = proxyConnection.parseResponseHeader(headers);
                    statusCode = statusLine.getStatusCode();
                } while (statusCode < HttpStatus.SC_OK);
            } catch (ParseException e) {
                String errorMessage = e.getMessage();
                if (errorMessage == null) {
                    errorMessage =
                        "failed to send a CONNECT request";
                }

                throw new IOException(errorMessage);
            } catch (HttpException e) {
                String errorMessage = e.getMessage();
                if (errorMessage == null) {
                    errorMessage =
                        "failed to send a CONNECT request";
                }

                throw new IOException(errorMessage);
            } catch (IOException e) {
                String errorMessage = e.getMessage();
                if (errorMessage == null) {
                    errorMessage =
                        "failed to send a CONNECT request";
                }

                throw new IOException(errorMessage);
            }

            if (statusCode == HttpStatus.SC_OK) {
                try {
                    synchronized (mSslSocketFactory) {
                        sslSock = (SSLSocket) mSslSocketFactory.createSocket(
                            proxySock, mHost.getHostName(), mHost.getPort(), true);
                    }
                } catch(IOException e) {
                    if (sslSock != null) {
                        sslSock.close();
                    }

                    String errorMessage = e.getMessage();
                    if (errorMessage == null) {
                        errorMessage =
                            "failed to create an SSL socket";
                    }
                    throw new IOException(errorMessage);
                }
            } else {
                // if the code is not OK, inform the event handler
                ProtocolVersion version = statusLine.getProtocolVersion();

                req.mEventHandler.status(version.getMajor(),
                                         version.getMinor(),
                                         statusCode,
                                         statusLine.getReasonPhrase());
                req.mEventHandler.headers(headers);
                req.mEventHandler.endData();

                proxyConnection.close();

                // here, we return null to indicate that the original
                // request needs to be dropped
                return null;
            }
        } else {
            // if we do not have a proxy, we simply connect to the host
            try {
                synchronized (mSslSocketFactory) {
                    sslSock = (SSLSocket) mSslSocketFactory.createSocket();
                    
                    sslSock.setSoTimeout(SOCKET_TIMEOUT);
                    sslSock.connect(new InetSocketAddress(mHost.getHostName(),
                            mHost.getPort()));
                    
                }
            } catch(IOException e) {
                if (sslSock != null) {
                    sslSock.close();
                }

                String errorMessage = e.getMessage();
                if (errorMessage == null) {
                    errorMessage = "failed to create an SSL socket";
                }

                throw new IOException(errorMessage);
            }
        }

        // do handshake and validate server certificates
        SslError error = CertificateChainValidator.getInstance().
            doHandshakeAndValidateServerCertificates(this, sslSock, mHost.getHostName());

        EventHandler eventHandler = req.getEventHandler();

        // Update the certificate info (to be consistent, it is better to do it
        // here, before we start handling SSL errors, if any)
        eventHandler.certificate(mCertificate);

        // Inform the user if there is a problem
        if (error != null) {
            // handleSslErrorRequest may immediately unsuspend if it wants to
            // allow the certificate anyway.
            // So we mark the connection as suspended, call handleSslErrorRequest
            // then check if we're still suspended and only wait if we actually
            // need to.
            synchronized (mSuspendLock) {
                mSuspended = true;
            }
            // don't hold the lock while calling out to the event handler
            eventHandler.handleSslErrorRequest(error);
            synchronized (mSuspendLock) {
                if (mSuspended) {
                    try {
                        // Put a limit on how long we are waiting; if the timeout
                        // expires (which should never happen unless you choose
                        // to ignore the SSL error dialog for a very long time),
                        // we wake up the thread and abort the request. This is
                        // to prevent us from stalling the network if things go
                        // very bad.
                        mSuspendLock.wait(10 * 60 * 1000);
                        if (mSuspended) {
                            // mSuspended is true if we have not had a chance to
                            // restart the connection yet (ie, the wait timeout
                            // has expired)
                            mSuspended = false;
                            mAborted = true;
                            if (HttpLog.LOGV) {
                                HttpLog.v("HttpsConnection.openConnection():" +
                                          " SSL timeout expired and request was cancelled!!!");
                            }
                        }
                    } catch (InterruptedException e) {
                        // ignore
                    }
                }
                if (mAborted) {
                    // The user decided not to use this unverified connection
                    // so close it immediately.
                    sslSock.close();
                    throw new SSLConnectionClosedByUserException("connection closed by the user");
                }
            }
        }

        // All went well, we have an open, verified connection.
        AndroidHttpClientConnection conn = new AndroidHttpClientConnection();
        BasicHttpParams params = new BasicHttpParams();
        params.setIntParameter(HttpConnectionParams.SOCKET_BUFFER_SIZE, 8192);
        conn.bind(sslSock, params);
        return conn;
    }

    /**
     * Closes the low level connection.
     *
     * If an exception is thrown then it is assumed that the connection will
     * have been closed (to the extent possible) anyway and the caller does not
     * need to take any further action.
     *
     */
    @Override
    void closeConnection() {
        // if the connection has been suspended due to an SSL error
        if (mSuspended) {
            // wake up the network thread
            restartConnection(false);
        }

        try {
            if (mHttpClientConnection != null && mHttpClientConnection.isOpen()) {
                mHttpClientConnection.close();
            }
        } catch (IOException e) {
            if (HttpLog.LOGV)
                HttpLog.v("HttpsConnection.closeConnection():" +
                          " failed closing connection " + mHost);
            e.printStackTrace();
        }
    }

    /**
     * Restart a secure connection suspended waiting for user interaction.
     */
    void restartConnection(boolean proceed) {
        if (HttpLog.LOGV) {
            HttpLog.v("HttpsConnection.restartConnection():" +
                      " proceed: " + proceed);
        }

        synchronized (mSuspendLock) {
            if (mSuspended) {
                mSuspended = false;
                mAborted = !proceed;
                mSuspendLock.notify();
            }
        }
    }

    @Override
    String getScheme() {
        return "https";
    }
}
