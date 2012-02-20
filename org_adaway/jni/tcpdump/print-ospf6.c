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
 * OSPF support contributed by Jeffrey Honig (jch@mitchell.cit.cornell.edu)
 */

#ifndef lint
static const char rcsid[] _U_ =
    "@(#) $Header: /tcpdump/master/tcpdump/print-ospf6.c,v 1.13 2003/11/16 09:36:31 guy Exp $ (LBL)";
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

#include "ospf6.h"

struct bits {
	u_int32_t bit;
	const char *str;
};

static const struct bits ospf6_option_bits[] = {
	{ OSPF6_OPTION_V6,	"V6" },
	{ OSPF6_OPTION_E,	"E" },
	{ OSPF6_OPTION_MC,	"MC" },
	{ OSPF6_OPTION_N,	"N" },
	{ OSPF6_OPTION_R,	"R" },
	{ OSPF6_OPTION_DC,	"DC" },
	{ 0,			NULL }
};

static const struct bits ospf6_rla_flag_bits[] = {
	{ RLA_FLAG_B,		"B" },
	{ RLA_FLAG_E,		"E" },
	{ RLA_FLAG_V,		"V" },
	{ RLA_FLAG_W,		"W" },
	{ 0,			NULL }
};

static const struct bits ospf6_asla_flag_bits[] = {
	{ ASLA_FLAG_EXTERNAL,	"E" },
	{ ASLA_FLAG_FWDADDR,	"F" },
	{ ASLA_FLAG_ROUTETAG,	"T" },
	{ 0,			NULL }
};

static struct tok type2str[] = {
	{ OSPF_TYPE_UMD,	"umd" },
	{ OSPF_TYPE_HELLO,	"hello" },
	{ OSPF_TYPE_DB,		"dd" },
	{ OSPF_TYPE_LSR,	"ls_req" },
	{ OSPF_TYPE_LSU,	"ls_upd" },
	{ OSPF_TYPE_LSA,	"ls_ack" },
	{ 0,			NULL }
};

static char tstr[] = " [|ospf]";

#ifdef WIN32
#define inline __inline
#endif /* WIN32 */

/* Forwards */
static inline void ospf6_print_seqage(u_int32_t, time_t);
static inline void ospf6_print_bits(const struct bits *, u_char);
static void ospf6_print_ls_type(u_int, const rtrid_t *,
    const rtrid_t *, const char *);
static int ospf6_print_lshdr(const struct lsa_hdr *);
static int ospf6_print_lsa(const struct lsa *);
static int ospf6_decode_v3(const struct ospf6hdr *, const u_char *);

static inline void
ospf6_print_seqage(register u_int32_t seq, register time_t us)
{
	register time_t sec = us % 60;
	register time_t mins = (us / 60) % 60;
	register time_t hour = us / 3600;

	printf(" S %X age ", seq);
	if (hour)
		printf("%u:%02u:%02u",
		    (u_int32_t) hour, (u_int32_t) mins, (u_int32_t) sec);
	else if (mins)
		printf("%u:%02u", (u_int32_t) mins, (u_int32_t) sec);
	else
		printf("%u", (u_int32_t) sec);
}


static inline void
ospf6_print_bits(register const struct bits *bp, register u_char options)
{
	register char sep = ' ';

	do {
		if (options & bp->bit) {
			printf("%c%s", sep, bp->str);
			sep = '/';
		}
	} while ((++bp)->bit);
}

static void
ospf6_print_ls_type(register u_int ls_type,
    register const rtrid_t *ls_stateid,
    register const rtrid_t *ls_router, register const char *fmt)
{
	const char *scope;

	switch (ls_type & LS_SCOPE_MASK) {
	case LS_SCOPE_LINKLOCAL:
		scope = "linklocal-";
		break;
	case LS_SCOPE_AREA:
		scope = "area-";
		break;
	case LS_SCOPE_AS:
		scope = "AS-";
		break;
	default:
		scope = "";
		break;
	}

	switch (ls_type & LS_TYPE_MASK) {
	case LS_TYPE_ROUTER:
		printf(" %srtr %s", scope, ipaddr_string(ls_router));
		break;

	case LS_TYPE_NETWORK:
		printf(" %snet dr %s if %s", scope,
		    ipaddr_string(ls_router),
		    ipaddr_string(ls_stateid));
		break;

	case LS_TYPE_INTER_AP:
		printf(" %sinter-area-prefix %s abr %s", scope,
		    ipaddr_string(ls_stateid),
		    ipaddr_string(ls_router));
		break;

	case LS_TYPE_INTER_AR:
		printf(" %sinter-area-router %s rtr %s", scope,
		    ipaddr_string(ls_router),
		    ipaddr_string(ls_stateid));
		break;

	case LS_TYPE_ASE:
		printf(" %sase %s asbr %s", scope,
		    ipaddr_string(ls_stateid),
		    ipaddr_string(ls_router));
		break;

	case LS_TYPE_GROUP:
		printf(" %sgroup %s rtr %s", scope,
		    ipaddr_string(ls_stateid),
		    ipaddr_string(ls_router));
		break;

	case LS_TYPE_TYPE7:
		printf(" %stype7 %s rtr %s", scope,
		    ipaddr_string(ls_stateid),
		    ipaddr_string(ls_router));
		break;

	case LS_TYPE_LINK:
		printf(" %slink %s rtr %s", scope,
		    ipaddr_string(ls_stateid),
		    ipaddr_string(ls_router));
		break;

	case LS_TYPE_INTRA_AP:
		printf(" %sintra-area-prefix %s rtr %s", scope,
		    ipaddr_string(ls_stateid),
		    ipaddr_string(ls_router));
		break;

	default:
		printf(" %s", scope);
		printf(fmt, ls_type);
		break;
	}

}

static int
ospf6_print_lshdr(register const struct lsa_hdr *lshp)
{

	TCHECK(lshp->ls_type);
	printf(" {");						/* } (ctags) */

	TCHECK(lshp->ls_seq);
	ospf6_print_seqage(EXTRACT_32BITS(&lshp->ls_seq), EXTRACT_16BITS(&lshp->ls_age));
	ospf6_print_ls_type(EXTRACT_16BITS(&lshp->ls_type), &lshp->ls_stateid,
		&lshp->ls_router, "ls_type %d");

	return (0);
trunc:
	return (1);
}

static int
ospf6_print_lsaprefix(register const struct lsa_prefix *lsapp)
{
	u_int k;
	struct in6_addr prefix;

	TCHECK(*lsapp);
	k = (lsapp->lsa_p_len + 31) / 32;
	if (k * 4 > sizeof(struct in6_addr)) {
		printf("??prefixlen %d??", lsapp->lsa_p_len);
		goto trunc;
	}
	memset(&prefix, 0, sizeof(prefix));
	memcpy(&prefix, lsapp->lsa_p_prefix, k * 4);
	printf(" %s/%d", ip6addr_string(&prefix),
		lsapp->lsa_p_len);
	if (lsapp->lsa_p_opt)
		printf("(opt=%x)", lsapp->lsa_p_opt);
	if (lsapp->lsa_p_mbz)
		printf("(mbz=%x)", EXTRACT_16BITS(&lsapp->lsa_p_mbz)); /* XXX */
	return sizeof(*lsapp) - 4 + k * 4;

trunc:
	return -1;
}


/*
 * Print a single link state advertisement.  If truncated return 1, else 0.
 */
static int
ospf6_print_lsa(register const struct lsa *lsap)
{
	register const u_char *ls_end, *ls_opt;
	register const struct rlalink *rlp;
#if 0
	register const struct tos_metric *tosp;
#endif
	register const rtrid_t *ap;
#if 0
	register const struct aslametric *almp;
	register const struct mcla *mcp;
#endif
	register const struct llsa *llsap;
	register const struct lsa_prefix *lsapp;
#if 0
	register const u_int32_t *lp;
#endif
	register u_int j;
	register int k;
	u_int32_t flags32;

	if (ospf6_print_lshdr(&lsap->ls_hdr))
		return (1);
	TCHECK(lsap->ls_hdr.ls_length);
	ls_end = (u_char *)lsap + EXTRACT_16BITS(&lsap->ls_hdr.ls_length);
	switch (EXTRACT_16BITS(&lsap->ls_hdr.ls_type)) {
	case LS_TYPE_ROUTER | LS_SCOPE_AREA:
		TCHECK(lsap->lsa_un.un_rla.rla_flags);
		ospf6_print_bits(ospf6_rla_flag_bits,
			lsap->lsa_un.un_rla.rla_flags);
		TCHECK(lsap->lsa_un.un_rla.rla_options);
		ospf6_print_bits(ospf6_option_bits,
			EXTRACT_32BITS(&lsap->lsa_un.un_rla.rla_options));

		TCHECK(lsap->lsa_un.un_rla.rla_link);
		rlp = lsap->lsa_un.un_rla.rla_link;
		while (rlp + sizeof(*rlp) <= (struct rlalink *)ls_end) {
			TCHECK(*rlp);
			printf(" {");				/* } (ctags) */
			switch (rlp->link_type) {

			case RLA_TYPE_VIRTUAL:
				printf(" virt");
				/* Fall through */

			case RLA_TYPE_ROUTER:
				printf(" nbrid %s nbrif %s if %s",
				    ipaddr_string(&rlp->link_nrtid),
				    ipaddr_string(&rlp->link_nifid),
				    ipaddr_string(&rlp->link_ifid));
				break;

			case RLA_TYPE_TRANSIT:
				printf(" dr %s drif %s if %s",
				    ipaddr_string(&rlp->link_nrtid),
				    ipaddr_string(&rlp->link_nifid),
				    ipaddr_string(&rlp->link_ifid));
				break;

			default:
								/* { (ctags) */
				printf(" ??RouterLinksType 0x%02x?? }",
				    rlp->link_type);
				return (0);
			}
			printf(" metric %d", EXTRACT_16BITS(&rlp->link_metric));
								/* { (ctags) */
			printf(" }");
			rlp++;
		}
		break;

	case LS_TYPE_NETWORK | LS_SCOPE_AREA:
		TCHECK(lsap->lsa_un.un_nla.nla_options);
		ospf6_print_bits(ospf6_option_bits,
			EXTRACT_32BITS(&lsap->lsa_un.un_nla.nla_options));
		printf(" rtrs");
		ap = lsap->lsa_un.un_nla.nla_router;
		while ((u_char *)ap < ls_end) {
			TCHECK(*ap);
			printf(" %s", ipaddr_string(ap));
			++ap;
		}
		break;

	case LS_TYPE_INTER_AP | LS_SCOPE_AREA:
		TCHECK(lsap->lsa_un.un_inter_ap.inter_ap_metric);
		printf(" metric %u",
			EXTRACT_32BITS(&lsap->lsa_un.un_inter_ap.inter_ap_metric) & SLA_MASK_METRIC);
		lsapp = lsap->lsa_un.un_inter_ap.inter_ap_prefix;
		while (lsapp + sizeof(lsapp) <= (struct lsa_prefix *)ls_end) {
			k = ospf6_print_lsaprefix(lsapp);
			if (k)
				goto trunc;
			lsapp = (struct lsa_prefix *)(((u_char *)lsapp) + k);
		}
		break;
	case LS_SCOPE_AS | LS_TYPE_ASE:
		TCHECK(lsap->lsa_un.un_asla.asla_metric);
		flags32 = EXTRACT_32BITS(&lsap->lsa_un.un_asla.asla_metric);
		ospf6_print_bits(ospf6_asla_flag_bits, flags32);
		printf(" metric %u",
		       EXTRACT_32BITS(&lsap->lsa_un.un_asla.asla_metric) &
		       ASLA_MASK_METRIC);
		lsapp = lsap->lsa_un.un_asla.asla_prefix;
		k = ospf6_print_lsaprefix(lsapp);
		if (k < 0)
			goto trunc;
		if ((ls_opt = (u_char *)(((u_char *)lsapp) + k)) < ls_end) {
			struct in6_addr *fwdaddr6;

			if ((flags32 & ASLA_FLAG_FWDADDR) != 0) {
				fwdaddr6 = (struct in6_addr *)ls_opt;
				TCHECK(*fwdaddr6);
				printf(" forward %s",
				       ip6addr_string(fwdaddr6));

				ls_opt += sizeof(struct in6_addr);
			}

			if ((flags32 & ASLA_FLAG_ROUTETAG) != 0) {
				TCHECK(*(u_int32_t *)ls_opt);
				printf(" tag %s",
				       ipaddr_string((u_int32_t *)ls_opt));

				ls_opt += sizeof(u_int32_t);
			}

			if (lsapp->lsa_p_mbz) {
				TCHECK(*(u_int32_t *)ls_opt);
				printf(" RefLSID: %s",
				       ipaddr_string((u_int32_t *)ls_opt));

				ls_opt += sizeof(u_int32_t);
			}
		}
		break;
#if 0
	case LS_TYPE_SUM_ABR:
		TCHECK(lsap->lsa_un.un_sla.sla_tosmetric);
		lp = lsap->lsa_un.un_sla.sla_tosmetric;
		while ((u_char *)lp < ls_end) {
			register u_int32_t ul;

			TCHECK(*lp);
			ul = EXTRACT_32BITS(lp);
			printf(" tos %d metric %d",
			    (ul & SLA_MASK_TOS) >> SLA_SHIFT_TOS,
			    ul & SLA_MASK_METRIC);
			++lp;
		}
		break;

	case LS_TYPE_GROUP:
		/* Multicast extensions as of 23 July 1991 */
		mcp = lsap->lsa_un.un_mcla;
		while ((u_char *)mcp < ls_end) {
			TCHECK(mcp->mcla_vid);
			switch (EXTRACT_32BITS(&mcp->mcla_vtype)) {

			case MCLA_VERTEX_ROUTER:
				printf(" rtr rtrid %s",
				    ipaddr_string(&mcp->mcla_vid));
				break;

			case MCLA_VERTEX_NETWORK:
				printf(" net dr %s",
				    ipaddr_string(&mcp->mcla_vid));
				break;

			default:
				printf(" ??VertexType %u??",
				    EXTRACT_32BITS(&mcp->mcla_vtype));
				break;
			}
		++mcp;
		}
#endif

	case LS_TYPE_LINK:
		/* Link LSA */
		llsap = &lsap->lsa_un.un_llsa;
		TCHECK(llsap->llsa_options);
		ospf6_print_bits(ospf6_option_bits, EXTRACT_32BITS(&llsap->llsa_options));
		TCHECK(llsap->llsa_nprefix);
		printf(" pri %d lladdr %s npref %d", llsap->llsa_priority,
			ip6addr_string(&llsap->llsa_lladdr),
			EXTRACT_32BITS(&llsap->llsa_nprefix));
		lsapp = llsap->llsa_prefix;
		for (j = 0; j < EXTRACT_32BITS(&llsap->llsa_nprefix); j++) {
			k = ospf6_print_lsaprefix(lsapp);
			if (k)
				goto trunc;
			lsapp = (struct lsa_prefix *)(((u_char *)lsapp) + k);
		}
		break;

	case LS_TYPE_INTRA_AP | LS_SCOPE_AREA:
		/* Intra-Area-Prefix LSA */
		TCHECK(lsap->lsa_un.un_intra_ap.intra_ap_rtid);
		ospf6_print_ls_type(
			EXTRACT_16BITS(&lsap->lsa_un.un_intra_ap.intra_ap_lstype),
			&lsap->lsa_un.un_intra_ap.intra_ap_lsid,
			&lsap->lsa_un.un_intra_ap.intra_ap_rtid,
			"LinkStateType %d");
		TCHECK(lsap->lsa_un.un_intra_ap.intra_ap_nprefix);
		printf(" npref %d",
			EXTRACT_16BITS(&lsap->lsa_un.un_intra_ap.intra_ap_nprefix));

		lsapp = lsap->lsa_un.un_intra_ap.intra_ap_prefix;
		for (j = 0;
		     j < EXTRACT_16BITS(&lsap->lsa_un.un_intra_ap.intra_ap_nprefix);
		     j++) {
			k = ospf6_print_lsaprefix(lsapp);
			if (k)
				goto trunc;
			lsapp = (struct lsa_prefix *)(((u_char *)lsapp) + k);
		}
		break;

	default:
		printf(" ??LinkStateType 0x%04x??",
			EXTRACT_16BITS(&lsap->ls_hdr.ls_type));
	}

								/* { (ctags) */
	fputs(" }", stdout);
	return (0);
trunc:
	fputs(" }", stdout);
	return (1);
}

static int
ospf6_decode_v3(register const struct ospf6hdr *op,
    register const u_char *dataend)
{
	register const rtrid_t *ap;
	register const struct lsr *lsrp;
	register const struct lsa_hdr *lshp;
	register const struct lsa *lsap;
	register char sep;
	register int i;

	switch (op->ospf6_type) {

	case OSPF_TYPE_UMD:
		/*
		 * Rob Coltun's special monitoring packets;
		 * do nothing
		 */
		break;

	case OSPF_TYPE_HELLO:
		if (vflag) {
			TCHECK(op->ospf6_hello.hello_deadint);
			ospf6_print_bits(ospf6_option_bits,
			    EXTRACT_32BITS(&op->ospf6_hello.hello_options));
			printf(" ifid %s pri %d int %d dead %u",
			    ipaddr_string(&op->ospf6_hello.hello_ifid),
			    op->ospf6_hello.hello_priority,
			    EXTRACT_16BITS(&op->ospf6_hello.hello_helloint),
			    EXTRACT_16BITS(&op->ospf6_hello.hello_deadint));
		}
		TCHECK(op->ospf6_hello.hello_dr);
		if (op->ospf6_hello.hello_dr != 0)
			printf(" dr %s",
			    ipaddr_string(&op->ospf6_hello.hello_dr));
		TCHECK(op->ospf6_hello.hello_bdr);
		if (op->ospf6_hello.hello_bdr != 0)
			printf(" bdr %s",
			    ipaddr_string(&op->ospf6_hello.hello_bdr));
		if (vflag) {
			printf(" nbrs");
			ap = op->ospf6_hello.hello_neighbor;
			while ((u_char *)ap < dataend) {
				TCHECK(*ap);
				printf(" %s", ipaddr_string(ap));
				++ap;
			}
		}
		break;	/* HELLO */

	case OSPF_TYPE_DB:
		TCHECK(op->ospf6_db.db_options);
		ospf6_print_bits(ospf6_option_bits,
			EXTRACT_32BITS(&op->ospf6_db.db_options));
		sep = ' ';
		TCHECK(op->ospf6_db.db_flags);
		if (op->ospf6_db.db_flags & OSPF6_DB_INIT) {
			printf("%cI", sep);
			sep = '/';
		}
		if (op->ospf6_db.db_flags & OSPF6_DB_MORE) {
			printf("%cM", sep);
			sep = '/';
		}
		if (op->ospf6_db.db_flags & OSPF6_DB_MASTER) {
			printf("%cMS", sep);
			sep = '/';
		}
		TCHECK(op->ospf6_db.db_seq);
		printf(" mtu %u S %X", EXTRACT_16BITS(&op->ospf6_db.db_mtu),
			EXTRACT_32BITS(&op->ospf6_db.db_seq));

		if (vflag) {
			/* Print all the LS adv's */
			lshp = op->ospf6_db.db_lshdr;

			while (!ospf6_print_lshdr(lshp)) {
							/* { (ctags) */
				printf(" }");
				++lshp;
			}
		}
		break;

	case OSPF_TYPE_LSR:
		if (vflag) {
			lsrp = op->ospf6_lsr;
			while ((u_char *)lsrp < dataend) {
				TCHECK(*lsrp);
				printf(" {");		/* } (ctags) */
				ospf6_print_ls_type(EXTRACT_16BITS(&lsrp->ls_type),
				    &lsrp->ls_stateid,
				    &lsrp->ls_router,
				    "LinkStateType %d");
							/* { (ctags) */
				printf(" }");
				++lsrp;
			}
		}
		break;

	case OSPF_TYPE_LSU:
		if (vflag) {
			lsap = op->ospf6_lsu.lsu_lsa;
			TCHECK(op->ospf6_lsu.lsu_count);
			i = EXTRACT_32BITS(&op->ospf6_lsu.lsu_count);
			while (i--) {
				if (ospf6_print_lsa(lsap))
					goto trunc;
				lsap = (struct lsa *)((u_char *)lsap +
				    EXTRACT_16BITS(&lsap->ls_hdr.ls_length));
			}
		}
		break;


	case OSPF_TYPE_LSA:
		if (vflag) {
			lshp = op->ospf6_lsa.lsa_lshdr;

			while (!ospf6_print_lshdr(lshp)) {
							/* { (ctags) */
				printf(" }");
				++lshp;
			}
		}
		break;

	default:
		printf("v3 type %d", op->ospf6_type);
		break;
	}
	return (0);
trunc:
	return (1);
}

void
ospf6_print(register const u_char *bp, register u_int length)
{
	register const struct ospf6hdr *op;
	register const u_char *dataend;
	register const char *cp;

	op = (struct ospf6hdr *)bp;

	/* If the type is valid translate it, or just print the type */
	/* value.  If it's not valid, say so and return */
	TCHECK(op->ospf6_type);
	cp = tok2str(type2str, "type%d", op->ospf6_type);
	printf("OSPFv%d-%s %d:", op->ospf6_version, cp, length);
	if (*cp == 't')
		return;

	TCHECK(op->ospf6_len);
	if (length != EXTRACT_16BITS(&op->ospf6_len)) {
		printf(" [len %d]", EXTRACT_16BITS(&op->ospf6_len));
		return;
	}
	dataend = bp + length;

	/* Print the routerid if it is not the same as the source */
	TCHECK(op->ospf6_routerid);
	printf(" rtrid %s", ipaddr_string(&op->ospf6_routerid));

	TCHECK(op->ospf6_areaid);
	if (op->ospf6_areaid != 0)
		printf(" area %s", ipaddr_string(&op->ospf6_areaid));
	else
		printf(" backbone");
	TCHECK(op->ospf6_instanceid);
	if (op->ospf6_instanceid)
		printf(" instance %u", op->ospf6_instanceid);

	/* Do rest according to version.	 */
	switch (op->ospf6_version) {

	case 3:
		/* ospf version 3 */
		if (ospf6_decode_v3(op, dataend))
			goto trunc;
		break;

	default:
		printf(" ospf [version %d]", op->ospf6_version);
		break;
	}			/* end switch on version */

	return;
trunc:
	fputs(tstr, stdout);
}
