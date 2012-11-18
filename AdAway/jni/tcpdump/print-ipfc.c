/*
 * Copyright (c) 1991, 1992, 1993, 1994, 1995, 1996, 1997
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
    "@(#) $Header: /tcpdump/master/tcpdump/print-ipfc.c,v 1.7.2.2 2005/11/13 12:12:59 guy Exp $ (LBL)";
#endif

#ifdef HAVE_CONFIG_H
#include "config.h"
#endif

#include <tcpdump-stdinc.h>

#include <pcap.h>
#include <stdio.h>
#include <string.h>

#include "interface.h"
#include "addrtoname.h"
#include "ethertype.h"

#include "ether.h"
#include "ipfc.h"

/*
 * RFC 2625 IP-over-Fibre Channel.
 */

/* Extract src, dst addresses */
static inline void
extract_ipfc_addrs(const struct ipfc_header *ipfcp, char *ipfcsrc,
    char *ipfcdst)
{
	/*
	 * We assume that, as per RFC 2625, the lower 48 bits of the
	 * source and destination addresses are MAC addresses.
	 */
	memcpy(ipfcdst, (const char *)&ipfcp->ipfc_dhost[2], 6);
	memcpy(ipfcsrc, (const char *)&ipfcp->ipfc_shost[2], 6);
}

/*
 * Print the Network_Header
 */
static inline void
ipfc_hdr_print(register const struct ipfc_header *ipfcp _U_,
	   register u_int length, register const u_char *ipfcsrc,
	   register const u_char *ipfcdst)
{
	const char *srcname, *dstname;

	srcname = etheraddr_string(ipfcsrc);
	dstname = etheraddr_string(ipfcdst);

	/*
	 * XXX - show the upper 16 bits?  Do so only if "vflag" is set?
	 */
	(void) printf("%s %s %d: ", srcname, dstname, length);
}

static void
ipfc_print(const u_char *p, u_int length, u_int caplen)
{
	const struct ipfc_header *ipfcp = (const struct ipfc_header *)p;
	struct ether_header ehdr;
	u_short extracted_ethertype;

	if (caplen < IPFC_HDRLEN) {
		printf("[|ipfc]");
		return;
	}
	/*
	 * Get the network addresses into a canonical form
	 */
	extract_ipfc_addrs(ipfcp, (char *)ESRC(&ehdr), (char *)EDST(&ehdr));

	if (eflag)
		ipfc_hdr_print(ipfcp, length, ESRC(&ehdr), EDST(&ehdr));

	/* Skip over Network_Header */
	length -= IPFC_HDRLEN;
	p += IPFC_HDRLEN;
	caplen -= IPFC_HDRLEN;

	/* Try to print the LLC-layer header & higher layers */
	if (llc_print(p, length, caplen, ESRC(&ehdr), EDST(&ehdr),
	    &extracted_ethertype) == 0) {
		/*
		 * Some kinds of LLC packet we cannot
		 * handle intelligently
		 */
		if (!eflag)
			ipfc_hdr_print(ipfcp, length + IPFC_HDRLEN,
			    ESRC(&ehdr), EDST(&ehdr));
		if (extracted_ethertype) {
			printf("(LLC %s) ",
		etherproto_string(htons(extracted_ethertype)));
		}
		if (!suppress_default_print)
			default_print(p, caplen);
	}
}

/*
 * This is the top level routine of the printer.  'p' points
 * to the Network_Header of the packet, 'h->ts' is the timestamp,
 * 'h->len' is the length of the packet off the wire, and 'h->caplen'
 * is the number of bytes actually captured.
 */
u_int
ipfc_if_print(const struct pcap_pkthdr *h, register const u_char *p)
{
	ipfc_print(p, h->len, h->caplen);

	return (IPFC_HDRLEN);
}
