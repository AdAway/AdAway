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
    "@(#) $Header: /tcpdump/master/tcpdump/print-udp.c,v 1.138.2.1 2007/03/28 07:45:46 hannes Exp $ (LBL)";
#endif

#ifdef HAVE_CONFIG_H
#include "config.h"
#endif

#include <tcpdump-stdinc.h>

#ifdef SEGSIZE
#undef SEGSIZE
#endif
//#include <arpa/tftp.h>
#include "tftp.h"

#include <stdio.h>
#include <string.h>

#include "interface.h"
#include "addrtoname.h"
#include "extract.h"
#include "appletalk.h"

#include "udp.h"

#include "ip.h"
#ifdef INET6
#include "ip6.h"
#endif
#include "ipproto.h"
#include "rpc_auth.h"
#include "rpc_msg.h"

#include "nameser.h"
#include "nfs.h"
#include "bootp.h"

struct rtcphdr {
	u_int16_t rh_flags;	/* T:2 P:1 CNT:5 PT:8 */
	u_int16_t rh_len;	/* length of message (in words) */
	u_int32_t rh_ssrc;	/* synchronization src id */
};

typedef struct {
	u_int32_t upper;	/* more significant 32 bits */
	u_int32_t lower;	/* less significant 32 bits */
} ntp64;

/*
 * Sender report.
 */
struct rtcp_sr {
	ntp64 sr_ntp;		/* 64-bit ntp timestamp */
	u_int32_t sr_ts;	/* reference media timestamp */
	u_int32_t sr_np;	/* no. packets sent */
	u_int32_t sr_nb;	/* no. bytes sent */
};

/*
 * Receiver report.
 * Time stamps are middle 32-bits of ntp timestamp.
 */
struct rtcp_rr {
	u_int32_t rr_srcid;	/* sender being reported */
	u_int32_t rr_nl;	/* no. packets lost */
	u_int32_t rr_ls;	/* extended last seq number received */
	u_int32_t rr_dv;	/* jitter (delay variance) */
	u_int32_t rr_lsr;	/* orig. ts from last rr from this src  */
	u_int32_t rr_dlsr;	/* time from recpt of last rr to xmit time */
};

/*XXX*/
#define RTCP_PT_SR	200
#define RTCP_PT_RR	201
#define RTCP_PT_SDES	202
#define 	RTCP_SDES_CNAME	1
#define 	RTCP_SDES_NAME	2
#define 	RTCP_SDES_EMAIL	3
#define 	RTCP_SDES_PHONE	4
#define 	RTCP_SDES_LOC	5
#define 	RTCP_SDES_TOOL	6
#define 	RTCP_SDES_NOTE	7
#define 	RTCP_SDES_PRIV	8
#define RTCP_PT_BYE	203
#define RTCP_PT_APP	204

static void
vat_print(const void *hdr, register const struct udphdr *up)
{
	/* vat/vt audio */
	u_int ts = *(u_int16_t *)hdr;
	if ((ts & 0xf060) != 0) {
		/* probably vt */
		(void)printf("udp/vt %u %d / %d",
			     (u_int32_t)(EXTRACT_16BITS(&up->uh_ulen) - sizeof(*up)),
			     ts & 0x3ff, ts >> 10);
	} else {
		/* probably vat */
		u_int32_t i0 = EXTRACT_32BITS(&((u_int *)hdr)[0]);
		u_int32_t i1 = EXTRACT_32BITS(&((u_int *)hdr)[1]);
		printf("udp/vat %u c%d %u%s",
			(u_int32_t)(EXTRACT_16BITS(&up->uh_ulen) - sizeof(*up) - 8),
			i0 & 0xffff,
			i1, i0 & 0x800000? "*" : "");
		/* audio format */
		if (i0 & 0x1f0000)
			printf(" f%d", (i0 >> 16) & 0x1f);
		if (i0 & 0x3f000000)
			printf(" s%d", (i0 >> 24) & 0x3f);
	}
}

static void
rtp_print(const void *hdr, u_int len, register const struct udphdr *up)
{
	/* rtp v1 or v2 */
	u_int *ip = (u_int *)hdr;
	u_int hasopt, hasext, contype, hasmarker;
	u_int32_t i0 = EXTRACT_32BITS(&((u_int *)hdr)[0]);
	u_int32_t i1 = EXTRACT_32BITS(&((u_int *)hdr)[1]);
	u_int dlen = EXTRACT_16BITS(&up->uh_ulen) - sizeof(*up) - 8;
	const char * ptype;

	ip += 2;
	len >>= 2;
	len -= 2;
	hasopt = 0;
	hasext = 0;
	if ((i0 >> 30) == 1) {
		/* rtp v1 */
		hasopt = i0 & 0x800000;
		contype = (i0 >> 16) & 0x3f;
		hasmarker = i0 & 0x400000;
		ptype = "rtpv1";
	} else {
		/* rtp v2 */
		hasext = i0 & 0x10000000;
		contype = (i0 >> 16) & 0x7f;
		hasmarker = i0 & 0x800000;
		dlen -= 4;
		ptype = "rtp";
		ip += 1;
		len -= 1;
	}
	printf("udp/%s %d c%d %s%s %d %u",
		ptype,
		dlen,
		contype,
		(hasopt || hasext)? "+" : "",
		hasmarker? "*" : "",
		i0 & 0xffff,
		i1);
	if (vflag) {
		printf(" %u", EXTRACT_32BITS(&((u_int *)hdr)[2]));
		if (hasopt) {
			u_int i2, optlen;
			do {
				i2 = ip[0];
				optlen = (i2 >> 16) & 0xff;
				if (optlen == 0 || optlen > len) {
					printf(" !opt");
					return;
				}
				ip += optlen;
				len -= optlen;
			} while ((int)i2 >= 0);
		}
		if (hasext) {
			u_int i2, extlen;
			i2 = ip[0];
			extlen = (i2 & 0xffff) + 1;
			if (extlen > len) {
				printf(" !ext");
				return;
			}
			ip += extlen;
		}
		if (contype == 0x1f) /*XXX H.261 */
			printf(" 0x%04x", ip[0] >> 16);
	}
}

static const u_char *
rtcp_print(const u_char *hdr, const u_char *ep)
{
	/* rtp v2 control (rtcp) */
	struct rtcp_rr *rr = 0;
	struct rtcp_sr *sr;
	struct rtcphdr *rh = (struct rtcphdr *)hdr;
	u_int len;
	u_int16_t flags;
	int cnt;
	double ts, dts;
	if ((u_char *)(rh + 1) > ep) {
		printf(" [|rtcp]");
		return (ep);
	}
	len = (EXTRACT_16BITS(&rh->rh_len) + 1) * 4;
	flags = EXTRACT_16BITS(&rh->rh_flags);
	cnt = (flags >> 8) & 0x1f;
	switch (flags & 0xff) {
	case RTCP_PT_SR:
		sr = (struct rtcp_sr *)(rh + 1);
		printf(" sr");
		if (len != cnt * sizeof(*rr) + sizeof(*sr) + sizeof(*rh))
			printf(" [%d]", len);
		if (vflag)
			printf(" %u", EXTRACT_32BITS(&rh->rh_ssrc));
		if ((u_char *)(sr + 1) > ep) {
			printf(" [|rtcp]");
			return (ep);
		}
		ts = (double)(EXTRACT_32BITS(&sr->sr_ntp.upper)) +
		    ((double)(EXTRACT_32BITS(&sr->sr_ntp.lower)) /
		    4294967296.0);
		printf(" @%.2f %u %up %ub", ts, EXTRACT_32BITS(&sr->sr_ts),
		    EXTRACT_32BITS(&sr->sr_np), EXTRACT_32BITS(&sr->sr_nb));
		rr = (struct rtcp_rr *)(sr + 1);
		break;
	case RTCP_PT_RR:
		printf(" rr");
		if (len != cnt * sizeof(*rr) + sizeof(*rh))
			printf(" [%d]", len);
		rr = (struct rtcp_rr *)(rh + 1);
		if (vflag)
			printf(" %u", EXTRACT_32BITS(&rh->rh_ssrc));
		break;
	case RTCP_PT_SDES:
		printf(" sdes %d", len);
		if (vflag)
			printf(" %u", EXTRACT_32BITS(&rh->rh_ssrc));
		cnt = 0;
		break;
	case RTCP_PT_BYE:
		printf(" bye %d", len);
		if (vflag)
			printf(" %u", EXTRACT_32BITS(&rh->rh_ssrc));
		cnt = 0;
		break;
	default:
		printf(" type-0x%x %d", flags & 0xff, len);
		cnt = 0;
		break;
	}
	if (cnt > 1)
		printf(" c%d", cnt);
	while (--cnt >= 0) {
		if ((u_char *)(rr + 1) > ep) {
			printf(" [|rtcp]");
			return (ep);
		}
		if (vflag)
			printf(" %u", EXTRACT_32BITS(&rr->rr_srcid));
		ts = (double)(EXTRACT_32BITS(&rr->rr_lsr)) / 65536.;
		dts = (double)(EXTRACT_32BITS(&rr->rr_dlsr)) / 65536.;
		printf(" %ul %us %uj @%.2f+%.2f",
		    EXTRACT_32BITS(&rr->rr_nl) & 0x00ffffff,
		    EXTRACT_32BITS(&rr->rr_ls),
		    EXTRACT_32BITS(&rr->rr_dv), ts, dts);
	}
	return (hdr + len);
}

static int udp_cksum(register const struct ip *ip,
		     register const struct udphdr *up,
		     register u_int len)
{
	union phu {
		struct phdr {
			u_int32_t src;
			u_int32_t dst;
			u_char mbz;
			u_char proto;
			u_int16_t len;
		} ph;
		u_int16_t pa[6];
	} phu;
	register const u_int16_t *sp;

	/* pseudo-header.. */
	phu.ph.len = htons((u_int16_t)len);
	phu.ph.mbz = 0;
	phu.ph.proto = IPPROTO_UDP;
	memcpy(&phu.ph.src, &ip->ip_src.s_addr, sizeof(u_int32_t));
	if (IP_HL(ip) == 5)
		memcpy(&phu.ph.dst, &ip->ip_dst.s_addr, sizeof(u_int32_t));
	else
		phu.ph.dst = ip_finddst(ip);

	sp = &phu.pa[0];
	return in_cksum((u_short *)up, len,
			sp[0]+sp[1]+sp[2]+sp[3]+sp[4]+sp[5]);
}

#ifdef INET6
static int udp6_cksum(const struct ip6_hdr *ip6, const struct udphdr *up,
	u_int len)
{
	size_t i;
	register const u_int16_t *sp;
	u_int32_t sum;
	union {
		struct {
			struct in6_addr ph_src;
			struct in6_addr ph_dst;
			u_int32_t	ph_len;
			u_int8_t	ph_zero[3];
			u_int8_t	ph_nxt;
		} ph;
		u_int16_t pa[20];
	} phu;

	/* pseudo-header */
	memset(&phu, 0, sizeof(phu));
	phu.ph.ph_src = ip6->ip6_src;
	phu.ph.ph_dst = ip6->ip6_dst;
	phu.ph.ph_len = htonl(len);
	phu.ph.ph_nxt = IPPROTO_UDP;

	sum = 0;
	for (i = 0; i < sizeof(phu.pa) / sizeof(phu.pa[0]); i++)
		sum += phu.pa[i];

	sp = (const u_int16_t *)up;

	for (i = 0; i < (len & ~1); i += 2)
		sum += *sp++;

	if (len & 1)
		sum += htons((*(const u_int8_t *)sp) << 8);

	while (sum > 0xffff)
		sum = (sum & 0xffff) + (sum >> 16);
	sum = ~sum & 0xffff;

	return (sum);
}
#endif

static void
udpipaddr_print(const struct ip *ip, int sport, int dport)
{
#ifdef INET6
	const struct ip6_hdr *ip6;

	if (IP_V(ip) == 6)
		ip6 = (const struct ip6_hdr *)ip;
	else
		ip6 = NULL;

	if (ip6) {
		if (ip6->ip6_nxt == IPPROTO_UDP) {
			if (sport == -1) {
				(void)printf("%s > %s: ",
					ip6addr_string(&ip6->ip6_src),
					ip6addr_string(&ip6->ip6_dst));
			} else {
				(void)printf("%s.%s > %s.%s: ",
					ip6addr_string(&ip6->ip6_src),
					udpport_string(sport),
					ip6addr_string(&ip6->ip6_dst),
					udpport_string(dport));
			}
		} else {
			if (sport != -1) {
				(void)printf("%s > %s: ",
					udpport_string(sport),
					udpport_string(dport));
			}
		}
	} else
#endif /*INET6*/
	{
		if (ip->ip_p == IPPROTO_UDP) {
			if (sport == -1) {
				(void)printf("%s > %s: ",
					ipaddr_string(&ip->ip_src),
					ipaddr_string(&ip->ip_dst));
			} else {
				(void)printf("%s.%s > %s.%s: ",
					ipaddr_string(&ip->ip_src),
					udpport_string(sport),
					ipaddr_string(&ip->ip_dst),
					udpport_string(dport));
			}
		} else {
			if (sport != -1) {
				(void)printf("%s > %s: ",
					udpport_string(sport),
					udpport_string(dport));
			}
		}
	}
}

void
udp_print(register const u_char *bp, u_int length,
	  register const u_char *bp2, int fragmented)
{
	register const struct udphdr *up;
	register const struct ip *ip;
	register const u_char *cp;
	register const u_char *ep = bp + length;
	u_int16_t sport, dport, ulen;
#ifdef INET6
	register const struct ip6_hdr *ip6;
#endif

	if (ep > snapend)
		ep = snapend;
	up = (struct udphdr *)bp;
	ip = (struct ip *)bp2;
#ifdef INET6
	if (IP_V(ip) == 6)
		ip6 = (struct ip6_hdr *)bp2;
	else
		ip6 = NULL;
#endif /*INET6*/
	cp = (u_char *)(up + 1);
	if (!TTEST(up->uh_dport)) {
		udpipaddr_print(ip, -1, -1);
		(void)printf("[|udp]");
		return;
	}

	sport = EXTRACT_16BITS(&up->uh_sport);
	dport = EXTRACT_16BITS(&up->uh_dport);

	if (length < sizeof(struct udphdr)) {
		udpipaddr_print(ip, sport, dport);
		(void)printf("truncated-udp %d", length);
		return;
	}
	length -= sizeof(struct udphdr);

	if (cp > snapend) {
		udpipaddr_print(ip, sport, dport);
		(void)printf("[|udp]");
		return;
	}

	ulen = EXTRACT_16BITS(&up->uh_ulen);
	if (ulen < 8) {
		udpipaddr_print(ip, sport, dport);
		(void)printf("truncated-udplength %d", ulen);
		return;
	}
	if (packettype) {
		register struct sunrpc_msg *rp;
		enum sunrpc_msg_type direction;

		switch (packettype) {

		case PT_VAT:
			udpipaddr_print(ip, sport, dport);
			vat_print((void *)(up + 1), up);
			break;

		case PT_WB:
			udpipaddr_print(ip, sport, dport);
			wb_print((void *)(up + 1), length);
			break;

		case PT_RPC:
			rp = (struct sunrpc_msg *)(up + 1);
			direction = (enum sunrpc_msg_type)EXTRACT_32BITS(&rp->rm_direction);
			if (direction == SUNRPC_CALL)
				sunrpcrequest_print((u_char *)rp, length,
				    (u_char *)ip);
			else
				nfsreply_print((u_char *)rp, length,
				    (u_char *)ip);			/*XXX*/
			break;

		case PT_RTP:
			udpipaddr_print(ip, sport, dport);
			rtp_print((void *)(up + 1), length, up);
			break;

		case PT_RTCP:
			udpipaddr_print(ip, sport, dport);
			while (cp < ep)
				cp = rtcp_print(cp, ep);
			break;

		case PT_SNMP:
			udpipaddr_print(ip, sport, dport);
			snmp_print((const u_char *)(up + 1), length);
			break;

		case PT_CNFP:
			udpipaddr_print(ip, sport, dport);
			cnfp_print(cp, (const u_char *)ip);
			break;

		case PT_TFTP:
			udpipaddr_print(ip, sport, dport);
			tftp_print(cp, length);
			break;

		case PT_AODV:
			udpipaddr_print(ip, sport, dport);
			aodv_print((const u_char *)(up + 1), length,
#ifdef INET6
			    ip6 != NULL);
#else
			    0);
#endif
			break;
		}
		return;
	}

	if (!qflag) {
		register struct sunrpc_msg *rp;
		enum sunrpc_msg_type direction;

		rp = (struct sunrpc_msg *)(up + 1);
		if (TTEST(rp->rm_direction)) {
			direction = (enum sunrpc_msg_type)EXTRACT_32BITS(&rp->rm_direction);
			if (dport == NFS_PORT && direction == SUNRPC_CALL) {
				nfsreq_print((u_char *)rp, length,
				    (u_char *)ip);
				return;
			}
			if (sport == NFS_PORT && direction == SUNRPC_REPLY) {
				nfsreply_print((u_char *)rp, length,
				    (u_char *)ip);
				return;
			}
#ifdef notdef
			if (dport == SUNRPC_PORT && direction == SUNRPC_CALL) {
				sunrpcrequest_print((u_char *)rp, length, (u_char *)ip);
				return;
			}
#endif
		}
		if (TTEST(((struct LAP *)cp)->type) &&
		    ((struct LAP *)cp)->type == lapDDP &&
		    (atalk_port(sport) || atalk_port(dport))) {
			if (vflag)
				fputs("kip ", stdout);
			llap_print(cp, length);
			return;
		}
	}
	udpipaddr_print(ip, sport, dport);

	if (IP_V(ip) == 4 && (vflag > 1) && !fragmented) {
		int sum = up->uh_sum;
		if (sum == 0) {
			(void)printf("[no cksum] ");
		} else if (TTEST2(cp[0], length)) {
			sum = udp_cksum(ip, up, length + sizeof(struct udphdr));
			if (sum != 0)
				(void)printf("[bad udp cksum %x!] ", sum);
			else
				(void)printf("[udp sum ok] ");
		}
	}
#ifdef INET6
	if (IP_V(ip) == 6 && ip6->ip6_plen && vflag && !fragmented) {
		int sum = up->uh_sum;
		/* for IPv6, UDP checksum is mandatory */
		if (TTEST2(cp[0], length)) {
			sum = udp6_cksum(ip6, up, length + sizeof(struct udphdr));
			if (sum != 0)
				(void)printf("[bad udp cksum %x!] ", sum);
			else
				(void)printf("[udp sum ok] ");
		}
	}
#endif

	if (!qflag) {
#define ISPORT(p) (dport == (p) || sport == (p))
		if (ISPORT(NAMESERVER_PORT))
			ns_print((const u_char *)(up + 1), length, 0);
		else if (ISPORT(MULTICASTDNS_PORT))
			ns_print((const u_char *)(up + 1), length, 1);
		else if (ISPORT(TIMED_PORT))
			timed_print((const u_char *)(up + 1));
		else if (ISPORT(TFTP_PORT))
			tftp_print((const u_char *)(up + 1), length);
		else if (ISPORT(IPPORT_BOOTPC) || ISPORT(IPPORT_BOOTPS))
			bootp_print((const u_char *)(up + 1), length);
		else if (ISPORT(RIP_PORT))
			rip_print((const u_char *)(up + 1), length);
		else if (ISPORT(AODV_PORT))
			aodv_print((const u_char *)(up + 1), length,
#ifdef INET6
			    ip6 != NULL);
#else
			    0);
#endif
	        else if (ISPORT(ISAKMP_PORT))
			 isakmp_print(gndo, (const u_char *)(up + 1), length, bp2);
  	        else if (ISPORT(ISAKMP_PORT_NATT))
			 isakmp_rfc3948_print(gndo, (const u_char *)(up + 1), length, bp2);
#if 1 /*???*/
   	        else if (ISPORT(ISAKMP_PORT_USER1) || ISPORT(ISAKMP_PORT_USER2))
			isakmp_print(gndo, (const u_char *)(up + 1), length, bp2);
#endif
		else if (ISPORT(SNMP_PORT) || ISPORT(SNMPTRAP_PORT))
			snmp_print((const u_char *)(up + 1), length);
		else if (ISPORT(NTP_PORT))
			ntp_print((const u_char *)(up + 1), length);
		else if (ISPORT(KERBEROS_PORT) || ISPORT(KERBEROS_SEC_PORT))
			krb_print((const void *)(up + 1));
		else if (ISPORT(L2TP_PORT))
			l2tp_print((const u_char *)(up + 1), length);
#ifdef TCPDUMP_DO_SMB
		else if (ISPORT(NETBIOS_NS_PORT))
			nbt_udp137_print((const u_char *)(up + 1), length);
		else if (ISPORT(NETBIOS_DGRAM_PORT))
			nbt_udp138_print((const u_char *)(up + 1), length);
#endif
		else if (dport == 3456)
			vat_print((const void *)(up + 1), up);
		else if (ISPORT(ZEPHYR_SRV_PORT) || ISPORT(ZEPHYR_CLT_PORT))
			zephyr_print((const void *)(up + 1), length);
		/*
		 * Since there are 10 possible ports to check, I think
		 * a <> test would be more efficient
		 */
		else if ((sport >= RX_PORT_LOW && sport <= RX_PORT_HIGH) ||
			 (dport >= RX_PORT_LOW && dport <= RX_PORT_HIGH))
			rx_print((const void *)(up + 1), length, sport, dport,
				 (u_char *) ip);
#ifdef INET6
		else if (ISPORT(RIPNG_PORT))
			ripng_print((const u_char *)(up + 1), length);
		else if (ISPORT(DHCP6_SERV_PORT) || ISPORT(DHCP6_CLI_PORT)) {
			dhcp6_print((const u_char *)(up + 1), length);
		}
#endif /*INET6*/
		/*
		 * Kludge in test for whiteboard packets.
		 */
		else if (dport == 4567)
			wb_print((const void *)(up + 1), length);
		else if (ISPORT(CISCO_AUTORP_PORT))
			cisco_autorp_print((const void *)(up + 1), length);
		else if (ISPORT(RADIUS_PORT) ||
			 ISPORT(RADIUS_NEW_PORT) ||
			 ISPORT(RADIUS_ACCOUNTING_PORT) ||
			 ISPORT(RADIUS_NEW_ACCOUNTING_PORT) )
			radius_print((const u_char *)(up+1), length);
		else if (dport == HSRP_PORT)
			hsrp_print((const u_char *)(up + 1), length);
		else if (ISPORT(LWRES_PORT))
			lwres_print((const u_char *)(up + 1), length);
                else if (ISPORT(LDP_PORT))
			ldp_print((const u_char *)(up + 1), length);
                else if (ISPORT(OLSR_PORT))
			olsr_print((const u_char *)(up + 1), length);
                else if (ISPORT(MPLS_LSP_PING_PORT))
			lspping_print((const u_char *)(up + 1), length);
		else if (dport == BFD_CONTROL_PORT ||
			 dport == BFD_ECHO_PORT )
			bfd_print((const u_char *)(up+1), length, dport);
                else if (ISPORT(LMP_PORT))
			lmp_print((const u_char *)(up + 1), length);
                else if (ISPORT(SIP_PORT))
			sip_print((const u_char *)(up + 1), length);
                else if (ISPORT(SYSLOG_PORT))
			syslog_print((const u_char *)(up + 1), length);
		else
			(void)printf("UDP, length %u",
			    (u_int32_t)(ulen - sizeof(*up)));
#undef ISPORT
	} else
		(void)printf("UDP, length %u", (u_int32_t)(ulen - sizeof(*up)));
}


/*
 * Local Variables:
 * c-style: whitesmith
 * c-basic-offset: 8
 * End:
 */
