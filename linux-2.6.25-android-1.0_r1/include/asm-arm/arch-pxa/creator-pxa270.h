/*
 *  linux/include/asm-arm/arch-pxa/creator-pxa270.h
 * *
 * Copyright (c) 2006 Microtime Computer Inc. <sales@microtime.com.tw>
 *
 */

#include <asm/arch/pxa-regs.h>
#include <asm/arch/irqs.h>


/*
 * Note: include file for assembler and C
 */


/*============================================================================================
; Memory mapping
;============================================================================================*/
#define CREATOR_FLASH_PHYS		PXA_CS0_PHYS    /* 0x00000000 */
#define CREATOR_FLASH_VIRT		
#define CREATOR_FLASH_SIZE		(0x02000000)

#define CREATOR_ETH_PHYS		PXA_CS1_PHYS    /* 0x04000000 */
#define CREATOR_ETH_VIRT		(0xF0000000)
#define CREATOR_ETH_SIZE		(0x00100000)

#define CREATOR_MASTER_PHYS		PXA_CS2_PHYS    /* 0x08000000 */
#define CREATOR_MASTER_VIRT		(CREATOR_ETH_VIRT+CREATOR_ETH_SIZE)
	#define MASTER_ECS0_PHYS		(CREATOR_MASTER_PHYS+0x00000000)    /* 0x08000000 */
	#define MASTER_ECS0_VIRT		(CREATOR_MASTER_VIRT)
	#define MASTER_ECS0_SIZE		(0x00100000)	
	#define MASTER_RCS0_PHYS		(CREATOR_MASTER_PHYS+0x01000000)    /* 0x09000000 */
	#define MASTER_RCS0_VIRT		(MASTER_ECS0_VIRT+MASTER_ECS0_SIZE)
	#define MASTER_RCS0_SIZE		(0x00200000)	
	#define MASTER_ECS3_PHYS		(CREATOR_MASTER_PHYS+0x02000000)    /* 0x0A000000 */
	#define MASTER_ECS3_VIRT		(MASTER_RCS0_VIRT+MASTER_RCS0_SIZE)
	#define MASTER_ECS3_SIZE		(0x00100000)	
	#define MASTER_RCS3_PHYS		(CREATOR_MASTER_PHYS+0x03000000)    /* 0x0B000000 */
	#define MASTER_RCS3_VIRT		(MASTER_ECS3_VIRT+MASTER_ECS3_SIZE)
	#define MASTER_RCS3_SIZE		(0x00100000)	
#define CREATOR_MASTER_SIZE		(MASTER_ECS0_SIZE+MASTER_RCS0_SIZE+MASTER_ECS3_SIZE+MASTER_RCS3_SIZE)

#define CREATOR_RSV1_PHYS		PXA_CS3_PHYS    /* 0x0C000000 */

#define CREATOR_CPLD_PHYS		PXA_CS4_PHYS    /* 0x10000000 */
#define CREATOR_CPLD_VIRT		(CREATOR_MASTER_VIRT+CREATOR_MASTER_SIZE)
	#define CPLD_1_PHYS			(CREATOR_CPLD_PHYS+0x00000000)    /* 0x10000000 */
	#define CPLD_1_VIRT			(CREATOR_CPLD_VIRT)
	#define CPLD_1_SIZE			(0x00100000)	
	#define CPLD_2_PHYS			(CREATOR_CPLD_PHYS+0x01000000)    /* 0x11000000 */
	#define CPLD_2_VIRT			(CPLD_1_VIRT+CPLD_1_SIZE)
	#define CPLD_2_SIZE			(0x00100000)	
#define CREATOR_CPLD_SIZE		(CPLD_1_SIZE+CPLD_2_SIZE)	

#define CREATOR_RSV2_PHYS       PXA_CS5_PHYS    /* 0x14000000 */

#define SMC_IOADDR		        CREATOR_ETH_VIRT



/*============================================================================================
;Ethernet
;============================================================================================*/
#define GPIO_ETH_IRQ    0
#define CREATOR_ETH_IRQ IRQ_GPIO(GPIO_ETH_IRQ)

/*============================================================================================
; Video settings	TODO
;============================================================================================*/
#define COLLIE_TC35143_GPIO_TBL_CHK     UCB_IO_1	/* GPIO1=TBL_CHK. used drivers/mfd/ucb1x00-ts.c     */ 




/*============================================================================================
;Compact Flash/PCMCIA
;============================================================================================*/
//#define GPIO_PCMCIA0_CD_IRQ     1
//#define CREATOR_PCMCIA0_CD_IRQ  IRQ_GPIO(GPIO_PCMCIA0_CD_IRQ)
//#define COLIBRI_PCMCIA_CD_EDGE  IRQT_BOTHEDGE
//#define GPIO_PCMCIA0_RDY_IRQ    1
//#define COLIBRI_PCMCIA0_RDY_IRQ IRQ_GPIO(GPIO_PCMCIA0_RDY_IRQ)
//#define COLIBRI_PCMCIA_RDY_EDGE IRQT_FALLING
//#define GPIO_PCMCIA_POW_EN      107
#define GPIO_PCMCIA_NPOE        48
#define GPIO_PCMCIA_NPOE_AF     GPIO_ALT_FN_2_OUT
#define GPIO_PCMCIA_NPIOR       50
#define GPIO_PCMCIA_NPIOR_AF    GPIO_ALT_FN_2_OUT
#define GPIO_PCMCIA_NPIOW       51
#define GPIO_PCMCIA_NPIOW_AF    GPIO_ALT_FN_2_OUT
#define GPIO_PCMCIA_NPCE1       85
#define GPIO_PCMCIA_NPCE1_AF    GPIO_ALT_FN_1_OUT
#define GPIO_PCMCIA_NPCE2       54
#define GPIO_PCMCIA_NPCE2_AF    GPIO_ALT_FN_2_OUT
#define GPIO_PCMCIA_NPREG       55
#define GPIO_PCMCIA_NPREG_AF    GPIO_ALT_FN_2_OUT
#define GPIO_PCMCIA_NPWAIT      56
#define GPIO_PCMCIA_NPWAIT_AF   GPIO_ALT_FN_1_IN
#define GPIO_PCMCIA_NPIOIS16    57
#define GPIO_PCMCIA_NPIOIS16_AF GPIO_ALT_FN_1_IN
#define GPIO_PCMCIA_PSKTSEL     79
#define GPIO_PCMCIA_PSKTSEL_AF  GPIO_ALT_FN_1_OUT
#define PCC_RESET_ASSERT      	MASTER_CTRL1 |= 0x08;
#define PCC_RESET_DEASSERT     	MASTER_CTRL1 &= 0xF7;
//#define GPIO_PCMCIA0_BVD1       83
//#define GPIO_PCMCIA0_BVD2       82

#define PCC_DETECT 		(MASTER_STATUS2 & 0x04)
#define PCC_READY 		(MASTER_STATUS2 & 0x08)
#define PCC_BVD1()  	(MASTER_STATUS2 & 0x10)
#define PCC_BVD2()  	(MASTER_STATUS2 & 0x20)
#define PCC_VS3V()  	1       /* only 3.3V supported */
#define PCC_VS5V()  	0       /* only 3.3V supported */
#define PCC_PWR_ON() 	MASTER_CTRL1 |= 4;
#define PCC_PWR_OFF() 	MASTER_CTRL1 &= (~4);



/*============================================================================================
; MMC/SD
;============================================================================================*/
//#define GPIO_MMC_CD_IRQ         1
#define CREATOR_MMC_CD_IRQ      CREATOR_MMCI_IRQ
#define GPIO_MMCCLK_AF          GPIO32_MMCCLK_MD
#define GPIO_MMCDAT0_AF         GPIO92_MMCDAT0
#define GPIO_MMCDAT1_AF         GPIO109_MMCDAT1
#define GPIO_MMCDAT2_AF         GPIO110_MMCDAT2
#define GPIO_MMCDAT3_AF         GPIO111_MMCDAT3
#define GPIO_MMCCMD_AF          GPIO112_MMCCMD_MD
//#define GPIO_MMCCS0_AF          GPIO110_MMCCS0_MD

/*============================================================================================
; AC97/Touch screen
;============================================================================================*/
//#define GPIO_TOUCH_IRQ          1
//#define COLIBRI_TOUCH_IRQ       IRQ_GPIO(GPIO_TOUCH_IRQ)

#define GPIO_AC97_RESET         113
#define GPIO_AC97_RST_AF        GPIO_ALT_FN_2_OUT
#define GPIO_AC97_SYSCLK        98
#define GPIO_AC97_SYSCLK_AF     GPIO_ALT_FN_1_OUT

#define RAMDISK_DN_ADDR          0xA0800000       /* used in arch/arm/mach-pxa/colibri.c     */

/*============================================================================================
;Create-xscale-pxa27x master board CPLD control registers
;============================================================================================*/
#ifndef __ASSEMBLY__
#  define CPLD_REG(x)           (*((volatile unsigned short *)(CPLD_1_VIRT + (x))))
#else
#  define CPLD_REG(x)           (CPLD_1_VIRT + (x))
#endif


//CPLD Registers
#define CPLD_INTPEND1 	CPLD_REG(0x2)
						//Write '1' to clear the corresponding pending bit
						//00 : CF_IDE		: R/W-(0)1 = CF IRQ Pending (IDE Mode)
						//01 : TOUCH		: R/W-(0)1 = Touch Screen Pending
						//02 : CF_IRQ		: R/W-(0)1 = CF IRQ Pending (IO Mode)
						//03 : CF_STS		: R/W-(0)1 = CF STSCHG Pending
						//04 : MIRQ0		: R/W-(0)1 = Master Bus IRQ(0) Pending
						//05 : MIRQ1		: R/W-(0)1 = Master Bus IRQ(1) Pending
						//06 : MIRQ2		: R/W-(0)1 = Master Bus IRQ(2) Pending
						//07 : MIRQ3		: R/W-(0)1 = Master Bus IRQ(3) Pending
#define CPLD_INTPEND2 	CPLD_REG(0x4)
						//Write '1' to clear the corresponding pending bit
						//00 : CF_I			: R/W-(0)1 = CF Inserted Pending
						//01 : CF_O			: R/W-(0)1 = CF Removed Pending
						//02 : MMC_I		: R/W-(0)1 = MMC Inserted Pending
						//03 : MMC_O		: R/W-(0)1 = MMC Removed Pending
						//04 : USB_I		: R/W-(0)1 = USB Device Cable Attached Pending
						//05 : USB_O		: R/W-(0)1 = USB Device Cable Detached Pending
						//06 : RSV			: R/W-(0)X   Should Be 0
						//07 : RSV			: R/W-(0)X   Should Be 0
#define CPLD_INTMASK1 	CPLD_REG(0x6)
						//Write '1' to disable the corresponding IRQ
						//00 : CF_IDE		: R/W-(1)1 = Mask CF IRQ (IDE Mode)
						//01 : TOUCH		: R/W-(1)1 = Mask Touch Screen IRQ
						//02 : CF_IRQ		: R/W-(1)1 = Mask CF IRQ (IO Mode)
						//03 : CF_STS		: R/W-(1)1 = Mask CF STSCHG IRQ
						//04 : MIRQ0		: R/W-(1)1 = Mask Master Bus IRQ(0) 
						//05 : MIRQ1		: R/W-(1)1 = Mask Master Bus IRQ(1) 
						//06 : MIRQ2		: R/W-(1)1 = Mask Master Bus IRQ(2) 
						//07 : MIRQ3		: R/W-(1)1 = Mask Master Bus IRQ(3) 
#define CPLD_INTMASK2 	CPLD_REG(0x8)
						//Write '1' to disable the corresponding IRQ
						//00 : CF_I			: R/W-(1)1 = Mask CF Inserted IRQ
						//01 : CF_O			: R/W-(1)1 = Mask CF Removed IRQ
						//02 : MMC_I		: R/W-(1)1 = Mask MMC Inserted IRQ
						//03 : MMC_O		: R/W-(1)1 = Mask MMC Removed IRQ
						//04 : USB_I		: R/W-(1)1 = Mask USB Device Cable Attached IRQ
						//05 : USB_O		: R/W-(1)1 = Mask USB Device Cable Detached IRQ
						//06 : RSV			: R/W-(1)X   Should Be 1
						//07 : RSV			: R/W-(1)X   Should Be 1
#define CPLD_CTRL1		CPLD_REG(0xa)
						//00 : USB_FS		: R/W-(0)1 = D+ Pull Hi 1.5K (Attached)
						//01 : FF_UART		: R/W-(1)1 = COM2 (FF-UART) Enable
						//02 : CF_PWR		: R/W-(0)1 = CF Power ON
						//03 : CF_RST		: R/W-(0)1 = CF Reset
						//04 : ROM_WP		: R/W-(1)0 = SOM-PXA270 Flash Write Protect
						//05 : LCD_PWR		: R/W-(0)1 = TFT-LCD Power ON
						//06 : ETH_RST		: R/W-(0)1 = Reset LAN91C111
						//07 : SB0			: R/W-(0)X   Should Be 0
#define CPLD_CTRL2		CPLD_REG(0xc)
						//00 : EMU_FA		: R/W-(1)0 = Use KEY(0) to emulation nBATT_FAULT
						//							 	 Use KEY(1) to emulation nVDD_FAULT
						//01 : SB1			: R/W-(1)X   Should Be 1
						//02 : N_MIRQ3		: R/W-(1)0 = Polarity change on MIRQ 
						//03 : ADACK		: R/W-(1)X   Auto DACK
						//04 : LED0			: R/W-(1)0 = D1 On
						//05 : LED1			: R/W-(1)0 = D2 On
						//06 : LED2			: R/W-(1)0 = D3 On
						//07 : LED3			: R/W-(1)0 = D4 On						
#define CPLD_CTRL3		CPLD_REG(0x10)
						//00 : SB0			: W-(0)X   Should Be 0
						//...				: W-(0)X   Should Be 0
						//07 : SB0			: W-(0)X   Should Be 0
#define CPLD_STATUS1 	CPLD_REG(0x0)
						//00 : KEY0			: State of S1
						//01 : KEY1			: State of S2
						//02 : MMC_WP		: State of MMC/SD WP Pin
						//03 : MMC_CD		: State of MMC/SD nCD Pin
						//04 : Version		: Version Number
						//05 : Version		: 0001 = Ver 1.0
						//06 : Version		: 0010 = Ver 2.0
						//07 : Version		: ...
#define CPLD_STATUS2 	CPLD_REG(0xe)
						//00 : USBC_IRQ		: USB Device Cable Status, (1)=Cable Inserted
						//01 : CF_nINPACK	: State of CF nINPACK Pin
						//02 : CF_nCD1		: State of CF CF_nCD1 Pin
						//03 : CF_RDY		: State of CF CF_RDY Pin
						//04 : CF_BVD1		: State of CF CF_BVD1 Pin
						//05 : CF_BVD2		: State of CF CF_BVD2 Pin
						//06 : RSV			: Reserved
						//07 : RSV			: Reserved


/*============================================================================================
;Creator main board peripheral control registers
;============================================================================================*/
#define ECS0_BASE 		MASTER_ECS0_VIRT	//Creator main I/O
#define ECS1_BASE 							//Unuse
#define ECS2_BASE 							//Unuse
#define RCS0_BASE 		MASTER_RCS0_VIRT	//Creator 2M FLASH
#define ECS3_BASE 		MASTER_ECS3_VIRT 	//FPGA board I/O
#define RCS3_BASE 		MASTER_RCS3_VIRT	//FPGA board SRAM

#define CF_TASK_BASE 	(ECS0_BASE+0x0000)	/* CF_nCS0 */
#define CF_STATUS_BASE	(ECS0_BASE+0x0200)	/* CF_nCS1 : not used */

#define ATA_BYTDAT_OFFSET 		(0) 		/* DATA read/write */
#define ATA_ERRFEA_OFFSET   	(2) 		/* read error/write feature */
#define ATA_SECTCOUNT_OFFSET    (4) 		/* sector count */
#define ATA_SECTORNO_OFFSET     (6)			/* sector number */
#define ATA_CYLINDLOW_OFFSET    (8)			/* cylinder low */
#define ATA_CYLINDHI_OFFSET     (0xa)		/* cylinder high */
#define ATA_SCARDHEAD_OFFSET    (0xc)		/* select card/head */
#define ATA_STATCOMMD_OFFSET    (0xe)
#define ATA_CONTROL_OFFSET		(0X200+0xc)	/* control */

#define ATA_BYTDATREG   (*(volatile unsigned short *)(CF_TASK_BASE+0x0000)) /* DATA read/write */
#define ATA_ERRFEAREG   (*(volatile unsigned short *)(CF_TASK_BASE+0x0002)) /* read error/write feature */
#define ATA_SECTCOUNT   (*(volatile unsigned short *)(CF_TASK_BASE+0x0004)) /* sector count */
#define ATA_SECTORNO    (*(volatile unsigned short *)(CF_TASK_BASE+0x0006)) /* sector number */
#define ATA_CYLINDLOW   (*(volatile unsigned short *)(CF_TASK_BASE+0x0008)) /* cylinder low */
#define ATA_CYLINDHI    (*(volatile unsigned short *)(CF_TASK_BASE+0x000A)) /* cylinder high */
#define ATA_SCARDHEAD   (*(volatile unsigned short *)(CF_TASK_BASE+0x000C)) /* select card/head */
#define ATA_STATCOMMD   (*(volatile unsigned short *)(CF_TASK_BASE+0x000E)) /* read status/write command */
#define ATA_CONTROL		(*(volatile unsigned short *)(CF_TASK_BASE+ATA_CONTROL_OFFSET))	/* control */

#define USB_DATA 		(*(volatile unsigned short *)(ECS0_BASE+0x0400))
#define USB_CMD 		(*(volatile unsigned short *)(ECS0_BASE+0x0402))
#define CCM_DATA 		(*(volatile unsigned short *)(ECS0_BASE+0x0600))
#define LCD_CMD 		(*(volatile unsigned short *)(ECS0_BASE+0x0800))
#define LCD_DATA 		(*(volatile unsigned short *)(ECS0_BASE+0x0802))
#define CODEC_DATA 		(*(volatile unsigned short *)(ECS0_BASE+0x0A00))
#define IO_REG0			(*(volatile unsigned short *)(ECS0_BASE+0x0C00))
#define IO_REG1 		(*(volatile unsigned short *)(ECS0_BASE+0x0C02))	
#define IO_REG2 		(*(volatile unsigned short *)(ECS0_BASE+0x0C04))	
#define CPLD_CTRL 		(*(volatile unsigned short *)(ECS0_BASE+0x0E00))	/* Write */
						//00	: IO_nRST
						//01	: AIC_ON
						//02	: EXPTM1
						//03	: EXPTM2
						//04	: FPGA_nPGM
						//05	: FPGA_CCLK
						//06	: IRQ3_SEL	0 : IRQ3=CCM(CMOS sensor)
						//					1 : IRQ3=X_nINT(FPGA Slave Bus)
						//07	: DMA_SEL	0 : DMA0=CODEC_nDRQ, 	DMA1=X_nDRQ
						//					1 : DMA0=USB_DRQ, 		DMA1=X_nDRQ 
						
#define CPLD_STATUS		(*(volatile unsigned short *)(ECS0_BASE+0x0E00))	/* Read */
						//00 	: EE_DO
						//01 	: USB_nEOT
						//02 	: USB_ON
						//03 	: NA
						//04 	: CCD_nRDY
						//07-05 : 0
						//15-08	: Version (0x10=V1.0, 0x11=V1.1, ...)

#define IRQ_SELECT 		(*(volatile unsigned short *)(ECS0_BASE+0x0E02))	/* For CPLD(U16) V1.1 or later */
						//02-00	: IRQ0_MUX (see IRQ mapping)
						//05-03	: IRQ1_MUX (see IRQ mapping)
						//08-06	: IRQ2_MUX (see IRQ mapping)
						//11-09	: IRQ3_MUX (see IRQ mapping)
						//12	: INV_CF_IRQ 	: (0)1 => CF_IRQ Inversed	/* For CPLD(U16) V1.2 or later */
						//13	: IRQ3_MODE		: (0)1 => IRQ3 = Function of IRQ3_PEND and IRQ3_MASK
						//14	: IRQ0_MODE		: (0)0 => IRQ0 = Main Board PHY_nINT(U19:PHY)
						//							 1 => IRQ0 = Main Board CF_IRQ
						//15	: IRQ_DEFAULT (0=use default)
						//		: 0 : 	IRQ0=PHY_nINT(U19),		IRQ1=USB_nINT(U25)
						//				IRQ2=CODEC_IRQ(U21),	IRQ3=CCM or X_nINT (CPLD_CTRL bit6)
						//		: 1 :	Controlled by IRQ?_MUX
						
						//IRQ mapping :	000 -> PHY_nINT(U19:PHY)	001 -> USB_nINT(U25:USB)
						//				010 -> CODEC_IRQ(U21:AIC)	011 -> X_nINT(FPGA Slave Bus)
						//				100 -> CCM (CMOS sensor)	101 -> RTC_nINT(U32:RTC)
						//				110 -> Reserved (J4:CF card)


#define DMA_SELECT 		(*(volatile unsigned short *)(ECS0_BASE+0x0E04))	/* For CPLD(U16) V1.1 or later */
						//01-00	: DMA0_MUX (see IRQ mapping)
						//02-01	: DMA1_MUX (see IRQ mapping)
						//07	: DMA_DEFAULT (0=use default)
						//		: 0 : 	DMA0=CODEC_nDRQ, 	DMA1=X_nDRQ (when DMA_SEL(CPLD_CTRL bit7)=0)
						//				DMA0=USB_nDRQ, 		DMA1=X_nDRQ (when DMA_SEL(CPLD_CTRL bit7)=1)
						//		: 1 : 	Controlled by DMA?_MUX
						
						//DMA mapping :	00 -> CODEC_nDRQ(U21:AIC)	01 -> USB_nDRQ(U25:USB)
						//				10 -> X_nDRQ(FPGA Slave Bus
#define IRQ3_PEND 		(*(volatile unsigned short *)(ECS0_BASE+0x0A02))
						//Write '1' to clear the corresponding pending bit
						//00 	: CF_IRQ		: R/W-(0)1 = CF IRQ Pending
						//01 	: X_nINT		: R/W-(0)1 = Slave Bus IRQ Pending
						//02-15 : Reserved		: R/W-(0)X   Should Be 0
#define IRQ3_MASK 		(*(volatile unsigned short *)(ECS0_BASE+0x0A04))
						//Write '1' to disable the corresponding IRQ
						//00 	: CF_IRQ		: R/W-(1)1 = Mask CF_IRQ IRQ
						//01 	: X_nINT		: R/W-(1)1 = Mask Slave Bus IRQ
						//02-15 : Reserved		: R/W-(1)X   Should Be 1


#define FPGA_CTRL 		(*(volatile unsigned short *)(ECS3_BASE+0x0000))	//CD_PORT & ADDRL=0 & WR
#define FPGA_STATUS 	(*(volatile unsigned short *)(ECS3_BASE+0x0000))	//CD_PORT & ADDRL=0 & RD
#define SCAN_OUT 		(*(volatile unsigned short *)(ECS3_BASE+0x0002))	//CS_PORT & ADDRL=1 & WR
#define FPGA_LED_HUHU	(*(volatile unsigned short *)(ECS3_BASE+0x0004))	//CS_PORT & ADDRL=2 & WR
#define CODEC_DATA_FPGA	(*(volatile unsigned short *)(ECS3_BASE+0x0200))	//CS_AIC  & ADDRL=X & RD/WR



#define MASTER_STATUS1          CPLD_STATUS1T
#define MASTER_INTPEND1     	CPLD_INTPEND1
#define MASTER_INTPEND2     	CPLD_INTPEND2
#define MASTER_INTMASK1    		CPLD_INTMASK1
#define MASTER_INTMASK2    		CPLD_INTMASK2
#define MASTER_CTRL1      		CPLD_CTRL1
#define MASTER_CTRL2   			CPLD_CTRL2
#define MASTER_STATUS2     		CPLD_STATUS2
#define MASTER_IO1     	 		CPLD_IO1
#define MASTER_IO2     	 		CPLD_IO2
#define MASTER_IRQ3_PEND     	IRQ3_PEND
#define MASTER_IRQ3_MASK     	IRQ3_MASK
