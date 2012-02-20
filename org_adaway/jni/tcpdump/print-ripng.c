/*
 * Copyright (c) 1989, 1990, 1991, 1993, 1994
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
    "@(#) $Header: /tcpdump/master/tcpdump/print-ripng.c,v 1.18 2005/01/04 00:15:54 guy Exp $";
#endif

#ifdef HAVE_CONFIG_H
#include "config.h"
#endif

#ifdef INET6

#include <tcpdump-stdinc.h>
#include <stdio.h>

#include "route6d.h"
#include "interface.h"
#include "addrtoname.h"
#include "extract.h"

#if !defined(IN6_IS_ADDR_UNSPECIFIED) && !defined(_MSC_VER) /* MSVC inline */
static int IN6_IS_ADDR_UNSPECIFIED(const struct in6_addr *addr)
{
    static const struct in6_addr in6addr_any;        /* :: */
    return (memcmp(addr, &in6addr_any, sizeof(*addr)) == 0);
}
#endif

static int
rip6_entry_print(register const struct netinfo6 *ni, int metric)
{
	int l;
	l = printf("%s/%d", ip6addr_string(&ni->rip6_dest), ni->rip6_plen);
	if (ni->rip6_tag)
		l += printf(" [%d]", EXTRACT_16BITS(&ni->rip6_tag));
	if (metric)
		l += printf(" (%d)", ni->rip6_metric);
	return l;
}

void
ripng_print(const u_char *dat, unsigned int length)
{
	register const struct rip6 *rp = (struct rip6 *)dat;
	register const struct netinfo6 *ni;
	register u_int amt;
	register u_int i;
	int j;
	int trunc;

	if (snapend < dat)
		return;
	amt = snapend - dat;
	i = min(length, amt);
	if (i < (sizeof(struct rip6) - sizeof(struct netinfo6)))
		return;
	i -= (sizeof(struct rip6) - sizeof(struct netinfo6));

	switch (rp->rip6_cmd) {

	case RIP6_REQUEST:
		j = length / sizeof(*ni);
		if (j == 1
		    &&  rp->rip6_nets->rip6_metric == HOPCNT_INFINITY6
		    &&  IN6_IS_ADDR_UNSPECIFIED(&rp->rip6_nets->rip6_dest)) {
			printf(" ripng-req dump");
			break;
		}
		if (j * sizeof(*ni) != length - 4)
			printf(" ripng-req %d[%u]:", j, length);
		else
			printf(" ripng-req %d:", j);
		trunc = ((i / sizeof(*ni)) * sizeof(*ni) != i);
		for (ni = rp->rip6_nets; i >= sizeof(*ni);
		    i -= sizeof(*ni), ++ni) {
			if (vflag > 1)
				printf("\n\t");
			else
				printf(" ");
			rip6_entry_print(ni, 0);
		}
		break;
	case RIP6_RESPONSE:
		j = length / sizeof(*ni);
		if (j * sizeof(*ni) != length - 4)
			printf(" ripng-resp %d[%u]:", j, length);
		else
			printf(" ripng-resp %d:", j);
		trunc = ((i / sizeof(*ni)) * sizeof(*ni) != i);
		for (ni = rp->rip6_nets; i >= sizeof(*ni);
		    i -= sizeof(*ni), ++ni) {
			if (vflag > 1)
				printf("\n\t");
			else
				printf(" ");
			rip6_entry_print(ni, ni->rip6_metric);
		}
		if (trunc)
			printf("[|ripng]");
		break;
	default:
		printf(" ripng-%d ?? %u", rp->rip6_cmd, length);
		break;
	}
	if (rp->rip6_vers != RIP6_VERSION)
		printf(" [vers %d]", rp->rip6_vers);
}
#endif /* INET6 */
