   /*************************************************************************/
   //
   //                  P H I L I P S   P R O P R I E T A R Y
   //
   //           COPYRIGHT (c)   1999 BY PHILIPS SINGAPORE.
   //                     --  ALL RIGHTS RESERVED  --
   //
   // File Name:	CHAP_9.H
   // Author:       Hilbert Zhang ZhenYu
   // Created:		Nov. 26 99
   // Modified:
   // Revision:		0.0
   //
   /*************************************************************************/
   //
   /*************************************************************************/


#ifndef __CHAP_9_H__
#define __CHAP_9_H__


/*************************************************************************/
// basic #defines
/*************************************************************************/
#define MAX_ENDPOINTS      0x16

#define STR_INDEX_LANGUAGE						    0x00	
#define STR_INDEX_MANUFACTURER					    0x01	
#define STR_INDEX_PRODUCT						    0x02	
#define STR_INDEX_SERIALNUMBER					    0x03
#define STR_INDEX_CONFIGURATION					    0x04	
#define STR_INDEX_INTERFACE						    0x05

#define USB_CLASS_CODE_MASSSTORAGE_CLASS_DEVICE             0x08

#define USB_SUBCLASS_CODE_RBC			                    0x01
#define USB_SUBCLASS_CODE_SFF8020I			                0x02
#define USB_SUBCLASS_CODE_QIC157			                0x03
#define USB_SUBCLASS_CODE_UFI			                    0x04
#define USB_SUBCLASS_CODE_SFF8070I			                0x05
#define USB_SUBCLASS_CODE_SCSI			                    0x06

#define USB_PROTOCOL_CODE_CBI0								0x00
#define USB_PROTOCOL_CODE_CBI1								0x01
#define USB_PROTOCOL_CODE_BULK								0x50

/*************************************************************************/
// masks
/*************************************************************************/

#define USB_RECIPIENT            (INT8)0x1F
#define USB_RECIPIENT_DEVICE     (INT8)0x00
#define USB_RECIPIENT_INTERFACE  (INT8)0x01
#define USB_RECIPIENT_ENDPOINT   (INT8)0x02

#define USB_REQUEST_TYPE_MASK    (INT8)0x60
#define USB_STANDARD_REQUEST     (INT8)0x00
#define USB_CLASS_REQUEST        (INT8)0x20
#define USB_VENDOR_REQUEST       (INT8)0x40

#define USB_REQUEST_MASK         (INT8)0xFF

#define DEVICE_ADDRESS_MASK      0x7F

/* GetStatus */
#define DEVSTS_SELFPOWERED       0x01
#define DEVSTS_REMOTEWAKEUP      0x02

#define ENDPSTS_HALT             0x01

/*************************************************************************/
// USB Protocol Layer
/*************************************************************************/

typedef struct _USB_STRING_LANGUAGE_DESCRIPTOR {
	INT8  bLength;
	INT8  bDescriptorType;
	INT16 ulanguageID;
} __attribute__ ((packed))  USB_STRING_LANGUAGE_DESCRIPTOR,*PUSB_STRING_LANGUAGE_DESCRIPTOR;

typedef struct _USB_STRING_INTERFACE_DESCRIPTOR {
	INT8  bLength;
	INT8  bDescriptorType;
	INT8  Interface[22];
} __attribute__ ((packed))  USB_STRING_INTERFACE_DESCRIPTOR,*PUSB_STRING_INTERFACE_DESCRIPTOR;

typedef struct _USB_STRING_CONFIGURATION_DESCRIPTOR {
	INT8  bLength;
	INT8  bDescriptorType;
	INT8  Configuration[16];
} __attribute__ ((packed))  USB_STRING_CONFIGURATION_DESCRIPTOR,*PUSB_STRING_CONFIGURATION_DESCRIPTOR;

typedef struct _USB_STRING_SERIALNUMBER_DESCRIPTOR {
	INT8  bLength;
	INT8  bDescriptorType;
	INT8  SerialNum[24];
} __attribute__ ((packed))  USB_STRING_SERIALNUMBER_DESCRIPTOR,*PUSB_STRING_SERIALNUMBER_DESCRIPTOR;

typedef struct _USB_STRING_PRODUCT_DESCRIPTOR {
	INT8  bLength;
	INT8  bDescriptorType;
	INT8  Product[30];
} __attribute__ ((packed))  USB_STRING_PRODUCT_DESCRIPTOR,*PUSB_STRING_PRODUCT_DESCRIPTOR;

typedef struct _USB_STRING_MANUFACTURER_DESCRIPTOR {
	INT8  bLength;
	INT8  bDescriptorType;
	INT8  Manufacturer[24];
} __attribute__ ((packed))  USB_STRING_MANUFACTURER_DESCRIPTOR,*PUSB_STRING_MANUFACTURER_DESCRIPTOR;

/*************************************************************************/
// USB standard device requests
/*************************************************************************/
void Chap9_GetStatus(void);
void Chap9_ClearFeature(void);
void Chap9_SetFeature(void);
void Chap9_SetAddress(void);
void Chap9_GetDescriptor(void);
void Chap9_GetConfiguration(void);
void Chap9_SetConfiguration(void);
void Chap9_GetInterface(void);
void Chap9_SetInterface(void);

/*************************************************************************/
// Chap9 support functions
/*************************************************************************/
void Chap9sup_SingleTransmitEP0(INT8 * buf, INT8 len);
void Chap9sup_BurstTransmitEP0(void);
#endif
