// --------------------------------------------------------------------
//
//   Title     :  creator-pxa270-flash.c
//             :
//   Library   :
//             :
//   Developers:  MICROTIME MDS group
//             :
//   Purpose   :  
//             :
//   Limitation:
//             :
//   Note      :
//             :
// --------------------------------------------------------------------
//   modification history :
// --------------------------------------------------------------------
//   Version| mod. date: |
//   Vx.xx  | mm/dd/yyyy |
//   V1.00  | 05/25/2006 | First release
// --------------------------------------------------------------------
// --------------------------------------------------------------------
//
// Note:
//
//       MICROTIME COMPUTER INC.
//
//
/*************************************************************************
Include files
*************************************************************************/
#include <linux/config.h>
#include <linux/module.h>
#include <linux/types.h>
#include <linux/kernel.h>
#include <linux/slab.h>
#include <linux/ioport.h>
#include <linux/platform_device.h>
#include <linux/init.h>

#include <linux/mtd/mtd.h>
#include <linux/mtd/map.h>
#include <linux/mtd/partitions.h>

#include <asm/mach/flash.h>
#include <asm/hardware.h>
#include <asm/io.h>
#include <asm/system.h>

/*************************************************************************
Constant define
*************************************************************************/
#define DO_FLASH_UNLOCK

struct creator_pxa270_flash_info {
	struct flash_platform_data *plat;
	struct resource		*res;
	struct mtd_partition	*parts;
	struct mtd_info		*mtd;
	struct map_info		map;
	unsigned int		nr_parts;	
};


/*************************************************************************
Function prototypes
*************************************************************************/


/*************************************************************************
Variable define
*************************************************************************/


static void 
creator_pxa270_flash_set_vpp (struct map_info *map, int on)
{
        struct creator_pxa270_flash_info *info = container_of(map, struct creator_pxa270_flash_info, map);

        if (info->plat && info->plat->set_vpp)
            info->plat->set_vpp(on);
}



static void 
creator_pxa270_flash_destroy (struct creator_pxa270_flash_info *info)
{
        if (info->mtd) {
            if (info->nr_parts == 0)
                del_mtd_device(info->mtd);
#ifdef CONFIG_MTD_PARTITIONS
            else
                del_mtd_partitions(info->mtd);
#endif
        }
        
        kfree(info->parts);

        iounmap(info->map.virt);
        release_resource(info->res);
        kfree(info->res);

        if (info->plat && info->plat->exit){
            info->plat->exit();
        }    
        kfree(info);        
}


    

static const char* part_probes[] = { "RedBoot", "cmdlinepart", NULL };


static int 
creator_pxa270_flash_probe (struct platform_device *pdev)
{
        struct flash_platform_data        *plat = pdev->dev.platform_data;
        struct resource                   *res  = pdev->resource;
        struct creator_pxa270_flash_info  *info;    
	    struct mtd_partition              *parts;            
        void __iomem                      *base;    
        struct mtd_info                   *mymtd;        
	    const char                        *part_type = NULL;       
	    unsigned long                     phys;	         
        unsigned int                      size ;
	    int                               i, ret, nr_parts = 0;

        phys = res->start;
        size = res->end - phys + 1;

        info = kmalloc(sizeof(struct creator_pxa270_flash_info), GFP_KERNEL);
        if (!info) {
            ret = -ENOMEM;
            goto out;
        }

        memset(info, 0, sizeof(struct creator_pxa270_flash_info));

        info->plat = plat;
        if (plat && plat->init) {
            ret = plat->init();
            if (ret)
                goto err;
        }

        info->res = request_mem_region(phys, size, "creator_pxa270_flash");
        if (!info->res) {
            ret = -EBUSY;
            goto err;
        }

        base = ioremap(phys, size);
        if (!base) {
            ret = -ENOMEM;
            goto err;
        }

        /*
         * look for CFI based flash parts fitted to this board
         */
        info->map.phys		= phys;         
        info->map.size		= size;
        info->map.bankwidth	= plat->width;
        info->map.virt		= base;
        info->map.name		= pdev->dev.bus_id;
        info->map.set_vpp	= creator_pxa270_flash_set_vpp;

        simple_map_init(&info->map);

        /*
        * Also, the CFI layer automatically works out what size
        * of chips we have, and does the necessary identification
        * for us automatically.
        */
        info->mtd = mymtd = do_map_probe(plat->map_name, &info->map);
        if (!info->mtd) {
            ret = -ENXIO;
            goto err;
        }        
        info->mtd->owner = THIS_MODULE;
        

#ifdef DO_FLASH_UNLOCK
        /* Unlock the flash device. */
        for (i = 0; i < mymtd->numeraseregions; i++) {
            int j;
            
            for(j = 0; j < mymtd->eraseregions[i].numblocks; j++) {
                mymtd->unlock(mymtd, mymtd->eraseregions[i].offset +
                              j * mymtd->eraseregions[i].erasesize,
                              mymtd->eraseregions[i].erasesize);
            }
        }
#endif        
        
        /*
         * Partition selection stuff.
         */
        
#ifdef CONFIG_MTD_PARTITIONS
        nr_parts = parse_mtd_partitions(info->mtd, part_probes, &parts, 0);
        if (nr_parts > 0) {
            info->parts = parts;
            part_type = "dynamic";
        } else
#endif

        {
            parts = plat->parts;
            nr_parts = plat->nr_parts;
            part_type = "static";
        }

        if (nr_parts == 0) {
            printk(KERN_NOTICE "Creator XScale-PXA270 flash: no partition info "
			                   "available, registering whole flash\n");
            add_mtd_device(info->mtd);
        } else {
            printk(KERN_NOTICE "Creator XScale-PXA270  flash: using %s partition "
                               "definition\n", part_type);
            add_mtd_partitions(info->mtd, parts, nr_parts);
        }

        info->nr_parts = nr_parts;

        platform_set_drvdata(pdev, info);
        ret = 0;
        return (ret);
        
err:
        creator_pxa270_flash_destroy(info);
out:
        return (ret);
}



static int 
creator_pxa270_flash_remove (struct platform_device *pdev)
{
        struct creator_pxa270_flash_info *info = platform_get_drvdata(pdev);      

        platform_set_drvdata(pdev, NULL);
        creator_pxa270_flash_destroy(info);

        return (0);
}



static struct platform_driver creator_pxa270_flash_driver = {
	.probe		= creator_pxa270_flash_probe,
	.remove		= creator_pxa270_flash_remove,
	.driver		= {
		.name	= "creator_pxa270_flash",
	},
};



static int __init 
creator_pxa270_flash_init (void)
{
         return platform_driver_register(&creator_pxa270_flash_driver);
}



static void __exit 
creator_pxa270_flash_exit (void)
{
        platform_driver_unregister(&creator_pxa270_flash_driver);
}


module_init(creator_pxa270_flash_init);
module_exit(creator_pxa270_flash_exit);


MODULE_DESCRIPTION("Creator XScale-PXA270 CFI map driver");
MODULE_LICENSE("GPL");
