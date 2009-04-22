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

package com.android.hierarchyviewer.scene;

import com.android.ddmlib.Device;
import com.android.hierarchyviewer.device.Configuration;
import com.android.hierarchyviewer.device.Window;
import com.android.hierarchyviewer.device.DeviceBridge;

import java.awt.Image;
import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import javax.imageio.ImageIO;

public class CaptureLoader {
    public static Image loadCapture(Device device, Window window, String params) {
        Socket socket = null;
        BufferedInputStream in = null;
        BufferedWriter out = null;
        
        try {
            socket = new Socket();
            socket.connect(new InetSocketAddress("127.0.0.1",
                    DeviceBridge.getDeviceLocalPort(device)));
            
            out = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
            in = new BufferedInputStream(socket.getInputStream());

            out.write("CAPTURE " + window.encode() + " " + params);
            out.newLine();
            out.flush();

            return ImageIO.read(in);
        } catch (IOException e) {
            // Empty
        } finally {
            try {
                if (out != null) {
                    out.close();
                }
                if (in != null) {
                    in.close();
                }
                if (socket != null) {
                    socket.close();
                }
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
        
        return null;
    }
}
