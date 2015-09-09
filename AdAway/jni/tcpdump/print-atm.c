/*
 * Copyright (c) 1994, 1995, 1996, 1997
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
 */

#define NETDISSECT_REWORKED
#ifdef HAVE_CONFIG_H
#include "config.h"
#endif

#include <tcpdump-stdinc.h>

#include "interface.h"
#include "extract.h"
#include "addrtoname.h"
#include "atm.h"
#include "atmuni31.h"
#include "llc.h"

static const char tstr[] = "[|atm]";

#define OAM_CRC10_MASK 0x3ff
#define OAM_PAYLOAD_LEN 48
#define OAM_FUNCTION_SPECIFIC_LEN 45 /* this excludes crc10 and cell-type/function-type */
#define OAM_CELLTYPE_FUNCTYPE_LEN 1

static const struct tok oam_f_values[] = {
    { VCI_OAMF4SC, "OAM F4 (segment)" },
    { VCI_OAMF4EC, "OAM F4 (end)" },
    { 0, NULL }
};

static const struct tok atm_pty_values[] = {
    { 0x0, "user data, uncongested, SDU 0" },
    { 0x1, "user data, uncongested, SDU 1" },
    { 0x2, "user data, congested, SDU 0" },
    { 0x3, "user data, congested, SDU 1" },
    { 0x4, "VCC OAM F5 flow segment" },
    { 0x5, "VCC OAM F5 flow end-to-end" },
    { 0x6, "Traffic Control and resource Mgmt" },
    { 0, NULL }
};

#define OAM_CELLTYPE_FM 0x1
#define OAM_CELLTYPE_PM 0x2
#define OAM_CELLTYPE_AD 0x8
#define OAM_CELLTYPE_SM 0xf

static const struct tok oam_celltype_values[] = {
    { OAM_CELLTYPE_FM, "Fault Management" },
    { OAM_CELLTYPE_PM, "Performance Management" },
    { OAM_CELLTYPE_AD, "activate/deactivate" },
    { OAM_CELLTYPE_SM, "System Management" },
    { 0, NULL }
};

#define OAM_FM_FUNCTYPE_AIS       0x0
#define OAM_FM_FUNCTYPE_RDI       0x1
#define OAM_FM_FUNCTYPE_CONTCHECK 0x4
#define OAM_FM_FUNCTYPE_LOOPBACK  0x8

static const struct tok oam_fm_functype_values[] = {
    { OAM_FM_FUNCTYPE_AIS, "AIS" },
    { OAM_FM_FUNCTYPE_RDI, "RDI" },
    { OAM_FM_FUNCTYPE_CONTCHECK, "Continuity Check" },
    { OAM_FM_FUNCTYPE_LOOPBACK, "Loopback" },
    { 0, NULL }
};

static const struct tok oam_pm_functype_values[] = {
    { 0x0, "Forward Monitoring" },
    { 0x1, "Backward Reporting" },
    { 0x2, "Monitoring and Reporting" },
    { 0, NULL }
};

static const struct tok oam_ad_functype_values[] = {
    { 0x0, "Performance Monitoring" },
    { 0x1, "Continuity Check" },
    { 0, NULL }
};

#define OAM_FM_LOOPBACK_INDICATOR_MASK 0x1

static const struct tok oam_fm_loopback_indicator_values[] = {
    { 0x0, "Reply" },
    { 0x1, "Request" },
    { 0, NULL }
};

static const struct tok *oam_functype_values[16] = {
    NULL,
    oam_fm_functype_values, /* 1 */
    oam_pm_functype_values, /* 2 */
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    oam_ad_functype_values, /* 8 */
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL,
    NULL
};

/*
 * Print an RFC 1483 LLC-encapsulated ATM frame.
 */
static void
atm_llc_print(netdissect_options *ndo,
              const u_char *p, int length, int caplen)
{
	u_short extracted_ethertype;

	if (!llc_print(ndo, p, length, caplen, NULL, NULL,
	    &extracted_ethertype)) {
		/* ether_type not known, print raw packet */
		if (extracted_ethertype) {
			ND_PRINT((ndo, "(LLC %s) ",
		etherproto_string(htons(extracted_ethertype))));
		}
		if (!ndo->ndo_suppress_default_print)
			ND_DEFAULTPRINT(p, caplen);
	}
}

/*
 * Given a SAP value, generate the LLC header value for a UI packet
 * with that SAP as the source and destination SAP.
 */
#define LLC_UI_HDR(sap)	((sap)<<16 | (sap<<8) | 0x03)

/*
 * This is the top level routine of the printer.  'p' points
 * to the LLC/SNAP header of the packet, 'h->ts' is the timestamp,
 * 'h->len' is the length of the packet off the wire, and 'h->caplen'
 * is the number of bytes actually captured.
 */
u_int
atm_if_print(netdissect_options *ndo,
             const struct pcap_pkthdr *h, const u_char *p)
{
	u_int caplen = h->caplen;
	u_int length = h->len;
	uint32_t llchdr;
	u_int hdrlen = 0;

	if (caplen < 1 || length < 1) {
		ND_PRINT((ndo, "%s", tstr));
		return (caplen);
	}

        /* Cisco Style NLPID ? */
        if (*p == LLC_UI) {
            if (ndo->ndo_eflag)
                ND_PRINT((ndo, "CNLPID "));
            isoclns_print(ndo, p + 1, length - 1, caplen - 1);
            return hdrlen;
        }

	/*
	 * Must have at least a DSAP, an SSAP, and the first byte of the
	 * control field.
	 */
	if (caplen < 3 || length < 3) {
		ND_PRINT((ndo, "%s", tstr));
		return (caplen);
	}

	/*
	 * Extract the presumed LLC header into a variable, for quick
	 * testing.
	 * Then check for a header that's neither a header for a SNAP
	 * packet nor an RFC 2684 routed NLPID-formatted PDU nor
	 * an 802.2-but-no-SNAP IP packet.
	 */
	llchdr = EXTRACT_24BITS(p);
	if (llchdr != LLC_UI_HDR(LLCSAP_SNAP) &&
	    llchdr != LLC_UI_HDR(LLCSAP_ISONS) &&
	    llchdr != LLC_UI_HDR(LLCSAP_IP)) {
		/*
		 * XXX - assume 802.6 MAC header from Fore driver.
		 *
		 * Unfortunately, the above list doesn't check for
		 * all known SAPs, doesn't check for headers where
		 * the source and destination SAP aren't the same,
		 * and doesn't check for non-UI frames.  It also
		 * runs the risk of an 802.6 MAC header that happens
		 * to begin with one of those values being
		 * incorrectly treated as an 802.2 header.
		 *
		 * So is that Fore driver still around?  And, if so,
		 * is it still putting 802.6 MAC headers on ATM
		 * packets?  If so, could it be changed to use a
		 * new DLT_IEEE802_6 value if we added it?
		 */
		if (caplen < 20 || length < 20) {
			ND_PRINT((ndo, "%s", tstr));
			return (caplen);
		}
		if (ndo->ndo_eflag)
			ND_PRINT((ndo, "%08x%08x %08x%08x ",
			       EXTRACT_32BITS(p),
			       EXTRACT_32BITS(p+4),
			       EXTRACT_32BITS(p+8),
			       EXTRACT_32BITS(p+12)));
		p += 20;
		length -= 20;
		caplen -= 20;
		hdrlen += 20;
	}
	atm_llc_print(ndo, p, length, caplen);
	return (hdrlen);
}

/*
 * ATM signalling.
 */
static const struct tok msgtype2str[] = {
	{ CALL_PROCEED,		"Call_proceeding" },
	{ CONNECT,		"Connect" },
	{ CONNECT_ACK,		"Connect_ack" },
	{ SETUP,		"Setup" },
	{ RELEASE,		"Release" },
	{ RELEASE_DONE,		"Release_complete" },
	{ RESTART,		"Restart" },
	{ RESTART_ACK,		"Restart_ack" },
	{ STATUS,		"Status" },
	{ STATUS_ENQ,		"Status_enquiry" },
	{ ADD_PARTY,		"Add_party" },
	{ ADD_PARTY_ACK,	"Add_party_ack" },
	{ ADD_PARTY_REJ,	"Add_party_reject" },
	{ DROP_PARTY,		"Drop_party" },
	{ DROP_PARTY_ACK,	"Drop_party_ack" },
	{ 0,			NULL }
};

static void
sig_print(netdissect_options *ndo,
          const u_char *p, int caplen)
{
	uint32_t call_ref;

	if (caplen < PROTO_POS) {
		ND_PRINT((ndo, "%s", tstr));
		return;
	}
	if (p[PROTO_POS] == Q2931) {
		/*
		 * protocol:Q.2931 for User to Network Interface
		 * (UNI 3.1) signalling
		 */
		ND_PRINT((ndo, "Q.2931"));
		if (caplen < MSG_TYPE_POS) {
			ND_PRINT((ndo, " %s", tstr));
			return;
		}
		ND_PRINT((ndo, ":%s ",
		    tok2str(msgtype2str, "msgtype#%d", p[MSG_TYPE_POS])));

		/*
		 * The call reference comes before the message type,
		 * so if we know we have the message type, which we
		 * do from the caplen test above, we also know we have
		 * the call reference.
		 */
		call_ref = EXTRACT_24BITS(&p[CALL_REF_POS]);
		ND_PRINT((ndo, "CALL_REF:0x%06x", call_ref));
	} else {
		/* SCCOP with some unknown protocol atop it */
		ND_PRINT((ndo, "SSCOP, proto %d ", p[PROTO_POS]));
	}
}

/*
 * Print an ATM PDU (such as an AAL5 PDU).
 */
void
atm_print(netdissect_options *ndo,
          u_int vpi, u_int vci, u_int traftype, const u_char *p, u_int length,
          u_int caplen)
{
	if (ndo->ndo_eflag)
		ND_PRINT((ndo, "VPI:%u VCI:%u ", vpi, vci));

	if (vpi == 0) {
		switch (vci) {

		case VCI_PPC:
			sig_print(ndo, p, caplen);
			return;

		case VCI_BCC:
			ND_PRINT((ndo, "broadcast sig: "));
			return;

		case VCI_OAMF4SC: /* fall through */
		case VCI_OAMF4EC:
			oam_print(ndo, p, length, ATM_OAM_HEC);
			return;

		case VCI_METAC:
			ND_PRINT((ndo, "meta: "));
			return;

		case VCI_ILMIC:
			ND_PRINT((ndo, "ilmi: "));
			snmp_print(ndo, p, length);
			return;
		}
	}

	switch (traftype) {

	case ATM_LLC:
	default:
		/*
		 * Assumes traffic is LLC if unknown.
		 */
		atm_llc_print(ndo, p, length, caplen);
		break;

	case ATM_LANE:
		lane_print(ndo, p, length, caplen);
		break;
	}
}

struct oam_fm_loopback_t {
    uint8_t loopback_indicator;
    uint8_t correlation_tag[4];
    uint8_t loopback_id[12];
    uint8_t source_id[12];
    uint8_t unused[16];
};

struct oam_fm_ais_rdi_t {
    uint8_t failure_type;
    uint8_t failure_location[16];
    uint8_t unused[28];
};

int
oam_print (netdissect_options *ndo,
           const u_char *p, u_int length, u_int hec)
{
    uint32_t cell_header;
    uint16_t vpi, vci, cksum, cksum_shouldbe, idx;
    uint8_t  cell_type, func_type, payload, clp;

    union {
        const struct oam_fm_loopback_t *oam_fm_loopback;
        const struct oam_fm_ais_rdi_t *oam_fm_ais_rdi;
    } oam_ptr;


    cell_header = EXTRACT_32BITS(p+hec);
    cell_type = ((*(p+ATM_HDR_LEN_NOHEC+hec))>>4) & 0x0f;
    func_type = (*(p+ATM_HDR_LEN_NOHEC+hec)) & 0x0f;

    vpi = (cell_header>>20)&0xff;
    vci = (cell_header>>4)&0xffff;
    payload = (cell_header>>1)&0x7;
    clp = cell_header&0x1;

    ND_PRINT((ndo, "%s, vpi %u, vci %u, payload [ %s ], clp %u, length %u",
           tok2str(oam_f_values, "OAM F5", vci),
           vpi, vci,
           tok2str(atm_pty_values, "Unknown", payload),
           clp, length));

    if (!ndo->ndo_vflag) {
        return 1;
    }

    ND_PRINT((ndo, "\n\tcell-type %s (%u)",
           tok2str(oam_celltype_values, "unknown", cell_type),
           cell_type));

    if (oam_functype_values[cell_type] == NULL)
        ND_PRINT((ndo, ", func-type unknown (%u)", func_type));
    else
        ND_PRINT((ndo, ", func-type %s (%u)",
               tok2str(oam_functype_values[cell_type],"none",func_type),
               func_type));

    p += ATM_HDR_LEN_NOHEC + hec;

    switch (cell_type << 4 | func_type) {
    case (OAM_CELLTYPE_FM << 4 | OAM_FM_FUNCTYPE_LOOPBACK):
        oam_ptr.oam_fm_loopback = (const struct oam_fm_loopback_t *)(p + OAM_CELLTYPE_FUNCTYPE_LEN);
        ND_PRINT((ndo, "\n\tLoopback-Indicator %s, Correlation-Tag 0x%08x",
               tok2str(oam_fm_loopback_indicator_values,
                       "Unknown",
                       oam_ptr.oam_fm_loopback->loopback_indicator & OAM_FM_LOOPBACK_INDICATOR_MASK),
               EXTRACT_32BITS(&oam_ptr.oam_fm_loopback->correlation_tag)));
        ND_PRINT((ndo, "\n\tLocation-ID "));
        for (idx = 0; idx < sizeof(oam_ptr.oam_fm_loopback->loopback_id); idx++) {
            if (idx % 2) {
                ND_PRINT((ndo, "%04x ", EXTRACT_16BITS(&oam_ptr.oam_fm_loopback->loopback_id[idx])));
            }
        }
        ND_PRINT((ndo, "\n\tSource-ID   "));
        for (idx = 0; idx < sizeof(oam_ptr.oam_fm_loopback->source_id); idx++) {
            if (idx % 2) {
                ND_PRINT((ndo, "%04x ", EXTRACT_16BITS(&oam_ptr.oam_fm_loopback->source_id[idx])));
            }
        }
        break;

    case (OAM_CELLTYPE_FM << 4 | OAM_FM_FUNCTYPE_AIS):
    case (OAM_CELLTYPE_FM << 4 | OAM_FM_FUNCTYPE_RDI):
        oam_ptr.oam_fm_ais_rdi = (const struct oam_fm_ais_rdi_t *)(p + OAM_CELLTYPE_FUNCTYPE_LEN);
        ND_PRINT((ndo, "\n\tFailure-type 0x%02x", oam_ptr.oam_fm_ais_rdi->failure_type));
        ND_PRINT((ndo, "\n\tLocation-ID "));
        for (idx = 0; idx < sizeof(oam_ptr.oam_fm_ais_rdi->failure_location); idx++) {
            if (idx % 2) {
                ND_PRINT((ndo, "%04x ", EXTRACT_16BITS(&oam_ptr.oam_fm_ais_rdi->failure_location[idx])));
            }
        }
        break;

    case (OAM_CELLTYPE_FM << 4 | OAM_FM_FUNCTYPE_CONTCHECK):
        /* FIXME */
        break;

    default:
        break;
    }

    /* crc10 checksum verification */
    cksum = EXTRACT_16BITS(p + OAM_CELLTYPE_FUNCTYPE_LEN + OAM_FUNCTION_SPECIFIC_LEN)
        & OAM_CRC10_MASK;
    cksum_shouldbe = verify_crc10_cksum(0, p, OAM_PAYLOAD_LEN);

    ND_PRINT((ndo, "\n\tcksum 0x%03x (%scorrect)",
           cksum,
           cksum_shouldbe == 0 ? "" : "in"));

    return 1;
}
