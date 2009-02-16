/*
 * $Id: mphysmap.c,v 1.4 2005/11/07 11:14:27 gleixner Exp $
 *
 * Several mappings of NOR chips
 *
 * Copyright (c) 2001-2005	Jörn Engel <joern@wh.fh-wedelde>
 */
#include <linux/kernel.h>
#include <linux/module.h>
#include <linux/mtd/map.h>
#include <linux/mtd/mtd.h>
#ifdef CONFIG_MTD_PARTITIONS
#include <linux/mtd/partitions.h>
#endif

static struct map_info mphysmap_static_maps[] = {
#if CONFIG_MTD_MULTI_PHYSMAP_1_WIDTH
	{
		.name		= CONFIG_MTD_MULTI_PHYSMAP_1_NAME,
		.phys		= CONFIG_MTD_MULTI_PHYSMAP_1_START,
		.size		= CONFIG_MTD_MULTI_PHYSMAP_1_LEN,
		.bankwidth	= CONFIG_MTD_MULTI_PHYSMAP_1_WIDTH,
	},
#endif
#if CONFIG_MTD_MULTI_PHYSMAP_2_WIDTH
	{
		.name		= CONFIG_MTD_MULTI_PHYSMAP_2_NAME,
		.phys		= CONFIG_MTD_MULTI_PHYSMAP_2_START,
		.size		= CONFIG_MTD_MULTI_PHYSMAP_2_LEN,
		.bankwidth	= CONFIG_MTD_MULTI_PHYSMAP_2_WIDTH,
	},
#endif
#if CONFIG_MTD_MULTI_PHYSMAP_3_WIDTH
	{
		.name		= CONFIG_MTD_MULTI_PHYSMAP_3_NAME,
		.phys		= CONFIG_MTD_MULTI_PHYSMAP_3_START,
		.size		= CONFIG_MTD_MULTI_PHYSMAP_3_LEN,
		.bankwidth	= CONFIG_MTD_MULTI_PHYSMAP_3_WIDTH,
	},
#endif
#if CONFIG_MTD_MULTI_PHYSMAP_4_WIDTH
	{
		.name		= CONFIG_MTD_MULTI_PHYSMAP_4_NAME,
		.phys		= CONFIG_MTD_MULTI_PHYSMAP_4_START,
		.size		= CONFIG_MTD_MULTI_PHYSMAP_4_LEN,
		.bankwidth	= CONFIG_MTD_MULTI_PHYSMAP_4_WIDTH,
	},
#endif
};

DECLARE_MUTEX(map_mutex);


static int mphysmap_map_device(struct map_info *map)
{
	static const char *rom_probe_types[] = { "cfi_probe", "jedec_probe", "map_rom", NULL };
	const char **type;
	struct mtd_info* mtd;
#ifdef CONFIG_MTD_PARTITIONS
       struct mtd_partition* mtd_parts;
       int mtd_parts_nb;
       static const char *part_probes[] __initdata = {
#ifdef CONFIG_MTD_CMDLINE_PARTS
       "cmdlinepart",
#endif
#ifdef CONFIG_MTD_REDBOOT_PARTS
       "RedBoot",
#endif
       NULL};
#endif
	map->virt = ioremap(map->phys, map->size);
	if (!map->virt)
		return -EIO;

	simple_map_init(map);
	mtd = NULL;
	type = rom_probe_types;
	for(; !mtd && *type; type++) {
		mtd = do_map_probe(*type, map);
	}

	if (!mtd) {
		iounmap(map->virt);
		return -ENXIO;
	}

	map->map_priv_1 = (unsigned long)mtd;
	mtd->owner = THIS_MODULE;

#ifdef CONFIG_MTD_PARTITIONS
	mtd_parts_nb = parse_mtd_partitions(mtd, part_probes,
					    &mtd_parts, 0);
	if (mtd_parts_nb > 0)
	{
		add_mtd_partitions (mtd, mtd_parts, mtd_parts_nb);
		map->map_priv_2=(unsigned long)mtd_parts;
	}
	else
	{
		add_mtd_device(mtd);
		map->map_priv_2=(unsigned long)NULL;
        };
#else
	add_mtd_device(mtd);
#endif
	return 0;
}


static void mphysmap_unmap_device(struct map_info *map)
{
	struct mtd_info* mtd = (struct mtd_info*)map->map_priv_1;
#ifdef CONFIG_MTD_PARTITIONS
       struct mtd_partition* mtd_parts=(struct mtd_partition*)map->map_priv_2;
#endif
	BUG_ON(!mtd);
	if (!map->virt)
		return;

#ifdef CONFIG_MTD_PARTITIONS
	if (mtd_parts)
	{
		del_mtd_partitions(mtd);
		kfree(mtd_parts);
	}
	else
	    del_mtd_device(mtd);
#else
	del_mtd_device(mtd);
#endif
	map_destroy(mtd);
	iounmap(map->virt);

	map->map_priv_1 = 0;
	map->map_priv_2 = 0;
	map->virt = NULL;
}




static int __init mphysmap_init(void)
{
	int i;
	down(&map_mutex);
	for (i=0;
	     i<sizeof(mphysmap_static_maps)/sizeof(mphysmap_static_maps[0]);
	     i++)
	{
	        if (strcmp(mphysmap_static_maps[i].name,"")!=0 &&
		    mphysmap_static_maps[i].size!=0 &&
		    mphysmap_static_maps[i].bankwidth!=0)
		{
		    mphysmap_map_device(&mphysmap_static_maps[i]);
		};
	};
	up(&map_mutex);
	return 0;
}


static void __exit mphysmap_exit(void)
{
	int i;
	down(&map_mutex);
	for (i=0;
	     i<sizeof(mphysmap_static_maps)/sizeof(mphysmap_static_maps[0]);
	     i++)
	{
	        if (strcmp(mphysmap_static_maps[i].name,"")!=0 &&
		    mphysmap_static_maps[i].size!=0 &&
		    mphysmap_static_maps[i].bankwidth!=0)
		{
		    mphysmap_unmap_device(&mphysmap_static_maps[i]);
		};
	};
	up(&map_mutex);
}


module_init(mphysmap_init);
module_exit(mphysmap_exit);

MODULE_LICENSE("GPL");
MODULE_AUTHOR("Jörn Engel <joern@wh.fh-wedelde>");
MODULE_DESCRIPTION("Generic configurable extensible MTD map driver");
