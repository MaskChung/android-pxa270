////////////////////////////////////////////////////////////////////////////////////
//
// Copyright (c) 1999-2003 PHILIPS Semiconductors - APIC
//
// Module Name:
//
//	ATA.h
//
// Abstract:
//
//     This module contains the structures and definitions for the ATAPI
//     IDE miniport driver.
//
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


#ifndef __ATA_H
#define __ATA_H

#include "asm/arch/lib/usb/basictyp.h"
#include "asm/arch/lib/usb/Hal4d12.h"

#define SECTOR_SIZE     512
#if(EP2_PACKET_SIZE == 8)
#define EP2PKTNUM_PER_SECTOR        64
#define BITNUM4EP2PKT_PER_SECTOR    6
#elif(EP2_PACKET_SIZE == 16)
#define EP2PKTNUM_PER_SECTOR        32
#define BITNUM4EP2PKT_PER_SECTOR    5
#elif(EP2_PACKET_SIZE == 32)
#define EP2PKTNUM_PER_SECTOR        16
#define BITNUM4EP2PKT_PER_SECTOR    4
#elif(EP2_PACKET_SIZE == 64)
#define EP2PKTNUM_PER_SECTOR        8
#define BITNUM4EP2PKT_PER_SECTOR    3
#endif


#define MULTI_BLOCK_1   1
#define MULTI_BLOCK_2   2
#define MULTI_BLOCK_4   4
#define MULTI_BLOCK_8   8
#define MULTI_BLOCK_16  16
#define MULTI_BLOCK_32  32
#define MULTI_BLOCK_64  64
#define MULTI_BLOCK_128 128

//
// IDE register definition
//

typedef struct _IDE_REGISTERS_1 {
	INT16 	Data;
	INT8 	BlockCount;
	INT8 	BlockNumber;
	INT8 	CylinderLow;
	INT8 	CylinderHigh;
	INT8 	DriveSelect;
	INT8 	Command;
} __attribute__ ((packed))  IDE_REGISTERS_1, *PIDE_REGISTERS_1;

typedef struct _IDE_REGISTERS_2 {
	INT8 AlternateStatus;
	INT8 DriveAddress;
} __attribute__ ((packed))  IDE_REGISTERS_2, *PIDE_REGISTERS_2;

typedef struct _IDE_REGISTERS_3 {
	INT32 Data;
	INT8 Others[4];
} __attribute__ ((packed))  IDE_REGISTERS_3, *PIDE_REGISTERS_3;



// IDE command definitions
#define IDE_COMMAND_ATAPI_RESET      0x08
#define IDE_COMMAND_RECALIBRATE      0x10
#define IDE_COMMAND_READ             0x20
#define IDE_COMMAND_WRITE            0x30
#define IDE_COMMAND_VERIFY           0x40
#define IDE_COMMAND_SEEK             0x70
#define IDE_COMMAND_EXEC_DIAGNOSE    0x90
#define IDE_COMMAND_SET_DRIVE_PARAMETERS 0x91
#define IDE_COMMAND_ATAPI_PACKET     0xA0
#define IDE_COMMAND_ATAPI_IDENTIFY   0xA1
#define IDE_COMMAND_READ_MULTIPLE    0xC4
#define IDE_COMMAND_WRITE_MULTIPLE   0xC5
#define IDE_COMMAND_SET_MULTIPLE     0xC6
#define IDE_COMMAND_READ_DMA         0xC8
#define IDE_COMMAND_WRITE_DMA             0xCA
#define IDE_COMMAND_GET_MEDIA_STATUS      0xDA
#define IDE_COMMAND_ENABLE_MEDIA_STATUS   0xEF
#define IDE_COMMAND_IDENTIFY              0xEC
#define IDE_COMMAND_MEDIA_EJECT           0xED


// IDE status definitions

#define IDE_STATUS_ERROR             0x01
#define IDE_STATUS_INDEX             0x02
#define IDE_STATUS_CORRECTED_ERROR   0x04
#define IDE_STATUS_DRQ               0x08
#define IDE_STATUS_DSC               0x10
#define IDE_STATUS_DRDY              0x40
#define IDE_STATUS_IDLE              0x50
#define IDE_STATUS_BUSY              0x80


// IDE drive select/head definitions

#define IDE_DRIVE_SELECT_1           0xA0
#define IDE_DRIVE_SELECT_2           0x10


// IDE drive control definitions

#define IDE_DC_DISABLE_INTERRUPTS    0x02
#define IDE_DC_RESET_CONTROLLER      0x04
#define IDE_DC_REENABLE_CONTROLLER   0x00


// IDE error definitions

#define IDE_ERROR_BAD_BLOCK          0x80
#define IDE_ERROR_DATA_ERROR         0x40
#define IDE_ERROR_MEDIA_CHANGE       0x20
#define IDE_ERROR_ID_NOT_FOUND       0x10
#define IDE_ERROR_MEDIA_CHANGE_REQ   0x08
#define IDE_ERROR_COMMAND_ABORTED    0x04
#define IDE_ERROR_END_OF_MEDIA       0x02
#define IDE_ERROR_ILLEGAL_LENGTH     0x01



// ATAPI Register Files



typedef struct _ATAPI_REGISTERS_1 {
	INT16 Data;
	INT8 InterruptReason;
	INT8 Unused1;
	INT8 ByteCountLow;
	INT8 ByteCountHigh;
	INT8 DriveSelect;
	INT8 Command;
} __attribute__ ((packed))  ATAPI_REGISTERS_1, *PATAPI_REGISTERS_1;

typedef struct _ATAPI_REGISTERS_2 {
	INT8 AlternateStatus;
	INT8 DriveAddress;
} __attribute__ ((packed))  ATAPI_REGISTERS_2, *PATAPI_REGISTERS_2;




// ATAPI interrupt reasons
#define ATAPI_IR_COD 0x01
#define ATAPI_IR_IO  0x02


// ATAPI command definitions
#define ATAPI_READ10       0x28
#define ATAPI_WRITE10      0x2A
#define ATAPI_MODE_SENSE   0x5A
#define ATAPI_MODE_SELECT  0x55
#define ATAPI_FORMAT_UNIT  0x24


// ATAPI Command Descriptor Block
typedef struct _READ_10 {
		INT8 OperationCode;
		INT8 Reserved1;
		INT8 LBA_3;
		INT8 LBA_2;
        INT8 LBA_1;
		INT8 LBA_0;
		INT8 Reserved2;
		INT8 XferLen_1;
		INT8 XferLen_0;
		INT8 Reserved3[3];
} __attribute__ ((packed))  READ_10, * PREAD_10;



typedef struct _WRITE_10 {
		INT8 OperationCode;
		INT8 Reserved1;
		INT8 LBA_3;
		INT8 LBA_2;
        INT8 LBA_1;
		INT8 LBA_0;
		INT8 Reserved2;
		INT8 XferLen_1;
		INT8 XferLen_0;
		INT8 Reserved3[3];
} __attribute__ ((packed))  WRITE_10, *PWRITE_10;

typedef struct _MODE_SENSE_10 {
		INT8 OperationCode;
		INT8 Reserved1;
		INT8 PageCode : 6;
		INT8 Pc : 2;
		INT8 Reserved2[4];
		INT8 ParameterListLengthMsb;
		INT8 ParameterListLengthLsb;
		INT8 Reserved3[3];
} __attribute__ ((packed))  MODE_SENSE_10, *PMODE_SENSE_10;

typedef struct _MODE_SELECT_10 {
		INT8 OperationCode;
		INT8 Reserved1 : 4;
		INT8 PFBit : 1;
		INT8 Reserved2 : 3;
		INT8 Reserved3[5];
		INT8 ParameterListLengthMsb;
		INT8 ParameterListLengthLsb;
		INT8 Reserved4[3];
} __attribute__ ((packed))  MODE_SELECT_10, *PMODE_SELECT_10;

typedef union _ATAPI_COMMAND_PACKET {

    READ_10         CmdRead10;
    WRITE_10        CmdWrite10;
    MODE_SELECT_10  CmdModeSel10;
    MODE_SENSE_10   CmdModeSen10;
} __attribute__ ((packed))  ATAPI_COMMAND_PACKET, * PATAPI_COMMAND_PACKET;

typedef struct _MODE_PARAMETER_HEADER_10 {
	INT8 ModeDataLengthMsb;
	INT8 ModeDataLengthLsb;
	INT8 MediumType;
	INT8 Reserved[5];
} __attribute__ ((packed)) MODE_PARAMETER_HEADER_10, *PMODE_PARAMETER_HEADER_10;


// IDENTIFY data
typedef struct _IDENTIFY_DATA {
	INT16 GeneralConfiguration;            // 00 00
	INT16 NumberOfCylinders;               // 02  1
	INT16 Reserved1;                       // 04  2
	INT16 NumberOfHeads;                   // 06  3
	INT16 UnformattedBytesPerTrack;        // 08  4
	INT16 UnformattedBytesPerSector;       // 0A  5
	INT16 SectorsPerTrack;                 // 0C  6
	INT16 VendorUnique1[3];                // 0E  7-9
	INT16 SerialNumber[10];                // 14  10-19
	INT16 BufferType;                      // 28  20
	INT16 BufferSectorSize;                // 2A  21
	INT16 NumberOfEccBytes;                // 2C  22
	INT16 FirmwareRevision[4];             // 2E  23-26
    INT16 ModelNumber[20];                 // 36  27-46
    INT16  MaximumBlockTransfer;            // 5E 47
	//INT8  VendorUnique2;                   // 5F
    INT16 DoubleWordIo;                    // 60  48
    INT16 Capabilities;                    // 62  49
    INT16 Reserved2;                       // 64  50
    INT8  VendorUnique3;                   // 66  51
    INT8  PioCycleTimingMode;              // 67
    INT8  VendorUnique4;                   // 68  52
    INT8  DmaCycleTimingMode;              // 69
    INT16 TranslationFieldsValid:1;        // 6A  53
    INT16 Reserved3:15;
    INT16 NumberOfCurrentCylinders;        // 6C  54
    INT16 NumberOfCurrentHeads;            // 6E  55
    INT16 CurrentSectorsPerTrack;          // 70  56
    INT32  CurrentSectorCapacity;           // 72  57-58
    INT16 CurrentMultiSectorSetting;       //     59
    INT32  UserAddressableSectors;          //     60-61
    INT16 SingleWordDMASupport : 8;        //     62
    INT16 SingleWordDMAActive : 8;
    INT16 MultiWordDMASupport : 8;         //     63
    INT16 MultiWordDMAActive : 8;
    INT16 AdvancedPIOModes : 8;            //     64
    INT16 Reserved4 : 8;
    INT16 MinimumMWXferCycleTime;          //     65
    INT16 RecommendedMWXferCycleTime;      //     66
	INT16 MinimumPIOCycleTime;             //     67
	INT16 MinimumPIOCycleTimeIORDY;        //     68
    INT16 Reserved5[2];                    //     69-70
    INT16 ReleaseTimeOverlapped;           //     71
    INT16 ReleaseTimeServiceCommand;       //     72
    INT16 MajorRevision;                   //     73
    INT16 MinorRevision;                   //     74
    INT16 Reserved6[50];                   //     75-126
    INT16 SpecialFunctionsEnabled;         //     127
    INT16 Reserved7[128];                  //     128-255
} __attribute__ ((packed))  IDENTIFY_DATA, *PIDENTIFY_DATA;


// Identify data without the Reserved4.
typedef struct _IDENTIFY_DATA2 {
    INT16 GeneralConfiguration;            // 00
    INT16 NumberOfCylinders;               // 02
//    INT16 Reserved1;                       // 04
    INT16 NumberOfHeads;                   // 06
//    INT16 UnformattedBytesPerTrack;        // 08
//    INT16 UnformattedBytesPerSector;       // 0A
    INT16 SectorsPerTrack;                 // 0C
//    INT16 VendorUnique1[3];                // 0E
//    INT16 SerialNumber[10];                // 14
//    INT16 BufferType;                      // 28
//    INT16 BufferSectorSize;                // 2A
//	INT16 NumberOfEccBytes;                // 2C
//    INT16 FirmwareRevision[4];             // 2E
//    INT16 ModelNumber[20];                 // 36
    INT16  MaximumBlockTransfer;            // 5E
//    INT8  VendorUnique2;                   // 5F
//    INT16 DoubleWordIo;                    // 60
    INT16 Capabilities;                    // 62
//    INT16 Reserved2;                       // 64
//    INT8  VendorUnique3;                   // 66
//	INT8  PioCycleTimingMode;              // 67
//    INT8  VendorUnique4;                   // 68
//    INT8  DmaCycleTimingMode;              // 69
//    INT16 TranslationFieldsValid:1;        // 6A
//    INT16 Reserved3:15;
    INT16 NumberOfCurrentCylinders;        // 6C
    INT16 NumberOfCurrentHeads;            // 6E
    INT16 CurrentSectorsPerTrack;          // 70
    FLEXI_INT32 CurrentSectorCapacity;           // 72
} __attribute__ ((packed))  IDENTIFY_DATA2, *PIDENTIFY_DATA2;

#define IDENTIFY_DATA_SIZE sizeof(IDENTIFY_DATA)

// IDENTIFY capability bit definitions.
#define IDENTIFY_CAPABILITIES_DMA_SUPPORTED 0x0100
#define IDENTIFY_CAPABILITIES_LBA_SUPPORTED 0x0200

// IDENTIFY DMA timing cycle modes.
#define IDENTIFY_DMA_CYCLES_MODE_0 0x00
#define IDENTIFY_DMA_CYCLES_MODE_1 0x01
#define IDENTIFY_DMA_CYCLES_MODE_2 0x02





#endif /* ATAPI_H */




