
////////////////////////////////////////////////////////////////////////////////////
//
// Copyright (c) 1998 - 1999
//
// Module Name:
//
//      Hal4ATA.h
//
// Abstract:
//
//     This module contains the USB interfaces for the ATAPI IDE miniport driver.
//
// Author:
//
//     Zhang ZhenYu
//
// Created Date: 26 Nov. 1999
//
///////////////////////////////////////////////////////////////////////////////////


#ifndef __HAL4ATA_H
#define __HAL4ATA_H


#include "asm/arch/lib/usb/basictyp.h"
#include "asm/arch/lib/usb/Ata.h"


#define HAL4ATA_MAX_ATADEV   1

typedef struct _HW_ATA_DEVICES_EXTENSION {

	IDENTIFY_DATA2 IdentifyData;//[HAL4ATA_MAX_ATADEV];

} __attribute__ ((packed))  HW_ATA_DEVICES_EXTENSION, *PHW_ATA_DEVICES_EXTENSION;



//
// Device Extension Device Flags
//

#define DFLAGS_DEVICE_PRESENT        0x0001    /* Indicates that some device is present.*/
#define DFLAGS_ATAPI_DEVICE          0x0002    /* Indicates whether Atapi commands can be used.*/
#define DFLAGS_TAPE_DEVICE           0x0004    /* Indicates whether this is a tape device.*/
#define DFLAGS_INT_DRQ               0x0008    /* Indicates whether device interrupts as DRQ is set after*/
											   /* receiving Atapi Packet Command*/
#define DFLAGS_REMOVABLE_DRIVE       0x0010    /* Indicates that the drive has the 'removable' bit set in*/
											   /* identify data (offset 128)*/
#define DFLAGS_MEDIA_STATUS_ENABLED  0x0020    /* Media status notification enabled*/
#define DFLAGS_ATAPI_CHANGER         0x0040    /* Indicates atapi 2.5 changer present.*/
#define DFLAGS_SANYO_ATAPI_CHANGER   0x0080    /* Indicates multi-platter device, not conforming to the 2.5 spec.*/
#define DFLAGS_CHANGER_INITED        0x0100    /* Indicates that the init path for changers has already been done.*/

#define DFLAGS_LBA                   0x2000    /* If Device is IDE harddisk, Check it as LBA mode or CHS mode */

//
// Subroutines
//

void
Hal4ATA_GetStatus(
	void
);

BOOLEAN
Hal4ATA_WaitOnBusy(
	void
);

BOOLEAN
Hal4ATA_WaitOnBusyNDrdy(
	void
);

BOOLEAN
Hal4ATA_WaitOnBusyNDrq(
	void
);

/*
BOOLEAN
Hal4ATA_WaitForDrq(
	void
);
*/


BOOLEAN
Hal4ATA_IdeSoftReset(
	void
) ;

BOOLEAN
Hal4ATA_IdeHardReset(
    void
);

BOOLEAN
Hal4ATA_SelDevice(
    void
);

BOOLEAN
Hal4ATA_IsModeOK(
    void
);

BOOLEAN
Hal4ATA_IsLBAmode(
    void
);

BOOLEAN
Hal4ATA_IssueIDEIdentify(
    void
);

BOOLEAN
Hal4ATA_SetDriveParameters(
    void
);

BOOLEAN
Hal4ATA_SetMultipleMode(
    void
);

BOOLEAN
Hal4ATA_SetFeature(
    void
);


BOOLEAN
Hal4ATA_FindIDEDevice(
    void
);

BOOLEAN
Hal4ATA_ReadWriteSetting(
    void
);

BOOLEAN
Hal4ATA_InitDevExt(
    void
);

#endif  /*Hal4ATA.H*/
