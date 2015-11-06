/*
 * Copyright (c) 2009
 * 	Siemens AG, All rights reserved.
 * 	Dmitry Eremin-Solenikov (dbaryshkov@gmail.com)
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
 */

#define NETDISSECT_REWORKED
#ifdef HAVE_CONFIG_H
#include "config.h"
#endif

#include <tcpdump-stdinc.h>

#include "interface.h"
#include "addrtoname.h"

#include "extract.h"

static const char *ftypes[] = {
	"Beacon",			/* 0 */
	"Data",				/* 1 */
	"ACK",				/* 2 */
	"Command",			/* 3 */
	"Reserved",			/* 4 */
	"Reserved",			/* 5 */
	"Reserved",			/* 6 */
	"Reserved",			/* 7 */
};

static int
extract_header_length(uint16_t fc)
{
	int len = 0;

	switch ((fc >> 10) & 0x3) {
	case 0x00:
		if (fc & (1 << 6)) /* intra-PAN with none dest addr */
			return -1;
		break;
	case 0x01:
		return -1;
	case 0x02:
		len += 4;
		break;
	case 0x03:
		len += 10;
		break;
	}

	switch ((fc >> 14) & 0x3) {
	case 0x00:
		break;
	case 0x01:
		return -1;
	case 0x02:
		len += 4;
		break;
	case 0x03:
		len += 10;
		break;
	}

	if (fc & (1 << 6)) {
		if (len < 2)
			return -1;
		len -= 2;
	}

	return len;
}


u_int
ieee802_15_4_if_print(netdissect_options *ndo,
                      const struct pcap_pkthdr *h, const u_char *p)
{
	u_int caplen = h->caplen;
	int hdrlen;
	uint16_t fc;
	uint8_t seq;

	if (caplen < 3) {
		ND_PRINT((ndo, "[|802.15.4] %x", caplen));
		return caplen;
	}

	fc = EXTRACT_LE_16BITS(p);
	hdrlen = extract_header_length(fc);

	seq = EXTRACT_LE_8BITS(p + 2);

	p += 3;
	caplen -= 3;

	ND_PRINT((ndo,"IEEE 802.15.4 %s packet ", ftypes[fc & 0x7]));
	if (ndo->ndo_vflag)
		ND_PRINT((ndo,"seq %02x ", seq));
	if (hdrlen == -1) {
		ND_PRINT((ndo,"malformed! "));
		return caplen;
	}


	if (!ndo->ndo_vflag) {
		p+= hdrlen;
		caplen -= hdrlen;
	} else {
		uint16_t panid = 0;

		switch ((fc >> 10) & 0x3) {
		case 0x00:
			ND_PRINT((ndo,"none "));
			break;
		case 0x01:
			ND_PRINT((ndo,"reserved destination addressing mode"));
			return 0;
		case 0x02:
			panid = EXTRACT_LE_16BITS(p);
			p += 2;
			ND_PRINT((ndo,"%04x:%04x ", panid, EXTRACT_LE_16BITS(p)));
			p += 2;
			break;
		case 0x03:
			panid = EXTRACT_LE_16BITS(p);
			p += 2;
			ND_PRINT((ndo,"%04x:%s ", panid, le64addr_string(p)));
			p += 8;
			break;
		}
		ND_PRINT((ndo,"< "));

		switch ((fc >> 14) & 0x3) {
		case 0x00:
			ND_PRINT((ndo,"none "));
			break;
		case 0x01:
			ND_PRINT((ndo,"reserved source addressing mode"));
			return 0;
		case 0x02:
			if (!(fc & (1 << 6))) {
				panid = EXTRACT_LE_16BITS(p);
				p += 2;
			}
			ND_PRINT((ndo,"%04x:%04x ", panid, EXTRACT_LE_16BITS(p)));
			p += 2;
			break;
		case 0x03:
			if (!(fc & (1 << 6))) {
				panid = EXTRACT_LE_16BITS(p);
				p += 2;
			}
                        ND_PRINT((ndo,"%04x:%s ", panid, le64addr_string(p)));
			p += 8;
			break;
		}

		caplen -= hdrlen;
	}

	if (!ndo->ndo_suppress_default_print)
		ND_DEFAULTPRINT(p, caplen);

	return 0;
}
