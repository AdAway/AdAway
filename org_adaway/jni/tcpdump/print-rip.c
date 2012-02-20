/*
 * Copyright (c) 1989, 1990, 1991, 1993, 1994, 1996
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
    "@(#) $Header: /tcpdump/master/tcpdump/print-rip.c,v 1.57 2003/11/16 09:36:34 guy Exp $ (LBL)";
#endif

#ifdef HAVE_CONFIG_H
#include "config.h"
#endif

#include <tcpdump-stdinc.h>

#include <stdio.h>
#include <string.h>

#include "interface.h"
#include "addrtoname.h"
#include "extract.h"			/* must come after interface.h */

struct rip {
	u_int8_t rip_cmd;		/* request/response */
	u_int8_t rip_vers;		/* protocol version # */
	u_int8_t unused[2];		/* unused */
};

#define	RIPCMD_REQUEST		1	/* want info */
#define	RIPCMD_RESPONSE		2	/* responding to request */
#define	RIPCMD_TRACEON		3	/* turn tracing on */
#define	RIPCMD_TRACEOFF		4	/* turn it off */
#define	RIPCMD_POLL		5	/* want info from everybody */
#define	RIPCMD_POLLENTRY	6	/* poll for entry */

static const struct tok rip_cmd_values[] = {
    { RIPCMD_REQUEST,	        "Request" },
    { RIPCMD_RESPONSE,	        "Response" },
    { RIPCMD_TRACEON,	        "Trace on" },
    { RIPCMD_TRACEOFF,	        "Trace off" },
    { RIPCMD_POLL,	        "Poll" },
    { RIPCMD_POLLENTRY,	        "Poll Entry" },
    { 0, NULL}
};

#define RIP_AUTHLEN  16
#define RIP_ROUTELEN 20

/*
 * rfc 1723
 * 
 *  0                   1                   2                   3 3
 *  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * | Command (1)   | Version (1)   |           unused              |
 * +---------------+---------------+-------------------------------+
 * | Address Family Identifier (2) |        Route Tag (2)          |
 * +-------------------------------+-------------------------------+
 * |                         IP Address (4)                        |
 * +---------------------------------------------------------------+
 * |                         Subnet Mask (4)                       |
 * +---------------------------------------------------------------+
 * |                         Next Hop (4)                          |
 * +---------------------------------------------------------------+
 * |                         Metric (4)                            |
 * +---------------------------------------------------------------+
 *
 */

struct rip_netinfo {
	u_int16_t rip_family;
	u_int16_t rip_tag;
	u_int32_t rip_dest;
	u_int32_t rip_dest_mask;
	u_int32_t rip_router;
	u_int32_t rip_metric;		/* cost of route */
};

static void
rip_entry_print_v1(register const struct rip_netinfo *ni)
{
	register u_short family;

	/* RFC 1058 */
	family = EXTRACT_16BITS(&ni->rip_family);
	if (family != AF_INET) {
		printf("\n\t AFI: %u:", family);
                print_unknown_data((u_int8_t *)&ni->rip_family,"\n\t  ",RIP_ROUTELEN);
		return;
	}
	if (EXTRACT_16BITS(&ni->rip_tag) ||
	    EXTRACT_32BITS(&ni->rip_dest_mask) ||
	    EXTRACT_32BITS(&ni->rip_router)) {
		/* MBZ fields not zero */
                print_unknown_data((u_int8_t *)&ni->rip_family,"\n\t  ",RIP_ROUTELEN);
		return;
	} /* AF_INET */
	printf("\n\t  %s, metric: %u",
               ipaddr_string(&ni->rip_dest),
	       EXTRACT_32BITS(&ni->rip_metric));
}

static void
rip_entry_print_v2(register const struct rip_netinfo *ni)
{
	register u_char *p;
	register u_short family;
	u_char buf[RIP_AUTHLEN];

	family = EXTRACT_16BITS(&ni->rip_family);
	if (family == 0xFFFF) { /* 16 bytes authentication ? */
                if (EXTRACT_16BITS(&ni->rip_tag) == 2) { /* simple text authentication ? */
			memcpy(buf, &ni->rip_dest, sizeof(buf));
			buf[sizeof(buf)-1] = '\0';
			for (p = buf; *p; p++) {
				if (!isprint(*p))
					break;
			}
                        printf("\n\t  Simple Text Authentication data: %s", buf);
                } else {
			printf("\n\t  Unknown (%u) Authentication data:",
			       EXTRACT_16BITS(&ni->rip_tag));
                        print_unknown_data((u_int8_t *)&ni->rip_dest,"\n\t  ",RIP_AUTHLEN);
		}
	} else if (family != AF_INET) {
		printf("\n\t  AFI: %u", family);
                print_unknown_data((u_int8_t *)&ni->rip_tag,"\n\t  ",RIP_ROUTELEN-2);
		return;
	} else { /* AF_INET */
		printf("\n\t  AFI: IPv4: %15s/%-2d, tag 0x%04x, metric: %u, next-hop: ",
                       ipaddr_string(&ni->rip_dest),
		       mask2plen(EXTRACT_32BITS(&ni->rip_dest_mask)),
                       EXTRACT_16BITS(&ni->rip_tag),
                       EXTRACT_32BITS(&ni->rip_metric));
		if (EXTRACT_32BITS(&ni->rip_router))
			printf("%s", ipaddr_string(&ni->rip_router));
                else
                    printf("self");
	}
}

void
rip_print(const u_char *dat, u_int length)
{
	register const struct rip *rp;
	register const struct rip_netinfo *ni;
	register u_int i, j;
	register int trunc;

	if (snapend < dat) {
		printf(" [|rip]");
		return;
	}
	i = snapend - dat;
	if (i > length)
		i = length;
	if (i < sizeof(*rp)) {
		printf(" [|rip]");
		return;
	}
	i -= sizeof(*rp);

	rp = (struct rip *)dat;

        printf("%sRIPv%u",
               (vflag >= 1) ? "\n\t" : "",
               rp->rip_vers);

	switch (rp->rip_vers) {
	case 0:
		/*
		 * RFC 1058.
		 *
		 * XXX - RFC 1058 says
		 *
		 * 0  Datagrams whose version number is zero are to be ignored.
		 *    These are from a previous version of the protocol, whose
		 *    packet format was machine-specific.
		 *
		 * so perhaps we should just dump the packet, in hex.
		 */
                print_unknown_data((u_int8_t *)&rp->rip_cmd,"\n\t",length);
		break;
	default:
                /* dump version and lets see if we know the commands name*/
                printf(", %s, length: %u",
                       tok2str(rip_cmd_values,
                               "unknown command (%u)",
                               rp->rip_cmd),
                       length);

                if (vflag < 1)
                    return;

		switch (rp->rip_cmd) {
		case RIPCMD_RESPONSE:
			j = length / sizeof(*ni);
                        printf(", routes: %u",j);
			trunc = (i / sizeof(*ni)) != j;
			ni = (struct rip_netinfo *)(rp + 1);
			for (; i >= sizeof(*ni); ++ni) {
				if (rp->rip_vers == 1)
					rip_entry_print_v1(ni);
				else if (rp->rip_vers == 2)
					rip_entry_print_v2(ni);
                                else
                                    break;
				i -= sizeof(*ni);
			}
			if (trunc)
				printf("[|rip]");
			break;

		case RIPCMD_REQUEST:
		case RIPCMD_TRACEOFF:
		case RIPCMD_POLL:
		case RIPCMD_POLLENTRY:
			break;

		case RIPCMD_TRACEON:
                    /* fall through */
	        default:
                    if (vflag <= 1) {
                        if(!print_unknown_data((u_int8_t *)rp,"\n\t",length))
                            return;
                    }
                    break;
                }
                /* do we want to see an additionally hexdump ? */
                if (vflag> 1) {
                    if(!print_unknown_data((u_int8_t *)rp,"\n\t",length))
                        return;
                }
        }
}


