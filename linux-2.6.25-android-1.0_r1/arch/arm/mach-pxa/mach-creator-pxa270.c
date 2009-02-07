// --------------------------------------------------------------------
//
//   Title     :  mach-creator-pxa270.c
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
//   V1.00  | 05/29/2006 | First release
//   V1.01  | 05/08/2007 | support MTLCD-0353224A (320*240 Landscape)
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
#include <linux/mtd/mtd.h>
#include <linux/mtd/partitions.h>
#include <linux/root_dev.h>

#include <asm/setup.h>
#include <asm/memory.h>
#include <asm/mach-types.h>
#include <asm/hardware.h>
#include <asm/irq.h>
#include <asm/sizes.h>

#include <asm/mach/arch.h>
#include <asm/mach/map.h>

#include <asm/arch/creator-pxa270.h>
#include <asm/arch/audio.h>
#include <asm/mach/flash.h>
#include <asm/mach/irq.h>
#include <asm/arch/pxafb.h>
#include <asm/arch/mmc.h>
#include <asm/arch/creator-regs.h>


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


/*************************************************************************
Variable define
*************************************************************************/



static int 
creator_pxa270_mst_audio_startup (struct snd_pcm_substream *substream, void *priv)
{  
        volatile unsigned int *ptrAddr ;
     
        ptrAddr = (unsigned int*)(0xf2500200 + (2<<1));
        *ptrAddr = 0x0808 ;
        udelay(100);     
       
        ptrAddr = (unsigned int*)(0xf2500200 + (0x6a<<1));
        *ptrAddr = 0x40 ;       
        udelay(100);	
	
        return (0);
}

static void 
creator_pxa270_mst_audio_shutdown (struct snd_pcm_substream *substream, void *priv)
{	
}

static void 
creator_pxa270_mst_audio_suspend (void *priv)
{
}

static void 
creator_pxa270_mst_audio_resume (void *priv)
{
}

static pxa2xx_audio_ops_t creator_pxa270_mst_audio_ops = {
	.startup	= creator_pxa270_mst_audio_startup,
	.shutdown	= creator_pxa270_mst_audio_shutdown,
	.suspend	= creator_pxa270_mst_audio_suspend,
	.resume		= creator_pxa270_mst_audio_resume,
};

static struct platform_device creator_pxa270_mst_audio_device = {
	.name		= "pxa2xx-ac97",
	.id		= -1,
	.dev		= { .platform_data = &creator_pxa270_mst_audio_ops },
};

static void 
create_pxa270_TFTLCD_power (int on, struct fb_var_screeninfo *var)
{ 
        if (on)
           MASTER_CTRL1 = MASTER_CTRL1 | 0x20;
        else    
           MASTER_CTRL1 = MASTER_CTRL1 & 0xdf;                     
}


static u64 fb_dma_mask = ~(u64)0;

/**
 * Set lcd on or off
 **/
static struct pxafb_mode_info LTS280Q1_modes[] = {
	[0] = {
	.pixclock		= 156250,
	.xres			= 240,
	.yres			= 320,
	.bpp			= 16,
	.hsync_len		= 60,
	.left_margin	= 10,
	.right_margin	= 10,
	.vsync_len		= 13,
	.upper_margin	= 0,
	.lower_margin	= 0,
	.sync			= 0,

	},
};
static struct pxafb_mach_info LTS280Q1  = {
	.modes = LTS280Q1_modes,
	.num_modes = 1,
/*
	.pixclock		= 156250,
	.xres			= 240,
	.yres			= 320,
	.bpp			= 16,
	.hsync_len		= 60,
	.left_margin	= 10,
	.right_margin	= 10,
	.vsync_len		= 13,
	.upper_margin	= 0,
	.lower_margin	= 0,
	.sync			= 0,
	*/
	.lccr0			= LCCR0_Act,
	.lccr3			= LCCR3_PCP,
	.pxafb_lcd_power = create_pxa270_TFTLCD_power,
};


static struct pxafb_mode_info TD035STEB1_modes []  = {
[0] = {
	.pixclock		= 156250,
	.xres			= 240,
	.yres			= 320,
	.bpp			= 16,
	.hsync_len		= 60,
	.left_margin	= 12,
	.right_margin	= 12,
	.vsync_len		= 13,
	.upper_margin	= 0,
	.lower_margin	= 0,
	.sync			= 0,

},
};

static struct pxafb_mach_info TD035STEB1  = {
	.modes = TD035STEB1_modes,
	.num_modes = 1,
/*
	.pixclock		= 156250,
	.xres			= 240,
	.yres			= 320,
	.bpp			= 16,
	.hsync_len		= 60,
	.left_margin	= 12,
	.right_margin	= 12,
	.vsync_len		= 13,
	.upper_margin	= 0,
	.lower_margin	= 0,
	.sync			= 0,
	*/
	.lccr0			= LCCR0_Act,
	.lccr3			= LCCR3_PCP,
	.pxafb_lcd_power = create_pxa270_TFTLCD_power,
};


static struct pxafb_mode_info HX8218A_modes [] = {
[0] = {
	.pixclock		= 156250,
	.xres			= 320,
	.yres			= 240,
	.bpp			= 16,
	.hsync_len		= 30,
	.left_margin	=  2,
	.right_margin	=  2,
	.vsync_len		=  3,
	.upper_margin	= 1,
	.lower_margin	= 1,
	.sync			= 0,
	},
};

static struct pxafb_mach_info HX8218A  = {
	.modes = HX8218A_modes,
	.num_modes = 1,
	/*
	.pixclock		= 156250,
	.xres			= 320,
	.yres			= 240,
	.bpp			= 16,
	.hsync_len		= 30,
	.left_margin	=  2,
	.right_margin	=  2,
	.vsync_len		=  3,
	.upper_margin	= 1,
	.lower_margin	= 1,
	.sync			= 0,
	*/
	.lccr0			= LCCR0_Act,
	.lccr3			= 0,
	.pxafb_lcd_power = create_pxa270_TFTLCD_power,
};


static struct pxafb_mode_info NL6448BC33_modes [] = {
[0] = {
	.pixclock		= 39682,
	.xres			= 640,
	.yres			= 480,
	.bpp			= 16,
	.hsync_len		= 60,
	.left_margin	= 10,
	.right_margin	= 10,
	.vsync_len		= 13,
	.upper_margin	= 0,
	.lower_margin	= 0,
	.sync			= 0,
	},
};

static struct pxafb_mach_info NL6448BC33  = {
	.modes = NL6448BC33_modes,
	.num_modes = 1,
/*
	.pixclock		= 39682,
	.xres			= 640,
	.yres			= 480,
	.bpp			= 16,
	.hsync_len		= 60,
	.left_margin	= 10,
	.right_margin	= 10,
	.vsync_len		= 13,
	.upper_margin	= 0,
	.lower_margin	= 0,
	.sync			= 0,
	*/
	.lccr0			= LCCR0_Act,
	.lccr3			= LCCR3_PCP,
	.pxafb_lcd_power = create_pxa270_TFTLCD_power,
};


static struct platform_device pxafb_device_mtlcd_0283224  = {
	.name		= "pxa2xx-fb-0283224",
	.id		= -1,
	.dev		= {
 		.platform_data	= &LTS280Q1,
		.dma_mask	= &fb_dma_mask,
		.coherent_dma_mask = 0xffffffff,
	},
};

static struct platform_device pxafb_device_mtlcd_0353224 = {
	.name		= "pxa2xx-fb-0353224",
	.id		= -1,
	.dev		= {
 		.platform_data	= &TD035STEB1,
		.dma_mask	= &fb_dma_mask,
		.coherent_dma_mask = 0xffffffff,
	},
};

static struct platform_device pxafb_device_mtlcd_1046448 = {
	.name		= "pxa2xx-fb-1046448A",
	.id		= -1,
	.dev		= {
 		.platform_data	= &NL6448BC33,
		.dma_mask	= &fb_dma_mask,
		.coherent_dma_mask = 0xffffffff,
	},
};

static struct platform_device pxafb_device_mtlcd_0353224A = {
	.name		= "pxa2xx-fb-0353224A",
	.id		= -1,
	.dev		= {
 		.platform_data	= &HX8218A,
		.dma_mask	= &fb_dma_mask,
		.coherent_dma_mask = 0xffffffff,
	},
};


static struct resource creator_pxa270_smc91x_resources[] = {
	[0] = {
		.start	= (CREATOR_ETH_PHYS + 0x300),
		.end	= (CREATOR_ETH_PHYS + CREATOR_ETH_SIZE),
		.flags	= IORESOURCE_MEM,
	},
	[1] = {
		.start	= CREATOR_ETH_IRQ,
		.end	= CREATOR_ETH_IRQ,
		.flags	= IORESOURCE_IRQ,
	}
};



static struct platform_device creator_pxa270_smc91x_device = {
	.name		= "smc91x",
	.id		= 0,
	.num_resources	= ARRAY_SIZE(creator_pxa270_smc91x_resources),
	.resource	= creator_pxa270_smc91x_resources,
};



static struct pxamci_platform_data creator_pxa270_mci_platform_data ;
/*
 * MMC/SD Device
 *
 */
static int 
creator_pxa270_mci_init (struct device *dev, irqreturn_t (*detect_int)(int, void *), void *data)
//creator_pxa270_mci_init (struct device *dev, irqreturn_t (*detect_int)(int, void *, struct pt_regs *), void *data)
{
       int err;

       printk("Create XScale-PXA270 MMC/SD setup ");
       /* setup GPIO for PXA25x MMC controller	*/
       pxa_gpio_mode(GPIO_MMCCLK_AF);
       pxa_gpio_mode(GPIO_MMCCMD_AF);
       pxa_gpio_mode(GPIO_MMCDAT0_AF);
       pxa_gpio_mode(GPIO_MMCDAT1_AF);
       pxa_gpio_mode(GPIO_MMCDAT2_AF);
       pxa_gpio_mode(GPIO_MMCDAT3_AF);

       //creator_pxa270_mci_platform_data.detect_delay = msecs_to_jiffies(250);
       err = request_irq(CREATOR_MMC_CD_IRQ, detect_int, IRQF_SHARED, "MMC card detect", data);
       //err = request_irq(CREATOR_MMC_CD_IRQ, detect_int, SA_INTERRUPT, "MMC card detect", data);
       if (err) {
           printk(KERN_ERR "creator_pxa270_mci_init: MMC/SD: can't request MMC card detect IRQ\n");
           return -1;
       }

       printk("done.\n");
       return (0);
}



static void 
creator_pxa270_mci_exit (struct device *dev, void *data)
{
	    free_irq(CREATOR_MMC_CD_IRQ, data);
}



static struct pxamci_platform_data creator_pxa270_mci_platform_data = {
	.ocr_mask	= MMC_VDD_32_33|MMC_VDD_33_34,
	.init 		= creator_pxa270_mci_init,
	.exit       = creator_pxa270_mci_exit,	
};



static struct mtd_partition creator_pxa270_partitions[] = {
	{
		name:		"Bootloader",
		offset:		0,		
		size:		0x00040000,      /* 256K U-Boot and 256K config params */
		mask_flags:	MTD_WRITEABLE,   /* force read-only */
	},{
		name:		"Diag",
		offset:		0x00040000,		
		size:		0x000C0000,      /* 768k Diag program */
		mask_flags:	MTD_WRITEABLE,   /* force read-only */
	},{	    
		name:		"Kernel",
		offset:		0x00100000,		 
		size:		0x00380000,      /* 3.5M for kernel */
       mask_flags:  MTD_WRITEABLE	 /* force read-only */	
	},{
		name:		"Filesystem",
		offset:		0x00480000,
//      size:		MTDPART_SIZ_FULL,
  		size:		0x00F00000,		 /* 15M for rootfs */	
	}
};



static struct flash_platform_data creator_pxa270_flash_data = {
	.map_name	= "cfi_probe",
	.width		= 2,
	.parts		= creator_pxa270_partitions,
	.nr_parts	= ARRAY_SIZE(creator_pxa270_partitions),	
};



static struct resource creator_pxa270_flash_resource = {
	.start		= PXA_CS0_PHYS,
	.end		= PXA_CS0_PHYS + SZ_32M - 1,
	.flags		= IORESOURCE_MEM,
};



static struct platform_device creator_pxa270_cfi_flash_device = {
	.name		= "creator_pxa270_flash",
	.id		= 0,
	.dev		= {
		.platform_data	= &creator_pxa270_flash_data,
	},
	.num_resources	= 1,
	.resource	= &creator_pxa270_flash_resource,
};


static struct platform_device *devices[] __initdata = {
	&creator_pxa270_smc91x_device,
	&creator_pxa270_mst_audio_device,
	&creator_pxa270_cfi_flash_device,
	/*
	&pxafb_device_mtlcd_0283224,
	&pxafb_device_mtlcd_0353224,
	&pxafb_device_mtlcd_1046448,
	&pxafb_device_mtlcd_0353224A,	
	*/
};



static void __init 
creator_pxa270_init (void)
{
       /* reset UCB1400 */
       GPSR3 &= ~(1u << (113-96));
       pxa_gpio_mode(GPIO113_AC97_RESET_N_MD);               
       udelay(12);

       pxa_set_mci_info(&creator_pxa270_mci_platform_data);
       
       
#ifdef  CONFIG_MTLCD_0283224
       set_pxa_fb_info(&LTS280Q1);          
#endif

#ifdef  CONFIG_MTLCD_0353224
       set_pxa_fb_info(&TD035STEB1);          
#endif

#ifdef  CONFIG_MTLCD_0353224A
       set_pxa_fb_info(&HX8218A);          
#endif

#ifdef  CONFIG_MTLCD_1046448
       set_pxa_fb_info(&NL6448BC33);          
#endif
	   (void) platform_add_devices(devices, ARRAY_SIZE(devices));            
}



static void __init
fixup_creator_pxa270 (struct machine_desc *desc, struct tag *tags, char **cmdline, struct meminfo *mi)
{
       SET_BANK (0, 0xa0000000, 64*1024*1024);
       mi->nr_banks      = 1;
	
#ifdef CONFIG_BLK_DEV_INITRD
       if (initrd_start)
           ROOT_DEV = Root_RAM0;
#endif	

#ifdef CONFIG_BLK_DEV_INITRD
		initrd_start = __phys_to_virt(RAMDISK_DN_ADDR);
		initrd_end = initrd_start + CONFIG_BLK_DEV_RAM_SIZE;
#endif			
}



static struct map_desc creator_pxa270_io_desc[] __initdata = {
 /* virtual         physical            length              type */
  	{	/* Ethernet */
		.virtual	=  CREATOR_ETH_VIRT,
		.pfn		= __phys_to_pfn(CREATOR_ETH_PHYS),
		.length		= CREATOR_ETH_SIZE,
		.type		= MT_DEVICE
	}, {	/* Creator Mainboard I/O */
		.virtual	=  MASTER_ECS0_VIRT,
		.pfn		= __phys_to_pfn(MASTER_ECS0_PHYS),
		.length		= MASTER_ECS0_SIZE,
		.type		= MT_DEVICE
	}, {	/* Creator Mainboard Flash */
		.virtual	=  MASTER_RCS0_VIRT,
		.pfn		= __phys_to_pfn(MASTER_RCS0_PHYS),
		.length		= MASTER_RCS0_SIZE,
		.type		= MT_DEVICE
	}, {	/* Slave bus : FPGA I/O */
		.virtual	=  MASTER_ECS3_VIRT,
		.pfn		= __phys_to_pfn(MASTER_ECS3_PHYS),
		.length		= MASTER_ECS3_SIZE,
		.type		= MT_DEVICE
	}, {	/* Slabe bus : FPGA SRAM */
		.virtual	=  MASTER_RCS3_VIRT,
		.pfn		= __phys_to_pfn(MASTER_RCS3_PHYS),
		.length		= MASTER_RCS3_SIZE,
		.type		= MT_DEVICE
	}, {	/* CPLD : */
		.virtual	=  CREATOR_CPLD_VIRT,
		.pfn		= __phys_to_pfn(CREATOR_CPLD_PHYS),
		.length		= CREATOR_CPLD_SIZE,
		.type		= MT_DEVICE
	}                                            
};
                           


static void __init 
creator_pxa270_map_io (void)
{
       pxa_map_io();
       iotable_init(creator_pxa270_io_desc, ARRAY_SIZE(creator_pxa270_io_desc));

       /* enabling FFUART */
       CKEN |= CKEN6_FFUART;
       pxa_gpio_mode(GPIO41_FFRXD_MD);
       pxa_gpio_mode(GPIO35_FFCTS_MD);
       pxa_gpio_mode(GPIO36_FFDCD_MD);
       pxa_gpio_mode(GPIO37_FFDSR_MD);
       pxa_gpio_mode(GPIO38_FFRI_MD);
       pxa_gpio_mode(GPIO39_FFTXD_MD);
       pxa_gpio_mode(GPIO40_FFDTR_MD);
       pxa_gpio_mode(GPIO83_FFRTS_MD);	

       /* enabling BTUART */
       CKEN |= CKEN7_BTUART;
       pxa_gpio_mode(GPIO42_BTRXD_MD);
       pxa_gpio_mode(GPIO43_BTTXD_MD);
       pxa_gpio_mode(GPIO44_BTCTS_MD);
       pxa_gpio_mode(GPIO45_BTRTS_MD);

       /* This is for the Davicom chip select */
       pxa_gpio_mode(GPIO78_nCS_2_MD);

       /* setup sleep mode values */
       PWER  = 0x00000002;
       PFER  = 0x00000000;
       PRER  = 0x00000002;
       PGSR0 = 0x00008000;
       PGSR1 = 0x003F0202;
       PGSR2 = 0x0001C000;
       PCFR |= PCFR_OPDE;
}


void creator_pxa270_init_irq (void);

MACHINE_START(CREATOR_PXA270, "Microtime Create XScale-PXA270 Module")
    /* .phys_ram	= 0xa0000000, */
	.phys_io	= 0x40000000,
	.io_pg_offst	= (io_p2v(0x40000000) >> 18) & 0xfffc,    
	/*.boot_params	= 0xa0000100,*/
	.fixup		= fixup_creator_pxa270,
	.map_io		= creator_pxa270_map_io,
	.init_irq	= creator_pxa270_init_irq,
	.timer		= &pxa_timer,
	.init_machine	= creator_pxa270_init,
MACHINE_END
