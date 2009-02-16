/* -*-Asm-*- */
/*
 *  Register locations, etc for DOCBoot
 *
 *  Author: David Woodhouse <dwmw2@infradead.org>
 *
 *  $Id: doc_bootstub.h,v 1.7 2005/06/27 18:48:48 dbrown Exp $
 *
 *  This program is free software; you can redistribute it and/or modify
 *  it under the terms of the GNU General Public License as published by
 *  the Free Software Foundation; either version 2 of the License, or
 *  (at your option) any later version.
 *
 *  This program is distributed in the hope that it will be useful,
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *  GNU General Public License for more details.
 *
 *  You should have received a copy of the GNU General Public License
 *  along with this program; if not, write to the Free Software
 *  Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 */


#define DoC_M_CDSN_IO 0x800

#define DoC_ChipID 0x1000
#define DoC_DOCStatus 0x1001
#define DoC_DOCControl 0x1002
#define DoC_FloorSelect 0x1003
#define DoC_CDSNControl 0x1004
#define DoC_CDSNDeviceSelect 0x1005
#define DoC_ECCConf 0x1006
#define DoC_2k_ECCStatus 0x1007

#define DoC_CDSNSlowIO 0x100d
#define DoC_ECCSyndrome0 0x1010
#define DoC_ECCSyndrome1 0x1011
#define DoC_ECCSyndrome2 0x1012
#define DoC_ECCSyndrome3 0x1013
#define DoC_ECCSyndrome4 0x1014
#define DoC_ECCSyndrome5 0x1015
#define DoC_AliasResolution 0x101b
#define DoC_ConfigInput 0x101c
#define DoC_ReadPipeInit 0x101d
#define DoC_WritePipeTerm 0x101e
#define DoC_LastDataRead 0x101f
#define DoC_NOP 0x1020

#define DoC_2k_CDSN_IO 0x1800


#define DoC_Mplus_NOP			0x1002
#define DoC_Mplus_AliasResolution	0x1004
#define DoC_Mplus_DOCControl		0x1006
#define DoC_Mplus_AccessStatus		0x1008
#define DoC_Mplus_DeviceSelect		0x1008
#define DoC_Mplus_Configuration		0x100a
#define DoC_Mplus_OutputControl		0x100c
#define DoC_Mplus_FlashControl		0x1020
#define DoC_Mplus_FlashSelect 		0x1022
#define DoC_Mplus_FlashCmd		0x1024
#define DoC_Mplus_FlashAddress		0x1026
#define DoC_Mplus_FlashData0		0x1028
#define DoC_Mplus_FlashData1		0x1029
#define DoC_Mplus_ReadPipeInit		0x102a
#define DoC_Mplus_LastDataRead		0x102c
#define DoC_Mplus_LastDataRead1		0x102d
#define DoC_Mplus_WritePipeTerm 	0x102e
#define DoC_Mplus_ECCSyndrome0		0x1040
#define DoC_Mplus_ECCSyndrome1		0x1041
#define DoC_Mplus_ECCSyndrome2		0x1042
#define DoC_Mplus_ECCSyndrome3		0x1043
#define DoC_Mplus_ECCSyndrome4		0x1044
#define DoC_Mplus_ECCSyndrome5		0x1045
#define DoC_Mplus_ECCConf 		0x1046
#define DoC_Mplus_Toggle		0x1046
#define DoC_Mplus_DownloadStatus	0x1074
#define DoC_Mplus_CtrlConfirm		0x1076
#define DoC_Mplus_Power			0x1fff


#define DOC_MODE_RESET 0
#define DOC_MODE_NORMAL 1
#define DOC_MODE_RESERVED1 2
#define DOC_MODE_RESERVED2 3

#define DOC_MODE_CLR_ERR 	0x80
#define DOC_MODE_RST_LAT	0x10
#define DOC_MODE_BDECT		0x08
#define DOC_MODE_MDWREN 	0x04

#define DOC_ChipID_Doc2k 0x20
#define DOC_ChipID_DocMil 0x30

#define CDSN_CTRL_FR_B 0x80
#define CDSN_CTRL_ECC_IO 0x20
#define CDSN_CTRL_FLASH_IO 0x10
#define CDSN_CTRL_WP 8
#define CDSN_CTRL_ALE 4
#define CDSN_CTRL_CLE 2
#define CDSN_CTRL_CE 1

#define DOC_FLASH_CE		0x80
#define DOC_FLASH_WP		0x40

#define NAND_CMD_READ0 0
#define NAND_CMD_READ1 1
#define NAND_CMD_READOOB 0x50
#define NAND_CMD_RESET 0xff

	/* Some macros to make it obvious what we're accessing */
#define BXREG	DoC_ChipID
//#define BXREG	DoC_CDSNControl

#define BX_ChipID		(DoC_ChipID-BXREG)(%bx)
#define BX_DOCControl		(DoC_DOCControl-BXREG)(%bx)
#define BX_CDSNControl		(DoC_CDSNControl-BXREG)(%bx)
//#define BX_CDSNControl		(%bx)
#define BX_SlowIO		(DoC_CDSNSlowIO-BXREG)(%bx)
#define BX_ConfigurationInput	(DoC_ConfigInput-BXREG)(%bx)
#define BX_ReadPipeInit		(DoC_ReadPipeInit-BXREG)(%bx)
#define BX_WritePipeTerm	(DoC_WritePipeTerm-BXREG)(%bx)
#define BX_LastDataRead		(DoC_LastDataRead-BXREG)(%bx)
#define BX_NOP			(DoC_NOP-BXREG)(%bx)
#define BX_DELAY		(%bx)
//#define BX_DELAY		BX_ChipID

#define DOC_DELAY2 testw BX_DELAY, %ax
#define DOC_DELAY4 incw BX_DELAY

#define BX_Mplus_NOP		2(%bx)
#define BX_Mplus_DOCControl	6(%bx)
#define BX_Mplus_FlashControl	32(%bx)
#define BX_Mplus_FlashSelect	34(%bx)
#define BX_Mplus_FlashCmd	36(%bx)
#define BX_Mplus_FlashAddress	38(%bx)
#define BX_Mplus_ReadPipeInit	42(%bx)
#define BX_Mplus_WritePipeTerm	46(%bx)
#define BX_Mplus_CtrlConfirm	118(%bx)

#define SI_CDSN_IO		(%si)

			/* Print message string */
#define MSG(x)	movw $(x), %si; call message

#define BIOS_SIG 0xAA55

#define PARAM_BYTES 12
#define SETUP_SECTS_LOCATION 497

#define DOC_BIOS_HOOK 0x18

/* #define DOC_ADDRESS	0xc800 */
/* #define BIOS_EXTENSION */
/* #define MILPLUS */
/* #define OLD_DOC2K */
/* #define DEBUG_BUILD */

#ifdef OLD_DOC2K
  #define CDSN_CTRL_BASE CDSN_CTRL_FLASH_IO + CDSN_CTRL_WP + CDSN_CTRL_CE
  #define SLOWIO_WRITE(x) movb x, BX_SlowIO
  #define SLOWIO_READ testb BX_SlowIO, %al
  #define SIREG DoC_2k_CDSN_IO
#else
  #define CDSN_CTRL_BASE CDSN_CTRL_WP + CDSN_CTRL_CE
  #define SLOWIO_WRITE(x) ;
  #define SLOWIO_READ ;
  #define SIREG DoC_M_CDSN_IO
#endif

#define BX_CDSN_IO		(SIREG-BXREG)(%bx)
