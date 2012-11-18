/*
 * Copyright (c) 1992, 1993, 1994, 1995, 1996, 1997
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
 * Code by Gert Doering, SpaceNet GmbH, gert@space.net
 *
 * Reference documentation:
 *    http://www.cisco.com/univercd/cc/td/doc/product/lan/trsrb/frames.htm
 */

#ifndef lint
static const char rcsid[] _U_ =
    "@(#) $Header: /tcpdump/master/tcpdump/print-cdp.c,v 1.25 2004/10/07 14:53:11 hannes Exp $";
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
#include "nlpid.h"

#define CDP_HEADER_LEN  4

static struct tok cdp_tlv_values[] = {
    { 0x01,             "Device-ID"},
    { 0x02,             "Address"},
    { 0x03,             "Port-ID"},
    { 0x04,             "Capability"},
    { 0x05,             "Version String"},
    { 0x06,             "Platform"},
    { 0x07,             "Prefixes"},
    { 0x08,             "Protocol-Hello option"},
    { 0x09,             "VTP Management Domain"},
    { 0x0a,             "Native VLAN ID"},
    { 0x0b,             "Duplex"},
    { 0x0e,             "ATA-186 VoIP VLAN request"},
    { 0x0f,             "ATA-186 VoIP VLAN assignment"},
    { 0x10,             "power consumption"},
    { 0x11,             "MTU"},
    { 0x12,             "AVVID trust bitmap"},
    { 0x13,             "AVVID untrusted ports CoS"},
    { 0x14,             "System Name"},
    { 0x15,             "System Object ID (not decoded)"},
    { 0x16,             "Management Addresses"},
    { 0x17,             "Physical Location"},
    { 0, NULL}
};

static struct tok cdp_capability_values[] = {
    { 0x01,             "Router" },
    { 0x02,             "Transparent Bridge" },
    { 0x04,             "Source Route Bridge" },
    { 0x08,             "L2 Switch" },
    { 0x10,             "L3 capable" },
    { 0x20,             "IGMP snooping" },
    { 0x40,             "L1 capable" },
    { 0, NULL }
};

static int cdp_print_addr(const u_char *, int);
static int cdp_print_prefixes(const u_char *, int);
static unsigned long cdp_get_number(const u_char *, int);

void
cdp_print(const u_char *pptr, u_int length, u_int caplen)
{
	int type, len, i, j;
        const u_char *tptr;

	if (caplen < CDP_HEADER_LEN) {
		(void)printf("[|cdp]");
		return;
	}

        tptr = pptr; /* temporary pointer */

        if (!TTEST2(*tptr, CDP_HEADER_LEN))
                goto trunc;
	printf("CDPv%u, ttl: %us", *tptr, *(tptr+1));
        if (vflag)
                printf(", checksum: %u (unverified), length %u", EXTRACT_16BITS(tptr), length);
	tptr += CDP_HEADER_LEN;

	while (tptr < (pptr+length)) {

                if (!TTEST2(*tptr, 4)) /* read out Type and Length */
                    goto trunc;
		type = EXTRACT_16BITS(tptr);
		len  = EXTRACT_16BITS(tptr+2); /* object length includes the 4 bytes header length */
                tptr += 4;
                len -= 4;

		if (!TTEST2(*tptr, len))
			goto trunc;

                if (vflag || type == 1) { /* in non-verbose mode just print Device-ID */

                    if (vflag)
                        printf("\n\t%s (0x%02x), length: %u byte%s: ",
                               tok2str(cdp_tlv_values,"unknown field type", type),
                               type,
                               len,
                               len>1 ? "s" : ""); /* plural */

                    switch (type) {

                    case 0x01: /* Device-ID */
                        if (!vflag)
                            printf(", Device-ID '%.*s'", len, tptr);
                        else
                            printf("'%.*s'", len, tptr);
			break;
                    case 0x02: /* Address */
                        if (cdp_print_addr(tptr, len) < 0)
                            goto trunc;
			break;
                    case 0x03: /* Port-ID */
			printf("'%.*s'", len, tptr);
			break;
                    case 0x04: /* Capabilities */
			printf("(0x%08x): %s",
                               EXTRACT_32BITS(tptr),
                               bittok2str(cdp_capability_values, "none",EXTRACT_32BITS(tptr)));
			break;
                    case 0x05: /* Version */
                        printf("\n\t  ");
                        for (i=0;i<len;i++) {
                            j = *(tptr+i);
                            putchar(j);
                            if (j == 0x0a) /* lets rework the version string to get a nice identation */
                                printf("\t  ");
                        }
			break;
                    case 0x06: /* Platform */
			printf("'%.*s'", len, tptr);
			break;
                    case 0x07: /* Prefixes */
			if (cdp_print_prefixes(tptr, len) < 0)
                            goto trunc;
			break;
                    case 0x08: /* Protocol Hello Option - not documented */
			break;
                    case 0x09: /* VTP Mgmt Domain  - not documented */
			printf("'%.*s'", len,tptr);
			break;
                    case 0x0a: /* Native VLAN ID - not documented */
			printf("%d",EXTRACT_16BITS(tptr));
			break;
                    case 0x0b: /* Duplex - not documented */
			printf("%s", *(tptr) ? "full": "half");
			break;

                    /* http://www.cisco.com/univercd/cc/td/doc/product/voice/ata/atarn/186rn21m.htm
                     * plus more details from other sources
                     */
                    case 0x0e: /* ATA-186 VoIP VLAN request - incomplete doc. */
			printf("app %d, vlan %d",
                               *(tptr), EXTRACT_16BITS(tptr+1));
			break;
                    case 0x10: /* ATA-186 VoIP VLAN assignment - incomplete doc. */
			printf("%1.2fW",
                               cdp_get_number(tptr, len)/1000.0 );
			break;
                    case 0x11: /* MTU - not documented */
			printf("%u bytes", EXTRACT_32BITS(tptr));
			break;
                    case 0x12: /* AVVID trust bitmap - not documented */
			printf("0x%02x", *(tptr) );
			break;
                    case 0x13: /* AVVID untrusted port CoS - not documented */
			printf("0x%02x", *(tptr));
			break;
                    case 0x14: /* System Name - not documented */
			printf("'%.*s'", len, tptr);
			break;
                    case 0x16: /* System Object ID - not documented */
			if (cdp_print_addr(tptr, len) < 0)
				goto trunc;
			break;
                    case 0x17: /* Physical Location - not documented */
			printf("0x%02x/%.*s", *(tptr), len - 1, tptr + 1 );
			break;
                    default:
                        print_unknown_data(tptr,"\n\t  ",len);
			break;
                    }
                }
		/* avoid infinite loop */
		if (len == 0)
			break;
		tptr = tptr+len;
	}
        if (vflag < 1)
            printf(", length %u",caplen);

	return;
trunc:
	printf("[|cdp]");
}

/*
 * Protocol type values.
 *
 * PT_NLPID means that the protocol type field contains an OSI NLPID.
 *
 * PT_IEEE_802_2 means that the protocol type field contains an IEEE 802.2
 * LLC header that specifies that the payload is for that protocol.
 */
#define PT_NLPID		1	/* OSI NLPID */
#define PT_IEEE_802_2		2	/* IEEE 802.2 LLC header */

static int
cdp_print_addr(const u_char * p, int l)
{
	int pt, pl, al, num;
	const u_char *endp = p + l;
#ifdef INET6
	static u_char prot_ipv6[] = {
		0xaa, 0xaa, 0x03, 0x00, 0x00, 0x00, 0x86, 0xdd
	};
#endif

	TCHECK2(*p, 2);
	num = EXTRACT_32BITS(p);
	p += 4;

	while (p < endp && num >= 0) {
		TCHECK2(*p, 2);
		if (p + 2 > endp)
			goto trunc;
		pt = p[0];		/* type of "protocol" field */
		pl = p[1];		/* length of "protocol" field */
		p += 2;

		TCHECK2(p[pl], 2);
		if (p + pl + 2 > endp)
			goto trunc;
		al = EXTRACT_16BITS(&p[pl]);	/* address length */

		if (pt == PT_NLPID && pl == 1 && *p == NLPID_IP && al == 4) {
			/*
			 * IPv4: protocol type = NLPID, protocol length = 1
			 * (1-byte NLPID), protocol = 0xcc (NLPID for IPv4),
			 * address length = 4
			 */
			p += 3;

			TCHECK2(*p, 4);
			if (p + 4 > endp)
				goto trunc;
			printf("IPv4 (%u) %s",
                               num,
                               ipaddr_string(p));
			p += 4;
		}
#ifdef INET6
		else if (pt == PT_IEEE_802_2 && pl == 8 &&
		    memcmp(p, prot_ipv6, 8) == 0 && al == 16) {
			/*
			 * IPv6: protocol type = IEEE 802.2 header,
			 * protocol length = 8 (size of LLC+SNAP header),
			 * protocol = LLC+SNAP header with the IPv6
			 * Ethertype, address length = 16
			 */
			p += 10;
			TCHECK2(*p, al);
			if (p + al > endp)
				goto trunc;

			printf("IPv6 (%u) %s",
                               num,
                               ip6addr_string(p));
			p += al;
		}
#endif
		else {
			/*
			 * Generic case: just print raw data
			 */
			TCHECK2(*p, pl);
			if (p + pl > endp)
				goto trunc;
			printf("pt=0x%02x, pl=%d, pb=", *(p - 2), pl);
			while (pl-- > 0)
				printf(" %02x", *p++);
			TCHECK2(*p, 2);
			if (p + 2 > endp)
				goto trunc;
			al = (*p << 8) + *(p + 1);
			printf(", al=%d, a=", al);
			p += 2;
			TCHECK2(*p, al);
			if (p + al > endp)
				goto trunc;
			while (al-- > 0)
				printf(" %02x", *p++);
		}
		num--;
		if (num)
			printf(" ");
	}

	return 0;

trunc:
	return -1;
}


static int
cdp_print_prefixes(const u_char * p, int l)
{
	if (l % 5)
		goto trunc;

	printf(" IPv4 Prefixes (%d):", l / 5);

	while (l > 0) {
		printf(" %u.%u.%u.%u/%u", p[0], p[1], p[2], p[3], p[4]);
		l -= 5;
		p += 5;
	}

	return 0;

trunc:
	return -1;
}

/* read in a <n>-byte number, MSB first
 * (of course this can handle max sizeof(long))
 */
static unsigned long cdp_get_number(const u_char * p, int l)
{
    unsigned long res=0;
    while( l>0 )
    {
	res = (res<<8) + *p;
	p++; l--;
    }
    return res;
}
