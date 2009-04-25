/*************************************************************************/
   //
   //                  P H I L I P S   P R O P R I E T A R Y
   //
   //           COPYRIGHT (c)   1999 BY PHILIPS SINGAPORE.
   //                     --  ALL RIGHTS RESERVED  --
   //
   // File Name:	USB100.H
   // Author:       Hilbert Zhang ZhenYu
   // Created:		Nov. 26 99
   // Modified:
   // Revision:		0.0
   //
/*************************************************************************/
   //
/*************************************************************************/

#ifndef   __USB100_H__
#define   __USB100_H__


#define MAXIMUM_USB_STRING_LENGTH 255

// values for the bits returned by the USB GET_STATUS command
#define USB_GETSTATUS_SELF_POWERED                0x01
#define USB_GETSTATUS_REMOTE_WAKEUP_ENABLED       0x02


#define USB_DEVICE_DESCRIPTOR_TYPE                0x01
#define USB_CONFIGURATION_DESCRIPTOR_TYPE         0x02
#define USB_STRING_DESCRIPTOR_TYPE                0x03
#define USB_INTERFACE_DESCRIPTOR_TYPE             0x04
#define USB_ENDPOINT_DESCRIPTOR_TYPE              0x05
#define USB_POWER_DESCRIPTOR_TYPE                 0x06

#define USB_DESCRIPTOR_MAKE_TYPE_AND_INDEX(d, i) ((INT16)((INT16)d<<8 | i))

//
// Values for bmAttributes field of an
// endpoint descriptor
//

#define USB_ENDPOINT_TYPE_MASK                    0x03

#define USB_ENDPOINT_TYPE_CONTROL                 0x00
#define USB_ENDPOINT_TYPE_ISOCHRONOUS             0x01
#define USB_ENDPOINT_TYPE_BULK                    0x02
#define USB_ENDPOINT_TYPE_INTERRUPT               0x03


//
// definitions for bits in the bmAttributes field of a 
// configuration descriptor.
//
#define USB_CONFIG_POWERED_MASK                   0xc0

#define USB_CONFIG_BUS_POWERED                    0x80
#define USB_CONFIG_SELF_POWERED                   0x40
#define USB_CONFIG_REMOTE_WAKEUP                  0x20

//
// Endpoint direction bit, stored in address
//

#define USB_ENDPOINT_DIRECTION_MASK               0x80

// test direction bit in the bEndpointAddress field of
// an endpoint descriptor.
#define USB_ENDPOINT_DIRECTION_OUT(addr)          (!((addr) & USB_ENDPOINT_DIRECTION_MASK))
#define USB_ENDPOINT_DIRECTION_IN(addr)           ((addr) & USB_ENDPOINT_DIRECTION_MASK)

//
// USB defined request codes
// see chapter 9 of the USB 1.0 specifcation for
// more information.
//

// These are the correct values based on the USB 1.0
// specification

#define USB_REQUEST_GET_STATUS                    0x00
#define USB_REQUEST_CLEAR_FEATURE                 0x01

#define USB_REQUEST_SET_FEATURE                   0x03

#define USB_REQUEST_SET_ADDRESS                   0x05
#define USB_REQUEST_GET_DESCRIPTOR                0x06
#define USB_REQUEST_SET_DESCRIPTOR                0x07
#define USB_REQUEST_GET_CONFIGURATION             0x08
#define USB_REQUEST_SET_CONFIGURATION             0x09
#define USB_REQUEST_GET_INTERFACE                 0x0A
#define USB_REQUEST_SET_INTERFACE                 0x0B
#define USB_REQUEST_SYNC_FRAME                    0x0C


//
// defined USB device classes
//


#define USB_DEVICE_CLASS_RESERVED           0x00
#define USB_DEVICE_CLASS_AUDIO              0x01
#define USB_DEVICE_CLASS_COMMUNICATIONS     0x02
#define USB_DEVICE_CLASS_HUMAN_INTERFACE    0x03
#define USB_DEVICE_CLASS_MONITOR            0x04
#define USB_DEVICE_CLASS_PHYSICAL_INTERFACE 0x05
#define USB_DEVICE_CLASS_POWER              0x06
#define USB_DEVICE_CLASS_PRINTER            0x07
#define USB_DEVICE_CLASS_STORAGE            0x08
#define USB_DEVICE_CLASS_HUB                0x09
#define USB_DEVICE_CLASS_VENDOR_SPECIFIC    0xFF

//
// USB defined Feature selectors
//

#define USB_FEATURE_ENDPOINT_STALL          0x0000
#define USB_FEATURE_REMOTE_WAKEUP           0x0001
#define USB_FEATURE_POWER_D0                0x0002
#define USB_FEATURE_POWER_D1                0x0003
#define USB_FEATURE_POWER_D2                0x0004
#define USB_FEATURE_POWER_D3                0x0005

typedef struct _USB_DEVICE_DESCRIPTOR {
    INT8 bLength;
    INT8 bDescriptorType;
    INT16 bcdUSB;
    INT8 bDeviceClass;
    INT8 bDeviceSubClass;
    INT8 bDeviceProtocol;
    INT8 bMaxPacketSize0;
    INT16 idVendor;
    INT16 idProduct;
    INT16 bcdDevice;
    INT8 iManufacturer;
    INT8 iProduct;
    INT8 iSerialNumber;
    INT8 bNumConfigurations;
} __attribute__ ((packed)) USB_DEVICE_DESCRIPTOR, *PUSB_DEVICE_DESCRIPTOR;

#define MAX_ENDPOINTS      0x16

typedef struct _USB_ENDPOINT_DESCRIPTOR {
    INT8 bLength;
    INT8 bDescriptorType;
    INT8 bEndpointAddress;
    INT8 bmAttributes;
    INT16 wMaxPacketSize;
    INT8 bInterval;
} __attribute__ ((packed))  USB_ENDPOINT_DESCRIPTOR, *PUSB_ENDPOINT_DESCRIPTOR;

//
// values for bmAttributes Field in
// USB_CONFIGURATION_DESCRIPTOR
//

#define BUS_POWERED                           0x80
#define SELF_POWERED                          0x40
#define REMOTE_WAKEUP                         0x20

typedef struct _USB_CONFIGURATION_DESCRIPTOR {
    INT8 bLength;
    INT8 bDescriptorType;
    INT16 wTotalLength;
    INT8 bNumInterfaces;
    INT8 bConfigurationValue;
    INT8 iConfiguration;
    INT8 bmAttributes;
    INT8 MaxPower;
} __attribute__ ((packed)) USB_CONFIGURATION_DESCRIPTOR, *PUSB_CONFIGURATION_DESCRIPTOR;

typedef struct _USB_INTERFACE_DESCRIPTOR {
    INT8 bLength;
    INT8 bDescriptorType;
    INT8 bInterfaceNumber;
    INT8 bAlternateSetting;
    INT8 bNumEndpoints;
    INT8 bInterfaceClass;
    INT8 bInterfaceSubClass;
    INT8 bInterfaceProtocol;
    INT8 iInterface;
} __attribute__ ((packed))  USB_INTERFACE_DESCRIPTOR, *PUSB_INTERFACE_DESCRIPTOR;

typedef struct _USB_STRING_DESCRIPTOR {
    INT8 bLength;
    INT8 bDescriptorType;
    INT8 bString[1];
} __attribute__ ((packed))  USB_STRING_DESCRIPTOR, *PUSB_STRING_DESCRIPTOR;

//
// USB power descriptor added to core specification
//

#define USB_SUPPORT_D0_COMMAND      0x01
#define USB_SUPPORT_D1_COMMAND      0x02
#define USB_SUPPORT_D2_COMMAND      0x04
#define USB_SUPPORT_D3_COMMAND      0x08

#define USB_SUPPORT_D1_WAKEUP       0x10
#define USB_SUPPORT_D2_WAKEUP       0x20


typedef struct _USB_POWER_DESCRIPTOR {
    INT8 bLength;
    INT8 bDescriptorType;
    INT8 bCapabilitiesFlags;
    INT16 EventNotification;
    INT16 D1LatencyTime;
    INT16 D2LatencyTime;
    INT16 D3LatencyTime;
    INT8 PowerUnit;
    INT16 D0PowerConsumption;
    INT16 D1PowerConsumption;
    INT16 D2PowerConsumption;
} __attribute__ ((packed))  USB_POWER_DESCRIPTOR, *PUSB_POWER_DESCRIPTOR;


typedef struct _USB_COMMON_DESCRIPTOR {
    INT8 bLength;
    INT8 bDescriptorType;
} __attribute__ ((packed))  USB_COMMON_DESCRIPTOR, *PUSB_COMMON_DESCRIPTOR;


//
// Standard USB HUB definitions 
//
// See Chapter 11
//

typedef struct _USB_HUB_DESCRIPTOR {
    INT8        bDescriptorLength;      // Length of this descriptor
    INT8        bDescriptorType;        // Hub configuration type
    INT8        bNumberOfPorts;         // number of ports on this hub
    INT16       wHubCharacteristics;    // Hub Charateristics
    INT8        bPowerOnToPowerGood;    // port power on till power good in 2ms
    INT8        bHubControlCurrent;     // max current in mA
    //
    // room for 255 ports power control and removable bitmask
    INT8        bRemoveAndPowerMask[64];
} __attribute__ ((packed))  USB_HUB_DESCRIPTOR, *PUSB_HUB_DESCRIPTOR;


#endif   /* __USB100_H__ */
