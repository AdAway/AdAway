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
 * Original code by Francesco Fondelli (francesco dot fondelli, gmail dot com)
 */

#ifdef HAVE_CONFIG_H
#include "config.h"
#endif

#include <tcpdump-stdinc.h>

#include <stdio.h>
#include <stdlib.h>

#include "interface.h"
#include "extract.h"
#include "addrtoname.h"

#include "udp.h"

/*
 * VXLAN header, draft-mahalingam-dutt-dcops-vxlan-03
 *
 *     0                   1                   2                   3
 *     0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 *    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *    |R|R|R|R|I|R|R|R|            Reserved                           |
 *    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *    |                VXLAN Network Identifier (VNI) |   Reserved    |
 *    +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+ 
 */

void
vxlan_print(const u_char *bp, u_int len)
{
    u_int8_t flags;
    u_int32_t vni;
    
    if (len < 8) {
        printf("[|VXLAN]");
        return;
    }

    flags = *bp;
    bp += 4;

    vni = EXTRACT_24BITS(bp);
    bp += 4;

    printf("VXLAN, ");

    fputs("flags [", stdout);
    if (flags & 0x08)
        fputs("I", stdout);
    else
        fputs(".", stdout);
    fputs("] ", stdout);

    printf("(0x%02x), ", flags);
    printf("vni %u\n", vni);

    ether_print(gndo, bp, len - 8, len - 8, NULL, NULL);
    return;
}
