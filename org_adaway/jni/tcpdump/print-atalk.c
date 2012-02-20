/*
 * Copyright (c) 1988, 1989, 1990, 1991, 1992, 1993, 1994, 1995, 1996, 1997
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
 * Format and print AppleTalk packets.
 */

#ifndef lint
static const char rcsid[] _U_ =
    "@(#) $Header: /tcpdump/master/tcpdump/print-atalk.c,v 1.81 2004/05/01 09:41:50 hannes Exp $ (LBL)";
#endif

#ifdef HAVE_CONFIG_H
#include "config.h"
#endif

#include <tcpdump-stdinc.h>

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <pcap.h>

#include "interface.h"
#include "addrtoname.h"
#include "ethertype.h"
#include "extract.h"			/* must come after interface.h */
#include "appletalk.h"

static struct tok type2str[] = {
	{ ddpRTMP,		"rtmp" },
	{ ddpRTMPrequest,	"rtmpReq" },
	{ ddpECHO,		"echo" },
	{ ddpIP,		"IP" },
	{ ddpARP,		"ARP" },
	{ ddpKLAP,		"KLAP" },
	{ 0,			NULL }
};

struct aarp {
	u_int16_t	htype, ptype;
	u_int8_t	halen, palen;
	u_int16_t	op;
	u_int8_t	hsaddr[6];
	u_int8_t	psaddr[4];
	u_int8_t	hdaddr[6];
	u_int8_t	pdaddr[4];
};

static char tstr[] = "[|atalk]";

static void atp_print(const struct atATP *, u_int);
static void atp_bitmap_print(u_char);
static void nbp_print(const struct atNBP *, u_int, u_short, u_char, u_char);
static const char *print_cstring(const char *, const u_char *);
static const struct atNBPtuple *nbp_tuple_print(const struct atNBPtuple *,
						const u_char *,
						u_short, u_char, u_char);
static const struct atNBPtuple *nbp_name_print(const struct atNBPtuple *,
					       const u_char *);
static const char *ataddr_string(u_short, u_char);
static void ddp_print(const u_char *, u_int, int, u_short, u_char, u_char);
static const char *ddpskt_string(int);

/*
 * Print LLAP packets received on a physical LocalTalk interface.
 */
u_int
ltalk_if_print(const struct pcap_pkthdr *h, const u_char *p)
{
	return (llap_print(p, h->caplen));
}

/*
 * Print AppleTalk LLAP packets.
 */
u_int
llap_print(register const u_char *bp, u_int length)
{
	register const struct LAP *lp;
	register const struct atDDP *dp;
	register const struct atShortDDP *sdp;
	u_short snet;
	u_int hdrlen;

	/*
	 * Our packet is on a 4-byte boundary, as we're either called
	 * directly from a top-level link-layer printer (ltalk_if_print)
	 * or from the UDP printer.  The LLAP+DDP header is a multiple
	 * of 4 bytes in length, so the DDP payload is also on a 4-byte
	 * boundary, and we don't need to align it before calling
	 * "ddp_print()".
	 */
	lp = (const struct LAP *)bp;
	bp += sizeof(*lp);
	length -= sizeof(*lp);
	hdrlen = sizeof(*lp);
	switch (lp->type) {

	case lapShortDDP:
		if (length < ddpSSize) {
			(void)printf(" [|sddp %d]", length);
			return (length);
		}
		sdp = (const struct atShortDDP *)bp;
		printf("%s.%s",
		    ataddr_string(0, lp->src), ddpskt_string(sdp->srcSkt));
		printf(" > %s.%s:",
		    ataddr_string(0, lp->dst), ddpskt_string(sdp->dstSkt));
		bp += ddpSSize;
		length -= ddpSSize;
		hdrlen += ddpSSize;
		ddp_print(bp, length, sdp->type, 0, lp->src, sdp->srcSkt);
		break;

	case lapDDP:
		if (length < ddpSize) {
			(void)printf(" [|ddp %d]", length);
			return (length);
		}
		dp = (const struct atDDP *)bp;
		snet = EXTRACT_16BITS(&dp->srcNet);
		printf("%s.%s", ataddr_string(snet, dp->srcNode),
		    ddpskt_string(dp->srcSkt));
		printf(" > %s.%s:",
		    ataddr_string(EXTRACT_16BITS(&dp->dstNet), dp->dstNode),
		    ddpskt_string(dp->dstSkt));
		bp += ddpSize;
		length -= ddpSize;
		hdrlen += ddpSize;
		ddp_print(bp, length, dp->type, snet, dp->srcNode, dp->srcSkt);
		break;

#ifdef notdef
	case lapKLAP:
		klap_print(bp, length);
		break;
#endif

	default:
		printf("%d > %d at-lap#%d %d",
		    lp->src, lp->dst, lp->type, length);
		break;
	}
	return (hdrlen);
}

/*
 * Print EtherTalk/TokenTalk packets (or FDDITalk, or whatever it's called
 * when it runs over FDDI; yes, I've seen FDDI captures with AppleTalk
 * packets in them).
 */
void
atalk_print(register const u_char *bp, u_int length)
{
	register const struct atDDP *dp;
	u_short snet;

        if(!eflag)
            printf("AT ");

	if (length < ddpSize) {
		(void)printf(" [|ddp %d]", length);
		return;
	}
	dp = (const struct atDDP *)bp;
	snet = EXTRACT_16BITS(&dp->srcNet);
	printf("%s.%s", ataddr_string(snet, dp->srcNode),
	       ddpskt_string(dp->srcSkt));
	printf(" > %s.%s: ",
	       ataddr_string(EXTRACT_16BITS(&dp->dstNet), dp->dstNode),
	       ddpskt_string(dp->dstSkt));
	bp += ddpSize;
	length -= ddpSize;
	ddp_print(bp, length, dp->type, snet, dp->srcNode, dp->srcSkt);
}

/* XXX should probably pass in the snap header and do checks like arp_print() */
void
aarp_print(register const u_char *bp, u_int length)
{
	register const struct aarp *ap;

#define AT(member) ataddr_string((ap->member[1]<<8)|ap->member[2],ap->member[3])

	printf("aarp ");
	ap = (const struct aarp *)bp;
	if (EXTRACT_16BITS(&ap->htype) == 1 &&
	    EXTRACT_16BITS(&ap->ptype) == ETHERTYPE_ATALK &&
	    ap->halen == 6 && ap->palen == 4 )
		switch (EXTRACT_16BITS(&ap->op)) {

		case 1:				/* request */
			(void)printf("who-has %s tell %s",
			    AT(pdaddr), AT(psaddr));
			return;

		case 2:				/* response */
			(void)printf("reply %s is-at %s",
			    AT(pdaddr), etheraddr_string(ap->hdaddr));
			return;

		case 3:				/* probe (oy!) */
			(void)printf("probe %s tell %s",
			    AT(pdaddr), AT(psaddr));
			return;
		}
	(void)printf("len %u op %u htype %u ptype %#x halen %u palen %u",
	    length, EXTRACT_16BITS(&ap->op), EXTRACT_16BITS(&ap->htype),
	    EXTRACT_16BITS(&ap->ptype), ap->halen, ap->palen);
}

/*
 * Print AppleTalk Datagram Delivery Protocol packets.
 */
static void
ddp_print(register const u_char *bp, register u_int length, register int t,
	  register u_short snet, register u_char snode, u_char skt)
{

	switch (t) {

	case ddpNBP:
		nbp_print((const struct atNBP *)bp, length, snet, snode, skt);
		break;

	case ddpATP:
		atp_print((const struct atATP *)bp, length);
		break;

	case ddpEIGRP:
		eigrp_print(bp, length);
		break;

	default:
		(void)printf(" at-%s %d", tok2str(type2str, NULL, t), length);
		break;
	}
}

static void
atp_print(register const struct atATP *ap, u_int length)
{
	char c;
	u_int32_t data;

	if ((const u_char *)(ap + 1) > snapend) {
		/* Just bail if we don't have the whole chunk. */
		fputs(tstr, stdout);
		return;
	}
	length -= sizeof(*ap);
	switch (ap->control & 0xc0) {

	case atpReqCode:
		(void)printf(" atp-req%s %d",
			     ap->control & atpXO? " " : "*",
			     EXTRACT_16BITS(&ap->transID));

		atp_bitmap_print(ap->bitmap);

		if (length != 0)
			(void)printf(" [len=%d]", length);

		switch (ap->control & (atpEOM|atpSTS)) {
		case atpEOM:
			(void)printf(" [EOM]");
			break;
		case atpSTS:
			(void)printf(" [STS]");
			break;
		case atpEOM|atpSTS:
			(void)printf(" [EOM,STS]");
			break;
		}
		break;

	case atpRspCode:
		(void)printf(" atp-resp%s%d:%d (%d)",
			     ap->control & atpEOM? "*" : " ",
			     EXTRACT_16BITS(&ap->transID), ap->bitmap, length);
		switch (ap->control & (atpXO|atpSTS)) {
		case atpXO:
			(void)printf(" [XO]");
			break;
		case atpSTS:
			(void)printf(" [STS]");
			break;
		case atpXO|atpSTS:
			(void)printf(" [XO,STS]");
			break;
		}
		break;

	case atpRelCode:
		(void)printf(" atp-rel  %d", EXTRACT_16BITS(&ap->transID));

		atp_bitmap_print(ap->bitmap);

		/* length should be zero */
		if (length)
			(void)printf(" [len=%d]", length);

		/* there shouldn't be any control flags */
		if (ap->control & (atpXO|atpEOM|atpSTS)) {
			c = '[';
			if (ap->control & atpXO) {
				(void)printf("%cXO", c);
				c = ',';
			}
			if (ap->control & atpEOM) {
				(void)printf("%cEOM", c);
				c = ',';
			}
			if (ap->control & atpSTS) {
				(void)printf("%cSTS", c);
				c = ',';
			}
			(void)printf("]");
		}
		break;

	default:
		(void)printf(" atp-0x%x  %d (%d)", ap->control,
			     EXTRACT_16BITS(&ap->transID), length);
		break;
	}
	data = EXTRACT_32BITS(&ap->userData);
	if (data != 0)
		(void)printf(" 0x%x", data);
}

static void
atp_bitmap_print(register u_char bm)
{
	register char c;
	register int i;

	/*
	 * The '& 0xff' below is needed for compilers that want to sign
	 * extend a u_char, which is the case with the Ultrix compiler.
	 * (gcc is smart enough to eliminate it, at least on the Sparc).
	 */
	if ((bm + 1) & (bm & 0xff)) {
		c = '<';
		for (i = 0; bm; ++i) {
			if (bm & 1) {
				(void)printf("%c%d", c, i);
				c = ',';
			}
			bm >>= 1;
		}
		(void)printf(">");
	} else {
		for (i = 0; bm; ++i)
			bm >>= 1;
		if (i > 1)
			(void)printf("<0-%d>", i - 1);
		else
			(void)printf("<0>");
	}
}

static void
nbp_print(register const struct atNBP *np, u_int length, register u_short snet,
	  register u_char snode, register u_char skt)
{
	register const struct atNBPtuple *tp =
		(const struct atNBPtuple *)((u_char *)np + nbpHeaderSize);
	int i;
	const u_char *ep;

	if (length < nbpHeaderSize) {
		(void)printf(" truncated-nbp %d", length);
		return;
	}

	length -= nbpHeaderSize;
	if (length < 8) {
		/* must be room for at least one tuple */
		(void)printf(" truncated-nbp %d", length + nbpHeaderSize);
		return;
	}
	/* ep points to end of available data */
	ep = snapend;
	if ((const u_char *)tp > ep) {
		fputs(tstr, stdout);
		return;
	}
	switch (i = np->control & 0xf0) {

	case nbpBrRq:
	case nbpLkUp:
		(void)printf(i == nbpLkUp? " nbp-lkup %d:":" nbp-brRq %d:",
			     np->id);
		if ((const u_char *)(tp + 1) > ep) {
			fputs(tstr, stdout);
			return;
		}
		(void)nbp_name_print(tp, ep);
		/*
		 * look for anomalies: the spec says there can only
		 * be one tuple, the address must match the source
		 * address and the enumerator should be zero.
		 */
		if ((np->control & 0xf) != 1)
			(void)printf(" [ntup=%d]", np->control & 0xf);
		if (tp->enumerator)
			(void)printf(" [enum=%d]", tp->enumerator);
		if (EXTRACT_16BITS(&tp->net) != snet ||
		    tp->node != snode || tp->skt != skt)
			(void)printf(" [addr=%s.%d]",
			    ataddr_string(EXTRACT_16BITS(&tp->net),
			    tp->node), tp->skt);
		break;

	case nbpLkUpReply:
		(void)printf(" nbp-reply %d:", np->id);

		/* print each of the tuples in the reply */
		for (i = np->control & 0xf; --i >= 0 && tp; )
			tp = nbp_tuple_print(tp, ep, snet, snode, skt);
		break;

	default:
		(void)printf(" nbp-0x%x  %d (%d)", np->control, np->id,
				length);
		break;
	}
}

/* print a counted string */
static const char *
print_cstring(register const char *cp, register const u_char *ep)
{
	register u_int length;

	if (cp >= (const char *)ep) {
		fputs(tstr, stdout);
		return (0);
	}
	length = *cp++;

	/* Spec says string can be at most 32 bytes long */
	if (length > 32) {
		(void)printf("[len=%u]", length);
		return (0);
	}
	while ((int)--length >= 0) {
		if (cp >= (const char *)ep) {
			fputs(tstr, stdout);
			return (0);
		}
		putchar(*cp++);
	}
	return (cp);
}

static const struct atNBPtuple *
nbp_tuple_print(register const struct atNBPtuple *tp,
		register const u_char *ep,
		register u_short snet, register u_char snode,
		register u_char skt)
{
	register const struct atNBPtuple *tpn;

	if ((const u_char *)(tp + 1) > ep) {
		fputs(tstr, stdout);
		return 0;
	}
	tpn = nbp_name_print(tp, ep);

	/* if the enumerator isn't 1, print it */
	if (tp->enumerator != 1)
		(void)printf("(%d)", tp->enumerator);

	/* if the socket doesn't match the src socket, print it */
	if (tp->skt != skt)
		(void)printf(" %d", tp->skt);

	/* if the address doesn't match the src address, it's an anomaly */
	if (EXTRACT_16BITS(&tp->net) != snet || tp->node != snode)
		(void)printf(" [addr=%s]",
		    ataddr_string(EXTRACT_16BITS(&tp->net), tp->node));

	return (tpn);
}

static const struct atNBPtuple *
nbp_name_print(const struct atNBPtuple *tp, register const u_char *ep)
{
	register const char *cp = (const char *)tp + nbpTupleSize;

	putchar(' ');

	/* Object */
	putchar('"');
	if ((cp = print_cstring(cp, ep)) != NULL) {
		/* Type */
		putchar(':');
		if ((cp = print_cstring(cp, ep)) != NULL) {
			/* Zone */
			putchar('@');
			if ((cp = print_cstring(cp, ep)) != NULL)
				putchar('"');
		}
	}
	return ((const struct atNBPtuple *)cp);
}


#define HASHNAMESIZE 4096

struct hnamemem {
	int addr;
	char *name;
	struct hnamemem *nxt;
};

static struct hnamemem hnametable[HASHNAMESIZE];

static const char *
ataddr_string(u_short atnet, u_char athost)
{
	register struct hnamemem *tp, *tp2;
	register int i = (atnet << 8) | athost;
	char nambuf[MAXHOSTNAMELEN + 20];
	static int first = 1;
	FILE *fp;

	/*
	 * if this is the first call, see if there's an AppleTalk
	 * number to name map file.
	 */
	if (first && (first = 0, !nflag)
	    && (fp = fopen("/etc/atalk.names", "r"))) {
		char line[256];
		int i1, i2, i3;

		while (fgets(line, sizeof(line), fp)) {
			if (line[0] == '\n' || line[0] == 0 || line[0] == '#')
				continue;
			if (sscanf(line, "%d.%d.%d %256s", &i1, &i2, &i3,
				     nambuf) == 4)
				/* got a hostname. */
				i3 |= ((i1 << 8) | i2) << 8;
			else if (sscanf(line, "%d.%d %256s", &i1, &i2,
					nambuf) == 3)
				/* got a net name */
				i3 = (((i1 << 8) | i2) << 8) | 255;
			else
				continue;

			for (tp = &hnametable[i3 & (HASHNAMESIZE-1)];
			     tp->nxt; tp = tp->nxt)
				;
			tp->addr = i3;
			tp->nxt = newhnamemem();
			tp->name = strdup(nambuf);
		}
		fclose(fp);
	}

	for (tp = &hnametable[i & (HASHNAMESIZE-1)]; tp->nxt; tp = tp->nxt)
		if (tp->addr == i)
			return (tp->name);

	/* didn't have the node name -- see if we've got the net name */
	i |= 255;
	for (tp2 = &hnametable[i & (HASHNAMESIZE-1)]; tp2->nxt; tp2 = tp2->nxt)
		if (tp2->addr == i) {
			tp->addr = (atnet << 8) | athost;
			tp->nxt = newhnamemem();
			(void)snprintf(nambuf, sizeof(nambuf), "%s.%d",
			    tp2->name, athost);
			tp->name = strdup(nambuf);
			return (tp->name);
		}

	tp->addr = (atnet << 8) | athost;
	tp->nxt = newhnamemem();
	if (athost != 255)
		(void)snprintf(nambuf, sizeof(nambuf), "%d.%d.%d",
		    atnet >> 8, atnet & 0xff, athost);
	else
		(void)snprintf(nambuf, sizeof(nambuf), "%d.%d", atnet >> 8,
		    atnet & 0xff);
	tp->name = strdup(nambuf);

	return (tp->name);
}

static struct tok skt2str[] = {
	{ rtmpSkt,	"rtmp" },	/* routing table maintenance */
	{ nbpSkt,	"nis" },	/* name info socket */
	{ echoSkt,	"echo" },	/* AppleTalk echo protocol */
	{ zipSkt,	"zip" },	/* zone info protocol */
	{ 0,		NULL }
};

static const char *
ddpskt_string(register int skt)
{
	static char buf[8];

	if (nflag) {
		(void)snprintf(buf, sizeof(buf), "%d", skt);
		return (buf);
	}
	return (tok2str(skt2str, "%d", skt));
}
