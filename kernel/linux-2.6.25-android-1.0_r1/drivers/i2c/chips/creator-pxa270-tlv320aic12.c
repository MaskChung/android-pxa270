// --------------------------------------------------------------------
//
//   Title     :  creator-pxa270-tlv320aic12.c
//             :
//   Library   :
//             :
//   Developers:  MICROTIME MDS group
//             :
//   Purpose   :  Non Interrupt (Polling)IIC bus for CODEC (TLV320AIC12)
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
//   V1.0   | 06/28/2004 | First release
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
#include <linux/module.h>
#include <linux/kernel.h>
#include <linux/i2c.h>
#include <linux/slab.h>
#include <linux/string.h>
#include <linux/rtc.h>		/* get the user-level API */
#include <linux/init.h>

#include "rtc8564.h"

/*************************************************************************
Constant define
*************************************************************************/
#define SLAVE_ADDR                  0x80
#define I2C_DRIVERID_TLV320AIC12    0xf010  

#ifdef DEBUG
# define _DBG(x, fmt, args...) do{ if (1) printk(KERN_ALERT"%s(), line=%d, : " fmt "\n", __FUNCTION__, __LINE__, ##args); } while(0);
#else
# define _DBG(x, fmt, args...) do { } while(0);
#endif



struct creator_tlv320aic12_data {
	struct i2c_client client;
	u16 ctrl;
};
/*************************************************************************
Function prototypes
*************************************************************************/
static int creator_tlv320aic12_read_mem (struct i2c_client *client, struct mem *mem);
static int creator_tlv320aic12_write_mem (struct i2c_client *client, struct mem *mem);


/*************************************************************************
Variable define
*************************************************************************/
static int debug;
module_param(debug, int, S_IRUGO | S_IWUSR);

static struct i2c_driver creator_tlv320aic12_driver;

static unsigned short ignore[] = { I2C_CLIENT_END };
static unsigned short normal_addr[] = { SLAVE_ADDR, I2C_CLIENT_END };

static struct i2c_client_address_data addr_data = {
	.normal_i2c		= normal_addr,
	.probe			= ignore,
	.ignore			= ignore,
};




static int 
creator_tlv320aic12_read (struct i2c_client *client, unsigned char adr,	unsigned char *buf, unsigned char len)
{
       int ret = -EIO;
       unsigned char addr[1] = { adr };
       struct i2c_msg msgs[2] = {
           {client->addr, 0, 1, addr},
           {client->addr, I2C_M_RD, len, buf}
       };

       _DBG(1, "client=%p, adr=%d, buf=%p, len=%d", client, adr, buf, len);

       if (!buf) {
           ret = -EINVAL;
           goto done;
       }

       ret = i2c_transfer(client->adapter, msgs, 2);
       if (ret == 2) {
           ret = 0;
       }

done:
       return ret;
}



static int 
creator_tlv320aic12_write (struct i2c_client *client, unsigned char adr, unsigned char *data, unsigned char len)
{
       int ret = 0;
       unsigned char _data[16];
       struct i2c_msg wr;
       int i;

       if (!data || len > 15) {
           ret = -EINVAL;
           goto done;
       }

       _DBG(1, "client=%p, adr=%d, buf=%p, len=%d", client, adr, data, len);

       _data[0] = adr;
       for (i = 0; i < len; i++) {
           _data[i + 1] = data[i];
           _DBG(5, "data[%d] = 0x%02x (%d)", i, data[i], data[i]);
       }

       wr.addr = client->addr;
       wr.flags = 0;
       wr.len = len + 1;
       wr.buf = _data;

       ret = i2c_transfer(client->adapter, &wr, 1);
       if (ret == 1) {
           ret = 0;
       }

done:
       return ret;
}



static int 
creator_tlv320aic12_attach (struct i2c_adapter *adap, int addr, int kind)
{
       int ret;
       struct i2c_client *new_client;
       struct creator_tlv320aic12_data *d;
       unsigned char data[10];
       unsigned char ad[1] = { 0 };
       struct i2c_msg ctrl_wr[1] = {
           {addr, 0, 2, data}
       };
       struct i2c_msg ctrl_rd[2] = {
           {addr, 0, 1, ad},
           {addr, I2C_M_RD, 2, data}
       };

       d = kzalloc(sizeof(struct creator_tlv320aic12_data), GFP_KERNEL);
       if (!d) {
           ret = -ENOMEM;
           goto done;
       }
       new_client = &d->client;

       strlcpy(new_client->name, "TLV320AIC12", I2C_NAME_SIZE);
       i2c_set_clientdata(new_client, d);
       new_client->flags = I2C_CLIENT_ALLOW_USE;
       new_client->addr = addr;
       new_client->adapter = adap;
       new_client->driver = &creator_tlv320aic12_driver;

       _DBG(1, "client=%p", new_client);

       /* init ctrl1 reg */
       data[0] = 0;
       data[1] = 0;
       ret = i2c_transfer(new_client->adapter, ctrl_wr, 1);
       if (ret != 1) {
           printk(KERN_INFO "tlv320aic12: cant init ctrl1\n");
           ret = -ENODEV;
           goto done;
       }

       /* read back ctrl1 and ctrl2 */
       ret = i2c_transfer(new_client->adapter, ctrl_rd, 2);
       if (ret != 2) {
           printk(KERN_INFO "tlv320aic12: cant read ctrl\n");
           ret = -ENODEV;
           goto done;
       }

       d->ctrl = data[0] | (data[1] << 8);

       _DBG(1, "RTC8564_REG_CTRL1=%02x, RTC8564_REG_CTRL2=%02x",
       data[0], data[1]);

       ret = i2c_attach_client(new_client);
done:
       if (ret) {
           kfree(d);
       }
       return ret;
}



static int 
creator_tlv320aic12_probe (struct i2c_adapter *adap)
{
       return i2c_probe (adap, &addr_data, creator_tlv320aic12_attach);
}



static int creator_tlv320aic12_detach(struct i2c_client *client)
{
       i2c_detach_client(client);
       kfree(i2c_get_clientdata(client));
       return 0;
}



static int creator_tlv320aic12_get_ctrl(struct i2c_client *client, unsigned int *ctrl)
{
struct creator_tlv320aic12_data *data = i2c_get_clientdata(client);

       if (!ctrl)
           return -1;

       *ctrl = data->ctrl;
       return 0;
}


static int creator_tlv320aic12_set_ctrl(struct i2c_client *client, unsigned int *ctrl)
{
       struct creator_tlv320aic12_data *data = i2c_get_clientdata(client);
       unsigned char buf[2];

       if (!ctrl)
           return -1;

       buf[0] = *ctrl & 0xff;
       buf[1] = (*ctrl & 0xff00) >> 8;
       data->ctrl = *ctrl;

       return creator_tlv320aic12_write(client, 0, buf, 2);
}

static int creator_tlv320aic12_read_mem(struct i2c_client *client, struct mem *mem)
{
       if (!mem)
           return -EINVAL;

       return creator_tlv320aic12_read(client, mem->loc, mem->data, mem->nr);
}

static int creator_tlv320aic12_write_mem(struct i2c_client *client, struct mem *mem)
{

       if (!mem)
           return -EINVAL;

       return creator_tlv320aic12_write(client, mem->loc, mem->data, mem->nr);
}

static int
creator_tlv320aic12_command(struct i2c_client *client, unsigned int cmd, void *arg)
{
       _DBG(1, "cmd=%d", cmd);

       switch (cmd) {


       case RTC_GETCTRL:
           return creator_tlv320aic12_get_ctrl(client, arg);

       case RTC_SETCTRL:
           return creator_tlv320aic12_set_ctrl(client, arg);

       case MEM_READ:
           return creator_tlv320aic12_read_mem(client, arg);

       case MEM_WRITE:
           return creator_tlv320aic12_write_mem(client, arg);

       default:
           return -EINVAL;
       }
}



static struct i2c_driver creator_tlv320aic12_driver = {
	.owner		= THIS_MODULE,
	.name		= "TLV320AIC12",
	.id		    = I2C_DRIVERID_TLV320AIC12,
	.flags		= I2C_DF_NOTIFY,
	.attach_adapter = creator_tlv320aic12_probe,
	.detach_client	= creator_tlv320aic12_detach,
	.command	= creator_tlv320aic12_command
};




static __init int 
creator_tlv320aic12_init(void)
{
       return i2c_add_driver(&creator_tlv320aic12_driver);
}



static __exit void creator_tlv320aic12_exit(void)
{
       i2c_del_driver(&creator_tlv320aic12_driver);
}


MODULE_AUTHOR("Microtime Computer Inc.");
MODULE_DESCRIPTION("Create XScale-PXA270 tlv320aic12 Driver");
MODULE_LICENSE("GPL");

module_init(creator_tlv320aic12_init);
module_exit(creator_tlv320aic12_exit);
