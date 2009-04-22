/*
 * Copyright (c) 1995 Danny Gasparovski.
 * 
 * Please read the file COPYRIGHT for the 
 * terms and conditions of the copyright.
 */

#include <slirp.h>
#define SLIRP_COMPILATION
#include "sockets.h"

void
sbuf_free(SBuf  sb)
{
	free(sb->sb_data);
}

void
sbuf_drop(SBuf  sb, int  num)
{
	/* 
	 * We can only drop how much we have
	 * This should never succeed 
	 */
	if(num > sb->sb_cc)
		num = sb->sb_cc;
	sb->sb_cc -= num;
	sb->sb_rptr += num;
	if(sb->sb_rptr >= sb->sb_data + sb->sb_datalen)
		sb->sb_rptr -= sb->sb_datalen;
   
}

void
sbuf_reserve(SBuf  sb, int  size)
{
    if (sb->sb_datalen == size)
        return;

    sb->sb_wptr = sb->sb_rptr = sb->sb_data = (char *)realloc(sb->sb_data, size);
    sb->sb_cc = 0;
    if (sb->sb_wptr)
       sb->sb_datalen = size;
    else
       sb->sb_datalen = 0;
}

/*
 * Try and write() to the socket, whatever doesn't get written
 * append to the buffer... for a host with a fast net connection,
 * this prevents an unnecessary copy of the data
 * (the socket is non-blocking, so we won't hang)
 */
void
sbuf_append(struct socket *so, MBuf  m)
{
	int ret = 0;

	DEBUG_CALL("sbuf_append");
	DEBUG_ARG("so = %lx", (long)so);
	DEBUG_ARG("m = %lx", (long)m);
	DEBUG_ARG("m->m_len = %d", m->m_len);
	
	/* Shouldn't happen, but...  e.g. foreign host closes connection */
	if (m->m_len <= 0) {
		mbuf_free(m);
		return;
	}
	
	/*
	 * If there is urgent data, call sosendoob
	 * if not all was sent, sowrite will take care of the rest
	 * (The rest of this function is just an optimisation)
	 */
	if (so->so_urgc) {
		sbuf_appendsb(&so->so_rcv, m);
		mbuf_free(m);
		sosendoob(so);
		return;
	}
	
	/*
	 * We only write if there's nothing in the buffer,
	 * ottherwise it'll arrive out of order, and hence corrupt
	 */
	if (!so->so_rcv.sb_cc) {
        do {
            ret = send(so->s, m->m_data, m->m_len, 0);
        } while (ret == 0 && socket_errno == EINTR);
    }

	if (ret <= 0) {
		/* 
		 * Nothing was written
		 * It's possible that the socket has closed, but
		 * we don't need to check because if it has closed,
		 * it will be detected in the normal way by soread()
		 */
		sbuf_appendsb(&so->so_rcv, m);
	} else if (ret != m->m_len) {
		/*
		 * Something was written, but not everything..
		 * sbuf_appendsb the rest
		 */
		m->m_len -= ret;
		m->m_data += ret;
		sbuf_appendsb(&so->so_rcv, m);
	} /* else */
	/* Whatever happened, we free the mbuf */
	mbuf_free(m);
}

/*
 * Copy the data from m into sb
 * The caller is responsible to make sure there's enough room
 */
void
sbuf_appendsb(SBuf  sb, MBuf  m)
{
	int len, n,  nn;
	
	len = m->m_len;

	if (sb->sb_wptr < sb->sb_rptr) {
		n = sb->sb_rptr - sb->sb_wptr;
		if (n > len) n = len;
		memcpy(sb->sb_wptr, m->m_data, n);
	} else {
		/* Do the right edge first */
		n = sb->sb_data + sb->sb_datalen - sb->sb_wptr;
		if (n > len) n = len;
		memcpy(sb->sb_wptr, m->m_data, n);
		len -= n;
		if (len) {
			/* Now the left edge */
			nn = sb->sb_rptr - sb->sb_data;
			if (nn > len) nn = len;
			memcpy(sb->sb_data,m->m_data+n,nn);
			n += nn;
		}
	}

	sb->sb_cc   += n;
	sb->sb_wptr += n;
	if (sb->sb_wptr >= sb->sb_data + sb->sb_datalen)
		sb->sb_wptr -= sb->sb_datalen;
}

/*
 * Copy data from sbuf to a normal, straight buffer
 * Don't update the sbuf rptr, this will be
 * done in sbdrop when the data is acked
 */
void
sbuf_copy(SBuf  sb, int  off, int  len, char*  to)
{
	char *from;
	
	from = sb->sb_rptr + off;
	if (from >= sb->sb_data + sb->sb_datalen)
		from -= sb->sb_datalen;

	if (from < sb->sb_wptr) {
		if (len > sb->sb_cc) len = sb->sb_cc;
		memcpy(to,from,len);
	} else {
		/* re-use off */
		off = (sb->sb_data + sb->sb_datalen) - from;
		if (off > len) off = len;
		memcpy(to,from,off);
		len -= off;
		if (len)
		   memcpy(to+off,sb->sb_data,len);
	}
}
		
