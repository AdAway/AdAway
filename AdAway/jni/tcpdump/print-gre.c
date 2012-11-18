/*	$OpenBSD: print-gre.c,v 1.6 2002/10/30 03:04:04 fgsch Exp $	*/

/*
 * Copyright (c) 2002 Jason L. Wright (jason@thought.net)
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. All advertising materials mentioning features or use of this software
 *    must display the following acknowledgement:
 *	This product includes software developed by Jason L. Wright
 * 4. The name of the author may not be used to endorse or promote products
 *    derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE AUTHOR ``AS IS'' AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED.  IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN
 * ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

/*
 * tcpdump filter for GRE - Generic Routing Encapsulation
 * RFC1701 (GRE), RFC1702 (GRE IPv4), and RFC2637 (Enhanced GRE)
 */

#ifndef lint
static const char rcsid[] _U_ =
    "@(#) $Header: /tcpdump/master/tcpdump/print-gre.c,v 1.28 2005/04/06 21:32:39 mcr Exp $ (LBL)";
#endif

#ifdef HAVE_CONFIG_H
#include "config.h"
#endif

#include <tcpdump-stdinc.h>

#include <stdio.h>
#include <string.h>

#include "interface.h"
#include "addrtoname.h"
#include "extract.h"

#include "ip.h"
#include "ethertype.h"

#define	GRE_CP		0x8000		/* checksum present */
#define	GRE_RP		0x4000		/* routing present */
#define	GRE_KP		0x2000		/* key present */
#define	GRE_SP		0x1000		/* sequence# present */
#define	GRE_sP		0x0800		/* source routing */
#define	GRE_RECRS	0x0700		/* recursion count */
#define	GRE_AP		0x0080		/* acknowledgment# present */

struct tok gre_flag_values[] = {
    { GRE_CP, "checksum present"},
    { GRE_RP, "routing present"}, 
    { GRE_KP, "key present"}, 
    { GRE_SP, "sequence# present"}, 
    { GRE_sP, "source routing present"},
    { GRE_RECRS, "recursion count"},
    { GRE_AP, "ack present"},
    { 0, NULL }
};

#define	GRE_VERS_MASK	0x0007		/* protocol version */

/* source route entry types */
#define	GRESRE_IP	0x0800		/* IP */
#define	GRESRE_ASN	0xfffe		/* ASN */

void gre_print_0(const u_char *, u_int);
void gre_print_1(const u_char *, u_int);
void gre_sre_print(u_int16_t, u_int8_t, u_int8_t, const u_char *, u_int);
void gre_sre_ip_print(u_int8_t, u_int8_t, const u_char *, u_int);
void gre_sre_asn_print(u_int8_t, u_int8_t, const u_char *, u_int);

void
gre_print(const u_char *bp, u_int length)
{
	u_int len = length, vers;

	if (len < 2) {
		printf("[|gre]");
		return;
	}
	vers = EXTRACT_16BITS(bp) & GRE_VERS_MASK;
        printf("GREv%u",vers);

        switch(vers) {
        case 0:
            gre_print_0(bp, len);
            break;
        case 1:
            gre_print_1(bp, len);
            break;
	default:
            printf(" ERROR: unknown-version");
            break;
        }
	return;

}

void
gre_print_0(const u_char *bp, u_int length)
{
	u_int len = length;
	u_int16_t flags, prot;

	flags = EXTRACT_16BITS(bp);
        if (vflag)
            printf(", Flags [%s]",
                   bittok2str(gre_flag_values,"none",flags));

	len -= 2;
	bp += 2;

	if (len < 2)
		goto trunc;
	prot = EXTRACT_16BITS(bp);
	len -= 2;
	bp += 2;

	if ((flags & GRE_CP) | (flags & GRE_RP)) {
		if (len < 2)
			goto trunc;
		if (vflag)
			printf(", sum 0x%x", EXTRACT_16BITS(bp));
		bp += 2;
		len -= 2;

		if (len < 2)
			goto trunc;
		printf(", off 0x%x", EXTRACT_16BITS(bp));
		bp += 2;
		len -= 2;
	}

	if (flags & GRE_KP) {
		if (len < 4)
			goto trunc;
		printf(", key=0x%x", EXTRACT_32BITS(bp));
		bp += 4;
		len -= 4;
	}

	if (flags & GRE_SP) {
		if (len < 4)
			goto trunc;
		printf(", seq %u", EXTRACT_32BITS(bp));
		bp += 4;
		len -= 4;
	}

	if (flags & GRE_RP) {
		for (;;) {
			u_int16_t af;
			u_int8_t sreoff;
			u_int8_t srelen;

			if (len < 4)
				goto trunc;
			af = EXTRACT_16BITS(bp);
			sreoff = *(bp + 2);
			srelen = *(bp + 3);
			bp += 4;
			len -= 4;

			if (af == 0 && srelen == 0)
				break;

			gre_sre_print(af, sreoff, srelen, bp, len);

			if (len < srelen)
				goto trunc;
			bp += srelen;
			len -= srelen;
		}
	}

        if (eflag)
            printf(", proto %s (0x%04x)",
                   tok2str(ethertype_values,"unknown",prot),
                   prot);

        printf(", length %u",length);

        if (vflag < 1)
            printf(": "); /* put in a colon as protocol demarc */
        else
            printf("\n\t"); /* if verbose go multiline */

	switch (prot) {
	case ETHERTYPE_IP:
	        ip_print(gndo, bp, len);
		break;
#ifdef INET6
	case ETHERTYPE_IPV6:
		ip6_print(bp, len);
		break;
#endif
	case ETHERTYPE_MPLS:
		mpls_print(bp, len);
		break;
	case ETHERTYPE_IPX:
		ipx_print(bp, len);
		break;
	case ETHERTYPE_ATALK:
		atalk_print(bp, len);
		break;
	case ETHERTYPE_GRE_ISO:
		isoclns_print(bp, len, len);
		break;
	default:
		printf("gre-proto-0x%x", prot);
	}
	return;

trunc:
	printf("[|gre]");
}

void
gre_print_1(const u_char *bp, u_int length)
{
	u_int len = length;
	u_int16_t flags, prot;

	flags = EXTRACT_16BITS(bp);
	len -= 2;
	bp += 2;

	if (vflag)
            printf(", Flags [%s]",
                   bittok2str(gre_flag_values,"none",flags));

	if (len < 2)
		goto trunc;
	prot = EXTRACT_16BITS(bp);
	len -= 2;
	bp += 2;


	if (flags & GRE_KP) {
		u_int32_t k;

		if (len < 4)
			goto trunc;
		k = EXTRACT_32BITS(bp);
		printf(", call %d", k & 0xffff);
		len -= 4;
		bp += 4;
	}

	if (flags & GRE_SP) {
		if (len < 4)
			goto trunc;
		printf(", seq %u", EXTRACT_32BITS(bp));
		bp += 4;
		len -= 4;
	}

	if (flags & GRE_AP) {
		if (len < 4)
			goto trunc;
		printf(", ack %u", EXTRACT_32BITS(bp));
		bp += 4;
		len -= 4;
	}

	if ((flags & GRE_SP) == 0)
		printf(", no-payload");

        if (eflag)
            printf(", proto %s (0x%04x)",
                   tok2str(ethertype_values,"unknown",prot),
                   prot);

        printf(", length %u",length);

        if ((flags & GRE_SP) == 0)
            return;

        if (vflag < 1)
            printf(": "); /* put in a colon as protocol demarc */
        else
            printf("\n\t"); /* if verbose go multiline */

	switch (prot) {
	case ETHERTYPE_PPP:
                ppp_print(bp, len);
		break;
	default:
		printf("gre-proto-0x%x", prot);
		break;
	}
	return;

trunc:
	printf("[|gre]");
}

void
gre_sre_print(u_int16_t af, u_int8_t sreoff, u_int8_t srelen,
    const u_char *bp, u_int len)
{
	switch (af) {
	case GRESRE_IP:
		printf(", (rtaf=ip");
		gre_sre_ip_print(sreoff, srelen, bp, len);
		printf(") ");
		break;
	case GRESRE_ASN:
		printf(", (rtaf=asn");
		gre_sre_asn_print(sreoff, srelen, bp, len);
		printf(") ");
		break;
	default:
		printf(", (rtaf=0x%x) ", af);
	}
}
void
gre_sre_ip_print(u_int8_t sreoff, u_int8_t srelen, const u_char *bp, u_int len)
{
	struct in_addr a;
	const u_char *up = bp;

	if (sreoff & 3) {
		printf(", badoffset=%u", sreoff);
		return;
	}
	if (srelen & 3) {
		printf(", badlength=%u", srelen);
		return;
	}
	if (sreoff >= srelen) {
		printf(", badoff/len=%u/%u", sreoff, srelen);
		return;
	}

	for (;;) {
		if (len < 4 || srelen == 0)
			return;

		memcpy(&a, bp, sizeof(a));
		printf(" %s%s",
		    ((bp - up) == sreoff) ? "*" : "",
		    inet_ntoa(a));

		bp += 4;
		len -= 4;
		srelen -= 4;
	}
}

void
gre_sre_asn_print(u_int8_t sreoff, u_int8_t srelen, const u_char *bp, u_int len)
{
	const u_char *up = bp;

	if (sreoff & 1) {
		printf(", badoffset=%u", sreoff);
		return;
	}
	if (srelen & 1) {
		printf(", badlength=%u", srelen);
		return;
	}
	if (sreoff >= srelen) {
		printf(", badoff/len=%u/%u", sreoff, srelen);
		return;
	}

	for (;;) {
		if (len < 2 || srelen == 0)
			return;

		printf(" %s%x",
		    ((bp - up) == sreoff) ? "*" : "",
		    EXTRACT_16BITS(bp));

		bp += 2;
		len -= 2;
		srelen -= 2;
	}
}
