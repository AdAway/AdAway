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

#define NETDISSECT_REWORKED
#ifdef HAVE_CONFIG_H
#include "config.h"
#endif

#include <tcpdump-stdinc.h>

#include "interface.h"
#include "ipproto.h"

const struct tok ipproto_values[] = {
    { IPPROTO_HOPOPTS, "Options" },
    { IPPROTO_ICMP, "ICMP" },
    { IPPROTO_IGMP, "IGMP" },
    { IPPROTO_IPV4, "IPIP" },
    { IPPROTO_TCP, "TCP" },
    { IPPROTO_EGP, "EGP" },
    { IPPROTO_PIGP, "IGRP" },
    { IPPROTO_UDP, "UDP" },
    { IPPROTO_DCCP, "DCCP" },
    { IPPROTO_IPV6, "IPv6" },
    { IPPROTO_ROUTING, "Routing" },
    { IPPROTO_FRAGMENT, "Fragment" },
    { IPPROTO_RSVP, "RSVP" },
    { IPPROTO_GRE, "GRE" },
    { IPPROTO_ESP, "ESP" },
    { IPPROTO_AH, "AH" },
    { IPPROTO_MOBILE, "Mobile IP" },
    { IPPROTO_ICMPV6, "ICMPv6" },
    { IPPROTO_MOBILITY_OLD, "Mobile IP (old)" },
    { IPPROTO_EIGRP, "EIGRP" },
    { IPPROTO_OSPF, "OSPF" },
    { IPPROTO_PIM, "PIM" },
    { IPPROTO_IPCOMP, "Compressed IP" },
    { IPPROTO_VRRP, "VRRP" },
    { IPPROTO_PGM, "PGM" },
    { IPPROTO_SCTP, "SCTP" },
    { IPPROTO_MOBILITY, "Mobility" },
    { IPPROTO_CARP, "CARP" },
    { 0, NULL }
};

