/*
 * Copyright (c) 1988, 1989, 1990, 1991, 1992, 1993, 1994, 1995, 1996, 1997, 2000
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
    "@(#) $Header: /tcpdump/master/tcpdump/print-ap1394.c,v 1.3.2.1 2005/07/07 01:24:33 guy Exp $ (LBL)";
#endif

#ifdef HAVE_CONFIG_H
#include "config.h"
#endif

#include <tcpdump-stdinc.h>

#include <stdio.h>
#include <pcap.h>

#include "interface.h"
#include "addrtoname.h"
#include "ethertype.h"

/*
 * Structure of a header for Apple's IP-over-IEEE 1384 BPF header.
 */
#define FIREWIRE_EUI64_LEN	8
struct firewire_header {
	u_char  firewire_dhost[FIREWIRE_EUI64_LEN];
	u_char  firewire_shost[FIREWIRE_EUI64_LEN];
	u_short firewire_type;
};

/*
 * Length of that header; note that some compilers may pad
 * "struct firewire_header" to a multiple of 4 bytes, for example, so
 * "sizeof (struct firewire_header)" may not give the right answer.
 */
#define FIREWIRE_HDRLEN		18

static inline void
ap1394_hdr_print(register const u_char *bp, u_int length)
{
	register const struct firewire_header *fp;
	fp = (const struct firewire_header *)bp;

	(void)printf("%s > %s",
		     linkaddr_string(fp->firewire_dhost, FIREWIRE_EUI64_LEN),
		     linkaddr_string(fp->firewire_shost, FIREWIRE_EUI64_LEN));

	if (!qflag) {
		(void)printf(", ethertype %s (0x%04x)",
			       tok2str(ethertype_values,"Unknown", ntohs(fp->firewire_type)),
                               ntohs(fp->firewire_type));	      
        } else {
                (void)printf(", %s", tok2str(ethertype_values,"Unknown Ethertype (0x%04x)", ntohs(fp->firewire_type)));  
        }

	(void)printf(", length %u: ", length);
}

/*
 * This is the top level routine of the printer.  'p' points
 * to the ether header of the packet, 'h->ts' is the timestamp,
 * 'h->len' is the length of the packet off the wire, and 'h->caplen'
 * is the number of bytes actually captured.
 */
u_int
ap1394_if_print(const struct pcap_pkthdr *h, const u_char *p)
{
	u_int length = h->len;
	u_int caplen = h->caplen;
	struct firewire_header *fp;
	u_short ether_type;
	u_short extracted_ether_type;

	if (caplen < FIREWIRE_HDRLEN) {
		printf("[|ap1394]");
		return FIREWIRE_HDRLEN;
	}

	if (eflag)
		ap1394_hdr_print(p, length);

	length -= FIREWIRE_HDRLEN;
	caplen -= FIREWIRE_HDRLEN;
	fp = (struct firewire_header *)p;
	p += FIREWIRE_HDRLEN;

	ether_type = ntohs(fp->firewire_type);

	extracted_ether_type = 0;
	if (ether_encap_print(ether_type, p, length, caplen,
	    &extracted_ether_type) == 0) {
		/* ether_type not known, print raw packet */
		if (!eflag)
			ap1394_hdr_print((u_char *)fp, length + FIREWIRE_HDRLEN);

		if (!suppress_default_print)
			default_print(p, caplen);
	} 

	return FIREWIRE_HDRLEN;
}
