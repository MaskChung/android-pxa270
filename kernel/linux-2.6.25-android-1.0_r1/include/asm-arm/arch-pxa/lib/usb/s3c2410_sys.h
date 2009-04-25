/*=============================================================================
// File Name : 2410addr.h
// Function  : S3C2410 Define Address Register
// Program   :
// Date      : 06/17/2004
// Version   : 1.00
// History
//   1.0.0 : Programming start (06/17/2004) -> SOP
============================================================================== */

#ifndef __S3C2410_SYS_H__
#define __S3C2410_SYS_H__


#ifndef __CREATOR_DEF_H__
#include "asm/arch/lib/def.h"
#endif

#ifndef __S3C2410_H
#include "asm/arch/s3c2410.h"
#endif


#ifdef __cplusplus
extern "C" {
#endif

#include "asm/arch/lib/creator_s3c2410_addr.h"


#define EnableInterrupt(x) 	rINTMSK &= (~x);
#define DisableInterrupt(x) rINTMSK |= (x);

/*============================================================================================
;Peripheral control registers
;============================================================================================*/

#define ATA_ADDR_BYTDATREG   (CF_TASK_BASE+0x0000) // DATA read/write 
#define ATA_ADDR_ERRFEAREG   (CF_TASK_BASE+0x0002) // read error/write feature 
#define ATA_ADDR_SECTCOUNT   (CF_TASK_BASE+0x0004) // sector count 
#define ATA_ADDR_SECTORNO    (CF_TASK_BASE+0x0006) // sector number 
#define ATA_ADDR_CYLINDLOW   (CF_TASK_BASE+0x0008) // cylinder low 
#define ATA_ADDR_CYLINDHI    (CF_TASK_BASE+0x000A) // cylinder high 
#define ATA_ADDR_SCARDHEAD   (CF_TASK_BASE+0x000C) // select card/head 
#define ATA_ADDR_STATCOMMD   (CF_TASK_BASE+0x000E) // read status/write command 
#define ATA_ADDR_CONTROL	 (CF_STATUS_BASE+0x000E) // write control 



#define ATAREG4OUT_DATA             ATA_ADDR_BYTDATREG
#define ATAREG4IN_DATA              ATA_ADDR_BYTDATREG
#define ATAREG4OUT_FEATURE          ATA_ADDR_ERRFEAREG
#define ATAREG4IN_ERROR             ATA_ADDR_ERRFEAREG
#define ATAREG4OUT_SECTOR_COUNT     ATA_ADDR_SECTCOUNT
#define ATAREG4IN_SECTOR_COUNT      ATA_ADDR_SECTCOUNT
#define ATAREG4OUT_SECTOR_NUMBER    ATA_ADDR_SECTORNO
#define ATAREG4IN_SECTOR_NUMBER     ATA_ADDR_SECTORNO
#define ATAREG4OUT_CYLINDER_LOW     ATA_ADDR_CYLINDLOW
#define ATAREG4IN_CYLINDER_LOW      ATA_ADDR_CYLINDLOW
#define ATAREG4OUT_CYLINDER_HIGH    ATA_ADDR_CYLINDHI
#define ATAREG4IN_CYLINDER_HIGH     ATA_ADDR_CYLINDHI
#define ATAREG4OUT_DEVICE_HEAD      ATA_ADDR_SCARDHEAD
#define ATAREG4IN_DEVICE_HEAD       ATA_ADDR_SCARDHEAD
#define ATAREG4OUT_COMMAND          ATA_ADDR_STATCOMMD
#define ATAREG4IN_STATUS			ATA_ADDR_STATCOMMD
#define ATAREG4OUT_CONTROL          ATA_ADDR_CONTROL
#define ATAREG4IN_ALTERNATE_STATUS  ATA_ADDR_STATCOMMD


#define ATARead2BWriteD12_1B        0x4002
#define ATARead0BWriteD12_2B        0x4004


#define RaiseIRQL()	        DisableInterrupt(BIT_EINT1)  
#define LowerIRQL()	        EnableInterrupt(BIT_EINT1)



#endif	//  __S3C2410_SYS_H__
