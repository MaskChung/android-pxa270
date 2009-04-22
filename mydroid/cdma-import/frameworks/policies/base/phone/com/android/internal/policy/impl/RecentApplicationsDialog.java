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

package com.android.internal.policy.impl;

import android.app.ActivityManager;
import android.app.Dialog;
import android.app.StatusBarManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.View.OnClickListener;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.List;

public class RecentApplicationsDialog extends Dialog implements OnClickListener {
    // Elements for debugging support
//  private static final String LOG_TAG = "RecentApplicationsDialog";
    private static final boolean DBG_FORCE_EMPTY_LIST = false;

    static private StatusBarManager sStatusBar;
    
    private static final int NUM_BUTTONS = 6;
    private static final int MAX_RECENT_TASKS = NUM_BUTTONS * 2;    // allow for some discards
    
    final View[] mButtons = new View[NUM_BUTTONS];
    View mNoAppsText;
    IntentFilter mBroadcastIntentFilter = new IntentFilter(Intent.ACTION_CLOSE_SYSTEM_DIALOGS);

    public RecentApplicationsDialog(Context context) {
        super(context);
    }

    /**
     * We create the recent applications dialog just once, and it stays around (hidden)
     * until activated by the user.
     * 
     * @see PhoneWindowManager#showRecentAppsDialog
     */
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Context context = getContext();

        if (sStatusBar == null) {
            sStatusBar = (StatusBarManager)context.getSystemService(Context.STATUS_BAR_SERVICE);
        }

        Window theWindow = getWindow();
        theWindow.requestFeature(Window.FEATURE_NO_TITLE);
        theWindow.setType(WindowManager.LayoutParams.TYPE_SYSTEM_DIALOG);
        theWindow.setFlags(WindowManager.LayoutParams.FLAG_BLUR_BEHIND,
                           WindowManager.LayoutParams.FLAG_BLUR_BEHIND);
        
        setContentView(com.android.internal.R.layout.recent_apps_dialog);

        mButtons[0] = findViewById(com.android.internal.R.id.button1);
        mButtons[1] = findViewById(com.android.internal.R.id.button2);
        mButtons[2] = findViewById(com.android.internal.R.id.button3);
        mButtons[3] = findViewById(com.android.internal.R.id.button4);
        mButtons[4] = findViewById(com.android.internal.R.id.button5);
        mButtons[5] = findViewById(com.android.internal.R.id.button6);
        mNoAppsText = findViewById(com.android.internal.R.id.no_applications_message);
        
        for (View b : mButtons) {
            b.setOnClickListener(this);
        }
    }

    /**
     * Handler for user clicks.  If a button was clicked, launch the corresponding activity.
     */
    public void onClick(View v) {
        
        for (View b : mButtons) {
            if (b == v) {
                // prepare a launch intent and send it
                Intent intent = (Intent)b.getTag();
                intent.addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY);
                getContext().startActivity(intent);
            }
        }
        dismiss();
    }

    /**
     * Set up and show the recent activities dialog.
     */
    @Override
    public void onStart() {
        super.onStart();
        reloadButtons();
        if (sStatusBar != null) {
            sStatusBar.disable(StatusBarManager.DISABLE_EXPAND);
        }

        // receive broadcasts
        getContext().registerReceiver(mBroadcastReceiver, mBroadcastIntentFilter);
    }

    /**
     * Dismiss the recent activities dialog.
     */
    @Override
    public void onStop() {
        super.onStop();
        
        // dump extra memory we're hanging on to
        for (View b : mButtons) {
            setButtonAppearance(b, null, null);
            b.setTag(null);
        }

        if (sStatusBar != null) {
            sStatusBar.disable(StatusBarManager.DISABLE_NONE);
        }

        // stop receiving broadcasts
        getContext().unregisterReceiver(mBroadcastReceiver);
     }
    
    /**
     * Reload the 6 buttons with recent activities
     */
    private void reloadButtons() {
        
        final Context context = getContext();
        final PackageManager pm = context.getPackageManager();
        final ActivityManager am = (ActivityManager) 
                                        context.getSystemService(Context.ACTIVITY_SERVICE);
        final List<ActivityManager.RecentTaskInfo> recentTasks = 
                                        am.getRecentTasks(MAX_RECENT_TASKS, 0);
        
        ResolveInfo homeInfo = pm.resolveActivity(
                new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
                0);
        
        // Performance note:  Our android performance guide says to prefer Iterator when
        // using a List class, but because we know that getRecentTasks() always returns
        // an ArrayList<>, we'll use a simple index instead.
        int button = 0;
        int numTasks = recentTasks.size();
        for (int i = 0; i < numTasks && (button < NUM_BUTTONS); ++i) {
            final ActivityManager.RecentTaskInfo info = recentTasks.get(i);
            
            // for debug purposes only, disallow first result to create empty lists
            if (DBG_FORCE_EMPTY_LIST && (i == 0)) continue;
            
            Intent intent = new Intent(info.baseIntent);
            if (info.origActivity != null) {
                intent.setComponent(info.origActivity);
            }
            
            // Skip the current home activity.
            if (homeInfo != null) {
                if (homeInfo.activityInfo.packageName.equals(
                        intent.getComponent().getPackageName())
                        && homeInfo.activityInfo.name.equals(
                                intent.getComponent().getClassName())) {
                    continue;
                }
            }
            
            intent.setFlags((intent.getFlags()&~Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                    | Intent.FLAG_ACTIVITY_NEW_TASK);
            final ResolveInfo resolveInfo = pm.resolveActivity(intent, 0);
            if (resolveInfo != null) {
                final ActivityInfo activityInfo = resolveInfo.activityInfo;
                final String title = activityInfo.loadLabel(pm).toString();
                final Drawable icon = activityInfo.loadIcon(pm);

                if (title != null && title.length() > 0 && icon != null) {
                    final View b = mButtons[button];
                    setButtonAppearance(b, title, icon);
                    b.setTag(intent);
                    b.setVisibility(View.VISIBLE);
                    b.setPressed(false);
                    b.clearFocus();
                    ++button;
                }
            }
        }
        
        // handle the case of "no icons to show"
        mNoAppsText.setVisibility((button == 0) ? View.VISIBLE : View.GONE);
        
        // hide the rest
        for ( ; button < NUM_BUTTONS; ++button) {
            mButtons[button].setVisibility(View.GONE);
        }
    }
    
    /**
     * Adjust appearance of each icon-button
     */
    private void setButtonAppearance(View theButton, final String theTitle, final Drawable icon) {
        TextView tv = (TextView) theButton.findViewById(com.android.internal.R.id.label);
        tv.setText(theTitle);
        ImageView iv = (ImageView) theButton.findViewById(com.android.internal.R.id.icon);
        iv.setImageDrawable(icon);
    }

    /**
     * This is the listener for the ACTION_CLOSE_SYSTEM_DIALOGS intent.  It's an indication that
     * we should close ourselves immediately, in order to allow a higher-priority UI to take over
     * (e.g. phone call received).
     * 
     * TODO: This is a really heavyweight solution for something that should be so simple.
     * For example, we already have a handler, in our superclass, why aren't we sharing that?
     * I think we need to investigate simplifying this entire methodology, or perhaps boosting 
     * it up into the Dialog class.
     */
    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (Intent.ACTION_CLOSE_SYSTEM_DIALOGS.equals(action)) {
                String reason = intent.getStringExtra(PhoneWindowManager.SYSTEM_DIALOG_REASON_KEY);
                if (! PhoneWindowManager.SYSTEM_DIALOG_REASON_RECENT_APPS.equals(reason)) {
                    dismiss();
                }
            }
        }
    };
}
