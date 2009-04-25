
#ifndef __CREATOR_DEF_H__
#define __CREATOR_DEF_H__

#define U32 unsigned int
#define U16 unsigned short
#define S32 int
#define S16 short int
#define U8  unsigned char
#define	S8  char

#define UL	U32
#define UI	U16
#define UC	U8
#define C	S8
#define I	S16
#define L	S32

/***********************************************************************
        return flag
************************************************************************/
#define OK       1
#define YES      1
#define ON       1
#define TRUE     1
#define FALSE    0
#define UM       0
#define OFF      0
#define NO       0
#define STRMATCH 0

/***********************************************************************
        union reference size
************************************************************************/
#define BYTE1   3
#define BYTE2   2
#define BYTE3   1
#define BYTE4   0
#define WORD1   1
#define WORD2   0
typedef union   CVT {
        UL  l ;
        UI  w[2] ;
        UC  b[4] ;
} UN_CVT;

#define ARRAY_LENGTH(x)     ( sizeof(x) / sizeof(x[0]) )

#endif /*__CREATOR_DEF_H__*/
