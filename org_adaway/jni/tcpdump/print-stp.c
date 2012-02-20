/*
 * Copyright (c) 2000 Lennert Buytenhek
 *
 * This software may be distributed either under the terms of the
 * BSD-style license that accompanies tcpdump or the GNU General
 * Public License
 *
 * Format and print IEEE 802.1d spanning tree protocol packets.
 * Contributed by Lennert Buytenhek <buytenh@gnu.org>
 */

#ifndef lint
static const char rcsid[] _U_ =
"@(#) $Header: /tcpdump/master/tcpdump/print-stp.c,v 1.13.2.7 2007/03/18 17:12:36 hannes Exp $";
#endif

#ifdef HAVE_CONFIG_H
#include "config.h"
#endif

#include <tcpdump-stdinc.h>

#include <stdlib.h>
#include <stdio.h>
#include <string.h>

#include "interface.h"
#include "addrtoname.h"
#include "extract.h"

#define	RSTP_EXTRACT_PORT_ROLE(x) (((x)&0x0C)>>2) 
/* STP timers are expressed in multiples of 1/256th second */
#define STP_TIME_BASE 256
#define STP_BPDU_MSTP_MIN_LEN 102

struct stp_bpdu_ {
    u_int8_t protocol_id[2];
    u_int8_t protocol_version;
    u_int8_t bpdu_type;
    u_int8_t flags;
    u_int8_t root_id[8];
    u_int8_t root_path_cost[4];
    u_int8_t bridge_id[8];
    u_int8_t port_id[2];
    u_int8_t message_age[2];
    u_int8_t max_age[2];
    u_int8_t hello_time[2];
    u_int8_t forward_delay[2];
    u_int8_t v1_length;
};

#define STP_PROTO_REGULAR 0x00
#define STP_PROTO_RAPID   0x02
#define STP_PROTO_MSTP    0x03

struct tok stp_proto_values[] = {
    { STP_PROTO_REGULAR, "802.1d" },
    { STP_PROTO_RAPID, "802.1w" },
    { STP_PROTO_MSTP, "802.1s" },
    { 0, NULL}
};

#define STP_BPDU_TYPE_CONFIG      0x00
#define STP_BPDU_TYPE_RSTP        0x02
#define STP_BPDU_TYPE_TOPO_CHANGE 0x80

struct tok stp_bpdu_flag_values[] = {
    { 0x01, "Topology change" },
    { 0x02, "Proposal" },
    { 0x10, "Learn" },
    { 0x20, "Forward" },
    { 0x40, "Agreement" },
    { 0x80, "Topology change ACK" },
    { 0, NULL}
};

struct tok stp_bpdu_type_values[] = {
    { STP_BPDU_TYPE_CONFIG, "Config" },
    { STP_BPDU_TYPE_RSTP, "Rapid STP" },
    { STP_BPDU_TYPE_TOPO_CHANGE, "Topology Change" },
    { 0, NULL}
};

struct tok rstp_obj_port_role_values[] = {
    { 0x00, "Unknown" },
    { 0x01, "Alternate" },
    { 0x02, "Root" },
    { 0x03, "Designated" },
    { 0, NULL}
};

static char *
stp_print_bridge_id(const u_char *p)
{
    static char bridge_id_str[sizeof("pppp.aa:bb:cc:dd:ee:ff")];

    snprintf(bridge_id_str, sizeof(bridge_id_str),
             "%.2x%.2x.%.2x:%.2x:%.2x:%.2x:%.2x:%.2x",
             p[0], p[1], p[2], p[3], p[4], p[5], p[6], p[7]);

    return bridge_id_str;
}

static void
stp_print_config_bpdu(const struct stp_bpdu_ *stp_bpdu, u_int length)
{
    printf(", Flags [%s]",
           bittok2str(stp_bpdu_flag_values, "none", stp_bpdu->flags));

    printf(", bridge-id %s.%04x, length %u",
           stp_print_bridge_id((const u_char *)&stp_bpdu->bridge_id),
           EXTRACT_16BITS(&stp_bpdu->port_id), length);

    /* in non-verbose mode just print the bridge-id */
    if (!vflag) {
        return;
    }

    printf("\n\tmessage-age %.2fs, max-age %.2fs"
           ", hello-time %.2fs, forwarding-delay %.2fs",
           (float)EXTRACT_16BITS(&stp_bpdu->message_age) / STP_TIME_BASE,
           (float)EXTRACT_16BITS(&stp_bpdu->max_age) / STP_TIME_BASE,
           (float)EXTRACT_16BITS(&stp_bpdu->hello_time) / STP_TIME_BASE,
           (float)EXTRACT_16BITS(&stp_bpdu->forward_delay) / STP_TIME_BASE);

    printf("\n\troot-id %s, root-pathcost %u",
           stp_print_bridge_id((const u_char *)&stp_bpdu->root_id),
           EXTRACT_32BITS(&stp_bpdu->root_path_cost));

    /* Port role is only valid for 802.1w */
    if (stp_bpdu->protocol_version == STP_PROTO_RAPID) {
        printf(", port-role %s",
               tok2str(rstp_obj_port_role_values, "Unknown",
                       RSTP_EXTRACT_PORT_ROLE(stp_bpdu->flags)));
    }
}

/*
 * MSTP packet format
 * Ref. IEEE 802.1Q 2003 Ed. Section 14
 *
 * MSTP BPDU
 *
 * 2 -  bytes Protocol Id
 * 1 -  byte  Protocol Ver. 
 * 1 -  byte  BPDU tye
 * 1 -  byte  Flags
 * 8 -  bytes CIST Root Identifier
 * 4 -  bytes CIST External Path Cost
 * 8 -  bytes CIST Regional Root Identifier
 * 2 -  bytes CIST Port Identifier
 * 2 -  bytes Message Age
 * 2 -  bytes Max age
 * 2 -  bytes Hello Time
 * 2 -  bytes Forward delay
 * 1 -  byte  Version 1 length. Must be 0
 * 2 -  bytes Version 3 length
 * 1 -  byte  Config Identifier
 * 32 - bytes Config Name
 * 2 -  bytes Revision level
 * 16 - bytes Config Digest [MD5]
 * 4 -  bytes CIST Internal Root Path Cost
 * 8 -  bytes CIST Bridge Identifier
 * 1 -  byte  CIST Remaining Hops
 * 16 - bytes MSTI information [Max 64 MSTI, each 16 bytes]
 *
 * MSTI Payload
 *
 * 1 - byte  MSTI flag
 * 8 - bytes MSTI Regional Root Identifier
 * 4 - bytes MSTI Regional Path Cost
 * 1 - byte  MSTI Bridge Priority
 * 1 - byte  MSTI Port Priority
 * 1 - byte  MSTI Remaining Hops
 */

#define MST_BPDU_MSTI_LENGTH		    16
#define MST_BPDU_CONFIG_INFO_LENGTH	    64

/* Offsets of fields from the begginning for the packet */
#define MST_BPDU_VER3_LEN_OFFSET	    36
#define MST_BPDU_CONFIG_NAME_OFFSET	    39
#define MST_BPDU_CONFIG_DIGEST_OFFSET	    73
#define MST_BPDU_CIST_INT_PATH_COST_OFFSET  89
#define MST_BPDU_CIST_BRIDGE_ID_OFFSET	    93
#define MST_BPDU_CIST_REMAIN_HOPS_OFFSET    101
#define MST_BPDU_MSTI_OFFSET		    102
/* Offsets within  an MSTI */
#define MST_BPDU_MSTI_ROOT_PRIO_OFFSET	    1
#define MST_BPDU_MSTI_ROOT_PATH_COST_OFFSET 9
#define MST_BPDU_MSTI_BRIDGE_PRIO_OFFSET    13
#define MST_BPDU_MSTI_PORT_PRIO_OFFSET	    14
#define MST_BPDU_MSTI_REMAIN_HOPS_OFFSET    15

static void
stp_print_mstp_bpdu(const struct stp_bpdu_ *stp_bpdu, u_int length)
{
    const u_char    *ptr;
    u_int16_t	    v3len;
    u_int16_t	    len;
    u_int16_t	    msti;
    u_int16_t	    offset;

    ptr = (const u_char *)stp_bpdu;
    printf(", CIST Flags [%s]",
           bittok2str(stp_bpdu_flag_values, "none", stp_bpdu->flags));

    /*
     * in non-verbose mode just print the flags. We dont read that much
     * of the packet (DEFAULT_SNAPLEN) to print out cist bridge-id
     */
    if (!vflag) {
        return;
    }

    printf(", CIST bridge-id %s.%04x, length %u",
           stp_print_bridge_id(ptr + MST_BPDU_CIST_BRIDGE_ID_OFFSET),
           EXTRACT_16BITS(&stp_bpdu->port_id), length);


    printf("\n\tmessage-age %.2fs, max-age %.2fs"
           ", hello-time %.2fs, forwarding-delay %.2fs",
           (float)EXTRACT_16BITS(&stp_bpdu->message_age) / STP_TIME_BASE,
           (float)EXTRACT_16BITS(&stp_bpdu->max_age) / STP_TIME_BASE,
           (float)EXTRACT_16BITS(&stp_bpdu->hello_time) / STP_TIME_BASE,
           (float)EXTRACT_16BITS(&stp_bpdu->forward_delay) / STP_TIME_BASE);

    printf("\n\tCIST root-id %s, ext-pathcost %u int-pathcost %u",
           stp_print_bridge_id((const u_char *)&stp_bpdu->root_id),
           EXTRACT_32BITS(&stp_bpdu->root_path_cost),
           EXTRACT_32BITS(ptr + MST_BPDU_CIST_INT_PATH_COST_OFFSET));

    printf(", port-role %s",
           tok2str(rstp_obj_port_role_values, "Unknown",
                   RSTP_EXTRACT_PORT_ROLE(stp_bpdu->flags)));

    printf("\n\tCIST regional-root-id %s",
           stp_print_bridge_id((const u_char *)&stp_bpdu->bridge_id));

    printf("\n\tMSTP Configuration Name %s, revision %u, digest %08x%08x%08x%08x",
           ptr + MST_BPDU_CONFIG_NAME_OFFSET,
	   EXTRACT_16BITS(ptr + MST_BPDU_CONFIG_NAME_OFFSET + 32),
	   EXTRACT_32BITS(ptr + MST_BPDU_CONFIG_DIGEST_OFFSET),
	   EXTRACT_32BITS(ptr + MST_BPDU_CONFIG_DIGEST_OFFSET + 4),
	   EXTRACT_32BITS(ptr + MST_BPDU_CONFIG_DIGEST_OFFSET + 8),
	   EXTRACT_32BITS(ptr + MST_BPDU_CONFIG_DIGEST_OFFSET + 12));

    printf("\n\tCIST remaining-hops %d", ptr[MST_BPDU_CIST_REMAIN_HOPS_OFFSET]);

    /* Dump all MSTI's */
    v3len = EXTRACT_16BITS(ptr + MST_BPDU_VER3_LEN_OFFSET);
    if (v3len > MST_BPDU_CONFIG_INFO_LENGTH) {
        len = v3len - MST_BPDU_CONFIG_INFO_LENGTH;
        offset = MST_BPDU_MSTI_OFFSET;
        while (len >= MST_BPDU_MSTI_LENGTH) {
            msti = EXTRACT_16BITS(ptr + offset +
                                  MST_BPDU_MSTI_ROOT_PRIO_OFFSET);
            msti = msti & 0x0FFF;

            printf("\n\tMSTI %d, Flags [%s], port-role %s", 
                   msti, bittok2str(stp_bpdu_flag_values, "none", ptr[offset]),
                   tok2str(rstp_obj_port_role_values, "Unknown",
                           RSTP_EXTRACT_PORT_ROLE(ptr[offset])));
            printf("\n\t\tMSTI regional-root-id %s, pathcost %u",
                   stp_print_bridge_id(ptr + offset +
                                       MST_BPDU_MSTI_ROOT_PRIO_OFFSET),
                   EXTRACT_32BITS(ptr + offset +
                                  MST_BPDU_MSTI_ROOT_PATH_COST_OFFSET));
            printf("\n\t\tMSTI bridge-prio %d, port-prio %d, hops %d",
                   ptr[offset + MST_BPDU_MSTI_BRIDGE_PRIO_OFFSET] >> 4,
                   ptr[offset + MST_BPDU_MSTI_PORT_PRIO_OFFSET] >> 4,
                   ptr[offset + MST_BPDU_MSTI_REMAIN_HOPS_OFFSET]);

            len -= MST_BPDU_MSTI_LENGTH;
            offset += MST_BPDU_MSTI_LENGTH;
        }
    }
}

/*
 * Print 802.1d / 802.1w / 802.1q (mstp) packets.
 */
void
stp_print(const u_char *p, u_int length)
{
    const struct stp_bpdu_ *stp_bpdu;
    u_int16_t              mstp_len;
    
    stp_bpdu = (struct stp_bpdu_*)p;

    /* Minimum STP Frame size. */
    if (length < 4)
        goto trunc;
        
    if (EXTRACT_16BITS(&stp_bpdu->protocol_id)) {
        printf("unknown STP version, length %u", length);
        return;
    }

    printf("STP %s", tok2str(stp_proto_values, "Unknown STP protocol (0x%02x)",
                         stp_bpdu->protocol_version));

    switch (stp_bpdu->protocol_version) {
    case STP_PROTO_REGULAR:
    case STP_PROTO_RAPID:
    case STP_PROTO_MSTP:
        break;
    default:
        return;
    }

    printf(", %s", tok2str(stp_bpdu_type_values, "Unknown BPDU Type (0x%02x)",
                           stp_bpdu->bpdu_type));

    switch (stp_bpdu->bpdu_type) {
    case STP_BPDU_TYPE_CONFIG:
        if (length < sizeof(struct stp_bpdu_) - 1) {
            goto trunc;
        }
        stp_print_config_bpdu(stp_bpdu, length);
        break;

    case STP_BPDU_TYPE_RSTP:
        if (stp_bpdu->protocol_version == STP_PROTO_RAPID) {
            if (length < sizeof(struct stp_bpdu_)) {
                goto trunc;
            }
            stp_print_config_bpdu(stp_bpdu, length);
        } else if (stp_bpdu->protocol_version == STP_PROTO_MSTP) {
            if (length < STP_BPDU_MSTP_MIN_LEN) {
                goto trunc;
            }
            if (stp_bpdu->v1_length != 0) {
                /* FIX ME: Emit a message here ? */
                goto trunc;
            }
            /* Validate v3 length */
            mstp_len = EXTRACT_16BITS(p + MST_BPDU_VER3_LEN_OFFSET);
            mstp_len += 2;  /* length encoding itself is 2 bytes */
            if (length < (sizeof(struct stp_bpdu_) + mstp_len)) {
                goto trunc;
            }
            stp_print_mstp_bpdu(stp_bpdu, length);
        }
        break;

    case STP_BPDU_TYPE_TOPO_CHANGE:
        /* always empty message - just break out */
        break;

    default:
        break;
    }

    return;
 trunc:
    printf("[|stp %d]", length);
}

/*
 * Local Variables:
 * c-style: whitesmith
 * c-basic-offset: 4
 * End:
 */
