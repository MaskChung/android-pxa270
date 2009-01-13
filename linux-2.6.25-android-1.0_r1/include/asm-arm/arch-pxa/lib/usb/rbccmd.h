
////////////////////////////////////////////////////////////////////////////////////
//
// Copyright (c) 1999-2003 PHILIPS Semiconductors - APIC
//
// Module Name:
//
//	rbcCMD.h
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


#ifndef __RBC_COMMAND_
#define __RBC_COMMAND_

//#ifndef BIG_ENDIAN
//#define BIG_ENDIAN
//#endif

#include "asm/arch/lib/usb/basictyp.h"
#include "asm/arch/lib/usb/Ata.h"


// RBC commands
#define RBC_CMD_FORMAT						0x04
#define RBC_CMD_READ10						0x28
#define RBC_CMD_READCAPACITY				0x25
#define RBC_CMD_STARTSTOPUNIT				0x1B
#define RBC_CMD_SYNCCACHE					0x35
#define RBC_CMD_VERIFY10					0x2F
#define RBC_CMD_WRITE10						0x2A


// SPC-2 commands
#define SPC_CMD_INQUIRY						0x12
#define SPC_CMD_MODESELECT6					0x15
#define SPC_CMD_MODESENSE6					0x1A
#define SPC_CMD_PERSISTANTRESERVIN			0x5E
#define SPC_CMD_PERSISTANTRESERVOUT			0x5F
#define SPC_CMD_PRVENTALLOWMEDIUMREMOVAL	0x1E
#define SPC_CMD_RELEASE6					0x17
#define SPC_CMD_REQUESTSENSE				0x03
#define SPC_CMD_RESERVE6					0x16
#define SPC_CMD_TESTUNITREADY				0x00
#define SPC_CMD_WRITEBUFFER					0x3B
#define SPC_CMD_READLONG					0x23



// for START_STOP_RBC
#define PWR_NOCHANGE    0
#define PWR_ACTIVE      1
#define PWR_IDLE        2
#define PWR_STANDBY     3
#define PWR_SLEEP       5
#define PWR_DEVCTRL     7

#define MEDIUM_STOP     0
#define MEDIUM_READY    1
#define MEDIUM_UNLOAD   2
#define MEDIUM_LOAD     3


// DeviceType field for Inquiry Data
#define DIRECT_ACCESS_DEVICE            0x00    /* disks */
#define SEQUENTIAL_ACCESS_DEVICE        0x01    /* tapes */
#define PRINTER_DEVICE                  0x02    /* printers */
#define PROCESSOR_DEVICE                0x03    /* scanners, printers, etc */
#define WRITE_ONCE_READ_MULTIPLE_DEVICE 0x04    /* worms */
#define READ_ONLY_DIRECT_ACCESS_DEVICE  0x05    /* cdroms */
#define SCANNER_DEVICE                  0x06    /* scanners */
#define OPTICAL_DEVICE                  0x07    /* optical disks */
#define MEDIUM_CHANGER                  0x08    /* jukebox */
#define COMMUNICATION_DEVICE            0x09    /* network */

#define ASC0T18_DEVICE                  0x0A    /* ASC IT8 */
#define ASC1T18_DEVICE                  0x0B    /* ASC IT8 */

#define SCC2_DEVICE                     0x0C    /* Storage Controller device */
#define SES_DEVICE                      0x0D    /* Enclousre Service device */
#define RBC_DEVICE                      0x0E    /* RBC device */
#define OCRW_DEVICE                     0x0F    /* optical Card Reader /writer Device */

#define LOGICAL_UNIT_NOT_PRESENT_DEVICE 0x7F
#define DEVICE_QUALIFIER_NOT_SUPPORTED  0x03

// DeviceTypeQualifier field
#define REMOVABLE_MASK  0x80
#define NOT_REMOVABLE   0x0         /* disks  */
#define REMOVABLE       0x80        /* CD-ROM  */


#define VPDPAGE_SERIAL_NUMBER   0x80
#define VPDPAGE_DEVICE_IDENTITY 0x83


// Mode Sense/Select page constants.
#define MODE_PAGE_RBC_DEVICE_PARAMETERS 0x06

#define PAGECTRL_CURRENT    0x0
#define PAGECTRL_CHANGEABLE 0x1
#define PAGECTRL_DEFAULT    0x2
#define PAGECTRL_SAVED      0x3

#define MEDIUMREMOVAL_PROHIBITED_ALL    0x3
#define MEDIUMREMOVAL_ALLOWED_ALL       0x0
#define MEDIUMREMOVAL_ALLOWED_CHANGER   0x1
#define MEDIUMREMOVAL_ALLOWED_DATA      0x2


#define SCSI_RESPONSECODE_CURRENT_ERROR     0x70
#define SCSI_RESPONSECODE_PREVIOUS_ERROR    0x71

//
// Sense codes
//

#define SCSI_SENSE_NO_SENSE         0x00
#define SCSI_SENSE_RECOVERED_ERROR  0x01
#define SCSI_SENSE_NOT_READY        0x02
#define SCSI_SENSE_MEDIUM_ERROR     0x03
#define SCSI_SENSE_HARDWARE_ERROR   0x04
#define SCSI_SENSE_ILLEGAL_REQUEST  0x05
#define SCSI_SENSE_UNIT_ATTENTION   0x06
#define SCSI_SENSE_DATA_PROTECT     0x07
#define SCSI_SENSE_BLANK_CHECK      0x08
#define SCSI_SENSE_UNIQUE           0x09
#define SCSI_SENSE_COPY_ABORTED     0x0A
#define SCSI_SENSE_ABORTED_COMMAND  0x0B
#define SCSI_SENSE_EQUAL            0x0C
#define SCSI_SENSE_VOL_OVERFLOW     0x0D
#define SCSI_SENSE_MISCOMPARE       0x0E
#define SCSI_SENSE_RESERVED         0x0F


// Additional tape bit
#define SCSI_ILLEGAL_LENGTH         0x20
#define SCSI_EOM                    0x40
#define SCSI_FILE_MARK              0x80

// Additional Sense codes
#define SCSI_ADSENSE_NO_SENSE       0x00
#define SCSI_ADSENSE_LUN_NOT_READY  0x04
#define SCSI_ADSENSE_ILLEGAL_COMMAND 0x20
#define SCSI_ADSENSE_ILLEGAL_BLOCK  0x21
#define SCSI_ADSENSE_INVALID_PARAMETER    0x26
#define SCSI_ADSENSE_INVALID_LUN    0x25
#define SCSI_ADSENSE_INVALID_CDB    0x24
#define SCSI_ADSENSE_MUSIC_AREA     0xA0
#define SCSI_ADSENSE_DATA_AREA      0xA1
#define SCSI_ADSENSE_VOLUME_OVERFLOW 0xA7

#define SCSI_ADSENSE_NO_MEDIA_IN_DEVICE 0x3A
#define SCSI_ADSENSE_FORMAT_ERROR		0x31
#define SCSI_ADSENSE_CMDSEQ_ERROR		0x2C
#define SCSI_ADSENSE_MEDIUM_CHANGED 0x28
#define SCSI_ADSENSE_BUS_RESET      0x29
#define SCSI_ADWRITE_PROTECT        0x27
#define SCSI_ADSENSE_TRACK_ERROR    0x14
#define SCSI_ADSENSE_SAVE_ERROR     0x39
#define SCSI_ADSENSE_SEEK_ERROR     0x15
#define SCSI_ADSENSE_REC_DATA_NOECC 0x17
#define SCSI_ADSENSE_REC_DATA_ECC   0x18


// Additional sense code qualifier
#define SCSI_SENSEQ_FORMAT_IN_PROGRESS 0x04
#define SCSI_SENSEQ_INIT_COMMAND_REQUIRED 0x02
#define SCSI_SENSEQ_MANUAL_INTERVENTION_REQUIRED 0x03
#define SCSI_SENSEQ_BECOMING_READY 0x01
#define SCSI_SENSEQ_FILEMARK_DETECTED 0x01
#define SCSI_SENSEQ_SETMARK_DETECTED 0x03
#define SCSI_SENSEQ_END_OF_MEDIA_DETECTED 0x02
#define SCSI_SENSEQ_BEGINNING_OF_MEDIA_DETECTED 0x04


#define WRBUFF_MODE_COMBINED            0x0
#define WRBUFF_MODE_Vendor              0x1
#define WRBUFF_MODE_DATA                0x2
#define WRBUFF_MODE_DOWNLD              0x4
#define WRBUFF_MODE_DOWNLD_SAVE         0x2
#define WRBUFF_MODE_DOWNLD_OFFSET       0x6
#define WRBUFF_MODE_DOWNLD_SAVE_OFFSET  0x7
#define WRBUFF_MODE_ECHO                0xA











// unsolicited status sense code qualifier values
#define RBC_UNSOLICITED_STATUS              0x02
#define RBC_UNSOLICITED_SENSE_KEY           0x06

#define RBC_UNSOLICITED_SC_PWR_STATE_CHNG   0xFF
#define RBC_UNSOLICITED_SC_EVENT_STATUS     0xFE

#define RBC_UNSOLICITED_CLASS_ASQ_DEVICE    0x06
#define RBC_UNSOLICITED_CLASS_ASQ_MEDIA     0x04
#define RBC_UNSOLICITED_CLASS_ASQ_POWER     0x02


////////////////////////////////////////////////////////////////////////////////////
// Command Descriptor Block
//      _RBC : Reduced Block Command
//      _SPC : SPC-2 SCSI primary Command - 2
////////////////////////////////////////////////////////////////////////////////////



// Generic

// Generic
typedef struct _GENERIC_CDB {
	INT8 OperationCode;
    INT8 Reserved[15];
} __attribute__ ((packed))  GENERIC_CDB,*PGENERIC_CDB;

typedef struct _GENERIC_RBC {
	INT8 OperationCode;
    INT8 Reserved[8];
	INT8 Control;
} __attribute__ ((packed))  GENERIC_RBC,*PGENERIC_RBC;


// format unit

typedef struct _FORMAT_RBC {
	INT8 OperationCode;	/* 04H */
	INT8 VendorSpecific;
	INT8 Increment : 1;
	INT8 PercentorTime : 1;
	INT8 Progress : 1;
    INT8 Immediate : 1;
	INT8 VendorSpecific1 : 4;
	INT8 Reserved2[2];
	INT8 Control;
} __attribute__ ((packed))  FORMAT_RBC, *PFORMAT_RBC;



// Read Command
typedef struct _READ_RBC {
	INT8 OperationCode;	/* 10H */
	INT8 VendorSpecific;
    union{
        struct
        {
	        INT8 LBA_3;
	        INT8 LBA_2;
	        INT8 LBA_1;
	        INT8 LBA_0;
        }__attribute__ ((packed))  LBA_W8 ;

       INT32 LBA_W32;
    } __attribute__ ((packed))   LBA;
	INT8 Reserved;
    INT8 XferLength_1;
	INT8 XferLength_0;
    INT8 Control;
} __attribute__ ((packed))  READ_RBC, *PREAD_RBC;



// Read Capacity Data - returned in Big Endian format
typedef struct _READ_CAPACITY_DATA {

    INT8 LBA_3;
    INT8 LBA_2;
    INT8 LBA_1;
    INT8 LBA_0;

    INT8 BlockLen_3;
    INT8 BlockLen_2;
    INT8 BlockLen_1;
    INT8 BlockLen_0;
} __attribute__ ((packed))  READ_CAPACITY_DATA, *PREAD_CAPACITY_DATA;


// Read Capacity command
typedef struct _READ_CAPACITY_RBC {
	INT8                OperationCode;	/* 10H */
    union   {
		FLEXI_INT32			l0[2];
        INT32               l[2];
        READ_CAPACITY_DATA  CapData;       /* Reserved area, here is used as temp*/
    } __attribute__ ((packed))  tmpVar;

	INT8                Control;
} __attribute__ ((packed))  READ_CAPACITY_RBC, *PREAD_CAPACITY_RBC;


// START_STOP_UNIT
typedef struct _START_STOP_RBC {
    INT8 OperationCode;    /*1BH*/
    INT8 Immediate: 1;
    INT8 Reserved1 : 7;
    INT8 Reserved2[2];
	union _START_STOP_FLAGS
    {
        struct
        {
            INT8 Start          : 1;
            INT8 LoadEject      : 1;
            INT8 Reserved3      : 2;
            INT8 PowerConditions: 4;
        }__attribute__ ((packed))  bits0;

        struct
        {
            INT8 MediumState    : 2;
            INT8 Reserved3      : 2;
            INT8 PowerConditions: 4;
        }__attribute__ ((packed))  bits1;
    }__attribute__ ((packed))  Flags;
    INT8 Control;
} __attribute__ ((packed))  START_STOP_RBC, *PSTART_STOP_RBC;


// Synchronize Cache
typedef struct _SYNCHRONIZE_CACHE_RBC {

	INT8 OperationCode;    /* 0x35 */
	INT8 Reserved[8];
	INT8 Control;

} __attribute__ ((packed))  SYNCHRONIZE_CACHE_RBC, *PSYNCHRONIZE_CACHE_RBC;


// Write Command
typedef struct _WRITE_RBC {
    INT8 OperationCode;	/* 2AH      */
    INT8 Reserved0 : 3;
	INT8 FUA : 1;
	INT8 Reserved1 : 4;
    union{
        struct
        {
	        INT8 LBA_3;
	        INT8 LBA_2;
	        INT8 LBA_1;
	        INT8 LBA_0;
        }__attribute__ ((packed))  LBA_W8 ;

       INT32 LBA_W32;
    } __attribute__ ((packed))   LBA;
	INT8 Reserved2;
    INT8 XferLength_1;
	INT8 XferLength_0;
    INT8 Control;
} __attribute__ ((packed))  WRITE_RBC, *PWRITE_RBC;


// VERIFY Command
typedef struct _VERIFY_RBC {
    INT8 OperationCode;	/* 2FH */
    INT8 Reserved0;
	INT8 LBA_3;			/* Big Endian */
	INT8 LBA_2;
	INT8 LBA_1;
	INT8 LBA_0;
	INT8 Reserved1;
    INT8 VerifyLength_1;		/* Big Endian */
	INT8 VerifyLength_0;
	INT8 Control;
} __attribute__ ((packed))  VERIFY_RBC, *PVERIFY_RBC;


/*************************************************************************/
// SPC-2 of SCSI-3 commands
/*************************************************************************/

// INQUIRY Command
typedef struct _INQUIRY_SPC {
	INT8 OperationCode;	/* 12H */
	INT8 EnableVPD:1 ;
    INT8 CmdSupportData:1 ;
	INT8 Reserved0:6 ;
	INT8 PageCode;
	INT8 Reserved1;
	INT8 AllocationLen;
    INT8 Control;
} __attribute__ ((packed))  INQUIRY_SPC, *PINQUIRY_SPC;


typedef struct _STD_INQUIRYDATA {
    INT8 DeviceType : 5;
    INT8 Reserved0 : 3;

    INT8 Reserved1 : 7;
    INT8 RemovableMedia : 1;

    INT8 Reserved2;

    INT8 Reserved3 : 5;
    INT8 NormACA : 1;
    INT8 Obsolete0 : 1;
    INT8 AERC : 1;

    INT8 Reserved4[3];

    INT8 SoftReset : 1;
    INT8 CommandQueue : 1;
	INT8 Reserved5 : 1;
	INT8 LinkedCommands : 1;
	INT8 Synchronous : 1;
	INT8 Wide16Bit : 1;
	INT8 Wide32Bit : 1;
	INT8 RelativeAddressing : 1;

	INT8 VendorId[8];

	INT8 ProductId[16];

	INT8 ProductRevisionLevel[4];

//  Above is 36 bytes
//  can be tranmitted by Bulk

    INT8 VendorSpecific[20];
    INT8 InfoUnitSupport : 1;
    INT8 QuickArbitSupport : 1;
    INT8 Clocking : 2;
    INT8 Reserved6 : 4;

    INT8  Reserved7 ;
    INT16 VersionDescriptor[8] ;

    INT8 Reserved8[22];
} __attribute__ ((packed))  STD_INQUIRYDATA, *PSTD_INQUIRYDATA;

typedef struct _SERIALNUMBER_PAGE {
    INT8 DeviceType : 5;
    INT8 DeviceTypeQualifier : 3;

    INT8 PageCode ;
    INT8 Reserved0 ;

    INT8 PageLength ;
    INT8 SerialNumber[24] ;

} __attribute__ ((packed)) VPD_SERIAL_PAGE,* PVPD_SERIAL_PAGE;

#define ASCII_ID_STRING 32
typedef struct _ID_DESCRIPTOR {
	INT8   CodeSet : 4;
	INT8   Reserved0 : 4;

	INT8   IDType : 4;
    INT8   Association : 2;
    INT8   Reserved1 : 2;

    INT8   Reserved2;

	INT8   IDLength ;
	INT8   AsciiID[ASCII_ID_STRING];
} __attribute__ ((packed))  ASCII_ID_DESCRIPTOR,* PASCII_ID_DESCRIPTOR;

typedef struct _DEVICE_ID_PAGE
{
    INT8 DeviceType : 5;
    INT8 DeviceTypeQualifier : 3;

    INT8 PageCode ;
    INT8 Reserved0 ;

    INT8 PageLength ;

    ASCII_ID_DESCRIPTOR   AsciiIdDescriptor[1];
} __attribute__ ((packed))  VPD_DEVICE_ID_PAGE, * PVPD_DEVICE_ID_PAGE;



// Mode Select
typedef struct _MODE_SELECT_SPC {
	INT8 OperationCode;	/* 15H */
	INT8 SavePage : 1 ;
	INT8 Reseved0 : 3 ;
	INT8 PageFormat : 1 ;
	INT8 Reserved1 : 3 ;
	INT8 Reserved2[2];
	INT8 ParameterLen;
	INT8 Control;
} __attribute__ ((packed))  MODE_SELECT_SPC, * PMODE_SELECT_SPC;

// Mode Sense
typedef struct _MODE_SENSE_SPC {
    INT8 OperationCode;	/* 1AH */
    INT8 Reseved0 : 3 ;
    INT8 DisableBlockDescriptor : 1 ;
    INT8 Reserved0 : 4 ;
    INT8 PageCode:6 ;
    INT8 PageControl : 2 ;
    INT8 Reserved1;
    INT8 ParameterLen;
    INT8 Control;
} __attribute__ ((packed))  MODE_SENSE_SPC, * PMODE_SENSE_SPC;

typedef struct _MODE_PARAMETER_HEAD {
    INT8 DataLen;
    INT8 MediumType;
    INT8 DeviceParameter;
    INT8 BlockDescriptorLen;
} __attribute__ ((packed))  MODE_PARAMETER_HEAD, * PMODE_PARAMETER_HEAD;


// Define Device Capabilities page.
typedef struct _MODE_RBC_DEVICE_PARAMETERS_PAGE {
    INT8 PageCode : 6;
	INT8 Reserved : 1;
    INT8 PageSavable : 1;
    INT8 PageLength;
    INT8 WriteCacheDisable : 1;
    INT8 Reserved1 : 7;
    INT8 LogicalBlockSize[2];
    INT8 NumberOfLogicalBlocks[5];
    INT8 PowerPerformance;
    INT8 Lockable : 1;
    INT8 Formattable : 1;
    INT8 Writable : 1;
    INT8 Readable : 1;
    INT8 Reserved2 : 4;
    INT8 Reserved3;
} __attribute__ ((packed)) MODE_RBC_DEVICE_PARAMETERS_PAGE, *PMODE_RBC_DEVICE_PARAMETERS_PAGE;


// prevent/allow medium removal
typedef struct _MEDIA_REMOVAL_SPC {
	INT8 OperationCode;    /* 1EH */
	INT8 Reserved0[3];
	INT8 Prevent:2 ;
	INT8 Reserved1:6 ;
	INT8 Control;
} __attribute__ ((packed))  MEDIA_REMOVAL_SPC, *PMEDIA_REMOVAL_SPC;


// Request Sense
typedef struct _REQUEST_SENSE_SPC {
    INT8 OperationCode;    /* 03H */
	INT8 Reserved[3];
    INT8 AllocationLen;
    INT8 Control;
} __attribute__ ((packed))  REQUEST_SENSE_SPC, *PREQUEST_SENSE_SPC;

typedef struct _REQUEST_SENSE_DATA {
    INT8 ResponseCode : 7;
    INT8 Valid : 1;

    INT8 SegmentNum;

    INT8 SenseKey : 4;
    INT8 Reserved0 : 1;
    INT8 WrongLenIndicator : 1;
    INT8 EndofMedium : 1;
    INT8 FileMark : 1;

    INT8 Info_0;
    INT8 Info_1;
    INT8 Info_2;
    INT8 Info_3;

    INT8 AdditionalSenseLen;

    INT8 CommandSpecInfo_0;
    INT8 CommandSpecInfo_1;
    INT8 CommandSpecInfo_2;
    INT8 CommandSpecInfo_3;

    INT8 ASC;
    INT8 ASCQ;
    INT8 FieldReplacableUnitCode;
    INT8 SenseKeySpec_0 : 7;
    INT8 SenseKeySpecValid : 1;
    INT8 SenseKeySpec_1;
    INT8 SenseKeySpec_2;

} __attribute__ ((packed))  REQUEST_SENSE_DATA, *PREQUEST_SENSE_DATA;


// Test Unit Ready
typedef struct _TEST_UNIT_SPC {
	INT8 OperationCode;    /* 00H */
	INT8 Reserved[4];
	INT8 Control;
} __attribute__ ((packed))  TEST_UNIT_SPC, *PTEST_UNIT_SPC;


// Write Buffer
typedef struct _WRITE_BUFFER_SPC {
    INT8 OperationCode;    /* 3BH */
    INT8 Mode:4 ;
    INT8 Reserved0:4 ;
	INT8 BufferID;
    INT8 BufferOff_2;
    INT8 BufferOff_1;
    INT8 BufferOff_0;
    INT8 ParameterLen_2;
    INT8 ParameterLen_1;
	INT8 ParameterLen_0;
    INT8 Control;
} __attribute__ ((packed))  WRITE_BUFFER_SPC, *PWRITE_BUFFER_SPC;

typedef union _CDB_RBC {
    GENERIC_CDB             Cdb_Generic;
    
    // RBC commands
    GENERIC_RBC             RbcCdb_Generic;

    FORMAT_RBC              RbcCdb_Format;
	READ_RBC                RbcCdb_Read;
    READ_CAPACITY_RBC       RbcCdb_ReadCapacity;
    START_STOP_RBC          RbcCdb_OnOffUnit;
    SYNCHRONIZE_CACHE_RBC   RbcCdb_SyncCache;
    VERIFY_RBC              RbcCdb_Verify;
    WRITE_RBC               RbcCdb_Write;

    
    // SPC-2 commands
    INQUIRY_SPC             SpcCdb_Inquiry;
    MODE_SELECT_SPC         SpcCdb_ModeSelect;
    MODE_SENSE_SPC          SpcCdb_ModeSense;
    MEDIA_REMOVAL_SPC       SpcCdb_Remove;
    REQUEST_SENSE_SPC       SpcCdb_RequestSense;
    TEST_UNIT_SPC           SpcCdb_TestUnit;
    WRITE_BUFFER_SPC        SpcCdb_WriteBuffer;


    // ATAPI Commands
    READ_10         CmdRead10;
    WRITE_10        CmdWrite10;
    MODE_SELECT_10  CmdModeSel10;
    MODE_SENSE_10   CmdModeSen10;

} __attribute__ ((packed))  CDB_RBC, *PCDB_RBC;


#endif

