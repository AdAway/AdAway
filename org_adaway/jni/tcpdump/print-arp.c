/*
 * Copyright (c) 1988, 1989, 1990, 1991, 1992, 1993, 1994, 1995, 1996, 1997
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
    "@(#) $Header: /tcpdump/master/tcpdump/print-arp.c,v 1.64 2004/04/30 16:42:14 mcr Exp $ (LBL)";
#endif

#ifdef HAVE_CONFIG_H
#include "config.h"
#endif

#include <tcpdump-stdinc.h>

#include <stdio.h>
#include <string.h>

#include "netdissect.h"
#include "addrtoname.h"
#include "ether.h"
#include "ethertype.h"
#include "extract.h"			/* must come after interface.h */

/*
 * Address Resolution Protocol.
 *
 * See RFC 826 for protocol description.  ARP packets are variable
 * in size; the arphdr structure defines the fixed-length portion.
 * Protocol type values are the same as those for 10 Mb/s Ethernet.
 * It is followed by the variable-sized fields ar_sha, arp_spa,
 * arp_tha and arp_tpa in that order, according to the lengths
 * specified.  Field names used correspond to RFC 826.
 */
struct	arp_pkthdr {
	u_short	ar_hrd;		/* format of hardware address */
#define ARPHRD_ETHER 	1	/* ethernet hardware format */
#define ARPHRD_IEEE802	6	/* token-ring hardware format */
#define ARPHRD_ARCNET	7	/* arcnet hardware format */
#define ARPHRD_FRELAY 	15	/* frame relay hardware format */
#define ARPHRD_STRIP 	23	/* Ricochet Starmode Radio hardware format */
#define ARPHRD_IEEE1394	24	/* IEEE 1394 (FireWire) hardware format */
	u_short	ar_pro;		/* format of protocol address */
	u_char	ar_hln;		/* length of hardware address */
	u_char	ar_pln;		/* length of protocol address */
	u_short	ar_op;		/* one of: */
#define	ARPOP_REQUEST	1	/* request to resolve address */
#define	ARPOP_REPLY	2	/* response to previous request */
#define	ARPOP_REVREQUEST 3	/* request protocol address given hardware */
#define	ARPOP_REVREPLY	4	/* response giving protocol address */
#define ARPOP_INVREQUEST 8 	/* request to identify peer */
#define ARPOP_INVREPLY	9	/* response identifying peer */
/*
 * The remaining fields are variable in size,
 * according to the sizes above.
 */
#ifdef COMMENT_ONLY
	u_char	ar_sha[];	/* sender hardware address */
	u_char	ar_spa[];	/* sender protocol address */
	u_char	ar_tha[];	/* target hardware address */
	u_char	ar_tpa[];	/* target protocol address */
#endif
#define ar_sha(ap)	(((const u_char *)((ap)+1))+0)
#define ar_spa(ap)	(((const u_char *)((ap)+1))+  (ap)->ar_hln)
#define ar_tha(ap)	(((const u_char *)((ap)+1))+  (ap)->ar_hln+(ap)->ar_pln)
#define ar_tpa(ap)	(((const u_char *)((ap)+1))+2*(ap)->ar_hln+(ap)->ar_pln)
};

#define ARP_HDRLEN	8

#define HRD(ap) EXTRACT_16BITS(&(ap)->ar_hrd)
#define HLN(ap) ((ap)->ar_hln)
#define PLN(ap) ((ap)->ar_pln)
#define OP(ap)  EXTRACT_16BITS(&(ap)->ar_op)
#define PRO(ap) EXTRACT_16BITS(&(ap)->ar_pro)
#define SHA(ap) (ar_sha(ap))
#define SPA(ap) (ar_spa(ap))
#define THA(ap) (ar_tha(ap))
#define TPA(ap) (ar_tpa(ap))

/*
 * ATM Address Resolution Protocol.
 *
 * See RFC 2225 for protocol description.  ATMARP packets are similar
 * to ARP packets, except that there are no length fields for the
 * protocol address - instead, there are type/length fields for
 * the ATM number and subaddress - and the hardware addresses consist
 * of an ATM number and an ATM subaddress.
 */
struct	atmarp_pkthdr {
	u_short	aar_hrd;	/* format of hardware address */
#define ARPHRD_ATM2225	19	/* ATM (RFC 2225) */
	u_short	aar_pro;	/* format of protocol address */
	u_char	aar_shtl;	/* length of source ATM number */
	u_char	aar_sstl;	/* length of source ATM subaddress */
#define ATMARP_IS_E164	0x40	/* bit in type/length for E.164 format */
#define ATMARP_LEN_MASK	0x3F	/* length of {sub}address in type/length */
	u_short	aar_op;		/* same as regular ARP */
#define ATMARPOP_NAK	10	/* NAK */
	u_char	aar_spln;	/* length of source protocol address */
	u_char	aar_thtl;	/* length of target ATM number */
	u_char	aar_tstl;	/* length of target ATM subaddress */
	u_char	aar_tpln;	/* length of target protocol address */
/*
 * The remaining fields are variable in size,
 * according to the sizes above.
 */
#ifdef COMMENT_ONLY
	u_char	aar_sha[];	/* source ATM number */
	u_char	aar_ssa[];	/* source ATM subaddress */
	u_char	aar_spa[];	/* sender protocol address */
	u_char	aar_tha[];	/* target ATM number */
	u_char	aar_tsa[];	/* target ATM subaddress */
	u_char	aar_tpa[];	/* target protocol address */
#endif

#define ATMHRD(ap)  EXTRACT_16BITS(&(ap)->aar_hrd)
#define ATMSHLN(ap) ((ap)->aar_shtl & ATMARP_LEN_MASK)
#define ATMSSLN(ap) ((ap)->aar_sstl & ATMARP_LEN_MASK)
#define ATMSPLN(ap) ((ap)->aar_spln)
#define ATMOP(ap)   EXTRACT_16BITS(&(ap)->aar_op)
#define ATMPRO(ap)  EXTRACT_16BITS(&(ap)->aar_pro)
#define ATMTHLN(ap) ((ap)->aar_thtl & ATMARP_LEN_MASK)
#define ATMTSLN(ap) ((ap)->aar_tstl & ATMARP_LEN_MASK)
#define ATMTPLN(ap) ((ap)->aar_tpln)
#define aar_sha(ap)	((const u_char *)((ap)+1))
#define aar_ssa(ap)	(aar_sha(ap) + ATMSHLN(ap))
#define aar_spa(ap)	(aar_ssa(ap) + ATMSSLN(ap))
#define aar_tha(ap)	(aar_spa(ap) + ATMSPLN(ap))
#define aar_tsa(ap)	(aar_tha(ap) + ATMTHLN(ap))
#define aar_tpa(ap)	(aar_tsa(ap) + ATMTSLN(ap))
};

#define ATMSHA(ap) (aar_sha(ap))
#define ATMSSA(ap) (aar_ssa(ap))
#define ATMSPA(ap) (aar_spa(ap))
#define ATMTHA(ap) (aar_tha(ap))
#define ATMTSA(ap) (aar_tsa(ap))
#define ATMTPA(ap) (aar_tpa(ap))

static u_char ezero[6];

static void
atmarp_addr_print(netdissect_options *ndo,
		  const u_char *ha, u_int ha_len, const u_char *srca,
    u_int srca_len)
{
	if (ha_len == 0)
		ND_PRINT((ndo, "<No address>"));
	else {
		ND_PRINT((ndo, "%s", linkaddr_string(ha, ha_len)));
		if (srca_len != 0) 
			ND_PRINT((ndo, ",%s",
				  linkaddr_string(srca, srca_len)));
	}
}

static void
atmarp_print(netdissect_options *ndo,
	     const u_char *bp, u_int length, u_int caplen)
{
	const struct atmarp_pkthdr *ap;
	u_short pro, hrd, op;

	ap = (const struct atmarp_pkthdr *)bp;
	ND_TCHECK(*ap);

	hrd = ATMHRD(ap);
	pro = ATMPRO(ap);
	op = ATMOP(ap);

	if (!ND_TTEST2(*aar_tpa(ap), ATMTPLN(ap))) {
		ND_PRINT((ndo, "truncated-atmarp"));
		ND_DEFAULTPRINT((const u_char *)ap, length);
		return;
	}

	if ((pro != ETHERTYPE_IP && pro != ETHERTYPE_TRAIL) ||
	    ATMSPLN(ap) != 4 || ATMTPLN(ap) != 4) {
		ND_PRINT((ndo, "atmarp-#%d for proto #%d (%d/%d) hardware #%d",
			  op, pro, ATMSPLN(ap), ATMTPLN(ap), hrd));
		return;
	}
	if (pro == ETHERTYPE_TRAIL)
		ND_PRINT((ndo, "trailer-"));
	switch (op) {

	case ARPOP_REQUEST:
		ND_PRINT((ndo, "arp who-has %s", ipaddr_string(ATMTPA(ap))));
		if (ATMTHLN(ap) != 0) {
			ND_PRINT((ndo, " ("));
			atmarp_addr_print(ndo, ATMTHA(ap), ATMTHLN(ap),
			    ATMTSA(ap), ATMTSLN(ap));
			ND_PRINT((ndo, ")"));
		}
		ND_PRINT((ndo, " tell %s", ipaddr_string(ATMSPA(ap))));
		break;

	case ARPOP_REPLY:
		ND_PRINT((ndo, "arp reply %s", ipaddr_string(ATMSPA(ap))));
		ND_PRINT((ndo, " is-at "));
		atmarp_addr_print(ndo, ATMSHA(ap), ATMSHLN(ap), ATMSSA(ap),
		    ATMSSLN(ap));
		break;

	case ARPOP_INVREQUEST:
		ND_PRINT((ndo, "invarp who-is "));
		atmarp_addr_print(ndo, ATMTHA(ap), ATMTHLN(ap), ATMTSA(ap),
		    ATMTSLN(ap));
		ND_PRINT((ndo, " tell "));
		atmarp_addr_print(ndo, ATMSHA(ap), ATMSHLN(ap), ATMSSA(ap),
		    ATMSSLN(ap));
		break;

	case ARPOP_INVREPLY:
		ND_PRINT((ndo, "invarp reply "));
		atmarp_addr_print(ndo, ATMSHA(ap), ATMSHLN(ap), ATMSSA(ap),
		    ATMSSLN(ap));
		ND_PRINT((ndo, " at %s", ipaddr_string(ATMSPA(ap))));
		break;

	case ATMARPOP_NAK:
		ND_PRINT((ndo, "nak reply for %s",
			  ipaddr_string(ATMSPA(ap))));
		break;

	default:
		ND_PRINT((ndo, "atmarp-#%d", op));
		ND_DEFAULTPRINT((const u_char *)ap, caplen);
		return;
	}
	return;
trunc:
	ND_PRINT((ndo, "[|atmarp]"));
}

void
arp_print(netdissect_options *ndo,
	  const u_char *bp, u_int length, u_int caplen)
{
	const struct arp_pkthdr *ap;
	u_short pro, hrd, op;

	ap = (const struct arp_pkthdr *)bp;
	ND_TCHECK(*ap);
	hrd = HRD(ap);
	if (hrd == ARPHRD_ATM2225) {
	        atmarp_print(ndo, bp, length, caplen);
		return;
	}
	pro = PRO(ap);
	op = OP(ap);

	if (!ND_TTEST2(*ar_tpa(ap), PLN(ap))) {
		ND_PRINT((ndo, "truncated-arp"));
		ND_DEFAULTPRINT((const u_char *)ap, length);
		return;
	}

	if ((pro != ETHERTYPE_IP && pro != ETHERTYPE_TRAIL) ||
	    PLN(ap) != 4 || HLN(ap) == 0) {
		ND_PRINT((ndo, "arp-#%d for proto #%d (%d) hardware #%d (%d)",
			  op, pro, PLN(ap), hrd, HLN(ap)));
		return;
	}
	if (pro == ETHERTYPE_TRAIL)
		ND_PRINT((ndo, "trailer-"));
	switch (op) {

	case ARPOP_REQUEST:
		ND_PRINT((ndo, "arp who-has %s", ipaddr_string(TPA(ap))));
		if (memcmp((const char *)ezero, (const char *)THA(ap), HLN(ap)) != 0)
			ND_PRINT((ndo, " (%s)",
				  linkaddr_string(THA(ap), HLN(ap))));
		ND_PRINT((ndo, " tell %s", ipaddr_string(SPA(ap))));
		break;

	case ARPOP_REPLY:
		ND_PRINT((ndo, "arp reply %s", ipaddr_string(SPA(ap))));
		ND_PRINT((ndo, " is-at %s", linkaddr_string(SHA(ap), HLN(ap))));
		break;

	case ARPOP_REVREQUEST:
		ND_PRINT((ndo, "rarp who-is %s tell %s",
			  linkaddr_string(THA(ap), HLN(ap)),
			  linkaddr_string(SHA(ap), HLN(ap))));
		break;

	case ARPOP_REVREPLY:
		ND_PRINT((ndo, "rarp reply %s at %s",
			  linkaddr_string(THA(ap), HLN(ap)),
			  ipaddr_string(TPA(ap))));
		break;

	case ARPOP_INVREQUEST:
		ND_PRINT((ndo, "invarp who-is %s tell %s",
			  linkaddr_string(THA(ap), HLN(ap)),
			  linkaddr_string(SHA(ap), HLN(ap))));
		break;

	case ARPOP_INVREPLY:
		ND_PRINT((ndo,"invarp reply %s at %s",
			  linkaddr_string(THA(ap), HLN(ap)),
			  ipaddr_string(TPA(ap))));
		break;

	default:
		ND_PRINT((ndo, "arp-#%d", op));
		ND_DEFAULTPRINT((const u_char *)ap, caplen);
		return;
	}
	if (hrd != ARPHRD_ETHER)
		ND_PRINT((ndo, " hardware #%d", hrd));
	return;
trunc:
	ND_PRINT((ndo, "[|arp]"));
}

/*
 * Local Variables:
 * c-style: bsd
 * End:
 */

