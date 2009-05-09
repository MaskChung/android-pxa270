/*
 * linux/drivers/input/keyboard/android_keypad.c
 *
 * Modified from driver for the pxa27x matrix keyboard controller.
 * Modified from linux/drivers/input/keyboard/pxa27x_keypad.c
 *
 * Modified:	April 6, 2009
 * Author:	Mask <cycdisk@gmail.com>
 *
 * Created:	Feb 22, 2007
 * Author:	Rodolfo Giometti <giometti@linux.it>
 *
 * Based on a previous implementations by Kevin O'Connor
 * <kevin_at_koconnor.net> and Alex Osborne <bobofdoom@gmail.com> and
 * on some suggestions by Nicolas Pitre <nico@cam.org>.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 2 as
 * published by the Free Software Foundation.
 */


#include <linux/kernel.h>
#include <linux/module.h>
#include <linux/init.h>
#include <linux/interrupt.h>
#include <linux/input.h>
#include <linux/device.h>
#include <linux/platform_device.h>
#include <linux/clk.h>
#include <linux/err.h>

#include <asm/mach-types.h>
#include <asm/mach/arch.h>
#include <asm/mach/map.h>

#include <asm/arch/hardware.h>
#include <asm/arch/android_keypad.h>

#include <linux/kthread.h>
#include <linux/freezer.h>

#define MAX_MATRIX_KEY_NUM	(4 * 4)
#define LOG_LEVEL KERN_ALERT

struct android_keypad {
	struct android_keypad_platform_data *pdata;// maybe take off
	struct input_dev *input_dev;
	unsigned long jiffy;
	unsigned long jiffy_diff;
	struct task_struct *polling_thread;
	wait_queue_head_t keypad_wq;
	struct mutex mutex;
	spinlock_t spinlock;
	unsigned long idle_period;
};

static DEFINE_MUTEX(mutex_io_reg2);
void io_reg2_write(unsigned short val)
//unsigned short io_reg2_write(unsigned short val)
{
	mutex_lock(&mutex_io_reg2);
	//read io_reg2 first
//	IO_REG2 = IO_REG2 | val;
	IO_REG2 = val;
	val = IO_REG2;
	//printk("%s: IO_REG2 = %x\n",val);
	mutex_unlock(&mutex_io_reg2);
//	printk("%s: IO_REG2 = %x\n",__func__,val);
}

static DEFINE_MUTEX(mutex_io_reg1);
unsigned short io_reg1_read(void)
{
	unsigned short val;
	mutex_lock(&mutex_io_reg1);
	//val = IO_REG1 & 0xf00;
	val = IO_REG1;
	mutex_unlock(&mutex_io_reg1);
	//printk("%s: IO_REG1 = %x\n",__func__,val);
	if(val != 0xfff)
	printk("%s: IO_REG1 = %x\n",__func__,val);
	return val;
}

/*	1(KEY_RESERVED)	2(KEY_UP)	3(KEY_RESERVED)	A(KEY_MENU)
 *	4(KEY_LEFT)	5(KEY_RESERVED)	6(KEY_RIGHT)	B(KEY_HOME)
 *	7(KEY_RESERVED)	8(KEY_DOWN)	9(KEY_RESERVED)	C(KEY_BACKSPACE)
 *	*(KEY_LEFTSHIFT)0(KEY_RESERVED)	#(KEY_RESERVED)	D(KEY_SPACE)
*/
struct android_keymap {
	unsigned int hw_key;
	unsigned int keycode;
	unsigned int shift_keycode;
};
#define KEY(r3,r2,r1,r0, c3,c2,c1,c0) \
( (r3<<7)|(r2<<6)|(r1<<5)|(r0<<4)|(c3<<3)|(c2<<2)|(c1<<1)|c0 )
static struct android_keymap keymap[] = {
{KEY(1,1,1,0, 1,1,1,0),		KEY_1,		KEY_RESERVED},
{KEY(1,1,1,0, 1,1,0,1),		KEY_2,		KEY_UP},
{KEY(1,1,1,0, 1,0,1,1),		KEY_3,		KEY_RESERVED},
{KEY(1,1,1,0, 0,1,1,1),		KEY_A,		KEY_MENU},
{KEY(1,1,0,1, 1,1,1,0),		KEY_4,		KEY_LEFT},
{KEY(1,1,0,1, 1,1,0,1),		KEY_5,		KEY_RESERVED},
{KEY(1,1,0,1, 1,0,1,1),		KEY_6,		KEY_RIGHT},
{KEY(1,1,0,1, 0,1,1,1),		KEY_B,		KEY_HOME},
{KEY(1,0,1,1, 1,1,1,0),		KEY_7,		KEY_RESERVED},
{KEY(1,0,1,1, 1,1,0,1),		KEY_8,		KEY_DOWN},
{KEY(1,0,1,1, 1,0,1,1),		KEY_9,		KEY_RESERVED},
{KEY(1,0,1,1, 0,1,1,1),		KEY_C,		KEY_BACKSPACE},
{KEY(0,1,1,1, 1,1,1,0),		KEY_LEFTSHIFT,	KEY_LEFTSHIFT},
{KEY(0,1,1,1, 1,1,0,1),		KEY_0,		KEY_RESERVED},
{KEY(0,1,1,1, 1,0,1,1),		KEY_D,		KEY_SPACE},
{0,KEY_UNKNOWN, KEY_UNKNOWN },
};

static void android_keypad_setkeycode(struct android_keypad *keypad)
{
	struct input_dev *input_dev = keypad->input_dev;
	unsigned int i;
	for(i = 0; keymap[i].keycode != KEY_UNKNOWN; ++i)
	{
		set_bit(keymap[i].keycode,input_dev->keybit);
		set_bit(keymap[i].shift_keycode,input_dev->keybit);
	}
}

static unsigned int lookup_keycode(unsigned int hw_key)
{
	unsigned int i;
	static bool shift_key = false; // maybe change to static
	for(i = 0; keymap[i].keycode != KEY_UNKNOWN; ++i)
	{
		if(keymap[i].hw_key == hw_key)
			break;
	}
	if(keymap[i].keycode == KEY_LEFTSHIFT)
		shift_key = !shift_key;
	if(shift_key)
		return keymap[i].shift_keycode;
	return keymap[i].keycode;
}

#define MAX_IDLE_MSEC (1*300) // 0.5 sec
//#define MAX_IDLE_MSEC (1*1000) // 1 sec
#define MIN_IDLE_MSEC (1*100) // 0.1 sec
#define IDLE_STEPS 3
static unsigned int min_idle_msec = MAX_IDLE_MSEC;
static unsigned int max_idle_msec = MIN_IDLE_MSEC;
static unsigned int idle_steps = IDLE_STEPS;
#define SCAN(r3,r2,r1,r0) ( (r3<<3)|(r2<<2)|(r1<<1)|r0 )
unsigned short scan_key[] = {
SCAN(1,1,1,0),
SCAN(1,1,0,1),
SCAN(1,0,1,1),
SCAN(0,1,1,1),
};
static int android_keypad_thread(void * data)
{
	struct android_keypad *keypad = data;
	struct sched_param param = { .sched_priority = 1 };
	unsigned short scan;
	unsigned int keycode;
	int i;

	keypad->idle_period = min_idle_msec;
	//keypad->idle_period = msecs_to_jiffies(min_idle_msec);
	sched_setscheduler(current, SCHED_FIFO, &param);
	current->flags |= PF_NOFREEZE;
	do {
//		scan = SCAN(1,1,1,0);
		i = 0;
		//scan = scan_key[i];
		spin_lock(&keypad->spinlock);
		do
		{
		scan = scan_key[i];
			io_reg2_write(scan); // maybe change to 0xe because bit 4~7 don't care
			keycode = lookup_keycode( (scan << 4) | ((io_reg1_read() & 0x0f00) >> 8));
			if(keycode != KEY_UNKNOWN)
				break;
//			scan = ((scan << 1) | (scan >> 3)) & 0xf;
			++i;
		} while(i < ARRAY_SIZE(scan_key));
		//} while(scan != SCAN(1,1,1,0));
		spin_unlock(&keypad->spinlock);
		if(keycode == KEY_UNKNOWN) // no input --- need to modify
		{
			if(keypad->idle_period < max_idle_msec)
				keypad->idle_period += (max_idle_msec - min_idle_msec)/idle_steps;
			if(keypad->idle_period > max_idle_msec)
				keypad->idle_period = max_idle_msec;
		}
		else
		{
			// calculate jiffies to cancel fast continuing key
			keypad->jiffy_diff = jiffies - keypad->jiffy;
			keypad->jiffy = jiffies;
			input_report_key(keypad->input_dev,keycode,1);
			input_sync(keypad->input_dev);
			input_report_key(keypad->input_dev,keycode,0);
			input_sync(keypad->input_dev);
			keypad->idle_period = min_idle_msec;
			printk(LOG_LEVEL "%s: jiffy_diff = %lu\n", __func__,keypad->jiffy_diff);
			printk(LOG_LEVEL "%s: keycode = %d\n",__func__,keycode);
		}
		set_task_state(current, TASK_INTERRUPTIBLE);
		if (!kthread_should_stop())
			schedule_timeout(msecs_to_jiffies(keypad->idle_period));
		set_task_state(current, TASK_RUNNING);
	} while(!kthread_should_stop());
	return 0;
}

static int android_keypad_open(struct input_dev *dev)
{
	struct android_keypad *keypad = input_get_drvdata(dev); // remember check "private" in _probe function
	int err;

	keypad->polling_thread = kthread_run(android_keypad_thread,keypad,"kandroid_keypadd");
	if(IS_ERR(keypad->polling_thread))
	{
		err = PTR_ERR(keypad->polling_thread);
		printk(LOG_LEVEL "%s: create kthread ERROR: %d\n",__func__,err);
		return err;
	}

	return 0;
}

static void android_keypad_close(struct input_dev *dev)
{
	struct android_keypad *keypad = input_get_drvdata(dev);

	kthread_stop(keypad->polling_thread);
}

#ifdef CONFIG_PM
static int android_keypad_suspend(struct platform_device *pdev, pm_message_t state)
{
	struct android_keypad *keypad = platform_get_drvdata(pdev);

	mutex_lock(&keypad->mutex);
	keypad->idle_period = max_idle_msec;
	mutex_unlock(&keypad->mutex);

	return 0;
}

static int android_keypad_resume(struct platform_device *pdev)
{
	struct android_keypad *keypad = platform_get_drvdata(pdev);

	mutex_lock(&keypad->mutex);
	keypad->idle_period = min_idle_msec;
	mutex_unlock(&keypad->mutex);

	return 0;
}
#else
#define android_keypad_suspend	NULL
#define android_keypad_resume	NULL
#endif

static int __devinit android_keypad_probe(struct platform_device *pdev)
{
	struct android_keypad *keypad;
	struct input_dev *input_dev;
	int error;

	keypad = kzalloc(sizeof(struct android_keypad), GFP_KERNEL);
	if (keypad == NULL) {
		dev_err(&pdev->dev, "failed to allocate driver data\n");
		return -ENOMEM;
	}

	mutex_init(&keypad->mutex);
	spin_lock_init(&keypad->spinlock);
/*
	keypad->pdata = pdev->dev.platform_data;
	if (keypad->pdata == NULL) {
		dev_err(&pdev->dev, "no platform data defined\n");
		error = -EINVAL;
		goto failed_free;
	}
*/
	/* Create and register the input driver. */
	input_dev = input_allocate_device();
	if (!input_dev) {
		dev_err(&pdev->dev, "failed to allocate input device\n");
		error = -ENOMEM;
		goto failed_free;
	}

	input_dev->name = pdev->name;
	input_dev->id.bustype = BUS_HOST;
	input_dev->open = android_keypad_open;
	input_dev->close = android_keypad_close;
	input_dev->dev.parent = &pdev->dev;

	keypad->input_dev = input_dev;
	input_set_drvdata(input_dev, keypad);

	//input_dev->evbit[0] = BIT_MASK(EV_KEY) | BIT_MASK(EV_REP) |
	//	BIT_MASK(EV_REL);
	//input_dev->evbit[0] = BIT(EV_KEY) | BIT(EV_REP) |
	//	BIT(EV_REL);
	//input_dev->evbit[0] = BIT(EV_KEY) | BIT(EV_REL);
	set_bit(EV_KEY,input_dev->evbit); 
	set_bit(EV_REL,input_dev->evbit); 
//	set_bit(EV_SYN,input_dev->evbit); 
//	set_bit(EV_REP,input_dev->evbit); 

	android_keypad_setkeycode(keypad);
	platform_set_drvdata(pdev, keypad);

	/* Register the input device */
	error = input_register_device(input_dev);
	if (error) {
		dev_err(&pdev->dev, "failed to register input device\n");
		goto failed_free_dev;
	}

	return 0;

failed_free_dev:
	platform_set_drvdata(pdev, NULL);
	input_free_device(input_dev);
failed_free:
	kfree(keypad);
	return error;
}

static int __devexit android_keypad_remove(struct platform_device *pdev)
{
	struct android_keypad *keypad = platform_get_drvdata(pdev);

	input_unregister_device(keypad->input_dev);
	input_free_device(keypad->input_dev);

	platform_set_drvdata(pdev, NULL);
	kfree(keypad);
	return 0;
}

static struct platform_driver android_keypad_driver = {
	.probe		= android_keypad_probe,
	.remove		= __devexit_p(android_keypad_remove),
	.suspend	= android_keypad_suspend,
	.resume		= android_keypad_resume,
	.driver		= {
		.name	= "android-keypad",
	},
};

static int __init android_keypad_init(void)
{
	return platform_driver_register(&android_keypad_driver);
}

static void __exit android_keypad_exit(void)
{
	platform_driver_unregister(&android_keypad_driver);
}

module_init(android_keypad_init);
module_exit(android_keypad_exit);

MODULE_DESCRIPTION("Android Keypad Controller Driver");
MODULE_LICENSE("GPL");
