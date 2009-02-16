/*
 * JFFS2 -- Journalling Flash File System, Version 2.
 *
 * Copyright (C) 2005  Zhao Forrest <forrest.zhao@intel.com>
 *
 * For licensing information, see the file 'LICENCE' in this directory.
 *
 */

#include "nodelist.h"

void jffs2_add_to_hash_table(struct jffs2_sb_info *c, struct jffs2_eraseblock *jeb, uint8_t flag)
{
	struct jffs2_blocks_bucket *hash_table;
	uint32_t index, *current_index_p;

	if (flag == 1) {
		hash_table = c->used_blocks;
		current_index_p = &(c->used_blocks_current_index);
	}else if (flag == 2) {
		hash_table = c->free_blocks;
		current_index_p = &(c->free_blocks_current_index);
	}else {
		return;
	}

	index = (jeb->erase_count >> BUCKET_RANGE_BIT_LEN);
	if (index >= HASH_SIZE) {
		return;
	}
	if (index < *current_index_p) {
		*current_index_p = index;
	}
	hash_table[index].number++;
	list_add_tail(&jeb->hash_list, &(hash_table[index].chain));
	return;
}

void jffs2_remove_from_hash_table(struct jffs2_sb_info *c, struct jffs2_eraseblock *jeb, uint8_t flag)
{
	struct jffs2_blocks_bucket *hash_table;
	uint32_t index, *current_index_p, i;

	if (flag == 1) {
		hash_table = c->used_blocks;
		current_index_p = &(c->used_blocks_current_index);
	}else if (flag == 2) {
		hash_table = c->free_blocks;
		current_index_p = &(c->free_blocks_current_index);
	}else {
		return;
	}

	index = (jeb->erase_count >> BUCKET_RANGE_BIT_LEN);
	if (index >= HASH_SIZE) {
		return;
	}
	hash_table[index].number--;
	list_del(&jeb->hash_list);

	if (hash_table[index].number == 0) {
		for (i=index+1; i<HASH_SIZE; i++) {
			if (hash_table[i].number != 0) {
				*current_index_p = i;
				break;
			}
		}
		if (i == HASH_SIZE) {
			*current_index_p = HASH_SIZE;
		}
	}
	return;
}

struct jffs2_eraseblock *jffs2_get_free_block(struct jffs2_sb_info *c)
{
	struct list_head *next;
	struct jffs2_eraseblock *jeb;

	if (c->free_blocks_current_index == HASH_SIZE) {
		return NULL;
	}
	next = c->free_blocks[c->free_blocks_current_index].chain.next;
	jeb = list_entry(next, struct jffs2_eraseblock, hash_list);
	list_del(&jeb->list);
	jffs2_remove_from_hash_table(c, jeb, 2);
	c->nr_free_blocks--;

	return jeb;
}

struct jffs2_eraseblock *jffs2_get_used_block(struct jffs2_sb_info *c)
{
	struct list_head *next;
	struct jffs2_eraseblock *jeb;

	if (c->used_blocks_current_index == HASH_SIZE) {
		return NULL;
	}
	next = c->used_blocks[c->used_blocks_current_index].chain.next;
	jeb = list_entry(next, struct jffs2_eraseblock, hash_list);
	list_del(&jeb->list);
	jffs2_remove_from_hash_table(c, jeb, 1);

	return jeb;
}

