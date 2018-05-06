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

#define NETDISSECT_REWORKED
#ifdef HAVE_CONFIG_H
#include "config.h"
#endif

#include <tcpdump-stdinc.h>

#include "interface.h"
#include "addrtoname.h"
#include "extract.h"			/* must come after interface.h */
#include "ip.h"
#ifdef INET6
#include "ip6.h"
#endif

/* Definitions from:
 *
 * SCTP reference Implementation Copyright (C) 1999 Cisco And Motorola
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
 * 3. Neither the name of Cisco nor of Motorola may be used
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
 *
 * This file is part of the SCTP reference Implementation
 *
 *
 * Please send any bug reports or fixes you make to one of the following email
 * addresses:
 *
 * rstewar1@email.mot.com
 * kmorneau@cisco.com
 * qxie1@email.mot.com
 *
 * Any bugs reported given to us we will try to fix... any fixes shared will
 * be incorperated into the next SCTP release.
 */

/* The valid defines for all message
 * types know to SCTP. 0 is reserved
 */
#define SCTP_DATA		0x00
#define SCTP_INITIATION		0x01
#define SCTP_INITIATION_ACK	0x02
#define SCTP_SELECTIVE_ACK	0x03
#define SCTP_HEARTBEAT_REQUEST	0x04
#define SCTP_HEARTBEAT_ACK	0x05
#define SCTP_ABORT_ASSOCIATION	0x06
#define SCTP_SHUTDOWN		0x07
#define SCTP_SHUTDOWN_ACK	0x08
#define SCTP_OPERATION_ERR	0x09
#define SCTP_COOKIE_ECHO	0x0a
#define SCTP_COOKIE_ACK         0x0b
#define SCTP_ECN_ECHO		0x0c
#define SCTP_ECN_CWR		0x0d
#define SCTP_SHUTDOWN_COMPLETE	0x0e
#define SCTP_FORWARD_CUM_TSN    0xc0
#define SCTP_RELIABLE_CNTL      0xc1
#define SCTP_RELIABLE_CNTL_ACK  0xc2

static const struct tok sctp_chunkid_str[] = {
	{ SCTP_DATA,              "DATA"              },
	{ SCTP_INITIATION,        "INIT"              },
	{ SCTP_INITIATION_ACK,    "INIT ACK"          },
	{ SCTP_SELECTIVE_ACK,     "SACK"              },
	{ SCTP_HEARTBEAT_REQUEST, "HB REQ"            },
	{ SCTP_HEARTBEAT_ACK,     "HB ACK"            },
	{ SCTP_ABORT_ASSOCIATION, "ABORT"             },
	{ SCTP_SHUTDOWN,          "SHUTDOWN"          },
	{ SCTP_SHUTDOWN_ACK,      "SHUTDOWN ACK"      },
	{ SCTP_OPERATION_ERR,     "OP ERR"            },
	{ SCTP_COOKIE_ECHO,       "COOKIE ECHO"       },
	{ SCTP_COOKIE_ACK,        "COOKIE ACK"        },
	{ SCTP_ECN_ECHO,          "ECN ECHO"          },
	{ SCTP_ECN_CWR,           "ECN CWR"           },
	{ SCTP_SHUTDOWN_COMPLETE, "SHUTDOWN COMPLETE" },
	{ SCTP_FORWARD_CUM_TSN,   "FOR CUM TSN"       },
	{ SCTP_RELIABLE_CNTL,     "REL CTRL"          },
	{ SCTP_RELIABLE_CNTL_ACK, "REL CTRL ACK"      },
	{ 0, NULL }
};

/* Data Chuck Specific Flags */
#define SCTP_DATA_FRAG_MASK	0x03
#define SCTP_DATA_MIDDLE_FRAG	0x00
#define SCTP_DATA_LAST_FRAG	0x01
#define SCTP_DATA_FIRST_FRAG	0x02
#define SCTP_DATA_NOT_FRAG	0x03
#define SCTP_DATA_UNORDERED	0x04

#define SCTP_ADDRMAX 60

#define CHAN_HP 6704
#define CHAN_MP 6705
#define CHAN_LP 6706

/* the sctp common header */

struct sctpHeader{
  uint16_t source;
  uint16_t destination;
  uint32_t verificationTag;
  uint32_t adler32;
};

/* various descriptor parsers */

struct sctpChunkDesc{
  uint8_t chunkID;
  uint8_t chunkFlg;
  uint16_t chunkLength;
};

struct sctpParamDesc{
  uint16_t paramType;
  uint16_t paramLength;
};


struct sctpRelChunkDesc{
  struct sctpChunkDesc chk;
  uint32_t serialNumber;
};

struct sctpVendorSpecificParam {
  struct sctpParamDesc p;  /* type must be 0xfffe */
  uint32_t vendorId;	   /* vendor ID from RFC 1700 */
  uint16_t vendorSpecificType;
  uint16_t vendorSpecificLen;
};


/* Structures for the control parts */



/* Sctp association init request/ack */

/* this is used for init ack, too */
struct sctpInitiation{
  uint32_t initTag;		/* tag of mine */
  uint32_t rcvWindowCredit;	/* rwnd */
  uint16_t NumPreopenStreams;	/* OS */
  uint16_t MaxInboundStreams;     /* MIS */
  uint32_t initialTSN;
  /* optional param's follow in sctpParamDesc form */
};

struct sctpV4IpAddress{
  struct sctpParamDesc p;	/* type is set to SCTP_IPV4_PARAM_TYPE, len=10 */
  uint32_t  ipAddress;
};


struct sctpV6IpAddress{
  struct sctpParamDesc p;	/* type is set to SCTP_IPV6_PARAM_TYPE, len=22 */
  uint8_t  ipAddress[16];
};

struct sctpDNSName{
  struct sctpParamDesc param;
  uint8_t name[1];
};


struct sctpCookiePreserve{
  struct sctpParamDesc p;	/* type is set to SCTP_COOKIE_PRESERVE, len=8 */
  uint32_t extraTime;
};


struct sctpTimeStamp{
  uint32_t ts_sec;
  uint32_t ts_usec;
};

/* wire structure of my cookie */
struct cookieMessage{
  uint32_t TieTag_curTag;		/* copied from assoc if present */
  uint32_t TieTag_hisTag; 		/* copied from assoc if present */
  int32_t cookieLife;			/* life I will award this cookie */
  struct sctpTimeStamp timeEnteringState; /* the time I built cookie */
  struct sctpInitiation initAckISent;	/* the INIT-ACK that I sent to my peer */
  uint32_t addressWhereISent[4];	/* I make this 4 ints so I get 128bits for future */
  int32_t addrtype;			/* address type */
  uint16_t locScope;			/* V6 local scope flag */
  uint16_t siteScope;			/* V6 site scope flag */
  /* at the end is tacked on the INIT chunk sent in
   * its entirety and of course our
   * signature.
   */
};


/* this guy is for use when
 * I have a initiate message gloming the
 * things together.

 */
struct sctpUnifiedInit{
  struct sctpChunkDesc uh;
  struct sctpInitiation initm;
};

struct sctpSendableInit{
  struct sctpHeader mh;
  struct sctpUnifiedInit msg;
};


/* Selective Acknowledgement
 * has the following structure with
 * a optional ammount of trailing int's
 * on the last part (based on the numberOfDesc
 * field).
 */

struct sctpSelectiveAck{
  uint32_t highestConseqTSN;
  uint32_t updatedRwnd;
  uint16_t numberOfdesc;
  uint16_t numDupTsns;
};

struct sctpSelectiveFrag{
  uint16_t fragmentStart;
  uint16_t fragmentEnd;
};


struct sctpUnifiedSack{
  struct sctpChunkDesc uh;
  struct sctpSelectiveAck sack;
};

/* for both RTT request/response the
 * following is sent
 */

struct sctpHBrequest {
  uint32_t time_value_1;
  uint32_t time_value_2;
};

/* here is what I read and respond with to. */
struct sctpHBunified{
  struct sctpChunkDesc hdr;
  struct sctpParamDesc hb;
};


/* here is what I send */
struct sctpHBsender{
  struct sctpChunkDesc hdr;
  struct sctpParamDesc hb;
  struct sctpHBrequest rtt;
  int8_t addrFmt[SCTP_ADDRMAX];
  uint16_t userreq;
};



/* for the abort and shutdown ACK
 * we must carry the init tag in the common header. Just the
 * common header is all that is needed with a chunk descriptor.
 */
struct sctpUnifiedAbort{
  struct sctpChunkDesc uh;
};

struct sctpUnifiedAbortLight{
  struct sctpHeader mh;
  struct sctpChunkDesc uh;
};

struct sctpUnifiedAbortHeavy{
  struct sctpHeader mh;
  struct sctpChunkDesc uh;
  uint16_t causeCode;
  uint16_t causeLen;
};

/* For the graceful shutdown we must carry
 * the tag (in common header)  and the highest consequitive acking value
 */
struct sctpShutdown {
  uint32_t TSN_Seen;
};

struct sctpUnifiedShutdown{
  struct sctpChunkDesc uh;
  struct sctpShutdown shut;
};

/* in the unified message we add the trailing
 * stream id since it is the only message
 * that is defined as a operation error.
 */
struct sctpOpErrorCause{
  uint16_t cause;
  uint16_t causeLen;
};

struct sctpUnifiedOpError{
  struct sctpChunkDesc uh;
  struct sctpOpErrorCause c;
};

struct sctpUnifiedStreamError{
  struct sctpHeader mh;
  struct sctpChunkDesc uh;
  struct sctpOpErrorCause c;
  uint16_t strmNum;
  uint16_t reserved;
};

struct staleCookieMsg{
  struct sctpHeader mh;
  struct sctpChunkDesc uh;
  struct sctpOpErrorCause c;
  uint32_t moretime;
};

/* the following is used in all sends
 * where nothing is needed except the
 * chunk/type i.e. shutdownAck Abort */

struct sctpUnifiedSingleMsg{
  struct sctpHeader mh;
  struct sctpChunkDesc uh;
};

struct sctpDataPart{
  uint32_t TSN;
  uint16_t streamId;
  uint16_t sequence;
  uint32_t payloadtype;
};

struct sctpUnifiedDatagram{
  struct sctpChunkDesc uh;
  struct sctpDataPart dp;
};

struct sctpECN_echo{
  struct sctpChunkDesc uh;
  uint32_t Lowest_TSN;
};


struct sctpCWR{
  struct sctpChunkDesc uh;
  uint32_t TSN_reduced_at;
};

static const struct tok ForCES_channels[] = {
	{ CHAN_HP, "ForCES HP" },
	{ CHAN_MP, "ForCES MP" },
	{ CHAN_LP, "ForCES LP" },
	{ 0, NULL }
};

/* data chunk's payload protocol identifiers */

#define SCTP_PPID_IUA 1
#define SCTP_PPID_M2UA 2
#define SCTP_PPID_M3UA 3
#define SCTP_PPID_SUA 4
#define SCTP_PPID_M2PA 5
#define SCTP_PPID_V5UA 6
#define SCTP_PPID_H248 7
#define SCTP_PPID_BICC 8
#define SCTP_PPID_TALI 9
#define SCTP_PPID_DUA 10
#define SCTP_PPID_ASAP 11
#define SCTP_PPID_ENRP 12
#define SCTP_PPID_H323 13
#define SCTP_PPID_QIPC 14
#define SCTP_PPID_SIMCO 15
#define SCTP_PPID_DDPSC 16
#define SCTP_PPID_DDPSSC 17
#define SCTP_PPID_S1AP 18
#define SCTP_PPID_RUA 19
#define SCTP_PPID_HNBAP 20
#define SCTP_PPID_FORCES_HP 21
#define SCTP_PPID_FORCES_MP 22
#define SCTP_PPID_FORCES_LP 23
#define SCTP_PPID_SBC_AP 24
#define SCTP_PPID_NBAP 25
/* 26 */
#define SCTP_PPID_X2AP 27

static const struct tok PayloadProto_idents[] = {
	{ SCTP_PPID_IUA,    "ISDN Q.921" },
	{ SCTP_PPID_M2UA,   "M2UA"   },
	{ SCTP_PPID_M3UA,   "M3UA"   },
	{ SCTP_PPID_SUA,    "SUA"    },
	{ SCTP_PPID_M2PA,   "M2PA"   },
	{ SCTP_PPID_V5UA,   "V5.2"   },
	{ SCTP_PPID_H248,   "H.248"  },
	{ SCTP_PPID_BICC,   "BICC"   },
	{ SCTP_PPID_TALI,   "TALI"   },
	{ SCTP_PPID_DUA,    "DUA"    },
	{ SCTP_PPID_ASAP,   "ASAP"   },
	{ SCTP_PPID_ENRP,   "ENRP"   },
	{ SCTP_PPID_H323,   "H.323"  },
	{ SCTP_PPID_QIPC,   "Q.IPC"  },
	{ SCTP_PPID_SIMCO,  "SIMCO"  },
	{ SCTP_PPID_DDPSC,  "DDPSC"  },
	{ SCTP_PPID_DDPSSC, "DDPSSC" },
	{ SCTP_PPID_S1AP,   "S1AP"   },
	{ SCTP_PPID_RUA,    "RUA"    },
	{ SCTP_PPID_HNBAP,  "HNBAP"  },
	{ SCTP_PPID_FORCES_HP, "ForCES HP" },
	{ SCTP_PPID_FORCES_MP, "ForCES MP" },
	{ SCTP_PPID_FORCES_LP, "ForCES LP" },
	{ SCTP_PPID_SBC_AP, "SBc-AP" },
	{ SCTP_PPID_NBAP,   "NBAP"   },
	/* 26 */
	{ SCTP_PPID_X2AP,   "X2AP"   },
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

void sctp_print(netdissect_options *ndo,
                const u_char *bp,        /* beginning of sctp packet */
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

  if( (u_long) endPacketPtr > (u_long) ndo->ndo_snapend)
    endPacketPtr = (const void *) ndo->ndo_snapend;
  ip = (struct ip *)bp2;
#ifdef INET6
  if (IP_V(ip) == 6)
    ip6 = (const struct ip6_hdr *)bp2;
  else
    ip6 = NULL;
#endif /*INET6*/
  ND_TCHECK(*sctpPktHdr);

  if (sctpPacketLength < sizeof(struct sctpHeader))
    {
      ND_PRINT((ndo, "truncated-sctp - %ld bytes missing!",
		   (long)sctpPacketLength-sizeof(struct sctpHeader)));
      return;
    }

  /*    sctpPacketLength -= sizeof(struct sctpHeader);  packet length  */
  /*  			      is now only as long as the payload  */

  sourcePort = EXTRACT_16BITS(&sctpPktHdr->source);
  destPort = EXTRACT_16BITS(&sctpPktHdr->destination);

#ifdef INET6
  if (ip6) {
    ND_PRINT((ndo, "%s.%d > %s.%d: sctp",
      ip6addr_string(ndo, &ip6->ip6_src),
      sourcePort,
      ip6addr_string(ndo, &ip6->ip6_dst),
      destPort));
  } else
#endif /*INET6*/
  {
    ND_PRINT((ndo, "%s.%d > %s.%d: sctp",
      ipaddr_string(ndo, &ip->ip_src),
      sourcePort,
      ipaddr_string(ndo, &ip->ip_dst),
      destPort));
  }

  if (isForCES_port(sourcePort)) {
         ND_PRINT((ndo, "[%s]", tok2str(ForCES_channels, NULL, sourcePort)));
         isforces = 1;
  }
  if (isForCES_port(destPort)) {
         ND_PRINT((ndo, "[%s]", tok2str(ForCES_channels, NULL, destPort)));
         isforces = 1;
  }

  if (ndo->ndo_vflag >= 2)
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
      uint16_t chunkLength;
      const u_char *chunkEnd;
      uint16_t align;

      ND_TCHECK(*chunkDescPtr);
      chunkLength = EXTRACT_16BITS(&chunkDescPtr->chunkLength);
      if (chunkLength < sizeof(*chunkDescPtr)) {
        ND_PRINT((ndo, "%s%d) [Bad chunk length %u]", sep, chunkCount+1, chunkLength));
        break;
      }

      ND_TCHECK2(*((uint8_t *)chunkDescPtr), chunkLength);
      chunkEnd = ((const u_char*)chunkDescPtr + chunkLength);

      align=chunkLength % 4;
      if (align != 0)
	align = 4 - align;

      nextChunk = (const void *) (chunkEnd + align);

      ND_PRINT((ndo, "%s%d) ", sep, chunkCount+1));
      ND_PRINT((ndo, "[%s] ", tok2str(sctp_chunkid_str, "Unknown chunk type: 0x%x",
                                      chunkDescPtr->chunkID)));
      switch (chunkDescPtr->chunkID)
	{
	case SCTP_DATA :
	  {
	    const struct sctpDataPart *dataHdrPtr;
	    uint32_t ppid;
	    const u_char *payloadPtr;
	    u_int payload_size;

	    if ((chunkDescPtr->chunkFlg & SCTP_DATA_UNORDERED)
		== SCTP_DATA_UNORDERED)
	      ND_PRINT((ndo, "(U)"));

	    if ((chunkDescPtr->chunkFlg & SCTP_DATA_FIRST_FRAG)
		== SCTP_DATA_FIRST_FRAG)
	      ND_PRINT((ndo, "(B)"));

	    if ((chunkDescPtr->chunkFlg & SCTP_DATA_LAST_FRAG)
		== SCTP_DATA_LAST_FRAG)
	      ND_PRINT((ndo, "(E)"));

	    if( ((chunkDescPtr->chunkFlg & SCTP_DATA_UNORDERED)
		 == SCTP_DATA_UNORDERED)
		||
		((chunkDescPtr->chunkFlg & SCTP_DATA_FIRST_FRAG)
		 == SCTP_DATA_FIRST_FRAG)
		||
		((chunkDescPtr->chunkFlg & SCTP_DATA_LAST_FRAG)
		 == SCTP_DATA_LAST_FRAG) )
	      ND_PRINT((ndo, " "));

	    dataHdrPtr=(const struct sctpDataPart*)(chunkDescPtr+1);

	    ppid = EXTRACT_32BITS(&dataHdrPtr->payloadtype);
	    ND_PRINT((ndo, "[TSN: %u] ", EXTRACT_32BITS(&dataHdrPtr->TSN)));
	    ND_PRINT((ndo, "[SID: %u] ", EXTRACT_16BITS(&dataHdrPtr->streamId)));
	    ND_PRINT((ndo, "[SSEQ %u] ", EXTRACT_16BITS(&dataHdrPtr->sequence)));
	    ND_PRINT((ndo, "[PPID %s] ",
		    tok2str(PayloadProto_idents, "0x%x", ppid)));

	    if (!isforces) {
		isforces = (ppid == SCTP_PPID_FORCES_HP) ||
		    (ppid == SCTP_PPID_FORCES_MP) ||
		    (ppid == SCTP_PPID_FORCES_LP);
	    }

	    payloadPtr = (const u_char *) (dataHdrPtr + 1);
	    if (EXTRACT_16BITS(&chunkDescPtr->chunkLength) <
		    sizeof(struct sctpDataPart) + sizeof(struct sctpChunkDesc) + 1) {
		ND_PRINT((ndo, "bogus chunk length %u]", EXTRACT_16BITS(&chunkDescPtr->chunkLength)));
		return;
	    }

	    payload_size = EXTRACT_16BITS(&chunkDescPtr->chunkLength) -
		(sizeof(struct sctpDataPart) + sizeof(struct sctpChunkDesc));

	    if (isforces) {
		forces_print(ndo, payloadPtr, payload_size);
	    } else if (ndo->ndo_vflag >= 2) {	/* if verbose output is specified */
					/* at the command line */
		switch (ppid) {
		case SCTP_PPID_M3UA :
			m3ua_print(ndo, payloadPtr, payload_size);
			break;
		default:
			ND_PRINT((ndo, "[Payload"));
			if (!ndo->ndo_suppress_default_print) {
				ND_PRINT((ndo, ":"));
				ND_DEFAULTPRINT(payloadPtr, payload_size);
			}
			ND_PRINT((ndo, "]"));
			break;
		}
	    }
	    break;
	  }
	case SCTP_INITIATION :
	  {
	    const struct sctpInitiation *init;

	    init=(const struct sctpInitiation*)(chunkDescPtr+1);
	    ND_PRINT((ndo, "[init tag: %u] ", EXTRACT_32BITS(&init->initTag)));
	    ND_PRINT((ndo, "[rwnd: %u] ", EXTRACT_32BITS(&init->rcvWindowCredit)));
	    ND_PRINT((ndo, "[OS: %u] ", EXTRACT_16BITS(&init->NumPreopenStreams)));
	    ND_PRINT((ndo, "[MIS: %u] ", EXTRACT_16BITS(&init->MaxInboundStreams)));
	    ND_PRINT((ndo, "[init TSN: %u] ", EXTRACT_32BITS(&init->initialTSN)));

#if(0) /* ALC you can add code for optional params here */
	    if( (init+1) < chunkEnd )
	      ND_PRINT((ndo, " @@@@@ UNFINISHED @@@@@@%s\n",
		     "Optional params present, but not printed."));
#endif
	    break;
	  }
	case SCTP_INITIATION_ACK :
	  {
	    const struct sctpInitiation *init;

	    init=(const struct sctpInitiation*)(chunkDescPtr+1);
	    ND_PRINT((ndo, "[init tag: %u] ", EXTRACT_32BITS(&init->initTag)));
	    ND_PRINT((ndo, "[rwnd: %u] ", EXTRACT_32BITS(&init->rcvWindowCredit)));
	    ND_PRINT((ndo, "[OS: %u] ", EXTRACT_16BITS(&init->NumPreopenStreams)));
	    ND_PRINT((ndo, "[MIS: %u] ", EXTRACT_16BITS(&init->MaxInboundStreams)));
	    ND_PRINT((ndo, "[init TSN: %u] ", EXTRACT_32BITS(&init->initialTSN)));

#if(0) /* ALC you can add code for optional params here */
	    if( (init+1) < chunkEnd )
	      ND_PRINT((ndo, " @@@@@ UNFINISHED @@@@@@%s\n",
		     "Optional params present, but not printed."));
#endif
	    break;
	  }
	case SCTP_SELECTIVE_ACK:
	  {
	    const struct sctpSelectiveAck *sack;
	    const struct sctpSelectiveFrag *frag;
	    int fragNo, tsnNo;
	    const u_char *dupTSN;

	    sack=(const struct sctpSelectiveAck*)(chunkDescPtr+1);
	    ND_PRINT((ndo, "[cum ack %u] ", EXTRACT_32BITS(&sack->highestConseqTSN)));
	    ND_PRINT((ndo, "[a_rwnd %u] ", EXTRACT_32BITS(&sack->updatedRwnd)));
	    ND_PRINT((ndo, "[#gap acks %u] ", EXTRACT_16BITS(&sack->numberOfdesc)));
	    ND_PRINT((ndo, "[#dup tsns %u] ", EXTRACT_16BITS(&sack->numDupTsns)));


	    /* print gaps */
	    for (frag = ( (const struct sctpSelectiveFrag *)
			  ((const struct sctpSelectiveAck *) sack+1)),
		   fragNo=0;
		 (const void *)frag < nextChunk && fragNo < EXTRACT_16BITS(&sack->numberOfdesc);
		 frag++, fragNo++)
	      ND_PRINT((ndo, "\n\t\t[gap ack block #%d: start = %u, end = %u] ",
		     fragNo+1,
		     EXTRACT_32BITS(&sack->highestConseqTSN) + EXTRACT_16BITS(&frag->fragmentStart),
		     EXTRACT_32BITS(&sack->highestConseqTSN) + EXTRACT_16BITS(&frag->fragmentEnd)));


	    /* print duplicate TSNs */
	    for (dupTSN = (const u_char *)frag, tsnNo=0;
		 (const void *) dupTSN < nextChunk && tsnNo<EXTRACT_16BITS(&sack->numDupTsns);
		 dupTSN += 4, tsnNo++)
	      ND_PRINT((ndo, "\n\t\t[dup TSN #%u: %u] ", tsnNo+1,
	          EXTRACT_32BITS(dupTSN)));

	    break;
	  }
	}

	if (ndo->ndo_vflag < 2)
	  sep = ", (";
    }
    return;

trunc:
    ND_PRINT((ndo, "[|sctp]"));
}
