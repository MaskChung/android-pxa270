/* -*-Asm-*- */
/*
 *  GRUB  --  GRand Unified Bootloader
 *  Copyright (C) 2000 Red Hat UK Ltd.
 *
 *  Author: David Woodhouse <dwmw2@infradead.org>
 *
 *  $Id: doc_stage1b.S,v 1.4 2000/08/31 16:27:28 dwmw2 Exp $
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

#include "doc_stage1.h"

	.file "doc_stage1b.S"
	.code16
	.text
stage1b:
mainloop:

#ifdef PAGE256
                /* Send new READ0 command */
        xorb    %ah, %ah
#else	
		/* Send new READ command - is it READ0 or READ1? */
	movw	%di, %ax
	andb	$1,%ah
#endif
	call	doc_cmd

		/* Start of new block. Set address */
	movb	$CSDN_CTRL_FLASH_IO + CSDN_CTRL_WP + CSDN_CTRL_ALE + CSDN_CTRL_CE, BX_CSDNControl
	incw	BX_ChipID

#ifdef PAGE256
		/* We sent bits 0-7, then 8-15, then 16-23 */
#else	
		/* We send bits 0-7, then 9-16, then 17-23 */
		/* Yes bit 8 is missing. Read the NAND flash specs */		
#endif
	movw	%di,%dx
		/* Bits 0-7 are always zero */
	movb	$0,BX_SlowIO
	movb	$0,SI_CSDN_IO
#ifndef PAGE256
			/* Bits 9-16 */
	shrw	$1,%dx
	.byte	0x80, 0xce /* orb adrbit16, %dh */
adrbit16:	
	.byte	0
#else
			/* Bits 8-15 */
#endif
	movb	%dh,BX_SlowIO
	movb	%dh,SI_CSDN_IO
#ifdef PAGE256
			/* Bits 16-23 */
#else	
			/* Bits 17-24 */
#endif
	.byte	0xb2 /* movb adrbytehi, %dl */
adrbytehi:
	.byte	0

	movb	%dl,BX_SlowIO
	movb	%dl,SI_CSDN_IO

		/* Clear the ALE line to the flash chip */
	movb	$CSDN_CTRL_FLASH_IO + CSDN_CTRL_WP + CSDN_CTRL_CE, BX_CSDNControl
	call	doc_wait

	pushw	%cx		/* Store the 'blocks remaining' count */
	movw	$0x100, %cx	/* Set up to copy 0x100 bytes */
readbyte:	
	testb	BX_SlowIO,%al	/* dummy read */
	movsb			/* movb SI_CSDN_IO ;  stosb would be more obvious, */
	decw	%si		/*  but would take an extra byte. */
	loop	readbyte


	testw	$0xffff, %di	/* Check if we've done a whole 0x10000 bytes */
	jnz	endloop		/* No - continue regardless */
		/* Yes - increase %es */
	movw	%es, %cx
	addb	$0x10, %ch
	movw	%cx, %es

#ifndef PAGE256
	addb	$0x80, %cs:adrbit16	/* Increase bit 16 */
	jnc	endloop			/* Did it overflow? */
#endif	
	incb	%cs:adrbytehi		/* If so, increase the high byte too */

endloop:	
	popw	%cx		/* Restore the 'blocks remaining' count */
	loop	mainloop	/* Loop till completely done */
good:	

#if 1	
	pushw	%ds	
	pushw	%cs
	popw	%ds
	MSG(msgjmp)
	popw	%ds
#endif
		/* Run it:	 jmpf 0:8200 */
	.byte	0xea
	.word	GRUBSTART,0

msgjmp:		
	.string "Jumping to Grub\n\r"
	.org 0xff
	.byte 0 /* checksum */

	
