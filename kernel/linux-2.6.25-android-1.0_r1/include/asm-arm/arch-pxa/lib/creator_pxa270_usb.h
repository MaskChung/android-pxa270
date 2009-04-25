//=============================================================================
// File Name : creator_s3c2410_usb.h
// Function  : usb storage device drvier define
// Program   :
// Date      : 03/05/2004
// Version   : 1.00
// History
//   1.0.0 : Programming start (03/05/2004) -> SOP
//=============================================================================
 
#ifndef _CREATOR_S3C2410_usb_H_ 
#define _CREATOR_S3C2410_usb_H_ 

#include <linux/config.h>
#if defined(__linux__)
#include <asm/ioctl.h>		/* For _IO* macros */
#define USB_IOCTL_NR(n)	     		_IOC_NR(n)
#elif defined(__FreeBSD__)
#include <sys/ioccom.h>
#define USB_IOCTL_NR(n)	     		((n) & 0xff)
#endif

#define USB_MAJOR_NUM			125
#define USB_IOCTL_MAGIC			USB_MAJOR_NUM
#define USB_IO(nr)			_IO(USB_IOCTL_MAGIC,nr)
#define USB_IOR(nr,size)		_IOR(USB_IOCTL_MAGIC,nr,size)
#define USB_IOW(nr,size)		_IOW(USB_IOCTL_MAGIC,nr,size)
#define USB_IOWR(nr,size)		_IOWR(USB_IOCTL_MAGIC,nr,size)



#endif // _CREATOR_S3C2410_USB_H_ 
