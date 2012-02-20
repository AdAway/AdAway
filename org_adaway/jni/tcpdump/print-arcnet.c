/*
 * Copyright (c) 1988, 1989, 1990, 1991, 1992, 1993, 1994, 1995, 1996, 1997
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
 *
 * From: NetBSD: print-arcnet.c,v 1.2 2000/04/24 13:02:28 itojun Exp
 */
#ifndef lint
static const char rcsid[] _U_ =
    "@(#) $Header: /tcpdump/master/tcpdump/print-arcnet.c,v 1.20 2005/04/06 21:32:38 mcr Exp $ (LBL)";
#endif

#ifdef HAVE_CONFIG_H
#include "config.h"
#endif

#include <tcpdump-stdinc.h>

#include <stdio.h>
#include <pcap.h>

#include "interface.h"
#include "arcnet.h"

static int arcnet_encap_print(u_char arctype, const u_char *p,
    u_int length, u_int caplen);

struct tok arctypemap[] = {
	{ ARCTYPE_IP_OLD,	"oldip" },
	{ ARCTYPE_ARP_OLD,	"oldarp" },
	{ ARCTYPE_IP,		"ip" },
	{ ARCTYPE_ARP,		"arp" },
	{ ARCTYPE_REVARP,	"rarp" },
	{ ARCTYPE_ATALK,	"atalk" },
	{ ARCTYPE_BANIAN,	"banyan" },
	{ ARCTYPE_IPX,		"ipx" },
	{ ARCTYPE_INET6,	"ipv6" },
	{ ARCTYPE_DIAGNOSE,	"diag" },
	{ 0, 0 }
};

static inline void
arcnet_print(const u_char *bp, u_int length, int phds, int flag, u_int seqid)
{
	const struct arc_header *ap;
	const char *arctypename;


	ap = (const struct arc_header *)bp;


	if (qflag) {
		(void)printf("%02x %02x %d: ",
			     ap->arc_shost,
			     ap->arc_dhost,
			     length);
		return;
	}

	arctypename = tok2str(arctypemap, "%02x", ap->arc_type);

	if (!phds) {
		(void)printf("%02x %02x %s %d: ",
			     ap->arc_shost, ap->arc_dhost, arctypename,
			     length);
			     return;
	}

	if (flag == 0) {
		(void)printf("%02x %02x %s seqid %04x %d: ",
			ap->arc_shost, ap->arc_dhost, arctypename, seqid,
			length);
			return;
	}

	if (flag & 1)
		(void)printf("%02x %02x %s seqid %04x "
			"(first of %d fragments) %d: ",
			ap->arc_shost, ap->arc_dhost, arctypename, seqid,
			(flag + 3) / 2, length);
	else
		(void)printf("%02x %02x %s seqid %04x "
			"(fragment %d) %d: ",
			ap->arc_shost, ap->arc_dhost, arctypename, seqid,
			flag/2 + 1, length);
}

/*
 * This is the top level routine of the printer.  'p' points
 * to the ARCNET header of the packet, 'h->ts' is the timestamp,
 * 'h->len' is the length of the packet off the wire, and 'h->caplen'
 * is the number of bytes actually captured.
 */
u_int
arcnet_if_print(const struct pcap_pkthdr *h, const u_char *p)
{
	u_int caplen = h->caplen;
	u_int length = h->len;
	const struct arc_header *ap;

	int phds, flag = 0, archdrlen = 0;
	u_int seqid = 0;
	u_char arc_type;

	if (caplen < ARC_HDRLEN) {
		printf("[|arcnet]");
		return (caplen);
	}

	ap = (const struct arc_header *)p;
	arc_type = ap->arc_type;

	switch (arc_type) {
	default:
		phds = 1;
		break;
	case ARCTYPE_IP_OLD:
	case ARCTYPE_ARP_OLD:
	case ARCTYPE_DIAGNOSE:
		phds = 0;
		archdrlen = ARC_HDRLEN;
		break;
	}

	if (phds) {
		if (caplen < ARC_HDRNEWLEN) {
			arcnet_print(p, length, 0, 0, 0);
			printf("[|phds]");
			return (caplen);
		}

		if (ap->arc_flag == 0xff) {
			if (caplen < ARC_HDRNEWLEN_EXC) {
				arcnet_print(p, length, 0, 0, 0);
				printf("[|phds extended]");
				return (caplen);
			}
			flag = ap->arc_flag2;
			seqid = ntohs(ap->arc_seqid2);
			archdrlen = ARC_HDRNEWLEN_EXC;
		} else {
			flag = ap->arc_flag;
			seqid = ntohs(ap->arc_seqid);
			archdrlen = ARC_HDRNEWLEN;
		}
	}


	if (eflag)
		arcnet_print(p, length, phds, flag, seqid);

	/*
	 * Go past the ARCNET header.
	 */
	length -= archdrlen;
	caplen -= archdrlen;
	p += archdrlen;

	if (phds && flag && (flag & 1) == 0) {
		/*
		 * This is a middle fragment.
		 */
		return (archdrlen);
	}

	if (!arcnet_encap_print(arc_type, p, length, caplen))
		default_print(p, caplen);

	return (archdrlen);
}

/*
 * This is the top level routine of the printer.  'p' points
 * to the ARCNET header of the packet, 'h->ts' is the timestamp,
 * 'h->len' is the length of the packet off the wire, and 'h->caplen'
 * is the number of bytes actually captured.  It is quite similar
 * to the non-Linux style printer except that Linux doesn't ever
 * supply packets that look like exception frames, it always supplies
 * reassembled packets rather than raw frames, and headers have an
 * extra "offset" field between the src/dest and packet type.
 */
u_int
arcnet_linux_if_print(const struct pcap_pkthdr *h, const u_char *p)
{
	u_int caplen = h->caplen;
	u_int length = h->len;
	const struct arc_linux_header *ap;

	int archdrlen = 0;
	u_char arc_type;

	if (caplen < ARC_LINUX_HDRLEN) {
		printf("[|arcnet]");
		return (caplen);
	}

	ap = (const struct arc_linux_header *)p;
	arc_type = ap->arc_type;

	switch (arc_type) {
	default:
		archdrlen = ARC_LINUX_HDRNEWLEN;
		if (caplen < ARC_LINUX_HDRNEWLEN) {
			printf("[|arcnet]");
			return (caplen);
		}
		break;
	case ARCTYPE_IP_OLD:
	case ARCTYPE_ARP_OLD:
	case ARCTYPE_DIAGNOSE:
		archdrlen = ARC_LINUX_HDRLEN;
		break;
	}

	if (eflag)
		arcnet_print(p, length, 0, 0, 0);

	/*
	 * Go past the ARCNET header.
	 */
	length -= archdrlen;
	caplen -= archdrlen;
	p += archdrlen;

	if (!arcnet_encap_print(arc_type, p, length, caplen))
		default_print(p, caplen);

	return (archdrlen);
}

/*
 * Prints the packet encapsulated in an ARCnet data field,
 * given the ARCnet system code.
 *
 * Returns non-zero if it can do so, zero if the system code is unknown.
 */


static int
arcnet_encap_print(u_char arctype, const u_char *p,
    u_int length, u_int caplen)
{
	switch (arctype) {

	case ARCTYPE_IP_OLD:
	case ARCTYPE_IP:
	        ip_print(gndo, p, length);
		return (1);

#ifdef INET6
	case ARCTYPE_INET6:
		ip6_print(p, length);
		return (1);
#endif /*INET6*/

	case ARCTYPE_ARP_OLD:
	case ARCTYPE_ARP:
	case ARCTYPE_REVARP:
	  arp_print(gndo, p, length, caplen);
		return (1);

	case ARCTYPE_ATALK:	/* XXX was this ever used? */
		if (vflag)
			fputs("et1 ", stdout);
		atalk_print(p, length);
		return (1);

	case ARCTYPE_IPX:
		ipx_print(p, length);
		return (1);

	default:
		return (0);
	}
}

/*
 * Local Variables:
 * c-style: bsd
 * End:
 */

