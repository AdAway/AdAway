/* 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that: (1) source code
 * distributions retain the above copyright notice and this paragraph
 * in its entirety, and (2) distributions including binary code include
 * the above copyright notice and this paragraph in its entirety in
 * the documentation or other materials provided with the distribution.
 * THIS SOFTWARE IS PROVIDED ``AS IS'' AND
 * WITHOUT ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, WITHOUT
 * LIMITATION, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 * FOR A PARTICULAR PURPOSE.
 *
 * Original code by Hannes Gredler (hannes@juniper.net)
 */

#ifndef lint
static const char rcsid[] _U_ =
    "@(#) $Header: /tcpdump/master/tcpdump/print-juniper.c,v 1.8.2.22 2006/05/10 22:42:46 guy Exp $ (LBL)";
#endif

#ifdef HAVE_CONFIG_H
#include "config.h"
#endif

#include <tcpdump-stdinc.h>

#include <pcap.h>
#include <stdio.h>

#include "interface.h"
#include "addrtoname.h"
#include "extract.h"
#include "ppp.h"
#include "llc.h"
#include "nlpid.h"
#include "ethertype.h"
#include "atm.h"

#define JUNIPER_BPF_OUT           0       /* Outgoing packet */
#define JUNIPER_BPF_IN            1       /* Incoming packet */
#define JUNIPER_BPF_PKT_IN        0x1     /* Incoming packet */
#define JUNIPER_BPF_NO_L2         0x2     /* L2 header stripped */
#define JUNIPER_BPF_EXT           0x80    /* extensions present */
#define JUNIPER_MGC_NUMBER        0x4d4743 /* = "MGC" */

#define JUNIPER_LSQ_COOKIE_RE         (1 << 3)
#define JUNIPER_LSQ_COOKIE_DIR        (1 << 2)
#define JUNIPER_LSQ_L3_PROTO_SHIFT     4
#define JUNIPER_LSQ_L3_PROTO_MASK     (0x17 << JUNIPER_LSQ_L3_PROTO_SHIFT)
#define JUNIPER_LSQ_L3_PROTO_IPV4     (0 << JUNIPER_LSQ_L3_PROTO_SHIFT)
#define JUNIPER_LSQ_L3_PROTO_IPV6     (1 << JUNIPER_LSQ_L3_PROTO_SHIFT)
#define JUNIPER_LSQ_L3_PROTO_MPLS     (2 << JUNIPER_LSQ_L3_PROTO_SHIFT)
#define JUNIPER_LSQ_L3_PROTO_ISO      (3 << JUNIPER_LSQ_L3_PROTO_SHIFT)
#define AS_PIC_COOKIE_LEN 8

#define JUNIPER_IPSEC_O_ESP_ENCRYPT_ESP_AUTHEN_TYPE 1
#define JUNIPER_IPSEC_O_ESP_ENCRYPT_AH_AUTHEN_TYPE 2
#define JUNIPER_IPSEC_O_ESP_AUTHENTICATION_TYPE 3
#define JUNIPER_IPSEC_O_AH_AUTHENTICATION_TYPE 4
#define JUNIPER_IPSEC_O_ESP_ENCRYPTION_TYPE 5

static struct tok juniper_ipsec_type_values[] = {
    { JUNIPER_IPSEC_O_ESP_ENCRYPT_ESP_AUTHEN_TYPE, "ESP ENCR-AUTH" },
    { JUNIPER_IPSEC_O_ESP_ENCRYPT_AH_AUTHEN_TYPE, "ESP ENCR-AH AUTH" },
    { JUNIPER_IPSEC_O_ESP_AUTHENTICATION_TYPE, "ESP AUTH" },
    { JUNIPER_IPSEC_O_AH_AUTHENTICATION_TYPE, "AH AUTH" },
    { JUNIPER_IPSEC_O_ESP_ENCRYPTION_TYPE, "ESP ENCR" },
    { 0, NULL}
};

static struct tok juniper_direction_values[] = {
    { JUNIPER_BPF_IN,  "In"},
    { JUNIPER_BPF_OUT, "Out"},
    { 0, NULL}
};

struct juniper_cookie_table_t {
    u_int32_t pictype;		/* pic type */
    u_int8_t  cookie_len;       /* cookie len */
    const char *s;		/* pic name */
};

static struct juniper_cookie_table_t juniper_cookie_table[] = {
#ifdef DLT_JUNIPER_ATM1
    { DLT_JUNIPER_ATM1,  4, "ATM1"},
#endif
#ifdef DLT_JUNIPER_ATM2
    { DLT_JUNIPER_ATM2,  8, "ATM2"},
#endif
#ifdef DLT_JUNIPER_MLPPP
    { DLT_JUNIPER_MLPPP, 2, "MLPPP"},
#endif
#ifdef DLT_JUNIPER_MLFR
    { DLT_JUNIPER_MLFR,  2, "MLFR"},
#endif
#ifdef DLT_JUNIPER_MFR
    { DLT_JUNIPER_MFR,   4, "MFR"},
#endif
#ifdef DLT_JUNIPER_PPPOE
    { DLT_JUNIPER_PPPOE, 0, "PPPoE"},
#endif
#ifdef DLT_JUNIPER_PPPOE_ATM
    { DLT_JUNIPER_PPPOE_ATM, 0, "PPPoE ATM"},
#endif
#ifdef DLT_JUNIPER_GGSN
    { DLT_JUNIPER_GGSN, 8, "GGSN"},
#endif
#ifdef DLT_JUNIPER_MONITOR
    { DLT_JUNIPER_MONITOR, 8, "MONITOR"},
#endif
#ifdef DLT_JUNIPER_SERVICES
    { DLT_JUNIPER_SERVICES, 8, "AS"},
#endif
#ifdef DLT_JUNIPER_ES
    { DLT_JUNIPER_ES, 0, "ES"},
#endif
    { 0, 0, NULL }
};

struct juniper_l2info_t {
    u_int32_t length;
    u_int32_t caplen;
    u_int32_t pictype;
    u_int8_t direction;
    u_int8_t header_len;
    u_int8_t cookie_len;
    u_int8_t cookie_type;
    u_int8_t cookie[8];
    u_int8_t bundle;
    u_int16_t proto;
};

#define LS_COOKIE_ID            0x54
#define AS_COOKIE_ID            0x47
#define LS_MLFR_COOKIE_LEN	4
#define ML_MLFR_COOKIE_LEN	2
#define LS_MFR_COOKIE_LEN	6
#define ATM1_COOKIE_LEN         4
#define ATM2_COOKIE_LEN         8

#define ATM2_PKT_TYPE_MASK  0x70
#define ATM2_GAP_COUNT_MASK 0x3F

#define JUNIPER_PROTO_NULL          1
#define JUNIPER_PROTO_IPV4          2
#define JUNIPER_PROTO_IPV6          6

#define MFR_BE_MASK 0xc0

static struct tok juniper_protocol_values[] = {
    { JUNIPER_PROTO_NULL, "Null" },
    { JUNIPER_PROTO_IPV4, "IPv4" },
    { JUNIPER_PROTO_IPV6, "IPv6" },
    { 0, NULL}
};

int ip_heuristic_guess(register const u_char *, u_int);
int juniper_ppp_heuristic_guess(register const u_char *, u_int);
static int juniper_parse_header (const u_char *, const struct pcap_pkthdr *, struct juniper_l2info_t *);

#ifdef DLT_JUNIPER_GGSN
u_int
juniper_ggsn_print(const struct pcap_pkthdr *h, register const u_char *p)
{
        struct juniper_l2info_t l2info;
        struct juniper_ggsn_header {
            u_int8_t svc_id;
            u_int8_t flags_len;
            u_int8_t proto;
            u_int8_t flags;
            u_int8_t vlan_id[2];
            u_int8_t res[2];
        };
        const struct juniper_ggsn_header *gh;

        l2info.pictype = DLT_JUNIPER_GGSN;
        if(juniper_parse_header(p, h, &l2info) == 0)
            return l2info.header_len;

        p+=l2info.header_len;
        gh = (struct juniper_ggsn_header *)p;

        if (eflag)
            printf("proto %s (%u), vlan %u: ",
                   tok2str(juniper_protocol_values,"Unknown",gh->proto),
                   gh->proto,
                   EXTRACT_16BITS(&gh->vlan_id[0]));

        switch (gh->proto) {
        case JUNIPER_PROTO_IPV4:
            ip_print(gndo, p, l2info.length);
            break;
#ifdef INET6
        case JUNIPER_PROTO_IPV6:
            ip6_print(p, l2info.length);
            break;
#endif /* INET6 */
        default:
            if (!eflag)
                printf("unknown GGSN proto (%u)", gh->proto);
        }

        return l2info.header_len;
}
#endif

#ifdef DLT_JUNIPER_ES
u_int
juniper_es_print(const struct pcap_pkthdr *h, register const u_char *p)
{
        struct juniper_l2info_t l2info;
        struct juniper_ipsec_header {
            u_int8_t sa_index[2];
            u_int8_t ttl;
            u_int8_t type;
            u_int8_t spi[4];
            u_int8_t src_ip[4];
            u_int8_t dst_ip[4];
        };
        u_int rewrite_len,es_type_bundle;
        const struct juniper_ipsec_header *ih;

        l2info.pictype = DLT_JUNIPER_ES;
        if(juniper_parse_header(p, h, &l2info) == 0)
            return l2info.header_len;

        p+=l2info.header_len;
        ih = (struct juniper_ipsec_header *)p;

        switch (ih->type) {
        case JUNIPER_IPSEC_O_ESP_ENCRYPT_ESP_AUTHEN_TYPE:
        case JUNIPER_IPSEC_O_ESP_ENCRYPT_AH_AUTHEN_TYPE:
            rewrite_len = 0;
            es_type_bundle = 1;
            break;
        case JUNIPER_IPSEC_O_ESP_AUTHENTICATION_TYPE:
        case JUNIPER_IPSEC_O_AH_AUTHENTICATION_TYPE:
        case JUNIPER_IPSEC_O_ESP_ENCRYPTION_TYPE:
            rewrite_len = 16;
            es_type_bundle = 0;
        default:
            printf("ES Invalid type %u, length %u",
                   ih->type,
                   l2info.length);
            return l2info.header_len;
        }

        l2info.length-=rewrite_len;
        p+=rewrite_len;

        if (eflag) {
            if (!es_type_bundle) {
                printf("ES SA, index %u, ttl %u type %s (%u), spi %u, Tunnel %s > %s, length %u\n", 
                       EXTRACT_16BITS(&ih->sa_index),
                       ih->ttl, 
                       tok2str(juniper_ipsec_type_values,"Unknown",ih->type),
                       ih->type,
                       EXTRACT_32BITS(&ih->spi),
                       ipaddr_string(EXTRACT_32BITS(&ih->src_ip)),
                       ipaddr_string(EXTRACT_32BITS(&ih->dst_ip)),
                       l2info.length);
            } else {
                printf("ES SA, index %u, ttl %u type %s (%u), length %u\n", 
                       EXTRACT_16BITS(&ih->sa_index),
                       ih->ttl, 
                       tok2str(juniper_ipsec_type_values,"Unknown",ih->type),
                       ih->type,
                       l2info.length);
            }
        }

        ip_print(gndo, p, l2info.length);
        return l2info.header_len;
}
#endif

#ifdef DLT_JUNIPER_MONITOR
u_int
juniper_monitor_print(const struct pcap_pkthdr *h, register const u_char *p)
{
        struct juniper_l2info_t l2info;
        struct juniper_monitor_header {
            u_int8_t pkt_type;
            u_int8_t padding;
            u_int8_t iif[2];
            u_int8_t service_id[4];
        };
        const struct juniper_monitor_header *mh;

        l2info.pictype = DLT_JUNIPER_MONITOR;
        if(juniper_parse_header(p, h, &l2info) == 0)
            return l2info.header_len;

        p+=l2info.header_len;
        mh = (struct juniper_monitor_header *)p;

        if (eflag)
            printf("service-id %u, iif %u, pkt-type %u: ",
                   EXTRACT_32BITS(&mh->service_id),
                   EXTRACT_16BITS(&mh->iif),
                   mh->pkt_type);

        /* no proto field - lets guess by first byte of IP header*/
        ip_heuristic_guess(p, l2info.length);

        return l2info.header_len;
}
#endif

#ifdef DLT_JUNIPER_SERVICES
u_int
juniper_services_print(const struct pcap_pkthdr *h, register const u_char *p)
{
        struct juniper_l2info_t l2info;
        struct juniper_services_header {
            u_int8_t svc_id;
            u_int8_t flags_len;
            u_int8_t svc_set_id[2];
            u_int8_t dir_iif[4];
        };
        const struct juniper_services_header *sh;

        l2info.pictype = DLT_JUNIPER_SERVICES;
        if(juniper_parse_header(p, h, &l2info) == 0)
            return l2info.header_len;

        p+=l2info.header_len;
        sh = (struct juniper_services_header *)p;

        if (eflag)
            printf("service-id %u flags 0x%02x service-set-id 0x%04x iif %u: ",
                   sh->svc_id,
                   sh->flags_len,
                   EXTRACT_16BITS(&sh->svc_set_id),
                   EXTRACT_24BITS(&sh->dir_iif[1]));

        /* no proto field - lets guess by first byte of IP header*/
        ip_heuristic_guess(p, l2info.length);

        return l2info.header_len;
}
#endif

#ifdef DLT_JUNIPER_PPPOE
u_int
juniper_pppoe_print(const struct pcap_pkthdr *h, register const u_char *p)
{
        struct juniper_l2info_t l2info;

        l2info.pictype = DLT_JUNIPER_PPPOE;
        if(juniper_parse_header(p, h, &l2info) == 0)
            return l2info.header_len;

        p+=l2info.header_len;
        /* this DLT contains nothing but raw ethernet frames */
        ether_print(p, l2info.length, l2info.caplen);
        return l2info.header_len;
}
#endif

#ifdef DLT_JUNIPER_ETHER
u_int
juniper_ether_print(const struct pcap_pkthdr *h, register const u_char *p)
{
        struct juniper_l2info_t l2info;

        l2info.pictype = DLT_JUNIPER_ETHER;
        if(juniper_parse_header(p, h, &l2info) == 0)
            return l2info.header_len;

        p+=l2info.header_len;
        /* this DLT contains nothing but raw Ethernet frames */
        ether_print(p, l2info.length, l2info.caplen);
        return l2info.header_len;
}
#endif

#ifdef DLT_JUNIPER_PPP
u_int
juniper_ppp_print(const struct pcap_pkthdr *h, register const u_char *p)
{
        struct juniper_l2info_t l2info;

        l2info.pictype = DLT_JUNIPER_PPP;
        if(juniper_parse_header(p, h, &l2info) == 0)
            return l2info.header_len;

        p+=l2info.header_len;
        /* this DLT contains nothing but raw ppp frames */
        ppp_print(p, l2info.length);
        return l2info.header_len;
}
#endif

#ifdef DLT_JUNIPER_FRELAY
u_int
juniper_frelay_print(const struct pcap_pkthdr *h, register const u_char *p)
{
        struct juniper_l2info_t l2info;

        l2info.pictype = DLT_JUNIPER_FRELAY;
        if(juniper_parse_header(p, h, &l2info) == 0)
            return l2info.header_len;

        p+=l2info.header_len;
        /* this DLT contains nothing but raw frame-relay frames */
        fr_print(p, l2info.length);
        return l2info.header_len;
}
#endif

#ifdef DLT_JUNIPER_CHDLC
u_int
juniper_chdlc_print(const struct pcap_pkthdr *h, register const u_char *p)
{
        struct juniper_l2info_t l2info;

        l2info.pictype = DLT_JUNIPER_CHDLC;
        if(juniper_parse_header(p, h, &l2info) == 0)
            return l2info.header_len;

        p+=l2info.header_len;
        /* this DLT contains nothing but raw c-hdlc frames */
        chdlc_print(p, l2info.length);
        return l2info.header_len;
}
#endif

#ifdef DLT_JUNIPER_PPPOE_ATM
u_int
juniper_pppoe_atm_print(const struct pcap_pkthdr *h, register const u_char *p)
{
        struct juniper_l2info_t l2info;
	u_int16_t extracted_ethertype;

        l2info.pictype = DLT_JUNIPER_PPPOE_ATM;
        if(juniper_parse_header(p, h, &l2info) == 0)
            return l2info.header_len;

        p+=l2info.header_len;

        extracted_ethertype = EXTRACT_16BITS(p);
        /* this DLT contains nothing but raw PPPoE frames,
         * prepended with a type field*/
        if (ether_encap_print(extracted_ethertype,
                              p+ETHERTYPE_LEN,
                              l2info.length-ETHERTYPE_LEN,
                              l2info.caplen-ETHERTYPE_LEN,
                              &extracted_ethertype) == 0)
            /* ether_type not known, probably it wasn't one */
            printf("unknown ethertype 0x%04x", extracted_ethertype);
        
        return l2info.header_len;
}
#endif

#ifdef DLT_JUNIPER_MLPPP
u_int
juniper_mlppp_print(const struct pcap_pkthdr *h, register const u_char *p)
{
        struct juniper_l2info_t l2info;

        l2info.pictype = DLT_JUNIPER_MLPPP;
        if(juniper_parse_header(p, h, &l2info) == 0)
            return l2info.header_len;

        /* suppress Bundle-ID if frame was captured on a child-link
         * best indicator if the cookie looks like a proto */
        if (eflag &&
            EXTRACT_16BITS(&l2info.cookie) != PPP_OSI &&
            EXTRACT_16BITS(&l2info.cookie) !=  (PPP_ADDRESS << 8 | PPP_CONTROL))
            printf("Bundle-ID %u: ",l2info.bundle);

        p+=l2info.header_len;

        /* first try the LSQ protos */
        switch(l2info.proto) {
        case JUNIPER_LSQ_L3_PROTO_IPV4:
            /* IP traffic going to the RE would not have a cookie
             * -> this must be incoming IS-IS over PPP
             */
            if (l2info.cookie[4] == (JUNIPER_LSQ_COOKIE_RE|JUNIPER_LSQ_COOKIE_DIR))
                ppp_print(p, l2info.length);
            else
                ip_print(gndo, p, l2info.length);
            return l2info.header_len;
#ifdef INET6
        case JUNIPER_LSQ_L3_PROTO_IPV6:
            ip6_print(p,l2info.length);
            return l2info.header_len;
#endif
        case JUNIPER_LSQ_L3_PROTO_MPLS:
            mpls_print(p,l2info.length);
            return l2info.header_len;
        case JUNIPER_LSQ_L3_PROTO_ISO:
            isoclns_print(p,l2info.length,l2info.caplen);
            return l2info.header_len;
        default:
            break;
        }

        /* zero length cookie ? */
        switch (EXTRACT_16BITS(&l2info.cookie)) {
        case PPP_OSI:
            ppp_print(p-2,l2info.length+2);
            break;
        case (PPP_ADDRESS << 8 | PPP_CONTROL): /* fall through */
        default:
            ppp_print(p,l2info.length);
            break;
        }

        return l2info.header_len;
}
#endif


#ifdef DLT_JUNIPER_MFR
u_int
juniper_mfr_print(const struct pcap_pkthdr *h, register const u_char *p)
{
        struct juniper_l2info_t l2info;

        l2info.pictype = DLT_JUNIPER_MFR;
        if(juniper_parse_header(p, h, &l2info) == 0)
            return l2info.header_len;
        
        p+=l2info.header_len;

        /* child-link ? */
        if (l2info.cookie_len == 0) {
            mfr_print(p,l2info.length);
            return l2info.header_len;
        }

        /* first try the LSQ protos */
        if (l2info.cookie_len == AS_PIC_COOKIE_LEN) {
            switch(l2info.proto) {
            case JUNIPER_LSQ_L3_PROTO_IPV4:
                ip_print(gndo, p, l2info.length);
                return l2info.header_len;
#ifdef INET6
            case JUNIPER_LSQ_L3_PROTO_IPV6:
                ip6_print(p,l2info.length);
                return l2info.header_len;
#endif
            case JUNIPER_LSQ_L3_PROTO_MPLS:
                mpls_print(p,l2info.length);
                return l2info.header_len;
            case JUNIPER_LSQ_L3_PROTO_ISO:
                isoclns_print(p,l2info.length,l2info.caplen);
                return l2info.header_len;
            default:
                break;
            }
            return l2info.header_len;
        }

        /* suppress Bundle-ID if frame was captured on a child-link */
        if (eflag && EXTRACT_32BITS(l2info.cookie) != 1) printf("Bundle-ID %u, ",l2info.bundle);
        switch (l2info.proto) {
        case (LLCSAP_ISONS<<8 | LLCSAP_ISONS):
            isoclns_print(p+1, l2info.length-1, l2info.caplen-1);
            break;
        case (LLC_UI<<8 | NLPID_Q933):
        case (LLC_UI<<8 | NLPID_IP):
        case (LLC_UI<<8 | NLPID_IP6):
            /* pass IP{4,6} to the OSI layer for proper link-layer printing */
            isoclns_print(p-1, l2info.length+1, l2info.caplen+1); 
            break;
        default:
            printf("unknown protocol 0x%04x, length %u",l2info.proto, l2info.length);
        }

        return l2info.header_len;
}
#endif

#ifdef DLT_JUNIPER_MLFR
u_int
juniper_mlfr_print(const struct pcap_pkthdr *h, register const u_char *p)
{
        struct juniper_l2info_t l2info;

        l2info.pictype = DLT_JUNIPER_MLFR;
        if(juniper_parse_header(p, h, &l2info) == 0)
            return l2info.header_len;

        p+=l2info.header_len;

        /* suppress Bundle-ID if frame was captured on a child-link */
        if (eflag && EXTRACT_32BITS(l2info.cookie) != 1) printf("Bundle-ID %u, ",l2info.bundle);
        switch (l2info.proto) {
        case (LLC_UI):
        case (LLC_UI<<8):
            isoclns_print(p, l2info.length, l2info.caplen);
            break;
        case (LLC_UI<<8 | NLPID_Q933):
        case (LLC_UI<<8 | NLPID_IP):
        case (LLC_UI<<8 | NLPID_IP6):
            /* pass IP{4,6} to the OSI layer for proper link-layer printing */
            isoclns_print(p-1, l2info.length+1, l2info.caplen+1);
            break;
        default:
            printf("unknown protocol 0x%04x, length %u",l2info.proto, l2info.length);
        }

        return l2info.header_len;
}
#endif

/*
 *     ATM1 PIC cookie format
 *
 *     +-----+-------------------------+-------------------------------+
 *     |fmtid|     vc index            |  channel  ID                  |
 *     +-----+-------------------------+-------------------------------+
 */

#ifdef DLT_JUNIPER_ATM1
u_int
juniper_atm1_print(const struct pcap_pkthdr *h, register const u_char *p)
{
        u_int16_t extracted_ethertype;

        struct juniper_l2info_t l2info;

        l2info.pictype = DLT_JUNIPER_ATM1;
        if(juniper_parse_header(p, h, &l2info) == 0)
            return l2info.header_len;

        p+=l2info.header_len;

        if (l2info.cookie[0] == 0x80) { /* OAM cell ? */
            oam_print(p,l2info.length,ATM_OAM_NOHEC);
            return l2info.header_len;
        }

        if (EXTRACT_24BITS(p) == 0xfefe03 || /* NLPID encaps ? */
            EXTRACT_24BITS(p) == 0xaaaa03) { /* SNAP encaps ? */

            if (llc_print(p, l2info.length, l2info.caplen, NULL, NULL,
                          &extracted_ethertype) != 0)
                return l2info.header_len;
        }

        if (p[0] == 0x03) { /* Cisco style NLPID encaps ? */
            isoclns_print(p + 1, l2info.length - 1, l2info.caplen - 1);
            /* FIXME check if frame was recognized */
            return l2info.header_len;
        }

        if(ip_heuristic_guess(p, l2info.length) != 0) /* last try - vcmux encaps ? */
            return l2info.header_len;

	return l2info.header_len;
}
#endif

/*
 *     ATM2 PIC cookie format
 *
 *     +-------------------------------+---------+---+-----+-----------+
 *     |     channel ID                |  reserv |AAL| CCRQ| gap cnt   |
 *     +-------------------------------+---------+---+-----+-----------+
 */

#ifdef DLT_JUNIPER_ATM2
u_int
juniper_atm2_print(const struct pcap_pkthdr *h, register const u_char *p)
{
        u_int16_t extracted_ethertype;

        struct juniper_l2info_t l2info;

        l2info.pictype = DLT_JUNIPER_ATM2;
        if(juniper_parse_header(p, h, &l2info) == 0)
            return l2info.header_len;

        p+=l2info.header_len;

        if (l2info.cookie[7] & ATM2_PKT_TYPE_MASK) { /* OAM cell ? */
            oam_print(p,l2info.length,ATM_OAM_NOHEC);
            return l2info.header_len;
        }

        if (EXTRACT_24BITS(p) == 0xfefe03 || /* NLPID encaps ? */
            EXTRACT_24BITS(p) == 0xaaaa03) { /* SNAP encaps ? */

            if (llc_print(p, l2info.length, l2info.caplen, NULL, NULL,
                          &extracted_ethertype) != 0)
                return l2info.header_len;
        }

        if (l2info.direction != JUNIPER_BPF_PKT_IN && /* ether-over-1483 encaps ? */
            (EXTRACT_32BITS(l2info.cookie) & ATM2_GAP_COUNT_MASK)) {
            ether_print(p, l2info.length, l2info.caplen);
            return l2info.header_len;
        }

        if (p[0] == 0x03) { /* Cisco style NLPID encaps ? */
            isoclns_print(p + 1, l2info.length - 1, l2info.caplen - 1);
            /* FIXME check if frame was recognized */
            return l2info.header_len;
        }

        if(juniper_ppp_heuristic_guess(p, l2info.length) != 0) /* PPPoA vcmux encaps ? */
            return l2info.header_len;

        if(ip_heuristic_guess(p, l2info.length) != 0) /* last try - vcmux encaps ? */
            return l2info.header_len;

	return l2info.header_len;
}
#endif


/* try to guess, based on all PPP protos that are supported in
 * a juniper router if the payload data is encapsulated using PPP */
int
juniper_ppp_heuristic_guess(register const u_char *p, u_int length) {

    switch(EXTRACT_16BITS(p)) {
    case PPP_IP :
    case PPP_OSI :
    case PPP_MPLS_UCAST :
    case PPP_MPLS_MCAST :
    case PPP_IPCP :
    case PPP_OSICP :
    case PPP_MPLSCP :
    case PPP_LCP :
    case PPP_PAP :
    case PPP_CHAP :
    case PPP_ML :
#ifdef INET6
    case PPP_IPV6 :
    case PPP_IPV6CP :
#endif
        ppp_print(p, length);
        break;

    default:
        return 0; /* did not find a ppp header */
        break;
    }
    return 1; /* we printed a ppp packet */
}

int
ip_heuristic_guess(register const u_char *p, u_int length) {

    switch(p[0]) {
    case 0x45:
    case 0x46:
    case 0x47:
    case 0x48:
    case 0x49:
    case 0x4a:
    case 0x4b:
    case 0x4c:
    case 0x4d:
    case 0x4e:
    case 0x4f:
	    ip_print(gndo, p, length);
	    break;
#ifdef INET6
    case 0x60:
    case 0x61:
    case 0x62:
    case 0x63:
    case 0x64:
    case 0x65:
    case 0x66:
    case 0x67:
    case 0x68:
    case 0x69:
    case 0x6a:
    case 0x6b:
    case 0x6c:
    case 0x6d:
    case 0x6e:
    case 0x6f:
        ip6_print(p, length);
        break;
#endif
    default:
        return 0; /* did not find a ip header */
        break;
    }
    return 1; /* we printed an v4/v6 packet */
}

static int
juniper_parse_header (const u_char *p, const struct pcap_pkthdr *h, struct juniper_l2info_t *l2info) {

    struct juniper_cookie_table_t *lp = juniper_cookie_table;
    u_int idx, offset;
#ifdef DLT_JUNIPER_ATM2
    u_int32_t control_word;
#endif

    l2info->header_len = 0;
    l2info->cookie_len = 0;
    l2info->proto = 0;


    l2info->length = h->len;
    l2info->caplen = h->caplen;
    l2info->direction = p[3]&JUNIPER_BPF_PKT_IN;
    
    TCHECK2(p[0],4);
    if (EXTRACT_24BITS(p) != JUNIPER_MGC_NUMBER) { /* magic number found ? */
        printf("no magic-number found!");
        return 0;
    } 

    if (eflag) /* print direction */
        printf("%3s ",tok2str(juniper_direction_values,"---",l2info->direction));

    /* extensions present ?  - calculate how much bytes to skip */
    if ((p[3] & JUNIPER_BPF_EXT ) == JUNIPER_BPF_EXT ) {
        offset = 6 + EXTRACT_16BITS(p+4);
        if (eflag>1)
            printf("ext-len %u, ",EXTRACT_16BITS(p+4));
    } else
        offset = 4;

    if ((p[3] & JUNIPER_BPF_NO_L2 ) == JUNIPER_BPF_NO_L2 ) {            
        if (eflag)
            printf("no-L2-hdr, ");

        /* there is no link-layer present -
         * perform the v4/v6 heuristics
         * to figure out what it is
         */
        TCHECK2(p[offset+4],1);
        if(ip_heuristic_guess(p+offset+4,l2info->length-(offset+4)) == 0)
            printf("no IP-hdr found!");

        l2info->header_len=offset+4;
        return 0; /* stop parsing the output further */
        
    }
    l2info->header_len = offset;
    p+=l2info->header_len;
    l2info->length -= l2info->header_len;
    l2info->caplen -= l2info->header_len;

    /* search through the cookie table and copy values matching for our PIC type */
    while (lp->s != NULL) {
        if (lp->pictype == l2info->pictype) {

            l2info->cookie_len += lp->cookie_len;

            switch (p[0]) {
            case LS_COOKIE_ID:
                l2info->cookie_type = LS_COOKIE_ID;
                l2info->cookie_len += 2;
                break;
            case AS_COOKIE_ID:
                l2info->cookie_type = AS_COOKIE_ID;
                l2info->cookie_len = 8;
                break;
            
            default:
                l2info->bundle = l2info->cookie[0];
                break;
            }


#ifdef DLT_JUNIPER_MFR
            /* MFR child links don't carry cookies */
            if (l2info->pictype == DLT_JUNIPER_MFR &&
                (p[0] & MFR_BE_MASK) == MFR_BE_MASK) {
                l2info->cookie_len = 0;
            }
#endif

            l2info->header_len += l2info->cookie_len;
            l2info->length -= l2info->cookie_len;
            l2info->caplen -= l2info->cookie_len;

            if (eflag)
                printf("%s-PIC, cookie-len %u",
                       lp->s,
                       l2info->cookie_len);

            if (l2info->cookie_len > 0) {
                TCHECK2(p[0],l2info->cookie_len);
                if (eflag)
                    printf(", cookie 0x");
                for (idx = 0; idx < l2info->cookie_len; idx++) {
                    l2info->cookie[idx] = p[idx]; /* copy cookie data */
                    if (eflag) printf("%02x",p[idx]);
                }
            }

            if (eflag) printf(": "); /* print demarc b/w L2/L3*/
            

            l2info->proto = EXTRACT_16BITS(p+l2info->cookie_len); 
            break;
        }
        ++lp;
    }
    p+=l2info->cookie_len;

    /* DLT_ specific parsing */
    switch(l2info->pictype) {
#ifdef DLT_JUNIPER_MLPPP
    case DLT_JUNIPER_MLPPP:
        switch (l2info->cookie_type) {
        case LS_COOKIE_ID:
            l2info->bundle = l2info->cookie[1];
            break;
        case AS_COOKIE_ID:
            l2info->bundle = (EXTRACT_16BITS(&l2info->cookie[6])>>3)&0xfff;
            l2info->proto = (l2info->cookie[5])&JUNIPER_LSQ_L3_PROTO_MASK;            
            break;
        default:
            l2info->bundle = l2info->cookie[0];
            break;
        }
        break;
#endif
#ifdef DLT_JUNIPER_MLFR
    case DLT_JUNIPER_MLFR:
        switch (l2info->cookie_type) {
        case LS_COOKIE_ID:
            l2info->bundle = l2info->cookie[1];
            l2info->proto = EXTRACT_16BITS(p);        
            l2info->header_len += 2;
            l2info->length -= 2;
            l2info->caplen -= 2;
            break;
        case AS_COOKIE_ID:
            l2info->bundle = (EXTRACT_16BITS(&l2info->cookie[6])>>3)&0xfff;
            l2info->proto = (l2info->cookie[5])&JUNIPER_LSQ_L3_PROTO_MASK;
            break;
        default:
            l2info->bundle = l2info->cookie[0];
            l2info->header_len += 2;
            l2info->length -= 2;
            l2info->caplen -= 2;
            break;
        }
        break;
#endif
#ifdef DLT_JUNIPER_MFR
    case DLT_JUNIPER_MFR:
        switch (l2info->cookie_type) {
        case LS_COOKIE_ID:
            l2info->bundle = l2info->cookie[1];
            l2info->proto = EXTRACT_16BITS(p);        
            l2info->header_len += 2;
            l2info->length -= 2;
            l2info->caplen -= 2;
            break;
        case AS_COOKIE_ID:
            l2info->bundle = (EXTRACT_16BITS(&l2info->cookie[6])>>3)&0xfff;
            l2info->proto = (l2info->cookie[5])&JUNIPER_LSQ_L3_PROTO_MASK;
            break;
        default:
            l2info->bundle = l2info->cookie[0];
            break;
        }
        break;
#endif
#ifdef DLT_JUNIPER_ATM2
    case DLT_JUNIPER_ATM2:
        TCHECK2(p[0],4);
        /* ATM cell relay control word present ? */
        if (l2info->cookie[7] & ATM2_PKT_TYPE_MASK) {
            control_word = EXTRACT_32BITS(p);
            /* some control word heuristics */
            switch(control_word) {
            case 0: /* zero control word */
            case 0x08000000: /* < JUNOS 7.4 control-word */
            case 0x08380000: /* cntl word plus cell length (56) >= JUNOS 7.4*/
                l2info->header_len += 4;
                break;
            default:
                break;
            }
            
            if (eflag)
                printf("control-word 0x%08x ", control_word);
        }
        break;
#endif
#ifdef DLT_JUNIPER_ATM1
    case DLT_JUNIPER_ATM1:
        break;
#endif
#ifdef DLT_JUNIPER_PPP
    case DLT_JUNIPER_PPP:
        break;
#endif
#ifdef DLT_JUNIPER_CHDLC
    case DLT_JUNIPER_CHDLC:
        break;
#endif
#ifdef DLT_JUNIPER_ETHER
    case DLT_JUNIPER_ETHER:
        break;
#endif
#ifdef DLT_JUNIPER_FRELAY
    case DLT_JUNIPER_FRELAY:
        break;
#endif

    default:
        printf("Unknown Juniper DLT_ type %u: ", l2info->pictype);
        break;
    }
    
    if (eflag > 1)
        printf("hlen %u, proto 0x%04x, ",l2info->header_len,l2info->proto);

    return 1; /* everything went ok so far. continue parsing */
 trunc:
    printf("[|juniper_hdr], length %u",h->len);
    return 0;
}


/*
 * Local Variables:
 * c-style: whitesmith
 * c-basic-offset: 4
 * End:
 */
