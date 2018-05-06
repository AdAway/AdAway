/*
 * Copyright (c) 1994, 1995, 1996
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
 * Format and print NETBIOS packets.
 * Contributed by Brad Parker (brad@fcr.com).
 */

#ifndef lint
static const char rcsid[] _U_ =
    "@(#) $Header: /tcpdump/master/tcpdump/print-netbios.c,v 1.20 2003-11-16 09:36:29 guy Exp $";
#endif

#ifdef HAVE_CONFIG_H
#include "config.h"
#endif

#include <tcpdump-stdinc.h>

#include <stdlib.h>
#include <stdio.h>
#include <string.h>

#include "interface.h"
#include "addrtoname.h"
#include "netbios.h"
#include "extract.h"

/*
 * Print NETBIOS packets.
 */
void
netbios_print(struct p8022Hdr *nb, u_int length)
{
	if (length < p8022Size) {
		(void)printf(" truncated-netbios %d", length);
		return;
	}

	if (nb->flags == UI) {
	    (void)printf("802.1 UI ");
	} else {
	    (void)printf("802.1 CONN ");
	}

	if ((u_char *)(nb + 1) > snapend) {
		printf(" [|netbios]");
		return;
	}

/*
	netbios_decode(nb, (u_char *)nb + p8022Size, length - p8022Size);
*/
}

#ifdef never
	(void)printf("%s.%d > ",
		     ipxaddr_string(EXTRACT_32BITS(ipx->srcNet), ipx->srcNode),
		     EXTRACT_16BITS(ipx->srcSkt));

	(void)printf("%s.%d:",
		     ipxaddr_string(EXTRACT_32BITS(ipx->dstNet), ipx->dstNode),
		     EXTRACT_16BITS(ipx->dstSkt));

	if ((u_char *)(ipx + 1) > snapend) {
		printf(" [|ipx]");
		return;
	}

	/* take length from ipx header */
	length = EXTRACT_16BITS(&ipx->length);

	ipx_decode(ipx, (u_char *)ipx + ipxSize, length - ipxSize);
#endif

