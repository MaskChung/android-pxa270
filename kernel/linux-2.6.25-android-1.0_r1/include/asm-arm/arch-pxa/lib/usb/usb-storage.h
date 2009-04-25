   /*************************************************************************/
   //
   //                  P H I L I P S   P R O P R I E T A R Y
   //
   //           COPYRIGHT (c)   1999 BY PHILIPS SINGAPORE.
   //                     --  ALL RIGHTS RESERVED  --
   //
   // File Name:        usb-storage.H
   // Author:           Hilbert Zhang ZhenYu
   //                   Chew Thing Piao
   // Created:          Oct. 1 99
   // Modified:
   // Revision: 		0.0
   //
   /*************************************************************************/


#ifndef __USB-STORAGE_H__
#define __USB-STORAGE_H__


#define MAX_STD_REQUEST     12
#define MAX_CLASS_REQUEST   2
#define MAX_VENDOR_REQUEST  1

/*************************************************************************/
// basic #defines
/*************************************************************************/

/*************************************************************************/
// structure and union definitions
/*************************************************************************/


/*************************************************************************/
// USB utility functions
/*************************************************************************/
#ifndef MAX_SPEED
void MLsup_XferWordFrUSB2IDE(void);
void MLsup_XferWordFrIDE2USB(void);
#endif

void MLsup_XferPktFrIDE2USB(void);
INT8 MLsup_XferPktFrMEM2USB(void);
void MLsup_XferPktFrUSB2IDE(void);
void MLsup_XferPktFrUSB2MEM(INT8 Len);

void MLsup_DisconnectUSB(void);
void MLsup_ConnectUSB(void);
void MLsup_ReconnectUSB(void);

void MLsup_USBSetupTokenHandler(void);

void MLsup_AcknowledgeSETUP(void);
void MLsup_StallEP0(void);

void usb_EventCheck(void);

#endif

