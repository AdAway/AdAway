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

#ifdef INET6

#include <tcpdump-stdinc.h>

#include <string.h>

#include "ip6.h"

#include "interface.h"
#include "addrtoname.h"
#include "extract.h"

int
rt6_print(netdissect_options *ndo, register const u_char *bp, const u_char *bp2 _U_)
{
	register const struct ip6_rthdr *dp;
	register const struct ip6_rthdr0 *dp0;
	register const u_char *ep;
	int i, len;
	register const struct in6_addr *addr;
	const struct in6_addr *last_addr = NULL;

	dp = (struct ip6_rthdr *)bp;
	len = dp->ip6r_len;

	/* 'ep' points to the end of available data. */
	ep = ndo->ndo_snapend;

	ND_TCHECK(dp->ip6r_segleft);

	ND_PRINT((ndo, "srcrt (len=%d", dp->ip6r_len));	/*)*/
	ND_PRINT((ndo, ", type=%d", dp->ip6r_type));
	ND_PRINT((ndo, ", segleft=%d", dp->ip6r_segleft));

	switch (dp->ip6r_type) {
#ifndef IPV6_RTHDR_TYPE_0
#define IPV6_RTHDR_TYPE_0 0
#endif
#ifndef IPV6_RTHDR_TYPE_2
#define IPV6_RTHDR_TYPE_2 2
#endif
	case IPV6_RTHDR_TYPE_0:
	case IPV6_RTHDR_TYPE_2:			/* Mobile IPv6 ID-20 */
		dp0 = (struct ip6_rthdr0 *)dp;

		ND_TCHECK(dp0->ip6r0_reserved);
		if (dp0->ip6r0_reserved || ndo->ndo_vflag) {
			ND_PRINT((ndo, ", rsv=0x%0x",
			    EXTRACT_32BITS(&dp0->ip6r0_reserved)));
		}

		if (len % 2 == 1)
			goto trunc;
		len >>= 1;
		addr = &dp0->ip6r0_addr[0];
		for (i = 0; i < len; i++) {
			if ((u_char *)(addr + 1) > ep)
				goto trunc;

			ND_PRINT((ndo, ", [%d]%s", i, ip6addr_string(ndo, addr)));
			last_addr = addr;
			addr++;
		}
		/*
		 * the destination address used in the pseudo-header is that of the final
		 * destination : the last address of the routing header
		 */
		if (last_addr != NULL) {
			struct ip6_hdr *ip6 = (struct ip6_hdr *)bp2;
			UNALIGNED_MEMCPY(&ip6->ip6_dst, last_addr, sizeof (struct in6_addr));
		}
		/*(*/
		ND_PRINT((ndo, ") "));
		return((dp0->ip6r0_len + 1) << 3);
		break;
	default:
		goto trunc;
		break;
	}

 trunc:
	ND_PRINT((ndo, "[|srcrt]"));
	return -1;
}
#endif /* INET6 */
