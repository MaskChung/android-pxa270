/* -*-Asm-*- */
/*
 *  GRUB  --  GRand Unified Bootloader
 *  Copyright (C) 2000 Machine Vision Holdings, Inc.
 *
 *  Author: David Woodhouse <dwmw2@infradead.org>
 *
 *  $Id: doc_stage1.S,v 1.11 2000/08/31 16:24:05 dwmw2 Exp $
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
	
	.file	"doc_stage1.S"

	.text

	/* Tell GAS to generate 16-bit instructions so that this code works
	   in real mode. */
	.code16

.globl _start; _start:
	/*
	 * _start is loaded at 0x20000 by the DiskOnChip IPL,
	 *	and is jumped to with CS:IP 0x2000:0
	 */

	
	/* What we need to do is:
	   1. Check the current end-of-memory in the BIOS
	   2. Reduce it by the amount of memory we need for our INT 19h 
	   3. Return, and wait for our INT 19h to be called.
	*/

		/* Store the DiskOnChip segment */
	movw	%ds, %cs:doc_seg
		
		/* Find top of memory */
	xorw	%ax, %ax
	movw	%ax, %ds
	movw	0x0413, %ax

		/* Steal 1K from the top */
	decw	%ax
	movw	%ax,0x0413

		/* Generate segment address */
	shlw	$6,%ax
	movw	%ax,%es

		/* Set up our shiny new INT 19h handler */
	movw	%es,0x0066
	movw	$int19,0x0064

		/* Copy ourself into the new segment we've just reserved */
	movw	$0x200, %cx
	cld
	pushw	%cs
	popw	%ds
	xorw	%si,%si
	xorw	%di,%di
	rep
	movsw

	lret	
	
int19:
	pushw	%cs
	popw	%ds

		/* Say hello */
	MSG(int19_execed_string)

		/* Check where the DiskOnChip is */
	movw	doc_seg, %ds

		/* Set up our target address for writing stage2 */
	movw	$GRUBLOADSEG, %ax
	movw	%ax, %es
	movw	$GRUBLOADOFS, %di

		/* Stage2 proper starts at offset 0x300 on the flash. We
		   have defined the load address GRUBLOADSEG so that
		   we're loading this to 0x8000:0000 or 0x8000:0200, 
		   depending on whether this is old or GNU Grub, 
		   respectively.
		*/

	cld

		/* Copy it.... */

		/* Reset DiskOnChip */
	movw	$BXREG, %bx
	movb	$DOC_MODE_CLR_ERR + DOC_MODE_MDWREN + DOC_MODE_NORMAL, BX_DOCControl
	movb	$DOC_MODE_CLR_ERR + DOC_MODE_MDWREN + DOC_MODE_NORMAL, BX_DOCControl

	movw	$SIREG, %si
	
		/* Flash command:	Reset */
	movb	$NAND_CMD_RESET, %ah
	call	doc_cmd

		/* Load %CX with the number of 256-byte blocks to load */	
	movw	$LOADLEN, %cx

		/* 
		   Basically, we know that the DiskOnChip IPL ROM will 
		   load only the first 256 bytes of each 512-byte page
		   from a 512-byte-page device, but it'll load _all_
		   the data from a 256-byte-page device.
		   
		   Therefore, we put the code to handle 256-byte-page
		   devices at offset 0x100, where we know it won't get
		   loaded if we're actually on a 512-byte-page device.
		   We put the code to handle 512-byte-page devices at
		   0x200, in the knowledge that it'll get loaded to 
		   offset 0x100 if it's appropriate.

		   Now, we don't have to probe the device, we just 
		   jump to the code at 0x100, because we _know_ that 
		   the IPL ROM will have loaded the correct code there.
		   
		   This is SICK. I love it. 
		*/

	jmp	stage1b

	/* Routines used by both 256- and 512- byte/page loaders.

                /* doc_cmd:      Send a command to the flash chip */
doc_cmd:
                /* Enable CLE line to flash */
        movb    $CSDN_CTRL_FLASH_IO + CSDN_CTRL_WP + CSDN_CTRL_CLE + CSDN_CTRL_CE, BX_CSDNControl
                /* Dummy */
        incw    -4(%bx)
                /* Write the actual command */
        movb    %ah,BX_SlowIO
        movb    %ah,(%si)

                /* doc_wait:     Wait for the DiskOnChip to be ready */
doc_wait:
        incw    BX_ChipID
l38:    
        testb   $0x80,BX_CSDNControl
        jz      l38
        test    BX_CSDNControl,%dx
        ret
                

/*
 * message: write the string pointed to by %si
 *
 *   WARNING: trashes %si, %ax, and %bx
 */

	/*
	 * Use BIOS "int 10H Function 0Eh" to write character in teletype mode
	 *	%ah = 0xe	%al = character
	 *	%bh = page	%bl = foreground color (graphics modes)
	 */
1:
	movw	$0x0001, %bx
	movb	$0xe, %ah
	int	$0x10		/* display a byte */

message:
	lodsb
	cmpb	$0, %al
	jne	1b	/* if not end of string, jmp to display */
	ret

	/* Hex output routines, used at one point in debugging */	
phword:
        pushw   %ax
        xchgb   %al,%ah
        call    phbyte
        movb    %ah,%al
        call    phbyte
        popw    %ax
        ret


phbyte:
        pushw   %ax
        movb    %al, %ah
        shrb    $4,%al
        call    phnibble
        movb    %ah, %al
        call    phnibble
        popw    %ax
        ret

phnibble:
        pushw   %ax
        andb    $0xf,%al
        addb    $48,%al
        cmpb    $57,%al
        jna     ph1
        add     $7,%al
ph1:    mov     $0xe,%ah
        int     $0x10
        popw    %ax
        ret

doc_seg:	.word 0
	
int19_execed_string:	.string "Loading GRUB from DiskOnChip\n\r"

	.org 0x100
stage1b:	

.globl doc_wait
.globl doc_cmd
.globl message
.globl phword
.globl phbyte
