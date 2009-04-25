   /*************************************************************************/
   //
   //                  P H I L I P S   P R O P R I E T A R Y
   //
   //           COPYRIGHT (c)   1999 BY PHILIPS SINGAPORE.
   //                     --  ALL RIGHTS RESERVED  --
   //
   // File Name:	Hal4sys.H
   // Author:       Hilbert Zhang ZhenYu
   // Created:		Nov. 26 99
   // Modified:
   // Revision:		0.0
   //
   /*************************************************************************/
   //
   /*************************************************************************/
#ifndef __HAL4SYS_H__
#define __HAL4SYS_H__

#include    	"asm/arch/lib/creator_s3c2410_addr.h"


char P0,P1,P2,P3;



// 8051 I/O map
#define MCU_P0          P0
#define MCU_P1          P1
#define MCU_P2          P2
#define MCU_P3          P3


// 8051 Machine cycle factor 1@12MHz, 2@24MHz, 3@36MHz
#define LOOP_MC                 10
#define MACHINECYCLE_AT12MHZ    0x01
#define MACHINECYCLE_AT24MHZ    0x02
#define MACHINECYCLE_AT36MHZ    0x03
#define MACHINECYCLE_AT48MHZ    0x04

// 8051 Timer
#define TIMER0_AT12MHZ          (0xFC)
#define TIMER0_AT24MHZ          (0xF8)
#define TIMER0_AT36MHZ          (0xF4)
#define TIMER0_AT48MHZ          (0xF0)

// 8051 REG Map
#define D12INT_EDGE	        IT0
#define D12INT_PRIORITY	    PX0
#define D12INT_ENABLE	    EX0

#define TIMER0_MODE	        TMOD
#define TIMER0_LOW          TL0
#define TIMER0_HIGH         TH0
#define TIMER0_PRIORITY	    PT0
#define TIMER0_IRQ_ENABLE	ET0
#define TIMER0_START    	TR0

// 8051 P1 port Map
//#define D12REG_ONLY         0xB8
#define D12REG_ONLY         0x38

#define D12REG_MASK         0x40
//#define D12REG_ATAREG4IN    0xD0    //ATA_CS0_N == ATA_CS1_N == 1
#define D12REG_ATAREG4IN    0x50

//#define D12REG_ATAREG4OUT   0x18    //ATA_CS0_N == ATA_CS1_N == 1 D12_CS == 0
#define D12REG_ATAREG4OUT   0x98

// 8051 Mem Address Space
#define ADDR_D12COMMAND     0x81
#define ADDR_D12DATA        0x80

// D12  map
#define D12_DATABUS        P0

//D12 map at P1
sbit D12CS_N            = 0x96;

// D12 map at P3
sbit D12A0              = 0xB0;
sbit IDE_CS             = 0xB1;
sbit D12INT_N           = 0xB2;
sbit D12RST_N           = 0xB5;
sbit D12WR_N            = 0xB6;
sbit D12RD_N            = 0xB7;
sbit D12SUSPD			= 0xB3;

// ATA  map
#define ATA_DATABUS_LO  MCU_P2
#define ATA_DATABUS_HI  MCU_P0

//ATA map at P1
sbit ATA_A0             = 0x90;
sbit ATA_A1             = 0x91;
sbit ATA_A2             = 0x92;
sbit ATA_CS0_N          = 0x93;
sbit ATA_CS1_N          = 0x94;
sbit BUFF_OE_N          = 0x95;
sbit BUFF_DIR_RD        = 0x97;

//ATA map at P3
sbit ATA_IORDY          = 0xB3;
sbit ATA_RST_N          = 0xB4;
sbit ATA_WR_N           = 0xB7;
sbit ATA_RD_N           = 0xB6;

/*
// ATA register file definition
*/
#define ATAREG_GRP0         0x50
#define ATAREG_GRP1         0x48

/*
#define ATAREG4OUT_DATA             0x1F0
#define ATAREG4IN_DATA              0x1F0
#define ATAREG4IN_DATA_H            0x1F0
#define ATAREG4IN_DATA_L            0x2F0   // special
#define ATAREG4OUT_FEATURE          0x1F1
#define ATAREG4IN_ERROR             0x1F1
#define ATAREG4OUT_SECTOR_COUNT     0x1F2
#define ATAREG4IN_SECTOR_COUNT      0x1F2
#define ATAREG4OUT_SECTOR_NUMBER    0x1F3
#define ATAREG4IN_SECTOR_NUMBER     0x1F3
#define ATAREG4OUT_CYLINDER_LOW     0x1F4
#define ATAREG4IN_CYLINDER_LOW      0x1F4
#define ATAREG4OUT_CYLINDER_HIGH    0x1F5
#define ATAREG4IN_CYLINDER_HIGH     0x1F5
#define ATAREG4OUT_DEVICE_HEAD      0x1F6
#define ATAREG4IN_DEVICE_HEAD       0x1F6
#define ATAREG4OUT_COMMAND          0x1F7
#define ATAREG4IN_STATUS			0x1F7
#define ATAREG4OUT_CONTROL          0x3F6
#define ATAREG4IN_ALTERNATE_STATUS  0x3F6

#define D12_command					0x100/
#define D12_data					0x101
#define D12_read_CPLD_writeL		0x102
#define D12_read_CPLD_writeH		0x103

*/
//Register Mapping
//================
/*
#define		UC			unsigned char
#define		UI			unsigned int
#define		US			unsigned short
#define		UL			unsigned long
#define		ULI			unsigned long int

//UC	xdata	D12_command _at_ 0x4001;	//Single Byte
//UC  xdata   D12_data    _at_ 0x4000;	//Single Byte


UI  xdata   IDE_WR_1F0  _at_ 0x8000;	//2 bytes
									//Eg, IDE_WR_1F0 = 0x0506;
									//(0x8000) = 0x05;
									//(0x8001) = 0x06;

UI  xdata   IDE_RD_1F0  _at_ 0x8002;	//2 bytes
									//Eg, IDE_WR_1F0 = 0x0506;
									//(0x8002) = 0x05;
									//(0x8003) = 0x06;
UC  xdata   IDE_1F1 	_at_ 0x8004; //Single Byte
UC  xdata   IDE_1F2 	_at_ 0x8005; //Single Byte
UC  xdata   IDE_1F3 	_at_ 0x8006; //Single Byte
UC  xdata   IDE_1F4 	_at_ 0x8007; //Single Byte
UC  xdata   IDE_1F5 	_at_ 0x8008; //Single Byte
UC  xdata   IDE_1F6 	_at_ 0x8009; //Single Byte
UC  xdata   IDE_1F7 	_at_ 0x800a; //Single Byte
UC  xdata   IDE_3F6 	_at_ 0x800b; //Single Byte
UC  xdata   IDE_3F7 	_at_ 0x800c; //Single Byte
*/

/*
#define D12_command					0x4001
#define D12_data					0x4000

#define ATAREG4OUT_DATA             0x8000

#define ATAREG4IN_DATA              0x8002


#define ATAREG4OUT_FEATURE          0x8004
#define ATAREG4IN_ERROR             0x8004
#define ATAREG4OUT_SECTOR_COUNT     0x8005
#define ATAREG4IN_SECTOR_COUNT      0x8005
#define ATAREG4OUT_SECTOR_NUMBER    0x8006
#define ATAREG4IN_SECTOR_NUMBER     0x8006
#define ATAREG4OUT_CYLINDER_LOW     0x8007
#define ATAREG4IN_CYLINDER_LOW      0x8007
#define ATAREG4OUT_CYLINDER_HIGH    0x8008
#define ATAREG4IN_CYLINDER_HIGH     0x8008
#define ATAREG4OUT_DEVICE_HEAD      0x8009
#define ATAREG4IN_DEVICE_HEAD       0x8009
#define ATAREG4OUT_COMMAND          0x800a
#define ATAREG4IN_STATUS			0x800a
#define ATAREG4OUT_CONTROL          0x800b
#define ATAREG4IN_ALTERNATE_STATUS  0x800b

#define ATARead2BWriteD12_1B        0x4002
#define ATARead0BWriteD12_2B        0x4004

*/
// Functions
//#define RaiseIRQL()	        EA=0
//#define LowerIRQL()	        EA=1

/*Give up all ports*/ 
#define Hal4Sys_InitMCU()   MCU_P0 = 0xFF; MCU_P1 = 0xFF; MCU_P2 = 0xFF; MCU_P3 = 0xFF

                        	


INT16 Hal4Sys_SwapINT16(INT16 wData);
INT32 Hal4Sys_SwapINT32(INT32 dData);

void Hal4Sys_InitTimer0(void);

void Hal4Sys_Wait4US(void);
void Hal4Sys_WaitInUS(INT16 time);
void Hal4Sys_WaitInMS(INT8 time);

void Hal4Sys_D12CmdPortOutB( INT8 val);
void Hal4Sys_D12DataPortOutB( INT8 val);
unsigned char Hal4Sys_D12DataPortInB( void);

void Hal4Sys_ResetD12(void);
void Hal4Sys_InitD12(void);

//    something different

void Hal4Sys_ATAPortOutB(INT32 Addr, INT8 Data);
INT8 Hal4Sys_ATAPortInB(INT32 Addr);
INT16 Hal4Sys_ATADataPortInW(void);
/*
void Hal4Sys_ATAPortOutB(INT16 Addr, INT8 Data);
INT8 Hal4Sys_ATAPortInB(INT16 Addr);
INT16 Hal4Sys_ATADataPortInW(void);
*/
#endif
