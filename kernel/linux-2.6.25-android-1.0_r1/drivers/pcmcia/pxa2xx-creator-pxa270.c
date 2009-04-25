/*
 * linux26/drivers/pcmcia/pxa2xx-creator-pxa270.c
 *
 */

#include <linux/kernel.h>
#include <linux/sched.h>
#include <linux/platform_device.h>

#include <pcmcia/ss.h>

#include <asm/delay.h>
#include <asm/hardware.h>
#include <asm/irq.h>
#include <asm/arch/creator-pxa270.h>

#include "soc_common.h"

#define DEBUG_COL_PCMCIA

#define DEBUG
#ifdef DEBUG
#define MSG(string, args...) printk("<1>%s(), line=%d" string, __FUNCTION__, __LINE__, ##args)
#else   
#define MSG(string, args...)
#endif

#ifdef DEBUG_COL_PCMCIA
#define MARK printk("<1> %s: %d\n", __FUNCTION__, __LINE__);
#else
//#define MARK (0)
#endif
#ifdef PCMCIA_DEBUG
int pc_debug = PCMCIA_DEBUG;
#endif

static struct pcmcia_irqs irqs[] = {
    { 0, CREATOR_CFI_IRQ, "PCMCIA CD"},
};

static int creator_pxa270_pcmcia_init_dev(struct soc_pcmcia_socket *skt)
{
    printk("Create-XScale-PXA270 PCMCIA\n");
    /* set power GPIO; switch off */

    /* set PCMCIA AFs */
    GPSR(GPIO_PCMCIA_NPOE) |= GPIO_bit(GPIO_PCMCIA_NPOE);
    pxa_gpio_mode(GPIO_PCMCIA_NPOE | GPIO_PCMCIA_NPOE_AF);

    GPSR(GPIO_PCMCIA_NPIOR) |= GPIO_bit(GPIO_PCMCIA_NPIOR);
    pxa_gpio_mode(GPIO_PCMCIA_NPIOR | GPIO_PCMCIA_NPIOR_AF);

    GPSR(GPIO_PCMCIA_NPIOW) |= GPIO_bit(GPIO_PCMCIA_NPIOW);
    pxa_gpio_mode(GPIO_PCMCIA_NPIOW | GPIO_PCMCIA_NPIOW_AF);

    GPSR(GPIO_PCMCIA_NPCE1) |= GPIO_bit(GPIO_PCMCIA_NPCE1);
    pxa_gpio_mode(GPIO_PCMCIA_NPCE1 | GPIO_PCMCIA_NPCE1_AF);

    GPCR(GPIO_PCMCIA_NPCE2) |= GPIO_bit(GPIO_PCMCIA_NPCE2);
    pxa_gpio_mode(GPIO_PCMCIA_NPCE2 | GPIO_PCMCIA_NPCE2_AF);

    GPSR(GPIO_PCMCIA_NPREG) |= GPIO_bit(GPIO_PCMCIA_NPREG);
    pxa_gpio_mode(GPIO_PCMCIA_NPREG | GPIO_PCMCIA_NPREG_AF);

    pxa_gpio_mode(GPIO_PCMCIA_NPWAIT | GPIO_PCMCIA_NPWAIT_AF);

    pxa_gpio_mode(GPIO_PCMCIA_NPIOIS16 | GPIO_PCMCIA_NPIOIS16_AF);

    GPSR(GPIO_PCMCIA_PSKTSEL) |= GPIO_bit(GPIO_PCMCIA_PSKTSEL);
    pxa_gpio_mode(GPIO_PCMCIA_PSKTSEL | GPIO_PCMCIA_PSKTSEL_AF);

    /* set other PCMCIA GPIOs */

    /* switch power on */
    PCC_PWR_ON();

	/* reset the PCMCIA controller */
		PCC_RESET_ASSERT
		udelay(500);
		PCC_RESET_DEASSERT
	

    /* set interrupts */	
    skt->irq = CREATOR_CF_IRQ;	
    return soc_pcmcia_request_irqs(skt, irqs, ARRAY_SIZE(irqs));
}

static void creator_pxa270_pcmcia_shutdown(struct soc_pcmcia_socket *skt)
{
	soc_pcmcia_free_irqs(skt, irqs, ARRAY_SIZE(irqs));

    /* switch power off */
    PCC_PWR_OFF();
}

static void creator_pxa270_pcmcia_socket_state(struct soc_pcmcia_socket *skt,
                                        struct pcmcia_state *state)
{
	//memset(state, 0, sizeof(*state));

    /* these are gpios */
	state->detect = (PCC_DETECT) ? 0 : 1;    /* active high??? */
	state->ready =  (PCC_READY) ? 1 : 0;
/*
    printk(KERN_INFO "CF status: detect: %u, ready: %u\n",
           GPLR(84) & GPIO_bit(84),
           GPLR(1) & GPIO_bit(1));
*/

	state->bvd1   = PCC_BVD1() ? 1 : 0;
	state->bvd2   = PCC_BVD2() ? 1 : 0;
	state->wrprot = 0; /* r/w all the time */
	state->vs_3v  = PCC_VS3V() ? 1 : 0;
	state->vs_Xv  = PCC_VS5V() ? 1 : 0;
}

static int creator_pxa270_pcmcia_configure_socket(struct soc_pcmcia_socket *skt,
                                           socket_state_t const *state)
{
    unsigned long flags;
 
    local_irq_save(flags);
	/* configure Vcc and Vpp */
    if (state->Vcc == 0 && state->Vpp == 0)
    {
        PCC_PWR_OFF();   
    }
    else if (state->Vcc == 33 && state->Vpp < 50)
    {
        PCC_PWR_ON();       
    }
    else
    {
        printk(KERN_ERR "%s(): unsupported Vcc %u Vpp %u combination\n",
               __FUNCTION__, state->Vcc, state->Vpp);              
        return -1;
    }

    /* reset PCMCIA if requested */
    if (state->flags & SS_RESET)
    {
        PCC_RESET_ASSERT		//JUPITER : ???   
    }
    else{
		PCC_RESET_DEASSERT        
    }

    local_irq_restore(flags);
    udelay(200);   
	return 0;
}

static void creator_pxa270_pcmcia_socket_init(struct soc_pcmcia_socket *skt)
{
}

static void creator_pxa270_pcmcia_socket_suspend(struct soc_pcmcia_socket *skt)
{
}

struct pcmcia_low_level creator_pxa270_pcmcia_ops = { 
    .owner             = THIS_MODULE,
    .hw_init           = creator_pxa270_pcmcia_init_dev,
    .hw_shutdown       = creator_pxa270_pcmcia_shutdown,
    .socket_state      = creator_pxa270_pcmcia_socket_state,
    .configure_socket  = creator_pxa270_pcmcia_configure_socket,
    .socket_init       = creator_pxa270_pcmcia_socket_init,
    .socket_suspend    = creator_pxa270_pcmcia_socket_suspend,
    .first             = 0,
    .nr                = 1
};



static struct platform_device *creator_pxa270_pcmcia_device;

static int __init creator_pxa270_pcmcia_init(void)
{
    int ret;

    creator_pxa270_pcmcia_device = kmalloc(sizeof(*creator_pxa270_pcmcia_device), GFP_KERNEL);
    if (!creator_pxa270_pcmcia_device)
        return -ENOMEM;
    memset(creator_pxa270_pcmcia_device, 0, sizeof(*creator_pxa270_pcmcia_device));
    creator_pxa270_pcmcia_device->name = "pxa2xx-pcmcia";
    creator_pxa270_pcmcia_device->dev.platform_data = &creator_pxa270_pcmcia_ops;

    ret = platform_device_register(creator_pxa270_pcmcia_device);
    if (ret)
        kfree(creator_pxa270_pcmcia_device);

    return ret;    
}


static void __exit creator_pxa270_pcmcia_exit(void)
{
    /* Are there still references to creator_pxa270_pcmcia_device?
     * I don't know, so I'd better not free it.
     * Actually, I don't really care, as I don't really support
     * this driver as module anyway...
     */
    platform_device_unregister(creator_pxa270_pcmcia_device);
}


module_init(creator_pxa270_pcmcia_init);
module_exit(creator_pxa270_pcmcia_exit);

MODULE_DESCRIPTION("Create XScale-PXA270 CF Support");
MODULE_LICENSE("GPL");
