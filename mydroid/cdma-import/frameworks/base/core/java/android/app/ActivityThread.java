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

package android.app;

import android.content.BroadcastReceiver;
import android.content.ComponentCallbacks;
import android.content.ComponentName;
import android.content.ContentProvider;
import android.content.Context;
import android.content.IContentProvider;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageManager;
import android.content.pm.InstrumentationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.pm.ServiceInfo;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteDebug;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.net.http.AndroidHttpClient;
import android.os.Bundle;
import android.os.Debug;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.MessageQueue;
import android.os.Process;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.SystemClock;
import android.util.AndroidRuntimeException;
import android.util.Config;
import android.util.DisplayMetrics;
import android.util.EventLog;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.view.ViewDebug;
import android.view.ViewManager;
import android.view.Window;
import android.view.WindowManager;
import android.view.WindowManagerImpl;

import com.android.internal.os.BinderInternal;
import com.android.internal.os.RuntimeInit;
import com.android.internal.util.ArrayUtils;

import org.apache.harmony.xnet.provider.jsse.OpenSSLSocketImpl;

import java.io.File;
import java.io.FileDescriptor;
import java.io.FileOutputStream;
import java.io.PrintWriter;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.regex.Pattern;

final class IntentReceiverLeaked extends AndroidRuntimeException {
    public IntentReceiverLeaked(String msg) {
        super(msg);
    }
}

final class ServiceConnectionLeaked extends AndroidRuntimeException {
    public ServiceConnectionLeaked(String msg) {
        super(msg);
    }
}

final class SuperNotCalledException extends AndroidRuntimeException {
    public SuperNotCalledException(String msg) {
        super(msg);
    }
}

/**
 * This manages the execution of the main thread in an
 * application process, scheduling and executing activities,
 * broadcasts, and other operations on it as the activity
 * manager requests.
 *
 * {@hide}
 */
public final class ActivityThread {
    private static final String TAG = "ActivityThread";
    private static final boolean DEBUG = false;
    private static final boolean localLOGV = DEBUG ? Config.LOGD : Config.LOGV;
    private static final boolean DEBUG_BROADCAST = false;
    private static final long MIN_TIME_BETWEEN_GCS = 5*1000;
    private static final Pattern PATTERN_SEMICOLON = Pattern.compile(";");
    private static final int SQLITE_MEM_RELEASED_EVENT_LOG_TAG = 75003;
    private static final int LOG_ON_PAUSE_CALLED = 30021;
    private static final int LOG_ON_RESUME_CALLED = 30022;

    
    public static final ActivityThread currentActivityThread() {
        return (ActivityThread)sThreadLocal.get();
    }

    public static final String currentPackageName()
    {
        ActivityThread am = currentActivityThread();
        return (am != null && am.mBoundApplication != null)
            ? am.mBoundApplication.processName : null;
    }

    public static IPackageManager getPackageManager() {
        if (sPackageManager != null) {
            //Log.v("PackageManager", "returning cur default = " + sPackageManager);
            return sPackageManager;
        }
        IBinder b = ServiceManager.getService("package");
        //Log.v("PackageManager", "default service binder = " + b);
        sPackageManager = IPackageManager.Stub.asInterface(b);
        //Log.v("PackageManager", "default service = " + sPackageManager);
        return sPackageManager;
    }

    DisplayMetrics getDisplayMetricsLocked() {
        if (mDisplay == null) {
            WindowManager wm = WindowManagerImpl.getDefault();
            mDisplay = wm.getDefaultDisplay();
        }
        DisplayMetrics metrics = new DisplayMetrics();
        mDisplay.getMetrics(metrics);
        return metrics;
    }

    Resources getTopLevelResources(String appDir) {
        synchronized (mPackages) {
            //Log.w(TAG, "getTopLevelResources: " + appDir);
            WeakReference<Resources> wr = mActiveResources.get(appDir);
            Resources r = wr != null ? wr.get() : null;
            if (r != null && r.getAssets().isUpToDate()) {
                //Log.w(TAG, "Returning cached resources " + r + " " + appDir);
                return r;
            }

            //if (r != null) {
            //    Log.w(TAG, "Throwing away out-of-date resources!!!! "
            //            + r + " " + appDir);
            //}

            AssetManager assets = new AssetManager();
            if (assets.addAssetPath(appDir) == 0) {
                return null;
            }
            DisplayMetrics metrics = getDisplayMetricsLocked();
            r = new Resources(assets, metrics, getConfiguration());
            //Log.i(TAG, "Created app resources " + r + ": " + r.getConfiguration());
            // XXX need to remove entries when weak references go away
            mActiveResources.put(appDir, new WeakReference<Resources>(r));
            return r;
        }
    }

    final Handler getHandler() {
        return mH;
    }

    public final static class PackageInfo {

        private final ActivityThread mActivityThread;
        private final ApplicationInfo mApplicationInfo;
        private final String mPackageName;
        private final String mAppDir;
        private final String mResDir;
        private final String[] mSharedLibraries;
        private final String mDataDir;
        private final File mDataDirFile;
        private final ClassLoader mBaseClassLoader;
        private final boolean mSecurityViolation;
        private final boolean mIncludeCode;
        private Resources mResources;
        private ClassLoader mClassLoader;
        private Application mApplication;
        private final HashMap<Context, HashMap<BroadcastReceiver, ReceiverDispatcher>> mReceivers
            = new HashMap<Context, HashMap<BroadcastReceiver, ReceiverDispatcher>>();
        private final HashMap<Context, HashMap<BroadcastReceiver, ReceiverDispatcher>> mUnregisteredReceivers
        = new HashMap<Context, HashMap<BroadcastReceiver, ReceiverDispatcher>>();
        private final HashMap<Context, HashMap<ServiceConnection, ServiceDispatcher>> mServices
            = new HashMap<Context, HashMap<ServiceConnection, ServiceDispatcher>>();
        private final HashMap<Context, HashMap<ServiceConnection, ServiceDispatcher>> mUnboundServices
            = new HashMap<Context, HashMap<ServiceConnection, ServiceDispatcher>>();

        int mClientCount = 0;

        public PackageInfo(ActivityThread activityThread, ApplicationInfo aInfo,
                ActivityThread mainThread, ClassLoader baseLoader,
                boolean securityViolation, boolean includeCode) {
            mActivityThread = activityThread;
            mApplicationInfo = aInfo;
            mPackageName = aInfo.packageName;
            mAppDir = aInfo.sourceDir;
            mResDir = aInfo.publicSourceDir;
            mSharedLibraries = aInfo.sharedLibraryFiles;
            mDataDir = aInfo.dataDir;
            mDataDirFile = mDataDir != null ? new File(mDataDir) : null;
            mBaseClassLoader = baseLoader;
            mSecurityViolation = securityViolation;
            mIncludeCode = includeCode;

            if (mAppDir == null) {
                if (mSystemContext == null) {
                    mSystemContext =
                        ApplicationContext.createSystemContext(mainThread);
                    mSystemContext.getResources().updateConfiguration(
                            mainThread.getConfiguration(),
                            mainThread.getDisplayMetricsLocked());
                    //Log.i(TAG, "Created system resources "
                    //        + mSystemContext.getResources() + ": "
                    //        + mSystemContext.getResources().getConfiguration());
                }
                mClassLoader = mSystemContext.getClassLoader();
                mResources = mSystemContext.getResources();
            }
        }

        public PackageInfo(ActivityThread activityThread, String name,
                Context systemContext) {
            mActivityThread = activityThread;
            mApplicationInfo = new ApplicationInfo();
            mApplicationInfo.packageName = name;
            mPackageName = name;
            mAppDir = null;
            mResDir = null;
            mSharedLibraries = null;
            mDataDir = null;
            mDataDirFile = null;
            mBaseClassLoader = null;
            mSecurityViolation = false;
            mIncludeCode = true;
            mClassLoader = systemContext.getClassLoader();
            mResources = systemContext.getResources();
        }

        public String getPackageName() {
            return mPackageName;
        }

        public boolean isSecurityViolation() {
            return mSecurityViolation;
        }

        /**
         * Gets the array of shared libraries that are listed as
         * used by the given package.
         * 
         * @param packageName the name of the package (note: not its
         * file name)
         * @return null-ok; the array of shared libraries, each one
         * a fully-qualified path
         */
        private static String[] getLibrariesFor(String packageName) {
            ApplicationInfo ai = null;
            try {
                ai = getPackageManager().getApplicationInfo(packageName,
                        PackageManager.GET_SHARED_LIBRARY_FILES);
            } catch (RemoteException e) {
                throw new AssertionError(e);
            }

            if (ai == null) {
                return null;
            }

            return ai.sharedLibraryFiles;
        }

        /**
         * Combines two arrays (of library names) such that they are
         * concatenated in order but are devoid of duplicates. The
         * result is a single string with the names of the libraries
         * separated by colons, or <code>null</code> if both lists
         * were <code>null</code> or empty.
         * 
         * @param list1 null-ok; the first list
         * @param list2 null-ok; the second list
         * @return null-ok; the combination
         */
        private static String combineLibs(String[] list1, String[] list2) {
            StringBuilder result = new StringBuilder(300);
            boolean first = true;

            if (list1 != null) {
                for (String s : list1) {
                    if (first) {
                        first = false;
                    } else {
                        result.append(':');
                    }
                    result.append(s);
                }
            }

            // Only need to check for duplicates if list1 was non-empty.
            boolean dupCheck = !first;

            if (list2 != null) {
                for (String s : list2) {
                    if (dupCheck && ArrayUtils.contains(list1, s)) {
                        continue;
                    }
                    
                    if (first) {
                        first = false;
                    } else {
                        result.append(':');
                    }
                    result.append(s);
                }
            }

            return result.toString();
        }
                
        public ClassLoader getClassLoader() {
            synchronized (this) {
                if (mClassLoader != null) {
                    return mClassLoader;
                }

                if (mIncludeCode && !mPackageName.equals("android")) {
                    String zip = mAppDir;

                    /*
                     * The following is a bit of a hack to inject
                     * instrumentation into the system: If the app
                     * being started matches one of the instrumentation names,
                     * then we combine both the "instrumentation" and
                     * "instrumented" app into the path, along with the
                     * concatenation of both apps' shared library lists.
                     */

                    String instrumentationAppDir =
                            mActivityThread.mInstrumentationAppDir;
                    String instrumentationAppPackage =
                            mActivityThread.mInstrumentationAppPackage;
                    String instrumentedAppDir =
                            mActivityThread.mInstrumentedAppDir;
                    String[] instrumentationLibs = null;

                    if (mAppDir.equals(instrumentationAppDir)
                            || mAppDir.equals(instrumentedAppDir)) {
                        zip = instrumentationAppDir + ":" + instrumentedAppDir;
                        if (! instrumentedAppDir.equals(instrumentationAppDir)) {
                            instrumentationLibs =
                                getLibrariesFor(instrumentationAppPackage);
                        }
                    }

                    if ((mSharedLibraries != null) ||
                            (instrumentationLibs != null)) {
                        zip = 
                            combineLibs(mSharedLibraries, instrumentationLibs)
                            + ':' + zip;
                    }

                    /*
                     * With all the combination done (if necessary, actually
                     * create the class loader.
                     */

                    if (localLOGV) Log.v(TAG, "Class path: " + zip);

                    mClassLoader =
                        ApplicationLoaders.getDefault().getClassLoader(
                            zip, mDataDir, mBaseClassLoader);
                } else {
                    if (mBaseClassLoader == null) {
                        mClassLoader = ClassLoader.getSystemClassLoader();
                    } else {
                        mClassLoader = mBaseClassLoader;
                    }
                }
                return mClassLoader;
            }
        }

        public String getAppDir() {
            return mAppDir;
        }

        public String getResDir() {
            return mResDir;
        }

        public String getDataDir() {
            return mDataDir;
        }

        public File getDataDirFile() {
            return mDataDirFile;
        }

        public AssetManager getAssets(ActivityThread mainThread) {
            return getResources(mainThread).getAssets();
        }

        public Resources getResources(ActivityThread mainThread) {
            if (mResources == null) {
                mResources = mainThread.getTopLevelResources(mResDir);
            }
            return mResources;
        }

        public Application makeApplication() {
            if (mApplication != null) {
                return mApplication;
            }
            
            Application app = null;
            
            String appClass = mApplicationInfo.className;
            if (appClass == null) {
                appClass = "android.app.Application";
            }

            try {
                java.lang.ClassLoader cl = getClassLoader();
                ApplicationContext appContext = new ApplicationContext();
                appContext.init(this, null, mActivityThread);
                app = mActivityThread.mInstrumentation.newApplication(
                        cl, appClass, appContext);
                appContext.setOuterContext(app);
            } catch (Exception e) {
                if (!mActivityThread.mInstrumentation.onException(app, e)) {
                    throw new RuntimeException(
                        "Unable to instantiate application " + appClass
                        + ": " + e.toString(), e);
                }
            }
            mActivityThread.mAllApplications.add(app);
            return mApplication = app;
        }
        
        public void removeContextRegistrations(Context context,
                String who, String what) {
            HashMap<BroadcastReceiver, ReceiverDispatcher> rmap =
                mReceivers.remove(context);
            if (rmap != null) {
                Iterator<ReceiverDispatcher> it = rmap.values().iterator();
                while (it.hasNext()) {
                    ReceiverDispatcher rd = it.next();
                    IntentReceiverLeaked leak = new IntentReceiverLeaked(
                            what + " " + who + " has leaked IntentReceiver "
                            + rd.getIntentReceiver() + " that was " +
                            "originally registered here. Are you missing a " +
                            "call to unregisterReceiver()?");
                    leak.setStackTrace(rd.getLocation().getStackTrace());
                    Log.e(TAG, leak.getMessage(), leak);
                    try {
                        ActivityManagerNative.getDefault().unregisterReceiver(
                                rd.getIIntentReceiver());
                    } catch (RemoteException e) {
                        // system crashed, nothing we can do
                    }
                }
            }
            mUnregisteredReceivers.remove(context);
            //Log.i(TAG, "Receiver registrations: " + mReceivers);
            HashMap<ServiceConnection, ServiceDispatcher> smap =
                mServices.remove(context);
            if (smap != null) {
                Iterator<ServiceDispatcher> it = smap.values().iterator();
                while (it.hasNext()) {
                    ServiceDispatcher sd = it.next();
                    ServiceConnectionLeaked leak = new ServiceConnectionLeaked(
                            what + " " + who + " has leaked ServiceConnection "
                            + sd.getServiceConnection() + " that was originally bound here");
                    leak.setStackTrace(sd.getLocation().getStackTrace());
                    Log.e(TAG, leak.getMessage(), leak);
                    try {
                        ActivityManagerNative.getDefault().unbindService(
                                sd.getIServiceConnection());
                    } catch (RemoteException e) {
                        // system crashed, nothing we can do
                    }
                    sd.doForget();
                }
            }
            mUnboundServices.remove(context);
            //Log.i(TAG, "Service registrations: " + mServices);
        }

        public IIntentReceiver getReceiverDispatcher(BroadcastReceiver r,
                Context context, Handler handler,
                Instrumentation instrumentation, boolean registered) {
            synchronized (mReceivers) {
                ReceiverDispatcher rd = null;
                HashMap<BroadcastReceiver, ReceiverDispatcher> map = null;
                if (registered) {
                    map = mReceivers.get(context);
                    if (map != null) {
                        rd = map.get(r);
                    }
                }
                if (rd == null) {
                    rd = new ReceiverDispatcher(r, context, handler,
                            instrumentation, registered);
                    if (registered) {
                        if (map == null) {
                            map = new HashMap<BroadcastReceiver, ReceiverDispatcher>();
                            mReceivers.put(context, map);
                        }
                        map.put(r, rd);
                    }
                } else {
                    rd.validate(context, handler);
                }
                return rd.getIIntentReceiver();
            }
        }

        public IIntentReceiver forgetReceiverDispatcher(Context context,
                BroadcastReceiver r) {
            synchronized (mReceivers) {
                HashMap<BroadcastReceiver, ReceiverDispatcher> map = mReceivers.get(context);
                ReceiverDispatcher rd = null;
                if (map != null) {
                    rd = map.get(r);
                    if (rd != null) {
                        map.remove(r);
                        if (map.size() == 0) {
                            mReceivers.remove(context);
                        }
                        if (r.getDebugUnregister()) {
                            HashMap<BroadcastReceiver, ReceiverDispatcher> holder
                                    = mUnregisteredReceivers.get(context);
                            if (holder == null) {
                                holder = new HashMap<BroadcastReceiver, ReceiverDispatcher>();
                                mUnregisteredReceivers.put(context, holder);
                            }
                            RuntimeException ex = new IllegalArgumentException(
                                    "Originally unregistered here:");
                            ex.fillInStackTrace();
                            rd.setUnregisterLocation(ex);
                            holder.put(r, rd);
                        }
                        return rd.getIIntentReceiver();
                    }
                }
                HashMap<BroadcastReceiver, ReceiverDispatcher> holder
                        = mUnregisteredReceivers.get(context);
                if (holder != null) {
                    rd = holder.get(r);
                    if (rd != null) {
                        RuntimeException ex = rd.getUnregisterLocation();
                        throw new IllegalArgumentException(
                                "Unregistering Receiver " + r
                                + " that was already unregistered", ex);
                    }
                }
                if (context == null) {
                    throw new IllegalStateException("Unbinding Receiver " + r
                            + " from Context that is no longer in use: " + context);
                } else {
                    throw new IllegalArgumentException("Receiver not registered: " + r);
                }

            }
        }

        static final class ReceiverDispatcher {

            final static class InnerReceiver extends IIntentReceiver.Stub {
                final WeakReference<ReceiverDispatcher> mDispatcher;
                final ReceiverDispatcher mStrongRef;
                
                InnerReceiver(ReceiverDispatcher rd, boolean strong) {
                    mDispatcher = new WeakReference<ReceiverDispatcher>(rd);
                    mStrongRef = strong ? rd : null;
                }
                public void performReceive(Intent intent, int resultCode,
                        String data, Bundle extras, boolean ordered) {
                    ReceiverDispatcher rd = mDispatcher.get();
                    if (DEBUG_BROADCAST) {
                        int seq = intent.getIntExtra("seq", -1);
                        Log.i(TAG, "Receiving broadcast " + intent.getAction() + " seq=" + seq
                                + " to " + rd);
                    }
                    if (rd != null) {
                        rd.performReceive(intent, resultCode, data, extras, ordered);
                    }
                }
            }
            
            final IIntentReceiver.Stub mIIntentReceiver;
            final BroadcastReceiver mReceiver;
            final Context mContext;
            final Handler mActivityThread;
            final Instrumentation mInstrumentation;
            final boolean mRegistered;
            final IntentReceiverLeaked mLocation;
            RuntimeException mUnregisterLocation;

            final class Args implements Runnable {
                private Intent mCurIntent;
                private int mCurCode;
                private String mCurData;
                private Bundle mCurMap;
                private boolean mCurOrdered;

                public void run() {
                    BroadcastReceiver receiver = mReceiver;
                    if (DEBUG_BROADCAST) {
                        int seq = mCurIntent.getIntExtra("seq", -1);
                        Log.i(TAG, "Dispathing broadcast " + mCurIntent.getAction() + " seq=" + seq
                                + " to " + mReceiver);
                    }
                    if (receiver == null) {
                        return;
                    }

                    IActivityManager mgr = ActivityManagerNative.getDefault();
                    Intent intent = mCurIntent;
                    mCurIntent = null;
                    try {
                        ClassLoader cl =  mReceiver.getClass().getClassLoader();
                        intent.setExtrasClassLoader(cl);
                        if (mCurMap != null) {
                            mCurMap.setClassLoader(cl);
                        }
                        receiver.setOrderedHint(true);
                        receiver.setResult(mCurCode, mCurData, mCurMap);
                        receiver.clearAbortBroadcast();
                        receiver.setOrderedHint(mCurOrdered);
                        receiver.onReceive(mContext, intent);
                    } catch (Exception e) {
                        if (mRegistered && mCurOrdered) {
                            try {
                                mgr.finishReceiver(mIIntentReceiver,
                                        mCurCode, mCurData, mCurMap, false);
                            } catch (RemoteException ex) {
                            }
                        }
                        if (mInstrumentation == null ||
                                !mInstrumentation.onException(mReceiver, e)) {
                            throw new RuntimeException(
                                "Error receiving broadcast " + intent
                                + " in " + mReceiver, e);
                        }
                    }
                    if (mRegistered && mCurOrdered) {
                        try {
                            mgr.finishReceiver(mIIntentReceiver,
                                    receiver.getResultCode(),
                                    receiver.getResultData(),
                                    receiver.getResultExtras(false),
                                    receiver.getAbortBroadcast());
                        } catch (RemoteException ex) {
                        }
                    }
                }
            }

            ReceiverDispatcher(BroadcastReceiver receiver, Context context,
                    Handler activityThread, Instrumentation instrumentation,
                    boolean registered) {
                if (activityThread == null) {
                    throw new NullPointerException("Handler must not be null");
                }

                mIIntentReceiver = new InnerReceiver(this, !registered);
                mReceiver = receiver;
                mContext = context;
                mActivityThread = activityThread;
                mInstrumentation = instrumentation;
                mRegistered = registered;
                mLocation = new IntentReceiverLeaked(null);
                mLocation.fillInStackTrace();
            }

            void validate(Context context, Handler activityThread) {
                if (mContext != context) {
                    throw new IllegalStateException(
                        "Receiver " + mReceiver +
                        " registered with differing Context (was " +
                        mContext + " now " + context + ")");
                }
                if (mActivityThread != activityThread) {
                    throw new IllegalStateException(
                        "Receiver " + mReceiver +
                        " registered with differing handler (was " +
                        mActivityThread + " now " + activityThread + ")");
                }
            }

            IntentReceiverLeaked getLocation() {
                return mLocation;
            }

            BroadcastReceiver getIntentReceiver() {
                return mReceiver;
            }
            
            IIntentReceiver getIIntentReceiver() {
                return mIIntentReceiver;
            }

            void setUnregisterLocation(RuntimeException ex) {
                mUnregisterLocation = ex;
            }

            RuntimeException getUnregisterLocation() {
                return mUnregisterLocation;
            }

            public void performReceive(Intent intent, int resultCode,
                    String data, Bundle extras, boolean ordered) {
                if (DEBUG_BROADCAST) {
                    int seq = intent.getIntExtra("seq", -1);
                    Log.i(TAG, "Enqueueing broadcast " + intent.getAction() + " seq=" + seq
                            + " to " + mReceiver);
                }
                Args args = new Args();
                args.mCurIntent = intent;
                args.mCurCode = resultCode;
                args.mCurData = data;
                args.mCurMap = extras;
                args.mCurOrdered = ordered;
                if (!mActivityThread.post(args)) {
                    if (mRegistered) {
                        IActivityManager mgr = ActivityManagerNative.getDefault();
                        try {
                            mgr.finishReceiver(mIIntentReceiver, args.mCurCode,
                                    args.mCurData, args.mCurMap, false);
                        } catch (RemoteException ex) {
                        }
                    }
                }
            }

        }

        public final IServiceConnection getServiceDispatcher(ServiceConnection c,
                Context context, Handler handler, int flags) {
            synchronized (mServices) {
                ServiceDispatcher sd = null;
                HashMap<ServiceConnection, ServiceDispatcher> map = mServices.get(context);
                if (map != null) {
                    sd = map.get(c);
                }
                if (sd == null) {
                    sd = new ServiceDispatcher(c, context, handler, flags);
                    if (map == null) {
                        map = new HashMap<ServiceConnection, ServiceDispatcher>();
                        mServices.put(context, map);
                    }
                    map.put(c, sd);
                } else {
                    sd.validate(context, handler);
                }
                return sd.getIServiceConnection();
            }
        }

        public final IServiceConnection forgetServiceDispatcher(Context context,
                ServiceConnection c) {
            synchronized (mServices) {
                HashMap<ServiceConnection, ServiceDispatcher> map
                        = mServices.get(context);
                ServiceDispatcher sd = null;
                if (map != null) {
                    sd = map.get(c);
                    if (sd != null) {
                        map.remove(c);
                        sd.doForget();
                        if (map.size() == 0) {
                            mServices.remove(context);
                        }
                        if ((sd.getFlags()&Context.BIND_DEBUG_UNBIND) != 0) {
                            HashMap<ServiceConnection, ServiceDispatcher> holder
                                    = mUnboundServices.get(context);
                            if (holder == null) {
                                holder = new HashMap<ServiceConnection, ServiceDispatcher>();
                                mUnboundServices.put(context, holder);
                            }
                            RuntimeException ex = new IllegalArgumentException(
                                    "Originally unbound here:");
                            ex.fillInStackTrace();
                            sd.setUnbindLocation(ex);
                            holder.put(c, sd);
                        }
                        return sd.getIServiceConnection();
                    }
                }
                HashMap<ServiceConnection, ServiceDispatcher> holder
                        = mUnboundServices.get(context);
                if (holder != null) {
                    sd = holder.get(c);
                    if (sd != null) {
                        RuntimeException ex = sd.getUnbindLocation();
                        throw new IllegalArgumentException(
                                "Unbinding Service " + c
                                + " that was already unbound", ex);
                    }
                }
                if (context == null) {
                    throw new IllegalStateException("Unbinding Service " + c
                            + " from Context that is no longer in use: " + context);
                } else {
                    throw new IllegalArgumentException("Service not registered: " + c);
                }
            }
        }

        static final class ServiceDispatcher {
            private final InnerConnection mIServiceConnection;
            private final ServiceConnection mConnection;
            private final Context mContext;
            private final Handler mActivityThread;
            private final ServiceConnectionLeaked mLocation;
            private final int mFlags;

            private RuntimeException mUnbindLocation;

            private boolean mDied;

            private static class ConnectionInfo {
                IBinder binder;
                IBinder.DeathRecipient deathMonitor;
            }

            private static class InnerConnection extends IServiceConnection.Stub {
                final WeakReference<ServiceDispatcher> mDispatcher;
                
                InnerConnection(ServiceDispatcher sd) {
                    mDispatcher = new WeakReference<ServiceDispatcher>(sd);
                }

                public void connected(ComponentName name, IBinder service) throws RemoteException {
                    ServiceDispatcher sd = mDispatcher.get();
                    if (sd != null) {
                        sd.connected(name, service);
                    }
                }
            }
            
            private final HashMap<ComponentName, ConnectionInfo> mActiveConnections
                = new HashMap<ComponentName, ConnectionInfo>();

            ServiceDispatcher(ServiceConnection conn,
                    Context context, Handler activityThread, int flags) {
                mIServiceConnection = new InnerConnection(this);
                mConnection = conn;
                mContext = context;
                mActivityThread = activityThread;
                mLocation = new ServiceConnectionLeaked(null);
                mLocation.fillInStackTrace();
                mFlags = flags;
            }

            void validate(Context context, Handler activityThread) {
                if (mContext != context) {
                    throw new RuntimeException(
                        "ServiceConnection " + mConnection +
                        " registered with differing Context (was " +
                        mContext + " now " + context + ")");
                }
                if (mActivityThread != activityThread) {
                    throw new RuntimeException(
                        "ServiceConnection " + mConnection +
                        " registered with differing handler (was " +
                        mActivityThread + " now " + activityThread + ")");
                }
            }

            void doForget() {
                synchronized(this) {
                    Iterator<ConnectionInfo> it = mActiveConnections.values().iterator();
                    while (it.hasNext()) {
                        ConnectionInfo ci = it.next();
                        ci.binder.unlinkToDeath(ci.deathMonitor, 0);
                    }
                    mActiveConnections.clear();
                }
            }

            ServiceConnectionLeaked getLocation() {
                return mLocation;
            }

            ServiceConnection getServiceConnection() {
                return mConnection;
            }

            IServiceConnection getIServiceConnection() {
                return mIServiceConnection;
            }
            
            int getFlags() {
                return mFlags;
            }

            void setUnbindLocation(RuntimeException ex) {
                mUnbindLocation = ex;
            }

            RuntimeException getUnbindLocation() {
                return mUnbindLocation;
            }

            public void connected(ComponentName name, IBinder service) {
                if (mActivityThread != null) {
                    mActivityThread.post(new RunConnection(name, service, 0));
                } else {
                    doConnected(name, service);
                }
            }

            public void death(ComponentName name, IBinder service) {
                ConnectionInfo old;

                synchronized (this) {
                    mDied = true;
                    old = mActiveConnections.remove(name);
                    if (old == null || old.binder != service) {
                        // Death for someone different than who we last
                        // reported...  just ignore it.
                        return;
                    }
                    old.binder.unlinkToDeath(old.deathMonitor, 0);
                }

                if (mActivityThread != null) {
                    mActivityThread.post(new RunConnection(name, service, 1));
                } else {
                    doDeath(name, service);
                }
            }

            public void doConnected(ComponentName name, IBinder service) {
                ConnectionInfo old;
                ConnectionInfo info;

                synchronized (this) {
                    old = mActiveConnections.get(name);
                    if (old != null && old.binder == service) {
                        // Huh, already have this one.  Oh well!
                        return;
                    }

                    if (service != null) {
                        // A new service is being connected... set it all up.
                        mDied = false;
                        info = new ConnectionInfo();
                        info.binder = service;
                        info.deathMonitor = new DeathMonitor(name, service);
                        try {
                            service.linkToDeath(info.deathMonitor, 0);
                            mActiveConnections.put(name, info);
                        } catch (RemoteException e) {
                            // This service was dead before we got it...  just
                            // don't do anything with it.
                            mActiveConnections.remove(name);
                            return;
                        }

                    } else {
                        // The named service is being disconnected... clean up.
                        mActiveConnections.remove(name);
                    }

                    if (old != null) {
                        old.binder.unlinkToDeath(old.deathMonitor, 0);
                    }
                }

                // If there was an old service, it is not disconnected.
                if (old != null) {
                    mConnection.onServiceDisconnected(name);
                }
                // If there is a new service, it is now connected.
                if (service != null) {
                    mConnection.onServiceConnected(name, service);
                }
            }

            public void doDeath(ComponentName name, IBinder service) {
                mConnection.onServiceDisconnected(name);
            }

            private final class RunConnection implements Runnable {
                RunConnection(ComponentName name, IBinder service, int command) {
                    mName = name;
                    mService = service;
                    mCommand = command;
                }

                public void run() {
                    if (mCommand == 0) {
                        doConnected(mName, mService);
                    } else if (mCommand == 1) {
                        doDeath(mName, mService);
                    }
                }

                final ComponentName mName;
                final IBinder mService;
                final int mCommand;
            }

            private final class DeathMonitor implements IBinder.DeathRecipient
            {
                DeathMonitor(ComponentName name, IBinder service) {
                    mName = name;
                    mService = service;
                }

                public void binderDied() {
                    death(mName, mService);
                }

                final ComponentName mName;
                final IBinder mService;
            }
        }
    }

    private static ApplicationContext mSystemContext = null;

    private static final class ActivityRecord {
        IBinder token;
        Intent intent;
        Bundle state;
        Activity activity;
        Window window;
        Activity parent;
        String embeddedID;
        Object lastNonConfigurationInstance;
        boolean paused;
        boolean stopped;
        boolean hideForNow;
        Configuration newConfig;
        ActivityRecord nextIdle;

        ActivityInfo activityInfo;
        PackageInfo packageInfo;

        List<ResultInfo> pendingResults;
        List<Intent> pendingIntents;

        boolean startsNotResumed;

        ActivityRecord() {
            parent = null;
            embeddedID = null;
            paused = false;
            stopped = false;
            hideForNow = false;
            nextIdle = null;
        }

        public String toString() {
            ComponentName componentName = intent.getComponent();
            return "ActivityRecord{"
                + Integer.toHexString(System.identityHashCode(this))
                + " token=" + token + " " + (componentName == null
                        ? "no component name" : componentName.toShortString())
                + "}";
        }
    }

    private final class ProviderRecord implements IBinder.DeathRecipient {
        final String mName;
        final IContentProvider mProvider;
        final ContentProvider mLocalProvider;

        ProviderRecord(String name, IContentProvider provider,
                ContentProvider localProvider) {
            mName = name;
            mProvider = provider;
            mLocalProvider = localProvider;
        }

        public void binderDied() {
            removeDeadProvider(mName, mProvider);
        }
    }

    private static final class NewIntentData {
        List<Intent> intents;
        IBinder token;
        public String toString() {
            return "NewIntentData{intents=" + intents + " token=" + token + "}";
        }
    }

    private static final class ReceiverData {
        Intent intent;
        ActivityInfo info;
        int resultCode;
        String resultData;
        Bundle resultExtras;
        boolean sync;
        boolean resultAbort;
        public String toString() {
            return "ReceiverData{intent=" + intent + " packageName=" +
            info.packageName + " resultCode=" + resultCode
            + " resultData=" + resultData + " resultExtras=" + resultExtras + "}";
        }
    }

    private static final class CreateServiceData {
        IBinder token;
        ServiceInfo info;
        Intent intent;
        public String toString() {
            return "CreateServiceData{token=" + token + " className="
            + info.name + " packageName=" + info.packageName
            + " intent=" + intent + "}";
        }
    }

    private static final class BindServiceData {
        IBinder token;
        Intent intent;
        boolean rebind;
        public String toString() {
            return "BindServiceData{token=" + token + " intent=" + intent + "}";
        }
    }

    private static final class ServiceArgsData {
        IBinder token;
        int startId;
        Intent args;
        public String toString() {
            return "ServiceArgsData{token=" + token + " startId=" + startId
            + " args=" + args + "}";
        }
    }

    private static final class AppBindData {
        PackageInfo info;
        String processName;
        ApplicationInfo appInfo;
        List<ProviderInfo> providers;
        ComponentName instrumentationName;
        String profileFile;
        Bundle instrumentationArgs;
        IInstrumentationWatcher instrumentationWatcher;
        int debugMode;
        Configuration config;
        boolean handlingProfiling;
        public String toString() {
            return "AppBindData{appInfo=" + appInfo + "}";
        }
    }

    private static final class DumpServiceInfo {
        FileDescriptor fd;
        IBinder service;
        String[] args;
        boolean dumped;
    }

    private static final class ResultData {
        IBinder token;
        List<ResultInfo> results;
        public String toString() {
            return "ResultData{token=" + token + " results" + results + "}";
        }
    }

    private static final class ContextCleanupInfo {
        ApplicationContext context;
        String what;
        String who;
    }

    private final class ApplicationThread extends ApplicationThreadNative {
        private static final String HEAP_COLUMN = "%17s %8s %8s %8s %8s";
        private static final String ONE_COUNT_COLUMN = "%17s %8d";
        private static final String TWO_COUNT_COLUMNS = "%17s %8d %17s %8d";

        public final void schedulePauseActivity(IBinder token, boolean finished,
                int configChanges) {
            queueOrSendMessage(
                    finished ? H.PAUSE_ACTIVITY_FINISHING : H.PAUSE_ACTIVITY,
                    token, configChanges);
        }

        public final void scheduleStopActivity(IBinder token, boolean showWindow,
                int configChanges) {
           queueOrSendMessage(
                showWindow ? H.STOP_ACTIVITY_SHOW : H.STOP_ACTIVITY_HIDE,
                token, 0, configChanges);
        }

        public final void scheduleWindowVisibility(IBinder token, boolean showWindow) {
            queueOrSendMessage(
                showWindow ? H.SHOW_WINDOW : H.HIDE_WINDOW,
                token);
        }

        public final void scheduleResumeActivity(IBinder token) {
            queueOrSendMessage(H.RESUME_ACTIVITY, token);
        }

        public final void scheduleSendResult(IBinder token, List<ResultInfo> results) {
            ResultData res = new ResultData();
            res.token = token;
            res.results = results;
            queueOrSendMessage(H.SEND_RESULT, res);
        }

        // we use token to identify this activity without having to send the
        // activity itself back to the activity manager. (matters more with ipc)
        public final void scheduleLaunchActivity(Intent intent, IBinder token,
                ActivityInfo info, Bundle state, List<ResultInfo> pendingResults,
                List<Intent> pendingNewIntents, boolean notResumed) {
            ActivityRecord r = new ActivityRecord();

            r.token = token;
            r.intent = intent;
            r.activityInfo = info;
            r.state = state;

            r.pendingResults = pendingResults;
            r.pendingIntents = pendingNewIntents;

            r.startsNotResumed = notResumed;

            queueOrSendMessage(H.LAUNCH_ACTIVITY, r);
        }

        public final void scheduleRelaunchActivity(IBinder token,
                List<ResultInfo> pendingResults, List<Intent> pendingNewIntents,
                int configChanges, boolean notResumed) {
            ActivityRecord r = new ActivityRecord();

            r.token = token;
            r.pendingResults = pendingResults;
            r.pendingIntents = pendingNewIntents;
            r.startsNotResumed = notResumed;

            synchronized (mRelaunchingActivities) {
                mRelaunchingActivities.add(r);
            }
            
            queueOrSendMessage(H.RELAUNCH_ACTIVITY, r, configChanges);
        }

        public final void scheduleNewIntent(List<Intent> intents, IBinder token) {
            NewIntentData data = new NewIntentData();
            data.intents = intents;
            data.token = token;

            queueOrSendMessage(H.NEW_INTENT, data);
        }

        public final void scheduleDestroyActivity(IBinder token, boolean finishing,
                int configChanges) {
            queueOrSendMessage(H.DESTROY_ACTIVITY, token, finishing ? 1 : 0,
                    configChanges);
        }

        public final void scheduleReceiver(Intent intent, ActivityInfo info,
                int resultCode, String data, Bundle extras, boolean sync) {
            ReceiverData r = new ReceiverData();

            r.intent = intent;
            r.info = info;
            r.resultCode = resultCode;
            r.resultData = data;
            r.resultExtras = extras;
            r.sync = sync;

            queueOrSendMessage(H.RECEIVER, r);
        }

        public final void scheduleCreateService(IBinder token,
                ServiceInfo info) {
            CreateServiceData s = new CreateServiceData();
            s.token = token;
            s.info = info;

            queueOrSendMessage(H.CREATE_SERVICE, s);
        }

        public final void scheduleBindService(IBinder token, Intent intent,
                boolean rebind) {
            BindServiceData s = new BindServiceData();
            s.token = token;
            s.intent = intent;
            s.rebind = rebind;

            queueOrSendMessage(H.BIND_SERVICE, s);
        }

        public final void scheduleUnbindService(IBinder token, Intent intent) {
            BindServiceData s = new BindServiceData();
            s.token = token;
            s.intent = intent;

            queueOrSendMessage(H.UNBIND_SERVICE, s);
        }

        public final void scheduleServiceArgs(IBinder token, int startId,
            Intent args) {
            ServiceArgsData s = new ServiceArgsData();
            s.token = token;
            s.startId = startId;
            s.args = args;

            queueOrSendMessage(H.SERVICE_ARGS, s);
        }

        public final void scheduleStopService(IBinder token) {
            queueOrSendMessage(H.STOP_SERVICE, token);
        }

        public final void bindApplication(String processName,
                ApplicationInfo appInfo, List<ProviderInfo> providers,
                ComponentName instrumentationName, String profileFile,
                Bundle instrumentationArgs, IInstrumentationWatcher instrumentationWatcher,
                int debugMode, Configuration config,
                Map<String, IBinder> services) {
            Process.setArgV0(processName);

            if (services != null) {
                // Setup the service cache in the ServiceManager
                ServiceManager.initServiceCache(services);
            }

            AppBindData data = new AppBindData();
            data.processName = processName;
            data.appInfo = appInfo;
            data.providers = providers;
            data.instrumentationName = instrumentationName;
            data.profileFile = profileFile;
            data.instrumentationArgs = instrumentationArgs;
            data.instrumentationWatcher = instrumentationWatcher;
            data.debugMode = debugMode;
            data.config = config;
            queueOrSendMessage(H.BIND_APPLICATION, data);
        }

        public final void scheduleExit() {
            queueOrSendMessage(H.EXIT_APPLICATION, null);
        }

        public void requestThumbnail(IBinder token) {
            queueOrSendMessage(H.REQUEST_THUMBNAIL, token);
        }

        public void scheduleConfigurationChanged(Configuration config) {
            synchronized (mRelaunchingActivities) {
                mPendingConfiguration = config;
            }
            queueOrSendMessage(H.CONFIGURATION_CHANGED, config);
        }

        public void updateTimeZone() {
            TimeZone.setDefault(null);
        }

        public void processInBackground() {
            mH.removeMessages(H.GC_WHEN_IDLE);
            mH.sendMessage(mH.obtainMessage(H.GC_WHEN_IDLE));
        }

        public void dumpService(FileDescriptor fd, IBinder servicetoken, String[] args) {
            DumpServiceInfo data = new DumpServiceInfo();
            data.fd = fd;
            data.service = servicetoken;
            data.args = args;
            data.dumped = false;
            queueOrSendMessage(H.DUMP_SERVICE, data);
            synchronized (data) {
                while (!data.dumped) {
                    try {
                        data.wait();
                    } catch (InterruptedException e) {
                        // no need to do anything here, we will keep waiting until
                        // dumped is set
                    }
                }
            }
        }

        // This function exists to make sure all receiver dispatching is
        // correctly ordered, since these are one-way calls and the binder driver
        // applies transaction ordering per object for such calls.
        public void scheduleRegisteredReceiver(IIntentReceiver receiver, Intent intent,
                int resultCode, String dataStr, Bundle extras, boolean ordered)
                throws RemoteException {
            receiver.performReceive(intent, resultCode, dataStr, extras, ordered);
        }
        
        public void scheduleLowMemory() {
            queueOrSendMessage(H.LOW_MEMORY, null);
        }

        public void scheduleActivityConfigurationChanged(IBinder token) {
            queueOrSendMessage(H.ACTIVITY_CONFIGURATION_CHANGED, token);
        }

        public void requestPss() {
            try {
                ActivityManagerNative.getDefault().reportPss(this,
                        (int)Process.getPss(Process.myPid()));
            } catch (RemoteException e) {
            }
        }
        
        @Override
        protected void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
            long nativeMax = Debug.getNativeHeapSize() / 1024;
            long nativeAllocated = Debug.getNativeHeapAllocatedSize() / 1024;
            long nativeFree = Debug.getNativeHeapFreeSize() / 1024;

            Debug.MemoryInfo memInfo = new Debug.MemoryInfo();
            Debug.getMemoryInfo(memInfo);

            final int nativeShared = memInfo.nativeSharedDirty;
            final int dalvikShared = memInfo.dalvikSharedDirty;
            final int otherShared = memInfo.otherSharedDirty;

            final int nativePrivate = memInfo.nativePrivateDirty;
            final int dalvikPrivate = memInfo.dalvikPrivateDirty;
            final int otherPrivate = memInfo.otherPrivateDirty;

            Runtime runtime = Runtime.getRuntime();

            long dalvikMax = runtime.totalMemory() / 1024;
            long dalvikFree = runtime.freeMemory() / 1024;
            long dalvikAllocated = dalvikMax - dalvikFree;

            printRow(pw, HEAP_COLUMN, "", "native", "dalvik", "other", "total");
            printRow(pw, HEAP_COLUMN, "size:", nativeMax, dalvikMax, "N/A", nativeMax + dalvikMax);
            printRow(pw, HEAP_COLUMN, "allocated:", nativeAllocated, dalvikAllocated, "N/A",
                    nativeAllocated + dalvikAllocated);
            printRow(pw, HEAP_COLUMN, "free:", nativeFree, dalvikFree, "N/A",
                    nativeFree + dalvikFree);

            printRow(pw, HEAP_COLUMN, "(Pss):", memInfo.nativePss, memInfo.dalvikPss,
                    memInfo.otherPss, memInfo.nativePss + memInfo.dalvikPss + memInfo.otherPss);

            printRow(pw, HEAP_COLUMN, "(shared dirty):", nativeShared, dalvikShared, otherShared,
                    nativeShared + dalvikShared + otherShared);
            printRow(pw, HEAP_COLUMN, "(priv dirty):", nativePrivate, dalvikPrivate, otherPrivate,
                    nativePrivate + dalvikPrivate + otherPrivate);

            pw.println(" ");
            pw.println(" Objects");
            printRow(pw, TWO_COUNT_COLUMNS, "Views:", ViewDebug.getViewInstanceCount(), "ViewRoots:",
                    ViewDebug.getViewRootInstanceCount());

            printRow(pw, TWO_COUNT_COLUMNS, "AppContexts:", ApplicationContext.getInstanceCount(),
                    "Activities:", Activity.getInstanceCount());

            printRow(pw, TWO_COUNT_COLUMNS, "Assets:", AssetManager.getGlobalAssetCount(),
                    "AssetManagers:", AssetManager.getGlobalAssetManagerCount());

            printRow(pw, TWO_COUNT_COLUMNS, "Local Binders:", Debug.getBinderLocalObjectCount(),
                    "Proxy Binders:", Debug.getBinderProxyObjectCount());
            printRow(pw, ONE_COUNT_COLUMN, "Death Recipients:", Debug.getBinderDeathObjectCount());

            printRow(pw, ONE_COUNT_COLUMN, "OpenSSL Sockets:", OpenSSLSocketImpl.getInstanceCount());

            // SQLite mem info
            long sqliteAllocated = SQLiteDebug.getHeapAllocatedSize() / 1024;
            SQLiteDebug.PagerStats stats = new SQLiteDebug.PagerStats();
            SQLiteDebug.getPagerStats(stats);

            pw.println(" ");
            pw.println(" SQL");
            printRow(pw, TWO_COUNT_COLUMNS, "heap:", sqliteAllocated, "dbFiles:",
                    stats.databaseBytes / 1024);
            printRow(pw, TWO_COUNT_COLUMNS, "numPagers:", stats.numPagers, "inactivePageKB:",
                    (stats.totalBytes - stats.referencedBytes) / 1024);
            printRow(pw, ONE_COUNT_COLUMN, "activePageKB:", stats.referencedBytes / 1024);
        }

        private void printRow(PrintWriter pw, String format, Object...objs) {
            pw.println(String.format(format, objs));
        }
    }

    private final class H extends Handler {
        public static final int LAUNCH_ACTIVITY         = 100;
        public static final int PAUSE_ACTIVITY          = 101;
        public static final int PAUSE_ACTIVITY_FINISHING= 102;
        public static final int STOP_ACTIVITY_SHOW      = 103;
        public static final int STOP_ACTIVITY_HIDE      = 104;
        public static final int SHOW_WINDOW             = 105;
        public static final int HIDE_WINDOW             = 106;
        public static final int RESUME_ACTIVITY         = 107;
        public static final int SEND_RESULT             = 108;
        public static final int DESTROY_ACTIVITY         = 109;
        public static final int BIND_APPLICATION        = 110;
        public static final int EXIT_APPLICATION        = 111;
        public static final int NEW_INTENT              = 112;
        public static final int RECEIVER                = 113;
        public static final int CREATE_SERVICE          = 114;
        public static final int SERVICE_ARGS            = 115;
        public static final int STOP_SERVICE            = 116;
        public static final int REQUEST_THUMBNAIL       = 117;
        public static final int CONFIGURATION_CHANGED   = 118;
        public static final int CLEAN_UP_CONTEXT        = 119;
        public static final int GC_WHEN_IDLE            = 120;
        public static final int BIND_SERVICE            = 121;
        public static final int UNBIND_SERVICE          = 122;
        public static final int DUMP_SERVICE            = 123;
        public static final int LOW_MEMORY              = 124;
        public static final int ACTIVITY_CONFIGURATION_CHANGED = 125;
        public static final int RELAUNCH_ACTIVITY       = 126;
        String codeToString(int code) {
            if (localLOGV) {
                switch (code) {
                    case LAUNCH_ACTIVITY: return "LAUNCH_ACTIVITY";
                    case PAUSE_ACTIVITY: return "PAUSE_ACTIVITY";
                    case PAUSE_ACTIVITY_FINISHING: return "PAUSE_ACTIVITY_FINISHING";
                    case STOP_ACTIVITY_SHOW: return "STOP_ACTIVITY_SHOW";
                    case STOP_ACTIVITY_HIDE: return "STOP_ACTIVITY_HIDE";
                    case SHOW_WINDOW: return "SHOW_WINDOW";
                    case HIDE_WINDOW: return "HIDE_WINDOW";
                    case RESUME_ACTIVITY: return "RESUME_ACTIVITY";
                    case SEND_RESULT: return "SEND_RESULT";
                    case DESTROY_ACTIVITY: return "DESTROY_ACTIVITY";
                    case BIND_APPLICATION: return "BIND_APPLICATION";
                    case EXIT_APPLICATION: return "EXIT_APPLICATION";
                    case NEW_INTENT: return "NEW_INTENT";
                    case RECEIVER: return "RECEIVER";
                    case CREATE_SERVICE: return "CREATE_SERVICE";
                    case SERVICE_ARGS: return "SERVICE_ARGS";
                    case STOP_SERVICE: return "STOP_SERVICE";
                    case REQUEST_THUMBNAIL: return "REQUEST_THUMBNAIL";
                    case CONFIGURATION_CHANGED: return "CONFIGURATION_CHANGED";
                    case CLEAN_UP_CONTEXT: return "CLEAN_UP_CONTEXT";
                    case GC_WHEN_IDLE: return "GC_WHEN_IDLE";
                    case BIND_SERVICE: return "BIND_SERVICE";
                    case UNBIND_SERVICE: return "UNBIND_SERVICE";
                    case DUMP_SERVICE: return "DUMP_SERVICE";
                    case LOW_MEMORY: return "LOW_MEMORY";
                    case ACTIVITY_CONFIGURATION_CHANGED: return "ACTIVITY_CONFIGURATION_CHANGED";
                    case RELAUNCH_ACTIVITY: return "RELAUNCH_ACTIVITY";
                }
            }
            return "(unknown)";
        }
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case LAUNCH_ACTIVITY: {
                    ActivityRecord r = (ActivityRecord)msg.obj;

                    r.packageInfo = getPackageInfoNoCheck(
                            r.activityInfo.applicationInfo);
                    handleLaunchActivity(r);
                } break;
                case RELAUNCH_ACTIVITY: {
                    ActivityRecord r = (ActivityRecord)msg.obj;
                    handleRelaunchActivity(r, msg.arg1);
                } break;
                case PAUSE_ACTIVITY:
                    handlePauseActivity((IBinder)msg.obj, false, msg.arg2);
                    break;
                case PAUSE_ACTIVITY_FINISHING:
                    handlePauseActivity((IBinder)msg.obj, true, msg.arg2);
                    break;
                case STOP_ACTIVITY_SHOW:
                    handleStopActivity((IBinder)msg.obj, true, msg.arg2);
                    break;
                case STOP_ACTIVITY_HIDE:
                    handleStopActivity((IBinder)msg.obj, false, msg.arg2);
                    break;
                case SHOW_WINDOW:
                    handleWindowVisibility((IBinder)msg.obj, true);
                    break;
                case HIDE_WINDOW:
                    handleWindowVisibility((IBinder)msg.obj, false);
                    break;
                case RESUME_ACTIVITY:
                    handleResumeActivity((IBinder)msg.obj, true);
                    break;
                case SEND_RESULT:
                    handleSendResult((ResultData)msg.obj);
                    break;
                case DESTROY_ACTIVITY:
                    handleDestroyActivity((IBinder)msg.obj, msg.arg1 != 0,
                            msg.arg2, false);
                    break;
                case BIND_APPLICATION:
                    AppBindData data = (AppBindData)msg.obj;
                    handleBindApplication(data);
                    break;
                case EXIT_APPLICATION:
                    if (mInitialApplication != null) {
                        mInitialApplication.onTerminate();
                    }
                    Looper.myLooper().quit();
                    break;
                case NEW_INTENT:
                    handleNewIntent((NewIntentData)msg.obj);
                    break;
                case RECEIVER:
                    handleReceiver((ReceiverData)msg.obj);
                    break;
                case CREATE_SERVICE:
                    handleCreateService((CreateServiceData)msg.obj);
                    break;
                case BIND_SERVICE:
                    handleBindService((BindServiceData)msg.obj);
                    break;
                case UNBIND_SERVICE:
                    handleUnbindService((BindServiceData)msg.obj);
                    break;
                case SERVICE_ARGS:
                    handleServiceArgs((ServiceArgsData)msg.obj);
                    break;
                case STOP_SERVICE:
                    handleStopService((IBinder)msg.obj);
                    break;
                case REQUEST_THUMBNAIL:
                    handleRequestThumbnail((IBinder)msg.obj);
                    break;
                case CONFIGURATION_CHANGED:
                    handleConfigurationChanged((Configuration)msg.obj);
                    break;
                case CLEAN_UP_CONTEXT:
                    ContextCleanupInfo cci = (ContextCleanupInfo)msg.obj;
                    cci.context.performFinalCleanup(cci.who, cci.what);
                    break;
                case GC_WHEN_IDLE:
                    scheduleGcIdler();
                    break;
                case DUMP_SERVICE:
                    handleDumpService((DumpServiceInfo)msg.obj);
                    break;
                case LOW_MEMORY:
                    handleLowMemory();
                    break;
                case ACTIVITY_CONFIGURATION_CHANGED:
                    handleActivityConfigurationChanged((IBinder)msg.obj);
                    break;
            }
        }
    }

    private final class Idler implements MessageQueue.IdleHandler {
        public final boolean queueIdle() {
            ActivityRecord a = mNewActivities;
            if (a != null) {
                mNewActivities = null;
                IActivityManager am = ActivityManagerNative.getDefault();
                ActivityRecord prev;
                do {
                    if (localLOGV) Log.v(
                        TAG, "Reporting idle of " + a +
                        " finished=" +
                        (a.activity != null ? a.activity.mFinished : false));
                    if (a.activity != null && !a.activity.mFinished) {
                        try {
                            am.activityIdle(a.token);
                        } catch (RemoteException ex) {
                        }
                    }
                    prev = a;
                    a = a.nextIdle;
                    prev.nextIdle = null;
                } while (a != null);
            }
            return false;
        }
    }

    final class GcIdler implements MessageQueue.IdleHandler {
        public final boolean queueIdle() {
            doGcIfNeeded();
            return false;
        }
    }

    static IPackageManager sPackageManager;

    final ApplicationThread mAppThread = new ApplicationThread();
    final Looper mLooper = Looper.myLooper();
    final H mH = new H();
    final HashMap<IBinder, ActivityRecord> mActivities
            = new HashMap<IBinder, ActivityRecord>();
    // List of new activities (via ActivityRecord.nextIdle) that should
    // be reported when next we idle.
    ActivityRecord mNewActivities = null;
    // Number of activities that are currently visible on-screen.
    int mNumVisibleActivities = 0;
    final HashMap<IBinder, Service> mServices
            = new HashMap<IBinder, Service>();
    AppBindData mBoundApplication;
    Configuration mConfiguration;
    Application mInitialApplication;
    final ArrayList<Application> mAllApplications
            = new ArrayList<Application>();
    static final ThreadLocal sThreadLocal = new ThreadLocal();
    Instrumentation mInstrumentation;
    String mInstrumentationAppDir = null;
    String mInstrumentationAppPackage = null;
    String mInstrumentedAppDir = null;
    boolean mSystemThread = false;

    /**
     * Activities that are enqueued to be relaunched.  This list is accessed
     * by multiple threads, so you must synchronize on it when accessing it.
     */
    final ArrayList<ActivityRecord> mRelaunchingActivities
            = new ArrayList<ActivityRecord>();
    Configuration mPendingConfiguration = null;
    
    // These can be accessed by multiple threads; mPackages is the lock.
    // XXX For now we keep around information about all packages we have
    // seen, not removing entries from this map.
    final HashMap<String, WeakReference<PackageInfo>> mPackages
        = new HashMap<String, WeakReference<PackageInfo>>();
    final HashMap<String, WeakReference<PackageInfo>> mResourcePackages
        = new HashMap<String, WeakReference<PackageInfo>>();
    Display mDisplay = null;
    HashMap<String, WeakReference<Resources> > mActiveResources
        = new HashMap<String, WeakReference<Resources> >();

    // The lock of mProviderMap protects the following variables.
    final HashMap<String, ProviderRecord> mProviderMap
        = new HashMap<String, ProviderRecord>();
    final HashMap<IBinder, ProviderRefCount> mProviderRefCountMap
        = new HashMap<IBinder, ProviderRefCount>();
    final HashMap<IBinder, ProviderRecord> mLocalProviders
        = new HashMap<IBinder, ProviderRecord>();

    final GcIdler mGcIdler = new GcIdler();
    boolean mGcIdlerScheduled = false;

    public final PackageInfo getPackageInfo(String packageName, int flags) {
        synchronized (mPackages) {
            WeakReference<PackageInfo> ref;
            if ((flags&Context.CONTEXT_INCLUDE_CODE) != 0) {
                ref = mPackages.get(packageName);
            } else {
                ref = mResourcePackages.get(packageName);
            }
            PackageInfo packageInfo = ref != null ? ref.get() : null;
            //Log.i(TAG, "getPackageInfo " + packageName + ": " + packageInfo);
            if (packageInfo != null && (packageInfo.mResources == null
                    || packageInfo.mResources.getAssets().isUpToDate())) {
                if (packageInfo.isSecurityViolation()
                        && (flags&Context.CONTEXT_IGNORE_SECURITY) == 0) {
                    throw new SecurityException(
                            "Requesting code from " + packageName
                            + " to be run in process "
                            + mBoundApplication.processName
                            + "/" + mBoundApplication.appInfo.uid);
                }
                return packageInfo;
            }
        }

        ApplicationInfo ai = null;
        try {
            ai = getPackageManager().getApplicationInfo(packageName,
                    PackageManager.GET_SHARED_LIBRARY_FILES);
        } catch (RemoteException e) {
        }

        if (ai != null) {
            return getPackageInfo(ai, flags);
        }

        return null;
    }

    public final PackageInfo getPackageInfo(ApplicationInfo ai, int flags) {
        boolean includeCode = (flags&Context.CONTEXT_INCLUDE_CODE) != 0;
        boolean securityViolation = includeCode && ai.uid != 0
                && ai.uid != Process.SYSTEM_UID && (mBoundApplication != null
                        ? ai.uid != mBoundApplication.appInfo.uid : true);
        if ((flags&(Context.CONTEXT_INCLUDE_CODE
                |Context.CONTEXT_IGNORE_SECURITY))
                == Context.CONTEXT_INCLUDE_CODE) {
            if (securityViolation) {
                String msg = "Requesting code from " + ai.packageName
                        + " (with uid " + ai.uid + ")";
                if (mBoundApplication != null) {
                    msg = msg + " to be run in process "
                        + mBoundApplication.processName + " (with uid "
                        + mBoundApplication.appInfo.uid + ")";
                }
                throw new SecurityException(msg);
            }
        }
        return getPackageInfo(ai, null, securityViolation, includeCode);
    }

    public final PackageInfo getPackageInfoNoCheck(ApplicationInfo ai) {
        return getPackageInfo(ai, null, false, true);
    }

    private final PackageInfo getPackageInfo(ApplicationInfo aInfo,
            ClassLoader baseLoader, boolean securityViolation, boolean includeCode) {
        synchronized (mPackages) {
            WeakReference<PackageInfo> ref;
            if (includeCode) {
                ref = mPackages.get(aInfo.packageName);
            } else {
                ref = mResourcePackages.get(aInfo.packageName);
            }
            PackageInfo packageInfo = ref != null ? ref.get() : null;
            if (packageInfo == null || (packageInfo.mResources != null
                    && !packageInfo.mResources.getAssets().isUpToDate())) {
                if (localLOGV) Log.v(TAG, (includeCode ? "Loading code package "
                        : "Loading resource-only package ") + aInfo.packageName
                        + " (in " + (mBoundApplication != null
                                ? mBoundApplication.processName : null)
                        + ")");
                packageInfo =
                    new PackageInfo(this, aInfo, this, baseLoader,
                            securityViolation, includeCode &&
                            (aInfo.flags&ApplicationInfo.FLAG_HAS_CODE) != 0);
                if (includeCode) {
                    mPackages.put(aInfo.packageName,
                            new WeakReference<PackageInfo>(packageInfo));
                } else {
                    mResourcePackages.put(aInfo.packageName,
                            new WeakReference<PackageInfo>(packageInfo));
                }
            }
            return packageInfo;
        }
    }

    public final boolean hasPackageInfo(String packageName) {
        synchronized (mPackages) {
            WeakReference<PackageInfo> ref;
            ref = mPackages.get(packageName);
            if (ref != null && ref.get() != null) {
                return true;
            }
            ref = mResourcePackages.get(packageName);
            if (ref != null && ref.get() != null) {
                return true;
            }
            return false;
        }
    }
    
    ActivityThread() {
    }

    public ApplicationThread getApplicationThread()
    {
        return mAppThread;
    }

    public Instrumentation getInstrumentation()
    {
        return mInstrumentation;
    }

    public Configuration getConfiguration() {
        return mConfiguration;
    }

    public boolean isProfiling() {
        return mBoundApplication != null && mBoundApplication.profileFile != null;
    }

    public String getProfileFilePath() {
        return mBoundApplication.profileFile;
    }

    public Looper getLooper() {
        return mLooper;
    }

    public Application getApplication() {
        return mInitialApplication;
    }
    
    public ApplicationContext getSystemContext() {
        synchronized (this) {
            if (mSystemContext == null) {
                ApplicationContext context =
                    ApplicationContext.createSystemContext(this);
                PackageInfo info = new PackageInfo(this, "android", context);
                context.init(info, null, this);
                context.getResources().updateConfiguration(
                        getConfiguration(), getDisplayMetricsLocked());
                mSystemContext = context;
                //Log.i(TAG, "Created system resources " + context.getResources()
                //        + ": " + context.getResources().getConfiguration());
            }
        }
        return mSystemContext;
    }

    void scheduleGcIdler() {
        if (!mGcIdlerScheduled) {
            mGcIdlerScheduled = true;
            Looper.myQueue().addIdleHandler(mGcIdler);
        }
        mH.removeMessages(H.GC_WHEN_IDLE);
    }

    void unscheduleGcIdler() {
        if (mGcIdlerScheduled) {
            mGcIdlerScheduled = false;
            Looper.myQueue().removeIdleHandler(mGcIdler);
        }
        mH.removeMessages(H.GC_WHEN_IDLE);
    }

    void doGcIfNeeded() {
        mGcIdlerScheduled = false;
        final long now = SystemClock.uptimeMillis();
        //Log.i(TAG, "**** WE MIGHT WANT TO GC: then=" + Binder.getLastGcTime()
        //        + "m now=" + now);
        if ((BinderInternal.getLastGcTime()+MIN_TIME_BETWEEN_GCS) < now) {
            //Log.i(TAG, "**** WE DO, WE DO WANT TO GC!");
            BinderInternal.forceGc("bg");
        }
    }

    public final ActivityInfo resolveActivityInfo(Intent intent) {
        ActivityInfo aInfo = intent.resolveActivityInfo(
                mInitialApplication.getPackageManager(), PackageManager.GET_SHARED_LIBRARY_FILES);
        if (aInfo == null) {
            // Throw an exception.
            Instrumentation.checkStartActivityResult(
                    IActivityManager.START_CLASS_NOT_FOUND, intent);
        }
        return aInfo;
    }
    
    public final Activity startActivityNow(Activity parent, String id,
            Intent intent, IBinder token, Bundle state) {
        ActivityInfo aInfo = resolveActivityInfo(intent);
        return startActivityNow(parent, id, intent, aInfo, token, state);
    }
    
    public final Activity startActivityNow(Activity parent, String id,
            Intent intent, ActivityInfo activityInfo, IBinder token, Bundle state) {
        ActivityRecord r = new ActivityRecord();
            r.token = token;
            r.intent = intent;
            r.state = state;
            r.parent = parent;
            r.embeddedID = id;
            r.activityInfo = activityInfo;
        if (localLOGV) {
            ComponentName compname = intent.getComponent();
            String name;
            if (compname != null) {
                name = compname.toShortString();
            } else {
                name = "(Intent " + intent + ").getComponent() returned null";
            }
            Log.v(TAG, "Performing launch: action=" + intent.getAction()
                    + ", comp=" + name
                    + ", token=" + token);
        }
        return performLaunchActivity(r);
    }

    public final Activity getActivity(IBinder token) {
        return mActivities.get(token).activity;
    }

    public final void sendActivityResult(
            IBinder token, String id, int requestCode,
            int resultCode, Intent data) {
        ArrayList<ResultInfo> list = new ArrayList<ResultInfo>();
        list.add(new ResultInfo(id, requestCode, resultCode, data));
        mAppThread.scheduleSendResult(token, list);
    }

    // if the thread hasn't started yet, we don't have the handler, so just
    // save the messages until we're ready.
    private final void queueOrSendMessage(int what, Object obj) {
        queueOrSendMessage(what, obj, 0, 0);
    }

    private final void queueOrSendMessage(int what, Object obj, int arg1) {
        queueOrSendMessage(what, obj, arg1, 0);
    }

    private final void queueOrSendMessage(int what, Object obj, int arg1, int arg2) {
        synchronized (this) {
            if (localLOGV) Log.v(
                TAG, "SCHEDULE " + what + " " + mH.codeToString(what)
                + ": " + arg1 + " / " + obj);
            Message msg = Message.obtain();
            msg.what = what;
            msg.obj = obj;
            msg.arg1 = arg1;
            msg.arg2 = arg2;
            mH.sendMessage(msg);
        }
    }

    final void scheduleContextCleanup(ApplicationContext context, String who,
            String what) {
        ContextCleanupInfo cci = new ContextCleanupInfo();
        cci.context = context;
        cci.who = who;
        cci.what = what;
        queueOrSendMessage(H.CLEAN_UP_CONTEXT, cci);
    }

    private final Activity performLaunchActivity(ActivityRecord r) {
        // System.out.println("##### [" + System.currentTimeMillis() + "] ActivityThread.performLaunchActivity(" + r + ")");

        ActivityInfo aInfo = r.activityInfo;
        if (r.packageInfo == null) {
            r.packageInfo = getPackageInfo(aInfo.applicationInfo,
                    Context.CONTEXT_INCLUDE_CODE);
        }
            
        ComponentName component = r.intent.getComponent();
        if (component == null) {
            component = r.intent.resolveActivity(
                mInitialApplication.getPackageManager());
            r.intent.setComponent(component);
        }

        if (r.activityInfo.targetActivity != null) {
            component = new ComponentName(r.activityInfo.packageName,
                    r.activityInfo.targetActivity);
        }

        Activity activity = null;
        try {
            java.lang.ClassLoader cl = r.packageInfo.getClassLoader();
            activity = mInstrumentation.newActivity(
                    cl, component.getClassName(), r.intent);
            r.intent.setExtrasClassLoader(cl);
            if (r.state != null) {
                r.state.setClassLoader(cl);
            }
        } catch (Exception e) {
            if (!mInstrumentation.onException(activity, e)) {
                throw new RuntimeException(
                    "Unable to instantiate activity " + component
                    + ": " + e.toString(), e);
            }
        }

        try {
            Application app = r.packageInfo.makeApplication();
            
            if (localLOGV) Log.v(TAG, "Performing launch of " + r);
            if (localLOGV) Log.v(
                    TAG, r + ": app=" + app
                    + ", appName=" + app.getPackageName()
                    + ", pkg=" + r.packageInfo.getPackageName()
                    + ", comp=" + r.intent.getComponent().toShortString()
                    + ", dir=" + r.packageInfo.getAppDir());

            if (activity != null) {
                ApplicationContext appContext = new ApplicationContext();
                appContext.init(r.packageInfo, r.token, this);
                appContext.setOuterContext(activity);
                CharSequence title = r.activityInfo.loadLabel(appContext.getPackageManager());
                Configuration config = new Configuration(mConfiguration);
                activity.attach(appContext, this, getInstrumentation(), r.token, app, 
                        r.intent, r.activityInfo, title, r.parent, r.embeddedID,
                        r.lastNonConfigurationInstance, config);
                
                r.lastNonConfigurationInstance = null;
                activity.mStartedActivity = false;
                int theme = r.activityInfo.getThemeResource();
                if (theme != 0) {
                    activity.setTheme(theme);
                }

                activity.mCalled = false;
                mInstrumentation.callActivityOnCreate(activity, r.state);
                if (!activity.mCalled) {
                    throw new SuperNotCalledException(
                        "Activity " + r.intent.getComponent().toShortString() +
                        " did not call through to super.onCreate()");
                }
                r.activity = activity;
                r.stopped = true;
                if (!r.activity.mFinished) {
                    activity.performStart();
                    r.stopped = false;
                }
                if (!r.activity.mFinished) {
                    if (r.state != null) {
                        mInstrumentation.callActivityOnRestoreInstanceState(activity, r.state);
                    }
                }
                if (!r.activity.mFinished) {
                    activity.mCalled = false;
                    mInstrumentation.callActivityOnPostCreate(activity, r.state);
                    if (!activity.mCalled) {
                        throw new SuperNotCalledException(
                            "Activity " + r.intent.getComponent().toShortString() +
                            " did not call through to super.onPostCreate()");
                    }
                }
                r.state = null;
            }
            r.paused = true;

            mActivities.put(r.token, r);

        } catch (SuperNotCalledException e) {
            throw e;

        } catch (Exception e) {
            if (!mInstrumentation.onException(activity, e)) {
                throw new RuntimeException(
                    "Unable to start activity " + component
                    + ": " + e.toString(), e);
            }
        }

        return activity;
    }

    private final void handleLaunchActivity(ActivityRecord r) {
        // If we are getting ready to gc after going to the background, well
        // we are back active so skip it.
        unscheduleGcIdler();

        if (localLOGV) Log.v(
            TAG, "Handling launch of " + r);
        Activity a = performLaunchActivity(r);

        if (a != null) {
            handleResumeActivity(r.token, false);

            if (!r.activity.mFinished && r.startsNotResumed) {
                // The activity manager actually wants this one to start out
                // paused, because it needs to be visible but isn't in the
                // foreground.  We accomplish this by going through the
                // normal startup (because activities expect to go through
                // onResume() the first time they run, before their window
                // is displayed), and then pausing it.  However, in this case
                // we do -not- need to do the full pause cycle (of freezing
                // and such) because the activity manager assumes it can just
                // retain the current state it has.
                try {
                    r.activity.mCalled = false;
                    mInstrumentation.callActivityOnPause(r.activity);
                    if (!r.activity.mCalled) {
                        throw new SuperNotCalledException(
                            "Activity " + r.intent.getComponent().toShortString() +
                            " did not call through to super.onPause()");
                    }

                } catch (SuperNotCalledException e) {
                    throw e;

                } catch (Exception e) {
                    if (!mInstrumentation.onException(r.activity, e)) {
                        throw new RuntimeException(
                                "Unable to pause activity "
                                + r.intent.getComponent().toShortString()
                                + ": " + e.toString(), e);
                    }
                }
                r.paused = true;
            }
        } else {
            // If there was an error, for any reason, tell the activity
            // manager to stop us.
            try {
                ActivityManagerNative.getDefault()
                    .finishActivity(r.token, Activity.RESULT_CANCELED, null);
            } catch (RemoteException ex) {
            }
        }
    }

    private final void deliverNewIntents(ActivityRecord r,
            List<Intent> intents) {
        final int N = intents.size();
        for (int i=0; i<N; i++) {
            Intent intent = intents.get(i);
            intent.setExtrasClassLoader(r.activity.getClassLoader());
            mInstrumentation.callActivityOnNewIntent(r.activity, intent);
        }
    }

    public final void performNewIntents(IBinder token,
            List<Intent> intents) {
        ActivityRecord r = mActivities.get(token);
        if (r != null) {
            final boolean resumed = !r.paused;
            if (resumed) {
                mInstrumentation.callActivityOnPause(r.activity);
            }
            deliverNewIntents(r, intents);
            if (resumed) {
                mInstrumentation.callActivityOnResume(r.activity);
            }
        }
    }
    
    private final void handleNewIntent(NewIntentData data) {
        performNewIntents(data.token, data.intents);
    }

    private final void handleReceiver(ReceiverData data) {
        // If we are getting ready to gc after going to the background, well
        // we are back active so skip it.
        unscheduleGcIdler();

        String component = data.intent.getComponent().getClassName();

        PackageInfo packageInfo = getPackageInfoNoCheck(
                data.info.applicationInfo);

        IActivityManager mgr = ActivityManagerNative.getDefault();

        BroadcastReceiver receiver = null;
        try {
            java.lang.ClassLoader cl = packageInfo.getClassLoader();
            data.intent.setExtrasClassLoader(cl);
            if (data.resultExtras != null) {
                data.resultExtras.setClassLoader(cl);
            }
            receiver = (BroadcastReceiver)cl.loadClass(component).newInstance();
        } catch (Exception e) {
            try {
                mgr.finishReceiver(mAppThread.asBinder(), data.resultCode,
                                   data.resultData, data.resultExtras, data.resultAbort);
            } catch (RemoteException ex) {
            }
            throw new RuntimeException(
                "Unable to instantiate receiver " + component
                + ": " + e.toString(), e);
        }

        try {
            Application app = packageInfo.makeApplication();
            
            if (localLOGV) Log.v(
                TAG, "Performing receive of " + data.intent
                + ": app=" + app
                + ", appName=" + app.getPackageName()
                + ", pkg=" + packageInfo.getPackageName()
                + ", comp=" + data.intent.getComponent().toShortString()
                + ", dir=" + packageInfo.getAppDir());

            ApplicationContext context = (ApplicationContext)app.getBaseContext();
            receiver.setOrderedHint(true);
            receiver.setResult(data.resultCode, data.resultData,
                data.resultExtras);
            receiver.setOrderedHint(data.sync);
            receiver.onReceive(context.getReceiverRestrictedContext(),
                    data.intent);
        } catch (Exception e) {
            try {
                mgr.finishReceiver(mAppThread.asBinder(), data.resultCode,
                    data.resultData, data.resultExtras, data.resultAbort);
            } catch (RemoteException ex) {
            }
            if (!mInstrumentation.onException(receiver, e)) {
                throw new RuntimeException(
                    "Unable to start receiver " + component
                    + ": " + e.toString(), e);
            }
        }

        try {
            if (data.sync) {
                mgr.finishReceiver(
                    mAppThread.asBinder(), receiver.getResultCode(),
                    receiver.getResultData(), receiver.getResultExtras(false),
                        receiver.getAbortBroadcast());
            } else {
                mgr.finishReceiver(mAppThread.asBinder(), 0, null, null, false);
            }
        } catch (RemoteException ex) {
        }
    }

    private final void handleCreateService(CreateServiceData data) {
        // If we are getting ready to gc after going to the background, well
        // we are back active so skip it.
        unscheduleGcIdler();

        PackageInfo packageInfo = getPackageInfoNoCheck(
                data.info.applicationInfo);
        Service service = null;
        try {
            java.lang.ClassLoader cl = packageInfo.getClassLoader();
            service = (Service) cl.loadClass(data.info.name).newInstance();
        } catch (Exception e) {
            if (!mInstrumentation.onException(service, e)) {
                throw new RuntimeException(
                    "Unable to instantiate service " + data.info.name
                    + ": " + e.toString(), e);
            }
        }

        try {
            if (localLOGV) Log.v(TAG, "Creating service " + data.info.name);

            ApplicationContext context = new ApplicationContext();
            context.init(packageInfo, null, this);

            Application app = packageInfo.makeApplication();
            context.setOuterContext(service);
            service.attach(context, this, data.info.name, data.token, app,
                    ActivityManagerNative.getDefault());
            service.onCreate();
            mServices.put(data.token, service);
            try {
                ActivityManagerNative.getDefault().serviceDoneExecuting(data.token);
            } catch (RemoteException e) {
                // nothing to do.
            }
        } catch (Exception e) {
            if (!mInstrumentation.onException(service, e)) {
                throw new RuntimeException(
                    "Unable to create service " + data.info.name
                    + ": " + e.toString(), e);
            }
        }
    }

    private final void handleBindService(BindServiceData data) {
        Service s = mServices.get(data.token);
        if (s != null) {
            try {
                data.intent.setExtrasClassLoader(s.getClassLoader());
                try {
                    if (!data.rebind) {
                        IBinder binder = s.onBind(data.intent);
                        ActivityManagerNative.getDefault().publishService(
                                data.token, data.intent, binder);
                    } else {
                        s.onRebind(data.intent);
                        ActivityManagerNative.getDefault().serviceDoneExecuting(
                                data.token);
                    }
                } catch (RemoteException ex) {
                }
            } catch (Exception e) {
                if (!mInstrumentation.onException(s, e)) {
                    throw new RuntimeException(
                            "Unable to bind to service " + s
                            + " with " + data.intent + ": " + e.toString(), e);
                }
            }
        }
    }

    private final void handleUnbindService(BindServiceData data) {
        Service s = mServices.get(data.token);
        if (s != null) {
            try {
                data.intent.setExtrasClassLoader(s.getClassLoader());
                boolean doRebind = s.onUnbind(data.intent);
                try {
                    if (doRebind) {
                        ActivityManagerNative.getDefault().unbindFinished(
                                data.token, data.intent, doRebind);
                    } else {
                        ActivityManagerNative.getDefault().serviceDoneExecuting(
                                data.token);
                    }
                } catch (RemoteException ex) {
                }
            } catch (Exception e) {
                if (!mInstrumentation.onException(s, e)) {
                    throw new RuntimeException(
                            "Unable to unbind to service " + s
                            + " with " + data.intent + ": " + e.toString(), e);
                }
            }
        }
    }

    private void handleDumpService(DumpServiceInfo info) {
        try {
            Service s = mServices.get(info.service);
            if (s != null) {
                PrintWriter pw = new PrintWriter(new FileOutputStream(info.fd));
                s.dump(info.fd, pw, info.args);
                pw.close();
            }
        } finally {
            synchronized (info) {
                info.dumped = true;
                info.notifyAll();
            }
        }
    }

    private final void handleServiceArgs(ServiceArgsData data) {
        Service s = mServices.get(data.token);
        if (s != null) {
            try {
                if (data.args != null) {
                    data.args.setExtrasClassLoader(s.getClassLoader());
                }
                s.onStart(data.args, data.startId);
                try {
                    ActivityManagerNative.getDefault().serviceDoneExecuting(data.token);
                } catch (RemoteException e) {
                    // nothing to do.
                }
            } catch (Exception e) {
                if (!mInstrumentation.onException(s, e)) {
                    throw new RuntimeException(
                            "Unable to start service " + s
                            + " with " + data.args + ": " + e.toString(), e);
                }
            }
        }
    }

    private final void handleStopService(IBinder token) {
        Service s = mServices.remove(token);
        if (s != null) {
            try {
                if (localLOGV) Log.v(TAG, "Destroying service " + s);
                s.onDestroy();
                Context context = s.getBaseContext();
                if (context instanceof ApplicationContext) {
                    final String who = s.getClassName();
                    ((ApplicationContext) context).scheduleFinalCleanup(who, "Service");
                }
                try {
                    ActivityManagerNative.getDefault().serviceDoneExecuting(token);
                } catch (RemoteException e) {
                    // nothing to do.
                }
            } catch (Exception e) {
                if (!mInstrumentation.onException(s, e)) {
                    throw new RuntimeException(
                            "Unable to stop service " + s
                            + ": " + e.toString(), e);
                }
            }
        }
        //Log.i(TAG, "Running services: " + mServices);
    }

    public final ActivityRecord performResumeActivity(IBinder token,
            boolean clearHide) {
        ActivityRecord r = mActivities.get(token);
        if (localLOGV) Log.v(TAG, "Performing resume of " + r
                + " finished=" + r.activity.mFinished);
        if (r != null && !r.activity.mFinished) {
            if (clearHide) {
                r.hideForNow = false;
                r.activity.mStartedActivity = false;
            }
            try {
                if (r.pendingIntents != null) {
                    deliverNewIntents(r, r.pendingIntents);
                    r.pendingIntents = null;
                }
                if (r.pendingResults != null) {
                    deliverResults(r, r.pendingResults);
                    r.pendingResults = null;
                }
                r.activity.performResume();

                EventLog.writeEvent(LOG_ON_RESUME_CALLED, 
                        r.activity.getComponentName().getClassName());
                
                r.paused = false;
                r.stopped = false;
                if (r.activity.mStartedActivity) {
                    r.hideForNow = true;
                }
                r.state = null;
            } catch (Exception e) {
                if (!mInstrumentation.onException(r.activity, e)) {
                    throw new RuntimeException(
                        "Unable to resume activity "
                        + r.intent.getComponent().toShortString()
                        + ": " + e.toString(), e);
                }
            }
        }
        return r;
    }

    final void handleResumeActivity(IBinder token, boolean clearHide) {
        // If we are getting ready to gc after going to the background, well
        // we are back active so skip it.
        unscheduleGcIdler();

        ActivityRecord r = performResumeActivity(token, clearHide);

        if (r != null) {
            final Activity a = r.activity;

            if (localLOGV) Log.v(
                TAG, "Resume " + r + " started activity: " +
                a.mStartedActivity + ", hideForNow: " + r.hideForNow
                + ", finished: " + a.mFinished);

            // If the window hasn't yet been added to the window manager,
            // and this guy didn't finish itself or start another activity,
            // then go ahead and add the window.
            if (r.window == null && !a.mFinished && !a.mStartedActivity) {
                r.window = r.activity.getWindow();
                View decor = r.window.getDecorView();
                decor.setVisibility(View.INVISIBLE);
                ViewManager wm = a.getWindowManager();
                WindowManager.LayoutParams l = r.window.getAttributes();
                a.mDecor = decor;
                l.type = WindowManager.LayoutParams.TYPE_BASE_APPLICATION;
                wm.addView(decor, l);

            // If the window has already been added, but during resume
            // we started another activity, then don't yet make the
            // window visisble.
            } else if (a.mStartedActivity) {
                if (localLOGV) Log.v(
                    TAG, "Launch " + r + " mStartedActivity set");
                r.hideForNow = true;
            }

            // The window is now visible if it has been added, we are not
            // simply finishing, and we are not starting another activity.
            if (!r.activity.mFinished && r.activity.mDecor != null
                    && !r.hideForNow) {
                if (r.newConfig != null) {
                    performConfigurationChanged(r.activity, r.newConfig);
                    r.newConfig = null;
                }
                r.activity.mDecor.setVisibility(View.VISIBLE);
                mNumVisibleActivities++;
            }

            r.nextIdle = mNewActivities;
            mNewActivities = r;
            if (localLOGV) Log.v(
                TAG, "Scheduling idle handler for " + r);
            Looper.myQueue().addIdleHandler(new Idler());

        } else {
            // If an exception was thrown when trying to resume, then
            // just end this activity.
            try {
                ActivityManagerNative.getDefault()
                    .finishActivity(token, Activity.RESULT_CANCELED, null);
            } catch (RemoteException ex) {
            }
        }
    }

    private int mThumbnailWidth = -1;
    private int mThumbnailHeight = -1;

    private final Bitmap createThumbnailBitmap(ActivityRecord r) {
        Bitmap thumbnail = null;
        try {
            int w = mThumbnailWidth;
            int h;
            if (w < 0) {
                Resources res = r.activity.getResources();
                mThumbnailHeight = h =
                    res.getDimensionPixelSize(com.android.internal.R.dimen.thumbnail_height);

                mThumbnailWidth = w =
                    res.getDimensionPixelSize(com.android.internal.R.dimen.thumbnail_width);
            } else {
                h = mThumbnailHeight;
            }

            // XXX Only set hasAlpha if needed?
            thumbnail = Bitmap.createBitmap(w, h, Bitmap.Config.RGB_565);
            thumbnail.eraseColor(0);
            Canvas cv = new Canvas(thumbnail);
            if (!r.activity.onCreateThumbnail(thumbnail, cv)) {
                thumbnail = null;
            }
        } catch (Exception e) {
            if (!mInstrumentation.onException(r.activity, e)) {
                throw new RuntimeException(
                        "Unable to create thumbnail of "
                        + r.intent.getComponent().toShortString()
                        + ": " + e.toString(), e);
            }
            thumbnail = null;
        }

        return thumbnail;
    }

    private final void handlePauseActivity(IBinder token, boolean finished,
            int configChanges) {
        ActivityRecord r = mActivities.get(token);
        if (r != null) {
            r.activity.mConfigChangeFlags |= configChanges;
            Bundle state = performPauseActivity(token, finished, true);

            // Tell the activity manager we have paused.
            try {
                ActivityManagerNative.getDefault().activityPaused(token, state);
            } catch (RemoteException ex) {
            }
        }
    }

    final Bundle performPauseActivity(IBinder token, boolean finished,
            boolean saveState) {
        ActivityRecord r = mActivities.get(token);
        return r != null ? performPauseActivity(r, finished, saveState) : null;
    }

    final Bundle performPauseActivity(ActivityRecord r, boolean finished,
            boolean saveState) {
        if (r.paused) {
            if (r.activity.mFinished) {
                // If we are finishing, we won't call onResume() in certain cases.
                // So here we likewise don't want to call onPause() if the activity
                // isn't resumed.
                return null;
            }
            RuntimeException e = new RuntimeException(
                    "Performing pause of activity that is not resumed: "
                    + r.intent.getComponent().toShortString());
            Log.e(TAG, e.getMessage(), e);
        }
        Bundle state = null;
        if (finished) {
            r.activity.mFinished = true;
        }
        try {
            // Next have the activity save its current state and managed dialogs...
            if (!r.activity.mFinished && saveState) {
                state = new Bundle();
                mInstrumentation.callActivityOnSaveInstanceState(r.activity, state);
                r.state = state;
            }
            // Now we are idle.
            r.activity.mCalled = false;
            mInstrumentation.callActivityOnPause(r.activity);
            EventLog.writeEvent(LOG_ON_PAUSE_CALLED, r.activity.getComponentName().getClassName());
            if (!r.activity.mCalled) {
                throw new SuperNotCalledException(
                    "Activity " + r.intent.getComponent().toShortString() +
                    " did not call through to super.onPause()");
            }

        } catch (SuperNotCalledException e) {
            throw e;

        } catch (Exception e) {
            if (!mInstrumentation.onException(r.activity, e)) {
                throw new RuntimeException(
                        "Unable to pause activity "
                        + r.intent.getComponent().toShortString()
                        + ": " + e.toString(), e);
            }
        }
        r.paused = true;
        return state;
    }

    final void performStopActivity(IBinder token) {
        ActivityRecord r = mActivities.get(token);
        performStopActivityInner(r, null, false);
    }

    private static class StopInfo {
        Bitmap thumbnail;
        CharSequence description;
    }

    private final class ProviderRefCount {
        public int count;
        ProviderRefCount(int pCount) {
            count = pCount;
        }
    }

    private final void performStopActivityInner(ActivityRecord r,
            StopInfo info, boolean keepShown) {
        if (localLOGV) Log.v(TAG, "Performing stop of " + r);
        if (r != null) {
            if (!keepShown && r.stopped) {
                if (r.activity.mFinished) {
                    // If we are finishing, we won't call onResume() in certain
                    // cases.  So here we likewise don't want to call onStop()
                    // if the activity isn't resumed.
                    return;
                }
                RuntimeException e = new RuntimeException(
                        "Performing stop of activity that is not resumed: "
                        + r.intent.getComponent().toShortString());
                Log.e(TAG, e.getMessage(), e);
            }

            if (info != null) {
                try {
                    // First create a thumbnail for the activity...
                    //info.thumbnail = createThumbnailBitmap(r);
                    info.description = r.activity.onCreateDescription();
                } catch (Exception e) {
                    if (!mInstrumentation.onException(r.activity, e)) {
                        throw new RuntimeException(
                                "Unable to save state of activity "
                                + r.intent.getComponent().toShortString()
                                + ": " + e.toString(), e);
                    }
                }
            }

            if (!keepShown) {
                try {
                    // Now we are idle.
                    r.activity.performStop();
                } catch (Exception e) {
                    if (!mInstrumentation.onException(r.activity, e)) {
                        throw new RuntimeException(
                                "Unable to stop activity "
                                + r.intent.getComponent().toShortString()
                                + ": " + e.toString(), e);
                    }
                }
                r.stopped = true;
            }

            r.paused = true;
        }
    }

    private final void updateVisibility(ActivityRecord r, boolean show) {
        View v = r.activity.mDecor;
        if (v != null) {
            if (show) {
                if (v.getVisibility() != View.VISIBLE) {
                    v.setVisibility(View.VISIBLE);
                    mNumVisibleActivities++;
                }
                if (r.newConfig != null) {
                    performConfigurationChanged(r.activity, r.newConfig);
                    r.newConfig = null;
                }
            } else {
                if (v.getVisibility() == View.VISIBLE) {
                    v.setVisibility(View.INVISIBLE);
                    mNumVisibleActivities--;
                }
            }
        }
    }

    private final void handleStopActivity(IBinder token, boolean show, int configChanges) {
        ActivityRecord r = mActivities.get(token);
        r.activity.mConfigChangeFlags |= configChanges;

        StopInfo info = new StopInfo();
        performStopActivityInner(r, info, show);

        if (localLOGV) Log.v(
            TAG, "Finishing stop of " + r + ": show=" + show
            + " win=" + r.window);

        updateVisibility(r, show);
        
        // Tell activity manager we have been stopped.
        try {
            ActivityManagerNative.getDefault().activityStopped(
                r.token, info.thumbnail, info.description);
        } catch (RemoteException ex) {
        }
    }

    final void performRestartActivity(IBinder token) {
        ActivityRecord r = mActivities.get(token);
        if (r.stopped) {
            r.activity.performRestart();
            r.stopped = false;
        }
    }

    private final void handleWindowVisibility(IBinder token, boolean show) {
        ActivityRecord r = mActivities.get(token);
        if (!show && !r.stopped) {
            performStopActivityInner(r, null, show);
        } else if (show && r.stopped) {
            // If we are getting ready to gc after going to the background, well
            // we are back active so skip it.
            unscheduleGcIdler();

            r.activity.performRestart();
            r.stopped = false;
        }
        if (r.activity.mDecor != null) {
            if (Config.LOGV) Log.v(
                TAG, "Handle window " + r + " visibility: " + show);
            updateVisibility(r, show);
        }
    }

    private final void deliverResults(ActivityRecord r, List<ResultInfo> results) {
        final int N = results.size();
        for (int i=0; i<N; i++) {
            ResultInfo ri = results.get(i);
            try {
                if (ri.mData != null) {
                    ri.mData.setExtrasClassLoader(r.activity.getClassLoader());
                }
                r.activity.dispatchActivityResult(ri.mResultWho,
                        ri.mRequestCode, ri.mResultCode, ri.mData);
            } catch (Exception e) {
                if (!mInstrumentation.onException(r.activity, e)) {
                    throw new RuntimeException(
                            "Failure delivering result " + ri + " to activity "
                            + r.intent.getComponent().toShortString()
                            + ": " + e.toString(), e);
                }
            }
        }
    }

    private final void handleSendResult(ResultData res) {
        ActivityRecord r = mActivities.get(res.token);
        if (localLOGV) Log.v(TAG, "Handling send result to " + r);
        if (r != null) {
            final boolean resumed = !r.paused;
            if (!r.activity.mFinished && r.activity.mDecor != null
                    && r.hideForNow && resumed) {
                // We had hidden the activity because it started another
                // one...  we have gotten a result back and we are not
                // paused, so make sure our window is visible.
                updateVisibility(r, true);
            }
            if (resumed) {
                try {
                    // Now we are idle.
                    r.activity.mCalled = false;
                    mInstrumentation.callActivityOnPause(r.activity);
                    if (!r.activity.mCalled) {
                        throw new SuperNotCalledException(
                            "Activity " + r.intent.getComponent().toShortString()
                            + " did not call through to super.onPause()");
                    }
                } catch (SuperNotCalledException e) {
                    throw e;
                } catch (Exception e) {
                    if (!mInstrumentation.onException(r.activity, e)) {
                        throw new RuntimeException(
                                "Unable to pause activity "
                                + r.intent.getComponent().toShortString()
                                + ": " + e.toString(), e);
                    }
                }
            }
            deliverResults(r, res.results);
            if (resumed) {
                mInstrumentation.callActivityOnResume(r.activity);
            }
        }
    }

    public final ActivityRecord performDestroyActivity(IBinder token, boolean finishing) {
        return performDestroyActivity(token, finishing, 0, false);
    }

    private final ActivityRecord performDestroyActivity(IBinder token, boolean finishing,
            int configChanges, boolean getNonConfigInstance) {
        ActivityRecord r = mActivities.get(token);
        if (localLOGV) Log.v(TAG, "Performing finish of " + r);
        if (r != null) {
            r.activity.mConfigChangeFlags |= configChanges;
            if (finishing) {
                r.activity.mFinished = true;
            }
            if (!r.paused) {
                try {
                    r.activity.mCalled = false;
                    mInstrumentation.callActivityOnPause(r.activity);
                    EventLog.writeEvent(LOG_ON_PAUSE_CALLED, 
                            r.activity.getComponentName().getClassName());
                    if (!r.activity.mCalled) {
                        throw new SuperNotCalledException(
                            "Activity " + r.intent.getComponent().toShortString()
                            + " did not call through to super.onPause()");
                    }
                } catch (SuperNotCalledException e) {
                    throw e;
                } catch (Exception e) {
                    if (!mInstrumentation.onException(r.activity, e)) {
                        throw new RuntimeException(
                                "Unable to pause activity "
                                + r.intent.getComponent().toShortString()
                                + ": " + e.toString(), e);
                    }
                }
                r.paused = true;
            }
            if (!r.stopped) {
                try {
                    r.activity.performStop();
                } catch (SuperNotCalledException e) {
                    throw e;
                } catch (Exception e) {
                    if (!mInstrumentation.onException(r.activity, e)) {
                        throw new RuntimeException(
                                "Unable to stop activity "
                                + r.intent.getComponent().toShortString()
                                + ": " + e.toString(), e);
                    }
                }
                r.stopped = true;
            }
            if (getNonConfigInstance) {
                try {
                    r.lastNonConfigurationInstance
                            = r.activity.onRetainNonConfigurationInstance();
                } catch (Exception e) {
                    if (!mInstrumentation.onException(r.activity, e)) {
                        throw new RuntimeException(
                                "Unable to retain activity "
                                + r.intent.getComponent().toShortString()
                                + ": " + e.toString(), e);
                    }
                }
                
            }
            try {
                r.activity.mCalled = false;
                r.activity.onDestroy();
                if (!r.activity.mCalled) {
                    throw new SuperNotCalledException(
                        "Activity " + r.intent.getComponent().toShortString() +
                        " did not call through to super.onDestroy()");
                }
                if (r.window != null) {
                    r.window.closeAllPanels();
                }
            } catch (SuperNotCalledException e) {
                throw e;
            } catch (Exception e) {
                if (!mInstrumentation.onException(r.activity, e)) {
                    throw new RuntimeException(
                            "Unable to destroy activity "
                            + r.intent.getComponent().toShortString()
                            + ": " + e.toString(), e);
                }
            }
        }
        mActivities.remove(token);

        return r;
    }

    private final void handleDestroyActivity(IBinder token, boolean finishing,
            int configChanges, boolean getNonConfigInstance) {
        ActivityRecord r = performDestroyActivity(token, finishing,
                configChanges, getNonConfigInstance);
        if (r != null) {
            WindowManager wm = r.activity.getWindowManager();
            View v = r.activity.mDecor;
            if (v != null) {
                if (v.getVisibility() == View.VISIBLE) {
                    mNumVisibleActivities--;
                }
                IBinder wtoken = v.getWindowToken();
                wm.removeViewImmediate(v);
                if (wtoken != null) {
                    WindowManagerImpl.getDefault().closeAll(wtoken,
                            r.activity.getClass().getName(), "Activity");
                }
                r.activity.mDecor = null;
            }
            WindowManagerImpl.getDefault().closeAll(token,
                    r.activity.getClass().getName(), "Activity");

            // Mocked out contexts won't be participating in the normal
            // process lifecycle, but if we're running with a proper
            // ApplicationContext we need to have it tear down things
            // cleanly.
            Context c = r.activity.getBaseContext();
            if (c instanceof ApplicationContext) {
                ((ApplicationContext) c).scheduleFinalCleanup(
                        r.activity.getClass().getName(), "Activity");
            }
        }
        if (finishing) {
            try {
                ActivityManagerNative.getDefault().activityDestroyed(token);
            } catch (RemoteException ex) {
                // If the system process has died, it's game over for everyone.
            }
        }
    }

    private final void handleRelaunchActivity(ActivityRecord tmp, int configChanges) {
        // If we are getting ready to gc after going to the background, well
        // we are back active so skip it.
        unscheduleGcIdler();

        Configuration changedConfig = null;
        
        // First: make sure we have the most recent configuration and most
        // recent version of the activity, or skip it if some previous call
        // had taken a more recent version.
        synchronized (mRelaunchingActivities) {
            int N = mRelaunchingActivities.size();
            IBinder token = tmp.token;
            tmp = null;
            for (int i=0; i<N; i++) {
                ActivityRecord r = mRelaunchingActivities.get(i);
                if (r.token == token) {
                    tmp = r;
                    mRelaunchingActivities.remove(i);
                    i--;
                    N--;
                }
            }
            
            if (tmp == null) {
                return;
            }
            
            if (mPendingConfiguration != null) {
                changedConfig = mPendingConfiguration;
                mPendingConfiguration = null;
            }
        }
        
        // If there was a pending configuration change, execute it first.
        if (changedConfig != null) {
            handleConfigurationChanged(changedConfig);
        }
        
        ActivityRecord r = mActivities.get(tmp.token);
        if (localLOGV) Log.v(TAG, "Handling relaunch of " + r);
        if (r == null) {
            return;
        }
        
        r.activity.mConfigChangeFlags |= configChanges;
        
        Bundle savedState = null;
        if (!r.paused) {
            savedState = performPauseActivity(r.token, false, true);
        }
        
        handleDestroyActivity(r.token, false, configChanges, true);
        
        r.activity = null;
        r.window = null;
        r.hideForNow = false;
        r.nextIdle = null;
        r.pendingResults = tmp.pendingResults;
        r.pendingIntents = tmp.pendingIntents;
        r.startsNotResumed = tmp.startsNotResumed;
        if (savedState != null) {
            r.state = savedState;
        }
        
        handleLaunchActivity(r);
    }

    private final void handleRequestThumbnail(IBinder token) {
        ActivityRecord r = mActivities.get(token);
        Bitmap thumbnail = createThumbnailBitmap(r);
        CharSequence description = null;
        try {
            description = r.activity.onCreateDescription();
        } catch (Exception e) {
            if (!mInstrumentation.onException(r.activity, e)) {
                throw new RuntimeException(
                        "Unable to create description of activity "
                        + r.intent.getComponent().toShortString()
                        + ": " + e.toString(), e);
            }
        }
        //System.out.println("Reporting top thumbnail " + thumbnail);
        try {
            ActivityManagerNative.getDefault().reportThumbnail(
                token, thumbnail, description);
        } catch (RemoteException ex) {
        }
    }

    ArrayList<ComponentCallbacks> collectComponentCallbacksLocked(
            boolean allActivities, Configuration newConfig) {
        ArrayList<ComponentCallbacks> callbacks
                = new ArrayList<ComponentCallbacks>();
        
        if (mActivities.size() > 0) {
            Iterator<ActivityRecord> it = mActivities.values().iterator();
            while (it.hasNext()) {
                ActivityRecord ar = it.next();
                Activity a = ar.activity;
                if (a != null) {
                    if (!ar.activity.mFinished && (allActivities ||
                            (a != null && !ar.paused))) {
                        // If the activity is currently resumed, its configuration
                        // needs to change right now.
                        callbacks.add(a);
                    } else if (newConfig != null) {
                        // Otherwise, we will tell it about the change
                        // the next time it is resumed or shown.  Note that
                        // the activity manager may, before then, decide the
                        // activity needs to be destroyed to handle its new
                        // configuration.
                        ar.newConfig = newConfig;
                    }
                }
            }
        }
        if (mServices.size() > 0) {
            Iterator<Service> it = mServices.values().iterator();
            while (it.hasNext()) {
                callbacks.add(it.next());
            }
        }
        synchronized (mProviderMap) {
            if (mLocalProviders.size() > 0) {
                Iterator<ProviderRecord> it = mLocalProviders.values().iterator();
                while (it.hasNext()) {
                    callbacks.add(it.next().mLocalProvider);
                }
            }
        }
        final int N = mAllApplications.size();
        for (int i=0; i<N; i++) {
            callbacks.add(mAllApplications.get(i));
        }
        
        return callbacks;
    }
    
    private final void performConfigurationChanged(
            ComponentCallbacks cb, Configuration config) {
        // Only for Activity objects, check that they actually call up to their
        // superclass implementation.  ComponentCallbacks is an interface, so
        // we check the runtime type and act accordingly.
        Activity activity = (cb instanceof Activity) ? (Activity) cb : null;
        if (activity != null) {
            activity.mCalled = false;
        }
        
        boolean shouldChangeConfig = false;
        if ((activity == null) || (activity.mCurrentConfig == null)) {
            shouldChangeConfig = true;
        } else {
            
            // If the new config is the same as the config this Activity
            // is already running with then don't bother calling
            // onConfigurationChanged
            int diff = activity.mCurrentConfig.diff(config);
            if (diff != 0) {
                
                // If this activity doesn't handle any of the config changes
                // then don't bother calling onConfigurationChanged as we're
                // going to destroy it.
                if ((~activity.mActivityInfo.configChanges & diff) == 0) {
                    shouldChangeConfig = true;
                }
            }
        }
        
        if (shouldChangeConfig) {
            cb.onConfigurationChanged(config);
            
            if (activity != null) {
                if (!activity.mCalled) {
                    throw new SuperNotCalledException(
                            "Activity " + activity.getLocalClassName() +
                        " did not call through to super.onConfigurationChanged()");
                }
                activity.mConfigChangeFlags = 0;
                activity.mCurrentConfig = new Configuration(config);
            }
        }
    }

    final void handleConfigurationChanged(Configuration config) {
        
        synchronized (mRelaunchingActivities) {
            if (mPendingConfiguration != null) {
                config = mPendingConfiguration;
                mPendingConfiguration = null;
            }
        }
        
        ArrayList<ComponentCallbacks> callbacks
                = new ArrayList<ComponentCallbacks>();
        
        synchronized(mPackages) {
            if (mConfiguration == null) {
                mConfiguration = new Configuration();
            }
            mConfiguration.updateFrom(config);

            // set it for java, this also affects newly created Resources
            if (config.locale != null) {
                Locale.setDefault(config.locale);
            }

            if (mSystemContext != null) {
                mSystemContext.getResources().updateConfiguration(config, null);
                //Log.i(TAG, "Updated system resources " + mSystemContext.getResources()
                //        + ": " + mSystemContext.getResources().getConfiguration());
            }

            ApplicationContext.ApplicationPackageManager.configurationChanged();
            //Log.i(TAG, "Configuration changed in " + currentPackageName());
            {
                Iterator<WeakReference<Resources>> it =
                    mActiveResources.values().iterator();
                //Iterator<Map.Entry<String, WeakReference<Resources>>> it =
                //    mActiveResources.entrySet().iterator();
                while (it.hasNext()) {
                    WeakReference<Resources> v = it.next();
                    Resources r = v.get();
                    if (r != null) {
                        r.updateConfiguration(config, null);
                        //Log.i(TAG, "Updated app resources " + v.getKey()
                        //        + " " + r + ": " + r.getConfiguration());
                    } else {
                        //Log.i(TAG, "Removing old resources " + v.getKey());
                        it.remove();
                    }
                }
            }
            
            callbacks = collectComponentCallbacksLocked(false, config);
        }
        
        final int N = callbacks.size();
        for (int i=0; i<N; i++) {
            performConfigurationChanged(callbacks.get(i), config);
        }
    }

    final void handleActivityConfigurationChanged(IBinder token) {
        ActivityRecord r = mActivities.get(token);
        if (r == null || r.activity == null) {
            return;
        }
        
        performConfigurationChanged(r.activity, mConfiguration);
    }

    final void handleLowMemory() {
        ArrayList<ComponentCallbacks> callbacks
                = new ArrayList<ComponentCallbacks>();

        synchronized(mPackages) {
            callbacks = collectComponentCallbacksLocked(true, null);
        }
        
        final int N = callbacks.size();
        for (int i=0; i<N; i++) {
            callbacks.get(i).onLowMemory();
        }

        // Ask SQLite to free up as much memory as it can, mostly from it's page caches
        int sqliteReleased = SQLiteDatabase.releaseMemory();
        EventLog.writeEvent(SQLITE_MEM_RELEASED_EVENT_LOG_TAG, sqliteReleased);

        BinderInternal.forceGc("mem");
    }

    private final void handleBindApplication(AppBindData data) {
        mBoundApplication = data;
        mConfiguration = new Configuration(data.config);

        // We now rely on this being set by zygote.
        //Process.setGid(data.appInfo.gid);
        //Process.setUid(data.appInfo.uid);

        // send up app name; do this *before* waiting for debugger
        android.ddm.DdmHandleAppName.setAppName(data.processName);

        /*
         * Before spawning a new process, reset the time zone to be the system time zone.
         * This needs to be done because the system time zone could have changed after the
         * the spawning of this process. Without doing this this process would have the incorrect
         * system time zone.
         */
        TimeZone.setDefault(null);

        /*
         * Initialize the default locale in this process for the reasons we set the time zone.
         */
        Locale.setDefault(data.config.locale);

        data.info = getPackageInfoNoCheck(data.appInfo);

        if (data.debugMode != IApplicationThread.DEBUG_OFF) {
            // XXX should have option to change the port.
            Debug.changeDebugPort(8100);
            if (data.debugMode == IApplicationThread.DEBUG_WAIT) {
                Log.w(TAG, "Application " + data.info.getPackageName()
                      + " is waiting for the debugger on port 8100...");

                IActivityManager mgr = ActivityManagerNative.getDefault();
                try {
                    mgr.showWaitingForDebugger(mAppThread, true);
                } catch (RemoteException ex) {
                }

                Debug.waitForDebugger();

                try {
                    mgr.showWaitingForDebugger(mAppThread, false);
                } catch (RemoteException ex) {
                }

            } else {
                Log.w(TAG, "Application " + data.info.getPackageName()
                      + " can be debugged on port 8100...");
            }
        }

        if (data.instrumentationName != null) {
            ApplicationContext appContext = new ApplicationContext();
            appContext.init(data.info, null, this);
            InstrumentationInfo ii = null;
            try {
                ii = appContext.getPackageManager().
                    getInstrumentationInfo(data.instrumentationName, 0);
            } catch (PackageManager.NameNotFoundException e) {
            }
            if (ii == null) {
                throw new RuntimeException(
                    "Unable to find instrumentation info for: "
                    + data.instrumentationName);
            }

            mInstrumentationAppDir = ii.sourceDir;
            mInstrumentationAppPackage = ii.packageName;
            mInstrumentedAppDir = data.info.getAppDir();

            ApplicationInfo instrApp = new ApplicationInfo();
            instrApp.packageName = ii.packageName;
            instrApp.sourceDir = ii.sourceDir;
            instrApp.publicSourceDir = ii.publicSourceDir;
            instrApp.dataDir = ii.dataDir;
            PackageInfo pi = getPackageInfo(instrApp,
                    appContext.getClassLoader(), false, true);
            ApplicationContext instrContext = new ApplicationContext();
            instrContext.init(pi, null, this);

            try {
                java.lang.ClassLoader cl = instrContext.getClassLoader();
                mInstrumentation = (Instrumentation)
                    cl.loadClass(data.instrumentationName.getClassName()).newInstance();
            } catch (Exception e) {
                throw new RuntimeException(
                    "Unable to instantiate instrumentation "
                    + data.instrumentationName + ": " + e.toString(), e);
            }

            mInstrumentation.init(this, instrContext, appContext,
                    new ComponentName(ii.packageName, ii.name), data.instrumentationWatcher);

            if (data.profileFile != null && !ii.handleProfiling) {
                data.handlingProfiling = true;
                File file = new File(data.profileFile);
                file.getParentFile().mkdirs();
                Debug.startMethodTracing(file.toString(), 8 * 1024 * 1024);
            }

            try {
                mInstrumentation.onCreate(data.instrumentationArgs);
            }
            catch (Exception e) {
                throw new RuntimeException(
                    "Exception thrown in onCreate() of "
                    + data.instrumentationName + ": " + e.toString(), e);
            }

        } else {
            mInstrumentation = new Instrumentation();
        }

        Application app = data.info.makeApplication();
        mInitialApplication = app;

        List<ProviderInfo> providers = data.providers;
        if (providers != null) {
            installContentProviders(app, providers);
        }

        try {
            mInstrumentation.callApplicationOnCreate(app);
        } catch (Exception e) {
            if (!mInstrumentation.onException(app, e)) {
                throw new RuntimeException(
                    "Unable to create application " + app.getClass().getName()
                    + ": " + e.toString(), e);
            }
        }
    }

    /*package*/ final void finishInstrumentation(int resultCode, Bundle results) {
        IActivityManager am = ActivityManagerNative.getDefault();
        if (mBoundApplication.profileFile != null && mBoundApplication.handlingProfiling) {
            Debug.stopMethodTracing();
        }
        //Log.i(TAG, "am: " + ActivityManagerNative.getDefault()
        //      + ", app thr: " + mAppThread);
        try {
            am.finishInstrumentation(mAppThread, resultCode, results);
        } catch (RemoteException ex) {
        }
    }

    private final void installContentProviders(
            Context context, List<ProviderInfo> providers) {
        final ArrayList<IActivityManager.ContentProviderHolder> results =
            new ArrayList<IActivityManager.ContentProviderHolder>();

        Iterator<ProviderInfo> i = providers.iterator();
        while (i.hasNext()) {
            ProviderInfo cpi = i.next();
            StringBuilder buf = new StringBuilder(128);
            buf.append("Publishing provider ");
            buf.append(cpi.authority);
            buf.append(": ");
            buf.append(cpi.name);
            Log.i(TAG, buf.toString());
            IContentProvider cp = installProvider(context, null, cpi, false);
            if (cp != null) {
                IActivityManager.ContentProviderHolder cph =
                    new IActivityManager.ContentProviderHolder(cpi);
                cph.provider = cp;
                results.add(cph);
                // Don't ever unload this provider from the process.
                synchronized(mProviderMap) {
                    mProviderRefCountMap.put(cp.asBinder(), new ProviderRefCount(10000));
                }
            }
        }

        try {
            ActivityManagerNative.getDefault().publishContentProviders(
                getApplicationThread(), results);
        } catch (RemoteException ex) {
        }
    }

    private final IContentProvider getProvider(Context context, String name) {
        synchronized(mProviderMap) {
            final ProviderRecord pr = mProviderMap.get(name);
            if (pr != null) {
                return pr.mProvider;
            }
        }

        IActivityManager.ContentProviderHolder holder = null;
        try {
            holder = ActivityManagerNative.getDefault().getContentProvider(
                getApplicationThread(), name);
        } catch (RemoteException ex) {
        }
        if (holder == null) {
            Log.e(TAG, "Failed to find provider info for " + name);
            return null;
        }
        if (holder.permissionFailure != null) {
            throw new SecurityException("Permission " + holder.permissionFailure
                    + " required for provider " + name);
        }

        IContentProvider prov = installProvider(context, holder.provider,
                holder.info, true);
        //Log.i(TAG, "noReleaseNeeded=" + holder.noReleaseNeeded);
        if (holder.noReleaseNeeded || holder.provider == null) {
            // We are not going to release the provider if it is an external
            // provider that doesn't care about being released, or if it is
            // a local provider running in this process.
            //Log.i(TAG, "*** NO RELEASE NEEDED");
            synchronized(mProviderMap) {
                mProviderRefCountMap.put(prov.asBinder(), new ProviderRefCount(10000));
            }
        }
        return prov;
    }

    public final IContentProvider acquireProvider(Context c, String name) {
        IContentProvider provider = getProvider(c, name);
        if(provider == null)
            return null;
        IBinder jBinder = provider.asBinder();
        synchronized(mProviderMap) {
            ProviderRefCount prc = mProviderRefCountMap.get(jBinder);
            if(prc == null) {
                mProviderRefCountMap.put(jBinder, new ProviderRefCount(1));
            } else {
                prc.count++;
            } //end else
        } //end synchronized
        return provider;
    }

    public final boolean releaseProvider(IContentProvider provider) {
        if(provider == null) {
            return false;
        }
        IBinder jBinder = provider.asBinder();
        synchronized(mProviderMap) {
            ProviderRefCount prc = mProviderRefCountMap.get(jBinder);
            if(prc == null) {
                if(localLOGV) Log.v(TAG, "releaseProvider::Weird shouldnt be here");
                return false;
            } else {
                prc.count--;
                if(prc.count == 0) {
                    mProviderRefCountMap.remove(jBinder);
                    //invoke removeProvider to dereference provider
                    removeProviderLocked(provider);
                } //end if
            } //end else
        } //end synchronized
        return true;
    }

    public final void removeProviderLocked(IContentProvider provider) {
        if (provider == null) {
            return;
        }
        IBinder providerBinder = provider.asBinder();
        boolean amRemoveFlag = false;

        // remove the provider from mProviderMap
        Iterator<ProviderRecord> iter = mProviderMap.values().iterator();
        while (iter.hasNext()) {
            ProviderRecord pr = iter.next();
            IBinder myBinder = pr.mProvider.asBinder();
            if (myBinder == providerBinder) {
                //find if its published by this process itself
                if(pr.mLocalProvider != null) {
                    if(localLOGV) Log.i(TAG, "removeProvider::found local provider returning");
                    return;
                }
                if(localLOGV) Log.v(TAG, "removeProvider::Not local provider Unlinking " +
                        "death recipient");
                //content provider is in another process
                myBinder.unlinkToDeath(pr, 0);
                iter.remove();
                //invoke remove only once for the very first name seen
                if(!amRemoveFlag) {
                    try {
                        if(localLOGV) Log.v(TAG, "removeProvider::Invoking " +
                                "ActivityManagerNative.removeContentProvider("+pr.mName);
                        ActivityManagerNative.getDefault().removeContentProvider(getApplicationThread(), pr.mName);
                        amRemoveFlag = true;
                    } catch (RemoteException e) {
                        //do nothing content provider object is dead any way
                    } //end catch
                }
            } //end if myBinder
        }  //end while iter
    }

    final void removeDeadProvider(String name, IContentProvider provider) {
        synchronized(mProviderMap) {
            ProviderRecord pr = mProviderMap.get(name);
            if (pr.mProvider.asBinder() == provider.asBinder()) {
                Log.i(TAG, "Removing dead content provider: " + name);
                mProviderMap.remove(name);
            }
        }
    }

    final void removeDeadProviderLocked(String name, IContentProvider provider) {
        ProviderRecord pr = mProviderMap.get(name);
        if (pr.mProvider.asBinder() == provider.asBinder()) {
            Log.i(TAG, "Removing dead content provider: " + name);
            mProviderMap.remove(name);
        }
    }

    private final IContentProvider installProvider(Context context,
            IContentProvider provider, ProviderInfo info, boolean noisy) {
        ContentProvider localProvider = null;
        if (provider == null) {
            if (noisy) {
                Log.d(TAG, "Loading provider " + info.authority + ": "
                        + info.name);
            }
            Context c = null;
            ApplicationInfo ai = info.applicationInfo;
            if (context.getPackageName().equals(ai.packageName)) {
                c = context;
            } else if (mInitialApplication != null &&
                    mInitialApplication.getPackageName().equals(ai.packageName)) {
                c = mInitialApplication;
            } else {
                try {
                    c = context.createPackageContext(ai.packageName,
                            Context.CONTEXT_INCLUDE_CODE);
                } catch (PackageManager.NameNotFoundException e) {
                }
            }
            if (c == null) {
                Log.w(TAG, "Unable to get context for package " +
                      ai.packageName +
                      " while loading content provider " +
                      info.name);
                return null;
            }
            try {
                final java.lang.ClassLoader cl = c.getClassLoader();
                localProvider = (ContentProvider)cl.
                    loadClass(info.name).newInstance();
                provider = localProvider.getIContentProvider();
                if (provider == null) {
                    Log.e(TAG, "Failed to instantiate class " +
                          info.name + " from sourceDir " +
                          info.applicationInfo.sourceDir);
                    return null;
                }
                if (Config.LOGV) Log.v(
                    TAG, "Instantiating local provider " + info.name);
                // XXX Need to create the correct context for this provider.
                localProvider.attachInfo(c, info);
            } catch (java.lang.Exception e) {
                if (!mInstrumentation.onException(null, e)) {
                    throw new RuntimeException(
                            "Unable to get provider " + info.name
                            + ": " + e.toString(), e);
                }
                return null;
            }
        } else if (localLOGV) {
            Log.v(TAG, "Installing external provider " + info.authority + ": "
                    + info.name);
        }

        synchronized (mProviderMap) {
            // Cache the pointer for the remote provider.
            String names[] = PATTERN_SEMICOLON.split(info.authority);
            for (int i=0; i<names.length; i++) {
                ProviderRecord pr = new ProviderRecord(names[i], provider,
                        localProvider);
                try {
                    provider.asBinder().linkToDeath(pr, 0);
                    mProviderMap.put(names[i], pr);
                } catch (RemoteException e) {
                    return null;
                }
            }
            if (localProvider != null) {
                mLocalProviders.put(provider.asBinder(),
                        new ProviderRecord(null, provider, localProvider));
            }
        }

        return provider;
    }

    private final void attach(boolean system) {
        sThreadLocal.set(this);
        mSystemThread = system;
        AndroidHttpClient.setThreadBlocked(true);
        if (!system) {
            android.ddm.DdmHandleAppName.setAppName("<pre-initialized>");
            RuntimeInit.setApplicationObject(mAppThread.asBinder());
            IActivityManager mgr = ActivityManagerNative.getDefault();
            try {
                mgr.attachApplication(mAppThread);
            } catch (RemoteException ex) {
            }
        } else {
            // Don't set application object here -- if the system crashes,
            // we can't display an alert, we just want to die die die.
            android.ddm.DdmHandleAppName.setAppName("system_process");
            try {
                mInstrumentation = new Instrumentation();
                ApplicationContext context = new ApplicationContext();
                context.init(getSystemContext().mPackageInfo, null, this);
                Application app = Instrumentation.newApplication(Application.class, context);
                mAllApplications.add(app);
                mInitialApplication = app;
                app.onCreate();
            } catch (Exception e) {
                throw new RuntimeException(
                        "Unable to instantiate Application():" + e.toString(), e);
            }
        }
    }

    private final void detach()
    {
        AndroidHttpClient.setThreadBlocked(false);
        sThreadLocal.set(null);
    }

    public static final ActivityThread systemMain() {
        ActivityThread thread = new ActivityThread();
        thread.attach(true);
        return thread;
    }

    public final void installSystemProviders(List providers) {
        if (providers != null) {
            installContentProviders(mInitialApplication,
                                    (List<ProviderInfo>)providers);
        }
    }

    public static final void main(String[] args) {
        Process.setArgV0("<pre-initialized>");

        Looper.prepareMainLooper();

        ActivityThread thread = new ActivityThread();
        thread.attach(false);

        Looper.loop();

        if (Process.supportsProcesses()) {
            throw new RuntimeException("Main thread loop unexpectedly exited");
        }

        thread.detach();
        String name;
        if (thread.mInitialApplication != null) name = thread.mInitialApplication.getPackageName();
        else name = "<unknown>";
        Log.i(TAG, "Main thread of " + name + " is now exiting");
    }
}
