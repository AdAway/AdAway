/*
 * Copyright (c) 1990, 1991, 1992, 1993, 1994, 1995, 1996, 1997
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
 * Format and print ntp packets.
 *	By Jeffrey Mogul/DECWRL
 *	loosely based on print-bootp.c
 */

#ifndef lint
static const char rcsid[] _U_ =
    "@(#) $Header: /tcpdump/master/tcpdump/print-ntp.c,v 1.41.2.1 2005/05/06 07:57:18 guy Exp $ (LBL)";
#endif

#ifdef HAVE_CONFIG_H
#include "config.h"
#endif

#include <tcpdump-stdinc.h>

#include <stdio.h>
#include <string.h>
#ifdef HAVE_STRFTIME
#include <time.h>
#endif

#include "interface.h"
#include "addrtoname.h"
#include "extract.h"
#ifdef MODEMASK
#undef MODEMASK					/* Solaris sucks */
#endif
#include "ntp.h"

static void p_sfix(const struct s_fixedpt *);
static void p_ntp_time(const struct l_fixedpt *);
static void p_ntp_delta(const struct l_fixedpt *, const struct l_fixedpt *);

static struct tok ntp_mode_values[] = {
    { MODE_UNSPEC,    "unspecified" },
    { MODE_SYM_ACT,   "symmetric active" },
    { MODE_SYM_PAS,   "symmetric passive" },
    { MODE_CLIENT,    "Client" },
    { MODE_SERVER,    "Server" },
    { MODE_BROADCAST, "Broadcast" },
    { MODE_RES1,      "Reserved" },
    { MODE_RES2,      "Reserved" },
    { 0, NULL }
};

static struct tok ntp_leapind_values[] = {
    { NO_WARNING,     "" },
    { PLUS_SEC,       "+1s" },
    { MINUS_SEC,      "-1s" },
    { ALARM,          "clock unsynchronized" },
    { 0, NULL }
};

/*
 * Print ntp requests
 */
void
ntp_print(register const u_char *cp, u_int length)
{
	register const struct ntpdata *bp;
	int mode, version, leapind;

	bp = (struct ntpdata *)cp;

	TCHECK(bp->status);

	version = (int)(bp->status & VERSIONMASK) >> 3;
	printf("NTPv%d", version);

	mode = bp->status & MODEMASK;
        if (!vflag) {
            printf (", %s, length %u",
                    tok2str(ntp_mode_values, "Unknown mode", mode),
                    length);
            return;
        }
        
        printf (", length %u\n\t%s",
                length,
                tok2str(ntp_mode_values, "Unknown mode", mode));        

	leapind = bp->status & LEAPMASK;
        printf (", Leap indicator: %s (%u)",
                tok2str(ntp_leapind_values, "Unknown", leapind),
                leapind);

	TCHECK(bp->stratum);
	printf(", Stratum %u", bp->stratum);

	TCHECK(bp->ppoll);
	printf(", poll %us", bp->ppoll);

	/* Can't TCHECK bp->precision bitfield so bp->distance + 0 instead */
	TCHECK2(bp->root_delay, 0);
	printf(", precision %d", bp->precision);

	TCHECK(bp->root_delay);
	fputs("\n\tRoot Delay: ", stdout);
	p_sfix(&bp->root_delay);

	TCHECK(bp->root_dispersion);
	fputs(", Root dispersion: ", stdout);
	p_sfix(&bp->root_dispersion);

	TCHECK(bp->refid);
	fputs(", Reference-ID: ", stdout);
	/* Interpretation depends on stratum */
	switch (bp->stratum) {

	case UNSPECIFIED:
		printf("(unspec)");
		break;

	case PRIM_REF:
		if (fn_printn((u_char *)&(bp->refid), 4, snapend))
			goto trunc;
		break;

	case INFO_QUERY:
		printf("%s INFO_QUERY", ipaddr_string(&(bp->refid)));
		/* this doesn't have more content */
		return;

	case INFO_REPLY:
		printf("%s INFO_REPLY", ipaddr_string(&(bp->refid)));
		/* this is too complex to be worth printing */
		return;

	default:
		printf("%s", ipaddr_string(&(bp->refid)));
		break;
	}

	TCHECK(bp->ref_timestamp);
	fputs("\n\t  Reference Timestamp:  ", stdout);
	p_ntp_time(&(bp->ref_timestamp));

	TCHECK(bp->org_timestamp);
	fputs("\n\t  Originator Timestamp: ", stdout);
	p_ntp_time(&(bp->org_timestamp));

	TCHECK(bp->rec_timestamp);
	fputs("\n\t  Receive Timestamp:    ", stdout);
	p_ntp_time(&(bp->rec_timestamp));

	TCHECK(bp->xmt_timestamp);
	fputs("\n\t  Transmit Timestamp:   ", stdout);
	p_ntp_time(&(bp->xmt_timestamp));

	fputs("\n\t    Originator - Receive Timestamp:  ", stdout);
	p_ntp_delta(&(bp->org_timestamp), &(bp->rec_timestamp));

	fputs("\n\t    Originator - Transmit Timestamp: ", stdout);
	p_ntp_delta(&(bp->org_timestamp), &(bp->xmt_timestamp));

        /* FIXME key-id, authentication */

	return;

trunc:
	fputs(" [|ntp]", stdout);
}

static void
p_sfix(register const struct s_fixedpt *sfp)
{
	register int i;
	register int f;
	register float ff;

	i = EXTRACT_16BITS(&sfp->int_part);
	f = EXTRACT_16BITS(&sfp->fraction);
	ff = f / 65536.0;	/* shift radix point by 16 bits */
	f = ff * 1000000.0;	/* Treat fraction as parts per million */
	printf("%d.%06d", i, f);
}

#define	FMAXINT	(4294967296.0)	/* floating point rep. of MAXINT */

static void
p_ntp_time(register const struct l_fixedpt *lfp)
{
	register int32_t i;
	register u_int32_t uf;
	register u_int32_t f;
	register float ff;

	i = EXTRACT_32BITS(&lfp->int_part);
	uf = EXTRACT_32BITS(&lfp->fraction);
	ff = uf;
	if (ff < 0.0)		/* some compilers are buggy */
		ff += FMAXINT;
	ff = ff / FMAXINT;	/* shift radix point by 32 bits */
	f = ff * 1000000000.0;	/* treat fraction as parts per billion */
	printf("%u.%09d", i, f);

#ifdef HAVE_STRFTIME
	/*
	 * print the time in human-readable format.
	 */
	if (i) {
	    time_t seconds = i - JAN_1970;
	    struct tm *tm;
	    char time_buf[128];

	    tm = localtime(&seconds);
	    strftime(time_buf, sizeof (time_buf), "%Y/%m/%d %H:%M:%S", tm);
	    printf (" (%s)", time_buf);
	}
#endif
}

/* Prints time difference between *lfp and *olfp */
static void
p_ntp_delta(register const struct l_fixedpt *olfp,
	    register const struct l_fixedpt *lfp)
{
	register int32_t i;
	register u_int32_t u, uf;
	register u_int32_t ou, ouf;
	register u_int32_t f;
	register float ff;
	int signbit;

	u = EXTRACT_32BITS(&lfp->int_part);
	ou = EXTRACT_32BITS(&olfp->int_part);
	uf = EXTRACT_32BITS(&lfp->fraction);
	ouf = EXTRACT_32BITS(&olfp->fraction);
	if (ou == 0 && ouf == 0) {
		p_ntp_time(lfp);
		return;
	}

	i = u - ou;

	if (i > 0) {		/* new is definitely greater than old */
		signbit = 0;
		f = uf - ouf;
		if (ouf > uf)	/* must borrow from high-order bits */
			i -= 1;
	} else if (i < 0) {	/* new is definitely less than old */
		signbit = 1;
		f = ouf - uf;
		if (uf > ouf)	/* must carry into the high-order bits */
			i += 1;
		i = -i;
	} else {		/* int_part is zero */
		if (uf > ouf) {
			signbit = 0;
			f = uf - ouf;
		} else {
			signbit = 1;
			f = ouf - uf;
		}
	}

	ff = f;
	if (ff < 0.0)		/* some compilers are buggy */
		ff += FMAXINT;
	ff = ff / FMAXINT;	/* shift radix point by 32 bits */
	f = ff * 1000000000.0;	/* treat fraction as parts per billion */
	if (signbit)
		putchar('-');
	else
		putchar('+');
	printf("%d.%09d", i, f);
}

