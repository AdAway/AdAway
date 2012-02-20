/*	$NetBSD: print-ah.c,v 1.4 1996/05/20 00:41:16 fvdl Exp $	*/

/*
 * Copyright (c) 1988, 1989, 1990, 1991, 1992, 1993, 1994
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
    "@(#) $Header: /tcpdump/master/tcpdump/print-esp.c,v 1.55.2.1 2005/04/21 06:44:57 guy Exp $ (LBL)";
#endif

#ifdef HAVE_CONFIG_H
#include "config.h"
#endif

#include <string.h>

#include <tcpdump-stdinc.h>

#include <stdlib.h>

#ifdef HAVE_LIBCRYPTO
#ifdef HAVE_OPENSSL_EVP_H
#include <openssl/evp.h>
#endif
#endif

#include <stdio.h>

#include "ip.h"
#include "esp.h"
#ifdef INET6
#include "ip6.h"
#endif

#include "netdissect.h"
#include "addrtoname.h"
#include "extract.h"

#ifndef HAVE_SOCKADDR_STORAGE
#ifdef INET6
struct sockaddr_storage {
	union {
		struct sockaddr_in sin;
		struct sockaddr_in6 sin6;
	} un;
};
#else
#define sockaddr_storage sockaddr
#endif
#endif /* HAVE_SOCKADDR_STORAGE */

#ifdef HAVE_LIBCRYPTO
struct sa_list {
	struct sa_list	*next;
	struct sockaddr_storage daddr;
	u_int32_t	spi;
	const EVP_CIPHER *evp;
	int		ivlen;
	int		authlen;
	u_char		secret[256];  /* is that big enough for all secrets? */
	int		secretlen;
};

static void esp_print_addsa(netdissect_options *ndo,
			    struct sa_list *sa, int sa_def)
{
	/* copy the "sa" */

	struct sa_list *nsa;

	nsa = (struct sa_list *)malloc(sizeof(struct sa_list));
	if (nsa == NULL)
		(*ndo->ndo_error)(ndo, "ran out of memory to allocate sa structure");

	*nsa = *sa;

	if (sa_def)
		ndo->ndo_sa_default = nsa;

	nsa->next = ndo->ndo_sa_list_head;
	ndo->ndo_sa_list_head = nsa;
}


static u_int hexdigit(netdissect_options *ndo, char hex)
{
	if (hex >= '0' && hex <= '9')
		return (hex - '0');
	else if (hex >= 'A' && hex <= 'F')
		return (hex - 'A' + 10);
	else if (hex >= 'a' && hex <= 'f')
		return (hex - 'a' + 10);
	else {
		(*ndo->ndo_error)(ndo, "invalid hex digit %c in espsecret\n", hex);
		return 0;
	}
}

static u_int hex2byte(netdissect_options *ndo, char *hexstring)
{
	u_int byte;

	byte = (hexdigit(ndo, hexstring[0]) << 4) + hexdigit(ndo, hexstring[1]);
	return byte;
}

/*
 * decode the form:    SPINUM@IP <tab> ALGONAME:0xsecret
 *
 * special form: file /name
 * causes us to go read from this file instead.
 *
 */
static void esp_print_decode_onesecret(netdissect_options *ndo, char *line)
{
	struct sa_list sa1;
	int sa_def;

	char *spikey;
	char *decode;

	spikey = strsep(&line, " \t");
	sa_def = 0;
	memset(&sa1, 0, sizeof(struct sa_list));

	/* if there is only one token, then it is an algo:key token */
	if (line == NULL) {
		decode = spikey;
		spikey = NULL;
		/* memset(&sa1.daddr, 0, sizeof(sa1.daddr)); */
		/* sa1.spi = 0; */
		sa_def    = 1;
	} else
		decode = line;

	if (spikey && strcasecmp(spikey, "file") == 0) {
		/* open file and read it */
		FILE *secretfile;
		char  fileline[1024];
		char  *nl;

		secretfile = fopen(line, FOPEN_READ_TXT);
		if (secretfile == NULL) {
			perror(line);
			exit(3);
		}

		while (fgets(fileline, sizeof(fileline)-1, secretfile) != NULL) {
			/* remove newline from the line */
			nl = strchr(fileline, '\n');
			if (nl)
				*nl = '\0';
			if (fileline[0] == '#') continue;
			if (fileline[0] == '\0') continue;

			esp_print_decode_onesecret(ndo, fileline);
		}
		fclose(secretfile);

		return;
	}

	if (spikey) {
		char *spistr, *foo;
		u_int32_t spino;
		struct sockaddr_in *sin;
#ifdef INET6
		struct sockaddr_in6 *sin6;
#endif

		spistr = strsep(&spikey, "@");

		spino = strtoul(spistr, &foo, 0);
		if (spistr == foo || !spikey) {
			(*ndo->ndo_warning)(ndo, "print_esp: failed to decode spi# %s\n", foo);
			return;
		}

		sa1.spi = spino;

		sin = (struct sockaddr_in *)&sa1.daddr;
#ifdef INET6
		sin6 = (struct sockaddr_in6 *)&sa1.daddr;
		if (inet_pton(AF_INET6, spikey, &sin6->sin6_addr) == 1) {
#ifdef HAVE_SOCKADDR_SA_LEN
			sin6->sin6_len = sizeof(struct sockaddr_in6);
#endif
			sin6->sin6_family = AF_INET6;
		} else
#endif
		if (inet_pton(AF_INET, spikey, &sin->sin_addr) == 1) {
#ifdef HAVE_SOCKADDR_SA_LEN
			sin->sin_len = sizeof(struct sockaddr_in);
#endif
			sin->sin_family = AF_INET;
		} else {
			(*ndo->ndo_warning)(ndo, "print_esp: can not decode IP# %s\n", spikey);
			return;
		}
	}

	if (decode) {
		char *colon, *p;
		u_char espsecret_key[256];
		int len;
		size_t i;
		const EVP_CIPHER *evp;
		int authlen = 0;

		/* skip any blank spaces */
		while (isspace((unsigned char)*decode))
			decode++;

		colon = strchr(decode, ':');
		if (colon == NULL) {
			(*ndo->ndo_warning)(ndo, "failed to decode espsecret: %s\n", decode);
			return;
		}
		*colon = '\0';

		len = colon - decode;
		if (strlen(decode) > strlen("-hmac96") &&
		    !strcmp(decode + strlen(decode) - strlen("-hmac96"),
		    "-hmac96")) {
			p = strstr(decode, "-hmac96");
			*p = '\0';
			authlen = 12;
		}
		if (strlen(decode) > strlen("-cbc") &&
		    !strcmp(decode + strlen(decode) - strlen("-cbc"), "-cbc")) {
			p = strstr(decode, "-cbc");
			*p = '\0';
		}
		evp = EVP_get_cipherbyname(decode);
		if (!evp) {
			(*ndo->ndo_warning)(ndo, "failed to find cipher algo %s\n", decode);
			sa1.evp = NULL;
			sa1.authlen = 0;
			sa1.ivlen = 0;
			return;
		}

		sa1.evp = evp;
		sa1.authlen = authlen;
		sa1.ivlen = EVP_CIPHER_iv_length(evp);

		colon++;
		if (colon[0] == '0' && colon[1] == 'x') {
			/* decode some hex! */
			colon += 2;
			len = strlen(colon) / 2;

			if (len > 256) {
				(*ndo->ndo_warning)(ndo, "secret is too big: %d\n", len);
				return;
			}

			i = 0;
			while (colon[0] != '\0' && colon[1]!='\0') {
				espsecret_key[i] = hex2byte(ndo, colon);
				colon += 2;
				i++;
			}

			memcpy(sa1.secret, espsecret_key, i);
			sa1.secretlen = i;
		} else {
			i = strlen(colon);

			if (i < sizeof(sa1.secret)) {
				memcpy(sa1.secret, colon, i);
				sa1.secretlen = i;
			} else {
				memcpy(sa1.secret, colon, sizeof(sa1.secret));
				sa1.secretlen = sizeof(sa1.secret);
			}
		}
	}

	esp_print_addsa(ndo, &sa1, sa_def);
}

static void esp_print_decodesecret(netdissect_options *ndo)
{
	char *line;
	char *p;

	p = ndo->ndo_espsecret;

	while (ndo->ndo_espsecret && ndo->ndo_espsecret[0] != '\0') {
		/* pick out the first line or first thing until a comma */
		if ((line = strsep(&ndo->ndo_espsecret, "\n,")) == NULL) {
			line = ndo->ndo_espsecret;
			ndo->ndo_espsecret = NULL;
		}

		esp_print_decode_onesecret(ndo, line);
	}
}

static void esp_init(netdissect_options *ndo _U_)
{

	OpenSSL_add_all_algorithms();
	EVP_add_cipher_alias(SN_des_ede3_cbc, "3des");
}
#endif

int
esp_print(netdissect_options *ndo,
	  const u_char *bp, const int length, const u_char *bp2
#ifndef HAVE_LIBCRYPTO
	_U_
#endif
	,
	int *nhdr
#ifndef HAVE_LIBCRYPTO
	_U_
#endif
	,
	int *padlen
#ifndef HAVE_LIBCRYPTO
	_U_
#endif
	)
{
	register const struct newesp *esp;
	register const u_char *ep;
#ifdef HAVE_LIBCRYPTO
	struct ip *ip;
	struct sa_list *sa = NULL;
	int espsecret_keylen;
#ifdef INET6
	struct ip6_hdr *ip6 = NULL;
#endif
	int advance;
	int len;
	u_char *secret;
	int ivlen = 0;
	u_char *ivoff;
	u_char *p;
	EVP_CIPHER_CTX ctx;
	int blocksz;
	static int initialized = 0;
#endif

	esp = (struct newesp *)bp;

#ifdef HAVE_LIBCRYPTO
	secret = NULL;
	advance = 0;

	if (!initialized) {
		esp_init(ndo);
		initialized = 1;
	}
#endif

#if 0
	/* keep secret out of a register */
	p = (u_char *)&secret;
#endif

	/* 'ep' points to the end of available data. */
	ep = ndo->ndo_snapend;

	if ((u_char *)(esp + 1) >= ep) {
		fputs("[|ESP]", stdout);
		goto fail;
	}
	(*ndo->ndo_printf)(ndo, "ESP(spi=0x%08x", EXTRACT_32BITS(&esp->esp_spi));
	(*ndo->ndo_printf)(ndo, ",seq=0x%x)", EXTRACT_32BITS(&esp->esp_seq));
        (*ndo->ndo_printf)(ndo, ", length %u", length);

#ifndef HAVE_LIBCRYPTO
	goto fail;
#else
	/* initiailize SAs */
	if (ndo->ndo_sa_list_head == NULL) {
		if (!ndo->ndo_espsecret)
			goto fail;

		esp_print_decodesecret(ndo);
	}

	if (ndo->ndo_sa_list_head == NULL)
		goto fail;

	ip = (struct ip *)bp2;
	switch (IP_V(ip)) {
#ifdef INET6
	case 6:
		ip6 = (struct ip6_hdr *)bp2;
		/* we do not attempt to decrypt jumbograms */
		if (!EXTRACT_16BITS(&ip6->ip6_plen))
			goto fail;
		/* if we can't get nexthdr, we do not need to decrypt it */
		len = sizeof(struct ip6_hdr) + EXTRACT_16BITS(&ip6->ip6_plen);

		/* see if we can find the SA, and if so, decode it */
		for (sa = ndo->ndo_sa_list_head; sa != NULL; sa = sa->next) {
			struct sockaddr_in6 *sin6 = (struct sockaddr_in6 *)&sa->daddr;
			if (sa->spi == ntohl(esp->esp_spi) &&
			    sin6->sin6_family == AF_INET6 &&
			    memcmp(&sin6->sin6_addr, &ip6->ip6_dst,
				   sizeof(struct in6_addr)) == 0) {
				break;
			}
		}
		break;
#endif /*INET6*/
	case 4:
		/* nexthdr & padding are in the last fragment */
		if (EXTRACT_16BITS(&ip->ip_off) & IP_MF)
			goto fail;
		len = EXTRACT_16BITS(&ip->ip_len);

		/* see if we can find the SA, and if so, decode it */
		for (sa = ndo->ndo_sa_list_head; sa != NULL; sa = sa->next) {
			struct sockaddr_in *sin = (struct sockaddr_in *)&sa->daddr;
			if (sa->spi == ntohl(esp->esp_spi) &&
			    sin->sin_family == AF_INET &&
			    sin->sin_addr.s_addr == ip->ip_dst.s_addr) {
				break;
			}
		}
		break;
	default:
		goto fail;
	}

	/* if we didn't find the specific one, then look for
	 * an unspecified one.
	 */
	if (sa == NULL)
		sa = ndo->ndo_sa_default;
	
	/* if not found fail */
	if (sa == NULL)
		goto fail;

	/* if we can't get nexthdr, we do not need to decrypt it */
	if (ep - bp2 < len)
		goto fail;
	if (ep - bp2 > len) {
		/* FCS included at end of frame (NetBSD 1.6 or later) */
		ep = bp2 + len;
	}

	ivoff = (u_char *)(esp + 1) + 0;
	ivlen = sa->ivlen;
	secret = sa->secret;
	espsecret_keylen = sa->secretlen;
	ep = ep - sa->authlen;

	if (sa->evp) {
		memset(&ctx, 0, sizeof(ctx));
		if (EVP_CipherInit(&ctx, sa->evp, secret, NULL, 0) < 0)
			(*ndo->ndo_warning)(ndo, "espkey init failed");

		blocksz = EVP_CIPHER_CTX_block_size(&ctx);

		p = ivoff;
		EVP_CipherInit(&ctx, NULL, NULL, p, 0);
		EVP_Cipher(&ctx, p + ivlen, p + ivlen, ep - (p + ivlen));
		advance = ivoff - (u_char *)esp + ivlen;
	} else
		advance = sizeof(struct newesp);

	/* sanity check for pad length */
	if (ep - bp < *(ep - 2))
		goto fail;

	if (padlen)
		*padlen = *(ep - 2) + 2;

	if (nhdr)
		*nhdr = *(ep - 1);

	(ndo->ndo_printf)(ndo, ": ");
	return advance;
#endif

fail:
	return -1;
}

/*
 * Local Variables:
 * c-style: whitesmith
 * c-basic-offset: 8
 * End:
 */
