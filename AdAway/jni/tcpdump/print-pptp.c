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
 * PPTP support contributed by Motonori Shindo (mshindo@mshindo.net)
 */


#ifndef lint
static const char rcsid[] _U_ =
     "@(#) $Header: /tcpdump/master/tcpdump/print-pptp.c,v 1.12 2006-06-23 02:03:09 hannes Exp $";
#endif

#ifdef HAVE_CONFIG_H
#include "config.h"
#endif

#include <tcpdump-stdinc.h>

#include <stdio.h>

#include "interface.h"
#include "extract.h"

static char tstr[] = " [|pptp]";

#define PPTP_MSG_TYPE_CTRL	1	/* Control Message */
#define PPTP_MSG_TYPE_MGMT	2	/* Management Message (currently not used */
#define PPTP_MAGIC_COOKIE	0x1a2b3c4d	/* for sanity check */

#define PPTP_CTRL_MSG_TYPE_SCCRQ	1
#define PPTP_CTRL_MSG_TYPE_SCCRP	2
#define PPTP_CTRL_MSG_TYPE_StopCCRQ	3
#define PPTP_CTRL_MSG_TYPE_StopCCRP	4
#define PPTP_CTRL_MSG_TYPE_ECHORQ	5
#define PPTP_CTRL_MSG_TYPE_ECHORP	6
#define PPTP_CTRL_MSG_TYPE_OCRQ		7
#define PPTP_CTRL_MSG_TYPE_OCRP		8
#define PPTP_CTRL_MSG_TYPE_ICRQ		9
#define PPTP_CTRL_MSG_TYPE_ICRP		10
#define PPTP_CTRL_MSG_TYPE_ICCN		11
#define PPTP_CTRL_MSG_TYPE_CCRQ		12
#define PPTP_CTRL_MSG_TYPE_CDN		13
#define PPTP_CTRL_MSG_TYPE_WEN		14
#define PPTP_CTRL_MSG_TYPE_SLI		15

#define PPTP_FRAMING_CAP_ASYNC_MASK	0x00000001      /* Aynchronous */
#define PPTP_FRAMING_CAP_SYNC_MASK	0x00000002      /* Synchronous */

#define PPTP_BEARER_CAP_ANALOG_MASK	0x00000001      /* Analog */
#define PPTP_BEARER_CAP_DIGITAL_MASK	0x00000002      /* Digital */

static const char *pptp_message_type_string[] = {
	"NOT_DEFINED",		/* 0  Not defined in the RFC2637 */
	"SCCRQ",		/* 1  Start-Control-Connection-Request */
	"SCCRP",		/* 2  Start-Control-Connection-Reply */
	"StopCCRQ",		/* 3  Stop-Control-Connection-Request */
	"StopCCRP",		/* 4  Stop-Control-Connection-Reply */
	"ECHORQ",		/* 5  Echo Request */
	"ECHORP",		/* 6  Echo Reply */

	"OCRQ",			/* 7  Outgoing-Call-Request */
	"OCRP",			/* 8  Outgoing-Call-Reply */
	"ICRQ",			/* 9  Incoming-Call-Request */
	"ICRP",			/* 10 Incoming-Call-Reply */
	"ICCN",			/* 11 Incoming-Call-Connected */
	"CCRQ",			/* 12 Call-Clear-Request */
	"CDN",			/* 13 Call-Disconnect-Notify */

	"WEN",			/* 14 WAN-Error-Notify */

	"SLI"			/* 15 Set-Link-Info */
#define PPTP_MAX_MSGTYPE_INDEX	16
};

/* common for all PPTP control messages */
struct pptp_hdr {
	u_int16_t length;
	u_int16_t msg_type;
	u_int32_t magic_cookie;
	u_int16_t ctrl_msg_type;
	u_int16_t reserved0;
};

struct pptp_msg_sccrq {
	u_int16_t proto_ver;
	u_int16_t reserved1;
	u_int32_t framing_cap;
	u_int32_t bearer_cap;
	u_int16_t max_channel;
	u_int16_t firm_rev;
	u_char hostname[64];
	u_char vendor[64];
};

struct pptp_msg_sccrp {
	u_int16_t proto_ver;
	u_int8_t result_code;
	u_int8_t err_code;
	u_int32_t framing_cap;
	u_int32_t bearer_cap;
	u_int16_t max_channel;
	u_int16_t firm_rev;
	u_char hostname[64];
	u_char vendor[64];
};

struct pptp_msg_stopccrq {
	u_int8_t reason;
	u_int8_t reserved1;
	u_int16_t reserved2;
};

struct pptp_msg_stopccrp {
	u_int8_t result_code;
	u_int8_t err_code;
	u_int16_t reserved1;
};

struct pptp_msg_echorq {
	u_int32_t id;
};

struct pptp_msg_echorp {
	u_int32_t id;
	u_int8_t result_code;
	u_int8_t err_code;
	u_int16_t reserved1;
};

struct pptp_msg_ocrq {
	u_int16_t call_id;
	u_int16_t call_ser;
	u_int32_t min_bps;
	u_int32_t max_bps;
	u_int32_t bearer_type;
	u_int32_t framing_type;
	u_int16_t recv_winsiz;
	u_int16_t pkt_proc_delay;
	u_int16_t phone_no_len;
	u_int16_t reserved1;
	u_char phone_no[64];
	u_char subaddr[64];
};

struct pptp_msg_ocrp {
	u_int16_t call_id;
	u_int16_t peer_call_id;
	u_int8_t result_code;
	u_int8_t err_code;
	u_int16_t cause_code;
	u_int32_t conn_speed;
	u_int16_t recv_winsiz;
	u_int16_t pkt_proc_delay;
	u_int32_t phy_chan_id;
};

struct pptp_msg_icrq {
	u_int16_t call_id;
	u_int16_t call_ser;
	u_int32_t bearer_type;
	u_int32_t phy_chan_id;
	u_int16_t dialed_no_len;
	u_int16_t dialing_no_len;
	u_char dialed_no[64];		/* DNIS */
	u_char dialing_no[64];		/* CLID */
	u_char subaddr[64];
};

struct pptp_msg_icrp {
	u_int16_t call_id;
	u_int16_t peer_call_id;
	u_int8_t result_code;
	u_int8_t err_code;
	u_int16_t recv_winsiz;
	u_int16_t pkt_proc_delay;
	u_int16_t reserved1;
};

struct pptp_msg_iccn {
	u_int16_t peer_call_id;
	u_int16_t reserved1;
	u_int32_t conn_speed;
	u_int16_t recv_winsiz;
	u_int16_t pkt_proc_delay;
	u_int32_t framing_type;
};

struct pptp_msg_ccrq {
	u_int16_t call_id;
	u_int16_t reserved1;
};

struct pptp_msg_cdn {
	u_int16_t call_id;
	u_int8_t result_code;
	u_int8_t err_code;
	u_int16_t cause_code;
	u_int16_t reserved1;
	u_char call_stats[128];
};

struct pptp_msg_wen {
	u_int16_t peer_call_id;
	u_int16_t reserved1;
	u_int32_t crc_err;
	u_int32_t framing_err;
	u_int32_t hardware_overrun;
	u_int32_t buffer_overrun;
	u_int32_t timeout_err;
	u_int32_t align_err;
};

struct pptp_msg_sli {
	u_int16_t peer_call_id;
	u_int16_t reserved1;
	u_int32_t send_accm;
	u_int32_t recv_accm;
};

/* attributes that appear more than once in above messages:

   Number of
   occurence    attributes
  --------------------------------------
      2         u_int32_t bearer_cap;
      2         u_int32_t bearer_type;
      6         u_int16_t call_id;
      2         u_int16_t call_ser;
      2         u_int16_t cause_code;
      2         u_int32_t conn_speed;
      6         u_int8_t err_code;
      2         u_int16_t firm_rev;
      2         u_int32_t framing_cap;
      2         u_int32_t framing_type;
      2         u_char hostname[64];
      2         u_int32_t id;
      2         u_int16_t max_channel;
      5         u_int16_t peer_call_id;
      2         u_int32_t phy_chan_id;
      4         u_int16_t pkt_proc_delay;
      2         u_int16_t proto_ver;
      4         u_int16_t recv_winsiz;
      2         u_int8_t reserved1;
      9         u_int16_t reserved1;
      6         u_int8_t result_code;
      2         u_char subaddr[64];
      2         u_char vendor[64];

  so I will prepare print out functions for these attributes (except for
  reserved*).
*/

/******************************************/
/* Attribute-specific print out functions */
/******************************************/

/* In these attribute-specific print-out functions, it't not necessary
   to do TCHECK because they are already checked in the caller of
   these functions. */

static void
pptp_bearer_cap_print(const u_int32_t *bearer_cap)
{
	printf(" BEARER_CAP(");
	if (EXTRACT_32BITS(bearer_cap) & PPTP_BEARER_CAP_DIGITAL_MASK) {
                printf("D");
        }
        if (EXTRACT_32BITS(bearer_cap) & PPTP_BEARER_CAP_ANALOG_MASK) {
                printf("A");
        }
	printf(")");
}

static void
pptp_bearer_type_print(const u_int32_t *bearer_type)
{
	printf(" BEARER_TYPE(");
	switch (EXTRACT_32BITS(bearer_type)) {
	case 1:
		printf("A");	/* Analog */
		break;
	case 2:
		printf("D");	/* Digital */
		break;
	case 3:
		printf("Any");
		break;
	default:
		printf("?");
		break;
        }
	printf(")");
}

static void
pptp_call_id_print(const u_int16_t *call_id)
{
	printf(" CALL_ID(%u)", EXTRACT_16BITS(call_id));
}

static void
pptp_call_ser_print(const u_int16_t *call_ser)
{
	printf(" CALL_SER_NUM(%u)", EXTRACT_16BITS(call_ser));
}

static void
pptp_cause_code_print(const u_int16_t *cause_code)
{
	printf(" CAUSE_CODE(%u)", EXTRACT_16BITS(cause_code));
}

static void
pptp_conn_speed_print(const u_int32_t *conn_speed)
{
	printf(" CONN_SPEED(%u)", EXTRACT_32BITS(conn_speed));
}

static void
pptp_err_code_print(const u_int8_t *err_code)
{
	printf(" ERR_CODE(%u", *err_code);
	if (vflag) {
		switch (*err_code) {
		case 0:
			printf(":None");
			break;
		case 1:
			printf(":Not-Connected");
			break;
		case 2:
			printf(":Bad-Format");
			break;
		case 3:
			printf(":Bad-Valude");
			break;
		case 4:
			printf(":No-Resource");
			break;
		case 5:
			printf(":Bad-Call-ID");
			break;
		case 6:
			printf(":PAC-Error");
			break;
		default:
			printf(":?");
			break;
		}
	}
	printf(")");
}

static void
pptp_firm_rev_print(const u_int16_t *firm_rev)
{
	printf(" FIRM_REV(%u)", EXTRACT_16BITS(firm_rev));
}

static void
pptp_framing_cap_print(const u_int32_t *framing_cap)
{
	printf(" FRAME_CAP(");
	if (EXTRACT_32BITS(framing_cap) & PPTP_FRAMING_CAP_ASYNC_MASK) {
                printf("A");		/* Async */
        }
        if (EXTRACT_32BITS(framing_cap) & PPTP_FRAMING_CAP_SYNC_MASK) {
                printf("S");		/* Sync */
        }
	printf(")");
}

static void
pptp_framing_type_print(const u_int32_t *framing_type)
{
	printf(" FRAME_TYPE(");
	switch (EXTRACT_32BITS(framing_type)) {
	case 1:
		printf("A");		/* Async */
		break;
	case 2:
		printf("S");		/* Sync */
		break;
	case 3:
		printf("E");		/* Either */
		break;
	default:
		printf("?");
		break;
	}
	printf(")");
}

static void
pptp_hostname_print(const u_char *hostname)
{
	printf(" HOSTNAME(%.64s)", hostname);
}

static void
pptp_id_print(const u_int32_t *id)
{
	printf(" ID(%u)", EXTRACT_32BITS(id));
}

static void
pptp_max_channel_print(const u_int16_t *max_channel)
{
	printf(" MAX_CHAN(%u)", EXTRACT_16BITS(max_channel));
}

static void
pptp_peer_call_id_print(const u_int16_t *peer_call_id)
{
	printf(" PEER_CALL_ID(%u)", EXTRACT_16BITS(peer_call_id));
}

static void
pptp_phy_chan_id_print(const u_int32_t *phy_chan_id)
{
	printf(" PHY_CHAN_ID(%u)", EXTRACT_32BITS(phy_chan_id));
}

static void
pptp_pkt_proc_delay_print(const u_int16_t *pkt_proc_delay)
{
	printf(" PROC_DELAY(%u)", EXTRACT_16BITS(pkt_proc_delay));
}

static void
pptp_proto_ver_print(const u_int16_t *proto_ver)
{
	printf(" PROTO_VER(%u.%u)",	/* Version.Revision */
	       EXTRACT_16BITS(proto_ver) >> 8,
	       EXTRACT_16BITS(proto_ver) & 0xff);
}

static void
pptp_recv_winsiz_print(const u_int16_t *recv_winsiz)
{
	printf(" RECV_WIN(%u)", EXTRACT_16BITS(recv_winsiz));
}

static void
pptp_result_code_print(const u_int8_t *result_code, int ctrl_msg_type)
{
	printf(" RESULT_CODE(%u", *result_code);
	if (vflag) {
		switch (ctrl_msg_type) {
		case PPTP_CTRL_MSG_TYPE_SCCRP:
			switch (*result_code) {
			case 1:
				printf(":Successful channel establishment");
				break;
			case 2:
				printf(":General error");
				break;
			case 3:
				printf(":Command channel already exists");
				break;
			case 4:
				printf(":Requester is not authorized to establish a command channel");
				break;
			case 5:
				printf(":The protocol version of the requester is not supported");
				break;
			default:
				printf(":?");
				break;
			}
			break;
		case PPTP_CTRL_MSG_TYPE_StopCCRP:
		case PPTP_CTRL_MSG_TYPE_ECHORP:
			switch (*result_code) {
			case 1:
				printf(":OK");
				break;
			case 2:
				printf(":General Error");
				break;
			default:
				printf(":?");
				break;
			}
			break;
		case PPTP_CTRL_MSG_TYPE_OCRP:
			switch (*result_code) {
			case 1:
				printf(":Connected");
				break;
			case 2:
				printf(":General Error");
				break;
			case 3:
				printf(":No Carrier");
				break;
			case 4:
				printf(":Busy");
				break;
			case 5:
				printf(":No Dial Tone");
				break;
			case 6:
				printf(":Time-out");
				break;
			case 7:
				printf(":Do Not Accept");
				break;
			default:
				printf(":?");
				break;
			}
			break;
		case PPTP_CTRL_MSG_TYPE_ICRP:
			switch (*result_code) {
			case 1:
				printf(":Connect");
				break;
			case 2:
				printf(":General Error");
				break;
			case 3:
				printf(":Do Not Accept");
				break;
			default:
				printf(":?");
				break;
			}
			break;
		case PPTP_CTRL_MSG_TYPE_CDN:
			switch (*result_code) {
			case 1:
				printf(":Lost Carrier");
				break;
			case 2:
				printf(":General Error");
				break;
			case 3:
				printf(":Admin Shutdown");
				break;
			case 4:
				printf(":Request");
			default:
				printf(":?");
				break;
			break;
			}
		default:
			/* assertion error */
			break;
		}
	}
	printf(")");
}

static void
pptp_subaddr_print(const u_char *subaddr)
{
	printf(" SUB_ADDR(%.64s)", subaddr);
}

static void
pptp_vendor_print(const u_char *vendor)
{
	printf(" VENDOR(%.64s)", vendor);
}

/************************************/
/* PPTP message print out functions */
/************************************/
static void
pptp_sccrq_print(const u_char *dat)
{
	struct pptp_msg_sccrq *ptr = (struct pptp_msg_sccrq *)dat;

	TCHECK(ptr->proto_ver);
	pptp_proto_ver_print(&ptr->proto_ver);
	TCHECK(ptr->reserved1);
	TCHECK(ptr->framing_cap);
	pptp_framing_cap_print(&ptr->framing_cap);
	TCHECK(ptr->bearer_cap);
	pptp_bearer_cap_print(&ptr->bearer_cap);
	TCHECK(ptr->max_channel);
	pptp_max_channel_print(&ptr->max_channel);
	TCHECK(ptr->firm_rev);
	pptp_firm_rev_print(&ptr->firm_rev);
	TCHECK(ptr->hostname);
	pptp_hostname_print(&ptr->hostname[0]);
	TCHECK(ptr->vendor);
	pptp_vendor_print(&ptr->vendor[0]);

	return;

trunc:
	printf("%s", tstr);
}

static void
pptp_sccrp_print(const u_char *dat)
{
	struct pptp_msg_sccrp *ptr = (struct pptp_msg_sccrp *)dat;

	TCHECK(ptr->proto_ver);
	pptp_proto_ver_print(&ptr->proto_ver);
	TCHECK(ptr->result_code);
	pptp_result_code_print(&ptr->result_code, PPTP_CTRL_MSG_TYPE_SCCRP);
	TCHECK(ptr->err_code);
	pptp_err_code_print(&ptr->err_code);
	TCHECK(ptr->framing_cap);
	pptp_framing_cap_print(&ptr->framing_cap);
	TCHECK(ptr->bearer_cap);
	pptp_bearer_cap_print(&ptr->bearer_cap);
	TCHECK(ptr->max_channel);
	pptp_max_channel_print(&ptr->max_channel);
	TCHECK(ptr->firm_rev);
	pptp_firm_rev_print(&ptr->firm_rev);
	TCHECK(ptr->hostname);
	pptp_hostname_print(&ptr->hostname[0]);
	TCHECK(ptr->vendor);
	pptp_vendor_print(&ptr->vendor[0]);

	return;

trunc:
	printf("%s", tstr);
}

static void
pptp_stopccrq_print(const u_char *dat)
{
	struct pptp_msg_stopccrq *ptr = (struct pptp_msg_stopccrq *)dat;

	TCHECK(ptr->reason);
	printf(" REASON(%u", ptr->reason);
	if (vflag) {
		switch (ptr->reason) {
		case 1:
			printf(":None");
			break;
		case 2:
			printf(":Stop-Protocol");
			break;
		case 3:
			printf(":Stop-Local-Shutdown");
			break;
		default:
			printf(":?");
			break;
		}
	}
	printf(")");
	TCHECK(ptr->reserved1);
	TCHECK(ptr->reserved2);

	return;

trunc:
	printf("%s", tstr);
}

static void
pptp_stopccrp_print(const u_char *dat)
{
	struct pptp_msg_stopccrp *ptr = (struct pptp_msg_stopccrp *)dat;

	TCHECK(ptr->result_code);
	pptp_result_code_print(&ptr->result_code, PPTP_CTRL_MSG_TYPE_StopCCRP);
	TCHECK(ptr->err_code);
	pptp_err_code_print(&ptr->err_code);
	TCHECK(ptr->reserved1);

	return;

trunc:
	printf("%s", tstr);
}

static void
pptp_echorq_print(const u_char *dat)
{
	struct pptp_msg_echorq *ptr = (struct pptp_msg_echorq *)dat;

	TCHECK(ptr->id);
	pptp_id_print(&ptr->id);

	return;

trunc:
	printf("%s", tstr);
}

static void
pptp_echorp_print(const u_char *dat)
{
	struct pptp_msg_echorp *ptr = (struct pptp_msg_echorp *)dat;

	TCHECK(ptr->id);
	pptp_id_print(&ptr->id);
	TCHECK(ptr->result_code);
	pptp_result_code_print(&ptr->result_code, PPTP_CTRL_MSG_TYPE_ECHORP);
	TCHECK(ptr->err_code);
	pptp_err_code_print(&ptr->err_code);
	TCHECK(ptr->reserved1);

	return;

trunc:
	printf("%s", tstr);
}

static void
pptp_ocrq_print(const u_char *dat)
{
	struct pptp_msg_ocrq *ptr = (struct pptp_msg_ocrq *)dat;

	TCHECK(ptr->call_id);
	pptp_call_id_print(&ptr->call_id);
	TCHECK(ptr->call_ser);
	pptp_call_ser_print(&ptr->call_ser);
	TCHECK(ptr->min_bps);
	printf(" MIN_BPS(%u)", EXTRACT_32BITS(&ptr->min_bps));
	TCHECK(ptr->max_bps);
	printf(" MAX_BPS(%u)", EXTRACT_32BITS(&ptr->max_bps));
	TCHECK(ptr->bearer_type);
	pptp_bearer_type_print(&ptr->bearer_type);
	TCHECK(ptr->framing_type);
	pptp_framing_type_print(&ptr->framing_type);
	TCHECK(ptr->recv_winsiz);
	pptp_recv_winsiz_print(&ptr->recv_winsiz);
	TCHECK(ptr->pkt_proc_delay);
	pptp_pkt_proc_delay_print(&ptr->pkt_proc_delay);
	TCHECK(ptr->phone_no_len);
	printf(" PHONE_NO_LEN(%u)", EXTRACT_16BITS(&ptr->phone_no_len));
	TCHECK(ptr->reserved1);
	TCHECK(ptr->phone_no);
	printf(" PHONE_NO(%.64s)", ptr->phone_no);
	TCHECK(ptr->subaddr);
	pptp_subaddr_print(&ptr->subaddr[0]);

	return;

trunc:
	printf("%s", tstr);
}

static void
pptp_ocrp_print(const u_char *dat)
{
	struct pptp_msg_ocrp *ptr = (struct pptp_msg_ocrp *)dat;

	TCHECK(ptr->call_id);
	pptp_call_id_print(&ptr->call_id);
	TCHECK(ptr->peer_call_id);
	pptp_peer_call_id_print(&ptr->peer_call_id);
	TCHECK(ptr->result_code);
	pptp_result_code_print(&ptr->result_code, PPTP_CTRL_MSG_TYPE_OCRP);
	TCHECK(ptr->err_code);
	pptp_err_code_print(&ptr->err_code);
	TCHECK(ptr->cause_code);
	pptp_cause_code_print(&ptr->cause_code);
	TCHECK(ptr->conn_speed);
	pptp_conn_speed_print(&ptr->conn_speed);
	TCHECK(ptr->recv_winsiz);
	pptp_recv_winsiz_print(&ptr->recv_winsiz);
	TCHECK(ptr->pkt_proc_delay);
	pptp_pkt_proc_delay_print(&ptr->pkt_proc_delay);
	TCHECK(ptr->phy_chan_id);
	pptp_phy_chan_id_print(&ptr->phy_chan_id);

	return;

trunc:
	printf("%s", tstr);
}

static void
pptp_icrq_print(const u_char *dat)
{
	struct pptp_msg_icrq *ptr = (struct pptp_msg_icrq *)dat;

	TCHECK(ptr->call_id);
	pptp_call_id_print(&ptr->call_id);
	TCHECK(ptr->call_ser);
	pptp_call_ser_print(&ptr->call_ser);
	TCHECK(ptr->bearer_type);
	pptp_bearer_type_print(&ptr->bearer_type);
	TCHECK(ptr->phy_chan_id);
	pptp_phy_chan_id_print(&ptr->phy_chan_id);
	TCHECK(ptr->dialed_no_len);
	printf(" DIALED_NO_LEN(%u)", EXTRACT_16BITS(&ptr->dialed_no_len));
	TCHECK(ptr->dialing_no_len);
	printf(" DIALING_NO_LEN(%u)", EXTRACT_16BITS(&ptr->dialing_no_len));
	TCHECK(ptr->dialed_no);
	printf(" DIALED_NO(%.64s)", ptr->dialed_no);
	TCHECK(ptr->dialing_no);
	printf(" DIALING_NO(%.64s)", ptr->dialing_no);
	TCHECK(ptr->subaddr);
	pptp_subaddr_print(&ptr->subaddr[0]);

	return;

trunc:
	printf("%s", tstr);
}

static void
pptp_icrp_print(const u_char *dat)
{
	struct pptp_msg_icrp *ptr = (struct pptp_msg_icrp *)dat;

	TCHECK(ptr->call_id);
	pptp_call_id_print(&ptr->call_id);
	TCHECK(ptr->peer_call_id);
	pptp_peer_call_id_print(&ptr->peer_call_id);
	TCHECK(ptr->result_code);
	pptp_result_code_print(&ptr->result_code, PPTP_CTRL_MSG_TYPE_ICRP);
	TCHECK(ptr->err_code);
	pptp_err_code_print(&ptr->err_code);
	TCHECK(ptr->recv_winsiz);
	pptp_recv_winsiz_print(&ptr->recv_winsiz);
	TCHECK(ptr->pkt_proc_delay);
	pptp_pkt_proc_delay_print(&ptr->pkt_proc_delay);
	TCHECK(ptr->reserved1);

	return;

trunc:
	printf("%s", tstr);
}

static void
pptp_iccn_print(const u_char *dat)
{
	struct pptp_msg_iccn *ptr = (struct pptp_msg_iccn *)dat;

	TCHECK(ptr->peer_call_id);
	pptp_peer_call_id_print(&ptr->peer_call_id);
	TCHECK(ptr->reserved1);
	TCHECK(ptr->conn_speed);
	pptp_conn_speed_print(&ptr->conn_speed);
	TCHECK(ptr->recv_winsiz);
	pptp_recv_winsiz_print(&ptr->recv_winsiz);
	TCHECK(ptr->pkt_proc_delay);
	pptp_pkt_proc_delay_print(&ptr->pkt_proc_delay);
	TCHECK(ptr->framing_type);
	pptp_framing_type_print(&ptr->framing_type);

	return;

trunc:
	printf("%s", tstr);
}

static void
pptp_ccrq_print(const u_char *dat)
{
	struct pptp_msg_ccrq *ptr = (struct pptp_msg_ccrq *)dat;

	TCHECK(ptr->call_id);
	pptp_call_id_print(&ptr->call_id);
	TCHECK(ptr->reserved1);

	return;

trunc:
	printf("%s", tstr);
}

static void
pptp_cdn_print(const u_char *dat)
{
	struct pptp_msg_cdn *ptr = (struct pptp_msg_cdn *)dat;

	TCHECK(ptr->call_id);
	pptp_call_id_print(&ptr->call_id);
	TCHECK(ptr->result_code);
	pptp_result_code_print(&ptr->result_code, PPTP_CTRL_MSG_TYPE_CDN);
	TCHECK(ptr->err_code);
	pptp_err_code_print(&ptr->err_code);
	TCHECK(ptr->cause_code);
	pptp_cause_code_print(&ptr->cause_code);
	TCHECK(ptr->reserved1);
	TCHECK(ptr->call_stats);
	printf(" CALL_STATS(%.128s)", ptr->call_stats);

	return;

trunc:
	printf("%s", tstr);
}

static void
pptp_wen_print(const u_char *dat)
{
	struct pptp_msg_wen *ptr = (struct pptp_msg_wen *)dat;

	TCHECK(ptr->peer_call_id);
	pptp_peer_call_id_print(&ptr->peer_call_id);
	TCHECK(ptr->reserved1);
	TCHECK(ptr->crc_err);
	printf(" CRC_ERR(%u)", EXTRACT_32BITS(&ptr->crc_err));
	TCHECK(ptr->framing_err);
	printf(" FRAMING_ERR(%u)", EXTRACT_32BITS(&ptr->framing_err));
	TCHECK(ptr->hardware_overrun);
	printf(" HARDWARE_OVERRUN(%u)", EXTRACT_32BITS(&ptr->hardware_overrun));
	TCHECK(ptr->buffer_overrun);
	printf(" BUFFER_OVERRUN(%u)", EXTRACT_32BITS(&ptr->buffer_overrun));
	TCHECK(ptr->timeout_err);
	printf(" TIMEOUT_ERR(%u)", EXTRACT_32BITS(&ptr->timeout_err));
	TCHECK(ptr->align_err);
	printf(" ALIGN_ERR(%u)", EXTRACT_32BITS(&ptr->align_err));

	return;

trunc:
	printf("%s", tstr);
}

static void
pptp_sli_print(const u_char *dat)
{
	struct pptp_msg_sli *ptr = (struct pptp_msg_sli *)dat;

	TCHECK(ptr->peer_call_id);
	pptp_peer_call_id_print(&ptr->peer_call_id);
	TCHECK(ptr->reserved1);
	TCHECK(ptr->send_accm);
	printf(" SEND_ACCM(0x%08x)", EXTRACT_32BITS(&ptr->send_accm));
	TCHECK(ptr->recv_accm);
	printf(" RECV_ACCM(0x%08x)", EXTRACT_32BITS(&ptr->recv_accm));

	return;

trunc:
	printf("%s", tstr);
}

void
pptp_print(const u_char *dat)
{
	const struct pptp_hdr *hdr;
	u_int32_t mc;
	u_int16_t ctrl_msg_type;

	printf(": pptp");

	hdr = (struct pptp_hdr *)dat;

	TCHECK(hdr->length);
	if (vflag) {
		printf(" Length=%u", EXTRACT_16BITS(&hdr->length));
	}
	TCHECK(hdr->msg_type);
	if (vflag) {
		switch(EXTRACT_16BITS(&hdr->msg_type)) {
		case PPTP_MSG_TYPE_CTRL:
			printf(" CTRL-MSG");
			break;
		case PPTP_MSG_TYPE_MGMT:
			printf(" MGMT-MSG");
			break;
		default:
			printf(" UNKNOWN-MSG-TYPE");
			break;
		}
	}

	TCHECK(hdr->magic_cookie);
	mc = EXTRACT_32BITS(&hdr->magic_cookie);
	if (mc != PPTP_MAGIC_COOKIE) {
		printf(" UNEXPECTED Magic-Cookie!!(%08x)", mc);
	}
	if (vflag || mc != PPTP_MAGIC_COOKIE) {
		printf(" Magic-Cookie=%08x", mc);
	}
	TCHECK(hdr->ctrl_msg_type);
	ctrl_msg_type = EXTRACT_16BITS(&hdr->ctrl_msg_type);
	if (ctrl_msg_type < PPTP_MAX_MSGTYPE_INDEX) {
		printf(" CTRL_MSGTYPE=%s",
		       pptp_message_type_string[ctrl_msg_type]);
	} else {
		printf(" UNKNOWN_CTRL_MSGTYPE(%u)", ctrl_msg_type);
	}
	TCHECK(hdr->reserved0);

	dat += 12;

	switch(ctrl_msg_type) {
	case PPTP_CTRL_MSG_TYPE_SCCRQ:
		pptp_sccrq_print(dat);
		break;
	case PPTP_CTRL_MSG_TYPE_SCCRP:
		pptp_sccrp_print(dat);
		break;
	case PPTP_CTRL_MSG_TYPE_StopCCRQ:
		pptp_stopccrq_print(dat);
		break;
	case PPTP_CTRL_MSG_TYPE_StopCCRP:
		pptp_stopccrp_print(dat);
		break;
	case PPTP_CTRL_MSG_TYPE_ECHORQ:
		pptp_echorq_print(dat);
		break;
	case PPTP_CTRL_MSG_TYPE_ECHORP:
		pptp_echorp_print(dat);
		break;
	case PPTP_CTRL_MSG_TYPE_OCRQ:
		pptp_ocrq_print(dat);
		break;
	case PPTP_CTRL_MSG_TYPE_OCRP:
		pptp_ocrp_print(dat);
		break;
	case PPTP_CTRL_MSG_TYPE_ICRQ:
		pptp_icrq_print(dat);
		break;
	case PPTP_CTRL_MSG_TYPE_ICRP:
		pptp_icrp_print(dat);
		break;
	case PPTP_CTRL_MSG_TYPE_ICCN:
		pptp_iccn_print(dat);
		break;
	case PPTP_CTRL_MSG_TYPE_CCRQ:
		pptp_ccrq_print(dat);
		break;
	case PPTP_CTRL_MSG_TYPE_CDN:
		pptp_cdn_print(dat);
		break;
	case PPTP_CTRL_MSG_TYPE_WEN:
		pptp_wen_print(dat);
		break;
	case PPTP_CTRL_MSG_TYPE_SLI:
		pptp_sli_print(dat);
		break;
	default:
		/* do nothing */
		break;
	}

	return;

trunc:
	printf("%s", tstr);
}
