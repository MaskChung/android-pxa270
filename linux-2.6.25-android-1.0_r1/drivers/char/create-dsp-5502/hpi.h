#ifndef _HPI_H
#define _HPI_H

#include    "asm/arch/lib/creator_pxa270_addr.h"

void Delay (UI timems);

#define DSP_BASE       (MASTER_ECS3_VIRT+0x0)

// ARM I/O address to CPLD 
#define REG_HPIC	    (*(volatile unsigned short *)(DSP_BASE + 0x00))
#define REG_HPID_AINC	(*(volatile unsigned short *)(DSP_BASE + 0x02))
#define REG_HPIA	    (*(volatile unsigned short *)(DSP_BASE + 0x04))
#define REG_HPID	    (*(volatile unsigned short *)(DSP_BASE + 0x06))
#define REG_SLAVE_S     (*(volatile unsigned short *)(DSP_BASE + 0x10))
#define REG_SLAVE_C     (*(volatile unsigned short *)(DSP_BASE + 0x18))


// Host Commands and DSP ACK
#define STOP_HOST2DSP_CMD           0x00
#define START_HOST2DSP_CMD          0x11
#define UPLOAD_HOST2DSP_CMD         0x22
#define DOWNLOAD_HOST2DSP_CMD       0x33
#define EXPOSURETIME_HOST2DSP_CMD   0x55
#define DOWNLOAD_ACK_HOST2DSP_CMD   0xEE

// DSP ACK State.
#define STOP_DPS_ACK                0x00
#define START_DSP_ACK               0x11    // wait until DSP finish one block data
#define UPLOADING_DSP_ACK           0x22    // 0x22 transfer state. 0x55AA-- finish
#define UPLOAD_END_DSP_ACK          0x55AA  // 0x22 transfer state. 0x55AA-- finish
#define DOWNLOAD_DSP2HOST_CMD       0x33
#define DOWNLOAD_ACK_DSP_ACK        0x44

// Data Buffer.
#define PING_BUFFER_INDEX           1
#define PONG_BUFFER_INDEX           2

#define HOST_TO_DSP_MAILBOX_ADDR    0x1000
#define DSP_TO_HOST_MAILBOX_ADDR    0x1080

#define PING_BUFFER_ADDR            0x1100
#define PONG_BUFFER_ADDR            0X4280


#define HINT_BIT_INDEX              2       // Host Notify DSP when Command ready
#define DSPINT_BIT_INDEX            1       // DSP Notify Host when Command ready

typedef struct MAILBOX {
        U16      wCommand ;                 // Host/DSP command.
        U16      wDataWordLength;           // Data Length of Current Transfer
        U16      wBufferAddress;            // Buffer Pointer
        U16      wACKState;                 // Command ACK from Host/DSP
        U16      wExposureTime ;            // CCM Exposure Time. Range 0x01~0x3F.(7 Bits)
} ST_MAILBOX ;    

#endif // _HPI_H
