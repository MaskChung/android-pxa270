   /*************************************************************************/
   //
   //                  P H I L I P S   P R O P R I E T A R Y
   //
   //           COPYRIGHT (c)   1999 BY PHILIPS SINGAPORE.
   //                     --  ALL RIGHTS RESERVED  --
   //
   // File Name:	    Hal4D12.H
   // Author:           Hilbert Zhang ZhenYu
   //                   Chew Thing Piao
   // Created:		Oct. 1 99
   // Modified:
   // Revision:		0.0
   //
   /*************************************************************************/
   //
   /*************************************************************************/
#ifndef __HAL4D12_H__
#define __HAL4D12_H__



// D12 data sheet
#define D12_FIFOEMPTY      0xff

#define EP0_TX_FIFO_SIZE   16
#define EP0_RX_FIFO_SIZE   16
#define EP0_PACKET_SIZE    16

#define EP1_TX_FIFO_SIZE   16
#define EP1_RX_FIFO_SIZE   16
#define EP1_PACKET_SIZE    16

#define EP2_TX_FIFO_SIZE   64
#define EP2_RX_FIFO_SIZE   64
#define EP2_PACKET_SIZE    64

#define D12CMD_SNDRESUME        0xF6
#define D12CMD_RDCURFRAME       0xF5

#define D12CMD_SETADDR          0xD0
#define D12CMD_SETENDP          0xD8
#define D12CMD_SETMODE          0xF3
#define D12CMD_SETDMA           0xFB

#define D12CMD_RDIR             0xF4

#define D12CMD_SELEP0OUT        0x00
#define D12CMD_SELEP0IN         0x01
#define D12CMD_SELEP1OUT        0x02
#define D12CMD_SELEP1IN         0x03
#define D12CMD_SELEP2OUT        0x04
#define D12CMD_SELEP2IN         0x05

#define D12CMD_RDBUFFER         0xF0
#define D12CMD_WRBUFFER         0xF0
#define D12CMD_ACKSETUP         0xF1
#define D12CMD_CLRBUFFER        0xF2
#define D12CMD_VALIDBUFFER      0xFA

#define D12CMD_RDLTSEP0OUT      0x40
#define D12CMD_RDLTSEP0IN       0x41
#define D12CMD_RDLTSEP1OUT      0x42
#define D12CMD_RDLTSEP1IN       0x43
#define D12CMD_RDLTSEP2OUT      0x44
#define D12CMD_RDLTSEP2IN       0x45

#define D12CMD_SETEP0OUTSTS     0x40
#define D12CMD_SETEP0INSTS      0x41
#define D12CMD_SETEP1OUTSTS     0x42
#define D12CMD_SETEP1INSTS      0x43
#define D12CMD_SETEP2OUTSTS     0x44
#define D12CMD_SETEP2INSTS      0x45

#define D12CMD_RDEP0OUTSTS      0x80
#define D12CMD_RDEP0INSTS       0x81
#define D12CMD_RDEP1OUTSTS      0x82
#define D12CMD_RDEP1INSTS       0x83
#define D12CMD_RDEP2OUTSTS      0x84
#define D12CMD_RDEP2INSTS       0x85



#define D12_NOLAZYCLOCK			0x02
#define D12_CLOCKRUNNING        0x04
#define D12_INTERRUPTMODE		0x08
#define D12_SOFTCONNECT			0x10
#define D12_ENDP_NONISO			0x00
#define D12_ENDP_ISOOUT			0x40
#define D12_ENDP_ISOIN			0x80
#define D12_ENDP_ISOIO			0xC0

#define D12_CLOCK_12M			0x03
#define D12_CLOCK_4M			0x0b
#define D12_SETTOONE            0x40
#define D12_SOFONLY				0x80

#define D12_DMASINGLE			0x00
#define D12_BURST_4				0x01
#define D12_BURST_8				0x02
#define D12_BURST_16			0x03
#define D12_DMAENABLE           0x04
#define D12_DMA_INTOKEN			0x08
#define D12_AUTOLOAD			0x10
#define D12_NORMALPLUSSOF		0x20
#define D12_ENDP4INTENABLE		0x40
#define D12_ENDP5INTENABLE		0x80	// bug fixed in V2.1

#define D12_INT_ENDP0OUT		0x01
#define D12_INT_ENDP0IN			0x02
#define D12_INT_ENDP1OUT		0x04
#define D12_INT_ENDP1IN			0x08
#define D12_INT_ENDP2OUT		0x10
#define D12_INT_ENDP2IN			0x20
#define D12_INT_BUSRESET		0x40
#define D12_INT_SUSPENDCHANGE	0x80
#define D12_INT_EOT				0x0100

#define D12_SETUPPACKET			0x20

#define D12_BUFFER0FULL			0x20
#define D12_BUFFER1FULL			0x40

#define D12_FULLEMPTY			0x01
#define D12_STALL				0x02


void Hal4D12_SetAddressEnable(INT8 bAddress, bit bEnable);

void Hal4D12_SetEndpointEnable(bit bEnable);

//void Hal4D12_SendResume(void);

void Hal4D12_AcknowledgeEndpoint(INT8 endp);

void Hal4D12_SetMode(INT8 bConfig, INT8 bClkDiv);

void Hal4D12_SetDMA(INT8 bMode);
//INT8 Hal4D12_GetDMA(void);

//INT16 Hal4D12_ReadInterruptRegister(void);
void Hal4D12_ReadInterruptRegister( INT16 * pInterruptReg);

INT8 Hal4D12_ReadLastTransactionStatus(INT8 bEndp);

//INT8 Hal4D12_ReadEndpointStatus(INT8 bEndp);
void Hal4D12_SetEndpointStatus(INT8 bEndp, INT8 bStalled);

INT8 Hal4D12_SelectEndpoint(INT8 bEndp);

INT8 Hal4D12_ReadEndpoint(INT8 endp, INT8 len, INT8 * buf);
INT8 Hal4D12_ReadEPAtCode(INT8 endp, INT8 len);
INT8 Hal4D12_WriteEndpoint(INT8 endp, INT8 len, INT8 * buf);
INT8 Hal4D12_WriteEPAtCode(INT8 endp, INT8 len, INT8 * buf);

void Hal4D12_ValidateBuffer(INT8 endp);
void Hal4D12_ClearBuffer(INT8 endp);


void Hal4D12_SingleTransmitEP0(INT8 * pData, INT8 len);
void Hal4D12_AcknowledgeSETUP(void);
void Hal4D12_StallEP0(void);

#endif
