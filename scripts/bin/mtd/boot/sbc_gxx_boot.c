/* sbc_mediagx_boot.c - boot kernel from flash.

   Copyright (C) 2000 Arcom Control System Ltd

   This program is free software; you can redistribute it and/or modify
   it under the terms of the GNU General Public License as published by
   the Free Software Foundation; either version 2 of the License, or
   (at your option) any later version.

   This program is distributed in the hope that it will be useful,
   but WITHOUT ANY WARRANTY; without even the implied warranty of
   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
   GNU General Public License for more details.

   You should have received a copy of the GNU General Public License
   along with this program; if not, write to the Free Software
   Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA

   $Id: sbc_gxx_boot.c,v 1.2 2005/11/07 11:14:18 gleixner Exp $

A boot loader which runs and loads the kernel directly from flash.  The kernel
command line is also read from flash.

Currently setup for an Arcom Control Systems SBC-MediaGX as follows
Flash address
00000           This boot loader BIOS extension
20000           Start of command line
20800           Start of kernel image (only zImage supported)
9FFFF           End of boot partition

25/04/2001  AJL (Arcom Control Systems) - Added bzImage support for systems with fast A20 gating support
                                          (SBC-MediaGX, SBC-GXm, SBC-GX1)

*/

// Includes

#include "local.h"
#include <asm/io.h>
#include <asm/boot.h>

// Defines

#define VERSION 1
#define ISSUE   02

#define BOARD_NAME "SBC-GXx Series Boards"

// - Do we support bzImages ?
#define SUPPORT_BZIMAGES

// - Where we can find the kernel
#define CMDLINE_START 128*1024
#define CMDLINE_SIZE  2*1024
#define KERNEL_START (CMDLINE_START+CMDLINE_SIZE)

// - Hardware specifics

#define PORT92 0x92

#define WINDOW_START 0xdc000
/* Number of bits in offset. */
#define WINDOW_SHIFT 14
#define WINDOW_LENGTH (1 << WINDOW_SHIFT)
/* The bits for the offset into the window. */
#define WINDOW_MASK (WINDOW_LENGTH-1)
#define PAGE_IO 0x258
#define PAGE_IO_SIZE 2
/* bit 7 of 0x259 must be 1 to enable device. */
#define DEVICE_ENABLE 0x8000

// Defines for the linux loader
#define SETUP_SIZE_OFF 497
#define SECTSIZE       512
#define SETUP_VERSION  0x0201
#define SETUP_HIGH     0x01
#define DEF_BOOTLSEG    0x9020
#define LOADER_TYPE    0x40

// Structures

// - From etherboot, this is the header to the image startup code
struct setup_header
{
   __u8 jump[2];
   __u8 magic[4];     /* "HdrS" */
   __u16 version;      /* >= 0x0201 for initrd */
   __u8 realmode_swtch[4];
   __u16 start_sys_seg;
   __u16 kernel_version;
   /* note: above part of header is compatible with loadlin-1.5 (header v1.5),*/
   /*       must not change it */
   __u8 type_of_loader;
   __u8 loadflags;
   __u16 setup_move_size;
   unsigned long code32_start;
   unsigned long ramdisk_image;
   unsigned long ramdisk_size;
   unsigned long bootsect_kludge;
   __u16 heap_end_ptr;
};

// - Current page in window
static int iCurPage = -1;

// -------------------------------------------------------------------
// Board Specific Access Routines
// -------------------------------------------------------------------

/*
 * Function: vPropagateA20( void )
 *
 *           Do a PORT20 A20 propogation assertion to ensure we
 *           can talk to high memory
 */
void vPropagateA20( void )
{
    outb( inb(PORT92) | 0x02, PORT92);
}

/*
 * Function: sbc_gxx_flash_page( unsigned long ofs )
 *
 * Description: Pages the SBC-GXm / SBC-GX1 flash window
 *              to make the byte at flash offset 'ofs'
 *              visible *somewhere* in the window
 *
 * Parameters:  unsigned long ofs - 32-bit flash offset
 *
 * Returns:     nothing
 *
 */
static inline void sbc_gxx_flash_page(unsigned long ulOfs)
{
  int iPage = (int)(ulOfs >> WINDOW_SHIFT);

  if( iPage != iCurPage )
  {
    outw( ((unsigned int)iPage) | DEVICE_ENABLE, PAGE_IO );
    iCurPage = iPage;
  }
}

/*
 * Function: sbc_gxx_flash_copy( void *pTo, unsigned long pFrom, unsigned long ulLen)
 *
 * Description: Copy bytes from (windowed) flash array to low memory
 *
 * Parameters:  void *pTo - Pointer to start of destination buffer
 *
 *              unsigned long pFrom - Pointer to start flash offset
 *
 *              unsigned long ulLen - Length (bytes) to copy
 *
 * Returns:     nothing
 *
 */
static void sbc_gxx_flash_copy(void *pTo, unsigned long ulFrom, unsigned long ulLen)
{
    while(ulLen)
    {
      unsigned long ulThisLen = ulLen;

      if (ulLen > (WINDOW_LENGTH - (ulFrom & WINDOW_MASK)))
       ulThisLen = WINDOW_LENGTH - (ulFrom & WINDOW_MASK);

      sbc_gxx_flash_page(ulFrom);

      memcpy(pTo, (__u8*)(WINDOW_START + (ulFrom & WINDOW_MASK)), ulThisLen);

      (__u8*)pTo += ulThisLen;

      ulFrom += ulThisLen;
      ulLen -= ulThisLen;
    }
}

// -------------------------------------------------------------------
// Generic Functions
// -------------------------------------------------------------------

/*
 * Function: iReadKernelImage( void )
 *
 * Description: Read in the kernel image from a region of flash
 *
 * Parameters: none
 *
 * Notes:
 *
 * This creates the same load map that the etherboot linux loader uses,
 *     0x10000-0x8FFFF     512kB   kernel and part of kernel setup
 *     0x90000-0x901FF     0.5kB   linux floppy boot sector
 *     0x90200-0x911FF       8kB   first sectors of kernel setup
 *     0x92200-0x931FF       4kB   primary boot loader
 *     0x93200-0x933FF     0.5kB   load header
 *     0x93400-0x93BFF       2kB   default command line
 *
 * Returns: == 0     -    Success
 *          != 0     -    Failure
 */

int iReadKernelImage( void )
{
    unsigned char *pTmp;
    unsigned short *pTmpS;

    unsigned long ulSetupSize;
    struct setup_header *pHdr;

    // Read the floppy loader to 0x90000
    pTmp = (unsigned char *)(DEF_INITSEG << 4);
    sbc_gxx_flash_copy(pTmp,0+KERNEL_START,512);

    // Check end of sector for the magic numbers
    if (pTmp[510] != 0x55 || pTmp[511] != 0xAA)
    {
      printk("Bad loader code\n");
      return -1;
    }

    /* 1 byte value at the end of the loader is the number of sectors of setup
       code */
    ulSetupSize = ((unsigned long)pTmp[SETUP_SIZE_OFF]) * SECTSIZE;

    // Read the setup code
    pTmp = (unsigned char *)(DEF_SETUPSEG << 4);
    sbc_gxx_flash_copy(pTmp,512+KERNEL_START, ulSetupSize);

    // Check the setup header
    pHdr = (struct setup_header *)pTmp;

    // Are magic number and version OK ?
    if (memcmp(pHdr->magic,"HdrS",4) != 0 || pHdr->version < SETUP_VERSION)
    {
      printk("Bad setup code\n");
      return -1;
    }

    // Set kernel destination
    pTmp = (unsigned char *)(pHdr->code32_start);

    // Dump kernel destination to screen
    printf("Loading kernel to 0x%X\n", (unsigned long)pTmp);

    // Is this a Big Kernel? (bzImage)
    if ((pHdr->loadflags & SETUP_HIGH) == SETUP_HIGH)
      {

#ifndef SUPPORT_BZIMAGES
      printf( "Can't handle big images.\n" );
	return -1;
#else
	// Make sure we diddle the type of loader so kernel setup will play with us...
	pHdr->type_of_loader = LOADER_TYPE; // (emulate ETHERBOOT version 0)

	/* Read the kernel EEK! How long is the kernel ?
	   - we've increased the max size from (512-10)K up to
	   (640-10K). Uses more flash, but what can we do...*/
	sbc_gxx_flash_copy(pTmp,512+ulSetupSize+KERNEL_START, (640-2)*1024);
#endif

      }
    else
      {
	pTmp = (unsigned char *)(DEF_SYSSEG << 4);
	sbc_gxx_flash_copy(pTmp,512+ulSetupSize+KERNEL_START,(512-10)*1024);
      }

    /* Prepare the command line
       0x90020-0x90021     2 bytes   command line magic number
       0x90022-0x90023     2 bytes   command line offs. relative to floppy boot sec
       0x901FA-0x901FB     2 bytes   video mode */

    pTmpS = (unsigned short *)(DEF_INITSEG << 4);
    pTmpS[0x10] = 0xA33F;
    pTmpS[0x11] = 0x93400 - 0x90000;

    pTmp = (unsigned char *)(0x93400);
    sbc_gxx_flash_copy( pTmp, CMDLINE_START, CMDLINE_SIZE );

    printk( "Command line: %s\n", pTmp );

    return 0;
}

int main()
{
    // Turn on A20 line propagation (so 1Mb accesses don't wrap)
    vPropagateA20();

    // Signon - NOTE: Important - Due to the way the putchar() function is
    //                implemented in start32.S we will be in PROTECTED MODE
    //                on our return. This is a *good* thing for loading in
    //                bzImages high
    printf("Arcom Control Systems Embedded Linux flash boot loader\n");
    printf("Version %d.%d - Built for %s\n\n", VERSION, ISSUE, BOARD_NAME);

    // Read in the kernel...
    if (iReadKernelImage() != 0) {
	printk("Failed while reading image\n");
	return 0;
    }

    // Start the kernel...
    xstart(DEF_BOOTLSEG << 16,0,0);

  return 0;
}
