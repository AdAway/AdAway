/*
 * Copyright (c) 2004 - Michael Richardson <mcr@xelerance.com>
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
 * Format and print bootp packets.
 */

#ifndef lint
static const char rcsid[] _U_ =
    "@(#) $Header: /tcpdump/master/tcpdump/print-eap.c,v 1.3 2004/04/23 19:03:39 mcr Exp $";
#endif

#ifdef HAVE_CONFIG_H
#include "config.h"
#endif

#include <tcpdump-stdinc.h>

#include <stdio.h>
#include <string.h>

#include "netdissect.h"
#include "addrtoname.h"
#include "extract.h"
#include "ether.h"

struct eap_packet_t {
	unsigned char	code;
	unsigned char	id;
	unsigned char	length[2];
	unsigned char	data[1];
};

/*
 * Print bootp requests
 */
void
eap_print(netdissect_options *ndo,
	  register const u_char *cp,
	  u_int length _U_)
{
	const struct eap_packet_t *eap;

	eap = (const struct eap_packet_t *)cp;
	ND_TCHECK(eap->data);

        ND_PRINT((ndo, "EAP code=%u id=%u length=%u ", 
		  eap->code, eap->id, (eap->length[0]<<8) + eap->length[1]));

        if (!ndo->ndo_vflag)
            return;

trunc:
	;
}

