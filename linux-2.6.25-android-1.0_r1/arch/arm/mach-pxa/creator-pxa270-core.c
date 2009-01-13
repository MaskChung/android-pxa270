// --------------------------------------------------------------------
//	Directory  : linux/arch/arm/mach-creator-s3c2410/
//
//   Title     :  creator-s3c2410-core.c
//             :
//   Library   :
//             :
//   Developers:  MICROTIME MDS group
//             :
//   Purpose   :  Global variables
//             :
//   Limitation:
//             :
//   Note      :
//             :
// --------------------------------------------------------------------
//   modification history :
// --------------------------------------------------------------------
//   Version | mod. date: |
//   Vx.xx   | mm/dd/yyyy | 
//   V1.00   | 05/22/2006 | First release
// --------------------------------------------------------------------
//
// Note:
//
//       MICROTIME COMPUTER INC.
//
//
/*************************************************************************
Include files
*************************************************************************/
#include <linux/config.h>
#include <linux/kernel.h>
#include <linux/module.h>
#include <linux/init.h>
#include <linux/delay.h>

#include <asm/hardware.h>
#include <asm/io.h>
#include <asm/system.h>
#include <asm/mach-types.h>
#include "asm/arch/lib/creator_pxa270_core.h"


/*************************************************************************
Constant define
*************************************************************************/

/* ****** Debug Information ******************************************** */
//#define DEBUG
#ifdef DEBUG 
#define MSG(string, args...) printk("<1>" string, ##args)
#else   
#define MSG(string, args...)
#endif



/*************************************************************************
Function prototypes
*************************************************************************/


/*************************************************************************
Variable define
*************************************************************************/
creator_io_t creator_io ;
/* ************************************************************************** */


void 
creator_cf_reset (void)
{ 
        unsigned long flags;
            
        spin_lock_irqsave(&creator_io.creator_lock, flags);            
        creator_io.io_reg0 &= 0x7FFFF;		// Bit 15 : CF_nRST = 0
        IO_REG0 = creator_io.io_reg0;
        spin_unlock_irqrestore(&creator_io.creator_lock, flags);        
        
        
        mdelay(10);
        
        spin_lock_irqsave(&creator_io.creator_lock, flags);                 
        creator_io.io_reg0 |= 0x8000;		// Bit 15 : CF_nRST = 1
        IO_REG0 = creator_io.io_reg0;        
        spin_unlock_irqrestore(&creator_io.creator_lock, flags);        
        
        mdelay(500);       
}
/* ************************************************************************** */



int 
creator_pxa270_core_init (void)
{  
        unsigned long flags;
            
        spin_lock_init(&creator_io.creator_lock);    
        
        spin_lock_irqsave(&creator_io.creator_lock, flags);        
        creator_io.io_reg0 = 0xc000;
        IO_REG0 = creator_io.io_reg0;
        spin_unlock_irqrestore(&creator_io.creator_lock, flags);
        
        mdelay(1);

        spin_lock_irqsave(&creator_io.creator_lock, flags);  
        creator_io.cpld_ctrl = 0x3D;
        CPLD_CTRL = creator_io.cpld_ctrl;
        spin_unlock_irqrestore(&creator_io.creator_lock, flags);       

        creator_io.cf_reset = creator_cf_reset;
        creator_io.cf_reset();     
        return 0;

}
/* ************************************************************************** */



unsigned char
creator_GetCreatorCPLDVersion (void)
{      
       return (CPLD_STATUS >> 8);                           
}    
/* ************************************************************************** */


EXPORT_SYMBOL(creator_io);
EXPORT_SYMBOL(creator_pxa270_core_init);
EXPORT_SYMBOL(creator_cf_reset);
EXPORT_SYMBOL(creator_GetCreatorCPLDVersion);

__initcall(creator_pxa270_core_init);
