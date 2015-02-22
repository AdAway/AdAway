/*
 * Copyright (c) 1990, 1991, 1993, 1994, 1995, 1996, 1997
 *	The Regents of the University of California.  All rights reserved.
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
 * Format and print trivial file transfer protocol packets.
 */

#ifndef lint
static const char rcsid[] _U_ =
    "@(#) $Header: /tcpdump/master/tcpdump/print-tftp.c,v 1.39 2008-04-11 16:47:38 gianluca Exp $ (LBL)";
#endif

#ifdef HAVE_CONFIG_H
#include "config.h"
#endif

#include <tcpdump-stdinc.h>

#ifdef SEGSIZE
#undef SEGSIZE					/* SINIX sucks */
#endif

#include <stdio.h>
#include <string.h>

#include "interface.h"
#include "addrtoname.h"
#include "extract.h"
#include "tftp.h"

/* op code to string mapping */
static const struct tok op2str[] = {
	{ RRQ,		"RRQ" },	/* read request */
	{ WRQ,		"WRQ" },	/* write request */
	{ DATA,		"DATA" },	/* data packet */
	{ ACK,		"ACK" },	/* acknowledgement */
	{ TFTP_ERROR,	"ERROR" },	/* error code */
	{ OACK,		"OACK" },	/* option acknowledgement */
	{ 0,		NULL }
};

/* error code to string mapping */
static const struct tok err2str[] = {
	{ EUNDEF,	"EUNDEF" },	/* not defined */
	{ ENOTFOUND,	"ENOTFOUND" },	/* file not found */
	{ EACCESS,	"EACCESS" },	/* access violation */
	{ ENOSPACE,	"ENOSPACE" },	/* disk full or allocation exceeded */
	{ EBADOP,	"EBADOP" },	/* illegal TFTP operation */
	{ EBADID,	"EBADID" },	/* unknown transfer ID */
	{ EEXISTS,	"EEXISTS" },	/* file already exists */
	{ ENOUSER,	"ENOUSER" },	/* no such user */
	{ 0,		NULL }
};

/*
 * Print trivial file transfer program requests
 */
void
tftp_print(register const u_char *bp, u_int length)
{
	register const struct tftphdr *tp;
	register const char *cp;
	register const u_char *p;
	register int opcode, i;
	static char tstr[] = " [|tftp]";

	tp = (const struct tftphdr *)bp;

	/* Print length */
	printf(" %d", length);

	/* Print tftp request type */
	TCHECK(tp->th_opcode);
	opcode = EXTRACT_16BITS(&tp->th_opcode);
	cp = tok2str(op2str, "tftp-#%d", opcode);
	printf(" %s", cp);
	/* Bail if bogus opcode */
	if (*cp == 't')
		return;

	switch (opcode) {

	case RRQ:
	case WRQ:
	case OACK:
		p = (u_char *)tp->th_stuff;
		putchar(' ');
		/* Print filename or first option */
		if (opcode != OACK)
			putchar('"');
		i = fn_print(p, snapend);
		if (opcode != OACK)
			putchar('"');

		/* Print the mode (RRQ and WRQ only) and any options */
		while ((p = (const u_char *)strchr((const char *)p, '\0')) != NULL) {
			if (length <= (u_int)(p - (const u_char *)&tp->th_block))
				break;
			p++;
			if (*p != '\0') {
				putchar(' ');
				fn_print(p, snapend);
			}
		}
		
		if (i)
			goto trunc;
		break;

	case ACK:
	case DATA:
		TCHECK(tp->th_block);
		printf(" block %d", EXTRACT_16BITS(&tp->th_block));
		break;

	case TFTP_ERROR:
		/* Print error code string */
		TCHECK(tp->th_code);
		printf(" %s \"", tok2str(err2str, "tftp-err-#%d \"",
				       EXTRACT_16BITS(&tp->th_code)));
		/* Print error message string */
		i = fn_print((const u_char *)tp->th_data, snapend);
		putchar('"');
		if (i)
			goto trunc;
		break;

	default:
		/* We shouldn't get here */
		printf("(unknown #%d)", opcode);
		break;
	}
	return;
trunc:
	fputs(tstr, stdout);
	return;
}
