/* If the Makefile does not provide configuration options, then set
   reasonable default values.
*/

#if defined(MOVEROM)
#undef	MOVEROM
#define MOVEROM		0x80000
#endif

/* Scratch space has to be at least 10kB; but because of the relocation
   address at 0x98000 we cannot use more then 32kB for uncompressed data
   anyways, so lets give all the rest of the segment to the scratch area.
 */

#ifndef SCRATCH
#define SCRATCH         0x8000
#endif


/* We need some unique magic ID, if we defer startup thru the INT19h
   handler. This way, we can check if we have already been installed.
 */

#ifndef MAGIC
#define MAGIC           0xE44C
#endif

/* Hook into INT19h handler
 */

#define INT19VEC	0x64
#define SCRATCHVEC	0x300

/* Offsets of words containing ROM's CS and size (in 512 byte blocks)
   from start of floppy boot block at 0x7C00
 */

#define	FLOPPY_SEGMENT	0x7C0
#define	ROM_SEGMENT	506
#define	ROM_LENGTH	508

/* Do not change these values unless you really know what you are doing;
   the pre-computed lookup tables rely on the buffer size being 4kB or
   smaller. The buffer size must be a power of two. The lookahead size has
   to fit into 6 bits. If you change any of these numbers, you will also
   have to adjust the compressor accordingly.
 */

#define BUFSZ           4096
#define LOOKAHEAD       60
#define THRESHOLD       2
#define NCHAR           (256+LOOKAHEAD-THRESHOLD)
#define TABLESZ         (NCHAR+NCHAR-1)
#define ROOT            (TABLESZ-1)
