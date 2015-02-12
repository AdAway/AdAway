/*
 * Copyright (c) 1998-2004  Hannes Gredler <hannes@tcpdump.org>
 *      The TCPDUMP project
 *
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
 */

#ifndef lint
static const char rcsid[] _U_ =
    "@(#) $Header: /tcpdump/master/tcpdump/print-eigrp.c,v 1.7 2005-05-06 02:53:26 guy Exp $";
#endif

#ifdef HAVE_CONFIG_H
#include "config.h"
#endif

#include <tcpdump-stdinc.h>

#include <stdio.h>
#include <stdlib.h>
#include <string.h>

#include "interface.h"
#include "extract.h"
#include "addrtoname.h"

/*
 * packet format documented at
 * http://www.rhyshaden.com/eigrp.htm
 */

struct eigrp_common_header {
    u_int8_t version;
    u_int8_t opcode;
    u_int8_t checksum[2];
    u_int8_t flags[4];
    u_int8_t seq[4];
    u_int8_t ack[4];
    u_int8_t asn[4];
};

#define	EIGRP_VERSION                        2

#define	EIGRP_OPCODE_UPDATE                  1
#define	EIGRP_OPCODE_QUERY                   3
#define	EIGRP_OPCODE_REPLY                   4
#define	EIGRP_OPCODE_HELLO                   5
#define	EIGRP_OPCODE_IPXSAP                  6
#define	EIGRP_OPCODE_PROBE                   7

static const struct tok eigrp_opcode_values[] = {
    { EIGRP_OPCODE_UPDATE, "Update" },
    { EIGRP_OPCODE_QUERY, "Query" },
    { EIGRP_OPCODE_REPLY, "Reply" },
    { EIGRP_OPCODE_HELLO, "Hello" },
    { EIGRP_OPCODE_IPXSAP, "IPX SAP" },
    { EIGRP_OPCODE_PROBE, "Probe" },
    { 0, NULL}
};

static const struct tok eigrp_common_header_flag_values[] = {
    { 0x01, "Init" },
    { 0x02, "Conditionally Received" },
    { 0, NULL}
};

struct eigrp_tlv_header {
    u_int8_t type[2];
    u_int8_t length[2];
};

#define EIGRP_TLV_GENERAL_PARM   0x0001
#define EIGRP_TLV_AUTH           0x0002
#define EIGRP_TLV_SEQ            0x0003
#define EIGRP_TLV_SW_VERSION     0x0004
#define EIGRP_TLV_MCAST_SEQ      0x0005
#define EIGRP_TLV_IP_INT         0x0102
#define EIGRP_TLV_IP_EXT         0x0103
#define EIGRP_TLV_AT_INT         0x0202
#define EIGRP_TLV_AT_EXT         0x0203
#define EIGRP_TLV_AT_CABLE_SETUP 0x0204
#define EIGRP_TLV_IPX_INT        0x0302
#define EIGRP_TLV_IPX_EXT        0x0303

static const struct tok eigrp_tlv_values[] = {
    { EIGRP_TLV_GENERAL_PARM, "General Parameters"},
    { EIGRP_TLV_AUTH, "Authentication"},
    { EIGRP_TLV_SEQ, "Sequence"},
    { EIGRP_TLV_SW_VERSION, "Software Version"},
    { EIGRP_TLV_MCAST_SEQ, "Next Multicast Sequence"},
    { EIGRP_TLV_IP_INT, "IP Internal routes"},
    { EIGRP_TLV_IP_EXT, "IP External routes"},
    { EIGRP_TLV_AT_INT, "AppleTalk Internal routes"},
    { EIGRP_TLV_AT_EXT, "AppleTalk External routes"},
    { EIGRP_TLV_AT_CABLE_SETUP, "AppleTalk Cable setup"},
    { EIGRP_TLV_IPX_INT, "IPX Internal routes"},
    { EIGRP_TLV_IPX_EXT, "IPX External routes"},
    { 0, NULL}
};

struct eigrp_tlv_general_parm_t {
    u_int8_t k1;
    u_int8_t k2;
    u_int8_t k3;
    u_int8_t k4;
    u_int8_t k5;
    u_int8_t res;
    u_int8_t holdtime[2];
};          

struct eigrp_tlv_sw_version_t {
    u_int8_t ios_major;
    u_int8_t ios_minor;
    u_int8_t eigrp_major;
    u_int8_t eigrp_minor;
}; 

struct eigrp_tlv_ip_int_t {
    u_int8_t nexthop[4];
    u_int8_t delay[4];
    u_int8_t bandwidth[4];
    u_int8_t mtu[3];
    u_int8_t hopcount;
    u_int8_t reliability;
    u_int8_t load;
    u_int8_t reserved[2];
    u_int8_t plen;
    u_int8_t destination; /* variable length [1-4] bytes encoding */
}; 

struct eigrp_tlv_ip_ext_t {
    u_int8_t nexthop[4];
    u_int8_t origin_router[4];
    u_int8_t origin_as[4];
    u_int8_t tag[4];
    u_int8_t metric[4];
    u_int8_t reserved[2];
    u_int8_t proto_id;
    u_int8_t flags;
    u_int8_t delay[4];
    u_int8_t bandwidth[4];
    u_int8_t mtu[3];
    u_int8_t hopcount;
    u_int8_t reliability;
    u_int8_t load;
    u_int8_t reserved2[2];
    u_int8_t plen;
    u_int8_t destination; /* variable length [1-4] bytes encoding */
}; 

struct eigrp_tlv_at_cable_setup_t {
    u_int8_t cable_start[2];
    u_int8_t cable_end[2];
    u_int8_t router_id[4];
};

struct eigrp_tlv_at_int_t {
    u_int8_t nexthop[4];
    u_int8_t delay[4];
    u_int8_t bandwidth[4];
    u_int8_t mtu[3];
    u_int8_t hopcount;
    u_int8_t reliability;
    u_int8_t load;
    u_int8_t reserved[2];
    u_int8_t cable_start[2];
    u_int8_t cable_end[2];
}; 

struct eigrp_tlv_at_ext_t {
    u_int8_t nexthop[4];
    u_int8_t origin_router[4];
    u_int8_t origin_as[4];
    u_int8_t tag[4];
    u_int8_t proto_id;
    u_int8_t flags;
    u_int8_t metric[2];
    u_int8_t delay[4];
    u_int8_t bandwidth[4];
    u_int8_t mtu[3];
    u_int8_t hopcount;
    u_int8_t reliability;
    u_int8_t load;
    u_int8_t reserved2[2];
    u_int8_t cable_start[2];
    u_int8_t cable_end[2];
};

static const struct tok eigrp_ext_proto_id_values[] = {
    { 0x01, "IGRP" },
    { 0x02, "EIGRP" },
    { 0x03, "Static" },
    { 0x04, "RIP" },
    { 0x05, "Hello" },
    { 0x06, "OSPF" },
    { 0x07, "IS-IS" },
    { 0x08, "EGP" },
    { 0x09, "BGP" },
    { 0x0a, "IDRP" },
    { 0x0b, "Connected" },
    { 0, NULL}
};

void
eigrp_print(register const u_char *pptr, register u_int len) {

    const struct eigrp_common_header *eigrp_com_header;
    const struct eigrp_tlv_header *eigrp_tlv_header;
    const u_char *tptr,*tlv_tptr;
    u_int tlen,eigrp_tlv_len,eigrp_tlv_type,tlv_tlen, byte_length, bit_length;
    u_int8_t prefix[4];

    union {
        const struct eigrp_tlv_general_parm_t *eigrp_tlv_general_parm;
        const struct eigrp_tlv_sw_version_t *eigrp_tlv_sw_version;
        const struct eigrp_tlv_ip_int_t *eigrp_tlv_ip_int;
        const struct eigrp_tlv_ip_ext_t *eigrp_tlv_ip_ext;
        const struct eigrp_tlv_at_cable_setup_t *eigrp_tlv_at_cable_setup;
        const struct eigrp_tlv_at_int_t *eigrp_tlv_at_int;
        const struct eigrp_tlv_at_ext_t *eigrp_tlv_at_ext;
    } tlv_ptr;

    tptr=pptr;
    eigrp_com_header = (const struct eigrp_common_header *)pptr;
    TCHECK(*eigrp_com_header);

    /*
     * Sanity checking of the header.
     */
    if (eigrp_com_header->version != EIGRP_VERSION) {
	printf("EIGRP version %u packet not supported",eigrp_com_header->version);
	return;
    }

    /* in non-verbose mode just lets print the basic Message Type*/
    if (vflag < 1) {
        printf("EIGRP %s, length: %u",
               tok2str(eigrp_opcode_values, "unknown (%u)",eigrp_com_header->opcode),
               len);
        return;
    }

    /* ok they seem to want to know everything - lets fully decode it */

    tlen=len-sizeof(struct eigrp_common_header);

    /* FIXME print other header info */
    printf("\n\tEIGRP v%u, opcode: %s (%u), chksum: 0x%04x, Flags: [%s]\n\tseq: 0x%08x, ack: 0x%08x, AS: %u, length: %u",
           eigrp_com_header->version,
           tok2str(eigrp_opcode_values, "unknown, type: %u",eigrp_com_header->opcode),
           eigrp_com_header->opcode,
           EXTRACT_16BITS(&eigrp_com_header->checksum),
           tok2str(eigrp_common_header_flag_values,
                   "none",
                   EXTRACT_32BITS(&eigrp_com_header->flags)),
           EXTRACT_32BITS(&eigrp_com_header->seq),
           EXTRACT_32BITS(&eigrp_com_header->ack),
           EXTRACT_32BITS(&eigrp_com_header->asn),
           tlen);

    tptr+=sizeof(const struct eigrp_common_header);

    while(tlen>0) {
        /* did we capture enough for fully decoding the object header ? */
        TCHECK2(*tptr, sizeof(struct eigrp_tlv_header));

        eigrp_tlv_header = (const struct eigrp_tlv_header *)tptr;
        eigrp_tlv_len=EXTRACT_16BITS(&eigrp_tlv_header->length);
        eigrp_tlv_type=EXTRACT_16BITS(&eigrp_tlv_header->type);


        if (eigrp_tlv_len < sizeof(struct eigrp_tlv_header) ||
            eigrp_tlv_len > tlen) {
            print_unknown_data(tptr+sizeof(struct eigrp_tlv_header),"\n\t    ",tlen);
            return;
        }

        printf("\n\t  %s TLV (0x%04x), length: %u",
               tok2str(eigrp_tlv_values,
                       "Unknown",
                       eigrp_tlv_type),
               eigrp_tlv_type,
               eigrp_tlv_len);

        tlv_tptr=tptr+sizeof(struct eigrp_tlv_header);
        tlv_tlen=eigrp_tlv_len-sizeof(struct eigrp_tlv_header);

        /* did we capture enough for fully decoding the object ? */
        TCHECK2(*tptr, eigrp_tlv_len);

        switch(eigrp_tlv_type) {

        case EIGRP_TLV_GENERAL_PARM:
            tlv_ptr.eigrp_tlv_general_parm = (const struct eigrp_tlv_general_parm_t *)tlv_tptr;

            printf("\n\t    holdtime: %us, k1 %u, k2 %u, k3 %u, k4 %u, k5 %u",
                   EXTRACT_16BITS(tlv_ptr.eigrp_tlv_general_parm->holdtime),
                   tlv_ptr.eigrp_tlv_general_parm->k1,
                   tlv_ptr.eigrp_tlv_general_parm->k2,
                   tlv_ptr.eigrp_tlv_general_parm->k3,
                   tlv_ptr.eigrp_tlv_general_parm->k4,
                   tlv_ptr.eigrp_tlv_general_parm->k5);
            break;

        case EIGRP_TLV_SW_VERSION:
            tlv_ptr.eigrp_tlv_sw_version = (const struct eigrp_tlv_sw_version_t *)tlv_tptr;

            printf("\n\t    IOS version: %u.%u, EIGRP version %u.%u",
                   tlv_ptr.eigrp_tlv_sw_version->ios_major,
                   tlv_ptr.eigrp_tlv_sw_version->ios_minor,
                   tlv_ptr.eigrp_tlv_sw_version->eigrp_major,
                   tlv_ptr.eigrp_tlv_sw_version->eigrp_minor);
            break;

        case EIGRP_TLV_IP_INT:
            tlv_ptr.eigrp_tlv_ip_int = (const struct eigrp_tlv_ip_int_t *)tlv_tptr;

            bit_length = tlv_ptr.eigrp_tlv_ip_int->plen;
            if (bit_length > 32) {
                printf("\n\t    illegal prefix length %u",bit_length);
                break;
            }
            byte_length = (bit_length + 7) / 8; /* variable length encoding */
            memset(prefix, 0, 4);
            memcpy(prefix,&tlv_ptr.eigrp_tlv_ip_int->destination,byte_length);

            printf("\n\t    IPv4 prefix: %15s/%u, nexthop: ",
                   ipaddr_string(prefix),
                   bit_length);
            if (EXTRACT_32BITS(&tlv_ptr.eigrp_tlv_ip_int->nexthop) == 0)
                printf("self");
            else
                printf("%s",ipaddr_string(&tlv_ptr.eigrp_tlv_ip_int->nexthop));

            printf("\n\t      delay %u ms, bandwidth %u Kbps, mtu %u, hop %u, reliability %u, load %u",
                   (EXTRACT_32BITS(&tlv_ptr.eigrp_tlv_ip_int->delay)/100),
                   EXTRACT_32BITS(&tlv_ptr.eigrp_tlv_ip_int->bandwidth),
                   EXTRACT_24BITS(&tlv_ptr.eigrp_tlv_ip_int->mtu),
                   tlv_ptr.eigrp_tlv_ip_int->hopcount,
                   tlv_ptr.eigrp_tlv_ip_int->reliability,
                   tlv_ptr.eigrp_tlv_ip_int->load);
            break;

        case EIGRP_TLV_IP_EXT:
            tlv_ptr.eigrp_tlv_ip_ext = (const struct eigrp_tlv_ip_ext_t *)tlv_tptr;

            bit_length = tlv_ptr.eigrp_tlv_ip_ext->plen;
            if (bit_length > 32) {
                printf("\n\t    illegal prefix length %u",bit_length);
                break;
            }
            byte_length = (bit_length + 7) / 8; /* variable length encoding */
            memset(prefix, 0, 4);
            memcpy(prefix,&tlv_ptr.eigrp_tlv_ip_ext->destination,byte_length);

            printf("\n\t    IPv4 prefix: %15s/%u, nexthop: ",
                   ipaddr_string(prefix),
                   bit_length);
            if (EXTRACT_32BITS(&tlv_ptr.eigrp_tlv_ip_ext->nexthop) == 0)
                printf("self");
            else
                printf("%s",ipaddr_string(&tlv_ptr.eigrp_tlv_ip_ext->nexthop));

            printf("\n\t      origin-router %s, origin-as %u, origin-proto %s, flags [0x%02x], tag 0x%08x, metric %u",
                   ipaddr_string(tlv_ptr.eigrp_tlv_ip_ext->origin_router),
                   EXTRACT_32BITS(tlv_ptr.eigrp_tlv_ip_ext->origin_as),
                   tok2str(eigrp_ext_proto_id_values,"unknown",tlv_ptr.eigrp_tlv_ip_ext->proto_id),
                   tlv_ptr.eigrp_tlv_ip_ext->flags,
                   EXTRACT_32BITS(tlv_ptr.eigrp_tlv_ip_ext->tag),
                   EXTRACT_32BITS(tlv_ptr.eigrp_tlv_ip_ext->metric));

            printf("\n\t      delay %u ms, bandwidth %u Kbps, mtu %u, hop %u, reliability %u, load %u",
                   (EXTRACT_32BITS(&tlv_ptr.eigrp_tlv_ip_ext->delay)/100),
                   EXTRACT_32BITS(&tlv_ptr.eigrp_tlv_ip_ext->bandwidth),
                   EXTRACT_24BITS(&tlv_ptr.eigrp_tlv_ip_ext->mtu),
                   tlv_ptr.eigrp_tlv_ip_ext->hopcount,
                   tlv_ptr.eigrp_tlv_ip_ext->reliability,
                   tlv_ptr.eigrp_tlv_ip_ext->load);
            break;

        case EIGRP_TLV_AT_CABLE_SETUP:
            tlv_ptr.eigrp_tlv_at_cable_setup = (const struct eigrp_tlv_at_cable_setup_t *)tlv_tptr;

            printf("\n\t    Cable-range: %u-%u, Router-ID %u",
                   EXTRACT_16BITS(&tlv_ptr.eigrp_tlv_at_cable_setup->cable_start),
                   EXTRACT_16BITS(&tlv_ptr.eigrp_tlv_at_cable_setup->cable_end),
                   EXTRACT_32BITS(&tlv_ptr.eigrp_tlv_at_cable_setup->router_id));
            break;

        case EIGRP_TLV_AT_INT:
            tlv_ptr.eigrp_tlv_at_int = (const struct eigrp_tlv_at_int_t *)tlv_tptr;

            printf("\n\t     Cable-Range: %u-%u, nexthop: ",
                   EXTRACT_16BITS(&tlv_ptr.eigrp_tlv_at_int->cable_start),
                   EXTRACT_16BITS(&tlv_ptr.eigrp_tlv_at_int->cable_end));

            if (EXTRACT_32BITS(&tlv_ptr.eigrp_tlv_at_int->nexthop) == 0)
                printf("self");
            else
                printf("%u.%u",
                       EXTRACT_16BITS(&tlv_ptr.eigrp_tlv_at_int->nexthop),
                       EXTRACT_16BITS(&tlv_ptr.eigrp_tlv_at_int->nexthop[2]));

            printf("\n\t      delay %u ms, bandwidth %u Kbps, mtu %u, hop %u, reliability %u, load %u",
                   (EXTRACT_32BITS(&tlv_ptr.eigrp_tlv_at_int->delay)/100),
                   EXTRACT_32BITS(&tlv_ptr.eigrp_tlv_at_int->bandwidth),
                   EXTRACT_24BITS(&tlv_ptr.eigrp_tlv_at_int->mtu),
                   tlv_ptr.eigrp_tlv_at_int->hopcount,
                   tlv_ptr.eigrp_tlv_at_int->reliability,
                   tlv_ptr.eigrp_tlv_at_int->load);
            break;

        case EIGRP_TLV_AT_EXT:
            tlv_ptr.eigrp_tlv_at_ext = (const struct eigrp_tlv_at_ext_t *)tlv_tptr;

            printf("\n\t     Cable-Range: %u-%u, nexthop: ",
                   EXTRACT_16BITS(&tlv_ptr.eigrp_tlv_at_ext->cable_start),
                   EXTRACT_16BITS(&tlv_ptr.eigrp_tlv_at_ext->cable_end));

            if (EXTRACT_32BITS(&tlv_ptr.eigrp_tlv_at_ext->nexthop) == 0)
                printf("self");
            else
                printf("%u.%u",
                       EXTRACT_16BITS(&tlv_ptr.eigrp_tlv_at_ext->nexthop),
                       EXTRACT_16BITS(&tlv_ptr.eigrp_tlv_at_ext->nexthop[2]));

            printf("\n\t      origin-router %u, origin-as %u, origin-proto %s, flags [0x%02x], tag 0x%08x, metric %u",
                   EXTRACT_32BITS(tlv_ptr.eigrp_tlv_at_ext->origin_router),
                   EXTRACT_32BITS(tlv_ptr.eigrp_tlv_at_ext->origin_as),
                   tok2str(eigrp_ext_proto_id_values,"unknown",tlv_ptr.eigrp_tlv_at_ext->proto_id),
                   tlv_ptr.eigrp_tlv_at_ext->flags,
                   EXTRACT_32BITS(tlv_ptr.eigrp_tlv_at_ext->tag),
                   EXTRACT_16BITS(tlv_ptr.eigrp_tlv_at_ext->metric));

            printf("\n\t      delay %u ms, bandwidth %u Kbps, mtu %u, hop %u, reliability %u, load %u",
                   (EXTRACT_32BITS(&tlv_ptr.eigrp_tlv_at_ext->delay)/100),
                   EXTRACT_32BITS(&tlv_ptr.eigrp_tlv_at_ext->bandwidth),
                   EXTRACT_24BITS(&tlv_ptr.eigrp_tlv_at_ext->mtu),
                   tlv_ptr.eigrp_tlv_at_ext->hopcount,
                   tlv_ptr.eigrp_tlv_at_ext->reliability,
                   tlv_ptr.eigrp_tlv_at_ext->load);
            break;

            /*
             * FIXME those are the defined TLVs that lack a decoder
             * you are welcome to contribute code ;-)
             */

        case EIGRP_TLV_AUTH:
        case EIGRP_TLV_SEQ:
        case EIGRP_TLV_MCAST_SEQ:
        case EIGRP_TLV_IPX_INT:
        case EIGRP_TLV_IPX_EXT:

        default:
            if (vflag <= 1)
                print_unknown_data(tlv_tptr,"\n\t    ",tlv_tlen);
            break;
        }
        /* do we want to see an additionally hexdump ? */
        if (vflag > 1)
            print_unknown_data(tptr+sizeof(struct eigrp_tlv_header),"\n\t    ",
                               eigrp_tlv_len-sizeof(struct eigrp_tlv_header));

        tptr+=eigrp_tlv_len;
        tlen-=eigrp_tlv_len;
    }
    return;
trunc:
    printf("\n\t\t packet exceeded snapshot");
}
