/*
 * Copyright (C) 2008 The Android Open Source Project
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *  * Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *  * Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 * FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS
 * OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT
 * OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 */
#ifndef _SYS_TLS_H
#define _SYS_TLS_H

#include <sys/cdefs.h>

__BEGIN_DECLS

/* maximum number of elements in the TLS array */
#define BIONIC_TLS_SLOTS            64

/* note that slot 0, called TLS_SLOT_SELF must point to itself.
 * this is required to implement thread-local storage with the x86
 * Linux kernel, that reads the TLS from fs:[0], where 'fs' is a
 * thread-specific segment descriptor...
 */

/* Well known TLS slots */
#define TLS_SLOT_SELF               0
#define TLS_SLOT_THREAD_ID          1
#define TLS_SLOT_ERRNO              2

#define TLS_SLOT_OPENGL_API         3
#define TLS_SLOT_OPENGL             4

/* small technical note: it is not possible to call pthread_setspecific
 * on keys that are <= TLS_SLOT_MAX_WELL_KNOWN, which is why it is set to
 * TLS_SLOT_ERRNO.
 *
 * later slots like TLS_SLOT_OPENGL are pre-allocated through the use of
 * TLS_DEFAULT_ALLOC_MAP. this means that there is no need to use
 * pthread_key_create() to initialize them. on the other hand, there is
 * no destructor associated to them (we might need to implement this later)
 */
#define TLS_SLOT_MAX_WELL_KNOWN     TLS_SLOT_ERRNO

#define TLS_DEFAULT_ALLOC_MAP       0x0000001F

/* set the Thread Local Storage, must contain at least BIONIC_TLS_SLOTS pointers */
extern void __init_tls(void**  tls, void*  thread_info);

/* syscall only, do not call directly */
extern int __set_tls(void *ptr);

/* get the TLS */
#ifdef __arm__
#  define __get_tls() ( *((volatile void **) 0xffff0ff0) )
#else
extern void*  __get_tls( void );
#endif

/* return the stack base and size, used by our malloc debugger */
extern void*  __get_stack_base(int  *p_stack_size);

__END_DECLS

#endif /* _SYS_TLS_H */
