/* $Id: jffs2_fs_sb.h,v 1.60 2005/11/29 14:34:37 gleixner Exp $ */

#ifndef _JFFS2_FS_SB
#define _JFFS2_FS_SB

#include <linux/types.h>
#include <linux/spinlock.h>
#include <linux/workqueue.h>
#include <linux/completion.h>
#include <asm/semaphore.h>
#include <linux/timer.h>
#include <linux/wait.h>
#include <linux/list.h>
#include <linux/rwsem.h>

#define JFFS2_SB_FLAG_RO 1
#define JFFS2_SB_FLAG_SCANNING 2 /* Flash scanning is in progress */
#define JFFS2_SB_FLAG_BUILDING 4 /* File system building is in progress */

#define MAX_ERASE_COUNT_BIT_LEN 18
#define MAX_ERASE_COUNT (1 << MAX_ERASE_COUNT_BIT_LEN) /* The maximum guaranteed erase cycles for NAND and NOR are ~ 100K at the moment */
#define WL_DELTA_BIT_LEN 10
#define WL_DELTA (1 << WL_DELTA_BIT_LEN) /* This is wear-leveling delta, which is defined as "maximum of all erase counts - minimum of all erase counts" */
#define HASH_SIZE_BIT_LEN (MAX_ERASE_COUNT_BIT_LEN - WL_DELTA_BIT_LEN + 1) /* The range size of per-bucket is half of WL_DELTA */
#define HASH_SIZE (1 << HASH_SIZE_BIT_LEN)
#define BUCKET_RANGE_BIT_LEN (MAX_ERASE_COUNT_BIT_LEN - HASH_SIZE_BIT_LEN)
#define BUCKET_RANGE (1 << BUCKET_RANGE_BIT_LEN)

struct jffs2_blocks_bucket {
	uint32_t number;        /* The number of erase blocks in this bucket*/
	struct list_head chain; /* The head of erase blocks in this bucket */
};

struct jffs2_inodirty;

/* A struct for the overall file system control.  Pointers to
   jffs2_sb_info structs are named `c' in the source code.
   Nee jffs_control
*/
struct jffs2_sb_info {
	struct mtd_info *mtd;

	uint32_t highest_ino;
	uint32_t checked_ino;

	unsigned int flags;

	struct task_struct *gc_task;	/* GC task struct */
	struct completion gc_thread_start; /* GC thread start completion */
	struct completion gc_thread_exit; /* GC thread exit completion port */

	struct semaphore alloc_sem;	/* Used to protect all the following
					   fields, and also to protect against
					   out-of-order writing of nodes. And GC. */
	uint32_t cleanmarker_size;	/* Size of an _inline_ CLEANMARKER
					 (i.e. zero for OOB CLEANMARKER */

	uint32_t flash_size;
	uint32_t used_size;
	uint32_t dirty_size;
	uint32_t wasted_size;
	uint32_t free_size;
	uint32_t erasing_size;
	uint32_t bad_size;
	uint32_t sector_size;
	uint32_t unchecked_size;

	uint32_t nr_free_blocks;
	uint32_t nr_erasing_blocks;

	/* Number of free blocks there must be before we... */
	uint8_t resv_blocks_write;	/* ... allow a normal filesystem write */
	uint8_t resv_blocks_deletion;	/* ... allow a normal filesystem deletion */
	uint8_t resv_blocks_gctrigger;	/* ... wake up the GC thread */
	uint8_t resv_blocks_gcbad;	/* ... pick a block from the bad_list to GC */
	uint8_t resv_blocks_gcmerge;	/* ... merge pages when garbage collecting */

	uint32_t nospc_dirty_size;

	uint32_t nr_blocks;
	struct jffs2_eraseblock **blocks;	/* The whole array of blocks. Used for getting blocks
						 * from the offset (blocks[ofs / sector_size]) */
	struct jffs2_eraseblock *nextblock;	/* The block we're currently filling */

	struct jffs2_eraseblock *gcblock;	/* The block we're currently garbage-collecting */

	struct list_head clean_list;		/* Blocks 100% full of clean data */
	struct list_head very_dirty_list;	/* Blocks with lots of dirty space */
	struct list_head dirty_list;		/* Blocks with some dirty space */
	struct list_head erasable_list;		/* Blocks which are completely dirty, and need erasing */
	struct list_head erasable_pending_wbuf_list;	/* Blocks which need erasing but only after the current wbuf is flushed */
	struct list_head erasing_list;		/* Blocks which are currently erasing */
	struct list_head erase_pending_list;	/* Blocks which need erasing now */
	struct list_head erase_complete_list;	/* Blocks which are erased and need the clean marker written to them */
	struct list_head free_list;		/* Blocks which are free and ready to be used */
	struct list_head bad_list;		/* Bad blocks. */
	struct list_head bad_used_list;		/* Bad blocks with valid data in. */

	spinlock_t erase_completion_lock;	/* Protect free_list and erasing_list
						   against erase completion handler */
	wait_queue_head_t erase_wait;		/* For waiting for erases to complete */

	wait_queue_head_t inocache_wq;
	struct jffs2_inode_cache **inocache_list;
	spinlock_t inocache_lock;

	/* Sem to allow jffs2_garbage_collect_deletion_dirent to
	   drop the erase_completion_lock while it's holding a pointer
	   to an obsoleted node. I don't like this. Alternatives welcomed. */
	struct semaphore erase_free_sem;

	uint32_t wbuf_pagesize; /* 0 for NOR and other flashes with no wbuf */

#ifdef CONFIG_JFFS2_FS_WRITEBUFFER
	/* Write-behind buffer for NAND flash */
	unsigned char *wbuf;
	uint32_t wbuf_ofs;
	uint32_t wbuf_len;
	struct jffs2_inodirty *wbuf_inodes;

	struct rw_semaphore wbuf_sem;	/* Protects the write buffer */

	/* Information about out-of-band area usage... */
	struct nand_oobinfo *oobinfo;
	uint32_t badblock_pos;
	uint32_t fsdata_pos;
	uint32_t fsdata_len;
#endif

	struct jffs2_summary *summary;		/* Summary information */

	uint32_t ebh_size;	/* This is the space size occupied by eraseblock_header on flash */

	uint32_t total_erase_count; /* The summary erase count of all erase blocks */
	uint32_t nr_blocks_with_ebh; /* The number of erase blocks, which has eraseblock header on it */
	uint32_t max_erase_count; /* The maximum erase count of all erase blocks */

	uint32_t used_blocks_current_index;
	uint32_t free_blocks_current_index;
	struct jffs2_blocks_bucket used_blocks[HASH_SIZE]; /* The hash table for both dirty and clean erase blocks */
	struct jffs2_blocks_bucket free_blocks[HASH_SIZE]; /* The hash table for free erase blocks */

	/* OS-private pointer for getting back to master superblock info */
	void *os_priv;
};

#endif /* _JFFS2_FB_SB */
