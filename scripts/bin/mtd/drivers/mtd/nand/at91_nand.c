/*
 * drivers/mtd/nand/at91_nand.c
 *
 *  Copyright (C) 2003 Rick Bronson
 *
 *  Derived from drivers/mtd/nand/autcpu12.c
 *	 Copyright (c) 2001 Thomas Gleixner (gleixner@autronix.de)
 *
 *  Derived from drivers/mtd/spia.c
 *	 Copyright (C) 2000 Steven J. Hill (sjhill@cotw.com)
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 2 as
 * published by the Free Software Foundation.
 *
 */

#include <linux/slab.h>
#include <linux/module.h>
#include <linux/platform_device.h>
#include <linux/mtd/mtd.h>
#include <linux/mtd/nand.h>
#include <linux/mtd/partitions.h>

#include <asm/io.h>
#include <asm/sizes.h>

#include <asm/arch/hardware.h>
#include <asm/arch/board.h>
#include <asm/arch/gpio.h>

struct at91_nand_host {
	struct nand_chip	nand_chip;
	struct mtd_info		mtd;
	void __iomem		*io_base;
	struct at91_nand_data	*board;
};

/*
 * Hardware specific access to control-lines
 */
static void at91_nand_hwcontrol(struct mtd_info *mtd, int cmd)
{
	struct nand_chip *nand_chip = mtd->priv;
	struct at91_nand_host *host = nand_chip->priv;

	switch(cmd) {
		case NAND_CTL_SETCLE:
			nand_chip->IO_ADDR_W = host->io_base + (1 << host->board->cle);
			break;
		case NAND_CTL_CLRCLE:
			nand_chip->IO_ADDR_W = host->io_base;
			break;
		case NAND_CTL_SETALE:
			nand_chip->IO_ADDR_W = host->io_base + (1 << host->board->ale);
			break;
		case NAND_CTL_CLRALE:
			nand_chip->IO_ADDR_W = host->io_base;
			break;
		case NAND_CTL_SETNCE:
			break;
		case NAND_CTL_CLRNCE:
			break;
	}
}


/*
 * Read the Device Ready pin.
 */
static int at91_nand_device_ready(struct mtd_info *mtd)
{
	struct nand_chip *nand_chip = mtd->priv;
	struct at91_nand_host *host = nand_chip->priv;

	return at91_get_gpio_value(host->board->rdy_pin);
}


/*
 * Enable NAND and detect card.
 */
static void at91_nand_enable(struct at91_nand_host *host)
{
	unsigned int csa;

	/* Setup Smart Media, first enable the address range of CS3 */
	csa = at91_sys_read(AT91_EBI_CSA);
	at91_sys_write(AT91_EBI_CSA, csa | AT91_EBI_CS3A_SMC_SMARTMEDIA);

	/* set the bus interface characteristics */
	at91_sys_write(AT91_SMC_CSR(3), AT91_SMC_ACSS_STD | AT91_SMC_DBW_8 | AT91_SMC_WSEN
				| AT91_SMC_NWS_(5)
				| AT91_SMC_TDF_(1)
				| AT91_SMC_RWSETUP_(0)	/* tDS Data Set up Time 30 - ns */
				| AT91_SMC_RWHOLD_(1)	/* tDH Data Hold Time 20 - ns */
	);

	if (host->board->enable_pin)
		at91_set_gpio_value(host->board->enable_pin, 0);
}

/*
 * Disable NAND.
 */
static void at91_nand_disable(struct at91_nand_host *host)
{
	if (host->board->enable_pin)
		at91_set_gpio_value(host->board->enable_pin, 1);
}

/*
 * Probe for the NAND device.
 */
static int __init at91_nand_probe(struct platform_device *pdev)
{
	struct at91_nand_host *host;
	struct mtd_info *mtd;
	struct nand_chip *nand_chip;
	int res;

#ifdef CONFIG_MTD_PARTITIONS
	struct mtd_partition *partitions = NULL;
	int num_partitions = 0;
#endif

	/* Allocate memory for the device structure (and zero it) */
	host = kzalloc(sizeof(struct at91_nand_host), GFP_KERNEL);
	if (!host) {
		printk(KERN_ERR "at91_nand: failed to allocate device structure.\n");
		return -ENOMEM;
	}

	host->io_base = ioremap(pdev->resource[0].start,
				pdev->resource[0].end - pdev->resource[0].start + 1);
	if (host->io_base == NULL) {
		printk(KERN_ERR "at91_nand: ioremap failed\n");
		kfree(host);
		return -EIO;
	}

	mtd = &host->mtd;
	nand_chip = &host->nand_chip;
	host->board = pdev->dev.platform_data;

	nand_chip->priv = host;		/* link the private data structures */
	mtd->priv = nand_chip;

	/* Set address of NAND IO lines */
	nand_chip->IO_ADDR_R = host->io_base;
	nand_chip->IO_ADDR_W = host->io_base;
	nand_chip->hwcontrol = at91_nand_hwcontrol;
	nand_chip->dev_ready = at91_nand_device_ready;
	nand_chip->eccmode = NAND_ECC_SOFT;	/* enable ECC */
	nand_chip->chip_delay = 20;		/* 20us command delay time */

	platform_set_drvdata(pdev, host);
	at91_nand_enable(host);

	if (host->board->det_pin) {
		if (at91_get_gpio_value(host->board->det_pin)) {
			printk ("No SmartMedia card inserted.\n");
			res = ENXIO;
			goto out;
		}
	}

	/* Scan to find existance of the device */
	if (nand_scan(mtd, 1)) {
		res = -ENXIO;
		goto out;
	}

#ifdef CONFIG_MTD_PARTITIONS
	if (host->board->partition_info)
		partitions = host->board->partition_info(mtd->size, &num_partitions);

	if ((!partitions) || (num_partitions == 0)) {
		printk(KERN_ERR "at91_nand: No parititions defined, or unsupported device.\n");
		res = ENXIO;
		goto out;
	}

	res = add_mtd_partitions(mtd, partitions, num_partitions);
#else
	res = add_mtd_device(mtd);
#endif

out:
	if (res) {
		at91_nand_disable(host);
		platform_set_drvdata(pdev, NULL);
	
		iounmap(host->io_base);
		kfree(host);
	}

	return res;
}

/*
 * Remove a NAND device.
 */
static int __devexit at91_nand_remove(struct platform_device *pdev)
{
	struct at91_nand_host *host = platform_get_drvdata(pdev);
	struct mtd_info *mtd = &host->mtd;

	del_mtd_partitions(mtd);
	del_mtd_device(mtd);

	at91_nand_disable(host);

	iounmap(host->io_base);
	kfree(host);

	return 0;
}

static struct platform_driver at91_nand_driver = {
	.probe		= at91_nand_probe,
	.remove		= at91_nand_remove,
	.driver		= {
		.name	= "at91_nand",
		.owner	= THIS_MODULE,
	},
};

static int __init at91_nand_init(void)
{
	return platform_driver_register(&at91_nand_driver);
}


static void __exit at91_nand_exit(void)
{
	platform_driver_unregister(&at91_nand_driver);
}


module_init(at91_nand_init);
module_exit(at91_nand_exit);

MODULE_LICENSE("GPL");
MODULE_AUTHOR("Rick Bronson");
MODULE_DESCRIPTION("Glue layer for SmartMediaCard on ATMEL AT91RM9200");
