// -*- mode: cpp; mode: fold -*-
// Description                                                          /*{{{*/
// $Id: main.c,v 1.2 2005/11/07 11:14:18 gleixner Exp $
/* ######################################################################

   Linux Flash File System boot loader

   This loader should be able to work with any sort of linearly paged flash
   that is mapped into the bios extension region. To do so you have to
   customize the first section to allow it to speak to your flash. The
   loader reads a FFS2 filesystem directly to locate the image and read it
   into ram. You'll need at least a 4K window size, prefferably 8K to fit
   the entire loader in. If you only have 4k then you have to use the
   SMALLER loader that omits error messages and error checking code to
   fit into 4k.

   TODO:
     - A Simple and worthwhile thing would be to read the kernel command
       line from the file /commandline on the flash - this would remove the
       need to hardcode one in here. Even better would be a config file, but
       I haven't the faintest idea what would be usefull to put in one!

   ##################################################################### */
									/*}}} */
#include "local.h"
#include "io.h"
#include <asm/io.h>
#include <asm/boot.h>

/* No more than 256 blocks must be used by the FFS2. If your flash window size
   is over 4k then you must compile with -DSMALLER defined to strip out enough
   code to fit the extension in the 4k window */
static unsigned long EraseSize = 0x20000;
static unsigned long FlashSize = 2*1024*1024;
static unsigned long WindowSize = 32*1024;
static unsigned char *FlashWindow = (void *)0xe8000;
#define PAGE_IO 0x208
#define CMDLINE "auto rw root=/dev/mtd1"

// PageTo - Window Swapping function					/*{{{*/
// ---------------------------------------------------------------------
/* Handles the specific flash window implemenation. This one is for the
   Octagon board */
void PageTo(unsigned long Window)
{
   outb(Window | (2 << 6),PAGE_IO);
}
									/*}}}*/

// Defines for the linux loader
#define SETUP_SIZE_OFF 497
#define SECTSIZE       512
#define SETUP_VERSION  0x0201
#define SETUP_HIGH     0x01
#define BIG_SYSSEG     0x10000
#define DEF_BOOTLSEG    0x9020

// From etherboot, this is the header to the image startup code
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

// ffs2_read - Read function from mapped flash				/*{{{*/
// ---------------------------------------------------------------------
/* This provides read capability to the FFS2 stuff, it is a simple wrapper
   around the window swapping function */
unsigned char *ffs2_read(struct ffs_read *r,unsigned long block,
			 unsigned long offset,unsigned long count)
{
   unsigned long loc;
   unsigned long left;
   unsigned char *pos;

   if (getFFS2_sb(r->super).Boot.TotalBlockCount != 0 &&
       block >= getFFS2_sb(r->super).Boot.TotalBlockCount)
      return 0;

   r->block = block;
   r->offset = offset;

   block += getFFS2_sb(r->super).ZeroBlock;
   loc = getFFS2_sb(r->super).EraseSize*block + offset;

   // We can return a full page
   if (loc/WindowSize == (loc + count - 1)/WindowSize)
   {
      PageTo(loc/WindowSize);

      r->behind = loc % WindowSize;
      r->ahead = WindowSize - r->behind;
      r->p = FlashWindow + r->behind;
      return r->p;
   }

   // Doomed :>
#ifndef SMALLER
   if (count > sizeof(r->temp))
   {
      printk("ffs2: Reading too much");
      return 0;
   }
#endif

   // Need to use the temp buffer
   pos = r->temp;
   memset(r->temp,0,sizeof(r->temp));
   left = count;
   while (left != 0)
   {
      unsigned long behind;
      unsigned long ahead;
      PageTo(loc/WindowSize);

      behind = loc % WindowSize;
      ahead = WindowSize - behind;
      if (left < ahead)
	 ahead = left;
      memcpy(pos,FlashWindow + behind,ahead);
      pos += ahead;
      loc += ahead;
      left -= ahead;
   }
   r->behind = 0;
   r->ahead = count;
   r->p = r->temp;
   return r->p;
}
									/*}}}*/

// ffs2_readfile - Read a file from the flash				/*{{{*/
// ---------------------------------------------------------------------
/* This is alike ffs2_readpage */
static int ffs2_readfile(struct ffs_read *r,unsigned long loc,
			 unsigned char *to,unsigned long start,
			 unsigned long stop)
{
   struct ffs2_entry *entry;
   unsigned long cur;
   unsigned long len = 0;
   unsigned long offset = 0;
   unsigned long toread;
   int res;

   // Get the inode and follow to find the first extent
   if ((entry = ffs2_find_entry(r,loc)) == 0 ||
       isFNULL(entry->PrimaryPtr) ||
       (entry->Status & FFS_ENTRY_PRIMARY) == FFS_ENTRY_PRIMARY)
      return -1;

   // Check for compression
   if ((entry->Status >> FFS_ENTRY_COMPIP_SHIFT) != 0xFF)
   {
      printk("ffs2: No support for compressed format %x\n",
	     entry->Status >> FFS_ENTRY_COMPIP_SHIFT);
      return -1;
   }

   cur = entry->PrimaryPtr;
   while (1)
   {
      struct ffs2_fileinfo *extent = (struct ffs2_fileinfo *)ffs2_find_entry(r,cur);
      if (extent == 0)
	 break;

      if (!isflagset(extent->Status,FFS_ENTRY_TYPEMASK,FFS_ENTRY_TYPEEXTENT))
	 break;

      // Skip cur to the next one
      if (isFNULL(extent->PrimaryPtr) == 0 &&
	  (extent->Status & FFS_ENTRY_PRIMARY) != FFS_ENTRY_PRIMARY)
	 cur = extent->PrimaryPtr;
      else
	 cur = 0xFFFFFFFF;

      if ((extent->Status & FFS_ENTRY_EXISTS) != FFS_ENTRY_EXISTS)
	 continue;

      // Read the fragment
      if (offset + extent->UncompressedExtentLen > start)
      {
	 toread = extent->UncompressedExtentLen;
	 if (stop > 0 &&
	     extent->UncompressedExtentLen > stop - offset)
	    toread = stop - offset;

	 if (start >= offset)
	    res = ffs2_copy_to_buff(r,to,extent,toread,start - offset);
	 else
	    res = ffs2_copy_to_buff(r,to + offset - start,extent,toread,0);

	 if (res != 0)
	    return -1;
	 len += toread;
      }

      offset += extent->UncompressedExtentLen;

      if (stop > 0 && offset >= stop)
	 return len;

      if (cur == 0xFFFFFFFF)
	 break;
   }
   return len;
}
									/*}}}*/
// ReadKernelImage - Given a kernel image file, load it			/*{{{*/
// ---------------------------------------------------------------------
/* This creates the same load map that the etherboot linux loader uses,
      0x10000-0x8FFFF     512kB   kernel and part of kernel setup
      0x90000-0x901FF     0.5kB   linux floppy boot sector
      0x90200-0x911FF       8kB   first sectors of kernel setup
      0x92200-0x931FF       4kB   primary boot loader
      0x93200-0x933FF     0.5kB   load header
      0x93400-0x93BFF       2kB   default command line
 */
int ReadKernelImage(struct ffs_read *r,unsigned long loc)
{
   unsigned char *Tmp;
   unsigned short *TmpS;
   unsigned long setup_size;
   struct setup_header *setup;

   // Read the floppy loader to 0x90000
   Tmp = (unsigned char *)(DEF_INITSEG << 4);
   if (ffs2_readfile(r,loc,Tmp,0,512) <= 0)
      return -1;

#ifndef SMALLER
   if (Tmp[510] != 0x55 || Tmp[511] != 0xAA)
      return -1;
#endif

   /* 1 byte value at the end of the loader is the number of sectors of setup
      code */
   setup_size = (int)(Tmp[SETUP_SIZE_OFF]) * SECTSIZE;

   // Read the setup code
   Tmp = (unsigned char *)(DEF_SETUPSEG << 4);
   if (ffs2_readfile(r,loc,Tmp,512,512+setup_size) <= 0)
      return -1;

   // Check the setup header
   setup = (struct setup_header *)Tmp;

#ifndef SMALLER
   if (memcmp(setup->magic,"HdrS",4) != 0 ||
       setup->version < SETUP_VERSION)
      return -1;

   // Big Kernel?
   if ((setup->loadflags & SETUP_HIGH) == SETUP_HIGH)
      Tmp = (unsigned char *)(BIG_SYSSEG << 4);
   else
      Tmp = (unsigned char *)(DEF_SYSSEG << 4);
#else
   Tmp = (unsigned char *)(DEF_SYSSEG << 4);
#endif

   // Read the kernel
   if (ffs2_readfile(r,loc,Tmp,512+setup_size,0) <= 0)
      return -1;

   /* Prepare the command line
      0x90020-0x90021     2 bytes   command line magic number
      0x90022-0x90023     2 bytes   command line offs. relative to floppy boot sec
      0x901FA-0x901FB     2 bytes   video mode */
   TmpS = (unsigned short *)(DEF_INITSEG << 4);
   TmpS[0x10] = 0xA33F;
   TmpS[0x11] = 0x93400 - 0x90000;
   Tmp = (unsigned char *)(0x93400);
   strcpy(Tmp,CMDLINE);

   return 0;
}
									/*}}}*/

int main()
{
   struct ffs2_sb_info sb;
   struct ffs_read r;
   unsigned long blocks;
   unsigned short Blocks[256];
   unsigned long Pos;
   struct qstr Name;

   printk("FFS2 Boot Loader " __DATE__ " " __TIME__ " Starting.\n");

   // Create the superblock information for the FFS2 filesystem
   memset(&sb,0,sizeof(sb));
   sb.EraseSize = EraseSize;
   blocks = FlashSize/EraseSize;
   sb.BlockMap = Blocks;
   memset(sb.BlockMap,0xFF,sizeof(*sb.BlockMap)*blocks);

   memset(&r,0,sizeof(r));
   r.super = (struct super_block *)&sb;
   if (ffs2_find_boot_block(&r,blocks) != 0 || ffs2_prepare(&r) != 0)
   {
      printk("Failed to locate the boot block!\n");
      return 0;
   }

   printk("Serial: %d\n",sb.Boot.SerialNumber);

   // Locate the kernel
   Name.name = "linux";
   Name.len = 5;
   if (ffs2_find_dirent(&r,sb.Boot.RootDirectoryPtr,&Name,&Pos) != 0)
   {
      printk("Could not find '%s'\n",Name.name);
      return 0;
   }

   if (ReadKernelImage(&r,Pos) != 0)
   {
      printk("Failed while reading image\n");
      return 0;
   }

   xstart(DEF_BOOTLSEG << 16,0,0);
   return 0;
}
