/*
 * Copyright (c) 1990, 1991, 1993, 1994, 1995, 1996
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
    "@(#) $Header: /tcpdump/master/tcpdump/print-pflog.c,v 1.13.2.4 2007/09/13 17:18:10 gianluca Exp $ (LBL)";
#endif

#ifdef HAVE_CONFIG_H
#include "config.h"
#endif

#ifndef HAVE_NET_PFVAR_H
#error "No pf headers available"
#endif

#include <sys/types.h>
#ifndef WIN32
#include <sys/socket.h>
#endif
#include <net/if.h>
#include <net/pfvar.h>
#include <net/if_pflog.h>



#include <tcpdump-stdinc.h>

#include <stdio.h>
#include <pcap.h>

#include "interface.h"
#include "addrtoname.h"

static struct tok pf_reasons[] = {
	{ 0,	"0(match)" },
	{ 1,	"1(bad-offset)" },
	{ 2,	"2(fragment)" },
	{ 3,	"3(short)" },
	{ 4,	"4(normalize)" },
	{ 5,	"5(memory)" },
	{ 6,	"6(bad-timestamp)" },
	{ 7,	"7(congestion)" },
	{ 8,	"8(ip-option)" },
	{ 9,	"9(proto-cksum)" },
	{ 10,	"10(state-mismatch)" },
	{ 11,	"11(state-insert)" },
	{ 12,	"12(state-limit)" },
	{ 13,	"13(src-limit)" },
	{ 14,	"14(synproxy)" },
	{ 0,	NULL }
};

static struct tok pf_actions[] = {
	{ PF_PASS,		"pass" },
	{ PF_DROP,		"block" },
	{ PF_SCRUB,		"scrub" },
	{ PF_NAT,		"nat" },
	{ PF_NONAT,		"nat" },
	{ PF_BINAT,		"binat" },
	{ PF_NOBINAT,		"binat" },
	{ PF_RDR,		"rdr" },
	{ PF_NORDR,		"rdr" },
	{ PF_SYNPROXY_DROP,	"synproxy-drop" },
	{ 0,			NULL }
};

static struct tok pf_directions[] = {
	{ PF_INOUT,	"in/out" },
	{ PF_IN,	"in" },
	{ PF_OUT,	"out" },
	{ 0,		NULL }
};

/* For reading capture files on other systems */
#define	OPENBSD_AF_INET		2
#define	OPENBSD_AF_INET6	24

static void
pflog_print(const struct pfloghdr *hdr)
{
	u_int32_t rulenr, subrulenr;

	rulenr = ntohl(hdr->rulenr);
	subrulenr = ntohl(hdr->subrulenr);
	if (subrulenr == (u_int32_t)-1)
		printf("rule %u/", rulenr);
	else
		printf("rule %u.%s.%u/", rulenr, hdr->ruleset, subrulenr);

	printf("%s: %s %s on %s: ",
	    tok2str(pf_reasons, "unkn(%u)", hdr->reason),
	    tok2str(pf_actions, "unkn(%u)", hdr->action),
	    tok2str(pf_directions, "unkn(%u)", hdr->dir),
	    hdr->ifname);
}

u_int
pflog_if_print(const struct pcap_pkthdr *h, register const u_char *p)
{
	u_int length = h->len;
	u_int hdrlen;
	u_int caplen = h->caplen;
	const struct pfloghdr *hdr;
	u_int8_t af;

	/* check length */
	if (caplen < sizeof(u_int8_t)) {
		printf("[|pflog]");
		return (caplen);
	}

#define MIN_PFLOG_HDRLEN	45
	hdr = (struct pfloghdr *)p;
	if (hdr->length < MIN_PFLOG_HDRLEN) {
		printf("[pflog: invalid header length!]");
		return (hdr->length);	/* XXX: not really */
	}
	hdrlen = BPF_WORDALIGN(hdr->length);

	if (caplen < hdrlen) {
		printf("[|pflog]");
		return (hdrlen);	/* XXX: true? */
	}

	/* print what we know */
	hdr = (struct pfloghdr *)p;
	TCHECK(*hdr);
	if (eflag)
		pflog_print(hdr);
	
	/* skip to the real packet */
	af = hdr->af;
	length -= hdrlen;
	caplen -= hdrlen;
	p += hdrlen;
	switch (af) {

		case AF_INET:
#if OPENBSD_AF_INET != AF_INET
		case OPENBSD_AF_INET:		/* XXX: read pcap files */
#endif
		        ip_print(gndo, p, length);
			break;

#ifdef INET6
		case AF_INET6:
#if OPENBSD_AF_INET6 != AF_INET6
		case OPENBSD_AF_INET6:		/* XXX: read pcap files */
#endif
			ip6_print(p, length);
			break;
#endif

	default:
		/* address family not handled, print raw packet */
		if (!eflag)
			pflog_print(hdr);
		if (!suppress_default_print)
			default_print(p, caplen);
	}
	
	return (hdrlen);
trunc:
	printf("[|pflog]");
	return (hdrlen);
}

/*
 * Local Variables:
 * c-style: whitesmith
 * c-basic-offset: 8
 * End:
 */
