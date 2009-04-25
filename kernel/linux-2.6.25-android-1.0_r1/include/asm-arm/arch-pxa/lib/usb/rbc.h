////////////////////////////////////////////////////////////////////////////////////
//
// Copyright (c) 1999-2003 PHILIPS Semiconductors - APIC
//
// Module Name:
//
//	rbc.h
//
// Abstract:
//
//    These are the structures and defines used in the Reduced Block Command set//

// Author:
//
//     Hilbert Zhang ZhenYu
//
// Revision History:
//
//		Created  29 Apr. 1999
//
// Copyright @ 1999-2003, PHILIPS Semiconductors - APIC. All rights reserved.
//
//  Implementation Notes:
//      1. LUN
//          In SCSI-2, 3 bits LUN locates the highest 3 bits of the byte next to
//          OperationCode of the command Request.
//          In SCSI-3, 4 Bits LUN is controlled by Transport Protocol,
//              RBC and SPC-2 are parts of SCSI-3
//

#ifndef __RBC_
#define __RBC_

#include "asm/arch/lib/usb/basictyp.h"

typedef union _RBC_PROP {
	struct {
        INT8    MediumRemovFlag : 2;
	    INT8    MediumState : 2;
	    INT8    PowerState : 4;
    }__attribute__ ((packed))  bits;

    INT8 value;
} __attribute__ ((packed))  RBC_PROPERTY, * PRBC_PROPERTY;


////////////////////////////////////////////////////////////////////////////////////
// Functions
////////////////////////////////////////////////////////////////////////////////////


BOOLEAN RBC_Handler(void);

BOOLEAN RBC_Read(void);
BOOLEAN RBC_ReadCapacity(void);
BOOLEAN RBC_OnOffUnit(void);
BOOLEAN RBC_Verify(void);
BOOLEAN RBC_Write(void);
BOOLEAN RBC_SyncCache(void);

BOOLEAN SPC_Inquiry(void);
BOOLEAN SPC_ModeSelect(void);
BOOLEAN SPC_ModeSense(void);
BOOLEAN SPC_LockMedia(void);
BOOLEAN SPC_TestUnit(void);
BOOLEAN SPC_RequestSense(void);

//Optional
BOOLEAN RBC_Format(void);
BOOLEAN SPC_Reserve6(void);
BOOLEAN SPC_Release6(void);
BOOLEAN SPC_PersisReserveIn(void);
BOOLEAN SPC_PersisReserveOut(void);
BOOLEAN SPC_WriteBuff(void);
BOOLEAN SPC_READLONG(void);


void RBC_BuildSenseData(INT8 SenseKey,INT8 ASC, INT8 ASCQ);

#endif

