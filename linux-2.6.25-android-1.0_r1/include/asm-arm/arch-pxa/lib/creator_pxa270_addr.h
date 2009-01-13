/*=============================================================================
// File Name : pxa270addr.h
// Function  : Xscale PXA270 Define Address Register
// Program   :
// Date      : 06/17/2004
// Version   : 1.00
// History
//   1.0.0 : Programming start (06/17/2004) -> SOP
============================================================================== */

#ifndef __CREATOR_PXA270_ADDR_H__
#define __CREATOR_PXA270_ADDR_H__


#ifndef __CREATOR_DEF_H__
#include "asm/arch/lib/def.h"
#endif

#ifndef __S3C2410_H
#include "asm/arch/creator-pxa270.h"
#endif


#include <asm/arch/creator-regs.h>

#define DSP_BASE_ADDR        ECS3_BASE
#define GRPS_BASE_ADDR       RCS3_BASE


#endif	//  __CREATOR_PXA270_ADDR_H__
