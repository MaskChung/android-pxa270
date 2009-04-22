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

import android.net.LocalSocketAddress;
import android.net.LocalSocket;
import android.os.Environment;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.util.Config;
import android.util.Log;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

/**
 * Thread for communicating with the mount service daemon via a local socket.
 * Events received from the daemon are passed to the MountService instance, 
 * and the MountService instance calls MountListener to send commands to the daemon.
 */
final class MountListener implements Runnable {

    private static final String TAG = "MountListener";

    // ** THE FOLLOWING STRING CONSTANTS MUST MATCH VALUES IN system/mountd/mountd.h
    
    // socket name for connecting to mountd
    private static final String MOUNTD_SOCKET = "mountd";
    
    // mountd commands
    private static final String MOUNTD_ENABLE_UMS = "enable_ums";
    private static final String MOUNTD_DISABLE_UMS = "disable_ums";
    private static final String MOUNTD_SEND_STATUS = "send_status";
    private static final String MOUNTD_MOUNT_MEDIA = "mount_media:";
    private static final String MOUNTD_EJECT_MEDIA = "eject_media:";

    // mountd events
    private static final String MOUNTD_UMS_ENABLED = "ums_enabled";
    private static final String MOUNTD_UMS_DISABLED = "ums_disabled";
    private static final String MOUNTD_UMS_CONNECTED = "ums_connected";
    private static final String MOUNTD_UMS_DISCONNECTED = "ums_disconnected";
    private static final String MOUNTD_MEDIA_REMOVED = "media_removed:";
    private static final String MOUNTD_MEDIA_UNMOUNTED = "media_unmounted:";
    private static final String MOUNTD_MEDIA_MOUNTED = "media_mounted:";
    private static final String MOUNTD_MEDIA_MOUNTED_READ_ONLY = "media_mounted_ro:";
    private static final String MOUNTD_MEDIA_SHARED = "media_shared:";
    private static final String MOUNTD_MEDIA_BAD_REMOVAL = "media_bad_removal:";
    private static final String MOUNTD_MEDIA_UNMOUNTABLE = "media_unmountable:";
    private static final String MOUNTD_REQUEST_EJECT = "request_eject:";

    /**
     * MountService that handles events received from the mount service daemon
     */
    private MountService mService;
    
    /**
     * Stream for sending commands to the mount service daemon.
     */
    private OutputStream mOutputStream;
    
    /** 
     * Cached value indicating whether or not USB mass storage is enabled.
     */
    private boolean mUmsEnabled;
 
    /** 
     * Cached value indicating whether or not USB mass storage is connected.
     */
    private boolean mUmsConnected;

   /**
     * Constructor for MountListener
     * 
     * @param service  The MountListener we are handling communication with USB
     *                 daemon for.
     */
    MountListener(MountService service) { 
        mService = service;   
    }

    /**
     * Process and dispatches events received from the mount service daemon
     * 
     * @param event  An event received from the mount service daemon
     */
    private void handleEvent(String event) {
        if (Config.LOGD) Log.d(TAG, "handleEvent " + event);
    
        int colonIndex = event.indexOf(':');
        String path = (colonIndex > 0 ? event.substring(colonIndex + 1) : null);
        
        if (event.equals(MOUNTD_UMS_ENABLED)) {
            mUmsEnabled = true;
        } else if (event.equals(MOUNTD_UMS_DISABLED)) {
            mUmsEnabled = false;
        } else if (event.equals(MOUNTD_UMS_CONNECTED)) {
            mUmsConnected = true;
            mService.notifyUmsConnected();
        } else if (event.equals(MOUNTD_UMS_DISCONNECTED)) {
            mUmsConnected = false;        
            mService.notifyUmsDisconnected();
        } else if (event.startsWith(MOUNTD_MEDIA_REMOVED)) {
            mService.notifyMediaRemoved(path);
        } else if (event.startsWith(MOUNTD_MEDIA_UNMOUNTED)) {
            mService.notifyMediaUnmounted(path);
        } else if (event.startsWith(MOUNTD_MEDIA_MOUNTED)) {
            mService.notifyMediaMounted(path, false);
        } else if (event.startsWith(MOUNTD_MEDIA_MOUNTED_READ_ONLY)) {
            mService.notifyMediaMounted(path, true);
        } else if (event.startsWith(MOUNTD_MEDIA_SHARED)) {
            mService.notifyMediaShared(path);
        } else if (event.startsWith(MOUNTD_MEDIA_BAD_REMOVAL)) {
            mService.notifyMediaBadRemoval(path);
            // also send media eject intent, to notify apps to close any open
            // files on the media.
            mService.notifyMediaEject(path);
        } else if (event.startsWith(MOUNTD_MEDIA_UNMOUNTABLE)) {
            mService.notifyMediaUnmountable(path);
        } else if (event.startsWith(MOUNTD_REQUEST_EJECT)) {
            mService.notifyMediaEject(path);
        }    
    }
    
    /**
     * Sends a command to the mount service daemon via a local socket
     * 
     * @param command  The command to send to the mount service daemon
     */
    private void writeCommand(String command) {
        writeCommand2(command, null);
    }
    
    /**
     * Sends a command to the mount service daemon via a local socket
     * with a single argument
     * 
     * @param command  The command to send to the mount service daemon
     * @param argument The argument to send with the command (or null)
     */
    private void writeCommand2(String command, String argument) {
        synchronized (this) {
            if (mOutputStream == null) {
                Log.e(TAG, "No connection to mountd", new IllegalStateException());
            } else {
                StringBuilder builder = new StringBuilder(command);
                if (argument != null) {
                    builder.append(argument);
                }
                builder.append('\0');

                try {
                    mOutputStream.write(builder.toString().getBytes());
                } catch (IOException ex) {
                    Log.e(TAG, "IOException in writeCommand", ex);
                }
            }
        }
    }

    /** 
     * Opens a socket to communicate with the mount service daemon and listens 
     * for events from the daemon.  
     *
     */
    private void listenToSocket() {
       LocalSocket socket = null;

        try {
            socket = new LocalSocket();
            LocalSocketAddress address = new LocalSocketAddress(MOUNTD_SOCKET, 
                    LocalSocketAddress.Namespace.RESERVED);

            socket.connect(address);

            InputStream inputStream = socket.getInputStream();
            mOutputStream = socket.getOutputStream();

            byte[] buffer = new byte[100];

            writeCommand(MOUNTD_SEND_STATUS);
            
            while (true) {
                int count = inputStream.read(buffer);
                if (count < 0) break;

                int start = 0;
                for (int i = 0; i < count; i++) {
                    if (buffer[i] == 0) {
                        String event = new String(buffer, start, i - start);
                        handleEvent(event);
                        start = i + 1;
                    }                   
                }
            }                
        } catch (IOException ex) {
            // This exception is normal when running in desktop simulator 
            // where there is no mount daemon to talk to

            // log("IOException in listenToSocket");
        }
        
        synchronized (this) {
            if (mOutputStream != null) {
                try {
                    mOutputStream.close();
                } catch (IOException e) {
                    Log.w(TAG, "IOException closing output stream");
                }
                
                mOutputStream = null;
            }
        }
        
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException ex) {
            Log.w(TAG, "IOException closing socket");
        }
       
        /*
         * Sleep before trying again.
         * This should not happen except while debugging.
         * Without this sleep, the emulator will spin and
         * create tons of throwaway LocalSockets, making
         * system_server GC constantly.
         */
        Log.e(TAG, "Failed to connect to mountd", new IllegalStateException());
        SystemClock.sleep(2000);
    }

    /**
     * Main loop for MountListener thread.
     */
    public void run() {
        // ugly hack for the simulator.
        if ("simulator".equals(SystemProperties.get("ro.product.device"))) {
            SystemProperties.set("EXTERNAL_STORAGE_STATE", Environment.MEDIA_MOUNTED);
            // usbd does not run in the simulator, so send a fake device mounted event to trigger the Media Scanner
            mService.notifyMediaMounted(Environment.getExternalStorageDirectory().getPath(), false);
            
            // no usbd in the simulator, so no point in hanging around.
            return;
        }
    
        try {  
            while (true) {
                listenToSocket();
            }
        } catch (Throwable t) {
            // catch all Throwables so we don't bring down the system process
            Log.e(TAG, "Fatal error " + t + " in MountListener thread!");
        }
    }
    
    /**
     * @return  true if USB mass storage is enabled
     */
    boolean getMassStorageEnabled() {
        return mUmsEnabled;
    }

    /**
     * Enables or disables USB mass storage support.
     * 
     * @param enable  true to enable USB mass storage support
     */
    void setMassStorageEnabled(boolean enable) {
        writeCommand(enable ? MOUNTD_ENABLE_UMS : MOUNTD_DISABLE_UMS);
    }

    /**
     * @return  true if USB mass storage is connected
     */
    boolean getMassStorageConnected() {
        return mUmsConnected;
    }

    /**
     * Mount media at given mount point.
     */
    public void mountMedia(String mountPoint) {
        writeCommand2(MOUNTD_MOUNT_MEDIA, mountPoint);
    }

    /**
     * Unmount media at given mount point.
     */
    public void ejectMedia(String mountPoint) {
        writeCommand2(MOUNTD_EJECT_MEDIA, mountPoint);
    }
}

