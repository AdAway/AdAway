/*
 * Copyright (c) 1998-2007 The TCPDUMP project
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
 * Dynamic Trunk Protocol (DTP)
 *
 * Original code by Carles Kishimoto <carles.kishimoto@gmail.com>
 */

#define NETDISSECT_REWORKED
#ifdef HAVE_CONFIG_H
#include "config.h"
#endif

#include <tcpdump-stdinc.h>

#include "interface.h"
#include "addrtoname.h"
#include "extract.h"

#define DTP_HEADER_LEN			1
#define DTP_DOMAIN_TLV			0x0001
#define DTP_STATUS_TLV			0x0002
#define DTP_DTP_TYPE_TLV		0x0003
#define DTP_NEIGHBOR_TLV		0x0004

static const struct tok dtp_tlv_values[] = {
    { DTP_DOMAIN_TLV, "Domain TLV"},
    { DTP_STATUS_TLV, "Status TLV"},
    { DTP_DTP_TYPE_TLV, "DTP type TLV"},
    { DTP_NEIGHBOR_TLV, "Neighbor TLV"},
    { 0, NULL}
};

void
dtp_print (netdissect_options *ndo, const u_char *pptr, u_int length)
{
    int type, len;
    const u_char *tptr;

    if (length < DTP_HEADER_LEN)
        goto trunc;

    tptr = pptr;

    ND_TCHECK2(*tptr, DTP_HEADER_LEN);

    ND_PRINT((ndo, "DTPv%u, length %u",
           (*tptr),
           length));

    /*
     * In non-verbose mode, just print version.
     */
    if (ndo->ndo_vflag < 1) {
	return;
    }

    tptr += DTP_HEADER_LEN;

    while (tptr < (pptr+length)) {

        ND_TCHECK2(*tptr, 4);

	type = EXTRACT_16BITS(tptr);
        len  = EXTRACT_16BITS(tptr+2);

        /* infinite loop check */
        if (type == 0 || len == 0) {
            return;
        }

        ND_PRINT((ndo, "\n\t%s (0x%04x) TLV, length %u",
               tok2str(dtp_tlv_values, "Unknown", type),
               type, len));

        switch (type) {
	case DTP_DOMAIN_TLV:
		ND_PRINT((ndo, ", %s", tptr+4));
		break;

	case DTP_STATUS_TLV:
	case DTP_DTP_TYPE_TLV:
                ND_PRINT((ndo, ", 0x%x", *(tptr+4)));
                break;

	case DTP_NEIGHBOR_TLV:
                ND_PRINT((ndo, ", %s", etheraddr_string(ndo, tptr+4)));
                break;

        default:
            break;
        }
        tptr += len;
    }

    return;

 trunc:
    ND_PRINT((ndo, "[|dtp]"));
}

/*
 * Local Variables:
 * c-style: whitesmith
 * c-basic-offset: 4
 * End:
 */
