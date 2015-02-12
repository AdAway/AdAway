/*
 * Copyright (c) 2013 The TCPDUMP project
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
 * Original code by Ola Martin Lykkja (ola.lykkja@q-free.com)
 */

#ifdef HAVE_CONFIG_H
#include "config.h"
#endif

#include <tcpdump-stdinc.h>

#include <pcap.h>
#include <stdio.h>
#include <string.h>

#include "interface.h"
#include "extract.h"
#include "addrtoname.h"

/*
   ISO 29281:2009
   Intelligent Transport Systems . Communications access for land mobiles (CALM)
   CALM non-IP networking
*/

/*
 * This is the top level routine of the printer.  'bp' points
 * to the calm header of the packet.
 */
void
calm_fast_print(netdissect_options *ndo, const u_char *eth, const u_char *bp, u_int length)
{
	int srcNwref = bp[0];
	int dstNwref = bp[1];
	length -= 2;
	bp += 2;

	printf("CALM FAST src:%s; ", etheraddr_string(eth+6));
	printf("SrcNwref:%d; ", srcNwref);
	printf("DstNwref:%d; ", dstNwref);

	if (ndo->ndo_vflag)
		default_print(bp, length);
}


/*
 * Local Variables:
 * c-style: whitesmith
 * c-basic-offset: 8
 * End:
 */
