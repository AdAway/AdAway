/*
 * Copyright (C) Arnaldo Carvalho de Melo 2004
 * Copyright (C) Ian McDonald 2005
 * Copyright (C) Yoshifumi Nishida 2005
 *
 * This software may be distributed either under the terms of the
 * BSD-style license that accompanies tcpdump or the GNU GPL version 2
 */

#ifndef lint
static const char rcsid[] _U_ =
    "@(#) $Header: /tcpdump/master/tcpdump/print-dccp.c,v 1.1.2.6 2006/02/19 05:08:44 guy Exp $ (LBL)";
#endif

#ifdef HAVE_CONFIG_H
#include "config.h"
#endif

#include <tcpdump-stdinc.h>

#include "dccp.h"

#include <stdio.h>
#include <string.h>

#include "interface.h"
#include "addrtoname.h"
#include "extract.h"			/* must come after interface.h */
#include "ip.h"
#ifdef INET6
#include "ip6.h"
#endif
#include "ipproto.h"

static const char *dccp_reset_codes[] = {
	"unspecified",
	"closed",
	"aborted",
	"no_connection",
	"packet_error",
	"option_error",
	"mandatory_error",
	"connection_refused",
	"bad_service_code",
	"too_busy",
	"bad_init_cookie",
	"aggression_penalty",
};

static const char *dccp_feature_nums[] = {
	"reserved", 
	"ccid",
	"allow_short_seqno",
	"sequence_window",
	"ecn_incapable", 
	"ack_ratio",     
	"send_ack_vector",
	"send_ndp_count", 
	"minimum checksum coverage", 
	"check data checksum",
};

static int dccp_cksum(const struct ip *ip,
	const struct dccp_hdr *dh, u_int len)
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
	phu.ph.mbz = 0;
	phu.ph.len = htons(len);
	phu.ph.proto = IPPROTO_DCCP;
	memcpy(&phu.ph.src, &ip->ip_src.s_addr, sizeof(u_int32_t));
	if (IP_HL(ip) == 5)
		memcpy(&phu.ph.dst, &ip->ip_dst.s_addr, sizeof(u_int32_t));
	else
		phu.ph.dst = ip_finddst(ip);

	sp = &phu.pa[0];
	return in_cksum((u_short *)dh, len, sp[0]+sp[1]+sp[2]+sp[3]+sp[4]+sp[5]);
}

#ifdef INET6
static int dccp6_cksum(const struct ip6_hdr *ip6, const struct dccp_hdr *dh, u_int len)
{
	size_t i;
	const u_int16_t *sp;
	u_int32_t sum;
	union {
		struct {
			struct in6_addr ph_src;
			struct in6_addr ph_dst;
			u_int32_t   ph_len;
			u_int8_t    ph_zero[3];
			u_int8_t    ph_nxt;
		} ph;
		u_int16_t pa[20];
	} phu;

	/* pseudo-header */
	memset(&phu, 0, sizeof(phu));
	phu.ph.ph_src = ip6->ip6_src;
	phu.ph.ph_dst = ip6->ip6_dst;
	phu.ph.ph_len = htonl(len);
	phu.ph.ph_nxt = IPPROTO_DCCP;

	sum = 0;
	for (i = 0; i < sizeof(phu.pa) / sizeof(phu.pa[0]); i++)
		sum += phu.pa[i];

	sp = (const u_int16_t *)dh;

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

static const char *dccp_reset_code(u_int8_t code)
{
	if (code >= __DCCP_RESET_CODE_LAST)
		return "invalid";
	return dccp_reset_codes[code];
}

static u_int64_t dccp_seqno(const struct dccp_hdr *dh)
{
	u_int32_t seq_high = DCCPH_SEQ(dh);
	u_int64_t seqno = EXTRACT_24BITS(&seq_high) & 0xFFFFFF;

	if (DCCPH_X(dh) != 0) {
		const struct dccp_hdr_ext *dhx = (void *)(dh + 1);
		u_int32_t seq_low = dhx->dccph_seq_low;
		seqno &= 0x00FFFF;  /* clear reserved field */
		seqno = (seqno << 32) + EXTRACT_32BITS(&seq_low);
	}

	return seqno;
}

static inline unsigned int dccp_basic_hdr_len(const struct dccp_hdr *dh)
{
	return sizeof(*dh) + (DCCPH_X(dh) ? sizeof(struct dccp_hdr_ext) : 0);
}

static void dccp_print_ack_no(const u_char *bp)
{
	const struct dccp_hdr *dh = (const struct dccp_hdr *)bp;
	const struct dccp_hdr_ack_bits *dh_ack =
		(struct dccp_hdr_ack_bits *)(bp + dccp_basic_hdr_len(dh));
	u_int32_t ack_high;
	u_int64_t ackno;

	TCHECK2(*dh_ack,4);
	ack_high = DCCPH_ACK(dh_ack);
	ackno = EXTRACT_24BITS(&ack_high) & 0xFFFFFF;

	if (DCCPH_X(dh) != 0) {
		u_int32_t ack_low;

		TCHECK2(*dh_ack,8);
		ack_low = dh_ack->dccph_ack_nr_low;

		ackno &= 0x00FFFF;  /* clear reserved field */
		ackno = (ackno << 32) + EXTRACT_32BITS(&ack_low);
	}

	(void)printf("(ack=%" PRIu64 ") ", ackno);
trunc:
	return;
}

static inline unsigned int dccp_packet_hdr_len(const u_int8_t type)
{
	if (type == DCCP_PKT_DATA)
		return 0;
	if (type == DCCP_PKT_DATAACK	||
	    type == DCCP_PKT_ACK	||
	    type == DCCP_PKT_SYNC	||
	    type == DCCP_PKT_SYNCACK	||
	    type == DCCP_PKT_CLOSE	||
	    type == DCCP_PKT_CLOSEREQ)
		return sizeof(struct dccp_hdr_ack_bits);
	if (type == DCCP_PKT_REQUEST)
		return sizeof(struct dccp_hdr_request);
	if (type == DCCP_PKT_RESPONSE)
		return sizeof(struct dccp_hdr_response);
	return sizeof(struct dccp_hdr_reset);
}

static int dccp_print_option(const u_char *option);

/**
 * dccp_print - show dccp packet
 * @bp - beginning of dccp packet
 * @data2 - beginning of enclosing 
 * @len - lenght of ip packet
 */
void dccp_print(const u_char *bp, const u_char *data2, u_int len)
{
	const struct dccp_hdr *dh;
	const struct ip *ip;
#ifdef INET6
	const struct ip6_hdr *ip6;
#endif
	const u_char *cp;
	u_short sport, dport;
	u_int hlen;
	u_int extlen = 0;

	dh = (const struct dccp_hdr *)bp;

	ip = (struct ip *)data2;
#ifdef INET6
	if (IP_V(ip) == 6)
		ip6 = (const struct ip6_hdr *)data2;
	else
		ip6 = NULL;
#endif /*INET6*/
	cp = (const u_char *)(dh + 1);
	if (cp > snapend) {
		printf("[Invalid packet|dccp]");
		return;
	}

	if (len < sizeof(struct dccp_hdr)) {
		printf("truncated-dccp - %ld bytes missing!",
			     (long)len - sizeof(struct dccp_hdr));
		return;
	}

	sport = EXTRACT_16BITS(&dh->dccph_sport);
	dport = EXTRACT_16BITS(&dh->dccph_dport);
	hlen = dh->dccph_doff * 4;

#ifdef INET6
	if (ip6) {
		(void)printf("%s.%d > %s.%d: ",
			     ip6addr_string(&ip6->ip6_src), sport,
			     ip6addr_string(&ip6->ip6_dst), dport);
	} else
#endif /*INET6*/
	{
		(void)printf("%s.%d > %s.%d: ",
			     ipaddr_string(&ip->ip_src), sport,
			     ipaddr_string(&ip->ip_dst), dport);
	}
	fflush(stdout);

	if (qflag) {
		(void)printf(" %d", len - hlen);
		if (hlen > len) {
			(void)printf("dccp [bad hdr length %u - too long, > %u]",
			    hlen, len);
		}
		return;
	}

	/* other variables in generic header */
	if (vflag) {
		(void)printf("CCVal %d, CsCov %d, ", DCCPH_CCVAL(dh), DCCPH_CSCOV(dh));
	}

	/* checksum calculation */
#ifdef INET6
	if (ip6) {
		if (ip6->ip6_plen && vflag) {
			u_int16_t sum, dccp_sum;

			sum = dccp6_cksum(ip6, dh, len);
			dccp_sum = EXTRACT_16BITS(&dh->dccph_checksum);		
			printf("cksum 0x%04x", dccp_sum);		
			if (sum != 0) {
				(void)printf(" (incorrect (-> 0x%04x), ",in_cksum_shouldbe(dccp_sum, sum));
			} else
				(void)printf(" (correct), ");
		}					
	} else 
#endif /* INET6 */
	if (vflag)
	{
		u_int16_t sum, dccp_sum;

		sum = dccp_cksum(ip, dh, len);
		dccp_sum = EXTRACT_16BITS(&dh->dccph_checksum);		
		printf("cksum 0x%04x", dccp_sum);		
		if (sum != 0) {
			(void)printf(" (incorrect (-> 0x%04x), ",in_cksum_shouldbe(dccp_sum, sum));
		} else
			(void)printf(" (correct), ");
	}

	switch (DCCPH_TYPE(dh)) {
	case DCCP_PKT_REQUEST: {
		struct dccp_hdr_request *dhr =
			(struct dccp_hdr_request *)(bp + dccp_basic_hdr_len(dh));
		TCHECK(*dhr);
		(void)printf("request (service=%d) ",
			     EXTRACT_32BITS(&dhr->dccph_req_service));
		extlen += 4;
		break;
	}
	case DCCP_PKT_RESPONSE: {
		struct dccp_hdr_response *dhr =
			(struct dccp_hdr_response *)(bp + dccp_basic_hdr_len(dh));
		TCHECK(*dhr);
		(void)printf("response (service=%d) ",
			     EXTRACT_32BITS(&dhr->dccph_resp_service));
		extlen += 12;
		break;
	}
	case DCCP_PKT_DATA:
		(void)printf("data ");
		break;
	case DCCP_PKT_ACK: {
		(void)printf("ack ");
		extlen += 8;
		break;
	}
	case DCCP_PKT_DATAACK: {
		(void)printf("dataack ");
		extlen += 8;
		break;
	}
	case DCCP_PKT_CLOSEREQ:
		(void)printf("closereq ");
		extlen += 8;
		break;
	case DCCP_PKT_CLOSE:
		(void)printf("close ");
		extlen += 8;
		break;
	case DCCP_PKT_RESET: {
		struct dccp_hdr_reset *dhr =
			(struct dccp_hdr_reset *)(bp + dccp_basic_hdr_len(dh));
		TCHECK(*dhr);
		(void)printf("reset (code=%s) ",
			     dccp_reset_code(dhr->dccph_reset_code));
		extlen += 12;
		break;
	}
	case DCCP_PKT_SYNC:
		(void)printf("sync ");
		extlen += 8;
		break;
	case DCCP_PKT_SYNCACK:
		(void)printf("syncack ");
		extlen += 8;
		break;
	default:
		(void)printf("invalid ");
		break;
	}

	if ((DCCPH_TYPE(dh) != DCCP_PKT_DATA) && 
			(DCCPH_TYPE(dh) != DCCP_PKT_REQUEST))
		dccp_print_ack_no(bp);

	if (vflag < 2)
		return;

	(void)printf("seq %" PRIu64, dccp_seqno(dh));

	/* process options */
	if (hlen > dccp_basic_hdr_len(dh) + extlen){
		const u_char *cp;
		u_int optlen;
		cp = bp + dccp_basic_hdr_len(dh) + extlen;
		printf(" <");	

		hlen -= dccp_basic_hdr_len(dh) + extlen;
		while(1){
			TCHECK(*cp);
			optlen = dccp_print_option(cp);
			if (!optlen) goto trunc2;
			if (hlen <= optlen) break; 
			hlen -= optlen;
			cp += optlen;
			printf(", ");
		}
		printf(">");	
	}
	return;
trunc:
	printf("[|dccp]");
trunc2:
	return;
}

static int dccp_print_option(const u_char *option)
{	
	u_int8_t optlen, i;
	u_int32_t *ts;
	u_int16_t *var16;
	u_int32_t *var32;

	TCHECK(*option);

	if (*option >= 32) {
		TCHECK(*(option+1));
		optlen = *(option +1);
		if (optlen < 2) {
			printf("Option %d optlen too short",*option);
			return 1;
		}
	} else optlen = 1;

	TCHECK2(*option,optlen);

	switch (*option){
	case 0:
		printf("nop");
		break;	
	case 1:
		printf("mandatory");
		break;	
	case 2:
		printf("slowreceiver");
		break;	
	case 32:
		printf("change_l");
		if (*(option +2) < 10){
			printf(" %s", dccp_feature_nums[*(option +2)]);
			for (i = 0; i < optlen -3; i ++) printf(" %d", *(option +3 + i));	
		}
		break;	
	case 33:
		printf("confirm_l");
		if (*(option +2) < 10){
			printf(" %s", dccp_feature_nums[*(option +2)]);
			for (i = 0; i < optlen -3; i ++) printf(" %d", *(option +3 + i));	
		}
		break;
	case 34:
	        printf("change_r");
		if (*(option +2) < 10){
			printf(" %s", dccp_feature_nums[*(option +2)]);
			for (i = 0; i < optlen -3; i ++) printf(" %d", *(option +3 + i));	
		}
		break;
	case 35:
		printf("confirm_r");
		if (*(option +2) < 10){
			printf(" %s", dccp_feature_nums[*(option +2)]);
			for (i = 0; i < optlen -3; i ++) printf(" %d", *(option +3 + i));	
		}
		break;
	case 36:
		printf("initcookie 0x");
		for (i = 0; i < optlen -2; i ++) printf("%02x", *(option +2 + i));	
		break;
	case 37:
		printf("ndp_count");
		for (i = 0; i < optlen -2; i ++) printf(" %d", *(option +2 + i));	
		break;
	case 38:
		printf("ack_vector0 0x");
		for (i = 0; i < optlen -2; i ++) printf("%02x", *(option +2 + i));	
		break;
	case 39:
		printf("ack_vector1 0x");
		for (i = 0; i < optlen -2; i ++) printf("%02x", *(option +2 + i));	
		break;
	case 40:
		printf("data_dropped 0x");
		for (i = 0; i < optlen -2; i ++) printf("%02x", *(option +2 + i));	
		break;
	case 41:
		ts = (u_int32_t *)(option + 2);
		printf("timestamp %u", (u_int32_t)ntohl(*ts));
		break;
	case 42:
		ts = (u_int32_t *)(option + 2);
		printf("timestamp_echo %u", (u_int32_t)ntohl(*ts));
		break;
	case 43:
		printf("elapsed_time ");
		if (optlen == 6){
			ts = (u_int32_t *)(option + 2);
			printf("%u", (u_int32_t)ntohl(*ts));
		} else {
			var16 = (u_int16_t *)(option + 2);
			printf("%u", ntohs(*var16));
		}	
		break;
	case 44:
		printf("data_checksum ");
		for (i = 0; i < optlen -2; i ++) printf("%02x", *(option +2 + i));	
		break;
	default :
		if (*option >= 128) {
			printf("CCID option %d",*option);
			switch (optlen) {
				case 4:
					var16 = (u_int16_t *)(option + 2);
					printf(" %u",ntohs(*var16));
					break;
				case 6:
					var32 = (u_int32_t *)(option + 2);
					printf(" %u",(u_int32_t)ntohl(*var32));
					break;
				default:
					break;
			}
			break;
		}
			
		printf("unknown_opt %d", *option);
		break;
	}

	return optlen;
trunc:
	printf("[|dccp]");
	return 0;
}
