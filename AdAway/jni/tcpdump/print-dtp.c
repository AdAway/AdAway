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

#ifdef HAVE_CONFIG_H
#include "config.h"
#endif

#include <tcpdump-stdinc.h>

#include <stdio.h>
#include <string.h>

#include "interface.h"
#include "addrtoname.h"
#include "extract.h"		
#include "nlpid.h"

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
dtp_print (const u_char *pptr, u_int length)
{
    int type, len;
    const u_char *tptr;

    if (length < DTP_HEADER_LEN)
        goto trunc;

    tptr = pptr; 

    if (!TTEST2(*tptr, DTP_HEADER_LEN))	
	goto trunc;

    printf("DTPv%u, length %u", 
           (*tptr),
           length);

    /*
     * In non-verbose mode, just print version.
     */
    if (vflag < 1) {
	return;
    }

    tptr += DTP_HEADER_LEN;

    while (tptr < (pptr+length)) {

        if (!TTEST2(*tptr, 4)) 
            goto trunc;

	type = EXTRACT_16BITS(tptr);
        len  = EXTRACT_16BITS(tptr+2); 

        /* infinite loop check */
        if (type == 0 || len == 0) {
            return;
        }

        printf("\n\t%s (0x%04x) TLV, length %u",
               tok2str(dtp_tlv_values, "Unknown", type),
               type, len);

        switch (type) {
	case DTP_DOMAIN_TLV:
		printf(", %s", tptr+4);
		break;

	case DTP_STATUS_TLV:            
	case DTP_DTP_TYPE_TLV:
                printf(", 0x%x", *(tptr+4));
                break;

	case DTP_NEIGHBOR_TLV:
                printf(", %s", etheraddr_string(tptr+4));
                break;

        default:
            break;
        }	
        tptr += len;
    }

    return;

 trunc:
    printf("[|dtp]");
}

/*
 * Local Variables:
 * c-style: whitesmith
 * c-basic-offset: 4
 * End:
 */
