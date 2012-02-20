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
    "@(#) $Header: /tcpdump/master/tcpdump/print-symantec.c,v 1.4.2.1 2005/07/07 01:24:39 guy Exp $ (LBL)";
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

#include "ether.h"

struct symantec_header {
	u_int8_t  stuff1[6];
	u_int16_t ether_type;
	u_int8_t  stuff2[36];
};

static inline void
symantec_hdr_print(register const u_char *bp, u_int length)
{
	register const struct symantec_header *sp;
	u_int16_t etype;

	sp = (const struct symantec_header *)bp;

	etype = ntohs(sp->ether_type);
	if (!qflag) {
	        if (etype <= ETHERMTU)
		          (void)printf("invalid ethertype %u", etype);
                else 
		          (void)printf("ethertype %s (0x%04x)",
				       tok2str(ethertype_values,"Unknown", etype),
                                       etype);
        } else {
                if (etype <= ETHERMTU)
                          (void)printf("invalid ethertype %u", etype);
                else 
                          (void)printf("%s", tok2str(ethertype_values,"Unknown Ethertype (0x%04x)", etype));  
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
symantec_if_print(const struct pcap_pkthdr *h, const u_char *p)
{
	u_int length = h->len;
	u_int caplen = h->caplen;
	struct symantec_header *sp;
	u_short ether_type;
	u_short extracted_ether_type;

	if (caplen < sizeof (struct symantec_header)) {
		printf("[|symantec]");
		return caplen;
	}

	if (eflag)
		symantec_hdr_print(p, length);

	length -= sizeof (struct symantec_header);
	caplen -= sizeof (struct symantec_header);
	sp = (struct symantec_header *)p;
	p += sizeof (struct symantec_header);

	ether_type = ntohs(sp->ether_type);

	if (ether_type <= ETHERMTU) {
		/* ether_type not known, print raw packet */
		if (!eflag)
			symantec_hdr_print((u_char *)sp, length + sizeof (struct symantec_header));

		if (!suppress_default_print)
			default_print(p, caplen);
	} else if (ether_encap_print(ether_type, p, length, caplen,
	    &extracted_ether_type) == 0) {
		/* ether_type not known, print raw packet */
		if (!eflag)
			symantec_hdr_print((u_char *)sp, length + sizeof (struct symantec_header));

		if (!suppress_default_print)
			default_print(p, caplen);
	} 

	return (sizeof (struct symantec_header));
}
