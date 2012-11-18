/*
 * Copyright (c) 1992, 1993, 1994, 1995, 1996
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
 * Original code by Matt Thomas, Digital Equipment Corporation
 *
 * Extensively modified by Hannes Gredler (hannes@juniper.net) for more
 * complete IS-IS & CLNP support.
 */

#ifndef lint
static const char rcsid[] _U_ =
    "@(#) $Header: /tcpdump/master/tcpdump/print-isoclns.c,v 1.133.2.25 2007/03/02 09:20:27 hannes Exp $ (LBL)";
#endif

#ifdef HAVE_CONFIG_H
#include "config.h"
#endif

#include <tcpdump-stdinc.h>

#include <stdio.h>
#include <string.h>

#include "interface.h"
#include "addrtoname.h"
#include "ethertype.h"
#include "ether.h"
#include "nlpid.h"
#include "extract.h"
#include "gmpls.h"
#include "oui.h"

#define IPV4            1       /* AFI value */
#define IPV6            2       /* AFI value */

/*
 * IS-IS is defined in ISO 10589.  Look there for protocol definitions.
 */

#define SYSTEM_ID_LEN	ETHER_ADDR_LEN
#define NODE_ID_LEN     SYSTEM_ID_LEN+1
#define LSP_ID_LEN      SYSTEM_ID_LEN+2

#define ISIS_VERSION	1
#define ESIS_VERSION	1
#define CLNP_VERSION	1

#define ISIS_PDU_TYPE_MASK      0x1F
#define ESIS_PDU_TYPE_MASK      0x1F
#define CLNP_PDU_TYPE_MASK      0x1F
#define CLNP_FLAG_MASK          0xE0
#define ISIS_LAN_PRIORITY_MASK  0x7F

#define ISIS_PDU_L1_LAN_IIH	15
#define ISIS_PDU_L2_LAN_IIH	16
#define ISIS_PDU_PTP_IIH	17
#define ISIS_PDU_L1_LSP       	18
#define ISIS_PDU_L2_LSP       	20
#define ISIS_PDU_L1_CSNP  	24
#define ISIS_PDU_L2_CSNP  	25
#define ISIS_PDU_L1_PSNP        26
#define ISIS_PDU_L2_PSNP        27

static struct tok isis_pdu_values[] = {
    { ISIS_PDU_L1_LAN_IIH,       "L1 Lan IIH"},
    { ISIS_PDU_L2_LAN_IIH,       "L2 Lan IIH"},
    { ISIS_PDU_PTP_IIH,          "p2p IIH"},
    { ISIS_PDU_L1_LSP,           "L1 LSP"},
    { ISIS_PDU_L2_LSP,           "L2 LSP"},
    { ISIS_PDU_L1_CSNP,          "L1 CSNP"},
    { ISIS_PDU_L2_CSNP,          "L2 CSNP"},
    { ISIS_PDU_L1_PSNP,          "L1 PSNP"},
    { ISIS_PDU_L2_PSNP,          "L2 PSNP"},
    { 0, NULL}
};

/*
 * A TLV is a tuple of a type, length and a value and is normally used for
 * encoding information in all sorts of places.  This is an enumeration of
 * the well known types.
 *
 * list taken from rfc3359 plus some memory from veterans ;-)
 */

#define ISIS_TLV_AREA_ADDR           1   /* iso10589 */
#define ISIS_TLV_IS_REACH            2   /* iso10589 */
#define ISIS_TLV_ESNEIGH             3   /* iso10589 */
#define ISIS_TLV_PART_DIS            4   /* iso10589 */
#define ISIS_TLV_PREFIX_NEIGH        5   /* iso10589 */
#define ISIS_TLV_ISNEIGH             6   /* iso10589 */
#define ISIS_TLV_ISNEIGH_VARLEN      7   /* iso10589 */
#define ISIS_TLV_PADDING             8   /* iso10589 */
#define ISIS_TLV_LSP                 9   /* iso10589 */
#define ISIS_TLV_AUTH                10  /* iso10589, rfc3567 */
#define ISIS_TLV_CHECKSUM            12  /* rfc3358 */
#define ISIS_TLV_CHECKSUM_MINLEN 2
#define ISIS_TLV_LSP_BUFFERSIZE      14  /* iso10589 rev2 */
#define ISIS_TLV_LSP_BUFFERSIZE_MINLEN 2
#define ISIS_TLV_EXT_IS_REACH        22  /* draft-ietf-isis-traffic-05 */
#define ISIS_TLV_IS_ALIAS_ID         24  /* draft-ietf-isis-ext-lsp-frags-02 */
#define ISIS_TLV_DECNET_PHASE4       42
#define ISIS_TLV_LUCENT_PRIVATE      66
#define ISIS_TLV_INT_IP_REACH        128 /* rfc1195, rfc2966 */
#define ISIS_TLV_PROTOCOLS           129 /* rfc1195 */
#define ISIS_TLV_EXT_IP_REACH        130 /* rfc1195, rfc2966 */
#define ISIS_TLV_IDRP_INFO           131 /* rfc1195 */
#define ISIS_TLV_IDRP_INFO_MINLEN      1
#define ISIS_TLV_IPADDR              132 /* rfc1195 */
#define ISIS_TLV_IPAUTH              133 /* rfc1195 */
#define ISIS_TLV_TE_ROUTER_ID        134 /* draft-ietf-isis-traffic-05 */
#define ISIS_TLV_EXTD_IP_REACH       135 /* draft-ietf-isis-traffic-05 */
#define ISIS_TLV_HOSTNAME            137 /* rfc2763 */
#define ISIS_TLV_SHARED_RISK_GROUP   138 /* draft-ietf-isis-gmpls-extensions */
#define ISIS_TLV_NORTEL_PRIVATE1     176
#define ISIS_TLV_NORTEL_PRIVATE2     177
#define ISIS_TLV_RESTART_SIGNALING   211 /* rfc3847 */
#define ISIS_TLV_RESTART_SIGNALING_FLAGLEN 1
#define ISIS_TLV_RESTART_SIGNALING_HOLDTIMELEN 2
#define ISIS_TLV_MT_IS_REACH         222 /* draft-ietf-isis-wg-multi-topology-05 */
#define ISIS_TLV_MT_SUPPORTED        229 /* draft-ietf-isis-wg-multi-topology-05 */
#define ISIS_TLV_MT_SUPPORTED_MINLEN 2
#define ISIS_TLV_IP6ADDR             232 /* draft-ietf-isis-ipv6-02 */
#define ISIS_TLV_MT_IP_REACH         235 /* draft-ietf-isis-wg-multi-topology-05 */
#define ISIS_TLV_IP6_REACH           236 /* draft-ietf-isis-ipv6-02 */
#define ISIS_TLV_MT_IP6_REACH        237 /* draft-ietf-isis-wg-multi-topology-05 */
#define ISIS_TLV_PTP_ADJ             240 /* rfc3373 */
#define ISIS_TLV_IIH_SEQNR           241 /* draft-shen-isis-iih-sequence-00 */
#define ISIS_TLV_IIH_SEQNR_MINLEN 4
#define ISIS_TLV_VENDOR_PRIVATE      250 /* draft-ietf-isis-experimental-tlv-01 */
#define ISIS_TLV_VENDOR_PRIVATE_MINLEN 3

static struct tok isis_tlv_values[] = {
    { ISIS_TLV_AREA_ADDR,	   "Area address(es)"},
    { ISIS_TLV_IS_REACH,           "IS Reachability"},
    { ISIS_TLV_ESNEIGH,            "ES Neighbor(s)"},
    { ISIS_TLV_PART_DIS,           "Partition DIS"},
    { ISIS_TLV_PREFIX_NEIGH,       "Prefix Neighbors"},
    { ISIS_TLV_ISNEIGH,            "IS Neighbor(s)"},
    { ISIS_TLV_ISNEIGH_VARLEN,     "IS Neighbor(s) (variable length)"},
    { ISIS_TLV_PADDING,            "Padding"},
    { ISIS_TLV_LSP,                "LSP entries"},
    { ISIS_TLV_AUTH,               "Authentication"},
    { ISIS_TLV_CHECKSUM,           "Checksum"},
    { ISIS_TLV_LSP_BUFFERSIZE,     "LSP Buffersize"},
    { ISIS_TLV_EXT_IS_REACH,       "Extended IS Reachability"},
    { ISIS_TLV_IS_ALIAS_ID,        "IS Alias ID"},
    { ISIS_TLV_DECNET_PHASE4,      "DECnet Phase IV"},
    { ISIS_TLV_LUCENT_PRIVATE,     "Lucent Proprietary"},
    { ISIS_TLV_INT_IP_REACH,       "IPv4 Internal Reachability"},
    { ISIS_TLV_PROTOCOLS,          "Protocols supported"},
    { ISIS_TLV_EXT_IP_REACH,       "IPv4 External Reachability"},
    { ISIS_TLV_IDRP_INFO,          "Inter-Domain Information Type"},
    { ISIS_TLV_IPADDR,             "IPv4 Interface address(es)"},
    { ISIS_TLV_IPAUTH,             "IPv4 authentication (deprecated)"},
    { ISIS_TLV_TE_ROUTER_ID,       "Traffic Engineering Router ID"},
    { ISIS_TLV_EXTD_IP_REACH,      "Extended IPv4 Reachability"},
    { ISIS_TLV_SHARED_RISK_GROUP,  "Shared Risk Link Group"},
    { ISIS_TLV_NORTEL_PRIVATE1,    "Nortel Proprietary"},
    { ISIS_TLV_NORTEL_PRIVATE2,    "Nortel Proprietary"},
    { ISIS_TLV_HOSTNAME,           "Hostname"},
    { ISIS_TLV_RESTART_SIGNALING,  "Restart Signaling"},
    { ISIS_TLV_MT_IS_REACH,        "Multi Topology IS Reachability"},
    { ISIS_TLV_MT_SUPPORTED,       "Multi Topology"},
    { ISIS_TLV_IP6ADDR,            "IPv6 Interface address(es)"},
    { ISIS_TLV_MT_IP_REACH,        "Multi-Topology IPv4 Reachability"},
    { ISIS_TLV_IP6_REACH,          "IPv6 reachability"},
    { ISIS_TLV_MT_IP6_REACH,       "Multi-Topology IP6 Reachability"},
    { ISIS_TLV_PTP_ADJ,            "Point-to-point Adjacency State"},
    { ISIS_TLV_IIH_SEQNR,          "Hello PDU Sequence Number"},
    { ISIS_TLV_VENDOR_PRIVATE,     "Vendor Private"},
    { 0, NULL }
};

#define ESIS_OPTION_PROTOCOLS        129
#define ESIS_OPTION_QOS_MAINTENANCE  195 /* iso9542 */
#define ESIS_OPTION_SECURITY         197 /* iso9542 */
#define ESIS_OPTION_ES_CONF_TIME     198 /* iso9542 */
#define ESIS_OPTION_PRIORITY         205 /* iso9542 */
#define ESIS_OPTION_ADDRESS_MASK     225 /* iso9542 */
#define ESIS_OPTION_SNPA_MASK        226 /* iso9542 */

static struct tok esis_option_values[] = {
    { ESIS_OPTION_PROTOCOLS,       "Protocols supported"},
    { ESIS_OPTION_QOS_MAINTENANCE, "QoS Maintenance" },
    { ESIS_OPTION_SECURITY,        "Security" },
    { ESIS_OPTION_ES_CONF_TIME,    "ES Configuration Time" },
    { ESIS_OPTION_PRIORITY,        "Priority" },
    { ESIS_OPTION_ADDRESS_MASK,    "Addressk Mask" },
    { ESIS_OPTION_SNPA_MASK,       "SNPA Mask" },
    { 0, NULL }
};

#define CLNP_OPTION_DISCARD_REASON   193
#define CLNP_OPTION_QOS_MAINTENANCE  195 /* iso8473 */
#define CLNP_OPTION_SECURITY         197 /* iso8473 */
#define CLNP_OPTION_SOURCE_ROUTING   200 /* iso8473 */
#define CLNP_OPTION_ROUTE_RECORDING  203 /* iso8473 */
#define CLNP_OPTION_PADDING          204 /* iso8473 */
#define CLNP_OPTION_PRIORITY         205 /* iso8473 */

static struct tok clnp_option_values[] = {
    { CLNP_OPTION_DISCARD_REASON,  "Discard Reason"},
    { CLNP_OPTION_PRIORITY,        "Priority"},
    { CLNP_OPTION_QOS_MAINTENANCE, "QoS Maintenance"},
    { CLNP_OPTION_SECURITY, "Security"},
    { CLNP_OPTION_SOURCE_ROUTING, "Source Routing"},
    { CLNP_OPTION_ROUTE_RECORDING, "Route Recording"},
    { CLNP_OPTION_PADDING, "Padding"},
    { 0, NULL }
};

static struct tok clnp_option_rfd_class_values[] = {
    { 0x0, "General"},
    { 0x8, "Address"},
    { 0x9, "Source Routeing"},
    { 0xa, "Lifetime"},
    { 0xb, "PDU Discarded"},
    { 0xc, "Reassembly"},
    { 0, NULL }
};

static struct tok clnp_option_rfd_general_values[] = {
    { 0x0, "Reason not specified"},
    { 0x1, "Protocol procedure error"},
    { 0x2, "Incorrect checksum"},
    { 0x3, "PDU discarded due to congestion"},
    { 0x4, "Header syntax error (cannot be parsed)"},
    { 0x5, "Segmentation needed but not permitted"},
    { 0x6, "Incomplete PDU received"},
    { 0x7, "Duplicate option"},
    { 0, NULL }
};

static struct tok clnp_option_rfd_address_values[] = {
    { 0x0, "Destination address unreachable"},
    { 0x1, "Destination address unknown"},
    { 0, NULL }
};

static struct tok clnp_option_rfd_source_routeing_values[] = {
    { 0x0, "Unspecified source routeing error"},
    { 0x1, "Syntax error in source routeing field"},
    { 0x2, "Unknown address in source routeing field"},
    { 0x3, "Path not acceptable"},
    { 0, NULL }
};

static struct tok clnp_option_rfd_lifetime_values[] = {
    { 0x0, "Lifetime expired while data unit in transit"},
    { 0x1, "Lifetime expired during reassembly"},
    { 0, NULL }
};

static struct tok clnp_option_rfd_pdu_discard_values[] = {
    { 0x0, "Unsupported option not specified"},
    { 0x1, "Unsupported protocol version"},
    { 0x2, "Unsupported security option"},
    { 0x3, "Unsupported source routeing option"},
    { 0x4, "Unsupported recording of route option"},
    { 0, NULL }
};

static struct tok clnp_option_rfd_reassembly_values[] = {
    { 0x0, "Reassembly interference"},
    { 0, NULL }
};

/* array of 16 error-classes */
static struct tok *clnp_option_rfd_error_class[] = {
    clnp_option_rfd_general_values,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    clnp_option_rfd_address_values,
    clnp_option_rfd_source_routeing_values,
    clnp_option_rfd_lifetime_values,
    clnp_option_rfd_pdu_discard_values,
    clnp_option_rfd_reassembly_values,
    NULL,
    NULL,
    NULL
};

#define CLNP_OPTION_OPTION_QOS_MASK 0x3f
#define CLNP_OPTION_SCOPE_MASK      0xc0
#define CLNP_OPTION_SCOPE_SA_SPEC   0x40
#define CLNP_OPTION_SCOPE_DA_SPEC   0x80
#define CLNP_OPTION_SCOPE_GLOBAL    0xc0

static struct tok clnp_option_scope_values[] = {
    { CLNP_OPTION_SCOPE_SA_SPEC, "Source Address Specific"},
    { CLNP_OPTION_SCOPE_DA_SPEC, "Destination Address Specific"},
    { CLNP_OPTION_SCOPE_GLOBAL, "Globally unique"},
    { 0, NULL }
};

static struct tok clnp_option_sr_rr_values[] = {
    { 0x0, "partial"},
    { 0x1, "complete"},
    { 0, NULL }
};

static struct tok clnp_option_sr_rr_string_values[] = {
    { CLNP_OPTION_SOURCE_ROUTING, "source routing"},
    { CLNP_OPTION_ROUTE_RECORDING, "recording of route in progress"},
    { 0, NULL }
};

static struct tok clnp_option_qos_global_values[] = {
    { 0x20, "reserved"},
    { 0x10, "sequencing vs. delay"},
    { 0x08, "congested"},
    { 0x04, "delay vs. cost"},
    { 0x02, "error vs. delay"},
    { 0x01, "error vs. cost"},
    { 0, NULL }
};

#define ISIS_SUBTLV_EXT_IS_REACH_ADMIN_GROUP           3 /* draft-ietf-isis-traffic-05 */
#define ISIS_SUBTLV_EXT_IS_REACH_LINK_LOCAL_REMOTE_ID  4 /* draft-ietf-isis-gmpls-extensions */
#define ISIS_SUBTLV_EXT_IS_REACH_LINK_REMOTE_ID        5 /* draft-ietf-isis-traffic-05 */
#define ISIS_SUBTLV_EXT_IS_REACH_IPV4_INTF_ADDR        6 /* draft-ietf-isis-traffic-05 */
#define ISIS_SUBTLV_EXT_IS_REACH_IPV4_NEIGHBOR_ADDR    8 /* draft-ietf-isis-traffic-05 */
#define ISIS_SUBTLV_EXT_IS_REACH_MAX_LINK_BW           9 /* draft-ietf-isis-traffic-05 */
#define ISIS_SUBTLV_EXT_IS_REACH_RESERVABLE_BW        10 /* draft-ietf-isis-traffic-05 */
#define ISIS_SUBTLV_EXT_IS_REACH_UNRESERVED_BW        11 /* rfc4124 */
#define ISIS_SUBTLV_EXT_IS_REACH_BW_CONSTRAINTS_OLD   12 /* draft-ietf-tewg-diff-te-proto-06 */
#define ISIS_SUBTLV_EXT_IS_REACH_TE_METRIC            18 /* draft-ietf-isis-traffic-05 */
#define ISIS_SUBTLV_EXT_IS_REACH_LINK_PROTECTION_TYPE 20 /* draft-ietf-isis-gmpls-extensions */
#define ISIS_SUBTLV_EXT_IS_REACH_INTF_SW_CAP_DESCR    21 /* draft-ietf-isis-gmpls-extensions */
#define ISIS_SUBTLV_EXT_IS_REACH_BW_CONSTRAINTS       22 /* rfc4124 */

static struct tok isis_ext_is_reach_subtlv_values[] = {
    { ISIS_SUBTLV_EXT_IS_REACH_ADMIN_GROUP,            "Administrative groups" },
    { ISIS_SUBTLV_EXT_IS_REACH_LINK_LOCAL_REMOTE_ID,   "Link Local/Remote Identifier" },
    { ISIS_SUBTLV_EXT_IS_REACH_LINK_REMOTE_ID,         "Link Remote Identifier" },
    { ISIS_SUBTLV_EXT_IS_REACH_IPV4_INTF_ADDR,         "IPv4 interface address" },
    { ISIS_SUBTLV_EXT_IS_REACH_IPV4_NEIGHBOR_ADDR,     "IPv4 neighbor address" },
    { ISIS_SUBTLV_EXT_IS_REACH_MAX_LINK_BW,            "Maximum link bandwidth" },
    { ISIS_SUBTLV_EXT_IS_REACH_RESERVABLE_BW,          "Reservable link bandwidth" },
    { ISIS_SUBTLV_EXT_IS_REACH_UNRESERVED_BW,          "Unreserved bandwidth" },
    { ISIS_SUBTLV_EXT_IS_REACH_TE_METRIC,              "Traffic Engineering Metric" },
    { ISIS_SUBTLV_EXT_IS_REACH_LINK_PROTECTION_TYPE,   "Link Protection Type" },
    { ISIS_SUBTLV_EXT_IS_REACH_INTF_SW_CAP_DESCR,      "Interface Switching Capability" },
    { ISIS_SUBTLV_EXT_IS_REACH_BW_CONSTRAINTS_OLD,     "Bandwidth Constraints (old)" },
    { ISIS_SUBTLV_EXT_IS_REACH_BW_CONSTRAINTS,         "Bandwidth Constraints" },
    { 250,                                             "Reserved for cisco specific extensions" },
    { 251,                                             "Reserved for cisco specific extensions" },
    { 252,                                             "Reserved for cisco specific extensions" },
    { 253,                                             "Reserved for cisco specific extensions" },
    { 254,                                             "Reserved for cisco specific extensions" },
    { 255,                                             "Reserved for future expansion" },
    { 0, NULL }
};

#define ISIS_SUBTLV_EXTD_IP_REACH_ADMIN_TAG32          1 /* draft-ietf-isis-admin-tags-01 */
#define ISIS_SUBTLV_EXTD_IP_REACH_ADMIN_TAG64          2 /* draft-ietf-isis-admin-tags-01 */
#define ISIS_SUBTLV_EXTD_IP_REACH_MGMT_PREFIX_COLOR  117 /* draft-ietf-isis-wg-multi-topology-05 */

static struct tok isis_ext_ip_reach_subtlv_values[] = {
    { ISIS_SUBTLV_EXTD_IP_REACH_ADMIN_TAG32,           "32-Bit Administrative tag" },
    { ISIS_SUBTLV_EXTD_IP_REACH_ADMIN_TAG64,           "64-Bit Administrative tag" },
    { ISIS_SUBTLV_EXTD_IP_REACH_MGMT_PREFIX_COLOR,     "Management Prefix Color" },
    { 0, NULL }
};

#define ISIS_SUBTLV_AUTH_SIMPLE        1
#define ISIS_SUBTLV_AUTH_MD5          54
#define ISIS_SUBTLV_AUTH_MD5_LEN      16
#define ISIS_SUBTLV_AUTH_PRIVATE     255

static struct tok isis_subtlv_auth_values[] = {
    { ISIS_SUBTLV_AUTH_SIMPLE,	"simple text password"},
    { ISIS_SUBTLV_AUTH_MD5,	"HMAC-MD5 password"},
    { ISIS_SUBTLV_AUTH_PRIVATE,	"Routing Domain private password"},
    { 0, NULL }
};

#define ISIS_SUBTLV_IDRP_RES           0
#define ISIS_SUBTLV_IDRP_LOCAL         1
#define ISIS_SUBTLV_IDRP_ASN           2

static struct tok isis_subtlv_idrp_values[] = {
    { ISIS_SUBTLV_IDRP_RES,         "Reserved"},
    { ISIS_SUBTLV_IDRP_LOCAL,       "Routing-Domain Specific"},
    { ISIS_SUBTLV_IDRP_ASN,         "AS Number Tag"},
    { 0, NULL}
};

#define CLNP_SEGMENT_PART  0x80
#define CLNP_MORE_SEGMENTS 0x40
#define CLNP_REQUEST_ER    0x20

static struct tok clnp_flag_values[] = {
    { CLNP_SEGMENT_PART, "Segmentation permitted"},
    { CLNP_MORE_SEGMENTS, "more Segments"},
    { CLNP_REQUEST_ER, "request Error Report"},
    { 0, NULL}
};

#define ISIS_MASK_LSP_OL_BIT(x)            ((x)&0x4)
#define ISIS_MASK_LSP_ISTYPE_BITS(x)       ((x)&0x3)
#define ISIS_MASK_LSP_PARTITION_BIT(x)     ((x)&0x80)
#define ISIS_MASK_LSP_ATT_BITS(x)          ((x)&0x78)
#define ISIS_MASK_LSP_ATT_ERROR_BIT(x)     ((x)&0x40)
#define ISIS_MASK_LSP_ATT_EXPENSE_BIT(x)   ((x)&0x20)
#define ISIS_MASK_LSP_ATT_DELAY_BIT(x)     ((x)&0x10)
#define ISIS_MASK_LSP_ATT_DEFAULT_BIT(x)   ((x)&0x8)

#define ISIS_MASK_MTID(x)                  ((x)&0x0fff)
#define ISIS_MASK_MTFLAGS(x)               ((x)&0xf000)

static struct tok isis_mt_flag_values[] = {
    { 0x4000,                  "sub-TLVs present"},
    { 0x8000,                  "ATT bit set"},
    { 0, NULL}
};

#define ISIS_MASK_TLV_EXTD_IP_UPDOWN(x)     ((x)&0x80)
#define ISIS_MASK_TLV_EXTD_IP_SUBTLV(x)     ((x)&0x40)

#define ISIS_MASK_TLV_EXTD_IP6_IE(x)        ((x)&0x40)
#define ISIS_MASK_TLV_EXTD_IP6_SUBTLV(x)    ((x)&0x20)

#define ISIS_LSP_TLV_METRIC_SUPPORTED(x)   ((x)&0x80)
#define ISIS_LSP_TLV_METRIC_IE(x)          ((x)&0x40)
#define ISIS_LSP_TLV_METRIC_UPDOWN(x)      ((x)&0x80)
#define ISIS_LSP_TLV_METRIC_VALUE(x)	   ((x)&0x3f)

#define ISIS_MASK_TLV_SHARED_RISK_GROUP(x) ((x)&0x1)

static struct tok isis_mt_values[] = {
    { 0,    "IPv4 unicast"},
    { 1,    "In-Band Management"},
    { 2,    "IPv6 unicast"},
    { 3,    "Multicast"},
    { 4095, "Development, Experimental or Proprietary"},
    { 0, NULL }
};

static struct tok isis_iih_circuit_type_values[] = {
    { 1,    "Level 1 only"},
    { 2,    "Level 2 only"},
    { 3,    "Level 1, Level 2"},
    { 0, NULL}
};

#define ISIS_LSP_TYPE_UNUSED0   0
#define ISIS_LSP_TYPE_LEVEL_1   1
#define ISIS_LSP_TYPE_UNUSED2   2
#define ISIS_LSP_TYPE_LEVEL_2   3

static struct tok isis_lsp_istype_values[] = {
    { ISIS_LSP_TYPE_UNUSED0,	"Unused 0x0 (invalid)"},
    { ISIS_LSP_TYPE_LEVEL_1,	"L1 IS"},
    { ISIS_LSP_TYPE_UNUSED2,	"Unused 0x2 (invalid)"},
    { ISIS_LSP_TYPE_LEVEL_2,	"L2 IS"},
    { 0, NULL }
};

/*
 * Katz's point to point adjacency TLV uses codes to tell us the state of
 * the remote adjacency.  Enumerate them.
 */

#define ISIS_PTP_ADJ_UP   0
#define ISIS_PTP_ADJ_INIT 1
#define ISIS_PTP_ADJ_DOWN 2

static struct tok isis_ptp_adjancey_values[] = {
    { ISIS_PTP_ADJ_UP,    "Up" },
    { ISIS_PTP_ADJ_INIT,  "Initializing" },
    { ISIS_PTP_ADJ_DOWN,  "Down" },
    { 0, NULL}
};

struct isis_tlv_ptp_adj {
    u_int8_t adjacency_state;
    u_int8_t extd_local_circuit_id[4];
    u_int8_t neighbor_sysid[SYSTEM_ID_LEN];
    u_int8_t neighbor_extd_local_circuit_id[4];
};

static int osi_cksum(const u_int8_t *, u_int);
static int clnp_print(const u_int8_t *, u_int);
static void esis_print(const u_int8_t *, u_int);
static int isis_print(const u_int8_t *, u_int);

struct isis_metric_block {
    u_int8_t metric_default;
    u_int8_t metric_delay;
    u_int8_t metric_expense;
    u_int8_t metric_error;
};

struct isis_tlv_is_reach {
    struct isis_metric_block isis_metric_block;
    u_int8_t neighbor_nodeid[NODE_ID_LEN];
};

struct isis_tlv_es_reach {
    struct isis_metric_block isis_metric_block;
    u_int8_t neighbor_sysid[SYSTEM_ID_LEN];
};

struct isis_tlv_ip_reach {
    struct isis_metric_block isis_metric_block;
    u_int8_t prefix[4];
    u_int8_t mask[4];
};

static struct tok isis_is_reach_virtual_values[] = {
    { 0,    "IsNotVirtual"},
    { 1,    "IsVirtual"},
    { 0, NULL }
};

static struct tok isis_restart_flag_values[] = {
    { 0x1,  "Restart Request"},
    { 0x2,  "Restart Acknowledgement"},
    { 0x4,  "Suppress adjacency advertisement"},
    { 0, NULL }
};

struct isis_common_header {
    u_int8_t nlpid;
    u_int8_t fixed_len;
    u_int8_t version;			/* Protocol version */
    u_int8_t id_length;
    u_int8_t pdu_type;		        /* 3 MSbits are reserved */
    u_int8_t pdu_version;		/* Packet format version */
    u_int8_t reserved;
    u_int8_t max_area;
};

struct isis_iih_lan_header {
    u_int8_t circuit_type;
    u_int8_t source_id[SYSTEM_ID_LEN];
    u_int8_t holding_time[2];
    u_int8_t pdu_len[2];
    u_int8_t priority;
    u_int8_t lan_id[NODE_ID_LEN];
};

struct isis_iih_ptp_header {
    u_int8_t circuit_type;
    u_int8_t source_id[SYSTEM_ID_LEN];
    u_int8_t holding_time[2];
    u_int8_t pdu_len[2];
    u_int8_t circuit_id;
};

struct isis_lsp_header {
    u_int8_t pdu_len[2];
    u_int8_t remaining_lifetime[2];
    u_int8_t lsp_id[LSP_ID_LEN];
    u_int8_t sequence_number[4];
    u_int8_t checksum[2];
    u_int8_t typeblock;
};

struct isis_csnp_header {
    u_int8_t pdu_len[2];
    u_int8_t source_id[NODE_ID_LEN];
    u_int8_t start_lsp_id[LSP_ID_LEN];
    u_int8_t end_lsp_id[LSP_ID_LEN];
};

struct isis_psnp_header {
    u_int8_t pdu_len[2];
    u_int8_t source_id[NODE_ID_LEN];
};

struct isis_tlv_lsp {
    u_int8_t remaining_lifetime[2];
    u_int8_t lsp_id[LSP_ID_LEN];
    u_int8_t sequence_number[4];
    u_int8_t checksum[2];
};

#define ISIS_COMMON_HEADER_SIZE (sizeof(struct isis_common_header))
#define ISIS_IIH_LAN_HEADER_SIZE (sizeof(struct isis_iih_lan_header))
#define ISIS_IIH_PTP_HEADER_SIZE (sizeof(struct isis_iih_ptp_header))
#define ISIS_LSP_HEADER_SIZE (sizeof(struct isis_lsp_header))
#define ISIS_CSNP_HEADER_SIZE (sizeof(struct isis_csnp_header))
#define ISIS_PSNP_HEADER_SIZE (sizeof(struct isis_psnp_header))

void isoclns_print(const u_int8_t *p, u_int length, u_int caplen)
{
	const struct isis_common_header *header;

	header = (const struct isis_common_header *)p;

        if (caplen <= 1) { /* enough bytes on the wire ? */
            printf("|OSI");
            return;
        }

        if (eflag)
            printf("OSI NLPID %s (0x%02x): ",
                   tok2str(nlpid_values,"Unknown",*p),
                   *p);
        
	switch (*p) {

	case NLPID_CLNP:
		if (!clnp_print(p, length))
                        print_unknown_data(p,"\n\t",caplen);
		break;

	case NLPID_ESIS:
		esis_print(p, length);
		return;

	case NLPID_ISIS:
		if (!isis_print(p, length))
                        print_unknown_data(p,"\n\t",caplen);
		break;

	case NLPID_NULLNS:
		(void)printf("%slength: %u",
		             eflag ? "" : ", ",
                             length);
		break;

        case NLPID_Q933:
                q933_print(p+1, length-1);
                break;

        case NLPID_IP:
		ip_print(gndo, p+1, length-1);
                break;

#ifdef INET6
        case NLPID_IP6:
                ip6_print(p+1, length-1);
                break;
#endif

        case NLPID_PPP:
                ppp_print(p+1, length-1);
                break;

	default:
                if (!eflag)
                    printf("OSI NLPID 0x%02x unknown",*p);
		(void)printf("%slength: %u",
		             eflag ? "" : ", ",
                             length);
		if (caplen > 1)
                        print_unknown_data(p,"\n\t",caplen);
		break;
	}
}

#define	CLNP_PDU_ER	 1
#define	CLNP_PDU_DT	28
#define	CLNP_PDU_MD	29
#define	CLNP_PDU_ERQ	30
#define	CLNP_PDU_ERP	31

static struct tok clnp_pdu_values[] = {
    { CLNP_PDU_ER,  "Error Report"},
    { CLNP_PDU_MD,  "MD"},
    { CLNP_PDU_DT,  "Data"},
    { CLNP_PDU_ERQ, "Echo Request"},
    { CLNP_PDU_ERP, "Echo Response"},
    { 0, NULL }
};

struct clnp_header_t {
    u_int8_t nlpid;
    u_int8_t length_indicator;
    u_int8_t version;
    u_int8_t lifetime; /* units of 500ms */
    u_int8_t type;
    u_int8_t segment_length[2];
    u_int8_t cksum[2];
};

struct clnp_segment_header_t {
    u_int8_t data_unit_id[2];
    u_int8_t segment_offset[2];
    u_int8_t total_length[2];
};

/*
 * clnp_print
 * Decode CLNP packets.  Return 0 on error.
 */

static int clnp_print (const u_int8_t *pptr, u_int length)
{
	const u_int8_t *optr,*source_address,*dest_address;
        u_int li,tlen,nsap_offset,source_address_length,dest_address_length, clnp_pdu_type, clnp_flags;
	const struct clnp_header_t *clnp_header;
	const struct clnp_segment_header_t *clnp_segment_header;
        u_int8_t rfd_error_major,rfd_error_minor;

	clnp_header = (const struct clnp_header_t *) pptr;
        TCHECK(*clnp_header);

        li = clnp_header->length_indicator;
        optr = pptr;

        if (!eflag)
            printf("CLNP");

        /*
         * Sanity checking of the header.
         */

        if (clnp_header->version != CLNP_VERSION) {
            printf("version %d packet not supported", clnp_header->version);
            return (0);
        }

        /* FIXME further header sanity checking */

        clnp_pdu_type = clnp_header->type & CLNP_PDU_TYPE_MASK;
        clnp_flags = clnp_header->type & CLNP_FLAG_MASK;

        pptr += sizeof(struct clnp_header_t);
        li -= sizeof(struct clnp_header_t);
        dest_address_length = *pptr;
        dest_address = pptr + 1;

        pptr += (1 + dest_address_length);
        li -= (1 + dest_address_length);
        source_address_length = *pptr;
        source_address = pptr +1;

        pptr += (1 + source_address_length);
        li -= (1 + source_address_length);

        if (vflag < 1) {
            printf("%s%s > %s, %s, length %u",
                   eflag ? "" : ", ",
                   isonsap_string(source_address, source_address_length),
                   isonsap_string(dest_address, dest_address_length),
                   tok2str(clnp_pdu_values,"unknown (%u)",clnp_pdu_type),
                   length);
            return (1);
        }
        printf("%slength %u",eflag ? "" : ", ",length);

        printf("\n\t%s PDU, hlen: %u, v: %u, lifetime: %u.%us, Segment PDU length: %u, checksum: 0x%04x ",
               tok2str(clnp_pdu_values, "unknown (%u)",clnp_pdu_type),
               clnp_header->length_indicator,
               clnp_header->version,
               clnp_header->lifetime/2,
               (clnp_header->lifetime%2)*5,
               EXTRACT_16BITS(clnp_header->segment_length),
               EXTRACT_16BITS(clnp_header->cksum));

        /* do not attempt to verify the checksum if it is zero */
        if (EXTRACT_16BITS(clnp_header->cksum) == 0)
                printf("(unverified)");
            else printf("(%s)", osi_cksum(optr, clnp_header->length_indicator) ? "incorrect" : "correct");

        printf("\n\tFlags [%s]",
               bittok2str(clnp_flag_values,"none",clnp_flags));

        printf("\n\tsource address (length %u): %s\n\tdest   address (length %u): %s",
               source_address_length,
               isonsap_string(source_address, source_address_length),
               dest_address_length,
               isonsap_string(dest_address,dest_address_length));

        if (clnp_flags & CLNP_SEGMENT_PART) {
            	clnp_segment_header = (const struct clnp_segment_header_t *) pptr;
                TCHECK(*clnp_segment_header);
                printf("\n\tData Unit ID: 0x%04x, Segment Offset: %u, Total PDU Length: %u",
                       EXTRACT_16BITS(clnp_segment_header->data_unit_id),
                       EXTRACT_16BITS(clnp_segment_header->segment_offset),
                       EXTRACT_16BITS(clnp_segment_header->total_length));
                pptr+=sizeof(const struct clnp_segment_header_t);
                li-=sizeof(const struct clnp_segment_header_t);
        }

        /* now walk the options */
        while (li >= 2) {
            u_int op, opli;
            const u_int8_t *tptr;
            
            TCHECK2(*pptr, 2);
            if (li < 2) {
                printf(", bad opts/li");
                return (0);
            }
            op = *pptr++;
            opli = *pptr++;
            li -= 2;
            TCHECK2(*pptr, opli);
            if (opli > li) {
                printf(", opt (%d) too long", op);
                return (0);
            }
            li -= opli;
            tptr = pptr;
            tlen = opli;
            
            printf("\n\t  %s Option #%u, length %u, value: ",
                   tok2str(clnp_option_values,"Unknown",op),
                   op,
                   opli);

            switch (op) {


            case CLNP_OPTION_ROUTE_RECORDING: /* those two options share the format */
            case CLNP_OPTION_SOURCE_ROUTING:  
                    printf("%s %s",
                           tok2str(clnp_option_sr_rr_values,"Unknown",*tptr),
                           tok2str(clnp_option_sr_rr_string_values,"Unknown Option %u",op));
                    nsap_offset=*(tptr+1);
                    if (nsap_offset == 0) {
                            printf(" Bad NSAP offset (0)");
                            break;
                    }
                    nsap_offset-=1; /* offset to nsap list */
                    if (nsap_offset > tlen) {
                            printf(" Bad NSAP offset (past end of option)");
                            break;
                    }
                    tptr+=nsap_offset;
                    tlen-=nsap_offset;
                    while (tlen > 0) {
                            source_address_length=*tptr;
                            if (tlen < source_address_length+1) {
                                    printf("\n\t    NSAP address goes past end of option");
                                    break;
                            }
                            if (source_address_length > 0) {
                                    source_address=(tptr+1);
                                    TCHECK2(*source_address, source_address_length);
                                    printf("\n\t    NSAP address (length %u): %s",
                                           source_address_length,
                                           isonsap_string(source_address, source_address_length));
                            }
                            tlen-=source_address_length+1;
                    }
                    break;

            case CLNP_OPTION_PRIORITY:
                    printf("0x%1x", *tptr&0x0f);
                    break;

            case CLNP_OPTION_QOS_MAINTENANCE:
                    printf("\n\t    Format Code: %s",
                           tok2str(clnp_option_scope_values,"Reserved",*tptr&CLNP_OPTION_SCOPE_MASK));

                    if ((*tptr&CLNP_OPTION_SCOPE_MASK) == CLNP_OPTION_SCOPE_GLOBAL)
                            printf("\n\t    QoS Flags [%s]",
                                   bittok2str(clnp_option_qos_global_values,
                                              "none",
                                              *tptr&CLNP_OPTION_OPTION_QOS_MASK));
                    break;

            case CLNP_OPTION_SECURITY:
                    printf("\n\t    Format Code: %s, Security-Level %u",
                           tok2str(clnp_option_scope_values,"Reserved",*tptr&CLNP_OPTION_SCOPE_MASK),
                           *(tptr+1));
                    break;

            case CLNP_OPTION_DISCARD_REASON:
                rfd_error_major = (*tptr&0xf0) >> 4;
                rfd_error_minor = *tptr&0x0f;
                printf("\n\t    Class: %s Error (0x%01x), %s (0x%01x)",
                       tok2str(clnp_option_rfd_class_values,"Unknown",rfd_error_major),
                       rfd_error_major,
                       tok2str(clnp_option_rfd_error_class[rfd_error_major],"Unknown",rfd_error_minor),
                       rfd_error_minor);
                break;

            case CLNP_OPTION_PADDING:
                    printf("padding data");
                break;

                /*
                 * FIXME those are the defined Options that lack a decoder
                 * you are welcome to contribute code ;-)
                 */

            default:
                print_unknown_data(tptr,"\n\t  ",opli);
                break;
            }
            if (vflag > 1)
                print_unknown_data(pptr,"\n\t  ",opli);
            pptr += opli;
        }

        switch (clnp_pdu_type) {

        case    CLNP_PDU_ER: /* fall through */
        case 	CLNP_PDU_ERP:
            TCHECK(*pptr);
            if (*(pptr) == NLPID_CLNP) {
                printf("\n\t-----original packet-----\n\t");
                /* FIXME recursion protection */
                clnp_print(pptr, length-clnp_header->length_indicator);
                break;
            } 

        case 	CLNP_PDU_DT:
        case 	CLNP_PDU_MD:
        case 	CLNP_PDU_ERQ:
            
        default:
            /* dump the PDU specific data */
            if (length-(pptr-optr) > 0) {
                printf("\n\t  undecoded non-header data, length %u",length-clnp_header->length_indicator);
                print_unknown_data(pptr,"\n\t  ",length-(pptr-optr));
            }
        }

        return (1);

 trunc:
    fputs("[|clnp]", stdout);
    return (1);

}


#define	ESIS_PDU_REDIRECT	6
#define	ESIS_PDU_ESH	        2
#define	ESIS_PDU_ISH	        4

static struct tok esis_pdu_values[] = {
    { ESIS_PDU_REDIRECT, "redirect"},
    { ESIS_PDU_ESH,      "ESH"},
    { ESIS_PDU_ISH,      "ISH"},
    { 0, NULL }
};

struct esis_header_t {
	u_int8_t nlpid;
	u_int8_t length_indicator;
	u_int8_t version;
	u_int8_t reserved;
	u_int8_t type;
	u_int8_t holdtime[2];
	u_int8_t cksum[2];
};

static void
esis_print(const u_int8_t *pptr, u_int length)
{
	const u_int8_t *optr;
	u_int li,esis_pdu_type,source_address_length, source_address_number;
	const struct esis_header_t *esis_header;

        if (!eflag)
            printf("ES-IS");

	if (length <= 2) {
		if (qflag)
			printf("bad pkt!");
		else
			printf("no header at all!");
		return;
	}

	esis_header = (const struct esis_header_t *) pptr;
        TCHECK(*esis_header);
        li = esis_header->length_indicator;
        optr = pptr;

        /*
         * Sanity checking of the header.
         */

        if (esis_header->nlpid != NLPID_ESIS) {
            printf(" nlpid 0x%02x packet not supported", esis_header->nlpid);
            return;
        }

        if (esis_header->version != ESIS_VERSION) {
            printf(" version %d packet not supported", esis_header->version);
            return;
        }
                
	if (li > length) {
            printf(" length indicator(%d) > PDU size (%d)!", li, length);
            return;
	}

	if (li < sizeof(struct esis_header_t) + 2) {
            printf(" length indicator < min PDU size %d:", li);
            while (--length != 0)
                printf("%02X", *pptr++);
            return;
	}

        esis_pdu_type = esis_header->type & ESIS_PDU_TYPE_MASK;

        if (vflag < 1) {
            printf("%s%s, length %u",
                   eflag ? "" : ", ",
                   tok2str(esis_pdu_values,"unknown type (%u)",esis_pdu_type),
                   length);
            return;
        } else
            printf("%slength %u\n\t%s (%u)",
                   eflag ? "" : ", ",
                   length,
                   tok2str(esis_pdu_values,"unknown type: %u", esis_pdu_type),
                   esis_pdu_type);

        printf(", v: %u%s", esis_header->version, esis_header->version == ESIS_VERSION ? "" : "unsupported" );
        printf(", checksum: 0x%04x ", EXTRACT_16BITS(esis_header->cksum));
        /* do not attempt to verify the checksum if it is zero */
        if (EXTRACT_16BITS(esis_header->cksum) == 0)
                printf("(unverified)");
        else
                printf("(%s)", osi_cksum(pptr, li) ? "incorrect" : "correct");

        printf(", holding time: %us, length indicator: %u",EXTRACT_16BITS(esis_header->holdtime),li);

        if (vflag > 1)
            print_unknown_data(optr,"\n\t",sizeof(struct esis_header_t));

	pptr += sizeof(struct esis_header_t);
	li -= sizeof(struct esis_header_t);

	switch (esis_pdu_type) {
	case ESIS_PDU_REDIRECT: {
		const u_int8_t *dst, *snpa, *neta;
		u_int dstl, snpal, netal;

		TCHECK(*pptr);
		if (li < 1) {
			printf(", bad redirect/li");
			return;
		}
		dstl = *pptr;
		pptr++;
		li--;
		TCHECK2(*pptr, dstl);
		if (li < dstl) {
			printf(", bad redirect/li");
			return;
		}
		dst = pptr;
		pptr += dstl;
                li -= dstl;
		printf("\n\t  %s", isonsap_string(dst,dstl));

		TCHECK(*pptr);
		if (li < 1) {
			printf(", bad redirect/li");
			return;
		}
		snpal = *pptr;
		pptr++;
		li--;
		TCHECK2(*pptr, snpal);
		if (li < snpal) {
			printf(", bad redirect/li");
			return;
		}
		snpa = pptr;
		pptr += snpal;
                li -= snpal;
		TCHECK(*pptr);
		if (li < 1) {
			printf(", bad redirect/li");
			return;
		}
		netal = *pptr;
		pptr++;
		TCHECK2(*pptr, netal);
		if (li < netal) {
			printf(", bad redirect/li");
			return;
		}
		neta = pptr;
		pptr += netal;
                li -= netal;

		if (netal == 0)
			printf("\n\t  %s", etheraddr_string(snpa));
		else
			printf("\n\t  %s", isonsap_string(neta,netal));
		break;
	}

	case ESIS_PDU_ESH:
            TCHECK(*pptr);
            if (li < 1) {
                printf(", bad esh/li");
                return;
            }
            source_address_number = *pptr;
            pptr++;
            li--;

            printf("\n\t  Number of Source Addresses: %u", source_address_number);
           
            while (source_address_number > 0) {
                TCHECK(*pptr);
            	if (li < 1) {
                    printf(", bad esh/li");
            	    return;
            	}
                source_address_length = *pptr;
                pptr++;
            	li--;

                TCHECK2(*pptr, source_address_length);
            	if (li < source_address_length) {
                    printf(", bad esh/li");
            	    return;
            	}
                printf("\n\t  NET (length: %u): %s",
                       source_address_length,
                       isonsap_string(pptr,source_address_length));
                pptr += source_address_length;
                li -= source_address_length;
                source_address_number--;
            }

            break;

	case ESIS_PDU_ISH: {
            TCHECK(*pptr);
            if (li < 1) {
                printf(", bad ish/li");
                return;
            }
            source_address_length = *pptr;
            pptr++;
            li--;
            TCHECK2(*pptr, source_address_length);
            if (li < source_address_length) {
                printf(", bad ish/li");
                return;
            }
            printf("\n\t  NET (length: %u): %s", source_address_length, isonsap_string(pptr, source_address_length));
            pptr += source_address_length;
            li -= source_address_length;
            break;
	}

	default:
            if (vflag <= 1) {
		    if (pptr < snapend) 
                            print_unknown_data(pptr,"\n\t  ",snapend-pptr);
            }
            return;
	}

        /* now walk the options */
        while (li >= 2) {
            u_int op, opli;
            const u_int8_t *tptr;
            
            TCHECK2(*pptr, 2);
            if (li < 2) {
                printf(", bad opts/li");
                return;
            }
            op = *pptr++;
            opli = *pptr++;
            li -= 2;
            if (opli > li) {
                printf(", opt (%d) too long", op);
                return;
            }
            li -= opli;
            tptr = pptr;
            
            printf("\n\t  %s Option #%u, length %u, value: ",
                   tok2str(esis_option_values,"Unknown",op),
                   op,
                   opli);

            switch (op) {

            case ESIS_OPTION_ES_CONF_TIME:
                TCHECK2(*pptr, 2);
                printf("%us", EXTRACT_16BITS(tptr));
                break;

            case ESIS_OPTION_PROTOCOLS:
                while (opli>0) {
                    TCHECK(*pptr);
                    printf("%s (0x%02x)",
                           tok2str(nlpid_values,
                                   "unknown",
                                   *tptr),
                           *tptr);
                    if (opli>1) /* further NPLIDs ? - put comma */
                        printf(", ");
                    tptr++;
                    opli--;
                }
                break;

                /*
                 * FIXME those are the defined Options that lack a decoder
                 * you are welcome to contribute code ;-)
                 */

            case ESIS_OPTION_QOS_MAINTENANCE:
            case ESIS_OPTION_SECURITY:
            case ESIS_OPTION_PRIORITY:
            case ESIS_OPTION_ADDRESS_MASK:
            case ESIS_OPTION_SNPA_MASK:

            default:
                print_unknown_data(tptr,"\n\t  ",opli);
                break;
            }
            if (vflag > 1)
                print_unknown_data(pptr,"\n\t  ",opli);
            pptr += opli;
        }
trunc:
	return;
}   

/* shared routine for printing system, node and lsp-ids */
static char *
isis_print_id(const u_int8_t *cp, int id_len)
{
    int i;
    static char id[sizeof("xxxx.xxxx.xxxx.yy-zz")];
    char *pos = id;

    for (i = 1; i <= SYSTEM_ID_LEN; i++) {
        snprintf(pos, sizeof(id) - (pos - id), "%02x", *cp++);
	pos += strlen(pos);
	if (i == 2 || i == 4)
	    *pos++ = '.';
	}
    if (id_len >= NODE_ID_LEN) {
        snprintf(pos, sizeof(id) - (pos - id), ".%02x", *cp++);
	pos += strlen(pos);
    }
    if (id_len == LSP_ID_LEN)
        snprintf(pos, sizeof(id) - (pos - id), "-%02x", *cp);
    return (id);
}

/* print the 4-byte metric block which is common found in the old-style TLVs */
static int
isis_print_metric_block (const struct isis_metric_block *isis_metric_block)
{
    printf(", Default Metric: %d, %s",
           ISIS_LSP_TLV_METRIC_VALUE(isis_metric_block->metric_default),
           ISIS_LSP_TLV_METRIC_IE(isis_metric_block->metric_default) ? "External" : "Internal");
    if (!ISIS_LSP_TLV_METRIC_SUPPORTED(isis_metric_block->metric_delay))
        printf("\n\t\t  Delay Metric: %d, %s",
               ISIS_LSP_TLV_METRIC_VALUE(isis_metric_block->metric_delay),
               ISIS_LSP_TLV_METRIC_IE(isis_metric_block->metric_delay) ? "External" : "Internal");
    if (!ISIS_LSP_TLV_METRIC_SUPPORTED(isis_metric_block->metric_expense))
        printf("\n\t\t  Expense Metric: %d, %s",
               ISIS_LSP_TLV_METRIC_VALUE(isis_metric_block->metric_expense),
               ISIS_LSP_TLV_METRIC_IE(isis_metric_block->metric_expense) ? "External" : "Internal");
    if (!ISIS_LSP_TLV_METRIC_SUPPORTED(isis_metric_block->metric_error))
        printf("\n\t\t  Error Metric: %d, %s",
               ISIS_LSP_TLV_METRIC_VALUE(isis_metric_block->metric_error),
               ISIS_LSP_TLV_METRIC_IE(isis_metric_block->metric_error) ? "External" : "Internal");

    return(1); /* everything is ok */
}

static int
isis_print_tlv_ip_reach (const u_int8_t *cp, const char *ident, int length)
{
	int prefix_len;
	const struct isis_tlv_ip_reach *tlv_ip_reach;

	tlv_ip_reach = (const struct isis_tlv_ip_reach *)cp;

	while (length > 0) {
		if ((size_t)length < sizeof(*tlv_ip_reach)) {
			printf("short IPv4 Reachability (%d vs %lu)",
                               length,
                               (unsigned long)sizeof(*tlv_ip_reach));
			return (0);
		}

		if (!TTEST(*tlv_ip_reach))
		    return (0);

		prefix_len = mask2plen(EXTRACT_32BITS(tlv_ip_reach->mask));

		if (prefix_len == -1)
			printf("%sIPv4 prefix: %s mask %s",
                               ident,
			       ipaddr_string((tlv_ip_reach->prefix)),
			       ipaddr_string((tlv_ip_reach->mask)));
		else
			printf("%sIPv4 prefix: %15s/%u",
                               ident,
			       ipaddr_string((tlv_ip_reach->prefix)),
			       prefix_len);

		printf(", Distribution: %s, Metric: %u, %s",
                       ISIS_LSP_TLV_METRIC_UPDOWN(tlv_ip_reach->isis_metric_block.metric_default) ? "down" : "up",
                       ISIS_LSP_TLV_METRIC_VALUE(tlv_ip_reach->isis_metric_block.metric_default),
                       ISIS_LSP_TLV_METRIC_IE(tlv_ip_reach->isis_metric_block.metric_default) ? "External" : "Internal");

		if (!ISIS_LSP_TLV_METRIC_SUPPORTED(tlv_ip_reach->isis_metric_block.metric_delay))
                    printf("%s  Delay Metric: %u, %s",
                           ident,
                           ISIS_LSP_TLV_METRIC_VALUE(tlv_ip_reach->isis_metric_block.metric_delay),
                           ISIS_LSP_TLV_METRIC_IE(tlv_ip_reach->isis_metric_block.metric_delay) ? "External" : "Internal");
                
		if (!ISIS_LSP_TLV_METRIC_SUPPORTED(tlv_ip_reach->isis_metric_block.metric_expense))
                    printf("%s  Expense Metric: %u, %s",
                           ident,
                           ISIS_LSP_TLV_METRIC_VALUE(tlv_ip_reach->isis_metric_block.metric_expense),
                           ISIS_LSP_TLV_METRIC_IE(tlv_ip_reach->isis_metric_block.metric_expense) ? "External" : "Internal");
                
		if (!ISIS_LSP_TLV_METRIC_SUPPORTED(tlv_ip_reach->isis_metric_block.metric_error))
                    printf("%s  Error Metric: %u, %s",
                           ident,
                           ISIS_LSP_TLV_METRIC_VALUE(tlv_ip_reach->isis_metric_block.metric_error),
                           ISIS_LSP_TLV_METRIC_IE(tlv_ip_reach->isis_metric_block.metric_error) ? "External" : "Internal");

		length -= sizeof(struct isis_tlv_ip_reach);
		tlv_ip_reach++;
	}
	return (1);
}

/*
 * this is the common IP-REACH subTLV decoder it is called
 * from various EXTD-IP REACH TLVs (135,235,236,237)
 */

static int
isis_print_ip_reach_subtlv (const u_int8_t *tptr,int subt,int subl,const char *ident) {

        /* first lets see if we know the subTLVs name*/
	printf("%s%s subTLV #%u, length: %u",
	       ident,
               tok2str(isis_ext_ip_reach_subtlv_values,
                       "unknown",
                       subt),
               subt,
               subl);

	if (!TTEST2(*tptr,subl))
	    goto trunctlv;

    switch(subt) {
    case ISIS_SUBTLV_EXTD_IP_REACH_MGMT_PREFIX_COLOR: /* fall through */
    case ISIS_SUBTLV_EXTD_IP_REACH_ADMIN_TAG32:
        while (subl >= 4) {
	    printf(", 0x%08x (=%u)",
		   EXTRACT_32BITS(tptr),
		   EXTRACT_32BITS(tptr));
	    tptr+=4;
	    subl-=4;
	}
	break;
    case ISIS_SUBTLV_EXTD_IP_REACH_ADMIN_TAG64:
        while (subl >= 8) {
	    printf(", 0x%08x%08x",
		   EXTRACT_32BITS(tptr),
		   EXTRACT_32BITS(tptr+4));
	    tptr+=8;
	    subl-=8;
	}
	break;
    default:
	if(!print_unknown_data(tptr,"\n\t\t    ",
			       subl))
	  return(0);
	break;
    }
    return(1);
	
trunctlv:
    printf("%spacket exceeded snapshot",ident);
    return(0);
}

/*
 * this is the common IS-REACH subTLV decoder it is called
 * from isis_print_ext_is_reach()
 */

static int
isis_print_is_reach_subtlv (const u_int8_t *tptr,u_int subt,u_int subl,const char *ident) {

        u_int te_class,priority_level;
        union { /* int to float conversion buffer for several subTLVs */
            float f; 
            u_int32_t i;
        } bw;

        /* first lets see if we know the subTLVs name*/
	printf("%s%s subTLV #%u, length: %u",
	       ident,
               tok2str(isis_ext_is_reach_subtlv_values,
                       "unknown",
                       subt),
               subt,
               subl);

	if (!TTEST2(*tptr,subl))
	    goto trunctlv;

        switch(subt) {
        case ISIS_SUBTLV_EXT_IS_REACH_ADMIN_GROUP:      
        case ISIS_SUBTLV_EXT_IS_REACH_LINK_LOCAL_REMOTE_ID:
        case ISIS_SUBTLV_EXT_IS_REACH_LINK_REMOTE_ID:
	    if (subl >= 4) {
	      printf(", 0x%08x", EXTRACT_32BITS(tptr));
	      if (subl == 8) /* draft-ietf-isis-gmpls-extensions */
	        printf(", 0x%08x", EXTRACT_32BITS(tptr+4));
	    }
	    break;
        case ISIS_SUBTLV_EXT_IS_REACH_IPV4_INTF_ADDR:
        case ISIS_SUBTLV_EXT_IS_REACH_IPV4_NEIGHBOR_ADDR:
            if (subl >= sizeof(struct in_addr))
              printf(", %s", ipaddr_string(tptr));
            break;
        case ISIS_SUBTLV_EXT_IS_REACH_MAX_LINK_BW :
	case ISIS_SUBTLV_EXT_IS_REACH_RESERVABLE_BW:  
            if (subl >= 4) {
              bw.i = EXTRACT_32BITS(tptr);
              printf(", %.3f Mbps", bw.f*8/1000000 );
            }
            break;
        case ISIS_SUBTLV_EXT_IS_REACH_UNRESERVED_BW :
            if (subl >= 32) {
              for (te_class = 0; te_class < 8; te_class++) {
                bw.i = EXTRACT_32BITS(tptr);
                printf("%s  TE-Class %u: %.3f Mbps",
                       ident,
                       te_class,
                       bw.f*8/1000000 );
		tptr+=4;
	      }
            }
            break;
        case ISIS_SUBTLV_EXT_IS_REACH_BW_CONSTRAINTS: /* fall through */
        case ISIS_SUBTLV_EXT_IS_REACH_BW_CONSTRAINTS_OLD:
            printf("%sBandwidth Constraints Model ID: %s (%u)",
                   ident,
                   tok2str(diffserv_te_bc_values, "unknown", *tptr),
                   *tptr);
            tptr++;
            /* decode BCs until the subTLV ends */
            for (te_class = 0; te_class < (subl-1)/4; te_class++) {
                bw.i = EXTRACT_32BITS(tptr);
                printf("%s  Bandwidth constraint CT%u: %.3f Mbps",
                       ident,
                       te_class,
                       bw.f*8/1000000 );
		tptr+=4;
            }
            break;
        case ISIS_SUBTLV_EXT_IS_REACH_TE_METRIC:
            if (subl >= 3)
              printf(", %u", EXTRACT_24BITS(tptr));
            break;
        case ISIS_SUBTLV_EXT_IS_REACH_LINK_PROTECTION_TYPE:
            if (subl >= 2) {
              printf(", %s, Priority %u",
		   bittok2str(gmpls_link_prot_values, "none", *tptr),
                   *(tptr+1));
            }
            break;
        case ISIS_SUBTLV_EXT_IS_REACH_INTF_SW_CAP_DESCR:
            if (subl >= 36) {
              printf("%s  Interface Switching Capability:%s",
                   ident,
                   tok2str(gmpls_switch_cap_values, "Unknown", *(tptr)));
              printf(", LSP Encoding: %s",
                   tok2str(gmpls_encoding_values, "Unknown", *(tptr+1)));
	      tptr+=4;
              printf("%s  Max LSP Bandwidth:",ident);
              for (priority_level = 0; priority_level < 8; priority_level++) {
                bw.i = EXTRACT_32BITS(tptr);
                printf("%s    priority level %d: %.3f Mbps",
                       ident,
                       priority_level,
                       bw.f*8/1000000 );
		tptr+=4;
              }
              subl-=36;
              /* there is some optional stuff left to decode but this is as of yet
                 not specified so just lets hexdump what is left */
              if(subl>0){
                if(!print_unknown_data(tptr,"\n\t\t    ",
				       subl))
                    return(0);
              }
            }
            break;
        default:
            if(!print_unknown_data(tptr,"\n\t\t    ",
				   subl))
                return(0);
            break;
        }
        return(1);

trunctlv:
    printf("%spacket exceeded snapshot",ident);
    return(0);
}


/*
 * this is the common IS-REACH decoder it is called
 * from various EXTD-IS REACH style TLVs (22,24,222)
 */

static int
isis_print_ext_is_reach (const u_int8_t *tptr,const char *ident, int tlv_type) {

    char ident_buffer[20];
    int subtlv_type,subtlv_len,subtlv_sum_len;
    int proc_bytes = 0; /* how many bytes did we process ? */
    
    if (!TTEST2(*tptr, NODE_ID_LEN))
        return(0);

    printf("%sIS Neighbor: %s", ident, isis_print_id(tptr, NODE_ID_LEN));
    tptr+=(NODE_ID_LEN);

    if (tlv_type != ISIS_TLV_IS_ALIAS_ID) { /* the Alias TLV Metric field is implicit 0 */
        if (!TTEST2(*tptr, 3))    /* and is therefore skipped */
	    return(0);
	printf(", Metric: %d",EXTRACT_24BITS(tptr));
	tptr+=3;
    }
        
    if (!TTEST2(*tptr, 1))
        return(0);
    subtlv_sum_len=*(tptr++); /* read out subTLV length */
    proc_bytes=NODE_ID_LEN+3+1;
    printf(", %ssub-TLVs present",subtlv_sum_len ? "" : "no ");
    if (subtlv_sum_len) {
        printf(" (%u)",subtlv_sum_len);
        while (subtlv_sum_len>0) {
            if (!TTEST2(*tptr,2))
                return(0);
            subtlv_type=*(tptr++);
            subtlv_len=*(tptr++);
            /* prepend the ident string */
            snprintf(ident_buffer, sizeof(ident_buffer), "%s  ",ident);
            if(!isis_print_is_reach_subtlv(tptr,subtlv_type,subtlv_len,ident_buffer))
                return(0);
            tptr+=subtlv_len;
            subtlv_sum_len-=(subtlv_len+2);
            proc_bytes+=(subtlv_len+2);
        }
    }
    return(proc_bytes);
}

/*
 * this is the common Multi Topology ID decoder
 * it is called from various MT-TLVs (222,229,235,237)
 */

static int
isis_print_mtid (const u_int8_t *tptr,const char *ident) {
    
    if (!TTEST2(*tptr, 2))
        return(0);

    printf("%s%s",
           ident,
           tok2str(isis_mt_values,
                   "Reserved for IETF Consensus",
                   ISIS_MASK_MTID(EXTRACT_16BITS(tptr))));

    printf(" Topology (0x%03x), Flags: [%s]",
           ISIS_MASK_MTID(EXTRACT_16BITS(tptr)),
           bittok2str(isis_mt_flag_values, "none",ISIS_MASK_MTFLAGS(EXTRACT_16BITS(tptr))));

    return(2);
}

/*
 * this is the common extended IP reach decoder
 * it is called from TLVs (135,235,236,237)
 * we process the TLV and optional subTLVs and return
 * the amount of processed bytes
 */

static int
isis_print_extd_ip_reach (const u_int8_t *tptr, const char *ident, u_int16_t afi) {

    char ident_buffer[20];
#ifdef INET6
    u_int8_t prefix[sizeof(struct in6_addr)]; /* shared copy buffer for IPv4 and IPv6 prefixes */
#else
    u_int8_t prefix[sizeof(struct in_addr)]; /* shared copy buffer for IPv4 prefixes */
#endif
    u_int metric, status_byte, bit_length, byte_length, sublen, processed, subtlvtype, subtlvlen;

    if (!TTEST2(*tptr, 4))
        return (0);
    metric = EXTRACT_32BITS(tptr);
    processed=4;
    tptr+=4;
    
    if (afi == IPV4) {
        if (!TTEST2(*tptr, 1)) /* fetch status byte */
            return (0);
        status_byte=*(tptr++);
        bit_length = status_byte&0x3f;
        if (bit_length > 32) {
            printf("%sIPv4 prefix: bad bit length %u",
                   ident,
                   bit_length);
            return (0);
        }
        processed++;
#ifdef INET6
    } else if (afi == IPV6) {
        if (!TTEST2(*tptr, 1)) /* fetch status & prefix_len byte */
            return (0);
        status_byte=*(tptr++);
        bit_length=*(tptr++);
        if (bit_length > 128) {
            printf("%sIPv6 prefix: bad bit length %u",
                   ident,
                   bit_length);
            return (0);
        }
        processed+=2;
#endif
    } else
        return (0); /* somebody is fooling us */

    byte_length = (bit_length + 7) / 8; /* prefix has variable length encoding */
   
    if (!TTEST2(*tptr, byte_length))
        return (0);
    memset(prefix, 0, sizeof prefix);   /* clear the copy buffer */
    memcpy(prefix,tptr,byte_length);    /* copy as much as is stored in the TLV */
    tptr+=byte_length;
    processed+=byte_length;

    if (afi == IPV4)
        printf("%sIPv4 prefix: %15s/%u",
               ident,
               ipaddr_string(prefix),
               bit_length);
#ifdef INET6
    if (afi == IPV6)
        printf("%sIPv6 prefix: %s/%u",
               ident,
               ip6addr_string(prefix),
               bit_length);
#endif 
   
    printf(", Distribution: %s, Metric: %u",
           ISIS_MASK_TLV_EXTD_IP_UPDOWN(status_byte) ? "down" : "up",
           metric);

    if (afi == IPV4 && ISIS_MASK_TLV_EXTD_IP_SUBTLV(status_byte))
        printf(", sub-TLVs present");
#ifdef INET6
    if (afi == IPV6)
        printf(", %s%s",
               ISIS_MASK_TLV_EXTD_IP6_IE(status_byte) ? "External" : "Internal",
               ISIS_MASK_TLV_EXTD_IP6_SUBTLV(status_byte) ? ", sub-TLVs present" : "");
#endif
    
    if ((ISIS_MASK_TLV_EXTD_IP_SUBTLV(status_byte)  && afi == IPV4) ||
        (ISIS_MASK_TLV_EXTD_IP6_SUBTLV(status_byte) && afi == IPV6)) {
        /* assume that one prefix can hold more
           than one subTLV - therefore the first byte must reflect
           the aggregate bytecount of the subTLVs for this prefix
        */
        if (!TTEST2(*tptr, 1))
            return (0);
        sublen=*(tptr++);
        processed+=sublen+1;
        printf(" (%u)",sublen);   /* print out subTLV length */
        
        while (sublen>0) {
            if (!TTEST2(*tptr,2))
                return (0);
            subtlvtype=*(tptr++);
            subtlvlen=*(tptr++);
            /* prepend the ident string */
            snprintf(ident_buffer, sizeof(ident_buffer), "%s  ",ident);
            if(!isis_print_ip_reach_subtlv(tptr,subtlvtype,subtlvlen,ident_buffer))
                return(0);
            tptr+=subtlvlen;
            sublen-=(subtlvlen+2);
        }
    }
    return (processed);
}

/*
 * isis_print
 * Decode IS-IS packets.  Return 0 on error.
 */

static int isis_print (const u_int8_t *p, u_int length)
{
    const struct isis_common_header *isis_header;

    const struct isis_iih_lan_header *header_iih_lan;
    const struct isis_iih_ptp_header *header_iih_ptp;
    const struct isis_lsp_header *header_lsp;
    const struct isis_csnp_header *header_csnp;
    const struct isis_psnp_header *header_psnp;

    const struct isis_tlv_lsp *tlv_lsp;
    const struct isis_tlv_ptp_adj *tlv_ptp_adj;
    const struct isis_tlv_is_reach *tlv_is_reach;
    const struct isis_tlv_es_reach *tlv_es_reach;

    u_int8_t pdu_type, max_area, id_length, tlv_type, tlv_len, tmp, alen, lan_alen, prefix_len;
    u_int8_t ext_is_len, ext_ip_len, mt_len;
    const u_int8_t *optr, *pptr, *tptr;
    u_short packet_len,pdu_len;
    u_int i,vendor_id;

    packet_len=length;
    optr = p; /* initialize the _o_riginal pointer to the packet start -
                 need it for parsing the checksum TLV */
    isis_header = (const struct isis_common_header *)p;
    TCHECK(*isis_header);
    pptr = p+(ISIS_COMMON_HEADER_SIZE);
    header_iih_lan = (const struct isis_iih_lan_header *)pptr;
    header_iih_ptp = (const struct isis_iih_ptp_header *)pptr;
    header_lsp = (const struct isis_lsp_header *)pptr;
    header_csnp = (const struct isis_csnp_header *)pptr;
    header_psnp = (const struct isis_psnp_header *)pptr;

    if (!eflag)
        printf("IS-IS");

    /*
     * Sanity checking of the header.
     */

    if (isis_header->version != ISIS_VERSION) {
	printf("version %d packet not supported", isis_header->version);
	return (0);
    }

    if ((isis_header->id_length != SYSTEM_ID_LEN) && (isis_header->id_length != 0)) {
	printf("system ID length of %d is not supported",
	       isis_header->id_length);
	return (0);
    }

    if (isis_header->pdu_version != ISIS_VERSION) {
	printf("version %d packet not supported", isis_header->pdu_version);
	return (0);
    }

    max_area = isis_header->max_area;
    switch(max_area) {
    case 0:
	max_area = 3;	 /* silly shit */
	break;
    case 255:
	printf("bad packet -- 255 areas");
	return (0);
    default:
	break;
    }

    id_length = isis_header->id_length;
    switch(id_length) {
    case 0:
        id_length = 6;	 /* silly shit again */
	break;
    case 1:              /* 1-8 are valid sys-ID lenghts */
    case 2:
    case 3:
    case 4:
    case 5:
    case 6:
    case 7:
    case 8:
        break;
    case 255:
        id_length = 0;   /* entirely useless */
	break;
    default:
        break;
    }

    /* toss any non 6-byte sys-ID len PDUs */
    if (id_length != 6 ) { 
	printf("bad packet -- illegal sys-ID length (%u)", id_length);
	return (0);
    }

    pdu_type=isis_header->pdu_type;

    /* in non-verbose mode print the basic PDU Type plus PDU specific brief information*/
    if (vflag < 1) {
        printf("%s%s",
               eflag ? "" : ", ",
               tok2str(isis_pdu_values,"unknown PDU-Type %u",pdu_type));

	switch (pdu_type) {

	case ISIS_PDU_L1_LAN_IIH:
	case ISIS_PDU_L2_LAN_IIH:
	    printf(", src-id %s",
                   isis_print_id(header_iih_lan->source_id,SYSTEM_ID_LEN));
	    printf(", lan-id %s, prio %u",
                   isis_print_id(header_iih_lan->lan_id,NODE_ID_LEN),
                   header_iih_lan->priority);
	    break;
	case ISIS_PDU_PTP_IIH:
	    printf(", src-id %s", isis_print_id(header_iih_ptp->source_id,SYSTEM_ID_LEN));
	    break;
	case ISIS_PDU_L1_LSP:
	case ISIS_PDU_L2_LSP:
	    printf(", lsp-id %s, seq 0x%08x, lifetime %5us",
		   isis_print_id(header_lsp->lsp_id, LSP_ID_LEN),
		   EXTRACT_32BITS(header_lsp->sequence_number),
		   EXTRACT_16BITS(header_lsp->remaining_lifetime));
	    break;
	case ISIS_PDU_L1_CSNP:
	case ISIS_PDU_L2_CSNP:
	    printf(", src-id %s", isis_print_id(header_csnp->source_id,NODE_ID_LEN));
	    break;
	case ISIS_PDU_L1_PSNP:
	case ISIS_PDU_L2_PSNP:
	    printf(", src-id %s", isis_print_id(header_psnp->source_id,NODE_ID_LEN));
	    break;

	}
	printf(", length %u", length);

        return(1);
    }

    /* ok they seem to want to know everything - lets fully decode it */
    printf("%slength %u", eflag ? "" : ", ",length);

    printf("\n\t%s, hlen: %u, v: %u, pdu-v: %u, sys-id-len: %u (%u), max-area: %u (%u)",
           tok2str(isis_pdu_values,
                   "unknown, type %u",
                   pdu_type),
           isis_header->fixed_len,
           isis_header->version,
           isis_header->pdu_version,
	   id_length,
	   isis_header->id_length,
           max_area,
           isis_header->max_area);

    if (vflag > 1) {
        if(!print_unknown_data(optr,"\n\t",8)) /* provide the _o_riginal pointer */
            return(0);                         /* for optionally debugging the common header */
    }

    switch (pdu_type) {

    case ISIS_PDU_L1_LAN_IIH:
    case ISIS_PDU_L2_LAN_IIH:
	if (isis_header->fixed_len != (ISIS_COMMON_HEADER_SIZE+ISIS_IIH_LAN_HEADER_SIZE)) {
	    printf(", bogus fixed header length %u should be %lu",
		   isis_header->fixed_len, (unsigned long)ISIS_IIH_LAN_HEADER_SIZE);
	    return (0);
	}

	pdu_len=EXTRACT_16BITS(header_iih_lan->pdu_len);
	if (packet_len>pdu_len) {
            packet_len=pdu_len; /* do TLV decoding as long as it makes sense */
            length=pdu_len;
	}

	TCHECK(*header_iih_lan);
	printf("\n\t  source-id: %s,  holding time: %us, Flags: [%s]",
               isis_print_id(header_iih_lan->source_id,SYSTEM_ID_LEN),
               EXTRACT_16BITS(header_iih_lan->holding_time),
               tok2str(isis_iih_circuit_type_values,
                       "unknown circuit type 0x%02x",
                       header_iih_lan->circuit_type));

	printf("\n\t  lan-id:    %s, Priority: %u, PDU length: %u",
               isis_print_id(header_iih_lan->lan_id, NODE_ID_LEN),
               (header_iih_lan->priority) & ISIS_LAN_PRIORITY_MASK,
               pdu_len);

        if (vflag > 1) {
            if(!print_unknown_data(pptr,"\n\t  ",ISIS_IIH_LAN_HEADER_SIZE))
                return(0);
        }

	packet_len -= (ISIS_COMMON_HEADER_SIZE+ISIS_IIH_LAN_HEADER_SIZE);
	pptr = p + (ISIS_COMMON_HEADER_SIZE+ISIS_IIH_LAN_HEADER_SIZE);
	break;

    case ISIS_PDU_PTP_IIH:
	if (isis_header->fixed_len != (ISIS_COMMON_HEADER_SIZE+ISIS_IIH_PTP_HEADER_SIZE)) {
	    printf(", bogus fixed header length %u should be %lu",
		   isis_header->fixed_len, (unsigned long)ISIS_IIH_PTP_HEADER_SIZE);
	    return (0);
	}

	pdu_len=EXTRACT_16BITS(header_iih_ptp->pdu_len);
	if (packet_len>pdu_len) {
            packet_len=pdu_len; /* do TLV decoding as long as it makes sense */
            length=pdu_len;
	}

	TCHECK(*header_iih_ptp);
	printf("\n\t  source-id: %s, holding time: %us, Flags: [%s]",
               isis_print_id(header_iih_ptp->source_id,SYSTEM_ID_LEN),
               EXTRACT_16BITS(header_iih_ptp->holding_time),
               tok2str(isis_iih_circuit_type_values,
                       "unknown circuit type 0x%02x",
                       header_iih_ptp->circuit_type));

	printf("\n\t  circuit-id: 0x%02x, PDU length: %u",
               header_iih_ptp->circuit_id,
               pdu_len);

        if (vflag > 1) {
            if(!print_unknown_data(pptr,"\n\t  ",ISIS_IIH_PTP_HEADER_SIZE))
                return(0);
        }

	packet_len -= (ISIS_COMMON_HEADER_SIZE+ISIS_IIH_PTP_HEADER_SIZE);
	pptr = p + (ISIS_COMMON_HEADER_SIZE+ISIS_IIH_PTP_HEADER_SIZE);
	break;

    case ISIS_PDU_L1_LSP:
    case ISIS_PDU_L2_LSP:
	if (isis_header->fixed_len != (ISIS_COMMON_HEADER_SIZE+ISIS_LSP_HEADER_SIZE)) {
	    printf(", bogus fixed header length %u should be %lu",
		   isis_header->fixed_len, (unsigned long)ISIS_LSP_HEADER_SIZE);
	    return (0);
	}

	pdu_len=EXTRACT_16BITS(header_lsp->pdu_len);
	if (packet_len>pdu_len) {
            packet_len=pdu_len; /* do TLV decoding as long as it makes sense */
            length=pdu_len;
	}

	TCHECK(*header_lsp);
	printf("\n\t  lsp-id: %s, seq: 0x%08x, lifetime: %5us\n\t  chksum: 0x%04x",
               isis_print_id(header_lsp->lsp_id, LSP_ID_LEN),
               EXTRACT_32BITS(header_lsp->sequence_number),
               EXTRACT_16BITS(header_lsp->remaining_lifetime),
               EXTRACT_16BITS(header_lsp->checksum));

        /* if this is a purge do not attempt to verify the checksum */
        if ( EXTRACT_16BITS(header_lsp->remaining_lifetime) == 0 &&
             EXTRACT_16BITS(header_lsp->checksum) == 0)
            printf(" (purged)");
        else
            /* verify the checksum -
             * checking starts at the lsp-id field at byte position [12]
             * hence the length needs to be reduced by 12 bytes */
            printf(" (%s)", (osi_cksum((u_int8_t *)header_lsp->lsp_id, length-12)) ? "incorrect" : "correct");

	printf(", PDU length: %u, Flags: [ %s",
               pdu_len,
               ISIS_MASK_LSP_OL_BIT(header_lsp->typeblock) ? "Overload bit set, " : "");

	if (ISIS_MASK_LSP_ATT_BITS(header_lsp->typeblock)) {
	    printf("%s", ISIS_MASK_LSP_ATT_DEFAULT_BIT(header_lsp->typeblock) ? "default " : "");
	    printf("%s", ISIS_MASK_LSP_ATT_DELAY_BIT(header_lsp->typeblock) ? "delay " : "");
	    printf("%s", ISIS_MASK_LSP_ATT_EXPENSE_BIT(header_lsp->typeblock) ? "expense " : "");
	    printf("%s", ISIS_MASK_LSP_ATT_ERROR_BIT(header_lsp->typeblock) ? "error " : "");
	    printf("ATT bit set, ");
	}
	printf("%s", ISIS_MASK_LSP_PARTITION_BIT(header_lsp->typeblock) ? "P bit set, " : "");
	printf("%s ]", tok2str(isis_lsp_istype_values,"Unknown(0x%x)",ISIS_MASK_LSP_ISTYPE_BITS(header_lsp->typeblock)));

        if (vflag > 1) {
            if(!print_unknown_data(pptr,"\n\t  ",ISIS_LSP_HEADER_SIZE))
                return(0);
        }

	packet_len -= (ISIS_COMMON_HEADER_SIZE+ISIS_LSP_HEADER_SIZE);
	pptr = p + (ISIS_COMMON_HEADER_SIZE+ISIS_LSP_HEADER_SIZE);
	break;

    case ISIS_PDU_L1_CSNP:
    case ISIS_PDU_L2_CSNP:
	if (isis_header->fixed_len != (ISIS_COMMON_HEADER_SIZE+ISIS_CSNP_HEADER_SIZE)) {
	    printf(", bogus fixed header length %u should be %lu",
		   isis_header->fixed_len, (unsigned long)ISIS_CSNP_HEADER_SIZE);
	    return (0);
	}

	pdu_len=EXTRACT_16BITS(header_csnp->pdu_len);
	if (packet_len>pdu_len) {
            packet_len=pdu_len; /* do TLV decoding as long as it makes sense */
            length=pdu_len;
	}

	TCHECK(*header_csnp);
	printf("\n\t  source-id:    %s, PDU length: %u",
               isis_print_id(header_csnp->source_id, NODE_ID_LEN),
               pdu_len);
	printf("\n\t  start lsp-id: %s",
               isis_print_id(header_csnp->start_lsp_id, LSP_ID_LEN));
	printf("\n\t  end lsp-id:   %s",
               isis_print_id(header_csnp->end_lsp_id, LSP_ID_LEN));

        if (vflag > 1) {
            if(!print_unknown_data(pptr,"\n\t  ",ISIS_CSNP_HEADER_SIZE))
                return(0);
        }

	packet_len -= (ISIS_COMMON_HEADER_SIZE+ISIS_CSNP_HEADER_SIZE);
	pptr = p + (ISIS_COMMON_HEADER_SIZE+ISIS_CSNP_HEADER_SIZE);
        break;

    case ISIS_PDU_L1_PSNP:
    case ISIS_PDU_L2_PSNP:
	if (isis_header->fixed_len != (ISIS_COMMON_HEADER_SIZE+ISIS_PSNP_HEADER_SIZE)) {
	    printf("- bogus fixed header length %u should be %lu",
		   isis_header->fixed_len, (unsigned long)ISIS_PSNP_HEADER_SIZE);
	    return (0);
	}

	pdu_len=EXTRACT_16BITS(header_psnp->pdu_len);
	if (packet_len>pdu_len) {
            packet_len=pdu_len; /* do TLV decoding as long as it makes sense */
            length=pdu_len;
	}

	TCHECK(*header_psnp);
	printf("\n\t  source-id:    %s, PDU length: %u",
               isis_print_id(header_psnp->source_id, NODE_ID_LEN),
               pdu_len);

        if (vflag > 1) {
            if(!print_unknown_data(pptr,"\n\t  ",ISIS_PSNP_HEADER_SIZE))
                return(0);
        }

	packet_len -= (ISIS_COMMON_HEADER_SIZE+ISIS_PSNP_HEADER_SIZE);
	pptr = p + (ISIS_COMMON_HEADER_SIZE+ISIS_PSNP_HEADER_SIZE);
	break;

    default:
	if(!print_unknown_data(pptr,"\n\t  ",length))
	    return(0);
	return (0);
    }

    /*
     * Now print the TLV's.
     */

    while (packet_len >= 2) {
        if (pptr == snapend) {
	    return (1);
        }

	if (!TTEST2(*pptr, 2)) {
	    printf("\n\t\t packet exceeded snapshot (%ld) bytes",
                   (long)(pptr-snapend));
	    return (1);
	}
	tlv_type = *pptr++;
	tlv_len = *pptr++;
        tmp =tlv_len; /* copy temporary len & pointer to packet data */
        tptr = pptr;
	packet_len -= 2;
	if (tlv_len > packet_len) {
	    break;
	}

        /* first lets see if we know the TLVs name*/
	printf("\n\t    %s TLV #%u, length: %u",
               tok2str(isis_tlv_values,
                       "unknown",
                       tlv_type),
               tlv_type,
               tlv_len);

        if (tlv_len == 0) /* something is malformed */
	    continue;

        /* now check if we have a decoder otherwise do a hexdump at the end*/
	switch (tlv_type) {
	case ISIS_TLV_AREA_ADDR:
	    if (!TTEST2(*tptr, 1))
		goto trunctlv;
	    alen = *tptr++;
	    while (tmp && alen < tmp) {
		printf("\n\t      Area address (length: %u): %s",
                       alen,
                       isonsap_string(tptr,alen));
		tptr += alen;
		tmp -= alen + 1;
		if (tmp==0) /* if this is the last area address do not attemt a boundary check */
                    break;
		if (!TTEST2(*tptr, 1))
		    goto trunctlv;
		alen = *tptr++;
	    }
	    break;
	case ISIS_TLV_ISNEIGH:
	    while (tmp >= ETHER_ADDR_LEN) {
                if (!TTEST2(*tptr, ETHER_ADDR_LEN))
                    goto trunctlv;
                printf("\n\t      SNPA: %s",isis_print_id(tptr,ETHER_ADDR_LEN));
                tmp -= ETHER_ADDR_LEN;
                tptr += ETHER_ADDR_LEN;
	    }
	    break;

        case ISIS_TLV_ISNEIGH_VARLEN:
            if (!TTEST2(*tptr, 1) || tmp < 3) /* min. TLV length */
		goto trunctlv;
	    lan_alen = *tptr++; /* LAN address length */
	    if (lan_alen == 0) {
                printf("\n\t      LAN address length 0 bytes (invalid)");
                break;
            }
            tmp --;
            printf("\n\t      LAN address length %u bytes ",lan_alen);
	    while (tmp >= lan_alen) {
                if (!TTEST2(*tptr, lan_alen))
                    goto trunctlv;
                printf("\n\t\tIS Neighbor: %s",isis_print_id(tptr,lan_alen));
                tmp -= lan_alen;
                tptr +=lan_alen;
            }
            break;

	case ISIS_TLV_PADDING:
	    break;

        case ISIS_TLV_MT_IS_REACH:
            while (tmp >= 2+NODE_ID_LEN+3+1) {
                mt_len = isis_print_mtid(tptr, "\n\t      ");
                if (mt_len == 0) /* did something go wrong ? */
                    goto trunctlv;
                tptr+=mt_len;
                tmp-=mt_len;

                ext_is_len = isis_print_ext_is_reach(tptr,"\n\t      ",tlv_type);
                if (ext_is_len == 0) /* did something go wrong ? */
                    goto trunctlv;
                   
                tmp-=ext_is_len;
                tptr+=ext_is_len;
            }
            break;

        case ISIS_TLV_IS_ALIAS_ID:
	    while (tmp >= NODE_ID_LEN+1) { /* is it worth attempting a decode ? */
	        ext_is_len = isis_print_ext_is_reach(tptr,"\n\t      ",tlv_type);
		if (ext_is_len == 0) /* did something go wrong ? */
	            goto trunctlv;
		tmp-=ext_is_len;
		tptr+=ext_is_len;
	    }
	    break;

        case ISIS_TLV_EXT_IS_REACH:
            while (tmp >= NODE_ID_LEN+3+1) { /* is it worth attempting a decode ? */
                ext_is_len = isis_print_ext_is_reach(tptr,"\n\t      ",tlv_type);
                if (ext_is_len == 0) /* did something go wrong ? */
                    goto trunctlv;                   
                tmp-=ext_is_len;
                tptr+=ext_is_len;
            }
            break;
        case ISIS_TLV_IS_REACH:
	    if (!TTEST2(*tptr,1))  /* check if there is one byte left to read out the virtual flag */
                goto trunctlv;
            printf("\n\t      %s",
                   tok2str(isis_is_reach_virtual_values,
                           "bogus virtual flag 0x%02x",
                           *tptr++));
	    tlv_is_reach = (const struct isis_tlv_is_reach *)tptr;
            while (tmp >= sizeof(struct isis_tlv_is_reach)) {
		if (!TTEST(*tlv_is_reach))
		    goto trunctlv;
		printf("\n\t      IS Neighbor: %s",
		       isis_print_id(tlv_is_reach->neighbor_nodeid, NODE_ID_LEN));
                isis_print_metric_block(&tlv_is_reach->isis_metric_block);
		tmp -= sizeof(struct isis_tlv_is_reach);
		tlv_is_reach++;
	    }
            break;

        case ISIS_TLV_ESNEIGH:
	    tlv_es_reach = (const struct isis_tlv_es_reach *)tptr;
            while (tmp >= sizeof(struct isis_tlv_es_reach)) {
		if (!TTEST(*tlv_es_reach))
		    goto trunctlv;
		printf("\n\t      ES Neighbor: %s",
                       isis_print_id(tlv_es_reach->neighbor_sysid,SYSTEM_ID_LEN));
                isis_print_metric_block(&tlv_es_reach->isis_metric_block);
		tmp -= sizeof(struct isis_tlv_es_reach);
		tlv_es_reach++;
	    }
            break;

            /* those two TLVs share the same format */
	case ISIS_TLV_INT_IP_REACH:
	case ISIS_TLV_EXT_IP_REACH:
	    if (!isis_print_tlv_ip_reach(pptr, "\n\t      ", tlv_len))
		return (1);
	    break;

	case ISIS_TLV_EXTD_IP_REACH:
	    while (tmp>0) {
                ext_ip_len = isis_print_extd_ip_reach(tptr, "\n\t      ", IPV4);
                if (ext_ip_len == 0) /* did something go wrong ? */
                    goto trunctlv;
                tptr+=ext_ip_len;
		tmp-=ext_ip_len;
	    }
	    break;

        case ISIS_TLV_MT_IP_REACH:
            mt_len = isis_print_mtid(tptr, "\n\t      ");
            if (mt_len == 0) { /* did something go wrong ? */
                goto trunctlv;
            }
            tptr+=mt_len;
            tmp-=mt_len;

            while (tmp>0) {
                ext_ip_len = isis_print_extd_ip_reach(tptr, "\n\t      ", IPV4);
                if (ext_ip_len == 0) /* did something go wrong ? */
                    goto trunctlv;
                tptr+=ext_ip_len;
		tmp-=ext_ip_len;
	    }
	    break;

#ifdef INET6
	case ISIS_TLV_IP6_REACH:
	    while (tmp>0) {
                ext_ip_len = isis_print_extd_ip_reach(tptr, "\n\t      ", IPV6);
                if (ext_ip_len == 0) /* did something go wrong ? */
                    goto trunctlv;
                tptr+=ext_ip_len;
		tmp-=ext_ip_len;
	    }
	    break;

	case ISIS_TLV_MT_IP6_REACH:
            mt_len = isis_print_mtid(tptr, "\n\t      ");
            if (mt_len == 0) { /* did something go wrong ? */
                goto trunctlv;
            }
            tptr+=mt_len;
            tmp-=mt_len;

	    while (tmp>0) {
                ext_ip_len = isis_print_extd_ip_reach(tptr, "\n\t      ", IPV6);
                if (ext_ip_len == 0) /* did something go wrong ? */
                    goto trunctlv;
                tptr+=ext_ip_len;
		tmp-=ext_ip_len;
	    }
	    break;

	case ISIS_TLV_IP6ADDR:
	    while (tmp>=sizeof(struct in6_addr)) {
		if (!TTEST2(*tptr, sizeof(struct in6_addr)))
		    goto trunctlv;

                printf("\n\t      IPv6 interface address: %s",
		       ip6addr_string(tptr));

		tptr += sizeof(struct in6_addr);
		tmp -= sizeof(struct in6_addr);
	    }
	    break;
#endif
	case ISIS_TLV_AUTH:
	    if (!TTEST2(*tptr, 1))
		goto trunctlv;

            printf("\n\t      %s: ",
                   tok2str(isis_subtlv_auth_values,
                           "unknown Authentication type 0x%02x",
                           *tptr));

	    switch (*tptr) {
	    case ISIS_SUBTLV_AUTH_SIMPLE:
		for(i=1;i<tlv_len;i++) {
		    if (!TTEST2(*(tptr+i), 1))
			goto trunctlv;
		    printf("%c",*(tptr+i));
		}
		break;
	    case ISIS_SUBTLV_AUTH_MD5:
		for(i=1;i<tlv_len;i++) {
		    if (!TTEST2(*(tptr+i), 1))
			goto trunctlv;
		    printf("%02x",*(tptr+i));
		}
		if (tlv_len != ISIS_SUBTLV_AUTH_MD5_LEN+1)
                    printf(", (malformed subTLV) ");
		break;
	    case ISIS_SUBTLV_AUTH_PRIVATE:
	    default:
		if(!print_unknown_data(tptr+1,"\n\t\t  ",tlv_len-1))
		    return(0);
		break;
	    }
	    break;

	case ISIS_TLV_PTP_ADJ:
	    tlv_ptp_adj = (const struct isis_tlv_ptp_adj *)tptr;
	    if(tmp>=1) {
		if (!TTEST2(*tptr, 1))
		    goto trunctlv;
		printf("\n\t      Adjacency State: %s (%u)",
		       tok2str(isis_ptp_adjancey_values, "unknown", *tptr),
                        *tptr);
		tmp--;
	    }
	    if(tmp>sizeof(tlv_ptp_adj->extd_local_circuit_id)) {
		if (!TTEST2(tlv_ptp_adj->extd_local_circuit_id,
                            sizeof(tlv_ptp_adj->extd_local_circuit_id)))
		    goto trunctlv;
		printf("\n\t      Extended Local circuit-ID: 0x%08x",
		       EXTRACT_32BITS(tlv_ptp_adj->extd_local_circuit_id));
		tmp-=sizeof(tlv_ptp_adj->extd_local_circuit_id);
	    }
	    if(tmp>=SYSTEM_ID_LEN) {
		if (!TTEST2(tlv_ptp_adj->neighbor_sysid, SYSTEM_ID_LEN))
		    goto trunctlv;
		printf("\n\t      Neighbor System-ID: %s",
		       isis_print_id(tlv_ptp_adj->neighbor_sysid,SYSTEM_ID_LEN));
		tmp-=SYSTEM_ID_LEN;
	    }
	    if(tmp>=sizeof(tlv_ptp_adj->neighbor_extd_local_circuit_id)) {
		if (!TTEST2(tlv_ptp_adj->neighbor_extd_local_circuit_id,
                            sizeof(tlv_ptp_adj->neighbor_extd_local_circuit_id)))
		    goto trunctlv;
		printf("\n\t      Neighbor Extended Local circuit-ID: 0x%08x",
		       EXTRACT_32BITS(tlv_ptp_adj->neighbor_extd_local_circuit_id));
	    }
	    break;

	case ISIS_TLV_PROTOCOLS:
	    printf("\n\t      NLPID(s): ");
	    while (tmp>0) {
		if (!TTEST2(*(tptr), 1))
		    goto trunctlv;
		printf("%s (0x%02x)",
                       tok2str(nlpid_values,
                               "unknown",
                               *tptr),
                       *tptr);
		if (tmp>1) /* further NPLIDs ? - put comma */
		    printf(", ");
                tptr++;
                tmp--;
	    }
	    break;

	case ISIS_TLV_TE_ROUTER_ID:
	    if (!TTEST2(*pptr, sizeof(struct in_addr)))
		goto trunctlv;
	    printf("\n\t      Traffic Engineering Router ID: %s", ipaddr_string(pptr));
	    break;

	case ISIS_TLV_IPADDR:
	    while (tmp>=sizeof(struct in_addr)) {
		if (!TTEST2(*tptr, sizeof(struct in_addr)))
		    goto trunctlv;
		printf("\n\t      IPv4 interface address: %s", ipaddr_string(tptr));
		tptr += sizeof(struct in_addr);
		tmp -= sizeof(struct in_addr);
	    }
	    break;

	case ISIS_TLV_HOSTNAME:
	    printf("\n\t      Hostname: ");
	    while (tmp>0) {
		if (!TTEST2(*tptr, 1))
		    goto trunctlv;
		printf("%c",*tptr++);
                tmp--;
	    }
	    break;

	case ISIS_TLV_SHARED_RISK_GROUP:
	    if (tmp < NODE_ID_LEN)
	        break;
	    if (!TTEST2(*tptr, NODE_ID_LEN))
                goto trunctlv;
	    printf("\n\t      IS Neighbor: %s", isis_print_id(tptr, NODE_ID_LEN));
	    tptr+=(NODE_ID_LEN);
	    tmp-=(NODE_ID_LEN);

	    if (tmp < 1)
	        break;
	    if (!TTEST2(*tptr, 1))
                goto trunctlv;
	    printf(", Flags: [%s]", ISIS_MASK_TLV_SHARED_RISK_GROUP(*tptr++) ? "numbered" : "unnumbered");
	    tmp--;

	    if (tmp < sizeof(struct in_addr))
	        break;
	    if (!TTEST2(*tptr,sizeof(struct in_addr)))
                goto trunctlv;
	    printf("\n\t      IPv4 interface address: %s", ipaddr_string(tptr));
	    tptr+=sizeof(struct in_addr);
	    tmp-=sizeof(struct in_addr);

	    if (tmp < sizeof(struct in_addr))
	        break;
	    if (!TTEST2(*tptr,sizeof(struct in_addr)))
                goto trunctlv;
	    printf("\n\t      IPv4 neighbor address: %s", ipaddr_string(tptr));
	    tptr+=sizeof(struct in_addr);
	    tmp-=sizeof(struct in_addr);

	    while (tmp>=4) {
                if (!TTEST2(*tptr, 4))
                    goto trunctlv;
                printf("\n\t      Link-ID: 0x%08x", EXTRACT_32BITS(tptr));
                tptr+=4;
                tmp-=4;
	    }
	    break;

	case ISIS_TLV_LSP:
	    tlv_lsp = (const struct isis_tlv_lsp *)tptr;
	    while(tmp>=sizeof(struct isis_tlv_lsp)) {
		if (!TTEST((tlv_lsp->lsp_id)[LSP_ID_LEN-1]))
		    goto trunctlv;
		printf("\n\t      lsp-id: %s",
                       isis_print_id(tlv_lsp->lsp_id, LSP_ID_LEN));
		if (!TTEST2(tlv_lsp->sequence_number, 4))
		    goto trunctlv;
		printf(", seq: 0x%08x",EXTRACT_32BITS(tlv_lsp->sequence_number));
		if (!TTEST2(tlv_lsp->remaining_lifetime, 2))
		    goto trunctlv;
		printf(", lifetime: %5ds",EXTRACT_16BITS(tlv_lsp->remaining_lifetime));
		if (!TTEST2(tlv_lsp->checksum, 2))
		    goto trunctlv;
		printf(", chksum: 0x%04x",EXTRACT_16BITS(tlv_lsp->checksum));
		tmp-=sizeof(struct isis_tlv_lsp);
		tlv_lsp++;
	    }
	    break;

	case ISIS_TLV_CHECKSUM:
	    if (tmp < ISIS_TLV_CHECKSUM_MINLEN)
	        break;
	    if (!TTEST2(*tptr, ISIS_TLV_CHECKSUM_MINLEN))
		goto trunctlv;
	    printf("\n\t      checksum: 0x%04x ", EXTRACT_16BITS(tptr));
            /* do not attempt to verify the checksum if it is zero
             * most likely a HMAC-MD5 TLV is also present and
             * to avoid conflicts the checksum TLV is zeroed.
             * see rfc3358 for details
             */
            if (EXTRACT_16BITS(tptr) == 0)
                printf("(unverified)");
            else printf("(%s)", osi_cksum(optr, length) ? "incorrect" : "correct");
	    break;

	case ISIS_TLV_MT_SUPPORTED:
            if (tmp < ISIS_TLV_MT_SUPPORTED_MINLEN)
                break;
	    while (tmp>1) {
		/* length can only be a multiple of 2, otherwise there is
		   something broken -> so decode down until length is 1 */
		if (tmp!=1) {
                    mt_len = isis_print_mtid(tptr, "\n\t      ");
                    if (mt_len == 0) /* did something go wrong ? */
                        goto trunctlv;
                    tptr+=mt_len;
                    tmp-=mt_len;
		} else {
		    printf("\n\t      malformed MT-ID");
		    break;
		}
	    }
	    break;

	case ISIS_TLV_RESTART_SIGNALING:
            /* first attempt to decode the flags */
            if (tmp < ISIS_TLV_RESTART_SIGNALING_FLAGLEN)
                break;
            if (!TTEST2(*tptr, ISIS_TLV_RESTART_SIGNALING_FLAGLEN))
                goto trunctlv;
            printf("\n\t      Flags [%s]",
                   bittok2str(isis_restart_flag_values, "none", *tptr));
            tptr+=ISIS_TLV_RESTART_SIGNALING_FLAGLEN;
            tmp-=ISIS_TLV_RESTART_SIGNALING_FLAGLEN;

            /* is there anything other than the flags field? */
            if (tmp == 0)
                break;

            if (tmp < ISIS_TLV_RESTART_SIGNALING_HOLDTIMELEN)
                break;
            if (!TTEST2(*tptr, ISIS_TLV_RESTART_SIGNALING_HOLDTIMELEN))
                goto trunctlv;

            printf(", Remaining holding time %us", EXTRACT_16BITS(tptr));
            tptr+=ISIS_TLV_RESTART_SIGNALING_HOLDTIMELEN;
            tmp-=ISIS_TLV_RESTART_SIGNALING_HOLDTIMELEN;

            /* is there an additional sysid field present ?*/
            if (tmp == SYSTEM_ID_LEN) {
                    if (!TTEST2(*tptr, SYSTEM_ID_LEN))
                            goto trunctlv;
                    printf(", for %s",isis_print_id(tptr,SYSTEM_ID_LEN));
            } 
	    break;

        case ISIS_TLV_IDRP_INFO:
	    if (tmp < ISIS_TLV_IDRP_INFO_MINLEN)
	        break;
            if (!TTEST2(*tptr, ISIS_TLV_IDRP_INFO_MINLEN))
                goto trunctlv;
            printf("\n\t      Inter-Domain Information Type: %s",
                   tok2str(isis_subtlv_idrp_values,
                           "Unknown (0x%02x)",
                           *tptr));
            switch (*tptr++) {
            case ISIS_SUBTLV_IDRP_ASN:
                if (!TTEST2(*tptr, 2)) /* fetch AS number */
                    goto trunctlv;
                printf("AS Number: %u",EXTRACT_16BITS(tptr));
                break;
            case ISIS_SUBTLV_IDRP_LOCAL:
            case ISIS_SUBTLV_IDRP_RES:
            default:
                if(!print_unknown_data(tptr,"\n\t      ",tlv_len-1))
                    return(0);
                break;
            }
            break;

        case ISIS_TLV_LSP_BUFFERSIZE:
	    if (tmp < ISIS_TLV_LSP_BUFFERSIZE_MINLEN)
	        break;
            if (!TTEST2(*tptr, ISIS_TLV_LSP_BUFFERSIZE_MINLEN))
                goto trunctlv;
            printf("\n\t      LSP Buffersize: %u",EXTRACT_16BITS(tptr));
            break;

        case ISIS_TLV_PART_DIS:
            while (tmp >= SYSTEM_ID_LEN) {
                if (!TTEST2(*tptr, SYSTEM_ID_LEN))
                    goto trunctlv;
                printf("\n\t      %s",isis_print_id(tptr,SYSTEM_ID_LEN));
                tptr+=SYSTEM_ID_LEN;
                tmp-=SYSTEM_ID_LEN;
            }
            break;

        case ISIS_TLV_PREFIX_NEIGH:
	    if (tmp < sizeof(struct isis_metric_block))
	        break;
            if (!TTEST2(*tptr, sizeof(struct isis_metric_block)))
                goto trunctlv;
            printf("\n\t      Metric Block");
            isis_print_metric_block((const struct isis_metric_block *)tptr);
            tptr+=sizeof(struct isis_metric_block);
            tmp-=sizeof(struct isis_metric_block);

            while(tmp>0) {
                if (!TTEST2(*tptr, 1))
                    goto trunctlv;
                prefix_len=*tptr++; /* read out prefix length in semioctets*/
                if (prefix_len < 2) {
                    printf("\n\t\tAddress: prefix length %u < 2", prefix_len);
                    break;
                }
                tmp--;
                if (tmp < prefix_len/2)
                    break;
                if (!TTEST2(*tptr, prefix_len/2))
                    goto trunctlv;
                printf("\n\t\tAddress: %s/%u",
                       isonsap_string(tptr,prefix_len/2),
                       prefix_len*4);
                tptr+=prefix_len/2;
                tmp-=prefix_len/2;
            }
            break;

        case ISIS_TLV_IIH_SEQNR:
	    if (tmp < ISIS_TLV_IIH_SEQNR_MINLEN)
	        break;
            if (!TTEST2(*tptr, ISIS_TLV_IIH_SEQNR_MINLEN)) /* check if four bytes are on the wire */
                goto trunctlv;
            printf("\n\t      Sequence number: %u", EXTRACT_32BITS(tptr) );
            break;

        case ISIS_TLV_VENDOR_PRIVATE:
	    if (tmp < ISIS_TLV_VENDOR_PRIVATE_MINLEN)
	        break;
            if (!TTEST2(*tptr, ISIS_TLV_VENDOR_PRIVATE_MINLEN)) /* check if enough byte for a full oui */
                goto trunctlv;
            vendor_id = EXTRACT_24BITS(tptr);
            printf("\n\t      Vendor: %s (%u)",
                   tok2str(oui_values,"Unknown",vendor_id),
                   vendor_id);
            tptr+=3;
            tmp-=3;
            if (tmp > 0) /* hexdump the rest */
                if(!print_unknown_data(tptr,"\n\t\t",tmp))
                    return(0);
            break;
            /*
             * FIXME those are the defined TLVs that lack a decoder
             * you are welcome to contribute code ;-)
             */

        case ISIS_TLV_DECNET_PHASE4:
        case ISIS_TLV_LUCENT_PRIVATE:
        case ISIS_TLV_IPAUTH:
        case ISIS_TLV_NORTEL_PRIVATE1:
        case ISIS_TLV_NORTEL_PRIVATE2:

	default:
            if (vflag <= 1) {
                if(!print_unknown_data(pptr,"\n\t\t",tlv_len))
                    return(0);
            }
	    break;
	}
        /* do we want to see an additionally hexdump ? */
        if (vflag> 1) {
	    if(!print_unknown_data(pptr,"\n\t      ",tlv_len))
	        return(0);
        }

	pptr += tlv_len;
	packet_len -= tlv_len;
    }

    if (packet_len != 0) {
	printf("\n\t      %u straggler bytes", packet_len);
    }
    return (1);

 trunc:
    fputs("[|isis]", stdout);
    return (1);

 trunctlv:
    printf("\n\t\t packet exceeded snapshot");
    return(1);
}

/*
 * Verify the checksum.  See 8473-1, Appendix C, section C.4.
 */

static int
osi_cksum(const u_int8_t *tptr, u_int len)
{
	int32_t c0 = 0, c1 = 0;

	while ((int)--len >= 0) {
		c0 += *tptr++;
		c0 %= 255;
		c1 += c0;
		c1 %= 255;
	}
	return (c0 | c1);
}


/*
 * Local Variables:
 * c-style: whitesmith
 * c-basic-offset: 8
 * End:
 */
