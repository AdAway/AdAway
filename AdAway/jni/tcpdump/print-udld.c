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
 * UNIDIRECTIONAL LINK DETECTION (UDLD) as per 
 * http://www.ietf.org/internet-drafts/draft-foschiano-udld-02.txt
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

#define UDLD_HEADER_LEN			4
#define UDLD_DEVICE_ID_TLV		0x0001
#define UDLD_PORT_ID_TLV		0x0002
#define UDLD_ECHO_TLV			0x0003
#define UDLD_MESSAGE_INTERVAL_TLV	0x0004
#define UDLD_TIMEOUT_INTERVAL_TLV	0x0005
#define UDLD_DEVICE_NAME_TLV		0x0006
#define UDLD_SEQ_NUMBER_TLV		0x0007

static const struct tok udld_tlv_values[] = {
    { UDLD_DEVICE_ID_TLV, "Device-ID TLV"},
    { UDLD_PORT_ID_TLV, "Port-ID TLV"},
    { UDLD_ECHO_TLV, "Echo TLV"},
    { UDLD_MESSAGE_INTERVAL_TLV, "Message Interval TLV"},
    { UDLD_TIMEOUT_INTERVAL_TLV, "Timeout Interval TLV"},
    { UDLD_DEVICE_NAME_TLV, "Device Name TLV"},
    { UDLD_SEQ_NUMBER_TLV,"Sequence Number TLV"},
    { 0, NULL}
};

static const struct tok udld_code_values[] = {
    { 0x00, "Reserved"},
    { 0x01, "Probe message"},
    { 0x02, "Echo message"},
    { 0x03, "Flush message"},
    { 0, NULL}
};

static const struct tok udld_flags_values[] = {
    { 0x00, "RT"},
    { 0x01, "RSY"},
    { 0, NULL}
};

/*
 *
 * 0                   1                   2                   3 
 * 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+ 
 * | Ver | Opcode  |     Flags     |           Checksum            | 
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+ 
 * |               List of TLVs (variable length list)             | 
 * |                              ...                              | 
 * +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+ 
 *
 */

#define	UDLD_EXTRACT_VERSION(x) (((x)&0xe0)>>5) 
#define	UDLD_EXTRACT_OPCODE(x) ((x)&0x1f) 

void
udld_print (const u_char *pptr, u_int length)
{
    int code, type, len;
    const u_char *tptr;

    if (length < UDLD_HEADER_LEN)
        goto trunc;

    tptr = pptr; 

    if (!TTEST2(*tptr, UDLD_HEADER_LEN))	
	goto trunc;

    code = UDLD_EXTRACT_OPCODE(*tptr);

    printf("UDLDv%u, Code %s (%x), Flags [%s] (0x%02x), length %u", 
           UDLD_EXTRACT_VERSION(*tptr),
           tok2str(udld_code_values, "Reserved", code),
           code,
           bittok2str(udld_flags_values, "none", *(tptr+1)),
           *(tptr+1),
           length);

    /*
     * In non-verbose mode, just print version and opcode type
     */
    if (vflag < 1) {
	return;
    }

    printf("\n\tChecksum 0x%04x (unverified)", EXTRACT_16BITS(tptr+2));

    tptr += UDLD_HEADER_LEN;

    while (tptr < (pptr+length)) {

        if (!TTEST2(*tptr, 4)) 
            goto trunc;

	type = EXTRACT_16BITS(tptr);
        len  = EXTRACT_16BITS(tptr+2); 
        len -= 4;
        tptr += 4;

        /* infinite loop check */
        if (type == 0 || len == 0) {
            return;
        }

        printf("\n\t%s (0x%04x) TLV, length %u",
               tok2str(udld_tlv_values, "Unknown", type),
               type, len);

        switch (type) {
        case UDLD_DEVICE_ID_TLV:
        case UDLD_PORT_ID_TLV:
        case UDLD_ECHO_TLV:
        case UDLD_DEVICE_NAME_TLV: 
            printf(", %s", tptr);
            break;

        case UDLD_MESSAGE_INTERVAL_TLV: 
        case UDLD_TIMEOUT_INTERVAL_TLV:
            printf(", %us", (*tptr));
            break;

        case UDLD_SEQ_NUMBER_TLV:
            printf(", %u", EXTRACT_32BITS(tptr));
            break;

        default:
            break;
        }	
        tptr += len;
    }

    return;

 trunc:
    printf("[|udld]");
}

/*
 * Local Variables:
 * c-style: whitesmith
 * c-basic-offset: 4
 * End:
 */
