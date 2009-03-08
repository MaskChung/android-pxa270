/*
 * JFFS2 -- Journalling Flash File System, Version 2.
 *
 * Copyright (C) 2004  Ferenc Havasi <havasi@inf.u-szeged.hu>,
 *                     Zoltan Sogor <weth@inf.u-szeged.hu>,
 *                     Patrik Kluba <pajko@halom.u-szeged.hu>,
 *                     University of Szeged, Hungary
 *
 * For licensing information, see the file 'LICENCE' in this directory.
 *
 * $Id: summary.h,v 1.4 2006/02/15 09:42:02 havasi Exp $
 *
 */

#ifndef JFFS2_SUMMARY_H
#define JFFS2_SUMMARY_H

#include <linux/uio.h>
#include <linux/jffs2.h>

#define DIRTY_SPACE(x) do { typeof(x) _x = (x); \
		c->free_size -= _x; c->dirty_size += _x; \
		jeb->free_size -= _x ; jeb->dirty_size += _x; \
		}while(0)
#define USED_SPACE(x) do { typeof(x) _x = (x); \
		c->free_size -= _x; c->used_size += _x; \
		jeb->free_size -= _x ; jeb->used_size += _x; \
		}while(0)
#define WASTED_SPACE(x) do { typeof(x) _x = (x); \
		c->free_size -= _x; c->wasted_size += _x; \
		jeb->free_size -= _x ; jeb->wasted_size += _x; \
		}while(0)
#define UNCHECKED_SPACE(x) do { typeof(x) _x = (x); \
		c->free_size -= _x; c->unchecked_size += _x; \
		jeb->free_size -= _x ; jeb->unchecked_size += _x; \
		}while(0)

#define BLK_STATE_ALLFF		0
#define BLK_STATE_CLEAN		1
#define BLK_STATE_PARTDIRTY	2
#define BLK_STATE_CLEANMARKER	3
#define BLK_STATE_ALLDIRTY	4
#define BLK_STATE_BADBLOCK	5

#define JFFS2_SUMMARY_NOSUM_SIZE 0xffffffff
#define JFFS2_SUMMARY_INODE_SIZE (sizeof(struct jffs2_sum_inode_flash))
#define JFFS2_SUMMARY_DIRENT_SIZE(x) (sizeof(struct jffs2_sum_dirent_flash) + (x))
#define JFFS2_SUMMARY_EBH_SIZE(x) (sizeof(struct jffs2_sum_ebh_flash) + (x))

/* Summary structures used on flash */

struct jffs2_sum_unknown_flash
{
	jint16_t nodetype;	/* node type */
} __attribute__((packed));

struct jffs2_sum_inode_flash
{
	jint16_t nodetype;	/* node type */
	jint32_t inode;		/* inode number */
	jint32_t version;	/* inode version */
	jint32_t offset;	/* offset on jeb */
	jint32_t totlen; 	/* record length */
} __attribute__((packed));

struct jffs2_sum_dirent_flash
{
	jint16_t nodetype;	/* == JFFS_NODETYPE_DIRENT */
	jint32_t totlen;	/* record length */
	jint32_t offset;	/* offset on jeb */
	jint32_t pino;		/* parent inode */
	jint32_t version;	/* dirent version */
	jint32_t ino; 		/* == zero for unlink */
	uint8_t nsize;		/* dirent name size */
	uint8_t type;		/* dirent type */
	uint8_t name[0];	/* dirent name */
} __attribute__((packed));

struct jffs2_sum_ebh_flash
{
	jint16_t nodetype;
	jint32_t totlen;
	jint32_t offset;
	uint8_t  reserved;
	uint8_t  compat_fset;
	uint8_t  incompat_fset;
	uint8_t  rocompat_fset;
	jint32_t erase_count;
	jint16_t dsize;
	jint32_t data[0];
} __attribute__((packed));

union jffs2_sum_flash
{
	struct jffs2_sum_unknown_flash u;
	struct jffs2_sum_inode_flash i;
	struct jffs2_sum_dirent_flash d;
	struct jffs2_sum_ebh_flash eh;
};

/* Summary structures used in the memory */

struct jffs2_sum_unknown_mem
{
	union jffs2_sum_mem *next;
	jint16_t nodetype;	/* node type */
} __attribute__((packed));

struct jffs2_sum_inode_mem
{
	union jffs2_sum_mem *next;
	jint16_t nodetype;	/* node type */
	jint32_t inode;		/* inode number */
	jint32_t version;	/* inode version */
	jint32_t offset;	/* offset on jeb */
	jint32_t totlen; 	/* record length */
} __attribute__((packed));

struct jffs2_sum_dirent_mem
{
	union jffs2_sum_mem *next;
	jint16_t nodetype;	/* == JFFS_NODETYPE_DIRENT */
	jint32_t totlen;	/* record length */
	jint32_t offset;	/* ofset on jeb */
	jint32_t pino;		/* parent inode */
	jint32_t version;	/* dirent version */
	jint32_t ino; 		/* == zero for unlink */
	uint8_t nsize;		/* dirent name size */
	uint8_t type;		/* dirent type */
	uint8_t name[0];	/* dirent name */
} __attribute__((packed));

struct jffs2_sum_ebh_mem
{
	union jffs2_sum_mem *next;
	jint16_t nodetype;
	jint32_t totlen;
	jint32_t offset;
	uint8_t  reserved;
	uint8_t  compat_fset;
	uint8_t  incompat_fset;
	uint8_t  rocompat_fset;
	jint32_t erase_count;
	jint16_t dsize;
	jint32_t data[0];
} __attribute__((packed));

union jffs2_sum_mem
{
	struct jffs2_sum_unknown_mem u;
	struct jffs2_sum_inode_mem i;
	struct jffs2_sum_dirent_mem d;
	struct jffs2_sum_ebh_mem eh;
};

/* Summary related information stored in superblock */

struct jffs2_summary
{
	uint32_t sum_size;      /* collected summary information for nextblock */
	uint32_t sum_num;
	uint32_t sum_padded;
	union jffs2_sum_mem *sum_list_head;
	union jffs2_sum_mem *sum_list_tail;

	jint32_t *sum_buf;	/* buffer for writing out summary */
};

/* Summary marker is stored at the end of every sumarized erase block */

struct jffs2_sum_marker
{
	jint32_t offset;	/* offset of the summary node in the jeb */
	jint32_t magic; 	/* == JFFS2_SUM_MAGIC */
};

#define JFFS2_SUMMARY_FRAME_SIZE (sizeof(struct jffs2_raw_summary) + sizeof(struct jffs2_sum_marker))

#ifdef CONFIG_JFFS2_SUMMARY	/* SUMMARY SUPPORT ENABLED */

#define jffs2_sum_active() (1)
int jffs2_sum_init(struct jffs2_sb_info *c);
void jffs2_sum_exit(struct jffs2_sb_info *c);
void jffs2_sum_disable_collecting(struct jffs2_summary *s);
int jffs2_sum_is_disabled(struct jffs2_summary *s);
void jffs2_sum_reset_collected(struct jffs2_summary *s);
void jffs2_sum_move_collected(struct jffs2_sb_info *c, struct jffs2_summary *s);
int jffs2_sum_add_kvec(struct jffs2_sb_info *c, const struct kvec *invecs,
			unsigned long count,  uint32_t to);
int jffs2_sum_write_sumnode(struct jffs2_sb_info *c);
int jffs2_sum_add_padding_mem(struct jffs2_summary *s, uint32_t size);
int jffs2_sum_add_inode_mem(struct jffs2_summary *s, struct jffs2_raw_inode *ri, uint32_t ofs);
int jffs2_sum_add_dirent_mem(struct jffs2_summary *s, struct jffs2_raw_dirent *rd, uint32_t ofs);
int jffs2_sum_add_ebh_mem(struct jffs2_summary *s, struct jffs2_raw_ebh *eh, uint32_t ofs);
int jffs2_sum_scan_sumnode(struct jffs2_sb_info *c, struct jffs2_eraseblock *jeb,
			uint32_t ofs, uint32_t *pseudo_random);

#else				/* SUMMARY DISABLED */

#define jffs2_sum_active() (0)
#define jffs2_sum_init(a) (0)
#define jffs2_sum_exit(a)
#define jffs2_sum_disable_collecting(a)
#define jffs2_sum_is_disabled(a) (0)
#define jffs2_sum_reset_collected(a)
#define jffs2_sum_add_kvec(a,b,c,d) (0)
#define jffs2_sum_move_collected(a,b)
#define jffs2_sum_write_sumnode(a) (0)
#define jffs2_sum_add_padding_mem(a,b)
#define jffs2_sum_add_inode_mem(a,b,c)
#define jffs2_sum_add_dirent_mem(a,b,c)
#define jffs2_sum_add_ebh_mem(a,b,c)
#define jffs2_sum_scan_sumnode(a,b,c,d) (0)

#endif /* CONFIG_JFFS2_SUMMARY */

#endif /* JFFS2_SUMMARY_H */