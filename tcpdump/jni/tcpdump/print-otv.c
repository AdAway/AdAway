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

#define NETDISSECT_REWORKED
#ifdef HAVE_CONFIG_H
#include "config.h"
#endif

#include <tcpdump-stdinc.h>

#include "interface.h"
#include "extract.h"

/*
 * OTV header, draft-hasmit-otv-04
 *
 *     0                   1                   2                   3
 *     0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1 2 3 4 5 6 7 8 9 0 1
 *     +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *     |R|R|R|R|I|R|R|R|           Overlay ID                          |
 *     +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 *     |          Instance ID                          | Reserved      |
 *     +-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+-+
 */

void
otv_print(netdissect_options *ndo, const u_char *bp, u_int len)
{
    uint8_t flags;
    uint32_t overlay_id;
    uint32_t instance_id;

    if (len < 8) {
        ND_PRINT((ndo, "[|OTV]"));
        return;
    }

    flags = *bp;
    bp += 1;

    overlay_id = EXTRACT_24BITS(bp);
    bp += 3;

    instance_id = EXTRACT_24BITS(bp);
    bp += 4;

    ND_PRINT((ndo, "OTV, "));
    ND_PRINT((ndo, "flags [%s] (0x%02x), ", flags & 0x08 ? "I" : ".", flags));
    ND_PRINT((ndo, "overlay %u, ", overlay_id));
    ND_PRINT((ndo, "instance %u\n", instance_id));

    ether_print(ndo, bp, len - 8, len - 8, NULL, NULL);
}
