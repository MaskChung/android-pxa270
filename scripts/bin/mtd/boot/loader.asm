! At entry, the processor is in 16 bit real mode and the code is being
! executed from an address it was not linked to. Code must be pic and
! 32 bit sensitive until things are fixed up.

#include "loader.inc"

	.text
_main:
	.word	0xAA55			! BIOS extension signature
size:	.byte	0			! number of 512 byte blocks
					! = number of 256 word blocks
					! filled in by makerom program
	jmp	over			! skip over checksum
	.byte	0			! checksum 
	jmp	blockmove		! alternate entry point +6
					! used by floppyload
over:
#ifndef	NOINT19H
	push	ax
	push	ds
	xor	ax,ax
	mov	ds,ax			! access first 64kB segment
	mov	ax,SCRATCHVEC+4		! check if already installed
	cmp	ax,#MAGIC		! check magic word
	jz	installed
	mov	ax,INT19VEC		! hook into INT19h
	mov	SCRATCHVEC,ax
	mov	ax,INT19VEC+2
	mov	SCRATCHVEC+2,ax
	mov	ax,#start19h
	mov	INT19VEC,ax
	mov	ax,cs
	mov	INT19VEC+2,ax
	mov	ax,#MAGIC		! set magic word
	mov	SCRATCHVEC+4,ax
installed:
	pop	ds
	pop	ax
	retf
start19h:				! clobber magic id, so that we will
	xor	ax,ax			!   not inadvertendly end up in an
	mov	ds,ax			!   endless loop
	mov	SCRATCHVEC+4,ax
	mov	ax,SCRATCHVEC+2		! restore original INT19h handler
	mov	INT19VEC+2,ax
	mov	ax,SCRATCHVEC
	mov	INT19VEC,ax
#endif	/* NOINT19H */
blockmove:
        mov	si,#_body-_main		! offset of _body
	/* fall thru */
! Relocate body of boot code to RELOC:0
	cld
	xor	di,di			! di = 0
	mov	ax,#RELOC>>4
        mov     es,ax

	xor	cx,cx
	mov	ax,cs
	mov	ds,ax			! for the ref to size below
	mov	ch,size-_main
	push	cx			! save cx before decrementing
	seg	cs
        rep
        movsw

! Save ROMs CS and length in floppy boot block before jumping to relocated
! code
	pop	cx
	push	ds
	mov	ax,#FLOPPY_SEGMENT
	mov	ds,ax
	mov	ax,cs
	mov	ROM_SEGMENT,ax
	mov	ROM_LENGTH,cx
	pop	ds

! Change stack
	mov	bx,#RELOC>>4		!  new ss
	mov	ds,bx			!  new ds

	int	#0x12			!  get conventional memory size in KB
	shl	ax, #6
	sub	ax, bx			
	shl	ax, #4			!  new sp
	cli
	mov	ss,bx
	mov	sp,ax
	callf	#0,RELOC>>4		!  call Etherboot body
	int	#0x19

! The body of etherboot is attached here at build time
! Force 4 byte alignment
	if	((*-_main)&3) != 0
	.blkb	3-((*-_main)&3)
	.byte	0
	endif
_body:
