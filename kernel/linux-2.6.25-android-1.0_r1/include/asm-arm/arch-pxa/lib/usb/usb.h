#ifndef _USB_H_ 
#define _USB_H_ 


/*************************************************************************/
// basic #defines
/*************************************************************************/
#define MAX_ENDPOINTS      (unsigned char)0x3

#define EP0_TX_FIFO_SIZE   16
#define EP0_RX_FIFO_SIZE   16
#define EP0_PACKET_SIZE    16

#define EP1_TX_FIFO_SIZE   4
#define EP1_RX_FIFO_SIZE   4
#define EP1_PACKET_SIZE    4

#define EP2_TX_FIFO_SIZE   64
#define EP2_RX_FIFO_SIZE   64
#define EP2_PACKET_SIZE    64


#define USB_IDLE           0
#define USB_TRANSMIT       1
#define USB_RECEIVE        2

#define USB_CLASS_CODE_TEST_CLASS_DEVICE                    0xdc
#define USB_SUBCLASS_CODE_TEST_CLASS_D12                    0xA0
#define USB_PROTOCOL_CODE_TEST_CLASS_D12                    0xB0

/*************************************************************************/
// masks
/*************************************************************************/

#define USB_RECIPIENT            (unsigned char)0x1F
#define USB_RECIPIENT_DEVICE     (unsigned char)0x00
#define USB_RECIPIENT_INTERFACE  (unsigned char)0x01
#define USB_RECIPIENT_ENDPOINT   (unsigned char)0x02

#define USB_REQUEST_TYPE_MASK    (unsigned char)0x60
#define USB_STANDARD_REQUEST     (unsigned char)0x00
#define USB_CLASS_REQUEST        (unsigned char)0x20
#define USB_VENDOR_REQUEST       (unsigned char)0x40

#define USB_REQUEST_MASK         (unsigned char)0x0F

#define DEVICE_ADDRESS_MASK      0x7F

/*************************************************************************/
// macros
/*************************************************************************/
#define SWAP(x)   (x)	//((((x) & 0xFF) << 8) | (((x) >> 8) & 0xFF))

#define MSB(x)    (((x) >> 8) & 0xFF)
#define LSB(x)    ((x) & 0xFF)

#define FALSE   0
#define TRUE    (!FALSE)

/*************************************************************************/
// basic typedefs
/*************************************************************************/
typedef unsigned char   UCHAR;
typedef unsigned short  USHORT;
typedef unsigned long   ULONG;
typedef unsigned char   BOOL;

/*************************************************************************/
// structure and union definitions
/*************************************************************************/
typedef union _epp_flags
{
	struct _flags
	{
		unsigned char timer               	: 1;
		unsigned char bus_reset           	: 1;
		unsigned char suspend             	: 1;
		unsigned char setup_packet  	  	: 1;
		unsigned char remote_wakeup		   	: 1;
		unsigned char in_isr		      	: 1;
		unsigned char control_state			: 2;

		unsigned char configuration			: 1;
		unsigned char verbose				: 1;
		unsigned char ep1_rxdone			: 1;
		unsigned char setup_dma				: 2; // V2.3
		unsigned char dma_state      		: 2;
		unsigned char power_down			: 1; // Smart Board
	}__attribute__ ((packed))  bits;
	unsigned short value;
}__attribute__ ((packed))  EPPFLAGS;

typedef struct _device_request
{
	unsigned char bmRequestType;
	unsigned char bRequest;
	unsigned short wValue;
	unsigned short wIndex;
	unsigned short wLength;
}__attribute__ ((packed))  DEVICE_REQUEST;

typedef struct _IO_REQUEST {
	unsigned short	uAddressL;
	unsigned char	bAddressH;
	unsigned short	uSize;
	unsigned char	bCommand;
}__attribute__ ((packed))  IO_REQUEST, *PIO_REQUEST;

#define MAX_CONTROLDATA_SIZE	8

typedef struct _control_xfer
{
	DEVICE_REQUEST DeviceRequest;
	unsigned short wLength;
	unsigned short wCount;
	unsigned char * pData;
	unsigned char dataBuffer[MAX_CONTROLDATA_SIZE];
}__attribute__ ((packed))  CONTROL_XFER;

/*************************************************************************/
// USB utility functions
/*************************************************************************/

/*void fn_usb_isr();

extern void suspend_change(void);
extern void stall_ep0(void);
extern void disconnect_USB(void);
extern void connect_USB(void);
extern void reconnect_USB(void);
extern void init_unconfig(void);
extern void init_config(void);
extern void single_transmit(unsigned char * pData, unsigned char len);
extern void code_transmit(unsigned char code * pRomData, unsigned short len);

extern void control_handler();
extern void check_key_LED(void);
extern void setup_dma();

void dma_start(PIO_REQUEST);

*/

#define IN_TOKEN_DMA 	1
#define OUT_TOKEN_DMA 	0

#define DMA_BUFFER_SIZE		256

#define DMA_IDLE	0
#define DMA_RUNNING	1
#define DMA_PENDING	2

#define SETUP_DMA_REQUEST 		0x0471
#define GET_FIRMWARE_VERSION    0x0472
#define GET_SET_TWAIN_REQUEST   0x0473
#define GET_BUFFER_SIZE		    0x0474

typedef struct _TWAIN_FILEINFO {
	unsigned char	bPage;    // bPage bit 7 - 5 map to uSize bit 18 - 16
	unsigned char	uSizeH;    // uSize bit 15 - 8
	unsigned char	uSizeL;    // uSize bit 7 - 0
}__attribute__ ((packed))  TWAIN_FILEINFO, *PTWAIN_FILEINFO;




////////////////////////////////////////////////////////////////////////////////////////////
//usb initial and connect
////////////////////////////////////////////////////////////////////////////////////////////
void init_usb(void);
void exit_usb(void);

//void connect_usb(void);

void disconnect_USB(void);
void connect_USB(void);
void reconnect_USB(void);



void USB_TEST1(void);


/////////////////////////////////////////////////////////////////////////////////////
//interrupt πÔ¿≥®Áº∆
////////////////////////////////////////////////////////////////////////////////////
void ep0_rxdone(void);



#endif /*_CODEC_H_ */

