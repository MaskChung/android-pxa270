/*=============================================================================
// File Name : creator-pxa270-core.h
// Function  : creator I/O variable
// Program   :
// Date      : 02/14/2006
// Version   : 1.00
// History
============================================================================== */

#ifndef __CREATOR_PXA270_CORE_H__
#define __CREATOR_CORE_H__


#ifndef __CREATOR_DEF_H__
#include "asm/arch/lib/def.h"
#endif

//#ifndef __S3C2410_H
#include "asm/arch/creator-pxa270.h"
//#endif

#ifndef _CREATOR_PXA270_ADDR_H
#include <asm/arch/lib/creator_pxa270_addr.h>
#endif


#ifdef __cplusplus
extern "C" {
#endif


void creator_cf_reset(void);

typedef	void (creator_cf_reset_proc)(void);

typedef struct creator_io_s {
	unsigned short cpld_ctrl;
	unsigned short io_reg0;
	creator_cf_reset_proc *cf_reset;
	
	spinlock_t creator_lock ;
} creator_io_t ;

extern creator_io_t creator_io ;



void creator_cf_reset (void);
unsigned char creator_GetCreatorCPLDVersion (void);

/* ****** irq Information *********************************************** */
// arch/arm/mach-pxa/creator-pxa270-irq.c
int  creator_get_irq_extirq3 (unsigned int extirq3_subid) ;



#ifdef __cplusplus
}
#endif

#endif	//  __CREATOR_PXA270_CORE_H__

