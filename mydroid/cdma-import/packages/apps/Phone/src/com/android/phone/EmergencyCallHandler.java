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

package com.android.phone;

import android.app.Activity;
import android.app.Application;
import android.app.ProgressDialog;
import android.content.Intent;
import android.os.AsyncResult;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import com.android.internal.telephony.Phone;
import com.android.internal.telephony.PhoneFactory;
import android.telephony.ServiceState;
import android.view.WindowManager;

 /**
 * This class coordinates with the InCallScreen by throwing the original intent
 * (from the dialer) back and forth. An extra integer referenced by 
 * EMERGENCY_CALL_RETRY_KEY, is used to convey all the information we need.
 */
public class EmergencyCallHandler extends Activity {
    /** the key used to get the count from our Intent's extra(s) */
    public static final String EMERGENCY_CALL_RETRY_KEY = "emergency_call_retry_count";
    
    /** count indicating an initial attempt at the call should be made. */
    public static final int INITIAL_ATTEMPT = -1;
    
    /** number of times to retry the call and the time spent in between attempts*/
    public static final int NUMBER_OF_RETRIES = 6;
    public static final int TIME_BETWEEN_RETRIES_MS = 5000;
    
    // constant events
    private static final int EVENT_SERVICE_STATE_CHANGED = 100;
    private static final int EVENT_TIMEOUT_EMERGENCY_CALL = 200;
    
    /**
     * Package holding information needed for the callback.
     */
    private static class EmergencyCallInfo {
        public Phone phone;
        public Intent intent;
        public ProgressDialog dialog;
        public Application app;
    }
    
    /**
     * static handler class, used to handle the two relevent events. 
     */
    private static EmergencyCallEventHandler sHandler;
    private static class EmergencyCallEventHandler extends Handler {
        public void handleMessage(Message msg) {
            switch(msg.what) {
                
                case EVENT_SERVICE_STATE_CHANGED: {
                        // make the initial call attempt after the radio is turned on.
                        ServiceState state = (ServiceState) ((AsyncResult) msg.obj).result;
                        if (state.getState() != ServiceState.STATE_POWER_OFF) {
                            EmergencyCallInfo eci = 
                                (EmergencyCallInfo) ((AsyncResult) msg.obj).userObj;
                            // deregister for the service state change events. 
                            eci.phone.unregisterForServiceStateChanged(this);
                            eci.app.startActivity(eci.intent);
                            eci.dialog.dismiss();
                        }
                    }
                    break;
                
                
                case EVENT_TIMEOUT_EMERGENCY_CALL: {
                        // repeated call after the timeout period.
                        EmergencyCallInfo eci = (EmergencyCallInfo) msg.obj;
                        eci.app.startActivity(eci.intent);
                        eci.dialog.dismiss();
                    }
                    break;
            }
        }
    };
    
    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        
        // setup the phone and get the retry count embedded in the intent.
        Phone phone = PhoneFactory.getDefaultPhone();
        int retryCount = getIntent().getIntExtra(EMERGENCY_CALL_RETRY_KEY, INITIAL_ATTEMPT);
        
        // create a new message object.
        EmergencyCallInfo eci = new EmergencyCallInfo();
        eci.phone = phone;
        eci.app = getApplication();
        eci.dialog = constructDialog(retryCount);
        eci.intent = getIntent().setComponent(null);
        
        // create the handler.
        if (sHandler == null) {
            sHandler = new EmergencyCallEventHandler();
        }
        
        // If this is the initial attempt, we need to register for a radio state
        // change and turn the radio on.  Otherwise, this is just a retry, and
        // we simply wait the alloted time before sending the request to try
        // the call again.
        
        // Note: The radio logic ITSELF will try its best to put the emergency
        // call through once the radio is turned on.  The retry we have here 
        // is in case it fails; the current constants we have include making
        // 6 attempts, with a 5 second delay between each.
        if (retryCount == INITIAL_ATTEMPT) {
            // place the number of pending retries in the intent.
            eci.intent.putExtra(EMERGENCY_CALL_RETRY_KEY, NUMBER_OF_RETRIES);
            
            // turn the radio on and listen for it to complete.
            phone.registerForServiceStateChanged(sHandler, 
                    EVENT_SERVICE_STATE_CHANGED, eci);

            // If airplane mode is on, we turn it off the same way that the 
            // Settings activity turns it off.
            if (Settings.System.getInt(getContentResolver(), 
                    Settings.System.AIRPLANE_MODE_ON, 0) > 0) {
                // Change the system setting
                Settings.System.putInt(getContentResolver(), Settings.System.AIRPLANE_MODE_ON, 0);
                
                // Post the intent
                Intent intent = new Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED);
                intent.putExtra("state", false);
                sendBroadcast(intent);

            // Otherwise, for some strange reason the radio is just off, so 
            // we just turn it back on.
            } else {
                phone.setRadioPower(true);
            }
            
        } else {
            // decrement and store the number of retries.
            eci.intent.putExtra(EMERGENCY_CALL_RETRY_KEY, (retryCount - 1));
            
            // get the message and attach the data, then wait the alloted
            // time and send.
            Message m = sHandler.obtainMessage(EVENT_TIMEOUT_EMERGENCY_CALL);
            m.obj = eci;
            sHandler.sendMessageDelayed(m, TIME_BETWEEN_RETRIES_MS);
        }
        finish();
    }
    
    /**
     * create the dialog and hand it back to caller.
     */
    private ProgressDialog constructDialog(int retryCount) {
        // figure out the message to display. 
        int msgId = (retryCount == INITIAL_ATTEMPT) ? 
                R.string.emergency_enable_radio_dialog_message :
                R.string.emergency_enable_radio_dialog_retry;

        // create a system dialog that will persist outside this activity.
        ProgressDialog pd = new ProgressDialog(getApplication());
        pd.setTitle(getText(R.string.emergency_enable_radio_dialog_title));
        pd.setMessage(getText(msgId));
        pd.setIndeterminate(true);
        pd.setCancelable(false);
        pd.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_DIALOG);
        pd.getWindow().addFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
        
        // show the dialog
        pd.show();
        
        return pd;
    }
    
    
}
