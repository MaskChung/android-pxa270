/*
 *  linux/include/asm-arm/arch-pxa/create-regs.h
 *

 */

#ifndef __CREATE_REGS_H
#define __CREATE_REGS_H

#include <linux/config.h>
#include <asm/arch/pxa-regs.h>


#define OSSR_M4		(1 << 4)	/* Match status channel 4 */
#define OSSR_M5		(1 << 5)	/* Match status channel 5 */
#define OSSR_M6		(1 << 6)	/* Match status channel 6 */
#define OSSR_M7		(1 << 7)	/* Match status channel 7 */
#define OSSR_M8		(1 << 8)	/* Match status channel 8 */
#define OSSR_M9		(1 << 9)	/* Match status channel 9 */
#define OSSR_M10	(1 << 10)   /* Match status channel 10*/
#define OSSR_M11	(1 << 11)	/* Match status channel 11*/

#define OIER_E4		(1 << 4)	/* Interrupt enable channel 4 */
#define OIER_E5		(1 << 5)	/* Interrupt enable channel 5 */
#define OIER_E6		(1 << 6)	/* Interrupt enable channel 6 */
#define OIER_E7		(1 << 7)	/* Interrupt enable channel 7 */
#define OIER_E8		(1 << 8)	/* Interrupt enable channel 8 */
#define OIER_E9		(1 << 9)	/* Interrupt enable channel 9 */
#define OIER_E10	(1 << 10)   /* Interrupt enable channel 10*/
#define OIER_E11	(1 << 11)   /* Interrupt enable channel 11*/





#endif // __CREATE_REGS_H