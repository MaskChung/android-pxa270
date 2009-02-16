/* -*-Asm-*- */
/*
 *  DOCBoot -- A very simple Linux bootloader for DiskOnChip
 *
 *  Author: Dan Brown <dan_brown@ieee.org>
 *
 *  Diskonchip Millennium Plus support by Kalev Lember <kalev@smartlink.ee>
 *
 *  Portions taken from the DOC Grub bootloader by
 *          David Woodhouse <dwmw2@infradead.org>
 *
 *  $Id: doc_bootstub.S,v 1.8 2005/06/29 20:09:46 dbrown Exp $
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

#include "doc_bootstub.h"
	
	.file	"doc_bootstub.S"

	.text

	/* Tell GAS to generate 16-bit instructions so that this code works
	   in real mode. */
	.code16

.globl _start; _start:
	/*
	 * _start is loaded by the DiskOnChip IPL.  0x3000 bytes are loaded
	 * at the first empty (all-zeros) 64K region in the range 0x20000 to
	 * 0x90000 (with 1k alignment).  If the 64k region is at 0x20000, then
	 * CS:IP at this point will be 0x2000:0
	 * DS will be the segment of the DOC itself.
	 */

#ifdef BIOS_EXTENSION
 #ifndef DOC_ADDRESS
  #error BIOS_EXTENSION requires DOC_ADDRESS
 #endif

       .word	BIOS_SIG	/* BIOS extension signature */
       .byte	((stub_end-_start)>>9)	/* BIOS extension size in 512 byte blocks */

	/* BIOS will call far _start + 3 */
#endif

/* Jump to the installer, which will install an int 18h or 19h handler and then
   return, waiting for the handler to be called.  The installer is placed after
   the handler to minimize the amount of interrupt handler code copied to the
   top of low memory. */
	jmp	install

/* The actual int 18h/19h handler does three things:
   1) Copy the real-mode kernel from the DOC into the same 64k memory segment
      originally used by the MSYS IPL to store the 0x3000 bytes of SPL (on the
      theory that we already know it's safe to use).
   2) Copy the rest of the kernel (the protected-mode part) from the DOC into
      high memory starting at 0x100000.  BIOS int15h function 87h is used to
      perform the low-to-high copy.
   3) Copy the initrd (if there is one) to the top of high memory using
      int15/87h.
   4) Patch some variables in the real-mode kernel, that a Linux bootloader is
      supposed to set.  Then jump to the real-mode kernel.
 */

handler:
	pushw	%cs
	popw	%ds
	MSG(kernel_string)

	cld

	pushw	%cs

	movw	doc_seg, %ds
	movw	$BXREG, %bx

		/* Enable the DiskOnChip ASIC */
#ifdef MILPLUS
	movb	$(DOC_MODE_NORMAL | DOC_MODE_MDWREN | DOC_MODE_RST_LAT | DOC_MODE_BDECT), BX_Mplus_DOCControl
	movb	$~(DOC_MODE_NORMAL | DOC_MODE_MDWREN | DOC_MODE_RST_LAT | DOC_MODE_BDECT), BX_Mplus_CtrlConfirm

		/* Assert ChipEnable and WriteProtect */
	movb	$(DOC_FLASH_CE | DOC_FLASH_WP), BX_Mplus_FlashSelect
#else
	movb	$DOC_MODE_CLR_ERR + DOC_MODE_MDWREN + DOC_MODE_NORMAL, BX_DOCControl
	movb	$DOC_MODE_CLR_ERR + DOC_MODE_MDWREN + DOC_MODE_NORMAL, BX_DOCControl

		/* Flash command:	Reset */
	movb	$NAND_CMD_RESET, %al
	call	doc_cmd
#endif

	popw	%ds			/* now ds == cs */
	xorw	%di, %di
	xorw	%dx, %dx
read_setup_sects:
	call	doc_readpage
	decw	low_sects
	jnz	read_setup_sects

#ifdef DEBUG_BUILD
		/* Print the kernel version string. */
	pushw	%bx
	MSG(crlf_string)
	pushw	%es
	popw	%ds
	movw	kernel_version, %si
	addw	$0x200, %si
	call	message
	pushw	%cs
	popw	%ds
	MSG(kernel_string)
	popw	%bx
#endif

	pushw	%es

	movw	$0x8000, %di		/* Go to 32k from setup base */
	call	read_high_sects		/* copy the kernel sectors */

		/* At this point, cx=0 */
	movw	initrd_sects, %ax
	orw	%ax, high_sects		/* high_sects was previously 0 */
	jz	skip_initrd
	movb	%cl, gdt_dst_mid
	movw	initrd_start, %ax
	movb	%al, gdt_dst_hi
	movb	%ah, gdt_dst_vhi

	pushw	%bx
	MSG(initrd_string)
	popw	%bx

	call	read_high_sects		/* copy the initrd sectors */

skip_initrd:

/* We're done with the DOC.  Set up some things a kernel loader is supposed
   to set, then try to boot! */

	MSG(done_string)
	MSG(cmdline)
	MSG(boot_string)

	movl	initrd_bytes, %eax

	popw	%ds	/* Now write parameters into setup segment */

	orl	%eax, ramdisk_size  /* ramdisk_size should be 0 before this */
	jz	skip_initrd2
	movw	%cs:initrd_start, %ax
	movw	%ax, ramdisk_image + 2	/* initrd_start is 64k aligned */
skip_initrd2:

		/* Compute the physical address of the commandline and
		   store into cmd_line_ptr.  Simplified by the fact that
		   CS must be 1k-aligned and cmdline is at a 256-byte-aligned
		   offset from CS. */
	pushw	%cs
	popw	%ax
	shrw	$4, %ax
	addw	$((cmdline-_start)>>8), %ax
	movw	%ax, cmd_line_ptr + 1

	movb	$0x81, loadflags
	movw	$0xffff, %ax
	movb	%al, type_of_loader
	movw	%ax, heap_end_ptr	/* Actually should be 0xfdff, but it
					   should never get that far */

	movw	%ds, %bx
	movw	%bx, %fs	/* Not strictly needed */
	movw	%bx, %gs	/* Not strictly needed */
	movw	%bx, %ss
	movw	%ax, %sp	/* We know we have 64k of space */

	incw	%ax		/* Now ax=0 */
	addw	$0x20, %bx
	pushw	%bx
	pushw	%ax
	cli
	lret		/* GO! */

/***************************************************************************/
		/* read_high_sects: Read pages from DOC into high memory.
		   Each page is first loaded into low memory using doc_readpage,
		   then copied to high memory using int 15h function 87h.
		   We read high_sects pages (leaving high_sects = 0 when
		   we're done.) */
read_high_sects:
	call	doc_readpage
	movw	%ax, %di	/* re-use the same low memory buffer */
	pushw	%cs
	popw	%es
	movw	$gdt, %si
	movw	$0x100, %cx
	movb	$0x87, %ah
	stc
	int	$0x15
	jc	highcopy_fail
	test	%ah, %ah
	jz	highcopy_ok
highcopy_fail:
	pushw	%cs
	popw	%ds
	MSG(copyfail_string)
1:	jmp	1b		/* hang. */
highcopy_ok:
	addw	$2, gdt_dst_mid
	adcb	$0, gdt_dst_vhi
	decw	high_sects
	jnz	read_high_sects
	ret
/***************************************************************************/

		/* doc_readpage: Read a page from DOC to es:di
		   Then read the OOB.  If the magic tag (0xdbb1) isn't found
		   at OOB locations 6 and 7, try the next page, etc.  This
		   allows us not only to scan until we find the kernel image,
		   but also to skip over holes in the image (which are
		   presumably the result of bad blocks). */
nextpage:
	popw	%di
doc_readpage:
#ifdef DEBUG_BUILD
	pushw	%bx
	movw	$4, %cx
bsloop:
	movw	$0x0001, %bx
	movw	$0x0e08, %ax
	int	$0x10
	loop	bsloop
	popw	%bx
	movw	%dx, %ax
	call	phword
#endif
	/* cs must == ds at this point */
	movw	$SIREG, %si
	movw	setup_seg, %es
	movw	doc_seg, %ds
#ifdef MILPLUS
		/* Flash command:	Reset */
	movb	$NAND_CMD_RESET, %al
	call	doc_cmd
#endif
		/* Flash command:	Read0 */
	movb	$NAND_CMD_READ0, %al
	call	doc_cmd
#ifdef MILPLUS
	movb	$0, BX_Mplus_FlashAddress
	movb	%dl, BX_Mplus_FlashAddress
	movb	%dh, BX_Mplus_FlashAddress
		/* Terminate the write pipeline */
	movb	$0, BX_Mplus_WritePipeTerm
	movb	$0, BX_Mplus_WritePipeTerm
		/* Deassert ALE */
	movb	$0, BX_Mplus_FlashControl
	call	doc_wait
	testb	$0, BX_Mplus_ReadPipeInit
	testb	$0, BX_Mplus_ReadPipeInit
#else
	movb	$CDSN_CTRL_BASE + CDSN_CTRL_ALE, BX_CDSNControl
	DOC_DELAY4
	SLOWIO_WRITE($0)
	movb	$0, SI_CDSN_IO
	SLOWIO_WRITE(%dl)
	movb	%dl, SI_CDSN_IO
	SLOWIO_WRITE(%dh)
	movb	%dh, SI_CDSN_IO
  #ifndef OLD_DOC2K
		/* This test is used in the MSYS IPL.  It appears to check
		   an undocumented bit in the ConfigurationInput register,
		   presumably to determine if the NAND chip is large enough to
		   require a 3-byte page number. */
	testb	$0x20, BX_ConfigurationInput
	jz	notbigchip
	movb	$0, SI_CDSN_IO
notbigchip:
	call	doc_wait
	testb	BX_ReadPipeInit, %al
  #else
	call	doc_wait
  #endif
#endif
	movw	$0x208, %cx	/* Read page + 8 bytes OOB */
	pushw	%di
rploop:
	SLOWIO_READ
	movsb
	decw	%si
	loop	rploop
	pushw	%cs
	popw	%ds		/* restore ds == cs */
	incw	%dx
	jnz	dx_ok
	MSG(nodata_string)
1:	jmp	1b		/* hang. */
dx_ok:
	cmpw	$0xb1db, %es:-2(%di)
	jne	nextpage
	subw	$8, %di
	popw	%ax	/* di = old di + 512. Return original di in ax. cx=0. */
	ret

                /* doc_cmd:      Send a command to the flash chip */
doc_cmd:
#ifdef MILPLUS
		/* Send the command to the flash */
	movb	%al, BX_Mplus_FlashCmd
		/* Terminate the write pipeline */
	movb	$0, BX_Mplus_WritePipeTerm
	movb	$0, BX_Mplus_WritePipeTerm
	/* fall through... */

                /* doc_wait:     Wait for the DiskOnChip to be ready */
doc_wait:
	/* FIXME: probably only three NOP's are needed */
	testb	$0xc0, BX_Mplus_NOP
	testb	$0xc0, BX_Mplus_NOP
	testb	$0xc0, BX_Mplus_NOP
	testb	$0xc0, BX_Mplus_NOP
doc_waitloop:    
	movb	BX_Mplus_FlashControl, %al
	andw	$0xc0, %ax
	cmpw	$0xc0, %ax
	jne	doc_waitloop
	ret
#else
                /* Enable CLE line to flash */
	movb	$CDSN_CTRL_BASE + CDSN_CTRL_CLE, BX_CDSNControl
	DOC_DELAY4
                /* Write the actual command */
	SLOWIO_WRITE(%al)
  	movb	%al, BX_CDSN_IO		/* Can't use SI_CDSN_IO here! */
	/* fall through... */

                /* doc_wait:     Wait for the DiskOnChip to be ready */
doc_wait:
  #ifndef OLD_DOC2K
        movb	$0, BX_WritePipeTerm
  #endif
	movb	$CDSN_CTRL_BASE, BX_CDSNControl
	DOC_DELAY4
doc_waitloop:
	testb	$0x80, BX_CDSNControl
	jz	doc_waitloop
	DOC_DELAY2
	ret
#endif
                

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

/***************************************************************************/
#ifdef DEBUG_BUILD

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

#endif
/***************************************************************************/
handler_end:

#ifdef DOC_ADDRESS
doc_seg:	.word DOC_ADDRESS
#else
doc_seg:	.word 0
#endif

initrd_start:	.word	0

/* gdt structure for high loading using int15/87 */
gdt:
		.skip   0x10
gdt_src_limit:  .word   0xffff
gdt_src_lo:     .byte   0
gdt_src_mid:    .byte   0x80
gdt_src_hi:     .byte   0
gdt_src_flags1: .byte   0x93
gdt_src_flags2: .byte   0
gdt_src_vhi:    .byte   0
gdt_dst_limit:  .word   0xffff
gdt_dst_lo:     .byte   0
gdt_dst_mid:    .byte   0
gdt_dst_hi:     .byte   0x10
gdt_dst_flags1: .byte   0x93
gdt_dst_flags2: .byte   0
gdt_dst_vhi:    .byte   0
                .skip   0x10

#ifdef DEBUG_BUILD
kernel_string:	.string "\n\rLoading kernel... page 0x????"
initrd_string:	.string " Loading initrd... page 0x????"
done_string:	.string " done.\n\rCommandline: "
#else
kernel_string:	.string "Loading kernel... "
initrd_string:	.string "Loading initrd... "
done_string:	.string "done.\n\rCommandline: "
#endif

copyfail_string:.string "COPY TO HIGH MEMORY FAILED"
failed_string = . - 7
nodata_string:	.string "CANNOT FIND IMAGE ON DEVICE"
boot_string:	.string "\n\rBooting!\n\r"
crlf_string = (. - 3)

checksum:
setup_seg:	.word 0
low_sects:	.word 0
high_sects:	.word 0
initrd_sects:	.word 0
initrd_bytes:	.long 0

/* Make sure our parameters are in sync with the C code. */
.if ((.-checksum) <> PARAM_BYTES)
 .err
.endif

	.balign	256, 0xff	/* cmdline starts on 256-byte boundary... */
cmdline:
	.skip	256, 0xff	/* .. and is 256-bytes long. */
reloc_end:


/***************************************************************************
 ***************************************************************************
 Everything ABOVE this point is needed by the int 18h/19h handler, and will
 be copied to the top of low memory by the installer (below).  Everything
 below this point is needed only by the installer.
 ***************************************************************************
 ***************************************************************************/


install:
#ifndef DOC_ADDRESS
		/* Store the DiskOnChip segment */
	movw	%ds, %cs:doc_seg
#endif
	cld
	xorw	%dx, %dx
	movw	%dx, %ds

		/* Abort installation if any shift, control, or alt key is
		   depressed. */
	movb	0x0417, %al
	andb	$0x0f, %al
	jnz	skip_install

		/* Test for multiple loads due to aliased ROM addresses.  If
		   the code pointed to by the interrupt vector is identical to
		   our code, just return. */
	movw	(DOC_BIOS_HOOK * 4 + 2), %es
	pushw	%cs
	popw	%ds
	xorw	%di, %di
	xorw	%si, %si
	movw	$handler_end, %cx
	repe
	cmpsb
	je	skip_install

	pushw	%dx		/* push 0 */

	MSG(installer_string)

#ifdef DEBUG_BUILD
	MSG(docseg_string)
	movw	doc_seg, %ax
	call	phword
	MSG(setupseg_string)
#endif

		/* Store the setup segment */
	movw	%cs, %ax
	movw	%ax, setup_seg
#ifdef DEBUG_BUILD
	call	phword
#endif
		/* gdt_src is currently set to 0x8000.  Add the setup base
		   address to it, yielding a 32k offset from the start of the
		   setup segment.  The math is simplified by the fact that
		   setup_seg must be 1Kbyte aligned. */
	shrw	$4, %ax
	addw	%ax, gdt_src_mid

		/* Memory-size code below adapted from Syslinux */
	movw	$(0x4000-0x400), %di	/* 15M in 1k chunks */
#ifdef DEBUG_BUILD
	MSG(int15e801_string)
#endif
	stc
	movw	$0xe801, %ax
	int	$0x15
	jc	e801_fail
	cmpw	%di, %ax
	jne	e801_fail
	movw	%bx, %ax	/* now ax = amount of mem -16M, in 64k blocks */
#ifdef DEBUG_BUILD
	call	phword
#endif
	incb	%ah		/* Add 16M (in 64k blocks) back in. */
	jmp	got_topmem
e801_fail:
#ifdef DEBUG_BUILD
	MSG(int1588_string)
#endif
	movb	$0x88, %ah
	int	$0x15		/* Get top of high mem (-1M) in 1k-blocks */
#ifdef DEBUG_BUILD
	call	phword
#endif
	cmpw	%di, %ax	/* safety check: if it's above 15M ... */
	jbe	mem_le16meg
	movw	%di, %ax	/* ... replace with 15M. */
mem_le16meg:
	addw	$0x400, %ax	/* Add 1M (in 1k blocks) back in. */
	shrw	$6, %ax		/* Convert to 64k blocks (round down) */
got_topmem:
	movw	initrd_sects, %bx
	shrw	$7, %bx		/* Convert to 64k blocks (round down) */
	incw	%bx		/* Account for the fact that we rounded down. */
	subw	%bx, %ax	/* Compute start of initrd in 64k blocks */
	movw	%ax, initrd_start
#ifdef DEBUG_BUILD
	pushw	%ax
	MSG(initrdstart_string)
	popw	%ax
	call	phword
	MSG(lowmemtop_string)
#endif

	/* Now install the handler.  What we need to do is:
	   1. Check the current end-of-memory in the BIOS
	   2. Reduce it by the amount of memory we need for our INT 18h/19h 
	   3. Copy the handler code and data into the area we just reserved.
	   4. Return, and wait for our INT 18h/19h to be called.
	*/

		/* Find top of low memory */
	popw	%ds		/* pop 0 */
	movw	0x0413, %ax
#ifdef DEBUG_BUILD
	call	phword
#endif

		/* Steal enough room from the top.  The line below is
		   suboptimal for relocated code size of 2k bytes or less, but
		   it's not a big deal (1-2 bytes). */
	subw	$(((reloc_end-_start)+1023)>>10), %ax
	movw	%ax,0x0413

		/* Generate segment address */
	shlw	$6,%ax
	movw	%ax,%es

		/* Set up our shiny new INT 18h/19h handler */
	movw	%ax,(DOC_BIOS_HOOK * 4 + 2)
	movw	$handler,(DOC_BIOS_HOOK * 4)

		/* Copy the handler into the new segment we've just reserved */
	movw	$(reloc_end-_start), %cx
	pushw	%cs
	popw	%ds
	xorw	%si,%si
	xorw	%di,%di
	rep
	movsb

#ifdef DEBUG_BUILD
	pushw	%ax
	MSG(handlerseg_string)
	popw	%ax
	call	phword
	MSG(presskey_string)
keydrain:
	movb	$1, %ah
	int	$0x16
	movb	$0, %ah
	jz	keywait
	int	$0x16
	jmp	keydrain
keywait:
	int	$0x16
#endif

skip_install:
	lret	

installer_string: .string "Installing DOCBoot.\n\r"

#ifdef DEBUG_BUILD
docseg_string: .string "doc_seg = 0x"
setupseg_string: .string "    setup_seg = 0x"
int15e801_string: .string "\n\rint15/e801 returns 0x"
int1588_string: .string "FAILED\n\rint15/88 returns 0x"
initrdstart_string: .string "    initrd_start = 0x"
lowmemtop_string: .string "0000\n\rtop of low mem = 0x"
handlerseg_string: .string "k    handler seg = 0x"
presskey_string: .string "\n\r  -- Press any key --\n\r"
#endif

install_end:

	.balign	512, 0xff	/* Pad the entire stub to a 512-byte boundary */
stub_end:


/**************************************************************************
 **************************************************************************
   The symbols defined below will not be emitted by the assembler.  This
   is just here as a convenient way of encoding the offsets for use in the
   bootloader.
   This section adapted from arch/i386/boot/boot.S
 **************************************************************************
 **************************************************************************/

	.section absolute
	.org 512
kernel_start:	.word 0
        # This is the setup header, and it must start at %cs:2 (old 0x9020:2)
header_sig:	.ascii  "...."          # header signature
header_version: .word   0               # header version number (>= 0x0105)
realmode_swtch: .word   0, 0            # default_switch, SETUPSEG
start_sys_seg:  .word   0
kernel_version: .word   0		# pointing to kernel version string
type_of_loader: .byte   0               # = 0, old one (LILO, Loadlin,
loadflags:	.byte   0		# If set, the kernel is loaded high
setup_move_size: .word  0               # size to move, when setup is not
code32_start:	.long   0               # 0x100000 = default for big kernel
ramdisk_image:  .long   0               # address of loaded ramdisk image
ramdisk_size:   .long   0               # its size in bytes
bootsect_kludge: .word  0, 0
heap_end_ptr:   .word   0
pad1:           .word   0
cmd_line_ptr:   .long   0               # If nonzero, a 32-bit pointer
ramdisk_max:    .long   0               # The highest safe address
