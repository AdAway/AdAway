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
    "@(#) $Header: /tcpdump/master/tcpdump/print-ip.c,v 1.149.2.9 2007/09/14 01:30:02 guy Exp $ (LBL)";
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

#include "ip.h"
#include "ipproto.h"

struct tok ip_option_values[] = {
    { IPOPT_EOL, "EOL" },
    { IPOPT_NOP, "NOP" },
    { IPOPT_TS, "timestamp" },
    { IPOPT_SECURITY, "security" },
    { IPOPT_RR, "RR" },
    { IPOPT_SSRR, "SSRR" },
    { IPOPT_LSRR, "LSRR" },
    { IPOPT_RA, "RA" },
    { IPOPT_RFC1393, "traceroute" },
    { 0, NULL }
};

/*
 * print the recorded route in an IP RR, LSRR or SSRR option.
 */
static void
ip_printroute(register const u_char *cp, u_int length)
{
	register u_int ptr;
	register u_int len;

	if (length < 3) {
		printf(" [bad length %u]", length);
		return;
	}
	if ((length + 1) & 3)
		printf(" [bad length %u]", length);
	ptr = cp[2] - 1;
	if (ptr < 3 || ((ptr + 1) & 3) || ptr > length + 1)
		printf(" [bad ptr %u]", cp[2]);

	for (len = 3; len < length; len += 4) {
		printf(" %s", ipaddr_string(&cp[len]));
                if (ptr > len)
                        printf(",");
	}
}

/*
 * If source-routing is present and valid, return the final destination.
 * Otherwise, return IP destination.
 *
 * This is used for UDP and TCP pseudo-header in the checksum
 * calculation.
 */
u_int32_t
ip_finddst(const struct ip *ip)
{
	int length;
	int len;
	const u_char *cp;
	u_int32_t retval;

	cp = (const u_char *)(ip + 1);
	length = (IP_HL(ip) << 2) - sizeof(struct ip);

	for (; length > 0; cp += len, length -= len) {
		int tt;

		TCHECK(*cp);
		tt = *cp;
		if (tt == IPOPT_EOL)
			break;
		else if (tt == IPOPT_NOP)
			len = 1;
		else {
			TCHECK(cp[1]);
			len = cp[1];
			if (len < 2)
				break;
		}
		TCHECK2(*cp, len);
		switch (tt) {

		case IPOPT_SSRR:
		case IPOPT_LSRR:
			if (len < 7)
				break;
			memcpy(&retval, cp + len - 4, 4);
			return retval;
		}
	}
trunc:
	memcpy(&retval, &ip->ip_dst.s_addr, sizeof(u_int32_t));
	return retval;
}

static void
ip_printts(register const u_char *cp, u_int length)
{
	register u_int ptr;
	register u_int len;
	int hoplen;
	const char *type;

	if (length < 4) {
		printf("[bad length %u]", length);
		return;
	}
	printf(" TS{");
	hoplen = ((cp[3]&0xF) != IPOPT_TS_TSONLY) ? 8 : 4;
	if ((length - 4) & (hoplen-1))
		printf("[bad length %u]", length);
	ptr = cp[2] - 1;
	len = 0;
	if (ptr < 4 || ((ptr - 4) & (hoplen-1)) || ptr > length + 1)
		printf("[bad ptr %u]", cp[2]);
	switch (cp[3]&0xF) {
	case IPOPT_TS_TSONLY:
		printf("TSONLY");
		break;
	case IPOPT_TS_TSANDADDR:
		printf("TS+ADDR");
		break;
	/*
	 * prespecified should really be 3, but some ones might send 2
	 * instead, and the IPOPT_TS_PRESPEC constant can apparently
	 * have both values, so we have to hard-code it here.
	 */

	case 2:
		printf("PRESPEC2.0");
		break;
	case 3:			/* IPOPT_TS_PRESPEC */
		printf("PRESPEC");
		break;
	default:
		printf("[bad ts type %d]", cp[3]&0xF);
		goto done;
	}

	type = " ";
	for (len = 4; len < length; len += hoplen) {
		if (ptr == len)
			type = " ^ ";
		printf("%s%d@%s", type, EXTRACT_32BITS(&cp[len+hoplen-4]),
		       hoplen!=8 ? "" : ipaddr_string(&cp[len]));
		type = " ";
	}

done:
	printf("%s", ptr == len ? " ^ " : "");

	if (cp[3]>>4)
		printf(" [%d hops not recorded]} ", cp[3]>>4);
	else
		printf("}");
}

/*
 * print IP options.
 */
static void
ip_optprint(register const u_char *cp, u_int length)
{
	register u_int option_len;
	const char *sep = "";

	for (; length > 0; cp += option_len, length -= option_len) {
		u_int option_code;

		printf("%s", sep);
		sep = ",";

		TCHECK(*cp);
		option_code = *cp;

                printf("%s",
                        tok2str(ip_option_values,"unknown %u",option_code));

		if (option_code == IPOPT_NOP ||
                    option_code == IPOPT_EOL)
			option_len = 1;

		else {
			TCHECK(cp[1]);
			option_len = cp[1];
			if (option_len < 2) {
		                printf(" [bad length %u]", option_len);
				return;
			}
		}

		if (option_len > length) {
	                printf(" [bad length %u]", option_len);
			return;
		}

                TCHECK2(*cp, option_len);

		switch (option_code) {
		case IPOPT_EOL:
			return;

		case IPOPT_TS:
			ip_printts(cp, option_len);
			break;

		case IPOPT_RR:       /* fall through */
		case IPOPT_SSRR:
		case IPOPT_LSRR:
			ip_printroute(cp, option_len);
			break;

		case IPOPT_RA:
			if (option_len < 4) {
				printf(" [bad length %u]", option_len);
				break;
			}
                        TCHECK(cp[3]);
                        if (EXTRACT_16BITS(&cp[2]) != 0)
                            printf(" value %u", EXTRACT_16BITS(&cp[2]));
			break;

		case IPOPT_NOP:       /* nothing to print - fall through */
		case IPOPT_SECURITY:
		default:
			break;
		}
	}
	return;

trunc:
	printf("[|ip]");
}

/*
 * compute an IP header checksum.
 * don't modifiy the packet.
 */
u_short
in_cksum(const u_short *addr, register u_int len, int csum)
{
	int nleft = len;
	const u_short *w = addr;
	u_short answer;
	int sum = csum;

	/*
	 *  Our algorithm is simple, using a 32 bit accumulator (sum),
	 *  we add sequential 16 bit words to it, and at the end, fold
	 *  back all the carry bits from the top 16 bits into the lower
	 *  16 bits.
	 */
	while (nleft > 1)  {
		sum += *w++;
		nleft -= 2;
	}
	if (nleft == 1)
		sum += htons(*(u_char *)w<<8);

	/*
	 * add back carry outs from top 16 bits to low 16 bits
	 */
	sum = (sum >> 16) + (sum & 0xffff);	/* add hi 16 to low 16 */
	sum += (sum >> 16);			/* add carry */
	answer = ~sum;				/* truncate to 16 bits */
	return (answer);
}

/*
 * Given the host-byte-order value of the checksum field in a packet
 * header, and the network-byte-order computed checksum of the data
 * that the checksum covers (including the checksum itself), compute
 * what the checksum field *should* have been.
 */
u_int16_t
in_cksum_shouldbe(u_int16_t sum, u_int16_t computed_sum)
{
	u_int32_t shouldbe;

	/*
	 * The value that should have gone into the checksum field
	 * is the negative of the value gotten by summing up everything
	 * *but* the checksum field.
	 *
	 * We can compute that by subtracting the value of the checksum
	 * field from the sum of all the data in the packet, and then
	 * computing the negative of that value.
	 *
	 * "sum" is the value of the checksum field, and "computed_sum"
	 * is the negative of the sum of all the data in the packets,
	 * so that's -(-computed_sum - sum), or (sum + computed_sum).
	 *
	 * All the arithmetic in question is one's complement, so the
	 * addition must include an end-around carry; we do this by
	 * doing the arithmetic in 32 bits (with no sign-extension),
	 * and then adding the upper 16 bits of the sum, which contain
	 * the carry, to the lower 16 bits of the sum, and then do it
	 * again in case *that* sum produced a carry.
	 *
	 * As RFC 1071 notes, the checksum can be computed without
	 * byte-swapping the 16-bit words; summing 16-bit words
	 * on a big-endian machine gives a big-endian checksum, which
	 * can be directly stuffed into the big-endian checksum fields
	 * in protocol headers, and summing words on a little-endian
	 * machine gives a little-endian checksum, which must be
	 * byte-swapped before being stuffed into a big-endian checksum
	 * field.
	 *
	 * "computed_sum" is a network-byte-order value, so we must put
	 * it in host byte order before subtracting it from the
	 * host-byte-order value from the header; the adjusted checksum
	 * will be in host byte order, which is what we'll return.
	 */
	shouldbe = sum;
	shouldbe += ntohs(computed_sum);
	shouldbe = (shouldbe & 0xFFFF) + (shouldbe >> 16);
	shouldbe = (shouldbe & 0xFFFF) + (shouldbe >> 16);
	return shouldbe;
}

#define IP_RES 0x8000

static struct tok ip_frag_values[] = {
        { IP_MF,        "+" },
        { IP_DF,        "DF" },
	{ IP_RES,       "rsvd" }, /* The RFC3514 evil ;-) bit */
        { 0,            NULL }
};

struct ip_print_demux_state {
	const struct ip *ip;
	const u_char *cp;
	u_int   len, off;
	u_char  nh;
	int     advance;
};

static void
ip_print_demux(netdissect_options *ndo,
	       struct ip_print_demux_state *ipds)
{
	struct protoent *proto;

again:
	switch (ipds->nh) {

	case IPPROTO_AH:
		ipds->nh = *ipds->cp;
		ipds->advance = ah_print(ipds->cp);
		if (ipds->advance <= 0)
			break;
		ipds->cp += ipds->advance;
		ipds->len -= ipds->advance;
		goto again;

	case IPPROTO_ESP:
	{
		int enh, padlen;
		ipds->advance = esp_print(ndo, ipds->cp, ipds->len,
				    (const u_char *)ipds->ip,
				    &enh, &padlen);
		if (ipds->advance <= 0)
			break;
		ipds->cp += ipds->advance;
		ipds->len -= ipds->advance + padlen;
		ipds->nh = enh & 0xff;
		goto again;
	}
	
	case IPPROTO_IPCOMP:
	{
		int enh;
		ipds->advance = ipcomp_print(ipds->cp, &enh);
		if (ipds->advance <= 0)
			break;
		ipds->cp += ipds->advance;
		ipds->len -= ipds->advance;
		ipds->nh = enh & 0xff;
		goto again;
	}

	case IPPROTO_SCTP:
		sctp_print(ipds->cp, (const u_char *)ipds->ip, ipds->len);
		break;

	case IPPROTO_DCCP:
		dccp_print(ipds->cp, (const u_char *)ipds->ip, ipds->len);
		break;
		
	case IPPROTO_TCP:
		/* pass on the MF bit plus the offset to detect fragments */
		tcp_print(ipds->cp, ipds->len, (const u_char *)ipds->ip,
			  ipds->off & (IP_MF|IP_OFFMASK));
		break;
		
	case IPPROTO_UDP:
		/* pass on the MF bit plus the offset to detect fragments */
		udp_print(ipds->cp, ipds->len, (const u_char *)ipds->ip,
			  ipds->off & (IP_MF|IP_OFFMASK));
		break;
		
	case IPPROTO_ICMP:
		/* pass on the MF bit plus the offset to detect fragments */
		icmp_print(ipds->cp, ipds->len, (const u_char *)ipds->ip,
			   ipds->off & (IP_MF|IP_OFFMASK));
		break;
		
	case IPPROTO_PIGP:
		/*
		 * XXX - the current IANA protocol number assignments
		 * page lists 9 as "any private interior gateway
		 * (used by Cisco for their IGRP)" and 88 as
		 * "EIGRP" from Cisco.
		 *
		 * Recent BSD <netinet/in.h> headers define
		 * IP_PROTO_PIGP as 9 and IP_PROTO_IGRP as 88.
		 * We define IP_PROTO_PIGP as 9 and
		 * IP_PROTO_EIGRP as 88; those names better
		 * match was the current protocol number
		 * assignments say.
		 */
		igrp_print(ipds->cp, ipds->len, (const u_char *)ipds->ip);
		break;
		
	case IPPROTO_EIGRP:
		eigrp_print(ipds->cp, ipds->len);
		break;
		
	case IPPROTO_ND:
		ND_PRINT((ndo, " nd %d", ipds->len));
		break;

	case IPPROTO_EGP:
		egp_print(ipds->cp, ipds->len);
		break;

	case IPPROTO_OSPF:
		ospf_print(ipds->cp, ipds->len, (const u_char *)ipds->ip);
		break;

	case IPPROTO_IGMP:
		igmp_print(ipds->cp, ipds->len);
		break;

	case IPPROTO_IPV4:
		/* DVMRP multicast tunnel (ip-in-ip encapsulation) */
		ip_print(gndo, ipds->cp, ipds->len);
		if (! vflag) {
			ND_PRINT((ndo, " (ipip-proto-4)"));
			return;
		}
		break;
		
#ifdef INET6
	case IPPROTO_IPV6:
		/* ip6-in-ip encapsulation */
		ip6_print(ipds->cp, ipds->len);
		break;
#endif /*INET6*/

	case IPPROTO_RSVP:
		rsvp_print(ipds->cp, ipds->len);
		break;

	case IPPROTO_GRE:
		/* do it */
		gre_print(ipds->cp, ipds->len);
		break;

	case IPPROTO_MOBILE:
		mobile_print(ipds->cp, ipds->len);
		break;

	case IPPROTO_PIM:
		pim_print(ipds->cp,  ipds->len);
		break;

	case IPPROTO_VRRP:
		vrrp_print(ipds->cp, ipds->len, ipds->ip->ip_ttl);
		break;

	case IPPROTO_PGM:
		pgm_print(ipds->cp, ipds->len, (const u_char *)ipds->ip);
		break;

	default:
		if ((proto = getprotobynumber(ipds->nh)) != NULL)
			ND_PRINT((ndo, " %s", proto->p_name));
		else
			ND_PRINT((ndo, " ip-proto-%d", ipds->nh));
		ND_PRINT((ndo, " %d", ipds->len));
		break;
	}
}
	       
void
ip_print_inner(netdissect_options *ndo,
	       const u_char *bp,
	       u_int length, u_int nh,
	       const u_char *bp2)
{
	struct ip_print_demux_state  ipd;

	ipd.ip = (const struct ip *)bp2;
	ipd.cp = bp;
	ipd.len  = length;
	ipd.off  = 0;
	ipd.nh   = nh;
	ipd.advance = 0;

	ip_print_demux(ndo, &ipd);
}


/*
 * print an IP datagram.
 */
void
ip_print(netdissect_options *ndo,
	 const u_char *bp,
	 u_int length)
{
	struct ip_print_demux_state  ipd;
	struct ip_print_demux_state *ipds=&ipd;
	const u_char *ipend;
	u_int hlen;
	u_int16_t sum, ip_sum;
	struct protoent *proto;

	ipds->ip = (const struct ip *)bp;
	if (IP_V(ipds->ip) != 4) { /* print version if != 4 */
	    printf("IP%u ", IP_V(ipds->ip));
	    if (IP_V(ipds->ip) == 6)
		printf(", wrong link-layer encapsulation");
	}
        else if (!eflag)
	    printf("IP ");

	if ((u_char *)(ipds->ip + 1) > snapend) {
		printf("[|ip]");
		return;
	}
	if (length < sizeof (struct ip)) {
		(void)printf("truncated-ip %u", length);
		return;
	}
	hlen = IP_HL(ipds->ip) * 4;
	if (hlen < sizeof (struct ip)) {
		(void)printf("bad-hlen %u", hlen);
		return;
	}

	ipds->len = EXTRACT_16BITS(&ipds->ip->ip_len);
	if (length < ipds->len)
		(void)printf("truncated-ip - %u bytes missing! ",
			ipds->len - length);
	if (ipds->len < hlen) {
#ifdef GUESS_TSO
            if (ipds->len) {
                (void)printf("bad-len %u", ipds->len);
                return;
            }
            else {
                /* we guess that it is a TSO send */
                ipds->len = length;
            }
#else
            (void)printf("bad-len %u", ipds->len);
            return;
#endif /* GUESS_TSO */
	}

	/*
	 * Cut off the snapshot length to the end of the IP payload.
	 */
	ipend = bp + ipds->len;
	if (ipend < snapend)
		snapend = ipend;

	ipds->len -= hlen;

	ipds->off = EXTRACT_16BITS(&ipds->ip->ip_off);

        if (vflag) {
            (void)printf("(tos 0x%x", (int)ipds->ip->ip_tos);
            /* ECN bits */
            if (ipds->ip->ip_tos & 0x03) {
                switch (ipds->ip->ip_tos & 0x03) {
                case 1:
                    (void)printf(",ECT(1)");
                    break;
                case 2:
                    (void)printf(",ECT(0)");
                    break;
                case 3:
                    (void)printf(",CE");
                }
            }

            if (ipds->ip->ip_ttl >= 1)
                (void)printf(", ttl %u", ipds->ip->ip_ttl);    

	    /*
	     * for the firewall guys, print id, offset.
             * On all but the last stick a "+" in the flags portion.
	     * For unfragmented datagrams, note the don't fragment flag.
	     */

	    (void)printf(", id %u, offset %u, flags [%s], proto %s (%u)",
                         EXTRACT_16BITS(&ipds->ip->ip_id),
                         (ipds->off & 0x1fff) * 8,
                         bittok2str(ip_frag_values, "none", ipds->off&0xe000),
                         tok2str(ipproto_values,"unknown",ipds->ip->ip_p),
                         ipds->ip->ip_p);

            (void)printf(", length %u", EXTRACT_16BITS(&ipds->ip->ip_len));

            if ((hlen - sizeof(struct ip)) > 0) {
                printf(", options (");
                ip_optprint((u_char *)(ipds->ip + 1), hlen - sizeof(struct ip));
                printf(")");
            }

	    if ((u_char *)ipds->ip + hlen <= snapend) {
	        sum = in_cksum((const u_short *)ipds->ip, hlen, 0);
		if (sum != 0) {
		    ip_sum = EXTRACT_16BITS(&ipds->ip->ip_sum);
		    (void)printf(", bad cksum %x (->%x)!", ip_sum,
			     in_cksum_shouldbe(ip_sum, sum));
		}
	    }

            printf(") ");
	}

	/*
	 * If this is fragment zero, hand it to the next higher
	 * level protocol.
	 */
	if ((ipds->off & 0x1fff) == 0) {
		ipds->cp = (const u_char *)ipds->ip + hlen;
		ipds->nh = ipds->ip->ip_p;

		if (ipds->nh != IPPROTO_TCP && ipds->nh != IPPROTO_UDP &&
		    ipds->nh != IPPROTO_SCTP && ipds->nh != IPPROTO_DCCP) {
			(void)printf("%s > %s: ",
				     ipaddr_string(&ipds->ip->ip_src),
				     ipaddr_string(&ipds->ip->ip_dst));
		}
		ip_print_demux(ndo, ipds);
	} else {
	    /* Ultra quiet now means that all this stuff should be suppressed */
	    if (qflag > 1) return;

	    /*
	     * if this isn't the first frag, we're missing the
	     * next level protocol header.  print the ip addr
	     * and the protocol.
	     */
	    if (ipds->off & 0x1fff) {
	        (void)printf("%s > %s:", ipaddr_string(&ipds->ip->ip_src),
			     ipaddr_string(&ipds->ip->ip_dst));
		if ((proto = getprotobynumber(ipds->ip->ip_p)) != NULL)
		    (void)printf(" %s", proto->p_name);
		else
		    (void)printf(" ip-proto-%d", ipds->ip->ip_p);
	    } 
	}
}

void
ipN_print(register const u_char *bp, register u_int length)
{
	struct ip *ip, hdr;

	ip = (struct ip *)bp;
	if (length < 4) {
		(void)printf("truncated-ip %d", length);
		return;
	}
	memcpy (&hdr, (char *)ip, 4);
	switch (IP_V(&hdr)) {
	case 4:
		ip_print (gndo, bp, length);
		return;
#ifdef INET6
	case 6:
		ip6_print (bp, length);
		return;
#endif
	default:
		(void)printf("unknown ip %d", IP_V(&hdr));
		return;
	}
}

/*
 * Local Variables:
 * c-style: whitesmith
 * c-basic-offset: 8
 * End:
 */


