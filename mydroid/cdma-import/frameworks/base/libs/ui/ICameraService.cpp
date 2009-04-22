/*
**
** Copyright 2008, The Android Open Source Project
**
** Licensed under the Apache License, Version 2.0 (the "License"); 
** you may not use this file except in compliance with the License. 
** You may obtain a copy of the License at 
**
**     http://www.apache.org/licenses/LICENSE-2.0 
**
** Unless required by applicable law or agreed to in writing, software 
** distributed under the License is distributed on an "AS IS" BASIS, 
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
** See the License for the specific language governing permissions and 
** limitations under the License.
*/

#include <stdint.h>
#include <sys/types.h>

#include <utils/Parcel.h>
#include <utils/IPCThreadState.h>
#include <utils/IServiceManager.h>

#include <ui/ICameraService.h>

namespace android {

class BpCameraService: public BpInterface<ICameraService>
{
public:
    BpCameraService(const sp<IBinder>& impl)
        : BpInterface<ICameraService>(impl)
    {
    }

    // connect to camera service
    virtual sp<ICamera> connect(const sp<ICameraClient>& cameraClient)
    {
        Parcel data, reply;
        data.writeInterfaceToken(ICameraService::getInterfaceDescriptor());
        data.writeStrongBinder(cameraClient->asBinder());
        remote()->transact(BnCameraService::CONNECT, data, &reply);
        return interface_cast<ICamera>(reply.readStrongBinder());
    }
};

IMPLEMENT_META_INTERFACE(CameraService, "android.hardware.ICameraService");

// ----------------------------------------------------------------------

#define CHECK_INTERFACE(interface, data, reply) \
        do { if (!data.enforceInterface(interface::getInterfaceDescriptor())) { \
            LOGW("Call incorrectly routed to " #interface); \
            return PERMISSION_DENIED; \
        } } while (0)

status_t BnCameraService::onTransact(
    uint32_t code, const Parcel& data, Parcel* reply, uint32_t flags)
{
    switch(code) {
        case CONNECT: {
            CHECK_INTERFACE(ICameraService, data, reply);
            sp<ICameraClient> cameraClient = interface_cast<ICameraClient>(data.readStrongBinder());
            sp<ICamera> camera = connect(cameraClient);
            reply->writeStrongBinder(camera->asBinder());
            return NO_ERROR;
        } break;
        default:
            return BBinder::onTransact(code, data, reply, flags);
    }
}

// ----------------------------------------------------------------------------

}; // namespace android

