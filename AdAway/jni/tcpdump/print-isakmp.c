/*
 * Copyright (C) 1995, 1996, 1997, and 1998 WIDE Project.
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
 *
 */

#ifndef lint
static const char rcsid[] _U_ =
    "@(#) $Header: /tcpdump/master/tcpdump/print-isakmp.c,v 1.61 2008-02-05 19:34:25 guy Exp $ (LBL)";
#endif

#define NETDISSECT_REWORKED
#ifdef HAVE_CONFIG_H
#include "config.h"
#endif

/* The functions from print-esp.c used in this file are only defined when both
 * OpenSSL and evp.h are detected. Employ the same preprocessor device here.
 */
#ifndef HAVE_OPENSSL_EVP_H
#undef HAVE_LIBCRYPTO
#endif

#include <tcpdump-stdinc.h>

#include <string.h>

#include <stdio.h>

#include "isakmp.h"
#include "ipsec_doi.h"
#include "oakley.h"
#include "interface.h"
#include "addrtoname.h"
#include "extract.h"                    /* must come after interface.h */

#include "ip.h"
#ifdef INET6
#include "ip6.h"
#endif

#ifndef HAVE_SOCKADDR_STORAGE
#define sockaddr_storage sockaddr
#endif

#define DECLARE_PRINTER(func) static const u_char *ike##func##_print( \
		netdissect_options *ndo, u_char tpay,	              \
		const struct isakmp_gen *ext,			      \
		u_int item_len, \
		const u_char *end_pointer, \
		u_int32_t phase,\
		u_int32_t doi0, \
		u_int32_t proto0, int depth)

DECLARE_PRINTER(v1_sa);
DECLARE_PRINTER(v1_p);
DECLARE_PRINTER(v1_t);
DECLARE_PRINTER(v1_ke);
DECLARE_PRINTER(v1_id);
DECLARE_PRINTER(v1_cert);
DECLARE_PRINTER(v1_cr);
DECLARE_PRINTER(v1_sig);
DECLARE_PRINTER(v1_hash);
DECLARE_PRINTER(v1_nonce);
DECLARE_PRINTER(v1_n);
DECLARE_PRINTER(v1_d);
DECLARE_PRINTER(v1_vid);

DECLARE_PRINTER(v2_sa);
DECLARE_PRINTER(v2_ke);
DECLARE_PRINTER(v2_ID);
DECLARE_PRINTER(v2_cert);
DECLARE_PRINTER(v2_cr);
DECLARE_PRINTER(v2_auth);
DECLARE_PRINTER(v2_nonce);
DECLARE_PRINTER(v2_n);
DECLARE_PRINTER(v2_d);
DECLARE_PRINTER(v2_vid);
DECLARE_PRINTER(v2_TS);
DECLARE_PRINTER(v2_cp);
DECLARE_PRINTER(v2_eap);

static const u_char *ikev2_e_print(netdissect_options *ndo,
				   struct isakmp *base,
				   u_char tpay,
				   const struct isakmp_gen *ext,
				   u_int item_len,	
				   const u_char *end_pointer, 
				   u_int32_t phase,
				   u_int32_t doi0, 
				   u_int32_t proto0, int depth);


static const u_char *ike_sub0_print(netdissect_options *ndo,u_char, const struct isakmp_gen *,
	const u_char *,	u_int32_t, u_int32_t, u_int32_t, int);
static const u_char *ikev1_sub_print(netdissect_options *ndo,u_char, const struct isakmp_gen *,
	const u_char *, u_int32_t, u_int32_t, u_int32_t, int);

static const u_char *ikev2_sub_print(netdissect_options *ndo,
				     struct isakmp *base,
				     u_char np, const struct isakmp_gen *ext,
				     const u_char *ep, u_int32_t phase,
				     u_int32_t doi, u_int32_t proto,
				     int depth);


static char *numstr(int);

static void
ikev1_print(netdissect_options *ndo,
	    const u_char *bp,  u_int length,
	    const u_char *bp2, struct isakmp *base);

#define MAXINITIATORS	20
int ninitiator = 0;
struct {
	cookie_t initiator;
	struct sockaddr_storage iaddr;
	struct sockaddr_storage raddr;
} cookiecache[MAXINITIATORS];

/* protocol id */
static const char *protoidstr[] = {
	NULL, "isakmp", "ipsec-ah", "ipsec-esp", "ipcomp",
};

/* isakmp->np */
static const char *npstr[] = {
	"none", "sa", "p", "t", "ke", "id", "cert", "cr", "hash", /* 0 - 8 */
	"sig", "nonce", "n", "d", "vid",      /* 9 - 13 */
	"pay14", "pay15", "pay16", "pay17", "pay18", /* 14- 18 */
	"pay19", "pay20", "pay21", "pay22", "pay23", /* 19- 23 */
	"pay24", "pay25", "pay26", "pay27", "pay28", /* 24- 28 */
	"pay29", "pay30", "pay31", "pay32",          /* 29- 32 */
	"v2sa",  "v2ke",  "v2IDi", "v2IDr", "v2cert",/* 33- 37 */
	"v2cr",  "v2auth","v2nonce", "v2n",   "v2d",   /* 38- 42 */
	"v2vid", "v2TSi", "v2TSr", "v2e",   "v2cp",  /* 43- 47 */
	"v2eap",                                     /* 48 */
	
};

/* isakmp->np */
static const u_char *(*npfunc[])(netdissect_options *ndo, u_char tpay, 
				 const struct isakmp_gen *ext,
				 u_int item_len,
				 const u_char *end_pointer,
				 u_int32_t phase,
				 u_int32_t doi0,
				 u_int32_t proto0, int depth) = {
	NULL,
	ikev1_sa_print,
	ikev1_p_print,
	ikev1_t_print,
	ikev1_ke_print,
	ikev1_id_print,
	ikev1_cert_print,
	ikev1_cr_print,
	ikev1_hash_print,
	ikev1_sig_print,
	ikev1_nonce_print,
	ikev1_n_print,
	ikev1_d_print,
	ikev1_vid_print,                  /* 13 */
	NULL, NULL, NULL, NULL, NULL,     /* 14- 18 */
	NULL, NULL, NULL, NULL, NULL,     /* 19- 23 */
	NULL, NULL, NULL, NULL, NULL,     /* 24- 28 */
	NULL, NULL, NULL, NULL,           /* 29- 32 */
	ikev2_sa_print,                 /* 33 */
	ikev2_ke_print,                 /* 34 */
	ikev2_ID_print,                 /* 35 */
	ikev2_ID_print,                 /* 36 */
	ikev2_cert_print,               /* 37 */
	ikev2_cr_print,                 /* 38 */
	ikev2_auth_print,               /* 39 */
	ikev2_nonce_print,              /* 40 */
	ikev2_n_print,                  /* 41 */
	ikev2_d_print,                  /* 42 */
	ikev2_vid_print,                /* 43 */
	ikev2_TS_print,                 /* 44 */
	ikev2_TS_print,                 /* 45 */
	NULL, /* ikev2_e_print,*/       /* 46 - special */
	ikev2_cp_print,                 /* 47 */
	ikev2_eap_print,                /* 48 */
};

/* isakmp->etype */
static const char *etypestr[] = {
/* IKEv1 exchange types */
	"none", "base", "ident", "auth", "agg", "inf", NULL, NULL,  /* 0-7 */
	NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,  /*  8-15 */
	NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,  /* 16-23 */
	NULL, NULL, NULL, NULL, NULL, NULL, NULL, NULL,  /* 24-31 */
	"oakley-quick", "oakley-newgroup",               /* 32-33 */
/* IKEv2 exchange types */
	"ikev2_init", "ikev2_auth", "child_sa", "inf2"   /* 34-37 */
};

#define STR_OR_ID(x, tab) \
	(((x) < sizeof(tab)/sizeof(tab[0]) && tab[(x)])	? tab[(x)] : numstr(x))
#define PROTOIDSTR(x)	STR_OR_ID(x, protoidstr)
#define NPSTR(x)	STR_OR_ID(x, npstr)
#define ETYPESTR(x)	STR_OR_ID(x, etypestr)

#define CHECKLEN(p, np)							\
		if (ep < (u_char *)(p)) {				\
			ND_PRINT((ndo," [|%s]", NPSTR(np)));		\
			goto done;					\
		}
		

#define NPFUNC(x) \
	(((x) < sizeof(npfunc)/sizeof(npfunc[0]) && npfunc[(x)]) \
		? npfunc[(x)] : NULL)

static int
iszero(u_char *p, size_t l)
{
	while (l--) {
		if (*p++)
			return 0;
	}
	return 1;
}

/* find cookie from initiator cache */
static int
cookie_find(cookie_t *in)
{
	int i;

	for (i = 0; i < MAXINITIATORS; i++) {
		if (memcmp(in, &cookiecache[i].initiator, sizeof(*in)) == 0)
			return i;
	}

	return -1;
}

/* record initiator */
static void
cookie_record(cookie_t *in, const u_char *bp2)
{
	int i;
	struct ip *ip;
	struct sockaddr_in *sin;
#ifdef INET6
	struct ip6_hdr *ip6;
	struct sockaddr_in6 *sin6;
#endif

	i = cookie_find(in);
	if (0 <= i) {
		ninitiator = (i + 1) % MAXINITIATORS;
		return;
	}

	ip = (struct ip *)bp2;
	switch (IP_V(ip)) {
	case 4:
		memset(&cookiecache[ninitiator].iaddr, 0,
			sizeof(cookiecache[ninitiator].iaddr));
		memset(&cookiecache[ninitiator].raddr, 0,
			sizeof(cookiecache[ninitiator].raddr));

		sin = (struct sockaddr_in *)&cookiecache[ninitiator].iaddr;
#ifdef HAVE_SOCKADDR_SA_LEN
		sin->sin_len = sizeof(struct sockaddr_in);
#endif
		sin->sin_family = AF_INET;
		UNALIGNED_MEMCPY(&sin->sin_addr, &ip->ip_src, sizeof(ip->ip_src));
		sin = (struct sockaddr_in *)&cookiecache[ninitiator].raddr;
#ifdef HAVE_SOCKADDR_SA_LEN
		sin->sin_len = sizeof(struct sockaddr_in);
#endif
		sin->sin_family = AF_INET;
		UNALIGNED_MEMCPY(&sin->sin_addr, &ip->ip_dst, sizeof(ip->ip_dst));
		break;
#ifdef INET6
	case 6:
		memset(&cookiecache[ninitiator].iaddr, 0,
			sizeof(cookiecache[ninitiator].iaddr));
		memset(&cookiecache[ninitiator].raddr, 0,
			sizeof(cookiecache[ninitiator].raddr));

		ip6 = (struct ip6_hdr *)bp2;
		sin6 = (struct sockaddr_in6 *)&cookiecache[ninitiator].iaddr;
#ifdef HAVE_SOCKADDR_SA_LEN
		sin6->sin6_len = sizeof(struct sockaddr_in6);
#endif
		sin6->sin6_family = AF_INET6;
		UNALIGNED_MEMCPY(&sin6->sin6_addr, &ip6->ip6_src, sizeof(ip6->ip6_src));
		sin6 = (struct sockaddr_in6 *)&cookiecache[ninitiator].raddr;
#ifdef HAVE_SOCKADDR_SA_LEN
		sin6->sin6_len = sizeof(struct sockaddr_in6);
#endif
		sin6->sin6_family = AF_INET6;
		UNALIGNED_MEMCPY(&sin6->sin6_addr, &ip6->ip6_dst, sizeof(ip6->ip6_dst));
		break;
#endif
	default:
		return;
	}
	UNALIGNED_MEMCPY(&cookiecache[ninitiator].initiator, in, sizeof(*in));
	ninitiator = (ninitiator + 1) % MAXINITIATORS;
}

#define cookie_isinitiator(x, y)	cookie_sidecheck((x), (y), 1)
#define cookie_isresponder(x, y)	cookie_sidecheck((x), (y), 0)
static int
cookie_sidecheck(int i, const u_char *bp2, int initiator)
{
	struct sockaddr_storage ss;
	struct sockaddr *sa;
	struct ip *ip;
	struct sockaddr_in *sin;
#ifdef INET6
	struct ip6_hdr *ip6;
	struct sockaddr_in6 *sin6;
#endif
	int salen;

	memset(&ss, 0, sizeof(ss));
	ip = (struct ip *)bp2;
	switch (IP_V(ip)) {
	case 4:
		sin = (struct sockaddr_in *)&ss;
#ifdef HAVE_SOCKADDR_SA_LEN
		sin->sin_len = sizeof(struct sockaddr_in);
#endif
		sin->sin_family = AF_INET;
		UNALIGNED_MEMCPY(&sin->sin_addr, &ip->ip_src, sizeof(ip->ip_src));
		break;
#ifdef INET6
	case 6:
		ip6 = (struct ip6_hdr *)bp2;
		sin6 = (struct sockaddr_in6 *)&ss;
#ifdef HAVE_SOCKADDR_SA_LEN
		sin6->sin6_len = sizeof(struct sockaddr_in6);
#endif
		sin6->sin6_family = AF_INET6;
		UNALIGNED_MEMCPY(&sin6->sin6_addr, &ip6->ip6_src, sizeof(ip6->ip6_src));
		break;
#endif
	default:
		return 0;
	}

	sa = (struct sockaddr *)&ss;
	if (initiator) {
		if (sa->sa_family != ((struct sockaddr *)&cookiecache[i].iaddr)->sa_family)
			return 0;
#ifdef HAVE_SOCKADDR_SA_LEN
		salen = sa->sa_len;
#else
#ifdef INET6
		if (sa->sa_family == AF_INET6)
			salen = sizeof(struct sockaddr_in6);
		else
			salen = sizeof(struct sockaddr);
#else
		salen = sizeof(struct sockaddr);
#endif
#endif
		if (memcmp(&ss, &cookiecache[i].iaddr, salen) == 0)
			return 1;
	} else {
		if (sa->sa_family != ((struct sockaddr *)&cookiecache[i].raddr)->sa_family)
			return 0;
#ifdef HAVE_SOCKADDR_SA_LEN
		salen = sa->sa_len;
#else
#ifdef INET6
		if (sa->sa_family == AF_INET6)
			salen = sizeof(struct sockaddr_in6);
		else
			salen = sizeof(struct sockaddr);
#else
		salen = sizeof(struct sockaddr);
#endif
#endif
		if (memcmp(&ss, &cookiecache[i].raddr, salen) == 0)
			return 1;
	}
	return 0;
}

static void
hexprint(netdissect_options *ndo, caddr_t loc, size_t len)
{
	u_char *p;
	size_t i;

	p = (u_char *)loc;
	for (i = 0; i < len; i++)
		ND_PRINT((ndo,"%02x", p[i] & 0xff));
}

static int
rawprint(netdissect_options *ndo, caddr_t loc, size_t len)
{
	ND_TCHECK2(*loc, len);

	hexprint(ndo, loc, len);
	return 1;
trunc:
	return 0;
}


/*
 * returns false if we run out of data buffer
 */
static int ike_show_somedata(struct netdissect_options *ndo,
			     const u_char *cp, const u_char *ep)
{
	/* there is too much data, just show some of it */
	const u_char *end = ep - 20;
	int  elen = 20;
	int   len = ep - cp;
	if(len > 10) {
		len = 10;
	}
	
	/* really shouldn't happen because of above */
	if(end < cp + len) {
		end = cp+len;
		elen = ep - end;
	}
	
	ND_PRINT((ndo," data=("));
	if(!rawprint(ndo, (caddr_t)(cp), len)) goto trunc;
	ND_PRINT((ndo, "..."));
	if(elen) {
		if(!rawprint(ndo, (caddr_t)(end), elen)) goto trunc;
	}
	ND_PRINT((ndo,")"));
	return 1;

trunc:
	return 0;
}

struct attrmap {
	const char *type;
	u_int nvalue;
	const char *value[30];	/*XXX*/
};

static const u_char *
ikev1_attrmap_print(netdissect_options *ndo,
		    const u_char *p, const u_char *ep,
		    const struct attrmap *map, size_t nmap)
{
	int totlen;
	u_int32_t t, v;

	if (p[0] & 0x80)
		totlen = 4;
	else
		totlen = 4 + EXTRACT_16BITS(&p[2]);
	if (ep < p + totlen) {
		ND_PRINT((ndo,"[|attr]"));
		return ep + 1;
	}

	ND_PRINT((ndo,"("));
	t = EXTRACT_16BITS(&p[0]) & 0x7fff;
	if (map && t < nmap && map[t].type)
		ND_PRINT((ndo,"type=%s ", map[t].type));
	else
		ND_PRINT((ndo,"type=#%d ", t));
	if (p[0] & 0x80) {
		ND_PRINT((ndo,"value="));
		v = EXTRACT_16BITS(&p[2]);
		if (map && t < nmap && v < map[t].nvalue && map[t].value[v])
			ND_PRINT((ndo,"%s", map[t].value[v]));
		else
			rawprint(ndo, (caddr_t)&p[2], 2);
	} else {
		ND_PRINT((ndo,"len=%d value=", EXTRACT_16BITS(&p[2])));
		rawprint(ndo, (caddr_t)&p[4], EXTRACT_16BITS(&p[2]));
	}
	ND_PRINT((ndo,")"));
	return p + totlen;
}

static const u_char *
ikev1_attr_print(netdissect_options *ndo, const u_char *p, const u_char *ep)
{
	int totlen;
	u_int32_t t;

	if (p[0] & 0x80)
		totlen = 4;
	else
		totlen = 4 + EXTRACT_16BITS(&p[2]);
	if (ep < p + totlen) {
		ND_PRINT((ndo,"[|attr]"));
		return ep + 1;
	}

	ND_PRINT((ndo,"("));
	t = EXTRACT_16BITS(&p[0]) & 0x7fff;
	ND_PRINT((ndo,"type=#%d ", t));
	if (p[0] & 0x80) {
		ND_PRINT((ndo,"value="));
		t = p[2];
		rawprint(ndo, (caddr_t)&p[2], 2);
	} else {
		ND_PRINT((ndo,"len=%d value=", EXTRACT_16BITS(&p[2])));
		rawprint(ndo, (caddr_t)&p[4], EXTRACT_16BITS(&p[2]));
	}
	ND_PRINT((ndo,")"));
	return p + totlen;
}

static const u_char *
ikev1_sa_print(netdissect_options *ndo, u_char tpay _U_,
	       const struct isakmp_gen *ext,
		u_int item_len _U_,
		const u_char *ep, u_int32_t phase, u_int32_t doi0 _U_,
		u_int32_t proto0, int depth)
{
	const struct ikev1_pl_sa *p;
	struct ikev1_pl_sa sa;
	u_int32_t doi, sit, ident;
	const u_char *cp, *np;
	int t;

	ND_PRINT((ndo,"%s:", NPSTR(ISAKMP_NPTYPE_SA)));

	p = (struct ikev1_pl_sa *)ext;
	ND_TCHECK(*p);
	UNALIGNED_MEMCPY(&sa, ext, sizeof(sa));
	doi = ntohl(sa.doi);
	sit = ntohl(sa.sit);
	if (doi != 1) {
		ND_PRINT((ndo," doi=%d", doi));
		ND_PRINT((ndo," situation=%u", (u_int32_t)ntohl(sa.sit)));
		return (u_char *)(p + 1);
	}

	ND_PRINT((ndo," doi=ipsec"));
	ND_PRINT((ndo," situation="));
	t = 0;
	if (sit & 0x01) {
		ND_PRINT((ndo,"identity"));
		t++;
	}
	if (sit & 0x02) {
		ND_PRINT((ndo,"%ssecrecy", t ? "+" : ""));
		t++;
	}
	if (sit & 0x04)
		ND_PRINT((ndo,"%sintegrity", t ? "+" : ""));

	np = (u_char *)ext + sizeof(sa);
	if (sit != 0x01) {
		ND_TCHECK2(*(ext + 1), sizeof(ident));
		UNALIGNED_MEMCPY(&ident, ext + 1, sizeof(ident));
		ND_PRINT((ndo," ident=%u", (u_int32_t)ntohl(ident)));
		np += sizeof(ident);
	}

	ext = (struct isakmp_gen *)np;
	ND_TCHECK(*ext);

	cp = ikev1_sub_print(ndo, ISAKMP_NPTYPE_P, ext, ep, phase, doi, proto0,
		depth);

	return cp;
trunc:
	ND_PRINT((ndo," [|%s]", NPSTR(ISAKMP_NPTYPE_SA)));
	return NULL;
}

static const u_char *
ikev1_p_print(netdissect_options *ndo, u_char tpay _U_,
	      const struct isakmp_gen *ext, u_int item_len _U_,
	       const u_char *ep, u_int32_t phase, u_int32_t doi0,
	       u_int32_t proto0 _U_, int depth)
{
	const struct ikev1_pl_p *p;
	struct ikev1_pl_p prop;
	const u_char *cp;

	ND_PRINT((ndo,"%s:", NPSTR(ISAKMP_NPTYPE_P)));

	p = (struct ikev1_pl_p *)ext;
	ND_TCHECK(*p);
	UNALIGNED_MEMCPY(&prop, ext, sizeof(prop));
	ND_PRINT((ndo," #%d protoid=%s transform=%d",
		  prop.p_no, PROTOIDSTR(prop.prot_id), prop.num_t));
	if (prop.spi_size) {
		ND_PRINT((ndo," spi="));
		if (!rawprint(ndo, (caddr_t)(p + 1), prop.spi_size))
			goto trunc;
	}

	ext = (struct isakmp_gen *)((u_char *)(p + 1) + prop.spi_size);
	ND_TCHECK(*ext);
	
	cp = ikev1_sub_print(ndo, ISAKMP_NPTYPE_T, ext, ep, phase, doi0,
			     prop.prot_id, depth);
	
	return cp;
trunc:
	ND_PRINT((ndo," [|%s]", NPSTR(ISAKMP_NPTYPE_P)));
	return NULL;
}

static const char *ikev1_p_map[] = {
	NULL, "ike",
};

static const char *ikev2_t_type_map[]={
	NULL, "encr", "prf", "integ", "dh", "esn"
};

static const char *ah_p_map[] = {
	NULL, "(reserved)", "md5", "sha", "1des",
	"sha2-256", "sha2-384", "sha2-512",
};

static const char *prf_p_map[] = {
	NULL, "hmac-md5", "hmac-sha", "hmac-tiger",
	"aes128_xcbc"
};

static const char *integ_p_map[] = {
	NULL, "hmac-md5", "hmac-sha", "dec-mac",
	"kpdk-md5", "aes-xcbc"
};

static const char *esn_p_map[] = {
	"no-esn", "esn"
};

static const char *dh_p_map[] = {
	NULL, "modp768",
	"modp1024",    /* group 2 */
	"EC2N 2^155",  /* group 3 */
	"EC2N 2^185",  /* group 4 */
	"modp1536",    /* group 5 */
	"iana-grp06", "iana-grp07", /* reserved */
	"iana-grp08", "iana-grp09",
	"iana-grp10", "iana-grp11",
	"iana-grp12", "iana-grp13",
	"modp2048",    /* group 14 */
	"modp3072",    /* group 15 */
	"modp4096",    /* group 16 */
	"modp6144",    /* group 17 */
	"modp8192",    /* group 18 */
};

static const char *esp_p_map[] = {
	NULL, "1des-iv64", "1des", "3des", "rc5", "idea", "cast",
	"blowfish", "3idea", "1des-iv32", "rc4", "null", "aes"
};

static const char *ipcomp_p_map[] = {
	NULL, "oui", "deflate", "lzs",
};

const struct attrmap ipsec_t_map[] = {
	{ NULL,	0, { NULL } },
	{ "lifetype", 3, { NULL, "sec", "kb", }, },
	{ "life", 0, { NULL } },
	{ "group desc", 18,	{ NULL, "modp768",
				  "modp1024",    /* group 2 */
				  "EC2N 2^155",  /* group 3 */
				  "EC2N 2^185",  /* group 4 */
				  "modp1536",    /* group 5 */
				  "iana-grp06", "iana-grp07", /* reserved */
				  "iana-grp08", "iana-grp09",
				  "iana-grp10", "iana-grp11",
				  "iana-grp12", "iana-grp13",
				  "modp2048",    /* group 14 */
				  "modp3072",    /* group 15 */
				  "modp4096",    /* group 16 */
				  "modp6144",    /* group 17 */
				  "modp8192",    /* group 18 */
		}, },
	{ "enc mode", 3, { NULL, "tunnel", "transport", }, },
	{ "auth", 5, { NULL, "hmac-md5", "hmac-sha1", "1des-mac", "keyed", }, },
	{ "keylen", 0, { NULL } },
	{ "rounds", 0, { NULL } },
	{ "dictsize", 0, { NULL } },
	{ "privalg", 0, { NULL } },
};

const struct attrmap encr_t_map[] = {
	{ NULL,	0, { NULL } }, 	{ NULL,	0, { NULL } },  /* 0, 1 */
	{ NULL,	0, { NULL } },	{ NULL,	0, { NULL } },  /* 2, 3 */
	{ NULL,	0, { NULL } },	{ NULL,	0, { NULL } },  /* 4, 5 */
	{ NULL,	0, { NULL } },	{ NULL,	0, { NULL } },  /* 6, 7 */
	{ NULL,	0, { NULL } },	{ NULL,	0, { NULL } },  /* 8, 9 */
	{ NULL,	0, { NULL } },	{ NULL,	0, { NULL } },  /* 10,11*/
	{ NULL,	0, { NULL } },	{ NULL,	0, { NULL } },  /* 12,13*/
	{ "keylen", 14, { NULL }},
};

const struct attrmap oakley_t_map[] = {
	{ NULL,	0, { NULL } },
	{ "enc", 8,	{ NULL, "1des", "idea", "blowfish", "rc5",
		 	  "3des", "cast", "aes", }, },
	{ "hash", 7,	{ NULL, "md5", "sha1", "tiger",
			  "sha2-256", "sha2-384", "sha2-512", }, },
	{ "auth", 6,	{ NULL, "preshared", "dss", "rsa sig", "rsa enc",
			  "rsa enc revised", }, },
	{ "group desc", 18,	{ NULL, "modp768",
				  "modp1024",    /* group 2 */
				  "EC2N 2^155",  /* group 3 */
				  "EC2N 2^185",  /* group 4 */
				  "modp1536",    /* group 5 */
				  "iana-grp06", "iana-grp07", /* reserved */
				  "iana-grp08", "iana-grp09",
				  "iana-grp10", "iana-grp11",
				  "iana-grp12", "iana-grp13",
				  "modp2048",    /* group 14 */
				  "modp3072",    /* group 15 */
				  "modp4096",    /* group 16 */
				  "modp6144",    /* group 17 */
				  "modp8192",    /* group 18 */
		}, },
	{ "group type", 4,	{ NULL, "MODP", "ECP", "EC2N", }, },
	{ "group prime", 0, { NULL } },
	{ "group gen1", 0, { NULL } },
	{ "group gen2", 0, { NULL } },
	{ "group curve A", 0, { NULL } },
	{ "group curve B", 0, { NULL } },
	{ "lifetype", 3,	{ NULL, "sec", "kb", }, },
	{ "lifeduration", 0, { NULL } },
	{ "prf", 0, { NULL } },
	{ "keylen", 0, { NULL } },
	{ "field", 0, { NULL } },
	{ "order", 0, { NULL } },
};

static const u_char *
ikev1_t_print(netdissect_options *ndo, u_char tpay _U_,
	      const struct isakmp_gen *ext, u_int item_len,
	      const u_char *ep, u_int32_t phase _U_, u_int32_t doi _U_,
	      u_int32_t proto, int depth _U_)
{
	const struct ikev1_pl_t *p;
	struct ikev1_pl_t t;
	const u_char *cp;
	const char *idstr;
	const struct attrmap *map;
	size_t nmap;
	const u_char *ep2;

	ND_PRINT((ndo,"%s:", NPSTR(ISAKMP_NPTYPE_T)));

	p = (struct ikev1_pl_t *)ext;
	ND_TCHECK(*p);
	UNALIGNED_MEMCPY(&t, ext, sizeof(t));

	switch (proto) {
	case 1:
		idstr = STR_OR_ID(t.t_id, ikev1_p_map);
		map = oakley_t_map;
		nmap = sizeof(oakley_t_map)/sizeof(oakley_t_map[0]);
		break;
	case 2:
		idstr = STR_OR_ID(t.t_id, ah_p_map);
		map = ipsec_t_map;
		nmap = sizeof(ipsec_t_map)/sizeof(ipsec_t_map[0]);
		break;
	case 3:
		idstr = STR_OR_ID(t.t_id, esp_p_map);
		map = ipsec_t_map;
		nmap = sizeof(ipsec_t_map)/sizeof(ipsec_t_map[0]);
		break;
	case 4:
		idstr = STR_OR_ID(t.t_id, ipcomp_p_map);
		map = ipsec_t_map;
		nmap = sizeof(ipsec_t_map)/sizeof(ipsec_t_map[0]);
		break;
	default:
		idstr = NULL;
		map = NULL;
		nmap = 0;
		break;
	}

	if (idstr)
		ND_PRINT((ndo," #%d id=%s ", t.t_no, idstr));
	else
		ND_PRINT((ndo," #%d id=%d ", t.t_no, t.t_id));
	cp = (u_char *)(p + 1);
	ep2 = (u_char *)p + item_len;
	while (cp < ep && cp < ep2) {
		if (map && nmap) {
			cp = ikev1_attrmap_print(ndo, cp, (ep < ep2) ? ep : ep2,
				map, nmap);
		} else
			cp = ikev1_attr_print(ndo, cp, (ep < ep2) ? ep : ep2);
	}
	if (ep < ep2)
		ND_PRINT((ndo,"..."));
	return cp;
trunc:
	ND_PRINT((ndo," [|%s]", NPSTR(ISAKMP_NPTYPE_T)));
	return NULL;
}

static const u_char *
ikev1_ke_print(netdissect_options *ndo, u_char tpay _U_,
	       const struct isakmp_gen *ext, u_int item_len _U_,
	       const u_char *ep _U_, u_int32_t phase _U_, u_int32_t doi _U_,
	       u_int32_t proto _U_, int depth _U_)
{
	struct isakmp_gen e;

	ND_PRINT((ndo,"%s:", NPSTR(ISAKMP_NPTYPE_KE)));

	ND_TCHECK(*ext);
	UNALIGNED_MEMCPY(&e, ext, sizeof(e));
	ND_PRINT((ndo," key len=%d", ntohs(e.len) - 4));
	if (2 < ndo->ndo_vflag && 4 < ntohs(e.len)) {
		ND_PRINT((ndo," "));
		if (!rawprint(ndo, (caddr_t)(ext + 1), ntohs(e.len) - 4))
			goto trunc;
	}
	return (u_char *)ext + ntohs(e.len);
trunc:
	ND_PRINT((ndo," [|%s]", NPSTR(ISAKMP_NPTYPE_KE)));
	return NULL;
}

static const u_char *
ikev1_id_print(netdissect_options *ndo, u_char tpay _U_,
	       const struct isakmp_gen *ext, u_int item_len _U_,
	       const u_char *ep _U_, u_int32_t phase, u_int32_t doi _U_,
	       u_int32_t proto _U_, int depth _U_)
{
#define USE_IPSECDOI_IN_PHASE1	1
	const struct ikev1_pl_id *p;
	struct ikev1_pl_id id;
	static const char *idtypestr[] = {
		"IPv4", "IPv4net", "IPv6", "IPv6net",
	};
	static const char *ipsecidtypestr[] = {
		NULL, "IPv4", "FQDN", "user FQDN", "IPv4net", "IPv6",
		"IPv6net", "IPv4range", "IPv6range", "ASN1 DN", "ASN1 GN",
		"keyid",
	};
	int len;
	const u_char *data;

	ND_PRINT((ndo,"%s:", NPSTR(ISAKMP_NPTYPE_ID)));

	p = (struct ikev1_pl_id *)ext;
	ND_TCHECK(*p);
	UNALIGNED_MEMCPY(&id, ext, sizeof(id));
	if (sizeof(*p) < item_len) {
		data = (u_char *)(p + 1);
		len = item_len - sizeof(*p);
	} else {
		data = NULL;
		len = 0;
	}

#if 0 /*debug*/
	ND_PRINT((ndo," [phase=%d doi=%d proto=%d]", phase, doi, proto));
#endif
	switch (phase) {
#ifndef USE_IPSECDOI_IN_PHASE1
	case 1:
#endif
	default:
		ND_PRINT((ndo," idtype=%s", STR_OR_ID(id.d.id_type, idtypestr)));
		ND_PRINT((ndo," doi_data=%u",
			  (u_int32_t)(ntohl(id.d.doi_data) & 0xffffff)));
		break;

#ifdef USE_IPSECDOI_IN_PHASE1
	case 1:
#endif
	case 2:
	    {
		const struct ipsecdoi_id *p;
		struct ipsecdoi_id id;
		struct protoent *pe;

		p = (struct ipsecdoi_id *)ext;
		ND_TCHECK(*p);
		UNALIGNED_MEMCPY(&id, ext, sizeof(id));
		ND_PRINT((ndo," idtype=%s", STR_OR_ID(id.type, ipsecidtypestr)));
		if (id.proto_id) {
#ifndef WIN32
			setprotoent(1);
#endif /* WIN32 */
			pe = getprotobynumber(id.proto_id);
			if (pe)
				ND_PRINT((ndo," protoid=%s", pe->p_name));
#ifndef WIN32
			endprotoent();
#endif /* WIN32 */
		} else {
			/* it DOES NOT mean IPPROTO_IP! */
			ND_PRINT((ndo," protoid=%s", "0"));
		}
		ND_PRINT((ndo," port=%d", ntohs(id.port)));
		if (!len)
			break;
		if (data == NULL)
			goto trunc;
		ND_TCHECK2(*data, len);
		switch (id.type) {
		case IPSECDOI_ID_IPV4_ADDR:
			if (len < 4)
				ND_PRINT((ndo," len=%d [bad: < 4]", len));
			else
				ND_PRINT((ndo," len=%d %s", len, ipaddr_string(data)));
			len = 0;
			break;
		case IPSECDOI_ID_FQDN:
		case IPSECDOI_ID_USER_FQDN:
		    {
			int i;
			ND_PRINT((ndo," len=%d ", len));
			for (i = 0; i < len; i++)
				safeputchar(data[i]);
			len = 0;
			break;
		    }
		case IPSECDOI_ID_IPV4_ADDR_SUBNET:
		    {
			const u_char *mask;
			if (len < 8)
				ND_PRINT((ndo," len=%d [bad: < 8]", len));
			else {
				mask = data + sizeof(struct in_addr);
				ND_PRINT((ndo," len=%d %s/%u.%u.%u.%u", len,
					  ipaddr_string(data),
					  mask[0], mask[1], mask[2], mask[3]));
			}
			len = 0;
			break;
		    }
#ifdef INET6
		case IPSECDOI_ID_IPV6_ADDR:
			if (len < 16)
				ND_PRINT((ndo," len=%d [bad: < 16]", len));
			else
				ND_PRINT((ndo," len=%d %s", len, ip6addr_string(data)));
			len = 0;
			break;
		case IPSECDOI_ID_IPV6_ADDR_SUBNET:
		    {
			const u_char *mask;
			if (len < 20)
				ND_PRINT((ndo," len=%d [bad: < 20]", len));
			else {
				mask = (u_char *)(data + sizeof(struct in6_addr));
				/*XXX*/
				ND_PRINT((ndo," len=%d %s/0x%02x%02x%02x%02x%02x%02x%02x%02x%02x%02x%02x%02x%02x%02x%02x%02x", len,
					  ip6addr_string(data),
					  mask[0], mask[1], mask[2], mask[3],
					  mask[4], mask[5], mask[6], mask[7],
					  mask[8], mask[9], mask[10], mask[11],
					  mask[12], mask[13], mask[14], mask[15]));
			}
			len = 0;
			break;
		    }
#endif /*INET6*/
		case IPSECDOI_ID_IPV4_ADDR_RANGE:
			if (len < 8)
				ND_PRINT((ndo," len=%d [bad: < 8]", len));
			else {
				ND_PRINT((ndo," len=%d %s-%s", len,
					  ipaddr_string(data),
					  ipaddr_string(data + sizeof(struct in_addr))));
			}
			len = 0;
			break;
#ifdef INET6
		case IPSECDOI_ID_IPV6_ADDR_RANGE:
			if (len < 32)
				ND_PRINT((ndo," len=%d [bad: < 32]", len));
			else {
				ND_PRINT((ndo," len=%d %s-%s", len,
					  ip6addr_string(data),
					  ip6addr_string(data + sizeof(struct in6_addr))));
			}
			len = 0;
			break;
#endif /*INET6*/
		case IPSECDOI_ID_DER_ASN1_DN:
		case IPSECDOI_ID_DER_ASN1_GN:
		case IPSECDOI_ID_KEY_ID:
			break;
		}
		break;
	    }
	}
	if (data && len) {
		ND_PRINT((ndo," len=%d", len));
		if (2 < ndo->ndo_vflag) {
			ND_PRINT((ndo," "));
			if (!rawprint(ndo, (caddr_t)data, len))
				goto trunc;
		}
	}
	return (u_char *)ext + item_len;
trunc:
	ND_PRINT((ndo," [|%s]", NPSTR(ISAKMP_NPTYPE_ID)));
	return NULL;
}

static const u_char *
ikev1_cert_print(netdissect_options *ndo, u_char tpay _U_,
		 const struct isakmp_gen *ext, u_int item_len _U_,
		 const u_char *ep _U_, u_int32_t phase _U_,
		 u_int32_t doi0 _U_,
		 u_int32_t proto0 _U_, int depth _U_)
{
	const struct ikev1_pl_cert *p;
	struct ikev1_pl_cert cert;
	static const char *certstr[] = {
		"none",	"pkcs7", "pgp", "dns",
		"x509sign", "x509ke", "kerberos", "crl",
		"arl", "spki", "x509attr",
	};

	ND_PRINT((ndo,"%s:", NPSTR(ISAKMP_NPTYPE_CERT)));

	p = (struct ikev1_pl_cert *)ext;
	ND_TCHECK(*p);
	UNALIGNED_MEMCPY(&cert, ext, sizeof(cert));
	ND_PRINT((ndo," len=%d", item_len - 4));
	ND_PRINT((ndo," type=%s", STR_OR_ID((cert.encode), certstr)));
	if (2 < ndo->ndo_vflag && 4 < item_len) {
		ND_PRINT((ndo," "));
		if (!rawprint(ndo, (caddr_t)(ext + 1), item_len - 4))
			goto trunc;
	}
	return (u_char *)ext + item_len;
trunc:
	ND_PRINT((ndo," [|%s]", NPSTR(ISAKMP_NPTYPE_CERT)));
	return NULL;
}

static const u_char *
ikev1_cr_print(netdissect_options *ndo, u_char tpay _U_,
	       const struct isakmp_gen *ext, u_int item_len _U_,
	       const u_char *ep _U_, u_int32_t phase _U_, u_int32_t doi0 _U_,
	       u_int32_t proto0 _U_, int depth _U_)
{
	const struct ikev1_pl_cert *p;
	struct ikev1_pl_cert cert;
	static const char *certstr[] = {
		"none",	"pkcs7", "pgp", "dns",
		"x509sign", "x509ke", "kerberos", "crl",
		"arl", "spki", "x509attr",
	};

	ND_PRINT((ndo,"%s:", NPSTR(ISAKMP_NPTYPE_CR)));

	p = (struct ikev1_pl_cert *)ext;
	ND_TCHECK(*p);
	UNALIGNED_MEMCPY(&cert, ext, sizeof(cert));
	ND_PRINT((ndo," len=%d", item_len - 4));
	ND_PRINT((ndo," type=%s", STR_OR_ID((cert.encode), certstr)));
	if (2 < ndo->ndo_vflag && 4 < item_len) {
		ND_PRINT((ndo," "));
		if (!rawprint(ndo, (caddr_t)(ext + 1), item_len - 4))
			goto trunc;
	}
	return (u_char *)ext + item_len;
trunc:
	ND_PRINT((ndo," [|%s]", NPSTR(ISAKMP_NPTYPE_CR)));
	return NULL;
}

static const u_char *
ikev1_hash_print(netdissect_options *ndo, u_char tpay _U_,
		 const struct isakmp_gen *ext, u_int item_len _U_,
		 const u_char *ep _U_, u_int32_t phase _U_, u_int32_t doi _U_,
		 u_int32_t proto _U_, int depth _U_)
{
	struct isakmp_gen e;

	ND_PRINT((ndo,"%s:", NPSTR(ISAKMP_NPTYPE_HASH)));

	ND_TCHECK(*ext);
	UNALIGNED_MEMCPY(&e, ext, sizeof(e));
	ND_PRINT((ndo," len=%d", ntohs(e.len) - 4));
	if (2 < ndo->ndo_vflag && 4 < ntohs(e.len)) {
		ND_PRINT((ndo," "));
		if (!rawprint(ndo, (caddr_t)(ext + 1), ntohs(e.len) - 4))
			goto trunc;
	}
	return (u_char *)ext + ntohs(e.len);
trunc:
	ND_PRINT((ndo," [|%s]", NPSTR(ISAKMP_NPTYPE_HASH)));
	return NULL;
}

static const u_char *
ikev1_sig_print(netdissect_options *ndo, u_char tpay _U_,
		const struct isakmp_gen *ext, u_int item_len _U_,
		const u_char *ep _U_, u_int32_t phase _U_, u_int32_t doi _U_,
		u_int32_t proto _U_, int depth _U_)
{
	struct isakmp_gen e;

	ND_PRINT((ndo,"%s:", NPSTR(ISAKMP_NPTYPE_SIG)));

	ND_TCHECK(*ext);
	UNALIGNED_MEMCPY(&e, ext, sizeof(e));
	ND_PRINT((ndo," len=%d", ntohs(e.len) - 4));
	if (2 < ndo->ndo_vflag && 4 < ntohs(e.len)) {
		ND_PRINT((ndo," "));
		if (!rawprint(ndo, (caddr_t)(ext + 1), ntohs(e.len) - 4))
			goto trunc;
	}
	return (u_char *)ext + ntohs(e.len);
trunc:
	ND_PRINT((ndo," [|%s]", NPSTR(ISAKMP_NPTYPE_SIG)));
	return NULL;
}

static const u_char *
ikev1_nonce_print(netdissect_options *ndo, u_char tpay _U_,
		  const struct isakmp_gen *ext,
		  u_int item_len _U_,
		  const u_char *ep _U_,
		  u_int32_t phase _U_, u_int32_t doi _U_,
		  u_int32_t proto _U_, int depth _U_)
{
	struct isakmp_gen e;

	ND_PRINT((ndo,"%s:", NPSTR(ISAKMP_NPTYPE_NONCE)));

	ND_TCHECK(*ext);
	UNALIGNED_MEMCPY(&e, ext, sizeof(e));
	ND_PRINT((ndo," n len=%d", ntohs(e.len) - 4));
	if (2 < ndo->ndo_vflag && 4 < ntohs(e.len)) {
		ND_PRINT((ndo," "));
		if (!rawprint(ndo, (caddr_t)(ext + 1), ntohs(e.len) - 4))
			goto trunc;
	} else if (1 < ndo->ndo_vflag && 4 < ntohs(e.len)) {
		ND_PRINT((ndo," "));
		if (!ike_show_somedata(ndo, (u_char *)(caddr_t)(ext + 1), ep))
			goto trunc;
	}
	return (u_char *)ext + ntohs(e.len);
trunc:
	ND_PRINT((ndo," [|%s]", NPSTR(ISAKMP_NPTYPE_NONCE)));
	return NULL;
}

static const u_char *
ikev1_n_print(netdissect_options *ndo, u_char tpay _U_,
	      const struct isakmp_gen *ext, u_int item_len,
	      const u_char *ep, u_int32_t phase, u_int32_t doi0 _U_,
	      u_int32_t proto0 _U_, int depth)
{
	struct ikev1_pl_n *p, n;
	const u_char *cp;
	u_char *ep2;
	u_int32_t doi;
	u_int32_t proto;
	static const char *notify_error_str[] = {
		NULL,				"INVALID-PAYLOAD-TYPE",
		"DOI-NOT-SUPPORTED",		"SITUATION-NOT-SUPPORTED",
		"INVALID-COOKIE",		"INVALID-MAJOR-VERSION",
		"INVALID-MINOR-VERSION",	"INVALID-EXCHANGE-TYPE",
		"INVALID-FLAGS",		"INVALID-MESSAGE-ID",
		"INVALID-PROTOCOL-ID",		"INVALID-SPI",
		"INVALID-TRANSFORM-ID",		"ATTRIBUTES-NOT-SUPPORTED",
		"NO-PROPOSAL-CHOSEN",		"BAD-PROPOSAL-SYNTAX",
		"PAYLOAD-MALFORMED",		"INVALID-KEY-INFORMATION",
		"INVALID-ID-INFORMATION",	"INVALID-CERT-ENCODING",
		"INVALID-CERTIFICATE",		"CERT-TYPE-UNSUPPORTED",
		"INVALID-CERT-AUTHORITY",	"INVALID-HASH-INFORMATION",
		"AUTHENTICATION-FAILED",	"INVALID-SIGNATURE",
		"ADDRESS-NOTIFICATION",		"NOTIFY-SA-LIFETIME",
		"CERTIFICATE-UNAVAILABLE",	"UNSUPPORTED-EXCHANGE-TYPE",
		"UNEQUAL-PAYLOAD-LENGTHS",
	};
	static const char *ipsec_notify_error_str[] = {
		"RESERVED",
	};
	static const char *notify_status_str[] = {
		"CONNECTED",
	};
	static const char *ipsec_notify_status_str[] = {
		"RESPONDER-LIFETIME",		"REPLAY-STATUS",
		"INITIAL-CONTACT",
	};
/* NOTE: these macro must be called with x in proper range */

/* 0 - 8191 */
#define NOTIFY_ERROR_STR(x) \
	STR_OR_ID((x), notify_error_str)

/* 8192 - 16383 */
#define IPSEC_NOTIFY_ERROR_STR(x) \
	STR_OR_ID((u_int)((x) - 8192), ipsec_notify_error_str)

/* 16384 - 24575 */
#define NOTIFY_STATUS_STR(x) \
	STR_OR_ID((u_int)((x) - 16384), notify_status_str)

/* 24576 - 32767 */
#define IPSEC_NOTIFY_STATUS_STR(x) \
	STR_OR_ID((u_int)((x) - 24576), ipsec_notify_status_str)

	ND_PRINT((ndo,"%s:", NPSTR(ISAKMP_NPTYPE_N)));

	p = (struct ikev1_pl_n *)ext;
	ND_TCHECK(*p);
	UNALIGNED_MEMCPY(&n, ext, sizeof(n));
	doi = ntohl(n.doi);
	proto = n.prot_id;
	if (doi != 1) {
		ND_PRINT((ndo," doi=%d", doi));
		ND_PRINT((ndo," proto=%d", proto));
		if (ntohs(n.type) < 8192)
			ND_PRINT((ndo," type=%s", NOTIFY_ERROR_STR(ntohs(n.type))));
		else if (ntohs(n.type) < 16384)
			ND_PRINT((ndo," type=%s", numstr(ntohs(n.type))));
		else if (ntohs(n.type) < 24576)
			ND_PRINT((ndo," type=%s", NOTIFY_STATUS_STR(ntohs(n.type))));
		else
			ND_PRINT((ndo," type=%s", numstr(ntohs(n.type))));
		if (n.spi_size) {
			ND_PRINT((ndo," spi="));
			if (!rawprint(ndo, (caddr_t)(p + 1), n.spi_size))
				goto trunc;
		}
		return (u_char *)(p + 1) + n.spi_size;
	}

	ND_PRINT((ndo," doi=ipsec"));
	ND_PRINT((ndo," proto=%s", PROTOIDSTR(proto)));
	if (ntohs(n.type) < 8192)
		ND_PRINT((ndo," type=%s", NOTIFY_ERROR_STR(ntohs(n.type))));
	else if (ntohs(n.type) < 16384)
		ND_PRINT((ndo," type=%s", IPSEC_NOTIFY_ERROR_STR(ntohs(n.type))));
	else if (ntohs(n.type) < 24576)
		ND_PRINT((ndo," type=%s", NOTIFY_STATUS_STR(ntohs(n.type))));
	else if (ntohs(n.type) < 32768)
		ND_PRINT((ndo," type=%s", IPSEC_NOTIFY_STATUS_STR(ntohs(n.type))));
	else
		ND_PRINT((ndo," type=%s", numstr(ntohs(n.type))));
	if (n.spi_size) {
		ND_PRINT((ndo," spi="));
		if (!rawprint(ndo, (caddr_t)(p + 1), n.spi_size))
			goto trunc;
	}

	cp = (u_char *)(p + 1) + n.spi_size;
	ep2 = (u_char *)p + item_len;

	if (cp < ep) {
		ND_PRINT((ndo," orig=("));
		switch (ntohs(n.type)) {
		case IPSECDOI_NTYPE_RESPONDER_LIFETIME:
		    {
			const struct attrmap *map = oakley_t_map;
			size_t nmap = sizeof(oakley_t_map)/sizeof(oakley_t_map[0]);
			while (cp < ep && cp < ep2) {
				cp = ikev1_attrmap_print(ndo, cp,
					(ep < ep2) ? ep : ep2, map, nmap);
			}
			break;
		    }
		case IPSECDOI_NTYPE_REPLAY_STATUS:
			ND_PRINT((ndo,"replay detection %sabled",
				  EXTRACT_32BITS(cp) ? "en" : "dis"));
			break;
		case ISAKMP_NTYPE_NO_PROPOSAL_CHOSEN:
			if (ikev1_sub_print(ndo, ISAKMP_NPTYPE_SA,
					    (struct isakmp_gen *)cp, ep, phase, doi, proto,
					    depth) == NULL)
				return NULL;
			break;
		default:
			/* NULL is dummy */
			isakmp_print(ndo, cp,
				     item_len - sizeof(*p) - n.spi_size,
				     NULL);
		}
		ND_PRINT((ndo,")"));
	}
	return (u_char *)ext + item_len;
trunc:
	ND_PRINT((ndo," [|%s]", NPSTR(ISAKMP_NPTYPE_N)));
	return NULL;
}

static const u_char *
ikev1_d_print(netdissect_options *ndo, u_char tpay _U_,
	      const struct isakmp_gen *ext, u_int item_len _U_,
	      const u_char *ep _U_, u_int32_t phase _U_, u_int32_t doi0 _U_,
	      u_int32_t proto0 _U_, int depth _U_)
{
	const struct ikev1_pl_d *p;
	struct ikev1_pl_d d;
	const u_int8_t *q;
	u_int32_t doi;
	u_int32_t proto;
	int i;

	ND_PRINT((ndo,"%s:", NPSTR(ISAKMP_NPTYPE_D)));

	p = (struct ikev1_pl_d *)ext;
	ND_TCHECK(*p);
	UNALIGNED_MEMCPY(&d, ext, sizeof(d));
	doi = ntohl(d.doi);
	proto = d.prot_id;
	if (doi != 1) {
		ND_PRINT((ndo," doi=%u", doi));
		ND_PRINT((ndo," proto=%u", proto));
	} else {
		ND_PRINT((ndo," doi=ipsec"));
		ND_PRINT((ndo," proto=%s", PROTOIDSTR(proto)));
	}
	ND_PRINT((ndo," spilen=%u", d.spi_size));
	ND_PRINT((ndo," nspi=%u", ntohs(d.num_spi)));
	ND_PRINT((ndo," spi="));
	q = (u_int8_t *)(p + 1);
	for (i = 0; i < ntohs(d.num_spi); i++) {
		if (i != 0)
			ND_PRINT((ndo,","));
		if (!rawprint(ndo, (caddr_t)q, d.spi_size))
			goto trunc;
		q += d.spi_size;
	}
	return q;
trunc:
	ND_PRINT((ndo," [|%s]", NPSTR(ISAKMP_NPTYPE_D)));
	return NULL;
}

static const u_char *
ikev1_vid_print(netdissect_options *ndo, u_char tpay _U_,
		const struct isakmp_gen *ext,
		u_int item_len _U_, const u_char *ep _U_,
		u_int32_t phase _U_, u_int32_t doi _U_,
		u_int32_t proto _U_, int depth _U_)
{
	struct isakmp_gen e;

	ND_PRINT((ndo,"%s:", NPSTR(ISAKMP_NPTYPE_VID)));

	ND_TCHECK(*ext);
	UNALIGNED_MEMCPY(&e, ext, sizeof(e));
	ND_PRINT((ndo," len=%d", ntohs(e.len) - 4));
	if (2 < ndo->ndo_vflag && 4 < ntohs(e.len)) {
		ND_PRINT((ndo," "));
		if (!rawprint(ndo, (caddr_t)(ext + 1), ntohs(e.len) - 4))
			goto trunc;
	}
	return (u_char *)ext + ntohs(e.len);
trunc:
	ND_PRINT((ndo," [|%s]", NPSTR(ISAKMP_NPTYPE_VID)));
	return NULL;
}

/************************************************************/
/*                                                          */
/*              IKE v2 - rfc4306 - dissector                */
/*                                                          */
/************************************************************/

static void
ikev2_pay_print(netdissect_options *ndo, const char *payname, int critical)
{
	ND_PRINT((ndo,"%s%s:", payname, critical&0x80 ? "[C]" : ""));
}

static const u_char *
ikev2_gen_print(netdissect_options *ndo, u_char tpay,
		const struct isakmp_gen *ext)
{
	struct isakmp_gen e;

	ND_TCHECK(*ext);
	UNALIGNED_MEMCPY(&e, ext, sizeof(e));
	ikev2_pay_print(ndo, NPSTR(tpay), e.critical);

	ND_PRINT((ndo," len=%d", ntohs(e.len) - 4));
	if (2 < ndo->ndo_vflag && 4 < ntohs(e.len)) {
		ND_PRINT((ndo," "));
		if (!rawprint(ndo, (caddr_t)(ext + 1), ntohs(e.len) - 4))
			goto trunc;
	}
	return (u_char *)ext + ntohs(e.len);
trunc:
	ND_PRINT((ndo," [|%s]", NPSTR(tpay)));
	return NULL;
}

static const u_char *
ikev2_t_print(netdissect_options *ndo, u_char tpay _U_, int pcount,
	      const struct isakmp_gen *ext, u_int item_len,
	      const u_char *ep, u_int32_t phase _U_, u_int32_t doi _U_,
	      u_int32_t proto _U_, int depth _U_)
{
	const struct ikev2_t *p;
	struct ikev2_t t;
	u_int16_t  t_id;
	const u_char *cp;
	const char *idstr;
	const struct attrmap *map;
	size_t nmap;
	const u_char *ep2;

	p = (struct ikev2_t *)ext;
	ND_TCHECK(*p);
	UNALIGNED_MEMCPY(&t, ext, sizeof(t));
	ikev2_pay_print(ndo, NPSTR(ISAKMP_NPTYPE_T), t.h.critical);

	t_id = ntohs(t.t_id);
	
	map = NULL;
	nmap = 0;

	switch (t.t_type) {
	case IV2_T_ENCR:
		idstr = STR_OR_ID(t_id, esp_p_map);
		map = encr_t_map;
		nmap = sizeof(encr_t_map)/sizeof(encr_t_map[0]);
		break;

	case IV2_T_PRF:
		idstr = STR_OR_ID(t_id, prf_p_map);
		break;

	case IV2_T_INTEG:
		idstr = STR_OR_ID(t_id, integ_p_map);
		break;

	case IV2_T_DH:
		idstr = STR_OR_ID(t_id, dh_p_map);
		break;

	case IV2_T_ESN:
		idstr = STR_OR_ID(t_id, esn_p_map);
		break;

	default:
		idstr = NULL;
		break;
	}

	if (idstr)
		ND_PRINT((ndo," #%u type=%s id=%s ", pcount,
			  STR_OR_ID(t.t_type, ikev2_t_type_map),
			  idstr));
	else
		ND_PRINT((ndo," #%u type=%s id=%u ", pcount,
			  STR_OR_ID(t.t_type, ikev2_t_type_map),
			  t.t_id));
	cp = (u_char *)(p + 1);
	ep2 = (u_char *)p + item_len;
	while (cp < ep && cp < ep2) {
		if (map && nmap) {
			cp = ikev1_attrmap_print(ndo, cp, (ep < ep2) ? ep : ep2,
				map, nmap);
		} else
			cp = ikev1_attr_print(ndo, cp, (ep < ep2) ? ep : ep2);
	}
	if (ep < ep2)
		ND_PRINT((ndo,"..."));
	return cp;
trunc:
	ND_PRINT((ndo," [|%s]", NPSTR(ISAKMP_NPTYPE_T)));
	return NULL;
}

static const u_char *
ikev2_p_print(netdissect_options *ndo, u_char tpay _U_, int pcount _U_,
	      const struct isakmp_gen *ext, u_int item_len _U_,
	       const u_char *ep, u_int32_t phase, u_int32_t doi0,
	       u_int32_t proto0 _U_, int depth)
{
	const struct ikev2_p *p;
	struct ikev2_p prop;
	const u_char *cp;

	p = (struct ikev2_p *)ext;
	ND_TCHECK(*p);
	UNALIGNED_MEMCPY(&prop, ext, sizeof(prop));
	ikev2_pay_print(ndo, NPSTR(ISAKMP_NPTYPE_P), prop.h.critical);

	ND_PRINT((ndo," #%u protoid=%s transform=%d len=%u",
		  prop.p_no,  PROTOIDSTR(prop.prot_id),
		  prop.num_t, ntohs(prop.h.len)));
	if (prop.spi_size) {
		ND_PRINT((ndo," spi="));
		if (!rawprint(ndo, (caddr_t)(p + 1), prop.spi_size))
			goto trunc;
	}

	ext = (struct isakmp_gen *)((u_char *)(p + 1) + prop.spi_size);
	ND_TCHECK(*ext);

	cp = ikev2_sub_print(ndo, NULL, ISAKMP_NPTYPE_T, ext, ep, phase, doi0,
			     prop.prot_id, depth);
	
	return cp;
trunc:
	ND_PRINT((ndo," [|%s]", NPSTR(ISAKMP_NPTYPE_P)));
	return NULL;
}

static const u_char *
ikev2_sa_print(netdissect_options *ndo, u_char tpay, 
		const struct isakmp_gen *ext1,
		u_int item_len _U_, const u_char *ep _U_,
		u_int32_t phase _U_, u_int32_t doi _U_,
		u_int32_t proto _U_, int depth _U_)
{
	struct isakmp_gen e;
	int    osa_length, sa_length;

	ND_TCHECK(*ext1);
	UNALIGNED_MEMCPY(&e, ext1, sizeof(e));
	ikev2_pay_print(ndo, "sa", e.critical);

	osa_length= ntohs(e.len);
	sa_length = osa_length - 4;
	ND_PRINT((ndo," len=%d", sa_length));

	ikev2_sub_print(ndo, NULL, ISAKMP_NPTYPE_P,
			ext1+1, ep,
			0, 0, 0, depth);

	return (u_char *)ext1 + osa_length;
trunc:
	ND_PRINT((ndo," [|%s]", NPSTR(tpay)));
	return NULL;
}

static const u_char *
ikev2_ke_print(netdissect_options *ndo, u_char tpay, 
		const struct isakmp_gen *ext,
		u_int item_len _U_, const u_char *ep _U_,
		u_int32_t phase _U_, u_int32_t doi _U_,
		u_int32_t proto _U_, int depth _U_)
{
	struct ikev2_ke ke;
	struct ikev2_ke *k;

	k = (struct ikev2_ke *)ext;
	ND_TCHECK(*ext);
	UNALIGNED_MEMCPY(&ke, ext, sizeof(ke));
	ikev2_pay_print(ndo, NPSTR(tpay), ke.h.critical);

	ND_PRINT((ndo," len=%u group=%s", ntohs(ke.h.len) - 8,
		  STR_OR_ID(ntohs(ke.ke_group), dh_p_map)));
		 
	if (2 < ndo->ndo_vflag && 8 < ntohs(ke.h.len)) {
		ND_PRINT((ndo," "));
		if (!rawprint(ndo, (caddr_t)(k + 1), ntohs(ke.h.len) - 8))
			goto trunc;
	}
	return (u_char *)ext + ntohs(ke.h.len);
trunc:
	ND_PRINT((ndo," [|%s]", NPSTR(tpay)));
	return NULL;
}

static const u_char *
ikev2_ID_print(netdissect_options *ndo, u_char tpay, 
		const struct isakmp_gen *ext,
		u_int item_len _U_, const u_char *ep _U_,
		u_int32_t phase _U_, u_int32_t doi _U_,
		u_int32_t proto _U_, int depth _U_)
{
	struct ikev2_id id;
	int id_len, idtype_len, i;
	unsigned int dumpascii, dumphex;
	unsigned char *typedata;

	ND_TCHECK(*ext);
	UNALIGNED_MEMCPY(&id, ext, sizeof(id));
	ikev2_pay_print(ndo, NPSTR(tpay), id.h.critical);

	id_len = ntohs(id.h.len);

	ND_PRINT((ndo," len=%d", id_len - 4));
	if (2 < ndo->ndo_vflag && 4 < id_len) {
		ND_PRINT((ndo," "));
		if (!rawprint(ndo, (caddr_t)(ext + 1), id_len - 4))
			goto trunc;
	}

	idtype_len =id_len - sizeof(struct ikev2_id);
	dumpascii = 0;
	dumphex   = 0;
	typedata  = (unsigned char *)(ext)+sizeof(struct ikev2_id);

	switch(id.type) {
	case ID_IPV4_ADDR:
		ND_PRINT((ndo, " ipv4:"));
		dumphex=1;
		break;
	case ID_FQDN:
		ND_PRINT((ndo, " fqdn:"));
		dumpascii=1;
		break;
	case ID_RFC822_ADDR:
		ND_PRINT((ndo, " rfc822:"));
		dumpascii=1;
		break;
	case ID_IPV6_ADDR:
		ND_PRINT((ndo, " ipv6:"));
		dumphex=1;
		break;
	case ID_DER_ASN1_DN:
		ND_PRINT((ndo, " dn:"));
		dumphex=1;
		break;
	case ID_DER_ASN1_GN:
		ND_PRINT((ndo, " gn:"));
		dumphex=1;
		break;
	case ID_KEY_ID:
		ND_PRINT((ndo, " keyid:"));
		dumphex=1;
		break;
	}

	if(dumpascii) {
		ND_TCHECK2(*typedata, idtype_len);
		for(i=0; i<idtype_len; i++) {
			if(ND_ISPRINT(typedata[i])) {
				ND_PRINT((ndo, "%c", typedata[i]));
			} else {
				ND_PRINT((ndo, "."));
			}
		}
	}
	if(dumphex) {
		if (!rawprint(ndo, (caddr_t)typedata, idtype_len))
			goto trunc;
	}

	return (u_char *)ext + id_len;
trunc:
	ND_PRINT((ndo," [|%s]", NPSTR(tpay)));
	return NULL;
}

static const u_char *
ikev2_cert_print(netdissect_options *ndo, u_char tpay, 
		const struct isakmp_gen *ext,
		u_int item_len _U_, const u_char *ep _U_,
		u_int32_t phase _U_, u_int32_t doi _U_,
		u_int32_t proto _U_, int depth _U_)
{
	return ikev2_gen_print(ndo, tpay, ext);
}

static const u_char *
ikev2_cr_print(netdissect_options *ndo, u_char tpay, 
		const struct isakmp_gen *ext,
		u_int item_len _U_, const u_char *ep _U_,
		u_int32_t phase _U_, u_int32_t doi _U_,
		u_int32_t proto _U_, int depth _U_)
{
	return ikev2_gen_print(ndo, tpay, ext);
}

static const u_char *
ikev2_auth_print(netdissect_options *ndo, u_char tpay, 
		const struct isakmp_gen *ext,
		u_int item_len _U_, const u_char *ep _U_,
		u_int32_t phase _U_, u_int32_t doi _U_,
		u_int32_t proto _U_, int depth _U_)
{
	struct ikev2_auth a;
	const char *v2_auth[]={ "invalid", "rsasig",
				"shared-secret", "dsssig" };
	u_char *authdata = (u_char*)ext + sizeof(a);
	unsigned int len;

	ND_TCHECK(*ext);
	UNALIGNED_MEMCPY(&a, ext, sizeof(a));
	ikev2_pay_print(ndo, NPSTR(tpay), a.h.critical);
	len = ntohs(a.h.len);

	ND_PRINT((ndo," len=%d method=%s", len-4, 
		  STR_OR_ID(a.auth_method, v2_auth)));

	if (1 < ndo->ndo_vflag && 4 < len) {
		ND_PRINT((ndo," authdata=("));
		if (!rawprint(ndo, (caddr_t)authdata, len - sizeof(a)))
			goto trunc;
		ND_PRINT((ndo,") "));
	} else if(ndo->ndo_vflag && 4 < len) {
		if(!ike_show_somedata(ndo, authdata, ep)) goto trunc;
	}

	return (u_char *)ext + len;
trunc:
	ND_PRINT((ndo," [|%s]", NPSTR(tpay)));
	return NULL;
}

static const u_char *
ikev2_nonce_print(netdissect_options *ndo, u_char tpay, 
		const struct isakmp_gen *ext,
		u_int item_len _U_, const u_char *ep _U_,
		u_int32_t phase _U_, u_int32_t doi _U_,
		u_int32_t proto _U_, int depth _U_)
{
	struct isakmp_gen e;

	ND_TCHECK(*ext);
	UNALIGNED_MEMCPY(&e, ext, sizeof(e));
	ikev2_pay_print(ndo, "nonce", e.critical);

	ND_PRINT((ndo," len=%d", ntohs(e.len) - 4));
	if (1 < ndo->ndo_vflag && 4 < ntohs(e.len)) {
		ND_PRINT((ndo," nonce=("));
		if (!rawprint(ndo, (caddr_t)(ext + 1), ntohs(e.len) - 4))
			goto trunc;
		ND_PRINT((ndo,") "));
	} else if(ndo->ndo_vflag && 4 < ntohs(e.len)) {
		if(!ike_show_somedata(ndo, (const u_char *)(ext+1), ep)) goto trunc;
	}

	return (u_char *)ext + ntohs(e.len);
trunc:
	ND_PRINT((ndo," [|%s]", NPSTR(tpay)));
	return NULL;
}

/* notify payloads */
static const u_char *
ikev2_n_print(netdissect_options *ndo, u_char tpay _U_, 
		const struct isakmp_gen *ext,
		u_int item_len _U_, const u_char *ep _U_,
		u_int32_t phase _U_, u_int32_t doi _U_,
		u_int32_t proto _U_, int depth _U_)
{
	struct ikev2_n *p, n;
	const u_char *cp;
	u_char showspi, showdata, showsomedata;
	const char *notify_name;
	u_int32_t type;

	p = (struct ikev2_n *)ext;
	ND_TCHECK(*p);
	UNALIGNED_MEMCPY(&n, ext, sizeof(n));
	ikev2_pay_print(ndo, NPSTR(ISAKMP_NPTYPE_N), n.h.critical);

	showspi = 1;
	showdata = 0;
	showsomedata=0;
	notify_name=NULL;

	ND_PRINT((ndo," prot_id=%s", PROTOIDSTR(n.prot_id)));

	type = ntohs(n.type);

	/* notify space is annoying sparse */
	switch(type) {
	case IV2_NOTIFY_UNSUPPORTED_CRITICAL_PAYLOAD:
		notify_name = "unsupported_critical_payload";
		showspi = 0;
		break;

	case IV2_NOTIFY_INVALID_IKE_SPI:
		notify_name = "invalid_ike_spi";
		showspi = 1;
		break;

	case IV2_NOTIFY_INVALID_MAJOR_VERSION:
		notify_name = "invalid_major_version";
		showspi = 0;
		break;

	case IV2_NOTIFY_INVALID_SYNTAX:
		notify_name = "invalid_syntax";
		showspi = 1;
		break;

	case IV2_NOTIFY_INVALID_MESSAGE_ID:
		notify_name = "invalid_message_id";
		showspi = 1;
		break;

	case IV2_NOTIFY_INVALID_SPI:
		notify_name = "invalid_spi";
		showspi = 1;
		break;

	case IV2_NOTIFY_NO_PROPOSAL_CHOSEN:
		notify_name = "no_protocol_chosen";
		showspi = 1;
		break;

	case IV2_NOTIFY_INVALID_KE_PAYLOAD:
		notify_name = "invalid_ke_payload";
		showspi = 1;
		break;

	case IV2_NOTIFY_AUTHENTICATION_FAILED:
		notify_name = "authentication_failed";
		showspi = 1;
		break;

	case IV2_NOTIFY_SINGLE_PAIR_REQUIRED:
		notify_name = "single_pair_required";
		showspi = 1;
		break;

	case IV2_NOTIFY_NO_ADDITIONAL_SAS:
		notify_name = "no_additional_sas";
		showspi = 0;
		break;

	case IV2_NOTIFY_INTERNAL_ADDRESS_FAILURE:
		notify_name = "internal_address_failure";
		showspi = 0;
		break;

	case IV2_NOTIFY_FAILED_CP_REQUIRED: 
		notify_name = "failed:cp_required";
		showspi = 0;
		break;

	case IV2_NOTIFY_INVALID_SELECTORS:
		notify_name = "invalid_selectors";
		showspi = 0;
		break;

	case IV2_NOTIFY_INITIAL_CONTACT:
		notify_name = "initial_contact";
		showspi = 0;
		break;

	case IV2_NOTIFY_SET_WINDOW_SIZE:   
		notify_name = "set_window_size";
		showspi = 0;
		break;

	case IV2_NOTIFY_ADDITIONAL_TS_POSSIBLE:
		notify_name = "additional_ts_possible";
		showspi = 0;
		break;

	case IV2_NOTIFY_IPCOMP_SUPPORTED: 
		notify_name = "ipcomp_supported";
		showspi = 0;
		break;

	case IV2_NOTIFY_NAT_DETECTION_SOURCE_IP:
		notify_name = "nat_detection_source_ip";
		showspi = 1;
		break;

	case IV2_NOTIFY_NAT_DETECTION_DESTINATION_IP:
		notify_name = "nat_detection_destination_ip";
		showspi = 1;
		break;

	case IV2_NOTIFY_COOKIE:
		notify_name = "cookie";
		showspi = 1;
		showsomedata= 1;
		showdata= 0;
		break;

	case IV2_NOTIFY_USE_TRANSPORT_MODE:
		notify_name = "use_transport_mode";
		showspi = 0;
		break;

	case IV2_NOTIFY_HTTP_CERT_LOOKUP_SUPPORTED:
		notify_name = "http_cert_lookup_supported";
		showspi = 0;
		break;

	case IV2_NOTIFY_REKEY_SA:
		notify_name = "rekey_sa";
		showspi = 1;
		break;

	case IV2_NOTIFY_ESP_TFC_PADDING_NOT_SUPPORTED:
		notify_name = "tfc_padding_not_supported";
		showspi = 0;
		break;

	case IV2_NOTIFY_NON_FIRST_FRAGMENTS_ALSO:
		notify_name = "non_first_fragment_also";
		showspi = 0;
		break;

	default:
		if (type < 8192) {
			notify_name="error";
		} else if(type < 16384) {
			notify_name="private-error";
		} else if(type < 40960) {
			notify_name="status";
		} else {
			notify_name="private-status";
		}
	}

	if(notify_name) {
		ND_PRINT((ndo," type=%u(%s)", type, notify_name));
	}
		

	if (showspi && n.spi_size) {
		ND_PRINT((ndo," spi="));
		if (!rawprint(ndo, (caddr_t)(p + 1), n.spi_size))
			goto trunc;
	}

	cp = (u_char *)(p + 1) + n.spi_size;

	if(3 < ndo->ndo_vflag) {
		showdata = 1;
	}

	if ((showdata || (showsomedata && ep-cp < 30)) && cp < ep) {
		ND_PRINT((ndo," data=("));
		if (!rawprint(ndo, (caddr_t)(cp), ep - cp))
			goto trunc;

		ND_PRINT((ndo,")"));

	} else if(showsomedata && cp < ep) {
		if(!ike_show_somedata(ndo, cp, ep)) goto trunc;
	}
		
	return (u_char *)ext + item_len;
trunc:
	ND_PRINT((ndo," [|%s]", NPSTR(ISAKMP_NPTYPE_N)));
	return NULL;
}

static const u_char *
ikev2_d_print(netdissect_options *ndo, u_char tpay, 
		const struct isakmp_gen *ext,
		u_int item_len _U_, const u_char *ep _U_,
		u_int32_t phase _U_, u_int32_t doi _U_,
		u_int32_t proto _U_, int depth _U_)
{
	return ikev2_gen_print(ndo, tpay, ext);
}

static const u_char *
ikev2_vid_print(netdissect_options *ndo, u_char tpay, 
		const struct isakmp_gen *ext,
		u_int item_len _U_, const u_char *ep _U_,
		u_int32_t phase _U_, u_int32_t doi _U_,
		u_int32_t proto _U_, int depth _U_)
{
	struct isakmp_gen e;
	const u_char *vid;
	int i, len;

	ND_TCHECK(*ext);
	UNALIGNED_MEMCPY(&e, ext, sizeof(e));
	ikev2_pay_print(ndo, NPSTR(tpay), e.critical);
	ND_PRINT((ndo," len=%d vid=", ntohs(e.len) - 4));

	vid = (const u_char *)(ext+1);
	len = ntohs(e.len) - 4;
	ND_TCHECK2(*vid, len);
	for(i=0; i<len; i++) {
		if(ND_ISPRINT(vid[i])) ND_PRINT((ndo, "%c", vid[i]));
		else ND_PRINT((ndo, "."));
	}
	if (2 < ndo->ndo_vflag && 4 < len) {
		ND_PRINT((ndo," "));
		if (!rawprint(ndo, (caddr_t)(ext + 1), ntohs(e.len) - 4))
			goto trunc;
	}
	return (u_char *)ext + ntohs(e.len);
trunc:
	ND_PRINT((ndo," [|%s]", NPSTR(tpay)));
	return NULL;
}

static const u_char *
ikev2_TS_print(netdissect_options *ndo, u_char tpay, 
		const struct isakmp_gen *ext,
		u_int item_len _U_, const u_char *ep _U_,
		u_int32_t phase _U_, u_int32_t doi _U_,
		u_int32_t proto _U_, int depth _U_)
{
	return ikev2_gen_print(ndo, tpay, ext);
}

static const u_char *
ikev2_e_print(netdissect_options *ndo,
#ifndef HAVE_LIBCRYPTO
	      _U_
#endif
	      struct isakmp *base,
	      u_char tpay, 
	      const struct isakmp_gen *ext,
	      u_int item_len _U_, const u_char *ep _U_,
#ifndef HAVE_LIBCRYPTO
	      _U_
#endif
	      u_int32_t phase,
#ifndef HAVE_LIBCRYPTO
	      _U_
#endif
	      u_int32_t doi,
#ifndef HAVE_LIBCRYPTO
	      _U_
#endif
	      u_int32_t proto,
#ifndef HAVE_LIBCRYPTO
	      _U_
#endif
	      int depth)
{
	struct isakmp_gen e;
	u_char *dat;
	volatile int dlen;

	ND_TCHECK(*ext);
	UNALIGNED_MEMCPY(&e, ext, sizeof(e));
	ikev2_pay_print(ndo, NPSTR(tpay), e.critical);

	dlen = ntohs(e.len)-4;

	ND_PRINT((ndo," len=%d", dlen));
	if (2 < ndo->ndo_vflag && 4 < dlen) {
		ND_PRINT((ndo," "));
		if (!rawprint(ndo, (caddr_t)(ext + 1), dlen))
			goto trunc;
	}

	dat = (u_char *)(ext+1);
	ND_TCHECK2(*dat, dlen);
	
#ifdef HAVE_LIBCRYPTO
	/* try to decypt it! */
	if(esp_print_decrypt_buffer_by_ikev2(ndo,
					     base->flags & ISAKMP_FLAG_I,
					     base->i_ck, base->r_ck,
					     dat, dat+dlen)) {
		
		ext = (const struct isakmp_gen *)ndo->ndo_packetp;

		/* got it decrypted, print stuff inside. */
		ikev2_sub_print(ndo, base, e.np, ext, ndo->ndo_snapend,
				phase, doi, proto, depth+1);
	}
#endif
	

	/* always return NULL, because E must be at end, and NP refers
	 * to what was inside.
	 */
	return NULL;
trunc:
	ND_PRINT((ndo," [|%s]", NPSTR(tpay)));
	return NULL;
}

static const u_char *
ikev2_cp_print(netdissect_options *ndo, u_char tpay, 
		const struct isakmp_gen *ext,
		u_int item_len _U_, const u_char *ep _U_,
		u_int32_t phase _U_, u_int32_t doi _U_,
		u_int32_t proto _U_, int depth _U_)
{
	return ikev2_gen_print(ndo, tpay, ext);
}

static const u_char *
ikev2_eap_print(netdissect_options *ndo, u_char tpay, 
		const struct isakmp_gen *ext,
		u_int item_len _U_, const u_char *ep _U_,
		u_int32_t phase _U_, u_int32_t doi _U_,
		u_int32_t proto _U_, int depth _U_)
{
	return ikev2_gen_print(ndo, tpay, ext);
}

static const u_char *
ike_sub0_print(netdissect_options *ndo,
		 u_char np, const struct isakmp_gen *ext, const u_char *ep,

	       u_int32_t phase, u_int32_t doi, u_int32_t proto, int depth)
{
	const u_char *cp;
	struct isakmp_gen e;
	u_int item_len;

	cp = (u_char *)ext;
	ND_TCHECK(*ext);
	UNALIGNED_MEMCPY(&e, ext, sizeof(e));

	/*
	 * Since we can't have a payload length of less than 4 bytes,
	 * we need to bail out here if the generic header is nonsensical
	 * or truncated, otherwise we could loop forever processing
	 * zero-length items or otherwise misdissect the packet.
	 */
	item_len = ntohs(e.len);
	if (item_len <= 4)
		return NULL;

	if (NPFUNC(np)) {
		/*
		 * XXX - what if item_len is too short, or too long,
		 * for this payload type?
		 */
		cp = (*npfunc[np])(ndo, np, ext, item_len, ep, phase, doi, proto, depth);
	} else {
		ND_PRINT((ndo,"%s", NPSTR(np)));
		cp += item_len;
	}

	return cp;
trunc:
	ND_PRINT((ndo," [|isakmp]"));
	return NULL;
}

static const u_char *
ikev1_sub_print(netdissect_options *ndo,
		u_char np, const struct isakmp_gen *ext, const u_char *ep,
		u_int32_t phase, u_int32_t doi, u_int32_t proto, int depth)
{
	const u_char *cp;
	int i;
	struct isakmp_gen e;

	cp = (const u_char *)ext;

	while (np) {
		ND_TCHECK(*ext);

		UNALIGNED_MEMCPY(&e, ext, sizeof(e));

		ND_TCHECK2(*ext, ntohs(e.len));

		depth++;
		ND_PRINT((ndo,"\n"));
		for (i = 0; i < depth; i++)
			ND_PRINT((ndo,"    "));
		ND_PRINT((ndo,"("));
		cp = ike_sub0_print(ndo, np, ext, ep, phase, doi, proto, depth);
		ND_PRINT((ndo,")"));
		depth--;

		if (cp == NULL) {
			/* Zero-length subitem */
			return NULL;
		}

		np = e.np;
		ext = (struct isakmp_gen *)cp;
	}
	return cp;
trunc:
	ND_PRINT((ndo," [|%s]", NPSTR(np)));
	return NULL;
}

static char *
numstr(int x)
{
	static char buf[20];
	snprintf(buf, sizeof(buf), "#%d", x);
	return buf;
}

static void
ikev1_print(netdissect_options *ndo,
	    const u_char *bp,  u_int length,
	    const u_char *bp2, struct isakmp *base)
{
	const struct isakmp *p;
	const u_char *ep;
	u_char np;
	int i;
	int phase;
	
	p = (const struct isakmp *)bp;
	ep = ndo->ndo_snapend;
	
	phase = (EXTRACT_32BITS(base->msgid) == 0) ? 1 : 2;
	if (phase == 1)
		ND_PRINT((ndo," phase %d", phase));
	else
		ND_PRINT((ndo," phase %d/others", phase));
	
	i = cookie_find(&base->i_ck);
	if (i < 0) {
		if (iszero((u_char *)&base->r_ck, sizeof(base->r_ck))) {
			/* the first packet */
			ND_PRINT((ndo," I"));
			if (bp2)
				cookie_record(&base->i_ck, bp2);
		} else
			ND_PRINT((ndo," ?"));
	} else {
		if (bp2 && cookie_isinitiator(i, bp2))
			ND_PRINT((ndo," I"));
		else if (bp2 && cookie_isresponder(i, bp2))
			ND_PRINT((ndo," R"));
		else
			ND_PRINT((ndo," ?"));
	}
	
	ND_PRINT((ndo," %s", ETYPESTR(base->etype)));
	if (base->flags) {
		ND_PRINT((ndo,"[%s%s]", base->flags & ISAKMP_FLAG_E ? "E" : "",
			  base->flags & ISAKMP_FLAG_C ? "C" : ""));
	}
	
	if (ndo->ndo_vflag) {
		const struct isakmp_gen *ext;
		
		ND_PRINT((ndo,":"));
		
		/* regardless of phase... */
		if (base->flags & ISAKMP_FLAG_E) {
			/*
			 * encrypted, nothing we can do right now.
			 * we hope to decrypt the packet in the future...
			 */
			ND_PRINT((ndo," [encrypted %s]", NPSTR(base->np)));
			goto done;
		}
		
		CHECKLEN(p + 1, base->np);
		np = base->np;
		ext = (struct isakmp_gen *)(p + 1);
		ikev1_sub_print(ndo, np, ext, ep, phase, 0, 0, 0);
	}
	
done:
	if (ndo->ndo_vflag) {
		if (ntohl(base->len) != length) {
			ND_PRINT((ndo," (len mismatch: isakmp %u/ip %u)",
				  (u_int32_t)ntohl(base->len), length));
		}
	}
}

static const u_char *
ikev2_sub0_print(netdissect_options *ndo, struct isakmp *base,
		 u_char np, int pcount,
		 const struct isakmp_gen *ext, const u_char *ep,
		 u_int32_t phase, u_int32_t doi, u_int32_t proto, int depth)
{
	const u_char *cp;
	struct isakmp_gen e;
	u_int item_len;

	cp = (u_char *)ext;
	ND_TCHECK(*ext);
	UNALIGNED_MEMCPY(&e, ext, sizeof(e));

	/*
	 * Since we can't have a payload length of less than 4 bytes,
	 * we need to bail out here if the generic header is nonsensical
	 * or truncated, otherwise we could loop forever processing
	 * zero-length items or otherwise misdissect the packet.
	 */
	item_len = ntohs(e.len);
	if (item_len <= 4)
		return NULL;

	if(np == ISAKMP_NPTYPE_P) {
		cp = ikev2_p_print(ndo, np, pcount, ext, item_len,
				   ep, phase, doi, proto, depth);
	} else if(np == ISAKMP_NPTYPE_T) {
		cp = ikev2_t_print(ndo, np, pcount, ext, item_len,
				   ep, phase, doi, proto, depth);
	} else if(np == ISAKMP_NPTYPE_v2E) {
		cp = ikev2_e_print(ndo, base, np, ext, item_len,
				   ep, phase, doi, proto, depth);
	} else if (NPFUNC(np)) {
		/*
		 * XXX - what if item_len is too short, or too long,
		 * for this payload type?
		 */
		cp = (*npfunc[np])(ndo, np, /*pcount,*/ ext, item_len,
				   ep, phase, doi, proto, depth);
	} else {
		ND_PRINT((ndo,"%s", NPSTR(np)));
		cp += item_len;
	}

	return cp;
trunc:
	ND_PRINT((ndo," [|isakmp]"));
	return NULL;
}

static const u_char *
ikev2_sub_print(netdissect_options *ndo,
		struct isakmp *base,
		u_char np, const struct isakmp_gen *ext, const u_char *ep,
		u_int32_t phase, u_int32_t doi, u_int32_t proto, int depth)
{
	const u_char *cp;
	int i;
	int pcount;
	struct isakmp_gen e;

	cp = (const u_char *)ext;
	pcount = 0;						
	while (np) {
		pcount++;
		ND_TCHECK(*ext);

		UNALIGNED_MEMCPY(&e, ext, sizeof(e));

		ND_TCHECK2(*ext, ntohs(e.len));

		depth++;
		ND_PRINT((ndo,"\n"));
		for (i = 0; i < depth; i++)
			ND_PRINT((ndo,"    "));
		ND_PRINT((ndo,"("));
		cp = ikev2_sub0_print(ndo, base, np, pcount,
				      ext, ep, phase, doi, proto, depth);
		ND_PRINT((ndo,")"));
		depth--;

		if (cp == NULL) {
			/* Zero-length subitem */
			return NULL;
		}

		np = e.np;
		ext = (struct isakmp_gen *)cp;
	}
	return cp;
trunc:
	ND_PRINT((ndo," [|%s]", NPSTR(np)));
	return NULL;
}

static void
ikev2_print(netdissect_options *ndo,
	    const u_char *bp,  u_int length,
	    const u_char *bp2 _U_, struct isakmp *base)
{
	const struct isakmp *p;
	const u_char *ep;
	u_char np;
	int phase;

	p = (const struct isakmp *)bp;
	ep = ndo->ndo_snapend;

	phase = (EXTRACT_32BITS(base->msgid) == 0) ? 1 : 2;
	if (phase == 1)
		ND_PRINT((ndo, " parent_sa"));
	else
		ND_PRINT((ndo, " child_sa "));

	ND_PRINT((ndo, " %s", ETYPESTR(base->etype)));
	if (base->flags) {
		ND_PRINT((ndo, "[%s%s%s]",
			  base->flags & ISAKMP_FLAG_I ? "I" : "",
			  base->flags & ISAKMP_FLAG_V ? "V" : "",
			  base->flags & ISAKMP_FLAG_R ? "R" : ""));
	}

	if (ndo->ndo_vflag) {
		const struct isakmp_gen *ext;

		ND_PRINT((ndo, ":"));

		/* regardless of phase... */
		if (base->flags & ISAKMP_FLAG_E) {
			/*
			 * encrypted, nothing we can do right now.
			 * we hope to decrypt the packet in the future...
			 */
			ND_PRINT((ndo, " [encrypted %s]", NPSTR(base->np)));
			goto done;
		}

		CHECKLEN(p + 1, base->np)

		np = base->np;
		ext = (struct isakmp_gen *)(p + 1);
		ikev2_sub_print(ndo, base, np, ext, ep, phase, 0, 0, 0);
	}

done:
	if (ndo->ndo_vflag) {
		if (ntohl(base->len) != length) {
			ND_PRINT((ndo, " (len mismatch: isakmp %u/ip %u)",
				  (u_int32_t)ntohl(base->len), length));
		}
	}
}

void
isakmp_print(netdissect_options *ndo,
	     const u_char *bp, u_int length,
	     const u_char *bp2)
{
	const struct isakmp *p;
	struct isakmp base;
	const u_char *ep;
	int major, minor;

#ifdef HAVE_LIBCRYPTO
	/* initialize SAs */
	if (ndo->ndo_sa_list_head == NULL) {
		if (ndo->ndo_espsecret)
			esp_print_decodesecret(ndo);
	}
#endif

	p = (const struct isakmp *)bp;
	ep = ndo->ndo_snapend;

	if ((struct isakmp *)ep < p + 1) {
		ND_PRINT((ndo,"[|isakmp]"));
		return;
	}

	UNALIGNED_MEMCPY(&base, p, sizeof(base));

	ND_PRINT((ndo,"isakmp"));
	major = (base.vers & ISAKMP_VERS_MAJOR)
		>> ISAKMP_VERS_MAJOR_SHIFT;
	minor = (base.vers & ISAKMP_VERS_MINOR)
		>> ISAKMP_VERS_MINOR_SHIFT;

	if (ndo->ndo_vflag) {
		ND_PRINT((ndo," %d.%d", major, minor));
	}

	if (ndo->ndo_vflag) {
		ND_PRINT((ndo," msgid "));
		hexprint(ndo, (caddr_t)&base.msgid, sizeof(base.msgid));
	}

	if (1 < ndo->ndo_vflag) {
		ND_PRINT((ndo," cookie "));
		hexprint(ndo, (caddr_t)&base.i_ck, sizeof(base.i_ck));
		ND_PRINT((ndo,"->"));
		hexprint(ndo, (caddr_t)&base.r_ck, sizeof(base.r_ck));
	}
	ND_PRINT((ndo,":"));

	switch(major) {
	case IKEv1_MAJOR_VERSION:
		ikev1_print(ndo, bp, length, bp2, &base);
		break;

	case IKEv2_MAJOR_VERSION:
		ikev2_print(ndo, bp, length, bp2, &base);
		break;
	}
}

void
isakmp_rfc3948_print(netdissect_options *ndo,
		     const u_char *bp, u_int length,
		     const u_char *bp2)
{

	if(length == 1 && bp[0]==0xff) {
		ND_PRINT((ndo, "isakmp-nat-keep-alive"));
		return;
	}

	if(length < 4) {
		goto trunc;
	}
	
	/*
	 * see if this is an IKE packet
	 */
	if(bp[0]==0 && bp[1]==0 && bp[2]==0 && bp[3]==0) {
		ND_PRINT((ndo, "NONESP-encap: "));
		isakmp_print(ndo, bp+4, length-4, bp2);
		return;
	}

	/* must be an ESP packet */
	{
		int nh, enh, padlen;
		int advance;

		ND_PRINT((ndo, "UDP-encap: "));

		advance = esp_print(ndo, bp, length, bp2, &enh, &padlen);
		if(advance <= 0)
			return;

		bp += advance;
		length -= advance + padlen;
		nh = enh & 0xff;
	     
		ip_print_inner(ndo, bp, length, nh, bp2);
		return;
	}

trunc:
	ND_PRINT((ndo,"[|isakmp]"));
	return;
}

/*
 * Local Variables:
 * c-style: whitesmith
 * c-basic-offset: 8
 * End:
 */


  

