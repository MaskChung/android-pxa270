/**************************************************************************
MISC Support Routines
**************************************************************************/

#include <stdarg.h>
#include <asm/byteorder.h>
#include <asm/io.h>
#include "proto.h"

void bcopy(void *s,void *d,int n)
{
	char *dd = d, *ss = s;
	while ((n--) > 0) {
		*(dd++) = *(ss++);
	}
}

void bzero(void *d,int n)
{
	char *dd = d;
	while ((n--) > 0) {
		*(dd++) = 0;
	}
}

int bcmp(void *d,void *s,int n)
{
	char *dd = d, *ss = s;
	while ((n--) > 0) {
		if (*(dd++) != *(ss++)) return(1);
	}
	return(0);
}

int strcasecmp(char *a,char *b)
{
	while (*a && *b && (*a & ~0x20) == (*b & ~0x20)) {a++; b++; }
	return((*a & ~0x20) - (*b & ~0x20));
}

int strncmp(char *a,char *b,unsigned long len)
{
   for (; len != 0; len--, a++, b++)
      if (*a != *b)
	 return *a - *b;
   return 0;
}

void *memset(void *p,int Set,size_t Len)
{
   unsigned char *I = p;

   for (; Len != 0; Len--,I++)
      *I = Set;
   return p;
}

/**************************************************************************
PRINTF and friends

	Formats:
		%X	- 4 byte ASCII (8 hex digits)
		%x	- 2 byte ASCII (4 hex digits)
		%b	- 1 byte ASCII (2 hex digits)
		%d	- decimal (also %i)
		%c	- ASCII char
		%s	- ASCII string
		%I	- Internet address in x.x.x.x notation
**************************************************************************/
static char hex[]="0123456789ABCDEF";
char *do_printf(char *buf,char *fmt,va_list args)
{
	register char *p;
	char tmp[16];
	while (*fmt) {
	        if (*fmt == '\n')
		        *(buf++) = '\r';

		if (*fmt == '%') {	/* switch() uses more space */
			fmt++;

			if (*fmt == 'X') {
				register long h = va_arg(args,long);
				*(buf++) = hex[(h>>28)& 0x0F];
				*(buf++) = hex[(h>>24)& 0x0F];
				*(buf++) = hex[(h>>20)& 0x0F];
				*(buf++) = hex[(h>>16)& 0x0F];
				*(buf++) = hex[(h>>12)& 0x0F];
				*(buf++) = hex[(h>>8)& 0x0F];
				*(buf++) = hex[(h>>4)& 0x0F];
				*(buf++) = hex[h& 0x0F];
			}
			if (*fmt == 'x') {
				register int h = va_arg(args,int);
				*(buf++) = hex[(h>>12)& 0x0F];
				*(buf++) = hex[(h>>8)& 0x0F];
				*(buf++) = hex[(h>>4)& 0x0F];
				*(buf++) = hex[h& 0x0F];
			}
			if (*fmt == 'b') {
				register int h = va_arg(args,int);
				*(buf++) = hex[(h>>4)& 0x0F];
				*(buf++) = hex[h& 0x0F];
			}
			if ((*fmt == 'd') || (*fmt == 'i')) {
				register int dec = va_arg(args,int);
				p = tmp;
				if (dec < 0) {
					*(buf++) = '-';
					dec = -dec;
				}
				do {
					*(p++) = '0' + (dec%10);
					dec = dec/10;
				} while(dec);
				while ((--p) >= tmp) *(buf++) = *p;
			}
			if (*fmt == 'I') {
				register long h = va_arg(args,long);
				buf = sprintf(buf,"%d.%d.%d.%d",
					(int)(h>>24) & 0x00FF,
					(int)(h>>16) & 0x00FF,
					(int)(h>>8) & 0x00FF,
					(int)h & 0x00FF);
			}
			if (*fmt == 'c')
				*(buf++) = va_arg(args,char);
			if (*fmt == 's') {
				p = va_arg(args,char *);
				while (*p) *(buf++) = *p++;
			}
		} else *(buf++) = *fmt;
		fmt++;
	}
	*buf = 0;
	return(buf);
}

char *sprintf(char *buf, char *fmt,...)
{
   va_list list;
   char *res;
   va_start(list,fmt);
   res = do_printf(buf,fmt,list);
   va_end(list);
   return res;
}

void printf(char *fmt,...)
{
   va_list list;
   char buf[120],*p;
   p = buf;
   va_start(list,fmt);
   do_printf(buf,fmt,list);
   while (*p) putchar(*p++);
   va_end(list);
}

int getdec(char **ptr)
{
	char *p = *ptr;
	int ret=0;
	if ((*p < '0') || (*p > '9')) return(-1);
	while ((*p >= '0') && (*p <= '9')) {
		ret = ret*10 + (*p - '0');
		p++;
	}
	*ptr = p;
	return(ret);
}

#define K_RDWR 		0x60		/* keyboard data & cmds (read/write) */
#define K_STATUS 	0x64		/* keyboard status */
#define K_CMD	 	0x64		/* keybd ctlr command (write-only) */

#define K_OBUF_FUL 	0x01		/* output buffer full */
#define K_IBUF_FUL 	0x02		/* input buffer full */

#define KC_CMD_WIN	0xd0		/* read  output port */
#define KC_CMD_WOUT	0xd1		/* write output port */
#define KB_A20		0xdf		/* enable A20,
					   enable output buffer full interrupt
					   enable data line
					   disable clock line */
#ifndef	IBM_L40

static void empty_8042(void)
{
	extern void slowdownio();
	unsigned long time;
	char st;

  	slowdownio();
	time = currticks() + 18;	/* max wait of 1 second */
  	while ((((st = inb(K_CMD)) & K_OBUF_FUL) ||
	       (st & K_IBUF_FUL)) &&
	       currticks() < time)
		inb(K_RDWR);
}
#endif	IBM_L40

/*
 * Gate A20 for high memory
 */
void gateA20()
{
#ifdef	IBM_L40
	outb(0x92, 0x2);
#else	IBM_L40
	empty_8042();
	outb(K_CMD, KC_CMD_WOUT);
	empty_8042();
	outb(K_RDWR, KB_A20);
	empty_8042();
#endif	IBM_L40
}

/*
 * Local variables:
 *  c-basic-offset: 8
 * End:
 */
