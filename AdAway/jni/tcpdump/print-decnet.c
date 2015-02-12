/*
 * Copyright (c) 1992, 1993, 1994, 1995, 1996, 1997
 *	The Regents of the University of California.  All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that: (1) source code distributions
 * retain the above copyright notice and this paragraph in its entirety, (2)
 * distributions including binary code include the above copyright notice and
 * this paragraph in its entirety in the documentation or other materials
 * provided with the distribution, and (3) all advertising materials mentioning
 * features or use of this software display the following acknowledgement:
 * ``This product includes software developed by the University of California,
 * Lawrence Berkeley Laboratory and its contributors.'' Neither the name of
 * the University nor the names of its contributors may be used to endorse
 * or promote products derived from this software without specific prior
 * written permission.
 * THIS SOFTWARE IS PROVIDED ``AS IS'' AND WITHOUT ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, WITHOUT LIMITATION, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE.
 */

#ifndef lint
static const char rcsid[] _U_ =
    "@(#) $Header: /tcpdump/master/tcpdump/print-decnet.c,v 1.39 2005-05-06 02:16:26 guy Exp $ (LBL)";
#endif

#ifdef HAVE_CONFIG_H
#include "config.h"
#endif

#include <tcpdump-stdinc.h>

struct mbuf;
struct rtentry;

#ifdef HAVE_NETDNET_DNETDB_H
#include <netdnet/dnetdb.h>
#endif

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "decnet.h"
#include "extract.h"
#include "interface.h"
#include "addrtoname.h"

/* Forwards */
static int print_decnet_ctlmsg(const union routehdr *, u_int, u_int);
static void print_t_info(int);
static int print_l1_routes(const char *, u_int);
static int print_l2_routes(const char *, u_int);
static void print_i_info(int);
static int print_elist(const char *, u_int);
static int print_nsp(const u_char *, u_int);
static void print_reason(int);
#ifdef	PRINT_NSPDATA
static void pdata(u_char *, int);
#endif

#ifndef HAVE_NETDNET_DNETDB_H_DNET_HTOA
extern char *dnet_htoa(struct dn_naddr *);
#endif

void
decnet_print(register const u_char *ap, register u_int length,
	     register u_int caplen)
{
	register const union routehdr *rhp;
	register int mflags;
	int dst, src, hops;
	u_int nsplen, pktlen;
	const u_char *nspp;

	if (length < sizeof(struct shorthdr)) {
		(void)printf("[|decnet]");
		return;
	}

	TCHECK2(*ap, sizeof(short));
	pktlen = EXTRACT_LE_16BITS(ap);
	if (pktlen < sizeof(struct shorthdr)) {
		(void)printf("[|decnet]");
		return;
	}
	if (pktlen > length) {
		(void)printf("[|decnet]");
		return;
	}
	length = pktlen;

	rhp = (const union routehdr *)&(ap[sizeof(short)]);
	TCHECK(rhp->rh_short.sh_flags);
	mflags = EXTRACT_LE_8BITS(rhp->rh_short.sh_flags);

	if (mflags & RMF_PAD) {
	    /* pad bytes of some sort in front of message */
	    u_int padlen = mflags & RMF_PADMASK;
	    if (vflag)
		(void) printf("[pad:%d] ", padlen);
	    if (length < padlen + 2) {
		(void)printf("[|decnet]");
		return;
	    }
	    TCHECK2(ap[sizeof(short)], padlen);
	    ap += padlen;
	    length -= padlen;
	    caplen -= padlen;
	    rhp = (const union routehdr *)&(ap[sizeof(short)]);
	    mflags = EXTRACT_LE_8BITS(rhp->rh_short.sh_flags);
	}

	if (mflags & RMF_FVER) {
		(void) printf("future-version-decnet");
		default_print(ap, min(length, caplen));
		return;
	}

	/* is it a control message? */
	if (mflags & RMF_CTLMSG) {
		if (!print_decnet_ctlmsg(rhp, length, caplen))
			goto trunc;
		return;
	}

	switch (mflags & RMF_MASK) {
	case RMF_LONG:
	    if (length < sizeof(struct longhdr)) {
		(void)printf("[|decnet]");
		return;
	    }
	    TCHECK(rhp->rh_long);
	    dst =
		EXTRACT_LE_16BITS(rhp->rh_long.lg_dst.dne_remote.dne_nodeaddr);
	    src =
		EXTRACT_LE_16BITS(rhp->rh_long.lg_src.dne_remote.dne_nodeaddr);
	    hops = EXTRACT_LE_8BITS(rhp->rh_long.lg_visits);
	    nspp = &(ap[sizeof(short) + sizeof(struct longhdr)]);
	    nsplen = length - sizeof(struct longhdr);
	    break;
	case RMF_SHORT:
	    TCHECK(rhp->rh_short);
	    dst = EXTRACT_LE_16BITS(rhp->rh_short.sh_dst);
	    src = EXTRACT_LE_16BITS(rhp->rh_short.sh_src);
	    hops = (EXTRACT_LE_8BITS(rhp->rh_short.sh_visits) & VIS_MASK)+1;
	    nspp = &(ap[sizeof(short) + sizeof(struct shorthdr)]);
	    nsplen = length - sizeof(struct shorthdr);
	    break;
	default:
	    (void) printf("unknown message flags under mask");
	    default_print((u_char *)ap, min(length, caplen));
	    return;
	}

	(void)printf("%s > %s %d ",
			dnaddr_string(src), dnaddr_string(dst), pktlen);
	if (vflag) {
	    if (mflags & RMF_RQR)
		(void)printf("RQR ");
	    if (mflags & RMF_RTS)
		(void)printf("RTS ");
	    if (mflags & RMF_IE)
		(void)printf("IE ");
	    (void)printf("%d hops ", hops);
	}

	if (!print_nsp(nspp, nsplen))
		goto trunc;
	return;

trunc:
	(void)printf("[|decnet]");
	return;
}

static int
print_decnet_ctlmsg(register const union routehdr *rhp, u_int length,
    u_int caplen)
{
	int mflags = EXTRACT_LE_8BITS(rhp->rh_short.sh_flags);
	register union controlmsg *cmp = (union controlmsg *)rhp;
	int src, dst, info, blksize, eco, ueco, hello, other, vers;
	etheraddr srcea, rtea;
	int priority;
	char *rhpx = (char *)rhp;
	int ret;

	switch (mflags & RMF_CTLMASK) {
	case RMF_INIT:
	    (void)printf("init ");
	    if (length < sizeof(struct initmsg))
		goto trunc;
	    TCHECK(cmp->cm_init);
	    src = EXTRACT_LE_16BITS(cmp->cm_init.in_src);
	    info = EXTRACT_LE_8BITS(cmp->cm_init.in_info);
	    blksize = EXTRACT_LE_16BITS(cmp->cm_init.in_blksize);
	    vers = EXTRACT_LE_8BITS(cmp->cm_init.in_vers);
	    eco = EXTRACT_LE_8BITS(cmp->cm_init.in_eco);
	    ueco = EXTRACT_LE_8BITS(cmp->cm_init.in_ueco);
	    hello = EXTRACT_LE_16BITS(cmp->cm_init.in_hello);
	    print_t_info(info);
	    (void)printf(
		"src %sblksize %d vers %d eco %d ueco %d hello %d",
			dnaddr_string(src), blksize, vers, eco, ueco,
			hello);
	    ret = 1;
	    break;
	case RMF_VER:
	    (void)printf("verification ");
	    if (length < sizeof(struct verifmsg))
		goto trunc;
	    TCHECK(cmp->cm_ver);
	    src = EXTRACT_LE_16BITS(cmp->cm_ver.ve_src);
	    other = EXTRACT_LE_8BITS(cmp->cm_ver.ve_fcnval);
	    (void)printf("src %s fcnval %o", dnaddr_string(src), other);
	    ret = 1;
	    break;
	case RMF_TEST:
	    (void)printf("test ");
	    if (length < sizeof(struct testmsg))
		goto trunc;
	    TCHECK(cmp->cm_test);
	    src = EXTRACT_LE_16BITS(cmp->cm_test.te_src);
	    other = EXTRACT_LE_8BITS(cmp->cm_test.te_data);
	    (void)printf("src %s data %o", dnaddr_string(src), other);
	    ret = 1;
	    break;
	case RMF_L1ROUT:
	    (void)printf("lev-1-routing ");
	    if (length < sizeof(struct l1rout))
		goto trunc;
	    TCHECK(cmp->cm_l1rou);
	    src = EXTRACT_LE_16BITS(cmp->cm_l1rou.r1_src);
	    (void)printf("src %s ", dnaddr_string(src));
	    ret = print_l1_routes(&(rhpx[sizeof(struct l1rout)]),
				length - sizeof(struct l1rout));
	    break;
	case RMF_L2ROUT:
	    (void)printf("lev-2-routing ");
	    if (length < sizeof(struct l2rout))
		goto trunc;
	    TCHECK(cmp->cm_l2rout);
	    src = EXTRACT_LE_16BITS(cmp->cm_l2rout.r2_src);
	    (void)printf("src %s ", dnaddr_string(src));
	    ret = print_l2_routes(&(rhpx[sizeof(struct l2rout)]),
				length - sizeof(struct l2rout));
	    break;
	case RMF_RHELLO:
	    (void)printf("router-hello ");
	    if (length < sizeof(struct rhellomsg))
		goto trunc;
	    TCHECK(cmp->cm_rhello);
	    vers = EXTRACT_LE_8BITS(cmp->cm_rhello.rh_vers);
	    eco = EXTRACT_LE_8BITS(cmp->cm_rhello.rh_eco);
	    ueco = EXTRACT_LE_8BITS(cmp->cm_rhello.rh_ueco);
	    memcpy((char *)&srcea, (char *)&(cmp->cm_rhello.rh_src),
		sizeof(srcea));
	    src = EXTRACT_LE_16BITS(srcea.dne_remote.dne_nodeaddr);
	    info = EXTRACT_LE_8BITS(cmp->cm_rhello.rh_info);
	    blksize = EXTRACT_LE_16BITS(cmp->cm_rhello.rh_blksize);
	    priority = EXTRACT_LE_8BITS(cmp->cm_rhello.rh_priority);
	    hello = EXTRACT_LE_16BITS(cmp->cm_rhello.rh_hello);
	    print_i_info(info);
	    (void)printf(
	    "vers %d eco %d ueco %d src %s blksize %d pri %d hello %d",
			vers, eco, ueco, dnaddr_string(src),
			blksize, priority, hello);
	    ret = print_elist(&(rhpx[sizeof(struct rhellomsg)]),
				length - sizeof(struct rhellomsg));
	    break;
	case RMF_EHELLO:
	    (void)printf("endnode-hello ");
	    if (length < sizeof(struct ehellomsg))
		goto trunc;
	    TCHECK(cmp->cm_ehello);
	    vers = EXTRACT_LE_8BITS(cmp->cm_ehello.eh_vers);
	    eco = EXTRACT_LE_8BITS(cmp->cm_ehello.eh_eco);
	    ueco = EXTRACT_LE_8BITS(cmp->cm_ehello.eh_ueco);
	    memcpy((char *)&srcea, (char *)&(cmp->cm_ehello.eh_src),
		sizeof(srcea));
	    src = EXTRACT_LE_16BITS(srcea.dne_remote.dne_nodeaddr);
	    info = EXTRACT_LE_8BITS(cmp->cm_ehello.eh_info);
	    blksize = EXTRACT_LE_16BITS(cmp->cm_ehello.eh_blksize);
	    /*seed*/
	    memcpy((char *)&rtea, (char *)&(cmp->cm_ehello.eh_router),
		sizeof(rtea));
	    dst = EXTRACT_LE_16BITS(rtea.dne_remote.dne_nodeaddr);
	    hello = EXTRACT_LE_16BITS(cmp->cm_ehello.eh_hello);
	    other = EXTRACT_LE_8BITS(cmp->cm_ehello.eh_data);
	    print_i_info(info);
	    (void)printf(
	"vers %d eco %d ueco %d src %s blksize %d rtr %s hello %d data %o",
			vers, eco, ueco, dnaddr_string(src),
			blksize, dnaddr_string(dst), hello, other);
	    ret = 1;
	    break;

	default:
	    (void)printf("unknown control message");
	    default_print((u_char *)rhp, min(length, caplen));
	    ret = 1;
	    break;
	}
	return (ret);

trunc:
	return (0);
}

static void
print_t_info(int info)
{
	int ntype = info & 3;
	switch (ntype) {
	case 0: (void)printf("reserved-ntype? "); break;
	case TI_L2ROUT: (void)printf("l2rout "); break;
	case TI_L1ROUT: (void)printf("l1rout "); break;
	case TI_ENDNODE: (void)printf("endnode "); break;
	}
	if (info & TI_VERIF)
	    (void)printf("verif ");
	if (info & TI_BLOCK)
	    (void)printf("blo ");
}

static int
print_l1_routes(const char *rp, u_int len)
{
	int count;
	int id;
	int info;

	/* The last short is a checksum */
	while (len > (3 * sizeof(short))) {
	    TCHECK2(*rp, 3 * sizeof(short));
	    count = EXTRACT_LE_16BITS(rp);
	    if (count > 1024)
		return (1);	/* seems to be bogus from here on */
	    rp += sizeof(short);
	    len -= sizeof(short);
	    id = EXTRACT_LE_16BITS(rp);
	    rp += sizeof(short);
	    len -= sizeof(short);
	    info = EXTRACT_LE_16BITS(rp);
	    rp += sizeof(short);
	    len -= sizeof(short);
	    (void)printf("{ids %d-%d cost %d hops %d} ", id, id + count,
			    RI_COST(info), RI_HOPS(info));
	}
	return (1);

trunc:
	return (0);
}

static int
print_l2_routes(const char *rp, u_int len)
{
	int count;
	int area;
	int info;

	/* The last short is a checksum */
	while (len > (3 * sizeof(short))) {
	    TCHECK2(*rp, 3 * sizeof(short));
	    count = EXTRACT_LE_16BITS(rp);
	    if (count > 1024)
		return (1);	/* seems to be bogus from here on */
	    rp += sizeof(short);
	    len -= sizeof(short);
	    area = EXTRACT_LE_16BITS(rp);
	    rp += sizeof(short);
	    len -= sizeof(short);
	    info = EXTRACT_LE_16BITS(rp);
	    rp += sizeof(short);
	    len -= sizeof(short);
	    (void)printf("{areas %d-%d cost %d hops %d} ", area, area + count,
			    RI_COST(info), RI_HOPS(info));
	}
	return (1);

trunc:
	return (0);
}

static void
print_i_info(int info)
{
	int ntype = info & II_TYPEMASK;
	switch (ntype) {
	case 0: (void)printf("reserved-ntype? "); break;
	case II_L2ROUT: (void)printf("l2rout "); break;
	case II_L1ROUT: (void)printf("l1rout "); break;
	case II_ENDNODE: (void)printf("endnode "); break;
	}
	if (info & II_VERIF)
	    (void)printf("verif ");
	if (info & II_NOMCAST)
	    (void)printf("nomcast ");
	if (info & II_BLOCK)
	    (void)printf("blo ");
}

static int
print_elist(const char *elp _U_, u_int len _U_)
{
	/* Not enough examples available for me to debug this */
	return (1);
}

static int
print_nsp(const u_char *nspp, u_int nsplen)
{
	const struct nsphdr *nsphp = (struct nsphdr *)nspp;
	int dst, src, flags;

	if (nsplen < sizeof(struct nsphdr))
		goto trunc;
	TCHECK(*nsphp);
	flags = EXTRACT_LE_8BITS(nsphp->nh_flags);
	dst = EXTRACT_LE_16BITS(nsphp->nh_dst);
	src = EXTRACT_LE_16BITS(nsphp->nh_src);

	switch (flags & NSP_TYPEMASK) {
	case MFT_DATA:
	    switch (flags & NSP_SUBMASK) {
	    case MFS_BOM:
	    case MFS_MOM:
	    case MFS_EOM:
	    case MFS_BOM+MFS_EOM:
		printf("data %d>%d ", src, dst);
		{
		    struct seghdr *shp = (struct seghdr *)nspp;
		    int ack;
#ifdef	PRINT_NSPDATA
		    u_char *dp;
#endif
		    u_int data_off = sizeof(struct minseghdr);

		    if (nsplen < data_off)
			goto trunc;
		    TCHECK(shp->sh_seq[0]);
		    ack = EXTRACT_LE_16BITS(shp->sh_seq[0]);
		    if (ack & SGQ_ACK) {	/* acknum field */
			if ((ack & SGQ_NAK) == SGQ_NAK)
			    (void)printf("nak %d ", ack & SGQ_MASK);
			else
			    (void)printf("ack %d ", ack & SGQ_MASK);
			data_off += sizeof(short);
			if (nsplen < data_off)
			    goto trunc;
			TCHECK(shp->sh_seq[1]);
		        ack = EXTRACT_LE_16BITS(shp->sh_seq[1]);
			if (ack & SGQ_OACK) {	/* ackoth field */
			    if ((ack & SGQ_ONAK) == SGQ_ONAK)
				(void)printf("onak %d ", ack & SGQ_MASK);
			    else
				(void)printf("oack %d ", ack & SGQ_MASK);
			    data_off += sizeof(short);
			    if (nsplen < data_off)
				goto trunc;
			    TCHECK(shp->sh_seq[2]);
			    ack = EXTRACT_LE_16BITS(shp->sh_seq[2]);
			}
		    }
		    (void)printf("seg %d ", ack & SGQ_MASK);
#ifdef	PRINT_NSPDATA
		    if (nsplen > data_off) {
			dp = &(nspp[data_off]);
			TCHECK2(*dp, nsplen - data_off);
			pdata(dp, nsplen - data_off);
		    }
#endif
		}
		break;
	    case MFS_ILS+MFS_INT:
		printf("intr ");
		{
		    struct seghdr *shp = (struct seghdr *)nspp;
		    int ack;
#ifdef	PRINT_NSPDATA
		    u_char *dp;
#endif
		    u_int data_off = sizeof(struct minseghdr);

		    if (nsplen < data_off)
			goto trunc;
		    TCHECK(shp->sh_seq[0]);
		    ack = EXTRACT_LE_16BITS(shp->sh_seq[0]);
		    if (ack & SGQ_ACK) {	/* acknum field */
			if ((ack & SGQ_NAK) == SGQ_NAK)
			    (void)printf("nak %d ", ack & SGQ_MASK);
			else
			    (void)printf("ack %d ", ack & SGQ_MASK);
			data_off += sizeof(short);
			if (nsplen < data_off)
			    goto trunc;
			TCHECK(shp->sh_seq[1]);
		        ack = EXTRACT_LE_16BITS(shp->sh_seq[1]);
			if (ack & SGQ_OACK) {	/* ackdat field */
			    if ((ack & SGQ_ONAK) == SGQ_ONAK)
				(void)printf("nakdat %d ", ack & SGQ_MASK);
			    else
				(void)printf("ackdat %d ", ack & SGQ_MASK);
			    data_off += sizeof(short);
			    if (nsplen < data_off)
				goto trunc;
			    TCHECK(shp->sh_seq[2]);
			    ack = EXTRACT_LE_16BITS(shp->sh_seq[2]);
			}
		    }
		    (void)printf("seg %d ", ack & SGQ_MASK);
#ifdef	PRINT_NSPDATA
		    if (nsplen > data_off) {
			dp = &(nspp[data_off]);
			TCHECK2(*dp, nsplen - data_off);
			pdata(dp, nsplen - data_off);
		    }
#endif
		}
		break;
	    case MFS_ILS:
		(void)printf("link-service %d>%d ", src, dst);
		{
		    struct seghdr *shp = (struct seghdr *)nspp;
		    struct lsmsg *lsmp =
			(struct lsmsg *)&(nspp[sizeof(struct seghdr)]);
		    int ack;
		    int lsflags, fcval;

		    if (nsplen < sizeof(struct seghdr) + sizeof(struct lsmsg))
			goto trunc;
		    TCHECK(shp->sh_seq[0]);
		    ack = EXTRACT_LE_16BITS(shp->sh_seq[0]);
		    if (ack & SGQ_ACK) {	/* acknum field */
			if ((ack & SGQ_NAK) == SGQ_NAK)
			    (void)printf("nak %d ", ack & SGQ_MASK);
			else
			    (void)printf("ack %d ", ack & SGQ_MASK);
			TCHECK(shp->sh_seq[1]);
		        ack = EXTRACT_LE_16BITS(shp->sh_seq[1]);
			if (ack & SGQ_OACK) {	/* ackdat field */
			    if ((ack & SGQ_ONAK) == SGQ_ONAK)
				(void)printf("nakdat %d ", ack & SGQ_MASK);
			    else
				(void)printf("ackdat %d ", ack & SGQ_MASK);
			    TCHECK(shp->sh_seq[2]);
			    ack = EXTRACT_LE_16BITS(shp->sh_seq[2]);
			}
		    }
		    (void)printf("seg %d ", ack & SGQ_MASK);
		    TCHECK(*lsmp);
		    lsflags = EXTRACT_LE_8BITS(lsmp->ls_lsflags);
		    fcval = EXTRACT_LE_8BITS(lsmp->ls_fcval);
		    switch (lsflags & LSI_MASK) {
		    case LSI_DATA:
			(void)printf("dat seg count %d ", fcval);
			switch (lsflags & LSM_MASK) {
			case LSM_NOCHANGE:
			    break;
			case LSM_DONOTSEND:
			    (void)printf("donotsend-data ");
			    break;
			case LSM_SEND:
			    (void)printf("send-data ");
			    break;
			default:
			    (void)printf("reserved-fcmod? %x", lsflags);
			    break;
			}
			break;
		    case LSI_INTR:
			(void)printf("intr req count %d ", fcval);
			break;
		    default:
			(void)printf("reserved-fcval-int? %x", lsflags);
			break;
		    }
		}
		break;
	    default:
		(void)printf("reserved-subtype? %x %d > %d", flags, src, dst);
		break;
	    }
	    break;
	case MFT_ACK:
	    switch (flags & NSP_SUBMASK) {
	    case MFS_DACK:
		(void)printf("data-ack %d>%d ", src, dst);
		{
		    struct ackmsg *amp = (struct ackmsg *)nspp;
		    int ack;

		    if (nsplen < sizeof(struct ackmsg))
			goto trunc;
		    TCHECK(*amp);
		    ack = EXTRACT_LE_16BITS(amp->ak_acknum[0]);
		    if (ack & SGQ_ACK) {	/* acknum field */
			if ((ack & SGQ_NAK) == SGQ_NAK)
			    (void)printf("nak %d ", ack & SGQ_MASK);
			else
			    (void)printf("ack %d ", ack & SGQ_MASK);
		        ack = EXTRACT_LE_16BITS(amp->ak_acknum[1]);
			if (ack & SGQ_OACK) {	/* ackoth field */
			    if ((ack & SGQ_ONAK) == SGQ_ONAK)
				(void)printf("onak %d ", ack & SGQ_MASK);
			    else
				(void)printf("oack %d ", ack & SGQ_MASK);
			}
		    }
		}
		break;
	    case MFS_IACK:
		(void)printf("ils-ack %d>%d ", src, dst);
		{
		    struct ackmsg *amp = (struct ackmsg *)nspp;
		    int ack;

		    if (nsplen < sizeof(struct ackmsg))
			goto trunc;
		    TCHECK(*amp);
		    ack = EXTRACT_LE_16BITS(amp->ak_acknum[0]);
		    if (ack & SGQ_ACK) {	/* acknum field */
			if ((ack & SGQ_NAK) == SGQ_NAK)
			    (void)printf("nak %d ", ack & SGQ_MASK);
			else
			    (void)printf("ack %d ", ack & SGQ_MASK);
			TCHECK(amp->ak_acknum[1]);
		        ack = EXTRACT_LE_16BITS(amp->ak_acknum[1]);
			if (ack & SGQ_OACK) {	/* ackdat field */
			    if ((ack & SGQ_ONAK) == SGQ_ONAK)
				(void)printf("nakdat %d ", ack & SGQ_MASK);
			    else
				(void)printf("ackdat %d ", ack & SGQ_MASK);
			}
		    }
		}
		break;
	    case MFS_CACK:
		(void)printf("conn-ack %d", dst);
		break;
	    default:
		(void)printf("reserved-acktype? %x %d > %d", flags, src, dst);
		break;
	    }
	    break;
	case MFT_CTL:
	    switch (flags & NSP_SUBMASK) {
	    case MFS_CI:
	    case MFS_RCI:
		if ((flags & NSP_SUBMASK) == MFS_CI)
		    (void)printf("conn-initiate ");
		else
		    (void)printf("retrans-conn-initiate ");
		(void)printf("%d>%d ", src, dst);
		{
		    struct cimsg *cimp = (struct cimsg *)nspp;
		    int services, info, segsize;
#ifdef	PRINT_NSPDATA
		    u_char *dp;
#endif

		    if (nsplen < sizeof(struct cimsg))
			goto trunc;
		    TCHECK(*cimp);
		    services = EXTRACT_LE_8BITS(cimp->ci_services);
		    info = EXTRACT_LE_8BITS(cimp->ci_info);
		    segsize = EXTRACT_LE_16BITS(cimp->ci_segsize);

		    switch (services & COS_MASK) {
		    case COS_NONE:
			break;
		    case COS_SEGMENT:
			(void)printf("seg ");
			break;
		    case COS_MESSAGE:
			(void)printf("msg ");
			break;
		    case COS_CRYPTSER:
			(void)printf("crypt ");
			break;
		    }
		    switch (info & COI_MASK) {
		    case COI_32:
			(void)printf("ver 3.2 ");
			break;
		    case COI_31:
			(void)printf("ver 3.1 ");
			break;
		    case COI_40:
			(void)printf("ver 4.0 ");
			break;
		    case COI_41:
			(void)printf("ver 4.1 ");
			break;
		    }
		    (void)printf("segsize %d ", segsize);
#ifdef	PRINT_NSPDATA
		    if (nsplen > sizeof(struct cimsg)) {
			dp = &(nspp[sizeof(struct cimsg)]);
			TCHECK2(*dp, nsplen - sizeof(struct cimsg));
			pdata(dp, nsplen - sizeof(struct cimsg));
		    }
#endif
		}
		break;
	    case MFS_CC:
		(void)printf("conn-confirm %d>%d ", src, dst);
		{
		    struct ccmsg *ccmp = (struct ccmsg *)nspp;
		    int services, info;
		    u_int segsize, optlen;
#ifdef	PRINT_NSPDATA
		    u_char *dp;
#endif

		    if (nsplen < sizeof(struct ccmsg))
			goto trunc;
		    TCHECK(*ccmp);
		    services = EXTRACT_LE_8BITS(ccmp->cc_services);
		    info = EXTRACT_LE_8BITS(ccmp->cc_info);
		    segsize = EXTRACT_LE_16BITS(ccmp->cc_segsize);
		    optlen = EXTRACT_LE_8BITS(ccmp->cc_optlen);

		    switch (services & COS_MASK) {
		    case COS_NONE:
			break;
		    case COS_SEGMENT:
			(void)printf("seg ");
			break;
		    case COS_MESSAGE:
			(void)printf("msg ");
			break;
		    case COS_CRYPTSER:
			(void)printf("crypt ");
			break;
		    }
		    switch (info & COI_MASK) {
		    case COI_32:
			(void)printf("ver 3.2 ");
			break;
		    case COI_31:
			(void)printf("ver 3.1 ");
			break;
		    case COI_40:
			(void)printf("ver 4.0 ");
			break;
		    case COI_41:
			(void)printf("ver 4.1 ");
			break;
		    }
		    (void)printf("segsize %d ", segsize);
		    if (optlen) {
			(void)printf("optlen %d ", optlen);
#ifdef	PRINT_NSPDATA
			if (optlen > nsplen - sizeof(struct ccmsg))
			    goto trunc;
			dp = &(nspp[sizeof(struct ccmsg)]);
			TCHECK2(*dp, optlen);
			pdata(dp, optlen);
#endif
		    }
		}
		break;
	    case MFS_DI:
		(void)printf("disconn-initiate %d>%d ", src, dst);
		{
		    struct dimsg *dimp = (struct dimsg *)nspp;
		    int reason;
		    u_int optlen;
#ifdef	PRINT_NSPDATA
		    u_char *dp;
#endif

		    if (nsplen < sizeof(struct dimsg))
			goto trunc;
		    TCHECK(*dimp);
		    reason = EXTRACT_LE_16BITS(dimp->di_reason);
		    optlen = EXTRACT_LE_8BITS(dimp->di_optlen);

		    print_reason(reason);
		    if (optlen) {
			(void)printf("optlen %d ", optlen);
#ifdef	PRINT_NSPDATA
			if (optlen > nsplen - sizeof(struct dimsg))
			    goto trunc;
			dp = &(nspp[sizeof(struct dimsg)]);
			TCHECK2(*dp, optlen);
			pdata(dp, optlen);
#endif
		    }
		}
		break;
	    case MFS_DC:
		(void)printf("disconn-confirm %d>%d ", src, dst);
		{
		    struct dcmsg *dcmp = (struct dcmsg *)nspp;
		    int reason;

		    TCHECK(*dcmp);
		    reason = EXTRACT_LE_16BITS(dcmp->dc_reason);

		    print_reason(reason);
		}
		break;
	    default:
		(void)printf("reserved-ctltype? %x %d > %d", flags, src, dst);
		break;
	    }
	    break;
	default:
	    (void)printf("reserved-type? %x %d > %d", flags, src, dst);
	    break;
	}
	return (1);

trunc:
	return (0);
}

static const struct tok reason2str[] = {
	{ UC_OBJREJECT,		"object rejected connect" },
	{ UC_RESOURCES,		"insufficient resources" },
	{ UC_NOSUCHNODE,	"unrecognized node name" },
	{ DI_SHUT,		"node is shutting down" },
	{ UC_NOSUCHOBJ,		"unrecognized object" },
	{ UC_INVOBJFORMAT,	"invalid object name format" },
	{ UC_OBJTOOBUSY,	"object too busy" },
	{ DI_PROTOCOL,		"protocol error discovered" },
	{ DI_TPA,		"third party abort" },
	{ UC_USERABORT,		"user abort" },
	{ UC_INVNODEFORMAT,	"invalid node name format" },
	{ UC_LOCALSHUT,		"local node shutting down" },
	{ DI_LOCALRESRC,	"insufficient local resources" },
	{ DI_REMUSERRESRC,	"insufficient remote user resources" },
	{ UC_ACCESSREJECT,	"invalid access control information" },
	{ DI_BADACCNT,		"bad ACCOUNT information" },
	{ UC_NORESPONSE,	"no response from object" },
	{ UC_UNREACHABLE,	"node unreachable" },
	{ DC_NOLINK,		"no link terminate" },
	{ DC_COMPLETE,		"disconnect complete" },
	{ DI_BADIMAGE,		"bad image data in connect" },
	{ DI_SERVMISMATCH,	"cryptographic service mismatch" },
	{ 0,			NULL }
};

static void
print_reason(register int reason)
{
	printf("%s ", tok2str(reason2str, "reason-%d", reason));
}

const char *
dnnum_string(u_short dnaddr)
{
	char *str;
	size_t siz;
	int area = (u_short)(dnaddr & AREAMASK) >> AREASHIFT;
	int node = dnaddr & NODEMASK;

	str = (char *)malloc(siz = sizeof("00.0000"));
	if (str == NULL)
		error("dnnum_string: malloc");
	snprintf(str, siz, "%d.%d", area, node);
	return(str);
}

const char *
dnname_string(u_short dnaddr)
{
#ifdef HAVE_DNET_HTOA
	struct dn_naddr dna;

	dna.a_len = sizeof(short);
	memcpy((char *)dna.a_addr, (char *)&dnaddr, sizeof(short));
	return (strdup(dnet_htoa(&dna)));
#else
	return(dnnum_string(dnaddr));	/* punt */
#endif
}

#ifdef	PRINT_NSPDATA
static void
pdata(u_char *dp, u_int maxlen)
{
	char c;
	u_int x = maxlen;

	while (x-- > 0) {
	    c = *dp++;
	    safeputchar(c);
	}
}
#endif
