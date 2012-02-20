/*
 * Copyright (c) 1995, 1996
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
    "@(#) $Header: /tcpdump/master/tcpdump/print-pim.c,v 1.45.2.4 2006/02/13 01:32:34 hannes Exp $ (LBL)";
#endif

#ifdef HAVE_CONFIG_H
#include "config.h"
#endif

#include <tcpdump-stdinc.h>
#include "interface.h"

#define PIMV2_TYPE_HELLO         0
#define PIMV2_TYPE_REGISTER      1
#define PIMV2_TYPE_REGISTER_STOP 2
#define PIMV2_TYPE_JOIN_PRUNE    3
#define PIMV2_TYPE_BOOTSTRAP     4
#define PIMV2_TYPE_ASSERT        5
#define PIMV2_TYPE_GRAFT         6
#define PIMV2_TYPE_GRAFT_ACK     7
#define PIMV2_TYPE_CANDIDATE_RP  8
#define PIMV2_TYPE_PRUNE_REFRESH 9

static struct tok pimv2_type_values[] = {
    { PIMV2_TYPE_HELLO,         "Hello" },
    { PIMV2_TYPE_REGISTER,      "Register" },
    { PIMV2_TYPE_REGISTER_STOP, "Register Stop" },
    { PIMV2_TYPE_JOIN_PRUNE,    "Join / Prune" },
    { PIMV2_TYPE_BOOTSTRAP,     "Bootstrap" },
    { PIMV2_TYPE_ASSERT,        "Assert" },
    { PIMV2_TYPE_GRAFT,         "Graft" },
    { PIMV2_TYPE_GRAFT_ACK,     "Graft Acknowledgement" },
    { PIMV2_TYPE_CANDIDATE_RP,  "Candidate RP Advertisement" },
    { PIMV2_TYPE_PRUNE_REFRESH, "Prune Refresh" },
    { 0, NULL}
};

#define PIMV2_HELLO_OPTION_HOLDTIME             1
#define PIMV2_HELLO_OPTION_LANPRUNEDELAY        2
#define PIMV2_HELLO_OPTION_DR_PRIORITY_OLD     18
#define PIMV2_HELLO_OPTION_DR_PRIORITY         19
#define PIMV2_HELLO_OPTION_GENID               20
#define PIMV2_HELLO_OPTION_REFRESH_CAP         21
#define PIMV2_HELLO_OPTION_BIDIR_CAP           22
#define PIMV2_HELLO_OPTION_ADDRESS_LIST        24
#define PIMV2_HELLO_OPTION_ADDRESS_LIST_OLD 65001

static struct tok pimv2_hello_option_values[] = {
    { PIMV2_HELLO_OPTION_HOLDTIME,         "Hold Time" },
    { PIMV2_HELLO_OPTION_LANPRUNEDELAY,    "LAN Prune Delay" },
    { PIMV2_HELLO_OPTION_DR_PRIORITY_OLD,  "DR Priority (Old)" },
    { PIMV2_HELLO_OPTION_DR_PRIORITY,      "DR Priority" },
    { PIMV2_HELLO_OPTION_GENID,            "Generation ID" },
    { PIMV2_HELLO_OPTION_REFRESH_CAP,      "State Refresh Capability" },
    { PIMV2_HELLO_OPTION_BIDIR_CAP,        "Bi-Directional Capability" },
    { PIMV2_HELLO_OPTION_ADDRESS_LIST,     "Address List" },
    { PIMV2_HELLO_OPTION_ADDRESS_LIST_OLD, "Address List (Old)" },
    { 0, NULL}
};

#define PIMV2_REGISTER_FLAG_LEN      4
#define PIMV2_REGISTER_FLAG_BORDER 0x80000000
#define PIMV2_REGISTER_FLAG_NULL   0x40000000

static struct tok pimv2_register_flag_values[] = {
    { PIMV2_REGISTER_FLAG_BORDER, "Border" },
    { PIMV2_REGISTER_FLAG_NULL, "Null" },
    { 0, NULL}
};    

/*
 * XXX: We consider a case where IPv6 is not ready yet for portability,
 * but PIM dependent defintions should be independent of IPv6...
 */

struct pim {
	u_int8_t pim_typever;
			/* upper 4bit: PIM version number; 2 for PIMv2 */
			/* lower 4bit: the PIM message type, currently they are:
			 * Hello, Register, Register-Stop, Join/Prune,
			 * Bootstrap, Assert, Graft (PIM-DM only),
			 * Graft-Ack (PIM-DM only), C-RP-Adv
			 */
#define PIM_VER(x)	(((x) & 0xf0) >> 4)
#define PIM_TYPE(x)	((x) & 0x0f)
	u_char  pim_rsv;	/* Reserved */
	u_short	pim_cksum;	/* IP style check sum */
};


#include <stdio.h>
#include <stdlib.h>

#include "interface.h"
#include "addrtoname.h"
#include "extract.h"

#include "ip.h"

static void pimv2_print(register const u_char *bp, register u_int len);

static void
pimv1_join_prune_print(register const u_char *bp, register u_int len)
{
	int maddrlen, addrlen, ngroups, njoin, nprune;
	int njp;

	/* If it's a single group and a single source, use 1-line output. */
	if (TTEST2(bp[0], 30) && bp[11] == 1 &&
	    ((njoin = EXTRACT_16BITS(&bp[20])) + EXTRACT_16BITS(&bp[22])) == 1) {
		int hold;

		(void)printf(" RPF %s ", ipaddr_string(bp));
		hold = EXTRACT_16BITS(&bp[6]);
		if (hold != 180) {
			(void)printf("Hold ");
			relts_print(hold);
		}
		(void)printf("%s (%s/%d, %s", njoin ? "Join" : "Prune",
		ipaddr_string(&bp[26]), bp[25] & 0x3f,
		ipaddr_string(&bp[12]));
		if (EXTRACT_32BITS(&bp[16]) != 0xffffffff)
			(void)printf("/%s", ipaddr_string(&bp[16]));
		(void)printf(") %s%s %s",
		    (bp[24] & 0x01) ? "Sparse" : "Dense",
		    (bp[25] & 0x80) ? " WC" : "",
		    (bp[25] & 0x40) ? "RP" : "SPT");
		return;
	}

	TCHECK2(bp[0], sizeof(struct in_addr));
	if (vflag > 1)
		(void)printf("\n");
	(void)printf(" Upstream Nbr: %s", ipaddr_string(bp));
	TCHECK2(bp[6], 2);
	if (vflag > 1)
		(void)printf("\n");
	(void)printf(" Hold time: ");
	relts_print(EXTRACT_16BITS(&bp[6]));
	if (vflag < 2)
		return;
	bp += 8;
	len -= 8;

	TCHECK2(bp[0], 4);
	maddrlen = bp[1];
	addrlen = bp[2];
	ngroups = bp[3];
	bp += 4;
	len -= 4;
	while (ngroups--) {
		/*
		 * XXX - does the address have length "addrlen" and the
		 * mask length "maddrlen"?
		 */
		TCHECK2(bp[0], sizeof(struct in_addr));
		(void)printf("\n\tGroup: %s", ipaddr_string(bp));
		TCHECK2(bp[4], sizeof(struct in_addr));
		if (EXTRACT_32BITS(&bp[4]) != 0xffffffff)
			(void)printf("/%s", ipaddr_string(&bp[4]));
		TCHECK2(bp[8], 4);
		njoin = EXTRACT_16BITS(&bp[8]);
		nprune = EXTRACT_16BITS(&bp[10]);
		(void)printf(" joined: %d pruned: %d", njoin, nprune);
		bp += 12;
		len -= 12;
		for (njp = 0; njp < (njoin + nprune); njp++) {
			const char *type;

			if (njp < njoin)
				type = "Join ";
			else
				type = "Prune";
			TCHECK2(bp[0], 6);
			(void)printf("\n\t%s %s%s%s%s/%d", type,
			    (bp[0] & 0x01) ? "Sparse " : "Dense ",
			    (bp[1] & 0x80) ? "WC " : "",
			    (bp[1] & 0x40) ? "RP " : "SPT ",
			ipaddr_string(&bp[2]), bp[1] & 0x3f);
			bp += 6;
			len -= 6;
		}
	}
	return;
trunc:
	(void)printf("[|pim]");
	return;
}

void
pimv1_print(register const u_char *bp, register u_int len)
{
	register const u_char *ep;
	register u_char type;

	ep = (const u_char *)snapend;
	if (bp >= ep)
		return;

	TCHECK(bp[1]);
	type = bp[1];

	switch (type) {
	case 0:
		(void)printf(" Query");
		if (TTEST(bp[8])) {
			switch (bp[8] >> 4) {
			case 0:
				(void)printf(" Dense-mode");
				break;
			case 1:
				(void)printf(" Sparse-mode");
				break;
			case 2:
				(void)printf(" Sparse-Dense-mode");
				break;
			default:
				(void)printf(" mode-%d", bp[8] >> 4);
				break;
			}
		}
		if (vflag) {
			TCHECK2(bp[10],2);
			(void)printf(" (Hold-time ");
			relts_print(EXTRACT_16BITS(&bp[10]));
			(void)printf(")");
		}
		break;

	case 1:
		(void)printf(" Register");
		TCHECK2(bp[8], 20);			/* ip header */
		(void)printf(" for %s > %s", ipaddr_string(&bp[20]),
		    ipaddr_string(&bp[24]));
		break;
	case 2:
		(void)printf(" Register-Stop");
		TCHECK2(bp[12], sizeof(struct in_addr));
		(void)printf(" for %s > %s", ipaddr_string(&bp[8]),
		    ipaddr_string(&bp[12]));
		break;
	case 3:
		(void)printf(" Join/Prune");
		if (vflag)
			pimv1_join_prune_print(&bp[8], len - 8);
		break;
	case 4:
		(void)printf(" RP-reachable");
		if (vflag) {
			TCHECK2(bp[22], 2);
			(void)printf(" group %s",
			ipaddr_string(&bp[8]));
			if (EXTRACT_32BITS(&bp[12]) != 0xffffffff)
				(void)printf("/%s", ipaddr_string(&bp[12]));
			(void)printf(" RP %s hold ", ipaddr_string(&bp[16]));
			relts_print(EXTRACT_16BITS(&bp[22]));
		}
		break;
	case 5:
		(void)printf(" Assert");
		TCHECK2(bp[16], sizeof(struct in_addr));
		(void)printf(" for %s > %s", ipaddr_string(&bp[16]),
		    ipaddr_string(&bp[8]));
		if (EXTRACT_32BITS(&bp[12]) != 0xffffffff)
			(void)printf("/%s", ipaddr_string(&bp[12]));
		TCHECK2(bp[24], 4);
		(void)printf(" %s pref %d metric %d",
		    (bp[20] & 0x80) ? "RP-tree" : "SPT",
		EXTRACT_32BITS(&bp[20]) & 0x7fffffff,
		EXTRACT_32BITS(&bp[24]));
		break;
	case 6:
		(void)printf(" Graft");
		if (vflag)
			pimv1_join_prune_print(&bp[8], len - 8);
		break;
	case 7:
		(void)printf(" Graft-ACK");
		if (vflag)
			pimv1_join_prune_print(&bp[8], len - 8);
		break;
	case 8:
		(void)printf(" Mode");
		break;
	default:
		(void)printf(" [type %d]", type);
		break;
	}
	if ((bp[4] >> 4) != 1)
		(void)printf(" [v%d]", bp[4] >> 4);
	return;

trunc:
	(void)printf("[|pim]");
	return;
}

/*
 * auto-RP is a cisco protocol, documented at
 * ftp://ftpeng.cisco.com/ipmulticast/specs/pim-autorp-spec01.txt
 *
 * This implements version 1+, dated Sept 9, 1998.
 */
void
cisco_autorp_print(register const u_char *bp, register u_int len)
{
	int type;
	int numrps;
	int hold;

	TCHECK(bp[0]);
	(void)printf(" auto-rp ");
	type = bp[0];
	switch (type) {
	case 0x11:
		(void)printf("candidate-advert");
		break;
	case 0x12:
		(void)printf("mapping");
		break;
	default:
		(void)printf("type-0x%02x", type);
		break;
	}

	TCHECK(bp[1]);
	numrps = bp[1];

	TCHECK2(bp[2], 2);
	(void)printf(" Hold ");
	hold = EXTRACT_16BITS(&bp[2]);
	if (hold)
		relts_print(EXTRACT_16BITS(&bp[2]));
	else
		printf("FOREVER");

	/* Next 4 bytes are reserved. */

	bp += 8; len -= 8;

	/*XXX skip unless -v? */

	/*
	 * Rest of packet:
	 * numrps entries of the form:
	 * 32 bits: RP
	 * 6 bits: reserved
	 * 2 bits: PIM version supported, bit 0 is "supports v1", 1 is "v2".
	 * 8 bits: # of entries for this RP
	 * each entry: 7 bits: reserved, 1 bit: negative,
	 *	       8 bits: mask 32 bits: source
	 * lather, rinse, repeat.
	 */
	while (numrps--) {
		int nentries;
		char s;

		TCHECK2(bp[0], 4);
		(void)printf(" RP %s", ipaddr_string(bp));
		TCHECK(bp[4]);
		switch (bp[4] & 0x3) {
		case 0: printf(" PIMv?");
			break;
		case 1:	printf(" PIMv1");
			break;
		case 2:	printf(" PIMv2");
			break;
		case 3:	printf(" PIMv1+2");
			break;
		}
		if (bp[4] & 0xfc)
			(void)printf(" [rsvd=0x%02x]", bp[4] & 0xfc);
		TCHECK(bp[5]);
		nentries = bp[5];
		bp += 6; len -= 6;
		s = ' ';
		for (; nentries; nentries--) {
			TCHECK2(bp[0], 6);
			(void)printf("%c%s%s/%d", s, bp[0] & 1 ? "!" : "",
			    ipaddr_string(&bp[2]), bp[1]);
			if (bp[0] & 0xfe)
				(void)printf("[rsvd=0x%02x]", bp[0] & 0xfe);
			s = ',';
			bp += 6; len -= 6;
		}
	}
	return;

trunc:
	(void)printf("[|autorp]");
	return;
}

void
pim_print(register const u_char *bp, register u_int len)
{
	register const u_char *ep;
	register struct pim *pim = (struct pim *)bp;

	ep = (const u_char *)snapend;
	if (bp >= ep)
		return;
#ifdef notyet			/* currently we see only version and type */
	TCHECK(pim->pim_rsv);
#endif

	switch (PIM_VER(pim->pim_typever)) {
	case 2:
            if (!vflag) {
                printf("PIMv%u, %s, length %u",
                       PIM_VER(pim->pim_typever),
                       tok2str(pimv2_type_values,"Unknown Type",PIM_TYPE(pim->pim_typever)),
                       len);
                return;
            } else {
                printf("PIMv%u, length %u\n\t%s",
                       PIM_VER(pim->pim_typever),
                       len,
                       tok2str(pimv2_type_values,"Unknown Type",PIM_TYPE(pim->pim_typever)));
                pimv2_print(bp, len);
            }
            break;
	default:
		printf("PIMv%u, length %u",
                       PIM_VER(pim->pim_typever),
                       len);
		break;
	}
	return;
}

/*
 * PIMv2 uses encoded address representations.
 *
 * The last PIM-SM I-D before RFC2117 was published specified the
 * following representation for unicast addresses.  However, RFC2117
 * specified no encoding for unicast addresses with the unicast
 * address length specified in the header.  Therefore, we have to
 * guess which encoding is being used (Cisco's PIMv2 implementation
 * uses the non-RFC encoding).  RFC2117 turns a previously "Reserved"
 * field into a 'unicast-address-length-in-bytes' field.  We guess
 * that it's the draft encoding if this reserved field is zero.
 *
 * RFC2362 goes back to the encoded format, and calls the addr length
 * field "reserved" again.
 *
 * The first byte is the address family, from:
 *
 *    0    Reserved
 *    1    IP (IP version 4)
 *    2    IP6 (IP version 6)
 *    3    NSAP
 *    4    HDLC (8-bit multidrop)
 *    5    BBN 1822
 *    6    802 (includes all 802 media plus Ethernet "canonical format")
 *    7    E.163
 *    8    E.164 (SMDS, Frame Relay, ATM)
 *    9    F.69 (Telex)
 *   10    X.121 (X.25, Frame Relay)
 *   11    IPX
 *   12    Appletalk
 *   13    Decnet IV
 *   14    Banyan Vines
 *   15    E.164 with NSAP format subaddress
 *
 * In addition, the second byte is an "Encoding".  0 is the default
 * encoding for the address family, and no other encodings are currently
 * specified.
 *
 */

static int pimv2_addr_len;

enum pimv2_addrtype {
	pimv2_unicast, pimv2_group, pimv2_source
};

/*  0                   1                   2                   3
 *  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * | Addr Family   | Encoding Type |     Unicast Address           |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+++++++
 *  0                   1                   2                   3
 *  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * | Addr Family   | Encoding Type |   Reserved    |  Mask Len     |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                Group multicast Address                        |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *  0                   1                   2                   3
 *  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * | Addr Family   | Encoding Type | Rsrvd   |S|W|R|  Mask Len     |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                        Source Address                         |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 */
static int
pimv2_addr_print(const u_char *bp, enum pimv2_addrtype at, int silent)
{
	int af;
	int len, hdrlen;

	TCHECK(bp[0]);

	if (pimv2_addr_len == 0) {
		TCHECK(bp[1]);
		switch (bp[0]) {
		case 1:
			af = AF_INET;
			len = sizeof(struct in_addr);
			break;
#ifdef INET6
		case 2:
			af = AF_INET6;
			len = sizeof(struct in6_addr);
			break;
#endif
		default:
			return -1;
		}
		if (bp[1] != 0)
			return -1;
		hdrlen = 2;
	} else {
		switch (pimv2_addr_len) {
		case sizeof(struct in_addr):
			af = AF_INET;
			break;
#ifdef INET6
		case sizeof(struct in6_addr):
			af = AF_INET6;
			break;
#endif
		default:
			return -1;
			break;
		}
		len = pimv2_addr_len;
		hdrlen = 0;
	}

	bp += hdrlen;
	switch (at) {
	case pimv2_unicast:
		TCHECK2(bp[0], len);
		if (af == AF_INET) {
			if (!silent)
				(void)printf("%s", ipaddr_string(bp));
		}
#ifdef INET6
		else if (af == AF_INET6) {
			if (!silent)
				(void)printf("%s", ip6addr_string(bp));
		}
#endif
		return hdrlen + len;
	case pimv2_group:
	case pimv2_source:
		TCHECK2(bp[0], len + 2);
		if (af == AF_INET) {
			if (!silent) {
				(void)printf("%s", ipaddr_string(bp + 2));
				if (bp[1] != 32)
					(void)printf("/%u", bp[1]);
			}
		}
#ifdef INET6
		else if (af == AF_INET6) {
			if (!silent) {
				(void)printf("%s", ip6addr_string(bp + 2));
				if (bp[1] != 128)
					(void)printf("/%u", bp[1]);
			}
		}
#endif
		if (bp[0] && !silent) {
			if (at == pimv2_group) {
				(void)printf("(0x%02x)", bp[0]);
			} else {
				(void)printf("(%s%s%s",
					bp[0] & 0x04 ? "S" : "",
					bp[0] & 0x02 ? "W" : "",
					bp[0] & 0x01 ? "R" : "");
				if (bp[0] & 0xf8) {
					(void) printf("+0x%02x", bp[0] & 0xf8);
				}
				(void)printf(")");
			}
		}
		return hdrlen + 2 + len;
	default:
		return -1;
	}
trunc:
	return -1;
}

static void
pimv2_print(register const u_char *bp, register u_int len)
{
	register const u_char *ep;
	register struct pim *pim = (struct pim *)bp;
	int advance;

	ep = (const u_char *)snapend;
	if (bp >= ep)
		return;
	if (ep > bp + len)
		ep = bp + len;
	TCHECK(pim->pim_rsv);
	pimv2_addr_len = pim->pim_rsv;
	if (pimv2_addr_len != 0)
		(void)printf(", RFC2117-encoding");

        printf(", cksum 0x%04x ", EXTRACT_16BITS(&pim->pim_cksum));
        if (EXTRACT_16BITS(&pim->pim_cksum) == 0) {
                printf("(unverified)");
        } else {
                printf("(%scorrect)",
                       TTEST2(bp[0], len) &&
                       in_cksum((const u_short*)bp, len, 0) ? "in" : "" );
        }

	switch (PIM_TYPE(pim->pim_typever)) {
	case PIMV2_TYPE_HELLO:
	    {
		u_int16_t otype, olen;
		bp += 4;
		while (bp < ep) {
			TCHECK2(bp[0], 4);
			otype = EXTRACT_16BITS(&bp[0]);
			olen = EXTRACT_16BITS(&bp[2]);
			TCHECK2(bp[0], 4 + olen);

                        printf("\n\t  %s Option (%u), length %u, Value: ",
                               tok2str( pimv2_hello_option_values,"Unknown",otype),
                               otype,
                               olen);
			bp += 4;

			switch (otype) {
			case PIMV2_HELLO_OPTION_HOLDTIME:
                                relts_print(EXTRACT_16BITS(bp));
                                break;

			case PIMV2_HELLO_OPTION_LANPRUNEDELAY:
				if (olen != 4) {
					(void)printf("ERROR: Option Lenght != 4 Bytes (%u)", olen);
				} else {
					char t_bit;
					u_int16_t lan_delay, override_interval;
					lan_delay = EXTRACT_16BITS(bp);
					override_interval = EXTRACT_16BITS(bp+2);
					t_bit = (lan_delay & 0x8000)? 1 : 0;
					lan_delay &= ~0x8000;
					(void)printf("\n\t    T-bit=%d, LAN delay %dms, Override interval %dms",
					t_bit, lan_delay, override_interval);
				}
				break;

			case PIMV2_HELLO_OPTION_DR_PRIORITY_OLD:
			case PIMV2_HELLO_OPTION_DR_PRIORITY:
                                switch (olen) {
                                case 0:
                                    printf("Bi-Directional Capability (Old)");
                                    break;
                                case 4:
                                    printf("%u", EXTRACT_32BITS(bp));
                                    break;
                                default:
                                    printf("ERROR: Option Lenght != 4 Bytes (%u)", olen);
                                    break;
                                }
                                break;

			case PIMV2_HELLO_OPTION_GENID:
                                (void)printf("0x%08x", EXTRACT_32BITS(bp));
				break;

			case PIMV2_HELLO_OPTION_REFRESH_CAP:
                                (void)printf("v%d", *bp);
				if (*(bp+1) != 0) {
                                    (void)printf(", interval ");
                                    relts_print(*(bp+1));
				}
				if (EXTRACT_16BITS(bp+2) != 0) {
                                    (void)printf(" ?0x%04x?", EXTRACT_16BITS(bp+2));
				}
				break;

			case  PIMV2_HELLO_OPTION_BIDIR_CAP:
				break;

                        case PIMV2_HELLO_OPTION_ADDRESS_LIST_OLD:
                        case PIMV2_HELLO_OPTION_ADDRESS_LIST:
				if (vflag > 1) {
					const u_char *ptr = bp;
					while (ptr < (bp+olen)) {
						int advance;

						printf("\n\t    ");
						advance = pimv2_addr_print(ptr, pimv2_unicast, 0);
						if (advance < 0) {
							printf("...");
							break;
						}
						ptr += advance;
					}
				}
				break;
			default:
                                if (vflag <= 1)
                                    print_unknown_data(bp,"\n\t    ",olen);
                                break;
			}
                        /* do we want to see an additionally hexdump ? */
                        if (vflag> 1)
                            print_unknown_data(bp,"\n\t    ",olen);
			bp += olen;
		}
		break;
	    }

	case PIMV2_TYPE_REGISTER:
	{
		struct ip *ip;

                if (!TTEST2(*(bp+4), PIMV2_REGISTER_FLAG_LEN))
                        goto trunc;

                printf(", Flags [ %s ]\n\t",
                       tok2str(pimv2_register_flag_values,
                               "none",
                               EXTRACT_32BITS(bp+4)));

		bp += 8; len -= 8;
		/* encapsulated multicast packet */
		ip = (struct ip *)bp;
		switch (IP_V(ip)) {
                case 0: /* Null header */
			(void)printf("IP-Null-header %s > %s",
                                     ipaddr_string(&ip->ip_src),
                                     ipaddr_string(&ip->ip_dst));
			break;

		case 4:	/* IPv4 */
			ip_print(gndo, bp, len);
			break;
#ifdef INET6
		case 6:	/* IPv6 */
			ip6_print(bp, len);
			break;
#endif
                default:
                        (void)printf("IP ver %d", IP_V(ip));
                        break;
		}
		break;
	}

	case PIMV2_TYPE_REGISTER_STOP:
		bp += 4; len -= 4;
		if (bp >= ep)
			break;
		(void)printf(" group=");
		if ((advance = pimv2_addr_print(bp, pimv2_group, 0)) < 0) {
			(void)printf("...");
			break;
		}
		bp += advance; len -= advance;
		if (bp >= ep)
			break;
		(void)printf(" source=");
		if ((advance = pimv2_addr_print(bp, pimv2_unicast, 0)) < 0) {
			(void)printf("...");
			break;
		}
		bp += advance; len -= advance;
		break;

	case PIMV2_TYPE_JOIN_PRUNE:
	case PIMV2_TYPE_GRAFT:
	case PIMV2_TYPE_GRAFT_ACK:


        /*
         * 0                   1                   2                   3
         *   0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
         *  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
         *  |PIM Ver| Type  | Addr length   |           Checksum            |
         *  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
         *  |             Unicast-Upstream Neighbor Address                 |
         *  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
         *  |  Reserved     | Num groups    |          Holdtime             |
         *  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
         *  |            Encoded-Multicast Group Address-1                  |
         *  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
         *  |   Number of Joined  Sources   |   Number of Pruned Sources    |
         *  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
         *  |               Encoded-Joined Source Address-1                 |
         *  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
         *  |                             .                                 |
         *  |                             .                                 |
         *  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
         *  |               Encoded-Joined Source Address-n                 |
         *  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
         *  |               Encoded-Pruned Source Address-1                 |
         *  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
         *  |                             .                                 |
         *  |                             .                                 |
         *  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
         *  |               Encoded-Pruned Source Address-n                 |
         *  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
         *  |                           .                                   |
         *  |                           .                                   |
         *  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
         *  |                Encoded-Multicast Group Address-n              |
         *  +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
         */

	    {
		u_int8_t ngroup;
		u_int16_t holdtime;
		u_int16_t njoin;
		u_int16_t nprune;
		int i, j;

		bp += 4; len -= 4;
		if (PIM_TYPE(pim->pim_typever) != 7) {	/*not for Graft-ACK*/
			if (bp >= ep)
				break;
			(void)printf(", upstream-neighbor: ");
			if ((advance = pimv2_addr_print(bp, pimv2_unicast, 0)) < 0) {
				(void)printf("...");
				break;
			}
			bp += advance; len -= advance;
		}
		if (bp + 4 > ep)
			break;
		ngroup = bp[1];
		holdtime = EXTRACT_16BITS(&bp[2]);
		(void)printf("\n\t  %u group(s)", ngroup);
		if (PIM_TYPE(pim->pim_typever) != 7) {	/*not for Graft-ACK*/
			(void)printf(", holdtime: ");
			if (holdtime == 0xffff)
				(void)printf("infinite");
			else
				relts_print(holdtime);
		}
		bp += 4; len -= 4;
		for (i = 0; i < ngroup; i++) {
			if (bp >= ep)
				goto jp_done;
			(void)printf("\n\t    group #%u: ", i+1);
			if ((advance = pimv2_addr_print(bp, pimv2_group, 0)) < 0) {
				(void)printf("...)");
				goto jp_done;
			}
			bp += advance; len -= advance;
			if (bp + 4 > ep) {
				(void)printf("...)");
				goto jp_done;
			}
			njoin = EXTRACT_16BITS(&bp[0]);
			nprune = EXTRACT_16BITS(&bp[2]);
			(void)printf(", joined sources: %u, pruned sources: %u", njoin,nprune);
			bp += 4; len -= 4;
			for (j = 0; j < njoin; j++) {
				(void)printf("\n\t      joined source #%u: ",j+1);
				if ((advance = pimv2_addr_print(bp, pimv2_source, 0)) < 0) {
					(void)printf("...)");
					goto jp_done;
				}
				bp += advance; len -= advance;
			}
			for (j = 0; j < nprune; j++) {
				(void)printf("\n\t      pruned source #%u: ",j+1);
				if ((advance = pimv2_addr_print(bp, pimv2_source, 0)) < 0) {
					(void)printf("...)");
					goto jp_done;
				}
				bp += advance; len -= advance;
			}
		}
	jp_done:
		break;
	    }

	case PIMV2_TYPE_BOOTSTRAP:
	{
		int i, j, frpcnt;
		bp += 4;

		/* Fragment Tag, Hash Mask len, and BSR-priority */
		if (bp + sizeof(u_int16_t) >= ep) break;
		(void)printf(" tag=%x", EXTRACT_16BITS(bp));
		bp += sizeof(u_int16_t);
		if (bp >= ep) break;
		(void)printf(" hashmlen=%d", bp[0]);
		if (bp + 1 >= ep) break;
		(void)printf(" BSRprio=%d", bp[1]);
		bp += 2;

		/* Encoded-Unicast-BSR-Address */
		if (bp >= ep) break;
		(void)printf(" BSR=");
		if ((advance = pimv2_addr_print(bp, pimv2_unicast, 0)) < 0) {
			(void)printf("...");
			break;
		}
		bp += advance;

		for (i = 0; bp < ep; i++) {
			/* Encoded-Group Address */
			(void)printf(" (group%d: ", i);
			if ((advance = pimv2_addr_print(bp, pimv2_group, 0))
			    < 0) {
				(void)printf("...)");
				goto bs_done;
			}
			bp += advance;

			/* RP-Count, Frag RP-Cnt, and rsvd */
			if (bp >= ep) {
				(void)printf("...)");
				goto bs_done;
			}
			(void)printf(" RPcnt=%d", bp[0]);
			if (bp + 1 >= ep) {
				(void)printf("...)");
				goto bs_done;
			}
			(void)printf(" FRPcnt=%d", frpcnt = bp[1]);
			bp += 4;

			for (j = 0; j < frpcnt && bp < ep; j++) {
				/* each RP info */
				(void)printf(" RP%d=", j);
				if ((advance = pimv2_addr_print(bp,
								pimv2_unicast,
								0)) < 0) {
					(void)printf("...)");
					goto bs_done;
				}
				bp += advance;

				if (bp + 1 >= ep) {
					(void)printf("...)");
					goto bs_done;
				}
				(void)printf(",holdtime=");
				relts_print(EXTRACT_16BITS(bp));
				if (bp + 2 >= ep) {
					(void)printf("...)");
					goto bs_done;
				}
				(void)printf(",prio=%d", bp[2]);
				bp += 4;
			}
			(void)printf(")");
		}
	   bs_done:
		break;
	}
	case PIMV2_TYPE_ASSERT:
		bp += 4; len -= 4;
		if (bp >= ep)
			break;
		(void)printf(" group=");
		if ((advance = pimv2_addr_print(bp, pimv2_group, 0)) < 0) {
			(void)printf("...");
			break;
		}
		bp += advance; len -= advance;
		if (bp >= ep)
			break;
		(void)printf(" src=");
		if ((advance = pimv2_addr_print(bp, pimv2_unicast, 0)) < 0) {
			(void)printf("...");
			break;
		}
		bp += advance; len -= advance;
		if (bp + 8 > ep)
			break;
		if (bp[0] & 0x80)
			(void)printf(" RPT");
		(void)printf(" pref=%u", EXTRACT_32BITS(&bp[0]) & 0x7fffffff);
		(void)printf(" metric=%u", EXTRACT_32BITS(&bp[4]));
		break;

	case PIMV2_TYPE_CANDIDATE_RP:
	{
		int i, pfxcnt;
		bp += 4;

		/* Prefix-Cnt, Priority, and Holdtime */
		if (bp >= ep) break;
		(void)printf(" prefix-cnt=%d", bp[0]);
		pfxcnt = bp[0];
		if (bp + 1 >= ep) break;
		(void)printf(" prio=%d", bp[1]);
		if (bp + 3 >= ep) break;
		(void)printf(" holdtime=");
		relts_print(EXTRACT_16BITS(&bp[2]));
		bp += 4;

		/* Encoded-Unicast-RP-Address */
		if (bp >= ep) break;
		(void)printf(" RP=");
		if ((advance = pimv2_addr_print(bp, pimv2_unicast, 0)) < 0) {
			(void)printf("...");
			break;
		}
		bp += advance;

		/* Encoded-Group Addresses */
		for (i = 0; i < pfxcnt && bp < ep; i++) {
			(void)printf(" Group%d=", i);
			if ((advance = pimv2_addr_print(bp, pimv2_group, 0))
			    < 0) {
				(void)printf("...");
				break;
			}
			bp += advance;
		}
		break;
	}

	case PIMV2_TYPE_PRUNE_REFRESH:
		(void)printf(" src=");
		if ((advance = pimv2_addr_print(bp, pimv2_unicast, 0)) < 0) {
			(void)printf("...");
			break;
		}
		bp += advance;
		(void)printf(" grp=");
		if ((advance = pimv2_addr_print(bp, pimv2_group, 0)) < 0) {
			(void)printf("...");
			break;
		}
		bp += advance;
		(void)printf(" forwarder=");
		if ((advance = pimv2_addr_print(bp, pimv2_unicast, 0)) < 0) {
			(void)printf("...");
			break;
		}
		bp += advance;
		TCHECK2(bp[0], 2);
		(void)printf(" TUNR ");
		relts_print(EXTRACT_16BITS(bp));
		break;


	 default:
		(void)printf(" [type %d]", PIM_TYPE(pim->pim_typever));
		break;
	}

	return;

trunc:
	(void)printf("[|pim]");
}

/*
 * Local Variables:
 * c-style: whitesmith
 * c-basic-offset: 8
 * End:
 */
