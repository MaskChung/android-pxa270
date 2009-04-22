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

package com.android.server;

import android.util.Log;

import java.net.ServerSocket;
import java.net.Socket;
import java.net.InetAddress;
import java.io.IOException;
import java.io.BufferedReader;
import java.io.InputStreamReader;

/**
 * The ViewServer is local socket server that can be used to communicate with the
 * views of the opened windows. Communication with the views is ensured by the
 * {@link com.android.server.WindowManagerService} and is a cross-process operation.
 *
 * {@hide}
 */
class ViewServer implements Runnable {
    /**
     * The default port used to start view servers.
     */
    public static final int VIEW_SERVER_DEFAULT_PORT = 4939;

    // Debug facility
    private static final String LOG_TAG = "ViewServer";

    // Protocol commands
    // Lists all of the available windows in the system
    private static final String COMMAND_WINDOW_MANAGER_LIST = "LIST";

    private ServerSocket mServer;
    private Thread mThread;

    private final WindowManagerService mWindowManager;
    private final int mPort;

    /**
     * Creates a new ViewServer associated with the specified window manager.
     * The server uses the default port {@link #VIEW_SERVER_DEFAULT_PORT}. The server
     * is not started by default.
     *
     * @param windowManager The window manager used to communicate with the views.
     *
     * @see #start()
     */
    ViewServer(WindowManagerService windowManager) {
        this(windowManager, VIEW_SERVER_DEFAULT_PORT);
    }

    /**
     * Creates a new ViewServer associated with the specified window manager on the
     * specified local port. The server is not started by default.
     *
     * @param windowManager The window manager used to communicate with the views.
     * @param port The port for the server to listen to.
     *
     * @see #start()
     */
    ViewServer(WindowManagerService windowManager, int port) {
        mWindowManager = windowManager;
        mPort = port;
    }

    /**
     * Starts the server.
     *
     * @return True if the server was successfully created, or false if it already exists.
     * @throws IOException If the server cannot be created.
     *
     * @see #stop()
     * @see #isRunning()
     * @see WindowManagerService#startViewServer(int)
     */
    boolean start() throws IOException {
        if (mThread != null) {
            return false;
        }

        mServer = new ServerSocket(mPort, 1, InetAddress.getLocalHost());
        mThread = new Thread(this, "Remote View Server [port=" + mPort + "]");
        mThread.start();

        return true;
    }

    /**
     * Stops the server.
     *
     * @return True if the server was stopped, false if an error occured or if the
     *         server wasn't started.
     *
     * @see #start()
     * @see #isRunning()
     * @see WindowManagerService#stopViewServer()
     */
    boolean stop() {
        if (mThread != null) {
            mThread.interrupt();
            mThread = null;
            try {
                mServer.close();
                mServer = null;
                return true;
            } catch (IOException e) {
                Log.w(LOG_TAG, "Could not close the view server");
            }
        }
        return false;
    }

    /**
     * Indicates whether the server is currently running.
     *
     * @return True if the server is running, false otherwise.
     *
     * @see #start()
     * @see #stop()
     * @see WindowManagerService#isViewServerRunning()  
     */
    boolean isRunning() {
        return mThread != null && mThread.isAlive();
    }

    /**
     * Main server loop.
     */
    public void run() {
        final ServerSocket server = mServer;

        while (Thread.currentThread() == mThread) {
            Socket client = null;
            // Any uncaught exception will crash the system process
            try {
                client = server.accept();

                BufferedReader in = null;
                try {
                    in = new BufferedReader(new InputStreamReader(client.getInputStream()), 1024);

                    final String request = in.readLine();

                    String command;
                    String parameters;

                    int index = request.indexOf(' ');
                    if (index == -1) {
                        command = request;
                        parameters = "";
                    } else {
                        command = request.substring(0, index);
                        parameters = request.substring(index + 1);
                    }

                    boolean result;
                    if (COMMAND_WINDOW_MANAGER_LIST.equalsIgnoreCase(command)) {
                        result = mWindowManager.viewServerListWindows(client);
                    } else {
                        result = mWindowManager.viewServerWindowCommand(client,
                                command, parameters);
                    }

                    if (!result) {
                        Log.w(LOG_TAG, "An error occured with the command: " + command);
                    }
                } finally {
                    if (in != null) {
                        in.close();
                    }
                }
            } catch (Exception e) {
                Log.w(LOG_TAG, "Connection error: ", e);
            } finally {
                if (client != null) {
                    try {
                        client.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }
}
