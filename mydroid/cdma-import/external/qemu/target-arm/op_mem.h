/* ARM memory operations.  */

#ifdef GEN_TRACE
/* Load from address T1 into T0.  */
#define MEM_LD_OP(name) \
void OPPROTO glue(op_ld##name,MEMSUFFIX)(void) \
{ \
    extern int tracing; \
    extern void dcache_load(uint32_t addr); \
    if (tracing) \
      dcache_load(T1); \
    T0 = glue(ld##name,MEMSUFFIX)(T1); \
    FORCE_RET(); \
}
#else
/* Load from address T1 into T0.  */
#define MEM_LD_OP(name) \
void OPPROTO glue(op_ld##name,MEMSUFFIX)(void) \
{ \
    T0 = glue(ld##name,MEMSUFFIX)(T1); \
    FORCE_RET(); \
}
#endif

MEM_LD_OP(ub)
MEM_LD_OP(sb)
MEM_LD_OP(uw)
MEM_LD_OP(sw)
MEM_LD_OP(l)

#undef MEM_LD_OP

#ifdef GEN_TRACE
/* Store T0 to address T1.  */
#define MEM_ST_OP(name) \
void OPPROTO glue(op_st##name,MEMSUFFIX)(void) \
{ \
    extern int tracing; \
    extern void dcache_store(uint32_t addr, uint32_t val); \
    if (tracing) \
      dcache_store(T1, T0); \
    glue(st##name,MEMSUFFIX)(T1, T0); \
    FORCE_RET(); \
}
#else
/* Store T0 to address T1.  */
#define MEM_ST_OP(name) \
void OPPROTO glue(op_st##name,MEMSUFFIX)(void) \
{ \
    glue(st##name,MEMSUFFIX)(T1, T0); \
    FORCE_RET(); \
}
#endif

MEM_ST_OP(b)
MEM_ST_OP(w)
MEM_ST_OP(l)

#undef MEM_ST_OP

#ifdef GEN_TRACE
/* Swap T0 with memory at address T1.  */
/* ??? Is this exception safe?  */
#define MEM_SWP_OP(name, lname) \
void OPPROTO glue(op_swp##name,MEMSUFFIX)(void) \
{ \
    extern int tracing; \
    extern void dcache_swp(uint32_t addr); \
    uint32_t tmp; \
    cpu_lock(); \
    if (tracing) \
      dcache_swp(T1); \
    tmp = glue(ld##lname,MEMSUFFIX)(T1); \
    glue(st##name,MEMSUFFIX)(T1, T0); \
    T0 = tmp; \
    cpu_unlock(); \
    FORCE_RET(); \
}
#else
/* Swap T0 with memory at address T1.  */
/* ??? Is this exception safe?  */
#define MEM_SWP_OP(name, lname) \
void OPPROTO glue(op_swp##name,MEMSUFFIX)(void) \
{ \
    uint32_t tmp; \
    cpu_lock(); \
    tmp = glue(ld##lname,MEMSUFFIX)(T1); \
    glue(st##name,MEMSUFFIX)(T1, T0); \
    T0 = tmp; \
    cpu_unlock(); \
    FORCE_RET(); \
}
#endif

MEM_SWP_OP(b, ub)
MEM_SWP_OP(l, l)

#undef MEM_SWP_OP

/* Floating point load/store.  Address is in T1 */
#define VFP_MEM_OP(p, w) \
void OPPROTO glue(op_vfp_ld##p,MEMSUFFIX)(void) \
{ \
    FT0##p = glue(ldf##w,MEMSUFFIX)(T1); \
    FORCE_RET(); \
} \
void OPPROTO glue(op_vfp_st##p,MEMSUFFIX)(void) \
{ \
    glue(stf##w,MEMSUFFIX)(T1, FT0##p); \
    FORCE_RET(); \
}

VFP_MEM_OP(s,l)
VFP_MEM_OP(d,q)

#undef VFP_MEM_OP

#undef MEMSUFFIX
