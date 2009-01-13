/*************************************************************************/   //
   //                  P H I L I P S   P R O P R I E T A R Y
   //
   //           COPYRIGHT (c)   1999 BY PHILIPS SINGAPORE.
   //                     --  ALL RIGHTS RESERVED  --
   //
   // File Name:	TPBulk.H
   // Author:		ZhenYu Zhang
   // Created:		Feb. 1, 1999
   // Modified:
   // Revision:		0.0
   //
/*************************************************************************/
   //
/*************************************************************************/

#ifndef __TPBULK_H__
#define __TPBULK_H__


#include "asm/arch/lib/usb/basictyp.h"
#include "asm/arch/lib/usb/rbccmd.h"


#define CSW_GOOD        0x00
#define CSW_FAIL        0x01
#define CSW_PHASE_ERROR 0x02

#ifdef  LITTLE_ENDIAN
#define CBW_SIGNATURE   0x43425355
#define CSW_SIGNATURE   0x53425355
#endif

#ifdef   BIG_ENDIAN
#define CBW_SIGNATURE   0x55534243
#define CSW_SIGNATURE   0x55534253
#endif

#define CBW_FLAG_IN     0x80

#define MAX_CDBLEN      0x10

typedef struct _COMMAND_BLOCK_WRAPPER{
    INT32   dCBW_Signature;
    INT32   dCBW_Tag;
    INT32   dCBW_DataXferLen;
    INT8    bCBW_Flag;
    INT8    bCBW_LUN;
    INT8    bCBW_CDBLen;
    CDB_RBC cdbRBC;
} __attribute__ ((packed)) CBW, *PCBW;

typedef struct _COMMAND_STATUS_WRAPPER{
    INT32   dCSW_Signature;
    INT32   dCSW_Tag;
    INT32   dCSW_DataResidue;
    INT8    bCSW_Status;
} __attribute__ ((packed)) CSW, *PCSW;

typedef union _TPBULK_STRUC {
    CBW     TPBulk_CommandBlock;
    CSW     TPBulk_CommandStatus;

} __attribute__ ((packed)) TPBLK_STRUC, * PTPBLK_STRUC;

/*************************************************************************/
// USB Class Request Functions
// and
// Public Functions 
/*************************************************************************/

// Host Device Disagreement Matrix
enum _HOST_DEV_DISAGREE {
CASEOK = 0,
CASE1,
CASE2,
CASE3,
CASE4,
CASE5,
CASE6,
CASE7,
CASE8,
CASE9,
CASE10,
CASE11,
CASE12,
CASE13,
CASECBW,
CASECMDFAIL
};



/*************************************************************************/
// C[ommand]D[ata]S[tatus] architecture for mass storage device over Bulk
// only Transport
/*************************************************************************/
void TPBulk_GetMaxLUN(void);
void TPBulk_ResetATA(void);


void TPBulk_CBWHandler( void );
void TPBulk_CSWHandler( void );


/*************************************************************************/
// Bulk Only Transport support functions
/*************************************************************************/

INT8 TPBulksup_ReadFrBOEP(INT8 Len);
INT8 TPBulksup_WriteToBIEP(INT8 Len);

BOOLEAN TPBulksup_IsCBWValid(void);
void TPBulksup_ErrorHandler(INT8 HostDevCase,INT16 wByteCounterDevWillXfer);

#endif
