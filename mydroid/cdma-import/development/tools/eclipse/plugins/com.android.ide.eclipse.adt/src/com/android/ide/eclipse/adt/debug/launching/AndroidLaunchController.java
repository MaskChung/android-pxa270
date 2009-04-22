/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Eclipse Public License, Version 1.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.eclipse.org/org/documents/epl-v10.php
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.ide.eclipse.adt.debug.launching;

import com.android.ddmlib.AndroidDebugBridge;
import com.android.ddmlib.Client;
import com.android.ddmlib.ClientData;
import com.android.ddmlib.Device;
import com.android.ddmlib.Log;
import com.android.ddmlib.MultiLineReceiver;
import com.android.ddmlib.SyncService;
import com.android.ddmlib.AndroidDebugBridge.IClientChangeListener;
import com.android.ddmlib.AndroidDebugBridge.IDebugBridgeChangeListener;
import com.android.ddmlib.AndroidDebugBridge.IDeviceChangeListener;
import com.android.ddmlib.SyncService.SyncResult;
import com.android.ide.eclipse.adt.AdtPlugin;
import com.android.ide.eclipse.adt.debug.launching.DeviceChooserDialog.DeviceChooserResponse;
import com.android.ide.eclipse.adt.debug.ui.EmulatorConfigTab;
import com.android.ide.eclipse.adt.debug.ui.SkinRepository;
import com.android.ide.eclipse.adt.project.ProjectHelper;
import com.android.ide.eclipse.common.project.AndroidManifestHelper;

import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.debug.core.DebugPlugin;
import org.eclipse.debug.core.ILaunchConfiguration;
import org.eclipse.debug.core.ILaunchConfigurationType;
import org.eclipse.debug.core.ILaunchConfigurationWorkingCopy;
import org.eclipse.debug.core.ILaunchManager;
import org.eclipse.debug.core.model.IDebugTarget;
import org.eclipse.debug.ui.DebugUITools;
import org.eclipse.jdt.launching.IJavaLaunchConfigurationConstants;
import org.eclipse.jdt.launching.IVMConnector;
import org.eclipse.jdt.launching.JavaRuntime;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.preference.IPreferenceStore;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Controls the launch of Android application either on a device or on the
 * emulator. If an emulator is already running, this class will attempt to reuse
 * it.
 */
public final class AndroidLaunchController implements IDebugBridgeChangeListener,
        IDeviceChangeListener, IClientChangeListener {
    
    private static final String FLAG_NETDELAY = "-netdelay"; //$NON-NLS-1$
    private static final String FLAG_NETSPEED = "-netspeed"; //$NON-NLS-1$
    private static final String FLAG_SKIN = "-skin"; //$NON-NLS-1$
    private static final String FLAG_WIPE_DATA = "-wipe-data"; //$NON-NLS-1$
    private static final String FLAG_NO_BOOT_ANIM = "-no-boot-anim"; //$NON-NLS-1$

    private static final int MAX_ATTEMPT_COUNT = 5;

    private final static Pattern sAmErrorType = Pattern.compile("Error type (\\d+)"); //$NON-NLS-1$

    /**
     * A delayed launch waiting for a device to be present or ready before the
     * application is launched.
     */
    static final class DelayedLaunchInfo {
        /** The device on which to launch the app */
        Device mDevice = null;

        /** The eclipse project */
        IProject mProject;

        /** Package name */
        String mPackageName;

        /** fully qualified name of the activity */
        String mActivity;

        /** IFile to the package (.apk) file */
        IFile mPackageFile;
        
        /** Debuggable attribute of the manifest file. */
        Boolean mDebuggable = null;
        
        InstallRetryMode mRetryMode = InstallRetryMode.NEVER;
        
        /**
         * Launch action. See {@link LaunchConfigDelegate#ACTION_DEFAULT},
         * {@link LaunchConfigDelegate#ACTION_ACTIVITY},
         * {@link LaunchConfigDelegate#ACTION_DO_NOTHING}
         */
        int mLaunchAction;

        /** the launch object */
        AndroidLaunch mLaunch;

        /** the monitor object */
        IProgressMonitor mMonitor;

        /** debug mode flag */
        boolean mDebugMode;

        int mAttemptCount = 0;

        boolean mCancelled = false;

        /** Basic constructor with activity and package info. */
        public DelayedLaunchInfo(IProject project, String packageName, String activity,
                IFile pack, Boolean debuggable, int launchAction, AndroidLaunch launch,
                IProgressMonitor monitor) {
            mProject = project;
            mPackageName = packageName;
            mActivity = activity;
            mPackageFile = pack;
            mLaunchAction = launchAction;
            mLaunch = launch;
            mMonitor = monitor;
            mDebuggable = debuggable;
        }
    }
    
    /**
     * Map to store {@link ILaunchConfiguration} objects that must be launched as simple connection
     * to running application. The integer is the port on which to connect. 
     * <b>ALL ACCESS MUST BE INSIDE A <code>synchronized (sListLock)</code> block!</b>
     */
    private final static HashMap<ILaunchConfiguration, Integer> sRunningAppMap =
        new HashMap<ILaunchConfiguration, Integer>();

    private final static Object sListLock = sRunningAppMap;

    /**
     * List of {@link DelayedLaunchInfo} waiting for an emulator to connect.
     * <p>Once an emulator has connected, {@link DelayedLaunchInfo#mDevice} is set and the
     * DelayedLaunchInfo object is moved to {@link AndroidLaunchController#mWaitingForReadyEmulatorList}.
     * <b>ALL ACCESS MUST BE INSIDE A <code>synchronized (sListLock)</code> block!</b>
     */
    private final ArrayList<DelayedLaunchInfo> mWaitingForEmulatorLaunches =
        new ArrayList<DelayedLaunchInfo>();

    /**
     * List of application waiting to be launched on a device/emulator.<br>
     * <b>ALL ACCESS MUST BE INSIDE A <code>synchronized (sListLock)</code> block!</b>
     * */
    private final ArrayList<DelayedLaunchInfo> mWaitingForReadyEmulatorList =
        new ArrayList<DelayedLaunchInfo>();
    
    /**
     * Application waiting to show up as waiting for debugger.
     * <b>ALL ACCESS MUST BE INSIDE A <code>synchronized (sListLock)</code> block!</b>
     */
    private final ArrayList<DelayedLaunchInfo> mWaitingForDebuggerApplications =
        new ArrayList<DelayedLaunchInfo>();
    
    /**
     * List of clients that have appeared as waiting for debugger before their name was available.
     * <b>ALL ACCESS MUST BE INSIDE A <code>synchronized (sListLock)</code> block!</b>
     */
    private final ArrayList<Client> mUnknownClientsWaitingForDebugger = new ArrayList<Client>();
    
    /** static instance for singleton */
    private static AndroidLaunchController sThis = new AndroidLaunchController();
    
    enum InstallRetryMode {
        NEVER, ALWAYS, PROMPT;  
    }

    /**
     * Represents a launch configuration.
     */
    static final class AndroidLaunchConfiguration {
        
        /**
         * Launch action. See {@link LaunchConfigDelegate#ACTION_DEFAULT},
         * {@link LaunchConfigDelegate#ACTION_ACTIVITY},
         * {@link LaunchConfigDelegate#ACTION_DO_NOTHING}
         */
        public int mLaunchAction = LaunchConfigDelegate.DEFAULT_LAUNCH_ACTION;
        
        public static final boolean AUTO_TARGET_MODE = true;

        /**
         * Target selection mode.
         * <ul>
         * <li><code>true</code>: automatic mode, see {@link #AUTO_TARGET_MODE}</li>
         * <li><code>false</code>: manual mode</li>
         * </ul>
         */
        public boolean mTargetMode = LaunchConfigDelegate.DEFAULT_TARGET_MODE;

        /**
         * Indicates whether the emulator should be called with -wipe-data
         */
        public boolean mWipeData = LaunchConfigDelegate.DEFAULT_WIPE_DATA;

        /**
         * Indicates whether the emulator should be called with -no-boot-anim
         */
        public boolean mNoBootAnim = LaunchConfigDelegate.DEFAULT_NO_BOOT_ANIM;
        
        /**
         * Screen size parameters.
         * This value can be provided to the emulator directly for the option "-skin"
         */
        public String mSkin = null;

        public String mNetworkSpeed = EmulatorConfigTab.getSpeed(
                LaunchConfigDelegate.DEFAULT_SPEED);
        public String mNetworkDelay = EmulatorConfigTab.getDelay(
                LaunchConfigDelegate.DEFAULT_DELAY);

        /**
         * Optional custom command line parameter to launch the emulator
         */
        public String mEmulatorCommandLine;

        /**
         * Initialized the structure from an ILaunchConfiguration object.
         * @param config
         */
        public void set(ILaunchConfiguration config) {
            try {
                mLaunchAction = config.getAttribute(LaunchConfigDelegate.ATTR_LAUNCH_ACTION,
                        mLaunchAction);
            } catch (CoreException e1) {
                // nothing to be done here, we'll use the default value
            }

            try {
                mTargetMode = config.getAttribute(LaunchConfigDelegate.ATTR_TARGET_MODE,
                        mTargetMode);
            } catch (CoreException e) {
                // nothing to be done here, we'll use the default value
            }

            try {
                mSkin = config.getAttribute(LaunchConfigDelegate.ATTR_SKIN, mSkin);
                if (mSkin == null) {
                    mSkin = SkinRepository.getInstance().checkSkin(
                            LaunchConfigDelegate.DEFAULT_SKIN);
                } else {
                    mSkin = SkinRepository.getInstance().checkSkin(mSkin);
                }
            } catch (CoreException e) {
                mSkin = SkinRepository.getInstance().checkSkin(LaunchConfigDelegate.DEFAULT_SKIN);
            }

            int index = LaunchConfigDelegate.DEFAULT_SPEED;
            try {
                index = config.getAttribute(LaunchConfigDelegate.ATTR_SPEED, index);
            } catch (CoreException e) {
                // nothing to be done here, we'll use the default value
            }
            mNetworkSpeed = EmulatorConfigTab.getSpeed(index);

            index = LaunchConfigDelegate.DEFAULT_DELAY;
            try {
                index = config.getAttribute(LaunchConfigDelegate.ATTR_DELAY, index);
            } catch (CoreException e) {
                // nothing to be done here, we'll use the default value
            }
            mNetworkDelay = EmulatorConfigTab.getDelay(index);

            try {
                mEmulatorCommandLine = config.getAttribute(
                        LaunchConfigDelegate.ATTR_COMMANDLINE, ""); //$NON-NLS-1$
            } catch (CoreException e) {
                // lets not do anything here, we'll use the default value
            }

            try {
                mWipeData = config.getAttribute(LaunchConfigDelegate.ATTR_WIPE_DATA, mWipeData);
            } catch (CoreException e) {
                // nothing to be done here, we'll use the default value
            }

            try {
                mNoBootAnim = config.getAttribute(LaunchConfigDelegate.ATTR_NO_BOOT_ANIM,
                                                  mNoBootAnim);
            } catch (CoreException e) {
                // nothing to be done here, we'll use the default value
            }
        }
    }

    /**
     * Output receiver for am process (activity Manager);
     */
    private final class AMReceiver extends MultiLineReceiver {
        private DelayedLaunchInfo mLaunchInfo;
        private Device mDevice;

        /**
         * Basic constructor.
         * @param launchInfo The launch info associated with the am process.
         * @param device The device on which the launch is done.
         */
        public AMReceiver(DelayedLaunchInfo launchInfo, Device device) {
            mLaunchInfo = launchInfo;
            mDevice = device;
        }

        @Override
        public void processNewLines(String[] lines) {
            // first we check if one starts with error
            ArrayList<String> array = new ArrayList<String>();
            boolean error = false;
            boolean warning = false;
            for (String s : lines) {
                // ignore empty lines.
                if (s.length() == 0) {
                    continue;
                }

                // check for errors that output an error type, if the attempt count is still
                // valid. If not the whole text will be output in the console
                if (mLaunchInfo.mAttemptCount < MAX_ATTEMPT_COUNT &&
                        mLaunchInfo.mCancelled == false) {
                    Matcher m = sAmErrorType.matcher(s);
                    if (m.matches()) {
                        // get the error type
                        int type = Integer.parseInt(m.group(1));

                        final int waitTime = 3;
                        String msg;

                        switch (type) {
                            case 1:
                                /* Intended fall through */
                            case 2:
                                msg = String.format(
                                        "Device not ready. Waiting %1$d seconds before next attempt.",
                                        waitTime);
                                break;
                            case 3:
                                msg = String.format(
                                        "New package not yet registered with the system. Waiting %1$d seconds before next attempt.",
                                        waitTime);
                                break;
                            default:
                                msg = String.format(
                                        "Device not ready (%2$d). Waiting %1$d seconds before next attempt.",
                                        waitTime, type);
                                break;

                        }

                        AdtPlugin.printToConsole(mLaunchInfo.mProject, msg);

                        // launch another thread, that waits a bit and attempts another launch
                        new Thread("Delayed Launch attempt") {
                            @Override
                            public void run() {
                                try {
                                    sleep(waitTime * 1000);
                                } catch (InterruptedException e) {
                                }

                                launchApp(mLaunchInfo, mDevice);
                            }
                        }.start();

                        // no need to parse the rest
                        return;
                    }
                }

                // check for error if needed
                if (error == false && s.startsWith("Error:")) { //$NON-NLS-1$
                    error = true;
                }
                if (warning == false && s.startsWith("Warning:")) { //$NON-NLS-1$
                    warning = true;
                }

                // add the line to the list
                array.add("ActivityManager: " + s); //$NON-NLS-1$
            }

            // then we display them in the console
            if (warning || error) {
                AdtPlugin.printErrorToConsole(mLaunchInfo.mProject, array.toArray());
            } else {
                AdtPlugin.printToConsole(mLaunchInfo.mProject, array.toArray());
            }

            // if error then we cancel the launch, and remove the delayed info
            if (error) {
                mLaunchInfo.mLaunch.stopLaunch();
                synchronized (sListLock) {
                    mWaitingForReadyEmulatorList.remove(mLaunchInfo);
                }
            }
        }

        public boolean isCancelled() {
            return false;
        }
    }

    /**
     * Output receiver for "pm install package.apk" command line.
     */
    private final static class InstallReceiver extends MultiLineReceiver {
        
        private final static String SUCCESS_OUTPUT = "Success"; //$NON-NLS-1$
        private final static Pattern FAILURE_PATTERN = Pattern.compile("Failure\\s+\\[(.*)\\]"); //$NON-NLS-1$
        
        private String mSuccess = null;
        
        public InstallReceiver() {
        }

        @Override
        public void processNewLines(String[] lines) {
            for (String line : lines) {
                if (line.length() > 0) {
                    if (line.startsWith(SUCCESS_OUTPUT)) {
                        mSuccess = null;
                    } else {
                        Matcher m = FAILURE_PATTERN.matcher(line);
                        if (m.matches()) {
                            mSuccess = m.group(1);
                        }
                    }
                }
            }
        }

        public boolean isCancelled() {
            return false;
        }

        public String getSuccess() {
            return mSuccess;
        }
    }


    /** private constructor to enforce singleton */
    private AndroidLaunchController() {
        AndroidDebugBridge.addDebugBridgeChangeListener(this);
        AndroidDebugBridge.addDeviceChangeListener(this);
        AndroidDebugBridge.addClientChangeListener(this);
    }

    /**
     * Returns the singleton reference.
     */
    public static AndroidLaunchController getInstance() {
        return sThis;
    }


    /**
     * Launches a remote java debugging session on an already running application
     * @param project The project of the application to debug.
     * @param debugPort The port to connect the debugger to.
     */
    public static void debugRunningApp(IProject project, int debugPort) {
        // get an existing or new launch configuration
        ILaunchConfiguration config = AndroidLaunchController.getLaunchConfig(project);
        
        if (config != null) {
            setPortLaunchConfigAssociation(config, debugPort);
            
            // and launch
            DebugUITools.launch(config, ILaunchManager.DEBUG_MODE);
        }
    }
    
    /**
     * Returns an {@link ILaunchConfiguration} for the specified {@link IProject}.
     * @param project the project
     * @return a new or already existing <code>ILaunchConfiguration</code> or null if there was
     * an error when creating a new one.
     */
    public static ILaunchConfiguration getLaunchConfig(IProject project) {
        // get the launch manager
        ILaunchManager manager = DebugPlugin.getDefault().getLaunchManager();

        // now get the config type for our particular android type.
        ILaunchConfigurationType configType = manager.getLaunchConfigurationType(
                        LaunchConfigDelegate.ANDROID_LAUNCH_TYPE_ID);

        String name = project.getName();

        // search for an existing launch configuration
        ILaunchConfiguration config = findConfig(manager, configType, name);

        // test if we found one or not
        if (config == null) {
            // Didn't find a matching config, so we make one.
            // It'll be made in the "working copy" object first.
            ILaunchConfigurationWorkingCopy wc = null;

            try {
                // make the working copy object
                wc = configType.newInstance(null,
                        manager.generateUniqueLaunchConfigurationNameFrom(name));

                // set the project name
                wc.setAttribute(IJavaLaunchConfigurationConstants.ATTR_PROJECT_NAME, name);

                // set the launch mode to default.
                wc.setAttribute(LaunchConfigDelegate.ATTR_LAUNCH_ACTION,
                        LaunchConfigDelegate.DEFAULT_LAUNCH_ACTION);
                
                // set default target mode
                wc.setAttribute(LaunchConfigDelegate.ATTR_TARGET_MODE,
                        LaunchConfigDelegate.DEFAULT_TARGET_MODE);

                // set the default network speed
                wc.setAttribute(LaunchConfigDelegate.ATTR_SPEED,
                        LaunchConfigDelegate.DEFAULT_SPEED);

                // and delay
                wc.setAttribute(LaunchConfigDelegate.ATTR_DELAY,
                        LaunchConfigDelegate.DEFAULT_DELAY);
                
                // default skin
                wc.setAttribute(LaunchConfigDelegate.ATTR_SKIN, LaunchConfigDelegate.DEFAULT_SKIN);
                
                // default wipe data mode
                wc.setAttribute(LaunchConfigDelegate.ATTR_WIPE_DATA,
                        LaunchConfigDelegate.DEFAULT_WIPE_DATA);
                
                // default disable boot animation option
                wc.setAttribute(LaunchConfigDelegate.ATTR_NO_BOOT_ANIM,
                        LaunchConfigDelegate.DEFAULT_NO_BOOT_ANIM);
                
                // set default emulator options
                IPreferenceStore store = AdtPlugin.getDefault().getPreferenceStore();
                String emuOptions = store.getString(AdtPlugin.PREFS_EMU_OPTIONS);
                wc.setAttribute(LaunchConfigDelegate.ATTR_COMMANDLINE, emuOptions);
                
                // map the config and the project
                wc.setMappedResources(getResourcesToMap(project));

                // save the working copy to get the launch config object which we return.
                return wc.doSave();

            } catch (CoreException e) {
                String msg = String.format(
                        "Failed to create a Launch config for project '%1$s': %2$s",
                        project.getName(), e.getMessage());
                AdtPlugin.printErrorToConsole(project, msg);

                // no launch!
                return null;
            }
        }
        
        return config;
    }
    
    /**
     * Returns the list of resources to map to a Launch Configuration.
     * @param project the project associated to the launch configuration.
     */
    public static IResource[] getResourcesToMap(IProject project) {
        ArrayList<IResource> array = new ArrayList<IResource>(2);
        array.add(project);
        
        AndroidManifestHelper helper = new AndroidManifestHelper(project);
        IFile manifest = helper.getManifestIFile();
        if (manifest != null) {
            array.add(manifest);
        }
        
        return array.toArray(new IResource[array.size()]);
    }

    /**
     * Launches an android app on the device or emulator
     *
     * @param project The project we're launching
     * @param mode the mode in which to launch, one of the mode constants
     *      defined by <code>ILaunchManager</code> - <code>RUN_MODE</code> or
     *      <code>DEBUG_MODE</code>.
     * @param apk the resource to the apk to launch.
     * @param debuggable 
     * @param activity the class to provide to am to launch
     * @param config the launch configuration
     * @param launch the launch object
     */
    public void launch(final IProject project, String mode, IFile apk,
            String packageName, Boolean debuggable, String activity,
            final AndroidLaunchConfiguration config, final AndroidLaunch launch,
            IProgressMonitor monitor) {
        
        String message;
        if (config.mLaunchAction == LaunchConfigDelegate.ACTION_DO_NOTHING) {
            message = String.format("Only Syncing Application Package");
        } else {
            message = String.format("Launching: %1$s", activity);
        }
        AdtPlugin.printToConsole(project, message);

        // create the launch info
        final DelayedLaunchInfo launchInfo = new DelayedLaunchInfo(project, packageName,
                activity, apk, debuggable, config.mLaunchAction, launch, monitor);

        // set the debug mode
        launchInfo.mDebugMode = mode.equals(ILaunchManager.DEBUG_MODE);

        // device chooser response.
        final DeviceChooserResponse response = new DeviceChooserResponse();
        
        if (config.mTargetMode == AndroidLaunchConfiguration.AUTO_TARGET_MODE) {
            // if we are in automatic target mode, we need to find the current devices
            Device[] devices = AndroidDebugBridge.getBridge().getDevices();
            
            // depending on the number of devices, we'll simulate an automatic choice
            // from the device chooser or simply show up the device chooser.
            if (devices.length == 0) {
                // if zero devices, we launch the device.
                AdtPlugin.printToConsole(project, "Automatic Target Mode: launching new emulator.");
                response.mustContinue = true;
                response.mustLaunchEmulator = true;
                continueLaunch(response, project, launch, launchInfo, config);
                return;
            } else if (devices.length == 1) {
                response.mustContinue = true;
                response.mustLaunchEmulator = false;
                response.deviceToUse = devices[0];

                if (response.deviceToUse.isEmulator()) {
                    message = String.format("Automatic Target Mode: using existing emulator: %1$s",
                            response.deviceToUse);
                } else {
                    message = String.format("Automatic Target Mode: using existing device: %1$s",
                            response.deviceToUse);
                }
                AdtPlugin.printToConsole(project, message);

                continueLaunch(response, project, launch, launchInfo, config);
                return;
            }

            // if more than one device, we'll bring up the DeviceChooser dialog below.
            AdtPlugin.printToConsole(project,
                    "Automatic Target Mode: user selection for 2+ devices.");
        }
        
        // bring up the device chooser.
        AdtPlugin.getDisplay().asyncExec(new Runnable() {
            public void run() {
                DeviceChooserDialog dialog = new DeviceChooserDialog(
                        AdtPlugin.getDisplay().getActiveShell());
                dialog.open(response, project, launch, launchInfo, config);
            }
        });
        
        return;
    }
    
    /**
     * Continues the launch based on the DeviceChooser response.
     * @param response the device chooser response
     * @param project The project being launched
     * @param launch The eclipse launch info
     * @param launchInfo The {@link DelayedLaunchInfo}
     * @param config The config needed to start a new emulator.
     */
    void continueLaunch(final DeviceChooserResponse response, final IProject project,
            final AndroidLaunch launch, final DelayedLaunchInfo launchInfo,
            final AndroidLaunchConfiguration config) {
        if (response.mustContinue == false) {
            AdtPlugin.printErrorToConsole(project, "Launch canceled!");
            launch.stopLaunch();
            return;
        }

        // Since this is called from the DeviceChooserDialog open, we are in the UI
        // thread. So we spawn a temporary new one to finish the launch.
        new Thread() {
            @Override
            public void run() {
                if (response.mustLaunchEmulator) {
                    // there was no selected device, we start a new emulator.
                    synchronized (sListLock) {
                        mWaitingForEmulatorLaunches.add(launchInfo);
                        AdtPlugin.printToConsole(project, "Launching a new emulator.");
                        boolean status = launchEmulator(config);
            
                        if (status == false) {
                            // launching the emulator failed!
                            AdtPlugin.displayError("Emulator Launch",
                                    "Couldn't launch the emulator! Make sure the SDK directory is properly setup and the emulator is not missing.");
            
                            // stop the launch and return
                            mWaitingForEmulatorLaunches.remove(launchInfo);
                            launch.stopLaunch();
                            return;
                        }
                        
                        return;
                    }
                } else if (response.deviceToUse != null) {
                    launchInfo.mDevice = response.deviceToUse;
                    simpleLaunch(launchInfo, response.deviceToUse);
                }
            }
        }.start();
    }
    
    /**
     * Queries for a debugger port for a specific {@link ILaunchConfiguration}.
     * <p/>
     * If the configuration and a debugger port where added through
     * {@link #setPortLaunchConfigAssociation(ILaunchConfiguration, int)}, then this method
     * will return the debugger port, and remove the configuration from the list.
     * @param launchConfig the {@link ILaunchConfiguration}
     * @return the debugger port or {@link LaunchConfigDelegate#INVALID_DEBUG_PORT} if the
     * configuration was not setup.
     */
    static int getPortForConfig(ILaunchConfiguration launchConfig) {
        synchronized (sListLock) {
            Integer port = sRunningAppMap.get(launchConfig);
            if (port != null) {
                sRunningAppMap.remove(launchConfig);
                return port;
            }
        }
        
        return LaunchConfigDelegate.INVALID_DEBUG_PORT;
    }
    
    /**
     * Set a {@link ILaunchConfiguration} and its associated debug port, in the list of
     * launch config to connect directly to a running app instead of doing full launch (sync,
     * launch, and connect to).
     * @param launchConfig the {@link ILaunchConfiguration} object.
     * @param port The debugger port to connect to.
     */
    private static void setPortLaunchConfigAssociation(ILaunchConfiguration launchConfig,
            int port) {
        synchronized (sListLock) {
            sRunningAppMap.put(launchConfig, port);
        }
    }
    
    private void checkBuildInfo(DelayedLaunchInfo launchInfo, Device device) {
        if (device != null) {
            // get the SDK build
            String sdkBuild = AdtPlugin.getSdkApiVersion();

            // can only complain if the sdkBuild is known
            if (sdkBuild != null) {
                
                String deviceVersion = device.getProperty(Device.PROP_BUILD_VERSION);

                if (deviceVersion == null) {
                    AdtPlugin.printToConsole(launchInfo.mProject, "WARNING: Unknown device API version!");
                } else {
                    if (sdkBuild.equals(deviceVersion) == false) {
                        // TODO do a proper check, including testing the content of the uses-sdk string in the manifest to detect real incompatibility.
                        String msg = String.format(
                                "WARNING: Device API version (%1$s) does not match SDK API version (%2$s)",
                                deviceVersion, sdkBuild);
                        AdtPlugin.printErrorToConsole(launchInfo.mProject, msg);
                    }
                }
            } else {
                AdtPlugin.printToConsole(launchInfo.mProject, "WARNING: Unknown SDK API version!");
            }
            
            // now checks that the device/app can be debugged (if needed)
            if (device.isEmulator() == false && launchInfo.mDebugMode) {
                String debuggableDevice = device.getProperty(Device.PROP_DEBUGGABLE);
                if (debuggableDevice != null && debuggableDevice.equals("0")) { //$NON-NLS-1$
                    // the device is "secure" and requires apps to declare themselves as debuggable!
                    if (launchInfo.mDebuggable == null) {
                        String message1 = String.format(
                                "Device '%1$s' requires that applications explicitely declare themselves as debuggable in their manifest.",
                                device.getSerialNumber());
                        String message2 = String.format("Application '%1$s' does not have the attribute 'debuggable' set to TRUE in its manifest and cannot be debugged.",
                                launchInfo.mPackageName);
                        AdtPlugin.printErrorToConsole(launchInfo.mProject, message1, message2);
                        
                        // because am -D does not check for ro.debuggable and the
                        // 'debuggable' attribute, it is important we do not use the -D option
                        // in this case or the app will wait for a debugger forever and never
                        // really launch.
                        launchInfo.mDebugMode = false;
                    } else if (launchInfo.mDebuggable == Boolean.FALSE) {
                        String message = String.format("Application '%1$s' has its 'debuggable' attribute set to FALSE and cannot be debugged.",
                                launchInfo.mPackageName);
                        AdtPlugin.printErrorToConsole(launchInfo.mProject, message);

                        // because am -D does not check for ro.debuggable and the
                        // 'debuggable' attribute, it is important we do not use the -D option
                        // in this case or the app will wait for a debugger forever and never
                        // really launch.
                        launchInfo.mDebugMode = false;
                    }
                }
            }
        }
    }

    /**
     * Do a simple launch on the specified device, attempting to sync the new
     * package, and then launching the application. Failed sync/launch will
     * stop the current AndroidLaunch and return false;
     * @param launchInfo
     * @param device
     * @return true if succeed
     */
    private boolean simpleLaunch(DelayedLaunchInfo launchInfo, Device device) {
        checkBuildInfo(launchInfo, device);

        // sync the app
        if (syncApp(launchInfo, device) == false) {
            launchInfo.mLaunch.stopLaunch();
            return false;
        }

        // launch the app
        launchApp(launchInfo, device);

        return true;
    }


    /**
     * Syncs the application on the device/emulator.
     *
     * @param launchInfo The Launch information object.
     * @param device the device on which to sync the application
     * @return true if the install succeeded.
     */
    private boolean syncApp(DelayedLaunchInfo launchInfo, Device device) {
        SyncService sync = device.getSyncService();
        if (sync != null) {
            IPath path = launchInfo.mPackageFile.getLocation();
            String message = String.format("Uploading %1$s onto device '%2$s'",
                    path.lastSegment(), device.getSerialNumber());
            AdtPlugin.printToConsole(launchInfo.mProject, message);

            String osLocalPath = path.toOSString();
            String apkName = launchInfo.mPackageFile.getName();
            String remotePath = "/data/local/tmp/" + apkName; //$NON-NLS-1$

            SyncResult result = sync.pushFile(osLocalPath, remotePath,
                    SyncService.getNullProgressMonitor());

            if (result.getCode() != SyncService.RESULT_OK) {
                String msg = String.format("Failed to upload %1$s on '%2$s': %3$s",
                        apkName, device.getSerialNumber(), result.getMessage());
                AdtPlugin.printErrorToConsole(launchInfo.mProject, msg);
                return false;
            }

            // Now that the package is uploaded, we can install it properly.
            // This will check that there isn't another apk declaring the same package, or
            // that another install used a different key.
            boolean installResult =  installPackage(launchInfo, remotePath, device);
            
            // now we delete the app we sync'ed
            try {
                device.executeShellCommand("rm " + remotePath, new MultiLineReceiver() { //$NON-NLS-1$
                    @Override
                    public void processNewLines(String[] lines) {
                        // pass
                    }
                    public boolean isCancelled() {
                        return false;
                    }
                });
            } catch (IOException e) {
                AdtPlugin.printErrorToConsole(launchInfo.mProject, String.format(
                        "Failed to delete temporary package: %1$s", e.getMessage()));
                return false;
            }
            
            return installResult;
        }

        String msg = String.format(
                "Failed to upload %1$s on device '%2$s': Unable to open sync connection!",
                launchInfo.mPackageFile.getName(), device.getSerialNumber());
        AdtPlugin.printErrorToConsole(launchInfo.mProject, msg);

        return false;
    }

    /**
     * Installs the application package that was pushed to a temporary location on the device.
     * @param launchInfo The launch information
     * @param remotePath The remote path of the package.
     * @param device The device on which the launch is done.
     */
    private boolean installPackage(DelayedLaunchInfo launchInfo, final String remotePath,
            final Device device) {

        String message = String.format("Installing %1$s...", launchInfo.mPackageFile.getName());
        AdtPlugin.printToConsole(launchInfo.mProject, message);

        try {
            String result = doInstall(launchInfo, remotePath, device, false /* reinstall */);
            
            /* For now we force to retry the install (after uninstalling) because there's no
             * other way around it: adb install does not want to update a package w/o uninstalling
             * the old one first!
             */
            return checkInstallResult(result, device, launchInfo, remotePath,
                    InstallRetryMode.ALWAYS);
        } catch (IOException e) {
            // do nothing, we'll return false
        }
        
        return false;
    }

    /**
     * Checks the result of an installation, and takes optional actions based on it.
     * @param result the result string from the installation
     * @param device the device on which the installation occured.
     * @param launchInfo the {@link DelayedLaunchInfo}
     * @param remotePath the temporary path of the package on the device
     * @param retryMode indicates what to do in case, a package already exists.
     * @return <code>true<code> if success, <code>false</code> otherwise.
     * @throws IOException
     */
    private boolean checkInstallResult(String result, Device device, DelayedLaunchInfo launchInfo,
            String remotePath, InstallRetryMode retryMode) throws IOException {
        if (result == null) {
            AdtPlugin.printToConsole(launchInfo.mProject, "Success!");
            return true;
        } else if (result.equals("INSTALL_FAILED_ALREADY_EXISTS")) { //$NON-NLS-1$
            if (retryMode == InstallRetryMode.PROMPT) {
                boolean prompt = AdtPlugin.displayPrompt("Application Install",
                        "A previous installation needs to be uninstalled before the new package can be installed.\nDo you want to uninstall?");
                if (prompt) {
                    retryMode = InstallRetryMode.ALWAYS;
                } else {
                    AdtPlugin.printErrorToConsole(launchInfo.mProject,
                        "Installation error! The package already exists.");
                    return false;
                }
            }

            if (retryMode == InstallRetryMode.ALWAYS) {
                /*
                 * TODO: create a UI that gives the dev the choice to:
                 * - clean uninstall on launch
                 * - full uninstall if application exists.
                 * - soft uninstall if application exists (keeps the app data around).
                 * - always ask (choice of soft-reinstall, full reinstall)
                AdtPlugin.printErrorToConsole(launchInfo.mProject,
                        "Application already exists, uninstalling...");
                String res = doUninstall(device, launchInfo);
                if (res == null) {
                    AdtPlugin.printToConsole(launchInfo.mProject, "Success!");
                } else {
                    AdtPlugin.printErrorToConsole(launchInfo.mProject,
                            String.format("Failed to uninstall: %1$s", res));
                    return false;
                }
                */

                AdtPlugin.printToConsole(launchInfo.mProject,
                        "Application already exists. Attempting to re-install instead...");
                String res = doInstall(launchInfo, remotePath, device, true /* reinstall */);
                return checkInstallResult(res, device, launchInfo, remotePath,
                        InstallRetryMode.NEVER);
            }

            AdtPlugin.printErrorToConsole(launchInfo.mProject,
                    "Installation error! The package already exists.");
        } else if (result.equals("INSTALL_FAILED_INVALID_APK")) { //$NON-NLS-1$
            AdtPlugin.printErrorToConsole(launchInfo.mProject,
                "Installation failed due to invalid APK file!",
                "Please check logcat output for more details.");
        } else if (result.equals("INSTALL_FAILED_INVALID_URI")) { //$NON-NLS-1$
            AdtPlugin.printErrorToConsole(launchInfo.mProject,
                "Installation failed due to invalid URI!",
                "Please check logcat output for more details.");
        } else if (result.equals("INSTALL_FAILED_COULDNT_COPY")) { //$NON-NLS-1$
            AdtPlugin.printErrorToConsole(launchInfo.mProject,
                String.format("Installation failed: Could not copy %1$s to its final location!",
                        launchInfo.mPackageFile.getName()),
                "Please check logcat output for more details.");
        } else if (result.equals("INSTALL_PARSE_FAILED_INCONSISTENT_CERTIFICATES")) {
            AdtPlugin.printErrorToConsole(launchInfo.mProject,
                    "Re-installation failed due to different application signatures.",
                    "You must perform a full uninstall of the application. WARNING: This will remove the application data!",
                    String.format("Please execute 'adb uninstall %1$s' in a shell.", launchInfo.mPackageName));
        } else {
            AdtPlugin.printErrorToConsole(launchInfo.mProject,
                String.format("Installation error: %1$s", result),
                "Please check logcat output for more details.");
        }

        return false;
    }

    /**
     * Performs the uninstallation of an application.
     * @param device the device on which to install the application.
     * @param launchInfo the {@link DelayedLaunchInfo}.
     * @return a {@link String} with an error code, or <code>null</code> if success.
     * @throws IOException 
     */
    @SuppressWarnings("unused")
    private String doUninstall(Device device, DelayedLaunchInfo launchInfo) throws IOException {
        InstallReceiver receiver = new InstallReceiver();
        try {
            device.executeShellCommand("pm uninstall " + launchInfo.mPackageName, //$NON-NLS-1$
                    receiver);
        } catch (IOException e) {
            String msg = String.format(
                    "Failed to uninstall %1$s: %2$s", launchInfo.mPackageName, e.getMessage());
            AdtPlugin.printErrorToConsole(launchInfo.mProject, msg);
            throw e;
        }
        
        return receiver.getSuccess();
    }

    /**
     * Performs the installation of an application whose package has been uploaded on the device.
     * <p/>Before doing it, if the application is already running on the device, it is killed. 
     * @param launchInfo the {@link DelayedLaunchInfo}.
     * @param remotePath the path of the application package in the device tmp folder.
     * @param device the device on which to install the application.
     * @param reinstall 
     * @return a {@link String} with an error code, or <code>null</code> if success.
     * @throws IOException 
     */
    private String doInstall(DelayedLaunchInfo launchInfo, final String remotePath,
            final Device device, boolean reinstall) throws IOException {
        // kill running application
        Client application = device.getClient(launchInfo.mPackageName);
        if (application != null) {
            application.kill();
        }
        
        InstallReceiver receiver = new InstallReceiver();
        try {
            String cmd = String.format(
                    reinstall ? "pm install -r \"%1$s\"" : "pm install \"%1$s\"", //$NON-NLS-1$ //$NON-NLS-2$
                    remotePath); //$NON-NLS-1$ //$NON-NLS-2$
            device.executeShellCommand(cmd, receiver);
        } catch (IOException e) {
            String msg = String.format(
                    "Failed to install %1$s on device '%2$s': %3$s",
                    launchInfo.mPackageFile.getName(), device.getSerialNumber(), e.getMessage());
            AdtPlugin.printErrorToConsole(launchInfo.mProject, msg);
            throw e;
        }
        
        return receiver.getSuccess();
    }
    
    /**
     * launches an application on a device or emulator
     *
     * @param classToLaunch the fully-qualified name of the activity to launch
     * @param device the device or emulator to launch the application on
     */
    private void launchApp(final DelayedLaunchInfo info, Device device) {
        // if we're not supposed to do anything, just stop the Launch item and return;
        if (info.mLaunchAction == LaunchConfigDelegate.ACTION_DO_NOTHING) {
            String msg = String.format("%1$s installed on device",
                    info.mPackageFile.getFullPath().toOSString());
            AdtPlugin.printToConsole(info.mProject, msg, "Done!");
            info.mLaunch.stopLaunch();
            return;
        }
        try {
            String msg = String.format("Starting activity %1$s on device ", info.mActivity,
                    info.mDevice);
            AdtPlugin.printToConsole(info.mProject, msg);

            // In debug mode, we need to add the info to the list of application monitoring
            // client changes.
            if (info.mDebugMode) {
                synchronized (sListLock) {
                    if (mWaitingForDebuggerApplications.contains(info) == false) {
                        mWaitingForDebuggerApplications.add(info);
                    }
                }
            }

            // increment launch attempt count, to handle retries and timeouts
            info.mAttemptCount++;

            // now we actually launch the app.
            device.executeShellCommand("am start" //$NON-NLS-1$
                    + (info.mDebugMode ? " -D" //$NON-NLS-1$
                            : "") //$NON-NLS-1$
                    + " -n " //$NON-NLS-1$
                    + info.mPackageName + "/" //$NON-NLS-1$
                    + info.mActivity.replaceAll("\\$", "\\\\\\$"), //$NON-NLS-1$ //$NON-NLS-2$
                    new AMReceiver(info, device));

            // if the app is not a debug app, we need to do some clean up, as
            // the process is done!
            if (info.mDebugMode == false) {
                // stop the launch object, since there's no debug, and it can't
                // provide any control over the app
                info.mLaunch.stopLaunch();
            }
        } catch (IOException e) {
            // something went wrong trying to launch the app.
            // lets stop the Launch
            info.mLaunch.stopLaunch();

            // and remove it from the list of app waiting for debuggers
            synchronized (sListLock) {
                mWaitingForDebuggerApplications.remove(info);
            }
        }
    }

    private boolean launchEmulator(AndroidLaunchConfiguration config) {

        // split the custom command line in segments
        String[] segs;
        boolean has_wipe_data = false;
        if (config.mEmulatorCommandLine != null && config.mEmulatorCommandLine.length() > 0) {
            segs = config.mEmulatorCommandLine.split("\\s+"); //$NON-NLS-1$

            // we need to remove the empty strings
            ArrayList<String> array = new ArrayList<String>();
            for (String s : segs) {
                if (s.length() > 0) {
                    array.add(s);
                    if (!has_wipe_data && s.equals(FLAG_WIPE_DATA)) {
                        has_wipe_data = true;
                    }
                }
            }

            segs = array.toArray(new String[array.size()]);
        } else {
            segs = new String[0];
        }

        boolean needs_wipe_data = config.mWipeData && !has_wipe_data;
        if (needs_wipe_data) {
            if (!AdtPlugin.displayPrompt("Android Launch", "Are you sure you want to wipe all user data when starting this emulator?")) {
                needs_wipe_data = false;
            }
        }
        
        boolean needs_no_boot_anim = config.mNoBootAnim;
        
        // get the command line
        String[] command = new String[7 + segs.length +
                                      (needs_wipe_data ? 1 : 0) +
                                      (needs_no_boot_anim ? 1 : 0)];
        int index = 0;
        command[index++] = AdtPlugin.getOsAbsoluteEmulator();
        command[index++] = FLAG_SKIN; //$NON-NLS-1$
        command[index++] = config.mSkin;
        command[index++] = FLAG_NETSPEED; //$NON-NLS-1$
        command[index++] = config.mNetworkSpeed;
        command[index++] = FLAG_NETDELAY; //$NON-NLS-1$
        command[index++] = config.mNetworkDelay;
        if (needs_wipe_data) {
            command[index++] = FLAG_WIPE_DATA;
        }
        if (needs_no_boot_anim) {
            command[index++] = FLAG_NO_BOOT_ANIM;
        }
        for (String s : segs) {
            command[index++] = s;
        }

        // launch the emulator
        try {
            Process process = Runtime.getRuntime().exec(command);
            grabEmulatorOutput(process);
        } catch (IOException e) {
            return false;
        }

        return true;
    }
    
    /**
     * Looks for and returns an existing {@link ILaunchConfiguration} object for a
     * specified project.
     * @param manager The {@link ILaunchManager}.
     * @param type The {@link ILaunchConfigurationType}.
     * @param projectName The name of the project
     * @return an existing <code>ILaunchConfiguration</code> object matching the project, or
     *      <code>null</code>.
     */
    private static ILaunchConfiguration findConfig(ILaunchManager manager,
            ILaunchConfigurationType type, String projectName) {
        try {
            ILaunchConfiguration[] configs = manager.getLaunchConfigurations(type);

            for (ILaunchConfiguration config : configs) {
                if (config.getAttribute(IJavaLaunchConfigurationConstants.ATTR_PROJECT_NAME,
                        "").equals(projectName)) {  //$NON-NLS-1$
                    return config;
                }
            }
        } catch (CoreException e) {
            MessageDialog.openError(AdtPlugin.getDisplay().getActiveShell(),
                    "Launch Error", e.getStatus().getMessage());
        }

        // didn't find anything that matches. Return null
        return null;

    }


    /**
     * Connects a remote debugger on the specified port.
     * @param debugPort The port to connect the debugger to
     * @param launch The associated AndroidLaunch object.
     * @param monitor A Progress monitor
     * @return false if cancelled by the monitor
     * @throws CoreException
     */
    public static boolean connectRemoteDebugger(int debugPort,
            AndroidLaunch launch, IProgressMonitor monitor)
                throws CoreException {
        // get some default parameters.
        int connectTimeout = JavaRuntime.getPreferences().getInt(JavaRuntime.PREF_CONNECT_TIMEOUT);

        HashMap<String, String> newMap = new HashMap<String, String>();

        newMap.put("hostname", "localhost");  //$NON-NLS-1$ //$NON-NLS-2$

        newMap.put("port", Integer.toString(debugPort)); //$NON-NLS-1$

        newMap.put("timeout", Integer.toString(connectTimeout));

        // get the default VM connector
        IVMConnector connector = JavaRuntime.getDefaultVMConnector();

        // connect to remote VM
        connector.connect(newMap, monitor, launch);

        // check for cancellation
        if (monitor.isCanceled()) {
            IDebugTarget[] debugTargets = launch.getDebugTargets();
            for (IDebugTarget target : debugTargets) {
                if (target.canDisconnect()) {
                    target.disconnect();
                }
            }
            return false;
        }

        return true;
    }

    /**
     * Launch a new thread that connects a remote debugger on the specified port.
     * @param debugPort The port to connect the debugger to
     * @param launch The associated AndroidLaunch object.
     * @param monitor A Progress monitor
     * @see connectRemoveDebugger()
     */
    public static void launchRemoteDebugger(final ILaunchConfiguration config,
            final int debugPort, final AndroidLaunch androidLaunch,
            final IProgressMonitor monitor) {
        new Thread("Debugger connection") { //$NON-NLS-1$
            @Override
            public void run() {
                try {
                    connectRemoteDebugger(debugPort, androidLaunch, monitor);
                } catch (CoreException e) {
                    androidLaunch.stopLaunch();
                }
                monitor.done();
            }
        }.start();
    }

    /**
     * Sent when a new {@link AndroidDebugBridge} is started.
     * <p/>
     * This is sent from a non UI thread.
     * @param bridge the new {@link AndroidDebugBridge} object.
     * 
     * @see IDebugBridgeChangeListener#serverChanged(AndroidDebugBridge)
     */
    public void bridgeChanged(AndroidDebugBridge bridge) {
        // The adb server has changed. We cancel any pending launches.
        String message1 = "adb server change: cancelling '%1$s' launch!";
        String message2 = "adb server change: cancelling sync!";
        synchronized (sListLock) {
            for (DelayedLaunchInfo launchInfo : mWaitingForReadyEmulatorList) {
                if (launchInfo.mLaunchAction == LaunchConfigDelegate.ACTION_DO_NOTHING) {
                    AdtPlugin.printErrorToConsole(launchInfo.mProject, message2);
                } else {
                    AdtPlugin.printErrorToConsole(launchInfo.mProject,
                            String.format(message1, launchInfo.mActivity));
                }
            }
            for (DelayedLaunchInfo launchInfo : mWaitingForDebuggerApplications) {
                if (launchInfo.mLaunchAction == LaunchConfigDelegate.ACTION_DO_NOTHING) {
                    AdtPlugin.printErrorToConsole(launchInfo.mProject, message2);
                } else {
                    AdtPlugin.printErrorToConsole(launchInfo.mProject,
                            String.format(message1, launchInfo.mActivity));
                }
            }
        }
    }

    /**
     * Sent when the a device is connected to the {@link AndroidDebugBridge}.
     * <p/>
     * This is sent from a non UI thread.
     * @param device the new device.
     * 
     * @see IDeviceChangeListener#deviceConnected(Device)
     */
    public void deviceConnected(Device device) {
        synchronized (sListLock) {
            // look if there's an app waiting for a device
            if (mWaitingForEmulatorLaunches.size() > 0) {
                // remove first item from the list
                DelayedLaunchInfo launchInfo = mWaitingForEmulatorLaunches.get(0);
                mWaitingForEmulatorLaunches.remove(0);
                
                // give it its device
                launchInfo.mDevice = device;
                
                // and move it to the other list
                mWaitingForReadyEmulatorList.add(launchInfo);
                
                // and tell the user about it
                AdtPlugin.printToConsole(launchInfo.mProject,
                        String.format("New emulator found: %1$s", device.getSerialNumber()));
                AdtPlugin.printToConsole(launchInfo.mProject,
                        String.format("Waiting for HOME ('%1$s') to be launched...",
                            AdtPlugin.getDefault().getPreferenceStore().getString(
                                    AdtPlugin.PREFS_HOME_PACKAGE)));
            }
        }
    }

    /**
     * Sent when the a device is connected to the {@link AndroidDebugBridge}.
     * <p/>
     * This is sent from a non UI thread.
     * @param device the new device.
     * 
     * @see IDeviceChangeListener#deviceDisconnected(Device)
     */
    public void deviceDisconnected(Device device) {
        // any pending launch on this device must be canceled.
        String message = "%1$s disconnected! Cancelling '%2$s' launch!";
        synchronized (sListLock) {
            for (DelayedLaunchInfo launchInfo : mWaitingForReadyEmulatorList) {
                if (launchInfo.mDevice == device) {
                    AdtPlugin.printErrorToConsole(launchInfo.mProject,
                            String.format(message, device.getSerialNumber(), launchInfo.mActivity));
                }
            }
            for (DelayedLaunchInfo launchInfo : mWaitingForDebuggerApplications) {
                if (launchInfo.mDevice == device) {
                    AdtPlugin.printErrorToConsole(launchInfo.mProject,
                            String.format(message, device.getSerialNumber(), launchInfo.mActivity));
                }
            }
        }

    }

    /**
     * Sent when a device data changed, or when clients are started/terminated on the device.
     * <p/>
     * This is sent from a non UI thread.
     * @param device the device that was updated.
     * @param changeMask the mask indicating what changed.
     * 
     * @see IDeviceChangeListener#deviceChanged(Device)
     */
    public void deviceChanged(Device device, int changeMask) {
        // We could check if any starting device we care about is now ready, but we can wait for
        // its home app to show up, so...
    }

    /**
     * Sent when an existing client information changed.
     * <p/>
     * This is sent from a non UI thread.
     * @param client the updated client.
     * @param changeMask the bit mask describing the changed properties. It can contain
     * any of the following values: {@link Client#CHANGE_INFO}, {@link Client#CHANGE_NAME}
     * {@link Client#CHANGE_DEBUGGER_INTEREST}, {@link Client#CHANGE_THREAD_MODE},
     * {@link Client#CHANGE_THREAD_DATA}, {@link Client#CHANGE_HEAP_MODE},
     * {@link Client#CHANGE_HEAP_DATA}, {@link Client#CHANGE_NATIVE_HEAP_DATA} 
     * 
     * @see IClientChangeListener#clientChanged(Client, int)
     */
    public void clientChanged(final Client client, int changeMask) {
        boolean connectDebugger = false;
        if ((changeMask & Client.CHANGE_NAME) == Client.CHANGE_NAME) {
            String applicationName = client.getClientData().getClientDescription();
            if (applicationName != null) {
                IPreferenceStore store = AdtPlugin.getDefault().getPreferenceStore();
                String home = store.getString(AdtPlugin.PREFS_HOME_PACKAGE);
                
                if (home.equals(applicationName)) {
                    
                    // looks like home is up, get its device
                    Device device = client.getDevice();
                    
                    // look for application waiting for home
                    synchronized (sListLock) {
                        boolean foundMatch = false;
                        for (int i = 0 ; i < mWaitingForReadyEmulatorList.size() ;) {
                            DelayedLaunchInfo launchInfo = mWaitingForReadyEmulatorList.get(i);
                            if (launchInfo.mDevice == device) {
                                // it's match, remove from the list
                                mWaitingForReadyEmulatorList.remove(i);
        
                                AdtPlugin.printToConsole(launchInfo.mProject,
                                        String.format("HOME is up on device '%1$s'",
                                                device.getSerialNumber()));
                                
                                // attempt to sync the new package onto the device.
                                if (syncApp(launchInfo, device)) {
                                    // application package is sync'ed, lets attempt to launch it.
                                    launchApp(launchInfo, device);
                                    
                                    // if we haven't checked the device build info, lets do it here
                                    if (foundMatch == false) {
                                        foundMatch = true;
                                        checkBuildInfo(launchInfo, device);
                                    }
                                } else {
                                    // failure! Cancel and return
                                    launchInfo.mLaunch.stopLaunch();
                                }
                            } else {
                                i++;
                            }
                        }
                    }
                }
    
                // check if it's already waiting for a debugger, and if so we connect to it.
                if (client.getClientData().getDebuggerConnectionStatus() == ClientData.DEBUGGER_WAITING) {
                    // search for this client in the list;
                    synchronized (sListLock) {
                        int index = mUnknownClientsWaitingForDebugger.indexOf(client);
                        if (index != -1) {
                            connectDebugger = true;
                            mUnknownClientsWaitingForDebugger.remove(client);
                        }
                    }
                }
            }
        }
        
        // if it's not home, it could be an app that is now in debugger mode that we're waiting for
        // lets check it

        if ((changeMask & Client.CHANGE_DEBUGGER_INTEREST) == Client.CHANGE_DEBUGGER_INTEREST) {
            ClientData clientData = client.getClientData();
            String applicationName = client.getClientData().getClientDescription();
            if (clientData.getDebuggerConnectionStatus() == ClientData.DEBUGGER_WAITING) {
                // Get the application name, and make sure its valid.
                if (applicationName == null) {
                    // looks like we don't have the client yet, so we keep it around for when its
                    // name becomes available.
                    synchronized (sListLock) {
                        mUnknownClientsWaitingForDebugger.add(client);
                    }
                    return;
                } else {
                    connectDebugger = true;
                }
            }
        }

        if (connectDebugger) {
            Log.d("adt", "Debugging " + client);
            // now check it against the apps waiting for a debugger
            String applicationName = client.getClientData().getClientDescription();
            Log.d("adt", "App Name: " + applicationName);
            synchronized (sListLock) {
                for (int i = 0 ; i < mWaitingForDebuggerApplications.size() ;) {
                    final DelayedLaunchInfo launchInfo = mWaitingForDebuggerApplications.get(i);
                    if (client.getDevice() == launchInfo.mDevice &&
                            applicationName.equals(launchInfo.mPackageName)) {
                        // this is a match. We remove the launch info from the list
                        mWaitingForDebuggerApplications.remove(i);
                        
                        // and connect the debugger.
                        String msg = String.format(
                                "Attempting to connect debugger to '%1$s' on port %2$d",
                                launchInfo.mPackageName, client.getDebuggerListenPort());
                        AdtPlugin.printToConsole(launchInfo.mProject, msg);
                        
                        new Thread("Debugger Connection") { //$NON-NLS-1$
                            @Override
                            public void run() {
                                try {
                                    if (connectRemoteDebugger(
                                            client.getDebuggerListenPort(),
                                            launchInfo.mLaunch, launchInfo.mMonitor) == false) {
                                        return;
                                    }
                                } catch (CoreException e) {
                                    // well something went wrong.
                                    // stop the launch
                                    launchInfo.mLaunch.stopLaunch();
                                }

                                launchInfo.mMonitor.done();
                            }
                        }.start();
                        
                        // we're done processing this client.
                        return;

                    } else {
                        i++;
                    }
                }
            }
            
            // if we get here, we haven't found an app that we were launching, so we look
            // for opened android projects that contains the app asking for a debugger.
            // If we find one, we automatically connect to it.
            IProject project = ProjectHelper.findAndroidProjectByAppName(applicationName);
            
            if (project != null) {
                debugRunningApp(project, client.getDebuggerListenPort());
            }
        }
    }
    
    /**
     * Get the stderr/stdout outputs of a process and return when the process is done.
     * Both <b>must</b> be read or the process will block on windows.
     * @param process The process to get the ouput from
     * @throws InterruptedException
     */
    private void grabEmulatorOutput(final Process process) {
        // read the lines as they come. if null is returned, it's
        // because the process finished
        new Thread("") { //$NON-NLS-1$
            @Override
            public void run() {
                // create a buffer to read the stderr output
                InputStreamReader is = new InputStreamReader(process.getErrorStream());
                BufferedReader errReader = new BufferedReader(is);

                try {
                    while (true) {
                        String line = errReader.readLine();
                        if (line != null) {
                            AdtPlugin.printErrorToConsole("Emulator", line);
                        } else {
                            break;
                        }
                    }
                } catch (IOException e) {
                    // do nothing.
                }
            }
        }.start();

        new Thread("") { //$NON-NLS-1$
            @Override
            public void run() {
                InputStreamReader is = new InputStreamReader(process.getInputStream());
                BufferedReader outReader = new BufferedReader(is);

                try {
                    while (true) {
                        String line = outReader.readLine();
                        if (line != null) {
                            AdtPlugin.printToConsole("Emulator", line);
                        } else {
                            break;
                        }
                    }
                } catch (IOException e) {
                    // do nothing.
                }
            }
        }.start();
    }

}
