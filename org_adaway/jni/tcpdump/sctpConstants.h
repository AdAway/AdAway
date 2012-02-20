/* @(#) $Header: /tcpdump/master/tcpdump/sctpConstants.h,v 1.4 2003/06/03 23:49:23 guy Exp $ (LBL) */

/* SCTP reference Implementation Copyright (C) 1999 Cisco And Motorola
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


#ifndef __sctpConstants_h__
#define __sctpConstants_h__


  /* If you wish to use MD5 instead of SLA uncomment the line
   * below. Why you would like to do this:
   * a) There may be IPR on SHA-1, or so the FIP-180-1 page says,
   * b) MD5 is 3 times faster (has coded here).
   *
   * The disadvantage is, it is thought that MD5 has been
   * cracked... see RFC2104.
   */
/*#define USE_MD5 1*/

/* the SCTP protocol signature
 * this includes the version number
 * encoded in the last 4 bits of the
 * signature.
 */
#define PROTO_SIGNATURE_A 0x30000000

#define SCTP_VERSION_NUMBER 0x3

#define MAX_TSN 0xffffffff
#define MAX_SEQ 0xffff

/* option:
 * If you comment out the following you will
 * receive the old behavior of obeying cwnd for
 * the fast retransmit algorithm. With this defined
 * a FR happens right away with-out waiting for the
 * flightsize to drop below the cwnd value (which
 * is reduced by the FR to 1/2 the inflight packets).
 */
#define SCTP_IGNORE_CWND_ON_FR 1
/* default max I can burst out after a fast retransmit */
#define SCTP_DEF_MAX_BURST 4

/* Packet transmit states in the sent
 * field in the SCTP_transmitOnQueue struct
 */
#define SCTP_DATAGRAM_UNSENT 		0
#define SCTP_DATAGRAM_SENT   		1
#define SCTP_DATAGRAM_RESEND1		2 /* not used */
#define SCTP_DATAGRAM_RESEND2		3 /* not used */
#define SCTP_DATAGRAM_RESEND3		4 /* not used */
#define SCTP_DATAGRAM_RESEND		5
#define SCTP_DATAGRAM_ACKED		10010
#define SCTP_DATAGRAM_INBOUND		10011
#define SCTP_READY_TO_TRANSMIT		10012
#define SCTP_DATAGRAM_MARKED		20010

#define MAX_FSID 64	/* debug 5 ints used for cc dynamic tracking */

/* The valid defines for all message
 * types know to SCTP. 0 is reserved
 */
#define SCTP_MSGTYPE_MASK	0xff

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

/* ABORT and SHUTDOWN COMPLETE FLAG */
#define SCTP_HAD_NO_TCB		0x01

/* Data Chuck Specific Flags */
#define SCTP_DATA_FRAG_MASK	0x03
#define SCTP_DATA_MIDDLE_FRAG	0x00
#define SCTP_DATA_LAST_FRAG	0x01
#define SCTP_DATA_FIRST_FRAG	0x02
#define SCTP_DATA_NOT_FRAG	0x03
#define SCTP_DATA_UNORDERED	0x04

#define SCTP_CRC_ENABLE_BIT	0x01	/* lower bit of reserved */

#define isSCTPControl(a) (a->chunkID != SCTP_DATA)
#define isSCTPData(a) (a->chunkID == SCTP_DATA)

/* sctp parameter types for init/init-ack */

#define SCTP_IPV4_PARAM_TYPE    0x0005
#define SCTP_IPV6_PARAM_TYPE    0x0006
#define SCTP_RESPONDER_COOKIE   0x0007
#define SCTP_UNRECOG_PARAM	0x0008
#define SCTP_COOKIE_PRESERVE    0x0009
#define SCTP_HOSTNAME_VIA_DNS   0x000b
#define SCTP_RESTRICT_ADDR_TO	0x000c

#define SCTP_ECN_I_CAN_DO_ECN	0x8000
#define SCTP_OPERATION_SUCCEED	0x4001
#define SCTP_ERROR_NOT_EXECUTED	0x4002

#define SCTP_UNRELIABLE_STRM    0xc000
#define SCTP_ADD_IP_ADDRESS     0xc001
#define SCTP_DEL_IP_ADDRESS     0xc002
#define SCTP_STRM_FLOW_LIMIT    0xc003
#define SCTP_PARTIAL_CSUM       0xc004
#define SCTP_ERROR_CAUSE_TLV	0xc005
#define SCTP_MIT_STACK_NAME	0xc006
#define SCTP_SETADDRESS_PRIMARY 0xc007

/* bits for TOS field */
#define SCTP_ECT_BIT		0x02
#define SCTP_CE_BIT		0x01

/* error codes */
#define SCTP_OP_ERROR_NO_ERROR		0x0000
#define SCTP_OP_ERROR_INV_STRM		0x0001
#define SCTP_OP_ERROR_MISS_PARAM	0x0002
#define SCTP_OP_ERROR_STALE_COOKIE	0x0003
#define SCTP_OP_ERROR_NO_RESOURCE 	0x0004
#define SCTP_OP_ERROR_DNS_FAILED   	0x0005
#define SCTP_OP_ERROR_UNK_CHUNK	   	0x0006
#define SCTP_OP_ERROR_INV_PARAM		0x0007
#define SCTP_OP_ERROR_UNK_PARAM	       	0x0008
#define SCTP_OP_ERROR_NO_USERD    	0x0009
#define SCTP_OP_ERROR_COOKIE_SHUT	0x000a
#define SCTP_OP_ERROR_DELETE_LAST	0x000b
#define SCTP_OP_ERROR_RESOURCE_SHORT 	0x000c

#define SCTP_MAX_ERROR_CAUSE  12

/* empty error causes i.e. nothing but the cause
 * are SCTP_OP_ERROR_NO_RESOURCE, SCTP_OP_ERROR_INV_PARAM,
 * SCTP_OP_ERROR_COOKIE_SHUT.
 */

/* parameter for Heart Beat */
#define HEART_BEAT_PARAM 0x0001



/* send options for SCTP
 */
#define SCTP_ORDERED_DELIVERY		0x01
#define SCTP_NON_ORDERED_DELIVERY	0x02
#define SCTP_DO_CRC16			0x08
#define SCTP_MY_ADDRESS_ONLY		0x10

/* below turns off above */
#define SCTP_FLEXIBLE_ADDRESS		0x20
#define SCTP_NO_HEARTBEAT		0x40

/* mask to get sticky */
#define SCTP_STICKY_OPTIONS_MASK        0x0c

/* MTU discovery flags */
#define SCTP_DONT_FRAGMENT		0x0100
#define SCTP_FRAGMENT_OK		0x0200


/* SCTP state defines for internal state machine */
#define SCTP_STATE_EMPTY		0x0000
#define SCTP_STATE_INUSE		0x0001
#define SCTP_STATE_COOKIE_WAIT		0x0002
#define SCTP_STATE_COOKIE_SENT		0x0004
#define SCTP_STATE_OPEN			0x0008
#define SCTP_STATE_SHUTDOWN		0x0010
#define SCTP_STATE_SHUTDOWN_RECV	0x0020
#define SCTP_STATE_SHUTDOWN_ACK_SENT	0x0040
#define SCTP_STATE_SHUTDOWN_PEND	0x0080
#define SCTP_STATE_MASK			0x007f
/* SCTP reachability state for each address */
#define SCTP_ADDR_NOT_REACHABLE		1
#define SCTP_ADDR_REACHABLE		2
#define SCTP_ADDR_NOHB			4
#define SCTP_ADDR_BEING_DELETED		8

/* How long a cookie lives */
#define SCTP_DEFAULT_COOKIE_LIFE 60 /* seconds */

/* resource limit of streams */
#define MAX_SCTP_STREAMS 2048


/* guess at how big to make the TSN mapping array */
#define SCTP_STARTING_MAPARRAY 10000

/* Here we define the timer types used
 * by the implementation has
 * arguments in the set/get timer type calls.
 */
#define SCTP_TIMER_INIT 	0
#define SCTP_TIMER_RECV 	1
#define SCTP_TIMER_SEND 	2
#define SCTP_TIMER_SHUTDOWN	3
#define SCTP_TIMER_HEARTBEAT	4
#define SCTP_TIMER_PMTU		5
/* number of timer types in the base SCTP
 * structure used in the set/get and has
 * the base default.
 */
#define SCTP_NUM_TMRS 6



#define SCTP_IPV4_ADDRESS	2
#define SCTP_IPV6_ADDRESS	4

/* timer types */
#define SctpTimerTypeNone		0
#define SctpTimerTypeSend		1
#define SctpTimerTypeInit		2
#define SctpTimerTypeRecv		3
#define SctpTimerTypeShutdown		4
#define SctpTimerTypeHeartbeat		5
#define SctpTimerTypeCookie		6
#define SctpTimerTypeNewCookie		7
#define SctpTimerTypePathMtuRaise	8
#define SctpTimerTypeShutdownAck	9
#define SctpTimerTypeRelReq		10

/* Here are the timer directives given to the
 * user provided function
 */
#define SCTP_TIMER_START	1
#define SCTP_TIMER_STOP		2

/* running flag states in timer structure */
#define SCTP_TIMER_IDLE		0x0
#define SCTP_TIMER_EXPIRED	0x1
#define SCTP_TIMER_RUNNING	0x2


/* number of simultaneous timers running */
#define SCTP_MAX_NET_TIMERS     6	/* base of where net timers start */
#define SCTP_NUMBER_TIMERS	12	/* allows up to 6 destinations */


/* Of course we really don't collect stale cookies, being
 * folks of decerning taste. However we do count them, if
 * we get to many before the association comes up.. we
 * give up. Below is the constant that dictates when
 * we give it up...this is a implemenation dependant
 * treatment. In ours we do not ask for a extension of
 * time, but just retry this many times...
 */
#define SCTP_MAX_STALE_COOKIES_I_COLLECT 10

/* max number of TSN's dup'd that I will hold */
#define SCTP_MAX_DUP_TSNS      20

/* Here we define the types used when
 * setting the retry ammounts.
 */
/* constants for type of set */
#define SCTP_MAXATTEMPT_INIT 2
#define SCTP_MAXATTEMPT_SEND 3

/* Here we define the default timers and the
 * default number of attemts we make for
 * each respective side (send/init).
 */

/* init timer def = 3sec  */
#define SCTP_INIT_SEC	3
#define SCTP_INIT_NSEC	0

/* send timer def = 3 seconds */
#define SCTP_SEND_SEC	1
#define SCTP_SEND_NSEC	0

/* recv timer def = 200ms (in nsec) */
#define SCTP_RECV_SEC	0
#define SCTP_RECV_NSEC	200000000

/* 30 seconds + RTO */
#define SCTP_HB_SEC	30
#define SCTP_HB_NSEC	0


/* 300 ms */
#define SCTP_SHUTDOWN_SEC	0
#define SCTP_SHUTDOWN_NSEC	300000000

#define SCTP_RTO_UPPER_BOUND 60000000 /* 60 sec in micro-second's */
#define SCTP_RTO_UPPER_BOUND_SEC 60  /* for the init timer */
#define SCTP_RTO_LOWER_BOUND  1000000 /* 1 sec in micro-sec's */

#define SCTP_DEF_MAX_INIT 8
#define SCTP_DEF_MAX_SEND 10

#define SCTP_DEF_PMTU_RAISE 600  /* 10 Minutes between raise attempts */
#define SCTP_DEF_PMTU_MIN   600

#define SCTP_MSEC_IN_A_SEC  1000
#define SCTP_USEC_IN_A_SEC  1000000
#define SCTP_NSEC_IN_A_SEC  1000000000


/* Events that SCTP will look for, these
 * are or'd together to declare what SCTP
 * wants. Each select mask/poll list should be
 * set for the fd, if the bit is on.
 */
#define SCTP_EVENT_READ		0x000001
#define SCTP_EVENT_WRITE	0x000002
#define SCTP_EVENT_EXCEPT	0x000004

/* The following constant is a value for this
 * particular implemenation. It is quite arbitrary and
 * is used to limit how much data will be queued up to
 * a sender, waiting for cwnd to be larger than flightSize.
 * All implementations will need this protection is some
 * way due to buffer size constraints.
 */

#define SCTP_MAX_OUTSTANDING_DG	10000



/* This constant (SCTP_MAX_READBUFFER) define
 * how big the read/write buffer is
 * when we enter the fd event notification
 * the buffer is put on the stack, so the bigger
 * it is the more stack you chew up, however it
 * has got to be big enough to handle the bigest
 * message this O/S will send you. In solaris
 * with sockets (not TLI) we end up at a value
 * of 64k. In TLI we could do partial reads to
 * get it all in with less hassel.. but we
 * write to sockets for generality.
 */
#define SCTP_MAX_READBUFFER 65536
#define SCTP_ADDRMAX 60

/* amount peer is obligated to have in rwnd or
 * I will abort
 */
#define SCTP_MIN_RWND	1500

#define SCTP_WINDOW_MIN	1500	/* smallest rwnd can be */
#define SCTP_WINDOW_MAX 1048576	/* biggest I can grow rwnd to
				 * My playing around suggests a
				 * value greater than 64k does not
				 * do much, I guess via the kernel
				 * limitations on the stream/socket.
				 */

#define SCTP_MAX_BUNDLE_UP 256	/* max number of chunks I can bundle */

/*  I can handle a 1meg re-assembly */
#define SCTP_DEFAULT_MAXMSGREASM 1048576


#define SCTP_DEFAULT_MAXWINDOW	32768	/* default rwnd size */
#define SCTP_DEFAULT_MAXSEGMENT 1500	/* MTU size, this is the default
                                         * to which we set the smallestMTU
					 * size to. This governs what is the
					 * largest size we will use, of course
					 * PMTU will raise this up to
					 * the largest interface MTU or the
					 * ceiling below if there is no
					 * SIOCGIFMTU.
					 */
#ifdef LYNX
#define DEFAULT_MTU_CEILING  1500 	/* Since Lynx O/S is brain dead
					 * in the way it handles the
					 * raw IP socket, insisting
					 * on makeing its own IP
					 * header, we limit the growth
					 * to that of the e-net size
					 */
#else
#define DEFAULT_MTU_CEILING  2048	/* If no SIOCGIFMTU, highest value
					 * to raise the PMTU to, i.e.
					 * don't try to raise above this
					 * value. Tune this per your
					 * largest MTU interface if your
					 * system does not support the
					 * SIOCGIFMTU ioctl.
					 */
#endif
#define SCTP_DEFAULT_MINSEGMENT 512	/* MTU size ... if no mtu disc */
#define SCTP_HOW_MANY_SECRETS 2		/* how many secrets I keep */
/* This is how long a secret lives, NOT how long a cookie lives */
#define SCTP_HOW_LONG_COOKIE_LIVE 3600	/* how many seconds the current secret will live */

#define SCTP_NUMBER_OF_SECRETS	8	/* or 8 * 4 = 32 octets */
#define SCTP_SECRET_SIZE 32		/* number of octets in a 256 bits */

#ifdef USE_MD5
#define SCTP_SIGNATURE_SIZE 16	/* size of a MD5 signature */
#else
#define SCTP_SIGNATURE_SIZE 20	/* size of a SLA-1 signature */
#endif
/* Here are the notification constants
 * that the code and upper layer will get
 */

/* association is up */
#define SCTP_NOTIFY_ASSOC_UP		1

/* association is down */
#define SCTP_NOTIFY_ASSOC_DOWN		2

/* interface on a association is down
 * and out of consideration for selection.
 */
#define SCTP_NOTIFY_INTF_DOWN		3

/* interface on a association is up
 * and now back in consideration for selection.
 */
#define SCTP_NOTIFY_INTF_UP		4

/* The given datagram cannot be delivered
 * to the peer, this will probably be followed
 * by a SCTP_NOTFIY_ASSOC_DOWN.
 */
#define SCTP_NOTIFY_DG_FAIL		5

/* Sent dg on non-open stream extreme code error!
 */
#define SCTP_NOTIFY_STRDATA_ERR 	6

#define SCTP_NOTIFY_ASSOC_ABORTED	7

/* The stream ones are not used yet, but could
 * be when a association opens.
 */
#define SCTP_NOTIFY_PEER_OPENED_STR	8
#define SCTP_NOTIFY_STREAM_OPENED_OK	9

/* association sees a restart event */
#define SCTP_NOTIFY_ASSOC_RESTART	10

/* a user requested HB returned */
#define SCTP_NOTIFY_HB_RESP             11

/* a result from a REL-REQ */
#define SCTP_NOTIFY_RELREQ_RESULT_OK		12
#define SCTP_NOTIFY_RELREQ_RESULT_FAILED	13

/* clock variance is 10ms or 10,000 us's */
#define SCTP_CLOCK_GRAINULARITY 10000

#define IP_HDR_SIZE 40		/* we use the size of a IP6 header here
				 * this detracts a small amount for ipv4
				 * but it simplifies the ipv6 addition
				 */

#define SCTP_NUM_FDS 3

/* raw IP filedescriptor */
#define SCTP_FD_IP   0
/* raw ICMP filedescriptor */
#define SCTP_FD_ICMP 1
/* processes contact me for requests here */
#define SCTP_REQUEST 2


#define SCTP_DEAMON_PORT 9899

/* Deamon registration message types/responses */
#define DEAMON_REGISTER       0x01
#define DEAMON_REGISTER_ACK   0x02
#define DEAMON_DEREGISTER     0x03
#define DEAMON_DEREGISTER_ACK 0x04
#define DEAMON_CHECKADDR_LIST 0x05

#define DEAMON_MAGIC_VER_LEN 0xff

/* max times I will attempt to send a message to deamon */
#define SCTP_MAX_ATTEMPTS_AT_DEAMON 5
#define SCTP_TIMEOUT_IN_POLL_FOR_DEAMON 1500 /* 1.5 seconds */

/* modular comparison */
/* True if a > b (mod = M) */
#define compare_with_wrap(a, b, M) ((a > b) && ((a - b) < (M >> 1))) || \
              ((b > a) && ((b - a) > (M >> 1)))

#ifndef TIMEVAL_TO_TIMESPEC
#define TIMEVAL_TO_TIMESPEC(tv, ts)			\
{							\
    (ts)->tv_sec  = (tv)->tv_sec;			\
    (ts)->tv_nsec = (tv)->tv_usec * 1000;		\
}
#endif

/* pegs */
#define SCTP_NUMBER_OF_PEGS 21
/* peg index's */
#define SCTP_PEG_SACKS_SEEN 0
#define SCTP_PEG_SACKS_SENT 1
#define SCTP_PEG_TSNS_SENT  2
#define SCTP_PEG_TSNS_RCVD  3
#define SCTP_DATAGRAMS_SENT 4
#define SCTP_DATAGRAMS_RCVD 5
#define SCTP_RETRANTSN_SENT 6
#define SCTP_DUPTSN_RECVD   7
#define SCTP_HBR_RECV	    8
#define SCTP_HBA_RECV       9
#define SCTP_HB_SENT	   10
#define SCTP_DATA_DG_SENT  11
#define SCTP_DATA_DG_RECV  12
#define SCTP_TMIT_TIMER    13
#define SCTP_RECV_TIMER    14
#define SCTP_HB_TIMER      15
#define SCTP_FAST_RETRAN   16
#define SCTP_PEG_TSNS_READ 17
#define SCTP_NONE_LFT_TO   18
#define SCTP_NONE_LFT_RWND 19
#define SCTP_NONE_LFT_CWND 20



#endif

