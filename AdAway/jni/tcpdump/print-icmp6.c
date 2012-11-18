/*
 * Copyright (c) 1988, 1989, 1990, 1991, 1993, 1994
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
    "@(#) $Header: /tcpdump/master/tcpdump/print-icmp6.c,v 1.79.2.6 2005/09/05 09:29:28 guy Exp $";
#endif

#ifdef HAVE_CONFIG_H
#include "config.h"
#endif

#ifdef INET6

#include <tcpdump-stdinc.h>

#include <stdio.h>
#include <string.h>

#include "interface.h"
#include "addrtoname.h"
#include "extract.h"

#include "ip6.h"
#include "icmp6.h"
#include "ipproto.h"

#include "udp.h"
#include "ah.h"

static const char *get_rtpref(u_int);
static const char *get_lifetime(u_int32_t);
static void print_lladdr(const u_char *, size_t);
static void icmp6_opt_print(const u_char *, int);
static void mld6_print(const u_char *);
static void mldv2_report_print(const u_char *, u_int);
static void mldv2_query_print(const u_char *, u_int);
static struct udphdr *get_upperlayer(u_char *, u_int *);
static void dnsname_print(const u_char *, const u_char *);
static void icmp6_nodeinfo_print(u_int, const u_char *, const u_char *);
static void icmp6_rrenum_print(const u_char *, const u_char *);

#ifndef abs
#define abs(a)	((0 < (a)) ? (a) : -(a))
#endif

static struct tok icmp6_type_values[] = {
    { ICMP6_DST_UNREACH, "destination unreachable"},
    { ICMP6_PACKET_TOO_BIG, "packet too big"},
    { ICMP6_TIME_EXCEEDED, "time exceeded in-transit"},
    { ICMP6_PARAM_PROB, "parameter problem"},
    { ICMP6_ECHO_REQUEST, "echo request"},
    { ICMP6_ECHO_REPLY, "echo reply"},
    { MLD6_LISTENER_QUERY, "multicast listener query"},
    { MLD6_LISTENER_REPORT, "multicast listener report"},
    { MLD6_LISTENER_DONE, "multicast listener done"},
    { ND_ROUTER_SOLICIT, "router solicitation"},
    { ND_ROUTER_ADVERT, "router advertisement"},
    { ND_NEIGHBOR_SOLICIT, "neighbor solicitation"},
    { ND_NEIGHBOR_ADVERT, "neighbor advertisement"},
    { ND_REDIRECT, "redirect"},
    { ICMP6_ROUTER_RENUMBERING, "router renumbering"},
    { IND_SOLICIT, "inverse neighbor solicitation"},
    { IND_ADVERT, "inverse neighbor advertisement"},
    { MLDV2_LISTENER_REPORT, "multicast listener report v2"},
    { ICMP6_HADISCOV_REQUEST, "ha discovery request"},
    { ICMP6_HADISCOV_REPLY, "ha discovery reply"},
    { ICMP6_MOBILEPREFIX_SOLICIT, "mobile router solicitation"},
    { ICMP6_MOBILEPREFIX_ADVERT, "mobile router advertisement"},
    { ICMP6_WRUREQUEST, "who-are-you request"},
    { ICMP6_WRUREPLY, "who-are-you reply"},
    { ICMP6_NI_QUERY, "node information query"},
    { ICMP6_NI_REPLY, "node information reply"},
    { MLD6_MTRACE, "mtrace message"},
    { MLD6_MTRACE_RESP, "mtrace response"},
    { 0,	NULL }
};

static struct tok icmp6_dst_unreach_code_values[] = {
    { ICMP6_DST_UNREACH_NOROUTE, "unreachable route" },
    { ICMP6_DST_UNREACH_ADMIN, " unreachable prohibited"},
    { ICMP6_DST_UNREACH_BEYONDSCOPE, "beyond scope"},
    { ICMP6_DST_UNREACH_ADDR, "unreachable address"},
    { ICMP6_DST_UNREACH_NOPORT, "unreachable port"},
    { 0,	NULL }
};

static struct tok icmp6_opt_pi_flag_values[] = {
    { ND_OPT_PI_FLAG_ONLINK, "onlink" },
    { ND_OPT_PI_FLAG_AUTO, "auto" },
    { ND_OPT_PI_FLAG_ROUTER, "router" },
    { 0,	NULL }
};

static struct tok icmp6_opt_ra_flag_values[] = {
    { ND_RA_FLAG_MANAGED, "managed" },
    { ND_RA_FLAG_OTHER, "other stateful"},
    { ND_RA_FLAG_HOME_AGENT, "home agent"},
    { 0,	NULL }
};

static struct tok icmp6_nd_na_flag_values[] = {
    { ND_NA_FLAG_ROUTER, "router" },
    { ND_NA_FLAG_SOLICITED, "solicited" },
    { ND_NA_FLAG_OVERRIDE, "override" },
    { 0,	NULL }
};


static struct tok icmp6_opt_values[] = {
   { ND_OPT_SOURCE_LINKADDR, "source link-address"},
   { ND_OPT_TARGET_LINKADDR, "destination link-address"},
   { ND_OPT_PREFIX_INFORMATION, "prefix info"},
   { ND_OPT_REDIRECTED_HEADER, "redirected header"},
   { ND_OPT_MTU, "mtu"},
   { ND_OPT_ADVINTERVAL, "advertisement interval"},
   { ND_OPT_HOMEAGENT_INFO, "homeagent information"},
   { ND_OPT_ROUTE_INFO, "route info"},
   { 0,	NULL }
};

/* mldv2 report types */
static struct tok mldv2report2str[] = {
	{ 1,	"is_in" },
	{ 2,	"is_ex" },
	{ 3,	"to_in" },
	{ 4,	"to_ex" },
	{ 5,	"allow" },
	{ 6,	"block" },
	{ 0,	NULL }
};

static const char *
get_rtpref(u_int v)
{
	static const char *rtpref_str[] = {
		"medium",		/* 00 */
		"high",			/* 01 */
		"rsv",			/* 10 */
		"low"			/* 11 */
	};

	return rtpref_str[((v & ND_RA_FLAG_RTPREF_MASK) >> 3) & 0xff];
}

static const char *
get_lifetime(u_int32_t v)
{
	static char buf[20];

	if (v == (u_int32_t)~0UL)
		return "infinity";
	else {
		snprintf(buf, sizeof(buf), "%u", v);
		return buf;
	}
}

static void
print_lladdr(const u_int8_t *p, size_t l)
{
	const u_int8_t *ep, *q;

	q = p;
	ep = p + l;
	while (l > 0 && q < ep) {
		if (q > p)
			printf(":");
		printf("%02x", *q++);
		l--;
	}
}

static int icmp6_cksum(const struct ip6_hdr *ip6, const struct icmp6_hdr *icp,
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
	phu.ph.ph_nxt = IPPROTO_ICMPV6;

	sum = 0;
	for (i = 0; i < sizeof(phu.pa) / sizeof(phu.pa[0]); i++)
		sum += phu.pa[i];

	sp = (const u_int16_t *)icp;

	for (i = 0; i < (len & ~1); i += 2)
		sum += *sp++;

	if (len & 1)
		sum += htons((*(const u_int8_t *)sp) << 8);

	while (sum > 0xffff)
		sum = (sum & 0xffff) + (sum >> 16);
	sum = ~sum & 0xffff;

	return (sum);
}

void
icmp6_print(const u_char *bp, u_int length, const u_char *bp2, int fragmented)
{
	const struct icmp6_hdr *dp;
	const struct ip6_hdr *ip;
	const struct ip6_hdr *oip;
	const struct udphdr *ouh;
	int dport;
	const u_char *ep;
	u_int prot;

	dp = (struct icmp6_hdr *)bp;
	ip = (struct ip6_hdr *)bp2;
	oip = (struct ip6_hdr *)(dp + 1);
	/* 'ep' points to the end of available data. */
	ep = snapend;

	TCHECK(dp->icmp6_cksum);

	if (vflag && !fragmented) {
		int sum = dp->icmp6_cksum;

		if (TTEST2(bp[0], length)) {
			sum = icmp6_cksum(ip, dp, length);
			if (sum != 0)
				(void)printf("[bad icmp6 cksum %x!] ", sum);
			else
				(void)printf("[icmp6 sum ok] ");
		}
	}

        printf("ICMP6, %s", tok2str(icmp6_type_values,"unknown icmp6 type (%u)",dp->icmp6_type));

        /* display cosmetics: print the packet length for printer that use the vflag now */
        if (vflag && (dp->icmp6_type ==
                      ND_ROUTER_SOLICIT ||
                      ND_ROUTER_ADVERT ||
                      ND_NEIGHBOR_ADVERT ||
                      ND_NEIGHBOR_SOLICIT ||
                      ND_REDIRECT ||
                      ICMP6_HADISCOV_REPLY ||
                      ICMP6_MOBILEPREFIX_ADVERT ))
            printf(", length %u", length);
                      
	switch (dp->icmp6_type) {
	case ICMP6_DST_UNREACH:
		TCHECK(oip->ip6_dst);
                printf(", %s", tok2str(icmp6_dst_unreach_code_values,"unknown unreach code (%u)",dp->icmp6_code));
		switch (dp->icmp6_code) {

		case ICMP6_DST_UNREACH_NOROUTE: /* fall through */
		case ICMP6_DST_UNREACH_ADMIN:
		case ICMP6_DST_UNREACH_ADDR:
                        printf(" %s",ip6addr_string(&oip->ip6_dst));
                        break;
		case ICMP6_DST_UNREACH_BEYONDSCOPE:
			printf(" %s, source address %s",
			       ip6addr_string(&oip->ip6_dst),
			       ip6addr_string(&oip->ip6_src));
			break;
		case ICMP6_DST_UNREACH_NOPORT:
			if ((ouh = get_upperlayer((u_char *)oip, &prot))
			    == NULL)
				goto trunc;

			dport = EXTRACT_16BITS(&ouh->uh_dport);
			switch (prot) {
			case IPPROTO_TCP:
				printf(", %s tcp port %s",
					ip6addr_string(&oip->ip6_dst),
					tcpport_string(dport));
				break;
			case IPPROTO_UDP:
				printf(", %s udp port %s",
					ip6addr_string(&oip->ip6_dst),
					udpport_string(dport));
				break;
			default:
				printf(", %s protocol %d port %d unreachable",
					ip6addr_string(&oip->ip6_dst),
					oip->ip6_nxt, dport);
				break;
			}
			break;
		default:
                    if (vflag <= 1) {
                            print_unknown_data(bp,"\n\t",length);
                            return;
                    }
                    break;
		}
		break;
	case ICMP6_PACKET_TOO_BIG:
		TCHECK(dp->icmp6_mtu);
		printf(", mtu %u", EXTRACT_32BITS(&dp->icmp6_mtu));
		break;
	case ICMP6_TIME_EXCEEDED:
		TCHECK(oip->ip6_dst);
		switch (dp->icmp6_code) {
		case ICMP6_TIME_EXCEED_TRANSIT:
			printf(" for %s",
				ip6addr_string(&oip->ip6_dst));
			break;
		case ICMP6_TIME_EXCEED_REASSEMBLY:
			printf(" (reassembly)");
			break;
		default:
			printf(", unknown code (%u)", dp->icmp6_code);
			break;
		}
		break;
	case ICMP6_PARAM_PROB:
		TCHECK(oip->ip6_dst);
		switch (dp->icmp6_code) {
		case ICMP6_PARAMPROB_HEADER:
			printf(", errorneous - octet %u", EXTRACT_32BITS(&dp->icmp6_pptr));
			break;
		case ICMP6_PARAMPROB_NEXTHEADER:
			printf(", next header - octet %u", EXTRACT_32BITS(&dp->icmp6_pptr));
			break;
		case ICMP6_PARAMPROB_OPTION:
			printf(", option - octet %u", EXTRACT_32BITS(&dp->icmp6_pptr));
			break;
		default:
			printf(", code-#%d",
			       dp->icmp6_code);
			break;
		}
		break;
	case ICMP6_ECHO_REQUEST:
	case ICMP6_ECHO_REPLY:
		TCHECK(dp->icmp6_seq);
		printf(", seq %u", EXTRACT_16BITS(&dp->icmp6_seq));
		break;
	case ICMP6_MEMBERSHIP_QUERY:
		if (length == MLD_MINLEN) {
			mld6_print((const u_char *)dp);
		} else if (length >= MLDV2_MINLEN) {
			printf("v2 ");
			mldv2_query_print((const u_char *)dp, length);
		} else {
			printf(" unknown-version (len %u) ", length);
		}
		break;
	case ICMP6_MEMBERSHIP_REPORT:
		mld6_print((const u_char *)dp);
		break;
	case ICMP6_MEMBERSHIP_REDUCTION:
		mld6_print((const u_char *)dp);
		break;
	case ND_ROUTER_SOLICIT:
#define RTSOLLEN 8
		if (vflag) {
			icmp6_opt_print((const u_char *)dp + RTSOLLEN,
					length - RTSOLLEN);
		}
		break;
	case ND_ROUTER_ADVERT:
#define RTADVLEN 16
		if (vflag) {
			struct nd_router_advert *p;

			p = (struct nd_router_advert *)dp;
			TCHECK(p->nd_ra_retransmit);
			printf("\n\thop limit %u, Flags [%s]" \
                               ", pref %s, router lifetime %us, reachable time %us, retrans time %us",
                               (u_int)p->nd_ra_curhoplimit,
                               bittok2str(icmp6_opt_ra_flag_values,"none",(p->nd_ra_flags_reserved)),
                               get_rtpref(p->nd_ra_flags_reserved),
                               EXTRACT_16BITS(&p->nd_ra_router_lifetime),
                               EXTRACT_32BITS(&p->nd_ra_reachable),
                               EXTRACT_32BITS(&p->nd_ra_retransmit));

			icmp6_opt_print((const u_char *)dp + RTADVLEN,
					length - RTADVLEN);
		}
		break;
	case ND_NEIGHBOR_SOLICIT:
	    {
		struct nd_neighbor_solicit *p;
		p = (struct nd_neighbor_solicit *)dp;
		TCHECK(p->nd_ns_target);
		printf(", who has %s", ip6addr_string(&p->nd_ns_target));
		if (vflag) {
#define NDSOLLEN 24
			icmp6_opt_print((const u_char *)dp + NDSOLLEN,
					length - NDSOLLEN);
		}
	    }
		break;
	case ND_NEIGHBOR_ADVERT:
	    {
		struct nd_neighbor_advert *p;

		p = (struct nd_neighbor_advert *)dp;
		TCHECK(p->nd_na_target);
		printf(", tgt is %s",
			ip6addr_string(&p->nd_na_target));
		if (vflag) {
                        printf(", Flags [%s]",
                               bittok2str(icmp6_nd_na_flag_values,
                                          "none",
                                          EXTRACT_32BITS(&p->nd_na_flags_reserved)));
#define NDADVLEN 24
			icmp6_opt_print((const u_char *)dp + NDADVLEN,
					length - NDADVLEN);
#undef NDADVLEN
		}
	    }
		break;
	case ND_REDIRECT:
#define RDR(i) ((struct nd_redirect *)(i))
		TCHECK(RDR(dp)->nd_rd_dst);
		printf(", %s", getname6((const u_char *)&RDR(dp)->nd_rd_dst));
		TCHECK(RDR(dp)->nd_rd_target);
		printf(" to %s",
		    getname6((const u_char*)&RDR(dp)->nd_rd_target));
#define REDIRECTLEN 40
		if (vflag) {
			icmp6_opt_print((const u_char *)dp + REDIRECTLEN,
					length - REDIRECTLEN);
		}
		break;
#undef REDIRECTLEN
#undef RDR
	case ICMP6_ROUTER_RENUMBERING:
		icmp6_rrenum_print(bp, ep);
		break;
	case ICMP6_NI_QUERY:
	case ICMP6_NI_REPLY:
		icmp6_nodeinfo_print(length, bp, ep);
		break;
	case IND_SOLICIT:
	case IND_ADVERT:
		break;
	case ICMP6_V2_MEMBERSHIP_REPORT:
		mldv2_report_print((const u_char *) dp, length);
		break;
	case ICMP6_MOBILEPREFIX_SOLICIT: /* fall through */
	case ICMP6_HADISCOV_REQUEST:
                TCHECK(dp->icmp6_data16[0]);
                printf(", id 0x%04x", EXTRACT_16BITS(&dp->icmp6_data16[0]));
                break;
	case ICMP6_HADISCOV_REPLY:
		if (vflag) {
			struct in6_addr *in6;
			u_char *cp;

			TCHECK(dp->icmp6_data16[0]);
			printf(", id 0x%04x", EXTRACT_16BITS(&dp->icmp6_data16[0]));
			cp = (u_char *)dp + length;
			in6 = (struct in6_addr *)(dp + 1);
			for (; (u_char *)in6 < cp; in6++) {
				TCHECK(*in6);
				printf(", %s", ip6addr_string(in6));
			}
		}
		break;
	case ICMP6_MOBILEPREFIX_ADVERT:
		if (vflag) {
			TCHECK(dp->icmp6_data16[0]);
			printf(", id 0x%04x", EXTRACT_16BITS(&dp->icmp6_data16[0]));
			if (dp->icmp6_data16[1] & 0xc0)
				printf(" ");
			if (dp->icmp6_data16[1] & 0x80)
				printf("M");
			if (dp->icmp6_data16[1] & 0x40)
				printf("O");
#define MPADVLEN 8
			icmp6_opt_print((const u_char *)dp + MPADVLEN,
					length - MPADVLEN);
		}
		break;
	default:
                printf(", length %u", length);
                if (vflag <= 1)
                    print_unknown_data(bp,"\n\t", length);
                return;
        }
        if (!vflag)
            printf(", length %u", length); 
	return;
trunc:
	fputs("[|icmp6]", stdout);
}

static struct udphdr *
get_upperlayer(u_char *bp, u_int *prot)
{
	const u_char *ep;
	struct ip6_hdr *ip6 = (struct ip6_hdr *)bp;
	struct udphdr *uh;
	struct ip6_hbh *hbh;
	struct ip6_frag *fragh;
	struct ah *ah;
	u_int nh;
	int hlen;

	/* 'ep' points to the end of available data. */
	ep = snapend;

	if (!TTEST(ip6->ip6_nxt))
		return NULL;

	nh = ip6->ip6_nxt;
	hlen = sizeof(struct ip6_hdr);

	while (bp < ep) {
		bp += hlen;

		switch(nh) {
		case IPPROTO_UDP:
		case IPPROTO_TCP:
			uh = (struct udphdr *)bp;
			if (TTEST(uh->uh_dport)) {
				*prot = nh;
				return(uh);
			}
			else
				return(NULL);
			/* NOTREACHED */

		case IPPROTO_HOPOPTS:
		case IPPROTO_DSTOPTS:
		case IPPROTO_ROUTING:
			hbh = (struct ip6_hbh *)bp;
			if (!TTEST(hbh->ip6h_len))
				return(NULL);
			nh = hbh->ip6h_nxt;
			hlen = (hbh->ip6h_len + 1) << 3;
			break;

		case IPPROTO_FRAGMENT: /* this should be odd, but try anyway */
			fragh = (struct ip6_frag *)bp;
			if (!TTEST(fragh->ip6f_offlg))
				return(NULL);
			/* fragments with non-zero offset are meaningless */
			if ((EXTRACT_16BITS(&fragh->ip6f_offlg) & IP6F_OFF_MASK) != 0)
				return(NULL);
			nh = fragh->ip6f_nxt;
			hlen = sizeof(struct ip6_frag);
			break;

		case IPPROTO_AH:
			ah = (struct ah *)bp;
			if (!TTEST(ah->ah_len))
				return(NULL);
			nh = ah->ah_nxt;
			hlen = (ah->ah_len + 2) << 2;
			break;

		default:	/* unknown or undecodable header */
			*prot = nh; /* meaningless, but set here anyway */
			return(NULL);
		}
	}

	return(NULL);		/* should be notreached, though */
}

static void
icmp6_opt_print(const u_char *bp, int resid)
{
	const struct nd_opt_hdr *op;
	const struct nd_opt_hdr *opl;	/* why there's no struct? */
	const struct nd_opt_prefix_info *opp;
	const struct icmp6_opts_redirect *opr;
	const struct nd_opt_mtu *opm;
	const struct nd_opt_advinterval *opa;
	const struct nd_opt_homeagent_info *oph;
	const struct nd_opt_route_info *opri;
	const u_char *cp, *ep;
	struct in6_addr in6, *in6p;
	size_t l;

#define ECHECK(var) if ((u_char *)&(var) > ep - sizeof(var)) return

	cp = bp;
	/* 'ep' points to the end of available data. */
	ep = snapend;

	while (cp < ep) {
		op = (struct nd_opt_hdr *)cp;

		ECHECK(op->nd_opt_len);
		if (resid <= 0)
			return;
		if (op->nd_opt_len == 0)
			goto trunc;
		if (cp + (op->nd_opt_len << 3) > ep)
			goto trunc;

                printf("\n\t  %s option (%u), length %u (%u): ",
                       tok2str(icmp6_opt_values, "unknown", op->nd_opt_type),
                       op->nd_opt_type,
                       op->nd_opt_len << 3,
                       op->nd_opt_len);

		switch (op->nd_opt_type) {
		case ND_OPT_SOURCE_LINKADDR:
			opl = (struct nd_opt_hdr *)op;
			l = (op->nd_opt_len << 3) - 2;
			print_lladdr(cp + 2, l);
			break;
		case ND_OPT_TARGET_LINKADDR:
			opl = (struct nd_opt_hdr *)op;
			l = (op->nd_opt_len << 3) - 2;
			print_lladdr(cp + 2, l);
			break;
		case ND_OPT_PREFIX_INFORMATION:
			opp = (struct nd_opt_prefix_info *)op;
			TCHECK(opp->nd_opt_pi_prefix);
                        printf("%s/%u%s, Flags [%s], valid time %ss",
                               ip6addr_string(&opp->nd_opt_pi_prefix),
                               opp->nd_opt_pi_prefix_len,
                               (op->nd_opt_len != 4) ? "badlen" : "",
                               bittok2str(icmp6_opt_pi_flag_values, "none", opp->nd_opt_pi_flags_reserved),
                               get_lifetime(EXTRACT_32BITS(&opp->nd_opt_pi_valid_time)));
                        printf(", pref. time %ss", get_lifetime(EXTRACT_32BITS(&opp->nd_opt_pi_preferred_time)));
			break;
		case ND_OPT_REDIRECTED_HEADER:
			opr = (struct icmp6_opts_redirect *)op;
                        print_unknown_data(bp,"\n\t    ",op->nd_opt_len<<3);
			/* xxx */
			break;
		case ND_OPT_MTU:
			opm = (struct nd_opt_mtu *)op;
			TCHECK(opm->nd_opt_mtu_mtu);
			printf(" %u%s",
                               EXTRACT_32BITS(&opm->nd_opt_mtu_mtu),
                               (op->nd_opt_len != 1) ? "bad option length" : "" );
                        break;
		case ND_OPT_ADVINTERVAL:
			opa = (struct nd_opt_advinterval *)op;
			TCHECK(opa->nd_opt_adv_interval);
			printf(" %us", EXTRACT_32BITS(&opa->nd_opt_adv_interval));
			break;
		case ND_OPT_HOMEAGENT_INFO:
			oph = (struct nd_opt_homeagent_info *)op;
			TCHECK(oph->nd_opt_hai_lifetime);
			printf(" preference %u, lifetime %u",
                               EXTRACT_16BITS(&oph->nd_opt_hai_preference),
                               EXTRACT_16BITS(&oph->nd_opt_hai_lifetime));
			break;
		case ND_OPT_ROUTE_INFO:
			opri = (struct nd_opt_route_info *)op;
			TCHECK(opri->nd_opt_rti_lifetime);
			memset(&in6, 0, sizeof(in6));
			in6p = (struct in6_addr *)(opri + 1);
			switch (op->nd_opt_len) {
			case 1:
				break;
			case 2:
				TCHECK2(*in6p, 8);
				memcpy(&in6, opri + 1, 8);
				break;
			case 3:
				TCHECK(*in6p);
				memcpy(&in6, opri + 1, sizeof(in6));
				break;
			default:
				goto trunc;
			}
			printf(" %s/%u", ip6addr_string(&in6),
			    opri->nd_opt_rti_prefixlen);
			printf(", pref=%s", get_rtpref(opri->nd_opt_rti_flags));
			printf(", lifetime=%s",
			    get_lifetime(EXTRACT_32BITS(&opri->nd_opt_rti_lifetime)));
			break;
		default:
                        if (vflag <= 1) {
                            print_unknown_data(cp+2,"\n\t  ", (op->nd_opt_len << 3) - 2); /* skip option header */
                            return;
                        }
                        break;
		}
                /* do we want to see an additional hexdump ? */
                if (vflag> 1)
                    print_unknown_data(cp+2,"\n\t    ", (op->nd_opt_len << 3) - 2); /* skip option header */

		cp += op->nd_opt_len << 3;
		resid -= op->nd_opt_len << 3;
	}
	return;

 trunc:
	fputs("[ndp opt]", stdout);
	return;
#undef ECHECK
}

static void
mld6_print(const u_char *bp)
{
	struct mld6_hdr *mp = (struct mld6_hdr *)bp;
	const u_char *ep;

	/* 'ep' points to the end of available data. */
	ep = snapend;

	if ((u_char *)mp + sizeof(*mp) > ep)
		return;

	printf("max resp delay: %d ", EXTRACT_16BITS(&mp->mld6_maxdelay));
	printf("addr: %s", ip6addr_string(&mp->mld6_addr));
}

static void
mldv2_report_print(const u_char *bp, u_int len)
{
    struct icmp6_hdr *icp = (struct icmp6_hdr *) bp;
    u_int group, nsrcs, ngroups;
    u_int i, j;

    /* Minimum len is 8 */
    if (len < 8) {
	printf(" [invalid len %d]", len);
	return;
    }

    TCHECK(icp->icmp6_data16[1]);
    ngroups = ntohs(icp->icmp6_data16[1]);
    printf(", %d group record(s)", ngroups);
    if (vflag > 0) {
	/* Print the group records */
	group = 8;
        for (i = 0; i < ngroups; i++) {
	    /* type(1) + auxlen(1) + numsrc(2) + grp(16) */
	    if (len < group + 20) {
		printf(" [invalid number of groups]");
		return;
	    }
            TCHECK2(bp[group + 4], sizeof(struct in6_addr));
            printf(" [gaddr %s", ip6addr_string(&bp[group + 4]));
	    printf(" %s", tok2str(mldv2report2str, " [v2-report-#%d]",
								bp[group]));
            nsrcs = (bp[group + 2] << 8) + bp[group + 3];
	    /* Check the number of sources and print them */
	    if (len < group + 20 + (nsrcs * sizeof(struct in6_addr))) {
		printf(" [invalid number of sources %d]", nsrcs);
		return;
	    }
            if (vflag == 1)
                printf(", %d source(s)", nsrcs);
            else {
		/* Print the sources */
                (void)printf(" {");
                for (j = 0; j < nsrcs; j++) {
                    TCHECK2(bp[group + 20 + j * sizeof(struct in6_addr)],
                            sizeof(struct in6_addr));
		    printf(" %s", ip6addr_string(&bp[group + 20 + j * sizeof(struct in6_addr)]));
		}
                (void)printf(" }");
            }
	    /* Next group record */
            group += 20 + nsrcs * sizeof(struct in6_addr);
	    printf("]");
        }
    }
    return;
trunc:
    (void)printf("[|icmp6]");
    return;
}

static void
mldv2_query_print(const u_char *bp, u_int len)
{
    struct icmp6_hdr *icp = (struct icmp6_hdr *) bp;
    u_int mrc;
    int mrt, qqi;
    u_int nsrcs;
    register u_int i;

    /* Minimum len is 28 */
    if (len < 28) {
	printf(" [invalid len %d]", len);
	return;
    }
    TCHECK(icp->icmp6_data16[0]);
    mrc = ntohs(icp->icmp6_data16[0]);
    if (mrc < 32768) {
	mrt = mrc;
    } else {
        mrt = ((mrc & 0x0fff) | 0x1000) << (((mrc & 0x7000) >> 12) + 3);
    }
    if (vflag) {
	(void)printf(" [max resp delay=%d]", mrt);
    }
    TCHECK2(bp[8], sizeof(struct in6_addr));
    printf(" [gaddr %s", ip6addr_string(&bp[8]));

    if (vflag) {
        TCHECK(bp[25]);
	if (bp[24] & 0x08) {
		printf(" sflag");
	}
	if (bp[24] & 0x07) {
		printf(" robustness=%d", bp[24] & 0x07);
	}
	if (bp[25] < 128) {
		qqi = bp[25];
	} else {
		qqi = ((bp[25] & 0x0f) | 0x10) << (((bp[25] & 0x70) >> 4) + 3);
	}
	printf(" qqi=%d", qqi);
    }

    TCHECK2(bp[26], 2);
    nsrcs = ntohs(*(u_short *)&bp[26]);
    if (nsrcs > 0) {
	if (len < 28 + nsrcs * sizeof(struct in6_addr))
	    printf(" [invalid number of sources]");
	else if (vflag > 1) {
	    printf(" {");
	    for (i = 0; i < nsrcs; i++) {
		TCHECK2(bp[28 + i * sizeof(struct in6_addr)],
                        sizeof(struct in6_addr));
		printf(" %s", ip6addr_string(&bp[28 + i * sizeof(struct in6_addr)]));
	    }
	    printf(" }");
	} else
	    printf(", %d source(s)", nsrcs);
    }
    printf("]");
    return;
trunc:
    (void)printf("[|icmp6]");
    return;
}

void
dnsname_print(const u_char *cp, const u_char *ep)
{
	int i;

	/* DNS name decoding - no decompression */
	printf(", \"");
	while (cp < ep) {
		i = *cp++;
		if (i) {
			if (i > ep - cp) {
				printf("???");
				break;
			}
			while (i-- && cp < ep) {
				safeputchar(*cp);
				cp++;
			}
			if (cp + 1 < ep && *cp)
				printf(".");
		} else {
			if (cp == ep) {
				/* FQDN */
				printf(".");
			} else if (cp + 1 == ep && *cp == '\0') {
				/* truncated */
			} else {
				/* invalid */
				printf("???");
			}
			break;
		}
	}
	printf("\"");
}

static void
icmp6_nodeinfo_print(u_int icmp6len, const u_char *bp, const u_char *ep)
{
	struct icmp6_nodeinfo *ni6;
	struct icmp6_hdr *dp;
	const u_char *cp;
	size_t siz, i;
	int needcomma;

	if (ep < bp)
		return;
	dp = (struct icmp6_hdr *)bp;
	ni6 = (struct icmp6_nodeinfo *)bp;
	siz = ep - bp;

	switch (ni6->ni_type) {
	case ICMP6_NI_QUERY:
		if (siz == sizeof(*dp) + 4) {
			/* KAME who-are-you */
			printf(" who-are-you request");
			break;
		}
		printf(" node information query");

		TCHECK2(*dp, sizeof(*ni6));
		ni6 = (struct icmp6_nodeinfo *)dp;
		printf(" (");	/*)*/
		switch (EXTRACT_16BITS(&ni6->ni_qtype)) {
		case NI_QTYPE_NOOP:
			printf("noop");
			break;
		case NI_QTYPE_SUPTYPES:
			printf("supported qtypes");
			i = EXTRACT_16BITS(&ni6->ni_flags);
			if (i)
				printf(" [%s]", (i & 0x01) ? "C" : "");
			break;
			break;
		case NI_QTYPE_FQDN:
			printf("DNS name");
			break;
		case NI_QTYPE_NODEADDR:
			printf("node addresses");
			i = ni6->ni_flags;
			if (!i)
				break;
			/* NI_NODEADDR_FLAG_TRUNCATE undefined for query */
			printf(" [%s%s%s%s%s%s]",
			    (i & NI_NODEADDR_FLAG_ANYCAST) ? "a" : "",
			    (i & NI_NODEADDR_FLAG_GLOBAL) ? "G" : "",
			    (i & NI_NODEADDR_FLAG_SITELOCAL) ? "S" : "",
			    (i & NI_NODEADDR_FLAG_LINKLOCAL) ? "L" : "",
			    (i & NI_NODEADDR_FLAG_COMPAT) ? "C" : "",
			    (i & NI_NODEADDR_FLAG_ALL) ? "A" : "");
			break;
		default:
			printf("unknown");
			break;
		}

		if (ni6->ni_qtype == NI_QTYPE_NOOP ||
		    ni6->ni_qtype == NI_QTYPE_SUPTYPES) {
			if (siz != sizeof(*ni6))
				if (vflag)
					printf(", invalid len");
			/*(*/
			printf(")");
			break;
		}


		/* XXX backward compat, icmp-name-lookup-03 */
		if (siz == sizeof(*ni6)) {
			printf(", 03 draft");
			/*(*/
			printf(")");
			break;
		}

		switch (ni6->ni_code) {
		case ICMP6_NI_SUBJ_IPV6:
			if (!TTEST2(*dp,
			    sizeof(*ni6) + sizeof(struct in6_addr)))
				break;
			if (siz != sizeof(*ni6) + sizeof(struct in6_addr)) {
				if (vflag)
					printf(", invalid subject len");
				break;
			}
			printf(", subject=%s",
			    getname6((const u_char *)(ni6 + 1)));
			break;
		case ICMP6_NI_SUBJ_FQDN:
			printf(", subject=DNS name");
			cp = (const u_char *)(ni6 + 1);
			if (cp[0] == ep - cp - 1) {
				/* icmp-name-lookup-03, pascal string */
				if (vflag)
					printf(", 03 draft");
				cp++;
				printf(", \"");
				while (cp < ep) {
					safeputchar(*cp);
					cp++;
				}
				printf("\"");
			} else
				dnsname_print(cp, ep);
			break;
		case ICMP6_NI_SUBJ_IPV4:
			if (!TTEST2(*dp, sizeof(*ni6) + sizeof(struct in_addr)))
				break;
			if (siz != sizeof(*ni6) + sizeof(struct in_addr)) {
				if (vflag)
					printf(", invalid subject len");
				break;
			}
			printf(", subject=%s",
			    getname((const u_char *)(ni6 + 1)));
			break;
		default:
			printf(", unknown subject");
			break;
		}

		/*(*/
		printf(")");
		break;

	case ICMP6_NI_REPLY:
		if (icmp6len > siz) {
			printf("[|icmp6: node information reply]");
			break;
		}

		needcomma = 0;

		ni6 = (struct icmp6_nodeinfo *)dp;
		printf(" node information reply");
		printf(" (");	/*)*/
		switch (ni6->ni_code) {
		case ICMP6_NI_SUCCESS:
			if (vflag) {
				printf("success");
				needcomma++;
			}
			break;
		case ICMP6_NI_REFUSED:
			printf("refused");
			needcomma++;
			if (siz != sizeof(*ni6))
				if (vflag)
					printf(", invalid length");
			break;
		case ICMP6_NI_UNKNOWN:
			printf("unknown");
			needcomma++;
			if (siz != sizeof(*ni6))
				if (vflag)
					printf(", invalid length");
			break;
		}

		if (ni6->ni_code != ICMP6_NI_SUCCESS) {
			/*(*/
			printf(")");
			break;
		}

		switch (EXTRACT_16BITS(&ni6->ni_qtype)) {
		case NI_QTYPE_NOOP:
			if (needcomma)
				printf(", ");
			printf("noop");
			if (siz != sizeof(*ni6))
				if (vflag)
					printf(", invalid length");
			break;
		case NI_QTYPE_SUPTYPES:
			if (needcomma)
				printf(", ");
			printf("supported qtypes");
			i = EXTRACT_16BITS(&ni6->ni_flags);
			if (i)
				printf(" [%s]", (i & 0x01) ? "C" : "");
			break;
		case NI_QTYPE_FQDN:
			if (needcomma)
				printf(", ");
			printf("DNS name");
			cp = (const u_char *)(ni6 + 1) + 4;
			if (cp[0] == ep - cp - 1) {
				/* icmp-name-lookup-03, pascal string */
				if (vflag)
					printf(", 03 draft");
				cp++;
				printf(", \"");
				while (cp < ep) {
					safeputchar(*cp);
					cp++;
				}
				printf("\"");
			} else
				dnsname_print(cp, ep);
			if ((EXTRACT_16BITS(&ni6->ni_flags) & 0x01) != 0)
				printf(" [TTL=%u]", *(u_int32_t *)(ni6 + 1));
			break;
		case NI_QTYPE_NODEADDR:
			if (needcomma)
				printf(", ");
			printf("node addresses");
			i = sizeof(*ni6);
			while (i < siz) {
				if (i + sizeof(struct in6_addr) + sizeof(int32_t) > siz)
					break;
				printf(" %s", getname6(bp + i));
				i += sizeof(struct in6_addr);
				printf("(%d)", (int32_t)EXTRACT_32BITS(bp + i));
				i += sizeof(int32_t);
			}
			i = ni6->ni_flags;
			if (!i)
				break;
			printf(" [%s%s%s%s%s%s%s]",
			    (i & NI_NODEADDR_FLAG_ANYCAST) ? "a" : "",
			    (i & NI_NODEADDR_FLAG_GLOBAL) ? "G" : "",
			    (i & NI_NODEADDR_FLAG_SITELOCAL) ? "S" : "",
			    (i & NI_NODEADDR_FLAG_LINKLOCAL) ? "L" : "",
			    (i & NI_NODEADDR_FLAG_COMPAT) ? "C" : "",
			    (i & NI_NODEADDR_FLAG_ALL) ? "A" : "",
			    (i & NI_NODEADDR_FLAG_TRUNCATE) ? "T" : "");
			break;
		default:
			if (needcomma)
				printf(", ");
			printf("unknown");
			break;
		}

		/*(*/
		printf(")");
		break;
	}
	return;

trunc:
	fputs("[|icmp6]", stdout);
}

static void
icmp6_rrenum_print(const u_char *bp, const u_char *ep)
{
	struct icmp6_router_renum *rr6;
	const char *cp;
	struct rr_pco_match *match;
	struct rr_pco_use *use;
	char hbuf[NI_MAXHOST];
	int n;

	if (ep < bp)
		return;
	rr6 = (struct icmp6_router_renum *)bp;
	cp = (const char *)(rr6 + 1);

	TCHECK(rr6->rr_reserved);
	switch (rr6->rr_code) {
	case ICMP6_ROUTER_RENUMBERING_COMMAND:
		printf("router renum: command");
		break;
	case ICMP6_ROUTER_RENUMBERING_RESULT:
		printf("router renum: result");
		break;
	case ICMP6_ROUTER_RENUMBERING_SEQNUM_RESET:
		printf("router renum: sequence number reset");
		break;
	default:
		printf("router renum: code-#%d", rr6->rr_code);
		break;
	}

	printf(", seq=%u", EXTRACT_32BITS(&rr6->rr_seqnum));

	if (vflag) {
#define F(x, y)	((rr6->rr_flags) & (x) ? (y) : "")
		printf("[");	/*]*/
		if (rr6->rr_flags) {
			printf("%s%s%s%s%s,", F(ICMP6_RR_FLAGS_TEST, "T"),
			    F(ICMP6_RR_FLAGS_REQRESULT, "R"),
			    F(ICMP6_RR_FLAGS_FORCEAPPLY, "A"),
			    F(ICMP6_RR_FLAGS_SPECSITE, "S"),
			    F(ICMP6_RR_FLAGS_PREVDONE, "P"));
		}
		printf("seg=%u,", rr6->rr_segnum);
		printf("maxdelay=%u", rr6->rr_maxdelay);
		if (rr6->rr_reserved)
			printf("rsvd=0x%x", EXTRACT_16BITS(&rr6->rr_reserved));
		/*[*/
		printf("]");
#undef F
	}

	if (rr6->rr_code == ICMP6_ROUTER_RENUMBERING_COMMAND) {
		match = (struct rr_pco_match *)cp;
		cp = (const char *)(match + 1);

		TCHECK(match->rpm_prefix);

		if (vflag > 1)
			printf("\n\t");
		else
			printf(" ");
		printf("match(");	/*)*/
		switch (match->rpm_code) {
		case RPM_PCO_ADD:	printf("add"); break;
		case RPM_PCO_CHANGE:	printf("change"); break;
		case RPM_PCO_SETGLOBAL:	printf("setglobal"); break;
		default:		printf("#%u", match->rpm_code); break;
		}

		if (vflag) {
			printf(",ord=%u", match->rpm_ordinal);
			printf(",min=%u", match->rpm_minlen);
			printf(",max=%u", match->rpm_maxlen);
		}
		if (inet_ntop(AF_INET6, &match->rpm_prefix, hbuf, sizeof(hbuf)))
			printf(",%s/%u", hbuf, match->rpm_matchlen);
		else
			printf(",?/%u", match->rpm_matchlen);
		/*(*/
		printf(")");

		n = match->rpm_len - 3;
		if (n % 4)
			goto trunc;
		n /= 4;
		while (n-- > 0) {
			use = (struct rr_pco_use *)cp;
			cp = (const char *)(use + 1);

			TCHECK(use->rpu_prefix);

			if (vflag > 1)
				printf("\n\t");
			else
				printf(" ");
			printf("use(");	/*)*/
			if (use->rpu_flags) {
#define F(x, y)	((use->rpu_flags) & (x) ? (y) : "")
				printf("%s%s,",
				    F(ICMP6_RR_PCOUSE_FLAGS_DECRVLTIME, "V"),
				    F(ICMP6_RR_PCOUSE_FLAGS_DECRPLTIME, "P"));
#undef F
			}
			if (vflag) {
				printf("mask=0x%x,", use->rpu_ramask);
				printf("raflags=0x%x,", use->rpu_raflags);
				if (~use->rpu_vltime == 0)
					printf("vltime=infty,");
				else
					printf("vltime=%u,",
					    EXTRACT_32BITS(&use->rpu_vltime));
				if (~use->rpu_pltime == 0)
					printf("pltime=infty,");
				else
					printf("pltime=%u,",
					    EXTRACT_32BITS(&use->rpu_pltime));
			}
			if (inet_ntop(AF_INET6, &use->rpu_prefix, hbuf,
			    sizeof(hbuf)))
				printf("%s/%u/%u", hbuf, use->rpu_uselen,
				    use->rpu_keeplen);
			else
				printf("?/%u/%u", use->rpu_uselen,
				    use->rpu_keeplen);
			/*(*/
			printf(")");
		}
	}

	return;

trunc:
	fputs("[|icmp6]", stdout);
}

#endif /* INET6 */
