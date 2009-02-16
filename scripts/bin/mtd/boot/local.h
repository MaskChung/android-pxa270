/* This header binds the reader code to some functions in the boot loader,
   providing some simple dummy structures to emulate the kernel */

#include "proto.h"

struct qstr
{
   unsigned char *name;
   unsigned int len;
};

#include "ffs2_fs.h"
#include "ffs2_fs_sb.h"

#ifdef SMALLER
# define printk(x...)
#else
# define printk printf
#endif
#define getFFS2_sb(x) (*((struct ffs2_sb_info *)x))


