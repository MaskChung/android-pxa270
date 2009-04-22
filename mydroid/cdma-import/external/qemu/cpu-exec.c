/*
 *  i386 emulator main execution loop
 *
 *  Copyright (c) 2003-2005 Fabrice Bellard
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
#include "config.h"
#include "exec.h"
#include "disas.h"

#ifdef _WIN32
#define WIN32_LEAN_AND_MEAN
#include <windows.h>
#endif

#ifndef _WIN32
#include <unistd.h>
#endif

extern int  qemu_milli_needed;
extern int  qemu_milli_check(void);
extern void qemu_check_interrupts(void);

extern int  qemu_cpu_delay;
extern int  qemu_cpu_delay_count;

#if !defined(CONFIG_SOFTMMU)
#undef EAX
#undef ECX
#undef EDX
#undef EBX
#undef ESP
#undef EBP
#undef ESI
#undef EDI
#undef EIP
#include <signal.h>
#include <sys/ucontext.h>
#endif

int tb_invalidated_flag;

//#define DEBUG_EXEC
//#define DEBUG_SIGNAL

#if defined(TARGET_ARM) || defined(TARGET_SPARC)
/* XXX: unify with i386 target */
void cpu_loop_exit(void)
{
    longjmp(env->jmp_env, 1);
}
#endif
#if !(defined(TARGET_SPARC) || defined(TARGET_SH4))
#define reg_T2
#endif

/* exit the current TB from a signal handler. The host registers are
   restored in a state compatible with the CPU emulator
 */
void cpu_resume_from_signal(CPUState *env1, void *puc)
{
#if !defined(CONFIG_SOFTMMU)
    struct ucontext *uc = puc;
#endif

    env = env1;

    /* XXX: restore cpu registers saved in host registers */

#if !defined(CONFIG_SOFTMMU)
    if (puc) {
        /* XXX: use siglongjmp ? */
        sigprocmask(SIG_SETMASK, &uc->uc_sigmask, NULL);
    }
#endif
    longjmp(env->jmp_env, 1);
}


static TranslationBlock *tb_find_slow(target_ulong pc,
                                      target_ulong cs_base,
                                      unsigned int flags)
{
    TranslationBlock *tb, **ptb1;
    int code_gen_size;
    unsigned int h;
    target_ulong phys_pc, phys_page1, phys_page2, virt_page2;
    uint8_t *tc_ptr;

    spin_lock(&tb_lock);

    tb_invalidated_flag = 0;

    regs_to_env(); /* XXX: do it just before cpu_gen_code() */

    /* find translated block using physical mappings */
    phys_pc = get_phys_addr_code(env, pc);
    phys_page1 = phys_pc & TARGET_PAGE_MASK;
    phys_page2 = -1;
    h = tb_phys_hash_func(phys_pc);
    ptb1 = &tb_phys_hash[h];
    for(;;) {
        tb = *ptb1;
        if (!tb)
            goto not_found;
        if (tb->pc == pc &&
            tb->page_addr[0] == phys_page1 &&
            tb->cs_base == cs_base &&
            tb->flags == flags) {
            /* check next page if needed */
            if (tb->page_addr[1] != -1) {
                virt_page2 = (pc & TARGET_PAGE_MASK) +
                    TARGET_PAGE_SIZE;
                phys_page2 = get_phys_addr_code(env, virt_page2);
                if (tb->page_addr[1] == phys_page2)
                    goto found;
            } else {
                goto found;
            }
        }
        ptb1 = &tb->phys_hash_next;
    }
 not_found:
    /* if no translated code available, then translate it now */
    tb = tb_alloc(pc);
    if (!tb) {
        /* flush must be done */
        tb_flush(env);
        /* cannot fail at this point */
        tb = tb_alloc(pc);
        /* don't forget to invalidate previous TB info */
        tb_invalidated_flag = 1;
    }
    tc_ptr = code_gen_ptr;
    tb->tc_ptr = tc_ptr;
    tb->cs_base = cs_base;
    tb->flags = flags;
#ifdef CONFIG_TRACE
    tb->bb_rec = NULL;
    tb->prev_time = 0;
#endif
    cpu_gen_code(env, tb, CODE_GEN_MAX_SIZE, &code_gen_size);
    code_gen_ptr = (void *)(((unsigned long)code_gen_ptr + code_gen_size + CODE_GEN_ALIGN - 1) & ~(CODE_GEN_ALIGN - 1));

    /* check next page if needed */
    virt_page2 = (pc + tb->size - 1) & TARGET_PAGE_MASK;
    phys_page2 = -1;
    if ((pc & TARGET_PAGE_MASK) != virt_page2) {
        phys_page2 = get_phys_addr_code(env, virt_page2);
    }
    tb_link_phys(tb, phys_pc, phys_page2);

 found:
    /* we add the TB in the virtual pc hash table */
    env->tb_jmp_cache[tb_jmp_cache_hash_func(pc)] = tb;
    spin_unlock(&tb_lock);
    return tb;
}

static inline TranslationBlock *tb_find_fast(void)
{
    TranslationBlock *tb;
    target_ulong cs_base, pc;
    unsigned int flags;

    /* we record a subset of the CPU state. It will
       always be the same before a given translated block
       is executed. */
#if defined(TARGET_I386)
    flags = env->hflags;
    flags |= (env->eflags & (IOPL_MASK | TF_MASK | VM_MASK));
    cs_base = env->segs[R_CS].base;
    pc = cs_base + env->eip;
#elif defined(TARGET_ARM)
    flags = env->thumb | (env->vfp.vec_len << 1)
            | (env->vfp.vec_stride << 4);
    if ((env->uncached_cpsr & CPSR_M) != ARM_CPU_MODE_USR)
        flags |= (1 << 6);
    if (env->vfp.xregs[ARM_VFP_FPEXC] & (1 << 30))
        flags |= (1 << 7);
    cs_base = 0;
    pc = env->regs[15];
#elif defined(TARGET_SPARC)
#ifdef TARGET_SPARC64
    // Combined FPU enable bits . PRIV . DMMU enabled . IMMU enabled
    flags = (((env->pstate & PS_PEF) >> 1) | ((env->fprs & FPRS_FEF) << 2))
        | (env->pstate & PS_PRIV) | ((env->lsu & (DMMU_E | IMMU_E)) >> 2);
#else
    // FPU enable . MMU enabled . MMU no-fault . Supervisor
    flags = (env->psref << 3) | ((env->mmuregs[0] & (MMU_E | MMU_NF)) << 1)
        | env->psrs;
#endif
    cs_base = env->npc;
    pc = env->pc;
#elif defined(TARGET_PPC)
    flags = (msr_pr << MSR_PR) | (msr_fp << MSR_FP) |
        (msr_se << MSR_SE) | (msr_le << MSR_LE);
    cs_base = 0;
    pc = env->nip;
#elif defined(TARGET_MIPS)
    flags = env->hflags & (MIPS_HFLAG_TMASK | MIPS_HFLAG_BMASK);
    cs_base = 0;
    pc = env->PC;
#elif defined(TARGET_SH4)
    flags = env->sr & (SR_MD | SR_RB);
    cs_base = 0;         /* XXXXX */
    pc = env->pc;
#else
#error unsupported CPU
#endif
    tb = env->tb_jmp_cache[tb_jmp_cache_hash_func(pc)];
    if (__builtin_expect(!tb || tb->pc != pc || tb->cs_base != cs_base ||
                         tb->flags != flags, 0)) {
        tb = tb_find_slow(pc, cs_base, flags);
        /* Note: we do it here to avoid a gcc bug on Mac OS X when
           doing it in tb_find_slow */
        if (tb_invalidated_flag) {
            /* as some TB could have been invalidated because
               of memory exceptions while generating the code, we
               must recompute the hash index here */
            T0 = 0;
        }
    }
    return tb;
}


/* main execution loop */

int cpu_exec(CPUState *env1)
{
    int saved_T0, saved_T1;
#if defined(reg_T2)
    int saved_T2;
#endif
    CPUState *saved_env;
#if defined(TARGET_I386)
#ifdef reg_EAX
    int saved_EAX;
#endif
#ifdef reg_ECX
    int saved_ECX;
#endif
#ifdef reg_EDX
    int saved_EDX;
#endif
#ifdef reg_EBX
    int saved_EBX;
#endif
#ifdef reg_ESP
    int saved_ESP;
#endif
#ifdef reg_EBP
    int saved_EBP;
#endif
#ifdef reg_ESI
    int saved_ESI;
#endif
#ifdef reg_EDI
    int saved_EDI;
#endif
#elif defined(TARGET_SPARC)
#if defined(reg_REGWPTR)
    uint32_t *saved_regwptr;
#endif
#endif
#if defined(__sparc__) && !defined(HOST_SOLARIS)
    int saved_i7, tmp_T0;
#endif
    int ret, interrupt_request;
    void (*gen_func)(void);
    TranslationBlock *tb;
    uint8_t *tc_ptr;

#if defined(TARGET_I386)
    /* handle exit of HALTED state */
    if (env1->hflags & HF_HALTED_MASK) {
        /* disable halt condition */
        if ((env1->interrupt_request & CPU_INTERRUPT_HARD) &&
            (env1->eflags & IF_MASK)) {
            env1->hflags &= ~HF_HALTED_MASK;
        } else {
            return EXCP_HALTED;
        }
    }
#elif defined(TARGET_PPC)
    if (env1->halted) {
        if (env1->msr[MSR_EE] &&
            (env1->interrupt_request &
             (CPU_INTERRUPT_HARD | CPU_INTERRUPT_TIMER))) {
            env1->halted = 0;
        } else {
            return EXCP_HALTED;
        }
    }
#elif defined(TARGET_SPARC)
    if (env1->halted) {
        if ((env1->interrupt_request & CPU_INTERRUPT_HARD) &&
            (env1->psret != 0)) {
            env1->halted = 0;
        } else {
            return EXCP_HALTED;
        }
    }
#elif defined(TARGET_ARM)
    if (env1->halted) {
        /* An interrupt wakes the CPU even if the I and F CPSR bits are
           set.  */
        if (env1->interrupt_request
            & (CPU_INTERRUPT_FIQ | CPU_INTERRUPT_HARD)) {
            env1->halted = 0;
        } else {
            return EXCP_HALTED;
        }
    }
#elif defined(TARGET_MIPS)
    if (env1->halted) {
        if (env1->interrupt_request &
            (CPU_INTERRUPT_HARD | CPU_INTERRUPT_TIMER)) {
            env1->halted = 0;
        } else {
            return EXCP_HALTED;
        }
    }
#endif

    cpu_single_env = env1;

    /* first we save global registers */
    saved_env = env;
    env = env1;
    saved_T0 = T0;
    saved_T1 = T1;
#if defined(reg_T2)
    saved_T2 = T2;
#endif
#if defined(__sparc__) && !defined(HOST_SOLARIS)
    /* we also save i7 because longjmp may not restore it */
    asm volatile ("mov %%i7, %0" : "=r" (saved_i7));
#endif

#if defined(TARGET_I386)
#ifdef reg_EAX
    saved_EAX = EAX;
#endif
#ifdef reg_ECX
    saved_ECX = ECX;
#endif
#ifdef reg_EDX
    saved_EDX = EDX;
#endif
#ifdef reg_EBX
    saved_EBX = EBX;
#endif
#ifdef reg_ESP
    saved_ESP = ESP;
#endif
#ifdef reg_EBP
    saved_EBP = EBP;
#endif
#ifdef reg_ESI
    saved_ESI = ESI;
#endif
#ifdef reg_EDI
    saved_EDI = EDI;
#endif

    env_to_regs();
    /* put eflags in CPU temporary format */
    CC_SRC = env->eflags & (CC_O | CC_S | CC_Z | CC_A | CC_P | CC_C);
    DF = 1 - (2 * ((env->eflags >> 10) & 1));
    CC_OP = CC_OP_EFLAGS;
    env->eflags &= ~(DF_MASK | CC_O | CC_S | CC_Z | CC_A | CC_P | CC_C);
#elif defined(TARGET_ARM)
#elif defined(TARGET_SPARC)
#if defined(reg_REGWPTR)
    saved_regwptr = REGWPTR;
#endif
#elif defined(TARGET_PPC)
#elif defined(TARGET_MIPS)
#elif defined(TARGET_SH4)
    /* XXXXX */
#else
#error unsupported target CPU
#endif
    env->exception_index = -1;

    /* prepare setjmp context for exception handling */
    for(;;) {
        if (setjmp(env->jmp_env) == 0) {
            env->current_tb = NULL;
            /* if an exception is pending, we execute it here */
            if (env->exception_index >= 0) {
                if (env->exception_index >= EXCP_INTERRUPT) {
                    /* exit request from the cpu execution loop */
                    ret = env->exception_index;
                    break;
                } else if (env->user_mode_only) {
                    /* if user mode only, we simulate a fake exception
                       which will be hanlded outside the cpu execution
                       loop */
#if defined(TARGET_I386)
                    do_interrupt_user(env->exception_index,
                                      env->exception_is_int,
                                      env->error_code,
                                      env->exception_next_eip);
#endif
                    ret = env->exception_index;
                    break;
                } else {
#if defined(TARGET_I386)
                    /* simulate a real cpu exception. On i386, it can
                       trigger new exceptions, but we do not handle
                       double or triple faults yet. */
                    do_interrupt(env->exception_index,
                                 env->exception_is_int,
                                 env->error_code,
                                 env->exception_next_eip, 0);
#elif defined(TARGET_PPC)
                    do_interrupt(env);
#elif defined(TARGET_MIPS)
                    do_interrupt(env);
#elif defined(TARGET_SPARC)
                    do_interrupt(env->exception_index);
#elif defined(TARGET_ARM)
                    do_interrupt(env);
#elif defined(TARGET_SH4)
		    do_interrupt(env);
#endif
                }
                env->exception_index = -1;
            }
#ifdef USE_KQEMU
            if (kqemu_is_ok(env) && env->interrupt_request == 0) {
                int ret;
                env->eflags = env->eflags | cc_table[CC_OP].compute_all() | (DF & DF_MASK);
                ret = kqemu_cpu_exec(env);
                /* put eflags in CPU temporary format */
                CC_SRC = env->eflags & (CC_O | CC_S | CC_Z | CC_A | CC_P | CC_C);
                DF = 1 - (2 * ((env->eflags >> 10) & 1));
                CC_OP = CC_OP_EFLAGS;
                env->eflags &= ~(DF_MASK | CC_O | CC_S | CC_Z | CC_A | CC_P | CC_C);
                if (ret == 1) {
                    /* exception */
                    longjmp(env->jmp_env, 1);
                } else if (ret == 2) {
                    /* softmmu execution needed */
                } else {
                    if (env->interrupt_request != 0) {
                        /* hardware interrupt will be executed just after */
                    } else {
                        /* otherwise, we restart */
                        longjmp(env->jmp_env, 1);
                    }
                }
            }
#endif

            T0 = 0; /* force lookup of first TB */
            for(;;) {
#if defined(__sparc__) && !defined(HOST_SOLARIS)
                /* g1 can be modified by some libc? functions */
                tmp_T0 = T0;
#endif
                interrupt_request = env->interrupt_request;
                if (__builtin_expect(interrupt_request, 0)) {
#if defined(TARGET_I386)
                    /* if hardware interrupt pending, we execute it */
                    if ((interrupt_request & CPU_INTERRUPT_HARD) &&
                        (env->eflags & IF_MASK) &&
                        !(env->hflags & HF_INHIBIT_IRQ_MASK)) {
                        int intno;
                        env->interrupt_request &= ~CPU_INTERRUPT_HARD;
                        intno = cpu_get_pic_interrupt(env);
                        if (loglevel & CPU_LOG_TB_IN_ASM) {
                            fprintf(logfile, "Servicing hardware INT=0x%02x\n", intno);
                        }
                        do_interrupt(intno, 0, 0, 0, 1);
                        /* ensure that no TB jump will be modified as
                           the program flow was changed */
#if defined(__sparc__) && !defined(HOST_SOLARIS)
                        tmp_T0 = 0;
#else
                        T0 = 0;
#endif
                    }
#elif defined(TARGET_PPC)
#if 0
                    if ((interrupt_request & CPU_INTERRUPT_RESET)) {
                        cpu_ppc_reset(env);
                    }
#endif
                    if (msr_ee != 0) {
                        if ((interrupt_request & CPU_INTERRUPT_HARD)) {
			    /* Raise it */
			    env->exception_index = EXCP_EXTERNAL;
			    env->error_code = 0;
                            do_interrupt(env);
                            env->interrupt_request &= ~CPU_INTERRUPT_HARD;
#if defined(__sparc__) && !defined(HOST_SOLARIS)
                            tmp_T0 = 0;
#else
                            T0 = 0;
#endif
                        } else if ((interrupt_request & CPU_INTERRUPT_TIMER)) {
                            /* Raise it */
                            env->exception_index = EXCP_DECR;
                            env->error_code = 0;
                            do_interrupt(env);
                            env->interrupt_request &= ~CPU_INTERRUPT_TIMER;
#if defined(__sparc__) && !defined(HOST_SOLARIS)
                            tmp_T0 = 0;
#else
                            T0 = 0;
#endif
                        }
                    }
#elif defined(TARGET_MIPS)
                    if ((interrupt_request & CPU_INTERRUPT_HARD) &&
                        (env->CP0_Status & (1 << CP0St_IE)) &&
                        (env->CP0_Status & env->CP0_Cause & 0x0000FF00) &&
                        !(env->hflags & MIPS_HFLAG_EXL) &&
                        !(env->hflags & MIPS_HFLAG_ERL) &&
                        !(env->hflags & MIPS_HFLAG_DM)) {
                        /* Raise it */
                        env->exception_index = EXCP_EXT_INTERRUPT;
                        env->error_code = 0;
                        do_interrupt(env);
                        env->interrupt_request &= ~CPU_INTERRUPT_HARD;
#if defined(__sparc__) && !defined(HOST_SOLARIS)
                        tmp_T0 = 0;
#else
                        T0 = 0;
#endif
                    }
#elif defined(TARGET_SPARC)
                    if ((interrupt_request & CPU_INTERRUPT_HARD) &&
			(env->psret != 0)) {
			int pil = env->interrupt_index & 15;
			int type = env->interrupt_index & 0xf0;

			if (((type == TT_EXTINT) &&
			     (pil == 15 || pil > env->psrpil)) ||
			    type != TT_EXTINT) {
			    env->interrupt_request &= ~CPU_INTERRUPT_HARD;
			    do_interrupt(env->interrupt_index);
			    env->interrupt_index = 0;
#if defined(__sparc__) && !defined(HOST_SOLARIS)
                            tmp_T0 = 0;
#else
                            T0 = 0;
#endif
			}
		    } else if (interrupt_request & CPU_INTERRUPT_TIMER) {
			//do_interrupt(0, 0, 0, 0, 0);
			env->interrupt_request &= ~CPU_INTERRUPT_TIMER;
		    } else if (interrupt_request & CPU_INTERRUPT_HALT) {
                        env1->halted = 1;
                        return EXCP_HALTED;
                    }
#elif defined(TARGET_ARM)
                    if (interrupt_request & CPU_INTERRUPT_FIQ
                        && !(env->uncached_cpsr & CPSR_F)) {
                        env->exception_index = EXCP_FIQ;
                        do_interrupt(env);
                    }
                    if (interrupt_request & CPU_INTERRUPT_HARD
                        && !(env->uncached_cpsr & CPSR_I)) {
                        env->exception_index = EXCP_IRQ;
                        do_interrupt(env);
                    }
#elif defined(TARGET_SH4)
		    /* XXXXX */
#endif
                   /* Don't use the cached interupt_request value,
                      do_interrupt may have updated the EXITTB flag. */
                    if (env->interrupt_request & CPU_INTERRUPT_EXITTB) {
                        env->interrupt_request &= ~CPU_INTERRUPT_EXITTB;
                        /* ensure that no TB jump will be modified as
                           the program flow was changed */
#if defined(__sparc__) && !defined(HOST_SOLARIS)
                        tmp_T0 = 0;
#else
                        T0 = 0;
#endif
                    }
                    if (interrupt_request & CPU_INTERRUPT_EXIT) {
                        env->interrupt_request &= ~CPU_INTERRUPT_EXIT;
                        env->exception_index = EXCP_INTERRUPT;
                        cpu_loop_exit();
                    }
                }
#ifdef DEBUG_EXEC
                if ((loglevel & CPU_LOG_TB_CPU)) {
#if defined(TARGET_I386)
                    /* restore flags in standard format */
#ifdef reg_EAX
                    env->regs[R_EAX] = EAX;
#endif
#ifdef reg_EBX
                    env->regs[R_EBX] = EBX;
#endif
#ifdef reg_ECX
                    env->regs[R_ECX] = ECX;
#endif
#ifdef reg_EDX
                    env->regs[R_EDX] = EDX;
#endif
#ifdef reg_ESI
                    env->regs[R_ESI] = ESI;
#endif
#ifdef reg_EDI
                    env->regs[R_EDI] = EDI;
#endif
#ifdef reg_EBP
                    env->regs[R_EBP] = EBP;
#endif
#ifdef reg_ESP
                    env->regs[R_ESP] = ESP;
#endif
                    env->eflags = env->eflags | cc_table[CC_OP].compute_all() | (DF & DF_MASK);
                    cpu_dump_state(env, logfile, fprintf, X86_DUMP_CCOP);
                    env->eflags &= ~(DF_MASK | CC_O | CC_S | CC_Z | CC_A | CC_P | CC_C);
#elif defined(TARGET_ARM)
                    cpu_dump_state(env, logfile, fprintf, 0);
#elif defined(TARGET_SPARC)
		    REGWPTR = env->regbase + (env->cwp * 16);
		    env->regwptr = REGWPTR;
                    cpu_dump_state(env, logfile, fprintf, 0);
#elif defined(TARGET_PPC)
                    cpu_dump_state(env, logfile, fprintf, 0);
#elif defined(TARGET_MIPS)
                    cpu_dump_state(env, logfile, fprintf, 0);
#elif defined(TARGET_SH4)
		    cpu_dump_state(env, logfile, fprintf, 0);
#else
#error unsupported target CPU
#endif
                }
#endif
                tb = tb_find_fast();
#ifdef DEBUG_EXEC
                if ((loglevel & CPU_LOG_EXEC)) {
                    fprintf(logfile, "Trace 0x%08lx [" TARGET_FMT_lx "] %s\n",
                            (long)tb->tc_ptr, tb->pc,
                            lookup_symbol(tb->pc));
                }
#endif
#if defined(__sparc__) && !defined(HOST_SOLARIS)
                T0 = tmp_T0;
#endif
                /* see if we can patch the calling TB. When the TB
                   spans two pages, we cannot safely do a direct
                   jump. */
                {
                    if (T0 != 0 &&
#if USE_KQEMU
                        (env->kqemu_enabled != 2) &&
#endif
                        tb->page_addr[1] == -1
#if defined(TARGET_I386) && defined(USE_CODE_COPY)
                    && (tb->cflags & CF_CODE_COPY) ==
                    (((TranslationBlock *)(T0 & ~3))->cflags & CF_CODE_COPY)
#endif
                    ) {
                    spin_lock(&tb_lock);
                    tb_add_jump((TranslationBlock *)(long)(T0 & ~3), T0 & 3, tb);
#if defined(USE_CODE_COPY)
                    /* propagates the FP use info */
                    ((TranslationBlock *)(T0 & ~3))->cflags |=
                        (tb->cflags & CF_FP_USED);
#endif
                    spin_unlock(&tb_lock);
                }
                }
                tc_ptr = tb->tc_ptr;
                env->current_tb = tb;
                /* execute the generated code */
                gen_func = (void *)tc_ptr;
#if defined(__sparc__)
                __asm__ __volatile__("call	%0\n\t"
                                     "mov	%%o7,%%i0"
                                     : /* no outputs */
                                     : "r" (gen_func)
                                     : "i0", "i1", "i2", "i3", "i4", "i5",
                                       "l0", "l1", "l2", "l3", "l4", "l5",
                                       "l6", "l7");
#elif defined(__arm__)
                asm volatile ("mov pc, %0\n\t"
                              ".global exec_loop\n\t"
                              "exec_loop:\n\t"
                              : /* no outputs */
                              : "r" (gen_func)
                              : "r1", "r2", "r3", "r8", "r9", "r10", "r12", "r14");
#elif defined(TARGET_X86_64) && defined(__i386__)
                asm volatile ("push %%ebx\n"
                              "push %%esi\n"
                              "push %%edi\n"
                              "call *%0\n"
                              "pop %%edi\n"
                              "pop %%esi\n"
                              "pop %%ebx\n"
                              : : "r" (gen_func) : "ebx", "esi", "edi");
#elif defined(TARGET_I386) && defined(USE_CODE_COPY)
{
    if (!(tb->cflags & CF_CODE_COPY)) {
        if ((tb->cflags & CF_FP_USED) && env->native_fp_regs) {
            save_native_fp_state(env);
        }
        gen_func();
    } else {
        if ((tb->cflags & CF_FP_USED) && !env->native_fp_regs) {
            restore_native_fp_state(env);
        }
        /* we work with native eflags */
        CC_SRC = cc_table[CC_OP].compute_all();
        CC_OP = CC_OP_EFLAGS;
        asm(".globl exec_loop\n"
            "\n"
            "debug1:\n"
            "    pushl %%ebp\n"
            "    fs movl %10, %9\n"
            "    fs movl %11, %%eax\n"
            "    andl $0x400, %%eax\n"
            "    fs orl %8, %%eax\n"
            "    pushl %%eax\n"
            "    popf\n"
            "    fs movl %%esp, %12\n"
            "    fs movl %0, %%eax\n"
            "    fs movl %1, %%ecx\n"
            "    fs movl %2, %%edx\n"
            "    fs movl %3, %%ebx\n"
            "    fs movl %4, %%esp\n"
            "    fs movl %5, %%ebp\n"
            "    fs movl %6, %%esi\n"
            "    fs movl %7, %%edi\n"
            "    fs jmp *%9\n"
            "exec_loop:\n"
            "    fs movl %%esp, %4\n"
            "    fs movl %12, %%esp\n"
            "    fs movl %%eax, %0\n"
            "    fs movl %%ecx, %1\n"
            "    fs movl %%edx, %2\n"
            "    fs movl %%ebx, %3\n"
            "    fs movl %%ebp, %5\n"
            "    fs movl %%esi, %6\n"
            "    fs movl %%edi, %7\n"
            "    pushf\n"
            "    popl %%eax\n"
            "    movl %%eax, %%ecx\n"
            "    andl $0x400, %%ecx\n"
            "    shrl $9, %%ecx\n"
            "    andl $0x8d5, %%eax\n"
            "    fs movl %%eax, %8\n"
            "    movl $1, %%eax\n"
            "    subl %%ecx, %%eax\n"
            "    fs movl %%eax, %11\n"
            "    fs movl %9, %%ebx\n" /* get T0 value */
            "    popl %%ebp\n"
            :
            : "m" (*(uint8_t *)offsetof(CPUState, regs[0])),
            "m" (*(uint8_t *)offsetof(CPUState, regs[1])),
            "m" (*(uint8_t *)offsetof(CPUState, regs[2])),
            "m" (*(uint8_t *)offsetof(CPUState, regs[3])),
            "m" (*(uint8_t *)offsetof(CPUState, regs[4])),
            "m" (*(uint8_t *)offsetof(CPUState, regs[5])),
            "m" (*(uint8_t *)offsetof(CPUState, regs[6])),
            "m" (*(uint8_t *)offsetof(CPUState, regs[7])),
            "m" (*(uint8_t *)offsetof(CPUState, cc_src)),
            "m" (*(uint8_t *)offsetof(CPUState, tmp0)),
            "a" (gen_func),
            "m" (*(uint8_t *)offsetof(CPUState, df)),
            "m" (*(uint8_t *)offsetof(CPUState, saved_esp))
            : "%ecx", "%edx"
            );
    }
}
#elif defined(__ia64)
		struct fptr {
			void *ip;
			void *gp;
		} fp;

		fp.ip = tc_ptr;
		fp.gp = code_gen_buffer + 2 * (1 << 20);
		(*(void (*)(void)) &fp)();
#else
                gen_func();
#endif
                env->current_tb = NULL;
                /* reset soft MMU for next block (it can currently
                   only be set by a memory fault) */
#if defined(TARGET_I386) && !defined(CONFIG_SOFTMMU)
                if (env->hflags & HF_SOFTMMU_MASK) {
                    env->hflags &= ~HF_SOFTMMU_MASK;
                    /* do not allow linking to another block */
                    T0 = 0;
                }
#endif
#if defined(USE_KQEMU)
#define MIN_CYCLE_BEFORE_SWITCH (100 * 1000)
                if (kqemu_is_ok(env) &&
                    (cpu_get_time_fast() - env->last_io_time) >= MIN_CYCLE_BEFORE_SWITCH) {
                    cpu_loop_exit();
                }
#endif

#if 0 /* digit: implement high-resolution polling */
                if (qemu_milli_needed) {
                    static int  counter;

                    if (++counter == 50) {
                        counter = 0;
                        if (qemu_milli_check())
                            qemu_check_interrupts();
                    }
                }
#endif
/* ANDROID-BEGIN */
                if (qemu_cpu_delay) {
                    if (++qemu_cpu_delay_count >= qemu_cpu_delay) {
                        qemu_cpu_delay_count = 0;
#ifdef _WIN32
                        Sleep(1);
#else
                        usleep(1000);
#endif
                    }
                }
/* ANDROID-END */
            }
        } else {
            env_to_regs();
        }
    } /* for(;;) */


#if defined(TARGET_I386)
#if defined(USE_CODE_COPY)
    if (env->native_fp_regs) {
        save_native_fp_state(env);
    }
#endif
    /* restore flags in standard format */
    env->eflags = env->eflags | cc_table[CC_OP].compute_all() | (DF & DF_MASK);

    /* restore global registers */
#ifdef reg_EAX
    EAX = saved_EAX;
#endif
#ifdef reg_ECX
    ECX = saved_ECX;
#endif
#ifdef reg_EDX
    EDX = saved_EDX;
#endif
#ifdef reg_EBX
    EBX = saved_EBX;
#endif
#ifdef reg_ESP
    ESP = saved_ESP;
#endif
#ifdef reg_EBP
    EBP = saved_EBP;
#endif
#ifdef reg_ESI
    ESI = saved_ESI;
#endif
#ifdef reg_EDI
    EDI = saved_EDI;
#endif
#elif defined(TARGET_ARM)
    /* XXX: Save/restore host fpu exception state?.  */
#elif defined(TARGET_SPARC)
#if defined(reg_REGWPTR)
    REGWPTR = saved_regwptr;
#endif
#elif defined(TARGET_PPC)
#elif defined(TARGET_MIPS)
#elif defined(TARGET_SH4)
    /* XXXXX */
#else
#error unsupported target CPU
#endif
#if defined(__sparc__) && !defined(HOST_SOLARIS)
    asm volatile ("mov %0, %%i7" : : "r" (saved_i7));
#endif
    T0 = saved_T0;
    T1 = saved_T1;
#if defined(reg_T2)
    T2 = saved_T2;
#endif
    env = saved_env;
    /* fail safe : never use cpu_single_env outside cpu_exec() */
    cpu_single_env = NULL;
    return ret;
}

/* must only be called from the generated code as an exception can be
   generated */
void tb_invalidate_page_range(target_ulong start, target_ulong end)
{
    /* XXX: cannot enable it yet because it yields to MMU exception
       where NIP != read address on PowerPC */
#if 0
    target_ulong phys_addr;
    phys_addr = get_phys_addr_code(env, start);
    tb_invalidate_phys_page_range(phys_addr, phys_addr + end - start, 0);
#endif
}

#if defined(TARGET_I386) && defined(CONFIG_USER_ONLY)

void cpu_x86_load_seg(CPUX86State *s, int seg_reg, int selector)
{
    CPUX86State *saved_env;

    saved_env = env;
    env = s;
    if (!(env->cr[0] & CR0_PE_MASK) || (env->eflags & VM_MASK)) {
        selector &= 0xffff;
        cpu_x86_load_seg_cache(env, seg_reg, selector,
                               (selector << 4), 0xffff, 0);
    } else {
        load_seg(seg_reg, selector);
    }
    env = saved_env;
}

void cpu_x86_fsave(CPUX86State *s, uint8_t *ptr, int data32)
{
    CPUX86State *saved_env;

    saved_env = env;
    env = s;

    helper_fsave((target_ulong)ptr, data32);

    env = saved_env;
}

void cpu_x86_frstor(CPUX86State *s, uint8_t *ptr, int data32)
{
    CPUX86State *saved_env;

    saved_env = env;
    env = s;

    helper_frstor((target_ulong)ptr, data32);

    env = saved_env;
}

#endif /* TARGET_I386 */

#if !defined(CONFIG_SOFTMMU)

#if defined(TARGET_I386)

/* 'pc' is the host PC at which the exception was raised. 'address' is
   the effective address of the memory exception. 'is_write' is 1 if a
   write caused the exception and otherwise 0'. 'old_set' is the
   signal set which should be restored */
static inline int handle_cpu_signal(unsigned long pc, unsigned long address,
                                    int is_write, sigset_t *old_set,
                                    void *puc)
{
    TranslationBlock *tb;
    int ret;

    if (cpu_single_env)
        env = cpu_single_env; /* XXX: find a correct solution for multithread */
#if defined(DEBUG_SIGNAL)
    qemu_printf("qemu: SIGSEGV pc=0x%08lx address=%08lx w=%d oldset=0x%08lx\n",
                pc, address, is_write, *(unsigned long *)old_set);
#endif
    /* XXX: locking issue */
    if (is_write && page_unprotect(h2g(address), pc, puc)) {
        return 1;
    }

    /* see if it is an MMU fault */
    ret = cpu_x86_handle_mmu_fault(env, address, is_write,
                                   ((env->hflags & HF_CPL_MASK) == 3), 0);
    if (ret < 0)
        return 0; /* not an MMU fault */
    if (ret == 0)
        return 1; /* the MMU fault was handled without causing real CPU fault */
    /* now we have a real cpu fault */
    tb = tb_find_pc(pc);
    if (tb) {
        /* the PC is inside the translated code. It means that we have
           a virtual CPU fault */
        cpu_restore_state(tb, env, pc, puc);
    }
    if (ret == 1) {
#if 0
        printf("PF exception: EIP=0x%08x CR2=0x%08x error=0x%x\n",
               env->eip, env->cr[2], env->error_code);
#endif
        /* we restore the process signal mask as the sigreturn should
           do it (XXX: use sigsetjmp) */
        sigprocmask(SIG_SETMASK, old_set, NULL);
        raise_exception_err(env->exception_index, env->error_code);
    } else {
        /* activate soft MMU for this block */
        env->hflags |= HF_SOFTMMU_MASK;
        cpu_resume_from_signal(env, puc);
    }
    /* never comes here */
    return 1;
}

#elif defined(TARGET_ARM)
static inline int handle_cpu_signal(unsigned long pc, unsigned long address,
                                    int is_write, sigset_t *old_set,
                                    void *puc)
{
    TranslationBlock *tb;
    int ret;

    if (cpu_single_env)
        env = cpu_single_env; /* XXX: find a correct solution for multithread */
#if defined(DEBUG_SIGNAL)
    printf("qemu: SIGSEGV pc=0x%08lx address=%08lx w=%d oldset=0x%08lx\n",
           pc, address, is_write, *(unsigned long *)old_set);
#endif
    /* XXX: locking issue */
    if (is_write && page_unprotect(h2g(address), pc, puc)) {
        return 1;
    }
    /* see if it is an MMU fault */
    ret = cpu_arm_handle_mmu_fault(env, address, is_write, 1, 0);
    if (ret < 0)
        return 0; /* not an MMU fault */
    if (ret == 0)
        return 1; /* the MMU fault was handled without causing real CPU fault */
    /* now we have a real cpu fault */
    tb = tb_find_pc(pc);
    if (tb) {
        /* the PC is inside the translated code. It means that we have
           a virtual CPU fault */
        cpu_restore_state(tb, env, pc, puc);
    }
    /* we restore the process signal mask as the sigreturn should
       do it (XXX: use sigsetjmp) */
    sigprocmask(SIG_SETMASK, old_set, NULL);
    cpu_loop_exit();
}
#elif defined(TARGET_SPARC)
static inline int handle_cpu_signal(unsigned long pc, unsigned long address,
                                    int is_write, sigset_t *old_set,
                                    void *puc)
{
    TranslationBlock *tb;
    int ret;

    if (cpu_single_env)
        env = cpu_single_env; /* XXX: find a correct solution for multithread */
#if defined(DEBUG_SIGNAL)
    printf("qemu: SIGSEGV pc=0x%08lx address=%08lx w=%d oldset=0x%08lx\n",
           pc, address, is_write, *(unsigned long *)old_set);
#endif
    /* XXX: locking issue */
    if (is_write && page_unprotect(h2g(address), pc, puc)) {
        return 1;
    }
    /* see if it is an MMU fault */
    ret = cpu_sparc_handle_mmu_fault(env, address, is_write, 1, 0);
    if (ret < 0)
        return 0; /* not an MMU fault */
    if (ret == 0)
        return 1; /* the MMU fault was handled without causing real CPU fault */
    /* now we have a real cpu fault */
    tb = tb_find_pc(pc);
    if (tb) {
        /* the PC is inside the translated code. It means that we have
           a virtual CPU fault */
        cpu_restore_state(tb, env, pc, puc);
    }
    /* we restore the process signal mask as the sigreturn should
       do it (XXX: use sigsetjmp) */
    sigprocmask(SIG_SETMASK, old_set, NULL);
    cpu_loop_exit();
}
#elif defined (TARGET_PPC)
static inline int handle_cpu_signal(unsigned long pc, unsigned long address,
                                    int is_write, sigset_t *old_set,
                                    void *puc)
{
    TranslationBlock *tb;
    int ret;

    if (cpu_single_env)
        env = cpu_single_env; /* XXX: find a correct solution for multithread */
#if defined(DEBUG_SIGNAL)
    printf("qemu: SIGSEGV pc=0x%08lx address=%08lx w=%d oldset=0x%08lx\n",
           pc, address, is_write, *(unsigned long *)old_set);
#endif
    /* XXX: locking issue */
    if (is_write && page_unprotect(h2g(address), pc, puc)) {
        return 1;
    }

    /* see if it is an MMU fault */
    ret = cpu_ppc_handle_mmu_fault(env, address, is_write, msr_pr, 0);
    if (ret < 0)
        return 0; /* not an MMU fault */
    if (ret == 0)
        return 1; /* the MMU fault was handled without causing real CPU fault */

    /* now we have a real cpu fault */
    tb = tb_find_pc(pc);
    if (tb) {
        /* the PC is inside the translated code. It means that we have
           a virtual CPU fault */
        cpu_restore_state(tb, env, pc, puc);
    }
    if (ret == 1) {
#if 0
        printf("PF exception: NIP=0x%08x error=0x%x %p\n",
               env->nip, env->error_code, tb);
#endif
    /* we restore the process signal mask as the sigreturn should
       do it (XXX: use sigsetjmp) */
        sigprocmask(SIG_SETMASK, old_set, NULL);
        do_raise_exception_err(env->exception_index, env->error_code);
    } else {
        /* activate soft MMU for this block */
        cpu_resume_from_signal(env, puc);
    }
    /* never comes here */
    return 1;
}

#elif defined (TARGET_MIPS)
static inline int handle_cpu_signal(unsigned long pc, unsigned long address,
                                    int is_write, sigset_t *old_set,
                                    void *puc)
{
    TranslationBlock *tb;
    int ret;

    if (cpu_single_env)
        env = cpu_single_env; /* XXX: find a correct solution for multithread */
#if defined(DEBUG_SIGNAL)
    printf("qemu: SIGSEGV pc=0x%08lx address=%08lx w=%d oldset=0x%08lx\n",
           pc, address, is_write, *(unsigned long *)old_set);
#endif
    /* XXX: locking issue */
    if (is_write && page_unprotect(h2g(address), pc, puc)) {
        return 1;
    }

    /* see if it is an MMU fault */
    ret = cpu_mips_handle_mmu_fault(env, address, is_write, 1, 0);
    if (ret < 0)
        return 0; /* not an MMU fault */
    if (ret == 0)
        return 1; /* the MMU fault was handled without causing real CPU fault */

    /* now we have a real cpu fault */
    tb = tb_find_pc(pc);
    if (tb) {
        /* the PC is inside the translated code. It means that we have
           a virtual CPU fault */
        cpu_restore_state(tb, env, pc, puc);
    }
    if (ret == 1) {
#if 0
        printf("PF exception: NIP=0x%08x error=0x%x %p\n",
               env->nip, env->error_code, tb);
#endif
    /* we restore the process signal mask as the sigreturn should
       do it (XXX: use sigsetjmp) */
        sigprocmask(SIG_SETMASK, old_set, NULL);
        do_raise_exception_err(env->exception_index, env->error_code);
    } else {
        /* activate soft MMU for this block */
        cpu_resume_from_signal(env, puc);
    }
    /* never comes here */
    return 1;
}

#elif defined (TARGET_SH4)
static inline int handle_cpu_signal(unsigned long pc, unsigned long address,
                                    int is_write, sigset_t *old_set,
                                    void *puc)
{
    TranslationBlock *tb;
    int ret;

    if (cpu_single_env)
        env = cpu_single_env; /* XXX: find a correct solution for multithread */
#if defined(DEBUG_SIGNAL)
    printf("qemu: SIGSEGV pc=0x%08lx address=%08lx w=%d oldset=0x%08lx\n",
           pc, address, is_write, *(unsigned long *)old_set);
#endif
    /* XXX: locking issue */
    if (is_write && page_unprotect(h2g(address), pc, puc)) {
        return 1;
    }

    /* see if it is an MMU fault */
    ret = cpu_sh4_handle_mmu_fault(env, address, is_write, 1, 0);
    if (ret < 0)
        return 0; /* not an MMU fault */
    if (ret == 0)
        return 1; /* the MMU fault was handled without causing real CPU fault */

    /* now we have a real cpu fault */
    tb = tb_find_pc(pc);
    if (tb) {
        /* the PC is inside the translated code. It means that we have
           a virtual CPU fault */
        cpu_restore_state(tb, env, pc, puc);
    }
#if 0
        printf("PF exception: NIP=0x%08x error=0x%x %p\n",
               env->nip, env->error_code, tb);
#endif
    /* we restore the process signal mask as the sigreturn should
       do it (XXX: use sigsetjmp) */
    sigprocmask(SIG_SETMASK, old_set, NULL);
    cpu_loop_exit();
    /* never comes here */
    return 1;
}
#else
#error unsupported target CPU
#endif

#if defined(__i386__)

#if defined(USE_CODE_COPY)
static void cpu_send_trap(unsigned long pc, int trap,
                          struct ucontext *uc)
{
    TranslationBlock *tb;

    if (cpu_single_env)
        env = cpu_single_env; /* XXX: find a correct solution for multithread */
    /* now we have a real cpu fault */
    tb = tb_find_pc(pc);
    if (tb) {
        /* the PC is inside the translated code. It means that we have
           a virtual CPU fault */
        cpu_restore_state(tb, env, pc, uc);
    }
    sigprocmask(SIG_SETMASK, &uc->uc_sigmask, NULL);
    raise_exception_err(trap, env->error_code);
}
#endif

int cpu_signal_handler(int host_signum, struct siginfo *info,
                       void *puc)
{
    struct ucontext *uc = puc;
    unsigned long pc;
    int trapno;

#ifndef REG_EIP
/* for glibc 2.1 */
#define REG_EIP    EIP
#define REG_ERR    ERR
#define REG_TRAPNO TRAPNO
#endif
    pc = uc->uc_mcontext.gregs[REG_EIP];
    trapno = uc->uc_mcontext.gregs[REG_TRAPNO];
#if defined(TARGET_I386) && defined(USE_CODE_COPY)
    if (trapno == 0x00 || trapno == 0x05) {
        /* send division by zero or bound exception */
        cpu_send_trap(pc, trapno, uc);
        return 1;
    } else
#endif
        return handle_cpu_signal(pc, (unsigned long)info->si_addr,
                                 trapno == 0xe ?
                                 (uc->uc_mcontext.gregs[REG_ERR] >> 1) & 1 : 0,
                                 &uc->uc_sigmask, puc);
}

#elif defined(__x86_64__)

int cpu_signal_handler(int host_signum, struct siginfo *info,
                       void *puc)
{
    struct ucontext *uc = puc;
    unsigned long pc;

    pc = uc->uc_mcontext.gregs[REG_RIP];
    return handle_cpu_signal(pc, (unsigned long)info->si_addr,
                             uc->uc_mcontext.gregs[REG_TRAPNO] == 0xe ?
                             (uc->uc_mcontext.gregs[REG_ERR] >> 1) & 1 : 0,
                             &uc->uc_sigmask, puc);
}

#elif defined(__powerpc__)

/***********************************************************************
 * signal context platform-specific definitions
 * From Wine
 */
#ifdef linux
/* All Registers access - only for local access */
# define REG_sig(reg_name, context)		((context)->uc_mcontext.regs->reg_name)
/* Gpr Registers access  */
# define GPR_sig(reg_num, context)		REG_sig(gpr[reg_num], context)
# define IAR_sig(context)			REG_sig(nip, context)	/* Program counter */
# define MSR_sig(context)			REG_sig(msr, context)   /* Machine State Register (Supervisor) */
# define CTR_sig(context)			REG_sig(ctr, context)   /* Count register */
# define XER_sig(context)			REG_sig(xer, context) /* User's integer exception register */
# define LR_sig(context)			REG_sig(link, context) /* Link register */
# define CR_sig(context)			REG_sig(ccr, context) /* Condition register */
/* Float Registers access  */
# define FLOAT_sig(reg_num, context)		(((double*)((char*)((context)->uc_mcontext.regs+48*4)))[reg_num])
# define FPSCR_sig(context)			(*(int*)((char*)((context)->uc_mcontext.regs+(48+32*2)*4)))
/* Exception Registers access */
# define DAR_sig(context)			REG_sig(dar, context)
# define DSISR_sig(context)			REG_sig(dsisr, context)
# define TRAP_sig(context)			REG_sig(trap, context)
#endif /* linux */

#ifdef __APPLE__
# include <sys/ucontext.h>
typedef struct ucontext SIGCONTEXT;
/* All Registers access - only for local access */
# define REG_sig(reg_name, context)		((context)->uc_mcontext->ss.reg_name)
# define FLOATREG_sig(reg_name, context)	((context)->uc_mcontext->fs.reg_name)
# define EXCEPREG_sig(reg_name, context)	((context)->uc_mcontext->es.reg_name)
# define VECREG_sig(reg_name, context)		((context)->uc_mcontext->vs.reg_name)
/* Gpr Registers access */
# define GPR_sig(reg_num, context)		REG_sig(r##reg_num, context)
# define IAR_sig(context)			REG_sig(srr0, context)	/* Program counter */
# define MSR_sig(context)			REG_sig(srr1, context)  /* Machine State Register (Supervisor) */
# define CTR_sig(context)			REG_sig(ctr, context)
# define XER_sig(context)			REG_sig(xer, context) /* Link register */
# define LR_sig(context)			REG_sig(lr, context)  /* User's integer exception register */
# define CR_sig(context)			REG_sig(cr, context)  /* Condition register */
/* Float Registers access */
# define FLOAT_sig(reg_num, context)		FLOATREG_sig(fpregs[reg_num], context)
# define FPSCR_sig(context)			((double)FLOATREG_sig(fpscr, context))
/* Exception Registers access */
# define DAR_sig(context)			EXCEPREG_sig(dar, context)     /* Fault registers for coredump */
# define DSISR_sig(context)			EXCEPREG_sig(dsisr, context)
# define TRAP_sig(context)			EXCEPREG_sig(exception, context) /* number of powerpc exception taken */
#endif /* __APPLE__ */

int cpu_signal_handler(int host_signum, struct siginfo *info,
                       void *puc)
{
    struct ucontext *uc = puc;
    unsigned long pc;
    int is_write;

    pc = IAR_sig(uc);
    is_write = 0;
#if 0
    /* ppc 4xx case */
    if (DSISR_sig(uc) & 0x00800000)
        is_write = 1;
#else
    if (TRAP_sig(uc) != 0x400 && (DSISR_sig(uc) & 0x02000000))
        is_write = 1;
#endif
    return handle_cpu_signal(pc, (unsigned long)info->si_addr,
                             is_write, &uc->uc_sigmask, puc);
}

#elif defined(__alpha__)

int cpu_signal_handler(int host_signum, struct siginfo *info,
                           void *puc)
{
    struct ucontext *uc = puc;
    uint32_t *pc = uc->uc_mcontext.sc_pc;
    uint32_t insn = *pc;
    int is_write = 0;

    /* XXX: need kernel patch to get write flag faster */
    switch (insn >> 26) {
    case 0x0d: // stw
    case 0x0e: // stb
    case 0x0f: // stq_u
    case 0x24: // stf
    case 0x25: // stg
    case 0x26: // sts
    case 0x27: // stt
    case 0x2c: // stl
    case 0x2d: // stq
    case 0x2e: // stl_c
    case 0x2f: // stq_c
	is_write = 1;
    }

    return handle_cpu_signal(pc, (unsigned long)info->si_addr,
                             is_write, &uc->uc_sigmask, puc);
}
#elif defined(__sparc__)

int cpu_signal_handler(int host_signum, struct siginfo *info,
                       void *puc)
{
    uint32_t *regs = (uint32_t *)(info + 1);
    void *sigmask = (regs + 20);
    unsigned long pc;
    int is_write;
    uint32_t insn;

    /* XXX: is there a standard glibc define ? */
    pc = regs[1];
    /* XXX: need kernel patch to get write flag faster */
    is_write = 0;
    insn = *(uint32_t *)pc;
    if ((insn >> 30) == 3) {
      switch((insn >> 19) & 0x3f) {
      case 0x05: // stb
      case 0x06: // sth
      case 0x04: // st
      case 0x07: // std
      case 0x24: // stf
      case 0x27: // stdf
      case 0x25: // stfsr
	is_write = 1;
	break;
      }
    }
    return handle_cpu_signal(pc, (unsigned long)info->si_addr,
                             is_write, sigmask, NULL);
}

#elif defined(__arm__)

int cpu_signal_handler(int host_signum, struct siginfo *info,
                       void *puc)
{
    struct ucontext *uc = puc;
    unsigned long pc;
    int is_write;

    pc = uc->uc_mcontext.gregs[R15];
    /* XXX: compute is_write */
    is_write = 0;
    return handle_cpu_signal(pc, (unsigned long)info->si_addr,
                             is_write,
                             &uc->uc_sigmask);
}

#elif defined(__mc68000)

int cpu_signal_handler(int host_signum, struct siginfo *info,
                       void *puc)
{
    struct ucontext *uc = puc;
    unsigned long pc;
    int is_write;

    pc = uc->uc_mcontext.gregs[16];
    /* XXX: compute is_write */
    is_write = 0;
    return handle_cpu_signal(pc, (unsigned long)info->si_addr,
                             is_write,
                             &uc->uc_sigmask, puc);
}

#elif defined(__ia64)

#ifndef __ISR_VALID
  /* This ought to be in <bits/siginfo.h>... */
# define __ISR_VALID	1
#endif

int cpu_signal_handler(int host_signum, struct siginfo *info, void *puc)
{
    struct ucontext *uc = puc;
    unsigned long ip;
    int is_write = 0;

    ip = uc->uc_mcontext.sc_ip;
    switch (host_signum) {
      case SIGILL:
      case SIGFPE:
      case SIGSEGV:
      case SIGBUS:
      case SIGTRAP:
	  if (info->si_code && (info->si_segvflags & __ISR_VALID))
	      /* ISR.W (write-access) is bit 33:  */
	      is_write = (info->si_isr >> 33) & 1;
	  break;

      default:
	  break;
    }
    return handle_cpu_signal(ip, (unsigned long)info->si_addr,
                             is_write,
                             &uc->uc_sigmask, puc);
}

#elif defined(__s390__)

int cpu_signal_handler(int host_signum, struct siginfo *info,
                       void *puc)
{
    struct ucontext *uc = puc;
    unsigned long pc;
    int is_write;

    pc = uc->uc_mcontext.psw.addr;
    /* XXX: compute is_write */
    is_write = 0;
    return handle_cpu_signal(pc, (unsigned long)info->si_addr,
                             is_write,
                             &uc->uc_sigmask, puc);
}

#else

#error host CPU specific signal handler needed

#endif

#endif /* !defined(CONFIG_SOFTMMU) */
