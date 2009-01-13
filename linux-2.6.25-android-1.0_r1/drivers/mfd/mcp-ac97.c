/*
 * linux/drivers/misc/mcp-ac97.c
 *
 * Author:	Nicolas Pitre
 * Created:	Jan 14, 2005
 * Copyright:	(C) MontaVista Software Inc.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * version 2 as published by the Free Software Foundation.
 *
 * This module provides the minimum replacement for mcp-core.c allowing for
 * the UCB1400 chip to be driven by the ucb1x00 driver over an AC97 link.
 */

#include <linux/module.h>
#include <linux/moduleparam.h>
#include <linux/init.h>
#include <linux/errno.h>
#include <linux/device.h>

#include <sound/driver.h>
#include <sound/core.h>
#include <sound/ac97_codec.h>

#include "mcp.h"

/* ucb1x00 SIB register to ucb1400 AC-link register mapping */

static const unsigned char regmap[] = {
	0x5a,	/* UCB_IO_DATA */
	0X5C,	/* UCB_IO_DIR */
	0X5E,	/* UCB_IE_RIS */
	0x60,	/* UCB_IE_FAL */
	0x62,	/* UCB_IE_STATUS */
	0,	/* UCB_TC_A */
	0,	/* UCB_TC_B */
	0,	/* UCB_AC_A */
	0,	/* UCB_AC_B */
	0x64,	/* UCB_TS_CR */
	0x66,	/* UCB_ADC_CR */
	0x68,	/* UCB_ADC_DATA */
	0x7e,	/* UCB_ID */
	0,	/* UCB_MODE */
	0x6a, /* Reserved : 14 */  
	0x2,  /* Reserved : 15 */
	0x6c  /* Reserved : 16*/		
};

unsigned int mcp_reg_read(struct mcp *mcp, unsigned int reg)
{
	ac97_t *ac97 = to_ac97_t(mcp->dev);
	if (reg < ARRAY_SIZE(regmap)) {
		reg = regmap[reg];
		if (reg)
			return ac97->bus->ops->read(ac97, reg);
	}
	return -1;
}

void mcp_reg_write(struct mcp *mcp, unsigned int reg, unsigned int val)
{
	ac97_t *ac97 = to_ac97_t(mcp->dev);
	if (reg < ARRAY_SIZE(regmap)) {
		reg = regmap[reg];
		if (reg)
			ac97->bus->ops->write(ac97, reg, val);
	}
}

void mcp_enable(struct mcp *mcp)
{
}

void mcp_disable(struct mcp *mcp)
{  
}


#define to_mcp_driver(d)	container_of(d, struct mcp_driver, drv)

static int mcp_probe(struct device *dev)
{
	struct mcp_driver *drv = to_mcp_driver(dev->driver);
	struct mcp *mcp;
	int ret;

	ret = -ENOMEM;
	mcp = kmalloc(sizeof(*mcp), GFP_KERNEL);
	if (mcp) {
		memset(mcp, 0, sizeof(*mcp));
		mcp->owner = THIS_MODULE;
		mcp->dev = dev;
		ret = drv->probe(mcp);
		if (ret)
			kfree(mcp);
	}
	if (!ret)
		dev_set_drvdata(dev, mcp);
	return ret;
}

static int mcp_remove(struct device *dev)
{
	struct mcp_driver *drv = to_mcp_driver(dev->driver);
	struct mcp *mcp = dev_get_drvdata(dev);

	drv->remove(mcp);
	dev_set_drvdata(dev, NULL);
	kfree(mcp);
	return 0;
}

static int mcp_suspend(struct device *dev, pm_message_t state)
{
	struct mcp_driver *drv = to_mcp_driver(dev->driver);
	struct mcp *mcp = dev_get_drvdata(dev);
	int ret = 0;

	if (drv->suspend)
		ret = drv->suspend(mcp, state);
	return ret;
}

static int mcp_resume(struct device *dev)
{
	struct mcp_driver *drv = to_mcp_driver(dev->driver);
	struct mcp *mcp = dev_get_drvdata(dev);
	int ret = 0;

	if (drv->resume)
		ret = drv->resume(mcp);
	return ret;
}

int mcp_driver_register(struct mcp_driver *mcpdrv)
{
	mcpdrv->drv.owner = THIS_MODULE;
	mcpdrv->drv.bus = &ac97_bus_type;
	mcpdrv->drv.probe = mcp_probe;
	mcpdrv->drv.remove = mcp_remove;
	mcpdrv->drv.suspend = mcp_suspend;
	mcpdrv->drv.resume = mcp_resume;
	return driver_register(&mcpdrv->drv);
}

void mcp_driver_unregister(struct mcp_driver *mcpdrv)
{
	driver_unregister(&mcpdrv->drv);
}

#ifdef MODULE
static int __init mcp97_init(void)
{
	return (0);
}

static void __exit mcp97_exit (void)
{
}	


module_init(mcp97_init);
module_exit(mcp97_exit);

EXPORT_SYMBOL(mcp_reg_read);
EXPORT_SYMBOL(mcp_reg_write);
EXPORT_SYMBOL(mcp_enable);
EXPORT_SYMBOL(mcp_disable);
EXPORT_SYMBOL(mcp_driver_register);
EXPORT_SYMBOL(mcp_driver_unregister);

#endif

MODULE_LICENSE("GPL");