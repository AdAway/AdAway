/*
 * Copyright (c) 1988, 1989, 1990, 1991, 1992, 1993, 1994, 1995, 1996, 1997
 *	The Regents of the University of California.  All rights reserved.
 *
 * Copyright (c) 1999-2004 The tcpdump.org project
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
    "@(#) $Header: /tcpdump/master/tcpdump/print-tcp.c,v 1.120.2.3 2005/10/16 06:05:46 guy Exp $ (LBL)";
#endif

#ifdef HAVE_CONFIG_H
#include "config.h"
#endif

#include <tcpdump-stdinc.h>

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "interface.h"
#include "addrtoname.h"
#include "extract.h"

#include "tcp.h"

#include "ip.h"
#ifdef INET6
#include "ip6.h"
#endif
#include "ipproto.h"
#include "rpc_auth.h"
#include "rpc_msg.h"

#include "nameser.h"

#ifdef HAVE_LIBCRYPTO
#include <openssl/md5.h>

#define SIGNATURE_VALID		0
#define SIGNATURE_INVALID	1
#define CANT_CHECK_SIGNATURE	2

static int tcp_verify_signature(const struct ip *ip, const struct tcphdr *tp,
    const u_char *data, int length, const u_char *rcvsig);
#endif

static void print_tcp_rst_data(register const u_char *sp, u_int length);

#define MAX_RST_DATA_LEN	30


struct tha {
#ifndef INET6
	struct in_addr src;
	struct in_addr dst;
#else
	struct in6_addr src;
	struct in6_addr dst;
#endif /*INET6*/
	u_int port;
};

struct tcp_seq_hash {
	struct tcp_seq_hash *nxt;
	struct tha addr;
	tcp_seq seq;
	tcp_seq ack;
};

#define TSEQ_HASHSIZE 919

/* These tcp optinos do not have the size octet */
#define ZEROLENOPT(o) ((o) == TCPOPT_EOL || (o) == TCPOPT_NOP)

static struct tcp_seq_hash tcp_seq_hash[TSEQ_HASHSIZE];


#ifndef TELNET_PORT
#define TELNET_PORT	23
#endif
#ifndef BGP_PORT
#define BGP_PORT	179
#endif
#define NETBIOS_SSN_PORT 139
#ifndef PPTP_PORT
#define PPTP_PORT	1723
#endif
#define BEEP_PORT        10288
#ifndef NFS_PORT
#define NFS_PORT	2049
#endif
#define MSDP_PORT	639
#define LDP_PORT        646

static int tcp_cksum(register const struct ip *ip,
		     register const struct tcphdr *tp,
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
	const u_int16_t *sp;

	/* pseudo-header.. */
	phu.ph.len = htons((u_int16_t)len);
	phu.ph.mbz = 0;
	phu.ph.proto = IPPROTO_TCP;
	memcpy(&phu.ph.src, &ip->ip_src.s_addr, sizeof(u_int32_t));
	if (IP_HL(ip) == 5)
		memcpy(&phu.ph.dst, &ip->ip_dst.s_addr, sizeof(u_int32_t));
	else
		phu.ph.dst = ip_finddst(ip);

	sp = &phu.pa[0];
	return in_cksum((u_short *)tp, len,
			sp[0]+sp[1]+sp[2]+sp[3]+sp[4]+sp[5]);
}

#ifdef INET6
static int tcp6_cksum(const struct ip6_hdr *ip6, const struct tcphdr *tp,
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
	phu.ph.ph_nxt = IPPROTO_TCP;

	sum = 0;
	for (i = 0; i < sizeof(phu.pa) / sizeof(phu.pa[0]); i++)
		sum += phu.pa[i];

	sp = (const u_int16_t *)tp;

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

void
tcp_print(register const u_char *bp, register u_int length,
	  register const u_char *bp2, int fragmented)
{
	register const struct tcphdr *tp;
	register const struct ip *ip;
	register u_char flags;
	register u_int hlen;
	register char ch;
	u_int16_t sport, dport, win, urp;
	u_int32_t seq, ack, thseq, thack;
	int threv;
#ifdef INET6
	register const struct ip6_hdr *ip6;
#endif

	tp = (struct tcphdr *)bp;
	ip = (struct ip *)bp2;
#ifdef INET6
	if (IP_V(ip) == 6)
		ip6 = (struct ip6_hdr *)bp2;
	else
		ip6 = NULL;
#endif /*INET6*/
	ch = '\0';
	if (!TTEST(tp->th_dport)) {
		(void)printf("%s > %s: [|tcp]",
			ipaddr_string(&ip->ip_src),
			ipaddr_string(&ip->ip_dst));
		return;
	}

	sport = EXTRACT_16BITS(&tp->th_sport);
	dport = EXTRACT_16BITS(&tp->th_dport);

	hlen = TH_OFF(tp) * 4;

	/*
	 * If data present, header length valid, and NFS port used,
	 * assume NFS.
	 * Pass offset of data plus 4 bytes for RPC TCP msg length
	 * to NFS print routines.
	 */
	if (!qflag && hlen >= sizeof(*tp) && hlen <= length) {
		if ((u_char *)tp + 4 + sizeof(struct sunrpc_msg) <= snapend &&
		    dport == NFS_PORT) {
			nfsreq_print((u_char *)tp + hlen + 4, length - hlen,
				     (u_char *)ip);
			return;
		} else if ((u_char *)tp + 4 + sizeof(struct sunrpc_msg)
			   <= snapend &&
			   sport == NFS_PORT) {
			nfsreply_print((u_char *)tp + hlen + 4, length - hlen,
				       (u_char *)ip);
			return;
		}
	}
#ifdef INET6
	if (ip6) {
		if (ip6->ip6_nxt == IPPROTO_TCP) {
			(void)printf("%s.%s > %s.%s: ",
				ip6addr_string(&ip6->ip6_src),
				tcpport_string(sport),
				ip6addr_string(&ip6->ip6_dst),
				tcpport_string(dport));
		} else {
			(void)printf("%s > %s: ",
				tcpport_string(sport), tcpport_string(dport));
		}
	} else
#endif /*INET6*/
	{
		if (ip->ip_p == IPPROTO_TCP) {
			(void)printf("%s.%s > %s.%s: ",
				ipaddr_string(&ip->ip_src),
				tcpport_string(sport),
				ipaddr_string(&ip->ip_dst),
				tcpport_string(dport));
		} else {
			(void)printf("%s > %s: ",
				tcpport_string(sport), tcpport_string(dport));
		}
	}

	if (hlen < sizeof(*tp)) {
		(void)printf(" tcp %d [bad hdr length %u - too short, < %lu]",
		    length - hlen, hlen, (unsigned long)sizeof(*tp));
		return;
	}

	TCHECK(*tp);

	seq = EXTRACT_32BITS(&tp->th_seq);
	ack = EXTRACT_32BITS(&tp->th_ack);
	win = EXTRACT_16BITS(&tp->th_win);
	urp = EXTRACT_16BITS(&tp->th_urp);

	if (qflag) {
		(void)printf("tcp %d", length - hlen);
		if (hlen > length) {
			(void)printf(" [bad hdr length %u - too long, > %u]",
			    hlen, length);
		}
		return;
	}
	if ((flags = tp->th_flags) & (TH_SYN|TH_FIN|TH_RST|TH_PUSH|
				      TH_ECNECHO|TH_CWR)) {
		if (flags & TH_SYN)
			putchar('S');
		if (flags & TH_FIN)
			putchar('F');
		if (flags & TH_RST)
			putchar('R');
		if (flags & TH_PUSH)
			putchar('P');
		if (flags & TH_CWR)
			putchar('W');	/* congestion _W_indow reduced (ECN) */
		if (flags & TH_ECNECHO)
			putchar('E');	/* ecn _E_cho sent (ECN) */
	} else
		putchar('.');

	if (!Sflag && (flags & TH_ACK)) {
		register struct tcp_seq_hash *th;
		const void *src, *dst;
		register int rev;
		struct tha tha;
		/*
		 * Find (or record) the initial sequence numbers for
		 * this conversation.  (we pick an arbitrary
		 * collating order so there's only one entry for
		 * both directions).
		 */
#ifdef INET6
		memset(&tha, 0, sizeof(tha));
		rev = 0;
		if (ip6) {
			src = &ip6->ip6_src;
			dst = &ip6->ip6_dst;
			if (sport > dport)
				rev = 1;
			else if (sport == dport) {
				if (memcmp(src, dst, sizeof ip6->ip6_dst) > 0)
					rev = 1;
			}
			if (rev) {
				memcpy(&tha.src, dst, sizeof ip6->ip6_dst);
				memcpy(&tha.dst, src, sizeof ip6->ip6_src);
				tha.port = dport << 16 | sport;
			} else {
				memcpy(&tha.dst, dst, sizeof ip6->ip6_dst);
				memcpy(&tha.src, src, sizeof ip6->ip6_src);
				tha.port = sport << 16 | dport;
			}
		} else {
			src = &ip->ip_src;
			dst = &ip->ip_dst;
			if (sport > dport)
				rev = 1;
			else if (sport == dport) {
				if (memcmp(src, dst, sizeof ip->ip_dst) > 0)
					rev = 1;
			}
			if (rev) {
				memcpy(&tha.src, dst, sizeof ip->ip_dst);
				memcpy(&tha.dst, src, sizeof ip->ip_src);
				tha.port = dport << 16 | sport;
			} else {
				memcpy(&tha.dst, dst, sizeof ip->ip_dst);
				memcpy(&tha.src, src, sizeof ip->ip_src);
				tha.port = sport << 16 | dport;
			}
		}
#else
		rev = 0;
		src = &ip->ip_src;
		dst = &ip->ip_dst;
		if (sport > dport)
			rev = 1;
		else if (sport == dport) {
			if (memcmp(src, dst, sizeof ip->ip_dst) > 0)
				rev = 1;
		}
		if (rev) {
			memcpy(&tha.src, dst, sizeof ip->ip_dst);
			memcpy(&tha.dst, src, sizeof ip->ip_src);
			tha.port = dport << 16 | sport;
		} else {
			memcpy(&tha.dst, dst, sizeof ip->ip_dst);
			memcpy(&tha.src, src, sizeof ip->ip_src);
			tha.port = sport << 16 | dport;
		}
#endif

		threv = rev;
		for (th = &tcp_seq_hash[tha.port % TSEQ_HASHSIZE];
		     th->nxt; th = th->nxt)
			if (memcmp((char *)&tha, (char *)&th->addr,
				  sizeof(th->addr)) == 0)
				break;

		if (!th->nxt || (flags & TH_SYN)) {
			/* didn't find it or new conversation */
			if (th->nxt == NULL) {
				th->nxt = (struct tcp_seq_hash *)
					calloc(1, sizeof(*th));
				if (th->nxt == NULL)
					error("tcp_print: calloc");
			}
			th->addr = tha;
			if (rev)
				th->ack = seq, th->seq = ack - 1;
			else
				th->seq = seq, th->ack = ack - 1;
		} else {
			if (rev)
				seq -= th->ack, ack -= th->seq;
			else
				seq -= th->seq, ack -= th->ack;
		}

		thseq = th->seq;
		thack = th->ack;
	} else {
		/*fool gcc*/
		thseq = thack = threv = 0;
	}
	if (hlen > length) {
		(void)printf(" [bad hdr length %u - too long, > %u]",
		    hlen, length);
		return;
	}

	if (IP_V(ip) == 4 && vflag && !fragmented) {
		u_int16_t sum, tcp_sum;
		if (TTEST2(tp->th_sport, length)) {
			sum = tcp_cksum(ip, tp, length);

                        (void)printf(", cksum 0x%04x",EXTRACT_16BITS(&tp->th_sum));
			if (sum != 0) {
				tcp_sum = EXTRACT_16BITS(&tp->th_sum);
				(void)printf(" (incorrect (-> 0x%04x),",in_cksum_shouldbe(tcp_sum, sum));
			} else
				(void)printf(" (correct),");
		}
	}
#ifdef INET6
	if (IP_V(ip) == 6 && ip6->ip6_plen && vflag && !fragmented) {
		u_int16_t sum,tcp_sum;
		if (TTEST2(tp->th_sport, length)) {
			sum = tcp6_cksum(ip6, tp, length);
                        (void)printf(", cksum 0x%04x",EXTRACT_16BITS(&tp->th_sum));
			if (sum != 0) {
				tcp_sum = EXTRACT_16BITS(&tp->th_sum);
				(void)printf(" (incorrect (-> 0x%04x),",in_cksum_shouldbe(tcp_sum, sum));
			} else
				(void)printf(" (correct),");

		}
	}
#endif

	length -= hlen;
	if (vflag > 1 || length > 0 || flags & (TH_SYN | TH_FIN | TH_RST))
		(void)printf(" %u:%u(%u)", seq, seq + length, length);
	if (flags & TH_ACK)
		(void)printf(" ack %u", ack);

	(void)printf(" win %d", win);

	if (flags & TH_URG)
		(void)printf(" urg %d", urp);
	/*
	 * Handle any options.
	 */
	if (hlen > sizeof(*tp)) {
		register const u_char *cp;
		register u_int i, opt, datalen;
		register u_int len;

		hlen -= sizeof(*tp);
		cp = (const u_char *)tp + sizeof(*tp);
		putchar(' ');
		ch = '<';
		while (hlen > 0) {
			putchar(ch);
			TCHECK(*cp);
			opt = *cp++;
			if (ZEROLENOPT(opt))
				len = 1;
			else {
				TCHECK(*cp);
				len = *cp++;	/* total including type, len */
				if (len < 2 || len > hlen)
					goto bad;
				--hlen;		/* account for length byte */
			}
			--hlen;			/* account for type byte */
			datalen = 0;

/* Bail if "l" bytes of data are not left or were not captured  */
#define LENCHECK(l) { if ((l) > hlen) goto bad; TCHECK2(*cp, l); }

			switch (opt) {

			case TCPOPT_MAXSEG:
				(void)printf("mss");
				datalen = 2;
				LENCHECK(datalen);
				(void)printf(" %u", EXTRACT_16BITS(cp));

				break;

			case TCPOPT_EOL:
				(void)printf("eol");
				break;

			case TCPOPT_NOP:
				(void)printf("nop");
				break;

			case TCPOPT_WSCALE:
				(void)printf("wscale");
				datalen = 1;
				LENCHECK(datalen);
				(void)printf(" %u", *cp);
				break;

			case TCPOPT_SACKOK:
				(void)printf("sackOK");
				break;

			case TCPOPT_SACK:
				datalen = len - 2;
				if (datalen % 8 != 0) {
					(void)printf("malformed sack");
				} else {
					u_int32_t s, e;

					(void)printf("sack %d ", datalen / 8);
					for (i = 0; i < datalen; i += 8) {
						LENCHECK(i + 4);
						s = EXTRACT_32BITS(cp + i);
						LENCHECK(i + 8);
						e = EXTRACT_32BITS(cp + i + 4);
						if (threv) {
							s -= thseq;
							e -= thseq;
						} else {
							s -= thack;
							e -= thack;
						}
						(void)printf("{%u:%u}", s, e);
					}
				}
				break;

			case TCPOPT_ECHO:
				(void)printf("echo");
				datalen = 4;
				LENCHECK(datalen);
				(void)printf(" %u", EXTRACT_32BITS(cp));
				break;

			case TCPOPT_ECHOREPLY:
				(void)printf("echoreply");
				datalen = 4;
				LENCHECK(datalen);
				(void)printf(" %u", EXTRACT_32BITS(cp));
				break;

			case TCPOPT_TIMESTAMP:
				(void)printf("timestamp");
				datalen = 8;
				LENCHECK(4);
				(void)printf(" %u", EXTRACT_32BITS(cp));
				LENCHECK(datalen);
				(void)printf(" %u", EXTRACT_32BITS(cp + 4));
				break;

			case TCPOPT_CC:
				(void)printf("cc");
				datalen = 4;
				LENCHECK(datalen);
				(void)printf(" %u", EXTRACT_32BITS(cp));
				break;

			case TCPOPT_CCNEW:
				(void)printf("ccnew");
				datalen = 4;
				LENCHECK(datalen);
				(void)printf(" %u", EXTRACT_32BITS(cp));
				break;

			case TCPOPT_CCECHO:
				(void)printf("ccecho");
				datalen = 4;
				LENCHECK(datalen);
				(void)printf(" %u", EXTRACT_32BITS(cp));
				break;

			case TCPOPT_SIGNATURE:
				(void)printf("md5:");
				datalen = TCP_SIGLEN;
				LENCHECK(datalen);
#ifdef HAVE_LIBCRYPTO
				switch (tcp_verify_signature(ip, tp,
				    bp + TH_OFF(tp) * 4, length, cp)) {

				case SIGNATURE_VALID:
					(void)printf("valid");
					break;

				case SIGNATURE_INVALID:
					(void)printf("invalid");
					break;

				case CANT_CHECK_SIGNATURE:
					(void)printf("can't check - ");
					for (i = 0; i < TCP_SIGLEN; ++i)
						(void)printf("%02x", cp[i]);
					break;
				}
#else
				for (i = 0; i < TCP_SIGLEN; ++i)
					(void)printf("%02x", cp[i]);
#endif
				break;

			default:
				(void)printf("opt-%u:", opt);
				datalen = len - 2;
				for (i = 0; i < datalen; ++i) {
					LENCHECK(i);
					(void)printf("%02x", cp[i]);
				}
				break;
			}

			/* Account for data printed */
			cp += datalen;
			hlen -= datalen;

			/* Check specification against observed length */
			++datalen;			/* option octet */
			if (!ZEROLENOPT(opt))
				++datalen;		/* size octet */
			if (datalen != len)
				(void)printf("[len %d]", len);
			ch = ',';
			if (opt == TCPOPT_EOL)
				break;
		}
		putchar('>');
	}

	if (length <= 0)
		return;

	/*
	 * Decode payload if necessary.
	 */
	bp += TH_OFF(tp) * 4;
	if (flags & TH_RST) {
		if (vflag)
			print_tcp_rst_data(bp, length);
	} else {
		if (sport == TELNET_PORT || dport == TELNET_PORT) {
			if (!qflag && vflag)
				telnet_print(bp, length);
		} else if (sport == BGP_PORT || dport == BGP_PORT)
			bgp_print(bp, length);
		else if (sport == PPTP_PORT || dport == PPTP_PORT)
			pptp_print(bp);
#ifdef TCPDUMP_DO_SMB
		else if (sport == NETBIOS_SSN_PORT || dport == NETBIOS_SSN_PORT)
			nbt_tcp_print(bp, length);
#endif
		else if (sport == BEEP_PORT || dport == BEEP_PORT)
			beep_print(bp, length);
		else if (length > 2 &&
		    (sport == NAMESERVER_PORT || dport == NAMESERVER_PORT ||
		     sport == MULTICASTDNS_PORT || dport == MULTICASTDNS_PORT)) {
			/*
			 * TCP DNS query has 2byte length at the head.
			 * XXX packet could be unaligned, it can go strange
			 */
			ns_print(bp + 2, length - 2, 0);
		} else if (sport == MSDP_PORT || dport == MSDP_PORT) {
			msdp_print(bp, length);
		}
                else if (length > 0 && (sport == LDP_PORT || dport == LDP_PORT)) {
                        ldp_print(bp, length);
		}
	}
	return;
bad:
	fputs("[bad opt]", stdout);
	if (ch != '\0')
		putchar('>');
	return;
trunc:
	fputs("[|tcp]", stdout);
	if (ch != '\0')
		putchar('>');
}

/*
 * RFC1122 says the following on data in RST segments:
 *
 *         4.2.2.12  RST Segment: RFC-793 Section 3.4
 *
 *            A TCP SHOULD allow a received RST segment to include data.
 *
 *            DISCUSSION
 *                 It has been suggested that a RST segment could contain
 *                 ASCII text that encoded and explained the cause of the
 *                 RST.  No standard has yet been established for such
 *                 data.
 *
 */

static void
print_tcp_rst_data(register const u_char *sp, u_int length)
{
	int c;

	if (TTEST2(*sp, length))
		printf(" [RST");
	else
		printf(" [!RST");
	if (length > MAX_RST_DATA_LEN) {
		length = MAX_RST_DATA_LEN;	/* can use -X for longer */
		putchar('+');			/* indicate we truncate */
	}
	putchar(' ');
	while (length-- && sp <= snapend) {
		c = *sp++;
		safeputchar(c);
	}
	putchar(']');
}

#ifdef HAVE_LIBCRYPTO
static int
tcp_verify_signature(const struct ip *ip, const struct tcphdr *tp,
    const u_char *data, int length, const u_char *rcvsig)
{
        struct tcphdr tp1;
	u_char sig[TCP_SIGLEN];
	char zero_proto = 0;
	MD5_CTX ctx;
	u_int16_t savecsum, tlen;
#ifdef INET6
	struct ip6_hdr *ip6;
#endif
	u_int32_t len32;
	u_int8_t nxt;

	tp1 = *tp;

	if (tcpmd5secret == NULL)
		return (CANT_CHECK_SIGNATURE);

	MD5_Init(&ctx);
	/*
	 * Step 1: Update MD5 hash with IP pseudo-header.
	 */
	if (IP_V(ip) == 4) {
		MD5_Update(&ctx, (char *)&ip->ip_src, sizeof(ip->ip_src));
		MD5_Update(&ctx, (char *)&ip->ip_dst, sizeof(ip->ip_dst));
		MD5_Update(&ctx, (char *)&zero_proto, sizeof(zero_proto));
		MD5_Update(&ctx, (char *)&ip->ip_p, sizeof(ip->ip_p));
		tlen = EXTRACT_16BITS(&ip->ip_len) - IP_HL(ip) * 4;
		tlen = htons(tlen);
		MD5_Update(&ctx, (char *)&tlen, sizeof(tlen));
#ifdef INET6
	} else if (IP_V(ip) == 6) {
		ip6 = (struct ip6_hdr *)ip;
		MD5_Update(&ctx, (char *)&ip6->ip6_src, sizeof(ip6->ip6_src));
		MD5_Update(&ctx, (char *)&ip6->ip6_dst, sizeof(ip6->ip6_dst));
		len32 = htonl(ntohs(ip6->ip6_plen));
		MD5_Update(&ctx, (char *)&len32, sizeof(len32));
		nxt = 0;
		MD5_Update(&ctx, (char *)&nxt, sizeof(nxt));
		MD5_Update(&ctx, (char *)&nxt, sizeof(nxt));
		MD5_Update(&ctx, (char *)&nxt, sizeof(nxt));
		nxt = IPPROTO_TCP;
		MD5_Update(&ctx, (char *)&nxt, sizeof(nxt));
#endif
	} else
		return (CANT_CHECK_SIGNATURE);

	/*
	 * Step 2: Update MD5 hash with TCP header, excluding options.
	 * The TCP checksum must be set to zero.
	 */
	savecsum = tp1.th_sum;
	tp1.th_sum = 0;
	MD5_Update(&ctx, (char *)&tp1, sizeof(struct tcphdr));
	tp1.th_sum = savecsum;
	/*
	 * Step 3: Update MD5 hash with TCP segment data, if present.
	 */
	if (length > 0)
		MD5_Update(&ctx, data, length);
	/*
	 * Step 4: Update MD5 hash with shared secret.
	 */
	MD5_Update(&ctx, tcpmd5secret, strlen(tcpmd5secret));
	MD5_Final(sig, &ctx);

	if (memcmp(rcvsig, sig, TCP_SIGLEN) == 0)
		return (SIGNATURE_VALID);
	else
		return (SIGNATURE_INVALID);
}
#endif /* HAVE_LIBCRYPTO */
