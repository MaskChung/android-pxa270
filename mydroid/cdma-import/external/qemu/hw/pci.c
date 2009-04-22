/*
 * QEMU PCI bus manager
 *
 * Copyright (c) 2004 Fabrice Bellard
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
 * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 */
#include "vl.h"

//#define DEBUG_PCI

struct PCIBus {
    int bus_num;
    int devfn_min;
    pci_set_irq_fn set_irq;
    uint32_t config_reg; /* XXX: suppress */
    /* low level pic */
    SetIRQFunc *low_set_irq;
    void *irq_opaque;
    PCIDevice *devices[256];
};

target_phys_addr_t pci_mem_base;
static int pci_irq_index;
static PCIBus *first_bus;

PCIBus *pci_register_bus(pci_set_irq_fn set_irq, void *pic, int devfn_min)
{
    PCIBus *bus;
    bus = qemu_mallocz(sizeof(PCIBus));
    bus->set_irq = set_irq;
    bus->irq_opaque = pic;
    bus->devfn_min = devfn_min;
    first_bus = bus;
    return bus;
}

int pci_bus_num(PCIBus *s)
{
    return s->bus_num;
}

void generic_pci_save(QEMUFile* f, void *opaque)
{
    PCIDevice* s=(PCIDevice*)opaque;

    qemu_put_buffer(f, s->config, 256);
}

int generic_pci_load(QEMUFile* f, void *opaque, int version_id)
{
    PCIDevice* s=(PCIDevice*)opaque;

    if (version_id != 1)
        return -EINVAL;

    qemu_get_buffer(f, s->config, 256);
    return 0;
}

/* -1 for devfn means auto assign */
PCIDevice *pci_register_device(PCIBus *bus, const char *name,
                               int instance_size, int devfn,
                               PCIConfigReadFunc *config_read,
                               PCIConfigWriteFunc *config_write)
{
    PCIDevice *pci_dev;

    if (pci_irq_index >= PCI_DEVICES_MAX)
        return NULL;

    if (devfn < 0) {
        for(devfn = bus->devfn_min ; devfn < 256; devfn += 8) {
            if (!bus->devices[devfn])
                goto found;
        }
        return NULL;
    found: ;
    }
    pci_dev = qemu_mallocz(instance_size);
    if (!pci_dev)
        return NULL;
    pci_dev->bus = bus;
    pci_dev->devfn = devfn;
    pstrcpy(pci_dev->name, sizeof(pci_dev->name), name);

    if (!config_read)
        config_read = pci_default_read_config;
    if (!config_write)
        config_write = pci_default_write_config;
    pci_dev->config_read = config_read;
    pci_dev->config_write = config_write;
    pci_dev->irq_index = pci_irq_index++;
    bus->devices[devfn] = pci_dev;
    return pci_dev;
}

void pci_register_io_region(PCIDevice *pci_dev, int region_num,
                            uint32_t size, int type,
                            PCIMapIORegionFunc *map_func)
{
    PCIIORegion *r;
    uint32_t addr;

    if ((unsigned int)region_num >= PCI_NUM_REGIONS)
        return;
    r = &pci_dev->io_regions[region_num];
    r->addr = -1;
    r->size = size;
    r->type = type;
    r->map_func = map_func;
    if (region_num == PCI_ROM_SLOT) {
        addr = 0x30;
    } else {
        addr = 0x10 + region_num * 4;
    }
    *(uint32_t *)(pci_dev->config + addr) = cpu_to_le32(type);
}

target_phys_addr_t pci_to_cpu_addr(target_phys_addr_t addr)
{
    return addr + pci_mem_base;
}

static void pci_update_mappings(PCIDevice *d)
{
    PCIIORegion *r;
    int cmd, i;
    uint32_t last_addr, new_addr, config_ofs;

    cmd = le16_to_cpu(*(uint16_t *)(d->config + PCI_COMMAND));
    for(i = 0; i < PCI_NUM_REGIONS; i++) {
        r = &d->io_regions[i];
        if (i == PCI_ROM_SLOT) {
            config_ofs = 0x30;
        } else {
            config_ofs = 0x10 + i * 4;
        }
        if (r->size != 0) {
            if (r->type & PCI_ADDRESS_SPACE_IO) {
                if (cmd & PCI_COMMAND_IO) {
                    new_addr = le32_to_cpu(*(uint32_t *)(d->config +
                                                         config_ofs));
                    new_addr = new_addr & ~(r->size - 1);
                    last_addr = new_addr + r->size - 1;
                    /* NOTE: we have only 64K ioports on PC */
                    if (last_addr <= new_addr || new_addr == 0 ||
                        last_addr >= 0x10000) {
                        new_addr = -1;
                    }
                } else {
                    new_addr = -1;
                }
            } else {
                if (cmd & PCI_COMMAND_MEMORY) {
                    new_addr = le32_to_cpu(*(uint32_t *)(d->config +
                                                         config_ofs));
                    /* the ROM slot has a specific enable bit */
                    if (i == PCI_ROM_SLOT && !(new_addr & 1))
                        goto no_mem_map;
                    new_addr = new_addr & ~(r->size - 1);
                    last_addr = new_addr + r->size - 1;
                    /* NOTE: we do not support wrapping */
                    /* XXX: as we cannot support really dynamic
                       mappings, we handle specific values as invalid
                       mappings. */
                    if (last_addr <= new_addr || new_addr == 0 ||
                        last_addr == -1) {
                        new_addr = -1;
                    }
                } else {
                no_mem_map:
                    new_addr = -1;
                }
            }
            /* now do the real mapping */
            if (new_addr != r->addr) {
                if (r->addr != -1) {
                    if (r->type & PCI_ADDRESS_SPACE_IO) {
                        int class;
                        /* NOTE: specific hack for IDE in PC case:
                           only one byte must be mapped. */
                        class = d->config[0x0a] | (d->config[0x0b] << 8);
                        if (class == 0x0101 && r->size == 4) {
                            isa_unassign_ioport(r->addr + 2, 1);
                        } else {
                            isa_unassign_ioport(r->addr, r->size);
                        }
                    } else {
                        cpu_register_physical_memory(pci_to_cpu_addr(r->addr),
                                                     r->size,
                                                     IO_MEM_UNASSIGNED);
                    }
                }
                r->addr = new_addr;
                if (r->addr != -1) {
                    r->map_func(d, i, r->addr, r->size, r->type);
                }
            }
        }
    }
}

uint32_t pci_default_read_config(PCIDevice *d,
                                 uint32_t address, int len)
{
    uint32_t val;
    switch(len) {
    case 1:
        val = d->config[address];
        break;
    case 2:
        val = le16_to_cpu(*(uint16_t *)(d->config + address));
        break;
    default:
    case 4:
        val = le32_to_cpu(*(uint32_t *)(d->config + address));
        break;
    }
    return val;
}

void pci_default_write_config(PCIDevice *d,
                              uint32_t address, uint32_t val, int len)
{
    int can_write, i;
    uint32_t end, addr;

    if (len == 4 && ((address >= 0x10 && address < 0x10 + 4 * 6) ||
                     (address >= 0x30 && address < 0x34))) {
        PCIIORegion *r;
        int reg;

        if ( address >= 0x30 ) {
            reg = PCI_ROM_SLOT;
        }else{
            reg = (address - 0x10) >> 2;
        }
        r = &d->io_regions[reg];
        if (r->size == 0)
            goto default_config;
        /* compute the stored value */
        if (reg == PCI_ROM_SLOT) {
            /* keep ROM enable bit */
            val &= (~(r->size - 1)) | 1;
        } else {
            val &= ~(r->size - 1);
            val |= r->type;
        }
        *(uint32_t *)(d->config + address) = cpu_to_le32(val);
        pci_update_mappings(d);
        return;
    }
 default_config:
    /* not efficient, but simple */
    addr = address;
    for(i = 0; i < len; i++) {
        /* default read/write accesses */
        switch(d->config[0x0e]) {
        case 0x00:
        case 0x80:
            switch(addr) {
            case 0x00:
            case 0x01:
            case 0x02:
            case 0x03:
            case 0x08:
            case 0x09:
            case 0x0a:
            case 0x0b:
            case 0x0e:
            case 0x10 ... 0x27: /* base */
            case 0x30 ... 0x33: /* rom */
            case 0x3d:
                can_write = 0;
                break;
            default:
                can_write = 1;
                break;
            }
            break;
        default:
        case 0x01:
            switch(addr) {
            case 0x00:
            case 0x01:
            case 0x02:
            case 0x03:
            case 0x08:
            case 0x09:
            case 0x0a:
            case 0x0b:
            case 0x0e:
            case 0x38 ... 0x3b: /* rom */
            case 0x3d:
                can_write = 0;
                break;
            default:
                can_write = 1;
                break;
            }
            break;
        }
        if (can_write) {
            d->config[addr] = val;
        }
        addr++;
        val >>= 8;
    }

    end = address + len;
    if (end > PCI_COMMAND && address < (PCI_COMMAND + 2)) {
        /* if the command register is modified, we must modify the mappings */
        pci_update_mappings(d);
    }
}

void pci_data_write(void *opaque, uint32_t addr, uint32_t val, int len)
{
    PCIBus *s = opaque;
    PCIDevice *pci_dev;
    int config_addr, bus_num;

#if defined(DEBUG_PCI) && 0
    printf("pci_data_write: addr=%08x val=%08x len=%d\n",
           addr, val, len);
#endif
    bus_num = (addr >> 16) & 0xff;
    if (bus_num != 0)
        return;
    pci_dev = s->devices[(addr >> 8) & 0xff];
    if (!pci_dev)
        return;
    config_addr = addr & 0xff;
#if defined(DEBUG_PCI)
    printf("pci_config_write: %s: addr=%02x val=%08x len=%d\n",
           pci_dev->name, config_addr, val, len);
#endif
    pci_dev->config_write(pci_dev, config_addr, val, len);
}

uint32_t pci_data_read(void *opaque, uint32_t addr, int len)
{
    PCIBus *s = opaque;
    PCIDevice *pci_dev;
    int config_addr, bus_num;
    uint32_t val;

    bus_num = (addr >> 16) & 0xff;
    if (bus_num != 0)
        goto fail;
    pci_dev = s->devices[(addr >> 8) & 0xff];
    if (!pci_dev) {
    fail:
        switch(len) {
        case 1:
            val = 0xff;
            break;
        case 2:
            val = 0xffff;
            break;
        default:
        case 4:
            val = 0xffffffff;
            break;
        }
        goto the_end;
    }
    config_addr = addr & 0xff;
    val = pci_dev->config_read(pci_dev, config_addr, len);
#if defined(DEBUG_PCI)
    printf("pci_config_read: %s: addr=%02x val=%08x len=%d\n",
           pci_dev->name, config_addr, val, len);
#endif
 the_end:
#if defined(DEBUG_PCI) && 0
    printf("pci_data_read: addr=%08x val=%08x len=%d\n",
           addr, val, len);
#endif
    return val;
}

/***********************************************************/
/* generic PCI irq support */

/* 0 <= irq_num <= 3. level must be 0 or 1 */
void pci_set_irq(PCIDevice *pci_dev, int irq_num, int level)
{
    PCIBus *bus = pci_dev->bus;
    bus->set_irq(pci_dev, bus->irq_opaque, irq_num, level);
}

/***********************************************************/
/* monitor info on PCI */

typedef struct {
    uint16_t class;
    const char *desc;
} pci_class_desc;

static pci_class_desc pci_class_descriptions[] =
{
    { 0x0101, "IDE controller"},
    { 0x0200, "Ethernet controller"},
    { 0x0300, "VGA controller"},
    { 0x0600, "Host bridge"},
    { 0x0601, "ISA bridge"},
    { 0x0604, "PCI bridge"},
    { 0x0c03, "USB controller"},
    { 0, NULL}
};

static void pci_info_device(PCIDevice *d)
{
    int i, class;
    PCIIORegion *r;
    pci_class_desc *desc;

    term_printf("  Bus %2d, device %3d, function %d:\n",
           d->bus->bus_num, d->devfn >> 3, d->devfn & 7);
    class = le16_to_cpu(*((uint16_t *)(d->config + PCI_CLASS_DEVICE)));
    term_printf("    ");
    desc = pci_class_descriptions;
    while (desc->desc && class != desc->class)
        desc++;
    if (desc->desc) {
        term_printf("%s", desc->desc);
    } else {
        term_printf("Class %04x", class);
    }
    term_printf(": PCI device %04x:%04x\n",
           le16_to_cpu(*((uint16_t *)(d->config + PCI_VENDOR_ID))),
           le16_to_cpu(*((uint16_t *)(d->config + PCI_DEVICE_ID))));

    if (d->config[PCI_INTERRUPT_PIN] != 0) {
        term_printf("      IRQ %d.\n", d->config[PCI_INTERRUPT_LINE]);
    }
    for(i = 0;i < PCI_NUM_REGIONS; i++) {
        r = &d->io_regions[i];
        if (r->size != 0) {
            term_printf("      BAR%d: ", i);
            if (r->type & PCI_ADDRESS_SPACE_IO) {
                term_printf("I/O at 0x%04x [0x%04x].\n",
                       r->addr, r->addr + r->size - 1);
            } else {
                term_printf("32 bit memory at 0x%08x [0x%08x].\n",
                       r->addr, r->addr + r->size - 1);
            }
        }
    }
}

void pci_for_each_device(void (*fn)(PCIDevice *d))
{
    PCIBus *bus = first_bus;
    PCIDevice *d;
    int devfn;

    if (bus) {
        for(devfn = 0; devfn < 256; devfn++) {
            d = bus->devices[devfn];
            if (d)
                fn(d);
        }
    }
}

void pci_info(void)
{
    pci_for_each_device(pci_info_device);
}

/* Initialize a PCI NIC.  */
void pci_nic_init(PCIBus *bus, NICInfo *nd)
{
#if 0 /* ANDROID */
    if (strcmp(nd->model, "ne2k_pci") == 0) {
        pci_ne2000_init(bus, nd);
    } else if (strcmp(nd->model, "rtl8139") == 0) {
        pci_rtl8139_init(bus, nd);
    } else if (strcmp(nd->model, "pcnet") == 0) {
        pci_pcnet_init(bus, nd);
    } else {
#else
    {
#endif
        fprintf(stderr, "qemu: Unsupported NIC: %s\n", nd->model);
        exit (1);
    }
}

