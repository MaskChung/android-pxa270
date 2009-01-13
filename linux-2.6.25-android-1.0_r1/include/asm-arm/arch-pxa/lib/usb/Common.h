   /*************************************************************************/
   //
   //                  P H I L I P S   P R O P R I E T A R Y
   //
   //           COPYRIGHT (c)   1999 BY PHILIPS SINGAPORE.
   //                     --  ALL RIGHTS RESERVED  --
   //
   // File Name:        Common.H
   // Author:           ZhenYu Zhang	
   // Created:          Feb. 1, 1999
   // Modified:
   // Revision: 		0.0
   //
   /*************************************************************************/
   //
   /*************************************************************************/
#ifndef __COMMON_H__
#define __COMMON_H__

#include "asm/arch/lib/usb/basictyp.h"
#include "asm/arch/lib/usb/tpbulk.h"
#include "asm/arch/lib/usb/Hal4ata.h"



/*************************************************************************/
// print message 
/*************************************************************************/
#define DEF_USB_PRINT 0
#define DEF_PRINT 0
#define DEF_PRINT1 0

/*************************************************************************/
// macros
/*************************************************************************/

/*************************************************************************/
// macros
/*************************************************************************/
#define SWAP(x)   (x)	//((((x) & 0xFF) << 8) | (((x) >> 8) & 0xFF))

#define MSB(x)    (((x) >> 8) & 0xFF)
#define LSB(x)    ((x) & 0xFF)

#define FALSE   0
#define TRUE    1


//#define LOBYTE(x)  (INT8)((x) >> 8)	//C51 byte ording uses Big Endian(Motorola)
//#define HIBYTE(x)  (INT8)((x) & 0x00ff) //High byte is stored first



#define NULL_LENGTH 0xff



// MAX_CONTROLDATA_SIZE is between ADSC command and 50ms*384Bytes/ms=18KBytes
// MaxSize for RBC command is 16 Bytes
typedef union _bit_flags
{
	struct _flags
	{
		INT8 timer               	: 1;    //MCUBF_Timer
		INT8 setup_overwritten  	: 1;    //D12BF_SetupOverwritten
		INT8 configuration			: 1;    //D12BF_Configuration    
        INT8 DCPRequst_Dir          : 1;    //REQBF_DCPRequest_dir ==1 Read(from Dev to Host)
        INT8 DCPRequst_EPDir        : 1;    //REQBF_DCPRequest_EPdir ==1 for In Endpoint
        INT8 Stall_DCPRequest       : 1;    //REQBF_StallDCPRequest
        INT8 BO_Stalled             : 1;    //BOTBF_StallSrcAtBulkOut
        INT8 BI_Stalled             : 1;    //BOTBF_StallSrcAtBulkIn

        INT8 Abort_BOT              : 1;    //Abort BOT Xfer
        INT8 ATABF_IsAttached       : 1;	//HardDisk is Attached
        INT8 ATABF_IsSupportMultiSector: 1; //MultiBlock support  
        INT8 ATABF_IDEXfer_dir      : 1;     // ATABF_Xfer_dir==1 Read(from Dev to Host)
        INT8 ATABF_IsSkipSetParameters: 1;   
        INT8 Reserved               : 3;  
	
    }__attribute__ ((packed))  bits;

    INT16 value;
}__attribute__ ((packed))  BITFLAGS;





typedef struct _device_request
{
	INT8 bmRequestType;
	INT8 bRequest;
	INT16 wValue;
	INT16 wIndex;
	INT16 wLength;
} __attribute__ ((packed))  DEVICE_REQUEST;


#define MAX_CONTROLDATA_SIZE	8

typedef struct _control_xfer
{
	INT16 wLength;
	INT16 wCount;
	INT8 * pData;
	INT8 dataBuffer[MAX_CONTROLDATA_SIZE];
} __attribute__ ((packed))  CONTROL_XFER, * PCONTROL_XFER;


/*************************************************************************/
// basic FSM state
/*************************************************************************/

// FSM for Device
#define USBFSM4DEV_ATTACHED             0
#define USBFSM4DEV_POWRED               1
#define USBFSM4DEV_DEFAULT              2
#define USBFSM4DEV_ADDRESS              3
#define USBFSM4DEV_CONFIGURED           4
#define USBFSM4DEV_SUSPENDED            5
#define USBFSM4DEV_RESET                USBFSM_DEFAULT


// FSM for Default Control Pipe
// One-Hot dinfition4DCPFSM
#define USBFSM4DCP_IDLE             0x00
#define USBFSM4DCP_SETUPPROC        0x01
#define USBFSM4DCP_DATAIN           0x02
#define USBFSM4DCP_DATAOUT          0x04
#define USBFSM4DCP_HANDSHAKE4CO     0x08
#define USBFSM4DCP_HANDSHAKE4CI     0x10
#define USBFSM4DCP_STALL            0x80

// FSM for Bulk-Only Transfer
// One-Hot dinfition4BOTFSM
#define USBFSM4BOT_STALL            0x80
#define USBFSM4BOT_IDLE             0x01
#define USBFSM4BOT_CBWPROC          0x02
#define USBFSM4BOT_DATAIN           0x04
#define USBFSM4BOT_DATAOUT          0x08
#define USBFSM4BOT_CSWPROC          0x10
#define USBFSM4BOT_CSW              0x20

// One-Hot dinfition4XferSpace
#define DCPXFERSPACE_MASK           0xF0
#define DCPXFERSPACE_UNKNOWN        0x00
#define DCPXFERSPACE_MCUCODE        0x01
#define DCPXFERSPACE_MCURAM         0x02
#define DCPXFERSPACE_EEROM          0x04
#define DCPXFERSPACE_ATAPORT        0x08

#define BOTXFERSPACE_MASK           0x0F
#define BOTXFERSPACE_UNKNOWN        0x00
#define BOTXFERSPACE_MCURAM         0x10
#define BOTXFERSPACE_ATAPORT        0x20
#define BOTXFERSPACE_MCUCODE        0x40

#define STALLSRC_BULKIN             0x1
#define STALLSRC_BULKOUT            0x2




////////////////////////////////////////////
// DefaultControlPipe Finite State Machine [One-Hot]
typedef union _dcpfsm_status
{
	struct _dcpfsm
	{
		INT8 SetupProc		: 1;   
		INT8 DataIn  		: 1;    
		INT8 DataOut		: 1;       
        INT8 COhandshake    : 1;    
        INT8 CIhandshake    : 1;    
        INT8 Reserved       : 2;    
        INT8 Stall          : 1;    
    }__attribute__ ((packed))  dcpfsm_bits;

    INT8 value;
}__attribute__ ((packed))  DCPFSM_STATUS;


// Bulk-Only TP Finite State Machine [One-Hot]
typedef union _botfsm_status
{
	struct _botfsm
	{
		INT8 IDLE			: 1;   
		INT8 CBWProc  		: 1;    
		INT8 DataIn			: 1;       
        INT8 DataOut    	: 1;    
        INT8 CSWProc    	: 1;    
        INT8 CSW       		: 1;    
        INT8 Reserved       : 1;    
        INT8 Stall          : 1;    
    }__attribute__ ((packed))  botfsm_bits;

    INT8 value;
}__attribute__ ((packed))  BOTFSM_STATUS;


// Xfer_Space
typedef union _xfer_space
{
	struct _xfer
	{
		INT8 DCPXfer_atMCUCODE 		: 1;   
		INT8 DCPXfer_atMCURAM  		: 1;    
		INT8 DCPXfer_atEEROM		: 1;       
        INT8 DCPXfer_atATA    		: 1;    
        INT8 BOTXfer_atRAM    		: 1;    
        INT8 BOTXfer_atATA       	: 1;    
        INT8 BOTXfer_atROM       	: 1;    
        INT8 Reserved          		: 1;    
    }__attribute__ ((packed))  xfer_bits;

    INT8 value;
}__attribute__ ((packed))  XFER_SPACE;


typedef union _flex_byte
{
	struct _flex
	{
		INT8 b0 		: 1;   
		INT8 b1  		: 1;    
		INT8 b2			: 1;       
        INT8 b3    		: 1;    
        INT8 b4    		: 1;    
        INT8 b5       	: 1;    
        INT8 b6       	: 1;    
        INT8 b7    		: 1;    
    }__attribute__ ((packed))  flex_bits;

    INT8 value;
}__attribute__ ((packed))  FLEX_BYTE;








#endif
