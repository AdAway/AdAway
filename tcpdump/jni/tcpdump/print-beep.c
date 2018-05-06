/*
 * Copyright (C) 2000, Richard Sharpe
 *
 * This software may be distributed either under the terms of the
 * BSD-style licence that accompanies tcpdump or under the GNU GPL
 * version 2 or later.
 *
 * print-beep.c
 *
 */

#define NETDISSECT_REWORKED
#ifdef HAVE_CONFIG_H
#include "config.h"
#endif

#include <tcpdump-stdinc.h>

#include <string.h>

#include "interface.h"

/* Check for a string but not go beyond length
 * Return TRUE on match, FALSE otherwise
 *
 * Looks at the first few chars up to tl1 ...
 */

static int
l_strnstart(const char *tstr1, u_int tl1, const char *str2, u_int l2)
{

	if (tl1 > l2)
		return 0;

	return (strncmp(tstr1, str2, tl1) == 0 ? 1 : 0);
}

void
beep_print(netdissect_options *ndo, const u_char *bp, u_int length)
{

	if (l_strnstart("MSG", 4, (const char *)bp, length)) /* A REQuest */
		ND_PRINT((ndo, " BEEP MSG"));
	else if (l_strnstart("RPY ", 4, (const char *)bp, length))
		ND_PRINT((ndo, " BEEP RPY"));
	else if (l_strnstart("ERR ", 4, (const char *)bp, length))
		ND_PRINT((ndo, " BEEP ERR"));
	else if (l_strnstart("ANS ", 4, (const char *)bp, length))
		ND_PRINT((ndo, " BEEP ANS"));
	else if (l_strnstart("NUL ", 4, (const char *)bp, length))
		ND_PRINT((ndo, " BEEP NUL"));
	else if (l_strnstart("SEQ ", 4, (const char *)bp, length))
		ND_PRINT((ndo, " BEEP SEQ"));
	else if (l_strnstart("END", 4, (const char *)bp, length))
		ND_PRINT((ndo, " BEEP END"));
	else
		ND_PRINT((ndo, " BEEP (payload or undecoded)"));
}
