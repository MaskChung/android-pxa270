

/**
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy
 * of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */

package com.android.settings;

import com.android.settings.R;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageDataObserver;
import android.content.pm.IPackageStatsObserver;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageStats;
import android.content.pm.PackageManager.NameNotFoundException;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Config;
import android.util.Log;
import java.util.ArrayList;
import java.util.List;
import android.content.ComponentName;
import android.view.View;
import android.widget.AppSecurityPermissions;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Activity to display application information from Settings
 * 
 */
public class InstalledAppDetails extends Activity implements View.OnClickListener, DialogInterface.OnClickListener  {
    private static final String TAG="InstalledAppDetails";
    private static final int _UNKNOWN_APP=R.string.unknown;
   //wait times used for the async package manager api
    private ApplicationInfo mAppInfo;
    private Button mUninstallButton;
    private Button mActivitiesButton;
    private boolean mSysPackage;
    private boolean localLOGV=Config.LOGV || true;
    private TextView mTotalSize;
    private TextView mAppSize;
    private TextView mDataSize;
    PackageStats mSizeInfo;
    private Button mManageSpaceButton;
    private PackageManager mPm;
    private String mBStr, mKbStr, mMbStr;
    
    //internal constants used in Handler
    private static final int CLEAR_USER_DATA = 1;
    private static final int OP_SUCCESSFUL = 1;
    private static final int OP_FAILED = 2;
    private static final int GET_PKG_SIZE = 2;
    private static final String ATTR_PACKAGE_STATS="PackageStats";
    
    private Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case CLEAR_USER_DATA:
                    processClearMsg(msg);
                    break;
                case GET_PKG_SIZE:
                    refreshSizeInfo(msg);
                    break;
                default:
                    break;
            }
        }
    };
    
    private boolean isSystemPackage() {
        if ((mAppInfo.flags&ApplicationInfo.FLAG_SYSTEM) != 0) {
            return true;
        }
        return false;
    }
    
    class ClearUserDataObserver extends IPackageDataObserver.Stub {
       public void onRemoveCompleted(final String packageName, final boolean succeeded) {
           final Message msg = mHandler.obtainMessage(CLEAR_USER_DATA);
           msg.arg1 = succeeded?OP_SUCCESSFUL:OP_FAILED;
           mHandler.sendMessage(msg);
        }
    }
    
    class PkgSizeObserver extends IPackageStatsObserver.Stub {
        public int idx;
        public void onGetStatsCompleted(PackageStats pStats, boolean succeeded) {
             Message msg = mHandler.obtainMessage(GET_PKG_SIZE);
             Bundle data = new Bundle();
             data.putParcelable(ATTR_PACKAGE_STATS, pStats);
             msg.setData(data);
             mHandler.sendMessage(msg);
            
         }
     }
    
    private String getSizeStr(long size) {
        String retStr = "";
        if(size < 1024) {
            return String.valueOf(size)+mBStr;
        }
        long kb, mb, rem;
        kb = size >> 10;
        rem = size - (kb << 10);
        if(kb < 1024) {
            if(rem > 512) {
                kb++;
            }
            retStr += String.valueOf(kb)+mKbStr;
            return retStr;
        }
        mb = kb >> 10;
        if(kb >= 512) {
            //round off
            mb++;
       }
       retStr += String.valueOf(mb)+ mMbStr;
       return retStr;
    }
    
    /** Called when the activity is first created. */
    @Override
    protected void onCreate(Bundle icicle) {
        super.onCreate(icicle);
        //get package manager
        mPm = getPackageManager();
        //get application's name from intent
        Intent intent = getIntent();
        final String packageName = intent.getStringExtra(ManageApplications.APP_PKG_NAME);
        mSizeInfo = intent.getParcelableExtra(ManageApplications.APP_PKG_SIZE);
        long total = -1;
        long code = -1;
        long data = -1;
        if(mSizeInfo != null) {
            total = mSizeInfo.cacheSize+mSizeInfo.codeSize+mSizeInfo.dataSize;
            code = mSizeInfo.codeSize;
            data = mSizeInfo.dataSize+mSizeInfo.cacheSize;
        }
        String unknownStr = getString(_UNKNOWN_APP);
        mBStr = getString(R.string.b_text);
        mKbStr = getString(R.string.kb_text);
        mMbStr = getString(R.string.mb_text);
        String totalSizeStr = unknownStr;
        if(total != -1) {
            totalSizeStr = getSizeStr(total);
        }
        String appSizeStr = unknownStr;
        if(code != -1) {
            appSizeStr = getSizeStr(code);
        }
        String dataSizeStr = unknownStr;
        if(data != -1) {
            dataSizeStr = getSizeStr(data);
        }
        if(localLOGV) Log.i(TAG, "packageName:"+packageName+", total="+total+
                "code="+code+", data="+data);
        try {
            mAppInfo = mPm.getApplicationInfo(packageName, 0);
        } catch (NameNotFoundException e) {
           Throwable th = e.fillInStackTrace();
            Log.e(TAG, "Exception when retrieving package:"+packageName, e);
            displayErrorDialog(R.string.app_not_found_dlg_text, true, true);
        }
        setContentView(R.layout.installed_app_details);
        ((ImageView)findViewById(R.id.app_icon)).setImageDrawable(mPm.
                getApplicationIcon(mAppInfo));
        //set application name TODO version
        CharSequence appName = mPm.getApplicationLabel(mAppInfo);
        if(appName == null) {
            appName = getString(_UNKNOWN_APP);
        }
        ((TextView)findViewById(R.id.app_name)).setText(appName);
        CharSequence appDesc = mAppInfo.loadDescription(mPm);
        if(appDesc != null) {
            ((TextView)findViewById(R.id.app_description)).setText(appDesc);
        }
        //TODO download str and download url
        //set values on views
        mTotalSize = (TextView)findViewById(R.id.total_size_text);
        mTotalSize.setText(totalSizeStr);
        mAppSize = (TextView)findViewById(R.id.application_size_text);
        mAppSize.setText(appSizeStr);
        mDataSize = (TextView)findViewById(R.id.data_size_text);
        mDataSize.setText(dataSizeStr);
         
         mUninstallButton = ((Button)findViewById(R.id.uninstall_button));
        //determine if app is a system app
         mSysPackage = isSystemPackage();
         if(localLOGV) Log.i(TAG, "Is systemPackage "+mSysPackage);
         int btnText;
         boolean btnClickable = true;
         
         if(mSysPackage) {
             //app can clear user data
             if((mAppInfo.flags & ApplicationInfo.FLAG_ALLOW_CLEAR_USER_DATA) 
                     == ApplicationInfo.FLAG_ALLOW_CLEAR_USER_DATA) {
                 mUninstallButton.setText(R.string.clear_user_data_text);
                 //disable button if data is 0
                 if(data == 0) {
                     mUninstallButton.setEnabled(false);
                 } else {
                     //enable button
                     mUninstallButton.setOnClickListener(this);
                 }
             } else {
                 //hide button if diableClearUserData is set
                 mUninstallButton.setVisibility(View.GONE);
             }
         } else {
             mUninstallButton.setText(R.string.uninstall_text);
             mUninstallButton.setOnClickListener(this);
         }
         //clear activities
         mActivitiesButton = (Button)findViewById(R.id.clear_activities_button);
         List<ComponentName> prefActList = new ArrayList<ComponentName>();
         //intent list cannot be null. so pass empty list
         List<IntentFilter> intentList = new ArrayList<IntentFilter>();
         mPm.getPreferredActivities(intentList,  prefActList, packageName);
         if(localLOGV) Log.i(TAG, "Have "+prefActList.size()+" number of activities in prefered list");
         TextView autoLaunchView = (TextView)findViewById(R.id.auto_launch);
         if(prefActList.size() <= 0) {
             //disable clear activities button
             autoLaunchView.setText(R.string.auto_launch_disable_text);
             mActivitiesButton.setEnabled(false);
         } else {
             autoLaunchView.setText(R.string.auto_launch_enable_text);
             mActivitiesButton.setOnClickListener(this);
         }
         mManageSpaceButton = (Button)findViewById(R.id.manage_space_button);
         if(mAppInfo.manageSpaceActivityName != null) {
             mManageSpaceButton.setVisibility(View.VISIBLE);
             mManageSpaceButton.setOnClickListener(this);
         }
         //security permissions section
         AppSecurityPermissions asp = new AppSecurityPermissions(this);
         PackageInfo pkgInfo;
        try {
            pkgInfo = mPm.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS);
        } catch (NameNotFoundException e) {
            Log.w(TAG, "Couldnt retrieve permissions for package:"+packageName);
            return;
        }
         asp.setSecurityPermissionsView(pkgInfo);
         LinearLayout securityList = (LinearLayout) findViewById(R.id.security_settings_list);
         securityList.addView(asp.getPermissionsView());
    }
    
    private void displayErrorDialog(int msgId, final boolean finish, final boolean changed) {
        //display confirmation dialog
        new AlertDialog.Builder(this)
        .setTitle(getString(R.string.app_not_found_dlg_title))
        .setIcon(R.drawable.ic_dialog_alert)
        .setMessage(getString(msgId))
        .setNeutralButton(getString(R.string.dlg_ok), 
                new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int which) {
                        //force to recompute changed value
                        setIntentAndFinish(finish, changed);
                    }
                }
        )
        .show();
    }
    
    private void setIntentAndFinish(boolean finish, boolean appChanged) {
        if(localLOGV) Log.i(TAG, "appChanged="+appChanged);
        Intent intent = new Intent();
        intent.putExtra(ManageApplications.APP_CHG, appChanged);
        setResult(ManageApplications.RESULT_OK, intent);
        mUninstallButton.setEnabled(false);
        if(finish) {
            finish();
        }
    }
    
    /*
     * Private method to handle get size info notification from observer when
     * the async operation from PackageManager is complete. The current user data
     * info has to be refreshed in the manage applications screen as well as the current screen.
     */
    private void refreshSizeInfo(Message msg) {
        boolean changed = false;
        Intent intent = new Intent();
        PackageStats newPs = msg.getData().getParcelable(ATTR_PACKAGE_STATS);
        long newTot = newPs.cacheSize+newPs.codeSize+newPs.dataSize;
        long oldTot = mSizeInfo.cacheSize+mSizeInfo.codeSize+mSizeInfo.dataSize;
        if(newTot != oldTot) {
            mTotalSize.setText(getSizeStr(newTot));
            changed = true;
        }
        if(newPs.codeSize != mSizeInfo.codeSize) {
            mAppSize.setText(getSizeStr(newPs.codeSize));
            changed = true;
        }
        if((newPs.dataSize != mSizeInfo.dataSize) || (newPs.cacheSize != mSizeInfo.cacheSize)) {
            mDataSize.setText(getSizeStr(newPs.dataSize+newPs.cacheSize));
            changed = true;
        }
        if(changed) {
            mUninstallButton.setText(R.string.clear_user_data_text);
            mSizeInfo = newPs;
            intent.putExtra(ManageApplications.APP_PKG_SIZE, mSizeInfo);
        }
        intent.putExtra(ManageApplications.APP_CHG, changed);
        setResult(ManageApplications.RESULT_OK, intent);
    }
    
    /*
     * Private method to handle clear message notification from observer when
     * the async operation from PackageManager is complete
     */
    private void processClearMsg(Message msg) {
        int result = msg.arg1;
        String packageName = mAppInfo.packageName;
        if(result == OP_SUCCESSFUL) {
            Log.i(TAG, "Cleared user data for system package:"+packageName);
            PkgSizeObserver observer = new PkgSizeObserver();
            mPm.getPackageSizeInfo(packageName, observer);
        } else {
            mUninstallButton.setText(R.string.clear_user_data_text);
            mUninstallButton.setEnabled(true);
        }
    }
    
    /*
     * Private method to initiate clearing user data when the user clicks the clear data 
     * button for a system package
     */
    private  void initiateClearUserDataForSysPkg() {
        mUninstallButton.setEnabled(false);
        //invoke uninstall or clear user data based on sysPackage
        boolean recomputeSizes = false;
        String packageName = mAppInfo.packageName;
        Log.i(TAG, "Clearing user data for system package");
        ClearUserDataObserver observer = new ClearUserDataObserver();
        ActivityManager am = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        boolean res = am.clearApplicationUserData(packageName, observer);
        if(!res) {
            //doesnt initiate clear. some error. should not happen but just log error for now
            Log.i(TAG, "Couldnt clear application user data for package:"+packageName);
            displayErrorDialog(R.string.clear_data_failed, false, false);
        } else {
                mUninstallButton.setText(R.string.recompute_size);
        }
    }
    
    /*
     * Method implementing functionality of buttons clicked
     * @see android.view.View.OnClickListener#onClick(android.view.View)
     */
    public void onClick(View v) {
        String packageName = mAppInfo.packageName;
        if(v == mUninstallButton) {
            if(mSysPackage) {
                //display confirmation dialog
                new AlertDialog.Builder(this)
                .setTitle(getString(R.string.clear_data_dlg_title))
                .setIcon(R.drawable.ic_dialog_alert)
                .setMessage(getString(R.string.clear_data_dlg_text))
                .setPositiveButton(R.string.dlg_ok, this)
                .setNegativeButton(R.string.dlg_cancel, this)
                .show();
            } else {
                //create new intent to launch Uninstaller activity
                Uri packageURI = Uri.parse("package:"+packageName);
                Intent uninstallIntent = new Intent(Intent.ACTION_DELETE, packageURI);
                startActivity(uninstallIntent);
                setIntentAndFinish(true, false);
            }
        } else if(v == mActivitiesButton) {
            mPm.clearPackagePreferredActivities(packageName);
            mActivitiesButton.setEnabled(false);
        } else if(v == mManageSpaceButton) {
            Intent intent = new Intent(Intent.ACTION_DEFAULT);
            intent.setClassName(mAppInfo.packageName, mAppInfo.manageSpaceActivityName);
            startActivityForResult(intent, -1);
        }
    }

    public void onClick(DialogInterface dialog, int which) {
        if(which == AlertDialog.BUTTON1) {
            //invoke uninstall or clear user data based on sysPackage
            initiateClearUserDataForSysPkg();
        } else {
            //cancel do nothing just retain existing screen
        }
    }
}

