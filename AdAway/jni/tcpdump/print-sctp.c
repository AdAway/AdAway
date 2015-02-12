/* Copyright (c) 2001 NETLAB, Temple University
 * Copyright (c) 2001 Protocol Engineering Lab, University of Delaware
 *
 * Jerry Heinz <gheinz@astro.temple.edu>
 * John Fiore <jfiore@joda.cis.temple.edu>
 * Armando L. Caro Jr. <acaro@cis.udel.edu>
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in the
 *    documentation and/or other materials provided with the distribution.
 *
 * 3. Neither the name of the University nor of the Laboratory may be used
 *    to endorse or promote products derived from this software without
 *    specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE REGENTS AND CONTRIBUTORS ``AS IS'' AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE REGENTS OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS
 * OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY
 * OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF
 * SUCH DAMAGE.
 */

#ifndef lint
static const char rcsid[] _U_ =
"@(#) $Header: /tcpdump/master/tcpdump/print-sctp.c,v 1.21 2007-09-13 18:03:49 guy Exp $ (NETLAB/PEL)";
#endif

#ifdef HAVE_CONFIG_H
#include "config.h"
#endif

#include <tcpdump-stdinc.h>

#include "sctpHeader.h"
#include "sctpConstants.h"
#include <assert.h>

#include <stdio.h>
#include <string.h>

#include "interface.h"
#include "addrtoname.h"
#include "extract.h"			/* must come after interface.h */
#include "ip.h"
#ifdef INET6
#include "ip6.h"
#endif

#define CHAN_HP 6704
#define CHAN_MP 6705
#define CHAN_LP 6706

static const struct tok ForCES_channels[] = {
	{ CHAN_HP, "ForCES HP" },
	{ CHAN_MP, "ForCES MP" },
	{ CHAN_LP, "ForCES LP" },
	{ 0, NULL }
};

static inline int isForCES_port(u_short Port)
{
	if (Port == CHAN_HP)
		return 1;
	if (Port == CHAN_MP)
		return 1;
	if (Port == CHAN_LP)
		return 1;

	return 0;
}

void sctp_print(const u_char *bp,        /* beginning of sctp packet */
		const u_char *bp2,       /* beginning of enclosing */
		u_int sctpPacketLength)  /* ip packet */
{
  const struct sctpHeader *sctpPktHdr;
  const struct ip *ip;
#ifdef INET6
  const struct ip6_hdr *ip6;
#endif
  const void *endPacketPtr;
  u_short sourcePort, destPort;
  int chunkCount;
  const struct sctpChunkDesc *chunkDescPtr;
  const void *nextChunk;
  const char *sep;
  int isforces = 0;


  sctpPktHdr = (const struct sctpHeader*) bp;
  endPacketPtr = (const u_char*)sctpPktHdr+sctpPacketLength;

  if( (u_long) endPacketPtr > (u_long) snapend)
    endPacketPtr = (const void *) snapend;
  ip = (struct ip *)bp2;
#ifdef INET6
  if (IP_V(ip) == 6)
    ip6 = (const struct ip6_hdr *)bp2;
  else
    ip6 = NULL;
#endif /*INET6*/
  TCHECK(*sctpPktHdr);

  if (sctpPacketLength < sizeof(struct sctpHeader))
    {
      (void)printf("truncated-sctp - %ld bytes missing!",
		   (long)sctpPacketLength-sizeof(struct sctpHeader));
      return;
    }

  /*    sctpPacketLength -= sizeof(struct sctpHeader);  packet length  */
  /*  			      is now only as long as the payload  */

  sourcePort = EXTRACT_16BITS(&sctpPktHdr->source);
  destPort = EXTRACT_16BITS(&sctpPktHdr->destination);

#ifdef INET6
  if (ip6) {
    (void)printf("%s.%d > %s.%d: sctp",
      ip6addr_string(&ip6->ip6_src),
      sourcePort,
      ip6addr_string(&ip6->ip6_dst),
      destPort);
  } else
#endif /*INET6*/
  {
    (void)printf("%s.%d > %s.%d: sctp",
      ipaddr_string(&ip->ip_src),
      sourcePort,
      ipaddr_string(&ip->ip_dst),
      destPort);
  }
  fflush(stdout);

  if (isForCES_port(sourcePort)) {
         printf("[%s]", tok2str(ForCES_channels, NULL, sourcePort));
         isforces = 1;
  }
  if (isForCES_port(destPort)) {
         printf("[%s]", tok2str(ForCES_channels, NULL, destPort));
         isforces = 1;
  }

  if (vflag >= 2)
    sep = "\n\t";
  else
    sep = " (";
  /* cycle through all chunks, printing information on each one */
  for (chunkCount = 0,
	 chunkDescPtr = (const struct sctpChunkDesc *)
	    ((const u_char*) sctpPktHdr + sizeof(struct sctpHeader));
       chunkDescPtr != NULL &&
	 ( (const void *)
	    ((const u_char *) chunkDescPtr + sizeof(struct sctpChunkDesc))
	   <= endPacketPtr);

       chunkDescPtr = (const struct sctpChunkDesc *) nextChunk, chunkCount++)
    {
      u_int16_t chunkLength;
      const u_char *chunkEnd;
      u_int16_t align;

      TCHECK(*chunkDescPtr);
      chunkLength = EXTRACT_16BITS(&chunkDescPtr->chunkLength);
      if (chunkLength < sizeof(*chunkDescPtr)) {
      	printf("%s%d) [Bad chunk length %u]", sep, chunkCount+1, chunkLength);
      	break;
      }

      TCHECK2(*((u_int8_t *)chunkDescPtr), chunkLength);
      chunkEnd = ((const u_char*)chunkDescPtr + chunkLength);

      align=chunkLength % 4;
      if (align != 0)
	align = 4 - align;

      nextChunk = (const void *) (chunkEnd + align);

      printf("%s%d) ", sep, chunkCount+1);
      switch (chunkDescPtr->chunkID)
	{
	case SCTP_DATA :
	  {
	    const struct sctpDataPart *dataHdrPtr;

	    printf("[DATA] ");

	    if ((chunkDescPtr->chunkFlg & SCTP_DATA_UNORDERED)
		== SCTP_DATA_UNORDERED)
	      printf("(U)");

	    if ((chunkDescPtr->chunkFlg & SCTP_DATA_FIRST_FRAG)
		== SCTP_DATA_FIRST_FRAG)
	      printf("(B)");

	    if ((chunkDescPtr->chunkFlg & SCTP_DATA_LAST_FRAG)
		== SCTP_DATA_LAST_FRAG)
	      printf("(E)");

	    if( ((chunkDescPtr->chunkFlg & SCTP_DATA_UNORDERED)
		 == SCTP_DATA_UNORDERED)
		||
		((chunkDescPtr->chunkFlg & SCTP_DATA_FIRST_FRAG)
		 == SCTP_DATA_FIRST_FRAG)
		||
		((chunkDescPtr->chunkFlg & SCTP_DATA_LAST_FRAG)
		 == SCTP_DATA_LAST_FRAG) )
	      printf(" ");

	    dataHdrPtr=(const struct sctpDataPart*)(chunkDescPtr+1);

	    printf("[TSN: %u] ", EXTRACT_32BITS(&dataHdrPtr->TSN));
	    printf("[SID: %u] ", EXTRACT_16BITS(&dataHdrPtr->streamId));
	    printf("[SSEQ %u] ", EXTRACT_16BITS(&dataHdrPtr->sequence));
	    printf("[PPID 0x%x] ", EXTRACT_32BITS(&dataHdrPtr->payloadtype));
	    fflush(stdout);
	    if (isforces) {
		const u_char *payloadPtr;
		u_int chunksize = sizeof(struct sctpDataPart)+
			          sizeof(struct sctpChunkDesc);
		payloadPtr = (const u_char *) (dataHdrPtr + 1);
		if (EXTRACT_16BITS(&chunkDescPtr->chunkLength) <
			sizeof(struct sctpDataPart)+
			sizeof(struct sctpChunkDesc)+1) {
		/* Less than 1 byte of chunk payload */
			printf("bogus ForCES chunk length %u]",
			    EXTRACT_16BITS(&chunkDescPtr->chunkLength));
			return;
		}

		forces_print(payloadPtr, EXTRACT_16BITS(&chunkDescPtr->chunkLength)- chunksize);
	   } else if (vflag >= 2) {	/* if verbose output is specified */
					/* at the command line */
		const u_char *payloadPtr;

		printf("[Payload");

		if (!suppress_default_print) {
			payloadPtr = (const u_char *) (++dataHdrPtr);
			printf(":");
			if (EXTRACT_16BITS(&chunkDescPtr->chunkLength) <
			    sizeof(struct sctpDataPart)+
			    sizeof(struct sctpChunkDesc)+1) {
				/* Less than 1 byte of chunk payload */
				printf("bogus chunk length %u]",
				    EXTRACT_16BITS(&chunkDescPtr->chunkLength));
				return;
			}
			default_print(payloadPtr,
			      EXTRACT_16BITS(&chunkDescPtr->chunkLength) -
			      (sizeof(struct sctpDataPart)+
			      sizeof(struct sctpChunkDesc)));
		} else
			printf("]");
	      }
	    break;
	  }
	case SCTP_INITIATION :
	  {
	    const struct sctpInitiation *init;

	    printf("[INIT] ");
	    init=(const struct sctpInitiation*)(chunkDescPtr+1);
	    printf("[init tag: %u] ", EXTRACT_32BITS(&init->initTag));
	    printf("[rwnd: %u] ", EXTRACT_32BITS(&init->rcvWindowCredit));
	    printf("[OS: %u] ", EXTRACT_16BITS(&init->NumPreopenStreams));
	    printf("[MIS: %u] ", EXTRACT_16BITS(&init->MaxInboundStreams));
	    printf("[init TSN: %u] ", EXTRACT_32BITS(&init->initialTSN));

#if(0) /* ALC you can add code for optional params here */
	    if( (init+1) < chunkEnd )
	      printf(" @@@@@ UNFINISHED @@@@@@%s\n",
		     "Optional params present, but not printed.");
#endif
	    break;
	  }
	case SCTP_INITIATION_ACK :
	  {
	    const struct sctpInitiation *init;

	    printf("[INIT ACK] ");
	    init=(const struct sctpInitiation*)(chunkDescPtr+1);
	    printf("[init tag: %u] ", EXTRACT_32BITS(&init->initTag));
	    printf("[rwnd: %u] ", EXTRACT_32BITS(&init->rcvWindowCredit));
	    printf("[OS: %u] ", EXTRACT_16BITS(&init->NumPreopenStreams));
	    printf("[MIS: %u] ", EXTRACT_16BITS(&init->MaxInboundStreams));
	    printf("[init TSN: %u] ", EXTRACT_32BITS(&init->initialTSN));

#if(0) /* ALC you can add code for optional params here */
	    if( (init+1) < chunkEnd )
	      printf(" @@@@@ UNFINISHED @@@@@@%s\n",
		     "Optional params present, but not printed.");
#endif
	    break;
	  }
	case SCTP_SELECTIVE_ACK:
	  {
	    const struct sctpSelectiveAck *sack;
	    const struct sctpSelectiveFrag *frag;
	    int fragNo, tsnNo;
	    const u_char *dupTSN;

	    printf("[SACK] ");
	    sack=(const struct sctpSelectiveAck*)(chunkDescPtr+1);
	    printf("[cum ack %u] ", EXTRACT_32BITS(&sack->highestConseqTSN));
	    printf("[a_rwnd %u] ", EXTRACT_32BITS(&sack->updatedRwnd));
	    printf("[#gap acks %u] ", EXTRACT_16BITS(&sack->numberOfdesc));
	    printf("[#dup tsns %u] ", EXTRACT_16BITS(&sack->numDupTsns));


	    /* print gaps */
	    for (frag = ( (const struct sctpSelectiveFrag *)
			  ((const struct sctpSelectiveAck *) sack+1)),
		   fragNo=0;
		 (const void *)frag < nextChunk && fragNo < EXTRACT_16BITS(&sack->numberOfdesc);
		 frag++, fragNo++)
	      printf("\n\t\t[gap ack block #%d: start = %u, end = %u] ",
		     fragNo+1,
		     EXTRACT_32BITS(&sack->highestConseqTSN) + EXTRACT_16BITS(&frag->fragmentStart),
		     EXTRACT_32BITS(&sack->highestConseqTSN) + EXTRACT_16BITS(&frag->fragmentEnd));


	    /* print duplicate TSNs */
	    for (dupTSN = (const u_char *)frag, tsnNo=0;
		 (const void *) dupTSN < nextChunk && tsnNo<EXTRACT_16BITS(&sack->numDupTsns);
		 dupTSN += 4, tsnNo++)
	      printf("\n\t\t[dup TSN #%u: %u] ", tsnNo+1,
	          EXTRACT_32BITS(dupTSN));

	    break;
	  }
	case SCTP_HEARTBEAT_REQUEST :
	  printf("[HB REQ] ");
	  break;
	case SCTP_HEARTBEAT_ACK :
	  printf("[HB ACK] ");
	  break;
	case SCTP_ABORT_ASSOCIATION :
	  printf("[ABORT] ");
	  break;
	case SCTP_SHUTDOWN :
	  printf("[SHUTDOWN] ");
	  break;
	case SCTP_SHUTDOWN_ACK :
	  printf("[SHUTDOWN ACK] ");
	  break;
	case SCTP_OPERATION_ERR :
	  printf("[OP ERR] ");
	  break;
	case SCTP_COOKIE_ECHO :
	  printf("[COOKIE ECHO] ");
	  break;
	case SCTP_COOKIE_ACK :
	  printf("[COOKIE ACK] ");
	  break;
	case SCTP_ECN_ECHO :
	  printf("[ECN ECHO] ");
	  break;
	case SCTP_ECN_CWR :
	  printf("[ECN CWR] ");
	  break;
	case SCTP_SHUTDOWN_COMPLETE :
	  printf("[SHUTDOWN COMPLETE] ");
	  break;
	case SCTP_FORWARD_CUM_TSN :
	  printf("[FOR CUM TSN] ");
	  break;
	case SCTP_RELIABLE_CNTL :
	  printf("[REL CTRL] ");
	  break;
	case SCTP_RELIABLE_CNTL_ACK :
	  printf("[REL CTRL ACK] ");
	  break;
	default :
	  printf("[Unknown chunk type: 0x%x]", chunkDescPtr->chunkID);
	  return;
	}

	if (vflag < 2)
	  sep = ", (";
    }
    return;

trunc:
    printf("[|sctp]");
    return;
}
