/*	$NetBSD: print-mobile.c,v 1.2 1998/09/30 08:57:01 hwr Exp $ */

/*
 * (c) 1998 The NetBSD Foundation, Inc.
 * All rights reserved.
 *
 * This code is derived from software contributed to The NetBSD Foundation
 * by Heiko W.Rupp <hwr@pilhuhn.de>
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 * 3. All advertising materials mentioning features or use of this software
 *    must display the following acknowledgement:
 *        This product includes software developed by the NetBSD
 *        Foundation, Inc. and its contributors.
 * 4. Neither the name of The NetBSD Foundation nor the names of its
 *    contributors may be used to endorse or promote products derived
 *    from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE NETBSD FOUNDATION, INC. AND CONTRIBUTORS
 * ``AS IS'' AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED
 * TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR
 * PURPOSE ARE DISCLAIMED.  IN NO EVENT SHALL THE FOUNDATION OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

#ifdef HAVE_CONFIG_H
#include "config.h"
#endif

#ifndef lint
static const char rcsid[] _U_ =
     "@(#) $Header: /tcpdump/master/tcpdump/print-mobile.c,v 1.15 2004-03-24 01:58:14 guy Exp $";
#endif

#include <tcpdump-stdinc.h>

#include <stdio.h>

#include "interface.h"
#include "addrtoname.h"
#include "extract.h"		/* must come after interface.h */

#define MOBILE_SIZE (8)

struct mobile_ip {
	u_int16_t proto;
	u_int16_t hcheck;
	u_int32_t odst;
	u_int32_t osrc;
};

#define OSRC_PRES	0x0080	/* old source is present */

/*
 * Deencapsulate and print a mobile-tunneled IP datagram
 */
void
mobile_print(const u_char *bp, u_int length)
{
	const u_char *cp = bp +8 ;
	const struct mobile_ip *mob;
	struct cksum_vec vec[1];
	u_short proto,crc;
	u_char osp =0;			/* old source address present */

	mob = (const struct mobile_ip *)bp;

	if (length < MOBILE_SIZE || !TTEST(*mob)) {
		fputs("[|mobile]", stdout);
		return;
	}
	fputs("mobile: ", stdout);

	proto = EXTRACT_16BITS(&mob->proto);
	crc =  EXTRACT_16BITS(&mob->hcheck);
	if (proto & OSRC_PRES) {
		osp=1;
		cp +=4 ;
	}

	if (osp)  {
		fputs("[S] ",stdout);
		if (vflag)
			(void)printf("%s ",ipaddr_string(&mob->osrc));
	} else {
		fputs("[] ",stdout);
	}
	if (vflag) {
		(void)printf("> %s ",ipaddr_string(&mob->odst));
		(void)printf("(oproto=%d)",proto>>8);
	}
	vec[0].ptr = (const u_int8_t *)(void *)mob;
	vec[0].len = osp ? 12 : 8;
	if (in_cksum(vec, 1)!=0) {
		(void)printf(" (bad checksum %d)",crc);
	}

	return;
}
