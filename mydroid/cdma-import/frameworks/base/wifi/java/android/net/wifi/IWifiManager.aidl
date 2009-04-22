/**
 * Copyright (c) 2008, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); 
 * you may not use this file except in compliance with the License. 
 * You may obtain a copy of the License at 
 *
 *     http://www.apache.org/licenses/LICENSE-2.0 
 *
 * Unless required by applicable law or agreed to in writing, software 
 * distributed under the License is distributed on an "AS IS" BASIS, 
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
 * See the License for the specific language governing permissions and 
 * limitations under the License.
 */

package android.net.wifi;

import android.net.wifi.WifiInfo;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.ScanResult;
import android.net.DhcpInfo;

/**
 * Interface that allows controlling and querying Wi-Fi connectivity.
 *
 * {@hide}
 */
interface IWifiManager
{
    List<WifiConfiguration> getConfiguredNetworks();
    
    int addOrUpdateNetwork(in WifiConfiguration config);

    boolean removeNetwork(int netId);

    boolean enableNetwork(int netId, boolean disableOthers);

    boolean disableNetwork(int netId);

    boolean pingSupplicant();

    boolean startScan();

    List<ScanResult> getScanResults();

    boolean disconnect();

    boolean reconnect();
    
    boolean reassociate();

    WifiInfo getConnectionInfo();

    boolean setWifiEnabled(boolean enable);

    int getWifiState();
    
    boolean saveConfiguration();

    DhcpInfo getDhcpInfo();

    boolean acquireWifiLock(IBinder lock, String tag);

    boolean releaseWifiLock(IBinder lock);
}

