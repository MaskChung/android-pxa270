/*
 * JFFS2 -- Journalling Flash File System, Version 2.
 *
 * Copyright (C) 2001-2003 Red Hat, Inc.
 *
 * Created by David Woodhouse <dwmw2@infradead.org>
 *
 * For licensing information, see the file 'LICENCE' in this directory.
 *
 * $Id: malloc.c,v 1.34 2005/11/29 14:34:38 gleixner Exp $
 *
 */

#include <linux/kernel.h>
#include <linux/slab.h>
#include <linux/init.h>
#include <linux/vmalloc.h>
#include <linux/jffs2.h>
#include "nodelist.h"

/* These are initialised to NULL in the kernel startup code.
   If you're porting to other operating systems, beware */
static kmem_cache_t *full_dnode_slab;
static kmem_cache_t *raw_dirent_slab;
static kmem_cache_t *raw_inode_slab;
static kmem_cache_t *tmp_dnode_info_slab;
static kmem_cache_t *raw_node_ref_slab;
static kmem_cache_t *node_frag_slab;
static kmem_cache_t *inode_cache_slab;
static kmem_cache_t *eraseblock_slab;

static inline int jffs2_blocks_use_vmalloc(struct jffs2_sb_info *c)
{
	return ((c->flash_size / c->sector_size) * sizeof(void*)) > (128 * 1024);
}

int __init jffs2_create_slab_caches(void)
{
	full_dnode_slab = kmem_cache_create("jffs2_full_dnode",
					    sizeof(struct jffs2_full_dnode),
					    0, 0, NULL, NULL);
	if (!full_dnode_slab)
		goto err;

	raw_dirent_slab = kmem_cache_create("jffs2_raw_dirent",
					    sizeof(struct jffs2_raw_dirent),
					    0, 0, NULL, NULL);
	if (!raw_dirent_slab)
		goto err;

	raw_inode_slab = kmem_cache_create("jffs2_raw_inode",
					   sizeof(struct jffs2_raw_inode),
					   0, 0, NULL, NULL);
	if (!raw_inode_slab)
		goto err;

	tmp_dnode_info_slab = kmem_cache_create("jffs2_tmp_dnode",
						sizeof(struct jffs2_tmp_dnode_info),
						0, 0, NULL, NULL);
	if (!tmp_dnode_info_slab)
		goto err;

	raw_node_ref_slab = kmem_cache_create("jffs2_raw_node_ref",
					      sizeof(struct jffs2_raw_node_ref),
					      0, 0, NULL, NULL);
	if (!raw_node_ref_slab)
		goto err;

	node_frag_slab = kmem_cache_create("jffs2_node_frag",
					   sizeof(struct jffs2_node_frag),
					   0, 0, NULL, NULL);
	if (!node_frag_slab)
		goto err;

	eraseblock_slab = kmem_cache_create("jffs2_eraseblock",
					   sizeof(struct jffs2_eraseblock),
					   0, 0, NULL, NULL);
	if (!eraseblock_slab)
		goto err;

	inode_cache_slab = kmem_cache_create("jffs2_inode_cache",
					     sizeof(struct jffs2_inode_cache),
					     0, 0, NULL, NULL);
	if (inode_cache_slab)
		return 0;
 err:
	jffs2_destroy_slab_caches();
	return -ENOMEM;
}

void jffs2_destroy_slab_caches(void)
{
	if(full_dnode_slab)
		kmem_cache_destroy(full_dnode_slab);
	if(raw_dirent_slab)
		kmem_cache_destroy(raw_dirent_slab);
	if(raw_inode_slab)
		kmem_cache_destroy(raw_inode_slab);
	if(tmp_dnode_info_slab)
		kmem_cache_destroy(tmp_dnode_info_slab);
	if(raw_node_ref_slab)
		kmem_cache_destroy(raw_node_ref_slab);
	if(node_frag_slab)
		kmem_cache_destroy(node_frag_slab);
	if(inode_cache_slab)
		kmem_cache_destroy(inode_cache_slab);
	if (eraseblock_slab)
		kmem_cache_destroy(eraseblock_slab);
}

struct jffs2_full_dirent *jffs2_alloc_full_dirent(int namesize)
{
	struct jffs2_full_dirent *ret;
	ret = kmalloc(sizeof(struct jffs2_full_dirent) + namesize, GFP_KERNEL);
	dbg_memalloc("%p\n", ret);
	return ret;
}

void jffs2_free_full_dirent(struct jffs2_full_dirent *x)
{
	dbg_memalloc("%p\n", x);
	kfree(x);
}

struct jffs2_full_dnode *jffs2_alloc_full_dnode(void)
{
	struct jffs2_full_dnode *ret;
	ret = kmem_cache_alloc(full_dnode_slab, GFP_KERNEL);
	dbg_memalloc("%p\n", ret);
	return ret;
}

void jffs2_free_full_dnode(struct jffs2_full_dnode *x)
{
	dbg_memalloc("%p\n", x);
	kmem_cache_free(full_dnode_slab, x);
}

struct jffs2_raw_dirent *jffs2_alloc_raw_dirent(void)
{
	struct jffs2_raw_dirent *ret;
	ret = kmem_cache_alloc(raw_dirent_slab, GFP_KERNEL);
	dbg_memalloc("%p\n", ret);
	return ret;
}

void jffs2_free_raw_dirent(struct jffs2_raw_dirent *x)
{
	dbg_memalloc("%p\n", x);
	kmem_cache_free(raw_dirent_slab, x);
}

struct jffs2_raw_inode *jffs2_alloc_raw_inode(void)
{
	struct jffs2_raw_inode *ret;
	ret = kmem_cache_alloc(raw_inode_slab, GFP_KERNEL);
	dbg_memalloc("%p\n", ret);
	return ret;
}

void jffs2_free_raw_inode(struct jffs2_raw_inode *x)
{
	dbg_memalloc("%p\n", x);
	kmem_cache_free(raw_inode_slab, x);
}

struct jffs2_tmp_dnode_info *jffs2_alloc_tmp_dnode_info(void)
{
	struct jffs2_tmp_dnode_info *ret;
	ret = kmem_cache_alloc(tmp_dnode_info_slab, GFP_KERNEL);
	dbg_memalloc("%p\n",
		ret);
	return ret;
}

void jffs2_free_tmp_dnode_info(struct jffs2_tmp_dnode_info *x)
{
	dbg_memalloc("%p\n", x);
	kmem_cache_free(tmp_dnode_info_slab, x);
}

struct jffs2_raw_node_ref *jffs2_alloc_raw_node_ref(void)
{
	struct jffs2_raw_node_ref *ret;
	ret = kmem_cache_alloc(raw_node_ref_slab, GFP_KERNEL);
	dbg_memalloc("%p\n", ret);
	return ret;
}

void jffs2_free_raw_node_ref(struct jffs2_raw_node_ref *x)
{
	dbg_memalloc("%p\n", x);
	kmem_cache_free(raw_node_ref_slab, x);
}

struct jffs2_node_frag *jffs2_alloc_node_frag(void)
{
	struct jffs2_node_frag *ret;
	ret = kmem_cache_alloc(node_frag_slab, GFP_KERNEL);
	dbg_memalloc("%p\n", ret);
	return ret;
}

void jffs2_free_node_frag(struct jffs2_node_frag *x)
{
	dbg_memalloc("%p\n", x);
	kmem_cache_free(node_frag_slab, x);
}

struct jffs2_inode_cache *jffs2_alloc_inode_cache(void)
{
	struct jffs2_inode_cache *ret;
	ret = kmem_cache_alloc(inode_cache_slab, GFP_KERNEL);
	dbg_memalloc("%p\n", ret);
	return ret;
}

void jffs2_free_inode_cache(struct jffs2_inode_cache *x)
{
	dbg_memalloc("%p\n", x);
	kmem_cache_free(inode_cache_slab, x);
}

int jffs2_alloc_eraseblocks(struct jffs2_sb_info *c)
{
	uint32_t i;
#ifndef __ECOS
	if (jffs2_blocks_use_vmalloc(c))
		c->blocks = vmalloc(sizeof(void *) * c->nr_blocks);
	else
#endif
		c->blocks = kmalloc(sizeof(void *) * c->nr_blocks, GFP_KERNEL);
	if (!c->blocks)
		return -ENOMEM;
	memset(c->blocks, 0, sizeof(void *) * c->nr_blocks);

	for (i=0; i<c->nr_blocks; i++) {
		c->blocks[i] = kmem_cache_alloc(eraseblock_slab, GFP_KERNEL);
		dbg_memalloc("%p\n", c->blocks[i]);
		if (!c->blocks[i]) {
			jffs2_free_eraseblocks(c);
			return -ENOMEM;
		}
		memset(c->blocks[i], 0, sizeof(struct jffs2_eraseblock));
	}


	for (i=0; i<c->nr_blocks; i++) {
		INIT_LIST_HEAD(&c->blocks[i]->list);
		INIT_LIST_HEAD(&c->blocks[i]->hash_list);
		c->blocks[i]->offset = i * c->sector_size;
		c->blocks[i]->free_size = c->sector_size;
		c->blocks[i]->first_node = NULL;
		c->blocks[i]->last_node = NULL;
	}

	return 0;
}

void jffs2_free_eraseblocks(struct jffs2_sb_info *c)
{
	uint32_t i;

	for (i=0; i<c->nr_blocks; i++) {
		if (c->blocks[i]) {
			dbg_memalloc("%p\n", c->blocks[i]);
			kmem_cache_free(eraseblock_slab, c->blocks[i]);
		}
	}
#ifndef __ECOS
	if (jffs2_blocks_use_vmalloc(c))
		vfree(c->blocks);
	else
#endif
		kfree(c->blocks);
}

