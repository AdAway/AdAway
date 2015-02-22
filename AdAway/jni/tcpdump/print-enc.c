/*	$OpenBSD: print-enc.c,v 1.7 2002/02/19 19:39:40 millert Exp $	*/

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
    "@(#) $Header: /tcpdump/master/tcpdump/print-enc.c,v 1.6 2008-11-18 07:35:32 guy Exp $ (LBL)";
#endif

#ifdef HAVE_CONFIG_H
#include "config.h"
#endif

#include <tcpdump-stdinc.h>

#include <pcap.h>

#include "interface.h"
#include "extract.h"
#include "addrtoname.h"

#include "enc.h"

#define ENC_PRINT_TYPE(wh, xf, nam) \
	if ((wh) & (xf)) { \
		printf("%s%s", nam, (wh) == (xf) ? "): " : ","); \
		(wh) &= ~(xf); \
	}

u_int
enc_if_print(const struct pcap_pkthdr *h, register const u_char *p)
{
	register u_int length = h->len;
	register u_int caplen = h->caplen;
	int flags;
	const struct enchdr *hdr;

	if (caplen < ENC_HDRLEN) {
		printf("[|enc]");
		goto out;
	}

	hdr = (struct enchdr *)p;
	flags = hdr->flags;
	if (flags == 0)
		printf("(unprotected): ");
	else
		printf("(");
	ENC_PRINT_TYPE(flags, M_AUTH, "authentic");
	ENC_PRINT_TYPE(flags, M_CONF, "confidential");
	/* ENC_PRINT_TYPE(flags, M_TUNNEL, "tunnel"); */
	printf("SPI 0x%08x: ", EXTRACT_32BITS(&hdr->spi));

	length -= ENC_HDRLEN;
	caplen -= ENC_HDRLEN;
	p += ENC_HDRLEN;
	
	switch (hdr->af) {
	case AF_INET:
		ip_print(gndo, p, length);
		break;
#ifdef INET6
	case AF_INET6:
		ip6_print(gndo, p, length);
		break;
#endif /*INET6*/
	}

out:
	return (ENC_HDRLEN);
}


/*
 * Local Variables:
 * c-style: whitesmith
 * c-basic-offset: 8
 * End:
 */
