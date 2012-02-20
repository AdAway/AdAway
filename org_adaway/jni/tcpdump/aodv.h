/* @(#) $Header: /tcpdump/master/tcpdump/aodv.h,v 1.3 2003/09/13 01:34:42 guy Exp $ (LBL) */
/*
 * Copyright (c) 2003 Bruce M. Simpson <bms@spc.org>
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. All advertising materials mentioning features or use of this software
 *    must display the following acknowledgement:
 *        This product includes software developed by Bruce M. Simpson.
 * 4. Neither the name of Bruce M. Simpson nor the names of co-
 *    contributors may be used to endorse or promote products derived
 *    from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY Bruce M. Simpson AND CONTRIBUTORS
 * ``AS IS'' AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 * TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL Bruce M. Simpson OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */
#ifndef _AODV_H_
#define _AODV_H_

struct aodv_rreq {
	u_int8_t	rreq_type;	/* AODV message type (1) */
	u_int8_t	rreq_flags;	/* various flags */
	u_int8_t	rreq_zero0;	/* reserved, set to zero */
	u_int8_t	rreq_hops;	/* number of hops from originator */
	u_int32_t	rreq_id;	/* request ID */
	u_int32_t	rreq_da;	/* destination IPv4 address */
	u_int32_t	rreq_ds;	/* destination sequence number */
	u_int32_t	rreq_oa;	/* originator IPv4 address */
	u_int32_t	rreq_os;	/* originator sequence number */
};
#ifdef INET6
struct aodv_rreq6 {
	u_int8_t	rreq_type;	/* AODV message type (1) */
	u_int8_t	rreq_flags;	/* various flags */
	u_int8_t	rreq_zero0;	/* reserved, set to zero */
	u_int8_t	rreq_hops;	/* number of hops from originator */
	u_int32_t	rreq_id;	/* request ID */
	struct in6_addr	rreq_da;	/* destination IPv6 address */
	u_int32_t	rreq_ds;	/* destination sequence number */
	struct in6_addr	rreq_oa;	/* originator IPv6 address */
	u_int32_t	rreq_os;	/* originator sequence number */
};
struct aodv_rreq6_draft_01 {
	u_int8_t	rreq_type;	/* AODV message type (16) */
	u_int8_t	rreq_flags;	/* various flags */
	u_int8_t	rreq_zero0;	/* reserved, set to zero */
	u_int8_t	rreq_hops;	/* number of hops from originator */
	u_int32_t	rreq_id;	/* request ID */
	u_int32_t	rreq_ds;	/* destination sequence number */
	u_int32_t	rreq_os;	/* originator sequence number */
	struct in6_addr	rreq_da;	/* destination IPv6 address */
	struct in6_addr	rreq_oa;	/* originator IPv6 address */
};
#endif

#define	RREQ_JOIN	0x80		/* join (reserved for multicast */
#define	RREQ_REPAIR	0x40		/* repair (reserved for multicast */
#define	RREQ_GRAT	0x20		/* gratuitous RREP */
#define	RREQ_DEST	0x10		/* destination only */
#define	RREQ_UNKNOWN	0x08		/* unknown destination sequence num */
#define	RREQ_FLAGS_MASK	0xF8		/* mask for rreq_flags */

struct aodv_rrep {
	u_int8_t	rrep_type;	/* AODV message type (2) */
	u_int8_t	rrep_flags;	/* various flags */
	u_int8_t	rrep_ps;	/* prefix size */
	u_int8_t	rrep_hops;	/* number of hops from o to d */
	u_int32_t	rrep_da;	/* destination IPv4 address */
	u_int32_t	rrep_ds;	/* destination sequence number */
	u_int32_t	rrep_oa;	/* originator IPv4 address */
	u_int32_t	rrep_life;	/* lifetime of this route */
};
#ifdef INET6
struct aodv_rrep6 {
	u_int8_t	rrep_type;	/* AODV message type (2) */
	u_int8_t	rrep_flags;	/* various flags */
	u_int8_t	rrep_ps;	/* prefix size */
	u_int8_t	rrep_hops;	/* number of hops from o to d */
	struct in6_addr	rrep_da;	/* destination IPv6 address */
	u_int32_t	rrep_ds;	/* destination sequence number */
	struct in6_addr	rrep_oa;	/* originator IPv6 address */
	u_int32_t	rrep_life;	/* lifetime of this route */
};
struct aodv_rrep6_draft_01 {
	u_int8_t	rrep_type;	/* AODV message type (17) */
	u_int8_t	rrep_flags;	/* various flags */
	u_int8_t	rrep_ps;	/* prefix size */
	u_int8_t	rrep_hops;	/* number of hops from o to d */
	u_int32_t	rrep_ds;	/* destination sequence number */
	struct in6_addr	rrep_da;	/* destination IPv6 address */
	struct in6_addr	rrep_oa;	/* originator IPv6 address */
	u_int32_t	rrep_life;	/* lifetime of this route */
};
#endif

#define	RREP_REPAIR		0x80	/* repair (reserved for multicast */
#define	RREP_ACK		0x40	/* acknowledgement required */
#define	RREP_FLAGS_MASK		0xC0	/* mask for rrep_flags */
#define	RREP_PREFIX_MASK	0x1F	/* mask for prefix size */

struct rerr_unreach {
	u_int32_t	u_da;	/* IPv4 address */
	u_int32_t	u_ds;	/* sequence number */
};
#ifdef INET6
struct rerr_unreach6 {
	struct in6_addr	u_da;	/* IPv6 address */
	u_int32_t	u_ds;	/* sequence number */
};
struct rerr_unreach6_draft_01 {
	struct in6_addr	u_da;	/* IPv6 address */
	u_int32_t	u_ds;	/* sequence number */
};
#endif

struct aodv_rerr {
	u_int8_t	rerr_type;	/* AODV message type (3 or 18) */
	u_int8_t	rerr_flags;	/* various flags */
	u_int8_t	rerr_zero0;	/* reserved, set to zero */
	u_int8_t	rerr_dc;	/* destination count */
	union {
		struct	rerr_unreach dest[1];
#ifdef INET6
		struct	rerr_unreach6 dest6[1];
		struct	rerr_unreach6_draft_01 dest6_draft_01[1];
#endif
	} r;
};

#define RERR_NODELETE		0x80	/* don't delete the link */
#define RERR_FLAGS_MASK		0x80	/* mask for rerr_flags */

struct aodv_rrep_ack {
	u_int8_t	ra_type;
	u_int8_t	ra_zero0;
};

union aodv {
	struct aodv_rreq rreq;
	struct aodv_rrep rrep;
	struct aodv_rerr rerr;
	struct aodv_rrep_ack rrep_ack;
#ifdef INET6
	struct aodv_rreq6 rreq6;
	struct aodv_rreq6_draft_01 rreq6_draft_01;
	struct aodv_rrep6 rrep6;
	struct aodv_rrep6_draft_01 rrep6_draft_01;
#endif
};

#define	AODV_RREQ		1	/* route request */
#define	AODV_RREP		2	/* route response */
#define	AODV_RERR		3	/* error report */
#define	AODV_RREP_ACK		4	/* route response acknowledgement */

#define AODV_V6_DRAFT_01_RREQ		16	/* IPv6 route request */
#define AODV_V6_DRAFT_01_RREP		17	/* IPv6 route response */
#define AODV_V6_DRAFT_01_RERR		18	/* IPv6 error report */
#define AODV_V6_DRAFT_01_RREP_ACK	19	/* IPV6 route response acknowledgment */

struct aodv_ext {
	u_int8_t	type;		/* extension type */
	u_int8_t	length;		/* extension length */
};

struct aodv_hello {
	struct	aodv_ext	eh;		/* extension header */
	u_int32_t		interval;	/* expect my next hello in
						 * (n) ms */
};

#define	AODV_EXT_HELLO	1

#endif /* _AODV_H_ */
