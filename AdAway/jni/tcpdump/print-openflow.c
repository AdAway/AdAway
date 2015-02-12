/*
 * This module implements printing of the very basic (version-independent)
 * OpenFlow header and iteration over OpenFlow messages. It is intended for
 * dispatching of version-specific OpenFlow message decoding.
 *
 *
 * Copyright (c) 2013 The TCPDUMP project
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 * FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN
 * ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

#ifdef HAVE_CONFIG_H
#include "config.h"
#endif

#include <tcpdump-stdinc.h>

#include "interface.h"
#include "extract.h"
#include "openflow.h"

#define OF_VER_1_0    0x01

static void
of_header_print(const uint8_t version, const uint8_t type,
                      const uint16_t length, const uint32_t xid) {
	printf("\n\tversion unknown (0x%02x), type 0x%02x, length %u, xid 0x%08x",
	       version, type, length, xid);
}

/* Print a single OpenFlow message. */
static const u_char *
of_header_body_print(const u_char *cp, const u_char *ep) {
	uint8_t version, type;
	uint16_t length;
	uint32_t xid;

	if (ep < cp + OF_HEADER_LEN)
		goto corrupt;
	/* version */
	TCHECK2(*cp, 1);
	version = *cp;
	cp += 1;
	/* type */
	TCHECK2(*cp, 1);
	type = *cp;
	cp += 1;
	/* length */
	TCHECK2(*cp, 2);
	length = EXTRACT_16BITS(cp);
	cp += 2;
	/* xid */
	TCHECK2(*cp, 4);
	xid = EXTRACT_32BITS(cp);
	cp += 4;
	/* Message length includes the header length and a message always includes
	 * the basic header. A message length underrun fails decoding of the rest of
	 * the current packet. At the same time, try decoding as much of the current
	 * message as possible even when it does not end within the current TCP
	 * segment. */
	if (length < OF_HEADER_LEN) {
		of_header_print(version, type, length, xid);
		goto corrupt;
	}
	/* Decode known protocol versions further without printing the header (the
	 * type decoding is version-specific. */
	switch (version) {
	case OF_VER_1_0:
		return of10_header_body_print(cp, ep, type, length, xid);
	default:
		of_header_print(version, type, length, xid);
		TCHECK2(*cp, length - OF_HEADER_LEN);
		return cp + length - OF_HEADER_LEN; /* done with current message */
	}

corrupt: /* fail current packet */
	printf(" (corrupt)");
	TCHECK2(*cp, ep - cp);
	return ep;
trunc:
	printf(" [|openflow]");
	return ep;
}

/* Print a TCP segment worth of OpenFlow messages presuming the segment begins
 * on a message boundary. */
void
openflow_print(const u_char *cp, const u_int len) {
	const u_char *ep = cp + len;

	printf(": OpenFlow");
	while (cp < ep)
		cp = of_header_body_print(cp, ep);
}
