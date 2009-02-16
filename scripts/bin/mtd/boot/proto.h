
#include <sys/types.h>

// Prototypes from misc.c
extern void bcopy(void *s,void *d,int n);
extern void bzero(void *d,int n);
extern int bcmp(void *d,void *s,int n);
extern int strcasecmp(char *a,char *b);
extern char *sprintf(char *buf, char *fmt,...);
extern void printf(char *fmt,...);
extern int getdec(char **ptr);
extern int strncmp(char *a,char *b,unsigned long len);
extern void *memset(void *p,int c,size_t len);
#define memcpy(d,s,n) bcopy(s,d,n)

// Prototypes from Start32.S
extern void putchar(char c);
extern long currticks();
extern void xstart (unsigned long exec,unsigned long header,unsigned long bootp);
