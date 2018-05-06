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
#include "l2vpn.h"

/* draft-ietf-pwe3-iana-allocation-04 */
const struct tok l2vpn_encaps_values[] = {
    { 0x00, "Reserved"},
    { 0x01, "Frame Relay"},
    { 0x02, "ATM AAL5 VCC transport"},
    { 0x03, "ATM transparent cell transport"},
    { 0x04, "Ethernet VLAN"},
    { 0x05, "Ethernet"},
    { 0x06, "Cisco-HDLC"},
    { 0x07, "PPP"},
    { 0x08, "SONET/SDH Circuit Emulation Service over MPLS"},
    { 0x09, "ATM n-to-one VCC cell transport"},
    { 0x0a, "ATM n-to-one VPC cell transport"},
    { 0x0b, "IP Layer2 Transport"},
    { 0x0c, "ATM one-to-one VCC Cell Mode"},
    { 0x0d, "ATM one-to-one VPC Cell Mode"},
    { 0x0e, "ATM AAL5 PDU VCC transport"},
    { 0x0f, "Frame-Relay Port mode"},
    { 0x10, "SONET/SDH Circuit Emulation over Packet"},
    { 0x11, "Structure-agnostic E1 over Packet"},
    { 0x12, "Structure-agnostic T1 (DS1) over Packet"},
    { 0x13, "Structure-agnostic E3 over Packet"},
    { 0x14, "Structure-agnostic T3 (DS3) over Packet"},
    { 0x15, "CESoPSN basic mode"},
    { 0x16, "TDMoIP basic mode"},
    { 0x17, "CESoPSN TDM with CAS"},
    { 0x18, "TDMoIP TDM with CAS"},
    { 0x40, "IP-interworking"},
    { 0, NULL}
};
