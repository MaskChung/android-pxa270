/* -*-Asm-*- */
/*
 *  GRUB  --  GRand Unified Bootloader
 *  Copyright (C) 2000 Machine Vision Holdings, Inc.
 *
 *  Author: David Woodhouse <dwmw2@infradead.org>
 *
 *  $Id: doc_stage1.h,v 1.3 2005/11/07 11:14:46 gleixner Exp $
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
#define DoC_CSDNControl 0x1004
#define DoC_CSDNDeviceSelect 0x1005
#define DoC_ECCConf 0x1006
#define DoC_2k_ECCStatus 0x1007

#define DoC_CSDNSlowIO 0x100d
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

#define DoC_2k_CSDN_IO 0x1800



#define DOC_MODE_RESET 0
#define DOC_MODE_NORMAL 1
#define DOC_MODE_RESERVED1 2
#define DOC_MODE_RESERVED2 3

#define DOC_MODE_MDWREN 4
#define DOC_MODE_CLR_ERR 0x80

#define DOC_ChipID_Doc2k 0x20
#define DOC_ChipID_DocMil 0x30

#define CSDN_CTRL_FR_B 0x80
#define CSDN_CTRL_ECC_IO 0x20
#define CSDN_CTRL_FLASH_IO 0x10
#define CSDN_CTRL_WP 8
#define CSDN_CTRL_ALE 4
#define CSDN_CTRL_CLE 2
#define CSDN_CTRL_CE 1


#define NAND_CMD_READ0 0
#define NAND_CMD_READ1 1
#define NAND_CMD_RESET 0xff

#include "stage2_size.h"

/*
 *  defines for the code go here
 */

#define LOADLEN (((STAGE2_SIZE + 0xff) & ~0xff) >> 8)
#define GRUBLOADOFS	0x300

#ifdef OLDGRUB /* Loading Erich's old grub */

#define GRUBSTART	0x8000
#define GRUBLOADSEG	0x7d0

#else /* Loading FSF grub */

#define GRUBSTART	0x8200
#define GRUBLOADSEG	0x7f0

#endif

	/* Some macros to make it obvious what we're accessing */
#define BXREG 0x1004

#define BX_ChipID		-4(%bx)
#define BX_DOCControl		-2(%bx)
#define BX_CSDNControl		(%bx)
#define BX_SlowIO		9(%bx)

#define SIREG 0x1800
#define SI_CSDN_IO		(%si)

			/* Print message string */
#define MSG(x)	movw $(x), %si; call message

