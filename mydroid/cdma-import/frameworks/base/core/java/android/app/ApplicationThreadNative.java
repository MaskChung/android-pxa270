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

import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.ProviderInfo;
import android.content.pm.ServiceInfo;
import android.content.res.Configuration;
import android.os.Binder;
import android.os.Bundle;
import android.os.RemoteException;
import android.os.IBinder;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;

import java.io.FileDescriptor;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** {@hide} */
public abstract class ApplicationThreadNative extends Binder
        implements IApplicationThread {
    /**
     * Cast a Binder object into an application thread interface, generating
     * a proxy if needed.
     */
    static public IApplicationThread asInterface(IBinder obj) {
        if (obj == null) {
            return null;
        }
        IApplicationThread in =
            (IApplicationThread)obj.queryLocalInterface(descriptor);
        if (in != null) {
            return in;
        }
        
        return new ApplicationThreadProxy(obj);
    }
    
    public ApplicationThreadNative() {
        attachInterface(this, descriptor);
    }
    
    @Override
    public boolean onTransact(int code, Parcel data, Parcel reply, int flags)
            throws RemoteException {
        switch (code) {
        case SCHEDULE_PAUSE_ACTIVITY_TRANSACTION:
        {
            data.enforceInterface(IApplicationThread.descriptor);
            IBinder b = data.readStrongBinder();
            boolean finished = data.readInt() != 0;
            int configChanges = data.readInt();
            schedulePauseActivity(b, finished, configChanges);
            return true;
        }

        case SCHEDULE_STOP_ACTIVITY_TRANSACTION:
        {
            data.enforceInterface(IApplicationThread.descriptor);
            IBinder b = data.readStrongBinder();
            boolean show = data.readInt() != 0;
            int configChanges = data.readInt();
            scheduleStopActivity(b, show, configChanges);
            return true;
        }
        
        case SCHEDULE_WINDOW_VISIBILITY_TRANSACTION:
        {
            data.enforceInterface(IApplicationThread.descriptor);
            IBinder b = data.readStrongBinder();
            boolean show = data.readInt() != 0;
            scheduleWindowVisibility(b, show);
            return true;
        }

        case SCHEDULE_RESUME_ACTIVITY_TRANSACTION:
        {
            data.enforceInterface(IApplicationThread.descriptor);
            IBinder b = data.readStrongBinder();
            scheduleResumeActivity(b);
            return true;
        }
        
        case SCHEDULE_SEND_RESULT_TRANSACTION:
        {
            data.enforceInterface(IApplicationThread.descriptor);
            IBinder b = data.readStrongBinder();
            List<ResultInfo> ri = data.createTypedArrayList(ResultInfo.CREATOR);
            scheduleSendResult(b, ri);
            return true;
        }
        
        case SCHEDULE_LAUNCH_ACTIVITY_TRANSACTION:
        {
            data.enforceInterface(IApplicationThread.descriptor);
            Intent intent = Intent.CREATOR.createFromParcel(data);
            IBinder b = data.readStrongBinder();
            ActivityInfo info = ActivityInfo.CREATOR.createFromParcel(data);
            Bundle state = data.readBundle();
            List<ResultInfo> ri = data.createTypedArrayList(ResultInfo.CREATOR);
            List<Intent> pi = data.createTypedArrayList(Intent.CREATOR);
            boolean notResumed = data.readInt() != 0;
            scheduleLaunchActivity(intent, b, info, state, ri, pi, notResumed);
            return true;
        }
        
        case SCHEDULE_RELAUNCH_ACTIVITY_TRANSACTION:
        {
            data.enforceInterface(IApplicationThread.descriptor);
            IBinder b = data.readStrongBinder();
            List<ResultInfo> ri = data.createTypedArrayList(ResultInfo.CREATOR);
            List<Intent> pi = data.createTypedArrayList(Intent.CREATOR);
            int configChanges = data.readInt();
            boolean notResumed = data.readInt() != 0;
            scheduleRelaunchActivity(b, ri, pi, configChanges, notResumed);
            return true;
        }
        
        case SCHEDULE_NEW_INTENT_TRANSACTION:
        {
            data.enforceInterface(IApplicationThread.descriptor);
            List<Intent> pi = data.createTypedArrayList(Intent.CREATOR);
            IBinder b = data.readStrongBinder();
            scheduleNewIntent(pi, b);
            return true;
        }

        case SCHEDULE_FINISH_ACTIVITY_TRANSACTION:
        {
            data.enforceInterface(IApplicationThread.descriptor);
            IBinder b = data.readStrongBinder();
            boolean finishing = data.readInt() != 0;
            int configChanges = data.readInt();
            scheduleDestroyActivity(b, finishing, configChanges);
            return true;
        }
        
        case SCHEDULE_RECEIVER_TRANSACTION:
        {
            data.enforceInterface(IApplicationThread.descriptor);
            Intent intent = Intent.CREATOR.createFromParcel(data);
            ActivityInfo info = ActivityInfo.CREATOR.createFromParcel(data);
            int resultCode = data.readInt();
            String resultData = data.readString();
            Bundle resultExtras = data.readBundle();
            boolean sync = data.readInt() != 0;
            scheduleReceiver(intent, info, resultCode, resultData,
                    resultExtras, sync);
            return true;
        }

        case SCHEDULE_CREATE_SERVICE_TRANSACTION: {
            data.enforceInterface(IApplicationThread.descriptor);
            IBinder token = data.readStrongBinder();
            ServiceInfo info = ServiceInfo.CREATOR.createFromParcel(data);
            scheduleCreateService(token, info);
            return true;
        }

        case SCHEDULE_BIND_SERVICE_TRANSACTION: {
            data.enforceInterface(IApplicationThread.descriptor);
            IBinder token = data.readStrongBinder();
            Intent intent = Intent.CREATOR.createFromParcel(data);
            boolean rebind = data.readInt() != 0;
            scheduleBindService(token, intent, rebind);
            return true;
        }

        case SCHEDULE_UNBIND_SERVICE_TRANSACTION: {
            data.enforceInterface(IApplicationThread.descriptor);
            IBinder token = data.readStrongBinder();
            Intent intent = Intent.CREATOR.createFromParcel(data);
            scheduleUnbindService(token, intent);
            return true;
        }

        case SCHEDULE_SERVICE_ARGS_TRANSACTION:
        {
            data.enforceInterface(IApplicationThread.descriptor);
            IBinder token = data.readStrongBinder();
            int startId = data.readInt();
            Intent args = Intent.CREATOR.createFromParcel(data);
            scheduleServiceArgs(token, startId, args);
            return true;
        }

        case SCHEDULE_STOP_SERVICE_TRANSACTION:
        {
            data.enforceInterface(IApplicationThread.descriptor);
            IBinder token = data.readStrongBinder();
            scheduleStopService(token);
            return true;
        }

        case BIND_APPLICATION_TRANSACTION:
        {
            data.enforceInterface(IApplicationThread.descriptor);
            String packageName = data.readString();
            ApplicationInfo info =
                ApplicationInfo.CREATOR.createFromParcel(data);
            List<ProviderInfo> providers =
                data.createTypedArrayList(ProviderInfo.CREATOR);
            ComponentName testName = (data.readInt() != 0)
                ? new ComponentName(data) : null;
            String profileName = data.readString();
            Bundle testArgs = data.readBundle();
            IBinder binder = data.readStrongBinder();
            IInstrumentationWatcher testWatcher = IInstrumentationWatcher.Stub.asInterface(binder);
            int testMode = data.readInt();
            Configuration config = Configuration.CREATOR.createFromParcel(data);
            HashMap<String, IBinder> services = data.readHashMap(null);
            bindApplication(packageName, info,
                            providers, testName, profileName,
                            testArgs, testWatcher, testMode, config, services);
            return true;
        }
        
        case SCHEDULE_EXIT_TRANSACTION:
        {
            data.enforceInterface(IApplicationThread.descriptor);
            scheduleExit();
            return true;
        }

        case REQUEST_THUMBNAIL_TRANSACTION:
        {
            data.enforceInterface(IApplicationThread.descriptor);
            IBinder b = data.readStrongBinder();
            requestThumbnail(b);
            return true;
        }
        
        case SCHEDULE_CONFIGURATION_CHANGED_TRANSACTION:
        {
            data.enforceInterface(IApplicationThread.descriptor);
            Configuration config = Configuration.CREATOR.createFromParcel(data);
            scheduleConfigurationChanged(config);
            return true;
        }

        case UPDATE_TIME_ZONE_TRANSACTION: {
            data.enforceInterface(IApplicationThread.descriptor);
            updateTimeZone();
            return true;
        }

        case PROCESS_IN_BACKGROUND_TRANSACTION: {
            data.enforceInterface(IApplicationThread.descriptor);
            processInBackground();
            return true;
        }
        
        case DUMP_SERVICE_TRANSACTION: {
            data.enforceInterface(IApplicationThread.descriptor);
            ParcelFileDescriptor fd = data.readFileDescriptor();
            final IBinder service = data.readStrongBinder();
            final String[] args = data.readStringArray();
            if (fd != null) {
                dumpService(fd.getFileDescriptor(), service, args);
                try {
                    fd.close();
                } catch (IOException e) {
                }
            }
            return true;
        }
        
        case SCHEDULE_REGISTERED_RECEIVER_TRANSACTION: {
            data.enforceInterface(IApplicationThread.descriptor);
            IIntentReceiver receiver = IIntentReceiver.Stub.asInterface(
                    data.readStrongBinder());
            Intent intent = Intent.CREATOR.createFromParcel(data);
            int resultCode = data.readInt();
            String dataStr = data.readString();
            Bundle extras = data.readBundle();
            boolean ordered = data.readInt() != 0;
            scheduleRegisteredReceiver(receiver, intent,
                    resultCode, dataStr, extras, ordered);
            return true;
        }

        case SCHEDULE_LOW_MEMORY_TRANSACTION:
        {
            scheduleLowMemory();
            return true;
        }
        
        case SCHEDULE_ACTIVITY_CONFIGURATION_CHANGED_TRANSACTION:
        {
            data.enforceInterface(IApplicationThread.descriptor);
            IBinder b = data.readStrongBinder();
            scheduleActivityConfigurationChanged(b);
            return true;
        }
        
        case REQUEST_PSS_TRANSACTION:
        {
            data.enforceInterface(IApplicationThread.descriptor);
            requestPss();
            return true;
        }
        }

        return super.onTransact(code, data, reply, flags);
    }

    public IBinder asBinder()
    {
        return this;
    }
}

class ApplicationThreadProxy implements IApplicationThread {
    private final IBinder mRemote;
    
    public ApplicationThreadProxy(IBinder remote) {
        mRemote = remote;
    }
    
    public final IBinder asBinder() {
        return mRemote;
    }
    
    public final void schedulePauseActivity(IBinder token, boolean finished,
            int configChanges) throws RemoteException {
        Parcel data = Parcel.obtain();
        data.writeInterfaceToken(IApplicationThread.descriptor);
        data.writeStrongBinder(token);
        data.writeInt(finished ? 1 : 0);
        data.writeInt(configChanges);
        mRemote.transact(SCHEDULE_PAUSE_ACTIVITY_TRANSACTION, data, null,
                IBinder.FLAG_ONEWAY);
        data.recycle();
    }

    public final void scheduleStopActivity(IBinder token, boolean showWindow,
            int configChanges) throws RemoteException {
        Parcel data = Parcel.obtain();
        data.writeInterfaceToken(IApplicationThread.descriptor);
        data.writeStrongBinder(token);
        data.writeInt(showWindow ? 1 : 0);
        data.writeInt(configChanges);
        mRemote.transact(SCHEDULE_STOP_ACTIVITY_TRANSACTION, data, null,
                IBinder.FLAG_ONEWAY);
        data.recycle();
    }

    public final void scheduleWindowVisibility(IBinder token,
            boolean showWindow) throws RemoteException {
        Parcel data = Parcel.obtain();
        data.writeInterfaceToken(IApplicationThread.descriptor);
        data.writeStrongBinder(token);
        data.writeInt(showWindow ? 1 : 0);
        mRemote.transact(SCHEDULE_WINDOW_VISIBILITY_TRANSACTION, data, null,
                IBinder.FLAG_ONEWAY);
        data.recycle();
    }

    public final void scheduleResumeActivity(IBinder token)
            throws RemoteException {
        Parcel data = Parcel.obtain();
        data.writeInterfaceToken(IApplicationThread.descriptor);
        data.writeStrongBinder(token);
        mRemote.transact(SCHEDULE_RESUME_ACTIVITY_TRANSACTION, data, null,
                IBinder.FLAG_ONEWAY);
        data.recycle();
    }

    public final void scheduleSendResult(IBinder token, List<ResultInfo> results)
    		throws RemoteException {
        Parcel data = Parcel.obtain();
        data.writeInterfaceToken(IApplicationThread.descriptor);
        data.writeStrongBinder(token);
        data.writeTypedList(results);
        mRemote.transact(SCHEDULE_SEND_RESULT_TRANSACTION, data, null,
                IBinder.FLAG_ONEWAY);
        data.recycle();
    }

    public final void scheduleLaunchActivity(Intent intent, IBinder token,
            ActivityInfo info, Bundle state, List<ResultInfo> pendingResults,
    		List<Intent> pendingNewIntents, boolean notResumed)
    		throws RemoteException {
        Parcel data = Parcel.obtain();
        data.writeInterfaceToken(IApplicationThread.descriptor);
        intent.writeToParcel(data, 0);
        data.writeStrongBinder(token);
        info.writeToParcel(data, 0);
        data.writeBundle(state);
        data.writeTypedList(pendingResults);
        data.writeTypedList(pendingNewIntents);
        data.writeInt(notResumed ? 1 : 0);
        mRemote.transact(SCHEDULE_LAUNCH_ACTIVITY_TRANSACTION, data, null,
                IBinder.FLAG_ONEWAY);
        data.recycle();
    }

    public final void scheduleRelaunchActivity(IBinder token,
            List<ResultInfo> pendingResults, List<Intent> pendingNewIntents,
            int configChanges, boolean notResumed) throws RemoteException {
        Parcel data = Parcel.obtain();
        data.writeInterfaceToken(IApplicationThread.descriptor);
        data.writeStrongBinder(token);
        data.writeTypedList(pendingResults);
        data.writeTypedList(pendingNewIntents);
        data.writeInt(configChanges);
        data.writeInt(notResumed ? 1 : 0);
        mRemote.transact(SCHEDULE_RELAUNCH_ACTIVITY_TRANSACTION, data, null,
                IBinder.FLAG_ONEWAY);
        data.recycle();
    }

    public void scheduleNewIntent(List<Intent> intents, IBinder token)
            throws RemoteException {
        Parcel data = Parcel.obtain();
        data.writeInterfaceToken(IApplicationThread.descriptor);
        data.writeTypedList(intents);
        data.writeStrongBinder(token);
        mRemote.transact(SCHEDULE_NEW_INTENT_TRANSACTION, data, null,
                IBinder.FLAG_ONEWAY);
        data.recycle();
    }

    public final void scheduleDestroyActivity(IBinder token, boolean finishing,
            int configChanges) throws RemoteException {
        Parcel data = Parcel.obtain();
        data.writeInterfaceToken(IApplicationThread.descriptor);
        data.writeStrongBinder(token);
        data.writeInt(finishing ? 1 : 0);
        data.writeInt(configChanges);
        mRemote.transact(SCHEDULE_FINISH_ACTIVITY_TRANSACTION, data, null,
                IBinder.FLAG_ONEWAY);
        data.recycle();
    }
    
    public final void scheduleReceiver(Intent intent, ActivityInfo info,
            int resultCode, String resultData,
            Bundle map, boolean sync) throws RemoteException {
        Parcel data = Parcel.obtain();
        data.writeInterfaceToken(IApplicationThread.descriptor);
        intent.writeToParcel(data, 0);
        info.writeToParcel(data, 0);
        data.writeInt(resultCode);
        data.writeString(resultData);
        data.writeBundle(map);
        data.writeInt(sync ? 1 : 0);
        mRemote.transact(SCHEDULE_RECEIVER_TRANSACTION, data, null,
                IBinder.FLAG_ONEWAY);
        data.recycle();
    }

    public final void scheduleCreateService(IBinder token, ServiceInfo info)
            throws RemoteException {
        Parcel data = Parcel.obtain();
        data.writeInterfaceToken(IApplicationThread.descriptor);
        data.writeStrongBinder(token);
        info.writeToParcel(data, 0);
        mRemote.transact(SCHEDULE_CREATE_SERVICE_TRANSACTION, data, null,
                IBinder.FLAG_ONEWAY);
        data.recycle();
    }

    public final void scheduleBindService(IBinder token, Intent intent, boolean rebind)
            throws RemoteException {
        Parcel data = Parcel.obtain();
        data.writeInterfaceToken(IApplicationThread.descriptor);
        data.writeStrongBinder(token);
        intent.writeToParcel(data, 0);
        data.writeInt(rebind ? 1 : 0);
        mRemote.transact(SCHEDULE_BIND_SERVICE_TRANSACTION, data, null,
                IBinder.FLAG_ONEWAY);
        data.recycle();
    }

    public final void scheduleUnbindService(IBinder token, Intent intent)
            throws RemoteException {
        Parcel data = Parcel.obtain();
        data.writeInterfaceToken(IApplicationThread.descriptor);
        data.writeStrongBinder(token);
        intent.writeToParcel(data, 0);
        mRemote.transact(SCHEDULE_UNBIND_SERVICE_TRANSACTION, data, null,
                IBinder.FLAG_ONEWAY);
        data.recycle();
    }

    public final void scheduleServiceArgs(IBinder token, int startId,
	    Intent args) throws RemoteException {
        Parcel data = Parcel.obtain();
        data.writeInterfaceToken(IApplicationThread.descriptor);
        data.writeStrongBinder(token);
        data.writeInt(startId);
        args.writeToParcel(data, 0);
        mRemote.transact(SCHEDULE_SERVICE_ARGS_TRANSACTION, data, null,
                IBinder.FLAG_ONEWAY);
        data.recycle();
    }

    public final void scheduleStopService(IBinder token)
            throws RemoteException {
        Parcel data = Parcel.obtain();
        data.writeInterfaceToken(IApplicationThread.descriptor);
        data.writeStrongBinder(token);
        mRemote.transact(SCHEDULE_STOP_SERVICE_TRANSACTION, data, null,
                IBinder.FLAG_ONEWAY);
        data.recycle();
    }

    public final void bindApplication(String packageName, ApplicationInfo info,
            List<ProviderInfo> providers, ComponentName testName,
            String profileName, Bundle testArgs, IInstrumentationWatcher testWatcher, int debugMode,
            Configuration config, Map<String, IBinder> services) throws RemoteException {
        Parcel data = Parcel.obtain();
        data.writeInterfaceToken(IApplicationThread.descriptor);
        data.writeString(packageName);
        info.writeToParcel(data, 0);
        data.writeTypedList(providers);
        if (testName == null) {
            data.writeInt(0);
        } else {
            data.writeInt(1);
            testName.writeToParcel(data, 0);
        }
        data.writeString(profileName);
        data.writeBundle(testArgs);
        data.writeStrongInterface(testWatcher);
        data.writeInt(debugMode);
        config.writeToParcel(data, 0);
        data.writeMap(services);
        mRemote.transact(BIND_APPLICATION_TRANSACTION, data, null,
                IBinder.FLAG_ONEWAY);
        data.recycle();
    }
    
    public final void scheduleExit() throws RemoteException {
        Parcel data = Parcel.obtain();
        data.writeInterfaceToken(IApplicationThread.descriptor);
        mRemote.transact(SCHEDULE_EXIT_TRANSACTION, data, null,
                IBinder.FLAG_ONEWAY);
        data.recycle();
    }
    
    public final void requestThumbnail(IBinder token)
            throws RemoteException {
        Parcel data = Parcel.obtain();
        data.writeInterfaceToken(IApplicationThread.descriptor);
        data.writeStrongBinder(token);
        mRemote.transact(REQUEST_THUMBNAIL_TRANSACTION, data, null,
                IBinder.FLAG_ONEWAY);
        data.recycle();
    }

    public final void scheduleConfigurationChanged(Configuration config)
            throws RemoteException {
        Parcel data = Parcel.obtain();
        data.writeInterfaceToken(IApplicationThread.descriptor);
        config.writeToParcel(data, 0);
        mRemote.transact(SCHEDULE_CONFIGURATION_CHANGED_TRANSACTION, data, null,
                IBinder.FLAG_ONEWAY);
        data.recycle();
    }

    public void updateTimeZone() throws RemoteException {
        Parcel data = Parcel.obtain();
        data.writeInterfaceToken(IApplicationThread.descriptor);
        mRemote.transact(UPDATE_TIME_ZONE_TRANSACTION, data, null,
                IBinder.FLAG_ONEWAY);
        data.recycle();
    }

    public void processInBackground() throws RemoteException {
        Parcel data = Parcel.obtain();
        data.writeInterfaceToken(IApplicationThread.descriptor);
        mRemote.transact(PROCESS_IN_BACKGROUND_TRANSACTION, data, null,
                IBinder.FLAG_ONEWAY);
        data.recycle();
    }

    public void dumpService(FileDescriptor fd, IBinder token, String[] args)
            throws RemoteException {
        Parcel data = Parcel.obtain();
        data.writeInterfaceToken(IApplicationThread.descriptor);
        data.writeFileDescriptor(fd);
        data.writeStrongBinder(token);
        data.writeStringArray(args);
        mRemote.transact(DUMP_SERVICE_TRANSACTION, data, null, 0);
        data.recycle();
    }
    
    public void scheduleRegisteredReceiver(IIntentReceiver receiver, Intent intent,
            int resultCode, String dataStr, Bundle extras, boolean ordered)
            throws RemoteException {
        Parcel data = Parcel.obtain();
        data.writeInterfaceToken(IApplicationThread.descriptor);
        data.writeStrongBinder(receiver.asBinder());
        intent.writeToParcel(data, 0);
        data.writeInt(resultCode);
        data.writeString(dataStr);
        data.writeBundle(extras);
        data.writeInt(ordered ? 1 : 0);
        mRemote.transact(SCHEDULE_REGISTERED_RECEIVER_TRANSACTION, data, null,
                IBinder.FLAG_ONEWAY);
        data.recycle();
    }

    public final void scheduleLowMemory() throws RemoteException {
        Parcel data = Parcel.obtain();
        data.writeInterfaceToken(IApplicationThread.descriptor);
        mRemote.transact(SCHEDULE_LOW_MEMORY_TRANSACTION, data, null,
                IBinder.FLAG_ONEWAY);
        data.recycle();
    }
    
    public final void scheduleActivityConfigurationChanged(
            IBinder token) throws RemoteException {
        Parcel data = Parcel.obtain();
        data.writeInterfaceToken(IApplicationThread.descriptor);
        data.writeStrongBinder(token);
        mRemote.transact(SCHEDULE_ACTIVITY_CONFIGURATION_CHANGED_TRANSACTION, data, null,
                IBinder.FLAG_ONEWAY);
        data.recycle();
    }
    
    public final void requestPss() throws RemoteException {
        Parcel data = Parcel.obtain();
        data.writeInterfaceToken(IApplicationThread.descriptor);
        mRemote.transact(REQUEST_PSS_TRANSACTION, data, null,
                IBinder.FLAG_ONEWAY);
        data.recycle();
    }
    
}

