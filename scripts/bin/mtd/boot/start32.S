#define KERN_CODE_SEG	0x08
#define KERN_DATA_SEG	0x10
#define REAL_MODE_SEG	0x18
#define REAL_MODE_DSEG	0x20
#define CR0_PE		1

/*
 * NOTE: if you write a subroutine that is called from C code (gcc/egcs),
 * then you only have to take care of %ebx, %esi, %edi and %ebp.  These
 * registers must not be altered under any circumstance.  All other registers
 * may be clobbered without any negative side effects.  If you don't follow
 * this rule then you'll run into strange effects that only occur on some
 * gcc versions (because the register allocator may use different registers).
 *
 * All the data32 prefixes for the ljmp instructions are necessary, because
 * the assembler emits code with a relocation address of 0.  This means that
 * all destinations are initially negative, which the assembler doesn't grok,
 * because for some reason negative numbers don't fit into 16 bits. The addr32
 * prefixes are there for the same reasons, because otherwise the memory
 * references are only 16 bit wide.  Theoretically they are all superfluous.
 * One last note about prefixes: the data32 prefixes on all call _real_to_prot
 * instructions could be removed if the _real_to_prot function is changed to
 * deal correctly with 16 bit return addresses.  I tried it, but failed.
 */

/**************************************************************************
START - Where all the fun begins....
**************************************************************************/
/* this must be the first thing in the file because we enter from the top */
	.global	_start
_start:
	.code16
	movw	%ss,%bx
	movw	%sp,%cx
	data32	call	_real_to_prot

	.code32
	movw	%bx,initss
	movw	%cx,initsp
	
#ifdef SERIAL_CONSOLE
	call	initserial
#endif
	call	main
	/* fall through */

	.globl	exit
exit:
	movw	initss,%bx
	movw	initsp,%cx
	call	_prot_to_real
	.code16
/*	we reset sp to the location just before entering main
	instead of relying on the return from main because exit
	could have been called from anywhere */
	movw	%bx,%ss
	movw	%cx,%sp
	lret
	.code32

initss:
	.word	0
initsp:
	.word	0

/**************************************************************************
SLOWDOWNIO - Short delay for I/O port accesses
**************************************************************************/
	.globl	slowdownio
slowdownio:
	jmp	1f
1:	jmp	2f
2:	ret

/**************************************************************************
CURRTICKS - Get Time
**************************************************************************/
	.globl	currticks
currticks:
	pushl	%ebx
	pushl	%esi
	pushl	%edi
	xorl	%edx,%edx
	call	_prot_to_real
	.code16
	xorw	%ax,%ax
	int	$0x1a
	data32	call	_real_to_prot
	.code32
	xorl	%eax,%eax
	shll	$16,%ecx
	movl	%edx,%eax
	orl	%ecx,%eax
	popl	%edi
	popl	%esi
	popl	%ebx
	ret

#ifdef SERIAL_CONSOLE
/**************************************************************************
INITSERIAL - Initialize serial port. Stolen from lilo
**************************************************************************/
	.globl	initserial
initserial:
	pushl	%ebx
	pushl	%esi
	pushl	%edi
	xorl	%ecx,%ecx
	movb	$COMPORT,%cl
	movb	$COMPARM,%ch	/* 0xe3 == 9600N8, 0xa3 == 2400N8 */
	call	_prot_to_real
	.code16
	xorw	%ax,%ax
	xorw	%dx,%dx
	movb	%cl,%dl
	xchgb	%ch,%al
	int	$0x14		/* initializes port. Base address of port is stored at 0x400 + offset */
	data32	call	_real_to_prot
	.code32
	shll	%ecx
	addl	$0x400,%ecx
	movzwl	(%ecx),%ebx
	movl	%ebx,slbase
	popl	%edi
	popl	%esi
	popl	%ebx
	ret	

slbase:	
	.long	0x0

/**************************************************************************
SERDISP - Print a character on a serial line. Stolen from lilo.
**************************************************************************/
serdisp:
	pushl	%eax		/* wait for space in the send buffer */
	addl	$5,%edx
1:
	inb	%dx,%al
	testb	$0x20,%al	/* ready to send ? */
	jz	1b		/* no -> wait */
	subl	$5,%edx		/* send the character */
	popl	%eax
	outb	%al,%dx
	ret
#endif	/* SERIAL_CONSOLE */

/**************************************************************************
PUTCHAR - Print a character
**************************************************************************/
	.globl	putchar
putchar:
	pushl	%ebp
	movl	%esp,%ebp
	pushl	%ebx
	pushl	%esi
	pushl	%edi
#ifdef SERIAL_CONSOLE
	movl	slbase,%edx
	orl	%edx,%edx
	jz	1f
	movb	8(%ebp),%al
	call	serdisp
1:	
#endif
#ifdef	ANSIESC
	movzbl	8(%ebp),%ecx
	pushl	%ecx
	call	handleansi
	addl	$4,%esp
#else
	movb	8(%ebp),%cl
	call	_prot_to_real
	.code16
	movl	$1,%ebx
	movb	$0x0e,%ah
	movb	%cl,%al
	int	$0x10
	data32 call	_real_to_prot
	.code32
#endif
	popl	%edi
	popl	%esi
	popl	%ebx
	popl	%ebp
	ret

/**************************************************************************
INT10 - Call Interrupt 0x10
**************************************************************************/
#ifdef	ANSIESC
	.globl	_int10
_int10:
	push	%ebp
	mov	%esp,%ebp
	push	%ebx
	push	%esi
	push	%edi
	movw	8(%ebp),%si
	movw	10(%ebp),%bx
	movw	12(%ebp),%cx
	movw	14(%ebp),%dx
	call	_prot_to_real
	.code16
	movw	%si,%ax
	int	$0x10
	movw	%ax,%si
	data32	call	_real_to_prot
	.code32
	movw	%si,int10ret
	movw	%bx,int10ret+2
	movw	%cx,int10ret+4
	movw	%dx,int10ret+6
	pop	%edi
	pop	%esi
	pop	%ebx
	pop	%ebp
	ret

	.globl	int10ret
int10ret:
	.word	0,0,0,0
#endif

/**************************************************************************
GETCHAR - Get a character
**************************************************************************/
	.globl	getchar
getchar:
	pushl	%ebx
	pushl	%esi
	pushl	%edi

#ifdef SERIAL_CONSOLE
	movl	slbase,%edx
	orl	%edx,%edx
	jz	3f		/* no serial port available */
1:	
	addl	$5,%edx
2:	
	inb	%dx,%al
	testb	$1,%al
	jz	2b
	subl	$5,%edx
	inb	%dx,%al
	andl	$0x7f,%eax
	je	1b
	popl	%edi
	popl	%esi
	popl	%ebx
	ret
3:	
#endif
	call	_prot_to_real
	.code16
	movb	$0x0,%ah
	int	$0x16
	movb	%al,%bl
	data32 	call	_real_to_prot
	.code32
	xor	%eax,%eax
	movzbl	%bl,%eax

	popl	%edi
	popl	%esi
	popl	%ebx
	ret

/**************************************************************************
ISKEY - Check for keyboard interrupt
**************************************************************************/
	.globl	iskey
iskey:
	pushl	%ebx
	pushl	%esi
	pushl	%edi
#ifdef SERIAL_CONSOLE
	movl	slbase,%edx
	orl	%edx,%edx
	jz	1f		/* no serial port, we loose */
	addl	$5,%edx
	inb	%dx,%al
	andl	$0x01,%eax
	popl	%edi
	popl	%esi
	popl	%ebx
	ret
1:
#endif
	call	_prot_to_real
	.code16
	xorw	%bx,%bx
	movb	$0x1,%ah
	int	$0x16
	jz	2f
	movb	%al,%bl
2:
	data32	call	_real_to_prot
	.code32
	movzbl	%bl,%eax
	popl	%edi
	popl	%esi
	popl	%ebx
	ret

#ifndef SERIAL_CONSOLE
/**************************************************************************
GETSHIFT - Get keyboard shift state
**************************************************************************/
	.globl	getshift
getshift:
	pushl	%ebx
	pushl	%esi
	pushl	%edi
	call	_prot_to_real
	.code16
	movb	$2,%ah
	int	$0x16
	andb	$0xdf,%al
	movw	%ax,%bx
	data32	call	_real_to_prot
	.code32
	movzbl	%bl,%eax
	popl	%edi
	popl	%esi
	popl	%ebx
	ret
#endif

/**************************************************************************
MEMSIZE - Determine size of extended memory
**************************************************************************/
	.globl	memsize
memsize:
	pushl	%ebx
	pushl	%esi
	pushl	%edi
	call	_prot_to_real
	.code16
	movw	$0x8800,%ax
	int	$0x15
	movw	%ax,%bx
	data32	call	_real_to_prot
	.code32
	movzwl	%bx,%eax
	popl	%edi
	popl	%esi
	popl	%ebx
	ret

/**************************************************************************
DISK_INIT - Initialize the disk system
**************************************************************************/
#ifdef FLOPPY
	.globl	disk_init
disk_init:
	pushl	%ebx
	pushl	%esi
	pushl	%edi
	call	_prot_to_real
	.code16
	xorw	%ax,%ax
	movb	$0x80,%dl
	int	$0x13
	data32	call	_real_to_prot
	.code32
	popl	%edi
	popl	%esi
	popl	%ebx
	ret
#endif

/**************************************************************************
DISK_READ - Read a sector from disk
**************************************************************************/
#ifdef FLOPPY
	.globl	disk_read
disk_read:
	pushl	%ebp
	movl	%esp,%ebp
	pushl	%ebx
	pushl	%esi
	pushl	%edi
	movb	8(%ebp),%dl	/* drive number    */
	movb	16(%ebp),%dh	/* head number     */
	movb	12(%ebp),%ch	/* cylinder number */
	movb	13(%ebp),%cl	/* cylinder number */
	shl	$6,%cl
	orb	20(%ebp),%cl	/* sector number   */
	movw	26(%ebp),%si
	rorw	$4,%esi
	movw	24(%ebp),%bx	/* buffer          */
	call	_prot_to_real
	.code16
	movw	$0x0201,%ax
	movw	%si,%es
	int	$0x13
	jc	1f
	xorw	%ax,%ax
1:
	movw	%ax,%bx
	data32	call	_real_to_prot
	.code32
	movzwl	%bx,%eax
	popl	%edi
	popl	%esi
	popl	%ebx
	popl	%ebp
	ret
#endif

/**************************************************************************
XSTART - Transfer control to the kernel just loaded
**************************************************************************/
	.globl	xstart
xstart:
	pushl	%ebp
	movl	%esp,%ebp
	pushl	%ebx
	pushl	%esi
	pushl	%edi
	movl	8(%ebp),%eax
	movl	%eax,execaddr
	movl	12(%ebp),%ebx
	movl	16(%ebp),%ecx	/* bootp record (32bit pointer) */
	addl	$28,%ecx	/* ip, udp header */
	shll	$12,%ecx
	shrw	$12,%cx
	call	_prot_to_real
	.code16
	pushl	%ecx		/* bootp record */
	pushl	%ebx		/* file header */
	movl	$((RELOC<<12)+(1f-RELOC)),%eax
	pushl	%eax
	addr32 ljmp	execaddr-RELOC
1:
	addw	$8,%sp		/* XXX or is this 10 in case of a 16bit "ret" */
	data32	call	_real_to_prot
	.code32
	popl	%edi
	popl	%esi
	popl	%ebx
	popl	%ebp
	ret

execaddr:
	.long	0

/**************************************************************************
SETJMP - Save stack context for non-local goto
**************************************************************************/
	.globl	setjmp
setjmp:
	movl	4(%esp),%ecx
	movl	0(%esp),%edx
	movl	%edx,0(%ecx)
	movl	%ebx,4(%ecx)
	movl	%esp,8(%ecx)
	movl	%ebp,12(%ecx)
	movl	%esi,16(%ecx)
	movl	%edi,20(%ecx)
	movl	%eax,24(%ecx)
	movl	$0,%eax
	ret

/**************************************************************************
SETJMP - Non-local jump to a saved stack context
**************************************************************************/
	.globl	longjmp
longjmp:
	movl	4(%esp),%edx
	movl	8(%esp),%eax
	movl	0(%edx),%ecx
	movl	4(%edx),%ebx
	movl	8(%edx),%esp
	movl	12(%edx),%ebp
	movl	16(%edx),%esi
	movl	20(%edx),%edi
	cmpl	$0,%eax
	jne	1f
	movl	$1,%eax
1:	movl	%ecx,0(%esp)
	ret

/**************************************************************************
_REAL_TO_PROT - Go from REAL mode to Protected Mode
**************************************************************************/
	.globl	_real_to_prot
_real_to_prot:
	.code16
	cli
	cs
	addr32 lgdt	gdtarg-RELOC
	movl	%cr0,%eax
	orl	$CR0_PE,%eax
	movl	%eax,%cr0		/* turn on protected mode */

	/* flush prefetch queue, and reload %cs:%eip */
	data32	ljmp	$KERN_CODE_SEG,$1f
1:
	.code32
	/* reload other segment registers */
	movl	$KERN_DATA_SEG,%eax
	movw	%ax,%ds
	movw	%ax,%es
	movw	%ax,%ss
	addl	$RELOC,%esp		/* Fix up stack pointer */
	xorl	%eax,%eax
	movw	%ax,%fs
	movw	%ax,%gs
	popl	%eax			/* Fix up return address */
	addl	$RELOC,%eax
	pushl	%eax
	ret

/**************************************************************************
_PROT_TO_REAL - Go from Protected Mode to REAL Mode
**************************************************************************/
	.globl	_prot_to_real
_prot_to_real:
	.code32
	popl	%eax
	subl	$RELOC,%eax		/* Adjust return address */
	pushl	%eax
	subl	$RELOC,%esp		/* Adjust stack pointer */
	ljmp	$REAL_MODE_SEG,$1f-RELOC	/* jump to a 16 bit segment */
1:
	.code16
	movw	$REAL_MODE_DSEG,%ax
	movw	%ax,%ds
	movw	%ax,%ss
	movw	%ax,%es
	movw	%ax,%fs
	movw	%ax,%gs

	/* clear the PE bit of CR0 */
	movl	%cr0,%eax
	andl	$0!CR0_PE,%eax
	movl	%eax,%cr0

	/* make intersegment jmp to flush the processor pipeline
	 * and reload %cs:%eip (to clear upper 16 bits of %eip).
	 */
	data32 ljmp	$(RELOC)>>4,$2f-RELOC
2:
	/* we are in real mode now
	 * set up the real mode segment registers : %ds, $ss, %es
	 */
	movw	%cs,%ax
	movw	%ax,%ds
	movw	%ax,%es
	movw	%ax,%ss
	sti
	data32	ret		/* There is a 32 bit return address on the stack */
	.code32

/**************************************************************************
GLOBAL DESCRIPTOR TABLE
**************************************************************************/
	.align	4
_gdt:
gdtarg:
	.word	0x27			/* limit */
	.long	_gdt			/* addr */
	.byte	0,0

_pmcs:
	/* 32 bit protected mode code segment */
	.word	0xffff,0
	.byte	0,0x9f,0xcf,0

_pmds:
	/* 32 bit protected mode data segment */
	.word	0xffff,0
	.byte	0,0x93,0xcf,0

	/* 16 bit real mode code segment */
	.word	0xffff,(RELOC&0xffff)
	.byte	(RELOC>>16),0x9b,0x00,(RELOC>>24)

	/* 16 bit real mode data segment */
	.word	0xffff,(RELOC&0xffff)
	.byte	(RELOC>>16),0x93,0x00,(RELOC>>24)

	.align	4
