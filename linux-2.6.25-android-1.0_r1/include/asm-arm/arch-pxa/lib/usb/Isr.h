   /*************************************************************************/
   //
   //                  P H I L I P S   P R O P R I E T A R Y
   //
   //           COPYRIGHT (c)   1999 BY PHILIPS SINGAPORE.
   //                     --  ALL RIGHTS RESERVED  --
   //
   // File Name:	Hal4sys.H
   // Author:       Hilbert Zhang ZhenYu
   // Created:		Nov. 26 99
   // Modified:
   // Revision:		0.0
   //
   /*************************************************************************/
   //
   /*************************************************************************/
#ifndef __ISR_H__
#define __ISR_H__

//#include    	"asm/arch/lib/creator_s3c2410_addr.h"







void ISR_struct_init(void);
void Init_D12(void);
void close_D12(void);

void Init_timer(void);
void Close_timer(void);


#endif
