/* drivers/mtd/devices/goldfish_nand.c
**
** Copyright (C) 2007 Google, Inc.
**
** This software is licensed under the terms of the GNU General Public
** License version 2, as published by the Free Software Foundation, and
** may be copied, distributed, and modified under those terms.
**
** This program is distributed in the hope that it will be useful,
** but WITHOUT ANY WARRANTY; without even the implied warranty of
** MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
** GNU General Public License for more details.
**
*/

#include <asm/div64.h>
#include <asm/io.h>
#include <linux/module.h>
#include <linux/slab.h>
#include <linux/ioport.h>
#include <linux/vmalloc.h>
#include <linux/init.h>
#include <linux/mtd/compatmac.h>
#include <linux/mtd/mtd.h>
#include <linux/platform_device.h>

#include "goldfish_nand_reg.h"

struct goldfish_nand {
	spinlock_t              lock;
	unsigned char __iomem  *base;
	size_t                  mtd_count;
	struct mtd_info         mtd[0];
};

static uint32_t goldfish_nand_cmd(struct mtd_info *mtd, enum nand_cmd cmd,
                              uint64_t addr, uint32_t len, void *ptr)
{
	struct goldfish_nand *nand = mtd->priv;
	uint32_t rv;
	unsigned long irq_flags;
	unsigned char __iomem  *base = nand->base;

	spin_lock_irqsave(&nand->lock, irq_flags);
	writel(mtd - nand->mtd, base + NAND_DEV);
	writel((uint32_t)(addr >> 32), base + NAND_ADDR_HIGH);
	writel((uint32_t)addr, base + NAND_ADDR_LOW);
	writel(len, base + NAND_TRANSFER_SIZE);
	writel(ptr, base + NAND_DATA);
	writel(cmd, base + NAND_COMMAND);
	rv = readl(base + NAND_RESULT);
	spin_unlock_irqrestore(&nand->lock, irq_flags);
	return rv;
}

static int goldfish_nand_erase(struct mtd_info *mtd, struct erase_info *instr)
{
	loff_t ofs = instr->addr;
	uint32_t len = instr->len;
	uint32_t rem;

	if (ofs + len > mtd->size)
		goto invalid_arg;
	rem = do_div(ofs, mtd->writesize);
	if(rem)
		goto invalid_arg;
	ofs *= (mtd->writesize + mtd->oobsize);
	
	if(len % mtd->writesize)
		goto invalid_arg;
	len = len / mtd->writesize * (mtd->writesize + mtd->oobsize);

	if(goldfish_nand_cmd(mtd, NAND_CMD_ERASE, ofs, len, NULL) != len) {
		printk("goldfish_nand_erase: erase failed, start %llx, len %x, dev_size "
		       "%x, erase_size %x\n", ofs, len, mtd->size, mtd->erasesize);
		return -EIO;
	}

	instr->state = MTD_ERASE_DONE;
	mtd_erase_callback(instr);

	return 0;

invalid_arg:
	printk("goldfish_nand_erase: invalid erase, start %llx, len %x, dev_size "
	       "%x, erase_size %x\n", ofs, len, mtd->size, mtd->erasesize);
	return -EINVAL;
}

static int goldfish_nand_read_oob(struct mtd_info *mtd, loff_t ofs,
                              struct mtd_oob_ops *ops)
{
	uint32_t rem;

	if(ofs + ops->len > mtd->size)
		goto invalid_arg;
	if(ops->datbuf && ops->len && ops->len != mtd->writesize)
		goto invalid_arg;
	if(ops->ooblen + ops->ooboffs > mtd->oobsize)
		goto invalid_arg;

	rem = do_div(ofs, mtd->writesize);
	if(rem)
		goto invalid_arg;
	ofs *= (mtd->writesize + mtd->oobsize);

	if(ops->datbuf)
		ops->retlen = goldfish_nand_cmd(mtd, NAND_CMD_READ, ofs,
		                            ops->len, ops->datbuf);
	ofs += mtd->writesize + ops->ooboffs;
	if(ops->oobbuf)
		ops->oobretlen = goldfish_nand_cmd(mtd, NAND_CMD_READ, ofs,
		                               ops->ooblen, ops->oobbuf);
	return 0;

invalid_arg:
	printk("goldfish_nand_read_oob: invalid read, start %llx, len %x, "
	       "ooblen %x, dev_size %x, write_size %x\n",
	       ofs, ops->len, ops->ooblen, mtd->size, mtd->writesize);
	return -EINVAL;
}

static int goldfish_nand_write_oob(struct mtd_info *mtd, loff_t ofs,
                               struct mtd_oob_ops *ops)
{
	uint32_t rem;

	if(ofs + ops->len > mtd->size)
		goto invalid_arg;
	if(ops->len && ops->len != mtd->writesize)
		goto invalid_arg;
	if(ops->ooblen + ops->ooboffs > mtd->oobsize)
		goto invalid_arg;
	
	rem = do_div(ofs, mtd->writesize);
	if(rem)
		goto invalid_arg;
	ofs *= (mtd->writesize + mtd->oobsize);

	if(ops->datbuf)
		ops->retlen = goldfish_nand_cmd(mtd, NAND_CMD_WRITE, ofs,
		                            ops->len, ops->datbuf);
	ofs += mtd->writesize + ops->ooboffs;
	if(ops->oobbuf)
		ops->oobretlen = goldfish_nand_cmd(mtd, NAND_CMD_WRITE, ofs,
		                               ops->ooblen, ops->oobbuf);
	return 0;

invalid_arg:
	printk("goldfish_nand_write_oob: invalid write, start %llx, len %x, "
	       "ooblen %x, dev_size %x, write_size %x\n",
	       ofs, ops->len, ops->ooblen, mtd->size, mtd->writesize);
	return -EINVAL;
}

static int goldfish_nand_read(struct mtd_info *mtd, loff_t from, size_t len,
                          size_t *retlen, u_char *buf)
{
	uint32_t rem;

	if(from + len > mtd->size)
		goto invalid_arg;
	if(len != mtd->writesize)
		goto invalid_arg;

	rem = do_div(from, mtd->writesize);
	if(rem)
		goto invalid_arg;
	from *= (mtd->writesize + mtd->oobsize);

	*retlen = goldfish_nand_cmd(mtd, NAND_CMD_READ, from, len, buf);
	return 0;

invalid_arg:
	printk("goldfish_nand_read: invalid read, start %llx, len %x, dev_size %x"
	       ", write_size %x\n", from, len, mtd->size, mtd->writesize);
	return -EINVAL;
}

static int goldfish_nand_write(struct mtd_info *mtd, loff_t to, size_t len,
                           size_t *retlen, const u_char *buf)
{
	uint32_t rem;

	if(to + len > mtd->size)
		goto invalid_arg;
	if(len != mtd->writesize)
		goto invalid_arg;

	rem = do_div(to, mtd->writesize);
	if(rem)
		goto invalid_arg;
	to *= (mtd->writesize + mtd->oobsize);

	*retlen = goldfish_nand_cmd(mtd, NAND_CMD_WRITE, to, len, (void *)buf);
	return 0;

invalid_arg:
	printk("goldfish_nand_write: invalid write, start %llx, len %x, dev_size %x"
	       ", write_size %x\n", to, len, mtd->size, mtd->writesize);
	return -EINVAL;
}

static int goldfish_nand_block_isbad(struct mtd_info *mtd, loff_t ofs)
{
	uint32_t rem;

	if(ofs >= mtd->size)
		goto invalid_arg;

	rem = do_div(ofs, mtd->erasesize);
	if(rem)
		goto invalid_arg;
	ofs *= mtd->erasesize / mtd->writesize;
	ofs *= (mtd->writesize + mtd->oobsize);

	return goldfish_nand_cmd(mtd, NAND_CMD_BLOCK_BAD_GET, ofs, 0, NULL);

invalid_arg:
	printk("goldfish_nand_block_isbad: invalid arg, ofs %llx, dev_size %x, "
	       "write_size %x\n", ofs, mtd->size, mtd->writesize);
	return -EINVAL;
}

static int goldfish_nand_block_markbad(struct mtd_info *mtd, loff_t ofs)
{
	uint32_t rem;

	if(ofs >= mtd->size)
		goto invalid_arg;

	rem = do_div(ofs, mtd->erasesize);
	if(rem)
		goto invalid_arg;
	ofs *= mtd->erasesize / mtd->writesize;
	ofs *= (mtd->writesize + mtd->oobsize);

	if(goldfish_nand_cmd(mtd, NAND_CMD_BLOCK_BAD_SET, ofs, 0, NULL) != 1)
		return -EIO;
	return 0;

invalid_arg:
	printk("goldfish_nand_block_markbad: invalid arg, ofs %llx, dev_size %x, "
	       "write_size %x\n", ofs, mtd->size, mtd->writesize);
	return -EINVAL;
}

static int goldfish_nand_init_device(struct goldfish_nand *nand, int id)
{
	uint32_t dev_size_high;
	uint32_t name_len;
	uint32_t result;
	uint32_t flags;
	unsigned long irq_flags;
	unsigned char __iomem  *base = nand->base;
	struct mtd_info *mtd = &nand->mtd[id];

	spin_lock_irqsave(&nand->lock, irq_flags);
	writel(id, base + NAND_DEV);
	flags = readl(base + NAND_DEV_FLAGS);
	name_len = readl(base + NAND_DEV_NAME_LEN);
	mtd->writesize = readl(base + NAND_DEV_PAGE_SIZE);
	mtd->size = readl(base + NAND_DEV_SIZE_LOW);
	mtd->oobsize = readl(base + NAND_DEV_EXTRA_SIZE);
	mtd->oobavail = mtd->oobsize;
	mtd->erasesize = readl(base + NAND_DEV_ERASE_SIZE) /
	                 (mtd->writesize + mtd->oobsize) * mtd->writesize;
	mtd->size = mtd->size / (mtd->writesize + mtd->oobsize) * mtd->writesize;
	dev_size_high = readl(base + NAND_DEV_SIZE_HIGH);
	printk("goldfish nand dev%d: size %x, page %d, extra %d, erase %d\n",
	       id, mtd->size, mtd->writesize, mtd->oobsize, mtd->erasesize);
	spin_unlock_irqrestore(&nand->lock, irq_flags);

	if(dev_size_high) {
		printk("goldfish_nand_init_device device to big 0x%08x%08x\n",
		       dev_size_high, mtd->size);
		return -ENODEV;
	}
	mtd->priv = nand;

	mtd->name = kmalloc(name_len + 1, GFP_KERNEL);
	if(mtd->name == NULL)
		return -ENOMEM;

	result = goldfish_nand_cmd(mtd, NAND_CMD_GET_DEV_NAME, 0, name_len, mtd->name);
	if(result != name_len) {
		kfree(mtd->name);
		mtd->name = NULL;
		printk("goldfish_nand_init_device failed to get dev name %d != %d\n",
		       result, name_len);
		return -ENODEV;
	}
	mtd->name[name_len] = '\0';

	/* Setup the MTD structure */
	mtd->type = MTD_NANDFLASH;
	mtd->flags = MTD_CAP_NANDFLASH;
	if(flags & NAND_DEV_FLAG_READ_ONLY)
		mtd->flags &= ~MTD_WRITEABLE;

	mtd->owner = THIS_MODULE;
	mtd->erase = goldfish_nand_erase;
	mtd->read = goldfish_nand_read;
	mtd->write = goldfish_nand_write;
	mtd->read_oob = goldfish_nand_read_oob;
	mtd->write_oob = goldfish_nand_write_oob;
	mtd->block_isbad = goldfish_nand_block_isbad;
	mtd->block_markbad = goldfish_nand_block_markbad;

	if (add_mtd_device(mtd)) {
		kfree(mtd->name);
		mtd->name = NULL;
		return -EIO;
	}

	return 0;
}

static int goldfish_nand_probe(struct platform_device *pdev)
{
	uint32_t num_dev;
	int i;
	int err;
	uint32_t num_dev_working;
	uint32_t version;
	struct resource *r;
	struct goldfish_nand *nand;
	unsigned char __iomem  *base;

	r = platform_get_resource(pdev, IORESOURCE_MEM, 0);
	if(r == NULL) {
		err = -ENODEV;
		goto err_no_io_base;
	}

	base = ioremap(r->start, PAGE_SIZE);
	if(base == NULL) {
		err = -ENOMEM;
		goto err_ioremap;
	}
	version = readl(base + NAND_VERSION);
	if(version != NAND_VERSION_CURRENT) {
		printk("goldfish_nand_init: version mismatch, got %d, expected %d\n",
		       version, NAND_VERSION_CURRENT);
		err = -ENODEV;
		goto err_no_dev;
	}
	num_dev = readl(base + NAND_NUM_DEV);
	if(num_dev == 0) {
		err = -ENODEV;
		goto err_no_dev;
	}

	nand = kzalloc(sizeof(*nand) + sizeof(struct mtd_info) * num_dev, GFP_KERNEL);
	if(nand == NULL) {
		err = -ENOMEM;
		goto err_nand_alloc_failed;
	}
	spin_lock_init(&nand->lock);
	nand->base = base;
	nand->mtd_count = num_dev;
	platform_set_drvdata(pdev, nand);

	num_dev_working = 0;
	for(i = 0; i < num_dev; i++) {
		err = goldfish_nand_init_device(nand, i);
		if(err == 0)
			num_dev_working++;
	}
	if(num_dev_working == 0) {
		err = -ENODEV;
		goto err_no_working_dev;
	}
	return 0;

err_no_working_dev:
	kfree(nand);
err_nand_alloc_failed:
err_no_dev:
	iounmap(base);
err_ioremap:
err_no_io_base:
	return err;
}

static int goldfish_nand_remove(struct platform_device *pdev)
{
	struct goldfish_nand *nand = platform_get_drvdata(pdev);
	int i;
	for(i = 0; i < nand->mtd_count; i++) {
		if(nand->mtd[i].name) {
			del_mtd_device(&nand->mtd[i]);
			kfree(nand->mtd[i].name);
		}
	}
	iounmap(nand->base);
	kfree(nand);
	return 0;
}

static struct platform_driver goldfish_nand_driver = {
	.probe		= goldfish_nand_probe,
	.remove		= goldfish_nand_remove,
	.driver = {
		.name = "goldfish_nand"
	}
};

static int __init goldfish_nand_init(void)
{
	return platform_driver_register(&goldfish_nand_driver);
}

static void __exit goldfish_nand_exit(void)
{
	platform_driver_unregister(&goldfish_nand_driver);
}


module_init(goldfish_nand_init);
module_exit(goldfish_nand_exit);

