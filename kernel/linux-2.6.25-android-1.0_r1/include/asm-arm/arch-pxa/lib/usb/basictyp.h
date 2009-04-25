   //*************************************************************************
   //
   //                  P H I L I P S   P R O P R I E T A R Y
   //
   //           COPYRIGHT (c)   1999 BY PHILIPS SINGAPORE.
   //                     --  ALL RIGHTS RESERVED  --
   //
   // File Name:        BasicTyp.H
   // Author:           Hilbert Zhang ZhenYu
   //                   Chew Thing Piao
   // Created:          Oct. 1 99
   // Modified:
   // Revision: 		0.0
   //
   /*************************************************************************/
   //
   /*************************************************************************/


#ifndef __BASIC_TYPE_H__
#define __BASIC_TYPE_H__


#define IRQL_0
#define IRQL_1

#define OUT
#define IN

/*************************************************************************/
// basic typedefs
/*************************************************************************/
typedef unsigned char       sbit;
typedef unsigned char       bit;
typedef unsigned char   	BOOLEAN;

typedef unsigned char       INT8;
typedef unsigned short      INT16;
typedef unsigned long       INT32;
typedef unsigned char *     PINT8;
typedef unsigned short *    PINT16;
typedef unsigned long *     PINT32;


//typedef bit                 BOOLEAN;
//typedef bit                 int;



#define SFR    sfr		 // 8 bits special function register
#define SBIT   sbit		 // bit access special function register

#define DATA   data		 // direct access internal data RAM
#define IDATA  idata		 // indirect access internal data RAM
#define XDATA  xdata		 // external access external data RAM
#define PDATA  pdata		 // paged access external data RAM
#define CODE   code		 // program code area


/*************************************************************************/
// basic typedefs for structures
/*************************************************************************/
#ifdef BIG_ENDIAN
typedef union {
    struct 
    {
        INT8    Type;
        INT8    Index;
    } Descriptor;

    struct 
    {
        INT8 tx0;  // MSB for 8051 Keil C
        INT8 tx1;   
        INT8 endp;   
        INT8 c0;   // LSB for 8051 Keil C
    } chars;

    struct 
    {
        INT8 c3;  // MSB for 8051 Keil C
        INT8 c2;   
        INT8 c1;   
        INT8 c0;   // LSB for 8051 Keil C
    } chars0;

    struct
    {
        INT16 i1;    // MSW for 8051 keil C
        INT16 i0;    // LSW for 8051 Keil C
    } ints;

    INT32 u0;

} FLEXI_INT32;

typedef union  {
    struct 
    {
        INT8 c1;   // MSB for 8051 Keil C
        INT8 c0;   // LSB for 8051 Keil C
    } chars;

    INT16 i0;
} FLEXI_INT16, * PFLEXI_INT16;

#else // Little Endian

typedef union {
    struct 
    {       
        INT8    Index;
        INT8    Type;
        INT8    desc0;
        INT8    desc1;
    }__attribute__ ((packed))  Descriptor;

    struct 
    {
        INT8 endp;   
        INT8 c0;   
        INT8 tx0;  
        INT8 tx1;   
    }__attribute__ ((packed))  chars;

    struct 
    {
        INT8 c0;	// LSB
        INT8 c1;   
        INT8 c2;   
        INT8 c3;	// MSB 
    }__attribute__ ((packed))  chars0;

    struct
    {
        INT16 i0;    // LSW 
        INT16 i1;    // MSW 
    }__attribute__ ((packed))  ints;

    INT32 u0;

}__attribute__ ((packed))  FLEXI_INT32;

typedef union  {
    struct 
    {
        INT8 c0;   // LSB for 8051 Keil C
        INT8 c1;   // MSB for 8051 Keil C
    }__attribute__ ((packed))  chars;

    INT16 i0;
}__attribute__ ((packed))  FLEXI_INT16, * PFLEXI_INT16;
#endif 


#define DATA_SEG    data
#define BDATA_SEG   bdata
#define IDATA_SEG   idata
#define XDATA_SEG   xdata

#ifdef  GLOBAL_EXT
#define BIT_EXT     bit
#define STRUC_EXT   
#define INT8_EXT    INT8
#define INT16_EXT   INT16
#define INT32_EXT   INT32
#else
#define BIT_EXT     extern bit
#define STRUC_EXT   extern 
#define INT8_EXT    extern INT8
#define INT16_EXT   extern INT16
#define INT32_EXT   extern INT32
#endif

#endif
