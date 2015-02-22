/*
 * Copyright (c) 1996, 1997
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
 * Initial contribution from Francis Dupont (francis.dupont@inria.fr)
 */

#ifndef lint
static const char rcsid[] _U_ =
    "@(#) $Header: /tcpdump/master/tcpdump/print-igrp.c,v 1.21 2005-04-20 21:01:56 guy Exp $ (LBL)";
#endif

#ifdef HAVE_CONFIG_H
#include "config.h"
#endif

#include <tcpdump-stdinc.h>

#include <stdio.h>

#include "interface.h"
#include "addrtoname.h"
#include "igrp.h"
#include "ip.h"
#include "extract.h"			/* must come after interface.h */

static void
igrp_entry_print(register struct igrprte *igr, register int is_interior,
    register int is_exterior)
{
	register u_int delay, bandwidth;
	u_int metric, mtu;

	if (is_interior)
		printf(" *.%d.%d.%d", igr->igr_net[0],
		    igr->igr_net[1], igr->igr_net[2]);
	else if (is_exterior)
		printf(" X%d.%d.%d.0", igr->igr_net[0],
		    igr->igr_net[1], igr->igr_net[2]);
	else
		printf(" %d.%d.%d.0", igr->igr_net[0],
		    igr->igr_net[1], igr->igr_net[2]);

	delay = EXTRACT_24BITS(igr->igr_dly);
	bandwidth = EXTRACT_24BITS(igr->igr_bw);
	metric = bandwidth + delay;
	if (metric > 0xffffff)
		metric = 0xffffff;
	mtu = EXTRACT_16BITS(igr->igr_mtu);

	printf(" d=%d b=%d r=%d l=%d M=%d mtu=%d in %d hops",
	    10 * delay, bandwidth == 0 ? 0 : 10000000 / bandwidth,
	    igr->igr_rel, igr->igr_ld, metric,
	    mtu, igr->igr_hct);
}

static const struct tok op2str[] = {
	{ IGRP_UPDATE,		"update" },
	{ IGRP_REQUEST,		"request" },
	{ 0,			NULL }
};

void
igrp_print(register const u_char *bp, u_int length, const u_char *bp2 _U_)
{
	register struct igrphdr *hdr;
	register u_char *cp;
	u_int nint, nsys, next;

	hdr = (struct igrphdr *)bp;
	cp = (u_char *)(hdr + 1);
        (void)printf("igrp:");

	/* Header */
	TCHECK(*hdr);
	nint = EXTRACT_16BITS(&hdr->ig_ni);
	nsys = EXTRACT_16BITS(&hdr->ig_ns);
	next = EXTRACT_16BITS(&hdr->ig_nx);

	(void)printf(" %s V%d edit=%d AS=%d (%d/%d/%d)",
	    tok2str(op2str, "op-#%d", IGRP_OP(hdr->ig_vop)),
	    IGRP_V(hdr->ig_vop),
	    hdr->ig_ed,
	    EXTRACT_16BITS(&hdr->ig_as),
	    nint,
	    nsys,
	    next);

	length -= sizeof(*hdr);
	while (length >= IGRP_RTE_SIZE) {
		if (nint > 0) {
			TCHECK2(*cp, IGRP_RTE_SIZE);
			igrp_entry_print((struct igrprte *)cp, 1, 0);
			--nint;
		} else if (nsys > 0) {
			TCHECK2(*cp, IGRP_RTE_SIZE);
			igrp_entry_print((struct igrprte *)cp, 0, 0);
			--nsys;
		} else if (next > 0) {
			TCHECK2(*cp, IGRP_RTE_SIZE);
			igrp_entry_print((struct igrprte *)cp, 0, 1);
			--next;
		} else {
			(void)printf(" [extra bytes %d]", length);
			break;
		}
		cp += IGRP_RTE_SIZE;
		length -= IGRP_RTE_SIZE;
	}
	if (nint == 0 && nsys == 0 && next == 0)
		return;
trunc:
	fputs(" [|igrp]", stdout);
}
