/*
 * Copyright (c) 1998-2005 The TCPDUMP project
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
 *
 * support for the IEEE "slow protocols" LACP, MARKER as per 802.3ad
 *
 * Original code by Hannes Gredler (hannes@juniper.net)
 */

#ifndef lint
static const char rcsid[] _U_ =
    "@(#) $Header: /tcpdump/master/tcpdump/print-slow.c,v 1.1.2.1 2005/07/10 14:47:56 hannes Exp $";
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
#include "ether.h"

struct slow_common_header {
    u_int8_t proto_subtype;
    u_int8_t version;
};

#define	SLOW_PROTO_LACP                     1
#define	SLOW_PROTO_MARKER                   2

#define	LACP_VERSION                        1
#define	MARKER_VERSION                      1

static const struct tok slow_proto_values[] = {
    { SLOW_PROTO_LACP, "LACP" },
    { SLOW_PROTO_MARKER, "MARKER" },
    { 0, NULL}
};

struct tlv_header_t {
    u_int8_t type;
    u_int8_t length;
};

#define LACP_TLV_TERMINATOR     0x00
#define LACP_TLV_ACTOR_INFO     0x01
#define LACP_TLV_PARTNER_INFO   0x02
#define LACP_TLV_COLLECTOR_INFO 0x03

#define MARKER_TLV_TERMINATOR   0x00
#define MARKER_TLV_MARKER_INFO  0x01

static const struct tok slow_tlv_values[] = {
    { (SLOW_PROTO_LACP << 8) + LACP_TLV_TERMINATOR, "Terminator"},
    { (SLOW_PROTO_LACP << 8) + LACP_TLV_ACTOR_INFO, "Actor Information"},
    { (SLOW_PROTO_LACP << 8) + LACP_TLV_PARTNER_INFO, "Partner Information"},
    { (SLOW_PROTO_LACP << 8) + LACP_TLV_COLLECTOR_INFO, "Collector Information"},

    { (SLOW_PROTO_MARKER << 8) + MARKER_TLV_TERMINATOR, "Terminator"},
    { (SLOW_PROTO_MARKER << 8) + MARKER_TLV_MARKER_INFO, "Marker Information"},
    { 0, NULL}
};

struct lacp_tlv_actor_partner_info_t {
    u_int8_t sys_pri[2];
    u_int8_t sys[ETHER_ADDR_LEN];
    u_int8_t key[2];
    u_int8_t port_pri[2];
    u_int8_t port[2];
    u_int8_t state;
    u_int8_t pad[3];
};          

static const struct tok lacp_tlv_actor_partner_info_state_values[] = {
    { 0x01, "Activity"},
    { 0x02, "Timeout"},
    { 0x04, "Aggregation"},
    { 0x08, "Synchronization"},
    { 0x10, "Collecting"},
    { 0x20, "Distributing"},
    { 0x40, "Default"},
    { 0x80, "Expired"},
    { 0, NULL}
};

struct lacp_tlv_collector_info_t {
    u_int8_t max_delay[2];
    u_int8_t pad[12];
}; 

struct marker_tlv_marker_info_t {
    u_int8_t req_port[2];
    u_int8_t req_sys[ETHER_ADDR_LEN];
    u_int8_t req_trans_id[4];
    u_int8_t pad[2];
}; 

struct lacp_marker_tlv_terminator_t {
    u_int8_t pad[50];
}; 

void
slow_print(register const u_char *pptr, register u_int len) {

    const struct slow_common_header *slow_com_header;
    const struct tlv_header_t *tlv_header;
    const u_char *tptr,*tlv_tptr;
    u_int tlv_len,tlen,tlv_tlen;

    union {
        const struct lacp_marker_tlv_terminator_t *lacp_marker_tlv_terminator;
        const struct lacp_tlv_actor_partner_info_t *lacp_tlv_actor_partner_info;
        const struct lacp_tlv_collector_info_t *lacp_tlv_collector_info;
        const struct marker_tlv_marker_info_t *marker_tlv_marker_info;
    } tlv_ptr;

    tptr=pptr;
    slow_com_header = (const struct slow_common_header *)pptr;
    TCHECK(*slow_com_header);

    /*
     * Sanity checking of the header.
     */
    if (slow_com_header->proto_subtype == SLOW_PROTO_LACP &&
        slow_com_header->version != LACP_VERSION) {
	printf("LACP version %u packet not supported",slow_com_header->version);
	return;
    }
    if (slow_com_header->proto_subtype == SLOW_PROTO_MARKER &&
        slow_com_header->version != MARKER_VERSION) {
	printf("MARKER version %u packet not supported",slow_com_header->version);
	return;
    }

    printf("%sv%u, length: %u",
           tok2str(slow_proto_values, "unknown (%u)",slow_com_header->proto_subtype),
           slow_com_header->version,
           len);

    if (!vflag)
        return;

    /* ok they seem to want to know everything - lets fully decode it */
    tlen=len-sizeof(struct slow_common_header);
    tptr+=sizeof(const struct slow_common_header);

    while(tlen>0) {
        /* did we capture enough for fully decoding the tlv header ? */
        TCHECK2(*tptr, sizeof(struct tlv_header_t));
        tlv_header = (const struct tlv_header_t *)tptr;
        tlv_len = tlv_header->length;

        printf("\n\t%s TLV (0x%02x), length: %u",
               tok2str(slow_tlv_values,
                       "Unknown",
                       (slow_com_header->proto_subtype << 8) + tlv_header->type),
               tlv_header->type,
               tlv_len);

        if ((tlv_len < sizeof(struct tlv_header_t) ||
            tlv_len > tlen) &&
            tlv_header->type != LACP_TLV_TERMINATOR &&
            tlv_header->type != MARKER_TLV_TERMINATOR) {
            printf("\n\t-----trailing data-----");
            print_unknown_data(tptr+sizeof(sizeof(struct tlv_header_t)),"\n\t  ",tlen);
            return;
        }

        tlv_tptr=tptr+sizeof(struct tlv_header_t);
        tlv_tlen=tlv_len-sizeof(struct tlv_header_t);

        /* did we capture enough for fully decoding the tlv ? */
        TCHECK2(*tptr, tlv_len);

        switch((slow_com_header->proto_subtype << 8) + tlv_header->type) {

            /* those two TLVs have the same structure -> fall through */
        case ((SLOW_PROTO_LACP << 8) + LACP_TLV_ACTOR_INFO):
        case ((SLOW_PROTO_LACP << 8) + LACP_TLV_PARTNER_INFO):
            tlv_ptr.lacp_tlv_actor_partner_info = (const struct lacp_tlv_actor_partner_info_t *)tlv_tptr;

            printf("\n\t  System %s, System Priority %u, Key %u" \
                   ", Port %u, Port Priority %u\n\t  State Flags [%s]",
                   etheraddr_string(tlv_ptr.lacp_tlv_actor_partner_info->sys),
                   EXTRACT_16BITS(tlv_ptr.lacp_tlv_actor_partner_info->sys_pri),
                   EXTRACT_16BITS(tlv_ptr.lacp_tlv_actor_partner_info->key),
                   EXTRACT_16BITS(tlv_ptr.lacp_tlv_actor_partner_info->port),
                   EXTRACT_16BITS(tlv_ptr.lacp_tlv_actor_partner_info->port_pri),
                   bittok2str(lacp_tlv_actor_partner_info_state_values,
                              "none",
                              tlv_ptr.lacp_tlv_actor_partner_info->state));

            break;

        case ((SLOW_PROTO_LACP << 8) + LACP_TLV_COLLECTOR_INFO):
            tlv_ptr.lacp_tlv_collector_info = (const struct lacp_tlv_collector_info_t *)tlv_tptr;

            printf("\n\t  Max Delay %u",
                   EXTRACT_16BITS(tlv_ptr.lacp_tlv_collector_info->max_delay));

            break;

        case ((SLOW_PROTO_MARKER << 8) + MARKER_TLV_MARKER_INFO):
            tlv_ptr.marker_tlv_marker_info = (const struct marker_tlv_marker_info_t *)tlv_tptr;

            printf("\n\t  Request System %s, Request Port %u, Request Transaction ID 0x%08x",
                   etheraddr_string(tlv_ptr.marker_tlv_marker_info->req_sys),
                   EXTRACT_16BITS(tlv_ptr.marker_tlv_marker_info->req_port),
                   EXTRACT_32BITS(tlv_ptr.marker_tlv_marker_info->req_trans_id));

            break;

            /* those two TLVs have the same structure -> fall through */
        case ((SLOW_PROTO_LACP << 8) + LACP_TLV_TERMINATOR):
        case ((SLOW_PROTO_MARKER << 8) + LACP_TLV_TERMINATOR):
            tlv_ptr.lacp_marker_tlv_terminator = (const struct lacp_marker_tlv_terminator_t *)tlv_tptr;
            if (tlv_len == 0) {
                tlv_len = sizeof(tlv_ptr.lacp_marker_tlv_terminator->pad) +
                    sizeof(struct tlv_header_t);
                /* tell the user that we modified the length field  */
                if (vflag>1)
                    printf(" (=%u)",tlv_len);
                /* we have messed around with the length field - now we need to check
                 * again if there are enough bytes on the wire for the hexdump */
                TCHECK2(tlv_ptr.lacp_marker_tlv_terminator->pad[0],
                        sizeof(tlv_ptr.lacp_marker_tlv_terminator->pad));
            }

            break;

        default:
            if (vflag <= 1)
                print_unknown_data(tlv_tptr,"\n\t  ",tlv_tlen);
            break;
        }
        /* do we want to see an additionally hexdump ? */
        if (vflag > 1)
            print_unknown_data(tptr+sizeof(sizeof(struct tlv_header_t)),"\n\t  ",
                               tlv_len-sizeof(struct tlv_header_t));

        tptr+=tlv_len;
        tlen-=tlv_len;
    }
    return;
trunc:
    printf("\n\t\t packet exceeded snapshot");
}
