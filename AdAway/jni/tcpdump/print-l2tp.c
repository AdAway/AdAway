/*
 * Copyright (c) 1991, 1993, 1994, 1995, 1996, 1997
 *      The Regents of the University of California.  All rights reserved.
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
 * L2TP support contributed by Motonori Shindo (mshindo@mshindo.net)
 */

#ifndef lint
static const char rcsid[] _U_ =
    "@(#) $Header: /tcpdump/master/tcpdump/print-l2tp.c,v 1.20 2006-06-23 02:03:09 hannes Exp $";
#endif

#ifdef HAVE_CONFIG_H
#include "config.h"
#endif

#include <tcpdump-stdinc.h>

#include <stdio.h>

#include "l2tp.h"
#include "interface.h"
#include "extract.h"

static char tstr[] = " [|l2tp]";

#define	L2TP_MSGTYPE_SCCRQ	1  /* Start-Control-Connection-Request */
#define	L2TP_MSGTYPE_SCCRP	2  /* Start-Control-Connection-Reply */
#define	L2TP_MSGTYPE_SCCCN	3  /* Start-Control-Connection-Connected */
#define	L2TP_MSGTYPE_STOPCCN	4  /* Stop-Control-Connection-Notification */
#define	L2TP_MSGTYPE_HELLO	6  /* Hello */
#define	L2TP_MSGTYPE_OCRQ	7  /* Outgoing-Call-Request */
#define	L2TP_MSGTYPE_OCRP	8  /* Outgoing-Call-Reply */
#define	L2TP_MSGTYPE_OCCN	9  /* Outgoing-Call-Connected */
#define	L2TP_MSGTYPE_ICRQ	10 /* Incoming-Call-Request */
#define	L2TP_MSGTYPE_ICRP	11 /* Incoming-Call-Reply */
#define	L2TP_MSGTYPE_ICCN	12 /* Incoming-Call-Connected */
#define	L2TP_MSGTYPE_CDN	14 /* Call-Disconnect-Notify */
#define	L2TP_MSGTYPE_WEN	15 /* WAN-Error-Notify */
#define	L2TP_MSGTYPE_SLI	16 /* Set-Link-Info */

static const struct tok l2tp_msgtype2str[] = {
	{ L2TP_MSGTYPE_SCCRQ, 	"SCCRQ" },
	{ L2TP_MSGTYPE_SCCRP,	"SCCRP" },
	{ L2TP_MSGTYPE_SCCCN,	"SCCCN" },
	{ L2TP_MSGTYPE_STOPCCN,	"StopCCN" },
	{ L2TP_MSGTYPE_HELLO,	"HELLO" },
	{ L2TP_MSGTYPE_OCRQ,	"OCRQ" },
	{ L2TP_MSGTYPE_OCRP,	"OCRP" },
	{ L2TP_MSGTYPE_OCCN,	"OCCN" },
	{ L2TP_MSGTYPE_ICRQ,	"ICRQ" },
	{ L2TP_MSGTYPE_ICRP,	"ICRP" },
	{ L2TP_MSGTYPE_ICCN,	"ICCN" },
	{ L2TP_MSGTYPE_CDN,	"CDN" },
	{ L2TP_MSGTYPE_WEN,	"WEN" },
	{ L2TP_MSGTYPE_SLI,	"SLI" },
	{ 0,			NULL }
};

#define L2TP_AVP_MSGTYPE		0  /* Message Type */
#define L2TP_AVP_RESULT_CODE		1  /* Result Code */
#define L2TP_AVP_PROTO_VER		2  /* Protocol Version */
#define L2TP_AVP_FRAMING_CAP		3  /* Framing Capabilities */
#define L2TP_AVP_BEARER_CAP		4  /* Bearer Capabilities */
#define L2TP_AVP_TIE_BREAKER		5  /* Tie Breaker */
#define L2TP_AVP_FIRM_VER		6  /* Firmware Revision */
#define L2TP_AVP_HOST_NAME		7  /* Host Name */
#define L2TP_AVP_VENDOR_NAME		8  /* Vendor Name */
#define L2TP_AVP_ASSND_TUN_ID 		9  /* Assigned Tunnel ID */
#define L2TP_AVP_RECV_WIN_SIZE		10 /* Receive Window Size */
#define L2TP_AVP_CHALLENGE		11 /* Challenge */
#define L2TP_AVP_Q931_CC		12 /* Q.931 Cause Code */
#define L2TP_AVP_CHALLENGE_RESP		13 /* Challenge Response */
#define L2TP_AVP_ASSND_SESS_ID  	14 /* Assigned Session ID */
#define L2TP_AVP_CALL_SER_NUM 		15 /* Call Serial Number */
#define L2TP_AVP_MINIMUM_BPS		16 /* Minimum BPS */
#define L2TP_AVP_MAXIMUM_BPS		17 /* Maximum BPS */
#define L2TP_AVP_BEARER_TYPE		18 /* Bearer Type */
#define L2TP_AVP_FRAMING_TYPE 		19 /* Framing Type */
#define L2TP_AVP_PACKET_PROC_DELAY	20 /* Packet Processing Delay (OBSOLETE) */
#define L2TP_AVP_CALLED_NUMBER		21 /* Called Number */
#define L2TP_AVP_CALLING_NUMBER		22 /* Calling Number */
#define L2TP_AVP_SUB_ADDRESS		23 /* Sub-Address */
#define L2TP_AVP_TX_CONN_SPEED		24 /* (Tx) Connect Speed */
#define L2TP_AVP_PHY_CHANNEL_ID		25 /* Physical Channel ID */
#define L2TP_AVP_INI_RECV_LCP		26 /* Initial Received LCP CONFREQ */
#define L2TP_AVP_LAST_SENT_LCP		27 /* Last Sent LCP CONFREQ */
#define L2TP_AVP_LAST_RECV_LCP		28 /* Last Received LCP CONFREQ */
#define L2TP_AVP_PROXY_AUTH_TYPE	29 /* Proxy Authen Type */
#define L2TP_AVP_PROXY_AUTH_NAME	30 /* Proxy Authen Name */
#define L2TP_AVP_PROXY_AUTH_CHAL	31 /* Proxy Authen Challenge */
#define L2TP_AVP_PROXY_AUTH_ID		32 /* Proxy Authen ID */
#define L2TP_AVP_PROXY_AUTH_RESP	33 /* Proxy Authen Response */
#define L2TP_AVP_CALL_ERRORS		34 /* Call Errors */
#define L2TP_AVP_ACCM			35 /* ACCM */
#define L2TP_AVP_RANDOM_VECTOR		36 /* Random Vector */
#define L2TP_AVP_PRIVATE_GRP_ID		37 /* Private Group ID */
#define L2TP_AVP_RX_CONN_SPEED		38 /* (Rx) Connect Speed */
#define L2TP_AVP_SEQ_REQUIRED 		39 /* Sequencing Required */
#define L2TP_AVP_PPP_DISCON_CC		46 /* PPP Disconnect Cause Code */

static const struct tok l2tp_avp2str[] = {
	{ L2TP_AVP_MSGTYPE,		"MSGTYPE" },
	{ L2TP_AVP_RESULT_CODE,		"RESULT_CODE" },
	{ L2TP_AVP_PROTO_VER,		"PROTO_VER" },
	{ L2TP_AVP_FRAMING_CAP,		"FRAMING_CAP" },
	{ L2TP_AVP_BEARER_CAP,		"BEARER_CAP" },
	{ L2TP_AVP_TIE_BREAKER,		"TIE_BREAKER" },
	{ L2TP_AVP_FIRM_VER,		"FIRM_VER" },
	{ L2TP_AVP_HOST_NAME,		"HOST_NAME" },
	{ L2TP_AVP_VENDOR_NAME,		"VENDOR_NAME" },
	{ L2TP_AVP_ASSND_TUN_ID,	"ASSND_TUN_ID" },
	{ L2TP_AVP_RECV_WIN_SIZE,	"RECV_WIN_SIZE" },
	{ L2TP_AVP_CHALLENGE,		"CHALLENGE" },
	{ L2TP_AVP_Q931_CC,		"Q931_CC", },
	{ L2TP_AVP_CHALLENGE_RESP,	"CHALLENGE_RESP" },
	{ L2TP_AVP_ASSND_SESS_ID,	"ASSND_SESS_ID" },
	{ L2TP_AVP_CALL_SER_NUM,	"CALL_SER_NUM" },
	{ L2TP_AVP_MINIMUM_BPS,		"MINIMUM_BPS" },
	{ L2TP_AVP_MAXIMUM_BPS,		"MAXIMUM_BPS" },
	{ L2TP_AVP_BEARER_TYPE,		"BEARER_TYPE" },
	{ L2TP_AVP_FRAMING_TYPE,	"FRAMING_TYPE" },
	{ L2TP_AVP_PACKET_PROC_DELAY,	"PACKET_PROC_DELAY" },
	{ L2TP_AVP_CALLED_NUMBER,	"CALLED_NUMBER" },
	{ L2TP_AVP_CALLING_NUMBER,	"CALLING_NUMBER" },
	{ L2TP_AVP_SUB_ADDRESS,		"SUB_ADDRESS" },
	{ L2TP_AVP_TX_CONN_SPEED,	"TX_CONN_SPEED" },
	{ L2TP_AVP_PHY_CHANNEL_ID,	"PHY_CHANNEL_ID" },
	{ L2TP_AVP_INI_RECV_LCP,	"INI_RECV_LCP" },
	{ L2TP_AVP_LAST_SENT_LCP,	"LAST_SENT_LCP" },
	{ L2TP_AVP_LAST_RECV_LCP,	"LAST_RECV_LCP" },
	{ L2TP_AVP_PROXY_AUTH_TYPE,	"PROXY_AUTH_TYPE" },
	{ L2TP_AVP_PROXY_AUTH_NAME,	"PROXY_AUTH_NAME" },
	{ L2TP_AVP_PROXY_AUTH_CHAL,	"PROXY_AUTH_CHAL" },
	{ L2TP_AVP_PROXY_AUTH_ID,	"PROXY_AUTH_ID" },
	{ L2TP_AVP_PROXY_AUTH_RESP,	"PROXY_AUTH_RESP" },
	{ L2TP_AVP_CALL_ERRORS,		"CALL_ERRORS" },
	{ L2TP_AVP_ACCM,		"ACCM" },
	{ L2TP_AVP_RANDOM_VECTOR,	"RANDOM_VECTOR" },
	{ L2TP_AVP_PRIVATE_GRP_ID,	"PRIVATE_GRP_ID" },
	{ L2TP_AVP_RX_CONN_SPEED,	"RX_CONN_SPEED" },
	{ L2TP_AVP_SEQ_REQUIRED,	"SEQ_REQUIRED" },
	{ L2TP_AVP_PPP_DISCON_CC,	"PPP_DISCON_CC" },
	{ 0,				NULL }
};

static const struct tok l2tp_authentype2str[] = {
	{ L2TP_AUTHEN_TYPE_RESERVED,	"Reserved" },
	{ L2TP_AUTHEN_TYPE_TEXTUAL,	"Textual" },
	{ L2TP_AUTHEN_TYPE_CHAP,	"CHAP" },
	{ L2TP_AUTHEN_TYPE_PAP,		"PAP" },
	{ L2TP_AUTHEN_TYPE_NO_AUTH,	"No Auth" },
	{ L2TP_AUTHEN_TYPE_MSCHAPv1,	"MS-CHAPv1" },
	{ 0,				NULL }
};

#define L2TP_PPP_DISCON_CC_DIRECTION_GLOBAL	0
#define L2TP_PPP_DISCON_CC_DIRECTION_AT_PEER	1
#define L2TP_PPP_DISCON_CC_DIRECTION_AT_LOCAL	2

static const struct tok l2tp_cc_direction2str[] = {
	{ L2TP_PPP_DISCON_CC_DIRECTION_GLOBAL,	"global error" },
	{ L2TP_PPP_DISCON_CC_DIRECTION_AT_PEER,	"at peer" },
	{ L2TP_PPP_DISCON_CC_DIRECTION_AT_LOCAL,"at local" },
	{ 0,					NULL }
};

#if 0
static char *l2tp_result_code_StopCCN[] = {
         "Reserved",
         "General request to clear control connection",
         "General error--Error Code indicates the problem",
         "Control channel already exists",
         "Requester is not authorized to establish a control channel",
         "The protocol version of the requester is not supported",
         "Requester is being shut down",
         "Finite State Machine error"
#define L2TP_MAX_RESULT_CODE_STOPCC_INDEX	8
};
#endif

#if 0
static char *l2tp_result_code_CDN[] = {
	"Reserved",
	"Call disconnected due to loss of carrier",
	"Call disconnected for the reason indicated in error code",
	"Call disconnected for administrative reasons",
	"Call failed due to lack of appropriate facilities being " \
	"available (temporary condition)",
	"Call failed due to lack of appropriate facilities being " \
	"available (permanent condition)",
	"Invalid destination",
	"Call failed due to no carrier detected",
	"Call failed due to detection of a busy signal",
	"Call failed due to lack of a dial tone",
	"Call was not established within time allotted by LAC",
	"Call was connected but no appropriate framing was detected"
#define L2TP_MAX_RESULT_CODE_CDN_INDEX	12
};
#endif

#if 0
static char *l2tp_error_code_general[] = {
	"No general error",
	"No control connection exists yet for this LAC-LNS pair",
	"Length is wrong",
	"One of the field values was out of range or " \
	"reserved field was non-zero"
	"Insufficient resources to handle this operation now",
	"The Session ID is invalid in this context",
	"A generic vendor-specific error occurred in the LAC",
	"Try another"
#define L2TP_MAX_ERROR_CODE_GENERAL_INDEX	8
};
#endif

/******************************/
/* generic print out routines */
/******************************/
static void
print_string(const u_char *dat, u_int length)
{
	u_int i;
	for (i=0; i<length; i++) {
		printf("%c", *dat++);
	}
}

static void
print_octets(const u_char *dat, u_int length)
{
	u_int i;
	for (i=0; i<length; i++) {
		printf("%02x", *dat++);
	}
}

static void
print_16bits_val(const u_int16_t *dat)
{
	printf("%u", EXTRACT_16BITS(dat));
}

static void
print_32bits_val(const u_int32_t *dat)
{
	printf("%lu", (u_long)EXTRACT_32BITS(dat));
}

/***********************************/
/* AVP-specific print out routines */
/***********************************/
static void
l2tp_msgtype_print(const u_char *dat)
{
	u_int16_t *ptr = (u_int16_t*)dat;

	printf("%s", tok2str(l2tp_msgtype2str, "MSGTYPE-#%u",
	    EXTRACT_16BITS(ptr)));
}

static void
l2tp_result_code_print(const u_char *dat, u_int length)
{
	u_int16_t *ptr = (u_int16_t *)dat;

	printf("%u", EXTRACT_16BITS(ptr)); ptr++;	/* Result Code */
	if (length > 2) {				/* Error Code (opt) */
	        printf("/%u", EXTRACT_16BITS(ptr)); ptr++;
	}
	if (length > 4) {				/* Error Message (opt) */
		printf(" ");
		print_string((u_char *)ptr, length - 4);
	}
}

static void
l2tp_proto_ver_print(const u_int16_t *dat)
{
	printf("%u.%u", (EXTRACT_16BITS(dat) >> 8),
	    (EXTRACT_16BITS(dat) & 0xff));
}

static void
l2tp_framing_cap_print(const u_char *dat)
{
	u_int32_t *ptr = (u_int32_t *)dat;

	if (EXTRACT_32BITS(ptr) &  L2TP_FRAMING_CAP_ASYNC_MASK) {
		printf("A");
	}
	if (EXTRACT_32BITS(ptr) &  L2TP_FRAMING_CAP_SYNC_MASK) {
		printf("S");
	}
}

static void
l2tp_bearer_cap_print(const u_char *dat)
{
	u_int32_t *ptr = (u_int32_t *)dat;

	if (EXTRACT_32BITS(ptr) &  L2TP_BEARER_CAP_ANALOG_MASK) {
		printf("A");
	}
	if (EXTRACT_32BITS(ptr) &  L2TP_BEARER_CAP_DIGITAL_MASK) {
		printf("D");
	}
}

static void
l2tp_q931_cc_print(const u_char *dat, u_int length)
{
	print_16bits_val((u_int16_t *)dat);
	printf(", %02x", dat[2]);
	if (length > 3) {
		printf(" ");
		print_string(dat+3, length-3);
	}
}

static void
l2tp_bearer_type_print(const u_char *dat)
{
	u_int32_t *ptr = (u_int32_t *)dat;

	if (EXTRACT_32BITS(ptr) &  L2TP_BEARER_TYPE_ANALOG_MASK) {
		printf("A");
	}
	if (EXTRACT_32BITS(ptr) &  L2TP_BEARER_TYPE_DIGITAL_MASK) {
		printf("D");
	}
}

static void
l2tp_framing_type_print(const u_char *dat)
{
	u_int32_t *ptr = (u_int32_t *)dat;

	if (EXTRACT_32BITS(ptr) &  L2TP_FRAMING_TYPE_ASYNC_MASK) {
		printf("A");
	}
	if (EXTRACT_32BITS(ptr) &  L2TP_FRAMING_TYPE_SYNC_MASK) {
		printf("S");
	}
}

static void
l2tp_packet_proc_delay_print(void)
{
	printf("obsolete");
}

static void
l2tp_proxy_auth_type_print(const u_char *dat)
{
	u_int16_t *ptr = (u_int16_t *)dat;

	printf("%s", tok2str(l2tp_authentype2str,
			     "AuthType-#%u", EXTRACT_16BITS(ptr)));
}

static void
l2tp_proxy_auth_id_print(const u_char *dat)
{
	u_int16_t *ptr = (u_int16_t *)dat;

	printf("%u", EXTRACT_16BITS(ptr) & L2TP_PROXY_AUTH_ID_MASK);
}

static void
l2tp_call_errors_print(const u_char *dat)
{
	u_int16_t *ptr = (u_int16_t *)dat;
	u_int16_t val_h, val_l;

	ptr++;		/* skip "Reserved" */

	val_h = EXTRACT_16BITS(ptr); ptr++;
	val_l = EXTRACT_16BITS(ptr); ptr++;
	printf("CRCErr=%u ", (val_h<<16) + val_l);

	val_h = EXTRACT_16BITS(ptr); ptr++;
	val_l = EXTRACT_16BITS(ptr); ptr++;
	printf("FrameErr=%u ", (val_h<<16) + val_l);

	val_h = EXTRACT_16BITS(ptr); ptr++;
	val_l = EXTRACT_16BITS(ptr); ptr++;
	printf("HardOver=%u ", (val_h<<16) + val_l);

	val_h = EXTRACT_16BITS(ptr); ptr++;
	val_l = EXTRACT_16BITS(ptr); ptr++;
	printf("BufOver=%u ", (val_h<<16) + val_l);

	val_h = EXTRACT_16BITS(ptr); ptr++;
	val_l = EXTRACT_16BITS(ptr); ptr++;
	printf("Timeout=%u ", (val_h<<16) + val_l);

	val_h = EXTRACT_16BITS(ptr); ptr++;
	val_l = EXTRACT_16BITS(ptr); ptr++;
	printf("AlignErr=%u ", (val_h<<16) + val_l);
}

static void
l2tp_accm_print(const u_char *dat)
{
	u_int16_t *ptr = (u_int16_t *)dat;
	u_int16_t val_h, val_l;

	ptr++;		/* skip "Reserved" */

	val_h = EXTRACT_16BITS(ptr); ptr++;
	val_l = EXTRACT_16BITS(ptr); ptr++;
	printf("send=%08x ", (val_h<<16) + val_l);

	val_h = EXTRACT_16BITS(ptr); ptr++;
	val_l = EXTRACT_16BITS(ptr); ptr++;
	printf("recv=%08x ", (val_h<<16) + val_l);
}

static void
l2tp_ppp_discon_cc_print(const u_char *dat, u_int length)
{
	u_int16_t *ptr = (u_int16_t *)dat;

	printf("%04x, ", EXTRACT_16BITS(ptr)); ptr++;	/* Disconnect Code */
	printf("%04x ",  EXTRACT_16BITS(ptr)); ptr++;	/* Control Protocol Number */
	printf("%s", tok2str(l2tp_cc_direction2str,
			     "Direction-#%u", *((u_char *)ptr++)));

	if (length > 5) {
		printf(" ");
		print_string((const u_char *)ptr, length-5);
	}
}

static void
l2tp_avp_print(const u_char *dat, int length)
{
	u_int len;
	const u_int16_t *ptr = (u_int16_t *)dat;
	u_int16_t attr_type;
	int hidden = FALSE;

	if (length <= 0) {
		return;
	}

	printf(" ");

	TCHECK(*ptr);	/* Flags & Length */
	len = EXTRACT_16BITS(ptr) & L2TP_AVP_HDR_LEN_MASK;

	/* If it is not long enough to contain the header, we'll give up. */
	if (len < 6)
		goto trunc;

	/* If it goes past the end of the remaining length of the packet,
	   we'll give up. */
	if (len > (u_int)length)
		goto trunc;

	/* If it goes past the end of the remaining length of the captured
	   data, we'll give up. */
	TCHECK2(*ptr, len);
	/* After this point, no need to worry about truncation */

	if (EXTRACT_16BITS(ptr) & L2TP_AVP_HDR_FLAG_MANDATORY) {
		printf("*");
	}
	if (EXTRACT_16BITS(ptr) & L2TP_AVP_HDR_FLAG_HIDDEN) {
		hidden = TRUE;
		printf("?");
	}
	ptr++;

	if (EXTRACT_16BITS(ptr)) {
		/* Vendor Specific Attribute */
	        printf("VENDOR%04x:", EXTRACT_16BITS(ptr)); ptr++;
		printf("ATTR%04x", EXTRACT_16BITS(ptr)); ptr++;
		printf("(");
		print_octets((u_char *)ptr, len-6);
		printf(")");
	} else {
		/* IETF-defined Attributes */
		ptr++;
		attr_type = EXTRACT_16BITS(ptr); ptr++;
		printf("%s", tok2str(l2tp_avp2str, "AVP-#%u", attr_type));
		printf("(");
		if (hidden) {
			printf("???");
		} else {
			switch (attr_type) {
			case L2TP_AVP_MSGTYPE:
				l2tp_msgtype_print((u_char *)ptr);
				break;
			case L2TP_AVP_RESULT_CODE:
				l2tp_result_code_print((u_char *)ptr, len-6);
				break;
			case L2TP_AVP_PROTO_VER:
				l2tp_proto_ver_print(ptr);
				break;
			case L2TP_AVP_FRAMING_CAP:
				l2tp_framing_cap_print((u_char *)ptr);
				break;
			case L2TP_AVP_BEARER_CAP:
				l2tp_bearer_cap_print((u_char *)ptr);
				break;
			case L2TP_AVP_TIE_BREAKER:
				print_octets((u_char *)ptr, 8);
				break;
			case L2TP_AVP_FIRM_VER:
			case L2TP_AVP_ASSND_TUN_ID:
			case L2TP_AVP_RECV_WIN_SIZE:
			case L2TP_AVP_ASSND_SESS_ID:
				print_16bits_val(ptr);
				break;
			case L2TP_AVP_HOST_NAME:
			case L2TP_AVP_VENDOR_NAME:
			case L2TP_AVP_CALLING_NUMBER:
			case L2TP_AVP_CALLED_NUMBER:
			case L2TP_AVP_SUB_ADDRESS:
			case L2TP_AVP_PROXY_AUTH_NAME:
			case L2TP_AVP_PRIVATE_GRP_ID:
				print_string((u_char *)ptr, len-6);
				break;
			case L2TP_AVP_CHALLENGE:
			case L2TP_AVP_INI_RECV_LCP:
			case L2TP_AVP_LAST_SENT_LCP:
			case L2TP_AVP_LAST_RECV_LCP:
			case L2TP_AVP_PROXY_AUTH_CHAL:
			case L2TP_AVP_PROXY_AUTH_RESP:
			case L2TP_AVP_RANDOM_VECTOR:
				print_octets((u_char *)ptr, len-6);
				break;
			case L2TP_AVP_Q931_CC:
				l2tp_q931_cc_print((u_char *)ptr, len-6);
				break;
			case L2TP_AVP_CHALLENGE_RESP:
				print_octets((u_char *)ptr, 16);
				break;
			case L2TP_AVP_CALL_SER_NUM:
			case L2TP_AVP_MINIMUM_BPS:
			case L2TP_AVP_MAXIMUM_BPS:
			case L2TP_AVP_TX_CONN_SPEED:
			case L2TP_AVP_PHY_CHANNEL_ID:
			case L2TP_AVP_RX_CONN_SPEED:
				print_32bits_val((u_int32_t *)ptr);
				break;
			case L2TP_AVP_BEARER_TYPE:
				l2tp_bearer_type_print((u_char *)ptr);
				break;
			case L2TP_AVP_FRAMING_TYPE:
				l2tp_framing_type_print((u_char *)ptr);
				break;
			case L2TP_AVP_PACKET_PROC_DELAY:
				l2tp_packet_proc_delay_print();
				break;
			case L2TP_AVP_PROXY_AUTH_TYPE:
				l2tp_proxy_auth_type_print((u_char *)ptr);
				break;
			case L2TP_AVP_PROXY_AUTH_ID:
				l2tp_proxy_auth_id_print((u_char *)ptr);
				break;
			case L2TP_AVP_CALL_ERRORS:
				l2tp_call_errors_print((u_char *)ptr);
				break;
			case L2TP_AVP_ACCM:
				l2tp_accm_print((u_char *)ptr);
				break;
			case L2TP_AVP_SEQ_REQUIRED:
				break;	/* No Attribute Value */
			case L2TP_AVP_PPP_DISCON_CC:
				l2tp_ppp_discon_cc_print((u_char *)ptr, len-6);
				break;
			default:
				break;
			}
		}
		printf(")");
	}

	l2tp_avp_print(dat+len, length-len);
	return;

 trunc:
	printf("|...");
}


void
l2tp_print(const u_char *dat, u_int length)
{
	const u_char *ptr = dat;
	u_int cnt = 0;			/* total octets consumed */
	u_int16_t pad;
	int flag_t, flag_l, flag_s, flag_o;
	u_int16_t l2tp_len;

	flag_t = flag_l = flag_s = flag_o = FALSE;

	TCHECK2(*ptr, 2);	/* Flags & Version */
	if ((EXTRACT_16BITS(ptr) & L2TP_VERSION_MASK) == L2TP_VERSION_L2TP) {
		printf(" l2tp:");
	} else if ((EXTRACT_16BITS(ptr) & L2TP_VERSION_MASK) == L2TP_VERSION_L2F) {
		printf(" l2f:");
		return;		/* nothing to do */
	} else {
		printf(" Unknown Version, neither L2F(1) nor L2TP(2)");
		return;		/* nothing we can do */
	}

	printf("[");
	if (EXTRACT_16BITS(ptr) & L2TP_FLAG_TYPE) {
		flag_t = TRUE;
		printf("T");
	}
	if (EXTRACT_16BITS(ptr) & L2TP_FLAG_LENGTH) {
		flag_l = TRUE;
		printf("L");
	}
	if (EXTRACT_16BITS(ptr) & L2TP_FLAG_SEQUENCE) {
		flag_s = TRUE;
		printf("S");
	}
	if (EXTRACT_16BITS(ptr) & L2TP_FLAG_OFFSET) {
		flag_o = TRUE;
		printf("O");
	}
	if (EXTRACT_16BITS(ptr) & L2TP_FLAG_PRIORITY)
		printf("P");
	printf("]");

	ptr += 2;
	cnt += 2;

	if (flag_l) {
		TCHECK2(*ptr, 2);	/* Length */
		l2tp_len = EXTRACT_16BITS(ptr);
		ptr += 2;
		cnt += 2;
	} else {
		l2tp_len = 0;
	}

	TCHECK2(*ptr, 2);		/* Tunnel ID */
	printf("(%u/", EXTRACT_16BITS(ptr));
	ptr += 2;
	cnt += 2;
	TCHECK2(*ptr, 2);		/* Session ID */
	printf("%u)",  EXTRACT_16BITS(ptr));
	ptr += 2;
	cnt += 2;

	if (flag_s) {
		TCHECK2(*ptr, 2);	/* Ns */
		printf("Ns=%u,", EXTRACT_16BITS(ptr));
		ptr += 2;
		cnt += 2;
		TCHECK2(*ptr, 2);	/* Nr */
		printf("Nr=%u",  EXTRACT_16BITS(ptr));
		ptr += 2;
		cnt += 2;
	}

	if (flag_o) {
		TCHECK2(*ptr, 2);	/* Offset Size */
		pad =  EXTRACT_16BITS(ptr);
		ptr += (2 + pad);
		cnt += (2 + pad);
	}

	if (flag_l) {
		if (length < l2tp_len) {
			printf(" Length %u larger than packet", l2tp_len);
			return;
		}
		length = l2tp_len;
	}
	if (length < cnt) {
		printf(" Length %u smaller than header length", length);
		return;
	}
	if (flag_t) {
		if (!flag_l) {
			printf(" No length");
			return;
		}
		if (length - cnt == 0) {
			printf(" ZLB");
		} else {
			l2tp_avp_print(ptr, length - cnt);
		}
	} else {
		printf(" {");
		ppp_print(ptr, length - cnt);
		printf("}");
	}

	return;

 trunc:
	printf("%s", tstr);
}
