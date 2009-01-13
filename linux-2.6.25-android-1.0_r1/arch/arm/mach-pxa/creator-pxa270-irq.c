// --------------------------------------------------------------------
//
//   Title     :  creator-pxa270-irq.c
//             :
//   Library   :
//             :
//   Developers:  MICROTIME MDS group
//             :
//   Purpose   :  
//             :
//   Limitation:
//             :
//   Note      :
//             :
// --------------------------------------------------------------------
//   modification history :
// --------------------------------------------------------------------
//   Version| mod. date: |
//   Vx.xx  | mm/dd/yyyy |
//   V1.00  | 04/02/2006 | First release
//   V1.01  | 06/06/2006 | fixed bug that pending process.
// --------------------------------------------------------------------
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
#include <linux/init.h>
#include <linux/platform_device.h>
#include <linux/initrd.h>
#include <linux/device.h>
#include <linux/delay.h>
#include <linux/fb.h>
#include <linux/kdev_t.h>
#include <linux/root_dev.h>

#include <asm/setup.h>
#include <asm/memory.h>
#include <asm/mach-types.h>
#include <asm/hardware.h>
#include <asm/irq.h>

#include <asm/mach/arch.h>
#include <asm/mach/map.h>

#include <asm/arch/audio.h>
#include <asm/mach/irq.h>
#include <asm/arch/pxafb.h>
#include <asm/arch/mmc.h>
#include <asm/arch/creator-regs.h>
#include <asm/arch/lib/creator_pxa270_core.h>

#include "generic.h"

/*************************************************************************
Constant define
*************************************************************************/
//#define DEBUG
#ifdef DEBUG
#define MSG(string, args...) printk("<1>%s(), line=%d, " string, __FUNCTION__, __LINE__, ##args)
#else   
#define MSG(string, args...)
#endif



/*************************************************************************
Function prototypes
*************************************************************************/
unsigned char creator_GetCreatorCPLDVersion (void);

static void creator_ack_extirq3 (unsigned int irqno);
static void creator_mask_extirq3 (unsigned int irqno);
static void creator_unmask_extirq3 (unsigned int irqno);
static void creator_ExtIRQ3_demux (unsigned int irqno, struct irq_desc *desc);
//static void creator_ExtIRQ3_demux (unsigned int irqno, struct irq_desc *desc, struct pt_regs *regs);

static void creator_ack_irq_gpio1 (unsigned int irqno);
static void creator_mask_irq_gpio1 (unsigned int irqno);
static void creator_unmask_irq_gpio1 (unsigned int irqno);
static void creator_gpio1_demux (unsigned int irqno, struct irq_desc *desc);
//static void creator_gpio1_demux (unsigned int irqno, struct irq_desc *desc, struct pt_regs *regs);

static void creator_mask_OST_4_11_irq (unsigned int irqno);
static void creator_unmask_OST_4_11_irq (unsigned int irqno);
static void creator_OST_4_11_irq_handler (unsigned int irqno, struct irq_desc *desc);
//static void creator_OST_4_11_irq_handler (unsigned int irqno, struct irq_desc *desc, struct pt_regs *regs);


/*************************************************************************
Variable define
*************************************************************************/
static DEFINE_SPINLOCK(irq_controller_lock);


int 
creator_get_irq_extirq3 (unsigned int extirq3_subid)
{
        int     irqno ;
            
        if (creator_GetCreatorCPLDVersion() >= 0x14){
            // Creator Mather board VB.
            irqno = extirq3_subid;              
        }        
        else {
            unsigned long flags;                
            
            spin_lock_irqsave(&creator_io.creator_lock, flags);             
		    creator_io.cpld_ctrl |= 0x40;		//int3 = X_nINT
            CPLD_CTRL = creator_io.cpld_ctrl;  
            spin_unlock_irqrestore(&creator_io.creator_lock, flags);              
            
            irqno = CREATOR_IO_XIRQ3_IRQ;                            
        }    
        
        return (irqno);
}    
EXPORT_SYMBOL(creator_get_irq_extirq3);



static void 
creator_ack_extirq3 (unsigned int irqno)
{
/*
clear pending & unmask ExtIRQ3 :
mask : 1 : disable, 
       0 : Enable.
       
pend : 1 : has IRQ : 
           Once a PENDING bit is set by an edge event, the bit remains set until it is cleared by writing a
           one to the status bit. Writing a zero to a GEDR status bit has no effect.
       0 : no IRQ        
*/    
        unsigned short bitval   ;      
       
        MSG("EXT3IRQ Ack irqno=%d\n", irqno) ;         
        bitval = 1 << (irqno - CREATOR_IO_XIRQ3_EXT_CF_IRQ);
        /* the irq can be acknowledged only if deasserted, so it's done here */                 
        MASTER_IRQ3_PEND = (bitval);                                 
}



static void 
creator_mask_extirq3 (unsigned int irqno)
{
/*
mask ExtIRQ3 :
mask : 1 : disable, 
       0 : Enable.
       
pend : 1 : has IRQ : 
           Once a PENDING bit is set by an edge event, the bit remains set until it is cleared by writing a
           one to the status bit. Writing a zero to a GEDR status bit has no effect.
       0 : no IRQ        
*/    
        unsigned short  bitval, mask ;
       
        MSG("EXT3IRQ Mask irq=%d\n", irqno) ;          
        bitval = (1 <<(irqno - CREATOR_IO_XIRQ3_EXT_CF_IRQ));        

        mask = MASTER_IRQ3_MASK;
        mask |= bitval;
        MASTER_IRQ3_MASK = mask;                                
}



static void 
creator_unmask_extirq3 (unsigned int irqno)
{
/*
unmask ExtIRQ3 :
mask : 1 : disable, 
       0 : Enable.
       
pend : 1 : has IRQ : 
           Once a PENDING bit is set by an edge event, the bit remains set until it is cleared by writing a
           one to the status bit. Writing a zero to a GEDR status bit has no effect.
       0 : no IRQ        
*/    
        unsigned short bitval, mask;
        MSG("EXT3IRQ unmask  irq=%d\n", irqno) ;     
             
        /* the irq can be acknowledged only if deasserted, so it's done here */
        bitval = (1 << (irqno - CREATOR_IO_XIRQ3_EXT_CF_IRQ));  
                                     
        mask = MASTER_IRQ3_MASK;           
        mask &= (~bitval);
        MASTER_IRQ3_MASK = mask;                                  
        creator_unmask_irq_gpio1(CREATOR_IO_XIRQ3_IRQ);  
}



static struct irq_chip creator_ExtIRQ3_edge_chip = {
	.ack		= creator_ack_extirq3,
	.mask		= creator_mask_extirq3,
	.unmask		= creator_unmask_extirq3,
};



static void 
creator_ExtIRQ3_demux (unsigned int irqno, struct irq_desc *desc)
//creator_ExtIRQ3_demux (unsigned int irqno, struct irq_desc *desc, struct pt_regs *regs)
{
        unsigned long   pending;      
        
        MSG("ExtIRQ3, irq=%d, MASTER_IRQ3_MASK=%x, MASTER_IRQ3_PEND=%x\n", irqno, MASTER_IRQ3_MASK, MASTER_IRQ3_PEND);                   
        pending = (unsigned char)(MASTER_IRQ3_PEND & (~MASTER_IRQ3_MASK)) ;          
        do {
            MASTER_INTPEND1 = (1<<(CREATOR_IO_XIRQ3_IRQ - CREATOR_IRQ(0)));            
            if (pending){                                
                irqno = CREATOR_IO_XIRQ3_EXT_CF_IRQ;
                desc = irq_desc + irqno;        
                do {
                    if (pending & 1){
                        MSG("irq=%d\n", irqno);
//                        desc_handle_irq(irqno, desc, regs);                                  
                        desc_handle_irq(irqno, desc);
                    }    
                    irqno++;
                    desc++;
                    pending >>= 1;
                } while (pending);  
                pending = (unsigned char)(MASTER_IRQ3_PEND & (~MASTER_IRQ3_MASK)) ;                    
            }   
        }while (pending) ;  
}



static void 
creator_ack_irq_gpio1 (unsigned int irqno)
{
/*
Enable IRQ
mask : 1 : disable, 
       0 : Enable.
       
pend : 1 : has IRQ : 
           Once a PENDING bit is set by an edge event, the bit remains set until it is cleared by writing a
           one to the status bit. Writing a zero to a GEDR status bit has no effect.
       0 : no IRQ           
*/    
        unsigned short   bitval ;  
       
        if (irqno < CREATOR_CFI_IRQ){
            /* the irq can be acknowledged only if deasserted, so it's done here */
            bitval = (1 <<(irqno - CREATOR_IRQ(0)));                                 
            MASTER_INTPEND1 = (bitval);            
        }
        else{
            bitval = (1 <<(irqno - CREATOR_IRQ(8)));                                 
            MASTER_INTPEND2 = (bitval);                                   
        }                 
}



static void 
creator_mask_irq_gpio1 (unsigned int irqno)
{
/*
mask : 1 : disable, 
       0 : Enable.
       
pend : 1 : has IRQ : 
           Once a PENDING bit is set by an edge event, the bit remains set until it is cleared by writing a
           one to the status bit. Writing a zero to a GEDR status bit has no effect.
       0 : no IRQ           
*/    
        unsigned short bitval, mask ;
 
        MSG("Mask irq=%d\n", irqno) ;         

        if (irqno < CREATOR_CFI_IRQ){
            bitval = 1 << (irqno - CREATOR_IRQ(0)); 
            mask = MASTER_INTMASK1;
            mask |= bitval;
            MASTER_INTMASK1 = mask;            
        } 
        else{
            bitval = 1 << (irqno - CREATOR_IRQ(8)); 
            mask = MASTER_INTMASK2;
            mask |= bitval;
            MASTER_INTMASK2 = mask;                           
        }                  
}



static void 
creator_unmask_irq_gpio1 (unsigned int irqno)
{
/*
unmask IRQ :
mask : 1 : disable, 
       0 : Enable.
       
pend : 1 : has IRQ : 
           Once a PENDING bit is set by an edge event, the bit remains set until it is cleared by writing a
           one to the status bit. Writing a zero to a GEDR status bit has no effect.
       0 : no IRQ     
*/    
        unsigned short bitval, mask;
   
        MSG("unMask irq=%d\n", irqno) ;         
        if (irqno < CREATOR_CFI_IRQ){
            /* the irq can be acknowledged only if deasserted, so it's done here */
            bitval = (1 <<(irqno - CREATOR_IRQ(0)));                                           
            mask = MASTER_INTMASK1;           
            mask &= (~bitval);
            MASTER_INTMASK1 = mask;
        }
        else{
            bitval = (1 <<(irqno - CREATOR_IRQ(8)));                                            
            mask = MASTER_INTMASK2;           
            mask &= (~bitval);
            MASTER_INTMASK2 = mask;                          
        }                                                 
}



static struct irq_chip creator_irq_edge_chip = {
	.ack		= creator_ack_irq_gpio1,	
	.mask		= creator_mask_irq_gpio1,
	.unmask		= creator_unmask_irq_gpio1,
};



static void 
creator_gpio1_demux (unsigned int irqno, struct irq_desc *desc)
//creator_gpio1_demux (unsigned int irqno, struct irq_desc *desc, struct pt_regs *regs)
{        
        unsigned long  pending;         
                 
        MSG("Entry GPIO1 irqno=%d\n", irqno);                      
        pending =  (((MASTER_INTPEND2 & (~MASTER_INTMASK2)) & 0xff)<<8)+ ((MASTER_INTPEND1 & (~MASTER_INTMASK1)) & 0xff);                        
        do {       ;       
            GEDR0 = GPIO_bit(1);  /* clear useless edge notification */           
            if (pending){                 
                irqno = CREATOR_IRQ(0);
                desc = irq_desc + irqno;                              
                do {
                    if (pending & 1){
                        MSG("irqno=%d\n", irqno);
                        desc_handle_irq(irqno, desc);
                        //desc_handle_irq(irqno, desc, regs);                                         
                    }    
                    irqno++;
                    desc++;
                    pending >>= 1;
                } while (pending);                    
                pending =  (((MASTER_INTPEND2 & (~MASTER_INTMASK2)) & 0xff)<<8)+ ((MASTER_INTPEND1 & (~MASTER_INTMASK1)) & 0xff);               
            }         
        }while (pending) ;                        
}             



static void 
creator_mask_OST_4_11_irq (unsigned int irqno)
{
        int ost_irq = (irqno - CREATOR_OST_4_IRQ) + 4;
	    unsigned long flags;    
      
        spin_lock_irqsave(&irq_controller_lock, flags);                
        OIER &= ~(1 << ost_irq);               
	    spin_unlock_irqrestore(&irq_controller_lock, flags);       
}



static void 
creator_unmask_OST_4_11_irq (unsigned int irqno)
{      
        int ost_irq = (irqno - CREATOR_OST_4_IRQ) + 4;
	    unsigned long flags;       

        /* the irq can be acknowledged only if deasserted, so it's done here */
        spin_lock_irqsave(&irq_controller_lock, flags);     
       
        OSSR = (1 << ost_irq);              
        OIER |= (1 << ost_irq);            

	    spin_unlock_irqrestore(&irq_controller_lock, flags);           
}



static struct irq_chip creator_OST_4_11_irq = {
	.ack		= creator_mask_OST_4_11_irq,
	.mask		= creator_mask_OST_4_11_irq,
	.unmask		= creator_unmask_OST_4_11_irq,
};



static void 
creator_OST_4_11_irq_handler (unsigned int irqno, struct irq_desc *desc)
//creator_OST_4_11_irq_handler (unsigned int irqno, struct irq_desc *desc, struct pt_regs *regs)

{       
        unsigned long  pending;         

        pending =  ((OSSR & 0xFFF)>> 4);                 
        do {           
		    if (likely(pending)) { 
		        irqno = CREATOR_OST_4_IRQ + __ffs(pending);
                desc = irq_desc + irqno;
                desc_handle_irq(irqno, desc);
                //desc_handle_irq(irqno, desc, regs);	
                return ;	                           
            }    
            pending =  ((OSSR & 0xFFF)>> 4);
        } while (pending);                         
}
       


void  
creator_pxa270_init_irq (void)
{        
        int irqno;	              
printk(KERN_ALERT " ------ into creator_pxa270_init_irq\n");        
        /* setup extra creat_pxa270 irqs */
        /* set_irq_type has to be called before an irq can be requested */
        pxa27x_init_irq();

        set_irq_type(CREATOR_ETH_IRQ, IRQT_RISING);       
        
        // Extend XINT3
        if (creator_GetCreatorCPLDVersion() >= 0x14){
            MSG("CPLD Version=%x\n", creator_GetCreatorCPLDVersion());
            // Creator Board XINTREQ map.
            // IRQ SELECT :
            //  bit  2-0  : IRQ0_MUX : PHY_nINT(U19)
            //  bit  5-3  : IRQ1_MUX : USB_nINT(U25)
            //  bit  8-6  : IRQ2_MUX : CODEC_INT(U21)
            //  bit 11-9  : IRQ3_MUX : SubXINT3
            //  bit 12    : INKCF_1  : 0 : Normal Mode, 1 : invert CF_IRQ signal
            //  bit 13    : IRQ3_MODE: 0 : Old Mode. 1 : User 1.2 Mode.
            //  bit 14    : IRQ0_MODE: 0 : XINTREQ(0) map to ethernet of creator board.
            //                         1 : XINTREQ(0) map to CF of creator board.
            //  bit 15    : IRQ_DEFAULT : 0 : Use V1.0 INT 
            //                            1 : Use V1.2 INT       
            IRQ_SELECT = (1<<15) + (1<<13) + (0<<12) + (2 << 6) + (1<<3) + (0) ;                 

            for(irqno = CREATOR_IO_XIRQ3_EXT_CF_IRQ; irqno <= CREATOR_IO_XIRQ3_EXT_CCD_IRQ; irqno++) {            
                set_irq_chip(irqno, &creator_ExtIRQ3_edge_chip);
                //set_irq_handler(irqno, do_edge_IRQ);
                set_irq_handler(irqno, handle_edge_irq);
                set_irq_flags(irqno, 0);       // disable               
            }                                                   
            set_irq_flags(CREATOR_IO_XIRQ3_EXT_CF_IRQ, IRQF_VALID);       // enable ExtIRQ3_CF 
            set_irq_flags(CREATOR_IO_XIRQ3_EXT_SLAVE_IRQ, IRQF_VALID);    // enable ExtIRQ3_Slave IRQ                           
             
        } 
        else{
            MSG("CPLD Version=%x\n", creator_GetCreatorCPLDVersion());            
        }           
        

        // GPIO1 : 16 Interrupts
        //       
        for (irqno = CREATOR_IRQ(0); irqno <= CREATOR_IRQ(15); irqno++) {            
            switch (irqno){
            default :    
                set_irq_chip(irqno, &creator_irq_edge_chip);                            
//                set_irq_handler(irqno, do_edge_IRQ);                                   
                set_irq_handler(irqno, handle_edge_irq);                                   
                set_irq_flags(irqno, 0);       // disable                     
            }             
        }        
        
        set_irq_flags(CREATOR_TOUCH_IRQ,  IRQF_VALID | IRQF_PROBE);          
        set_irq_flags(CREATOR_IO_XIRQ2_IRQ,  IRQF_VALID);       // Codec         
        set_irq_flags(CREATOR_IO_XIRQ3_IRQ,  IRQF_VALID);       // DSP     
        set_irq_flags(CREATOR_MMC_CD_IRQ,  IRQF_VALID);         
        set_irq_flags(CREATOR_CFI_IRQ,  IRQF_VALID);         
        set_irq_flags(CREATOR_CFO_IRQ,  IRQF_VALID);           
        set_irq_flags(CREATOR_CF_IRQ,  IRQF_VALID);               
        
        MASTER_IRQ3_MASK = ~(0);              
        MASTER_INTMASK1 = ~(0);
        MASTER_INTMASK2 = ~(0);  
        
        MASTER_IRQ3_PEND = ~0;                  
        MASTER_INTPEND1 = ~0;
        MASTER_INTPEND2 = ~0;         
          

        if (creator_GetCreatorCPLDVersion() >= 0x14){        
            set_irq_chained_handler(CREATOR_IO_XIRQ3_IRQ, creator_ExtIRQ3_demux);               
        }
        
        MSG("IRQ_GPIO chain to creator_gpio1_demux\n");
        set_irq_chained_handler(IRQ_GPIO(1), creator_gpio1_demux);
        set_irq_type(IRQ_GPIO(1), IRQT_FALLING);           
                

        // Timer4-11 Interrupts 
        //
        for(irqno = CREATOR_OST_4_IRQ; irqno <= CREATOR_OST_11_IRQ; irqno++) {            
            set_irq_chip(irqno, &creator_OST_4_11_irq);
            set_irq_handler(irqno, handle_level_irq);        
            //set_irq_handler(irqno, do_level_IRQ);        
            set_irq_flags(irqno, IRQF_VALID);       // enable               
        }                       
        set_irq_chained_handler(IRQ_OST_4_11, creator_OST_4_11_irq_handler);                                                               
}

