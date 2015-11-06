/*
 * Copyright (c) 1988, 1989, 1990, 1991, 1993, 1994
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

#define NETDISSECT_REWORKED
#ifdef HAVE_CONFIG_H
#include "config.h"
#endif

#include <tcpdump-stdinc.h>

struct ipcomp {
	uint8_t comp_nxt;	/* Next Header */
	uint8_t comp_flags;	/* Length of data, in 32bit */
	uint16_t comp_cpi;	/* Compression parameter index */
};

#if defined(HAVE_LIBZ) && defined(HAVE_ZLIB_H)
#include <zlib.h>
#endif

#include "interface.h"
#include "extract.h"

int
ipcomp_print(netdissect_options *ndo, register const u_char *bp, int *nhdr _U_)
{
	register const struct ipcomp *ipcomp;
	register const u_char *ep;
	uint16_t cpi;
#if defined(HAVE_LIBZ) && defined(HAVE_ZLIB_H)
	int advance;
#endif

	ipcomp = (struct ipcomp *)bp;
	cpi = EXTRACT_16BITS(&ipcomp->comp_cpi);

	/* 'ep' points to the end of available data. */
	ep = ndo->ndo_snapend;

	if ((u_char *)(ipcomp + 1) >= ep - sizeof(struct ipcomp)) {
		ND_PRINT((ndo, "[|IPCOMP]"));
		goto fail;
	}
	ND_PRINT((ndo, "IPComp(cpi=0x%04x)", cpi));

#if defined(HAVE_LIBZ) && defined(HAVE_ZLIB_H)
	if (1)
		goto fail;

	/*
	 * We may want to decompress the packet here.  Packet buffer
	 * management is a headache (if we decompress, packet will become
	 * larger).
	 */
	if (nhdr)
		*nhdr = ipcomp->comp_nxt;
	advance = sizeof(struct ipcomp);

	ND_PRINT((ndo, ": "));
	return advance;

#endif
fail:
	return -1;
}
