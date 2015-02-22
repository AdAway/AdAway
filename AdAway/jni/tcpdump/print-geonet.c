/*
 * Copyright (c) 2013 The TCPDUMP project
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
 * Original code by Ola Martin Lykkja (ola.lykkja@q-free.com)
 */

#ifdef HAVE_CONFIG_H
#include "config.h"
#endif

#include <tcpdump-stdinc.h>

#include <pcap.h>
#include <stdio.h>
#include <string.h>

#include "interface.h"
#include "extract.h"
#include "addrtoname.h"


/*
   ETSI TS 102 636-5-1 V1.1.1 (2011-02)
   Intelligent Transport Systems (ITS); Vehicular Communications; GeoNetworking;
   Part 5: Transport Protocols; Sub-part 1: Basic Transport Protocol

   ETSI TS 102 636-4-1 V1.1.1 (2011-06)
   Intelligent Transport Systems (ITS); Vehicular communications; GeoNetworking;
   Part 4: Geographical addressing and forwarding for point-to-point and point-to-multipoint communications;
   Sub-part 1: Media-Independent Functionality
*/

static const struct tok msg_type_values[] = {
	{   0, "CAM" },
	{   1, "DENM" },
	{ 101, "TPEGM" },
	{ 102, "TSPDM" },
	{ 103, "VPM" },
	{ 104, "SRM" },
	{ 105, "SLAM" },
	{ 106, "ecoCAM" },
	{ 107, "ITM" },
	{ 150, "SA" },
	{   0, NULL }
};

static void
print_btp_body(const u_char *bp, u_int length)
{
	int version;
	int msg_type;
	const char *msg_type_str;

	if (length <= 2) {
		return;
	}

	/* Assuming ItsDpuHeader */
	version = bp[0];
	msg_type = bp[1];
	msg_type_str = tok2str(msg_type_values, "unknown (%u)", msg_type);

	printf("; ItsPduHeader v:%d t:%d-%s", version, msg_type, msg_type_str);
}

static void
print_btp(const u_char *bp)
{
	u_int16_t dest = EXTRACT_16BITS(bp+0);
	u_int16_t src = EXTRACT_16BITS(bp+2);
	printf("; BTP Dst:%u Src:%u", dest, src);
}

static void
print_long_pos_vector(const u_char *bp)
{
	int i;
	u_int32_t lat, lon;

	printf("GN_ADDR:");
	for (i=0; i<8; i++) {
		if (i) printf(":");
		printf("%02x", bp[i]);
	}
	printf(" ");

	lat = EXTRACT_32BITS(bp+12);
	printf("lat:%d ", lat);
	lon = EXTRACT_32BITS(bp+16);
	printf("lon:%d", lon);
}


/*
 * This is the top level routine of the printer.  'p' points
 * to the geonet header of the packet.
 */
void
geonet_print(netdissect_options *ndo, const u_char *eth, const u_char *bp, u_int length)
{
	printf("GeoNet src:%s; ", etheraddr_string(eth+6));

	if (length >= 36) {
		/* Process Common Header */
		int version = bp[0] >> 4;
		int next_hdr = bp[0] & 0x0f;
		int hdr_type = bp[1] >> 4;
		int hdr_subtype = bp[1] & 0x0f;
		u_int16_t payload_length = EXTRACT_16BITS(bp+4);
		int hop_limit = bp[7];
		const char *next_hdr_txt = "Unknown";
		const char *hdr_type_txt = "Unknown";
		int hdr_size = -1;

		switch (next_hdr) {
			case 0: next_hdr_txt = "Any"; break;
			case 1: next_hdr_txt = "BTP-A"; break;
			case 2: next_hdr_txt = "BTP-B"; break;
			case 3: next_hdr_txt = "IPv6"; break;
		}

		switch (hdr_type) {
			case 0: hdr_type_txt = "Any"; break;
			case 1: hdr_type_txt = "Beacon"; break;
			case 2: hdr_type_txt = "GeoUnicast"; break;
			case 3: switch (hdr_subtype) {
					case 0: hdr_type_txt = "GeoAnycastCircle"; break;
					case 1: hdr_type_txt = "GeoAnycastRect"; break;
					case 2: hdr_type_txt = "GeoAnycastElipse"; break;
				}
				break;
			case 4: switch (hdr_subtype) {
					case 0: hdr_type_txt = "GeoBroadcastCircle"; break;
					case 1: hdr_type_txt = "GeoBroadcastRect"; break;
					case 2: hdr_type_txt = "GeoBroadcastElipse"; break;
				}
				break;
			case 5: switch (hdr_subtype) {
					case 0: hdr_type_txt = "TopoScopeBcast-SH"; break;
					case 1: hdr_type_txt = "TopoScopeBcast-MH"; break;
				}
				break;
			case 6: switch (hdr_subtype) {
					case 0: hdr_type_txt = "LocService-Request"; break;
					case 1: hdr_type_txt = "LocService-Reply"; break;
				}
				break;
		}

		printf("v:%d ", version);
		printf("NH:%d-%s ", next_hdr, next_hdr_txt);
		printf("HT:%d-%d-%s ", hdr_type, hdr_subtype, hdr_type_txt);
		printf("HopLim:%d ", hop_limit);
		printf("Payload:%d ", payload_length);
        	print_long_pos_vector(bp + 8);

		/* Skip Common Header */
		length -= 36;
		bp += 36;

		/* Process Extended Headers */
		switch (hdr_type) {
			case 0: /* Any */
				hdr_size = 0;
				break;
			case 1: /* Beacon */
				hdr_size = 0;
				break;
			case 2: /* GeoUnicast */
				break;
			case 3: switch (hdr_subtype) {
					case 0: /* GeoAnycastCircle */
						break;
					case 1: /* GeoAnycastRect */
						break;
					case 2: /* GeoAnycastElipse */
						break;
				}
				break;
			case 4: switch (hdr_subtype) {
					case 0: /* GeoBroadcastCircle */
						break;
					case 1: /* GeoBroadcastRect */
						break;
					case 2: /* GeoBroadcastElipse */
						break;
				}
				break;
			case 5: switch (hdr_subtype) {
					case 0: /* TopoScopeBcast-SH */
						hdr_size = 0;
						break;
					case 1: /* TopoScopeBcast-MH */
						hdr_size = 68 - 36;
						break;
				}
				break;
			case 6: switch (hdr_subtype) {
					case 0: /* LocService-Request */
						break;
					case 1: /* LocService-Reply */
						break;
				}
				break;
		}

		/* Skip Extended headers */
		if (hdr_size >= 0) {
			length -= hdr_size;
			bp += hdr_size;
			switch (next_hdr) {
				case 0: /* Any */
					break;
				case 1:
				case 2: /* BTP A/B */
					print_btp(bp);
					length -= 4;
					bp += 4;
					print_btp_body(bp, length);
					break;
				case 3: /* IPv6 */
					break;
			}
		}
	} else {
		printf("Malformed (small) ");
	}

	/* Print user data part */
	if (ndo->ndo_vflag)
		default_print(bp, length);
}


/*
 * Local Variables:
 * c-style: whitesmith
 * c-basic-offset: 8
 * End:
 */
