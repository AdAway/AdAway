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
    "@(#) $Header: /tcpdump/master/tcpdump/print-sip.c,v 1.1 2004/07/27 17:04:20 hannes Exp $";
#endif

#ifdef HAVE_CONFIG_H
#include "config.h"
#endif

#include <tcpdump-stdinc.h>

#include <stdio.h>
#include <stdlib.h>

#include "interface.h"
#include "extract.h"

#include "udp.h"

void
sip_print(register const u_char *pptr, register u_int len)
{
    u_int idx;

    printf("SIP, length: %u%s", len, vflag ? "\n\t" : "");

    /* in non-verbose mode just lets print the protocol and length */
    if (vflag < 1)
        return;

    for (idx = 0; idx < len; idx++) {
        if (EXTRACT_16BITS(pptr+idx) != 0x0d0a) { /* linefeed ? */
            safeputchar(*(pptr+idx));
        } else {
            printf("\n\t");
            idx+=1;
        }
    }

    /* do we want to see an additionally hexdump ? */
    if (vflag> 1)
        print_unknown_data(pptr,"\n\t",len);

    return;
}
