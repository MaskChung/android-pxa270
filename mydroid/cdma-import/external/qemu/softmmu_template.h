/*
 *  Software MMU support
 * 
 *  Copyright (c) 2003 Fabrice Bellard
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */
#define DATA_SIZE (1 << SHIFT)

#if DATA_SIZE == 8
#define SUFFIX q
#define USUFFIX q
#define DATA_TYPE uint64_t
#elif DATA_SIZE == 4
#define SUFFIX l
#define USUFFIX l
#define DATA_TYPE uint32_t
#elif DATA_SIZE == 2
#define SUFFIX w
#define USUFFIX uw
#define DATA_TYPE uint16_t
#elif DATA_SIZE == 1
#define SUFFIX b
#define USUFFIX ub
#define DATA_TYPE uint8_t
#else
#error unsupported data size
#endif

#ifdef SOFTMMU_CODE_ACCESS
#define READ_ACCESS_TYPE 2
#define ADDR_READ addr_code
#else
#define READ_ACCESS_TYPE 0
#define ADDR_READ addr_read
#endif

static DATA_TYPE glue(glue(slow_ld, SUFFIX), MMUSUFFIX)(target_ulong addr, 
                                                        int is_user,
                                                        void *retaddr);
static inline DATA_TYPE glue(io_read, SUFFIX)(target_phys_addr_t physaddr, 
                                              target_ulong tlb_addr)
{
    DATA_TYPE res;
    int index;

    index = (tlb_addr >> IO_MEM_SHIFT) & (IO_MEM_NB_ENTRIES - 1);
#if SHIFT <= 2
    res = io_mem_read[index][SHIFT](io_mem_opaque[index], physaddr);
#else
#ifdef TARGET_WORDS_BIGENDIAN
    res = (uint64_t)io_mem_read[index][2](io_mem_opaque[index], physaddr) << 32;
    res |= io_mem_read[index][2](io_mem_opaque[index], physaddr + 4);
#else
    res = io_mem_read[index][2](io_mem_opaque[index], physaddr);
    res |= (uint64_t)io_mem_read[index][2](io_mem_opaque[index], physaddr + 4) << 32;
#endif
#endif /* SHIFT > 2 */
#ifdef USE_KQEMU
    env->last_io_time = cpu_get_time_fast();
#endif
    return res;
}

/* handle all cases except unaligned access which span two pages */
DATA_TYPE REGPARM(1) glue(glue(__ld, SUFFIX), MMUSUFFIX)(target_ulong addr,
                                                         int is_user)
{
    DATA_TYPE res;
    int index;
    target_ulong tlb_addr;
    target_phys_addr_t physaddr;
    void *retaddr;
    
    /* test if there is match for unaligned or IO access */
    /* XXX: could done more in memory macro in a non portable way */
    index = (addr >> TARGET_PAGE_BITS) & (CPU_TLB_SIZE - 1);
 redo:
    tlb_addr = env->tlb_table[is_user][index].ADDR_READ;
    if ((addr & TARGET_PAGE_MASK) == (tlb_addr & (TARGET_PAGE_MASK | TLB_INVALID_MASK))) {
        physaddr = addr + env->tlb_table[is_user][index].addend;
        if (tlb_addr & ~TARGET_PAGE_MASK) {
            /* IO access */
            if ((addr & (DATA_SIZE - 1)) != 0)
            {
                retaddr = GETPC();
#ifdef IO_ALIGNED_ONLY
                do_unaligned_access(addr, READ_ACCESS_TYPE, is_user, retaddr);
#endif
                res = glue(glue(slow_ld, SUFFIX), MMUSUFFIX)(addr, is_user, retaddr);
            }
            else
                res = glue(io_read, SUFFIX)(physaddr, tlb_addr);
        } 
        else if (((addr & ~TARGET_PAGE_MASK) + DATA_SIZE - 1) >= TARGET_PAGE_SIZE) {
            /* slow unaligned access (it spans two pages or IO) */
            retaddr = GETPC();
#ifdef ALIGNED_ONLY
            do_unaligned_access(addr, READ_ACCESS_TYPE, is_user, retaddr);
#endif
            res = glue(glue(slow_ld, SUFFIX), MMUSUFFIX)(addr, 
                                                         is_user, retaddr);
        } else {
            /* unaligned/aligned access in the same page */
#ifdef ALIGNED_ONLY
            if ((addr & (DATA_SIZE - 1)) != 0) {
                retaddr = GETPC();
                do_unaligned_access(addr, READ_ACCESS_TYPE, is_user, retaddr);
            }
#endif
            res = glue(glue(ld, USUFFIX), _raw)((uint8_t *)(long)physaddr);
        }
    } else {
        /* the page is not in the TLB : fill it */
        retaddr = GETPC();
#ifdef zzALIGNED_ONLY
        if ((addr & (DATA_SIZE - 1)) != 0)
            do_unaligned_access(addr, READ_ACCESS_TYPE, is_user, retaddr);
#endif
        tlb_fill(addr, READ_ACCESS_TYPE, is_user, retaddr);
        goto redo;
    }
    return res;
}

/* handle all unaligned cases */
static DATA_TYPE glue(glue(slow_ld, SUFFIX), MMUSUFFIX)(target_ulong addr, 
                                                        int is_user,
                                                        void *retaddr)
{
    DATA_TYPE res, res1, res2;
    int index, shift;
    target_phys_addr_t physaddr;
    target_ulong tlb_addr, addr1, addr2;

    index = (addr >> TARGET_PAGE_BITS) & (CPU_TLB_SIZE - 1);
 redo:
    tlb_addr = env->tlb_table[is_user][index].ADDR_READ;
    if ((addr & TARGET_PAGE_MASK) == (tlb_addr & (TARGET_PAGE_MASK | TLB_INVALID_MASK))) {
        physaddr = addr + env->tlb_table[is_user][index].addend;
        if (tlb_addr & ~TARGET_PAGE_MASK) {
            /* IO access */
            if ((addr & (DATA_SIZE - 1)) != 0)
                goto do_unaligned_access;
            res = glue(io_read, SUFFIX)(physaddr, tlb_addr);
        } else if (((addr & ~TARGET_PAGE_MASK) + DATA_SIZE - 1) >= TARGET_PAGE_SIZE) {
        do_unaligned_access:
            /* slow unaligned access (it spans two pages) */
            addr1 = addr & ~(DATA_SIZE - 1);
            addr2 = addr1 + DATA_SIZE;
            res1 = glue(glue(slow_ld, SUFFIX), MMUSUFFIX)(addr1, 
                                                          is_user, retaddr);
            res2 = glue(glue(slow_ld, SUFFIX), MMUSUFFIX)(addr2, 
                                                          is_user, retaddr);
            shift = (addr & (DATA_SIZE - 1)) * 8;
#ifdef TARGET_WORDS_BIGENDIAN
            res = (res1 << shift) | (res2 >> ((DATA_SIZE * 8) - shift));
#else
            res = (res1 >> shift) | (res2 << ((DATA_SIZE * 8) - shift));
#endif
            res = (DATA_TYPE)res;
        } else {
            /* unaligned/aligned access in the same page */
            res = glue(glue(ld, USUFFIX), _raw)((uint8_t *)(long)physaddr);
        }
    } else {
        /* the page is not in the TLB : fill it */
        tlb_fill(addr, READ_ACCESS_TYPE, is_user, retaddr);
        goto redo;
    }
    return res;
}

#ifndef SOFTMMU_CODE_ACCESS

static void glue(glue(slow_st, SUFFIX), MMUSUFFIX)(target_ulong addr, 
                                                   DATA_TYPE val, 
                                                   int is_user,
                                                   void *retaddr);

static inline void glue(io_write, SUFFIX)(target_phys_addr_t physaddr, 
                                          DATA_TYPE val,
                                          target_ulong tlb_addr,
                                          void *retaddr)
{
    int index;

    index = (tlb_addr >> IO_MEM_SHIFT) & (IO_MEM_NB_ENTRIES - 1);
    env->mem_write_vaddr = tlb_addr;
    env->mem_write_pc = (unsigned long)retaddr;
#if SHIFT <= 2
    io_mem_write[index][SHIFT](io_mem_opaque[index], physaddr, val);
#else
#ifdef TARGET_WORDS_BIGENDIAN
    io_mem_write[index][2](io_mem_opaque[index], physaddr, val >> 32);
    io_mem_write[index][2](io_mem_opaque[index], physaddr + 4, val);
#else
    io_mem_write[index][2](io_mem_opaque[index], physaddr, val);
    io_mem_write[index][2](io_mem_opaque[index], physaddr + 4, val >> 32);
#endif
#endif /* SHIFT > 2 */
#ifdef USE_KQEMU
    env->last_io_time = cpu_get_time_fast();
#endif
}

void REGPARM(2) glue(glue(__st, SUFFIX), MMUSUFFIX)(target_ulong addr, 
                                                    DATA_TYPE val,
                                                    int is_user)
{
    target_phys_addr_t physaddr;
    target_ulong tlb_addr;
    void *retaddr;
    int index;
   
    env->mem_write_vaddr = addr; 
    index = (addr >> TARGET_PAGE_BITS) & (CPU_TLB_SIZE - 1);
 redo:
    tlb_addr = env->tlb_table[is_user][index].addr_write;
    if ((addr & TARGET_PAGE_MASK) == (tlb_addr & (TARGET_PAGE_MASK | TLB_INVALID_MASK))) {
        physaddr = addr + env->tlb_table[is_user][index].addend;
        if (tlb_addr & ~TARGET_PAGE_MASK) {
            /* IO access */
            retaddr = GETPC();
            if ((addr & (DATA_SIZE - 1)) != 0)
            {
#ifdef IO_ALIGNED_ONLY
                do_unaligned_acess(addr, 1, is_user, retaddr);
#endif
                glue(glue(slow_st, SUFFIX), MMUSUFFIX)(addr, val, is_user, retaddr);
            }
            else
                glue(io_write, SUFFIX)(physaddr, val, tlb_addr, retaddr);
        }
        else if (((addr & ~TARGET_PAGE_MASK) + DATA_SIZE - 1) >= TARGET_PAGE_SIZE) {
            retaddr = GETPC();
#ifdef ALIGNED_ONLY
            do_unaligned_access(addr, 1, is_user, retaddr);
#endif
            glue(glue(slow_st, SUFFIX), MMUSUFFIX)(addr, val, 
                                                   is_user, retaddr);
        } else {
            /* aligned/unaligned access in the same page */
#ifdef ALIGNED_ONLY
            if ((addr & (DATA_SIZE - 1)) != 0) {
                retaddr = GETPC();
                do_unaligned_access(addr, 1, is_user, retaddr);
            }
#endif
            glue(glue(st, SUFFIX), _raw)((uint8_t *)(long)physaddr, val);
        }
    } else {
        /* the page is not in the TLB : fill it */
        retaddr = GETPC();
#ifdef ALIGNED_ONLY
        if ((addr & (DATA_SIZE - 1)) != 0)
            do_unaligned_access(addr, 1, is_user, retaddr);
#endif
        tlb_fill(addr, 1, is_user, retaddr);
        goto redo;
    }
}

/* handles all unaligned cases */
static void glue(glue(slow_st, SUFFIX), MMUSUFFIX)(target_ulong addr, 
                                                   DATA_TYPE val,
                                                   int is_user,
                                                   void *retaddr)
{
    target_phys_addr_t physaddr;
    target_ulong tlb_addr;
    int index, i;

    index = (addr >> TARGET_PAGE_BITS) & (CPU_TLB_SIZE - 1);
 redo:
    tlb_addr = env->tlb_table[is_user][index].addr_write;
    if ((addr & TARGET_PAGE_MASK) == (tlb_addr & (TARGET_PAGE_MASK | TLB_INVALID_MASK))) {
        physaddr = addr + env->tlb_table[is_user][index].addend;
        if (tlb_addr & ~TARGET_PAGE_MASK) {
            /* IO access */
            if ((addr & (DATA_SIZE - 1)) != 0)
                goto do_unaligned_access;
            glue(io_write, SUFFIX)(physaddr, val, tlb_addr, retaddr);
        } else if (((addr & ~TARGET_PAGE_MASK) + DATA_SIZE - 1) >= TARGET_PAGE_SIZE) {
        do_unaligned_access:
            /* XXX: not efficient, but simple */
            for(i = 0;i < DATA_SIZE; i++) {
#ifdef TARGET_WORDS_BIGENDIAN
                glue(slow_stb, MMUSUFFIX)(addr + i, val >> (((DATA_SIZE - 1) * 8) - (i * 8)), 
                                          is_user, retaddr);
#else
                glue(slow_stb, MMUSUFFIX)(addr + i, val >> (i * 8), 
                                          is_user, retaddr);
#endif
            }
        } else {
            /* aligned/unaligned access in the same page */
            glue(glue(st, SUFFIX), _raw)((uint8_t *)(long)physaddr, val);
        }
    } else {
        /* the page is not in the TLB : fill it */
        tlb_fill(addr, 1, is_user, retaddr);
        goto redo;
    }
}

#endif /* !defined(SOFTMMU_CODE_ACCESS) */

#undef READ_ACCESS_TYPE
#undef SHIFT
#undef DATA_TYPE
#undef SUFFIX
#undef USUFFIX
#undef DATA_SIZE
#undef ADDR_READ
