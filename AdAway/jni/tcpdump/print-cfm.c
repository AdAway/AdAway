/*
 * Copyright (c) 1998-2006 The TCPDUMP project
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
 * Support for the IEEE Connectivity Fault Management Protocols as per 802.1ag.
 *
 * Original code by Hannes Gredler (hannes@juniper.net)
 */

#ifndef lint
static const char rcsid[] _U_ =
    "@(#) $Header: /tcpdump/master/tcpdump/print-cfm.c,v 1.5 2007-07-24 16:01:42 hannes Exp $";
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
#include "ether.h"
#include "addrtoname.h"
#include "oui.h"
#include "af.h"

/*
 * Prototypes
 */
const char * cfm_egress_id_string(register const u_char *);
int cfm_mgmt_addr_print(register const u_char *);

struct cfm_common_header_t {
    u_int8_t mdlevel_version;
    u_int8_t opcode;
    u_int8_t flags;
    u_int8_t first_tlv_offset;
};

#define	CFM_VERSION 0
#define CFM_EXTRACT_VERSION(x) (((x)&0x1f))
#define CFM_EXTRACT_MD_LEVEL(x) (((x)&0xe0)>>5)

#define	CFM_OPCODE_CCM 1
#define	CFM_OPCODE_LBR 2
#define	CFM_OPCODE_LBM 3
#define	CFM_OPCODE_LTR 4
#define	CFM_OPCODE_LTM 5

static const struct tok cfm_opcode_values[] = {
    { CFM_OPCODE_CCM, "Continouity Check Message"},
    { CFM_OPCODE_LBR, "Loopback Reply"},
    { CFM_OPCODE_LBM, "Loopback Message"},
    { CFM_OPCODE_LTR, "Linktrace Reply"},
    { CFM_OPCODE_LTM, "Linktrace Message"},
    { 0, NULL}
};

/*
 * Message Formats.
 */
struct cfm_ccm_t {
    u_int8_t sequence[4];
    u_int8_t ma_epi[2];
    u_int8_t md_nameformat;
    u_int8_t md_namelength;
    u_int8_t md_name[46]; /* md name and short ma name */
    u_int8_t reserved_itu[16];
    u_int8_t reserved[6];
};

/*
 * Timer Bases for the CCM Interval field.
 * Expressed in units of seconds.
 */
const float ccm_interval_base[8] = {0, 0.003333, 0.01, 0.1, 1, 10, 60, 600};
#define CCM_INTERVAL_MIN_MULTIPLIER 3.25
#define CCM_INTERVAL_MAX_MULTIPLIER 3.5

#define CFM_CCM_RDI_FLAG 0x80
#define CFM_EXTRACT_CCM_INTERVAL(x) (((x)&0x07))

#define CFM_CCM_MD_FORMAT_8021 0
#define CFM_CCM_MD_FORMAT_NONE 1
#define CFM_CCM_MD_FORMAT_DNS  2
#define CFM_CCM_MD_FORMAT_MAC  3
#define CFM_CCM_MD_FORMAT_CHAR 4

static const struct tok cfm_md_nameformat_values[] = {
    { CFM_CCM_MD_FORMAT_8021, "IEEE 802.1"},
    { CFM_CCM_MD_FORMAT_NONE, "No MD Name present"},
    { CFM_CCM_MD_FORMAT_DNS, "DNS string"},
    { CFM_CCM_MD_FORMAT_MAC, "MAC + 16Bit Integer"},
    { CFM_CCM_MD_FORMAT_CHAR, "Character string"},
    { 0, NULL}
};

#define CFM_CCM_MA_FORMAT_8021 0
#define CFM_CCM_MA_FORMAT_VID  1
#define CFM_CCM_MA_FORMAT_CHAR 2
#define CFM_CCM_MA_FORMAT_INT  3
#define CFM_CCM_MA_FORMAT_VPN  4

static const struct tok cfm_ma_nameformat_values[] = {
    { CFM_CCM_MA_FORMAT_8021, "IEEE 802.1"},
    { CFM_CCM_MA_FORMAT_VID, "Primary VID"},
    { CFM_CCM_MA_FORMAT_CHAR, "Character string"},
    { CFM_CCM_MA_FORMAT_INT, "16Bit Integer"},
    { CFM_CCM_MA_FORMAT_VPN, "RFC2685 VPN-ID"},
    { 0, NULL}
};

struct cfm_lbm_t {
    u_int8_t transaction_id[4];
    u_int8_t reserved[4];
};

struct cfm_ltm_t {
    u_int8_t transaction_id[4];
    u_int8_t egress_id[8];
    u_int8_t ttl;
    u_int8_t original_mac[ETHER_ADDR_LEN];
    u_int8_t target_mac[ETHER_ADDR_LEN];
    u_int8_t reserved[3];
};

static const struct tok cfm_ltm_flag_values[] = {
    { 0x80, "Use Forwarding-DB only"},
    { 0, NULL}
};

struct cfm_ltr_t {
    u_int8_t transaction_id[4];
    u_int8_t last_egress_id[8];
    u_int8_t next_egress_id[8];
    u_int8_t ttl;
    u_int8_t replay_action;
    u_int8_t reserved[6];
};

static const struct tok cfm_ltr_flag_values[] = {
    { 0x80, "Forwarded"},
    { 0x40, "Terminal MEP"},
    { 0, NULL}
};

static const struct tok cfm_ltr_replay_action_values[] = {
    { 1, "Exact Match"},
    { 2, "Filtering DB"},
    { 3, "MIP CCM DB"},
    { 0, NULL}
};


#define CFM_TLV_END 0
#define CFM_TLV_SENDER_ID 1
#define CFM_TLV_PORT_STATUS 2
#define CFM_TLV_INTERFACE_STATUS 3
#define CFM_TLV_DATA 4
#define CFM_TLV_REPLY_INGRESS 5
#define CFM_TLV_REPLY_EGRESS 6
#define CFM_TLV_PRIVATE 31

static const struct tok cfm_tlv_values[] = {
    { CFM_TLV_END, "End"},
    { CFM_TLV_SENDER_ID, "Sender ID"},
    { CFM_TLV_PORT_STATUS, "Port status"},
    { CFM_TLV_INTERFACE_STATUS, "Interface status"},
    { CFM_TLV_DATA, "Data"},
    { CFM_TLV_REPLY_INGRESS, "Reply Ingress"},
    { CFM_TLV_REPLY_EGRESS, "Reply Egress"},
    { CFM_TLV_PRIVATE, "Organization Specific"},
    { 0, NULL}
};

/*
 * TLVs
 */

struct cfm_tlv_header_t {
    u_int8_t type;
    u_int8_t length[2];
};

/* FIXME define TLV formats */

static const struct tok cfm_tlv_port_status_values[] = {
    { 1, "Blocked"},
    { 2, "Up"},
    { 0, NULL}
};

static const struct tok cfm_tlv_interface_status_values[] = {
    { 1, "Up"},
    { 2, "Down"},
    { 3, "Testing"},
    { 5, "Dormant"},
    { 6, "not present"},
    { 7, "lower Layer down"},
    { 0, NULL}
};

#define CFM_CHASSIS_ID_CHASSIS_COMPONENT 1
#define CFM_CHASSIS_ID_INTERFACE_ALIAS 2
#define CFM_CHASSIS_ID_PORT_COMPONENT 3
#define CFM_CHASSIS_ID_MAC_ADDRESS 4
#define CFM_CHASSIS_ID_NETWORK_ADDRESS 5
#define CFM_CHASSIS_ID_INTERFACE_NAME 6
#define CFM_CHASSIS_ID_LOCAL 7

static const struct tok cfm_tlv_senderid_chassisid_values[] = {
    { 0, "Reserved"},
    { CFM_CHASSIS_ID_CHASSIS_COMPONENT, "Chassis component"},
    { CFM_CHASSIS_ID_INTERFACE_ALIAS, "Interface alias"},
    { CFM_CHASSIS_ID_PORT_COMPONENT, "Port component"},
    { CFM_CHASSIS_ID_MAC_ADDRESS, "MAC address"},
    { CFM_CHASSIS_ID_NETWORK_ADDRESS, "Network address"},
    { CFM_CHASSIS_ID_INTERFACE_NAME, "Interface name"},
    { CFM_CHASSIS_ID_LOCAL, "Locally assigned"},
    { 0, NULL}
};


int
cfm_mgmt_addr_print(register const u_char *tptr) {

    u_int mgmt_addr_type;
    u_int hexdump =  FALSE;

    /*
     * Altough AFIs are tpically 2 octects wide,
     * 802.1ab specifies that this field width
     * is only once octet
     */
    mgmt_addr_type = *tptr;
    printf("\n\t  Management Address Type %s (%u)",
           tok2str(af_values, "Unknown", mgmt_addr_type),
           mgmt_addr_type);

    /*
     * Resolve the passed in Address.
     */
    switch(mgmt_addr_type) {
    case AFNUM_INET:
        printf(", %s", ipaddr_string(tptr + 1));
        break;

#ifdef INET6
    case AFNUM_INET6:
        printf(", %s", ip6addr_string(tptr + 1));
        break;
#endif

    default:
        hexdump = TRUE;
        break;
    }

    return hexdump;
}

/*
 * The egress-ID string is a 16-Bit string plus a MAC address.
 */
const char *
cfm_egress_id_string(register const u_char *tptr) {
    static char egress_id_buffer[80];
    
    snprintf(egress_id_buffer, sizeof(egress_id_buffer),
             "MAC 0x%4x-%s",
             EXTRACT_16BITS(tptr),
             etheraddr_string(tptr+2));

    return egress_id_buffer;
}

void
cfm_print(register const u_char *pptr, register u_int length) {

    const struct cfm_common_header_t *cfm_common_header;
    const struct cfm_tlv_header_t *cfm_tlv_header;
    const u_int8_t *tptr, *tlv_ptr, *ma_name, *ma_nameformat, *ma_namelength;
    u_int hexdump, tlen, cfm_tlv_len, cfm_tlv_type, ccm_interval;


    union {
        const struct cfm_ccm_t *cfm_ccm;
        const struct cfm_lbm_t *cfm_lbm;
        const struct cfm_ltm_t *cfm_ltm;
        const struct cfm_ltr_t *cfm_ltr;
    } msg_ptr;

    tptr=pptr;
    cfm_common_header = (const struct cfm_common_header_t *)pptr;
    TCHECK(*cfm_common_header);

    /*
     * Sanity checking of the header.
     */
    if (CFM_EXTRACT_VERSION(cfm_common_header->mdlevel_version) != CFM_VERSION) {
	printf("CFMv%u not supported, length %u",
               CFM_EXTRACT_VERSION(cfm_common_header->mdlevel_version), length);
	return;
    }

    printf("CFMv%u %s, MD Level %u, length %u",
           CFM_EXTRACT_VERSION(cfm_common_header->mdlevel_version),
           tok2str(cfm_opcode_values, "unknown (%u)", cfm_common_header->opcode),
           CFM_EXTRACT_MD_LEVEL(cfm_common_header->mdlevel_version),
           length);

    /*
     * In non-verbose mode just print the opcode and md-level.
     */
    if (vflag < 1) {
        return;
    }

    printf("\n\tFirst TLV offset %u", cfm_common_header->first_tlv_offset);

    tptr += sizeof(const struct cfm_common_header_t);
    tlen = length - sizeof(struct cfm_common_header_t);

    switch (cfm_common_header->opcode) {
    case CFM_OPCODE_CCM:
        msg_ptr.cfm_ccm = (const struct cfm_ccm_t *)tptr;

        ccm_interval = CFM_EXTRACT_CCM_INTERVAL(cfm_common_header->flags);
        printf(", Flags [CCM Interval %u%s]",
               ccm_interval,
               cfm_common_header->flags & CFM_CCM_RDI_FLAG ?
               ", RDI" : "");

        /*
         * Resolve the CCM interval field.
         */
        if (ccm_interval) {
            printf("\n\t  CCM Interval %.3fs"
                   ", min CCM Lifetime %.3fs, max CCM Lifetime %.3fs",
                   ccm_interval_base[ccm_interval],
                   ccm_interval_base[ccm_interval] * CCM_INTERVAL_MIN_MULTIPLIER,
                   ccm_interval_base[ccm_interval] * CCM_INTERVAL_MAX_MULTIPLIER);
        }

        printf("\n\t  Sequence Number 0x%08x, MA-End-Point-ID 0x%04x",
               EXTRACT_32BITS(msg_ptr.cfm_ccm->sequence),
               EXTRACT_16BITS(msg_ptr.cfm_ccm->ma_epi));


        /*
         * Resolve the MD fields.
         */
        printf("\n\t  MD Name Format %s (%u), MD Name length %u",
               tok2str(cfm_md_nameformat_values, "Unknown",
                       msg_ptr.cfm_ccm->md_nameformat),
               msg_ptr.cfm_ccm->md_nameformat,
               msg_ptr.cfm_ccm->md_namelength);

        if (msg_ptr.cfm_ccm->md_nameformat != CFM_CCM_MD_FORMAT_NONE) {
            printf("\n\t  MD Name: ");
            switch (msg_ptr.cfm_ccm->md_nameformat) {
            case CFM_CCM_MD_FORMAT_DNS:
            case CFM_CCM_MD_FORMAT_CHAR:
                safeputs((const char *)msg_ptr.cfm_ccm->md_name, msg_ptr.cfm_ccm->md_namelength);
                break;

            case CFM_CCM_MD_FORMAT_MAC:
                printf("\n\t  MAC %s", etheraddr_string(
                           msg_ptr.cfm_ccm->md_name));
                break;

                /* FIXME add printers for those MD formats - hexdump for now */
            case CFM_CCM_MA_FORMAT_8021:
            default:
                print_unknown_data(msg_ptr.cfm_ccm->md_name, "\n\t    ",
                                   msg_ptr.cfm_ccm->md_namelength);
            }
        }


        /*
         * Resolve the MA fields.
         */
        ma_nameformat = msg_ptr.cfm_ccm->md_name + msg_ptr.cfm_ccm->md_namelength;
        ma_namelength = msg_ptr.cfm_ccm->md_name + msg_ptr.cfm_ccm->md_namelength + 1;
        ma_name = msg_ptr.cfm_ccm->md_name + msg_ptr.cfm_ccm->md_namelength + 2;

        printf("\n\t  MA Name-Format %s (%u), MA name length %u",
               tok2str(cfm_ma_nameformat_values, "Unknown",
                       *ma_nameformat),
               *ma_nameformat,
               *ma_namelength);        

        printf("\n\t  MA Name: ");
        switch (*ma_nameformat) {
        case CFM_CCM_MA_FORMAT_CHAR:
            safeputs((const char *)ma_name, *ma_namelength);
            break;

            /* FIXME add printers for those MA formats - hexdump for now */
        case CFM_CCM_MA_FORMAT_8021:
        case CFM_CCM_MA_FORMAT_VID:
        case CFM_CCM_MA_FORMAT_INT:
        case CFM_CCM_MA_FORMAT_VPN:
        default:
            print_unknown_data(ma_name, "\n\t    ", *ma_namelength);
        }
        break;

    case CFM_OPCODE_LTM:
        msg_ptr.cfm_ltm = (const struct cfm_ltm_t *)tptr;

        printf(", Flags [%s]",
               bittok2str(cfm_ltm_flag_values, "none",  cfm_common_header->flags));

        printf("\n\t  Transaction-ID 0x%08x, Egress-ID %s, ttl %u",
               EXTRACT_32BITS(msg_ptr.cfm_ltm->transaction_id),
               cfm_egress_id_string(msg_ptr.cfm_ltm->egress_id),
               msg_ptr.cfm_ltm->ttl);

        printf("\n\t  Original-MAC %s, Target-MAC %s",
               etheraddr_string(msg_ptr.cfm_ltm->original_mac),
               etheraddr_string(msg_ptr.cfm_ltm->target_mac));
        break;

    case CFM_OPCODE_LTR:
        msg_ptr.cfm_ltr = (const struct cfm_ltr_t *)tptr;

        printf(", Flags [%s]",
               bittok2str(cfm_ltr_flag_values, "none",  cfm_common_header->flags));

        printf("\n\t  Transaction-ID 0x%08x, Last-Egress-ID %s",
               EXTRACT_32BITS(msg_ptr.cfm_ltr->transaction_id),
               cfm_egress_id_string(msg_ptr.cfm_ltr->last_egress_id));

        printf("\n\t  Next-Egress-ID %s, ttl %u",
               cfm_egress_id_string(msg_ptr.cfm_ltr->next_egress_id),
               msg_ptr.cfm_ltr->ttl);

        printf("\n\t  Replay-Action %s (%u)",
               tok2str(cfm_ltr_replay_action_values,
                       "Unknown",
                       msg_ptr.cfm_ltr->replay_action),
               msg_ptr.cfm_ltr->replay_action);
        break;

        /*
         * No message decoder yet.
         * Hexdump everything up until the start of the TLVs
         */
    case CFM_OPCODE_LBR:
    case CFM_OPCODE_LBM:
    default:
        if (tlen > cfm_common_header->first_tlv_offset) {
            print_unknown_data(tptr, "\n\t  ",
                               tlen -  cfm_common_header->first_tlv_offset);
        }
        break;
    }

    /*
     * Sanity check for not walking off.
     */
    if (tlen <= cfm_common_header->first_tlv_offset) {
        return;
    }

    tptr += cfm_common_header->first_tlv_offset;
    tlen -= cfm_common_header->first_tlv_offset;
    
    while (tlen > 0) {
        cfm_tlv_header = (const struct cfm_tlv_header_t *)tptr;

        /* Enough to read the tlv type ? */
        TCHECK2(*tptr, 1);
        cfm_tlv_type=cfm_tlv_header->type;

        if (cfm_tlv_type != CFM_TLV_END) {
            /* did we capture enough for fully decoding the object header ? */
            TCHECK2(*tptr, sizeof(struct cfm_tlv_header_t));            
            cfm_tlv_len=EXTRACT_16BITS(&cfm_tlv_header->length);
        } else {
            cfm_tlv_len = 0;
        }

        printf("\n\t%s TLV (0x%02x), length %u",
               tok2str(cfm_tlv_values, "Unknown", cfm_tlv_type),
               cfm_tlv_type,
               cfm_tlv_len);

        /* sanity check for not walking off and infinite loop check. */
        if ((cfm_tlv_type != CFM_TLV_END) &&
            ((cfm_tlv_len + sizeof(struct cfm_tlv_header_t) > tlen) ||
             (!cfm_tlv_len))) {
            print_unknown_data(tptr,"\n\t  ",tlen);
            return;
        }

        tptr += sizeof(struct cfm_tlv_header_t);
        tlen -= sizeof(struct cfm_tlv_header_t);
        tlv_ptr = tptr;

        /* did we capture enough for fully decoding the object ? */
        if (cfm_tlv_type != CFM_TLV_END) {
            TCHECK2(*tptr, cfm_tlv_len);
        }
        hexdump = FALSE;

        switch(cfm_tlv_type) {
        case CFM_TLV_END:
            /* we are done - bail out */
            return;

        case CFM_TLV_PORT_STATUS:
            printf(", Status: %s (%u)",
                   tok2str(cfm_tlv_port_status_values, "Unknown", *tptr),
                   *tptr);
            break;

        case CFM_TLV_INTERFACE_STATUS:
            printf(", Status: %s (%u)",
                   tok2str(cfm_tlv_interface_status_values, "Unknown", *tptr),
                   *tptr);
            break;

        case CFM_TLV_PRIVATE:
            printf(", Vendor: %s (%u), Sub-Type %u",
                   tok2str(oui_values,"Unknown", EXTRACT_24BITS(tptr)),
                   EXTRACT_24BITS(tptr),
                   *(tptr+3));
            hexdump = TRUE;
            break;

        case CFM_TLV_SENDER_ID:
        {
            u_int chassis_id_type, chassis_id_length;
            u_int mgmt_addr_length;

            /*
             * Check if there is a Chassis-ID.
             */
            chassis_id_length = *tptr;
            if (chassis_id_length > tlen) {
                hexdump = TRUE;
                break;
            }

            tptr++;
            tlen--;

            if (chassis_id_length) {
                chassis_id_type = *tptr;
                printf("\n\t  Chassis-ID Type %s (%u), Chassis-ID length %u",
                       tok2str(cfm_tlv_senderid_chassisid_values,
                               "Unknown",
                               chassis_id_type),
                       chassis_id_type,
                       chassis_id_length);

                switch (chassis_id_type) {
                case CFM_CHASSIS_ID_MAC_ADDRESS:
                    printf("\n\t  MAC %s", etheraddr_string(tptr+1));
                    break;

                case CFM_CHASSIS_ID_NETWORK_ADDRESS:
                    hexdump |= cfm_mgmt_addr_print(tptr);
                    break;

                case CFM_CHASSIS_ID_INTERFACE_NAME: /* fall through */
                case CFM_CHASSIS_ID_INTERFACE_ALIAS:
                case CFM_CHASSIS_ID_LOCAL:
                case CFM_CHASSIS_ID_CHASSIS_COMPONENT:
                case CFM_CHASSIS_ID_PORT_COMPONENT:
                    safeputs((const char *)tptr+1, chassis_id_length);
                    break;

                default:
                    hexdump = TRUE;
                    break;
                }
            }

            tptr += chassis_id_length;
            tlen -= chassis_id_length;

            /*
             * Check if there is a Management Address.
             */
            mgmt_addr_length = *tptr;
            if (mgmt_addr_length > tlen) {
                hexdump = TRUE;
                break;
            }

            tptr++;
            tlen--;

            if (mgmt_addr_length) {
                hexdump |= cfm_mgmt_addr_print(tptr);
            }

            tptr += mgmt_addr_length;
            tlen -= mgmt_addr_length;

        }
        break;

            /*
             * FIXME those are the defined TLVs that lack a decoder
             * you are welcome to contribute code ;-)
             */

        case CFM_TLV_DATA:
        case CFM_TLV_REPLY_INGRESS:
        case CFM_TLV_REPLY_EGRESS:
        default:
            hexdump = TRUE;
            break;
        }
        /* do we want to see an additional hexdump ? */
        if (hexdump || vflag > 1)
            print_unknown_data(tlv_ptr, "\n\t  ", cfm_tlv_len);

        tptr+=cfm_tlv_len;
        tlen-=cfm_tlv_len;
    }
    return;
trunc:
    printf("\n\t\t packet exceeded snapshot");
}
