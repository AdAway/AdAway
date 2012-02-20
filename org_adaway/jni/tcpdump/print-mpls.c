/*
 * Copyright (C) 2001 WIDE Project.  All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. Neither the name of the project nor the names of its contributors
 *    may be used to endorse or promote products derived from this software
 *    without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE PROJECT AND CONTRIBUTORS ``AS IS'' AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE PROJECT OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS
 * OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
 * OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 */

#ifndef lint
static const char rcsid[] _U_ =
    "@(#) $Header: /tcpdump/master/tcpdump/print-mpls.c,v 1.13.2.1 2005/07/05 09:39:29 hannes Exp $ (LBL)";
#endif

#ifdef HAVE_CONFIG_H
#include "config.h"
#endif

#include <tcpdump-stdinc.h>

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "addrtoname.h"
#include "interface.h"
#include "extract.h"			/* must come after interface.h */
#include "mpls.h"

static const char *mpls_labelname[] = {
/*0*/	"IPv4 explicit NULL", "router alert", "IPv6 explicit NULL",
	"implicit NULL", "rsvd",
/*5*/	"rsvd", "rsvd", "rsvd", "rsvd", "rsvd",
/*10*/	"rsvd", "rsvd", "rsvd", "rsvd", "rsvd",
/*15*/	"rsvd",
};

/*
 * RFC3032: MPLS label stack encoding
 */
void
mpls_print(const u_char *bp, u_int length)
{
	const u_char *p;
	u_int32_t label_entry;
        u_int16_t label_stack_depth = 0;

	p = bp;
	printf("MPLS");
	do {
		TCHECK2(*p, sizeof(label_entry));
		label_entry = EXTRACT_32BITS(p);
		printf("%s(label %u",
                       label_stack_depth ? "\n\t" : " ",
                       MPLS_LABEL(label_entry));
                label_stack_depth++;
		if (vflag &&
		    MPLS_LABEL(label_entry) < sizeof(mpls_labelname) / sizeof(mpls_labelname[0]))
			printf(" (%s)", mpls_labelname[MPLS_LABEL(label_entry)]);
		printf(", exp %u", MPLS_EXP(label_entry));
		if (MPLS_STACK(label_entry))
			printf(", [S]");
		printf(", ttl %u)", MPLS_TTL(label_entry));

		p += sizeof(label_entry);
	} while (!MPLS_STACK(label_entry));

	switch (MPLS_LABEL(label_entry)) {
	case 0:	/* IPv4 explicit NULL label */
        case 3:	/* IPv4 implicit NULL label */
                if (vflag>0) {
                        printf("\n\t");
                        ip_print(gndo, p, length - (p - bp));
                }
                else printf(", IP, length: %u",length);
		break;
#ifdef INET6
	case 2:	/* IPv6 explicit NULL label */
                if (vflag>0) {
                        printf("\n\t");
                        ip6_print(p, length - (p - bp));
                }
                else printf(", IPv6, length: %u",length);
		break;
#endif
	default:
		/*
		 * Generally there's no indication of protocol in MPLS label
		 * encoding, however draft-hsmit-isis-aal5mux-00.txt describes
                 * a technique that looks at the first payload byte if the BOS (bottom of stack)
                 * bit is set and tries to determine the network layer protocol
                 * 0x45-0x4f is IPv4
                 * 0x60-0x6f is IPv6
                 * 0x81-0x83 is OSI (CLNP,ES-IS,IS-IS)
                 * this technique is sometimes known as NULL encapsulation
                 * and decoding is particularly useful for control-plane traffic [BGP]
                 * which cisco by default sends MPLS encapsulated
		 */

                if (MPLS_STACK(label_entry)) { /* only do this if the stack bit is set */
                    switch(*p) {
                    case 0x45:
                    case 0x46:
                    case 0x47:
                    case 0x48:
                    case 0x49:
                    case 0x4a:
                    case 0x4b:
                    case 0x4c:
                    case 0x4d:
                    case 0x4e:
                    case 0x4f:
		        if (vflag>0) {
                            printf("\n\t");
                            ip_print(gndo, p, length - (p - bp));
			    }
                        else printf(", IP, length: %u",length);
                        break;
#ifdef INET6
                    case 0x60:
                    case 0x61:
                    case 0x62:
                    case 0x63:
                    case 0x64:
                    case 0x65:
                    case 0x66:
                    case 0x67:
                    case 0x68:
                    case 0x69:
                    case 0x6a:
                    case 0x6b:
                    case 0x6c:
                    case 0x6d:
                    case 0x6e:
                    case 0x6f:
		        if (vflag>0) {
                            printf("\n\t");
                            ip6_print(p, length - (p - bp));
                            }
			else printf(", IPv6, length: %u",length);
                        break;
#endif
                    case 0x81:
                    case 0x82:
                    case 0x83:
		        if (vflag>0) {
                            printf("\n\t");
                            isoclns_print(p, length - (p - bp), length - (p - bp));
			    }
			else printf(", OSI, length: %u",length);
                        break;
                    default:
                        /* ok bail out - we did not figure out what it is*/
                        break;
                    }
                }
                return;
	}

trunc:
	printf("[|MPLS]");
}


/*
 * Local Variables:
 * c-style: whitesmith
 * c-basic-offset: 8
 * End:
 */
