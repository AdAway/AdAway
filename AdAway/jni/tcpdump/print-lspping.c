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
    "@(#) $Header: /tcpdump/master/tcpdump/print-lspping.c,v 1.20 2008-01-28 14:20:43 hannes Exp $";
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

#include "bgp.h"
#include "l2vpn.h"
#include "oui.h"

/*
 * LSPPING common header
 *
 *  0                   1                   2                   3
 *  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |         Version Number        |         Must Be Zero          |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |  Message Type |   Reply mode  |  Return Code  | Return Subcode|
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                        Sender's Handle                        |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                        Sequence Number                        |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                    TimeStamp Sent (seconds)                   |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                  TimeStamp Sent (microseconds)                |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                  TimeStamp Received (seconds)                 |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                TimeStamp Received (microseconds)              |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                            TLVs ...                           |
 * .                                                               .
 * .                                                               .
 * .                                                               .
 */

struct lspping_common_header {
    u_int8_t version[2];
    u_int8_t reserved[2];
    u_int8_t msg_type;
    u_int8_t reply_mode;   
    u_int8_t return_code;   
    u_int8_t return_subcode;   
    u_int8_t sender_handle[4];
    u_int8_t seq_number[4];
    u_int8_t ts_sent_sec[4];
    u_int8_t ts_sent_usec[4];
    u_int8_t ts_rcvd_sec[4];
    u_int8_t ts_rcvd_usec[4];
};

#define LSPPING_VERSION            1

static const struct tok lspping_msg_type_values[] = {
    { 1, "MPLS Echo Request"},
    { 2, "MPLS Echo Reply"},
    { 0, NULL}
};

static const struct tok lspping_reply_mode_values[] = {
    { 1, "Do not reply"},
    { 2, "Reply via an IPv4/IPv6 UDP packet"},
    { 3, "Reply via an IPv4/IPv6 UDP packet with Router Alert"},
    { 4, "Reply via application level control channel"},
    { 0, NULL}
};

static const struct tok lspping_return_code_values[] = {
    {  0, "No return code or return code contained in the Error Code TLV"},
    {  1, "Malformed echo request received"},
    {  2, "One or more of the TLVs was not understood"},
    {  3, "Replying router is an egress for the FEC at stack depth"},
    {  4, "Replying router has no mapping for the FEC at stack depth"},
    {  5, "Reserved"},
    {  6, "Reserved"},
    {  7, "Reserved"},
    {  8, "Label switched at stack-depth"},
    {  9, "Label switched but no MPLS forwarding at stack-depth"},
    { 10, "Mapping for this FEC is not the given label at stack depth"},
    { 11, "No label entry at stack-depth"},
    { 12, "Protocol not associated with interface at FEC stack depth"},
};


/* 
 * LSPPING TLV header
 *  0                   1                   2                   3
 *  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |             Type              |            Length             |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                             Value                             |
 * .                                                               .
 * .                                                               .
 * .                                                               .
 * |                                                               |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 */

struct lspping_tlv_header {
    u_int8_t type[2];
    u_int8_t length[2];
};

#define	LSPPING_TLV_TARGET_FEC_STACK      1
#define	LSPPING_TLV_DOWNSTREAM_MAPPING    2
#define	LSPPING_TLV_PAD                   3
#define LSPPING_TLV_VENDOR_ENTERPRISE     5
#define LSPPING_TLV_VENDOR_ENTERPRISE_LEN 4
#define LSPPING_TLV_INTERFACE_LABEL_STACK 7
#define	LSPPING_TLV_ERROR_CODE            9
#define LSPPING_TLV_REPLY_TOS_BYTE        10
#define	LSPPING_TLV_BFD_DISCRIMINATOR     15 /* draft-ietf-bfd-mpls-02 */
#define LSPPING_TLV_BFD_DISCRIMINATOR_LEN 4
#define	LSPPING_TLV_VENDOR_PRIVATE        0xfc00

static const struct tok lspping_tlv_values[] = {
    { LSPPING_TLV_TARGET_FEC_STACK, "Target FEC Stack" },
    { LSPPING_TLV_DOWNSTREAM_MAPPING, "Downstream Mapping" },
    { LSPPING_TLV_PAD, "Pad" },
    { LSPPING_TLV_ERROR_CODE, "Error Code" },
    { LSPPING_TLV_VENDOR_ENTERPRISE, "Vendor Enterprise Code" },
    { LSPPING_TLV_INTERFACE_LABEL_STACK, "Interface Label Stack" },
    { LSPPING_TLV_REPLY_TOS_BYTE, "Reply TOS Byte" },
    { LSPPING_TLV_BFD_DISCRIMINATOR, "BFD Discriminator" },
    { LSPPING_TLV_VENDOR_PRIVATE, "Vendor Private Code" },
    { 0, NULL}
};

#define	LSPPING_TLV_TARGETFEC_SUBTLV_LDP_IPV4      1
#define	LSPPING_TLV_TARGETFEC_SUBTLV_LDP_IPV6      2
#define	LSPPING_TLV_TARGETFEC_SUBTLV_RSVP_IPV4     3
#define	LSPPING_TLV_TARGETFEC_SUBTLV_RSVP_IPV6     4
#define	LSPPING_TLV_TARGETFEC_SUBTLV_L3VPN_IPV4    6
#define	LSPPING_TLV_TARGETFEC_SUBTLV_L3VPN_IPV6    7
#define	LSPPING_TLV_TARGETFEC_SUBTLV_L2VPN_ENDPT   8
#define	LSPPING_TLV_TARGETFEC_SUBTLV_L2VPN_VCID_OLD 9
#define	LSPPING_TLV_TARGETFEC_SUBTLV_L2VPN_VCID   10
#define	LSPPING_TLV_TARGETFEC_SUBTLV_BGP_IPV4     11
#define	LSPPING_TLV_TARGETFEC_SUBTLV_BGP_IPV6     12

static const struct tok lspping_tlvtargetfec_subtlv_values[] = {
    { LSPPING_TLV_TARGETFEC_SUBTLV_LDP_IPV4, "LDP IPv4 prefix"},
    { LSPPING_TLV_TARGETFEC_SUBTLV_LDP_IPV6, "LDP IPv6 prefix"},
    { LSPPING_TLV_TARGETFEC_SUBTLV_RSVP_IPV4, "RSVP IPv4 Session Query"},
    { LSPPING_TLV_TARGETFEC_SUBTLV_RSVP_IPV6, "RSVP IPv6 Session Query"},
    { 5, "Reserved"},
    { LSPPING_TLV_TARGETFEC_SUBTLV_L3VPN_IPV4, "VPN IPv4 prefix"},
    { LSPPING_TLV_TARGETFEC_SUBTLV_L3VPN_IPV6, "VPN IPv6 prefix"},
    { LSPPING_TLV_TARGETFEC_SUBTLV_L2VPN_ENDPT, "L2 VPN endpoint"},
    { LSPPING_TLV_TARGETFEC_SUBTLV_L2VPN_VCID_OLD, "L2 circuit ID (old)"},
    { LSPPING_TLV_TARGETFEC_SUBTLV_L2VPN_VCID, "L2 circuit ID"},
    { LSPPING_TLV_TARGETFEC_SUBTLV_BGP_IPV4, "BGP labeled IPv4 prefix"},
    { LSPPING_TLV_TARGETFEC_SUBTLV_BGP_IPV6, "BGP labeled IPv6 prefix"},
    { 0, NULL}
};

/*
 *  0                   1                   2                   3
 *  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                          IPv4 prefix                          |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * | Prefix Length |         Must Be Zero                          |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 */
struct lspping_tlv_targetfec_subtlv_ldp_ipv4_t {
    u_int8_t prefix [4];
    u_int8_t prefix_len;
};

/*
 *  0                   1                   2                   3
 *  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                          IPv6 prefix                          |
 * |                          (16 octets)                          |
 * |                                                               |
 * |                                                               |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * | Prefix Length |         Must Be Zero                          |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 */
struct lspping_tlv_targetfec_subtlv_ldp_ipv6_t {
    u_int8_t prefix [16];
    u_int8_t prefix_len;
};

/*
 * 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                    Sender identifier                          |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                         IPv4 prefix                           |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * | Prefix Length |                 Must Be Zero                  |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 */
struct lspping_tlv_targetfec_subtlv_bgp_ipv4_t {
    u_int8_t sender_id [4];
    u_int8_t prefix [4];
    u_int8_t prefix_len;
};

/*
 * 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                    Sender identifier                          |
 * |                          (16 octets)                          |
 * |                                                               |
 * |                                                               |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                          IPv6 prefix                          |
 * |                          (16 octets)                          |
 * |                                                               |
 * |                                                               |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * | Prefix Length |                 Must Be Zero                  |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 */
struct lspping_tlv_targetfec_subtlv_bgp_ipv6_t {
    u_int8_t sender_id [16];
    u_int8_t prefix [16];
    u_int8_t prefix_len;
};

/*
 *  0                   1                   2                   3
 *  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                 IPv4 tunnel end point address                 |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |          Must Be Zero         |     Tunnel ID                 |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                       Extended Tunnel ID                      |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                   IPv4 tunnel sender address                  |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |          Must Be Zero         |            LSP ID             |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 */
struct lspping_tlv_targetfec_subtlv_rsvp_ipv4_t {
    u_int8_t tunnel_endpoint [4];
    u_int8_t res[2];
    u_int8_t tunnel_id[2];
    u_int8_t extended_tunnel_id[4];
    u_int8_t tunnel_sender [4];
    u_int8_t res2[2];
    u_int8_t lsp_id [2];
};

/*
 *  0                   1                   2                   3
 *  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                 IPv6 tunnel end point address                 |
 * |                                                               |
 * |                                                               |
 * |                                                               |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |          Must Be Zero         |          Tunnel ID            |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                       Extended Tunnel ID                      |
 * |                                                               |
 * |                                                               |
 * |                                                               |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                   IPv6 tunnel sender address                  |
 * |                                                               |
 * |                                                               |
 * |                                                               |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |          Must Be Zero         |            LSP ID             |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 */
struct lspping_tlv_targetfec_subtlv_rsvp_ipv6_t {
    u_int8_t tunnel_endpoint [16];
    u_int8_t res[2];
    u_int8_t tunnel_id[2];
    u_int8_t extended_tunnel_id[16];
    u_int8_t tunnel_sender [16];
    u_int8_t res2[2];
    u_int8_t lsp_id [2];
};

/*
 *  0                   1                   2                   3
 *  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                      Route Distinguisher                      |
 * |                          (8 octets)                           |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                         IPv4 prefix                           |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * | Prefix Length |                 Must Be Zero                  |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 */
struct lspping_tlv_targetfec_subtlv_l3vpn_ipv4_t {
    u_int8_t rd [8];
    u_int8_t prefix [4];
    u_int8_t prefix_len;
};

/*
 *  0                   1                   2                   3
 *  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                      Route Distinguisher                      |
 * |                          (8 octets)                           |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                          IPv6 prefix                          |
 * |                          (16 octets)                          |
 * |                                                               |
 * |                                                               |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * | Prefix Length |                 Must Be Zero                  |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 */
struct lspping_tlv_targetfec_subtlv_l3vpn_ipv6_t {
    u_int8_t rd [8];
    u_int8_t prefix [16];
    u_int8_t prefix_len;
};

/*
 *  0                   1                   2                   3
 *  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                      Route Distinguisher                      |
 * |                          (8 octets)                           |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |         Sender's CE ID        |       Receiver's CE ID        |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |      Encapsulation Type       |         Must Be Zero          |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *  0                   1                   2                   3
 */
struct lspping_tlv_targetfec_subtlv_l2vpn_endpt_t {
    u_int8_t rd [8];
    u_int8_t sender_ce_id [2];
    u_int8_t receiver_ce_id [2];
    u_int8_t encapsulation[2];
};

/*
 *  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                      Remote PE Address                        |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                             VC ID                             |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |      Encapsulation Type       |         Must Be Zero          |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 */
struct lspping_tlv_targetfec_subtlv_l2vpn_vcid_old_t {
    u_int8_t remote_pe_address [4];
    u_int8_t vc_id [4];
    u_int8_t encapsulation[2];
};

/*
 *  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                     Sender's PE Address                       |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                      Remote PE Address                        |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |                             VC ID                             |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |      Encapsulation Type       |         Must Be Zero          |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 */
struct lspping_tlv_targetfec_subtlv_l2vpn_vcid_t {
    u_int8_t sender_pe_address [4];
    u_int8_t remote_pe_address [4];
    u_int8_t vc_id [4];
    u_int8_t encapsulation[2];
};

/*
 *  0                   1                   2                   3
 *  0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |               MTU             | Address Type  |  Resvd (SBZ)  |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |             Downstream IP Address (4 or 16 octets)            |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |         Downstream Interface Address (4 or 16 octets)         |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * | Hash Key Type | Depth Limit   |        Multipath Length       |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * .                                                               .
 * .                     (Multipath Information)                   .
 * .                                                               .
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |               Downstream Label                |    Protocol   |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * .                                                               .
 * .                                                               .
 * .                                                               .
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 * |               Downstream Label                |    Protocol   |
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 */
struct lspping_tlv_downstream_map_ipv4_t {
    u_int8_t mtu [2];
    u_int8_t address_type;
    u_int8_t res;
    u_int8_t downstream_ip[4];
    u_int8_t downstream_interface[4];
};

struct lspping_tlv_downstream_map_ipv6_t {
    u_int8_t mtu [2];
    u_int8_t address_type;
    u_int8_t res;
    u_int8_t downstream_ip[16];
    u_int8_t downstream_interface[16];
};

struct lspping_tlv_downstream_map_info_t {
    u_int8_t hash_key_type;
    u_int8_t depth_limit;
    u_int8_t multipath_length [2];
};

#define LSPPING_AFI_IPV4 1
#define LSPPING_AFI_UNMB 2
#define LSPPING_AFI_IPV6 3

static const struct tok lspping_tlv_downstream_addr_values[] = {
    { LSPPING_AFI_IPV4, "IPv4"},
    { LSPPING_AFI_IPV6, "IPv6"},
    { LSPPING_AFI_UNMB, "Unnumbered"},
    { 0, NULL}
};

void
lspping_print(register const u_char *pptr, register u_int len) {

    const struct lspping_common_header *lspping_com_header;
    const struct lspping_tlv_header *lspping_tlv_header;
    const struct lspping_tlv_header *lspping_subtlv_header;
    const u_char *tptr,*tlv_tptr,*subtlv_tptr;
    int tlen,lspping_tlv_len,lspping_tlv_type,tlv_tlen;
    int tlv_hexdump,subtlv_hexdump;
    int lspping_subtlv_len,lspping_subtlv_type;
    struct timeval timestamp; 

    union {
        const struct lspping_tlv_downstream_map_ipv4_t *lspping_tlv_downstream_map_ipv4;
        const struct lspping_tlv_downstream_map_ipv6_t *lspping_tlv_downstream_map_ipv6;
        const struct lspping_tlv_downstream_map_info_t  *lspping_tlv_downstream_map_info;
    } tlv_ptr;

    union {
        const struct lspping_tlv_targetfec_subtlv_ldp_ipv4_t *lspping_tlv_targetfec_subtlv_ldp_ipv4;
        const struct lspping_tlv_targetfec_subtlv_ldp_ipv6_t *lspping_tlv_targetfec_subtlv_ldp_ipv6;
        const struct lspping_tlv_targetfec_subtlv_rsvp_ipv4_t *lspping_tlv_targetfec_subtlv_rsvp_ipv4;
        const struct lspping_tlv_targetfec_subtlv_rsvp_ipv6_t *lspping_tlv_targetfec_subtlv_rsvp_ipv6;
        const struct lspping_tlv_targetfec_subtlv_l3vpn_ipv4_t *lspping_tlv_targetfec_subtlv_l3vpn_ipv4;
        const struct lspping_tlv_targetfec_subtlv_l3vpn_ipv6_t *lspping_tlv_targetfec_subtlv_l3vpn_ipv6;
        const struct lspping_tlv_targetfec_subtlv_l2vpn_endpt_t *lspping_tlv_targetfec_subtlv_l2vpn_endpt;
        const struct lspping_tlv_targetfec_subtlv_l2vpn_vcid_old_t *lspping_tlv_targetfec_subtlv_l2vpn_vcid_old;
        const struct lspping_tlv_targetfec_subtlv_l2vpn_vcid_t *lspping_tlv_targetfec_subtlv_l2vpn_vcid;
        const struct lspping_tlv_targetfec_subtlv_bgp_ipv4_t *lspping_tlv_targetfec_subtlv_bgp_ipv4;
        const struct lspping_tlv_targetfec_subtlv_bgp_ipv6_t *lspping_tlv_targetfec_subtlv_bgp_ipv6;
    } subtlv_ptr;

    tptr=pptr;
    lspping_com_header = (const struct lspping_common_header *)pptr;
    TCHECK(*lspping_com_header);

    /*
     * Sanity checking of the header.
     */
    if (EXTRACT_16BITS(&lspping_com_header->version[0]) != LSPPING_VERSION) {
	printf("LSP-PING version %u packet not supported",
               EXTRACT_16BITS(&lspping_com_header->version[0]));
	return;
    }

    /* in non-verbose mode just lets print the basic Message Type*/
    if (vflag < 1) {
        printf("LSP-PINGv%u, %s, seq %u, length: %u",
               EXTRACT_16BITS(&lspping_com_header->version[0]),
               tok2str(lspping_msg_type_values, "unknown (%u)",lspping_com_header->msg_type),
               EXTRACT_32BITS(lspping_com_header->seq_number),
               len);
        return;
    }

    /* ok they seem to want to know everything - lets fully decode it */

    tlen=len;

    printf("\n\tLSP-PINGv%u, msg-type: %s (%u), length: %u\n\t  reply-mode: %s (%u)",
           EXTRACT_16BITS(&lspping_com_header->version[0]),
           tok2str(lspping_msg_type_values, "unknown",lspping_com_header->msg_type),
           lspping_com_header->msg_type,
           len,
           tok2str(lspping_reply_mode_values, "unknown",lspping_com_header->reply_mode),
           lspping_com_header->reply_mode);

    /*
     *  the following return codes require that the subcode is attached
     *  at the end of the translated token output
     */
    if (lspping_com_header->return_code == 3 ||
        lspping_com_header->return_code == 4 ||
        lspping_com_header->return_code == 8 ||
        lspping_com_header->return_code == 10 ||
        lspping_com_header->return_code == 11 ||
        lspping_com_header->return_code == 12 )
        printf("\n\t  Return Code: %s %u (%u)\n\t  Return Subcode: (%u)",
               tok2str(lspping_return_code_values, "unknown",lspping_com_header->return_code),
               lspping_com_header->return_subcode,    
               lspping_com_header->return_code,
               lspping_com_header->return_subcode);
    else
        printf("\n\t  Return Code: %s (%u)\n\t  Return Subcode: (%u)",
               tok2str(lspping_return_code_values, "unknown",lspping_com_header->return_code),   
               lspping_com_header->return_code,
               lspping_com_header->return_subcode);
 
    printf("\n\t  Sender Handle: 0x%08x, Sequence: %u",
           EXTRACT_32BITS(lspping_com_header->sender_handle),
           EXTRACT_32BITS(lspping_com_header->seq_number));

    timestamp.tv_sec=EXTRACT_32BITS(lspping_com_header->ts_sent_sec);
    timestamp.tv_usec=EXTRACT_32BITS(lspping_com_header->ts_sent_usec);     
    printf("\n\t  Sender Timestamp: ");
    ts_print(&timestamp);

    timestamp.tv_sec=EXTRACT_32BITS(lspping_com_header->ts_rcvd_sec);
    timestamp.tv_usec=EXTRACT_32BITS(lspping_com_header->ts_rcvd_usec); 
    printf("Receiver Timestamp: ");
    if ((timestamp.tv_sec != 0) && (timestamp.tv_usec != 0))
        ts_print(&timestamp);
    else
        printf("no timestamp");

    tptr+=sizeof(const struct lspping_common_header);
    tlen-=sizeof(const struct lspping_common_header);

    while(tlen>(int)sizeof(struct lspping_tlv_header)) {

        /* did we capture enough for fully decoding the tlv header ? */
        if (!TTEST2(*tptr, sizeof(struct lspping_tlv_header)))
            goto trunc;

        lspping_tlv_header = (const struct lspping_tlv_header *)tptr;
        lspping_tlv_type=EXTRACT_16BITS(lspping_tlv_header->type);
        lspping_tlv_len=EXTRACT_16BITS(lspping_tlv_header->length);

        /* some little sanity checking */
        if (lspping_tlv_type == 0 || lspping_tlv_len == 0)
            return;

        if(lspping_tlv_len < 4) {
            printf("\n\t  ERROR: TLV %u bogus size %u",lspping_tlv_type,lspping_tlv_len);
            return;
        }

        printf("\n\t  %s TLV (%u), length: %u",
               tok2str(lspping_tlv_values,
                       "Unknown",
                       lspping_tlv_type),
               lspping_tlv_type,
               lspping_tlv_len);

        tlv_tptr=tptr+sizeof(struct lspping_tlv_header);
        tlv_tlen=lspping_tlv_len; /* header not included -> no adjustment */

        /* did we capture enough for fully decoding the tlv ? */
        if (!TTEST2(*tptr, lspping_tlv_len))
            goto trunc;
        tlv_hexdump=FALSE;

        switch(lspping_tlv_type) {
        case LSPPING_TLV_TARGET_FEC_STACK:
            while(tlv_tlen>(int)sizeof(struct lspping_tlv_header)) {

                /* did we capture enough for fully decoding the subtlv header ? */
                if (!TTEST2(*tptr, sizeof(struct lspping_tlv_header)))
                    goto trunc;
                subtlv_hexdump=FALSE;

                lspping_subtlv_header = (const struct lspping_tlv_header *)tlv_tptr;
                lspping_subtlv_type=EXTRACT_16BITS(lspping_subtlv_header->type);
                lspping_subtlv_len=EXTRACT_16BITS(lspping_subtlv_header->length);
                subtlv_tptr=tlv_tptr+sizeof(struct lspping_tlv_header);
                
                if (lspping_subtlv_len == 0)
                    break;

                printf("\n\t    %s subTLV (%u), length: %u",
                       tok2str(lspping_tlvtargetfec_subtlv_values,
                               "Unknown",
                               lspping_subtlv_type),
                       lspping_subtlv_type,
                       lspping_subtlv_len);

                switch(lspping_subtlv_type) {

                case LSPPING_TLV_TARGETFEC_SUBTLV_LDP_IPV4:
                    subtlv_ptr.lspping_tlv_targetfec_subtlv_ldp_ipv4 = \
                        (const struct lspping_tlv_targetfec_subtlv_ldp_ipv4_t *)subtlv_tptr;
                    printf("\n\t      %s/%u",
                           ipaddr_string(subtlv_ptr.lspping_tlv_targetfec_subtlv_ldp_ipv4->prefix),
                           subtlv_ptr.lspping_tlv_targetfec_subtlv_ldp_ipv4->prefix_len);
                    break;

#ifdef INET6
                case LSPPING_TLV_TARGETFEC_SUBTLV_LDP_IPV6:
                    subtlv_ptr.lspping_tlv_targetfec_subtlv_ldp_ipv6 = \
                        (const struct lspping_tlv_targetfec_subtlv_ldp_ipv6_t *)subtlv_tptr;
                    printf("\n\t      %s/%u",
                           ip6addr_string(subtlv_ptr.lspping_tlv_targetfec_subtlv_ldp_ipv6->prefix),
                           subtlv_ptr.lspping_tlv_targetfec_subtlv_ldp_ipv6->prefix_len);
                    break;
#endif

                case LSPPING_TLV_TARGETFEC_SUBTLV_BGP_IPV4:
                    subtlv_ptr.lspping_tlv_targetfec_subtlv_bgp_ipv4 = \
                        (const struct lspping_tlv_targetfec_subtlv_bgp_ipv4_t *)subtlv_tptr;
                    printf("\n\t      %s/%u, sender-id %s",
                           ipaddr_string(subtlv_ptr.lspping_tlv_targetfec_subtlv_bgp_ipv4->prefix),
                           subtlv_ptr.lspping_tlv_targetfec_subtlv_bgp_ipv4->prefix_len,
                           ipaddr_string(subtlv_ptr.lspping_tlv_targetfec_subtlv_bgp_ipv4->sender_id));
                    break;

#ifdef INET6
                case LSPPING_TLV_TARGETFEC_SUBTLV_BGP_IPV6:
                    subtlv_ptr.lspping_tlv_targetfec_subtlv_bgp_ipv6 = \
                        (const struct lspping_tlv_targetfec_subtlv_bgp_ipv6_t *)subtlv_tptr;
                    printf("\n\t      %s/%u, sender-id %s",
                           ip6addr_string(subtlv_ptr.lspping_tlv_targetfec_subtlv_bgp_ipv6->prefix),
                           subtlv_ptr.lspping_tlv_targetfec_subtlv_bgp_ipv6->prefix_len,
                           ip6addr_string(subtlv_ptr.lspping_tlv_targetfec_subtlv_bgp_ipv6->sender_id));
                    break;
#endif

                case LSPPING_TLV_TARGETFEC_SUBTLV_RSVP_IPV4:
                    subtlv_ptr.lspping_tlv_targetfec_subtlv_rsvp_ipv4 = \
                        (const struct lspping_tlv_targetfec_subtlv_rsvp_ipv4_t *)subtlv_tptr;
                    printf("\n\t      tunnel end-point %s, tunnel sender %s, lsp-id 0x%04x" \
                           "\n\t      tunnel-id 0x%04x, extended tunnel-id %s",
                           ipaddr_string(subtlv_ptr.lspping_tlv_targetfec_subtlv_rsvp_ipv4->tunnel_endpoint),
                           ipaddr_string(subtlv_ptr.lspping_tlv_targetfec_subtlv_rsvp_ipv4->tunnel_sender),
                           EXTRACT_16BITS(subtlv_ptr.lspping_tlv_targetfec_subtlv_rsvp_ipv4->lsp_id),
                           EXTRACT_16BITS(subtlv_ptr.lspping_tlv_targetfec_subtlv_rsvp_ipv4->tunnel_id),
                           ipaddr_string(subtlv_ptr.lspping_tlv_targetfec_subtlv_rsvp_ipv4->extended_tunnel_id));
                    break;

#ifdef INET6
                case LSPPING_TLV_TARGETFEC_SUBTLV_RSVP_IPV6:
                    subtlv_ptr.lspping_tlv_targetfec_subtlv_rsvp_ipv6 = \
                        (const struct lspping_tlv_targetfec_subtlv_rsvp_ipv6_t *)subtlv_tptr;
                    printf("\n\t      tunnel end-point %s, tunnel sender %s, lsp-id 0x%04x" \
                           "\n\t      tunnel-id 0x%04x, extended tunnel-id %s",
                           ip6addr_string(subtlv_ptr.lspping_tlv_targetfec_subtlv_rsvp_ipv6->tunnel_endpoint),
                           ip6addr_string(subtlv_ptr.lspping_tlv_targetfec_subtlv_rsvp_ipv6->tunnel_sender),
                           EXTRACT_16BITS(subtlv_ptr.lspping_tlv_targetfec_subtlv_rsvp_ipv6->lsp_id),
                           EXTRACT_16BITS(subtlv_ptr.lspping_tlv_targetfec_subtlv_rsvp_ipv6->tunnel_id),
                           ip6addr_string(subtlv_ptr.lspping_tlv_targetfec_subtlv_rsvp_ipv6->extended_tunnel_id));
                    break;
#endif

                case LSPPING_TLV_TARGETFEC_SUBTLV_L3VPN_IPV4:
                    subtlv_ptr.lspping_tlv_targetfec_subtlv_l3vpn_ipv4 = \
                        (const struct lspping_tlv_targetfec_subtlv_l3vpn_ipv4_t *)subtlv_tptr;
                    printf("\n\t      RD: %s, %s/%u",
                           bgp_vpn_rd_print(subtlv_ptr.lspping_tlv_targetfec_subtlv_l3vpn_ipv4->rd),
                           ipaddr_string(subtlv_ptr.lspping_tlv_targetfec_subtlv_l3vpn_ipv4->prefix),
                           subtlv_ptr.lspping_tlv_targetfec_subtlv_l3vpn_ipv4->prefix_len);
                    break;

#ifdef INET6
                case LSPPING_TLV_TARGETFEC_SUBTLV_L3VPN_IPV6:
                    subtlv_ptr.lspping_tlv_targetfec_subtlv_l3vpn_ipv6 = \
                        (const struct lspping_tlv_targetfec_subtlv_l3vpn_ipv6_t *)subtlv_tptr;
                    printf("\n\t      RD: %s, %s/%u",
                           bgp_vpn_rd_print(subtlv_ptr.lspping_tlv_targetfec_subtlv_l3vpn_ipv6->rd),
                           ip6addr_string(subtlv_ptr.lspping_tlv_targetfec_subtlv_l3vpn_ipv6->prefix),
                           subtlv_ptr.lspping_tlv_targetfec_subtlv_l3vpn_ipv6->prefix_len);
                    break;
#endif

                case LSPPING_TLV_TARGETFEC_SUBTLV_L2VPN_ENDPT:
                    subtlv_ptr.lspping_tlv_targetfec_subtlv_l2vpn_endpt = \
                        (const struct lspping_tlv_targetfec_subtlv_l2vpn_endpt_t *)subtlv_tptr;
                    printf("\n\t      RD: %s, Sender CE-ID: %u, Receiver CE-ID: %u" \
                           "\n\t      Encapsulation Type: %s (%u)",
                           bgp_vpn_rd_print(subtlv_ptr.lspping_tlv_targetfec_subtlv_l2vpn_endpt->rd),
                           EXTRACT_16BITS(subtlv_ptr.lspping_tlv_targetfec_subtlv_l2vpn_endpt->sender_ce_id),
                           EXTRACT_16BITS(subtlv_ptr.lspping_tlv_targetfec_subtlv_l2vpn_endpt->receiver_ce_id),
                           tok2str(l2vpn_encaps_values,
                                   "unknown",
                                   EXTRACT_16BITS(subtlv_ptr.lspping_tlv_targetfec_subtlv_l2vpn_endpt->encapsulation)),
                           EXTRACT_16BITS(subtlv_ptr.lspping_tlv_targetfec_subtlv_l2vpn_endpt->encapsulation));
                    
                    break;

                    /* the old L2VPN VCID subTLV does not have support for the sender field */
                case LSPPING_TLV_TARGETFEC_SUBTLV_L2VPN_VCID_OLD:
                    subtlv_ptr.lspping_tlv_targetfec_subtlv_l2vpn_vcid_old = \
                        (const struct lspping_tlv_targetfec_subtlv_l2vpn_vcid_old_t *)subtlv_tptr;
                    printf("\n\t      Remote PE: %s" \
                           "\n\t      VC-ID: 0x%08x, Encapsulation Type: %s (%u)",
                           ipaddr_string(subtlv_ptr.lspping_tlv_targetfec_subtlv_l2vpn_vcid_old->remote_pe_address),
                           EXTRACT_32BITS(subtlv_ptr.lspping_tlv_targetfec_subtlv_l2vpn_vcid_old->vc_id),
                           tok2str(l2vpn_encaps_values,
                                   "unknown",
                                   EXTRACT_16BITS(subtlv_ptr.lspping_tlv_targetfec_subtlv_l2vpn_vcid_old->encapsulation)),
                           EXTRACT_16BITS(subtlv_ptr.lspping_tlv_targetfec_subtlv_l2vpn_vcid_old->encapsulation));
                    
                    break;

                case LSPPING_TLV_TARGETFEC_SUBTLV_L2VPN_VCID:
                    subtlv_ptr.lspping_tlv_targetfec_subtlv_l2vpn_vcid = \
                        (const struct lspping_tlv_targetfec_subtlv_l2vpn_vcid_t *)subtlv_tptr;
                    printf("\n\t      Sender PE: %s, Remote PE: %s" \
                           "\n\t      VC-ID: 0x%08x, Encapsulation Type: %s (%u)",
                           ipaddr_string(subtlv_ptr.lspping_tlv_targetfec_subtlv_l2vpn_vcid->sender_pe_address),
                           ipaddr_string(subtlv_ptr.lspping_tlv_targetfec_subtlv_l2vpn_vcid->remote_pe_address),
                           EXTRACT_32BITS(subtlv_ptr.lspping_tlv_targetfec_subtlv_l2vpn_vcid->vc_id),
                           tok2str(l2vpn_encaps_values,
                                   "unknown",
                                   EXTRACT_16BITS(subtlv_ptr.lspping_tlv_targetfec_subtlv_l2vpn_vcid->encapsulation)),
                           EXTRACT_16BITS(subtlv_ptr.lspping_tlv_targetfec_subtlv_l2vpn_vcid->encapsulation));
                    
                    break;

                default:
                    subtlv_hexdump=TRUE; /* unknown subTLV just hexdump it */
                    break;
                }
                /* do we want to see an additionally subtlv hexdump ? */
                if (vflag > 1 || subtlv_hexdump==TRUE)
                    print_unknown_data(tlv_tptr+sizeof(struct lspping_tlv_header), \
                                       "\n\t      ",
                                       lspping_subtlv_len);

                tlv_tptr+=lspping_subtlv_len;
                tlv_tlen-=lspping_subtlv_len+sizeof(struct lspping_tlv_header);
            }
            break;

        case LSPPING_TLV_DOWNSTREAM_MAPPING:
            /* that strange thing with the downstream map TLV is that until now
             * we do not know if its IPv4 or IPv6 , after we found the adress-type
             * lets recast the tlv_tptr and move on */

            tlv_ptr.lspping_tlv_downstream_map_ipv4= \
                (const struct lspping_tlv_downstream_map_ipv4_t *)tlv_tptr;
            tlv_ptr.lspping_tlv_downstream_map_ipv6= \
                (const struct lspping_tlv_downstream_map_ipv6_t *)tlv_tptr;
            printf("\n\t    MTU: %u, Address-Type: %s (%u)",
                   EXTRACT_16BITS(tlv_ptr.lspping_tlv_downstream_map_ipv4->mtu),
                   tok2str(lspping_tlv_downstream_addr_values,
                           "unknown",
                           tlv_ptr.lspping_tlv_downstream_map_ipv4->address_type),
                   tlv_ptr.lspping_tlv_downstream_map_ipv4->address_type);

            switch(tlv_ptr.lspping_tlv_downstream_map_ipv4->address_type) {

            case LSPPING_AFI_IPV4:
                printf("\n\t    Downstream IP: %s" \
                       "\n\t    Downstream Interface IP: %s",
                       ipaddr_string(tlv_ptr.lspping_tlv_downstream_map_ipv4->downstream_ip),
                       ipaddr_string(tlv_ptr.lspping_tlv_downstream_map_ipv4->downstream_interface));
                tlv_tptr+=sizeof(struct lspping_tlv_downstream_map_ipv4_t);
                tlv_tlen-=sizeof(struct lspping_tlv_downstream_map_ipv4_t);
                break;
#ifdef INET6
             case LSPPING_AFI_IPV6:
                printf("\n\t    Downstream IP: %s" \
                       "\n\t    Downstream Interface IP: %s",
                       ip6addr_string(tlv_ptr.lspping_tlv_downstream_map_ipv6->downstream_ip),
                       ip6addr_string(tlv_ptr.lspping_tlv_downstream_map_ipv6->downstream_interface));
                tlv_tptr+=sizeof(struct lspping_tlv_downstream_map_ipv6_t);
                tlv_tlen-=sizeof(struct lspping_tlv_downstream_map_ipv6_t);
                break;
#endif
            case LSPPING_AFI_UNMB:
                printf("\n\t    Downstream IP: %s" \
                       "\n\t    Downstream Interface Index: 0x%08x",
                       ipaddr_string(tlv_ptr.lspping_tlv_downstream_map_ipv4->downstream_ip),
                       EXTRACT_32BITS(tlv_ptr.lspping_tlv_downstream_map_ipv4->downstream_interface));
                tlv_tptr+=sizeof(struct lspping_tlv_downstream_map_ipv4_t);
                tlv_tlen-=sizeof(struct lspping_tlv_downstream_map_ipv4_t);
                break;

            default:
                /* should not happen ! - no error message - tok2str() has barked already */
                break;
            }

            tlv_ptr.lspping_tlv_downstream_map_info= \
                (const struct lspping_tlv_downstream_map_info_t *)tlv_tptr;
            
            /* FIXME add hash-key type, depth limit, multipath processing */


            tlv_tptr+=sizeof(struct lspping_tlv_downstream_map_info_t);
            tlv_tlen-=sizeof(struct lspping_tlv_downstream_map_info_t);

            /* FIXME print downstream labels */


            tlv_hexdump=TRUE; /* dump the TLV until code complete */

            break;

        case LSPPING_TLV_BFD_DISCRIMINATOR:
            tptr += sizeof(struct lspping_tlv_header);
            if (!TTEST2(*tptr, LSPPING_TLV_BFD_DISCRIMINATOR_LEN))
                goto trunc;
            printf("\n\t    BFD Discriminator 0x%08x", EXTRACT_32BITS(tptr));
            break;

        case  LSPPING_TLV_VENDOR_ENTERPRISE:
        {
            u_int32_t vendor_id;

            if (!TTEST2(*tptr, LSPPING_TLV_VENDOR_ENTERPRISE_LEN))
                goto trunc;
            vendor_id = EXTRACT_32BITS(tlv_tptr);
            printf("\n\t    Vendor: %s (0x%04x)",
                   tok2str(smi_values, "Unknown", vendor_id),
                   vendor_id);
        }
            break;

            /*
             *  FIXME those are the defined TLVs that lack a decoder
             *  you are welcome to contribute code ;-)
             */
        case LSPPING_TLV_PAD:
        case LSPPING_TLV_ERROR_CODE:
        case LSPPING_TLV_VENDOR_PRIVATE:
    
        default:
            if (vflag <= 1)
                print_unknown_data(tlv_tptr,"\n\t    ",tlv_tlen);
            break;
        }
        /* do we want to see an additionally tlv hexdump ? */
        if (vflag > 1 || tlv_hexdump==TRUE)
            print_unknown_data(tptr+sizeof(struct lspping_tlv_header),"\n\t    ",
                               lspping_tlv_len);


        /* All TLVs are aligned to four octet boundary */
        if (lspping_tlv_len % 4) {
            lspping_tlv_len += (4 - lspping_tlv_len % 4);
        }

        tptr+=lspping_tlv_len+sizeof(struct lspping_tlv_header);
        tlen-=lspping_tlv_len+sizeof(struct lspping_tlv_header);
    }
    return;
trunc:
    printf("\n\t\t packet exceeded snapshot");
}
