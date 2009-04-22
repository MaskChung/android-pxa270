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

#include <CoreFoundation/CoreFoundation.h>

#include <IOKit/IOKitLib.h>
#include <IOKit/IOCFPlugIn.h>
#include <IOKit/usb/IOUSBLib.h>
#include <IOKit/IOMessage.h>
#include <mach/mach_port.h>

#include "sysdeps.h"

#include <stdio.h>

#define TRACE_TAG   TRACE_USB
#include "adb.h"

#define  DBG   D

typedef struct {
    int vid;
    int pid;
} VendorProduct;

#define kSupportedDeviceCount   4
VendorProduct kSupportedDevices[kSupportedDeviceCount] = {
    { VENDOR_ID_GOOGLE, PRODUCT_ID_SOONER },
    { VENDOR_ID_GOOGLE, PRODUCT_ID_SOONER_COMP },
    { VENDOR_ID_HTC, PRODUCT_ID_DREAM },
    { VENDOR_ID_HTC, PRODUCT_ID_DREAM_COMP },
};

static IONotificationPortRef    notificationPort = 0;
static io_iterator_t            notificationIterators[kSupportedDeviceCount];

struct usb_handle
{
    UInt8                     bulkIn;
    UInt8                     bulkOut;
    IOUSBInterfaceInterface   **interface;
    io_object_t               usbNotification;
    unsigned int              zero_mask;
};

static CFRunLoopRef currentRunLoop = 0;
static pthread_mutex_t start_lock;
static pthread_cond_t start_cond;


static void AndroidDeviceAdded(void *refCon, io_iterator_t iterator);
static void AndroidDeviceNotify(void *refCon, io_iterator_t iterator, natural_t messageType, void *messageArgument);
static usb_handle* FindDeviceInterface(IOUSBDeviceInterface **dev, UInt16 vendor, UInt16 product);

static int
InitUSB()
{
    CFMutableDictionaryRef  matchingDict;
    CFRunLoopSourceRef      runLoopSource;
    SInt32					vendor, product;
    int                     i;
    
    //* To set up asynchronous notifications, create a notification port and
    //* add its run loop event source to the program's run loop
    notificationPort = IONotificationPortCreate(kIOMasterPortDefault);
    runLoopSource = IONotificationPortGetRunLoopSource(notificationPort);
    CFRunLoopAddSource(CFRunLoopGetCurrent(), runLoopSource, kCFRunLoopDefaultMode);
    
    memset(notificationIterators, 0, sizeof(notificationIterators));

    //* loop through all supported vendor/product pairs
    for (i = 0; i < kSupportedDeviceCount; i++) {
        //* Create our matching dictionary to find the Android device
        //* IOServiceAddMatchingNotification consumes the reference, so we do not need to release this
        matchingDict = IOServiceMatching(kIOUSBDeviceClassName);
    
        if (!matchingDict) {
            DBG("ERR: Couldn't create USB matching dictionary.\n");
            return -1;
        }
    
        //* Set up two matching dictionaries, one for each product ID we support.
        //* This will cause the kernel to notify us only if the vendor and product IDs match.
        vendor = kSupportedDevices[i].vid;
        product = kSupportedDevices[i].pid;
      	CFDictionarySetValue(matchingDict, CFSTR(kUSBVendorID), CFNumberCreate(kCFAllocatorDefault, kCFNumberSInt32Type, &vendor));
      	CFDictionarySetValue(matchingDict, CFSTR(kUSBProductID), CFNumberCreate(kCFAllocatorDefault, kCFNumberSInt32Type, &product));
    
        //* Now set up two notifications: one to be called when a raw device
        //* is first matched by the I/O Kit and another to be called when the
        //* device is terminated.
        //* we need to do this with each matching dictionary.
        IOServiceAddMatchingNotification(
                notificationPort,
                kIOFirstMatchNotification,
                matchingDict,
                AndroidDeviceAdded,
                NULL,
                &notificationIterators[i]);
    
        //* Iterate over set of matching devices to access already-present devices
        //* and to arm the notification
        AndroidDeviceAdded(NULL, notificationIterators[i]);
    }

    return 0;
}

static void
AndroidDeviceAdded(void *refCon, io_iterator_t iterator)
{
    kern_return_t            kr;
    io_service_t             usbDevice;
    IOCFPlugInInterface      **plugInInterface = NULL;
    IOUSBDeviceInterface182  **dev = NULL;
    HRESULT                  result;
    SInt32                   score;
    UInt16                   vendor;
    UInt16                   product;
    UInt8                    serialIndex;
    char                     serial[256];

    while ((usbDevice = IOIteratorNext(iterator))) {
        //* Create an intermediate plugin
        kr = IOCreatePlugInInterfaceForService(usbDevice,
                                               kIOUSBDeviceUserClientTypeID,
                                               kIOCFPlugInInterfaceID,
                                               &plugInInterface, &score);

        if ((kIOReturnSuccess != kr) || (!plugInInterface)) {
            DBG("ERR: Unable to create a plug-in (%08x)\n", kr);
            goto continue1;
        }

        //* Now create the device interface
        result = (*plugInInterface)->QueryInterface(plugInInterface,
                CFUUIDGetUUIDBytes(kIOUSBDeviceInterfaceID), (LPVOID) &dev);

        if (result || !dev) {
            DBG("ERR: Couldn't create a device interface (%08x)\n", (int) result);
            goto continue2;
        }

        //* Check the device to see if it's ours
        kr = (*dev)->GetDeviceVendor(dev, &vendor);
        kr = (*dev)->GetDeviceProduct(dev, &product);
        kr = (*dev)->USBGetSerialNumberStringIndex(dev, &serialIndex);

        if (serialIndex > 0) {
            IOUSBDevRequest req;
            UInt16          buffer[256];

            req.bmRequestType = USBmakebmRequestType(kUSBIn, kUSBStandard, kUSBDevice);
            req.bRequest = kUSBRqGetDescriptor;
            req.wValue = (kUSBStringDesc << 8) | serialIndex;
            req.wIndex = 0;
            req.pData = buffer;
            req.wLength = sizeof(buffer);
            kr = (*dev)->DeviceRequest(dev, &req);

            if (kr == kIOReturnSuccess && req.wLenDone > 0) {
                int i, count;
                
                // skip first word, and copy the rest to the serial string, changing shorts to bytes.
                count = (req.wLenDone - 1) / 2;
                for (i = 0; i < count; i++)
                  serial[i] = buffer[i + 1];
                serial[i] = 0;
            }
        }

        usb_handle* handle = NULL;

        //* Open the device
        kr = (*dev)->USBDeviceOpen(dev);

        if (kr != kIOReturnSuccess) {
            DBG("ERR: Could not open device: %08x\n", kr);
            goto continue3;
        } else {
            //* Find an interface for the device
            handle = FindDeviceInterface((IOUSBDeviceInterface**)dev, vendor, product);
        }

        if (handle == NULL) {
            DBG("ERR: Could not find device interface: %08x\n", kr);
            (*dev)->USBDeviceClose(dev);
            goto continue3;
        }

        DBG("AndroidDeviceAdded calling register_usb_transport\n");
        register_usb_transport(handle, (serial[0] ? serial : NULL));

        // Register for an interest notification of this device being removed. Pass the reference to our
        // private data as the refCon for the notification.
        kr = IOServiceAddInterestNotification(notificationPort,
                usbDevice,
                kIOGeneralInterest,
                AndroidDeviceNotify,
                handle,
                &handle->usbNotification);
        if (kIOReturnSuccess != kr) {
            DBG("ERR: Unable to create interest notification (%08x)\n", kr);
        }

continue3:
        (void)(*dev)->Release(dev);
continue2:
        IODestroyPlugInInterface(plugInInterface);
continue1:
        IOObjectRelease(usbDevice);
    }
}

static void
AndroidDeviceNotify(void *refCon, io_service_t service, natural_t messageType, void *messageArgument)
{
    usb_handle *handle = (usb_handle *)refCon;

    if (messageType == kIOMessageServiceIsTerminated) {
        DBG("AndroidDeviceNotify\n");
        IOObjectRelease(handle->usbNotification);
        usb_kick(handle);
    }
}

static usb_handle*
FindDeviceInterface(IOUSBDeviceInterface **dev, UInt16 vendor, UInt16 product)
{
    usb_handle*                 handle = NULL;
    IOReturn                    kr;
    IOUSBFindInterfaceRequest   request;
    io_iterator_t               iterator;
    io_service_t                usbInterface;
    IOCFPlugInInterface         **plugInInterface;
    IOUSBInterfaceInterface     **interface = NULL;
    HRESULT                     result;
    SInt32                      score;
    UInt8  interfaceNumEndpoints, interfaceClass, interfaceSubClass, interfaceProtocol;
    UInt8  endpoint, configuration;

    //* Placing the constant KIOUSBFindInterfaceDontCare into the following
    //* fields of the IOUSBFindInterfaceRequest structure will allow us to
    //* find all of the interfaces
    request.bInterfaceClass = kIOUSBFindInterfaceDontCare;
    request.bInterfaceSubClass = kIOUSBFindInterfaceDontCare;
    request.bInterfaceProtocol = kIOUSBFindInterfaceDontCare;
    request.bAlternateSetting = kIOUSBFindInterfaceDontCare;

    //* SetConfiguration will kill an existing UMS connection, so let's not do this if not necessary.
    configuration = 0;
    (*dev)->GetConfiguration(dev, &configuration);
    if (configuration != 1)
        (*dev)->SetConfiguration(dev, 1);

    //* Get an iterator for the interfaces on the device
    kr = (*dev)->CreateInterfaceIterator(dev, &request, &iterator);

    if (kr != kIOReturnSuccess) {
        DBG("ERR: Couldn't create a device interface iterator: (%08x)\n", kr);
        return NULL;
    }

    while ((usbInterface = IOIteratorNext(iterator))) {
    //* Create an intermediate plugin
        kr = IOCreatePlugInInterfaceForService(
                usbInterface,
                kIOUSBInterfaceUserClientTypeID,
                kIOCFPlugInInterfaceID,
                &plugInInterface,
                &score);

        //* No longer need the usbInterface object now that we have the plugin
        (void) IOObjectRelease(usbInterface);

        if ((kr != kIOReturnSuccess) || (!plugInInterface)) {
            DBG("ERR: Unable to create plugin (%08x)\n", kr);
            break;
        }

        //* Now create the interface interface for the interface
        result = (*plugInInterface)->QueryInterface(
                plugInInterface,
                CFUUIDGetUUIDBytes(kIOUSBInterfaceInterfaceID),
                (LPVOID) &interface);

        //* No longer need the intermediate plugin
        (*plugInInterface)->Release(plugInInterface);

        if (result || !interface) {
            DBG("ERR: Couldn't create interface interface: (%08x)\n",
               (unsigned int) result);
            break;
        }

        //* Now open the interface.  This will cause the pipes associated with
        //* the endpoints in the interface descriptor to be instantiated
        kr = (*interface)->USBInterfaceOpen(interface);

        if (kr != kIOReturnSuccess)
        {
            DBG("ERR: Could not open interface: (%08x)\n", kr);
            (void) (*interface)->Release(interface);
            //* continue so we can try the next interface
            continue;
        }

        //* Get the number of endpoints associated with this interface
        kr = (*interface)->GetNumEndpoints(interface, &interfaceNumEndpoints);

        if (kr != kIOReturnSuccess) {
            DBG("ERR: Unable to get number of endpoints: (%08x)\n", kr);
            goto next_interface;
        }

        //* Get interface class, subclass and protocol
        if ((*interface)->GetInterfaceClass(interface, &interfaceClass) != kIOReturnSuccess ||
            (*interface)->GetInterfaceSubClass(interface, &interfaceSubClass) != kIOReturnSuccess ||
            (*interface)->GetInterfaceProtocol(interface, &interfaceProtocol) != kIOReturnSuccess)
        {
            DBG("ERR: Unable to get interface class, subclass and protocol\n");
            goto next_interface;
        }

        //* check to make sure interface class, subclass and protocol match ADB
        //* avoid opening mass storage endpoints
        if (is_adb_interface(vendor, product, interfaceClass, interfaceSubClass, interfaceProtocol)) {
            handle = calloc(1, sizeof(usb_handle));

            //* Iterate over the endpoints for this interface and find the first
            //* bulk in/out pipes available.  These will be our read/write pipes.
            for (endpoint = 0; endpoint <= interfaceNumEndpoints; endpoint++) {
                UInt8   transferType;
                UInt16  maxPacketSize;
                UInt8   interval;
                UInt8   number;
                UInt8   direction;

                kr = (*interface)->GetPipeProperties(interface, endpoint, &direction,
                        &number, &transferType, &maxPacketSize, &interval);

                if (kIOReturnSuccess == kr) {
                    if (kUSBBulk != transferType)
                        continue;

                    if (kUSBIn == direction)
                        handle->bulkIn = endpoint;

                    if (kUSBOut == direction)
                        handle->bulkOut = endpoint;

                    if (interfaceProtocol == 0x01) {
                        handle->zero_mask = maxPacketSize - 1;
                    }

                } else {
                    DBG("ERR: FindDeviceInterface - could not get pipe properties\n");
                }
            }

            handle->interface = interface;
            break;
        }

next_interface:
        (*interface)->USBInterfaceClose(interface);
        (*interface)->Release(interface);
    }

    return handle;
}


void* RunLoopThread(void* unused)
{
    int i;

    InitUSB();

    currentRunLoop = CFRunLoopGetCurrent();

    // Signal the parent that we are running
    adb_mutex_lock(&start_lock);
    adb_cond_signal(&start_cond);
    adb_mutex_unlock(&start_lock);

    CFRunLoopRun();
    currentRunLoop = 0;

    for (i = 0; i < kSupportedDeviceCount; i++) {
	    IOObjectRelease(notificationIterators[i]);
	}
    IONotificationPortDestroy(notificationPort);

    DBG("RunLoopThread done\n");
    return NULL;    
}


static int initialized = 0;
void usb_init()
{
    if (!initialized)
    {
        adb_thread_t    tid;

        adb_mutex_init(&start_lock, NULL);
        adb_cond_init(&start_cond, NULL);

        if(adb_thread_create(&tid, RunLoopThread, NULL))
            fatal_errno("cannot create input thread");

        // Wait for initialization to finish
        adb_mutex_lock(&start_lock);
        adb_cond_wait(&start_cond, &start_lock);
        adb_mutex_unlock(&start_lock);

        adb_mutex_destroy(&start_lock);
        adb_cond_destroy(&start_cond);

        initialized = 1;
    }
}

void usb_cleanup()
{
    DBG("usb_cleanup\n");
    close_usb_devices();
    if (currentRunLoop)
        CFRunLoopStop(currentRunLoop);
}

int usb_write(usb_handle *handle, const void *buf, int len)
{
    IOReturn    result;

    if (!len)
        return 0;

    if (!handle)
        return -1;

    if (NULL == handle->interface) {
        DBG("ERR: usb_write interface was null\n");
        return -1;
    }

    if (0 == handle->bulkOut) {
        DBG("ERR: bulkOut endpoint not assigned\n");
        return -1;
    }

    result =
        (*handle->interface)->WritePipe(
                              handle->interface, handle->bulkOut, (void *)buf, len);

    if ((result == 0) && (handle->zero_mask)) {
        /* we need 0-markers and our transfer */
        if(!(len & handle->zero_mask)) {
            result =
                (*handle->interface)->WritePipe(
                        handle->interface, handle->bulkOut, (void *)buf, 0);
        }
    }

    if (0 == result)
        return 0;

    DBG("ERR: usb_write failed with status %d\n", result);
    return -1;
}

int usb_read(usb_handle *handle, void *buf, int len)
{
    IOReturn result;
    UInt32  numBytes = len;

    if (!len) {
        return 0;
    }

    if (!handle) {
        return -1;
    }

    if (NULL == handle->interface) {
        DBG("ERR: usb_read interface was null\n");
        return -1;
    }

    if (0 == handle->bulkIn) {
        DBG("ERR: bulkIn endpoint not assigned\n");
        return -1;
    }

    result =
      (*handle->interface)->ReadPipe(handle->interface,
                                    handle->bulkIn, buf, &numBytes);

    if (0 == result)
        return 0;
    else {
        DBG("ERR: usb_read failed with status %d\n", result);
    }

    return -1;
}

int usb_close(usb_handle *handle)
{
    return 0;
}

void usb_kick(usb_handle *handle)
{
    /* release the interface */
    if (handle->interface)
    {
        (*handle->interface)->USBInterfaceClose(handle->interface);
        (*handle->interface)->Release(handle->interface);
        handle->interface = 0;
    }
}
