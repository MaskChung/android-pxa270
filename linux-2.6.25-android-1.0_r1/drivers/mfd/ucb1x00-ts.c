/*
 *  Touchscreen driver for UCB1x00-based touchscreens
 *
 *  Copyright (C) 2001 Russell King, All Rights Reserved.
 *  Copyright (C) 2005 Pavel Machek
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 2 as
 * published by the Free Software Foundation.
 *
 * 21-Jan-2002 <jco@ict.es> :
 *
 * Added support for synchronous A/D mode. This mode is useful to
 * avoid noise induced in the touchpanel by the LCD, provided that
 * the UCB1x00 has a valid LCD sync signal routed to its ADCSYNC pin.
 * It is important to note that the signal connected to the ADCSYNC
 * pin should provide pulses even when the LCD is blanked, otherwise
 * a pen touch needed to unblank the LCD will never be read.
 */
#include <linux/config.h>
#include <linux/module.h>
#include <linux/moduleparam.h>
#include <linux/init.h>
#include <linux/smp.h>
#include <linux/smp_lock.h>
#include <linux/sched.h>
#include <linux/completion.h>
#include <linux/delay.h>
#include <linux/string.h>
#include <linux/input.h>
#include <linux/device.h>
#include <linux/suspend.h>
#include <linux/slab.h>
#include <linux/kthread.h>
#include <linux/delay.h>

#include <asm/dma.h>
#include <asm/semaphore.h>

#ifdef CONFIG_MCP_SA11X0
#include <asm/arch/collie.h>
#endif


#ifdef CONFIG_MACH_CREATOR_PXA270
#include <asm/arch/creator-pxa270.h>
#endif


#include <asm/mach-types.h>

#include "ucb1x00.h"

#ifdef MODULE
#define PXAFB_OPTIONS_SIZE 256
static char g_lcdtype_options[PXAFB_OPTIONS_SIZE] __initdata = "";
#endif


// Supported LCD type
enum LCD_TYPE {
	TFT_NONE_TYPE=-1,
	TFT_MTLCD_0283224=0,		
	TFT_MTLCD_0353224,	
	TFT_MTLCD_1046448,
	TFT_MTLCD_0353224A	
};

typedef struct MTLCD_INFO {
    int            id ;
    unsigned short xres ;
    unsigned short yres ;
    unsigned short min_x ;
    unsigned short min_y ;
    unsigned short adc_max_x ;
    unsigned short adc_max_y ;    
    char           *lcdtype;
    int            ts_convert_type;
} ST_MTLCD_INFO ;
    
ST_MTLCD_INFO  MT_LCD [] = {     
	 // id ,                  xres,  yres,  min_x,  min_y,  adc_max_x ;  adc_max_y 

    {   TFT_MTLCD_0283224,    240,   320,   80,    100,     960,         930     , "MTLCD-0283224"   , 1 },  // MTLCD-0283224(2.8")
    {   TFT_MTLCD_0353224,    240,   320,   90,    100,     950,         970     , "MTLCD-0353224"   , 2 },  // MTLCD-0353224(3.5" 240*320 Portrait)
    {   TFT_MTLCD_1046448,    640,   480,   16,     80,     965,         900     , "MTLCD-1046448"   , 3 },  // MTLCD-1046448(10.4")        
    {   TFT_MTLCD_0353224A,   320,   240,   60,     68,     965,         940     , "MTLCD-0353224A"  , 2},  // MTLCD-0353224(3.5" 320*240 Landscape)
    {   TFT_NONE_TYPE,        0,     0,     0,      0,      0,           0,        NULL              , 0 } ,
} ;   
static int touch_convert_type, select_lcd_index = TFT_NONE_TYPE;

struct ucb1x00_ts {
	struct input_dev	*idev;
	struct ucb1x00		*ucb;

	struct completion	irq_wait;
	struct task_struct	*rtask;
	u16			x_res;
	u16			y_res;
	
	unsigned int		restart:1;
	unsigned int		adcsync:1;
	ST_MTLCD_INFO       *pMTLCD;
};

static int adcsync;
static void ts_option (struct ucb1x00_ts *ts);

#ifdef MODULE

int __devinit ucb1x00_ts_setup (char *options)
{
        int i;
       
        for (i=0; MT_LCD[i].lcdtype; i++){
            if (strcmp(options, MT_LCD[i].lcdtype) == 0){	              
                select_lcd_index = i ;   	
	            return (0);
            }           		
        }       	       
        return (-1);               
}
#endif



static void 
ts_option (struct ucb1x00_ts *ts)
{
	    printk("LCD lcd index = %d\n", select_lcd_index); 
		touch_convert_type = MT_LCD[select_lcd_index].ts_convert_type;
		ts->pMTLCD = &MT_LCD[select_lcd_index];	
}


static inline void ucb1x00_ts_evt_add(struct ucb1x00_ts *ts, u16 pressure, u16 x, u16 y)
{
	struct input_dev *idev = ts->idev;
	ST_MTLCD_INFO       *pMTLCD = ts->pMTLCD;
    u16 x1, y1;
	//printk("<1>Touch before X=%d, Y=%d\n", x, y);

    if (touch_convert_type == 1){
         x1 = x;
         x = y;
         y = x1;    	
    }     

	x1   = x - pMTLCD->min_x;
	if ( x1 > 40000) x1 = 0;
	y1  = y - pMTLCD->min_y;
	if ( y1 > 40000) y1 = 0;

	x1  = (x1 * pMTLCD->xres) / (pMTLCD->adc_max_x - pMTLCD->min_x);
	y1  = (y1 * pMTLCD->yres) / (pMTLCD->adc_max_y - pMTLCD->min_y);

	if(x1 > pMTLCD->xres) x1  = pMTLCD->xres-1;
	if(y1 > pMTLCD->yres) y1  = pMTLCD->yres-1;
	
	if (touch_convert_type == 2){
		// MTLCD-0353224 (3.5" Portrait)
		// MTLCD-0353224A(3.5" Landscape)
        y1 = pMTLCD->yres - y1 - 1;		
    }	
    else if (touch_convert_type == 1){
    	// MTLCD-0283524
    	// 	
        x1 = pMTLCD->xres - x1 - 1;	        
        y1 = pMTLCD->yres - y1 - 1;	        
    }     	
    
	input_report_abs(idev, ABS_X, x1);
	input_report_abs(idev, ABS_Y, y1);
	input_report_abs(idev, ABS_PRESSURE, pressure);
	//printk("<1>Touch after X=%d, Y=%d\n", idev->abs[ABS_X], idev->abs[ABS_Y]);
	input_sync(idev);
}

static inline void ucb1x00_ts_event_release(struct ucb1x00_ts *ts)
{
	struct input_dev *idev = ts->idev;
	input_report_abs(idev, ABS_PRESSURE, 0);
	input_sync(idev);
}

/*
 * Switch to interrupt mode.
 */
static inline void ucb1x00_ts_mode_int(struct ucb1x00_ts *ts)
{
	ucb1x00_reg_write(ts->ucb, UCB_TS_CR,
			UCB_TS_CR_TSMX_POW | UCB_TS_CR_TSPX_POW |
			UCB_TS_CR_TSMY_GND | UCB_TS_CR_TSPY_GND |
			UCB_TS_CR_MODE_INT);
}

/*
 * Switch to pressure mode, and read pressure.  We don't need to wait
 * here, since both plates are being driven.
 */
static inline unsigned int ucb1x00_ts_read_pressure(struct ucb1x00_ts *ts)
{   
	if (machine_is_collie()) {
		ucb1x00_io_write(ts->ucb, COLLIE_TC35143_GPIO_TBL_CHK, 0);
		ucb1x00_reg_write(ts->ucb, UCB_TS_CR,
				  UCB_TS_CR_TSPX_POW | UCB_TS_CR_TSMX_POW |
				  UCB_TS_CR_MODE_POS | UCB_TS_CR_BIAS_ENA);

		udelay(55);

		return ucb1x00_adc_read(ts->ucb, UCB_ADC_INP_AD2, ts->adcsync);
	} else {	    
		ucb1x00_reg_write(ts->ucb, UCB_TS_CR,
				  UCB_TS_CR_TSMX_POW | UCB_TS_CR_TSPX_POW |
				  UCB_TS_CR_TSMY_GND | UCB_TS_CR_TSPY_GND |
				  UCB_TS_CR_MODE_PRES | UCB_TS_CR_BIAS_ENA);

		return ucb1x00_adc_read(ts->ucb, UCB_ADC_INP_TSPY, ts->adcsync);	
	}	
}

/*
 * Switch to X position mode and measure Y plate.  We switch the plate
 * configuration in pressure mode, then switch to position mode.  This
 * gives a faster response time.  Even so, we need to wait about 55us
 * for things to stabilise.
 */
static inline unsigned int ucb1x00_ts_read_xpos(struct ucb1x00_ts *ts)
{  
	if (machine_is_collie())
		ucb1x00_io_write(ts->ucb, 0, COLLIE_TC35143_GPIO_TBL_CHK);
	else {	    
		ucb1x00_reg_write(ts->ucb, UCB_TS_CR,
				  UCB_TS_CR_TSMX_GND | UCB_TS_CR_TSPX_POW |
				  UCB_TS_CR_MODE_PRES | UCB_TS_CR_BIAS_ENA);
		ucb1x00_reg_write(ts->ucb, UCB_TS_CR,
				  UCB_TS_CR_TSMX_GND | UCB_TS_CR_TSPX_POW |
				  UCB_TS_CR_MODE_PRES | UCB_TS_CR_BIAS_ENA);			  
				  
	}
	
	ucb1x00_reg_write(ts->ucb, UCB_TS_CR,
			UCB_TS_CR_TSMX_GND | UCB_TS_CR_TSPX_POW |
			UCB_TS_CR_MODE_POS | UCB_TS_CR_BIAS_ENA);

	udelay(55);

	return ucb1x00_adc_read(ts->ucb, UCB_ADC_INP_TSPY, ts->adcsync);
}

/*
 * Switch to Y position mode and measure X plate.  We switch the plate
 * configuration in pressure mode, then switch to position mode.  This
 * gives a faster response time.  Even so, we need to wait about 55us
 * for things to stabilise.
 */
static inline unsigned int ucb1x00_ts_read_ypos(struct ucb1x00_ts *ts)
{  
	if (machine_is_collie())
		ucb1x00_io_write(ts->ucb, 0, COLLIE_TC35143_GPIO_TBL_CHK);
	else {
	    
		ucb1x00_reg_write(ts->ucb, UCB_TS_CR,
				  UCB_TS_CR_TSMY_GND | UCB_TS_CR_TSPY_POW |
				  UCB_TS_CR_MODE_PRES | UCB_TS_CR_BIAS_ENA);
		ucb1x00_reg_write(ts->ucb, UCB_TS_CR,
				  UCB_TS_CR_TSMY_GND | UCB_TS_CR_TSPY_POW |
				  UCB_TS_CR_MODE_PRES | UCB_TS_CR_BIAS_ENA);
			  
	}

	ucb1x00_reg_write(ts->ucb, UCB_TS_CR,
			UCB_TS_CR_TSMY_GND | UCB_TS_CR_TSPY_POW |
			UCB_TS_CR_MODE_POS | UCB_TS_CR_BIAS_ENA);

	udelay(55);

	return ucb1x00_adc_read(ts->ucb, UCB_ADC_INP_TSPX, ts->adcsync);
}

/*
 * Switch to X plate resistance mode.  Set MX to ground, PX to
 * supply.  Measure current.
 */
static inline unsigned int ucb1x00_ts_read_xres(struct ucb1x00_ts *ts)
{
	ucb1x00_reg_write(ts->ucb, UCB_TS_CR,
			UCB_TS_CR_TSMX_GND | UCB_TS_CR_TSPX_POW |
			UCB_TS_CR_MODE_PRES | UCB_TS_CR_BIAS_ENA);
	return ucb1x00_adc_read(ts->ucb, 0, ts->adcsync);
}

/*
 * Switch to Y plate resistance mode.  Set MY to ground, PY to
 * supply.  Measure current.
 */
static inline unsigned int ucb1x00_ts_read_yres(struct ucb1x00_ts *ts)
{
	ucb1x00_reg_write(ts->ucb, UCB_TS_CR,
			UCB_TS_CR_TSMY_GND | UCB_TS_CR_TSPY_POW |
			UCB_TS_CR_MODE_PRES | UCB_TS_CR_BIAS_ENA);
	return ucb1x00_adc_read(ts->ucb, 0, ts->adcsync);
}

static inline int ucb1x00_ts_pen_down(struct ucb1x00_ts *ts)
{
	unsigned int val = ucb1x00_reg_read(ts->ucb, UCB_TS_CR);
	if (machine_is_collie()){
		return (!(val & (UCB_TS_CR_TSPX_LOW)));
	}	
	else{
		return (val & (UCB_TS_CR_TSPX_LOW | UCB_TS_CR_TSMX_LOW));
	}	
}

/*
 * This is a RT kernel thread that handles the ADC accesses
 * (mainly so we can use semaphores in the UCB1200 core code
 * to serialise accesses to the ADC).
 */
static int ucb1x00_thread(void *_ts)
{
	struct ucb1x00_ts *ts = _ts;
	struct task_struct *tsk = current;
	int valid;

	/*
	 * We could run as a real-time thread.  However, thus far
	 * this doesn't seem to be necessary.
	 */
//	tsk->policy = SCHED_FIFO;
//	tsk->rt_priority = 1;

	valid = 0;

	while (!kthread_should_stop()) {
		unsigned int x, y, p;

		ts->restart = 0;

		ucb1x00_adc_enable(ts->ucb);

		x = ucb1x00_ts_read_xpos(ts);
		y = ucb1x00_ts_read_ypos(ts);
		p = ucb1x00_ts_read_pressure(ts);

		/*
		 * Switch back to interrupt mode.
		 */
		ucb1x00_ts_mode_int(ts);
		ucb1x00_adc_disable(ts->ucb);

		//msleep(10);
		msleep(1);		
	//printk("ucb1x00-tc.c, %s(), line=%d\n", __FUNCTION__, __LINE__);
		ucb1x00_enable(ts->ucb);


		if (ucb1x00_ts_pen_down(ts)) {
			ucb1x00_enable_irq(ts->ucb, UCB_IRQ_TSPX, machine_is_collie() ? UCB_RISING : UCB_FALLING);
			ucb1x00_disable(ts->ucb);

			/*
			 * If we spat out a valid sample set last time,
			 * spit out a "pen off" sample here.
			 */
			if (valid) {
				ucb1x00_ts_event_release(ts);
				valid = 0;
			}

			/*
			 * Since ucb1x00_enable_irq() might sleep due
			 * to the way the UCB1400 regs are accessed, we
			 * can't use set_task_state() before that call,
			 * and not changing state before enabling the
			 * interrupt is racy. A completion handler avoids
			 * the issue.
			 */
			wait_for_completion_interruptible(&ts->irq_wait);
			//printk("ucb1x00-tc.c, %s(), line=%d\n", __FUNCTION__, __LINE__);
		} else {
			ucb1x00_disable(ts->ucb);
			//printk("ucb1x00-tc.c, %s(), line=%d\n", __FUNCTION__, __LINE__);
			/*
			 * Filtering is policy.  Policy belongs in user
			 * space.  We therefore leave it to user space
			 * to do any filtering they please.
			 */
			if (!ts->restart) {
				ucb1x00_ts_evt_add(ts, p, x, y);
				valid = 1;
			//printk("ucb1x00-tc.c, %s(), line=%d\n", __FUNCTION__, __LINE__);				
			}

			set_task_state(tsk, TASK_INTERRUPTIBLE);
			schedule_timeout(HZ/100);
		}

		try_to_freeze();
	}

	ts->rtask = NULL;
	return 0;
}

/*
 * We only detect touch screen _touches_ with this interrupt
 * handler, and even then we just schedule our task.
 */
static void ucb1x00_ts_irq(int idx, void *id)
{
	struct ucb1x00_ts *ts = id;
	//printk("usb1x00-ts.c %s(), line=%d\n", __FUNCTION__, __LINE__);
	ucb1x00_disable_irq(ts->ucb, UCB_IRQ_TSPX, UCB_FALLING);
	complete(&ts->irq_wait);
}

static int ucb1x00_ts_open(struct input_dev *idev)
{
	struct ucb1x00_ts *ts = idev->private;
	int ret = 0;

	BUG_ON(ts->rtask);
    ts_option(ts);
	init_completion(&ts->irq_wait);
	ret = ucb1x00_hook_irq(ts->ucb, UCB_IRQ_TSPX, ucb1x00_ts_irq, ts);
	if (ret < 0)
		goto out;

	/*
	 * If we do this at all, we should allow the user to
	 * measure and read the X and Y resistance at any time.
	 */
	ucb1x00_adc_enable(ts->ucb);
	ts->x_res = ucb1x00_ts_read_xres(ts);
	ts->y_res = ucb1x00_ts_read_yres(ts);
	ucb1x00_adc_disable(ts->ucb);

	ts->rtask = kthread_run(ucb1x00_thread, ts, "ktsd");
	if (!IS_ERR(ts->rtask)) {
		ret = 0;
	} else {
		ucb1x00_free_irq(ts->ucb, UCB_IRQ_TSPX, ts);
		ts->rtask = NULL;
		ret = -EFAULT;
	}

 out:
	return ret;
}

/*
 * Release touchscreen resources.  Disable IRQs.
 */
static void ucb1x00_ts_close(struct input_dev *idev)
{
	struct ucb1x00_ts *ts = idev->private;

	if (ts->rtask)
		kthread_stop(ts->rtask);

	ucb1x00_enable(ts->ucb);
	ucb1x00_free_irq(ts->ucb, UCB_IRQ_TSPX, ts);
	ucb1x00_reg_write(ts->ucb, UCB_TS_CR, 0);
	ucb1x00_disable(ts->ucb);
}

#ifdef CONFIG_PM
static int ucb1x00_ts_resume(struct ucb1x00_dev *dev)
{
	struct ucb1x00_ts *ts = dev->priv;

	if (ts->rtask != NULL) {
		/*
		 * Restart the TS thread to ensure the
		 * TS interrupt mode is set up again
		 * after sleep.
		 */
		ts->restart = 1;
		complete(&ts->irq_wait);
	}
	return 0;
}
#else
#define ucb1x00_ts_resume NULL
#endif


/*
 * Initialisation.
 */
static int ucb1x00_ts_add(struct ucb1x00_dev *dev)
{
	struct ucb1x00_ts *ts;

	ts = kzalloc(sizeof(struct ucb1x00_ts), GFP_KERNEL);
	if (!ts)
		return -ENOMEM;

	ts->idev = input_allocate_device();
	if (!ts->idev) {
		kfree(ts);
		return -ENOMEM;
	}

	ts->ucb = dev->ucb;
	ts->adcsync = adcsync ? UCB_SYNC : UCB_NOSYNC;

	ts->idev->private = ts;
	ts->idev->name       = "Touchscreen panel";
	ts->idev->id.product = ts->ucb->id;
	ts->idev->open       = ucb1x00_ts_open;
	ts->idev->close      = ucb1x00_ts_close;
	
	ts->idev->absfuzz[ABS_X] = 6;
	ts->idev->absfuzz[ABS_Y] = 6;	

    ts->idev->absmax[ABS_PRESSURE] = 0x100;

	__set_bit(EV_ABS, ts->idev->evbit);
	__set_bit(ABS_X, ts->idev->absbit);
	__set_bit(ABS_Y, ts->idev->absbit);
	__set_bit(ABS_PRESSURE, ts->idev->absbit);

	input_register_device(ts->idev);
	dev->priv = ts;
	ucb1x00_reg_write(ts->ucb, 16, 0x1000);
    
	return 0;
}

static void ucb1x00_ts_remove(struct ucb1x00_dev *dev)
{
	struct ucb1x00_ts *ts = dev->priv;

	input_unregister_device(ts->idev);
	kfree(ts);
}

static struct ucb1x00_driver ucb1x00_ts_driver = {
	.add		= ucb1x00_ts_add,
	.remove		= ucb1x00_ts_remove,
	.resume		= ucb1x00_ts_resume,
};

static int __init ucb1x00_ts_init(void)
{
        int lcd_type = TFT_NONE_TYPE;   
        int ret = 0;     	
        	
#ifdef CONFIG_MTLCD_0283224 
        lcd_type = TFT_MTLCD_0283224;
#endif    
		
#ifdef CONFIG_MTLCD_0353224 
        lcd_type = TFT_MTLCD_0353224;   		
#endif 
		
#ifdef CONFIG_MTLCD_0353224A   
        lcd_type = TFT_MTLCD_0353224A;   
#endif 
		
#ifdef CONFIG_MTLCD_1046448    
        lcd_type = TFT_MTLCD_1046448;  
#endif	
        
        select_lcd_index	= lcd_type ;
	
#ifdef MODULE	
	   ret = ucb1x00_ts_setup(g_lcdtype_options);	
	   if (ret != 0){
	   	   printk("lcdtype parameter  = MTLCD-0283224 or MTLCD-0353224 or MTLCD-0353224A or MTLCD-1046448\n");
       }	   	
#endif	
	   return ucb1x00_register_driver(&ucb1x00_ts_driver);
}


static void __exit ucb1x00_ts_exit(void)
{
	ucb1x00_unregister_driver(&ucb1x00_ts_driver);
}

module_param(adcsync, int, 0444);
#ifdef MODULE
module_param_string(lcdtype, g_lcdtype_options, sizeof(g_lcdtype_options), 0);
MODULE_PARM_DESC(lcdtype, "LCD Type(MTLCD-0283224, MTLCD-0353224, MTLCD-0353224A, MTLCD-1046448)");
#endif
module_init(ucb1x00_ts_init);
module_exit(ucb1x00_ts_exit);

MODULE_AUTHOR("Russell King <rmk@arm.linux.org.uk>");
MODULE_DESCRIPTION("UCB1x00 touchscreen driver");
MODULE_LICENSE("GPL");
